package twcore.bots.pubbot;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;

public class pubbotalias extends PubBotModule {

  public void initializeModule() {}

  public void requestEvents(EventRequester eventRequester)
  {
    eventRequester.request(EventRequester.MESSAGE);
    eventRequester.request(EventRequester.PLAYER_ENTERED);
  }

  public void sendPlayerInfo(String message)
  {
    String playerName = getInfo(message, "TypedName:");
    String playerIP = getInfo(message, "IP:");
    String playerMacID = getInfo(message, "MachineId:");

    m_botAction.ipcSendMessage(getIPCChannel(), "info " + playerName + ":" + playerIP + ":" + playerMacID, getPubHubName(), "pubbotalias");
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

  public void handleEvent(PlayerEntered event)
  {
    String playerName = event.getPlayerName();
    if(playerName.startsWith("^") == false) {
    	m_botAction.sendUnfilteredPrivateMessage(playerName, "*info");
    }
  }

  public void cancel() {}

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