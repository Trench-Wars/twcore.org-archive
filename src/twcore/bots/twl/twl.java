/*
    Matchtwl.java

    Created on August 19, 2002, 8:34 PM
*/

/**

    @author  Administrator
*/



package twcore.bots.twl;

import java.io.File;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import twcore.core.events.SQLResultEvent;
import twcore.core.events.ScoreReset;
import twcore.core.events.SoccerGoal;
import twcore.core.events.TurretEvent;
import twcore.core.events.WeaponFired;
import twcore.core.game.Player;
import twcore.core.stats.DBPlayerData;
import twcore.core.util.Tools;


public class twl extends SubspaceBot {

    MatchGame m_game;
    String m_arena;
    BotSettings m_botSettings;
    OperatorList m_opList;
    LinkedList<String> m_arenaList;
    LinkedList<GameRequest> m_gameRequests;
    TimerTask m_gameKiller;
    String startMessage;
    HashMap<String, String> m_registerList;

    String dbConn = "website";

    //
    boolean m_isLocked = false;
    boolean m_aliasCheck = false;
    boolean m_isStartingUp = false;
    boolean m_cancelGame = false;
    boolean m_off = false;
    String m_locker;
    int m_lockState = 0;
    //
    static int CHECKING_ARENAS = 1, LOCKED = 2;
    static int INACTIVE_MESSAGE_LIMIT = 3, ACTIVE_MESSAGE_LIMIT = 8;
    // these variables are for when the bot is locked
    BotSettings m_rules;
    String m_rulesFileName;

    // --- temporary
    String m_team1 = null, m_team2 = null;

    private static Pattern parseInfoRE = Pattern.compile("^IP:(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})  TimeZoneBias:\\d+  Freq:\\d+  TypedName:(.*)  Demo:\\d  MachineId:(\\d+)$");
    private static Pattern cruncherRE = Pattern.compile("\\s+");

    /** Creates a new instance of Matchtwl */
    public twl(BotAction botAction) {
        //Setup of necessary stuff for any bot.
        super(botAction);

        m_botSettings = m_botAction.getBotSettings();
        m_arena = m_botSettings.getString("Arena");
        m_opList = m_botAction.getOperatorList();
        m_gameRequests = new LinkedList<GameRequest>();
        m_registerList = new HashMap<String, String>();

        requestEvents();

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
    }

    public void handleEvent(ArenaJoined event) {
        if (m_game != null) {
            m_game.handleEvent(event);
        }

        m_botAction.setReliableKills(1); // Reliable kills so the bot receives
        // every packet
        m_botAction.sendUnfilteredPublicMessage("?obscene");
    }

    public void handleEvent(BallPosition event) {
        if (m_game != null)
            m_game.handleEvent(event);
    }

    public void handleEvent(LoggedOn event) {
        m_botAction.ipcSubscribe("MatchBot");
        m_botAction.sendUnfilteredPublicMessage("?chat=robodev");

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
        if ((event.getChannel().equals("MatchBot")) && (!event.getSenderName().equals(m_botAction.getBotName()))) {
            if (event.getObject() instanceof String) {
                String s = (String) event.getObject();

                if (s.equals("whatArena")) {
                    m_botAction.ipcTransmit("MatchBot", "myArena:" + m_botAction.getArenaName());
                }

                if ((s.startsWith("myArena:")) && (m_isLocked) && (m_lockState == CHECKING_ARENAS)) {
                    if (m_arenaList == null) {
                        m_arenaList = new LinkedList<String>();
                    }

                    m_arenaList.add(s.substring(8).toLowerCase());
                }
            }
        }
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

    public void handleEvent(Prize event) {
        if (m_game != null)
            m_game.handleEvent(event);
    }

    public void handleEvent(ScoreReset event) {
        if (m_game != null)
            m_game.handleEvent(event);
    }

    public void handleEvent(SoccerGoal event) {
        if (m_game != null) {
            m_game.handleEvent(event);
        }
    }

    public void handleEvent(SQLResultEvent event) {
    }

    public void handleEvent(TurretEvent event) {
        if (m_game != null)
            m_game.handleEvent(event);
    }

    /**
        @param event The weapon Fired event
    */
    public void handleEvent(WeaponFired event) {
        if (m_game != null)
            m_game.handleEvent(event);
    }

    public void handleEvent(Message event) {
        boolean isStaff, isRestrictedStaff;
        String message = event.getMessage();

        if ((event.getMessageType() == Message.ARENA_MESSAGE)
                && (event.getMessage().equals("WARNING: You have been disconnected because server has not been receiving data from you."))) {
            if (m_game != null)
                m_game.cancel();

            m_botAction.die("Received error: WARNING: You have been disconnected because server has not been receiving data from you.");
        }

        if ((event.getMessageType() == Message.ARENA_MESSAGE)
                && (event.getMessage()).startsWith("IP:")) {
            parseInfo( event.getMessage() );
        }

        if ((event.getMessageType() == Message.PRIVATE_MESSAGE)
                || ((event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) && (message.toLowerCase().startsWith("!accept")))) {
            String name = m_botAction.getPlayerName(event.getPlayerID());

            if (name == null) {
                name = event.getMessager();
            }

            isStaff = false;
            isRestrictedStaff = false;

            if ((m_isLocked) && (m_rules != null)) {
                if (m_rules.getString("specialaccess") != null) {
                    if ((new String(":" + m_rules.getString("specialaccess").toLowerCase() + ":")).indexOf(":" + name.toLowerCase() + ":") != -1) {
                        isStaff = true;
                        isRestrictedStaff = true;
                    }
                }
            }

            if (m_opList.isBot(name)) {
                isStaff = true;
                isRestrictedStaff = false;
            }

            if( stringChopper(message, ' ') == null )
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
        } else if (event.getMessageType() == Message.ARENA_MESSAGE) {
            String msg = event.getMessage();

            if (msg.startsWith("Arena UNLOCKED"))
                m_botAction.toggleLocked();
        }

        if (m_game != null) {
            m_game.handleEvent(event);
        }
    }

    // getHelpMessages, for hosts....
    public String[] getHelpMessages(String name, boolean isStaff, boolean isRestrictedStaff) {
        ArrayList<String> help = new ArrayList<String>();

        if (m_game != null) {
            if (isStaff) {
                help.add("!killgame                                - stops a game _immediately_");
            }

            help.addAll(m_game.getHelpMessages(name, isStaff));
        } else {
            if (isStaff) {
                if (!m_isLocked) {
                    if (!isRestrictedStaff) {
                        help.add("!listgames                               - list all available game types");
                        help.add("!game <typenumber>                       - start a game of <type>");
                        help.add("!game <typenumber>:<teamA>:<teamB>       - start a game of <type>");
                        help.add("!twlgame <game ID>                       - load a TWL game");
                    }

                    if (!isRestrictedStaff) {
                        help.add("!go <arena>                              - makes the bot go to the specified arena");
                        help.add("!lock <typenumber>                       - lock at a free arena where the event can be hosted");
                    }
                } else {
                    help.add("!game <squadA>:<squadB>                  - start a game of " + m_rules.getString("name") + " between teamA and teamB");

                    if (!isRestrictedStaff) {
                        help.add("!unlock                                  - unlock the bot, makes it go back to ?go twl");

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
                    help.add("The following command only works for rostered captains and assistants:");
                    help.add("!challenge <squad>                       - request a game of " + m_rules.getString("name") + " against <squad>");
                    help.add("!challenge <squad>:<players>             - request a game of " + m_rules.getString("name") + " against <squad> with <players> number of players");
                    help.add("!removechallenge <squad>                 - removes the challenge of " + m_rules.getString("name") + " game against <squad>");
                    help.add("!accept <squad>                          - accept the !challenge made by the challenging squad");
                }
            }
        }

        return help.toArray(new String[help.size()]);
    }

    public void parseCommand(String name, String command, String[] parameters, boolean isStaff, boolean isRestrictedStaff) {
        if (isStaff) {
            if (command.equals("!game"))
                createGame(name, parameters);

            if (command.equals("!twlgame"))
                createTWLGame(name, parameters);

            if (!isRestrictedStaff || name.toLowerCase().equals("humid")) {
                if (command.equals("!listgames"))
                    listGames(name);

                if (command.equals("!go"))
                    command_go(name, parameters);

                if (command.equals("!lock"))
                    command_lock(name, parameters);

                if (command.equals("!unlock"))
                    command_unlock(name, parameters);

                if ((command.equals("!die"))) {
                    if (m_isLocked) {
                        m_botAction.sendSmartPrivateMessage(name, "Please !unlock before dying, for security reasons.");
                        return;
                    } else {
                        m_botAction.sendSmartPrivateMessage(name, "Dying.");
                        m_botAction.die("!die by " + name);
                        return;
                    }
                }

                if ((command.equals("!off")) && (m_opList.isSmod(name)))
                    if (m_game == null) {
                        command_unlock(name, parameters);
                    } else {
                        command_setoff(name);
                    }

                if ((command.equals("!listaccess")) && (m_opList.isSmod(name)))
                    command_listaccess(name, parameters);

                if ((command.equals("!addaccess")) && (m_opList.isSmod(name)))
                    command_addaccess(name, parameters);

                if ((command.equals("!removeaccess")) && (m_opList.isSmod(name)))
                    command_removeaccess(name, parameters);
            }

            if (m_game != null) {
                if (command.equals("!killgame")) {
                    m_botAction.sendArenaMessage("The game has been brutally killed by " + name);
                    m_botAction.setMessageLimit(INACTIVE_MESSAGE_LIMIT);
                    m_game.cancel();
                    m_game = null;

                    try {
                        Thread.sleep(100);
                    }
                    catch (Exception e) {}

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
                    }
                    catch (Exception e) {}

                    if (m_off) {
                        m_off = false;
                        command_unlock(name, parameters);
                    }
                }

                if (command.equals("!startinfo")) {
                    if (startMessage != null)
                        m_botAction.sendPrivateMessage(name, startMessage);
                }

            }
        }

        if ((m_rules != null) && (m_rules.getInt("captain_can_start_game") == 1)) {
            if (command.equals("!challenge"))
                command_challenge(name, parameters);

            if (command.equals("!removechallenge"))
                command_removechallenge(name, parameters);

            if (command.equals("!accept"))
                command_accept(name, parameters);

        }

        if (command.equals("!help"))
            m_botAction.privateMessageSpam(name, getHelpMessages(name, isStaff, isRestrictedStaff));

        if (command.equals("!register"))
            command_registername(name, parameters);

        if (m_game != null)
            m_game.parseCommand(name, command, parameters, isStaff);
    }

    public void parseInfo(String message) {

        Matcher m = parseInfoRE.matcher(message);

        if ( !m.matches() )
            return;

        String ip = m.group(1);
        String name = cruncherRE.matcher( m.group(2) ).replaceAll(" ");
        String mid = m.group(3);

        //The purpose of this is to not confuse the info doen by PlayerLagInfo
        if( !m_registerList.containsKey( name ) ) return;

        m_registerList.remove( name );

        DBPlayerData dbP = new DBPlayerData( m_botAction, dbConn, name );

        //Note you can't get here if already registered, so can't match yourself.
        if( dbP.aliasMatch( ip, mid ) ) {
            m_botAction.sendSmartPrivateMessage( name, "Another account has already been registered on your connection, please contact a TWD/TWL Op for further information." );
            return;
        }

        if( !dbP.register( ip, mid ) ) {
            m_botAction.sendSmartPrivateMessage( name, "Unable to register name, please contact a TWL/TWD op for further help." );
            return;
        }

        m_botAction.sendSmartPrivateMessage( name, "Registration successful." );

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
                    m_rulesFileName = m_botAction.getGeneralSettings().getString("Core Location") + "/data/Rules/" + typeName + ".txt";
                    m_rules = new BotSettings(m_rulesFileName);
                    m_isLocked = true;
                    m_lockState = CHECKING_ARENAS;
                    m_locker = name;
                    m_arenaList = new LinkedList<String>();
                    m_isLocked = true;
                    m_botAction.ipcTransmit("MatchBot", "whatArena");

                    if( m_rules.getInt("aliascheck") == 1 ) m_aliasCheck = true;

                    TimerTask a = new TimerTask() {
                        public void run() {
                            goToLockedArena();
                        }
                    };
                    m_botAction.scheduleTask(a, 100);
                } else
                    m_botAction.sendPrivateMessage(name, "That game type does not exist");
            } catch (Exception e) {
                Tools.printStackTrace(e);
            }

        }
    }

    public void command_unlock(String name, String[] parameters) {
        if (m_game != null) {
            m_botAction.sendPrivateMessage(name, "Can't unlock, there's a game going on");
            return;
        }

        m_isLocked = false;
        m_botAction.sendPrivateMessage(name, "Unlocked, going to ?go twl");
        m_botAction.changeArena("twl");
    }

    //
    public void command_challenge(String name, String[] parameters) {
        try {
            if (m_game == null) {
                Player p = m_botAction.getPlayer(name);

                // check if he isn't challenging his own squad
                if (!p.getSquadName().equalsIgnoreCase(parameters[0])) {
                    int players;

                    if (parameters.length == 2) {
                        if (Integer.parseInt(parameters[1]) >= m_rules.getInt("minplayers") && Integer.parseInt(parameters[1]) < m_rules.getInt("players") ) {
                            players = Integer.parseInt(parameters[1]);
                        } else {
                            m_botAction.sendPrivateMessage(name, "Minimum # of players is " + m_rules.getInt("minplayers") + " and maximum is " + m_rules.getInt("players") + ".");
                            return;
                        }
                    } else {
                        players = m_rules.getInt("players");
                    }

                    DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);

                    if ((dp.getTeamName() != null) && (!dp.getTeamName().equals("")) && (p.getSquadName().equalsIgnoreCase(dp.getTeamName()))) {
                        // check if the challenged team exists
                        String nmySquad = parameters[0];
                        ResultSet rs =
                            m_botAction.SQLQuery(
                                dbConn,
                                "SELECT fnTeamId FROM tblTeam WHERE tblTeam.fcTeamName = '" + Tools.addSlashesToString(nmySquad) + "' and (tblTeam.fdDeleted = 0 or tblTeam.fdDeleted IS NULL)");

                        if (rs.next()) {
                            // check if he is assistant or captain
                            if (dp.hasRank(3) || dp.hasRank(4)) {
                                m_gameRequests.add(new GameRequest(name, p.getSquadName(), nmySquad, players));
                                m_botAction.sendSquadMessage(
                                    nmySquad,
                                    name
                                    + " is challenging you for a game of "
                                    + players + "vs" + players + " "
                                    + m_rules.getString("name")
                                    + " versus "
                                    + p.getSquadName()
                                    + ". Captains/assistants, ?go "
                                    + m_botAction.getArenaName()
                                    + " and pm me with '!accept "
                                    + p.getSquadName()
                                    + "'");
                                m_botAction.sendPrivateMessage(name, "Your challenge has been sent out to " + nmySquad);
                            } else
                                m_botAction.sendPrivateMessage(name, "You're not allowed to make challenges for your squad");
                        } else
                            m_botAction.sendPrivateMessage(name, "The team you want to challenge does NOT exist in TWD");
                    } else
                        m_botAction.sendPrivateMessage(name, "Your ?squad and your squad on the TWD roster are not the same");
                } else
                    m_botAction.sendPrivateMessage(name, "You can't challenge your own squad, silly :P");
            } else
                m_botAction.sendPrivateMessage(name, "You can't challenge here, there is a game going on here already");
        } catch (Exception e) {
            m_botAction.sendPrivateMessage(name, "Specify the squad you want to challenge");
        }
    }

    public void command_removechallenge(String name, String[] parameters) {
        try {
            if (m_game == null) {
                Player p = m_botAction.getPlayer(name);

                DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);

                if ((dp.getTeamName() != null) && (!dp.getTeamName().equals("")) && (p.getSquadName().equalsIgnoreCase(dp.getTeamName()))) {
                    String nmySquad = parameters[0];
                    GameRequest t;
                    ListIterator<GameRequest> i = m_gameRequests.listIterator();

                    while (i.hasNext()) {
                        t = i.next();

                        if (t.getRequestAge() >= 300000)
                            i.remove();
                        else if ((t.getChallenger().equalsIgnoreCase(p.getSquadName())) && (t.getChallenged().equalsIgnoreCase(nmySquad)))
                            if (dp.hasRank(3) || dp.hasRank(4)) {
                                m_botAction.sendPrivateMessage(name, "Your challenge vs. " + nmySquad + " has been cancelled.");
                                m_botAction.sendSquadMessage(
                                    nmySquad,
                                    name
                                    + " has cancelled the challenge of "
                                    + m_rules.getString("name")
                                    + " game versus "
                                    + p.getSquadName()
                                    + ".");
                                i.remove();
                            }
                    }
                }
            }
        } catch (Exception e) {}
    }

    public void command_accept(String name, String[] parameters) {
        try {
            if (m_isStartingUp == false) {
                if (m_game == null) {
                    DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);
                    Player p = m_botAction.getPlayer(name);

                    if (p != null) {
                        if ((dp.getTeamName() != null) && (!dp.getTeamName().equals("")) && (p.getSquadName().equalsIgnoreCase(dp.getTeamName()))) {
                            // check if the accepted challenge exists
                            String nmySquad = parameters[0];
                            GameRequest t, r = null;
                            ListIterator<GameRequest> i = m_gameRequests.listIterator();

                            while (i.hasNext()) {
                                t = i.next();

                                if (t.getRequestAge() >= 300000)
                                    i.remove();
                                else if ((t.getChallenged().equalsIgnoreCase(p.getSquadName())) && (t.getChallenger().equalsIgnoreCase(nmySquad)))
                                    r = t;
                            }

                            if (r != null) {
                                // check if he is assistant or captain
                                if (dp.hasRank(3) || dp.hasRank(4)) {
                                    m_isStartingUp = true;
                                    m_botAction.sendSquadMessage(
                                        nmySquad,
                                        "A game of "
                                        + r.getPlayersNum() + "vs" + r.getPlayersNum() + " "
                                        + m_rules.getString("name")
                                        + " versus "
                                        + p.getSquadName()
                                        + " will start in ?go "
                                        + m_botAction.getArenaName()
                                        + " in 30 seconds");
                                    m_botAction.sendSquadMessage(
                                        p.getSquadName(),
                                        "A game of " + r.getPlayersNum() + "vs" + r.getPlayersNum() + " " + m_rules.getString("name") + " versus " + nmySquad + " will start in ?go " + m_botAction.getArenaName() + " in 30 seconds");
                                    m_botAction.sendArenaMessage(nmySquad + " vs. " + p.getSquadName() + " will start here in 30 seconds", 2);
                                    m_team1 = nmySquad;
                                    m_team2 = p.getSquadName();
                                    startMessage = name + "(" + p.getSquadName() + ") accepted challenge from " + r.getRequester() + "(" + r.getChallenger() + ")";
                                    final int pNum = r.getPlayersNum();

                                    TimerTask m_startGameTimer = new TimerTask() {
                                        public void run() {
                                            m_isStartingUp = false;

                                            if (m_cancelGame) {
                                                m_botAction.sendArenaMessage(m_team1 + " vs. " + m_team2 + " has been cancelled.");
                                                m_team1 = null;
                                                m_team2 = null;
                                                m_cancelGame = false;
                                            } else {
                                                String dta[] = { m_team1, m_team2, Integer.toString(pNum) };
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
                        m_botAction.sendSmartPrivateMessage(
                            name,
                            "Please ?go " + m_botAction.getArenaName() + " and accept the challenge from there so I can check your ?squad. Thanks.");
                } else
                    m_botAction.sendPrivateMessage(name, "Can't accept challenge, there's a game going on here already");
            } else
                m_botAction.sendPrivateMessage(name, "Another game will start up here soon, already.");
        } catch (Exception e) {
        }
    }

    public void command_cancel(String name) {
        try {
            if (m_isStartingUp == true) {
                if (m_game == null) {
                    DBPlayerData dp = new DBPlayerData(m_botAction, dbConn, name);
                    Player p = m_botAction.getPlayer(name);

                    if (p != null) {
                        if (dp.getTeamName().equalsIgnoreCase(m_team1) || dp.getTeamName().equalsIgnoreCase(m_team2)) {
                            if (dp.hasRank(3) || dp.hasRank(4)) {
                                m_cancelGame = true;
                                m_botAction.sendSquadMessage( m_team1, "The " + m_rules.getString("name") + " game versus " + m_team2 + " has been cancelled by " + name + ".");
                                m_botAction.sendSquadMessage( m_team2, "The " + m_rules.getString("name") + " game versus " + m_team1 + " has been cancelled by " + name + ".");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {}
    }

    public void command_setoff(String name) {
        if (m_off) {
            m_off = false;
            m_botAction.sendPrivateMessage(name, "!off disabled, bot won't ?go twl after the current game finishes.");
        } else {
            m_off = true;
            m_botAction.sendPrivateMessage(name, "!off enabled, bot will ?go twl after the current game finishes.");
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

    public void command_registername(String name, String[] parameters) {
        if( !m_aliasCheck ) return;

        DBPlayerData dbP = new DBPlayerData( m_botAction, dbConn, name );

        if( dbP.isRegistered() ) {
            m_botAction.sendSmartPrivateMessage( name, "This name has already been registered." );
            return;
        }

        m_registerList.put( name, name );
        m_botAction.sendUnfilteredPrivateMessage( name, "*info" );
    }

    public void command_listaccess(String name, String[] parameters) {
        String accA[] = getAccessList();
        String answ = "";
        int j = 0;
        m_botAction.sendPrivateMessage(name, "Access list for game: " + m_rules.getString("name"));

        for (int i = 0; i < accA.length; i++) {
            if (accA[i].length() > 20)
                answ = answ + accA[i].substring(0, 20);
            else
                answ = answ + accA[i];

            for (j = 0; j < (20 - accA[i].length()); j++)
                answ = answ + " ";

            if (i % 3 == 1) {
                m_botAction.sendPrivateMessage(name, answ);
                answ = "";
            } else
                answ = answ + "          ";
        }

        if (!answ.equals(""))
            m_botAction.sendPrivateMessage(name, answ);
    }

    public void command_addaccess(String name, String[] parameters) {
        try {
            String newP = parameters[0];
            String acc = m_rules.getString("specialaccess");

            if (!(acc.trim().equals("")))
                acc = acc + ":";

            acc = acc + newP;
            m_rules.put("specialaccess", acc);
            m_rules.save();
            m_botAction.sendPrivateMessage(name, newP + " has been added to the access list");
        } catch (Exception e) {
            Tools.printStackTrace("Error in command_addaccess: ", e);
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
                m_botAction.sendPrivateMessage(name, newP + " has been removed from the access list");
            }
        } catch (Exception e) {
            Tools.printStackTrace("Error in command_removeaccess: ", e);
        }
    }

    //
    public void goToLockedArena() {
        String[] avaArena = m_rules.getString("arena").split(",");
        String pick = null;

        for (int i = 0; i < avaArena.length; i++) {

            if ((!m_arenaList.contains(avaArena[i].toLowerCase())) && (pick == null)) {
                pick = avaArena[i].toLowerCase();
            }
        }

        if (pick != null) {
            m_lockState = LOCKED;
            m_botAction.sendPrivateMessage(m_locker, "Going to ?go " + pick);
            m_botAction.changeArena(pick);
        } else {
            if (m_locker != null)
                m_botAction.sendPrivateMessage(m_locker, "I'm sorry, every arena where this event can be hosted is in use (" + m_rules.getString("arena") + ")");

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

                            if (m_off) {
                                m_off = false;
                                m_isLocked = false;
                                m_botAction.changeArena("twl");
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
        try {
            createKillChecker();
            String fcTeam1Name = null, fcTeam2Name = null, rulesName = null;

            int matchID = -1;
            int players = 0;
            int typenumber;

            if (!m_isLocked) {
                if (parameters.length == 5) {
                    m_botAction.sendPrivateMessage(name, "[" + parameters[0] + "] " + parameters[1] + ": " + parameters[2] + " vs. " + parameters[3] + " loaded.");

                    matchID = Integer.parseInt(parameters[0]);
                    rulesName = parameters[4];
                    m_rules = new BotSettings(rulesName);
                    fcTeam1Name = parameters[2];
                    fcTeam2Name = parameters[3];
                    players = m_rules.getInt("players");

                } else if (parameters.length >= 1) {
                    typenumber = Integer.parseInt(parameters[0]);
                    rulesName = m_botAction.getGeneralSettings().getString("Core Location") + "/data/Rules/" + getGameTypeName(typenumber) + ".txt";
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

                    if (parameters.length == 3) {
                        players = Integer.parseInt(parameters[2]);
                    }
                }
            }

            if (rulesName != null) {
                if (m_game == null) {
                    m_botAction.toggleLocked();
                    m_botAction.setMessageLimit(ACTIVE_MESSAGE_LIMIT);

                    if (!name.equalsIgnoreCase(m_botAction.getBotName()))
                        startMessage = "Game started by " + name;

                    m_game = new MatchGame(rulesName, fcTeam1Name, fcTeam2Name, players, matchID, m_botAction);
                } else
                    m_botAction.sendPrivateMessage(name, "There's already a game running, type !killgame to kill it first");
            } else
                m_botAction.sendPrivateMessage(name, "Game type doesn't exist");
        } catch (Exception e) {
            m_botAction.sendPrivateMessage(name, "Provide a correct game type number");
        }
    }

    // create TWL game
    public void createTWLGame(String name, String[] parameters) {
        try {

            if (parameters.length == 1 && Tools.isAllDigits(parameters[0])) {
                parameters = getTWLDetails(Integer.parseInt(parameters[0]));
            }

            if (parameters != null && parameters.length == 5) {
                createGame(name, parameters);
            } else {
                m_botAction.sendPrivateMessage(name, "Corrupted game details or game ID does not exist");
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public String[] getTWLDetails(int m_TWLID) {
        try {
            ResultSet result = m_botAction.SQLQuery(dbConn, "SELECT * FROM tblTWL__Match WHERE fnMatchID = '" + m_TWLID + "'");

            if (result.next()) {
                int fnMatchID = result.getInt("fnMatchID");
                int fnMatchTypeID = result.getInt("fnMatchTypeID");
                String fcTeam1Name = result.getString("fcTeam1Name");
                String fcTeam2Name = result.getString("fcTeam2Name");

                String rulesName = "";
                String gName = "";

                if (fnMatchTypeID == 1) {
                    rulesName = m_botAction.getGeneralSettings().getString("Core Location") + "/data/Rules/TWLD.txt";
                    gName = "TWLD";
                } else if (fnMatchTypeID == 2) {
                    rulesName = m_botAction.getGeneralSettings().getString("Core Location") + "/data/Rules/TWLJ.txt";
                    gName = "TWLJ";
                } else if (fnMatchTypeID == 3) {
                    rulesName = m_botAction.getGeneralSettings().getString("Core Location") + "/data/Rules/TWLB.txt";
                    gName = "TWLB";
                }

                if (rulesName.equals("")) {
                    return null;
                } else {
                    String s[] = {
                        Integer.toString(fnMatchID),
                        gName,
                        fcTeam1Name,
                        fcTeam2Name,
                        rulesName
                    };
                    return s;
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }


    // list games
    public void listGames(String name) {
        File f = m_botAction.getCoreDirectoryFile("data/Rules");
        String[] s = f.list();
        int cnter = 0;
        m_botAction.sendPrivateMessage(name, "I contain the following " + "games:");

        for (int i = 0; i < s.length; i++) {
            if (s[i].endsWith(".txt")) {
                s[i] = s[i].substring(0, s[i].lastIndexOf('.'));

                if (s[i].indexOf('$') == -1) {
                    cnter++;
                    String extraInfo = m_botSettings.getString(s[i]);

                    if (extraInfo == null)
                        extraInfo = "";
                    else
                        extraInfo = "      " + extraInfo;

                    m_botAction.sendPrivateMessage(name, cnter + ". " + s[i] + extraInfo);
                }
            }
        }
    }

    public String getGameTypeName(int fnGameTypeNumber) {
        File f = m_botAction.getCoreDirectoryFile("data/Rules");
        String[] s = f.list();
        int cnter = 0;

        for (int i = 0; i < s.length; i++) {
            if (s[i].endsWith(".txt")) {
                s[i] = s[i].substring(0, s[i].lastIndexOf('.'));

                if (s[i].indexOf('$') == -1) {
                    cnter++;

                    if (cnter == fnGameTypeNumber)
                        return s[i];
                }
            }
        }

        return null;
    }

    public int getGameTypeNumber(String fcGameTypeName) {
        File f = m_botAction.getCoreDirectoryFile("data/Rules");
        String[] s = f.list();
        int cnter = 0;

        for (int i = 0; i < s.length; i++) {
            if (s[i].endsWith(".txt")) {
                s[i] = s[i].substring(0, s[i].lastIndexOf('.'));

                if (s[i].indexOf('$') == -1) {
                    cnter++;

                    if (s[i].equalsIgnoreCase(fcGameTypeName))
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
        m_botAction.ipcUnSubscribe("MatchBot");
    }
}


class GameRequest {
    long m_timeRequest = 0;
    String m_challenger = "", m_challenged = "", m_requester = "";
    boolean accepted = false;
    int playersNum;
    BotAction m_botAction;

    public GameRequest(String requester, String challenger, String challenged, int players) {
        m_requester = requester;
        m_challenger = challenger;
        m_challenged = challenged;
        m_timeRequest = System.currentTimeMillis();
        playersNum = players;
    }


    public String getChallenged() {
        return m_challenged;
    }
    public String getChallenger() {
        return m_challenger;
    }
    public String getRequester()  {
        return m_requester;
    }
    public long getRequestAge() {
        return (System.currentTimeMillis() - m_timeRequest);
    }
    public int getPlayersNum() {
        return playersNum;
    }


}

