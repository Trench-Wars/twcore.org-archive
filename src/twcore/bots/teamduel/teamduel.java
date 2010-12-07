package twcore.bots.teamduel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.TimerTask;
import java.util.Vector;

//import twcore.bots.teamduel.Duel;
import twcore.bots.teamduel.WarpPoint;
import twcore.bots.teamduel.teamduel;
import twcore.bots.teamduel.DuelChallenge;
import twcore.bots.teamduel.DuelPlayer;
import twcore.bots.teamduel.Duel;
import twcore.bots.teamduel.DuelPlayerStats;
import twcore.bots.teamduel.DuelBox;
import twcore.bots.teamduel.NotPlaying;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.command.CommandInterpreter;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.SQLResultEvent;
import twcore.core.game.Player;
import twcore.core.lvz.Objset;
import twcore.core.util.Tools;

/**
 * 
 * TeamDuelBot is an adaptation of MatchBots and DuelBot 
 * It is responsible for managing team duels held in duel2
 * 
 * @author WingZero
 *
 */
public class teamduel extends SubspaceBot {
    CommandInterpreter m_commandInterpreter;
    final String mySQLHost = "website";
    final int MSG_LIMIT = 8;
    // Used to 'shutdown' the bot and allow no new duels.
    boolean shutDown = false;
    // Disallows new duels and shuts bot down once all dueling has stopped
    boolean shutDownDie = false;
    String aliasChecker = "";
    boolean aliasCheck = false;

    Objset objects = m_botAction.getObjectSet();
    LockSmith locksmith;

    // Duel variables read from CGI
    int s_season; // current season
    int s_spawn; // time until spawn after death
    int s_spawnLimit; // # of spawns allowed before Forfeit
    int s_spawnTime; // Time after death considered a spawn
    int s_noCount; // Time after double death considered a NC
    int s_lagLimit; // # of lagouts allowed before Forfeit
    int s_challengeTime; // How long before a challenge expires
    int s_duelLimit; // Number of duels allowed per s_duelDays
    int s_duelDays; // Number of days restriction is put on duels
    long lastZoner; // Last time someone's streak was zoned.
    String from = "", to = ""; // used for lag info
    String shutDownMessage = "";
    String greet = "Welcome to TeamDuel (BETA)! If you are new, PM me with !about for information. PM me !help for a detailed list of commands or !h for a concise version.";
    BotSettings m_botSettings;
    OperatorList opList;
    SimpleDateFormat banDate = new SimpleDateFormat("MM.dd.yy - ");

    // Contains the list of current duels in progress
    HashMap<Integer, Duel> duels = new HashMap<Integer, Duel>();
    // Contains the list of duel boxes available for dueling
    HashMap<String, DuelBox> duelBoxes = new HashMap<String, DuelBox>();
    // Contains the list of players with !notplaying ON
    HashSet<String> notPlaying = new HashSet<String>();
    // Contains the list of players currently playing
    HashMap <String, Duel> playing = new HashMap<String, Duel>(); 
    // Contains a list of players (took out idle times cuz not used) and idle times
    HashMap<String, DuelPlayer> players = new HashMap<String, DuelPlayer>();
    // Contains a list of unregistered players and their userIDs
    HashMap<String, Integer> newbies = new HashMap<String, Integer>();
    // Contains list of teams present and playable in the arena
    HashMap<Integer, DuelTeam> teamList = new HashMap<Integer, DuelTeam>();
    // Contains the list of currently issued challenges
    HashMap<String, DuelChallenge> challenges = new HashMap<String, DuelChallenge>();
    // Contains a list of challenges not yet accept by both challenging players
    HashMap<String, TeamChallenge> teamChallenges = new HashMap<String, TeamChallenge>();
    // Contains the list of players lagged out
    HashMap<String, Lagger> laggers = new HashMap<String, Lagger>();
    // Contains the list of players that are allowed to !signup.
    HashSet<String> allowedNames = new HashSet<String>();
    // Contains the list of players that were allowed to !signup and can now be !enable
    HashSet<String> canEnableNames = new HashSet<String>();
    // Contains the list of league Operators *** SHOULD BE HASHSET ... ***
    HashMap<String, String> leagueOps = new HashMap<String, String>();
    // Contains the list of league Head Operators
    HashMap<String, String> leagueHeadOps = new HashMap<String, String>();
    // Contains the list of hidden operators (bot developers)
    HashMap<String, String> hiddenOps = new HashMap<String, String>();
    // Contains list of pending team invites
    HashMap<String, TeamInvite> invites = new HashMap<String, TeamInvite>();
    // something for respawns
    HashMap<String, SpawnDelay> spawnDelays = new HashMap<String, SpawnDelay>();
    // List of delays in case of double kills on last death
    HashMap<String, KillDelay> killDelays = new HashMap<String, KillDelay>();

    public teamduel(BotAction botAction) {
        super(botAction);
        EventRequester events = m_botAction.getEventRequester();
        events.request(EventRequester.MESSAGE);
        events.request(EventRequester.PLAYER_DEATH);
        events.request(EventRequester.PLAYER_POSITION);
        events.request(EventRequester.FREQUENCY_SHIP_CHANGE);
        events.request(EventRequester.PLAYER_LEFT);
        events.request(EventRequester.PLAYER_ENTERED);
        events.request(EventRequester.ARENA_JOINED);
        
        opList = m_botAction.getOperatorList();

        m_commandInterpreter = new CommandInterpreter(m_botAction);
        registerCommands();
        /* not yet
        TimerTask check = new TimerTask() {
            public void run() {
                check();
            }
        };
        */
        //m_botAction.scheduleTaskAtFixedRate(check, 1000, Tools.TimeInMillis.MINUTE);
    }

    public void registerCommands() {
        int acceptedMessages = Message.PRIVATE_MESSAGE;

        /********* Player Commands *********/
        m_commandInterpreter.registerCommand("!about", acceptedMessages, this, "do_showAbout"); 
        m_commandInterpreter.registerCommand("!challenge", acceptedMessages, this, "do_teamChallenge"); 
        m_commandInterpreter.registerCommand("!ch", acceptedMessages, this, "do_teamChallenge"); 
        m_commandInterpreter.registerCommand("!removechallenge", acceptedMessages, this, "do_removeChallenge");
        m_commandInterpreter.registerCommand("!rc", acceptedMessages, this, "do_removeChallenge");
        m_commandInterpreter.registerCommand("!agree", acceptedMessages, this, "do_teamAccept");
        m_commandInterpreter.registerCommand("!accept", acceptedMessages, this, "do_acceptChallenge");
        m_commandInterpreter.registerCommand("!disable", acceptedMessages, this, "do_disableName");
        m_commandInterpreter.registerCommand("!rules", acceptedMessages, this, "do_setRules");
        m_commandInterpreter.registerCommand("!enable", acceptedMessages, this, "do_enableName");
        m_commandInterpreter.registerCommand("!score", acceptedMessages, this, "do_showScore");
        m_commandInterpreter.registerCommand("!duels", acceptedMessages, this, "do_showDuels");
        m_commandInterpreter.registerCommand("!teams", acceptedMessages, this, "do_showTeams");
        m_commandInterpreter.registerCommand("!me", acceptedMessages, this, "do_showMe");
        m_commandInterpreter.registerCommand("!lagout", acceptedMessages, this, "do_lagOut"); 
        m_commandInterpreter.registerCommand("!cancel", acceptedMessages, this, "do_cancelDuel");
        m_commandInterpreter.registerCommand("!signup", acceptedMessages, this, "do_signUp");
        m_commandInterpreter.registerCommand("!register", acceptedMessages, this, "do_registerTeam");
        m_commandInterpreter.registerCommand("!team", acceptedMessages, this, "do_registerTeam");
        m_commandInterpreter.registerCommand("!reg", acceptedMessages, this, "do_registerTeam");
        m_commandInterpreter.registerCommand("!join", acceptedMessages, this, "do_joinTeam");
        m_commandInterpreter.registerCommand("!quit", acceptedMessages, this, "do_quitDivision");
        m_commandInterpreter.registerCommand("!notplaying", acceptedMessages, this, "do_setNotPlaying");
        m_commandInterpreter.registerCommand("!np", acceptedMessages, this, "do_setNotPlaying");
        m_commandInterpreter.registerCommand("!ops", acceptedMessages, this, "do_showLeagueOps");
        m_commandInterpreter.registerCommand("!lag", acceptedMessages, this, "do_showLag");
        m_commandInterpreter.registerCommand("!rank", acceptedMessages, this, "do_showRank");
        m_commandInterpreter.registerCommand("!help", acceptedMessages, this, "do_showHelp");
        m_commandInterpreter.registerCommand("!tinyhelp", acceptedMessages, this, "do_showQuickHelp");
        m_commandInterpreter.registerCommand("!h", acceptedMessages, this, "do_showQuickHelp");
        m_commandInterpreter.registerCommand("!divisions", acceptedMessages, this, "do_showDivisions");
        m_commandInterpreter.registerCommand("!cancelinvite", acceptedMessages, this, "do_cancelTeamInvite");
        /********* LeagueOp Commands *********/
        m_commandInterpreter.registerCommand("!limit", acceptedMessages, this, "do_setMessageLimit");
        m_commandInterpreter.registerCommand("!allowuser", acceptedMessages, this, "do_allowUser");
        m_commandInterpreter.registerCommand("!banuser", acceptedMessages, this, "do_banUser");
        m_commandInterpreter.registerCommand("!unbanuser", acceptedMessages, this, "do_unbanUser");
        m_commandInterpreter.registerCommand("!banned", acceptedMessages, this, "do_sayBanned");
        m_commandInterpreter.registerCommand("!comment", acceptedMessages, this, "do_reComment");
        m_commandInterpreter.registerCommand("!readcomment", acceptedMessages, this, "do_getComment");
        m_commandInterpreter.registerCommand("!setgreet", acceptedMessages, this, "do_setGreetMessage");
        m_commandInterpreter.registerCommand("!shutdown", acceptedMessages, this, "do_shutDown");
        m_commandInterpreter.registerCommand("!alias", acceptedMessages, this, "do_aliasCheck");
        m_commandInterpreter.registerCommand("!disableuser", acceptedMessages, this, "do_opDisableName");
        m_commandInterpreter.registerCommand("!refresh", acceptedMessages, this, "do_opRefreshArena");
        m_commandInterpreter.registerCommand("!shutdowndie", acceptedMessages, this, "do_shutDownDie");
        m_commandInterpreter.registerCommand("!die", acceptedMessages, this, "do_die");
        m_commandInterpreter.registerCommand("!setgreet", acceptedMessages, this, "do_setGreetMessage");
        /********* Head Operator Commands *********/
        m_commandInterpreter.registerCommand("!addop", acceptedMessages, this, "do_addOp");
        m_commandInterpreter.registerCommand("!removeop", acceptedMessages, this, "do_removeOp");
        m_commandInterpreter.registerCommand("!setgreet", acceptedMessages, this, "do_setGreetMessage");
        m_commandInterpreter.registerCommand("!addhiddenop", acceptedMessages, this, "do_addHiddenOp");
        m_commandInterpreter.registerCommand("!removehiddenop", acceptedMessages, this, "do_removeHiddenOp");

        m_commandInterpreter.registerDefaultCommand(Message.ARENA_MESSAGE, this, "do_checkArena");     
    }
    
    /**
     * Event handling
     */
    public void handleEvent(LoggedOn event) {
        // join initial arena
        m_botSettings = m_botAction.getBotSettings();
        m_botAction.joinArena(m_botSettings.getString("Arena")); // remove +
        // m_botAction.getBotNumber()
        // for dev zone functionality

        // Sets up all variables for new features that I can't think of a good
        // comment for
        lastZoner = System.currentTimeMillis() - (30 * 60 * 1000);

        // Create new box Objects
        int boxCount = m_botSettings.getInt("BoxCount");
        for (int i = 1; i <= boxCount; i++) {
            String boxset[] = m_botSettings.getString("Box" + i).split(",");
            String warps[] = m_botSettings.getString("Warp" + i).split(",");
            String area[] = m_botSettings.getString("Area" + i).split(",");
            if (boxset.length == 17)
                duelBoxes.put("" + i, new DuelBox(boxset, warps, area, i));
        }
        // Reads in general settings for dueling
        s_season = m_botSettings.getInt("Season");
        s_spawn = m_botSettings.getInt("SpawnAfter");
        s_spawnLimit = m_botSettings.getInt("SpawnLimit");
        s_spawnTime = m_botSettings.getInt("SpawnTime");
        s_noCount = m_botSettings.getInt("NoCount");
        s_lagLimit = m_botSettings.getInt("LagLimit");
        s_challengeTime = m_botSettings.getInt("ChallengeTime");
        s_duelLimit = m_botSettings.getInt("DuelLimit");
        s_duelDays = m_botSettings.getInt("DuelDays");
        // Reads in the league operators
        do_updateOps();
        // Puts the arena in 'ready' state
        locksmith = new LockSmith(m_botAction);
        m_botAction.toggleLocked();
        m_botAction.specAll();
        m_botAction.setReliableKills(1);
        m_botAction.setMessageLimit(MSG_LIMIT);
    }

    public void handleEvent(ArenaJoined event) {
        do_initialScan();
    }

    public void handleEvent(Message event) {
        m_commandInterpreter.handleEvent(event);
    }

    public void handleEvent(PlayerEntered event) {
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (name == null || name.length() < 1)
            name = event.getPlayerName();
        
        Player p = m_botAction.getPlayer(event.getPlayerID());
        if (p == null)
            p = m_botAction.getPlayer(event.getPlayerName());
        if (p.getShipType() != Tools.Ship.SPECTATOR) {
            m_botAction.spec(name);
            m_botAction.spec(name);
        }
        
        if (laggers.containsKey(name))
            m_botAction.sendPrivateMessage(name, "To get back in your duel, PM me with !lagout");
        else 
            m_botAction.sendPrivateMessage(name, greet);
        
        // make SURE there is a UserID available in tblUser if not, create one
        int id = sql_getUserID(name);
        
        DuelPlayer dp = sql_buildPlayer(name, id);
        if (dp != null) {
            players.put(name, dp);
            do_addListTeams(dp);
        } else
            newbies.put(name, id);        
    }

    public void handleEvent(PlayerLeft event) {
        // Get the player name for this event
        String name = m_botAction.getPlayerName(event.getPlayerID());

        if (players.containsKey(name))
            do_removeListTeams(players.remove(name));

        if (playing.containsKey(name) && !laggers.containsKey(name))
            handleLagout(name);
        
        if (!invites.isEmpty()) {
            if (invites.containsKey(name)) {
                TeamInvite invite = invites.remove(name);
                m_botAction.cancelTask(invite);                
                m_botAction.sendPrivateMessage(invite.getInvitee(), "Team invitation from " + invite.getInviter() + " has been cancelled; the inviter left arena.");
            }
            Iterator<TeamInvite> i = invites.values().iterator();
            while (i.hasNext()) {
                TeamInvite invite = i.next();
                if (name.equalsIgnoreCase(invite.getInvitee())) {
                    m_botAction.cancelTask(invite);
                    invites.remove(invite);
                    m_botAction.sendPrivateMessage(invite.getInviter(), "Your team invitation to " + invite.getInvitee() + " has been cancelled; the invitee left arena.");                    
                }
            }
        }
    }
    
    public void handleEvent(PlayerDeath event) {
        Player p1 = m_botAction.getPlayer(event.getKilleeID());
        Player p2 = m_botAction.getPlayer(event.getKillerID());
        if (p1 == null || p2 == null)
            return;
        String name = p1.getPlayerName();
        String killer = p2.getPlayerName();
        if (playing.containsKey(name)) {
            Duel duel = playing.get(name);
            int spawndx = (int) (System.currentTimeMillis()/1000) - duel.getPlayer(name).getLastSpawn();
            int deathdx = (int) (System.currentTimeMillis()/1000) - duel.getPlayer(killer).getTimeOfLastDeath();
            int team = duel.getPlayerNumber(name);
            String[] killers;
            String[] names;
            DuelPlayerStats killerStats = duel.getPlayer(killer);
            DuelPlayerStats nameStats = duel.getPlayer(name);
            int killerFreq;
            int nameFreq;
            int maxDeaths = duel.toWin();
            if (team == 1) {
                names = duel.getChallenger();
                killers = duel.getChallenged();
                nameFreq = duel.getBoxFreq();
                killerFreq = nameFreq + 1;
            } else {
                names = duel.getChallenged();
                killers = duel.getChallenger();
                nameFreq = duel.getBoxFreq() + 1;
                killerFreq = nameFreq - 1;
            }

            // check for teamkill
            if (killer.equalsIgnoreCase(names[0]) || killer.equalsIgnoreCase(names[1])) {
                nameStats.addDeath();
                killers = duel.getOpponent(name);
                // setScoreboard(duel, 0);

                // is killed player out?
                if (nameStats.getDeaths() >= maxDeaths) {
                    m_botAction.sendOpposingTeamMessageByFrequency(killerFreq, name + " is out with " + nameStats.getKills() + ":" + nameStats.getDeaths(), 26);
                    m_botAction.sendOpposingTeamMessageByFrequency(nameFreq, name + " is out with " + nameStats.getKills() + ":" + nameStats.getDeaths(), 26);                
                    nameStats.setOut();
                    playing.remove(name);
                    m_botAction.spec(name);
                    m_botAction.spec(name);
                    m_botAction.setFreq(name, nameFreq);
                }

                // update scorereports
                do_score(duel, 0);                
            } else {
                int s_extra = 0;
                if (duel.getDivisionID() == 2)
                    s_extra = 2;
                // check for Double Kill
                if (duel.getNoCount() && deathdx < s_noCount && killerStats.getLastKiller().equalsIgnoreCase(name)) {
                    if (killDelays.containsKey(killer)) {
                        m_botAction.cancelTask(killDelays.remove(killer));
                    }
                    killerStats.removeDeath();
                    nameStats.removeKill();
                    m_botAction.sendPrivateMessage(killer, "Double Kill, doesn't count.");
                    m_botAction.sendPrivateMessage(name, "Double Kill, doesn't count.");
                    
                    int scoreK = duel.getPlayer(names[0]).getDeaths() + duel.getPlayer(names[1]).getDeaths();
                    int scoreD = duel.getPlayer(killers[0]).getDeaths() + duel.getPlayer(killers[1]).getDeaths();
                    m_botAction.sendOpposingTeamMessageByFrequency(killerFreq, "Score: " + scoreK + "-" + scoreD, 26);
                    m_botAction.sendOpposingTeamMessageByFrequency(nameFreq, "Score: " + scoreD + "-" + scoreK, 26);
                    
                    if (playing.containsKey(name)) {
                        m_botAction.shipReset(name);
                        int[] coord = nameStats.getSafeCoords();
                        m_botAction.warpTo(name, coord[0], coord[1]);
                        
                        coord = nameStats.getCoords();
                        SpawnDelay warp = new SpawnDelay(name, coord[0], coord[1], System.currentTimeMillis());
                        try {
                            spawnDelays.put(name, warp);
                            m_botAction.scheduleTask(warp, s_spawn * 1000);
                        } catch (Exception e) { }
                    }
                    return;
                }

                if (spawndx < s_spawnTime + s_extra) {
                    killerStats.addSpawn();
                    nameStats.addSpawned();
                    nameStats.setLastDeath();
                    if (killerStats.getSpawns() >= s_spawnLimit) {
                        m_botAction.sendOpposingTeamMessageByFrequency(nameFreq, killer + " is out due to spawn kill abuse.", 26);
                        m_botAction.sendOpposingTeamMessageByFrequency(killerFreq, killer + " is out due to spawn kill abuse.", 26); 
                        killerStats.setDeaths(duel.toWin());
                        killerStats.setOut();
                        playing.remove(killer);
                        m_botAction.spec(killer);
                        m_botAction.spec(killer);
                        m_botAction.setFreq(killer, killerFreq);
                        do_score(duel, 1);
                        return;
                    } else {
                        m_botAction.sendPrivateMessage(killer, "Spawns are illegal in this league. If you should continue to spawn you will forfeit your match. (NC)");
                        m_botAction.sendPrivateMessage(name, "The kill was considered a spawn and does not count.");
                        // setScoreboard(duel, 1);
                    }
                } else {
                    if (duel.getNoCount() && nameStats.getDeaths() == duel.toWin() - 1) {
                        nameStats.setLastKiller(killer);
                        nameStats.addDeath();
                        killerStats.addKill();

                        m_botAction.shipReset(name);
                        int[] coord = playing.get(name).getPlayer(name).getSafeCoords();
                        m_botAction.warpTo(name, coord[0], coord[1]);
                        
                        killDelays.put(name, new KillDelay(name, killer, nameStats, killerStats, names, killers, duel));
                        m_botAction.scheduleTask(killDelays.get(name), s_noCount * 1000);
                        coord = nameStats.getCoords();
                        spawnDelays.put(name, new SpawnDelay(name, coord[0], coord[1], System.currentTimeMillis()));
                        m_botAction.scheduleTask(spawnDelays.get(name), (s_noCount + s_spawn) * 1000);
                        return;
                    }
                    nameStats.setLastKiller(killer);
                    nameStats.addDeath();
                    killerStats.addKill();

                    // setScoreboard(duel, 0);
                }

                // is killed player out?
                if (nameStats.getDeaths() >= maxDeaths) {
                    m_botAction.sendOpposingTeamMessageByFrequency(killerFreq, name + " is out with " + nameStats.getKills() + ":" + nameStats.getDeaths(), 26);
                    m_botAction.sendOpposingTeamMessageByFrequency(nameFreq, name + " is out with " + nameStats.getKills() + ":" + nameStats.getDeaths(), 26);                
                    nameStats.setOut();
                    playing.remove(name);
                    m_botAction.spec(name);
                    m_botAction.spec(name);
                    if (duel.getPlayerNumber(name) == 1)
                        m_botAction.setFreq(name, duel.getBoxFreq());
                    else
                        m_botAction.setFreq(name, duel.getBoxFreq() + 1);
                }

                // update scorereports
                do_score(duel, 0);

                // were there any cancel requests?
                if (duel.getCancelState(name)) {
                    duel.toggleCancelGame(name);
                    m_botAction.sendPrivateMessage(name, "Your cancel request has been voided because " + killer + " killed you.");
                }
                if (duel.getCancelState(killer)) {
                    duel.toggleCancelGame(killer);
                    m_botAction.sendPrivateMessage(killer, "Your cancel request has been voided becase you killed " + name + ".");
                }
            }
            // redoBox(duel.getDivisionID(), duel.getBoxNumber(), duel.getPlayerOne().getDeaths() + duel.getPlayerTwo().getDeaths(), duel.getPlayerThree().getDeaths() + duel.getPlayerFour().getDeaths());
        }
        
        if (playing.containsKey(name)) {
            DuelPlayerStats stats = playing.get(name).getPlayer(name);
            int[] coord = stats.getSafeCoords();
            m_botAction.warpTo(name, coord[0], coord[1]);
            m_botAction.shipReset(name);
            
            coord = stats.getCoords();
            SpawnDelay warp = new SpawnDelay(name, coord[0], coord[1], System.currentTimeMillis());
            try {
                spawnDelays.put(name, warp);
                m_botAction.scheduleTask(warp, s_spawn * 1000);
            } catch (Exception e) { }
        }
    }

    public void handleEvent(PlayerPosition event) {
        Player ptest = m_botAction.getPlayer(event.getPlayerID());
        /*
         * Sometimes a player leaves the arena just after a position packet is
         * received; while the position event is distributed, the PlayerLeft
         * event is given to Arena, causing all information about the player to
         * be wiped from record. This also occurs frequently with the
         * PlayerDeath event. Moral: check for null.
         */
        if (ptest == null)
            return;

        String name = ptest.getPlayerName();
            
        if (!playing.containsKey(name))
            return;

        // Get the associated duel
        Duel duel = playing.get(name);
        
        int x = event.getXLocation() / 16;
        int y = event.getYLocation() / 16;

        if (x < duel.getAreaMinX() || y < duel.getAreaMinY() || x > duel.getAreaMaxX() || y > duel.getAreaMaxY()) {


            // Get the associated player
            DuelPlayerStats player = duel.getPlayer(name);
            int ship = player.getShip();

            // Make sure it isn't a ship change allowed for mixed duel
            if (!duel.isLocked()) {
                int[] coords = player.getSafeCoords();
                m_botAction.warpTo(name, coords[0], coords[1]);
                return;
            }

            if (player.getTimeFromLastWarp() > 3 && player.getTimeFromLastDeath() > 3 && player.getTimeFromLastReturn() > 3) {
                // Increment the count of warpings
                player.addWarp();
                player.setLastWarp((int) (System.currentTimeMillis() / 1000));

                if (duel.hasStarted() && player.getWarps() > 1) {
                    int team = duel.getPlayerNumber(name);
                    int warperFreq = player.getFreq();
                    int otherFreq;
                    if (team == 1)
                        otherFreq = duel.getChallengedFreq();
                    else 
                        otherFreq = duel.getChallengerFreq();
                    m_botAction.sendOpposingTeamMessageByFrequency(warperFreq, name + " is out due to warp abuse.", 26);
                    m_botAction.sendOpposingTeamMessageByFrequency(otherFreq, name + " is out due to warp abuse.", 26); 
                    player.setDeaths(duel.toWin());
                    player.setOut();
                    playing.remove(name);
                    m_botAction.spec(name);
                    m_botAction.spec(name);
                    m_botAction.setFreq(name, warperFreq);
                    do_score(duel, 2);
                    return;
                }
                m_botAction.sendPrivateMessage(name, "Warping is not allowed. Do not warp again else you will forfeit your duel.");
                if (duel.isLocked() && m_botAction.getPlayer(name).getShipType() != ship) {
                    player.setLastWarp((int) (System.currentTimeMillis() / 1000));
                    m_botAction.setShip(name, ship);
                    if (!duel.hasStarted()) {
                        int[] coords = player.getSafeCoords();
                        m_botAction.warpTo(name, coords[0], coords[1]);
                    } else {
                        player.removeWarp();
                        WarpPoint p = duel.getRandomWarpPoint();
                        m_botAction.warpTo(name, p.getXCoord(), p.getYCoord());
                    }
                    return;
                }
                if (duel.hasStarted()) {
                    WarpPoint p = duel.getRandomWarpPoint();
                    m_botAction.warpTo(name, p.getXCoord(), p.getYCoord());
                } else {
                    player.removeWarp();
                    int[] coords = player.getSafeCoords();
                    m_botAction.warpTo(name, coords[0], coords[1]);
                }
            }
        }
    }

    public void handleEvent(FrequencyShipChange event) {

        // Get the player name for this event
        String name = m_botAction.getPlayerName(event.getPlayerID());
        // Make sure the player is playing and in spectator mode
        if (!playing.containsKey(name) && event.getShipType() != 0) {
            m_botAction.spec(name);
            m_botAction.spec(name);
            return;
        }

        if (!playing.containsKey(name))
            return;
        
        // Get the associated duel for this player
        Duel duel = playing.get(name);

        DuelPlayerStats player = duel.getPlayer(name);
        int ship = event.getShipType();
        
        if (ship != 0) {
            if (!laggers.containsKey(name) && !duel.isLocked()) {
                if (ship == 6) {
                    m_botAction.setShip(name, player.getShip());
                } else
                    player.setShip(event.getShipType());
            }
            return;
        }
        
        handleLagout(name);
    }
    
    public void handleLagout(String name) {

        // Get the associated duel for this player
        Duel duel = playing.get(name);

        // Get the associated stats object for the player
        DuelPlayerStats player = duel.getPlayer(name);

        String[] others;
        String[] names;
        int team = duel.getPlayerNumber(name);
        if (team == 1) {
            names = duel.getChallenger();
            others = duel.getChallenged();
            
        } else {
            names = duel.getChallenged();
            others = duel.getChallenger();
        }

        if (duel.hasStarted())
            player.addLagout();
        
        if (player.getLagouts() >= s_lagLimit) {
            int laggerFreq = player.getFreq();
            int otherFreq;
            if (team == 1)
                otherFreq = duel.getChallengedFreq();
            else
                otherFreq = duel.getChallengerFreq();
            m_botAction.sendOpposingTeamMessageByFrequency(laggerFreq, name + " is out due to lagouts.", 26);
            m_botAction.sendOpposingTeamMessageByFrequency(otherFreq, name + " is out due to lagouts.", 26); 
            m_botAction.sendPrivateMessage(name, "You have forfeited because you have no more lagouts left.");
            player.setDeaths(duel.toWin());
            player.setOut();
            playing.remove(name);
            m_botAction.spec(name);
            m_botAction.spec(name);
            m_botAction.setFreq(name, laggerFreq);
            do_score(duel, 3);
            if (laggers.containsKey(name)) {
                m_botAction.cancelTask(laggers.get(name));
                laggers.remove(name);
            }
            return;
        }

        m_botAction.sendPrivateMessage(others[0], "Your opponent has lagged out or specced, if he/she does not return in 1 minute will forfeit.");
        m_botAction.sendPrivateMessage(others[1], "Your opponent has lagged out or specced, if he/she does not return in 1 minute will forfeit.");
        if (name.equalsIgnoreCase(names[0]))
            m_botAction.sendPrivateMessage(names[1], "Your partner has lagged out and has 1 minute to return or will forfeit");
        else if (name.equalsIgnoreCase(names[1]))
            m_botAction.sendPrivateMessage(names[0], "Your partner has lagged out and has 1 minute to return or will forfeit");
        m_botAction.sendPrivateMessage(name, "You have 1 minute to return to your duel or you forfeit (!lagout)");

        if (laggers.containsKey(name)) {
            TimerTask t = laggers.remove(name);
            try {
                m_botAction.cancelTask(t);
            } catch (IllegalStateException e) {
                t = null;
            }
        }
        laggers.put(name, new Lagger(name, duel));
        m_botAction.scheduleTask(laggers.get(name), 60000);
    }
    
    /**
     * Player Command Executions
     */

    public void do_showHelp(String name, String message) {
            String help[] = {
                "--Player commands-----------------------------------------------------------------------------",
                "| !h                                 - condensed command list                                 |",
                "| !signup                            - signs you up for the dueling league                    |",
                "| !divisions                         - shows the list of playable divisions                   |",
                "|*!register <partner>:<division#>    - registers you and your partner as a team in <division#>|",
                "| !cancelinvite <name>               - cancels team invite to <name> or cancell all if blank  |",
                "| !join <name>                       - accepts a team invite request from <name>              |",
                "|*!quit <division#>                  - disables your team in <division#>  (quit team)         |",
                "| !rules                             - shows your current rules                               |",
                "| !rules <deaths>                    - sets the default death limit for all challenges(NC off)|",
                "| !rules <deaths>:nc                 - sets death limit and turns no count double kills on    |",
                "| !challenge <teamID>                - challenges <teamID> to a duel if your partner agrees   |",
                "|*!challenge <teamID>:<BoxTypeID>    - challenges <teamID> to a duel in box type <BoxTypeID>  |",
                "|                                       if your partner agrees *details listed below          |",
                "| !agree <teamID>                    - accepts a challenge request from your partner and then |",
                "|                                       challenges <teamID> to a duel                         |",
                "| !accept <teamID>                   - accepts a challenge from <teamID>                      |",
                "|                                       your partner must also accept to begin the duel       |",
                "| !removechallenge <teamID>          - removes the challenge issued to <teamID>               |",
                "| !notplaying                        - toggles on/off notplaying                              |",
                "| !lagout                            - puts you back in to your duel                          |",
                "| !cancel                            - toggles your decision to cancel your duel              |",
                "| !lag <name>                        - returns the lag of player <name>                       |",
                "| !ops                               - shows list of league operators                         |",
                "| !score <box#> or <player>          - shows the score of a duel # or a player                |",
                "| !duels                             - shows the current duels being played                   |",
                "| !teams                             - shows a list of teams available for challenges         |",
                "| !me                                - shows a list of teams you're on                        |",
                "| !enable                            - enables your username                                  |",
                "| !disable                           - disables your username (and all teams you were in)     |",
                "| !rank                              - displays your ranks for all leagues                    |",
                "| !rank <name>                       - displays <name>'s ranks for all leagues                |",
                "|*DIVISIONS: 1-Warbird 2-Javelin 3-Spider 4-Lancaster 5-Mixed                                 |",
                "|  - your <partner> must be present in the arena in order for you to issue the team invite    |",
                "|  - your <partner> must be eligible for the invite (not banned or disabled & no team for div)|",
                "|  - you may register 1 team per division                                                     |",
                "|  - team invites time out after 1 minute                                                     |",
                "|*CHALLENGES: you are required to have a team in the division of the team you are challenging |",
                "|  - your <partner> must be present in the arena in order for you to issue the challenge      |",
                "|  - all participants must have !notplaying off (meaning their team shows on !teams)          |",
                "|*MIXED DIVISION: you will be able to choose a ship to play in right before the duel begins   |",
                "|*BOX TYPE ID: Type 1 is the Warbird style box and Type 2 is the Javelin style box            |",
                "|  - Any division may specify a box type EXCEPT for Javelin. Jav duels are always in Type 2   |",
                "|  - If you do not specify a box type, the default will be used which is 1 except for jav     |",
                "| FOR A MORE CONCISE COMMAND LIST USE !h                                                      |",
                "`---------------------------------------------------------------------------------------------"

            };
        m_botAction.privateMessageSpam( name, help );
        if(leagueOps.containsKey(name.toLowerCase()) || leagueHeadOps.containsKey(name.toLowerCase()) || hiddenOps.containsKey(name.toLowerCase())) {
            String help2[] = {
                " ",
                "--Operator commands---------------------------------------------------------------------------",
                "| !refresh                           - Recycles player list and populates team list           |",
                "|                                       in case !teams does not reflect actual teams in arena |",
                "| !allowuser <name>                  - Allows <name> to register                              |",
                "|*!banuser <name>:<comment>          - Bans <name> from TeamDuel                              |",
                "| !unbanuser <name>                  - Unbans <name>                                          |",
                "| !banned                            - Lists all banned  users                                |",
                "| !comment <name>:<comment>          - Recomments <name>'s ban                                |",
                "| !readcomment <name>                - Gets the ban comment for <name>                        |",
                "| !alias <name>                      - Checks for names with the same IP & MID, IP, and MID   |",
                "| !disableuser <name>                - Disables <name> in the database                        |",
                "| !refresh                           - Used in case bot failed to update present teams        |",
                "| OTHERS: !die (kills bot)    !shutdown (prevents new duels)                                  |",
                "|      !shutdowndie  (prevents new games, kills bot when no duels are active)                 |",
                "| * New bans automatically include the date at the beginning of the comment (not old)         |",
                "`---------------------------------------------------------------------------------------------"
                };
            
            // hidden commands: (probably hidden because they are very error sensitive and powerful)
            // !limit        - sets BotAction.setMessageLimit(msgsPerMin)
            // !version      - returns 1.40
            
            m_botAction.privateMessageSpam( name, help2 );
        }
        if(leagueHeadOps.containsKey(name.toLowerCase())) {
            String help3[] = {
                " ",
                "--Head Operator commands----------------------------------------------------------------------",
                "| !addop <name>                       - Adds <name> to the Duel Operators list (!ops)         |",
                "| !removeop <name>                    - Removes <name> from the Duel Operators list (!ops)    |",
                "| !addhiddenop <name>                 - Adds <name> to the Hidden Operators list (!ops)       |",
                "| !removehiddenop <name>              - Removes <name> to the Hidden Operators list (!ops)    |",
                "| !setgreet <greeting>                - Changes the arena greeting to <greeting>              |",
                "`---------------------------------------------------------------------------------------------"
                };
            
            m_botAction.privateMessageSpam( name, help3 );
        }
    }

    public void do_showQuickHelp(String name, String message) {

        String help[] = {
            "--Player commands (SHORT VERSION)-----------------------------------------------",
            "| !signup               - signs you up for the league                           |",
            "| !divisions            - shows the list of playable divisions                  |",
            "| !reg <partner>:<div#> - registers you and your partner as a team in <div#>    |",
            "| !join <name>          - accepts a team invite request from <name>             |",
            "| !quit <division#>     - disables your team in <division#>  (quit team)        |",
            "| !rules <deaths>:nc    - sets death limit and turns no count double kills on   |",
            "| !ch <teamID>          - challenges <teamID> to duel (add :1 or :2 for boxtype)|",
            "| !agree <teamID>       - accepts a challenge request from your partner         |",
            "| !accept <teamID>      - accepts a challenge from <teamID>                     |",
            "| !rc <teamID>          - removes the challenge issued to <teamID>              |",
            "| !np                   - toggles on/off notplaying                             |",
            "| !lagout               - puts you back in your duel                            |",
            "| !cancel               - toggles your decision to cancel your duel             |",
            "| !teams                - shows a list of teams available for challenges        |",
            "| !enable               - enables your username                                 |",
            "| !disable              - disables your username (and all teams you were in)    |",
            "`-------------------------------------------------------------------------------"

        };
        m_botAction.privateMessageSpam(name, help);
    }

    public void do_showAbout(String name, String message) {

        String about[] = {
            "--About Team Duel----------------------------------------------------------------",
            "| Team Duel is an extension of TWEL that allows 2v2 dueling in nearly any ship.  |",
            "|  It is split up into 5 divisions: 1 Warbird, 2 Javelin, 3 Spider, 4 Lancaster, |",
            "|  and 5 Mixed. Mixed division allows you to play in any ship you want.          |",
            "|  NOTE: PM the bot all !commands or they will be ignored                        |",
            "| To get started, follow these simple instructions:                              |",
            "| 1. Find a partner and decide on which division you want to play                |",
            "| 2. Type !register <Partner>:<Division#>  this will send your partner an invite |",
            "| 3. Your partner must type !join <YourName>  in order to create your team       |",
            "| 4. Type !teams to see what teams are available to challenge in your DIVISION   |",
            "| 5. Using their TeamID#, type !challenge <TeamID#>                              |",
            "| 6. Now your partner must type !agree <TeamID#>  to confirm this challenge      |",
            "| 7. Once the other team's members accept the challenge, your duel will begin!   |",
            "`--------------------------------------------------------------------------------"

        };
        m_botAction.privateMessageSpam(name, about);
        
    }
    
    public void do_showDivisions(String name, String message) {
        String divisions[] = {
                "--League Divisions--",
                "| # | Name          |",
                "|---|---------------|",
                "| 1 | Warbird       |",
                "| 2 | Javelin       |",
                "| 3 | Spider        |",
                "| 4 | Lancaster     |",
                "| 5 | Mixed         |",
                "`---\\ aka Freestyle |",
                "     `--------------"
        };
        m_botAction.privateMessageSpam( name, divisions );
    }

    public void do_showLeagueOps(String name, String message) {
        do_updateOps();
        String hops = "Head League Operators: ";
        Iterator<String> it1 = leagueHeadOps.values().iterator();
        while (it1.hasNext()) {
            if (it1.hasNext())
                hops += (String) it1.next() + ", ";
            else
                hops += (String) it1.next();
        }
        String ops = "League Operators: ";
        Iterator<String> it2 = leagueOps.values().iterator();
        while (it2.hasNext()) {
            if (it2.hasNext())
                ops += (String) it2.next() + ", ";
            else
                ops += (String) it2.next();
        }
        hops = hops.substring(0, hops.length() - 2);
        ops = ops.substring(0, ops.length() - 2);
        m_botAction.sendPrivateMessage(name, hops);
        m_botAction.sendPrivateMessage(name, ops);

        if (leagueHeadOps.containsKey(name.toLowerCase())) {
            String hidden = "Hidden Ops (visible only to HeadOps): ";
            Iterator<String> it3 = hiddenOps.values().iterator();
            while (it3.hasNext()) {
                if (it3.hasNext())
                    hidden += (String) it3.next() + ", ";
                else
                    hidden += (String) it3.next();
            }
            hidden = hidden.substring(0, hidden.length() - 2);
            m_botAction.sendPrivateMessage(name, hidden);
        }
    }
    
    public void do_showTeams(String name, String message) {
        if (message != null && message.length() > 0) {
            do_showYou(name, message);
            return;
        }
        
        if (teamList.isEmpty()) {
            m_botAction.sendPrivateMessage(name, "No teams available.");
        } else {
            ArrayList<ArrayList<String>> messages = new ArrayList<ArrayList<String>>(5);
            for (int i = 0; i < 5; i++) {
                messages.add(i, new ArrayList<String>());
            }
            m_botAction.sendPrivateMessage(name, "The following teams are available for challenges (if list is blank then all teams are notplaying):");
            m_botAction.sendPrivateMessage(name, "--Teams------------------------------------------------");
            m_botAction.sendPrivateMessage(name, "| ID# | Division | Partners                            |");
            m_botAction.sendPrivateMessage(name, "|-----|----------|-------------------------------------|");
            Iterator<DuelTeam> it = teamList.values().iterator();
            while (it.hasNext()) {
                DuelTeam team = it.next();
                if (!team.getNotPlaying() && !name.equalsIgnoreCase(team.getNames()[0]) && !name.equalsIgnoreCase(team.getNames()[1])) {
                    int index = team.getDivision() - 1;
                    ArrayList<String> temp = messages.get(index);
                    temp.add(team.toString());
                    messages.set(index, temp);
                }
            }
            for (int x = 0; x < 5; x++) {
                ArrayList<String> print = messages.get(x);
                if (!print.isEmpty()) {
                    String[] division = print.toArray(new String[print.size()]);
                    m_botAction.privateMessageSpam(name, division);
                }
            }
            m_botAction.sendPrivateMessage(name, "`------------------------------------------------------");
        }
        
    }

    public void do_showScore(String name, String message) {
        String player = m_botAction.getFuzzyPlayerName(message);
        int i = -1;
        if (player == null) {
            try {
                i = Integer.parseInt(message);
            } catch (Exception e) {
                m_botAction.sendPrivateMessage(name, "Can't read a player name or box # from your input.  Please check the name/number and try again.");
                return;
            }
            if (duels.containsKey(new Integer(i))) {
                m_botAction.sendPrivateMessage(name, duels.get(new Integer(Integer.parseInt(message))).showScore());
            } else {
                m_botAction.sendPrivateMessage(name, "No score available for requested duel (box is probably empty).");
            }
        } else {
            if (playing.containsKey(player)) {
                m_botAction.sendPrivateMessage(name, playing.get(player).showScore());
            } else {
                m_botAction.sendPrivateMessage(name, "No score available for requested duel (player is probably not playing).");
            }
        }
    }
    
    public void do_showDuels(String name, String message) {
        if (duels.isEmpty()) {
            m_botAction.sendPrivateMessage(name, "No duels are being played.");
        } else {
            Iterator<Duel> it = duels.values().iterator();
            ArrayList<ArrayList<String>> duellist = new ArrayList<ArrayList<String>>();
            for (int i = 0; i < 6; i++) {
                duellist.add(i, new ArrayList<String>());
            }
            while (it.hasNext()) {
                Duel duel = it.next();
                ArrayList<String> division = duellist.get(duel.getDivisionID());
                division.add(duel.toString());
                duellist.set(duel.getDivisionID(), division);
            }
            if (!duellist.isEmpty()) {
                m_botAction.sendPrivateMessage(name, "--Duels----------------------------------------------------------------------------");
                m_botAction.sendPrivateMessage(name, "| Box | Division | Teams                                                           |");
                m_botAction.sendPrivateMessage(name, "|-----|----------|-----------------------------------------------------------------|");
                for (int i = 1; i <= 5; i++) {
                    ArrayList<String> div = duellist.get(i);
                    String[] msg = div.toArray(new String[div.size()]);
                    m_botAction.privateMessageSpam(name, msg);                
                }
                m_botAction.sendPrivateMessage(name, "`----------------------------------------------------------------------------------");
            }
        }
    }
    
    public void do_showLag(String name, String message) {
        String player = m_botAction.getFuzzyPlayerName(message);
        if (player == null) {
            m_botAction.sendPrivateMessage(name, "Unable to find player.");
            return;
        }
        if (!from.equals("")) {
            m_botAction.sendPrivateMessage(name, "Please try again in a few seconds.");
            return;
        }
        from = player;
        to = name;
        m_botAction.sendUnfilteredPrivateMessage(player, "*lag");
    }    
    
    public void do_showYou(String name, String message) {
        DuelPlayer dp = players.get(message);
        if (dp == null) {
            m_botAction.sendPrivateMessage(name, "Loading player information...");
            dp = sql_buildPlayer(message);
        }
        if (dp == null) {
            m_botAction.sendPrivateMessage(name, "Player, " + message + " not found.");
            return;
        }
        
        if (!dp.isBanned()) {
            if (dp.isEnabled()) {
                if (dp.hasTeams()) {
                    int[] teams = dp.getTeams();
                    ArrayList<String> msgs = new ArrayList<String>();
                    //TeamsTeamsTeamsTeamsTeamsTeamsTeamsTeamsTeamsTeams
                    String title = "--" + message + "'s Teams";
                    while (title.length() < 45)
                        title += "-";
                    msgs.add(title);
                    msgs.add("| ID# | Division | Partner                   |");
                    msgs.add("|-----|----------|---------------------------");
                    for (int i = 1; i <= 5; i++) {
                        if (teams[i] > -1) {
                            int teamID = teams[i];
                            String divisionName = getDivision(i);
                            String partner = dp.getPartner(i);
                            String msg = "|";
                            if (teamID / 10 > 0) {
                                if (teamID / 100 > 0) {
                                    if (teamID / 1000 > 0) {
                                        msg += " " + teamID + "|"; 
                                    } else
                                        msg += "  " + teamID + " |";
                                } else
                                    msg += "  " + teamID + " |";
                            } else // "| ID#  | Division | Partners"
                                msg += "   " + teamID + " |";

                            if (i == 1 || i == 2) 
                                msg += " " + divisionName + "  | ";
                            else if (i == 3)
                                msg += "  " + divisionName + "  | ";
                            else if (i == 4 || i == 7)
                                msg += " " + divisionName + "| ";
                            else if (i == 5)
                                msg += "  " + divisionName + "   | ";
                            msg += partner;

                            while (msg.length() < 45) 
                                msg += " ";
                            msg += "|";
                            msgs.add(msg);
                        }
                    } 
                    m_botAction.privateMessageSpam(name, msgs.toArray(new String[msgs.size()]));
                    m_botAction.sendPrivateMessage(name, "`--------------------------------------------");
                } else
                    m_botAction.sendPrivateMessage(name, dp.getName() + " is not currently on any teams.");
            } else 
                m_botAction.sendPrivateMessage(name, dp.getName() + " is not enabled.");
        } else
            m_botAction.sendPrivateMessage(name, dp.getName() + " is not allowed to play in this league.");
    }
    
    public void do_showMe(String name, String message) {
        DuelPlayer dp = players.get(name);
        if (dp == null) {
            m_botAction.sendPrivateMessage(name, "Unable to retreive your team information. You must be signed up and registered to a team.");
            return;
        }
        if (!dp.isBanned()) {
            if (dp.isEnabled()) {
                if (dp.hasTeams()) {
                    int[] teams = dp.getTeams();
                    ArrayList<String> msgs = new ArrayList<String>();
                    //TeamsTeamsTeamsTeamsTeamsTeamsTeamsTeamsTeamsTeams
                    msgs.add("--My Teams-----------------------------------");
                    msgs.add("| ID# | Division | Partner                   |");
                    msgs.add("|-----|----------|---------------------------");
                    for (int i = 1; i <= 5; i++) {
                        if (teams[i] > -1) {
                            int teamID = teams[i];
                            String divisionName = getDivision(i);
                            String partner = dp.getPartner(i);
                            String msg = "|";
                            if (teamID / 10 > 0) {
                                if (teamID / 100 > 0) {
                                    if (teamID / 1000 > 0) {
                                        msg += " " + teamID + "|"; 
                                    } else
                                        msg += "  " + teamID + " |";
                                } else
                                    msg += "  " + teamID + " |";
                            } else // "| ID#  | Division | Partners"
                                msg += "   " + teamID + " |";

                            if (i == 1 || i == 2) 
                                msg += " " + divisionName + "  | ";
                            else if (i == 3)
                                msg += "  " + divisionName + "  | ";
                            else if (i == 4 || i == 7)
                                msg += " " + divisionName + "| ";
                            else if (i == 5)
                                msg += "  " + divisionName + "   | ";
                            msg += partner;

                            while (msg.length() < 45) 
                                msg += " ";
                            msg += "|";
                            msgs.add(msg);
                        }
                    } 
                    m_botAction.privateMessageSpam(name, msgs.toArray(new String[msgs.size()]));
                    m_botAction.sendPrivateMessage(name, "`--------------------------------------------");
                } else
                    m_botAction.sendPrivateMessage(name, "You are not currently on any teams. Find a partner and use !reg partner:division");
            } else 
                m_botAction.sendPrivateMessage(name, "You must be enabled in order to be on a team.");
        } else
            m_botAction.sendPrivateMessage(name, "You have been banned from this league.");
    }
    
    public void do_signUp(String name, String message) {
        aliasChecker = "";
        aliasCheck = false;
        m_botAction.sendUnfilteredPrivateMessage(name, "*info");
    }

    public void do_registerTeam(String name, String message) {
        // !register PArtners NAme:1
        String[] vars = message.split(":");
        vars[0] = m_botAction.getFuzzyPlayerName(vars[0]);
        if (vars[0] == null) {
            m_botAction.sendPrivateMessage(name, "Error sending team invite: player must be in this arena");
            return;
        }
        DuelPlayer part = players.get(vars[0]);
        DuelPlayer dp = players.get(name);
        if (dp == null) {
            m_botAction.sendPrivateMessage(name, "You must use !signup to signup to play before you can register a team.");
            return;
        }
        int typeID = -1;
        if (dp.isEnabled()) {
            if (!dp.isBanned()) {
                if (vars.length == 2) {
                    if (do_partnerCheck(name, vars[0])) {
                        try {
                            typeID = Integer.parseInt(vars[1]);
                            if (dp.getTeam(typeID) == -1) {
                                if (part.getTeam(typeID) == -1) {
                                    if (invites.containsKey(name)) {
                                        m_botAction.sendPrivateMessage(name, "You are only allowed to send one team invite at a time.");
                                        return;
                                    }
                                    TeamInvite invite = new TeamInvite(name, vars[0], typeID);
                                    invites.put(name, invite);
                                    m_botAction.scheduleTask(invite, 60000);
                                } else
                                    m_botAction.sendPrivateMessage(name, part.getName() + " is all ready registered for a team in that division.");
                            } else 
                                m_botAction.sendPrivateMessage(name, "You are all ready registered for a team in that division.");
                        } catch (NumberFormatException e) {
                            m_botAction.sendPrivateMessage(name, vars[1] + " is not a valid division ID number.");
                            m_botAction.sendPrivateMessage(name, "Division ID#s: 1-Warbird 2-Javelin 3-Spider 4-Lancaster 5-Mixed");
                        }
                    }
                } else {
                    m_botAction.sendPrivateMessage(name, "Please use the following syntax: !register <Partner Name>:<Division ID#>");
                    m_botAction.sendPrivateMessage(name, "Division ID#s: 1-Warbird 2-Javelin 3-Spider 4-Lancaster 5-Mixed");
                }
            } else 
                m_botAction.sendPrivateMessage(name, "You have been banned from this league.");
        } else 
            m_botAction.sendPrivateMessage(name, "Your name must be enabled in order to register a team.");
    }
    
    public void do_cancelTeamInvite(String name, String message) {
        if (!invites.isEmpty()) {
            if (invites.containsKey(name)) {
                TeamInvite invite = invites.remove(name);
                m_botAction.cancelTask(invite);
                m_botAction.sendPrivateMessage(name, "Your team invitation to " + invite.getInvitee() + " has been cancelled.");
            }
        } else
            m_botAction.sendPrivateMessage(name, "No team invitations found.");
    }
    
    public void do_joinTeam(String name, String message) {
        if (m_botAction.getFuzzyPlayerName(message) == null) {
            m_botAction.sendPrivateMessage(name, "Error joining team: player team invite not found");
            return;
        }
        TeamInvite invite = null;
        if (invites.containsKey(message)) {
            invite = invites.remove(message);
            m_botAction.cancelTask(invite);
            DuelPlayer per = players.get(message);
            DuelPlayer pee = players.get(name);
            int div = invite.division;
            int id = sql_createTeam(per, pee, div);
            if (id > 0) {
                per.setTeams(id, div);
                pee.setTeams(id, div);
                per.setPartner(pee, div);
                pee.setPartner(per, div);
                m_botAction.sendPrivateMessage(message, "Team creation successful! Your TeamID for the " + getDivision(div) + " Division is " + id);
                m_botAction.sendPrivateMessage(name, "Team creation successful! Your TeamID for the " + getDivision(div) + " Division is " + id);
                do_teamList();

                if (!teamList.containsKey(id))
                    do_refreshArena();
                
            } else {
                m_botAction.sendPrivateMessage(invite.inviter, "Team creation failed. Please contact an Operator.");
                m_botAction.sendPrivateMessage(invite.invitee, "Team creation failed. Please contact an Operator.");
            }
        } else
            m_botAction.sendPrivateMessage(name, "No invites from " + message + " found.");
    }

    public void do_teamChallenge(String name, String message) {
        // Shutdown mode check
        if (shutDown) {
            m_botAction.sendPrivateMessage(name, "Currently in 'ShutDown' mode, no new duels may begin at this time: " + shutDownMessage);
            return;
        }
        DuelPlayer dp = players.get(name);
        if (dp == null) {
            m_botAction.sendPrivateMessage(name, "You must be signed up and registered to challenge teams.");
            return;
        }
        int challengedTeam = -1;
        int boxType = -1;
        try {
            if (message.indexOf(':') > 0) {
                boxType = Integer.parseInt(message.substring(message.indexOf(':') + 1));
                message = message.substring(0, message.indexOf(':'));
                if (boxType != -1 && boxType != 1 && boxType != 2) {
                    m_botAction.sendPrivateMessage(name, "Invalid box type. The only acceptable box types are 1 and 2");
                    return;
                }
            }
            challengedTeam = Integer.parseInt(message);
        } catch (Exception e) {
            m_botAction.sendPrivateMessage(name, "Incorrect syntax, please use the following: !challenge <teamID> or !challenge <teamID>:<boxtype#>");
            return;
        }
        // Player banned check
        if (dp.isBanned()) {
            m_botAction.sendPrivateMessage(name, "You have been banned from this league.");
            return;
        }

        // Signed up check
        if (!dp.isEnabled()) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, you have not signed up or have disabled this name.");
            return;
        }
        // Notplaying challenger check
        if (notPlaying.contains(name)) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, you have enabled 'notplaying', toggle it off with !notplaying.");
            return;
        }
        
        DuelTeam challed = teamList.get(challengedTeam);
        if (challed == null) {
            m_botAction.sendPrivateMessage(name, "Invalid team, please select from the list of available teams by using !teams");
            return;            
        }

        int division = challed.getDivision();
        if (boxType == 1 && division == 2) {
            m_botAction.sendPrivateMessage(name, "Javelin Division is only playable in box type 2. Box type changed to type 2.");
            boxType = 2;
        } else if (boxType == 2 && division == 1) {
            m_botAction.sendPrivateMessage(name, "Warbird Division is only playable in box type 1. Box type changed to type 1.");
            boxType = 1;            
        }
        
        if (boxType == -1){
            if (division == 2)
                boxType = 2;
            else
                boxType = 1;
        }
        
        int challengerTeam = dp.getTeam(division);
        DuelTeam challer = teamList.get(challengerTeam);
        if (challengerTeam < 0) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, you do not have a team in this division.");
            return;
        }
        String[] challenger = challer.getNames();
        if (notPlaying.contains(challenger[0]) || notPlaying.contains(challenger[1])) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, your partner has enabled 'notplaying', have him toggle it off in order to challenge.");
            return;            
        }
        if (challed.getNotPlaying()) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, your opponent has enabled 'notplaying'.");
            return;             
        }
        
        if (challengerTeam == challengedTeam) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, you cannot challenge yourself.");
            return;
        }
        
        if (playing.containsKey(name)) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, you are already dueling.");
            return;
        }
        if (challed.getNowPlaying()) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge that team is already dueling.");
            return;
        }
        if (!boxOpen(boxType)) {
            m_botAction.sendPrivateMessage(name, "There is currently no duel box open for that division or box, please challenge again when a box opens up.");
            return;
        }
        
        if (sql_gameLimitReached(challengerTeam, challengedTeam, division)) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, you are only allowed "
                    + s_duelLimit
                    + " duels against a team every "
                    + s_duelDays
                    + " day(s).");
            return;
        }
        
        if (challenges.containsKey("" + challengerTeam + ":" + challengedTeam))
            challenges.remove("" + challengerTeam + ":" + challengedTeam);
        
        String partner = "";
        
        String[] challenged = challed.getNames();        
        
        if (name.equals(challenger[0]))
            partner = challenger[1];
        else
            partner = challenger[0];
        TeamChallenge teamChall = new TeamChallenge(challengerTeam, challengedTeam, division, boxType, name, partner);
        String type = getDivision(division);
        m_botAction.sendPrivateMessage(name, "You have sent a challenge request to your partner, " + partner + ". He must !agree " + challengedTeam + " in order for the challenge to be issued.");
        m_botAction.sendPrivateMessage(partner, "Your partner, " + name + ", wants to send a " + type + " challenge to " + challenged[0] + " and " + challenged[1] + " in box type " + boxType + ". If you want to send this challenge, PM me with !agree " + challengedTeam);
        teamChallenges.put("" + challengerTeam + ":" + challengedTeam, teamChall);           
    }
    
    public void do_issueChallenge(int challengerTeam, int challengedTeam, String initiater, int division, int boxType) {        
        String[] challenger = teamList.get(challengerTeam).getNames();
        String[] challenged = teamList.get(challengedTeam).getNames();  
        
        DuelPlayer[] challengerPlayers = new DuelPlayer[2];
        DuelPlayer[] challengedPlayers = new DuelPlayer[2];
        
        challengerPlayers[0] = players.get(challenger[0]);
        challengerPlayers[1] = players.get(challenger[1]);
        challengedPlayers[0] = players.get(challenged[0]);
        challengedPlayers[1] = players.get(challenged[1]);      
              
        DuelPlayer challengingPlayer = players.get(initiater);
        String rules;
        if (challengingPlayer.getNoCount())
            rules = "Rules: " + challengingPlayer.getDeaths() + " death elimination, no count double kills, box type " + boxType;
        else
            rules = "Rules: " + challengingPlayer.getDeaths() + " death elimination, box type " + boxType;

        int[] lag1 = new int[2];
        int[] lag2 = new int[2];
        lag1[0] = challengerPlayers[0].getAverageLag();
        lag1[1] = challengerPlayers[1].getAverageLag();
        lag2[0] = challengedPlayers[0].getAverageLag();
        lag2[1] = challengedPlayers[1].getAverageLag();
        String[] avgLag1 = new String[2];
        String[] avgLag2 = new String[2];
        if (lag1[0] > 0)
            avgLag1[0] = String.valueOf(lag1[0]);
        else
            avgLag1[0] = "Unknown";
        if (lag1[1] > 0)
            avgLag1[1] = String.valueOf(lag1[1]);
        else
            avgLag1[1] = "Unknown";
        if (lag2[0] > 0)
            avgLag2[0] = String.valueOf(lag2[0]);
        else
            avgLag2[0] = "Unknown";
        if (lag2[1] > 0)
            avgLag2[1] = String.valueOf(lag2[1]);
        else
            avgLag2[1] = "Unknown";
        
        challenges.put("" + challengerTeam + ":" + challengedTeam, new DuelChallenge(challengerTeam, challengedTeam, challenger, challenged, challengingPlayer, division, boxType));
        
        String type = getDivision(division);
        
        m_botAction.sendPrivateMessage(challenger[0], "Your challenge has been sent to Team #" + challengedTeam + " (" + challenged[0] + " and " + challenged[1] + ").");
        m_botAction.sendPrivateMessage(challenger[1], "Your challenge has been sent to Team #" + challengedTeam + " (" + challenged[0] + " and " + challenged[1] + ").");
        m_botAction.sendPrivateMessage(challenger[0], challenged[0] + "'s average lag: " + avgLag2[0]);
        m_botAction.sendPrivateMessage(challenger[0], challenged[1] + "'s average lag: " + avgLag2[1]);
        m_botAction.sendPrivateMessage(challenger[1], challenged[0] + "'s average lag: " + avgLag2[0]);
        m_botAction.sendPrivateMessage(challenger[1], challenged[1] + "'s average lag: " + avgLag2[1]);
        m_botAction.sendPrivateMessage(challenger[0], rules);
        m_botAction.sendPrivateMessage(challenger[1], rules);
        m_botAction.sendPrivateMessage(challenged[0], challenger[0] + " and " + challenger[1] + " are challenging you and " + challenged[1] + " to a " + type + " duel in box type " + boxType + ". PM me with, !accept " + challengerTeam + " to accept.");
        m_botAction.sendPrivateMessage(challenged[1], challenger[0] + " and " + challenger[1] + " are challenging you and " + challenged[0] + " to a " + type + " duel in box type " + boxType + ". PM me with, !accept " + challengerTeam + " to accept.");
        m_botAction.sendPrivateMessage(challenged[0], challenger[0] + "'s average lag: " + avgLag1[0]);
        m_botAction.sendPrivateMessage(challenged[1], challenger[0] + "'s average lag: " + avgLag1[0]);
        m_botAction.sendPrivateMessage(challenged[0], challenger[1] + "'s average lag: " + avgLag1[1]);
        m_botAction.sendPrivateMessage(challenged[1], challenger[1] + "'s average lag: " + avgLag1[1]);
        m_botAction.sendPrivateMessage(challenged[0], rules);
        m_botAction.sendPrivateMessage(challenged[1], rules);
    }
    
    public void do_teamAccept(String name, String message) {
        if (shutDown) {
            m_botAction.sendPrivateMessage(name, "Currently in 'ShutDown' mode, no new duels may begin at this time: " + shutDownMessage);
            return;
        }
        
        if (!players.containsKey(name))
            return;
        DuelPlayer dp = players.get(name);
        if (teamChallenges.isEmpty()) {
            m_botAction.sendPrivateMessage(name, "Error: no team challenges found.");
            return;
        }
        int challengedTeam = -1;
        try {
            challengedTeam = Integer.parseInt(message);
        } catch (NumberFormatException e) {
            m_botAction.sendPrivateMessage(name, "Incorrect syntax, please use the following: !agree <teamID>");
            return;            
        }
        
        if (dp == null) {
            m_botAction.sendPrivateMessage(name, "You must !signup first and register a team");
            return;
        }
        
        if (dp.isBanned()) {
            m_botAction.sendPrivateMessage(name, "You have been banned from this league.");
            return;
        }
        
        // Signed up check
        if (!dp.isEnabled()) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, you have not signed up or have disabled this name.");
            return;
        }
        
        if (notPlaying.contains(name)) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, you have enabled 'notplaying', toggle it off with !notplaying.");
            return;
        }
        
        int division = -1;
        DuelTeam challed;
        if (teamList.containsKey(challengedTeam)) {
            challed = teamList.get(challengedTeam);
            division = challed.getDivision();
        } else {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge. Team #" + challengedTeam + " is not present in this arena.");
            return;
        }
        int challengerTeam = dp.getTeam(division);
        if (!teamList.containsKey(challengerTeam)) {
            m_botAction.sendPrivateMessage(name, "Your partner must have left the arena. Both players must be present to challenge.");
            return;
        }
        String key = "" + challengerTeam + ":" + challengedTeam;
        
        if (!teamChallenges.containsKey(key)) {
            m_botAction.sendPrivateMessage(name, "Your partner has not tried to challenge the team you specified.");
            return;
        }
        
        if (!name.equals(teamChallenges.get(key).getPartner())) {
            m_botAction.sendPrivateMessage(name, "You are not " + teamChallenges.get(key).getPartner() + "!");
            return;
        }
        
        if (teamList.containsKey(challengedTeam) && teamList.get(challengedTeam).getNotPlaying()) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, the opposing team is !notplaying.");
            return;            
        }
        
        if (teamList.containsKey(challengedTeam) && teamList.get(challengedTeam).getNowPlaying()) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, the opposing team is all ready playing.");
            return;            
        }
        
        TeamChallenge teamChall = teamChallenges.remove("" + challengerTeam + ":" + challengedTeam);
        
        if (teamChall.getElapsedTime() > s_challengeTime) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, the challenge has expired.");
            return;
        }        
        do_issueChallenge(teamChall.getChallengers(), teamChall.getChallenged(), teamChall.getChallenger(), division, teamChall.getBoxType());
    }

    public void do_acceptChallenge(String name, String message) {
        if (shutDown) {
            m_botAction.sendPrivateMessage(name, "Currently in 'ShutDown' mode, no new duels may begin at this time: " + shutDownMessage);
            return;
        }
        
        DuelPlayer dp;
        if (players.containsKey(name))
            dp = players.get(name);
        else {
            m_botAction.sendPrivateMessage(name, "Error: you were not found on the player list. You must be signed up and registered with a team to accept challenges.");
            return;
        }
        int challengerTeam = -1;
        try {
            challengerTeam = Integer.parseInt(message);
        } catch (NumberFormatException e) {
            m_botAction.sendPrivateMessage(name, "Incorrect syntax, please use the following: !accept <teamID>");
            return;
        }
        
        if (dp.isBanned()) {
            m_botAction.sendPrivateMessage(name, "You have been banned from this league.");
            return;
        }
        
        // Signed up check
        if (!dp.isEnabled()) {
            m_botAction.sendPrivateMessage(name, "Unable to issue challenge, you have not signed up or have disabled this name.");
            return;
        }
        
        if (notPlaying.contains(name)) {
            m_botAction.sendPrivateMessage(name, "Unable to accept challenge, you have enabled 'notplaying', toggle it off with !notplaying.");
            return;
        }

        int division = -1;
        DuelTeam challer;
        if (teamList.containsKey(challengerTeam)) {
            challer = teamList.get(challengerTeam);
            division = challer.getDivision();
        } else {
            m_botAction.sendPrivateMessage(name, "Unable to accept challenge, team " + message + " is not present in this arena.");
            return;
        }
        int challengedTeam = dp.getTeam(division);
        if (!teamList.containsKey(challengedTeam)) {
            m_botAction.sendPrivateMessage(name, "Your partner must have left the arena. Both players must be present to accept a challenge.");
            return;
        }
        
        String key = "" + challengerTeam + ":" + challengedTeam;
        if (!challenges.containsKey(key)) {
            m_botAction.sendPrivateMessage(name, "Unable to accept challenge, team " + challengerTeam + " has not challenged you");
            return;
        }
        if (teamList.get(challengerTeam).getNotPlaying()) {
            m_botAction.sendPrivateMessage(name, "Unable to accept challenge, team " + challengerTeam + " has enabled notplaying.");
            return;
        }
        if (playing.containsKey(name)) {
            m_botAction.sendPrivateMessage(name, "Unable to accept challenge, you are already dueling.");
            return;
        }
        if (teamList.get(challengerTeam).getNowPlaying()) {
            m_botAction.sendPrivateMessage(name, "Unable to accept challenge, team " + challengerTeam + " is already dueling.");
            return;
        }
        DuelChallenge thisChallenge = challenges.get(key);
        if (thisChallenge == null) {
            m_botAction.sendPrivateMessage(name, "Unable to accept challenge, team " + challengerTeam + " has not challenged you.");
            return;
        }
        boolean[] accepted = thisChallenge.getAccepted();
        if (thisChallenge.getElapsedTime() > s_challengeTime) {
            challenges.remove(key);
            m_botAction.sendPrivateMessage(name, "Unable to accept challenge, the challenge has expired.");
            return;
        }
        DuelBox thisBox = getDuelBox(thisChallenge.getBoxType());
        // Add queue system for dueling
        if (thisBox == null) {
            m_botAction.sendPrivateMessage(name, "Unable to accept challenge, all duel boxes are full.");
            return;
        }
        String[] challenger = thisChallenge.getChallenger();
        String[] challenged = thisChallenge.getChallenged();
        if (name.equalsIgnoreCase(challenged[0]) && accepted[0] && !accepted[1]) {
            m_botAction.sendPrivateMessage(name, "You have all ready accepted this challenge. Your partner must accept it as well.");
            return;
        } else if (name.equalsIgnoreCase(challenged[1]) && accepted[1] && !accepted[0]) {
            m_botAction.sendPrivateMessage(name, "You have all ready accepted this challenge. Your partner must accept it as well.");
            return;
        } else if (name.equalsIgnoreCase(challenged[0]) && !accepted[0] && !accepted[1]) {
            thisChallenge.acceptOne();
            m_botAction.sendPrivateMessage(name, "The challenge will be accepted once your partner accepts it as well.");
            m_botAction.sendPrivateMessage(challenged[1], "Your partner has accepted the challenge. If you also wish to accept PM me !accept " + challengerTeam);
            return;
        } else if (name.equalsIgnoreCase(challenged[1]) && !accepted[1] && !accepted[0]) {
            thisChallenge.acceptTwo();
            m_botAction.sendPrivateMessage(name, "The challenge will be accepted once your partner accepts it as well.");
            m_botAction.sendPrivateMessage(challenged[0], "Your partner has accepted the challenge. If you also wish to accept PM me !accept " + challengerTeam);
            return;
        } else if ((name.equalsIgnoreCase(challenged[0]) && !accepted[0] && accepted[1]) || (name.equalsIgnoreCase(challenged[1]) && accepted[0] && !accepted[1])) {
            thisChallenge.acceptOne();
            thisChallenge.acceptTwo();
        }
        
        if (!challenges.isEmpty()) {
            Set<String> list = challenges.keySet();
            for (String ch : list) {
                if (!ch.equals(key) && ch.startsWith("" + challengerTeam) || ch.startsWith("" + challengedTeam) || ch.endsWith("" + challengerTeam) || ch.endsWith("" + challengedTeam))
                    challenges.remove(ch);
            }
        }
        
        duels.put(new Integer(thisBox.getBoxNumber()), new Duel(thisBox, thisChallenge));
        playing.put(challenger[0], duels.get(new Integer(thisBox.getBoxNumber())));
        playing.put(challenger[1], duels.get(new Integer(thisBox.getBoxNumber())));
        playing.put(challenged[0], duels.get(new Integer(thisBox.getBoxNumber())));
        playing.put(challenged[1], duels.get(new Integer(thisBox.getBoxNumber())));
        startDuel(duels.get(new Integer(thisBox.getBoxNumber())), challenger, challenged);

    }

    public void do_removeChallenge(String name, String message) {
        int challengedTeam = -1;
        try {
            challengedTeam = Integer.parseInt(message);
        } catch (NumberFormatException e) {
            m_botAction.sendPrivateMessage(name, "Invalid syntax, please use the following syntax: !removechallenge <teamID>");
            return;
        }
        if (teamList.containsKey(challengedTeam)) {
            DuelTeam challed = teamList.get(challengedTeam);
            int div = challed.getDivision();
            int challengerTeam = players.get(name).getTeam(div);
            String key = "" + challengerTeam + ":" + challengedTeam;
            if (challenges.containsKey(key) && teamChallenges.containsKey(key)) {
                teamChallenges.remove(key);
                challenges.remove(key);
            } else if (challenges.containsKey(key) && !teamChallenges.containsKey(key)) {
                challenges.remove(key);                
            } else if (!challenges.containsKey(key) && teamChallenges.containsKey(key)) {
                teamChallenges.remove(key);                
            } else {
                m_botAction.sendPrivateMessage(name, "Unable to remove challenge, no such challenge exists.");
                return;                
            }
        } else {
            int division = sql_getTeamDivision(challengedTeam);
            int challengerTeam = sql_getTeams(name).get(division);
            String key = "" + challengerTeam + ":" + challengedTeam;
            teamChallenges.remove(key);
            challenges.remove(key);
        }
        m_botAction.sendPrivateMessage(name, "Your challenge to team " + challengedTeam + " has been removed.");
    }

    public void do_setRules(String name, String message) {
        DuelPlayer player;
        if (!players.containsKey(name)) {
            m_botAction.sendPrivateMessage(name, "You must be signed up first! Use !signup");
            return;
        }
        player = players.get(name);
        int deaths = 5;
        boolean nc = true;
        if (message.isEmpty()) {
            if (player.getNoCount()) 
                m_botAction.sendPrivateMessage(name, "Max deaths is " + player.getDeaths() + " and no count double kills (NC) is on");
            else
                m_botAction.sendPrivateMessage(name, "Max deaths is " + player.getDeaths() + " and no count double kills (NC) is off");
            return;
        } else if (message.indexOf(':') > 0) {
            String vars = message.substring(0, message.indexOf(':'));
            try {
                deaths = Integer.parseInt(vars);
            } catch (Exception e) {
                m_botAction.sendPrivateMessage(name, "Invalid syntax, please use !rules <deaths> or !rules <deaths>:nc");
                return;
            }
            nc = true;
        } else {
            try {
                deaths = Integer.parseInt(message);
            } catch (Exception e) {
                m_botAction.sendPrivateMessage(name, "Invalid syntax, please use !rules <deaths> or !rules <deaths>:nc");
                return;
            }
            nc = false;
        }
        player.setDeaths(deaths);
        player.setNoCount(nc);        
        if (nc) {
            String query = "UPDATE tblDuel__2player SET fnGameDeaths = " + deaths + ", fnNoCount = 1 WHERE fnEnabled = 1 AND fnUserID = " + player.getID();
            try {
                m_botAction.SQLQueryAndClose(mySQLHost, query);
                m_botAction.sendPrivateMessage(name, "Max deaths set to " + deaths + " and no count double kills (NC) set on");
            } catch (Exception e) {
                m_botAction.sendPrivateMessage(name, "No player profile found.");
                return;
            }
        } else {
            String query = "UPDATE tblDuel__2player SET fnGameDeaths = " + deaths + ", fnNoCount = 0 WHERE fnEnabled = 1 AND fnUserID = " + player.getID();
            try {
                m_botAction.SQLQueryAndClose(mySQLHost, query);
                m_botAction.sendPrivateMessage(name, "Max deaths set to " + deaths + " and no count double kills (NC) set off");
            } catch (Exception e) {
                m_botAction.sendPrivateMessage(name, "No player profile found.");
                return;
            }
        }
    }
    
    public void do_setNotPlaying(String name, String message) {
        if (notPlaying.contains(name)) {
            notPlaying.remove(name);
            if (!teamList.isEmpty() && players.containsKey(name)) {
                DuelPlayer dp = players.get(name);
                int[] teams = dp.getTeams();
                String[] partners = dp.getPartners();
                for (int i = 1; i < 6; i++) {
                    if (teamList.containsKey(teams[i])) {
                        DuelTeam team = teamList.get(teams[i]);
                        if (!notPlaying.contains(partners[i]))
                            team.setNotPlayingOff();
                        else
                            team.setNotPlayingOn();
                        teamList.put(teams[i], team);
                    }
                }
            }
            m_botAction.sendPrivateMessage(name, "NotPlaying has been turned off.");
        } else {
            if (players.containsKey(name)) {
                DuelPlayer dp = players.get(name);
                int[] teams = dp.getTeams();
                for (int i = 1; i < 6; i++) {
                    if (teamList.containsKey(teams[i])) {
                        DuelTeam team = teamList.get(teams[i]);
                        team.setNotPlayingOn();
                        teamList.put(teams[i], team);
                    }
                }
            }
            notPlaying.add(name);       
            m_botAction.sendPrivateMessage(name, "NotPlaying mode has been turned on. Send !notplaying again to toggle it off.");     
        }
    }
    
    public void do_disableName(String name, String message) {
        DuelPlayer dp = players.get(name);
        if (dp != null) {
            if (!dp.isEnabled()) {
                m_botAction.sendSmartPrivateMessage(name, "This account is already disabled or does not exist.");
                return;
            }
            if (dp.isBanned()) {
                m_botAction.sendPrivateMessage(name, "You have been banned from this league.");
                return;
            }
            dp.disable();
            sql_disableUser(dp.getID());
        } else
            sql_disableUser(sql_getUserID(name));
        m_botAction.sendSmartPrivateMessage(name, "Your username has been disabled and removed from every division.");
        m_botAction.sendSmartPrivateMessage(name, "To reenable this account use !enable, please note all other accounts must be disabled first.");
    }

    public void do_enableName(String name, String message) {
        if (newbies.containsKey(name) || !players.containsKey(name)) {
            m_botAction.sendPrivateMessage(name, "You must be signed up first.");
            return;
        }
        DuelPlayer dp = players.get(name);

        if (dp.isEnabled()) {
            m_botAction.sendSmartPrivateMessage(name, "This name is already enabled for play.");
            return;
        }

        ResultSet info = sql_getPlayerInfo(dp.getID());

        if (info == null) {
            m_botAction.sendSmartPrivateMessage(name, "Problem accessing info from database.  Please make sure you have done !signup before !enable.");
            return;
        }

        try {
            String IP = info.getString("fcIP");
            int MID = info.getInt("fnMID");
            m_botAction.SQLClose(info);
            ResultSet result = m_botAction.SQLQuery(mySQLHost, "SELECT U.fcUserName FROM tblDuel__2player P JOIN tblUser U ON P.fnUserID = U.fnUserID WHERE P.fnEnabled = 1 AND P.fcIP = '"
                    + IP + "' AND P.fnMID = " + MID);
            boolean okToEnable = canEnableNames.remove(name);
            if (result.next() && !okToEnable) {
                String extras = "";
                do {
                    extras += " " + result.getString("U.fcUserName") + " ";
                } while (result.next());
                m_botAction.sendSmartPrivateMessage(name, "To enable this name you must disable: ("
                        + extras + ")");
                m_botAction.sendSmartPrivateMessage(name, "If a mistake has been made and these are not your names, please contact a TWEL Op.");
            } else {
                sql_enableUser(dp.getID());
                m_botAction.sendSmartPrivateMessage(name, "Your name has been enabled to play.");
            }
            m_botAction.SQLClose(result);
        } catch (Exception e) {
            // This exception is caught frequently. Removed stack trace print
            // Don't need to see it anymore until we take the time to deal w/ it.
            Tools.printStackTrace(e);
        }
    }

    public void do_cancelDuel(String name, String message) {
        if (!playing.containsKey(name)) {
            m_botAction.sendPrivateMessage(name, "You are not playing a duel.");
            return;
        }
        Duel duel = playing.get(name);
        int team = duel.getPlayerNumber(name);
        String[] others;
        String[] names;
        if (team == 1) {
            names = duel.getChallenger();
            others = duel.getChallenged();
            
        } else {
            names = duel.getChallenged();
            others = duel.getChallenger();
        }
        boolean state = duel.toggleCancelGame(name);
        if (state && duel.getCancelState(others[0])) {
            m_botAction.sendPrivateMessage(names[0], "Your duel has been cancelled.", 103);
            m_botAction.sendPrivateMessage(names[1], "Your duel has been cancelled.", 103);
            m_botAction.sendPrivateMessage(others[0], "Your duel has been cancelled.", 103);
            m_botAction.sendPrivateMessage(others[1], "Your duel has been cancelled.", 103);
            m_botAction.sendTeamMessage(names[0] + " and " + names[1] + " VERSUS " + others[0] + " and " + others[1] + " have cancelled their " + duel.getDivision() + " duel");
            duel.toggleDuelBox();
            duels.remove(new Integer(duel.getBoxNumber()));
            playing.remove(names[0]);
            playing.remove(names[1]);
            playing.remove(others[0]);
            playing.remove(others[1]);
            m_botAction.spec(names[0]);
            m_botAction.spec(names[0]);
            m_botAction.spec(names[1]);
            m_botAction.spec(names[1]);
            m_botAction.spec(others[0]);
            m_botAction.spec(others[0]);
            m_botAction.spec(others[1]);
            m_botAction.spec(others[1]);
            if (laggers.containsKey(names[0])) {
                m_botAction.cancelTask(laggers.get(names[0]));
                laggers.remove(names[0]);
            }
            if (laggers.containsKey(names[1])) {
                m_botAction.cancelTask(laggers.get(names[1]));
                laggers.remove(names[1]);
            }
            if (laggers.containsKey(others[0])) {
                m_botAction.cancelTask(laggers.get(others[0]));
                laggers.remove(others[0]);
            }
            if (laggers.containsKey(others[1])) {
                m_botAction.cancelTask(laggers.get(others[1]));
                laggers.remove(others[1]);
            }
            clearScoreboard(duel);
        } else if (state) {
            m_botAction.sendPrivateMessage(names[0], "Your team has opted to end this duel. Your opponent must also use !cancel to cancel the duel.");
            m_botAction.sendPrivateMessage(names[1], "Your team has opted to end this duel. Your opponent must also use !cancel to cancel the duel.");
            m_botAction.sendPrivateMessage(others[0], name + " has opted to end this duel. Use !cancel to end the duel.");
            m_botAction.sendPrivateMessage(others[1], name + " has opted to end this duel. Use !cancel to end the duel.");
        } else {
            m_botAction.sendPrivateMessage(names[0], "You have removed your decision to cancel this duel.");
            m_botAction.sendPrivateMessage(names[1], "You have removed your decision to cancel this duel.");
            m_botAction.sendPrivateMessage(others[0], name + " has removed his decision to cancel this duel.");
            m_botAction.sendPrivateMessage(others[1], name + " has removed his decision to cancel this duel.");
        }
    }

    public void do_lagOut(String name, String message) {
        // Check for rules on using this command
        if (!playing.containsKey(name)) {
            m_botAction.sendPrivateMessage(name, "You are not playing a duel.");
            return;
        }
        if (m_botAction.getPlayer(name).getShipType() > 0) {
            m_botAction.sendPrivateMessage(name, "You are already in. ");
            return;
        }

        // Get the duel associated with this player
        Duel duel = playing.get(name);

        // Get the stats object associated with this player
        DuelPlayerStats playerStats = duel.getPlayer(name);
        
        String[] names;
        String[] others;
        if (duel.getPlayerNumber(name) == 1) {
            names = duel.getChallenger();
            others = duel.getChallenged();
        } else {
            others = duel.getChallenger();
            names = duel.getChallenged();
        }
        
        playerStats.setLastReturn((int)(System.currentTimeMillis() / 1000));
        if (name.equals(names[0]))
            m_botAction.sendPrivateMessage(names[1], "Your partner has returned from lagging out.");
        else
            m_botAction.sendPrivateMessage(names[0], "Your partner has returned from lagging out.");            
        
        m_botAction.sendPrivateMessage(others[0], name + " has returned from lagging out.");
        m_botAction.sendPrivateMessage(others[1], name + " has returned from lagging out.");
        
        m_botAction.sendPrivateMessage(name, "You have " + (s_lagLimit - playerStats.getLagouts()) + " lagouts remaining.");

        // Put the player back into the game
        m_botAction.setShip(name, playerStats.getShip());
        m_botAction.setFreq(name, playerStats.getFreq());
        if (!duel.hasStarted()) {
            int[] coords = playerStats.getSafeCoords();
            m_botAction.warpTo(name, coords[0], coords[1]);
        } else {
            WarpPoint p = duel.getRandomWarpPoint();
            m_botAction.warpTo(name, p.getXCoord(), p.getYCoord());
        }

        // Remove any lag timers for this player
        if (laggers.containsKey(name))
            m_botAction.cancelTask(laggers.remove(name));
        // setScoreboard(duel, 0);
    }

    public void do_quitDivision(String name, String message) {
        int division = -1;
        int team = -1;
        try {
            division = Integer.parseInt(message);
        } catch (NumberFormatException e) {
            m_botAction.sendPrivateMessage(name, "Please use the following syntax: !quit <division#>");
            m_botAction.sendPrivateMessage(name, "Division IDs: 1-Warbird 2-Javelin 3-Spider 4-Lancaster 5-Mixed");
            return;
        }
        DuelPlayer dp = players.get(name);
        if (dp == null) {
            m_botAction.sendPrivateMessage(name, "You must be signed up and registered on a team in that division first.");
            return;
        }
        
        if ((division > 0 && division < 6) || division == 7) {
            if (division == 7)
                division = 4;
            team = dp.getTeam(division);
            if (team == -1) {
                m_botAction.sendPrivateMessage(name, "You are not on a team in division " + division);
                return;
            }
            try {
                m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuel__2league SET fnStatus = 0 " +
                        "WHERE fnSeason = " + s_season + 
                        " AND fnStatus = 1 AND fnLeagueTypeID = " + division + " AND fnTeamID = " + team);
                m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuel__2team SET fnStatus = 0 " +
                        "WHERE fnSeason = " + s_season + 
                        " AND fnStatus = 1 AND fnLeagueTypeID = " + division + " AND fnTeamID = " + team);
            } catch (Exception e) { }
            m_botAction.sendPrivateMessage(name, "Your team has been removed from division " + division + ".");
            if (players.containsKey(dp.getPartner(division)))
                players.get(dp.getPartner(division)).quitTeam(division);
                
            dp.quitTeam(division);
        } else
            m_botAction.sendPrivateMessage(name, "Please provide an appropriate divisionID: 1-Warbird 2-Javelin 3-Spider 4-Lancaster 5-Mixed");
        do_teamList();
    }
    
    public void do_showRank(String name, String message) {
        String player = name;
        if(message != null && !(message.equals("") || message.equals(" "))) {
            player = message;
        }
        int id = -1;
        if (players.containsKey(player))
            id = players.get(player).getID();
        else
            id = sql_getUserID(player);
        
        m_botAction.SQLBackgroundQuery(mySQLHost, "Rank:" + name + ":" + player, "SELECT fnTeamID, fnLeagueTypeID, fnRating FROM tblDuel__2team T WHERE fnSeason = " + s_season + " AND fnStatus = 1 AND (T.fnUser1ID = " + id + " OR fnUser2ID = " + id + ") ORDER BY fnLeagueTypeID");
    }
    
    public void handleEvent(SQLResultEvent event) {
        String[] id = event.getIdentifier().split(":");
        if (id[0].equals("Rank")) {
            String title = "--Ratings------------------";
            String name = id[1];
            String target = id[2];
            boolean ranked = false;
            if(name.equals(target)) {
                title = "--" + target;
                while (title.length() < 27)
                    title+= "-";
            }
            ArrayList<String> info = new ArrayList<String>();
            try {
                ResultSet rank = event.getResultSet();
                while (rank.next()) {
                    ranked = true;
                    int teamID = rank.getInt("fnTeamID");
                    int div = rank.getInt("fnLeagueTypeID");
                    int rating = rank.getInt("fnRating");
                    String team = "|";

                    if (teamID / 10 > 0) {
                        if (teamID / 100 > 0) {
                            if (teamID / 1000 > 0) {
                                team += "  " + teamID + "|"; 
                            } else
                                team += "   " + teamID + " |";
                        } else
                            team += "   " + teamID + " |";
                    } else // "| ID#  | Division | Partners"
                        team += "    " + teamID + " |";
                    if (div== 1 || div== 2) 
                        team += " " + getDivision(div) + "  | ";
                    else if (div== 3)
                        team += "  " + getDivision(div) + "  | ";
                    else if (div== 4 || div== 7)
                        team += " " + getDivision(div) + "| ";
                    else if (div== 5)
                        team += "  " + getDivision(div) + "   | ";
                    if (rating / 10 > 0) {
                        if (rating / 100 > 0) {
                            if (rating / 1000 > 0) {
                                team += " " + rating + ""; 
                            } else
                                team += "  " + rating + " ";
                        } else
                            team += "  " + rating + " ";
                    } else // "| Team | Division | Rating |"
                        team += "   " + rating + " ";
                    while (team.length() < 27)
                        team += " ";
                    team += "|";
                    info.add(team);
                }
            } catch (Exception e) {
                Tools.printStackTrace("Background handle failure.", e);
            }
            m_botAction.SQLClose(event.getResultSet());
            if (ranked) {
                m_botAction.sendPrivateMessage(name, title);
                m_botAction.sendPrivateMessage(name, "| Team | Division | Rating |");
                m_botAction.sendPrivateMessage(name, "|------|----------|--------");
                m_botAction.privateMessageSpam(name, info.toArray(new String[info.size()]));
                m_botAction.sendPrivateMessage(name, "`--------------------------");
            }
            if (!ranked) {
                m_botAction.sendPrivateMessage(name, "No team information found for " + target);
            }
        }
    }
    
    
    /**
     * Operator Command Executions
     * 
     * @param name
     * @param message
     */

    public void do_setGreetMessage(String name, String message) {
        if (!leagueHeadOps.containsKey(name.toLowerCase()))
            return;

        m_botAction.sendUnfilteredPublicMessage("?set misc:greetmessage:" + message);
        m_botAction.sendPrivateMessage(name, "Greet Set: " + message);
    }

    public void do_allowUser(String name, String message) {
        if (!leagueOps.containsKey(name.toLowerCase()) && !leagueHeadOps.containsKey(name.toLowerCase()) && !hiddenOps.containsKey(name.toLowerCase()))
            return;
        DuelPlayer dp;
        if (players.containsKey(message)) {
            dp = players.get(message);
            if (dp.isEnabled())
                m_botAction.sendSmartPrivateMessage(name, "This user is already enabled to play.");
            else {
                sql_enableUser(dp.getID());
                dp.enable();
                m_botAction.sendSmartPrivateMessage(name, message + " should now be enabled.");
            }
        } else {
            allowedNames.add(message);
            m_botAction.sendPrivateMessage(name, message + " should now be able to !signup.");
        }
    }

    public void do_banUser(String name, String message) {
        if (!leagueOps.containsKey(name.toLowerCase()) && !leagueHeadOps.containsKey(name.toLowerCase()) && !hiddenOps.containsKey(name.toLowerCase()))
            return;

        String pieces[] = message.split(":", 2);
        if (pieces.length != 2) {
            m_botAction.sendPrivateMessage(name, "Please provide a ban comment (!banuser name:comment).");
            return;
        }

        String player = m_botAction.getFuzzyPlayerName(pieces[0]);
        if (player == null)
            player = pieces[0];
        if (players.containsKey(player)) {
            DuelPlayer dp = players.get(player);
            if (dp.isBanned()) {
                m_botAction.sendPrivateMessage(name, player + " has all ready been banned.");
                return;
            }   
            if (sql_banPlayer(dp.getID(), banDate.format(System.currentTimeMillis()) + pieces[1])) {
                dp.ban();
                m_botAction.sendPrivateMessage(name, player + " has been banned.");
            } 
        } else if (sql_banPlayer(player, banDate.format(System.currentTimeMillis()) + pieces[1]))
            m_botAction.sendPrivateMessage(name, player + " has been banned.");
        else
            m_botAction.sendPrivateMessage(name, "Unable to ban user " + player);
    }

    public void do_unbanUser(String name, String message) {
        if (!leagueOps.containsKey(name.toLowerCase()) && !leagueHeadOps.containsKey(name.toLowerCase()) && !hiddenOps.containsKey(name.toLowerCase()))
            return;

        String player = m_botAction.getFuzzyPlayerName(message);
        if (player == null)
            player = message;
        
        if (players.containsKey(player)) {
            DuelPlayer dp = players.get(player);
            if (sql_unbanPlayer(dp.getID())) {
                m_botAction.sendPrivateMessage(name, player + " has been unbanned.");
                dp.unban();
            } else
                m_botAction.sendPrivateMessage(name, "Unable to unban user " + player);
        } else {
            if (sql_unbanPlayer(player))
                m_botAction.sendPrivateMessage(name, player + " has been unbanned.");
            else
                m_botAction.sendPrivateMessage(name, "Unable to unban user " + player);
        }
    }

    public void do_sayBanned(String name, String message) {
        if (!leagueOps.containsKey(name.toLowerCase()) && !leagueHeadOps.containsKey(name.toLowerCase()) && !hiddenOps.containsKey(name.toLowerCase()))
            return;

        m_botAction.sendPrivateMessage(name, "Banned players: ");
        try {
            ResultSet results = m_botAction.SQLQuery(mySQLHost, "SELECT B.*, U.fcUserName FROM tblDuel__2ban B JOIN tblUser U ON U.fnUserID = B.fnUserID");
            while (results.next())
                m_botAction.sendPrivateMessage(name, results.getString("U.fcUserName"));
            m_botAction.SQLClose(results);
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void do_reComment(String name, String message) {
        if (!leagueOps.containsKey(name.toLowerCase()) && !leagueHeadOps.containsKey(name.toLowerCase()) && !hiddenOps.containsKey(name.toLowerCase()))
            return;

        String pieces[] = message.split(":", 2);
        if (pieces.length != 2) {
            m_botAction.sendPrivateMessage(name, "Please provide a ban comment (!banuser name:comment).");
            return;
        }

        String player = m_botAction.getFuzzyPlayerName(pieces[0]);
        if (player == null)
            player = pieces[0];
        int id = -1;
        if (players.containsKey(player))
            id = players.get(player).getID();
        else
            id = sql_getUserID(player);
        String comment = pieces[1];
        try {
            String lastCom = "";
            String banOnDate = "";
            ResultSet getTime = m_botAction.SQLQuery(mySQLHost, "SELECT fcComment FROM tblDuel__2ban WHERE fnUserID = " + id);

            if (getTime.next()) {
                lastCom = getTime.getString("fcComment");
                m_botAction.SQLClose(getTime);
                if ('.' == lastCom.charAt(2) && '.' == lastCom.charAt(5))
                    banOnDate = lastCom.substring(0, 8) + " - ";
                if (banOnDate.length() > 0) {
                    comment = banOnDate + Tools.addSlashesToString(pieces[1]);
                    m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuel__2ban SET fcComment = '" + comment + "' WHERE fnUserID = " + id);
                    m_botAction.sendPrivateMessage(name, "Set " + player + "'s ban comment to: " + comment);
                } else {
                    comment = Tools.addSlashesToString(comment);
                    m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuel__2ban SET fcComment = '" + comment + "' WHERE fnUserID = " + id);
                    m_botAction.sendPrivateMessage(name, "Set " + player
                            + "'s ban comment to: " + comment);
                }
            } else {
                m_botAction.SQLClose(getTime);
                m_botAction.sendPrivateMessage(name, "That player is not banned.");
            }
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void do_getComment(String name, String message) {
        if (!leagueOps.containsKey(name.toLowerCase()) && !leagueHeadOps.containsKey(name.toLowerCase()) && !hiddenOps.containsKey(name.toLowerCase()))
            return;

        String player = m_botAction.getFuzzyPlayerName(message);
        if (player == null)
            player = message;

        try {
            ResultSet results = m_botAction.SQLQuery(mySQLHost, "SELECT fcComment FROM tblDuel__2ban WHERE fnUserID = (SELECT fnUserID FROM tblUser WHERE fcUserName = '" + player + "' ORDER BY fnUserID ASC LIMIT 1)");
            if (results.next()) {
                m_botAction.sendPrivateMessage(name, "Ban comment: " + results.getString("fcComment"));
            } else
                m_botAction.sendPrivateMessage(name, "That player is not banned.");
            m_botAction.SQLClose(results);
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void do_aliasCheck(String name, String message) {
        if (!leagueOps.containsKey(name.toLowerCase()) && !leagueHeadOps.containsKey(name.toLowerCase()) && !hiddenOps.containsKey(name.toLowerCase()))
            return;
        
        boolean useName = false;
        int id = -1;
        DuelPlayer dp = null;
        if (players.containsKey(message)) {
            dp = players.get(message);
            id = dp.getID();
        } else if (newbies.containsKey(message)) {
            id = newbies.get(message);
            if (id < 1)
                useName = true;
        } else {
            useName = true;
        }

        try {
            int MID = -1;
            String IP = "";
            ResultSet info;
            if (!useName)
                info = sql_getPlayerInfo(id);
            else
                info = sql_getPlayerInfo(message);
            if (info != null && info.next()) {
                MID = info.getInt("fnMID");
                IP = info.getString("fcIP");
                if (info.getInt("fnEnabled") == 1)
                    m_botAction.sendSmartPrivateMessage(name, "This name is enabled for play.");
                m_botAction.SQLClose(info);
                aliasChecker = name;
                do_getAliases(message, IP, MID);                
            } else {
                message = m_botAction.getFuzzyPlayerName(message);
                if (message != null) {
                    aliasCheck = true;
                    aliasChecker = name;
                    m_botAction.sendUnfilteredPrivateMessage(message, "*info");
                } else
                    m_botAction.sendSmartPrivateMessage(name, "Player must be in this arena if not signed up.");
                m_botAction.SQLClose(info);
                return;
            }
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }
    
    public void do_getAliases(String name, String IP, int MID) {
        try {
            ResultSet result = m_botAction.SQLQuery(mySQLHost, "SELECT U.fcUserName FROM tblDuel__2player P JOIN tblUser U ON U.fnUserID = P.fnUserID WHERE P.fnEnabled = 1 AND P.fcIP = '" + IP + "' AND P.fnMID = " + MID);
            if (result.next()) {
                String extras = "";
                do {
                    extras += "" + result.getString("U.fcUserName") + ", ";
                } while (result.next());
                m_botAction.sendSmartPrivateMessage(aliasChecker, "Aliases registered with the same IP and MID: " + extras.substring(0, extras.length() - 2));
            } else
                m_botAction.sendSmartPrivateMessage(aliasChecker, "No IP/MID matches for player " + name);
            m_botAction.SQLClose(result);
            result = m_botAction.SQLQuery(mySQLHost, "SELECT U.fcUserName FROM tblDuel__2player P JOIN tblUser U ON U.fnUserID = P.fnUserID WHERE P.fnEnabled = 1 AND P.fcIP = '" + IP + "'");
            if (result.next()) {
                String extras = "";
                do {
                    extras += "" + result.getString("U.fcUserName") + ", ";
                } while (result.next());
                m_botAction.sendSmartPrivateMessage(aliasChecker, "Aliases registered with the same IP: " + extras.substring(0, extras.length() - 2));
            } else
                m_botAction.sendSmartPrivateMessage(aliasChecker, "No IP matches for player " + name);
            m_botAction.SQLClose(result);
            result = m_botAction.SQLQuery(mySQLHost, "SELECT U.fcUserName FROM tblDuel__2player P JOIN tblUser U ON U.fnUserID = P.fnUserID WHERE P.fnEnabled = 1 AND P.fnMID = " + MID);
            if (result.next()) {
                String extras = "";
                do {
                    extras += "" + result.getString("U.fcUserName") + ", ";
                } while (result.next());
                m_botAction.sendSmartPrivateMessage(aliasChecker, "Aliases registered with the same MID: " + extras.substring(0, extras.length() - 2));
            } else
                m_botAction.sendSmartPrivateMessage(aliasChecker, "No MID matches for player " + name);
            m_botAction.SQLClose(result);
            aliasChecker = "";
        } catch (Exception e) {
            // This exception is caught frequently. Removed stack trace print
            // Don't need to see it anymore until we take the time to deal w/
            // it.
            Tools.printStackTrace(e);
        }
    }

    public void do_opDisableName(String name, String message) {
        if (!leagueOps.containsKey(name.toLowerCase()) && !leagueHeadOps.containsKey(name.toLowerCase()) && !hiddenOps.containsKey(name.toLowerCase()))
            return;
        String player = m_botAction.getFuzzyPlayerName(message);
        if (player == null)
            player = message;
        int id = -1;
        DuelPlayer dp = players.get(player);
        if (dp == null) {
            id = sql_getUserID(player);
        } else {
            id = dp.getID();
            dp.disable();
        }
        sql_disableUser(id);
        m_botAction.sendSmartPrivateMessage(name, player + " disabled.");
    }

    public void do_setMessageLimit(String name, String message) {
        if (!(leagueOps.containsKey(name.toLowerCase())) || !leagueHeadOps.containsKey(name.toLowerCase()) || !hiddenOps.containsKey(name.toLowerCase()))
            return;
        int limit = MSG_LIMIT;
        try {
            limit = Integer.parseInt(message);
        } catch (Exception e) { }
        m_botAction.setMessageLimit(limit);
    }

    public void do_die(String name, String message) {
        if (!leagueOps.containsKey(name.toLowerCase())
                && !leagueHeadOps.containsKey(name.toLowerCase())
                && !hiddenOps.containsKey(name.toLowerCase())
                && !m_botAction.getOperatorList().isSmod(name))
            return;
        m_botAction.sendPrivateMessage(name, "Have a nice day, goodbye!");
        // Removes the bot from the server.
        TimerTask die = new TimerTask() {
            @Override
            public void run() {
                m_botAction.die();                
            }
        };
        m_botAction.scheduleTask(die, 300);
    }

    public void do_shutDown(String name, String message) {
        if (!leagueOps.containsKey(name.toLowerCase())
                && !leagueHeadOps.containsKey(name.toLowerCase())
                && !hiddenOps.containsKey(name.toLowerCase())
                && !m_botAction.getOperatorList().isSmod(name))
            return;
        shutDownMessage = message;
        if (shutDown) {
            m_botAction.sendPrivateMessage(name, "Shutdown mode turned off.");
            shutDown = false;
        } else {
            m_botAction.sendPrivateMessage(name, "Shutdown mode turned on.");
            shutDown = true;
        }
    }

    public void do_shutDownDie(String name, String message) {
        if (!leagueOps.containsKey(name.toLowerCase())
                && !leagueHeadOps.containsKey(name.toLowerCase())
                && !hiddenOps.containsKey(name.toLowerCase())
                && !m_botAction.getOperatorList().isSmod(name))
            return;
        shutDownMessage = message;
        if (shutDownDie) {
            m_botAction.sendPrivateMessage(name, "Shutdown+Die mode turned off.");
            shutDown = false;
            shutDownDie = false;
        } else {
            m_botAction.sendPrivateMessage(name, "Shutdown+Die mode turned on.");
            shutDown = true;
            shutDownDie = true;
            if (duels.size() == 0) {
                m_botAction.sendArenaMessage("Shutting down for core maintenance.", 1);
                TimerTask dieTask = new TimerTask() {
                    public void run() {
                        m_botAction.die();
                    }
                };
                m_botAction.scheduleTask(dieTask, 5000);
            }
        }
    }
    
    public void do_addOp(String name, String message) {
        if (!leagueHeadOps.containsKey(name.toLowerCase()))
            return;

        if (leagueOps.containsKey(message.toLowerCase())) {
            m_botAction.sendPrivateMessage(name, "Add Op: " + message + " failed, operator all ready exists");
            return;
        }
        String ops = m_botSettings.getString("LeagueOps");
        if (ops.length() < 1)
            m_botSettings.put("LeagueOps", message);
        else
            m_botSettings.put("LeagueOps", ops + "," + message);
        m_botAction.sendPrivateMessage(name, "Add Op: " + message + " successful");
        m_botSettings.save();
        do_updateOps();
    }

    public void do_removeOp(String name, String message) {
        if (!leagueHeadOps.containsKey(name.toLowerCase()))
            return;
        String ops = m_botSettings.getString("LeagueOps");
        int spot = ops.indexOf(message);
        if (spot == 0 && ops.length() == message.length()) {
            ops = "";
            m_botAction.sendPrivateMessage(name, "Remove Op: " + message
                    + " successful");
        } else if (spot == 0 && ops.length() > message.length()) {
            ops = ops.substring(message.length() + 1);
            m_botAction.sendPrivateMessage(name, "Remove Op: " + message
                    + " successful");
        } else if (spot > 0 && spot + message.length() < ops.length()) {
            ops = ops.substring(0, spot)
                    + ops.substring(spot + message.length() + 1);
            m_botAction.sendPrivateMessage(name, "Remove Op: " + message
                    + " successful");
        } else if (spot > 0 && spot == ops.length() - message.length()) {
            ops = ops.substring(0, spot - 1);
            m_botAction.sendPrivateMessage(name, "Remove Op: " + message
                    + " successful");
        } else {
            m_botAction.sendPrivateMessage(name, "Remove Op: " + message
                    + " failed, operator doesn't exist");
        }

        m_botSettings.put("LeagueOps", ops);
        m_botSettings.save();
        do_updateOps();
    }

    public void do_addHiddenOp(String name, String message) {
        if (!leagueHeadOps.containsKey(name.toLowerCase()))
            return;

        if (hiddenOps.containsKey(message.toLowerCase())) {
            m_botAction.sendPrivateMessage(name, "Add Hidden Op: " + message
                    + " failed, operator all ready exists");
            return;
        }
        String ops = m_botSettings.getString("HiddenOps");
        if (ops.length() < 1)
            m_botSettings.put("HiddenOps", message);
        else
            m_botSettings.put("HiddenOps", ops + "," + message);
        m_botAction.sendPrivateMessage(name, "Add Hidden Op: " + message
                + " successful");
        m_botSettings.save();
        do_updateOps();
    }

    public void do_removeHiddenOp(String name, String message) {
        if (!leagueHeadOps.containsKey(name.toLowerCase()))
            return;
        String ops = m_botSettings.getString("HiddenOps");
        int spot = ops.indexOf(message);
        if (spot == 0 && ops.length() == message.length()) {
            ops = "";
            m_botAction.sendPrivateMessage(name, "Remove Hidden Op: " + message
                    + " successful");
        } else if (spot == 0 && ops.length() > message.length()) {
            ops = ops.substring(message.length() + 1);
            m_botAction.sendPrivateMessage(name, "Remove Hidden Op: " + message
                    + " successful");
        } else if (spot > 0 && spot + message.length() < ops.length()) {
            ops = ops.substring(0, spot)
                    + ops.substring(spot + message.length() + 1);
            m_botAction.sendPrivateMessage(name, "Remove Hidden Op: " + message
                    + " successful");
        } else if (spot > 0 && spot == ops.length() - message.length()) {
            ops = ops.substring(0, spot - 1);
            m_botAction.sendPrivateMessage(name, "Remove Hidden Op: " + message
                    + " successful");
        } else {
            m_botAction.sendPrivateMessage(name, "Remove Hidden Op: " + message
                    + " failed, operator doesn't exist");
        }

        m_botSettings.put("HiddenOps", ops);
        m_botSettings.save();
        do_updateOps();
    }
    
    public void do_opRefreshArena(String name, String message) {
        if (!leagueOps.containsKey(name.toLowerCase()) && !leagueHeadOps.containsKey(name.toLowerCase()) && !hiddenOps.containsKey(name.toLowerCase())) 
            return;
        if (!duels.isEmpty()) {
            m_botAction.sendPrivateMessage(name, "Refresh not recomended with duels in progress. Have players re-enter the arena instead.");
            return;
        }
        players.clear();
        Iterator<Player> it = m_botAction.getPlayerIterator();
        while (it.hasNext()) {
            Player player = it.next();
            String pname = player.getPlayerName();
            if (player.getFrequency() != 9999) {
                m_botAction.setShip(pname, 1);
                m_botAction.spec(pname);
                m_botAction.spec(pname);
            }
            do_addListPlayer(player);
        }
        do_teamList();
        
        m_botAction.sendPrivateMessage(name, "Arena refreshed. If problems persist, make players leave then reenter arena OR restart the bot.");
    }

    /**
     * Bot Command Executions
     * 
     * @param name
     * @param message
     */

    public void do_initialScan() {
        players.clear();
        Iterator<Player> it = m_botAction.getPlayerIterator();
        while (it.hasNext()) {
            Player player = it.next();
            String name = player.getPlayerName();
            // make SURE there is a UserID available in tblUser
            
            if (player.getFrequency() != 9999) {
                m_botAction.setShip(name, 1);
                m_botAction.spec(name);
                m_botAction.spec(name);
            }
            do_addListPlayer(player);
        }
        do_teamList();
    }
    
    public boolean do_partnerCheck(String name, String part) {
        if (name.equalsIgnoreCase(part)) {
            m_botAction.sendPrivateMessage(name, "One man teams are not allowed!");
            return false;
        }
        DuelPlayer dp = players.get(m_botAction.getFuzzyPlayerName(part));
        if (dp == null) {
            m_botAction.sendPrivateMessage(name, part + " must be in this arena and signed up in order to join your team.");
            return false;
        } else if (dp.isBanned()) {
            m_botAction.sendPrivateMessage(name, part + " is not allowed to play in this league.");
            return false;
        } else if (!dp.isEnabled()) {
            m_botAction.sendPrivateMessage(name, part + " must be enabled before allowed to register for a team.");
            return false;
        } else
            return true;
    }
    
    public void do_addListPlayer(DuelPlayer dp) {
        players.put(dp.getName(), dp);
   }
    
    public void do_addListPlayer(Player p) {
        String name = p.getPlayerName();
        if (name == null) 
            return;
        if (!opList.isBotExact(name)) {
            DuelPlayer dp = sql_buildPlayer(name);
            if (dp != null)
                players.put(name, dp);
            else
                newbies.put(name, sql_getUserID(name));
        }
   }

    public void do_addListTeams(String name) {
        if (players.isEmpty() || newbies.containsKey(name)) 
            return;
        DuelPlayer player;
        if (!players.containsKey(name))
            return;
        player = players.get(name);
        int[] teams = player.getTeams();
        String[] partners = player.getPartners();
        for (int i = 1; i <= 5; i++) {
            if (teams[i] > -1) {
                if (players.containsKey(partners[i])) {
                    teamList.put(teams[i], new DuelTeam(teams[i], name, partners[i], player.getID(), player.getPID(i), i, getDivision(i)));
                }
            }
        }
    }

    public void do_addListTeams(DuelPlayer player) {
        int[] teams = player.getTeams();
        String[] partners = player.getPartners();
        for (int i = 1; i <= 5; i++) {
            if (teams[i] > -1) {
                if (players.containsKey(partners[i])) {
                    teamList.put(teams[i], new DuelTeam(teams[i], player.getName(), partners[i], player.getID(), player.getPID(i), i, getDivision(i)));
                }
            }
        }
    }
    
    public void do_removeListTeams(DuelPlayer player) {
        int[] teams = player.getTeams();
        for (int i = 1; i <= 5; i++)
            if (teams[i] > -1 )
                teamList.remove(teams[i]);
    }
    
    public void do_teamList() {
        teamList.clear();
        if (!players.isEmpty()) {
            Iterator<DuelPlayer> it = players.values().iterator();
            while (it.hasNext())
                do_addListTeams(it.next());
        }
    }
    
    public void do_refreshArena() {
        players.clear();
        Iterator<Player> it = m_botAction.getPlayerIterator();
        while (it.hasNext())
            do_addListPlayer(it.next());
        do_teamList();
    }
    
    /* REMOVED - not using idle times currenlty
    public long do_getIdleTime(String name) {
        return (System.currentTimeMillis() - players.get(name)) / Tools.TimeInMillis.MINUTE;
    }
    */
    
    // Used by TimerTask to execute statements conditioned by idle time
    /* not using just yet
    public void check() {
        if (!players.isEmpty()) {
            for (String name : players.keySet()) {
                
            }
        }
    }
    */
    
    public void do_addPlayer(String name, String IP, String sMID) {
        int MID = 0;
        try {
            MID = Integer.parseInt(sMID);
        } catch (NumberFormatException e) {
            System.out.println("[TEAMDUEL] Failed to convert MID " + sMID);
            return;
        }
        
        try {
            ResultSet result = m_botAction.SQLQuery(mySQLHost, "SELECT U.fcUserName FROM tblDuel__2player P JOIN tblUser U ON U.fnUserID = P.fnUserID WHERE P.fnEnabled = 1 AND (P.fcIP = '"+IP+"' OR (P.fcIP = '"+IP+"' AND P.fnMID = "+MID+")) OR P.fnUserID = (SELECT fnUserID FROM tblUser WHERE fcUserName = '" + Tools.addSlashesToString(name) + "' ORDER BY fnUserID ASC LIMIT 1)");
            if (!result.next()) {
                m_botAction.SQLClose(result);
                DuelPlayer dp = null;
                if (newbies.containsKey(name)) {
                    int id = newbies.remove(name);
                    m_botAction.SQLQueryAndClose(mySQLHost, "INSERT INTO tblDuel__2player (`fnUserID`, `fcIP`, `fnMID`, `fnLag`, `fnLagCheckCount`, `fdLastPlayed`) " + 
                            "VALUES(" + id + ", '" + IP + "', " + MID + ", 0, 0, NOW())");
                    dp = sql_buildPlayer(name, id);
                }
                else {
                    m_botAction.SQLQueryAndClose(mySQLHost, "INSERT INTO tblDuel__2player (`fnUserID`, `fcIP`, `fnMID`, `fnLag`, `fnLagCheckCount`, `fdLastPlayed`) " + 
                            "VALUES((SELECT fnUserID FROM tblUser WHERE fcUserName = '" + Tools.addSlashesToString(name) + "' ORDER BY fnUserID ASC LIMIT 1), '" + IP + "', " + MID + ", 0, 0, NOW())");
                    dp = sql_buildPlayer(name);
                }
                players.put(name, dp);
                
                if (aliasChecker.equals(""))
                    m_botAction.sendPrivateMessage(name, "You have been registered to use this bot. It is advised you set your personal dueling rules, for further information use !help");
                else {
                    m_botAction.sendPrivateMessage(aliasChecker, "This player has no registered aliases.");
                    aliasChecker = "";
                }
            } else {
                boolean signedup = false;
                LinkedList<String> aliases = new LinkedList<String>();
                String alias = result.getString("U.fcUserName");
                aliases.add(alias);
                if (alias.equalsIgnoreCase(name)) {
                    signedup = true;
                }
                
                while (result.next()) {
                    alias = result.getString("U.fcUserName");
                    aliases.add(alias);
                    if (alias.equalsIgnoreCase(name)) {
                        signedup = true;
                    }
                }
                m_botAction.SQLClose(result);
                
                if (signedup) {
                    if (aliasChecker.equals(""))
                        m_botAction.sendSmartPrivateMessage(name, "You have already signed up.");
                    else {
                        m_botAction.sendSmartPrivateMessage(aliasChecker, "This player is already signed up.");
                        aliasChecker = "";
                    }
                } else {
                    if (allowedNames.contains(name) && aliasChecker.equals("")) {
                        DuelPlayer dp = null;
                        if (newbies.containsKey(name)) {
                            int id = newbies.remove(name);
                            m_botAction.SQLQueryAndClose(mySQLHost, "INSERT INTO tblDuel__2player (`fnUserID`, `fcIP`, `fnMID`, `fnLag`, `fnLagCheckCount`, `fdLastPlayed`) " + 
                                    "VALUES(" + id + ", '" + IP + "', " + MID + ", 0, 0, NOW())");
                            dp = sql_buildPlayer(name, id);
                        }
                        else {
                            m_botAction.SQLQueryAndClose(mySQLHost, "INSERT INTO tblDuel__2player (`fnUserID`, `fcIP`, `fnMID`, `fnLag`, `fnLagCheckCount`, `fdLastPlayed`) " + 
                                    "VALUES((SELECT fnUserID FROM tblUser WHERE fcUserName = '" + Tools.addSlashesToString(name) + "' ORDER BY fnUserID ASC LIMIT 1), '" + IP + "', " + MID + ", 0, 0, NOW())");
                            dp = sql_buildPlayer(name);
                        }
                        players.put(name, dp);
                        m_botAction.sendPrivateMessage(name, "You have been registered to use this bot. Find a partner, pick a division and have some fun. ");
                        allowedNames.remove(name);
                        canEnableNames.add(name);
                    } else {
                        String extras = "";
                        Iterator<String> i = aliases.iterator();
                        while (i.hasNext())
                            extras += " " + i.next() + " ";

                        if (aliasChecker.equals("")) {
                            m_botAction.sendSmartPrivateMessage(name, "It appears you already have other names signed up for TeamDuel or have registered this name already.");
                            m_botAction.sendSmartPrivateMessage(name, "Please login with these names: ("
                                    + extras
                                    + ") and use the command !disable, you will then be able to signup a new name.");
                            m_botAction.sendSmartPrivateMessage(name, "Disabled names automatically disable the teams they were on. If you have further problems please contact a league op.");
                        } else {
                            m_botAction.sendSmartPrivateMessage(aliasChecker, "Aliases registered: "
                                    + extras);
                            aliasChecker = "";
                        }
                    }
                }
            }
        } catch (Exception e) {
            Tools.printStackTrace("Failed to signup new user", e);
        }
    }

    public void do_checkArena(String name, String message) {
        if (message.startsWith("IP:")) {
            // Sorts information from *info
            String[] pieces = message.split("  ");
            String thisName = pieces[3].substring(10);
            String thisIP = pieces[0].substring(3);
            String thisID = pieces[5].substring(10);
            if (aliasCheck) {
                do_getAliases(thisName, thisIP, Integer.parseInt(thisID));
            } else {
                do_addPlayer(thisName, thisIP, thisID);
            }
        } else if (message.equals("Arena UNLOCKED")) {
            locksmith.arenaUnlocked();
        } else if (message.equals("Arena LOCKED")) {
            locksmith.arenaLocked();
        } else if (message.startsWith("PING Current:")) {
            String pieces[] = message.split(" ");
            String output = pieces[4] + " ms  " + pieces[10] + " ms";
            output = message.substring(0, message.length() - 16);
            if (to != null)
                m_botAction.sendPrivateMessage(to, from + ":  " + output);
            int average = Integer.parseInt(pieces[4].substring(pieces[4].indexOf(":") + 1));
            sql_lagInfo(name, average);
            to = "";
            from = "";
        }
    }

    public void do_updateOps() {
        leagueOps.clear();
        leagueHeadOps.clear();
        hiddenOps.clear();
        // Reads in the league operators
        String ops[] = m_botSettings.getString("LeagueOps").split(",");
        for (int i = 0; i < ops.length; i++)
            leagueOps.put(ops[i].toLowerCase(), ops[i]);
        // Reads in the head league operators
        String hops[] = m_botSettings.getString("HeadOps").split(",");
        for (int j = 0; j < hops.length; j++)
            leagueHeadOps.put(hops[j].toLowerCase(), hops[j]);
        // Reads in the hidden league operators
        String hideops[] = m_botSettings.getString("HiddenOps").split(",");
        for (int k = 0; k < hideops.length; k++)
            hiddenOps.put(hideops[k].toLowerCase(), hideops[k]);
    }
    
    private String getDivision(int type) {

        if (type == 1)
            return "Warbird";
        else if (type == 2)
            return "Javelin";
        else if (type == 3)
            return "Spider";
        else if (type == 4 || type == 7)
            return "Lancaster";
        else if (type == 5)
            return "Mixed";
        else
            return "invalid";
    }
    
    /**
     * Duel Operations
     */

    public boolean boxOpen(int division) {
        int i = 0;
        Iterator<String> it = duelBoxes.keySet().iterator();
        while (it.hasNext()) {
            String key = (String) it.next();
            DuelBox b = duelBoxes.get(key);
            if (!b.inUse() && b.gameType(division))
                i++;
        }
        if (i == 0)
            return false;
        else
            return true;
    }

    public DuelBox getDuelBox(int division) {
        Vector<DuelBox> v = new Vector<DuelBox>();
        Iterator<String> it = duelBoxes.keySet().iterator();
        while (it.hasNext()) {
            String key = (String) it.next();
            DuelBox b = duelBoxes.get(key);
            if (!b.inUse() && b.gameType(division))
                v.add(b);
        }
        if (v.size() == 0)
            return null;
        else {
            Random generator = new Random();
            return v.elementAt(generator.nextInt(v.size()));
        }
    }

    public void startDuel(Duel d, String[] challenger, String[] challenged) {
        d.settingUpOn();
        int freq = d.getBoxFreq();
        int[] challengerShip = d.getChallengerShip();
        int[] challengedShip = d.getChallengedShip();
        m_botAction.setShip(challenger[0], challengerShip[0]);
        m_botAction.setFreq(challenger[0], freq);
        m_botAction.setShip(challenger[1], challengerShip[1]);
        m_botAction.setFreq(challenger[1], freq);
        m_botAction.setShip(challenged[0], challengedShip[0]);
        m_botAction.setFreq(challenged[0], freq + 1);
        m_botAction.setShip(challenged[1], challengedShip[1]);
        m_botAction.setFreq(challenged[1], freq + 1);
        m_botAction.warpTo(challenger[0], d.getSafeAXOne(), d.getSafeAYOne());
        m_botAction.warpTo(challenger[1], d.getSafeAXTwo(), d.getSafeAYTwo());
        m_botAction.warpTo(challenged[0], d.getSafeBXOne(), d.getSafeBYOne());
        m_botAction.warpTo(challenged[1], d.getSafeBXTwo(), d.getSafeBYTwo());
        if (d.getDivisionID() != 5) {
            m_botAction.sendPrivateMessage(challenger[0], "Duel Begins in 15 Seconds Against '" + challenged[0] + "' and '" + challenged[1] + "'", 27);
            m_botAction.sendPrivateMessage(challenger[1], "Duel Begins in 15 Seconds Against '" + challenged[0] + "' and '" + challenged[1] + "'", 27);
            m_botAction.sendUnfilteredPrivateMessage(challenger[0], "*lag");
            to = null;
            from = challenger[0];
            m_botAction.sendPrivateMessage(challenged[0], "Duel Begins in 15 Seconds Against '" + challenger[0] + "' and '" + challenger[1] + "'", 27);
            m_botAction.sendPrivateMessage(challenged[1], "Duel Begins in 15 Seconds Against '" + challenger[0] + "' and '" + challenger[1] + "'", 27);
            m_botAction.sendUnfilteredPrivateMessage(challenger[1], "*lag");
            to = null;
            from = challenger[1];
        } else {
            d.setLockOff();
            locksmith.unlock();
            m_botAction.sendUnfilteredPrivateMessage(challenger[0], "*lag");
            to = null;
            from = challenger[0];
            m_botAction.sendPrivateMessage(challenger[0], "Duel Begins in 15 Seconds Against '" + challenged[0] + "' and '" + challenged[1] + "'", 29);
            m_botAction.sendPrivateMessage(challenger[0], "You may change your ship until that time! No ship changes will be allowed after the duel starts.");
            m_botAction.sendPrivateMessage(challenger[1], "Duel Begins in 15 Seconds Against '" + challenged[0] + "' and '" + challenged[1] + "'", 29);
            m_botAction.sendPrivateMessage(challenger[1], "You may change your ship until that time! No ship changes will be allowed after the duel starts.");
            m_botAction.sendPrivateMessage(challenged[0], "Duel Begins in 15 Seconds Against '" + challenger[0] + "' and '" + challenger[1] + "'", 29);
            m_botAction.sendPrivateMessage(challenged[0], "You may change your ship until that time! No ship changes will be allowed after the duel starts.");
            m_botAction.sendPrivateMessage(challenged[1], "Duel Begins in 15 Seconds Against '" + challenger[0] + "' and '" + challenger[1] + "'", 29);
            m_botAction.sendPrivateMessage(challenged[1], "You may change your ship until that time! No ship changes will be allowed after the duel starts.");
            m_botAction.sendUnfilteredPrivateMessage(challenger[1], "*lag");
            to = null;
            from = challenger[1];
        }
        
        m_botAction.scheduleTask(new GameStartTimer(d, challenger, challenged, m_botAction, locksmith), 15000);

        m_botAction.sendTeamMessage("A " + d.getDivision() + " duel is starting: " + challenger[0] + " and " + challenger[1] + " VERSUS " + challenged[0] + " and " + challenged[1] + " in box #" + (d.getBoxFreq() / 2));
        // setScoreboard(d, 0);
        int div = d.getDivisionID();
        
        // this should prevent the ship bug
        if (div == 4) {
            if (m_botAction.getPlayer(challenger[0]).getShipType() != 7) {
                DuelPlayerStats p = d.getPlayerOne();
                p.setShip(7);
                m_botAction.setShip(challenger[0], 7);
            }
            if (m_botAction.getPlayer(challenger[1]).getShipType() != 7) {
                DuelPlayerStats p = d.getPlayerTwo();
                p.setShip(7);
                m_botAction.setShip(challenger[1], 7);
            }
            if (m_botAction.getPlayer(challenged[0]).getShipType() != 7) {
                DuelPlayerStats p = d.getPlayerThree();
                m_botAction.setShip(challenged[0], 7);
                p.setShip(7);
            }
            if (m_botAction.getPlayer(challenged[0]).getShipType() != 7) {
                DuelPlayerStats p = d.getPlayerFour();
                p.setShip(7);
                m_botAction.setShip(challenged[1], 7);
            }
        } else if (div == 3) {
            if (m_botAction.getPlayer(challenger[0]).getShipType() != 3) {
                DuelPlayerStats p = d.getPlayerOne();
                p.setShip(3);
                m_botAction.setShip(challenger[0], 3);
            }
            if (m_botAction.getPlayer(challenger[1]).getShipType() != 3) {
                DuelPlayerStats p = d.getPlayerTwo();
                p.setShip(3);
                m_botAction.setShip(challenger[1], 3);
            }
            if (m_botAction.getPlayer(challenged[0]).getShipType() != 3) {
                DuelPlayerStats p = d.getPlayerThree();
                p.setShip(3);
                m_botAction.setShip(challenged[0], 3);
            }
            if (m_botAction.getPlayer(challenged[0]).getShipType() != 3) {
                DuelPlayerStats p = d.getPlayerFour();
                p.setShip(3);
                m_botAction.setShip(challenged[1], 3);
            }
            
        } else if (div == 2) {
            if (m_botAction.getPlayer(challenger[0]).getShipType() != 2) {
                DuelPlayerStats p = d.getPlayerOne();
                p.setShip(2);
                m_botAction.setShip(challenger[0], 2);
            }
            if (m_botAction.getPlayer(challenger[1]).getShipType() != 2) {
                DuelPlayerStats p = d.getPlayerTwo();
                p.setShip(2);
                m_botAction.setShip(challenger[1], 2);
            }
            if (m_botAction.getPlayer(challenged[0]).getShipType() != 2) {
                DuelPlayerStats p = d.getPlayerThree();
                p.setShip(2);
                m_botAction.setShip(challenged[0], 2);
            }
            if (m_botAction.getPlayer(challenged[0]).getShipType() != 2) {
                DuelPlayerStats p = d.getPlayerFour();
                p.setShip(2);
                m_botAction.setShip(challenged[1], 2);
            }
        } else if (div == 1) {
            if (m_botAction.getPlayer(challenger[0]).getShipType() != 1) {
                DuelPlayerStats p = d.getPlayerOne();
                p.setShip(1);
                m_botAction.setShip(challenger[0], 1);
            }
            if (m_botAction.getPlayer(challenger[1]).getShipType() != 1) {
                DuelPlayerStats p = d.getPlayerTwo();
                p.setShip(1);
                m_botAction.setShip(challenger[1], 1);
            }
            if (m_botAction.getPlayer(challenged[0]).getShipType() != 1) {
                DuelPlayerStats p = d.getPlayerThree();
                p.setShip(1);
                m_botAction.setShip(challenged[0], 1);
            }
            if (m_botAction.getPlayer(challenged[0]).getShipType() != 1) {
                DuelPlayerStats p = d.getPlayerFour();
                p.setShip(1);
                m_botAction.setShip(challenged[1], 1);
            }
        }
        d.settingUpOff();
    }

    public void endDuel(Duel d, String[] winner, String[] loser, int winnerTeam, int loserTeam, int type) {
        // 0 - normal, 1 - spawning, 2 - warping, 3 - lagouts, 4 - 1 min lagout
        d.endTime();
        String challed[] = d.getChallenged();
        to = null;
        from = challed[0];
        m_botAction.sendUnfilteredPrivateMessage(challed[0], "*lag");
        DuelPlayer[] winners = new DuelPlayer[] {players.get(winner[0]), players.get(winner[1])};
        DuelPlayer[] losers = new DuelPlayer[] {players.get(loser[0]), players.get(loser[1])};
        if (spawnDelays.containsKey(loser[0]))
            m_botAction.cancelTask(spawnDelays.get(loser[0]));
        if (spawnDelays.containsKey(loser[1]))
            m_botAction.cancelTask(spawnDelays.get(loser[1]));
        if (spawnDelays.containsKey(winner[0]))
            m_botAction.cancelTask(spawnDelays.get(winner[0]));
        if (spawnDelays.containsKey(winner[1]))
            m_botAction.cancelTask(spawnDelays.get(winner[1]));
        // finalOff(d.getDivisionID(), d.getBoxNumber(), d.getPlayerOne().getDeaths() + d.getPlayerTwo().getDeaths(), d.getPlayerThree().getDeaths() + d.getPlayerFour().getDeaths());

        if (laggers.containsKey(winner[0])) {
            m_botAction.cancelTask(laggers.get(winner[0]));
            laggers.remove(winner[0]);
        }
        if (laggers.containsKey(winner[1])) {
            m_botAction.cancelTask(laggers.get(winner[1]));
            laggers.remove(winner[1]);
        }
        if (laggers.containsKey(loser[0])) {
            m_botAction.cancelTask(laggers.get(loser[0]));
            laggers.remove(loser[0]);
        }
        if (laggers.containsKey(loser[1])) {
            m_botAction.cancelTask(laggers.get(loser[1]));
            laggers.remove(loser[1]);
        }

        int loserScore = d.getPlayer(winner[0]).getDeaths() + d.getPlayer(winner[1]).getDeaths();
        int winnerScore = d.getPlayer(loser[0]).getDeaths() + d.getPlayer(loser[1]).getDeaths();
        
        if (type == 0) {
            m_botAction.sendPrivateMessage(winner[0], "You and '" + winner[1] + "' have defeated '" + loser[0] + "' and '" + loser[1] + "' score: (" + winnerScore + "-" + loserScore + ")");
            m_botAction.sendPrivateMessage(winner[1], "You and '" + winner[0] + "' have defeated '" + loser[0] + "' and '" + loser[1] + "' score: (" + winnerScore + "-" + loserScore + ")");
            m_botAction.sendPrivateMessage(loser[0], "You and '" + loser[1] + "' have been defeated by '" + winner[0] + "' and '" + winner[1] + "' score: (" + loserScore + "-" + winnerScore + ")");
            m_botAction.sendPrivateMessage(loser[1], "You and '" + loser[0] + "' have been defeated by '" + winner[0] + "' and '" + winner[1] + "' score: (" + loserScore + "-" + winnerScore + ")");
            m_botAction.sendTeamMessage(winner[0] + " and " + winner[1] + " defeat " + loser[0] + " and " + loser[1] + " in " + d.getDivision() + " score: (" + winnerScore + "-" + loserScore + ")");
        } else if (type == 1) {
            m_botAction.sendPrivateMessage(loser[0], "You have forfeited your duel because one of your team abused the spawning rule.", 103);
            m_botAction.sendPrivateMessage(loser[1], "You have forfeited your duel because one of your team abused the spawning rule.", 103);
            m_botAction.sendPrivateMessage(winner[0], loser[0] + " and " + loser[1] + " have forfeited due to abusing spawning.", 103);
            m_botAction.sendPrivateMessage(winner[1], loser[0] + " and " + loser[1] + " have forfeited due to abusing spawning.", 103);
            m_botAction.sendTeamMessage(loser[0] + " and " + loser[1] + " forfeit to " + winner[1] + " and " + winner[1] + " (SPAWNING) in their " + d.getDivision() + " duel");
        } else if (type == 2) {
            m_botAction.sendPrivateMessage(loser[0], "You have forfeited your duel due to one of your team's abuse of warping.", 103);
            m_botAction.sendPrivateMessage(loser[1], "You have forfeited your duel due to one of your team's abuse of warping.", 103);
            m_botAction.sendPrivateMessage(winner[0], loser[0] + " and " + loser[1] + " have forfeited due to abuse of warping.", 103);
            m_botAction.sendPrivateMessage(winner[1], loser[0] + " and " + loser[1] + " have forfeited due to abuse of warping.", 103);
            m_botAction.sendTeamMessage(loser[0] + " and " + loser[1] + " forfeit to " + winner[0] + " and " + winner[1] + " (WARPING) in their " + d.getDivision() + " duel");
        } else if (type == 3) {
            m_botAction.sendPrivateMessage(loser[0], "You have forfeited your duel due to one of your team lagging out too many times.", 103);
            m_botAction.sendPrivateMessage(loser[1], "You have forfeited your duel due to one of your team lagging out too many times.", 103);
            m_botAction.sendPrivateMessage(winner[0], loser[0] + " and " + loser[1] + " have forfeited due to lagging out too many times.", 103);
            m_botAction.sendPrivateMessage(winner[1], loser[0] + " and " + loser[1] + " have forfeited due to lagging out too many times.", 103);
            m_botAction.sendTeamMessage(loser[0] + " and " + loser[1] + " forfeit to " + winner[0] + " and " + winner[1] + " (LAGOUTS) in their " + d.getDivision() + " duel");
        } else if (type == 4) {
            m_botAction.sendPrivateMessage(loser[0], "One of your team has been lagged out for over 1 minute and forfeits your duel.", 103);
            m_botAction.sendPrivateMessage(loser[1], "One of your team has been lagged out for over 1 minute and forfeits your duel.", 103);
            m_botAction.sendPrivateMessage(winner[0], loser[0] + " or " + loser[1] + " have been lagged out for over 1 minute and forfeit the duel.", 103);
            m_botAction.sendPrivateMessage(winner[1], loser[0] + " or " + loser[1] + " have been lagged out for over 1 minute and forfeit the duel.", 103);
            m_botAction.sendTeamMessage(loser[0] + " and " + loser[1] + " forfeit to " + winner[0] + " and " + winner[1] + " (1 MIN LAGOUT) in their " + d.getDivision() + " duel");
        }
        
        to = null;
        from = challed[1];
        m_botAction.sendUnfilteredPrivateMessage(challed[1], "*lag");
        
        int division = d.getDivisionID();
        d.toggleDuelBox();
        duels.remove(new Integer(d.getBoxNumber()));
        playing.remove(winner[0]);
        playing.remove(winner[1]);
        playing.remove(loser[0]);
        playing.remove(loser[1]);
        // putting players into spec freq
        m_botAction.setShip(winner[0], 1);
        m_botAction.setShip(winner[1], 1);
        m_botAction.setShip(loser[0], 1);
        m_botAction.setShip(loser[1], 1);
        m_botAction.spec(winner[0]);
        m_botAction.spec(winner[0]);
        m_botAction.spec(winner[1]);
        m_botAction.spec(winner[1]);
        m_botAction.spec(loser[0]);
        m_botAction.spec(loser[1]);
        m_botAction.spec(loser[0]);
        m_botAction.spec(loser[1]);
        
        
        ResultSet loserSet = sql_getTeamInfo(loserTeam);
        ResultSet winnerSet = sql_getTeamInfo(winnerTeam);
        try {
            // Calculate new streaks.
            int loserStreak = loserSet.getInt("fnLossStreak");
            int loserCurStreak = loserSet.getInt("fnCurrentLossStreak") + 1;
            if (loserStreak < loserCurStreak)
                loserStreak = loserCurStreak;
            int winnerStreak = winnerSet.getInt("fnWinStreak");
            int winnerCurStreak = winnerSet.getInt("fnCurrentWinStreak") + 1;
            if (winnerStreak < winnerCurStreak)
                winnerStreak = winnerCurStreak;

            if ((lastZoner + 60 * 60 * 1000) < System.currentTimeMillis() && winnerCurStreak > 5)
                streakZoner(winner, winnerCurStreak, d.getDivision());

            boolean aced = false;
            if (d.getPlayer(loser[0]).getDeaths() == 0 && d.getPlayer(loser[1]).getDeaths() == 0)
                aced = true;
            
            // Calculate new ratings.
            int winnerRatingBefore = winnerSet.getInt("fnRating");
            int loserRatingBefore = loserSet.getInt("fnRating");
            int loserRatingAfter = 0;
            int winnerRatingAfter = 0;
            
            if (winnerRatingBefore == 0)
                winnerRatingAfter = 45;
            else
                winnerRatingAfter = (int) Math.round((winnerRatingBefore + 32*(1 - (1 / (1 + Math.pow(10, (loserRatingBefore - winnerRatingBefore) / 400))))));
            if (loserRatingBefore <= 0)
                loserRatingAfter = 0;
            else
                loserRatingAfter = (int) Math.round((loserRatingBefore + 32*(0 - (1 / (1 + Math.pow(10, (winnerRatingBefore - loserRatingBefore) / 400))))));

            int time = d.getTime();
            // Store loser information
            DuelPlayerStats[] loserStats = new DuelPlayerStats[2];
            loserStats[0] = d.getPlayer(loser[0]);
            loserStats[1] = d.getPlayer(loser[1]);
            sql_storeUserLoss(losers[0].getID(), loserTeam, division, loserStats[0].getKills(), loserStats[0].getDeaths(), loserStats[0].getSpawns(), loserStats[0].getSpawned(), loserStats[0].getLagouts(), time);
            sql_storeUserLoss(losers[1].getID(), loserTeam, division, loserStats[1].getKills(), loserStats[1].getDeaths(), loserStats[1].getSpawns(), loserStats[1].getSpawned(), loserStats[1].getLagouts(), time);
            sql_storeTeamLoss(loserTeam, division, loserStreak, loserCurStreak, winnerTeam, loserRatingAfter, aced);
            // Store winner information
            DuelPlayerStats[] winnerStats = new DuelPlayerStats[2];
            winnerStats[0] = d.getPlayer(winner[0]);
            winnerStats[1] = d.getPlayer(winner[1]);
            sql_storeUserWin(winners[0].getID(), winnerTeam, division, winnerStats[0].getKills(), winnerStats[0].getDeaths(), winnerStats[0].getSpawns(), winnerStats[0].getSpawned(), winnerStats[0].getLagouts(), time);
            sql_storeUserWin(winners[1].getID(), winnerTeam, division, winnerStats[1].getKills(), winnerStats[1].getDeaths(), winnerStats[1].getSpawns(), winnerStats[1].getSpawned(), winnerStats[1].getLagouts(), time);
            sql_storeTeamWin(winnerTeam, division, winnerStreak, winnerCurStreak, loserTeam, winnerRatingAfter, aced);

            String[] fields = new String[] {"fnSeason", "fnLeagueTypeID", "fnBoxType", "fnWinnerScore", "fnLoserScore", "fnWinnerTeamID", "fnLoserTeamID", "fnWinner1Ship", "fnWinner2Ship", "fnLoser1Ship", "fnLoser2Ship", "fnCommentID", "fnDeaths", "fnDuration", "fnWinnerRatingBefore", "fnWinnerRatingAfter", "fnLoserRatingBefore", "fnLoserRatingAfter"};
            String[] values = new String[] {"" + s_season, "" + division, "" + d.getBoxType(), "" + winnerScore, "" + loserScore, "" + winnerTeam, "" + loserTeam, "" + winnerStats[0].getShip(), "" + winnerStats[1].getShip(), "" + loserStats[0].getShip(), "" + loserStats[1].getShip(), "" + type, "" + d.toWin(), "" + d.getTime(), "" + winnerRatingBefore, "" + winnerRatingAfter, "" + loserRatingBefore, "" + loserRatingAfter}; 
            m_botAction.SQLBackgroundInsertInto(mySQLHost, "tblDuel__2match", fields, values);
            /*
            String query = "INSERT INTO `tblDuel__2match` (`fnSeason`, `fnLeagueTypeID`, `fnBoxType`, `fnWinnerScore`, `fnLoserScore`, `fnWinnerTeamID`, `fnLoserTeamID`, `fnWinner1Ship`, `fnWinner2Ship`, `fnLoser1Ship`, `fnLoser2Ship`, `fnCommentID`, `fnDeaths`, `fnDuration`, `fnWinnerRatingBefore`, `fnWinnerRatingAfter`, `fnLoserRatingBefore`, `fnLoserRatingAfter` ) VALUES (";
            query += s_season + ", " + division + ", " + d.getBoxType() + ", " + winnerScore + ", " + loserScore + ", ";
            query += "" + winnerTeam + ", " + loserTeam + ", ";
            query += winnerStats[0].getShip() + ", " + winnerStats[1].getShip() + ", " + loserStats[0].getShip() + ", " + loserStats[1].getShip() + ", ";
            query += type + ", ";
            query += d.toWin() + ", " + d.getTime() + ", ";
            query += winnerRatingBefore + ", " + winnerRatingAfter + ", ";
            query += loserRatingBefore + ", " + loserRatingAfter + ")";
            m_botAction.SQLQueryAndClose(mySQLHost, query);
             */
        } catch (Exception e) {
            Tools.printStackTrace("Error ending duel", e);
        }
        m_botAction.SQLClose(loserSet);
        m_botAction.SQLClose(winnerSet);

        if (shutDownDie && duels.size() == 0) {
            m_botAction.sendArenaMessage("Shutting down for core maintenance.", 1);
            TimerTask dieTask = new TimerTask() {
                public void run() {
                    m_botAction.die();
                }
            };
            m_botAction.scheduleTask(dieTask, 5000);
        }
    }
    
    public void do_score(Duel duel, int type) {
        int scoreWinner = duel.getPlayerThree().getDeaths() + duel.getPlayerFour().getDeaths();
        int scoreLoser = duel.getPlayerOne().getDeaths() + duel.getPlayerTwo().getDeaths();
        String[] winner = {duel.getPlayerOne().getName(), duel.getPlayerTwo().getName()};
        String[] loser = {duel.getPlayerThree().getName(), duel.getPlayerFour().getName()};
        int winnerTeam = duel.getChallengerTeam();
        int loserTeam = duel.getChallengedTeam();
        int winnerFreq = duel.getChallengerFreq();
        int loserFreq = duel.getChallengedFreq();

        if (scoreWinner < scoreLoser) {
            String[] temp = winner;
            winner = loser;
            loser = temp;
            scoreWinner = scoreLoser;
            scoreLoser = duel.getPlayerThree().getDeaths() + duel.getPlayerFour().getDeaths();
            winnerTeam = loserTeam;
            loserTeam = duel.getChallengerTeam();
            winnerFreq = loserFreq;
            loserFreq = duel.getChallengerFreq();
        }

        m_botAction.sendOpposingTeamMessageByFrequency(winnerFreq, "Score: " + scoreWinner + "-" + scoreLoser, 26);
        m_botAction.sendOpposingTeamMessageByFrequency(loserFreq, "Score: " + scoreLoser + "-" + scoreWinner, 26);

        if (scoreWinner >= duel.toWin()*2)
            endDuel(duel, winner, loser, winnerTeam, loserTeam, type);
    }

    public void streakZoner(String[] name, int streak, String divisionName) {
        if (streak >= 10 && streak < 15)
            m_botAction.sendZoneMessage(name[0] + " and " + name[1] + " are on a roll, bring your team and ?go " + m_botAction.getArenaName() + " to try to stop this " + divisionName + " Team's " + streak + " game winning streak! -" + m_botAction.getBotName(), 2);
        else if (streak >= 15 && streak < 20)
            m_botAction.sendZoneMessage(name[0] + " and " + name[1] + " are on fire with a " + streak + " game winning streak in " + divisionName + ". Come stop them before he burns down ?go " + m_botAction.getArenaName() + " -" + m_botAction.getBotName(), 2);
        else if (streak >= 20)
            m_botAction.sendZoneMessage("Someone bring the kryptonite to ?go " + m_botAction.getArenaName() + ", " + name[0] + " and " + name[1] + " have a " + streak + " game winning streak in " + divisionName + "! -" + m_botAction.getBotName(), 2);

        lastZoner = System.currentTimeMillis();
    }

    public void warpPlayers(String[] one, String[] two) {
        Duel d = playing.get(one[0]);

        if (d.getPlayerNumber(one[0]) == 1) {
            m_botAction.warpTo(one[0], d.getAXOne(), d.getAYOne());
            m_botAction.warpTo(one[1], d.getAXTwo(), d.getAYTwo());
            m_botAction.warpTo(two[0], d.getBXOne(), d.getBYOne());
            m_botAction.warpTo(two[1], d.getBXTwo(), d.getBYTwo());
        } else {
            m_botAction.warpTo(two[0], d.getAXOne(), d.getAYOne());
            m_botAction.warpTo(two[1], d.getAXTwo(), d.getAYTwo());
            m_botAction.warpTo(one[0], d.getBXOne(), d.getBYOne());
            m_botAction.warpTo(one[1], d.getBXTwo(), d.getBYTwo());
        }
    }

    public void clearScoreboard(Duel d) {
        // Shutoff scoreboard
        int[] challenger = { m_botAction.getPlayerID(d.getPlayerOne().getName()), m_botAction.getPlayerID(d.getPlayerTwo().getName()) };
        int[] challenged = { m_botAction.getPlayerID(d.getPlayerThree().getName()), m_botAction.getPlayerID(d.getPlayerFour().getName()) };
        objects.hideAllObjects(challenger[0]);
        objects.hideAllObjects(challenger[1]);
        objects.hideAllObjects(challenged[0]);
        objects.hideAllObjects(challenged[1]);
    }
 
    /**
     * SQL Database Queries
     */
    
    public DuelPlayer sql_buildPlayer(String name) {
        int id;
        int lag = 0;
        int deaths = -1;
        boolean nc = true;
        boolean enabled = false;
        boolean banned = false;
        int[] teams = new int[6];
        int[] pids = new int[6];
        String[] partners = new String[6];
        for (int i = 1; i < 6; i++) {
            teams[i] = -1;
            partners[i] = null;
        }

        try {
            // METHOD TWO
            String q = "SELECT p.fnUserID, b.fnBanID, p.fnLag, p.fnGameDeaths, p.fnNoCount, p.fnEnabled FROM tblDuel__2player p LEFT JOIN tblDuel__2ban b ON p.fnUserID = b.fnUserID WHERE p.fnUserID = (SELECT fnUserID FROM tblUser WHERE fcUserName = '" + Tools.addSlashesToString(name) + "' ORDER BY fnUserID ASC LIMIT 1) LIMIT 1";
            ResultSet info = m_botAction.SQLQuery(mySQLHost, q);
            if (info.next()) {
                Integer tenabled = info.getInt("p.fnEnabled");
                if (tenabled != null) {
                    if (tenabled == 0)
                        enabled = false;
                    else
                        enabled = true;
                } else
                    return null;
                Integer tid = info.getInt("p.fnUserID");
                if (tid != null && tid > 0)
                    id = tid;
                else
                    return null;
                Integer tban = info.getInt("b.fnBanID");
                if (tban != null && tban > 0)
                    banned = true;
                Integer tlag = info.getInt("p.fnLag");
                if (tlag != null && tlag > 0)
                    lag = tlag;
                Integer tdeaths = info.getInt("p.fnGameDeaths");
                if (tdeaths != null)
                    deaths = tdeaths;
                Integer tnc = info.getInt("p.fnNoCount");
                if (tnc != null && tnc == 0)
                    nc = false;
                m_botAction.SQLClose(info);
            } else {
                m_botAction.SQLClose(info);
                return null;
            }
            
            q = "SELECT t.fnTeamID, t.fnLeagueTypeID, u2.fnUserID, u2.fcUserName FROM tblDuel__2team t JOIN tblUser u JOIN tblUser u2 ON (u.fnUserID = t.fnUser1ID AND u2.fnUserID = t.fnUser2ID) OR (u.fnUserID = t.fnUser2ID AND u2.fnUserID = t.fnUser1ID) WHERE t.fnTeamID IN (SELECT fnTeamID FROM tblDuel__2league WHERE fnUserID = " + id + " AND fnStatus = 1 AND fnSeason = " + s_season + ") AND u.fnUserID = " + id + " AND t.fnStatus = 1 AND t.fnSeason = " + s_season;
            info = m_botAction.SQLQuery(mySQLHost, q);
            while (info.next()) {
                Integer div = info.getInt("t.fnLeagueTypeID");
                if (div != null && div > 0 && div < 6) {
                    Integer team = info.getInt("t.fnTeamID");
                    if (team != null && team > 0) {
                        teams[div] = team;
                        partners[div] = info.getString("u2.fcUserName");
                        pids[div] = info.getInt("u2.fnUserID");

                    }
                }
            }
            m_botAction.SQLClose(info);
            return new DuelPlayer(name, id, lag, deaths, nc, enabled, banned, teams, partners, pids);
        } catch (Exception e) {
            Tools.printStackTrace("Failed to get player information", e);
        }
        
        /** old way which apparently doesn't work
        String query = "SELECT U.fnUserID, B.fnBanID, P.fnGameDeaths, P.fnNoCount, P.fnEnabled, P.fnLag, " + 
                "L.fnLeagueTypeID, L.fnTeamID, T.fnTeamID, T.fnLeagueTypeID, U2.fnUserID, U2.fcUserName " + 
            "FROM tblUser U LEFT JOIN tblDuel__2player P ON U.fnUserID = P.fnUserID " + 
            "LEFT JOIN tblDuel__2ban B ON P.fnUserID = B.fnUserID LEFT JOIN tblDuel__2league L ON L.fnSeason = " + s_season + " " + 
                "AND L.fnStatus = 1 AND P.fnUserID = L.fnUserID " + 
            "LEFT JOIN tblDuel__2team T ON T.fnSeason = " + s_season + " AND T.fnStatus = 1 AND L.fnTeamID = T.fnTeamID " + 
            "LEFT JOIN tblUser U2 ON (U.fnUserID = T.fnUser1ID AND U2.fnUserID = T.fnUser2ID) " + 
                "OR (U.fnUserID = T.fnUser2ID AND U2.fnUserID = T.fnUser1ID) " + 
            "WHERE U.fnUserID = (SELECT fnUserID FROM tblUser WHERE fcUserName = '" + Tools.addSlashesToString(name) + "' ORDER BY fnUserID ASC LIMIT 1)";
        try {
            ResultSet result = m_botAction.SQLQuery(mySQLHost, query);
            if (result.next()) {
                do {
                    Integer tid = result.getInt("U.fnUserID");
                    if (!result.wasNull())
                        id = tid;
                    else
                        return null;
                    Integer tban = result.getInt("B.fnBanID");
                    if (!result.wasNull() && tban > -1)
                        banned = true;
                    Integer tlag = result.getInt("P.fnLag");
                    if (!result.wasNull() && tlag > 0)
                        lag = tlag;
                    Integer tdeaths = result.getInt("P.fnGameDeaths");
                    if (!result.wasNull())
                        deaths = tdeaths;
                    Integer tenabled = result.getInt("P.fnEnabled");
                    if (!result.wasNull()) {
                        if (tenabled == 0)
                            enabled = false;
                        else
                            enabled = true;
                    }
                    Integer tnc = result.getInt("P.fnNoCount");
                    if (!result.wasNull() && tnc == 0)
                        nc = false;
                    Integer ldiv = result.getInt("L.fnLeagueTypeID");
                    if (!result.wasNull()) {
                        Integer div = result.getInt("T.fnLeagueTypeID");
                        if (!result.wasNull()) {
                            Integer lteam = result.getInt("L.fnTeamID");
                            if (!result.wasNull()) {
                                Integer team = result.getInt("T.fnTeamID");
                                if (!result.wasNull() && ldiv.equals(div) && team.equals(lteam)) {
                                    teams[div] = team;
                                    partners[div] = result.getString("U2.fcUserName");
                                    pids[div] = result.getInt("U2.fnUserID");
                                    
                                }
                                
                            }
                            
                        }
                        
                    }
                } while (result.next());
                m_botAction.SQLClose(result);
                return new DuelPlayer(name, id, lag, deaths, nc, enabled, banned, teams, partners, pids);
            }
        } catch (Exception e) {
            Tools.printStackTrace("Failed to get player information", e);
        } 
        **/
        return null;
    }
    
    public DuelPlayer sql_buildPlayer(String name, int userID) {
        int lag = 0;
        int deaths = -1;
        boolean nc = true;
        boolean enabled = false;
        boolean banned = false;
        int[] teams = new int[6];
        int[] pids = new int[6];
        String[] partners = new String[6];
        for (int i = 1; i < 6; i++) {
            teams[i] = -1;
            partners[i] = null;
        }

        try {
            // METHOD TWO
            String q = "SELECT p.fnUserID, b.fnBanID, p.fnLag, p.fnGameDeaths, p.fnNoCount, p.fnEnabled FROM tblDuel__2player p LEFT JOIN tblDuel__2ban b ON p.fnUserID = b.fnUserID WHERE p.fnUserID = " + userID + " LIMIT 1";
            ResultSet info = m_botAction.SQLQuery(mySQLHost, q);
            if (info.next()) {
                Integer tenabled = info.getInt("p.fnEnabled");
                if (tenabled != null) {
                    if (tenabled == 0)
                        enabled = false;
                    else
                        enabled = true;
                } else
                    return null;
                Integer tid = info.getInt("p.fnUserID");
                if (tid != null && userID == tid);
                else
                    return null;
                Integer tban = info.getInt("b.fnBanID");
                if (tban != null && tban > 0)
                    banned = true;
                Integer tlag = info.getInt("p.fnLag");
                if (tlag != null && tlag > 0)
                    lag = tlag;
                Integer tdeaths = info.getInt("p.fnGameDeaths");
                if (tdeaths != null)
                    deaths = tdeaths;
                Integer tnc = info.getInt("p.fnNoCount");
                if (tnc != null && tnc == 0)
                    nc = false;
                m_botAction.SQLClose(info);
            } else {
                m_botAction.SQLClose(info);
                return null;
            }
            
            q = "SELECT t.fnTeamID, t.fnLeagueTypeID, u2.fnUserID, u2.fcUserName FROM tblDuel__2team t JOIN tblUser u JOIN tblUser u2 ON (u.fnUserID = t.fnUser1ID AND u2.fnUserID = t.fnUser2ID) OR (u.fnUserID = t.fnUser2ID AND u2.fnUserID = t.fnUser1ID) WHERE t.fnTeamID IN (SELECT fnTeamID FROM tblDuel__2league WHERE fnUserID = " + userID + " AND fnStatus = 1 AND fnSeason = " + s_season + ") AND u.fnUserID = " + userID + " AND t.fnStatus = 1 AND t.fnSeason = " + s_season;
            info = m_botAction.SQLQuery(mySQLHost, q);
            while (info.next()) {
                Integer div = info.getInt("t.fnLeagueTypeID");
                if (div != null && div > 0 && div < 6) {
                    Integer team = info.getInt("t.fnTeamID");
                    if (team != null && team > 0) {
                        teams[div] = team;
                        partners[div] = info.getString("u2.fcUserName");
                        pids[div] = info.getInt("u2.fnUserID");

                    }
                }
            }
            m_botAction.SQLClose(info);
            return new DuelPlayer(name, userID, lag, deaths, nc, enabled, banned, teams, partners, pids);
        } catch (Exception e) {
            Tools.printStackTrace("Failed to get player information", e);
        }
        /** old way which apparently doesnt work
        String query = "SELECT U.fnUserID, B.fnBanID, P.fnGameDeaths, P.fnNoCount, P.fnEnabled, P.fnLag, " + 
                "L.fnLeagueTypeID, L.fnTeamID, T.fnTeamID, T.fnLeagueTypeID, U2.fnUserID, U2.fcUserName " + 
            "FROM tblUser U LEFT JOIN tblDuel__2player P ON U.fnUserID = P.fnUserID " + 
            "LEFT JOIN tblDuel__2ban B ON P.fnUserID = B.fnUserID LEFT JOIN tblDuel__2league L ON L.fnSeason = " + s_season + " " + 
                "AND L.fnStatus = 1 AND P.fnUserID = L.fnUserID " + 
            "LEFT JOIN tblDuel__2team T ON T.fnSeason = " + s_season + " AND T.fnStatus = 1 AND L.fnTeamID = T.fnTeamID " + 
            "LEFT JOIN tblUser U2 ON (U.fnUserID = T.fnUser1ID AND U2.fnUserID = T.fnUser2ID) " + 
                "OR (U.fnUserID = T.fnUser2ID AND U2.fnUserID = T.fnUser1ID) " + 
            "WHERE U.fnUserID = " + userID;
        try {
            ResultSet result = m_botAction.SQLQuery(mySQLHost, query);
            if (result.next()) {
                do {
                    Integer tban = result.getInt("B.fnBanID");
                    if (!result.wasNull() && tban > -1)
                        banned = true;
                    Integer tlag = result.getInt("P.fnLag");
                    if (!result.wasNull() && tlag > 0)
                        lag = tlag;
                    Integer tdeaths = result.getInt("P.fnGameDeaths");
                    if (!result.wasNull())
                        deaths = tdeaths;
                    Integer tenabled = result.getInt("P.fnEnabled");
                    if (!result.wasNull()) {
                        if (tenabled == 0)
                            enabled = false;
                        else
                            enabled = true;
                    }
                    Integer tnc = result.getInt("P.fnNoCount");
                    if (!result.wasNull() && tnc == 0)
                        nc = false;
                    Integer ldiv = result.getInt("L.fnLeagueTypeID");
                    if (!result.wasNull()) {
                        Integer div = result.getInt("T.fnLeagueTypeID");
                        if (!result.wasNull()) {
                            Integer lteam = result.getInt("L.fnTeamID");
                            if (!result.wasNull()) {
                                Integer team = result.getInt("T.fnTeamID");
                                if (!result.wasNull() && ldiv.equals(div) && team.equals(lteam)) {
                                    teams[div] = team;
                                    partners[div] = result.getString("U2.fcUserName");
                                    pids[div] = result.getInt("U2.fnUserID");
                                    
                                }
                                
                            }
                            
                        }
                        
                    }
                } while (result.next());
                return new DuelPlayer(name, userID, lag, deaths, nc, enabled, banned, teams, partners, pids);
            }
        } catch (Exception e) {
            Tools.printStackTrace("Failed to get player information", e);
        }
        **/
        return null;
    }
    
    public int sql_getUserID(String name) {
        int id = -1;
        try {
            ResultSet rs = m_botAction.SQLQuery(mySQLHost, "SELECT fnUserID FROM tblUser WHERE fcUserName = '" + name + "' ORDER BY fnUserID ASC LIMIT 1");
            if (rs.next()) {
                id = rs.getInt("fnUserID");
                m_botAction.SQLClose(rs);
            } else
                m_botAction.SQLQueryAndClose(mySQLHost, "INSERT INTO tblUser (fcUserName, fdSignedUp) VALUES ('" + Tools.addSlashesToString(name) + "', NOW())");
        } catch (Exception e) {
            Tools.printLog("Couldn't get or create user: " + name);
        }
        return id;        
    }
    
    public ArrayList<Integer> sql_getTeams(String name) {
        ArrayList<Integer> teams = new ArrayList<Integer>(6);
        teams.add(0, -1);
        for (int i = 1; i <=5; i++)
            teams.add(i, -1);
        try {
            String query = "SELECT l.fnUserID, l.fnTeamID, l.fnLeagueTypeID FROM tblDuel__2league l JOIN tblUser u ON u.fnUserID = l.fnUserID WHERE l.fnSeason = " + s_season + " AND l.fnStatus = 1 AND u.fcUserName = '" + Tools.addSlashesToString(name) + "' ORDER BY l.fnLeagueTypeID";
            ResultSet result = m_botAction.SQLQuery(mySQLHost, query);
            if (result.next()) {
                teams.add(0, result.getInt("l.fnUserID"));
                do {
                    teams.add(result.getInt("l.fnLeagueTypeID"), result.getInt("l.fnTeamID"));                    
                } while (result.next());
            }
            m_botAction.SQLClose(result);
        } catch (Exception e) { }        
        return teams;
    }
    
    public int sql_getTeamDivision(int teamID) {
        int id = 0;
        try {
            String query = "SELECT fnLeagueTypeID FROM tblDuel__2team WHERE fnSeason = " + s_season + " AND fnStatus = 1 AND fnTeamID = " + teamID;
            ResultSet result = m_botAction.SQLQuery(mySQLHost, query);
            if (result.next()) {
                id = result.getInt("fnLeagueTypeID");
            }
            m_botAction.SQLClose(result);
        } catch (Exception e) { }
        return id;
    }
    
    public String[] sql_getPartners(int teamID) {
        String[] info = new String[2];
        try {
            String query = "SELECT u.fcUserName FROM tblUser u JOIN tblDuel__2team t ON (t.fnUser1ID = u.fnUserID OR t.fnUser2ID = u.fnUserID) WHERE t.fnSeason = " + s_season + " AND t.fnStatus = 1 AND t.fnTeamID = " + teamID;
            ResultSet team = m_botAction.SQLQuery(mySQLHost, query);
            if (team.next())
                info[0] = team.getString("u.fcUserName");
            if (team.next())
                info[1] = team.getString("u.fcUserName");
            m_botAction.SQLClose(team);
        } catch (Exception e) { }
        return info;
    }
    
    public boolean sql_banned(String name) {
        try {
            boolean banned = false;
            ResultSet result = m_botAction.SQLQuery(mySQLHost, "SELECT * FROM tblDuel__2ban WHERE fnUserID = (SELECT fnUserID FROM tblUser WHERE fcUserName = '"
                    + Tools.addSlashesToString(name) + "' ORDER BY fnUserID ASC LIMIT 1)");
            if (result.next())
                banned = true;
            m_botAction.SQLClose(result);
            return banned;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean sql_banPlayer(int id, String comment) {
        try {
            m_botAction.SQLQueryAndClose(mySQLHost, "INSERT INTO tblDuel__2ban (fnUserID, fcComment) VALUES(" + id + ", '" + Tools.addSlashesToString(comment) + "')");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean sql_banPlayer(String name, String comment) {
        try {
            m_botAction.SQLQueryAndClose(mySQLHost, "INSERT INTO tblDuel__2ban (fnUserID, fcComment) VALUES((SELECT fnUserID FROM tblUser WHERE fcUserName = '" + name + "' ORDER BY fnUserID ASC LIMIT 1), '" + Tools.addSlashesToString(comment) + "')");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean sql_unbanPlayer(int id) {
        try {
            m_botAction.SQLQueryAndClose(mySQLHost, "DELETE FROM tblDuel__2ban WHERE fnUserID = " + id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean sql_unbanPlayer(String name) {
        try {
            m_botAction.SQLQueryAndClose(mySQLHost, "DELETE FROM tblDuel__2ban WHERE fnUserID = (SELECT fnUserID FROM tblUser WHERE fcUserName = '" + name + "' ORDER BY fnUserID ASC LIMIT 1)");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean sql_enabledUser(String name) {

        try {
            String query = "SELECT fnUserID FROM tblDuel__2player WHERE fnEnabled = 1 AND fnUserID = (SELECT fnUserID FROM tblUser WHERE fcUserName = '"
                    + Tools.addSlashesToString(name) + "' ORDER BY fnUserID ASC LIMIT 1)";
            ResultSet result = m_botAction.SQLQuery(mySQLHost, query);
            boolean hasNext = result.next();
            m_botAction.SQLClose(result);
            return hasNext;
        } catch (Exception e) {
            Tools.printStackTrace("Failed to check for enabled user", e);
            return false;
        }
    }
    
    public boolean sql_enabledUser(int id) {
        try {
            String query = "SELECT * FROM tblDuel__2player WHERE fnEnabled = 1 AND fnUserID = " + id;
            ResultSet result = m_botAction.SQLQuery(mySQLHost, query);
            boolean hasNext = result.next();
            m_botAction.SQLClose(result);
            return hasNext;
        } catch (Exception e) {
            Tools.printStackTrace("Failed to check for enabled user", e);
            return false;
        }
    }
    
    public void sql_enableUser(int id) {
        try {
            String query = "UPDATE tblDuel__2player SET fnEnabled = 1 WHERE fnUserID = " + id;
            m_botAction.SQLQueryAndClose(mySQLHost, query);
        } catch (Exception e) {
            Tools.printStackTrace("Error enabling user", e);
        }
    }
    
    // Disable not only disables the player, but disables all the teams player was on
    public void sql_disableUser(int id) {
        try {
            m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuel__2player SET fnEnabled = 0 WHERE fnUserID = " + id);
            m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuel__2team SET fnStatus = 0 WHERE fnSeason = " + s_season + " AND fnStatus = 1 AND (fnUser1ID = " + id + " OR fnUser2ID = " + id + ")");
            m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuel__2league SET fnStatus = 0 WHERE fnSeason = " + s_season + " AND fnStatus = 1 AND fnUserID = " + id);           
        } catch (Exception e) {
            Tools.printStackTrace("Error disabling user", e);
        }
    }
    
    // Now rendered useless due to DuelPlayer holding all that information
    public boolean sql_divisionCheck(String name, String part, int div) {
        if ((div > 0 && div < 6) || div == 7) {
            if (div == 7)
                div = 4;
            try {
                ResultSet user = m_botAction.SQLQuery(mySQLHost, "SELECT L.fnTeamID, L.fnStatus FROM tblDuel__2league L JOIN tblUser U ON U.fnUserID = L.fnUserID WHERE L.fnSeason = " + s_season + " AND U.fcUserName = '" + Tools.addSlashesToString(name) + "' AND L.fnLeagueTypeID = " + div + " AND L.fnStatus = 1");
                if (user.next()) {
                    m_botAction.sendPrivateMessage(name, "You are all ready registered for a team in that division.");
                    m_botAction.SQLClose(user);   
                    return false;
                }
                m_botAction.SQLClose(user);                
                user = m_botAction.SQLQuery(mySQLHost, "SELECT L.fnTeamID, L.fnStatus FROM tblDuel__2league L JOIN tblUser U ON U.fnUserID = L.fnUserID WHERE L.fnSeason = " + s_season + " AND fcUserName = '" + Tools.addSlashesToString(part) + "' AND L.fnLeagueTypeID = " + div + " AND L.fnStatus = 1");
                if (user.next()) {
                    m_botAction.sendPrivateMessage(name, part + " is all ready registered for a team in that division.");
                    m_botAction.SQLClose(user);                   
                    return false;
                }
                m_botAction.SQLClose(user);
            } catch (Exception e) { 
                return false;
            }
            return true;
        } else 
            return false;
    }
    
    public int sql_createTeam(DuelPlayer a, DuelPlayer b, int div) {
        int id = -1;
        String query = "INSERT INTO tblDuel__2team (fnSeason, fnLeagueTypeID, fnUser1ID, fnUser2ID) VALUES(" + s_season + ", " + div + ", " + a.getID() + ", " + b.getID() + ")";
        try {
            m_botAction.SQLQueryAndClose(mySQLHost, query);
            ResultSet rs = m_botAction.SQLQuery(mySQLHost, "SELECT fnTeamID FROM tblDuel__2team WHERE fnUser1ID = " + a.getID() + " AND fnUser2ID = " + b.getID() + " AND fnSeason = " + s_season + " AND fnStatus = 1 AND fnLeagueTypeID = " + div);
            if (rs.next())
                id = rs.getInt("fnTeamID");
            m_botAction.SQLClose(rs);
            query = "INSERT INTO tblDuel__2league (fnSeason, fnTeamID, fnUserID, fnLeagueTypeID) VALUES(" + s_season + ", " + id + ", " + a.getID() + ", " + div + "),(" + s_season + ", " + id + ", " + b.getID() + ", " + div + ")";
            m_botAction.SQLQueryAndClose(mySQLHost, query);
        } catch (SQLException e) {
            e.printStackTrace();
        }        
        return id;
    }

    public void sql_storeUserLoss(int id, int teamID, int division, int kills, int deaths, int spawns, int spawned, int lagouts, int time) {
        try {
            String query = "UPDATE tblDuel__2league SET fnLosses = fnLosses + 1, fnKills = fnKills + " + kills + 
            ", fnDeaths = fnDeaths + " + deaths + ", fnTimePlayed = fnTimePlayed + " + time + 
            ", fnSpawns = fnSpawns + " + spawns + ", fnSpawned = fnSpawned + " + spawned + 
            ", fnLagouts = fnLagouts + " + lagouts + 
            " WHERE fnSeason = " + s_season + " AND fnStatus = 1 AND fnTeamID = " + teamID + " AND fnUserID = " + id + " AND fnLeagueTypeID = " + division;
            m_botAction.SQLQueryAndClose(mySQLHost, query);
            query = "UPDATE tblDuel__2player SET fdLastPlayed = NOW() WHERE fnUserID = " + id;
            m_botAction.SQLQueryAndClose(mySQLHost, query);
        } catch (Exception e) {
            Tools.printStackTrace("Failed to store user loss", e);
        }
    }

    public void sql_storeUserWin(int id, int teamID, int division, int kills, int deaths, int spawns, int spawned, int lagouts, int time) {
        try {
            String query = "UPDATE tblDuel__2league SET fnWins = fnWins + 1, fnKills = fnKills + " + kills + 
            ", fnDeaths = fnDeaths + " + deaths + ", fnTimePlayed = fnTimePlayed + " + time + 
            ", fnSpawns = fnSpawns + " + spawns + ", fnSpawned = fnSpawned + " + spawned + 
            ", fnLagouts = fnLagouts + " + lagouts + 
            " WHERE fnSeason = " + s_season + " AND fnStatus = 1 AND fnTeamID = " + teamID + " AND fnUserID = " + id + " AND fnLeagueTypeID = " + division;
            m_botAction.SQLQueryAndClose(mySQLHost, query);
            query = "UPDATE tblDuel__2player SET fdLastPlayed = NOW() WHERE fnUserID = " + id;
            m_botAction.SQLQueryAndClose(mySQLHost, query);
        } catch (Exception e) {
            Tools.printStackTrace("Failed to store user win", e);
        }
    }
    
    public void sql_storeTeamWin(int teamID, int division, int winnerStreak, int winnerCurStreak, int wonAgainst, int rating, boolean aced ) {
        try {
            String query;
            if (aced) {
                query = "UPDATE tblDuel__2team SET fnWins = fnWins + 1, fnWinStreak = " + winnerStreak + 
                ", fnCurrentWinStreak = " + winnerCurStreak + ", fnCurrentLossStreak = 0, fnAces = fnAces + 1, fnLastWin = " + wonAgainst + 
                ", fnRating = " + rating + 
                ", fdLastPlayed = NOW()" +
                " WHERE fnSeason = " + s_season + " AND fnStatus = 1 AND fnTeamID = " + teamID + " AND fnLeagueTypeID = " + division;
            } else {
                query = "UPDATE tblDuel__2team SET fnWins = fnWins + 1, fnWinStreak = " + winnerStreak + 
                ", fnCurrentWinStreak = " + winnerCurStreak + ", fnCurrentLossStreak = 0, fnLastWin = " + wonAgainst + ", fnRating = " + rating +
                ", fdLastPlayed = NOW()" +
                " WHERE fnSeason = " + s_season + " AND fnStatus = 1 AND fnTeamID = " + teamID + 
                " AND fnLeagueTypeID = " + division;
            }
            m_botAction.SQLQueryAndClose(mySQLHost, query);
        } catch (Exception e) {
            Tools.printStackTrace( "Failed to store team win", e );
        }
    }
    
    public void sql_storeTeamLoss(int teamID, int division, int loserStreak, int loserCurStreak, int lostAgainst, int rating, boolean aced ) {
        try {
            String query;
            if (aced) {
                query = "UPDATE tblDuel__2team SET fnLosses = fnLosses + 1, fnLossStreak = " + loserStreak + 
                ", fnCurrentLossStreak = " + loserCurStreak + 
                ", fnCurrentWinStreak = 0, fnAced = fnAced + 1, fnLastLoss = " + lostAgainst + ", fnRating = " + rating + 
                ", fdLastPlayed = NOW()" +
                " WHERE fnSeason = " + s_season + " AND fnStatus = 1 AND fnTeamID = " + teamID + " AND fnLeagueTypeID = " + division;
            } else {
                query = "UPDATE tblDuel__2team SET fnLosses = fnLosses + 1, fnLossStreak = " + loserStreak + 
                ", fnCurrentLossStreak = " + loserCurStreak + ", fnCurrentWinStreak = 0, fnLastLoss = " + lostAgainst + ", fnRating = " + rating +
                ", fdLastPlayed = NOW()" +
                " WHERE fnSeason = " + s_season + " AND fnStatus = 1 AND fnTeamID = " + teamID + " AND fnLeagueTypeID = " + division;
            }
            m_botAction.SQLQueryAndClose( mySQLHost, query );
        } catch (Exception e) {
            Tools.printStackTrace( "Failed to store team loss", e );
        }
    }
    
    public ResultSet sql_getTeamInfo(int id) {
        try {
            ResultSet result = m_botAction.SQLQuery(mySQLHost, "SELECT * FROM tblDuel__2team WHERE fnTeamID = " + id + " AND fnStatus = 1 AND fnSeason = " + s_season + " LIMIT 1");
            if (result.next())
                return result;
            else
                return null;
        } catch (Exception e) {
            Tools.printStackTrace("Failed to get team information", e);
            return null;
        }
    }
    
    public ResultSet sql_getPlayerInfo(int id) {
        try {
            String query = "SELECT fcIP, fnMID, fnEnabled FROM tblDuel__2player WHERE fnUserID = " + id;
            ResultSet result = m_botAction.SQLQuery(mySQLHost, query);
            return result;
        } catch (Exception e) {
            Tools.printStackTrace("Problem getting user IP/MID", e);
        }
        return null;
    }
    
    public ResultSet sql_getPlayerInfo(String name) {
        try {
            String query = "SELECT fcIP, fnMID, fnEnabled FROM tblDuel__2player WHERE fnUserID = (SELECT fnUserID FROM tblUser WHERE fcUserName = '" + Tools.addSlashesToString(name) + "' ORDER BY fnUserID ASC LIMIT 1)";
            ResultSet result = m_botAction.SQLQuery(mySQLHost, query);
            return result;
        } catch (Exception e) {
            Tools.printStackTrace("Problem getting user IP/MID", e);
        }
        return null;
    }

    public void sql_lagInfo(String name, int average) {
        if (newbies.containsKey(name))
            return;
        int id = -1;
        DuelPlayer dp = players.get(name); 
        if (dp != null)
            id = dp.getID();
        else
            id = sql_getUserID(name);
        
        try {
            m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuel__2player SET fnLag = (fnLag * fnLagCheckCount + " + average + ") / (fnLagCheckCount + 1), fnLagCheckCount = fnLagCheckCount + 1 WHERE fnUserID = " + id);
            if (dp != null) {
                ResultSet rs = m_botAction.SQLQuery(mySQLHost, "SELECT fnLag FROM tblDuel__2player WHERE fnUserID = " + id);
                if (rs.next()) {
                    dp.setLag(rs.getInt("fnLag"));
                    players.put(name, dp);
                }
                m_botAction.SQLClose(rs);
            }
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public boolean sql_gameLimitReached(int a, int b, int division) {
        try {
            String query = "SELECT COUNT(fnMatchID) AS count FROM `tblDuel__2match` WHERE" + " ((fnWinnerTeamID = '" + a + "' AND fnLoserTeamID = '" + b + "')" + " OR (fnWinnerTeamID = '" + b + "' AND fnLoserTeamID = '" + a + "'))" + " AND TO_DAYS(NOW()) - TO_DAYS(ftUpdated ) <= " + s_duelDays + " AND fnLeagueTypeID = " + division;
            ResultSet result = m_botAction.SQLQuery(mySQLHost, query);
            if (result.next()) {
                if (result.getInt("count") >= s_duelLimit) {
                    m_botAction.SQLClose(result);
                    return true;
                }
            }
            m_botAction.SQLClose(result);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Internal Classes
     */
    class GameStartTimer extends TimerTask {

        BotAction m_botAction;
        LockSmith locks;
        String[] challenger;
        String[] challenged;
        int[] a1;
        int[] a2;
        int[] b1;
        int[] b2;
        Duel duel;

        public GameStartTimer(Duel d, String[] t1, String[] t2, BotAction b, LockSmith ls) {
            m_botAction = b;
            duel = d;
            locks = ls;
            challenger = t1;
            challenged = t2;
            a1 = duel.getA1();
            a2 = duel.getA2();
            b1 = duel.getB1();
            b2 = duel.getB2();
        }
        
        public void run() {
            duel.setLockOn();
            if (!laggers.containsKey(challenger[0])) {
                m_botAction.warpTo(challenger[0], a1[0], a1[1]);
                m_botAction.sendPrivateMessage(challenger[0], "GO GO GO!!!", 104);
            }
            if (!laggers.containsKey(challenger[1])) {
                m_botAction.warpTo(challenger[1], a2[0], a2[1]);
                m_botAction.sendPrivateMessage(challenger[1], "GO GO GO!!!", 104);                
            }
            if (!laggers.containsKey(challenged[0])) {
                m_botAction.warpTo(challenged[0], b1[0], b1[1]);
                m_botAction.sendPrivateMessage(challenged[0], "GO GO GO!!!", 104);
            }
            if (!laggers.containsKey(challenged[1])) {
                m_botAction.warpTo(challenged[1], b2[0], b2[1]);
                m_botAction.sendPrivateMessage(challenged[1], "GO GO GO!!!", 104);
            }
            duel.started();
            duel.getPlayerOne().setSpawn((int) (System.currentTimeMillis()/1000));
            duel.getPlayerTwo().setSpawn((int) (System.currentTimeMillis()/1000));
            duel.getPlayerThree().setSpawn((int) (System.currentTimeMillis()/1000));
            duel.getPlayerFour().setSpawn((int) (System.currentTimeMillis()/1000));
            duel.getPlayerOne().setLastDeath();
            duel.getPlayerTwo().setLastDeath();
            duel.getPlayerThree().setLastDeath();
            duel.getPlayerFour().setLastDeath();
            locks.lock();
        }
    }
       
    class TeamInvite extends TimerTask{
        String inviter;
        String invitee;
        int division;
        
        public TeamInvite(String a, String b, int type) {
            inviter = a;
            invitee = b;
            division = type;
            m_botAction.sendPrivateMessage(invitee, "You have been invited to " + inviter + "'s team in " + getDivision(division) + " Division. To accept, respond with !join " + inviter + " otherwise this invite will expire in 1 minute.");
            m_botAction.sendPrivateMessage(inviter, "A team invite has been sent to " + invitee + " for " + getDivision(division) + " Division and will expire in 1 minute.");
        }

        @Override
        public void run() {
            m_botAction.sendPrivateMessage(inviter, "Your team invitation to " + invitee + " has now expired.");
            m_botAction.sendPrivateMessage(invitee, "The team invitation from " + inviter + " has now expired.");
            invites.remove(this);
        }
        
        public String getInviter() {
            return inviter;
        }
        
        public String getInvitee() {
            return invitee;
        }
        
        public int getDivisionID() {
            return division;
        }
    }
    
    class TeamChallenge {
        private int challengers;
        private int challenged;
        private int division;
        private int boxType;
        private String challenger;
        private String partner;
        private boolean accepted = false;
        private int issueTime;
        
        public TeamChallenge(int team1, int team2, int div, int box, String name, String partner) {
            challengers = team1;
            challenged = team2;
            division = div;
            boxType = box;
            challenger = name;
            this.partner = partner;
            issueTime = ((int) System.currentTimeMillis() / 1000);
        }
        
        public boolean getAccepted() {
            return accepted;
        }
        
        public void accept() {
            accepted = true;
        }
        
        public int getChallengers() {
            return challengers;
        }
        
        public int getChallenged() {
            return challenged;
        }
        
        public String getChallenger() {
            return challenger;
        }
        
        public String getPartner() {
            return partner;
        }
        
        public int getDivision() {
            return division;
        }
        
        public int getBoxType() {
            return boxType;
        }

        public int getElapsedTime() {
            return ((int) System.currentTimeMillis() / 1000) - issueTime;
        }
    }

    class SpawnDelay extends TimerTask {
        String player;
        long timeStamp;
        int xCoord;
        int yCoord;

        public SpawnDelay(String name, int x, int y, long time) {
            player = name;
            timeStamp = time;
            xCoord = x;
            yCoord = y;
        }

        public void run() {
            if (spawnDelays.containsKey(player))
                spawnDelays.remove(player);
            m_botAction.warpTo(player, xCoord, yCoord);
            m_botAction.shipReset(player);
            playing.get(player).getPlayer(player).setSpawn((int) (System.currentTimeMillis()/1000));
        }
    }

    class Lagger extends TimerTask {
        String lagger;
        int laggerFreq;
        int otherFreq;
        Duel duel;

        public Lagger(String name, Duel d) {
            lagger = name;
            duel = d;
        }

        public void run() {
            int team = duel.getPlayerNumber(lagger);
            DuelPlayerStats player = duel.getPlayer(lagger);
            int laggerFreq = player.getFreq();
            int otherFreq;
            if (team == 1)
                otherFreq = duel.getChallengedTeam();
            else
                otherFreq = duel.getChallengerTeam();
            m_botAction.sendPrivateMessage(lagger, "You have forfeited since you have been lagged out for over a minute.");
            m_botAction.sendOpposingTeamMessageByFrequency(laggerFreq, lagger + " has been lagged out for over a minute and forfeits.", 26);
            m_botAction.sendOpposingTeamMessageByFrequency(otherFreq, lagger + " has been lagged out for over a minute and forfeits.", 26); 
            player.setDeaths(duel.toWin());
            player.setOut();
            playing.remove(lagger);
            m_botAction.spec(lagger);
            m_botAction.spec(lagger);
            m_botAction.setFreq(lagger, laggerFreq);
            do_score(duel, 4);
            if (laggers.containsKey(lagger)) {
                m_botAction.cancelTask(laggers.remove(lagger));
            }
        }
    }
    
    class KillDelay extends TimerTask {
        String name;
        String killer;
        String[] names;
        String[] killers;
        DuelPlayerStats nameStats;
        DuelPlayerStats killerStats;
        int nameFreq;
        int killerFreq;
        Duel duel;
        public KillDelay(String n, String k, DuelPlayerStats nstat, DuelPlayerStats kstat, String[] ns, String[] ks, Duel d) {
            name = n;
            killer = k;
            duel = d;
            nameStats = nstat;
            killerStats = kstat;
            names = ns;
            killers = ks;
            nameFreq = nameStats.getFreq();
            killerFreq = killerStats.getFreq();
        }

        public void run() {
            // setScoreboard(duel, 0);

            // is killed player out?
            if (nameStats.getDeaths() >= duel.toWin()) {
                m_botAction.sendOpposingTeamMessageByFrequency(killerFreq, name + " is out with " + nameStats.getKills() + ":" + nameStats.getDeaths(), 26);
                m_botAction.sendOpposingTeamMessageByFrequency(nameFreq, name + " is out with " + nameStats.getKills() + ":" + nameStats.getDeaths(), 26);                
                nameStats.setOut();
                playing.remove(name);
                m_botAction.spec(name);
                m_botAction.spec(name);
                if (duel.getPlayerNumber(name) == 1)
                    m_botAction.setFreq(name, duel.getBoxFreq());
                else
                    m_botAction.setFreq(name, duel.getBoxFreq() + 1);
            }
            
            if (spawnDelays.containsKey(name)) {
                m_botAction.cancelTask(spawnDelays.remove(name));
            }
            
            do_score(duel, 0);
        }
    }
}

class NotPlaying {
    int time = 0;
    int period = 0;

    public NotPlaying(int t, int p) {
        time = t;
        period = p;
    }

    public boolean timeUp(int t) {
        if ((t - time) / 60 > period)
            return true;
        else
            return false;
    }
}
