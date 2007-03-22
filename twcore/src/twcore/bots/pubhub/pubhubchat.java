package twcore.bots.pubhub;

import java.sql.ResultSet;
import java.sql.SQLException;

import twcore.bots.PubBotModule;
import twcore.bots.pubbot.pubbotchatIPC;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.stats.DBPlayerData;
import twcore.core.util.Tools;

public class pubhubchat extends PubBotModule {

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
			
			DBPlayerData playerdata = new DBPlayerData(m_botAction, dbConn, ipc.getSender());
			
			if(playerdata.checkPlayerExists() || ipc.getMessageType() == Message.ARENA_MESSAGE) {
				String arena = Tools.addSlashesToString(ipc.getArena());
				String message = Tools.addSlashesToString(ipc.getMessage());
				String sender = (ipc.getMessageType() == Message.ARENA_MESSAGE) ? null : String.valueOf(playerdata.getUserID());
				
				// Save the message
				try {
					m_botAction.SQLQuery( dbConn, "INSERT INTO tblChat (`fcArena`, `fnMessageType`, `fnUserID`, `fcMessage`) VALUES ('"+arena+"', "+ipc.getMessageType()+", "+sender+", '"+message+"')" );
				} catch(SQLException sqle) {
					Tools.printStackTrace(sqle);
				}
				
				// Clean up
				try {
					ResultSet resultSet = m_botAction.SQLQuery( dbConn, "SELECT fnChatId FROM tblChat WHERE fcArena = '"+arena+"'");
					if(resultSet != null) {
						resultSet.afterLast();					// Go to the last row
						resultSet.relative(-maxSavedLines);		// Move up a number of rows to save
						if(resultSet.isBeforeFirst() == false) {
							int fnChatID = resultSet.getInt("fnChatID"); // Get the fnChatID of this row
							m_botAction.SQLQuery( dbConn, "DELETE FROM tblChat WHERE fcArena = '"+arena+"' AND fnChatID < "+fnChatID);
						}
					}
				} catch (SQLException sqle) {
					Tools.printStackTrace(sqle);
				}
				
			}
			
		}
	}
	
	

}
