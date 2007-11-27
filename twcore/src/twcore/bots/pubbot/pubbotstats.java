package twcore.bots.pubbot;

import java.util.HashMap;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.bots.pubhub.PubStats;
import twcore.core.EventRequester;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;

public class pubbotstats extends PubBotModule {
  
  protected HashMap<String, PubStats> stats = new HashMap<String, PubStats>();
  // 			   <#ship#Playername, PubStats>
  // F.ex:		   3MMaverick, PubStats <-- spider
  
  protected final String IPCCHANNEL = "pubstats";
  private final int SEND_STATS_TIME = 1000*60*15; // 15 minutes

  public void initializeModule() {
    m_botAction.scheduleTaskAtFixedRate(new sendStatsTask(), SEND_STATS_TIME, SEND_STATS_TIME);
  }

  public void requestEvents(EventRequester eventRequester) {
	  eventRequester.request(EventRequester.PLAYER_DEATH);
	  eventRequester.request(EventRequester.PLAYER_ENTERED);
	  eventRequester.request(EventRequester.PLAYER_LEFT);
  }
  
  public void handleEvent( PlayerDeath event ) {
	  Player killer = m_botAction.getPlayer(event.getKillerID());
	  Player killed = m_botAction.getPlayer(event.getKilleeID());
	  
	  if(killer != null) {
		  updateStats(killer);
		  /*updateStats(
				  killer.getPlayerName(), 
				  killer.getShipType(), 
				  killer.getSquadName(),
				  killer.getFlagPoints(),
				  killer.getKillPoints(),
				  killer.getWins(),
				  killer.getLosses(),
				  this.calculateRating(killer.getKillPoints(), killer.getWins(), killer.getLosses()),
				  this.calculateAverage(killer.getKillPoints(),killer.getWins()));*/
	  }
	  if(killed != null) {
		  updateStats(killed);
		  /*updateStats(
				  killed.getPlayerName(), 
				  killed.getShipType(), 
				  killed.getSquadName(),
				  killed.getFlagPoints(),
				  killed.getKillPoints(),
				  killed.getWins(),
				  killed.getLosses(),
				  this.calculateRating(killed.getKillPoints(), killed.getWins(), killed.getLosses()),
				  this.calculateAverage(killed.getKillPoints(),killed.getWins()));*/
	  }
  }
  
  public void handleEvent( PlayerEntered event ) {
	  Player player = m_botAction.getPlayer(event.getPlayerID());
	  updateStats(player);
	  /*updateStats(
			  player.getPlayerName(), 
			  player.getShipType(), 
			  player.getSquadName(),
			  player.getFlagPoints(),
			  player.getKillPoints(),
			  player.getWins(),
			  player.getLosses(),
			  this.calculateRating(player.getKillPoints(), player.getWins(), player.getLosses()),
			  this.calculateAverage(player.getKillPoints(),player.getWins()));*/
  }
  
  public void handleEvent( PlayerLeft event ) {
	  Player player = m_botAction.getPlayer(event.getPlayerID());
	  updateStats(player);
	  /*updateStats(
			  player.getPlayerName(), 
			  player.getShipType(), 
			  player.getSquadName(),
			  player.getFlagPoints(),
			  player.getKillPoints(),
			  player.getWins(),
			  player.getLosses(),
			  this.calculateRating(player.getKillPoints(), player.getWins(), player.getLosses()),
			  this.calculateAverage(player.getKillPoints(),player.getWins()));*/
  }

  public void cancel() {
	  m_botAction.ipcTransmit(IPCCHANNEL, stats);
	  m_botAction.cancelTasks();
	  stats.clear();
	  stats = null;
  }
  
  /**** Private Helper methods ****/
  
  private void updateStats(Player player) {
	  
	  PubStats savedTotalStats;
	  
	  // Get saved statistics
	  if(stats.containsKey("0" + player.getPlayerName())) {
		  savedTotalStats = stats.get("0" + player.getPlayerName());
	  } else {
		  savedTotalStats = new PubStats();
	  }
//		  shipstats.setFlagPoints(shipstats.getFlagPoints() + (player.getFlagPoints() - shipstats.getFlagPoints()));
//		  shipstats.setKillPoints(player.getKillPoints() - shipstats.getKillPoints());
//		  shipstats.setWins(player.getWins())
//	  }
	  
	  
	  // TODO
	  
	  // Save cumulative player/ship statistics
	  
	  PubStats pubstats = new PubStats();
	  pubstats.setPlayername(player.getPlayerName());
	  pubstats.setShip(0);
	  pubstats.setSquad(player.getSquadName());
	  pubstats.setFlagPoints(player.getFlagPoints());
	  pubstats.setKillPoints(player.getKillPoints());
	  pubstats.setWins(player.getWins());
	  pubstats.setLosses(player.getLosses());
	  pubstats.setRate(
			  this.calculateRating(player.getKillPoints(), player.getWins(), player.getLosses())
	  );
	  pubstats.setAverage(
			  this.calculateAverage(player.getKillPoints(), player.getWins())
	  );
	  stats.put("0" + player.getPlayerName(), pubstats);
	  
	  
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
  
  
  
  /*********************** TimerTask classes ****************************/
  
  private class sendStatsTask extends TimerTask {
	public void run() {
		m_botAction.ipcTransmit(IPCCHANNEL, stats);
	}
  }
}
