package twcore.bots.pubhub;

import twcore.bots.PubBotModule;
import twcore.core.*;

public class pubhubtk extends PubBotModule {

    private String botName;

    /**
     * This method initializes the pubhubtk module.  It is called after
     * m_botAction has been initialized.
     */
    public void initializeModule() {
        botName = m_botAction.getBotName();
    }


    /**
     * Requests the events.  No events.
     */
    public void requestEvents(EventRequester eventRequester) {
    }

    /**
     * Unimplemented.
     */
    public void cancel() {
    }
}