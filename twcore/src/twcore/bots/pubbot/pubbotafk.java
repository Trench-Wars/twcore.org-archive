package twcore.bots.pubbot;

import java.util.Iterator;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.game.Player;
import twcore.core.util.Tools;

/**
 * Move players to the AFK_ARENA when they exceed the MAX_IDLE_TIME
 * 
 * @author fantus
 */
public class pubbotafk extends PubBotModule {
    private final static int MAX_IDLE_TIME = 60; //Time in minutes
    private final static String AFK_ARENA = "afk"; //Arena to where the players get moved
    private final static String AFK_MESSAGE = "NOTICE: You have been idle for too long. " +
    		"You have been moved to the \"afk\"-arena";
    
    private String zoneIP;
    private String zonePort;
    private TimerTask getIdleTimeTimer;

    public void cancel() {
        m_botAction.cancelTask(getIdleTimeTimer);
    }

    public void initializeModule() {
        zoneIP = m_botAction.getGeneralSettings().getString( "Server" );
        zonePort = m_botAction.getGeneralSettings().getString( "Port" );
        
        getIdleTimeTimer = new TimerTask() {
            public void run() {
                getIdleTime();
            }
        };
        m_botAction.scheduleTaskAtFixedRate(getIdleTimeTimer, Tools.TimeInMillis.SECOND, 5 * Tools.TimeInMillis.MINUTE);
    }

    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
    }
    
    public void handleEvent(Message event) {
        if (event.getMessageType() == Message.ARENA_MESSAGE)
        {
            String message = event.getMessage();
            
            //Retrieve Idle time
            if (message.indexOf("Idle: ") != -1) {
                int indexStart = message.indexOf("Idle: ") + 6;
                int indexEnd = message.indexOf(" s", indexStart);
                
                if (indexStart > -1 && indexEnd > -1) {
                    int idleTime = Integer.parseInt(message.substring(indexStart, indexEnd));
                    
                    //Check if idle time has been exceeded
                    if (idleTime >= (MAX_IDLE_TIME * 60)) {
                        String name = message.substring(0, message.indexOf(":"));
                        
                        //Notice the player
                        m_botAction.sendPrivateMessage(name, AFK_MESSAGE);
                        //Move the player to the afk arena
                        m_botAction.sendUnfilteredPrivateMessage(name,
                                "*sendto " + zoneIP + "," + zonePort + "," + AFK_ARENA);
                    }
                }
            }
        }
    }
    
    /**
     * Get the idle time info from all the players
     * 
     */
    public void getIdleTime() {
        for (Iterator<Player> it = m_botAction.getPlayerIterator(); it.hasNext();) {
            Player p = it.next();
            
            //Check if player is not a bot
            if (!m_botAction.getOperatorList().isBotExact(p.getPlayerName()))
                m_botAction.sendUnfilteredPrivateMessage(p.getPlayerID(), "*einfo");
        }
    }
}
