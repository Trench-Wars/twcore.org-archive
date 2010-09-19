package twcore.bots.pubsystem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.SQLResultEvent;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubPlayerManager {

	private BotAction m_botAction;
    private HashMap<String, PubPlayer> players; // Always lowercase!
	
	private String databaseName;
	
	private SavePlayersTask saveTask = new SavePlayersTask();
	private int SAVETASK_INTERVAL = 1; // minutes
	
	public PubPlayerManager(BotAction m_botAction) {
		this(m_botAction, null);
	}
	
	public PubPlayerManager(BotAction m_botAction, String database) {
		this.m_botAction = m_botAction;
		this.databaseName = database;
		this.players = new HashMap<String, PubPlayer>();
		m_botAction.scheduleTaskAtFixedRate(saveTask, SAVETASK_INTERVAL * Tools.TimeInMillis.MINUTE, SAVETASK_INTERVAL * Tools.TimeInMillis.MINUTE);
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
						players.put(name, new PubPlayer(m_botAction, name, money));
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
	
	public void handleEvent(PlayerEntered event) {

    	Player p = m_botAction.getPlayer(event.getPlayerID());
    	String playerName = p.getPlayerName();
    	PubPlayer pubPlayer = addPlayerToSystem(playerName);
    	if (pubPlayer != null)
    		pubPlayer.setIsOnline(true);
	}
	
	public void handleEvent(PlayerLeft event) {
		
    	Player p = m_botAction.getPlayer(event.getPlayerID());
    	String playerName = p.getPlayerName();
    	PubPlayer pubPlayer = players.get(playerName);
    	if (pubPlayer != null)
    		pubPlayer.setIsOnline(false);		
	}
	
    public void handleEvent(FrequencyShipChange event) {

        int playerID = event.getPlayerID();
        Player p = m_botAction.getPlayer(playerID);
    	
		PubPlayer pubPlayer = players.get(p.getPlayerName().toLowerCase());
		if (pubPlayer!=null) {
			pubPlayer.handleShipChange(event);
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
