package twcore.bots.pubsystem.module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.Map.Entry;

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
	private LinkedHashSet<String> hunterWaitingList;
	
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
    
    public void doHuntDebugCmd(String name) {
    	
		for(HuntPlayer player: players.values()) {
			if (player.isPlaying())
				m_botAction.sendSmartPrivateMessage(name, "isPlaying: " + player.name);
		}
		
		System.out.println(preyToHunter.size());
		System.out.println(preyToHunter.keySet().size());
    	
		for(Entry<String, HuntPlayer> entry: preyToHunter.entrySet()) {
			String playerName = entry.getKey();
			m_botAction.sendSmartPrivateMessage(playerName, "preyToHunter: " + playerName);
			//m_botAction.sendSmartPrivateMessage(playerName, "preyToHunter: " + playerName + " hunted by " + preyToHunter.get(playerName).name);
		}
		
		for(String player: preyWaitingList) {
			m_botAction.sendSmartPrivateMessage(name, "preyWaitingList: " + player);
		}
    	
		for(String player: hunterWaitingList) {
			m_botAction.sendSmartPrivateMessage(name, "hunterWaitingList: " + player);
		}
    	
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
    	
    	prepareGame();
    }
    
    public void announceWinner(HuntPlayer[] winners, int freq) {

    	String winnersText = "";
    	for(HuntPlayer p: winners)
    		winnersText += ", " + p.name;
    	winnersText = winnersText.substring(2);
 
    	int money = winners[0].preyKilled * moneyWinner;
    	
    	String moneyMessage = "";
    	if (context.getMoneySystem().isEnabled()) {
    		moneyMessage = ", +$" + money;
    	}
    	
    	if (winners.length > 1)
    		m_botAction.sendArenaMessage("[HUNT] Winners (freq " + freq + "): " + winnersText + " (" + winners[0].preyKilled + " preys each" + moneyMessage + ")", Tools.Sound.HALLELUJAH);
    	else
    		m_botAction.sendArenaMessage("[HUNT] Winner (freq " + freq + "): " + winnersText + " (" + winners[0].preyKilled + " preys" + moneyMessage + ")", Tools.Sound.HALLELUJAH);
    	
    	for(HuntPlayer p: winners) {
    		if (context.getMoneySystem().isEnabled())
    			context.getPlayerManager().addMoney(p.name, money);
    		updateWinnerDB(p.name);
    		playerOut(p);
    	}
    	
    	stopGame();
    }
    
    public void announceWinner(HuntPlayer huntPlayer) {
    	announceWinner(huntPlayer, false);
    }
    
    public void announceWinner(HuntPlayer huntPlayer, boolean byForfeit) {
    	
		int money = huntPlayer.preyKilled * moneyWinner;
		
    	String moneyMessage = "";
    	if (context.getMoneySystem().isEnabled()) {
    		moneyMessage = ", +$" + money;
    	}
		
		if (byForfeit)
			m_botAction.sendArenaMessage("[HUNT] Winner by forfeit: " + huntPlayer.name + " (" + huntPlayer.preyKilled + " preys" + moneyMessage + ")", Tools.Sound.HALLELUJAH);
		else
			m_botAction.sendArenaMessage("[HUNT] Winner: " + huntPlayer.name + " (" + huntPlayer.preyKilled + " preys" + moneyMessage + ")", Tools.Sound.HALLELUJAH);
		
		if (context.getMoneySystem().isEnabled())
			context.getPlayerManager().addMoney(huntPlayer.name, money);
		
		updateWinnerDB(huntPlayer.name);
		
		playerOut(huntPlayer);
		
		stopGame();
    }
    
    private void updateWinnerDB(String playerName) {
    	
		String database = m_botAction.getBotSettings().getString("database");
		
		// The query will be closed by PlayerManagerModule
		if (database!=null)
		m_botAction.SQLBackgroundQuery(database, null, "UPDATE tblPlayerStats "
			+ "SET fnHuntWinner = fnHuntWinner+1 "
			+ "WHERE fcName='" + Tools.addSlashes(playerName) + "'");
    	
    }
    
    public void playerOut(HuntPlayer player) {
    	
    	m_botAction.sendPublicMessage("Player out: " + player.name);
    	
    	preyWaitingList.remove(player.name);
    	hunterWaitingList.remove(player.name);
    	preyToHunter.remove(player.prey);
    	preyToHunter.remove(player.name);
    	HuntPlayer hunter = preyToHunter.remove(player.name);
    	if (hunter != null && players.get(hunter.name).isPlaying())
    		hunterWaitingList.add(hunter.name);
    	if (player.prey != null && players.get(player.prey).isPlaying())
    		preyWaitingList.add(player.prey);
    	player.setPlaying(false);
    }

    public void handleEvent(PlayerDeath event) {
    	
        if (!isRunning)
            return;
        
        String killer = m_botAction.getPlayerName(event.getKillerID());
        String killed = m_botAction.getPlayerName(event.getKilleeID());
        
        if (players.containsKey(killer) && players.containsKey(killed)) {
        	
        	HuntPlayer hunter = players.get(killer);
        	HuntPlayer hunted = players.get(killed);
        	
        	hunter.kills++;
        	hunted.deaths++;
        	
        	if (hunter.hasPrey() && hunter.prey.equals(killed)) {
        		hunter.preyKilled++;
        	}
        	
        	// We have a winner?
        	if (hunter.hasPrey() && hunter.prey.equals(killed) && hunted.hasPrey() && hunted.prey.equals(killer) && hunterWaitingList.isEmpty()) {
        		
        		m_botAction.sendArenaMessage("[HUNT] " + killed + " has been hunted by " + killer + " and is out!");
        		playerOut(hunted);
        		playerOut(hunter);
        		announceWinner(hunter);

        	}
        	else if (hunter.hasPrey() && hunter.prey.equals(killed) && hunter.isPlaying()) {
        		
        		hunted.killer = killer;
        		playerOut(hunted);
        		setPrey(hunter);
        		
        		m_botAction.sendArenaMessage("[HUNT] " + killed + " has been hunted by " + killer + " and is out!");
        		
        		int money = hunted.preyKilled * moneyPerPrey;
        		if (money != 0 && context.getMoneySystem().isEnabled()) {
        			m_botAction.sendSmartPrivateMessage(killed, "Thank you for playing!. You have earned $" + money + " to have killed " + hunted.preyKilled + " prey(s).");
        			context.getPlayerManager().addMoney(killed, money);
        		} else {
        			m_botAction.sendSmartPrivateMessage(killed, "Thank you for playing!");
        		}
        		
        	} 
        	else if (hunted.hasPrey() && hunted.prey.equals(killer) && hunter.isPlaying()) {
        		m_botAction.sendSmartPrivateMessage(killer, "You killed your hunter!");
        	} 
        	else if (hunter.isPlaying()) {
        		m_botAction.sendSmartPrivateMessage(killer, "You killed an innocent bystander!");
        	}
        	
        }
       
    }
    
    public void handleEvent(PlayerLeft event) {
    	
        if (!isRunning)
            return;
        
        Player player = m_botAction.getPlayer(event.getPlayerID());
        HuntPlayer huntPlayerLeft = players.get(player.getPlayerName());
        if (huntPlayerLeft != null) {
        	HuntPlayer hunter = preyToHunter.get(huntPlayerLeft.name);
        	playerOut(huntPlayerLeft);
        	m_botAction.sendSmartPrivateMessage(hunter.name, "Your prey has left, please wait for a new prey.");
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
    	
        if (!isRunning)
            return;

        Player player = m_botAction.getPlayer(event.getPlayerID());
        HuntPlayer hunted = players.get(player.getPlayerName());
        
        if (hunted != null && hunted.isPlaying()) {
        	if (hunted.ship != 0 && player.getShipType() != 0) 
        	{
        		if (hunted.ship != event.getShipType()) {
        			m_botAction.setShip(event.getPlayerID(), hunted.ship);
        			m_botAction.sendSmartPrivateMessage(hunted.name, "You cannot change your ship during a game of hunt.");
        		}
        	} 
        	else if (player.getShipType() == 0)
        	{
            	HuntPlayer hunter = preyToHunter.get(hunted.name);
            	playerOut(hunted);
            	setPrey(hunter);
            	m_botAction.sendSmartPrivateMessage(hunter.name, "Your prey is now a spectator, please wait for a new prey.");
            	m_botAction.sendSmartPrivateMessage(hunted.name, "You cannot be a spectator during a game of hunt, you are out.");
            	checkForWinner();
        	}
        }

    }
    
    public boolean setPrey(HuntPlayer player) {
    	return setPrey(player, false);
    }
    
    public boolean setPrey(HuntPlayer player, boolean newGame) {
    	
    	Iterator<String> it;
    	it = preyWaitingList.descendingIterator();

    	String nameToRemove = null;
		while(it.hasNext()) {
			String name = it.next();
			HuntPlayer prey = players.get(name);
			if (player.freq != prey.freq && prey.isPlaying()) {
				player.setPrey(prey.name);
				preyToHunter.put(prey.name, player);
				player.tellPrey();
				nameToRemove = name;
				break;
			}
		}

		if (nameToRemove != null) {
			preyWaitingList.remove(nameToRemove);
			return true;
		}
		if (newGame)
			m_botAction.sendSmartPrivateMessage(player.name, "You don't have a prey yet, please wait..");
		return false;
    }
    
    public boolean isPlayerPlaying(String playerName) {
    	if (!isRunning)
    		return false;
    	return getPlayerPlaying(playerName) != null;
    }
    
    public boolean isRunning() {
    	return isRunning;
    }
    
    public HuntPlayer getPlayerPlaying(String playerName) {
    	if (!isRunning)
    		return null;
    	HuntPlayer player = players.get(playerName);
    	if (player != null && player.isPlaying()) {
    		return player;
    	}
    	return null;
    }

	@Override
	public String[] getHelpMessage(String sender) {
		return new String[] {
			pubsystem.getHelpLine("!prey             -- Show your current prey."),
			pubsystem.getHelpLine("!huntnp           -- If you don't want to play the game of hunt."),
        };
	}

	@Override
	public String[] getModHelpMessage(String sender) {
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
		else if(command.startsWith("!debughunt")) {
            doHuntDebugCmd(sender);
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
	
	public void prepareGame() {
		
    	players = new HashMap<String, HuntPlayer>();
    	
        Iterator<Player> it = m_botAction.getPlayingPlayerIterator();
        while(it.hasNext()) {
        	Player player = it.next();
        	String playerName = player.getPlayerName();
        	if (!playersNotPlaying.contains(playerName) && !context.getPubChallenge().isDueling(playerName)) {
        		players.put(playerName, new HuntPlayer(playerName));
        	}
        }
        
        if (players.size() > 1) {
        	m_botAction.sendArenaMessage("[HUNT] A game of hunt is starting in 10 seconds (!huntnp if not playing).");
        	m_botAction.sendArenaMessage("[HUNT] Winners will earn $" + moneyWinner + ".");
	        TimerTask timer = new TimerTask() {
				public void run() {
					startGame();
				}
			};
			m_botAction.scheduleTask(timer, 10*Tools.TimeInMillis.SECOND);
        }
        else {
        	m_botAction.sendSmartPrivateMessage(name, "You need more players to start a hunt game.");
        }
		
	}

	public void startGame() {
		
		if (!enabled) {
			return;
		}

		preyToHunter = new HashMap<String, HuntPlayer>();
		preyWaitingList = new TreeSet<String>();
		hunterWaitingList = new LinkedHashSet<String>();
		
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
				hunterWaitingList.add(player.name);
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
				hunterWaitingList.add(player.name);
			}
		}
		
		// Last check
		if (players.size() < 2 && (!strictMode && freqs.size() < 2))  {
			m_botAction.sendArenaMessage("[HUNT] The game of hunt has been cancelled.");
			stopGame();
			return;
		}
		
		m_botAction.sendArenaMessage("[HUNT] GO GO GO!", Tools.Sound.GOGOGO);

		isRunning = true;

		new PreyTask(true).run();
		
		preyTask = new PreyTask(false);
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
        	this.playing = playing;
        }
        
        public boolean hasPrey() {
        	return prey != null;
        }

        public void setPrey(String prey) {
            this.prey = prey;
        }

        public void tellPrey() {
            if (prey == null)
                return;
            m_botAction.sendSmartPrivateMessage(getPlayerName(), "Prey: " + prey + ".");
            m_botAction.sendPublicMessage(getPlayerName() + " -> " + prey);
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
    	
    	private boolean newGame = false;
    	
    	public PreyTask(boolean newGame) {
    		this.newGame = newGame;
    	}
    	
		public void run() {

			if (!hunterWaitingList.isEmpty()) {
				Iterator<String> it = hunterWaitingList.iterator();
				while(it.hasNext()) {
					String player = it.next();
					HuntPlayer huntPlayer = players.get(player);
					if (setPrey(huntPlayer,newGame)) {
						it.remove();
						continue;
					}
				}
			}

		}
    	
    }

	@Override
	public void stop() {
		stopGame();
	}

    @Override
    public void handleSmodCommand(String sender, String command) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[]{};
    }

}
