package twcore.bots.twdbot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.TimerTask;
import java.util.ArrayList;
import java.util.Vector;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerLeft;
import twcore.core.events.SQLResultEvent;
import twcore.core.game.Player;
import twcore.core.stats.DBPlayerData;
import twcore.core.util.Tools;

/**
 * Handles TWD administration tasks such as registration and deletion.
 * Prevents players from playing from any but registered IP/MID, unless
 * overridden by an Op.
 */
public class twdbot extends SubspaceBot {

    String m_arena;
    BotSettings m_botSettings;
    OperatorList m_opList;
    LinkedList<DBPlayerData> m_players;
    LinkedList<SquadOwner> m_squadowner;
    
    HashMap<String, Boolean> arenas;
    Vector<String> needsBot;
    Vector<String> needsDie;
    Vector<String> readyBots;
    Vector<String> dying;
    HashMap<String, Long> spawning;

    public HashMap<String, String> m_requesters;

    private String register = "";
    private HashMap<String, String> m_waitingAction;
    private HashMap<String, Squad> m_squads;
    private String webdb = "website";
    private boolean manualSpawnOverride;
    private boolean startingUp;
    private boolean shuttingDown;
    private static final String HUB = "TWCore-League";
    private static final String TWBD = "6";
    private static final String TWDD = "8";
    private static final String TWJD = "15";
    private static final String TWSD = "19";
    private static final long SPAWN_RETRY = 100000;

    int ownerID;

    /** Creates a new instance of twdbot */
    public twdbot( BotAction botAction) {
        //Setup of necessary stuff for any bot.
        super( botAction );

        m_botSettings   = m_botAction.getBotSettings();
        m_arena     = m_botSettings.getString("Arena");
        m_opList        = m_botAction.getOperatorList();

        m_players = new LinkedList<DBPlayerData>();
        m_squadowner = new LinkedList<SquadOwner>();
        
        arenas = new HashMap<String, Boolean>();
        readyBots = new Vector<String>();
        needsBot = new Vector<String>();
        needsDie = new Vector<String>();
        dying = new Vector<String>();
        spawning = new HashMap<String, Long>();
        manualSpawnOverride = false;
        startingUp = true;
        shuttingDown = false;

        m_waitingAction = new HashMap<String, String>();
        m_requesters = new HashMap<String, String>();
        
        m_squads = new HashMap<String, Squad>();
        requestEvents();
    }



    public void requestEvents() {
        EventRequester req = m_botAction.getEventRequester();
        req.request( EventRequester.MESSAGE );
        req.request( EventRequester.PLAYER_LEFT );
    }
    
    public void handleEvent(PlayerLeft event) {
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (name == null)
            return;
        
        if (m_opList.isBotExact(name)) {
            readyBots.remove(name);
        }
    }
    
    public void checkIN() {
        arenas.clear();
        m_botAction.ipcTransmit("MatchBot", "twdmatchbots:checkin");
        
        TimerTask ch = new TimerTask() {
            @Override
            public void run() {
                checkArenas();
            }
        };
        m_botAction.scheduleTask(ch, 5000);
    }
    
    public void spawn(String arena) {
        if (manualSpawnOverride)
            return;
        // spawn bot
        // add arena to the to-be-locked list

        arena = arena.toLowerCase();
        needsBot.add(arena);

        if (!startingUp) {
            String div = arena.substring(0, 4);
            if (readyBots.isEmpty()) {
                long time = System.currentTimeMillis();
                if (spawning.containsKey(arena)) {
                    if (time - spawning.get(arena) > SPAWN_RETRY) {
                        spawning.put(arena, time);
                        m_botAction.sendSmartPrivateMessage(HUB, "!spawn matchbot");
                    }
                } else {
                    spawning.put(arena, time);
                    m_botAction.sendSmartPrivateMessage(HUB, "!spawn matchbot");
                }
            } else {
                String bot = readyBots.remove(0);
                needsBot.removeElement(arena);
                lockBot(bot, div);
            }
        }        
    }
    
    public void lockBot(String bot, String arena) {
        arenas.put(arena, null);
        String div = arena.substring(0, 4);
        m_botAction.sendChatMessage("Sending " + bot + " to " + arena + "...");
        if (div.equalsIgnoreCase("twbd"))
            m_botAction.sendSmartPrivateMessage(bot, "!lock " + TWBD);
        else if (div.equalsIgnoreCase("twdd"))
            m_botAction.sendSmartPrivateMessage(bot, "!lock " + TWDD);
        else if (div.equalsIgnoreCase("twjd"))
            m_botAction.sendSmartPrivateMessage(bot, "!lock " + TWJD);
        else if (div.equalsIgnoreCase("twsd"))
            m_botAction.sendSmartPrivateMessage(bot, "!lock " + TWSD);
    }
    
    public void remove(String arena) {
        if (manualSpawnOverride)
            return;
        arena = arena.toLowerCase();
        if (arenas.containsKey(arena)) {
            if (arenas.get(arena)) {
                m_botAction.sendChatMessage("Adding " + arena + " to the kill queue..." + " (!manualspawn to stop spawn control)");
                // add to the to-be-removed list
                if (!needsDie.contains(arena))
                    needsDie.add(arena);
            } else {
                m_botAction.sendChatMessage("Sending kill request to " + arena + "..." + " (!manualspawn to stop spawn control)");
                // kill bot
                arenas.remove(arena);
                if (!needsDie.contains(arena))
                    needsDie.add(arena);
                m_botAction.ipcTransmit("MatchBot", "twdmatchbot:" + arena + " die");
            }
        }
    }
    
    public void stay(String arena) {
        arena = arena.toLowerCase();
        needsDie.remove(arena);
        m_botAction.ipcTransmit("MatchBot", "twdmatchbot:" + arena + " stay");
    }
    
    public void checkArenas() {
        if (manualSpawnOverride)
            return;
        checkDiv("twbd");
        checkDiv("twdd");
        checkDiv("twjd");
        checkDiv("twsd");

        m_botAction.sendChatMessage("Checking TWD arenas...");
        
        if (startingUp)
            startLock();
    }
    
    public void checkDiv(String div) {
        if (manualSpawnOverride)
            return;
        
        div = div.toLowerCase();
        
        if (arenas.containsKey(div) && arenas.get(div) != null) {
            spawning.remove(div); // in case TWDBot didn't register a bot locked in this twd arena
            if (arenas.get(div)) {
                // a1 exists and has game
                if (arenas.containsKey(div + "2") && arenas.get(div + "2") != null) {
                    spawning.remove(div + "2"); // in case TWDBot didn't register a bot locked in this twd arena
                    if (arenas.get(div + "2")) {
                        // a1 & a2 exist and have games
                        if (needsDie.contains(div + "2"))
                            stay(div + "2");
                        if (arenas.containsKey(div + "3") && arenas.get(div + "3") != null) {
                            spawning.remove(div + "3"); // in case TWDBot didn't register a bot locked in this twd arena
                            if (arenas.get(div + "3")) {
                                if (needsDie.contains(div + "3"))
                                    stay(div + "3");
                                // a1, a2, a3 exist and have games
                                // --> spawn a4 if no exist
                                if (arenas.containsKey(div + "4") && arenas.get(div + "4") != null) {
                                    spawning.remove(div + "4"); // in case TWDBot didn't register a bot locked in this twd arena
                                    if (arenas.get(div + "4") && needsDie.contains(div + "3"))
                                        stay(div + "3");
                                } else {
                                    if (arenas.get(div + "4") == null)
                                        arenas.remove(div + "4");
                                    // a4 does not exist
                                    // --> spawn it  
                                    spawn(div + "4");                                    
                                }
                            } else {
                                // a3 exists, no game  
                                // --> remove remaining arenas 
                                remove(div + "4");     
                            }
                        } else {
                            if (arenas.get(div + "3") == null)
                                arenas.remove(div + "3");
                            // a3 does not exist
                            // --> spawn it
                            // --> remove remaining arenas   
                            spawn(div + "3");   
                            remove(div + "4");             
                        }
                    } else {
                        // a2 exists, no game  
                        // --> remove remaining arenas  
                        remove(div + "3");
                        remove(div + "4");                    
                    }
                } else {
                    if (arenas.get(div + "2") == null)
                        arenas.remove(div + "2");
                    // a2 does not exist
                    // --> spawn it
                    // --> remove remaining arenas
                    spawn(div + "2");
                    remove(div + "3");
                    remove(div + "4");
                }
            } else {
                // a1 exists, no game
                // --> remove remaining arenas
                remove(div + "2");
                remove(div + "3");
                remove(div + "4");
            }
        } else {
            if (arenas.get(div) == null)
                arenas.remove(div);
            // a1 does not exist
            // --> spawn it
            // --> remove remaining arenas
            spawn(div);
            remove(div + "2");
            remove(div + "3");
            remove(div + "4");
        }        
    }
    
    public void startLock() {
        while (!readyBots.isEmpty() && !needsBot.isEmpty()) {
            String arena = needsBot.remove(0);
            String div = arena.substring(0, 4);
            String bot = readyBots.remove(0);
            lockBot(bot, div);
        }
        
        startingUp = false;
        
        if (!needsBot.isEmpty()) {
            String[] aa = needsBot.toArray(new String[needsBot.size()]);
            needsBot.clear();
            for (String a : aa) {
                spawn(a);
            }
        }
    }


    public static String[] stringChopper( String input, char deliniator ){
        try
        {
            LinkedList<String> list = new LinkedList<String>();

            int nextSpace = 0;
            int previousSpace = 0;

            if( input == null ){
                return null;
            }

            do{
                previousSpace = nextSpace;
                nextSpace = input.indexOf( deliniator, nextSpace + 1 );

                if ( nextSpace!= -1 ){
                    String stuff = input.substring( previousSpace, nextSpace ).trim();
                    if( stuff!=null && !stuff.equals("") )
                        list.add( stuff );
                }

            } while( nextSpace != -1 );
            String stuff = input.substring( previousSpace );
            stuff=stuff.trim();
            if (stuff.length() > 0) {
                list.add( stuff );
            };
            return list.toArray(new String[list.size()]);
        }
        catch(Exception e)
        {
            throw new RuntimeException("Error in stringChopper.");
        }
    }

    public void handleEvent(InterProcessEvent event) {
        if (event.getChannel().equals("MatchBot")) {
            if (event.getObject() instanceof String) {
                String msg = (String) event.getObject();
                if (msg.startsWith("twdinfo:newgame")) {
                    //newgame matchID,squad,squad,type,state,arena
                    String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                    arenas.put(args[4].toLowerCase(), true);
                    if (args.length != 5)
                        return;
                    int id;
                    try {
                        id = Integer.valueOf(args[0]);
                    } catch (NumberFormatException e) {
                        return;
                    }
                    
                    String arena = args[4].toLowerCase();
                    Game g1 = new Game(id, args[2], args[3], arena);
                    Game g2 = new Game(id, args[1], args[3], arena);

                    Squad squad;
                    if (m_squads.containsKey(args[1].toLowerCase())) {
                        squad = m_squads.get(args[1].toLowerCase());
                        squad.putGame(g1);
                    } else {
                        m_squads.put(args[1].toLowerCase(), new Squad(args[1], g1));
                    }
                    if (m_squads.containsKey(args[2].toLowerCase())) {
                        squad = m_squads.get(args[2].toLowerCase());
                        squad.putGame(g2);
                    } else {
                        m_squads.put(args[2].toLowerCase(), new Squad(args[2], g2));
                    }
                    checkDiv(arena.substring(0, 4));
                } else if (msg.startsWith("twdinfo:gamestate")) {
                    String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                    if (args.length != 4)
                        return;
                    int id, state;
                    try {
                        id = Integer.valueOf(args[0]);
                        state = Integer.valueOf(args[3]);
                    } catch (NumberFormatException e) {
                        return;
                    }
                    if (m_squads.containsKey(args[1].toLowerCase()) && m_squads.containsKey(args[2].toLowerCase())) {
                        Squad squad;
                        squad = m_squads.get(args[1].toLowerCase());
                        squad.setState(id, state);
                        squad = m_squads.get(args[2].toLowerCase());
                        squad.setState(id, state);
                    }
                } else if (msg.startsWith("twdinfo:endgame")) {
                    // matchID,squad,squad,arena
                    String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                    if (args.length != 4)
                        return;
                    int id;
                    try {
                        id = Integer.valueOf(args[0]);
                    } catch (NumberFormatException e) {
                        return;
                    }
                    
                    String arena = args[3].toLowerCase();
                    arenas.put(arena, false);
                    
                    checkDiv(arena.substring(0, 4));

                    if (m_squads.containsKey(args[1].toLowerCase()) && m_squads.containsKey(args[2].toLowerCase())) {
                        Squad squad;
                        squad = m_squads.get(args[1].toLowerCase());
                        if (squad.end(id))
                            m_squads.remove(args[1].toLowerCase());
                        squad = m_squads.get(args[2].toLowerCase());
                        if (squad.end(id))
                            m_squads.remove(args[2].toLowerCase());
                    }
                    
                    if (needsDie.contains(arena)) {
                        remove(arena);                   
                    }
                    
                } else if (msg.startsWith("twdmatchbot:checkin:")) {
                    // twdmatchbot:checkin:bot:arena:game
                    String[] args = msg.split(":");
                    if (args.length == 4) {
                        if (args[3].equalsIgnoreCase("twd"))
                            readyBots.add(args[2]);
                        else 
                            arenas.put(args[3].toLowerCase(), false);
                    } else if (args.length == 5) {
                        arenas.put(args[3].toLowerCase(), true);
                    } 
                } else if (msg.startsWith("twdmatchbot:spawned")) {
                    if (manualSpawnOverride)
                        return;
                    final String bot = msg.substring(msg.indexOf(" ") + 1);
                    if (!needsBot.isEmpty()) {
                        final String arena = needsBot.remove(0);
                        TimerTask lock = new TimerTask() {
                            @Override
                            public void run() {
                                lockBot(bot, arena);
                            }
                        };
                        m_botAction.scheduleTask(lock, 2000);
                    } else {
                        readyBots.add(bot);
                    }
                } else if (msg.startsWith("twdmatchbot:locked ")) {
                    String arena = msg.substring(msg.indexOf(" ") + 1).toLowerCase();
                    arenas.put(arena, false);
                } else if (msg.startsWith("twdmatchbot:dying ")) {
                    // arena,bot
                    String[] args  = msg.substring(msg.indexOf(" ") + 1).split(",");
                    if (args.length == 2) {
                        String arena = args[0].toLowerCase();
                        String bot = args[1];
                        if (!dying.contains(bot))
                            dying.add(bot);   
                        
                        needsDie.remove(arena);                                             
                    }
                } else if (msg.startsWith("twdmatchbot:shuttingdown ")) {
                    // arena,bot
                    String[] args  = msg.substring(msg.indexOf(" ") + 1).split(",");
                    if (args.length == 2) {
                        String bot = args[1];
                        String arena = args[0].toLowerCase();
                        if (!dying.contains(bot))
                            dying.add(bot);
                        
                        needsDie.remove(arena);                        
                    }
                } else if (msg.startsWith("twdmatchbot:staying ")) {
                    // arena,bot
                    String[] args  = msg.substring(msg.indexOf(" ") + 1).split(",");
                    if (args.length == 2) {
                        String arena = args[0].toLowerCase();
                        String bot = args[1];
                        needsDie.remove(arena);
                        
                        if (dying.removeElement(bot))
                            m_botAction.sendChatMessage(bot + " has been prevented from dying in " + arena + " (!manualspawn to stop spawn control)");
                    }
                }
            }
        }
    }
    
    public void handleEvent( Message event )
    {
            boolean isStaff;
            String message = event.getMessage();
            
            String messager = m_botAction.getPlayerName(event.getPlayerID());
            if (messager == null)
            	messager = event.getMessager();
            
            if (!manualSpawnOverride && event.getMessageType() == Message.CHAT_MESSAGE) {
                if (message.contains("(matchbot)") && message.contains("disconnected")) {
                    String bot = message.substring(0, message.indexOf("("));
                    if (!shuttingDown && !dying.removeElement(bot)) {
                        m_botAction.sendChatMessage("Unexpected disconnect detected - calling for checkIN...");
                        checkIN();
                    } else if (shuttingDown) {
                        dying.removeElement(bot);
                    }
                }
            }
            
            if( event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ){
                String name = m_botAction.getPlayerName( event.getPlayerID() );
                if( m_opList.isER( name )) 
                	isStaff = true; 
                else 
                	isStaff= false;
                
                if (messager.startsWith("TW-Guard")) {
                    String msg = event.getMessage();
                    if (msg.startsWith("twdplayer") && !m_squads.isEmpty()) {
                        String[] args = msg.substring(msg.indexOf(" ") + 1).split(":");
                        if (args.length == 2 && m_squads.containsKey(args[1].toLowerCase())) {
                            Squad squad = m_squads.get(args[1].toLowerCase());
                            HashMap<Integer, Game> games = squad.getGames();
                            for (Game game : games.values()) {
                                if (!game.alerted(args[0])) {
                                    String state;
                                    if (game.getState() == 0)
                                        state = "preparing";
                                    else
                                        state = "playing";
                                    m_botAction.sendSmartPrivateMessage(args[0], "Your squad is " + state + " a " + game.type() + " match against " + game.op() + " in ?go " + game.arena());
                                }
                            }
                        }
                    }
                }

                if( m_opList.isSysop( name ) || isTWDOp(name) || m_opList.isOwner(name)) {
                    if (message.startsWith("!manualspawn")) {
                        commandManualSpawn(name);
                        return;
                    } else if (message.startsWith("!forcecheck")) {
                        m_botAction.sendSmartPrivateMessage(name, "Initiating a check of TWD arenas.");
                        checkArenas();
                        return;
                    } else if (message.startsWith("!shutdowntwd")) {
                        m_botAction.sendSmartPrivateMessage(name, "Initiating shutdown of all matchbots.");
                        m_botAction.sendChatMessage("Total MatchBot shutdown requested by " + name);
                        m_botAction.cancelTasks();
                        shuttingDown = true;
                        m_botAction.ipcTransmit("MatchBot", "all twdbots die");
                        arenas.clear();
                        needsBot.clear();
                        readyBots.clear();
                        needsDie.clear();
                        return;
                    }
                }

                if( m_opList.isSysop( name ) || isTWDOp(name) )
                {
                    //Operator commands
                    if( message.startsWith( "!resetname " ) )
                        commandResetName( name, message.substring( 11 ), false );
                    else if( message.startsWith( "!cancelreset " ) )
                        commandCancelResetName( name, message.substring( 13 ), false );
                    else if( message.startsWith( "!resettime " ) )
                        commandGetResetTime( name, message.substring( 11 ), false, false );
                    else if( message.startsWith( "!enablename " ) )
                        commandEnableName( name, message.substring( 12 ) );
                    else if( message.startsWith( "!disablename " ) )
                        commandDisableName( name, message.substring( 13 ) );
                    else if( message.startsWith( "!info " ) )
                        commandDisplayInfo( name, message.substring( 6 ), false );
                    else if( message.startsWith( "!fullinfo " ) )
                        commandDisplayInfo( name, message.substring( 10 ), true );
                    else if( message.startsWith( "!register " ) )
                        commandRegisterName( name, message.substring( 10 ), false );
                    else if( message.startsWith( "!registered " ) )
                        commandCheckRegistered( name, message.substring( 12 ) );
                    else if( message.equals( "!twdops" ) )
                    	commandTWDOps( name );
                    else if( message.startsWith( "!altip " ) )
                        commandIPCheck( name, message.substring( 7 ), true );
                    else if( message.startsWith( "!altmid " ) )
                        commandMIDCheck( name, message.substring( 8 ), true );
                    else if( message.startsWith( "!check " ) )
                        checkIP( name, message.substring( 7 ) );
                    else if( message.startsWith( "!go " ) )
                        m_botAction.changeArena( message.substring( 4 ) );
                    else if( message.startsWith( "!help" ) )
                        commandDisplayHelp( name, false );
                    else if( message.startsWith("!add "))
                        commandAddMIDIP(name, message.substring(5));
                    else if( message.startsWith("!removeip "))
                        commandRemoveIP(name, message.substring(10));
                    else if( message.startsWith("!removemid "))
                        commandRemoveMID(name, message.substring(11));
                    else if( message.startsWith("!removeipmid "))
                        commandRemoveIPMID(name, message.substring(13));
                    else if( message.startsWith("!listipmid "))
                        commandListIPMID(name, message.substring(11));
                    else if( message.equalsIgnoreCase("!die")) {
        				this.handleDisconnect();
        				m_botAction.die();
        			}
                }
                else
                {
                    if( ! (event.getMessageType() == Message.PRIVATE_MESSAGE) )
                        return;
                    //Player commands
                    if( message.equals( "!resetname" ) )
                        commandResetName( name, name, true);
                    else if( message.equals( "!resettime" ) )
                        commandGetResetTime( name, name, true, false );
                    else if( message.equals( "!cancelreset" ) )
                        commandCancelResetName( name, name, true );
                    else if( message.equals( "!registered" ) )
                        commandCheckRegistered( name, name );
                    else if( message.startsWith( "!registered " ) )
                        commandCheckRegistered( name, message.substring( 12 ) );
                    else if( message.equals( "!register" ) )
                        commandRegisterName( name, name, true );
                    else if( message.equals( "!twdops" ) )
                    	commandTWDOps( name );
                    else if( message.equals( "!help" ) )
                        commandDisplayHelp( name, true );
                }

                // First: convert the command to a command with parameters
                String command = stringChopper(message, ' ')[0];
                String[] parameters = stringChopper( message.substring( command.length() ).trim(), ':' );
                for (int i=0; i < parameters.length; i++) parameters[i] = parameters[i].replace(':',' ').trim();
                command = command.trim();

                parseCommand( name, command, parameters, isStaff );
            }

            if( event.getMessageType() == Message.ARENA_MESSAGE) {	// !squadsignup
                if (event.getMessage().startsWith("Owner is ")) {
                    String squadOwner = event.getMessage().substring(9);

                    ListIterator<SquadOwner> i = m_squadowner.listIterator();
                    while (i.hasNext())
                    {
                        SquadOwner t = i.next();
                        if (t.getID() == ownerID) {
                            if (t.getOwner().equalsIgnoreCase(squadOwner)) {
                                storeSquad(t.getSquad(), t.getOwner());
                            } else {
                                m_botAction.sendSmartPrivateMessage(t.getOwner(), "You are not the owner of the squad " + t.getSquad());
                            }
                        }
                    }
                    ownerID++;
                } else if (message.startsWith( "IP:" )) { // !register
                    parseIP( message );
                }
            }
    }
    
    public void commandManualSpawn(String name) {
        if (!manualSpawnOverride) {
            manualSpawnOverride = true;
            spawning.clear();
            needsBot.clear();
            needsDie.clear();
            arenas.clear();
            m_botAction.cancelTasks();
            m_botAction.sendSmartPrivateMessage(name, "Manual spawn override has been ENABLED.");
            m_botAction.sendChatMessage("Manual spawn override ENABLED by " + name);
        } else {
            manualSpawnOverride = false;
            spawning.clear();
            needsBot.clear();
            needsDie.clear();
            arenas.clear();
            startingUp = true;
            checkIN();
            m_botAction.sendChatMessage("Manual spawn override DISABLED by " + name + ". MatchBot spawn control restarting.");
            m_botAction.sendSmartPrivateMessage(name, "Manual spawn override has been DISABLED. Restarting the bot spawn control system.");
        }
    }
    
    public boolean isTWDOp(String name){
        try{
            ResultSet result = m_botAction.SQLQuery(webdb, "SELECT DISTINCT tblUser.fcUserName FROM tblUser, tblUserRank"+
                                                           " WHERE tblUser.fcUserName = '"+Tools.addSlashesToString(name)+"'"+
                                                           " AND tblUser.fnUserID = tblUserRank.fnUserID"+
                                                           " AND ( tblUserRank.fnRankID = 14 OR tblUserRank.fnRankID = 19 )");
            if(result != null && result.next()){
                m_botAction.SQLClose(result);
                return true;
            } else {
                m_botAction.SQLClose(result);
                return false;
            }                
        }catch(SQLException e){
            Tools.printStackTrace(e);
            return false;
        }
    }
    
    public void commandTWDOps(String name){
    		try{
    			HashSet<String> twdOps = new HashSet<String>();
    			ResultSet rs = m_botAction.SQLQuery(webdb, "SELECT tblUser.fcUserName, tblUserRank.fnRankID FROM tblUser, tblUserRank"+
                                                           " WHERE tblUser.fnUserID = tblUserRank.fnUserID"+
                                                           " AND ( tblUserRank.fnRankID = 14 OR tblUserRank.fnRankID = 19 )");
    			while(rs != null && rs.next()){
    				String queryName;
    				String temp = rs.getString("fcUserName");
    				queryName = temp;
    				if(rs.getInt("fnRankID") == 19){
    					queryName = temp + " (SMod)";
    					twdOps.remove(temp);
    				}
    				if(!twdOps.contains(queryName) && !twdOps.contains(queryName + " (SMod)"))
    					twdOps.add(queryName);
    			}
    			Iterator<String> it = twdOps.iterator();
    			ArrayList<String> bag = new ArrayList<String>();
    			m_botAction.sendSmartPrivateMessage(name, "+------------------- TWD Operators --------------------");
    			while(it.hasNext()){
    				bag.add(it.next());
    				if(bag.size() == 4){
    					String row = "";
    					for(int i = 0;i<4;i++){
    						row = row + bag.get(i) + ", ";
    					}
    					m_botAction.sendSmartPrivateMessage( name, "| " + row.substring(0, row.length() - 2));
    					bag.clear();
    				}
    			}
    			if(bag.size() != 0){
    				String row = "";
    				for(int i=0;i<bag.size();i++){
    					row = row + bag.get(i) + ", ";
    				}
    				m_botAction.sendSmartPrivateMessage( name, "| " + row.substring(0, row.length() - 2));
    			}
    			m_botAction.sendSmartPrivateMessage( name, "+------------------------------------------------------");
    		} catch(SQLException e){
    			Tools.printStackTrace(e);
    		}
    }
    
    public void handleEvent(SQLResultEvent event) {
    	if (event.getIdentifier().equals("twdbot"))
    		m_botAction.SQLClose( event.getResultSet() );
    }

    public void commandAddMIDIP(String staffname, String info) {
        try {
            info = info.toLowerCase();
            String pieces[] = info.split("  ", 3);
            HashSet<String> names = new HashSet<String>();
            HashSet<String> IPs = new HashSet<String>();
            HashSet<String> mIDs = new HashSet<String>();
            for(int k = 0;k < pieces.length;k++) {
                if(pieces[k].startsWith("name"))
                    names.add(pieces[k].split(":")[1]);
                else if(pieces[k].startsWith("ip"))
                    IPs.add(pieces[k].split(":")[1]);
                else if(pieces[k].startsWith("mid"))
                    mIDs.add(pieces[k].split(":")[1]);
            }
            Iterator<String> namesIt = names.iterator();
            while(namesIt.hasNext()) {
                String name = namesIt.next();
                Iterator<String> ipsIt = IPs.iterator();
                Iterator<String> midsIt = mIDs.iterator();
                while(ipsIt.hasNext() || midsIt.hasNext()) {
                    String IP = null;
                    if(ipsIt.hasNext()) IP = ipsIt.next();
                    String mID = null;
                    if(midsIt.hasNext()) mID = midsIt.next();
                    if(IP == null && mID == null) {
                        m_botAction.sendSmartPrivateMessage(staffname, "Syntax (note double spaces):  !add name:thename  ip:IP  mid:MID");
                    } else if(IP == null) {
                        ResultSet r = m_botAction.SQLQuery(webdb, "SELECT fnUserID FROM tblTWDPlayerMID WHERE fcUserName='"+Tools.addSlashesToString(name)+"' AND fnMID=" + mID );
                        if( r.next() ) {
                            m_botAction.sendPrivateMessage(staffname, "Entry for '" + name + "' already exists with that MID in it.");
                            m_botAction.SQLClose(r);
                            return;
                        }
                        m_botAction.SQLClose(r);
                        m_botAction.SQLQueryAndClose(webdb, "INSERT INTO tblTWDPlayerMID (fnUserID, fcUserName, fnMID) VALUES "
                                + "((SELECT fnUserID FROM tblUser WHERE fcUserName = '"+Tools.addSlashesToString(name)+"' LIMIT 0,1), "
                                + "'"+Tools.addSlashesToString(name)+"', "+Tools.addSlashesToString(mID)+")");
                        m_botAction.sendSmartPrivateMessage(staffname, "Added MID: " + mID);
                    } else if(mID == null) {
                        ResultSet r = m_botAction.SQLQuery(webdb, "SELECT fnUserID FROM tblTWDPlayerMID WHERE fcUserName='"+Tools.addSlashesToString(name)+"' AND fcIP='" + IP +"'" );
                        if( r.next() ) {
                            m_botAction.sendPrivateMessage(staffname, "Entry for '" + name + "' already exists with that IP in it.");
                            m_botAction.SQLClose(r);
                            return;
                        }
                        m_botAction.SQLClose(r);
                        m_botAction.SQLQueryAndClose(webdb, "INSERT INTO tblTWDPlayerMID (fnUserID, fcUserName, fcIP) VALUES "
                                + "((SELECT fnUserID FROM tblUser WHERE fcUserName = '"+Tools.addSlashesToString(name)+"' LIMIT 0,1), "
                                + "'"+Tools.addSlashesToString(name)+"', '"+Tools.addSlashesToString(IP)+"')");
                        m_botAction.sendSmartPrivateMessage(staffname, "Added IP: " + IP);
                    } else {
                        ResultSet r = m_botAction.SQLQuery(webdb, "SELECT fnUserID FROM tblTWDPlayerMID WHERE fcUserName='"+Tools.addSlashesToString(name)+"' AND fcIP='" + IP + "' AND fnMID=" + mID );
                        if( r.next() ) {
                            m_botAction.sendPrivateMessage(staffname, "Entry for '" + name + "' already exists with that IP/MID combination.");
                            m_botAction.SQLClose(r);
                            return;
                        }
                        m_botAction.SQLClose(r);
                        m_botAction.SQLQueryAndClose(webdb, "INSERT INTO tblTWDPlayerMID (fnUserID, fcUserName, fnMID, fcIP) VALUES "
                                + "((SELECT fnUserID FROM tblUser WHERE fcUserName = '"+Tools.addSlashesToString(name)+"' LIMIT 0,1), "
                                + "'"+Tools.addSlashesToString(name)+"', "+Tools.addSlashesToString(mID)+", "
                                + "'"+Tools.addSlashesToString(IP)+"')");
                        m_botAction.sendSmartPrivateMessage(staffname, "Added IP " + IP + " and MID " + mID + " into a combined entry.");
                    }
                }
            }
        } catch(Exception e) {
            m_botAction.sendPrivateMessage(staffname, "An unexpected error occured. Please contact a bot developer with the following message: "+e.getMessage());
        }
    }

    public void commandRemoveMID(String Name, String info) {
        try {
            String pieces[] = info.split(":", 2);
            if(pieces.length < 2) {
                m_botAction.sendPrivateMessage(Name, "Needs to be like !removemid <name>:<mid>");
            }
            String name = pieces[0];
            String mID = pieces[1];
            m_botAction.SQLQueryAndClose(webdb, "UPDATE tblTWDPlayerMID SET fnMID = 0 WHERE fcUserName = '"+Tools.addSlashesToString(name)+"' AND fnMID = "+Tools.addSlashesToString(mID));
            m_botAction.sendPrivateMessage(Name, "MID removed.");
        } catch(Exception e) {e.printStackTrace();}
    }

    public void commandRemoveIP(String Name, String info) {
        try {
            String pieces[] = info.split(":", 2);
            if(pieces.length < 2) {
                m_botAction.sendPrivateMessage(Name, "Needs to be like !removeip <name>:<IP>");
            }
            String name = pieces[0];
            String IP = pieces[1];
            m_botAction.SQLQueryAndClose(webdb, "UPDATE tblTWDPlayerMID SET fcIP = '0.0.0.0' WHERE fcUserName = '"+Tools.addSlashesToString(name)+"' AND fcIP = '"+Tools.addSlashesToString(IP)+"'");
            m_botAction.sendPrivateMessage(Name, "IP removed.");
        } catch(Exception e) {e.printStackTrace();}
    }

    public void commandRemoveIPMID(String name, String playerName) {
        try {
            m_botAction.SQLQueryAndClose(webdb, "DELETE FROM tblTWDPlayerMID WHERE fcUserName = '"+Tools.addSlashesToString(playerName)+"'" );
            m_botAction.sendPrivateMessage(name, "Removed all IP and MID entries for '" + playerName + "'.");
        } catch (SQLException e) {}
    }

    public void commandListIPMID(String name, String player) {
        DBPlayerData dbP = new DBPlayerData( m_botAction, webdb, player );
        m_botAction.sendSmartPrivateMessage( name, "TWD record for '" + player + "'" );
        String header = "Status: ";
        if( dbP.getStatus() == 0 ) {
            header += "NOT REGISTERED";
        } else {
            if( dbP.getStatus() == 1 )
                header += "REGISTERED";
            else
                header += "DISABLED";
            header += "  Registered IP: " + dbP.getIP() + "  Registered MID: " + dbP.getMID();
        }

        try {
            ResultSet results = m_botAction.SQLQuery(webdb, "SELECT fcIP, fnMID FROM tblTWDPlayerMID WHERE fcUserName = '"+Tools.addSlashesToString(player)+"'");
            if( !results.next() )
                m_botAction.sendPrivateMessage(name, "There are no staff-registered IPs and MIDs for this name." );
            else {
                m_botAction.sendPrivateMessage(name, "Staff-registered IP and MID exceptions:" );
                do {
                    String message = "";
                    if(!results.getString("fcIP").equals("0.0.0.0"))
                        message += "IP: " + results.getString("fcIP") + "   ";
                    if(results.getInt("fnMID") != 0)
                        message += "mID: " + results.getInt("fnMID");
                    m_botAction.sendPrivateMessage(name, message);
                } while( results.next() );
            }
            m_botAction.SQLClose( results );
        } catch(Exception e) {e.printStackTrace();}

    }

    public void parseCommand(String name, String command, String[] parameters, boolean isStaff) {
        try
        {
            if( command.equals("!squads"))
                command_squads(name);
            if (command.equals("!signup")) {
                command_signup(name, command, parameters);
            }
            if (command.equals("!squadsignup")) {
                command_squadsignup(name, command);
            }
            if (command.equals("!help")) {
                String help[] = {
                        "--------- TWD/TWL COMMANDS -----------------------------------------------------------",
                        "!signup <password>      - Replace <password> with a password which is hard to guess.",
                        "                          You are safer if you choose a password that differs",
                        "                          completely from your current SSCU Continuum password.",
                        "                          Example: !signup mypass. This command will get you an",
                        "                          useraccount for TWL and TWD. If you have forgotten your",
                        "                          password, you can use this to pick a new password",
                        "!squadsignup            - This command will sign up your current ?squad for TWD.",
                        "                          Note: You need to be the squadowner of the squad",
                        "                          and !registered"
                };
                m_botAction.privateMessageSpam(name, help);
            }
        }
        catch(Exception e)
        {
            throw new RuntimeException("Error in parseCommand.");
        }
    }

    public void handleEvent( LoggedOn event ) {
        m_botAction.joinArena( m_arena );
        ownerID = 0;
        m_botAction.ipcSubscribe("MatchBot");
        TimerTask checkMessages = new TimerTask() {
            public void run() {
                checkMessages();
                checkNamesToReset();
            };
        };
        m_botAction.scheduleTaskAtFixedRate(checkMessages, 5000, 30000);
        m_botAction.sendUnfilteredPublicMessage("?chat=robodev");
        checkIN();
    }

    public void command_signup(String name, String command, String[] parameters) {
        try {
        	
        	DBPlayerData data = new DBPlayerData(m_botAction, webdb, name, false);
        	
        	// Special if captain or staff
        	boolean specialPlayer = m_botAction.getOperatorList().isER(name) || data.hasRank(4);
        	
            if (parameters.length > 0 && passwordIsValid(parameters[0], specialPlayer)) {
                boolean success = false;
                boolean can_continue = true;

                String fcPassword = parameters[0];
                DBPlayerData thisP;

                thisP = findPlayerInList(name);
                if (thisP != null) {
                    if (System.currentTimeMillis() - thisP.getLastQuery() < 300000) {
                        can_continue = false;
                    }
                }

                if (thisP == null) {
                    thisP = new DBPlayerData(m_botAction, webdb, name, true);
                    success = thisP.getPlayerAccountData();
                } else {
                    success = true;
                }

                if (can_continue) {
                    if (!success) {
                        success = thisP.createPlayerAccountData(fcPassword);
                    } else {
                        if (!thisP.getPassword().equals(fcPassword)) {
                            success = thisP.updatePlayerAccountData(fcPassword);
                        }
                    }

                    if (!thisP.hasRank(2)) thisP.giveRank(2);

                    if (success) {
                        m_botAction.sendSmartPrivateMessage(name, "This is your account information: ");
                        m_botAction.sendSmartPrivateMessage(name, "Username: " + thisP.getUserName());
                        m_botAction.sendSmartPrivateMessage(name, "Password: " + thisP.getPassword());
                        m_botAction.sendSmartPrivateMessage(name, "To join your squad roster, go to http://twd.trenchwars.org . Log in, click on 'My Profile', select your squad and then click on 'Apply to this squad'");
                        m_players.add(thisP);
                    } else {
                        m_botAction.sendSmartPrivateMessage(name, "Couldn't create/update your user account.  Contact a TWDOp with the ?help command for assistance.");
                    }
                } else {
                    m_botAction.sendSmartPrivateMessage(name, "You can only signup / change passwords once every 5 minutes");
                }
            } else {
            	if (specialPlayer)
            		m_botAction.sendSmartPrivateMessage(name, "Specify a password, ex. '!signup MySuperPass11!'. The password must contain at least 1 number, 1 uppercase, and needs to be at least 10 characters long. ");
            	else
            		m_botAction.sendSmartPrivateMessage(name, "Specify a password, ex. '!signup MyPass11'. The password must contain a number and needs to be at least 8 characters long.");
            }

        }
        catch(Exception e) {
            throw new RuntimeException("Error in command_signup.");
        }
    };

    public boolean passwordIsValid(String password, boolean specialPlayer) {

		boolean digit = false;
		boolean uppercase = false;
		boolean length = false;
		boolean specialcharacter = false;
		
        for (int i = 0; i < password.length(); i++) {

        	if (Character.isDigit(password.charAt(i))) {
                digit = true;
            }
        	if (Character.isUpperCase(password.charAt(i))) {
        		uppercase = true;
            }
        	if (!Character.isLetterOrDigit(password.charAt(i))) {
        		specialcharacter = true;
            }
        }
        
    	if (specialPlayer) {
            if (password.length() >= 10) {
            	length = true;
            } 
            return length && digit && uppercase;
    		
    	}
    	else {
    		
            if (password.length() >= 8) {
            	length = true;
            } 
    		
    		return length && digit;
    		
    	}

    }

    public void command_squadsignup(String name, String command) {
        Player p = m_botAction.getPlayer(name);
        String squad = p.getSquadName();
        if (squad.equals(""))
        {
            m_botAction.sendSmartPrivateMessage(name, "You are not in a squad.");
        } else {
            m_squadowner.add(new SquadOwner(name, squad, ownerID));
            m_botAction.sendUnfilteredPublicMessage("?squadowner " + squad);
        }
    }


    public DBPlayerData findPlayerInList(String name) {
        try
        {
            ListIterator<DBPlayerData> l = m_players.listIterator();
            DBPlayerData thisP;

            while (l.hasNext()) {
                thisP = l.next();
                if (name.equalsIgnoreCase(thisP.getUserName())) return thisP;
            };
            return null;
        }
        catch(Exception e)
        {
            throw new RuntimeException("Error in findPlayerInList.");
        }
    };



    public void checkMessages() {
        try {
            // Improved query: tblMessage is only used for TWD score change messages, so we only need to check for unprocessed msgs
            ResultSet s = m_botAction.SQLQuery(webdb, "SELECT * FROM tblMessage WHERE fnProcessed = 0");
            while (s != null && s.next()) {
                if (s.getString("fcMessageType").equalsIgnoreCase("squad")) {
                    m_botAction.sendSquadMessage(s.getString("fcTarget"), s.getString("fcMessage"), s.getInt("fnSound"));
                    // Delete messages rather than update them as sent, as we don't need to keep records.
                    // This table is only used to send msgs between the website and this bot.
                    m_botAction.SQLQueryAndClose(webdb, "DELETE FROM tblMessage WHERE fnMessageID = " + s.getInt("fnMessageID"));
                };
            };
            m_botAction.SQLClose( s );
        } catch (SQLException e) {
            Tools.printStackTrace(e);
        };
    };


    public void storeSquad(String squad, String owner) {

        try
        {
            DBPlayerData thisP2 = new DBPlayerData(m_botAction, webdb, owner, false);

            if (thisP2 == null || !thisP2.isRegistered()) {
                m_botAction.sendSmartPrivateMessage(owner, "Your name has not been !registered. Please private message me with !register.");
                return;
            }
            if (!thisP2.isEnabled()) {
                m_botAction.sendSmartPrivateMessage( owner, "Your name has been disabled from TWD.  (This means you may not register a squad.)  Contact a TWDOp with ?help for assistance." );
                return;
            }

            DBPlayerData thisP = new DBPlayerData(m_botAction, webdb, owner, false);

            if (thisP != null)
            {
                if (thisP.getTeamID() == 0)
                {
                    ResultSet s = m_botAction.SQLQuery(webdb, "select fnTeamID from tblTeam where fcTeamName = '" + Tools.addSlashesToString(squad) + "' and (fdDeleted = 0 or fdDeleted IS NULL)");
                    if (s.next()) {
                        m_botAction.sendSmartPrivateMessage(owner, "That squad is already registered..");
                        m_botAction.SQLClose( s );;
                        return;
                    } else m_botAction.SQLClose( s );

                    String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                    String fields[] = {
                            "fcTeamName",
                            "fdCreated"
                    };
                    String values[] = {
                            Tools.addSlashesToString(squad),
                            time
                    };
                    m_botAction.SQLInsertInto(webdb, "tblTeam", fields, values);

                    int teamID;

                    // This query gets the latest inserted TeamID from the database to associate the player with
                    ResultSet s2 = m_botAction.SQLQuery(webdb, "SELECT MAX(fnTeamID) AS fnTeamID FROM tblTeam");
                    if (s2.next()) {
                        teamID = s2.getInt("fnTeamID");
                        m_botAction.SQLClose( s2 );
                    } else {
                        m_botAction.sendSmartPrivateMessage(owner, "Database error, contact a TWD Op.");
                        m_botAction.SQLClose( s2 );
                        return;
                    }

                    String fields2[] = {
                            "fnUserID",
                            "fnTeamID",
                            "fdJoined",
                            "fnCurrentTeam"
                    };
                    String values2[] = {
                            Integer.toString(thisP.getUserID()),
                            Integer.toString(teamID),
                            time,
                            "1"
                    };
                    m_botAction.SQLInsertInto(webdb, "tblTeamUser", fields2, values2);

                    thisP.giveRank(4);

                    m_botAction.sendSmartPrivateMessage(owner, "The squad " + squad + " has been signed up for TWD.");
                } else
                    m_botAction.sendSmartPrivateMessage(owner, "You must leave your current squad first.");
            } else
                m_botAction.sendSmartPrivateMessage(owner, "You must !signup first.");
        }
        catch (Exception e)
        {
            m_botAction.sendSmartPrivateMessage(owner, "Database error, contact a TWD Op.");
        }
    }

    class SquadOwner {
        String owner = "", squad = "";
        int id;

        public SquadOwner(String name, String tSquad, int tID) {
            owner = name;
            squad = tSquad;
            id = tID;
        };

        public String getOwner() { return owner; };
        public String getSquad() { return squad; };
        public int getID() { return id; };
    };




    // aliasbot

    public void commandCheckRegistered( String name, String message )
    {

        DBPlayerData dbP = new DBPlayerData( m_botAction, webdb, message );

        if( dbP.isRegistered() )
            m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has been registered." );
        else
            m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has NOT been registered." );
    }

    public void commandResetName( String name, String message, boolean player )
    {

        DBPlayerData dbP = new DBPlayerData( m_botAction, webdb, message );

        if( !dbP.isRegistered() )
        {
            if( player )
                m_botAction.sendSmartPrivateMessage( name, "Your name '"+message+"' has not been registered." );
            else
                m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has not been registered." );
            return;
        }

        if( !dbP.isEnabled() )
        {
            m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' is disabled and can't be reset." );
            return;
        }

        if ( player ) {
            if( dbP.hasBeenDisabled() ) {
                m_botAction.sendSmartPrivateMessage( name, "Unable to reset name.  Please contact a TWD Op for assistance." );
                return;
            }
            if( !resetPRegistration(dbP.getUserID()) )
                m_botAction.sendSmartPrivateMessage( name, "Unable to reset name.  Please contact a TWD Op for assistance." );
            
            else if(isBeingReseted(name, message))
                return ;
            
            else {
                try {
                    m_botAction.SQLQueryAndClose(webdb, "DELETE FROM tblTWDPlayerMID WHERE fcUserName = '"+Tools.addSlashesToString(name)+"'");
                } catch (SQLException e) {}
                m_botAction.sendSmartPrivateMessage( name, "Your name will be reset in 24 hours." );
                m_botAction.sendTeamMessage(name+" will be reseted in 24 hrs(testing reset func) - 1");
            }
        } else {
            if( dbP.hasBeenDisabled() ) {
                m_botAction.sendSmartPrivateMessage( name, "That name has been disabled.  If you are sure the player should be allowed to play, enable before resetting." );
                return;
            }
            if ( !dbP.resetRegistration() )
                m_botAction.sendSmartPrivateMessage( name, "Error resetting name '"+message+"'.  The name may not exist in the database." );
            else {
                try {
                    m_botAction.SQLQueryAndClose(webdb, "DELETE FROM tblTWDPlayerMID WHERE fcUserName = '"+Tools.addSlashesToString(name)+"'");
                } catch (SQLException e) {}
                m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has been reset, and all IP/MID entries have been removed." );
                m_botAction.sendTeamMessage(name+" is force-reseted(testing reset func) - 2");
                
            }
        }
    }

    public void commandCancelResetName( String name, String message, boolean player )
    {
        DBPlayerData dbP = new DBPlayerData( m_botAction, webdb, message );

        try
        {
            ResultSet s = m_botAction.SQLQuery( webdb, "SELECT * FROM tblAliasSuppression WHERE fnUserID = '" + dbP.getUserID() + "' && fdResetTime IS NOT NULL");
            if (s.next())
            {
                m_botAction.SQLBackgroundQuery( webdb, "twdbot", "UPDATE tblAliasSuppression SET fdResetTime = NULL WHERE fnUserID = '" + dbP.getUserID() + "'");

                if (player)
                {
                    m_botAction.sendSmartPrivateMessage( name, "Your name has been removed from the list of names about to get reset.");
                } else {
                    m_botAction.sendSmartPrivateMessage( name, "The name '" + message + "' has been removed from the list of names about to get reset.");
                }
            } else {
                if (player)
                {
                    m_botAction.sendSmartPrivateMessage( name, "Your name isn't on the list of names about to get reset.");
                } else {
                    m_botAction.sendSmartPrivateMessage( name, "The name '" + message + "' was not found on the list of names about to get reset.");
                }
            }
            m_botAction.SQLClose( s );
        }
        catch (Exception e)
        {
            if (player)
            {
                m_botAction.sendSmartPrivateMessage( name, "Database error, contact a TWD Op.");
            } else {
                m_botAction.sendSmartPrivateMessage( name, "Database error: " + e.getMessage() + ".");
            }
        }
    }

    public boolean isBeingReseted( String name, String message){
        DBPlayerData database = new DBPlayerData( m_botAction, webdb, message);
        try{
            String query = "SELECT DATE_ADD(fdResetTime, INTERVAL 1 DAY) as resettime FROM tblAliasSuppression WHERE fnUserId = '"+database.getUserID()+"' && fdResetTime IS NOT NULL";
            ResultSet rs = m_botAction.SQLQuery( webdb , query);
            if(rs.next()){ //then it'll be really reseted
                String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                m_botAction.sendPrivateMessage(name, "Your name will be reseted  at "+rs.getString("resettime")+" already. Current time: "+time+" ..So please wait a bit more!");
                return true;
            }
        }catch(SQLException e){}
        return false;
    }
    public void commandGetResetTime( String name, String message, boolean player, boolean silent )
    {
        DBPlayerData dbP = new DBPlayerData( m_botAction, webdb, message );

        try
        {
            ResultSet s = m_botAction.SQLQuery( webdb, "SELECT DATE_ADD(fdResetTime, INTERVAL 1 DAY) AS resetTime FROM tblAliasSuppression WHERE fnUserID = '" + dbP.getUserID() + "' && fdResetTime IS NOT NULL");
            if (s.next())
            {
                String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                if (player)
                {
                    m_botAction.sendSmartPrivateMessage( name, "Your name will reset at " + s.getString("resetTime") + ". Current time: " + time);
                } else {
                    m_botAction.sendSmartPrivateMessage( name, "The name '" + message + "' will reset at " + s.getString("resetTime") + ". Current time: " + time);
                }
            } else {
                if (!silent) {
                    if (player)
                    {
                        m_botAction.sendSmartPrivateMessage( name, "Your name was not found on the list of names about to get reset.");
                    } else {
                        m_botAction.sendSmartPrivateMessage( name, "The name '" + message + "' was not found on the list of names about to get reset.");
                    }
                }
            }
            m_botAction.SQLClose( s );
        }
        catch (Exception e)
        {
            if (player)
            {
                m_botAction.sendSmartPrivateMessage( name, "Database error, contact a TWD Op.");
            } else {
                m_botAction.sendSmartPrivateMessage( name, "Database error: " + e.getMessage() + ".");
            }
        }
    }

    public void commandEnableName( String name, String message )
    {

        DBPlayerData dbP = new DBPlayerData( m_botAction, webdb, message );

        if( !dbP.isRegistered() )
        {
            m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has not been registered." );
            return;
        }

        if( dbP.isEnabled() )
        {
            m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' is already enabled." );
            return;
        }

        if( !dbP.enableName() )
        {
            m_botAction.sendSmartPrivateMessage( name, "Error enabling name '"+message+"'" );
            return;
        }
        m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has been enabled." );
    }

    public void commandDisableName( String name, String message )
    {
        // Create the player if not registered
        DBPlayerData dbP = new DBPlayerData( m_botAction, webdb, message, true );

        if( dbP.hasBeenDisabled() ) {
            m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has already been disabled." );
            return;
        }

        if( !dbP.disableName() ) {
            m_botAction.sendSmartPrivateMessage( name, "Error disabling name '"+message+"'" );
            return;
        }
        m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has been disabled, and will not be able to reset manually without being enabled again." );
    }

    public void commandDisplayInfo( String name, String message, boolean verbose )
    {

        DBPlayerData dbP = new DBPlayerData( m_botAction, webdb, message );

        if( !dbP.isRegistered() )
        {
            m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has not been registered." );
            return;
        }
        String status = "ENABLED";
        if( !dbP.isEnabled() ) status = "DISABLED";
        m_botAction.sendSmartPrivateMessage( name, "'"+message+"'  IP:"+dbP.getIP()+"  MID:"+dbP.getMID()+"  "+status + ".  Registered " + dbP.getSignedUp() );
        if( verbose ) {
            dbP.getPlayerSquadData();
            m_botAction.sendSmartPrivateMessage( name, "Member of '" + dbP.getTeamName() + "'; squad created " + dbP.getTeamSignedUp() );
        }
        commandGetResetTime( name, message, false, true );
    }

    public void commandRegisterName( String name, String message, boolean p )
    {

        Player pl = m_botAction.getPlayer( message );
        String player;
        if( pl == null )
        {
            m_botAction.sendSmartPrivateMessage( name, "Unable to find "+message+" in the arena." );
            return;
        } else {
            player = message;
        }

        DBPlayerData dbP = new DBPlayerData( m_botAction, webdb, player );

        if( dbP.isRegistered() )
        {
            m_botAction.sendSmartPrivateMessage( name, "This name has already been registered." );
            return;
        }
        register = name;
        if( p )
            m_waitingAction.put( player, "register" );
        else
            m_waitingAction.put( player, "forceregister" );
        m_botAction.sendUnfilteredPrivateMessage( player, "*info" );
    }

    public void commandIPCheck( String name, String ip, boolean staff )
    {

        try
        {
            String query = "SELECT fcUserName, fcIP, fnMID FROM tblAliasSuppression AS A, ";
            query += " tblUser AS U WHERE A.fnUserID = U.fnUserID AND fcIP LIKE '"+ip+"%'";
            ResultSet result = m_botAction.SQLQuery( webdb, query );
            while( result.next () )
            {
                String out = result.getString( "fcUserName" ) + "  ";
                if(staff) {
                    out += "IP:" + result.getString( "fcIP" ) + "  ";
                    out += "MID:" + result.getString( "fnMID" );
                }
                m_botAction.sendSmartPrivateMessage( name, out );
            }
            m_botAction.SQLClose( result );
        }
        catch (Exception e)
        {
            Tools.printStackTrace( e );
            m_botAction.sendSmartPrivateMessage( name, "Error doing IP check." );
        }
    }

    public void commandMIDCheck( String name, String mid, boolean staff )
    {

        if( mid == null || mid == "" || !(Tools.isAllDigits(mid)) ) {
            m_botAction.sendSmartPrivateMessage( name, "MID must be all numeric." );
            return;
        }

        try
        {
            String query = "SELECT fcUserName, fcIP, fnMID FROM tblAliasSuppression AS A, ";
            query += " tblUser AS U WHERE A.fnUserID = U.fnUserID AND fnMID = "+mid;
            ResultSet result = m_botAction.SQLQuery( webdb, query );
            while( result.next () )
            {
                String out = result.getString( "fcUserName" ) + "  ";
                if(staff) {
                    out += "IP:" + result.getString( "fcIP" ) + "  ";
                    out += "MID:" + result.getString( "fnMID" );
                }
                m_botAction.sendSmartPrivateMessage( name, out );
            }
            m_botAction.SQLClose( result );
        }
        catch (Exception e)
        {
            Tools.printStackTrace( e );
            m_botAction.sendSmartPrivateMessage( name, "Error doing MID check." );
        }
    }
    
    public void command_squads(String name) {
        if (m_squads.isEmpty()) {
            m_botAction.sendSmartPrivateMessage(name, "No squads are playing at this time :(");
        } else {
            String result = "Squads Playing: ";
            for (Squad squad : m_squads.values()) {
                result += squad.getName() + ", ";
            }
            result = result.substring(0, result.lastIndexOf(","));
            m_botAction.sendSmartPrivateMessage(name, result);
        }
    }

    public void commandDisplayHelp( String name, boolean player )
    {
        String help[] =
        {
                "--------- ACCOUNT MANAGEMENT COMMANDS ------------------------------------------------",
                "!resetname <name>       - resets the name (unregisters it)",
                "!resettime <name>       - returns the time when the name will be reset",
                "!cancelreset <name>     - cancels the !reset a player has issued",
                "!enablename <name>      - enables the name so it can be used in TWD/TWL games",
                "!disablename <name>     - disables the name so it can not be used in TWD/TWL games",
                "!register <name>        - force registers that name, that player must be in the arena",
                "!registered <name>      - checks if the name is registered",
                "!add name:<name>  ip:<IP>  mid:<MID> - Adds <name> to DB with <IP> and/or <MID>",
                "!removeip <name>:<IP>                - Removes <IP> associated with <name>",
                "!removemid <name>:<MID>              - Removes <MID> associated with <name>",
                "!removeipmid <name>                  - Removes all IPs and MIDs for <name>",
                "!listipmid <name>                    - Lists IP's and MID's associated with <name>",
                "--------- ALIAS CHECK COMMANDS -------------------------------------------------------",
                "!info <name>            - displays the IP/MID that was used to register this name",
                "!fullinfo <name>        - displays IP/MID, squad name, and date squad was reg'd",
                "!altip <IP>             - looks for matching records based on <IP>",
                "!altmid <MID>           - looks for matching records based on <MID>",
                "!ipidcheck <IP> <MID>   - looks for matching records based on <IP> and <MID>",
                "         <IP> can be partial address - ie:  192.168.0.",
                "--------- MISC COMMANDS --------------------------------------------------------------",
                "!check <name>           - checks live IP and MID of <name> (through *info, NOT the DB)",
                "!twdops                 - displays a list of the current TWD Ops",
                "!go <arena>             - moves the bot"
        };
        String SModHelp[] =
        {
                "--------- SMOD COMMANDS --------------------------------------------------------------",
                " TWD Operators are determined by levels on the website which can be modified at www.trenchwars.org/staff"
        };
        String help2[] =
        {
                "--------- ACCOUNT MANAGEMENT COMMANDS ------------------------------------------------",
                "!resetname              - resets your name",
                "!resettime              - returns the time when your name will be reset",
                "!cancelreset            - cancels the !resetname",
                "!register               - registers your name",
                "!registered <name>      - checks if the name is registered",
                "!twdops                 - displays a list of the current TWD Ops"
        };

        if( player )
            m_botAction.privateMessageSpam( name, help2 );
        else {
            m_botAction.privateMessageSpam( name, help );
            if(m_opList.isSmod(name)) {
                m_botAction.privateMessageSpam( name, SModHelp );
            }
        }
    }

    public void parseIP( String message )
    {

        String[] pieces = message.split("  ");

        //Log the *info message that used to make the bot crash
        if ( pieces.length < 5 ) { 
            Tools.printLog( "TWDBOT ERROR: " + message);
            
            //Make sure that the bot wont crash on it again
            return;
        }
            
        
        String name = pieces[3].substring(10);
        String ip = pieces[0].substring(3);
        String mid = pieces[5].substring(10);

        DBPlayerData dbP = new DBPlayerData( m_botAction, webdb, name );

        //If an info action wasn't set don't handle it
        if( m_waitingAction.containsKey( name ) ) {

            String option = m_waitingAction.get( name );
            m_waitingAction.remove( name );

            //Note you can't get here if already registered, so can't match yourself.
            if( dbP.aliasMatchCrude( ip, mid ) )
            {

                if( option.equals("register") )
                {
                    m_botAction.sendSmartPrivateMessage( name, "ERROR: One or more names have already been registered into TWD from your computer and/or internet address." );
                    m_botAction.sendSmartPrivateMessage( name, "Please login and reset your old name(s) with !resetname, wait 24 hours, and then !register this name." );
                    m_botAction.sendSmartPrivateMessage( name, "If one of these name(s) do not belong to you or anyone in your house, you may still register, but only with staff approval.  Type ?help <msg> for assistance." );
                    commandMIDCheck(name, mid, false);
                    commandIPCheck(name,ip,false);
                    return;
                }
                else
                    m_botAction.sendSmartPrivateMessage( register, "WARNING: Another account may have been registered on that connection." );
            }

            if( !dbP.register( ip, mid ) )
            {
                m_botAction.sendSmartPrivateMessage( register, "Unable to register name." );
                return;
            }
            commandAddMIDIP(name, "name:"+name+"  ip:"+ip+"  mid:"+mid);
            m_botAction.sendSmartPrivateMessage( register, "REGISTRATION SUCCESSFUL" );
            m_botAction.sendSmartPrivateMessage( register, "NOTE: Only one name per household is allowed to be registered with TWD staff approval.  If you have family members that also play, you must register manually with staff (type ?help <msg>)." );
            m_botAction.sendSmartPrivateMessage( register, "Holding two or more name registrations in one household without staff approval may result in the disabling of one or all names registered." );

        } else {
            if( m_requesters != null ) {
                String response = name + "  IP:"+ip+"  MID:"+mid;
                String requester = m_requesters.remove( name );
                if( requester != null )
                    m_botAction.sendSmartPrivateMessage( requester, response );
            }
        }
    }

    public boolean resetPRegistration(int id) {

        try {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            m_botAction.SQLBackgroundQuery( webdb, "twdbot", "UPDATE tblAliasSuppression SET fdResetTime = '"+time+"' WHERE fnUserID = '" + id + "'");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void checkNamesToReset() {
        try {
            m_botAction.SQLBackgroundQuery(webdb, "twdbot", "DELETE FROM tblAliasSuppression WHERE fdResetTime < DATE_SUB(NOW(), INTERVAL 1 DAY);");
        } catch (Exception e) {
            System.out.println("Can't check for new names to reset...");
        };
    };


    // ipbot

    public void checkIP( String name, String message ) {

        String target = m_botAction.getFuzzyPlayerName( message );
        if( target == null ) {
            m_botAction.sendSmartPrivateMessage( name, "Unable to find "+message+" in this arena." );
            return;
        }

        m_botAction.sendUnfilteredPrivateMessage( target, "*info" );
        m_requesters.put( target, name );
    }
    
    class Squad {
        String name;
        HashMap<Integer, Game> games;

        public Squad(String squad, Game game) {
            name = squad;
            games = new HashMap<Integer, Game>();
            games.put(game.getID(), game);
        }
        
        public HashMap<Integer, Game> getGames() {
            return games;
        }
        
        public String getName() {
            return name;
        }
        
        public void putGame(Game game) {
            games.put(game.getID(), game);
        }
        
        public void setState(int id, int state) {
            if (!games.containsKey((Integer) id))
                return;
            Game game = games.get((Integer) id);
            game.setState(state);
            games.put(id, game);
        }
        
        public boolean end(int id) {
            if (games.containsKey((Integer) id))
                games.remove((Integer) id);
            
            return games.isEmpty();
        }
        
    }

    class Game {
        int id;
        int state;
        String op;
        String type;
        String arena;
        LinkedList<String> alerted;

        //newgame matchID,squad,squad,type,state,arena
        public Game(int matchid, String o, String t, String a) {
            id = matchid;
            type = t;
            arena = a;
            op = o;
            state = 0;
            alerted = new LinkedList<String>();
        }
        
        public void setState(int s) {
            state = s;
        }
        
        public int getID() {
            return id;
        }
        
        public int getState() {
            return state;
        }
        
        public String type() {
            return type;
        }
        
        public String arena() {
            return arena;
        }
        
        public String op() {
            return op;
        }
        
        public boolean alerted(String name) {
            if (alerted.contains(name.toLowerCase()))
                return true;
            else {
                alerted.add(name.toLowerCase());
                return false;
            }
        }
        
    }
    
    
    
    
    
    
    
}
