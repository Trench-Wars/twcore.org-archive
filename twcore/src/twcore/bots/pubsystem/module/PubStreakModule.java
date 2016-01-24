package twcore.bots.pubsystem.module;

import java.util.concurrent.ConcurrentHashMap;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.events.FrequencyShipChange;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubStreakModule extends AbstractModule {

    public final static int ARENA_TIMEOUT = 3 * Tools.TimeInMillis.MINUTE;

    private ConcurrentHashMap<String, Integer> winStreaks;
    private ConcurrentHashMap<String, Integer> loseStreaks;

    //private int streakJump;
    private int winsStreakArenaAt;
    //private int winsStreakZoneAt;
    private int winsStreakMoneyMultiplicator;
    private int streakBrokerBonus;

    private int bestWinStreak = 0;
    private int worstLoseStreak = 0;
    private PubPlayer bestWinStreakPlayer;
    //private PubPlayer worstLoseStreakPlayer;

    private long lastArena = 0;
    //private long lastZone = 0;

    private boolean moneyEnabled = false;

    public PubStreakModule(BotAction botAction, PubContext context) {

        super(botAction, context, "Streak");

        this.winStreaks = new ConcurrentHashMap<String, Integer>();
        this.loseStreaks = new ConcurrentHashMap<String, Integer>();

        //m_botAction.scheduleTask(new SafeChecker(context), 10*Tools.TimeInMillis.SECOND, 3*Tools.TimeInMillis.SECOND);

        reloadConfig();
    }

    public void requestEvents(EventRequester eventRequester)
    {
        eventRequester.request(EventRequester.PLAYER_DEATH);
        eventRequester.request(EventRequester.PLAYER_LEFT);
        // Disabled at the request of KrynetiX
        //eventRequester.request(EventRequester.PLAYER_POSITION);
        eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
    }

    /*  29Sep2013 Trancid Disabled/removed at the request of KrynetiX
        public void handleEvent(PlayerPosition event)
        {
        Player p = m_botAction.getPlayer(event.getPlayerID());
        if (p != null && p.isInSafe()) {
            try {
                if (winStreaks.containsKey(p.getPlayerName())) {
                    if (winStreaks.get(p.getPlayerName()) >= winsStreakArenaAt)
                    {
                        winStreaks.remove(p.getPlayerName());
                        m_botAction.sendSmartPrivateMessage(p.getPlayerName(),
                                "You have entered a safe and lost your streak.");
                    }
                }
            } catch (ConcurrentModificationException e) {
            }
        }
        }*/

    //15Aug2013 POiD    Added Speccing removing a streak.
    public void handleEvent(FrequencyShipChange event) {
        if (!enabled) return;

        if (event.getShipType() == Tools.Ship.SPECTATOR)
        {
            Player p = m_botAction.getPlayer(event.getPlayerID());

            if (p != null)
            {
                if (winStreaks.containsKey(p.getPlayerName()))
                {
                    if (winStreaks.get(p.getPlayerName()) >= winsStreakArenaAt)
                    {
                        winStreaks.remove(p.getPlayerName());
                        m_botAction.sendSmartPrivateMessage(p.getPlayerName(),
                                                            "You have entered spectator mode and thus have lost your streak.");
                    }
                }
            }
        }
    }

    public void handleEvent(PlayerLeft event) {
        if (!enabled) return;

        Player p = m_botAction.getPlayer(event.getPlayerID());

        if (p == null)
            return;

        winStreaks.remove(p.getPlayerName());
        loseStreaks.remove(p.getPlayerName());
    }

    public void handleEvent(PlayerDeath event) {
        if (!enabled)
            return;

        Player killer = m_botAction.getPlayer(event.getKillerID());
        Player killed = m_botAction.getPlayer(event.getKilleeID());

        if (killer == null || killed == null)
            return;

        // Dueling? do nothing
        if (context.getPubChallenge().isDueling(killer.getPlayerName())
                || context.getPubChallenge().isDueling(killed.getPlayerName())) {
            return;
        }

        // Same team? Reset streak!
        if (killer.getFrequency() == killed.getFrequency()) {
            winStreaks.put(killer.getPlayerName(), 0);
        }

        // Not in the system yet?
        if (winStreaks.get(killer.getPlayerName()) == null) {
            winStreaks.put(killer.getPlayerName(), 0);
            loseStreaks.put(killer.getPlayerName(), 0);
        }

        if (winStreaks.get(killed.getPlayerName()) == null) {
            winStreaks.put(killed.getPlayerName(), 0);
            loseStreaks.put(killed.getPlayerName(), 0);
        }


        PubPlayer pubPlayerKiller = context.getPlayerManager().getPlayer(killer.getPlayerName());
        PubPlayer pubPlayerKilled = context.getPlayerManager().getPlayer(killed.getPlayerName());

        if (pubPlayerKiller == null || pubPlayerKilled == null) {
            return;
        }

        boolean streakBroker = false;

        // Streak breaker??
        if (winStreaks.get(killed.getPlayerName()) >= winsStreakArenaAt) {
            if (pubPlayerKiller != null) {

                int streak = winStreaks.get(killed.getPlayerName());
                int moneybroker = streakBrokerBonus + streak * winsStreakMoneyMultiplicator;

                announceStreakBreaker(pubPlayerKiller, pubPlayerKilled, streak, moneybroker,
                                      (winStreaks.get(killed.getPlayerName()) >= winsStreakArenaAt * 3) );

                if (streakBrokerBonus > 0) {
                    pubPlayerKiller.addMoney(streakBrokerBonus);
                }
            }

            streakBroker = true;
        }

        int streak;

        // Updating stats for the killed (if not dueling)
        winStreaks.put(killed.getPlayerName(), 0);
        loseStreaks.put(killed.getPlayerName(), loseStreaks.get(killed.getPlayerName()) + 1);

        streak = loseStreaks.get(killed.getPlayerName());

        if (streak > worstLoseStreak) {
            worstLoseStreak = streak;
            //worstLoseStreakPlayer = pubPlayerKilled;
        }

        // Updating stats for the killer (if not dueling)
        loseStreaks.put(killer.getPlayerName(), 0);
        winStreaks.put(killer.getPlayerName(), winStreaks.get(killer.getPlayerName()) + 1);

        streak = winStreaks.get(killer.getPlayerName());

        // Time to tell the player speccing is not allowed
        // Else, the player will lose his streak
        if (streak == winsStreakArenaAt) {
            m_botAction.sendSmartPrivateMessage(killer.getPlayerName(), "You have made " + winsStreakArenaAt + " kills in a row. Congrats! Please do not go into spectator mode.");
            m_botAction.sendSmartPrivateMessage(killer.getPlayerName(), "If you do, you will lose your streak. May the force be with you.");
        }

        // Is a streak worth to be said?
        if (streak >= winsStreakArenaAt) {

            // Best of session?
            if (streak > bestWinStreak) {
                bestWinStreak = streak;
                bestWinStreakPlayer = pubPlayerKiller;

                if (!streakBroker)
                    announceWinStreak(pubPlayerKiller, streak);
            }
            else {
                if (!streakBroker)
                    announceWinStreak(pubPlayerKiller, streak);
            }
        }

        // Money gains by the killer?
        if (context.getMoneySystem().isEnabled()) {
            int money = getMoney(winStreaks.get(killer.getPlayerName()));

            if (money > 0 && moneyEnabled) {
                pubPlayerKiller.addMoney(money);
            }
        }

    }

    private void saveBestStreak(PubPlayer player, Integer streak) {

        if (streak > player.getBestStreak()) {

            player.setBestStreak(streak);
            checkStreakMilestone(player, streak);

            String database = m_botAction.getBotSettings().getString("database");

            // The query will be closed by PlayerManagerModule
            if (database != null)
                m_botAction.SQLBackgroundQuery(database, null, "UPDATE tblPlayerStats "
                                               + "SET "
                                               + "fdBestStreak = IF(" + streak + ">fnBestStreak ,NOW(),fdBestStreak),"
                                               + "fnBestStreak = IF(" + streak + ">fnBestStreak," + streak + ",fnBestStreak) "
                                               + "WHERE fcName='" + Tools.addSlashes(player.getPlayerName()) + "'");

        }

    }

    /**
        Checks for streak milestones and gives bonuses if reached.
        @param pp Player
        @param numWins Streak amount
    */
    public void checkStreakMilestone( PubPlayer pp, int numWins ) {
        String awardString = "";
        int bonus = 0;

        switch (numWins) {
        case 5:
            awardString = "Your first streak! Nice!";
            bonus = 100;
            break;

        case 10:
            awardString = "It seems you know what you're doing.";
            bonus = 200;
            break;

        case 20:
            awardString = "I doubt that was exactly what would be called 'easy.'";
            bonus = 300;
            break;

        case 30:
            awardString = "Sure you're not cheating?";
            bonus = 400;
            break;

        case 40:
            awardString = "The gods smile on you today.";
            bonus = 500;
            break;

        case 50:
            awardString = "Respect.";
            bonus = 600;
            break;

        case 75:
            awardString = "Don't blow it now!";
            bonus = 700;
            break;

        case 100:
            awardString = "100 dead spaceships and you without a scratch.";
            bonus = 800;
            break;

        case 125:
            awardString = "They come at you ... they come at you ... and they always seem to lose.";
            bonus = 1000;
            break;

        case 150:
            awardString = "Do you give lessons?";
            bonus = 1500;
            break;

        case 175:
            awardString = "This is starting to get a little ridiculous.";
            bonus = 2000;
            break;

        case 200:
            awardString = "To say you're on a roll is an understatement.";
            bonus = 2500;
            break;

        case 250:
            awardString = "Don't die.";
            bonus = 5000;
            break;

        case 300:
            awardString = "YOU'RE THE BEST ... AROUND! -- NOTHIN'S GONNA EVER KEEP YA DOWN!";
            bonus = 10000;
            break;

        case 400:
            awardString = "Pretty sure nobody's ever done this before.";
            bonus = 25000;
            break;

        case 500:
            awardString = "LEGEND. YOU ARE A LEGEND.";
            bonus = 50000;
            break;

        case 750:
            awardString = "You can't possibly expect to keep this going much longer.";
            bonus = 100000;
            break;

        case 1000:
            awardString = "PLEASE DON'T HURT ME, BASED GOD.";
            bonus = 150000;
            break;

        case 5000:
            awardString = "...wow. No words. No words for this.";
            bonus = 250000;
            break;

        case 10000:
            awardString = "To give you real money for this would only cheapen your accomplishment. Congratu-freakin'-lations.";
            bonus = 1;
            break;
        }

        if (bonus > 0) {
            m_botAction.sendPrivateMessage(pp.getPlayerName(), "PERSONAL STREAK MILESTONE REACHED ... " + numWins + " kills" + (numWins == 1 ? "" : "s") + "!  Bonus: $" + bonus + "!  " + awardString );
            pp.addMoney(bonus);

            if (numWins >= 150)
                m_botAction.sendZoneMessage( pp.getPlayerName() + " has made " + numWins + " kills without dying for the first time ever. End this madness! ?go", Tools.Sound.CROWD_GEE);
        }
    }


    private void announceStreakBreaker(PubPlayer killer, PubPlayer killed, int streak, int money, boolean arena) {
        String message = "[STREAK BREAKER!] " + killed.getPlayerName() + " (" + streak + " kills) broken by " + killer.getPlayerName() + "!";

        if (context.getMoneySystem().isEnabled()) {
            message += " (+$" + money + ")";
        }

        if (arena)
            m_botAction.sendArenaMessage(message);
        else
            m_botAction.sendPrivateMessage((int)killer.getPlayerID(), message);
    }

    private void announceWinStreak(PubPlayer player, int streak) {
        if (m_botAction.getOperatorList().isBotExact(player.getPlayerName()))
            return;

        /*
            int money = getMoney(winStreaks.get(player.getPlayerName()));
            String moneyMessage = "";
            if (context.getMoneySystem().isEnabled()) {
            moneyMessage = "(+$" + String.valueOf(money) + ")";
            }
        */

        // Arena?
        if (streak >= winsStreakArenaAt) {

            /*
                if (System.currentTimeMillis()-lastArena > ARENA_TIMEOUT || bestWinStreak==streak) {
                String message = "[STREAK] " + player.getPlayerName() + " with " + streak + " kills! " + moneyMessage;
                if (bestWinStreak==streak)
                    message += " Best Streak of the Session!";
                m_botAction.sendArenaMessage(message);
                lastArena = System.currentTimeMillis();
                }
            */

            saveBestStreak(player, streak);

            // Arena only if "BEST OF THE SESSION" and if first *arena since ARENA_TIMEOUT
            if (System.currentTimeMillis() - lastArena > ARENA_TIMEOUT && bestWinStreak == streak) {
                m_botAction.sendArenaMessage("[STREAK] " + player.getPlayerName() + " with " + streak + " kills! Best Streak of the Session!");
                lastArena = System.currentTimeMillis();
            }
        }

    }

    /**
        Return the money gains by a player for a streak
        Encapsulate the algorithm used
    */
    private int getMoney(int streak) {

        if (!moneyEnabled)
            return 0;

        if (streak >= winsStreakArenaAt) {
            int diff = Math.min(50, streak) - winsStreakArenaAt + 1;
            return diff * winsStreakMoneyMultiplicator;
        }

        return 0;
    }

    public void doStreakCmd( String sender, String name ) {

        if (name.isEmpty()) {

            PubPlayer player = context.getPlayerManager().getPlayer(sender);

            if (winStreaks.containsKey(sender)) {
                m_botAction.sendSmartPrivateMessage(sender, "Current streak: " + winStreaks.get(sender) + " kill(s).");
            } else
                m_botAction.sendSmartPrivateMessage(sender, "Current streak: none");

            if (player != null)
                m_botAction.sendSmartPrivateMessage(sender, "Best streak: " + player.getBestStreak() + " kill(s).");
        }
        else {

            PubPlayer player = context.getPlayerManager().getPlayer(name);

            if (player != null) {

                name = player.getPlayerName();

                if (winStreaks.containsKey(name)) {
                    m_botAction.sendSmartPrivateMessage(sender, "Current streak of " + name + ": " + winStreaks.get(name) + " kill(s).");
                } else {
                    m_botAction.sendSmartPrivateMessage(sender, name + " has no streak yet.");
                }

                m_botAction.sendSmartPrivateMessage(sender, "Best streak: " + player.getBestStreak() + " kill(s).");

            } else {
                m_botAction.sendSmartPrivateMessage(sender, "Player not found.");
            }

        }
    }

    public void doBestSessionStreakCmd( String sender ) {

        if (bestWinStreakPlayer != null) {
            m_botAction.sendSmartPrivateMessage(sender, "Best streak of the session: " + bestWinStreakPlayer.getPlayerName() + " with " + bestWinStreak + " kills.");
        } else {
            m_botAction.sendSmartPrivateMessage(sender, "There is no streak recorded yet.");
        }
    }

    public void doSetStreakCmd( String sender, String command ) {

        command = command.substring(11).trim();

        if (command.contains(":"))
        {
            String[] split = command.split("\\s*:\\s*");
            String name = split[0];

            try {
                int streak = Integer.parseInt(split[1]);
                PubPlayer pubPlayer = context.getPlayerManager().getPlayer(name);
                winStreaks.put(pubPlayer.getPlayerName(), streak);
            } catch (NumberFormatException e) {
                m_botAction.sendSmartPrivateMessage(sender, "Error number!");
                return;
            }

            m_botAction.sendSmartPrivateMessage(sender, "Done!");
            doStreakCmd(sender, name);
        } else {
            m_botAction.sendSmartPrivateMessage(sender, "Error command!");
        }

    }

    public void doStreakResetCmd( String sender ) {

        bestWinStreak = 0;
        bestWinStreakPlayer = null;

        worstLoseStreak = 0;
        //worstLoseStreakPlayer = null;

        winStreaks = new ConcurrentHashMap<String, Integer>();
        loseStreaks = new ConcurrentHashMap<String, Integer>();

        m_botAction.sendArenaMessage("[STREAK] The streak session has been reset.");
    }

    @Override
    public void handleCommand(String sender, String command) {

        if(command.trim().equals("!streak") || command.startsWith("!streak "))
            doStreakCmd(sender, command.substring(7).trim());
        else if(command.trim().equals("!s") || command.startsWith("!s "))
            doStreakCmd(sender, command.substring(2).trim());
        else if(command.trim().equals("!streakbest") || command.trim().equals("!beststreak"))
            doBestSessionStreakCmd(sender);

    }

    @Override
    public void handleModCommand(String sender, String command) {

        if(command.equals("!streakreset"))
            doStreakResetCmd(sender);

        if(m_botAction.getOperatorList().isOwner(sender) && command.startsWith("!setstreak"))
            doSetStreakCmd(sender, command);
    }

    @Override
    public String[] getHelpMessage(String sender) {
        return new String[] {
                   pubsystem.getHelpLine("!streak            -- Your current streak. (Shorthand: !s)"),
                   pubsystem.getHelpLine("!streak <name>     -- Best and current streak of a given player name."),
                   pubsystem.getHelpLine("!beststreak        -- Current best streak of the session."),
               };
    }

    @Override
    public String[] getModHelpMessage(String sender) {
        return new String[] {
                   pubsystem.getHelpLine("!streakreset       -- Reset the current session (with *arena)."),
               };
    }

    @Override
    public void start() {

    }

    @Override
    public void reloadConfig() {
        if (m_botAction.getBotSettings().getInt("streak_enabled") == 1) {
            enabled = true;
        }

        if (m_botAction.getBotSettings().getInt("streak_money_enabled") == 1) {
            moneyEnabled = true;
        }

        //streakJump = m_botAction.getBotSettings().getInt("streak_jump");
        winsStreakArenaAt = m_botAction.getBotSettings().getInt("streak_wins_arena_at");
        //winsStreakZoneAt = m_botAction.getBotSettings().getInt("streak_wins_zone_at");
        winsStreakMoneyMultiplicator = m_botAction.getBotSettings().getInt("streak_wins_money_multiplicator");
        streakBrokerBonus = m_botAction.getBotSettings().getInt("streak_broker_bonus");
    }

    @Override
    public void stop() {

    }
    /*  29Sep2013 Trancid Disabled/removed at the request of KrynetiX
        private class SafeChecker extends TimerTask {

        private PubContext context;

        public SafeChecker(PubContext context) {
            this.context = context;
        }

        public void run() {
            Iterator<Entry<String,Integer>> it = winStreaks.entrySet().iterator();
            while(it.hasNext()) {
                Entry<String,Integer> entry = it.next();
                if (entry.getValue() >= winsStreakArenaAt) {
                    Player p = m_botAction.getPlayer(entry.getKey());
                    if (p != null) {
                        Location loc = context.getPubUtil().getLocation(p.getXTileLocation(), p.getYTileLocation());
                        if (loc.equals(Location.SAFE)) {

                            m_botAction.sendSmartPrivateMessage(entry.getKey(), "You cannot go in safe with a streak higher than " + winsStreakArenaAt + " kills. You have lost your streak.");
                            winStreaks.put(entry.getKey(), 0);

                        }
                    }
                }
            }
        }
        }*/

    @Override
    public void handleSmodCommand(String sender, String command) {

    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[] {};
    };

}
