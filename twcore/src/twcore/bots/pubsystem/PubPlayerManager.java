package twcore.bots.pubsystem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.pubsystem.game.GameContext;
import twcore.core.BotAction;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.SQLResultEvent;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubPlayerManager {

    private static final int MSG_AT_FREQSIZE_DIFF = 4;  // Max # difference in size of freqs before
    													//   bot requests players even frequencies.
    													//   Value of -1 disables this feature.
    private static final int KEEP_MVP_FREQSIZE_DIFF = 2;// Max # difference in size of freqs required
                                                        //   for a player to keep MVP/get bonus on switching.

    private static final int NICEGUY_BOUNTY_AWARD = 25; // Bounty given to those that even freqs/ships

	private BotAction m_botAction;
	private GameContext context;
	
    private HashMap<String, PubPlayer> players;         // Always lowercase!
    private HashSet<String> freq0;                   // Players on freq 0
    private HashSet<String> freq1;                   // Players on freq 1
    
    private boolean teamsUneven;                        // True if teams are uneven as given in MAX_FREQSIZE_DIFF
    private int[] freqSizeInfo = {0, 0};                // Index 0: size difference; 1: # of smaller freq
   
    
    private Vector<Integer> shipWeight; //
	
	private String databaseName;
	
	private SavePlayersTask saveTask = new SavePlayersTask();
	private int SAVETASK_INTERVAL = 1; // minutes
	
	public PubPlayerManager(BotAction m_botAction) {
		this(m_botAction, null);
	}
	
	public PubPlayerManager(BotAction m_botAction, GameContext context) 
	{
		this.m_botAction = m_botAction;
		this.context = context;
		this.databaseName = m_botAction.getBotSettings().getString("database");
		this.players = new HashMap<String, PubPlayer>();
		this.shipWeight = new Vector<Integer>();
		this.freq0 = new HashSet<String>();
		this.freq1 = new HashSet<String>();
		
		m_botAction.scheduleTaskAtFixedRate(saveTask, SAVETASK_INTERVAL * Tools.TimeInMillis.MINUTE, SAVETASK_INTERVAL * Tools.TimeInMillis.MINUTE);
		
        shipWeight.add(new Integer(1));		// Allow unlimited number of spec players
        for(int i = 1; i < 9; i++) {
            shipWeight.add( new Integer(m_botAction.getBotSettings().getInt(m_botAction.getBotName() + "Ship" + i)));
        }
	}

	/**
	 * Retrieve a player on the cache only (if the player is currently playing)
	 * or by requesting the database. You may only want to request the database
	 * if you want to get information about a player not playing.
	 */
	public PubPlayer getPlayer(String playerName, boolean cacheOnly) 
	{
		PubPlayer player = players.get(playerName.toLowerCase());
		
		if (cacheOnly) {
			return player;
		}
		else {
			
			if (player != null)
				return player;
			
			if (databaseName != null) {
	    		try {
					ResultSet rs = m_botAction.SQLQuery(databaseName, "SELECT fcName, fnMoney FROM tblPlayerMoney WHERE fcName = '"+Tools.addSlashes(playerName)+"'");
					if (rs.next()) {
						String name = rs.getString("fcName");
						int money = rs.getInt("fnMoney");
						players.put(name.toLowerCase(), new PubPlayer(m_botAction, name, money));
					}
					rs.close();
				} catch (SQLException e) {
					Tools.printStackTrace(e);
				}
			}

			return players.get(playerName.toLowerCase());
		}
	}
	
	public PubPlayer getPlayer(String name) {
		return getPlayer(name, true);
	}
	
	public boolean isPlayerExists(String name) {
		return players.containsKey(name.toLowerCase());
	}
	
	public boolean isPlayerOnFreq(String name, int freq) {
		if (freq == 0)
			return freq0.contains(players.get(name));
		if (freq == 1)
			return freq1.contains(players.get(name));
		return false;
	}
	
	public void handleEvent(PlayerEntered event) {

    	Player p = m_botAction.getPlayer(event.getPlayerID());
    	String playerName = p.getPlayerName();
    	PubPlayer pubPlayer = addPlayerToSystem(playerName);
    	
    	if (pubPlayer != null)
    		pubPlayer.setIsOnline(true);
    	
        String restrictions = "";
        int weight;

        for(int i=1; i<9; i++) {
            weight = shipWeight.get(i).intValue();
            if( weight == 0 )
                restrictions += Tools.shipName( i ) + "s disabled.  ";
            if( weight > 1 )
                restrictions += Tools.shipName( i ) + "s limited.  ";
        }

        if( restrictions != "" )
            m_botAction.sendPrivateMessage(playerName, "Ship restrictions: " + restrictions );

        if (context.isStarted()) {
	        checkPlayer(event.getPlayerID());
	        if(!context.isPrivFreqEnabled()) {
	            checkFreq(event.getPlayerID(), p.getFrequency(), false);
	            checkFreqSizes();
	        }
        }
        
	}
	
	public void handleEvent(PlayerLeft event) {
		
    	Player p = m_botAction.getPlayer(event.getPlayerID());
    	String playerName = p.getPlayerName();
    	PubPlayer pubPlayer = players.get(playerName.toLowerCase());
    	if (pubPlayer != null)
    		pubPlayer.setIsOnline(false);		
    	
        removeFromLists(playerName);
        checkFreqSizes();
	}
	
	public void handleEvent(FrequencyChange event) {
		
        int playerID = event.getPlayerID();
        int freq = event.getFrequency();

        if(context.isStarted()) {
            checkPlayer(playerID);
            if(!context.isPrivFreqEnabled()) {
                checkFreq(playerID, freq, true);
                checkFreqSizes();
            }
        }
        
	}
	
    public void handleEvent(FrequencyShipChange event) {

        int playerID = event.getPlayerID();
        int freq = event.getFrequency();
        Player p = m_botAction.getPlayer(playerID);
    	
		PubPlayer pubPlayer = players.get(p.getPlayerName().toLowerCase());
		if (pubPlayer!=null) {
			pubPlayer.handleShipChange(event);
		}

		if (context.isStarted()) {
			checkPlayer(playerID);
			if (!context.isPrivFreqEnabled()) {
				checkFreq(playerID, freq, true);
			}
		}
    }
    
    public void handleDisconnect() {
    	saveTask.run();
    }
    
    public void handleEvent(SQLResultEvent event){
        
    	if (event.getIdentifier().startsWith("newplayer")) {
    		ResultSet rs = event.getResultSet();
    		String playerName = event.getIdentifier().substring(10);
    		try {
				if (rs.next()) {
					String name = rs.getString("fcName");
					int money = rs.getInt("fnMoney");
					players.put(name.toLowerCase(), new PubPlayer(m_botAction, name, money));
				} else {
					players.put(playerName.toLowerCase(), new PubPlayer(m_botAction, playerName));
				}
				rs.close();
			} catch (SQLException e) {
				Tools.printStackTrace(e);
			}
    	}
    }
    
    public void handleEvent(ArenaJoined event)
    {
    	Iterator<Player> it = m_botAction.getPlayerIterator();
    	while(it.hasNext()) {
    		Player p = it.next();
    		PubPlayer pubPlayer = addPlayerToSystem(p.getPlayerName());
    		if (pubPlayer != null)
    			pubPlayer.setIsOnline(true);
    	}
    }
    
    private PubPlayer addPlayerToSystem(final String playerName) {
    	
    	PubPlayer player = players.get(playerName.toLowerCase());
    	if (player != null) {
    		player.reloadPanel();
    		return player;
    	}
    	else if (databaseName != null) {
    		m_botAction.SQLBackgroundQuery(databaseName, "newplayer_"+playerName, "SELECT fcName, fnMoney FROM tblPlayerMoney WHERE fcName = '"+Tools.addSlashes(playerName)+"'");
    	}
    	else {
    		players.put(playerName.toLowerCase(), new PubPlayer(m_botAction, playerName));
    	}
    	
    	return players.get(playerName.toLowerCase());
    }
    
    public boolean isShipRestricted(int ship) {
    	return shipWeight.get(ship) == 0;
    }
    
    /**
     * Specs all ships in the arena that are over the weighted restriction limit.
     */
    private void specRestrictedShips()
    {
        Iterator<Player> iterator = m_botAction.getPlayingPlayerIterator();
        Player player;

        while(iterator.hasNext()) {
            player = (Player) iterator.next();
            checkPlayer(player.getPlayerID());
        }
    }
    
    /**
     * Sets a given ship to a particular restriction.
     */
    public void doSetCmd(String sender, String argString) {
        String[] args = argString.split(" ");
        if( args.length != 2 )
            throw new RuntimeException("Usage: !set <ship#> <weight#>");

        try {
            Integer ship = Integer.valueOf(args[0]);
            ship = ship.intValue();
            Integer weight = Integer.valueOf(args[1]);
            if( ship > 0 && ship < 9 ) {
                if( weight >= 0 ) {
                    shipWeight.set( ship.intValue(), weight );
                    if( weight == 0 )
                        m_botAction.sendSmartPrivateMessage(sender, Tools.shipName( ship ) + "s: disabled." );
                    if( weight == 1 )
                        m_botAction.sendSmartPrivateMessage(sender, Tools.shipName( ship ) + "s: unrestricted." );
                    if( weight > 1 )
                        m_botAction.sendSmartPrivateMessage(sender, Tools.shipName( ship ) + "s: limited to 1/" + weight + " of the size of a frequency (but 1 always allowed).");
                    specRestrictedShips();
                } else
                    throw new RuntimeException("Weight must be >= 0.");
            } else
                throw new RuntimeException("Invalid ship number.");
        } catch (Exception e) {
            throw new RuntimeException("Usage: !set <ship#> <weight#>");
        }
    }
    
    /**
     * Lists any ship restrictions in effect.
     */
    public void doRestrictionsCmd(String sender) {
        int weight;
        m_botAction.sendSmartPrivateMessage(sender, "Ship limitations/restrictions (if any)" );
        for( int i = 1; i < 9; i++ ) {
            weight = shipWeight.get( i ).intValue();
            if( weight == 0 )
                m_botAction.sendSmartPrivateMessage(sender, Tools.shipName( i ) + "s disabled." );
            else if( weight > 1 )
                m_botAction.sendSmartPrivateMessage(sender, Tools.shipName( i ) + "s limited to 1/" + weight + " of the size of a frequency (but 1 always allowed).");
        }
        m_botAction.sendSmartPrivateMessage(sender, "Private frequencies are " + (context.isPrivFreqEnabled() ? "enabled." : "disabled.") );
    }
    
    /**
     * This method checks to see if a player is in a restricted ship, or the
     * weight for the given ship has been reached.  If either is true, then the
     * player is specced.
     *
     * Weights can be thought of as a denominator (bottom number) of a fraction,
     * the fraction saying how much of the freq can be made up of ships of this
     * type.  If the weight is 0, no ships of a type are allowed.  Weight of 1
     * gives a fraction of 1/1, or a whole -- the entire freq can be made up of
     * this ship.  Following that, 2 is half, 3 is a third, 4 is a fourth, etc.
     * Play with what weight seems right to you.
     *
     * Note that even with a very small freq, if a weight is 1 or greater, 1 ship
     * of this type is ALWAYS allowed.
     *
     * Value for ship "weights":
     *
     * 0  - No ships of this type allowed
     * 1  - Unlimited number of ships of this type are allowed
     * #  - If the number of current ships of the type on this frequency is
     *      greater than the total number of people on the frequency divided
     *      by this number (ships of this type > total ships / weight), then the
     *      ship is not allowed.  Exception to this rule is if the player is the
     *      only one on the freq currently in the ship.
     *
     * @param playerName is the player to be checked.
     * @param specMessage enables the spec message.
     */
    public void checkPlayer(int playerID)
    {
        Player player = m_botAction.getPlayer(playerID);
        if( player == null )
            return;

        int weight = shipWeight.get(player.getShipType()).intValue();

        // If weight is 1, unlimited number of that shiptype is allowed.  (Spec is also set to 1.)
        if( weight == 1 )
            return;

        // If weight is 0, ship is completely restricted.
        if( weight == 0 ) {
            
            int randomShip = player.getShipType();
            
            while( randomShip == player.getShipType() 
                    && shipWeight.get(randomShip) == 0){
                randomShip = new Random().nextInt(8);
                if(randomShip == 0)
                    randomShip = player.getShipType();
            }
            m_botAction.setShip(playerID, randomShip);
       	    m_botAction.sendSmartPrivateMessage(m_botAction.getPlayerName(playerID), "That ship has been restricted in this arena.");  
       	    m_botAction.sendSmartPrivateMessage(m_botAction.getPlayerName(playerID), "Please choose another, or type ?arena to select another arena. You've been put randomly in ship "+randomShip);
       	    return;
        }

        // For all other weights, we must decide whether they can play based on the
        // number of people on freq who are also using the ship.
        Iterator<Player> i = m_botAction.getPlayingPlayerIterator();
        if( i == null)
            return;

        int freqTotal = 0;
        int numShipsOfType = 0;

        Player dummy;
        while( i.hasNext() ) {
            dummy = (Player)i.next();
            if( dummy != null) {
                if( dummy.getFrequency() == player.getFrequency() ) {
                    freqTotal++;
                    if( dummy.getShipType() == player.getShipType() )
                        numShipsOfType++;
                }
            }
        }

    	// Free pass if you're the only one on the freq, regardless of weight.
    	if( numShipsOfType <= 1 )
    	    return;

    	if( freqTotal == 0 ) {
            m_botAction.sendSmartPrivateMessage(m_botAction.getPlayerName(playerID), "Problem locating your freq!  Please contact a mod with ?help.");
            return;
    	}

        if( numShipsOfType > freqTotal / weight ) {
            // If unlimited spiders are allowed, set them to spider; else spec
            if( shipWeight.get(3).intValue() == 1 ) {
                m_botAction.setShip(playerID, 3);
                m_botAction.sendSmartPrivateMessage(m_botAction.getPlayerName(playerID), "There are too many ships of that kind (" + (numShipsOfType - 1) + "), or not enough people on the freq to allow you to play that ship.");
            } else {
                m_botAction.spec(playerID);
                m_botAction.spec(playerID);
                m_botAction.sendSmartPrivateMessage(m_botAction.getPlayerName(playerID), "There are too many ships of that kind (" + (numShipsOfType - 1) + "), or not enough people on the freq to allow you to play that ship.  Please choose another.");
            }
        }
    }
    
    /**
     * Sets a player to a freq and updates the freq lists.
     */
    private void addToLists(String playerName, int freq)
    {
        String lowerName = playerName.toLowerCase();
        if(freq == pubsystem.FREQ_0)
            freq0.add(lowerName);
        if(freq == pubsystem.FREQ_1)
            freq1.add(lowerName);
    }
    
    /**
     * Removes a playerName from the freq tracking lists.
     */
    private void removeFromLists(String playerName) {
        String lowerName = playerName.toLowerCase();
        freq0.remove(lowerName);
        freq1.remove(lowerName);
    }
    
    /**
     * Checks to see if a player is on a private freq.  If they are then
     * they are changed to the pub freq with the fewest number of players.
     *
     * @param Player player is the player to check.
     * @param changeMessage is true if a changeMessage will be displayed.
     */
    private void checkFreq(int playerID, int freq, boolean changeMessage)
    {
        Player player = m_botAction.getPlayer(playerID);
        String playerName = player.getPlayerName();
        if( player == null )
            return;

        int ship = player.getShipType();
        int newFreq = freq;

        if( playerName == null )
            return;

        removeFromLists(playerName);

        if(ship != pubsystem.SPEC)
        {
            if(player != null && freq != pubsystem.FREQ_0 && freq != pubsystem.FREQ_1)
            {
                if(freq0.size() <= freq1.size())
                    newFreq = pubsystem.FREQ_0;
                else
                    newFreq = pubsystem.FREQ_1;
                if(changeMessage)
                    m_botAction.sendSmartPrivateMessage(playerName, "Private Frequencies are currently disabled.  You have been placed on a public Frequency.");
                m_botAction.setFreq(playerName, newFreq);
            }
            addToLists(playerName, newFreq);
        }
    }


    
    /**
     * Checks for imbalance in frequencies, and requests the stacked freq to even it up
     * if there's a significant gap.
     */
    private void checkFreqSizes() {
        if( MSG_AT_FREQSIZE_DIFF == -1 || context.isPrivFreqEnabled() )
            return;
        int freq0 = m_botAction.getPlayingFrequencySize(0);
        int freq1 = m_botAction.getPlayingFrequencySize(1);
        int diff = java.lang.Math.abs( freq0 - freq1 );
        if( diff == freqSizeInfo[0] )
            return;
        freqSizeInfo[0] = diff;
        if( freqSizeInfo[0] >= MSG_AT_FREQSIZE_DIFF ) {
            if( freq0 > freq1 ) {
                m_botAction.sendOpposingTeamMessageByFrequency(0, "Teams unbalanced: " + freq0 + "v" + freq1 + ".  Volunteers requested; type =1 to switch to freq 1.  (Keep MVP status + earn " + NICEGUY_BOUNTY_AWARD + " bounty.)" );
                freqSizeInfo[1] = 1;
            } else {
                m_botAction.sendOpposingTeamMessageByFrequency(1, "Teams unbalanced: " + freq1 + "v" + freq0 + ".  Volunteers requested; type =0 to switch to freq 0.  (Keep MVP status + earn " + NICEGUY_BOUNTY_AWARD + " bounty.)" );
                freqSizeInfo[1] = 0;
            }
        }
        if( freqSizeInfo[0] >= KEEP_MVP_FREQSIZE_DIFF )
            teamsUneven = true;
        else
            teamsUneven = false;
    }



    
    /**
     * Fixes the freq of each player.
     */
    public void fixFreqs()
    {
        Iterator<Player> iterator = m_botAction.getPlayingPlayerIterator();
        Player player;

        fillFreqLists();
        while(iterator.hasNext())
        {
            player = (Player) iterator.next();
            checkFreq(player.getPlayerID(), player.getFrequency(), false);
        }
    }
	

    /**
     * Fills the freq lists for freqs 1 and 0.
     */
    private void fillFreqLists()
    {
        Iterator<Player> iterator = m_botAction.getPlayingPlayerIterator();
        Player player;
        String lowerName;

        freq0.clear();
        freq1.clear();
        while(iterator.hasNext())
        {
            player = (Player) iterator.next();
            lowerName = player.getPlayerName().toLowerCase();
            if(player.getFrequency() == pubsystem.FREQ_0)
            	freq0.add(lowerName);
            if(player.getFrequency() == pubsystem.FREQ_1)
            	freq1.add(lowerName);
        }
    }
    
    private class SavePlayersTask extends TimerTask {
    	
        public void run() {
        	
        	Collection<String> arenaPlayers = new ArrayList<String>();
            Iterator<Player> it = m_botAction.getPlayerIterator();
            while(it.hasNext()) {
            	Player p = it.next();
            	arenaPlayers.add(p.getPlayerName());
            }

            Iterator<PubPlayer> it2 = players.values().iterator();
            while(it2.hasNext()) {
            	
            	PubPlayer player = it2.next();

            	// No change since last save?
            	if (player.getLastMoneyUpdate() < player.getLastSavedState())
            		continue;

            	if (databaseName != null) {
                	// Update only if no save since 15 minutes
                	long diff = System.currentTimeMillis()-player.getLastSavedState();
                	if (diff > (SAVETASK_INTERVAL * Tools.TimeInMillis.MINUTE)) {
                		m_botAction.SQLBackgroundQuery(databaseName, "", "INSERT INTO tblPlayerMoney VALUES ('"+Tools.addSlashes(player.getPlayerName())+"',"+player.getMoney()+",NOW()) ON DUPLICATE KEY UPDATE fnMoney=" + player.getMoney());
                	}
            	}
    
            	// Not anymore on this arena? remove this player from the PubPlayerManager
            	if (!arenaPlayers.contains(player.getPlayerName())) {
            		it2.remove();
            	}
            }
        }
    }
}
