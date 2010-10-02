package twcore.bots.pubsystem.module;

import java.util.HashMap;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubStreakModule extends AbstractModule {

	public final static int ARENA_TIMEOUT = 2 * Tools.TimeInMillis.MINUTE;
	public final static int ZONE_TIMEOUT = 15 * Tools.TimeInMillis.MINUTE;

	private HashMap<String,Integer> winStreaks;
	private HashMap<String,Integer> loseStreaks;
	
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

		this.winStreaks = new HashMap<String,Integer>();
		this.loseStreaks = new HashMap<String,Integer>();

		initialize();
	}
	
	public void requestEvents(EventRequester eventRequester)
	{
		eventRequester.request(EventRequester.PLAYER_DEATH);
		eventRequester.request(EventRequester.PLAYER_LEFT);
	}
	
	public void initialize() {
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

        PubPlayer pubPlayerKiller = context.getPlayerManager().getPlayer(killer.getPlayerName());
        PubPlayer pubPlayerKilled = context.getPlayerManager().getPlayer(killed.getPlayerName());
        
        // Already on the system?
        if (winStreaks.get(pubPlayerKiller.getPlayerName()) == null) {
        	winStreaks.put(pubPlayerKiller.getPlayerName(),0);
        	loseStreaks.put(pubPlayerKiller.getPlayerName(),0);
        }
        if (winStreaks.get(pubPlayerKilled.getPlayerName()) == null) {
        	winStreaks.put(pubPlayerKilled.getPlayerName(),0);
        	loseStreaks.put(pubPlayerKilled.getPlayerName(),0);
        }
        
        boolean streakBroker = false;
        
        // Streak breaker??
        if (winStreaks.get(pubPlayerKilled.getPlayerName()) >= winsStreakArenaAt) {
        	announceStreakBreaker(pubPlayerKiller, pubPlayerKilled, winStreaks.get(pubPlayerKilled.getPlayerName()));
        	if (streakBrokerBonus > 0) {
        		pubPlayerKiller.addMoney(streakBrokerBonus);
        	}
        	
        	streakBroker = true;
        }
        
        int streak;
        
        // Updating stats for the killed (if not dueling)
        if (!context.getPubChallenge().isDueling(pubPlayerKilled.getPlayerName())) 
        {
	        winStreaks.put(pubPlayerKilled.getPlayerName(), 0);
	        loseStreaks.put(pubPlayerKilled.getPlayerName(), loseStreaks.get(pubPlayerKilled.getPlayerName())+1);
	        
	        streak = loseStreaks.get(pubPlayerKilled.getPlayerName());
	        if (streak > worstLoseStreak) {
	        	worstLoseStreak = streak;
	        	worstLoseStreakPlayer = pubPlayerKilled;
	        }
        }
        
        // Updating stats for the killer (if not dueling)
        if (!context.getPubChallenge().isDueling(pubPlayerKiller.getPlayerName())) 
        {
	        loseStreaks.put(pubPlayerKiller.getPlayerName(), 0);
	        winStreaks.put(pubPlayerKiller.getPlayerName(), winStreaks.get(pubPlayerKiller.getPlayerName())+1);
	        
	        streak = winStreaks.get(pubPlayerKiller.getPlayerName());
	        
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
	        int money = getMoney(winStreaks.get(pubPlayerKiller.getPlayerName()));
	        if (money > 0 && moneyEnabled) {
	        	pubPlayerKiller.addMoney(money);
	        }
        }
        
    }
    
    private void announceStreakBreaker(PubPlayer killer, PubPlayer killed, int streak) {
    	String message = "[STREAK BREAKER!] " + killed.getPlayerName() + " (" + streak + " kills) broken by " + killer.getPlayerName() + "!";
    	if (streakBrokerBonus > 0) {
    		message += " (+$" + streakBrokerBonus + ")";
    	}
    	m_botAction.sendArenaMessage(message, Tools.Sound.INCONCEIVABLE);
    }

    private void announceWinStreak(PubPlayer player, int streak) {
    	
    	int money = getMoney(winStreaks.get(player.getPlayerName()));
    	String moneyMessage = "";
    	if (moneyEnabled) {
    		moneyMessage = "(+$" + String.valueOf(money) + ")";
    	}
    	
    	// Zone?
    	if (streak >= winsStreakZoneAt) {
    		
    		if ((streak-winsStreakZoneAt)%streakJump!=0) {
    			return;
    		}
    		
    		if (System.currentTimeMillis()-lastZone > ZONE_TIMEOUT) {
    			m_botAction.sendArenaMessage("[MASSIVE STREAK in pub!] " + player.getPlayerName() + " with " + streak + " kills.");
    			lastZone = System.currentTimeMillis();
    		}
    	}
    	// Arena?
    	else if (streak >= winsStreakArenaAt || bestWinStreak==streak) {
    		
    		// No *arena for a streak in the last interval or best streak?
    		// We may want to remove "if best streak", someone could easily spam the arena with tons of message
    		if (System.currentTimeMillis()-lastArena > ARENA_TIMEOUT || bestWinStreak==streak) {
    			String message = "[STREAK] " + player.getPlayerName() + " with " + streak + " kills! " + moneyMessage;
    			if (bestWinStreak==streak)
    				message += " Best Streak of the Session!";
    			m_botAction.sendArenaMessage(message);
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
    		int diff = streak-winsStreakArenaAt+1;
    		return diff*winsStreakMoneyMultiplicator;
    	}
    	
    	return 0;    	
    }
    
    public void doStreakCmd( String sender, String name ) {

    	if (name.isEmpty()) {
    		
    		if (winStreaks.containsKey(sender)) {
    			m_botAction.sendPrivateMessage(sender, "Current streak: " + winStreaks.get(sender) + " kill(s).");
    			m_botAction.sendPrivateMessage(sender, "Your next kill will get you $" + getMoney(winStreaks.get(sender)+1) + ".");
    		} else
    			m_botAction.sendPrivateMessage(sender, "You don't have any streak yet.");
    	}
    	else {
    		
    		PubPlayer player = context.getPlayerManager().getPlayer(name);
    		if (player != null) {
    		
    			name = player.getPlayerName();
	    		if (winStreaks.containsKey(name))
	    			m_botAction.sendPrivateMessage(sender, "Current streak of " + name + ": " + winStreaks.get(name) + " kill(s).");
	    		else
	    			m_botAction.sendPrivateMessage(sender, name + " has not streak yet.");
	    		}
    		
    		else {
    			m_botAction.sendPrivateMessage(sender, "Player not found.");
    		}
    		
    	}
    }

    public void doBestSessionStreakCmd( String sender ) {

    	if (bestWinStreakPlayer != null) {
    		m_botAction.sendPrivateMessage(sender, "Best streak of the session: " + bestWinStreakPlayer.getPlayerName() + " with " + bestWinStreak + " kills.");
    	} else {
    		m_botAction.sendPrivateMessage(sender, "There is no streak recorded yet.");
    	}
    }
    
    public void doStreakResetCmd( String sender ) {

    	bestWinStreak = 0;
    	bestWinStreakPlayer = null;
    	
    	worstLoseStreak = 0;
    	worstLoseStreakPlayer = null;
    	
		winStreaks = new HashMap<String,Integer>();
		loseStreaks = new HashMap<String,Integer>();
    	
    	m_botAction.sendArenaMessage("[STREAK] The streak session has been reset.", Tools.Sound.BEEP2);
    }
    
	@Override
	public void handleCommand(String sender, String command) {

        if(command.trim().equals("!streak") || command.startsWith("!streak "))
        	doStreakCmd(sender, command.substring(7).trim());
        else if(command.trim().equals("!streakbest"))
            	doBestSessionStreakCmd(sender);

	}

	@Override
	public void handleModCommand(String sender, String command) {
		
        if(command.equals("!streakreset"))
            doStreakResetCmd(sender);
	}
	
	@Override
	public String[] getHelpMessage() {
		return new String[] {
			pubsystem.getHelpLine("!streak            -- Your current streak."),
			pubsystem.getHelpLine("!streak <name>     -- Current streak of a given player name."),
			pubsystem.getHelpLine("!streakbest        -- Current best streak of the session."),
        };
	}

	@Override
	public String[] getModHelpMessage() {
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
		// TODO Auto-generated method stub
		
	}
    
}
