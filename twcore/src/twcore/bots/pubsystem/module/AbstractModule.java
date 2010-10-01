package twcore.bots.pubsystem.module;

import twcore.bots.pubsystem.PubContext;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.ArenaJoined;
import twcore.core.events.ArenaList;
import twcore.core.events.BallPosition;
import twcore.core.events.FileArrived;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FlagDropped;
import twcore.core.events.FlagPosition;
import twcore.core.events.FlagReward;
import twcore.core.events.FlagVictory;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerBanner;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.Prize;
import twcore.core.events.SQLResultEvent;
import twcore.core.events.ScoreReset;
import twcore.core.events.ScoreUpdate;
import twcore.core.events.SoccerGoal;
import twcore.core.events.SubspaceEvent;
import twcore.core.events.WatchDamage;
import twcore.core.events.WeaponFired;

/*
 * A module is something that can be disabled/enabled.
 * It has a functionnality to the pub system.
 * 
 * Some are more importants than others like :
 * - MoneySystem
 * - PlayerManager
 * 
 * Also, some modules are dependants to others module
 * Dependencies:
 * - TO BE COMPLETED
 * 
 */
public abstract class AbstractModule {

	protected BotAction m_botAction;
	protected PubContext context;
	
	protected String name;
	
	protected boolean enabled = false;
	
	public AbstractModule(BotAction botAction, PubContext context, String name) {
		this.m_botAction = botAction;
		this.context = context;
		this.name = name;
		requestEvents(m_botAction.getEventRequester());
	}
	
	public void enable() {
		this.enabled = true;
	}
	
	public void disable() {
		this.enabled = false;
	}
	
	public boolean isEnabled() {
		return enabled;
	}
	
	public String getName() {
		return name;
	}
	
	public abstract void start();
	
	public abstract void requestEvents(EventRequester eventRequester);
	public abstract void reloadConfig();
	
	public abstract void handleCommand(String sender, String command);
	public abstract void handleModCommand(String sender, String command);
	
	public abstract String[] getHelpMessage();
	public abstract String[] getModHelpMessage();
	
    /**
     * This method distributes the events to the appropriate event handlers.
     *
     * @param event is the event to distribute.
     */

    public void handleEvent(SubspaceEvent event)
    {
        if(event instanceof Message)
            handleEvent((Message) event);
        else if(event instanceof InterProcessEvent)
            handleEvent((InterProcessEvent) event);
        else if(event instanceof PlayerLeft)
            handleEvent((PlayerLeft) event);
        else if(event instanceof PlayerDeath)
            handleEvent((PlayerDeath) event);
        else if(event instanceof PlayerEntered)
            handleEvent((PlayerEntered) event);
        else if(event instanceof FrequencyChange)
            handleEvent((FrequencyChange) event);
        else if(event instanceof FrequencyShipChange)
            handleEvent((FrequencyShipChange) event);
        else if(event instanceof ArenaList)
            handleEvent((ArenaList) event);
        else if(event instanceof PlayerPosition)
            handleEvent((PlayerPosition) event);
        else if(event instanceof Prize)
            handleEvent((Prize) event);
        else if(event instanceof ScoreUpdate)
            handleEvent((ScoreUpdate) event);
        else if(event instanceof WeaponFired)
            handleEvent((WeaponFired) event);
        else if(event instanceof LoggedOn)
            handleEvent((LoggedOn) event);
        else if(event instanceof FileArrived)
            handleEvent((FileArrived) event);
        else if(event instanceof ArenaJoined)
            handleEvent((ArenaJoined) event);
        else if(event instanceof FlagVictory)
            handleEvent((FlagVictory) event);
        else if(event instanceof FlagReward)
            handleEvent((FlagReward) event);
        else if(event instanceof ScoreReset)
            handleEvent((ScoreReset) event);
        else if(event instanceof WatchDamage)
            handleEvent((WatchDamage) event);
        else if(event instanceof SoccerGoal)
            handleEvent((SoccerGoal) event);
        else if(event instanceof BallPosition)
            handleEvent((BallPosition) event);
        else if(event instanceof FlagPosition)
            handleEvent((FlagPosition) event);
        else if(event instanceof FlagDropped)
            handleEvent((FlagDropped) event);
        else if(event instanceof FlagClaimed)
            handleEvent((FlagClaimed) event);
        else if(event instanceof PlayerBanner)
            handleEvent((PlayerBanner) event);
    }
	
    /**
     * All of these stub functions handle the various events.
     */

    public void handleEvent(Message event){}
    
    public void handleEvent(ArenaList event){}

    public void handleEvent(PlayerEntered event){}

    public void handleEvent(PlayerPosition event){}

    public void handleEvent(PlayerLeft event){}

    public void handleEvent(PlayerDeath event){}
    
    public void handleEvent(PlayerBanner event){}

    public void handleEvent(Prize event){}

    public void handleEvent(ScoreUpdate event){}
    
    public void handleEvent(ScoreReset event){}
    
    public void handleEvent(SoccerGoal event){}

    public void handleEvent(WeaponFired event){}

    public void handleEvent(FrequencyChange event){}

    public void handleEvent(FrequencyShipChange event){}

    public void handleEvent(LoggedOn event){}

    public void handleEvent(FileArrived event){}

    public void handleEvent(ArenaJoined event){}

    public void handleEvent(WatchDamage event){}

    public void handleEvent(BallPosition event){}

    public void handleEvent(FlagPosition event){}

    public void handleEvent(FlagDropped event){}
    
    public void handleEvent(FlagVictory event){}

    public void handleEvent(FlagReward event){}
    
    public void handleEvent(FlagClaimed event){}
    
    public void handleEvent(InterProcessEvent event){}
	
	public void handleEvent(SQLResultEvent event) {}
	
	public void handleDisconnect() {}
	
}
