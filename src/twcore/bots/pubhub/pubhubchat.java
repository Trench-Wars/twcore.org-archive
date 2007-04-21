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
			pubbotchatIPC ipc = (pubbotchatIPC)event.getObject();
			String arena = Tools.addSlashesToString(ipc.getArena());
			String message = Tools.addSlashesToString(ipc.getMessage());
			String sender = (ipc.getMessageType() == Message.ARENA_MESSAGE) ? null : ipc.getSender();
			Vector<Integer> ids;
			
			// Check if the HashSet for this arena already exists, if not, create it
			if(arenaIDs.containsKey(arena)) {
				ids = arenaIDs.get(arena);
			} else {	// Create the HashSet and put it to the HashMap
				ids = new Vector<Integer>(maxSavedLines);
				arenaIDs.put(arena, ids);
			}
			
			// Save the message
			try {
			    m_botAction.SQLQueryAndClose( dbConn, "INSERT INTO tblChat (`fcArena`, `fnMessageType`, `fcPlayername`, `fcMessage`) VALUES ('"+arena+"', "+ipc.getMessageType()+", '"+sender+"', '"+message+"')" );
			} catch(SQLException sqle) {
				Tools.printStackTrace(sqle);
			}
			
			// Add the inserted ID to the map tracking the IDs
			try {
			    ResultSet rs = m_botAction.SQLQuery( dbConn, "SELECT LAST_INSERT_ID()" );
			    if(rs != null && rs.next()) {
			    	ids.add(0,rs.getInt(1));
			    	ids.setSize(maxSavedLines);
			    }
			} catch(SQLException sqle) {
				Tools.printStackTrace(sqle);
			}
			
			// Clean up
			if(ids.get(19) != null) {
				try {
					m_botAction.SQLQueryAndClose( dbConn, "DELETE FROM tblChat WHERE fcArena = '"+arena+"' AND fnChatID < "+ids.get(19));
				} catch (SQLException sqle) {
					Tools.printStackTrace(sqle);
				}
			}
		}
	}
	
	

}
