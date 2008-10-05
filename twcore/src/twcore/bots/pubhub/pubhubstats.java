package twcore.bots.pubhub;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.helper.pubstats.PubStatsArena;
import twcore.core.helper.pubstats.PubStatsPlayer;
import twcore.core.helper.pubstats.PubStatsScore;
import twcore.core.util.Tools;

public class pubhubstats extends PubBotModule {
	private String database = "pubstats";
	private String uniqueConnectionID = "pubstats";

	// PreparedStatements
	private PreparedStatement psUpdatePlayer, psReplaceScore, psUpdateScore, psUpdateAddScore, psScoreExists, psScoreReset;

	// boolean to immediately stop execution
	private boolean stop = false;

	/**
	 * This method initializes the pubhubstats module.  It is called after
	 * m_botAction has been initialized.
	 */
	public void initializeModule() {
	    psUpdatePlayer = m_botAction.createPreparedStatement(database, uniqueConnectionID, "REPLACE INTO tblPlayer(fnId, fcName, fcSquad, fcIP, fnTimezone, fcUsage, fdLastSeen) VALUES (?,?,?,?,?,?,?)", true);
	    
	    psScoreExists = m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnPlayerId FROM tblScore WHERE fnPlayerId = ? AND fnShip = ?");
	    psReplaceScore = m_botAction.createPreparedStatement(database, uniqueConnectionID, "REPLACE INTO tblScore(fnPlayerId, fnShip, fnFlagPoints, fnKillPoints, fnWins, fnLosses, fnRate, fnAverage, ftLastUpdate) VALUES (?,?,?,?,?,?,?,?,?)");
	    psUpdateScore = m_botAction.createPreparedStatement(database, uniqueConnectionID, "UPDATE tblScore SET fnFlagPoints = ?, fnKillPoints = ?, fnWins = ?, fnLosses = ?, fnRate = ?, fnAverage = ?, ftLastUpdate = ? WHERE fnPlayerId = ? AND fnShip = ?"); 
	    psUpdateAddScore = m_botAction.createPreparedStatement(database, uniqueConnectionID, "UPDATE tblScore SET fnFlagPoints = fnFlagPoints + ?, fnKillPoints = fnKillPoints + ?, fnWins = fnWins + ?, fnLosses = fnLosses + ?, fnRate = ?, fnAverage = ?, ftLastUpdate = ? WHERE fnPlayerId = ? AND fnShip = ?");
	    
	    psScoreReset = m_botAction.createPreparedStatement(database, uniqueConnectionID, "DELETE FROM tblScore WHERE fnPlayerId = ?");
	    
        //psGetPlayerID =  m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnID FROM tblPlayer WHERE fcName = ? LIMIT 0,1");
	    //psGetScoreCalc = m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnKillPoints, fnWins, fnLosses FROM tblScore WHERE fnPlayerId = ? AND fnShip = ?");
	    //psUpdateScoreCalc = m_botAction.createPreparedStatement(database, uniqueConnectionID, "UPDATE tblSCORE SET fnRate = ?, fnAverage = ? WHERE fnPlayerId = ? AND fnShip = ?");
	    
	    
	    if(psUpdatePlayer == null || psReplaceScore == null || psUpdateScore == null || psUpdateAddScore == null || psScoreExists == null || psScoreReset == null) {
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
	public void handleEvent(InterProcessEvent event)
	{

		// If the event.getObject() is anything else then the HashMap then return
		if(event.getObject() instanceof PubStatsArena == false) {
			return;
		}
		if(event.getChannel().equals(pubhub.IPCPUBSTATS) == false) {
			return;
		}
		
		synchronized(event.getObject()) {
		    PubStatsArena stats = (PubStatsArena)event.getObject();
    		try {
    		    if(stats != null && stats.size() > 0) {
    		        stats.cleanOldPlayers();
    		        updateDatabase(stats);
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
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psUpdatePlayer);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psReplaceScore);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psUpdateAddScore);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psUpdateScore);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psScoreExists);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psScoreReset);
	}

	/**
	 * Stores all the data from the PubStats objects into the tblScore table in the database
	 *
	 * @param stats the Collection containing the PubStats objects
	 */
	private void updateDatabase(PubStatsArena stats) throws SQLException {
	    
	    // Loop over all players in PubStatsArena
	    // update tblPlayer
	    // get the playerid
	    // save overall scores (batch)
	    // loop over available shipscores and save each one to database (batch)
	    // execute batches
	    
	    Map<String, PubStatsPlayer>  playerMap = stats.getPlayers();
	    
	    Collection<PubStatsPlayer> players = playerMap.values();
	    
	    synchronized(playerMap) {
	        Iterator<PubStatsPlayer> playerIterator = players.iterator();
	        while (playerIterator.hasNext()) {
	            PubStatsPlayer player = playerIterator.next();
	            if(stop) break;
	            
	            // If the player hasn't got the required fields filled, skip the player
	            // Note: the player might have gone offline before the required fields could be filled, thus the player will never be saved
	            if(!player.isExtraInfoFilled()) continue;
	            
	            // Insert/Update Player information to the database
                // fnId, fcName, fcSquad, fcIP, fnTimezone, fcUsage, fdLastSeen
	            psUpdatePlayer.clearParameters();
                psUpdatePlayer.setInt(1, player.getUserID());
                psUpdatePlayer.setString(2, player.getName());
                psUpdatePlayer.setString(3, player.getSquad());
                psUpdatePlayer.setString(4, player.getIP());
                psUpdatePlayer.setInt(   5, player.getTimezone());
                psUpdatePlayer.setString(6, player.getUsage());
                psUpdatePlayer.setTimestamp(7, new Timestamp(player.getLastSeen()));
                psUpdatePlayer.execute();
                
                if(player.isScorereset()) {
                    psScoreReset.setInt(1, player.getUserID());
                    psScoreReset.execute();
                    player.setScorereset(false);
                }
                
                // Update scores
                // overall scores
                if(scoreExists(player.getUserID(), (short)0)) {
                    // +fnFlagPoints, +fnKillPoints, +fnWins, +fnLosses, fnRate, fnAverage, fnPlayerId, fnShip
                    psUpdateScore.setInt(1, player.getFlagPoints());
                    psUpdateScore.setInt(2, player.getKillPoints());
                    psUpdateScore.setInt(3, player.getWins());
                    psUpdateScore.setInt(4, player.getLosses());
                    psUpdateScore.setInt(5, this.calculateRating(player.getKillPoints(), player.getWins(), player.getLosses()));
                    psUpdateScore.setFloat(6, this.calculateAverage(player.getKillPoints(), player.getWins()));
                    psUpdateScore.setTimestamp(7, new Timestamp(player.getLastUpdate()));
                    psUpdateScore.setInt(8, player.getUserID());
                    psUpdateScore.setInt(9, 0);
                    psUpdateScore.addBatch();
                } else {
                    // fnPlayerID, fnShip, fnFlagPoints, fnKillPoints, fnWins, fnLosses, fnRate, fnAverage, ftLastUpdate
                    psReplaceScore.setInt(1, player.getUserID());
                    psReplaceScore.setInt(2, 0);
                    psReplaceScore.setInt(3, player.getFlagPoints());
                    psReplaceScore.setInt(4, player.getKillPoints());
                    psReplaceScore.setInt(5, player.getWins());
                    psReplaceScore.setInt(6, player.getLosses());
                    psReplaceScore.setInt(7, this.calculateRating(player.getKillPoints(), player.getWins(), player.getLosses()));
                    psReplaceScore.setFloat(8, this.calculateAverage(player.getKillPoints(), player.getWins()));
                    psReplaceScore.setTimestamp(9, new Timestamp(player.getLastUpdate()));
                    psReplaceScore.addBatch();
                }
                
                // ship scores
	            // loop over all ships
                PubStatsScore shipScore;
                
                for(short ship = 1 ; ship <= 8 ; ship++) {
                    shipScore = player.getShipScore(ship);
                    
                    if(shipScore == null) 
                        continue;
                    
                    if(scoreExists(player.getUserID(), ship) && !player.isScorereset()) {
                        // +fnFlagPoints, +fnKillPoints, +fnWins, +fnLosses, fnRate, fnAverage, fnPlayerId, fnShip
                        psUpdateAddScore.setInt(1, shipScore.getFlagPoints());
                        psUpdateAddScore.setInt(2, shipScore.getKillPoints());
                        psUpdateAddScore.setInt(3, shipScore.getWins());
                        psUpdateAddScore.setInt(4, shipScore.getLosses());
                        psUpdateAddScore.setInt(5, this.calculateRating(shipScore.getKillPoints(), shipScore.getWins(), shipScore.getLosses()));
                        psUpdateAddScore.setFloat(6, this.calculateAverage(shipScore.getKillPoints(), shipScore.getWins()));
                        psUpdateAddScore.setTimestamp(7, new Timestamp(player.getLastUpdate()));
                        psUpdateAddScore.setInt(8, player.getUserID());
                        psUpdateAddScore.setInt(9, ship);
                        psUpdateAddScore.addBatch();
                    } else {
                        // fnPlayerID, fnShip, fnFlagPoints, fnKillPoints, fnWins, fnLosses, fnRate, fnAverage, ftLastUpdate
                        psReplaceScore.setInt(1, player.getUserID());
                        psReplaceScore.setInt(2, ship);
                        psReplaceScore.setInt(3, shipScore.getFlagPoints());
                        psReplaceScore.setInt(4, shipScore.getKillPoints());
                        psReplaceScore.setInt(5, shipScore.getWins());
                        psReplaceScore.setInt(6, shipScore.getLosses());
                        psReplaceScore.setInt(7, this.calculateRating(shipScore.getKillPoints(), shipScore.getWins(), shipScore.getLosses()));
                        psReplaceScore.setFloat(8, this.calculateAverage(shipScore.getKillPoints(), shipScore.getWins()));
                        psReplaceScore.setTimestamp(9, new Timestamp(player.getLastUpdate()));
                        psReplaceScore.addBatch();
                    }
                    
                    player.removeShipScore(ship);   // remove score statistics for this ship as it's already saved
                    
                }
	            
	            
	            player.setLastSave(System.currentTimeMillis());
	        }
	    }
	    
	    psReplaceScore.executeBatch();
	    psUpdateScore.executeBatch();
	    psUpdateAddScore.executeBatch();
	    
	}
	
	private boolean scoreExists(int playerID, short ship) {
	    try {
    	    psScoreExists.setInt(1, playerID);
            psScoreExists.setInt(2, ship);
            ResultSet rs = psScoreExists.executeQuery();
            
            if(rs != null && rs.next()) {
                return true;
            }
	    } catch(SQLException sqle) {
	        Tools.printLog("SQLException occured while checking if score exists in tblScore for playerid '"+playerID+"' and ship '"+ship+"': "+sqle.getMessage());
            Tools.printStackTrace(sqle);
	    }
	    return false;
	}
	
	
	private int calculateRating(int killPoints, int wins, int losses) {
	    return ((killPoints*10) + (wins-losses)*100) / (wins +100);
	}
  
	private float calculateAverage(int killPoints, int wins) {
	    if(wins > 0)
	        return killPoints / wins;
	    else 
	        return 0;
	}
}