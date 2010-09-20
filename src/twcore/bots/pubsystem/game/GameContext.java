package twcore.bots.pubsystem.game;

import twcore.bots.pubsystem.PubMoneySystem;
import twcore.bots.pubsystem.PubPlayerManager;
import twcore.bots.pubsystem.PubStreak;
import twcore.bots.pubsystem.PubTileset;
import twcore.bots.pubsystem.PubWarp;
import twcore.core.BotAction;

public class GameContext {
	
	private BotAction m_botAction;

	public static enum Mode { FLAG_TIME, STRICT_FLAG_TIME };
	
	private Mode mode;
	private boolean started = false;
	private boolean flagTimeStarted = false;
	private boolean privFreqEnabled = false;
	
	private PubPlayerManager playerManager;
	private PubMoneySystem moneySystem;
	private PubTileset pubTileset;
	private PubStreak pubStreak;
	private PubWarp pubWarp;
	
	public GameContext(BotAction botAction) {
		this.m_botAction = botAction;
		this.mode = Mode.FLAG_TIME;
		
		// Instanciate
		getPlayerManager();
		getMoneySystem();
		getPutTileset();
		getPubStreak();
		getPubWarp();
	}
	
	public void start() {
		this.started = true;
	}
	
	public void stop() {
		this.started = false;
	}
	
	public void startFlagTimeStarted() {
		this.flagTimeStarted = true;
	}
	
	public void stopFlagTimeStarted() {
		this.flagTimeStarted = false;
	}

	public boolean isStarted() {
		return started;
	}
	
	public boolean isFlagTimeStarted() {
		return flagTimeStarted;
	}
	
	public boolean isPrivFreqEnabled() {
		return privFreqEnabled;
	}
	
	public Mode getMode() {
		return mode;
	}
	
	public boolean isMode(Mode mode) {
		return this.mode.equals(mode);
	}
	
	public void setMode(Mode mode) {
		this.mode = mode;
	}
	
	public void setPrivFreqEnabled(boolean b) {
		this.privFreqEnabled = b;
	}
	
	public PubPlayerManager getPlayerManager() {
		if (playerManager == null) {
			playerManager = new PubPlayerManager(m_botAction, this);
		}
		return playerManager;
	}
	
	public PubMoneySystem getMoneySystem() {
		if (moneySystem == null) {
			moneySystem = new PubMoneySystem(m_botAction, getPlayerManager());
		}
		return moneySystem;
	}
	
	public PubTileset getPutTileset() {
		if (pubTileset == null) {
			pubTileset = new PubTileset(m_botAction);
		}
		return pubTileset;
	}
	
	public PubStreak getPubStreak() {
		if (pubStreak == null) {
			pubStreak = new PubStreak(m_botAction, playerManager);
		}
		return pubStreak;
	}
	
	public PubWarp getPubWarp() {
		if (pubWarp == null) {
			pubWarp = new PubWarp(m_botAction, playerManager, this);
		}
		return pubWarp;
	}
		
}
