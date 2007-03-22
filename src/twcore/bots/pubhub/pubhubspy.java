package twcore.bots.pubhub;

import java.util.*;

import twcore.bots.PubBotModule;
import twcore.core.*;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.IPCMessage;

public class pubhubspy extends PubBotModule
{
  public static final int IGNORE_TIME = 10;

  private HashSet watchList;
  private HashMap ignoreList;
  private String botName;

  /**
   * This method initializes the pubhubspy module.  It is called after
   * m_botAction has been initialized.
   */

  public void initializeModule()
  {
    watchList = new HashSet();
    ignoreList = new HashMap();
    botName = m_botAction.getBotName();
  }

  /**
   * This method requests the events that are to be used in the module.
   *
   * @param eventRequester is the bots EventRequester
   */

  public void requestEvents(EventRequester eventRequester)
  {
    eventRequester.request(EventRequester.MESSAGE);
  }

  public void watch(String argString)
  {
    String playerName = argString.toLowerCase();

    watchList.add(playerName);
    m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("watch " + playerName));
    m_botAction.sendChatMessage("Watching player: " + argString + " enabled.");
  }

  public void unWatch(String argString)
  {
    String playerName = argString.toLowerCase();

    watchList.remove(playerName);
    m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("unwatch " + playerName));
    m_botAction.sendChatMessage("Watching player: " + argString + " disabled.");
  }

  public void doWatchCmd(String argString)
  {
    String playerName = argString.toLowerCase();

    if(watchList.contains(playerName))
      unWatch(argString);
    else
      watch(argString);
  }

  public void doWatchListCmd()
  {
    Iterator iterator = watchList.iterator();
    StringBuffer line = new StringBuffer();
    String playerName;

    if(watchList.isEmpty())
      m_botAction.sendChatMessage("Not currently watching any players.");

    for(int counter = 0; iterator.hasNext(); counter++)
    {
      playerName = (String) iterator.next();
      line.append(padString(playerName, 20));
      if((counter + 1) % 5 == 0)
      {
        m_botAction.sendChatMessage(line.toString());
        line = new StringBuffer();
      }
    }
    m_botAction.sendChatMessage(line.toString());
  }

  public void doIgnoreCmd(String argString)
  {
    String playerName = argString.toLowerCase();
    if(ignoreList.containsKey(playerName))
      doIgnoreOff(playerName);
    else
      doIgnoreOn(playerName);
  }

  public void doIgnoreOff(String playerName)
  {
    IgnoreTask ignoreTask = (IgnoreTask) ignoreList.get(playerName);
    ignoreTask.cancel();
    m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("unignore " + playerName));
    m_botAction.sendChatMessage("Listening to racist words from " + playerName + ".");
    ignoreList.remove(playerName);
  }

  public void doIgnoreOn(String playerName)
  {
    IgnoreTask ignoreTask = new IgnoreTask(playerName);
    m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("ignore " + playerName));
    ignoreList.put(playerName, ignoreTask);
    m_botAction.scheduleTask(ignoreTask, IGNORE_TIME * 60 * 1000);
    m_botAction.sendChatMessage("Ignoring " + playerName + " for " + IGNORE_TIME + " minutes.");
  }

  public void doIgnoreListCmd()
  {
    Set set = ignoreList.keySet();
    Iterator iterator = set.iterator();
    StringBuffer line = new StringBuffer();
    String playerName;

    if(ignoreList.isEmpty())
      m_botAction.sendChatMessage("Not currently ignoring any players.");

    for(int counter = 0; iterator.hasNext(); counter++)
    {
      playerName = (String) iterator.next();
      line.append(padString(playerName, 20));
      if((counter + 1) % 5 == 0)
      {
        m_botAction.sendChatMessage(line.toString());
        line = new StringBuffer();
      }
    }
    m_botAction.sendChatMessage(line.toString());
  }

  public void handleChatMessage(String sender, String message)
  {
    String command = message.toLowerCase();

    try
    {
      if(command.startsWith("!watch "))
        doWatchCmd(message.substring(7));
      if(command.equals("!watchlist"))
        doWatchListCmd();
      if(command.startsWith("!ignore "))
        doIgnoreCmd(message.substring(8));
      if(command.equals("!ignorelist"))
        doIgnoreListCmd();
    }
    catch(Exception e)
    {
      m_botAction.sendChatMessage(e.getMessage());
    }
  }

  public void handleEvent(Message event)
  {
    String sender = event.getMessager();
    String message = event.getMessage();
    int messageType = event.getMessageType();

    if(messageType == Message.CHAT_MESSAGE)
    {
      handleChatMessage(sender, message);
    }
  }

  public void gotLoadedSpyCmd(String botSender)
  {
    Iterator iterator = watchList.iterator();
    String playerName;

    while(iterator.hasNext())
    {
      playerName = (String) iterator.next();
      m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("watch " + playerName.toLowerCase(), botSender));
    }
  }

  public void handleIPC(String botSender, String recipient, String sender, String message)
  {
    String command = message.toLowerCase();

    try
    {
      if(command.equals("loadedspy"))
        gotLoadedSpyCmd(botSender);
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

    if(recipient == null || recipient.equals(botName))
      handleIPC(botSender, recipient, sender, message);
  }

  public void cancel()
  {
  }

  /**
   * This method pads the string with whitespace.
   *
   * @param string is the string to pad.
   * @param spaces is the size of the resultant string.
   * @return the string padded with spaces to fit into spaces characters.  If
   * the string is too long to fit, it is truncated.
   */

  private String padString(String string, int spaces)
  {
    int whitespaces = spaces - string.length();

    if(whitespaces < 0)
      return string.substring(spaces);

    StringBuffer stringBuffer = new StringBuffer(string);
    for(int index = 0; index < whitespaces; index++)
      stringBuffer.append(' ');
    return stringBuffer.toString();
  }

  private class IgnoreTask extends TimerTask
  {
    private String playerName;

    public IgnoreTask(String playerName)
    {
      this.playerName = playerName;
    }

    public void run()
    {
      m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("unignore " + playerName));
      m_botAction.sendChatMessage("Listening to racist words from " + playerName + ".");
    }
  }
}