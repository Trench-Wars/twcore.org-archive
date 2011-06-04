package twcore.bots.pubhub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
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
    public ArrayList<String> online = new ArrayList<String>();
    // simple filo list for sql updates
    public Vector<String> queue = new Vector<String>();
    // ties in with queue for what to update to
    public HashMap<String, Boolean> update = new HashMap<String, Boolean>();
    // list of checkout tasks
    public HashMap<String, TimerTask> check = new HashMap<String, TimerTask>();

    /**
     * This method initializes the pubhubwho module. It is called after
     * m_botAction has been initialized.
     * 
     */
    public void initializeModule() {
        doUpdate = new TimerTask() {
            public void run() {
                update();
            }
        };
        m_botAction.scheduleTask(doUpdate, 2000, INTERVAL);
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
        doUpdate.cancel();
        status = false;
    }

    public void handleEvent(Message event) {
        int type = event.getMessageType();
        String name = event.getMessager();
        String msg = event.getMessage();
        if (name == null || msg == null)
            return;
        if (type == Message.PRIVATE_MESSAGE || type == Message.REMOTE_PRIVATE_MESSAGE) {
            if (m_botAction.getOperatorList().isSmod(name)) {
                if (msg.equals("!update"))
                    status(name);
                else if (msg.startsWith("!online "))
                    isOnline(name, msg);
                else if (msg.startsWith("!info "))
                    getInfo(name, msg);
                else if (msg.equals("!help"))
                    help(name);
            }
        }
    }
    
    public void help(String name) {
        String[] msg = {
                "PUBHUB WHO ONLINE COMMANDS:",
                "!update         - Toggles the online status update process on and off",
                "!online <name>  - Shows if <name> is currently online according to list on bot",
                "!info <name>    - Shows detailed information from the bot's lists about <name>"                
        };
        m_botAction.smartPrivateMessageSpam(name, msg);
    }

    public void status(String name) {
        if (status) {
            cancel();
            m_botAction.sendSmartPrivateMessage(name, "Player online status update process STOPPED.");
        } else {
            status = true;
            initializeModule();
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
        if (queue.contains(name.toLowerCase())) {
            msg = " - QUEUED ";
        }
        if (update.containsKey(name.toLowerCase())) {
            if (update.get(name.toLowerCase()))
                msg += " - UPDATING to ONLINE ";
            else
                msg += " - UPDATING to OFFLINE ";
        }

        if (check.containsKey(name.toLowerCase()))
            msg += " - CheckingOut ";

        if (msg.length() > 0)
            m_botAction.sendSmartPrivateMessage(sender, msg);

    }

    public void handleEvent(InterProcessEvent event) {
        if (!event.getChannel().equals(IPC))
            return;

        String[] msg = ((IPCMessage) event.getObject()).getMessage().split(":");
        String name = msg[1].trim().toLowerCase();

        if (msg[0].equals("enter")) {
            if (check.containsKey(name))
                m_botAction.cancelTask(check.remove(name));

            if (update.containsKey(name)) {
                if (!update.get(name)) {
                    update.remove(name);
                    queue.removeElement(name);
                }
            } else {
                update.put(name, true);
                queue.add(name);
            }

            if (!online.contains(name))
                online.add(name);
        } else {
            if (!check.containsKey(name)) {
                TimerTask left = new CheckOut(name);
                check.put(name, left);
                m_botAction.scheduleTask(left, 3 * Tools.TimeInMillis.SECOND);
            }
        }
    }

    public void update() {
        while (status && !queue.isEmpty()) {
            String n = queue.remove(0);
            String query = "";
            if (update.remove(n))
                query = "UPDATE tblPlayer SET fnOnline = 1 WHERE fcName = '" + Tools.addSlashesToString(n) + "'";
            else
                query = "UPDATE tblPlayer SET fnOnline = 0 WHERE fcName = '" + Tools.addSlashesToString(n) + "'";

            m_botAction.SQLBackgroundQuery(db, null, query);
        }
    }

    public class CheckOut extends TimerTask {
        String name;
        String nl;

        public CheckOut(String name) {
            this.name = name;
            nl = name.toLowerCase();
        }

        @Override
        public void run() {
            check.remove(nl);
            online.remove(nl);
            if (update.containsKey(nl)) {
                if (update.get(nl)) {
                    update.remove(nl);
                    queue.removeElement(nl);
                } else if (!queue.contains(nl))
                    queue.add(nl);
            } else {
                update.put(nl, false);
                if (!queue.contains(nl))
                    queue.add(nl);
            }
        }
    }
}
