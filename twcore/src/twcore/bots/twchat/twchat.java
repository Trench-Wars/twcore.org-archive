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
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

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
    public HashMap<String, Boolean> tstates = new HashMap<String, Boolean>();
    public LinkedList<String> locates = new LinkedList<String>();
    private static final String IPC = "whoonline";
    private static final String WHOBOT = "WhoBot";
    private static final String db = "pubstats";
    private static final String CORE = "TWCore";
    private static final String ECORE = "TWCore-Events";
    private static final String LCORE = "TWCore-League";

    private String squadInfo = "";
    private String debugger = "";
    private boolean DEBUG = false;
    public boolean signup = false;
    public boolean notify = false;
    // status of the database update task sqlDump
    private boolean status = false;
    // number of seconds between database updates
    private int delay = 20;
    // updates player database periodically according to delay
    private TimerTask sqlDump;
    private TimerTask locater;
    private TimerTask nocore;

    private String stater = "";
    private long lastUpdate = 0;

    private boolean countBots = false;
    private int botCount = 0;

    // up to date list of who is online
    public HashSet<String> online = new HashSet<String>();
    // queue of status updates
    public Map<String, Boolean> updateQueue = Collections.synchronizedMap(new HashMap<String, Boolean>());
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
        String name = event.getMessager();
        if (name == null || name.length() < 1) 
            name = m_botAction.getPlayerName(event.getPlayerID());
        String message = event.getMessage();
        int type = event.getMessageType();

        if (type == Message.ARENA_MESSAGE) {
            if (message.contains("Client: VIE 1.34") && notify == true) {
                String nameFromMessage = message.substring(0, message.indexOf(":", 0));
                if (m_botAction.getOperatorList().isSysopExact(nameFromMessage) && !nameFromMessage.equalsIgnoreCase("Pure_Luck")
                        && !nameFromMessage.equalsIgnoreCase("Witness"))
                    return;
                else
                    m_botAction.sendChatMessage(2, "Non Continuum Client Detected! (" + nameFromMessage + ")");
                if (!show.equals(nameFromMessage.toLowerCase())) {
                    show.add(nameFromMessage.toLowerCase());
                }
                if (message.startsWith("Not online")) {
                    for (int i = 0; i < show.size(); i++) {
                        show.remove(i);

                    }
                }
            } else if (squadInfo.length() > 1 && message.contains(" - ")) {
                ba.cancelTask(locater);
                debug(message);
                String p = message.substring(0, message.lastIndexOf(" - "));
                String arena = message.substring(message.lastIndexOf("- ") + 2);
                if (locates.contains(p.toLowerCase())) {
                    locates.remove(p.toLowerCase());
                    if (arena.contains("#") || arena.contains("afk"))
                        tstates.put(p, true);
                    else
                        tstates.put(p, false);
                }
                
                if (!locates.isEmpty())
                    locate();
                else {
                    debug("locates is empty, building info msg...");
                    String s = squadInfo.substring(squadInfo.indexOf(":")+1);
                    String n = squadInfo.substring(0, squadInfo.indexOf(":"));
                    String msg = "";
                    if (!tstates.isEmpty()) {
                        msg += s + "(" + tstates.size() + "): ";
                        for (String pl : tstates.keySet()) {
                            msg += "" + pl;
                            if (tstates.get(pl))
                                msg += "*";
                            msg += ", ";
                        }
                        msg = msg.substring(0, msg.length()-2);
                    } else {
                        debug("tstates was empty...");
                        msg += s + "(0): None found.";
                    }
                    m_botAction.sendSmartPrivateMessage(n, msg);
                    locates.clear();
                    tstates.clear();
                    squadInfo = "";
                }
            }
        }
        
        if (type == Message.REMOTE_PRIVATE_MESSAGE || type == Message.PRIVATE_MESSAGE) {

            if (countBots && message.startsWith("Total: ")) {
                if (name.equals(CORE)) {
                    m_botAction.cancelTask(nocore);
                    botCount = 1;
                    debug("Received: " + message + " from " + name);
                    botCount += Integer.valueOf(message.substring(message.indexOf(" ") + 1));
                    ba.sendSmartPrivateMessage(ECORE, "!totalbots");
                    nocore = new TimerTask() {
                        public void run() {
                            debug("No response from " + ECORE);
                            ba.sendSmartPrivateMessage(LCORE, "!totalbots");                            
                        }
                    };
                    ba.scheduleTask(nocore, 2000);                    
                } else if (name.equals(ECORE)) {
                    m_botAction.cancelTask(nocore);
                    debug("Received: " + message + " from " + name);
                    botCount++;
                    botCount += Integer.valueOf(message.substring(message.indexOf(" ") + 1));
                    ba.sendSmartPrivateMessage(LCORE, "!totalbots");
                    nocore = new TimerTask() {
                        public void run() {
                            debug("No response from " + LCORE);
                            ba.requestArenaList();                          
                        }
                    };
                    ba.scheduleTask(nocore, 2000);   
                } else if (name.equals(LCORE)) {
                    debug("Received: " + message + " from " + name);
                    botCount++;
                    botCount += Integer.valueOf(message.substring(message.indexOf(" ") + 1));
                    ba.requestArenaList();
                }
            }

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
            else if (message.equals("!stats")) 
                stats(name);

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
                else if (message.equals("!errors"))
                    errors(name);
                else if (message.equals("!outsiders"))
                    outsiders(name);
                else if (message.equals("!debug"))
                    debugger(name);
                else if (message.startsWith("!dev")) 
                    deviates(name);
            }

            if (ops.isSmod(name)) {
                if (message.equalsIgnoreCase("!show"))
                    show(name, message);
                else if (message.startsWith("!si "))
                    getSquadInfo(name, message);
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
                else if (message.equalsIgnoreCase("!recal"))
                    recalculate(name);
                else if (message.equalsIgnoreCase("!die")) m_botAction.die();
            }
        }
    }

    public void handleEvent(FileArrived event) {
        for (int i = 0; i < lastPlayer.size(); i++) {
            if (event.getFileName().equals("vip.txt") && !signup) {
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
        if (!event.getIdentifier().contains(":")) return;
        String[] id = event.getIdentifier().split(":");
        boolean afk = false;
        if (id[0].startsWith("squadINFO"))
            afk = true;
        String squad = id[1];
        String name = id[2];
        String mems = "";
        int online = 0;
        ResultSet rs = event.getResultSet();
        try {
            if (rs.next()) {
                if (!afk) {
                    mems += rs.getString("fcName");
                    online++;
                    while (rs.next()) {
                        mems += ", " + rs.getString("fcName");
                        online++;
                    }
                } else {
                    String p = rs.getString("fcName").toLowerCase();
                    locates.add(p);
                    while (rs.next()) {
                        p = rs.getString("fcName").toLowerCase();
                        locates.add(p);
                    }
                }
            } else {
                m_botAction.sendSmartPrivateMessage(name, squad + "(" + 0 + "): None found.");
                if (afk) {
                    locates.clear();
                    tstates.clear();
                    squadInfo = "";
                }
                return;
            }
        } catch (SQLException e) {
            Tools.printStackTrace(e);
        }
        m_botAction.SQLClose(rs);
        if (!afk)
            m_botAction.sendSmartPrivateMessage(name, squad + "(" + online + "): " + mems);
    }

    public void handleEvent(PlayerLeft event) {
        String name = ba.getPlayerName(event.getPlayerID());
        if (name == null) return;
        if (show.contains(name.toLowerCase()) && !online.contains(name.toLowerCase())) show.remove(name.toLowerCase());
    }

    public void handleEvent(PlayerEntered event) {
        Player player = m_botAction.getPlayer(event.getPlayerID());
        if (ba.getOperatorList().isBotExact(player.getPlayerName())) return;
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
        if (!event.getChannel().equals(IPC) || !status) return;
        synchronized (event.getObject()) {
            String bug = "ipc:";
            if (event.getObject() instanceof IPCEvent) {
                IPCEvent ipc = (IPCEvent) event.getObject();
                int type = ipc.getType();
                if (event.getSenderName().equals(WHOBOT)) {
                    bug += "WhoBot sends ";
                    if (type == EventRequester.PLAYER_ENTERED) {
                        if (!ipc.isAll()) {
                            bug += ipc.getName() + " entering ";
                            String name = ipc.getName().toLowerCase();
                            updateQueue.put(name, true);
                            online.add(name);
                            outsiders.add(name);
                        } else {
                            bug += "mass entrance list ";
                            @SuppressWarnings("unchecked")
                            HashMap<String, Long> list = (HashMap<String, Long>) ipc.getList();
                            for (String name : list.keySet()) {
                                updateQueue.put(name, true);
                                online.add(name);
                                outsiders.add(name);
                            }
                        }
                    } else if (type == EventRequester.PLAYER_LEFT) {
                        if (!ipc.isAll()) {
                            bug += ipc.getName() + " leaving ";
                            String name = ipc.getName().toLowerCase();
                            updateQueue.put(name, false);
                            online.remove(name);
                            outsiders.remove(name);
                        } else {
                            bug += "mass exit ";
                            Iterator<String> i = outsiders.iterator();
                            while (i.hasNext()) {
                                String name = i.next();
                                updateQueue.put(name, false);
                                online.remove(name);
                                i.remove();
                            }
                        }
                    } else if (type == EventRequester.PLAYER_POSITION) {
                        @SuppressWarnings("unchecked")
                        Set<String> who = (Set<String>) ipc.getList();
                        Set<String> twc = outsiders;
                        String[] msg = { "Deviates(TWC): ", "Deviates(WHO): " };
                        for (String p : twc)
                            if (!who.contains(p)) msg[0] += p + ", ";
                        msg[0] = msg[0].substring(0, msg[0].length() - 2);
                        for (String p : who)
                            if (!twc.contains(p)) msg[1] += p + ", ";
                        msg[1] = msg[1].substring(0, msg[1].length() - 2);
                        ba.smartPrivateMessageSpam(stater, msg);
                        stater = "";
                    } else if (type == EventRequester.MESSAGE) {
                        String name = ipc.getName();
                        String cmd = name.substring(name.indexOf(":") + 1);
                        name = name.substring(0, name.indexOf(":"));

                        if (cmd.startsWith("!squad "))
                            getSquad(name, cmd);
                        else if (cmd.startsWith("!whohas "))
                            whoHas(name, cmd);
                        else if (cmd.equalsIgnoreCase("!stats"))
                            stats(name);
                        else if (cmd.equalsIgnoreCase("!help"))
                            help(name, cmd);
                    }
                } else if (!ipc.isAll()) {
                    String name = ipc.getName().toLowerCase();
                    if (ops.isBotExact(name) || (ops.isSysopExact(name) && !name.equalsIgnoreCase("Pure_Luck") && !name.equalsIgnoreCase("Witness")))
                        return;
                    if (type == EventRequester.PLAYER_ENTERED) {
                        updateQueue.put(name, true);
                        online.add(name);
                        outsiders.remove(name);
                        bug += "" + name + " online";
                    } else if (type == EventRequester.PLAYER_LEFT) {
                        updateQueue.put(name, false);
                        online.remove(name);
                        outsiders.remove(name);
                        bug += "" + name + " offline";
                    }
                } else {
                    if (type == EventRequester.PLAYER_ENTERED) {
                        bug += "bot entered new arena.";
                        @SuppressWarnings("unchecked")
                        Iterator<Player> i = (Iterator<Player>) ipc.getList();
                        while (i.hasNext()) {
                            String name = i.next().getPlayerName().toLowerCase();
                            if (!ops.isBotExact(name)
                                    && !(ops.isSysopExact(name) && !name.equalsIgnoreCase("Pure_Luck") && !name.equalsIgnoreCase("Witness"))) {
                                updateQueue.put(name, true);
                                online.add(name);
                                outsiders.remove(name);
                            }
                        }
                    } else if (type == EventRequester.PLAYER_LEFT) {
                        bug += "bot left arena.";
                        @SuppressWarnings("unchecked")
                        Iterator<Player> i = (Iterator<Player>) ipc.getList();
                        while (i.hasNext()) {
                            String name = i.next().getPlayerName().toLowerCase();
                            if (!ops.isBotExact(name)
                                    && !(ops.isSysopExact(name) && !name.equalsIgnoreCase("Pure_Luck") && !name.equalsIgnoreCase("Witness"))) {
                                updateQueue.put(name, false);
                                online.remove(name);
                                outsiders.remove(name);
                            }
                        }
                    }
                }
                debug(bug);
                return;
            } else if (countBots && event.getObject() instanceof IPCMessage) {
                IPCMessage ipc = (IPCMessage) event.getObject();
                if (ipc.getRecipient().equals(m_botAction.getBotName())) {
                    if (ipc.getMessage().equals("countit")) botCount++;
                }
            }
        }
    }

    public void handleEvent(ArenaList event) {
        if (stater.length() < 1) return;
        String msg = "";
        int pop = 0;
        Map<String, Integer> arenas = event.getArenaList();
        for (String a : arenas.keySet())
            pop += arenas.get(a);
        msg += "Pop=" + (pop - botCount) + " | Online=" + online.size() + " | Outsiders=" + outsiders.size() + " | Bots=" + botCount;
        String query = "SELECT COUNT(DISTINCT fcName) as c FROM tblPlayer WHERE fnOnline = 1";
        try {
            ResultSet rs = ba.SQLQuery(db, query);
            if (rs.next()) pop = rs.getInt("c");
            ba.SQLClose(rs);
        } catch (SQLException e) {
            pop = -1;
        }
        msg += " | Database=" + pop + " | Queued=" + updateQueue.size() + " | Last update " + (System.currentTimeMillis() - lastUpdate)/1000 + " sec ago";
        ba.sendSmartPrivateMessage(stater, msg);
        stater = "";
        countBots = false;
    }

    private void help(String name, String message) {
        String[] startCommands = { 
                        "+-------------------------------------------------------------------------------+",
                        "|                                 Trench Wars Chat                              |",
                        "|                                                                               |",
                        "| Hello! I'm a bot that will allow you to chat on the web!                      |",
                        "| I also have the ability to look for online squad players!                     |",
                        "| Please look below for the available commands.                                 |" };
        String[] publicCommands = { 
                        "|                                                                               |",
                        "| !signup                     - Signs you up to be able to use the online TW    |",
                        "|                               Chat App                                        |",
                        "|-------------------------------------------------------------------------------|",
                        "|                                Who Is Online                                  |",
                        "|                                                                               |",
                        "| !whohas <#>     - Lists all the squads who have <#> or more members online    |",
                        "| !squad <squad>  - Lists all the members of <squad> currently online           |",
                        "| !online <name>  - Shows if <name> is currently online according to list on bot|",
                        "| !stats          - Displays population and player online status information    |",
                        "|                                                                               |", };
        String[] modCommands = {
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
                        "| !recal                      - Recalculate people online or off on TWChat      |",
                        "|-------------------------------------------------------------------------------|",
                        "|                                Who Is Online (SMod)                           |",
                        "|                                                                               |",
                        "| !update         - Toggles the online status update process on and off         |",
                        "| !info <name>    - Shows detailed information from the bot's lists about <name>|",
                        "| !delay <sec>    - Sets the delay between updates in seconds and restarts task |",
                        "| !errors         - Displays the inconsistencies between bot list and db list   |",
                        "| !deviates       - Compares outsiders list to WhoBot's and returns deviations  |",
                        "| !whosonline     - Lists every single player found in the online list          |",
                        "| !refresh        - Resets entire database & calls for bots to update players   |", };
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
                        "| !refresh        - Resets entire database & calls for bots to update players   |", };
        String[] endCommands = { "\\-------------------------------------------------------------------------------/" };

        m_botAction.smartPrivateMessageSpam(name, startCommands);
        m_botAction.smartPrivateMessageSpam(name, publicCommands);

        if (m_botAction.getOperatorList().isSmod(name)) {
            m_botAction.smartPrivateMessageSpam(name, modCommands);
        } else if (ops.isDeveloper(name)) m_botAction.smartPrivateMessageSpam(name, devCommands);

        m_botAction.smartPrivateMessageSpam(name, endCommands);

    }

    private void stats(String name) {
        stater = name;
        countBots = true;
        debug("Calculating stats...");
        ba.sendSmartPrivateMessage(CORE, "!totalbots");
        nocore = new TimerTask() {
            public void run() {
                debug("No response from " + CORE);
                ba.sendSmartPrivateMessage(ECORE, "!totalbots");
            }
        };
        ba.scheduleTask(nocore, 2500);
    }

    private void deviates(String name) {
        stater = name;
        ba.ipcTransmit(IPC, new IPCMessage("who:deviates", WHOBOT));
    }

    private void put(String name, String message) {
        m_botAction.putFile("vip.txt");
        m_botAction.sendSmartPrivateMessage(name, "Done.");

    }

    private void recalculate(String name) {
        Iterator<String> list = show.iterator();
        if (!list.hasNext()) m_botAction.sendSmartPrivateMessage(name, "No-one is online!");

        String pName = (String) list.next();
        m_botAction.sendUnfilteredPublicMessage("?find " + pName);
        m_botAction.sendSmartPrivateMessage(name, "Recalculated.");

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
        if (!list.hasNext()) m_botAction.sendSmartPrivateMessage(name, "No-one! :(");

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
        if (signup == false) {
            m_botAction.sendSmartPrivateMessage(name, "You cannot signup to TWChat at this time.");
        } else {
            m_botAction.getServerFile("vip.txt");
            name = name.toLowerCase();
            lastPlayer.add(name);

        }
    }

    public void toggle(String name, String message) {
        if (signup == false) {
            signup = true;
            m_botAction.sendSmartPrivateMessage(name, "Signup ACTIVATED");
        } else {
            signup = false;
            m_botAction.sendSmartPrivateMessage(name, "Signup DEACTIVATED");
        }
    }

    public void toggleNotify(String name, String message) {
        if (notify == false) {
            notify = true;
            m_botAction.sendSmartPrivateMessage(name, "Notify ACTIVATED");
        } else {
            notify = false;
            m_botAction.sendSmartPrivateMessage(name, "Notify DEACTIVATED");
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
            if (msg.length() > 9) ba.sendSmartPrivateMessage(name, msg);
        } else {
            ba.sendSmartPrivateMessage(name, "Online list is too big to display.");
        }
    }

    public void isOnline(String sender, String msg) {
        String name = msg.substring(msg.indexOf(" ") + 1);
        if (name == null || name.length() < 1) return;

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
        if (msg.length() < 1) return;
        m_botAction.SQLBackgroundQuery(db, "squad:" + msg + ":" + name,
                "SELECT fcName FROM tblPlayer WHERE fcSquad = '" + Tools.addSlashesToString(msg) + "' AND fnOnline = 1");
    }
    
    public void getSquadInfo(String name, String msg) {
        if (msg.length() < 1) return;
        if (squadInfo.length() > 1) {
            ba.sendSmartPrivateMessage(name, "Command being used by someone else, please try again in a moment...");
            return;
        }
        msg = msg.substring(msg.indexOf(" ") + 1);
        squadInfo = name + ":" + msg;
        m_botAction.sendSmartPrivateMessage(name, "Processing squad list for: " + msg + " (* means potentially afk)");
        m_botAction.SQLBackgroundQuery(db, "squadINFO:" + msg + ":" + name,
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
            int t = Integer.valueOf(cmd.substring(cmd.indexOf(" ") + 1));
            delay = t;
            update();
            ba.sendSmartPrivateMessage(name, "Database update rate set to " + delay + " seconds.");
        } catch (NumberFormatException e) {
            ba.sendSmartPrivateMessage(name, "Error processing request: " + cmd);
        }
    }

    public void update() {
        if (status) ba.cancelTask(sqlDump);

        sqlDump = new TimerTask() {
            public void run() {
                lastUpdate = System.currentTimeMillis();
                if (updateQueue.isEmpty()) return;
                String on = "(";
                String off = "(";
                synchronized (updateQueue) {
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
                on = on.substring(0, on.length() - 1);
                on += ")";
                off = off.substring(0, off.length() - 1);
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

    private void errors(String name) {
        if (online.isEmpty()) {
            ba.sendSmartPrivateMessage(name, "Online list empty.");
            return;
        }
        String on = "(";
        for (String n : online)
            on += "'" + Tools.addSlashesToString(n) + "',";
        on = on.substring(0, on.lastIndexOf(',')) + ")";
        String msg = "Inconsistencies: ";
        String query = "SELECT fcName FROM tblPlayer WHERE fcName NOT IN " + on + " AND fnOnline = 1";
        try {
            ResultSet rs = ba.SQLQuery(db, query);
            String n = "";
            if (rs.next()) {
                n = rs.getString("fcName");
                if (updateQueue.containsKey(n.toLowerCase())) n += "(Q)";
                msg += n;
                while (rs.next()) {
                    if (updateQueue.containsKey(n.toLowerCase())) n += "(Q)";
                    msg += ", " + n;
                }
            }

            ba.SQLClose(rs);
            ba.sendSmartPrivateMessage(name, msg);
        } catch (SQLException e) {
            ba.sendSmartPrivateMessage(name, "SQL error.");
            Tools.printStackTrace(e);
        }
    }

    private void outsiders(String name) {
        String msg = "Outsiders: ";
        for (String n : outsiders)
            msg += n + ", ";
        ba.sendSmartPrivateMessage(name, msg.substring(0, msg.length() - 2));
    }

    private void whoHas(String name, String cmd) {
        if (cmd.indexOf(" ") < 0 || cmd.length() < 9) return;
        try {
            int x = Integer.valueOf(cmd.substring(cmd.indexOf(" ") + 1));
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

    private boolean isNotBot(String name) {
        if (ops.isBotExact(name)
                || (!ops.isOwner(name) && ops.isSysopExact(name) && !name.equalsIgnoreCase("Pure_Luck") && !name.equalsIgnoreCase("Witness")))
            return false;
        else
            return true;
    }

    private void debugger(String name) {
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
    
    private void locate() {
        final String loc = locates.get(0);
        TimerTask delay = new TimerTask() {
            public void run() {
                debug("Locating " + loc);
                m_botAction.sendUnfilteredPublicMessage("*locate " + loc);
                locater = new TimerTask() {
                    public void run() {
                        locates.remove(loc.toLowerCase());
                        if (!locates.isEmpty())
                            locate();
                        else {
                            m_botAction.sendSmartPrivateMessage(squadInfo.substring(0, squadInfo.indexOf(":")), squadInfo.substring(squadInfo.indexOf(":") + 1) + "(0): None found.");
                            squadInfo = "";
                            tstates.clear();
                            locates.clear();
                        }
                    }
                };
                ba.scheduleTask(locater, 1500);
            }
        };
        ba.scheduleTask(delay, 2500);
    }

    public void debug(String msg) {
        if (DEBUG) ba.sendSmartPrivateMessage(debugger, "[DEBUG] " + msg);
    }
}
