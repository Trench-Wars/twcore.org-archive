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
    public static final String TWSITES_DATABASE = "server";
    public static final String FIND_DELIMETER = " - ";
    public static final int CHECK_LENGTH = 180;
    public static final int CHECK_DURATION = 1;
    public static final int DIE_DELAY = 100;

    OperatorList        m_opList;
    HashMap             m_playerList = new HashMap();
    BotAction           m_botAction;
    BotSettings         m_botSettings;
    boolean             m_logActive = false;
    boolean             m_logArchivingEnabled;
    String              m_logNotifyPlayer;
    final String        m_LOGFILENAME = "subgame.log";
    java.util.Date      m_logTimeStamp;
    private AltCheck 	currentCheck;

    /* Initialization code */
    public staffbot( BotAction botAction ) {
        super( botAction );
        m_botAction = botAction;
        m_botSettings = m_botAction.getBotSettings();
        currentCheck = new AltCheck();
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
            } else {
                handleArenaMessage(message);
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
            }
        }

        if( m_opList.isER( name ) ){
            if( message.toLowerCase().startsWith( "!warnings " ) ){
                queryWarnings( name, message.substring( 10 ) );
            }
        }

        if( m_opList.isHighmod( name ) ){
            if( message.toLowerCase().startsWith( "!altwarn " ) ){
                doAltWarnCmd( name, message.substring( 9 ) );
            } else if( message.toLowerCase().startsWith( "!find " ) ){
                doFindCmd( name, message.substring( 6 ) );
            }
        }

        if( m_opList.isSmod( name ) ){
            if( message.toLowerCase().startsWith( "!warningsfrom " ))
                queryWarningsFrom( name, message.substring( 14 ) );
        }

        if( m_opList.isZH(name) )
            handleCommand( name, message, remote );
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
            "!add <player>          - Adds a player to the recommendation list",
            "!comment <player>:<rating>:<comment>  - Adds a comment and rating(0-5) for specified player"
        };

        final String[] helpTextER = {
            "!warnings <player>     - Checks red warnings on specified player",
            "!add <player>          - Adds a player to the recommendation list",
            "!comment <player>:<rating>:<comment>  - Adds a comment and rating(0-5) for specified player"
        };

        final String[] helpTextMod = {
            "!warnings <player>     - Checks red warnings on specified player",
            "!add <player>          - Adds a player to the recommendation list",
            "!comment <player>:<rating>:<comment>  - Adds a comment and rating(0-5) for specified player",
            "!list <num>            - Lists <num>(optional) of players in chronological order",
            "!listrating <num>      - Lists <num>(optional) of players descending by average rating",
            "!listall               - Displays all players in the list in alphabetical order",
            "!listplayer <player>   - Displays that player's details along with all comments"
        };
        
        final String[] helpTextHighmod = {
            "!warnings <player>     - Checks red warnings on specified player",
            "!altwarn <player>      - Checks warns on all of player's altnicks",
            "!find <player>         - Finds a player on all of their altnicks",
            "!add <player>          - Adds a player to the recommendation list",
            "!comment <player>:<rating>:<comment>  - Adds a comment and rating(0-5) for specified player",
            "!list <num>            - Lists <num>(optional) of players in chronological order",
            "!listrating <num>      - Lists <num>(optional) of players descending by average rating",
            "!listall               - Displays all players in the list in alphabetical order",
            "!listplayer <player>   - Displays that player's details along with all comments"
        };
        
        final String[] helpTextSmod = {
            "!warnings <player>     - Checks red warnings on specified player",
            "!warningsfrom <player> - Displays a list of recent warns given to a player.",
            "!altwarn <player>      - Checks warns on all of player's altnicks",
            "!find <player>         - Finds a player on all of their altnicks",
            "!add <player>          - Adds a player to the recommendation list",
            "!comment <player>:<rating>:<comment>  - Adds a comment and rating(0-5) for specified player",
            "!remove <player>       - Removes a player from the list",
            "!list <num>            - Lists <num>(optional) of players in chronological order",
            "!listrating <num>      - Lists <num>(optional) of players descending by average rating",
            "!listall               - Displays all players in the list in alphabetical order",
            "!listplayer <player>   - Displays that player's details along with all comments",
            "!getlog                - Downloads server log"
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
            m_botAction.remotePrivateMessageSpam( name, helpTextHighmod );
        }
        else if( m_opList.isSmod( name ) ){
            m_botAction.remotePrivateMessageSpam( name, helpTextSmod );
        }
    }

    /**
     * This method handles an arena message.  If it is the result of a *locate
     * command, and a check is active, it will send the results of the find to the
     * person.
     *
     * @param message is the arena message to handle.
     */
    private void handleArenaMessage(String message)
    {
        String name;
        int endOfNameIndex;
        
        if(currentCheck.isActive())
        {
            endOfNameIndex = message.indexOf(FIND_DELIMETER);
            if(endOfNameIndex != -1) {
                name = message.substring(0, endOfNameIndex).trim();
                currentCheck.addResult(name, message);
            }
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
            if (set != null) set.close();
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
            if (set != null) set.close();
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
    

    // **** EXPERIMENTAL '!FIND' and '!ALTWARN' COMMAND SUPPORT 

    /**
     * This method finds a player if they are in the zone, using any of their
     * known altnicks.
     *
     * @param argString
     */
    private void doFindCmd(String sender, String argString)
    {
        if(currentCheck.isActive())
            throw new RuntimeException("Already performing a check.  Please try again momentarily.");
        
        Vector altNicks = getAltNicks(argString);
        if(altNicks.isEmpty())
            throw new RuntimeException("Player not found in database.");
        currentCheck.startCheck(sender, altNicks);
        locateAll(altNicks);
        m_botAction.scheduleTask(new EndCheckTask(), CHECK_DURATION * 1000);
    }
    
    /**
     *
     * @param sender
     * @param argString
     */
    private void doAltWarnCmd(String sender, String argString)
    {
        if(currentCheck.isActive())
            throw new RuntimeException("Already performing a check.  Please try again momentarily.");
        
        Vector altNicks = getAltNicks(argString);
        if(altNicks.isEmpty())
            throw new RuntimeException("Player not found in database.");
        findWarnings(sender, argString, altNicks);
    }
    
    /**
     * This private method performs a locate command on a Vector of names.
     *
     * @param altNicks are the names to perform the altnick command on.
     */
    private void locateAll(Vector altNicks)
    {
        for(int index = 0; index < altNicks.size(); index++)
            m_botAction.sendUnfilteredPublicMessage("*locate " + (String) altNicks.get(index));
    }
    
    /**
     * This private method finds the warnings for a Vector of names.
     *
     * @param altNicks are the names to perform the altNick command on.
     */
    private void findWarnings(String sender, String name, Vector altNicks)
    {
        if( altNicks.size() <= 0 )
            throw new RuntimeException( "Unable to retreive any altnicks for player." );
                       
        m_botAction.sendRemotePrivateMessage(sender, "Warnings for " + name + ":");                

        try {
            Iterator i = altNicks.iterator();
            String altnick;
            while( i.hasNext() ) {
                altnick = (String)i.next();
                queryWarnings(sender, altnick);
            }
            m_botAction.sendRemotePrivateMessage(sender, "End of list.");
        } catch (Exception e) {
            throw new RuntimeException( "Unexpected error while querying altnicks." );            
        }
    }
    
    /**
     * This private method performs an altnick query on a players name and returns
     * the results in the form of a ResultSet.
     * @param playerName is the player to check.
     * @return the results of the query are returned.
     */
    private Vector getAltNicks(String playerName)
    {
        try
        {
            Vector altNicks = new Vector();
            ResultSet resultSet = m_botAction.SQLQuery(TWSITES_DATABASE,
                    "SELECT * " +
                    "FROM tblAlias A1, tblAlias A2, tblUser U1, tblUser U2 " +
                    "WHERE U1.fcUserName = '" + Tools.addSlashesToString(playerName) + "' " +
                    "AND U1.fnUserID = A1.fnUserID " +
                    "AND A1.fcIP = A2.fcIP " +
                    "AND A1.fnMachineID = A2.fnMachineID " +
                    "AND A2.fnUserID = U2.fnUserID " +
            "ORDER BY U2.fcUserName, A2.fdUpdated");
            String lastName = "";
            String currName;
            if(resultSet == null)
                throw new RuntimeException("ERROR: Cannot connect to database.");
            
            while(resultSet.next())
            {
                currName = resultSet.getString("U2.fcUserName");
                if(!currName.equalsIgnoreCase(lastName))
                    altNicks.add(currName);
                lastName = currName;
            }
            return altNicks;
        }
        catch(SQLException e)
        {
            throw new RuntimeException("ERROR: Cannot connect to database.");
        }
    }
    
    private class AltCheck
    {
        public static final int FIND_CHECK = 0;
        
        private TreeMap checkResults;
        private String checkSender;
        private boolean isActive;
        
        public AltCheck()
        {
            checkResults = new TreeMap();
            isActive = false;
        }
        
        public void startCheck(String checkSender, Vector altNicks)
        {
            if(isActive)
                throw new RuntimeException("Already performing a check.  Please try again momentarily.");
            this.checkSender = checkSender;
            checkResults.clear();
            populateResults(altNicks);
            isActive = true;
        }
        
        public String getCheckSender()
        {
            return checkSender;
        }
        
        /**
         * This method stops the current check.
         */
        public void stopCheck()
        {
            isActive = false;
        }
        
        /**
         * This method gets the number of checks that were made.
         *
         * @return the number of checks that were made is returned.
         */
        public int getNumNicks()
        {
            return checkResults.size();
        }
        
        /**
         * This method gets the results of a check.
         *
         * @return a Vector containing the results of a check is returned.
         */
        public Vector getResults()
        {
            Vector results = new Vector();
            Collection allResults = checkResults.values();
            Iterator iterator = allResults.iterator();
            String result;
            
            while(iterator.hasNext())
            {
                result = (String) iterator.next();
                if(result != null)
                    results.add(result);
            }
            
            return results;
        }
        
        /**
         * This method adds a result to the result map.
         *
         * @param name is the name of the nick that got the result.
         * @param result is the result of the check.
         */
        public void addResult(String name, String result)
        {
            String lowerName = name.toLowerCase();
            if(checkResults.containsKey(lowerName))
                checkResults.put(lowerName, result);
        }
        
        /**
         * This method checks to see if a check is currently active.
         *
         * @return true is returned if the check is active.
         */
        public boolean isActive()
        {
            return isActive;
        }
        
        /**
         * This private method populates the keys of the result map with the names
         * from the altNick check.
         *
         * @param altNicks are the names that will be checked.
         */
        private void populateResults(Vector altNicks)
        {
            String altNick;
            
            for(int index = 0; index < altNicks.size(); index++)
            {
                altNick = (String) altNicks.get(index);
                checkResults.put(altNick.toLowerCase(), null);
            }
        }
    }
    
    /**
     * This private method displays the results of a check.
     *
     * @param sender
     * @param numNicks
     * @param checkType
     * @param results
     */
    private void displayResults(String sender, int numNicks, Vector results)
    {
        if(results.isEmpty())
            throw new RuntimeException("Player not online.");
        for(int index = 0; index < results.size(); index++)
            m_botAction.sendSmartPrivateMessage(sender, (String) results.get(index));
        m_botAction.sendSmartPrivateMessage(sender, "Checked " + numNicks + " names.");
    }
    
    private class EndCheckTask extends TimerTask
    {
        public void run()
        {
            Vector results = currentCheck.getResults();
            String checkSender = currentCheck.getCheckSender();
            int numNicks = currentCheck.getNumNicks();
            
            try
            {
                currentCheck.stopCheck();
                displayResults(checkSender, numNicks, results);
            }
            catch(RuntimeException e)
            {
                m_botAction.sendSmartPrivateMessage(checkSender, e.getMessage());
            }
        }
    }
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