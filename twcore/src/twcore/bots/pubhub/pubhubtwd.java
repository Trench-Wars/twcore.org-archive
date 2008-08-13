package twcore.bots.pubhub;

import java.util.TreeMap;
import java.util.HashSet;
import java.util.Iterator;
import java.sql.SQLException;
import java.sql.ResultSet;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.util.ipc.IPCMessage;
import twcore.core.util.Tools;

public class pubhubtwd extends PubBotModule {

  /*
   * String[] object of games
   *0 - Arena Game is In
   *1 - Freq 1 ID
   *2 - Freq 2 ID
   *3 - MatchBot hosting game
   */
  private HashSet<String[]> games;
  private TreeMap<String, Integer> teams;
  private long cfg_time;
  private String webdb = "website";
  private String PUBBOTS = "pubBots";

  public void initializeModule(){
    games = new HashSet<String[]>();
    teams = new TreeMap<String, Integer>();
    cfg_time = m_botAction.getBotSettings().getInt("TimeInMillis");
  }
  
  public void requestEvents(EventRequester r){}
  
  public void cancel() {
      games.clear();
      teams.clear();
  }

  public void handleEvent(InterProcessEvent event) {
  	try
      {
  		if(!(event.getObject() instanceof IPCMessage))return;
  		IPCMessage ipcMessage = (IPCMessage) event.getObject();
  		String message = ipcMessage.getMessage();
  		String recipient = ipcMessage.getRecipient();
  		String sender = ipcMessage.getSender();
  		if(recipient == null || recipient.equals(m_botAction.getBotName()))
  			handleBotIPC(sender, message);
    }
    catch(Exception e){
      Tools.printStackTrace(e);
    }
  }
  
  public void handleBotIPC(String sender, String message){
  	if(message.startsWith("twdgame "))
  		gotTWDGameCmd(message.substring(8), true);
  	else if(message.startsWith("endtwdgame "))
  		gotTWDGameCmd(message.substring(11), false);
  	else if(message.startsWith("getgame "))
  		giveGame(sender, message.substring(10));
  	
  }
  
  public void gotTWDGameCmd(String message, boolean isStartOfGame){
  	String[] msg = message.split(":");
  	if(isStartOfGame && !games.contains(msg[0]))
  		games.add(msg);
  	else
  		games.remove(msg[0]);
  }
  
  public void giveGame(String pubbot, String message){
	  if((System.currentTimeMillis() - cfg_time) > (12 * Tools.TimeInMillis.HOUR)){
		  populateTeams();
		  m_botAction.getBotSettings().put("TimeInMillis", System.currentTimeMillis());
	  }
	  String[] temp = message.split(":");
	  if(temp.length != 2)return;
	  String playerName = temp[0];
	  String squadName = temp[1];
	  if(teams.containsKey(squadName.toLowerCase())){
		  String teamID = Integer.toString(teams.get(squadName.toLowerCase()));
		  Iterator<String[]> i = games.iterator();
		  while(i.hasNext()){
			  String[] msg = i.next();
			  if(teamID.equals(msg[1]) || teamID.equals(msg[2]))
				  m_botAction.ipcTransmit(PUBBOTS, new IPCMessage("givegame " + playerName + ":" + msg[0] + ":" + msg[3], pubbot));			  
		  }
	  }
  }
  
  public void populateTeams(){
	  try{
		  ResultSet rs = m_botAction.SQLQuery(webdb, "SELECT fnTeamID, fcTeamName FROM tblTeam WHERE fdDeleted IS NULL OR fdDeleted = 0");
		  while(rs != null && rs.next()){
			  teams.put(rs.getString("fcTeamName").toLowerCase(), rs.getInt("fnTeamID"));
		  }
		  m_botAction.SQLClose(rs);
	  }catch(SQLException e){
		  Tools.printStackTrace(e);
	  }
  }
  
}