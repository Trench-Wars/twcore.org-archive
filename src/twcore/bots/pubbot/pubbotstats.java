package twcore.bots.pubbot;

import java.util.Arrays;
import java.util.Iterator;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.ScoreReset;
import twcore.core.events.ScoreUpdate;
import twcore.core.game.Player;
import twcore.core.helper.pubstats.PubStatsArena;
import twcore.core.helper.pubstats.PubStatsPlayer;
import twcore.core.util.Tools;

public class pubbotstats extends PubBotModule {
  
    private PubStatsArena arenaStats;
    
    
    private String[] infoBuffer = new String[8];
    
    protected final String IPCCHANNEL = "pubstats";
    private final int SEND_STATS_TIME = Tools.TimeInMillis.MINUTE*15; // 15 minutes
    
    private boolean debug = true;

  public void initializeModule() {
      arenaStats = new PubStatsArena(m_botAction.getArenaName());
      
      // Add all players from arena
      // (it's needed to do this manually because the module is loaded after the bot has entered the arena)
      Iterator<Player> it = m_botAction.getPlayerIterator();
      while(it.hasNext()) {
          Player p = it.next();
          if(!m_botAction.getOperatorList().isBotExact(p.getPlayerName())) {
              arenaStats.addPlayer(p);
          }
      }
      SendStatsTask sendstats = new SendStatsTask();
      m_botAction.scheduleTaskAtFixedRate(sendstats, SEND_STATS_TIME, SEND_STATS_TIME);
      RequestInfo requestInfo = new RequestInfo();
      m_botAction.scheduleTaskAtFixedRate(requestInfo, Tools.TimeInMillis.MINUTE, Tools.TimeInMillis.SECOND*5);
  }

  public void requestEvents(EventRequester eventRequester) {
	  eventRequester.request(EventRequester.PLAYER_ENTERED);
	  eventRequester.request(EventRequester.PLAYER_LEFT);
	  eventRequester.request(EventRequester.PLAYER_DEATH);
	  eventRequester.request(EventRequester.SCORE_UPDATE);
	  eventRequester.request(EventRequester.SCORE_RESET);
	  eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
	  eventRequester.request(EventRequester.ARENA_JOINED);
	  eventRequester.request(EventRequester.MESSAGE);
  }
  
  public void handleEvent( ArenaJoined event ) {
	  m_botAction.receiveAllPlayerDeaths();
  }
  
  public void handleEvent( ScoreUpdate event ) {
      // Update the score of the player
      Player p = m_botAction.getPlayer(event.getPlayerID());
      
      PubStatsPlayer player = arenaStats.getPlayer(p.getPlayerName());
      if(player != null) {
          
          // Update the score of the ship the player is in
          player.updateShipScore(
                  player.getShip(), 
                  event.getFlagPoints()-player.getFlagPoints(), 
                  event.getKillPoints()-player.getKillPoints(), 
                  event.getWins()-player.getWins(), 
                  event.getLosses()-player.getLosses());    
          
          // Update the overall score of the player
          player.setFlagPoints(event.getFlagPoints());
          player.setKillPoints(event.getKillPoints());
          player.setWins(event.getWins());
          player.setLosses(event.getLosses());
          player.updated();
      }
  }
  
  public void handleEvent( ScoreReset event ) {
      // Score reset the player
      Player p = m_botAction.getPlayer(event.getPlayerID());
      
      PubStatsPlayer player = arenaStats.getPlayer(p.getPlayerName());
      if(player != null) {
          player.scorereset();
          player.updated();
      }
  }
  
  public void handleEvent( PlayerEntered event ) {
      if(m_botAction.getOperatorList().isBotExact(event.getPlayerName()))
          return;
      
      // A new player entered
      // (this event is also fired after bot enters the arena)
	  PubStatsPlayer player = arenaStats.getPlayer(event.getPlayerName());
	  
	  if(player == null) {
		  arenaStats.addPlayer(
		          event.getPlayerName(),
		          event.getSquadName(),
		          event.getFlagPoints(),
		          event.getKillPoints(),
		          event.getLosses(),
		          event.getWins(),
		          event.getShipType());
	  } else {
	      player.seen();
	  }
  }
  
  public void handleEvent( PlayerLeft event ) {
      Player p = m_botAction.getPlayer(event.getPlayerID());
      
      if(arenaStats.getPlayer(p.getPlayerName()) != null)
          arenaStats.getPlayer(p.getPlayerName()).seen();
  }
  
  public void handleEvent( PlayerDeath event ) {
      Player killee = m_botAction.getPlayer(event.getKilleeID());
      Player killer = m_botAction.getPlayer(event.getKillerID());
      PubStatsPlayer killeeStats = arenaStats.getPlayer(killee.getPlayerName());
      PubStatsPlayer killerStats = arenaStats.getPlayer(killer.getPlayerName());
      
      if(killee != null) {
          // Update ship stats
          killeeStats.updateShipScore(
                  killeeStats.getShip(), 
                  killee.getFlagPoints()-killeeStats.getFlagPoints(), 
                  killee.getKillPoints()-killeeStats.getKillPoints(), 
                  killee.getWins()-killeeStats.getWins(), 
                  killee.getLosses()-killeeStats.getLosses());
          
          // Update overall stats
          killeeStats.setWins(killee.getWins());
          killeeStats.setLosses(killee.getLosses());
          killeeStats.setKillPoints(killee.getKillPoints());
          killeeStats.setFlagPoints(killee.getFlagPoints());
      }
      
      if(killer != null) {
          // Update ship stats
          killerStats.updateShipScore(
                  killerStats.getShip(), 
                  killer.getFlagPoints()-killerStats.getFlagPoints(), 
                  killer.getKillPoints()-killerStats.getKillPoints(), 
                  killer.getWins()-killerStats.getWins(), 
                  killer.getLosses()-killerStats.getLosses());
          
          // Update overall stats
          killerStats.setWins(killer.getWins());
          killerStats.setLosses(killer.getLosses());
          killerStats.setKillPoints(killer.getKillPoints());
          killerStats.setFlagPoints(killer.getFlagPoints());
      }
      
  }
  
  public void handleEvent( FrequencyShipChange event) {
      Player p = m_botAction.getPlayer(event.getPlayerID());
      
      if(arenaStats.getPlayer(p.getPlayerName()) != null)
          arenaStats.getPlayer(p.getPlayerName()).shipchange(event.getShipType());
  }
  
  // Examples
  
  /*
  PubBot9> *info
  IP:24.22.176.33  TimeZoneBias:420  Freq:9999  TypedName:eN.yoU.Tee.Zee.  Demo:0  MachineId:1828021299
  Ping:130ms  LowPing:130ms  HighPing:160ms  AvePing:130ms
  LOSS: S2C:0.1%  C2S:0.1%  S2CWeapons:0.1%  S2C_RelOut:0(0)
  S2C:47864-->47805  C2S:7449-->7449
  C2S CURRENT: Slow:0 Fast:468 0.0%   TOTAL: Slow:0 Fast:3006 0.0%
  S2C CURRENT: Slow:0 Fast:897 0.0%   TOTAL: Slow:0 Fast:22186 0.0%
  TIME: Session:    0:57:00  Total: 2160:31:00  Created: 5-16-2006 06:44:09
  Bytes/Sec:241  LowBandwidth:0  MessageLogging:0  ConnectType:UnknownNotRAS



  IP:72.232.237.74  TimeZoneBias:480  Freq:9999  TypedName:Mr. Arrogant 2  Demo:0  MachineId:1693149144
  Ping:0ms  LowPing:0ms  HighPing:0ms  AvePing:0ms
  LOSS: S2C:0.1%  C2S:0.1%  S2CWeapons:0.0%  S2C_RelOut:32(245)
  S2C:1017491-->1017416  C2S:233740-->233748
  C2S CURRENT: Slow:0 Fast:117 0.0%   TOTAL: Slow:0 Fast:501 0.0%
  S2C CURRENT: Slow:0 Fast:0 0.0%   TOTAL: Slow:0 Fast:0 0.0%
  TIME: Session:   20:28:00  Total:36510:02:00  Created: 10-26-2002 10:23:34
  Bytes/Sec:1615  LowBandwidth:0  MessageLogging:0  ConnectType:UnknownNotRAS*/
  
  public void handleEvent( Message event ) {
	  String message = event.getMessage();
	  
	  if(message != null && event.getMessageType() == Message.ARENA_MESSAGE) {
	      // Store *info results in infoBuffer
	      if(message.startsWith("IP:") && message.indexOf("TypedName:") > 0) {
	          Arrays.fill(infoBuffer, ""); // clear buffer
	          infoBuffer[0] = message;     // *info 1st line
	      } else
	      if(message.startsWith("Ping:") && message.indexOf("HighPing:") > 0) {
	          infoBuffer[1] = message;     // *info 2nd line
	      } else
	      if(message.startsWith("LOSS:") && message.indexOf("S2CWeapons:") > 0) {
	          infoBuffer[2] = message;
	      } else
	      if(message.startsWith("S2C:") && message.indexOf("C2S:") > 0) {
	          infoBuffer[3] = message;
	      } else
	      if(message.startsWith("C2S CURRENT: Slow:") && message.indexOf("TOTAL: Slow:") > 0) {
	          infoBuffer[4] = message;
	      } else
	      if(message.startsWith("S2C CURRENT: Slow:") && message.indexOf("TOTAL: Slow:") > 0) {
	          infoBuffer[5] = message;
	      } else
	      if(message.startsWith("TIME: Session:") && message.indexOf("Total:") > 0) {
	          infoBuffer[6] = message;
	      } else
	      if(message.startsWith("Bytes/Sec:") && message.indexOf("ConnectType:") > 0 ) {
	          infoBuffer[7] = message;
	          processInfoBuffer(infoBuffer);
	      }
	  }
	  
	  // COMMANDS
	  if(message != null && event.getMessageType() == Message.PRIVATE_MESSAGE) {
	      
	      // debug command, force save
	      if(message.startsWith("!forcesave")) {
	          m_botAction.sendPrivateMessage(event.getPlayerID(), "done");
	          m_botAction.ipcTransmit(IPCCHANNEL, arenaStats);   
	      }
	      
	      // debug command, check statistics map
	      if(message.startsWith("!check")) {
	          
	          int good = 0;
	          String names = "";
	          
	          Iterator<Player> it = m_botAction.getPlayerIterator();
	          
	          while(it.hasNext()) {
	              Player p = it.next();
	              PubStatsPlayer player = arenaStats.getPlayer(p.getPlayerName());
	              if(player != null && player.isExtraInfoFilled()) {
	                  good++;
	              } else if(player != null && !player.isExtraInfoFilled()) {
	                  names += player.getName() +", ";
	              }
	          }
	       
	          debug("Stats: "+ good +" / "+ (m_botAction.getArenaSize()-1) + " (" + arenaStats.size() + ")");
	          
	          if(names.length() > 0)
	              debug(names);
	      }
	  }
  }

  @Override
  public void cancel() {
	  //m_botAction.ipcTransmit(IPCCHANNEL, stats);
	  m_botAction.cancelTasks();
  }
  
  /**** Private Helper methods ****/
  
  private void processInfoBuffer(String[] buffer) {
      String name = getInfo(buffer[0], "TypedName:");
      String IP = getInfo(buffer[0], "IP:");
      String machineID = getInfo(buffer[0], "MachineId:");
      String timezone = getInfo(buffer[0], "TimeZoneBias:");
      String usage = getInfo(buffer[6], "Total:");
      String dateCreated = getInfo(buffer[6], "Created:");
      
      PubStatsPlayer player = arenaStats.getPlayer(name);
      
      // The long name is longer then the registered name from %tickname
      // try searching for the name where each registered name can be the start of this name 
      if(player == null) {
          player = arenaStats.getPlayerOnPartialName2(name);
      }
      
      if(player != null) {
          player.setName(name);
          player.setIP(IP);
          player.setMachineID(machineID);
          player.setTimezone(timezone);
          player.setUsage(usage);
          player.setDateCreated(dateCreated);
          
          debug("Received info of player '"+name+"'");
      }
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
	  
	  // TIME: Session:    0:39:00  Total:    0:39:00  Created: 1-4-2008 09:01:41
      // TIME: Session:   20:28:00  Total:36510:02:00  Created: 10-26-2002 10:23:34
	  while(beginIndex < message.length() && message.charAt(beginIndex) == ' ') {
          beginIndex++;
      }
	  
	  endIndex = message.indexOf("  ", beginIndex);
	  if(endIndex == -1)
		  endIndex = message.length();
	  return message.substring(beginIndex, endIndex).trim();
  }
  
  
  private void debug(String message) {
      if(debug)
          m_botAction.sendChatMessage(2,message);
  }
  
  
  
  
  /*********************** TimerTask classes ****************************/
  
  private class SendStatsTask extends TimerTask {
	public void run() {
		m_botAction.ipcTransmit(IPCCHANNEL, arenaStats);
	}
  }
  
  private class RequestInfo extends TimerTask {
      
      public void run() {
          // Loop through all players in arena and check if we have their *info
          // if one player is found without extra info, request it and then quit
          // This task is repeated in a short time so a time delay is between each *info
          // We have to do it this way because doing multiple *info's at once will result in that one *info request falls away
          Iterator<Player> it = m_botAction.getPlayerIterator();
          
          while(it.hasNext()) {
              Player p = it.next();
              PubStatsPlayer player = arenaStats.getPlayer(p.getPlayerName());
              
              if(player != null && !player.isExtraInfoFilled()) {
                  m_botAction.sendUnfilteredPrivateMessage(p.getPlayerID(), "*info");
                  debug("Requesting info of '"+player.getName()+"'");
                  break;
              }
          }
      }
  }
}
