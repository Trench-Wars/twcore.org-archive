package twcore.bots.pubhub;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.ModuleHandler;
import twcore.bots.PubBotModule;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.ArenaList;
import twcore.core.events.BallPosition;
import twcore.core.events.FileArrived;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FlagDropped;
import twcore.core.events.FlagPosition;
import twcore.core.events.FlagReward;
import twcore.core.events.FlagVictory;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.Prize;
import twcore.core.events.ScoreReset;
import twcore.core.events.ScoreUpdate;
import twcore.core.events.SoccerGoal;
import twcore.core.events.WatchDamage;
import twcore.core.events.WeaponFired;
import twcore.core.util.ipc.IPCChatMessage;
import twcore.core.util.ipc.IPCMessage;

public class pubhub extends SubspaceBot
{
  public static final char CHAT_DELIM = ':';
  public static final String IPCCHANNEL = "pubBots";
  public static final String IPCCHAT = "pubbotChat";
  public static final String IPCSILENCE = "pubbotsilence";
  public static final String IPCPUBSTATS = "pubstats";
  public static final int UPDATE_CHECK_DELAY = 500;
  public static final int CHECK_DELAY = 5 * 60 * 1000;
  public static final int LOG_OFF_DELAY = 200;
  public static final int AUTOLOAD_DELAY = 30000;

  private int SPAWN_DELAY = 25000;
  private OperatorList opList;
  private ModuleHandler moduleHandler;
  private HashMap<String, String> botList;
  private HashSet<String> autoLoadModuleList;
  private HashMap<String,HashSet<String>> arenaModules;
  private HashSet<String> permitList;
  private Vector<String> arenaList;
  private HashSet<String> nonPubArenaList;
  private String hubBot;
  private String pubHubChat;
  private String botName;

  private int numPubBots;
  private boolean gotArenaList;

  /**
   * This method initializes the bot.
   *
   * @param botAction is the BotAction object of the bot.
   */

  public pubhub(BotAction botAction)
  {
    super(botAction);

    moduleHandler = new ModuleHandler(m_botAction, m_botAction.getGeneralSettings().getString( "Core Location" ) + "/twcore/bots/pubhub", "pubhub");
    botList = new HashMap<String, String>();
    autoLoadModuleList = new HashSet<String>();
    arenaModules = new HashMap<String, HashSet<String>>();
    permitList = new HashSet<String>();
    arenaList = new Vector<String>();
    nonPubArenaList = new HashSet<String>();
    numPubBots = 0;
    gotArenaList = false;
    requestEvents();

    if (m_botAction.getCoreData().getGeneralSettings().getString( "Server" ).equals("localhost"))
        SPAWN_DELAY = 100;

  }

  /**
   * This method handles a LoggedOn event.
   *
   * @param event is the message event to handle.
   */

  public void handleEvent(LoggedOn event)
  {
    try
    {
      getBotSettings();
      opList = m_botAction.getOperatorList();
      botName = m_botAction.getBotName();
      m_botAction.ipcSubscribe(IPCCHANNEL);
      m_botAction.ipcSubscribe(IPCCHAT);
      m_botAction.ipcSubscribe(IPCSILENCE);
      m_botAction.ipcSubscribe(IPCPUBSTATS);
      m_botAction.sendUnfilteredPublicMessage("?chat=" + pubHubChat);
      m_botAction.scheduleTask(new CheckPubsTask(), SPAWN_DELAY);
      moduleHandler.handleEvent(event);
    }
    catch(Exception e)
    {
      m_botAction.sendArenaMessage(e.getMessage());
      m_botAction.scheduleTask(new LogOffTask(), LOG_OFF_DELAY);
    }
  }

  /**
   * This method handles the ArenaList event and fills the arenaList with
   * all of the names of the pubs that the bots are supposed to go to.
   *
   * @param event is the ArenaList event.
   */

  public void handleEvent(ArenaList event)
  {
    String[] arenaNames = event.getArenaNames();
    String arenaName;

    arenaList.clear();

    for(int index = 0; index < arenaNames.length; index++)
    {
      arenaName = arenaNames[index].toLowerCase();
      if(isPubArena(arenaName) || nonPubArenaList.contains(arenaName))
        arenaList.add(arenaName);
    }
    Collections.sort(arenaList);
    gotArenaList = true;
    moduleHandler.handleEvent(event);
  }

  /**
   * This method lists all of the modules that the bot can load.
   */

  public void doListModulesCmd(String sender)
  {
    Vector<String> moduleNames = new Vector<String>(moduleHandler.getModuleNames());
    String moduleName;

    if(moduleNames.isEmpty())
      throw new RuntimeException("ERROR: There are no modules available to this bot.");

    m_botAction.sendSmartPrivateMessage(sender, "A * indicates a loaded module.");
    for(int index = 0; index < moduleNames.size(); index++)
    {
      moduleName = moduleNames.get(index);
      if(moduleHandler.isLoaded(moduleName))
        moduleName.concat(" *");
      m_botAction.sendSmartPrivateMessage(sender, moduleName);
    }
  }

  /**
   * This method respawns all of the PubBot.
   */

  public void doRespawnCmd()
  {
    m_botAction.sendChatMessage("Respawning pub bots.");
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("die"));
    m_botAction.scheduleTask(new CheckPubsTask(), SPAWN_DELAY);
  }

  /**
   * This method reloads the non pub arena list, the auto load modules and the
   * permit list without rebooting the bots.
   */

  public void doUpdateInfoCmd()
  {
    BotSettings botSettings = m_botAction.getBotSettings();
    String nonPubArenaString = botSettings.getString("NonPubArenas");
    String autoLoadModulesString = botSettings.getString("AutoLoadModules");
    String permitListString = botSettings.getString("Permit");

    setupNonPubArenas(nonPubArenaString);
    setupAutoLoadModules(autoLoadModulesString);
    setupPermitList(permitListString);
    m_botAction.sendChatMessage("PubBot info updated.");
  }

  /**
   * This method displays where all the bots are currently.
   */

  public void doWhereCmd()
  {
    Vector<String> botNames = new Vector<String>(botList.keySet());
    Collections.sort(botNames);
    StringBuffer line = new StringBuffer();
    String botName;

    for(int index = 0; index < botNames.size(); index++)
    {
      botName = botNames.get(index);
      line.append(padString(botName + ": " + botList.get(botName), 30));
      if((index + 1) % 3 == 0)
      {
        m_botAction.sendChatMessage(line.toString());
        line = new StringBuffer();
      }
    }
    m_botAction.sendChatMessage(line.toString());
  }

  /**
   * This logs all of the bots off including the pubbot.
   */

  public void doOffCmd()
  {
    m_botAction.sendChatMessage("Logging all pub bots off.");
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("die"));
    m_botAction.scheduleTask(new LogOffTask(), LOG_OFF_DELAY);
  }
  
  public void doHubHelpCmd(String sender) {
    m_botAction.sendSmartPrivateMessage(sender, "!respawn      - Respawns all PubBots.");
    m_botAction.sendSmartPrivateMessage(sender, "!off          - Logs the Hub and all PubBots off.");
    m_botAction.sendSmartPrivateMessage(sender, "!where        - Shows location of all PubBots.");
    m_botAction.sendSmartPrivateMessage(sender, "!listmodules  - Gives a list of pub modules available.");
    m_botAction.sendSmartPrivateMessage(sender, "!updateinfo   - Updates pubbot settings from the CFG.");
    m_botAction.sendSmartPrivateMessage(sender, "!help         - Gets you a date and a real job.  Probably.");
    
  }

  /**
   * This method handles a message from the pubHubBot.
   *
   * @param message is the message from the pubHubBot.
   */

  public void handleHubBotMessage(String message)
  {
    if(message.equals("Maximum number of bots of this type has been reached.") ||
       message.equals("Bot failed to log in."))
    {
      m_botAction.sendChatMessage("ERROR: Cannot spawn bot from " + hubBot + ".");
      m_botAction.scheduleTask(new CheckPubsTask(), CHECK_DELAY);
    }
  }

  /**
   * This method handles a message and performs the appropriate command.
   *
   * @param sender is the sender of the message.
   * @param message is the unprocessed chat message.
   */

  public void handleMessage(String sender, String message)
  {
    try
    {
      if(sender.equals(hubBot))
        handleHubBotMessage(message);
      //if(startsWithIgnoreCase(message, "!spawn ")) {}
      if(message.equalsIgnoreCase("!respawn"))
        doRespawnCmd();
      if(message.equalsIgnoreCase("!updateinfo"))
        doUpdateInfoCmd();
      if(message.equalsIgnoreCase("!where"))
        doWhereCmd();
      if(message.equalsIgnoreCase("!listmodules"))
        doListModulesCmd(sender);
      if(message.equalsIgnoreCase("!off"))
        doOffCmd();
      if(message.equalsIgnoreCase("!help")) {
        doHubHelpCmd(sender);
//      moduleHandler.handleEvent(new CommandEvent(sender, message));
      }
    }
    catch(Exception e)
    {
      m_botAction.sendChatMessage(e.getMessage());
    }
  }

  /**
   * This method handles a chat message and distributes it to the appropriate
   * bot.
   *
   * @param sender is the sender of the message.
   * @param message is the unprocessed chat message.
   */

  public void handleChatMessage(String sender, String message)
  {
    String botTarget = getBotTarget(message);

    if(botTarget != null)
      m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage(getBotMessage(message), botTarget, sender));
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage(message, null, sender));
    handleMessage(sender, message);
  }

  /**
   * This method handles a message event.
   *
   * @param event is the message event to handle.
   */

  public void handleEvent(Message event)
  {
    String sender = getSender(event);
    String message = event.getMessage();
    int messageType = event.getMessageType();

    if(opList.isSmod(sender) || (sender != null && permitList.contains(sender.toLowerCase())))
    {
      if(messageType == Message.CHAT_MESSAGE)
        handleChatMessage(sender, message);
      if(messageType == Message.PRIVATE_MESSAGE || messageType == Message.REMOTE_PRIVATE_MESSAGE)
        handleMessage(sender, message);
    }
    moduleHandler.handleEvent(event);
  }

  /**
   * This method asks a PubBot to die.
   *
   * @param pubBot is the bot to die.
   */

  public void sendDieCmd(String pubBot)
  {
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("die", pubBot));
  }

  /**
   * This method sends a PubBot to another arena.
   *
   * @param is the arena to send the bot to.
   */

  public void sendGoCmd(String pubBot, String arenaName)
  {
    if(botList.containsValue(arenaName))
      throw new IllegalArgumentException("ERROR: A PubBot is already present in that arena.");
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("go " + arenaName, pubBot));
  }

  /**
   * This method sends all of the apropriate IPC messages required to autoload
   * all of the modules.
   *
   * @param pubBot is the name of the bot to send the messages to.
   */

  public void sendAutoLoadModules(String pubBot) {
	  
	  String arena = botList.get(pubBot);
	  HashSet<String> modules;
	  
	  if(arena != null) {
		  if(arenaModules.containsKey(arena)) {			// Autoload predefined specific modules
			  modules = arenaModules.get(arena);
		  } else {										// Autoload default modules
			  modules = autoLoadModuleList;
		  }
	  } else {											 // Autoload default modules + warning
		  m_botAction.sendChatMessage("ERROR: Cannot autoload modules on "+pubBot+": Arena/location is unknown - autoloading default modules.");
		  modules = autoLoadModuleList;
	  }
	  
	  // Send the IPC Messages for loading the modules to the Pubbot
	  for(String moduleName:modules) {
		  m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("load " + moduleName, pubBot));
	  }
	  
  }

  /**
   * This method handles a bot logging off of.
   *
   * @param botSender is the bot sending the command.
   */

  public void gotDyingCmd(String botSender)
  {
    if(numPubBots <= 0)
      throw new RuntimeException("ERROR: Unregistered bot disconnected.");
    numPubBots--;
    botList.remove(botSender);
  }

  /**
   * This method is called when a bot gets spawned.  It sends the bot to the
   * appropriate arena and initializes it.
   *
   * @param botSender is the bot that was just spawned.
   */

  public void gotSpawnedCmd(String botSender)
  {
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("initialize", botSender));
    updateArenaInfo();
    m_botAction.scheduleTaskAtFixedRate(new SpawnGoTask(botSender), UPDATE_CHECK_DELAY, UPDATE_CHECK_DELAY);
  }

  /**
   * This method loads and initializes a PubBotModule.  It then sends an IPC
   * message that signifies that loading was successful.
   *
   * @param botSender is the bot that sent the loading command.
   * @param argString is the name of the module.
   */

  public void gotLoadingCmd(String botSender, String argString)
  {
    PubBotModule module;

    if(!moduleHandler.isLoaded(argString))
    {
      moduleHandler.loadModule(argString);
      module = (PubBotModule) moduleHandler.getModule(argString);
      module.initializeModule(IPCCHANNEL, botName);
    }
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("loaded " + argString, botSender));
  }

  /**
   * This method unloads and a PubBotModule.  It then sends an IPC message that
   * signifies that unloading was successful.
   *
   * @param botSender is the bot that sent the unloading command.
   * @param argString is the name of the module.
   */

  public void gotUnloadingCmd(String botSender, String argString)
  {
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("unloaded " + argString, botSender));
  }

  /**
   * This method handles the here command sent by a PubBot.  It updates the
   * bot list with the location of the bot.
   *
   * @param botSender is the bot sending the command.
   * @param argString is the arena that the bot is in.
   */

  public void gotHereCmd(String botSender, String argString)
  {
    if(!botSender.equals(botName))
      botList.put(botSender, argString);
  }

  /**
   * This method handles IPC commands.
   *
   * @param botSender is the bot that sent the command.
   * @param sender is the person that issued the command.
   * @param message is the IPC message.
   */

  public void handleIPC(String botSender, String recipient, String sender, String message)
  {
    try
    {
      if(message.equalsIgnoreCase("dying"))
        gotDyingCmd(botSender);
      if(message.equalsIgnoreCase("spawned"))
        gotSpawnedCmd(botSender);
      if(startsWithIgnoreCase(message, "loading "))
        gotLoadingCmd(botSender, message.substring(8).trim());
      if(startsWithIgnoreCase(message, "unloading "))
        gotUnloadingCmd(botSender, message.substring(10).trim());
      if(startsWithIgnoreCase(message, "here "))
        gotHereCmd(botSender, message.substring(5).trim());
//      moduleHandler.handleEvent(new IPCCommandEvent(message, recipient, sender, botSender));
    }
    catch(Exception e)
    {
      m_botAction.sendChatMessage(e.getMessage());
    }
  }

  /**
   * This method handles the specific pubbotchatIPC and should be overridden to be used
   *
   * @param ipc
   */
  public void handleChatIPC(IPCChatMessage ipc) {

  }

  /**
   * This method handles an InterProcessEvent.
   *
   * @param event is the InterProcessEvent to handle.
   */

  public void handleEvent(InterProcessEvent event)
  {
	  // If the event.getObject() is anything else then the IPCMessage (pubbotchatIPC f.ex) then return
	  if(event.getObject() instanceof IPCMessage == false) {
		  moduleHandler.handleEvent(event);
		  return;
	  }

	  IPCMessage ipcMessage = (IPCMessage) event.getObject();
	  String message = ipcMessage.getMessage();
	  String recipient = ipcMessage.getRecipient();
	  String sender = ipcMessage.getSender();
	  String botSender = event.getSenderName();

	  if(recipient == null || recipient.equals(botName))
		  handleIPC(botSender, recipient, sender, message);

	  moduleHandler.handleEvent(event);
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
   * This method updates the arena information.
   */

  private void updateArenaInfo()
  {
    gotArenaList = false;
    botList.clear();
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("where"));
    m_botAction.requestArenaList();
  }

  /**
   * This method returns the destination arena based on the current arena list
   * and bot locations.
   *
   * @return an empty arena for the bot to go is returned.  If there are no
   * arenas for the bot to go to, null is returned.
   */

  private String getDestinationArena()
  {
    String arenaName;
    String lowerName;

    for(int index = 0; index < arenaList.size(); index++)
    {
      arenaName = arenaList.get(index);
      lowerName = arenaName.toLowerCase();
      if(!botList.containsValue(lowerName))
        return lowerName;
    }
    return null;
  }

  /**
   * This checks to see if an arena is public arena.
   *
   * @param arenaName is the arena to check.
   * @return true is returned if it is a pub arena.
   */

  private boolean isPubArena(String arenaName)
  {
    for(int index = 0; index < arenaName.length(); index++)
      if(!Character.isDigit(arenaName.charAt(index)))
        return false;
    return true;
  }

  /**
   * This method requests all of the events that the bot uses.
   */

  private void requestEvents()
  {
    EventRequester eventRequester = m_botAction.getEventRequester();

    eventRequester.request(EventRequester.ARENA_LIST);
    eventRequester.request(EventRequester.MESSAGE);
  }

  /**
   * This method gets all of the bot settings from the cfg file.
   */

  private void getBotSettings()
  {
    BotSettings botSettings = m_botAction.getBotSettings();
    String nonPubArenaString = botSettings.getString("NonPubArenas");
    String autoLoadModulesString = botSettings.getString("AutoLoadModules");
    String permitListString = botSettings.getString("Permit");
    String initialArena = botSettings.getString("InitialArena");
    hubBot = botSettings.getString("HubBot");
    pubHubChat = botSettings.getString("PubHubChat");

    setupNonPubArenas(nonPubArenaString);
    setupAutoLoadModules(autoLoadModulesString);
    setupArenaModules(botSettings);
    setupPermitList(permitListString);
    m_botAction.changeArena(initialArena);
  }

  /**
   * This method sets up the non pub arenas that the pubbots will go to.
   *
   * @param nonPubArenaString is a string containing the arena names seperated
   * by spaces.
   */

  private void setupNonPubArenas(String nonPubArenaString)
  {
    StringTokenizer arenaTokens = new StringTokenizer(nonPubArenaString);
    String arenaName;
    nonPubArenaList.clear();

    while(arenaTokens.hasMoreTokens())
    {
      arenaName = arenaTokens.nextToken().toLowerCase();
      if(!isPubArena(arenaName))
        nonPubArenaList.add(arenaName);
    }
  }

  /**
   * This method sets up the modules that each bot will autoload with.
   *
   * @param autoLoadModulesString is a string containing the module names
   * seperated by spaces.
   */

  private void setupAutoLoadModules(String autoLoadModulesString)
  {
    StringTokenizer moduleTokens = new StringTokenizer(autoLoadModulesString);
    String moduleName;
    autoLoadModuleList.clear();

    while(moduleTokens.hasMoreTokens())
    {
      moduleName = moduleTokens.nextToken().toLowerCase();
      if(moduleHandler.isModule(moduleName))
        autoLoadModuleList.add(moduleName);
    }
  }
  
  /**
   * This method sets up the modules that each bot will load with in a specified arena.
   *
   *
   */
  private void setupArenaModules(BotSettings botSettings) {
	  // Cycle the nonPubArenaList and get each setting from the configuration file
	  arenaModules.clear();
	  
	  for(String arena:nonPubArenaList) {
		  String modulesSetting = botSettings.getString("ArenaModules-"+arena);
		  
		  if(modulesSetting != null && modulesSetting.length() > 0) {
			  // Cycle through the modules and add each module to the HashMap, the arena as key
			  StringTokenizer moduleTokens = new StringTokenizer(modulesSetting);
	
			  while(moduleTokens.hasMoreTokens()) {
				  String moduleName = moduleTokens.nextToken().toLowerCase();
				  if(moduleHandler.isModule(moduleName)) {
					  HashSet<String> moduleSet;
					  
					  if(arenaModules.containsKey(arena)) {
						  moduleSet = arenaModules.get(arena);
					  } else {
						  moduleSet = new HashSet<String>();
					  }
					  moduleSet.add(moduleName);
					  arenaModules.put(arena, moduleSet);
				  }
			  }
		  }
	  }
  }

  /**
   * This method sets up the permit list which grants users access to the
   * pubhub commands.
   *
   * @param permitListString is a string containing the player names
   * seperated by spaces.
   */

  private void setupPermitList(String permitListString)
  {
    StringTokenizer permitTokens = new StringTokenizer(permitListString, ":");
    String playerName;
    permitList.clear();

    while(permitTokens.hasMoreTokens())
    {
      playerName = permitTokens.nextToken().toLowerCase();
      permitList.add(playerName);
    }
  }

  /**
   * This method gets the bot target from the name of the bot from a chat
   * command.  Chat commands are structured as such: <Bot Target>:<Message>.
   * The bot target can be an arena name or a bot name.  A fuzzy string
   * compare is performed on it.
   *
   * @param message is the chat message to analyze.
   * @return the name of the bot is returned.  If no bot name is found then
   * null is returned.
   */

  private String getBotTarget(String message)
  {
    String targetString;
    String botTarget;
    String arenaName;
    int targetIndex = message.indexOf(CHAT_DELIM);

    if(targetIndex == -1)
      return null;
    targetString = message.substring(0, targetIndex);
    arenaName = fuzzyStringCompare(botList.values(), targetString);
    botTarget = getTargetFromArena(arenaName);
    if(botTarget == null)
      botTarget = fuzzyStringCompare(botList.keySet(), targetString);
    return botTarget;
  }

  /**
   * This method gets the bot name from the arena name.
   *
   * @param arenaName
   */

  private String getTargetFromArena(String arenaName)
  {
    Set<String> set = botList.keySet();
    Iterator<String> iterator = set.iterator();
    String botName;

    if(arenaName == null)
      return null;
    while(iterator.hasNext())
    {
      botName = iterator.next();
      if(arenaName.equals(botList.get(botName)))
        return botName;
    }
    return null;
  }

  /**
   * This method does a fuzzy string compare on a collection of strings.  If no
   * suitable match is found then null is returned.
   *
   * @param collection is the collection of strings to search.
   * @param targetString is the target string to use.
   * @return the closest string from collection is returned.  If there are no
   * suitable matches then null is returned.
   */

  private String fuzzyStringCompare(Collection collection, String targetString)
  {
    Iterator iterator = collection.iterator();
    String string;
    String bestMatch = null;

    while(iterator.hasNext())
    {
      string = (String) iterator.next();
      if(string.equalsIgnoreCase(targetString))
        return string;
      if(startsWithIgnoreCase(string, targetString))
        bestMatch = string;
    }
    return bestMatch;
  }

  /**
   * This method is String.startsWith but case insensitive.
   *
   * @param string is the bigger string.
   * @param startString is the smaller string.
   * @return true is returned if the string starts with startString.
   */

  private boolean startsWithIgnoreCase(String string, String startString)
  {
    char stringChar;
    char startStringChar;

    if(startString.length() > string.length())
      return false;
    for(int index = 0; index < startString.length(); index++)
    {
      stringChar = string.charAt(index);
      startStringChar = startString.charAt(index);
      if(Character.toLowerCase(stringChar) != Character.toLowerCase(startStringChar))
        return false;
    }
    return true;
  }

  /**
   * This method gets the a message without the target name from a chat message.
   *
   * @param message is the chat message.
   * @return the message without the target name is returned.
   */

  private String getBotMessage(String message)
  {
    int messageIndex = message.indexOf(CHAT_DELIM) + 1;

    return message.substring(messageIndex);
  }

  /**
   * This method gets the sender from a message Event.
   *
   * @param event is the message event to analyze.
   * @return the name of the sender is returned.  If the sender cannot be
   * determined then null is returned.
   */

  private String getSender(Message event)
  {
    int messageType = event.getMessageType();

    if(messageType == Message.REMOTE_PRIVATE_MESSAGE || messageType == Message.CHAT_MESSAGE)
      return event.getMessager();
    int senderID = event.getPlayerID();
    return m_botAction.getPlayerName(senderID);
  }

  /**
   * This class checks the pub to see if a bot needs to be spawned by requesting
   * arena info.  CheckSpawnTask waits for the info to come before spawning
   * the bot.
   */

  private class CheckPubsTask extends TimerTask
  {
    public void run()
    {
      updateArenaInfo();
      m_botAction.scheduleTaskAtFixedRate(new CheckSpawnTask(), UPDATE_CHECK_DELAY, UPDATE_CHECK_DELAY);
    }
  }

  /**
   * This class checks to see if a bot should be spawned.  If no bot needs to
   * be spawned, then it will schedule another CheckPubsTask in CHECK_DELAY
   * seconds.
   */

  private class CheckSpawnTask extends TimerTask
  {
    public void run()
    {
      if(gotArenaList && botList.size() == numPubBots)
      {
        if(numPubBots < arenaList.size())
          m_botAction.sendSmartPrivateMessage(hubBot, "!spawn PubBot");
        else
          m_botAction.scheduleTask(new CheckPubsTask(), CHECK_DELAY);
        cancel();
      }
    }
  }

  /**
   * This class moves a newly spawned bot to where it needs to go.
   */

  private class SpawnGoTask extends TimerTask
  {
    private String pubBot;

    public SpawnGoTask(String pubBot)
    {
      this.pubBot = pubBot;
    }

    public void run()
    {
      String destinationArena = getDestinationArena();

      if(gotArenaList && botList.size() - 1 == numPubBots)
      {
        if(destinationArena == null)
          sendDieCmd(pubBot);
        else
        {
          m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("joinchat " + pubHubChat, pubBot));
          sendGoCmd(pubBot, destinationArena);
          m_botAction.scheduleTask(new AutoLoadTask(pubBot), AUTOLOAD_DELAY);
          numPubBots++;
        }
        m_botAction.scheduleTask(new CheckPubsTask(), SPAWN_DELAY);
        cancel();
      }
    }
  }

  private class AutoLoadTask extends TimerTask
  {
    private String pubBot;

    public AutoLoadTask(String pubBot)
    {
      this.pubBot = pubBot;
    }

    public void run()
    {
      sendAutoLoadModules(pubBot);
    }
  }

  /**
   * This class logs the bot off of the server.  It is used to put a bit of
   * delay between the last command and the die command.
   */

  private class LogOffTask extends TimerTask
  {
    public void run()
    {
      m_botAction.die();
    }
  }

  public void handleEvent(PlayerEntered event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(PlayerPosition event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(PlayerLeft event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(PlayerDeath event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(Prize event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(ScoreUpdate event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(WeaponFired event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(FrequencyChange event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(FrequencyShipChange event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(FileArrived event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(ArenaJoined event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(FlagVictory event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(FlagReward event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(ScoreReset event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(WatchDamage event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(SoccerGoal event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(BallPosition event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(FlagPosition event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(FlagDropped event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(FlagClaimed event)
  {
    moduleHandler.handleEvent(event);
  }
}
