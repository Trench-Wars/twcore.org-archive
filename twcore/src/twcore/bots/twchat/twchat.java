package twcore.bots.twchat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FileArrived;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.SQLResultEvent;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

public class twchat extends SubspaceBot {

    BotSettings m_botSettings;
    private String info = "";
    public ArrayList<String> lastPlayer = new ArrayList<String>();
    public ArrayList<String> show = new ArrayList<String>();
    private final String IPC = "whoonline";
    private final int INTERVAL = 5 * Tools.TimeInMillis.SECOND;

    private String db = "pubstats";
    private boolean status = true;
    public TimerTask doUpdate;

    // up to date list of who is online
    public HashSet<String> online = new HashSet<String>();
    // list of checkout tasks
    public HashMap<String, TimerTask> check = new HashMap<String, TimerTask>();

    public twchat(BotAction botAction) {
        super(botAction);
        requestEvents();

        m_botSettings = m_botAction.getBotSettings();

    }

    public void requestEvents() {
        EventRequester req = m_botAction.getEventRequester();
        req.request(EventRequester.MESSAGE);
        req.request(EventRequester.ARENA_JOINED);
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
        if (event.getMessageType() == Message.ARENA_MESSAGE) {

            // Received from a *info
            if (message.contains("Client: VIE 1.34")) {
                if (m_botAction.getOperatorList().isBotExact(info))
                    return;
                else
                    m_botAction.sendChatMessage("Non Continuum Client Detected! (" + info + ")");
                show.add(info.toLowerCase());

                if (message.startsWith("Not online")) {
                    show.remove(info.toLowerCase());
                }
                if(event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE){
                    
                if (message.equalsIgnoreCase("!signup"))
                    signup(name, message);

                if (message.startsWith("!online "))
                    isOnline(name, message);

                if (message.startsWith("!squad "))
                    getSquad(name, message);

                if (message.equalsIgnoreCase("!help"))
                    help(name, message);

                if (m_botAction.getOperatorList().isSmod(name)) {

                    if (message.equalsIgnoreCase("!show"))
                        show(name, message);

                    if (message.equalsIgnoreCase("!test"))
                        test(name, message);

                    if (message.equals("!update"))
                        status(name);

                    if (message.startsWith("!info "))
                        getInfo(name, message);

                    if (message.equals("!refresh"))
                        resetAll(name);

                    if (message.startsWith("!go "))
                        go(name, message);

                    if (message.startsWith("!vipadd "))
                        vipadd(name, message);

                    if (message.equalsIgnoreCase("!die"))
                        m_botAction.die();
                }
            }
        }
        }
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
                        "|                               Chat App (DISABLED)                             |",
                        "\\------------------------------------------------------------------------------/",
                        "|                                Who Is Online                                  |",
                        "|                                                                               |",
                        "| !online <name>  - Shows if <name> is currently online according to list on bot|",
                        "| !squad <squad>  - Lists all the members of <squad> currently online           |", };
        String[] modCommands =
                { "|------------------------------- TWChat SMod+ ----------------------------------|",
                        "| !test                       - Retrieves the VIP text file from the server to  |",
                        "|                               be accurate where it is placed.                 |",
                        "| !die                        - Throw me off a bridge without a parachute       |",
                        "| !vipadd                     - Manually add this person to VIP.                |",
                        "| !go <arena>                 - I'll go to the arena you specify.               |",
                        "| !show                       - Show people online using TWChat App             |",
                        "|-------------------------------------------------------------------------------|",
                        "|                                Who Is Online (SMod)                           |",
                        "|                                                                               |",
                        "| !online <name>  - Shows if <name> is currently online according to list on bot|",
                        "| !squad <squad>  - Lists all the members of <squad> currently online           |",
                        "| !update         - Toggles the online status update process on and off         |",
                        "| !info <name>    - Shows detailed information from the bot's lists about <name>|",
                        "| !refresh        - Resets entire database & calls for bots to update players   |", };
        String[] endCommands = { "\\-------------------------------------------------------------------------------/" };

        m_botAction.smartPrivateMessageSpam(name, startCommands);
        m_botAction.smartPrivateMessageSpam(name, publicCommands);

        if (m_botAction.getOperatorList().isSmod(name)) {
            m_botAction.smartPrivateMessageSpam(name, modCommands);
        }

        m_botAction.smartPrivateMessageSpam(name, endCommands);

    }

    private void vipadd(String name, String message) {
        m_botAction.getServerFile("vip.txt");
        String vip = message.substring(8).toLowerCase();
        lastPlayer.add(vip);
        m_botAction.sendSmartPrivateMessage(name, "Done.");
    }

    private void go(String name, String message) {
        String go = message.substring(4);
        m_botAction.changeArena(go);

    }

    private void test(String name, String message) {
        m_botAction.sendSmartPrivateMessage(name, "Test complete, Gotten VIP.TXT");
        m_botAction.getServerFile("vip.txt");
    }

    private void show(String name, String message) {
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

    private void signup(String name, String message) {
        m_botAction.getServerFile("vip.txt");
        name = name.toLowerCase();
        lastPlayer.add(name);

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
        status = true;
        sqlReset();
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

    public void handleEvent(PlayerLeft Event) {
        m_botAction.sendUnfilteredPublicMessage("?find " + info);

    }

    public void handleEvent(PlayerEntered event) {
        Player player = m_botAction.getPlayer(event.getPlayerID());
        m_botAction.sendUnfilteredPrivateMessage(player.getPlayerName(), "*einfo");
        info = player.getPlayerName();
    }

    public void handleEvent(ArenaJoined event) {
        m_botAction.setReliableKills(1);
        m_botAction.sendUnfilteredPublicMessage("?chat=robodev");
    }

    public void status(String name) {
        if (status) {
            cancel();
            m_botAction.sendSmartPrivateMessage(name, "Player online status update process STOPPED.");
        } else {
            status = true;
            m_botAction.sendSmartPrivateMessage(name, "Player online status update process STARTED.");
        }
    }

    private void cancel() {
        status = false;
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
        /*
         * if (queue.contains(name.toLowerCase())) { msg = " - QUEUED "; } if
         * (update.containsKey(name.toLowerCase())) { if
         * (update.get(name.toLowerCase())) msg += " - UPDATING to ONLINE ";
         * else msg += " - UPDATING to OFFLINE "; }
         */

        if (check.containsKey(name.toLowerCase()))
            msg += " - CheckingOut ";

        if (msg.length() > 0)
            m_botAction.sendSmartPrivateMessage(sender, msg);

    }

    public void handleEvent(InterProcessEvent event) {
        if (!event.getChannel().equals(IPC) || !status)
            return;

        synchronized (event.getObject()) {
            String[] msg = ((IPCMessage) event.getObject()).getMessage().split(":");
            String name = msg[1].toLowerCase();
            if (m_botAction.getOperatorList().isBotExact(name))
                return;

            if (msg[0].equals("enter")) {
                if (check.containsKey(name))
                    m_botAction.cancelTask(check.remove(name));
                ;

                m_botAction.SQLBackgroundQuery(db, null, "UPDATE tblPlayer SET fnOnline = 1 WHERE fcName = '" + Tools.addSlashesToString(name) + "'");
                online.add(name);
                /*
                 * if (check.containsKey(name)) { check.remove(name).cancel();
                 * queue.remove(name); update.remove(name); } else {
                 * update.put(name, true); if (!queue.contains(name))
                 * queue.add(name); }
                 */
            } else {
                if (!check.containsKey(name)) {
                    check.put(name, new CheckOut(name));
                    m_botAction.scheduleTask(check.get(name), 5 * Tools.TimeInMillis.SECOND);
                } else {
                    m_botAction.cancelTask(check.remove(name));
                    check.put(name, new CheckOut(name));
                    m_botAction.scheduleTask(check.get(name), 5 * Tools.TimeInMillis.SECOND);
                }
                /*
                 * if (!check.containsKey(name)) { check.put(name, new
                 * CheckOut(name)); m_botAction.scheduleTask(check.get(name), 5
                 * * Tools.TimeInMillis.SECOND); update.put(name, false); } else
                 * if (!update.containsKey(name) || update.get(name))
                 * update.put(name, false);
                 */
            }
        }
    }

    public void resetAll(String name) {
        Iterator<TimerTask> i = check.values().iterator();
        while (i.hasNext())
            m_botAction.cancelTask(check.remove(i.next()));
        online.clear();
        sqlReset();
        TimerTask call = new TimerTask() {
            public void run() {
                m_botAction.ipcTransmit(IPC, new IPCMessage("who:refresh"));
            }
        };
        m_botAction.scheduleTask(call, 4000);
        m_botAction.sendSmartPrivateMessage(name, "Commencing reset...");
    }

    public void getSquad(String name, String msg) {
        msg = msg.substring(7);
        if (msg.length() < 1)
            return;
        m_botAction.SQLBackgroundQuery(db, "squad:" + msg + ":" + name, "SELECT fcName FROM tblPlayer WHERE fcSquad = '"
                + Tools.addSlashesToString(msg) + "' AND fnOnline = 1");
    }

    public void sqlReset() {
        try {
            m_botAction.SQLQueryAndClose(db, "UPDATE tblPlayer SET fnOnline = 0");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public class CheckOut extends TimerTask {
        String name;

        public CheckOut(String name) {
            this.name = name.toLowerCase();
        }

        @Override
        public void run() {
            check.remove(name);
            m_botAction.SQLBackgroundQuery(db, null, "UPDATE tblPlayer SET fnOnline = 0 WHERE fcName = '" + Tools.addSlashesToString(name) + "'");
            online.remove(name);
        }
    }
}
