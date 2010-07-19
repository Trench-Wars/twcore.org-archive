package twcore.bots.pubbot;

import java.util.ArrayList;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.PubBotModule;
import twcore.bots.staffbot.staffbot_banc.BanCType;
import twcore.core.EventRequester;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

/**
 * Pubbot BanC module.
 * 
 * Behaviour:
 *  - when a player enters, sends a IPC message to pubhub's banc module with the player's name
 *    "entered <playername>"
 *    
 *  - when the pubbot's alias module requests *info of the player, catch it and IPC it to pubhub's banc module
 *    "info <playername>:<ip>:<mid>"
 *  - on receiving IPC text 'silence <playername>', *shutup player
 *     - reply with IPC message that playername is silenced
 *  - on receiving IPC text 'unsilence <playername>', un-*shutup player
 *     - reply with IPC message that playername is unsilenced
 *  - on receiving IPC text 'speclock <playername>', *spec player
 *     - reply with IPC message that playername is spec-locked
 *  - on receiving IPC text 'unspeclock <playername>', un-*spec player
 *     - reply with IPC message that playername is unspec-locked
 *  - on receiving IPC text 'kick <playername>', *kill player
 *     - reply with IPC message that playername is kicked
 *  
 * Dependencies:
 *  - pubhub's banc module
 *  - pubbot's alias module
 * 
 * @author Maverick
 *
 */
public class pubbotbanc extends PubBotModule {
	
    private Vector<String> banCSuperLocked;
    
	private String tempBanCCommand = null;
	private String tempBanCTime = null;
	private String tempBanCPlayer = null;
	
	public static final String IPCBANC = "banc";
	
	private final static String INFINTE_DURATION = "0";
	
	private Vector<String> IPCQueue = new Vector<String>(); 
	
	private TimerTask checkIPCQueue;
	private TimerTask initActiveBanCs;
	
    public void initializeModule(){
    	m_botAction.ipcSubscribe(IPCBANC);
    	
    	// Request active BanCs from StaffBot
    	initActiveBanCs = new TimerTask() {
    		public void run() {
    			m_botAction.ipcSendMessage(IPCBANC, "BANC PUBBOT INIT", "banc", m_botAction.getBotName());
    		}
    	};
    	m_botAction.scheduleTask(initActiveBanCs, 1000);
    	
    	checkIPCQueue = new TimerTask() {
    		public void run() {
    			if(IPCQueue.size() != 0) {
            		handleIPCMessage(IPCQueue.remove(0));
            	}
    		}
    	};
    	m_botAction.scheduleTaskAtFixedRate(checkIPCQueue, 5*Tools.TimeInMillis.SECOND, 5*Tools.TimeInMillis.SECOND);
    	banCSuperLocked = new Vector<String>();
    }

    public void cancel(){
    	m_botAction.ipcUnSubscribe(IPCBANC);
    	m_botAction.cancelTask(initActiveBanCs);
    	m_botAction.cancelTask(checkIPCQueue);
    }

    public void requestEvents( EventRequester eventRequester ){
        eventRequester.request( EventRequester.MESSAGE );
    }

    public void handleEvent(InterProcessEvent event) {
    	// IPCMessage.recipient null		==> All pubbots
    	// IPCMessage.recipient "PubBotX" 	==> Specific Pubbot X
    	
    	if(IPCBANC.equals(event.getChannel()) && event.getObject() != null && event.getObject() instanceof IPCMessage && 
    			((IPCMessage)event.getObject()).getSender() != null && ((IPCMessage)event.getObject()).getSender().equalsIgnoreCase("banc") &&
    			(((IPCMessage)event.getObject()).getRecipient() == null ||((IPCMessage)event.getObject()).getRecipient().equalsIgnoreCase(m_botAction.getBotName()))) {

    		IPCMessage ipc = (IPCMessage) event.getObject();
    		String command = ipc.getMessage();
    		
    		// Are we still busy waiting for an answer?
    		if(tempBanCCommand != null) {
        		IPCQueue.add(command);
        	} else {
        		handleIPCMessage(command);
        	}
    		
    	}
    }
    public void handleEvent(PlayerEntered event){
        
    }
    public void handleEvent(FrequencyShipChange event){
        try{
            String namePlayer = m_botAction.getPlayerName(event.getPlayerID());
            m_botAction.sendPrivateMessage("Dexter", "Someone changed ship: "+m_botAction.getPlayerName( event.getPlayerID() ));
            if(banCSuperLocked.contains(namePlayer) && ( event.getShipType() == 2 || event.getShipType() == 4 || event.getShipType() == 8 ))
            {
                
                superLockMethod(namePlayer, event.getShipType());
            
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    private void superLockMethod(String namePlayer, int shipNumber){
        m_botAction.sendPrivateMessage(namePlayer, "You're banned from ship"+shipNumber);
        m_botAction.sendPrivateMessage(namePlayer, "Choose other ship, like: warbird, spider, weasel, lancaster...");
        m_botAction.specWithoutLock(namePlayer);
    }
    
    private void handleIPCMessage(String command) {
    	if(command.startsWith(BanCType.SILENCE.toString())) {
			// silence player in arena
			tempBanCCommand = BanCType.SILENCE.toString();
			tempBanCTime = command.substring(8).split(":")[0];
			tempBanCPlayer = command.substring(8).split(":")[1];
			m_botAction.sendUnfilteredPrivateMessage(tempBanCPlayer, "*shutup");
		} else
		if(command.startsWith("REMOVE "+BanCType.SILENCE.toString())) {
			// unsilence player in arena
			tempBanCCommand = "REMOVE "+BanCType.SILENCE.toString();
			tempBanCTime = null;
			tempBanCPlayer = command.substring(15);
			m_botAction.sendUnfilteredPrivateMessage(tempBanCPlayer, "*shutup");
		} else
		if(command.startsWith(BanCType.SPEC.toString())) {
			// speclock player in arena
			tempBanCCommand = BanCType.SPEC.toString();
			tempBanCTime = command.substring(5).split(":")[0];
			tempBanCPlayer = command.substring(5).split(":")[1];
			
			m_botAction.spec(tempBanCPlayer);
		} else
		if(command.startsWith(BanCType.SUPERSPEC.toString())){
		    //superspec lock player in arena
		    tempBanCCommand = BanCType.SUPERSPEC.toString();
		    //!spec player:time
		    //SPEC time:target
		    //012345
		    //SUPERSPEC time:target
		    //0123456789T
		    tempBanCTime = command.substring(10).split(":")[0];
		    tempBanCPlayer = command.substring(10).split(":")[1];
		    m_botAction.specWithoutLock(tempBanCPlayer);
		    banCSuperLocked.add(tempBanCPlayer);
		    System.out.println("Super specced "+tempBanCPlayer+" for "+tempBanCTime);
		} else
		if(command.startsWith("REMOVE "+BanCType.SPEC.toString())) {
			// remove speclock of player in arena
			tempBanCCommand = "REMOVE "+BanCType.SPEC.toString();
			tempBanCTime = null;
			tempBanCPlayer = command.substring(12);
			m_botAction.spec(tempBanCPlayer);
			//need to make remove for super spec
		} else
		if(command.startsWith(BanCType.KICK.toString())) {
			// kick player from arena
			tempBanCCommand = BanCType.KICK.toString();
			tempBanCTime = command.substring(5).split(":")[0];
			tempBanCPlayer = command.substring(5).split(":")[1];
			if(tempBanCTime.equals(INFINTE_DURATION))
    			m_botAction.sendPrivateMessage(tempBanCPlayer, "You're permanently not allowed from Trench Wars because of abuse and/or violation of Trench Wars rules.");
    		else
    			m_botAction.sendPrivateMessage(tempBanCPlayer, "You're not allowed in Trench Wars for "+tempBanCTime+" minutes because of abuse and/or violation of Trench Wars rules.");
    		
			m_botAction.sendUnfilteredPrivateMessage(tempBanCPlayer, "*kill");
		}
    }
    
    public void handleEvent( Message event ) {
        String message = event.getMessage().trim();
        
    	if(tempBanCCommand != null && event.getMessageType() == Message.ARENA_MESSAGE) {

    		// <player> can now speak
        	if(	message.equalsIgnoreCase(tempBanCPlayer + " can now speak")) {
        	    
        	    if(tempBanCCommand.startsWith("REMOVE")) {
        	    	// The bot just unsilenced the player (and it's ok)
        	    	m_botAction.sendPrivateMessage(tempBanCPlayer, "Silence lifted. You can now speak.");
                    m_botAction.ipcSendMessage(IPCBANC, tempBanCCommand +" "+ tempBanCPlayer, "banc", m_botAction.getBotName());
                    tempBanCCommand = null;
                    tempBanCPlayer = null;
        	    } else {
        	    	// The bot just unsilenced the player, while the player should be silenced.
            		m_botAction.sendUnfilteredPrivateMessage(tempBanCPlayer, "*shutup");
        	    }
        	} else
        	// <player> has been silenced
        	if(	 message.equalsIgnoreCase(tempBanCPlayer + " has been silenced")) {
        	    
        	    if(tempBanCCommand.startsWith("REMOVE")) {
        	    	// The bot just silenced the player, while the player should be unsilenced.
        	    	m_botAction.sendUnfilteredPrivateMessage(tempBanCPlayer, "*shutup");
        	    } else {
        	    	// The bot just silenced the player (and it's ok)
        	    	if(tempBanCTime.equals(INFINTE_DURATION))
        	    		m_botAction.sendPrivateMessage(tempBanCPlayer, "You've been permanently silenced because of abuse and/or violation of Trench Wars rules.");
        	    	else
        	    		m_botAction.sendPrivateMessage(tempBanCPlayer, "You've been silenced for "+tempBanCTime+" minutes because of abuse and/or violation of Trench Wars rules.");
        	    	
        	    	m_botAction.ipcSendMessage(IPCBANC, tempBanCCommand +" "+ tempBanCPlayer, "banc", m_botAction.getBotName());
        	    	tempBanCCommand = null;
                    tempBanCPlayer = null;
        	    }
        	} else
        	// Player locked in spectator mode
        	if( message.equalsIgnoreCase("Player locked in spectator mode")) {
        	    
        	    if(tempBanCCommand.startsWith("REMOVE")) {
        	    	// The bot just spec-locked the player, while the player shouldn't be spec-locked.
                    m_botAction.spec(tempBanCPlayer);
                } else {
                	// The bot just spec-locked the player (and it's ok)
                	if(tempBanCTime.equals(INFINTE_DURATION))
                		m_botAction.sendPrivateMessage(tempBanCPlayer, "You've been permanently locked into spectator because of abuse and/or violation of Trench Wars rules.");
                	else
                		m_botAction.sendPrivateMessage(tempBanCPlayer, "You've been locked into spectator for "+tempBanCTime+" minutes because of abuse and/or violation of Trench Wars rules.");
                	
                	m_botAction.ipcSendMessage(IPCBANC, tempBanCCommand +" "+ tempBanCPlayer, "banc", m_botAction.getBotName());
                	tempBanCCommand = null;
                    tempBanCPlayer = null;
                }
        	} else
        	// Player free to enter arena
        	if( message.equalsIgnoreCase("Player free to enter arena")) {
        	    
        		if(tempBanCCommand.startsWith("REMOVE")) {
        	    	// The bot just unspec-locked the player (and it's ok)
        			m_botAction.sendPrivateMessage(tempBanCPlayer, "Spectator-lock removed. You may now enter.");
        			m_botAction.ipcSendMessage(IPCBANC, tempBanCCommand +" "+ tempBanCPlayer, "banc", m_botAction.getBotName());
                	tempBanCCommand = null;
                    tempBanCPlayer = null;
                } else {
                	// The bot just unspec-locked the player, while the player should be spec-locked.
                	m_botAction.spec(tempBanCPlayer);
        	    }
        	} else
        	// Player kicked off
        	if( message.equalsIgnoreCase("Player kicked off")) {
        		m_botAction.ipcSendMessage(IPCBANC, tempBanCCommand +" "+ tempBanCPlayer, "banc", m_botAction.getBotName());
            	tempBanCCommand = null;
                tempBanCPlayer = null;
        	}
        	
        	if(tempBanCCommand == null && IPCQueue.size() != 0) {
        		handleIPCMessage(IPCQueue.remove(0));
        	}
    	}
    }
    
}

