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
import twcore.core.util.ipc.IPCEvent;
import twcore.core.util.ipc.IPCMessage;

public class pubbotwho extends PubBotModule {

    protected final String IPC = "whoonline";
    
    HashMap<String, Who> who = new HashMap<String, Who>();
    
    boolean debug = false;

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
                debug();
            }
        }

    }
    
    private void debug() {
        debug = !debug;
        if (debug)
            m_botAction.sendSmartPrivateMessage("WingZero", "Debug ON");
        else
            m_botAction.sendSmartPrivateMessage("WingZero", "Debug OFF");
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
            m_botAction.sendSmartPrivateMessage("WingZero", "enter event for: " + p.getPlayerName());

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
                m_botAction.scheduleTask(who.put(name, new Who(p.getPlayerName(), System.currentTimeMillis())), 2000);
            } catch (IllegalStateException e) {
                m_botAction.sendSmartPrivateMessage("WingZero", "IllegalStateException on: " + name);
            } catch (NullPointerException e) {
                m_botAction.sendSmartPrivateMessage("WingZero", "NullPointerException on: " + (name != null ? name : "null"));
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
            m_botAction.sendSmartPrivateMessage("WingZero", "left event for: " + p.getPlayerName());

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
