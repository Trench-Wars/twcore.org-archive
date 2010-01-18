package twcore.bots.pubbot;

import java.util.Iterator;
import java.util.Stack;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCChatMessage;

public class pubbotchatlog extends PubBotModule{

	private Stack<String> chat = new Stack<String>();
	
	private int maxSize = 50;
	
	private String currentArena = "";
	private String botName = "";
	
	public void handleEvent(Message event){
		
		String message = event.getMessage().toLowerCase();
		
		if(event.getMessageType() == Message.PRIVATE_MESSAGE && message.startsWith("!")){
	
			String name = m_botAction.getPlayerName(event.getPlayerID());
			boolean level = m_botAction.getOperatorList().isBot(name);
			
			if(level){
		
				if(message.startsWith("!help") )
					m_botAction.sendPrivateMessage(name, "!last #      - returns # previous recorded chat lines (max. is "+maxSize+")");
	
				else if(message.startsWith("!last")){
					
					String STRnumberSizeChatLog = message.substring(6);
					
					if(Tools.isAllDigits(STRnumberSizeChatLog)){
							int numberSizeChatLog = Integer.parseInt(STRnumberSizeChatLog);
							
							if(numberSizeChatLog > maxSize)
								numberSizeChatLog = maxSize;
							if(numberSizeChatLog > chat.size())
								numberSizeChatLog = chat.size();
						
							for(int i = numberSizeChatLog - 1 ; i >= 0 ; i--) //will get lines of chats
									if(chat.get(i) != null)
										m_botAction.sendPrivateMessage(name, chat.get(i));
						} else m_botAction.sendPrivateMessage(name, "Syntax error. Please use the following syntax: !last <NUMBER of previous chat lines to display>");
				}
				return ;	
			}
		}
		
		if(event.getMessageType() == Message.PRIVATE_MESSAGE && message.trim().equalsIgnoreCase("report"))
			return;
		
		if(event.getMessageType() == Message.ARENA_MESSAGE &&
				(
							message.equals("Message has been sent to online moderators") ||
							message.equals("Reliable kill messages ON")									 ||
							message.startsWith("IP:")																		 ||
							message.startsWith("MachineId:")														 ||
							message.startsWith("Ping:")																	 ||
							message.startsWith("LOSS: S2C:")														 ||
							message.startsWith("S2C:")																	 ||
							message.startsWith("C2S CURRENT: Slow:")										 ||
							message.startsWith("S2C CURRENT: Slow:")										 ||
							message.startsWith("TIME: Session: ")												 ||
							message.startsWith("Bytes/Sec:") 
																								)) return ;
		
		if(     event.getMessageType() == Message.ARENA_MESSAGE ||
					  event.getMessageType() == Message.OPPOSING_TEAM_MESSAGE ||
					  event.getMessageType() == Message.PRIVATE_MESSAGE ||
					  event.getMessageType() == Message.PUBLIC_MACRO_MESSAGE ||
					  event.getMessageType() == Message.PUBLIC_MESSAGE ||
					  event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ||
					  event.getMessageType() == Message.TEAM_MESSAGE ) {
			
			String sender = getSender(event);
		
			IPCChatMessage ipc = new IPCChatMessage(currentArena, event.getMessageType(), sender, event.getMessage(), botName, this.getPubHubName());
			m_botAction.ipcTransmit(pubbot.IPCCHAT, ipc);
			
			chat.insertElementAt(getMessageTypeString(event.getMessageType()) + "  " + (sender==null ? "" : sender+"> ") + message,0);
			chat.setSize(maxSize);
		}	
	}
	
	public String getSender(Message event){
		
		int messageType = event.getMessageType();
		int senderID = event.getPlayerID();
		
		if(messageType == Message.REMOTE_PRIVATE_MESSAGE || messageType == Message.CHAT_MESSAGE)
			return event.getMessager();
		
		return m_botAction.getPlayerName(senderID);
	}
	private String getMessageTypeString(int messageType){
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
			      }
		
		return "??";
	}
	
	public void cancel() {
		
	}

	@Override
	public void initializeModule() {
	
		this.currentArena = m_botAction.getArenaName();
		this.botName = m_botAction.getBotName();
		
		Iterator<Player> i = m_botAction.getPlayerIterator();
		while(i.hasNext())
			m_botAction.ipcTransmit(pubbot.IPCCHAT, i.next());
	
	}

	@Override
	public void requestEvents(EventRequester eventRequester) {
		
		eventRequester.request(EventRequester.MESSAGE);
	}
	
}
