// 604 271 8507

package twcore.bots.pubbot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.ArenaList;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.Spy;
import twcore.core.util.ipc.IPCMessage;

public class pubbotspy extends PubBotModule
{
  public ArrayList<String> keywords = new ArrayList<String>(); // our banned words
  public ArrayList<String> fragments = new ArrayList<String>(); // our banned fragments
  private HashSet<String> watchList;
  private TreeMap<String, ArrayList<String>> pWatchList;
  private HashSet<String> ignoreList;
  private String currentArena;
  private String botName;
  private boolean spying;
  
  private Spy racismCheck;

  public void initializeModule()
  {
    racismCheck = new Spy(m_botAction);
    
    spying = false;
    ignoreList = new HashSet<String>();
    currentArena = m_botAction.getArenaName();
    watchList = new HashSet<String>();
    pWatchList = new TreeMap<String, ArrayList<String>>();
    botName = m_botAction.getBotName();
  }

  public void requestEvents(EventRequester eventRequester)
  {
    eventRequester.request(EventRequester.MESSAGE);
    eventRequester.request(EventRequester.ARENA_LIST);
  }

  public void handleEvent(Message event)
  {
    int messageType = event.getMessageType();
    String sender = getSender(event);
    String message = event.getMessage();
    String messageTypeString = getMessageTypeString(messageType);

    if(sender != null && messageType != Message.CHAT_MESSAGE && messageType != Message.PRIVATE_MESSAGE &&
                         messageType != Message.REMOTE_PRIVATE_MESSAGE)
    {
      if(racismCheck.isRacist(message))
      {
        if(!ignoreList.contains(sender.toLowerCase()))
          m_botAction.sendUnfilteredPublicMessage("?cheater " + messageTypeString + ": (" + sender + "): " + message);
        else
          m_botAction.sendChatMessage(messageTypeString + ": (" + sender + "): " + message);
      }
      if(spying || watchList.contains(sender.toLowerCase()))
        m_botAction.sendChatMessage(messageTypeString + ": (" + sender + ") (" + currentArena + "): " + message);
      if(pWatchList.containsKey(sender.toLowerCase()))
    	  for(String staffMember:pWatchList.get(sender.toLowerCase()))
    		  m_botAction.sendSmartPrivateMessage(staffMember, messageTypeString + ": (" + sender + ") (" + currentArena + "): " + message);
    }
  }

  public void handleEvent(ArenaList event)
  {
    currentArena = event.getCurrentArenaName();
  }

  public void doSpyOffCmd()
  {
    if(spying)
    {
      m_botAction.sendChatMessage("Spying disabled in " + currentArena + ".");
      spying = false;
    }
  }

  public void doSpyOnCmd()
  {
    if(!spying)
    {
      m_botAction.sendChatMessage("Spying enabled in " + currentArena + ".");
      spying = true;
    }
  }

  public void doSpyCmd()
  {
    if(spying)
      doSpyOffCmd();
    else
      doSpyOnCmd();
  }

  public void handlePlayerIPC(String botSender, String sender, String message)
  {
    String command = message.toLowerCase();

    try
    {
      if(command.equals("!spy on"))
        doSpyOnCmd();
      if(command.equals("!spy off"))
        doSpyOffCmd();
      if(command.equals("!spy"))
        doSpyCmd();
    }
    catch(Exception e)
    {
      m_botAction.sendChatMessage(e.getMessage());
    }
  }

  public void gotWatchCmd(String argString)
  {
    String playerName = argString.toLowerCase();

    watchList.add(playerName);
  }

  public void gotUnWatchCmd(String argString)
  {
    String playerName = argString.toLowerCase();

    watchList.remove(playerName);
  }
  
  public void gotPWatchCmd(String argString)
  {
	String[] names = argString.split(":");
	String playerName = names[0];
	String staffName = names[1];
	if(pWatchList.containsKey(playerName) && pWatchList.get(playerName).contains(staffName)){
		ArrayList<String> staffNames = pWatchList.get(playerName);
		if(staffNames.size() == 1)
			pWatchList.remove(playerName);
		else if(staffNames.size() != 1){
			staffNames.remove(staffName);
			pWatchList.remove(playerName);
			pWatchList.put(playerName, staffNames);
		}
		else if(!staffNames.contains(staffName)){
			staffNames.add(staffName);
			pWatchList.remove(playerName);
			pWatchList.put(playerName, staffNames);
		}
	} else {
		ArrayList<String> staffNames = new ArrayList<String>();
		staffNames.add(staffName);
		pWatchList.put(playerName, staffNames);
	}   
  }

  public void gotIgnoreCmd(String argString)
  {
    ignoreList.add(argString);
  }

  public void gotUnignoreCmd(String argString)
  {
    ignoreList.remove(argString);
  }

  public void handleBotIPC(String botSender, String recipient, String sender, String message)
  {
    String command = message.toLowerCase();

    try
    {
      if(command.startsWith("watch "))
        gotWatchCmd(message.substring(6));
      if(command.startsWith("unwatch "))
        gotUnWatchCmd(message.substring(8));
      if(command.startsWith("ignore "))
        gotIgnoreCmd(message.substring(7));
      if(command.startsWith("unignore "))
        gotUnignoreCmd(message.substring(9));
      if(command.startsWith("pwatch "))
    	gotPWatchCmd(message.substring(7));
    	  
    }
    catch(Exception e)
    {
      m_botAction.sendChatMessage(e.getMessage());
    }
  }

  /**
   * This method handles an InterProcessEvent.
   *
   * @param event is the InterProcessEvent to handle.
   */

  public void handleEvent(InterProcessEvent event)
  {
	  // If the event.getObject() is anything else then the IPCMessage (pubbotchatIPC f.ex) then return
	  if(event.getObject() instanceof IPCMessage == false) {
		  return;
	  }

    IPCMessage ipcMessage = (IPCMessage) event.getObject();
    String message = ipcMessage.getMessage();
    String recipient = ipcMessage.getRecipient();
    String sender = ipcMessage.getSender();
    String botSender = event.getSenderName();

    try
    {
      if(recipient == null || recipient.equals(botName))
      {
        if(sender == null)
          handleBotIPC(botSender, recipient, sender, message);
        else
          handlePlayerIPC(botSender, sender, message);
      }
    }
    catch(Exception e)
    {
      m_botAction.sendChatMessage(e.getMessage());
    }
  }

  public void cancel()
  {
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
    }
    return "Other";
  }

  
  /**
   * This method gets the sender from a message Event.
   *
   * @param event is the message event to analyze.
   * @return the name of the sender is returned.  If the sender cannot be
   * determined then null is returned.
   */

  private String getSender(Message event)
  {
    int messageType = event.getMessageType();

    if(messageType == Message.REMOTE_PRIVATE_MESSAGE || messageType == Message.CHAT_MESSAGE)
      return event.getMessager();
    int senderID = event.getPlayerID();
    return m_botAction.getPlayerName(senderID);
  }
}
