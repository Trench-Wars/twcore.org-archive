package twcore.bots.pubsystem.module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.TreeSet;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.util.Tools;

/*
 * Strict mode = everyone are set on their own frequency
 * Normal mode = everyone are set on their current freq (usually freq 0 vs freq 1)
 */

public class PubHuntModule extends AbstractModule {


	private HashMap<String, HuntPlayer> players;
	private HashMap<String, HuntPlayer> preyToHunter;
	private HashSet<String> playersNotPlaying;
	
	private TreeSet<String> preyWaitingList;
	private HashSet<String> hunterWaitingList;
	
	private PreyTask preyTask;
	
	private boolean strictMode = false;			// Players warped on their own freq
	private int moneyWinner = 0;				// Money earn by the winner
	private int moneyPerPrey = 0;
	
	private boolean isRunning = false;
	
	private boolean startBetweenFlagTimeGame = false;
	
	// This variable make sure to random the selection of prey
	private boolean setPreyReverse = false;
	
	public PubHuntModule(BotAction botAction, PubContext context) {
		super(botAction, context, "Hunt");
		players = new HashMap<String,HuntPlayer>();
		playersNotPlaying = new HashSet<String>();
		reloadConfig();
	}
	
    public void doTellPreyCmd(String name) {
    	
    	if (!isRunning) {
    		m_botAction.sendSmartPrivateMessage(name, "No game of hunt is running at this moment.");
    		return;
    	}
    	
        HuntPlayer player = players.get(name);
        if (player != null && player.isPlaying()) {
            m_botAction.sendSmartPrivateMessage(name, "You are currently hunting " + player.prey + ".");
            return;
        } else {
        	m_botAction.sendSmartPrivateMessage(name, "You are not playing!");
        }
    }
    
    public void doToggleStrictModeCmd(String name) {
        this.strictMode = !strictMode;
        if (strictMode)
        	m_botAction.sendSmartPrivateMessage(name, "Strict mode for hunt has been enabled.");
        else
        	m_botAction.sendSmartPrivateMessage(name, "Strict mode for hunt has been disabled.");
    }
    
    public void doToggleNotPlayingCmd(String name) {
    	System.out.println(name);
    	boolean playing = false;
        if (playersNotPlaying.contains(name)) {
        	playersNotPlaying.remove(name);
        	playing = true;
        } else {
        	playersNotPlaying.add(name);
        }
        if (playing)
        	m_botAction.sendSmartPrivateMessage(name, "You will play the game of hunt the next game.");
        else
        	m_botAction.sendSmartPrivateMessage(name, "You won't be in the list of players playing.");
    }
    
    public void doStopCmd(String name) {
    	
    	if (!isRunning) {
    		m_botAction.sendSmartPrivateMessage(name, "No game of hunt is running at this moment.");
    		return;
    	}
    	
    	m_botAction.sendArenaMessage("[HUNT] The current game of hunt has been cancelled.");
    	stopGame();
    	
    }
    
    public void doStartCmd(String name) {
    	
    	if (!enabled) {
    		m_botAction.sendSmartPrivateMessage(name, "The module 'hunt' is disabled.");
    		return;
    	}
    	
    	if (isRunning) {
    		m_botAction.sendSmartPrivateMessage(name, "A game of hunt is already running, use !stophunt.");
    		return;
    	}
    	
    	players = new HashMap<String, HuntPlayer>();
    	
        Iterator<Player> it = m_botAction.getPlayingPlayerIterator();
        while(it.hasNext()) {
        	Player player = it.next();
        	String playerName = player.getPlayerName();
        	if (!playersNotPlaying.contains(playerName)) {
        		players.put(playerName, new HuntPlayer(playerName));
        	}
        }
        
        if (players.size() > 1) {
	        m_botAction.sendArenaMessage("[HUNT] A game of hunt is starting in 10 seconds (!huntnp if not playing).");
	        TimerTask timer = new TimerTask() {
				public void run() {
					startGame();
				}
			};
			m_botAction.scheduleTask(timer, 10*Tools.TimeInMillis.SECOND);
        }
        else {
        	m_botAction.sendPrivateMessage(name, "You need more players to start a hunt game.");
        }
    }
    
    public void announceWinner(HuntPlayer[] winners, int freq) {

    	String winnersText = "";
    	for(HuntPlayer p: winners)
    		winnersText += ", " + p.name;
    	winnersText = winnersText.substring(2);
 
    	int money = winners[0].preyKilled * moneyWinner;
    	
    	if (winners.length > 1)
    		m_botAction.sendArenaMessage("[HUNT] Winners (freq " + freq + "): " + winnersText + " (" + winners[0].preyKilled + " preys each, +$" + money + ")", Tools.Sound.HALLELUJAH);
    	else
    		m_botAction.sendArenaMessage("[HUNT] Winner (freq " + freq + "): " + winnersText + " (" + winners[0].preyKilled + " preys, +$" + money + ")", Tools.Sound.HALLELUJAH);
    	
    	for(HuntPlayer p: winners)
    		context.getPlayerManager().addMoney(p.name, money);
    	
    	stopGame();
    }
    
    public void announceWinner(HuntPlayer huntPlayer) {
    	announceWinner(huntPlayer, false);
    }
    
    public void announceWinner(HuntPlayer huntPlayer, boolean byForfeit) {
    	
		int money = huntPlayer.preyKilled * moneyWinner;
		if (byForfeit)
			m_botAction.sendArenaMessage("[HUNT] Winner by forfeit: " + huntPlayer.name + " (" + huntPlayer.preyKilled + " preys, +$" + money + ")", Tools.Sound.HALLELUJAH);
		else
			m_botAction.sendArenaMessage("[HUNT] Winner: " + huntPlayer.name + " (" + huntPlayer.preyKilled + " preys, +$" + money + ")", Tools.Sound.HALLELUJAH);
		
		context.getPlayerManager().addMoney(huntPlayer.name, money);
		
		stopGame();
    }

    public void handleEvent(PlayerDeath event) {
    	
        if (!enabled || !isRunning)
            return;
        
        String killer = m_botAction.getPlayerName(event.getKillerID());
        String killed = m_botAction.getPlayerName(event.getKilleeID());
        
        if (players.containsKey(killer) && players.containsKey(killed)) {
        	
        	HuntPlayer huntKiller = players.get(killer);
        	HuntPlayer huntKilled = players.get(killed);
        	
        	huntKiller.kills++;
        	huntKilled.deaths++;
        	
        	// We have a winner?
        	if (huntKiller.prey.equals(killed) && huntKilled.prey.equals(killer) && hunterWaitingList.isEmpty()) {
        		
        		huntKiller.preyKilled++;

        		m_botAction.sendArenaMessage("[HUNT] " + killed + " has been hunted by " + killer + " and is out!");
  
        		huntKiller.setPlaying(false);
        		huntKilled.setPlaying(false);
        		preyToHunter.remove(killer);
        		preyToHunter.remove(killed);
        		
        		announceWinner(huntKiller);

        	}
        	else if (huntKiller.prey.equals(killed) && huntKiller.isPlaying()) {
        		
        		huntKiller.preyKilled++;
        		preyWaitingList.add(huntKilled.prey);
        		setPrey(huntKiller);
        		
        		huntKilled.setPlaying(false);
        		huntKilled.killer = killer;
        		m_botAction.sendArenaMessage("[HUNT] " + killed + " has been hunted by " + killer + " and is out!");
        		
        		int money = huntKilled.preyKilled * moneyPerPrey;
        		if (money != 0) {
        			m_botAction.sendPrivateMessage(killed, "Thank you for playing!. You have earned $" + money + " to have killed " + huntKilled.preyKilled + " prey(s).");
        			context.getPlayerManager().addMoney(killed, money);
        		} else {
        			m_botAction.sendPrivateMessage(killed, "Thank you for playing!");
        		}
        		
        	} 
        	else if (huntKilled.prey.equals(killer) && huntKiller.isPlaying()) {
        		m_botAction.sendSmartPrivateMessage(killer, "You killed your hunter!");
        	} 
        	else if (huntKiller.isPlaying()) {
        		m_botAction.sendSmartPrivateMessage(killer, "You killed an innocent bystander!");
        	}
        	
        }
       
    }
    
    public void handleEvent(PlayerLeft event) {
    	
        if (!enabled || !isRunning)
            return;
        
        Player player = m_botAction.getPlayer(event.getPlayerID());
        HuntPlayer huntPlayerLeft = players.get(player.getPlayerName());
        if (huntPlayerLeft != null) {
        	huntPlayerLeft.setPlaying(false);
        	HuntPlayer hunter = preyToHunter.remove(huntPlayerLeft.name);
        	hunterWaitingList.add(hunter.name);
        	preyToHunter.remove(huntPlayerLeft.prey);
        	if (hunter.prey.equals(huntPlayerLeft.name)) {
        		preyToHunter.remove(hunter.prey);
        	}
        	//m_botAction.sendArenaMessage("[HUNT] " + huntPlayerLeft.name + " is out!");
        	m_botAction.sendPrivateMessage(hunter.name, "Your prey has left, please wait for a new prey.");
        	checkForWinner();
        }

    }
    
    public void checkForWinner() {
    	
    	if (strictMode)
    	{
			Iterator<HuntPlayer> it = players.values().iterator();
			HuntPlayer lastPlayer = null;
			
			while(it.hasNext()) {
				HuntPlayer player = it.next();
				if (player.isPlaying()) {
					if (lastPlayer == null)
						lastPlayer = player;
					else
						return;
				}
			}
			announceWinner(lastPlayer, true);
    	}
    	else {
    		
    		if (preyToHunter.isEmpty()) {
    			
    			Iterator<HuntPlayer> it = players.values().iterator();
    			ArrayList<HuntPlayer> winners = new ArrayList<HuntPlayer>();
    			int freq = 0;
    			int maxPreyKilled = 0;
    			while(it.hasNext()) {
    				HuntPlayer player = it.next();
    				if (player.playing) {
    					winners.add(player);
    					freq = player.freq;
    					if (maxPreyKilled < player.preyKilled) {
    						maxPreyKilled = player.preyKilled;
    					}
    				}
    			}
    			
    			// Winners = highest prey killed
    			ArrayList<HuntPlayer> realWinners = new ArrayList<HuntPlayer>();
    			for(HuntPlayer p: winners) {
    				if (p.preyKilled == maxPreyKilled)
    					realWinners.add(p);
    			}
    			
    			announceWinner(realWinners.toArray(new HuntPlayer[realWinners.size()]), freq);
    			
    		}
    		
    	}
    }

    public void handleEvent(FrequencyShipChange event) {
    	
        if (!enabled || !isRunning)
            return;
        
        Player player = m_botAction.getPlayer(event.getPlayerID());
        HuntPlayer huntPlayer = players.get(player.getPlayerName());
        
        if (huntPlayer != null && huntPlayer.isPlaying()) {
        	if (huntPlayer.ship != 0 && player.getShipType() != 0) 
        	{
        		if (huntPlayer.ship != event.getShipType()) {
        			m_botAction.setShip(event.getPlayerID(), huntPlayer.ship);
        			m_botAction.sendPrivateMessage(huntPlayer.ship, "You cannot change your ship during a game of hunt.");
        		}
        	} 
        	else if (player.getShipType() == 0)
        	{
            	preyWaitingList.add(huntPlayer.prey);
            	HuntPlayer hunter = preyToHunter.remove(huntPlayer.name);
            	hunterWaitingList.add(hunter.name);
            	preyToHunter.remove(huntPlayer.prey);
            	if (hunter.prey.equals(huntPlayer.name)) {
            		preyToHunter.remove(hunter.prey);
            	}
            	m_botAction.sendPrivateMessage(hunter.name, "Your prey is now a spectator, please wait for a new prey.");
            	m_botAction.sendPrivateMessage(huntPlayer.name, "You cannot be a spectator during a game of hunt, you are out.");
            	huntPlayer.setPlaying(false);
            	checkForWinner();
        	}
        }

    }
    
    public boolean setPrey(HuntPlayer player) {
    	return setPrey(player, false);
    }
    
    public boolean setPrey(HuntPlayer player, boolean newGame) {
    	
    	setPreyReverse = !setPreyReverse;
    	Iterator<String> it;
    	if (setPreyReverse)
    		it = preyWaitingList.descendingIterator();
    	else
    		it = preyWaitingList.iterator();
    	
		while(it.hasNext()) {
			String name = it.next();
			HuntPlayer prey = players.get(name);
			if (player.freq != prey.freq && prey.isPlaying()) {
				player.setPrey(prey.name);
				System.out.println(player.name + ": " + prey.name);
				player.tellPrey();
				it.remove();
				return true;
			}
		}
		if (newGame)
			m_botAction.sendPrivateMessage(player.name, "You don't have a prey yet, please wait.");
    	hunterWaitingList.add(player.name);
		return false;
    }
    
    public boolean isPlayerPlaying(String playerName) {
    	return getPlayerPlaying(playerName) != null;
    }
    
    public boolean isRunning() {
    	return isRunning;
    }
    
    public HuntPlayer getPlayerPlaying(String playerName) {
    	HuntPlayer player = players.get(playerName);
    	if (player != null && player.isPlaying()) {
    		return player;
    	}
    	return null;
    }

	@Override
	public String[] getHelpMessage() {
		return new String[] {
			pubsystem.getHelpLine("!prey             -- Show your current prey."),
			pubsystem.getHelpLine("!huntnp           -- If you don't want to play the game of hunt."),
        };
	}

	@Override
	public String[] getModHelpMessage() {
		return new String[] {    
			pubsystem.getHelpLine("!starthunt        -- Start a game of hunt."),
			pubsystem.getHelpLine("!stophunt         -- Stop a game of hunt (will be announced as cancelled)."),
			pubsystem.getHelpLine("!huntnp           -- Toggle strict mode (own frequency for everyone)."),
        };
	}

    public void handleCommand(String sender, String command) {
    	
        if(command.equals("!prey")){
            doTellPreyCmd(sender);
        }
		else if(command.startsWith("!huntnp")) {
            doToggleNotPlayingCmd(sender);
        }

    }

	@Override
	public void handleModCommand(String sender, String command) {
        
		if(command.startsWith("!starthunt")) {
            doStartCmd(sender);
        }
		else if(command.startsWith("!huntstrictmode")) {
            doToggleStrictModeCmd(sender);
        }
		else if(command.startsWith("!stophunt")) {
            doStopCmd(sender);
        }
	}

	@Override
	public void reloadConfig() {
		
		moneyWinner = m_botAction.getBotSettings().getInt("hunt_winner_money");
		moneyPerPrey = m_botAction.getBotSettings().getInt("hunt_money_per_prey");
		
		if (m_botAction.getBotSettings().getInt("hunt_strictmode")==1) {
			strictMode = true;
		}
		if (m_botAction.getBotSettings().getInt("hunt_enabled")==1) {
			enabled = true;
		}
		if (m_botAction.getBotSettings().getInt("hunt_start_between_flagtime")==1) {
			startBetweenFlagTimeGame = true;
		}
	}

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.PLAYER_DEATH);
		eventRequester.request(EventRequester.PLAYER_LEFT);
		eventRequester.request(EventRequester.FREQUENCY_CHANGE);
		eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
		eventRequester.request(EventRequester.PLAYER_ENTERED);
	}
	
	@Override
	public void start() {
		
	}

	public void startGame() {
		
		if (!enabled) {
			return;
		}
		
		isRunning = true;

		preyToHunter = new HashMap<String, HuntPlayer>();
		preyWaitingList = new TreeSet<String>();
		hunterWaitingList = new HashSet<String>();
		
		HashSet<Integer> freqs = new HashSet<Integer>();
		
		// Set frequency
		if (strictMode) {

			int freq = 1000;
			Iterator<HuntPlayer> it = players.values().iterator();
			while(it.hasNext()) {
				HuntPlayer player = it.next();
				if (playersNotPlaying.contains(player.name)) {
					it.remove();
					continue;
				}
				player.freq = freq;
				m_botAction.setFreq(player.name, freq);
				preyWaitingList.add(player.name);
				freq++;
			}
		}
		else {
			
			Iterator<HuntPlayer> it = players.values().iterator();
			while(it.hasNext()) {
				HuntPlayer player = it.next();
				if (playersNotPlaying.contains(player.name)) {
					it.remove();
					continue;
				}
				Player p = m_botAction.getPlayer(player.name);
				player.freq = p.getFrequency();
				freqs.add(player.freq);
				preyWaitingList.add(player.name);
			}
		}
		
		// Last check
		if (players.size() < 2 && (!strictMode && freqs.size() < 2))  {
			m_botAction.sendArenaMessage("[HUNT] The game of hunt has been cancelled.");
			stopGame();
			return;
		}
		
		m_botAction.sendArenaMessage("[HUNT] GO GO GO!", Tools.Sound.GOGOGO);

		// Set preys!
		Iterator<HuntPlayer> it = players.values().iterator();
		while(it.hasNext()) {
			HuntPlayer player = it.next();
			setPrey(player, true);
		}
	
		preyTask = new PreyTask();
		m_botAction.scheduleTask(preyTask, 2000, 3 * Tools.TimeInMillis.SECOND);
		
	}
	
	public void stopGame() {
		try {
		preyTask.cancel();
		} catch(Exception e) {		
		}
		isRunning = false;
	}
	
    public class HuntPlayer {
        
        public String name;
        public String prey;
        
        // Settings
        public int freq;
        public int ship;
        
        // Stats
        public int preyKilled = 0;
        public int kills = 0;
        public int deaths = 0;
        public boolean playing = true;
        public String killer;
        
        public HuntPlayer(String name) {
            this.name = name;
        }
        
        public String getPlayerName() {
            return name;
        }
        
        public boolean isPlaying() {
        	return playing;
        }
        
        public void setPlaying(boolean playing) {
        	if (!playing) {
        		System.out.println(name + " is not playing anymore.");
        	}
        	this.playing = playing;
        }

        public void setPrey(String prey) {
            this.prey = prey;
            preyToHunter.put(prey, this);
        }

        public void tellPrey() {
            if (prey == null)
                return;
            m_botAction.sendSmartPrivateMessage(getPlayerName(), "Prey: " + prey + ".");
        }

        public int getTotalPreyKilled() {
            return preyKilled;
        }

        public void addPreyKilled() {
        	preyKilled++;
        }
        
        public void killed() {
        	playing = false;
        }

    }
    
    /*
     * Try to find a prey for a chaser without one
     */
    private class PreyTask extends TimerTask {
    	
		public void run() {
			
			if (!hunterWaitingList.isEmpty()) {
				Iterator<String> it = hunterWaitingList.iterator();
				while(it.hasNext()) {
					String player = it.next();
					HuntPlayer huntPlayer = players.get(player);
					if (setPrey(huntPlayer)) {
						it.remove();
						continue;
					}
				}
			}
			
		}
    	
    }

}
