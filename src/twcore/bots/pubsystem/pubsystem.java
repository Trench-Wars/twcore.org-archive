package twcore.bots.pubsystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.pubsystem.module.AbstractModule;
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
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.Prize;
import twcore.core.events.SQLResultEvent;
import twcore.core.events.ScoreReset;
import twcore.core.events.ScoreUpdate;
import twcore.core.events.SoccerGoal;
import twcore.core.events.SocketMessageEvent;
import twcore.core.events.TurretEvent;
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

    public static final int SPEC = 0;                   // Number of the spec ship
    public static final int FREQ_0 = 0;                 // Frequency 0
    public static final int FREQ_1 = 1;                 // Frequency 1

    private ToggleTask toggleTask;                      // Toggles commands on and off at a specified interval

    private boolean roamPub = true;						// True if bots auto-roam to public
    private boolean initLogin = true;                   // True if first arena login
    private int initialPub;                             // Order of pub arena to defaultjoin
    private String initialSpawn;                        // Arena initially spawned in
    
    private String greeting;
    
    /**
     * Creates a new instance of pubsystem bot and initializes necessary data.
     *
     * @param Reference to bot utility class
     */
    public pubsystem(BotAction botAction)
    {
        super(botAction);
        requestEvents();

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
    	try {
	        initialSpawn = m_botAction.getBotSettings().getString("InitialArena");
	        initialPub = (m_botAction.getBotSettings().getInt("Pub" + m_botAction.getBotNumber()) - 1);
	        
	        String arena = initialSpawn;
	        int botNumber = m_botAction.getBotSettings().getInt(m_botAction.getBotName() + "Pub");
	        
	        if (m_botAction.getBotSettings().getString("Arena"+botNumber) != null) {
	        	roamPub = false;
	        	arena = m_botAction.getBotSettings().getString("Arena"+botNumber);
	        }
	        
	        if (m_botAction.getBotSettings().getString("Chats") != null) {
	        	String chats = m_botAction.getBotSettings().getString("Chats");
	        	m_botAction.sendUnfilteredPublicMessage("?chat=" + chats);
	        }
	        
	        try {
				m_botAction.joinArena(arena,(short)3392,(short)3392); // Max resolution
			} catch (Exception e) {
				m_botAction.joinArena(arena);
			}
			
	        context = new PubContext(m_botAction);
	        context.handleEvent(event);

	        m_botAction.getShip().setSpectatorUpdateTime(200);
	        m_botAction.receiveAllPlayerDeaths();
	        
	        m_botAction.socketSubscribe("PUBSYSTEM");
	        
	        greeting = (m_botAction.getBotSettings().getString("Greeting"));
	        if (greeting.isEmpty())
	            greeting = null;
	        
    	} catch (Exception e) {
    		Tools.printStackTrace(e);
    	}

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
    	if (!roamPub || context.isStarted())
    		return;
    	
    	try {
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
		} catch (Exception e) {
			Tools.printStackTrace(e);
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
            String playerName = m_botAction.getPlayerName(playerID);

            if(context.isStarted()) {
            	
            	String message = 
            		//"Welcome to Pub.  " +
            		"Private freqs:[" + (context.getPubUtil().isPrivateFrequencyEnabled() ? "ON" : "OFF") + "]  " + 
            		"Streak:[" + (context.getPubStreak().isEnabled() ? "ON" : "OFF") + "]  " +
            		"Store:[" + (context.getMoneySystem().isStoreOpened() ? "ON" : "OFF") + "]  " + 
            		"Kill-o-thon:[" + (context.getPubKillSession().isRunning() ? "ON" : "OFF") + "]  " +
            		"Duel:[" + (context.getPubChallenge().isEnabled() ? "ON" : "OFF") + "]  " +
            		"Hunt:[" + (context.getPubHunt().isEnabled() ? "ON" : "OFF") + "]";
            		//"Lottery:[" + (context.getP().isRunning() ? "ON" : "OFF") + "]; 
            	
            	
                m_botAction.sendSmartPrivateMessage(playerName, message );

                context.handleEvent(event);
            }
            
            if (greeting != null)
                m_botAction.sendSmartPrivateMessage(playerName, greeting);

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
        if (message == null || sender == null)
            return;

        if ((messageType == Message.PRIVATE_MESSAGE || messageType == Message.PUBLIC_MESSAGE))
            handlePublicCommand(sender, message, messageType);
        if (m_botAction.getOperatorList().isZH(sender) && (message.startsWith("!newplayer ") || message.startsWith("!next ") || message.startsWith("!end "))) {
            if ((messageType == Message.PRIVATE_MESSAGE || messageType == Message.REMOTE_PRIVATE_MESSAGE))
                handleModCommand(sender, message);
        } else if (m_botAction.getOperatorList().isModerator(sender) || sender.equals(m_botAction.getBotName()) || m_botAction.getOperatorList().isBotExact(sender))
            if ((messageType == Message.PRIVATE_MESSAGE || messageType == Message.REMOTE_PRIVATE_MESSAGE)) {
                handleModCommand(sender, message);
                if (m_botAction.getOperatorList().isSmod(sender)) {
                    handleSmodCommand(sender, message);
                    if (message.startsWith("!greet"))
                        doGreet(sender, message);
                }
            }
    }


    /* **********************************  COMMANDS  ************************************ */

    /**
     * Handles public commands sent to the bot, either in PM or pub chat.
     *
     * @param sender is the person issuing the command.
     * @param command is the command that is being sent.
     */
    public void handlePublicCommand(String sender, String command, int messageType) {
    	
        try {
            
            if(command.equals("!help") || command.equals("!h"))
                doHelpCmd(sender, false);
            else if(command.equals("!help -tutorial"))
                doTutorialHelpCmd(sender);
            //else if(command.equals("!algorithm") || command.equals("!algo"))
            //    doAlgorithmCmd(sender);
            else if(command.startsWith("!greetmessage"))
                doGreetMessageCmd(sender, command);
            else if(command.equals("!about"))
                doAboutCmd(sender);
            else if(command.equals("!tutorial"))
                context.getPubUtil().doTutorial(sender);
            else if(command.equals("!next"))
                context.getPubUtil().doNext(sender, true);
            else if(command.equals("!end"))
                context.getPubUtil().doEnd(sender);
            else if(command.equals("!quickhelp"))
                context.getPubUtil().doQuickHelp(sender);
            else if (command.startsWith("!") && !command.contains(" ") && command.length()>1 && command.charAt(1)!='!' && messageType == Message.PUBLIC_MESSAGE) {
            	m_botAction.sendSmartPrivateMessage(sender, "Please, send your command in private. Try :" + m_botAction.getBotName() + ":" + command);
            }
            else if (messageType != Message.PUBLIC_MESSAGE) {
            	context.handleCommand(sender, command.toLowerCase());
            }
            
        } catch(RuntimeException e) {
        	Tools.printStackTrace(e);
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
            if(command.startsWith("New Player:")) {
                context.getPubUtil().handleNewPlayer(command);
                return;
            }
            command = command.toLowerCase();
        	if (command.equals("!setuparena") && m_botAction.getOperatorList().isOwner(sender)) {
        		setupArenaSetting();
        		m_botAction.sendSmartPrivateMessage(sender, "Setting changed!");
        	}
        	else if(command.equals("!modhelp") || command.equals("!helpmod"))
                doHelpCmd(sender, true);
        	else {
        		context.handleModCommand(sender, command);
        	}
        } catch(RuntimeException e) {
        	Tools.printStackTrace(e);
            m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
    }
    
    /**
     * Handles smod-only commands sent to the bot.
     *
     * @param sender is the person issuing the command.
     * @param command is the command that is being sent.
     */
    public void handleSmodCommand(String sender, String command) {
        context.handleSmodCommand(sender, command);
    }
    
    public void doGreet(String name, String cmd) {
        BotSettings set = m_botAction.getBotSettings();
        if (cmd.length() < 7) {
            greeting = null;
            set.put("Greeting", "");
            m_botAction.sendSmartPrivateMessage(name, "Private message greeting DISABELD.");
        } else {
            greeting = cmd.substring(cmd.indexOf(" ") + 1);
            set.put("Greeting", greeting);
            m_botAction.sendSmartPrivateMessage(name, "Set PM greeting to: " + greeting);
        }
        set.save();
    }

    public void doAboutCmd(String sender) {
    	String text = "This bot is an updated version of purepubbot, formerly known as RoboBoy/Girl.";
    	m_botAction.sendSmartPrivateMessage(sender, text);
    	m_botAction.sendSmartPrivateMessage(sender, "");
    	m_botAction.sendSmartPrivateMessage(sender, "Credits: Arobas+ and Dexter (main update)");
    	m_botAction.sendSmartPrivateMessage(sender, "         Subby and Eria (challenge/lottery feature)");
    	m_botAction.sendSmartPrivateMessage(sender, "         Diakka and Flared (for the map and setting)");
    	m_botAction.sendSmartPrivateMessage(sender, "         Witness, Dezmond and Cheese! (for their support)");
    	m_botAction.sendSmartPrivateMessage(sender, "         Qan and Cpt. Guano (authors of purepubbot)");
    	m_botAction.sendSmartPrivateMessage(sender, "         And many more...");
    }
    
    public void doAlgorithmCmd(String sender) {
    	m_botAction.sendSmartPrivateMessage(sender, "This is a secret!");
    }
    
    public void doGreetMessageCmd(String sender, String command) {
    	m_botAction.sendUnfilteredPublicMacro("?set Misc:GreetMessage:" + command.substring(14).trim());
    	m_botAction.sendSmartPrivateMessage(sender, "Greeting message changed, reconnect to see the effect.");
    }

    /**
     * Displays a help message depending on access level.
     *
     * @param sender is the person issuing the command.
     */
    public void doHelpCmd(String sender, boolean modHelp)
    {
        Vector<String> lines = new Vector<String>();
        
        if (!modHelp) {
        	
			for(AbstractModule module: context.getModules()) {
				
            	if (!module.isEnabled())
            		continue;
            	
				List<String> m = new ArrayList<String>();
				if (module.getHelpMessage(sender).length>0) {
					m.add(getModuleHelpHeader(module.getName()));
				}
				if (module.getHelpMessage(sender).length>0) {
					m.addAll(Arrays.asList(module.getHelpMessage(null)));
					m.add(" ");
				}
				lines.addAll(m);
			}
             
	     	String[] others = new String[] {
	     			getModuleHelpHeader("Others"),
	     			//getHelpLine("!algorithm        -- How the robot calculate the money you earn for each kill."),
	     			getHelpLine("!about            -- About this bot."),
	     	};
	     	
	    	lines.addAll(Arrays.asList(others));
 			if( m_botAction.getOperatorList().isModerator( sender ) )
 				lines.add(getHelpLine("!helpmod          -- Show the !help menu for Mod+."));
	     	
 			lines.add(" ");
 	        lines.add("Note: Commands must be sent in private.");
 			
	    	m_botAction.smartPrivateMessageSpam(sender, (String[])lines.toArray(new String[lines.size()]));
        	
        } else {
        	boolean smod = false;
        	if (m_botAction.getOperatorList().isSmod(sender))
        	    smod = true;
        	
            for(AbstractModule module: context.getModules()) {
            	List<String> m = new ArrayList<String>();
            	if (module.getModHelpMessage(sender).length>0) {
            		m.add(getModuleHelpHeader(module.getName()));
            	}
            	if (module.getModHelpMessage(sender).length>0) {
            		m.addAll(Arrays.asList(module.getModHelpMessage(sender)));
            		m.add(" ");
            	}
            	if (smod && module.getSmodHelpMessage(sender).length > 0) {
                    m.addAll(Arrays.asList(module.getSmodHelpMessage(sender)));
                    m.add("- !greet <msg>      -- Change private message greeting.");
                    m.add(" ");            	    
            	}
            	lines.addAll(m);
            }
            
            if( m_botAction.getOperatorList().isModerator( sender ) )
                m_botAction.smartPrivateMessageSpam(sender, (String[])lines.toArray(new String[lines.size()]));

        }

    }
    
    public void doTutorialHelpCmd(String sender){
        List<String> list = new ArrayList<String>();
        String st = "This is your guide to use our tutorial.";
        list.add(st);
        st = "Use !tutorial to start it";
        list.add(st);
        st = "Use !next to see step by step";
        list.add(st);
        st = "Use !quickhelp to see the whole tutorial";
        list.add(st);
        st = "If you're done, try !end";
        list.add(st);
        st = "Thanks to Flared and WingZero for creating the tutorial in Trench Wars!";
        list.add(st);
        m_botAction.remotePrivateMessageSpam(sender, list.toArray(new String[list.size()]));
    }
    
    public static String getHelpLine(String line) {
    	return "- " + line;
    }
    
    public static String getModuleHelpHeader(String headerName) {
    	return "[" + Tools.formatString(headerName + "] ", 25, " ");
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
        	
        	if (context.isStarted())
        		return;
        	
            String commands[] = m_botAction.getBotSettings().getString("Setup" + m_botAction.getBotNumber()).split(",");
        	for(int k = 0; k < commands.length; k++) {
        		handleModCommand(m_botAction.getBotName(), commands[k]);
    		}
            String toggleInfoString = m_botAction.getBotSettings().getString("ToggleOptions" + m_botAction.getBotNumber());
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
            Tools.printStackTrace(e);
        }

        context.start();
        
        String commands[] = m_botAction.getBotSettings().getString("Post" + m_botAction.getBotNumber()).split(",");
        for(int k = 0; k < commands.length; k++) {
            handleModCommand(m_botAction.getBotName(), commands[k]);
        }
        
    }
	
    public void handleEvent(SocketMessageEvent event){

    	if (event.getRequest().equals("GETPLAYERS")) {
    		Iterator<Player> it = m_botAction.getPlayerIterator();
    		
    		// Building a JSON-format result
    		StringBuilder builder = new StringBuilder();
    		while(it.hasNext()) {
    			Player p = it.next();
    			builder.append(p.getPlayerName()+":"+p.getXTileLocation()+":"+p.getYTileLocation()+":"+p.getShipType() + "$$:$$");
    		}

    		event.setResponse(builder.toString());
    	}
    	
    	context.handleEvent(event);
    }
	
    public void handleEvent(PlayerLeft event) {
    	if (context!=null)
    		context.handleEvent(event);
    }
	
    public void handleDisconnect() {
    	if (context!=null)
    		context.handleDisconnect();
    }
    
    public void handleEvent(InterProcessEvent event){
    	if (context!=null)
    		context.handleEvent(event);
    }
    
    public void handleEvent(SQLResultEvent event){
    	if (context!=null)
    		context.handleEvent(event);
    }
    
    public void handleEvent(TurretEvent event) {
    	if (context!=null)
    		context.handleEvent(event);
    }
    
    public void handleEvent(FlagClaimed event) {
    	if (context!=null)
    		context.handleEvent(event);
    }

    public void handleEvent(PlayerDeath event) {
    	if (context!=null)
    		context.handleEvent(event);
    }

    public void handleEvent(Prize event) {
    	if (context!=null)
    		context.handleEvent(event);
    }

    public void handleEvent(ScoreUpdate event) {
    	if (context!=null)
    		context.handleEvent(event);
    }

    public void handleEvent(WeaponFired event) {
    	if (context!=null)
            context.handleEvent(event);
    }

    public void handleEvent(FileArrived event) {
    	if (context!=null)
            context.handleEvent(event);
    }

    public void handleEvent(FlagVictory event) {
    	if (context!=null)
            context.handleEvent(event);
    }

    public void handleEvent(FlagReward event) {
    	if (context!=null)
            context.handleEvent(event);
    }

    public void handleEvent(ScoreReset event) {
    	if (context!=null)
            context.handleEvent(event);
    }

    public void handleEvent(WatchDamage event) {
    	if (context!=null)
        context.handleEvent(event);
    }

    public void handleEvent(SoccerGoal event) {
    	if (context!=null)
            context.handleEvent(event);
    }

    public void handleEvent(BallPosition event) {
    	if (context!=null)
            context.handleEvent(event);
    }

    public void handleEvent(FlagPosition event) {
    	if (context!=null)
            context.handleEvent(event);
    }

    public void handleEvent(FlagDropped event) {
    	if (context!=null)
            context.handleEvent(event);
    }
    
    public void handleEvent(PlayerPosition event) {
    	if (context!=null)
            context.handleEvent(event);
    }

    public void handleEvent(FrequencyShipChange event) {        
    	if (context!=null)
            context.handleEvent(event);
    }

    public void handleEvent(FrequencyChange event) {
    	if (context!=null)
            context.handleEvent(event);
    }
    
    /*
     * This method should be called only once to change the current setting of the arena
     * The bot needs to be sysop of course.
     */
    public void setupArenaSetting() {

        
        // Engine ShutDown Time set to 5 seconds
        m_botAction.sendUnfilteredPublicMessage("?set Prize:EngineShutDownTime:500");
        /*
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
    	for(String shipName: ships) {
    		//m_botAction.sendUnfilteredPublicMessage("?set "+shipName+":ShieldTime:50000");
    		//m_botAction.sendUnfilteredPublicMessage("?set "+shipName+":SuperTime:50000");
    		//m_botAction.sendUnfilteredPublicMessage("?set "+shipName+":ThorMax:1");
    		//m_botAction.sendUnfilteredPublicMessage("?set "+shipName+":XRadarStatus:1");
    		//m_botAction.sendUnfilteredPublicMessage("?set "+shipName+":AntiWarpStatus:1");
    		//m_botAction.sendUnfilteredPublicMessage("?set "+shipName+":BrickMax:1");
    	}
    	*/
    	// Specific
    	//m_botAction.sendUnfilteredPublicMessage("?set Javelin:XRadarStatus:2");
    	//m_botAction.sendUnfilteredPublicMessage("?set Terrier:XRadarStatus:2");

    	// No ?buy
    	/*
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Energy:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Rotation:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Stealth:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Cloak:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:XRadar:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Gun:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Bomb:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Bounce:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Thrust:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Speed:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:MultiFire:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Prox:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Super:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Shield:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Shrap:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:AntiWarp:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Repel:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Burst:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Decoy:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Thor:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Brick:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Rocket:0");
    	m_botAction.sendUnfilteredPublicMessage("?set Cost:Portal:0");
    	*/

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