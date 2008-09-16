package twcore.bots.mrarrogant;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TimerTask;
import java.util.Vector;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaList;
import twcore.core.events.KotHReset;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.util.Tools;

/**
 * Mr. Arrogant -- everyone's favorite Subspace Gestapo.
 *
 * Kicks idle players; provides a way to check logs for certain commands; can spy
 * on chat going on in a room; can act as a talking puppet.
 */
public class mrarrogant extends SubspaceBot
{
  public static final int MIN_ROAM_TIME = 1 * 60 * 1000;
  public static final int MAX_ROAM_TIME = 3 * 60 * 1000;
  public static final int IDLE_KICK_TIME = 15 * 60;
  public static final int LOWERSTAFF_IDLE_KICK_TIME = 180 * 60;
  public static final int CHECK_LOG_TIME = 30 * 1000;
  public static final int COMMAND_CLEAR_TIME = 3 * 60 * 60 * 1000;
  public static final int DIE_DELAY = 500;
  public static final int ENTER_DELAY = 5000;
  public static final int DEFAULT_CHECK_TIME = 10;

  private SimpleDateFormat dateFormat;
  private SimpleDateFormat fileNameFormat;
  private OperatorList opList;
  private String currentArena;
  private RoamTask roamTask;
  private HashSet<String> accessList;
  private String target;
  private Date lastLogDate;
  private Vector<CommandLog> commandQueue;
  private FileWriter logFile;
  private String logFileName;
  private int year;
  private boolean isStaying;
  private boolean isArroSpy;
  
  private HashSet<String> offlimitArenas;	// Contains arena names which Mr. Arrogant won't visit

  /**
   * This method initializes the mrarrogant class.
   */

  public mrarrogant(BotAction botAction)
  {
    super(botAction);

    requestEvents();
    dateFormat = new SimpleDateFormat("yyyy EEE MMM dd HH:mm:ss", Locale.US);
    roamTask = new RoamTask();
    lastLogDate = null;
    commandQueue = new Vector<CommandLog>();
    accessList = new HashSet<String>();
    isStaying = false;
    isArroSpy = false;
    offlimitArenas = new HashSet<String>();
    offlimitArenas.add("afk");		// Add any arenas here that Mr. Arrogant shouldn't visit (lowercase)
  }

  /**
   * This method handles the logon event and sets up the bot.
   *
   * @param event is the LoggedOn event to handle.
   */

  public void handleEvent(LoggedOn event)
  {
    BotSettings botSettings = m_botAction.getBotSettings();
    String initialArena = botSettings.getString("initialarena");
    String chat = botSettings.getString("chat");
    String accessString = botSettings.getString("accesslist");
    String logPath = botSettings.getString("logpath");
    fileNameFormat = new SimpleDateFormat("'" + logPath + "'MMMyyyy'.log'");
    logFileName = fileNameFormat.format(new Date());
    m_botAction.sendUnfilteredPublicMessage("?chat=" + chat);
    changeArena(initialArena);
    openFile(logFileName);
    opList = m_botAction.getOperatorList();
    setupAccessList(accessString);
    m_botAction.scheduleTaskAtFixedRate(new CheckLogTask(), 0, CHECK_LOG_TIME);
  }
  
  public void handleDisconnect() {
	  doDieCmd();
  }

  /**
   * This method handles a message event.
   *
   * @param event is the Message event to handle.
   */

  public void handleEvent(Message event)
  {
    String sender = getSender(event);
    String message = event.getMessage();
    int messageType = event.getMessageType();

    if(messageType == Message.ARENA_MESSAGE)
      handleArenaMessage(message);
    if(messageType == Message.REMOTE_PRIVATE_MESSAGE || messageType == Message.PRIVATE_MESSAGE)
      handleCommand(sender, message);
    if(messageType == Message.CHAT_MESSAGE)
      handleChatCommand(sender, message);
    if(isArroSpy && messageType == Message.SERVER_ERROR)
      handleSpyMessage(sender, message, messageType);
  }

  /**
   * This method relays a message to chat.
   *
   * @param sender is the sender of the message.
   * @param message is the message to relay.
   * @param messageType is the type of message.
   */
  private void handleSpyMessage(String sender, String message, int messageType)
  {
    String messageTypeString = getMessageTypeString(messageType);

    m_botAction.sendChatMessage(messageTypeString + ": (" + sender + ") (" + currentArena + "): " + message);
  }

  /**
   * This method gets a string representation of the message type.
   *
   * @param messageType is the type of message to handle
   * @returns a string representation of the message is returned.
   */
  private String getMessageTypeString(int messageType)
  {
    switch(messageType)
    {
      case Message.PUBLIC_MESSAGE:
        return "Public";
      case Message.PRIVATE_MESSAGE:
        return "Private";
      case Message.TEAM_MESSAGE:
        return "Team";
      case Message.OPPOSING_TEAM_MESSAGE:
        return "Opp. Team";
      case Message.ARENA_MESSAGE:
        return "Arena";
      case Message.PUBLIC_MACRO_MESSAGE:
        return "Pub. Macro";
      case Message.REMOTE_PRIVATE_MESSAGE:
        return "Private";
      case Message.WARNING_MESSAGE:
        return "Warning";
      case Message.SERVER_ERROR:
        return "Serv. Error";
      case Message.ALERT_MESSAGE:
        return "Alert";
    }
    return "Other";
  }

  private void openFile(String fileName)
  {
    try
    {
      File file = new File(fileName);
      logFile = new FileWriter(file, true);
    }
    catch(IOException e)
    {
      m_botAction.sendChatMessage("Unable to open log file.");
    }
  }

  /**
   * This method handles server output.
   *
   * @param message is the arena message from the server.
   */

  private void handleArenaMessage(String message)
  {
      if(message.startsWith("IP:"))
        updateTarget(message);
      else if(message.startsWith(target + ": " + "UserId: "))
        killIdle(message);
      else if(!message.startsWith("Ping:") &&			// Exclude all the info from *info
    		  !message.startsWith("LOSS: S2C:") &&
    		  !message.startsWith("S2C:") &&
    		  !message.startsWith("C2S CURRENT: Slow:") &&
    		  !message.startsWith("S2C CURRENT: Slow:") &&
    		  !message.startsWith("TIME: Session:") &&
    		  !message.startsWith("Bytes/Sec:") &&
    		  message.length() > 20)
        handleLogMessage(message);
  }

  /**
   * This method kills a player if he / she is idle.
   *
   * @param message is the arena message from the server.
   */

  private void killIdle(String message) {
    if(opList.isSmod(target))
      return;
    
    int idleTime = getIdleTime(message);
    if(idleTime > LOWERSTAFF_IDLE_KICK_TIME || (idleTime > IDLE_KICK_TIME && !opList.isBot(target))) {
    	String[] msg = { 
    			target+", you will now be removed from SSCU Trench Wars because you are found idle too long.",
    			"You won't be kicked if you idle in the arena 'afk' (type '?go afk' after logging in). Cya later."
    			};
    	m_botAction.smartPrivateMessageSpam(target, msg);
    	m_botAction.sendUnfilteredPrivateMessage(target, "*kill");
    }
      
  }

  /**
   * This method parses a log message.
   *
   * @param message is the log message to parse.
   */

  private void handleLogMessage(String message)
  {
    try {
      Date date = dateFormat.parse(year + " " + message.substring(0, 19));
      if(lastLogDate == null)
        lastLogDate = date;
      
      if(date.after(lastLogDate) && message.indexOf("Ext: ") == 22) {
    	  handleLogCommand(date, message.substring(27));  
      }
    } catch(ParseException pe) {}
  }

  /**
   * This method handles a log command.
   *
   * @param date is the date that the command was issued.
   * @param command is the command that was issued.
   */

  private void handleLogCommand(Date date, String logMessage)
  {
    String arena = getArena(logMessage);
    String fromPlayer = getFromPlayer(logMessage);
    String toPlayer = getToPlayer(logMessage);
    String command = getCommand(logMessage);
    CommandLog commandLog;

    if (fromPlayer != null && command != null && opList.isBot(fromPlayer))
    {
      commandLog = new CommandLog(date, arena, fromPlayer, toPlayer, command);
      if (isBadCommand(command))
        m_botAction.sendChatMessage(commandLog.toString());
      commandQueue.add(commandLog);
      writeCommand(commandLog);
    }
  }

  private void writeCommand(CommandLog commandLog)
  {
    try
    {
      String newFileName = fileNameFormat.format(commandLog.getDate());

      if(!logFileName.equals(newFileName))
        openNewLogFile(newFileName);
      logFile.write(commandLog.toString() + '\n');
      logFile.flush();
      lastLogDate = commandLog.getDate();
    }
    catch(IOException e)
    {
    }
  }

  private void openNewLogFile(String newFileName) throws IOException
  {
    logFileName = newFileName;
    logFile.close();
    openFile(logFileName);
  }


  /**
   * This method gets the sender of a command.
   *
   * @param message is the message to parse.
   * @return the sender of the command is returned.
   */

  private String getFromPlayer(String message)
  {
    int endIndex = message.indexOf(" (");
    if(endIndex == -1)
      return null;
    return message.substring(0, endIndex);
  }

  /**
   * This method gets the player that the command is destined for.  If there is
   * no target of the command, then an empty string is returned.
   *
   * @param message is the log message to parse.
   * @return the name of the target of the command is returned.  If there is
   * no target then an empty string is returned.
   */

  private String getToPlayer(String message)
  {
    int beginIndex = 0;
    int endIndex;

    for(;;)
    {
      beginIndex = message.indexOf(") to ", beginIndex);
      if(beginIndex == -1)
        return "";
      beginIndex += 5;
      endIndex = message.indexOf(":", beginIndex);
      if(endIndex != -1)
        break;
    }
    return message.substring(beginIndex, endIndex);
  }

  /**
   * This method returns the arena name from the log message.
   *
   * @param message is the log message to parse.
   * @return the arena name is returned.
   */

  public String getArena(String message)
  {
    int beginIndex = message.indexOf(" (");
    int endIndex;

    if(beginIndex == -1)
      return null;
    beginIndex += 2;
    endIndex = message.indexOf(")", beginIndex);
    if(endIndex == -1)
      return null;
    return message.substring(beginIndex, endIndex);
  }

  private String getCommand(String message)
  {
    int beginIndex = message.lastIndexOf(": *");

    if(beginIndex == -1)
      return null;
    return message.substring(beginIndex + 2);
  }

  private boolean isBadCommand(String command)
  {
    return command.startsWith("*kill") || command.startsWith("*shutup");
  }

  /**
   * This method updates the target based on the *info message that is received.
   *
   * @param message is the info message that was received.
   */

  private void updateTarget(String message)
  {
    int beginIndex = message.indexOf("TypedName:") + 10;
    int endIndex = message.indexOf("  ", beginIndex);

    target = message.substring(beginIndex, endIndex);
  }

  /**
   * This method gets the idle time of a player from an *einfo message.
   *
   * @param message is the einfo message to parse.
   * @return the idle time in seconds is returned.
   */

  private int getIdleTime(String message)
  {
    int beginIndex = message.indexOf("Idle: ") + 6;
    int endIndex = message.indexOf(" s", beginIndex);

    if(beginIndex == -1 || endIndex == -1)
      throw new RuntimeException("Cannot get idle time.");
    return Integer.parseInt(message.substring(beginIndex, endIndex));
  }

  /**
   * This method handles any private message sent to the bot.
   *
   * @param sender is the sender of the message.
   * @param message is the message that was sent.
   */

  private void handleCommand(String sender, String message)
  {
    String command = message.toLowerCase();

    try
    {
      if(opList.isHighmod(sender) || accessList.contains(sender.toLowerCase()))
      {
        if(command.startsWith("!go "))
          doGoCmd(sender, message.substring(4));
        if(command.equals("!help"))
          doPrivHelpCmd(sender);
        if(command.equals("!die"))
          doDieCmd();
      }
      else if(!opList.isBotExact(sender))
        m_botAction.sendChatMessage(sender + " said: \"" + message + "\" in " + currentArena + ".");
    }
    catch(RuntimeException e)
    {
      if(sender != null)
          m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
      m_botAction.sendChatMessage(e.getMessage());
    }
  }

  private void handleChatCommand(String sender, String message)
  {
    String command = message.toLowerCase();

    try
    {
      if(opList.isSmod(sender) || accessList.contains(sender.toLowerCase()))
      {
        if(command.equals("!arrospy"))
          doArroSpyCmd();
        if(command.equals("!stay"))
          doStayCmd();
        if(message.startsWith( "!say" ) || message.startsWith( "!//" ) || message.startsWith( "!;" ) || message.startsWith( "!!" ))
          handleSay(message);
//        if(command.startsWith("!say "))
//          doSayCmd(message.substring(5).trim());
        if(command.startsWith("!logto "))
          doLogToCmd(message.substring(7).trim());
        if(command.startsWith("!logfrom "))
          doLogFromCmd(message.substring(9).trim());
        if(command.startsWith("!log "))
          doLogCmd(message.substring(4).trim());
        if(command.equals("!log"))
          doLogCmd("");
        if(command.equals("!help"))
          doChatHelpCmd();
      }
    }
    catch(RuntimeException e)
    {
      m_botAction.sendChatMessage(e.getMessage());
    }
  }

  /**
   * This method makes arrogant say chat/team/public/private messages.
   */

  public void handleSay( String message ){
      if( message.startsWith( "!sayc " )){
          m_botAction.sendChatMessage( 1, message.substring( 6 ));
      } else if( message.startsWith( "!;" )){
          m_botAction.sendChatMessage( 1, message.substring( 2 ));
      } else if( message.startsWith( "!sayt " )){
          m_botAction.sendTeamMessage( message.substring( 6 ));
      } else if( message.startsWith( "!//" )){
          m_botAction.sendTeamMessage( message.substring( 3 ));
      } else if( message.indexOf( ":" ) != -1 ){  // Must be a private message request...
    	  // !say <name>:<text>
          int indexOfColon = message.indexOf( ":" );
          String recipient = message.substring( 5, indexOfColon );
          m_botAction.sendSmartPrivateMessage( recipient,
          message.substring( indexOfColon + 1 ));
          m_botAction.sendChatMessage("Message sent to "  + recipient );
      } else if( message.startsWith( "!say " )){
          m_botAction.sendPublicMessage( message.substring( 5 ));
      } else if( message.startsWith( "!!" )){
          m_botAction.sendPublicMessage( message.substring( 2 ));
      }
  }

  /**
   * This method toggles the arrogant spying.
   */
  private void doArroSpyCmd()
  {
    isArroSpy = !isArroSpy;
    if(isArroSpy)
      m_botAction.sendChatMessage("Arrogant Spying Enabled.");
    else
      m_botAction.sendChatMessage("Arrogant Spying Disabled.");
  }

  /**
   * This method toggles staying.
   */
  private void doStayCmd()
  {
    isStaying = !isStaying;
    if(isStaying)
      m_botAction.sendChatMessage("Arrogant Staying in " + m_botAction.getArenaName() + ".");
    else
      m_botAction.sendChatMessage("Arrogant Roaming.");
  }

  /**
   * This method handles the !logto command.  The format for !logto is:
   * !logto <ToPlayer>:<Command>:<Time>
   */
  private void doLogToCmd(String argString)
  {
    String[] argTokens = argString.split(":");

    if(argTokens.length < 1 || argTokens.length > 3)
      throw new IllegalArgumentException("Please use the following format: !LogTo <PlayerName>:<Command>:<Minutes>");

    try
    {
      String toPlayer = argTokens[0];
      String command = "";
      int time = DEFAULT_CHECK_TIME;

      switch(argTokens.length)
      {
        case 3:
          command = argTokens[1];
          time = Integer.parseInt(argTokens[2]);
          break;
        case 2:
          if(argTokens[1].startsWith("*"))
            command = argTokens[1];
          else
            time = Integer.parseInt(argTokens[1]);
          break;
      }
      if(!command.startsWith("*") && !command.equals(""))
        throw new IllegalArgumentException("Please use the following format: !LogTo <PlayerName>:<Command>:<Minutes>");

      displayLog("", toPlayer, command, time);
    }
    catch(NumberFormatException e)
    {
      throw new NumberFormatException("Please use the following format: !LogTo <PlayerName>:<Command>:<Minutes>");
    }
  }

  private void doLogFromCmd(String argString)
  {
    String[] argTokens = argString.split(":");

    if(argTokens.length < 1 || argTokens.length > 3)
      throw new IllegalArgumentException("Please use the following format: !LogFrom <PlayerName>:<Command>:<Minutes>");

    try
    {
      String fromPlayer = argTokens[0];
      String command = "";
      int time = DEFAULT_CHECK_TIME;

      switch(argTokens.length)
      {
        case 3:
          command = argTokens[1];
          time = Integer.parseInt(argTokens[2]);
          break;
        case 2:
          if(argTokens[1].startsWith("*"))
            command = argTokens[1];
          else
            time = Integer.parseInt(argTokens[1]);
          break;
      }
      if(!command.startsWith("*") && !command.equals(""))
        throw new IllegalArgumentException("Please use the following format: !LogFrom <PlayerName>:<Command>:<Minutes>");

      displayLog(fromPlayer, "", command, time);
    }
    catch(NumberFormatException e)
    {
      throw new NumberFormatException("Please use the following format: !LogFrom <PlayerName>:<Command>:<Minutes>");
    }
  }

  private void doDieCmd()
  {
    m_botAction.sendChatMessage("Logging off.");
    m_botAction.scheduleTask(new DieTask(), DIE_DELAY);
  }

  private void doLogCmd(String argString)
  {
    StringTokenizer argTokens = new StringTokenizer(argString, ":");

    if(argTokens.countTokens() > 1) {
    	m_botAction.sendChatMessage("Please use the following format: !Log <Minutes>");
    	return;
    }

    try {
      int time = DEFAULT_CHECK_TIME;
      if(argTokens.hasMoreTokens()) {
        time = Integer.parseInt(argTokens.nextToken());
      }
      
      m_botAction.sendChatMessage("Command log of the past "+time+" minute(s):");
      displayLog("", "", "", time);
    } catch(NumberFormatException e) {
    	m_botAction.sendChatMessage("Please use the following format: !Log <Minutes>");
    	return;
    }
  }

  private void displayLog(String fromPlayer, String toPlayer, String command, int time)
  {
    CommandLog commandLog;
    Date lastDate = new Date(this.lastLogDate.getTime() - time * 60 * 1000);
    int displayed = 0;

    for(int index = 0; index < commandQueue.size(); index++)
    {
      commandLog = commandQueue.get(index);
      if(commandLog.isMatch(lastDate, fromPlayer, toPlayer, command))
      {
        displayed++;
        m_botAction.sendChatMessage(commandLog.toString());
      }
    }

    if(displayed == 0)
      m_botAction.sendChatMessage("No commands matching your search criteria were recorded.");
  }

  /**
   * This method handles the !go command by moving the bot from one arena to
   * another.
   *
   * @param sender is the person who sent the command.
   * @param argString is the new arena to go to.
   */

  private void doGoCmd(String sender, String argString)
  {
    if(argString.equalsIgnoreCase(currentArena))
      throw new IllegalArgumentException("Already in that arena.");
    
    if(offlimitArenas.contains(argString.toLowerCase())) {
    	m_botAction.sendSmartPrivateMessage(sender, "I'm sorry, that arena is off limits for me.");
    } else {
    	m_botAction.sendSmartPrivateMessage(sender, "Going to " + argString + ".");
    	changeArena(argString);
    }
  }

  /**
   * This method changes the arena and updates the currentArena.  It also
   * schedules a new roam task and kills all of the idlers.
   */

  private void changeArena(String arenaName)
  {
    int beginIndex = 0;
    int endIndex;

    beginIndex = arenaName.indexOf("(Public ");
    endIndex = arenaName.indexOf(")");

    if( beginIndex != -1 && endIndex != -1 && beginIndex <= endIndex)
      arenaName = arenaName.substring(beginIndex, endIndex);

    m_botAction.changeArena(arenaName);
    currentArena = arenaName;
    m_botAction.scheduleTask(new KillIdlersTask(), ENTER_DELAY);
    scheduleRoamTask();
  }

  /**
   * This method kills all of the idlers in the arena.
   */

  private void killIdlers()
  {
    Iterator<Integer> iterator = m_botAction.getPlayerIDIterator();
    Integer playerID;
    String playerName;

    while(iterator.hasNext())
    {
      playerID = (Integer) iterator.next();
      playerName = m_botAction.getPlayerName(playerID.intValue());
      m_botAction.sendUnfilteredPrivateMessage(playerName, "*info");
      m_botAction.sendUnfilteredPrivateMessage(playerName, "*einfo");
    }
  }

  /**
   * This method displays the help message.
   *
   * @param sender is the person to receive the help message.
   */

  private void doPrivHelpCmd(String sender)
  {
    String[] message =
    {
      "!Go <ArenaName>                     -- Moves the bot to <ArenaName>.",
      "!Help                               -- Displays this help message.",
      "!Die                                -- Logs the bot off."
    };
    m_botAction.smartPrivateMessageSpam(sender, message);
  }

  /**
   * This method displays the chat help message.
   */

  private void doChatHelpCmd()
  {
    m_botAction.sendChatMessage("!Go <ArenaName>                [PM] - Moves the bot to <ArenaName>.");
    m_botAction.sendChatMessage("!Help                          [PM] - Displays this help message.");
    m_botAction.sendChatMessage("!Die                           [PM] - Logs the bot off.");
    m_botAction.sendChatMessage("!Log [Time/minutes]          [Chat] - Displays last commands of last [Time].");
    m_botAction.sendChatMessage("!LogFrom <Name>[:Cmd][:Time] [Chat] - Displays last commands from <Name>.");
    m_botAction.sendChatMessage("!LogTo <Name>[:Cmd][:Time]   [Chat] - Displays last commands to <Name>.");
    m_botAction.sendChatMessage("!Stay                        [Chat] - Causes bot to stay in arena.");
    m_botAction.sendChatMessage("!ArroSpy                     [Chat] - Reports chat in current arena.");
    m_botAction.sendChatMessage("!say   OR  !!                [Chat] - Sends message to standard chat.");
    m_botAction.sendChatMessage("!sayt  OR  !//               [Chat] - Sends message to team chat.");
    m_botAction.sendChatMessage("!sayc  OR  !;                [Chat] - Sends message to chat channel.");
    m_botAction.sendChatMessage(":<name>:<msg>                [Chat] - Sends PM <msg> to <name>.");
  }

  /**
   * This method handles the ArenaList event and takes the bot to a random
   * arena.
   *
   * @param event is the ArenaList to handle.
   */

  public void handleEvent(ArenaList event)
  {
        try{
          String[] arenaNames = event.getArenaNames();

            Comparator<String> a = new Comparator<String>(){
                public int compare(String a, String b){
                    if (Tools.isAllDigits(a) && !a.equals("") ) {
                        if (Tools.isAllDigits(b) && !b.equals("") ){
                            if (Integer.parseInt(a) < Integer.parseInt(b))
                                return -1;
                            else
                                return 1;
                        } else
                            return -1;
                    } else if (Tools.isAllDigits(b))
                        return 1;
                    else
                        return a.compareToIgnoreCase(b);
                };
            };

          Arrays.sort(arenaNames, a);
          int endIndex = 0;
          for(int k = 0;k < arenaNames.length;k++) {
            if(Tools.isAllDigits(arenaNames[k])) endIndex = k;
            else break;
          }
          int seed = (int) (Math.random() * 100);
          int start = 0;
          int arenaIndex = -1;
          for(int k = 50;arenaIndex != endIndex;k/=2) {
              arenaIndex++;
              if(seed >= start && seed < (start + k))
                break;
              start += k;
          }
          changeArena(arenaNames[arenaIndex]);
        }
        catch(Exception e){}
  }
  
  /**
   * Handles restarting of the KOTH game
   * 
   * @param event is the event to handle.
   */
  public void handleEvent(KotHReset event) {
      if(event.isEnabled() && event.getPlayerID()==-1) {
          // Make the bot ignore the KOTH game (send that he's out immediately after restarting the game)
          m_botAction.endKOTH();
      }
  }

  /**
   * This method requests the events that the bot will use.
   */

  private void requestEvents()
  {
    EventRequester eventRequester = m_botAction.getEventRequester();

    eventRequester.request(EventRequester.MESSAGE);
    eventRequester.request(EventRequester.ARENA_LIST);
    eventRequester.request(EventRequester.KOTH_RESET);
  }

  /**
   * This method gets the sender of a message regardless of its type.
   *
   * @param message is the message that was sent.
   * @return the sender is returned.
   */

  private String getSender(Message message)
  {
    int messageType = message.getMessageType();
    if(messageType == Message.REMOTE_PRIVATE_MESSAGE || messageType == Message.CHAT_MESSAGE)
      return message.getMessager();
    int senderID = message.getPlayerID();
    return m_botAction.getPlayerName(senderID);
  }

  /**
   * This method schedules a new roam task that is to happen at a random time.
   */

  private void scheduleRoamTask()
  {
    int roamTime = MIN_ROAM_TIME + (int) (Math.random() * (MAX_ROAM_TIME - MIN_ROAM_TIME));
    m_botAction.cancelTask(roamTask);
    roamTask = new RoamTask();
    
    //m_botAction.sendChatMessage("Moving to a different arena in "+roamTime/1000+" seconds.");
        
    m_botAction.scheduleTask(roamTask, roamTime);
  }

  private void setupAccessList(String accessString)
  {
    StringTokenizer accessTokens = new StringTokenizer(accessString, ":");

    accessList.clear();

    while(accessTokens.hasMoreTokens())
      accessList.add(accessTokens.nextToken().toLowerCase());
  }

  /**
   * This method clears commands older than COMMAND_CLEAR_TIME
   */

  private void clearOldCommands()
  {
    CommandLog commandLog;
    Date commandDate;
    
    if(this.lastLogDate == null)
    	return;
    
    Date removeDate = new Date(this.lastLogDate.getTime() - COMMAND_CLEAR_TIME);

    while(!commandQueue.isEmpty())
    {
      commandLog = commandQueue.get(0);
      commandDate = commandLog.getDate();
      if(removeDate.before(commandDate))
        break;
      commandQueue.remove(0);
    }
  }

  /**
   * This helper method makes the bot move around periodically.
   */

  private class RoamTask extends TimerTask
  {
    public void run()
    {
      if(!isStaying)
        m_botAction.requestArenaList();
      scheduleRoamTask();
    }
  }

  private class CheckLogTask extends TimerTask
  {
    public void run()
    {
      GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
      year = calendar.get(GregorianCalendar.YEAR);

      m_botAction.sendUnfilteredPublicMessage("*log");
      clearOldCommands();
    }
  }

  private class CommandLog
  {
    private Date date;
    private String arena;
    private String fromPlayer;
    private String toPlayer;
    private String command;

    public CommandLog(Date date, String arena, String fromPlayer, String toPlayer, String command)
    {
      this.date = date;
      this.arena = arena;
      this.fromPlayer = fromPlayer;
      this.toPlayer = toPlayer;
      this.command = command;
    }

    public Date getDate()
    {
      return date;
    }

    public String getArena()
    {
      return arena;
    }

    public String getFromPlayer()
    {
      return fromPlayer;
    }

    public String getToPlayer()
    {
      return toPlayer;
    }

    public String getCommand()
    {
      return command;
    }

    public String toString()
    {
      if(toPlayer.equals(""))
        return date + ":  " + fromPlayer + " (" + arena + ") " + command;
      return date + ":  " + fromPlayer + " (" + arena + ") to " + toPlayer + ": " + command;
    }

    public boolean isMatch(Date currentDate, String fromPlayerMask, String toPlayerMask, String commandMask)
    {
      return currentDate.before(date) && contains(fromPlayer, fromPlayerMask) &&
             contains(toPlayer, toPlayerMask) && contains(command, commandMask);
    }

    private boolean contains(String string1, String string2)
    {
      String lower1 = string1.toLowerCase();
      String lower2 = string2.toLowerCase();

      return lower1.indexOf(lower2) != -1;
    }
  }

  private class DieTask extends TimerTask
  {
    public void run()
    {
      try
      {
        logFile.close();
        m_botAction.cancelTasks();
        m_botAction.die();
      }
      catch(IOException e)
      {
        m_botAction.sendChatMessage("Unable to close log file.");
      }
    }
  }

  private class KillIdlersTask extends TimerTask
  {
    public void run()
    {
      killIdlers();
    }
  }
}
