package twcore.bots.pubbot;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.util.ipc.IPCMessage;

public class pubbotsilence extends PubBotModule {
	
	private String tempTarget;
	private boolean silencePlayer;

    public void initializeModule(){
    }

    public void cancel(){
    }

    public void requestEvents( EventRequester eventRequester ){
        eventRequester.request( EventRequester.PLAYER_ENTERED );
        eventRequester.request( EventRequester.MESSAGE );
    }


    public void handleEvent( PlayerEntered event ){
    	m_botAction.ipcSendMessage(pubbot.IPCSILENCE, "entered "+event.getPlayerName(), "pubhubsilence", "pubbotsilence");
    }

    public void handleEvent( PlayerLeft event ){
    }
    
    public void handleEvent(InterProcessEvent event) {
    	if(event.getChannel() == pubbot.IPCSILENCE) {
    		IPCMessage ipc = (IPCMessage)event.getObject();
    		String message = ipc.getMessage();
    		
    		if(message.startsWith("silence")) {
    			// silence player in arena
    			String target = message.substring(8);
    			m_botAction.sendUnfilteredPrivateMessage(target, "*shutup");
    			tempTarget = target;
    			silencePlayer = true;
    			
    		} else
    		if(message.startsWith("unsilence")) {
    			// unsilence player
    			String target = message.substring(10);
    			m_botAction.sendUnfilteredPrivateMessage(target, "*shutup");
    			tempTarget = target;
    			silencePlayer = false;
    		}
    	}
    }
    
    public void handleEvent( Message event ) {
    	if(tempTarget == null)
    		return;
    	
    	if(	event.getMessageType() == Message.ARENA_MESSAGE && 
    		event.getMessage().trim().equalsIgnoreCase(tempTarget + " can now speak") &&
    		silencePlayer == true) {
    		// The bot just unsilenced the player, while the player should be silenced.
    		m_botAction.sendUnfilteredPrivateMessage(tempTarget, "*shutup");
    		tempTarget = null;
    	} else
    	if(	event.getMessageType() == Message.ARENA_MESSAGE && 
    		event.getMessage().trim().equalsIgnoreCase(tempTarget + " can now speak") &&
    		silencePlayer == false) {
    		// The bot just unsilenced the player (and it's ok)
			m_botAction.ipcSendMessage(pubbot.IPCSILENCE, "unsilenced "+tempTarget, "pubhubsilence", "pubbotsilence");
    		tempTarget = null;
    	} else
    	if(	event.getMessageType() == Message.ARENA_MESSAGE && 
        	event.getMessage().trim().equalsIgnoreCase(tempTarget + " has been silenced") &&
        	silencePlayer == false) {
    		// The bot just silenced the player, while the player should be unsilenced.
    		m_botAction.sendUnfilteredPrivateMessage(tempTarget, "*shutup");
    		tempTarget = null;
    	} else
    	if(	event.getMessageType() == Message.ARENA_MESSAGE && 
        	event.getMessage().trim().equalsIgnoreCase(tempTarget + " has been silenced") &&
        	silencePlayer == true) {
    		// The bot just silenced the player (and it's ok)
    		m_botAction.ipcSendMessage(pubbot.IPCSILENCE, "silenced "+tempTarget, "pubhubsilence", "pubbotsilence");
    		tempTarget = null;
    	}
    }
}

