package twcore.bots.pubsystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.pubsystem.PubTileset.Tileset;
import twcore.bots.pubsystem.game.AbstractGame;
import twcore.bots.pubsystem.game.GameContext;
import twcore.bots.pubsystem.game.GameFlagTime;
import twcore.bots.pubsystem.game.NoGame;
import twcore.bots.pubsystem.game.GameContext.Mode;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.ArenaList;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.KotHReset;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.SQLResultEvent;
import twcore.core.events.WeaponFired;
import twcore.core.game.Player;
import twcore.core.lvz.Objset;
import twcore.core.util.Point;
import twcore.core.util.PointLocation;
import twcore.core.util.Tools;
import twcore.core.util.Tools.Weapon;

/**
 * "Pure" pub bot that can enforce ship restrictions, freq restrictions, and run
 * a timed pub game using a flag. (Note that for non-TW zones, the warp points for
 * flag time games must be set up before use.)
 *
 * Restrictions for any ship can be easily enforced using this bot.  Each restriction
 * should be marked in this format in the CFG: (BotName)Ship(#)=(Value), e.g., if
 * the bot's name is MyPurePub, to completely restrict ship 1, one would use
 * MyPurePubShip1=0, and to allow ship 3, one would use MyPurePubShip3=1.  All
 * playable ships 1-8 must be defined for each bot.  Ship 0 is autodefined as 1.
 *
 *   Values:
 *   0  - No ships of this type allowed
 *   1  - Unlimited number of ships of this type are allowed
 *   #  - If the number of current ships of the type on this frequency is
 *        greater than the total number of people on the frequency divided
 *        by this number (ships of this type > total ships / weight), then the
 *        ship is not allowed.  The exception to this rule is if the player is
 *        the only one on the freq currently in the ship.
 *
 * For example, to say that only half the ships on a freq are allowed to be javs:
 * MyPurePub2=2, and for only a fifth of the ships allowed to be terrs, MyPurePub=5.
 * See JavaDocs of the checkPlayer(int) method for more information.
 *
 *
 * (NOTE: purepubbot is different than the pub bot module and hub system.  Pubhub /
 * Pubbot answers queries about player aliases, spies for certain words, monitors
 * TKs, informs people of messages received, and can perform any other task necessary
 * in a pub -- particularly ones that require a way to verify when a person logs on.)
 *
 * @author qan / original idea and bot by Cpt. Guano!
 * @see pubbot; pubhub
 */
public class pubsystem extends SubspaceBot
{	
	private GameContext context;								// Context of the game
	
	private PubPlayerManager playerManager;				// Player manager
    private PubMoneySystem moneySystem;					// Money system
    private PubTileset pubTileset;						// Change the current tileset
    private PubStreak pubStreak;						// Streak system
    private PubWarp pubWarp;							// Warp system

    private BotSettings m_botSettings;

    public static final int SPEC = 0;                   // Number of the spec ship
    public static final int FREQ_0 = 0;                 // Frequency 0
    public static final int FREQ_1 = 1;                 // Frequency 1
 
    private OperatorList opList;                        // Admin rights info obj
    private HashMap <String,Integer>playerTimes;        // Roundtime of player on freq

    private ToggleTask toggleTask;                      // Toggles commands on and off at a specified interval

    private boolean roamPub = true;						// True if bots auto-roam to public
    private boolean initLogin = true;                   // True if first arena login
    private int initialPub;                             // Order of pub arena to defaultjoin
    private String initialSpawn;                        // Arena initially spawned in

    private AbstractGame game;							// Current game

    /**
     * Creates a new instance of pubsystem bot and initializes necessary data.
     *
     * @param Reference to bot utility class
     */
    public pubsystem(BotAction botAction)
    {
        super(botAction);
        requestEvents();
        m_botSettings = m_botAction.getBotSettings();
        opList = m_botAction.getOperatorList();
        
        context = new GameContext(m_botAction);
        
        playerManager = context.getPlayerManager();
        moneySystem = context.getMoneySystem();
        pubTileset = context.getPutTileset();
        pubStreak = context.getPubStreak();
        pubWarp = context.getPubWarp();
        
        playerTimes = new HashMap<String,Integer>();
        context.setPrivFreqEnabled(true);
        
        game = new NoGame();
    }

    public boolean isIdle() {
        return game.isIdle();
    }

    /**
     * Requests all of the appropriate events.
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
        eventRequester.request(EventRequester.LOGGED_ON);
        eventRequester.request(EventRequester.ARENA_LIST);
        eventRequester.request(EventRequester.ARENA_JOINED);
        eventRequester.request(EventRequester.KOTH_RESET);
        eventRequester.request(EventRequester.PLAYER_DEATH);
    }


    /* **********************************  EVENTS  ************************************ */

    /**
     * Retreives all necessary settings for the bot to operate.
     *
     * @param event is the event to process.
     */
    public void handleEvent(LoggedOn event)
    {
        BotSettings botSettings = m_botAction.getBotSettings();
 
        initialSpawn = botSettings.getString("InitialArena");
        initialPub = (botSettings.getInt(m_botAction.getBotName() + "Pub") - 1);
        
        String arena = initialSpawn;
        int botNumber = botSettings.getInt(m_botAction.getBotName() + "Pub");
        
        if (botSettings.getString("Arena"+botNumber) != null) {
        	roamPub = false;
        	arena = botSettings.getString("Arena"+botNumber);
        }
        
        try {
			m_botAction.joinArena(arena,(short)3392,(short)3392); // Max resolution
		} catch (Exception e) {
			m_botAction.joinArena(arena);
		}

        m_botAction.setPlayerPositionUpdating(500);
        m_botAction.receiveAllPlayerDeaths();

    }


    /**
     * Requests arena list to move to appropriate pub automatically, if the arena
     * is the first arena joined.
     *
     * @param event is the event to process.
     */
    public void handleEvent(ArenaJoined event)
    {
    	if(!initLogin && roamPub)
    		return;

    	if (roamPub) {
    		m_botAction.requestArenaList();
    		initLogin = false;
    	} else {
    		startBot();
    	}

    	m_botAction.setReliableKills(1); 
    	moneySystem.handleEvent(event);
    }


    /**
     * Sends bot to public arena specified in CFG.
     *
     * @param event is the event to process.
     */
    public void handleEvent(ArenaList event)
    {
    	if (!roamPub)
    		return;

    	String[] arenaNames = event.getArenaNames();

        Comparator <String>a = new Comparator<String>()
        {
            public int compare(String a, String b)
            {
                if (Tools.isAllDigits(a) && !a.equals("") ) {
                    if (Tools.isAllDigits(b) && !b.equals("") ) {
                        if (Integer.parseInt(a) < Integer.parseInt(b)) {
                            return -1;
                        } else {
                            return 1;
                        }
                    } else {
                        return -1;
                    }
                } else if (Tools.isAllDigits(b)) {
                    return 1;
                } else {
                    return a.compareToIgnoreCase(b);
				}
            };
        };

        Arrays.sort(arenaNames, a);
        
    	String arenaToJoin = arenaNames[initialPub];// initialPub+1 if you spawn it in # arena
    	if(Tools.isAllDigits(arenaToJoin))
    	{
    		m_botAction.changeArena(arenaToJoin);
    		startBot();
    	}
    }


    /**
     * Handles the FrequencyShipChange event.
     * Checks players for appropriate ships/freqs.
     * Resets their MVP timer if they spec or change ships (new rule).
     *
     * @param event is the event to process.
     */
    public void handleEvent(FrequencyShipChange event)
    {
    	moneySystem.handleEvent(event);
    	playerManager.handleEvent(event);
    	
    	game.handleEvent(event);
    }


    /**
     * Checks if freq is valid (if private frequencies are disabled), and prevents
     * freq-hoppers from switching freqs for end round prizes.
     *
     * @param event is the event to handle.
     */
    public void handleEvent(FrequencyChange event)
    {
    	playerManager.handleEvent(event);
    	game.handleEvent(event);
    }


    /**
     * When a player enters, displays necessary information, and checks
     * their ship & freq.
     *
     * @param event is the event to process.
     */
    public void handleEvent(PlayerEntered event)
    {
        try {
        	
            int playerID = event.getPlayerID();
            Player player = m_botAction.getPlayer(playerID);
            String playerName = m_botAction.getPlayerName(playerID);

            if(context.isStarted()) {
            	
            	String message = 
            		//"Welcome to Pub.  " +
            		"Private freqs:[" + (context.isPrivFreqEnabled() ? "YES" : "NO") + "]  " + 
            		"Streak:[" + (pubStreak.isEnabled() ? "ON" : "OFF") + "]  " +
            		"Store:[" + (moneySystem.isStoreOpened() ? "ON" : "OFF") + "]";
            	
            	
                m_botAction.sendPrivateMessage(playerName, message );
  
                String cmds = "!terr !team !clearmines";
                if( pubWarp.isWarpEnabled() )
                    cmds += " !warp";
                m_botAction.sendPrivateMessage(playerName, "Commands:  " + cmds + "  (!help for more)");
 
                
                if (game.isStarted()) {
                	m_botAction.sendPrivateMessage(playerName, "Current game: " + game.getName());
                } else if (game instanceof NoGame) {
                	m_botAction.sendPrivateMessage(playerName, "There is no game running.");
                } else {
                	m_botAction.sendPrivateMessage(playerName, "Current game: " + game.getName());
                }
                
                
            }
            
            moneySystem.handleEvent(event);
            playerManager.handleEvent(event);
            pubWarp.handleEvent(event);
            
            // If a game is running, announce the status of the game
            game.statusMessage(playerName);

        } catch (Exception e) {
        	Tools.printStackTrace(e);
        }

    }


    /**
     * Removes a player from all tracking lists when they leave the arena.
     *
     * @param event is the event to handle.
     */
    public void handleEvent(PlayerLeft event)
    {
        int playerID = event.getPlayerID();
        String playerName = m_botAction.getPlayerName(playerID);

        playerTimes.remove( playerName );
        
        playerManager.handleEvent(event);
        pubStreak.handleEvent(event);
        pubWarp.handleEvent(event);
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
     * If flag time mode is running, register with the flag time game that the
     * flag has been claimed.
     *
     * @param event is the event to handle.
     */
    public void handleEvent(FlagClaimed event) {
        game.handleEvent(event);
    }

    /**
     * Handles deaths in player challenges.
     *
     * @param event is the event to handle.
     */
    public void handleEvent(PlayerDeath event) {
    	
        moneySystem.handleEvent(event);
        pubStreak.handleEvent(event);
    }

    /**
     * Handles all messages received.
     *
     * @param event is the message event to handle.
     */
    public void handleEvent(Message event) {
    	
    	moneySystem.handleEvent(event);
    	
        String sender = getSender(event);
        int messageType = event.getMessageType();
        String message = event.getMessage().trim();

        if( message == null || sender == null )
            return;

        message = message.toLowerCase();
        if((messageType == Message.PRIVATE_MESSAGE || messageType == Message.PUBLIC_MESSAGE ) )
            handlePublicCommand(sender, message);
        if ( opList.isHighmod(sender) || sender.equals(m_botAction.getBotName()) )
            if((messageType == Message.PRIVATE_MESSAGE || messageType == Message.REMOTE_PRIVATE_MESSAGE) )
                handleModCommand(sender, message);
    }



    /* **********************************  COMMANDS  ************************************ */

    /**
     * Handles public commands sent to the bot, either in PM or pub chat.
     *
     * @param sender is the person issuing the command.
     * @param command is the command that is being sent.
     */
    public void handlePublicCommand(String sender, String command) {
        try {
            if(command.equals("!help") || command.equals("!h"))
                doHelpCmd(sender, false);
            else if(command.equals("!more"))
                doHelpCmd(sender, true);
            else if(command.startsWith("!whereis "))
                doWhereIsCmd(sender, command.substring(9), opList.isBot(sender));
            else if(command.startsWith("!settile ") || command.startsWith("!tileset "))
            	doSetTileCmd(sender, command.substring(9));
            else if(command.startsWith("!warp") || command.trim().equals("!w"))
                pubWarp.doWarpCmd(sender);
            else if(command.equals("!restrictions"))
                playerManager.doRestrictionsCmd(sender);

            else {
            	game.handleCommand(sender, command);
            }
            
        } catch(RuntimeException e) {
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
    }


    /**
     * Handles mod-only commands sent to the bot.
     *
     * @param sender is the person issuing the command.
     * @param command is the command that is being sent.
     */
    public void handleModCommand(String sender, String command) {
        try {
            if(command.startsWith("!go "))
                doGoCmd(sender, command.substring(4));
            else if(command.equals("!privfreqs"))
                doPrivFreqsCmd(sender);
            else if(command.startsWith("!starttime "))
                doStartTimeCmd(sender, command.substring(11));
            else if(command.equals("!stricttime"))
                doStrictTimeCmd(sender);
            else if(command.equals("!stoptime"))
                doStopTimeCmd(sender);
            else if(command.startsWith("!set "))
                playerManager.doSetCmd(sender, command.substring(5));
            else if(command.equals("!autowarp"))
                pubWarp.doAutowarpCmd(sender);
            else if(command.equals("!allowwarp"))
            	pubWarp.doAllowWarpCmd(sender);
            else if(command.equals("!die"))
                doDieCmd(sender);
            
        } catch(RuntimeException e) {
            m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
    }

    public void handleDisconnect() {
    	moneySystem.handleDisconnect();
    }
    
    public void handleEvent(SQLResultEvent event){
        moneySystem.handleEvent(event);
    }

    /**
     * Moves the bot from one arena to another.  The bot must not be
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

        if(context.isStarted() || context.isFlagTimeStarted())
            throw new RuntimeException("Bot is currently running pub settings in " + currentArena + ".  Please !Stop and/or !Endtime before trying to move.");
        if(currentArena.equalsIgnoreCase(argString))
            throw new IllegalArgumentException("Bot is already in that arena.");

        m_botAction.changeArena(argString);
        m_botAction.sendSmartPrivateMessage(sender, "Bot going to: " + argString);
    }

    /**
     * Toggles if private frequencies are allowed or not.
     *
     * @param sender is the sender of the command.
     */
    public void doPrivFreqsCmd(String sender)
    {
        if(!context.isPrivFreqEnabled())
        {
            m_botAction.sendArenaMessage("Private Frequencies enabled.", 2);
            m_botAction.sendSmartPrivateMessage(sender, "Private frequencies succesfully enabled.");
        }
        else
        {
            playerManager.fixFreqs();
            m_botAction.sendArenaMessage("Private Frequencies disabled.", 2);
            m_botAction.sendSmartPrivateMessage(sender, "Private frequencies succesfully disabled.");
        }
        context.setPrivFreqEnabled(!context.isPrivFreqEnabled());
    }


    /**
     * Starts a "flag time" mode in which a team must hold the flag for a certain
     * consecutive number of minutes in order to win the round.
     *
     * @param sender is the person issuing the command.
     * @param argString is the number of minutes to hold the game to.
     */
    public void doStartTimeCmd(String sender, String argString )
    {
    	game = new GameFlagTime(m_botAction, context);
    	game.start(argString);
    }


    /**
     * Toggles "strict" flag time mode in which all players are first warped
     * automatically into safe (must be set), and then warped into base.
     *
     * @param sender is the person issuing the command.
     */
    public void doStrictTimeCmd(String sender ) {
        if( context.isMode(Mode.STRICT_FLAG_TIME) ) {
            context.setMode(Mode.FLAG_TIME);
            if( context.isFlagTimeStarted() )
                m_botAction.sendSmartPrivateMessage(sender, "Strict flag time mode disabled.  Changes will go into effect next round.");
            else
                m_botAction.sendSmartPrivateMessage(sender, "Strict flag time mode disabled.  !startflagtime <minutes> to begin a normal flag time game.");
        } else {
            context.setMode(Mode.STRICT_FLAG_TIME);
            if( context.isFlagTimeStarted()) {
                m_botAction.sendSmartPrivateMessage(sender, "Strict flag time mode enabled.  All players will be warped into base next round.");
            } else {
                m_botAction.sendSmartPrivateMessage(sender, "Strict flag time mode enabled.  !startflagtime <minutes> to begin a strict flag time game.");
            }
        }
    }


    /**
     * Ends "flag time" mode.
     *
     * @param sender is the person issuing the command.
     */
    public void doStopTimeCmd(String sender )
    {
        game.stop();
    }

    /**
     * Logs the bot off if not enabled.
     *
     * @param sender is the person issuing the command.
     * @throws RuntimeException if the bot is running pure pub settings.
     */
    public void doDieCmd(String sender)
    {
        m_botAction.sendSmartPrivateMessage(sender, "Bot logging off.");
        m_botAction.setObjects();
        m_botAction.scheduleTask(new DieTask(), 300);
    }


    /**
     * Shows last seen location of a given individual.
     */
    public void doWhereIsCmd( String sender, String argString, boolean isStaff ) {
        Player p = m_botAction.getPlayer(sender);
        if( p == null )
            throw new RuntimeException("Can't find you.  Please report this to staff.");
        if( p.getShipType() == 0 && !isStaff )
            throw new RuntimeException("You must be in a ship for this command to work.");
        Player p2;
        p2 = m_botAction.getPlayer( argString );
        if( p2 == null )
            p2 = m_botAction.getFuzzyPlayer( argString );
        if( p2 == null )
            throw new RuntimeException("I can't find the player '" + argString + "'.  Tough shit, bucko.");
        if( p.getFrequency() != p2.getFrequency() && !isStaff )
            throw new RuntimeException(p2.getPlayerName() + " is not on your team!");
        m_botAction.sendPrivateMessage( sender, p2.getPlayerName() + " last seen: " + PubLocation.getPlayerLocation( p2, isStaff ));
    }
    
    /**
     * Change the current tileset for a player
     */
    public void doSetTileCmd( String sender, String tileName ) {

    	try {
    		Tileset tileset = Tileset.valueOf(tileName.toUpperCase());
    		pubTileset.setTileset(tileset, sender);
    	} catch (IllegalArgumentException e) {
    		m_botAction.sendPrivateMessage(sender, "The tileset '" + tileName + "' does not exists.");
    	}

    }


    /**
     * Displays a help message depending on access level.
     *
     * @param sender is the person issuing the command.
     */
    public void doHelpCmd(String sender, boolean advanced)
    {
        String[] modHelpMessage =
        {
                "Hi. I'm your space traffic controller for this arena. I restrict ships, manage private frequencies, and much more.",
                "Perhaps you want to run a command?",
                "=============================================================",
                "!warp    -- Warps you into flagroom at start of next round. (abbv: !w)",
                "!terr    -- Shows terriers on the team and their last seen locations. (abbv: !t)",
                "!team    -- Tells you which ships your team members are in.",
                "!whereis <name>   -- Shows last seen location of <name> (if on your team).",
                "!clearmines       -- Clears all mines you have laid, keeping MVP status. (abbv: !cl)",
                "!restrictions     -- Lists all current ship restrictions.",
                "!settile <name>   -- Change the current tileset (bluetech, boki, monolith).",
                "",
                "[STORE]",
                "!buy              -- Shows buyable items from the store. (abbv: !items)",
                "!buy <item_name>  -- Item to buy on the store. (abbv: !b) ",
                "(!more for more commands)",  
                "",
                "[STAFF]",
                "!go <ArenaName>   -- Moves the bot to <ArenaName>.",
                "!privfreqs        -- Toggles private frequencies & check for imbalances.",
                "!starttime <#>    -- Starts Flag Time game to <#> minutes",
                "!stoptime         -- Ends Flag Time mode.",
                "!stricttime       -- Toggles strict mode (all players warped)",
                "!autowarp         -- Enables and disables 'opt out' warping style",
                "!restrictions     -- Lists all current ship restrictions.",
                "!set <ship> <#>   -- Sets <ship> to restriction <#>.",
                "                     0=disabled; 1=any amount; other=weighted:",
                "                     2 = 1/2 of freq can be this ship, 5 = 1/5, ...",
                "!restrictions     -- Lists all current ship restrictions.",
                "!die              -- Logs the bot off of the server.",
        };

        String[] playerHelpMessage =
        {
                "Hi. I'm your space traffic controller for this arena. I restrict ships, manage private frequencies, and much more.",
                "Perhaps you want to run a command?",
                "=============================================================",
                "!warp   -- Warps you into flagroom at start of next round. (abbv: !w)",
                "!terr   -- Shows terriers on the team and their last seen locations. (abbv: !t)",
                "!team   -- Tells you which ships your team members are in.",
                "!buy    -- Shows buyable items from the store. (abbv: !items)",
                "!buy <item>  -- Item to buy on the store. (abbv: !b) ",
                "(!more for more commands)",          
        };
        
        String[] advancedPlayerHelpMessage =
        {
                "!whereis <name>   -- Shows last seen location of <name> (if on your team).",
                "!clearmines       -- Clears all mines you have laid, keeping MVP status. (abbv: !cl)",
                "!restrictions     -- Lists all current ship restrictions.",
                "!settile <name>   -- Change the current tileset (bluetech, boki, monolith).",
        };

        if( opList.isHighmod( sender ) )
            m_botAction.smartPrivateMessageSpam(sender, modHelpMessage);
        else if (!advanced)
            m_botAction.smartPrivateMessageSpam(sender, playerHelpMessage);
        else
        	m_botAction.smartPrivateMessageSpam(sender, advancedPlayerHelpMessage);
    }


    /* **********************************  SUPPORT METHODS  ************************************ */

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
     * Starts the bot with CFG-specified setup commands.
     */
	public void startBot()
    {
        try{
        	
            String commands[] = m_botSettings.getString(m_botAction.getBotName() + "Setup").split(",");
        	for(int k = 0; k < commands.length; k++) {
        		handleModCommand(m_botAction.getBotName(), commands[k]);
    		}
            String toggleInfoString = m_botAction.getBotSettings().getString(m_botAction.getBotName() + "Toggle");
            if( toggleInfoString != null && !toggleInfoString.trim().equals("") ) {
                String toggleSplit[] = toggleInfoString.split(":");
                if( toggleSplit.length == 2 ) {
                    try {
                        Integer toggleTime = Integer.parseInt(toggleSplit[1]);
                        String toggles[] = toggleSplit[0].split(";");
                        if( toggles.length == 2 ) {
                            toggleTask = new ToggleTask( toggles[0].split(","),toggles[1].split(",") );
                            m_botAction.scheduleTaskAtFixedRate(toggleTask, toggleTime * Tools.TimeInMillis.MINUTE, toggleTime * Tools.TimeInMillis.MINUTE );
                        } else {
                            Tools.printLog("Must have two toggles (did not find semicolon)");
                        }
                    } catch(NumberFormatException e) {
                        Tools.printLog("Unreadable time in toggle.");
                    }
                } else {
                    Tools.printLog("Must have both toggles and number of minutes defined (!toggle;!toggle2:mins)");
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        
        context.start();
    }


    /* **********************************  TIMERTASK CLASSES  ************************************ */

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
     * Task used to toggle bot options on or off.  (Define toggles inside CFG.)
     */
    private class ToggleTask extends TimerTask {
    	
        String[] toggleOn;
        String[] toggleOff;
        boolean stateOn = false;

        public ToggleTask( String[] on, String[] off ) {
            toggleOn = on;
            toggleOff = off;
        }

        public void run() {
            if( stateOn ) {
                stateOn = false;
                for(int k = 0; k < toggleOff.length; k++) {
                    if( toggleOff[k].startsWith("*") ) {
                        m_botAction.sendUnfilteredPublicMessage( toggleOff[k] );
                    } else {
                        handleModCommand( m_botAction.getBotName(), toggleOff[k] );
                    }
                }
            } else {
                stateOn = true;
                for(int k = 0; k < toggleOn.length; k++) {
                    if( toggleOn[k].startsWith("*") ) {
                        m_botAction.sendUnfilteredPublicMessage( toggleOn[k] );
                    } else {
                        handleModCommand( m_botAction.getBotName(), toggleOn[k] );
                    }
                }
            }
        }
    }
 
}
