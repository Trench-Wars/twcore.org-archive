package twcore.bots.pubsystem;

import java.util.HashMap;

import twcore.core.BotAction;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubStreak {

	public final static int ARENA_TIMEOUT = 30 * Tools.TimeInMillis.SECOND;
	public final static int ZONE_TIMEOUT = 15 * Tools.TimeInMillis.MINUTE;
	
	public BotAction m_botAction;
	private PubPlayerManager playerManager;
	
	private HashMap<String,Integer> winStreaks;
	private HashMap<String,Integer> loseStreaks;
	
	private int streakJump;
	private int winsStreakArenaAt;
	private int winsStreakZoneAt;
	private int winsStreakMoneyMultiplicator;
	
	private PubPlayer bestWinStreak;
	private PubPlayer worstLoseStreak;
	
	private long lastArena = 0;
	private long lastZone = 0;
	
	private boolean enabled = false;
	private boolean moneyEnabled = false;
	
	public PubStreak(BotAction botAction, PubPlayerManager manager) {
		this.m_botAction = botAction;
		this.playerManager = manager;
		
		this.winStreaks = new HashMap<String,Integer>();
		this.loseStreaks = new HashMap<String,Integer>();
		
		initialize();
	}
	
	public void initialize() {
		if (m_botAction.getBotSettings().getInt("streak_enabled")==1) {
			enabled = true;
		}
		if (m_botAction.getBotSettings().getInt("streak_money")==1) {
			moneyEnabled = true;
		}
		streakJump = m_botAction.getBotSettings().getInt("streak_jump");
		winsStreakArenaAt = m_botAction.getBotSettings().getInt("wins_streak_arena_at");
		winsStreakZoneAt = m_botAction.getBotSettings().getInt("wins_streak_zone_at");
		winsStreakMoneyMultiplicator = m_botAction.getBotSettings().getInt("wins_streak_money_multiplicator");
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
        
        PubPlayer pubPlayerKiller = playerManager.getPlayer(killer.getPlayerName());
        PubPlayer pubPlayerKilled = playerManager.getPlayer(killed.getPlayerName());
        
        // Already on the system?
        if (winStreaks.get(pubPlayerKiller.getPlayerName()) == null) {
        	winStreaks.put(pubPlayerKiller.getPlayerName(),0);
        	loseStreaks.put(pubPlayerKiller.getPlayerName(),0);
        }
        if (winStreaks.get(pubPlayerKilled.getPlayerName()) == null) {
        	winStreaks.put(pubPlayerKilled.getPlayerName(),0);
        	loseStreaks.put(pubPlayerKilled.getPlayerName(),0);
        }
        
        // Streak breaker??
        if (winStreaks.get(pubPlayerKilled.getPlayerName()) >= winsStreakArenaAt) {
        	announceStreakBreaker(pubPlayerKiller, pubPlayerKilled, winStreaks.get(pubPlayerKilled.getPlayerName()));
        }
        
        // Killed
        winStreaks.put(pubPlayerKilled.getPlayerName(), 0);
        loseStreaks.put(pubPlayerKilled.getPlayerName(), loseStreaks.get(pubPlayerKilled.getPlayerName())+1);
        if (worstLoseStreak != null) {
	        if (loseStreaks.get(pubPlayerKilled.getPlayerName())+1 > loseStreaks.get(worstLoseStreak.getPlayerName())) {
	        	worstLoseStreak = pubPlayerKilled;
	        }
        } else {
        	worstLoseStreak = pubPlayerKilled;
        }
        
        // Killer
        loseStreaks.put(pubPlayerKiller.getPlayerName(), 0);
        winStreaks.put(pubPlayerKiller.getPlayerName(), winStreaks.get(pubPlayerKiller.getPlayerName())+1);
        if (bestWinStreak != null) {
	        if (winStreaks.get(pubPlayerKiller.getPlayerName())+1 > winStreaks.get(bestWinStreak.getPlayerName())) {
	        	bestWinStreak = pubPlayerKiller;
	        	announceWinStreak(pubPlayerKiller, winStreaks.get(pubPlayerKiller.getPlayerName()),true);
	        }
	        else {
	        	announceWinStreak(pubPlayerKiller, winStreaks.get(pubPlayerKiller.getPlayerName()),false);
	        }
        } else {
        	bestWinStreak = pubPlayerKiller;
        	announceWinStreak(pubPlayerKiller, winStreaks.get(pubPlayerKiller.getPlayerName()),true);
        }
        
        // Money gains?
        int money = getMoney(winStreaks.get(pubPlayerKiller.getPlayerName()));
        if (money > 0) {
        	pubPlayerKiller.addMoney(money);
        }
        
    }

    private void announceStreakBreaker(PubPlayer killer, PubPlayer killed, int streak) {
    	m_botAction.sendArenaMessage("[STREAK BREAKER!] " + killed.getPlayerName() + " (" + streak + " kills) broken by " + killer.getPlayerName() + "!", Tools.Sound.INCONCEIVABLE);
    }

    private void announceWinStreak(PubPlayer player, int streak, boolean best) {
    	
    	int money = getMoney(winStreaks.get(player.getPlayerName()));
    	
    	// Zone?
    	if (streak >= winsStreakZoneAt) {
    		
    		if ((streak-winsStreakZoneAt)%streakJump!=0) {
    			return;
    		}
    		
    		if (System.currentTimeMillis()-lastZone > ZONE_TIMEOUT) {
    			m_botAction.sendArenaMessage("[ZONE - STREAK!] " + player.getPlayerName() + " with " + streak + " kills! ($" + String.valueOf(money) + ")");
    			lastZone = System.currentTimeMillis();
    		}
    	}
    	// Arena?
    	else if (streak >= winsStreakArenaAt) {
    		
    		if (System.currentTimeMillis()-lastArena > ARENA_TIMEOUT) {
    			m_botAction.sendArenaMessage("[ARENA - STREAK!] " + player.getPlayerName() + " with " + streak + " kills! ($" + String.valueOf(money) + ")");
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
    		int diff = streak-winsStreakArenaAt;
    		return diff*winsStreakMoneyMultiplicator;
    	}
    	
    	return 0;    	
    }
    
}
