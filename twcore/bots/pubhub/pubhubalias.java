package twcore.bots.pubhub;

import java.util.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import twcore.core.*;
import twcore.misc.database.DBPlayerData;
import twcore.misc.pubcommon.*;

public class pubhubalias extends PubBotModule
{
  public static final String DATABASE = "local";
  public static final int REMOVE_DELAY = 3 * 60 * 60 * 1000;
  public static final int CLEAR_DELAY = 30 * 60 * 1000;
  public static final int DEFAULT_DAYS = 180;

  private Set justAdded;
  private Set deleteNextTime;
  private Set watchedIPs;
  private Set watchedNames;
  private Set watchedMIDs;
  private ClearRecordTask clearRecordTask;
  
  private int m_maxRecords = 50;
  private boolean m_sortByName = true;

  /**
   * This method initializes the module.
   */
  public void initializeModule()
  {
    justAdded = Collections.synchronizedSet(new HashSet());
    deleteNextTime = Collections.synchronizedSet(new HashSet());
    watchedIPs = Collections.synchronizedSet(new HashSet());
    watchedNames = Collections.synchronizedSet(new HashSet());
    watchedMIDs = Collections.synchronizedSet(new HashSet());
    clearRecordTask = new ClearRecordTask();

    m_botAction.scheduleTaskAtFixedRate(clearRecordTask, CLEAR_DELAY, CLEAR_DELAY);
  }

  public void requestEvents(EventRequester eventRequester)
  {
    eventRequester.request(EventRequester.MESSAGE);
  }

  public void doAltNickCmd(String argString, boolean compareIP, boolean compareMID)
  {
    StringTokenizer argTokens = new StringTokenizer(argString, ":");

    if(argTokens.countTokens() < 1 || argTokens.countTokens() > 2)
      throw new IllegalArgumentException("Please use the following format: !altnick <PlayerName>:<Date Updated>");

    String playerName = argTokens.nextToken();
    int updateDays = DEFAULT_DAYS;

    if(argTokens.hasMoreTokens())
      updateDays = Integer.parseInt(argTokens.nextToken());

    try
    {
      String queryString =
      "SELECT * " +
      "FROM tblAlias A1, tblAlias A2, tblUser U1, tblUser U2 " +
      "WHERE U1.fcUserName = '" + Tools.addSlashesToString(playerName) + "' " +
      "AND U1.fnUserID = A1.fnUserID ";     
      if(compareIP)
          queryString += "AND A1.fcIP = A2.fcIP ";
      if(compareMID)          
          queryString += "AND A1.fnMachineID = A2.fnMachineID ";
      queryString += "AND A2.fnUserID = U2.fnUserID ";      
      if( m_sortByName )
          queryString += "ORDER BY U2.fcUserName DESC, A2.fdUpdated";
      else
          queryString += "ORDER BY A2.fdUpdated DESC, U2.fcUserName";
          
      ResultSet resultSet = m_botAction.SQLQuery(DATABASE, queryString);
      int results = 0;
      String lastName = "";
      String currName;
      if(resultSet == null)
        throw new RuntimeException("ERROR: Null result set returned; connection may be down.");
      resultSet.afterLast();
      while( resultSet.previous() )
      {
        currName = resultSet.getString("U2.fcUserName");
        if(!currName.equalsIgnoreCase(lastName)) {
          if( results <= m_maxRecords )
            m_botAction.sendChatMessage("Name: " + padString(currName, 25) + " Last Updated: " + resultSet.getDate("A2.fdUpdated") + " " + resultSet.getTime("A2.fdUpdated"));
          results++;
        }
        lastName = currName;
      }
      resultSet.close();
      if(results == 0)
        m_botAction.sendChatMessage("Player not in database.");
      if( results > m_maxRecords )
        m_botAction.sendChatMessage( results - m_maxRecords + " records not shown.  !maxrecords # to show (current: " + m_maxRecords + ")" );
    }
    catch(SQLException e)
    {
      throw new RuntimeException("ERROR: Cannot connect to database.");
    }
  }

  public void doAltIPCmd(String argString, boolean compareMID)
  {
    StringTokenizer argTokens = new StringTokenizer(argString, ":");

    if(argTokens.countTokens() < 1 || argTokens.countTokens() > 2)
      throw new IllegalArgumentException("Please use the following format: !altip <PlayerName>:<Date Updated>");

    String playerIP = argTokens.nextToken();
    int updateDays = DEFAULT_DAYS;

    if(argTokens.hasMoreTokens())
      updateDays = Integer.parseInt(argTokens.nextToken());

    try
    {        
      String queryString =
      "SELECT * " +
      "FROM tblAlias A1, tblAlias A2, tblUser U1, tblUser U2 " +
      "WHERE A1.fcIP = '" + playerIP + "' " +
      "AND U1.fnUserID = A1.fnUserID " +
      "AND A1.fcIP = A2.fcIP ";
      if(compareMID)
        queryString += "AND A1.fnMachineID = A2.fnMachineID ";      
      queryString += "AND A2.fnUserID = U2.fnUserID ";
      if( m_sortByName )
          queryString += "ORDER BY U2.fcUserName DESC, A2.fdUpdated";
      else
          queryString += "ORDER BY A2.fdUpdated DESC, U2.fcUserName";
      
      ResultSet resultSet = m_botAction.SQLQuery(DATABASE, queryString);
      int results = 0;
      String lastName = "";
      String currName;
      if(resultSet == null)
        throw new RuntimeException("ERROR: Null result set returned; connection may be down.");
      resultSet.afterLast();
      while( resultSet.previous() )
      {
        currName = resultSet.getString("U2.fcUserName");
        if(!currName.equalsIgnoreCase(lastName)) {
          if( results <= m_maxRecords )
            m_botAction.sendChatMessage("Name: " + padString(currName, 25) + " Last Updated: " + resultSet.getDate("A2.fdUpdated") + " " + resultSet.getTime("A2.fdUpdated"));
          results++;
        }
        lastName = currName;
      }
      resultSet.close();
      if(results == 0)
        m_botAction.sendChatMessage("Player not in database.");
      if( results > m_maxRecords )
          m_botAction.sendChatMessage( results - m_maxRecords + " records not shown.  !maxrecords # to show (current: " + m_maxRecords + ")" );
    }
    catch(SQLException e)
    {
      throw new RuntimeException("ERROR: Cannot connect to database.");
    }
  }

  public void doAltMacIDCmd(String argString, boolean compareIP)
  {
      StringTokenizer argTokens = new StringTokenizer(argString, ":");

      if(argTokens.countTokens() < 1 || argTokens.countTokens() > 2)
        throw new IllegalArgumentException("Please use the following format: !altmid <PlayerName>:<Date Updated>");

      String playerMID = argTokens.nextToken();
      int updateDays = DEFAULT_DAYS;

      if(argTokens.hasMoreTokens())
        updateDays = Integer.parseInt(argTokens.nextToken());

      try
      {
        String queryString =
        "SELECT * " +
        "FROM tblAlias A1, tblAlias A2, tblUser U1, tblUser U2 " +
        "WHERE A1.fnMachineID = '" + playerMID + "' " +
        "AND U1.fnUserID = A1.fnUserID ";
        if(compareIP)
          queryString += "AND A1.fcIP = A2.fcIP ";
        queryString += 
        "AND A1.fnMachineID = A2.fnMachineID " +
        "AND A2.fnUserID = U2.fnUserID ";
        if( m_sortByName )
            queryString += "ORDER BY U2.fcUserName DESC, A2.fdUpdated";
        else
            queryString += "ORDER BY A2.fdUpdated DESC, U2.fcUserName";
        
        ResultSet resultSet = m_botAction.SQLQuery(DATABASE, queryString);
        int results = 0;
        String lastName = "";
        String currName;
        if(resultSet == null)
          throw new RuntimeException("ERROR: Null result set returned; connection may be down.");
        resultSet.afterLast();
        while( resultSet.previous() )
        {
          currName = resultSet.getString("U2.fcUserName");
          if(!currName.equalsIgnoreCase(lastName)) {
            if( results <= m_maxRecords )
              m_botAction.sendChatMessage("Name: " + padString(currName, 25) + " Last Updated: " + resultSet.getDate("A2.fdUpdated") + " " + resultSet.getTime("A2.fdUpdated"));
            results++;
          }
          lastName = currName;
        }
        resultSet.close();
        if(results == 0)
          m_botAction.sendChatMessage("Player not in database.");
        if( results > m_maxRecords )
            m_botAction.sendChatMessage( results - m_maxRecords + " records not shown.  !maxrecords # to show (current: " + m_maxRecords + ")" );
      }
      catch(SQLException e)
      {
        throw new RuntimeException("ERROR: Cannot connect to database.");
      }
  }

  /**
   * Returns IP and MID entries for a particular player.
   * @param argString
   * @throws SQLException
   */
  public void doInfoCmd(String argString) throws SQLException
  {
      try
      {
          ResultSet resultSet = m_botAction.SQLQuery(DATABASE,
                  "SELECT * " +
                  "FROM tblUser U, tblAlias A " +
                  "WHERE U.fcUserName = '" + Tools.addSlashesToString(argString) + "' " +
          		  "AND U.fnUserID = A.fnUserID " +
                  "ORDER BY A.fdUpdated DESC" );

          if(resultSet == null)
              throw new RuntimeException("ERROR: Null result set returned; connection may be down.");
          
          resultSet.afterLast();
          m_botAction.sendChatMessage("Info results for '" + argString + "':" );
          int results = 0;
          while( resultSet.previous() ) {
              if( results <= m_maxRecords ) {
                  m_botAction.sendChatMessage( padString("MID: " + resultSet.getInt("A.fnMachineID"), 15) + "  IP: " + padString(resultSet.getString("A.fcIP"), 15) +
                                                         " Updated " + resultSet.getDate("A.fdUpdated") + " - " + resultSet.getInt("A.fnTimesUpdated") + " update(s)" );
                  results++;                  
              }
          }
          resultSet.close();
          if( results == 0 )
              m_botAction.sendChatMessage( "None found." );
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
      "!AltNick[-noip|-nomid] <PlayerName>:<Days>",
      "!AltIP[-nomid]         <IP>:<Days>",
      "!AltMID[-noip]         <MacID>:<Days>",
      "!Info                  <PlayerName>",
      "!MaxResults            <Max # results to return>",
      "!NameWatch             <Name>",
      "!IPWatch               <IP>",
      "!MIDWatch              <MID>",
      "!ClearNameWatch        (clears all names being watched)",
      "!ClearIPWatch          (clears all IPs being watched)",
      "!ClearMIDWatch         (clears all MIDs being watched)",
      "!ShowWatches           (shows all watches in effect)",
      "!SortByName / !SortByDate   (Selects sorting method)",
      "!Help",
      "(-noip and -nomid additions to cmds will force-ignore IP/MID, respectively)"
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
      "AND A1.fcIP = A2.fcIP " +
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
      resultSet.close();
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
  public void doIPWatchCmd( String IP ) {
      if( watchedIPs.contains( IP ) ) {
          watchedIPs.remove( IP );
          m_botAction.sendChatMessage( "IP watching disabled for IPs starting with " + IP );          
      } else {
          watchedIPs.add( IP );
          m_botAction.sendChatMessage( "IP watching enabled for IPs starting with " + IP );          
      }          
  }

  /**
   * Starts watching for a name to log on.
   * @param name Name to watch for
   */
  public void doNameWatchCmd( String name ) {
      if( watchedNames.contains( name.toLowerCase() ) ) {
          watchedNames.remove( name.toLowerCase() );
          m_botAction.sendChatMessage( "Login watching disabled for '" + name + "'." );          
      } else {
          watchedNames.add( name.toLowerCase() );
          m_botAction.sendChatMessage( "Login watching enabled for '" + name + "'." );          
      }          
  }

  /**
   * Starts watching for a given MacID.
   * @param MID MID to watch for
   */
  public void doMIDWatchCmd( String MID ) {
      if( watchedMIDs.contains( MID ) ) {
          watchedMIDs.remove( MID );
          m_botAction.sendChatMessage( "MID watching disabled for MID: " + MID );          
      } else {
          watchedMIDs.add( MID );
          m_botAction.sendChatMessage( "MID watching enabled for MID: " + MID );          
      }          
  }

  /**
   * Stops all IP watching.
   */
  public void doClearIPWatchCmd( ) {
      watchedIPs.clear();
      m_botAction.sendChatMessage( "All watched IPs cleared." );                
  }

  /**
   * Stops all name watching.
   */
  public void doClearNameWatchCmd( ) {
      watchedNames.clear();
      m_botAction.sendChatMessage( "All watched names cleared." );                
  }
        
  /**
   * Stops all MacID watching.
   */
  public void doClearMIDWatchCmd( ) {
      watchedNames.clear();
      m_botAction.sendChatMessage( "All watched MIDs cleared." );                
  }

  /**
   * Shows current watches.
   */
  public void doShowWatchesCmd( ) {
      Iterator i;
      i = watchedIPs.iterator();
      if( i.hasNext() ) {
        m_botAction.sendChatMessage( "------------" );
        m_botAction.sendChatMessage( "IP watches" );
        m_botAction.sendChatMessage( "------------" );
        do {
          m_botAction.sendChatMessage( (String)i.next() );
        } while( i.hasNext() );
      }
      i = watchedMIDs.iterator();
      if( i.hasNext() ) {
        m_botAction.sendChatMessage( "------------" );
        m_botAction.sendChatMessage( "MID watches" );
        m_botAction.sendChatMessage( "------------" );
        do {
          m_botAction.sendChatMessage( (String)i.next() );
        } while( i.hasNext() );
      }
      i = watchedNames.iterator();
      if( i.hasNext() ) {
        m_botAction.sendChatMessage( "------------" );
        m_botAction.sendChatMessage( "Name watches" );
        m_botAction.sendChatMessage( "------------" );
        do {
          m_botAction.sendChatMessage( (String)i.next() );
        } while( i.hasNext() );
      }      
  }

  public void handleChatMessage(String sender, String message)
  {
    String command = message.toLowerCase();

    try
    {
      if(command.equals("!recordinfo"))
        doRecordInfoCmd(sender);
      else if(command.equals("!help"))
        doHelpCmd(sender);
      else if(command.startsWith("!altnick-nomid "))
        doAltNickCmd(message.substring(15).trim(), true, false);
      else if(command.startsWith("!altnick-noip "))
        doAltNickCmd(message.substring(14).trim(), false, true);
      else if(command.startsWith("!altnick "))
        doAltNickCmd(message.substring(9).trim(), true, true);
      else if(command.startsWith("!altip-nomid "))
        doAltIPCmd(message.substring(13).trim(), false);
      else if(command.startsWith("!altip "))
        doAltIPCmd(message.substring(7).trim(), true);
      else if(command.startsWith("!altmid-noip "))
        doAltMacIDCmd(message.substring(13).trim(), false);
      else if(command.startsWith("!altmid "))
        doAltMacIDCmd(message.substring(8).trim(), true);
      else if(command.startsWith("!alttwl "))
        doAltTWLCmd(message.substring(8).trim());
      else if(command.startsWith("!info "))
        doInfoCmd(message.substring(6).trim());
      else if(command.startsWith("!maxrecords "))
        doMaxRecordsCmd(message.substring(12).trim());
      else if(command.startsWith("!ipwatch "))
        doIPWatchCmd(message.substring(9).trim());        
      else if(command.startsWith("!namewatch "))
        doNameWatchCmd(message.substring(11).trim());
      else if(command.startsWith("!midwatch "))
        doMIDWatchCmd(message.substring(10).trim());
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

  public void gotEntered(String botSender, String argString)
  {
    if(!justAdded.contains(argString.toLowerCase()) && !deleteNextTime.contains(argString.toLowerCase()))
      m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("notrecorded " + argString, botSender));
  }

  public void gotRecord(String argString)
  {
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
      if( watchedNames.contains( name.toLowerCase() ) ) {
          m_botAction.sendChatMessage( "NAMEWATCH: '" + name + "' logged in.  (IP: " + IP + ", MID: " + MacID + ")" );                          
      }          
  }

  /**
   * Check if an IP is being watched for, and notify on chat if so.
   * @param name Name of player
   * @param IP IP to check
   * @param MacId MacID of player
   */
  public void checkIP( String name, String IP, String MacID ) {
      Iterator i = watchedIPs.iterator();
      
      while( i.hasNext() ) {
          String IPfragment = (String)i.next();
          if( IP.startsWith( IPfragment ) ) {
              m_botAction.sendChatMessage( "IPWATCH: Match on '" + name + "' - " + IP + " (matches " + IPfragment + "*)  MID: " + MacID );                          
          }
      }
  }

  /**
   * Check if an MID is being watched for, and notify on chat if so.
   * @param name Name of player
   * @param IP IP of player
   * @param MacId MacID to checl
   */
  public void checkMID( String name, String IP, String MacID ) {
      if( watchedMIDs.contains( MacID ) ) {
          m_botAction.sendChatMessage( "MIDWATCH: Match on '" + name + "' - " + MacID + "  IP: " + IP );                          
      }          
  }

  /**
   * This method handles an IPC message.
   *
   * @param botSender is the bot that sent the command.
   * @param message is the message that is being sent via IPC.
   */
  public void handleIPCMessage(String botSender, String message)
  {
    if(message.startsWith("entered "))
      gotEntered(botSender, message.substring(8));
    if(message.startsWith("record "))
      gotRecord(message.substring(7));
  }

  /**
   * This method handles an InterProcess event.
   *
   * @param event is the IPC event to handle.
   */
  public void handleEvent(InterProcessEvent event)
  {
    IPCMessage ipcMessage = (IPCMessage) event.getObject();
    String botName = m_botAction.getBotName();
    String message = ipcMessage.getMessage();
    String botSender = event.getSenderName();

    try
    {
      if(botName.equals(ipcMessage.getRecipient()))
        handleIPCMessage(botSender, message);
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
    clearRecordTask.cancel();
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
      ResultSet resultSet = m_botAction.SQLQuery(DATABASE,
      "SELECT * " +
      "FROM tblAlias " +
      "WHERE fnUserID = " + userID + " " +
      "AND fcIP = '" + playerIP + "' " +
      "AND fnMachineID = " + playerMacID);
      if(!resultSet.next())
        return -1;
      int results = resultSet.getInt("fnAliasID");
      resultSet.close();
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
      ResultSet r = m_botAction.SQLQuery(DATABASE,
      "INSERT INTO tblAlias " +
      "(fnUserID, fcIP, fnMachineID, fnTimesUpdated, fdRecorded, fdUpdated) " +
      "VALUES (" + userID + ", '" + playerIP + "', " + playerMacID + ", 1, NOW(), NOW())");
      if (r != null) r.close();
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
      if (r != null) r.close();
    }
    catch(SQLException e)
    {
      throw new RuntimeException("ERROR: Unable to update alias entry.");
    }
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