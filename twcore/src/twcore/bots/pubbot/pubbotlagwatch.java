package twcore.bots.pubbot;

import java.util.HashSet;
import java.util.HashMap;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.Message;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

/**
 * Lagchecks players on lagwatch who stay in the arena for a short time, for use in TWD/TWL matches later on.
 * @author qan
 */
public class pubbotlagwatch extends PubBotModule {
	
	private String PUBBOTS = "pubBots";
    private String webdb = "website";
	private HashSet<String> lagWatched;    // List of players on the lagwatch list
	private HashMap<String,LagWatchTimer> lagWatchTimers;
	private String lagChecking = "";       // Player presently being lagchecked
	
    public void initializeModule(){
        lagWatched = new HashSet<String>();
        lagWatchTimers = new HashMap<String,LagWatchTimer>();
        m_botAction.ipcTransmit(PUBBOTS, new IPCMessage("lagwatchrequest", getPubHubName()));
    }
    
    public void cancel(){
        lagWatched.clear();
    }

    public void requestEvents( EventRequester eventRequester ){
        eventRequester.request( EventRequester.PLAYER_ENTERED );
        eventRequester.request( EventRequester.PLAYER_LEFT );
        eventRequester.request( EventRequester.MESSAGE );
    }

    public void handleEvent( PlayerEntered event ){
    	Player p = m_botAction.getPlayer(event.getPlayerID());
    	if(p == null)return;
    	
    	if (lagWatched.contains(p.getPlayerName())) {
    	    LagWatchTimer lwt = new LagWatchTimer(p.getPlayerName());
    	    try {
    	        lagWatchTimers.put(p.getPlayerName(), lwt);
    	        m_botAction.scheduleTask(lwt, Tools.TimeInMillis.MINUTE * 3);
    	    } catch( Exception e) {
    	        m_botAction.cancelTask(lwt);
    	    }
    	}
    }
    
    public void handleEvent( PlayerLeft event ) {
        Player p = m_botAction.getPlayer(event.getPlayerID());
        if(p == null)return;
        
        if (lagWatched.contains(p.getPlayerName())) {
            try {
                LagWatchTimer lwt = lagWatchTimers.remove(p.getPlayerName());
                m_botAction.cancelTask(lwt);
            } catch (Exception e) {}
        }
    }
    
    public void handleEvent(InterProcessEvent event) {
      	try
          {
      		if(!(event.getObject() instanceof IPCMessage))return;
      		IPCMessage ipcMessage = (IPCMessage) event.getObject();
      		String message = ipcMessage.getMessage();
      		String recipient = ipcMessage.getRecipient();
      		String sender = ipcMessage.getSender();
      		if(recipient == null || recipient.equals(m_botAction.getBotName()))
      			handleBotIPC(sender, message);
        }
        catch(Exception e){
          Tools.printStackTrace(e);
        }
    }
    
    public void handleEvent(Message event) {
        String message = event.getMessage();

        if(message != null && event.getMessageType() == Message.ARENA_MESSAGE) {
            if(message.startsWith("PING Current:") && !lagChecking.equals("")) {
                try {
                    String query = "INSERT INTO tblLagWatchData (fcName,fcLagLine) VALUES ('" + Tools.addSlashesToString(lagChecking) + "','(" + m_botAction.getArenaName() + ")  " + message + "')";
                    m_botAction.SQLQueryAndClose(webdb, query );
                } catch(Exception e) {
                    Tools.printLog("Trouble logging lagwatched player '" + lagChecking + "' to database." );
                }
                lagChecking = "";
            }
        }

    }
    
    public void handleBotIPC(String sender, String message){
    	if(message.startsWith("lagwatchon "))
            doLagWatchOn(message.substring(11));
        else if(message.startsWith("lagwatchoff "))
            doLagWatchOn(message.substring(12));
    }
    
    public void doLagWatchOn(String message) {
        try {
            lagWatched.add(message);
        } catch(Exception e) {}
    }

    public void doLagWatchOff(String message) {
        try {
            lagWatched.remove(message);
        } catch(Exception e) {}
    }
    
    class LagWatchTimer extends TimerTask {
        String player;
        
        public LagWatchTimer( String player ) {
            this.player = player;
        }
        
        public void run() {
            Player p = m_botAction.getPlayer(player);
            if (p == null)
                return;
            lagChecking = player;
            m_botAction.sendUnfilteredPrivateMessage(player, "*lag");
        }
    }
}

