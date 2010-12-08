package twcore.bots.twdop;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.Module;
import twcore.core.EventRequester;
import twcore.core.command.CommandInterpreter;
import twcore.core.events.Message;
import twcore.core.events.SQLResultEvent;

/**
 * TWDOpStats bot is like Robohelp for "on it" gathering but only 
 * for TWD Ops and TWD calls.
 * @author Maverick / MMaverick
 */
public class twdopstats extends Module {

    private EventRequester events; 
    private final String mySQLHost = "website";
    
    private Vector<EventData> callList = new Vector<EventData>();
    
    private CommandInterpreter m_commandInterpreter;
    
    public static final int CALL_EXPIRATION_TIME = 90000;
    
    private HashMap<String,String> twdops = new HashMap<String,String>();
    
    private updateTWDOpsTask updateOpsList;
    private String updateTWDOpDelay = String.valueOf(24 * 60 * 60 * 1000); // once every day
    
    
    /**
     * Registers the command to the CommandInterpreter
     */
    private void registerCommands(){
        m_commandInterpreter = new CommandInterpreter( m_botAction );
        m_commandInterpreter.registerCommand( "!update", Message.CHAT_MESSAGE, this, "handleUpdateCommand" );
    }




    
    /**
     * @see twcore.core.SubspaceBot.handleDisconnect() 
     */

    
    /**
     * Initiates the update of the TWD Operator list by starting
     * a background query
     */
    private void loadTWDOps() {
        
        String query = "SELECT tblUser.fcUsername FROM `tblUserRank`, `tblUser` WHERE `fnRankID` = '14' AND tblUser.fnUserID = tblUserRank.fnUserID";
        m_botAction.SQLBackgroundQuery(mySQLHost, "TWDOpsUpdate", query);
    }
    
    /**
     * Handle the background sql process once it's finished
     * and update the TWD Operator list
     * @see twcore.core.SubspaceBot.handleEvent(SQLResultEvent event)
     */
    public void handleEvent( SQLResultEvent event) {
        if(event.getIdentifier().equals("TWDOpsUpdate")) {
            ResultSet resultSet = event.getResultSet();
            
            if(resultSet == null) {
                throw new RuntimeException("ERROR: Null resultSet returned; the database connection might be down");
            }
            
            // Clear current list of TWD Operators
            twdops.clear();
            
            // Iterate over all the TWD Operators from the database and add them to the hashmap
            try {           
                while(resultSet.next()) {
                    String name = resultSet.getString("fcUsername");
                    twdops.put( name.toLowerCase(), name );
                }
            } catch(SQLException sqle) {
                throw new RuntimeException("SQL Error: " + sqle.getMessage(), sqle);
            }
            m_botAction.SQLClose(resultSet);
        }
    }
    
    /**
     * Handles any messages send to the bot. 
     * The bot triggers on the ?help alerts and "on it" in chat
     * 
     * @see twcore.core.SubspaceBot.handleEvent( Message event )
     */
    public void handleEvent( Message event ){

        m_commandInterpreter.handleEvent( event );

        if( event.getMessageType() == Message.ALERT_MESSAGE ){
            String command = event.getAlertCommandType().toLowerCase();
            if( command.equals( "help" ) || command.equals("cheater")){
                if( event.getMessager().compareTo( m_botAction.getBotName() ) != 0 &&
                    (
                        event.getMessage().toLowerCase().contains("twd") || 
                        event.getMessage().toLowerCase().contains("twbd") ||
                        event.getMessage().toLowerCase().contains("twdd") ||
                        event.getMessage().toLowerCase().contains("twsd") ||
                        event.getMessage().toLowerCase().contains("twjd")
                    )) {
                    m_botAction.sendChatMessage("...");
                    callList.addElement( new EventData( new Date().getTime() ) );
                    // add:  twbd, twdd, twsd and twjd
                }
            }
        }
        else if (event.getMessageType() == Message.CHAT_MESSAGE) {
            String message = event.getMessage().toLowerCase();
            if (message.startsWith("on it"))
                handleOnIt(event.getMessager(), event.getMessage());
        }
    }
    


    


    
    /**
     * Initiates TWD Operator list update on the !update command.
     * @param playerName the player who did !help
     * @param message the message of the player (will always start with !update)
     */
    public void handleUpdateCommand( String name, String message) {
        if( m_botAction.getOperatorList().isSmod(name)) {
            loadTWDOps();
        }
    }
    


    
    
    
    /**
     * Records the staffer's claim for the call once he has said "on it" on cha.
     * The call must be twd related ("twd" word in the call) and the staffer must be a TWD Operator.
     * @param name Name of person saying on it
     * @param message Message containing on it
     */
    public void handleOnIt( String name, String message ) {
        boolean record = false;
        Date now = new Date();
        
        if(callList.size()==0) {
            return;
        }
        
        if(twdops.containsKey(name.toLowerCase()) == false) {
            return;
        }
        
        // Clear the queue of expired calls, get the first non-expired call but leave other non-expired calls
        Iterator<EventData> iter = callList.iterator();
        while(iter.hasNext()) {
            EventData e = iter.next();
            
            if( record == false && now.getTime() < e.getTime() + CALL_EXPIRATION_TIME ) {
                // This is a non-expired call and no call has been counted yet. 
                record = true;
                iter.remove();
            } else if(now.getTime() >= e.getTime() + CALL_EXPIRATION_TIME) {
                // This is an expired call
                iter.remove();
            }
        }
        
        if(record) 
            updateStatRecordsONIT( name );
    }
    
    /**
     * Records the call to the database
     * @param name the staffer who claimed the call
     */
    private void updateStatRecordsONIT( String name ) {
        try {
            Calendar thisTime = Calendar.getInstance();
            Date day = thisTime.getTime();
            String time = new SimpleDateFormat("yyyy-MM").format( day ) + "-01";
            
            ResultSet result = m_botAction.SQLQuery(mySQLHost, "SELECT * FROM tblTWDCall WHERE fcUserName = '"+name+"' AND fdDate = '"+time+"' LIMIT 0,1" );
            
            if(result.next()) {
                m_botAction.SQLQueryAndClose( mySQLHost, "UPDATE tblTWDCall SET fnCount = fnCount + 1 WHERE fcUserName = '"+name+"' AND fdDate = '"+time+"'" );
            } else {
                m_botAction.SQLQueryAndClose( mySQLHost, "INSERT INTO tblTWDCall (`fcUserName`, `fnCount`, `fdDate`) VALUES ('"+name+"', '1', '"+time+"')" );
            }
            m_botAction.SQLClose(result);
            
        } catch ( SQLException e ) {
            m_botAction.sendChatMessage(2,"EXCEPTION: Unable to update the tblTWDCall table: " + e.getMessage() );
        }
    }
    
    
    
    /**
     * Returns the number of rows in the ResultSet
     * @param rs : ResultSet
     * @return count
     */
    public static int getRowCount(ResultSet rs) throws SQLException{
        int numResults = 0;
        
        rs.last();
        numResults = rs.getRow();
        rs.beforeFirst();

        return numResults;
    }
    
    
    class EventData {

        String  arena;
        long    time;
        int     dups;

        public EventData( String a ) {
            arena = a;
            dups  = 1;
        }

        public EventData( long t ) {
            time = t;
        }

        public EventData( String a, long t ) {
            arena = a;
            time = t;
        }

        public void inc() {
            dups++;
        }

        public String getArena() { return arena; }
        public long getTime() { return time; }
        public int getDups() { return dups; }
    }
    
    /**
     * This TimerTask initiates the TWD Operator list update 
     */
    private class updateTWDOpsTask extends TimerTask {
        twdopstats botInstance;
        
        public updateTWDOpsTask(twdopstats botInstance) {
            this.botInstance = botInstance;
        }
        
        public void run() {
            botInstance.loadTWDOps();
        }
    }

    @Override
    public void cancel() {
        m_botAction.cancelTask(this.updateOpsList);
        updateOpsList = null;
        
    }

    @Override
    public void initializeModule() {
        
        // Load the TWD Operators list
        loadTWDOps();
        
        // Start the task to update the TWD Operator list
        updateOpsList = new updateTWDOpsTask(this);
        m_botAction.scheduleTaskAtFixedRate(updateOpsList, Long.valueOf(this.updateTWDOpDelay).longValue(), Long.valueOf(this.updateTWDOpDelay).longValue());
        registerCommands();
    }
    
        
    

    @Override
    public void requestEvents(EventRequester eventRequester) {
        events = m_botAction.getEventRequester();
        events.request(EventRequester.MESSAGE);
        
        
        
    }

}
