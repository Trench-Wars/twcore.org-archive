package twcore.bots.pubhub;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.stats.DBPlayerData;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

/**
 * THIS CODE SERIOUSLY NEEDS TO BE REFACTORED!! - Cpt
 */

public class pubhubalias extends PubBotModule
{
	public static final String DATABASE = "website";
	public static final int REMOVE_DELAY = 3 * 60 * 60 * 1000;
	public static final int CLEAR_DELAY = 3 * 60 * 1000;
	public static final int DEFAULT_DAYS = 180;

	private static final String NAME_FIELD = "fcUserName";
	private static final String IP_FIELD = "fcIpString";
	private static final String MID_FIELD = "fnMachineId";
	private static final String TIMES_UPDATED_FIELD = "fnTimesUpdated";
	private static final String LAST_UPDATED_FIELD = "fdUpdated";

	private static final int NAME_PADDING = 25;
	private static final int IP_PADDING = 18;
	private static final int MID_PADDING = 14;
	private static final int TIMES_UPDATED_PADDING = 12;
	private static final int DATE_UPDATED_PADDING = 20;
	private static final int DEFAULT_PADDING = 25;

	private static final String NAME_HEADER = "Player Name";
	private static final String IP_HEADER = "IP";
	private static final String MID_HEADER = "MID";
	private static final String TIMES_UPDATED_HEADER = "X Updated";
	private static final String DATE_UPDATED_HEADER = "Last Updated";
	private static final String DEFAULT_HEADER = "Unknown Column";

	private static final String DATE_FIELD_PREFIX = "fd";

	private Set<String> justAdded;
	private Set<String> deleteNextTime;
	private Map<String,String> watchedIPs;
	private Map<String,String> watchedNames;
	private Map<String,String> watchedMIDs;
	//    < IP , Comment >
	//    < MID, Comment >
	//    <Name, Comment >
	private ClearRecordTask clearRecordTask;

	private int m_maxRecords = 15;
	private boolean m_sortByName = false;
	
	private HashMap<String,String> twdops = new HashMap<String,String>();
	private HashMap<String,String> aliasops = new HashMap<String,String>();

	/**
	 * This method initializes the module.
	 */
	public void initializeModule()
	{
		justAdded = Collections.synchronizedSet(new HashSet<String>());
		deleteNextTime = Collections.synchronizedSet(new HashSet<String>());
		watchedIPs = Collections.synchronizedMap(new HashMap<String,String>());
		watchedNames = Collections.synchronizedMap(new HashMap<String,String>());
		watchedMIDs = Collections.synchronizedMap(new HashMap<String,String>());
		clearRecordTask = new ClearRecordTask();

		m_botAction.scheduleTaskAtFixedRate(clearRecordTask, CLEAR_DELAY, CLEAR_DELAY);
		
		loadWatches();
		
		updateTWDOps();
		updateAliasOps();
	}
	
	private boolean isTWDOp(String name) {
	    if (twdops.containsKey(name.toLowerCase())) {
	        return true;
	    } else {
	        return false;
	    }
	}
	
	private boolean isAliasOp(String name) {
        if (aliasops.containsKey(name.toLowerCase())) {
            return true;
        } else {
            return false;
        }
    }

	public void requestEvents(EventRequester eventRequester)
	{
		eventRequester.request(EventRequester.MESSAGE);
	}

	private void doAltNickCmd(String playerName)
	{
		try
		{
			String[] headers = {NAME_FIELD, IP_FIELD, MID_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD};

			String ipResults = getSubQueryResultString(
					"SELECT DISTINCT(fnIP) " +
					"FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " +
					"WHERE fcUserName = '" + Tools.addSlashesToString(playerName) + "'", "fnIP");

			String midResults = getSubQueryResultString(
					"SELECT DISTINCT(fnMachineId) " +
					"FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " +
					"WHERE fcUserName = '" + Tools.addSlashesToString(playerName) + "'", "fnMachineId");

			String queryString =
				"SELECT * " +
				"FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " +
				"WHERE fnIP IN " + ipResults + " " +
				"AND fnMachineID IN " + midResults + " " + getOrderBy();

			if(ipResults == null || midResults == null)
				m_botAction.sendChatMessage("Player not found in database.");
			else
				displayAltNickResults(queryString, headers, "fcUserName");

		}
		catch(SQLException e)
		{
			throw new RuntimeException("SQL Error: " + e.getMessage(), e);
		}
	}

	private void doAltMacIdCmd(String playerMid)
	{
	    if(!Tools.isAllDigits(playerMid)) {
	        m_botAction.sendChatMessage("Command syntax error: Please use !altmid <number>");
	        return;
	    }
	    
		try
		{
			String[] headers = {NAME_FIELD, IP_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD};

			displayAltNickResults(
					"SELECT * " +
					"FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " +
					"WHERE fnMachineId = " + playerMid + " " +
					getOrderBy(), headers, "fcUserName"
			);
		}
		catch(SQLException e)
		{
			throw new RuntimeException("SQL Error: " + e.getMessage(), e);
		}
	}

	private String getOrderBy()
	{
		if(m_sortByName)
			return "ORDER BY fcUserName";
		return "ORDER BY fdUpdated DESC";
	}

	private void doAltIpCmd(String playerIp)
	{
		try
		{
			String[] headers = {NAME_FIELD, MID_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD};
			long ip32Bit = make32BitIp(playerIp);

			displayAltNickResults(
					"SELECT * " +
					"FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " +
					"WHERE fnIp LIKE '" + ip32Bit + " %'" +
					getOrderBy(), headers, "fcUserName"
			);
		}
		catch(SQLException e)
		{
			throw new RuntimeException("SQL Error: " + e.getMessage(), e);
		}
	}

	private void doInfoCmd(String playerName)
	{
		try
		{
			String[] headers = {IP_FIELD, MID_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD};
			displayAltNickResults(
					"SELECT * " +
					"FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " +
					"WHERE fcUserName = '" + Tools.addSlashesToString(playerName) + "' " +
					getOrderBy(), headers
			);
		}
		catch(SQLException e)
		{
			throw new RuntimeException("SQL Error: " + e.getMessage(), e);
		}
	}

	private long make32BitIp(String ipString)
	{
		StringTokenizer stringTokens = new StringTokenizer(ipString, ".");
		String ipPart;
		long ip32Bit = 0;

		try
		{
			while(stringTokens.hasMoreTokens())
			{
				ipPart = stringTokens.nextToken();
				ip32Bit = ip32Bit * 256 + Integer.parseInt(ipPart);
			}
		}
		catch(NumberFormatException e)
		{
			throw new IllegalArgumentException("Error: Malformed IP Address.");
		}

		return ip32Bit;
	}


	private void displayAltNickResults(String queryString, String[] headers, String uniqueField) throws SQLException
	{
		ResultSet resultSet = m_botAction.SQLQuery(DATABASE, queryString);
		HashSet<String> prevResults = new HashSet<String>();
		String curResult = null;
		int numResults = 0;

		if(resultSet == null)
			throw new RuntimeException("ERROR: Null result set returned; connection may be down.");

		m_botAction.sendChatMessage(getResultHeaders(headers));
		while(resultSet.next())
		{
			if(uniqueField != null)
				curResult = resultSet.getString(uniqueField);

			if(uniqueField == null || !prevResults.contains(curResult))
			{
				if(numResults <= m_maxRecords)
					m_botAction.sendChatMessage(getResultLine(resultSet, headers));
				prevResults.add(curResult);
				numResults++;
			}
		}

		if(numResults > m_maxRecords)
			m_botAction.sendChatMessage(numResults - m_maxRecords + " records not shown.  !maxrecords # to show (current: " + m_maxRecords + ")" );
		else
			m_botAction.sendChatMessage("Altnick returned " + numResults + " results.");
                m_botAction.SQLClose( resultSet );	}

	private void displayAltNickResults(String queryString, String[] headers) throws SQLException
	{
		displayAltNickResults(queryString, headers, null);
	}

	private String getResultHeaders(String[] displayFields)
	{
		StringBuffer resultHeaders = new StringBuffer();
		String displayField;
		String fieldHeader;
		int padding;

		for(int index = 0; index < displayFields.length; index++)
		{
			displayField = displayFields[index];
			padding = getFieldPadding(displayField);
			fieldHeader = getFieldHeader(displayField);

			resultHeaders.append(padString(fieldHeader, padding));
		}
		return resultHeaders.toString().trim();
	}

	private String getResultLine(ResultSet resultSet, String[] displayFields) throws SQLException
	{
		StringBuffer resultLine = new StringBuffer();
		String displayField;
		String fieldValue;
		int padding;

		for(int index = 0; index < displayFields.length; index++)
		{
			displayField = displayFields[index];
			padding = getFieldPadding(displayField);

			if(displayField.startsWith(DATE_FIELD_PREFIX))
				fieldValue = resultSet.getDate(displayField).toString();
			else
				fieldValue = resultSet.getString(displayField);
			resultLine.append(padString(fieldValue, padding));
		}
		return resultLine.toString().trim();
	}

	private String getFieldHeader(String displayField)
	{
		if(displayField.equalsIgnoreCase(NAME_FIELD))
			return NAME_HEADER;
		if(displayField.equalsIgnoreCase(IP_FIELD))
			return IP_HEADER;
		if(displayField.equalsIgnoreCase(MID_FIELD))
			return MID_HEADER;
		if(displayField.equalsIgnoreCase(TIMES_UPDATED_FIELD))
			return TIMES_UPDATED_HEADER;
		if(displayField.equalsIgnoreCase(LAST_UPDATED_FIELD))
			return DATE_UPDATED_HEADER;
		return DEFAULT_HEADER;
	}

	private int getFieldPadding(String displayField)
	{
		if(displayField.equalsIgnoreCase(NAME_FIELD))
			return NAME_PADDING;
		if(displayField.equalsIgnoreCase(IP_FIELD))
			return IP_PADDING;
		if(displayField.equalsIgnoreCase(MID_FIELD))
			return MID_PADDING;
		if(displayField.equalsIgnoreCase(TIMES_UPDATED_FIELD))
			return TIMES_UPDATED_PADDING;
		if(displayField.equalsIgnoreCase(LAST_UPDATED_FIELD))
			return DATE_UPDATED_PADDING;

		return DEFAULT_PADDING;
	}

	private String getSubQueryResultString(String queryString, String columnName) throws SQLException
	{
		ResultSet resultSet = m_botAction.SQLQuery(DATABASE, queryString);
		StringBuffer subQueryResultString = new StringBuffer("(");

		if(resultSet == null)
			throw new RuntimeException("ERROR: Null result set returned; connection may be down.");
		if(!resultSet.next())
			return null;
		for(;;)
		{
			subQueryResultString.append(resultSet.getString(columnName));
			if(!resultSet.next())
				break;
			subQueryResultString.append(", ");
		}
		subQueryResultString.append(") ");
                m_botAction.SQLClose( resultSet );

		return subQueryResultString.toString();
	}

	/**
	 * Compares IP and MID info of two names, and shows where they match.
	 * @param argString
	 * @throws SQLException
	 */
	public void doCompareCmd(String argString) throws SQLException
	{
		StringTokenizer argTokens = new StringTokenizer(argString, ":");

		if( argTokens.countTokens() != 2 )
			throw new IllegalArgumentException("Please use the following format: !compare <Player1Name>:<Player2Name>");

		String player1Name = argTokens.nextToken();
		String player2Name = argTokens.nextToken();

		try
		{
			ResultSet p1Set = m_botAction.SQLQuery(DATABASE,
					"SELECT * " +
					"FROM tblUser U, tblAlias A " +
					"WHERE U.fcUserName = '" + Tools.addSlashesToString(player1Name) + "' " +
					"AND U.fnUserID = A.fnUserID " +
			"ORDER BY A.fdUpdated DESC" );

			ResultSet p2Set = m_botAction.SQLQuery(DATABASE,
					"SELECT * " +
					"FROM tblUser U, tblAlias A " +
					"WHERE U.fcUserName = '" + Tools.addSlashesToString(player2Name) + "' " +
					"AND U.fnUserID = A.fnUserID " +
			"ORDER BY A.fdUpdated DESC" );

			if( p1Set == null || p2Set == null )
				throw new RuntimeException("ERROR: Null result set returned; connection may be down.");

			p1Set.afterLast();
			p2Set.afterLast();

			m_botAction.sendChatMessage("Comparison of " + player1Name + " to " + player2Name + ":" );

			LinkedList<String> IPs = new LinkedList<String>();
			LinkedList<Integer> MIDs = new LinkedList<Integer>();
			while( p1Set.previous() ) {
				IPs.add( p1Set.getString("A.fcIPString") );
				MIDs.add( p1Set.getInt("A.fnMachineID") );
			}

			int results = 0;
			boolean matchIP, matchMID;
			String display;

			while( p2Set.previous() ) {
				matchIP = false;
				matchMID = false;
				display = "";
				if( MIDs.contains( p2Set.getInt("A.fnMachineID") ) )
					matchMID = true;
				if( IPs.contains( p2Set.getString("A.fcIPString") ) )
					matchIP = true;

				if( matchMID == true )
					display += "MID match: " + p2Set.getInt("A.fnMachineID") + " ";
				if( matchIP == true )
					display += " IP match: " + p2Set.getString("A.fcIPString");

				if( display != "" ) {
					if( results < m_maxRecords ) {
						m_botAction.sendChatMessage( display );
						results++;
					}
				}
			}

            m_botAction.SQLClose( p1Set );
            m_botAction.SQLClose( p2Set );
			if( results == 0 )
				m_botAction.sendChatMessage( "No matching IPs or MIDs found." );
			if( results > m_maxRecords )
				m_botAction.sendChatMessage( results - m_maxRecords + " records not shown.  !maxrecords # to show (current: " + m_maxRecords + ")" );
		}
		catch(SQLException e)
		{
			throw new RuntimeException("ERROR: Cannot connect to database.");
		}

	}

	public void doHelpCmd(String sender)
	{
		String[] message =
		{
		        "ALIAS CHAT COMMANDS: ",
				"!AltNick  <PlayerName>         - Alias by <PlayerName>",
				"!AltIP    <IP>                 - Alias by <IP>",
				"!AltMID   <MacID>              - Alias by <MacID>",
				"!Info     <PlayerName>         - Shows stored info of <PlayerName>",
				"!Compare  <Player1>:<Player2>  - Compares and shows matches",
				"!MaxResults <#>                - Changes the max. number of results to return",
				"!NameWatch <Name>:<reason>     - Watches logins for <Name> with the specified <reason>",
				"!NameWatch <Name>              - Disables the login watch for <Name>",
				"!IPWatch   <IP>:<reason>       - Watches logins for <IP> with the specified <reason>",
				"!IPWatch   <IP>                - Disables the login watch for <IP>",
				"!MIDWatch  <MID>:<reason>      - Watches logins for <MID> with the specified <reason>",
				"!MIDWatch  <MID>               - Disables the login watch for <MID>",
				"!ClearNameWatch                - Clears all login watches for names",
				"!ClearIPWatch                  - Clears all login watches for IPs",
				"!ClearMIDWatch                 - Clears all login watches for MIDs",
				"!ShowWatches                   - Shows all current login watches",
				"!SortByName / !SortByDate      - Selects sorting method",
				"!update                        - Updates TWDOps list",
				"!aliasop <name>                - Gives <name> alias access",
				"!aliasdeop <name>              - Removes alias access for <name>",     
				"!listaliasops (!lao)           - Lists current alias-ops"      
				
		};
		m_botAction.smartPrivateMessageSpam(sender, message);
	}

	public void doRecordInfoCmd(String sender)
	{
		m_botAction.sendChatMessage("Players recorded in the hashmap: " + (justAdded.size() + deleteNextTime.size()));
	}

	public void doAltTWLCmd(String argString)
	{
		try
		{
			ResultSet resultSet = m_botAction.SQLQuery(DATABASE,
					"SELECT U2.fcUserName, T.fcTeamName " +
					"FROM tblAlias A1, tblAlias A2, tblUser U1, tblUser U2, tblTeam T, tblTeamUser TU " +
					"WHERE U1.fcUserName = '" + Tools.addSlashesToString(argString) + "' " +
					"AND A1.fnUserID = U1.fnUserID " +
					"AND A1.fnIP = A2.fnIP " +
					"AND A1.fnMachineID = A2.fnMachineID " +
					"AND A2.fnUserID = U2.fnUserID " +
					"AND TU.fnUserID = U2.fnUserID " +
					"AND TU.fnCurrentTeam = 1 " +
					"AND TU.fnTeamID = T.fnTeamID " +
					"AND T.fdDeleted = '0000-00-00 00:00:00' " +
			"ORDER BY U2.fcUserName, T.fcTeamName");

			int results = 0;
			String lastName = "";
			String currName;
			if(resultSet == null)
				throw new RuntimeException("ERROR: Cannot connect to database.");
			for(; resultSet.next(); results++)
			{
				if( results <= m_maxRecords ) {
					currName = resultSet.getString("U2.fcUserName");
					if(!currName.equalsIgnoreCase(lastName))
						m_botAction.sendChatMessage("Name: " + padString(currName, 25) + " Squad: " + resultSet.getString("T.fcTeamName"));
					lastName = currName;
				}
			}
                        m_botAction.SQLClose( resultSet );
			if(results == 0)
				m_botAction.sendChatMessage("Player is not on a TWL squad.");
			if( results > m_maxRecords )
				m_botAction.sendChatMessage( results - m_maxRecords + " records not shown.  !maxrecords # to show (current: " + m_maxRecords + ")" );
		}
		catch(SQLException e)
		{
			throw new RuntimeException("ERROR: Cannot connect to database.");
		}
	}

	/**
	 *
	 */
	public void doMaxRecordsCmd( String maxRecords ) {
		try {
			m_maxRecords = Integer.parseInt( maxRecords );
			m_botAction.sendChatMessage( "Max. number of records to display set to " + m_maxRecords + "." );
		} catch (Exception e) {
			throw new RuntimeException("Please give a number.");
		}
	}

	/**
	 * Starts watching for an IP starting with a given string.
	 * @param IP IP to watch for
	 */
	public void doIPWatchCmd( String sender, String message ) {
	    String[] params = message.split(":");
	    String IP = params[0].trim();
	    
		if( watchedIPs.containsKey( IP ) && (params.length == 1 || params[1] == null || params[1].length() == 0)) {
			watchedIPs.remove( IP );
			m_botAction.sendChatMessage( "Login watching disabled for IPs starting with " + IP );
			saveWatches();
		} else if(params.length == 1 || params[1] == null || params[1].length() == 0){
		    m_botAction.sendChatMessage( "Please specify a comment/reason after the IP seperated by a : . For example, !IPWatch 123.123.123.9:Possible hacker .");
		} else { 
		    String comment = params[1].trim();
		    
		    if(watchedIPs.containsKey(IP)) {
		        m_botAction.sendChatMessage( "Login watching for (partial) IP "+IP+" reason changed.");
		    } else {
		        m_botAction.sendChatMessage( "Login watching enabled for IPs starting with " + IP );
		    }
			watchedIPs.put( IP, sender + ": "+comment );
			saveWatches();
		}
	}
	
	public void doAddAliasOp(String sender, String message) {
	    if (!m_botAction.getOperatorList().isSmod(sender)){
	        m_botAction.sendChatMessage( "Only Smods or higher can add alias-ops.");
	        return;
	    }
	    
	    if (aliasops.containsKey(message.toLowerCase())) {
	        m_botAction.sendChatMessage( message + " is already on the alias-ops list.");
	        return;
	    } else {
	        aliasops.put(message.toLowerCase(), message);
	    }
	    
	    try
        {
            String query = "INSERT INTO tblAliasOps " +
                    "(User) " +
                    "VALUES ('" + Tools.addSlashes(message) + "')";
            ResultSet r = m_botAction.SQLQuery(DATABASE, query);
            m_botAction.SQLClose( r );
            m_botAction.sendChatMessage(message + " has been added to the alias-ops list.");
        }
        catch(SQLException e)
        {
            throw new RuntimeException("ERROR: Unable to add " + message + " to the alias-ops list on the database.");
        }
	}
	
	public void doRemAliasOp(String sender, String message) {
        if (!m_botAction.getOperatorList().isSmod(sender)){
            m_botAction.sendChatMessage( "Only Smods or higher can remove alias-ops.");
            return;
        }
        
        if (!aliasops.containsKey(message.toLowerCase())) {
            m_botAction.sendChatMessage( message + " could not be found on the alias-ops list.");
            return;
        } else {
            aliasops.remove(message.toLowerCase());
        }
        
        try
        {
            String query = "DELETE FROM tblAliasOps WHERE User = '" + Tools.addSlashes(message) + "'";
            ResultSet r = m_botAction.SQLQuery(DATABASE, query);
            m_botAction.SQLClose( r );
            m_botAction.sendChatMessage(message + " has been removed from the alias-ops list.");
        }
        catch(SQLException e)
        {
            throw new RuntimeException("ERROR: Unable to remove " + message + " from the alias-ops list on the database.");
        }
    }
	
	public void doListAliasOps() { 
	    int counter = 0;
	    String output = "";
	    
	    m_botAction.sendChatMessage("AliasOps:");
	    
	    for(String i : aliasops.values()) {
	        output += i + ", ";
	        counter++;
	        
	        if (counter == 6) {
	            counter = 0;
	            if (aliasops.size() <= 6)
	                m_botAction.sendChatMessage(output.substring(0, output.length() - 2));
	            else
	                m_botAction.sendChatMessage(output);
	            output = "";
	        }
	    }
	    
	    if (!output.isEmpty())
	        m_botAction.sendChatMessage(output.substring(0, output.length() - 2));
	    
	}

	/**
	 * Starts watching for a name to log on.
	 * @param name Name to watch for
	 */
	public void doNameWatchCmd( String sender, String message ) {
	    String[] params = message.split(":");
	    String name = params[0].trim();
	    
		if( watchedNames.containsKey( name.toLowerCase() ) && (params.length == 1 || params[1] == null || params[1].length() == 0)) {
			watchedNames.remove( name.toLowerCase() );
			m_botAction.sendChatMessage( "Login watching disabled for '" + name + "'." );
			saveWatches();
		} else if(params.length == 1 || params[1] == null || params[1].length() == 0) {
		    m_botAction.sendChatMessage( "Please specify a comment/reason after the name seperated by a : . For example, !NameWatch Pure_Luck:Bad boy .");
		} else {
		    String comment = params[1].trim();
		    
		    if(watchedNames.containsKey(name.toLowerCase())) {
		        m_botAction.sendChatMessage( "Login watching for '"+name+"' reason changed.");
		    } else {
		        m_botAction.sendChatMessage( "Login watching enabled for '" + name + "'." );
		    }
			watchedNames.put( name.toLowerCase(), sender + ": "+comment );
			saveWatches();
		}
	}

	/**
	 * Starts watching for a given MacID.
	 * @param MID MID to watch for
	 */
	public void doMIDWatchCmd( String sender, String message ) {
	    String[] params = message.split(":");
	    String MID = params[0].trim();
	    
		if( watchedMIDs.containsKey( MID ) && (params.length == 1 || params[1] == null || params[1].length() == 0)) {
			watchedMIDs.remove( MID );
			m_botAction.sendChatMessage( "Login watching disabled for MID: " + MID );
			saveWatches();
		} else if(params.length == 1 || params[1] == null || params[1].length() == 0) {
		    m_botAction.sendChatMessage( "Please specify a comment/reason after the MID seperated by a : . For example, !MIDWatch 777777777:I like the number .");
		} else {
		    String comment = params[1].trim();
		    
		    if(watchedMIDs.containsKey(MID)) {
		        m_botAction.sendChatMessage( "Login watching for MID "+MID+" reason changed.");
		    } else {
		        m_botAction.sendChatMessage( "Login watching enabled for MID: " + MID );
		    }
			watchedMIDs.put( MID, sender + ": "+comment );
			saveWatches();
			
		}
	}

	/**
	 * Stops all IP watching.
	 */
	public void doClearIPWatchCmd( ) {
		watchedIPs.clear();
		m_botAction.sendChatMessage( "All watched IPs cleared." );
		saveWatches();
	}

	/**
	 * Stops all name watching.
	 */
	public void doClearNameWatchCmd( ) {
		watchedNames.clear();
		m_botAction.sendChatMessage( "All watched names cleared." );
		saveWatches();
	}

	/**
	 * Stops all MacID watching.
	 */
	public void doClearMIDWatchCmd( ) {
		watchedMIDs.clear();
		m_botAction.sendChatMessage( "All watched MIDs cleared." );
		saveWatches();
	}

	/**
	 * Shows current watches.
	 */
	public void doShowWatchesCmd( ) {
	    if(watchedIPs.size() == 0) {
	        m_botAction.sendChatMessage( "IP:   (none)");
	    }
	    for(String IP:watchedIPs.keySet()) {
	        m_botAction.sendChatMessage( "IP:   " + IP + "  ( "+watchedIPs.get(IP)+" )" );
	    }
	    
		if(watchedMIDs.size() == 0) {
            m_botAction.sendChatMessage( "MID:  (none)");
        }
		for(String MID:watchedMIDs.keySet()) {
		    m_botAction.sendChatMessage( "MID:  " + MID + "  ( "+watchedMIDs.get(MID)+" )" );
		}
		
		if(watchedNames.size() == 0 ) {
            m_botAction.sendChatMessage( "Name: (none)");
        }
		for(String Name:watchedNames.keySet()) {
		    m_botAction.sendChatMessage( "Name: " + Name + "  ( "+watchedNames.get(Name)+" )");
		}
	}

	public void handleChatMessage(String sender, String message)
	{
		String command = message.toLowerCase();
		
		/*
		 * Extra check for smod and twdop added
		 * -fantus
		 */
		if (!m_botAction.getOperatorList().isSmod(sender) && !isTWDOp(sender) && !isAliasOp(sender)) {
		    return;
		}
		
		

		try
		{
			if(command.equals("!recordinfo"))
				doRecordInfoCmd(sender);
			else if(command.equals("!help"))
				doHelpCmd(sender);
			else if(command.startsWith("!altnick "))
				doAltNickCmd(message.substring(9).trim());
			else if(command.startsWith("!altip "))
				doAltIpCmd(message.substring(7).trim());
			else if(command.startsWith("!altmid "))
				doAltMacIdCmd(message.substring(8).trim());
//			else if(command.startsWith("!alttwl "))
//				doAltTWLCmd(message.substring(8).trim());
			else if(command.startsWith("!info "))
				doInfoCmd(message.substring(6).trim());
			else if(command.startsWith("!compare "))
				doCompareCmd(message.substring(9).trim());
			else if(command.startsWith("!maxrecords "))
				doMaxRecordsCmd(message.substring(12).trim());
			else if(command.startsWith("!ipwatch "))
				doIPWatchCmd(sender, message.substring(9).trim());
			else if(command.startsWith("!namewatch "))
				doNameWatchCmd(sender, message.substring(11).trim());
			else if(command.startsWith("!midwatch "))
				doMIDWatchCmd(sender, message.substring(10).trim());
			else if(command.equals("!clearipwatch"))
				doClearIPWatchCmd();
			else if(command.equals("!clearnamewatch"))
				doClearNameWatchCmd();
			else if(command.equals("!clearmidwatch"))
				doClearMIDWatchCmd();
			else if(command.equals("!showwatches"))
				doShowWatchesCmd();
			else if(command.equals("!sortbyname")) {
				m_sortByName = true;
				m_botAction.sendChatMessage( "Sorting !alt cmds by name first." );
			}
			else if(command.equals("!sortbydate")) {
				m_sortByName = false;
				m_botAction.sendChatMessage( "Sorting !alt cmds by date first." );
			}
			else if(command.equals("!update")) {
			    updateTWDOps();
			    updateAliasOps();
			    m_botAction.sendChatMessage( "Updating twdop & alias-op lists." );
			}
			else if(command.equals("!listaliasops") || command.equals("!lao"))
			    doListAliasOps();
			else if(command.startsWith("!aliasop "))
			    doAddAliasOp(sender, message.substring(8).trim());
			else if(command.startsWith("!aliasdeop "))
			    doRemAliasOp(sender, message.substring(10).trim());
		}
		catch(Exception e)
		{
			m_botAction.sendChatMessage(e.getMessage());
		}
	}

	public void handleEvent(Message event)
	{
		String sender = event.getMessager();
		String message = event.getMessage();
		int messageType = event.getMessageType();

		if(messageType == Message.CHAT_MESSAGE)
			handleChatMessage(sender, message);
	}

	public void gotRecord(String argString)
	{
		if(justAdded.contains(argString.toLowerCase()) || deleteNextTime.contains(argString.toLowerCase()))
			return;
			
		StringTokenizer recordArgs = new StringTokenizer(argString, ":");
		if(recordArgs.countTokens() != 3)
			throw new IllegalArgumentException("ERROR: Could not write player information.");
		String playerName = recordArgs.nextToken();
		String playerIP = recordArgs.nextToken();
		String playerMacID = recordArgs.nextToken();

		checkName( playerName, playerIP, playerMacID );
		checkIP( playerName, playerIP, playerMacID );
		checkMID( playerName, playerIP, playerMacID );

		try
		{
			recordInfo(playerName, playerIP, playerMacID);
			justAdded.add(playerName.toLowerCase());
		}
		catch(Exception e)
		{
		}
	}

	/**
	 * Check if a name is being watched for, and notify on chat if so.
	 * @param name Name to check
	 * @param IP IP of player
	 * @param MacId MacID of player
	 */
	public void checkName( String name, String IP, String MacID ) {
		if( watchedNames.containsKey( name.toLowerCase() ) ) {
			m_botAction.sendChatMessage( "NAMEWATCH: '" + name + "' logged in.  (IP: " + IP + ", MID: " + MacID + ")" );
			m_botAction.sendChatMessage( "           "+watchedNames.get(name.toLowerCase()));
		}
	}

	/**
	 * Check if an IP is being watched for, and notify on chat if so.
	 * @param name Name of player
	 * @param IP IP to check
	 * @param MacId MacID of player
	 */
	public void checkIP( String name, String IP, String MacID ) {
	    for(String IPfragment:watchedIPs.keySet()) {
	        if( IP.startsWith( IPfragment ) ) {
                m_botAction.sendChatMessage( "IPWATCH: Match on '" + name + "' - " + IP + " (matches " + IPfragment + "*)  MID: " + MacID );
                m_botAction.sendChatMessage( "         "+watchedIPs.get(IPfragment));
            }
	    }
	}

	/**
	 * Check if an MID is being watched for, and notify on chat if so.
	 * @param name Name of player
	 * @param IP IP of player
	 * @param MacId MacID to check
	 */
	public void checkMID( String name, String IP, String MacID ) {
		if( watchedMIDs.containsKey( MacID ) ) {
			m_botAction.sendChatMessage( "MIDWATCH: Match on '" + name + "' - " + MacID + "  IP: " + IP );
			m_botAction.sendChatMessage( "          "+watchedMIDs.get( MacID ));
		}
	}

	/**
	 * This method handles an InterProcess event.
	 *
	 * @param event is the IPC event to handle.
	 */
	public void handleEvent(InterProcessEvent event)
	{
		// If the event.getObject() is anything else then the IPCMessage (pubbotchatIPC f.ex) then return
		if(event.getObject() instanceof IPCMessage == false) {
			return;
		}

		IPCMessage ipcMessage = (IPCMessage) event.getObject();
		String botName = m_botAction.getBotName();
		String message = ipcMessage.getMessage();

		try
		{
			if(botName.equals(ipcMessage.getRecipient())) {
				if(message.startsWith("info "))
					gotRecord(message.substring(5));
			}
		}
		catch(Exception e)
		{
			m_botAction.sendChatMessage(e.getMessage());
		}
	}

	/**
	 * This method handles the destruction of this module.
	 */

	public void cancel()
	{
        m_botAction.cancelTask(clearRecordTask);
	}

	private void recordInfo(String playerName, String playerIP, String playerMacID)
	{
		DBPlayerData playerData = new DBPlayerData(m_botAction, DATABASE, playerName, true);
		int userID = playerData.getUserID();
		int aliasID = getAliasID(userID, playerIP, playerMacID);

		if(aliasID == -1)
			createAlias(userID, playerIP, playerMacID);
		else
			updateAlias(aliasID);
	}

	private int getAliasID(int userID, String playerIP, String playerMacID)
	{
		try
		{
			long ip32Bit = make32BitIp(playerIP);
			ResultSet resultSet = m_botAction.SQLQuery(DATABASE,
					"SELECT * " +
					"FROM tblAlias " +
					"WHERE fnUserID = " + userID + " " +
					"AND fnIP = " + ip32Bit + " " +
					"AND fnMachineID = " + playerMacID);
			if(!resultSet.next())
				return -1;
			int results = resultSet.getInt("fnAliasID");
                        m_botAction.SQLClose( resultSet );
			return results;
		}
		catch(SQLException e)
		{
			throw new RuntimeException("ERROR: Unable to access database.");
		}
	}

	private void createAlias(int userID, String playerIP, String playerMacID)
	{
		try
		{
			long ip32Bit = make32BitIp(playerIP);
			String query = "INSERT INTO tblAlias " +
					"(fnUserID, fcIPString, fnIP, fnMachineID, fnTimesUpdated, fdRecorded, fdUpdated) " +
					"VALUES (" + userID + ", '" + playerIP + "', " + ip32Bit + ", " + playerMacID + ", 1, NOW(), NOW())";
			ResultSet r = m_botAction.SQLQuery(DATABASE, query);
			m_botAction.SQLClose( r );
		}
		catch(SQLException e)
		{
			throw new RuntimeException("ERROR: Unable to create alias entry.");
		}
	}

	private void updateAlias(int aliasID)
	{
		try
		{
			ResultSet r = m_botAction.SQLQuery(DATABASE,
					"UPDATE tblAlias " +
					"SET fnTimesUpdated = fnTimesUpdated + 1, fdUpdated = NOW() " +
					"WHERE fnAliasID = " + aliasID);
			m_botAction.SQLClose( r );
		}
		catch(SQLException e)
		{
			throw new RuntimeException("ERROR: Unable to update alias entry.");
		}
	}
	
	private void updateTWDOps() {
	    try {
	        ResultSet r = m_botAction.SQLQuery(DATABASE, "SELECT tblUser.fcUsername FROM `tblUserRank`, `tblUser` WHERE `fnRankID` = '14' AND tblUser.fnUserID = tblUserRank.fnUserID");
	        
	        if (r == null) {
	            return;
	        }
	        
	        twdops.clear();
	        
	        while(r.next()) {
	            String name = r.getString("fcUsername");
	            twdops.put(name.toLowerCase(), name);
	        }
	        
	        m_botAction.SQLClose( r );
	    } catch (SQLException e) {
	        throw new RuntimeException("ERROR: Unable to update twdop list.");
	    }
	}
	
	private void updateAliasOps() {
        try {
            ResultSet r = m_botAction.SQLQuery(DATABASE, "SELECT User FROM `tblAliasOps`");
            
            if (r == null) {
                return;
            }
            
            aliasops.clear();
            
            while(r.next()) {
                String name = r.getString("User");
                aliasops.put(name.toLowerCase(), name);
            }
            
            m_botAction.SQLClose( r );
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Unable to update alias-op list.");
        }
    }	
	
	private void loadWatches() {
	    BotSettings cfg = m_botAction.getBotSettings();
        boolean loop = true;
        int i = 1;
	    
	    watchedIPs.clear();
	    watchedMIDs.clear();
	    watchedNames.clear();
	    
	    // Load the IP watches from the configuration
	    while(loop) {
	        String IPWatch = cfg.getString("IPWatch"+i);
	        if(IPWatch != null && IPWatch.trim().length()>0) {
	            String[] IPWatchSplit = IPWatch.split(":",2);
	            if(IPWatchSplit.length == 2)       // Check for corrupted data
	                watchedIPs.put(IPWatchSplit[0], IPWatchSplit[1]);
	            i++;
	        } else {
	            loop = false;
	        }
	    }
	    
	    // Load the MID watches from the configuration
	    loop = true;
	    i =1;
	    
        while(loop) {
            String MIDWatch = cfg.getString("MIDWatch"+i);
            if(MIDWatch != null && MIDWatch.trim().length()>0) {
                String[] MIDWatchSplit = MIDWatch.split(":",2);
                if(MIDWatchSplit.length == 2)       // Check for corrupted data
                    watchedMIDs.put(MIDWatchSplit[0], MIDWatchSplit[1]);
                i++;
            } else {
                loop = false;
            }
        }
        
        // Load the Name watches from the configuration
        loop = true;
        i =1;
        
        while(loop) {
            String NameWatch = cfg.getString("NameWatch"+i);
            if(NameWatch != null && NameWatch.trim().length()>0) {
                String[] NameWatchSplit = NameWatch.split(":",2);
                if(NameWatchSplit.length == 2)       // Check for corrupted data
                    watchedNames.put(NameWatchSplit[0], NameWatchSplit[1]);
                i++;
            } else {
                loop = false;
            }
        }
	    
        // Done loading watches
	}
	
	private void saveWatches() {
	    BotSettings cfg = m_botAction.getBotSettings();
	    boolean loop = true;
	    int i = 1;
	    
	    // Save IP watches
	    for(String IP:watchedIPs.keySet()) {
	        cfg.put("IPWatch"+i, IP+":"+watchedIPs.get(IP));
	        i++;
	    }
	    // Clear any other still stored IP watches
	    while(loop) {
	        if(cfg.getString("IPWatch"+i) != null) {
	            cfg.remove("IPWatch"+i);
	            i++;
	        } else {
	            loop = false;
	        }
	    }
	    
	    i = 1;
	    loop = true;
	    
	    // Save MID watches
	    for(String MID:watchedMIDs.keySet()) {
	        cfg.put("MIDWatch"+i, MID+":"+watchedMIDs.get(MID));
	        i++;
	    }
	    // Clear any other still stored MID watches
	    while(loop) {
	        if(cfg.getString("MIDWatch"+i) != null) {
	            cfg.remove("MIDWatch"+i);
	            i++;
	        } else {
	            loop = false;
	        }
	    }
	    
	    i = 1;
	    loop = true;
	    
	    // Save Name watches
        for(String Name:watchedNames.keySet()) {
            cfg.put("NameWatch"+i, Name+":"+watchedNames.get(Name));
            i++;
        }
        // Clear any other still stored MID watches
        while(loop) {
            if(cfg.getString("NameWatch"+i) != null) {
                cfg.remove("NameWatch"+i);
                i++;
            } else {
                loop = false;
            }
        }
        
        cfg.save();
	}

	/**
	 * This method pads the string with whitespace.
	 *
	 * @param string is the string to pad.
	 * @param spaces is the size of the resultant string.
	 * @return the string padded with spaces to fit into spaces characters.  If
	 * the string is too long to fit, it is truncated.
	 */
	private String padString(String string, int spaces)
	{
		int whitespaces = spaces - string.length();

		if(whitespaces < 0)
			return string.substring(spaces);

		StringBuffer stringBuffer = new StringBuffer(string);
		for(int index = 0; index < whitespaces; index++)
			stringBuffer.append(' ');
		return stringBuffer.toString();
	}

	/**
	 * This method clears out RecordTimes that have been recorded later than
	 * RECORD_DELAY so the info for those players can be recorded again.
	 */
	private class ClearRecordTask extends TimerTask
	{
		public void run()
		{
			deleteNextTime.clear();
			deleteNextTime.addAll(justAdded);
			justAdded.clear();
		}
	}
}