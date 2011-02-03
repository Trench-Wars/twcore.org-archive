package twcore.bots.matchbot;

import java.io.File;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.TimerTask;
import java.util.TreeSet;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.BallPosition;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FlagReward;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.Prize;
import twcore.core.events.ScoreReset;
import twcore.core.events.SoccerGoal;
import twcore.core.events.TurretEvent;
import twcore.core.events.WatchDamage;
import twcore.core.events.WeaponFired;
import twcore.core.game.Player;
import twcore.core.stats.DBPlayerData;
import twcore.core.util.Spy;
import twcore.core.util.Tools;

/**
 * Runs automated squad vs. squad TWD matches.
 */
public class matchbot extends SubspaceBot {

    MatchGame m_game;
    String m_arena;
    BotSettings m_botSettings;
    OperatorList m_opList;
    LinkedList<String> m_arenaList;
    LinkedList<GameRequest> m_gameRequests;
    TimerTask m_gameKiller;
    String startMessage;

    String dbConn = "website";

    //
    boolean ipcTimer = false;
    boolean m_isLocked = false;
    boolean m_aliasCheck = false;
    boolean m_isStartingUp = false;
    boolean m_cancelGame = false;
    boolean m_off = false;
    boolean m_die = false;
    String m_locker;
    int m_lockState = 0;
    int m_typeNumber;
    int m_matchTypeID;
    //
    static int CHECKING_ARENAS = 1, LOCKED = 2;
    static int INACTIVE_MESSAGE_LIMIT = 3, ACTIVE_MESSAGE_LIMIT = 16;
    final static String IPC = "MatchBot";
    final static String TWDHUB = "TWDBot";
    // these variables are for when the bot is locked
    BotSettings m_rules;
    String m_rulesFileName;
    Spy racismSpy; // Equivalent to using a PubBot's spy module

    // The last time (in ms) that an advert was done for this game
    protected long lastAdvertTime = 0;

    /** The required ?obscene status */
    private boolean obsceneStatus = false;

    // --- temporary
    String m_team1 = null, m_team2 = null;
    String m_lock = "";
    TimerTask exp;

    // private static Pattern parseInfoRE =
    // Pattern.compile("^IP:(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})  TimeZoneBias:\\d+  Freq:\\d+  TypedName:(.*)  Demo:\\d  MachineId:(\\d+)$");
    // private static Pattern cruncherRE = Pattern.compile("\\s+");

    protected String previousCaptain1 = null;
    protected String previousCaptain2 = null;

    /** Creates a new instance of Matchtwl */
    public matchbot(BotAction botAction) {
        // Setup of necessary stuff for any bot.
        super(botAction);

        m_botSettings = m_botAction.getBotSettings();
        m_arena = m_botSettings.getString("Arena");
        m_opList = m_botAction.getOperatorList();
        m_gameRequests = new LinkedList<GameRequest>();

        requestEvents();
        racismSpy = new Spy(m_botAction);
    }

    public boolean isIdle() {
        return (m_game == null);
    }

    public static String[] stringChopper(String input, char deliniator) {

        LinkedList<String> list = new LinkedList<String>();

        int nextSpace = 0;
        int previousSpace = 0;

        if (input == null) {
            return null;
        }

        do {
            previousSpace = nextSpace;
            nextSpace = input.indexOf(deliniator, nextSpace + 1);

            if (nextSpace != -1) {
                String stuff = input.substring(previousSpace, nextSpace).trim();
                if (stuff != null && !stuff.equals(""))
                    list.add(stuff);
            }

        } while (nextSpace != -1);
        String stuff = input.substring(previousSpace);
        stuff = stuff.trim();
        if (stuff.length() > 0) {
            list.add(stuff);
        }

        return list.toArray(new String[list.size()]);
    }

    public int getBotNumber() {
        int nrBots = m_botSettings.getInt("Max Bots");
        for (int i = 1; i <= nrBots; i++) {
            if (m_botSettings.getString("Name" + i).equalsIgnoreCase(m_botAction.getBotName()))
                return i;
        }
        return 0;
    }

    public void requestEvents() {
        EventRequester req = m_botAction.getEventRequester();
        req.request(EventRequester.LOGGED_ON);
        req.request(EventRequester.FREQUENCY_SHIP_CHANGE);
        req.request(EventRequester.FREQUENCY_CHANGE);
        req.request(EventRequester.SCORE_RESET);
        req.request(EventRequester.FLAG_CLAIMED);
        req.request(EventRequester.FLAG_REWARD);
        req.request(EventRequester.PLAYER_DEATH);
        req.request(EventRequester.PLAYER_LEFT);
        req.request(EventRequester.PLAYER_ENTERED);
        req.request(EventRequester.PLAYER_POSITION);
        req.request(EventRequester.MESSAGE);
        req.request(EventRequester.ARENA_JOINED);
        req.request(EventRequester.WEAPON_FIRED);
        req.request(EventRequester.SOCCER_GOAL);
        req.request(EventRequester.BALL_POSITION);
        req.request(EventRequester.PRIZE);
        req.request(EventRequester.TURRET_EVENT);
        req.request(EventRequester.WATCH_DAMAGE);
    }

    /**
     * @param event
     *            The weapon Fired event
     */
    public void handleEvent(WeaponFired event) {
        if (m_game != null)
            m_game.handleEvent(event);
    }

    public void handleEvent(ArenaJoined event) {
        if (m_game != null) {
            m_game.handleEvent(event);
        }

        m_botAction.setReliableKills(1); // Reliable kills so the bot receives
                                         // every packet
        m_botAction.sendUnfilteredPublicMessage("?obscene");
        
        String arena = m_botAction.getArenaName().toLowerCase(); 
        if (arena.equalsIgnoreCase("twd")) {
            m_botAction.ipcTransmit(IPC, "twdmatchbot:spawned " + m_botAction.getBotName());
        } else if (isTWD() && m_isLocked) {
            m_botAction.ipcTransmit(IPC, "twdmatchbot:locked " + m_botAction.getBotName() + "," + arena);            
        }
    }

    public void handleEvent(SoccerGoal event) {
        if (m_game != null) {
            m_game.handleEvent(event);
        }
    }

    public void handleEvent(LoggedOn event) {
        m_botAction.ipcSubscribe(IPC);

        String def = m_botSettings.getString("Default" + getBotNumber());
        int typeNumber = getGameTypeNumber(def);

        if (typeNumber == 0) {
            m_botAction.joinArena(m_arena);
        } else {
            String[] param = { Integer.toString(typeNumber) };
            command_lock(m_botAction.getBotName(), param);
        }
        m_botAction.setMessageLimit(INACTIVE_MESSAGE_LIMIT);
        m_botAction.setPlayerPositionUpdating(300);
    }

    public void handleEvent(FlagClaimed event) {
        if (m_game != null) {
            m_game.handleEvent(event);
        }
    }

    public void handleEvent(FrequencyChange event) {
        if (m_game != null) {
            m_game.handleEvent(event);
        }
    }

    public void handleEvent(FrequencyShipChange event) {
        if (m_game != null) {
            m_game.handleEvent(event);
        }
    }

    public void handleEvent(FlagReward event) {
        if (m_game != null) {
            m_game.handleEvent(event);
        }
    }

    public void handleEvent(InterProcessEvent event) {
        if ((event.getChannel().equals(IPC))
                && (!event.getSenderName().equals(m_botAction.getBotName()))) {
            if (event.getObject() instanceof String) {
                String s = (String) event.getObject();
                if (s.equals("whatArena")) {
                    m_botAction.ipcTransmit(IPC, "myArena:"
                            + m_botAction.getArenaName());
                }

                if ((s.startsWith("myArena:")) && (m_isLocked)
                        && (m_lockState == CHECKING_ARENAS)) {
                    if (m_arenaList == null) {
                        m_arenaList = new LinkedList<String>();
                    }
                    m_arenaList.add(s.substring(8).toLowerCase());
                }

                String arena = m_botAction.getArenaName().toLowerCase();
                String bot = m_botAction.getBotName();
                if (s.startsWith("twdmatchbot:" + bot + ":denylock ")) {
                    String name = s.substring(s.indexOf(" ") + 1);
                    // name
                    m_botAction.sendSmartPrivateMessage(name, "NO! You may NOT !lock while !manualspawn is DISABLED. READ MESSAGES!");
                    
                } else if (s.startsWith("twdmatchbot:" + bot + ":denyunlock ")) {
                    String name = s.substring(s.indexOf(" ") + 1);
                    // name
                    m_botAction.sendSmartPrivateMessage(name, "NO! You may NOT !unlock while !manualspawn is DISABLED. READ MESSAGES!");

                } else if (s.startsWith("twdmatchbot:" + bot + ":allowunlock ")) {
                    String name = s.substring(s.indexOf(" ") + 1);
                    // name
                    command_unlock(name, null);
                } else if (s.startsWith("twdmatchbots:newcheckin")) {
                    if (isTWD()) {
                        if (m_game != null) {
                            // checkin:bot:arena:matchID:squad:squad:type
                            m_botAction.ipcTransmit(IPC, "twdmatchbot:checkingin:" + bot + ":" + arena + ":" + m_game.m_fnMatchID + ":" + m_game.m_fcTeam1Name + ":" + m_game.m_fcTeam2Name + ":" + m_game.m_matchTypeName);
                        } else {
                            m_botAction.ipcTransmit(IPC, "twdmatchbot:checkingin:" + bot + ":" + arena);
                        }
                    } else if (arena.equalsIgnoreCase("twd")) {
                        m_botAction.ipcTransmit(IPC, "twdmatchbot:checkingin:" + bot + ":" + arena);                        
                    }
                } else if (s.startsWith("twdmatchbots:checkin")) {
                    if (isTWD()) {
                        if (m_game != null) {
                            m_botAction.ipcTransmit(IPC, "twdmatchbot:checkingin:" + bot + ":game");
                        } else {
                            m_botAction.ipcTransmit(IPC, "twdmatchbot:checkingin:" + bot + ":" + arena);
                        }
                    } else if (arena.equalsIgnoreCase("twd")) {
                        m_botAction.ipcTransmit(IPC, "twdmatchbot:checkingin:" + bot + ":" + arena);                        
                    }
                    
                } else if ((s.equals("all twdbots die")) || (s.equalsIgnoreCase("twdmatchbot:" + arena + " die")) || (s.equalsIgnoreCase("twdmatchbot:" + m_botAction.getBotName() + " die"))) {
                    if (!m_isLocked || m_game == null) {
                        m_botAction.ipcTransmit(IPC, "twdmatchbot:shuttingdown " + arena + "," + m_botAction.getBotName());
                        TimerTask d = new TimerTask() {
                            @Override
                            public void run() {
                                m_botAction.die();
                            }
                        };
                        m_botAction.scheduleTask(d, 3000);
                    } else if (m_game != null) {
                        m_die = true;
                        m_off = true;
                        m_botAction.ipcTransmit(IPC, "twdmatchbot:dying " + arena + "," + m_botAction.getBotName());
                    }
                } else if (s.equalsIgnoreCase("twdmatchbot:" + arena + " stay")) {
                    m_botAction.ipcTransmit(IPC, "twdmatchbot:staying " + arena + "," + m_botAction.getBotName());
                    m_die = false;
                    m_off = false;
                } else if (s.startsWith("twd:" + arena + ":challenge ")) {
                    // name,squad_op,players
                    String[] args = s.substring(s.indexOf(" ") + 1).split(",");
                    if (args.length != 3)
                        return;
                    command_ipcchallenge(args[0], new String[] {args[1], args[2]});
                }
            }
        }
    }
    
    private boolean isTWD() {
        String arena = m_botAction.getArenaName().toLowerCase();
        
        if (((arena.length() == 4) || (arena.length() == 5)) && ((arena.startsWith("twbd")) || (arena.startsWith("twdd")) || (arena.startsWith("twjd")) || (arena.startsWith("twsd")) || (arena.startsWith("twfd"))))
            return true;
        
        return false;
    }

    public void handleEvent(PlayerDeath event) {
        if (m_game != null) {
            m_game.handleEvent(event);
        }
    }

    public void handleEvent(PlayerLeft event) {
        if (m_game != null) {
            m_game.handleEvent(event);
        }
    }

    public void handleEvent(PlayerEntered event) {
        if (m_game != null) {
            m_game.handleEvent(event);
        }
    }

    public void handleEvent(PlayerPosition event) {
        if (m_game != null) {
            m_game.handleEvent(event);
        }
    }

    public void handleEvent(ScoreReset event) {
        if (m_game != null)
            m_game.handleEvent(event);
    }

    public void handleEvent(BallPosition event) {
        if (m_game != null)
            m_game.handleEvent(event);
    }

    public void handleEvent(Prize event) {
        if (m_game != null)
            m_game.handleEvent(event);
    }

    public void handleEvent(TurretEvent event) {
        if (m_game != null)
            m_game.handleEvent(event);
    }

    public void handleEvent(WatchDamage event) {
        if (m_game != null)
            m_game.handleEvent(event);
    }

    public void handleEvent(Message event) {
        boolean isStaff, isRestrictedStaff;
        int messageType = event.getMessageType();
        String message = event.getMessage();

        if (messageType == Message.ARENA_MESSAGE
                && message.equals("Obscenity block ON") && !obsceneStatus) {
            m_botAction.sendUnfilteredPublicMessage("?obscene");
        }
        if (messageType == Message.ARENA_MESSAGE
                && message.equals("Obscenity block OFF") && obsceneStatus) {
            m_botAction.sendUnfilteredPublicMessage("?obscene");
        }

        if ((messageType == Message.ARENA_MESSAGE)
                && (event.getMessage().equals("WARNING: You have been disconnected because server has not been receiving data from you."))) {
            if (m_game != null)
                m_game.cancel();
            m_botAction.die();
        }
        
        if (messageType == Message.ARENA_MESSAGE){
            if (message.startsWith(TWDHUB + " is in SSCU Trench Wars") && m_lock.length() > 0) {
                m_botAction.ipcTransmit(IPC, m_lock);
                m_lock = "";
                return;
            } else if (message.startsWith("Not online, last seen") && m_lock.length() > 0) {
                if (m_lock.startsWith("twdmatchbot:manlock")) {
                    String[] args = m_lock.substring(m_lock.indexOf(" ") + 1).split(",");
                    if (args.length == 3) {
                        if (args[2].contains(":"))
                            command_lock(args[1], new String[] { args[2].substring(0, args[2].indexOf(":")), args[2].substring(args[2].indexOf(":") + 1) });
                        else
                            command_lock(args[1], new String[] { args[2].substring(args[2].indexOf(" ") + 1) });
                    }
                } else if (m_lock.startsWith("twdmatchbot:manunlock")) {
                    String[] args = m_lock.substring(m_lock.indexOf(" ") + 1).split(",");
                    command_unlock(args[1], null);
                }
                m_lock = "";
                return;
            }
        }
        
        if (messageType == Message.PRIVATE_MESSAGE || messageType == Message.REMOTE_PRIVATE_MESSAGE) {
            String name = m_botAction.getPlayerName(event.getPlayerID());
            if (name == null) {
                name = event.getMessager();
            }
            
            if (m_opList.isBotExact(name) && message.startsWith("!lock")) {
                String[] args = message.substring(message.indexOf(" ") + 1).split(":");
                command_lock(name, args);
                return;
            }
        }

        if ((messageType == Message.PRIVATE_MESSAGE)
                || ((messageType == Message.REMOTE_PRIVATE_MESSAGE) && (message.toLowerCase().startsWith("!accept")))
                || ((messageType == Message.REMOTE_PRIVATE_MESSAGE) && (message.toLowerCase().startsWith("!off")))) {
            String name = m_botAction.getPlayerName(event.getPlayerID());
            if (name == null) {
                name = event.getMessager();
            }

            
            isStaff = false;
            isRestrictedStaff = false;
            if ((m_isLocked) && (m_rules != null)) {
                if (m_rules.getString("specialaccess") != null) {
                    try {
                        if ((new String(":"
                                + m_rules.getString("specialaccess").toLowerCase())).indexOf(":"
                                + name.toLowerCase()) != -1) {
                            isStaff = true;
                            isRestrictedStaff = true;
                        }
                    } catch (Exception e) {
                        Tools.printStackTrace(e);
                    }
                }
            }

            if (m_opList.isBot(name)) {
                isStaff = true;
                isRestrictedStaff = false;
            }
            
            if (messageType == Message.PRIVATE_MESSAGE && m_opList.isModerator(name)) {
                if (message.startsWith("!lock ")) {
                    m_lock = "twdmatchbot:manlock " + m_botAction.getBotName() + "," + name + "," + message;
                    m_botAction.sendUnfilteredPublicMessage("?find=" + TWDHUB);
                    return;
                } else if (message.startsWith("!unlock")) {
                    m_lock = "twdmatchbot:manunlock " + m_botAction.getBotName() + "," + name + "," + message + "," + m_botAction.getArenaName();
                    m_botAction.sendUnfilteredPublicMessage("?find=" + TWDHUB);
                    return;
                }
            }

            if (stringChopper(message, ' ') == null)
                return;

            // First: convert the command to a command with parameters
            if (stringChopper(message, ' ').length > 0) {
                String command;
                try {
                    command = stringChopper(message, ' ')[0];
                } catch (Exception e) {
                    return;
                }
                String[] parameters = stringChopper(message.substring(command.length()).trim(), ':');
                for (int i = 0; i < parameters.length; i++)
                    parameters[i] = parameters[i].replace(':', ' ').trim();
                command = command.trim();

                parseCommand(name, command, parameters, isStaff, isRestrictedStaff);
            }
        } else if (messageType == Message.ARENA_MESSAGE) {
            String msg = event.getMessage();
            if (msg.startsWith("Arena UNLOCKED"))
                m_botAction.toggleLocked();
        }

        // Send to spy to check for racist comments
        if (messageType == Message.PUBLIC_MESSAGE
                || messageType == Message.TEAM_MESSAGE
                || messageType == Message.OPPOSING_TEAM_MESSAGE
                || messageType == Message.PUBLIC_MACRO_MESSAGE)
            racismSpy.handleEvent(event);

        if (m_game != null) {
            m_game.handleEvent(event);
        }
    }

    // getHelpMessages, for hosts....
    public String[] getHelpMessages(String name, boolean isStaff,
            boolean isRestrictedStaff) {
        ArrayList<String> help = new ArrayList<String>();

        if (m_game != null) // Bot is running a game
        {
            if (isStaff) {
                help.add("!killgame                                - stops a game _immediately_");
            }
            help.addAll(m_game.getHelpMessages(name, isStaff));
        } else {
            if (isStaff) {
                if (!m_isLocked) // Bot isn't locked in a specific arena / game
                {
                    if (!isRestrictedStaff)
                        help.add("!listgames                               - list all available game types");
                    help.add("!game <typenumber>                       - start a game of <type>");
                    help.add("!game <typenumber>:<teamA>:<teamB>       - start a game of <type>");

                    if (!isRestrictedStaff) {
                        help.add("!go <arena>                              - makes the bot go to the specified arena");
                        help.add("!lock <typenumber>                       - lock at a free arena where the event can be hosted");
                    }
                } else // Bot is locked in a specific arena / game but not
                       // started
                {
                    help.add("!game <squadA>:<squadB>                  - start a game of "
                            + m_rules.getString("name")
                            + " between teamA and teamB");
                    if (!isRestrictedStaff) {
                        help.add("!unlock                                  - unlock the bot, makes it go back to ?go twd");
                        if (m_opList.isSmod(name)) {
                            help.add("!listaccess                              - list all the players who have special access to this game");
                            help.add("!addaccess <name>                        - add a player to the list");
                            help.add("!removeaccess <name>                     - remove a player from the list");
                        }
                    }
                }
            }
            if (m_isLocked) {
                if (m_rules.getInt("captain_can_start_game") == 1) {
                    help.add("The following commands only work for rostered captains and assistants (shortcuts in parentheses):");
                    help.add("!challenge <squad>                       - (!ch)request a game of " + m_rules.getString("name") + " against <squad>");
                    help.add("!challenge <squad>:<arena>               - (!ch)request a game of " + m_rules.getString("name") + " against <squad> in <arena>");
                    help.add("!challenge <squad>:<players>             - (!ch)request a game of " + m_rules.getString("name") + " against <squad> with <players> number of players");
                    help.add("!challenge <squad>:<players>:<arena>     - (!ch)request a game of " + m_rules.getString("name") + " against <squad> with <players> in <arena>");
                    help.add("!challengeall <players>                  - (!chall)request a game of " + m_rules.getString("name") + " against the most active 8 squads with <players>s");
                    help.add("!challengetopteams <players>             - (!chtop)request a game of " + m_rules.getString("name") + " against the 8 highest rated squads with <players>s");
                    help.add("!challenges                              - (!chals)lists all active challenges " + " made by your squad");
                    help.add("!removechallenge <squad>                 - (!rc)removes the challenge of " + m_rules.getString("name") + " game against <squad>");
                    help.add("!removechallenge *                       - (!rc *)removes all challenges sent out by your squad");
                    help.add("!accept <squad>                          - (!a)accept the !challenge made by the challenging squad");
                }
            }
        }

        return help.toArray(new String[help.size()]);
    }

    public void parseCommand(String name, String command, String[] parameters,
            boolean isStaff, boolean isRestrictedStaff) {
        if (isStaff) {
            if (command.equals("!game"))
                createGame(name, parameters);
            if (!isRestrictedStaff) {
                if (command.equals("!listgames"))
                    listGames(name);
                if (command.equals("!go"))
                    command_go(name, parameters);
                if (command.equals("!lock"))
                    command_lock(name, parameters);
                if (command.equals("!unlock"))
                    command_unlock(name, parameters);
                if ((command.equals("!die")) && (m_opList.isSmod(name))) {
                    if (m_game != null)
                        m_game.cancel();
                    m_botAction.die();
                }
                if ((command.equals("!off"))) {
                    if (m_game == null) {
                        command_unlock(name, parameters);
                    } else {
                        command_setoff(name);
                    }
                }
                if ((command.equals("!listaccess")) && (m_opList.isSmod(name)))
                    command_listaccess(name, parameters);
                if ((command.equals("!addaccess")) && (m_opList.isSmod(name)))
                    command_addaccess(name, parameters);
                if ((command.equals("!removeaccess"))
                        && (m_opList.isSmod(name)))
                    command_removeaccess(name, parameters);
            }
            if (m_game != null) {
                if (command.equals("!killgame")) {
                    m_botAction.sendArenaMessage("The game has been brutally killed by "
                            + name);
                    m_botAction.setMessageLimit(INACTIVE_MESSAGE_LIMIT);
                    m_game.cancel();
                    m_game = null;
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                    }
                    if (m_die && m_off) {
                        m_off = false;
                        m_die = false;
                        m_botAction.ipcTransmit(IPC, "twdmatchbot:shuttingdown " + m_botAction.getArenaName() + "," + m_botAction.getBotName());
                        TimerTask d = new TimerTask() {
                            @Override
                            public void run() {
                                m_botAction.die();
                            }
                        };
                        m_botAction.scheduleTask(d, 1500);
                    }
                    if (m_off) {
                        m_off = false;
                        command_unlock(name, parameters);
                    }
                }
                if (command.equals("!endgameverysilently")) {
                    m_botAction.setMessageLimit(INACTIVE_MESSAGE_LIMIT);
                    m_game.cancel();
                    m_game = null;
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                    }
                    if (m_off) {
                        m_off = false;
                        command_unlock(name, parameters);
                    }
                }
                if (command.equals("!startinfo")) {
                    if (startMessage != null)
                        m_botAction.sendPrivateMessage(name, startMessage);
                }
                if (command.equals("!blueout")) {

                    if (m_game.m_curRound != null) {

                        boolean isCaptain = false;
                        if (m_game.m_curRound.m_team1 != null
                                && m_game.m_curRound.m_team2 != null) {
                            isCaptain = m_game.m_curRound.m_team1.isCaptain(name);
                            if (!isCaptain)
                                isCaptain = m_game.m_curRound.m_team2.isCaptain(name);
                        }

                        if (!isCaptain)
                            m_game.m_curRound.toggleBlueout(m_game.m_curRound.m_blueoutState == 0 ? true
                                    : false);
                    }
                }

            }
        }

        // Non-staff commands
        if (m_rules != null) {

            if (m_rules.getInt("captain_can_start_game") == 1) {
                if (command.equals("!challenge") || command.equals("!ch"))
                    command_challenge(name, parameters);
                if (command.equals("!challengeall") || command.equals("!chall"))
                    command_challengeall(name, parameters);
                if (command.equals("!challengetopteams") || command.equals("!chtop"))
                    command_challengetopteams(name, parameters);
                if (command.equals("!challenges") || command.equals("!challs") || command.equals("!chals"))
                    command_challenges(name);
                if (command.equals("!removechallenge") || command.equals("!rc"))
                    command_removechallenge(name, parameters);
                if (command.equals("!accept") || command.equals("!a"))
                    command_accept(name, parameters);
            }
            if (m_rules.getInt("playerclaimcaptain") == 1) {
                if (command.equals("!cap")) {

                    // Start game if there is none
                    if (m_game == null) {

                        // Start game
                        createGame(name, parameters);

                        // Assign captain after starting game
                        final String nm = name;
                        TimerTask setcaptain = new TimerTask() {
                            public void run() {
                                m_game.parseCommand(nm, "!cap", null, false);
                            }
                        };
                        m_botAction.scheduleTask(setcaptain, 1500);

                    }
                }
            }

        }
        if (command.equals("!help"))
            m_botAction.privateMessageSpam(name, getHelpMessages(name, isStaff, isRestrictedStaff));

        if (m_game != null)
            m_game.parseCommand(name, command, parameters, isStaff);
    }

    public void command_go(String name, String[] parameters) {
        if (m_game == null) {
            if (!m_isLocked) {
                if (parameters.length > 0) {
                    String s = parameters[0];
                    m_arena = s;
                    m_botAction.joinArena(m_arena);
                }
            } else
                m_botAction.sendPrivateMessage(name, "I am locked in this arena");
        } else
            m_botAction.sendPrivateMessage(name, "There's still a game going on, kill it first");
    }

    public void command_lock(String name, String[] parameters) {
        if (m_game != null) {
            m_botAction.sendPrivateMessage(name, "Can't lock to a game, there is another game going on");
            return;
        }

        if (m_isLocked) {
            m_botAction.sendPrivateMessage(name, "Can't lock to a game, I'm already locked in here. Unlock me first");
            return;
        }

        // lock here

        if (parameters.length >= 1) {
            try {
                int typenumber = Integer.parseInt(parameters[0]);
                String typeName = getGameTypeName(typenumber);
                if (typeName != null) {
                    m_rulesFileName = m_botAction.getGeneralSettings().getString("Core Location")
                            + "/data/Rules/" + typeName + ".txt";
                    m_rules = new BotSettings(m_rulesFileName);
                    m_isLocked = true;
                    m_typeNumber = typenumber;
                    m_matchTypeID = m_rules.getInt("matchtype");
                    m_arenaList = new LinkedList<String>();
                    if (parameters.length == 2) {
                        m_botAction.changeArena(parameters[1]);
                    } else {
                        m_lockState = CHECKING_ARENAS;
                        m_locker = name;
                        m_isLocked = true;
                        m_botAction.ipcTransmit(IPC, "whatArena");

                        TimerTask a = new TimerTask() {
                            public void run() {
                                goToLockedArena();
                            }
                        };
                        m_botAction.scheduleTask(a, 100);
                    }
                    
                    if (m_rules.getInt("aliascheck") == 1)
                        m_aliasCheck = true;
                } else
                    m_botAction.sendPrivateMessage(name, "That game type does not exist");
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }

        }
    }

    public void command_unlock(String name, String[] parameters) {
        if (m_game != null) {
            m_botAction.sendPrivateMessage(name, "Can't unlock, there's a game going on");
            return;
        }

        m_isLocked = false;
        m_botAction.ipcTransmit(IPC, "twdmatchbot:unlocked " + m_botAction.getArenaName() + "," + m_botAction.getBotName());
        m_botAction.sendPrivateMessage(name, "Unlocked, going to ?go twd");
        m_botAction.changeArena("twd");
    }

    // challenge without unnecessary and previously completed checks 
    public void command_ipcchallenge(String name, String[] parameters) {
        try {
            if (m_game == null) {
                int players;
                if (parameters.length == 2) {
                    players = Integer.parseInt(parameters[1]);
                    if (players == -1)
                        players = m_rules.getInt("minplayers");
                    else if (players < m_rules.getInt("minplayers") && players > m_rules.getInt("players")) {
                        m_botAction.sendSmartPrivateMessage(name, "Minimum # of players is " + m_rules.getInt("minplayers") + " and maximum is " + m_rules.getInt("players") + ".");
                        return;
                    }
                } else {
                    players = m_rules.getInt("minplayers");
                }
                DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);
                if (!dp.isRankAssistantMinimum() && m_rules.getInt("anyone_can_start_game") != 1) {
                    m_botAction.sendSmartPrivateMessage(name, "You're not allowed to make challenges for your squad unless you're an assistant or captain.");
                    return;
                }

                String nmySquad = parameters[0];
                m_gameRequests.add(new GameRequest(name, dp.getTeamName(), nmySquad, players, dp.getUserID()));
                m_botAction.sendSquadMessage(nmySquad, name + " is challenging you for a game of " + players + "vs" + players + " " + m_rules.getString("name") + " versus " + dp.getTeamName() + ". Captains/assistants, ?go " + m_botAction.getArenaName() + " and pm me with '!accept " + dp.getTeamName() + "'");
                m_botAction.sendSmartPrivateMessage(name, "Your challenge has been sent out to " + nmySquad);
                final String arena = m_botAction.getArenaName();
                final String bot = m_botAction.getBotName();
                if (!ipcTimer) {
                    exp = new TimerTask() {
                        @Override
                        public void run() {
                            m_botAction.ipcTransmit(IPC, "twd:expiredchallenge " + arena + "," + bot);
                        }
                    };
                    m_botAction.scheduleTask(exp, 300000);
                    ipcTimer = true;
                }
            } else
                m_botAction.sendSmartPrivateMessage(name, "You can't challenge here, there is a game going on here already");
        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(name, "Specify the squad you want to challenge");
        }
    }

    public void command_charena(String name, String[] args) {
        try {
            Player p = m_botAction.getPlayer(name);
            // check if he isn't challenging his own squad
            if (!p.getSquadName().equalsIgnoreCase(args[0])) {
                int players;
                String arena;
                if (args.length == 3) {
                    players = Integer.parseInt(args[1]);
                    arena = args[2].toLowerCase();
                } else {
                    arena = args[1].toLowerCase();
                    players = -1;
                }
                DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);
                if (!dp.isRankAssistantMinimum()
                        && m_rules.getInt("anyone_can_start_game") != 1) {
                    m_botAction.sendSmartPrivateMessage(name, "You're not allowed to make challenges for your squad unless you're an assistant or captain.");
                    return;
                }

                if ((dp.getTeamName() != null)
                        && (!dp.getTeamName().equals(""))
                        && (p.getSquadName().equalsIgnoreCase(dp.getTeamName()))) {
                    // check if the challenged team exists
                    String nmySquad = args[0];
                    ResultSet rs = m_botAction.SQLQuery(dbConn, "select fnTeamID from tblTeam where fcTeamName = '"
                            + Tools.addSlashesToString(nmySquad)
                            + "' and (fdDeleted = 0 or fdDeleted IS NULL)");
                    if (rs.next()) {
                        // check to make sure the arena isn't this arena
                        if (arena.equalsIgnoreCase(m_botAction.getArenaName())) {
                            if (players == -1)
                                players = m_rules.getInt("minplayers");
                            m_gameRequests.add(new GameRequest(name, dp.getTeamName(), nmySquad, players, dp.getUserID()));
                            m_botAction.sendSquadMessage(nmySquad, name
                                    + " is challenging you for a game of "
                                    + players + "vs" + players + " "
                                    + m_rules.getString("name") + " versus "
                                    + dp.getTeamName()
                                    + ". Captains/assistants, ?go "
                                    + m_botAction.getArenaName()
                                    + " and pm me with '!accept "
                                    + dp.getTeamName() + "'");
                            m_botAction.sendSmartPrivateMessage(name, "Your challenge has been sent out to "
                                    + nmySquad);
                        } else if (arena.startsWith("twbd") || arena.startsWith("twdd") || arena.startsWith("twjd") || arena.startsWith("twsd") || arena.startsWith("twfd")) {
                            try {
                                if (arena.length() > 4) {
                                    int n = Integer.valueOf(arena.substring(4));
                                    if (n < 1 || n > 5)
                                        throw new NumberFormatException();
                                }

                                // send this request to twdbot for analysis (arena,squad_ch,squad_op,name,players,my_arena,my_botname)
                                m_botAction.ipcTransmit(IPC, "twd:arena_request " + arena + "," + dp.getTeamName() + "," + args[0] + "," + name + "," + players + "," + m_arena + "," + m_botAction.getBotName());
                            } catch (NumberFormatException e) {
                                m_botAction.sendSmartPrivateMessage(name, "Invalid arena requested.");
                            }
                        } else {
                            m_botAction.sendSmartPrivateMessage(name, "Please specify a valid TWD arena");                                
                        }
                    } else
                        m_botAction.sendSmartPrivateMessage(name, "The team you want to challenge does NOT exist in TWD");
                    m_botAction.SQLClose(rs);
                } else
                    m_botAction.sendSmartPrivateMessage(name, "Your ?squad and your squad on the TWD roster are not the same");
            } else
                m_botAction.sendSmartPrivateMessage(name, "You can't challenge your own squad, silly :P");
        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(name, "Specify the squad you want to challenge");
        }

    }

    //
    public void command_challenge(String name, String[] parameters)

    {
        if (parameters.length == 3) {
            command_charena(name, parameters);
            return;
        } else if (parameters.length == 2) {
            String arena = parameters[1].toLowerCase();
            if (arena.startsWith("twbd") || arena.startsWith("twdd") || arena.startsWith("twjd") || arena.startsWith("twsd") || arena.startsWith("twfd")) {
                command_charena(name, parameters);
                return;                
            }                
        }
        
        try {
            
            if (m_game == null) {
                Player p = m_botAction.getPlayer(name);
                // check if he isn't challenging his own squad
                if (!p.getSquadName().equalsIgnoreCase(parameters[0])) {
                    int players;
                    if (parameters.length == 2) {
                        if (Integer.parseInt(parameters[1]) >= m_rules.getInt("minplayers")
                                && Integer.parseInt(parameters[1]) <= m_rules.getInt("players")) {
                            players = Integer.parseInt(parameters[1]);
                        } else {
                            m_botAction.sendSmartPrivateMessage(name, "Minimum # of players is "
                                    + m_rules.getInt("minplayers")
                                    + " and maximum is "
                                    + m_rules.getInt("players") + ".");
                            return;
                        }
                    } else {
                        players = m_rules.getInt("minplayers");
                    }
                    DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);
                    if (!dp.isRankAssistantMinimum()
                            && m_rules.getInt("anyone_can_start_game") != 1) {
                        m_botAction.sendPrivateMessage(name, "You're not allowed to make challenges for your squad unless you're an assistant or captain.");
                        return;
                    }

                    if ((dp.getTeamName() != null)
                            && (!dp.getTeamName().equals(""))
                            && (p.getSquadName().equalsIgnoreCase(dp.getTeamName()))) {
                        // check if the challenged team exists
                        String nmySquad = parameters[0];
                        ResultSet rs = m_botAction.SQLQuery(dbConn, "select fnTeamID from tblTeam where fcTeamName = '"
                                + Tools.addSlashesToString(nmySquad)
                                + "' and (fdDeleted = 0 or fdDeleted IS NULL)");
                        if (rs.next()) {
                            m_gameRequests.add(new GameRequest(name, dp.getTeamName(), nmySquad, players, dp.getUserID()));
                            m_botAction.sendSquadMessage(nmySquad, name
                                    + " is challenging you for a game of "
                                    + players + "vs" + players + " "
                                    + m_rules.getString("name") + " versus "
                                    + dp.getTeamName()
                                    + ". Captains/assistants, ?go "
                                    + m_botAction.getArenaName()
                                    + " and pm me with '!accept "
                                    + dp.getTeamName() + "'");
                            m_botAction.sendSmartPrivateMessage(name, "Your challenge has been sent out to "
                                    + nmySquad);
                        } else
                            m_botAction.sendSmartPrivateMessage(name, "The team you want to challenge does NOT exist in TWD");
                        m_botAction.SQLClose(rs);
                    } else
                        m_botAction.sendSmartPrivateMessage(name, "Your ?squad and your squad on the TWD roster are not the same");
                } else
                    m_botAction.sendSmartPrivateMessage(name, "You can't challenge your own squad, silly :P");
            } else
                m_botAction.sendSmartPrivateMessage(name, "You can't challenge here, there is a game going on here already");
        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(name, "Specify the squad you want to challenge");
        }
    }

    //
    public void command_challengetopteams(String name, String[] parameters) {
        try {
            if (m_game == null) {
                Player p = m_botAction.getPlayer(name);
                int players;
                if (parameters.length == 1) {
                    if (Integer.parseInt(parameters[0]) >= m_rules.getInt("minplayers")
                            && Integer.parseInt(parameters[0]) <= m_rules.getInt("players")) {
                        players = Integer.parseInt(parameters[0]);
                    } else {
                        m_botAction.sendPrivateMessage(name, "Minimum # of players is "
                                + m_rules.getInt("minplayers")
                                + " and maximum is "
                                + m_rules.getInt("players") + ".");
                        return;
                    }
                } else {
                    players = m_rules.getInt("minplayers");
                }
                DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);
                if (!dp.isRankAssistantMinimum()
                        && m_rules.getInt("anyone_can_start_game") != 1) {
                    m_botAction.sendPrivateMessage(name, "You're not allowed to make challenges for your squad unless you're an assistant or captain.");
                    return;
                }

                if ((dp.getTeamName() != null)
                        && (!dp.getTeamName().equals(""))
                        && (p.getSquadName().equalsIgnoreCase(dp.getTeamName()))) {
                    ResultSet squads = m_botAction.SQLQuery(dbConn, "SELECT tblTWDTeam.fnTeamID, tblTeam.fnTeamID, tblTeam.fcTeamName, tblTWDTeam.fnRating "
                            + "FROM tblTWDTeam, tblTeam "
                            + "WHERE tblTWDTeam.fnMatchTypeID="
                            + m_matchTypeID
                            + " AND tblTeam.fnTeamID=tblTWDTeam.fnTeamID "
                            + "AND (tblTeam.fdDeleted=0 OR tblTeam.fdDeleted IS NULL) "
                            + "AND tblTWDTeam.fnGames>0 "
                            + "AND tblTeam.fcTeamName != '"
                            + p.getSquadName()
                            + "' "
                            + "ORDER BY tblTWDTeam.fnRating DESC "
                            + "LIMIT 8");

                    String squadsChalled = "You have challenged: ";

                    while (squads.next()) {
                        String nmySquad = squads.getString("fcTeamName");

                        m_gameRequests.add(new GameRequest(name, dp.getTeamName(), nmySquad, players, dp.getUserID()));

                        m_botAction.sendSquadMessage(nmySquad, name
                                + " is challenging you for a game of "
                                + players + "vs" + players + " "
                                + m_rules.getString("name") + " versus "
                                + dp.getTeamName()
                                + ". Captains/assistants, ?go "
                                + m_botAction.getArenaName()
                                + " and pm me with '!accept "
                                + dp.getTeamName() + "'");
                        if (!squads.isLast())
                            squadsChalled += nmySquad + ", ";
                        else
                            squadsChalled += "and " + nmySquad + ".";
                    }
                    m_botAction.SQLClose(squads);
                    m_botAction.sendPrivateMessage(name, squadsChalled);
                } else
                    m_botAction.sendPrivateMessage(name, "Your ?squad and your squad on the TWD roster are not the same");
            } else
                m_botAction.sendPrivateMessage(name, "You can't challenge here, there is a game going on here already");
        } catch (Exception e) {
            m_botAction.sendPrivateMessage(name, "Your challenge could not be completed. Please contact a TWD Operator."); // ********************************
        }
    }

    //
    public void command_challengeall(String name, String[] parameters) {
        try {
            if (m_game == null) {
                Player p = m_botAction.getPlayer(name);
                int players;
                if (parameters.length == 1) {
                    if (Integer.parseInt(parameters[0]) >= m_rules.getInt("minplayers")
                            && Integer.parseInt(parameters[0]) <= m_rules.getInt("players")) {
                        players = Integer.parseInt(parameters[0]);
                    } else {
                        m_botAction.sendPrivateMessage(name, "Minimum # of players is "
                                + m_rules.getInt("minplayers")
                                + " and maximum is "
                                + m_rules.getInt("players") + ".");
                        return;
                    }
                } else {
                    players = m_rules.getInt("minplayers");
                }
                DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);
                if (!dp.isRankAssistantMinimum()
                        && m_rules.getInt("anyone_can_start_game") != 1) {
                    m_botAction.sendPrivateMessage(name, "You're not allowed to make challenges for your squad unless you're an assistant or captain.");
                    return;
                }

                if ((dp.getTeamName() != null)
                        && (!dp.getTeamName().equals(""))
                        && (p.getSquadName().equalsIgnoreCase(dp.getTeamName()))) {
                    ResultSet squads = m_botAction.SQLQuery(dbConn, "SELECT tblTWDTeam.fnTeamID, tblTeam.fnTeamID, tblTeam.fcTeamName, tblTWDTeam.fnRating "
                            + "FROM tblTWDTeam, tblTeam "
                            + "WHERE tblTWDTeam.fnMatchTypeID="
                            + m_matchTypeID
                            + " AND tblTeam.fnTeamID=tblTWDTeam.fnTeamID "
                            + "AND (tblTeam.fdDeleted=0 OR tblTeam.fdDeleted IS NULL) "
                            + "AND tblTWDTeam.fnGames>0 "
                            + "AND tblTeam.fcTeamName != '"
                            + p.getSquadName()
                            + "' "
                            + "ORDER BY tblTWDTeam.fdLastRatingChange DESC "
                            + "LIMIT 8");

                    String squadsChalled = "You have challenged: ";

                    while (squads.next()) {
                        String nmySquad = squads.getString("fcTeamName");

                        m_gameRequests.add(new GameRequest(name, dp.getTeamName(), nmySquad, players, dp.getUserID()));

                        m_botAction.sendSquadMessage(nmySquad, name
                                + " is challenging you for a game of "
                                + players + "vs" + players + " "
                                + m_rules.getString("name") + " versus "
                                + dp.getTeamName()
                                + ". Captains/assistants, ?go "
                                + m_botAction.getArenaName()
                                + " and pm me with '!accept "
                                + dp.getTeamName() + "'");
                        if (!squads.isLast())
                            squadsChalled += nmySquad + ", ";
                        else
                            squadsChalled += "and " + nmySquad + ".";
                    }
                    m_botAction.SQLClose(squads);
                    m_botAction.sendPrivateMessage(name, squadsChalled);
                } else
                    m_botAction.sendPrivateMessage(name, "Your ?squad and your squad on the TWD roster are not the same");
            } else
                m_botAction.sendPrivateMessage(name, "You can't challenge here, there is a game going on here already");
        } catch (Exception e) {
            m_botAction.sendPrivateMessage(name, "Your challenge could not be completed. Please contact a TWD Operator."); // ********************************
        }
    }

    public void command_removechallenge(String name, String[] parameters) {
        try {
            if (m_game == null) {
                Player p = m_botAction.getPlayer(name);

                DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);
                if (!dp.isRankAssistantMinimum()
                        && m_rules.getInt("anyone_can_start_game") != 1) {
                    m_botAction.sendPrivateMessage(name, "You must be a captain or assistant to remove challenges.");
                    return;
                }
                if (parameters.length > 0 && (dp.getTeamName() != null) && (!dp.getTeamName().equals("")) && (p.getSquadName().equalsIgnoreCase(dp.getTeamName()))) {
                    String nmySquad = parameters[0];
                    boolean removeAll = false;
                    if (nmySquad.equals("*"))
                        removeAll = true;
                    GameRequest t;
                    ListIterator<GameRequest> i = m_gameRequests.listIterator();
                    while (i.hasNext()) {
                        t = (GameRequest) i.next();
                        if (t.getRequestAge() >= 300000)
                            i.remove();
                        else {
                            if ((t.getChallenger().equalsIgnoreCase(p.getSquadName()))
                                    && (removeAll || (t.getChallenged().equalsIgnoreCase(nmySquad)))) {
                                m_botAction.sendPrivateMessage(name, "Your challenge vs. "
                                        + t.getChallenged() + " has been cancelled.");
                                m_botAction.sendSquadMessage(t.getChallenged(), name
                                        + " has cancelled the challenge of "
                                        + m_rules.getString("name")
                                        + " game versus " + p.getSquadName()
                                        + ".");
                                i.remove();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void command_accept(String name, String[] parameters) {
        try {
            if (m_isStartingUp == false) {
                if (m_game == null) {
                    DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);
                    Player p = m_botAction.getPlayer(name);
                    if (p != null) {
                        if (parameters.length > 0) {
                            if ((dp.getTeamName() != null)
                                    && (!dp.getTeamName().equals(""))
                                    && (p.getSquadName().equalsIgnoreCase(dp.getTeamName()))) {
                                // check if the accepted challenge exists
                                String nmySquad = parameters[0];
                                GameRequest t, r = null;
                                ListIterator<GameRequest> i = m_gameRequests.listIterator();
                                while (i.hasNext()) {
                                    t = (GameRequest) i.next();
                                    if (t.getRequestAge() >= 300000)
                                        i.remove();
                                    else if ((t.getChallenged().equalsIgnoreCase(p.getSquadName()))
                                            && (t.getChallenger().equalsIgnoreCase(nmySquad)))
                                        r = t;
                                }
                                if (r != null) {
                                    // check if he is assistant or captain
                                    if (dp.isRankAssistantMinimum()
                                            && m_rules.getInt("anyone_can_start_game") != 1) {
                                        m_isStartingUp = true;
                                        m_botAction.sendSquadMessage(nmySquad, "A game of "
                                                + r.getPlayersNum()
                                                + "vs"
                                                + r.getPlayersNum()
                                                + " "
                                                + m_rules.getString("name")
                                                + " versus "
                                                + dp.getTeamName()
                                                + " will start in ?go "
                                                + m_botAction.getArenaName()
                                                + " in 30 seconds");
                                        m_botAction.sendSquadMessage(p.getSquadName(), "A game of "
                                                + r.getPlayersNum()
                                                + "vs"
                                                + r.getPlayersNum()
                                                + " "
                                                + m_rules.getString("name")
                                                + " versus "
                                                + r.getChallenger()
                                                + " will start in ?go "
                                                + m_botAction.getArenaName()
                                                + " in 30 seconds");
                                        m_botAction.sendArenaMessage(r.getChallenger()
                                                + " vs. "
                                                + dp.getTeamName()
                                                + " will start here in 30 seconds", 2);
                                        m_team1 = r.getChallenger();
                                        m_team2 = dp.getTeamName();
                                        startMessage = name + "("
                                        + p.getSquadName()
                                        + ") accepted challenge from "
                                        + r.getRequester() + "("
                                        + r.getChallenger() + ")";
                                        final int pNum = r.getPlayersNum();
                                        final int chID = r.getRequesterID();
                                        final int acID = dp.getUserID();
                                        
                                        TimerTask m_startGameTimer = new TimerTask() {
                                            public void run() {
                                                m_isStartingUp = false;
                                                if (m_cancelGame) {
                                                    m_botAction.sendArenaMessage(m_team1
                                                            + " vs. "
                                                            + m_team2
                                                            + " has been cancelled.");
                                                    m_team1 = null;
                                                    m_team2 = null;
                                                    m_cancelGame = false;
                                                } else {
                                                    String dta[] = { m_team1,
                                                            m_team2,
                                                            Integer.toString(pNum),
                                                            Integer.toString(chID),
                                                            Integer.toString(acID) };
                                                    createGame(m_botAction.getBotName(), dta);
                                                }
                                            }
                                        };

                                        m_botAction.scheduleTask(m_startGameTimer, 30000);
                                    } else
                                        m_botAction.sendPrivateMessage(name, "You're not allowed to accept challenges for your squad");
                                } else
                                    m_botAction.sendPrivateMessage(name, "The team you want to accept has not challenged you.");
                            } else
                                m_botAction.sendPrivateMessage(name, "Your ?squad and your squad on the TWD roster are not the same");
                        } else
                            m_botAction.sendPrivateMessage(name, "You must specify a squad to accept.");                            
                    } else
                        m_botAction.sendSmartPrivateMessage(name, "Please ?go "
                                + m_botAction.getArenaName()
                                + " and accept the challenge from there so I can check your ?squad. Thanks.");
                } else
                    m_botAction.sendPrivateMessage(name, "Can't accept challenge, there's a game going on here already");
            } else
                m_botAction.sendPrivateMessage(name, "Another game will start up here soon, already.");
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }
    public void command_challenges(String name) {
        try {
            if (m_isStartingUp == false) {
                if (m_game == null) {
                    DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);
                    Player p = m_botAction.getPlayer(name);
                    if (p != null) {
                        if ((dp.getTeamName() != null)
                                && (!dp.getTeamName().equals(""))
                                && (p.getSquadName().equalsIgnoreCase(dp.getTeamName()))) {
                                // check if he is assistant or captain
                            if (dp.isRankAssistantMinimum() && m_rules.getInt("anyone_can_start_game") != 1) { 
                                int numChalls = 0;
                                GameRequest t = null;
                                ListIterator<GameRequest> i = m_gameRequests.listIterator();
                                while (i.hasNext()) {
                                    t = (GameRequest) i.next();
                                    if (t.getRequestAge() >= 300000)
                                        i.remove();
                                    else if (t.getChallenger().equalsIgnoreCase(p.getSquadName())) {
                                        m_botAction.sendPrivateMessage(name, t.toString());
                                        numChalls++;
                                    }
                                }
                                if (numChalls == 0)
                                    m_botAction.sendPrivateMessage(name, "No challenges found");                                    
                            } else
                                m_botAction.sendPrivateMessage(name, "You're not allowed to view challenges for your squad");
                        } else 
                            m_botAction.sendPrivateMessage(name, "Your ?squad and your squad on the TWD roster are not the same");
                    } else
                        m_botAction.sendSmartPrivateMessage(name, "Please ?go "
                                + m_botAction.getArenaName()
                                + " and accept the challenge from there so I can check your ?squad. Thanks.");
                } else
                    m_botAction.sendPrivateMessage(name, "No challenges available, there's a game going on here already");
            } else
                m_botAction.sendPrivateMessage(name, "No challenges available because another game will start up here soon.");
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }


    public void command_cancel(String name) {
        try {
            if (m_isStartingUp == true) {
                if (m_game == null) {
                    DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);
                    Player p = m_botAction.getPlayer(name);
                    if (p != null) {
                        if (dp.getTeamName().equalsIgnoreCase(m_team1)
                                || dp.getTeamName().equalsIgnoreCase(m_team2)) {
                            if (dp.isRankAssistantMinimum()
                                    && m_rules.getInt("anyone_can_start_game") != 1) {
                                m_cancelGame = true;
                                m_botAction.sendSquadMessage(m_team1, "The "
                                        + m_rules.getString("name")
                                        + " game versus " + m_team2
                                        + " has been cancelled by " + name
                                        + ".");
                                m_botAction.sendSquadMessage(m_team2, "The "
                                        + m_rules.getString("name")
                                        + " game versus " + m_team1
                                        + " has been cancelled by " + name
                                        + ".");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void command_setoff(String name) {
        if (m_off) {
            m_off = false;
            m_botAction.sendPrivateMessage(name, "!off disabled, bot won't ?go twd after the current game finishes.");
        } else {
            m_off = true;
            m_botAction.sendPrivateMessage(name, "!off enabled, bot will ?go twd after the current game finishes.");
        }
    }

    public String[] getAccessList() {
        String accA[] = stringChopper(m_rules.getString("specialaccess"), ':');
        if (accA != null) {
            for (int i = 1; i < accA.length; i++)
                accA[i] = accA[i].substring(1);
            return accA;
        }
        return null;
    }

    public void command_listaccess(String name, String[] parameters) {
        String accA[] = getAccessList();
        if (accA == null) {
            m_botAction.sendSmartPrivateMessage(name, "Access list could not be found.");
            return;
        }
        m_botAction.sendPrivateMessage(name, "Access list for game: "
                + m_rules.getString("name"));
        for (int i = 0; i < accA.length; i++) {
            m_botAction.sendPrivateMessage(name, accA[i]);
        }
    }

    public void command_addaccess(String name, String[] parameters) {
        try {
            String newP = parameters[0];
            String acc = m_rules.getString("specialaccess");
            if (!(acc.trim().equals("")))
                acc = acc + ":";
            acc = acc + newP + "(Granted by " + name + ")";
            m_rules.put("specialaccess", acc);
            m_rules.save();
            m_botAction.sendPrivateMessage(name, newP
                    + " has been added to the access list");
        } catch (Exception e) {
            System.out.println("Error in command_addaccess: " + e.getMessage());
        }
    }

    public void command_removeaccess(String name, String[] parameters) {
        try {
            String newP = parameters[0].toLowerCase();
            String acc = m_rules.getString("specialaccess");
            int cutFrom = acc.toLowerCase().indexOf(newP);
            if (cutFrom != -1) {
                int cutTo = acc.indexOf(":", cutFrom);
                if (cutTo == -1)
                    cutTo = acc.length();
                if (cutFrom == 0) {
                    cutFrom = 1;
                    cutTo += 1;
                }
                if (cutTo > acc.length())
                    cutTo = acc.length();
                acc = acc.substring(0, cutFrom - 1) + acc.substring(cutTo);
                m_rules.put("specialaccess", acc);
                m_rules.save();
                m_botAction.sendPrivateMessage(name, newP
                        + " has been removed from the access list");
            }
        } catch (Exception e) {
            System.out.println("Error in command_removeaccess: "
                    + e.getMessage());
        }
    }

    //
    public void goToLockedArena() {
        String[] avaArena = m_rules.getString("arena").split(",");
        String pick = null;
        for (int i = 0; i < avaArena.length; i++) {

            if ((!m_arenaList.contains(avaArena[i].toLowerCase()))
                    && (pick == null)) {
                pick = avaArena[i].toLowerCase();
            }
        }
        if (pick != null) {
            m_lockState = LOCKED;
            m_botAction.sendPrivateMessage(m_locker, "Going to ?go " + pick);
            m_botAction.changeArena(pick);
        } else {
            if (m_locker != null)
                m_botAction.sendPrivateMessage(m_locker, "I'm sorry, every arena where this event can be hosted is in use ("
                        + m_rules.getString("arena") + ")");
            m_lockState = 0;
            m_isLocked = false;
        }
    }

    //
    public void createKillChecker() {
        if (m_gameKiller == null) {
            m_gameKiller = new TimerTask() {
                public void run() {
                    if (m_game != null) {
                        if (m_game.getGameState() == MatchGame.KILL_ME_PLEASE) {
                            m_game.cancel();
                            m_game = null;
                            m_botAction.setMessageLimit(INACTIVE_MESSAGE_LIMIT);

                            if (!m_die && m_off) {
                                m_off = false;
                                m_isLocked = false;
                                m_botAction.ipcTransmit(IPC, "twdmatchbot:unlocked " + m_botAction.getArenaName() + "," + m_botAction.getBotName());
                                m_botAction.changeArena("twd");
                            }
                            
                            if (m_die) {
                                m_die = false;
                                m_off = false;
                                m_isLocked = false;
                                m_botAction.ipcTransmit(IPC, "twdmatchbot:shuttingdown " + m_botAction.getArenaName() + "," + m_botAction.getBotName());
                                TimerTask d = new TimerTask() {
                                    @Override
                                    public void run() {
                                        m_botAction.die();
                                    }
                                };
                                m_botAction.scheduleTask(d, 2000);
                            }
                        }
                    }
                }
            };
            m_botAction.scheduleTaskAtFixedRate(m_gameKiller, 2000, 2000);
        }
    }

    // create game
    public void createGame(String name, String[] parameters) {
        if (ipcTimer) {
            ipcTimer = false;
            m_botAction.cancelTask(exp);
        }
            
        try {
            createKillChecker();
            String fcTeam1Name = null, fcTeam2Name = null, rulesName = null;

            int players = 0;
            int challenger = 0;
            int accepter = 0;
            int typenumber;
            if (!m_isLocked) {
                if (parameters.length >= 1) {
                    typenumber = Integer.parseInt(parameters[0]);
                    rulesName = m_botAction.getGeneralSettings().getString("Core Location")
                            + "/data/Rules/"
                            + getGameTypeName(typenumber)
                            + ".txt";
                    m_rules = new BotSettings(rulesName);
                    players = m_rules.getInt("players");
                    if (parameters.length < 3) {
                        fcTeam1Name = "Freq 1";
                        fcTeam2Name = "Freq 2";
                    } else {
                        fcTeam1Name = parameters[1];
                        fcTeam2Name = parameters[2];
                    }
                }
            } else {
                rulesName = m_rulesFileName;
                players = m_rules.getInt("players");
                if (parameters.length < 2) {
                    fcTeam1Name = "Freq 1";
                    fcTeam2Name = "Freq 2";
                } else {
                    fcTeam1Name = parameters[0];
                    fcTeam2Name = parameters[1];
                    if (parameters.length >= 3) {
                        players = Integer.parseInt(parameters[2]);
                        if (parameters.length >= 4) {
                            challenger = Integer.parseInt(parameters[3]);
                            if (parameters.length >= 5) {
                                accepter = Integer.parseInt(parameters[4]);
                            }
                        }
                    }
                }
            }

            if (rulesName != null) {
                if (m_game == null) {
                    m_botAction.toggleLocked();
                    m_botAction.setMessageLimit(ACTIVE_MESSAGE_LIMIT);
                    if (!name.equalsIgnoreCase(m_botAction.getBotName()))
                        startMessage = "Game started by " + name;
                    m_game = new MatchGame(rulesName, fcTeam1Name, fcTeam2Name, players, challenger, accepter, m_botAction, this);
                    m_game.zone();
                } else
                    m_botAction.sendPrivateMessage(name, "There's already a game running, type !killgame to kill it first");
            } else
                m_botAction.sendPrivateMessage(name, "Game type doesn't exist");
        } catch (Exception e) {
            m_botAction.sendPrivateMessage(name, "Provide a correct game type number");
        }
    }

    // list games
    public void listGames(String name) {
        File f = m_botAction.getCoreDirectoryFile("data/Rules");
        TreeSet<String> files = new TreeSet<String>(Arrays.asList(f.list()));
        int cnter = 0;

        m_botAction.sendPrivateMessage(name, "I contain the following "
                + "games:");
        for (String file : files) {
            if (file.endsWith(".txt")) {
                file = file.substring(0, file.lastIndexOf('.'));
                if (file.indexOf('$') == -1) {
                    cnter++;
                    String extraInfo = m_botSettings.getString(file);
                    if (extraInfo == null)
                        extraInfo = "";
                    else
                        extraInfo = "      " + extraInfo;
                    m_botAction.sendPrivateMessage(name, "#" + cnter + "  "
                            + file + extraInfo);
                }
            }
        }
    }

    public String getGameTypeName(int fnGameTypeNumber) {
        File f = m_botAction.getCoreDirectoryFile("data/Rules");
        TreeSet<String> files = new TreeSet<String>(Arrays.asList(f.list()));
        int cnter = 0;

        for (String file : files) {
            if (file.endsWith(".txt")) {
                file = file.substring(0, file.lastIndexOf('.'));
                if (file.indexOf('$') == -1) {
                    cnter++;
                    if (cnter == fnGameTypeNumber)
                        return file;
                }
            }
        }
        return null;
    }

    public int getGameTypeNumber(String fcGameTypeName) {
        File f = m_botAction.getCoreDirectoryFile("data/Rules");
        TreeSet<String> files = new TreeSet<String>(Arrays.asList(f.list()));
        int cnter = 0;

        for (String file : files) {
            if (file.endsWith(".txt")) {
                file = file.substring(0, file.lastIndexOf('.'));
                if (file.indexOf('$') == -1) {
                    cnter++;
                    if (file.equalsIgnoreCase(fcGameTypeName))
                        return cnter;
                }
            }
        }
        return 0;
    }

    public void cancel() {
        m_botAction.cancelTask(m_gameKiller);
        m_gameKiller = null;
        m_botAction.cancelTasks();
        m_botAction.ipcUnSubscribe(IPC);
    }

    public void reload() {
        m_botSettings.reloadFile();
    }
}

class GameRequest {
    long m_timeRequest = 0;
    String m_challenger = "", m_challenged = "", m_requester = "";
    int m_requesterID;
    boolean accepted = false;
    int playersNum;
    BotAction m_botAction;

    public GameRequest(String requester, String challenger, String challenged,
            int players, int requesterID) {
        m_requester = requester;
        m_challenger = challenger;
        m_challenged = challenged;
        m_timeRequest = System.currentTimeMillis();
        playersNum = players;
        m_requesterID = requesterID;
    }
    
    public String toString() {
        return "Squad: " + m_challenged + " was challenged to " + playersNum + "s by " + m_requester + " " + (System.currentTimeMillis() - m_timeRequest)/60000 + "." + (System.currentTimeMillis() - m_timeRequest)%60000/1000 + " minutes ago";
    }

    public String getChallenged() {
        return m_challenged;
    }

    public String getChallenger() {
        return m_challenger;
    }

    public String getRequester() {
        return m_requester;
    }

    public long getRequestAge() {
        return (System.currentTimeMillis() - m_timeRequest);
    }

    public int getPlayersNum() {
        return playersNum;
    }

    public int getRequesterID() {
        return m_requesterID;
    }

}
