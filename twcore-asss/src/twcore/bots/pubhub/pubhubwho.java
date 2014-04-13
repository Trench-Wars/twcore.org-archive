package twcore.bots.pubhub;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
/**
 * Receive player enter and leave information for EVERY arena. Intelligently
 * update the pubstats db when a player is thought to have logged or logged off.
 * 
 * @author WingZero
 * 
 */
public class pubhubwho extends PubBotModule {
    /**
     * This method initializes the pubhubwho module. It is called after
     * m_botAction has been initialized.
     * 
     */
    public void initializeModule() {
    }

    /**
     * Requests the events.
     */
    public void requestEvents(EventRequester er) {
    }

    /**
     * Cancel updates.
     */
    public void cancel() {
    }
}
