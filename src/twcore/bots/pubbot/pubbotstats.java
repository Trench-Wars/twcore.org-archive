package twcore.bots.pubbot;

import java.util.HashMap;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.bots.pubhub.PubStats;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;

public class pubbotstats extends PubBotModule {
  private String botName;
  
  protected HashMap<String, PubStats> stats = new HashMap<String, PubStats>();
  // 			   <#ship#Playername, PubStats>
  // F.ex:		   3MMaverick, PubStats <-- spider
  
  protected final String IPCCHANNEL = "pubstats";
  private final int SEND_STATS_TIME = 1000*60*15; // 15 minutes

  public void initializeModule() {
    botName = m_botAction.getBotName();
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
		  updateStats(
				  killer.getPlayerName(), 
				  killer.getShipType(), 
				  killer.getSquadName(),
				  killer.getFlagPoints(),
				  killer.getKillPoints(),
				  killer.getWins(),
				  killer.getLosses(),
				  this.calculateRating(killer.getKillPoints(), killer.getWins(), killer.getLosses()),
				  (killer.getKillPoints()/killer.getWins()));
	  }
	  if(killed != null) {
		  updateStats(
				  killed.getPlayerName(), 
				  killed.getShipType(), 
				  killed.getSquadName(),
				  killed.getFlagPoints(),
				  killed.getKillPoints(),
				  killed.getWins(),
				  killed.getLosses(),
				  this.calculateRating(killed.getKillPoints(), killed.getWins(), killed.getLosses()),
				  (killed.getKillPoints()/killed.getWins()));
	  }
  }
  
  public void handleEvent( PlayerEntered event ) {
	  Player player = m_botAction.getPlayer(event.getPlayerID());
	  updateStats(
			  player.getPlayerName(), 
			  player.getShipType(), 
			  player.getSquadName(),
			  player.getFlagPoints(),
			  player.getKillPoints(),
			  player.getWins(),
			  player.getLosses(),
			  this.calculateRating(player.getKillPoints(), player.getWins(), player.getLosses()),
			  (player.getKillPoints()/player.getWins()));
  }
  
  public void handleEvent( PlayerLeft event ) {
	  Player player = m_botAction.getPlayer(event.getPlayerID());
	  updateStats(
			  player.getPlayerName(), 
			  player.getShipType(), 
			  player.getSquadName(),
			  player.getFlagPoints(),
			  player.getKillPoints(),
			  player.getWins(),
			  player.getLosses(),
			  this.calculateRating(player.getKillPoints(), player.getWins(), player.getLosses()),
			  (player.getKillPoints()/player.getWins()));
  }

  public void cancel() {
	  m_botAction.ipcTransmit(IPCCHANNEL, stats);
	  m_botAction.cancelTasks();
	  stats.clear();
	  stats = null;
  }
  
  /**** Private Helper methods ****/
  
  private void updateStats(
		  String playername, 
		  int ship, 
		  String squad, 
		  int flagpoints, 
		  int killpoints, 
		  int wins, 
		  int losses, 
		  int rate, 
		  int average) {
	  
	  PubStats pubstats = new PubStats();
	  pubstats.setPlayername(playername);
	  pubstats.setShip(ship);
	  pubstats.setSquad(squad);
	  pubstats.setFlagPoints(flagpoints);
	  pubstats.setKillPoints(killpoints);
	  pubstats.setWins(wins);
	  pubstats.setLosses(losses);
	  pubstats.setRate(rate);
	  pubstats.setAverage(average);
	  stats.put(String.valueOf(ship) + playername, pubstats);
	  // 
  }
  
  private int calculateRating(int killPoints, int wins, int losses) {
	  return (killPoints*10 + (wins-losses)*100) / (wins +100);
  }
  
  
  
  /*********************** TimerTask classes ****************************/
  
  private class sendStatsTask extends TimerTask {
	public void run() {
		m_botAction.ipcTransmit(IPCCHANNEL, stats);
	}
  }
}
