package twcore.bots.pubsystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.pubsystem.module.AbstractModule;
import twcore.bots.pubsystem.module.GameFlagTimeModule;
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
import twcore.core.events.KotHReset;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
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
import twcore.core.game.Player;
import twcore.core.util.Tools;

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
	private PubContext context;								// Context of the game

    private BotSettings m_botSettings;

    public static final int SPEC = 0;                   // Number of the spec ship
    public static final int FREQ_0 = 0;                 // Frequency 0
    public static final int FREQ_1 = 1;                 // Frequency 1

    private ToggleTask toggleTask;                      // Toggles commands on and off at a specified interval

    private boolean roamPub = true;						// True if bots auto-roam to public
    private boolean initLogin = true;                   // True if first arena login
    private int initialPub;                             // Order of pub arena to defaultjoin
    private String initialSpawn;                        // Arena initially spawned in

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
        
        context = new PubContext(m_botAction);
    }

    /**
     * Requests all of the appropriate events.
     * You don't need to bother about the events requested by the modules here
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
        
        if (botSettings.getString("Chat"+botNumber) != null) {
        	String chats = botSettings.getString("Chat"+botNumber);
        	m_botAction.sendUnfilteredPublicMessage("?chat=" + chats);
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
    	context.handleEvent(event);
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
    	
    	context.handleEvent(event);
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
            		"Private freqs:[" + (context.getPubUtil().isPrivateFrequencyEnabled() ? "ON" : "OFF") + "]  " + 
            		"Streak:[" + (context.getPubStreak().isEnabled() ? "ON" : "OFF") + "]  " +
            		"Store:[" + (context.getMoneySystem().isStoreOpened() ? "ON" : "OFF") + "]  " + 
            		"Kill-o-thon:[" + (context.getPubKillSession().isRunning() ? "ON" : "OFF") + "]  " +
            		"Duel:[" + (context.getPubChallenge().isEnabled() ? "ON" : "OFF") + "]"; 
            		//"Lottery:[" + (context.getP().isRunning() ? "ON" : "OFF") + "]; 
            	
            	
                m_botAction.sendPrivateMessage(playerName, message );
                m_botAction.sendPrivateMessage(playerName, "Type !help for a list of commands.");

                context.handleEvent(event);
            }

        } catch (Exception e) {
        	Tools.printStackTrace(e);
        }

    }


    public void handleEvent(KotHReset event) {
        if(event.isEnabled() && event.getPlayerID()==-1) {
            // Make the bot ignore the KOTH game (send that he's out immediately after restarting the game)
            m_botAction.endKOTH();
        }
    }

    public void handleEvent(Message event) {
    	
    	context.handleEvent(event);
    	
        String sender = getSender(event);
        int messageType = event.getMessageType();
        String message = event.getMessage().trim();

        if( message == null || sender == null )
            return;

        if((messageType == Message.PRIVATE_MESSAGE || messageType == Message.PUBLIC_MESSAGE ) )
            handlePublicCommand(sender, message);
        if ( m_botAction.getOperatorList().isHighmod(sender) || sender.equals(m_botAction.getBotName()) )
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
                doHelpCmd(sender);
            if(command.equals("!algorithm") || command.equals("!algo"))
                doAlgorithmCmd(sender);
            if(command.startsWith("!greetmessage"))
                doGreetMessageCmd(sender, command);
            if(command.equals("!about"))
                doAboutCmd(sender);
            else {
            	context.handleCommand(sender, command.toLowerCase());
            }
            
        } catch(RuntimeException e) {
        	e.printStackTrace();
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
    	
    	command = command.toLowerCase();
    	
        try {
        	if (command.equals("!setuparena") && m_botAction.getOperatorList().isOwner(sender)) {
        		setupArenaSetting();
        		m_botAction.sendSmartPrivateMessage(sender, "Setting changed!");
        	}
        	else {
        		context.handleModCommand(sender, command);
        	}
        } catch(RuntimeException e) {
            m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
    }

    public void doAboutCmd(String sender) {
    	String text = "This bot is an updated version of purepubbot, formerly known as RoboBoy/Girl.";
    	m_botAction.sendPrivateMessage(sender, text);
    	m_botAction.sendPrivateMessage(sender, "");
    	m_botAction.sendPrivateMessage(sender, "Credits: Arobas+ and Dexter (main update)");
    	m_botAction.sendPrivateMessage(sender, "         Subby and Eria (challenge/lottery feature)");
    	m_botAction.sendPrivateMessage(sender, "         Diakka and Flared (for the map and setting)");
    	m_botAction.sendPrivateMessage(sender, "         Witness, Dezmond and Cheese! (for their support)");
    	m_botAction.sendPrivateMessage(sender, "         Qan and Cpt. Guano (authors of purepubbot)");
    	m_botAction.sendPrivateMessage(sender, "         And many more...");
    }
    
    public void doAlgorithmCmd(String sender) {
    	m_botAction.sendPrivateMessage(sender, "This is a secret!");
    }
    
    public void doGreetMessageCmd(String sender, String command) {
    	m_botAction.sendUnfilteredPublicMacro("?set Misc:GreetMessage:" + command.substring(14).trim());
    	m_botAction.sendPrivateMessage(sender, "Greeting message changed, reconnect to see the effect.");
    }

    /**
     * Displays a help message depending on access level.
     *
     * @param sender is the person issuing the command.
     */
    public void doHelpCmd(String sender)
    {
        Vector<String> messages = new Vector<String>();
        Vector<String> modMessages = new Vector<String>();
        
        for(AbstractModule module: context.getModules()) {
        	
        	List<String> m1 = new ArrayList<String>();
        	List<String> m2 = new ArrayList<String>();
        	
        	if (module.getHelpMessage().length>0 || module.getModHelpMessage().length>0) {
        		m1.add(getModuleHelpHeader(module.getName()));
        		m2.add(getModuleHelpHeader(module.getName()));
        	}
        	
        	if (module.getHelpMessage().length>0) {
        		m1.addAll(Arrays.asList(module.getHelpMessage()));
        		m2.addAll(Arrays.asList(module.getHelpMessage()));
        	}
        	
        	if (module.getModHelpMessage().length>0) {
        		m2.add("-- Mod+ --");
        		m2.addAll(Arrays.asList(module.getModHelpMessage()));
        	}

        	messages.addAll(m1);
        	modMessages.addAll(m2);
        }
        
    	String[] others = new String[] {
    			getModuleHelpHeader("Others"),
    			getHelpLine("!algorithm    -- How the robot calculate the money you earn for each kill."),
    			getHelpLine("!about        -- About this bot."),
    	};
    	modMessages.addAll(Arrays.asList(others));
    	messages.addAll(Arrays.asList(others));

        if( m_botAction.getOperatorList().isHighmod( sender ) )
            m_botAction.smartPrivateMessageSpam(sender, (String[])modMessages.toArray(new String[modMessages.size()]));
        else
            m_botAction.smartPrivateMessageSpam(sender, (String[])messages.toArray(new String[messages.size()]));
    }
    
    public static String getHelpLine(String line) {
    	return " " + line;
    }
    
    public static String getModuleHelpHeader(String headerName) {
    	return "== " + Tools.formatString(headerName + " ", 25, "=");
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
	
    public void handleEvent(PlayerLeft event) {
        context.handleEvent(event);
    }
	
    public void handleDisconnect() {
    	context.handleDisconnect();
    }
    
    public void handleEvent(SQLResultEvent event){
    	context.handleEvent(event);
    }
    
    public void handleEvent(FlagClaimed event) {
        context.handleEvent(event);
    }

    public void handleEvent(PlayerDeath event) {
    	context.handleEvent(event);
    }

    public void handleEvent(Prize event) {
        context.handleEvent(event);
    }

    public void handleEvent(ScoreUpdate event) {
        context.handleEvent(event);
    }

    public void handleEvent(WeaponFired event) {
        context.handleEvent(event);
    }

    public void handleEvent(FileArrived event) {
        context.handleEvent(event);
    }

    public void handleEvent(FlagVictory event) {
        context.handleEvent(event);
    }

    public void handleEvent(FlagReward event) {
        context.handleEvent(event);
    }

    public void handleEvent(ScoreReset event) {
        context.handleEvent(event);
    }

    public void handleEvent(WatchDamage event) {
        context.handleEvent(event);
    }

    public void handleEvent(SoccerGoal event) {
        context.handleEvent(event);
    }

    public void handleEvent(BallPosition event) {
        context.handleEvent(event);
    }

    public void handleEvent(FlagPosition event) {
        context.handleEvent(event);
    }

    public void handleEvent(FlagDropped event) {
        context.handleEvent(event);
    }
    
    public void handleEvent(PlayerPosition event) {
        context.handleEvent(event);
    }

    public void handleEvent(FrequencyShipChange event) {
    	context.handleEvent(event);
    }

    public void handleEvent(FrequencyChange event) {
    	context.handleEvent(event);
    }
    
    /*
     * This method should be called only once to change the current setting of the arena
     * The bot needs to be sysop of course.
     */
    public void setupArenaSetting() {
    	
    	String[] ships = new String[] {
    		"Warbird",
    		"Javelin",
    		"Spider",
    		"Leviathan",
    		"Terrier",
    		"Weasel",
    		"Lancaster",
    		"Shark"
    	};
    	
    	// Engine ShutDown Time set to 5 seconds
    	m_botAction.sendUnfilteredPublicMessage("?set Prize:EngineShutDownTime:500");
    	
    	for(String shipName: ships) {
    		m_botAction.sendUnfilteredPublicMessage("?set "+shipName+":ShieldTime:50000");
    		m_botAction.sendUnfilteredPublicMessage("?set "+shipName+":SuperTime:50000");
    		m_botAction.sendUnfilteredPublicMessage("?set "+shipName+":ThorMax:1");
    	}
    	
    }


    /* **********************************  TIMERTASK CLASSES  ************************************ */


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
