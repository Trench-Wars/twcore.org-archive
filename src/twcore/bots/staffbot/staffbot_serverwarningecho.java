package twcore.bots.staffbot;

import java.util.ArrayList;
import java.util.List;

import twcore.bots.Module;
import twcore.core.EventRequester;
import twcore.core.events.Message;

public class staffbot_serverwarningecho extends Module {
    
    List<String> ignoredPlayers = new ArrayList<String>();
    
    final String[] helpSmod = {
            "----------------[ ServerErrorEcho: SMod+ ]-----------------",
            " !errorignore <player>     - Ignores server errors from <player> (toggable).",
            " !ignorelist               - Shows ignored players for server errors."
    };

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.MESSAGE);
	}
	
	public void handleEvent(Message event) {

 	    if(event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE || event.getMessageType() == Message.PRIVATE_MESSAGE) {
	        String message = event.getMessage().toLowerCase();
	        String name = event.getMessager() == null ? m_botAction.getPlayerName(event.getPlayerID()) : event.getMessager();
	        
	        if(!m_botAction.getOperatorList().isSmod(name)) {
	            return;
	        }
	        
	        if(message.startsWith("!help")) {
	            m_botAction.smartPrivateMessageSpam(name, helpSmod);
	        } else
	        
	        if(message.startsWith("!errorignore ")) {
	            String player = message.substring(12).trim();
	            if(player.length() == 0) {
	                m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please use !errorignore <name>. PM !help for more information.");
	            } else {
	                if(ignoredPlayers.contains(player)) {
	                    ignoredPlayers.remove(player);
	                    m_botAction.sendSmartPrivateMessage(name, "Player '"+player+"' removed from ignore list.");
	                    m_botAction.sendChatMessage(2, name + " removed the ignore of '"+player+"' for server errors.");
	                } else {
	                    ignoredPlayers.add(player);
	                    m_botAction.sendSmartPrivateMessage(name, "Player '"+player+"' added to ignore list.");
	                    m_botAction.sendChatMessage(2, name + " ignored '"+player+"' for server errors.");
	                }
	            }
	        } else
	        if(message.startsWith("!ignorelist")) {
	            if(ignoredPlayers.isEmpty()) {
	                m_botAction.sendSmartPrivateMessage(name, "No players currently ignored.");
	            } else {
	                m_botAction.sendSmartPrivateMessage(name, "Ignored players:");
	                for(String player : ignoredPlayers) {
	                    m_botAction.sendSmartPrivateMessage(name, "- "+player);
	                }
	            }
	        }
	    }
		if(event.getMessageType() == Message.SERVER_ERROR) {
		    // 1 = staff chat
	        // 2 = smod chat
		    if(ignoredPlayers.isEmpty() || !ignoredPlayers.contains(getPlayerNameFromError(event.getMessage()).toLowerCase()))
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
	  
	  private String getPlayerNameFromError(String errorMessage) {
	      if(errorMessage.indexOf('(') != -1 && errorMessage.indexOf(')') != -1) {
    	      int startPosition = errorMessage.indexOf('(')+1;
    	      int endPosition = errorMessage.indexOf(')');
    	      return errorMessage.substring(startPosition, endPosition);
	      } else {
	          return null;
	      }
	  }

	@Override
	public void cancel() {
	    ignoredPlayers.clear();
	}

	@Override
	public void initializeModule() {
	}
}
