package twcore.bots.pubsystem;

import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import twcore.bots.Module;
import twcore.bots.pubsystem.game.AbstractGame;
import twcore.bots.pubsystem.module.AbstractModule;
import twcore.bots.pubsystem.module.PubChallengeModule;
import twcore.bots.pubsystem.module.PubKillSessionModule;
import twcore.bots.pubsystem.module.PubMoneySystemModule;
import twcore.bots.pubsystem.module.PubPlayerManagerModule;
import twcore.bots.pubsystem.module.PubStreakModule;
import twcore.bots.pubsystem.module.PubTilesetModule;
import twcore.bots.pubsystem.module.PubUtilModule;
import twcore.core.BotAction;
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
import twcore.core.util.Tools;

public class PubContext {
	
	private BotAction m_botAction;
	
	private boolean started = false;

	private boolean privFreqEnabled = false;

	private PubPlayerManagerModule playerManager;
	private PubMoneySystemModule moneySystem;
	private PubChallengeModule pubChallenge;
	private PubKillSessionModule pubKillSession;
	private PubTilesetModule pubTileset;
	private PubStreakModule pubStreak;
	private PubUtilModule pubUtil;
	
	private AbstractGame game;
	
	private Vector<AbstractModule> modules;
	
	public PubContext(BotAction botAction) 
	{
		this.m_botAction = botAction;
		
		this.modules = new Vector<AbstractModule>();
		
		// Instanciate (order matter)
		modules.add(getPlayerManager());
		modules.add(getMoneySystem());
		modules.add(getPubChallenge());
		modules.add(getPubStreak());
		modules.add(getPubUtil());
		modules.add(getPutTileset());
		modules.add(getPubKillSession());
		
	}
	
	public void start() {
		this.started = true;
		for(AbstractModule module: modules) {
			module.start();
		}
	}
	
	public void stop() {
		this.started = false;
	}
	
	public boolean isStarted() {
		return started;
	}

	public AbstractGame getGame() {
		return game;
	}
	
	public boolean isPrivFreqEnabled() {
		return privFreqEnabled;
	}
	
	public void setPrivFreqEnabled(boolean b) {
		this.privFreqEnabled = b;
	}
	
	public PubPlayerManagerModule getPlayerManager() {
		if (playerManager == null) {
			playerManager = new PubPlayerManagerModule(m_botAction, this);
		}
		return playerManager;
	}
	
	public PubMoneySystemModule getMoneySystem() {
		if (moneySystem == null) {
			moneySystem = new PubMoneySystemModule(m_botAction, this);
		}
		return moneySystem;
	}
	
	public PubTilesetModule getPutTileset() {
		if (pubTileset == null) {
			pubTileset = new PubTilesetModule(m_botAction);
		}
		return pubTileset;
	}
	
	public PubKillSessionModule getPubKillSession() {
		if (pubKillSession == null) {
			pubKillSession = new PubKillSessionModule(m_botAction, this);
		}
		return pubKillSession;
	}
	
	public PubStreakModule getPubStreak() {
		if (pubStreak == null) {
			pubStreak = new PubStreakModule(m_botAction, this);
		}
		return pubStreak;
	}
	
	public PubUtilModule getPubUtil() {
		if (pubUtil == null) {
			pubUtil = new PubUtilModule(m_botAction, this);
		}
		return pubUtil;
	}
	
	public PubChallengeModule getPubChallenge() {
		if (pubChallenge == null) {
			pubChallenge = new PubChallengeModule(m_botAction, this);
		}
		return pubChallenge;
	}
	
    public void handleEvent(SubspaceEvent event) {
    	
        Iterator<AbstractModule> iterator = modules.iterator();
        AbstractModule module;

        try {
            while(iterator.hasNext()) {
                module = (AbstractModule) iterator.next();
                module.handleEvent(event);
            }
        } catch (Exception e) {
        	displayException(e);
        }
    }
	
	public void handleCommand(String sender, String command) {

        Iterator<AbstractModule> iterator = modules.iterator();
        AbstractModule module;

        try {
            while(iterator.hasNext()) {
                module = (AbstractModule) iterator.next();
                module.handleCommand(sender, command);
            }
        } catch (Exception e) {
        	displayException(e);
        }
	}
	
	public void handleModCommand(String sender, String command) {
		
        Iterator<AbstractModule> iterator = modules.iterator();
        AbstractModule module;

        try {
            while(iterator.hasNext()) {
                module = (AbstractModule) iterator.next();
                module.handleModCommand(sender, command);
            }
        } catch (Exception e) {
        	displayException(e);
        }
	}
	
	public void handleEvent(SQLResultEvent event) {
		
        Iterator<AbstractModule> iterator = modules.iterator();
        AbstractModule module;

        try {
            while(iterator.hasNext()) {
                module = (AbstractModule) iterator.next();
                module.handleEvent(event);
            }
        } catch (Exception e) {
        	displayException(e);
        }
	}

	
	public void handleDisconnect() {
		
        Iterator<AbstractModule> iterator = modules.iterator();
        AbstractModule module;

        try {
            while(iterator.hasNext()) {
                module = (AbstractModule) iterator.next();
                module.handleDisconnect();
            }
        } catch (Exception e) {
        	displayException(e);
        }
	}
	
	private void displayException(Exception e) {
		String method = e.getStackTrace()[0].getMethodName();
		int line = e.getStackTrace()[0].getLineNumber();
		m_botAction.sendChatMessage(1, e.getClass().getSimpleName() + " caught, " + method + " at line " + line);
		Tools.printStackTrace(e);
	}


		
}
