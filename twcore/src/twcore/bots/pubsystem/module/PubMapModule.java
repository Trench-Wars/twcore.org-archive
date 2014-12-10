package twcore.bots.pubsystem.module;

import java.util.TimerTask;

import twcore.bots.pubsystem.PubContext;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.util.Tools;

public class PubMapModule extends AbstractModule {

    private int timeDelay;      // minimum amount of time between door changes
    private long lastChange;
    
    private BotAction ba;
    private boolean inPub;

    public PubMapModule(BotAction botAction, PubContext context) {
        super(botAction, context, "PubMap");
        ba = botAction;
        inPub = ba.getArenaName().startsWith("(Public");
        lastChange = 0;
        timeDelay = 7;
        ba.setPlayerPositionUpdating(300);
        TimerTask initialize = new TimerTask() {
            public void run() {
                inPub = ba.getArenaName().startsWith("(Public");
                doPopCheck();
            }
        };
        
        ba.scheduleTaskAtFixedRate(initialize, 5000, Tools.TimeInMillis.MINUTE * 5);
    }
    
    @Override
    public void reloadConfig() {
    	
    }
    

    @Override
    public void start() {
        //doPopCheck();
    }

    @Override
    public void stop() {
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        EventRequester er = eventRequester;
        er.request(EventRequester.PLAYER_ENTERED);
        er.request(EventRequester.PLAYER_LEFT);
        er.request(EventRequester.FREQUENCY_SHIP_CHANGE);
        er.request(EventRequester.ARENA_JOINED);
        //er.request(EventRequester.PLAYER_POSITION);
    }

    @Override
    public void handleEvent(ArenaJoined event) {
        inPub = ba.getArenaName().startsWith("(Public");
    }

    public void handleEvent(PlayerEntered event) {
        if (!enabled || !inPub) return;
        if (event.getShipType() > 0)
            doPopCheck();
    }
    
    public void handleEvent(PlayerLeft event) {
        if (enabled && inPub)
            doPopCheck();
    }
    
    public void handleEvent(FrequencyShipChange event) {
        if (enabled && inPub)
            doPopCheck();
    }
    
    private void doPopCheck() {
        if (!enabled || !inPub) return;
        int pop = ba.getPlayingPlayers().size();
        long now = System.currentTimeMillis();
        
        if(lastChange != 0)
        	if (now <= (timeDelay * Tools.TimeInMillis.MINUTE) + lastChange) //hold base constant for timeDelay
        		return;
        
        if (pop >= 14) {
            ba.setDoors(0);
            //ba.sendArenaMessage("Base is now changing to SANTA size!", 2);
            lastChange = now;           
        }
        else if (pop < 14) {
            ba.setDoors(255);
            //m_botAction.sendArenaMessage("Base is now changing to ELF size!", 2);
            lastChange = now;
        }
    }
    
    @Override
    public void handleCommand(String sender, String command) {

    }

    @Override
    public void handleModCommand(String sender, String command) {
    }

    @Override
    public void handleSmodCommand(String sender, String command) {

    }
    
    @Override
    public String[] getHelpMessage(String sender) {
    	 return new String[]{};
    }

    @Override
    public String[] getModHelpMessage(String sender) {
        return new String[]{};
    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
    	return new String[]{};
    }

}
