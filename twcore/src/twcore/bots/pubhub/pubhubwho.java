package twcore.bots.pubhub;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.events.SQLResultEvent;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

/**
 * Receive player enter and leave information for EVERY arena. Intelligently
 * update the pubstats db when a player is thought to have logged or logged off.
 * 
 * @author WingZero
 * 
 */
public class pubhubwho extends PubBotModule {

    private final String IPC = "whoonline";
    private final int INTERVAL = 5 * Tools.TimeInMillis.SECOND;

    private String db = "pubstats";
    private boolean status = true;
    public TimerTask doUpdate;

    // up to date list of who is online
    public HashSet<String> online = new HashSet<String>();
    // list of checkout tasks
    public HashMap<String, TimerTask> check = new HashMap<String, TimerTask>();

    /**
     * This method initializes the pubhubwho module. It is called after
     * m_botAction has been initialized.
     * 
     */
    public void initializeModule() {
        status = true;
        sqlReset();
    }

    /**
     * Requests the events.
     */
    public void requestEvents(EventRequester er) {
        er.request(EventRequester.MESSAGE);
    }

    /**
     * Cancel updates.
     */
    public void cancel() {
        status = false;
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

    public void handleEvent(Message event) {
        int type = event.getMessageType();
        String name = event.getMessager();
        String msg = event.getMessage();
        if (name == null || msg == null)
            return;
        if (type == Message.PRIVATE_MESSAGE || type == Message.REMOTE_PRIVATE_MESSAGE) {
            if (msg.startsWith("!online "))
                isOnline(name, msg);
            else if (msg.startsWith("!squad "))
                getSquad(name, msg);
            if (m_botAction.getOperatorList().isSmod(name)) {
                if (msg.equals("!update"))
                    status(name);
                else if (msg.startsWith("!info "))
                    getInfo(name, msg);
                else if (msg.equals("!help"))
                    helpSmod(name);
                else if (msg.equals("!refresh"))
                    resetAll(name);
            } else if (msg.equals("!help"))
                help(name);
        }
    }
    
    public void help(String name) {
        String[] msg = {
                "PUBHUB WHO ONLINE COMMANDS:",
                "!online <name>  - Shows if <name> is currently online according to list on bot",   
                "!squad <squad>  - Lists all the members of <squad> currently online"          
        };
        m_botAction.smartPrivateMessageSpam(name, msg);
    }
    
    public void helpSmod(String name) {
        String[] msg = {
                "PUBHUB WHO ONLINE COMMANDS:",
                "!online <name>  - Shows if <name> is currently online according to list on bot",
                "!squad <squad>  - Lists all the members of <squad> currently online",
                "!update         - Toggles the online status update process on and off",
                "!info <name>    - Shows detailed information from the bot's lists about <name>", 
                "!refresh        - Resets entire database and calls for bots to update all players"               
        };
        m_botAction.smartPrivateMessageSpam(name, msg);
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
        if (queue.contains(name.toLowerCase())) {
            msg = " - QUEUED ";
        }
        if (update.containsKey(name.toLowerCase())) {
            if (update.get(name.toLowerCase()))
                msg += " - UPDATING to ONLINE ";
            else
                msg += " - UPDATING to OFFLINE ";
        }
        */

        if (check.containsKey(name.toLowerCase()))
            msg += " - CheckingOut ";

        if (msg.length() > 0)
            m_botAction.sendSmartPrivateMessage(sender, msg);

    }

    public void handleEvent(InterProcessEvent event) {
        if (!event.getChannel().equals(IPC) || !status)
            return;

        synchronized(event.getObject()) {
            String[] msg = ((IPCMessage) event.getObject()).getMessage().split(":");
            String name = msg[1].toLowerCase();
            if (m_botAction.getOperatorList().isBotExact(name))
                return;
            
            if (msg[0].equals("enter")) {
                if (check.containsKey(name))
                    check.remove(name).cancel();

                m_botAction.SQLBackgroundQuery(db, null, "UPDATE tblPlayer SET fnOnline = 1 WHERE fcName = '" + Tools.addSlashesToString(name) + "'");
                online.add(name);
                /*
                if (check.containsKey(name)) {
                    check.remove(name).cancel();
                    queue.remove(name);
                    update.remove(name);
                } else {
                    update.put(name, true);
                    if (!queue.contains(name))
                        queue.add(name);
                }
                */
            } else {
                if (!check.containsKey(name)) {
                    check.put(name, new CheckOut(name));
                    m_botAction.scheduleTask(check.get(name), 5 * Tools.TimeInMillis.SECOND);
                } else {
                    check.remove(name).cancel();
                    check.put(name, new CheckOut(name));
                    m_botAction.scheduleTask(check.get(name), 5 * Tools.TimeInMillis.SECOND);
                }
                /*
                if (!check.containsKey(name)) {
                    check.put(name, new CheckOut(name));
                    m_botAction.scheduleTask(check.get(name), 5 * Tools.TimeInMillis.SECOND);
                    update.put(name, false);
                } else if (!update.containsKey(name) || update.get(name))
                    update.put(name, false);
                    */
            }
        }
    }
    
    public void resetAll(String name) {
        Iterator<TimerTask> i = check.values().iterator();
        while (i.hasNext()) {
            i.next().cancel();
        }
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
        m_botAction.SQLBackgroundQuery(db, "squad:" + msg + ":" + name, "SELECT fcName FROM tblPlayer WHERE fcSquad = '" + Tools.addSlashesToString(msg) + "' AND fnOnline = 1");
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
