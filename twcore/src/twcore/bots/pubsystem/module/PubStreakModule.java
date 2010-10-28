package twcore.bots.pubsystem.module;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.PubUtilModule.Location;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubStreakModule extends AbstractModule {

	public final static int ARENA_TIMEOUT = 1 * Tools.TimeInMillis.MINUTE;

	private ConcurrentHashMap<String,Integer> winStreaks;
	private ConcurrentHashMap<String,Integer> loseStreaks;
	
	private int streakJump;
	private int winsStreakArenaAt;
	private int winsStreakZoneAt;
	private int winsStreakMoneyMultiplicator;
	private int streakBrokerBonus;
	
	private int bestWinStreak = 0;
	private int worstLoseStreak = 0;
	private PubPlayer bestWinStreakPlayer;
	private PubPlayer worstLoseStreakPlayer;
	
	private long lastArena = 0;
	private long lastZone = 0;
	
	private boolean moneyEnabled = false;

	public PubStreakModule(BotAction botAction, PubContext context) {
		
		super(botAction, context, "Streak");

		this.winStreaks = new ConcurrentHashMap<String,Integer>();
		this.loseStreaks = new ConcurrentHashMap<String,Integer>();

		m_botAction.scheduleTask(new SafeChecker(context), 10*Tools.TimeInMillis.SECOND, 3*Tools.TimeInMillis.SECOND);
		
		reloadConfig();
	}
	
	public void requestEvents(EventRequester eventRequester)
	{
		eventRequester.request(EventRequester.PLAYER_DEATH);
		eventRequester.request(EventRequester.PLAYER_LEFT);
	}


	public void handleEvent(PlayerLeft event) {
		Player player = m_botAction.getPlayer(event.getPlayerID());
		winStreaks.remove(player.getPlayerName());
		loseStreaks.remove(player.getPlayerName());
	}
    
    public void handleEvent(PlayerDeath event) {

    	if (!enabled)
    		return;
    	
    	Player killer = m_botAction.getPlayer(event.getKillerID());
        Player killed = m_botAction.getPlayer(event.getKilleeID());
        
		if (killer == null || killed == null)
			return;
        
        // Dueling? do nothing
        if (context.getPubChallenge().isDueling(killer.getPlayerName())
        		|| context.getPubChallenge().isDueling(killed.getPlayerName())) {
        		return;
        }
        
        // Same team? do nothing
        if (killer.getFrequency() == killed.getFrequency()) {
        	return;
        }

        // Already on the system?
        if (winStreaks.get(killer.getPlayerName()) == null) {
        	winStreaks.put(killer.getPlayerName(),0);
        	loseStreaks.put(killer.getPlayerName(),0);
        }
        if (winStreaks.get(killed.getPlayerName()) == null) {
        	winStreaks.put(killed.getPlayerName(),0);
        	loseStreaks.put(killed.getPlayerName(),0);
        }
        

        PubPlayer pubPlayerKiller = context.getPlayerManager().getPlayer(killer.getPlayerName());
        PubPlayer pubPlayerKilled = context.getPlayerManager().getPlayer(killed.getPlayerName());
        
        if (pubPlayerKiller == null || pubPlayerKilled == null) {
        	return;
        }
        
        boolean streakBroker = false;
        
        // Streak breaker??
        if (winStreaks.get(killed.getPlayerName()) >= winsStreakArenaAt) {
        	if (pubPlayerKiller != null) {
        		
        		int streak = winStreaks.get(killed.getPlayerName());
        		int moneybroker = streakBrokerBonus + streak*winsStreakMoneyMultiplicator;
        		
	        	announceStreakBreaker(pubPlayerKiller, pubPlayerKilled, streak, moneybroker);
	        	if (streakBrokerBonus > 0) {
	        		pubPlayerKiller.addMoney(streakBrokerBonus);
	        	}
        	}
        	
        	streakBroker = true;
        }
        
        int streak;
        
        // Updating stats for the killed (if not dueling)
        winStreaks.put(killed.getPlayerName(), 0);
        loseStreaks.put(killed.getPlayerName(), loseStreaks.get(killed.getPlayerName())+1);
        
        streak = loseStreaks.get(killed.getPlayerName());
        if (streak > worstLoseStreak) {
        	worstLoseStreak = streak;
        	worstLoseStreakPlayer = pubPlayerKilled;
        }
        
        // Updating stats for the killer (if not dueling)
        loseStreaks.put(killer.getPlayerName(), 0);
        winStreaks.put(killer.getPlayerName(), winStreaks.get(killer.getPlayerName())+1);
        
        streak = winStreaks.get(killer.getPlayerName());
        
        // Time to tell the player that SAFE are not allowed
        // Else, the player will lose his streak
        if (streak == winsStreakArenaAt) {
        	m_botAction.sendSmartPrivateMessage(killer.getPlayerName(), "You have kill " + winsStreakArenaAt + " times in a row, you are now not allowed to go in safe.");
        	m_botAction.sendSmartPrivateMessage(killer.getPlayerName(), "If you do, you will lose your streak. May the force be with you.");
        }
        
        // Is a streak worth to be said?
        if (streak >= winsStreakArenaAt) {

	        // Best of session?
	        if (streak > bestWinStreak) {
	        	bestWinStreak = streak;
		        bestWinStreakPlayer = pubPlayerKiller;
		        if (!streakBroker)
		        	announceWinStreak(pubPlayerKiller, streak);		        
	        }
	        else {
	        	if (!streakBroker)
	        		announceWinStreak(pubPlayerKiller, streak);
	        }
        }
        
        // Money gains by the killer?
        if (context.getMoneySystem().isEnabled()) {
	        int money = getMoney(winStreaks.get(killer.getPlayerName()));
	        if (money > 0 && moneyEnabled) {
	        	pubPlayerKiller.addMoney(money);
	        }
        }
  
    }
    
    private void saveBestStreak(PubPlayer player, Integer streak) {
		
    	if (streak > player.getBestStreak()) {
    		
    		player.setBestStreak(streak);
    		
    		String database = m_botAction.getBotSettings().getString("database");
    		
    		// The query will be closed by PlayerManagerModule
    		if (database!=null)
    		m_botAction.SQLBackgroundQuery(database, "", "UPDATE tblPlayerStats "
				+ "SET "
				+ "fdBestStreak = IF("+streak+">fnBestStreak ,NOW(),fdBestStreak),"
				+ "fnBestStreak = IF("+streak+">fnBestStreak,"+streak+",fnBestStreak) "
				+ "WHERE fcName='" + player.getPlayerName() + "'");
    		
    	}
    	
	}

	private void announceStreakBreaker(PubPlayer killer, PubPlayer killed, int streak, int money) {
    	String message = "[STREAK BREAKER!] " + killed.getPlayerName() + " (" + streak + " kills) broken by " + killer.getPlayerName() + "!";
    	if (context.getMoneySystem().isEnabled()) {
    		message += " (+$" + money + ")";
    	}
    	m_botAction.sendArenaMessage(message, Tools.Sound.INCONCEIVABLE);
    }

    private void announceWinStreak(PubPlayer player, int streak) {
    	
    	int money = getMoney(winStreaks.get(player.getPlayerName()));
    	String moneyMessage = "";
    	if (context.getMoneySystem().isEnabled()) {
    		moneyMessage = "(+$" + String.valueOf(money) + ")";
    	}
    	
    	// Arena?
    	if (streak >= winsStreakArenaAt) {
    		
    		/*
    		if (System.currentTimeMillis()-lastArena > ARENA_TIMEOUT || bestWinStreak==streak) {
    			String message = "[STREAK] " + player.getPlayerName() + " with " + streak + " kills! " + moneyMessage;
    			if (bestWinStreak==streak)
    				message += " Best Streak of the Session!";
    			m_botAction.sendArenaMessage(message);
    			lastArena = System.currentTimeMillis();
    		}
    		*/
    		
    		saveBestStreak(player, streak);
    		
    		// Arena only if "BEST OF THE SESSION" and if first *arena since ARENA_TIMEOUT
    		if (System.currentTimeMillis()-lastArena > ARENA_TIMEOUT && bestWinStreak==streak) {
    			m_botAction.sendArenaMessage("[STREAK] " + player.getPlayerName() + " with " + streak + " kills! Best Streak of the Session!");
    			lastArena = System.currentTimeMillis();
    		}
    	}
    	
    }
    
    /**
     * Return the money gains by a player for a streak
     * Encapsulate the algorithm used
     */
    private int getMoney(int streak) {

    	if (!moneyEnabled)
    		return 0;

    	if (streak >= winsStreakArenaAt) {
    		int diff = Math.min(50, streak)-winsStreakArenaAt+1;
    		return diff*winsStreakMoneyMultiplicator;
    	}
    	
    	return 0;    	
    }
    
    public void doStreakCmd( String sender, String name ) {

    	if (name.isEmpty()) {
    		
    		PubPlayer player = context.getPlayerManager().getPlayer(sender);
    		
    		if (winStreaks.containsKey(sender)) {
    			m_botAction.sendSmartPrivateMessage(sender, "Current streak: " + winStreaks.get(sender) + " kill(s).");
    		} else
    			m_botAction.sendSmartPrivateMessage(sender, "Current streak: none");
			if (player != null)
				m_botAction.sendSmartPrivateMessage(sender, "Best streak: " + player.getBestStreak() + " kill(s).");
    	}
    	else {
    		
    		PubPlayer player = context.getPlayerManager().getPlayer(name);
    		if (player != null) {
    		
    			name = player.getPlayerName();
	    		if (winStreaks.containsKey(name)) {
	    			m_botAction.sendSmartPrivateMessage(sender, "Current streak of " + name + ": " + winStreaks.get(name) + " kill(s).");
	    		} else {
	    			m_botAction.sendSmartPrivateMessage(sender, name + " has not streak yet.");
	    		}
    			m_botAction.sendSmartPrivateMessage(sender, "Best streak: " + player.getBestStreak() + " kill(s).");
    		
    		} else {
    			m_botAction.sendSmartPrivateMessage(sender, "Player not found.");
    		}
    		
    	}
    }

    public void doBestSessionStreakCmd( String sender ) {

    	if (bestWinStreakPlayer != null) {
    		m_botAction.sendSmartPrivateMessage(sender, "Best streak of the session: " + bestWinStreakPlayer.getPlayerName() + " with " + bestWinStreak + " kills.");
    	} else {
    		m_botAction.sendSmartPrivateMessage(sender, "There is no streak recorded yet.");
    	}
    }
    
    public void doSetStreakCmd( String sender, String command ) {
    	
    	command = command.substring(11).trim();
    	if (command.contains(":")) 
    	{
    		String[] split = command.split("\\s*:\\s*");
    		String name = split[0];
    		try {
	    		int streak = Integer.parseInt(split[1]);
	    		PubPlayer pubPlayer = context.getPlayerManager().getPlayer(name);
	    		if (pubPlayer != null) {
	    			pubPlayer.setBestStreak(streak);
	    		}
	    		winStreaks.put(pubPlayer.getPlayerName(), streak);
    		} catch (NumberFormatException e) {
    			m_botAction.sendSmartPrivateMessage(sender, "Error number!");
    			return;
    		}
        	m_botAction.sendSmartPrivateMessage(sender, "Done!");
        	doStreakCmd(sender, name);
    	} else {
    		m_botAction.sendSmartPrivateMessage(sender, "Error command!");
    	}
    	
    }
    
    public void doStreakResetCmd( String sender ) {

    	bestWinStreak = 0;
    	bestWinStreakPlayer = null;
    	
    	worstLoseStreak = 0;
    	worstLoseStreakPlayer = null;
    	
		winStreaks = new ConcurrentHashMap<String,Integer>();
		loseStreaks = new ConcurrentHashMap<String,Integer>();
    	
    	m_botAction.sendArenaMessage("[STREAK] The streak session has been reset.", Tools.Sound.BEEP2);
    }
    
	@Override
	public void handleCommand(String sender, String command) {

        if(command.trim().equals("!streak") || command.startsWith("!streak "))
        	doStreakCmd(sender, command.substring(7).trim());
        else if(command.trim().equals("!streakbest") || command.trim().equals("!beststreak"))
            	doBestSessionStreakCmd(sender);

	}

	@Override
	public void handleModCommand(String sender, String command) {
		
        if(command.equals("!streakreset"))
            doStreakResetCmd(sender);
        
        if(m_botAction.getOperatorList().isOwner(sender) && command.startsWith("!setstreak"))
            doSetStreakCmd(sender, command);
	}
	
	@Override
	public String[] getHelpMessage(String sender) {
		return new String[] {
			pubsystem.getHelpLine("!streak            -- Your current streak."),
			pubsystem.getHelpLine("!streak <name>     -- Best and current streak of a given player name."),
			pubsystem.getHelpLine("!beststreak        -- Current best streak of the session."),
        };
	}

	@Override
	public String[] getModHelpMessage(String sender) {
		return new String[] {
			pubsystem.getHelpLine("!streakreset       -- Reset the current session (with *arena)."),
        };
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void reloadConfig() {
		if (m_botAction.getBotSettings().getInt("streak_enabled")==1) {
			enabled = true;
		}
		if (m_botAction.getBotSettings().getInt("streak_money_enabled")==1) {
			moneyEnabled = true;
		}
		streakJump = m_botAction.getBotSettings().getInt("streak_jump");
		winsStreakArenaAt = m_botAction.getBotSettings().getInt("streak_wins_arena_at");
		winsStreakZoneAt = m_botAction.getBotSettings().getInt("streak_wins_zone_at");
		winsStreakMoneyMultiplicator = m_botAction.getBotSettings().getInt("streak_wins_money_multiplicator");
		streakBrokerBonus = m_botAction.getBotSettings().getInt("streak_broker_bonus");
	}

	@Override
	public void stop() {
		
	}
	
	private class SafeChecker extends TimerTask {

		private PubContext context;
		
		public SafeChecker(PubContext context) {
			this.context = context;
		}
		
		public void run() {
			Iterator<Entry<String,Integer>> it = winStreaks.entrySet().iterator();
			while(it.hasNext()) {
				Entry<String,Integer> entry = it.next();
				if (entry.getValue() >= winsStreakArenaAt) {
					Player p = m_botAction.getPlayer(entry.getKey());
					if (p != null) {
						Location loc = context.getPubUtil().getLocation(p.getXTileLocation(), p.getYTileLocation());
						if (loc.equals(Location.SAFE)) {
							
							m_botAction.sendSmartPrivateMessage(entry.getKey(), "You cannot go in safe with a streak higher than " + winsStreakArenaAt + " kills. You have lost your streak.");
							winStreaks.put(entry.getKey(), 0);
							
						}
					}
				}
			}
		}
	};

}
