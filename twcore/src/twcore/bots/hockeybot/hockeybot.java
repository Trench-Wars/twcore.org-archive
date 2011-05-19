package twcore.bots.hockeybot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.SoccerGoal;
import twcore.core.game.Player;
import twcore.core.util.Point;
import twcore.core.util.Tools;
import twcore.core.util.Spy;

/**
 * Class HockeyBot
 *
 * <p>Used for hosting ?go hockey</p>
 *
 * @author fantus, spookedone
 */
public class hockeybot extends SubspaceBot {

    private boolean lockArena;
    private boolean lockLastGame;
    private HockeyConfig config;                            //Game configuration
    private HockeyTeam team0;                               //Teams
    private HockeyTeam team1;
    private HockeyPuck puck;                                //the ball in arena
    private Spy racismWatcher;                              //Racism watcher
    private ArrayList<String> listNotplaying;               //List of notplaying players
    private ArrayList<String> listAlert;                    //List of players who toggled !subscribe on
    private long zonerTimestamp;                            //Timestamp of the last zoner
    private long manualZonerTimestamp;                      //Timestamp of the last manualzoner
    //Frequencies
    private static final int FREQ_SPEC = 9999;
    private static final int FREQ_NOTPLAYING = 666;
    //Static variables
    private static final int ZONER_WAIT_TIME = 15;

    //Game states
    private enum HockeyState {

        OFF, WAITING_FOR_CAPS, ADDING_PLAYERS, FACE_OFF,
        GAME_IN_PROGRESS, REVIEW, GAME_OVER
    };
    private HockeyState currentState;
    private long timeStamp;
    private long roundTime;
    private long gameTime;

    private enum HockeyPenalty {

        NONE, OFFSIDE, D_CREASE, FO_CREASE
    };
    //Game ticker
    private Gameticker gameticker;

    private static enum Vote {

        NONE, CLEAN, PHASE
    };

    private static enum Zone {

        LEFT, NEUTRAL, RIGHT
    };

    /** Class constructor */
    public hockeybot(BotAction botAction) {
        super(botAction);
        initializeVariables();  //Initialize variables
        requestEvents();        //Request Subspace Events
    }

    /** Initializes all the variables used in this class */
    private void initializeVariables() {
        config = new HockeyConfig();            //Game configuration
        currentState = HockeyState.OFF;         //Game state

        puck = new HockeyPuck();
        team0 = new HockeyTeam(0);              //Team: Freq 0
        team1 = new HockeyTeam(1);              //Team: Freq 1

        racismWatcher = new Spy(m_botAction);   //Racism watcher

        listNotplaying = new ArrayList<String>();
        listNotplaying.add(m_botAction.getBotName().toLowerCase());
        listAlert = new ArrayList<String>();

        lockArena = false;
        lockLastGame = false;
    }

    /** Requests Subspace events */
    private void requestEvents() {
        EventRequester req = m_botAction.getEventRequester();

        req.request(EventRequester.ARENA_JOINED);           //Bot joined arena
        req.request(EventRequester.FREQUENCY_CHANGE);       //Player changed frequency
        req.request(EventRequester.FREQUENCY_SHIP_CHANGE);  //Player changed frequency/ship
        req.request(EventRequester.LOGGED_ON);              //Bot logged on
        req.request(EventRequester.MESSAGE);                //Bot received message
        req.request(EventRequester.PLAYER_ENTERED);         //Player entered arena
        req.request(EventRequester.PLAYER_LEFT);            //Player left arena
        req.request(EventRequester.PLAYER_POSITION);        //Player position
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
        //m_botAction.setPlayerPositionUpdating(1);
        m_botAction.sendUnfilteredPublicMessage("?chat=" + config.getChats());  //Join all the chats
        start();    //Autostart the bot
    }

    /**
     * Handles FrequencyChange event
     * - since this event looks almost the same as FrequencyShipChange
     *   event its passed on to checkFCandFSC(name, frequency, ship).
     */
    @Override
    public void handleEvent(FrequencyChange event) {
        if (currentState != HockeyState.OFF) {
            Player p;

            p = m_botAction.getPlayer(event.getPlayerID());

            if (p != null) {
                if (!p.getPlayerName().equals(m_botAction.getBotName())) {
                    checkFCandFSC(p.getPlayerName(), p.getFrequency(), p.getShipType());
                }
            }
        }
    }

    /**
     * Handles FrequencyShipChange event
     * - since this event looks almost the same as FrequencyChange
     *   event its passed on to checkFCandFSC(name, frequency, ship).
     */
    @Override
    public void handleEvent(FrequencyShipChange event) {
        if (currentState != HockeyState.OFF) {
            Player p;

            p = m_botAction.getPlayer(event.getPlayerID());

            if (p != null) {
                if (!p.getPlayerName().equals(m_botAction.getBotName())) {
                    checkFCandFSC(p.getPlayerName(), p.getFrequency(), p.getShipType());
                }
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

        racismWatcher.handleEvent(event);   //Racism watcher

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

            name = m_botAction.getPlayerName(event.getPlayerID());

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
        if (currentState != HockeyState.OFF) {
            String name;    //Name of the player that left

            name = m_botAction.getPlayerName(event.getPlayerID());

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

        int playerID = event.getPlayerID();
        String name = m_botAction.getPlayerName(playerID);

        HockeyTeam team = null;
        if (name != null) {
            team = getTeam(name);
        }

        /* Null pointer exception check */
        if (team != null && !team.laggedOut(name)) {

            switch (currentState) {
                case FACE_OFF:

                    checkPenalty(event);

                    //check offside
                    int x = event.getXLocation();

                    try {
                    if (team.getFrequency() == 0 && x > config.getPuckDropX()) {
                        if (!team.offside.contains(name)) {
                            team.offside.push(name);
                        }
                    } else if (team.getFrequency() == 1 && x < config.getPuckDropX()) {
                        if (!team.offside.contains(name)) {
                            team.offside.push(name);
                        }
                    } else if (team.offside.contains(name)) {
                        team.offside.remove(name);
                    }} catch (Exception e) {}

                    //check faceoff crease
                    int fX = Math.abs(config.getPuckDropX() - event.getXLocation());
                    int fY = Math.abs(config.getPuckDropY() - event.getYLocation());
                    double fDistance = Math.sqrt(Math.pow(fX, 2) + Math.pow(fY, 2));

                    try {
                    if (fDistance < config.getPuckDropRadius()) {
                        if (!team.fCrease.contains(name)) {
                            team.fCrease.push(name);
                        }
                    } else if (team.fCrease.contains(name)) {
                        team.fCrease.remove(name);
                    }} catch (Exception e) {}

                    break;
                case GAME_IN_PROGRESS:
                    checkPenalty(event);
                    //check defense crease
                    int dX,
                     dY;
                    if (team.getFrequency() == 0) {
                        dX = Math.abs(config.getTeam0GoalX() - event.getXLocation());
                        dY = Math.abs(config.getTeam0GoalY() - event.getYLocation());
                    } else {
                        dX = Math.abs(config.getTeam1GoalX() - event.getXLocation());
                        dY = Math.abs(config.getTeam1GoalY() - event.getYLocation());
                    }

                    double dDistance = Math.sqrt(Math.pow(dX, 2) + Math.pow(dY, 2));

                    try {
                    if (dDistance < config.getGoalRadius()) {
                        if (!team.dCrease.contains(name)) {
                                team.dCrease.push(name);
                            }
                        //TODO test this more thoroughly
                        /*if (event.getXLocation() > config.getTeam0GoalX()
                                || event.getXLocation() < config.getTeam1GoalX()) {
                            if (!team.dCrease.contains(name)) {
                                team.dCrease.push(name);
                            }
                        }*/
                    } else if (team.dCrease.contains(name)) {
                        team.dCrease.remove(name);
                    }} catch (Exception e) {}
                    break;
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
        //sql.closePreparedStatements();
    }

    /**
     * Grabs ball and sits in drop location
     */
    public void getBall() {
        if (m_botAction.getShip().getShip() != 0 || !puck.holding) {
            m_botAction.getShip().setShip(8);
            m_botAction.getShip().setShip(0);
            m_botAction.getShip().move(config.getPuckDropX(), config.getPuckDropY());
            m_botAction.getBall(puck.getBallID(), (int) puck.getTimeStamp());
        }
    }

    /**
     * Drops the ball at current location
     */
    public void dropBall() {
        m_botAction.getShip().setShip(8);
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
            //check penalty
            if (player.penalty != HockeyPenalty.NONE) {

                if (team.getFrequency() == 0 && event.getYLocation() > config.getTeam0ExtY()) {
                    m_botAction.warpTo(name, config.getTeam0PenX() / 16, config.getTeam0PenY() / 16);
                } else if (event.getYLocation() > config.getTeam1ExtY()) {
                    m_botAction.warpTo(name, config.getTeam1PenX() / 16, config.getTeam1PenY() / 16);
                }

                if ((gameTime - player.penaltyTimestamp) >= config.getPenaltyTime()) {
                    player.setPenalty(HockeyPenalty.NONE);
                    if (team.getFrequency() == 0) {
                        m_botAction.warpTo(name, config.getTeam0ExtX() / 16, config.getTeam0ExtY() / 16);
                    } else {
                        m_botAction.warpTo(name, config.getTeam1ExtX() / 16, config.getTeam1ExtY() / 16);
                    }
                }
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
     * @param cmd command
     * @param override Override number, -1 for default, 0 for Freq 0, 1 for Freq 1
     */
    private void handleCommand(String name, String command, int override) {
        String cmd = command.toLowerCase();

        /* Captain commands */
        if (isCaptain(name) || override != -1) {
            if (cmd.startsWith("!change")) {
                cmd_change(name, cmd, override);
            } else if (cmd.startsWith("!switch")) {
                cmd_switch(name, cmd, override);
            } else if (cmd.startsWith("!add")) {
                cmd_add(name, cmd, override);
            } else if (cmd.equals("!ready")) {
                cmd_ready(name, override);
            } else if (cmd.equals("!removecap")) {
                cmd_removecap(name, override);
            } else if (cmd.startsWith("!remove")) {
                cmd_remove(name, cmd, override);
            } else if (cmd.startsWith("!sub")) {
                cmd_sub(name, cmd, override);
            }
        }

        /* Player commands */
        if (cmd.equals("!cap")) {
            cmd_cap(name);
        } else if (cmd.equals("!help")) {
            cmd_help(name);
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

        /* Voting commands */
        if (cmd.equals("!clean") || cmd.equals("!cl")) {
        } else if (cmd.equals("!phase") || cmd.equals("!ph")) {
        }

        /* Staff commands ZH+ */
        if (m_botAction.getOperatorList().isZH(name)) {
            if (cmd.equals("!start")) {
                cmd_start(name);
            } else if (cmd.equals("!stop")) {
                cmd_stop(name);
            } else if (cmd.startsWith("!zone") && !config.getAllowAutoCaps()) {
                cmd_zone(name, command);
            } else if (cmd.equals("!off")) {
                cmd_off(name);
            } else if (cmd.startsWith("!forcenp ")) {
                cmd_forcenp(name, cmd);
            } else if (cmd.startsWith("!setcaptain")) {
                cmd_setCaptain(name, cmd, override);
            } else if (cmd.equals("!ball")) {
                cmd_ball(name);
            } else if (cmd.equals("!drop")) {
                cmd_drop(name);
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

    /** Handles the !ball command */
    private void cmd_ball(String name) {
        m_botAction.sendPrivateMessage(name, "Ball was located at: " + puck.getBallX()
                + ", " + puck.getBallY());
        getBall();
    }

    /** Handles the !drop command */
    private void cmd_drop(String name) {
        dropBall();
    }

    /** Handles the !add command */
    private void cmd_add(String name, String cmd, int override) {
        int shipType;       //Specified shiptype (in cmd)
        Player p;           //Specified player (in cmd)
        String p_lc;        //Specified player's name in lower case
        String[] splitCmd;  //Cmd split up
        HockeyTeam t;         //Team

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
        if (cmd.length() < 5) {
            m_botAction.sendPrivateMessage(name, "Error: Please specify atleast a playername, !add <player>");
            return;
        }

        if (currentState == HockeyState.ADDING_PLAYERS || currentState == HockeyState.FACE_OFF
                || currentState == HockeyState.GAME_IN_PROGRESS) {
            splitCmd = cmd.substring(5).split(":"); //Split command (<player>:<shiptype>)

            p = m_botAction.getFuzzyPlayer(splitCmd[0]);    //Find <player>

            /* Check if p has been found */
            if (p == null) {
                m_botAction.sendPrivateMessage(name, "Error: " + splitCmd[0] + " could not be found.");
                return;
            }

            p_lc = p.getPlayerName().toLowerCase();

            /* Check if p is a bot */
            if (m_botAction.getOperatorList().isBotExact(p_lc)) {
                m_botAction.sendPrivateMessage(name, "Error: Pick again, bots are not allowed to play.");
                return;
            }

            /* Check if the player is set to notplaying */
            if (listNotplaying.contains(p_lc)) {
                m_botAction.sendPrivateMessage(name, "Error: " + p.getPlayerName() + " is set to notplaying.");
                return;
            }

            /* Check if the maximum amount of players IN is already reached */
            if (t.getSizeIN() >= config.getMaxPlayers()) {
                m_botAction.sendPrivateMessage(name, "Error: Maximum amount of players already reached.");
                return;
            }

            /* Check if the player is already on the team and playing */
            if (t.isIN(p_lc)) {
                m_botAction.sendPrivateMessage(name, "Error: " + p.getPlayerName()
                        + " is already on your team, check with !list");
                return;
            }

            /* Check if the player was already on the other team */
            if (getOtherTeam(name, override).isOnTeam(p_lc)) {
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
            if (t.getShipCount(shipType) >= config.getMaxShips(shipType) && config.getMaxShips(shipType) != -1) {
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
     * Handles the !cap command
     *
     * @param name player that issued the !cap command
     */
    private void cmd_cap(String name) {
        HockeyTeam t;
        name = name.toLowerCase();

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
     * Handles the !change command
     *
     * @param name name of the player that issued the command
     * @param cmd command
     * @param override teamnumber to override, else -1
     */
    private void cmd_change(String name, String cmd, int override) {
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
        if (cmd.length() < 8) {
            m_botAction.sendPrivateMessage(name,
                    "Error: Please specify a playername and shiptype, !change <player>:<# shiptype>");
            return;
        }

        splitCmd = cmd.substring(8).split(":"); //Split command in 1. <player> 2. <# shiptype>

        /* Check command syntax */
        if (splitCmd.length < 2) {
            m_botAction.sendPrivateMessage(name,
                    "Error: Please specify a playername and shiptype, !change <player>:<# shiptype>");
            return;
        }

        p = t.searchPlayer(splitCmd[0]);    //Search for the player

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
        if (t.getShipCount(shipType) >= config.getMaxShips(shipType) && config.getMaxShips(shipType) != -1) {
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
     * Handles the !forcenp command
     * Forces a player to !notplaying
     *
     * @param name name of the player that issued the command
     * @param cmd name of the player that needs to get forced into !notplaying
     */
    private void cmd_forcenp(String name, String cmd) {
        Player p;

        cmd = cmd.substring(8).trim();

        p = m_botAction.getFuzzyPlayer(cmd);

        if (p == null) {
            m_botAction.sendPrivateMessage(name, cmd + " could not be found.");
            return;
        }

        if (listNotplaying.contains(p.getPlayerName().toLowerCase())) {
            m_botAction.sendPrivateMessage(name, p.getPlayerName() + " is already set to !notplaying.");
            return;
        }

        cmd_notplaying(p.getPlayerName());

        m_botAction.sendPrivateMessage(name, p.getPlayerName() + " has been set to !notplaying.");
    }

    /**
     * Handles the !help command
     *
     * @param name name of the player that issued the !help command
     */
    private void cmd_help(String name) {
        //TODO clean this up
        ArrayList<String> help = new ArrayList<String>();   //Help messages

        if (currentState == HockeyState.WAITING_FOR_CAPS) {
            if (config.getAllowAutoCaps()) {
                help.add("!cap                      -- Become captain of a team");
            } else {
                help.add("!cap                      -- List captains");
            }
            if (isCaptain(name)) {
                help.add("!removecap                -- Removes you as a captain");
            }
        } else if (currentState == HockeyState.ADDING_PLAYERS) {
            if (isCaptain(name)) {
                help.add("!add <player>             -- Adds player");
                help.add("!add <player>:<ship>      -- Adds player in the specified ship");
            }
            help.add("!cap                      -- Become captain of a team / shows current captains!");
            if (isCaptain(name)) {
                help.add("!change <player>:<ship>   -- Sets the player in the specified ship");
            }
            help.add("!lagout                   -- Puts you back into the game if you have lagged out");
            help.add("!list                     -- Lists all players on this team");
            help.add("!myfreq                   -- Puts you on your team's frequency");
            //help.add("!mvp                      -- Displays the current mvp");
            help.add("!rating <player>          -- Displays your/<player> current rating");
            help.add("!score <player>           -- Displays your/<player> current score");
            if (currentState == HockeyState.ADDING_PLAYERS && isCaptain(name)) {
                help.add("!ready                    -- Use this when you're done setting your lineup");
                help.add("!remove <player>          -- Removes specified player)");
            }
            if (isCaptain(name)) {
                help.add("!removecap                -- Removes you as a captain");
                help.add("!sub <playerA>:<playerB>  -- Substitutes <playerA> with <playerB>");
                help.add("!switch <player>:<player> -- Exchanges the ship of both players");
            }
        }

        help.add("!status                   -- Display status and score");

        if (currentState != HockeyState.OFF) {
            help.add("!notplaying               -- Toggles not playing mode  (short !np)");
            help.add("!subscribe                -- Toggles alerts in private messages");
        }

        if (m_botAction.getOperatorList().isModerator(name)) {
            help.add("MOD commands:");
            help.add("!off                      -- stops the bot after the current game");
            help.add("!die                      -- disconnects the bot");
        }

        if (m_botAction.getOperatorList().isZH(name)) {
            help.add("ZH+ commands:");
            help.add("!start                            -- starts the bot");
            help.add("!stop                             -- stops the bot");
            help.add("!ball                             -- retrieves the ball");
            help.add("!drop                             -- drops the ball");
            if (!config.getAllowAutoCaps()) {
                help.add("!zone <message>                   -- sends time-restricted advert, message is optional");
            }
            help.add("!forcenp <player>                 -- Sets <player> to !notplaying");
            if (currentState == HockeyState.WAITING_FOR_CAPS) {
                help.add("!setcaptain <# freq>:<player>     -- Sets <player> as captain for <# freq>");
                help.add("!setcaptain <player>      -- Sets <player> to captain");
                help.add("!removecap                -- Removes the cap of team !t#");
            }
        }

        if (m_botAction.getOperatorList().isSmod(name)) {
            help.add("Smod+ command:");
            help.add("!allowzoner                       -- Forces the zone timers to reset allowing !zone");
        }

        String[] spam = help.toArray(new String[help.size()]);
        m_botAction.privateMessageSpam(name, spam);
    }

    /**
     * Handles the !lagout/!return command
     *
     * @param name name of the player that issued the !lagout command
     */
    private void cmd_lagout(String name) {
        if (currentState == HockeyState.ADDING_PLAYERS
                || currentState == HockeyState.GAME_IN_PROGRESS
                || currentState == HockeyState.FACE_OFF
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
            if (listNotplaying.contains(name.toLowerCase())) {
                listNotplaying.remove(name.toLowerCase());  //Remove from him from the notplaying list
                m_botAction.sendPrivateMessage(name,
                        "You have been removed from the not playing list.");   //Notify the player
                /* Put the player on the spectator frequency */
                m_botAction.setShip(name, 1);
                m_botAction.specWithoutLock(name);
                return;
            }

            /* Add the player to the notplaying list */
            listNotplaying.add(name.toLowerCase()); //Add the player to the notplaying list
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
     * Handles the !off command
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
     * Handles the !ready command
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
     * Handles the !remove command
     *
     * @param name name of the player that issued the !remove command
     * @param cmd command parameters
     * @param override override 0/1 for teams, -1 if not overriden
     */
    private void cmd_remove(String name, String cmd, int override) {
        HockeyTeam t;
        HockeyPlayer p;   //Player to be removed

        if (currentState == HockeyState.ADDING_PLAYERS || currentState == HockeyState.FACE_OFF
                || currentState == HockeyState.GAME_IN_PROGRESS) {
            t = getTeam(name, override); //Retrieve team

            /* Check command syntax */
            if (cmd.length() < 8) {
                m_botAction.sendPrivateMessage(name, "Error: Please specify a player, !remove <player>");
                return;
            }

            cmd = cmd.substring(8);

            /* Check command syntax */
            if (cmd.isEmpty()) {
                m_botAction.sendPrivateMessage(name, "Error: Please specify a player, !remove <player>");
                return;
            }

            p = t.searchPlayer(cmd); //Search for player to remove

            /* Check if player has been found */
            if (p == null) {
                m_botAction.sendPrivateMessage(name, "Error: Player could not be found");
                return;
            }

            t.removePlayer(p.getName());

            determineTurn();
        }
    }

    /**
     * Handles the !removecap command
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
     * Handles the !setcaptain command
     *
     * @param name name of the player that issued the !setcaptain command
     * @param cmd command parameters
     * @param override override 0/1 for teams, -1 if not overriden
     */
    private void cmd_setCaptain(String name, String cmd, int override) {
        int frequency;
        Player p;
        String[] splitCmd;

        /* Alter command if overriden */
        if (override != -1) {
            cmd = "!setcaptain " + override + ":" + cmd.substring(11).trim();
        }

        if (currentState != HockeyState.OFF && currentState != HockeyState.GAME_OVER) {
            cmd = cmd.substring(11).trim(); //Cut of !setcaptain part

            /* Check command syntax */
            if (cmd.isEmpty()) {
                m_botAction.sendPrivateMessage(name, "Error: please specify a player, "
                        + "'!setcaptain <# freq>:<player>', or '!t1-/!t2-setcaptain <player>'");
                return;
            }

            splitCmd = cmd.split(":"); //Split parameters

            /* Check command syntax */
            if (splitCmd.length < 2) {
                m_botAction.sendPrivateMessage(name, "Error: please specify a player, "
                        + "'!setcaptain <# freq>:<player>', or '!t1-/!t2-setcaptain <player>'");
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
                frequency = Integer.parseInt(splitCmd[0]);
            } catch (Exception e) {
                m_botAction.sendPrivateMessage(name, "Error: please specify a correct frequency, "
                        + "'!setcaptain <# freq>:<player>', or '!t1-/!t2-setcaptain <player>'");
                return;
            }

            /* Check if frequency is valid */
            if (frequency == 0) {
                team0.setCaptain(p.getPlayerName()); //Set player to captain
            } else if (frequency == 1) {
                team1.setCaptain(p.getPlayerName());
            } else {
                m_botAction.sendPrivateMessage(name, "Error: please specify a correct frequency, "
                        + "'!setcaptain <# freq>:<player>', or '!t1-/!t2-setcaptain <player>'");
            }





        }
    }

    /**
     * Handles the !start command
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
     * Handles the !stop command
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
     * Handles the !sub command
     *
     * @param name name of the player that issued the !sub command
     * @param cmd command parameters
     * @param override 0/1 for teams, -1 for not overriden
     */
    private void cmd_sub(String name, String cmd, int override) {
        HockeyTeam t;
        String[] splitCmd;
        HockeyPlayer playerA;
        HockeyPlayer playerB;
        Player playerBnew;

        if (currentState == HockeyState.GAME_IN_PROGRESS || currentState == HockeyState.FACE_OFF
                || currentState == HockeyState.ADDING_PLAYERS) {
            t = getTeam(name, override); //Retrieve teamnumber

            if (t == null) {
                return;
            }

            cmd = cmd.substring(4).trim();  //Remove !sub part of the cmd

            /* Check command syntax */
            if (cmd.isEmpty()) {
                m_botAction.sendPrivateMessage(name, "Error: Specify players, !sub <playerA>:<playerB>");
                return;
            }

            splitCmd = cmd.split(":");

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

            playerA = t.searchPlayer(splitCmd[0]);   //Search for <playerA>
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
            if (listNotplaying.contains(playerBnew.getPlayerName().toLowerCase())) {
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

            t.sub(playerA, playerBnew); //Execute the substitute
        }
    }

    /**
     * Handles the !subscribe command
     *
     * @param name player that issued the !subscribe command
     */
    private void cmd_subscribe(String name) {
        if (currentState != HockeyState.OFF) {
            name = name.toLowerCase();

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
     * Handles the !switch command
     *
     * @param name player that issued the !switch command
     * @param cmd command parameters
     * @param override override 0/1 for teams, -1 for not overriden
     */
    private void cmd_switch(String name, String cmd, int override) {
        HockeyTeam t;
        String[] splitCmd;
        HockeyPlayer playerA;
        HockeyPlayer playerB;

        if (currentState == HockeyState.ADDING_PLAYERS || currentState == HockeyState.FACE_OFF
                || currentState == HockeyState.GAME_IN_PROGRESS) {
            t = getTeam(name, override); //Retrieve team number

            cmd = cmd.substring(7).trim(); //Cut off the !switch part of the command

            /* Check command syntax */
            if (cmd.isEmpty()) {
                m_botAction.sendPrivateMessage(name,
                        "Error: Specify players to be switched, !switch <playerA>:<playerB>");
                return;
            }

            splitCmd = cmd.split(":"); //Split command parameters

            /* Check command syntax */
            if (splitCmd.length < 2) {
                m_botAction.sendPrivateMessage(name,
                        "Error: Specify players to be switched, !switch <playerA>:<playerB>");
                return;
            }

            playerA = t.searchPlayer(splitCmd[0]); //Search <playerA>
            playerB = t.searchPlayer(splitCmd[1]); //Search <playerB>

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
     * Handles the !zone command
     *
     * @param name name of the player that issued the command
     * @param message message to use for zoner
     */
    private void cmd_zone(String name, String message) {

        //grab message from !zone message if there
        String msg = null;
        if (message.length() > 6) {
            msg = message.substring(6);
        }

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

        newGameAlert(name, msg);
    }

    private void cmd_allowZoner(String name) {
        zonerTimestamp = zonerTimestamp - (ZONER_WAIT_TIME * Tools.TimeInMillis.MINUTE);
        manualZonerTimestamp = manualZonerTimestamp - (10 * Tools.TimeInMillis.MINUTE);
        m_botAction.sendPrivateMessage(name, "Zone message timestamps have been reset.");
    }

    /*
     * Game modes
     */
    /**
     * Starts the bot
     */
    private void start() {
        lockLastGame = false;
        lockArena();
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
        reset();

        currentState = HockeyState.WAITING_FOR_CAPS;
        if (config.getAllowAutoCaps()) {
            m_botAction.sendArenaMessage("A new game will start when two people message me with !cap -"
                    + m_botAction.getBotName(), Tools.Sound.BEEP2);
        } else {
            m_botAction.sendArenaMessage("Request a new game with '?help start hockey please'"
                    + " -" + m_botAction.getBotName(), Tools.Sound.BEEP2);
        }
    }

    /**
     * Start adding players state
     * - Notify arena
     * - Notify chats
     * - Determine next pick
     */
    private void startAddingPlayers() {
        currentState = HockeyState.ADDING_PLAYERS;

        timeStamp = System.currentTimeMillis();
        m_botAction.sendArenaMessage("Captains you have 10 minutes to set up your lineup correctly!",
                Tools.Sound.BEEP2);

        if (config.getAllowAutoCaps()) {
            newGameAlert(null, null);
        }

        if (team0.hasCaptain()) {
            team0.putCaptainInList();
        }

        if (team1.hasCaptain()) {
            team1.putCaptainInList();
        }

        determineTurn();
    }

    /**
     * Starts pre game
     */
    private void startFaceOff() {
        currentState = HockeyState.FACE_OFF;

        puck.clear();
        team0.clearUnsetPenalties();
        team1.clearUnsetPenalties();

        m_botAction.sendArenaMessage("Lineup For FaceOff", Tools.Sound.CROWD_OOO);

        timeStamp = System.currentTimeMillis();
    }

    private void startReview(SoccerGoal event) {

        Point release = puck.getLastReleasePoint();

        int pX0 = Math.abs(config.team0GoalX - release.x);
        int pY0 = Math.abs(config.team0GoalY - release.y);
        double distance0 = Math.sqrt(Math.pow(pX0, 2) + Math.pow(pY0, 2));

        int pX1 = Math.abs(config.team1GoalX - release.x);
        int pY1 = Math.abs(config.team1GoalY - release.y);
        double distance1 = Math.sqrt(Math.pow(pX1, 2) + Math.pow(pY1, 2));

        if (distance0 < config.getGoalRadius() && event.getFrequency() == 1) {
            m_botAction.sendArenaMessage("CREASE. No count.", Tools.Sound.CROWD_GEE);
        } else if (distance0 < config.getGoalRadius() && event.getFrequency() == 0) {
            m_botAction.sendArenaMessage("OWN GOAL!", Tools.Sound.GAME_SUCKS);
            team1.increaseScore();
        } else if (distance1 < config.getGoalRadius() && event.getFrequency() == 0) {
            m_botAction.sendArenaMessage("CREASE. No count.", Tools.Sound.CROWD_GEE);
        } else if (distance1 < config.getGoalRadius() && event.getFrequency() == 1) {
            m_botAction.sendArenaMessage("OWN GOAL!", Tools.Sound.GAME_SUCKS);
            team0.increaseScore();
        } else if (puck.veloX > 0 && event.getFrequency() == 1) {
            m_botAction.sendArenaMessage("OWN GOAL!", Tools.Sound.GAME_SUCKS);
            team0.increaseScore();
        } else if (puck.veloX < 0 && event.getFrequency() == 0) {
            m_botAction.sendArenaMessage("OWN GOAL!", Tools.Sound.GAME_SUCKS);
            team1.increaseScore();
        } else {
            m_botAction.sendArenaMessage("Clean!");
            if (event.getFrequency() == 0) {
                team0.increaseScore();
            } else {
                team1.increaseScore();
            }
        }


        if (team0.getScore() >= 7) {
            gameOver(0);
        } else if (team1.getScore() >= 7) {
            gameOver(1);
        } else {
            displayScores();
            startFaceOff();
        }
    }

    /**
     * Starts a game
     */
    private void startGame() {
        currentState = HockeyState.GAME_IN_PROGRESS;
        dropBall();

        timeStamp = System.currentTimeMillis();
        m_botAction.sendArenaMessage("Go go go!!!", Tools.Sound.GOGOGO);
    }

    /**
     * What to do with when game is over
     */
    private void gameOver(int winningFreq) {

        currentState = HockeyState.GAME_OVER;

        //Cancel timer
        m_botAction.setTimer(0);

        m_botAction.sendArenaMessage("Result of " + team0.getName() + " vs. "
                + team1.getName(), Tools.Sound.HALLELUJAH);

        displayScores();
    }

    /*
     * Tools
     */
    private String getMVP() {
        //Todo update this to get score from players
        String mvp;
        //int highestRating;

        //highestRating = 0;
        mvp = "";

        /*for (HockeyTeam i : team) {
        for (HockeyPlayer p : i.players.values()) {
        if (highestRating < p.getTotalRating()) {
        highestRating = p.getTotalRating();
        mvp = p.getName();
        }
        }
        }*/

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

    /** Sends the captain list to the player */
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
        name = name.toLowerCase();

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

        name = name.toLowerCase();

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
        if (t.offside.contains(name)) {
            t.offside.remove(name);
        }
        if (t.fCrease.contains(name)) {
            t.offside.remove(name);
        }
        if (t.dCrease.contains(name)) {
            t.offside.remove(name);
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

        name = name.toLowerCase();

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
        int teamNumber;

        if (!team0.getTurn() && !team1.getTurn()) {
            teamNumber = -1;

            if (team0.players.size() <= team1.players.size()) {
                teamNumber = 0;
            } else if (team1.players.size() < team0.players.size()) {
                teamNumber = 1;
            }


            if (teamNumber == 0) {
                if (team0.players.size() != config.getMaxPlayers()) {
                    m_botAction.sendArenaMessage(team0.captainName + " pick a player!", Tools.Sound.BEEP2);
                    team0.setTurn();
                }
            } else if (teamNumber == 1) {
                if (team1.players.size() != config.getMaxPlayers()) {
                    m_botAction.sendArenaMessage(team1.captainName + " pick a player!", Tools.Sound.BEEP2);
                    team1.setTurn();
                }
            }
        }
    }

    /**
     * Returns BWJSTeam of the player with "name"
     *
     * @param name name of the player
     * @return BWJSTeam of the player, null if not on any team
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
     * @return BWJSTeam, null if player doesn't belong to any team
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
     * Returns the opposite team according to BWJSTeam
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
     * Returns BWJSTeam of the player with "name" or according to the override
     *
     * @param name name of the player
     * @param override override number 0 for Freq 0, 1 for Freq 1, -1 for normal
     * @return BWJSTeam, null if player doesn't belong to any team
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
        if (message.equals("Arena UNLOCKED") && lockArena) {
            m_botAction.toggleLocked();
        }
        if (message.equals("Arena LOCKED") && !lockArena) {
            m_botAction.toggleLocked();
        }
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
        currentState = HockeyState.FACE_OFF;

        if (team0.players.size() >= config.getMinPlayers() && team1.players.size() >= config.getMinPlayers()) {
            m_botAction.sendArenaMessage("Lineups are ok! Game will start in 30 seconds!", Tools.Sound.CROWD_OOO);

            startFaceOff();
        } else {
            m_botAction.sendArenaMessage("Lineups are NOT ok! :( Game has been reset.", Tools.Sound.CROWD_GEE);
            startWaitingForCaps();
        }
    }

    /**
     * Resets variables to their default value
     */
    private void reset() {
        gameTime = 0;
        team0.resetVariables();
        team1.resetVariables();
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
            if (i.getShipType() != Tools.Ship.SPECTATOR) {
                m_botAction.specWithoutLock(id);
            }
            if (listNotplaying.contains(i.getPlayerName().toLowerCase()) && freq != FREQ_NOTPLAYING) {
                m_botAction.setFreq(id, FREQ_NOTPLAYING);
            } else if (freq != FREQ_SPEC && !listNotplaying.contains(i.getPlayerName().toLowerCase())) {
                m_botAction.setShip(id, 1);
                m_botAction.specWithoutLock(id);
            }
        }
    }

    /**
     * Displays the scores
     */
    private void displayScores() {
        //TODO fix score display
        ArrayList<String> spam = new ArrayList<String>();
        spam.add("----------------------------------+----");
        spam.add("|     Freq 0      |       Freq 1       |");
        spam.add("|       " + team0.getScore() + (team0.getScore() < 10 ? " " : "") + "        |         "
                + team1.getScore() + (team1.getScore() < 10 ? " " : "") + "         |");
        spam.add("---------------------------------------");

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
        private String chats;
        private String arena;
        private int maxLagouts;
        private int maxSubs;
        private int maxPlayers;
        private int minPlayers;
        private int defaultShipType;
        private int[] maxShips;
        private boolean announceShipType;
        private boolean allowAutoCaps;
        private boolean allowZoner;
        //#Coordinate for puck drop
        private int puckDropX;
        private int puckDropY;
        private int puckDropRadius;
        //#X Coordinates for blue lines
        private int team0BlueLine;
        private int team1BlueLine;
        //#Coordinates for goals
        private int team0GoalX;
        private int team0GoalY;
        private int team1GoalX;
        private int team1GoalY;
        private int goalRadius;
        private boolean allowVote;
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
            int tmpAnnounceShipCounter;
            String[] maxShipsString;

            //Arena
            arena = botSettings.getString("Arena");

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

            //Max Amount of Substitutes Allowed
            maxSubs = botSettings.getInt("MaxSubs");

            //Announce Ship Type
            if (tmpAnnounceShipCounter > 1) {
                announceShipType = true;
            } else {
                announceShipType = false;
            }

            //Min Players
            minPlayers = botSettings.getInt("MinPlayers");

            //#Coordinate for puck drop
            puckDropX = botSettings.getInt("PuckDropX");
            puckDropY = botSettings.getInt("PuckDropY");
            puckDropRadius = botSettings.getInt("PuckDropRadius");

            //#X Coordinates for blue lines
            team0BlueLine = botSettings.getInt("Team0BlueLine");
            team1BlueLine = botSettings.getInt("Team1BlueLine");

            //#Coordinates for goals
            team0GoalX = botSettings.getInt("Team0GoalX");
            team0GoalY = botSettings.getInt("Team0GoalY");
            team1GoalX = botSettings.getInt("Team1GoalX");
            team1GoalY = botSettings.getInt("Team1GoalY");
            goalRadius = botSettings.getInt("GoalRadius");

            //Coordinate for penalty boxes
            team0PenX = botSettings.getInt("Team0PenX");
            team0PenY = botSettings.getInt("Team0PenY");
            team1PenX = botSettings.getInt("Team1PenX");
            team1PenY = botSettings.getInt("Team1PenY");

            team0ExtX = botSettings.getInt("Team0ExtX");
            team0ExtY = botSettings.getInt("Team0ExtY");
            team1ExtX = botSettings.getInt("Team1ExtX");
            team1ExtY = botSettings.getInt("Team1ExtY");

            //penalty time
            penaltyTime = botSettings.getInt("PenaltyTime");
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
         * @return the puckDropRadius
         */
        public int getPuckDropRadius() {
            return puckDropRadius;
        }

        /**
         * @return the team0BlueLine
         */
        public int getTeam0BlueLine() {
            return team0BlueLine;
        }

        /**
         * @return the team1BlueLine
         */
        public int getTeam1BlueLine() {
            return team1BlueLine;
        }

        /**
         * @return the team0GoalX
         */
        public int getTeam0GoalX() {
            return team0GoalX;
        }

        /**
         * @return the team0GoalY
         */
        public int getTeam0GoalY() {
            return team0GoalY;
        }

        /**
         * @return the team1GoalX
         */
        public int getTeam1GoalX() {
            return team1GoalX;
        }

        /**
         * @return the team1GoalY
         */
        public int getTeam1GoalY() {
            return team1GoalY;
        }

        /**
         * @return the goalRadius
         */
        public int getGoalRadius() {
            return goalRadius;
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
         * @return the penaltyTime
         */
        public int getPenaltyTime() {
            return penaltyTime;
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
        private long penaltyTimestamp;  //in gametime which increments from 0 each second of gameplay

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
                m_botAction.setShip(p_name, shipType);
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
                    return "OUT";
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
                if (!listNotplaying.contains(p_name.toLowerCase())) {
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

        private void setPenalty(HockeyPenalty pen) {
            this.penalty = pen;
            this.penaltyTimestamp = gameTime;
            if (team0.isPlayer(p_name)) {
                m_botAction.warpTo(p_name, config.getTeam0PenX() / 16, config.getTeam0PenY() / 16);
            } else {
                m_botAction.warpTo(p_name, config.getTeam1PenX() / 16, config.getTeam1PenY() / 16);
            }
        }
    }

    private class HockeyTeam {

        private boolean flag;
        private boolean turnToPick;
        private boolean ready;
        private int flagTime;
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
            ;
        }

        private void clearUnsetPenalties() {
            try {
                offside.clear();
                dCrease.clear();
                fCrease.clear();
            } catch (Exception e) {
            }
        }

        /**
         * Increases team score by 1.
         */
        private void increaseScore() {
            teamScore++;
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

            name = name.toLowerCase();

            playerState = -1;   //-1 if not found

            if (players.containsKey(name)) {
                playerState = players.get(name).getCurrentState();
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
            boolean isOnTeam;

            name = name.toLowerCase();

            if (players.containsKey(name)) {
                isOnTeam = true;
            } else if (name.equalsIgnoreCase(captainName)) {
                isOnTeam = true;
            } else {
                isOnTeam = false;
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
            boolean isPlayer;

            name = name.toLowerCase();

            if (players.containsKey(name)) {
                isPlayer = true;
            } else {
                isPlayer = false;
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
            boolean isIN;

            name = name.toLowerCase();
            isIN = false;

            if (players.containsKey(name)) {
                if (players.get(name).getCurrentState() != HockeyPlayer.SUBBED) {
                    isIN = true;
                }
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
            name = name.toLowerCase();

            if (!players.containsKey(name)) {
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
            name = name.toLowerCase();

            if (players.containsKey(name)) {
                players.get(name).lagout();
            }

            //Notify captain if captian is in the arena
            if (m_botAction.getPlayer(captainName) != null) {
                m_botAction.sendPrivateMessage(captainName, name + " lagged out!");
            }

            //NOTE: the team is notified in the BWJSPlayer lagout() method
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
            name = name.toLowerCase();
            if (players.containsKey(name)) {
                players.get(name).timestampLastPosition();
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
         * Returns the amount of ships of shiptype in use
         *
         * @param shiptype type of ship
         * @return amount of shiptype in
         */
        private int getShipCount(int shiptype) {
            int shipCount;

            shipCount = 0;

            for (HockeyPlayer i : players.values()) {
                if (i.p_state < HockeyPlayer.SUBBED && i.p_currentShip == shiptype) {
                    shipCount++;
                }
            }

            return shipCount;
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
            String p_lc;            //Player name in lowercase

            p_lc = p.getPlayerName().toLowerCase();

            captainMessage = p.getPlayerName() + " has been added.";
            playerMessage = "You've been added to the game.";

            if (config.announceShipType()) {
                arenaMessage = p.getPlayerName() + " is in for " + teamName + " as a " + Tools.shipName(shipType) + ".";
            } else {
                arenaMessage = p.getPlayerName() + " is in for " + teamName + ".";
            }

            /* Send the messages */
            m_botAction.sendArenaMessage(arenaMessage);
            m_botAction.sendPrivateMessage(captainName, captainMessage);
            m_botAction.sendPrivateMessage(p.getPlayerName(), playerMessage);

            if (!players.containsKey(p_lc)) {
                players.put(p_lc, new HockeyPlayer(p.getPlayerName(), shipType, frequency));
            } else {
                players.get(p_lc).putIN(shipType);
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
         * @return BWJSPlayer if found, else null
         */
        private HockeyPlayer searchPlayer(String name) {
            HockeyPlayer p;

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

            name = name.toLowerCase();

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

            name = name.toLowerCase();

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
            name = name.toLowerCase();

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
            name = name.toLowerCase();

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
            name = name.toLowerCase();

            if (players.containsKey(name)) {
                players.remove(name);
            }

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
         * Sets player to not playing modus, player will still be subable
         *
         * @param name Name of the player that should be set to out notplaying
         */
        private void setOutNotPlaying(String name) {
            name = name.toLowerCase();

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

            //Removing player
            playerOne.sub();

            //Adding substitute
            if (players.containsKey(playerTwo.getPlayerName().toLowerCase())) {
                HockeyPlayer p = players.get(playerTwo.getPlayerName().toLowerCase());
                p.p_currentShip = shipType;
                p.addPlayer();
            } else {
                players.put(playerTwo.getPlayerName().toLowerCase(),
                        new HockeyPlayer(playerTwo.getPlayerName(), shipType, frequency));
            }

            m_botAction.sendPrivateMessage(playerTwo.getPlayerID(), "You are subbed in the game.");

            m_botAction.sendArenaMessage(playerOne.p_name + " has been substituted by "
                    + playerTwo.getPlayerName());

            if (substitutesLeft != -1) {
                substitutesLeft--;
            }

            if (substitutesLeft >= 0) {
                m_botAction.sendSmartPrivateMessage(captainName, "You have "
                        + substitutesLeft + " substitutes left.");
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
            }

            if (m_botAction.getPlayer(playerTwo.p_name) != null) {
                playerTwo.switchPlayer();
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
        private long timestamp;
        private short ballX;
        private short ballY;
        private short veloX;
        private boolean carried;
        private String carrier;
        private Stack<String> carriers;
        private Stack<Point> releases;
        private boolean holding;

        public HockeyPuck() {
            carrier = null;
            carriers = new Stack<String>();
            releases = new Stack<Point>();
            carried = false;
            holding = false;
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
            } catch (Exception e) {
            }
        }

        public byte getBallID() {
            return ballID;
        }

        public long getTimeStamp() {
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

    private class Gameticker extends TimerTask {

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
            }
        }

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

        private void doAddingPlayers() {
            //Check if time has ended for adding players
            int multiplier = 10;

            if ((System.currentTimeMillis() - timeStamp) >= Tools.TimeInMillis.MINUTE * multiplier) {
                m_botAction.sendArenaMessage("Time is up! Checking lineups..");
                checkLineup();
            }
        }

        private void doFaceOff() {

            if (!puck.holding) {
                timeStamp = System.currentTimeMillis();
                getBall();
            }

            long time = (System.currentTimeMillis() - timeStamp)
                    / Tools.TimeInMillis.SECOND;

            //DROP WARNING
            if (time == 20) {
                m_botAction.sendArenaMessage("Get READY! DROP in 10 seconds.", 1);
                try {
                    if (!team0.offside.empty()) {
                        Iterator<String> i = team0.offside.iterator();
                        while (i.hasNext()) {
                            String name = i.next();
                            m_botAction.sendPrivateMessage(name, "WARNING: You are "
                                    + "offside. Get left (<-) of the center red line "
                                    + "before drop or you will receive a penalty.");
                        }
                    }
                } catch (Exception e) {
                }
                try {
                    if (!team1.offside.empty()) {
                        Iterator<String> i = team1.offside.iterator();
                        while (i.hasNext()) {
                            String name = i.next();
                            m_botAction.sendPrivateMessage(name, "WARNING: You are "
                                    + "offside. Get right (->) of the center red line "
                                    + "before drop or you will receive a penalty.");
                        }
                    }
                } catch (Exception e) {
                }
                try {
                    if (team0.fCrease.size() > 1) {
                        Iterator<String> i = team0.fCrease.iterator();
                        while (i.hasNext()) {
                            String name = i.next();
                            m_botAction.sendPrivateMessage(name, "WARNING: Only one "
                                    + "member per team is allowed in crease during "
                                    + "Face Off. The last players who entered leave "
                                    + "the crease or you will recieve a penalty.");
                        }
                    }
                } catch (Exception e) {
                }
                try {
                    if (team1.fCrease.size() > 1) {
                        Iterator<String> i = team1.fCrease.iterator();
                        while (i.hasNext()) {
                            String name = i.next();
                            m_botAction.sendPrivateMessage(name, "WARNING: Only one "
                                    + "member per team is allowed in crease during "
                                    + "Face Off. The last players who entered leave "
                                    + "the crease or you will recieve a penalty.");
                        }
                    }
                } catch (Exception e) {
                }
            }

            //CHECK PENALTIES AND DROP
            if (time >= 29) {
                try {
                    if (!team0.offside.empty()) {
                        Iterator<String> i = team0.offside.iterator();
                        while (i.hasNext()) {
                            String name = i.next();
                            team0.searchPlayer(name).setPenalty(HockeyPenalty.OFFSIDE);
                            m_botAction.sendArenaMessage("OFFSIDE PENALTY: " + name);
                        }
                    }
                } catch (Exception e) {
                }
                try {
                    if (!team1.offside.empty()) {
                        Iterator<String> i = team1.offside.iterator();
                        while (i.hasNext()) {
                            String name = i.next();
                            team1.searchPlayer(name).setPenalty(HockeyPenalty.OFFSIDE);
                            m_botAction.sendArenaMessage("OFFSIDE PENALTY: " + name);
                        }
                    }
                } catch (Exception e) {
                }
                try {
                    if (team0.fCrease.size() > 1) {
                        Iterator<String> i = team0.fCrease.iterator();
                        while (i.hasNext()) {
                            String name = i.next();
                            team0.searchPlayer(name).setPenalty(HockeyPenalty.FO_CREASE);
                            m_botAction.sendArenaMessage("FACEOFF CREASE PENALTY: " + name);
                        }
                    }
                } catch (Exception e) {
                }
                try {
                    if (team1.fCrease.size() > 1) {
                        Iterator<String> i = team1.fCrease.iterator();
                        while (i.hasNext()) {
                            String name = i.next();
                            team1.searchPlayer(name).setPenalty(HockeyPenalty.FO_CREASE);
                            m_botAction.sendArenaMessage("FACEOFF CREASE PENALTY: " + name);
                        }
                    }
                } catch (Exception e) {
                }
            }

            if (time >= 30) {
                startGame();
            }
        }

        private void doStartGame() {
            roundTime = ((System.currentTimeMillis() - timeStamp)
                    / Tools.TimeInMillis.SECOND);
            try {
                if (team0.dCrease.size() > 1) {
                    Iterator<String> i = team0.dCrease.iterator();
                    while (i.hasNext()) {
                        String name = i.next();
                        if (name.equals(puck.carrier)) {
                            team0.searchPlayer(name).setPenalty(HockeyPenalty.FO_CREASE);
                            m_botAction.sendArenaMessage("DEFENSE CREASE PENALTY: " + name);
                            startFaceOff();
                        }
                    }
                }
            } catch (Exception e) {
            }
            try {
                if (team1.dCrease.size() > 1) {
                    Iterator<String> i = team1.dCrease.iterator();
                    while (i.hasNext()) {
                        String name = i.next();
                        if (name.equals(puck.carrier)) {
                            team1.searchPlayer(name).setPenalty(HockeyPenalty.FO_CREASE);
                            m_botAction.sendArenaMessage("DEFENSE CREASE PENALTY: " + name);
                            startFaceOff();
                        }
                    }
                }
            } catch (Exception e) {
            }

            gameTime++;

        }

        private void doReview() {
            //would do voting here
        }

        private void doGameOver() {
            long time;

            time = (System.currentTimeMillis() - timeStamp) / Tools.TimeInMillis.SECOND;

            if (!lockLastGame && (time >= 15)) {
                startWaitingForCaps();
            } else if (time >= 15) {
                currentState = HockeyState.OFF;
                m_botAction.sendArenaMessage("Bot has been shutdown.", Tools.Sound.GAME_SUCKS);
                reset();
                unlockArena();
            }
        }
    }

    /*private void debugMessage(String msg) {
    m_botAction.sendPrivateMessage("Spook <ZH>", msg);
    }*/
}
