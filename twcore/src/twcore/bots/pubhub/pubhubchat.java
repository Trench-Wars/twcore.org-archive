package twcore.bots.pubhub;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Vector;

import twcore.bots.PubBotModule;
import twcore.bots.pubbot.pubbotchatIPC;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.Tools;

public class pubhubchat extends PubBotModule {

	private HashMap<String, Vector> arenaIDs = new HashMap<String, Vector>(10);
	
	private final String dbConn = "local";
	private final int maxSavedLines = 20;
	
	@Override
	public void cancel() {
	}

	@Override
	public void initializeModule() {
		
	}

	@Override
	public void requestEvents(EventRequester eventRequester) {
	}

	@Override
	public void handleEvent(InterProcessEvent event) {
		if(event.getObject() instanceof pubbotchatIPC) {
			m_botAction.sendTeamMessage("pubbotchatIPC");
			
			pubbotchatIPC ipc = (pubbotchatIPC)event.getObject();
			String arena = Tools.addSlashesToString(ipc.getArena());
			String message = Tools.addSlashesToString(ipc.getMessage());
			String sender = (ipc.getMessageType() == Message.ARENA_MESSAGE) ? null : ipc.getSender();
			Vector<Integer> ids;
			
			// Check if the HashSet for this arena already exists, if not, create it
			if(arenaIDs.containsKey(arena)) {
				m_botAction.sendTeamMessage("arena exists");
				ids = arenaIDs.get(arena);
			} else {	// Create the HashSet and put it to the HashMap
				ids = new Vector<Integer>(maxSavedLines);
				arenaIDs.put(arena, ids);
				m_botAction.sendTeamMessage("new arena");
			}
			
			// Save the message
			m_botAction.sendTeamMessage("save chat to db");
			try {
			    m_botAction.SQLQueryAndClose( dbConn, "INSERT INTO tblChat (`fcArena`, `fnMessageType`, `fcPlayername`, `fcMessage`) VALUES ('"+arena+"', "+ipc.getMessageType()+", '"+sender+"', '"+message+"')" );
			} catch(SQLException sqle) {
				Tools.printStackTrace(sqle);
			}
			
			m_botAction.sendTeamMessage("getting latest inserted id");
			// Add the inserted ID to the map tracking the IDs
			try {
			    ResultSet rs = m_botAction.SQLQuery( dbConn, "SELECT LAST_INSERT_ID()" );
			    if(rs != null && rs.next()) {
			    	ids.add(0,rs.getInt(1));
			    	ids.setSize(maxSavedLines);
			    }
			    m_botAction.SQLClose(rs);
			} catch(SQLException sqle) {
				Tools.printStackTrace(sqle);
			}
			
			// Clean up
			m_botAction.sendTeamMessage("cleanup necessary?");
			if(ids.get(19) != null) {
				m_botAction.sendTeamMessage("yes");
				try {
					m_botAction.SQLQueryAndClose( dbConn, "DELETE FROM tblChat WHERE fcArena = '"+arena+"' AND fnChatID < "+ids.get(19));
				} catch (SQLException sqle) {
					Tools.printStackTrace(sqle);
				}
			}
		}
	}
	
	

}
