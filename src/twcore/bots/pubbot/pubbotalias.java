package twcore.bots.pubbot;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;

public class pubbotalias extends PubBotModule {

    public void initializeModule() {
    }

    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
    }

    public void sendPlayerInfo(String message) {
        String playerName = getInfo(message, "TypedName:");
        String playerIP = getInfo(message, "IP:");
        String playerMacID = getInfo(message, "MachineId:");

        m_botAction.ipcSendMessage(getIPCChannel(), "info " + playerName + ":" + playerIP + ":" + playerMacID, getPubHubName(), "pubbotalias");
        m_botAction.ipcSendMessage("TWDOp Alias", "info " + playerName + ":" + playerIP + ":" + playerMacID, "TWDOpBot", "pubbotalias");   
    }
    

    public void handleArenaMessage(String message) {
    }

    public void handleEvent(Message event) {
    }

    public void handleEvent(PlayerEntered event) {
        //String arena = m_botAction.getArenaName().toLowerCase();
        //if (arena.equals("tw") || arena.equals("trenchwars"))
          //  return;
        String playerName = event.getPlayerName();
        if (playerName == null)
            playerName = m_botAction.getPlayerName(event.getPlayerID());
        if (playerName.startsWith("^") == false) {
            m_botAction.sendUnfilteredPrivateMessage(playerName, "*info");
        }
    }

    public void cancel() {
    }

    private String getInfo(String message, String infoName) {
        int beginIndex = message.indexOf(infoName);
        int endIndex;

        if (beginIndex == -1)
            return null;
        beginIndex = beginIndex + infoName.length();
        endIndex = message.indexOf("  ", beginIndex);
        if (endIndex == -1)
            endIndex = message.length();
        return message.substring(beginIndex, endIndex);
    }
}