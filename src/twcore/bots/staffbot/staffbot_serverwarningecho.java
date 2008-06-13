package twcore.bots.staffbot;

import twcore.bots.Module;
import twcore.core.EventRequester;
import twcore.core.events.Message;

public class staffbot_serverwarningecho extends Module {

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.MESSAGE);
	}
	
	public void handleEvent(Message event) {
		if(event.getMessageType() == Message.SERVER_ERROR) {
			m_botAction.sendChatMessage(getMessageTypeString(event.getMessageType()) + ": (" + getSender(event) + ") (" + m_botAction.getArenaName() + "): " + event.getMessage());
		}
	}
	
	private String getSender(Message message) {
		int messageType = message.getMessageType();
	    if(messageType == Message.REMOTE_PRIVATE_MESSAGE || messageType == Message.CHAT_MESSAGE)
	      return message.getMessager();
	    int senderID = message.getPlayerID();
	    return m_botAction.getPlayerName(senderID);
	}
	
	/**
	 * This method gets a string representation of the message type.
	 * @param messageType is the type of message to handle
	 * @returns a string representation of the message is returned.
	 */
	  private String getMessageTypeString(int messageType)
	  {
	    switch(messageType)
	    {
	      case Message.PUBLIC_MESSAGE:
	        return "Public";
	      case Message.PRIVATE_MESSAGE:
	        return "Private";
	      case Message.TEAM_MESSAGE:
	        return "Team";
	      case Message.OPPOSING_TEAM_MESSAGE:
	        return "Opp. Team";
	      case Message.ARENA_MESSAGE:
	        return "Arena";
	      case Message.PUBLIC_MACRO_MESSAGE:
	        return "Pub. Macro";
	      case Message.REMOTE_PRIVATE_MESSAGE:
	        return "Private";
	      case Message.WARNING_MESSAGE:
	        return "Warning";
	      case Message.SERVER_ERROR:
	        return "Serv. Error";
	      case Message.ALERT_MESSAGE:
	        return "Alert";
	    }
	    return "Other";
	  }

	@Override
	public void cancel() {}

	@Override
	public void initializeModule() {}
}
