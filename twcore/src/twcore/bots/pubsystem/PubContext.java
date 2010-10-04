package twcore.bots.pubsystem;

import java.util.Iterator;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.pubsystem.module.AbstractModule;
import twcore.bots.pubsystem.module.GameFlagTimeModule;
import twcore.bots.pubsystem.module.PubChallengeModule;
import twcore.bots.pubsystem.module.PubHuntModule;
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
	
	// True during the first 5 seconds
	// Reason: to avoid spamming the arena when the bot spawn
	private boolean hasJustStarted = true; 

	private Vector<AbstractModule> modules;
	
	// Modules
	private PubPlayerManagerModule playerManager;
	private PubMoneySystemModule moneySystem;
	private PubChallengeModule pubChallenge;
	private PubKillSessionModule pubKillSession;
	private PubStreakModule pubStreak;
	private PubUtilModule pubUtil;
	private PubHuntModule pubHunt;
	
	// Game module
	private GameFlagTimeModule gameFlagTime;

	
	public PubContext(BotAction botAction) 
	{
		this.m_botAction = botAction;
		
		this.modules = new Vector<AbstractModule>();
		
		// Instanciate (order matter)
		
		long start = System.currentTimeMillis();
		
		// Order matter (!help)
		getGameFlagTime();
		getPlayerManager();
		getMoneySystem();
		getPubChallenge();
		getPubHunt();
		getPubStreak();
		getPubKillSession();
		getPubUtil();
		
		
		int seconds = (int)(System.currentTimeMillis()-start)/1000;
		Tools.printLog("Modules(" + modules.size() + ") for pubsystem loaded in " + seconds + " seconds.");
		
	}
	
	public void start() {
		this.started = true;
		for(AbstractModule module: modules) {
			module.start();
		}
		TimerTask timer = new TimerTask() {
			public void run() {
				hasJustStarted = false;
			}
		};
		m_botAction.scheduleTask(timer, 5*Tools.TimeInMillis.SECOND);
	}
	
	public void stop() {
		this.started = false;
	}
	
	public void reloadConfig() {
		m_botAction.getBotSettings().reloadFile();
		for(AbstractModule module: modules) {
			try {
				module.reloadConfig();
			} catch (Exception e) {
				displayException(e);
			}
		}
	}
	
	public boolean hasJustStarted() {
		return hasJustStarted;
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
			modules.add(gameFlagTime);
		}
		return gameFlagTime;
	}
	
	public PubPlayerManagerModule getPlayerManager() {
		if (playerManager == null) {
			playerManager = new PubPlayerManagerModule(m_botAction, this);
			modules.add(playerManager);
		}
		return playerManager;
	}
	
	public PubMoneySystemModule getMoneySystem() {
		if (moneySystem == null) {
			moneySystem = new PubMoneySystemModule(m_botAction, this);
			modules.add(moneySystem);
		}
		return moneySystem;
	}
	
	public PubHuntModule getPubHunt() {
		if (pubHunt == null) {
			pubHunt = new PubHuntModule(m_botAction, this);
			modules.add(pubHunt);
		}
		return pubHunt;
	}
	
	public PubKillSessionModule getPubKillSession() {
		if (pubKillSession == null) {
			pubKillSession = new PubKillSessionModule(m_botAction, this);
			modules.add(pubKillSession);
		}
		return pubKillSession;
	}
	
	public PubStreakModule getPubStreak() {
		if (pubStreak == null) {
			pubStreak = new PubStreakModule(m_botAction, this);
			modules.add(pubStreak);
		}
		return pubStreak;
	}
	
	public PubUtilModule getPubUtil() {
		if (pubUtil == null) {
			pubUtil = new PubUtilModule(m_botAction, this);
			modules.add(pubUtil);
		}
		return pubUtil;
	}
	
	public PubChallengeModule getPubChallenge() {
		if (pubChallenge == null) {
			pubChallenge = new PubChallengeModule(m_botAction, this);
			modules.add(pubChallenge);
		}
		return pubChallenge;
	}
	
    public void handleEvent(SubspaceEvent event) {
    	
        Iterator<AbstractModule> iterator = modules.iterator();
        AbstractModule module;

        while(iterator.hasNext()) {
            module = (AbstractModule) iterator.next();
            try {
            	module.handleEvent(event);
            } catch (Exception e) {
            	displayException(e);
            }
        }

    }
	
	public void handleCommand(String sender, String command) {

        Iterator<AbstractModule> iterator = modules.iterator();
        AbstractModule module;

        while(iterator.hasNext()) {
            module = (AbstractModule) iterator.next();
            try {
            	module.handleCommand(sender, command);
            } catch (Exception e) {
            	displayException(e);
            }
        }

	}
	
	public void handleModCommand(String sender, String command) {
		
        Iterator<AbstractModule> iterator = modules.iterator();
        AbstractModule module;

        while(iterator.hasNext()) {
            module = (AbstractModule) iterator.next();
            try {
            	module.handleModCommand(sender, command);
            } catch (Exception e) {
            	displayException(e);
            }
        }
       
	}
	
	public void handleEvent(SQLResultEvent event) {
		
        Iterator<AbstractModule> iterator = modules.iterator();
        AbstractModule module;

        while(iterator.hasNext()) {
            module = (AbstractModule) iterator.next();
            try {
            	module.handleEvent(event);
            } catch (Exception e) {
            	displayException(e);
            }
        }
  
	}

	
	public void handleDisconnect() {
		
        Iterator<AbstractModule> iterator = modules.iterator();
        AbstractModule module;

        while(iterator.hasNext()) {
            module = (AbstractModule) iterator.next();
            try {
            	module.handleDisconnect();
            } catch (Exception e) {
            	displayException(e);
            }
        }
     
	}
	
	private void displayException(Exception e) {
		String method = e.getStackTrace()[0].getMethodName();
		String className = e.getStackTrace()[0].getClassName();
		int line = e.getStackTrace()[0].getLineNumber();
		m_botAction.sendChatMessage(1, e.getClass().getSimpleName() + " caught on " + className + ", " + method + " at line " + line);
		Tools.printStackTrace(e);
	}
	
}
