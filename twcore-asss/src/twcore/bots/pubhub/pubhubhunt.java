package twcore.bots.pubhub;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Random;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.ipc.IPCMessage;

public class pubhubhunt extends PubBotModule
{
	
    String database = "website";
    
	boolean isRunning = false;
	String botName;
	TimerTask checkActivity;
	public void initializeModule()
	{
		try {
			ResultSet results = m_botAction.SQLQuery(database, "SELECT fcVarValue AS val FROM tblSiteVar WHERE fcVarName = 'fbHuntRunning'");
			results.next();
			isRunning = new Boolean(results.getString("val").equals("true"));
            m_botAction.SQLClose( results );
		} catch(Exception e) {e.printStackTrace();}
		botName = m_botAction.getBotName();
	}

	public void requestEvents(EventRequester eventRequester)
	{
		eventRequester.request(EventRequester.MESSAGE);
	}

	public void handleChatMessage(String sender, String message)
	{
		if(message.equalsIgnoreCase("!starthunt")) {
			startHunt();
		}
	}

	public void handleEvent(Message event)
	{
		String sender = event.getMessager();
		String message = event.getMessage();
		int messageType = event.getMessageType();
		if(messageType == Message.CHAT_MESSAGE)
		{
			handleChatMessage(sender, message);
		}
	}

	public void handleIPC(String botSender, String recipient, String sender, String message)
	{
		String command = message.toLowerCase();

		try
		{
			if(command.equals("huntover")) {
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

		if(recipient == null || recipient.equals(botName))
			handleIPC(botSender, recipient, sender, message);
	}

	public void cancel()
	{
	}
	
	public void endHunt() {
		if(!isRunning) return;
		m_botAction.ipcSendMessage("pubBots", "endhunt", null, "PubHub");
		try {
		    m_botAction.SQLQueryAndClose(database, "UPDATE tblSiteVar SET fcVarValue = 'false' WHERE fcVarName = 'fbHuntRunning'");
			ResultSet results = m_botAction.SQLQuery(database, "SELECT * FROM tblPubHuntPlayer WHERE fnUserID = (SELECT fnUserID FROM tblPubHuntCurrent)");
			if(results == null || !results.next())
			    m_botAction.SQLQueryAndClose(database, "INSERT INTO tblPubHuntPlayer (fnUserID, fnPoints) "
					+ "(SELECT fnUserID, fnPoints FROM tblPubHuntCurrent)");
			else 
			    m_botAction.SQLQueryAndClose(database, "UPDATE tblPubHuntPlayer SET fnPoints = fnPoints + (SELECT fnPoints FROM tblPubHuntCurrent) "
					+ "WHERE fnUserID = (SELECT fnUserID FROM tblPubHuntCurrent)");
			m_botAction.SQLClose( results );
            m_botAction.SQLQueryAndClose(database, "TRUNCATE TABLE tblPubHuntCurrent");
		} catch(Exception e) {e.printStackTrace();}
		isRunning = false;
	}	
	
	public void startHunt() {
		if(isRunning) return;
		try {
			ArrayList<Integer> userids = new ArrayList<Integer>();
			int k = 0;
			ResultSet results = m_botAction.SQLQuery(database, "SELECT fnUserID FROM tblPubHuntCurrent");
			while(results.next()) {
				userids.add((Integer)results.getInt("fnUserID"));
			}
                        m_botAction.SQLClose( results );
			Random rand = new Random();
			ArrayList<Integer> huntOrder = new ArrayList<Integer>();
			while(userids.size() > 0) {
				huntOrder.add(userids.remove(rand.nextInt(userids.size())));
			}
			for(k = 0;k < huntOrder.size();k++) {
				int prey;
				if(k > 0) prey = huntOrder.get(k-1);
				else prey = huntOrder.get(huntOrder.size()-1);
				int hunter = huntOrder.get(k);
				m_botAction.SQLQueryAndClose(database, "UPDATE tblPubHuntCurrent SET fnPreyID = "+prey+" WHERE fnUserID = "+hunter);
			}
			m_botAction.SQLQueryAndClose(database, "UPDATE tblSiteVar SET fcVarValue = 'true' WHERE fcVarName = 'fbHuntRunning'");
		} catch(Exception e) {e.printStackTrace();}
		m_botAction.ipcSendMessage("pubBots", "starthunt", null, "PubHub");
		scheduleTasks();
		isRunning = true;
	}
	
	public void scheduleTasks() {
		checkActivity = new TimerTask() {
			public void run() {
				checkActivity();
			}
		};
		m_botAction.scheduleTaskAtFixedRate(checkActivity, getDelay()*1000, 60 * 1000);
	}
	
	public void checkActivity() {
		if(!isRunning) return;
		
		try {
			ResultSet results = m_botAction.SQLQuery(database, "SELECT MAX(fnActivity) AS max FROM tblPubHuntCurrent");
			results.next();
			int minAct = results.getInt("max") - 15;
            m_botAction.SQLClose( results );
			results = m_botAction.SQLQuery(database, "SELECT fnUserID, fnPreyID FROM tblPubHuntCurrent WHERE fnActivity < "+minAct);
			while(results.next()) {
				ResultSet result2 = m_botAction.SQLQuery(database, 
					  "SELECT phc.fnUserID AS uid, u.fcUserName AS hunter, u2.fcUserName AS newprey "
					+ "FROM tblUser AS u, tblPubHuntCurrent AS phc, tblUser AS u2 "
					+ "WHERE u.fnUserID = phc.fnUserID AND u2.fnUserID = "+results.getInt("fnPreyID") + " "
					+ "AND phc.fnPreyID = "+results.getInt("fnUserID"));
				m_botAction.SQLQuery(database, "DELETE FROM tblPubHuntCurrent WHERE fnUserID = "+results.getInt("fnUserID"));
				if(result2.next() && !result2.getString("hunter").equals(result2.getString("newprey"))) {
					m_botAction.SQLQuery(database, "UPDATE tblPubHuntCurrent SET fnPreyID = "+results.getInt("fnPreyID")+" "
					+ "WHERE fnUserID = "+result2.getInt("uid"));
					m_botAction.sendSmartPrivateMessage(result2.getString("hunter"), "Your new prey is "+result2.getString("newprey"));
				} else {
					m_botAction.sendSmartPrivateMessage(result2.getString("hunter"), "Congratulations, you have won this round of hunt.");
					endHunt();	
				}
			}
			m_botAction.SQLClose( results );
		} catch(Exception e) {e.printStackTrace();}
	}
	
	private int getDelay() {
		java.util.Date d = new java.util.Date();

		SimpleDateFormat formatter = new SimpleDateFormat("ss");
		int seconds = Integer.parseInt( formatter.format( d ) );

		int secondsTill = 70 - seconds;

		return secondsTill;
	}
}
