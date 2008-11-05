package twcore.bots.pubhub;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Map;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.helper.pubstats.PubStatsArena;
import twcore.core.helper.pubstats.PubStatsPlayer;
import twcore.core.helper.pubstats.PubStatsScore;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

public class pubhubstats extends PubBotModule {
	private String database = "pubstats";
	private String uniqueConnectionID = "pubstats";
	
	protected final String IPCCHANNEL = "pubstats";

	// PreparedStatements
	private PreparedStatement psUpdatePlayer, psReplaceScore, psUpdateScore, psUpdateAddScore, psScoreExists, psScoreReset, psGetBanner;
	private PreparedStatement psSRGetPeriodID, psSRClosePeriods, psSRNewPeriod, psSRPeriodResult, psSRPurgeScores;
	private PreparedStatement psSRGetResult_rating, psSRGetResult_wins, psSRGetResult_losses, psSRGetResult_average, psSRGetResult_flagPoints, psSRGetResult_killPoints, psSRGetResult_totalPoints;
	
	// SELECT fcName AS name, fnId AS id, SUBSTRING_INDEX( fcUsage, ':', 1 ) AS usageHours, SUBSTRING_INDEX( fcUsage, ':', -2 ) AS usageMinutes, fcUsage AS usageHoursMins FROM tblPlayer ORDER BY ABS( usageHours ) ASC, ABS( usageMinutes) ASC LIMIT 0,100

	// boolean to immediately stop execution
	private boolean stop = false;

	/**
	 * This method initializes the pubhubstats module.  It is called after
	 * m_botAction has been initialized.
	 */
	public void initializeModule() {
	    psUpdatePlayer = m_botAction.createPreparedStatement(database, uniqueConnectionID, "REPLACE INTO tblPlayer(fnId, fcName, fcSquad, fcBanner, fcIP, fnTimezone, fcUsage, fcResolution, fdCreated, fdLastSeen) VALUES (?,?,?,?,?,?,?,?,?,?)", true);
	    
	    psScoreExists = m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnPlayerId FROM tblScore WHERE fnPlayerId = ? AND fnShip = ?");
	    psReplaceScore = m_botAction.createPreparedStatement(database, uniqueConnectionID, "REPLACE INTO tblScore(fnPlayerId, fnShip, fnFlagPoints, fnKillPoints, fnWins, fnLosses, fnRate, fnAverage, ftLastUpdate) VALUES (?,?,?,?,?,?,?,?,?)");
	    psUpdateScore = m_botAction.createPreparedStatement(database, uniqueConnectionID, "UPDATE tblScore SET fnFlagPoints = ?, fnKillPoints = ?, fnWins = ?, fnLosses = ?, fnRate = ?, fnAverage = ?, ftLastUpdate = ? WHERE fnPlayerId = ? AND fnShip = ?"); 
	    psUpdateAddScore = m_botAction.createPreparedStatement(database, uniqueConnectionID, "UPDATE tblScore SET fnFlagPoints = fnFlagPoints + ?, fnKillPoints = fnKillPoints + ?, fnWins = fnWins + ?, fnLosses = fnLosses + ?, fnRate = ?, fnAverage = ?, ftLastUpdate = ? WHERE fnPlayerId = ? AND fnShip = ?");
	    
	    psScoreReset = m_botAction.createPreparedStatement(database, uniqueConnectionID, "DELETE FROM tblScore WHERE fnPlayerId = ?");
	    psGetBanner = m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fcBanner FROM tblPlayer WHERE fnId = ?");
	    
	    psSRGetPeriodID = m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fcPeriodID FROM tblPeriod WHERE fdEnd IS NULL");
	    psSRClosePeriods = m_botAction.createPreparedStatement(database, uniqueConnectionID, "UPDATE tblPeriod SET fdEnd = NOW() WHERE fdEnd IS NULL");
	    psSRNewPeriod = m_botAction.createPreparedStatement(database, uniqueConnectionID, "INSERT INTO tblPeriod(fcPeriodID, fdStart) VALUES(?,NOW())");
	    
	    psSRGetResult_rating = m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnPlayerId, fnRate FROM tblScore s WHERE s.fnShip = ? ORDER BY fnRate DESC LIMIT 0,1");
	    psSRGetResult_wins =   m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnPlayerId, fnWins FROM tblScore s WHERE s.fnShip = ? ORDER BY fnWins DESC LIMIT 0,1");
	    psSRGetResult_losses = m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnPlayerId, fnLosses FROM tblScore s WHERE s.fnShip = ? ORDER BY fnLosses DESC LIMIT 0,1");
	    psSRGetResult_average= m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnPlayerId, fnAverage FROM tblScore s WHERE s.fnShip = ? ORDER BY fnAverage DESC LIMIT 0,1");
	    psSRGetResult_flagPoints =  m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnPlayerId, fnFlagPoints FROM tblScore s WHERE s.fnShip = ? ORDER BY fnFlagPoints DESC LIMIT 0,1");
	    psSRGetResult_killPoints =  m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnPlayerId, fnKillPoints FROM tblScore s WHERE s.fnShip = ? ORDER BY fnKillPoints DESC LIMIT 0,1");
	    psSRGetResult_totalPoints = m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnPlayerId, (fnFlagPoints + fnKillPoints) AS fnTotalPoints FROM tblScore s WHERE s.fnShip = ? ORDER BY fnTotalPoints DESC LIMIT 0,1");
	    
	    psSRPeriodResult = m_botAction.createPreparedStatement(database, uniqueConnectionID, "INSERT INTO tblPeriodResults(fcPeriodID, fnShipID, fnPlayerID, fcStatistic, fnStatisticResult) VALUES(?,?,?,?,?)");
	    
	    psSRPurgeScores = m_botAction.createPreparedStatement(database, uniqueConnectionID, "TRUNCATE TABLE tblScore");
	    
        //psGetPlayerID =  m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnID FROM tblPlayer WHERE fcName = ? LIMIT 0,1");
	    //psGetScoreCalc = m_botAction.createPreparedStatement(database, uniqueConnectionID, "SELECT fnKillPoints, fnWins, fnLosses FROM tblScore WHERE fnPlayerId = ? AND fnShip = ?");
	    //psUpdateScoreCalc = m_botAction.createPreparedStatement(database, uniqueConnectionID, "UPDATE tblSCORE SET fnRate = ?, fnAverage = ? WHERE fnPlayerId = ? AND fnShip = ?");
	    
	    
	    if(     psUpdatePlayer == null || 
	            psReplaceScore == null || 
	            psUpdateScore == null || 
	            psUpdateAddScore == null || 
	            psScoreExists == null || 
	            psScoreReset == null || 
	            psGetBanner == null ||
	            psSRGetPeriodID == null ||
	            psSRClosePeriods == null ||
	            psSRNewPeriod == null ||
	            psSRGetResult_rating == null ||
	            psSRGetResult_wins == null ||
	            psSRGetResult_losses == null ||
	            psSRGetResult_average == null ||
	            psSRGetResult_flagPoints == null ||
	            psSRGetResult_killPoints == null ||
	            psSRGetResult_totalPoints == null ||
	            psSRPeriodResult == null ||
	            psSRPurgeScores == null) {
	        
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
	
	/* (non-Javadoc)
	 * @see twcore.bots.Module#handleEvent(twcore.core.events.Message)
	 */
	public void handleEvent(Message event) {
	    // NOTICE: Public scores reset
	    // Periodic scorereset triggered
	    if(event.getMessageType() == Message.ARENA_MESSAGE && "NOTICE: Public scores reset".equals(event.getMessage())) {
	        try {
	            stop = true;
	            
	            m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("globalScorereset"));
	            globalScorereset();
	            
	            stop = false;
	        } catch(SQLException sqle) {
	            Tools.printLog("SQLException occured while ending the score reset period: "+sqle.getMessage());
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
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psGetBanner);
	    
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psSRGetPeriodID);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psSRClosePeriods);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psSRNewPeriod);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psSRGetResult_rating);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psSRGetResult_wins);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psSRGetResult_losses);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psSRGetResult_average);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psSRGetResult_flagPoints);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psSRGetResult_killPoints);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psSRGetResult_totalPoints);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psSRPeriodResult);
	    m_botAction.closePreparedStatement(database, uniqueConnectionID, psSRPurgeScores);
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
	            // If the player's score got globally scoreresetted, don't save his scores anymore
	            // until he re-enters
	            if(player.isPeriodReset()) continue;
	            
	            if(!player.isBannerReceived()) {
                    player.setBanner(getBanner(player.getUserID()));
	                player.setBannerReceived(true);
	            }
	            
	            // Insert/Update Player information to the database
	            // fnId, fcName, fcSquad, fcBanner, fcIP, fnTimezone, fcUsage, fcResolution, fdCreated, fdLastSeen
	            // 1     2       3        4         5     6           7        8             9          10
	            psUpdatePlayer.clearParameters();
                psUpdatePlayer.setInt(1, player.getUserID());
                psUpdatePlayer.setString(2, player.getName());
                if(player.getSquad().trim().length() > 0)
                    psUpdatePlayer.setString(3, player.getSquad());
                else   
                    psUpdatePlayer.setNull(3, java.sql.Types.VARCHAR);
                if(player.getBanner() != null)
                    psUpdatePlayer.setString(4, player.getBanner());
                else
                    psUpdatePlayer.setNull(4, java.sql.Types.VARCHAR);
                psUpdatePlayer.setString(5, player.getIP());
                psUpdatePlayer.setInt(   6, player.getTimezone());
                psUpdatePlayer.setString(7, player.getUsage());
                psUpdatePlayer.setString(8, player.getResolution());
                psUpdatePlayer.setTimestamp(9, new Timestamp(player.getDateCreated().getTime()));
                psUpdatePlayer.setTimestamp(10, new Timestamp(player.getLastSeen()));
                psUpdatePlayer.execute();
                
                if(player.isScorereset()) {
                    psScoreReset.setInt(1, player.getUserID());
                    psScoreReset.execute();
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
                        // +fnFlagPoints, +fnKillPoints, +fnWins, +fnLosses, fnRate, fnAverage, ftLastUpdate, fnPlayerId, fnShip
                        
                        if(shipScore.getFlagPoints() < 0 || shipScore.getKillPoints() < 0 || shipScore.getWins() < 0 || shipScore.getLosses() < 0) {
                            Tools.printLog("Pubstats: Updating ship scores of player '"+player.getName()+"' on ship "+ship+": One of FlagPoints ("+shipScore.getFlagPoints()+") / KillPoints ("+shipScore.getKillPoints()+") / Wins ("+shipScore.getWins()+") / Losses ("+shipScore.getLosses()+") is negative!");
                        }
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
                        
                        if(shipScore.getFlagPoints() < 0 || shipScore.getKillPoints() < 0 || shipScore.getWins() < 0 || shipScore.getLosses() < 0) {
                            Tools.printLog("Pubstats: Replacing ship scores of player '"+player.getName()+"' on ship "+ship+": One of FlagPoints ("+shipScore.getFlagPoints()+") / KillPoints ("+shipScore.getKillPoints()+") / Wins ("+shipScore.getWins()+") / Losses ("+shipScore.getLosses()+") is negative!");
                        }
                        
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
	            
                if(player.isScorereset())
                    player.setScorereset(false);
                
	            player.setLastSave(System.currentTimeMillis());
	        }
	    }
	    
	    psReplaceScore.executeBatch();
	    psUpdateScore.executeBatch();
	    psUpdateAddScore.executeBatch();
	    
	    psReplaceScore.clearBatch();
	    psUpdateScore.clearBatch();
	    psUpdateAddScore.clearBatch();
	    
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
	
	private String getBanner(int playerID) {
	    String banner = null;
	    
	    try {
	    	    psGetBanner.setInt(1, playerID);
	    	    ResultSet rs = psGetBanner.executeQuery();
	    
	    	    if(rs != null && rs.next()) {
	    	        banner = rs.getString(1);
	    	    }
	    } catch(SQLException sqle) {
	        Tools.printLog("SQLException occured while getting the banner of playerID '"+playerID+"': "+sqle.getMessage());
            Tools.printStackTrace(sqle);
	    }
	    
	    return banner;
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
	
	private void globalScorereset() throws SQLException {
	    // 0. Get current fcPeriodID
	    // 1. Write results of this period to tblPeriodResults
	    // 2. Purge tblScore
	    // 3. Close current period         --> tblPeriod 
	    // 4. Start a new period           --> tblPeriod
	    
	    String thisPeriodID = "";
	            // yyyy-mm
	    String nextPeriodID = "";
	    
	    // 0. Get current fcPeriodID
	    // psSRGetPeriodID
	    ResultSet rs = psSRGetPeriodID.executeQuery();
	    if(rs != null && rs.next()) {
	        thisPeriodID = rs.getString(1);
	    } else {
	        // Fatal error: unable to retrieve the period id
	        String msg = "ERROR: Unable to retrieve the current periodID while ending the current scorereset period! Human intervention required!!";
	        Tools.printLog(msg);
	        m_botAction.sendChatMessage(msg);
	        return;
	    }
	    
	    // Create new period id
	    int year = new GregorianCalendar().get(Calendar.YEAR);
	    if(thisPeriodID.startsWith(String.valueOf(year))) {
	        // thisPeriodID starts with current year
	        String[] pieces = thisPeriodID.split("-");
	        int nextNumber = Integer.parseInt(pieces[1])+1;
	        
	        nextPeriodID = pieces[0] + "-" + (nextNumber < 10 ? "0" : "") + nextNumber;
	    } else {
	        nextPeriodID = String.valueOf(year) + "-01";
	    }
	    
	    // 1. Write results of this period to tblPeriodResults
	    // psSRGetResult_ -> 1-playerID, 2-amount
	    // psSRPeriodResult -> 1-fcPeriodID, 2-fnShipID, 3-fnPlayerID, 4-fcStatistic, 5-fnStatisticResult
	    for(int ship = 0 ; ship < 9 ; ship++) {
	        int player, amount;
	        String type;
	        
	        // Highest Rating
	        psSRGetResult_rating.setInt(1, ship);
	        ResultSet rsRating = psSRGetResult_rating.executeQuery();
	        if(rsRating != null && rsRating.next()) {
	            player = rsRating.getInt(1);
	            amount = rsRating.getInt(2);
	            type = "RATE";
	            
	            // Save results of this period to tblPeriodResults
                psSRPeriodResult.setString(1, thisPeriodID);
                psSRPeriodResult.setInt(2, ship);
                psSRPeriodResult.setInt(3, player);
                psSRPeriodResult.setString(4, type);
                psSRPeriodResult.setInt(5, amount);
                psSRPeriodResult.addBatch();
	        }
	        
	        // Most Wins
	        psSRGetResult_wins.setInt(1, ship);
	        ResultSet rsWins = psSRGetResult_wins.executeQuery();
	        if(rsWins != null && rsWins.next()) {
	            player = rsWins.getInt(1);
	            amount = rsWins.getInt(2);
	            type = "WINS";
	            
	            // Save results of this period to tblPeriodResults
                psSRPeriodResult.setString(1, thisPeriodID);
                psSRPeriodResult.setInt(2, ship);
                psSRPeriodResult.setInt(3, player);
                psSRPeriodResult.setString(4, type);
                psSRPeriodResult.setInt(5, amount);
                psSRPeriodResult.addBatch();
	        }
	        
	        // Most Losses
	        psSRGetResult_losses.setInt(1, ship);
	        ResultSet rsLosses = psSRGetResult_losses.executeQuery();
	        if(rsLosses != null && rsLosses.next()) {
	            player = rsLosses.getInt(1);
	            amount = rsLosses.getInt(2);
	            type = "LOSSES";
	            
	            // Save results of this period to tblPeriodResults
                psSRPeriodResult.setString(1, thisPeriodID);
                psSRPeriodResult.setInt(2, ship);
                psSRPeriodResult.setInt(3, player);
                psSRPeriodResult.setString(4, type);
                psSRPeriodResult.setInt(5, amount);
                psSRPeriodResult.addBatch();
	        }
	        
	        // Highest Average
	        psSRGetResult_average.setInt(1, ship);
	        ResultSet rsAverage = psSRGetResult_average.executeQuery();
	        if(rsAverage != null && rsAverage.next()) {
	            player = rsAverage.getInt(1);
	            amount = rsAverage.getInt(2);
	            type = "AVERAGE";
	            
	            // Save results of this period to tblPeriodResults
                psSRPeriodResult.setString(1, thisPeriodID);
                psSRPeriodResult.setInt(2, ship);
                psSRPeriodResult.setInt(3, player);
                psSRPeriodResult.setString(4, type);
                psSRPeriodResult.setInt(5, amount);
                psSRPeriodResult.addBatch();
	        }
	        
	        // Most FlagPoints
	        psSRGetResult_flagPoints.setInt(1, ship);
            ResultSet rsFlagPoints = psSRGetResult_flagPoints.executeQuery();
            if(rsFlagPoints != null && rsFlagPoints.next()) {
                player = rsFlagPoints.getInt(1);
                amount = rsFlagPoints.getInt(2);
                type = "FLAGPOINTS";
                
                // Save results of this period to tblPeriodResults
                psSRPeriodResult.setString(1, thisPeriodID);
                psSRPeriodResult.setInt(2, ship);
                psSRPeriodResult.setInt(3, player);
                psSRPeriodResult.setString(4, type);
                psSRPeriodResult.setInt(5, amount);
                psSRPeriodResult.addBatch();
            }
	        
	        // Most KillPoints
            psSRGetResult_killPoints.setInt(1, ship);
            ResultSet rsKillPoints = psSRGetResult_killPoints.executeQuery();
            if(rsKillPoints != null && rsKillPoints.next()) {
                player = rsKillPoints.getInt(1);
                amount = rsKillPoints.getInt(2);
                type = "KILLPOINTS";
                
                // Save results of this period to tblPeriodResults
                psSRPeriodResult.setString(1, thisPeriodID);
                psSRPeriodResult.setInt(2, ship);
                psSRPeriodResult.setInt(3, player);
                psSRPeriodResult.setString(4, type);
                psSRPeriodResult.setInt(5, amount);
                psSRPeriodResult.addBatch();
            }
	        
	        // Most TotalPoints
            psSRGetResult_totalPoints.setInt(1, ship);
            ResultSet rsTotalPoints = psSRGetResult_totalPoints.executeQuery();
            if(rsTotalPoints != null && rsTotalPoints.next()) {
                player = rsTotalPoints.getInt(1);
                amount = rsTotalPoints.getInt(2);
                type = "TOTALPOINTS";
                
                // Save results of this period to tblPeriodResults
                psSRPeriodResult.setString(1, thisPeriodID);
                psSRPeriodResult.setInt(2, ship);
                psSRPeriodResult.setInt(3, player);
                psSRPeriodResult.setString(4, type);
                psSRPeriodResult.setInt(5, amount);
                psSRPeriodResult.addBatch();
            }
            
	    }
	    
	    psSRPeriodResult.executeBatch();
	    psSRPeriodResult.clearBatch();
	    
        
	    // 2. Purge tblScore
	    psSRPurgeScores.execute();
	    
	    // 3. Close current period
	    psSRClosePeriods.execute();
	    
	    // 4. Start a new period
	    psSRNewPeriod.setString(1, nextPeriodID);
	    psSRNewPeriod.execute();
	}
}