package twcore.bots.basicbot;

import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.*;
import twcore.core.util.IPCMessage;
import twcore.core.events.InterProcessEvent;

public class basicbot extends SubspaceBot {
    
    private BotSettings m_botSettings;
    
    /** Creates a new instance of ultrabot */
    public basicbot(BotAction botAction) {
        super(botAction);
        requestEvents();
        m_botSettings = m_botAction.getBotSettings();
    }
    
    
    /** Request events that this bot requires to receive.  */
    public void requestEvents() {
        EventRequester req = m_botAction.getEventRequester();
        req.request(EventRequester.MESSAGE);
        req.request(EventRequester.LOGGED_ON);
    }
    
    
    public void handleEvent(Message event) {
    	if(event.getMessageType() == Message.CHAT_MESSAGE) {
    		String message = event.getMessage();
    		String sender = event.getMessager();
    		int chatNumber = event.getChatNumber();
    		String sendMessage = sender + "> " + message;
    		if(chatNumber == 1) {
    			if(m_botAction.getOperatorList().isZH(sender)) {
    				IPCMessage sendIPCMessage = new IPCMessage(sendMessage, "staff", m_botAction.getBotName());
    				m_botAction.ipcTransmit("crosszones", sendIPCMessage);
    			}
    		} else if(chatNumber == 2) {
    			IPCMessage sendIPCMessage = new IPCMessage(sendMessage, "twdev", m_botAction.getBotName());
    			m_botAction.ipcTransmit("crosszones", sendIPCMessage);
    		}
    	}
        
    }
    
    public void handleEvent(LoggedOn event) {
        m_botAction.joinArena(m_botSettings.getString("arena"));
        m_botAction.sendUnfilteredPublicMessage("?chat=staff,twdev");
        m_botAction.sendUnfilteredPublicMessage("?chat=staff,twdev");
        m_botAction.ipcSubscribe("crosszones");
    }
    
    public void handleEvent(InterProcessEvent event) {
    	IPCMessage message = (IPCMessage)event.getObject();
    	if(message.getSender().equals(m_botAction.getBotName())) return;
    	if(message.getRecipient().equals("staff")) m_botAction.sendChatMessage(1, message.getMessage());
    	else if(message.getRecipient().equals("twdev")) m_botAction.sendChatMessage(2, mesage.getMessage());
    }
}