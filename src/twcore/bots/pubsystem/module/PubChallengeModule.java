package twcore.bots.pubsystem.module;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.game.Player;
import twcore.core.util.Point;
import twcore.core.util.Tools;

/*
 * By subby, updated by Arobas+
 */
public class PubChallengeModule extends AbstractModule {

    public static final int MAX_WARP = 2;
        
    private Map<Integer, DuelArea> areas;
    private Map<String, Dueler> duelers;
    private Map<String, Challenge> challenges;
    private Map<String, StartLagout> laggers;
    private Map<String, Long> spam;
    private LinkedList<String> noplay;
    private Map<String,String> openChallenges;

    private boolean sharkShrap = false; // True if sharks get shrap
    private boolean saveDuel = false;   // True if duels are saved to DB
    private boolean allowBets = true;   // True if betting is allowed
    private String database = "";

    //private boolean announceNew = false;
    private boolean announceWinner = false;
    private int announceWinnerAt = 0;
    private int announceZoneWinnerAt = 50000;
    private int announceOpenChallengeAt = 1000;
    private int deaths;
    
    // Coordinates of the safe zone used to clear out projectiles and mines.
    private Point coordsSafe;

    private int minBet = 100;
    public long lastBetAdvert = 0;
    public final long MAX_BET_ADVERT_FREQUENCY = 2 * Tools.TimeInMillis.MINUTE;  // Time in ms between bets being arena'd
    public final int MIN_BET_TO_NOTIFY = 500;
    public final long TIME_OPEN_CHALS_ACTIVE = 30 * Tools.TimeInMillis.MINUTE;  // Time open challenges are active
    
    public final String TWChatMID = "1693149144";

    public PubChallengeModule(BotAction m_botAction, PubContext context) {
        super(m_botAction, context, "Challenge");
        
        reloadConfig();

    }


    // *** EVENTS ***
    
    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.PLAYER_DEATH);
        eventRequester.request(EventRequester.PLAYER_LEFT);
        eventRequester.request(EventRequester.PLAYER_POSITION);
    }

    @Override
    public void handleEvent(PlayerLeft event) {
        if (!enabled)
            return;

        Player p = m_botAction.getPlayer(event.getPlayerID());
        if (p == null)
            return;

        String name = p.getPlayerName();

        noplay.remove(name.toLowerCase());
        openChallenges.remove(name);

        Dueler dueler = duelers.get(name);
        if (dueler == null)
            return;

        Challenge challenge = dueler.challenge;
        if (challenge != null && challenge.isStarted() && !challenge.hasEnded()) {
            laggers.put(name, new StartLagout(name));
            m_botAction.scheduleTask(laggers.get(name), 60 * 1000);
            m_botAction.sendSmartPrivateMessage(challenge.getOppositeDueler(name).name, "Your opponent has lagged out, and has 60 seconds to return to the game.");
        }
    }

    @Override
    public void handleEvent(FrequencyShipChange event) {

        if (!enabled)
            return;

        String name = m_botAction.getPlayerName(event.getPlayerID());

        Dueler dueler = duelers.get(name);
        if (dueler == null)
            return;

        Challenge challenge = dueler.challenge;

        if (challenge.isStarted() && !challenge.hasEnded()) {

            if (event.getShipType() == 0) {
                dueler.lagouts++;
                if (dueler.lagouts > 2) {
                    challenge.setLoser(dueler);
                    challenge.setWinByLagout();
                    announceWinner(challenge);
                    return;
                }
                laggers.put(name, new StartLagout(name));
                m_botAction.scheduleTask(laggers.get(name), 60 * 1000);
                m_botAction.sendSmartPrivateMessage(name, "You have lagged out. You have 60 seconds to return to the game. Use !lagout to return. You have "
                        + (2 - duelers.get(name).lagouts) + " lagouts left.");
                m_botAction.sendSmartPrivateMessage(challenge.getOppositeDueler(name).name, "Your opponent has lagged out. He has 60 seconds to return to the game.");
                return;
            }
            if( challenge.getChallenger().equals(name) ) {                
                if (event.getShipType() != challenge.ship1) {
                    dueler.lastShipChange = 1;
                    m_botAction.setShip(name, challenge.ship1);
                    givePrize(name);
                }
            } else if( challenge.getChallenged().equals(name) ) {                
                if (event.getShipType() != challenge.ship2) {
                    dueler.lastShipChange = 1;
                    m_botAction.setShip(name, challenge.ship2);
                    givePrize(name);
                }
            } 
        }

    }

    @Override
    public void handleEvent(PlayerPosition event) {
        if (!enabled || duelers.isEmpty())
            return;

        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (!duelers.containsKey(name))
            return;

        Dueler dueler = duelers.get(name);

        if (dueler == null)
            return;
        
        Challenge challenge = dueler.challenge;
        if (challenge.hasEnded()) {
            challenges.remove(getKey(challenge));
            duelers.remove(name);
            warpToSafe(name, false);
            return;
        }
        
        int x = event.getXLocation() / 16;
        int y = event.getYLocation() / 16;

        if (y > 225 && y < 675 && x > 225 && x < 675 && challenge.isStarted()) {
            DuelArea area = challenge.area;

            if (!laggers.containsKey(name) && System.currentTimeMillis() - dueler.backFromLagout > 2 * Tools.TimeInMillis.SECOND
                    && System.currentTimeMillis() - challenge.startAt > 7 * Tools.TimeInMillis.SECOND
                    && System.currentTimeMillis() - dueler.lastDeath > 7 * Tools.TimeInMillis.SECOND) {

                if (dueler.lastShipChange != 1)
                    dueler.warps++;
                else
                    dueler.lastShipChange = 0;

                if (MAX_WARP - dueler.warps == 1 && dueler.lastShipChange == 0)
                    m_botAction.sendSmartPrivateMessage(name, "You cannot warp/ship change during a duel. If you do it one more time, you lose.");
                else if (MAX_WARP == dueler.warps) {
                    m_botAction.sendSmartPrivateMessage(name, "Maximum of warp/ship change reached during a duel.");
                    m_botAction.sendSmartPrivateMessage(challenge.getOppositeDueler(dueler).name, "Your opponent has warped too many times, you are the winner.");
                    challenge.setLoser(dueler);
                    challenge.getOppositeDueler(dueler).kills = deaths;
                    announceWinner(challenge);
                    return;
                }
            }

            StartLagout l = laggers.remove(name);
            if (l != null) {
                l.cancelLagout();
                m_botAction.cancelTask(l);
                m_botAction.sendSmartPrivateMessage(name, "You have returned from lagout.");
            }

            Dueler d = duelers.get(name);

            if (duelers.get(name).type == Dueler.DUEL_CHALLENGER) {
                m_botAction.setShip(name, challenge.ship1);
                m_botAction.setFreq(name, getFreq());
                m_botAction.warpTo(name, area.warp1);
                m_botAction.warpTo(d.challenge.challengedName, area.warp2);
                givePrize(name);
            } else {
                m_botAction.setShip(name, challenge.ship2);
                m_botAction.setFreq(name, getFreq());
                m_botAction.warpTo(name, area.warp2);
                m_botAction.warpTo(d.challenge.challengerName, area.warp1);
                givePrize(name);
            }

        }
    }

    @Override
    public void handleEvent(PlayerDeath event) {
        if (!enabled)
            return;

        String killer = m_botAction.getPlayerName(event.getKillerID());
        String killee = m_botAction.getPlayerName(event.getKilleeID());

        if (!duelers.containsKey(killer) || !duelers.containsKey(killee))
            if (duelers.containsKey(killee))
                duelers.get(killee).lastDeath = System.currentTimeMillis();

        Dueler w = duelers.get(killer);
        Dueler l = duelers.get(killee);

        if (w == null || l == null)
            return;

        w.lastKill = System.currentTimeMillis();

        Challenge challenge = w.challenge;
        if (challenge == null || !challenge.getOppositeDueler(w).name.equals(l.name) || !challenge.isStarted()) {
            l.lastDeath = System.currentTimeMillis();
            return;
        }

        if (System.currentTimeMillis() - l.lastDeath < 7 * Tools.TimeInMillis.SECOND
                && System.currentTimeMillis() - challenge.startAt > 7 * Tools.TimeInMillis.SECOND) {
            w.spawns++;
            m_botAction.sendSmartPrivateMessage(w.name, "Spawning is illegal, no count.");
            m_botAction.sendSmartPrivateMessage(l.name, "No count.");
            l.lastDeath = System.currentTimeMillis();
            return;
        }

        l.lastDeath = System.currentTimeMillis();

        //m_botAction.shipReset(killer);
        m_botAction.scheduleTask(new ResetMines(killer), 4300);
        m_botAction.scheduleTask(new ResetMines(killee), 4300);

        m_botAction.scheduleTask(new UpdateScore(w, l), 1 * Tools.TimeInMillis.SECOND);

        //m_botAction.scheduleTask(new EnergyDepletedTask(killer), 1 * 1000);
        //m_botAction.scheduleTask(new EnergyDepletedTask(killer), 2 * 1000);
        m_botAction.scheduleTask(new EnergyDepletedTask(killer), 3 * 1000);
        m_botAction.scheduleTask(new EnergyDepletedTask(killer), 4 * 1000);

        m_botAction.scheduleTask(new SpawnBack(killer), 5 * 1000);
        m_botAction.scheduleTask(new SpawnBack(killee), 5 * 1000);

    }
    
    /**
     * Check for TWChat users, and cancel their duels and challenges.
     */
    @Override
    public void handleEvent(Message event) {
        String msg = event.getMessage();
        if (event.getMessageType() == Message.ARENA_MESSAGE && msg.startsWith("IP:"))
            checkForTWChat(msg);
    }

    
    // *** METHODS ***
    
    public void checkForTWChat(String message) {
        String playerMacID = extractFromInfoString(message, "MachineId:");
        String playerName = extractFromInfoString(message, "TypedName:");
        if (playerMacID == TWChatMID) {
            removePendingChallenge(playerName,true,true);
            removeChallengesTo(playerName,true,true);
        }
    }

    private String extractFromInfoString(String message, String infoName) {
        int beginIndex = message.indexOf(infoName);
        int endIndex;

        if (beginIndex == -1)
            return null;
        beginIndex = beginIndex + infoName.length();
        endIndex = message.indexOf("  ", beginIndex);
        if (endIndex == -1)
            endIndex = message.length();
        return message.substring(beginIndex, endIndex);
    }


    public DuelArea getEmptyDuelArea(int shipType) {
        Iterator<Entry<Integer, DuelArea>> iter = areas.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<Integer, DuelArea> entry = iter.next();
            DuelArea area = entry.getValue();
            if (!area.inUse && area.isShipAllowed(shipType))
                return area;
        }
        return null;
    }

    public void issueChallenge(String challenger, String challenged, int amount, int ship1, int ship2) {
        boolean moneyActive = context.getMoneySystem().isEnabled();
        boolean openChal = challenged.equals("*");
        Player playerChallenged = m_botAction.getFuzzyPlayer(challenged);
        if (!openChal) {
            if (playerChallenged == null) {
                m_botAction.sendSmartPrivateMessage(challenger, "No such player in the arena.");
                return;
            }
            challenged = playerChallenged.getPlayerName();
        }

        if (isDueling(challenger)) {
            m_botAction.sendSmartPrivateMessage(challenger, "You are already dueling.");
            return;
        }

        if (!openChal) {
            if (isDueling(challenged)) {
                m_botAction.sendSmartPrivateMessage(challenger, "This player is already dueling.");
                return;
            }

            if (noplay.contains(challenged.toLowerCase())) {
                m_botAction.sendSmartPrivateMessage(challenger, "This player is not accepting challenges.");
                return;
            }

        }
        
        String key = challenger.toLowerCase() + "-" + challenged.toLowerCase();
        if (!openChal && spam.containsKey(key) && ((System.currentTimeMillis() - spam.get(key)) < 30 * Tools.TimeInMillis.SECOND)) {
            m_botAction.sendSmartPrivateMessage(challenger, "Please wait 30 seconds before challenging this player again.");
            return;
        }

        PubPlayer pubChallenger = context.getPlayerManager().getPlayer(challenger);
        PubPlayer pubChallenged = context.getPlayerManager().getPlayer(challenged);

        if (pubChallenger == null) {
            m_botAction.sendSmartPrivateMessage(challenger, "Please wait, you are not in the system yet.");
            return;
        }

        if (!openChal && pubChallenged == null) {
            m_botAction.sendSmartPrivateMessage(challenger, "Please wait, " + challenged + " is not in the system yet.");
            return;
        }

        if (!openChal && pubChallenged.isIgnored(challenger)) {
            m_botAction.sendSmartPrivateMessage(challenger, "This player is not accepting challenges from you.");
            return;
        }

        if (isChallengeAlreadySent(challenger, challenged)) {
            if (openChal)
                m_botAction.sendSmartPrivateMessage(challenger, "You already have an open challenge set.");
            else
                m_botAction.sendSmartPrivateMessage(challenger, "You already have a pending challenge with " + challenged + ".");
            m_botAction.sendSmartPrivateMessage(challenger, "Please remove it using !removechallenge before challenging more.");
            return;
        }

        if (ship1 == Tools.Ship.TERRIER || ship2 == Tools.Ship.TERRIER) {
            m_botAction.sendSmartPrivateMessage(challenger, "You cannot duel someone in Terrier.");
            return;
        }

        if (context.getPlayerManager().isShipRestricted(ship1) || context.getPlayerManager().isShipRestricted(ship2)) {
            m_botAction.sendSmartPrivateMessage(challenger, "This ship is restricted in this arena, you cannot duel a player in this ship.");
            return;
        }
        
        if (challenger.equalsIgnoreCase(challenged)) {
            m_botAction.sendSmartPrivateMessage(challenger, "I pity the fool who challenges himself for a duel.");
            return;
        }

        if (moneyActive) {
            if (amount < 10) {
                m_botAction.sendSmartPrivateMessage(challenger, "You must challenge someone for $10 or more.");
                return;
            }
            if (pubChallenger.getMoney() < amount) {
                m_botAction.sendSmartPrivateMessage(challenger, "You don't have enough money.");
                return;
            }
            if (!openChal && pubChallenged.getMoney() < amount) {
                m_botAction.sendSmartPrivateMessage(challenger, challenged + " does not have enough money.");
                return;
            }
        }
        
        // Check MID for TWChat # (catch in Message event)
        m_botAction.sendUnfilteredPrivateMessage( challenger, "*info" );
        m_botAction.sendUnfilteredPrivateMessage( challenged, "*info" );

        if (openChal) {
            m_botAction.sendSmartPrivateMessage(challenger, "Open challenge sent for any player to accept; it will now show in !openduels (!od) for the next " + (TIME_OPEN_CHALS_ACTIVE / Tools.TimeInMillis.MINUTE) + " minutes.");
            String displayStr;
            
            if (ship1==ship2 )
            	displayStr = Tools.shipName(ship1) + (moneyActive ? (" for $" + amount) : "");
            else
            	displayStr = Tools.shipName(ship1) + " (their ship)  vs  " + Tools.shipName(ship2) + " (your ship)  duel" + (moneyActive ? (" for $" + amount) : "");
            openChallenges.put(challenger, displayStr);
            if (amount >= announceOpenChallengeAt)
                m_botAction.sendArenaMessage("[OPEN DUEL] " + challenger + " challenges anyone to " + displayStr + ".  :tw-p:!accept " + challenger );
            context.moneyLog("[OPEN CHALLENGE] " + challenger + " (" + Tools.shipName(ship1) + ") challenged anyone (" + Tools.shipName(ship2) + ") for $" + amount);
        } else {
            if (ship1 == ship2) {
                m_botAction.sendSmartPrivateMessage(challenged, challenger + " has challenged you to duel" + (moneyActive ? (" for $" + amount) : "") + " in "
                        + Tools.shipName(ship1) + ". To accept reply !accept " + challenger);
                m_botAction.sendSmartPrivateMessage(challenged, "Duel to " + deaths + ".");
                m_botAction.sendSmartPrivateMessage(challenger, "Challenge sent to " + challenged + (moneyActive ? (" for $" + amount) : "") + ".");
            } else {
                m_botAction.sendSmartPrivateMessage(challenged, challenger + " has challenged you to a special duel" + (moneyActive ? (" for $" + amount) : "") + " in THEIR "
                        + Tools.shipName(ship1) + " vs YOUR " + Tools.shipName(ship2) +".");
                m_botAction.sendSmartPrivateMessage(challenged, "Duel to " + deaths + ". To accept reply !accept " + challenger);
                m_botAction.sendSmartPrivateMessage(challenger, "Challenge sent to " + challenged + (moneyActive ? (" for $" + amount) : "") + " in your " + Tools.shipName(ship1) + " vs their " + Tools.shipName(ship2) + ".");
            }
            context.moneyLog("[CHALLENGE] " + challenger + " (" + Tools.shipName(ship1) + ") challenged " + challenged  + " (" + Tools.shipName(ship2) + ") for $" + amount);
        }

        final Challenge challenge = new Challenge(amount, ship1, ship2, challenger, challenged, this);
        addChallenge(challenge);
        spam.put(challenger.toLowerCase() + "-" + challenged.toLowerCase(), System.currentTimeMillis());
        if (openChal)
            m_botAction.scheduleTask(new RemoveChallenge(challenge), TIME_OPEN_CHALS_ACTIVE);
        else
            m_botAction.scheduleTask(new RemoveChallenge(challenge), 60 * Tools.TimeInMillis.SECOND);

    }

    public String getKey(Challenge challenge) {
        return challenge.challengerName + "-" + challenge.challengedName;
    }

    public boolean isChallengeAlreadySent(String challenger, String challenged) {
        return challenges.containsKey(challenger + "-" + challenged);
    }

    public void addChallenge(Challenge challenge) {
        if (challenges.containsKey(getKey(challenge)))
            challenges.remove(getKey(challenge));
        challenges.put(getKey(challenge), challenge);
    }

    public void acceptChallenge(String accepter, String challenger) {
        boolean openChal = false;

        // Already playing?
        if (duelers.containsKey(accepter)) {
            m_botAction.sendSmartPrivateMessage(accepter, "You are already dueling.");
            return;
        }

        // Get the real player name
        Player player = m_botAction.getFuzzyPlayer(challenger);
        if (player == null) {
            m_botAction.sendSmartPrivateMessage(accepter, "Player not found.");
            return;
        }
        challenger = player.getPlayerName();

        Challenge challenge = challenges.get(challenger + "-" + accepter);
        if (challenge == null) {
            // Check for open challenge
            challenge = challenges.get(challenger + "-*" );
            if (challenge == null) {
                m_botAction.sendSmartPrivateMessage(accepter, "You don't have a challenge from " + challenger + ".");
                return;
            }
            openChal = true;
        }
        
        if (openChal) {
            player = m_botAction.getPlayer(accepter);
            if (player != null && player.getShipType() == Tools.Ship.SPECTATOR) {
                // XXX: Hack fix for special case of TWChat user accepting open duel
                m_botAction.sendSmartPrivateMessage(accepter, "You cannot accept open challenges unless already in-game. Please enter a ship.");
                return;
            }
            
            if (challenger.equalsIgnoreCase(accepter)) {
                m_botAction.sendSmartPrivateMessage(accepter, "You cannot accept your own challenges.");
                return;
            }
        }
        
        int amount = challenge.amount;
        int ship1 = challenge.ship1;
        int ship2 = challenge.ship2;

        if (context.getMoneySystem().isEnabled()) {
            if (context.getPlayerManager().getPlayer(accepter).getMoney() < amount) {
                if (!openChal) {
                    m_botAction.sendSmartPrivateMessage(accepter, "You don't have enough money to accept the challenge. Challenge removed.");
                    m_botAction.sendSmartPrivateMessage(challenger, accepter + " does not have enough money to accept the challenge. Challenge removed.");
                } else {
                    m_botAction.sendSmartPrivateMessage(accepter, "You don't have enough money to accept that challenge.");
                }
                return;
            }
            if (context.getPlayerManager().getPlayer(challenger).getMoney() < amount) {
                m_botAction.sendSmartPrivateMessage(accepter, challenger + " does not have the money.");
                return;
            }
        }

        // TODO: Integrate into CFG a way to designate arenas for special duels (players using different ships)
        // Instead we use something of a hack for the present moment.
        // Also removed the unnecessary arena check in the !challenge, as it makes no sense to
        // check for an open arena until the other player accepts the challenge.
        DuelArea area;
        if (ship1 == ship2)
            area = getEmptyDuelArea(ship1);
        else {
            if (ship1 == 2 || ship1 == 8 || ship1 == 4 || ship1 == 6)
                area = getEmptyDuelArea(ship1);
            else
                area = getEmptyDuelArea(ship2);
        }
        if (area == null) {
            m_botAction.sendSmartPrivateMessage(accepter, "Unfortunately there is no available duel area. Try again later.");
            m_botAction.sendSmartPrivateMessage(challenger, accepter
                    + " has accepted your challenge. Unfortunately there is no available duel area. Try again later.");
            return;
        } else
            area.setInUse();
        
        if (openChal)
            challenge.challengedName = accepter;
        openChallenges.remove(challenger);

        // Set duelers in the challenge
        Dueler duelerChallenger = new Dueler(challenger, Dueler.DUEL_CHALLENGER, challenge);
        Dueler duelerAccepter = new Dueler(accepter, Dueler.DUEL_ACCEPTER, challenge);
        duelers.put(challenger, duelerChallenger);
        duelers.put(accepter, duelerAccepter);

        challenge.setDuelers(duelerChallenger, duelerAccepter);
        challenge.setArea(area);

        if (ship1 == 0) {
            m_botAction.sendSmartPrivateMessage(challenger, accepter
                    + " has accepted your challenge. You have 10 seconds to get into your dueling ship.");
            m_botAction.sendSmartPrivateMessage(challenger, "Duel to " + deaths + ".");
            m_botAction.sendSmartPrivateMessage(accepter, "Challenge accepted. You have 10 seconds to get into your dueling ship.");
        } else {
            m_botAction.sendSmartPrivateMessage(challenger, accepter + " has accepted your challenge. The duel will start in 10 seconds.");
            m_botAction.sendSmartPrivateMessage(accepter, "Challenge accepted. The duel will start in 10 seconds.");
        }

        context.moneyLog("[ACCEPT] " + accepter + " (" + Tools.shipName(ship2) + ") accepted challenge by " + challenger  + " (" + Tools.shipName(ship1) + ") for $" + amount);
        
        Player playerChallenger = m_botAction.getPlayer(challenger);
        Player playerAccepter = m_botAction.getPlayer(accepter);

        duelerAccepter.oldFreq = playerAccepter.isPlaying() ? playerAccepter.getFrequency() : 0;
        duelerChallenger.oldFreq = playerChallenger.isPlaying() ? playerChallenger.getFrequency() : 0;

        if (playerChallenger.getShipType() == 0)
            m_botAction.setShip(challenger, ship1);
        if (playerAccepter.getShipType() == 0)
            m_botAction.setShip(accepter, ship2);

        /*
        String moneyMessage = "";
        if (context.getMoneySystem().isEnabled())
            moneyMessage = " for $" + amount;

        if (announceNew && amount >= announceZoneWinnerAt)
            m_botAction.sendZoneMessage("[PUB] A duel is starting between " + challenger + " and " + accepter + " in " + Tools.shipName(ship)
                    + moneyMessage + ".", Tools.Sound.BEEP1);
        */
        removePendingChallenge(challenger, false, false);
        removePendingChallenge(accepter, false, false);

        // Prepare the timer, in 15 seconds the game should starts (added 5s to allow more bets)
        m_botAction.scheduleTask(new StartDuel(challenge), 10 * 1000);

    }

    public void watchDuel(final String sender, String command) {

        if (command.contains(" ")) {

            String playerName = command.substring(command.indexOf(" ") + 1).trim();
            Player player = m_botAction.getFuzzyPlayer(playerName);
            if (player == null) {
                m_botAction.sendSmartPrivateMessage(sender, "Player not found.");
                return;
            }
            playerName = player.getPlayerName();

            Dueler dueler = duelers.get(playerName);
            if (dueler == null)
                m_botAction.sendSmartPrivateMessage(sender, playerName + " is not dueling.");
            else if (dueler.challenge.area != null) {

                final int posX = Math.min(dueler.challenge.area.warp1.x, dueler.challenge.area.warp2.x);
                final int posY = dueler.challenge.area.warp1.y;

                final int diff = Math.abs((dueler.challenge.area.warp1.x - dueler.challenge.area.warp2.x) / 2);

                m_botAction.specWithoutLock(sender);
                TimerTask timer = new TimerTask() {
                    @Override
                    public void run() {
                        m_botAction.warpTo(sender, posX + diff, posY);
                    }
                };
                m_botAction.scheduleTask(timer, 1000);

                if (dueler.challenge.accepter.kills == dueler.challenge.challenger.kills)
                    m_botAction.sendSmartPrivateMessage(sender, "Current stat: " + dueler.challenge.accepter.kills + "-"
                            + dueler.challenge.challenger.kills);
                else if (dueler.challenge.accepter.kills < dueler.challenge.challenger.kills)
                    m_botAction.sendSmartPrivateMessage(sender, "Current stat: " + dueler.challenge.challenger.kills + "-"
                            + dueler.challenge.accepter.kills + ", " + dueler.challenge.challenger.name + " leading.");
                else
                    m_botAction.sendSmartPrivateMessage(sender, "Current stat: " + dueler.challenge.accepter.kills + "-"
                            + dueler.challenge.challenger.kills + ", " + dueler.challenge.accepter.name + " leading.");

            }
        }

    }

    public void placeBet(String name, String cmd) {
        if (!allowBets) {
            m_botAction.sendPrivateMessage(name, "Betting has been DISABLED for the time being.");
            return;
        }
        if (context.getPubChallenge().isDueling(name)) {
            m_botAction.sendSmartPrivateMessage(name, "You cannot place bets while dueling.");
            return;/*These three lines have been placed to prevent exploitation of the dueling and betting system - Dral <ZH> */
        }
        if (!context.getMoneySystem().isEnabled()) {
            m_botAction.sendPrivateMessage(name, "[ERROR]  Please provide both name and amount to bet.  Example:  !beton qan:299");
            return;
        }

        String[] cmds = cmd.split(":");
        if (cmds.length != 2)
            m_botAction.sendPrivateMessage(name, "[ERROR]  Please provide both name and amount to bet.  Example:  !beton qan:299");
        boolean bettingChallenger = false;
        Challenge foundDuel = null;

        String searchName = m_botAction.getFuzzyPlayerName(cmds[0]);
        if (searchName == null) {
            m_botAction.sendPrivateMessage(name, "[SNARKY ERROR]  Sorry, the player you have dialed is either disconnected or is no longer in service.  Please check the name, then hang up and try again.");
            return;
        }

        if (!duelers.containsKey(searchName)) {
            m_botAction.sendPrivateMessage(name, "[ERROR]  Dueler not found.");
            return;
        }

        Challenge c = duelers.get(searchName).challenge;

        if (c.challengerName.equalsIgnoreCase(name) || c.challengedName.equalsIgnoreCase(name)) {
            m_botAction.sendPrivateMessage(name, "[ERROR]  You can't bet on your own duel! Nice try though.");
            return;
        }

        if (c != null)
            if (c.challengerName != null && c.challengedName != null)
                if (searchName.equalsIgnoreCase(c.getChallenger())) {
                    foundDuel = c;
                    bettingChallenger = true;
                } else if (searchName.equalsIgnoreCase(c.getChallenged())) {
                    foundDuel = c;
                    bettingChallenger = false;
                }

        if (foundDuel == null) {
            m_botAction.sendPrivateMessage(name, "[ERROR]  Either duel for '" + searchName
                    + "' has not yet started, a player has not accepted the challenge, or the challenge does not exist.");
            return;
        }

        if (!foundDuel.canBet()) {
            m_botAction.sendPrivateMessage(name, "[ERROR]  Betting time has passed (1 minute after duel start) OR duel over/already started.");
            return;
        }

        Integer amt = 0;
        try {
            amt = Integer.decode(cmds[1]);
            if (amt < minBet) {
                m_botAction.sendPrivateMessage(name, "[ERROR]  Your bet must be at least $" + minBet + ".");
                return;
            }
        } catch (Exception e) {
            m_botAction.sendPrivateMessage(name, "[ERROR]  Found player, but can't read the amount you have bet.  Usage: !beton <name>:<$amount>");
            return;
        }
        
        if (amt > 10000) {
            m_botAction.sendPrivateMessage(name, "You may not bet more than 10000 at a time on any given duel.");
            return;
        }

        PubPlayer bettor = context.getPlayerManager().getPlayer(name);
        if (!(bettor != null && bettor.getMoney() >= amt)) {
            m_botAction.sendPrivateMessage(name, "[ERROR]  You don't have the kind of cash to just throw around on idle bets, friend.  (You have $"
                    + bettor.getMoney() + " available to bet.)");
            return;
        }

        if (!foundDuel.betOnDueler(name, bettor, bettingChallenger, amt))
            m_botAction.sendPrivateMessage(name, "[ERROR]  Couldn't finalize bet.  (You are not allowed to be in the duel you're betting on, or bet on both players.)");
        else
            m_botAction.sendPrivateMessage(name, "[OK!]  Bet for $" + amt + " deducted from your account and placed on " + searchName
                    + ". To win any money, another player will need to place an opposing bet. Good luck! "
                    + "(NOTE: stay in pub until duel is finished or your bet will be lost!)");

    }

    public void removePendingChallenge(String name, boolean tellPlayer, boolean forTWChat ) {
        Iterator<Challenge> it = challenges.values().iterator();
        int totalRemoved = 0;
        while (it.hasNext()) {
            Challenge c = it.next();
            if (c.challengerName.equals(name))
                if (!c.isStarted()) {
                    if (tellPlayer)
                        if( c.challengedName.equals("*"))
                            m_botAction.sendSmartPrivateMessage(name, "Open challenge removed." + (forTWChat ? "" : " TWChat users may not challenge."));
                        else
                            m_botAction.sendSmartPrivateMessage(name, "Challenge against " + c.challengedName + " removed." + (forTWChat ? "" : " TWChat users may not challenge."));                            
                    it.remove();
                    totalRemoved++;
                } else if( forTWChat ) {
                    // TODO: Cancel even after it's started. Methods are a bit messy though.
                    // This is necessary for TWChat users who accept open duels.
                    // Hack fix: to accept an open duel, you now have to be in ship already.
                }
        }
        openChallenges.remove(name);

        if (totalRemoved == 0) {
            if (tellPlayer && !forTWChat)
                m_botAction.sendSmartPrivateMessage(name, "No pending challenge to remove.");
        } else {
            if (tellPlayer && !forTWChat)
                m_botAction.sendSmartPrivateMessage(name, "Removed " + totalRemoved + "challenges.");            
        }
    }
    
    public void removeChallengesTo(String name, boolean tellPlayer, boolean forTWChat ) {
        Iterator<Challenge> it = challenges.values().iterator();
        while (it.hasNext()) {
            Challenge c = it.next();
            if (c.challengedName.equals(name))
                if (!c.isStarted()) {
                    if (tellPlayer) {
                        m_botAction.sendSmartPrivateMessage(c.challengerName, "Challenge against " + c.challengedName + " removed." + (forTWChat ? "" : " TWChat users may not be challenged."));
                        m_botAction.sendSmartPrivateMessage(name, "Challenge from " + c.challengerName + " removed." + (forTWChat ? "" : " TWChat users may not accept challenges."));
                    }
                    it.remove();
                }
        }
        openChallenges.remove(name);
    }


    public Challenge getChallengeStartedByPlayerName(String name) {
        Iterator<Challenge> it = challenges.values().iterator();
        //int totalRemoved = 0;
        while (it.hasNext()) {
            Challenge c = it.next();
            if (c.isStarted())
                if (c.challengedName.equals(name) || c.challengerName.equals(name))
                    return c;
        }
        return null;
    }

    private void announceWinner(Challenge challenge) {
        if (challenge == null || !challenge.hasEnded())
            throw new RuntimeException("You need to set a winner or a loser! setWinner()/setLoser()");

        boolean cancelled = false;

        Dueler winner = challenge.winner;
        Dueler loser = challenge.loser;

        int winnerKills = winner.kills;
        int loserKills = loser.kills;

        int money = challenge.amount;

        String moneyMessage = "";
        if (context.getMoneySystem().isEnabled())
            moneyMessage = " for $" + money;

        if (laggers.containsKey(winner.name) && laggers.containsKey(loser.name)) {
            StartLagout l1 = laggers.get(winner.name);
            StartLagout l2 = laggers.get(loser.name);
            l1.cancelLagout();
            l2.cancelLagout();
            m_botAction.cancelTask(l1);
            m_botAction.cancelTask(l2);
            m_botAction.sendSmartPrivateMessage(winner.name, "Your duel against " + loser.name + " has been cancelled, both lagout/specced.");
            m_botAction.sendSmartPrivateMessage(loser.name, "Your duel against " + winner.name + " has been cancelled, both lagout/specced.");
            context.moneyLog("[DUEL END] Duel cancelled between " + winner.name +" and " + loser.name + " due to lagout/spec.");
            cancelled = true;
            challenge.returnAllBets(context.getPlayerManager());
        } else if (challenge.winByLagout) {
            if (announceWinner && challenge.amount >= announceZoneWinnerAt)
                m_botAction.sendZoneMessage("[PUBDUEL] " + winner.name + " has defeated " + loser.name + " (by lagout) in duel" + moneyMessage + "!");
            else if (announceWinner && challenge.amount >= announceWinnerAt)
                m_botAction.sendArenaMessage("[DUEL] " + winner.name + " has defeated " + loser.name + " (by lagout) in duel" + moneyMessage + ".");
            else {
                m_botAction.sendSmartPrivateMessage(winner.name, "You have defeated " + loser.name + " (by lagout) in duel" + moneyMessage + ".");
                m_botAction.sendSmartPrivateMessage(loser.name, "You have lost to " + winner.name + " (by lagout) in duel" + moneyMessage + ".");
            }

            context.moneyLog("[DUEL END] Duel won by " + winner.name +" vs " + loser.name + " due to lagout/spec " + moneyMessage + ".");
            
            StartLagout lagger = laggers.remove(loser.name);
            if (lagger != null) {
                lagger.cancelLagout();
                m_botAction.cancelTask(lagger);
            }
            challenge.settleAllBets(winner.name,context.getPlayerManager());

        } else {
            if (announceWinner && money >= announceZoneWinnerAt)
                m_botAction.sendZoneMessage("[PUBDUEL] " + winner.name + " has defeated " + loser.name + " " + winnerKills + "-" + loserKills
                        + " in duel" + moneyMessage + "!");
            else if (announceWinner && money >= announceWinnerAt)
                m_botAction.sendArenaMessage("[DUEL] " + winner.name + " has defeated " + loser.name + " " + winnerKills + "-" + loserKills
                        + " in duel" + moneyMessage + "!");
            else {
                m_botAction.sendSmartPrivateMessage(winner.name, "You have defeated " + loser.name + " " + winnerKills + "-" + loserKills
                        + " in duel" + moneyMessage + ".");
                m_botAction.sendSmartPrivateMessage(loser.name, "You have lost to " + winner.name + " " + loserKills + "-" + winnerKills + " in duel"
                        + moneyMessage + ".");
            }
            context.moneyLog("[DUEL END] Duel won by " + winner.name +" vs " + loser.name + moneyMessage +". Result: " + winnerKills + "-" + loserKills + ".");
            challenge.settleAllBets(winner.name, context.getPlayerManager());
        }

        // Free the area
        challenge.area.free();

        // Give/Remove money
        if (!cancelled && context.getMoneySystem().isEnabled()) {
            context.getPlayerManager().addMoney(winner.name, money, true);
            context.getPlayerManager().removeMoney(loser.name, money, true);
        }

        // Setting the frequency before
        if (duelers.containsKey(winner.name))
            m_botAction.setFreq(winner.name, duelers.get(winner.name).oldFreq);
        if (duelers.containsKey(loser.name))
            m_botAction.setFreq(loser.name, duelers.get(loser.name).oldFreq);

        // Removing stuff
        duelers.remove(winner.name);
        if (challenge.winByLagout)
            duelers.remove(loser.name);
        challenges.remove(getKey(challenge));
        StartLagout lagger = laggers.remove(winner.name);
        if (lagger != null) {
            lagger.cancelLagout();
            m_botAction.cancelTask(lagger);
        }
        lagger = laggers.remove(loser.name);
        if (lagger != null) {
            lagger.cancelLagout();
            m_botAction.cancelTask(lagger);
        }

        warpToSafe(winner.name, true);
        warpToSafe(loser.name, false);

        if (saveDuel && !cancelled) {

            String[] fields = { "fcNameChallenger", "fcNameAccepter", "fcWinner", "fnScoreChallenger", "fnScoreAccepter", "fnShip", "fnWinByLagout",
                    "fnDuration", "fnMoney", "fdDate" };

            String[] values = { Tools.addSlashes(winner.type == Dueler.DUEL_CHALLENGER ? winner.name : loser.name),
                    Tools.addSlashes(loser.type == Dueler.DUEL_ACCEPTER ? loser.name : winner.name), Tools.addSlashes(winner.name),
                    String.valueOf(winner.type == Dueler.DUEL_CHALLENGER ? winner.kills : loser.kills),
                    String.valueOf(loser.type == Dueler.DUEL_ACCEPTER ? loser.kills : winner.kills),
                    (challenge.ship1==challenge.ship2 ? String.valueOf(challenge.ship1) : String.valueOf(challenge.ship1) + String.valueOf(challenge.ship2)),
                    String.valueOf(challenge.winByLagout ? 1 : 0), String.valueOf((int) ((System.currentTimeMillis() - challenge.startAt) / 1000)),
                    String.valueOf(money), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) };

            m_botAction.SQLBackgroundInsertInto(database, "tblDuel", fields, values);
        }

    }

    private void notDueling(String name) {
        if (noplay.remove(name.toLowerCase()))
            m_botAction.sendPrivateMessage(name, "You will now receive dueling challenges.");
        else {
            noplay.add(name.toLowerCase());
            m_botAction.sendPrivateMessage(name, "You will no longer receive dueling challenges.");
        }
    }

    private void givePrize(String name) {

        m_botAction.shipReset(name);
        m_botAction.specificPrize(name, Tools.Prize.FULLCHARGE);
        m_botAction.specificPrize(name, Tools.Prize.MULTIFIRE);
        m_botAction.specificPrize(name, -Tools.Prize.ROCKET); // NEGATIVE ROCKET
        m_botAction.specificPrize(name, -Tools.Prize.BRICK); // NEGATIVE BRICK
        m_botAction.specificPrize(name, -Tools.Prize.PORTAL); // NEGATIVE PORT (for Levs)
        if (!sharkShrap) {
            m_botAction.specificPrize(name, -Tools.Prize.SHRAPNEL); // NEGATIVE SHRAPNEL
            m_botAction.specificPrize(name, -Tools.Prize.SHRAPNEL);
            m_botAction.specificPrize(name, -Tools.Prize.SHRAPNEL);
        }

    }

    private class RemoveChallenge extends TimerTask {

        private Challenge challenge;

        public RemoveChallenge(Challenge c) {
            this.challenge = c;
        }

        @Override
        public void run() {
            if (challenge == null)
                return;
            if (!challenge.isStarted()) {
                challenges.remove(getKey(challenge));
                m_botAction.sendSmartPrivateMessage(challenge.challengerName, "Challenge against " + challenge.challengedName + " removed. (timeout)");
            }
        }
    }

    private void refreshSpamStopper() {
        Long now = System.currentTimeMillis();
        Vector<String> removes = new Vector<String>();
        for (String k : spam.keySet())
            if (now - spam.get(k) > Tools.TimeInMillis.MINUTE)
                removes.add(k);

        while (!removes.isEmpty())
            spam.remove(removes.remove(0));
    }

    private class ResetMines extends TimerTask {

        String name;

        private ResetMines(String name) {
            this.name = name;
        }

        @Override
        public void run() {

            Dueler dueler = duelers.get(name);
            if (dueler == null || dueler.challenge == null)
                return;

            if (dueler != null) {
                m_botAction.shipReset(name);
                m_botAction.warpTo(name, coordsSafe);
            }
        }

    }


    private class SpawnBack extends TimerTask {

        String name;

        private SpawnBack(String name) {
            this.name = name;
        }

        @Override
        public void run() {

            Dueler dueler = duelers.get(name);
            if (dueler == null || dueler.challenge == null)
                return;

            Challenge challenge = dueler.challenge;

            if (dueler != null) {

                if (dueler.type == 1)
                    m_botAction.warpTo(name, challenge.area.warp1);
                else if (dueler.type == 2)
                    m_botAction.warpTo(name, challenge.area.warp2);
                givePrize(name);
            }
        }
    }

    private class EnergyDepletedTask extends TimerTask {

        String name;

        private EnergyDepletedTask(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            Dueler dueler = duelers.get(name);
            if (dueler != null)
                m_botAction.specificPrize(name, Tools.Prize.ENERGY_DEPLETED);
        }
    }

    private class UpdateScore extends TimerTask {
        private Dueler killer;
        private Dueler killed;

        private UpdateScore(Dueler killer, Dueler killed) {
            this.killer = killer;
            this.killed = killed;
        }

        @Override
        public void run() {

            // If a kill occurred between starting and running this TimerTask, then don't count it.
            if (Math.abs(killer.lastDeath - killed.lastDeath) < 1 * Tools.TimeInMillis.SECOND) {
                m_botAction.sendSmartPrivateMessage(killer.name, "No count");
                return;

            } else if (Math.abs(killed.lastKill - killer.lastKill) < 5 * Tools.TimeInMillis.SECOND) {
                // In this scenario, the TimerTask of the previous kill has already been fired, and adjusted the score.
                // Since this is a no count scenario, we will have to lower the scores of both players.
                killed.kills--;
                killer.deaths--;
                m_botAction.sendSmartPrivateMessage(killer.name, "No count, back to " + killer.kills + "-" + killed.kills);
                m_botAction.sendSmartPrivateMessage(killed.name, "No count, back to " + killed.kills + "-" + killer.kills);
                return;

            } else {

                killer.kills++;
                killed.deaths++;

                m_botAction.sendSmartPrivateMessage(killer.name, killer.kills + "-" + killed.kills);
                m_botAction.sendSmartPrivateMessage(killed.name, killed.kills + "-" + killer.kills);

                if (killed.deaths == deaths) {
                    killed.challenge.setWinner(killer);
                    announceWinner(killed.challenge);
                    return;
                }
            }

        }
    }

    private class StartLagout extends TimerTask {
        private String name;
        private boolean cancelled = false;

        private StartLagout(String name) {
            this.name = name;
        }
        
        private void cancelLagout() {
            cancelled = true;
        }

        @Override
        public void run() {
            Dueler dueler = duelers.get(name);
            if (dueler == null || cancelled)
                return;

            Challenge challenge = dueler.challenge;
            challenge.setLoser(duelers.get(name));
            challenge.getOppositeDueler(duelers.get(name)).kills = deaths;
            challenge.setWinByLagout();
            announceWinner(challenge);
        }
    }

    private class StartDuel extends TimerTask {
        private Challenge challenge;
        private String challenger, accepter;
        private DuelArea area;
        private int ship1;
        private int ship2;
        private Random random;

        private StartDuel(Challenge challenge) {
            this.challenge = challenge;
            this.ship1 = challenge.ship1;
            this.ship2 = challenge.ship2;
            this.area = challenge.area;
            this.challenger = challenge.challenger.name;
            this.accepter = challenge.accepter.name;
            refreshSpamStopper();
            
            // Placing this code here (before duel starts) to encourage more betting
            if (allowBets) {
                String amtString = (challenge.amount >= 250 ? "$" + challenge.amount : "peanuts"); 
                if (ship1==ship2) {
                    m_botAction.sendArenaMessage( "- [" + Tools.shipNameSlang(ship1).toUpperCase() + " DUEL] - " + challenger + " vs " + accepter
                            + " for " + amtString +". Betting closes soon. Use :tw-p:!beton <name>:<$>");
                } else {
                    m_botAction.sendArenaMessage( "- [" + Tools.shipNameSlang(ship1).toUpperCase() + " vs " +  Tools.shipNameSlang(ship2).toUpperCase() + " DUEL] - " + challenger + " vs " + accepter
                            + " for " + amtString +". Betting closes soon. Use :tw-p:!beton <name>:<$>");                    
                }
            }
        }

        @Override
        public void run() {
            challenge.start();

            Player p_chall = m_botAction.getPlayer(challenger);
            Player p_acc = m_botAction.getPlayer(accepter);

            if (p_chall == null || p_acc == null) {
                duelers.remove(challenge.challengerName);
                duelers.remove(challenge.challengedName);
                challenges.remove(getKey(challenge));
                StartLagout l = laggers.remove(challenge.challengerName);
                if (l != null)
                    l.cancelLagout();
                l = laggers.remove(challenge.challengedName);
                if (l != null)
                    l.cancelLagout();
                area.free();
                if (p_chall == null)
                    m_botAction.sendSmartPrivateMessage(accepter, "The duel cannot start, " + challenger + " not found.");
                else
                    m_botAction.sendSmartPrivateMessage(challenger, "The duel cannot start, " + accepter + " not found.");
                return;
            }

            int ichalship = p_chall.getShipType();
            int iaccship = p_acc.getShipType();

            // Player in spec? specify a ship
            if (ichalship == 0) {
                ichalship = ship1 == 0 ? 1 : ship1;
                m_botAction.setShip(challenger, ichalship);
            }
            if (iaccship == 0) {
                iaccship = ship2 == 0 ? 1 : ship2;
                m_botAction.setShip(accepter, iaccship);
            }

            // Set the player in the dueling ship
            if (ship1 > 0 && ichalship != ship1)
                m_botAction.setShip(challenger, ship1);
            if (ship2 > 0 && iaccship != ship2)
                m_botAction.setShip(accepter, ship2);

            random = new Random();

            //private frequency range (minus 1 for other dueler)
            long range = (long) 9997 - (long) 100 + 1;
            int freq1 = 0;
            int freq2 = 0;

            while (freq1 == 0) {
                long fraction = (long) (range * random.nextDouble());
                freq1 = (int) (fraction + 100);
                if (m_botAction.getFrequencySize(freq1) > 0)
                    freq1 = 0;
            }

            while (freq2 == 0) {
                long fraction = (long) (range * random.nextDouble());
                freq2 = (int) (fraction + 100);
                if (m_botAction.getFrequencySize(freq2) > 0 || freq2 == freq1)
                    freq2 = 0;
            }

            duelers.get(challenger).setFreq(freq1);
            duelers.get(accepter).setFreq(freq2);
            m_botAction.setFreq(challenger, freq1);
            m_botAction.setFreq(accepter, freq2);
            m_botAction.warpTo(challenger, area.warp1);
            m_botAction.warpTo(accepter, area.warp2);
            givePrize(challenger);
            givePrize(accepter);
            
            m_botAction.sendSmartPrivateMessage(challenger, "GO GO GO!", Tools.Sound.GOGOGO);
            m_botAction.sendSmartPrivateMessage(accepter, "GO GO GO!", Tools.Sound.GOGOGO);
        }
    }

    private void warpToSafe(String name, boolean winner) {
        m_botAction.warpTo(name, coordsSafe);
    }

    public boolean hasChallenged(String name) {
        Iterator<Challenge> it = challenges.values().iterator();
        while (it.hasNext()) {
            Challenge c = it.next();
            if (!c.isStarted() && c.challengerName.equals(name))
                return true;
        }
        return false;
    }

    /**
     * Case-sensitive
     */
    public boolean isDueling(String name) {
        return duelers.containsKey(name);
    }

    public void returnFromLagout(String name) {

        if (!laggers.containsKey(name)) {
            m_botAction.sendSmartPrivateMessage(name, "You have not lagged out from a duel.");
            return;
        } else {
            StartLagout l = laggers.remove(name);
            l.cancelLagout();
            m_botAction.cancelTask(l);
        }

        Dueler dueler = duelers.get(name);
        if (dueler == null)
            return;

        Challenge challenge = dueler.challenge;

        dueler.backFromLagout = System.currentTimeMillis();

        if (dueler.type == Dueler.DUEL_CHALLENGER) {
            m_botAction.setShip(name, challenge.ship1);
            m_botAction.setFreq(name, getFreq());
            m_botAction.warpTo(name, challenge.area.warp1);
            // Warp other player so they can't just go to where the first player spawns
            m_botAction.warpTo(dueler.challenge.challengedName, challenge.area.warp2);
        } else {
            m_botAction.setShip(name, challenge.ship2);
            m_botAction.setFreq(name, getFreq());
            m_botAction.warpTo(name, challenge.area.warp2);
            // Warp other player so they can't just go to where the first player spawns
            m_botAction.warpTo(dueler.challenge.challengerName, challenge.area.warp1);
        }
        givePrize(name);
    }
    
    private int getFreq() {
        Random r = new Random();
        int freq = -1;
        while (freq < 0) {
            freq = r.nextInt(9999);
            if (m_botAction.getFrequencySize(freq) > 0)
                freq = -1;
        }
        return freq;
    }

    public String getRealName(String name) {
        PubPlayer player = context.getPlayerManager().getPlayer(name);
        if (player != null)
            return player.getPlayerName();
        return null;
    }

    public void doDebugCmd(String sender) {

        int count = 0;
        for (Challenge c : challenges.values()) {

            String status = c.isStarted() ? "STARTED" : "PENDING";

            Dueler d1 = null;
            Dueler d2 = null;
            if (c.isStarted()) {
                d1 = c.challenger;
                d2 = c.accepter;
            }

            int k1 = d1 == null ? -1 : d1.kills;
            int k2 = d2 == null ? -1 : d2.kills;

            m_botAction.sendSmartPrivateMessage(sender, "Challenge [" + status + "] (" + k1 + ":" + k2 + ")");
            count++;
        }
        for (Dueler dueler : duelers.values()) {
            String status = dueler.challenge.isStarted() ? "STARTED" : "PENDING";
            m_botAction.sendSmartPrivateMessage(sender, "Dueler [" + status + "] " + dueler.name);
            count++;
        }
        if (count == 0)
            m_botAction.sendSmartPrivateMessage(sender, "Nothing.");

    }

    public void doCancelDuelCmd(String sender, String onePlayer) {

        String name = getRealName(onePlayer);

        Iterator<Challenge> it = challenges.values().iterator();
        Challenge challenge = null;
        while (it.hasNext()) {
            Challenge c = it.next();
            if (c.isStarted())
                if (c.challengedName.equals(sender) || c.challengerName.equals(sender)) {
                    challenge = c;
                    break;
                }
        }

        if (challenge != null) {
            String opponent = challenge.getOppositeDueler(name).name;
            challenge.area.free();
            challenges.remove(getKey(challenge));
            duelers.remove(name);
            duelers.remove(opponent);
            StartLagout l = laggers.remove(name);
            if (l != null)
                l.cancelLagout();
            l = laggers.remove(opponent);
            if (l != null)
                l.cancelLagout();
            warpToSafe(name, true);
            warpToSafe(opponent, false);
            m_botAction.sendSmartPrivateMessage(name, "Your duel has been cancelled by " + sender);
            m_botAction.sendSmartPrivateMessage(opponent, "Your duel has been cancelled by " + sender);
        } else
            m_botAction.sendSmartPrivateMessage(sender, "No duel found associated with this player.");

    }

    @Override
    public void handleCommand(String sender, String command) {

        if (command.startsWith("!challenge ") || command.startsWith("!ch ") || command.startsWith("!duel ")) {
            if (context.getMoneySystem().isEnabled()) {
                parseChallenge( sender, command, false );
            } else {
                parseChallenge( sender, command.concat(":0"), false );
            }
        } else if (command.startsWith("!chspecial ") ) {
            if (context.getMoneySystem().isEnabled()) {
                parseChallenge( sender, command, true );
            } else {
                parseChallenge( sender, command.concat(":0"), true );
            }
        } else if (command.startsWith("!accept ")) {
            if (command.length() > 8)
                acceptChallenge(sender, command.substring(8));
        } else if (command.startsWith("!watchduel") || command.startsWith("!wd"))
            watchDuel(sender, command);
        else if (command.startsWith("!beton "))
            placeBet(sender, command.substring(7));
        else if (command.startsWith("!removechallenge") || command.equalsIgnoreCase("!rm"))
            removePendingChallenge(sender, true, false);
        else if (command.equalsIgnoreCase("!lagout"))
            returnFromLagout(sender);
        else if (command.equalsIgnoreCase("!od") || command.equalsIgnoreCase("!openduels"))
            listOpenDuels(sender, true);
        else if (command.equalsIgnoreCase("!ld") || command.equalsIgnoreCase("!duels"))
            listDuels(sender);
        else if (command.equals("!npduel"))
            notDueling(sender);
        else if (command.startsWith("!ignore "))
            doIgnorePlayer(sender, command);
        else if (command.equals("!ignores"))
            getIgnoredPlayers(sender);
    }
    
    
    public void parseChallenge( String sender, String command, boolean isSpecialChallenge ) {
        String pieces[];
        String opponent = "";
        boolean openChal = false;
        Player p = null;
        try {
            pieces = command.substring(command.indexOf(" ") + 1).split(":");
            if (pieces[0].equals("*"))
                openChal = true;
            else
                p = m_botAction.getFuzzyPlayer(pieces[0]);
        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(sender, "Error in command; try again.");
            return;
        }
        if (p != null)
            opponent = p.getPlayerName();

        // Check if we aren't challenging a bot.
        if(m_botAction.getOperatorList().isBotExact(opponent)) {
            m_botAction.sendSmartPrivateMessage(sender, "You cannot challenge a bot.");
            return;
        }
        
        if (!isSpecialChallenge) {
            if (pieces.length == 3 ) {
                if (!openChal) {
                    PubPlayer player = context.getPlayerManager().getPlayer(opponent);
                    if (player == null) {
                        m_botAction.sendSmartPrivateMessage(sender, "Player not on the system yet.");
                        return;
                    } else
                        opponent = player.getPlayerName();
                } else {
                    opponent = "*";
                }

                try {
                    int ship = Integer.parseInt(pieces[1]);
                    int amount = Integer.parseInt(pieces[2]);
                    if (ship >= 1 && ship <= 8)
                        issueChallenge(sender, opponent, amount, ship, ship);
                    else
                        m_botAction.sendSmartPrivateMessage(sender, "Ship must be a number between 1 and 8.");
                } catch (NumberFormatException e) {
                    m_botAction.sendSmartPrivateMessage(sender, "Proper use is !challenge name:ship" + (context.getMoneySystem().isEnabled() ? ":amount" : "") );
                }
            } else {
                m_botAction.sendSmartPrivateMessage(sender, "Proper use is !challenge name:ship" + (context.getMoneySystem().isEnabled() ? ":amount" : "") );
            }
        } else {
            if (pieces.length == 4 ) {
                if (!openChal) {
                    PubPlayer player = context.getPlayerManager().getPlayer(opponent);
                    if (player == null) {
                        m_botAction.sendSmartPrivateMessage(sender, "Player not on the system yet.");
                        return;
                    } else
                        opponent = player.getPlayerName();
                } else {
                    opponent = "*";
                }

                try {
                    int ship = Integer.parseInt(pieces[1]);
                    int ship2 = Integer.parseInt(pieces[2]);
                    int amount = Integer.parseInt(pieces[3]);
                    if ((ship >= 1 && ship <= 8) && (ship2 >= 1 && ship2 <= 8))
                        issueChallenge(sender, opponent, amount, ship, ship2);
                    else
                        m_botAction.sendSmartPrivateMessage(sender, "Ships must both be a number between 1 and 8.");
                } catch (NumberFormatException e) {
                    m_botAction.sendSmartPrivateMessage(sender, "Proper use is !challenge name:ship" + (context.getMoneySystem().isEnabled() ? ":amount" : "") );
                }
            } else {
                m_botAction.sendSmartPrivateMessage(sender, "Proper use is !challenge name:ship" + (context.getMoneySystem().isEnabled() ? ":amount" : "") );
            }
        }
    }
    

    public void getIgnoredPlayers(String name) {
        PubPlayer p = context.getPlayerManager().getPlayer(name);
        if (p != null)
            p.getIgnores();
    }

    public void doIgnorePlayer(String sender, String cmd) {
        cmd = cmd.substring(cmd.indexOf(" ") + 1);
        if (cmd.length() < 1)
            return;
        PubPlayer p = context.getPlayerManager().getPlayer(sender);
        if (p != null)
            p.ignorePlayer(cmd);
        else
            m_botAction.sendPrivateMessage(sender, "Error processing your request.");
    }

    public void doToggleBets(String name) {
        if (allowBets) {
            allowBets = false;
            m_botAction.sendPrivateMessage(name, "Betting has been DISABLED.");
        } else {
            allowBets = true;
            m_botAction.sendPrivateMessage(name, "Betting has been ENABLED.");
        }
    }

    public void listDuels(String name) {
        LinkedList<String> ops = new LinkedList<String>();
        for (Dueler d : duelers.values())
            if (!ops.contains(d.name) && d.challenge.isStarted()) {
                ops.add(d.challenge.getOppositeDueler(d.name).name);
                ops.add(d.name);

                String timeStr = Tools.getTimeDiffString(d.challenge.startAt, true);
                String better = "";
                String betted = "";                
                if (!d.challenge.challengerBets.isEmpty())
                    better = " ($" + d.challenge.getTotalChallengerBets() + " bet)";
                if (!d.challenge.challengedBets.isEmpty())
                    betted = " ($" + d.challenge.getTotalChallengedBets() + " bet)";
                if (d.challenge.ship1==d.challenge.ship2) {
                    m_botAction.sendSmartPrivateMessage(name, "" + d.challenge.challengerName + better + " vs " + d.challenge.challengedName + betted
                            + " in " + Tools.shipName(d.challenge.ship1) + ": " + d.challenge.challenger.kills + "-" + d.challenge.accepter.kills + "  [" + timeStr + "]" );
                } else {
                    m_botAction.sendSmartPrivateMessage(name, "" + d.challenge.challengerName + better + " vs " + d.challenge.challengedName + betted
                            + " in " + Tools.shipName(d.challenge.ship1) + " vs " + Tools.shipName(d.challenge.ship2) + ": " + d.challenge.challenger.kills + "-" + d.challenge.accepter.kills  + "  [" + timeStr + "]" );                    
                }
            }

        if (ops.isEmpty()) {
            m_botAction.sendSmartPrivateMessage(name, "There are currently no active duels.");
        }
        listOpenDuels(name,false);
    }
    
    /**
     * Lists all open challenges available.
     * @param name
     */
    public void listOpenDuels(String name, boolean pmIfNone) {
        if (openChallenges.size() < 1) {
            if (pmIfNone)
                m_botAction.sendSmartPrivateMessage(name, "No open challenges found. To issue your own, type !challenge *:ship#:money");            
        } else {
            for (String challenger : openChallenges.keySet())            	
                m_botAction.sendSmartPrivateMessage(name, challenger + ": " + openChallenges.get(challenger));
        }
    }

    public void doSharkShrap(String name) {
        if (sharkShrap) {
            sharkShrap = false;
            m_botAction.sendSmartPrivateMessage(name, "Shark duels now have shrapnel DISABLED");
        } else {
            sharkShrap = true;
            m_botAction.sendSmartPrivateMessage(name, "Shark duels now have shrapnel ENABLED");
        }
    }

    @Override
    public void handleModCommand(String sender, String command) {

        try {
            if (command.equalsIgnoreCase("!betting"))
                doToggleBets(sender);
            if (command.startsWith("!cancelchallenge"))
                doCancelDuelCmd(sender, command.substring(16).trim());
            else if (command.startsWith("!debugchallenge"))
                doDebugCmd(sender);
            else if (command.startsWith("!sharkshrap"))
                doSharkShrap(sender);

        } catch (RuntimeException e) {
            if (e != null && e.getMessage() != null)
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
    }

    @Override
    public void handleSmodCommand(String sender, String command) {
        if (command.startsWith("!info "))
            doSuperInfo(sender, command);
    }

    @Override
    public String[] getHelpMessage(String sender) {
        if (context.getMoneySystem().isEnabled())
            return new String[] {
                    pubsystem.getHelpLine("!challenge <name>:<ship>:<$>  -- Challenge NAME to " + deaths
                            + " in SHIP (1-8) for $. (!duel)"),
                    pubsystem.getHelpLine("!chspecial <name>:<ship>:<ship2>:<$> -- As above, but in your ship vs their ship2."),
                    pubsystem.getHelpLine("!duel *:<ship>:<$>            -- Duel ANYONE in SHIP for $ (!openduels to list all)" ),
                    //pubsystem.getHelpLine("!watchduel <name>             -- Watch the duel of this player. (!wd)"),
                    pubsystem.getHelpLine("!removechallenge              -- Cancel all challenges sent. (!rm)"),
                    pubsystem.getHelpLine("!duels                        -- Lists the duels currently being played. (!ld)"),
                    pubsystem.getHelpLine("!openduels                    -- Lists all open duels you can accept. (!od)"),
                    pubsystem.getHelpLine("!beton <name>:<$>             -- Bet on <name> to win a duel."),
                    pubsystem.getHelpLine("!watchduel <name>             -- Displays the score of <names>'s duel. (!wd)"),
                    pubsystem.getHelpLine("!npduel                       -- Enables or disables you from receiving duel challenges."),
                    pubsystem.getHelpLine("!ignore <name>                -- Prevents/allows <name> from challenging you to duels."),
                    pubsystem.getHelpLine("!ignores                      -- Lists who you are currently ignoring challenges from."), };
        else
            return new String[] { pubsystem.getHelpLine("!duels                        -- Lists the duels currently being played. (!ld)"),
                    pubsystem.getHelpLine("!challenge <name>:<ship>      -- Challenge NAME to " + deaths + " in SHIP (1-8). (!duel)"),
                    pubsystem.getHelpLine("!chspecial <name>:<ship>:<ship2> -- As above, but in your ship vs their ship2."),
                    pubsystem.getHelpLine("!duel *:<ship>                -- Duel ANYONE in SHIP (!openduels to list all)" ),
                    //pubsystem.getHelpLine("!watchduel <name>             -- Displays the score of <names>'s duel. (!wd)"),
                    pubsystem.getHelpLine("!removechallenge              -- Cancel all challenges sent. (!rm)"),
                    pubsystem.getHelpLine("!duels                        -- Lists the duels currently being played. (!ld)"),
                    pubsystem.getHelpLine("!openduels                    -- Lists all open duels you can accept. (!od)"),
                    pubsystem.getHelpLine("!npduel                       -- Enables or disables you from receiving duel challenges."),
                    pubsystem.getHelpLine("!ignore <name>                -- Prevents/allows <name> from challenging you to duels."),
                    pubsystem.getHelpLine("!ignores                      -- Lists who you are currently ignoring challenges from."), };
    }

    @Override
    public String[] getModHelpMessage(String sender) {
        return new String[] { pubsystem.getHelpLine("!betting                     -- Toggles duel betting on or off."),
                pubsystem.getHelpLine("!cancelchallenge <name>      -- Cancel a challenge (specify one of the player)."), };
    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[] { pubsystem.getHelpLine("!info <name>                  -- Displays detailed monetary information on duel bets for <name>."), };
    }

    public void doSuperInfo(String sender, String com) {
        String player = "";
        if (com.contains(" ") && com.length() > 6)
            player = com.substring(com.indexOf(" ") + 1);
        else {
            m_botAction.sendSmartPrivateMessage(sender, "Invalid syntax! Please specify a player name.");
            return;
        }

        player = m_botAction.getFuzzyPlayerName(player);
        if (player == null) {
            m_botAction.sendSmartPrivateMessage(sender, "Player not found.");
            return;
        }

        PubPlayer pubber = context.getPlayerManager().getPlayer(player);
        if (pubber != null) {
            m_botAction.sendSmartPrivateMessage(sender, pubber.getPlayerName() + ": $" + pubber.getMoney());
            if (duelers.containsKey(player)) {
                Dueler subject = duelers.get(player);
                Challenge duel = subject.challenge;
                Dueler op = duel.getOppositeDueler(player);

                if (duel.ship1==duel.ship2) {
                    m_botAction.sendSmartPrivateMessage(sender, "(" + subject.kills + "-" + op.kills + ") $" + duel.amount + " "
                            + Tools.shipName(duel.ship1) + " duel vs " + op.name + ": $" + context.getPlayerManager().getPlayer(op.name).getMoney());
                } else {
                    m_botAction.sendSmartPrivateMessage(sender, "(" + subject.kills + "-" + op.kills + ") $" + duel.amount + " "
                            + Tools.shipName(duel.ship1) + "vs" + Tools.shipName(duel.ship2) + " duel vs " + op.name + ": $" + context.getPlayerManager().getPlayer(op.name).getMoney());                    
                }

                m_botAction.sendSmartPrivateMessage(sender, "Bets on " + duel.challengerName + ": $" + duel.totalC);
                if (duel.totalC > 0)
                    for (String key : duel.challengerBets.keySet())
                        m_botAction.sendSmartPrivateMessage(sender, "> $" + duel.challengerBets.get(key) + " " + key);

                m_botAction.sendSmartPrivateMessage(sender, "Bets on " + duel.challengedName + ": $" + duel.totalA);
                if (duel.totalA > 0)
                    for (String key : duel.challengedBets.keySet())
                        m_botAction.sendSmartPrivateMessage(sender, "> $" + duel.challengedBets.get(key) + " " + key);
            }
        } else
            m_botAction.sendSmartPrivateMessage(sender, "Player profile for '" + player + "' not found.");
    }

    @Override
    public void start() {
    }

    @Override
    public void reloadConfig() {
        if (challenges == null || challenges.size() == 0) {
            this.areas = new HashMap<Integer, DuelArea>();
            this.duelers = new HashMap<String, Dueler>();
            this.challenges = new HashMap<String, Challenge>();
            this.laggers = new HashMap<String, StartLagout>();
            this.spam = new HashMap<String, Long>();
            this.noplay = new LinkedList<String>();
            this.openChallenges = new HashMap<String,String>();

            // Setting Duel Areas
            BotSettings cfg = new BotSettings(m_botAction.getBotSettings().getString("coords_config"));
            
            Point[] pos1 = cfg.getPointArray("duel_area_pos1", ",", ":");
            Point[] pos2 = cfg.getPointArray("duel_area_pos2", ",", ":");
            
            // Load all the duel area's. To be safe, only load until we either hit the config cap or the max entries on the positions.
            for (int i = 1; i <= m_botAction.getBotSettings().getInt("duel_area") && i <= pos1.length && i <= pos2.length; i++) {
                int shipArray[] = m_botAction.getBotSettings().getIntArray("duel_area" + i + "_ship", ",");
                boolean[] ships = { false, false, false, false, false, false, false, false };
                for (int ship : shipArray)
                    ships[ship - 1] = true;
                areas.put(i, new DuelArea(i, pos1[i - 1], pos2[i - 1], ships));
            }
            
            coordsSafe = cfg.getPoint("duel_safe", ":");

            // Setting Misc.
            enabled = m_botAction.getBotSettings().getInt("duel_enabled") == 1;
            //announceNew = m_botAction.getBotSettings().getInt("duel_announce_new") == 1;
            announceWinner = m_botAction.getBotSettings().getInt("duel_announce_winner") == 1;
            saveDuel = m_botAction.getBotSettings().getInt("duel_database_enabled") == 1;

            database = m_botAction.getBotSettings().getString("database");

            announceWinnerAt = m_botAction.getBotSettings().getInt("duel_announce_arena_winner_at");
            announceZoneWinnerAt = m_botAction.getBotSettings().getInt("duel_announce_zone_winner_at");
            announceOpenChallengeAt = m_botAction.getBotSettings().getInt("duel_announce_open_challenge_at");
            deaths = m_botAction.getBotSettings().getInt("duel_deaths");
        }
    }

    @Override
    public void stop() {
        Iterator<Challenge> it = challenges.values().iterator();
        while (it.hasNext()) {
            Challenge c = it.next();
            if (!c.hasEnded() && c.isStarted()) {
                it.remove();
                c.duelEnded = true;
                warpToSafe(c.challengerName, true);
                warpToSafe(c.challengedName, false);
                try {
                    duelers.remove(c.challenger);
                    duelers.remove(c.accepter);                    
                    StartLagout l = laggers.remove(c.challengerName);
                    if (l != null) {
                        l.cancelLagout();
                        m_botAction.cancelTask(l);
                    }
                    l = laggers.remove(c.challengedName);
                    if (l != null) {
                        l.cancelLagout();
                        m_botAction.cancelTask(l);
                    }
                } catch (Exception e) {
                    Tools.printStackTrace(e);
                }
                m_botAction.sendPrivateMessage(c.challengerName, "The bot stopped working due to a 'stop' request from a staff member. Your duel has been cancelled.");
                m_botAction.sendPrivateMessage(c.challengedName, "The bot stopped working due to a 'stop' request from a staff member. Your duel has been cancelled.");
            }
        }

    }

}

class DuelArea {

    public int number; // Area number

    public boolean inUse = false;
    
    public Point warp1, warp2;

    public boolean[] shipAllowed; // 0=wb, 7=shark

    public DuelArea(int number, Point warp1, Point warp2, boolean[] shipAllowed) {
        this.number = number;
        this.warp1 = warp1;
        this.warp2 = warp2;
        this.shipAllowed = shipAllowed;
    }

    public boolean isShipAllowed(int shipType) {
        if (shipType == 0)
            return false;
        if (shipAllowed.length >= shipType)
            return shipAllowed[shipType - 1];
        else
            return false;
    }

    public boolean inUse() {
        return inUse;
    }

    public void setInUse() {
        this.inUse = true;
    }

    public void free() {
        this.inUse = false;
    }

}

class Dueler {

    public static final int DUEL_CHALLENGER = 1;
    public static final int DUEL_ACCEPTER = 2;

    public int type = DUEL_CHALLENGER;

    public String name;
    public Challenge challenge;

    // Stats during a duel
    public int lagouts = 0;
    public int deaths = 0;
    public int kills = 0;
    public int warps = 0;
    public int spawns = 0;

    public int oldFreq = 0;
    public int freq = 0;

    public long lastDeath = 0; // To detect warp vs death and no count (timestamp)
    public long lastKill = 0; // For no count (timestamp)
    public long lastShipChange = 0; // To detect warp vs shipchange (value:0,1,2)
    public long backFromLagout = 0; // To detect warp vs lagout (timestamp)

    public Dueler(String name, int type, Challenge challenge) {
        this.name = name;
        this.type = type;
        this.challenge = challenge;
    }

    public void setFreq(int f) {
        freq = f;
    }

}

class Challenge {

    public Dueler challenger;
    public Dueler accepter;

    public DuelArea area;

    public String challengerName;
    public String challengedName;

    public int amount;       // Playing for $ money
    public int ship1;         // Which ship? 0 = any
    public int ship2;        // -1 if sameship; otherwise, ship of challenged player
    public long startAt = 0; // Started at? Epoch in millis

    public boolean duelEnded = false;
    public Dueler winner;
    public Dueler loser;
    public boolean winByLagout = false;

    public HashMap<String, Integer> challengerBets;
    public HashMap<String, Integer> challengedBets;
    public int totalC = 0;
    public int totalA = 0;
    private PubChallengeModule pcm_ref;

    static int betTimeWindow = 60 * Tools.TimeInMillis.SECOND; // Time after duel start in which you can still bet
    static float betWinMultiplier = 1.8f;   // How much the person wins with a bet (80% of original=default)

    public Challenge(int amount, int ship, int ship2, String challenger, String challenged, PubChallengeModule pcm_ref) {
        this.amount = amount;
        this.ship1 = ship;
        this.ship2 = ship2;
        this.challengerName = challenger;
        this.challengedName = challenged;

        challengerBets = new HashMap<String, Integer>();
        challengedBets = new HashMap<String, Integer>();
        this.pcm_ref = pcm_ref;
    }
    
    public Dueler getOppositeDueler(Dueler dueler) {
        return getOppositeDueler(dueler.name);
    }

    public String getChallenger() {
        return challengerName;
    }

    public String getChallenged() {
        return challengedName;
    }

    public Dueler getOppositeDueler(String playerName) {
        if (challenger != null && accepter != null) {
            if (challenger.name.equals(playerName))
                return accepter;
            else
                return challenger;
        } else
            return null;
    }
    
    public int getTotalChallengerBets() {
        int total = 0;
        for( Integer bet : challengerBets.values() )
            total += bet;
        return total;
    }

    public int getTotalChallengedBets() {
        int total = 0;
        for( Integer bet : challengedBets.values() )
            total += bet;
        return total;
    }
    
    public void setArea(DuelArea area) {
        this.area = area;
    }

    public void setDuelers(Dueler challenger, Dueler accepter) {
        this.challenger = challenger;
        this.accepter = accepter;
    }

    public void start() {
        this.startAt = System.currentTimeMillis();
        challenger.lastDeath = System.currentTimeMillis();
        accepter.lastDeath = System.currentTimeMillis();
        challenger.backFromLagout = System.currentTimeMillis();
        accepter.backFromLagout = System.currentTimeMillis();
    }

    public boolean isStarted() {
        return startAt != 0;
    }

    public boolean hasEnded() {
        return duelEnded;
    }

    public void setWinByLagout() {
        this.winByLagout = true;
    }

    public void setWinner(Dueler dueler) {
        this.winner = dueler;
        this.loser = getOppositeDueler(dueler);
        this.duelEnded = true;
    }

    public void setLoser(Dueler dueler) {
        this.loser = dueler;
        this.winner = getOppositeDueler(dueler);
        this.duelEnded = true;
    }

    public boolean canBet() {
        return (isStarted() && !duelEnded && (System.currentTimeMillis() - startAt) < betTimeWindow);
    }

    public void returnAllBets(PubPlayerManagerModule ppmm) {
        Integer bet = 0;
        
        PubPlayer p;
        for (String n : challengerBets.keySet()) {
            p = ppmm.getPlayer(n);
            if (p != null) {
                bet = challengerBets.get(n);
                p.addMoney(bet);
                pcm_ref.m_botAction.sendSmartPrivateMessage(n, "[BET INFO]  The duel you bet on has been cancelled.  $" + bet + " returned to your account.");
                pcm_ref.context.moneyLog("[BET INFO] Duel cancelled, refunding " + n + " $" + bet + ".");
            }
        }
        for (String n : challengedBets.keySet()) {
            p = ppmm.getPlayer(n);
            if (p != null) {
                bet = challengedBets.get(n);
                p.addMoney(bet);
                pcm_ref.m_botAction.sendSmartPrivateMessage(n, "[BET INFO]  The duel you bet on has been cancelled.  $" + bet + " returned to your account.");
                pcm_ref.context.moneyLog("[BET INFO] Duel cancelled, refunding " + n + " $" + bet + ".");
            }
        }

        challengerBets.clear();
        challengedBets.clear();
        totalC = 0;
        totalA = 0;
    }

    public void settleAllBets(String winner, PubPlayerManagerModule ppmm) {
        Integer bet = 0;
        PubPlayer p;
        boolean challengerWon = winner.equalsIgnoreCase(challengerName);
        String winnerName, loserName;
        int winnerKills, loserKills;
        
        if(challengerWon) {
            winnerName = challengerName;
            loserName = challengedName;
            winnerKills = challenger.kills;
            loserKills = accepter.kills;            
        } else {
            winnerName = challengedName;
            loserName = challengerName;
            winnerKills = accepter.kills;
            loserKills = challenger.kills;
        }

        for (String n : challengerBets.keySet()) {
            p = ppmm.getPlayer(n);
            if (p != null) {
                bet = challengerBets.get(n);
                if (bet != null)
                    if (totalC <= totalA) {
                        if (challengerWon) {
                            bet = bet * 2;
                            p.addMoney(bet);
                            pcm_ref.m_botAction.sendSmartPrivateMessage(n, "[BET WON]  " + winnerName + " defeated " + loserName + " " + winnerKills + ":" + loserKills +
                                    ".  You win $" + bet + "!");
                            pcm_ref.context.moneyLog("[BET WON] " + n + " won $" + bet + ".");
                        } else {
                            pcm_ref.m_botAction.sendSmartPrivateMessage(n, "[BET LOST]  " + winnerName + " defeated " + loserName + " " + winnerKills + ":" + loserKills + ".  You lost $" + bet
                                    + ".  Better luck next time.");
                            pcm_ref.context.moneyLog("[BET LOST] " + n + " lost $" + bet + ".");
                        }
                    } else if (challengerWon) {
                        bet = bet + Math.round(totalA * ((float) bet / totalC));
                        p.addMoney(bet);
                        if( bet != challengerBets.get(n) ) {
                            pcm_ref.m_botAction.sendSmartPrivateMessage(n, "[BET WON]  " + winnerName + " defeated " + loserName + " " + winnerKills + ":" + loserKills + ".  You win $" + bet + "!");
                            pcm_ref.context.moneyLog("[BET WON] " + n + " won $" + bet + ".");
                        } else {
                            pcm_ref.m_botAction.sendSmartPrivateMessage(n, "[BET RETURNED]  " + winnerName + " defeated " + loserName + " " + winnerKills + ":" + loserKills + ", but no-one bet against you.  $" + bet + " returned to your account.");
                            pcm_ref.context.moneyLog("[BET LOST] " + n + " lost nothing. (No opposing bets)");
                        }
                    } else {
                        Integer diff = (Math.round(totalA * ((float) bet / totalC)));
                        p.addMoney(bet - diff);
                        pcm_ref.m_botAction.sendSmartPrivateMessage(n, "[BET LOST]  " + winnerName + " defeated " + loserName + " " + winnerKills + ":" + loserKills + ".  You lost $" + diff
                                + ".  Better luck next time.");
                        pcm_ref.context.moneyLog("[BET LOST] " + n + " lost $" + diff + ".");
                    }
            }
        }
        for (String n : challengedBets.keySet()) {
            p = ppmm.getPlayer(n);
            if (p != null) {
                bet = challengedBets.get(n);
                if (bet != null)
                    if (totalA <= totalC) {
                        if (!challengerWon) {
                            bet = bet * 2;
                            p.addMoney(bet);
                            pcm_ref.m_botAction.sendSmartPrivateMessage(n, "[BET WON]  " + winnerName + " defeated " + loserName + " " + winnerKills + ":" + loserKills + ".  You win $" + bet
                                    + "!");
                            pcm_ref.context.moneyLog("[BET WON] " + n + " won $" + bet + ".");
                        } else {
                            pcm_ref.m_botAction.sendSmartPrivateMessage(n, "[BET LOST]  " + winnerName + " defeated " + loserName + " " + winnerKills + ":" + loserKills + ".  You lost $" + bet
                                    + ".  Better luck next time.");
                            pcm_ref.context.moneyLog("[BET LOST] " + n + " lost $" + bet + ".");
                        }
                    } else if (!challengerWon) {
                        bet = bet + Math.round(totalC * ((float) bet / totalA));
                        p.addMoney(bet);
                        if( bet != challengedBets.get(n) ) {
                            pcm_ref.m_botAction.sendSmartPrivateMessage(n, "[BET WON]  " + winnerName + " defeated " + loserName + " " + winnerKills + ":" + loserKills + ".  You win $" + bet + "!");
                            pcm_ref.context.moneyLog("[BET WON] " + n + " won $" + bet + ".");
                        } else {
                            pcm_ref.m_botAction.sendSmartPrivateMessage(n, "[BET RETURNED] " + winnerName + " defeated " + loserName + " " + winnerKills + ":" + loserKills + ", but no-one bet against you.  $" + bet + " returned to your account.");
                            pcm_ref.context.moneyLog("[BET LOST] " + n + " lost nothing. (No opposing bets)");
                        }
                    } else {
                        Integer diff = (Math.round(totalC * ((float) bet / totalA)));
                        p.addMoney(bet - diff);
                        pcm_ref.m_botAction.sendSmartPrivateMessage(n, "[BET LOST]  " + winnerName + " defeated " + loserName + " " + winnerKills + ":" + loserKills + ".  You lost $" + diff
                                + ".  Better luck next time.");
                        pcm_ref.context.moneyLog("[BET LOST] " + n + " lost $" + diff + ".");
                    }
            }
        }

        challengerBets.clear();
        challengedBets.clear();
        totalC = 0;
        totalA = 0;

    }

    public boolean betOnDueler(String name, PubPlayer bettor, boolean bettingChallenger, int amount) {
        if (duelEnded || (System.currentTimeMillis() - startAt) > betTimeWindow)
            return false;

        if (name.equals(challengerName) || name.equals(challengedName))
            return false;

        if (bettingChallenger) {
            if (challengedBets.containsKey(name))
                return false;

            if (challengerBets.containsKey(name)) {
                pcm_ref.m_botAction.sendSmartPrivateMessage(name, "You can't change your bet once it's been made.");
                return false;
                /* No longer allowing refunds.
                bettor.addMoney(challengerBets.get(name));
                totalC -= challengerBets.get(name);
                pcm_ref.m_botAction.sendSmartPrivateMessage(name, "[NOTE]  Your previous bet of $" + challengerBets.get(name) + " has been returned to you.");
                */
            }

            bettor.removeMoney(amount);
            challengerBets.put(name, amount);
            if( challengedBets.isEmpty() )
                doBetAdvert(name,amount,true);
            totalC += amount;
            pcm_ref.context.moneyLog("[BET] " + name + " has bet $ " + amount + " bet on " + challengerName + "." );
            return true;
        } else {
            if (challengerBets.containsKey(name))
                return false;

            if (challengedBets.containsKey(name)) {
                pcm_ref.m_botAction.sendSmartPrivateMessage(name, "You can't change your bet once it's been made.");
                return false;
                /* No longer allowing refunds.
                bettor.addMoney(challengedBets.get(name));
                totalA -= challengedBets.get(name);
                pcm_ref.m_botAction.sendSmartPrivateMessage(name, "[NOTE]  Your previous bet of $" + challengedBets.get(name) + " has been returned to you.");
                */
            }

            bettor.removeMoney(amount);
            challengedBets.put(name, amount);
            if( challengerBets.isEmpty() )
                doBetAdvert(name,amount,false);
            totalA += amount;
            pcm_ref.context.moneyLog("[BET] " + name + " has bet $ " + amount + " bet on " + challengedName + "." );
            return true;
        }        
    }
    
    void doBetAdvert( String bettor, int amount, boolean onChallenger ) {
        if( amount < pcm_ref.MIN_BET_TO_NOTIFY )
            return;
        
        if( System.currentTimeMillis() - pcm_ref.lastBetAdvert < pcm_ref.MAX_BET_ADVERT_FREQUENCY )
            return;
        if ( onChallenger ) {
            if ( ship1 == ship2 )
                pcm_ref.m_botAction.sendArenaMessage( "[BET] $" + amount + " on " + challengerName + " in " + Tools.shipNameSlang( ship1 ) + ". To match: :tw-p:!beton " + challengedName + ":" + amount );
            else
                pcm_ref.m_botAction.sendArenaMessage( "[BET] $" + amount + " on " + challengerName + " in " + Tools.shipNameSlang( ship1 ) + " vs " + Tools.shipNameSlang( ship2 ) + ". To match: :tw-p:!beton " + challengedName + ":" + amount );
        } else {
            if ( ship1 == ship2 )
                pcm_ref.m_botAction.sendArenaMessage( "[BET] $" + amount + " on " + challengedName + " in " + Tools.shipNameSlang( ship2 ) + ". To match: :tw-p:!beton " + challengerName + ":" + amount );
            else
                pcm_ref.m_botAction.sendArenaMessage( "[BET] $" + amount + " on " + challengedName + " in " + Tools.shipNameSlang( ship2 ) + " vs " + Tools.shipNameSlang( ship1 ) + ". To match: :tw-p:!beton " + challengerName + ":" + amount );            
        }
        pcm_ref.lastBetAdvert = System.currentTimeMillis();
    }

}
