package twcore.bots.pubbot;

import java.util.HashSet;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.util.ipc.IPCMessage;

public class pubbotalias extends PubBotModule {

    HashSet<String> specs = new HashSet<String>();
    
    public void initializeModule() {
        m_botAction.ipcSubscribe("banc");
    }

    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
    }
    
    public void handleEvent(InterProcessEvent event) {
        if (event.getChannel().equals("banc")) {
            IPCMessage ipc = (IPCMessage)event.getObject();
            String msg = ipc.getMessage();
            String name;
            if (msg.startsWith("addspec")) {
                name = msg.substring(msg.indexOf(" ") + 1);
                if (!specs.contains(name.toLowerCase()))
                    specs.add(name.toLowerCase());
            } else if (msg.startsWith("remspec")) {
                name = msg.substring(msg.indexOf(" ") + 1);
                if (specs.contains(name.toLowerCase()))
                    specs.remove(name.toLowerCase());
            }
        }
    }

    public void sendPlayerInfo(String message) {
        String playerName = getInfo(message, "TypedName:");
        String playerIP = getInfo(message, "IP:");
        String playerMacID = getInfo(message, "MachineId:");

        m_botAction.ipcSendMessage(getIPCChannel(), "info " + playerName + ":" + playerIP + ":" + playerMacID, getPubHubName(), "pubbotalias");
    }

    public void handleArenaMessage(String message) {
        if (message.startsWith("IP:"))
            sendPlayerInfo(message);
    }

    public void handleEvent(Message event) {
        String message = event.getMessage();
        int messageType = event.getMessageType();

        if (messageType == Message.ARENA_MESSAGE)
            handleArenaMessage(message);
    }
    
    public void sendSpecs() {
        if (specs.isEmpty()) {
            m_botAction.sendSmartPrivateMessage("WingZero", "Specs is empty");
        } else {
            String result = "Spec-locks: ";
            for (String name : specs)
                result += name + ", ";
            result = result.substring(0, result.lastIndexOf(","));
            m_botAction.sendSmartPrivateMessage("WingZero", result);
        }
    }

    public void handleEvent(PlayerEntered event) {
        String playerName = event.getPlayerName();
        if (playerName.startsWith("^") == false) {
            m_botAction.sendUnfilteredPrivateMessage(playerName, "*info");
        }
    }
    
    public void handleEvent(FrequencyShipChange event) {
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (!name.startsWith("^") && specs.contains(name.toLowerCase()))
            m_botAction.sendUnfilteredPrivateMessage(name, "*info");
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