package twcore.bots.pubbot;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.PlayerEntered;
import twcore.core.game.Player;
// import twcore.core.util.ipc.IPCMessage;
// import twcore.core.events.InterProcessEvent;

/**
 *  This class handles PlayerEntered events and sends the information to pubhub who then sends appropriate alerts back to pubbot
 *  @author WingZero
 */
public class pubbotgamealert extends PubBotModule {

    @Override
    public void initializeModule() {
    }

    @Override
    public void cancel() {
    }

    @Override
    public void requestEvents( EventRequester eventRequester ){
        eventRequester.request( EventRequester.PLAYER_ENTERED );
    }
    
    public void handleEvent(PlayerEntered event) {
        String name = m_botAction.getPlayerName(event.getPlayerID());
        Player p = m_botAction.getPlayer(name);
        String squad = p.getSquadName();
        if (squad.length() > 0)
            m_botAction.ipcTransmit("TWDInfo", "twdplayer " + name + ":" + squad);
    }
}
