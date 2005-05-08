/*
 * twdtbot.java
 *
 */

package twcore.bots.twdtbot;

import twcore.core.*;
import twcore.misc.database.*;
import java.util.*;
import java.sql.*;


public class twdtbot extends SubspaceBot {

    private BotSettings m_botSettings;
	private String m_conn = "local";
	private HashMap m_access;

    public twdtbot(BotAction botAction) {
        super(botAction);
        requestEvents();

		m_access = new HashMap();
        m_botSettings = m_botAction.getBotSettings();
    }


    void requestEvents() {

        EventRequester req = m_botAction.getEventRequester();
        req.request(EventRequester.MESSAGE);
        req.request(EventRequester.ARENA_JOINED);
        req.request(EventRequester.LOGGED_ON);
    }


    public void handleEvent(Message event) {

    	if(event.getMessageType() != Message.PRIVATE_MESSAGE)
    		return;

        String name = event.getMessager() != null ? event.getMessager() : m_botAction.getPlayerName(event.getPlayerID());
        String msg = event.getMessage().toLowerCase();

        if(name == null)
        	return;

        boolean isStaff = m_botAction.getOperatorList().isSmod(name)
        					|| m_access.containsKey(name.toLowerCase());

        if(msg.equals("!help")) {
        	doHelp(name, isStaff);
        }
        else if(msg.equals("!signup")) {
        	if(doSignup(name))
        		m_botAction.sendSmartPrivateMessage(name, "Signup succeeded.");
        	else
        		m_botAction.sendSmartPrivateMessage(name, "Signup failed.");
        }
        else if(msg.equals("!about")) {
        	doAbout(name);
        }
        else if(isStaff) {
        	if(msg.equals("!die")) {
            	m_botAction.sendPublicMessage(name + " commanded me to die. Disconnecting...");
            	try { Thread.sleep(50); } catch (Exception e) {};
            	m_botAction.die();
        	} else if(msg.startsWith("!go ")) {
        		m_botAction.changeArena(msg.substring(4));
        	} else if(msg.equals("!trimusers")) {
        		//doTrimUsers(name);
        	}
        }
    }


    boolean doSignup(String name) {
    	if(!m_botAction.SQLisOperational()) {
    		m_botAction.sendSmartPrivateMessage(name, "error: the database is not operational");
    		return false;
    	}

    	/* check that player is enabled in TWD */
    	DBPlayerData pdata = new DBPlayerData(m_botAction, m_conn, name);
    	if(!pdata.playerLoaded()) {
    		m_botAction.sendSmartPrivateMessage(name, "Please ?go twd and register for TWD before signing up for TWDT.");
    		return false;
    	}
    	if(!pdata.isEnabled()) {
    		m_botAction.sendSmartPrivateMessage(name, "Your TWD account is currently disabled.");
    		return false;
    	}


		try {
			ResultSet r = m_botAction.SQLQuery(m_conn, "SELECT fnTWDUserID FROM tblTWDTUser WHERE fcUserName = '" + Tools.addSlashesToString(name) + "'");
			if(r.next()) {
				m_botAction.sendSmartPrivateMessage(name, "You have already signed up.");
				return false;
			}
			if(r != null)
				r.close();

	    	r = m_botAction.SQLQuery(m_conn,
	    		"INSERT INTO tblTWDTUser (fnTWDUserID, fcUserName, ftCreated) VALUES ("
	    		+ pdata.getUserID() + ", '" + Tools.addSlashesToString(name) + "', NOW())");
 m_botAction.sendSmartPrivateMessage(name, pdata.getUserID() + name);
	    	if(r != null)
	    		r.close();
	    	return true;
		} catch(SQLException e) {
			m_botAction.sendSmartPrivateMessage(name, "Database error.");
			return false;
		}
    }


    void doHelp(String name, boolean isStaff) {
		String[] msg = {
			"----- TWDTBot -----------------",
			"!signup       -sign up for TWDT",
		};
		String[] msg2 = {
			"-------------------------------",
			"!die",
			"!go <arena>",
		};
		m_botAction.smartPrivateMessageSpam(name, msg);
		if(isStaff)
			m_botAction.smartPrivateMessageSpam(name, msg2);
    }


    void doAbout(String name) {
    	String[] msg = {
    		"TWDTBot v0.1 by Flibb1e"
    	};
    	m_botAction.smartPrivateMessageSpam(name, msg);
    }


    public void handleEvent(LoggedOn event) {
        m_botAction.joinArena(m_botSettings.getString("arena"));

        String accessList = m_botSettings.getString("AccessList");
        //Parse accesslist
        String pieces[] = accessList.split(",");
        for(int i = 0; i < pieces.length; i++)
            m_access.put( pieces[i].toLowerCase(), pieces[i] );
    }


    public void handleEvent(ArenaJoined event) {
    }

}
