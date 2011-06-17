package twcore.bots.pubbot;

import java.util.Iterator;

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
    boolean notify = false;

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
        String name = event.getMessager();
        String msg = event.getMessage();
        if (name == null || msg == null)
            return;
        if (m_botAction.getOperatorList().isSmod(name) && msg.equals("!notifyme"))
            notify = !notify;
        
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
        if (notify)
            m_botAction.sendSmartPrivateMessage("WingZero", "enter event for: " + event.getPlayerName());

        // ignore players when biller is down
        if (p.getPlayerName().startsWith("^"))
            return;

        //m_botAction.ipcTransmit(IPC, new IPCMessage("enter:" + p.getPlayerName()));
        
        m_botAction.ipcTransmit(IPC, new IPCEvent(p.getPlayerName(), System.currentTimeMillis(), EventRequester.PLAYER_ENTERED));
    }

    public void handleEvent(PlayerLeft event) {
        Player p = m_botAction.getPlayer(event.getPlayerID());

        // ignore bots & nulls
        if (p == null || m_botAction.getOperatorList().isBotExact(p.getPlayerName()))
            return;

        // ignore players when biller is down
        if (p.getPlayerName().startsWith("^"))
            return;

        //m_botAction.ipcTransmit(IPC, new IPCMessage("left:" + p.getPlayerName()));
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
}
