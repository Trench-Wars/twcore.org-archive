package twcore.bots.pubbot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.PubBotModule;
import twcore.bots.PubStatsPlayer;
import twcore.bots.PubStatsScore;
import twcore.core.EventRequester;
import twcore.core.events.ArenaJoined;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.ScoreReset;
import twcore.core.events.ScoreUpdate;
import twcore.core.game.Player;

public class pubbotstats extends PubBotModule {
  
  protected Map<String, PubStatsScore> stats = Collections.synchronizedMap(new HashMap<String, PubStatsScore>());
  // 			   <Ship#playerid, PubStats>
  // F.ex:		   3:1234, PubStats <-- spider
  // F.ex:         :1234, PubStats <-- total stats
  
  private Vector<String> infoBuffer = new Vector<String>(20);
  // *info buffer, stores all arena messages that come from *info
  private Map<String,Boolean> infoRequested = Collections.synchronizedMap(new HashMap<String, Boolean>());
  // <Playername, PlayerID>
  
  protected final String IPCCHANNEL = "pubstats";
  private final int SEND_STATS_TIME = 1000*60*15; // 15 minutes
  
  private boolean debug = false;

  public void initializeModule() {
      SendStatsTask sendstats = new SendStatsTask();
      m_botAction.scheduleTaskAtFixedRate(sendstats, SEND_STATS_TIME, SEND_STATS_TIME);
      RequestInfo requestInfo = new RequestInfo(this);
      m_botAction.scheduleTaskAtFixedRate(requestInfo , 1000, 1000);
  }

  public void requestEvents(EventRequester eventRequester) {
	  eventRequester.request(EventRequester.PLAYER_ENTERED);
	  eventRequester.request(EventRequester.PLAYER_LEFT);
	  eventRequester.request(EventRequester.PLAYER_DEATH);
	  eventRequester.request(EventRequester.SCORE_UPDATE);
	  eventRequester.request(EventRequester.SCORE_RESET);
	  eventRequester.request(EventRequester.ARENA_JOINED);
	  eventRequester.request(EventRequester.MESSAGE);
  }
  
  public void handleEvent( ArenaJoined event ) {
	  m_botAction.receiveAllPlayerDeaths();
  }
  
  public void handleEvent( ScoreUpdate event ) {
	  Player player = m_botAction.getPlayer(event.getPlayerID());
	  if(player != null) {
		  updateStats(player, "scoreupdate");
	  }
  }
  
  public void handleEvent( ScoreReset event ) {
	  Player player = m_botAction.getPlayer(event.getPlayerID());
	  if(player != null) {
		  updateStats(player, "scorereset");
	  }
  }
  
  public void handleEvent( PlayerEntered event ) {
	  Player player = m_botAction.getPlayer(event.getPlayerID());
	  if(player != null) {
		  updateStats(player, "entered");
	  }
  }
  
  public void handleEvent( PlayerLeft event ) {
	  /*Player player = m_botAction.getPlayer(event.getPlayerID());
	  if(player != null) {
		  updateStats(player, "left");
	  }*/
  }
  
  public void handleEvent( PlayerDeath event ) {
      Player killee = m_botAction.getPlayer(event.getKilleeID());
      if(killee != null) {
          updateStats(killee, "killee");
      }
      Player killer = m_botAction.getPlayer(event.getKillerID());
      if(killer != null) {
          updateStats(killer, "killer");
      }
      
  }
  
  public void handleEvent( Message event ) {
	  String message = event.getMessage();
	  
	  if(message != null && event.getMessageType() == Message.ARENA_MESSAGE && message.startsWith("IP:")) {
	      infoBuffer.add(0, message);
	      infoBuffer.setSize(20);
		  
	      String playerName = getInfo(message, "TypedName:");
		  //String playerIP = getInfo(message, "IP:");
		  //String timezone = getInfo(message, "TimeZoneBias:");
	      
	      if(infoRequested.containsKey(playerName)) {
	          fillBlankPlayerInfo();
	      } else {
	          // fuzzy name player check
	          for(String name:infoRequested.keySet()) {
	              if(playerName.startsWith(name)) {
	                  fillBlankPlayerInfo();
	              }
	          }
	      }
	      
	      
	  }
	  if(message != null && event.getMessageType() == Message.ARENA_MESSAGE && message.startsWith("TIME: Session:")) {
	      try {
	          String latestInfo = infoBuffer.get(0);
	          if(latestInfo != null && latestInfo.length()>0) {
	              // Make sure this latestInfo doesn't already has this info
	              if(latestInfo.indexOf("TIME: Session:")==-1) {
	                  latestInfo = latestInfo+" "+message;
	                  infoBuffer.set(0, latestInfo);
	              }
	          }
	          //String usage = getInfo(message, "Total: ");
	          
	          String playerName = getInfo(latestInfo, "TypedName:");
	          
	          if(infoRequested.containsKey(playerName.trim())) {
	              infoRequested.remove(playerName);
	              fillBlankPlayerInfo();
	          } else {
	              // fuzzy name player check
	              for(String name:infoRequested.keySet()) {
	                  if(playerName.startsWith(name)) {
	                      infoRequested.remove(name);
	                      fillBlankPlayerInfo();
	                  }
	              }
	          }
	          
	      } catch(IndexOutOfBoundsException ioobe) {
	          // Probably received a wrong arena message
	      }
	  }
	  if(message != null && event.getMessageType() == Message.PRIVATE_MESSAGE) {
	      // commands
	      if(message.startsWith("!forcesave")) {
	          m_botAction.sendPrivateMessage(event.getPlayerID(), "done");
	          m_botAction.ipcTransmit(IPCCHANNEL, stats);   
	      }
	      if(message.startsWith("!check")) {
	          synchronized(stats) {
	              m_botAction.sendTeamMessage("stats size: "+stats.size());
	              for(PubStatsScore score:stats.values()) {
	                  PubStatsPlayer player = score.getPlayer();
	                  if(player.getIP() == null)
	                      m_botAction.sendTeamMessage(player.getName() + " null IP");
	                  if(player.getName() == null)
	                      m_botAction.sendTeamMessage(player.getName() + " null name");
	                  if(player.getTimezone() == 0)
	                      m_botAction.sendTeamMessage(player.getName() + " 0 timezone");
	                  if(player.getUsage() == null)
	                      m_botAction.sendTeamMessage(player.getName() + " null usage");
	              }
	          }
	      }
	      if(message.startsWith("!request")) {
	          synchronized(infoRequested) {
	              m_botAction.sendTeamMessage("size: "+infoRequested.size());
	              
	              for(String name:infoRequested.keySet()) {
	                  m_botAction.sendTeamMessage(name + ": " + infoRequested.get(name));
	              }
	          }
	      }
	  }
  }

  public void cancel() {
	  //m_botAction.ipcTransmit(IPCCHANNEL, stats);
	  m_botAction.cancelTasks();
  }
  
  /**** Private Helper methods ****/
  
  private synchronized void updateStats(Player player, String action) {
	  PubStatsScore pubStatsScore;
	  PubStatsPlayer pubStatsPlayer;
	  
	  boolean previousScoreAvailable = false;
	  
	  // Get total statistics
	  if(stats.containsKey(":"+player.getPlayerID())) {
		  pubStatsScore = stats.get(":" + player.getPlayerID());
		  pubStatsPlayer = pubStatsScore.getPlayer();
		  previousScoreAvailable = true;
	  } else {
		  // Create objects
		  pubStatsPlayer = new PubStatsPlayer(	player.getPlayerName(),        // Save it with playername from %tickname first
				  								player.getSquadName(),
				  								getInfoFromBuffer(player.getPlayerName(), "IP"),		// IP is unknown
				  								getInfoFromBuffer(player.getPlayerName(), "Timezone"),	// timezone is unknown
				  								getInfoFromBuffer(player.getPlayerName(), "Usage")		// usage is unknown
		                                      );
		  
		  if(pubStatsPlayer.getIP() == null) {
		      // the IP wasn't found in the infoBuffer yet, request it ourselves then
		      infoRequested.put(player.getPlayerName(),false);
		      debug("Requesting *info of "+player.getPlayerName()+" myself");
		  }
		  
		  pubStatsScore = new PubStatsScore(	pubStatsPlayer, 
				  								0,    // Total statistics: ship is always 0
				  								player.getFlagPoints(),
				  								player.getKillPoints(),
				  								player.getWins(),
				  								player.getLosses(),
				  								calculateRating(player.getKillPoints(), player.getWins(), player.getLosses()),
				  								calculateAverage(player.getKillPoints(), player.getWins()));
		  previousScoreAvailable = false;
	  }
	  
	  
	  if(player.getShipType() > 0 && (action.equals("scoreupdate") || action.equals("killee") || action.equals("killer"))) {
		  PubStatsScore pubStatsScoreShip;
		  if(stats.containsKey(player.getShipType()+":"+player.getPlayerID())) {
			  pubStatsScoreShip = stats.get(player.getShipType()+":"+player.getPlayerID());
		  } else {
			  // Create new empty object
			  pubStatsScoreShip = new PubStatsScore(	pubStatsPlayer, 
					  									player.getShipType(),
					  									0,		// Flag points
					  									0,		// Kill points
					  									0,		// Wins
					  									0,		// Losses
					  									0,		// Rating
					  									0);		// Average
		  }
		  
		  if(previousScoreAvailable) {
			  pubStatsScoreShip.setFlagPoints(player.getFlagPoints()-pubStatsScore.getFlagPoints());
			  pubStatsScoreShip.setKillPoints(player.getKillPoints()-pubStatsScore.getKillPoints());
			  pubStatsScoreShip.setWins(player.getWins()-pubStatsScore.getWins());
			  pubStatsScoreShip.setLosses(player.getLosses()-pubStatsScore.getLosses());
			  pubStatsScoreShip.setRate(calculateRating(pubStatsScoreShip.getKillPoints(), pubStatsScoreShip.getWins(), pubStatsScoreShip.getLosses()));
			  pubStatsScoreShip.setAverage(calculateAverage(pubStatsScoreShip.getKillPoints(), pubStatsScoreShip.getWins()));
			  pubStatsScoreShip.resetLastUpdate();
		  }
		  
		  // TODO: Update total score aswell?
		  pubStatsScore.resetLastUpdate();
		  // etc
		  
		  stats.put(player.getShipType()+":"+player.getPlayerID(), pubStatsScoreShip);
	  } else if(action.equals("entered")) {
		  // No update necessary here
	  } else if(action.equals("left")) {
		  // No update necessary here
	  } else if(action.equals("scorereset")) {
		  // Player scoreresets or gets scorereset
		  // Set the scorereset property of the total score and each ship score
		  pubStatsScore.setScorereset(true);
		  for(int i = 1 ; i <= 8; i++) {
			  if(stats.containsKey(i+":"+player.getPlayerID())) {
				  stats.get(i+":"+player.getPlayerID()).setScorereset(true);
			  } else {
				  PubStatsScore ship = new PubStatsScore(pubStatsPlayer, i, 0, 0, 0, 0, 0, 0);
				  ship.setScorereset(true);
				  stats.put(i+":"+player.getPlayerID(), ship);
			  }
		  }
	  }
	  
	  stats.put(":"+player.getPlayerID(), pubStatsScore);
	  debug("Stats size: "+stats.size()); 
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

  /**
   * Cycle the stats HashMap and check if a player has IP and Usage
   */
  private synchronized void fillBlankPlayerInfo() {
      synchronized(stats) {
          for(PubStatsScore score:stats.values()) {
              PubStatsPlayer player = score.getPlayer();
              if(player.getIP() == null) {
                  String playerName = getInfoFromBuffer(player.getName(), "Name");
                  String playerIP = getInfoFromBuffer(player.getName(), "IP"  );
                  String playerTimezone = getInfoFromBuffer(player.getName(), "Timezone");
                  
                  if(playerName != null)
                      player.setName(playerName);
                  if(playerIP != null)
                      player.setIP(playerIP);
                  if(playerTimezone != null)
                      player.setTimezone(playerTimezone);
              }
              if(player.getUsage() == null) {
                  String playerUsage = getInfoFromBuffer(player.getName(), "Usage");
                  
                  if(playerUsage != null ) {
                      player.setUsage(playerUsage);
                  }
              }
          }
      }
  }
  
  /**
   * Retrieve info from the *info buffer
   * 
   * @param playerName *info or %tickname player name
   * @param infoPart what info to retrieve: choose from 'Name','IP', 'Timezone' and 'Usage'
   * @return the requested info part from the info buffer or null if it isn't found
   */
  private String getInfoFromBuffer(String playerName, String infoPart) {
      // WARNING: playerName is that from %tickname OR *info, infoBuffer contains playernames from %tickname OR *info
      String result = null;
      
      playerName = getFuzzyPlayerNameInfoBuffer(playerName);
      if(playerName == null) {
          // playername already isn't found
          return null;
      }
      
      for(String info:infoBuffer) {
          if(info != null && info.toLowerCase().indexOf(playerName.toLowerCase()) > -1) {
              // Found the player in the buffer!
              if(infoPart.equals("Name")) {
                  result = getInfo(info, "TypedName:");
                  break;
              }
              if(infoPart.equals("IP")) {
                  result = getInfo(info, "IP:");
                  break;
              }
              if(infoPart.equals("Timezone")) {
                  result = getInfo(info, "TimeZoneBias:");
                  break;
              }
              if(infoPart.equals("Usage")) {
                  result = getInfo(info, "Total: ");
                  break;
              }
          }
      }
      
      return result;
  }
  
  /**
   * Searches the infoBuffer for the best match for the given playerName considering
   * that this name can be of 23 to 19 characters maximum length
   * 
   * @param playerName
   * @return
   */
  private String getFuzzyPlayerNameInfoBuffer(String playerName) {
      // Assume the given playerName is 23 characters maximum length
      
      if(playerName == null) {
          return playerName;
      }
      if(playerName.length() <= 19) {
          return playerName;
      }
      
      for(int i = 23; i > 19; i--) {
          for(String info:infoBuffer) {
              try {
                  if(info.indexOf(playerName.toLowerCase().substring(0,i))>-1) {
                      // found playername
                      return playerName.substring(0,i);
                  }
              } catch(IndexOutOfBoundsException ioobe) {
                  // substring's endindex is larger then the playerName
              }
          }
      }
      
      return null; //not found
  }
  
  /**
   * Gets the info parameters from the *info response
   * 
   * @param message
   * @param infoName
   * @return
   */
  private String getInfo(String message, String infoName) {
	  int beginIndex = message.indexOf(infoName);
	  int endIndex;

	  if(beginIndex == -1)
		  return null;
	  beginIndex = beginIndex + infoName.length();
	  
	  // Custom beginIndex for "Total:" (Usage)
	  /*if(infoName.startsWith("Total:")) {
	      // TIME: Session:    0:39:00  Total:    0:39:00  Created: 1-4-2008 09:01:41
	      // TIME: Session:   20:28:00  Total:36510:02:00  Created: 10-26-2002 10:23:34
	      while(beginIndex < message.length() && message.charAt(beginIndex) == ' ') {
	          beginIndex++;
	      }
	  }
	  Removed because moving the beginIndex doesn't work for high usage numbers (see 2nd example above)
	  Furthermore, the trim() removes any left spaces (below).
	  */
	  
	  endIndex = message.indexOf("  ", beginIndex);
	  if(endIndex == -1)
		  endIndex = message.length();
	  return message.substring(beginIndex, endIndex).trim();
  }
  
  
  private void debug(String message) {
      if(debug)
          m_botAction.sendChatMessage(message);
  }
  
  
  
  
  /*********************** TimerTask classes ****************************/
  
  private class SendStatsTask extends TimerTask {
	public void run() {
		m_botAction.ipcTransmit(IPCCHANNEL, stats);
	}
  }
  
  private class RequestInfo extends TimerTask {
      private pubbotstats bot;
      
      public RequestInfo(pubbotstats bot) {
          this.bot = bot;
      }
      
      public void run() {
          
          if(bot.infoRequested.size() == 0)
              return;
          
          for(String playername:bot.infoRequested.keySet()) {
              if(bot.infoRequested.get(playername)==false) {
                  m_botAction.sendUnfilteredPrivateMessage(playername, "*info");
                  bot.infoRequested.put(playername, true);
                  bot.debug("info of "+playername+" queried");
              }
          }
      }
  }
}
