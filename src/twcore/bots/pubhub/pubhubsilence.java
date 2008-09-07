package twcore.bots.pubhub;

import java.util.HashMap;
import java.util.Set;
import java.util.TimerTask;
import java.util.Map.Entry;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

public class pubhubsilence extends PubBotModule {

	private HashMap<String, TimerTask> silencedPlayers;
	
    /**
     * This method initializes the pubhubtk module.  It is called after
     * m_botAction has been initialized.
     */
    public void initializeModule() {
    	silencedPlayers = new HashMap<String, TimerTask>();
    }


    /**
     * Requests the events.  No events.
     */
    public void requestEvents(EventRequester eventRequester) {
    	eventRequester.request( EventRequester.MESSAGE );
    }

    /**
     * Unimplemented.
     */
    public void cancel() {
    	m_botAction.cancelTasks();
    }
    
    
    public void handleEvent( Message event ){
    	final String name = event.getMessager();
    	final String message = event.getMessage().trim();
    	final Message event2 = event;
    	
    	// PM !help
    	if(opList.isModerator( name ) ){
    		// !help - only via ?chat
    		if(	(
    				event.getMessageType() == Message.CHAT_MESSAGE ||
    				event.getMessageType() == Message.PRIVATE_MESSAGE ||
    				event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE
    			) && message.startsWith("!help")) {
		        m_botAction.sendSmartPrivateMessage( name, "!silence <name>         - Permanent silence the specified player even when he tries to bypass." );
		        m_botAction.sendSmartPrivateMessage( name, "                          Notice: The player isn't given a warning that he is silenced." );
		        m_botAction.sendSmartPrivateMessage( name, "!silence <name>:<time>  - Permanent silences specified player for <time> minutes.");
		        m_botAction.sendSmartPrivateMessage( name, "!removesilence <name>   - Remove the auto-silence on the specified player.");
		        m_botAction.sendSmartPrivateMessage( name, "!listsilence            - List all silenced players and the amount of time left.");
		    }
    		
    		if(	event.getMessageType() == Message.CHAT_MESSAGE ||
    			event.getMessageType() == Message.PRIVATE_MESSAGE ||
    			event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) {
    			
    			// !silence player
    			if( message.startsWith("!silence")) {
    				String parameters = message.substring(9);
    				String timeStr = "10";
    				
    				if(parameters.contains(":")) {
    					timeStr = parameters.split(":")[1];
    					parameters = parameters.split(":")[0];
    				}
    				final String target = parameters;
    				
    				// Check target
    				if(silencedPlayers.containsKey(target.toLowerCase())) {
    					responseMessage(event, "Player '"+target+"' is already auto-silenced. Check !listsilence.", false);
    					return;
    				} else
    				if(m_botAction.getOperatorList().isBot(target)) {
    					responseMessage(event, "Player '"+target+"' is staff, staff can't be auto-silenced.", false);
    					return;
    				}
    				
    				if(message.contains(":") == false) {
    					responseMessage(event, "Player '"+target+"' has been auto-silenced permanently by "+name+".", true);
    					silencedPlayers.put(target.toLowerCase(), null);
    					m_botAction.ipcSendMessage(pubhub.IPCSILENCE, "silence "+target.toLowerCase(), "pubbotsilence", "pubhubsilence");
    					
    				} else if(message.contains(":") == true) {
    					
    					try {
    						int time = Integer.parseInt(timeStr);
    						responseMessage(event, "Player '"+target+"' has been auto-silenced by "+name+" for "+time+" minutes.", true);
    						
    						TimerTask removeSilence = new TimerTask() {
    				    		public void run() {
    				    			if(silencedPlayers.remove(target.toLowerCase()) != null) {
    				    				responseMessage(event2, "Silence of '"+target+"' has expired, auto-silence removed. Please remove the *shutup if player is still silenced.", true);
    				    			} else {
    				    				responseMessage(event2, "Silence of '"+target+"' has expired but auto-silence task wasn't found. Please remove the *shutup if player is still silenced.", true);
    				    			}
    				    			
    				    			m_botAction.ipcSendMessage(pubhub.IPCSILENCE, "unsilence "+target.toLowerCase(), "pubbotsilence", "pubhubsilence");
    				    			
    				    		}
    				    	};
    				    	m_botAction.scheduleTask(removeSilence, time*60*1000);
    				    	silencedPlayers.put(target.toLowerCase(), removeSilence);
    				    	m_botAction.ipcSendMessage(pubhub.IPCSILENCE, "silence "+target.toLowerCase(), "pubbotsilence", "pubhubsilence");
    						
    					} catch(NumberFormatException nfe) {
    						responseMessage(event, "Syntax error. Please use: !silence <name>:<time>", false);
    					}
    				}
    				
    				
    			}
    			
    			// !remove silence of player
    			if( message.startsWith("!removesilence")) {
    				String target = message.substring(15).toLowerCase();
    				
    				if(silencedPlayers.containsKey(target)) {
    					responseMessage(event, "Auto-silence of '"+target+"' removed. Please remove the *shutup if player is still silenced.", true);
    					silencedPlayers.remove(target);
    					m_botAction.ipcSendMessage(pubhub.IPCSILENCE, "unsilence "+target, "pubbotsilence", "pubhubsilence");
    				} else {
    					responseMessage(event, "Player '"+target+"' isn't auto-silenced.", true);
    				}
    			}
    			
    			if( message.startsWith("!listsilence")) {
    				
    				if(event.getMessageType() == Message.CHAT_MESSAGE) {
    					if(silencedPlayers.size() == 0) {
    						m_botAction.sendChatMessage("No silenced players.");
    						return;
    					}
    					
    					m_botAction.sendChatMessage("Silenced players:");
    					
    					Set<Entry<String, TimerTask>> silenced = silencedPlayers.entrySet();
    					
    					for(Entry<String, TimerTask> entry:silenced) {
    						if(entry.getValue() != null) {
    							m_botAction.sendChatMessage(" "+Tools.formatString(entry.getKey(), 19)+"- "+Tools.getTimeDiffString(entry.getValue().scheduledExecutionTime(), false)+" left" );
    						} else {
    							m_botAction.sendChatMessage(" "+Tools.formatString(entry.getKey(), 19)+"- infinite");
    						}
    					}
    				}
    				if(event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) {
    					if(silencedPlayers.size() == 0) {
    						m_botAction.sendSmartPrivateMessage(name, "No silenced players.");
    						return;
    					}
    					
    					m_botAction.sendSmartPrivateMessage(name, "Silenced players:");
    					
    					Set<Entry<String, TimerTask>> silenced = silencedPlayers.entrySet();
    					
    					for(Entry<String, TimerTask> entry:silenced) {
    						if(entry.getValue() != null) {
    							m_botAction.sendSmartPrivateMessage(name, " "+Tools.formatString(entry.getKey(), 19)+"- "+Tools.getTimeDiffString(entry.getValue().scheduledExecutionTime(), false)+" left" );
    						} else {
    							m_botAction.sendSmartPrivateMessage(name, " "+Tools.formatString(entry.getKey(), 19)+"- infinite");
    						}
    					}
    				}
    			}
    			
    		}
    	}

    }
    
    
    /**
     * Returns a reply to the messager on a !command. 
     * Special method to send a chat message on a chat !command but also a PM on a PM !command.
     * 
     * @param event
     * @param message
     */
    private void responseMessage(Message event, String message, boolean echoChat) {
    	if(event.getMessageType() == Message.CHAT_MESSAGE || echoChat) {
    		m_botAction.sendChatMessage(message);
    	}
    	if(event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) {
    		m_botAction.sendSmartPrivateMessage(event.getMessager(), message);
    	}
    }
    
    
    public void handleEvent(InterProcessEvent event) {
    	if(event.getChannel() == pubhub.IPCSILENCE) {
    		IPCMessage ipc = (IPCMessage)event.getObject();
    		String message = ipc.getMessage();
    			
    		if(message.startsWith("entered")) {
    			// check if player should be silenced
    			String target = message.substring(8);
    			
    			if(silencedPlayers.containsKey(target.toLowerCase())) {
    				m_botAction.ipcSendMessage(pubhub.IPCSILENCE, "silence "+target.toLowerCase(), "pubbotsilence", "pubhubsilence");
    			}
    		} 
    		if(message.startsWith("silenced")) {
    			String target = message.substring(9);
				m_botAction.sendChatMessage("Player '"+target+"' has been (re)silenced.");
    		}
    		if(message.startsWith("unsilenced")) {
    			String target = message.substring(11);
				m_botAction.sendChatMessage("Player '"+target+"' has been unsilenced.");
    		}
    	}
	}
}