package twcore.bots.pubbot;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.game.Player;
import twcore.core.util.ipc.IPCMessage;

/**
 *  This class handles PlayerEntered events and sends the information to pubhub who then sends appropriate alerts back to pubbot
 *  @author WingZero
 */
public class pubbotgamealert extends PubBotModule {

    String botName;
    boolean debug = false;
    
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
        eventRequester.request( EventRequester.MESSAGE );
    }

    /**
     * This method handles an InterProcess event.
     *
     * @param event is the IPC event to handle.
     */
    public void handleEvent(InterProcessEvent event)
    {
        // If the event.getObject() is anything else then the IPCMessage (pubbotchatIPC f.ex) then return
        if(event.getObject() instanceof IPCMessage == false)
            return;

        IPCMessage ipcMessage = (IPCMessage) event.getObject();
        String message = ipcMessage.getMessage();
        
        if (debug)
            relayMessage(message);

        try {
            if(message.startsWith("send "))
                sendAlert(message.substring(message.indexOf(' ')+1, message.indexOf(':')), message.substring(message.indexOf(':')+1));
        }
        catch(Exception e) {
            m_botAction.sendChatMessage(e.getMessage());
        }
    }
    
    
    public void handleEvent(PlayerEntered event) {
        String name = m_botAction.getPlayerName(event.getPlayerID());
        Player p = m_botAction.getPlayer(name);
        String squad = p.getSquadName();
        m_botAction.ipcSendMessage(getIPCChannel(), "player " + name + ":" + squad, null, botName);
    }
    
    public void handleEvent(Message event) {
        int type = event.getMessageType();
        String msg = event.getMessage();
        String name = getSender(event);
        if ((type == Message.PRIVATE_MESSAGE || type == Message.REMOTE_PRIVATE_MESSAGE) && "WingZero".equalsIgnoreCase(name) && msg.equalsIgnoreCase(msg))
            setDebug(name);
    }
    
    public void setDebug(String name) {
        if (!debug) {
            debug = true;
            m_botAction.sendSmartPrivateMessage(name, "Debug mode enabled.");
        }
        else {
            debug = false;
            m_botAction.sendSmartPrivateMessage(name, "Debug mode disabled.");
        }
    }
    
    public void relayMessage(String msg) {
        m_botAction.sendSmartPrivateMessage("WingZero", msg);
    }
    
    public void sendAlert(String name, String msg) {
        m_botAction.sendSmartPrivateMessage(name, msg);
    }

    private String getSender(Message event)
    {
        int messageType = event.getMessageType();

        if(messageType == Message.REMOTE_PRIVATE_MESSAGE || messageType == Message.CHAT_MESSAGE)
            return event.getMessager();
        int senderID = event.getPlayerID();
        return m_botAction.getPlayerName(senderID);
    }
}
