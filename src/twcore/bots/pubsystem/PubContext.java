package twcore.bots.pubsystem;

import java.util.Iterator;
import java.util.Vector;

import twcore.bots.pubsystem.module.AbstractModule;
import twcore.bots.pubsystem.module.GameFlagTimeModule;
import twcore.bots.pubsystem.module.PubChallengeModule;
import twcore.bots.pubsystem.module.PubKillSessionModule;
import twcore.bots.pubsystem.module.PubMoneySystemModule;
import twcore.bots.pubsystem.module.PubPlayerManagerModule;
import twcore.bots.pubsystem.module.PubStreakModule;
import twcore.bots.pubsystem.module.PubUtilModule;
import twcore.core.BotAction;
import twcore.core.events.SQLResultEvent;
import twcore.core.events.SubspaceEvent;
import twcore.core.util.Tools;

public class PubContext {
	
	private BotAction m_botAction;
	
	private boolean started = false;

	private Vector<AbstractModule> modules;
	
	// Modules
	private PubPlayerManagerModule playerManager;
	private PubMoneySystemModule moneySystem;
	private PubChallengeModule pubChallenge;
	private PubKillSessionModule pubKillSession;
	private PubStreakModule pubStreak;
	private PubUtilModule pubUtil;
	
	// Game module
	private GameFlagTimeModule gameFlagTime;

	
	public PubContext(BotAction botAction) 
	{
		this.m_botAction = botAction;
		
		this.modules = new Vector<AbstractModule>();
		
		// Instanciate (order matter)
		modules.add(getGameFlagTime());
		modules.add(getPlayerManager());
		modules.add(getMoneySystem());
		modules.add(getPubChallenge());
		modules.add(getPubStreak());
		modules.add(getPubKillSession());
		modules.add(getPubUtil());
		
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
	
	public Vector<AbstractModule> getModules() {
		return modules;
	}

	public GameFlagTimeModule getGameFlagTime() {
		if (gameFlagTime == null) {
			gameFlagTime = new GameFlagTimeModule(m_botAction, this);
		}
		return gameFlagTime;
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
