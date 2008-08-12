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
		    // 1 = staff chat
	        // 2 = smod chat
			m_botAction.sendChatMessage(2, getMessageTypeString(event.getMessageType()) + ": " + event.getMessage());
		}
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
