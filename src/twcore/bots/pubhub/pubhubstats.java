package twcore.bots.pubhub;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import twcore.bots.PubBotModule;
import twcore.bots.PubStatsPlayer;
import twcore.bots.PubStatsScore;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.util.Tools;

public class pubhubstats extends PubBotModule {
	private String database = "pubstats";
	private String uniqueConnectionID = "pubstats";

	// PreparedStatements
	PreparedStatement psGetPlayerID, psUpdatePlayer, psReplaceScore, psUpdateScore, psGetScoreCalc, psUpdateScoreCalc, psScoreExists;

	// boolean to immediately stop execution
	private boolean stop = false;

	/**
	 * This method initializes the pubhubstats module.  It is called after
	 * m_botAction has been initialized.
	 */
	public void initializeModule() {
	    psGetPlayerID =  m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnID FROM pubstats.tblPlayer WHERE fcName = ? LIMIT 0,1");
	    psUpdatePlayer = m_botAction.createPreparedStatement(database, uniqueConnectionID, "REPLACE INTO pubstats.tblPlayer(fnId, fcName, fcSquad, fcIP, fnTimezone, fcUsage, fdLastSeen) VALUES (?,?,?,?,?,?,?)", true);
	    psReplaceScore = m_botAction.createPreparedStatement(database, uniqueConnectionID, "REPLACE INTO pubstats.tblScore(fnPlayerId, fnShip, fnFlagPoints, fnKillPoints, fnWins, fnLosses, fnRate, fnAverage, ftLastUpdate) VALUES (?,?,?,?,?,?,?,?,?)");
	    psUpdateScore = m_botAction.createPreparedStatement(database, uniqueConnectionID, "UPDATE pubstats.tblScore SET fnFlagPoints = fnFlagPoints + ?, fnKillPoints = fnKillPoints + ?, fnWins = fnWins + ?, fnLosses = fnLosses + ?, fnRate = 0, fnAverage = 0 WHERE fnPlayerId = ? AND fnShip = ?");
	    psGetScoreCalc = m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnKillPoints, fnWins, fnLosses FROM pubstats.tblScore WHERE fnPlayerId = ? AND fnShip = ?");
	    psUpdateScoreCalc = m_botAction.createPreparedStatement(database, uniqueConnectionID, "UPDATE pubstats.tblSCORE SET fnRate = ?, fnAverage = ? WHERE fnPlayerId = ? AND fnShip = ?");
	    psScoreExists = m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnShip FROM pubstats.tblScore WHERE fnPlayerId = ? AND fnShip = ?");
	    
	    if(psGetPlayerID == null || psUpdatePlayer == null || psReplaceScore == null || psUpdateScore == null || psGetScoreCalc == null || psUpdateScoreCalc == null || psScoreExists == null) {
	        Tools.printLog("pubhubstats: One or more PreparedStatements are null! Module pubhubstats disabled.");
	        m_botAction.sendChatMessage(2, "pubhubstats: One or more PreparedStatements are null! Module pubhubstats disabled.");
	        this.cancel();
	    }
	    
	}

	/**
	 * Unused method but needs to be overridden
	 * @see Module.requestEvents()
	 */
	public void requestEvents(EventRequester eventRequester) {}

	/**
	 * This method handles the incoming InterProcessEvents,
	 * checks if the IPC is meant for this module and then extracts the containing
	 * object to store it to database.
	 *
	 * @param event is the InterProcessEvent to handle.
	 */
	@SuppressWarnings("unchecked")
	public void handleEvent(InterProcessEvent event)
	{

		// If the event.getObject() is anything else then the HashMap then return
		if(event.getObject() instanceof Map == false) {
			return;
		}
		if(event.getChannel().equals(pubhub.IPCPUBSTATS) == false) {
			return;
		}
		
		synchronized(event.getObject()) {
    		Map<String, PubStatsScore> stats = (Map<String, PubStatsScore>)event.getObject();
    		try {
    		    if(stats != null && stats.size() > 0) {
    		        updateDatabase(stats.values());
    		        stats.clear();
    		    }
    		} catch(SQLException sqle) {
    		    m_botAction.sendChatMessage(2, "SQL Exception encountered while saving "+stats.size()+" stats to database: "+sqle.getMessage());
    		    Tools.printLog("SQL Exception encountered while saving "+stats.size()+" stats to database: "+sqle.getMessage());
    		    Tools.printStackTrace(sqle);
    		}
		}
  }

	/**
	 * cancel() is called when this module is unloaded
	 */
	public void cancel() {
	    stop = true;
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psGetPlayerID);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psUpdatePlayer);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psReplaceScore);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psUpdateScore);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psUpdateScoreCalc);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psScoreExists);
	}

	/**
	 * Stores all the data from the PubStats objects into the tblScore table in the database
	 *
	 * @param stats the Collection containing the PubStats objects
	 */
	private synchronized void updateDatabase(Collection<PubStatsScore> stats) throws SQLException {
	    HashMap<String,Integer> playerIDs = new HashMap<String, Integer>();
	    // Map for playername to tblPlayer.fnId
	    
	    // Remove all stat objects from the stats collection if they haven't been updated since last save
	    Iterator<PubStatsScore> i = stats.iterator();
	    while(i.hasNext()) {
	        PubStatsScore stat = i.next();
	        if(stat.getLastSave() != null && !stat.getLastUpdate().after(stat.getLastSave())) {
	            i.remove();
	        }
	    }
	    
	    
		// Loop over all the PubStats objects and replace each in the stats table
		for(PubStatsScore score:stats) {
			PubStatsPlayer player = score.getPlayer();
		    if(stop) break;
		    
		    
		    // Update player information
		    if(playerIDs.containsKey(player.getName())== false) {
    		    // Get the player ID from the database
                psGetPlayerID.setString(1, player.getName());
                ResultSet rsPlayerID = psGetPlayerID.executeQuery();
                
                // Retrieve player ID
                int playerID=0;
                if(rsPlayerID.next()) {
                    playerID = rsPlayerID.getInt(1);
                    playerIDs.put(player.getName(), playerID);
                }
                
                // Insert/Update Player information to the database
                // fnId, fcName, fcSquad, fcIP, fnTimezone, fcUsage, fdLastSeen
                if(playerID==0)
                    psUpdatePlayer.setNull(1, Types.INTEGER);
                else
                    psUpdatePlayer.setInt(1, playerID);
                psUpdatePlayer.setString(2, player.getName());
                psUpdatePlayer.setString(3, player.getSquad());
                psUpdatePlayer.setString(4, player.getIP());
                psUpdatePlayer.setInt(   5, player.getTimezone());
                psUpdatePlayer.setString(6, player.getUsage());
                psUpdatePlayer.setTimestamp(7, new Timestamp(player.getDate().getTime()));
                psUpdatePlayer.executeUpdate();
                
                // If previous update inserted a new row, retrieve the auto-generated player id
                if(playerID==0) {
                    ResultSet rsGeneratedPlayerID = psUpdatePlayer.getGeneratedKeys();
                    if(rsGeneratedPlayerID.next()) {
                        playerIDs.put(player.getName(), rsGeneratedPlayerID.getInt(1));
                    }
                }
		    }
			
			
			if(score.getShip() == 0) {
			    // Total statistics
			    // fnPlayerID, fnShip, fnFlagPoints, fnKillPoints, fnWins, fnLosses, fnRate, fnAverage, ftLastUpdate
			    psReplaceScore.setInt(1, playerIDs.get(player.getName()));
			    psReplaceScore.setInt(2, score.getShip());    // always 0
			    psReplaceScore.setInt(3, score.getFlagPoints());
			    psReplaceScore.setInt(4, score.getKillPoints());
			    psReplaceScore.setInt(5, score.getWins());
			    psReplaceScore.setInt(6, score.getLosses());
			    psReplaceScore.setInt(7, score.getRate());
			    psReplaceScore.setFloat(8, score.getAverage());
			    psReplaceScore.setTimestamp(9, new Timestamp(score.getLastUpdate().getTime()));
			    psReplaceScore.executeUpdate();
			} else {
			    // Ship statistics
			    psScoreExists.setInt(1, playerIDs.get(player.getName()));
			    psScoreExists.setInt(2, score.getShip());
			    if(psScoreExists.execute()) {
			        // Score row already exists for this player+ship 
			        // +fnFlagPoints, +fnKillPoints, +fnWins, +fnLosses, fnPlayerId, fnShip
			        psUpdateScore.setInt(1, score.getFlagPoints());
			        psUpdateScore.setInt(2, score.getKillPoints());
			        psUpdateScore.setInt(3, score.getWins());
			        psUpdateScore.setInt(4, score.getLosses());
			        psUpdateScore.setInt(5, playerIDs.get(player.getName()));
			        psUpdateScore.setInt(6, score.getShip());
			        psUpdateScore.executeUpdate();
			        
			        // Get fnKillPoints, fnWins and fnLosses -- do calculations
			        psGetScoreCalc.setInt(1, playerIDs.get(player.getName()));
			        psGetScoreCalc.setInt(2, score.getShip());
			        ResultSet rsScore = psGetScoreCalc.executeQuery();
			        if(rsScore.next()) {
			            int killPoints = rsScore.getInt("fnKillPoints");
			            int wins = rsScore.getInt("fnWins");
			            int losses = rsScore.getInt("fnLosses");
			            // fnRate, fnAverage, fnPlayerId, fnShip
			            psUpdateScoreCalc.setInt(1, this.calculateRating(killPoints, wins, losses));
			            psUpdateScoreCalc.setFloat(2, this.calculateAverage(killPoints, wins));
			            psUpdateScoreCalc.setInt(3, playerIDs.get(player.getName()));
			            psUpdateScoreCalc.setInt(4, score.getShip());
			            psUpdateScoreCalc.executeUpdate();
			        }
			    } else {
			        // fnPlayerID, fnShip, fnFlagPoints, fnKillPoints, fnWins, fnLosses, fnRate, fnAverage, ftLastUpdate
	                psReplaceScore.setInt(1, playerIDs.get(player.getName()));
	                psReplaceScore.setInt(2, score.getShip());
	                psReplaceScore.setInt(3, score.getFlagPoints());
	                psReplaceScore.setInt(4, score.getKillPoints());
	                psReplaceScore.setInt(5, score.getWins());
	                psReplaceScore.setInt(6, score.getLosses());
	                psReplaceScore.setInt(7, score.getRate());
	                psReplaceScore.setFloat(8, score.getAverage());
	                psReplaceScore.setTimestamp(9, new Timestamp(score.getLastUpdate().getTime()));
	                psReplaceScore.executeUpdate();
			    }
			}
			score.setLastSave(new Date());
		}
	}
	
	
	private int calculateRating(int killPoints, int wins, int losses) {
	    return (killPoints*10 + (wins-losses)*100) / (wins +100);
	}
  
	private float calculateAverage(int killPoints, int wins) {
	    if(wins > 0)
	        return killPoints / wins;
	    else 
	        return 0;
	}
}