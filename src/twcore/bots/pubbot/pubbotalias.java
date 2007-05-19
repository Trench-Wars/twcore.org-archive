// 604 271 8507

package twcore.bots.pubbot;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.util.IPCMessage;

public class pubbotalias extends PubBotModule
{
  private String botName;

  public void initializeModule()
  {
    botName = m_botAction.getBotName();
  }

  public void requestEvents(EventRequester eventRequester)
  {
    eventRequester.request(EventRequester.MESSAGE);
    eventRequester.request(EventRequester.PLAYER_ENTERED);
  }

  public void handlePlayerIPC(String botSender, String sender, String message)
  {
    String command = message.toLowerCase();

    try
    {
    }
    catch(Exception e)
    {
      m_botAction.sendChatMessage(e.getMessage());
    }
  }

  public void sendPlayerInfo(String message)
  {
    String playerName = getInfo(message, "TypedName:");
    String playerIP = getInfo(message, "IP:");
    String playerMacID = getInfo(message, "MachineId:");

//    m_botAction.sendSmartPrivateMessage("Cpt.Guano!", playerName + ": " + playerIP + ", " + playerMacID + ".");
    m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("record " + playerName + ":" + playerIP + ":" + playerMacID, getPubHubName()));
  }

  public void handleArenaMessage(String message)
  {
    if(message.startsWith("IP:"))
      sendPlayerInfo(message);
  }

  public void handleEvent(Message event)
  {
    String message = event.getMessage();
    int messageType = event.getMessageType();

    if(messageType == Message.ARENA_MESSAGE)
      handleArenaMessage(message);
  }

  public void gotNotRecordedCmd(String argString)
  {
    m_botAction.sendUnfilteredPrivateMessage(argString, "*info");
  }

  public void handleBotIPC(String botSender, String recipient, String sender, String message)
  {
    String command = message.toLowerCase();

    try
    {
      if(command.startsWith("notrecorded "))
        gotNotRecordedCmd(message.substring(12));
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

  public void handleEvent(PlayerEntered event)
  {
    String playerName = event.getPlayerName();
    if(playerName.startsWith("^") == false) {
    	m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("entered " + playerName, getPubHubName()));
    }
  }

  public void cancel()
  {
  }

  private String getInfo(String message, String infoName)
  {
    int beginIndex = message.indexOf(infoName);
    int endIndex;

    if(beginIndex == -1)
      return null;
    beginIndex = beginIndex + infoName.length();
    endIndex = message.indexOf("  ", beginIndex);
    if(endIndex == -1)
      endIndex = message.length();
    return message.substring(beginIndex, endIndex);
  }
}