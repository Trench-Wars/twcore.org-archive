package twcore.bots.staffbot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.FileArrived;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.util.Tools;

/**
 * StaffBot performs two functions: checking for *warn-ings on specific players,
 * or from specific staff members; and rating potential new staffers.
 *
 *
 * @author  mr. spam
 * last updated: 07/26/2002
 *
 * Changes:
 *   Added "Comment count" to /!list* commands
 *   Cleaned up staffbot.cfg.  Removed unused settings and added LogArchivedEnabled, ArchivePath, & AutoLogTime
 *   Misc code changes/tweaks.. cleanup
 *   Added /!listrating (sorts ascending by average rating)
 *   Added automatic server log archiving. See .cfg
 *   Added /!getlog command, unlisted in help as not all smods will have access to logs
 */
public class staffbot extends SubspaceBot {
    OperatorList        m_opList;
    HashMap             <String,potentialStaffer>m_playerList = new HashMap<String,potentialStaffer>();
    BotAction           m_botAction;
    BotSettings         m_botSettings;
    boolean             m_logActive = false;
    boolean             m_logArchivingEnabled;
    String              m_logNotifyPlayer;
    final String        m_LOGFILENAME = "subgame.log";
    java.util.Date      m_logTimeStamp;
    private final static int MAX_NAME_SUGGESTIONS = 20;         // Max number of suggestions given
                                                                // for a name match in !warn
    private final static int WARNING_EXPIRE_TIME = Tools.TimeInMillis.WEEK * 2;
    

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
                    millis += Tools.TimeInMillis.DAY; //add a day

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

                temp = message.substring( message.indexOf( ") to " ) + 5 );
                warnedPlayer = temp.substring( 0, temp.indexOf( ":" ) ).toLowerCase();
                temp = message.substring( message.indexOf( "Ext: " ) + 5 );
                staffMember = temp.substring( 0, temp.indexOf( " (" ) ).toLowerCase().trim();
                if( !m_opList.isZH( staffMember ) )
                    return;

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
                queryWarnings( name, message.substring( 9 ), false );
            }
            else if( message.toLowerCase().startsWith( "!warnings " ) ){
                queryWarnings( name, message.substring( 10 ), false );
            }
            else if( message.toLowerCase().startsWith( "! " ) ){
                queryWarnings( name, message.substring( 2 ), false );
            }
            else if( message.toLowerCase().startsWith( "!allwarnings " ) ){
                queryWarnings( name, message.substring( 13 ), true );
            }
            else if( message.toLowerCase().startsWith( "!fuzzyname " ) ) {
                getFuzzyNames( name, message.substring( 11 ) );
            }
        }

        if( m_opList.isSmod( name ) ){
            if( message.toLowerCase().startsWith( "!warningsfrom " ))
                queryWarningsFrom( name, message.substring( 14 ) );
        }

        if( m_opList.isZH(name) )
            handleCommand( name, message, remote );
    }

    /**
     * Queries the database for stored warnings on a player.
     * @param name Staffer requesting
     * @param message Player to query
     * @param showExpired Whether or not to display expired warnings
     */
    public void queryWarnings( String name, String message, boolean showExpired ){
        String      query = "SELECT * FROM tblWarnings WHERE name = '" + Tools.addSlashesToString(message.toLowerCase()) + "' ORDER BY timeofwarning ASC";
        ArrayList<String> warnings = new ArrayList<String>();

        try {
            ResultSet set = m_botAction.SQLQuery( "local", query );

            if( set == null ) {
                m_botAction.sendRemotePrivateMessage( name, "ERROR: There is a problem with your query (returned null) or the database is down.  Please report this to bot development." );
                return;
            }
            
            // Lookup the warnings from the database
            int numExpired = 0;
            int numTotal = 0;
            
            while( set.next() ){
                String warning = set.getString( "warning" );
                java.sql.Date date = set.getDate( "timeofwarning" );
                java.sql.Date expireDate = new java.sql.Date(System.currentTimeMillis() - WARNING_EXPIRE_TIME);
                boolean expired = date.before(expireDate);
                if( expired )
                    numExpired++;
                if( !expired || showExpired ) {
                    String strDate = new SimpleDateFormat("dd MMM yyyy").format( date );

                    String[] text;
                    if( warning.contains("Ext: "))
                        text = warning.split( "Ext: ", 2);
                    else
                        text = warning.split( ": ", 2);

                    if( text.length == 2 )
                        warnings.add(strDate + "  " + text[1]);
                }
                numTotal++;
            }
            
            m_botAction.SQLClose( set );
            
            
            // Respond to the user
            if(numTotal > 0) {                
                if( showExpired ) {   // !allwarnings
                    m_botAction.sendRemotePrivateMessage( name, "Warnings in database for " + message + ":" );
                    m_botAction.remotePrivateMessageSpam( name, warnings.toArray(new String[warnings.size()]));
                    m_botAction.sendRemotePrivateMessage( name, "Displayed " + warnings.size() + " warnings (including " + numExpired + " expired warnings)." );
                } else {              // !warnings
                    if(warnings.size() > 0) {
                        m_botAction.sendRemotePrivateMessage( name, "Warnings in database for " + message + ":" );
                        m_botAction.remotePrivateMessageSpam( name, warnings.toArray(new String[warnings.size()]));
                        m_botAction.sendRemotePrivateMessage( name, "Displayed " + warnings.size() + " valid warnings (suppressed " + numExpired + " expired). PM !allwarnings to display all." );
                    } else {
                        m_botAction.sendRemotePrivateMessage( name, "No active warnings for "+ message +".");
                        m_botAction.sendRemotePrivateMessage( name, "There are "+numExpired+" expired warnings. PM !allwarnings to display these.");
                    }
                    
                }
                
            } else {
                m_botAction.sendRemotePrivateMessage( name, "No warnings found for " + message + ".");
                m_botAction.sendRemotePrivateMessage( name, "PM '!fuzzyname "+message+"' to check for similar names.");
            }
        } catch( SQLException e ){
            Tools.printStackTrace( e );
        }
    }
    
    /**
     * Based on a given name fragment, find other players that start with the fragment.
     * @param name Staffer running cmd
     * @param message Name fragment
     */
    public void getFuzzyNames( String name, String message ) {
        ArrayList<String> fuzzynames = new ArrayList<String>();
        
        String query = "" +
        		"SELECT DISTINCT(name) " +
        		"FROM tblWarnings " +
        		"WHERE name LIKE '" + Tools.addSlashesToString(message.toLowerCase()) + "%' " +
        		"ORDER BY name LIMIT 0,"+MAX_NAME_SUGGESTIONS;
        
        try {
            ResultSet set = m_botAction.SQLQuery( "local", query );                
            while( set.next() ) {
                fuzzynames.add(" " + set.getString( "name" ));
            }
            
            if(fuzzynames.size() > 0) {
                m_botAction.sendRemotePrivateMessage( name, "Names in database starting with '" + message + "':" );
                m_botAction.remotePrivateMessageSpam( name, fuzzynames.toArray(new String[fuzzynames.size()]));
                if( fuzzynames.size() == MAX_NAME_SUGGESTIONS )
                    m_botAction.sendRemotePrivateMessage( name, "Results limited to "+ MAX_NAME_SUGGESTIONS + ", refine your search further if you have not found the desired result." );
            } else {
                m_botAction.sendRemotePrivateMessage( name, "No names found starting with '"+message+"'.");
            }
            
        } catch( SQLException e ){
            Tools.printStackTrace( e );
        }

    }

    public void queryWarningsFrom( String name, String message ){
        String      query = "SELECT * FROM tblWarnings WHERE staffmember = \"" + Tools.addSlashesToString(message.toLowerCase()) + "\" ORDER BY timeofwarning DESC";

        try {
            ResultSet set = m_botAction.SQLQuery( "local", query );

            m_botAction.sendRemotePrivateMessage( name, "Warnings in database given by " + message + ":" );
            while( set.next() ){
                String warning = set.getString( "warning" );
                java.sql.Date date = set.getDate( "timeofwarning" );
                String strDate = new SimpleDateFormat("dd MMM yyyy").format( date );

                String[] text = warning.split( ": \\S", 2);
                if( text.length == 2 )
                    m_botAction.sendRemotePrivateMessage( name, strDate + "  - " + text[1]);
            }
            m_botAction.sendRemotePrivateMessage( name, "End of list." );
            m_botAction.SQLClose( set );
        } catch( SQLException e ){
            Tools.printStackTrace( e );
        }
    }

    public String formatString(String fragment, int length) {
        if(fragment.length() > length)
            fragment = fragment.substring(0,length-1);
        else {
            for(int i=fragment.length();i<length;i++)
                fragment = fragment + " ";
        }
        return fragment;
    }

    public void handleEvent( FileArrived event ){
        if ( event.getFileName().equals(m_LOGFILENAME) ){
            logArrived();
        }
    }

    public void handleCommand( String name, String message, boolean remote){
        if( message.toLowerCase().equals("!help") ){
            handleHelp( name, remote );
        } else if( message.toLowerCase().startsWith("!add ") ){
            addPlayer( name, message.substring(5), remote );
        } else if( message.toLowerCase().startsWith("!comment ") ){
            String[] args;

            args = message.substring(9).split(":", 3);
            if ( args.length == 3 ){
                addComment( args[0], name, args[2], getInteger(args[1], 0), remote );
            } else {
                sendPM( name, "Incorrect parameters. Use !comment <player>:<rating>:<comment>", remote);
            }
        }

        if( ! m_opList.isModerator(name) )
            return;

        if( message.toLowerCase().equals("!list") ){
            sendListAsc( name, 10, SORT_DATE_ADDED, remote );
        } else if( message.toLowerCase().startsWith("!list ") ){
            sendListAsc( name, getInteger(message.substring(6), 10), SORT_DATE_ADDED, remote );
        } else if( message.toLowerCase().equals("!listall") ){
            sendListDesc( name, m_playerList.size(), SORT_NAME, remote );
        } else if( message.toLowerCase().equals("!listrating") ){
            sendListDesc( name, 10, SORT_RATING, remote );
        } else if( message.toLowerCase().startsWith("!listrating ") ){
            sendListDesc( name, getInteger(message.substring(12), 10), SORT_RATING, remote );
        } else if( message.toLowerCase().startsWith("!listplayer ") ){
            showComments( name, message.substring(12), remote );
        }

        if( ! m_opList.isSmod(name) )
            return;

        if( message.toLowerCase().startsWith("!remove ") ){
            remPlayer( name, message.substring(8).trim(), remote );
        } else if( message.toLowerCase().equals("!getlog") ){
            getLog( name, remote );
        }
    }

    public void handleHelp( String name, boolean remote ){
        final String[] helpTextZH = {
            "Available ZH commands:",
            "!add <player>          - Adds a player to the recommendation list",
            "!comment <player>:<rating>:<comment>  - Adds a comment and rating(0-5) for specified player"
        };

        final String[] helpTextER = {
            "Available ER commands:",
            "!warnings <player>     - Checks valid red warnings on specified player",
            "! <player>             - (shortcut for above)",
            "!allwarnings <player>  - Shows all warnings on player, including expired.",
            "!fuzzyname <player>    - Checks for names similar to <player> in database.",
            "!add <player>          - Adds a player to the recommendation list",
            "!comment <player>:<rating>:<comment>  - Adds a comment and rating(0-5) for specified player"
        };

        final String[] helpTextMod = {
            "Available Mod commands:",
            "!warnings <player>     - Checks valid red warnings on specified player",
            "! <player>             - (shortcut for above)",
            "!allwarnings <player>  - Shows all warnings on player, including expired.",
            "!fuzzyname <player>    - Checks for names similar to <player> in database.",
            "!add <player>          - Adds a player to the recommendation list",
            "!comment <player>:<rating>:<comment>  - Adds a comment and rating(0-5) for specified player",
            "!list <num>            - Lists <num>(optional) of players in chronological order",
            "!listrating <num>      - Lists <num>(optional) of players descending by average rating",
            "!listall               - Displays all players in the list in alphabetical order",
            "!listplayer <player>   - Displays that player's details along with all comments"
        };

        final String[] helpTextSmod = {
            "Available upper staff commands:",
            "!add <player>          - Adds a player to the recommendation list",
            "!comment <player>:<rating>:<comment>  - Adds a comment and rating(0-5) for specified player",
            "!remove <player>       - Removes a player from the list",
            "!list <num>            - Lists <num>(optional) of players in chronological order",
            "!listrating <num>      - Lists <num>(optional) of players descending by average rating",
            "!listall               - Displays all players in the list in alphabetical order",
            "!listplayer <player>   - Displays that player's details along with all comments",
            "!getlog                - Downloads server log",
            "Player DB commands:",
/*            "!squaddies <player>    - Displays a list of all the players on a player's squad",
            "!dblsquad <player>     - Displays a list of all the name/squad combinations this player might own",
            "!altnick <player>      - Displays a list of all the player's alt nicks.",*/
            "!warnings <player>     - Checks valid red warnings on specified player",
            "! <player>             - (shortcut for above)",
            "!allwarnings <player>  - Shows all warnings on player, including expired.",
            "!warningsfrom <player> - Displays a list of recent warns given to a player.",
            "!fuzzyname <player>    - Checks for names similar to <player> in database."
        };

        if( m_opList.isZHExact( name ) ){
            m_botAction.remotePrivateMessageSpam( name, helpTextZH );
        }
        else if( m_opList.isERExact( name ) ){
            m_botAction.remotePrivateMessageSpam( name, helpTextER );
        }
        else if( m_opList.isModeratorExact( name ) ){
            m_botAction.remotePrivateMessageSpam( name, helpTextMod );
        }
        else if( m_opList.isHighmodExact( name ) ){
            m_botAction.remotePrivateMessageSpam( name, helpTextMod );
        }
        else if( m_opList.isSmod( name ) ){
            m_botAction.remotePrivateMessageSpam( name, helpTextSmod );
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

        axed = m_playerList.get( playerName.toLowerCase().trim() );

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

    public void sendListAsc( String name, int count, Comparator <potentialStaffer>sort, boolean remote ){
        LinkedList <potentialStaffer>sortedList = new LinkedList<potentialStaffer>();
        LinkedList <potentialStaffer>buffer = new LinkedList<potentialStaffer>();
        potentialStaffer listItem;
        sortedList.addAll( m_playerList.values() );
        Collections.sort( sortedList, sort );

        if( count > sortedList.size() ){ count = sortedList.size() ; }

        Iterator <potentialStaffer>i = sortedList.iterator();

        for( int x = 0 ; x < count ; x++ ){
            buffer.addFirst( i.next() );
        }

        for( i = buffer.iterator(); i.hasNext(); ){
            listItem = i.next();
            sendRecord( name, remote, listItem.getDate(), listItem.getCommentCount(), listItem.getAveRating(), listItem.getName() );
        }
    }

    public void sendListDesc( String name, int count, Comparator <potentialStaffer>sort, boolean remote ){
        LinkedList <potentialStaffer>sortedList = new LinkedList<potentialStaffer>();
        potentialStaffer listItem;

        sortedList.addAll( m_playerList.values() );
        Collections.sort( sortedList, sort );

        if( count > sortedList.size() ){ count = sortedList.size() ; }

        Iterator<potentialStaffer> i = sortedList.iterator();

        for( int x = 0 ; x < count ; x++ ){
            listItem = i.next();
            sendRecord( name, remote, listItem.getDate(), listItem.getCommentCount(), listItem.getAveRating(), listItem.getName() );
        }
    }

    public void listPlayer( String staffName, String playerName, boolean remote ){
        potentialStaffer player = m_playerList.get( playerName.toLowerCase().trim() );

        if (player != null){
            sendRecord( staffName, remote, player.getDate(), player.getCommentCount(), player.getAveRating(), player.getName() );
        } else {
            sendPM( staffName, playerName + " not found", remote );
        }
    }

    public void addComment( String playerName, String staffName, String comment, int rating, boolean remote ){
        potentialStaffer player;

        player = m_playerList.get( playerName.toLowerCase().trim() );

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

        player = m_playerList.get( playerName.toLowerCase().trim() );

        if ( player != null ){
            HashMap<String, staffComment> comments = player.getComments();
            staffComment comment;

            listPlayer( staffName, playerName, remote );

            for ( Iterator<staffComment> i = comments.values().iterator(); i.hasNext(); ){
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
            Iterator<potentialStaffer> i = m_playerList.values().iterator();
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

    static final Comparator <potentialStaffer>SORT_DATE_ADDED = new Comparator<potentialStaffer>() {
        public int compare(potentialStaffer r1, potentialStaffer r2) {
            return r2.getDate().compareTo(r1.getDate());
        }
    };

    static final Comparator <potentialStaffer>SORT_NAME = new Comparator<potentialStaffer>() {
        public int compare(potentialStaffer r1, potentialStaffer r2) {
            return r1.getName().toLowerCase().compareTo(r2.getName().toLowerCase());
        }
    };

    static final Comparator <potentialStaffer>SORT_RATING = new Comparator<potentialStaffer>() {
        public int compare(potentialStaffer r1, potentialStaffer r2) {
            return Double.compare(r2.getAveRating(), r1.getAveRating());
        }
    };
}

class potentialStaffer implements Serializable, Comparable<potentialStaffer> {
    static final long serialVersionUID = -518954860696583857L; //Old checksum so ReadObjects() will still read the .dat file
    private String name;
    private java.util.Date created = new java.util.Date();
    private HashMap <String,staffComment>comments = new HashMap<String,staffComment>();

    public int compareTo(potentialStaffer o) {
        potentialStaffer n = o;
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

    public HashMap<String, staffComment> getComments(){
        return comments;
    }

    public double getAveRating(){
        double sum = 0;
        double count = 0;
        double value = 0;
        staffComment comment;

        for ( Iterator<staffComment> i = comments.values().iterator(); i.hasNext(); ){
            comment = i.next();
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

class staffComment implements Serializable {
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

