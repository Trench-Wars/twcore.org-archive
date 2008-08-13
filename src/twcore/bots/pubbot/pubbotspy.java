// 604 271 8507

package twcore.bots.pubbot;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.TreeMap;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.ArenaList;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
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

  public void initializeModule()
  {
    loadConfig();
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
      if(isRacist(message))
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
  
  /***
   * Searches words/fragments for banned words in corecfg/racism.cfg
   * @param message Text to check
   * @return True: If a word/fragment is detected. False: If nothing is found.
   */
  public boolean isRacist(String message)
  {
    StringTokenizer words = new StringTokenizer(message.toLowerCase(), " ");
    String word = "";
    
    while(words.hasMoreTokens()) {
  	  word = words.nextToken();
  	  for (String i : keywords){ 
  		  if (word.trim().equals(i.trim())) {
  			  return true;
  		  }
  	  }
  	  
  	  for (String i : fragments){ 
  		  if (word.contains(i)) {
  			  return true;
  		  }
  	  }
    }

    return false;
  }

  /*** 
   * Loads the banned keywords and fragments via corecfg/racism.cfg
   */
  public void loadConfig () { 
  	BufferedReader sr;
  	String line;
  	boolean loadWords = true;
  	try {
  		sr = new BufferedReader(new FileReader(m_botAction.getCoreCfg("racism.cfg")));
  		while((line = sr.readLine()) != null)
  		{
  		   if(line.contains("[Words]")) { loadWords = true; }
  		   if(line.contains("[Fragments]")) { loadWords = false; }
  		   
  		   if(line.startsWith("[") == false) { 
  			   if (loadWords) {
  				   keywords.add(line.trim());
  			   }
  			   else {
  				   fragments.add(line.trim());
  			   }
  		   }
  		}
  		sr.close();
  		sr = null;
  	}
  		catch (Exception e) {
  		sr = null;
  	}
  	
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
