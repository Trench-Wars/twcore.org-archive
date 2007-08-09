// 604 271 8507

package twcore.bots.pubbot;

import java.util.HashSet;
import java.util.StringTokenizer;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.ArenaList;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.ipc.IPCMessage;

public class pubbotspy extends PubBotModule
{
  public static final String keywords = "j3w jew chink nig nigger n1g n1gg3r nigg3r paki gook nigg@ n1gg@ nigga niggaa nignog nign0g n1gnog n1gn0g nikka nika n1kka n1ka n*gga n*ggaa n*ggaaa n*gger n*g nigg*r nigg* n*gg*r n*gg* n!g n!ga n!gg n!gga n!ggaa n!ggaaa n!ggaaaa n!ggaaaaa";
  private HashSet<String> watchList;
  private HashSet<String> ignoreList;
  private String currentArena;
  private String botName;
  private boolean spying;

  public void initializeModule()
  {
    spying = false;
    ignoreList = new HashSet<String>();
    currentArena = m_botAction.getArenaName();
    watchList = new HashSet<String>();
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

  private boolean containsWord(String message, String word)
  {
    StringBuffer stringBuffer = new StringBuffer();
    String formattedMessage;
    String lowerWord = word.toLowerCase();
    char character;

    for(int index = 0; index < message.length(); index++)
    {
      character = Character.toLowerCase(message.charAt(index));
      if(Character.isLetterOrDigit(character) || character == ' ')
        stringBuffer.append(character);
    }

    formattedMessage = " " + stringBuffer.toString() + " ";
    return formattedMessage.indexOf(" " + lowerWord + " ") != -1 ||
           formattedMessage.indexOf(" " + lowerWord + "s ") != -1;
  }

  private boolean isRacist(String message)
  {
    StringTokenizer keywordTokens = new StringTokenizer(keywords," ");

    while(keywordTokens.hasMoreTokens())
      if(containsWord(message, keywordTokens.nextToken()))
        return true;
    return false;
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
