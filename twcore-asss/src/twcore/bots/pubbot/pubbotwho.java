package twcore.bots.pubbot;

//import java.util.Iterator;
import java.util.HashMap;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.ArenaJoined;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.InterProcessEvent;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCEvent;
import twcore.core.util.ipc.IPCMessage;

public class pubbotwho extends PubBotModule {

    protected final String IPC = "whoonline";
    
    HashMap<String, Who> who = new HashMap<String, Who>();
    
    boolean debug = false;
    String debuggee = "WingZero";

    public void initializeModule() {
        m_botAction.ipcSubscribe(IPC);
    }

    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.PLAYER_LEFT);
        eventRequester.request(EventRequester.ARENA_JOINED);
        eventRequester.request(EventRequester.MESSAGE);
    }
    
    public void handleEvent(Message event) {
        String name = m_botAction.getPlayerName(event.getPlayerID());
        String msg = event.getMessage();
        int type = event.getMessageType();
        if (name == null || msg == null)
            return;
        if (type == Message.PRIVATE_MESSAGE || type == Message.REMOTE_PRIVATE_MESSAGE) {
            if (m_botAction.getOperatorList().isSmod(name) && msg.equals("!debug")) {
                debug(name);
            }
        }

    }
    
    private void debug(String name) {
        debug = !debug;
        if (debug)
            debuggee = name;
        
        m_botAction.sendSmartPrivateMessage(debuggee, "Debug " + (debug?"ON":"OFF"));
    }

    public void handleEvent(ArenaJoined event) {
        /*
        Iterator<Player> i = m_botAction.getPlayerIterator();
        while (i.hasNext())
            m_botAction.ipcTransmit(IPC, new IPCEvent(p.getPlayerName(), System.currentTimeMillis(), EventRequester.PLAYER_ENTERED));
        */
        m_botAction.ipcTransmit(IPC, new IPCEvent(m_botAction.getPlayerIterator(), System.currentTimeMillis(), EventRequester.PLAYER_ENTERED));
    }

    public void handleEvent(PlayerEntered event) {
        Player p = m_botAction.getPlayer(event.getPlayerID());

        // ignore bots & nulls
        if (p == null || m_botAction.getOperatorList().isBotExact(p.getPlayerName()))
            return;

        // ignore players when biller is down
        if (p.getPlayerName().startsWith("^"))
            return;
        
        if (debug)
            m_botAction.sendSmartPrivateMessage(debuggee, "enter event for: " + p.getPlayerName());

        //m_botAction.ipcTransmit(IPC, new IPCMessage("enter:" + p.getPlayerName()));
        String name = p.getPlayerName().toLowerCase();
        
        // Has player left and then re-entered before their TimerTask has fired? If so, update time
        if (who.containsKey(name)) {
        	Who temp = who.get(name);
        	if (temp != null)
        		temp.setTime( System.currentTimeMillis() );
            //m_botAction.cancelTask(who.remove(name));
        } else {
            try {
                Who newWho = new Who(p.getPlayerName(), System.currentTimeMillis());
                who.put(name, newWho);
                m_botAction.scheduleTask(newWho, 2000);
                
                //who.put(name, new Who(p.getPlayerName(), System.currentTimeMillis()));
                //m_botAction.scheduleTask(who.get(name), 2000);
            } catch (IllegalStateException e) {
                m_botAction.sendChatMessage(1, "[ERROR] I think I became unresponsive. Please contact a dev or restart me.");
                Tools.printLog("IllegalStateException on: " + name + " in pubbotwho");
                //Tools.printStackTrace(e);
            } catch (NullPointerException e) {
                Tools.printLog("NullPointerException on: " + (name != null ? name : "null") + "," + (p != null ? "p OK" : "p is null") + "," + (p.getPlayerName() != null ? "getPlayerName OK" : "getPlayerName is null") + "," + (who.get(name) != null ? "who.get OK" : "who.get is null") );
            }
        }
    }

    public void handleEvent(PlayerLeft event) {
        Player p = m_botAction.getPlayer(event.getPlayerID());

        // ignore bots & nulls
        if (p == null || m_botAction.getOperatorList().isBotExact(p.getPlayerName()))
            return;

        // ignore players when biller is down
        if (p.getPlayerName().startsWith("^"))
            return;
        
        if (debug)
            m_botAction.sendSmartPrivateMessage(debuggee, "left event for: " + p.getPlayerName());

        //m_botAction.ipcTransmit(IPC, new IPCMessage("left:" + p.getPlayerName()));
        String name = p.getPlayerName().toLowerCase();
        if (who.containsKey(name))
            m_botAction.cancelTask(who.remove(name));
        
        m_botAction.ipcTransmit(IPC, new IPCEvent(p.getPlayerName(), System.currentTimeMillis(), EventRequester.PLAYER_LEFT));
    }
    
    public void handleEvent(InterProcessEvent event) {
        if (!event.getChannel().equals(IPC) || !(event.getObject() instanceof IPCMessage))
            return;
        
        if (((IPCMessage) event.getObject()).getMessage().equals("who:refresh")) {
            /*
            Iterator<Player> i = m_botAction.getPlayerIterator();
            while (i.hasNext())
                m_botAction.ipcTransmit(IPC, new IPCMessage("enter:" + i.next().getPlayerName()));
            */        
            m_botAction.ipcTransmit(IPC, new IPCEvent(m_botAction.getPlayerIterator(), System.currentTimeMillis(), EventRequester.PLAYER_ENTERED));
            
        }
    }
    
    public void doDie() {
        /*
        Iterator<Player> i = m_botAction.getPlayerIterator();
        while (i.hasNext())
            m_botAction.ipcTransmit(IPC, new IPCMessage("left:" + i.next().getPlayerName()));
        */
        m_botAction.ipcTransmit(IPC, new IPCEvent(m_botAction.getPlayerIterator(), System.currentTimeMillis(), EventRequester.PLAYER_LEFT));
        
        
    }

    @Override
    public void cancel() {
    }
    
    class Who extends TimerTask {
        
        String name;    // Player name
        long time;      // Time at which they entered arena  (TODO: verify that this is actually used?
                        //                                          Its run() uses System's current time)
        
        public Who(String name, long time) {
            this.name = name;
            this.time = time;
        }

        @Override
        public void run() {
            who.remove(name.toLowerCase());
            m_botAction.ipcTransmit(IPC, new IPCEvent(name, System.currentTimeMillis(), EventRequester.PLAYER_ENTERED));
        }
        
        public void setTime( long time ) {
        	this.time = time;
        }
        
    }
}
