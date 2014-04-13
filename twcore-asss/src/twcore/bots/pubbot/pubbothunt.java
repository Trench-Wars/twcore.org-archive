// 604 271 8507

package twcore.bots.pubbot;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

public class pubbothunt extends PubBotModule
{
	private String botName;
	HashMap<String,Integer> playerNames = new HashMap<String,Integer>();
	TimerTask activity;
	boolean isRunning = false;
	
	String database = "website";

	public void initializeModule()
	{
		try {
			ResultSet results = m_botAction.SQLQuery(database, "SELECT fcVarValue AS val FROM tblSiteVar WHERE fcVarName = 'fbHuntRunning'");
			results.next();
			isRunning = new Boolean(results.getString("val"));
                        m_botAction.SQLClose( results ); 
		} catch(Exception e) {e.printStackTrace();}
		botName = m_botAction.getBotName();
		if(isRunning) {
			startHunt();
			scheduleTasks();
		}	
	}

	public void requestEvents(EventRequester eventRequester)
	{
		eventRequester.request(EventRequester.MESSAGE);
		eventRequester.request(EventRequester.PLAYER_DEATH);
	}

	public void handleEvent(Message event)
	{
		String sender = getSender(event);
		String message = event.getMessage();
		if(message.equalsIgnoreCase("!prey")) {
			getPrey(sender,false);
		} else if(message.equalsIgnoreCase("!help")) {
			sendHelp(sender);
		} else if(message.equalsIgnoreCase("!score")) {
			getScore(sender);
		} else if(message.equalsIgnoreCase("!register")) {
			registerPlayer(sender);
		}
	}
	
	public void getScore(String name) {
		if(!playerNames.containsKey(name.toLowerCase())) {
			m_botAction.sendPrivateMessage(name, "You do not have any points at the moment.");
			return;
		}
		try {
			ResultSet results = m_botAction.SQLQuery(database, "SELECT fnPoints FROM tblPubHuntCurrent WHERE fnUserID = "+playerNames.get(name.toLowerCase()));
			if(results.next()) {
				m_botAction.sendPrivateMessage(name, "You currently have "+results.getInt("fnPoints")+" points.");
			} else {
				m_botAction.sendPrivateMessage(name, "You do not have any points at the moment.");
			}
                        m_botAction.SQLClose( results );
		} catch(Exception e) {}
	}
	
	public void registerPlayer(String name) {
		if(isRunning) {
			m_botAction.sendPrivateMessage(name, "You can only register when a game is not running.");
			return;
		}
		try {
            m_botAction.SQLQueryAndClose(database, "INSERT INTO tblPubHuntCurrent (fnUserID) (SELECT fnUserID FROM tblUser WHERE fcUserName = '"+Tools.addSlashesToString(name)+"' LIMIT 0,1)");
			m_botAction.sendPrivateMessage(name, "Registered.");
		} catch(Exception e) {}
	}
	
	public void sendHelp(String name) {
		String[] helps = {
			"!prey          -Tells you your prey",
			"!score         -Tells you your current score",
			"!register      -Registers you for next hunt game",
			"!help          -Sends you this message"
		};
		m_botAction.privateMessageSpam(name, helps);
	}

	public void handleIPC(String botSender, String recipient, String sender, String message)
	{
		String command = message.toLowerCase();

		try
		{
			if(command.equals("starthunt")) {
				scheduleTasks();
				startHunt();
			} else if(command.equals("endhunt")) {
				endHunt();
			}
		}
		catch(Exception e)
		{
		}
	}

	public void handleEvent(InterProcessEvent event)
	{
		// If the event.getObject() is anything else then the IPCMessage (pubbotchatIPC f.ex) then return
  	  	if(event.getObject() instanceof IPCMessage == false) { 
  	  		return;
  	  	}
  	  	
		IPCMessage ipcMessage = (IPCMessage) event.getObject();
		String message = ipcMessage.getMessage();
		String recipient = ipcMessage.getRecipient();
		String sender = ipcMessage.getSender();
		String botSender = event.getSenderName();

		try
		{
			if(recipient == null || recipient.equals(botName))
			{
				handleIPC(botSender, recipient, sender, message);
			}
		} catch(Exception e) {e.printStackTrace();}
	}

	public void cancel()
	{
	}

	private String getSender(Message event)
	{
		int messageType = event.getMessageType();

		if(messageType == Message.REMOTE_PRIVATE_MESSAGE || messageType == Message.CHAT_MESSAGE)
			return event.getMessager();
		int senderID = event.getPlayerID();
		return m_botAction.getPlayerName(senderID);
	}
	
	public void scheduleTasks() {
		activity = new TimerTask() {
			public void run() {
				updateActive();
			}
		};
		m_botAction.scheduleTaskAtFixedRate(activity, getDelay() * 1000, 60 * 1000);
	}
	
	public void updateActive() {
		Iterator<String> it = playerNames.keySet().iterator();
		while(it.hasNext()) {
			String name = it.next();
			Player p = m_botAction.getPlayer(name);
			if(p != null && !p.isInSafe() && p.getShipType() != 0)
				try { m_botAction.SQLQueryAndClose(database, getActivityQuery(name));
				} catch(Exception e) {}
		}
	}
	
	public String getActivityQuery(String name) {
		return "UPDATE tblPubHuntCurrent SET fnActivity = fnActivity + 1 WHERE "
			+ "fnUserID = (SELECT fnUserID FROM tblUser WHERE fcUserName = '"
			+ Tools.addSlashesToString(name) + "' LIMIT 0,1)";
	}
	
	public void handleEvent(PlayerDeath event) {
		String hunter = m_botAction.getPlayerName(event.getKillerID()).toLowerCase();
		String huntee = m_botAction.getPlayerName(event.getKilleeID()).toLowerCase();
		if(hunter == null || huntee == null) return;
		if(playerNames.containsKey(hunter) && playerNames.containsKey(huntee)) {
			try {
				ResultSet results = m_botAction.SQLQuery(database, "SELECT * FROM tblPubHuntCurrent WHERE fnUserID = "+playerNames.get(hunter)+" AND fnPreyID = "+playerNames.get(huntee));
				if(results.next()) {
					delayHuntedTell(huntee);
					ResultSet result2 = m_botAction.SQLQuery(database, "SELECT * FROM tblPubHuntCurrent WHERE fnUserID = "+playerNames.get(huntee));
					result2.next();
                    m_botAction.SQLQueryAndClose(database, "UPDATE tblPubHuntCurrent SET fnPoints = fnPoints + 1 + "+result2.getInt("fnPoints")+", "
						+ "fnPreyID = "+result2.getInt("fnPreyID")+" WHERE fnUserID = "+playerNames.get(hunter));
                    m_botAction.SQLQueryAndClose(database, "DELETE FROM tblPubHuntCurrent WHERE fnUserID = "+playerNames.get(huntee));
                    m_botAction.SQLClose( result2 );
					ResultSet results3 = m_botAction.SQLQuery(database, "SELECT * FROM tblPubHuntCurrent");
					int playersLeft = 0;
					while(results3.next()) playersLeft++;
                                        m_botAction.SQLClose( results3 );
					if(playersLeft == 1) {
						m_botAction.ipcSendMessage("pubBots", "huntover", "PubHub", botName);
						m_botAction.sendPrivateMessage(hunter, "Congratulations, you have won this round of hunt.");
					} else {
						getPrey(hunter, true);
					}
				}
                                m_botAction.SQLClose( results );
			} catch(Exception e) {e.printStackTrace();}
		}
	}
	
	public void delayHuntedTell(String name) {
		final String playerHunted = name;
		TimerTask tellPlayer = new TimerTask() {
			public void run() {
				m_botAction.sendSmartPrivateMessage(playerHunted, "You have been hunted.");
			}
		};
		m_botAction.scheduleTask(tellPlayer, 30 * 1000);
	}
	
	public void handleEvent(PlayerEntered event) {
		String name = m_botAction.getPlayerName(event.getPlayerID());
		if(playerNames.containsKey(name.toLowerCase())) {
			getPrey(name,true);
		}
	}
	
	public void getPrey(String name, boolean entered) {
		try {
			ResultSet results2 = m_botAction.SQLQuery(database, 
				"SELECT fcUserName AS name FROM tblUser AS u, tblPubHuntCurrent AS phc "
				+ "WHERE phc.fnUserID = "+playerNames.get(name.toLowerCase())+" AND phc.fnPreyID = u.fnUserID");
			if(results2.next())
				m_botAction.sendPrivateMessage(name, "Your current prey is: "+results2.getString("name"));
			else if(!entered) {
				m_botAction.sendPrivateMessage(name, "You do not currently have a prey.");
			}
                        m_botAction.SQLClose( results2 );
		} catch(Exception e) {}
	}
	
	public void startHunt() {
		isRunning = true;
		try {
			ResultSet results = m_botAction.SQLQuery(database, "SELECT fcUserName AS name, u.fnUserID AS id FROM tblUser AS u, tblPubHuntCurrent AS phc WHERE phc.fnUserID = u.fnUserID");
			while(results.next()) {
				playerNames.put(results.getString("name").toLowerCase(),results.getInt("id"));
				if(m_botAction.getPlayer(results.getString("name")) != null) {
					ResultSet results2 = m_botAction.SQLQuery(database, 
						"SELECT fcUserName AS name FROM tblUser AS u, tblPubHuntCurrent AS phc "
						+ "WHERE phc.fnUserID = "+results.getInt("id")+" AND phc.fnPreyID = u.fnUserID");
					results2.next();
                                        m_botAction.SQLClose( results2 );
					m_botAction.sendPrivateMessage(results.getString("name"), "Your current prey is: "+results2.getString("name"));
				}
			}
                        m_botAction.SQLClose( results );
		} catch(Exception e) {}
	}
	
	public void endHunt() {
		m_botAction.cancelTask(activity);
		playerNames.clear();
		isRunning = false;
	}
	
	private int getDelay() {
		java.util.Date d = new java.util.Date();

		SimpleDateFormat formatter = new SimpleDateFormat("ss");
		int seconds = Integer.parseInt( formatter.format( d ) );

		int secondsTill = 60 - seconds;

		return secondsTill;
	}
}
