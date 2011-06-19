package twcore.bots.twchat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.TreeMap;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.ArenaList;
import twcore.core.events.FileArrived;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.SQLResultEvent;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCEvent;
import twcore.core.util.ipc.IPCMessage;

public class twchat extends SubspaceBot {

    BotSettings m_botSettings;
    OperatorList ops;
    BotAction ba;
    public ArrayList<String> lastPlayer = new ArrayList<String>();
    public ArrayList<String> show = new ArrayList<String>();
    private static final String IPC = "whoonline";
    private static final String WHOBOT = "TW-WhoBot";
    private static final String db = "pubstats";

    private boolean DEBUG = false;
    private boolean signup = false;
    private boolean notify = false;
    // status of the database update task sqlDump
    private boolean status = false;
    // number of seconds between database updates
    private int delay = 30;
    // updates player database periodically according to delay
    private TimerTask sqlDump;
    
    private String stater = "";
    private long lastUpdate = 0;
    private int saves = 0;

    // up to date list of who is online
    public HashSet<String> online = new HashSet<String>();
    // queue of status updates
    public Map<String, Boolean> updateQueue = Collections.synchronizedMap(new HashMap<String, Boolean>());
    public Map<String, Long> events = Collections.synchronizedMap(new TreeMap<String, Long>());
    public Set<String> outsiders = Collections.synchronizedSet(new HashSet<String>());

    public twchat(BotAction botAction) {
        super(botAction);
        requestEvents();
        ba = m_botAction;
        m_botSettings = m_botAction.getBotSettings();
        ops = m_botAction.getOperatorList();

    }

    public void requestEvents() {
        EventRequester req = m_botAction.getEventRequester();
        req.request(EventRequester.MESSAGE);
        req.request(EventRequester.ARENA_JOINED);
        req.request(EventRequester.ARENA_LIST);
        req.request(EventRequester.LOGGED_ON);
        req.request(EventRequester.FILE_ARRIVED);
        req.request(EventRequester.PLAYER_ENTERED);
        req.request(EventRequester.PLAYER_LEFT);
    }

    /**
     * You must write an event handler for each requested event/packet. This is
     * an example of how you can handle a message event.
     */
    public void handleEvent(Message event) {
        short sender = event.getPlayerID();
        String name = event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ? event.getMessager() : m_botAction.getPlayerName(sender);
        String message = event.getMessage();

        if (message.startsWith("!online "))
            isOnline(name, message);
        else if (message.equalsIgnoreCase("!signup"))
            signup(name, message);
        else if (message.startsWith("!squad "))
            getSquad(name, message);
        else if (message.equalsIgnoreCase("!help"))
            help(name, message);
        else if (message.startsWith("!whohas "))
            whoHas(name, message);
        
        if (ops.isDeveloperExact(name) || ops.isSmod(name)) {
            if (message.startsWith("!delay "))
                setDelay(name, message);
            else if (message.equals("!update"))
                status(name);
            else if (message.startsWith("!info "))
                getInfo(name, message);
            else if (message.equals("!refresh"))
                resetAll(name);
            else if (message.equals("!whosonline"))
                listOnline(name);
            else if (message.equals("!stats"))
                stats(name);
            else if (message.equals("!truncate"))
                truncate(name);
            else if (message.equals("!errors"))
                errors(name);
        }
        
        if (ops.isSmod(name)) {
            if (message.equalsIgnoreCase("!show"))
                show(name, message);
            else if (message.equals("!debug"))
                debug();
            else if (message.equalsIgnoreCase("!toggle"))
                toggle(name, message);
            else if (message.equalsIgnoreCase("!get"))
                test(name, message);
            else if (message.equalsIgnoreCase("!put"))
                put(name, message);
            else if (message.equals("!notify"))
                toggleNotify(name, message);
            else if (message.startsWith("!go "))
                go(name, message);
            else if (message.startsWith("!vipadd "))
                vipadd(name, message);
            else if (message.equalsIgnoreCase("!die"))
                m_botAction.die();
        }

        if (event.getMessageType() == Message.ARENA_MESSAGE) {
            if (message.contains("Client: VIE 1.34") && !notify) {
                String nameFromMessage = message.substring(0, message.indexOf(":", 0));
                if (m_botAction.getOperatorList().isSysopExact(nameFromMessage) && !nameFromMessage.equalsIgnoreCase("Pure_Luck") && !nameFromMessage.equalsIgnoreCase("Witness"))
                    return;
                else
                    m_botAction.sendChatMessage(2, "Non Continuum Client Detected! (" + nameFromMessage + ")");
                    show.add(nameFromMessage.toLowerCase());
            }
        }
    }

    public void handleEvent(FileArrived event) {
        for (int i = 0; i < lastPlayer.size(); i++) {
            if (event.getFileName().equals("vip.txt")) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(m_botAction.getDataFile("vip.txt")));
                    BufferedWriter writer = new BufferedWriter(new FileWriter(m_botAction.getDataFile("vip.txt"), true));

                    reader.readLine();
                    writer.write("\r\n" + lastPlayer.get(i));

                    // writer.write("\n"+name);

                    reader.close();
                    writer.close();

                    m_botAction.putFile("vip.txt");
                    m_botAction.sendSmartPrivateMessage(lastPlayer.get(i), "You have successfully signed up to TWChat!");
                    Tools.printLog("Added player " + lastPlayer.get(i) + " to VIP.txt for TWChat");
                    m_botAction.sendChatMessage("Good Day, I have added " + lastPlayer.get(i) + " to VIP for TWChat.");
                    lastPlayer.remove(i);
                }

                catch (Exception e) {
                    m_botAction.sendChatMessage("Error, Cannot edit VIP.txt for " + lastPlayer.get(i) + " " + e);
                    Tools.printStackTrace(e);
                }

            }
        }
    }

    public void handleEvent(LoggedOn event) {
        m_botAction.joinArena(m_botSettings.getString("Arena"));
        m_botAction.ipcSubscribe(IPC);
    }

    public void handleEvent(SQLResultEvent event) {
        if (!event.getIdentifier().contains(":"))
            return;
        String[] id = event.getIdentifier().split(":");
        String squad = id[1];
        String name = id[2];
        String mems = "";
        int online = 0;
        ResultSet rs = event.getResultSet();
        try {
            if (rs.next()) {
                mems += rs.getString("fcName");
                online++;
                while (rs.next()) {
                    mems += ", " + rs.getString("fcName");
                    online++;
                }
            } else {
                m_botAction.sendSmartPrivateMessage(name, squad + "(" + 0 + "): none found");
                return;
            }
        } catch (SQLException e) {
            Tools.printStackTrace(e);
        }
        m_botAction.SQLClose(rs);
        m_botAction.sendSmartPrivateMessage(name, squad + "(" + online + "): " + mems);
    }

    public void handleEvent(PlayerLeft event) {
        String name = ba.getPlayerName(event.getPlayerID());
        if (name == null)
            return;
        if (show.contains(name.toLowerCase()) && !online.contains(name.toLowerCase()))
            show.remove(name.toLowerCase());
    }

    public void handleEvent(PlayerEntered event) {
        Player player = m_botAction.getPlayer(event.getPlayerID());
        if (ba.getOperatorList().isBotExact(player.getPlayerName()))
            return;
        m_botAction.sendUnfilteredPrivateMessage(player.getPlayerName(), "*einfo");
    }

    public void handleEvent(ArenaJoined event) {
        m_botAction.setReliableKills(1);
        String g = m_botSettings.getString("Chats");
        m_botAction.sendUnfilteredPublicMessage("?chat=" + g);
        update();
        resetAll("WingZero");
    }

    public void handleEvent(InterProcessEvent event) {
        if (!event.getChannel().equals(IPC) || !status)
            return;
        synchronized(event.getObject()) {
            String bug = "";
            if (event.getObject() instanceof IPCEvent) {
                IPCEvent ipc = (IPCEvent) event.getObject();
                int type = ipc.getType();
                long now = System.currentTimeMillis();
                if (DEBUG)
                    bug += "ipc " + (now - ipc.getTime()) + " ms ago";
                if (event.getSenderName().equals(WHOBOT)) {
                    if (type == EventRequester.PLAYER_ENTERED) { 
                        if (!ipc.isAll()) {
                            String name = ipc.getName().toLowerCase();
                            updateQueue.put(name, true);
                            online.add(name);
                            events.put(name, ipc.getTime());
                            outsiders.add(name);
                        } else {
                            HashMap<String, Long> list = (HashMap<String, Long>) ipc.getList();
                            for (String name : list.keySet()) {
                                updateQueue.put(name, true);
                                online.add(name);
                                events.put(name, list.get(name));
                                outsiders.add(name);
                            }
                        }
                    } else if (type == EventRequester.PLAYER_LEFT) {
                        if (!ipc.isAll()) {
                            String name = ipc.getName().toLowerCase();
                            if (ipc.getTime() > events.get(name)) {
                                updateQueue.put(name, false);
                                online.remove(name);
                                events.put(name, ipc.getTime());
                                outsiders.remove(name);
                            }
                        } else {
                            Iterator<String> i = outsiders.iterator();
                            while (i.hasNext()) {
                                String name = i.next();
                                if (ipc.getTime() > events.get(name)) {
                                    updateQueue.put(name, false);
                                    online.remove(name);
                                    events.put(name, ipc.getTime());
                                    i.remove();
                                }
                            }
                        }
                    }
                } else if (!ipc.isAll()) {
                    String name = ipc.getName().toLowerCase();
                    if (ops.isBotExact(name) || (ops.isSysopExact(name) && !name.equalsIgnoreCase("Pure_Luck") && !name.equalsIgnoreCase("Witness")))
                        return;
                    if (type == EventRequester.PLAYER_ENTERED) {
                        if (events.containsKey(name)) {
                            if (ipc.getTime() >= events.get(name)) {
                                updateQueue.put(name, true);
                                events.put(name, ipc.getTime());
                                online.add(name);
                                if (DEBUG)
                                    bug += " for " + name + " enters on time";
                            } else {
                                saves++;
                                if (DEBUG)
                                    ba.sendSmartPrivateMessage("WingZero", "Saved " + name);
                            }
                        } else {
                            updateQueue.put(name, true);
                            events.put(name, ipc.getTime());
                            online.add(name);   
                            if (DEBUG)
                                bug += " for " + name + " enters, new record";                         
                        }
                    } else if (type == EventRequester.PLAYER_LEFT) {
                        if (events.containsKey(name)) {
                            if (ipc.getTime() > events.get(name)) {
                                updateQueue.put(name, false);
                                events.put(name, ipc.getTime());
                                online.remove(name);
                                if (DEBUG)
                                    bug += " for " + name + " left on time";
                            } else {
                                saves++;
                                if (DEBUG)
                                    ba.sendSmartPrivateMessage("WingZero", "Saved " + name);
                            }
                        } else {
                            updateQueue.put(name, false);
                            events.put(name, ipc.getTime());
                            online.remove(name);
                            if (DEBUG)
                                bug += " for " + name + " left, new record";
                        }
                    }
                } else {
                    if (type == EventRequester.PLAYER_ENTERED) {
                        if (DEBUG)
                            bug += " for bot entered new arena.";
                        Iterator<Player> i = (Iterator<Player>)ipc.getList();
                        while (i.hasNext()) {
                            String name = i.next().getPlayerName().toLowerCase();
                            if (!ops.isBotExact(name) && !(ops.isSysopExact(name) && !name.equalsIgnoreCase("Pure_Luck") && !name.equalsIgnoreCase("Witness"))) {
                                if (events.containsKey(name)) {
                                    if (ipc.getTime() >= events.get(name)) {
                                        updateQueue.put(name, true);
                                        events.put(name, ipc.getTime());
                                        online.add(name);      
                                    } else {
                                        saves++;
                                        if (DEBUG)
                                            ba.sendSmartPrivateMessage("WingZero", "Saved " + name);
                                    }
                                } else {
                                    updateQueue.put(name, false);
                                    events.put(name, ipc.getTime());
                                    online.remove(name);                                
                                }
                            }
                        }
                    } else if (type == EventRequester.PLAYER_LEFT) {
                        if (DEBUG)
                            bug += " for bot left arena.";
                        Iterator<Player> i = (Iterator<Player>)ipc.getList();
                        while (i.hasNext()) {
                            String name = i.next().getPlayerName().toLowerCase();
                            if (!ops.isBotExact(name) && !(ops.isSysopExact(name) && !name.equalsIgnoreCase("Pure_Luck") && !name.equalsIgnoreCase("Witness"))) {
                                if (events.containsKey(name)) {
                                    if (ipc.getTime() > events.get(name)) {
                                        updateQueue.put(name, false);
                                        events.put(name, ipc.getTime());
                                        online.remove(name);        
                                    } else {
                                        saves++;
                                        if (DEBUG)
                                            ba.sendSmartPrivateMessage("WingZero", "Saved " + name);
                                    }
                                } else {
                                    updateQueue.put(name, false);
                                    events.put(name, ipc.getTime());
                                    online.remove(name);                                 
                                }
                            }
                        }
                    }
                }
                if (DEBUG)
                    ba.sendSmartPrivateMessage("WingZero", bug);
                return;                
            }
            
            
            /*
            String[] msg = ((IPCMessage) event.getObject()).getMessage().split(":");
            String name = msg[1].toLowerCase();
            if (m_botAction.getOperatorList().isBotExact(name))
                return;
            if (msg[0].equals("enter")) {
                updateQueue.put(name, true);
                online.add(name);
            } else if (msg[0].equals("left")) {
                updateQueue.put(name, false);
                online.remove(name);
            }
            */
        }
    }
    
    public void handleEvent(ArenaList event) {
        if (stater.length() < 1)
            return;
        String msg = "";
        int pop = 0;
        Map<String, Integer> arenas = event.getArenaList();
        for (String a : arenas.keySet()) {
            if (!a.contains("#"))
                pop += arenas.get(a);
        }
        msg += "Pub Pop=" + pop + " | Online=" + online.size();
        String query = "SELECT COUNT(DISTINCT fcName) as c FROM tblPlayer WHERE fnOnline = 1";
        try {
            ResultSet rs = ba.SQLQuery(db, query);
            if (rs.next())
                pop = rs.getInt("c");
            ba.SQLClose(rs);
        } catch (SQLException e) {
            pop = -1;
        }
        msg += " | Database=" + pop + " | Queued=" + updateQueue.size() + " | Events=" + events.size() + " | Saves: " + saves + " | Last update " + (System.currentTimeMillis() - lastUpdate) + " ms ago";
        ba.sendSmartPrivateMessage(stater, msg);
        stater = "";
    }

    private void help(String name, String message) {
        String[] startCommands =
                { "+-------------------------------------------------------------------------------+",
                        "|                                 Trench Wars Chat                              |",
                        "|                                                                               |",
                        "| Hello! I'm a bot that will allow you to chat on the web!                      |",
                        "| I also have the ability to look for online squad players!                     |",
                        "| Please look below for the available commands.                                 |" };
        String[] publicCommands =
                { "|                                                                               |",
                        "| !signup                     - Signs you up to be able to use the online TW    |",
                        "|                               Chat App                                        |",
                        "|-------------------------------------------------------------------------------|",
                        "|                                Who Is Online                                  |",
                        "|                                                                               |",
                        "| !squad <squad>  - Lists all the members of <squad> currently online           |",
                        "| !whohas <#>     - Lists all the squads who have <#> or more members online    |",
                        "| !online <name>  - Shows if <name> is currently online according to list on bot|",
                        "|                                                                               |", };
        String[] modCommands =
                {

                "|------------------------------- TWChat SMod+ ----------------------------------|",
                        "|                                                                               |",
                        "| !test                       - Retrieves the VIP text file from the server to  |",
                        "|                               be accurate where it is placed.                 |",
                        "| !die                        - Throw me off a bridge without a parachute       |",
                        "| !vipadd                     - Manually add this person to VIP.                |",
                        "| !go <arena>                 - I'll go to the arena you specify.               |",
                        "| !show                       - Show people online using TWChat App             |",
                        "| !toggle                     - Disables/Enables ability to !signup             |",
                        "| !notify                     - Toggles chat notify (stops !show)               |",
                        "| !put                        - Force putfile VIP.txt                           |",
                        "|-------------------------------------------------------------------------------|",
                        "|                                Who Is Online (SMod)                           |",
                        "|                                                                               |",
                        "| !update         - Toggles the online status update process on and off         |",
                        "| !info <name>    - Shows detailed information from the bot's lists about <name>|",
                        "| !delay <sec>    - Sets the delay between updates in seconds and restarts task |",
                        "| !stats          - Displays population and player online status information    |",
                        "| !errors         - Displays the inconsistencies between bot list and db list   |",
                        "| !whosonline     - Lists every single player found in the online list          |",
                        "| !refresh        - Resets entire database & calls for bots to update players   |",
                        "| !truncate       - Shrinks the events tree in case it gets large               |", };
        String[] devCommands = {

                "|-------------------------------------------------------------------------------|",
                "|                                Who Is Online (Dev)                            |",
                "|                                                                               |",
                "| !update         - Toggles the online status update process on and off         |",
                "| !info <name>    - Shows detailed information from the bot's lists about <name>|",
                "| !delay <sec>    - Sets the delay between updates in seconds and restarts task |",
                "| !stats          - Displays population and player online status information    |",
                "| !errors         - Displays the inconsistencies between bot list and db list   |",
                "| !whosonline     - Lists every single player found in the online list          |",
                "| !refresh        - Resets entire database & calls for bots to update players   |",
                "| !truncate       - Shrinks the events tree in case it gets large               |" };
        String[] endCommands = { "\\-------------------------------------------------------------------------------/" };

        m_botAction.smartPrivateMessageSpam(name, startCommands);
        m_botAction.smartPrivateMessageSpam(name, publicCommands);

        if (m_botAction.getOperatorList().isSmod(name)) {
            m_botAction.smartPrivateMessageSpam(name, modCommands);
        } else if (ops.isDeveloper(name))
            m_botAction.smartPrivateMessageSpam(name, devCommands);

        m_botAction.smartPrivateMessageSpam(name, endCommands);

    }
    
    public void debug() {
        DEBUG = !DEBUG;
        if (DEBUG)
            ba.sendSmartPrivateMessage("WingZero", "DEBUG ENABLED!");
        else
            ba.sendSmartPrivateMessage("WingZero", "DEBUG DISABLED!");
    }

    private void put(String name, String message) {
        m_botAction.putFile("vip.txt");
        m_botAction.sendSmartPrivateMessage(name, "Done.");
        
    }

    public void vipadd(String name, String message) {
        m_botAction.getServerFile("vip.txt");
        String vip = message.substring(8).toLowerCase();
        lastPlayer.add(vip);
        m_botAction.sendSmartPrivateMessage(name, "Done.");
    }

    public void go(String name, String message) {
        String go = message.substring(4);
        m_botAction.changeArena(go);

    }

    public void test(String name, String message) {
        m_botAction.sendSmartPrivateMessage(name, "Test complete, Gotten VIP.TXT");
        m_botAction.getServerFile("vip.txt");
       
    }

    public void show(String name, String message) {
        String people = "";
        m_botAction.sendSmartPrivateMessage(name, "People ONLINE using TW Chat App:");
        Iterator<String> list = show.iterator();
        if (!list.hasNext())
            m_botAction.sendSmartPrivateMessage(name, "No-one! :(");

        for (int k = 0; list.hasNext();) {

            String pName = (String) list.next();
            if (m_botAction.getOperatorList().isSysop(pName))
                people += pName + " (SysOp), ";
            else if (m_botAction.getOperatorList().isSmodExact(pName))
                people += pName + " (SMod), ";
            else
                people += pName + ", ";
            k++;
            if (k % 10 == 0 || !list.hasNext()) {
                if (people.length() > 2) {
                    m_botAction.sendSmartPrivateMessage(name, people.substring(0, people.length() - 2));
                    people = "";
                }
            }
        }
    }

    public void signup(String name, String message) {
        if (!signup)
            m_botAction.sendSmartPrivateMessage(name, "You cannot signup to TWChat at this time.");
        else
            m_botAction.getServerFile("vip.txt");
        name = name.toLowerCase();
        lastPlayer.add(name);

    }

    public void toggle(String name, String message) {
        if (signup) {
            signup = false;
            m_botAction.sendSmartPrivateMessage(name, "Signup DEACTIVATED");
        } else {
            signup = true;
            m_botAction.sendSmartPrivateMessage(name, "Signup ACTIVATED");
        }
    }
    
    public void toggleNotify(String name, String message) {
        if (notify) {
            notify = false;
            m_botAction.sendSmartPrivateMessage(name, "Notify DEACTIVATED");
        } else {
            notify = true;
            m_botAction.sendSmartPrivateMessage(name, "Notify ACTIVATED");
        }
    }

    public void status(String name) {
        if (status) {
            ba.cancelTask(sqlDump);
            status = false;
            m_botAction.sendSmartPrivateMessage(name, "Player online status update process STOPPED.");
        } else {
            update();
            m_botAction.sendSmartPrivateMessage(name, "Player online status update process STARTED.");
        }
    }
    
    public void listOnline(String name) {
        if (online.size() < 150) {
            String msg = "ONLINE: ";
            for (String p : online) {
                msg += p + ", ";
                if (msg.length() > 200) {
                    ba.sendSmartPrivateMessage(name, msg.substring(0, msg.length() - 2));
                    msg = "ONLINE: ";
                }
            }
            if (msg.length() > 9)
                ba.sendSmartPrivateMessage(name, msg);
        } else {
            ba.sendSmartPrivateMessage(name, "Online list is too big to display.");
        }
    }

    public void isOnline(String sender, String msg) {
        String name = msg.substring(msg.indexOf(" ") + 1);
        if (name == null || name.length() < 1)
            return;

        if (online.contains(name.toLowerCase()))
            m_botAction.sendSmartPrivateMessage(sender, name + ": ONLINE");
        else
            m_botAction.sendSmartPrivateMessage(sender, name + ": OFFLINE");
    }

    public void getInfo(String sender, String message) {
        String name = message.substring(message.indexOf(" ") + 1);
        String msg = name + ": ";
        if (online.contains(name.toLowerCase()))
            m_botAction.sendSmartPrivateMessage(sender, msg + "ONLINE");
        else
            m_botAction.sendSmartPrivateMessage(sender, msg + "OFFLINE");

        msg = "";
        if (updateQueue.containsKey(name.toLowerCase())) {
            if (updateQueue.get(name.toLowerCase()))
                msg += "- QUEUED for ONLINE update";
            else
                msg += "- QUEUED for OFFLINE update";
            ba.sendSmartPrivateMessage(sender, msg);
        }

    }

    public void resetAll(String name) {
        updateQueue.clear();
        online.clear();
        sqlReset();
        final String p = name;
        TimerTask call = new TimerTask() {
            public void run() {
                m_botAction.sendSmartPrivateMessage(p, "Commencing reset...");
                m_botAction.ipcTransmit(IPC, new IPCMessage("who:refresh"));
            }
        };
        m_botAction.scheduleTask(call, 3000);
    }

    public void getSquad(String name, String msg) {
        msg = msg.substring(7);
        if (msg.length() < 1)
            return;
        m_botAction.SQLBackgroundQuery(db, "squad:" + msg + ":" + name,
                "SELECT fcName FROM tblPlayer WHERE fcSquad = '" + Tools.addSlashesToString(msg) + "' AND fnOnline = 1");
    }

    public void sqlReset() {
        try {
            m_botAction.SQLQueryAndClose(db, "UPDATE tblPlayer SET fnOnline = 0");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public void setDelay(String name, String cmd) {
        try {
            int t = Integer.valueOf(cmd.substring(cmd.indexOf(" ")+1));
            delay = t;
            update();
            ba.sendSmartPrivateMessage(name, "Database update rate set to " + delay + " seconds.");
        } catch (NumberFormatException e) {
            ba.sendSmartPrivateMessage(name, "Error processing request: " + cmd);
        }
    }
    
    public void update() {
        if (status)
            ba.cancelTask(sqlDump);
        
        sqlDump = new TimerTask() {
            public void run() {
                lastUpdate = System.currentTimeMillis();
                if (updateQueue.isEmpty())
                    return;
                String on = "(";
                String off = "(";
                synchronized(updateQueue) {
                    Iterator<String> i = updateQueue.keySet().iterator();
                    while (i.hasNext()) {
                        String name = i.next();
                        if (updateQueue.get(name))
                            on += "'" + Tools.addSlashesToString(name) + "',";
                        else
                            off += "'" + Tools.addSlashesToString(name) + "',";
                        i.remove();
                    }
                }
                on = on.substring(0, on.length()-1);
                on += ")";
                off = off.substring(0, off.length()-1);
                off += ")";
                String query = "";
                if (on.length() > 2) {
                    query = "UPDATE tblPlayer SET fnOnline = 1 WHERE fcName IN " + on;
                    ba.SQLBackgroundQuery(db, null, query);
                }
                if (off.length() > 2) {
                    query = "UPDATE tblPlayer SET fnOnline = 0 WHERE fcName IN " + off;
                    ba.SQLBackgroundQuery(db, null, query);
                }
                
            }
        };
        status = true;
        ba.scheduleTask(sqlDump, 1000, delay * Tools.TimeInMillis.SECOND);
    }
    
    private void stats(String name) {
        stater = name;
        ba.requestArenaList();
    }
    
    private void truncate(String name) {
        int size = events.size();
        long now = System.currentTimeMillis();
        Iterator<String> i = events.keySet().iterator();
        while (i.hasNext()) {
            String key = i.next();
            if (now - events.get(key) > 60*Tools.TimeInMillis.SECOND)
                i.remove();
        }
        ba.sendSmartPrivateMessage(name, "" + size + " event mappings reduced to " + events.size());
    }
    
    private void errors(String name) {
        if (online.isEmpty()) {
            ba.sendSmartPrivateMessage(name, "Online list empty.");
            return;
        }
        String on = "(";
        for (String n : online)
            on += "'" + n + "',";
        on = on.substring(0, on.lastIndexOf(',')) + ")";
        String msg = "Inconsistencies: ";
        String query = "SELECT fcName FROM tblPlayer WHERE fcName NOT IN " + on + " AND fnOnline = 1";
        try {
            ResultSet rs = ba.SQLQuery(db, query);
            String n = "";
            if (rs.next()) {
                n = rs.getString("fcName");
                if (updateQueue.containsKey(n.toLowerCase()))
                    n += "(Q)";
                msg += n;
                while (rs.next()) {
                    if (updateQueue.containsKey(n.toLowerCase()))
                        n += "(Q)";
                    msg += ", " + n;
                }
            }
            ba.sendSmartPrivateMessage(name, msg);
        } catch (SQLException e) {
            ba.sendSmartPrivateMessage(name, "SQL error.");
            Tools.printStackTrace(e);
        }
    }
    
    private void whoHas(String name, String cmd) {
        if (cmd.indexOf(" ") < 0 || cmd.length() < 9)
            return;
        try {
            int x = Integer.valueOf(cmd.substring(cmd.indexOf(" ")+1));
            if (x < 2) {
                ba.sendSmartPrivateMessage(name, "Number of players too small.");
                return;
            }
            String result = "Squads with (" + x + "+): ";
            String query = "SELECT fcSquad, COUNT(fcSquad) as c FROM tblPlayer WHERE fnOnline = 1 GROUP BY fcSquad ORDER BY c DESC LIMIT 25";
            ResultSet rs = ba.SQLQuery(db, query);
            while (rs.next()) {
                int c = rs.getInt("c");
                if (c >= x) {
                    result += rs.getString("fcSquad") + "(" + c + ") ";
                } else {
                    break;
                }
            }
            ba.SQLClose(rs);
            ba.sendSmartPrivateMessage(name, result);            
        } catch (SQLException e) {
            Tools.printStackTrace(e);
        } catch (NumberFormatException e) {      
        }
    }
}
