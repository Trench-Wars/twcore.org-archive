package twcore.bots.pubbot;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.PlayerEntered;
import twcore.core.game.Player;
import twcore.core.util.ipc.IPCMessage;

/**
 *  This class handles PlayerEntered events and sends the information to pubhub who then sends appropriate alerts back to pubbot
 *  @author WingZero
 */
public class pubbotgamealert extends PubBotModule {

    String botName;
    
    @Override
    public void initializeModule() {
        botName = m_botAction.getBotName();
    }

    @Override
    public void cancel() {
    }

    @Override
    public void requestEvents( EventRequester eventRequester ){
        eventRequester.request( EventRequester.PLAYER_ENTERED );
    }

    /**
     * This method handles an InterProcess event.
     * @param event is the IPC event to handle.
     */
    public void handleEvent(InterProcessEvent event)
    {
        // If the event.getObject() is anything else then the IPCMessage (pubbotchatIPC f.ex) then return
        if(event.getObject() instanceof IPCMessage == false)
            return;

        IPCMessage ipcMessage = (IPCMessage) event.getObject();
        String message = ipcMessage.getMessage();

        if(message.startsWith("send "))
            sendAlert(message.substring(message.indexOf(' ')+1, message.indexOf(':')), message.substring(message.indexOf(':')+1));
    }
    
    public void handleEvent(PlayerEntered event) {
        String name = m_botAction.getPlayerName(event.getPlayerID());
        Player p = m_botAction.getPlayer(name);
        String squad = p.getSquadName();
        m_botAction.ipcSendMessage(this.getIPCChannel(), "player " + name + ":" + squad, this.getPubHubName(), botName);
    }
    
    public void sendAlert(String name, String msg) {
        m_botAction.sendSmartPrivateMessage(name, msg);
    }
}
