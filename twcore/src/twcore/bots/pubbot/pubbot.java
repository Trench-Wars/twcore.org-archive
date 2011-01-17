package twcore.bots.pubbot;

import java.util.TimerTask;

import twcore.bots.ModuleHandler;
import twcore.bots.PubBotModule;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
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
import twcore.core.events.KotHReset;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerBanner;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.Prize;
import twcore.core.events.SQLResultEvent;
import twcore.core.events.ScoreReset;
import twcore.core.events.ScoreUpdate;
import twcore.core.events.SoccerGoal;
import twcore.core.events.WatchDamage;
import twcore.core.events.WeaponFired;
import twcore.core.util.ipc.IPCMessage;

public class pubbot extends SubspaceBot
{
  public static final String IPCCHANNEL = "pubBots";
  public static final String IPCCHANNEL2 = "messages";
  public static final String IPCCHAT = "pubbotChat";
  public static final String IPCSILENCE = "pubbotsilence";
  public static final int UPDATE_CHECK_DELAY = 500;
  public static final int LOGOFF_TIMEOUT_DELAY = 5 * 1000;
  public static final int LOG_OFF_DELAY = 200;

  private ModuleHandler moduleHandler;
  private String pubHubBot;
  private String currentArena;
  private String botName;
  private boolean connected;

  private boolean movingGoCmd = false;  // true if this bot received a "go " command and is moving to the new arena

  /**
   * This method initializes the bot.
   *
   * @param botAction is the BotAction object of the bot.
   */

  public pubbot(BotAction botAction)
  {
    super(botAction);

    connected = false;
    requestEvents();
  }

  public void handleDisconnect() {
      m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("dying", pubHubBot));
      if( moduleHandler != null )
          moduleHandler.unloadAllModules();
      m_botAction.cancelTasks();
      m_botAction.ipcUnSubscribe(IPCCHANNEL);
      m_botAction.ipcUnSubscribe(IPCCHANNEL2);
      m_botAction.ipcUnSubscribe(IPCSILENCE);
  }

  /**
   * This method handles the logging on of the bot.
   */

  public void handleEvent(LoggedOn event)
  {
    BotSettings botSettings = m_botAction.getBotSettings();

    moduleHandler = new ModuleHandler(m_botAction, m_botAction.getGeneralSettings().getString( "Core Location" ) + "/twcore/bots/pubbot", "pubbot");

    // Join the initial arena from settings
    currentArena = botSettings.getString("InitialArena");
    m_botAction.changeArena(currentArena);

    botName = m_botAction.getBotName();
    m_botAction.ipcSubscribe(IPCCHANNEL);
    m_botAction.ipcSubscribe(IPCCHANNEL2);
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("spawned"));         // Let the pubhub know that this bot has spawned
    m_botAction.ipcSubscribe(IPCSILENCE);
    m_botAction.scheduleTask(new LogOffTimeoutTask(), LOGOFF_TIMEOUT_DELAY);
    moduleHandler.handleEvent(event);
  }

  public void doDieCmd(boolean notify)
  {
    if(notify)
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("dying", pubHubBot));
    moduleHandler.unloadAllModules();
    m_botAction.scheduleTask(new LogOffTask(), LOG_OFF_DELAY);
  }

  /**
   * This method initializes the bot and sets the connected flag.
   *
   * @param botSender is the name of the PubHubBot.
   */

  public void gotInitializedCmd(String botSender)
  {
    if(!connected)
    {
      pubHubBot = botSender;
      connected = true;
    }
  }

  /**
   * This method logs the bot off.
   *
   * @param recipient is the bot that is supposed to recieve the command.  If
   * it is null then all pubbots recieve it and thus a chat message is not
   * necessary.
   */

  public void gotDieCmd(String recipient)
  {
    if(botName.equals(recipient))
      doDieCmd(true);
    else
        doDieCmd(false);

  }

  /**
   * This method makes the bot change arenas.
   *
   * @param argString is the new arena to go to.
   */

  public void gotGoCmd(String argString) {
      currentArena = argString;
      movingGoCmd = true;
      m_botAction.changeArena(currentArena);
  }

  /**
   * This method checks to see if loading a module is possible (ie the module
   * is present).  If it is, it sends an "loading" message to the pubhub bot.
   *
   * @param argString is the name of the module to load.
   */

  public void gotLoadCmd(String argString)
  {
    if(moduleHandler.isModule(argString)) {
        PubBotModule module;
        moduleHandler.loadModule(argString);
        module = (PubBotModule) moduleHandler.getModule(argString);
        module.initializeModule(IPCCHANNEL, pubHubBot);
    }
  }

  /**
   * This method checks to see if unloading a module is possible (ie the module
   * is present).  If it is, it sends an "unloading" message to the pubhub bot.
   *
   * @param argString is the name of the module to unload.
   */

  public void gotUnloadCmd(String argString)
  {
    if(!moduleHandler.isModule(argString))
      throw new IllegalArgumentException("ERROR: Could not find " + argString + ".");
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("unloading " + argString, pubHubBot));
  }

  /**
   * This method unloads a module from the bot.  It is called when the pubhub
   * bot sends the unloaded message via IPC.
   *
   * @param argString is the module to unload.
   */

  public void gotUnloadedCmd(String argString)
  {
    moduleHandler.unloadModule(argString);
    m_botAction.sendChatMessage(argString + " has been unloaded from " + currentArena);
  }

  /**
   * This method handles the join chat command from the pubhubbot.
   *
   * @param argString is the chat to join.
   */

  public void gotJoinChatCmd(String argString)
  {
    m_botAction.sendUnfilteredPublicMessage("?chat=" + argString);
  }

  /**
   * This method handles IPC commands.
   *
   * @param botSender is the bot that sent the IPC command.
   * @param recipient is the bot that is suppoed to get the command.
   * @param message is the IPC message.
   */

  public void handleBotIPC(String botSender, String recipient, String sender, String message)
  {
    if(message.equalsIgnoreCase("initialize"))
      gotInitializedCmd(botSender);
    if(message.equalsIgnoreCase("die"))
      gotDieCmd(recipient);
    if(startsWithIgnoreCase(message, "location "))
      currentArena = message.substring(9);
    if(startsWithIgnoreCase(message, "go "))
      gotGoCmd(message.substring(3).trim());
    if(startsWithIgnoreCase(message, "load "))
      gotLoadCmd(message.substring(5).trim());
    if(startsWithIgnoreCase(message, "unloaded "))
      gotUnloadedCmd(message.substring(9).trim());
    if(startsWithIgnoreCase(message, "joinchat "))
      gotJoinChatCmd(message.substring(9).trim());
//    moduleHandler.handleEvent(new IPCCommandEvent(message, recipient, sender, botSender));
  }

  /**
   * This method handles all of the player commands that the hub bot
   * interpreted.
   *
   * @param botSender is the bot that issued the IPC command.
   * @param sender is the person that issued the command.
   * @param message is the IPC message.
   */

  public void handlePlayerIPC(String botSender, String sender, String message)
  {
    if(startsWithIgnoreCase(message, "!load "))
      gotLoadCmd(message.substring(6).trim());
    if(startsWithIgnoreCase(message, "!unload "))
      gotUnloadCmd(message.substring(8).trim());
//    moduleHandler.handleEvent(new CommandEvent(sender, message));
  }

  /**
   * This method handles an InterProcessEvent.
   *
   * @param event is the InterProcessEvent to handle.
   */

  public void handleEvent(InterProcessEvent event)
  {
	  // If the event.getObject() is anything else then the IPCMessage (pubbotchatIPC f.ex) then return
	  if(event.getObject() instanceof IPCMessage) {
		  IPCMessage ipcMessage = (IPCMessage) event.getObject();
		  String message = ipcMessage.getMessage();
		  String recipient = ipcMessage.getRecipient();
		  String sender = ipcMessage.getSender();
		  String botSender = event.getSenderName();

		  if(recipient == null || recipient.equals(botName)) {
			  if(sender == null)
				  handleBotIPC(botSender, recipient, sender, message);
			  else
				  handlePlayerIPC(botSender, sender, message);
		  }
	  }
	  
	  moduleHandler.handleEvent(event);
  }

  /**
   * This method handles the playerLeft event.  If the arena is empty, the
   * bot will die.
   *
   * @param event is the PlayerLeft event.
   */

  public void handleEvent(PlayerLeft event)
  {
    try {
        m_botAction.scheduleTask(new ArenaDyingTask(), UPDATE_CHECK_DELAY);
    } catch (Exception e) {
    }
    moduleHandler.handleEvent(event);
  }

  /**
   * This method handles a message event.
   *
   * @param event is the message event to handle.
   */

  public void handleEvent(Message event)
  {
    moduleHandler.handleEvent(event);
  }

  /**
   * This method handles an ArenaList event.
   *
   * @param event is the ArenaList event.
   */

  public void handleEvent(ArenaList event)
  {
    moduleHandler.handleEvent(event);
  }

  /**
   * This method requests the events that the bot will use.
   */

  private void requestEvents()
  {
    EventRequester eventRequester = m_botAction.getEventRequester();
    eventRequester.request(EventRequester.ARENA_LIST);
    eventRequester.request(EventRequester.ARENA_JOINED);
    eventRequester.request(EventRequester.PLAYER_LEFT);
    eventRequester.request(EventRequester.PLAYER_ENTERED);
    eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
    eventRequester.request(EventRequester.MESSAGE);
    eventRequester.request(EventRequester.KOTH_RESET);
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
   * This class logs the bot off if it has not connected to a PubHub in
   * LOGOFF_TIMEOUT_DELAY miliseconds.
   */

  private class LogOffTimeoutTask extends TimerTask
  {
    public void run()
    {
      if(!connected)
        m_botAction.die();
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
        m_botAction.ipcUnSubscribe(IPCCHANNEL);
        m_botAction.ipcUnSubscribe(IPCCHANNEL2);
        m_botAction.ipcUnSubscribe(IPCSILENCE);
        m_botAction.cancelTasks();
        m_botAction.die( "normal log-off" );
    }
  }

  /**
   * This class checks to see if an arena is dead after a player leaves it.
   * If it is it will log the bot off.
   */

  private class ArenaDyingTask extends TimerTask
  {
	  public void run()
	  {
		  if(m_botAction.getArenaSize() == 1) {
    	  
			  // The arena "tw/trenchwars" must always have a pubbot there
			  // to avoid players to evade banc and other stuff
			  if (!m_botAction.getArenaName().equals("tw")
					  && !m_botAction.getArenaName().equals("trenchwars"))
				  doDieCmd(true);
		  }
	  }
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

  public void handleEvent(PlayerEntered event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(PlayerPosition event)
  {
    moduleHandler.handleEvent(event);
  }

  public void handleEvent(PlayerDeath event)
  {
    moduleHandler.handleEvent(event);
  }
  
  public void handleEvent(PlayerBanner event)
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
      if(movingGoCmd) {
          m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("arrivedArena", pubHubBot));
          movingGoCmd = false;
      }
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

  public void handleEvent(SQLResultEvent event)
  {
    moduleHandler.handleEvent(event);
  }
}
