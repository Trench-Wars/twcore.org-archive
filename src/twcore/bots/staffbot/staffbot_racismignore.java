/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package twcore.bots.staffbot;

import twcore.bots.Module;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.util.Spy;

/**
 *
 * @author SpookedOne
 */
public class staffbot_racismignore extends Module {
    
    final String[] helpSmod = {
            "----------------[ Racism Ignore: SMod+ ]-----------------",
            " !ignoreword <word>        - Ignores word from racism spies.",
            " !listignore               - List any ignored words on spies.",
            " !clearignore              - Clear any currently ignored words."
    };

    @Override
    public void initializeModule() {
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
    }
    
    @Override
    public void cancel() {
        Spy.clearIgnored();
    }
    
    public void handleMessage(Message event) {
        if (event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ||
                event.getMessageType() == Message.PRIVATE_MESSAGE) {
            String message = event.getMessage().toLowerCase();
            
            String name = event.getMessager();
            if (name == null) {
                name = m_botAction.getPlayerName(event.getPlayerID());
            }
            
            if (m_botAction.getOperatorList().isSmod(name)) {
                if (message.startsWith("!help")) {
                    m_botAction.smartPrivateMessageSpam(name, helpSmod);
                }else if (message.startsWith("!ignoreword ")) {
                    Spy.addIgnore(message.substring("!ignoreword ".length()));
                    m_botAction.sendPrivateMessage(name, "Ignore word added.");
                }else if (message.startsWith("!listignore")) {
                    m_botAction.sendPrivateMessage(name, "Ignoring: " + 
                            Spy.getIgnored().toString());
                }else if (message.startsWith("!clearignore")) {
                    Spy.clearIgnored();
                    m_botAction.sendPrivateMessage(name, "Ignore list cleared.");
                }
            }
        }
    }
}
