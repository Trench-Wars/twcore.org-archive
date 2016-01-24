package twcore.bots.twdhub;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TimerTask;
import java.util.Vector;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.util.ipc.Command;
import twcore.core.util.ipc.EventType;
import twcore.core.util.ipc.IPCChallenge;
import twcore.core.util.ipc.IPCCommand;
import twcore.core.util.ipc.IPCTWD;
import twcore.core.util.Tools;
import twcore.core.net.iharder.*;

/**

    @author WingZero
*/
public class twdhub extends SubspaceBot {

    private static final String HUB = "TWCore-League";
    private static final String IPC = "MatchBot";
    private static final String BOT_NAME = "MatchBot";
    private static final String PUBBOT = "TW-Guard";
    private static final String TWBD = "9";
    private static final String TWDD = "11";
    private static final String TWFD = "16";
    private static final String TWJD = "21";
    private static final String TWSD = "25";

    public static final String DATABASE = "website";
    public static final String DB_BOTS = "bots";

    private String connectionID = "pushbulletbot";
    private PushbulletClient pbClient; // Push to mobile data, private MobilePusher mobilePusher;

    enum ArenaStatus { WAITING, READY, DYING };

    BotAction ba;
    OperatorList oplist;
    BotSettings rules;

    VectorSet<String> needsBot;
    VectorSet<String> freeBots;
    VectorSet<String> sentSpawn;
    HashSet<String> alerts;
    HashMap<String, Arena> arenas;
    HashMap<String, Arena> bots;
    HashMap<String, Squad> squads;
    HashMap<String, String> twdOps;

    boolean shutdown;
    boolean startup;
    boolean DEBUG;
    String debugger;

    public twdhub(BotAction botAction) {
        super(botAction);
        ba = botAction;
        oplist = ba.getOperatorList();
        rules = ba.getBotSettings();
        startup = true;
        requestEvents();
        sentSpawn = new VectorSet<String>();
        needsBot = new VectorSet<String>();
        freeBots = new VectorSet<String>();
        arenas = new HashMap<String, Arena>();
        bots = new HashMap<String, Arena>();
        squads = new HashMap<String, Squad>();
        alerts = new HashSet<String>();
        shutdown = false;
        DEBUG = false;
        debugger = "";
        twdOps = new HashMap<String, String>();
    }

    public void handleEvent(LoggedOn event) {
        ba.ipcSubscribe(IPC);
        ba.sendUnfilteredPublicMessage("?chat=robodev,executive lounge,twdstaff");
        ba.joinArena(rules.getString("Arena"));

        updateTWDOps();
        String pushAuth = ba.getGeneralSettings().getString("PushAuth");
        pbClient = new PushbulletClient(pushAuth);
        StartPushbulletListener();
    }

    private void StartPushbulletListener() {
        pbClient.addPushbulletListener(new PushbulletListener() {
            @Override
            public void pushReceived(PushbulletEvent pushEvent) {
                //This is probably doubling dipping on the rate limit by pulling the message the bot just posted
                //possibly need to change this once we confirm
                List<Push> pushes = null;
                pushes = pushEvent.getPushes();
                Push lastPush = pushes.get(0);
                String userMsg = lastPush.getBody().toString();

                String senderEmail = lastPush.getSender_email().toString();

                if (senderEmail == "") {
                    return;    //means it came from the channel, no need to push it back to the channel
                }

                String playerName = getUserNameByEmail(senderEmail);

                if (playerName == "") {
                    return;    //means it came from the bot account, probably using !push
                }

                //handle push
                handleNewPush(playerName, userMsg);
            }

            @Override
            public void devicesChanged(PushbulletEvent pushEvent) {
                Tools.printLog("devicesChanged PushEvent received: " + pushEvent);
            }

            @Override
            public void websocketEstablished(PushbulletEvent pushEvent) {
                Tools.printLog("websocketEstablished PushEvent received: " + pushEvent);
            }
        });

        Tools.printLog("Getting previous pushes to find most recent...");

        try {
            pbClient.getPushes(1);
        } catch (PushbulletException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        Tools.printLog("Starting websocket...try sending a push now.");

        pbClient.startWebsocket();
        Tools.printLog("Listening Started!");
    }

    public void handleEvent(ArenaJoined event) {
        checkIn();
    }

    public void handleEvent(PlayerEntered event) {
        String name = event.getPlayerName();

        if (name == null)
            name = ba.getPlayerName(event.getPlayerID());

        if (oplist.isBotExact(name) && name.startsWith(BOT_NAME)) {
            freeBots.add(name);
            TimerTask lock = new TimerTask() {
                public void run() {
                    if (!needsBot.isEmpty())
                        lockBots();
                }
            };
            ba.scheduleTask(lock, 2000);
        }
    }

    public void handleEvent(PlayerLeft event) {
        String name = ba.getPlayerName(event.getPlayerID());

        if (name != null && oplist.isBotExact(name) && name.startsWith(BOT_NAME))
            freeBots.remove(name);
    }

    public void handleEvent(Message event) {
        int type = event.getMessageType();
        String msg = event.getMessage();
        String name = ba.getPlayerName(event.getPlayerID());

        if (name == null)
            name = event.getMessager();

        if (name == null) return;

        if (type == Message.CHAT_MESSAGE) {
            if (msg.contains("matchbot")) {
                if (msg.contains("disconnected")) {
                    String bot = msg.substring(0, msg.indexOf("("));
                    botDisconnected(bot);
                } else if (msg.contains("failed to log in") && !sentSpawn.isEmpty()) {
                    debug("Detected failed log in so resending spawn request...");
                    sentSpawn.remove(0);
                    checkArenas();
                }
            }
        }

        if (type == Message.PRIVATE_MESSAGE || type == Message.REMOTE_PRIVATE_MESSAGE) {
            if (name.startsWith(PUBBOT) && msg.startsWith("twdplayer") && !squads.isEmpty()) {
                String[] args = msg.substring(msg.indexOf(" ") + 1).split(":");

                if (args.length == 2 && squads.containsKey(args[1].toLowerCase())) {
                    Squad squad = squads.get(args[1].toLowerCase());
                    Vector<String> games = squad.getGames();

                    for (String arena : games) {
                        if (arenas.containsKey(arena) && arenas.get(arena).game != null)
                            arenas.get(arena).game.alert(args[0], args[1]);
                    }
                }

                return;
            }

            String cmd = low(msg);

            if (cmd.equals("!list"))
                cmd_list(name);
            else if (cmd.equals("!games"))
                cmd_games(name);
            else if (cmd.equals("!alert"))
                cmd_alert(name);
            else if (cmd.equals("!help"))
                cmd_help(name);

            if (cmd.startsWith("!signup "))
                cmd_signup(name, msg.substring(msg.indexOf(" ") + 1));
            else if (cmd.equals("!enable")){
                cmd_enable(name);}
            else if (cmd.equalsIgnoreCase("!disable"))
                cmd_disable(name);
            else if (cmd.startsWith("!push "))
                cmd_push(name, msg.substring(msg.indexOf(" ") + 1));
            else if (cmd.startsWith("!beep "))
                cmd_beep(name, msg.substring(msg.indexOf(" ") + 1));
            else if (cmd.equals("!challenge"))
                cmd_challenge(name);
            else if (cmd.equals("!accept"))
                cmd_accept(name);

            if (oplist.isSmod(name) || isTWDOp(name)) {
                if (cmd.equals("!die"))
                    cmd_die(name);
                else if (cmd.startsWith("!send "))
                    ;//sendBot(cmd.substring(6));
                else if (cmd.equals("!sdtwd"))
                    cmd_shutdown(name);
                else if (cmd.equals("!check"))
                    cmd_reset(name);
                else if (cmd.equals("!reset"))
                    cmd_reset(name);
            }

            if(oplist.isSmod(name))
            {
                if (cmd.equals("!update"))
                    cmd_update(name);
                else if (cmd.equals("!debug"))
                    cmd_debug(name);

                if (cmd.startsWith("!listen")) {
                    ba.sendSmartPrivateMessage(name, "Listening Started!");
                    pbClient.startWebsocket();
                }

                if (cmd.startsWith("!stoplisten")) {
                    ba.sendSmartPrivateMessage(name, "Listening Halted!");
                    pbClient.stopWebsocket();
                }

                if (cmd.startsWith("!pushes")) {
                    List<Push> pushes = null;

                    try {
                        pushes = pbClient.getPushes();
                    } catch (PushbulletException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    //ba.sendSmartPrivateMessage(name, "Number of pushes: " + pushes.size() );
                    //ba.sendSmartPrivateMessage(name, pushes.get(0).toString());
                    Push lastPush = pushes.get(0);

                    ba.sendSmartPrivateMessage(name, "getBody: " + lastPush.getBody().toString());
                    //try {Thread.sleep(100);} catch (InterruptedException e) {// TODO Auto-generated catch block e.printStackTrace();}
                    ba.sendSmartPrivateMessage(name, "getIden: " + lastPush.getIden().toString());
                    //ba.sendSmartPrivateMessage(name, "getOwner_iden: " + lastPush.getOwner_iden().toString());
                    ////ba.sendSmartPrivateMessage(name, "getReceiver_email: " + lastPush.getReceiver_email().toString());
                    ////ba.sendSmartPrivateMessage(name, "getReceiver_email_normalized : " + lastPush.getReceiver_email_normalized().toString());
                    ////ba.sendSmartPrivateMessage(name, "getReceiver_iden: " + lastPush.getReceiver_iden().toString());
                    ////ba.sendSmartPrivateMessage(name, "getSender_email: " + lastPush.getSender_email().toString());
                    ////ba.sendSmartPrivateMessage(name, "getSender_email_normalized : " + lastPush.getSender_email_normalized().toString());
                    ////ba.sendSmartPrivateMessage(name, "getSender_iden: " + lastPush.getSender_iden().toString());
                    //ba.sendSmartPrivateMessage(name, "getTitle: " + lastPush.getTitle().toString());
                    ba.sendSmartPrivateMessage(name, "getType: " + lastPush.getType().toString());
                    //ba.sendSmartPrivateMessage(name, "getUrl: " + lastPush.getUrl().toString());
                    //ba.sendSmartPrivateMessage(name, "getClass: " + lastPush.getClass().toString());
                }
                
                if (cmd.startsWith("!testsquad")) {
                    cmd_test(name, msg.substring(msg.indexOf(" ") + 1));
                }
            }
        }
    }

    public void handleEvent(InterProcessEvent event) {
        if (!event.getChannel().equals(IPC) || event.getSenderName().equals(ba.getBotName())) return;

        if (event.getObject() instanceof IPCTWD) {
            IPCTWD ipc = (IPCTWD) event.getObject();

            if (ipc.getType() == EventType.NEW) {
                Game game = new Game(-1, ipc.getSquad1(), ipc.getSquad2(), ipc.getArena());

                if (arenas.containsKey(ipc.getArena()))
                    arenas.get(ipc.getArena()).game = game;

                checkDiv(ipc.getArena().substring(0, 4));
            } else if (ipc.getType() == EventType.CHECKIN) {
                if (!isTWD(ipc.getArena())) return;

                if (ipc.getArena().equalsIgnoreCase("twd"))
                    freeBots.add(ipc.getBot());
                else if (arenas.containsKey(ipc.getArena())) {
                    needsBot.remove(ipc.getArena());
                    Arena arena = arenas.get(ipc.getArena());

                    if (arena.status == ArenaStatus.READY && arena.bot != null) {
                        ba.sendChatMessage("Found multipe bots in arena: " + ipc.getArena());
                        botRemove(ipc.getBot());
                        return;
                    }

                    arena.bot = ipc.getBot();
                    bots.put(arena.bot, arena);
                    arena.status = ArenaStatus.READY;
                    freeBots.remove(arena.bot);

                    if (ipc.getID() != 0) {
                        Game game = new Game(ipc.getID(), ipc.getSquad1(), ipc.getSquad2(), ipc.getArena());
                        arenas.get(ipc.getArena()).game = game;
                        game.setRound(1);
                        game.setState(1);
                    }
                } else {
                    Arena arena = new Arena(ipc.getArena());
                    arena.bot = ipc.getBot();
                    bots.put(arena.bot, arena);
                    arena.status = ArenaStatus.READY;
                    arenas.put(ipc.getArena(), arena);

                    if (ipc.getID() != 0) {
                        Game game = new Game(ipc.getID(), ipc.getSquad1(), ipc.getSquad2(), ipc.getArena());
                        arenas.get(ipc.getArena()).game = game;
                        game.setRound(1);
                        game.setState(1);
                    }
                }
            } else if (ipc.getType() == EventType.STARTING) {
                if (arenas.containsKey(ipc.getArena()) && arenas.get(ipc.getArena()).game != null) {
                    arenas.get(ipc.getArena()).expire();
                    Game game = arenas.get(ipc.getArena()).game;
                    game.setID(ipc.getID());
                    game.setRound(1);
                    game.setState(0);
                }
            } else if (ipc.getType() == EventType.STATE) {
                if (arenas.containsKey(ipc.getArena()) && arenas.get(ipc.getArena()).game != null) {
                    Game game = arenas.get(ipc.getArena()).game;
                    game.next();
                    game.score1 = ipc.getScore1();
                    game.score2 = ipc.getScore2();
                }
            } else if (ipc.getType() == EventType.END) {
                if (arenas.containsKey(ipc.getArena())) {
                    Arena arena = arenas.get(ipc.getArena());
                    Squad squad = squads.get(low(arena.game.squad1.name));
                    squad.endGame(arena.name);
                    squad = squads.get(low(arena.game.squad2.name));
                    squad.endGame(arena.name);
                    arena.game = null;
                }

                // May be causing issues. Don't need to remove at this point anyhow. Wait for standard spawn check.
                //checkDiv(ipc.getArena().substring(0, 4));
            }
        } else if (!shutdown && event.getObject() instanceof IPCChallenge) {
            IPCChallenge ipc = (IPCChallenge) event.getObject();
            if (ipc.getRecipient().equals("TWDBot")) {
                // hijack this so that we don't have to send a duplicate (from originating matchbot) to TWDHub as well
                // this will also avoid any confusion with spawning new bots as this IPC is used for more than just notification
                String message = "";
                if (ipc.getType() == EventType.CHALLENGE) {
                    // single squad challenge
                    String gameType = ipc.getArena().substring(0, 4).toUpperCase();
                    message = "" + ipc.getName() + " challenged " + ipc.getSquad2() + " to " + ipc.getPlayers() + "s in " + ipc.getArena();
                    debug(message);
                    message = "" + ipc.getName() + " is challenging you to a " + ipc.getPlayers() + "vs" + ipc.getPlayers() + " " 
                                + gameType + " vs " + ipc.getSquad1() + " in " + ipc.getArena() + ".";
                    PreparedStatement ps_squadMembers = ba.createPreparedStatement(DB_BOTS, connectionID, this.getPreparedStatement("getenabledsquadmembers"));
                    try {
                        ps_squadMembers.clearParameters();
                        ps_squadMembers.setString(1, Tools.addSlashesToString(ipc.getSquad2()));
                        ps_squadMembers.execute();
                        ResultSet rs = ps_squadMembers.getResultSet();
                        while (rs.next()) {
                            debug("Pushing to " + rs.getString("fcUserName"));
                            pbClient.sendNote(null, rs.getString("fcPushBulletEmail"), "", message);
                        }
                    } catch (SQLException | PushbulletException e) {
                        Tools.printStackTrace(e);
                    } finally {
                    }
                    
                } else if (ipc.getType() == EventType.ALLCHALLENGE) {
                    // multi squad challenge
                    message = "" + ipc.getName() + " challenged all to " + ipc.getPlayers() + "s in " + ipc.getArena();
                    debug(message);    
                    
                } else if (ipc.getType() == EventType.TOPCHALLENGE) {
                    // multi squad challenge
                    message = "" + ipc.getName() + " challenged top teams to " + ipc.getPlayers() + "s in " + ipc.getArena();
                    debug(message);
                    ResultSet squads = m_botAction.SQLQuery(db, "SELECT tblTWDTeam.fnTeamID, tblTeam.fnTeamID, tblTeam.fcTeamName, tblTWDTeam.fnRating "
                            + "FROM tblTWDTeam, tblTeam "
                            + "WHERE tblTWDTeam.fnMatchTypeID="
                            + m_matchTypeID
                            + " AND tblTeam.fnTeamID=tblTWDTeam.fnTeamID "
                            + "AND (tblTeam.fdDeleted=0 OR tblTeam.fdDeleted IS NULL) "
                            + "AND tblTWDTeam.fnGames>0 "
                            + "AND tblTeam.fcTeamName != '"
                            + squad
                            + "' "
                            + "ORDER BY tblTWDTeam.fnRating DESC "
                            + "LIMIT 10");
                }            
                return;
            }
            
            if (!isTWD(ipc.getArena()) || !ipc.getRecipient().equals(ba.getBotName())) return;

            String arenaName = ipc.getArena();
            String name = ipc.getName();

            if (arenas.containsKey(arenaName)) {
                Arena arena = arenas.get(arenaName);

                if (arena.game != null) {
                    ba.sendSmartPrivateMessage(name, "A game is already being played in " + arenaName + ".");
                } else {
                    if (arena.status == ArenaStatus.READY) {
                        ipc.setBot(arena.bot);
                        debug("Transmitting challenge IPC for " + ipc.getName());
                        ba.ipcTransmit(IPC, ipc);
                        arena.ipcFlag();
                    } else
                        arena.add(ipc);
                }
            } else {
                Arena arena = new Arena(ipc.getArena());
                arena.add(ipc);
                arenas.put(low(arena.name), arena);
                botSpawn(ipc.getArena());
            }
        } else if (event.getObject() instanceof IPCCommand) {
            IPCCommand ipc = (IPCCommand) event.getObject();

            if (ipc.getBot() != null && !ipc.getBot().equalsIgnoreCase(ba.getBotName())) return;

            if (ipc.getType() == Command.ECHO)
                ba.sendChatMessage(ipc.getCommand());
            else if (ipc.getType() == Command.EXPIRED) {
                if (arenas.containsKey(ipc.getCommand()))
                    arenas.get(ipc.getCommand()).expire();

                checkArenas();
            } else if (ipc.getType() == Command.CHALL) {
                if (arenas.containsKey(ipc.getCommand()))
                    arenas.get(ipc.getCommand()).flag();
            }
        }
    }

    public void cmd_help(String name) {
        ArrayList<String> msg = new ArrayList<String>();
        msg.add("- PushBullet Commands -");
        msg.add(" !signup <email> - signs up <email> for notifications");
        msg.add(" !enable         - enable alerts to your pushbullet account");
        msg.add(" !disable        - disable alerts to your pushbullet account");
        msg.add(" !push <>        - Push a test msg to yourself");
        msg.add(" !beep <>        - Beep for TWD match. Commands: jd dd bd fd sd any");
        msg.add(" !challenge      - Mimic squad challenge for TWJD");
        msg.add(" !accept         - Mimic squad acceptance for TWJD");

        msg.add("- TWD Hub Commands -");
        msg.add(" !alert          - Toggles a PM alert whenever a new TWD match starts");
        msg.add(" !games          - List of games currently in progress");
        msg.add(" !list           - List of current bot values");


        if (oplist.isSmod(name) || isTWDOp(name)) {
            msg.add("- TWDOP+ -");
            msg.add(" !check          - Checks for any arenas needing bots");
            msg.add(" !reset          - Resets all trackers and calls for checkin (goto fix it cmd)");
            msg.add(" !sdtwd          - Shutdown TWD: kill all matchbots (lets games finish)");
            msg.add(" !die            - Kills bot");
        }

        if(oplist.isSmod(name))
        {
            msg.add("- SMOD+ -");
            msg.add(" !update        - Reloads and Updates Bot Operators.");
            msg.add(" !debug        - Toggle debug mode");
        }

        ba.smartPrivateMessageSpam(name, msg.toArray(new String[msg.size()]));
    }

    public void cmd_reset(String name) {
        ba.sendSmartPrivateMessage(name, "Initiating reset and requesting bot checkin...");
        needsBot.clear();
        squads.clear();
        bots.clear();
        arenas.clear();
        checkIn();
    }

    public void cmd_alert(String name) {
        if (alerts.remove(low(name)))
            ba.sendSmartPrivateMessage(name, "New game alerts DISABLED");
        else {
            alerts.add(low(name));
            ba.sendSmartPrivateMessage(name, "New game alerts ENABLED");
        }
    }

    public void cmd_list(String name) {
        String msg = "needsBot: ";

        for (String n : needsBot)
            msg += n + " ";

        ba.sendSmartPrivateMessage(name, msg);
        msg = "freeBots: ";

        for (String n : freeBots)
            msg += n + " ";

        ba.sendSmartPrivateMessage(name, msg);
        msg = "arenas: ";

        for (String n : arenas.keySet())
            msg += n + " ";

        ba.sendSmartPrivateMessage(name, msg);
        msg = "bots: ";

        for (String n : bots.keySet())
            msg += n + " ";

        ba.sendSmartPrivateMessage(name, msg);
    }

    public void cmd_games(String name) {
        boolean idle = true;

        for (Arena arena : arenas.values()) {
            if (arena.hasGame()) {
                idle = false;
                ba.sendSmartPrivateMessage(name, arena.game.toString());
            }
        }

        if (idle)
            ba.sendSmartPrivateMessage(name, "No games are being played at the moment.");
    }

    public void cmd_shutdown(String name) {
        shutdown = true;
        ba.sendSmartPrivateMessage(name, "Initiating shutdown of all matchbots.");
        ba.sendChatMessage(2, "Total TWD bot shutdown requested by: " + name);
        ba.sendChatMessage(3, "Total TWD bot shutdown requested by: " + name);
        ba.ipcTransmit(IPC, new IPCCommand(Command.DIE, "all", "Full shutdown"));
    }

    public void cmd_die(String name) {
        ba.sendChatMessage(3, "Disconnecting at the request of " + name);
        ba.sendChatMessage(2, "Disconnecting at the request of " + name);
        ba.sendChatMessage("Disconnecting at the request of " + name);
        ba.cancelTasks();
        TimerTask die = new TimerTask() {
            public void run() {
                ba.die("Termination via !die command");
            }
        };
        ba.scheduleTask(die, 2000);
    }

    private void cmd_update(String name)
    {
        oplist = ba.getOperatorList();
        updateTWDOps();
        ba.sendSmartPrivateMessage(name, "Updating Operators.");
    }

    private void cmd_debug(String name) {
        if (!DEBUG) {
            debugger = name;
            DEBUG = true;
            ba.sendSmartPrivateMessage(name, "Debugging ENABLED. You are now set as the debugger.");
        } else if (debugger.equalsIgnoreCase(name)) {
            debugger = "";
            DEBUG = false;
            ba.sendSmartPrivateMessage(name, "Debugging DISABLED and debugger reset.");
        } else {
            ba.sendChatMessage(name + " has overriden " + debugger + " as the target of debug messages.");
            ba.sendSmartPrivateMessage(name, "Debugging still ENABLED and you have replaced " + debugger + " as the debugger.");
            debugger = name;
        }
    }

    public void cmd_signup(String name, String email) {

        try {
            //check if valid email address, if not then exit
            if (!email.contains("@") || !email.contains(".")) {
                // ba.sendPublicMessage("Invalid Email Adress entered!");
                ba.sendSmartPrivateMessage(name, "Invalid Email Adress entered!");
                return;
            }

            //get signup Query
            PreparedStatement ps_signup = ba.createPreparedStatement(DATABASE, connectionID, this.getPreparedStatement("signup"));

            //put values in prepared statement
            try {
                ps_signup.clearParameters();
                ps_signup.setString(1, Tools.addSlashesToString(name));
                ps_signup.setString(2, Tools.addSlashesToString(email));
                // ba.sendPublicMessage(ps_signup.toString());
                ps_signup.execute();
                ba.sendSmartPrivateMessage(name, "Signed Up " + name + " : " + email + " Successfully!");
                Tools.printLog("Debug: Signed Up " + name + " Successfully!");
            } catch (SQLException e1) {
                try {
                    for (Throwable x : ps_signup.getWarnings()) {
                        if (x.getMessage().toLowerCase().contains("unique")) {
                            // ba.sendPublicMessage(email + " is already registered by " + getUserNameByEmail(email));
                            ba.sendSmartPrivateMessage(name, email + " is already registered by " + getUserNameByEmail(email));
                        } else {
                            Tools.printLog("Error: " + x.getMessage());
                            e1.printStackTrace();
                        }
                    }
                } catch (SQLException e) {
                    // TODO Auto-generated catch block
                    Tools.printLog("Error: " + e.getMessage());
                    Tools.printStackTrace(e);
                }
            }
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void cmd_enable(String name) {
        handleNewPush(name, "enable");
    }

    public void cmd_disable(String name) {
        handleNewPush(name, "disable");
    }

    public void cmd_push(String name, String msg) {
        try {
            pbClient.sendNote( null, getEmailByUserName(name), "", msg);
            // ba.sendPublicMessage("Private Message: '" + msg + "' Pushed Successfully to " + name + ": " + getEmailByUserName(name));
        } catch( PushbulletException e ) {
            // Huh, didn't work
        }
    }

    public void cmd_beep(String name, String msg) {
        handleNewPush(name, msg);
    }

    public void cmd_challenge(String name) {
        String msg = "(MatchBot3)>Axwell is challenging you for a game of 3vs3 TWJD versus Rage. Captains/assistants, ?go twjd and pm me with '!accept Rage'";
        messagePlayerSquadMembers(name, msg);
        Tools.printLog("Debug: " + msg);
    }

    public void cmd_test(String name, String cmd) {
        String[] params = cmd.split(":");
        String squad = params[0];
        String type = params[1].toUpperCase();
        PreparedStatement ps_squadMembers = ba.createPreparedStatement(DB_BOTS, connectionID, this.getPreparedStatement("getenabledsquadmembers"));
        try {
            ps_squadMembers.clearParameters();
            ps_squadMembers.setString(1, Tools.addSlashesToString(squad));
            ps_squadMembers.execute();
            ResultSet rs = ps_squadMembers.getResultSet();
            while (rs.next()) {
                debug("Found: " + rs.getString("fcUserName"));
            }

            String rulesFileName = m_botAction.getGeneralSettings().getString("Core Location") + "/data/Rules/" + type + ".txt";
            BotSettings m_rules = new BotSettings(rulesFileName);
            int matchTypeID = m_rules.getInt("matchtype");

            ResultSet squads = m_botAction.SQLQuery(db, "SELECT tblTWDTeam.fnTeamID, tblTeam.fnTeamID, tblTeam.fcTeamName, tblTWDTeam.fnRating "
                    + "FROM tblTWDTeam, tblTeam "
                    + "WHERE tblTWDTeam.fnMatchTypeID="
                    + matchTypeID
                    + " AND tblTeam.fnTeamID=tblTWDTeam.fnTeamID "
                    + "AND (tblTeam.fdDeleted=0 OR tblTeam.fdDeleted IS NULL) "
                    + "AND tblTWDTeam.fnGames>0 "
                    + "AND tblTeam.fcTeamName != '"
                    + squad
                    + "' "
                    + "ORDER BY tblTWDTeam.fnRating DESC "
                    + "LIMIT 10");

            while (squads.next()) {
                String toSquad = squads.getString("fcTeamName");
                debug("Found top team: " + toSquad);
            }
            m_botAction.SQLClose(squads);
        } catch (SQLException e) {
            Tools.printStackTrace(e);
        } finally {
        }
    }

    public void cmd_accept(String name) {
        String msg = "(MatchBot3)>A game of 3vs3 TWJD versus Rage will start in ?go twjd in 30 seconds";
        messagePlayerSquadMembers(name, msg);
        Tools.printLog("Debug: " + msg);
    }



    /**

        @param statementName = signup : @PlayerName, @PushBulletEmail
        @param statementName = createchannel : @PlayerName, @ChannelName
        @param statementName = getusernamebyemail : @PushBulletEmail
        @param statementName = getemailbyusername : @PlayerName

        @return preparedStatement Query
    */
    private String getPreparedStatement(String statementName) {
        String preparedStatement = "";

        switch (statementName.toLowerCase()) {
        case "signup":
            preparedStatement =
                "SET @PlayerName = ?, @PushBulletEmail = ?;"
                +   "DELETE PBA FROM trench_TrenchWars.tblPBAccount AS PBA "
                +   "JOIN trench_TrenchWars.tblUser AS U ON U.fnUserID = PBA.fnPlayerID AND U.fcUserName = @PlayerName;"
                +   "INSERT INTO trench_TrenchWars.tblPBAccount (fnPlayerID, fcPushBulletEmail, fdCreated)"
                +   "SELECT fnUserID, @PushBulletEmail, NOW() FROM trench_TrenchWars.tblUser WHERE fcUserName = @PlayerName  AND ISNULL(fdDeleted) LIMIT 1;";
            break;

        /*
            case "createchannel":
            preparedStatement =
                    " SET @PlayerName = ?, @ChannelName = ?;"
                +   " DELETE FROM trench_TrenchWars.tblPBSquadChannel WHERE fnSquadID ="
                +   "   (SELECT T.fnTeamID FROM trench_TrenchWars.tblTeam AS T"
                +   "   JOIN trench_TrenchWars.tblTeamUser AS TU ON T.fnTeamID = TU.fnTeamID AND fnCurrentTeam = 1"
                +   "   JOIN trench_TrenchWars.tblUser AS U ON TU.fnUserID = U.fnUserID"
                +   "   WHERE U.fcUserName = @PlayerName AND isnull(T.fdDeleted));"
                +   " INSERT INTO trench_TrenchWars.tblPBSquadChannel (fnSquadID, fcChannelName)"
                +   " SELECT T.fnTeamID, @ChannelName AS fcChannelName"
                +   " FROM trench_TrenchWars.tblTeam AS T"
                +   " JOIN trench_TrenchWars.tblTeamUser AS TU ON T.fnTeamID = TU.fnTeamID AND fnCurrentTeam = 1"
                +   " JOIN trench_TrenchWars.tblUser AS U ON TU.fnUserID = U.fnUserID"
                +   " WHERE U.fcUserName = @PlayerName AND isnull(T.fdDeleted);";
            break;
        */
        case "getusernamebyemail": //can't use @Params if expecting recordset results
            preparedStatement =
                " SELECT U.fcUserName FROM trench_TrenchWars.tblPBAccount AS PBA"
                +   " JOIN trench_TrenchWars.tblUser AS U ON PBA.fnPlayerID = U.fnUserID WHERE PBA.fcPushBulletEmail = ?;";
            break;

        case "getemailbyusername": //can't use @Params if expecting recordset results
            preparedStatement =
                " SELECT PBA.fcPushBulletEmail FROM trench_TrenchWars.tblPBAccount AS PBA"
                +   " JOIN trench_TrenchWars.tblUser AS U ON PBA.fnPlayerID = U.fnUserID WHERE U.fcUserName = ?;";
            break;

        case "interpretbeep": //can't use @Params if expecting recordset results
            preparedStatement =
                " SELECT fcCommand, fcCommandShortDescription FROM trench_TrenchWars.tblPBCommands"
                +   " WHERE INSTR(?, fcCommand) > 0;";
            break;

        case "interpretcommand": //can't use @Params if expecting recordset results
            preparedStatement =
                " SELECT fcCommand, fcCommandShortDescription, fnSettingUpdate  FROM trench_TrenchWars.tblPBCommands"
                +   " WHERE INSTR(?, fcCommand) > 0;";
            break;

        /*
            case "getsquadchannel": //can't use @Params if expecting recordset results
            preparedStatement =
                    " SELECT PBS.fcChannelName FROM trench_TrenchWars.tblPBSquadChannel AS PBS"
                +   " JOIN trench_TrenchWars.tblTeam AS T ON T.fnTeamID = PBS.fnSquadID"
                +   " JOIN trench_TrenchWars.tblTeamUser AS TU ON T.fnTeamID = TU.fnTeamID AND fnCurrentTeam = 1"
                +   " JOIN trench_TrenchWars.tblUser AS U ON TU.fnUserID = U.fnUserID AND U.fcUserName = ? AND isnull(T.fdDeleted);";
            break;
        */
        case "getplayersquadmembers": //can't use @Params if expecting recordset results
            preparedStatement =
                " SELECT U.fnUserID, U.fcUserName, PBA.fcPushBulletEmail, PBA.fbDisabled, T.fcTeamName FROM trench_TrenchWars.tblUser AS U"
                +   " JOIN trench_TrenchWars.tblTeamUser AS TU ON TU.fnUserID = U.fnUserID"
                +   " JOIN trench_TrenchWars.tblTeam AS T ON T.fnTeamID = TU.fnTeamID AND fnCurrentTeam = 1"
                +   " JOIN (    SELECT T.fnTeamID FROM trench_TrenchWars.tblTeam AS T"
                +   "       JOIN trench_TrenchWars.tblTeamUser AS TU ON T.fnTeamID = TU.fnTeamID AND fnCurrentTeam = 1"
                +   "       JOIN trench_TrenchWars.tblUser AS U ON TU.fnUserID = U.fnUserID AND U.fcUserName = ?"
                +   "    ) AS SID ON SID.fnTeamID = T.fnTeamID"
                +   " JOIN trench_TrenchWars.tblPBAccount AS PBA ON U.fnUserID = PBA.fnPlayerID;";
            break;

        case "enabledisablepb": //can't use @Params if expecting recordset results
            preparedStatement =
                " SET @PlayerName = ?;"
                +   " UPDATE trench_TrenchWars.tblPBAccount"
                +   " SET fbDisabled = ?"
                +   " WHERE fnPlayerID = (SELECT U.fnUserID FROM trench_TrenchWars.tblUser AS U WHERE U.fcUserName = @PlayerName LIMIT 1);";
            break;
        case "getenabledsquadmembers": //can't use @Params if expecting recordset results
            preparedStatement =
              " SELECT U.fnUserID, U.fcUserName, PBA.fcPushBulletEmail, PBA.fbDisabled, T.fcTeamName FROM trench_TrenchWars.tblUser AS U"
             + " JOIN trench_TrenchWars.tblTeamUser AS TU ON TU.fnUserID = U.fnUserID"
             + " JOIN trench_TrenchWars.tblTeam AS T ON T.fnTeamID = TU.fnTeamID AND TU.fnCurrentTeam = 1 AND T.fcTeamName = ?"
             + " JOIN trench_TrenchWars.tblPBAccount AS PBA ON U.fnUserID = PBA.fnPlayerID AND PBA.fbDisabled = 0;";
           break;
        }

        return preparedStatement;
    }

    private String getUserNameByEmail(String email) {
        String userName = "";
        PreparedStatement ps_getusernamebyemail = ba.createPreparedStatement(DATABASE, connectionID, this.getPreparedStatement("getusernamebyemail"));

        try {
            ps_getusernamebyemail.clearParameters();
            ps_getusernamebyemail.setString(1, Tools.addSlashesToString(email));
            ps_getusernamebyemail.execute();

            try (ResultSet rs = ps_getusernamebyemail.getResultSet()) {
                if (rs.next()) {
                    userName = rs.getString(1);
                }
            }
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            Tools.printStackTrace(e);
        }

        return userName;
    }

    private String getEmailByUserName(String userName) {
        String email = "";
        PreparedStatement ps_getemailbyusername = ba.createPreparedStatement(DATABASE, connectionID, this.getPreparedStatement("getemailbyusername"));

        try {
            ps_getemailbyusername.clearParameters();
            ps_getemailbyusername.setString(1, Tools.addSlashesToString(userName));
            ps_getemailbyusername.execute();

            try (ResultSet rs = ps_getemailbyusername.getResultSet()) {
                if (rs.next()) {
                    email = rs.getString(1);
                }
            }
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            Tools.printStackTrace(e);
        }

        return email;
    }

    private ResultSet getInterpretCommand(String userName, String userMsg) {
        //String commandResponseOriginal = commandResponse;
        ResultSet rs = null;
//        Tools.printLog(ba.getCoreData().toString());
//        Tools.printLog(ba.getCoreData().getSQLManager().toString());
        PreparedStatement ps_getinterpretbeep = ba.createPreparedStatement(DATABASE, connectionID, this.getPreparedStatement("interpretcommand"));

        try {
            ps_getinterpretbeep.clearParameters();
            ps_getinterpretbeep.setString(1, Tools.addSlashesToString(userMsg));
            ps_getinterpretbeep.execute();
            rs = ps_getinterpretbeep.getResultSet();
        }
        catch (SQLException e) {
            // TODO Auto-generated catch block
            Tools.printStackTrace(e);
        }

        //if (commandResponse == commandResponseOriginal) {commandResponse = "";}
        return rs;
    }

    /*
        public String getSquadChannel(String userName) {
        String squadChannel = "";
        PreparedStatement ps_getsquadchannel = ba.createPreparedStatement(db, connectionID, this.getPreparedStatement("getsquadchannel"));
        try {
            ps_getsquadchannel.clearParameters();
            ps_getsquadchannel.setString(1, Tools.addSlashesToString(userName));
            ps_getsquadchannel.execute();
            try (ResultSet rs = ps_getsquadchannel.getResultSet()) {
                if (rs.next()) {
                    squadChannel = rs.getString(1);
                }
            }
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return squadChannel;
        }
    */

    /*
        public Boolean createSquadChannel(String userName, String squadChannel) {
        PreparedStatement ps_createsquadchannel = ba.createPreparedStatement(db, connectionID, this.getPreparedStatement("createchannel"));
        try {
            ps_createsquadchannel.clearParameters();
            ps_createsquadchannel.setString(1, Tools.addSlashesToString(userName));
            ps_createsquadchannel.setString(2, Tools.addSlashesToString(squadChannel));
            ps_createsquadchannel.execute();
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
        return true;
        }
    */

    private void switchAlertsPB (String userName, Integer Disable) {
        PreparedStatement ps_enablepb = ba.createPreparedStatement(DATABASE, connectionID, this.getPreparedStatement("enabledisablepb"));

        try {
            ps_enablepb.clearParameters();
            ps_enablepb.setString(1, Tools.addSlashesToString(userName));
            ps_enablepb.setInt(2, Disable);
            ps_enablepb.execute();
            //ba.sendPublicMessage(ps_enablepb.toString());
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Tools.printLog(e.getMessage());
        }
    }

    private void messagePlayerSquadMembers(String userName, String msg) {
        String squadName = "";

        if (msg == "") {
            return;
        }

        PreparedStatement ps_messagePlayerSquadMembers = ba.createPreparedStatement(DATABASE, connectionID, this.getPreparedStatement("getplayersquadmembers"));

        try {
            ps_messagePlayerSquadMembers.clearParameters();
            ps_messagePlayerSquadMembers.setString(1, Tools.addSlashesToString(userName));
            ps_messagePlayerSquadMembers.execute();

            try (ResultSet rs = ps_messagePlayerSquadMembers.getResultSet()) {
                while (rs.next()) {
                    if (rs.getInt("fbDisabled") != 1) {
                        pbClient.sendNote( null, rs.getString("fcPushBulletEmail"), "", msg);
                        Tools.printLog("Debug: Pushed to " + rs.getString("fcUserName")); //+ " | " + rs.getString("fcPushBulletEmail") );
                        squadName = rs.getString("T.fcTeamName");
                    }
                }
            } catch (PushbulletException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                Tools.printLog(e.getMessage());
            }
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Tools.printLog(e.getMessage());
        } finally {
            if (squadName != "") {
                ba.sendSquadMessage(squadName, msg);
            }
        }
    }

    private void handleNewPush(String playerName, String userMsg) {
        String squadAlert = "";
        Boolean settingChange = false;
        ResultSet rs_InterpretCommand = getInterpretCommand(playerName, userMsg);

        try {
            while (rs_InterpretCommand.next()) {
                if (rs_InterpretCommand.getInt("fnSettingUpdate") == 1) {
                    //This is a setting command
                    try {
                        switch (rs_InterpretCommand.getString("fcCommand").toLowerCase()) {
                        case "enable":
                            switchAlertsPB(playerName, 0);
                            settingChange = true;
                            break;

                        case "disable":
                            switchAlertsPB(playerName, 1);
                            settingChange = true;
                            break;
                        }

                        //if setting change above matches, send personal note to player's pushbullet account letting them know of successful change
                        if (settingChange) {
                            pbClient.sendNote( null, getEmailByUserName(playerName), "", rs_InterpretCommand.getString("fcCommandShortDescription"));
                            ba.sendSmartPrivateMessage(playerName, rs_InterpretCommand.getString("fcCommandShortDescription"));
                        }
                    } catch (PushbulletException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    } finally {
                        settingChange = false;
                    }
                } else {
                    //This is a beep
                    if (squadAlert != "") {
                        squadAlert += ",";
                    }

                    squadAlert += rs_InterpretCommand.getString("fcCommandShortDescription");
                }
            }
        } catch (SQLException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        //send a message to everyone on squad who has pushbullet setup to alert of beep
        if (squadAlert != "") {
            squadAlert = playerName + " beeped for: " + squadAlert;
            messagePlayerSquadMembers(playerName, squadAlert);
            Tools.printLog("Debug: " + playerName + " : " + squadAlert);
        } else {
            Tools.printLog("Filtered Message From " + playerName + " : " + userMsg);
        }
    }


    private void debug(String msg) {
        if (DEBUG)
            ba.sendSmartPrivateMessage(debugger, "[DEBUG] " + msg);
    }

    private void requestEvents() {
        EventRequester er = ba.getEventRequester();
        er.request(EventRequester.ARENA_JOINED);
        er.request(EventRequester.LOGGED_ON);
        er.request(EventRequester.PLAYER_ENTERED);
        er.request(EventRequester.PLAYER_LEFT);
        er.request(EventRequester.MESSAGE);
    }

    private void checkIn() {
        ba.ipcTransmit(IPC, new IPCCommand(Command.CHECKIN, "all", "checkin"));
        TimerTask check = new TimerTask() {
            public void run() {
                if (startup)
                    startup = false;

                checkArenas();
            }
        };
        ba.scheduleTask(check, 3000);
    }

    private void checkArenas() {
        debug("Checking arenas...");
        checkDiv("twbd");
        checkDiv("twdd");
        checkDiv("twjd");
        checkDiv("twsd");
        checkDiv("twfd");
        arenaDebugDump(debugger);
    }

    private void checkDiv(String div) {
        if (startup || shutdown)
            return;

        div = div.toLowerCase().substring(0, 4);

        // TODO: modify so this takes arena names from cfg.
        Vector<String> alwaysKeepAlive = new Vector<String>();
        // The following arenas need to be kept alive at all times
        String[] alwaysKeepAliveStringArray = { "twdd", "twdd3", "twjd",
                                                "twbd", "twsd", "twfd"
                                              };
        alwaysKeepAlive.addAll(Arrays.asList(alwaysKeepAliveStringArray));

        // Following numbers are allowed to be suffixed to the arena
        Vector<String> allowedSuffixNumbers = new Vector<String>();
        allowedSuffixNumbers.addAll(Arrays.asList(new String[] { "", "2", "3",
                                    "4", "5"
                                                               }));

        // Arenas in division that currently have a bot.
        Vector<String> currentDivArenas = new Vector<String>();

        // Filter out the division we are currently looking into..
        for (String arena : arenas.keySet()) {
            if (arena.startsWith(div.toLowerCase()))
                currentDivArenas.add(arena);
        }

        // Check the arenas that need to be kept alive
        for (String keepAliveArena : alwaysKeepAlive) {
            // if its not in the division, skip
            if (!keepAliveArena.startsWith(div)) {
                continue;
            }

            if (!currentDivArenas.contains(keepAliveArena)) {
                botSpawn(keepAliveArena);
            }
        }

        // Now check if there are dead arenas & if they are dead kill the bot &
        // compute ideal arena size
        // Remove elements from allowedSuffixNumbers as we find arenas

        int idealDivSize = div.equals("twdd") ? 2 : 1;
        int realDivSize = 0;

        for (String arena : currentDivArenas) {
            String arenaNum = "";

            if (arena.length() > 4) {
                arenaNum = arena.substring(0, 4);
            }

            allowedSuffixNumbers.remove(arenaNum);

            realDivSize++;

            if (alwaysKeepAlive.contains(arena)) {

                if (arenas.get(arena).hasGame()) {
                    botStay(arena);
                    idealDivSize++;
                }

                continue;
            }

            // If a game is running in the arena, keep it running.. pop the
            // number out of allowed suffixes
            // Otherwise, kill it
            if (arenas.get(arena).hasGame()) {
                botStay(arena);
                idealDivSize++;
            } else {
                // If we are larger than our ideal size, kill the bot.
                if (realDivSize > idealDivSize) {
                    debug(div + " Ideal: " + idealDivSize + " Real:"
                          + realDivSize);

                    if (!arenas.get(arena).isActive()) {
                        botRemove(arena);
                        realDivSize--;
                    }
                }
            }
        }

        debug(div + " Ideal: " + idealDivSize + " Real:"
              + realDivSize);

        // Now check if we need to spawn anyone..
        while (realDivSize < idealDivSize) {
            debug(div + " Available suffixes: " + allowedSuffixNumbers.size());

            if (allowedSuffixNumbers.size() > 0) {
                botSpawn(div + allowedSuffixNumbers.remove(0));
                debug("Attempting to spawn: " + div + allowedSuffixNumbers.remove(0) );
                realDivSize++;
            }
        }

    }
    /*
        private void checkDivPartial(String div, int start, int end) {
        if (startup || shutdown) return;
        div = div.substring(0, 4).toLowerCase();
        Arena arena;
        String arenaName;
        for(; start <= end; start++) {
            arenaName = div;
            if(start != 1)
                arenaName += start;

            if (arenas.containsKey(div)) {
                arena = arenas.get(div);
                if (arena.hasGame()) {
                    botStay(arena.bot);
                    continue;
                } else if (!arena.isActive()){
                    botRemove(arenaName);
                    break;
                }
            } else {
                botSpawn(arenaName);
                break;
            }
        }

        for(++start; start <= end; start++) {
            arenaName = div;
            if(start != 1)
                arenaName += start;

            botRemove(arenaName);
        }
        }*/

    private void lockBots() {
        while (!needsBot.isEmpty() && !freeBots.isEmpty()) {
            String name = needsBot.remove(0);
            sentSpawn.remove(name);
            botLock(freeBots.remove(0), name);
        }
    }

    private void botDisconnected(String bot) {
        debug("Bot disconnected: " + bot);

        if (bots.containsKey(bot)) {
            Arena arena = bots.get(bot);

            if (arena.status == ArenaStatus.DYING) {
                arenas.remove(arena.name);
                bots.remove(bot);
            } else if (!shutdown) {
                // UNEXPECTED DISCONNECT
                debug("Unexpected disconnect for " + bot);
                arenas.remove(arena.name);
                bots.remove(bot);

                if (arena != null && arena.game != null) {
                    arena.game.squad1.endGame(arena.name);
                    arena.game.squad2.endGame(arena.name);
                }
            }

            checkArenas();
        }
    }

    private void botStay(String name) {
        if (arenas.containsKey(name)) {
            Arena arena = arenas.get(name);

            if (arena.status == ArenaStatus.DYING) {
                debug("Bot stay: " + name);
                arena.status = ArenaStatus.READY;
                ba.ipcTransmit(IPC, new IPCCommand(Command.STAY, name, null));
            }
        }
    }

    private void botSpawn(String name) {
        if (shutdown) return;

        if (!arenas.containsKey(name)) {
            Arena arena = new Arena(name);
            arenas.put(low(name), arena);
        } else if (arenas.get(name).status != ArenaStatus.WAITING)
            return;

        debug("Bot spawn: " + name);
        needsBot.add(name);

        if (freeBots.isEmpty() && !sentSpawn.contains(name)) {
            sentSpawn.add(name);
            ba.sendSmartPrivateMessage(HUB, "!spawn matchbot");
        } else
            lockBots();
    }

    private void botRemove(String name) {
        if (arenas.containsKey(name)) {
            needsBot.remove(name);
            Arena arena = arenas.get(name);

            if (arena.status != ArenaStatus.DYING) {
                debug("Arena remove: " + name);
                arena.status = ArenaStatus.DYING;
                ba.ipcTransmit(IPC, new IPCCommand(Command.DIE, arena.bot, "DEAD ARENA on " + name));
            }
        } else if (oplist.isBotExact(name)) {
            debug("Bot remove: " + name);
            ba.ipcTransmit(IPC, new IPCCommand(Command.DIE, name, "REMOVE bot " + name));
        }
    }

    private void updateTWDOps() {
        try {
            ResultSet r = ba.SQLQuery(DATABASE, "SELECT tblUser.fcUsername FROM `tblUserRank`, `tblUser` WHERE `fnRankID` = '14' AND tblUser.fnUserID = tblUserRank.fnUserID");

            if (r == null)
                return;

            twdOps.clear();

            while (r.next()) {
                String name = r.getString("fcUsername");
                twdOps.put(name.toLowerCase(), name);
            }

            ba.SQLClose(r);
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Unable to update TWDOp list.");
        }
    }
    /**
        Gets the Arena object and sets the bot name. Then sets the Arena status to READY
        and sends !lock for the appropriate division and arena.

        @param bot
        @param arenaName
    */
    private void botLock(String bot, String arenaName) {
        if (shutdown) return;

        debug("Bot lock: " + bot + "-" + arenaName);
        Arena arena = arenas.get(arenaName);

        if (arena.status != ArenaStatus.WAITING) {
            debug("Status was not waiting, so aborting " + arena.name);
            return;
        }

        arena.bot = bot;
        arena.status = ArenaStatus.READY;
        String div = arenaName.substring(0, 4);

        if (div.equals("twbd"))
            ba.sendSmartPrivateMessage(bot, "!lock " + TWBD + ":" + arenaName);
        else if (div.equals("twjd"))
            ba.sendSmartPrivateMessage(bot, "!lock " + TWJD + ":" + arenaName);
        else if (div.equals("twdd"))
            ba.sendSmartPrivateMessage(bot, "!lock " + TWDD + ":" + arenaName);
        else if (div.equals("twsd"))
            ba.sendSmartPrivateMessage(bot, "!lock " + TWSD + ":" + arenaName);
        else if (div.equals("twfd"))
            ba.sendSmartPrivateMessage(bot, "!lock " + TWFD + ":" + arenaName);

        bots.put(bot, arena);
        final Arena farena = arena;
        TimerTask flush = new TimerTask() {
            public void run() {
                farena.flushIPC();
            }
        };
        ba.scheduleTask(flush, 2400);
    }

    private void sendAlerts(Game game) {
        for (String name : alerts)
            ba.sendSmartPrivateMessage(name, game.getType() + " (" + game.arena.name + "): " + game.squad1.name + " vs " + game.squad2.name);
    }

    private boolean isTWD(String name) {
        name = low(name);
        return name.startsWith("twdd") || name.startsWith("twbd") || name.startsWith("twjd") || name.startsWith("twsd") || name.startsWith("twfd");
    }

    private boolean isTWDOp(String name) {
        return twdOps.containsKey(name.toLowerCase());
    }

    private void arenaDebugDump(String name) {

        if(debugger.equals("")) return;

        ArrayList<String> dump = new ArrayList<String>();

        for (Arena a : arenas.values()) {
            String status = a.name;
            status += (" " + a.status.toString());
            status += (" " + (a.expired ? "expired" : "not expired"));
            status += (" " + a.bot);

            dump.add(status);
        }

        String[] send = dump.toArray(new String[dump.size()]);

        ba.remotePrivateMessageSpam(name, send);
    }

    class Arena {

        String name;
        String bot;
        Game game;
        ArenaStatus status;
        boolean activeChalls, expired;
        HashMap<String, IPCChallenge> challs;

        public Arena(String name) {
            this.name = name;
            status = ArenaStatus.WAITING;
            bot = null;
            game = null;
            activeChalls = false;
            expired = false;
            challs = new HashMap<String, IPCChallenge>();
        }

        public void expire() {
            if (name.equalsIgnoreCase("twdd3")) return;

            activeChalls = false;
            expired = true;
        }

        public boolean hasGame() {
            return game != null;
        }

        public boolean isActive() {
            return activeChalls || hasGame();
        }

        public HashMap<String, IPCChallenge> getChalls() {
            return challs;
        }

        public void add(IPCChallenge challenge) {
            if (challs.containsKey(low(challenge.getName())))
                ba.sendSmartPrivateMessage(challenge.getName(), "Previous challenge replaced.");

            challs.put(low(challenge.getName()), challenge);
        }

        public void flushIPC() {
            if (challs.isEmpty()) return;

            ipcFlag();

            for (IPCChallenge ipc : challs.values()) {
                ipc.setBot(bot);
                ba.ipcTransmit(IPC, ipc);
            }

            challs.clear();
        }

        public void ipcFlag() {
            if (!expired)
                activeChalls = true;
        }

        public void flag() {
            expired = false;
            activeChalls = true;
        }
    }

    class Game {

        Squad squad1, squad2;
        Arena arena;
        int round;
        int score1;
        int score2;
        int state;
        int id;
        HashSet<String> alerted;

        public Game(int id, String team1, String team2, String arenaName) {
            this.id = id;
            arena = null;
            squad1 = null;
            squad2 = null;
            score1 = 0;
            score2 = 0;
            round = 0;
            state = 0;
            alerted = new HashSet<String>();

            if (arenas.containsKey(arenaName))
                arena = arenas.get(arenaName);
            else {
                debug("Arena not found.");
                return;
            }

            if (squads.containsKey(low(team1)))
                squad1 = squads.get(low(team1));
            else {
                squad1 = new Squad(team1);
                squads.put(low(team1), squad1);
            }

            if (squads.containsKey(low(team2)))
                squad2 = squads.get(low(team2));
            else {
                squad2 = new Squad(team2);
                squads.put(low(team2), squad2);
            }

            if (squad1 != null && squad2 != null) {
                squad1.addGame(arenaName);
                squad2.addGame(arenaName);
                sendAlerts(this);
            }
        }

        public void setID(int id) {
            this.id = id;
        }

        public void alert(String name, String squad) {
            if (!alerted.isEmpty() && alerted.contains(name.toLowerCase()))
                return;

            String nme;

            if (squad.equalsIgnoreCase(squad1.name))
                nme = squad2.name;
            else
                nme = squad1.name;

            String stateS;

            if (state == 0)
                stateS = "preparing";
            else
                stateS = "playing";

            ba.sendSmartPrivateMessage(name, "Your squad is " + stateS + " a " + getType() + " match against " + nme + " in ?go " + arena.name);
            alerted.add(name.toLowerCase());
        }

        public String getType() {
            String aname = arena.name.toLowerCase();

            if (aname.startsWith("twbd"))
                return "TWD Basing";
            else if (aname.startsWith("twdd"))
                return "TWD Dueling";
            else if (aname.startsWith("twjd"))
                return "TWD Javelin";
            else if (aname.startsWith("twsd"))
                return "TWD Spider";
            else if (aname.startsWith("twfd"))
                return "TWD Fighter";
            else
                return "TWD";
        }

        public void setState(int s) {
            state = s;
        }

        public void setRound(int r) {
            round = r;
        }

        public void next() {
            if (state == 0)
                state = 1;
            else {
                state = 0;
                round++;
            }
        }

        public String toString() {
            String stateS;

            if (state == 0)
                stateS = "starting";
            else
                stateS = "playing";

            String result = "";

            if (getType().equals("TWD Basing"))
                result += getType() + "(" + arena.name + "): " + squad1.name + " vs " + squad2.name + " (" + stateS + ")";
            else
                result += getType() + "(" + arena.name + "): " + squad1.name + " vs " + squad2.name + " (" + score1 + "-" + score2 + " " + stateS + " round " + round + ")";

            return result;
        }
    }

    class Squad {

        String name;
        VectorSet<String> games;

        public Squad(String name) {
            this.name = name;
            games = new VectorSet<String>();
        }

        public VectorSet<String> getGames() {
            return games;
        }

        public void addGame(String arena) {
            games.add(arena);
        }

        public void endGame(String arena) {
            games.removeElement(arena);

            if (games.isEmpty())
                squads.remove(low(name));
        }

    }

    private String low(String str) {
        return str.toLowerCase();
    }
}
