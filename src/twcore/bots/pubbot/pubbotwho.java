package twcore.bots.pubbot;

import java.util.Iterator;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.ArenaJoined;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.util.ipc.IPCMessage;

public class pubbotwho extends PubBotModule {

    protected final String IPC = "whoonline";

    public void initializeModule() {
        m_botAction.ipcSubscribe(IPC);
    }

    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.PLAYER_LEFT);
        eventRequester.request(EventRequester.ARENA_JOINED);
    }

    public void handleEvent(ArenaJoined event) {
        Iterator<Player> i = m_botAction.getPlayerIterator();
        while (i.hasNext())
            m_botAction.ipcTransmit(IPC, new IPCMessage("enter:" + i.next().getPlayerName()));
    }

    public void handleEvent(PlayerEntered event) {
        Player p = m_botAction.getPlayer(event.getPlayerID());

        // ignore bots & nulls
        if (p == null || m_botAction.getOperatorList().isBotExact(p.getPlayerName()))
            return;

        // ignore players when biller is down
        if (p.getPlayerName().startsWith("^"))
            return;

        m_botAction.ipcTransmit(IPC, new IPCMessage("enter:" + p.getPlayerName()));
    }

    public void handleEvent(PlayerLeft event) {
        Player p = m_botAction.getPlayer(event.getPlayerID());

        // ignore bots & nulls
        if (p == null || m_botAction.getOperatorList().isBotExact(p.getPlayerName()))
            return;

        // ignore players when biller is down
        if (p.getPlayerName().startsWith("^"))
            return;

        m_botAction.ipcTransmit(IPC, new IPCMessage("left:" + p.getPlayerName()));
    }

    @Override
    public void cancel() {
    }
}
