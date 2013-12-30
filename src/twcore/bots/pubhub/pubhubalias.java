package twcore.bots.pubhub;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.TreeMap;

import twcore.bots.PubBotModule;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.events.FileArrived;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.stats.DBPlayerData;
import twcore.core.util.Hider;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

/**
 * THIS CODE SERIOUSLY NEEDS TO BE REFACTORED!! - Cpt
 * 
 * We're working on it, Guano... we're working on it. :>   - qan
 */

public class pubhubalias extends PubBotModule {
    public static final String DATABASE = "website";
    public static final String ARCHIVE = "archive";
    public static final int REMOVE_DELAY = 3 * 60 * 60 * 1000;
    public static final int CLEAR_DELAY = 3 * 60 * 1000;
    public static final int DEFAULT_DAYS = 180;

    private static final String NAME_FIELD = "fcUserName";
    private static final String IP_FIELD = "fcIPString";
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
    
    public final String TWChatMID = "1693149144";

    
    /** Sorting options for !showwatches and !showmywatches */
    private static enum SortField { NONE, DATE, TRIGGER, ISSUER };

    private SimpleDateFormat sdf = new SimpleDateFormat("MM/dd");

    private Set<String> justAdded;
    private Set<String> deleteNextTime;
    private Map<String, WatchComment> watchedIPs;
    private Map<String, WatchComment> watchedNames;
    private Map<String, WatchComment> watchedMIDs;
    private Map<String, WatchComment> watchedLNames;    // Watched names starting with String.
    private Map<String, WatchComment> watchedRNames;    // Watched names ending with String.
    private Map<String, WatchComment> watchedPNames;    // Watched names containing String.
    //    < IP , Comment >
    //    < MID, Comment >
    //    <Name, Comment >
    private ClearRecordTask clearRecordTask;

    private int m_maxRecords = 15;
    private boolean m_sortByName = false;

    private HashSet<String> twdops = new HashSet<String>();
    private HashSet<String> aliasops = new HashSet<String>();
    private HashSet<String> bangops = new HashSet<String>();
    private Hider hider;
    
    private boolean privateAliases;

    /**
     * This method initializes the module.
     */
    public void initializeModule() {
        justAdded = Collections.synchronizedSet(new HashSet<String>());
        deleteNextTime = Collections.synchronizedSet(new HashSet<String>());
        watchedIPs = Collections.synchronizedMap(new HashMap<String, WatchComment>());
        watchedNames = Collections.synchronizedMap(new HashMap<String, WatchComment>());
        watchedMIDs = Collections.synchronizedMap(new HashMap<String, WatchComment>());
        watchedLNames = Collections.synchronizedMap(new HashMap<String, WatchComment>());
        watchedRNames = Collections.synchronizedMap(new HashMap<String, WatchComment>());
        watchedPNames = Collections.synchronizedMap(new HashMap<String, WatchComment>()); 
        clearRecordTask = new ClearRecordTask();
        hider = new Hider(m_botAction);

        m_botAction.scheduleTaskAtFixedRate(clearRecordTask, CLEAR_DELAY, CLEAR_DELAY);

        loadWatches();

        updateTWDOps();
        updateBangOps();
        updateAliasOps();
    }

    private boolean isTWDOp(String name) {
        return twdops.contains(name.toLowerCase());
    }

    private boolean isBangOp(String name) {
        return bangops.contains(name.toLowerCase());
    }
    
    private boolean isAliasOp(String name) {
        return aliasops.contains(name.toLowerCase());
    }

    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.FILE_ARRIVED);
    }
    
    public void handleEvent(FileArrived event) {
        hider.handleEvent(event);
    }

    private void doORIGAltNickCmd(String sender, String playerName, boolean all) {
        try {
            String[] headers = { NAME_FIELD, IP_FIELD, MID_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD };

            //long t = System.currentTimeMillis();
            String ipResults = getSubQueryResultString("SELECT DISTINCT(fnIP) FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID WHERE fcUserName = '"
                    + Tools.addSlashes(playerName) + "'", "fnIP");

            String midResults = getSubQueryResultString("SELECT DISTINCT(fnMachineId) FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID WHERE fcUserName = '"
                    + Tools.addSlashes(playerName) + "'", "fnMachineId");

            String queryString = "SELECT * FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID WHERE fnIP IN " + ipResults + " AND fnMachineID IN "
                    + midResults + " " + getOrderBy();

            if (ipResults == null || midResults == null)
                m_botAction.sendChatMessage("Player not found in database.");
            else if (all)
                displayAltNickAllResults(sender, queryString, headers, "fcUserName");
            else
                displayAltNickResults(sender, playerName, queryString, headers, "fcUserName");
            //m_botAction.sendSmartPrivateMessage(sender, "Execution time: " + (System.currentTimeMillis() - t) + "ms" );

        } catch (SQLException e) {
            throw new RuntimeException("SQL Error: " + e.getMessage(), e);
        }
    }
    

    /*
    private void doAltNick2Cmd(String sender, String playerName, boolean all) {
        Integer id = -1;
        try {
            ResultSet resultSet = m_botAction.SQLQuery(DATABASE, "SELECT fnUserID FROM `tblUser` WHERE fcUserName='" + Tools.addSlashes(playerName) + "'");
            if (resultSet == null)
                throw new RuntimeException("ERROR: Null result set returned; connection may be down.");

            if (resultSet.next())
                id = resultSet.getInt("fnUserID");

            if (id==null) {
                m_botAction.sendChatMessage("Player not found in database.");
                return;
            }
        } catch( SQLException e ) {
            m_botAction.sendChatMessage("ERROR: SQL Exception thrown.");
            return;
        }

        try {
            String[] headers = { NAME_FIELD, IP_FIELD, MID_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD };

            long t = System.currentTimeMillis();
            String ipResults = getSubQueryResultString("SELECT DISTINCT(fnIP) FROM `tblAlias` WHERE fnUserID='" + id + "'", "fnIP");
            String midResults = getSubQueryResultString("SELECT DISTINCT(fnMachineId) FROM `tblAlias` WHERE fnUserID='" + id + "'", "fnMachineId");
            String queryString = "SELECT fcUserName FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID WHERE (fnIP IN " + ipResults + " " + "AND fnMachineID IN "
                    + midResults + ")" + getOrderBy();
                    
            if (ipResults == null || midResults == null)
                m_botAction.sendChatMessage("Player not found in database.");
            else if (all)
                displayAltNickAllResults(sender, queryString, headers, "fcUserName");
            else
                displayAltNickResults(sender, playerName, queryString, headers, "fcUserName");
            m_botAction.sendSmartPrivateMessage(sender, "Execution time: " + (System.currentTimeMillis() - t) + "ms" );

        } catch (SQLException e) {
            throw new RuntimeException("SQL Error: " + e.getMessage(), e);
        }
    }
    */

    private void doAltNickCmd(String sender, String playerName, boolean all) {
        try {
            String[] headers = { NAME_FIELD, IP_FIELD, MID_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD };

            //long t = System.currentTimeMillis();
            String queryString = 
                //"SET @userId = (SELECT fnUserId FROM tblUser WHERE fcUserName='"
                //+ Tools.addSlashes(playerName) + "' LIMIT 0,1);"
                  " SELECT U.fcUserName, A.* FROM tblAlias A JOIN tblUser U ON A.fnUserId = U.fnUserId WHERE "
                + " fnIP IN ( SELECT DISTINCT(fnIP) FROM tblAlias WHERE fnUserId ="
                + " (SELECT fnUserId FROM tblUser WHERE fcUserName='" + Tools.addSlashes(playerName) + "' LIMIT 0,1) ) AND" 
                + " fnMachineId IN ( SELECT DISTINCT(fnMachineID) FROM tblAlias WHERE fnUserId ="
                + "(SELECT fnUserId FROM tblUser WHERE fcUserName='" + Tools.addSlashes(playerName) + "' LIMIT 0,1) ) "
                + getOrderBy();
            
            if (all)
                displayAltNickAllResults(sender, queryString, headers, "fcUserName");
            else
                displayAltNickResults(sender, playerName, queryString, headers, "fcUserName");
            //m_botAction.sendSmartPrivateMessage(sender, "Execution time: " + (System.currentTimeMillis() - t) + "ms" );

        } catch (SQLException e) {
            throw new RuntimeException("SQL Error: " + e.getMessage(), e);
        }
    }
    
    private void doAltNickOrCmd(String sender, String playerName, boolean all) {
        try {
            String[] headers = { NAME_FIELD, IP_FIELD, MID_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD };

            String ipResults = getSubQueryResultString("SELECT DISTINCT(fnIP) " + "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " + "WHERE fcUserName = '"
                    + Tools.addSlashes(playerName) + "'", "fnIP");

            String midResults = getSubQueryResultString("SELECT DISTINCT(fnMachineId) " + "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " + "WHERE fcUserName = '"
                    + Tools.addSlashes(playerName) + "'", "fnMachineId");

            String queryString = "SELECT * FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " + "WHERE (fnIP IN " + ipResults + " " + "OR fnMachineID IN "
                    + midResults + ") AND fnMachineID > 1 " + getOrderBy();

            if (ipResults == null || midResults == null)
                m_botAction.sendChatMessage("Player not found in database.");
            else if (all)
                displayAltNickAllResults(sender, queryString, headers, "fcUserName");
            else
                displayAltNickResults(sender, playerName, queryString, headers, "fcUserName");

        } catch (SQLException e) {
            throw new RuntimeException("SQL Error: " + e.getMessage(), e);
        }
    }

    private void doAltMacIdCmd(String sender, String playerMid) {
        if (!Tools.isAllDigits(playerMid)) {
            m_botAction.sendChatMessage("Command syntax error: Please use !altmid <number>");
            return;
        }

        try {
            String[] headers = { NAME_FIELD, IP_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD };

            displayAltNickResults(sender, null, "SELECT * " + "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " + "WHERE fnMachineId = " + playerMid + " " + getOrderBy(), headers, "fcUserName");
        } catch (SQLException e) {
            throw new RuntimeException("SQL Error: " + e.getMessage(), e);
        }
    }

    private String getOrderBy() {
        if (m_sortByName)
            return "ORDER BY fcUserName";
        return "ORDER BY fdUpdated DESC";
    }

    private void doAltIpCmdPartial(String sender, String stringPlayerIP) {
        try {
            String[] headers = { NAME_FIELD, IP_FIELD, MID_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD };
            String query = "SELECT * " + "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " + "WHERE fcIPString LIKE '" + stringPlayerIP + "%'" + " " + getOrderBy();

            displayAltNickResults(sender, null, query, headers, "fcUserName");

        } catch (SQLException e) {
            //throw new RuntimeException("SQL Error: "+e.getMessage(), e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doAltIpCmd(String sender, String playerIp) {
        try {
            String[] headers = { NAME_FIELD, MID_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD };
            long ip32Bit = make32BitIp(playerIp);

            displayAltNickResults(sender, null, "SELECT * " + "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " + "WHERE fnIp LIKE " + ip32Bit + " " + getOrderBy(), headers, "fcUserName");
        } catch (SQLException e) {
            throw new RuntimeException("SQL Error: " + e.getMessage(), e);
        }
    }

    private void doInfoCmd(String sender, String playerName) {
        try {
            String[] headers = { IP_FIELD, MID_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD };
            String queryString = "SELECT * " + "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " + "WHERE fcUserName = '" + Tools.addSlashesToString(playerName) + "' " + getOrderBy();
            ResultSet resultSet = m_botAction.SQLQuery(DATABASE, queryString);
            int numResults = 0;

            if (resultSet == null)
                throw new RuntimeException("ERROR: Null result set returned; connection may be down.");

            boolean hide = hider.isHidden(playerName);
            
            ArrayList<String> results = new ArrayList<String>();
            results.add(getResultHeaders(headers));
            while (resultSet.next()) {
                if (!hide && numResults <= m_maxRecords)
                    results.add(getResultLine(resultSet, headers));
                numResults++;
            }

            if (numResults > m_maxRecords)
                results.add(numResults - m_maxRecords + " records not shown.  !maxrecords # to show (current: " + m_maxRecords + ")");
            else
                results.add("Altnick returned " + numResults + " results.");
            m_botAction.SQLClose(resultSet);
            if (privateAliases)
                m_botAction.smartPrivateMessageSpam(sender, results.toArray(new String[results.size()]));
            else
                for (String message : results)
                    m_botAction.sendChatMessage(message);
        } catch (SQLException e) {
            throw new RuntimeException("SQL Error: " + e.getMessage(), e);
        }
    }

    private void doInfoAllCmd(String sender, String playerName) {
        try {
            String[] headers = { IP_FIELD, MID_FIELD, TIMES_UPDATED_FIELD, LAST_UPDATED_FIELD };
            displayAltNickAllResults(sender, 
                    "SELECT * "
                            + "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID "
                            + "WHERE fcUserName = '" + Tools.addSlashesToString(playerName) + "' "
                            + getOrderBy(), headers, null);
        } catch (SQLException e) {
            throw new RuntimeException("SQL Error: " + e.getMessage(), e);
        }
    }

    private long make32BitIp(String ipString) {
        StringTokenizer stringTokens = new StringTokenizer(ipString, ".");
        String ipPart;
        long ip32Bit = 0;

        try {
            while (stringTokens.hasMoreTokens()) {
                ipPart = stringTokens.nextToken();
                ip32Bit = ip32Bit * 256 + Integer.parseInt(ipPart);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Error: Malformed IP Address.");
        }

        return ip32Bit;
    }

    private void displayAltNickAllResults(String sender, String queryString, String[] headers, String uniqueField) throws SQLException {
        ResultSet resultSet = m_botAction.SQLQuery(DATABASE, queryString);
        HashSet<String> prevResults = new HashSet<String>();
        String curResult = null;
        int numResults = 0;

        if (resultSet == null)
            throw new RuntimeException("ERROR: Null result set returned; connection may be down.");

        ArrayList<String> results = new ArrayList<String>();
        results.add(getResultHeaders(headers));
        
        while (resultSet.next()) {
            if (uniqueField != null)
                curResult = resultSet.getString(uniqueField);

            if (uniqueField == null || !prevResults.contains(curResult)) {
                if (numResults <= m_maxRecords)
                    results.add(getResultLine(resultSet, headers));
                prevResults.add(curResult);
                numResults++;
            }
        }

        if (numResults > m_maxRecords)
            results.add(numResults - m_maxRecords + " records not shown.  !maxrecords # to show (current: " + m_maxRecords + ")");
        else
            results.add("Altnick returned " + numResults + " results.");
        m_botAction.SQLClose(resultSet);
        m_botAction.smartPrivateMessageSpam(sender, results.toArray(new String[results.size()]));
    }

    private void displayAltNickResults(String sender, String player, String queryString, String[] headers, String uniqueField) throws SQLException {
        ResultSet resultSet = m_botAction.SQLQuery(DATABASE, queryString);
        HashSet<String> prevResults = new HashSet<String>();
        String curResult = null;
        int totalResults = 0;
        int shownResults = 0;
        int hiddenResults = 0;

        if (resultSet == null)
            throw new RuntimeException("ERROR: Null result set returned; connection may be down.");
        
        boolean hide = false;
        if (player != null && hider.isHidden(player))
            hide = true;

        ArrayList<String> results = new ArrayList<String>();
        results.add(getResultHeaders(headers));
        while (resultSet.next()) {
            if (uniqueField != null)
                curResult = resultSet.getString(uniqueField);

            if (uniqueField == null || !prevResults.contains(curResult)) {
                if (hide || (hider.isHidden(curResult.substring(0, (curResult.length() > 24 ? 24 : curResult.length())).trim()))) {
                    hiddenResults++;
                } else {                
                    if (shownResults < m_maxRecords) {
                        results.add(getResultLine(resultSet, headers));
                        shownResults++;
                    }
                }
                prevResults.add(curResult);
                totalResults++;
            }
        }

        if (shownResults >= m_maxRecords)
            results.add(shownResults + " results shown, " + (totalResults - shownResults) + " repressed.  !maxrecords # to show if available (current: " + m_maxRecords + ")");
        else {
            if (opList.isSysopExact(sender)) 
                results.add(shownResults + " results shown (" + (hiddenResults) + " hidden, " + (totalResults - hiddenResults) + " duplicates repressed)" );
            else
                results.add("All " + shownResults + " results shown (" + (totalResults - hiddenResults) + " duplicates repressed)" );
        }
        m_botAction.SQLClose(resultSet);
        if (privateAliases)
            m_botAction.smartPrivateMessageSpam(sender, results.toArray(new String[results.size()]));
        else
            for (String message : results)
                m_botAction.sendChatMessage(message);
    }

    /*
    private void displayAltNickResults(String player, String queryString, String[] headers) throws SQLException {
        displayAltNickResults(player, queryString, headers, null);
    }
    */

    private String getResultHeaders(String[] displayFields) {
        StringBuffer resultHeaders = new StringBuffer();
        String displayField;
        String fieldHeader;
        int padding;

        for (int index = 0; index < displayFields.length; index++) {
            displayField = displayFields[index];
            padding = getFieldPadding(displayField);
            fieldHeader = getFieldHeader(displayField);

            resultHeaders.append(padString(fieldHeader, padding));
        }
        return resultHeaders.toString().trim();
    }

    private String getResultLine(ResultSet resultSet, String[] displayFields) throws SQLException {
        StringBuffer resultLine = new StringBuffer();
        String displayField;
        String fieldValue;
        int padding;

        for (int index = 0; index < displayFields.length; index++) {
            displayField = displayFields[index];
            padding = getFieldPadding(displayField);

            if (displayField.startsWith(DATE_FIELD_PREFIX))
                fieldValue = resultSet.getDate(displayField).toString();
            else
                fieldValue = resultSet.getString(displayField);
            resultLine.append(padString(fieldValue, padding));
        }
        return resultLine.toString().trim();
    }

    private String getFieldHeader(String displayField) {
        if (displayField.equalsIgnoreCase(NAME_FIELD))
            return NAME_HEADER;
        if (displayField.equalsIgnoreCase(IP_FIELD))
            return IP_HEADER;
        if (displayField.equalsIgnoreCase(MID_FIELD))
            return MID_HEADER;
        if (displayField.equalsIgnoreCase(TIMES_UPDATED_FIELD))
            return TIMES_UPDATED_HEADER;
        if (displayField.equalsIgnoreCase(LAST_UPDATED_FIELD))
            return DATE_UPDATED_HEADER;
        return DEFAULT_HEADER;
    }

    private int getFieldPadding(String displayField) {
        if (displayField.equalsIgnoreCase(NAME_FIELD))
            return NAME_PADDING;
        if (displayField.equalsIgnoreCase(IP_FIELD))
            return IP_PADDING;
        if (displayField.equalsIgnoreCase(MID_FIELD))
            return MID_PADDING;
        if (displayField.equalsIgnoreCase(TIMES_UPDATED_FIELD))
            return TIMES_UPDATED_PADDING;
        if (displayField.equalsIgnoreCase(LAST_UPDATED_FIELD))
            return DATE_UPDATED_PADDING;

        return DEFAULT_PADDING;
    }

    private String getSubQueryResultString(String queryString, String columnName) throws SQLException {
        ResultSet resultSet = m_botAction.SQLQuery(DATABASE, queryString);
        StringBuffer subQueryResultString = new StringBuffer("(");

        if (resultSet == null)
            throw new RuntimeException("ERROR: Null result set returned; connection may be down.");
        if (!resultSet.next())
            return null;
        for (;;) {
            subQueryResultString.append(resultSet.getString(columnName));
            if (!resultSet.next())
                break;
            subQueryResultString.append(", ");
        }
        subQueryResultString.append(") ");
        m_botAction.SQLClose(resultSet);

        return subQueryResultString.toString();
    }

    /**
     * Compares IP and MID info of two names, and shows where they match.
     * 
     * @param argString
     * @throws SQLException
     */
    public void doCompareCmd(String argString) throws SQLException {
        StringTokenizer argTokens = new StringTokenizer(argString, ":");

        if (argTokens.countTokens() != 2)
            throw new IllegalArgumentException("Please use the following format: !compare <Player1Name>:<Player2Name>");

        String player1Name = argTokens.nextToken();
        String player2Name = argTokens.nextToken();

        try {
            ResultSet p1Set = m_botAction.SQLQuery(DATABASE, "SELECT * " + "FROM tblUser U, tblAlias A " + "WHERE U.fcUserName = '" + Tools.addSlashesToString(player1Name) + "' "
                    + "AND U.fnUserID = A.fnUserID " + "ORDER BY A.fdUpdated DESC");

            ResultSet p2Set = m_botAction.SQLQuery(DATABASE, "SELECT * " + "FROM tblUser U, tblAlias A " + "WHERE U.fcUserName = '" + Tools.addSlashesToString(player2Name) + "' "
                    + "AND U.fnUserID = A.fnUserID " + "ORDER BY A.fdUpdated DESC");

            if (p1Set == null || p2Set == null)
                throw new RuntimeException("ERROR: Null result set returned; connection may be down.");

            p1Set.afterLast();
            p2Set.afterLast();

            m_botAction.sendChatMessage("Comparison of " + player1Name + " to " + player2Name + ":");

            LinkedList<String> IPs = new LinkedList<String>();
            LinkedList<Integer> MIDs = new LinkedList<Integer>();
            while (p1Set.previous()) {
                IPs.add(p1Set.getString("A.fcIPString"));
                MIDs.add(p1Set.getInt("A.fnMachineID"));
            }

            int results = 0;
            boolean matchIP, matchMID;
            String display;

            while (p2Set.previous()) {
                matchIP = false;
                matchMID = false;
                display = "";
                if (MIDs.contains(p2Set.getInt("A.fnMachineID")))
                    matchMID = true;
                if (IPs.contains(p2Set.getString("A.fcIPString")))
                    matchIP = true;

                if (matchMID == true)
                    display += "MID match: " + p2Set.getInt("A.fnMachineID") + " ";
                if (matchIP == true)
                    display += " IP match: " + p2Set.getString("A.fcIPString");

                if (display != "") {
                    if (results < m_maxRecords) {
                        m_botAction.sendChatMessage(display);
                        results++;
                    }
                }
            }

            m_botAction.SQLClose(p1Set);
            m_botAction.SQLClose(p2Set);
            if (results == 0)
                m_botAction.sendChatMessage("No matching IPs or MIDs found.");
            if (results > m_maxRecords)
                m_botAction.sendChatMessage(results - m_maxRecords + " records not shown.  !maxrecords # to show (current: " + m_maxRecords + ")");
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Cannot connect to database.");
        }

    }

    public void doHelpCmd(String sender) {
        String[] message = { "ALIAS CHAT COMMANDS: ",
                "!AltNick  <PlayerName>         - Alias by <PlayerName> (!altnickorig for old alg.)",
                "!AltOr    <PlayerName>         - Alias by <PlayerName> using IP OR MID",
                "!AltIP    <IP>                 - Alias by <IP>",
                "!PartialIP <IP>                - Alias by <PARTIALIP>",
                "!AltMID   <MacID>              - Alias by <MacID>",
                "!Info     <PlayerName>         - Shows stored info of <PlayerName>",
                "!Compare  <Player1>:<Player2>  - Compares and shows matches",
                "!MaxResults <#>                - Changes the max. number of results to return",
                "!NameWatch <Name>:<reason>     - Watches logins for <Name> with the specified <reason>",
                "!NameWatch <Name>              - Disables the login watch for <Name>",
                "!LNameWatch <Name>:<reason>    - Watches logins that start with <Name> with the specified <reason>",
                "!LNameWatch <Name>             - Disables the left partial login watch for <Name>",
                "!RNameWatch <Name>:<reason>    - Watches logins that end with <Name> with the specified <reason>",
                "!RNameWatch <Name>             - Disables the right partial login watch for <Name>",
                "!PNameWatch <Name>:<reason>    - Watches logins that contain <Name> with the specified <reason>",
                "!PNameWatch <Name>             - Disables the partial login watch for <Name>",
                "!IPWatch   <IP>:<reason>       - Watches logins for <IP> with the specified <reason>",
                "!IPWatch   <IP>                - Disables the login watch for <IP>",
                "!MIDWatch  <MID>:<reason>      - Watches logins for <MID> with the specified <reason>",
                "!MIDWatch  <MID>               - Disables the login watch for <MID>",
                "!ClearNameWatch                - Clears all login watches for names (disabled)",
                "!ClearIPWatch                  - Clears all login watches for IPs (disabled)",
                "!ClearMIDWatch                 - Clears all login watches for MIDs (disabled)",
                "!ShowWatches [<Sort>:<Dir>]    - Shows all current login watches, optionally sorted by <Sort>",
                "                               Sort options: d(ate), t(rigger), i(ssuer); Dir: A(scending), D(escending)",
                "!ShowMyWatches [<Sort>:<Dir>]  - Same as !ShowWatches, but only displays your own watches",
                "!SortByName / !SortByDate      - Selects sorting method",
                "!update                        - Updates TWDOps list",
                //"!aliasop <name>                - Gives <name> alias access",
                //"!aliasdeop <name>              - Removes alias access for <name>",
                "!listaliasops (!lao)           - Lists current alias-ops"

        };
        m_botAction.smartPrivateMessageSpam(sender, message);
    }

    public void doCheatSheet(String sender) {
        String[] message = { "ALIAS CHAT CHEAT SHEET: ",
                "!AltIP             - !ai           !MIDWatch       - !mw",
                "!AltMID            - !am           !IPWatch        - !iw",
                "!AltNick           - !an           !NameWatch      - !nw",
                "!Info              -               !PNameWatch     - !pnw",
                "!AltOr             - !ao           !LNameWatch     - !lnw",
                "!PartialIP         - !pip          !RNameWatch     - !rnw",
                "!Compare           -               !ShowWatches    - !sw",
                "!MaxResults        - !mr           !ShowMyWatches  - !smw",
                "!update            -               !SortByName     - !sbn",
                "!ListAliasOps      - !lao          !SortByDate     - !sbd"
        };
        m_botAction.smartPrivateMessageSpam(sender, message);
    }
    
    public void doRecordInfoCmd(String sender) {
        m_botAction.sendChatMessage("Players recorded in the hashmap: " + (justAdded.size() + deleteNextTime.size()));
    }

    public void doAltTWLCmd(String argString) {
        try {
            ResultSet resultSet = m_botAction.SQLQuery(DATABASE, "SELECT U2.fcUserName, T.fcTeamName " + "FROM tblAlias A1, tblAlias A2, tblUser U1, tblUser U2, tblTeam T, tblTeamUser TU "
                    + "WHERE U1.fcUserName = '" + Tools.addSlashesToString(argString) + "' " + "AND A1.fnUserID = U1.fnUserID " + "AND A1.fnIP = A2.fnIP " + "AND A1.fnMachineID = A2.fnMachineID "
                    + "AND A2.fnUserID = U2.fnUserID " + "AND TU.fnUserID = U2.fnUserID " + "AND TU.fnCurrentTeam = 1 " + "AND TU.fnTeamID = T.fnTeamID " + "AND T.fdDeleted = '0000-00-00 00:00:00' "
                    + "ORDER BY U2.fcUserName, T.fcTeamName");

            int results = 0;
            String lastName = "";
            String currName;
            if (resultSet == null)
                throw new RuntimeException("ERROR: Cannot connect to database.");
            for (; resultSet.next(); results++) {
                if (results <= m_maxRecords) {
                    currName = resultSet.getString("U2.fcUserName");
                    if (!currName.equalsIgnoreCase(lastName))
                        m_botAction.sendChatMessage("Name: " + padString(currName, 25) + " Squad: " + resultSet.getString("T.fcTeamName"));
                    lastName = currName;
                }
            }
            m_botAction.SQLClose(resultSet);
            if (results == 0)
                m_botAction.sendChatMessage("Player is not on a TWL squad.");
            if (results > m_maxRecords)
                m_botAction.sendChatMessage(results - m_maxRecords + " records not shown.  !maxrecords # to show (current: " + m_maxRecords + ")");
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Cannot connect to database.");
        }
    }

    /**
	 *
	 */
    public void doMaxRecordsCmd(String maxRecords) {
        try {
            m_maxRecords = Integer.parseInt(maxRecords);
            m_botAction.sendChatMessage("Max. number of records to display set to " + m_maxRecords + ".");
        } catch (Exception e) {
            throw new RuntimeException("Please give a number.");
        }
    }

    /**
     * Starts watching for an IP starting with a given string.
     * 
     * @param IP
     *            IP to watch for
     */
    public void doIPWatchCmd(String sender, String message) {
        String[] params = message.split(":");
        String IP = params[0].trim();

        if (watchedIPs.containsKey(IP) && (params.length == 1 || params[1] == null || params[1].length() == 0)) {
            watchedIPs.remove(IP);
            m_botAction.sendChatMessage("Login watching disabled for IPs starting with " + IP);
            saveWatches();
        } else if (params.length == 1 || params[1] == null || params[1].length() == 0) {
            m_botAction.sendChatMessage("Please specify a comment/reason after the IP seperated by a : . For example, !IPWatch 123.123.123.9:Possible hacker .");
        } else {
            String comment = params[1].trim();

            if (watchedIPs.containsKey(IP)) {
                m_botAction.sendChatMessage("Login watching for (partial) IP " + IP + " reason changed.");
            } else {
                m_botAction.sendChatMessage("Login watching enabled for IPs starting with " + IP);
            }
            String date = sdf.format(System.currentTimeMillis());
            watchedIPs.put(IP, new WatchComment(date, sender + ": " + comment));
            saveWatches();
        }
    }

    public void doAddAliasOp(String sender, String message) {
        if (!m_botAction.getOperatorList().isSmod(sender)) {
            m_botAction.sendChatMessage("Only Smods or higher can add alias-ops.");
            return;
        }

        if (aliasops.contains(message.toLowerCase())) {
            m_botAction.sendChatMessage(message + " is already on the alias-ops list.");
            return;
        } else {
            aliasops.add(message.toLowerCase());
        }

        try {
            String query = "INSERT INTO tblAliasOps " + "(User) " + "VALUES ('" + Tools.addSlashes(message) + "')";
            ResultSet r = m_botAction.SQLQuery(DATABASE, query);
            m_botAction.SQLClose(r);
            m_botAction.sendChatMessage(message + " has been added to the alias-ops list.");
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Unable to add " + message + " to the alias-ops list on the database.");
        }
    }

    public void doRemAliasOp(String sender, String message) {
        if (!m_botAction.getOperatorList().isSmod(sender)) {
            m_botAction.sendChatMessage("Only Smods or higher can remove alias-ops.");
            return;
        }

        if (!aliasops.contains(message.toLowerCase())) {
            m_botAction.sendChatMessage(message + " could not be found on the alias-ops list.");
            return;
        } else {
            aliasops.remove(message.toLowerCase());
        }

        try {
            String query = "DELETE FROM tblAliasOps WHERE User = '" + Tools.addSlashes(message) + "'";
            ResultSet r = m_botAction.SQLQuery(DATABASE, query);
            m_botAction.SQLClose(r);
            m_botAction.sendChatMessage(message + " has been removed from the alias-ops list.");
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Unable to remove " + message + " from the alias-ops list on the database.");
        }
    }

    public void doListAliasOps() {
        int counter = 0;
        String output = "";

        m_botAction.sendChatMessage("AliasOps:");

        for (String i : aliasops) {
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
     * 
     * @param name
     *            Name to watch for
     */
    public void doNameWatchCmd(String sender, String message) {
        String[] params = message.split(":");
        String name = params[0].trim();
        if (watchedNames.containsKey(name.toLowerCase()) && (params.length == 1 || params[1] == null || params[1].length() == 0)) {
            watchedNames.remove(name.toLowerCase());
            m_botAction.sendChatMessage("Login watching disabled for '" + name + "'.");
            saveWatches();
        } else if (params.length == 1 || params[1] == null || params[1].length() == 0) {
            m_botAction.sendChatMessage("Please specify a comment/reason after the name seperated by a : . For example, !NameWatch Pure_Luck:Bad boy .");
        } else {
            String comment = params[1].trim();

            if (watchedNames.containsKey(name.toLowerCase())) {
                m_botAction.sendChatMessage("Login watching for '" + name + "' reason changed.");
            } else {
                m_botAction.sendChatMessage("Login watching enabled for '" + name + "'.");
            }
            String date = sdf.format(System.currentTimeMillis());
            watchedNames.put(name.toLowerCase(), new WatchComment(date, sender + ": " + comment));
            saveWatches();
        }
    }
    
    /**
     * Starts watching for a left partial name to log on.
     * 
     * @param name
     *            Name to watch for
     */
    public void doLNameWatchCmd(String sender, String message) {
        String[] params = message.split(":");
        String name = params[0].trim();
        String lcname = name.toLowerCase();
        if (watchedLNames.containsKey(lcname) && (params.length == 1 || params[1] == null || params[1].length() == 0)) {
            watchedLNames.remove(lcname);
            m_botAction.sendChatMessage("Login watching disabled for '" + name + "'.");
            saveWatches();
        } else if (params.length == 1 || params[1] == null || params[1].length() == 0) {
            m_botAction.sendChatMessage("Please specify a comment/reason after the name seperated by a : . For example, !LNameWatch Wing:Evil genius.");
        } else {
            String comment = params[1].trim();

            if (watchedLNames.containsKey(lcname)) {
                m_botAction.sendChatMessage("Login watching for '" + name + "' reason changed.");
            } else {
                m_botAction.sendChatMessage("Login watching enabled for '" + name + "'.");
            }
            String date = sdf.format(System.currentTimeMillis());
            watchedLNames.put(lcname, new WatchComment(date, sender + ": " + comment));
            saveWatches();
        }
    }
    
    /**
     * Starts watching for a name to log on.
     * 
     * @param name
     *            Name to watch for
     */
    public void doRNameWatchCmd(String sender, String message) {
        String[] params = message.split(":");
        String name = params[0].trim();
        String lcname = name.toLowerCase();
        if (watchedRNames.containsKey(lcname) && (params.length == 1 || params[1] == null || params[1].length() == 0)) {
            watchedRNames.remove(lcname);
            m_botAction.sendChatMessage("Login watching disabled for '" + name + "'.");
            saveWatches();
        } else if (params.length == 1 || params[1] == null || params[1].length() == 0) {
            m_botAction.sendChatMessage("Please specify a comment/reason after the name seperated by a : . For example, !RNameWatch PAP:Lazy Dev.");
        } else {
            String comment = params[1].trim();

            if (watchedRNames.containsKey(lcname)) {
                m_botAction.sendChatMessage("Login watching for '" + name + "' reason changed.");
            } else {
                m_botAction.sendChatMessage("Login watching enabled for '" + name + "'.");
            }
            String date = sdf.format(System.currentTimeMillis());
            watchedRNames.put(lcname, new WatchComment(date, sender + ": " + comment));
            saveWatches();
        }
    }
    
    /**
     * Starts watching for a name to log on.
     * 
     * @param name
     *            Name to watch for
     */
    public void doPNameWatchCmd(String sender, String message) {
        String[] params = message.split(":");
        String name = params[0].trim();
        String lcname = name.toLowerCase();
        if (watchedPNames.containsKey(lcname) && (params.length == 1 || params[1] == null || params[1].length() == 0)) {
            watchedPNames.remove(lcname);
            m_botAction.sendChatMessage("Login watching disabled for '" + name + "'.");
            saveWatches();
        } else if (params.length == 1 || params[1] == null || params[1].length() == 0) {
            m_botAction.sendChatMessage("Please specify a comment/reason after the name seperated by a : . For example, !PNameWatch eft_Ey:Birthday watch.");
        } else {
            String comment = params[1].trim();

            if (watchedPNames.containsKey(lcname)) {
                m_botAction.sendChatMessage("Login watching for '" + name + "' reason changed.");
            } else {
                m_botAction.sendChatMessage("Login watching enabled for '" + name + "'.");
            }
            String date = sdf.format(System.currentTimeMillis());
            watchedPNames.put(lcname, new WatchComment(date, sender + ": " + comment));
            saveWatches();
        }
    }
    
    /**
     * Starts watching for a given MacID.
     * 
     * @param MID
     *            MID to watch for
     */
    public void doMIDWatchCmd(String sender, String message) {
        String[] params = message.split(":");
        String MID = params[0].trim();

        if (watchedMIDs.containsKey(MID) && (params.length == 1 || params[1] == null || params[1].length() == 0)) {
            watchedMIDs.remove(MID);
            m_botAction.sendChatMessage("Login watching disabled for MID: " + MID);
            saveWatches();
        } else if (params.length == 1 || params[1] == null || params[1].length() == 0) {
            m_botAction.sendChatMessage("Please specify a comment/reason after the MID seperated by a : . For example, !MIDWatch 777777777:I like the number .");
        } else {
            String comment = params[1].trim();

            if (watchedMIDs.containsKey(MID)) {
                m_botAction.sendChatMessage("Login watching for MID " + MID + " reason changed.");
            } else {
                m_botAction.sendChatMessage("Login watching enabled for MID: " + MID);
            }
            String date = sdf.format(System.currentTimeMillis());
            watchedMIDs.put(MID, new WatchComment(date, sender + ": " + comment));
            saveWatches();

        }
    }

    /**
     * Stops all IP watching.
     */
    public void doClearIPWatchCmd() {
        watchedIPs.clear();
        m_botAction.sendChatMessage("All watched IPs cleared.");
        saveWatches();
    }

    /**
     * Stops all name watching.
     */
    public void doClearNameWatchCmd() {
        watchedNames.clear();
        m_botAction.sendChatMessage("All watched names cleared.");
        saveWatches();
    }

    /**
     * Stops all MacID watching.
     */
    public void doClearMIDWatchCmd() {
        watchedMIDs.clear();
        m_botAction.sendChatMessage("All watched MIDs cleared.");
        saveWatches();
    }

    /**
     * Stops all left hand name watching.
     */
    public void doClearLNameWatchCmd() {
        watchedLNames.clear();
        m_botAction.sendChatMessage("All watched left names cleared.");
        saveWatches();
    }
    
    /**
     * Stops all right hand name watching.
     */
    public void doClearRNameWatchCmd() {
        watchedRNames.clear();
        m_botAction.sendChatMessage("All watched right names cleared.");
        saveWatches();
    }
    
    /**
     * Stops all partial name watching.
     */
    public void doClearPNameWatchCmd() {
        watchedPNames.clear();
        m_botAction.sendChatMessage("All watched partial names cleared.");
        saveWatches();
    }
    
    /**
     * Shows current watches.
     */
    public void doShowWatchesCmd() {
        if (watchedIPs.size() == 0) {
            m_botAction.sendChatMessage("IP:   (none)");
        }
        for (String IP : watchedIPs.keySet()) {
            WatchComment com = watchedIPs.get(IP);
            m_botAction.sendChatMessage(com.date + " IP:   " + IP + "  ( " + com.comment + " )");
        }

        if (watchedMIDs.size() == 0) {
            m_botAction.sendChatMessage("MID:  (none)");
        }
        for (String MID : watchedMIDs.keySet()) {
            WatchComment com = watchedMIDs.get(MID);
            m_botAction.sendChatMessage(com.date + " MID:  " + MID + "  ( " + com.comment + " )");
        }

        if (watchedNames.size() == 0) {
            m_botAction.sendChatMessage("Name: (none)");
        }
        for (String Name : watchedNames.keySet()) {
            WatchComment com = watchedNames.get(Name);
            m_botAction.sendChatMessage(com.date + " Name: " + Name + "  ( " + com.comment + " )");
        }
        
        if (watchedLNames.size() == 0) {
            m_botAction.sendChatMessage("Left Name: (none)");
        }
        for (String Name : watchedLNames.keySet()) {
            WatchComment com = watchedLNames.get(Name);
            m_botAction.sendChatMessage(com.date + " Left Name: " + Name + "  ( " + com.comment + " )");
        }
        
        if (watchedRNames.size() == 0) {
            m_botAction.sendChatMessage("Right Name: (none)");
        }
        for (String Name : watchedRNames.keySet()) {
            WatchComment com = watchedRNames.get(Name);
            m_botAction.sendChatMessage(com.date + " Right Name: " + Name + "  ( " + com.comment + " )");
        }
        
        if (watchedPNames.size() == 0) {
            m_botAction.sendChatMessage("Partial Name: (none)");
        }
        for (String Name : watchedPNames.keySet()) {
            WatchComment com = watchedPNames.get(Name);
            m_botAction.sendChatMessage(com.date + " Partial Name: " + Name + "  ( " + com.comment + " )");
        }
    }
    
    /**
     * New version for {@link #doShowWatchesCmd()}, handles the !showwatches and !showmywatches commands.
     * <p>
     * This version combines the standard result provided by !showwatches with optional parameters, being:
     * <ul>
     *  <li>Sort by date issued;
     *  <li>Sort by trigger;
     *  <li>Sort by issuer.
     * </ul>
     * The above can be combined by any of the following:
     * <ul>
     *  <li>Ascending or descending sort;
     *  <li>Only show the watches issued by the user of the command.
     * </ul>
     * <p>
     * Please do note that any sorting is done per "group" and that the result is still displayed in the appropriate chat.
     * @param name Issuer of the command.
     * @param args Optional arguments: [<code><</code>{d(ate), t(rigger), i(ssuer)}>:<code><</code>{a(scending), d(escending)}>]
     * @param showAll True if all watches are to be shown, false if only the issuer's watches are to be shown.
     */
    public void doShowWatchesCmd(String name, String args, boolean showAll) {
        SortField sortBy = SortField.NONE;
        boolean sortDirection = false;
        boolean nothingFound = true;
        Map<String, WatchComment> tmpWatchComments = new TreeMap<String, WatchComment>();
        
        if(!args.isEmpty()) {
            String[] splitArgs = args.toLowerCase().split(":");
            
            if(splitArgs.length != 2) {
                m_botAction.sendSmartPrivateMessage(name, "Invalid arguments, please consult !help.");
                return;
            }
            
            if(splitArgs[0].startsWith("d"))
                sortBy = SortField.DATE;
            else if(splitArgs[0].startsWith("t"))
                sortBy = SortField.TRIGGER;
            else if(splitArgs[0].startsWith("i"))
                sortBy = SortField.ISSUER;
            else
                sortBy = SortField.NONE;
            
            sortDirection = splitArgs[1].startsWith("a");
        }
        
        if (watchedIPs.size() == 0) {
            m_botAction.sendSmartPrivateMessage(name, "IP:   (none)");
        } else {
            if(sortBy != SortField.NONE)
                tmpWatchComments = WatchListSorter.sortByValue(watchedIPs, sortBy, sortDirection);
            else
                tmpWatchComments = watchedIPs;
            for (String IP : tmpWatchComments.keySet()) {
                WatchComment com = tmpWatchComments.get(IP);
                if(!showAll && !com.comment.startsWith(name))
                    continue;
                
                m_botAction.sendSmartPrivateMessage(name, com.date + " IP:   " + Tools.formatString(IP, 19) + "  ( " + com.comment + " )");
                nothingFound = false;
            }
            if(nothingFound)
                m_botAction.sendSmartPrivateMessage(name, "IP:   (none)");
        }
        
        nothingFound = true;
        
        if (watchedMIDs.size() == 0) {
            m_botAction.sendSmartPrivateMessage(name, "MID:  (none)");
        } else {
            if(sortBy != SortField.NONE)
                tmpWatchComments = WatchListSorter.sortByValue(watchedMIDs, sortBy, sortDirection);
            else
                tmpWatchComments = watchedMIDs;
            for (String MID : tmpWatchComments.keySet()) {
                WatchComment com = tmpWatchComments.get(MID);
                if(!showAll && !com.comment.startsWith(name))
                    continue;
                
                m_botAction.sendSmartPrivateMessage(name, com.date + " MID:  " + Tools.formatString(MID, 19) + "  ( " + com.comment + " )");
                nothingFound = false;
            }
            if(nothingFound)
                m_botAction.sendSmartPrivateMessage(name, "MID:  (none)");
        }
        
        nothingFound = true;
        
        if (watchedNames.size() == 0) {
            m_botAction.sendSmartPrivateMessage(name, "Name: (none)");
        } else {
            if(sortBy != SortField.NONE) 
                tmpWatchComments = WatchListSorter.sortByValue(watchedNames, sortBy, sortDirection);
            else
                tmpWatchComments = watchedNames;
            for (String Name : tmpWatchComments.keySet()) {
                WatchComment com = tmpWatchComments.get(Name);
                if(!showAll && !com.comment.startsWith(name))
                    continue;
                
                m_botAction.sendSmartPrivateMessage(name, com.date + " Name: " + Tools.formatString(Name, 19) + "  ( " + com.comment + " )");
                nothingFound = false;
            }
            if(nothingFound)
                m_botAction.sendSmartPrivateMessage(name, "Name: (none)");
        }
        
        nothingFound = true;
        
        if (watchedLNames.size() == 0) {
            m_botAction.sendSmartPrivateMessage(name, "Left Name:    (none)");
        } else {
            if(sortBy != SortField.NONE)
                tmpWatchComments = WatchListSorter.sortByValue(watchedLNames, sortBy, sortDirection);
            else
                tmpWatchComments = watchedLNames;
            for (String Name : tmpWatchComments.keySet()) {
                WatchComment com = tmpWatchComments.get(Name);
                if(!showAll && !com.comment.startsWith(name))
                    continue;
                
                m_botAction.sendSmartPrivateMessage(name, com.date + " Left Name:    " + Tools.formatString(Name, 19) + "  ( " + com.comment + " )");
                nothingFound = false;
            }
            if(nothingFound)
                m_botAction.sendSmartPrivateMessage(name, "Left Name:    (none)");
        }
        
        nothingFound = true;
        
        if (watchedRNames.size() == 0) {
            m_botAction.sendSmartPrivateMessage(name, "Right Name:   (none)");
        } else {
            if(sortBy != SortField.NONE)
                tmpWatchComments = WatchListSorter.sortByValue(watchedRNames, sortBy, sortDirection);
            else
                tmpWatchComments = watchedRNames;
            for (String Name : tmpWatchComments.keySet()) {
                WatchComment com = tmpWatchComments.get(Name);
                if(!showAll && !com.comment.startsWith(name))
                    continue;
                
                m_botAction.sendSmartPrivateMessage(name, com.date + " Right Name:   " + Tools.formatString(Name, 19) + "  ( " + com.comment + " )");
                nothingFound = false;
            }
            if(nothingFound)
                m_botAction.sendSmartPrivateMessage(name, "Right Name:   (none)");
        }
        
        nothingFound = true;
        
        if (watchedPNames.size() == 0) {
            m_botAction.sendSmartPrivateMessage(name, "Partial Name: (none)");
        } else {
            if(sortBy != SortField.NONE)
                tmpWatchComments = WatchListSorter.sortByValue(watchedPNames, sortBy, sortDirection);
            else
                tmpWatchComments = watchedPNames;
            for (String Name : tmpWatchComments.keySet()) {
                WatchComment com = tmpWatchComments.get(Name);
                if(!showAll && !com.comment.startsWith(name))
                    continue;
                
                m_botAction.sendSmartPrivateMessage(name, com.date + " Partial Name: " + Tools.formatString(Name, 19) + "  ( " + com.comment + " )");
                nothingFound = false;
            }
            if(nothingFound)
                m_botAction.sendSmartPrivateMessage(name, "Partial Name: (none)");
        }
    }
    
    private void doPrivateAliases() {
        privateAliases = !privateAliases;
        if (privateAliases) {
            m_botAction.sendChatMessage("Alias command output will now be directed to PRIVATE messages.");
            m_botAction.getBotSettings().put("PrivateAliases", 1);
            m_botAction.getBotSettings().save();
        } else {
            m_botAction.sendChatMessage("Alias command output will now be directed to CHAT.");
            m_botAction.getBotSettings().put("PrivateAliases", 0);
            m_botAction.getBotSettings().save();
        }
    }

    public void handleChatMessage(String sender, String message) {
        String command = message.toLowerCase();
        String args = "";

        //Separate the command from its arguments if applicable.
        if(message.contains(" ")) {
            int index = message.indexOf(" ");
            command = command.substring(0, index);
            if(message.length() > ++index)
                args = message.substring(index).trim();
        }
        /*
         * Extra check for smod and twdop added
         * -fantus
         */
        if (!m_botAction.getOperatorList().isSmod(sender) && !isTWDOp(sender) && !isAliasOp(sender)) {
            return;
        }

        try {
            if (command.equals("!recordinfo")) {
                doRecordInfoCmd(sender);
            } else if (command.equals("!partialip")     || command.equals("!pip")) {
                doAltIpCmdPartial(sender, args);
            } else if (command.equals("!help")) {
                doHelpCmd(sender);
            } else if (command.equals("!cheatsheet")    || command.equals("!cs")) {
                doCheatSheet(sender);
            } else if (command.equals("!altnick")       || command.equals("!an")) {
                doAltNickCmd(sender, args, false);
                record(sender, message);
            } else if (command.equals("!altnickorig")   || command.equals("!ano")) {
                doORIGAltNickCmd(sender, args, false);
                record(sender, message);
            /*
            } else if (command.startsWith("!altnick2 ")) {
                doAltNick2Cmd(sender, message.substring(10).trim(), false);
                record(sender, message);
            } else if (command.startsWith("!altnick3 ")) {
                doAltNick3Cmd(sender, message.substring(10).trim(), false);
                record(sender, message);
            */
            } else if (command.equals("!altor")         || command.equals("!ao")) {
                doAltNickOrCmd(sender, args, false);
                record(sender, message);
            } else if (command.equals("!altip")         || command.equals("!ai")) {
                doAltIpCmd(sender, args);
                record(sender, message);
            } else if (command.equals("!altmid")        || command.equals("!am")) {
                doAltMacIdCmd(sender, args);
                record(sender, message);
            } 
            //			else if(command.startsWith("!alttwl "))
            //				doAltTWLCmd(message.substring(8).trim());
            else if (command.equals("!info")) {
                doInfoCmd(sender, args);
                record(sender, message);
            }
            else if (opList.isSysopExact(sender) && command.equals("!infoall")) {
                doInfoAllCmd(sender, args);
                record(sender, message);
            } else if (opList.isSysopExact(sender) && command.startsWith("!priv"))
                doPrivateAliases();
            else if (command.equals("!compare")) {
                doCompareCmd(args);
                record(sender, message);
            } 
            else if (command.equals("!maxrecords")      || command.equals("!mr"))
                doMaxRecordsCmd(args);
            else if (command.equals("!ipwatch")         || command.equals("!iw"))
                doIPWatchCmd(sender, args);
            else if (command.equals("!namewatch")       || command.equals("!nw"))
                doNameWatchCmd(sender, args);
            else if (command.equals("!midwatch")        || command.equals("!mw"))
                doMIDWatchCmd(sender, args);
            else if (command.equals("!lnamewatch")      || command.equals("!lnw"))
                doLNameWatchCmd(sender, args);
            else if (command.equals("!rnamewatch")      || command.equals("!rnw"))
                doRNameWatchCmd(sender, args);
            else if (command.equals("!pnamewatch")      || command.equals("!pnw"))
                doPNameWatchCmd(sender, args);
            /* Disabled for now. No reason to clear a full list.
            else if (command.equals("!clearipwatch")    || command.equals("!ciw"))
                doClearIPWatchCmd();
            else if (command.equals("!clearnamewatch")  || command.equals("!cnw"))
                doClearNameWatchCmd();
            else if (command.equals("!clearmidwatch")   || command.equals("!cmw"))
                doClearMIDWatchCmd();
            else if (command.equals("!clearlnamewatch") || command.equals("!clw"))
                doClearLNameWatchCmd();
            else if (command.equals("!clearrnamewatch") || command.equals("!crw"))
                doClearRNameWatchCmd();
            else if (command.equals("!clearpnamewatch") || command.equals("!cpw"))
                doClearPNameWatchCmd();
             */
            else if (command.equals("!showwatches")     || command.equals("!sw"))
                doShowWatchesCmd(sender, args, true);
//            else if (command.equals("!showwatches"))
//                doShowWatchesCmd();
            else if (command.equals("!showmywatches")   || command.equals("!smw"))
                doShowWatchesCmd(sender, args, false);
            else if (command.equals("!sortbyname")      || command.equals("!sbn")) {
                m_sortByName = true;
                m_botAction.sendChatMessage("Sorting !alt cmds by name first.");
            } else if (command.equals("!sortbydate")    || command.equals("!sbd")) {
                m_sortByName = false;
                m_botAction.sendChatMessage("Sorting !alt cmds by date first.");
            } else if (command.equals("!update")) {
                updateTWDOps();
                updateBangOps();
                updateAliasOps();
                m_botAction.sendChatMessage("Updating twdop & alias-op lists.");
            } else if (command.equals("!listaliasops")  || command.equals("!lao"))
                doListAliasOps();
            /*
            else if (command.equals("!aliasop"))
                doAddAliasOp(sender, args);
            else if (command.equals("!aliasdeop"))
                doRemAliasOp(sender, args);
            */
            else if (command.equals("!altall") && opList.isSysopExact(sender)) {
                doAltNickCmd(sender, args, true);
                record(sender, message);
            }
        } catch (Exception e) {
            m_botAction.sendChatMessage(e.getMessage());
        }
    }

    public void handleEvent(Message event) {
        String sender = event.getMessager();
        String message = event.getMessage();
        int messageType = event.getMessageType();

        if (messageType == Message.CHAT_MESSAGE) {
            handleChatMessage(sender, message);
        } else {
            if (isBangOp(sender) && (messageType == Message.PRIVATE_MESSAGE) || (messageType == Message.REMOTE_PRIVATE_MESSAGE) )
                handleChatMessage(sender, message);
        }
    }

    @SuppressWarnings("static-access")
    public void record(String sender, String message) {
        try {
            Calendar c = Calendar.getInstance();
            String timestamp = (c.get(c.MONTH) + 1) + "/" + c.get(c.DAY_OF_MONTH) + "/" + c.get(c.YEAR) + " - ";

            BufferedReader reader = new BufferedReader(new FileReader("/home/bots/twcore/bin/logs/alias.log"));
            BufferedWriter writer = new BufferedWriter(new FileWriter("/home/bots/twcore/bin/logs/alias.log", true));
            writer.write("\r\n" + timestamp + sender + " - " + message);
            reader.close();
            writer.close();
        } catch (Exception e) {
            m_botAction.sendChatMessage(2, "I cannot log this to the alias.log! + " + sender + "-" + message);
            Tools.printStackTrace(e);
        }

    }

    public void gotRecord(String argString) {
        if (justAdded.contains(argString.toLowerCase()) || deleteNextTime.contains(argString.toLowerCase()))
            return;

        StringTokenizer recordArgs = new StringTokenizer(argString, ":");
        if (recordArgs.countTokens() != 3)
            throw new IllegalArgumentException("ERROR: Could not write player information.");
        String playerName = recordArgs.nextToken();
        String playerIP = recordArgs.nextToken();
        String playerMacID = recordArgs.nextToken();

        checkName(playerName, playerIP, playerMacID);
        checkIP(playerName, playerIP, playerMacID);
        checkMID(playerName, playerIP, playerMacID);
        checkLName(playerName, playerIP, playerMacID);
        checkRName(playerName, playerIP, playerMacID);
        checkPName(playerName, playerIP, playerMacID);
        if (playerMacID.equals(TWChatMID))  // Do not record TW Chat users in alias system
            return;

        try {
            recordInfo(playerName, playerIP, playerMacID);
            justAdded.add(playerName.toLowerCase());
        } catch (Exception e) {}
    }

    /**
     * Check if a name is being watched for, and notify on chat if so.
     * 
     * @param name
     *            Name to check
     * @param IP
     *            IP of player
     * @param MacId
     *            MacID of player
     */
    public void checkName(String name, String IP, String MacID) {
        if (watchedNames.containsKey(name.toLowerCase())) {
            m_botAction.sendChatMessage("NAMEWATCH: '" + name + "' logged in.  (IP: " + IP + ", MID: " + MacID + ")");
            m_botAction.sendChatMessage("           " + watchedNames.get(name.toLowerCase()));
        }
    }

    /**
     * Check if a name is being watched for, and notify on chat if so.
     * Used for left partial matches. (Starts with)
     * 
     * @param name
     *            Name to check
     * @param IP
     *            IP of player
     * @param MacId
     *            MacID of player
     */
    public void checkLName(String name, String IP, String MacID) {
        for (String startName : watchedLNames.keySet()) {
            if (name.toLowerCase().startsWith(startName)) {
                m_botAction.sendChatMessage("LEFT PARTIAL NAMEWATCH: '" + name + "' logged in.  (IP: " + IP + ", MID: " + MacID + ")");
                m_botAction.sendChatMessage("           " + watchedLNames.get(startName));
            }
        }
    }
    
    /**
     * Check if a name is being watched for, and notify on chat if so.
     * Used for right partial matches. (Ends with)
     * 
     * @param name
     *            Name to check
     * @param IP
     *            IP of player
     * @param MacId
     *            MacID of player
     */
    public void checkRName(String name, String IP, String MacID) {
        for (String startName : watchedRNames.keySet()) {
            if (name.toLowerCase().endsWith(startName)) {
                m_botAction.sendChatMessage("RIGHT PARTIAL NAMEWATCH: '" + name + "' logged in.  (IP: " + IP + ", MID: " + MacID + ")");
                m_botAction.sendChatMessage("           " + watchedRNames.get(startName));
            }
        }
    }
    
    /**
     * Check if a name is being watched for, and notify on chat if so.
     * Used for partial matches. (Contains)
     * 
     * @param name
     *            Name to check
     * @param IP
     *            IP of player
     * @param MacId
     *            MacID of player
     */
    public void checkPName(String name, String IP, String MacID) {
        for (String startName : watchedPNames.keySet()) {
            if (name.toLowerCase().contains(startName)) {
                m_botAction.sendChatMessage("PARTIAL NAMEWATCH: '" + name + "' logged in.  (IP: " + IP + ", MID: " + MacID + ")");
                m_botAction.sendChatMessage("           " + watchedPNames.get(startName));
            }
        }
    }
    
    /**
     * Check if an IP is being watched for, and notify on chat if so.
     * 
     * @param name
     *            Name of player
     * @param IP
     *            IP to check
     * @param MacId
     *            MacID of player
     */
    public void checkIP(String name, String IP, String MacID) {
        for (String IPfragment : watchedIPs.keySet()) {
            if (IP.startsWith(IPfragment)) {
                m_botAction.sendChatMessage("IPWATCH: Match on '" + name + "' - " + IP + " (matches " + IPfragment + "*)  MID: " + MacID);
                m_botAction.sendChatMessage("         " + watchedIPs.get(IPfragment));
            }
        }
    }

    /**
     * Check if an MID is being watched for, and notify on chat if so.
     * 
     * @param name
     *            Name of player
     * @param IP
     *            IP of player
     * @param MacId
     *            MacID to check
     */
    public void checkMID(String name, String IP, String MacID) {
        if (watchedMIDs.containsKey(MacID)) {
            m_botAction.sendChatMessage("MIDWATCH: Match on '" + name + "' - " + MacID + "  IP: " + IP);
            m_botAction.sendChatMessage("          " + watchedMIDs.get(MacID));
        }
    }

    /**
     * This method handles an InterProcess event.
     * 
     * @param event
     *            is the IPC event to handle.
     */
    public void handleEvent(InterProcessEvent event) {
        // If the event.getObject() is anything else then the IPCMessage (pubbotchatIPC f.ex) then return
        if (event.getObject() instanceof IPCMessage == false) {
            return;
        }

        IPCMessage ipcMessage = (IPCMessage) event.getObject();
        String botName = m_botAction.getBotName();
        String message = ipcMessage.getMessage();

        try {
            if (botName.equals(ipcMessage.getRecipient())) {
                if (message.startsWith("info "))
                    gotRecord(message.substring(5));
            }
        } catch (Exception e) {
            m_botAction.sendChatMessage(e.getMessage());
        }
    }

    /**
     * This method handles the destruction of this module.
     */

    public void cancel() {
        m_botAction.cancelTask(clearRecordTask);
    }

    private void recordInfo(String playerName, String playerIP, String playerMacID) {
        DBPlayerData playerData = new DBPlayerData(m_botAction, DATABASE, playerName, true);
        int userID = playerData.getUserID();
        int aliasID = getAliasID(userID, playerIP, playerMacID);

        if (aliasID == -1)
            createAlias(userID, playerIP, playerMacID);
        else
            updateAlias(aliasID);
    }

    private int getAliasID(int userID, String playerIP, String playerMacID) {
        try {
            long ip32Bit = make32BitIp(playerIP);
            ResultSet resultSet = m_botAction.SQLQuery(DATABASE, "SELECT * " + "FROM tblAlias " + "WHERE fnUserID = " + userID + " " + "AND fnIP = " + ip32Bit + " " + "AND fnMachineID = "
                    + playerMacID);
            if (!resultSet.next())
                return -1;
            int results = resultSet.getInt("fnAliasID");
            m_botAction.SQLClose(resultSet);
            return results;
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Unable to access database.");
        }
    }

    private void createAlias(int userID, String playerIP, String playerMacID) {
        try {
            long ip32Bit = make32BitIp(playerIP);
            String query = "INSERT INTO tblAlias " + "(fnUserID, fcIPString, fnIP, fnMachineID, fnTimesUpdated, fdRecorded, fdUpdated) " + "VALUES (" + userID + ", '" + playerIP + "', " + ip32Bit
                    + ", " + playerMacID + ", 1, NOW(), NOW())";
            ResultSet r = m_botAction.SQLQuery(DATABASE, query);
            m_botAction.SQLClose(r);
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Unable to create alias entry.");
        }
    }

    private void updateAlias(int aliasID) {
        try {
            ResultSet r = m_botAction.SQLQuery(DATABASE, "UPDATE tblAlias " + "SET fnTimesUpdated = fnTimesUpdated + 1, fdUpdated = NOW() " + "WHERE fnAliasID = " + aliasID);
            m_botAction.SQLClose(r);
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Unable to update alias entry.");
        }
    }

    private void updateTWDOps() {
        try {
            ResultSet r = m_botAction.SQLQuery(DATABASE, "SELECT tblUser.fcUsername FROM `tblUserRank`, `tblUser` WHERE `fnRankID` = '14' AND tblUser.fnUserID = tblUserRank.fnUserID");
            if (r == null)
                return;

            twdops.clear();

            while (r.next()) {
                String name = r.getString("fcUsername");
                twdops.add(name.toLowerCase());
            }
            m_botAction.SQLClose(r);
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Unable to update twdop list.");
        }
    }

    private void updateBangOps() {
        try {
            ResultSet r = m_botAction.SQLQuery(DATABASE, "SELECT tblUser.fcUsername FROM `tblUserRank`, `tblUser` WHERE `fnRankID` = '31' AND tblUser.fnUserID = tblUserRank.fnUserID");
            if (r == null)
                return;

            bangops.clear();

            while (r.next()) {
                String name = r.getString("fcUsername");
                bangops.add(name.toLowerCase());
            }
            m_botAction.SQLClose(r);
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Unable to update bangop list.");
        }
    }
    
    private void updateAliasOps() {
        try {
            ResultSet r = m_botAction.SQLQuery(DATABASE, "SELECT tblUser.fcUsername FROM `tblUserRank`, `tblUser` WHERE `fnRankID` = '20' AND tblUser.fnUserID = tblUserRank.fnUserID");
            if (r == null)
                return;

            aliasops.clear();

            while (r.next()) {
                String name = r.getString("fcUserName");
                aliasops.add(name.toLowerCase());
            }
            m_botAction.SQLClose(r);
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Unable to update alias-op list.");
        }
    }


    private void loadWatches() {
        BotSettings cfg = m_botAction.getBotSettings();
        privateAliases = cfg.getInt("PrivateAliases") == 1;
        boolean loop = true;
        int i = 1;

        watchedIPs.clear();
        watchedMIDs.clear();
        watchedNames.clear();
        watchedLNames.clear();
        watchedRNames.clear();
        watchedPNames.clear();

        // Load the IP watches from the configuration
        while (loop) {
            String IPWatch = cfg.getString("IPWatch" + i);
            if (IPWatch != null && IPWatch.trim().length() > 0) {
                String[] IPWatchSplit = IPWatch.split(":", 3);
                if (IPWatchSplit.length == 3)       // Check for corrupted data
                    watchedIPs.put(IPWatchSplit[1], new WatchComment(IPWatchSplit[0], IPWatchSplit[2]));
                i++;
            } else {
                loop = false;
            }
        }

        // Load the MID watches from the configuration
        loop = true;
        i = 1;

        while (loop) {
            String MIDWatch = cfg.getString("MIDWatch" + i);
            if (MIDWatch != null && MIDWatch.trim().length() > 0) {
                String[] MIDWatchSplit = MIDWatch.split(":", 3);
                if (MIDWatchSplit.length == 3)       // Check for corrupted data
                    watchedMIDs.put(MIDWatchSplit[1], new WatchComment(MIDWatchSplit[0], MIDWatchSplit[2]));
                i++;
            } else {
                loop = false;
            }
        }

        // Load the Name watches from the configuration
        loop = true;
        i = 1;

        while (loop) {
            String NameWatch = cfg.getString("NameWatch" + i);
            if (NameWatch != null && NameWatch.trim().length() > 0) {
                String[] NameWatchSplit = NameWatch.split(":", 3);
                if (NameWatchSplit.length == 3)       // Check for corrupted data
                    watchedNames.put(NameWatchSplit[1], new WatchComment(NameWatchSplit[0], NameWatchSplit[2]));
                i++;
            } else {
                loop = false;
            }
        }

        // Load the Left Partial Name watches from the configuration
        loop = true;
        i = 1;

        while (loop) {
            String NameWatch = cfg.getString("LeftNameWatch" + i);
            if (NameWatch != null && NameWatch.trim().length() > 0) {
                String[] NameWatchSplit = NameWatch.split(":", 3);
                if (NameWatchSplit.length == 3)       // Check for corrupted data
                    watchedLNames.put(NameWatchSplit[1], new WatchComment(NameWatchSplit[0], NameWatchSplit[2]));
                i++;
            } else {
                loop = false;
            }
        }
        
        // Load the Right Partial Name watches from the configuration
        loop = true;
        i = 1;

        while (loop) {
            String NameWatch = cfg.getString("RightNameWatch" + i);
            if (NameWatch != null && NameWatch.trim().length() > 0) {
                String[] NameWatchSplit = NameWatch.split(":", 3);
                if (NameWatchSplit.length == 3)       // Check for corrupted data
                    watchedRNames.put(NameWatchSplit[1], new WatchComment(NameWatchSplit[0], NameWatchSplit[2]));
                i++;
            } else {
                loop = false;
            }
        }
        
        // Load the Partial Name watches from the configuration
        loop = true;
        i = 1;

        while (loop) {
            String NameWatch = cfg.getString("PartialNameWatch" + i);
            if (NameWatch != null && NameWatch.trim().length() > 0) {
                String[] NameWatchSplit = NameWatch.split(":", 3);
                if (NameWatchSplit.length == 3)       // Check for corrupted data
                    watchedPNames.put(NameWatchSplit[1], new WatchComment(NameWatchSplit[0], NameWatchSplit[2]));
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
        for (String IP : watchedIPs.keySet()) {
            WatchComment com = watchedIPs.get(IP);
            cfg.put("IPWatch" + i, com.date + ":" + IP + ":" + com.comment);
            i++;
        }
        // Clear any other still stored IP watches
        while (loop) {
            if (cfg.getString("IPWatch" + i) != null) {
                cfg.remove("IPWatch" + i);
                i++;
            } else {
                loop = false;
            }
        }

        i = 1;
        loop = true;

        // Save MID watches
        for (String MID : watchedMIDs.keySet()) {
            WatchComment com = watchedMIDs.get(MID);
            cfg.put("MIDWatch" + i, com.date + ":" + MID + ":" + com.comment);
            i++;
        }
        // Clear any other still stored MID watches
        while (loop) {
            if (cfg.getString("MIDWatch" + i) != null) {
                cfg.remove("MIDWatch" + i);
                i++;
            } else {
                loop = false;
            }
        }

        i = 1;
        loop = true;

        // Save Name watches
        for (String Name : watchedNames.keySet()) {
            WatchComment com = watchedNames.get(Name);
            cfg.put("NameWatch" + i, com.date + ":" + Name + ":" + com.comment);
            i++;
        }
        // Clear any other still stored Name watches
        while (loop) {
            if (cfg.getString("NameWatch" + i) != null) {
                cfg.remove("NameWatch" + i);
                i++;
            } else {
                loop = false;
            }
        }
        
        i = 1;
        loop = true;

        // Save LName watches
        for (String Name : watchedLNames.keySet()) {
            WatchComment com = watchedLNames.get(Name);
            cfg.put("LeftNameWatch" + i, com.date + ":" + Name + ":" + com.comment);
            i++;
        }
        // Clear any other still stored LName watches
        while (loop) {
            if (cfg.getString("LeftNameWatch" + i) != null) {
                cfg.remove("LeftNameWatch" + i);
                i++;
            } else {
                loop = false;
            }
        }
        
        i = 1;
        loop = true;

        // Save RName watches
        for (String Name : watchedRNames.keySet()) {
            WatchComment com = watchedRNames.get(Name);
            cfg.put("RightNameWatch" + i, com.date + ":" + Name + ":" + com.comment);
            i++;
        }
        // Clear any other still stored RName watches
        while (loop) {
            if (cfg.getString("RightNameWatch" + i) != null) {
                cfg.remove("RightNameWatch" + i);
                i++;
            } else {
                loop = false;
            }
        }
        
        i = 1;
        loop = true;

        // Save PName watches
        for (String Name : watchedPNames.keySet()) {
            WatchComment com = watchedPNames.get(Name);
            cfg.put("PartialNameWatch" + i, com.date + ":" + Name + ":" + com.comment);
            i++;
        }
        // Clear any other still stored PName watches
        while (loop) {
            if (cfg.getString("PartialNameWatch" + i) != null) {
                cfg.remove("PartialNameWatch" + i);
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
     * @param string
     *            is the string to pad.
     * @param spaces
     *            is the size of the resultant string.
     * @return the string padded with spaces to fit into spaces characters. If the string is too long to fit, it is truncated.
     */
    private String padString(String string, int spaces) {
        int whitespaces = spaces - string.length();

        if (whitespaces < 0)
            return string.substring(spaces);

        StringBuffer stringBuffer = new StringBuffer(string);
        for (int index = 0; index < whitespaces; index++)
            stringBuffer.append(' ');
        return stringBuffer.toString();
    }

    /**
     * This method clears out RecordTimes that have been recorded later than RECORD_DELAY so the info for those players can be recorded again.
     */
    private class ClearRecordTask extends TimerTask {
        public void run() {
            deleteNextTime.clear();
            deleteNextTime.addAll(justAdded);
            justAdded.clear();
        }
    }

    private class WatchComment {
        String comment;
        String date;

        public WatchComment(String date, String comment) {
            this.date = date;
            this.comment = comment;
        }

        public String toString() {
            return date + " " + comment;
        }
    }

    /**
     * Sorter class for the various watch lists.
     * <p>
     * This class allows the tracking TreeMaps for the various watches to be sorted.
     * The fields that can be sorted by are:
     * <ul>
     *  <li>Date issued;
     *  <li>Trigger used;
     *  <li>Issuer of the watch.
     * </ul>
     * The targeted map can also be sorted ascending or descending.
     * This static class does not change the inputed map.
     * <p>
     * The base for this class was blatantly stolen from:  
     * http://stackoverflow.com/questions/109383/how-to-sort-a-mapkey-value-on-the-values-in-java/2581754#2581754
     * by Carter Page.
     * 
     * @author Trancid
     *
     */
    public static class WatchListSorter {

        /**
         * Sorts the given map according to the given parameters.
         * @param map The map that needs to be sorted. Must be of the type Map<{@link String}, {@link WatchComment}>
         * @param sortBy The field that is used to sort by.
         * @param direction True for ascending, false for descending.
         * @return
         */
        public static Map<String, WatchComment> sortByValue(Map<String, WatchComment> map, final SortField sortBy, final boolean direction) {
            // Create a list of all the entries in the given map.
            List<Map.Entry<String, WatchComment>> list = new LinkedList<Map.Entry<String, WatchComment>>(map.entrySet());

            // Sort the list according to the rules inside the compare function.
            Collections.sort( list, new Comparator<Map.Entry<String, WatchComment>>() {
                public int compare( Map.Entry<String, WatchComment> wc1, Map.Entry<String, WatchComment> wc2 ) {
                    switch(sortBy) {
                    case DATE:
                        return (wc1.getValue().date).compareTo( wc2.getValue().date ) * (direction?1:-1);
                    case ISSUER:
                        return (wc1.getValue().comment).compareTo( wc2.getValue().comment ) * (direction?1:-1);
                    case TRIGGER:
                        return (wc1.getKey().compareTo(wc2.getKey())* (direction?1:-1));
                    case NONE:
                    default:
                        return (direction?1:-1);
                    }
                }
            } );

            // Put the sorted list back into a map, and return the result.
            Map<String, WatchComment> result = new LinkedHashMap<String, WatchComment>();
            for (Map.Entry<String, WatchComment> entry : list) {
                result.put( entry.getKey(), entry.getValue() );
            }
            return result;
        }
    }
    
}