package twcore.bots.pubbot;

import java.util.Iterator;
import java.util.Stack;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.ArenaList;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.ScoreUpdate;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCChatMessage;
import twcore.core.util.ipc.IPCChatPlayer;

public class pubbotchat extends PubBotModule {
	private String currentArena;
	private String botName;
	
	private Stack<String> chat = new Stack<String>();
	private int maxSize = 50; //max size of the Stack which holds the messages for the !last command
	
	/**
	 * Initialize this module
	 */
	public void initializeModule() {
		currentArena = m_botAction.getArenaName();
		botName = m_botAction.getBotName();
		
		// Send all the players in the arena to pubhubchat
		Iterator<Player> it = m_botAction.getPlayerIterator();
		while(it.hasNext()) {
			m_botAction.ipcTransmit(pubbot.IPCCHAT, it.next());
		}
		
	}

	/**
	 * Requests the necessary events for this module to work properly
	 */
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.MESSAGE);
		eventRequester.request(EventRequester.ARENA_LIST);
		eventRequester.request(EventRequester.FREQUENCY_CHANGE);
		eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
		eventRequester.request(EventRequester.PLAYER_ENTERED);
		eventRequester.request(EventRequester.PLAYER_LEFT);
		eventRequester.request(EventRequester.SCORE_UPDATE);
	}

	/**
	 * Sends each chat message that should be recorded to the pubhub
	 */
	public void handleEvent(Message event) {
		// Ignore commands send to the bot
		if( event.getMessageType() == Message.PRIVATE_MESSAGE &&
			event.getMessage().startsWith("!")) {
			
			String name = m_botAction.getPlayerName( event.getPlayerID() );
			
			// !help
			if(m_botAction.getOperatorList().isBot(name) && event.getMessage().toLowerCase().startsWith("!help")) {
				m_botAction.sendPrivateMessage( name, "Pubbot Chat Module" );
            	m_botAction.sendPrivateMessage( name, "!help        - this message");
            	m_botAction.sendPrivateMessage( name, "!last #      - returns # previous recorded chat lines (max. "+maxSize+")");
			}
			// !last
			if(m_botAction.getOperatorList().isBot(name) && event.getMessage().toLowerCase().startsWith("!last ")) {
				String arg = event.getMessage().substring(6).toLowerCase();
				
				if(Tools.isAllDigits(arg)) {
					int lines = Integer.parseInt(arg);
					if(lines > maxSize)
						lines = maxSize;
					if(lines > chat.size()) 
						lines = chat.size();
					
					for(int i = lines-1 ; i >= 0 ; i--) {
						if(chat.get(i)!=null)
							m_botAction.sendPrivateMessage(name, chat.get(i));
					}
				} else {
					m_botAction.sendPrivateMessage(name, "Syntax error. Please use the following syntax: !last <NUMBER of previous chat lines to display>");
				}
			}
			return;
		}
		
		// Ignore the "report" command
		if( event.getMessageType() == Message.PRIVATE_MESSAGE && 
			event.getMessage().trim().equalsIgnoreCase("report")) {
			return;
		}
		
		// Ignore arena messages that the bot shouldn't record
		if( event.getMessageType() == Message.ARENA_MESSAGE &&
			(
				event.getMessage().equals("Message has been sent to online moderators") ||
				event.getMessage().equals("Reliable kill messages ON") ||
				event.getMessage().startsWith("IP:") ||			// Below are arena message from *info
				event.getMessage().startsWith("MachineId:") ||
				event.getMessage().startsWith("Ping:") ||
				event.getMessage().startsWith("LOSS: S2C:") ||
				event.getMessage().startsWith("S2C:") ||
				event.getMessage().startsWith("C2S CURRENT: Slow:") ||
				event.getMessage().startsWith("S2C CURRENT: Slow:") ||
				event.getMessage().startsWith("TIME: Session: ") ||
				event.getMessage().startsWith("Bytes/Sec:")
			)) {
			return;
		}
		
		if(	event.getMessageType() == Message.ARENA_MESSAGE ||
			event.getMessageType() == Message.OPPOSING_TEAM_MESSAGE ||
			event.getMessageType() == Message.PRIVATE_MESSAGE ||
			event.getMessageType() == Message.PUBLIC_MACRO_MESSAGE ||
			event.getMessageType() == Message.PUBLIC_MESSAGE ||
			event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ||
			event.getMessageType() == Message.TEAM_MESSAGE) {
			
			String sender = getSender(event);
			// If the sender is null then it's probably an Arena message
			
			IPCChatMessage ipc = new IPCChatMessage(currentArena, event.getMessageType(), sender, event.getMessage(), botName, this.getPubHubName());
			m_botAction.ipcTransmit(pubbot.IPCCHAT, ipc);
			
			chat.insertElementAt(getMessageTypeString(event.getMessageType()) + "  " + (sender==null ? "" : sender+"> ") + event.getMessage(),0);
			chat.setSize(maxSize);
		}
	}

	/**
	 * This method handles an ArenaList event. 
	 * When ?arena has been done by the pubbot, this module updates its location by getting it from botAction.
	 * The Pubhub has a timertask to ask each pubbot where they are, each pubbot does ?arena and returns its position.
	 * 
	 * @param event is the ArenaList event.
	 */
	public void handleEvent(ArenaList event) {
		currentArena = m_botAction.getArenaName();
	}
	
	public void handleEvent(FrequencyChange event) {
		Player player = this.getPlayer(null, event.getPlayerID());
		IPCChatPlayer ipc = new IPCChatPlayer(this.currentArena, player, "FREQCHANGE", this.botName, this.getPubHubName());
		m_botAction.ipcTransmit(pubbot.IPCCHAT, ipc);
	}
	
	public void handleEvent(FrequencyShipChange event) {
		Player player = this.getPlayer(null, event.getPlayerID());
		IPCChatPlayer ipc = new IPCChatPlayer(this.currentArena, player, "FREQSHIPCHANGE", this.botName, this.getPubHubName());
		m_botAction.ipcTransmit(pubbot.IPCCHAT, ipc);
	}
	
	public void handleEvent(PlayerEntered event) {
		Player player = this.getPlayer(null, event.getPlayerID());
		IPCChatPlayer ipc = new IPCChatPlayer(this.currentArena, player, "ENTERED", this.botName, this.getPubHubName());
		m_botAction.ipcTransmit(pubbot.IPCCHAT, ipc);
	}
	
	public void handleEvent(ScoreUpdate event) {
		Player player = this.getPlayer(null, event.getPlayerID());
		IPCChatPlayer ipc = new IPCChatPlayer(this.currentArena, player, "SCOREUPDATE", this.botName, this.getPubHubName());
		m_botAction.ipcTransmit(pubbot.IPCCHAT, ipc);
	}
	
	public void handleEvent(PlayerLeft event) {
		Player player = this.getPlayer(null, event.getPlayerID());
		IPCChatPlayer ipc = new IPCChatPlayer(this.currentArena, player, "LEFT", this.botName, this.getPubHubName());
		m_botAction.ipcTransmit(pubbot.IPCCHAT, ipc);
	}

	@Override
	public void cancel() {}
	
	/**
	 * This method gets the sender from a message Event.
	 *
	 * @param event is the message event to analyze.
	 * @return the name of the sender is returned.  If the sender cannot be
	 * determined then null is returned.
	 */
  	private String getSender(Message event) {
  		int messageType = event.getMessageType();

  		if(messageType == Message.REMOTE_PRIVATE_MESSAGE || messageType == Message.CHAT_MESSAGE)
  			return event.getMessager();
  		int senderID = event.getPlayerID();
  		return m_botAction.getPlayerName(senderID);
  	}
  	
  	/**
  	 * Returns a two-letter combination which can be used to identify the message type
  	 * @param messageType
  	 * @return
  	 */
  	private String getMessageTypeString(int messageType)
    {
      switch(messageType)
      {
        case Message.PUBLIC_MESSAGE:
          return "  ";
        case Message.PRIVATE_MESSAGE:
          return "P ";
        case Message.TEAM_MESSAGE:
          return "T ";
        case Message.OPPOSING_TEAM_MESSAGE:
          return "OT";
        case Message.ARENA_MESSAGE:
          return "";
        case Message.PUBLIC_MACRO_MESSAGE:
          return "MC";
        case Message.REMOTE_PRIVATE_MESSAGE:
          return "RP";
        /*case Message.WARNING_MESSAGE:
          return "W";
        case Message.SERVER_ERROR:
          return "SE";
        case Message.ALERT_MESSAGE:
          return "AL";*/
      }
      return "??";
    }
  	
  	private Player getPlayer(String name, int id) {
  		Player player = m_botAction.getPlayer(id);
  		if(player == null && name != null) {
  			return m_botAction.getPlayer(name);
  		} else {
  			return player;
  		}
  	}
}
