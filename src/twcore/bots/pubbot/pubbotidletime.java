/**
    Pubbot Idle Module
    -> Uses *einfo to display idle time
    @author CRe
*/

package twcore.bots.pubbot;

import java.util.HashMap;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.Message;

public class pubbotidletime extends PubBotModule {

    private HashMap<String, String> idleTimeRequests;
    @Override
    public void initializeModule() {
        idleTimeRequests = new HashMap<String, String>();
    }

    @Override
    public void cancel() {
        idleTimeRequests = null;
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
    }

    public void handleEvent(Message event) {

        if(event.getMessageType() == Message.ARENA_MESSAGE) {
            handleEinfo(event.getMessage());
            return;
        }

        if(event.getMessageType() == Message.PRIVATE_MESSAGE) {
            String msg = event.getMessage();
            String name = event.getMessager();

            if(msg == null || name == null) {
                return;
            }

            if(!m_botAction.getOperatorList().isModerator(name)) return;

            if (msg.startsWith("!idletime ")) {
                String playerName = msg.substring(10);

                if (playerName == "") {
                    m_botAction.sendPrivateMessage(name, "Please specify a player.");
                    return;
                }

                playerName = m_botAction.getFuzzyPlayerName(playerName);

                if (playerName == null) {
                    m_botAction.sendPrivateMessage(name, "Player not found.");
                    return;
                }

                idleTimeRequests.put(playerName.toLowerCase(), name.toLowerCase());
            }
            else if (msg.equals("!help")) {
                m_botAction.sendPrivateMessage(name, "!idletime <name> - Returns idle time for name.");
            }
        }
    }

    private void handleEinfo(String msg) {
        if (!msg.contains("Idle:")) return;

        String name = msg.substring(0, msg.indexOf(":"));

        if (idleTimeRequests.containsKey(name.toLowerCase())) {
            String idleTime = msg.substring(msg.indexOf("Idle: ") + 6, msg.indexOf(" s  Timer drift"));
            String staffer = idleTimeRequests.get(name.toLowerCase());
            m_botAction.sendPrivateMessage(staffer, "Idle time for " + name + " is " + idleTime + " seconds.");
            idleTimeRequests.remove(name.toLowerCase());
        }
    }

}
