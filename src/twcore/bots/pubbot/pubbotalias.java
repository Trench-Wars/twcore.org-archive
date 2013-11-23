package twcore.bots.pubbot;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;

public class pubbotalias extends PubBotModule {

    private boolean twchat = false;
    
    public void initializeModule() {
    }

    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
    }

    public void sendPlayerInfo(String message) {
        String playerName = getInfo(message, "TypedName:");
        String playerIP = getInfo(message, "IP:");
        String playerMacID = getInfo(message, "MachineId:");

        if(twchat == true){
            m_botAction.sendSmartPrivateMessage("TW-Chat", "ALIASIGNORE: " + playerName);
        } else {
        m_botAction.ipcSendMessage(getIPCChannel(), "info " + playerName + ":" + playerIP + ":" + playerMacID, getPubHubName(), "pubbotalias");
        m_botAction.ipcSendMessage("TWDOp Alias", "info " + playerName + ":" + playerIP + ":" + playerMacID, "TWDOpBot", "pubbotalias");   
    }
    }

    public void handleEvent(Message event) {
        String msg = event.getMessage();
        if (event.getMessageType() == Message.ARENA_MESSAGE && msg.startsWith("IP:"))
            if (msg.contains("Client: VIE 1.34")) {
                twchat = true;
                sendPlayerInfo(msg);
            }
                twchat = false;
                sendPlayerInfo(msg);
        

    }

    public void handleEvent(PlayerEntered event) {
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