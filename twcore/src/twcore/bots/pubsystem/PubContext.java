package twcore.bots.pubsystem;

import twcore.bots.pubsystem.game.AbstractGame;
import twcore.bots.pubsystem.module.AbstractModule;
import twcore.bots.pubsystem.module.PubChallengeModule;
import twcore.bots.pubsystem.module.PubMoneySystemModule;
import twcore.bots.pubsystem.module.PubPlayerManagerModule;
import twcore.bots.pubsystem.module.PubStreakModule;
import twcore.bots.pubsystem.module.PubTilesetModule;
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
import twcore.core.events.ScoreReset;
import twcore.core.events.ScoreUpdate;
import twcore.core.events.SoccerGoal;
import twcore.core.events.WatchDamage;
import twcore.core.events.WeaponFired;

public class PubContext {
	
	private BotAction m_botAction;
	
	private boolean started = false;

	private boolean privFreqEnabled = false;

	private PubPlayerManagerModule playerManager;
	private PubMoneySystemModule moneySystem;
	private PubChallengeModule pubChallenge;
	private PubTilesetModule pubTileset;
	private PubStreakModule pubStreak;
	
	private AbstractGame game;
	
	private AbstractModule[] modules;
	
	public PubContext(BotAction botAction) 
	{
		this.m_botAction = botAction;
		
		// Instanciate
		getPlayerManager();
		getMoneySystem();
		getPutTileset();
		getPubStreak();
		getPubChallenge();
		
		// Order matter (espically for challenge and streak)
		modules = new AbstractModule[] { 
				playerManager,
				moneySystem,
				pubStreak,
				pubChallenge, 
				pubTileset 
		};
	}
	
	public void start() {
		this.started = true;
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
	
	public PubStreakModule getPubStreak() {
		if (pubStreak == null) {
			pubStreak = new PubStreakModule(m_botAction, this);
		}
		return pubStreak;
	}
	
	public PubChallengeModule getPubChallenge() {
		if (pubChallenge == null) {
			pubChallenge = new PubChallengeModule(m_botAction, this);
		}
		return pubChallenge;
	}
	
	public void handleCommand(String sender, String command) {
		for(AbstractModule m: modules)
			m.handleCommand(sender, command);
	}
	
	public void handleModCommand(String sender, String command) {
		for(AbstractModule m: modules)
			m.handleModCommand(sender, command);
	}
	
	public void handleEvent(Message event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}
	
	public void handleEvent(PlayerLeft event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}
	
	public void handleEvent(ArenaList event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}
	
	public void handleEvent(PlayerEntered event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(PlayerPosition event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(PlayerDeath event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(PlayerBanner event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(Prize event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(ScoreUpdate event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(WeaponFired event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(FrequencyChange event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(FrequencyShipChange event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(FileArrived event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(ArenaJoined event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(FlagVictory event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(FlagReward event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(ScoreReset event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(WatchDamage event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(SoccerGoal event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(BallPosition event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(FlagPosition event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(FlagDropped event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}

	public void handleEvent(FlagClaimed event) {
		for(AbstractModule m: modules)
			m.handleEvent(event);
	}
		
}
