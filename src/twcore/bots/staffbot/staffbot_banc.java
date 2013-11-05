package twcore.bots.staffbot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TimerTask;

import twcore.bots.Module;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCEvent;
import twcore.core.util.ipc.IPCMessage;

/**
 * StaffBot BanC module
 * 
 * - automatic-silence - automatic-spec-lock - automatic-kick-lock - automatic-super-spec-lock @author quiles/dexter - !search feature @author
 * quiles/dexter - !lifted feature @author quiles changed lifted banc to a new field instead of deleting. TODO:
 * 
 * - Time left in !listban #ID
 * 
 * @author Maverick
 */
public class staffbot_banc extends Module {

    // Helps screens
    final String[] helpER = {
            "---------------------- [ BanC: Operators+ ] -------------------------------------------------------------------------",
            " !silence <player>:<time>[mins][d]         - Initiates an automatically enforced",
            "                                               silence on <player> for <time/mins/days>.",
            " !superspec <player>:<time>[mins][d]       - Initiates an automatically enforced",
            "                                               spec-lock on <player> the ships 2,4 and 8",
            "                                               for <time/mins/days>",
            " !spec <player>:<time>[mins][d]            - Initiates an automatically enforced",
            "                                               spectator-lock on <player>",
            "                                               for <time/mins/days>.",
            " !search -help                             - !search command help guide",
            " !search <player>[:<#banC>][:<#Warnings>]  - Search the players history",
            " !listban -help                 - !listban command help guide",
            " !listban [arg] [count]         - Shows last 10/[count] BanCs. Optional arguments see below.",
            " !listban [#id]                 - Shows information about BanC with <id>.",
            " !changeban <#id> <arguments>   - Changes banc with <id>. Type !arg to see Arguments. Don't forget the #",

            //arguments commented by quiles. because I coded the !listban -help to teach how to use !listban easily.
            /*" Arguments:",
            "             -player='<..>'     - Specifies player name",
            "             -d=#               - Specifies duration in minutes",
            "             -a=<...>           - Specifies access requirement, options; mod / smod / sysop",
            "             -ip=<#.#.#.#> -ir  - Specifies IP or remove IP (-ir) so banc is not matched by IP",
            "             -mid=#  -mr        - Specifies MID or remove MID (-mr) so banc is not matched by MID",
            "             -notif=<yes/no>    - Specifies wether a notification is sent on staff chat",
            "             -staffer='<..>'    - Specifies the name who issued the ban. [Only avail. on !listban]",*/

            " !bancomment <#id> <comments>   - Adds / Changes comments on BanC with specified #id.",
            " !liftban <#id>                 - Removes ban with #id.", " !banaccess                     - Returns the access level restrictions",
    //" !shortcutkeys                  - Shows the available shortcut keys for the commands above"
    };

    final String[] helpSmod = { "----------------------[ BanC: SMod+ ]---------------------",
            " !reload                           - Reloads the list of active bancs from the database",
            " !forcedb                          - Forces to connect to the database",
            " !searchip <ip>                    - where ip can be the x. or x.x. or full x.x.x",
            " !addop                            - Adds a Banc Operator",
            " !removeop                         - Removes a Banc Operator and adds them to Revoked list",
            " !listops                          - Displays all Banc Operators and Banc Revoked Operators",
            " !deleteop                         - If removed from staff, use this.",
            " !isop                             - Checks to see if name is an operator",

    };

    final String[] shortcutKeys = { "Available shortcut keys:", " !silence  -> !s   |  !listban    -> !lb",
            " !spec     -> !sp  |  !changeban  -> !cb",
    //" !kick     -> !k   |  !bancomment -> !bc",
    };

    // Definition variables
    public enum BanCType {
        SILENCE, SPEC, SUPERSPEC
    }

    // private staffbot_database Database = new staffbot_database();

    HashMap<String, String> bancStaffers = new HashMap<String, String>();
    HashMap<String, String> bancOp = new HashMap<String, String>();
    HashMap<String, String> bancRevoked = new HashMap<String, String>();

    private final String botsDatabase = "bots";
    private final String trenchDatabase = "website";
    private final String uniqueConnectionID = "banc";
    private final String IPCBANC = "banc";
    private final String IPCALIAS = "pubBots";
    private final String webdb = "website";

    //private final String MINACCESS_BANCSTAFFER = "OPS";
    private final String MINACCESS_ER = "ER";
    private final String MINACCESS_MOD = "MOD";
    private final String MINACCESS_SMOD = "SMOD";
    private final String MINACCESS_SYSOP = "SYSOP";

    private final Integer[][] BANCLIMITS = { { 120, 60 * 24 * 7, 0, 0 }, // BanC limits
            { 120, 60 * 24 * 7, 0, 0 },
            //{ null, 30, 60, 0 }, // [BanCType] [Accesslevel]
            { 120, 60 * 24 * 7, 0, 0 } };
    private SimpleDateFormat datetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private final int BANC_MAX_DURATION = 525600; // (365 days in minutes)
    @SuppressWarnings("unused")
    private final static int BANC_EXPIRE_TIME = Tools.TimeInMillis.WEEK * 2;
    @SuppressWarnings("unused")
    private final static int MAX_NAME_SUGGESTIONS = 20;

    // Operation variables
    private List<BanC> activeBanCs = Collections.synchronizedList(new ArrayList<BanC>());

    boolean stop = false;

    // PreparedStatements
    private PreparedStatement psListBanCs, psCheckAccessReq, psActiveBanCs, psAddBanC, psUpdateComment, psRemoveBanC, psLookupIPMID, psKeepAlive1,
            psKeepAlive2, psElapsed, psExpired;

    // Keep database connection alive workaround
    private KeepAliveConnection keepAliveConnection = new KeepAliveConnection();
    private UpdateElapsed updateElapsed;

    @Override
    public void initializeModule() {
        //*/
        // Initialize Prepared Statements
        psActiveBanCs = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "SELECT * FROM tblBanc WHERE (fnDuration = 0 OR fnElapsed < fnDuration) AND fbLifted = 0");
        //psListBanCs = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "SELECT * FROM tblBanc LIMIT 0,?");
        psCheckAccessReq = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "SELECT fcMinAccess FROM tblBanc WHERE fnID = ?");
        psAddBanC = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "INSERT INTO tblBanc(fcType, fcUsername, fcIP, fcMID, fcMinAccess, fnDuration, fcStaffer, fdCreated) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())", true);
        psUpdateComment = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "UPDATE tblBanc SET fcComment = ? WHERE fnID = ?");
        //psRemoveBanC = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "DELETE FROM tblBanc WHERE fnID = ?");
        psRemoveBanC = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "UPDATE tblBanc SET fbLifted = 1, fdExpired = NOW() WHERE fnID = ?");
        psLookupIPMID = m_botAction.createPreparedStatement(trenchDatabase, uniqueConnectionID, "SELECT fcIPString, fnMachineId FROM tblAlias INNER JOIN tblUser ON tblAlias.fnUserID = tblUser.fnUserID WHERE fcUserName = ? ORDER BY fdUpdated DESC LIMIT 0,1");
        psKeepAlive1 = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "SHOW DATABASES");
        psKeepAlive2 = m_botAction.createPreparedStatement(trenchDatabase, uniqueConnectionID, "SHOW DATABASES");
        psElapsed = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "UPDATE tblBanc SET fnElapsed = ? WHERE fnID = ?");
        psExpired = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "UPDATE tblBanc SET fdExpired = NOW() WHERE fnID = ? AND fdExpired IS NULL");
        
        if (psActiveBanCs == null || psCheckAccessReq == null || psAddBanC == null || psUpdateComment == null || psRemoveBanC == null
                || psLookupIPMID == null || psKeepAlive1 == null || psKeepAlive2 == null || psElapsed == null || psExpired == null) {
            Tools.printLog("BanC: One or more PreparedStatements are null! Module BanC disabled.");
            m_botAction.sendChatMessage(2, "BanC: One or more connections (prepared statements) couldn't be made! Module BanC disabled.");
            //this.cancel();

        } else {
            // Join IPC channels
            m_botAction.ipcSubscribe(IPCALIAS);
            m_botAction.ipcSubscribe(IPCBANC);

            // load active BanCs
            loadActiveBanCs();

            // Send out IPC messages for active BanCs
            sendIPCActiveBanCs(null);

            // Start TimerTasks
            CheckExpiredBanCs checkExpiredBanCs = new CheckExpiredBanCs();
            m_botAction.scheduleTaskAtFixedRate(checkExpiredBanCs, Tools.TimeInMillis.MINUTE, Tools.TimeInMillis.MINUTE);

            updateElapsed = new UpdateElapsed();
            m_botAction.scheduleTaskAtFixedRate(updateElapsed, 3 * Tools.TimeInMillis.MINUTE, 5 * Tools.TimeInMillis.MINUTE);

            //Schedule the timertask to keep alive the database connection
            m_botAction.scheduleTaskAtFixedRate(keepAliveConnection, 5 * Tools.TimeInMillis.MINUTE, 2 * Tools.TimeInMillis.MINUTE);

            //Load the operators
            restart_ops();
        }
    }

    @Override
    public void cancel() {
        stop = true;
        if( updateElapsed != null )     // Gave NPE in error log; better safe than sorry
            updateElapsed.run();

        m_botAction.ipcUnSubscribe(IPCALIAS);
        m_botAction.ipcUnSubscribe(IPCBANC);

        m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psCheckAccessReq);
        m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psActiveBanCs);
        m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psListBanCs);
       // m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psActiveBanCs);
        m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psAddBanC);
        m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psUpdateComment);
        m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psRemoveBanC);
        m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psElapsed);
        m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psExpired);
        m_botAction.closePreparedStatement(trenchDatabase, uniqueConnectionID, psLookupIPMID);
        m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psKeepAlive1);
        m_botAction.closePreparedStatement(trenchDatabase, uniqueConnectionID, psKeepAlive2);

        m_botAction.cancelTasks();
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
    }

    @Override
    public void handleEvent(Message event) {
        if (stop)
            return;

        if (event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE || event.getMessageType() == Message.PRIVATE_MESSAGE) {

            String message = event.getMessage();
            String messageLc = message.toLowerCase();
            String name = event.getMessager() == null ? m_botAction.getPlayerName(event.getPlayerID()) : event.getMessager();
            OperatorList opList = m_botAction.getOperatorList();

            // Minimum BANC STAFFER access requirement for all !commands
            //Everytime a someone tries a command, reload the ops and see if they have access
            restart_ops();
            if (!bancStaffers.containsKey(name.toLowerCase())) {
                if (bancRevoked.containsKey(name.toLowerCase()))
                    m_botAction.sendSmartPrivateMessage(name, "Sorry, Your access to BanC has been revoked");
                return;
            }

            if (messageLc.startsWith("!addop"))
                //!addop
                addBancOperator(name, messageLc.substring(7));
            if (messageLc.startsWith("!removeop"))
                //!revokeop
                removeBancStaffer(name, messageLc.substring(10));
            if (messageLc.equals("!listops"))
                showBancPeople(name);
            if (messageLc.startsWith("!deleteop"))
                deleteBancOperator(name, messageLc.substring(10));
            if (messageLc.startsWith("!isop"))
                isOp(name, messageLc.substring(6));
            if (messageLc.equals("!reloadops"))
                if (!opList.isOwner(name))
                    return;
            restart_ops();
            // !help
            if (messageLc.startsWith("!help"))
                cmd_Help(name, message.substring(5).trim());
            else if (messageLc.startsWith("!lifted"))
                searchByLiftedBancs(name);

            else if (messageLc.startsWith("!searchip"))
                searchByIp(name, message.substring(9));

            else if (messageLc.startsWith("!search -help"))
                searchByNameHelp(name);

            else if (messageLc.startsWith("!search")) {
                String commandTillName = messageLc.split(":")[0];
                String nameToSearch = commandTillName.substring(8);
                int limits[] = getLimits(messageLc);
                this.searchByName(name, nameToSearch, limits[0], limits[1]);

            }

            // !banaccesss
            else if (messageLc.startsWith("!banaccess"))
                cmd_BanAccess(name, message.substring(10).trim());
            else if (messageLc.startsWith("!shortcutkeys"))
                cmd_Shortcutkeys(name);
            else if (messageLc.equalsIgnoreCase("!arg"))
                cmd_Argument(name);
            else if (messageLc.startsWith("!silence") || messageLc.startsWith("!s") || messageLc.startsWith("!spec") || messageLc.startsWith("!sp ")
                    || messageLc.startsWith("!superspec"))
                //messageLc.startsWith("!kick") && opList.isModerator(name)) ||
                //(messageLc.startsWith("!k") && opList.isModerator(name))) {
                cmd_SilenceSpecKick(name, message);
            else if (messageLc.startsWith("!listban -help"))
                cmd_ListBanHelp(name);
            else if (messageLc.startsWith("!listban"))
                cmd_ListBan(name, message.substring(8).trim(), true, true);
            else if (messageLc.startsWith("!lb"))
                cmd_ListBan(name, message.substring(3).trim(), true, true);
            else if (messageLc.startsWith("!banlist"))
                cmd_ListBan(name, message.substring(8).trim(), true, true);
            else if (messageLc.startsWith("!changeban")) {
                cmd_ChangeBan(name, message.substring(10).trim());
                record(name, message);
            } else if (messageLc.startsWith("!cb")) {
                cmd_ChangeBan(name, message.substring(3).trim());
                record(name, message);
            }

            // !bancomment <#id> <comments>
            else if (messageLc.startsWith("!bancomment"))
                cmd_Bancomment(name, message.substring(11).trim());
            else if (messageLc.startsWith("!bc"))
                cmd_Bancomment(name, message.substring(3).trim());
            else if (messageLc.startsWith("!liftban")) {
                cmd_Liftban(name, message.substring(8).trim());
                record(name, message);
            }
            // !reload [Smod+]
            else if (messageLc.startsWith("!reload") && opList.isDeveloper(name))
                cmd_Reload(name);
            else if (messageLc.startsWith("!listactive") && opList.isDeveloper(name))
                cmd_ListActiveBanCs(name);
            else if (messageLc.startsWith("!forcedb") && opList.isDeveloper(name))
                doForceDBConnection(name);
        }

    }

    @Override
    public void handleEvent(InterProcessEvent event) {
        if (stop)
            return;
        if (!(event.getObject() instanceof IPCMessage))
            return;

        if (IPCBANC.equals(event.getChannel()) && (event.getSenderName().startsWith("TW-Guard") || event.getSenderName().startsWith("TW-Police"))) {
            IPCMessage ipc = (IPCMessage) event.getObject();
            String command = ipc.getMessage();
            String[] args = command.split(":");
            // On initilization of a pubbot, send the active bancs to that pubbot
            if (command.equals("BANC PUBBOT INIT"))
                sendIPCActiveBanCs(ipc.getSender());
            else if (command.startsWith("ELAPSED:"))
                doElapsed(args[1], args[2]);
            else if (command.startsWith("KICKED:"))// || command.startsWith("PROXY:"))
                m_botAction.sendChatMessage(2, command.substring(command.indexOf(":")+1));
            else if (command.startsWith(BanCType.SILENCE.toString())) {
                BanC banc = lookupActiveBanC(BanCType.SILENCE, args[1]);
                if (banc != null && banc.isNotification())
                    m_botAction.sendChatMessage("Player '" + banc.getPlayername() + "' has been (re)silenced by " + ipc.getSender() + ". (BanC #"
                            + banc.getId() + ")");
                else if (banc == null) {
                    banc = lookupActiveBanC(BanCType.SILENCE, args[2]);
                    if (banc.isNotification())
                        m_botAction.sendChatMessage("Player '" + args[1] + "' has been (re)silenced by " + ipc.getSender() + ".");
                }
            } else if (command.startsWith("REMOVE:" + BanCType.SILENCE.toString())) {
                BanC banc = lookupActiveBanC(BanCType.SILENCE, args[2]);
                if (banc != null && banc.isNotification())
                    m_botAction.sendChatMessage("Player '" + banc.getPlayername() + "' has been unsilenced.");
                else if (banc == null)
                    m_botAction.sendChatMessage("Player '" + args[2] + "' has been unsilenced.");
            } else if (command.startsWith(BanCType.SPEC.toString())) {
                BanC banc = lookupActiveBanC(BanCType.SPEC, args[1]);
                if (banc != null && banc.isNotification())
                    m_botAction.sendChatMessage("Player '" + banc.getPlayername() + "' has been (re)locked in spectator. (BanC #" + banc.getId()
                            + ")");
                else if (banc == null)
                    m_botAction.sendChatMessage("Player '" + args[1] + "' has been (re)locked in spectator.");
            } else if (command.startsWith(BanCType.SUPERSPEC.toString())) {
                //SUPERSPEC PLAYER
                //0123456789T
                BanC banc = lookupActiveBanC(BanCType.SUPERSPEC, args[1]);
                if (banc != null && banc.isNotification())
                    m_botAction.sendChatMessage("Player '" + banc.getPlayername() + "' has been (re)superlocked in spectator. (BanC #" + banc.getId()
                            + ")");
                else if (banc == null)
                    m_botAction.sendChatMessage("Player '" + args[1] + "' has been (re)superlocked in spectator.");
            } else if (command.startsWith("REMOVE:" + BanCType.SUPERSPEC.toString())) {
                //REMOVE SUPERSPEC
                BanC banc = lookupActiveBanC(BanCType.SUPERSPEC, args[2]);
                if (banc != null && banc.isNotification())
                    m_botAction.sendChatMessage("Player '" + banc.getPlayername() + "' has been unsuper-specced.");
                else if (banc == null)
                    m_botAction.sendChatMessage("Player '" + args[2] + "' has been unsuper-specced.");
            } else if (command.startsWith("REMOVE:" + BanCType.SPEC.toString())) {
                BanC banc = lookupActiveBanC(BanCType.SPEC, args[2]);
                if (banc != null && banc.isNotification())
                    m_botAction.sendChatMessage("Player '" + banc.getPlayername() + "' has had the speclock removed.");
                else if (banc == null)
                    m_botAction.sendChatMessage("Player '" + args[2] + "' has had the speclock removed.");
            }
        }
    }

    /**
     * Takes input from IPC and updates elapsed time for matching banc names.
     * 
     * @param name
     * @param mins
     */
    private void doElapsed(String name, String mins) {
        for (BanC b : activeBanCs) {
            if (b.getPlayername().equalsIgnoreCase(name)) {
                try {
                    int min = Integer.valueOf(mins);
                    b.updateElapsed(min);
                } catch (NumberFormatException e) {}
                ;
            }
        }
    }

    private void record(String name, String message) {
        try {
            Calendar c = Calendar.getInstance();
            String timestamp = c.get(Calendar.MONTH) + "/" + c.get(Calendar.DAY_OF_MONTH) + "/" + c.get(Calendar.YEAR) + " - ";

            BufferedReader reader = new BufferedReader(new FileReader("/home/bots/twcore/bin/logs/banc.log"));
            BufferedWriter writer = new BufferedWriter(new FileWriter("/home/bots/twcore/bin/logs/banc.log", true));

            writer.write("\r\n" + timestamp + name + " - " + message);

            reader.close();
            writer.close();

            Tools.printLog("Banc Record: Print File Successful!");
        }

        catch (Exception e) {
            m_botAction.sendChatMessage(2, "I cannot log this to the banc.log! + " + name + "-" + message);
            Tools.printStackTrace(e);
        }

    }

    private void cmd_Argument(String name) {
        String Argument[] = { " Arguments:", "             -player='<..>'     - Specifies player name",
                "             -d=#               - Specifies duration in minutes",
                "             -a=<...>           - Specifies access requirement, options; mod / smod / sysop",
                "             -ip=<#.#.#.#> -ir  - Specifies IP or remove IP (-ir) so banc is not matched by IP",
                "             -mid=#  -mr        - Specifies MID or remove MID (-mr) so banc is not matched by MID",
                "             -notif=<yes/no>    - Specifies wether a notification is sent on staff chat",
                "             -staffer='<..>'    - Specifies the name who issued the ban. [Only avail. on !listban]" };

        m_botAction.smartPrivateMessageSpam(name, Argument);

    }

    private void isOp(String name, String substring) {
        if (!opList.isSmod(name))
            return;
        restart_ops();
        BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("BancStaffers");
        if (ops.contains(substring.toLowerCase()))
            m_botAction.sendSmartPrivateMessage(name, "Staffer " + substring + " is a Banc Operator");
        else
            m_botAction.sendSmartPrivateMessage(name, "Sorry, " + substring + " is not a Banc Operator");
    }

    private void deleteBancOperator(String name, String message) {
        if (!opList.isSmod(name))
            return;
        restart_ops();
        BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("BancStaffers");

        int spot = ops.indexOf(message);
        if (spot == 0 && ops.length() == message.length()) {
            ops = "";
            m_botAction.sendSmartPrivateMessage(name, "Delete Op: " + message + " successful");
        } else if (spot == 0 && ops.length() > message.length()) {
            ops = ops.substring(message.length() + 1);
            m_botAction.sendSmartPrivateMessage(name, "Delete Op: " + message + " successful");
        } else if (spot > 0 && spot + message.length() < ops.length()) {
            ops = ops.substring(0, spot) + ops.substring(spot + message.length() + 1);
            m_botAction.sendSmartPrivateMessage(name, "Delete Op: " + message + " successful");
        } else if (spot > 0 && spot == ops.length() - message.length()) {
            ops = ops.substring(0, spot - 1);
            m_botAction.sendSmartPrivateMessage(name, "Delete Op: " + message + " successful");
        } else
            m_botAction.sendSmartPrivateMessage(name, "Delete Op: " + message + " successful");

        m_botSettings.put("BancStaffers", ops);
        m_botSettings.save();
        m_botAction.sendChatMessage(2, "Staffer " + name + " deleted operator " + message);
        is_revoked(name, message);
        restart_ops();
    }

    /**
     * !addop
     * 
     * @param name
     * @param substring
     */

    private void addBancOperator(String name, String substring) {
        //SMod+ only command.
        if (!opList.isSmod(name))
            return;

        BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("BancStaffers");

        if (ops.contains(substring)) {
            m_botAction.sendSmartPrivateMessage(name, substring + " is already an operator.");
            return;
        }
        if (ops.length() < 1)
            m_botSettings.put("BancStaffers", substring);
        else
            m_botSettings.put("BancStaffers", ops + "," + substring);
        m_botAction.sendSmartPrivateMessage(name, "Add Op: " + substring + " successful");
        m_botSettings.save();
        m_botAction.sendChatMessage(2, "Staffer " + name + " added operator " + substring);
        is_revoked(name, substring);
        restart_ops();
    }

    private void is_revoked(String name, String substring) {
        //if they operator is on revoked list, remove them from it
        BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("BancRevoked");
        int spot = ops.indexOf(substring);
        if (spot == 0 && ops.length() == substring.length())
            ops = "";
        else if (spot == 0 && ops.length() > substring.length())
            ops = ops.substring(substring.length() + 1);
        else if (spot > 0 && spot + substring.length() < ops.length())
            ops = ops.substring(0, spot) + ops.substring(spot + substring.length() + 1);
        else if (spot > 0 && spot == ops.length() - substring.length())
            ops = ops.substring(0, spot - 1);
        else
            m_botAction.sendSmartPrivateMessage(name, "This person was NOT revoked.");
        m_botSettings.put("BancRevoked", ops);
        m_botSettings.save();
        restart_ops();
        return;

    }

    public void restart_ops() {
        //Load the operators and add them
        try {
            BotSettings m_botSettings = m_botAction.getBotSettings();
            bancStaffers.clear();
            bancRevoked.clear();
            //
            String ops[] = m_botSettings.getString("BancStaffers").split(",");
            for (int i = 0; i < ops.length; i++)
                bancStaffers.put(ops[i].toLowerCase(), ops[i]);

            //
            String revoked[] = m_botSettings.getString("BancRevoked").split(",");
            for (int j = 0; j < revoked.length; j++)
                bancRevoked.put(revoked[j].toLowerCase(), revoked[j]);

            String op[] = m_botSettings.getString("BancOperators").split(",");
            for (int j = 0; j < op.length; j++)
                bancOp.put(op[j].toLowerCase(), op[j]);

        } catch (Exception e) {
            Tools.printStackTrace("Method Failed: ", e);
        }

    }

    /**
     * !listops
     * 
     * @param name
     */

    public void showBancPeople(String name) {
        //SMod Only command
        if (!opList.isSmod(name))
            return;
        restart_ops();
        m_botAction.sendSmartPrivateMessage(name, "List of staff that have Banc Access: ");
        Iterator<String> list = bancStaffers.keySet().iterator();
        if (!list.hasNext()) {
            m_botAction.sendSmartPrivateMessage(name, "Banc Error: No staff found. Contact botdev.");
            return;
        }

        OperatorList opList = m_botAction.getOperatorList();
        List<String> sysops = new LinkedList<String>();
        List<String> smods = new LinkedList<String>();
        List<String> mods = new LinkedList<String>();
        List<String> ers = new LinkedList<String>();
        List<String> other = new LinkedList<String>();

        for (; list.hasNext();) {
            String pName = (String) list.next();

            if (opList.isSysop(pName))
                sysops.add(pName);
            else if (opList.isSmodExact(pName))
                smods.add(pName);
            else if (opList.isModerator(pName))
                mods.add(pName);
            else if (opList.isER(pName))
                ers.add(pName);
            else
                other.add(pName);
        }

        List<String> revoked = new LinkedList<String>();
        Iterator<String> list1 = bancRevoked.values().iterator();
        while (list1.hasNext())
            revoked.add((String) list1.next());

        Collections.sort(sysops);
        Collections.sort(smods);
        Collections.sort(mods);
        Collections.sort(ers);
        Collections.sort(other);
        Collections.sort(revoked);

        /* for (int k = 0; list.hasNext();) {

            String pName = list.next();
            if (m_botAction.getOperatorList().isSysop(pName))
                bancs += pName + " (SysOp), ";
            else if (m_botAction.getOperatorList().isSmodExact(pName))
                bancs += pName + " (SMod), ";
            else
                bancs += pName + ", ";
            k++;
            if (k % 10 == 0 || !list.hasNext())
                if (bancs.length() > 2) {
                    m_botAction.sendSmartPrivateMessage(name, bancs.substring(0, bancs.length() - 2));
                    bancs = "";
                }
        }
        
        String bancs1 = "List of staffers that have no access: ";
        Iterator<String> list1 = bancRevoked.values().iterator();
        while (list1.hasNext())
            if (list1.hasNext())
                bancs1 += list1.next() + ", ";
            else
                bancs1 += list1.next();

        bancs1 = bancs1.substring(0, bancs1.length() - 2);
        m_botAction.sendSmartPrivateMessage(name, bancs1);
        */
        //!!!!!!!!!!!!!!!!!!!!!!!

        ArrayList<String> dispList = new ArrayList<String>();

        String temp = "";
        Iterator<String> o = null;

        for (int i = 0; i < 6; i++) {
            switch (i) {
                case 0:
                    o = sysops.iterator();
                    temp = "Sysops: ";
                    break;
                case 1:
                    o = smods.iterator();
                    temp = "Smods : ";
                    break;
                case 2:
                    o = mods.iterator();
                    temp = "Mods  : ";
                    break;
                case 3:
                    o = ers.iterator();
                    temp = "ERs   : ";
                    break;
                case 4:
                    o = other.iterator();
                    temp = "Others : ";
                    break;
                default:
                    o = revoked.iterator();
                    if (!o.hasNext())
                        continue;
                    temp = "List of staffers that have no access.";
                    dispList.add(temp);
                    temp = "Revoked: ";
                    break;
            }

            if (!o.hasNext())
                continue;

            int count = 0;
            while (o.hasNext()) {
                String n = o.next();
                if (count == 5) {
                    count++;
                    dispList.add(temp);
                    temp = "        ";
                    count = 0;
                }
                if (count > 0)
                    temp += ", ";
                temp += n;
                count++;
            }
            dispList.add(temp);
        }

        m_botAction.smartPrivateMessageSpam(name, dispList.toArray(new String[dispList.size()]));
    }

    /**
     * !removeop
     * 
     * @param name
     * @param message
     */

    private void removeBancStaffer(String name, String message) {
        if (!opList.isSmod(name))
            return;
        restart_ops();
        BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("BancStaffers");
        String ops1 = m_botSettings.getString("BancRevoked");

        if (ops1.contains(message)) {
            m_botAction.sendSmartPrivateMessage(name, "Operator is already revoked.");
            return;
        }

        int spot = ops.indexOf(message);
        if (spot == 0 && ops.length() == message.length()) {
            ops = "";
            m_botAction.sendSmartPrivateMessage(name, "Remove Op: " + message + " successful");
        } else if (spot == 0 && ops.length() > message.length()) {
            ops = ops.substring(message.length() + 1);
            m_botAction.sendSmartPrivateMessage(name, "Remove Op: " + message + " successful");
        } else if (spot > 0 && spot + message.length() < ops.length()) {
            ops = ops.substring(0, spot) + ops.substring(spot + message.length() + 1);
            m_botAction.sendSmartPrivateMessage(name, "Remove Op: " + message + " successful");
        } else if (spot > 0 && spot == ops.length() - message.length()) {
            ops = ops.substring(0, spot - 1);
            m_botAction.sendSmartPrivateMessage(name, "Remove Op: " + message + " successful");
        } else {
            m_botAction.sendSmartPrivateMessage(name, "Remove Op: " + message + " failed, operator doesn't exist");
            return;
        }
        m_botSettings.put("BancStaffers", ops);
        m_botSettings.save();
        m_botAction.sendChatMessage(2, "Staffer " + name + " removed operator " + message);
        remove_op(name, message);
        restart_ops();
    }

    /**
     * 
     * @param name
     * @param message
     */
    private void remove_op(String name, String message) {
        //If operator is operator list, remove them from it
        BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("BancRevoked");
        if (ops.length() < 1)
            m_botSettings.put("BancRevoked", message);
        else
            m_botSettings.put("BancRevoked", ops + "," + message);
        m_botAction.sendSmartPrivateMessage(name, "Operator added to Revoke Power list");
        m_botSettings.save();
        restart_ops();
        return;
    }

    private void searchByLiftedBancs(String name) {
        cmd_ListBan(name, "-lifted", true, true);
    }

    /***
     * !search -help command explaining how to use it.
     * 
     * @author quiles
     */
    private void searchByNameHelp(String name) {
        // TODO Auto-generated method stub
        ArrayList<String> list = new ArrayList<String>();

        String helpSearch = "Hi, I'll explain you how to use !search feature.";
        list.add(helpSearch);

        helpSearch = "The main functionality is to search the whole player's history with this command.";
        list.add(helpSearch);

        helpSearch = "Try !search quiles:-1:-1 to search everything about quiles (All banCs and warnings - latests and expireds)";
        list.add(helpSearch);

        helpSearch = "But you can customizable it: Try !search quiles:5:5 (Latest 5 banCs and 5 warnings)";
        list.add(helpSearch);

        helpSearch = "And then if you just use !search quiles, it'll give you all banCs and just the active warnings.";
        list.add(helpSearch);

        helpSearch = "Simple like that, enjoy!";
        list.add(helpSearch);

        m_botAction.remotePrivateMessageSpam(name, list.toArray(new String[list.size()]));
    }

    /**
     * 
     * @author quiles/dexter Search feature Extract'd method here: into sendBancs and sendWarnings.
     * 
     * @see getLimits method
     * */
    private void searchByName(String stafferName, String name, int limitBanCs, int limitWarnings) {
        try {
            m_botAction.sendSmartPrivateMessage(stafferName, " ------ Latest bancs (last 2 weeks): ");
            sendBanCs(stafferName, name, limitBanCs);
            sendWarnings(stafferName, name, limitWarnings);
            if (m_botAction.getOperatorList().isSmod(stafferName) || bancOp.containsKey(stafferName.toLowerCase()) || isTWDOp(stafferName)) {
                sendAltNicks(stafferName, name, limitBanCs, limitWarnings);
                if (limitBanCs == 0 && limitWarnings == 0)
                    m_botAction.sendSmartPrivateMessage(stafferName, "You can see all the player's history too typing !search player:-1:-1");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public boolean isTWDOp(String name) {
        try {
            ResultSet result = m_botAction.SQLQuery(webdb, "SELECT DISTINCT tblUser.fcUserName FROM tblUser, tblUserRank"
                    + " WHERE tblUser.fcUserName = '" + Tools.addSlashesToString(name) + "'" + " AND tblUser.fnUserID = tblUserRank.fnUserID"
                    + " AND ( tblUserRank.fnRankID = 14 OR tblUserRank.fnRankID = 19 )");
            if (result != null && result.next()) {
                m_botAction.SQLClose(result);
                return true;
            } else {
                m_botAction.SQLClose(result);
                return false;
            }
        } catch (SQLException e) {
            Tools.printStackTrace(e);
            return false;
        }
    }

    private boolean sendBanCs(String stafferName, String name, int limit) throws SQLException {
        if (limit != -1)
            this.cmd_ListBan(stafferName, "-player='" + name + "'", false, false);
        else
            this.cmd_ListBan(stafferName, "-player='" + name + "'", false, true);
        /* List<String> list = new ArrayList<String>();
        
        String query;
        
        if(limit == -1)
            query = "SELECT * from tblBanc WHERE fcUsername = '"+name+"' ORDER BY fdCreated ASC";
        else
            query = "SELECT * from tblBanc WHERE fcUsername = '"+name+"' ORDER BY fdCreated ASC "+" LIMIT 0,"+limit;
            
        ResultSet rs = m_botAction.SQLQuery(botsDatabase, query);
        
        if(rs == null )
            m_botAction.sendSmartPrivateMessage(stafferName, "No banCs made on the player "+name);
        
        else{
            String result = "BanCs: ";
            
            while(rs.next()){
                result += Tools.formatString(rs.getString("fcUsername"), 10);
                result += Tools.formatString(rs.getString("fcType"), 10);
                
                String IP = rs.getString("fcIp");
                
                if(IP == null)
                    IP = "(UNKNOWN)";
                
                result += "IP: "+Tools.formatString(IP, 15);
                
                String MID = rs.getString("fcMID");
                if(MID == null)
                    MID = "(UNKNOWN)";
                
                result += "MID: "+Tools.formatString(MID, 10);
                
                int duration = rs.getInt("fnDuration");
                boolean isDay = duration >= 1440? true:false;
                
                if(isDay){
                    duration = (duration/60)/24;
                    result += Tools.formatString(" Duration: "+duration+" days", 19);
                    }
                else
                    result += Tools.formatString(" Duration: "+duration+" mins", 19);
                
                result += Tools.formatString(" by: " + rs.getString("fcStaffer"), 17);
                String comments = rs.getString("fcComment");
                
                if(comments == null)
                    comments = "No Comments";
                
                list.add(result);
                list.add(comments);
            }
            String strSpam[] = list.toArray(new String[list.size()]);
            m_botAction.remotePrivateMessageSpam(stafferName, strSpam);
            
            return true;
        }*/
        return true;
    }

    private boolean sendWarnings(String stafferName, String name, int limit) throws SQLException {

        String query;
        if (limit == 0 || limit == -1)
            query = "SELECT * FROM tblWarnings WHERE name = '" + Tools.addSlashesToString(name) + "' ORDER BY timeofwarning ASC";
        else
            query = "SELECT * FROM tblWarnings WHERE name = '" + Tools.addSlashesToString(name) + "' ORDER BY timeofwarning ASC LIMIT 0," + limit;

        ResultSet rs = m_botAction.SQLQuery(this.trenchDatabase, query);

        List<String> lastestWarnings = new ArrayList<String>();
        List<String> expiredWarnings;
        expiredWarnings = new ArrayList<String>();

        while (rs.next()) {

            SimpleDateFormat f = new SimpleDateFormat("dd-MM-yyyy");
            java.sql.Date pre = null;
            try {
                pre = new java.sql.Date(f.parse("15-11-2012").getTime());
            } catch (ParseException e) {
                Tools.printStackTrace(e);
            }
            String warningStr = rs.getString("warning");
            String stringDateExpired = "";
            String stringDateNotExpired = "";
            Date date = rs.getDate("timeofwarning");
            int expiredTime = Tools.TimeInMillis.WEEK * 2; //last month

            Date expireDate = new java.sql.Date(System.currentTimeMillis() - expiredTime);

            if (date.before(expireDate) || date.before(pre))
                stringDateExpired = new SimpleDateFormat("dd MMM yyyy").format(date);
            else
                stringDateNotExpired = new SimpleDateFormat("dd MMM yyyy").format(date);

            String warningSplitBecauseOfExt[];

            if (warningStr.contains("Ext: "))
                warningSplitBecauseOfExt = warningStr.split("Ext: ", 2);
            else
                warningSplitBecauseOfExt = warningStr.split(": ", 2);

            if ((date.before(expireDate) || date.before(pre)) && warningSplitBecauseOfExt.length == 2)
                expiredWarnings.add(stringDateExpired + " " + warningSplitBecauseOfExt[1]);
            else if (warningSplitBecauseOfExt.length == 2) //lastest warnings AND warnings done correctly in database
                lastestWarnings.add(stringDateNotExpired + " " + warningSplitBecauseOfExt[1]);

        }

        m_botAction.SQLClose(rs);

        if (lastestWarnings.size() > 0) {

            m_botAction.sendSmartPrivateMessage(stafferName, " ------ Latest warnings (last 2 weeks): ");
            m_botAction.remotePrivateMessageSpam(stafferName, lastestWarnings.toArray(new String[lastestWarnings.size()]));
        }

        if (limit == 0)
            m_botAction.sendSmartPrivateMessage(stafferName, "There are " + expiredWarnings.size()
                    + " expired warnings. Use !search <player>:[limits]:[limitWarning] to see");
        else if (expiredWarnings.size() > 0) {
            m_botAction.sendSmartPrivateMessage(stafferName, " ------ Expired warnings (more than 2 weeks): ");
            m_botAction.remotePrivateMessageSpam(stafferName, expiredWarnings.toArray(new String[lastestWarnings.size()]));
        }

        return true;
    }

    /**
     * Captures aliases for playerName during !search using provided limitBan and limitWarn
     * 
     * @param stafferName
     *            Name of staff to respond to with data
     * @param playerName
     *            Name of the player being !search 'ed
     * @param limitBan
     *            Date limit for
     * @param limitWarn
     */
    private void sendAltNicks(String stafferName, String playerName, int limitBan, int limitWarn) {

        try {
            String ipResults = getSubQueryResultString("SELECT DISTINCT(fnIP) "
                    + "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " + "WHERE fcUserName = '"
                    + Tools.addSlashesToString(playerName) + "'", "fnIP");

            String midResults = getSubQueryResultString("SELECT DISTINCT(fnMachineId) "
                    + "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " + "WHERE fcUserName = '"
                    + Tools.addSlashesToString(playerName) + "'", "fnMachineId");

            String queryString = "SELECT * " + "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " + "WHERE fnIP IN "
                    + ipResults + " " + "AND fnMachineID IN " + midResults + " ORDER BY fcUserName";

            if (ipResults != null || midResults != null) {
                ResultSet resultSet = m_botAction.SQLQuery(trenchDatabase, queryString);
                List<String> nicks = new LinkedList<String>();
                String curResult = null;
                //int numResults = 0;

                while (resultSet.next()) {
                    curResult = resultSet.getString("fcUserName");

                    if (!nicks.contains(curResult) && !playerName.toLowerCase().equals(curResult.toLowerCase()))
                        nicks.add(curResult);
                    //numResults++;
                }

                m_botAction.SQLClose(resultSet);

                int expiredTime = Tools.TimeInMillis.WEEK * 2; // last month
                Date expireDate = new java.sql.Date(System.currentTimeMillis() - expiredTime);

                Iterator<String> i = nicks.iterator();
                while (i.hasNext()) {
                    String s = i.next();

                    boolean hasWarning = false;

                    ResultSet w = m_botAction.SQLQuery(this.trenchDatabase, "SELECT * FROM tblWarnings WHERE name = '" + Tools.addSlashesToString(s)
                            + "' ORDER BY timeofwarning ASC");

                    while (w.next()) {
                        Date date = w.getDate("timeofwarning");

                        if (date.after(expireDate)) {
                            hasWarning = true;
                            break;
                        }
                    }

                    m_botAction.SQLClose(w);

                    boolean hasBanc = false;
                    ResultSet b = m_botAction.SQLQuery(this.botsDatabase, "SELECT * FROM tblBanc WHERE fcUsername = '" + Tools.addSlashesToString(s)
                            + "' ORDER BY fdCreated ASC");

                    while (b.next()) {
                        Date date = b.getDate("fdCreated");

                        if (date.after(expireDate)) {
                            hasBanc = true;
                            break;
                        }
                    }

                    m_botAction.SQLClose(b);

                    if (hasWarning) {
                        m_botAction.sendSmartPrivateMessage(stafferName, " ");
                        m_botAction.sendSmartPrivateMessage(stafferName, "Warnings under Alias: " + s);
                        sendWarnings(stafferName, s, limitWarn);
                    }

                    if (hasBanc) {
                        m_botAction.sendSmartPrivateMessage(stafferName, " ");
                        m_botAction.sendSmartPrivateMessage(stafferName, "BanCs under Alias: " + s);
                        sendBanCs(stafferName, s, limitBan);
                    }
                }

            }

        } catch (SQLException e) {
            throw new RuntimeException("SQL Error: " + e.getMessage(), e);
        }
    }

    private String getSubQueryResultString(String queryString, String columnName) throws SQLException {
        ResultSet resultSet = m_botAction.SQLQuery(trenchDatabase, queryString);
        StringBuffer subQueryResultString = new StringBuffer("(");

        if (resultSet == null)
            throw new RuntimeException("ERROR: Null result set returned; connection may be down.");
        if (!resultSet.next())
            return null;
        for (;;) {
            subQueryResultString.append(resultSet.getString(columnName));
            if (!resultSet.next())
                break;
            subQueryResultString.append(", ");
        }
        subQueryResultString.append(") ");
        m_botAction.SQLClose(resultSet);

        return subQueryResultString.toString();
    }

    private int[] getLimits(String commandSearch) {

        //vector to limits of banc and warning. [0] = #banc. [1] = #warnings
        int limits[] = { 0, 0 };

        if (commandSearch.contains(":")) {
            String stPieces[] = commandSearch.split(":");
            if (stPieces.length == 3) {
                //to limit #bancs and #warnings
                limits[0] = Integer.parseInt(stPieces[1]);
                limits[1] = Integer.parseInt(stPieces[2]);
            } else if (stPieces.length == 2)
                //to limits just #bancs and see all warnings
                limits[0] = Integer.parseInt(stPieces[1]);
        }
        return limits;
    }

    /**
     * Search ip feature - shortkut to !listban -ip= Changed the query in listban to find ips starting with substring. "x." - where like 'ipstr%'
     * */
    private void searchByIp(String stafferName, String ipString) {
        this.cmd_ListBan(stafferName, "-ip=" + ipString, true, true);
    }

    private void cmd_ListBanHelp(String name) {
        // TODO Auto-generated method stub
        //!listban -player='name'
        List<String> listBanHelp = new ArrayList<String>();
        String helpStr = "";

        helpStr = "    Hi, I'm your help guide. How to use !listban in the best way, so it can be useful?";
        listBanHelp.add(helpStr);

        helpStr = "    There are few arguments you can do and model your own !listban: ";
        listBanHelp.add(helpStr);

        helpStr = "    !listban -player='quiles'        -   to search all bancS of the playername quiles, for example.";
        listBanHelp.add(helpStr);

        helpStr = "    Don't forget the ''";
        listBanHelp.add(helpStr);

        helpStr = "    !listban -d=60                   -   to search lastest banCs with duration of 60.";
        listBanHelp.add(helpStr);

        helpStr = "    try !listban -d=30 too, 20, 15, etc!";
        listBanHelp.add(helpStr);

        helpStr = "    You can also combine those both above. Try !listban -player='quiles' -d=60";
        listBanHelp.add(helpStr);

        helpStr = "    !listban -ip=74.243.233.254      -   to search all bancs of the ip 74.243.233.254. ";
        listBanHelp.add(helpStr);

        helpStr = "    !listban -staffer='quiles'       -   to search all bancs done by the staffer quiles.";
        listBanHelp.add(helpStr);

        helpStr = "    Don't forget the ''";
        listBanHelp.add(helpStr);

        helpStr = "    You can combine all those arguments above into:";
        listBanHelp.add(helpStr);

        helpStr = "    Check out !listban -player='Mime' -staffer='Dexter' to see all bancs done on Mime by Dexter.";
        listBanHelp.add(helpStr);

        helpStr = "    Some examples of !listban combinations: ";
        listBanHelp.add(helpStr);

        helpStr = "    !listban -ip=x.x.x -player='playername' -d=mins -staffer='staffername'";
        listBanHelp.add(helpStr);

        String spamPM[] = listBanHelp.toArray(new String[listBanHelp.size()]);
        m_botAction.remotePrivateMessageSpam(name, spamPM);
    }

    /**
     * Handles the !help command
     * 
     * @param name
     *            player who issued the command
     * @param parameters
     *            any command parameters
     */
    private void cmd_Help(String name, String parameters) {

        m_botAction.smartPrivateMessageSpam(name, helpER);

        if (opList.isSmod(name))
            m_botAction.smartPrivateMessageSpam(name, helpSmod);

    }

    /**
     * Handles the !banaccess command
     * 
     * @param name
     *            player who issued the command
     * @param parameters
     *            any command parameters
     */
    private void cmd_BanAccess(String name, String parameters) {
        /*      Limitations on BanC by access level
                         
                                       ER      MOD     SMOD    SYSOP
                 Silence time/mins     10      60      240     none
                 Speclock time/mins    30      60      120     none
                 Auto-kick time/mins   n/a     30      60      none
        */

        m_botAction.sendSmartPrivateMessage(name, "Limitations on BanC by access level");
        m_botAction.sendSmartPrivateMessage(name, " ");
        m_botAction.sendSmartPrivateMessage(name, "                       ER     MOD     SMOD    SYSOP");

        for (int type = 0; type < BANCLIMITS.length; type++) {
            String line = "";
            switch (type) {
                case 0:
                    line += " Silence time/mins";
                    break;
                case 1:
                    line += " Speclock time/mins";
                    break;
                case 2:
                    line += " SuperSpec time/mins";
                    break;
                //case 2: line += " Auto-kick time/mins"; break; 
            }
            line = Tools.formatString(line, 23);

            for (int level = 0; level < BANCLIMITS[0].length; level++) {
                String limit = "";
                if (BANCLIMITS[type][level] == null)
                    limit += "n/a";
                else if (BANCLIMITS[type][level].intValue() == 0)
                    limit += "none";
                else
                    limit += String.valueOf(BANCLIMITS[type][level]);
                if (level < (BANCLIMITS[0].length - 1))
                    limit = Tools.formatString(limit, 8);
                line += limit;
            }

            m_botAction.sendSmartPrivateMessage(name, line);
        }
    }

    /**
     * Handles the !shortcutkeys command
     * 
     * @param name
     *            player who issued the command
     */
    private void cmd_Shortcutkeys(String name) {
        m_botAction.smartPrivateMessageSpam(name, shortcutKeys);
    }

    /**
     * Handles the !silence, !spec and !kick command
     * 
     * @param name
     *            player who issed the command
     * @param message
     *            full message that the player sent
     */
    private void cmd_SilenceSpecKick(String name, String message) {
        String timeStr = "10";
        String parameters = "";
        BanCType bancType = BanCType.SILENCE;
        String bancName = "";
        String messageLc = message.toLowerCase();

        if (messageLc.startsWith("!silence")) {
            parameters = message.substring(8).trim();
            bancType = BanCType.SILENCE;
            bancName = "auto-silence";
        } else if (messageLc.startsWith("!s ")) {
            parameters = message.substring(2).trim();
            bancType = BanCType.SILENCE;
            bancName = "auto-silence";

        } else if (messageLc.startsWith("!spec")) {
            parameters = message.substring(5).trim();
            bancType = BanCType.SPEC;
            bancName = "auto-speclock";
        } else if (messageLc.startsWith("!sp ")) {
            parameters = message.substring(3).trim();
            bancType = BanCType.SPEC;
            bancName = "auto-speclock";

        } else if (messageLc.startsWith("!superspec")) {
            //!superspec
            //0123456789T
            parameters = message.substring(10).trim();
            bancType = BanCType.SUPERSPEC;
            bancName = "auto-superspec";

            //} else if(messageLc.startsWith("!kick")) {
            //parameters = message.substring(5).trim();
            //bancType = BanCType.KICK;
            //bancName = "auto-kick";
            //} else if(messageLc.startsWith("!k ")) {
            //parameters = message.substring(2).trim();
            //bancType = BanCType.KICK;
            //bancName = "auto-kick";

        }
        String comment = null;
        
        if (parameters.length() > 2 && parameters.contains(":")) {
            timeStr = parameters.split(":")[1];
            if (parameters.split(":").length == 3)
                comment = parameters.split(":")[2];
            parameters = parameters.split(":")[0];
        } else {
            m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify <playername>:<time/mins> or PM !help for more information.");
            return;
        }
        if (timeStr.length() > 6)
            timeStr = timeStr.substring(0, 5);

        final String target = parameters;
        int time;
        int timeToTell = 0;
        if (timeStr.contains("d")) {
            String justTime = timeStr.substring(0, timeStr.indexOf("d"));
            try { 
                timeToTell = Integer.parseInt(justTime);
                time = Integer.parseInt(justTime) * 1440;            
            } catch (NumberFormatException e) {
                m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify <playername>:<time/mins> or PM !help for more information.");
                return;
            }
        } else if (timeStr.contains("h")) {
            String justTime = timeStr.substring(0, timeStr.indexOf("h"));            
            try { 
                timeToTell = Integer.parseInt(justTime);
                time = Integer.parseInt(justTime) * 60;            
            } catch (NumberFormatException e) {
                m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify <playername>:<time/mins> or PM !help for more information.");
                return;
            }            
        } else {
            try { 
                time = Integer.parseInt(timeStr);
            } catch (NumberFormatException e) {
                m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify <playername>:<time/mins> or PM !help for more information.");
                return;
            }
		}

        if (time > BANC_MAX_DURATION) {
            m_botAction.sendSmartPrivateMessage(name, "The maximum amount of minutes for a BanC is " + BANC_MAX_DURATION
                    + " minutes (365 days). Duration changed to this maximum.");
            time = BANC_MAX_DURATION;
            timeToTell = (BANC_MAX_DURATION / 24) / 60;
        }

        // Check target
        // Already banced?
        if (isBanCed(target, bancType)) {
            m_botAction.sendSmartPrivateMessage(name, "Player '" + target + "' is already banced. Check !listban.");
            return;
        } else if (m_botAction.getOperatorList().isBotExact(target)) {
            m_botAction.sendSmartPrivateMessage(name, "You can't place a BanC on '" + target + "' as it is a bot.");
            return;
        } else
        // staff member?
        if (m_botAction.getOperatorList().isBot(target)) {
            m_botAction.sendSmartPrivateMessage(name, "Player '" + target + "' is staff, staff can't be banced.");
            return;
        }

        // limit != 0   &   limit < time    >> change
        // limit != 0   &   time == 0       >> change
        if (getBanCAccessDurationLimit(bancType, opList.getAccessLevel(name)) != 0
                && (getBanCAccessDurationLimit(bancType, opList.getAccessLevel(name)) < time || time == 0)) {
            time = getBanCAccessDurationLimit(bancType, opList.getAccessLevel(name));
            m_botAction.sendSmartPrivateMessage(name, "You are not allowed to issue an " + bancName + " of that duration.");
            m_botAction.sendSmartPrivateMessage(name, "The duration has been changed to the maximum duration of your access level: " + time
                    + " mins.");
            timeToTell = 7;
        }

        BanC banc;
        banc = new BanC(bancType, target, time);
        banc.staffer = name;
        dbLookupIPMID(banc);
        dbAddBan(banc);
        activeBanCs.add(banc);
        if (time >= 24 * 60 * 7 && timeToTell > 0) {

            m_botAction.sendChatMessage(name + " initiated an " + bancName + " on '" + target + "' for " + timeToTell + " days(" + time + " mins).");
            m_botAction.sendSmartPrivateMessage(name, "BanC #" + banc.id + ": " + bancName + " on '" + target + "' for " + timeToTell + " days("
                    + time + " mins) initiated.");
        } else if (time > 0) {
            m_botAction.sendChatMessage(name + " initiated an " + bancName + " on '" + target + "' for " + time + " minutes.");
            m_botAction.sendSmartPrivateMessage(name, "BanC #" + banc.id + ": " + bancName + " on '" + target + "' for " + time
                    + " minutes initiated.");
        } else {
            m_botAction.sendChatMessage(name + " initiated an infinite/permanent " + bancName + " on '" + target + "'.");
            m_botAction.sendSmartPrivateMessage(name, "BanC #" + banc.id + ": " + bancName + " on '" + target
                    + "' for infinite amount of time initiated.");
        }
        m_botAction.sendSmartPrivateMessage(name, "Please do not forget to add comments to your BanC with !bancomment <#id> <comments>.");
        m_botAction.ipcTransmit(IPCBANC, new IPCEvent(banc.getPlayername() + ":" + (banc.getIP() != null ? banc.getIP() : " ") + ":"
                + (banc.getMID() != null ? banc.getMID() : " ") + ":" + banc.getDuration() + ":" + banc.getType().toString() + ":"
                + banc.getElapsed(), 0, -1));

        if (comment != null)
            cmd_Bancomment(name, "#" + banc.getId() + " " + comment);
    }

    /**
     * Handles the !listban command
     * 
     * @param name
     *            player who issued the command
     * @param parameters
     *            any command parameters
     */
    private void cmd_ListBan(String name, String parameters, boolean showLBHelp, boolean twoWeeks) {
        int viewcount = 10;
        parameters = parameters.toLowerCase();
        String sqlWhere = "";
        boolean showLifted = false;
        /*
            !listban [arg] [#id] [count]   - Shows last 10/[count] BanCs or info about BanC with <id>. Arguments see below.
            !listban <player>:[#]          - Shows the last [#]/10 BanCs applied on <player>
            Arguments:
                        -player='<..>'     - Specifies player name
                        -d=#               - Specifies duration in minutes
                        -a=<...>           - Specifies access requirement, options; mod / smod / sysop
                        -ip=<#.#.#.#> -ir  - Specifies IP or remove IP (-ir) so banc is not matched by IP
                        -mid=#  -mr        - Specifies MID or remove MID (-mr) so banc is not matched by MID
                        -notif=<yes/no>    - Specifies wether a notification is sent on staff chat
        */

        if (parameters.length() > 0) {
            // ?listban
            // #19861 by PUNK rock  2009-09-26 18:14 days:1   ~98.194.169.186:8    AmoresPerros

            // ?listban #..
            // #19861 access:4 by PUNK rock 2009-09-26 17:14 days:1 ~98.194.169.186:8 AmoresPerros
            // #19861 access:4 by PUNK rock 2009-09-26 18:14 days:1 ~98.194.169.186:8  ~mid:462587662* AmoresPerros

            boolean playerArgument = false, stafferArgument = false;

            for (String argument : parameters.split(" "))
                if (!playerArgument && !stafferArgument) {
                    // [#id]
                    if (argument.startsWith("#") && Tools.isAllDigits(argument.substring(1))) {
                        if (!sqlWhere.isEmpty())
                            sqlWhere += " AND ";
                        sqlWhere += "fnID=" + argument.substring(1);

                    } else
                    // [count]
                    if (Tools.isAllDigits(argument)) {
                        viewcount = Integer.parseInt(argument);
                        if (viewcount < 1)
                            viewcount = 1;
                        if (viewcount > 100)
                            viewcount = 100;

                    } else
                    // -player='<..>'
                    if (argument.startsWith("-player='")) {//argument.startsWith("-player=")) {
                        String playerString = argument.substring(9);

                        if (!sqlWhere.isEmpty())
                            sqlWhere += " AND ";
                        //sqlWhere += "fcUsername='"+playerString+"'";
                        if (playerString.endsWith("'"))
                            sqlWhere += "fcUsername='" + playerString.replace("'", "") + "'";
                        else {
                            sqlWhere += "fcUsername='" + Tools.addSlashes(playerString);
                            playerArgument = true;
                        }

                    } else
                    // -d=#
                    if (argument.startsWith("-d=") && Tools.isAllDigits(argument.substring(3))) {
                        if (!sqlWhere.isEmpty())
                            sqlWhere += " AND ";
                        sqlWhere += "fnDuration=" + argument.substring(3);

                    } else
                    // -a=<...>
                    if (argument.startsWith("-a=")) {
                        String accessString = argument.substring(3);

                        if (accessString.trim().length() == 0)
                            continue;

                        if (!sqlWhere.isEmpty())
                            sqlWhere += " AND ";

                        sqlWhere += "fcMinAccess='" + accessString.toUpperCase() + "'";

                    } else
                    // -ip=<#.#.#.#>
                    if (argument.startsWith("-ip=")) {
                        String ipString = argument.substring(4);

                        if (ipString.trim().length() == 0 || Tools.isAllDigits(ipString.replace(".", "")) == false)
                            continue;

                        if (!sqlWhere.isEmpty())
                            sqlWhere += " AND ";

                        sqlWhere += "fcIP LIKE '" + Tools.addSlashes(ipString) + "%'";

                    } else
                    // -mid=#
                    if (argument.startsWith("-mid=") && Tools.isAllDigits(argument.substring(5))) {
                        if (!sqlWhere.isEmpty())
                            sqlWhere += " AND ";
                        sqlWhere += "fcMID=" + argument.substring(5);

                    } else
                    // -notif=
                    if (argument.startsWith("-notif=")
                            && (argument.substring(7).equalsIgnoreCase("yes") || argument.substring(7).equalsIgnoreCase("no"))) {
                        if (!sqlWhere.isEmpty())
                            sqlWhere += " AND ";
                        sqlWhere += "fbNotification=" + (argument.substring(7).equalsIgnoreCase("yes") ? "1" : "0");

                    } else
                    // -staffer='<..>'
                    if (argument.startsWith("-staffer='")) {//argument.startsWith("-staffer=")) {
                        String stafferString = argument.substring(10);
                        if (!sqlWhere.isEmpty())
                            sqlWhere += " AND ";
                        //sqlWhere += "fcStaffer='"+stafferString+"'";

                        if (stafferString.endsWith("'"))
                            sqlWhere += "fcStaffer='" + stafferString.replace("'", "") + "'";
                        else {
                            sqlWhere += "fcStaffer='" + Tools.addSlashes(stafferString);
                            stafferArgument = true;
                        }
                    } else if (argument.startsWith("-lifted")) {
                        if (!sqlWhere.isEmpty())
                            sqlWhere += " AND ";
                        sqlWhere += "fbLifted=1";
                        showLifted = true;
                    }
                } else if (argument.endsWith("'")) {
                    playerArgument = false;
                    stafferArgument = false;
                    sqlWhere += " " + argument.replace("'", "") + "'";

                } else
                    sqlWhere += " " + Tools.addSlashes(argument);

            if (playerArgument || stafferArgument)
                sqlWhere += "'";
        }

        String sqlQuery;

        try {

            if (!showLifted) {
                if (!sqlWhere.isEmpty())
                    sqlWhere += " AND ";
                sqlWhere += "fbLifted=0";
            }

            if (sqlWhere.contains("fnID")) {
                sqlQuery = "SELECT (fnElapsed < fnDuration OR fnDuration = 0) AS active, fnID, fcType, fcUsername, fcIP, fcMID, fcMinAccess, fnDuration, fnElapsed, fcStaffer, fcComment, fbNotification, fdCreated, fbLifted FROM tblBanc WHERE "
                        + sqlWhere + " LIMIT 0,1";
                ResultSet rs = m_botAction.SQLQuery(botsDatabase, sqlQuery);

                if (rs.next()) {
                    String result = "";
                    result += (rs.getBoolean("active") ? "#" : "^");
                    result += rs.getString("fnID") + " ";
                    if (rs.getString("fcMinAccess") != null)
                        result += "access:" + rs.getString("fcMinAccess") + " ";
                    result += "by " + Tools.formatString(rs.getString("fcStaffer"), 10) + " ";
                    result += datetimeFormat.format(rs.getTimestamp("fdCreated")) + "  ";
                    result += Tools.formatString(rs.getString("fcType"), 7) + "  ";
                    result += "mins:" + Tools.formatString(rs.getString("fnDuration"), 4) + Tools.formatString("(" + rs.getInt("fnElapsed") + ")", 6)
                            + " ";
                    result += rs.getString("fcUsername");

                    m_botAction.sendSmartPrivateMessage(name, result);

                    if (m_botAction.getOperatorList().isModerator(name)) {
                        String IP = rs.getString("fcIP");
                        if (IP == null)
                            IP = "(UNKNOWN)";
                        result = " IP: " + Tools.formatString(IP, 15) + "   ";
                    } else
                        result = " ";
                    if (m_botAction.getOperatorList().isSmod(name)) {
                        String MID = rs.getString("fcMID");
                        if (MID == null)
                            MID = "(UNKNOWN)";
                        result += "MID: " + Tools.formatString(MID, 10) + "   ";
                    } else
                        result += " ";
                    result += "Notification: " + (rs.getBoolean("fbNotification") ? "enabled" : "disabled");
                    m_botAction.sendSmartPrivateMessage(name, result);

                    String comments = rs.getString("fcComment");
                    if (comments != null)
                        m_botAction.sendSmartPrivateMessage(name, " " + comments);
                    else
                        m_botAction.sendSmartPrivateMessage(name, " (no BanC comments)");
                } else
                    m_botAction.sendSmartPrivateMessage(name, "No BanC with that ID found.");

                rs.close();

            } else {
                if (sqlWhere.length() > 0) {
                    if (!twoWeeks)
                        sqlWhere = "WHERE (fdExpired IS NULL OR (fdExpired > DATE_SUB(NOW(), INTERVAL 2 WEEK))) AND " + sqlWhere;
                    else
                        sqlWhere = "WHERE " + sqlWhere;
                } else if (!twoWeeks)
                    sqlWhere = "WHERE (fdExpired IS NULL OR (fdExpired > DATE_SUB(NOW(), INTERVAL 2 WEEK)))";

                sqlQuery = "SELECT (fnElapsed < fnDuration OR fnDuration = 0) AS active, fnID, fcType, fcUsername, fcIP, fcMID, fcMinAccess, fnDuration, fnElapsed, fcStaffer, fdCreated, fdExpired, fbLifted FROM tblBanc "
                        + sqlWhere + " ORDER BY fnID DESC LIMIT 0," + viewcount;

                ResultSet rs = m_botAction.SQLQuery(botsDatabase, sqlQuery);

                if (rs != null) {
                    rs.afterLast();
                    if (rs.previous()) {
                        do {

                            String result = "";
                            result += (rs.getBoolean("active") ? "#" : "^");
                            result += Tools.formatString(rs.getString("fnID"), 4) + " ";
                            result += "by " + Tools.formatString(rs.getString("fcStaffer"), 10) + " ";
                            result += datetimeFormat.format(rs.getTimestamp("fdCreated")) + " ";
                            result += Tools.formatString(rs.getString("fcType"), 7) + " ";
                            int time = Integer.parseInt(rs.getString("fnDuration"));
                            if (time >= 24 * 60) {
                                int days = (time / 24) / 60;
                                String daysNumber = days + "";
                                double elap = rs.getInt("fnElapsed") / 24 / 60 * 10;
                                elap = (elap - elap % 1) / 10;
                                String e = "" + elap;
                                if (elap % 1 == 0)
                                    e = "" + (int) elap;
                                result += " days: " + Tools.formatString(daysNumber, 4);
                                result += Tools.formatString("(" + e + ")", 6) + " ";
                            } else {
                                result += " mins:" + Tools.formatString(rs.getString("fnDuration"), 4) + " ";
                                result += Tools.formatString("(" + rs.getInt("fnElapsed") + ")", 6) + " ";
                            }
                            if (m_botAction.getOperatorList().isModerator(name))
                                result += " " + Tools.formatString(rs.getString("fcIP"), 15) + "  ";
                            result += rs.getString("fcUsername");

                            m_botAction.sendSmartPrivateMessage(name, result);
                        } while (rs.previous());
                        if (showLBHelp)
                            m_botAction.sendSmartPrivateMessage(name, "!listban -help for more info");

                    } else
                        // Empty resultset - nothing found
                        m_botAction.sendSmartPrivateMessage(name, "No BanCs matching given arguments found.");
                } else
                    // Empty resultset - nothing found
                    m_botAction.sendSmartPrivateMessage(name, "No BanCs matching given arguments found.");
            }

        } catch (SQLException sqle) {
            m_botAction.sendSmartPrivateMessage(name, "A problem occured while retrieving ban listing from the database. Please try again or report the problem.");
            Tools.printStackTrace("SQLException while querying the database for BanCs", sqle);
        }
    }

    /**
     * Handles the !changeban command
     * 
     * @param name
     *            player who issued the command
     * @param parameters
     *            any command parameters
     */
    private void cmd_ChangeBan(String name, String parameters) {
        String sqlSet = "";
        int banID;
        OperatorList opList = m_botAction.getOperatorList();
        BanC banChange = new BanC();
        banChange.staffer = name;

        /*
            !changeban <#id> <arguments>   - Changes banc with <id>. Arguments see below.
             Arguments:
                         -player=<..>       - Specifies player name
                         -d=#               - Specifies duration in minutes
                         -a=<...>           - Specifies access requirement, options; mod / smod / sysop
                         -ip=<#.#.#.#> -ir  - Specifies IP or remove IP (-ir) so banc is not matched by IP
                         -mid=#  -mr        - Specifies MID or remove MID (-mr) so banc is not matched by MID
                         -notif=<yes/no>    - Specifies wether a notification is sent on staff chat
        */

        if (parameters.length() > 0 && parameters.startsWith("#") && parameters.contains(" ")
                && Tools.isAllDigits(parameters.substring(1).split(" ")[0])) {
            boolean playerArgument = false;

            // Extract given #id
            String id = parameters.substring(1, parameters.indexOf(" ", 1));
            if (Tools.isAllDigits(id))
                banID = Integer.parseInt(id);
            else {
                m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify #id and arguments. For more information, PM !help.");
                return;
            }

            for (String argument : parameters.split(" "))
                if (!playerArgument) {
                    // -player='<..>'
                    if (argument.startsWith("-player='")) {
                        String playerString = argument.substring(9);
                        playerArgument = true;

                        if (!sqlSet.isEmpty())
                            sqlSet += ", ";

                        if (playerString.endsWith("'")) {
                            sqlSet += "fcUsername='" + playerString.replace("'", "") + "'";
                            banChange.setPlayername(playerString.replace("'", ""));
                        } else {
                            sqlSet += "fcUsername='" + Tools.addSlashes(playerString);
                            banChange.setPlayername(playerString);
                        }

                    } else
                    // -d=#
                    if (argument.startsWith("-d=") && argument.length() > 3) {
                        String timeStr = argument.substring(3);
                        argument = argument.substring(0 , 3);                        
                        int time;
                        int timeToTell = 0;
                        
                        if (timeStr.contains("d")) {
                            String justTime = timeStr.substring(0, timeStr.indexOf("d"));            
                            try { 
                                timeToTell = Integer.parseInt(justTime);
                                time = Integer.parseInt(justTime) * 1440;            
                            } catch (NumberFormatException e) {
                                m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please format the time as  -d=<duration>");
                                return;
                            }
                        } else if (timeStr.contains("h")) {
                            String justTime = timeStr.substring(0, timeStr.indexOf("h"));            
                            try { 
                                timeToTell = Integer.parseInt(justTime);
                                time = Integer.parseInt(justTime) * 60;            
                            } catch (NumberFormatException e) {
                                m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please format the time as  -d=<duration>");
                                return;
                            }            
                        } else {
                            try { 
                                time = Integer.parseInt(timeStr);
                            } catch (NumberFormatException e) {
                                m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please format the time as  -d=<duration>");
                                return;
                            }
                        }
                        
                        
                        if (!sqlSet.isEmpty())
                            sqlSet += ", ";
                        sqlSet += "fnDuration=" + time;
                        banChange.setDuration(time);

                    } else
                    // -a=<...>
                    if (argument.startsWith("-a=")) {
                        String accessRequirement = argument.substring(3);
                        if ((MINACCESS_ER.equalsIgnoreCase(accessRequirement) && !opList.isER(name))
                                || (MINACCESS_MOD.equalsIgnoreCase(accessRequirement) && !opList.isModerator(name))
                                ||
                                //(MINACCESS_BANCSTAFFER.equalsIgnoreCase(accessRequirement) && !bancStaffers.containsKey(name.toLowerCase()))  ||
                                (MINACCESS_SMOD.equalsIgnoreCase(accessRequirement) && !opList.isSmod(name))
                                || (MINACCESS_SYSOP.equalsIgnoreCase(accessRequirement) && !opList.isSysop(name)))
                            m_botAction.sendSmartPrivateMessage(name, "You can't set the access requirement higher then your own access. (Argument ignored.)");
                        else if ( //MINACCESS_BANCSTAFFER.equalsIgnoreCase(accessRequirement)  ||
                        MINACCESS_ER.equalsIgnoreCase(accessRequirement) || MINACCESS_MOD.equalsIgnoreCase(accessRequirement)
                                || MINACCESS_SMOD.equalsIgnoreCase(accessRequirement) || MINACCESS_SYSOP.equalsIgnoreCase(accessRequirement)) {
                            if (!sqlSet.isEmpty())
                                sqlSet += ", ";

                            sqlSet += "fcMinAccess='" + accessRequirement.toUpperCase() + "'";
                        }

                    } else
                    // -ip=<#.#.#.#>
                    if (argument.startsWith("-ip=")) {
                        if (!sqlSet.isEmpty())
                            sqlSet += ", ";
                        sqlSet += "fcIP='" + argument.substring(4) + "'";
                        banChange.setIP(argument.substring(4));

                    } else
                    // -ir
                    if (argument.startsWith("-ir")) {
                        if (!sqlSet.isEmpty())
                            sqlSet += ", ";
                        sqlSet += "fcIP=NULL";
                        banChange.setIP("NULL");
                    } else
                    /* 18-Oct-2013: Disabled by request of Left_Eye.
                    //staffer
                    if (argument.startsWith("-staffname=")) {
                        if (!sqlSet.isEmpty())
                            sqlSet += ", ";
                        sqlSet += "fcStaffer='" + argument.substring(11) + "'";
                        banChange.setStaffer(argument.substring(11));

                    } else
                    */
                    // -mid=#
                    if (argument.startsWith("-mid=") && Tools.isAllDigits(argument.substring(5))) {
                        if (!sqlSet.isEmpty())
                            sqlSet += ", ";
                        sqlSet += "fcMID=" + argument.substring(5);
                        banChange.setMID(argument.substring(5));

                    } else
                    // -mr
                    if (argument.startsWith("-mr")) {
                        if (!sqlSet.isEmpty())
                            sqlSet += ", ";
                        sqlSet += "fcMID=NULL";
                        banChange.setMID("NULL");

                    } else
                    // -notif=
                    if (argument.startsWith("-notif=")) {
                        String notification = argument.substring(7);
                        if (notification.equalsIgnoreCase("yes")) {
                            if (!sqlSet.isEmpty())
                                sqlSet += ", ";

                            sqlSet += "fbNotification=1";
                            banChange.setNotification(true);
                        } else if (notification.equalsIgnoreCase("no")) {
                            if (!sqlSet.isEmpty())
                                sqlSet += ", ";

                            sqlSet += "fbNotification=0";
                            banChange.setNotification(false);
                        } else
                            m_botAction.sendSmartPrivateMessage(name, "Syntax error on the -notif argument. (Argument ignored.)");
                    }
                } else if (argument.endsWith("'")) {
                    sqlSet += " " + argument.replace("'", "") + "'";
                    banChange.setPlayername(banChange.getPlayername() + " " + argument.replace("'", ""));
                    playerArgument = false;
                } else {
                    sqlSet += " " + Tools.addSlashes(argument);
                    banChange.setPlayername(banChange.getPlayername() + " " + argument);
                }
        } else {
            // No parameters
            m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify #id and arguments. For more information, PM !help.");
            return;
        }

        if (sqlSet.isEmpty()) {
            // No arguments
            m_botAction.sendSmartPrivateMessage(name, "Syntax error (no arguments specified). Please specify #id and arguments. For more information, PM !help.");
            return;
        }

        // Retrieve ban with id and check access requirement
        // Modify ban

        String sqlUpdate;

        try {
            psCheckAccessReq.setInt(1, banID);
            ResultSet rsAccessReq = psCheckAccessReq.executeQuery();

            if (rsAccessReq.next()) {
                String accessReq = rsAccessReq.getString(1);
                if (//MINACCESS_BANCSTAFFER.equalsIgnoreCase(accessReq) && !bancStaffers.containsKey(name.toLowerCase())  ||
                (MINACCESS_ER.equals(accessReq) && !opList.isER(name)) || (MINACCESS_MOD.equals(accessReq) && !opList.isModerator(name))
                        || (MINACCESS_SMOD.equals(accessReq) && !opList.isSmod(name)) || (MINACCESS_SYSOP.equals(accessReq) && !opList.isSysop(name))) {
                    m_botAction.sendSmartPrivateMessage(name, "You don't have enough access to modify this BanC.");
                    return;
                }
            }

            sqlUpdate = "UPDATE tblBanc SET " + sqlSet + " WHERE fnID=" + banID;
            m_botAction.SQLQueryAndClose(botsDatabase, sqlUpdate);

            // Retrieve active banc if it exists and let expire if it by changes becomes expired
            synchronized (activeBanCs) {
                Iterator<BanC> iterator = activeBanCs.iterator(); // Must be in synchronized block
                while (iterator.hasNext()) {
                    BanC banc = iterator.next();

                    if (banc.getId() == banID) {
                        banc.applyChanges(banChange);
                        banc.calculateExpired();
                        if (banc.isExpired()) {
                            switch (banc.type) {
                                case SILENCE:
                                    m_botAction.sendChatMessage("Auto-silence BanC #" + banc.id + " (" + banc.playername
                                            + ") has expired. Duration: " + banc.getDuration() + " minute(s)." + " Authorized by "
                                            + banc.getStaffer());
                                    break;
                                case SPEC:
                                    m_botAction.sendChatMessage("Auto-speclock BanC #" + banc.id + " (" + banc.playername
                                            + ") has expired. Duration: " + banc.getDuration() + " minute(s)." + " Authorized by "
                                            + banc.getStaffer());
                                    break;
                                //case KICK :   m_botAction.sendChatMessage("Auto-kick BanC #"+banc.id+" ("+banc.playername+") has expired."); break;
                                case SUPERSPEC:
                                    m_botAction.sendChatMessage("Auto-superspeclock BanC #" + banc.id + " (" + banc.playername
                                            + ") has expired. Duration: " + banc.getDuration() + " minute(s)." + " Authorized by "
                                            + banc.getStaffer());
                                    break;
                            }
                            m_botAction.ipcSendMessage(IPCBANC, "REMOVE:" + banc.type.toString() + ":" + banc.playername, null, "banc");
                            iterator.remove();
                        }
                    }
                }
            }

            m_botAction.sendSmartPrivateMessage(name, "BanC #" + banID + " changed.");

        } catch (SQLException sqle) {
            m_botAction.sendSmartPrivateMessage(name, "A problem occured while modifying the ban in the database. Please try again or report the problem.");
            Tools.printStackTrace("SQLException while modifying the database", sqle);
        }
    }

    /**
     * Handles the !bancomment command
     * 
     * @param name
     *            player who issued the command
     * @param parameters
     *            any command parameters
     */
    private void cmd_Bancomment(String name, String message) {
        // !bancomment <#id> <comments>   - Adds comments to BanC with specified #id.

        int id = -1;
        String comments = null;

        if (message.length() == 0 || message.startsWith("#") == false || message.contains(" ") == false
                || Tools.isAllDigits(message.substring(1).split(" ")[0]) == false || message.split(" ")[1].isEmpty()) {
            m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify #id and comments. For more information, PM !help.");
            return;
        } else {
            id = Integer.parseInt(message.substring(1).split(" ")[0]);
            comments = message.substring(message.indexOf(" ") + 1);
        }

        // failsafe
        if (id == -1 || comments.isEmpty())
            return;

        // "UPDATE tblBanc SET fcComment = ? WHERE fnID = ?"
        try {
            psUpdateComment.setString(1, comments);
            psUpdateComment.setInt(2, id);
            psUpdateComment.executeUpdate();

            if (psUpdateComment.executeUpdate() == 1)
                m_botAction.sendSmartPrivateMessage(name, "BanC #" + id + " modified");
            else
                m_botAction.sendSmartPrivateMessage(name, "BanC #" + id + " doesn't exist.");

            // Apply the banc comment to the active banc
            BanC activeBanc = lookupActiveBanC(id);
            if (activeBanc != null)
                activeBanc.comment = comments;
        } catch (SQLException sqle) {
            m_botAction.sendSmartPrivateMessage(name, "A problem occured while modifying the ban in the database. Please try again or report the problem.");
            Tools.printStackTrace("SQLException while modifying the database", sqle);
        }

    }

    private void doForceDBConnection(String name) {
        try {

            this.psKeepAlive1.execute();
            this.psKeepAlive2.execute();

            if (!psKeepAlive1.isClosed() && !psKeepAlive2.isClosed()) {
                m_botAction.sendPrivateMessage(name, "Force-Connected to the database successfuly,");
                m_botAction.sendPrivateMessage(name, "now try to !lb, !bc and others banc commands to check");
            }

        } catch (SQLException e) {
            m_botAction.sendPrivateMessage(name, "I had a problem to force the DB Connection, try again in some minutes. StackTrace:" + " "
                    + e.toString());
            e.printStackTrace();
        }
    }

    private void cmd_Liftban(String name, String message) {
        // !liftban <#id>                 - Removes ban with #id.
        int id = -1;

        if (message.length() == 0 || !message.startsWith("#") || !Tools.isAllDigits(message.substring(1))) {
            m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify #id. For more information, PM !help.");
            return;
        } else
            id = Integer.parseInt(message.substring(1));

        // failsafe
        if (id == -1)
            return;

        try {
            psRemoveBanC.setInt(1, id);
            psRemoveBanC.executeUpdate();

            m_botAction.sendSmartPrivateMessage(name, "BanC #" + id + " removed");
            m_botAction.sendChatMessage("BanC #" + id + " has been lifted by " + name);

            // Make the banc expired so it's removed from the player if still active.
            BanC activeBanc = lookupActiveBanC(id);
            if (activeBanc != null) {
                m_botAction.ipcSendMessage(IPCBANC, "REMOVE:" + activeBanc.type.toString() + ":" + activeBanc.playername, null, "banc");
                activeBanCs.remove(activeBanc);
            }
        } catch (SQLException sqle) {
            m_botAction.sendSmartPrivateMessage(name, "A problem occured while deleting the banc from the database. Please try again or report the problem.");
            Tools.printStackTrace("SQLException while modifying the database", sqle);
        }
    }

    private void cmd_Reload(String name) {
        activeBanCs.clear();
        this.loadActiveBanCs();
        m_botAction.sendSmartPrivateMessage(name, "Bans reloaded from database.");
        this.sendIPCActiveBanCs(null);
    }

    private void cmd_ListActiveBanCs(String name) {
        for (BanC banc : activeBanCs)
            m_botAction.sendSmartPrivateMessage(name, "#" + banc.getId() + " " + banc.getType() + " " + banc.getDuration() + "mins on "
                    + banc.getPlayername());
    }

    /**
     * @return the maximum duration amount (in minutes) for the given access level
     */
    private Integer getBanCAccessDurationLimit(BanCType banCType, int accessLevel) {
        int level = 0;

        switch (accessLevel) {
            case OperatorList.ER_LEVEL:
                level = 0;
                break;
            case OperatorList.MODERATOR_LEVEL:
            case OperatorList.HIGHMOD_LEVEL:
            case OperatorList.DEV_LEVEL:
                level = 1;
                break;
            case OperatorList.SMOD_LEVEL:
                level = 2;
                break;
            case OperatorList.SYSOP_LEVEL:
            case OperatorList.OWNER_LEVEL:
                level = 3;
                break;
        }
        return BANCLIMITS[banCType.ordinal()][level];
    }

    /**
     * Loads active BanCs from the database
     */
    private void loadActiveBanCs() {
        try {
            ResultSet rs = psActiveBanCs.executeQuery();

            if (rs != null)
                while (rs.next()) {
                    BanC banc = new BanC();
                    banc.id = rs.getInt("fnID");
                    String banCType = rs.getString("fcType");

                    if (banCType.equals("S-SPEC"))
                        banc.type = BanCType.valueOf("SUPERSPEC");
                    else
                        banc.type = BanCType.valueOf(rs.getString("fcType"));
                    String playerName = rs.getString("fcUsername");
                    banc.addNickName(playerName);
                    banc.IP = rs.getString("fcIP");
                    banc.MID = rs.getString("fcMID");
                    banc.notification = rs.getBoolean("fbNotification");
                    banc.created = rs.getTimestamp("fdCreated");
                    banc.duration = rs.getInt("fnDuration");
                    banc.elapsed = rs.getInt("fnElapsed");
                    banc.staffer = rs.getString("fcStaffer");
                    activeBanCs.add(banc);
                }
        } catch (SQLException sqle) {
            Tools.printLog("SQLException occured while retrieving active BanCs: " + sqle.getMessage());
            m_botAction.sendChatMessage(2, "BANC WARNING: Problem occured while retrieving active BanCs: " + sqle.getMessage());
            Tools.printStackTrace(sqle);
        }
    }

    /**
     * Sends out all active BanCs trough IPC Messages to the pubbots so they are applied
     * 
     * @param receiver
     *            Receiving bot in case a certain pubbot needs to be initialized, else NULL for all pubbots
     */
    private void sendIPCActiveBanCs(String receiver) {
        int bot = -1;
        if (receiver != null && receiver.startsWith("TW-Guard"))
            bot = Integer.valueOf(receiver.substring(8));
        LinkedList<String> l = new LinkedList<String>();
        for (BanC b : activeBanCs)
            l.add(b.getPlayername() + ":" + (b.getIP() != null ? b.getIP() : " ") + ":" + (b.getMID() != null ? b.getMID() : " ") + ":"
                    + b.getDuration() + ":" + b.getType().toString() + ":" + b.getElapsed());
        m_botAction.ipcTransmit(IPCBANC, new IPCEvent(l.listIterator(), 0, bot));
        /*
        for (BanC b : activeBanCs) {
            IPCEvent ipc = new IPCEvent(b, 0, bot);
            m_botAction.ipcTransmit(IPCBANC, ipc);
        }
        for (BanC banc : activeBanCs)
            if (banc.getType() != BanCType.SILENCE)
                m_botAction.ipcSendMessage(IPCBANC, banc.type.toString() + " " + banc.duration + ":" + banc.playername, receiver, "banc");
        */
    }

    /**
     * Checks if the player already has a BanC of the given type
     * 
     * @param playername
     * @param bancType
     * @return
     */
    private boolean isBanCed(String playername, BanCType bancType) {
        for (BanC banc : activeBanCs)
            if (banc.type.equals(bancType) && banc.playername.equalsIgnoreCase(playername))
                return true;
        return false;
    }

    /**
     * Lookups a BanC from the list of active BanCs by the given id.
     * 
     * @param id
     * @return an active BanC matching the given id or NULL if not found
     */
    private BanC lookupActiveBanC(int id) {
        for (BanC banc : this.activeBanCs)
            if (banc.id == id)
                return banc;
        return null;
    }

    /**
     * Lookups a BanC from the list of active BanCs that matches the type and playername
     * 
     * @param bancType
     * @param name
     * @return an active BanC matching the given type and playername or NULL if not found
     */
    private BanC lookupActiveBanC(BanCType bancType, String name) {
        for (BanC banc : this.activeBanCs)
            if (banc.type == bancType && banc.playername.equalsIgnoreCase(name))
                return banc;
        return null;
    }

    /**
     * Saves the BanC to the database and stores the database ID in the BanC
     */
    private void dbAddBan(BanC banc) {

        try {
            // INSERT INTO tblBanc(fcType, fcUsername, fcIP, fcMID, fcMinAccess, fnDuration, fcStaffer, fdCreated) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
            //                       1         2         3     4         5           6           7          
            if (banc.type.name().equals("SUPERSPEC"))
                psAddBanC.setString(1, "S-SPEC");

            else
                psAddBanC.setString(1, banc.type.name());

            psAddBanC.setString(2, banc.playername);
            psAddBanC.setString(3, banc.IP);
            psAddBanC.setString(4, banc.MID);
            psAddBanC.setString(5, MINACCESS_ER);
            psAddBanC.setLong(6, banc.duration);
            psAddBanC.setString(7, banc.staffer);
            psAddBanC.execute();

            ResultSet rsKeys = psAddBanC.getGeneratedKeys();
            if (rsKeys.next())
                banc.id = rsKeys.getInt(1);
            rsKeys.close();

        } catch (SQLException sqle) {
            m_botAction.sendChatMessage(2, "BANC WARNING: Unable to save BanC (" + banc.type.name() + "," + banc.playername
                    + ") to database. Please try to reinstate the banc.");
            Tools.printStackTrace("SQLException encountered while saving BanC (" + banc.type.name() + "," + banc.playername + ")", sqle);
        }
    }

    private void dbLookupIPMID(BanC banc) {
        try {
            psLookupIPMID.setString(1, banc.playername);
            ResultSet rsLookup = psLookupIPMID.executeQuery();
            if (rsLookup.next()) {
                banc.IP = rsLookup.getString(1);
                banc.MID = rsLookup.getString(2);
            }
        } catch (SQLException sqle) {
            m_botAction.sendChatMessage(2, "BANC WARNING: Unable to lookup the IP / MID data for BanC (" + banc.type.name() + "," + banc.playername
                    + ") from the database. BanC aliassing disabled for this BanC.");
            Tools.printStackTrace("SQLException encountered while retrieving IP/MID details for BanC (" + banc.type.name() + "," + banc.playername
                    + ")", sqle);
        }
    }

    /**
     * Simply tries to send new elapsed times for any updated bancs.
     *
     * @author WingZero
     */
    private class UpdateElapsed extends TimerTask {

        public void run() {
            for (BanC b : activeBanCs)
                b.sendUpdate();
        }
    }

    /**
     * This TimerTask periodically runs over all the active BanCs in the activeBanCs arrayList and (1) removes already expired BanCs and (2) checks if
     * BanCs has expired. If a BanC has expired, a chat message is given and the BanC is sent to the pubbots to lift the silence/spec if necessary.
     * 
     * @author Maverick
     */
    public class CheckExpiredBanCs extends TimerTask {

        @Override
        public void run() {

            // Run trough all the active BanCs
            // Check for expired bancs
            // Notify of banc expired
            // remove BanC
            synchronized (activeBanCs) {
                Iterator<BanC> iterator = activeBanCs.iterator(); // Must be in synchronized block
                while (iterator.hasNext()) {
                    BanC banc = iterator.next();

                    banc.calculateExpired();
                    if (banc.isExpired()) {
                        switch (banc.type) {
                            case SILENCE:
                                m_botAction.sendChatMessage("Auto-silence BanC #" + banc.id + " (" + banc.playername + ") has expired.  Duration: "
                                        + banc.getDuration() + " minute(s)." + " Authorized by " + banc.getStaffer());
                                break;
                            case SPEC:
                                m_botAction.sendChatMessage("Auto-speclock BanC #" + banc.id + " (" + banc.playername + ") has expired. Duration: "
                                        + banc.getDuration() + " minute(s)." + " Authorized by " + banc.getStaffer());
                                break;
                            //case KICK :   m_botAction.sendChatMessage("Auto-kick BanC #"+banc.id+" ("+banc.playername+") has expired."); break;
                            case SUPERSPEC:
                                m_botAction.sendChatMessage("Auto-superspec BanC #" + banc.id + " (" + banc.playername + ") has expired. Duration: "
                                        + banc.getDuration() + " minute(s)." + " Authorized by " + banc.getStaffer());
                                break;
                        }
                        m_botAction.ipcSendMessage(IPCBANC, "REMOVE:" + banc.type.toString() + ":" + banc.playername, null, "banc");
                        iterator.remove();
                    }
                }
            }
        }
    }

    /**
     * This TimerTask executes psKeepAlive which just sends a query to the database to keep the connection alive
     */
    private class KeepAliveConnection extends TimerTask {
        @Override
        public void run() {
            try {
                psKeepAlive1.execute();
                psKeepAlive2.execute();
            } catch (SQLException sqle) {
                Tools.printStackTrace("SQLException encountered while executing queries to keep alive the database connection", sqle);
            }
        }
    }

    public class BanC {

        public BanC() {
        }

        public BanC(BanCType type, String username, int duration) {
            this.type = type;
            this.playername = username;
            this.duration = duration;
            created = new Date();
        }

        private int id;
        private BanCType type;
        private String playername;
        private String IP;
        private String MID;
        private Date created;
        /** Duration of the BanC in minutes */
        private long duration = -1;
        private int elapsed = 0;
        private Boolean notification = false;

        private String staffer;
        private String comment;

        private boolean updated = false;
        private boolean applied = false;
        private boolean expired = false;

        public void calculateExpired() {
            if (duration == 0)
                expired = false;
            else {
                expired = elapsed >= duration;
                /*
                Date now = new Date();
                Date expiration = new Date(created.getTime() + (duration * Tools.TimeInMillis.MINUTE));
                expired = now.equals(expiration) || now.after(expiration);
                */
            }
        }

        public void addNickName(String name) {
            this.playername = name;
        }

        public void applyChanges(BanC changes) {
            if (changes.playername != null)
                this.playername = changes.playername;
            if (changes.IP != null)
                this.IP = changes.IP;
            if (changes.IP != null && changes.IP.equals("NULL"))
                this.IP = null;
            if (changes.MID != null)
                this.MID = changes.MID;
            if (changes.MID != null && changes.MID.equals("NULL"))
                this.MID = null;
            if (changes.duration > 0)
                this.duration = changes.duration;
            if (changes.notification != null)
                this.notification = changes.notification;
            if (changes.comment != null)
                this.comment = changes.comment;
            if (changes.staffer != null)
                this.staffer = changes.staffer;
        }

        public void updateElapsed(int mins) {
            elapsed += mins;
            updated = true;
            if (duration > 0 && elapsed >= duration) {
                expired = true;
                sendUpdate();
            }
        }

        public void sendUpdate() {
            if (!updated)
                return;
            try {
                psElapsed.setInt(1, elapsed);
                psElapsed.setInt(2, id);
                psElapsed.executeUpdate();
                if (expired) {
                    psExpired.setInt(1, id);
                    psExpired.executeUpdate();
                }
                
            } catch (SQLException sqle) {
                Tools.printLog("SQLException occured while updating active BanCs: " + sqle.getMessage());
                m_botAction.sendChatMessage(2, "BANC WARNING: Problem occured while updating active BanCs: " + sqle.getMessage());
                Tools.printStackTrace(sqle);
            }
            updated = false;
        }

        /**
         * @return the id
         */
        public int getId() {
            return id;
        }

        /**
         * @param id
         *            the id to set
         */
        public void setId(int id) {
            this.id = id;
        }

        /**
         * @return the type
         */
        public BanCType getType() {
            return type;
        }

        /**
         * @param type
         *            the type to set
         */
        public void setType(BanCType type) {
            this.type = type;
        }

        /**
         * @return the playername
         */
        public String getPlayername() {
            return playername;
        }

        /**
         * @param playername
         *            the playername to set
         */
        public void setPlayername(String playername) {
            this.playername = playername;
        }

        /**
         * @return the iP
         */
        public String getIP() {
            return IP;
        }

        /**
         * @param iP
         *            the iP to set
         */
        public void setIP(String iP) {
            IP = iP;
        }

        /**
         * @return the mID
         */
        public String getMID() {
            return MID;
        }

        /**
         * @param mID
         *            the mID to set
         */
        public void setMID(String mID) {
            MID = mID;
        }

        /**
         * @return the created
         */
        public Date getCreated() {
            return created;
        }

        /**
         * @param created
         *            the created to set
         */
        public void setCreated(Date created) {
            this.created = created;
        }

        public int getElapsed() {
            return elapsed;
        }

        /**
         * @return the duration
         */
        public long getDuration() {
            return duration;
        }

        /**
         * @param duration
         *            the duration to set
         */
        public void setDuration(long duration) {
            this.duration = duration;
        }

        /**
         * @return the notification
         */
        public boolean isNotification() {
            return notification;
        }

        /**
         * @param notification
         *            the notification to set
         */
        public void setNotification(boolean notification) {
            this.notification = notification;
        }

        /**
         * @return the notification
         */
        public Boolean getNotification() {
            return notification;
        }

        /**
         * @param notification
         *            the notification to set
         */
        public void setNotification(Boolean notification) {
            this.notification = notification;
        }

        /**
         * @return the staffer
         */
        public String getStaffer() {
            return staffer;
        }

        /**
         * @param staffer
         *            the staffer to set
         */
        public void setStaffer(String staffer) {
            this.staffer = staffer;
        }

        /**
         * @return the comment
         */
        public String getComment() {
            return comment;
        }

        /**
         * @param comment
         *            the comment to set
         */
        public void setComment(String comment) {
            this.comment = comment;
        }

        /**
         * @return the applied
         */
        public boolean isApplied() {
            return applied;
        }

        /**
         * @param applied
         *            the applied to set
         */
        public void setApplied(boolean applied) {
            this.applied = applied;
        }

        /**
         * @return the expired
         */
        public boolean isExpired() {
            return expired;
        }

        /**
         * @param expired
         *            the expired to set
         */
        public void setExpired(boolean expired) {
            this.expired = expired;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((IP == null) ? 0 : IP.hashCode());
            result = prime * result + ((MID == null) ? 0 : MID.hashCode());
            result = prime * result + (applied ? 1231 : 1237);
            result = prime * result + ((created == null) ? 0 : created.hashCode());
            result = (int) (prime * result + duration);
            result = prime * result + id;
            result = prime * result + ((notification == null) ? 0 : notification.hashCode());
            result = prime * result + ((playername == null) ? 0 : playername.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            BanC other = (BanC) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (IP == null) {
                if (other.IP != null)
                    return false;
            } else if (!IP.equals(other.IP))
                return false;
            if (MID == null) {
                if (other.MID != null)
                    return false;
            } else if (!MID.equals(other.MID))
                return false;
            if (applied != other.applied)
                return false;
            if (created == null) {
                if (other.created != null)
                    return false;
            } else if (!created.equals(other.created))
                return false;
            if (duration != other.duration)
                return false;
            if (id != other.id)
                return false;
            if (notification == null) {
                if (other.notification != null)
                    return false;
            } else if (!notification.equals(other.notification))
                return false;
            if (playername == null) {
                if (other.playername != null)
                    return false;
            } else if (!playername.equals(other.playername))
                return false;
            if (type == null) {
                if (other.type != null)
                    return false;
            } else if (!type.equals(other.type))
                return false;
            return true;
        }

        private staffbot_banc getOuterType() {
            return staffbot_banc.this;
        }
    }

}