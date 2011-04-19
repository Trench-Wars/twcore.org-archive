package twcore.bots.twdbot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.SQLResultEvent;
import twcore.core.game.Player;
import twcore.core.stats.DBPlayerData;
import twcore.core.util.Tools;

/**
 * Handles TWD administration tasks such as registration and deletion.
 * Prevents players from playing from any but registered IP/MID, unless
 * overridden by an Op.
 * 
 * Also handles and maintains MatchBot location and availability as well
 * as game information for potential alerts to entering players
 */
public class twdbot extends SubspaceBot {

    String m_arena;
    BotSettings m_botSettings;
    OperatorList m_opList;
    LinkedList<DBPlayerData> m_players;
    LinkedList<SquadOwner> m_squadowner;

    public HashMap<String, String> m_requesters;

    private int arenaChallCount;
    private String birthday;
    private String register = "";
    private HashMap<String, String> m_waitingAction;
    private String webdb = "website";
    private boolean manualSpawnOverride;
    private boolean shuttingDown;
    private boolean endgameAlert = false;
    private boolean killAlert = false;
    private boolean arenaChallAlert = true;
    private boolean otherAlerts = true;
    private boolean respawn = true;
    private static final String HUB = "TWCore-League";
    private static final String IPC = "MatchBot";
    private static final String BOT_NAME = "MatchBot";
    private static final String PUBBOT = "TW-Guard";
    private static final String TWBD = "6";
    private static final String TWDD = "8";
    private static final String TWFD = "13";
    private static final String TWJD = "15";
    private static final String TWSD = "19";

    // keeps track for alerter
    private HashMap<String, Squad> m_squads;
    private HashMap<Integer, Game> m_games;
    // bots -> arena name
    private HashMap<String, String> m_bots;
    // arena name -> info 
    private HashMap<String, Arena> m_arenas;
    // list of arenas waiting for bots (easier to grab than having to search thru a map's values)
    private Vector<String> m_spawner;
    // list of free bots
    private Vector<String> m_idlers;
    private HashMap<String, KillRequest> m_killer;
    private LinkedList<String> m_watches;
    
    TimerTask check, lock, messages;

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
        
        manualSpawnOverride = false;
        shuttingDown = false;

        m_waitingAction = new HashMap<String, String>();
        m_requesters = new HashMap<String, String>();

        arenaChallCount = 0;
        birthday = new SimpleDateFormat("HH:mm MM.dd.yy").format(Calendar.getInstance().getTime());
        m_arenas = new HashMap<String, Arena>();
        m_games = new HashMap<Integer, Game>();
        m_squads = new HashMap<String, Squad>();
        m_bots = new HashMap<String, String>();
        m_killer = new HashMap<String, KillRequest>();
        m_spawner = new Vector<String>();
        m_idlers = new Vector<String>();
        m_watches = new LinkedList<String>();
        requestEvents();
    }

    public void requestEvents() {
        EventRequester req = m_botAction.getEventRequester();
        req.request( EventRequester.MESSAGE );
        req.request( EventRequester.PLAYER_LEFT );
        req.request( EventRequester.PLAYER_ENTERED );
    }
    
    public void handleEvent(PlayerLeft event) {
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (name == null)
            return;
        
        if (m_opList.isBotExact(name)) {
            m_idlers.remove(name);
        }
    }
    
    public void handleEvent(PlayerEntered event) {
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (name == null)
            return;
        
        if (m_opList.isBotExact(name) && name.startsWith(BOT_NAME) && !m_idlers.contains(name)) {
            m_idlers.add(name);
        }
    }
    
    public void checkIN() {
        m_bots.clear();
        m_idlers.clear();
        m_arenas.clear();
        m_spawner.clear();
        
        m_botAction.ipcTransmit(IPC, "twdmatchbots:newcheckin");
        
        check = new TimerTask() {
            @Override
            public void run() {
                checkArenas();
            }
        };
        m_botAction.scheduleTask(check, 5000);
    }
    
    public void spawn(String arena) {
        if (manualSpawnOverride || shuttingDown)
            return;
        // spawn bot
        // add arena to the to-be-locked list

        arena = arena.toLowerCase();
        if (!m_spawner.contains(arena))
            m_spawner.add(arena);
        if (!m_idlers.isEmpty()) {
            String bot = m_idlers.remove(0);
            arena = m_spawner.remove(0);
            if (!m_arenas.containsKey(arena))
                m_arenas.put(arena, new Arena(arena));
            lockBot(bot, arena);
        } else {
            m_botAction.sendSmartPrivateMessage(HUB, "!spawn matchbot");
        }
    }
    
    public void lockBot(String bot, String arena) {
        if (m_bots.containsValue(arena)) {
            m_botAction.sendChatMessage("Anomaly detected: lock attempt on " + bot + " for the pre-existing arena " + arena + " prevented");
            return;
        }
        if (!m_arenas.containsKey(arena))
            m_arenas.put(arena, new Arena(arena));
        m_arenas.get(arena).setBot(bot);
        String div = arena.substring(0, 4);
        if (otherAlerts)
            m_botAction.sendChatMessage("Sending " + bot + " to " + arena + "...");
        if (div.equalsIgnoreCase("twbd"))
            m_botAction.sendSmartPrivateMessage(bot, "!lock " + TWBD + ":" + arena);
        else if (div.equalsIgnoreCase("twdd"))
            m_botAction.sendSmartPrivateMessage(bot, "!lock " + TWDD + ":" + arena);
        else if (div.equalsIgnoreCase("twjd"))
            m_botAction.sendSmartPrivateMessage(bot, "!lock " + TWJD + ":" + arena);
        else if (div.equalsIgnoreCase("twsd"))
            m_botAction.sendSmartPrivateMessage(bot, "!lock " + TWSD + ":" + arena);
        else if (div.equalsIgnoreCase("twfd"))
            m_botAction.sendSmartPrivateMessage(bot, "!lock " + TWFD + ":" + arena);
    }
    
    public void remove(String arena) {
        if (manualSpawnOverride)
            return;
        arena = arena.toLowerCase();
        if (m_arenas.containsKey(arena)) {
            Arena info = m_arenas.get(arena);
            if (info.isIPC())
                return;
            String bot = info.getBot();
            if (!m_killer.containsKey(bot) && m_bots.containsKey(bot) && killAlert)
                m_botAction.sendChatMessage("Sending kill request to " + bot + " in " + arena + "...");
            
            if (!m_killer.containsKey(bot)) {
                KillRequest die = new KillRequest(bot);
                m_killer.put(bot, die);
                m_botAction.scheduleTask(die, 32000);
            }
        }
    }
    
    public void stay(String arena) {
        arena = arena.toLowerCase();
        if (m_arenas.containsKey(arena)) {
            Arena info = m_arenas.get(arena);
            String bot = info.getBot();
            m_botAction.ipcTransmit(IPC, "twdmatchbot:" + bot + " stay");
            if (m_killer.containsKey(bot)) {
                if (m_killer.get(bot).cancel() && killAlert)
                    m_botAction.sendChatMessage("Scheduled kill request for " + bot + " has been cancelled.");
                m_killer.remove(bot);
            }
        } else {
            spawn(arena);
        }
    }
    
    public void checkArenas() {
        if (manualSpawnOverride || shuttingDown)
            return;
        checkDiv("twbd");
        checkDiv("twdd");
        checkDiv("twjd");
        checkDiv("twsd");
        checkDiv("twfd");
        if (otherAlerts)
            m_botAction.sendChatMessage("Checking TWD arenas...");
        
    }
    
    public void checkDiv(String div) {
        if (manualSpawnOverride || shuttingDown)
            return;
        div = div.toLowerCase();
        Arena arena;
        if (m_arenas.containsKey(div)) {
            arena = m_arenas.get(div);
            if (arena.isGame() || arena.isIPC()) {
                // continue on, need to make sure 1 arena is free
                if (m_arenas.containsKey(div + "2")) {
                    arena = m_arenas.get(div + "2");
                    if (arena.isGame() || arena.isIPC()) {
                        // make sure it stays and remove remaining
                        if(!m_bots.containsKey(arena.getBot()))
                            stay(div + "2");
                        // continue on, need to make sure 1 arena is free
                        if (m_arenas.containsKey(div + "3")) {
                            arena = m_arenas.get(div + "3");
                            if (arena.isGame() || arena.isIPC()) {
                                // make sure it stays and remove remaining
                                if(!m_bots.containsKey(arena.getBot()))
                                    stay(div + "3");
                                // continue on, need to make sure 1 arena is free
                                if (m_arenas.containsKey(div + "4")) {
                                    arena = m_arenas.get(div + "4");
                                    if (arena.isGame() || arena.isIPC()) {
                                        // make sure it stays and remove remaining
                                        if(!m_bots.containsKey(arena.getBot()))
                                            stay(div + "4");
                                        // continue on, need to make sure 1 arena is free
                                        if (m_arenas.containsKey(div + "5")) {
                                            arena = m_arenas.get(div + "5");
                                            // all previous arenas have games 
                                            // make sure this one isn't dying
                                            if(!m_bots.containsKey(arena.getBot()))
                                                stay(div + "5");
                                            
                                        } else {
                                            // all previous arenas have games so this needs a bot
                                            m_arenas.put(div + "5", new Arena(div + "5"));
                                            spawn(div + "5");
                                        }
                                    } else {
                                        // arena4 does not have a game                                        
                                        remove(div + "5");                                        
                                    }
                                } else {
                                    // arena4 missing, spawn it, remove rest
                                    m_arenas.put(div + "4", new Arena(div + "4"));
                                    spawn(div + "4");
                                    remove(div + "5");
                                }
                            } else {
                                // arena3 does not have a game
                                remove(div + "4"); 
                                remove(div + "5");     
                            }
                        } else {
                            // arena3 missing, spawn it, remove rest
                            m_arenas.put(div + "3", new Arena(div + "3"));
                            spawn(div + "3");
                            remove(div + "4");
                            remove(div + "5");
                        }
                    } else {
                        // arena2 does not have a game
                        remove(div + "3"); 
                        remove(div + "4"); 
                        remove(div + "5"); 
                    }
                } else {
                    // arena2 missing, spawn it, remove rest
                    m_arenas.put(div + "2", new Arena(div + "2"));
                    spawn(div + "2");
                    remove(div + "3");
                    remove(div + "4");
                    remove(div + "5");
                    
                }
            } else {
                // no game in here, kill remaining bots
                remove(div + "2");
                remove(div + "3");
                remove(div + "4");
                remove(div + "5");
            }
        } else {
            // this arena should never be without a bot
            if (m_bots.containsValue(div)) {
                // something must be wrong with it
                remove(div);
            }
            m_arenas.put(div, new Arena(div));
            spawn(div);
        }
    }
    
    public void startLock() {        
        while (!m_idlers.isEmpty() && !m_spawner.isEmpty()) {
            String arena = m_spawner.remove(0);
            String bot = m_idlers.remove(0);
            m_arenas.put(arena, new Arena(arena));
            lockBot(bot, arena);
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
                if (msg.startsWith("twdinfo:")) {
                    if (msg.startsWith("twdinfo:gamein30")) {
                        // arena,bot
                        String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                        if (args.length != 2)
                            return;
                        String arena = args[0];
                        String bot = args[1];
                        if (m_arenas.containsKey(arena)) {
                            Arena info = m_arenas.get(arena);
                            info.setGame(true);
                        } else {
                            Arena info = new Arena(arena);
                            info.setWaiting(false);
                            info.setBot(bot);
                            info.setGame(true);
                        }
                        if (!m_bots.containsKey(bot))
                            m_bots.put(bot, arena);
                        
                        checkDiv(arena.substring(0, 4));
                        
                    } else if (msg.startsWith("twdinfo:newgame")) {
                        //newgame matchID,squad,squad,type,state,arena,bot
                        String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                        String arena = args[4].toLowerCase();
                        String bot = args[5];
                        if (args.length != 6)
                            return;
                        int id;
                        try {
                            id = Integer.valueOf(args[0]);
                        } catch (NumberFormatException e) {
                            return;
                        }
                        Arena info;
                        if (m_arenas.containsKey(arena)) {
                            info = m_arenas.get(arena);
                        } else {
                            info = new Arena(arena);
                            info.setWaiting(false);
                            info.setBot(bot);
                        }
                        if (!m_bots.containsKey(bot))
                            m_bots.put(bot, arena);
                        
                        info.setGame(true);
                        info.resetIPC();

                        Game game = new Game(id, args[1], args[2], args[3], arena);
                        m_games.put(id, game);

                        Squad squad;
                        if (m_squads.containsKey(args[1].toLowerCase())) {
                            squad = m_squads.get(args[1].toLowerCase());
                            squad.addGame(id);
                        } else {
                            m_squads.put(args[1].toLowerCase(), new Squad(args[1], id));
                        }
                        if (m_squads.containsKey(args[2].toLowerCase())) {
                            squad = m_squads.get(args[2].toLowerCase());
                            squad.addGame(id);
                        } else {
                            m_squads.put(args[2].toLowerCase(), new Squad(args[2], id));
                        }
                        checkDiv(arena.substring(0, 4));
                        
                    } else if (msg.startsWith("twdinfo:gamestate")) {
                        String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                        if (args.length != 6 && args.length != 4)
                            return;
                        int id, state;
                        int s1 = -1;
                        int s2 = -1;
                        try {
                            id = Integer.valueOf(args[0]);
                            if (args.length == 6) {
                                state = Integer.valueOf(args[5]);
                                s1 = Integer.valueOf(args[3]);
                                s2 = Integer.valueOf(args[4]);
                            } else {
                                state = Integer.valueOf(args[3]);                                
                            }
                        } catch (NumberFormatException e) {
                            return;
                        }
                        //maybe should put a setGame, just in case
                        if (m_games.containsKey(id)) {
                            Game game = m_games.get(id);
                            if (s1 > -1 && s2 > -1) {
                                game.setScore1(s1);
                                game.setScore2(s2);
                            }
                            game.setState(state);
                            if (state == 0)
                                game.nextRound();
                            m_games.put(id, game);
                        } else if (!m_games.containsKey(id) && m_squads.containsKey(args[1].toLowerCase()) && m_squads.containsKey(args[2].toLowerCase())) {
                            Squad squad;
                            squad = m_squads.get(args[1].toLowerCase());
                            squad.endGame(id);
                            m_squads.put(args[1].toLowerCase(), squad);
                            squad = m_squads.get(args[2].toLowerCase());
                            squad.endGame(id);
                            m_squads.put(args[2].toLowerCase(), squad);
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
                        if (endgameAlert)
                            m_botAction.sendChatMessage("End game IPC received for match " + args[0] + " " + args[1] + " vs " + args[2] + " in " + args[3]);

                        String arena = args[3].toLowerCase();
                        m_arenas.get(arena).setGame(false);
                        m_arenas.get(arena).setIPC(false);

                        checkDiv(arena.substring(0, 4));

                        m_games.remove(id);
                        
                        if (m_squads.containsKey(args[1].toLowerCase()) && m_squads.containsKey(args[2].toLowerCase())) {
                            Squad squad;
                            squad = m_squads.get(args[1].toLowerCase());
                            if (squad.endGame(id))
                                m_squads.remove(args[1].toLowerCase());
                            squad = m_squads.get(args[2].toLowerCase());
                            if (squad.endGame(id))
                                m_squads.remove(args[2].toLowerCase());
                        }
                    }
                } else if (msg.startsWith("twdmatchbot:")) {
                    if (msg.startsWith("twdmatchbot:checkingin:")) {
                        // twdmatchbot:checkingin:bot:arena
                        // twdmatchbot:checkingin:bot:arena:game
                        // twdmatchbot:checkingin:bot:arena:matchID:squad:squad:type
                        m_botAction.sendSmartPrivateMessage("WingZero", msg);
                        String[] args = msg.split(":");
                        String arena = args[3].toLowerCase();
                        if (args.length == 4) {
                            if (args[3].equalsIgnoreCase("twd")) {
                                if (!m_idlers.contains(args[2]))
                                    m_idlers.add(args[2]);
                            } else {
                                if (!m_arenas.containsKey(arena) && !m_bots.containsKey(args[2]) && !m_bots.containsValue(arena)) {
                                    Arena info = new Arena(arena);
                                    info.setBot(args[2]);
                                    info.setWaiting(false);
                                    m_bots.put(args[2], arena);
                                    m_arenas.put(arena, info);
                                } else {
                                    if (m_arenas.containsKey(arena)) {
                                        String bot = m_arenas.get(arena).getBot();
                                        if (!bot.equals(args[2])) {
                                            m_bots.remove(args[2]);
                                            m_botAction.ipcTransmit(IPC, "twdmatchbot:" + args[2] + " die");
                                        }
                                    } else {
                                        Vector<String> list = new Vector<String>();
                                        for (String b : m_bots.keySet()) {
                                            if (arena.equalsIgnoreCase(m_bots.get(b))) {
                                                if (!list.contains(b))
                                                    list.add(b);
                                            }                                                
                                        }
                                        while (!list.isEmpty()) {
                                            m_botAction.ipcTransmit(IPC, "twdmatchbot:" + list.remove(0) + " die");
                                        }
                                    }
                                    m_botAction.sendChatMessage("Extra bot reported in " + arena);
                                }
                            }
                        } else if (args.length == 5) {
                            Arena info = new Arena(arena);
                            info.setWaiting(false);
                            info.setBot(args[2]);
                            info.setGame(true);
                            m_arenas.put(arena, info);
                            m_bots.put(args[2], arena);
                        } else if (args.length == 8) {
                            // LOAD ALL GAME INFORMATION AS IF IT WERE A NEWGAME IPC
                            int id;
                            try {
                                id = Integer.valueOf(args[4]);
                            } catch (NumberFormatException e) {
                                return;
                            }
                            Arena info = new Arena(arena);
                            info.setWaiting(false);
                            info.setBot(args[2]);
                            info.setGame(true);
                            m_arenas.put(arena, info);
                            m_bots.put(args[2], arena);

                            m_games.put(id, new Game(id, args[5], args[6], args[7], arena));

                            Squad squad;
                            if (m_squads.containsKey(args[5].toLowerCase())) {
                                squad = m_squads.get(args[5].toLowerCase());
                                squad.addGame(id);
                            } else {
                                m_squads.put(args[5].toLowerCase(), new Squad(args[5], id));
                            }
                            if (m_squads.containsKey(args[6].toLowerCase())) {
                                squad = m_squads.get(args[6].toLowerCase());
                                squad.addGame(id);
                            } else {
                                m_squads.put(args[6].toLowerCase(), new Squad(args[6], id));
                            }
                        }
                    } else if (msg.startsWith("twdmatchbot:spawned")) {
                        if (manualSpawnOverride)
                            return;
                        final String bot = msg.substring(msg.indexOf(" ") + 1);
                        if (!m_spawner.isEmpty()) {
                            final String arena = m_spawner.remove(0);
                            TimerTask lock = new TimerTask() {
                                @Override
                                public void run() {
                                    lockBot(bot, arena);
                                }
                            };
                            m_botAction.scheduleTask(lock, 2000);
                        } else if (!m_idlers.contains(bot)) {
                            m_idlers.add(bot);
                        }
                    } else if (msg.startsWith("twdmatchbot:locked ")) {
                        if (manualSpawnOverride)
                            return;
                        // bot,arena
                        String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                        String arena = args[1].toLowerCase();
                        if (!m_arenas.containsKey(arena)) {
                            m_botAction.ipcTransmit(IPC, "twdmatchbot:" + args[0] + " die");
                            return;
                        } else {
                            m_bots.put(args[0], arena);
                            Arena info = m_arenas.get(arena);
                            if (info.isWaiting()) {
                                info.setBot(args[0]);
                                info.setWaiting(false);
                                Vector<String> msgs = info.getIPC();
                                while (!msgs.isEmpty()) {
                                    String ipc = msgs.remove(0);
                                    m_botAction.sendSmartPrivateMessage(ipc.substring(ipc.indexOf(" ") + 1, ipc.indexOf(",")), "Your challenge was sent as requested.");
                                    m_botAction.ipcTransmit(IPC, ipc);
                                    info.setIPC(true);
                                }
                            } else {
                                m_botAction.ipcTransmit(IPC, "twdmatchbot:" + args[0] + " die");
                                return;                                
                            }
                        }
                    } else if (msg.startsWith("twdmatchbot:unlocked ")) {
                        if (manualSpawnOverride)
                            return;
                        String arena = msg.substring(msg.indexOf(" ") + 1, msg.indexOf(",")).toLowerCase();
                        String bot = msg.substring(msg.indexOf(",") + 1);
                        if (!m_arenas.containsKey(arena))
                            return;
                        m_arenas.remove(arena);
                        m_botAction.sendChatMessage("Unexpected unlock occured from " + bot + " in " + arena + ". Responding appropriately...");
                        checkDiv(arena.substring(0, 4));                        
                    } else if (msg.startsWith("twdmatchbot:dying ")) {
                        // arena,bot
                        String[] args  = msg.substring(msg.indexOf(" ") + 1).split(",");
                        if (args.length == 2) {
                            String arena = args[0].toLowerCase();
                            String bot = args[1];
                            if (m_killer.containsKey(bot))
                                m_killer.remove(bot);
                            if (m_bots.containsKey(bot)) {
                                m_bots.remove(bot);
                                if (otherAlerts)
                                    m_botAction.sendChatMessage(bot + " reports it will die when " + arena + " is over...");
                            }
                        }
                    } else if (msg.startsWith("twdmatchbot:shuttingdown ")) {
                        // arena,bot
                        String[] args  = msg.substring(msg.indexOf(" ") + 1).split(",");
                        if (args.length == 2) {
                            String bot = args[1];
                            String arena = args[0].toLowerCase();
                            if (m_arenas.containsKey(arena)) {
                                Arena info = m_arenas.get(arena);
                                if (bot.equalsIgnoreCase(info.getBot()))
                                    m_arenas.remove(arena);
                            }
                            if (m_bots.containsKey(bot))
                                m_bots.remove(bot);
                            if (m_killer.containsKey(bot))
                                m_killer.remove(bot);
                        }
                    } else if (msg.startsWith("twdmatchbot:staying ")) {
                        // arena,bot
                        String[] args  = msg.substring(msg.indexOf(" ") + 1).split(",");
                        if (args.length == 2) {
                            String arena = args[0].toLowerCase();
                            String bot = args[1];
                            if (!m_bots.containsKey(bot)) {
                                m_bots.put(bot, arena);
                                if (otherAlerts)
                                    m_botAction.sendChatMessage(bot + " has been prevented from dying in " + arena);
                            }
                        }
                    } else if (msg.startsWith("twdmatchbot:manlock ")) {
                        // bot,name,msg
                        String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                        if (args.length == 3) {
                            m_botAction.sendChatMessage("" + args[1] + " attempted to lock " + args[0] + " with " + args[2]);
                            if (!manualSpawnOverride && (args[2].contains("6") || args[2].contains("8") || args[2].contains("13") || args[2].contains("15") || args[2].contains("19"))) {
                                m_botAction.ipcTransmit(IPC, "twdmatchbot:" + args[0] + ":denylock " + args[1]);
                            } else {
                                m_botAction.sendPrivateMessage(args[0], args[2]);
                            }
                        }
                    } else if (msg.startsWith("twdmatchbot:manunlock ")) {
                        // bot,name,msg,arena
                        String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                        if (args.length == 4) {
                            m_botAction.sendChatMessage("" + args[1] + " attempted to unlock " + args[0] + " with " + args[2] + " in " + args[3]);
                            if (!manualSpawnOverride && (args[3].toLowerCase().contains("twbd") || args[3].toLowerCase().contains("twdd") || args[3].toLowerCase().contains("twfd") || args[3].toLowerCase().contains("twjd") || args[3].toLowerCase().contains("twsd"))) {
                                m_botAction.ipcTransmit(IPC, "twdmatchbot:" + args[0] + ":denyunlock " + args[1]);
                            } else {
                                m_botAction.ipcTransmit(IPC, "twdmatchbot:" + args[0] + ":allowunlock " + args[1] + "," + args[2]);
                            }
                        }
                    }
                } else if (msg.startsWith("twd:")) {
                    if (msg.startsWith("twd:arena_request ")) {
                        String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                        // (arena,squad_ch,squad_op,name,players,my_arena,my_botname)
                        if (args.length != 7)
                            return;
                        String arena = args[0].toLowerCase();
                        String player = args[3];
                        if (m_watches.contains(player.toLowerCase())) {
                            m_botAction.sendChatMessage(1, "" + player + " arena challenged " + args[2] + " to " + args[4] + "s in " + arena);
                            m_botAction.sendChatMessage(2, "" + player + " arena challenged " + args[2] + " to " + args[4] + "s in " + arena);
                        }
                        // name,squad_ch,squad_op,players
                        String ipc = "twd:" + arena + ":challenge " + player + "," + args[2] + "," + args[4];
                        arenaChallCount++;
                        if (arenaChallAlert)
                            m_botAction.sendChatMessage("Arena challenge request made by " + player + " for " + arena + " against " + args[2]);
                        if (m_arenas.containsKey(arena)) {
                            Arena info = m_arenas.get(arena);
                            if (info.isGame()) {
                                m_botAction.sendSmartPrivateMessage(player, "Challenge denied: " + arena + " is currently being played in");
                            } else if (info.isWaiting()) { 
                                info.addIPC(ipc);
                                info.setIPC(true);
                            } else {
                                m_botAction.ipcTransmit(IPC, ipc);
                            }
                        } else {
                            // create a new bot and store the arena with the ipc message/challenge
                            Arena info = new Arena(arena);
                            info.setIPC(true);
                            info.addIPC(ipc);
                            m_spawner.add(arena);
                            m_arenas.put(arena, info);
                            spawn(arena);
                            m_botAction.sendSmartPrivateMessage(player, "A bot is being spawned for " + arena + ". If it does not spawn or send your challenge please use ?help");
                        }
                    } else if (msg.startsWith("twd:expiredchallenge")) {
                        String[] args  = msg.substring(msg.indexOf(" ") + 1).split(",");
                        if (args.length != 2)
                            return;
                        String arena = args[0].toLowerCase();
                        m_arenas.get(arena).setIPC(false);
                        checkDiv(arena.substring(0, 4));
                    } else if (msg.startsWith("twd:challenge ")) {
                        // name,squad,players,arena
                        String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                        if (m_watches.contains(args[0].toLowerCase())) {
                            m_botAction.sendChatMessage(3, "" + args[0] + " challenged " + args[1] + " to " + args[2] + "s in " + args[3]);
                            m_botAction.sendChatMessage(2, "" + args[0] + " challenged " + args[1] + " to " + args[2] + "s in " + args[3]);
                        }
                    } else if (msg.startsWith("twd:topchallenge ")) {
                        // name,players,arena
                        String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                        if (m_watches.contains(args[0].toLowerCase())) {
                            m_botAction.sendChatMessage(3, "" + args[0] + " challenged top teams to " + args[1] + "s in " + args[2]);
                            m_botAction.sendChatMessage(2, "" + args[0] + " challenged top teams to " + args[1] + "s in " + args[2]);
                        }
                    } else if (msg.startsWith("twd:allchallenge ")) {
                        // name,players,arena
                        String[] args = msg.substring(msg.indexOf(" ") + 1).split(",");
                        if (m_watches.contains(args[0].toLowerCase())) {
                            m_botAction.sendChatMessage(3, "" + args[0] + " challenged all to " + args[1] + "s in " + args[2]);
                            m_botAction.sendChatMessage(2, "" + args[0] + " challenged all to " + args[1] + "s in " + args[2]);
                        }
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
                if (!shuttingDown && m_bots.containsKey(bot)) {
                    m_botAction.sendChatMessage("Unexpected disconnect detected - calling for MatchBot checkin...");
                    checkIN();
                }
            } else if (respawn && message.startsWith("Bot of type matchbot failed to log in.")) {
                m_botAction.sendSmartPrivateMessage(HUB, "!spawn matchbot");
            }
        }

        if( event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE || event.getMessageType() == Message.CHAT_MESSAGE ){
            String name = m_botAction.getPlayerName( event.getPlayerID() );
            if (name == null)
                name = event.getMessager();

            if( m_opList.isSysop( name ) || isTWDOp(name) || m_opList.isOwner(name)) {
                if (message.startsWith("!manualspawn")) {
                    commandManualSpawn(name);
                    return;
                } else if (message.startsWith("!forcecheck")) {
                    if (manualSpawnOverride) {
                        m_botAction.sendSmartPrivateMessage(name, "Manual spawn override in effect.");
                        return;
                    }
                    m_botAction.sendSmartPrivateMessage(name, "Initiating a check of all TWD arenas...");
                    checkArenas();
                    return;
                } else if (message.startsWith("!fullcheck")) {
                    if (manualSpawnOverride) {
                        m_botAction.sendSmartPrivateMessage(name, "Manual spawn override in effect.");
                        return;
                    }
                    m_botAction.sendSmartPrivateMessage(name, "Requesting a checkin from all TWD bots...");
                    checkIN();
                    return;
                } else if (message.startsWith("!shutdowntwd")) {
                    command_shutdown(name);
                    return;
                } else if (message.startsWith("!endgamealerts")) {
                    command_endgameAlerts(name);
                    return;
                } else if (message.startsWith("!killalerts")) {
                    command_killAlerts(name);
                    return;
                } else if (message.startsWith("!respawn")) {
                    command_respawn(name);
                    return;
                } else if (message.startsWith("!otheralerts")) {
                    command_other(name);
                    return;
                } else if (message.startsWith("!challalerts")) {
                    command_challs(name);
                    return;
                } else if (message.startsWith("!ban ")) {
                    command_challengeBan(name, message.substring(message.indexOf(" ") + 1));
                    return;
                } else if (event.getMessageType() != Message.CHAT_MESSAGE) {
                    if (message.startsWith("!watch ")) {
                        String player = message.substring(message.indexOf(" ") + 1);
                        if (m_watches.contains(player.toLowerCase())) {
                            m_watches.remove(player.toLowerCase());
                            m_botAction.sendChatMessage(3, "" + player + " has been removed from challenge watch by " + name);
                            m_botAction.sendChatMessage(2, "" + player + " has been removed from challenge watch by " + name);
                        } else {
                            m_watches.add(player.toLowerCase());
                            m_botAction.sendChatMessage(3, "" + player + " has been added to challenge watch by " + name);
                            m_botAction.sendChatMessage(2, "" + player + " has been added to challenge watch by " + name);                            
                        }
                    }
                }
                
                
            }
        }

        if( event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ){
            String name = m_botAction.getPlayerName( event.getPlayerID() );
            if (name == null)
                name = event.getMessager();
            
            if( m_opList.isER( name )) 
                isStaff = true; 
            else 
                isStaff= false;

            if (message.startsWith("!games")) {
                command_games(name);
                return;
            } else if (message.startsWith("!acrs")) {
                command_acrs(name);
                return;
            }

            if (messager.startsWith(PUBBOT)) {
                String msg = event.getMessage();
                if (msg.startsWith("twdplayer") && !m_squads.isEmpty()) {
                    String[] args = msg.substring(msg.indexOf(" ") + 1).split(":");
                    if (args.length == 2 && m_squads.containsKey(args[1].toLowerCase())) {
                        Squad squad = m_squads.get(args[1].toLowerCase());
                        Vector<Integer> games = squad.getGames();
                        for (Integer id : games) {
                            if (m_games.containsKey(id)) {
                                m_games.get(id).alert(args[0], args[1]);
                            }
                        }
                    }
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
    
    public void command_shutdown(String name) {
        m_botAction.sendSmartPrivateMessage(name, "Initiating shutdown of all matchbots.");
        m_botAction.sendChatMessage("Total MatchBot shutdown requested by " + name);
        shuttingDown = true;
        m_botAction.cancelTask(check);
        m_botAction.cancelTask(lock);
        m_botAction.ipcTransmit(IPC, "all twdbots die");
        m_arenas.clear();
        m_bots.clear();
        m_idlers.clear();
        m_spawner.clear();
        return;
    }
    
    public void command_games(String name) {
        if (!m_games.isEmpty()) {
            for (Game game : m_games.values())
                m_botAction.sendSmartPrivateMessage(name, game.toString());
        } else
            m_botAction.sendSmartPrivateMessage(name, "No games are being played at the moment.");
    }
    
    public void command_challenge(String name, String msg) {
        if (!msg.contains(":")) {
            m_botAction.sendSmartPrivateMessage(name, "Please use the following syntax: !ch Squad:Players:Arena");
            return;
        }
        
        String[] args = msg.substring(msg.indexOf(" ") + 1).split(":");
        
    }
    
    public void command_respawn(String name) {
        if (respawn) {
            respawn = false;
            m_botAction.sendChatMessage("Respawn attempts for failed bot logins have been DISABLED by " + name);
        } else {
            respawn = true;
            m_botAction.sendChatMessage("Respawn attempts for failed bot logins have been ENABLED by " + name);
        }
    }
    
    public void command_other(String name) {
        if (otherAlerts) {
            otherAlerts = false;
            m_botAction.sendChatMessage("Other alerts have been DISABLED by " + name);
        } else {
            otherAlerts = true;
            m_botAction.sendChatMessage("Other alerts have been ENABLED by " + name);
        }
    }
    
    public void command_challs(String name) {
        if (arenaChallAlert) {
            arenaChallAlert = false;
            m_botAction.sendChatMessage("Arena challenge alerts have been DISABLED by " + name);
        } else {
            arenaChallAlert = true;
            m_botAction.sendChatMessage("Arena challenge alerts have been ENABLED by " + name);
        }
    }
    
    public void command_endgameAlerts(String name) {
        if (endgameAlert) {
            endgameAlert = false;
            m_botAction.sendChatMessage("End game alerts have been DISABLED by " + name);
        } else {
            endgameAlert = true;
            m_botAction.sendChatMessage("End game alerts have been ENABLED by " + name);            
        }
    }
    
    public void command_killAlerts(String name) {
        if (killAlert) {
            killAlert = false;
            m_botAction.sendChatMessage("Kill request alerts have been DISABLED by " + name);
        } else {
            killAlert = true;
            m_botAction.sendChatMessage("Kill request alerts have been ENABLED by " + name);            
        }
    }
    
    public void command_challengeBan(String name, String msg) {
        DBPlayerData dbp = new DBPlayerData(m_botAction, webdb, msg, false);
        if (dbp.checkPlayerExists()) {
            try {
                ResultSet rs = m_botAction.SQLQuery(webdb, "SELECT * FROM tblChallengeBan WHERE fnUserID = " + dbp.getUserID() + " AND fnActive = 1");
                if (rs.next()) {
                    m_botAction.sendSmartPrivateMessage(name, "This ban already exists.");
                    return;
                }
                m_botAction.SQLClose(rs);
                m_botAction.SQLQueryAndClose(webdb, "INSERT INTO tblChallengeBan (fnUserID) VALUES(" + dbp.getUserID() + ")");
                m_botAction.sendChatMessage(1, "" + dbp.getUserName() + " has been challenge banned by " + name);
                m_botAction.sendChatMessage(2, "" + dbp.getUserName() + " has been challenge banned by " + name);
            } catch (SQLException e) {
                Tools.printStackTrace(e);
            }
        }
    }
    
    public void commandManualSpawn(String name) {
        if (!manualSpawnOverride) {
            manualSpawnOverride = true;
            m_arenas.clear();
            m_bots.clear();
            m_idlers.clear();
            m_spawner.clear();
            m_botAction.cancelTask(check);
            m_botAction.cancelTask(lock);
            m_botAction.sendSmartPrivateMessage(name, "Manual spawn override has been ENABLED.");
            m_botAction.sendChatMessage("Manual spawn override ENABLED by " + name);
        } else {
            manualSpawnOverride = false;
            m_arenas.clear();
            m_bots.clear();
            m_idlers.clear();
            m_spawner.clear();
            checkIN();
            m_botAction.sendChatMessage("Manual spawn override DISABLED by " + name + ". MatchBot spawn control restarting.");
            m_botAction.sendSmartPrivateMessage(name, "Manual spawn override has been DISABLED. Restarting the bot spawn control system.");
        }
    }
    
    public void command_acrs(String name) {
        m_botAction.sendSmartPrivateMessage(name, arenaChallCount + " arena challenge requests since " + birthday);
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
                        "                          and !registered",
                        "!games                  - This command will give you a list of the current matches",
                        "                          Note: It will work from any arena!",
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
        m_botAction.sendUnfilteredPublicMessage("?chat=robodev,twdstaff,executive lounge");
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
                "!go <arena>             - moves the bot",
                "--------- TWD BOT MANAGER -------------------------------------------------------------",
                "!manualspawn            - toggles manual spawning (in case errors occur in placement)",
                "                          when toggled back it resets and starts spawning/placing bots",
                "!respawn                - turns on/off TWDBot's respawn attempt when a bot fails to login",
                "!endgamealerts          - turns on/off the end game alerts sent to bot chat",
                "!killalerts             - turns on/off the kill request alerts sent to bot chat",
                "!otheralerts            - turns on/off all other alerts sent to bot chat",
                "!forcecheck             - forces the bot to re-evaluate bot placement",
                "!fullcheck              - when forcecheck fails, this gives the bot more game info",
                "!shutdowntwd            - kills all twd matchbots when they become idle (no undo)"
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
            m_botAction.SQLBackgroundQuery(webdb, "twdbot", "UPDATE tblChallengeBan SET fnActive = 0 WHERE fnActive = 1 AND fdDateCreated < DATE_SUB(NOW(), INTERVAL 1 DAY)");
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
    
    class Arena {
        String name;
        String bot;
        boolean game;
        boolean waiting;
        boolean ipc;
        Vector<String> challs;

        public Arena(String n) {
            name = n;
            bot = "";
            game = false;
            waiting = true;
            ipc = false;
            challs = new Vector<String>();
        }
        
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
        
        public boolean isWaiting() {
            return waiting;
        }

        public void setWaiting(boolean waiting) {
            this.waiting = waiting;
        }

        public String getBot() {
            return bot;
        }

        public void setBot(String bot) {
            this.bot = bot;
        }

        public boolean isGame() {
            return game;
        }

        public void setGame(boolean game) {
            this.game = game;
        }
        
        public void setIPC(boolean c) {
            ipc = c;
        }
        
        public void addIPC(String ipc) {
            challs.add(ipc);
        }
        
        public Vector<String> getIPC() {
            return challs;
        }
        
        public boolean isIPC() {
            return ipc;
        }
        
        public void resetIPC() {
            challs.clear();
        }
    }
    
    class Squad {
        String name;
        Vector<Integer> games;

        public Squad(String squad, Integer id) {
            name = squad;
            games = new Vector<Integer>();
            if (!games.isEmpty() || !games.contains(id))
                games.add(id);
        }
        
        public Vector<Integer> getGames() {
            return games;
        }
        
        public String getName() {
            return name;
        }
        
        public void addGame(Integer id) {
            if (!games.isEmpty() || !games.contains(id))
                games.add(id);
        }        
        
        public boolean endGame(Integer id) {
            games.remove((Integer) id);
            return games.isEmpty();
        }
    }

    class Game {
        int id;
        int state;
        int round;
        int team1, team2;
        String op;
        String squad1;
        String squad2;
        String type;
        String arena;
        LinkedList<String> alerted;

        //newgame matchID,squad,squad,type,arena
        public Game(int matchid, String s1, String s2, String t, String a) {
            id = matchid;
            type = t;
            arena = a;
            squad1 = s1;
            squad2 = s2;
            state = 0;
            alerted = new LinkedList<String>();
            round = 1;
            team1 = 0;
            team2 = 0;
        }
        
        public void alert(String name, String squad) {
            if (!alerted.isEmpty() && alerted.contains(name.toLowerCase()))
                    return;
            String nme;
            if (squad.equalsIgnoreCase(squad1))
                nme = squad2;
            else
                nme = squad1;

            String stateS;
            if (state == 0)
                stateS = "preparing";
            else
                stateS = "playing";
            
            m_botAction.sendSmartPrivateMessage(name, "Your squad is " + stateS + " a " + type + " match against " + nme + " in ?go " + arena);
            alerted.add(name.toLowerCase());
        }
        
        public void nextRound() {
            round++;
        }
        
        public void setScore1(int s) {
            team1 = s;
        }
        
        public void setScore2(int s) {
            team2 = s;
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
        
        public String[] getSquads() {
            return new String[] { squad1, squad2 };
        }
        
        public boolean alerted(String name) {
            if (alerted.contains(name.toLowerCase()))
                return true;
            else
                return false;
        }
        
        public String toString() {

            String stateS;
            if (state == 0)
                stateS = "starting";
            else
                stateS = "playing";
            
            String result = "";
            if (type.equals("TWD Basing"))
                result += type + "(" + arena + "): " + squad1 + " vs " + squad2 + " (" + stateS + ")";
            else
                result += type + "(" + arena + "): " + squad1 + " vs " + squad2 + " (" + team1 + "-" + team2 + " " + stateS + " round " + round + ")";                
            
            return result;
        }
        
    }
    
    class KillRequest extends TimerTask {
        String bot;
        
        public KillRequest(String name) {
            bot = name;
        }
        
        @Override
        public void run() {
            m_botAction.ipcTransmit(IPC, "twdmatchbot:" + bot + " die");
        }
        
    }
    
    
    
    
    
    
    
}
