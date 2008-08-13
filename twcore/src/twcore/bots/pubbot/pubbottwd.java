package twcore.bots.pubbot;

import java.util.TreeMap;
import java.util.Iterator;
import java.sql.SQLException;
import java.sql.ResultSet;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.PlayerEntered;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

public class pubbottwd extends PubBotModule {

	private TreeMap<String, String[]> games;
	private String webdb = "website";
	
    public void initializeModule(){
    	games = new TreeMap<String, String[]>();
    }

    public void cancel(){
    }

    public void requestEvents( EventRequester eventRequester ){
        eventRequester.request( EventRequester.PLAYER_ENTERED );
    }


    public void handleEvent( PlayerEntered event ){
    	Player p = m_botAction.getPlayer(event.getPlayerID());
    	String squad = p.getSquadName();
    	if(p == null || squad == null)return;
    	try{
    		/*
        	 * 0 - arena game is in
        	 * 1 - team 1 squad id
        	 * 2 - team 2 squad id
        	 * 3 - name of bot hosting the game
        	 */
    		ResultSet rs = m_botAction.SQLQuery(webdb, "SELECT fnTeamID FROM tblTeam WHERE fcTeamName LIKE '"+squad+"' AND (fdDeleted = 0 OR fdDeleted IS NULL)");
    		String squadID = Integer.toString(rs.getInt("fnTeamID"));
    		Iterator<String[]> i = games.values().iterator();
    		while(i.hasNext()){
    			String[] game = i.next();
    			if(squadID.equals(game[1]) || squadID.equals(game[2]))
    				m_botAction.sendSmartPrivateMessage( p.getPlayerName(), "Your squad is currently playing a TWD game in ?go " + game[0] + " -" + game[3]);
    		}
    		m_botAction.SQLClose(rs);
    	}catch(SQLException e){
    		Tools.printStackTrace(e);
    	}
    }
    
    public void handleEvent(InterProcessEvent event) {
    	try
        {
    		if(event.getObject() instanceof IPCMessage == false)
    			return;
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
    		gotTWDGameCmd(sender, message.substring(8), true);
    	else if(message.startsWith("endtwdgame "))
    		gotTWDGameCmd(sender, message.substring(11), false);
    }
    
    public void gotTWDGameCmd(String name, String message, boolean isStartOfGame){
    	/*
    	 * 0 - arena game is in
    	 * 1 - team 1 squad id
    	 * 2 - team 2 squad id
    	 * 3 - name of bot hosting the game
    	 */
    	String[] msg = message.split(":");
    	if(isStartOfGame && !games.containsKey(msg[0]))
    		games.put(msg[0], msg);
    	else
    		games.remove(msg[0]);
    }
}

