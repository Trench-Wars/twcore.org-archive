package twcore.bots.purepubbot;

import java.util.*;

import twcore.core.*;

public class purepubbot extends SubspaceBot
{

  public static final int SPEC = 0;
  public static final int FREQ_0 = 0;
  public static final int FREQ_1 = 1;
  public static final int LEVIATHAN = 4;

  private OperatorList opList;
  private HashSet freq0List;
  private HashSet freq1List;
  private boolean started;
  private boolean privFreqs;
  private boolean flagTimeStarted;
  private FlagCountTask flagTimer;
  private int flagMinutesRequired; 

  /**
   * This method initializes the bot.
   *
   * @param botAction is the BotAction method for the bot.
   */

  public purepubbot(BotAction botAction)
  {
    super(botAction);

    requestEvents();
    opList = m_botAction.getOperatorList();
    freq0List = new HashSet();
    freq1List = new HashSet();
    started = false;
    privFreqs = true;
  }

  /**
   * This method handles the FrequencyShipChange event.
   *
   * @param event is the event to process.
   */

  public void handleEvent(FrequencyShipChange event)
  {
    int playerID = event.getPlayerID();
    int freq = event.getFrequency();

    if(started)
    {
      checkPlayer(playerID, true);
      if(!privFreqs)
        checkFreq(playerID, freq, true);
    }
  }

  /**
   * This method handles a PlayerLeft event.
   *
   * @param event is the event to handle.
   */
  public void handleEvent(PlayerLeft event)
  {
    int playerID = event.getPlayerID();
    String playerName = m_botAction.getPlayerName(playerID);

    removeFromLists(playerName);
  }

  /**
   * This method handles the FrequencyChange event.
   *
   * @param event is the event to handle.
   */
  public void handleEvent(FrequencyChange event)
  {
    int playerID = event.getPlayerID();
    int freq = event.getFrequency();

    if(started && !privFreqs)
      checkFreq(playerID, freq, true);
  }

  /**
   * This method handles the PlayerEntered event.
   *
   * @param event is the event to process.
   */

  public void handleEvent(PlayerEntered event)
  {
    if(started)
    {
      int playerID = event.getPlayerID();
      Player player = m_botAction.getPlayer(playerID);
      String playerName = m_botAction.getPlayerName(playerID);

      m_botAction.sendSmartPrivateMessage(playerName, "This arena has pure pub settings enabled.  Leviathans (Ship 4) are no longer allowed in this arena.");
      checkPlayer(playerID, false);
      if(!privFreqs)
      {
        m_botAction.sendSmartPrivateMessage(playerName, "Private Frequencies are currently disabled.");
        checkFreq(playerID, player.getFrequency(), false);
      }
    }
  }

  /**
   * This method handles a PlayerLeft event.
   *
   * @param event is the event to handle.
   */
  public void handleEvent(FlagClaimed event)
  {
    if(!flagTimeStarted)
      return;
    
    int playerID = event.getPlayerID();
    Player p = m_botAction.getPlayer(playerID);
    
    try {
      if( p != null ) {
        flagTimer.flagClaimed( p.getFrequency(), playerID );
      }
    } catch (Exception e) {
    }
  }

  
  /**
   * This method moves a bot from one arena to another.  The bot must not be
   * started for it to move.
   *
   * @param sender is the person issuing the command.
   * @param argString is the new arena to go to.
   * @throws RuntimeException if the bot is currently running.
   * @throws IllegalArgumentException if the bot is already in that arena.
   */

  public void doGoCmd(String sender, String argString)
  {
    String currentArena = m_botAction.getArenaName();

    if(started || flagTimeStarted)
      throw new RuntimeException("Bot is currently running pure pub settings in " + currentArena + ".  Please !Stop and/or !Endtime before trying to move.");
    if(currentArena.equalsIgnoreCase(argString))
      throw new IllegalArgumentException("Bot is already in that arena.");

    m_botAction.changeArena(argString);
    m_botAction.sendSmartPrivateMessage(sender, "Bot going to: " + argString);
  }

  /**
   * This method starts the pure pub settings.
   *
   * @param sender is the person issuing the command.
   * @throws RuntimeException if the bot is already running pure pub settings.
   */

  public void doStartCmd(String sender)
  {
    if(started)
      throw new RuntimeException("Bot is already running pure pub settings.");

    started = true;
    specLevis();
    m_botAction.sendArenaMessage("Pure pub settings enabled.  Leviathans (Ship 4) are no longer allowed in this arena.", 2);
    m_botAction.sendSmartPrivateMessage(sender, "Pure pub succesfully enabled.");
  }

  /**
   * This method stops the pure pub settings.
   *
   * @param sender is the person issuing the command.
   * @throws RuntimeException if the bot is not currently running pure pub
   * settings.
   */

  public void doStopCmd(String sender)
  {
    if(!started)
      throw new RuntimeException("Bot is not currently running pure pub settings.");

    started = false;
    m_botAction.sendArenaMessage("Pure pub settings disabled.  Leviathans (Ship 4) are allowed in this arena.", 2);
    m_botAction.sendSmartPrivateMessage(sender, "Pure pub succesfully disabled.");
  }

  /**
   * This method toggles if private frequencies are allowed or not.
   *
   * @param sender is the sender of the command.
   */
  public void doPrivFreqsCmd(String sender)
  {
    if(!started)
      throw new RuntimeException("Bot is not currently running pure pub settings.");
    if(!privFreqs)
    {
      m_botAction.sendArenaMessage("Private Frequencies enabled.", 2);
      m_botAction.sendSmartPrivateMessage(sender, "Private frequencies succesfully enabled.");
    }
    else
    {
      fixFreqs();
      m_botAction.sendArenaMessage("Private Frequencies disabled.", 2);
      m_botAction.sendSmartPrivateMessage(sender, "Private frequencies succesfully disabled.");
    }
    privFreqs = !privFreqs;
  }

  /**
   * Starts a "flag time" mode in which a team must hold the flag for a certain consecutive
   * number of minutes in order to win the round.
   * 
   * @param sender is the person issuing the command.
   * @param argString is the number of minutes to hold the game to.
   */
  public void doStartTimeCmd(String sender, String argString )
  {
    if(flagTimeStarted)
      throw new RuntimeException( "Flag Time mode has already been started." );

    int min = 0;
    
    try {
      min = (Integer.valueOf( argString )).intValue();
    } catch (Exception e) {
      throw new RuntimeException( "Bad input.  Please supply a number." );        
    }
    
    if( min < 1 || min > 120 )
      throw new RuntimeException( "The number of minutes required must be between 1 and 120." );
    
    flagMinutesRequired = min;        
    
    m_botAction.sendSmartPrivateMessage(sender, "Flag Time mode enabled." );
    m_botAction.sendArenaMessage( "Flag Time mode has been enabled." );
      
    m_botAction.sendArenaMessage( "RULES: Hold the flag for " + flagMinutesRequired + " minute" + (flagMinutesRequired == 1 ? "" : "s") + " to win the round." );
    m_botAction.sendArenaMessage( "Next round will begin in 60 seconds." );
    
    flagTimeStarted = true;
    m_botAction.scheduleTask( new StartRoundTask(), 60000 );
  }
  
  /**
   * Ends "flag time" mode.
   * 
   * @param sender is the person issuing the command.
   */
  public void doStopTimeCmd(String sender )
  {
    if(!flagTimeStarted)
      throw new RuntimeException("Flag Time mode is not currently running.");
    
    m_botAction.sendSmartPrivateMessage(sender, "Flag Time mode disabled." );
    m_botAction.sendArenaMessage( "Flag Time mode has been disabled." );

    try {
      flagTimer.endGame();
      flagTimer.cancel();
    } catch (Exception e ) {
    }
    
    flagTimeStarted = false;
  }

  
  /**
   * Displays info about time remaining, if applicable.
   * 
   * @param sender is the person issuing the command.
   */
  public void doTimeCmd(String sender )
  {   
    if( flagTimer != null && flagTimeStarted )
      flagTimer.sendTimeRemaining( sender );
    else
      throw new RuntimeException( "Flag time mode is not currently running." );
  }
  
  /**
   * This method logs the bot off.
   *
   * @param sender is the person issuing the command.
   * @throws RuntimeException if the bot is running pure pub settings.
   */

  public void doDieCmd(String sender)
  {
    String currentArena = m_botAction.getArenaName();

    if(started)
      throw new RuntimeException("Bot is currently running pure pub settings in " + currentArena + ".  Please !Stop before trying to die.");

    m_botAction.sendSmartPrivateMessage(sender, "Bot logging off.");
    m_botAction.scheduleTask(new DieTask(), 100);
  }

  /**
   * This method displays a help message.
   *
   * @param sender is the person issuing the command.
   */

  public void doHelpCmd(String sender)
  {
    String[] helpMessage =
    {
      "!Go <ArenaName>                  -- Moves the bot to <ArenaName>.",
      "!Start                           -- Starts pure pub settings.",
      "!Stop                            -- Stops pure pub settings.",
      "!Privfreqs                       -- Toggles Private Frequencies.",
      "!StartTime #                     -- Starts Flag Time mode (a team wins",
      "                                    with # consecutive min of flagtime).",
      "!StopTime                        -- Ends Flag Time mode.",
      "!Time                            -- Provides time remaining in Flag Time mode.",
      "!Die                             -- Logs the bot off of the server.",
      "!Help                            -- Displays this help message."
    };
    
    String[] playerHelpMessage =
    {
      "Hello!  I am a bot designed to enforce 'pure pub' rules.",
      "When enabled, I may restrict levis from playing, prevent private frequencies, or run Flag Time mode.",
      "Flag Time mode commands (a freq must hold flag for an amount of consecutive minutes to win):",
      "!Help                            -- Displays this help message.",
      "!Time                            -- Provides time remaining in Flag Time mode.",
    };

    if( opList.isHighmod( sender ) )
      m_botAction.smartPrivateMessageSpam(sender, helpMessage);
    else
      m_botAction.smartPrivateMessageSpam(sender, playerHelpMessage);
  }

  /**
   * This method handles a command sent to the bot.
   *
   * @param sender is the person issuing the command.
   * @param message is the command that is being sent.
   */

  public void handleCommand(String sender, String message)
  {
    String command = message.toLowerCase();

    try
    {
      if(message.equals("!time"))
        doTimeCmd(sender);
      else if(command.equals("!help"))
        doHelpCmd(sender);
      
      if ( !opList.isHighmod(sender) )
        return;
      
      if(command.startsWith("!go "))
        doGoCmd(sender, message.substring(4));
      else if(command.equals("!start"))
        doStartCmd(sender);
      else if(command.equals("!stop"))
        doStopCmd(sender);
      else if(command.equals("!privfreqs"))
        doPrivFreqsCmd(sender);
      else if(command.startsWith("!starttime "))
        doStartTimeCmd(sender, message.substring(11));
      else if(command.equals("!stoptime"))
        doStopTimeCmd(sender);
      else if(command.equals("!die"))
        doDieCmd(sender);
    }
    catch(RuntimeException e)
    {
      m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
    }
  }

  /**
   * This method handles a Message event.
   *
   * @param event is the message event to handle.
   */

  public void handleEvent(Message event)
  {
    String sender = getSender(event);
    int messageType = event.getMessageType();
    String message = event.getMessage().trim();

    if((messageType == Message.PRIVATE_MESSAGE || messageType == Message.REMOTE_PRIVATE_MESSAGE) )    
        handleCommand(sender, message);
  }

  /**
   * This method handles a LoggedOn event.
   *
   * @param event is the message event to handle.
   */

  public void handleEvent(LoggedOn event)
  {
    BotSettings botSettings = m_botAction.getBotSettings();
    String initialArena = botSettings.getString("InitialArena");

    m_botAction.changeArena(initialArena);
  }

  /**
   * This method requests all of the appropriate events.
   */

  private void requestEvents()
  {
    EventRequester eventRequester = m_botAction.getEventRequester();

    eventRequester.request(EventRequester.MESSAGE);
    eventRequester.request(EventRequester.PLAYER_LEFT);
    eventRequester.request(EventRequester.PLAYER_ENTERED);
    eventRequester.request(EventRequester.FLAG_CLAIMED);
    eventRequester.request(EventRequester.FREQUENCY_CHANGE);
    eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
  }

  /**
   * This method returns the name of the player that sent the message regardless
   * of whether or not the message is a remote private message or a private
   * message.
   *
   * @param event is the message event.
   * @return the name of the sender is returned.  If the name of the sender
   * cannot be determined then null is returned.
   */

  private String getSender(Message event)
  {
    if(event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE)
      return event.getMessager();

    int senderID = event.getPlayerID();
    return m_botAction.getPlayerName(senderID);
  }

  /**
   * This method checks to see if a player is a leviathan.  If they are then
   * they are specced.
   *
   * @param playerName is the player to be checked.
   * @param specMessage enables the spec message.
   */

  private void checkPlayer(int playerID, boolean specMessage)
  {
    Player player = m_botAction.getPlayer(playerID);

    if(player != null && player.getShipType() == LEVIATHAN)
    {
      m_botAction.spec(playerID);
      m_botAction.spec(playerID);
      if(specMessage)
        m_botAction.sendSmartPrivateMessage(m_botAction.getPlayerName(playerID), "Leviathans are not allowed in this pub.  Please change pubs if you wish to be a levi.");
    }
  }

  /**
   * This moethod removes a playerName from the freq lists.
   */
  private void removeFromLists(String playerName)
  {
    String lowerName = playerName.toLowerCase();

    freq0List.remove(lowerName);
    freq1List.remove(lowerName);
  }

  /**
   * This method sets a player to a freq and updates the freq lists.
   *
   * @param playerName is the name of the player to add.
   * @param freq is the new freq.
   */
  private void addToLists(String playerName, int freq)
  {
    String lowerName = playerName.toLowerCase();

    if(freq == FREQ_0)
      freq0List.add(lowerName);
    if(freq == FREQ_1)
      freq1List.add(lowerName);
  }

  /**
   * This method checks to see if a player is on a private freq.  If they are
   * then they are changed to the pub freq with the fewest number of players.
   *
   * @param Player player is the player to check.
   * @param changeMessage is true if a changeMessage will be displayed.
   */
  private void checkFreq(int playerID, int freq, boolean changeMessage)
  {
    Player player = m_botAction.getPlayer(playerID);
    String playerName = player.getPlayerName();
    int ship = player.getShipType();
    int newFreq = freq;

    removeFromLists(playerName);

    if(ship != SPEC)
    {
      if(player != null && freq != FREQ_0 && freq != FREQ_1)
      {
        if(freq0List.size() <= freq1List.size())
          newFreq = FREQ_0;
        else
          newFreq = FREQ_1;
        if(changeMessage)
          m_botAction.sendSmartPrivateMessage(playerName, "Private Frequencies are currently disabled.  You have been placed on a public Frequency.");
        m_botAction.setFreq(playerName, newFreq);
      }
      addToLists(playerName, newFreq);
    }
  }

  /**
   * This method specs all of the levis in the arena.
   */

  private void specLevis()
  {
    Iterator iterator = m_botAction.getPlayingPlayerIterator();
    Player player;

    while(iterator.hasNext())
    {
      player = (Player) iterator.next();
      checkPlayer(player.getPlayerID(), false);
    }
  }

  /**
   * This method fills the freq lists for freqs 1 and 0.
   */
  private void fillFreqLists()
  {
    Iterator iterator = m_botAction.getPlayingPlayerIterator();
    Player player;
    String lowerName;

    freq0List.clear();
    freq1List.clear();
    while(iterator.hasNext())
    {
      player = (Player) iterator.next();
      lowerName = player.getPlayerName().toLowerCase();
      if(player.getFrequency() == FREQ_0)
        freq0List.add(lowerName);
      if(player.getFrequency() == FREQ_1)
        freq1List.add(lowerName);
    }
  }

  /**
   * This method fixes the freq of each player.
   */
  private void fixFreqs()
  {
    Iterator iterator = m_botAction.getPlayingPlayerIterator();
    Player player;

    fillFreqLists();
    while(iterator.hasNext())
    {
      player = (Player) iterator.next();
      checkFreq(player.getPlayerID(), player.getFrequency(), false);
    }
  }
  
  /**
   * Starts a game of flag time mode.
   */
  private void doStartRound() {
    if(!flagTimeStarted)
      return;
    
    flagTimer = new FlagCountTask();
    m_botAction.scheduleTaskAtFixedRate( flagTimer, 100, 1000);
  }

  /**
   * Displays rules and pauses for intermission.
   */
  private void doIntermission() {
    if(!flagTimeStarted)
      return;
    
    m_botAction.sendArenaMessage( "RULES: Hold the flag for " + flagMinutesRequired + " minute" + (flagMinutesRequired == 1 ? "" : "s") + " to win the round." );
    m_botAction.sendArenaMessage( "Next round will begin in 3 minutes." );
    m_botAction.scheduleTask( new StartRoundTask(), 3 * 60 * 1000 );
  }
  
  /**
   * Ends a round of Flag Time mode, and sets up an intermission, followed by a new round.
   */
  private void doEndRound( int winningFreq ) {
    if(!flagTimeStarted)
      return;

    try {
      flagTimer.endGame();
      flagTimer.cancel();
    } catch (Exception e ) {
    }
    
    m_botAction.sendArenaMessage( "END ROUND:  Freq " + winningFreq + " has emerged victorious.", 1 );
    m_botAction.scheduleTask( new IntermissionTask(), 10000 );
  }
  
  /**
   * Formats an integer time as a String. 
   * @param time Time in seconds.
   * @return Formatted string in 0:00 format.
   */
  public String getTimeString( int time ) {
    if( time <= 0 ) {
      return "0:00";            
  	}else {
      int minutes = time / 60;
      int seconds = time % 60;
      return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }
  }
  


  

  /**
   * This private class logs the bot off.  It is used to give a slight delay
   * to the log off process.
   */
  private class DieTask extends TimerTask
  {

    /**
     * This method logs the bot off.
     */

    public void run()
    {
      m_botAction.die();
    }
  }

  /**
   * This private class starts the round.
   */
  private class StartRoundTask extends TimerTask {
    public void run() {
      doStartRound();
    }
  }

  /**
   * This private class provides a pause before starting the round.
   */
  private class IntermissionTask extends TimerTask {
    public void run() {
      doIntermission();
    }
  }

  /**
   * This private class counts the consecutive flag time an individual team racks up.
   * Upon reaching the time needed to win, it fires the end of the round.
   */
  private class FlagCountTask extends TimerTask {
    int flagholdingFreq;
    int secondsHeld, totalSecs;
    int preTimeCount;
    boolean isStarted, isRunning;
    
    
    public FlagCountTask() {
      flagholdingFreq = -1;
      secondsHeld = 0;
      totalSecs = 0;
      isStarted = false;
      isRunning = false;
    }
      
    public void flagClaimed( int freq, int pid ) {
      if( isRunning == false )
        return;
        
      if( freq != flagholdingFreq && freq != -1 ) {
        flagholdingFreq = freq;
        
        int remain = (flagMinutesRequired * 60) - secondsHeld; 
        
        if( remain <= 20 ) {
          Player p = m_botAction.getPlayer( pid );

          if( p != null ) {
            if( remain < 10 )
              m_botAction.sendArenaMessage( "AMAZING!: " + p.getPlayerName() + " claims the flag for Freq " + freq + " with just " + remain + " second" + (remain == 1 ? "" : "s") + " left!" );
            else
              m_botAction.sendArenaMessage( "SAVE!: " + p.getPlayerName() + " claims the flag for Freq " + freq + " with " + remain + " seconds left!" );
          }
        }

        m_botAction.setTimer( flagMinutesRequired );
        secondsHeld = 0;              
      }     
    }
    
    public void endGame() {
      isRunning = false;
    }
    
    /**
     * Give out prizes to the winning freq.  If it was a hard-fought victory, give more.
     * Then prize them a shipreset and warp them out so that another freq can have a shot.
     */
    public void givePrizes() {
        int weight = (totalSecs / 60);
        if( weight >= 20 && weight < 40 )
          weight += 10;
        else if( weight >= 40 && weight < 50 )
          weight += 20;
        else if( weight >= 50 && weight < 100 )
          weight += 30;
        else if( weight >= 100 )
          weight += 50;
        
        try
        {
          Iterator iterator = m_botAction.getPlayerIterator();
          Player player;
          while(iterator.hasNext())
          {
            player = (Player) iterator.next();
            if( player != null ) {
              if(player.getFrequency() == flagholdingFreq ) {
                for( int i = 0; i < weight; i++ )               
                  m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #13");
              }
            }
          }
          
          iterator = m_botAction.getPlayerIterator();
          while(iterator.hasNext()) {
            player = (Player) iterator.next();
            if( player != null ) {
              if(player.getFrequency() == flagholdingFreq ) {
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #7");
                m_botAction.shipReset( player.getPlayerID() );
              }
            }
          }
        }
        catch(Exception e)
        {
        }
    }
    
    public void sendTimeRemaining( String name ) {       
      if( isRunning == false ) {
        m_botAction.sendSmartPrivateMessage( name, "We are currently between games." );
        return;
      }
      
      m_botAction.sendSmartPrivateMessage( name, (flagholdingFreq == -1 ? "Nobody" : "Freq " + flagholdingFreq ) + " has held for " + getTimeString(secondsHeld) + " and needs " + getTimeString( (flagMinutesRequired * 60) - secondsHeld ) + " more to win.  [Total time: " + getTimeString( totalSecs ) + "]" );      
    }
    
    public void run()
    {        
      if( isStarted == false ) {
        if( preTimeCount == 0 )
          m_botAction.sendArenaMessage( "Round begins in 10 seconds . . ." );
        preTimeCount++;

        if( preTimeCount >= 10 ) {
          isStarted = true;
          isRunning = true;
          m_botAction.sendArenaMessage( "ROUND START!" );
          m_botAction.sendArenaMessage( "Hold the flag for " + flagMinutesRequired + " consecutive minute" + (flagMinutesRequired == 1 ? "" : "s") + " to win.", 103 );
          m_botAction.resetFlagGame();
          return;
        }
      }
        
      if( isRunning == false )
          return;
      
      if( flagholdingFreq == -1 )
        return;
      
      secondsHeld++;
      totalSecs++;
      
      if( secondsHeld >= flagMinutesRequired * 60 ) {
        endGame();
        givePrizes();
        doEndRound(flagholdingFreq);
      } else if( (flagMinutesRequired * 60) - secondsHeld == 60 ) {
        m_botAction.sendArenaMessage( "Freq " + flagholdingFreq + " will win in 60 seconds." );
      } else if( (flagMinutesRequired * 60) - secondsHeld == 10 ) {
        m_botAction.sendArenaMessage( "Freq " + flagholdingFreq + " will win in 10 seconds . . ." );
      }
    }
  }  

}
