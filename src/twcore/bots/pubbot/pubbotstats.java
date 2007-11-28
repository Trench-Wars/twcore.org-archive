package twcore.bots.pubbot;

import java.util.HashMap;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.bots.pubhub.PubStatsPlayer;
import twcore.bots.pubhub.PubStatsScore;
import twcore.core.EventRequester;
import twcore.core.events.ArenaJoined;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.ScoreReset;
import twcore.core.events.ScoreUpdate;
import twcore.core.game.Player;

public class pubbotstats extends PubBotModule {
  
  protected HashMap<String, PubStatsScore> stats = new HashMap<String, PubStatsScore>();
  // 			   <Ship#playerid, PubStats>
  // F.ex:		   3:1234, PubStats <-- spider
  // F.ex:         :1234, PubStats <-- total stats
  
  private boolean infoRequested = false;
  
  protected final String IPCCHANNEL = "pubstats";
  private final int SEND_STATS_TIME = 1000*60*15; // 15 minutes

  public void initializeModule() {
    m_botAction.scheduleTaskAtFixedRate(new sendStatsTask(), SEND_STATS_TIME, SEND_STATS_TIME);
  }

  public void requestEvents(EventRequester eventRequester) {
	  eventRequester.request(EventRequester.PLAYER_ENTERED);
	  eventRequester.request(EventRequester.PLAYER_LEFT);
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
	  Player player = m_botAction.getPlayer(event.getPlayerID());
	  if(player != null) {
		  updateStats(player, "left");
	  }
  }
  
  public void handleEvent( Message event ) {
	  String message = event.getMessage();
	  
	  if(infoRequested && event.getMessageType() == Message.ARENA_MESSAGE && message.startsWith("IP:")) {
		  String playerName = getInfo(message, "TypedName:");
		  String playerIP = getInfo(message, "IP:");
		  String timezone = getInfo(message, "TimeZoneBias:");
		  // TODO: PlayerId opzoeken en info op hashmap zetten
	  }
	  if(infoRequested && event.getMessageType() == Message.ARENA_MESSAGE && message.startsWith("TIME: Session:")) {
		  String usage = getInfo(message, "Total: ");
		  infoRequested = false;
		  // TODO: Playerid opzoeken en info op hashmap zetten
	  }
  }

  public void cancel() {
	  m_botAction.ipcTransmit(IPCCHANNEL, stats);
	  m_botAction.cancelTasks();
	  stats.clear();
	  stats = null;
  }
  
  /**** Private Helper methods ****/
  
  private void updateStats(Player player, String action) {
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
		  pubStatsPlayer = new PubStatsPlayer(	player.getPlayerName(),
				  								player.getSquadName(),
				  								null,		// IP is unknown
				  								0,			// timezone is unknown
				  								null);		// usage is unknown
		  m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*info");
		  infoRequested = true;
		  
		  pubStatsScore = new PubStatsScore(	pubStatsPlayer, 
				  								player.getShipType(),
				  								player.getFlagPoints(),
				  								player.getKillPoints(),
				  								player.getWins(),
				  								player.getLosses(),
				  								calculateRating(player.getKillPoints(), player.getWins(), player.getLosses()),
				  								calculateAverage(player.getKillPoints(), player.getWins()));
		  previousScoreAvailable = false;
	  }
	  
	  
	  if(action.equals("scoreupdate")) {
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
			  stats.put(player.getShipType()+":"+player.getPlayerID(), pubStatsScoreShip);
		  }
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
	  m_botAction.sendChatMessage("Stats size: "+stats.size()); 
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
	  endIndex = message.indexOf("  ", beginIndex);
	  if(endIndex == -1)
		  endIndex = message.length();
	  return message.substring(beginIndex, endIndex);
  }
  
  
  /*********************** TimerTask classes ****************************/
  
  private class sendStatsTask extends TimerTask {
	public void run() {
		m_botAction.ipcTransmit(IPCCHANNEL, stats);
	}
  }
  
  
  
}
