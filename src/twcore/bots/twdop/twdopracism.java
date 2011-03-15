package twcore.bots.twdop;


import java.util.ArrayList;

import twcore.bots.Module;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.util.Spy;



public class twdopracism extends Module
{
    public ArrayList<String> keywords = new ArrayList<String>(); // our banned words
    public ArrayList<String> fragments = new ArrayList<String>(); // our banned fragments
    private Spy racismCheck;


    @Override
    public void cancel() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void initializeModule() {
        //String chat = "twd";
        //m_botAction.sendUnfilteredPublicMessage("?chat="+chat);
        racismCheck = new Spy(m_botAction);
        
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.ARENA_LIST);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
      }

    public void handleEvent(Message event)
    {
      int messageType = event.getMessageType();
      String sender = getSender(event);
      String message = event.getMessage();
      String messageTypeString = getMessageTypeString(messageType);

      if(sender != null && messageType != Message.PRIVATE_MESSAGE &&
                           messageType != Message.CHAT_MESSAGE)
      {
        if(racismCheck.isRacist(message))
        {

            m_botAction.sendUnfilteredPublicMessage("?cheater " + messageTypeString + ": (" + sender + "): " + message);}}
        }
        
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
            case Message.CHAT_MESSAGE:
            return "Chat Msg. (TWD)";
          }
          return "Other";
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