package twcore.bots.hockeybot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Stack;
import java.util.TimerTask;
import java.util.TreeMap;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.BallPosition;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.SoccerGoal;
import twcore.core.game.Player;
import twcore.core.util.MapRegions;
import twcore.core.util.Point;
import twcore.core.util.Spy;
import twcore.core.util.Tools;

/**
 * Class HockeyBot
 *
 * <p>Used for hosting ?go hockey</p>
 *
 * @author fantus, spookedone
 */
public class hockeybot extends SubspaceBot {
    // Todo-list of features that need to be added in the future.
    //TODO Add and display time tracking.
    //TODO Add option to do a timed match.
    //TODO Add count down during setup time.
    //TODO Add vote on last goal in none-timed games.
    //TODO Add shoot-outs on tied timed game.
    //TODO Add tracking of time played and ship time played.
    //TODO Add Persistent stat tracking through SQL.
    //TODO Add custom team names.
    //TODO Add multi-layered penalty system. (I.e. short time for minor penalties up to removal from game on huge penalties.)
    //TODO (Non-bot related) Custom arena graphics.

    private boolean lockArena;
    private boolean lockLastGame;
    private boolean isPenalty = false;
    private boolean reviewing = false;
    
    // Various lists
    private HockeyConfig config;                            //Game configuration
    private MapRegions hockeyZones;                         //Map regions
    private HockeyTeam team0;                               //Teams
    private HockeyTeam team1;
    private ArrayList<HockeyTeam> teams;                    //Simple arraylist to optimize some routines.
    //private Vote staffVote;                               //staff vote for clean or phase
    private HockeyPuck puck;                                //the ball in arena
    private Spy racismWatcher;                              //Racism watcher
    private ArrayList<String> listNotplaying;               //List of notplaying players
    private ArrayList<String> listAlert;                    //List of players who toggled !subscribe on
    private Stack<String> botCrease;                        //Crease tracking for faceoff.
    private TreeMap<String,HockeyVote> listVotes;           //Voting list for the review on the final goal.
    
    //Game tickers & other time related stuff
    private Gameticker gameticker;
    private TimerTask fo_botUpdateTimer;
    private TimerTask ballDelay;
    private TimerTask statsDelay;
    private TimerTask mvpDelay;
    private TimerTask reviewDelay;
    private long timeStamp;
    private long roundTime;
    private long gameTime;
    
    // Zoner related stuff
    private long zonerTimestamp;                            //Timestamp of the last zoner
    private long manualZonerTimestamp;                      //Timestamp of the last manualzoner
    private int maxTimeouts;                                //Maximum allowed timeouts per game.
    //Frequencies
    private static final int FREQ_SPEC = 8025;              //Frequency of specced players.
    private static final int FREQ_NOTPLAYING = 2;           //Frequency of players that are !np
    //Static variables
    private static final int ZONER_WAIT_TIME = 7;
    

    //Game states
    private static enum HockeyState {
        OFF, WAITING_FOR_CAPS, ADDING_PLAYERS, FACE_OFF,
        GAME_IN_PROGRESS, REVIEW, TIMEOUT, GAME_OVER, 
        WAIT;
        
        // Collection of commonly together used HockeyStates.
        private static final EnumSet<HockeyState> MIDGAME = EnumSet.of(FACE_OFF, GAME_IN_PROGRESS, TIMEOUT, WAIT);
        private static final EnumSet<HockeyState> ACTIVEGAME = EnumSet.of(ADDING_PLAYERS,
                FACE_OFF, GAME_IN_PROGRESS, TIMEOUT, WAIT);
        
    };    

    private HockeyState currentState;
    
    private int carriersSize;
    // private String staffVoter;

    // Hockey penalties.
    private static enum HockeyPenalty {
        NONE, OFFSIDE, D_CREASE, FO_CREASE, 
        ILLEGAL_CHK_WARN, ILLEGAL_CHECK, 
        GOALIE_KILL, OTHER
    };
    
    
    // Game modes.
    private static enum GameMode {
        GOALS, TIMED
    };

    // Votes for review of final goal.
    private static enum HockeyVote {
        NONE, ABSTAIN, CLEAN, CREASE, PHASE;
    };

    /**
     * The defined zones in the hockey arena.
     * <p>
     * Due to the methods that setup and determine the zones, overlapping is not allowed.
     * To counter this, each defined zone will get its own enum value, and overlapping zones
     * will be grouped together in EnumSets.
     * 
     * @author Trancid
     * @see EnumSet
     */
    private static enum HockeyZone {
        /*
         * Explanation of names:
         * CREASEX:     Crease zone (including goal) of freq X.
         * DZX:         Defensive zone of freq X.
         * AREAX:       The half/side that belongs to freq X.
         * FOX:         The semi-circle in the face off zone on the side of freq X, excluding the safe area and dropzone.
         * FO_SAFEX:    The safezone in the face off zone on the side of freq X.
         * FO_DROPX:    The inner drop zone in the face off area on the side of freq X.
         * PENX:        Penalty box of freq X.
         */
        CREASE0, DZ0, AREA0, FO0, FO_SAFE0, FO_DROP0, PEN0,
        CREASE1, DZ1, AREA1, FO1, FO_SAFE1, FO_DROP1, PEN1;
        
        //Overlapping zones for freq 0
        //Defensive zone (everything left of the left blue line, including the blue line)
        private static final EnumSet<HockeyZone> DZONE0 = EnumSet.of(CREASE0, DZ0, PEN0);
        //The entire side that belongs to freq 0.
        private static final EnumSet<HockeyZone> SIDE0 = EnumSet.of(CREASE0, DZ0, AREA0, FO0, FO_SAFE0, FO_DROP0, PEN0);
        //The entire face off area that is on the side of freq 0.
        private static final EnumSet<HockeyZone> FO_AREA0 = EnumSet.of(FO0, FO_SAFE0, FO_DROP0);
        
        //Overlapping zones for freq 1
        //Defensive zone (everything right of the right blue line, including the blue line)
        private static final EnumSet<HockeyZone> DZONE1 = EnumSet.of(CREASE1, DZ1, PEN1);
        //The entire side that belongs to freq 1.
        private static final EnumSet<HockeyZone> SIDE1 = EnumSet.of(CREASE1, DZ1, AREA1, FO1, FO_SAFE1, FO_DROP1, PEN1);
        //The entire face off area that is on the side of freq 1.
        private static final EnumSet<HockeyZone> FO_AREA1 = EnumSet.of(FO1, FO_SAFE1, FO_DROP1);
        
        //The entire face off area, ignoring sides.
        private static final EnumSet<HockeyZone> FO_AREA = EnumSet.of(FO0, FO_SAFE0, FO_DROP0, FO1, FO_SAFE1, FO_DROP1);
        //The puck drop area, ignoring sides.
        private static final EnumSet<HockeyZone> FO_DROP = EnumSet.of(FO_DROP0, FO_DROP1);
        
        /**
         * Method to convert an index/int to a specific enum.
         * 
         * @param i Index number of the HockeyZone you want to convert to.
         * @return HockeyZone if a match is found, otherwise null.
         */
        private static HockeyZone intToEnum(int i) {
            for(HockeyZone hz : HockeyZone.values()) {
                if(i == hz.ordinal())
                    return hz;
            }
            return null;
        }
    };

    /** Class constructor */
    public hockeybot(BotAction botAction) {
        super(botAction);
        initializeVariables();  //Initialize variables
        requestEvents();        //Request Subspace Events
    }

    /** Initializes all the variables used in this class */
    private void initializeVariables() {
        config = new HockeyConfig();                    //Game configuration
        currentState = HockeyState.OFF;                 //Game state
        
        puck = new HockeyPuck();
        team0 = new HockeyTeam(0);                      //Team: Freq 0
        team1 = new HockeyTeam(1);                      //Team: Freq 1
        teams = new ArrayList<HockeyTeam>();            // List containing the teams. Mainly used to optimize code.
        teams.add(team0);
        teams.add(team1);
        teams.trimToSize();
        
        maxTimeouts = 1;                                // Default value of maximum timeouts.

        racismWatcher = new Spy(m_botAction);           //Racism watcher

        listNotplaying = new ArrayList<String>();       // List of not-playing players,
        listNotplaying.add(m_botAction.getBotName());   // including the bot.
        listAlert = new ArrayList<String>();            // List of the players who want to get alerts.
        
        listVotes = new TreeMap<String, HockeyVote>();  // Setup a new voting list for staff votes on the final goal review.

        lockArena = true;
        lockLastGame = false;

        botCrease = new Stack<String>();                // Face off crease zone list.

    }

    /** Requests Subspace events */
    private void requestEvents() {
        EventRequester req = m_botAction.getEventRequester();

        req.request(EventRequester.ARENA_JOINED);           //Bot joined arena
        req.request(EventRequester.FREQUENCY_SHIP_CHANGE);  //Player changed frequency/ship
        req.request(EventRequester.LOGGED_ON);              //Bot logged on
        req.request(EventRequester.MESSAGE);                //Bot received message
        req.request(EventRequester.PLAYER_ENTERED);         //Player entered arena
        req.request(EventRequester.PLAYER_LEFT);            //Player left arena
        req.request(EventRequester.PLAYER_POSITION);        //Player position
        req.request(EventRequester.PLAYER_DEATH);           //Player died
        req.request(EventRequester.BALL_POSITION);          //Watch ball position
        req.request(EventRequester.SOCCER_GOAL);            //A goal has been made
    }

    /*
     * Events
     */
    /**
     * Handles ArenaJoined event
     * - Sets up reliable kills
     * - Sets up chats
     * - Auto-starts bot
     */
    @Override
    public void handleEvent(ArenaJoined event) {
        //m_botAction.setReliableKills(1);
        start();
        m_botAction.setPlayerPositionUpdating(300);
        m_botAction.sendUnfilteredPublicMessage("?chat=" + config.getChats());  //Join all the chats
    }

    /**
     * Handles FrequencyShipChange event
     * - since this event looks almost the same as FrequencyChange
     *   event its passed on to checkFCandFSC(name, frequency, ship).
     */
    @Override
    public void handleEvent(FrequencyShipChange event) {
        if (currentState != HockeyState.OFF && currentState != HockeyState.WAITING_FOR_CAPS) {
            Player p;

            p = m_botAction.getPlayer(event.getPlayerID());
            
            HockeyTeam team = null;
            team = getTeam(p.getPlayerName());
            
            if (team == null){
                return;
            } else {
            if (p != null && !p.getPlayerName().equals(m_botAction.getBotName()))
                checkFCandFSC(p.getPlayerName(), p.getFrequency(), p.getShipType());
            }
        }
    }

    /**
     * Handles LoggedOn event
     * - Join arena
     * - Set antispam measurements
     */
    @Override
    public void handleEvent(LoggedOn event) {
        short resolution;   //Screen resolution of the bot

        resolution = 3392;  //Set the maximum allowed resolution

        /* Join Arena */
        try {
            m_botAction.joinArena(config.getArena(), resolution, resolution);
        } catch (Exception e) {
            m_botAction.joinArena(config.getArena());
        }

        m_botAction.setMessageLimit(10);    //Set antispam measurements
    }

    /**
     * Handles Message event
     * - Racism watcher
     * - Arena lock
     * - Player commands
     */
    @Override
    public void handleEvent(Message event) {
        String message;     //Message
        String sender;      //Sender of the message
        int messageType;    //Message type

        message = event.getMessage();
        sender = m_botAction.getPlayerName(event.getPlayerID());
        messageType = event.getMessageType();
        
        // Although the sender check is done in the racismWatcher,
        // doing it here as well saves a few executions.
        if(sender != null) {
            racismWatcher.handleEvent(event);   //Racism watcher
        }
        
        if (messageType == Message.ARENA_MESSAGE) {
            checkArenaLock(message);    //Checks if the arena should be locked
        } else if (messageType == Message.PRIVATE_MESSAGE) {
            if (sender != null) {
                handleCommand(sender, message, -1);   //Handle commands
            }
        }
    }

    /**
     * Handles PlayerEntered event
     * - Sends welcome message
     * - Puts the player on the corresponding frequency
     */
    @Override
    public void handleEvent(PlayerEntered event) {
        if (currentState != HockeyState.OFF) {
            String name;    //Name of the player that entered the zone
            int pID;        //ID of the player that entered the arena
            
            name = m_botAction.getPlayerName(event.getPlayerID());
            
            Player p;
            p = m_botAction.getPlayer(event.getPlayerID());
            
            if (p != null) {
                pID = p.getPlayerID();
                newPlayerUpdateScoreBoard(pID);
            }
            
            if (name != null) {
                sendWelcomeMessage(name);   //Sends welcome message with status info to the player
                putOnFreq(name);            //Puts the player on the corresponding frequency
                
            }
        }
    }

    /**
     * Handles PlayerLeft event
     * - Checks if the player that left was a captain
     * - Checks if the player that left lagged out
     */
    @Override
    public void handleEvent(PlayerLeft event) {
        if (currentState != HockeyState.OFF && currentState != HockeyState.WAITING_FOR_CAPS) {
            String name;    //Name of the player that left

            name = m_botAction.getPlayerName(event.getPlayerID());
            HockeyTeam team = null;
            
            team = getTeam(name);
            
            if (team == null){
                return;
            }
            if (name != null) {
                //Check if the player that left was a captain
                checkCaptainLeft(name);
                //Check if the player that left was IN the game
                checkLagout(name, Tools.Ship.SPECTATOR);
            }
        }
    }

    /**
     * Handles PlayerPosition event
     * - Warps players back to their safes during PRE_GAME
     * - Timestamps last received position for out of border time
     */
    @Override
    public void handleEvent(PlayerPosition event) {
        HockeyZone currentZone;
        int playerID = event.getPlayerID();
        String name = m_botAction.getPlayerName(playerID);

        HockeyTeam team = null;
        if (name == null || name == m_botAction.getBotName())   // Can do exactly squat if we can't get the name
            return;
        
        Player p = m_botAction.getPlayer(name);
        team = getTeam(name);

        /* Null pointer exception check */
        if (team != null && !team.laggedOut(name)) {
            int freq = team.getFrequency();
            currentZone = getZone(p);

            switch (currentState) {
                case FACE_OFF:
                    checkPenalty(event);
                    
                    if(freq == 0) {
                        // Offside checks
                        if(team.offside.contains(name)) {
                            // Name is on the list, check if the player moved out.
                            if(HockeyZone.SIDE0.contains(currentZone))
                                team.offside.remove(name);
                        } else {
                            // Name isn't on the list, check if the player did a bad thing.
                            if(HockeyZone.SIDE1.contains(currentZone))
                                team.offside.push(name);
                        }
                    } else if (freq == 1) {
                        // Offside checks
                        if(team.offside.contains(name)) {
                            // Name is on the list, check if the player moved out.
                            if(HockeyZone.SIDE1.contains(currentZone))
                                team.offside.remove(name);
                        } else {
                            // Name isn't on the list, check if the player did a bad thing.
                            if(HockeyZone.SIDE0.contains(currentZone))
                                team.offside.push(name);
                        }
                    }
                        
                    // Bot crease checks
                    if(!botCrease.contains(name)) {
                        if(HockeyZone.FO_DROP.contains(currentZone))
                            botCrease.push(name);
                    } else {
                        if(!HockeyZone.FO_DROP.contains(currentZone))
                            botCrease.remove(name);
                    }
                    
                    // Faceoff crease checks
                    if(!team.fCrease.contains(name)) {
                        if(HockeyZone.FO_AREA.contains(currentZone))
                            team.fCrease.push(name);
                    } else {
                        if(!HockeyZone.FO_AREA.contains(currentZone))
                            team.fCrease.remove(name);
                    }

                    break;
                case GAME_IN_PROGRESS:
                    checkPenalty(event);
                    // Check defense crease.
                    HockeyZone tempZone = null;
                    
                    if(freq == 0) {
                        tempZone = HockeyZone.CREASE0;
                    } else if(freq == 1) {
                        tempZone = HockeyZone.CREASE1;
                    }
                    
                    if(!team.dCrease.contains(name)) {
                        if(tempZone == currentZone)
                            team.dCrease.push(name);
                    } else {
                        if(tempZone != currentZone)
                            team.dCrease.remove(name);
                    }

                    // Check offside for goalie.
                    if(p != null && team.isGoalie(name)) {
                        if(freq == 0) {
                            if(!team.offside.contains(name) && !HockeyZone.DZONE0.contains(currentZone)) {
                                team.offside.push(name);
                                m_botAction.sendPrivateMessage(name, "WARNING: You're offside and not allowed to touch the puck. " 
                                        + "Return left (<-) of blue line or you may recieve a penalty.");
                            } else if(team.offside.contains(name) && HockeyZone.DZONE0.contains(currentZone)) {
                                team.offside.remove(name);
                            }
                        } else if(freq == 1) {
                            if(!team.offside.contains(name) && !HockeyZone.DZONE1.contains(currentZone)) {
                                team.offside.push(name);
                                m_botAction.sendPrivateMessage(name, "WARNING: You're offside and not allowed to touch the puck. " 
                                        + " Return right (->) of blue line or you may recieve a penalty.");
                            } else if(team.offside.contains(name) && HockeyZone.DZONE1.contains(currentZone)) {
                                team.offside.remove(name);
                            }
                        }
                    }
                    break;
            }
        }
    }

    /**
     * Handles PlayerDeath event
     */
    public void handleEvent(PlayerDeath event) {
        int idKillee, idKiller;
        HockeyPlayer pKillee, pKiller;
        HockeyTeam tKillee, tKiller;
        String killee, killer;
        HockeyPenalty bodyCheckState;
        
        //Get the IDs of the killer and killee from the packet.
        idKillee = event.getKilleeID();
        idKiller = event.getKillerID();
        
        //Lookup their names.
        killee = m_botAction.getPlayerName(idKillee);
        killer = m_botAction.getPlayerName(idKiller);
        
        //Check if we actually got their names.
        if(killee == null || killer == null)
            return;
        
        //Get the teams
        tKillee = getTeam(killee);
        tKiller = getTeam(killer);
        
        //Again, check if the fetch succeeded.
        if(tKillee == null || tKiller == null)
            return;
        
        //Get their player object
        pKillee = tKillee.getPlayer(killee);
        pKiller = tKiller.getPlayer(killer);
        
        //Final null checks
        if(pKillee == null || pKiller == null)
            return;
        
        // Check for the goalie being killed in the crease zone.
        if(tKillee.isGoalie(killee) && HockeyState.MIDGAME.contains(currentState)
                && ((tKillee.getFrequency() == 0 && getZone(m_botAction.getPlayer(killee)) == HockeyZone.CREASE0)
                || (tKillee.getFrequency() == 1 && getZone(m_botAction.getPlayer(killee)) == HockeyZone.CREASE1))) {
            // Goalie was killed in his own crease zone
            pKiller.setPenalty(HockeyPenalty.GOALIE_KILL);
            m_botAction.sendArenaMessage("GOALIE KILL PENALTY: " + killer);
            return;
        }

        // Check if respawnkilling has occured.
        bodyCheckState = pKillee.trackDeaths(killer);
        
        if(bodyCheckState == HockeyPenalty.ILLEGAL_CHK_WARN) {
            // Issue a warning to the player.
            m_botAction.sendSmartPrivateMessage(killer, "You've received a warning for illegal checking! (Respawnkilling)");
        } else if(bodyCheckState == HockeyPenalty.ILLEGAL_CHECK) {
            if(currentState == HockeyState.ADDING_PLAYERS) {
                // Pregame. Penalty: remove the player from the game.
                m_botAction.sendArenaMessage("ILLEGAL CHECK PENALTY: " + killer + " has been removed from the game. (Respawnkilling)");
                tKiller.removePlayer(killer);
                determineTurn();
            } else {
                // Midgame. Give the player a penalty.
                pKiller.setPenalty(HockeyPenalty.ILLEGAL_CHECK);
                m_botAction.sendArenaMessage("ILLEGAL CHECK PENALTY: " + killer + ". (Respawnkilling)");
            }
        }
    }
    
    @Override
    public void handleEvent(BallPosition event) {
        puck.update(event);
    }

    @Override
    public void handleEvent(SoccerGoal event) {
        if (currentState == HockeyState.GAME_IN_PROGRESS) {
            startReview(event);
        }
    }

    /**
     * Handles a disconnect
     * - cancel all tasks
     */
    @Override
    public void handleDisconnect() {
        m_botAction.cancelTasks();

    }

    /**
     * Removes the ball from play
     */
    public void doRemoveBall() {
        doGetBall(config.getPuckToX(), config.getPuckToY());
        
        ballDelay = new TimerTask() {
            @Override
            public void run() {
                dropBall();
            }
        }; m_botAction.scheduleTask(ballDelay, 2 * Tools.TimeInMillis.SECOND);
    }
    
    /**
     * Causes the bot to grab the ball and goes to a specific location
     */
    public void doGetBall(int xLoc, int yLoc) {
        fo_botUpdateTimer = new TimerTask() {
            @Override
            public void run() {
                if (m_botAction.getShip().needsToBeSent())
                    m_botAction.getShip().sendPositionPacket();
            }
        }; m_botAction.scheduleTask(fo_botUpdateTimer, 0, Tools.TimeInMillis.SECOND);
        
        if (m_botAction.getShip().getShip() != 0 || !puck.holding) {
            m_botAction.getShip().setShip(0);
            m_botAction.getShip().setFreq(FREQ_NOTPLAYING);
            m_botAction.getShip().move(xLoc, yLoc);
            m_botAction.getBall(puck.getBallID(), puck.getTimeStamp());
            puck.holding = true;
        }
    }

    /**
     * Drops the ball at current location
     */
    public void dropBall() {
        m_botAction.cancelTask(fo_botUpdateTimer);
        m_botAction.getShip().setShip(8);
        m_botAction.getShip().setFreq(FREQ_NOTPLAYING);
        puck.holding = false;
    }

    private void checkPenalty(PlayerPosition event) {

        int playerID = event.getPlayerID();
        String name = m_botAction.getPlayerName(playerID);
        HockeyTeam team = null;
        if (name != null) {
            team = getTeam(name);
        }

        if (team != null) {
            HockeyPlayer player = team.searchPlayer(name);
            // check penalty
            if (player != null) {
                if (player.penalty != HockeyPenalty.NONE) {

                    if (team.getFrequency() == 0 && event.getYLocation() > config.getTeam0ExtY()) {
                        m_botAction.warpTo(name, config.getTeam0PenX() / 16, config.getTeam0PenY() / 16);
                    } else if (event.getYLocation() > config.getTeam1ExtY()) {
                        m_botAction.warpTo(name, config.getTeam1PenX() / 16, config.getTeam1PenY() / 16);
                    }
                    checkPenaltyExipred(name, team);
                }
            }
        }

    }

    private void checkPenaltyExipred(String name, HockeyTeam team) {
        HockeyPlayer player = team.searchPlayer(name);

        if ((gameTime - player.penaltyTimestamp) >= config.getPenaltyTime()) {
            player.penalty = HockeyPenalty.NONE;
            if (team.getFrequency() == 0) {
                m_botAction.warpTo(name, config.getTeam0ExtX() / 16, config.getTeam0ExtY() / 16);
            } else {
                m_botAction.warpTo(name, config.getTeam1ExtX() / 16, config.getTeam1ExtY() / 16);
            }
        }
    }

    /*
     * Commands
     */
    /**
     * Handles player commands
     *
     * @param name Sender of the command
     * @param command command
     * @param override Override number, -1 for default, 0 for Freq 0, 1 for Freq 1
     */
    private void handleCommand(String name, String command, int override) {
        String cmd = command.toLowerCase();
        String args = "";

        //Separate the command from its arguments if applicable.
        if(command.contains(" ")) {
            int index = command.indexOf(" ");
            cmd = cmd.substring(0, index);
            if(command.length() > ++index)
                args = command.substring(index).trim();
        }

        /* Captain commands */
        if (isCaptain(name) || override != -1) {
            if (cmd.equals("!change")) {
                cmd_change(name, args, override);
            } else if (cmd.equals("!switch")) {
                cmd_switch(name, args, override);
            } else if (cmd.equals("!add")) {
                cmd_add(name, args, override);
            } else if (cmd.equals("!ready")) {
                cmd_ready(name, override);
            } else if (cmd.equals("!remove")) {
                cmd_remove(name, args, override);
            } else if (cmd.equals("!sub")) {
                cmd_sub(name, args, override);
            } else if (cmd.equals("!timeout") && (maxTimeouts > 0)) {
                cmd_timeout(name);
            }
        }

        /* Player commands */
        if (cmd.equals("!cap")) {
            cmd_cap(name);
        } else if (cmd.startsWith("!help")) {
            cmd_help(name, args);
        } else if (cmd.equals("!return")) {
            cmd_lagout(name);
        } else if (cmd.equals("!lagout")) {
            cmd_lagout(name);
        } else if (cmd.equals("!list")) {
            cmd_list(name);
        } else if (cmd.equals("!notplaying") || cmd.equals("!np")) {
            cmd_notplaying(name);
        } else if (cmd.equals("!status")) {
            cmd_status(name);
        } else if (cmd.equals("!subscribe")) {
            cmd_subscribe(name);
        }


        /* Staff commands ZH+ */
        if (m_botAction.getOperatorList().isZH(name)) {
            if (cmd.equals("!start")) {
                cmd_start(name);
            } else if (cmd.equals("!stop")) {
                cmd_stop(name);
            } else if (cmd.equals("!zone")) {
                cmd_zone(name, args);
            } else if (cmd.equals("!off")) {
                cmd_off(name);
            } else if (cmd.equals("!forcenp")) {
                cmd_forcenp(name, args);
            } else if (cmd.equals("!setcaptain") || cmd.equals("!sc")) {
                cmd_setCaptain(name, args, override);
            } else if (cmd.equals("!ball")) {
                cmd_ball(name);
            } else if (cmd.equals("!drop")) {
                cmd_drop(name);
            } else if (cmd.startsWith("!dec")) {     //!dec & !decrease
                cmd_decrease(name, args);
            } else if (cmd.startsWith("!inc")) {     //!inc & !increase
                cmd_increase(name, args);
            } else if (cmd.startsWith("!pen")) {     //!pen & !penalty
                cmd_penalty(name, args);
            } else if (cmd.equals("!rempenalty") || cmd.equals("!rpen")) {
                cmd_removePenalty(name, args);
            } else if (cmd.equals("!hosttimeout") || cmd.equals("!hto")) {
                cmd_hosttimeout(name);
            } else if (cmd.equals("!vote")) {
                cmd_vote(name, args);
            }
        }
       
        /* Staff commands ER+ */
        if (m_botAction.getOperatorList().isER(name)) {
            if (cmd.equals("!settimeout")) {
                cmd_settimeout(name, args);
            }
        }

        /* Staff commands Moderator+ */
        if (m_botAction.getOperatorList().isModerator(name)) {
            if (cmd.equals("!die")) {
                m_botAction.die();
            }
        }

        /* Staff commands SMOD+ */
        if (m_botAction.getOperatorList().isSmod(name)) {
            if (cmd.equals("!allowzoner")) {
                cmd_allowZoner(name);
            }
        }
    }

    /** 
     * Handles the !add command (cap)
     *
     * @param name Name of the player who issued the command.
     * @param args the player (and ship) to be added
     * @param override Override number, -1 for default, 0 for Freq 0, 1 for Freq 1
     */
    private void cmd_add(String name, String args, int override) {
        int shipType;       //Specified shiptype (in args)
        Player p;           //Specified player (in args)
        String pName;       //Specified player's name in normal case
        String[] splitCmd;  //Args split up
        HockeyTeam t;       //Team

        /* Check if name is a captain or that the command is overriden */
        if (!isCaptain(name) && override == -1) {
            return;
        }

        t = getTeam(name, override);    //Retrieve team

        /* Check if it is the team's turn to pick during ADDING_PLAYERS */
        if (currentState == HockeyState.ADDING_PLAYERS) {
            if (!t.isTurn()) {
                m_botAction.sendPrivateMessage(name, "Error: Not your turn to pick!");
                return;
            }
        }

        /* Check command syntax */
        if (args.isEmpty()) {
            m_botAction.sendPrivateMessage(name, "Error: Please specify atleast a playername, !add <player>");
            return;
        }

        if (HockeyState.ACTIVEGAME.contains(currentState)) {
            splitCmd = args.split(":"); //Split command (<player>:<shiptype>)

            p = m_botAction.getFuzzyPlayer(splitCmd[0]);    //Find <player>

            /* Check if p has been found */
            if (p == null) {
                m_botAction.sendPrivateMessage(name, "Error: " + splitCmd[0] + " could not be found.");
                return;
            }

            pName = p.getPlayerName();

            /* Check if p is a bot */
            if (m_botAction.getOperatorList().isBotExact(pName.toLowerCase())) {
                m_botAction.sendPrivateMessage(name, "Error: Pick again, bots are not allowed to play.");
                return;
            }

            /* Check if the player is set to notplaying */
            if (listNotplaying.contains(pName)) {
                m_botAction.sendPrivateMessage(name, "Error: " + p.getPlayerName() + " is set to notplaying.");
                return;
            }

            /* Check if the maximum amount of players IN is already reached */
            if (t.getSizeIN() >= config.getMaxPlayers()) {
                m_botAction.sendPrivateMessage(name, "Error: Maximum amount of players already reached.");
                return;
            }

            /* Check if the player is already on the team and playing */
            if (t.isIN(pName)) {
                m_botAction.sendPrivateMessage(name, "Error: " + p.getPlayerName()
                        + " is already on your team, check with !list");
                return;
            }

            /* Check if the player was already on the other team */
            if (getOtherTeam(name, override).isOnTeam(pName)) {
                m_botAction.sendPrivateMessage(name, "Error: Player is already on the other team.");
                return;
            }

            /* Check for ship type */
            if (splitCmd.length > 1) {
                try {
                    shipType = Integer.parseInt(splitCmd[1]);
                } catch (Exception e) {
                    shipType = config.getDefaultShipType();
                }

                /* Check if the  ship type is valid */
                if (shipType < Tools.Ship.WARBIRD || shipType > Tools.Ship.SHARK) {
                    shipType = config.getDefaultShipType();
                }
            } else {
                /* Fall back to default shiptype if not specified */
                shipType = config.getDefaultShipType();
            }

            /* Check if the maximum amount of ships of this type is reached */
            if (!t.isShipAllowed(shipType)) {
                m_botAction.sendPrivateMessage(name, "Error: Could not add " + p.getPlayerName() + " as "
                        + Tools.shipName(shipType) + ", team has already reached the maximum number of "
                        + Tools.shipName(shipType) + "s allowed.");
                return;
            }

            /*
             * All checks are done
             */

            /* Add player */
            t.addPlayer(p, shipType);

            /* Toggle turn */
            if (currentState == HockeyState.ADDING_PLAYERS) {
                t.picked();
                determineTurn();
            }
        }
    }

    /**
     * Handles the !allowzoner command (sMod+)
     * 
     * @param name name of the player who issued the command.
     */
    private void cmd_allowZoner(String name) {
        zonerTimestamp = zonerTimestamp - (ZONER_WAIT_TIME * Tools.TimeInMillis.MINUTE);
        manualZonerTimestamp = manualZonerTimestamp - (10 * Tools.TimeInMillis.MINUTE);
        m_botAction.sendPrivateMessage(name, "Zone message timestamps have been reset.");
    }
    
    /**
     * Handles the !ball command (ZH+)
     * 
     * @param name Name of the player who issued the command.
     */
    private void cmd_ball(String name) {
        int xCoord = puck.getBallX();
        int yCoord = puck.getBallY();
        
        m_botAction.sendPrivateMessage(name, "Ball was located at: " + xCoord
                + ", " + yCoord);
        doGetBall(xCoord, yCoord);
    }
    
    /**
     * Handles the !cap command
     *
     * @param name player that issued the !cap command
     */
    private void cmd_cap(String name) {
        HockeyTeam t;

        /* Check if bot is turned on */
        if (currentState == HockeyState.OFF) {
            return;
        }

        /* Check if auto captains is allowed */
        if (!config.getAllowAutoCaps()) {
            sendCaptainList(name);
            return;
        }

        /* Check if sender is on the not playing list */
        if (listNotplaying.contains(name)) {
            sendCaptainList(name);
            return;
        }

        /* Check if captain spots are already taken */
        if (team0.hasCaptain() && team1.hasCaptain()) {
            sendCaptainList(name);
            return;
        }

        /*
         * Check if the sender is already on one of the teams
         * If so he can only get captain of his own team
         */
        t = getTeam(name);
        if (t != null) {
            if (t.hasCaptain()) {
                sendCaptainList(name);
                return;
            } else {
                t.setCaptain(name);
                return;
            }
        }

        /* Check if game state is waiting for caps, or adding players */
        if (currentState == HockeyState.WAITING_FOR_CAPS
                || currentState == HockeyState.ADDING_PLAYERS) {
            if (!team0.hasCaptain()) {
                team0.setCaptain(name);
                return;
            } else if (!team1.hasCaptain()) {
                team1.setCaptain(name);
                return;
            } else {
                sendCaptainList(name);
                return;
            }
        } else {
            sendCaptainList(name);
            return;
        }
    }

    /**
     * Handles the !change command (cap)
     *
     * @param name name of the player that issued the command
     * @param args command parameters
     * @param override teamnumber to override, else -1
     */
    private void cmd_change(String name, String args, int override) {
        HockeyTeam t;
        String[] splitCmd;
        HockeyPlayer p;
        int shipType;

        t = getTeam(name, override);

        /* Check if sender is in a team and that the command was not overriden */
        if (t == null && override == -1) {
            return;
        }

        /* Check if the sender is a captain and that the command was not overriden */
        if (!t.isCaptain(name) && override == -1) {
            return;
        }

        /* Check command syntax */
        if (args.isEmpty()) {
            m_botAction.sendPrivateMessage(name,
                    "Error: Please specify a playername and shiptype, !change <player>:<# shiptype>");
            return;
        }

        splitCmd = args.split(":"); //Split command in 1. <player> 2. <# shiptype>

        /* Check command syntax */
        if (splitCmd.length < 2) {
            m_botAction.sendPrivateMessage(name,
                    "Error: Please specify a playername and shiptype, !change <player>:<# shiptype>");
            return;
        }

        p = t.searchPlayer(m_botAction.getFuzzyPlayerName(splitCmd[0]));    //Search for the player

        /* Check if the player has been found */
        if (p == null) {
            m_botAction.sendPrivateMessage(name, "Error: Unknown player");
            return;
        }

        /* Check if the player is already out */
        if (p.isOut()) {
            m_botAction.sendPrivateMessage(name, "Error: Player is already out and cannot be shipchanged.");
            return;
        }

        /* Check if the shiptype is set correctly */
        try {
            shipType = Integer.parseInt(splitCmd[1]);
        } catch (Exception e) {
            m_botAction.sendPrivateMessage(name, "Error: invalid shiptype");
            return;
        }

        if (shipType < 1 || shipType > 8) {
            m_botAction.sendPrivateMessage(name, "Error: invalid shiptype");
            return;
        }

        /* Check if the specified shiptype is allowed */
        if (!t.isShipAllowed(shipType, p.p_currentShip)) {
            m_botAction.sendPrivateMessage(name, "Error: Could not change " + p.getName() + " to "
                    + Tools.shipName(shipType) + ", team has already reached the maximum number of "
                    + Tools.shipName(shipType) + "s allowed.");
            return;
        }

        /* Check if the player is already on that ship */
        if (p.getCurrentShipType() == shipType) {
            m_botAction.sendPrivateMessage(name, "Error: Could not change " + p.getName() + " to "
                    + Tools.shipName(shipType) + ", player already in that ship.");
            return;
        }

        /* Check when the last !change happened and if this change is allowed */
        if (!p.isChangeAllowed()) {
            m_botAction.sendPrivateMessage(name, "Error: Changed not allowed yet for this player, wait "
                    + p.getTimeUntilNextChange() + " more seconds before next !change");
            return;
        }

        /*
         * All checks done
         */
        p.change(shipType); //Change player

        /* Notify sender of successful change */
        m_botAction.sendPrivateMessage(name, p.getName() + " has been changed to " + Tools.shipName(shipType));
    }

    /**
     * Handles the !decrease command (ZH+)
     * Decreases a frequencies score by 1
     * 
     * @param name player that issue the !decrease command
     * @param args frequency whose score is to be decreased
     */
    private void cmd_decrease(String name, String args) {
        int tempCheck = 0;
        int targetFreq = -1;
        HockeyTeam t;
        
        try {
            targetFreq = Integer.valueOf(args);
        } catch (NumberFormatException e) {
            m_botAction.sendPrivateMessage(name, "Invalid syntax. Please use !decrease <freq> where <freq> is either 0 or 1.");
            return;
        }
        
        if (currentState != HockeyState.OFF && (targetFreq == 0 || targetFreq == 1)) {
            t = teams.get(targetFreq);
            tempCheck = t.getScore();
            if(tempCheck > 0) {
                t.decreaseScore();
                m_botAction.sendArenaMessage("Score for " + t.getName() + " has been set to " + t.getScore() + " by " + name, 2);
            } else {
                m_botAction.sendPrivateMessage(name, t.getName() + " does not have any goals.");
            }
        } else if(currentState != HockeyState.OFF){
                m_botAction.sendPrivateMessage(name, "The action could not be completed at this time. Use !decrease <freq> "
                                                                                        + "to subtract a goal from <freq>.");
        }
    }
    
    /** 
     * Handles the !drop command (ZH+)
     *
     * @param name Name of the player who issued the command.
     */
    private void cmd_drop(String name) {
        dropBall();
    }
    
    /**
     * Handles the !forcenp command (ZH+)
     * Forces a player to !notplaying
     *
     * @param name name of the player that issued the command
     * @param args name of the player that needs to get forced into !notplaying
     */
    private void cmd_forcenp(String name, String args) {
        Player p;

        if(args.isEmpty()) {
            m_botAction.sendSmartPrivateMessage(name, "Error: Please provide a player's name, '!forcenp <player>'");
            return;
        }
        
        p = m_botAction.getFuzzyPlayer(args);

        if (p == null) {
            m_botAction.sendPrivateMessage(name, args + " could not be found.");
            return;
        }

        if (listNotplaying.contains(p.getPlayerName())) {
            m_botAction.sendPrivateMessage(name, p.getPlayerName() + " is already set to !notplaying.");
            return;
        }

        cmd_notplaying(p.getPlayerName());

        m_botAction.sendPrivateMessage(name, p.getPlayerName() + " has been set to !notplaying.");
    }

    /**
     * Handles the !help command
     * 
     * @param name name of the player
     * @param args The arguments of the full command message
     */
    private void cmd_help(String name, String args) {

        ArrayList<String> help = new ArrayList<String>();   //Help messages

        if (currentState == HockeyState.OFF) {
            help.add("Hockey Help Menu");
            help.add("-----------------------------------------------------------------------");
            help.add("!subscribe                           Toggles alerts in private messages");
            if (m_botAction.getOperatorList().isZH(name)) {
                help.add("!start                                                -- starts the bot");
            }
            String[] spam = help.toArray(new String[help.size()]);
            m_botAction.privateMessageSpam(name, spam);
         } else {
             if (!args.contains("cap") && !args.contains("staff")){
            help.add("Hockey Help Menu");
            help.add("-----------------------------------------------------------------------");
            help.add("!notplaying                       Toggles not playing mode  (short !np)");
            help.add("!cap                                            shows current captains!");
            help.add("!lagout              Puts you back into the game if you have lagged out");
            help.add("!list                                    Lists all players on this team");
            help.add("!status                                        Display status and score");
            help.add("!subscribe                           Toggles alerts in private messages");
            help.add("-----------------------------------------------------------------------");
            help.add("For more help: Private Mesage Me !help <topic>           ex. !help cap ");
            help.add("                                                                       ");
            help.add("Topics            Cap (Captain commands for before and during the game)");
                                  
             if (m_botAction.getOperatorList().isZH(name))
                 help.add("              Staff (The staff commands for before and during the game)");
             }
                
                String[] spam = help.toArray(new String[help.size()]);
                m_botAction.privateMessageSpam(name, spam);
            
            if (args.contains("cap")) {
                
                 ArrayList<String> hCap = new ArrayList<String>();
                 
                 hCap.add("Hockey Help Menu: Captain Controls");
                 hCap.add("-----------------------------------------------------------------------");
                 hCap.add("!add <player>                        Adds player (Default Ship: Spider)");
                 hCap.add("!add <player>:<ship>                  Adds player in the specified ship");
                 hCap.add("!remove <player>                              Removes specified player)");
                 hCap.add("!change <player>:<ship>           Sets the player in the specified ship");
                 hCap.add("!sub <playerA>:<playerB>           Substitutes <playerA> with <playerB>");
                 hCap.add("!switch <player>:<player>            Exchanges the ship of both players");
                 hCap.add("!timeout                       During faceoff, request a 30 sec timeout");
                 hCap.add("!ready                    Use this when you're done setting your lineup");
                 hCap.add("-----------------------------------------------------------------------");
                 
                String[] spamCap = hCap.toArray(new String[hCap.size()]);
                m_botAction.privateMessageSpam(name, spamCap);
                
            }
            if (args.contains("staff")) {
                if (m_botAction.getOperatorList().isZH(name)) {
                    
                    ArrayList<String> hStaff = new ArrayList<String>();
                    
                    hStaff.add("Hockey Help Menu: Staff Controls");
                    hStaff.add("----------------------------------------------------------------------------------");
                    hStaff.add("!start                                                              starts the bot");
                    hStaff.add("!stop                                                                stops the bot");
                    hStaff.add("!ball                                                           retrieves the ball");
                    hStaff.add("!drop                                                               drops the ball");
                    hStaff.add("!decrease <freq>                        subtracts a goal from <freq> (short: !dec)");
                    hStaff.add("!increase <freq>                              adds a goal for <freq> (short: !inc)");
                    hStaff.add("!zone <message>                  sends time-restricted advert, message is optional");
                    hStaff.add("!forcenp <player>                                     Sets <player> to !notplaying");
                    hStaff.add("!setcaptain <# freq>:<player>   Sets <player> as captain for <# freq> (short: !sc)");
                    hStaff.add("!penalty <player>:<reason>         Sends <player> to the penalty box (short: !pen)");
                    hStaff.add("!rempenalty <player>        Removes the current penalty of <player> (short: !rpen)");
                    hStaff.add("!hosttimeout                             Request a 30 second timeout (short: !hto)");
                    hStaff.add("!vote <vote>                        Give your <vote> during the final goal review.");
                    if (m_botAction.getOperatorList().isER(name)) {
                        hStaff.add("!settimeout <amount>                Sets captain timeouts to <amount> (default: 1)");
                    }
                    if (m_botAction.getOperatorList().isModerator(name)) {
                        hStaff.add("!off                                          stops the bot after the current game");
                        hStaff.add("!die                                                           disconnects the bot");
                    }
                    if (m_botAction.getOperatorList().isSmod(name)) {
                        hStaff.add("!allowzoner                         Forces the zone timers to reset allowing !zone");
                                      
                    }
                    String[] spamStaff = hStaff.toArray(new String[hStaff.size()]);
                    m_botAction.privateMessageSpam(name, spamStaff);
                }
            }
         }

    }
    
    /**
     * Handles the !hosttimeout command. (ZH+)
     * 
     * @param name name of the host.
     */
    private void cmd_hosttimeout(String name) {
        // Completely ignore the command if the bot is off.
        if(currentState == HockeyState.OFF)
            return;
        
        // If the host requests a timeout, check if the current phase allows it.   
        if(!(currentState == HockeyState.FACE_OFF)) {
            m_botAction.sendPrivateMessage(name, "This is currently only available during a FaceOff.");
        } else {
            // Send a nice message ...
            m_botAction.sendArenaMessage(name + 
                    " has issued a 30-second timeout.", Tools.Sound.BEEP1);
            // ... and start the timeout
            startTimeout();
        }
    }
    
    /**
     * Handles the !increase command (ZH+)
     * Increases a frequencies score by 1
     * 
     * @param name player that issue the !decrease command
     * @param args frequency whose score is to be decreased
     */
    private void cmd_increase(String name, String args) {
        int tempCheck = 0;
        int targetFreq = -1;
        HockeyTeam t;
        
        try {
            targetFreq = Integer.valueOf(args);
        } catch (NumberFormatException e) {
            m_botAction.sendPrivateMessage(name, "Invalid syntax. Please use !increase <freq> where <freq> is either 0 or 1.");
        }
        
        if (currentState != HockeyState.OFF) {
            if(targetFreq == 0 || targetFreq == 1) {
                t = teams.get(targetFreq);
                tempCheck = t.getScore();
                if (tempCheck + 1 >= config.getGameTarget()) {
                    m_botAction.sendPrivateMessage(name, "This command cannot be used for the final goal.");
                } else {
                    t.increaseScore();
                    m_botAction.sendArenaMessage("Score for " + t.getName() + " has been set to " + t.getScore() + " by " + name, 2);
                }
            } else {
                m_botAction.sendPrivateMessage(name, "The action could not be completed at this time. Use !increase <freq> "
                        + "to add a goal for <freq>.");
            }
        }
    }
    
    /**
     * Handles the !lagout/!return command
     *
     * @param name name of the player that issued the !lagout command
     */
    private void cmd_lagout(String name) {
        if (HockeyState.ACTIVEGAME.contains(currentState)
                || currentState == HockeyState.REVIEW) {
            HockeyTeam t;

            t = getTeam(name);

            /* Check if player was on a team */
            if (t == null) {
                return;
            }

            /* Check if the player was at least IN, or LAGGED OUT */
            if (!t.laggedOut(name)) {
                return;
            }

            /* Check if a return is possible */
            if (!t.laginAllowed(name)) {
                m_botAction.sendPrivateMessage(name, t.getLaginErrorMessage(name)); //Send error message
                return;
            }

            t.lagin(name); //Puts the player in again
        }
    }

    /**
     * Handles !list command
     *
     * @param name player that issued the !list command
     */
    private void cmd_list(String name) {
        HockeyTeam t;

        if (currentState != HockeyState.OFF && currentState != HockeyState.WAITING_FOR_CAPS
                && currentState != HockeyState.GAME_OVER) {
            t = getTeam(name);   //Retrieve teamnumber

            /* Check if the player is a staff member (In order to show the list of both teams to the staff member) */
            if (m_botAction.getOperatorList().isER(name)) {
                t = null;
            }

            /* Display set up */
            ArrayList<String> list = new ArrayList<String>();
            if (t == null) {
                /* Display both teams */
                for (int i = 0; i < 2; i++) {
                    list = listTeam(0);
                    list.add("`");
                    list.addAll(listTeam(1));
                }
            } else {
                /* Display one team */
                list = listTeam(t.getFrequency());
            }

            String[] spam = list.toArray(new String[list.size()]);
            m_botAction.privateMessageSpam(name, spam);
        }
    }

    private ArrayList<String> listTeam(int frequency) {
        /* Set up sorting */
        Comparator<HockeyPlayer> comparator = new Comparator<HockeyPlayer>() {

            @Override
            public int compare(HockeyPlayer pa, HockeyPlayer pb) {
                if (pa.getCurrentState() < pb.getCurrentState()) {
                    return -1;
                } else if (pa.getCurrentState() > pb.getCurrentState()) {
                    return 1;
                } else if (pa.getCurrentShipType() < pb.getCurrentShipType()) {
                    return -1;
                } else if (pa.getCurrentShipType() > pb.getCurrentShipType()) {
                    return 1;
                } else if (pb.getName().compareTo(pa.getName()) < 0) {
                    return 1;
                } else {
                    return 0;
                }
            }
        };
        ArrayList<String> list = new ArrayList<String>();
        HockeyTeam t = frequency == 0 ? team0 : team1;
        /* Display one team */
        list.add(t.getName() + " (captain: " + t.getCaptainName() + ")");
        list.add(Tools.formatString("Name:", 23) + " - "
                + Tools.formatString("Ship:", 10) + " - " + "Status:");

        HockeyPlayer[] players = t.players.values().toArray(
                new HockeyPlayer[t.players.values().size()]);
        Arrays.sort(players, comparator);

        for (HockeyPlayer p : players) {
            list.add(Tools.formatString(p.p_name, 23) + " - "
                    + Tools.formatString(Tools.shipName(p.getCurrentShipType()), 10) + " - " + p.getStatus());
        }
        return list;
    }

    /**
     * Handles the !notplaying command
     *
     * @param name name of the player that issued the !notplaying command
     */
    private void cmd_notplaying(String name) {
        HockeyTeam t;

        if (currentState != HockeyState.OFF) {
            t = getTeam(name);

            /* Check if player is on the notplaying list and if so remove him from that list */
            if (listNotplaying.contains(name)) {
                listNotplaying.remove(name);  //Remove from him from the notplaying list
                m_botAction.sendPrivateMessage(name,
                        "You have been removed from the not playing list.");   //Notify the player
                /* Put the player on the spectator frequency */
                m_botAction.setShip(name, 1);
                m_botAction.specWithoutLock(name);
                return;
            }

            /* Add the player to the notplaying list */
            listNotplaying.add(name); //Add the player to the notplaying list
            m_botAction.sendPrivateMessage(name, "You have been added to the not playing list. "
                    + "(Captains will be unable to add or sub you in.)"); //Notify the player
            m_botAction.specWithoutLock(name);  //Spectate the player
            m_botAction.setFreq(name, FREQ_NOTPLAYING);  //Set the player to the notplaying frequency

            /* Check if the player was on one of the teams */
            if (t != null) {
                /* Check if the player was a captain */
                if (isCaptain(name)) {
                    t.captainLeft();   //Remove the player as captain
                }

                if (currentState == HockeyState.ADDING_PLAYERS) {
                    if (t.isOnTeam(name)) {
                        m_botAction.sendArenaMessage(name + " has been removed from the game. (not playing)");
                        t.removePlayer(name);
                    }
                }

                /* Check if a player was in and set him to "out but subable" status */
                if (currentState != HockeyState.ADDING_PLAYERS
                        && currentState != HockeyState.GAME_OVER) {
                    if (t.isOnTeam(name)) {
                        if (t.isPlaying(name) || t.laggedOut(name)) {
                            m_botAction.sendArenaMessage(
                                    name + " has been removed from the game. (not playing)"); //Notify the player
                            t.setOutNotPlaying(name); //Set player to out, but subable status
                        }
                    }
                }

                m_botAction.setFreq(name, FREQ_NOTPLAYING);     //Set the player to the notplaying frequency
            }
        }
    }

    /**
     * Handles the !off command (Mod+)
     *
     * @param name name of the player that issued the !off command
     */
    private void cmd_off(String name) {
        switch (currentState) {
            case OFF:
                m_botAction.sendPrivateMessage(name, "Bot is already OFF");
                break;
            case WAITING_FOR_CAPS:
                cmd_stop(name);
                break;
            default:
                m_botAction.sendPrivateMessage(name, "Turning OFF after this game");
                lockLastGame = true;
        }
    }

    /**
     * Handles the !penalty command (ZH+)
     * The duration of the penalty is fixed at the value in the config file. (Default: 60 sec)
     * 
     * @param name Name of the player that issued the command.
     * @param args The arguments given in the full command line.
     */
    private void cmd_penalty(String name, String args) {
        String targetName;
        String[] splitCmd;
        HockeyPlayer targetPlayer;
        HockeyTeam t;
        
        if (HockeyState.ACTIVEGAME.contains(currentState)) {
            // Check if a valid argument is given.
            if(args.isEmpty()) {
                m_botAction.sendSmartPrivateMessage(name,
                        "Error: Specify player and penalty reason, '!penalty <player>:<reason>'");
                return;
            }

            splitCmd = args.split(":"); //Split command parameters

            /* Check command syntax */
            if (splitCmd.length < 2) {
                m_botAction.sendSmartPrivateMessage(name,
                        "Error: Specify player and penalty reason, '!penalty <player>:<reason>'");
                return;
            }

            targetName = m_botAction.getFuzzyPlayerName(splitCmd[0]);
            
            if(targetName == null) {
                m_botAction.sendSmartPrivateMessage(name, "Player " + splitCmd[0] + " does not exist.");
                return;
            }
            
            if(team0.isOnTeam(targetName)) {
                t = team0;
            } else if(team1.isOnTeam(targetName)) {
                t = team1;
            } else {
                m_botAction.sendSmartPrivateMessage(name, 
                        "Player " + targetName + " is not a player in any of the hockeyteams.");
                return;
            }
            
            targetPlayer = t.getPlayer(targetName);
            
            if(targetPlayer != null && targetPlayer.penalty == HockeyPenalty.NONE) {
                targetPlayer.setPenalty(HockeyPenalty.OTHER);
                m_botAction.sendArenaMessage(name + " has given " + targetName +" a penalty. "
                        + "Reason: " + splitCmd[1] + ".",Tools.Sound.BEEP2);
            } else {
                m_botAction.sendSmartPrivateMessage(name, 
                        "Player " + targetName + " is already sitting out a penalty.");
            }
        }
        
    }
    
    /**
     * Handles the !ready command (cap)
     *
     * @param name name of the player that issued the command
     * @param override override 0/1 for teams, -1 if not overriden
     */
    private void cmd_ready(String name, int override) {
        HockeyTeam t;

        if (currentState == HockeyState.ADDING_PLAYERS) {
            t = getTeam(name, override); //Retrieve teamnumber

            t.ready();   //Ready team

            /* Check if both teams are ready */
            if (team0.isReady() && team1.isReady()) {
                checkLineup(); //Check lineups
            }
        }
    }

    /**
     * Handles the !remove command (cap)
     *
     * @param name name of the player that issued the !remove command
     * @param args command parameters
     * @param override override 0/1 for teams, -1 if not overriden
     */
    private void cmd_remove(String name, String args, int override) {
        HockeyTeam t;
        HockeyPlayer p;   //Player to be removed

        if (HockeyState.ACTIVEGAME.contains(currentState)) {
            t = getTeam(name, override); //Retrieve team

            /* Check command syntax */
            if (args.isEmpty()) {
                m_botAction.sendPrivateMessage(name, "Error: Please specify a player, '!remove <player>'");
                return;
            }

            /* Search for the to be removed player */
            p = t.searchPlayer(m_botAction.getFuzzyPlayerName(args));

            /* Check if player has been found */
            if (p == null) {
                m_botAction.sendPrivateMessage(name, "Error: Player could not be found");
                return;
            }

            if (p.penalty != HockeyPenalty.NONE) {
                checkPenaltyExipred(name, t);
                if (p.penalty != HockeyPenalty.NONE)
                    m_botAction.sendPrivateMessage(name,"Player cannot be removed because they are in the penalty box.");
                else
                    t.removePlayer(p.getName());
            } else {
                t.removePlayer(p.getName());
            }

            if (currentState == HockeyState.ADDING_PLAYERS)
                determineTurn();
        }
    }

    /**
     * Handles the !removecap command (Not used atm)
     *
     * @param name name of the player that issued the !removecap command
     * @param override override 0/1 for teams, -1 if not overriden
     */
    private void cmd_removecap(String name, int override) {
        HockeyTeam t;

        if (currentState != HockeyState.OFF && currentState != HockeyState.GAME_OVER) {
            t = getTeam(name, override); //Retrieve team number

            t.captainLeft();   //Remove captain
        }
    }

    /**
     * Handles the !rempenalty command. (ZH+)
     * 
     * @param name name of the player that issued the command.
     * @param args The arguments given in the full command line.
     */
    private void cmd_removePenalty(String name, String args) {
        String targetName;
        HockeyPlayer targetPlayer;
        HockeyTeam t;
        
        if(args.isEmpty()) {
            // No name given
            m_botAction.sendSmartPrivateMessage(name, "Error: Please specify a player, '!rempenalty <player>'");
            return;
        }
;
        targetName = m_botAction.getFuzzyPlayerName(args);
        if(targetName == null) {
            m_botAction.sendSmartPrivateMessage(name, "Player " + args + " does not exist.");
            return;
        }
        
        if(team0.isOnTeam(targetName)) {
            t = team0;
        } else if(team1.isOnTeam(targetName)) {
            t = team1;
        } else {
            m_botAction.sendSmartPrivateMessage(name, 
                    "Player " + targetName + " is not a player in any of the hockeyteams.");
            return;
        }
        
        targetPlayer = t.getPlayer(targetName);
        
        if(targetPlayer != null && targetPlayer.penalty != HockeyPenalty.NONE) {
            targetPlayer.penaltyTimestamp = 0;
            targetPlayer.penalty = HockeyPenalty.NONE;
            targetPlayer.penalties--;
            if(t.getFrequency() == 0) {
                m_botAction.warpTo(targetName, config.getTeam0ExtX() / 16, config.getTeam0ExtY() / 16);
            } else {
                m_botAction.warpTo(targetName, config.getTeam1ExtX() / 16, config.getTeam1ExtY() / 16);
            }
            m_botAction.sendArenaMessage(name + " has declared " + targetName +"'s last penalty "+ 
                    "to be void.",Tools.Sound.BEEP2);
        } else {
            m_botAction.sendSmartPrivateMessage(name, 
                    "Player " + targetName + " is currently not sitting out any penalties.");
        }
    }
    
    /**
     * Handles the !setcaptain command (ZH+)
     *
     * @param name name of the player that issued the !setcaptain command
     * @param args command parameters
     * @param override override 0/1 for teams, -1 if not overriden
     */
    private void cmd_setCaptain(String name, String args, int override) {
        int frequency;
        Player p;
        String[] splitCmd;

        if (currentState != HockeyState.OFF && currentState != HockeyState.GAME_OVER) {

            /* Check command syntax */
            if (args.isEmpty()) {
                m_botAction.sendPrivateMessage(name, 
                        "Error: please specify a player and frequency, '!setcaptain <# freq>:<player>'");
                return;
            }

            splitCmd = args.split(":"); //Split parameters

            /* Check command syntax */
            if (splitCmd.length < 2) {
                m_botAction.sendPrivateMessage(name, 
                        "Error: please specify a player, '!setcaptain <# freq>:<player>'");
                return;
            }

            p = m_botAction.getFuzzyPlayer(splitCmd[1]); //Search player

            /* Check if player has been found */
            if (p == null) {
                m_botAction.sendPrivateMessage(name, "Error: Unknown player");
                return;
            }

            /* Retrieve teamnumber or frequency number */
            try {
                if(override == -1) {
                    frequency = Integer.parseInt(splitCmd[0]);
                } else {
                    frequency = override;
                }
            } catch (Exception e) {
                m_botAction.sendPrivateMessage(name, 
                        "Error: please specify a correct frequency, '!setcaptain <# freq>:<player>'");
                return;
            }

            /* Check if frequency is valid */
            if (frequency == 0) {
                team0.setCaptain(p.getPlayerName()); //Set player to captain
            } else if (frequency == 1) {
                team1.setCaptain(p.getPlayerName());
            } else {
                m_botAction.sendPrivateMessage(name, 
                        "Error: please specify a correct frequency, '!setcaptain <# freq>:<player>'");
            }

        }
    }

    /**
     * Handles the !settimeout command. (ER+)
     * Intended to be used to disable the system in case of abuse by the captains.
     * 
     * @param name Name of the player that issued the command.
     * @param args The arguments of the issued command.
     */
    private void cmd_settimeout(String name, String args) {
        int value;
        
        if(currentState == HockeyState.OFF)
            return;
        
        if (!(currentState == HockeyState.GAME_OVER
                || currentState == HockeyState.WAITING_FOR_CAPS
                || currentState == HockeyState.ADDING_PLAYERS)) {
            // Only allowed to change the setting when no game is in progress.
            m_botAction.sendPrivateMessage(name, "Changing the timeout " +
                "setting is not allowed at this stage of the game.");
            return;
        }
        try {
            value = Integer.parseInt(args);
        } catch (Exception e) {
         // No argument or an invalid argument given.
            m_botAction.sendPrivateMessage(name, "Please provide a valid number. " +
                    "(Usage: !settimeout <number>)");
            return;
        }
        
        // If value is less than 0, set maxTimeouts to 0. Otherwise the value provided.
        maxTimeouts = (value < 0)?0:value;
        
        m_botAction.sendPrivateMessage(name, "Maximum timeouts set to " + maxTimeouts + ".");
            
    }
    
    /**
     * Handles the !start command (ZH+)
     *
     * @param name player that issued the !start command
     */
    private void cmd_start(String name) {
        if (currentState == HockeyState.OFF) {
            start();
        } else {
            m_botAction.sendPrivateMessage(name, "Error: Bot is already ON");
        }
    }

    /**
     * Handles the !status command
     *
     * @param name name of the player that issued the command
     */
    private void cmd_status(String name) {
        String[] status;    //Status message

        status = new String[2];
        status[0] = ""; //Default value
        status[1] = ""; //Default value

        switch (currentState) {
            case OFF:
                status[0] = "Bot turned off, no games can be started at this moment.";
                break;
            case WAITING_FOR_CAPS:
                if (config.getAllowAutoCaps()) {
                    status[0] = "A new game will start when two people message me with !cap";
                } else {
                    status[0] = "Request a new game with '?help start hockey please'";
                }
                break;
            case ADDING_PLAYERS:
                status[0] = "Teams: " + team0.getName() + " vs. " + team1.getName()
                        + ". We are currently arranging lineups";
                break;
            case FACE_OFF:
                status[0] = "Teams: " + team0.getName() + " vs. " + team1.getName()
                        + ". We are currently facing off";
                break;
            case TIMEOUT:
                status[0] = "Teams: " + team0.getName() + " vs. " + team1.getName()
                        + ". We are currently in a timeout";
                break;
            case GAME_IN_PROGRESS:
                status[0] = "Game is in progress.";
                status[1] = "Score " + team0.getName() + " vs. " + team1.getName() + ": " + score();
                break;
            case GAME_OVER:
                status[0] = "Teams: " + team0.getName() + " vs. " + team1.getName()
                        + ". We are currently ending the game";
                break;
        }

        /* Send status message */
        if (!status[0].isEmpty()) {
            m_botAction.sendPrivateMessage(name, status[0]);
        }

        if (!status[1].isEmpty()) {
            m_botAction.sendPrivateMessage(name, status[1]);
        }
    }

    /**
     * Handles the !stop command (ZH+)
     *
     * @param name player that issued the !stop command
     */
    private void cmd_stop(String name) {
        if (currentState != HockeyState.OFF) {
            m_botAction.sendArenaMessage("Bot has been turned OFF");
            currentState = HockeyState.OFF;
            reset();
            unlockArena();
        } else {
            m_botAction.sendPrivateMessage(name, "Error: Bot is already OFF");
        }
    }

    /**
     * Handles the !sub command (cap)
     *
     * @param name name of the player that issued the !sub command
     * @param args command parameters
     * @param override 0/1 for teams, -1 for not overriden
     */
    private void cmd_sub(String name, String args, int override) {
        HockeyTeam t;
        String[] splitCmd;
        HockeyPlayer playerA;
        HockeyPlayer playerB;
        Player playerBnew;

        if (HockeyState.ACTIVEGAME.contains(currentState)) {
            t = getTeam(name, override); //Retrieve teamnumber

            if (t == null) {
                return;
            }

            /* Check command syntax */
            if (args.isEmpty()) {
                m_botAction.sendPrivateMessage(name, "Error: Specify players, !sub <playerA>:<playerB>");
                return;
            }

            splitCmd = args.split(":");

            /* Check command syntax */
            if (splitCmd.length < 2) {
                m_botAction.sendPrivateMessage(name, "Error: Specify players, !sub <playerA>:<playerB>");
                return;
            }

            /* Check if team has any substitutes left */
            if (!t.hasSubtitutesLeft()) {
                m_botAction.sendPrivateMessage(name, "Error: You have 0 substitutes left.");
                return;
            }

            playerA = t.searchPlayer(m_botAction.getFuzzyPlayerName(splitCmd[0]));   //Search for <playerA>
            playerBnew = m_botAction.getFuzzyPlayer(splitCmd[1]);   //Search for <playerB>

            /* Check if players can be found */
            if (playerA == null || playerBnew == null) {
                m_botAction.sendPrivateMessage(name, "Error: Player could not be found");
                return;
            }

            /* Check if sub is a bot */
            if (m_botAction.getOperatorList().isBotExact(playerBnew.getPlayerName())) {
                m_botAction.sendPrivateMessage(name, "Error: Bots are not allowed to play.");
                return;
            }

            /* Check if <playerA> is already out and thus cannot be subbed */
            if (playerA.p_state > HockeyPlayer.OUT_SUBABLE) {
                m_botAction.sendPrivateMessage(name, "Error: Cannot substitute a player that is already out");
                return;
            }

            /* Check if <playerB> is on the notplaying list */
            if (listNotplaying.contains(playerBnew.getPlayerName())) {
                m_botAction.sendPrivateMessage(name,
                        "Error: " + playerBnew.getPlayerName() + " is set to not playing.");
                return;
            }

            /* Check if <playerB> is already on the other team */
            if (getOtherTeam(t).isOnTeam(playerBnew.getPlayerName())) {
                m_botAction.sendPrivateMessage(name, "Error: Substitute is already on the other team");
                return;
            }

            /* Check if <playerB> was already on the team */
            playerB = t.searchPlayer(playerBnew.getPlayerName());
            if (playerB != null) {
                /* Check when last !sub was and if this sub is allowed */
                if (!playerB.isSubAllowed()) {
                    m_botAction.sendPrivateMessage(name, "Error: Sub not allowed yet for this player, wait "
                            + playerB.getTimeUntilNextSub() + " more seconds before next !sub");
                    return;
                }
            }

            if (playerA.penalty != HockeyPenalty.NONE) {
                checkPenaltyExipred(playerA.getName(), t);
                if (playerA.penalty != HockeyPenalty.NONE)
                    m_botAction.sendPrivateMessage(name,"Player cannot be subbed because they are in the penalty box.");
                else
                    t.sub(playerA, playerBnew); //Execute the substitute
            } else {
                t.sub(playerA, playerBnew); //Execute the substitute
            }
        }
    }
    
    /**
     * Handles the !subscribe command
     *
     * @param name player that issued the !subscribe command
     */
    private void cmd_subscribe(String name) {
        if (currentState != HockeyState.OFF) {
            if (listAlert.contains(name)) {
                listAlert.remove(name);
                m_botAction.sendPrivateMessage(name, "You have been removed from the alert list.");
            } else {
                listAlert.add(name);
                m_botAction.sendPrivateMessage(name, "You have been added to the alert list.");
            }
        }
    }
    
    /**
     * Handles the !switch command (cap)
     *
     * @param name player that issued the !switch command
     * @param args command parameters
     * @param override override 0/1 for teams, -1 for not overriden
     */
    private void cmd_switch(String name, String args, int override) {
        HockeyTeam t;
        String[] splitCmd;
        HockeyPlayer playerA;
        HockeyPlayer playerB;

        if (HockeyState.ACTIVEGAME.contains(currentState)) {
            t = getTeam(name, override); //Retrieve team number

            /* Check command syntax */
            if (args.isEmpty()) {
                m_botAction.sendPrivateMessage(name,
                        "Error: Specify players to be switched, !switch <playerA>:<playerB>");
                return;
            }

            splitCmd = args.split(":"); //Split command parameters

            /* Check command syntax */
            if (splitCmd.length < 2) {
                m_botAction.sendPrivateMessage(name,
                        "Error: Specify players to be switched, !switch <playerA>:<playerB>");
                return;
            }

            playerA = t.searchPlayer(m_botAction.getFuzzyPlayerName(splitCmd[0])); //Search <playerA>
            playerB = t.searchPlayer(m_botAction.getFuzzyPlayerName(splitCmd[1])); //Search <playerB>

            /* Check if both players have been found */
            if (playerA == null || playerB == null) {
                m_botAction.sendPrivateMessage(name, "Error: Unknown players");
                return;
            }

            /* Check if player is already out or subbed */
            if (playerA.isOut() || playerB.isOut()) {
                m_botAction.sendPrivateMessage(name, "Error: Cannot switch a player that is already out.");
                return;
            }

            /* Check if a switch is allowed timewise */
            if (!playerA.isSwitchAllowed()) {
                m_botAction.sendPrivateMessage(name, "Error: Switch not allowed yet for " + playerA.getName()
                        + ", wait " + playerA.getTimeUntilNextSwitch() + " more seconds before next !switch");
                return;
            }
            if (!playerB.isSwitchAllowed()) {
                m_botAction.sendPrivateMessage(name, "Error: Switch not allowed yet for " + playerB.getName()
                        + ", wait " + playerB.getTimeUntilNextSwitch() + " more seconds before next !switch");
                return;
            }

            t.switchPlayers(playerA, playerB);   //Switch players
        } else if (currentState != HockeyState.OFF) {
            m_botAction.sendPrivateMessage(name, "Error: could not switch at this moment of the game");
        }
    }
    
    /**
     * Handles the !timeout command (cap)
     * 
     * @param name name of the player that issued the command.
     */
    private void cmd_timeout(String name) {
        // If a captain requests a timeout, get his team's info.
        HockeyTeam t = getTeam(name);
        
        // Check if the request is valid
        if(!(currentState == HockeyState.FACE_OFF)) {
            m_botAction.sendPrivateMessage(name, "You can only request a timeout during the FaceOff.");
        } else if(t.timeout == 0) {
            // Checks if the captain has any timeouts left to use
            m_botAction.sendPrivateMessage(name, "You have already used your timeout" + 
                    ((maxTimeouts > 1)?"s":"") + ".");
        } else {
            // Good to go. Lower the amount of available timeouts ...
            t.useTimeOut();
            // .. send a nice message ...
            m_botAction.sendArenaMessage(name + 
                    " has requested a 30-second timeout for Freq " +
                    t.getFrequency() + ".", Tools.Sound.CROWD_GEE);
            // ... and start the timeout.
            startTimeout();
        }
    }
    
    /**
     * Handles the voting by the staff during the review of the final goal.
     * 
     * @param name Name of the staffmember who is voting.
     * @param args The vote of the staffmember.
     */
    private void cmd_vote(String name, String args) {
        HockeyVote vote = HockeyVote.NONE;
        
        // No need to spam, just ignore the command.
        if(currentState != HockeyState.REVIEW)
            return;
        
        // For faster comparison.
        args = args.toLowerCase();
        
        // Check which vote has been cast.
        if(args.startsWith("ab")) {
            // Voted to abstain
            vote = HockeyVote.ABSTAIN;
        } else if(args.startsWith("cl")) {
            // Voted clean.
            vote = HockeyVote.CLEAN;
        } else if(args.startsWith("cr")) {
            // Voted crease.
            vote = HockeyVote.CREASE;
        } else if(args.startsWith("ph")) {
            // Voted phase.
            vote = HockeyVote.PHASE;
        }
        
        // Check if a valid vote has been cast.
        if(vote == HockeyVote.NONE) {
            m_botAction.sendSmartPrivateMessage(name, "When using !vote, please use one of the following options: Abstain, Clean, Crease or Phase.");
            return;
        }
        
        // Check if the voter already had a vote cast, and has changed his/her mind.
        if(listVotes.containsKey(name)) {
            m_botAction.sendSmartPrivateMessage(name, "Your previous vote (" + listVotes.remove(name) + ") has been replaced with "+ vote +".");
        } else {
            m_botAction.sendSmartPrivateMessage(name, "Your vote ("+ vote +") has been stored.");
        }
        
        // Store the vote
        listVotes.put(name, vote);
        
    }
    
    /**
     * Handles the !zone command (ZH+)
     *
     * @param name name of the player that issued the command
     * @param args message to use for zoner
     */
    private void cmd_zone(String name, String args) {
        if (!allowManualZoner()) {
            m_botAction.sendPrivateMessage(name, "Zoner not allowed yet.");
            return;
        }

        if (!(currentState == HockeyState.GAME_OVER
                || currentState == HockeyState.WAITING_FOR_CAPS
                || currentState == HockeyState.ADDING_PLAYERS)) {
            m_botAction.sendPrivateMessage(name, "Zoner not allowed at this stage of the game.");
            return;
        }

        //args can go through regardless if it has a valid value. This is taken care of in the newGameAlert function.
        newGameAlert(name, args);
    }

    /*
     * Game modes
     */
    /**
     * Starts the bot
     */
    private void start() {
        m_botAction.setMessageLimit(8, false);
        m_botAction.setReliableKills(1);
        m_botAction.setPlayerPositionUpdating(300);
        m_botAction.setLowPriorityPacketCap(8);
        lockLastGame = false;
        lockDoors();
        setSpecAndFreq();

        try {
            gameticker.cancel();
        } catch (Exception e) {
        }

        gameticker = new Gameticker();
        m_botAction.scheduleTask(gameticker, Tools.TimeInMillis.SECOND, Tools.TimeInMillis.SECOND);

        startWaitingForCaps();
    }
  
    /**
     * Starts waiting for caps
     */
    private void startWaitingForCaps() {
        // To avoid any racing conditions, set the current state to WAIT.
        // This prevents the bot from accidentally doing stuff that influences the commands here.
        currentState = HockeyState.WAIT;
        reset();

        if (config.getAllowAutoCaps()) {
            m_botAction.sendArenaMessage("A new game will start when two people message me with !cap -"
                    + m_botAction.getBotName(), Tools.Sound.BEEP2);
        } else {
            m_botAction.sendArenaMessage("Request a new game with '?help start hockey please'"
                    + " -" + m_botAction.getBotName(), Tools.Sound.BEEP2);
        }
        currentState = HockeyState.WAITING_FOR_CAPS;
    }

    /**
     * Start adding players state
     * - Notify arena
     * - Notify chats
     * - Determine next pick
     */
    private void startAddingPlayers() {
        // To avoid any racing conditions, set the current state to WAIT.
        // This prevents the bot from accidentally doing stuff that influences the commands here.
        currentState = HockeyState.WAIT;
               
        lockArena();
        m_botAction.specAll();
        timeStamp = System.currentTimeMillis();
        m_botAction.sendArenaMessage("Captains you have 10 minutes to set up your lineup correctly!",
                Tools.Sound.BEEP2);
        showTeamNameObjects();

        if (config.getAllowAutoCaps()) {
            newGameAlert(null, null);
        }

        if (team0.hasCaptain()) {
            team0.putCaptainInList();
        }

        if (team1.hasCaptain()) {
            team1.putCaptainInList();
        }
        
        currentState = HockeyState.ADDING_PLAYERS;
        determineTurn();
    }

    /**
     * Starts pre game
     */
    private void startFaceOff() {
        // To avoid any racing conditions, set the current state to WAIT.
        // This prevents the bot from accidentally doing stuff that influences the commands here.
        currentState = HockeyState.WAIT;
        updateScoreBoard();

        puck.clear();
        team0.clearUnsetPenalties();
        team1.clearUnsetPenalties();

        m_botAction.sendArenaMessage("Prepare For FaceOff", Tools.Sound.CROWD_OOO);

        timeStamp = System.currentTimeMillis();
        puck.dropDelay = (int) (Math.random() * 9 + 15);
        puck.holding = false;
        doGetBall(config.getPuckDropX(), config.getPuckDropY());
        
        currentState = HockeyState.FACE_OFF;
    }

    private void startReview(SoccerGoal event) {
        
        Point release = puck.peekLastReleasePoint();
        int freq = event.getFrequency();
        HockeyZone hz = getZone(release);
        
        if((freq == 0 && HockeyZone.CREASE1.equals(hz))
                || (freq == 1 && HockeyZone.CREASE0.equals(hz))) {
            //Offensive crease.
            m_botAction.sendArenaMessage("CREASE. No count.", Tools.Sound.CROWD_GEE);
        } else if ((freq == 0 && (puck.veloX < 0 || HockeyZone.CREASE0.equals(hz)))
                || (freq == 1 && (puck.veloX > 0 || HockeyZone.CREASE1.equals(hz)))) {
            //Own goal scored.
            m_botAction.sendArenaMessage("OWN GOAL!", Tools.Sound.GAME_SUCKS);
            m_botAction.sendArenaMessage("Goal awarded to: " + addOwnGoal());
            //Award point to the opposing team.
            teams.get(1 - freq).increaseScore();
            displayScores();
        } else if (freq == 0 || freq == 1) {
            //Clean goal.
            m_botAction.sendArenaMessage("Clean!");
            teams.get(freq).increaseScore();
            addPlayerGoalWithAssist();
            displayScores();
            teams.get(1 - freq).clearDCs();
        }

        //TODO change this
        if (team0.getScore() >= config.getGameTarget()
                || (team1.getScore() >= config.getGameTarget())) {
            if(config.allowVote) {
                startFinalReview();
            } else {
                gameOver();
            }
        } else {
            startFaceOff();
        }
    }
    
    /**
     * Starts a game
     */
    private void startGame() {
        // To avoid any racing conditions, set the current state to WAIT.
        // This prevents the bot from accidentally doing stuff that influences the commands here.
        currentState = HockeyState.WAIT;

        timeStamp = System.currentTimeMillis();
        m_botAction.sendArenaMessage("Go Go Go !!!", Tools.Sound.VICTORY_BELL);
        
        currentState = HockeyState.GAME_IN_PROGRESS;
    }
    
    /**
     * Initiates the timeout state.
     */
    private void startTimeout() {
        // To avoid any racing conditions, set the current state to WAIT.
        // This prevents the bot from accidentally doing stuff that influences the commands here.
        currentState = HockeyState.WAIT;
        
        timeStamp = System.currentTimeMillis();
        doRemoveBall();
        
        // When looking at doRemoveBall(), this code seems to be redundant.
        // However, due to the bot not always having the latest ball positions, this 
        // safeguard is needed to make it function properly, for now.
        m_botAction.getShip().move(config.getPuckToX(), config.getPuckToY());
        m_botAction.getBall(puck.getBallID(), puck.getTimeStamp());
        dropBall();
        
        currentState = HockeyState.TIMEOUT;
    }

    private void startFinalReview() {
        // To avoid any racing conditions, set the current state to WAIT.
        // This prevents the bot from accidentally doing stuff that influences the commands here.
        currentState = HockeyState.WAIT;
        
        // Get the ball out of play.
        timeStamp = System.currentTimeMillis();
        doRemoveBall();
        
        // When looking at doRemoveBall(), this code seems to be redundant.
        // However, due to the bot not always having the latest ball positions, this 
        // safeguard is needed to make it function properly, for now.
        m_botAction.getShip().move(config.getPuckToX(), config.getPuckToY());
        m_botAction.getBall(puck.getBallID(), puck.getTimeStamp());
        dropBall();
        
        // Send a message to notify everyone.
        m_botAction.sendArenaMessage("Final goal under review by the hosts. Hosts, you have 15 seconds to cast your vote!", Tools.Sound.START_MUSIC);
        
        // Start a timer for the voting period
        reviewDelay = new TimerTask() {
            @Override
            public void run() {
                reviewing = false;
            }
        }; m_botAction.scheduleTask(reviewDelay, 15 * Tools.TimeInMillis.SECOND);
        
        // Reset the list
        listVotes.clear();
        
        // Don't let the review end until this is set to false by the timer.
        reviewing = true;
        
        currentState = HockeyState.REVIEW;
    }
    
    /**
     * What to do with when game is over
     */
    private void gameOver() {
        // To avoid any racing conditions, set the current state to WAIT.
        // This prevents the bot from accidentally doing stuff that influences the commands here.
        currentState = HockeyState.WAIT;
        
        clearTeamNameObjects();

        //Cancel timer
        m_botAction.setTimer(0);

        statsDelay = new TimerTask() {
            @Override
            public void run() {
                m_botAction.sendArenaMessage("------------ GAME OVER ------------");
                m_botAction.sendArenaMessage("Result of " + team0.getName() + " vs. "
                        + team1.getName(), Tools.Sound.HALLELUJAH);
                dispResults();
            }
        }; ba.scheduleTask(statsDelay, Tools.TimeInMillis.SECOND * 2);

        mvpDelay = new TimerTask() {
            @Override
            public void run() {
                m_botAction.sendArenaMessage("MVP: " + getMVP() + "!", Tools.Sound.INCONCEIVABLE);
            }
        }; ba.scheduleTask(mvpDelay, Tools.TimeInMillis.SECOND * 5);
        
        timeStamp = System.currentTimeMillis();
        
        currentState = HockeyState.GAME_OVER;
    }
    
    /**
     * Display the statistics of the game
     */
    private void dispResults() {
        ArrayList<String> spam = new ArrayList<String>();
        spam.add("+----------------------+-------+-------+---------+-------+--------+-----------+-----------+----------+");
        spam.add("|        Freq 0        | Goals | Saves | Assists | Shots | Steals | Turnovers | Penalties | Own Goal |");
        spam.add("+----------------------+-------+-------+---------+-------+--------+-----------+-----------+----------+");
        ////////("012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901");
        ////////("0         1         2         3         4         5         6         7         8         9         10");
    
        spam.addAll(addTeamStats(team0));

        spam.add("+----------------------+-------+-------+---------+-------+--------+-----------+-----------+----------+");
        spam.add("|        Freq 1        | Goals | Saves | Assists | Shots | Steals | Turnovers | Penalties | Own Goal |");
        spam.add("+----------------------+-------+-------+---------+-------+--------+-----------+-----------+----------+");
        
        spam.addAll(addTeamStats(team1));
        
        spam.add("+----------------------+-------+-------+---------+-------+--------+-----------+-----------+----------+");
        //goals, saves, assists, steals, penalties

        m_botAction.arenaMessageSpam(spam.toArray(new String[spam.size()]));
    }
    
    /**
     * Looks up all the statistics of the players of a team and neatly formats them into a list.
     * 
     * @param team HockeyTeam for which to look up the statistics.
     * @return ArrayList<String> of the statistics. 
     */
    private ArrayList<String> addTeamStats(HockeyTeam team) {
        ArrayList<String> stats = new ArrayList<String>();
        for (HockeyPlayer p : team.players.values()) {
            stats.add("| " + Tools.formatString(p.getName(), 20)
                    + " |" + Tools.rightString(Integer.toString(p.goals), 6)
                    + " |" + Tools.rightString(Integer.toString(p.saves), 6)
                    + " |" + Tools.rightString(Integer.toString(p.assists), 8)
                    + " |" + Tools.rightString(Integer.toString(p.shotsOnGoal), 6)
                    + " |" + Tools.rightString(Integer.toString(p.steals), 7)
                    + " |" + Tools.rightString(Integer.toString(p.turnovers), 10)
                    + " |" + Tools.rightString(Integer.toString(p.penalties), 10)
                    + " |" + Tools.rightString(Integer.toString(p.ownGoals), 9)
                    + " |");
        }
        return stats;
    }

    private void addPlayerGoalWithAssist() {
        try {
            String scorer = puck.getLastCarrierName();
            if (team0.isOnTeam(scorer)) {
                team0.getPlayer(scorer).madeGoal(true);
                String assister = puck.getLastCarrierName();
                if (team0.isOnTeam(assister)) {
                    team0.getPlayer(assister).madeAssist();
                }
            } else {
                team1.getPlayer(scorer).madeGoal(true);
                String assister = puck.getLastCarrierName();
                if (team1.isOnTeam(assister)) {
                    team1.getPlayer(assister).madeAssist();
                }
            }
        } catch (Exception e) {
        }

    }
    
    /**
     * Handles the assigning of stats when a player scored in his own goal.
     * @return Name of the player who the goal is awarded to. 
     */
    private String addOwnGoal() {
        //Two possible situations:
        //A) The goal gets awarded to the previous carrier if it's the opponent of the person who made the goal.
        //B) The goal gets awarded to the closest opponent of the person who made the goal.
        String playerA, playerB;
        HockeyTeam tA, tB;
        
        playerA = puck.getLastCarrierName();
        playerB = puck.getLastCarrierName();
        
        tA = getTeam(playerA);
        tB = getTeam(playerB);
        
        //Situation A: We can directly assign the owngoal and goals to the players, so nothing to do.
        //Situation B: If tB is null or the players are on the same team, we need to find the closest opponent.
        //This will be highly inaccurate though...
        if(tA == null || tB == null || tA == tB) {
            Integer d = 1100000;    // Squared value of current minimum distance.
            Integer dP, dX, dY;
            Point pt;
            Player p;
            
            pt = puck.peekLastReleasePoint();
            tB = getOtherTeam(tA);
            for(HockeyPlayer pB : tB.players.values()) {
                //Exclude the player if he/she's in the penalty box.
                if(pB.penalty != HockeyPenalty.NONE)
                    continue;
                
                p = m_botAction.getPlayer(pB.getName());
                if(p != null) {
                    dX = p.getXTileLocation() - (pt.x / 16);
                    dY = p.getYTileLocation() - (pt.y / 16);
                    dP = (int) (Math.pow(dX, 2) + Math.pow(dY, 2));
                    if(dP < d) {
                        d = dP;
                        playerB = pB.getName();
                    }
                }
            }
        }
        tA.getPlayer(playerA).madeOwnGoal();
        try {
            tB.getPlayer(playerB).madeGoal(false);
        } catch(Exception e) {
            // There is an odd/very slim chance that there is no suitable candidate to award the goal to. We cannot do anything in this case.
        }
        
        return playerB;
    }

    /*
     * Tools
     */
    /**
     * Fetches the current zone a player is in.
     * 
     * @param p Player of who to look up the zone for.
     * @return The current HockeyZone the player is in, or null if something bad happens.
     */
    private HockeyZone getZone(Player p) {
        if(p == null)
            return null;
        
        return HockeyZone.intToEnum(hockeyZones.getRegion(p));
    }
    
    /**
     * Fetches the current zone for a point.
     * 
     * @param p Point for which to look up the zone of.
     * @return The current HockeyZone the point belongs to, or null if something bad happens.
     */
    private HockeyZone getZone(Point p) {
        if(p == null)
            return null;
        
        return HockeyZone.intToEnum(hockeyZones.getRegion(p.x / 16, p.y / 16));
    }
    
    /**
     * Determines the MVP of the match
     * 
     * @return name of the MVP
     */
    private String getMVP() {
        //TODO update this to get score from players
        String mvp;
        int highestRating = 0;
        
        mvp = "";

        for (HockeyPlayer p : team0.players.values()) {
            if (highestRating < p.getTotalRating()) {
                highestRating = p.getTotalRating();
                mvp = p.getName();
            }
        }
        
        for (HockeyPlayer p : team1.players.values()) {
            if (highestRating < p.getTotalRating()) {
                highestRating = p.getTotalRating();
                mvp = p.getName();
            }
        }

        return mvp;
    }

    /**
     * Check if there are enough captains to start the game
     */
    private void checkIfEnoughCaps() {
        if (currentState == HockeyState.WAITING_FOR_CAPS) {
            if (team0.hasCaptain() && team1.hasCaptain()) {
                startAddingPlayers();
            }
        }
    }

    /**
     * Alerts players that a new game is starting
     * - Send alert to chats
     * - Send alert to subscribers
     * - Send alert to zone
     */
    private void newGameAlert(String name, String message) {

        String nameTag = " -" + m_botAction.getBotName();

        //Build generic message in one is not passed
        if (message == null || message.isEmpty()) {
            message = "A game of hockey is starting! Type ?go hockey to play.";
        } else if (message.toLowerCase().contains("?go")) {
            m_botAction.sendPrivateMessage(name, "Please do not include ?go base in the zoner as I will add this for you automatically.");
            return;
        } else if (message.toLowerCase().contains("-" + name.toLowerCase())) {
            m_botAction.sendPrivateMessage(name, "Please do not include your name in the zoner as I will provide mine automatically.");
            return;
        } else {
            message += " ?go " + m_botAction.getArenaName();
        }

        //Alert Chats
        for (int i = 1; i < 11; i++) {
            m_botAction.sendChatMessage(i, message + nameTag);
        }

        //Alert Subscribers
        if (name == null && listAlert.size() > 0) {
            for (int i = 0; i < listAlert.size(); i++) {
                m_botAction.sendSmartPrivateMessage(listAlert.get(i), message);
            }
        }

        //Alert zoner, (max once every ZONER_WAIT_TIME (minutes))
        if ((allowZoner() && config.getAllowZoner()) || (allowManualZoner() && !config.getAllowAutoCaps())) {
            m_botAction.sendZoneMessage(message + nameTag, Tools.Sound.BEEP2);
            zonerTimestamp = System.currentTimeMillis();
            manualZonerTimestamp = zonerTimestamp;
        }
    }

    /**
     * Returns if a zoner can be send or not
     * @return True if a zoner can be send, else false
     */
    private boolean allowZoner() {
        boolean bool;
        if ((System.currentTimeMillis() - zonerTimestamp)
                <= (ZONER_WAIT_TIME * Tools.TimeInMillis.MINUTE)) {
            bool = false;
        } else {
            bool = true;
        }
        return bool;
    }

    /**
     * Returns if a zoner can be send or not
     * @return True if a zoner can be send, else false
     */
    private boolean allowManualZoner() {
        boolean bool;
        if ((System.currentTimeMillis() - manualZonerTimestamp)
                <= (10 * Tools.TimeInMillis.MINUTE)) {
            bool = false;
        } else {
            bool = true;
        }
        return bool;
    }

    /**
     * Returns the score in form of a String
     * @return game score
     */
    private String score() {
        return team0.getName() + " (" + team0.getScore() + ") - "
                + team1.getName() + " (" + team1.getScore() + ")";
    }

    /**
     * Checks if name was a captain on one of the teams and notifies the team of the leave
     *
     * @param name name of the player that left the game and could be captain
     */
    private void checkCaptainLeft(String name) {
        if (team0.getCaptainName().equalsIgnoreCase(name)) {
            if (currentState != HockeyState.WAITING_FOR_CAPS) {
                team0.captainLeftArena();
            } else {
                team0.captainLeft();
            }
        }

        if (team1.getCaptainName().equalsIgnoreCase(name)) {
            if (currentState != HockeyState.WAITING_FOR_CAPS) {
                team1.captainLeftArena();
            } else {
                team1.captainLeft();
            }
        }
    }

    /** 
     * Sends the captain list to the player 
     * 
     * @param name Who to send the list to.
     */
    private void sendCaptainList(String name) {
        m_botAction.sendPrivateMessage(name, team0.getCaptainName() + " is captain of " + team0.getName() + ".");
        m_botAction.sendPrivateMessage(name, team1.getCaptainName() + " is captain of " + team1.getName() + ".");
    }

    /**
     * Puts a player on a frequency if not playing
     *
     * @param name name of the player that should be put on a frequency
     */
    private void putOnFreq(String name) {
        if (listNotplaying.contains(name)) {
            m_botAction.setFreq(name, FREQ_NOTPLAYING);
            m_botAction.sendPrivateMessage(name, "You are on the !notplaying-list, "
                    + "captains are unable to sub or put you in. "
                    + "Message me with !notplaying again to get you off this list.");
            return;
        }
    }

    /**
     * Sends a welcome message with status info to the player
     *
     * @param name Name of the player that should receive the welcome message
     */
    private void sendWelcomeMessage(String name) {
        m_botAction.sendPrivateMessage(name, "Welcome to Hockey.");
        cmd_status(name);    //Sends status info to the player
    }

    /**
     * Handles FrequencyChange event and FrequencyShipChange event
     * - Checks if the player has lagged out
     * - Checks if the player is allowed in
     *
     * @param name Name of the player
     * @param frequency Frequency of the player
     * @param ship Ship type of the player
     */
    private void checkFCandFSC(String name, int frequency, int ship) {
        checkLagout(name, ship);  //Check if the player has lagged out
        checkPlayer(name, frequency, ship);  //Check if the player is allowed in
    }

    /**
     * Checks if a player has lagged out
     * - Check if the player is on one of the teams
     * - Check if the player is in spectator mode
     * - Check if the player is a player on the team
     *
     * @param name Name of the player
     * @param frequency Frequency of the player
     * @param ship Ship type of the player
     */
    private void checkLagout(String name, int ship) {
        HockeyTeam t;

        t = getTeam(name);  //Retrieve team

        //Check if the player is in one of the teams, if not exit method
        if (t == null) {
            return;
        }

        //Check if player is in spectator mode, if not exit method
        if (ship != Tools.Ship.SPECTATOR) {
            return;
        }

        //Check if the player is in the team, if not it could just be a captain
        if (!t.isPlayer(name)) {
            return;
        }

        //Check if player is already listed as lagged out/sub/out
        if (!t.isPlaying(name)) {
            return;
        }

        t.lagout(name); //Notify the team that a player lagged out
        try {
            if (t.offside.contains(name)) {
                t.offside.remove(name);
            }
            if (t.fCrease.contains(name)) {
                t.offside.remove(name);
            }
            if (t.dCrease.contains(name)) {
                t.offside.remove(name);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Checks if a player is allowed in or not
     *
     * @param name Name of the player
     * @param frequency Frequency of the player
     * @param ship Ship type of the player
     */
    private void checkPlayer(String name, int frequency, int ship) {
        HockeyTeam t;

        //Check if the player is in a ship (atleast not in spectating mode)
        if (ship == Tools.Ship.SPECTATOR) {
            return;
        }

        t = getTeam(name); //Retrieve team, null if not found

        //Check if the player is on one of the teams, if not spectate the player
        if (t == null) {
            m_botAction.specWithoutLock(name);
            return;
        }

        if (t.getPlayerState(name) != HockeyPlayer.IN) {
            m_botAction.specWithoutLock(name);
        }
    }

    /** Determines who's turn it is to pick a player*/
    private void determineTurn() {
        if (team0.getSizeIN() <= team1.getSizeIN()) {
            if (team0.getSizeIN() != config.getMaxPlayers()) {
                m_botAction.sendArenaMessage(team0.captainName + " pick a player!", Tools.Sound.BEEP2);
                team1.picked();
                team0.setTurn();
            }
        } else if (team1.getSizeIN() < team0.getSizeIN()) {
            if (team1.getSizeIN() != config.getMaxPlayers()) {
                m_botAction.sendArenaMessage(team1.captainName + " pick a player!", Tools.Sound.BEEP2);
                team0.picked();
                team1.setTurn();
            }
        }
    }

    /**
     * Returns HockeyTeam of the player with "name"
     *
     * @param name name of the player
     * @return HockeyTeam of the player, null if not on any team
     */
    private HockeyTeam getTeam(String name) {
        HockeyTeam t;

        if (team0.isOnTeam(name)) {
            t = team0;
        } else if (team1.isOnTeam(name)) {
            t = team1;
        } else {
            t = null;
        }

        return t;
    }

    /**
     * Returns the opposite team of the player
     *
     * @param name name of the player
     * @param override override number 0 for Freq 0, 1 for Freq 1, -1 for normal
     * @return HockeyTeam, null if player doesn't belong to any team
     */
    private HockeyTeam getOtherTeam(String name, int override) {
        if (override == -1) {
            if (team0.isOnTeam(name)) {
                return team1;
            } else if (team1.isOnTeam(name)) {
                return team0;
            }
        } else if (override == 0) {
            return team1;
        } else if (override == 1) {
            return team0;
        }

        return null;
    }

    /**
     * Returns the opposite team according to HockeyTeam
     *
     * @param t current team
     * @return other team
     */
    private HockeyTeam getOtherTeam(HockeyTeam t) {
        if (t.getFrequency() == 0) {
            return team1;
        } else if (t.getFrequency() == 1) {
            return team0;
        } else {
            return null;
        }
    }

    /**
     * Returns HockeyTeam of the player with "name" or according to the override
     *
     * @param name name of the player
     * @param override override number 0 for Freq 0, 1 for Freq 1, -1 for normal
     * @return HockeyTeam, null if player doesn't belong to any team
     */
    private HockeyTeam getTeam(String name, int override) {
        if (override == -1) {
            return getTeam(name);
        } else if (override == 0) {
            return team0;
        } else if (override == 1) {
            return team1;
        } else {
            return null;
        }
    }

    /**
     * Checks if the arena should be locked or not
     *
     * @param message Arena message
     */
    private void checkArenaLock(String message) {
        if (message.equals("Arena UNLOCKED") && lockArena)
            m_botAction.toggleLocked();
        else if (message.equals("Arena LOCKED") && !lockArena )
            m_botAction.toggleLocked();
    }

    /**
     * Checks if name is a captain on one of the teams
     * Returns true if true, else false
     *
     * @param name Name of the player that could be captain
     * @return true if name is captain, else false
     */
    private boolean isCaptain(String name) {
        boolean isCaptain;
        HockeyTeam t;

        isCaptain = false;
        t = getTeam(name);

        if (t != null) {
            if (t.getCaptainName().equalsIgnoreCase(name)) {
                isCaptain = true;
            }
        }

        return isCaptain;
    }

    /**
     * Checks if lineups are ok
     */
    private void checkLineup() {
        int sizeTeam0, sizeTeam1;
        
        sizeTeam0 = team0.getSizeIN();
        sizeTeam1 = team1.getSizeIN();
        
        // Extended lineup check
        if(sizeTeam0 < config.getMinPlayers()) {
            m_botAction.sendArenaMessage("Freq 0 does not have enough players. " +
                    "(Current: " + sizeTeam0 + "; Needed: " + 
                    config.getMinPlayers() + ")");
        } else if(sizeTeam1 < config.getMinPlayers()) {
            m_botAction.sendArenaMessage("Freq 1 does not have enough players. " +
                    "(Current: " + sizeTeam1 + "; Needed: " + 
                    config.getMinPlayers() + ")");
        } else if(sizeTeam0 > config.getMaxPlayers()) {
            m_botAction.sendArenaMessage("Freq 0 has too many players. " +
                    "(Current: " + sizeTeam0 + "; Maximum: " + 
                    config.getMaxPlayers() + ")");
        } else if(sizeTeam1 > config.getMaxPlayers()) {
            m_botAction.sendArenaMessage("Freq 1 has too many players. " +
                    "(Current: " + sizeTeam1 + "; Maximum: " + 
                    config.getMaxPlayers() + ")");
        } else if(sizeTeam0 != sizeTeam1) {
            m_botAction.sendArenaMessage("Teams are unequal. " +
                    "(Freq 0: " + sizeTeam0 + "; Freq 1: " + sizeTeam1 + ")");
        } else {
            currentState = HockeyState.FACE_OFF;
            m_botAction.sendArenaMessage("Lineups are ok! Game will start in 30 seconds!", Tools.Sound.CROWD_OOO);
            m_botAction.sendArenaMessage("Freq 0 <---  |  ---> Freq 1");
            team0.timeout = maxTimeouts;
            team1.timeout = maxTimeouts;
            startFaceOff();
            return;
        }
            
        // Code will only go here if the lineups are not ok, otherwise, the return above kicks in.
        m_botAction.sendArenaMessage("Lineups are NOT ok! Status of teams set to NOT ready. " 
                + "Captains, fix your lineups and try again.", Tools.Sound.CROWD_GEE);
        //startWaitingForCaps();
        team0.ready();
        team1.ready();
    }

    /**
     * Resets variables to their default value
     */
    private void reset() {
        gameTime = 0;
        team0.resetVariables();
        team1.resetVariables();
        clearObjects();
        clearTeamNameObjects();
        puck.clear();

        setSpecAndFreq();
    }

    /**
     * Locks arena
     */
    private void lockArena() {
        lockArena = true;
        m_botAction.toggleLocked();
    }

    /**
     * Unlocks arena
     */
    private void unlockArena() {
        lockArena = false;
        m_botAction.toggleLocked();
    }

    /**
     * Locks all doors in the arena
     */
    private void lockDoors() {
        m_botAction.setDoors(255);
    }

    /**
     * Sets everyone in spec and on right frequency
     */
    private void setSpecAndFreq() {
        for (Iterator<Player> it = m_botAction.getPlayerIterator(); it.hasNext();) {
            Player i = it.next();
            int id = i.getPlayerID();
            int freq = i.getFrequency();
            if (m_botAction.getPlayerName(id) == m_botAction.getBotName()){
                return;
            } else {
            if (i.getShipType() != Tools.Ship.SPECTATOR) {
                m_botAction.specWithoutLock(id);
            }
            if (listNotplaying.contains(i.getPlayerName()) && freq != FREQ_NOTPLAYING) {
                m_botAction.setFreq(id, FREQ_NOTPLAYING);
            } else if (freq != FREQ_SPEC && !listNotplaying.contains(i.getPlayerName())) {
                m_botAction.setShip(id, 1);
                m_botAction.specWithoutLock(id);
            }
        }
        }
    }
    
    private void clearTeamNameObjects() {
        //"FREQ0" team name
        m_botAction.hideObject(350);
        m_botAction.hideObject(471);
        m_botAction.hideObject(342);
        m_botAction.hideObject(463);
        m_botAction.hideObject(564);
        //"FREQ1" team name
        m_botAction.hideObject(355);
        m_botAction.hideObject(476);
        m_botAction.hideObject(347);
        m_botAction.hideObject(468);
        m_botAction.hideObject(579);
    }
    
    private void showTeamNameObjects() {
        //"FREQ0" team name
        m_botAction.showObject(350);
        m_botAction.showObject(471);
        m_botAction.showObject(342);
        m_botAction.showObject(463);
        m_botAction.showObject(564);
        //"FREQ1" team name
        m_botAction.showObject(355);
        m_botAction.showObject(476);
        m_botAction.showObject(347);
        m_botAction.showObject(468);
        m_botAction.showObject(579);
    }
    
    private void pmShowTeamNameObjects(int pID) {
        //"FREQ0" team name
        m_botAction.showObjectForPlayer(pID,350);
        m_botAction.showObjectForPlayer(pID,471);
        m_botAction.showObjectForPlayer(pID,342);
        m_botAction.showObjectForPlayer(pID,463);
        m_botAction.showObjectForPlayer(pID,564);
        //"FREQ1" team name
        m_botAction.showObjectForPlayer(pID,355);
        m_botAction.showObjectForPlayer(pID,476);
        m_botAction.showObjectForPlayer(pID,347);
        m_botAction.showObjectForPlayer(pID,468);
        m_botAction.showObjectForPlayer(pID,579);
    }
    
    private void clearObjects() {
        //0-7 for freq 0
        m_botAction.hideObject(100);
        m_botAction.hideObject(101);
        m_botAction.hideObject(102);
        m_botAction.hideObject(103);
        m_botAction.hideObject(104);
        m_botAction.hideObject(105);
        m_botAction.hideObject(106);
        m_botAction.hideObject(107);
        //0-7 for freq 1
        m_botAction.hideObject(200);
        m_botAction.hideObject(201);
        m_botAction.hideObject(202);
        m_botAction.hideObject(203);
        m_botAction.hideObject(204);
        m_botAction.hideObject(205);
        m_botAction.hideObject(206);
        m_botAction.hideObject(207);
        
    }
    
    private void newPlayerUpdateScoreBoard(int pID) {
        int team0Score = team0.getScore();
        int team1Score = team1.getScore();
        
        pmShowTeamNameObjects(pID);
        m_botAction.showObjectForPlayer(pID, 100 + team0Score);
        m_botAction.showObjectForPlayer(pID, 200 + team1Score);
    }
    
    private void updateScoreBoard() {
        int team0Score = team0.getScore();
        int team1Score = team1.getScore();
        clearObjects();
        m_botAction.showObject(100 + team0Score);
        m_botAction.showObject(200 + team1Score);
    }

    /**
     * Displays the scores
     */
    private void displayScores() {
        //TODO fix score display
        ArrayList<String> spam = new ArrayList<String>();
        spam.add("+----------------+----------------+");
        spam.add("|     Freq 0     |     Freq 1     |");
        spam.add("|       " + team0.getScore() + "        |       "
                + team1.getScore() + "        |");
        spam.add("+----------------+----------------+");

        m_botAction.arenaMessageSpam(spam.toArray(new String[spam.size()]));
    }

    /* Game classes */
    private class HockeyCaptain {

        private String captainName;
        private long startTime;
        private long endTime;

        private HockeyCaptain(String name) {
            captainName = name;
            endTime = -1;
        }

        private boolean hasEnded() {
            if (endTime != -1) {
                return true;
            } else {
                return false;
            }
        }
    }

    private class HockeyConfig {

        private BotSettings botSettings;
        private ArrayList<Integer> goalieShips;
        private String chats;
        private String arena;
        private int maxLagouts;
        private int maxSubs;
        private int maxPlayers;
        private int minPlayers;
        private int defaultShipType;
        private int[] maxShips;
        private GameMode gameMode;
        private int gameModeTarget;
        private boolean announceShipType;
        private boolean allowAutoCaps;
        private boolean allowZoner;
        private boolean allowVote;        
        //#Coordinate for puck drop
        private int puckDropX;
        private int puckDropY;
        //Coordinates for puck during timeout.
        private int puckToX;
        private int puckToY;
        //Coordinate for penalty boxes
        private int team0PenX;
        private int team0PenY;
        private int team1PenX;
        private int team1PenY;
        private int team0ExtX;
        private int team0ExtY;
        private int team1ExtX;
        private int team1ExtY;
        private int penaltyTime;

        /** Class constructor */
        private HockeyConfig() {
            botSettings = m_botAction.getBotSettings();
            goalieShips = new ArrayList<Integer>();
            int tmpAnnounceShipCounter;
            String[] maxShipsString;
            String[] goalieShipsString;

            //Arena
            arena = botSettings.getString("Arena");

            //Allow final review voting
            allowVote = (botSettings.getInt("AllowVote") == 1);
            
            //Allow Zoner
            allowZoner = (botSettings.getInt("SendZoner") == 1);

            //Allow automation of captains
            allowAutoCaps = (botSettings.getInt("AllowAuto") == 1);

            //Chats
            chats = botSettings.getString("Chats");

            //Default Ship Type
            defaultShipType = botSettings.getInt("DefaultShipType");

            //Max Lagouts
            maxLagouts = botSettings.getInt("MaxLagouts");

            //Min Players
            minPlayers = botSettings.getInt("MinPlayers");
            
            //Max Players
            maxPlayers = botSettings.getInt("MaxPlayers");

            //Max Ships
            maxShips = new int[9];
            maxShipsString = botSettings.getString("MaxShips").split(",");
            tmpAnnounceShipCounter = 0; //Counter for Announce Ship Type
            for (int i = Tools.Ship.WARBIRD; i <= maxShipsString.length; i++) {
                maxShips[i] = Integer.parseInt(maxShipsString[i - 1]);
                if (maxShips[i] != 0) {
                    tmpAnnounceShipCounter++;
                }
            }

            //Goalie ships
            goalieShipsString = botSettings.getNonNullString("GoalieShips").split(",");
            if(goalieShipsString.length > 0) {
                for(String sTemp : goalieShipsString) {
                    try {
                        Integer shipNo = Integer.parseInt(sTemp);
                        // If the ship is a valid shiptype, add it to the list.
                        if(shipNo >= Tools.Ship.WARBIRD && shipNo <= Tools.Ship.SHARK)
                            goalieShips.add(shipNo);
                    } catch (Exception e) {
                        // Entry wasn't a valid number. Skip it.
                    }
                }
            }
            // If the above if didn't fire or no valid goalie ships were found, fall back to the default value.
            if(goalieShips.isEmpty()) {
                // Default value when nothing is found.
                goalieShips.add(Tools.Ship.SHARK);
                // Put a warning in the logs to signal the incomplete/incorrect cfg file.
                Tools.printLog("Hockeybot.cfg has an invalid or missing entry: GoalieShips. Falling back to default value.");
            }
            
            //Max Amount of Substitutes Allowed
            maxSubs = botSettings.getInt("MaxSubs");

            //Announce Ship Type
            if (tmpAnnounceShipCounter > 1) {
                announceShipType = true;
            } else {
                announceShipType = false;
            }

            // Gamemode
            if(botSettings.getInt("GameMode") == 1) {
                gameMode = GameMode.TIMED;
            } else {
                gameMode = GameMode.GOALS;
            }
            gameModeTarget = botSettings.getInt("GameModeTarget");
            
            //penalty time
            penaltyTime = botSettings.getInt("PenaltyTime");
            
            // Zone and coordinate configuration
            if(!initZones()) {                      
                //Sorry folks, can't do anything if the zones fail to load.
                m_botAction.die("Unable to load zones.");
            }
        }

        /**
         * Loads in and sets up the zones.
         */
        private boolean initZones() {
            BotSettings cfg;
            hockeyZones = new MapRegions();
            
            hockeyZones.clearRegions();
            
            try {
                hockeyZones.loadRegionImage("hockey.png");
            } catch (IOException e) {
                e.printStackTrace();
                Tools.printLog("ERROR: Failed to load zone image file hockey.png.");
                return false;
            }
            try {
                cfg = hockeyZones.loadRegionCfg("hockey.cfg");
            } catch (IOException e) {
                e.printStackTrace();
                Tools.printLog("ERROR: Failed to load zone config file hockey.cfg.");
                return false;
            }
            
            //Coordinate for puck drop
            puckDropX = cfg.getInt("PuckDropX");
            puckDropY = cfg.getInt("PuckDropY");
            //Coordinates for puck during timeout
            puckToX = cfg.getInt("PuckTimeoutX");
            puckToY = cfg.getInt("PuckTimeoutY");

            //Coordinate for penalty boxes
            team0PenX = cfg.getInt("Team0PenX");
            team0PenY = cfg.getInt("Team0PenY");
            team1PenX = cfg.getInt("Team1PenX");
            team1PenY = cfg.getInt("Team1PenY");

            team0ExtX = cfg.getInt("Team0ExtX");
            team0ExtY = cfg.getInt("Team0ExtY");
            team1ExtX = cfg.getInt("Team1ExtX");
            team1ExtY = cfg.getInt("Team1ExtY");
            
            return true;
        }
        
        /**
         * Returns whether to announce the ship type
         *
         * @return true if ship type should be announced, else false
         */
        private boolean announceShipType() {
            return announceShipType;
        }

        /**
         * Returns max amount of ships of ship type
         *
         * @param shipType type of ship
         * @return max amount of ships of ship type
         */
        private int getMaxShips(int shipType) {
            return maxShips[shipType];
        }

        /**
         * Returns the default ship type
         *
         * @return default ship type
         */
        private int getDefaultShipType() {
            return defaultShipType;
        }

        /**
         * Returns string with chats
         *
         * @return String with all the chats
         */
        private String getChats() {
            return chats;
        }

        /**
         * Returns the amount of maximum lag outs
         *
         * @return amount of maximum lag outs, -1 if unlimited
         */
        private int getMaxLagouts() {
            return maxLagouts;
        }

        /**
         * Returns the arena name
         *
         * @return arena name
         */
        private String getArena() {
            return arena;
        }

        /**
         * Returns the maximum amount of players allowed
         *
         * @return maximum amount of players allowed
         */
        private int getMaxPlayers() {
            return maxPlayers;
        }

        /**
         * Returns true if auto caps is on, else false
         *
         * @return Returns true if auto caps is on, else false
         */
        private boolean getAllowAutoCaps() {
            return allowAutoCaps;
        }

        /**
         * Returns if a zoner can be send
         *
         * @return true if a zoner can be send, else false
         */
        private boolean getAllowZoner() {
            return allowZoner;
        }

        /**
         * Returns minimal amount of players needed in
         *
         * @return minimal amount of players
         */
        private int getMinPlayers() {
            return minPlayers;
        }

        /**
         * Returns maximum allowed substitutes
         *
         * @return maximum allowed substitutes
         */
        private int getMaxSubs() {
            return maxSubs;
        }

        /**
         * @return the puckDropX
         */
        public int getPuckDropX() {
            return puckDropX;
        }

        /**
         * @return the puckDropY
         */
        public int getPuckDropY() {
            return puckDropY;
        }

        /**
         * @return X-coordinate of timeout location of the puck
         */
        public int getPuckToX() {
            return puckToX;
        }

        /**
         * @return Y-coordinate of timeout location of the puck
         */
        public int getPuckToY() {
            return puckToY;
        }
        
        /**
         * @return the Team0PenX
         */
        public int getTeam0PenX() {
            return team0PenX;
        }

        /**
         * @return the Team0PenY
         */
        public int getTeam0PenY() {
            return team0PenY;
        }

        /**
         * @return the Team1PenX
         */
        public int getTeam1PenX() {
            return team1PenX;
        }

        /**
         * @return the Team1PenY
         */
        public int getTeam1PenY() {
            return team1PenY;
        }

        /**
         * @return the Team0ExtX
         */
        public int getTeam0ExtX() {
            return team0ExtX;
        }

        /**
         * @return the Team0ExtY
         */
        public int getTeam0ExtY() {
            return team0ExtY;
        }

        /**
         * @return the Team1ExtX
         */
        public int getTeam1ExtX() {
            return team1ExtX;
        }

        /**
         * @return the Team1ExtY
         */
        public int getTeam1ExtY() {
            return team1ExtY;
        }

        /**
         * Returns the default gamemode, which can be to a target number of goals or timed.
         * @return {@link GameMode} Current gamemode
         */
        public GameMode getGameMode() {
            return gameMode;
        }
        
        /**
         * @return The target minutes or goals to which the game is played.
         */
        public int getGameTarget() {
            return gameModeTarget;
        }
        
        /**
         * @return the penaltyTime
         */
        public int getPenaltyTime() {
            return penaltyTime;
        }
        
        /**
         * Sets the {@link GameMode gamemode} to a specific type and target.
         * 
         * @param gm A {@link GameMode}. (Either {@link GameMode#GOALS} or {@link GameMode#TIMED}) 
         * @param target Target score or time.
         */
        public void setGameMode(GameMode gm, int target) {
            gameMode = gm;
            gameModeTarget = target;
        }
        
        public boolean isGoalieShip(int shipType) {
            return goalieShips.contains(shipType);
        }
    }

    private class HockeyPlayer {

        private String p_name;
        private int[][] p_ship;
        private int p_currentShip;
        private int p_state;
        private long p_timestampLagout;
        private long p_timestampChange;
        private long p_timestampSub;
        private long p_timestampSwitch;
        private int p_lagouts;
        private int p_frequency;
        private long p_lastPositionUpdate;
        private int p_userID;

        /* Constants */
        private final static int SCORE = 0;
        private final static int DEATHS = 1;
        private final static int USED = 24;
        private final static int PLAY_TIME = 25;
        //Ship states
        private static final int IN = 0;
        private static final int LAGOUT = 1;
        private static final int OUT_SUBABLE = 2;
        private static final int SUBBED = 3;
        private static final int PENALTY = 4;
        //Static variables
        private static final int CHANGE_WAIT_TIME = 15; //In seconds
        private static final int SWITCH_WAIT_TIME = 15; //In seconds
        private static final int SUB_WAIT_TIME = 15; //In seconds
        private static final int LAGOUT_TIME = 15 * Tools.TimeInMillis.SECOND;  //In seconds
        //penalty handling
        private HockeyPenalty penalty = HockeyPenalty.NONE;
        private long warnTimestamp = 0;     //timestamp
        private long penaltyTimestamp = 0;  //in gametime which increments from 0 each second of gameplay
        private int penalties = 0;          //penalites recieved
        //TODO Improve size of deathTracker to Integer or Short instead of Long. Possibly after more timetrackers have been added.
        private TreeMap<Long,String> deathTracker;  //Used for detecting respawnkilling
        //stats
        private int saves = 0;              //saves in defense crease
        private int steals = 0;             //puck steals
        private int assists = 0;            //clean goals assisted
        private int goals = 0;              //clean goals made
        private int shotsOnGoal = 0;        //Intercepted shots at goal.
        private int ownGoals = 0;           //Own goals made
        private int turnovers = 0;          //Allowed the puck to be turned over (go to the other team)

        /** Class constructor */
        private HockeyPlayer(String player, int shipType, int frequency) {
            p_ship = new int[9][26];
            p_name = player;
            p_currentShip = shipType;
            p_frequency = frequency;
            p_lagouts = 0;

            m_botAction.scoreReset(p_name);
            addPlayer();

            p_ship[p_currentShip][USED] = 1;

            p_timestampLagout = 0;
            p_timestampChange = 0;
            p_timestampSub = 0;
            p_timestampSwitch = 0;
            
            deathTracker = new TreeMap<Long,String>();
        }

        /**
         * Adds a player into the game
         * - Resets out of border time
         */
        private void addPlayer() {
            p_state = IN;
            p_ship[p_currentShip][USED] = 1;

            if (m_botAction.getPlayer(p_name) == null) {
                return;
            }

            m_botAction.setShip(p_name, p_currentShip);
            m_botAction.setFreq(p_name, p_frequency);

            if (p_frequency == 0) {
                m_botAction.warpTo(p_name, config.getTeam0ExtX() / 16, config.getTeam0ExtY() / 16);
            } else {
                m_botAction.warpTo(p_name, config.getTeam1ExtX() / 16, config.getTeam1ExtY() / 16);
            }
        }

        public void madeSave() {
            this.saves++;
        }

        public void madeSteal() {
            this.steals++;
        }

        public void madeAssist() {
            this.assists++;
        }

        /**
         * Adds a goal to the player stats.
         * If the goal was made by the player himself, adds one to the shotsOnGoal as well.
         *  
         * @param addShotOnGoal Boolean. This needs to be true if there is also the need to add one to the shotsOnGoal stat. 
         * It should be false if the goal was awarded to the player through an own goal by the opponent.
         * (In this case, the shot on goal was either already counted when the puck was saved, or the person did not
         * make a shot at the goal at all.)
         */
        public void madeGoal(boolean addShotOnGoal) {
            this.goals++;
            if(addShotOnGoal)
                shotOnGoal();
            if (this.goals == 3) {
                m_botAction.sendArenaMessage("HAT TRICK by " + this.getName() + "!", 19);
            }
        }
        
        public void shotOnGoal() {
            this.shotsOnGoal++;
        }
        
        public void madeOwnGoal() {
            this.ownGoals++;
        }

        public void madeTurnover() {
            this.turnovers++;
        }

        /**
         * Puts player IN in shiptype
         *
         * @param shipType ship type
         */
        private void putIN(int shipType) {
            p_currentShip = shipType;
            addPlayer();
        }

        /**
         * Returns the current ship state
         *
         * @return int current ship state
         */
        private int getCurrentState() {
            return p_state;
        }

        /**
         * Handles a lagout event
         * - Notes down the timestamp of the lagout
         * - Adds one to the lagout counter
         * - Adds a death if gametype is WBDUEL,JAVDUEL or SPIDDUEL
         * - Check if the player hits loss/deaths limit
         * - Check if the player is out due maximum of lagouts
         * - Tell the player how to get back in
         */
        private void lagout() {
            p_state = LAGOUT;
            p_timestampLagout = System.currentTimeMillis();

            if (currentState == HockeyState.GAME_IN_PROGRESS) {
                p_lagouts++;

                //Notify the team of the lagout
                m_botAction.sendOpposingTeamMessageByFrequency(p_frequency, p_name + " lagged out or specced.");

                //Check if player is out due maximum of lagouts
                if ((config.getMaxLagouts() != -1) && p_lagouts >= config.getMaxLagouts()) {
                    //Extra check if player is not already set to OUT, due death limit (the +1 death thing)
                    if (p_state < PENALTY) {
                        out("lagout limit");
                    }
                }

                //Message player how to get back in if he is not out
                if (p_state != PENALTY) {
                    m_botAction.sendPrivateMessage(p_name, "PM me \"!lagout\" to get back in.");
                }
            }
        }

        /**
         * This method handles a player going out
         * - Changes player state
         * - Spectates the player
         * - Notifies the arena
         * - Change state according to reason
         *
         * @param reason Reason why the player went out
         */
        private void out(String reason) {
            String arenaMessage = "";

            p_state = PENALTY;

            //Spectate the player if he is in the arena
            if (m_botAction.getPlayer(p_name) != null) {
                m_botAction.specWithoutLock(p_name);
                m_botAction.setFreq(p_name, p_frequency);
            }

            //Notify arena and change state if player is still subable
            if (reason.equals("out not playing")) {
                arenaMessage = p_name + " is out, (set himself to notplaying). NOTICE: Player is still subable.";
                p_state = OUT_SUBABLE;
            }

            m_botAction.sendArenaMessage(arenaMessage);
        }

        /**
         * Timestamps last position received
         */
        private void timestampLastPosition() {
            p_lastPositionUpdate = System.currentTimeMillis();
        }

        /**
         * Returns the name of this player
         *
         * @return Returns the name of this player
         */
        private String getName() {
            return p_name;
        }

        /**
         * Returns whether a player is out or not
         *
         * @return Returns true if the player is out, else false
         */
        private boolean isOut() {
            if (p_state >= OUT_SUBABLE) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * Returns current type of ship
         *
         * @return Returns current type of ship
         */
        private int getCurrentShipType() {
            return p_currentShip;
        }

        /**
         * Returns whether a !change is allowed on this player
         *
         * @return true if change is allowed, else false
         */
        private boolean isChangeAllowed() {
            if ((System.currentTimeMillis() - p_timestampChange) <= (CHANGE_WAIT_TIME * Tools.TimeInMillis.SECOND)) {
                return false;
            } else {
                return true;
            }
        }

        /**
         * Returns time in seconds until next change
         *
         * @return Returns time in seconds until next change
         */
        private long getTimeUntilNextChange() {
            return (CHANGE_WAIT_TIME - ((System.currentTimeMillis() - p_timestampChange) / Tools.TimeInMillis.SECOND));
        }

        /**
         * Returns whether a !sub is allowed on this player
         *
         * @return true if sub is allowed, else false
         */
        private boolean isSubAllowed() {
            if ((System.currentTimeMillis() - p_timestampSub) <= (SUB_WAIT_TIME * Tools.TimeInMillis.SECOND)) {
                return false;
            } else {
                return true;
            }
        }

        /**
         * Returns time in seconds until next sub
         *
         * @return Returns time in seconds until next sub
         */
        private long getTimeUntilNextSub() {
            return (SUB_WAIT_TIME - ((System.currentTimeMillis() - p_timestampSub) / Tools.TimeInMillis.SECOND));
        }

        /**
         * Returns whether a !switch is allowed on this player
         *
         * @return true if switch is allowed, else false
         */
        private boolean isSwitchAllowed() {
            if ((System.currentTimeMillis() - p_timestampSwitch) <= (SWITCH_WAIT_TIME * Tools.TimeInMillis.SECOND)) {
                return false;
            } else {
                return true;
            }
        }

        /**
         * Returns time in seconds until next switch
         *
         * @return Returns time in seconds until next switch
         */
        private long getTimeUntilNextSwitch() {
            return (SWITCH_WAIT_TIME - ((System.currentTimeMillis() - p_timestampSwitch) / Tools.TimeInMillis.SECOND));
        }

        /**
         * Changes player to shipType
         *
         * @param shipType Shiptype to change to
         */
        private void change(int shipType) {
            m_botAction.sendArenaMessage(p_name + " changed from " + Tools.shipName(p_currentShip)
                    + " to " + Tools.shipName(shipType));
            p_currentShip = shipType;

            p_ship[p_currentShip][USED] = 1;

            if (m_botAction.getPlayer(p_name) != null) {
                HockeyTeam t = getTeam(p_name);
                // Remove player as goalie, if he was it.
                if(t != null && t.isGoalie(p_name))
                    t.removeGoalie();
                
                m_botAction.setShip(p_name, shipType);
                // Add player as goalie, if he becomes it.
                if(t != null && config.isGoalieShip(shipType))
                    t.goalieName = p_name;
                
                if (p_frequency == 0) {
                    m_botAction.warpTo(p_name, config.getTeam0ExtX() / 16, config.getTeam0ExtY() / 16);
                } else {
                    m_botAction.warpTo(p_name, config.getTeam1ExtX() / 16, config.getTeam1ExtY() / 16);
                }
            }

            p_timestampChange = System.currentTimeMillis();
        }

        /**
         * Returns lagout time
         *
         * @return lagout time
         */
        private long getLagoutTimestamp() {
            return p_timestampLagout;
        }

        /**
         * Returns a player into the game
         */
        private void lagin() {
            m_botAction.sendOpposingTeamMessageByFrequency(p_frequency, p_name + " returned from lagout.");
            addPlayer();
        }

        /**
         * Returns status
         *
         * @return status
         */
        private String getStatus() {
            switch (p_state) {
                case (IN):
                    return "IN";
                case (LAGOUT):
                    return "LAGGED OUT";
                case (SUBBED):
                    return "SUBSTITUTED";
                case (PENALTY):
                    return "PENALTY";
                case (OUT_SUBABLE):
                    return "OUT (still substitutable)";
                default:
                    return "";
            }
        }

        /**
         * Subs the player OUT
         */
        private void sub() {
            p_state = SUBBED;
            if (m_botAction.getPlayer(p_name) != null) {
                m_botAction.specWithoutLock(p_name);
                if (!listNotplaying.contains(p_name)) {
                    m_botAction.setFreq(p_name, p_frequency);
                }
            }

            p_timestampSub = System.currentTimeMillis();
        }

        /**
         * Adds a second to playtime
         */
        private void addPlayTime() {
            if (p_state == IN) {
                p_ship[p_currentShip][PLAY_TIME]++;
            }
        }

        /**
         * Returns lagouts
         *
         * @return lagouts
         */
        private int getLagouts() {
            return p_lagouts;
        }

        /**
         * Switches player
         * - puts the player in
         * - timestamps the event
         */
        private void switchPlayer() {
            addPlayer();
            p_timestampSwitch = System.currentTimeMillis();
        }

        /**
         * Tracks the last deaths of this player.
         * <p>
         * This function updates the TreeMap {@link #deathTracker deathTracker}.
         * <p>
         * In order to be remarked as spawnkilling, each next death must be within two seconds of the previous one.
         * In order to accomplish this, this function simply checks if the previous death was more or less than two seconds ago.
         * If this was more than two seconds ago, the old entries will become void and are erased.
         * In either case, the new death is added.
         * <p>
         * When the old entries are found to still be useful, deathTracker will be used to determine how often the current
         * killer has killed this player. Depending on how often the killer is found in deathTracker, the following value will be returned:
         * <ul>
         * <li>First kill: HockeyPenalty.NONE
         * <li>Second kill: HockeyPenalty.ILLEGAL_CHK_WARN
         * <li>Third or higher kill: HockeyPenalty.ILLEGAL_CHECK
         * </ul>
         * 
         * @param killer Name of the last killer
         * @return HockeyPenalty: NONE, ILLEGAL_CHK_WARN or ILLEGAL_CHECK
         */
        private HockeyPenalty trackDeaths(String killer) {
            Long time = System.currentTimeMillis() / 1000;
            
            if(deathTracker.isEmpty()) {
                // No entries in the deathTracker.
                deathTracker.put(time, killer);
                return HockeyPenalty.NONE;
            } else if(time - deathTracker.lastKey() > 2)  {
                // More than two seconds have passed since the last death. Reset the deathTracker.
                deathTracker.clear();
                deathTracker.put(time, killer);
                return HockeyPenalty.NONE;
            } else {
                // Less than two seconds have passed since the last death.
                // Check if the current killer is spawnkilling.
                int count = 0;
                
                deathTracker.put(System.currentTimeMillis(), killer);
                for(String s : deathTracker.values()) {
                    if(s.equals(killer))
                        count++;
                }
                
                if(count == 2) {
                    return HockeyPenalty.ILLEGAL_CHK_WARN;
                } else if(count >= 3) {
                    return HockeyPenalty.ILLEGAL_CHECK;
                } else {
                    return HockeyPenalty.NONE;
                }
            }
        }
        
        private void setPenalty(HockeyPenalty pen) {
            if (pen == HockeyPenalty.NONE) {
                this.penalty = pen;
                return;
            }

            this.penalty = pen;
            this.penaltyTimestamp = gameTime;
            if (team0.isPlayer(p_name)) {
                m_botAction.warpTo(p_name, config.getTeam0PenX() / 16, config.getTeam0PenY() / 16);
            } else {
                m_botAction.warpTo(p_name, config.getTeam1PenX() / 16, config.getTeam1PenY() / 16);
            }
            penalties++;
            m_botAction.sendPrivateMessage(p_name, "You recieved a penalty for "
                    + config.getPenaltyTime() + " seconds of game time.");
        }
        
        /**
         * Calculates the rating of a player
         * Used weights might require to be changed in the future
         * 
         * @return players rating
         */
        private int getTotalRating() {
            //TODO Improve this
            // Random formula
            return (goals * 5 + saves * 5 + assists * 3 + shotsOnGoal + steals
                    - penalties * 5 - ownGoals * 5 - turnovers);
        }
    }

    private class HockeyTeam {

        private boolean flag;
        private boolean turnToPick;
        private boolean ready;
        private int flagTime;
        private int timeout;
        private int frequency;
        private TreeMap<String, HockeyPlayer> players;
        private TreeMap<Short, HockeyCaptain> captains;
        private short captainsIndex;
        private String captainName;
        private String lastCaptainName;
        private String teamName;
        private long captainTimestamp;
        private int substitutesLeft;
        private int teamScore;
        private String goalieName;
        //penalties
        private Stack<String> offside;
        private Stack<String> dCrease;
        private Stack<String> fCrease;

        /** Class constructor */
        private HockeyTeam(int frequency) {
            this.frequency = frequency;
            this.teamName = "Freq " + frequency;

            players = new TreeMap<String, HockeyPlayer>();
            captains = new TreeMap<Short, HockeyCaptain>();

            offside = new Stack<String>();
            dCrease = new Stack<String>();
            fCrease = new Stack<String>();

            resetVariables();
        }

        /**
         * Resets all variables except frequency
         */
        private void resetVariables() {
            players.clear();
            flag = false;
            turnToPick = false;
            captainName = "[NONE]";
            lastCaptainName = "[NONE]";
            goalieName = "[NONE]";
            captains.clear();
            flagTime = 0;
            ready = false;
            substitutesLeft = config.getMaxSubs();
            captainsIndex = -1;
            teamScore = 0;
            
            try {
                offside.clear();
                dCrease.clear();
                fCrease.clear();
            } catch (Exception e) {
            }
        }

        private void clearUnsetPenalties() {
            try {
                offside.clear();
                dCrease.clear();
                fCrease.clear();
            } catch (Exception e) {
            }
        }

        private void clearDCs() {
            for (HockeyPlayer i : players.values()) {
                if (i.penalty == HockeyPenalty.D_CREASE) {
                    i.penalty = HockeyPenalty.NONE;
                    if (frequency == 0) {
                        m_botAction.warpTo(i.p_name, config.getTeam0ExtX() / 16, config.getTeam0ExtY() / 16);
                    } else {
                        m_botAction.warpTo(i.p_name, config.getTeam1ExtX() / 16, config.getTeam1ExtY() / 16);
                    }
                }
            }
        }

        /**
         * Increases team score by 1.
         */
        private void increaseScore() {
            teamScore++;
            updateScoreBoard();
        }
        
        private void decreaseScore() {
            teamScore--;
            updateScoreBoard();
        }
        
        private void useTimeOut() {
            if(timeout > 0) timeout--;
        }

        /**
         * Returns the teamname
         *
         * @return teamname
         */
        private String getName() {
            return teamName;
        }

        /**
         * Returns the current state of the player
         *
         * @param name name of the player
         * @return current state of the player
         */
        private int getPlayerState(String name) {
            int playerState;    //Current state of the player

            playerState = -1;   //-1 if not found

            try {
                if (players.containsKey(name)) {
                    playerState = players.get(name).getCurrentState();
                }
            } catch (Exception e) {
            }

            return playerState;
        }

        /**
         * Checks if the player is on this team
         *
         * @param name name of the player
         * @return true if on team, false if not
         */
        private boolean isOnTeam(String name) {
            boolean isOnTeam = false;

            try {
                if (players.containsKey(name)) {
                    isOnTeam = true;
                } else if (name.equalsIgnoreCase(captainName)) {
                    isOnTeam = true;
                } else {
                    isOnTeam = false;
                }
            } catch (Exception e) {
            }

            return isOnTeam;
        }

        /**
         * Checks if the player is a player on the team
         *
         * @param name Name of the player
         * @return true if is a player, false if not
         */
        private boolean isPlayer(String name) {
            boolean isPlayer = false;

            try {
                if (players.containsKey(name)) {
                    isPlayer = true;
                } else {
                    isPlayer = false;
                }
            } catch (Exception e) {
            }

            return isPlayer;
        }

        /**
         * Checks if the player is playing or has played for the team
         *
         * @param name Name of the player
         * @return return if player was IN, else false
         */
        private boolean isIN(String name) {
            boolean isIN = false;

            try {
                if (players.containsKey(name)) {
                    if (players.get(name).getCurrentState() != HockeyPlayer.SUBBED) {
                        isIN = true;
                    }
                }
            } catch (Exception e) {
            }

            return isIN;
        }

        /**
         * Checks if a player has lagged out or not
         *
         * @param name name of the player that could have lagged out
         * @return true if player has lagged out, else false
         */
        private boolean laggedOut(String name) {
            HockeyPlayer p;
            Player player;

            try {
                if (!players.containsKey(name)) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }

            p = players.get(name);

            if (p.getCurrentState() == HockeyPlayer.IN) {
                player = m_botAction.getPlayer(name);

                if (player == null) {
                    return false;
                }

                if (player.getShipType() == Tools.Ship.SPECTATOR) {
                    return true;
                }
            }

            if (p.getCurrentState() == HockeyPlayer.LAGOUT) {
                return true;
            }

            return false;
        }

        /**
         * Handles a lagout event
         * - Sends a lagout event to the player
         * - Notify the captain
         *
         * @param name Name of the player that lagged out
         */
        private void lagout(String name) {

            try {
                if (players.containsKey(name)) {
                    players.get(name).lagout();
                }
            } catch (Exception e) {
            }

            //Notify captain if captian is in the arena
            if (m_botAction.getPlayer(captainName) != null) {
                m_botAction.sendPrivateMessage(captainName, name + " lagged out!");
            }

            //NOTE: the team is notified in the HockeyPlayer lagout() method
        }

        /**
         * Returns the flagtime
         *
         * @return flag time in seconds
         */
        private int getFlagTime() {
            return flagTime;
        }

        /**
         * Returns the name of the current captain
         *
         * @return name of the current captain
         */
        private String getCaptainName() {
            return captainName;
        }

        /**
         * Removes captain
         * - Notifies arena
         * - Sets captainName to [NONE]
         */
        private void captainLeft() {
            m_botAction.sendArenaMessage(captainName + " has been removed as captain of " + teamName + ".");
            lastCaptainName = captainName;
            captainName = "[NONE]";
        }

        /**
         * Notify the arena that the captain has left the arena
         */
        private void captainLeftArena() {
            if (config.getAllowAutoCaps()) {
                m_botAction.sendArenaMessage("The captain of " + teamName
                        + " has left the arena, anyone of Freq " + frequency + " can claim cap with !cap");
            } else {
                m_botAction.sendArenaMessage("The captain of " + teamName
                        + " has left the arena.");
            }
        }

        /**
         * Timestamps last position of the player
         *
         * @param name name of the player
         */
        private void timestampLastPosition(String name) {
            try {
                if (players.containsKey(name)) {
                    players.get(name).timestampLastPosition();
                }
            } catch (Exception e) {
            }
        }

        /**
         * Returns if its the team's turn to pick
         *
         * @return true if its the team's turn, else false
         */
        private boolean isTurn() {
            return turnToPick;
        }

        /**
         * Checks if the player is a goalie.
         * 
         * @param name Name of the player to check
         * @return True if the player is the goalie of his team.
         */
        public boolean isGoalie(String name) {
            return goalieName.equalsIgnoreCase(name);
        }
        
        /**
         * Cleanly removes the goalie's name.
         */
        public void removeGoalie() {
            // Remove the goalie from the offside list, if applicable.
            if(offside.contains(goalieName))
                offside.remove(goalieName);
            // Reset the goalie's name.
            goalieName = "[NONE]";
        }

        /**
         * Returns the amount of players in the team.
         * Meaning all the players but the subbed ones
         *
         * @return amount of players IN
         */
        private int getSizeIN() {
            int sizeIn;

            sizeIn = 0;

            for (HockeyPlayer i : players.values()) {
                if (i.p_state != HockeyPlayer.SUBBED) {
                    sizeIn++;
                }
            }

            return sizeIn;
        }
        
        /**
         * This function checks if a certain ship has exceeded its limit for this team.
         * If this function is called for a ship change of a single player, please use {@link #isShipAllowed(int, int)}.
         * 
         * @param shipType The ship to be checked. Please, use the constants from {@link Tools.Ship} for this.
         * @return true when the ship is allowed to be added. Returns false when the maximum has been reached.
         * 
         * @see #isShipAllowed(int, int)
         */
        private boolean isShipAllowed(int shipType) {
            return isShipAllowed(shipType, -1);
        }
        
        /**
         * This function checks if a certain ship has exceeded its limit for this team.
         * This function differs from {@link #isShipAllowed(int)} that it is intended to be used on a ship change for
         * a single player. For this specific case, there is an additional check that needs to be done to allow
         * a goalie to change to another goalie ship.
         * 
         * @param shipType The ship to be checked. Please, use the constants from {@link Tools.Ship} for this.
         * @param oldShipType The old ship type.
         * @return true when the ship is allowed to be added. Returns false when the maximum has been reached.
         */
        private boolean isShipAllowed(int shipType, int oldShipType) {
            int shipCount = 0;
            int goalieShips = 0;
            boolean chkGoalieShips = config.isGoalieShip(shipType);

            // Iterate through this team's player list and count the ships.
            for(HockeyPlayer p : players.values()) {
                if(p.p_state < HockeyPlayer.SUBBED && p.p_currentShip == shipType)
                    shipCount++;
                // Also count the ships if the wanted shipType is a goalie ship.
                if(chkGoalieShips && config.isGoalieShip(p.p_currentShip))
                    goalieShips++;
            }
            
            // In case of a change ship, when the old ship was a goalie, substract one from the total, since it will be removed.
            // This can be ignored for any other ship, since those maximum numbers aren't linked.
            if(config.isGoalieShip(oldShipType))
                goalieShips--;
            
            //Return false when any of the following is true:
            //- shipCount equals or exceeds maximum ships allowed for this type and the amount of allowed ships is not unlimited;
            //- the requested ship is a goalie ship and there is already one on the team.
            if((shipCount >= config.getMaxShips(shipType) && config.getMaxShips(shipType) != -1)
                    || (chkGoalieShips && goalieShips >= 1)) {
                return false;
            } else {
                return true;
            }
        }

        /**
         * Adds a player to the team
         * - Sending the arena, captain and the player a message
         * - Adding the player
         *
         * @param p Player that is added
         * @param shipType shiptype
         */
        private void addPlayer(Player p, int shipType) {
            String arenaMessage;    //Arena message
            String captainMessage;  //Captain message
            String playerMessage;   //Player message
            String pName;           //Player name

            pName = p.getPlayerName();

            captainMessage = pName + " has been added.";
            playerMessage = "You've been added to the game.";

            if (config.announceShipType()) {
                arenaMessage = pName + " is in for " + teamName + " as a " + Tools.shipName(shipType) + ".";
            } else {
                arenaMessage = pName + " is in for " + teamName + ".";
            }

            /* Send the messages */
            m_botAction.sendArenaMessage(arenaMessage);
            m_botAction.sendPrivateMessage(captainName, captainMessage);
            m_botAction.sendPrivateMessage(pName, playerMessage);

            if (!players.containsKey(pName)) {
                players.put(pName, new HockeyPlayer(pName, shipType, frequency));
            } else {
                players.get(pName).putIN(shipType);
            }

            if (config.isGoalieShip(shipType)) {
                goalieName = pName;
            }
        }

        /**
         * Returns turn value
         *
         * @return true if its the teams turn to pick, else false
         */
        private boolean getTurn() {
            return turnToPick;
        }

        /**
         * Team has picked a player
         * - turnToPick set to false
         */
        private void picked() {
            turnToPick = false;
        }

        /**
         * Sets turn to true
         */
        private void setTurn() {
            turnToPick = true;
        }

        /**
         * Checks if the team has a captain
         * - Checks if captainName is equal to [NONE]
         * - Checks if captain is in the arena
         *
         * @return true if the team has a captain, else false
         */
        private boolean hasCaptain() {
            if (captainName.equals("[NONE]")) {
                return false;
            } else if (m_botAction.getPlayer(captainName) == null) {
                return false;
            } else {
                return true;
            }
        }

        /**
         * Sets captain
         * - Sets timestamp
         * - Sends arena message
         *
         * @param name Name of the captain
         */
        private void setCaptain(String name) {
            Player p;

            p = m_botAction.getPlayer(name);

            if (p == null) {
                return;
            }

            /* Last check to prevent arena spamming */
            if (name.equalsIgnoreCase(lastCaptainName)) {
                if ((System.currentTimeMillis() - captainTimestamp) <= (5 * Tools.TimeInMillis.SECOND)) {
                    m_botAction.sendPrivateMessage(name, "You will have to wait "
                            + (System.currentTimeMillis() - captainTimestamp) / 1000
                            + " more seconds before you can claim cap again.");
                    sendCaptainList(name);
                    return;
                }
            }

            captainName = p.getPlayerName();
            captainTimestamp = System.currentTimeMillis();

            m_botAction.sendArenaMessage(captainName + " is assigned as captain for "
                    + teamName, Tools.Sound.BEEP1);

            if (currentState != HockeyState.WAITING_FOR_CAPS) {
                captainsIndex++;
                captains.put(captainsIndex, new HockeyCaptain(captainName));
            }
        }

        /**
         * Sets the current captain in the captain list
         */
        private void putCaptainInList() {
            captainsIndex++;
            captains.put(captainsIndex, new HockeyCaptain(captainName));
        }

        /**
         * Returns if name is a captain or not
         *
         * @return true if name is captain, else false
         */
        private boolean isCaptain(String name) {
            if (captainName.equalsIgnoreCase(name)) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * Searches for name in team
         *
         * @param name name of the player that needs to get found
         * @return HockeyPlayer if found, else null
         */
        private HockeyPlayer searchPlayer(String name) {
            HockeyPlayer p;

            if(name == null)
                return null;
            
            p = null;
            name = name.toLowerCase();

            for (HockeyPlayer i : players.values()) {
                if (i.getName().toLowerCase().startsWith(name)) {
                    if (p == null) {
                        p = i;
                    } else if (i.getName().toLowerCase().compareTo(p.getName().toLowerCase()) > 0) {
                        p = i;
                    }
                }
            }

            return p;
        }

        /**
         * Determines if a player is allowed back in with !lagout/!return
         *
         * @param name name of the player that needs to get checked
         * @return true if allowed back in, else false
         */
        private boolean laginAllowed(String name) {
            HockeyPlayer p;
            Player player;
            boolean skipLagoutTime;

            skipLagoutTime = false;

            if (!players.containsKey(name)) {
                return false;
            }

            p = players.get(name);

            switch (p.getCurrentState()) {
                case HockeyPlayer.IN:
                    player = m_botAction.getPlayer(name);

                    if (player == null) {
                        return false;
                    }

                    if (player.getShipType() != Tools.Ship.SPECTATOR) {
                        return false;
                    }

                    skipLagoutTime = true;
                    break;
                case HockeyPlayer.LAGOUT:
                    break;
                default:
                    return false;
            }

            //Check if enough time has passed
            if (!skipLagoutTime) {
                if (System.currentTimeMillis() - p.getLagoutTimestamp() < HockeyPlayer.LAGOUT_TIME) {
                    return false;
                }
            }

            /*
             * All checks done
             */
            return true;
        }

        /**
         * Returns the corresponding error message of a not allowed !lagout
         *
         * @param name name of the player that issued the !lagout command
         * @return Error message string
         */
        private String getLaginErrorMessage(String name) {
            HockeyPlayer p;
            Player player;
            boolean skipLagoutTime;

            skipLagoutTime = false;

            if (!players.containsKey(name)) {
                return "ERROR: You are not on one of the teams.";
            }

            p = players.get(name);

            switch (p.getCurrentState()) {
                case HockeyPlayer.IN:
                    player = m_botAction.getPlayer(name);

                    if (player == null) {
                        return "ERROR: Unknown";
                    }

                    if (player.getShipType() != Tools.Ship.SPECTATOR) {
                        return "Error: You have not lagged out.";
                    }

                    skipLagoutTime = true;
                    break;
                case HockeyPlayer.LAGOUT:
                    break;
                default:
                    return "ERROR: You have not lagged out.";
            }

            //Check if enough time has passed
            if (!skipLagoutTime) {
                if (System.currentTimeMillis() - p.getLagoutTimestamp() < HockeyPlayer.LAGOUT_TIME) {
                    return "You must wait for " + (HockeyPlayer.LAGOUT_TIME
                            - (System.currentTimeMillis() - p.getLagoutTimestamp())) / Tools.TimeInMillis.SECOND
                            + " more seconds before you can return into the game.";
                }
            }

            return "ERROR: Unknown";
        }

        /**
         * Returns player into the game
         *
         * @param name name of the player
         */
        private void lagin(String name) {
            if (!players.containsKey(name)) {
                return;
            }

            players.get(name).lagin();
        }

        /**
         * Returns if player is currently playing or not
         *
         * @param name name of the player
         * @return true if the player is IN, else false
         */
        private boolean isPlaying(String name) {
            if (!players.containsKey(name)) {
                return false;
            }

            if (players.get(name).getCurrentState() == HockeyPlayer.IN) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * Returns team's frequency
         *
         * @return frequency
         */
        private int getFrequency() {
            return frequency;
        }

        /**
         * Completely removes player from the team
         *
         * @param name name of the player that needs to get removed
         */
        private void removePlayer(String name) {
            Player p;

            if (players.containsKey(name)) {
                // Remove the name as goalie, if he is the goalie.
                if(isGoalie(name))
                    removeGoalie();
                //Remove the player from the team.
                players.remove(name);
            }

            removeFromPenaltyLists(name);
            
            m_botAction.sendArenaMessage(name + " has been removed from " + teamName);

            p = m_botAction.getPlayer(name);

            if (p == null) {
                return;
            }

            if (p.getShipType() != Tools.Ship.SPECTATOR) {
                m_botAction.specWithoutLock(name);
            }

            if (listNotplaying.contains(name)) {
                m_botAction.setFreq(name, FREQ_NOTPLAYING);
            } else {
                m_botAction.setFreq(name, FREQ_SPEC);
            }
        }

        /**
         * Removes a player from all the penalty lists of his team.
         * @param name Name of the player.
         */
        private void removeFromPenaltyLists(String name) {
            // Just in case, prevent unexpected things from happening.
            if(name == null)
                return;
            // Check the defensive crease list.
            if(dCrease.contains(name))
                dCrease.remove(name);
            // Check the face off crease list.
            if(fCrease.contains(name))
                fCrease.remove(name);
            // Check the offside list.
            if(offside.contains(name))
                offside.remove(name);
        }
        
        /**
         * Sets player to not playing modus, player will still be subable
         *
         * @param name Name of the player that should be set to out notplaying
         */
        private void setOutNotPlaying(String name) {
            if (players.containsKey(name)) {
                players.get(name).out("out not playing");
            }
        }

        /**
         * Readies the team or sets it to not ready
         */
        private void ready() {
            if (!ready) {
                if (players.size() >= config.getMinPlayers()) {
                    m_botAction.sendArenaMessage(teamName + " is ready to begin.");
                    ready = true;
                } else {
                    m_botAction.sendPrivateMessage(captainName, "Cannot ready, not enough players in.");
                }
            } else {
                notReady();
            }
        }

        /**
         * Sets the team to not ready
         */
        private void notReady() {
            ready = false;
            m_botAction.sendArenaMessage(teamName + " is NOT ready to begin.");
        }

        /**
         * Returns if team is ready or not
         *
         * @return true if team is ready, else false
         */
        private boolean isReady() {
            if (ready) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * Returns if the team has any substitutes left
         *
         * @return True if team has substitutes left, else false
         */
        private boolean hasSubtitutesLeft() {
            if (substitutesLeft > 0 || substitutesLeft == -1) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * Handles the sub further
         *
         * @param playerOne player one
         * @param playerTwo player two
         */
        private void sub(HockeyPlayer playerOne, Player playerTwo) {
            int shipType = playerOne.p_currentShip;
            String p2Name = playerTwo.getPlayerName();      //Name player 2

            //Removing player
            removeFromPenaltyLists(playerOne.getName());
            playerOne.sub();
            
            //Adding substitute
            if (players.containsKey(p2Name)) {
                HockeyPlayer p = players.get(p2Name);
                p.p_currentShip = shipType;
                p.addPlayer();
            } else {
                players.put(p2Name,
                        new HockeyPlayer(p2Name, shipType, frequency));
            }

            m_botAction.sendSmartPrivateMessage(p2Name, "You are subbed in the game.");

            m_botAction.sendArenaMessage(playerOne.p_name + " has been substituted by " + p2Name);

            if (substitutesLeft != -1) {
                substitutesLeft--;
            }

            if (substitutesLeft >= 0) {
                m_botAction.sendSmartPrivateMessage(captainName, "You have "
                        + substitutesLeft + " substitutes left.");
            }

            if (config.isGoalieShip(shipType)) {
                goalieName = p2Name;
            }

        }

        /**
         * Switches playerOne with playerTwo
         *
         * @param playerOne playerOne
         * @param playerTwo playerTwo
         */
        private void switchPlayers(HockeyPlayer playerOne, HockeyPlayer playerTwo) {
            m_botAction.sendArenaMessage(playerOne.p_name + " (" + Tools.shipName(playerOne.p_currentShip) + ") and "
                    + playerTwo.p_name + " (" + Tools.shipName(playerTwo.p_currentShip) + ") switched ships.");

            int playerOneShipType = playerTwo.p_currentShip;
            int playerTwoShipType = playerOne.p_currentShip;

            playerOne.p_currentShip = playerOneShipType;
            playerTwo.p_currentShip = playerTwoShipType;

            if (m_botAction.getPlayer(playerOne.p_name) != null) {
                playerOne.switchPlayer();
                if (config.isGoalieShip(playerOneShipType)) {
                    goalieName = playerOne.p_name;
                }
            }

            if (m_botAction.getPlayer(playerTwo.p_name) != null) {
                playerTwo.switchPlayer();
                if (config.isGoalieShip(playerTwoShipType)) {
                    goalieName = playerTwo.p_name;
                }
            }

        }

        /**
         * Returns the timestamp when the captain was set
         *
         * @return timestamp of when the captain was set
         */
        private long getCaptainTimeStamp() {
            return captainTimestamp;
        }

        /**
         * Returns sum of scores
         *
         * @return sum of scores
         */
        private int getScore() {
            return teamScore;
        }

        /**
         * Returns the total sum of all lag outs
         *
         * @return total sum of all lag outs
         */
        private int getLagouts() {
            int lagouts = 0;

            for (HockeyPlayer i : players.values()) {
                lagouts += i.getLagouts();
            }

            return lagouts;
        }

        public HockeyPlayer getPlayer(String name) {
            return players.get(name);
        }

        /**
         * Warps player to coords
         *
         * @param x_coord x_coord
         * @param y_coord y_coord
         */
        private void warpTo(int x_coord, int y_coord) {
            for (HockeyPlayer i : players.values()) {
                int playerID = m_botAction.getPlayerID(i.p_name);

                if (playerID == -1) {
                    return;
                }

                m_botAction.warpTo(playerID, x_coord, y_coord);
            }
        }
    }

    private class HockeyPuck {

        private byte ballID;
        private int timestamp;
        private short ballX;
        private short ballY;
        private short veloX;
        private boolean carried;
        private String carrier;
        private final Stack<String> carriers;
        private Stack<Point> releases;
        private boolean holding;
        private Point lastPickup;
        private int dropDelay;

        public HockeyPuck() {
            carrier = null;
            carriers = new Stack<String>();
            releases = new Stack<Point>();
            carried = false;
            holding = false;
            lastPickup = null;
        }

        /**
         * Called by handleEvent(BallPosition event)
         * @param event the ball position
         */
        public void update(BallPosition event) {
            ballID = event.getBallID();
            this.timestamp = event.getTimeStamp();
            ballX = event.getXLocation();
            ballY = event.getYLocation();
            if (event.getXVelocity() != 0) {
                veloX = event.getXVelocity();
            }
            short carrierID = event.getCarrier();
            if (carrierID != -1) {
                carrier = m_botAction.getPlayerName(carrierID);
            } else {
                carrier = null;
            }

            if (carrier != null && !carrier.equals(m_botAction.getBotName())) {
                if (!carried && currentState == HockeyState.GAME_IN_PROGRESS) {
                    carriers.push(carrier);
                    lastPickup = new Point(ballX, ballY);
                }
                carried = true;
            } else if (carrier == null && carried) {
                if (carried && currentState == HockeyState.GAME_IN_PROGRESS) {
                    releases.push(new Point(ballX, ballY));
                }
                carried = false;
            } else if (carrier != null && carrier.equals(m_botAction.getBotName())) {
                holding = true;
            } else if (carrier == null && holding) {
                if (holding && currentState == HockeyState.GAME_IN_PROGRESS) {
                    releases.push(new Point(ballX, ballY));
                }
                holding = false;
            }

        }

        /**
         * clears local data for puck
         */
        public void clear() {
            carrier = null;
            try {
                carriers.clear();
                releases.clear();
                lastPickup = null;
                carriersSize = 0;
            } catch (Exception e) {
            }
        }

        public byte getBallID() {
            return ballID;
        }

        public int getTimeStamp() {
            return timestamp;
        }

        public short getBallX() {
            return ballX;
        }

        public short getBallY() {
            return ballY;
        }

        public boolean isCarried() {
            return carried;
        }

        /**
         * Peeks at last ball carrier without removing them from stack
         * @return short player id or null if empty
         */
        public String peekLastCarrierName() {
            String id = null;
            if (!carriers.empty()) {
                id = carriers.peek();
            }
            return id;
        }

        /**
         * Gets last ball carrier (removes it from stack)
         * @return short player id or null if empty
         */
        public String getLastCarrierName() {
            String id = null;
            if (!carriers.empty()) {
                id = carriers.pop();
            }
            return id;
        }

        /**
         * Peeks at last ball release without removing them from stack
         * @return point last point released or null if empty
         */
        public Point peekLastReleasePoint() {
            Point p = null;
            if (!releases.empty()) {
                p = releases.peek();
            }
            return p;
        }

        /**
         * Get last ball release (removes it from stack)
         * @return point last point released or null if empty
         */
        public Point getLastReleasePoint() {
            Point p = null;
            if (!releases.empty()) {
                p = releases.pop();
            }
            return p;
        }
    }

    /**
     * Class Gameticker
     * 
     * This class is the engine of the hockeybot. 
     * <p>
     * In essence this is a state machine.
     * Each tick this class performs a check to see in which state it currently is.
     * Depending on the current state, the accompanied "do"-functions get called and executed.
     * <p>
     * At the current default settings, this class' run function is executed every second.
     * <p>
     * Please keep in mind that the run function is threaded, i.e. it runs quasi-simultaniously
     * to the other functions in this bot. This can lead to racing conditions, if no safeguards are used.
     * 
     * @author Unknown
     * 
     * @see TimerTask
     *
     */
    private class Gameticker extends TimerTask {

        /**
         * The core of the Gameticker class.
         * This function is run on every tick and determines what to do next.
         */
        @Override
        public void run() {
            switch (currentState) {
                case OFF:
                    break;
                case WAITING_FOR_CAPS:
                    doWaitingForCaps();
                    break;
                case ADDING_PLAYERS:
                    doAddingPlayers();
                    break;
                case REVIEW:
                    doReview();
                    break;
                case FACE_OFF:
                    doFaceOff();
                    break;
                case GAME_IN_PROGRESS:
                    doStartGame();
                    break;
                case GAME_OVER:
                    doGameOver();
                    break;
                case TIMEOUT:
                    doTimeout();
                    break;
                case WAIT:
                    /* 
                     * This state is intended to make the timer skip a round.
                     * This is useful when someone wants to switch between certain states,
                     * but not cause any racing conditions.
                     * 
                     * For example: 
                     * When switching from FACE_OFF to GAME_IN_PROGRESS there is a moment when the
                     * bot is actually in between states. During this period, the bot should not refresh
                     * holding the ball, but also not yet start the GAME_IN_PROGRESS part while the correct
                     * things are being set up and done.
                     */
                    break;
            }
        }

        /**
         * Handles the state in which the captains are assigned.
         */
        private void doWaitingForCaps() {
            /*
             * Need two captains within one minute, else remove captain
             */
            if (team0.hasCaptain()) {
                if ((System.currentTimeMillis() - team0.getCaptainTimeStamp()) >= Tools.TimeInMillis.MINUTE) {
                    team0.captainLeft();
                }
            }

            if (team1.hasCaptain()) {
                if ((System.currentTimeMillis() - team1.getCaptainTimeStamp()) >= Tools.TimeInMillis.MINUTE) {
                    team1.captainLeft();
                }
            }

            checkIfEnoughCaps();
        }

        /**
         * Checks if the timelimit has past for adding players.
         * Initiates {@link hockeybot#checkLineup() checkLineup()} if this is the case.
         */
        private void doAddingPlayers() {
            int multiplier = 10;

            if ((System.currentTimeMillis() - timeStamp) >= Tools.TimeInMillis.MINUTE * multiplier) {
                m_botAction.sendArenaMessage("Time is up! Checking lineups..");
                checkLineup();
            }
        }

        /**
         * During the faceoff, this function checks the following:
         * <ul>
         * <li>Checks if the bot needs to pick up the puck;
         * <li>Checks if it's time to issue a drop warning;
         * <li>Checks if the puck needs to be dropped;
         * <li>Checks if a player is offside and warn or penalize them.
         * </ul>
         * If there is a faceoff crease, then it will restart the faceoff after
         * penalizing the offending player(s). Otherwise, it will start the game.
         * <p>
         * @see hockeybot#startFaceOff()
         * @see hockeybot#startGame()
         */
        private void doFaceOff() {
            long time = (System.currentTimeMillis() - timeStamp) / Tools.TimeInMillis.SECOND;
            
            // When looking at startFaceOff(), this code seems to be redundant.
            // However, due to the bot not always having the latest ball positions, this 
            // safeguard is needed to make it function properly.
            if(!puck.holding) {
                doGetBall(config.getPuckDropX(), config.getPuckDropY());
            }
            
            //DROP WARNING
            if (time == 10) {
                m_botAction.sendArenaMessage("Get READY! THE PUCK WILL BE DROPPED SOON.", 1);
                
                try {
                    for(HockeyTeam t: teams) {
                        String arrow = (t.getFrequency()==0)?"left (<-)":"right (->)";
                        if (!t.offside.empty()) {
                            Iterator<String> i = t.offside.iterator();
                            while (i.hasNext()) {
                                String name = i.next();
                                m_botAction.sendPrivateMessage(name, "WARNING: You are "
                                        + "offside. Get " + arrow + " of the center red line "
                                        + "before drop or you will receive a penalty.");
                            }
                        }
                        if (t.fCrease.size() > 1) {
                            Iterator<String> i = t.fCrease.iterator();
                            while (i.hasNext()) {
                                String name = i.next();
                                m_botAction.sendPrivateMessage(name, "WARNING: Only one "
                                        + "member per team is allowed in crease during "
                                        + "Face Off. The last players who entered leave "
                                        + "the crease or you will recieve a penalty.");
                            }
                        }
                    }
                    if (!botCrease.empty()) {
                        Iterator<String> i = botCrease.iterator();
                        while (i.hasNext()) {
                            String name = i.next();
                            m_botAction.sendPrivateMessage(name, "WARNING: You are "
                                    + "not allowed to sit inside the red crease during"
                                    + " Face Off. Exit before drop or you will recieve"
                                    + " a penalty.");
                        }
                    }
                } catch (Exception e) {
                }
            }

            //CHECK PENALTIES AND DROP
            if (time >= puck.dropDelay && puck.holding) {
                dropBall();
                try {
                    for(HockeyTeam t: teams) {
                        if (!t.offside.empty()) {
                            Iterator<String> i = t.offside.iterator();
                            while (i.hasNext()) {
                                String name = i.next();
                                t.searchPlayer(name).setPenalty(HockeyPenalty.OFFSIDE);
                                m_botAction.sendArenaMessage("OFFSIDE PENALTY: " + name);
                            }
                        }
                        if (t.fCrease.size() > 1) {
                            Iterator<String> i = t.fCrease.iterator();
                            i.next();
                            while (i.hasNext()) {
                                String name = i.next();
                                t.searchPlayer(name).setPenalty(HockeyPenalty.FO_CREASE);
                                m_botAction.sendArenaMessage("FACEOFF CREASE PENALTY: " + name);
                            }
                        }
                    }
                    if (!botCrease.empty()) {
                        Iterator<String> i = botCrease.iterator();
                        HockeyPlayer player = null;
                        while (i.hasNext()) {
                            String name = i.next();
                            player = team0.searchPlayer(name);
                            if (player == null) {
                                player = team1.searchPlayer(name);
                            }
                            player.setPenalty(HockeyPenalty.FO_CREASE);
                            m_botAction.sendArenaMessage("FACEOFF CREASE PENALTY: " + name);
                            isPenalty = true;
                        }
                    }
                } catch (Exception e) {
                }
                if (isPenalty){
                    isPenalty = false;
                    startFaceOff();
                } else {
                    team0.clearUnsetPenalties();
                    team1.clearUnsetPenalties();
                    startGame();
                }
            }
        }

        /**
         * This function is active when the puck is in play.
         * <p>
         * Its main tasks is to keep track of various stats, like steals, turnovers and saves,
         * as well as checking for possible penalties. It also handles the {@link hockeybot#gameTime gameTime} counter.
         * <p>
         * Depending on the type of crease, this function might initiate a new {@link hockeybot#startFaceOff() Faceoff}.
         */
        private void doStartGame() {
            HockeyTeam tC, tP;
            roundTime = ((System.currentTimeMillis() - timeStamp)
                    / Tools.TimeInMillis.SECOND);

            //check for steals/saves
            try {
                int currentSize = puck.carriers.size();
                while (carriersSize < currentSize) {
                    carriersSize++;
                    if (carriersSize > 1) {
                        String carrier;
                        String previous;
                        carrier = puck.carriers.elementAt(carriersSize - 1);
                        previous = puck.carriers.elementAt(carriersSize - 2);
                        tC = getTeam(carrier);
                        tP = getTeam(previous);
                        // Check if the players are from a different team.
                        if(tC != null && tP != null && tC != tP) {
                            // If the current carrier was in the defensive crease zone, add a save,
                            // otherwise, add a steal, and a turnover to the previous carrier.
                            if(tC.dCrease.contains(carrier)) {
                                tC.getPlayer(carrier).madeSave();
                                tP.getPlayer(previous).shotOnGoal();
                            } else {
                                tC.getPlayer(carrier).madeSteal();
                                tP.getPlayer(previous).madeTurnover();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                //System.out.println(e.getMessage() + e.getStackTrace());
            }

            //DEFENSE CREASE PENALTY TEAM 0 and 1
            try {
                for(HockeyTeam t : teams) {
                    // If the dCrease size is 1, then the intercept is valid, no matter if it's a goalie or not.
                    if(t.dCrease.size() > 1) {
                        int size;
                        String carrier, previous;
                        Point pickup;
                        
                        //Fetch the correct crease zone.
                        HockeyZone hz = (t.getFrequency() == 0)?HockeyZone.CREASE0:HockeyZone.CREASE1;
                        
                        synchronized (puck.carriers) {
                            size = puck.carriers.size();
                            carrier = puck.carrier;
                            previous = puck.carriers.elementAt(size-2);
                            pickup = puck.lastPickup;
                        }
                        
                        if(carrier != null && !t.isGoalie(carrier)
                                && t.dCrease.contains(carrier) && previous != null 
                                && !t.isOnTeam(previous) && hz == getZone(pickup)) {
                            HockeyPlayer player = t.getPlayer(carrier);
                            if(player != null) {
                                player.setPenalty(HockeyPenalty.D_CREASE);
                                m_botAction.sendArenaMessage("DEFENSE CREASE PENALTY: " + carrier);
                                startFaceOff();
                            }
                        }
                    }
                }
            } catch (Exception e) {
            }

            //OFFSIDE PENALTY TEAM 0 and 1 (Goalie type, midgame)
            try {
                for(HockeyTeam t: teams) {
                    if (!t.offside.empty()) {
                        Iterator<String> i = t.offside.iterator();
                        while (i.hasNext()) {
                            String name = i.next();
                            if (name.equals(puck.carrier)) {
                                t.getPlayer(name).setPenalty(HockeyPenalty.OFFSIDE);
                                m_botAction.sendArenaMessage("OFFSIDE PENALTY: " + name);
                                startFaceOff();
                            }
                        }
                    }
                }
            } catch (Exception e) {
            }

            gameTime++;

        }

        /**
         * Currently, this function is not used.
         * It's original intent was to give the staff time to vote on the final goal.
         */
        private void doReview() {
            // Only do stuff when reviewing is done.
            if(!reviewing) {
                int count[] = {0, 0, 0, 0, 0};   // NONE, ABSTAIN, CLEAN, CREASE, PHASE  
                
                // Put the statemachine on hold, to prevent racing conditions.
                currentState = HockeyState.WAIT;
                
                m_botAction.sendArenaMessage("Review period is done. Please wait while we count the votes.", Tools.Sound.STOP_MUSIC);
                
                // Count the votes ...
                for(HockeyVote vote : listVotes.values()) {
                    count[vote.ordinal()]++;
                }
                
                // ... and judge accordingly.
                if((count[2] <= (count[3] + count[4])) && (count[3] + count[4] > 0)) {
                    // On a tie, play it safe and do it over, unless no one voted at all for crease or phase.
                    if(count[4] >= count[3]) {
                        m_botAction.sendArenaMessage("The goal has been rejected! Reason: Phasing.", Tools.Sound.CROWD_AWW);
                    } else {
                        m_botAction.sendArenaMessage("The goal has been rejected! Reason: Crease.", Tools.Sound.CROWD_AWW);
                    }
                    
                    //TODO Make this part better, and substract a goal stat from the last person who made the goal.
                    // Since it's a final goal kind of deal, for now, we will check it this way.
                    if(team0.getScore() >= config.gameModeTarget) {
                        team0.decreaseScore();
                    } else {
                        team1.decreaseScore();
                    }
                    // Start the faceoff. This will automatically also update the scoreboard.
                    startFaceOff();
                } else {
                    // Clean goal.
                    m_botAction.sendArenaMessage("The goal has been judged as Clean!");
                    // Th-th-th-that's all, folks!
                    gameOver();
                }
                    
            }
        }

        /**
         * Handles the GAME_OVER state.
         * 
         * This state is active after the game has ended and the final stats have been displayed.
         * Depending on the settings, it will either automatically start a new game or 
         * cleans everything up and shuts the Hockeybot down.
         * 
         * @see hockeybot#cmd_off(String)
         */
        private void doGameOver() {
            long time;

            time = (System.currentTimeMillis() - timeStamp) / Tools.TimeInMillis.SECOND;

            if (!lockLastGame && (time >= 10)) {
                startWaitingForCaps();
            } else if (time >= 10) {
                currentState = HockeyState.OFF;
                m_botAction.sendArenaMessage("Bot has been shutdown.", Tools.Sound.GAME_SUCKS);
                reset();
                unlockArena();
                clearObjects();
            }
        }
        
        /**
         * Handles the TIMEOUT state.
         * 
         * Checks if a 10 second warning needs to be fired, or
         * if the timeout has ended. In case of the latter, 
         * a new {@link hockeybot#startFaceOff() faceoff} will be started.
         */
        private void doTimeout() {
            long time;
            
            time = (System.currentTimeMillis() - timeStamp) / Tools.TimeInMillis.SECOND;
            
            // The timeout has finished, going back to the faceoff.
            if(time >= 30) {
                m_botAction.sendArenaMessage("The timeout has ended.", Tools.Sound.HALLELUJAH);
                startFaceOff();
            } else if (time == 20) {
                    m_botAction.sendArenaMessage("Timeout will end in 10 seconds.");
            }
        }
    }

    /**
     * Used for debugging purposes only. When committing the code, please either temporary remove
     * the @SuppressWarnings line to doublecheck that this function throws the being unused warning, or
     * check if you get an "Unnescessary @SuppressWarnings("unused")" message.
     * <p>
     * When choosing to send the debugmessages in game, please be aware of the location you are
     * putting your calls to this function, because this can easily get your bot kicked for flooding.
     * 
     * @param msg Message to be sent to either the console and/or ingame.
     */
    @SuppressWarnings("unused")
    private void debugMessage(String msg) {
        //m_botAction.sendSmartPrivateMessage("ThePAP", msg);
        System.out.println(msg);
    }
}

