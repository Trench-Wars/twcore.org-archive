package twcore.bots.pubbot;

import java.util.*;
import twcore.core.*;
import twcore.misc.pubcommon.*;

public class pubbot extends SubspaceBot
{
  public static final String IPCCHANNEL = "pubBots";
  public static final String IPCCHANNEL2 = "messages";
  public static final int UPDATE_CHECK_DELAY = 500;
  public static final int LOGOFF_TIMEOUT_DELAY = 5 * 1000;
  public static final int LOG_OFF_DELAY = 200;

  private ModuleHandler moduleHandler;
  private OperatorList opList;
  private String pubHubBot;
  private String currentArena;
  private String botName;
  private boolean connected;
  private boolean gotArenaList;

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

  /**
   * This method handles the logging on of the bot.
   */

  public void handleEvent(LoggedOn event)
  {
    BotSettings botSettings = m_botAction.getBotSettings();

    moduleHandler = new ModuleHandler(m_botAction, m_botAction.getGeneralSettings().getString( "Core Location" ) + "/twcore/bots/pubbot", "pubbot");
    currentArena = botSettings.getString("InitialArena");
    m_botAction.changeArena(currentArena);
    opList = m_botAction.getOperatorList();
    botName = m_botAction.getBotName();
    m_botAction.ipcSubscribe(IPCCHANNEL);
    m_botAction.ipcSubscribe(IPCCHANNEL2);
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("spawned"));
    m_botAction.scheduleTask(new LogOffTimeoutTask(), LOGOFF_TIMEOUT_DELAY);
    moduleHandler.handleEvent(event);
  }

  public void doDieCmd(boolean notify)
  {
    if(notify)
      m_botAction.sendChatMessage("Logging out of " + currentArena + ".");
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("dying", pubHubBot));
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
   * it is null than all pubbots recieve it and thus a chat message is not
   * necessary.
   */

  public void gotDieCmd(String recipient)
  {
    if(botName.equals(recipient))
      doDieCmd(true);
    doDieCmd(false);

  }

  /**
   * This method handles the where command from the pubhubbot.  It waits for the
   * arena info to be updated and then sends the current arena back to the
   * pubhubbot.
   */

  public void gotWhereCmd()
  {
    updateArenaInfo();
    m_botAction.scheduleTaskAtFixedRate(new SendWhereTask(), UPDATE_CHECK_DELAY, UPDATE_CHECK_DELAY);
  }

  /**
   * This method makes the bot change arenas.
   *
   * @param argString is the new arena to go to.
   */

  public void gotGoCmd(String argString)
  {
    if(currentArena.equalsIgnoreCase(argString))
      throw new IllegalArgumentException("ERROR: " + botName + " is currently in that arena.");
    currentArena = argString;
    m_botAction.changeArena(currentArena);
    m_botAction.sendChatMessage("Going to " + currentArena);
  }

  /**
   * This method checks to see if loading a module is possible (ie the module
   * is present).  If it is, it sends an "loading" message to the pubhub bot.
   *
   * @param argString is the name of the module to load.
   */

  public void gotLoadCmd(String argString)
  {
    if(!moduleHandler.isModule(argString))
      throw new IllegalArgumentException("ERROR: Could not find " + argString + ".");
    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("loading " + argString, pubHubBot));
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
   * This method loads and initializes a module from the bot.  It is called when
   * the pubhub bot sends the loaded message via IPC.
   *
   * @param argString is the module to load.
   */

  public void gotLoadedCmd(String argString)
  {
    PubBotModule module;

    moduleHandler.loadModule(argString);
    module = (PubBotModule) moduleHandler.getModule(argString);
    module.initializeModule(IPCCHANNEL, pubHubBot);
    m_botAction.sendChatMessage(argString + " has been loaded in " + currentArena);
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
    if(message.equalsIgnoreCase("where"))
      gotWhereCmd();
    if(startsWithIgnoreCase(message, "go "))
      gotGoCmd(message.substring(3).trim());
    if(startsWithIgnoreCase(message, "load "))
      gotLoadCmd(message.substring(5).trim());
    if(startsWithIgnoreCase(message, "loaded "))
      gotLoadedCmd(message.substring(7).trim());
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
    IPCMessage ipcMessage = (IPCMessage) event.getObject();
    String message = ipcMessage.getMessage();
    String recipient = ipcMessage.getRecipient();
    String sender = ipcMessage.getSender();
    String botSender = event.getSenderName();

    try
    {
      if(recipient == null || recipient.equals(botName))
      {
        if(sender == null)
          handleBotIPC(botSender, recipient, sender, message);
        else
          handlePlayerIPC(botSender, sender, message);
      }
    }
    catch(Exception e)
    {
      m_botAction.sendChatMessage(e.getMessage());
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
    Player p = m_botAction.getPlayer(event.getPlayerID());
    String sender;
    if( p == null ) {
        sender = event.getMessager();
        if( sender == null )
            sender = "unknown";
    } else {
        sender = p.getPlayerName();
    }    
    
    int messageType = event.getMessageType();
    String message = event.getMessage();

    // No need to spam the chat if a mod is checking TKs
    if( ! opList.isZH( sender ) )
    {
      if(messageType == Message.PRIVATE_MESSAGE)
        m_botAction.sendChatMessage(sender + " said: \"" + message + "\" in " + currentArena + ".");
    }
    moduleHandler.handleEvent(event);
  }

  /**
   * This method handles an ArenaList event.
   *
   * @param event is the ArenaList event.
   */

  public void handleEvent(ArenaList event)
  {
    currentArena = event.getCurrentArenaName();
    gotArenaList = true;
    moduleHandler.handleEvent(event);
  }

  /**
   * This method requests the events that the bot will use.
   */

  private void requestEvents()
  {
    EventRequester eventRequester = m_botAction.getEventRequester();
    eventRequester.request(EventRequester.ARENA_LIST);
    eventRequester.request(EventRequester.PLAYER_LEFT);
    eventRequester.request(EventRequester.MESSAGE);
  }

  /**
   * This method updates the arena info.
   */

  private void updateArenaInfo()
  {
    gotArenaList = false;
    m_botAction.requestArenaList();
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
      stringChar = (char) string.charAt(index);
      startStringChar = (char) startString.charAt(index);
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
   * This class waits for the arena info to update.  When it has updated, it
   * sends the bots current location to the pubHubBot.
   */

  private class SendWhereTask extends TimerTask
  {
    public void run()
    {
      if(gotArenaList)
      {
        m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("here " + currentArena, pubHubBot));
        cancel();
      }
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

  /**
   * This class checks to see if an arena is dead after a player leaves it.
   * If it is it will log the bot off.
   */

  private class ArenaDyingTask extends TimerTask
  {
    public void run()
    {
      if(m_botAction.getArenaSize() == 1)
        doDieCmd(true);
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
