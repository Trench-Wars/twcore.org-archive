/*
  @author  mr. spam
  last updated: 07/26/2002
 
  Changes:
    Added "Comment count" to /!list* commands
    Cleaned up staffbot.cfg.  Removed unused settings and added LogArchivedEnabled, ArchivePath, & AutoLogTime
    Misc code changes/tweaks.. cleanup
    Added /!listrating (sorts ascending by average rating)
    Added automatic server log archiving. See .cfg
    Added /!getlog command, unlisted in help as not all smods will have access to logs
 */

package twcore.bots.staffbot;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.zip.*;
import java.sql.*;
import twcore.core.*;

public class staffbot extends SubspaceBot {
    OperatorList        m_opList;
    HashMap             m_playerList = new HashMap();
    BotAction           m_botAction;
    BotSettings         m_botSettings;
    boolean             m_logActive = false;
    boolean             m_logArchivingEnabled;
    String              m_logNotifyPlayer;
    final String        m_LOGFILENAME = "subgame.log";
    java.util.Date      m_logTimeStamp;
    
    /* Initialization code */
    public staffbot( BotAction botAction ) {
        super( botAction );
        m_botAction = botAction;
        m_botSettings = m_botAction.getBotSettings();
        if ( m_botSettings.getString( "LogArchivingEnabled" ).toLowerCase().equals("true") ){
            m_logArchivingEnabled = true;
        } else {
            m_logArchivingEnabled = false;
        }
        EventRequester req = botAction.getEventRequester();
        req.request( EventRequester.FILE_ARRIVED );
        req.request( EventRequester.MESSAGE );
    }

    /* Handle Events */
    public void handleEvent( LoggedOn event ){
        readObjects();
        m_botAction.joinArena( m_botSettings.getString( "InitialArena" ) );
        m_opList = m_botAction.getOperatorList();
        m_botAction.sendUnfilteredPublicMessage( "?chat=" + m_botAction.getGeneralSettings().getString( "Chat Name" ) );
        m_botAction.sendUnfilteredPublicMessage( "?obscene" );

        if( m_logArchivingEnabled ){
            TimerTask getLogTask = new TimerTask(){
                public void run(){
                    m_botAction.sendChatMessage( "Automatically processing server log." );
                    getLog();
                }
            };
            
            try{
                java.util.Date dateNow = new java.util.Date();
                
                String strDate = new SimpleDateFormat("MM/dd/yyyy").format( dateNow );
                strDate += " " + m_botSettings.getString( "AutoLogTime" );
                java.util.Date timeToActivate = new SimpleDateFormat("MM/dd/yyyy k:m").parse( strDate );
                
                //If scheduled time for today is passed, schedule for tommorow
                if( dateNow.after( timeToActivate ) ){
                    Calendar calCompare = Calendar.getInstance();
                    long millis;
                    
                    calCompare.setTime( timeToActivate );
                    millis = calCompare.getTimeInMillis();
                    millis += 86400000; //add a day
                    
                    calCompare.setTimeInMillis( millis );
                    timeToActivate = calCompare.getTime();
                }
                
                m_botAction.scheduleTaskAtFixedRate( getLogTask, timeToActivate.getTime(), 86400000 );
                Tools.printLog( m_botAction.getBotName() + "> Autolog at: " + timeToActivate );
            } catch( Exception e ){
                Tools.printStackTrace( e );
            }
        }

        // Check the logs for *warnings.
        TimerTask getLog = new TimerTask() {
            public void run() {
                m_botAction.sendUnfilteredPublicMessage( "*log" );
            }
        };
        
        m_botAction.scheduleTaskAtFixedRate( getLog, 0, 60000 );
    }
    
    public void handleEvent( Message event ){
        String name = m_botAction.getPlayerName( event.getPlayerID() ); ;
        String message = event.getMessage();
        boolean remote = false;
        
        if( event.getMessageType() == Message.ARENA_MESSAGE ){
            if( message.toLowerCase().indexOf( "*warn" ) > 0 ){
                String          temp;
                String          staffMember;
                String          warnedPlayer;
                LinkedList      warnings;
                
                temp = message.substring( message.indexOf( ") to " ) + 5 );
                warnedPlayer = temp.substring( 0, temp.indexOf( ":" ) ).toLowerCase();
                temp = message.substring( message.indexOf( "Ext: " ) + 5 );
                staffMember = temp.substring( 0, temp.indexOf( " (" ) ).toLowerCase().trim();

                String[] paramNames = { "name", "warning", "staffmember", "timeofwarning" };
                String date = new java.sql.Date( System.currentTimeMillis() ).toString();
                String[] data = { warnedPlayer, message, staffMember, date };
        
                m_botAction.SQLInsertInto( "local", "tblWarnings", paramNames, data );
            }

            return;
        }

        if( !message.startsWith("!") ) return;
        
        if( event.getMessageType() == Message.PRIVATE_MESSAGE ){
            remote = false;
        } else if( event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ) {
            name = event.getMessager();
            remote = true;
        } else {
            return;
        }
        
        if( m_opList.isER( name ) ){
            if( message.toLowerCase().startsWith( "!warning " ) ){
                queryWarnings( name, message.substring( 9 ) );
                return;
            }
        }
        
        if( m_opList.isER( name ) ){
            if( message.toLowerCase().startsWith( "!warnings " ) ){
                queryWarnings( name, message.substring( 10 ) );
                return;
            }
        }

        if( m_opList.isSmod( name ) ){
            if( message.startsWith( "!altnick " )){
                queryAltNick( name, message.substring( 9 ));
            } else if( message.startsWith( "!squaddies " )){
                querySquaddies( name, message.substring( 11 ));
            } else if( message.startsWith( "!dblsquad " )){
                queryDblSquad( name, message.substring( 10 ));
            } else if( message.toLowerCase().startsWith( "!warningsfrom " )){
                queryWarningsFrom( name, message.substring( 14 ) );
/*            } else if( message.toLowerCase().startsWith( "!messagestaff " )){
                handleMessageStaff( OperatorList.ZH_LEVEL, OperatorList.MODERATOR_LEVEL, name, message.substring( 14 ) );
            } else if( message.toLowerCase().startsWith( "!messageall " )){
                handleMessageStaff( OperatorList.ZH_LEVEL, OperatorList.OWNER_LEVEL, name, message.substring( 12 ) );
            } else if( message.toLowerCase().startsWith( "!messagezh " )){
                handleMessageStaff( OperatorList.ZH_LEVEL, OperatorList.ZH_LEVEL, name, message.substring( 11 ) );
            } else if( message.toLowerCase().startsWith( "!messageer " )){
                handleMessageStaff( OperatorList.ER_LEVEL, OperatorList.ER_LEVEL, name, message.substring( 11 ) );*/
            }
            
            handleCommand( name, message, remote );
        }
    }
    
    public void queryWarnings( String name, String message ){
        String      query = "select * from tblWarnings where name = \"" + message.toLowerCase() + "\"";
        
        try {
            ResultSet set = m_botAction.SQLQuery( "local", query );
            
            m_botAction.sendRemotePrivateMessage( name, "Warnings for " + message + ":" );
            while( set.next() ){
                m_botAction.sendRemotePrivateMessage( name, set.getString( "warning" ) );
            }
            m_botAction.sendRemotePrivateMessage( name, "End of list." );
        } catch( SQLException e ){
            Tools.printStackTrace( e );
        }
    }

    public void queryWarningsFrom( String name, String message ){
        String      query = "select * from tblWarnings where staffmember = \"" + message.toLowerCase() + "\"";
        
        try {
            ResultSet set = m_botAction.SQLQuery( "local", query );
            
            m_botAction.sendRemotePrivateMessage( name, "Warnings given by " + message + ":" );
            while( set.next() ){
                m_botAction.sendRemotePrivateMessage( name, set.getString( "warning" ) );
            }
            m_botAction.sendRemotePrivateMessage( name, "End of list." );
        } catch( SQLException e ){
            Tools.printStackTrace( e );
        }
    }
    
    public void queryAltNick( String name, String message ){
    	m_botAction.sendSmartPrivateMessage( name, "Please hold as I look for matches." );
    	HashMap altNick = new HashMap();
        //look up the machineid for the nick
        String query = "select machineid, ip from Arrogant where name = \"" + message + "\"";
        try{
            ResultSet set = m_botAction.SQLQuery( "local", query );
            while( set.next() ){
                String macid = set.getString( "MachineID" );
                String ip = set.getString( "IP" );
                String newquery = "select distinct Name, ip, machineid from Arrogant where IP = \"" 
                + ip + "\" order by Name";
                ResultSet set2 = m_botAction.SQLQuery( "local", newquery );
                //m_botAction.sendSmartPrivateMessage( name, message 
                //+ " has MachineID: " + macid + " and IP: " + ip );
                while( set2.next() ){     
                    String n2 = set2.getString( "Name" );
                    String machineid = set2.getString( "MachineID" );
                    String ip2 = set2.getString( "IP" );
                    String response = n2 + " matches: ";
                    if( !altNick.containsKey( n2 ) )
						altNick.put( n2, new AltNick( n2 ) );
					AltNick alt = (AltNick)altNick.get( n2 );
                    if( macid.equals( machineid ))
                        alt.setMIDMatch();//response += "MachineID ";
                    if( ip2.equals( ip ))
                        alt.setIPMatch();//response += "IP ";
                    //m_botAction.sendSmartPrivateMessage( name, response );
                }
            }
            
            int ct = 0;
            Set set3 = altNick.keySet();
			Iterator it = set3.iterator();
			while (it.hasNext()) {
				String curName = (String) it.next();
				AltNick alt = (AltNick)altNick.get( curName );
				String output = formatString( curName, 20 );
				if( alt.hasIPMatch() )
					output += "  (IP Match)";
				if( alt.hasMIDMatch() )
					output += "  (MachineID Match)";
				m_botAction.sendSmartPrivateMessage( name, output );
				ct++;
			}
			altNick.clear();
			m_botAction.sendSmartPrivateMessage( name, "Matches Displayed: " + ct );
            
        } catch( SQLException e ){
            Tools.printStackTrace( e );
        }
       
    }
    
    public String formatString(String fragment, int length) {
		String line;
		if(fragment.length() > length)
			fragment = fragment.substring(0,length-1);
		else {
			for(int i=fragment.length();i<length;i++)
				fragment = fragment + " ";
		}
		return fragment;
	}
    
    class AltNick {
    	String name;
    	boolean ip = false, mid = false;
    	public AltNick( String n ) {
    		name = n;
    	}
    	public void setMIDMatch() { mid = true; }
    	public void setIPMatch() { ip = true; }
    	public boolean hasMIDMatch() { return mid; }
    	public boolean hasIPMatch() { return ip; }
    }
    
    public void queryDblSquad( String name, String message ){
        //look up the machineid for the nick
        String query = "select machineid, ip from Arrogant where name = \"" + message + "\"";
        try{
            ResultSet set = m_botAction.SQLQuery( "local", query );
            while( set.next() ){
                String macid = set.getString( "MachineID" );
                String ip = set.getString( "IP" );
                String newquery = "select distinctrow name, squad from Arrogant where MachineID=\""
                + macid + "\" OR IP = \"" + ip + "\" order by Name";
                ResultSet set2 = m_botAction.SQLQuery( "local", newquery );
                while( set2.next() ){
                    String sqd = set2.getString( "Squad" );
                    if( !sqd.trim().equals( "" )){
                        m_botAction.sendSmartPrivateMessage( name, set2.getString( "Name" )
                        + " is in " + sqd );
                    }
                }
            }
            
        } catch( SQLException e ){
            Tools.printStackTrace( e );
        }
    }
    public void querySquaddies( String name, String message ){
        //look up the machineid for the nick
        String query = "select squad from Arrogant where name = \"" + message + "\"";
        try{
            ResultSet set = m_botAction.SQLQuery( "local", query );
            while( set.next() ){
                String squad = set.getString( "Squad" );
                if( squad.trim().equals( "" )) return;
                m_botAction.sendSmartPrivateMessage( name, "Members of squad " + squad);
                String newquery = "select distinct Name from Arrogant where Squad=\""
                + squad + "\" order by Name";
                ResultSet set2 = m_botAction.SQLQuery( "local", newquery );
                while( set2.next() ){
                    m_botAction.sendSmartPrivateMessage( name, set2.getString( "Name" ));
                }
            }
            
        } catch( SQLException e ){
            Tools.printStackTrace( e );
        }
    }
    
    public void handleEvent( FileArrived event ){
        if ( event.getFileName().equals(m_LOGFILENAME) ){
            logArrived();
        }
    }
   /*     
    public void handleMessageStaff( int minAccessLevel, int maxAccessLevel, String name, String message ){
        int         level;
        String      recipient;
        Map         staffList;
        Iterator    staffIterator;
        
        staffList = m_opList.getList();
        staffIterator = staffList.keySet().iterator();
        
        while( staffIterator.hasNext() ){
            recipient = ((String)staffIterator.next()).trim();
            level = m_opList.getAccessLevel( recipient );
            
            if( level >= minAccessLevel && level <= maxAccessLevel ){
                m_botAction.sendRemotePrivateMessage( "Sphonk", "...?message " + recipient + ":Message from " + name + ": " + message );
            
                m_botAction.sendUnfilteredPublicMessage( "?message " + recipient + ":Message from " + name + ": " + message );
            }
        }
    }*/
        
    public void handleCommand( String name, String message, boolean remote){
        if( message.toLowerCase().equals("!help") ){
            handleHelp( name, remote );
        } else if( message.toLowerCase().startsWith("!add ") ){
            addPlayer( name, message.substring(5), remote );
        } else if( message.toLowerCase().equals("!list") ){
            sendListAsc( name, 10, SORT_DATE_ADDED, remote );
        } else if( message.toLowerCase().startsWith("!list ") ){
            sendListAsc( name, getInteger(message.substring(6), 10), SORT_DATE_ADDED, remote );
        } else if( message.toLowerCase().equals("!listall") ){
            sendListDesc( name, m_playerList.size(), SORT_NAME, remote );
        } else if( message.toLowerCase().equals("!listrating") ){
            sendListDesc( name, 10, SORT_RATING, remote );
        } else if( message.toLowerCase().startsWith("!listrating ") ){
            sendListDesc( name, getInteger(message.substring(12), 10), SORT_RATING, remote );
        } else if( message.toLowerCase().startsWith("!remove ") ){
            remPlayer( name, message.substring(8).trim(), remote );
        } else if( message.toLowerCase().startsWith("!comment ") ){
            String[] args;
            
            args = message.substring(9).split(":", 3);
            if ( args.length == 3 ){
                addComment( args[0], name, args[2], getInteger(args[1], 0), remote );
            } else {
                sendPM( name, "Incorrect parameters. Use !comment <player>:<rating>:<comment>", remote);
            }
        } else if( message.toLowerCase().startsWith("!listplayer ") ){
            showComments( name, message.substring(12), remote );
        } else if( message.toLowerCase().equals("!getlog") ){
            getLog( name, remote );
        }
    }
    
    public void handleHelp( String name, boolean remote ){
        final String[] smodHelpText = {
            "Smod+ commands:",
            "!add <player>          - Adds a player to the list",
            "!remove <player>       - Removes a player from the list",
            "!comment <player>:<rating>:<comment>  - Adds a comment and rating(0-5) for specified player",
            "!list <num>            - Lists <num>(optional) of players in chronological order",
            "!listrating <num>      - Lists <num>(optional) of players descending by average rating",
            "!listall               - Displays all players in the list in alphabetical order",
            "!listplayer <player>   - Displays that player's details along with all comments",
            "Player Database Commands",
            "!squaddies <player>    - Displays a list of all the players on a player's squad",
            "!dblsquad <player>     - Displays a list of all the name/squad combinations this player might own",
            "!altnick <player>      - Displays a list of all the player's alt nicks.",
            "!warningsfrom <player> - Displays a list of recent warns given to a player."
        };
        
        final String[] helpText = {
            "Moderator and ER commands:",
            "!warning <player>      - Displays a list of recent warns given to a player."
        };

        if( m_opList.isER( name ) ){
            m_botAction.remotePrivateMessageSpam( name, helpText );
        }

        if( m_opList.isSmod( name ) ){
            m_botAction.remotePrivateMessageSpam( name, smodHelpText );
        }
    }
    
    /* Potential Staff List Code */
    public void addPlayer( String staffName, String playerName, boolean remote ){
        playerName = playerName.trim();
        
        if ( !m_playerList.containsKey( playerName.toLowerCase() ) ){
            m_playerList.put( playerName.toLowerCase(), new potentialStaffer( playerName ) );
            writeObjects();
            sendPM( staffName, "Player added: " + playerName, remote );
        } else {
            sendPM( staffName, "Player already exists: " + playerName, remote );
        }
    }
    
    public void remPlayer( String staffName, String playerName, boolean remote ){
        potentialStaffer axed;
        boolean found = false;
        
        axed = (potentialStaffer)m_playerList.get( playerName.toLowerCase().trim() );
        
        if ( axed != null ){
            m_playerList.remove( playerName.toLowerCase().trim() );
            writeObjects();
            sendPM( staffName, axed.getName() + " has been removed from the list.", remote );
        } else {
            sendPM( staffName, "Player could not be found", remote );
        }
    }
    
    public void sendRecord( String staffName, boolean remote, String dateAdded, int numComments, double aveRating, String playerName ){
        sendPM( staffName, "Added: " + dateAdded + "  Comments: " + numComments + "  Ave Rating: " + aveRating + "  Name: " + playerName, remote );
    }
    
    public void sendListAsc( String name, int count, Comparator sort, boolean remote ){
        LinkedList sortedList = new LinkedList();
        LinkedList buffer = new LinkedList();
        potentialStaffer listItem;
        
        sortedList.addAll( m_playerList.values() );
        Collections.sort( sortedList, sort );
        
        if( count > sortedList.size() ){ count = sortedList.size() ; }
        
        Iterator i = sortedList.iterator();
        
        for( int x = 0 ; x < count ; x++ ){
            buffer.addFirst( i.next() );
        }
        
        for( i = buffer.iterator(); i.hasNext(); ){
            listItem = (potentialStaffer)i.next();
            sendRecord( name, remote, listItem.getDate(), listItem.getCommentCount(), listItem.getAveRating(), listItem.getName() );
        }
    }
    
    public void sendListDesc( String name, int count, Comparator sort, boolean remote ){
        LinkedList sortedList = new LinkedList();
        potentialStaffer listItem;
        
        sortedList.addAll( m_playerList.values() );
        Collections.sort( sortedList, sort );
        
        if( count > sortedList.size() ){ count = sortedList.size() ; }
        
        Iterator i = sortedList.iterator();
        
        for( int x = 0 ; x < count ; x++ ){
            listItem = (potentialStaffer)i.next();
            sendRecord( name, remote, listItem.getDate(), listItem.getCommentCount(), listItem.getAveRating(), listItem.getName() );
        }
    }
    
    public void listPlayer( String staffName, String playerName, boolean remote ){
        potentialStaffer player = (potentialStaffer)m_playerList.get( playerName.toLowerCase().trim() );
        
        if (player != null){
            sendRecord( staffName, remote, player.getDate(), player.getCommentCount(), player.getAveRating(), player.getName() );
        } else {
            sendPM( staffName, playerName + " not found", remote );
        }
    }
    
    public void addComment( String playerName, String staffName, String comment, int rating, boolean remote ){
        potentialStaffer player;
        
        player = (potentialStaffer)m_playerList.get( playerName.toLowerCase().trim() );
        
        if ( player != null ){
            if ( comment.length() > 212 ){
                comment = comment.substring(0, 212);
            }
            
            if ( rating >= 0 && rating <= 5 ){
                player.addComment( staffName, comment, rating );
                writeObjects();
                sendPM( staffName, "Comment added", remote );
            } else {
                sendPM( staffName, "Your rating is invalid.  Please choose from 0-5.", remote );
            }
        } else {
            sendPM( staffName, "Player could not be found", remote );
        }
    }
    
    public void showComments( String staffName, String playerName, boolean remote ){
        potentialStaffer player;
        
        player = (potentialStaffer)m_playerList.get( playerName.toLowerCase().trim() );
        
        if ( player != null ){
            HashMap comments = player.getComments();
            staffComment comment;
            
            listPlayer( staffName, playerName, remote );
            
            for ( Iterator i = comments.values().iterator(); i.hasNext(); ){
                comment = (staffComment)i.next();
                sendPM( staffName, "- Rating: " + comment.getRating() + "  By: " + comment.getName() + "  Comment: " + comment.getComment(), remote );
            }
        } else {
            sendPM( staffName, playerName + " not found", remote );
        }
    }
    
    /* Log Archiving Code */
    public void getLog(){
        if(!m_logArchivingEnabled)return;
        if(m_logActive)return;
        
        m_logActive = true;
        
        m_logTimeStamp = new java.util.Date();
        m_botAction.sendUnfilteredPublicMessage( "*getfile " + m_LOGFILENAME );
    }
    
    public void getLog( String name, boolean remote){
        if(!m_logArchivingEnabled){
            sendPM( name, "Sorry, all log archiving functions for this bot have been disabled.", remote );
            return;
        }
        
        if(!m_logActive){
            Tools.printLog( m_botAction.getBotName() + "> Processing server log at " + name + "'s request." );
            m_botAction.sendChatMessage( "Processing server log at " + name + "'s request." );
            sendPM( name, "Downloading server log. You will be notified when completed.", remote );
            
            m_logNotifyPlayer = name;
            getLog();
        } else {
            sendPM( name, "Already processing a log. Please try again when finished.", remote );
        }
    }
    
    public void logArrived(){
        String archivePath = m_botSettings.getString( "ArchivePath" );
        compressToZip( m_LOGFILENAME, archivePath + "TWLog " + new SimpleDateFormat("MMM-dd-yyyy(HH-mm-ss z)").format(m_logTimeStamp) + ".zip" );
        sendBlankLog( m_LOGFILENAME );
        
        if( m_logNotifyPlayer != null ){
            m_botAction.sendRemotePrivateMessage( m_logNotifyPlayer, "Server log successfully downloaded and archived." );
            m_logNotifyPlayer = null;
        }
        
        m_botAction.sendChatMessage( "Server log successfully downloaded and archived." );
        m_logActive = false;
    }
    
    public void sendBlankLog( String fileName ){
        try {
            FileWriter fileOut = new FileWriter( fileName );
            fileOut.write( new SimpleDateFormat("EEE MMM dd KK:mm:ss:  ").format(new java.util.Date()) + "Log cleared by " + m_botAction.getBotName() + "\n" );
            fileOut.close();
            m_botAction.putFile( fileName );
        } catch( Exception e ){
            Tools.printStackTrace( e );
        }
    }
    
    /* Misc Code */
    public void sendPM( String name, String message, boolean remote ){
        if(remote){
            m_botAction.sendRemotePrivateMessage( name, message );
        } else {
            m_botAction.sendPrivateMessage( name, message );
        }
    }
    
    public void readObjects(){
        String fname = m_botAction.getBotName().toLowerCase() + ".dat";
        File f = new File(fname);
        
        
        m_playerList.clear();
        
        
        try{
            if (!f.exists()) f.createNewFile();
            
            ObjectInputStream in = new ObjectInputStream( 
            new BufferedInputStream(
            new FileInputStream( 
            m_botAction.getDataFile( fname ))));
            
            while ( true ) {
                potentialStaffer o = (potentialStaffer)in.readObject();
                
                if( o == null ) break;
                m_playerList.put( o.getName().toLowerCase(), o );
            }
            
            in.close();
            
        }catch( EOFException e){
            
        }catch( Exception e ){
            Tools.printStackTrace( e );
        }
    }
    
    public void writeObjects(){
        try{
            ObjectOutputStream out = new ObjectOutputStream( 
            new BufferedOutputStream( new FileOutputStream(
            m_botAction.getDataFile( 
            m_botAction.getBotName().toLowerCase() + ".dat" ))));
            Iterator i = m_playerList.values().iterator();
            while( i.hasNext() ){
                out.writeObject( i.next() );
            }
            out.close();
        } catch( Exception e ){
            Tools.printStackTrace( e );
        }
    }
    
    public int getInteger( String input, int def ){
        try{
            return Integer.parseInt( input.trim() );
        } catch( Exception e ){
            return def;
        }
    }
    
    public void compressToZip( String inFileName, String outFileName){
        try {
            FileInputStream fileIn = new FileInputStream( inFileName );
            ZipOutputStream fileOut = new ZipOutputStream( new FileOutputStream( outFileName ));
            
            fileOut.putNextEntry(new ZipEntry( inFileName ));
            
            byte[] buf = new byte[1024];
            int len;
            
            while ((len = fileIn.read(buf)) > 0) {
                fileOut.write(buf, 0, len);
            }
            
            fileIn.close();
            fileOut.closeEntry();
            fileOut.close();
            
        } catch( Exception e ){
            Tools.printStackTrace( e );
        }
    }
    
    static final Comparator SORT_DATE_ADDED = new Comparator() {
        public int compare(Object o1, Object o2) {
            potentialStaffer r1 = (potentialStaffer) o1;
            potentialStaffer r2 = (potentialStaffer) o2;
            return r2.getDate().compareTo(r1.getDate());
        }
    };
    
    static final Comparator SORT_NAME = new Comparator() {
        public int compare(Object o1, Object o2) {
            potentialStaffer r1 = (potentialStaffer) o1;
            potentialStaffer r2 = (potentialStaffer) o2;
            return r1.getName().toLowerCase().compareTo(r2.getName().toLowerCase());
        }
    };
    
    static final Comparator SORT_RATING = new Comparator() {
        public int compare(Object o1, Object o2) {
            potentialStaffer r1 = (potentialStaffer) o1;
            potentialStaffer r2 = (potentialStaffer) o2;
            return Double.compare(r2.getAveRating(), r1.getAveRating());
        }
    };
}

class potentialStaffer implements java.io.Serializable, java.lang.Comparable {
    static final long serialVersionUID = -518954860696583857L; //Old checksum so ReadObjects() will still read the .dat file
    private String name;
    private java.util.Date created = new java.util.Date();
    private HashMap comments = new HashMap();
    
    public int compareTo(Object o) {
        potentialStaffer n = (potentialStaffer)o;
        int lastCmp = name.compareTo(n.name);
        return (lastCmp);
    }
    
    public potentialStaffer( String playerName ){
        name = playerName;
    }
    
    public String getName(){
        return name;
    }
    
    public String getDate(){
        return new SimpleDateFormat("MM.dd.yy HH:mm:ss z").format(created);
    }
    
    public void addComment( String staffName, String comment, int rating ){
        comments.put( staffName.toLowerCase(), new staffComment( staffName, comment, rating ) );
    }
    
    public HashMap getComments(){
        return comments;
    }
    
    public double getAveRating(){
        double sum = 0;
        double count = 0;
        double value = 0;
        staffComment comment;
        
        for ( Iterator i = comments.values().iterator(); i.hasNext(); ){
            comment = (staffComment)i.next();
            sum += comment.getRating();
            count++;
        }
        
        if (count != 0){
            value = sum / count;
            value *= 10;
            value = Math.round( value );
            value /= 10;
            return value;
        } else {
            return 0;
        }
    }
    
    public int getCommentCount(){
        return comments.size();
    }
}

class staffComment implements java.io.Serializable {
    static final long serialVersionUID = 7658709147071201543L; //Old checksum
    private String name;
    private String comment;
    private int rating;
    
    public staffComment( String inName, String inComment, int inRating ){
        name = inName.trim();
        comment = inComment.trim();
        rating = inRating;
    }
    
    public String getComment(){
        return comment;
    }
    
    public int getRating(){
        return rating;
    }
    
    public String getName(){
        return name;
    }
}
