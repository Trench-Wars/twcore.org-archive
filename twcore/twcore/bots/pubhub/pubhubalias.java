package twcore.bots.pubhub;

import java.util.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import twcore.core.*;
import twcore.misc.database.DBPlayerData;
import twcore.misc.pubcommon.*;

public class pubhubalias extends PubBotModule
{
  public static final String DATABASE = "server";
  public static final int REMOVE_DELAY = 3 * 60 * 60 * 1000;
  public static final int CLEAR_DELAY = 30 * 60 * 1000;
  public static final int DEFAULT_DAYS = 180;

  private Set justAdded;
  private Set deleteNextTime;
  private ClearRecordTask clearRecordTask;

  /**
   * This method initializes the module.
   */

  public void initializeModule()
  {
    justAdded = Collections.synchronizedSet(new HashSet());
    deleteNextTime = Collections.synchronizedSet(new HashSet());
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
      queryString +=
      "AND A2.fnUserID = U2.fnUserID " +
      "ORDER BY U2.fcUserName, A2.fdUpdated";          
      ResultSet resultSet = m_botAction.SQLQuery(DATABASE, queryString);
      
      int results = 0;
      String lastName = "";
      String currName;
      if(resultSet == null)
        throw new RuntimeException("ERROR: Null result set returned; connnection may be down.");
      for(; resultSet.next(); results++)
      {
        currName = resultSet.getString("U2.fcUserName");
        if(!currName.equalsIgnoreCase(lastName))
          m_botAction.sendChatMessage("Name: " + padString(currName, 25) + " Last Updated: " + resultSet.getDate("A2.fdUpdated") + " " + resultSet.getTime("A2.fdUpdated"));
        lastName = currName;
      }
      resultSet.close();
      if(results == 0)
        m_botAction.sendChatMessage("Player not in database.");
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
      queryString +=
      "AND A2.fnUserID = U2.fnUserID " +
      "ORDER BY U2.fcUserName";      
      
      ResultSet resultSet = m_botAction.SQLQuery(DATABASE, queryString);
      int results = 0;
      String lastName = "";
      String currName;
      if(resultSet == null)
        throw new RuntimeException("ERROR: Null result set returned; connnection may be down.");
      for(; resultSet.next(); results++)
      {
        currName = resultSet.getString("U2.fcUserName");
        if(!currName.equalsIgnoreCase(lastName))
          m_botAction.sendChatMessage("Name: " + padString(currName, 25) + " Last Updated: " + resultSet.getDate("A2.fdUpdated") + " " + resultSet.getTime("A2.fdUpdated"));
        lastName = currName;
      }
      resultSet.close();
      if(results == 0)
        m_botAction.sendChatMessage("Player not in database.");
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
        "AND A2.fnUserID = U2.fnUserID " +
        "ORDER BY U2.fcUserName";
        
        ResultSet resultSet = m_botAction.SQLQuery(DATABASE, queryString);
        int results = 0;
        String lastName = "";
        String currName;
        if(resultSet == null)
          throw new RuntimeException("ERROR: Null result set returned; connnection may be down.");
        for(; resultSet.next(); results++)
        {
          currName = resultSet.getString("U2.fcUserName");
          if(!currName.equalsIgnoreCase(lastName))
            m_botAction.sendChatMessage("Name: " + padString(currName, 25) + " Last Updated: " + resultSet.getDate("A2.fdUpdated") + " " + resultSet.getTime("A2.fdUpdated"));
          lastName = currName;
        }
        resultSet.close();
        if(results == 0)
          m_botAction.sendChatMessage("Player not in database.");
      }
      catch(SQLException e)
      {
        throw new RuntimeException("ERROR: Cannot connect to database.");
      }
  }

  // made useful.  -qan
  public void doInfoCmd(String argString) throws SQLException
  {
      try
      {
          ResultSet resultSet = m_botAction.SQLQuery(DATABASE,
                  "SELECT * " +
                  "FROM tblUser U, tblAlias A " +
                  "WHERE U.fcUserName = '" + Tools.addSlashesToString(argString) + "' " +
          		  "AND U.fnUserID = A.fnUserID");

          if(resultSet == null)
              throw new RuntimeException("ERROR: Null result set returned; connnection may be down.");
        
          if( resultSet.next() ) {
              m_botAction.sendChatMessage("Name: " + padString(resultSet.getString("U.fcUserName"), 25) + " Last Updated: " + resultSet.getDate("A.fdUpdated") + " " + resultSet.getTime("A.fdUpdated"));
              m_botAction.sendChatMessage("Last reg'd info - MID: " + resultSet.getInt("A.fnMachineID") + "  IP: " + resultSet.getString("A.fcIP") + "  (Times updated: " + resultSet.getInt("A.fnTimesUpdated") + ")" );
          }
          resultSet.close();
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
      "!AltNick  [-noip|-nomid] <PlayerName>:<Days>",
      "!AltIP    [-nomid]       <IP>:<Days>",
      "!AltMID   [-noip]        <MacID>:<Days>",
      "!Info                    <PlayerName>",
      "!Help",
      "(-noip and -nomid will force-ignore IP/MID, respectively)"
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
        currName = resultSet.getString("U2.fcUserName");
        if(!currName.equalsIgnoreCase(lastName))
          m_botAction.sendChatMessage("Name: " + padString(currName, 25) + " Squad: " + resultSet.getString("T.fcTeamName"));
        lastName = currName;
      }
      resultSet.close();
      if(results == 0)
        m_botAction.sendChatMessage("Player is not on a TWL squad.");
    }
    catch(SQLException e)
    {
      throw new RuntimeException("ERROR: Cannot connect to database.");
    }
  }

  public void handleChatMessage(String sender, String message)
  {
    String command = message.toLowerCase();

    try
    {
      if(command.equals("!recordinfo"))
        doRecordInfoCmd(sender);
      if(command.startsWith("!altnick -nomid "))
        doAltNickCmd(message.substring(16).trim(), true, false);
      if(command.startsWith("!altnick -noip "))
        doAltNickCmd(message.substring(15).trim(), false, true);
      if(command.startsWith("!altnick "))
        doAltNickCmd(message.substring(9).trim(), true, true);
      if(command.startsWith("!altip -nomid "))
        doAltIPCmd(message.substring(14).trim(), false);
      if(command.startsWith("!altip "))
        doAltIPCmd(message.substring(7).trim(), true);
      if(command.startsWith("!altmid -noip "))
        doAltMacIDCmd(message.substring(14).trim(), false);
      if(command.startsWith("!altmid "))
        doAltMacIDCmd(message.substring(8).trim(), true);
      if(command.startsWith("!alttwl "))
        doAltTWLCmd(message.substring(8).trim());
      if(command.startsWith("!info "))
        doInfoCmd(message.substring(6).trim());
      if(command.equals("!help"))
        doHelpCmd(sender);
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