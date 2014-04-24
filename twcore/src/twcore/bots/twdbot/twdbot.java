package twcore.bots.twdbot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.StringTokenizer;
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
import twcore.core.events.SQLResultEvent;
import twcore.core.game.Player;
import twcore.core.stats.DBPlayerData;
import twcore.core.util.Tools;
import twcore.core.util.ipc.EventType;
import twcore.core.util.ipc.IPCChallenge;

/**
 * Handles TWD administration tasks such as registration and deletion. Prevents players from playing from any but registered IP/MID, unless overridden
 * by an Op.
 * 
 * Also handles and maintains MatchBot location and availability as well as game information for potential alerts to entering players
 */
public class twdbot extends SubspaceBot {

    String m_arena;
    BotSettings m_botSettings;
    OperatorList m_opList;
    LinkedList<DBPlayerData> m_players;
    LinkedList<SquadOwner> m_squadowner;

    public HashMap<String, String> m_requesters;

    private String register = "";
    private String einfoer = "";
    private String einfoee = "";
    private String locatee = "";
    private HashMap<String, String> m_waitingAction;
    private String webdb = "website";
    private static final String IPC = "MatchBot";

    private LinkedList<String> m_watches;
    private TimerTask einfo;

    private boolean aliasRelay;
    
    TimerTask messages, locater;

    int ownerID;

    /** Creates a new instance of twdbot */
    public twdbot(BotAction botAction) {
        // Setup of necessary stuff for any bot.
        super(botAction);

        m_botSettings = m_botAction.getBotSettings();
        m_arena = m_botSettings.getString("Arena");
        m_opList = m_botAction.getOperatorList();

        m_players = new LinkedList<DBPlayerData>();
        m_squadowner = new LinkedList<SquadOwner>();

        m_waitingAction = new HashMap<String, String>();
        m_requesters = new HashMap<String, String>();

        //birthday = new SimpleDateFormat("HH:mm MM.dd.yy").format(Calendar.getInstance().getTime());
        m_watches = new LinkedList<String>();
        aliasRelay = m_botSettings.getInt("AliasRelay") == 1;
        requestEvents();
    }

    public void requestEvents() {
        EventRequester req = m_botAction.getEventRequester();
        req.request(EventRequester.LOGGED_ON);
        req.request(EventRequester.ARENA_JOINED);
        req.request(EventRequester.MESSAGE);
    }

    @Override
    public void handleEvent(ArenaJoined event) {
        if (!m_botAction.getArenaName().equalsIgnoreCase("TWD"))
            if (einfoer.length() > 1 && einfoee.length() > 1) {
                if (locatee.length() > 0) {
                    locatee = "";
                    m_botAction.sendUnfilteredPrivateMessage(einfoee, "*info");
                } else
                    m_botAction.sendUnfilteredPrivateMessage(einfoee, "*einfo");
            } else if (locatee.length() > 0)
                m_botAction.sendUnfilteredPrivateMessage(locatee, "*info");
    }

    @Override
    public void handleEvent(LoggedOn event) {
        m_botAction.joinArena(m_arena);
        ownerID = 0;
        m_botAction.ipcSubscribe(IPC);
        TimerTask checkMessages = new TimerTask() {
            @Override
            public void run() {
                checkMessages();
                checkForResets();
            };
        };
        m_botAction.scheduleTaskAtFixedRate(checkMessages, 5000, 30000);
        m_botAction.sendUnfilteredPublicMessage("?chat=robodev,twdstaff,executive lounge");
    }

    @Override
    public void handleEvent(SQLResultEvent event) {
        if (event.getIdentifier().equals("twdbot"))
            m_botAction.SQLClose(event.getResultSet());
    }

    @Override
    public void handleEvent(InterProcessEvent event) {
        if (event.getChannel().equals(IPC))
            if (event.getObject() instanceof IPCChallenge) {
                IPCChallenge ipc = (IPCChallenge) event.getObject();
                if (ipc.getRecipient().equalsIgnoreCase(m_botAction.getBotName()) && m_watches.contains(ipc.getName().toLowerCase()))
                    if (ipc.getType() == EventType.CHALLENGE) {
                        m_botAction.sendChatMessage(3, "" + ipc.getName() + " challenged " + ipc.getSquad2() + " to " + ipc.getPlayers() + "s in "
                                + ipc.getArena());
                        m_botAction.sendChatMessage(2, "" + ipc.getName() + " challenged " + ipc.getSquad2() + " to " + ipc.getPlayers() + "s in "
                                + ipc.getArena());
                    } else if (ipc.getType() == EventType.ALLCHALLENGE) {
                        m_botAction.sendChatMessage(3, "" + ipc.getName() + " challenged all to " + ipc.getPlayers() + "s in " + ipc.getArena());
                        m_botAction.sendChatMessage(2, "" + ipc.getName() + " challenged all to " + ipc.getPlayers() + "s in " + ipc.getArena());
                    } else if (ipc.getType() == EventType.TOPCHALLENGE) {
                        m_botAction.sendChatMessage(3, "" + ipc.getName() + " challenged top teams to " + ipc.getPlayers() + "s in " + ipc.getArena());
                        m_botAction.sendChatMessage(2, "" + ipc.getName() + " challenged top teams to " + ipc.getPlayers() + "s in " + ipc.getArena());
                    }
            }
    }

    @Override
    public void handleEvent(Message event) {
        String message = event.getMessage();
        String name = m_botAction.getPlayerName(event.getPlayerID());
        // Weird check, might need optimization, but for some reason, the bot sometimes managed to link someone else's pID to his own name.
        if (name == null ||
                (event.getMessager() != null
                        && name.equalsIgnoreCase(m_botAction.getBotName())
                        && event.getMessager().equalsIgnoreCase(m_botAction.getBotName()) ))
            name = event.getMessager();
        int type = event.getMessageType();

        if (type == Message.PRIVATE_MESSAGE || type == Message.REMOTE_PRIVATE_MESSAGE || type == Message.CHAT_MESSAGE) {
            if (m_opList.isSysop(name) || isTWDOp(name)) {
                if (message.startsWith("!ban ")) {
                    cmd_challengeBan(name, message.substring(message.indexOf(" ") + 1));
                    return;
                } else if (message.startsWith("!unban ")) {
                    cmd_challengeUnban(name, message.substring(message.indexOf(" ") + 1));
                    return;
                } else if (message.startsWith("!chawas")) {
                    cmd_watches(name);
                    return;
                } else if (message.startsWith("!sibling "))
                    cmd_sibling(name, message.substring(message.indexOf(" ")));
                else if (message.startsWith("!addsibling "))
                    cmd_addSibling(name, message.substring(message.indexOf(" ")));
                else if (message.startsWith("!changesibling "))
                    cmd_changeSibling(name, message.substring(message.indexOf(" ")));
                else if (message.equalsIgnoreCase("!help")) {
                    cmd_help(name);
                    cmd_DisplayHelp(name, false);
                } else if (message.startsWith("!pwmatch "))
                    cmd_passwordMatch(name, message);
                else if (message.startsWith("!chawa ")) {
                    String player = message.substring(message.indexOf(" ") + 1);
                    if (m_watches.contains(player.toLowerCase())) {
                        m_watches.remove(player.toLowerCase());
                        m_botAction.sendChatMessage(3, "" + player + " has been removed from challenge watch by " + name);
                        m_botAction.sendChatMessage(2, "" + player + " has been removed from challenge watch by " + name);
                    } else {
                        m_watches.add(player.toLowerCase());
                        m_botAction.sendChatMessage(3, "" + player + " has been added to challenge watch by " + name);
                        m_botAction.sendChatMessage(2, "" + player + " has been added to challenge watch by " + name);
                    }
                } else if (m_opList.isSysop(name) && message.equalsIgnoreCase("!relay")) {
                    cmd_relay(name);
                } else if (type != Message.CHAT_MESSAGE) {
                    // Operator commands
                    if (message.startsWith("!watch ")) {
                        String player = message.substring(message.indexOf(" ") + 1);
                        if (m_watches.contains(player.toLowerCase())) {
                            m_watches.remove(player.toLowerCase());
                            m_botAction.sendChatMessage(3, "" + player + " has been removed from challenge watch by " + name);
                            m_botAction.sendChatMessage(2, "" + player + " has been removed from challenge watch by " + name);
                        } else {
                            m_watches.add(player.toLowerCase());
                            m_botAction.sendChatMessage(3, "" + player + " has been added to challenge watch by " + name);
                            m_botAction.sendChatMessage(2, "" + player + " has been added to challenge watch by " + name);
                        }
                    } else if (message.startsWith("!watches")) {
                        cmd_watches(name);
                        return;
                    } else if (message.startsWith("!resetname "))
                        cmd_ResetName(name, message.substring(11), false);
                    else if (message.startsWith("!cancelreset "))
                        cmd_CancelResetName(name, message.substring(13), false);
                    else if (message.startsWith("!resettime "))
                        cmd_GetResetTime(name, message.substring(11), false, false);
                    else if (message.startsWith("!enablename "))
                        cmd_EnableName(name, message.substring(12));
                    else if (message.startsWith("!disablename "))
                        cmd_DisableName(name, message.substring(13));
                    else if (message.startsWith("!info "))
                        cmd_DisplayInfo(name, message.substring(6), false);
                    else if (message.startsWith("!fullinfo "))
                        cmd_DisplayInfo(name, message.substring(10), true);
                    else if (message.startsWith("!usage "))
                        cmd_usage(name, message);
                    else if (message.startsWith("!register "))
                        cmd_RegisterName(name, message.substring(10), false);
                    else if (message.startsWith("!regconflicts "))
                        cmd_RegisterConflicts(name, message.substring(14));
                    else if (message.startsWith("!registered "))
                        cmd_CheckRegistered(name, message.substring(12));
                    else if (message.equals("!twdops"))
                        cmd_TWDOps(name);
                    else if (message.startsWith("!altip "))
                        cmd_IPCheck(name, message.substring(7), true);
                    else if (message.startsWith("!altmid "))
                        cmd_MIDCheck(name, message.substring(8), true);
                    else if (message.startsWith("!check "))
                        checkIP(name, message.substring(7));
                    else if (message.startsWith("!go "))
                        m_botAction.changeArena(message.substring(4));
                    else if (message.startsWith("!help"))
                        cmd_DisplayHelp(name, false);
                    else if (message.startsWith("!add "))
                        cmd_AddMIDIP(name, message.substring(5));
                    else if (message.startsWith("!removeip "))
                        cmd_RemoveIP(name, message.substring(10));
                    else if (message.startsWith("!removemid "))
                        cmd_RemoveMID(name, message.substring(11));
                    else if (message.startsWith("!removeipmid "))
                        cmd_RemoveIPMID(name, message.substring(13));
                    else if (message.startsWith("!listipmid "))
                        cmd_ListIPMID(name, message.substring(11));
                    else if (message.equalsIgnoreCase("!die")) {
                        this.handleDisconnect();
                        m_botAction.die();
                    }
                    if (m_opList.isSmod(name)) {
                        if (message.startsWith("!einfo "))
                            cmd_einfo(name, message);
                    }
                }
            } else if (type != Message.REMOTE_PRIVATE_MESSAGE) {
                // Player commands
                if (message.equals("!resetname"))
                    cmd_ResetName(name, name, true);
                else if (message.equals("!resettime"))
                    cmd_GetResetTime(name, name, true, false);
                else if (message.equals("!cancelreset"))
                    cmd_CancelResetName(name, name, true);
                else if (message.equals("!registered"))
                    cmd_CheckRegistered(name, name);
                else if (message.startsWith("!registered "))
                    cmd_CheckRegistered(name, message.substring(12));
                else if (message.equals("!register"))
                    cmd_RegisterName(name, name, true);
                else if (message.equals("!twdops"))
                    cmd_TWDOps(name);
                else if (message.equals("!help"))
                    cmd_DisplayHelp(name, true);
            }

            if (type != Message.REMOTE_PRIVATE_MESSAGE) {
                // First: convert the command to a command with parameters
                String[] temp = stringChopper(message, ' ');
                if (temp != null && temp.length > 0 ) {
                    String command = temp[0];
                    String[] parameters = stringChopper(message.substring(command.length()).trim(), ':');
                    for (int i = 0; i < parameters.length; i++)
                        parameters[i] = parameters[i].replace(':', ' ').trim();
                    command = command.trim();

                    if (command.equals("!signup"))
                        cmd_signup(name, command, parameters);
                    if (command.equals("!squadsignup"))
                        cmd_squadsignup(name, message);
                }
            }
        }

        if (type == Message.ARENA_MESSAGE) {
            if (event.getMessage().startsWith("Owner is ")) {
                String squadOwner = event.getMessage().substring(9);

                ListIterator<SquadOwner> i = m_squadowner.listIterator();
                while (i.hasNext()) {
                    SquadOwner t = i.next();
                    if (t.getID() == ownerID)
                        if (t.getOwner().equalsIgnoreCase(squadOwner))
                            storeSquad(t.getSquad(), t.getOwner());
                        else
                            m_botAction.sendSmartPrivateMessage(t.getOwner(), "You are not the owner of the squad " + t.getSquad());
                }
                ownerID++;
            } else if (message.startsWith("IP:") && (einfoee.isEmpty() || !message.contains(einfoee)))
                parseIP(message);
            else if ((message.startsWith("TIME") || message.contains(" Res: ")) && einfoer.length() > 1) {
                m_botAction.sendSmartPrivateMessage(einfoer, message);
                einfoer = "";
                einfoee = "";
                if (!m_botAction.getArenaName().equalsIgnoreCase("TWD")) {
                    TimerTask goback = new TimerTask() {
                        @Override
                        public void run() {
                            m_botAction.changeArena("TWD");
                        }
                    };
                    m_botAction.scheduleTask(goback, 1500);
                }
            } else if (message.contains(" - ")) {
                String located = message.substring(0, message.lastIndexOf(" - "));
                if (located.equalsIgnoreCase(einfoee)) {
                    m_botAction.cancelTask(einfo);
                    String arena = message.substring(message.lastIndexOf("- ") + 2);
                    if (arena.startsWith("Public"))
                        arena = arena.substring(arena.indexOf(" ") + 1);
                    m_botAction.changeArena(arena);
                } else if (m_requesters.containsKey(located) || m_requesters.containsKey(located.toLowerCase()))
                    if (locatee.equalsIgnoreCase(located)) {
                        m_botAction.cancelTask(locater);
                        locatee = located;
                        String arena = message.substring(message.lastIndexOf("- ") + 2);
                        if (arena.startsWith("Public"))
                            arena = arena.substring(arena.indexOf(" ") + 1);
                        m_botAction.changeArena(arena);
                    }
            }
        }
    }
    
    private void cmd_relay(String name) {
        aliasRelay = !aliasRelay;
        if (aliasRelay) {
            m_botAction.sendSmartPrivateMessage(name, "Alias relay messages: ENABLED");
            m_botSettings.put("AliasRelay", 1);
        } else {
            m_botAction.sendSmartPrivateMessage(name, "Alias relay messages: DISABLED");
            m_botSettings.put("AliasRelay", 0);
        }
    }

    private void cmd_passwordMatch(String name, String message) {
        try {
            StringTokenizer arguments = new StringTokenizer(message.substring(9), ":");
            if (!(arguments.countTokens() == 2))
                m_botAction.sendSmartPrivateMessage(name, "How can I check if you don't give me two values to check?");
            else {

                String name1 = arguments.nextToken();
                String name2 = arguments.nextToken();

                DBPlayerData pws = new DBPlayerData(m_botAction, "website", name1, false);
                DBPlayerData pws2 = new DBPlayerData(m_botAction, "website", name2, false);

                int id = pws.getUserID();
                int id2 = pws2.getUserID();

                ResultSet result = m_botAction.SQLQuery("website", "SELECT fcPassword FROM tblUserAccount WHERE fnUserID = " + id);
                ResultSet resultB = m_botAction.SQLQuery("website", "SELECT fcPassword FROM tblUserAccount WHERE fnUserID = " + id2);

                if (!result.next() || !resultB.next())
                    m_botAction.sendChatMessage(2, "Cannot compare one of the pwmatch values. Please make sure the user exists on TWD.");
                else {

                    String pass = result.getString("fcPassword");
                    String pass2 = resultB.getString("fcPassword");

                    if (pass.equals(pass2))
                        m_botAction.sendChatMessage(2, "The requested TWD Password match by " + name + " returned POSITIVE");
                    else
                        m_botAction.sendChatMessage(2, "The requested TWD Password match by " + name + " returned NEGATIVE");
                }
                m_botAction.SQLClose(result);
                m_botAction.SQLClose(resultB);
            }
        } catch (SQLException e) {
            Tools.printStackTrace(e);
        }
    }

    private void cmd_challengeBan(String name, String msg) {
        DBPlayerData dbp = new DBPlayerData(m_botAction, webdb, msg, false);
        if (dbp.checkPlayerExists())
            try {
                ResultSet rs = m_botAction.SQLQuery(webdb, "SELECT * FROM tblChallengeBan WHERE fnUserID = " + dbp.getUserID() + " AND fnActive = 1");
                if (rs.next()) {
                    m_botAction.SQLClose(rs);
                    m_botAction.sendSmartPrivateMessage(name, "This ban already exists.");
                    return;
                }
                m_botAction.SQLClose(rs);
                m_botAction.SQLQueryAndClose(webdb, "INSERT INTO tblChallengeBan (fnUserID) VALUES(" + dbp.getUserID() + ")");
                m_botAction.sendChatMessage(3, "" + dbp.getUserName() + " has been challenge banned by " + name);
                m_botAction.sendChatMessage(2, "" + dbp.getUserName() + " has been challenge banned by " + name);
            } catch (SQLException e) {
                Tools.printStackTrace(e);
            }
        else
            m_botAction.sendSmartPrivateMessage(name, "Player not found.");
    }

    private void cmd_challengeUnban(String name, String msg) {
        DBPlayerData dbp = new DBPlayerData(m_botAction, webdb, msg, false);
        if (dbp.checkPlayerExists())
            try {
                ResultSet rs = m_botAction.SQLQuery(webdb, "SELECT * FROM tblChallengeBan WHERE fnUserID = " + dbp.getUserID() + " AND fnActive = 1");
                if (rs.next()) {
                    m_botAction.SQLClose(rs);
                    m_botAction.SQLQueryAndClose(webdb, "UPDATE tblChallengeBan SET fnActive = 0 WHERE fnUserID = " + dbp.getUserID());
                    m_botAction.sendChatMessage(2, "" + dbp.getUserName() + "'s challenge ban has been removed by " + name);
                    m_botAction.sendChatMessage(3, "" + dbp.getUserName() + "'s challenge ban has been removed by " + name);
                    return;
                } else {
                    m_botAction.SQLClose(rs);
                    m_botAction.sendSmartPrivateMessage(name, "No ban found for " + dbp.getUserName() + ".");
                }
            } catch (SQLException e) {
                Tools.printStackTrace(e);
            }
        else
            m_botAction.sendSmartPrivateMessage(name, "Player not found.");
    }

    private void cmd_watches(String name) {
        String watches = "Challenge watches: ";
        for (String p : m_watches)
            watches += p + ", ";

        watches = watches.substring(0, watches.length() - 2);
        m_botAction.sendChatMessage(2, watches);
        m_botAction.sendChatMessage(3, watches);
    }

    private void cmd_sibling(String name, String params) {
        if (params == null || params.isEmpty())
            m_botAction.sendChatMessage(2, "Usage: !sibling name");
        try {

            //grab the sibling group ids for corresponding player
            ResultSet groupSet = m_botAction.SQLQuery(webdb, "SELECT fnTWDSiblingGroupID FROM tblTWDSibling WHERE fnUserID in ("
                    + "SELECT fnUserID FROM tblUser WHERE fcUserName like '" + params.trim() + "' ORDER BY fnUserID ) and fnStaffRemoveUserID is NULL");

            if (groupSet == null || !groupSet.next()) {
                m_botAction.sendChatMessage(2, "No registered siblings found for " + params.trim() + ".");
                return;
            }

            do {
                m_botAction.sendChatMessage(2, "Siblings found for " + params.trim() + ": ");

                //m_botAction.sendChatMessage(2, "");
                
                ResultSet nameSet = m_botAction.SQLQuery(webdb, "SELECT fcUserName FROM tblUser WHERE fnUserID IN ("
                        + "SELECT fnUserID FROM tblTWDSibling WHERE fnTWDSiblingGroupID = '" + groupSet.getString(1) + "' and fnStaffRemoveUserID is NULL )");

                ResultSet infoSet = m_botAction.SQLQuery(webdb, "SELECT fcComment, ftUpdated FROM tblTWDSiblingGroup "
                        + "WHERE fnTWDSiblingGroupID = '" + groupSet.getString(1) + "'" );

                while (nameSet != null && nameSet.next())
                    m_botAction.sendChatMessage(2, "  " + nameSet.getString(1));

                if (infoSet != null && infoSet.next()) {
                    m_botAction.sendChatMessage(2, "Updated: " + infoSet.getString(2));
                    String comments[] = infoSet.getString(1).split("\\r?\\n");
                    for (int i=0; i<comments.length;i++)
                    {
                    	comments[i] = comments[i].trim();
                    	if (!comments[i].isEmpty())
                    		m_botAction.sendChatMessage(2, "Comment: "+ comments[i]);
                    }
                    //m_botAction.sendChatMessage(2, "Comment: " + infoSet.getString(1));
                }

                nameSet.close();
                infoSet.close();

            } while (groupSet.next());

            groupSet.close();
        } catch (SQLException e) {
            m_botAction.sendChatMessage(2, "An SQLException occured in !sibling");
            m_botAction.sendChatMessage(2, e.getMessage());
        }
    }

    private void cmd_addSibling(String name, String params) {
        String[] names = params.split(":");
        if (names.length < 2)
            m_botAction.sendChatMessage(2, "Usage: !addsibling name:siblingName");
        else {
            /*try {
                
            } catch (SQLException e) {
                m_botAction.sendChatMessage(2, "An SQLException occured in !addsibling");
            }*/
        }
    }

    private void cmd_changeSibling(String name, String params) {
        String[] names = params.split(":");
        if (names.length < 2)
            m_botAction.sendChatMessage(2, "Usage: !changesibling oldName:newName");
        else {
            /*try {
                
            } catch (SQLException e) {
                m_botAction.sendChatMessage(2, "An SQLException occured in !changesibling");
            }*/
        }
    }

    private void cmd_TWDOps(String name) {
        try {
            HashSet<String> twdOps = new HashSet<String>();
            ResultSet rs = m_botAction.SQLQuery(webdb, "SELECT tblUser.fcUserName, tblUserRank.fnRankID FROM tblUser, tblUserRank"
                    + " WHERE tblUser.fnUserID = tblUserRank.fnUserID" + " AND ( tblUserRank.fnRankID = 14 OR tblUserRank.fnRankID = 19 )");
            while (rs != null && rs.next()) {
                String queryName;
                String temp = rs.getString("fcUserName");
                queryName = temp;
                if (rs.getInt("fnRankID") == 19) {
                    queryName = temp + " (SMod)";
                    twdOps.remove(temp);
                }
                if (!twdOps.contains(queryName) && !twdOps.contains(queryName + " (SMod)"))
                    twdOps.add(queryName);
            }
            Iterator<String> it = twdOps.iterator();
            ArrayList<String> bag = new ArrayList<String>();
            m_botAction.sendSmartPrivateMessage(name, "+------------------- TWD Operators --------------------");
            while (it.hasNext()) {
                bag.add(it.next());
                if (bag.size() == 4) {
                    String row = "";
                    for (int i = 0; i < 4; i++)
                        row = row + bag.get(i) + ", ";
                    m_botAction.sendSmartPrivateMessage(name, "| " + row.substring(0, row.length() - 2));
                    bag.clear();
                }
            }
            if (bag.size() != 0) {
                String row = "";
                for (int i = 0; i < bag.size(); i++)
                    row = row + bag.get(i) + ", ";
                m_botAction.sendSmartPrivateMessage(name, "| " + row.substring(0, row.length() - 2));
            }
            m_botAction.sendSmartPrivateMessage(name, "+------------------------------------------------------");
        } catch (SQLException e) {
            Tools.printStackTrace(e);
        }
    }

    private void cmd_AddMIDIP(String staffname, String info) {
        try {
            info = info.toLowerCase();
            String pieces[] = info.split("  ", 3);
            HashSet<String> names = new HashSet<String>();
            HashSet<String> IPs = new HashSet<String>();
            HashSet<String> mIDs = new HashSet<String>();
            for (int k = 0; k < pieces.length; k++)
                if (pieces[k].startsWith("name"))
                    names.add(pieces[k].split(":")[1]);
                else if (pieces[k].startsWith("ip"))
                    IPs.add(pieces[k].split(":")[1]);
                else if (pieces[k].startsWith("mid"))
                    mIDs.add(pieces[k].split(":")[1]);
            Iterator<String> namesIt = names.iterator();
            while (namesIt.hasNext()) {
                String name = namesIt.next();
                Iterator<String> ipsIt = IPs.iterator();
                Iterator<String> midsIt = mIDs.iterator();
                while (ipsIt.hasNext() || midsIt.hasNext()) {
                    String IP = null;
                    if (ipsIt.hasNext())
                        IP = ipsIt.next();
                    String mID = null;
                    if (midsIt.hasNext())
                        mID = midsIt.next();
                    if (IP == null && mID == null)
                        m_botAction.sendSmartPrivateMessage(staffname, "Syntax (note double spaces):  !add name:thename  ip:IP  mid:MID");
                    else if (IP == null) {
                        ResultSet r = m_botAction.SQLQuery(webdb, "SELECT fnUserID FROM tblTWDPlayerMID WHERE fcUserName='"
                                + Tools.addSlashesToString(name) + "' AND fnMID=" + mID);
                        if (r.next()) {
                            m_botAction.sendPrivateMessage(staffname, "Entry for '" + name + "' already exists with that MID in it.");
                            m_botAction.SQLClose(r);
                            return;
                        }
                        m_botAction.SQLClose(r);
                        m_botAction.SQLQueryAndClose(webdb, "INSERT INTO tblTWDPlayerMID (fnUserID, fcUserName, fnMID) VALUES "
                                + "((SELECT fnUserID FROM tblUser WHERE fcUserName = '" + Tools.addSlashesToString(name) + "' LIMIT 0,1), " + "'"
                                + Tools.addSlashesToString(name) + "', " + Tools.addSlashesToString(mID) + ")");
                        m_botAction.sendSmartPrivateMessage(staffname, "Added MID: " + mID);
                    } else if (mID == null) {
                        ResultSet r = m_botAction.SQLQuery(webdb, "SELECT fnUserID FROM tblTWDPlayerMID WHERE fcUserName='"
                                + Tools.addSlashesToString(name) + "' AND fcIP='" + IP + "'");
                        if (r.next()) {
                            m_botAction.sendPrivateMessage(staffname, "Entry for '" + name + "' already exists with that IP in it.");
                            m_botAction.SQLClose(r);
                            return;
                        }
                        m_botAction.SQLClose(r);
                        m_botAction.SQLQueryAndClose(webdb, "INSERT INTO tblTWDPlayerMID (fnUserID, fcUserName, fcIP) VALUES "
                                + "((SELECT fnUserID FROM tblUser WHERE fcUserName = '" + Tools.addSlashesToString(name) + "' LIMIT 0,1), " + "'"
                                + Tools.addSlashesToString(name) + "', '" + Tools.addSlashesToString(IP) + "')");
                        m_botAction.sendSmartPrivateMessage(staffname, "Added IP: " + IP);
                    } else {
                        ResultSet r = m_botAction.SQLQuery(webdb, "SELECT fnUserID FROM tblTWDPlayerMID WHERE fcUserName='"
                                + Tools.addSlashesToString(name) + "' AND fcIP='" + IP + "' AND fnMID=" + mID);
                        if (r.next()) {
                            m_botAction.sendPrivateMessage(staffname, "Entry for '" + name + "' already exists with that IP/MID combination.");
                            m_botAction.SQLClose(r);
                            return;
                        }
                        m_botAction.SQLClose(r);
                        m_botAction.SQLQueryAndClose(webdb, "INSERT INTO tblTWDPlayerMID (fnUserID, fcUserName, fnMID, fcIP) VALUES "
                                + "((SELECT fnUserID FROM tblUser WHERE fcUserName = '" + Tools.addSlashesToString(name) + "' LIMIT 0,1), " + "'"
                                + Tools.addSlashesToString(name) + "', " + Tools.addSlashesToString(mID) + ", " + "'" + Tools.addSlashesToString(IP)
                                + "')");
                        m_botAction.sendSmartPrivateMessage(staffname, "Added IP " + IP + " and MID " + mID + " into a combined entry.");
                    }
                }
            }
        } catch (Exception e) {
            m_botAction.sendPrivateMessage(staffname, "An unexpected error occured. Please contact a bot developer with the following message: "
                    + e.getMessage());
        }
    }

    private void cmd_RemoveMID(String Name, String info) {
        try {
            String pieces[] = info.split(":", 2);
            if (pieces.length < 2)
                m_botAction.sendPrivateMessage(Name, "Needs to be like !removemid <name>:<mid>");
            String name = pieces[0];
            String mID = pieces[1];
            m_botAction.SQLQueryAndClose(webdb, "UPDATE tblTWDPlayerMID SET fnMID = 0 WHERE fcUserName = '" + Tools.addSlashesToString(name)
                    + "' AND fnMID = " + Tools.addSlashesToString(mID));
            m_botAction.sendPrivateMessage(Name, "MID removed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cmd_RemoveIP(String Name, String info) {
        try {
            String pieces[] = info.split(":", 2);
            if (pieces.length < 2)
                m_botAction.sendPrivateMessage(Name, "Needs to be like !removeip <name>:<IP>");
            String name = pieces[0];
            String IP = pieces[1];
            m_botAction.SQLQueryAndClose(webdb, "UPDATE tblTWDPlayerMID SET fcIP = '0.0.0.0' WHERE fcUserName = '" + Tools.addSlashesToString(name)
                    + "' AND fcIP = '" + Tools.addSlashesToString(IP) + "'");
            m_botAction.sendPrivateMessage(Name, "IP removed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cmd_RemoveIPMID(String name, String playerName) {
        try {
            m_botAction.SQLQueryAndClose(webdb, "DELETE FROM tblTWDPlayerMID WHERE fcUserName = '" + Tools.addSlashesToString(playerName) + "'");
            m_botAction.sendPrivateMessage(name, "Removed all IP and MID entries for '" + playerName + "'.");
        } catch (SQLException e) {}
    }

    private void cmd_ListIPMID(String name, String player) {
        DBPlayerData dbP = new DBPlayerData(m_botAction, webdb, player);
        m_botAction.sendSmartPrivateMessage(name, "TWD record for '" + player + "'");
        String header = "Status: ";
        if (dbP.getStatus() == 0)
            header += "NOT REGISTERED";
        else {
            if (dbP.getStatus() == 1)
                header += "REGISTERED";
            else
                header += "DISABLED";
            header += "  Registered IP: " + dbP.getIP() + "  Registered MID: " + dbP.getMID();
        }
        m_botAction.sendSmartPrivateMessage(name, header);

        try {
            ResultSet results = m_botAction.SQLQuery(webdb, "SELECT fcIP, fnMID FROM tblTWDPlayerMID WHERE fcUserName = '"
                    + Tools.addSlashesToString(player) + "'");
            if (!results.next())
                m_botAction.sendSmartPrivateMessage(name, "There are no staff-registered IPs and MIDs for this name.");
            else {
                m_botAction.sendSmartPrivateMessage(name, "Staff-registered IP and MID exceptions:");
                do {
                    String message = "";
                    if (!results.getString("fcIP").equals("0.0.0.0"))
                        message += "IP: " + results.getString("fcIP") + "   ";
                    if (results.getInt("fnMID") != 0)
                        message += "mID: " + results.getInt("fnMID");
                    m_botAction.sendSmartPrivateMessage(name, message);
                } while (results.next());
            }
            m_botAction.SQLClose(results);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void cmd_signup(String name, String command, String[] parameters) {
        try {

            DBPlayerData data = new DBPlayerData(m_botAction, webdb, name, false);

            // Special if captain or staff
            boolean specialPlayer = m_botAction.getOperatorList().isER(name) || data.hasRank(4);

            if (parameters.length > 0 && passwordIsValid(parameters[0], specialPlayer)) {
                boolean success = false;
                boolean can_continue = true;

                String fcPassword = parameters[0];
                DBPlayerData thisP;

                thisP = findPlayerInList(name);
                if (thisP != null)
                    if (System.currentTimeMillis() - thisP.getLastQuery() < 300000)
                        can_continue = false;

                if (thisP == null) {
                    thisP = new DBPlayerData(m_botAction, webdb, name, true);
                    success = thisP.getPlayerAccountData();
                } else
                    success = true;

                if (can_continue) {
                    if (!success)
                        success = thisP.createPlayerAccountData(fcPassword);
                    else if (!thisP.getPassword().equals(fcPassword))
                        success = thisP.updatePlayerAccountData(fcPassword);

                    if (!thisP.hasRank(2))
                        thisP.giveRank(2);

                    if (success) {
                        m_botAction.sendSmartPrivateMessage(name, "This is your account information: ");
                        m_botAction.sendSmartPrivateMessage(name, "Username: " + thisP.getUserName());
                        m_botAction.sendSmartPrivateMessage(name, "Password: " + thisP.getPassword());
                        m_botAction.sendSmartPrivateMessage(name, "To join your squad roster, go to http://twd.trenchwars.org . Log in, click on 'My Profile', select your squad and then click on 'Apply to this squad'");
                        m_players.add(thisP);
                    } else
                        m_botAction.sendSmartPrivateMessage(name, "Couldn't create/update your user account.  Contact a TWDOp with the ?help command for assistance.");
                } else
                    m_botAction.sendSmartPrivateMessage(name, "You can only signup / change passwords once every 5 minutes");
            } else if (specialPlayer)
                m_botAction.sendSmartPrivateMessage(name, "Specify a password, ex. '!signup MySuperPass11!'. The password must contain at least 1 number, 1 uppercase, and needs to be at least 10 characters long. ");
            else
                m_botAction.sendSmartPrivateMessage(name, "Specify a password, ex. '!signup MyPass11'. The password must contain a number and needs to be at least 8 characters long.");

        } catch (Exception e) {
            throw new RuntimeException("Error in command_signup.");
        }
    }

    private void cmd_squadsignup(String name, String command) {
        Player p = m_botAction.getPlayer(name);
        String squad = p.getSquadName();
        if (squad.equals(""))
            m_botAction.sendSmartPrivateMessage(name, "You are not in a squad.");
        else {
            m_squadowner.add(new SquadOwner(name, squad, ownerID));
            m_botAction.sendUnfilteredPublicMessage("?squadowner " + squad);
        }
    }

    // aliasbot

    private void cmd_CheckRegistered(String name, String message) {

        DBPlayerData dbP = new DBPlayerData(m_botAction, webdb, message);

        if (dbP.isRegistered())
            m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' has been registered.");
        else
            m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' has NOT been registered.");
    }

    private void cmd_ResetName(String name, String message, boolean player) {

        DBPlayerData dbP = new DBPlayerData(m_botAction, webdb, message);

        if (!dbP.isRegistered()) {
            if (player)
                m_botAction.sendSmartPrivateMessage(name, "Your name '" + message + "' has not been registered.");
            else
                m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' has not been registered.");
            return;
        }

        if (!dbP.isEnabled()) {
            m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' is disabled and can't be reset.");
            return;
        }

        if (player) {
            if (dbP.hasBeenDisabled()) {
                m_botAction.sendSmartPrivateMessage(name, "Unable to reset name.  Please contact a TWD Op for assistance.");
                return;
            }
            if (isBeingReset(name, message))
                return;
            else if (!resetPRegistration(dbP.getUserID()))
                m_botAction.sendSmartPrivateMessage(name, "Unable to reset name.  Please contact a TWD Op for assistance.");
            else {
                try {
                    m_botAction.SQLQueryAndClose(webdb, "DELETE FROM tblTWDPlayerMID WHERE fcUserName = '" + Tools.addSlashesToString(name) + "'");
                } catch (SQLException e) {
                    Tools.printStackTrace(e);
                }
                m_botAction.sendSmartPrivateMessage(name, "Your name will be reset in 24 hours.");
            }
        } else {
            if (dbP.hasBeenDisabled()) {
                m_botAction.sendSmartPrivateMessage(name, "That name has been disabled.  If you are sure the player should be allowed to play, enable before resetting.");
                return;
            }
            if (!dbP.resetRegistration())
                m_botAction.sendSmartPrivateMessage(name, "Error resetting name '" + message + "'.  The name may not exist in the database.");
            else {
                try {
                    m_botAction.SQLQueryAndClose(webdb, "DELETE FROM tblTWDPlayerMID WHERE fcUserName = '" + Tools.addSlashesToString(name) + "'");
                } catch (SQLException e) {
                    Tools.printStackTrace(e);
                }
                m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' has been reset, and all IP/MID entries have been removed.");

            }
        }
    }

    private void cmd_CancelResetName(String name, String message, boolean player) {
        DBPlayerData dbP = new DBPlayerData(m_botAction, webdb, message);

        try {
            ResultSet s = m_botAction.SQLQuery(webdb, "SELECT * FROM tblAliasSuppression WHERE fnUserID = '" + dbP.getUserID()
                    + "' && fdResetTime IS NOT NULL");
            if (s.next()) {
                m_botAction.SQLBackgroundQuery(webdb, "twdbot", "UPDATE tblAliasSuppression SET fdResetTime = NULL WHERE fnUserID = '"
                        + dbP.getUserID() + "'");

                if (player)
                    m_botAction.sendSmartPrivateMessage(name, "Your name has been removed from the list of names about to get reset.");
                else
                    m_botAction.sendSmartPrivateMessage(name, "The name '" + message
                            + "' has been removed from the list of names about to get reset.");
            } else if (player)
                m_botAction.sendSmartPrivateMessage(name, "Your name isn't on the list of names about to get reset.");
            else
                m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' was not found on the list of names about to get reset.");
            m_botAction.SQLClose(s);
        } catch (Exception e) {
            if (player)
                m_botAction.sendSmartPrivateMessage(name, "Database error, contact a TWD Op.");
            else
                m_botAction.sendSmartPrivateMessage(name, "Database error: " + e.getMessage() + ".");
        }
    }

    private void cmd_GetResetTime(String name, String message, boolean player, boolean silent) {
        DBPlayerData dbP = new DBPlayerData(m_botAction, webdb, message);

        try {
            ResultSet s = m_botAction.SQLQuery(webdb, "SELECT NOW() as now, DATE_ADD(fdResetTime, INTERVAL 1 DAY) AS resetTime FROM tblAliasSuppression WHERE fnUserID = '"
                    + dbP.getUserID() + "' && fdResetTime IS NOT NULL");
            if (s.next()) {
                String time = s.getString("resetTime");
                String now = s.getString("now");
                if (time.contains("."))
                    time = time.substring(0, time.lastIndexOf("."));
                if (now.contains("."))
                    now = now.substring(0, now.lastIndexOf("."));
                if (player)
                    m_botAction.sendSmartPrivateMessage(name, "Your name will reset at " + time + ". Current time: " + now);
                else
                    m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' will reset at " + time + ". Current time: " + now);
            } else if (!silent)
                if (player)
                    m_botAction.sendSmartPrivateMessage(name, "Your name was not found on the list of names about to get reset.");
                else
                    m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' was not found on the list of names about to get reset.");
            m_botAction.SQLClose(s);
        } catch (Exception e) {
            if (player)
                m_botAction.sendSmartPrivateMessage(name, "Database error, contact a TWD Op.");
            else
                m_botAction.sendSmartPrivateMessage(name, "Database error: " + e.getMessage() + ".");
        }
    }

    private void cmd_EnableName(String name, String message) {

        DBPlayerData dbP = new DBPlayerData(m_botAction, webdb, message);

        if (!dbP.isRegistered()) {
            m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' has not been registered.");
            return;
        }

        if (dbP.isEnabled()) {
            m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' is already enabled.");
            return;
        }

        if (!dbP.enableName()) {
            m_botAction.sendSmartPrivateMessage(name, "Error enabling name '" + message + "'");
            return;
        }
        m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' has been enabled.");
    }

    private void cmd_DisableName(String name, String message) {
        // Create the player if not registered
        DBPlayerData dbP = new DBPlayerData(m_botAction, webdb, message, true);

        if (dbP.hasBeenDisabled()) {
            m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' has already been disabled.");
            return;
        }

        if (!dbP.disableName()) {
            m_botAction.sendSmartPrivateMessage(name, "Error disabling name '" + message + "'");
            return;
        }
        m_botAction.sendSmartPrivateMessage(name, "The name '" + message
                + "' has been disabled, and will not be able to reset manually without being enabled again.");
    }

    private void cmd_DisplayInfo(String name, String message, boolean verbose) {

        DBPlayerData dbP = new DBPlayerData(m_botAction, webdb, message);

        if (!dbP.isRegistered()) {
            m_botAction.sendSmartPrivateMessage(name, "The name '" + message + "' has not been registered.");
            return;
        }
        String status = "ENABLED";
        if (!dbP.isEnabled())
            status = "DISABLED";
        m_botAction.sendSmartPrivateMessage(name, "'" + message + "'  IP:" + dbP.getIP() + "  MID:" + dbP.getMID() + "  " + status + ".  Registered "
                + dbP.getSignedUp());
        if (verbose) {
            dbP.getPlayerSquadData();
            m_botAction.sendSmartPrivateMessage(name, "Member of '" + dbP.getTeamName() + "'; squad created " + dbP.getTeamSignedUp());
        }
        cmd_GetResetTime(name, message, false, true);
    }

    private void cmd_RegisterName(String name, String message, boolean p) {

        Player pl = m_botAction.getPlayer(message);
        String player;
        if (pl == null) {
            m_botAction.sendSmartPrivateMessage(name, "Unable to find " + message + " in the arena.");
            return;
        } else
            player = message;

        DBPlayerData dbP = new DBPlayerData(m_botAction, webdb, player);

        if (dbP.isRegistered()) {
            m_botAction.sendSmartPrivateMessage(name, "This name has already been registered.");
            return;
        }
        register = name;
        if (p)
            m_waitingAction.put(player, "register");
        else
            m_waitingAction.put(player, "forceregister");
        m_botAction.sendUnfilteredPrivateMessage(player, "*info");
    }

    private void cmd_RegisterConflicts(String name, String message) {

        String target = m_botAction.getFuzzyPlayerName(message);
        if (target == null) {
            m_botAction.sendSmartPrivateMessage(name, "Unable to find " + message + " in the arena.");
            return;
        }

        //display if the nick is registered, but continue as we still want to see the other conflicted nicks.
        DBPlayerData dbP = new DBPlayerData(m_botAction, webdb, target);
        if (dbP.isRegistered())
            m_botAction.sendSmartPrivateMessage(name, "The name " + target + " is already registered.");

        register = name;

        //add an entry to the waiting queue so that we know how to process the *info returned information
        m_waitingAction.put(target, "regconflicts");
        m_botAction.sendUnfilteredPrivateMessage(target, "*info");
    }

    private void cmd_IPCheck(String name, String ip, boolean staff) {

        try {
            String query = "SELECT fcUserName, fcIP, fnMID FROM tblAliasSuppression AS A, ";
            query += " tblUser AS U WHERE A.fnUserID = U.fnUserID AND fcIP LIKE '" + ip + "%'";
            ResultSet result = m_botAction.SQLQuery(webdb, query);
            while (result.next()) {
                String out = result.getString("fcUserName") + "  ";
                if (staff) {
                    out += "IP:" + result.getString("fcIP") + "  ";
                    out += "MID:" + result.getString("fnMID");
                }
                m_botAction.sendSmartPrivateMessage(name, out);
            }
            m_botAction.SQLClose(result);
        } catch (Exception e) {
            Tools.printStackTrace(e);
            m_botAction.sendSmartPrivateMessage(name, "Error doing IP check.");
        }
    }

    private void cmd_MIDCheck(String name, String mid, boolean staff) {

        if (mid == null || mid == "" || !(Tools.isAllDigits(mid))) {
            m_botAction.sendSmartPrivateMessage(name, "MID must be all numeric.");
            return;
        }

        try {
            String query = "SELECT fcUserName, fcIP, fnMID FROM tblAliasSuppression AS A, ";
            query += " tblUser AS U WHERE A.fnUserID = U.fnUserID AND fnMID = " + mid;
            ResultSet result = m_botAction.SQLQuery(webdb, query);
            while (result.next()) {
                String out = result.getString("fcUserName") + "  ";
                if (staff) {
                    out += "IP:" + result.getString("fcIP") + "  ";
                    out += "MID:" + result.getString("fnMID");
                }
                m_botAction.sendSmartPrivateMessage(name, out);
            }
            m_botAction.SQLClose(result);
        } catch (Exception e) {
            Tools.printStackTrace(e);
            m_botAction.sendSmartPrivateMessage(name, "Error doing MID check.");
        }
    }

    private void cmd_einfo(String name, String msg) {
        if (msg.length() < 8) return;
        String p = msg.substring(msg.indexOf(" ") + 1);
        if (m_botAction.getFuzzyPlayerName(p) != null) {
            einfoer = name;
            einfoee = p;
            m_botAction.sendUnfilteredPrivateMessage(p, "*einfo");
        } else {
            einfoer = name;
            einfoee = p;
            m_botAction.sendUnfilteredPublicMessage("*locate " + p);
            einfo = new TimerTask() {
                @Override
                public void run() {
                    m_botAction.sendSmartPrivateMessage(einfoer, "Could not locate " + einfoee);
                    einfoer = "";
                    einfoee = "";
                }
            };
            m_botAction.scheduleTask(einfo, 2000);
        }
    }

    private void cmd_usage(String name, String msg) {
        if (msg.length() < 8) return;
        String p = msg.substring(msg.indexOf(" ") + 1);
        if (m_botAction.getFuzzyPlayerName(p) != null) {
            einfoer = name;
            einfoee = p;
            m_botAction.sendUnfilteredPrivateMessage(p, "*info");
        } else if (locatee.isEmpty()) {
            einfoer = name;
            einfoee = p;
            locatee = p;
            m_botAction.sendUnfilteredPublicMessage("*locate " + p);
            einfo = new TimerTask() {
                @Override
                public void run() {
                    m_botAction.sendSmartPrivateMessage(einfoer, "Could not locate " + einfoee);
                    einfoer = "";
                    einfoee = "";
                    locatee = "";
                }
            };
            m_botAction.scheduleTask(einfo, 2000);
        }
    }

    private void cmd_help(String name) {
        String[] msg = { 
                "--------- TWD CHALLENGE COMMANDS -----------------------------------------------------",
                " !chawa <name>                - Toggles challenge watch on or off for <name>",
                " !chawas                      - Displays current challenge watches",
                " !ban <name>                  - Prevents <name> from being able to do challenges for a day",
                " !unban <name>                - Removes challenge ban for <name> if it exists",
                " !sibling <name>              - Looks up all siblings registered for <name>",
                " !addsibling <name>:<sibling> - Registers a <sibling> to <name>",
                " !changesibling <old>:<new>   - changes name in siblings from <old> to <new>" };
        m_botAction.smartPrivateMessageSpam(name, msg);

    }

    private void cmd_DisplayHelp(String name, boolean player) {
        String help[] = { 
                "--------- ACCOUNT MANAGEMENT COMMANDS ------------------------------------------------",
                " !resetname <name>            - resets the name (unregisters it)",
                " !resettime <name>            - returns the time when the name will be reset",
                " !cancelreset <name>          - cancels the !reset a player has issued",
                " !enablename <name>           - enables the name so it can be used in TWD/TWL games",
                " !disablename <name>          - disables the name so it can not be used in TWD/TWL games",
                " !register <name>             - force registers that name, that player must be in the arena",
                " !regconflicts <name>         - Display the list of names causing conflicts when trying to register <name>",
                " !registered <name>           - checks if the name is registered",
                " !add name:<name>  ip:<IP>  mid:<MID>  ",
                "                              - Adds <name> to DB with <IP> and/or <MID>",
                " !removeip <name>:<IP>        - Removes <IP> associated with <name>",
                " !removemid <name>:<MID>      - Removes <MID> associated with <name>",
                " !removeipmid <name>          - Removes all IPs and MIDs for <name>",
                " !listipmid <name>            - Lists IP's and MID's associated with <name>",
                " !pwmatch <name>:<name>       - Compares TWD Passwords of <name> and <name>",
                "--------- ALIAS CHECK COMMANDS -------------------------------------------------------",
                " !info <name>                 - displays the IP/MID that was used to register this name",
                " !fullinfo <name>             - displays IP/MID, squad name, and date squad was reg'd",
                " !altip <IP>                  - looks for matching records based on <IP>",
                " !altmid <MID>                - looks for matching records based on <MID>",
                " !ipidcheck <IP> <MID>        - looks for matching records based on <IP> and <MID>",
                "         <IP> can be partial address - ie:  192.168.0.",
                "--------- MISC COMMANDS --------------------------------------------------------------",
                " !check <name>                - checks live IP and MID of <name> (through *info [no !go] works any arena, NOT the DB)",
                " !einfo <name>                - displays the einfo for <name> in any arena",
                " !usage <name>                - displays the usage information for <name> in any arena",
                " !twdops                      - displays a list of the current TWD Ops", 
                " !go <arena>                  - moves the bot" };
        String SModHelp[] = { 
                "--------- SMOD COMMANDS --------------------------------------------------------------",
                " TWD Operators are determined by levels on the website which can be modified at www.trenchwars.org/staff" };
        String help2[] = { 
                "--------- ACCOUNT MANAGEMENT COMMANDS ------------------------------------------------",
                " !resetname                   - resets your name", 
                " !resettime                   - returns the time when your name will be reset",
                " !cancelreset                 - cancels the !resetname", 
                " !register                    - registers your name",
                " !registered <name>           - checks if the name is registered", };

        String help3[] = { 
                "--------- TWD/TWL COMMANDS -----------------------------------------------------------",
                " !signup <password>           - Replace <password> with a password which is hard to guess.",
                "                                You are safer if you choose a password that differs",
                "                                completely from your current SSCU Continuum password.",
                "                                Example: !signup mypass. This command will get you an",
                "                                useraccount for TWL and TWD. If you have forgotten your",
                "                                password, you can use this to pick a new password",
                " !squadsignup                 - This command will sign up your current ?squad for TWD.",
                "                                Note: You need to be the squadowner of the squad",
                "                                and !registered",
                " !games                       - This command will give you a list of the current matches",
                "                                Note: It will work from any arena!", };

        m_botAction.privateMessageSpam(name, help2);
        if (!player) {
            m_botAction.privateMessageSpam(name, help);
            if (m_opList.isSmod(name))
                m_botAction.privateMessageSpam(name, SModHelp);
        }
        m_botAction.privateMessageSpam(name, help3);
        
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

    public boolean isBeingReset(String name, String message) {
        DBPlayerData database = new DBPlayerData(m_botAction, webdb, message);
        try {
            String query = "SELECT DATE_ADD(fdResetTime, INTERVAL 1 DAY) as resettime, NOW() as now FROM tblAliasSuppression WHERE fnUserID = "
                    + database.getUserID() + " AND fdResetTime IS NOT NULL";
            ResultSet rs = m_botAction.SQLQuery(webdb, query);
            if (rs.next()) { // then it'll be really reseted
                String time = rs.getString("resetTime");
                String now = rs.getString("now");
                if (time.contains("."))
                    time = time.substring(0, time.lastIndexOf("."));
                if (now.contains("."))
                    now = now.substring(0, now.lastIndexOf("."));
                m_botAction.sendPrivateMessage(name, "Your name will reset at " + time + " already. Current time: " + now);
                m_botAction.SQLClose(rs);
                return true;
            }
            m_botAction.SQLClose(rs);
        } catch (SQLException e) {}
        return false;
    }

    public static String[] stringChopper(String input, char deliniator) {
        try {
            LinkedList<String> list = new LinkedList<String>();

            int nextSpace = 0;
            int previousSpace = 0;

            if (input == null)
                return null;

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
            if (stuff.length() > 0)
                list.add(stuff);
            ;
            return list.toArray(new String[list.size()]);
        } catch (Exception e) {
            throw new RuntimeException("Error in stringChopper.");
        }
    }

    public boolean resetPRegistration(int id) {

        try {
            m_botAction.SQLQueryAndClose(webdb, "UPDATE tblAliasSuppression SET fdResetTime = NOW() WHERE fnUserID = " + id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void checkForResets() {
        try {
            m_botAction.SQLBackgroundQuery(webdb, null, "DELETE FROM tblAliasSuppression WHERE fdResetTime < DATE_SUB(NOW(), INTERVAL 1 DAY);");
            m_botAction.SQLBackgroundQuery(webdb, null, "UPDATE tblChallengeBan SET fnActive = 0 WHERE fnActive = 1 AND fdDateCreated < DATE_SUB(NOW(), INTERVAL 1 DAY)");
        } catch (Exception e) {
            Tools.printLog("[TWDBot] Can't check for new names to reset...");
        }
    }

    public void parseIP(String message) {

        String[] pieces = message.split("  ");

        // Log the *info message that used to make the bot crash
        if (pieces.length < 5) {
            Tools.printLog("TWDBOT ERROR: " + message);

            // Make sure that the bot wont crash on it again
            return;
        }

        String name = pieces[3].substring(10);
        String ip = pieces[0].substring(3);
        String mid = pieces[5].substring(10);

        DBPlayerData dbP = new DBPlayerData(m_botAction, webdb, name);

        // If an info action wasn't set don't handle it
        if (m_waitingAction.containsKey(name)) {

            String option = m_waitingAction.get(name);
            m_waitingAction.remove(name);

            // Note you can't get here if already registered, so can't match
            // yourself.
            //with !regconflicts, the above isn't true anymore. Though is handled in it's own processing.
            if (dbP.aliasMatchCrude(ip, mid)) {

                if (option.equals("register")) {
                    m_botAction.sendSmartPrivateMessage(name, "ERROR: One or more names have already been registered into TWD from your computer and/or internet address.");
                    m_botAction.sendSmartPrivateMessage(name, "Please login and reset your old name(s) with !resetname, wait 24 hours, and then !register this name.");
                    m_botAction.sendSmartPrivateMessage(name, "If one of these name(s) do not belong to you or anyone in your house, you may still register, but only with staff approval.  Type ?help <msg> for assistance.");
                    cmd_MIDCheck(name, mid, false);
                    cmd_IPCheck(name, ip, false);
                    return;
                } else if (option.equals("regconflicts")) {
                    m_botAction.sendSmartPrivateMessage(register, "The following names show up as conflicts when trying to register:");
                    cmd_MIDCheck(register, mid, true);
                    cmd_IPCheck(register, ip, true);
                    return;
                } else
                    m_botAction.sendSmartPrivateMessage(register, "WARNING: Another account may have been registered on that connection.");
            } else if (option.equals("regconflicts")) {
                m_botAction.sendSmartPrivateMessage(register, "No conflicts were found for the name " + name);
                return;
            }

            if (!dbP.register(ip, mid)) {
                m_botAction.sendSmartPrivateMessage(register, "Unable to register name.");
                return;
            }
            cmd_AddMIDIP(name, "name:" + name + "  ip:" + ip + "  mid:" + mid);
            m_botAction.sendSmartPrivateMessage(register, "REGISTRATION SUCCESSFUL");
            m_botAction.sendSmartPrivateMessage(register, "NOTE: Only one name per household is allowed to be registered with TWD staff approval.  If you have family members that also play, you must register manually with staff (type ?help <msg>).");
            m_botAction.sendSmartPrivateMessage(register, "Holding two or more name registrations in one household without staff approval may result in the disabling of one or all names registered.");

        } else if (m_requesters != null) {
            String response = name + "  IP:" + ip + "  MID:" + mid;
            String requester = "";
            if (m_requesters.containsKey(name))
                requester = m_requesters.remove(name);
            else
                requester = m_requesters.remove(name.toLowerCase());
            if (requester != null) {
                m_botAction.sendSmartPrivateMessage(requester, response);
                if (locatee.equalsIgnoreCase(name))
                    locatee = "";
                if (!m_botAction.getArenaName().equalsIgnoreCase("TWD")) {
                    TimerTask delay = new TimerTask() {
                        @Override
                        public void run() {
                            m_botAction.changeArena("TWD");
                        }
                    };
                    m_botAction.scheduleTask(delay, 2000);
                }
            }
        }
    }

    public void checkIP(String name, String message) {

        String target = m_botAction.getFuzzyPlayerName(message);
        if (target != null) {
            m_requesters.put(target, name);
            m_botAction.sendUnfilteredPrivateMessage(target, "*info");
        } else {
            m_requesters.put(message.toLowerCase(), name);
            locatee = message.toLowerCase();
            final String n = name;
            final String p = message;
            m_botAction.sendUnfilteredPublicMessage("*locate " + message);
            locater = new TimerTask() {
                @Override
                public void run() {
                    m_botAction.sendSmartPrivateMessage(n, "Could not locate " + p);
                    m_requesters.remove(n.toLowerCase());
                    locatee = "";
                }
            };
            m_botAction.scheduleTask(locater, 2000);
        }
    }

    public boolean passwordIsValid(String password, boolean specialPlayer) {

        boolean digit = false;
        boolean uppercase = false;
        boolean length = false;
        @SuppressWarnings("unused")
        boolean specialcharacter = false;

        for (int i = 0; i < password.length(); i++) {

            if (Character.isDigit(password.charAt(i)))
                digit = true;
            if (Character.isUpperCase(password.charAt(i)))
                uppercase = true;
            if (!Character.isLetterOrDigit(password.charAt(i)))
                specialcharacter = true;
        }

        if (specialPlayer) {
            if (password.length() >= 10)
                length = true;
            return length && digit && uppercase;

        } else {

            if (password.length() >= 8)
                length = true;

            return length && digit;

        }

    }

    public DBPlayerData findPlayerInList(String name) {
        try {
            ListIterator<DBPlayerData> l = m_players.listIterator();
            DBPlayerData thisP;

            while (l.hasNext()) {
                thisP = l.next();
                if (name.equalsIgnoreCase(thisP.getUserName()))
                    return thisP;
            }
            ;
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Error in findPlayerInList.");
        }
    }

    public void checkMessages() {
        try {
            // Improved query: tblMessage is only used for TWD score change
            // messages, so we only need to check for unprocessed msgs
            ResultSet s = m_botAction.SQLQuery(webdb, "SELECT * FROM tblMessage WHERE fnProcessed = 0");
            while (s != null && s.next()) {
                if (s.getString("fcMessageType").equalsIgnoreCase("squad")) {
                    m_botAction.sendSquadMessage(s.getString("fcTarget"), s.getString("fcMessage"), s.getInt("fnSound"));
                    // Delete messages rather than update them as sent, as we
                    // don't need to keep records.
                    // This table is only used to send msgs between the website
                    // and this bot.
                    m_botAction.SQLQueryAndClose(webdb, "DELETE FROM tblMessage WHERE fnMessageID = " + s.getInt("fnMessageID"));
                }
            }
            m_botAction.SQLClose(s);            
            
            ResultSet rs = m_botAction.SQLQuery(webdb, "SELECT * FROM tblTWDAliasRelay");
            String inserts = "";
            Vector<Integer> ids = new Vector<Integer>();
            while (rs.next()) {
                int id = rs.getInt(1);
                String user = rs.getString(2);
                String alias = rs.getString(3);
                java.sql.Timestamp d = rs.getTimestamp(4);
                int type = rs.getInt(5);
                ids.add(id);
                if (aliasRelay)
                    m_botAction.sendChatMessage(3,"[TWD Staff Site] Alias(" + type + ") by " + user + ": " + alias);
                inserts += "('" + Tools.addSlashes(user) + "','" + Tools.addSlashes(alias) + "','" + d.toString() + "'," + type + "), ";
            }
            m_botAction.SQLClose(rs);
            if (!ids.isEmpty()) {
                inserts = "INSERT INTO tblTWDAliasLog (fcUser, fcAlias, fdDate, fnType) VALUES " + inserts.substring(0, inserts.lastIndexOf(","));
                m_botAction.SQLBackgroundQuery(webdb, null, inserts);
                String deletes = "";
                for (Integer i : ids)
                    deletes += i + ",";
                deletes = deletes.substring(0, deletes.lastIndexOf(","));
                m_botAction.SQLQuery(webdb, "DELETE FROM tblTWDAliasRelay WHERE fnID IN (" + deletes + ")");
            }
        } catch (SQLException e) {
            Tools.printStackTrace(e);
        }
        ;
    };

    public void storeSquad(String squad, String owner) {

        try {
            DBPlayerData thisP2 = new DBPlayerData(m_botAction, webdb, owner, false);

            if (thisP2 == null || !thisP2.isRegistered()) {
                m_botAction.sendSmartPrivateMessage(owner, "Your name has not been !registered. Please private message me with !register.");
                return;
            }
            if (!thisP2.isEnabled()) {
                m_botAction.sendSmartPrivateMessage(owner, "Your name has been disabled from TWD.  (This means you may not register a squad.)  Contact a TWDOp with ?help for assistance.");
                return;
            }

            DBPlayerData thisP = new DBPlayerData(m_botAction, webdb, owner, false);

            if (thisP != null)
                if (thisP.getTeamID() == 0) {
                    ResultSet s = m_botAction.SQLQuery(webdb, "select fnTeamID from tblTeam where fcTeamName = '" + Tools.addSlashesToString(squad)
                            + "' and (fdDeleted = 0 or fdDeleted IS NULL)");
                    if (s.next()) {
                        m_botAction.sendSmartPrivateMessage(owner, "That squad is already registered..");
                        m_botAction.SQLClose(s);
                        ;
                        return;
                    } else
                        m_botAction.SQLClose(s);

                    String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                    String fields[] = { "fcTeamName", "fdCreated" };
                    String values[] = { Tools.addSlashesToString(squad), time };
                    m_botAction.SQLInsertInto(webdb, "tblTeam", fields, values);

                    int teamID;

                    // This query gets the latest inserted TeamID from the
                    // database to associate the player with
                    ResultSet s2 = m_botAction.SQLQuery(webdb, "SELECT MAX(fnTeamID) AS fnTeamID FROM tblTeam");
                    if (s2.next()) {
                        teamID = s2.getInt("fnTeamID");
                        m_botAction.SQLClose(s2);
                    } else {
                        m_botAction.sendSmartPrivateMessage(owner, "Database error, contact a TWD Op.");
                        m_botAction.SQLClose(s2);
                        return;
                    }

                    String fields2[] = { "fnUserID", "fnTeamID", "fdJoined", "fnCurrentTeam" };
                    String values2[] = { Integer.toString(thisP.getUserID()), Integer.toString(teamID), time, "1" };
                    m_botAction.SQLInsertInto(webdb, "tblTeamUser", fields2, values2);

                    thisP.giveRank(4);

                    m_botAction.sendSmartPrivateMessage(owner, "The squad " + squad + " has been signed up for TWD.");
                } else
                    m_botAction.sendSmartPrivateMessage(owner, "You must leave your current squad first.");
        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(owner, "Database error, contact a TWD Op.");
        }
    }

    class SquadOwner {
        String owner = "", squad = "";
        int id;

        public SquadOwner(String name, String tSquad, int tID) {
            owner = name;
            squad = tSquad;
            id = tID;
        }

        public String getOwner() {
            return owner;
        }

        public String getSquad() {
            return squad;
        }

        public int getID() {
            return id;
        }
    }

}
