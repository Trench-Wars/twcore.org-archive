package twcore.bots.twdhub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TimerTask;
import java.util.Vector;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.util.ipc.EventType;
import twcore.core.util.ipc.IPCChallenge;
import twcore.core.util.ipc.IPCCommand;
import twcore.core.util.ipc.IPCTWD;
import twcore.core.util.ipc.Command;

/**
 *
 * @author WingZero
 */
public class twdhub extends SubspaceBot {

    private static final String HUB = "TWCore-League";
    private static final String IPC = "MatchBot";
    private static final String BOT_NAME = "MatchBot";
    private static final String PUBBOT = "TW-Guard";
    private static final String TWBD = "6";
    private static final String TWDD = "8";
    private static final String TWFD = "13";
    private static final String TWJD = "15";
    private static final String TWSD = "19";
    enum ArenaStatus { WAITING, READY, DYING };

    BotAction ba;
    OperatorList oplist;
    BotSettings rules;
    
    VectorSet<String> needsBot;
    VectorSet<String> freeBots;
    VectorSet<String> sentSpawn;
    HashSet<String> alerts;
    HashMap<String, Arena> arenas;
    HashMap<String, Arena> bots;
    HashMap<String, Squad> squads;
    boolean shutdown;
    boolean startup;
    boolean DEBUG;
    String debugger;
    
    public twdhub(BotAction botAction) {
        super(botAction);
        ba = botAction;
        oplist = ba.getOperatorList();
        rules = ba.getBotSettings();
        startup = true;
        requestEvents();
        sentSpawn = new VectorSet<String>();
        needsBot = new VectorSet<String>();
        freeBots = new VectorSet<String>();
        arenas = new HashMap<String, Arena>();
        bots = new HashMap<String, Arena>();
        squads = new HashMap<String, Squad>();
        alerts = new HashSet<String>();
        shutdown = false;
        DEBUG = false;
        debugger = "";
    }
    
    public void handleEvent(LoggedOn event) {
        ba.ipcSubscribe(IPC);
        ba.sendUnfilteredPublicMessage("?chat=robodev,executive lounge,twdstaff");
        ba.joinArena(rules.getString("Arena"));
    }
    
    public void handleEvent(ArenaJoined event) {
        checkIn();
    }
    
    public void handleEvent(PlayerEntered event) {
        String name = event.getPlayerName();
        if (name == null)
            name = ba.getPlayerName(event.getPlayerID());
        if (oplist.isBotExact(name) && name.startsWith(BOT_NAME)) {
            freeBots.add(name);
            TimerTask lock = new TimerTask() {
                public void run() {
                    if (!needsBot.isEmpty())
                        lockBots();
                }
            };
            ba.scheduleTask(lock, 2000);
        }
    }
    
    public void handleEvent(PlayerLeft event) {
        String name = ba.getPlayerName(event.getPlayerID());
        if (name != null && oplist.isBotExact(name) && name.startsWith(BOT_NAME))
            freeBots.remove(name);
    }
    
    public void handleEvent(Message event) {
        int type = event.getMessageType();
        String msg = event.getMessage();
        String name = ba.getPlayerName(event.getPlayerID());
        if (name == null)
            name = event.getMessager();
        if (name == null) return;
        
        if (type == Message.CHAT_MESSAGE) {
            if (msg.contains("(matchbot)") && msg.contains("disconnected")) {
                String bot = msg.substring(0, msg.indexOf("("));
                botDisconnected(bot);
            }
        }
        
        if (type == Message.PRIVATE_MESSAGE || type == Message.REMOTE_PRIVATE_MESSAGE) {
            if (name.startsWith(PUBBOT) && msg.startsWith("twdplayer") && !squads.isEmpty()) {
                String[] args = msg.substring(msg.indexOf(" ") + 1).split(":");
                if (args.length == 2 && squads.containsKey(args[1].toLowerCase())) {
                    Squad squad = squads.get(args[1].toLowerCase());
                    Vector<String> games = squad.getGames();
                    for (String arena : games) {
                        if (arenas.containsKey(arena) && arenas.get(arena).game != null)
                            arenas.get(arena).game.alert(args[0], args[1]);
                    }
                }
                return;
            }

            String cmd = low(msg);
            if (cmd.equals("!list"))
                cmd_list(name);
            else if (cmd.equals("!games"))
                cmd_games(name);
            else if (cmd.equals("!alert"))
                cmd_alert(name);
            else if (cmd.equals("!help"))
                cmd_help(name);
            
            if (oplist.isModerator(name)) {
                if (cmd.equals("!die"))
                    cmd_die(name);
                else if (cmd.equals("!sdtwd"))
                    cmd_shutdown(name);
                else if (cmd.equals("!check"))
                    checkArenas();
                else if (cmd.equals("!debug"))
                    cmd_debug(name);
                else if (cmd.equals("!reset"))
                    cmd_reset(name);
            }
        }
    }
    
    public void handleEvent(InterProcessEvent event) {
        if (!event.getChannel().equals(IPC) || event.getSenderName().equals(ba.getBotName())) return;
        if (event.getObject() instanceof IPCTWD) {
            IPCTWD ipc = (IPCTWD) event.getObject();
            if (ipc.getType() == EventType.NEW) {
                Game game = new Game(-1, ipc.getSquad1(), ipc.getSquad2(), ipc.getArena());
                if (arenas.containsKey(ipc.getArena()))
                    arenas.get(ipc.getArena()).game = game;
                checkDiv(ipc.getArena().substring(0, 4));
            } else if (ipc.getType() == EventType.CHECKIN) {
                if (!isTWD(ipc.getArena())) return;
                if (ipc.getArena().equalsIgnoreCase("twd"))
                    freeBots.add(ipc.getBot());
                else if (arenas.containsKey(ipc.getArena())) {
                    needsBot.remove(ipc.getArena());
                    Arena arena = arenas.get(ipc.getArena());
                    if (arena.status == ArenaStatus.READY && arena.bot != null) {
                        ba.sendChatMessage("Found multipe bots in arena: " + ipc.getArena());
                        botRemove(ipc.getBot());
                        return;
                    }
                    arena.bot = ipc.getBot();
                    bots.put(arena.bot, arena);
                    arena.status = ArenaStatus.READY;
                    freeBots.remove(arena.bot);
                    if (ipc.getID() != 0) {
                        Game game = new Game(ipc.getID(), ipc.getSquad1(), ipc.getSquad2(), ipc.getArena());
                        arenas.get(ipc.getArena()).game = game;
                        game.setRound(1);
                        game.setState(1);
                    }
                } else {
                    Arena arena = new Arena(ipc.getArena());
                    arena.bot = ipc.getBot();
                    bots.put(arena.bot, arena);
                    arena.status = ArenaStatus.READY;
                    arenas.put(ipc.getArena(), arena);
                    if (ipc.getID() != 0) {
                        Game game = new Game(ipc.getID(), ipc.getSquad1(), ipc.getSquad2(), ipc.getArena());
                        arenas.get(ipc.getArena()).game = game;
                        game.setRound(1);
                        game.setState(1);
                    }
                }
                checkDiv(ipc.getArena().substring(0, 4));
            } else if (ipc.getType() == EventType.STARTING) {
                if (arenas.containsKey(ipc.getArena()) && arenas.get(ipc.getArena()).game != null) { 
                    arenas.get(ipc.getArena()).expire();
                    Game game = arenas.get(ipc.getArena()).game;
                    game.setID(ipc.getID());
                    game.setRound(1);
                    game.setState(0);
                }
            } else if (ipc.getType() == EventType.STATE) {
                if (arenas.containsKey(ipc.getArena()) && arenas.get(ipc.getArena()).game != null) { 
                    Game game = arenas.get(ipc.getArena()).game;
                    game.next();
                    game.score1 = ipc.getScore1();
                    game.score2 = ipc.getScore2();
                }
            } else if (ipc.getType() == EventType.END) {
                if (arenas.containsKey(ipc.getArena())) {
                    Arena arena = arenas.get(ipc.getArena());
                    Squad squad = squads.get(low(arena.game.squad1.name));
                    squad.endGame(arena.name);
                    squad = squads.get(low(arena.game.squad2.name));
                    squad.endGame(arena.name);
                    arena.game = null;
                }
                checkDiv(ipc.getArena().substring(0, 4));
            }
        } else if (!shutdown && event.getObject() instanceof IPCChallenge) {
            IPCChallenge ipc = (IPCChallenge) event.getObject();
            if (!isTWD(ipc.getArena()) || !ipc.getRecipient().equals(ba.getBotName())) return;
            String arenaName = ipc.getArena();
            String name = ipc.getName();
            if (arenas.containsKey(arenaName)) {
                Arena arena = arenas.get(arenaName);
                if (arena.game != null) {
                    ba.sendSmartPrivateMessage(name, "A game is already being played in " + arenaName + ".");
                } else {
                    if (arena.status == ArenaStatus.READY) { 
                        ipc.setBot(arena.bot);
                        debug("Transmitting challenge IPC for " + ipc.getName());
                        ba.ipcTransmit(IPC, ipc);
                        arena.ipcFlag();
                    } else
                        arena.add(ipc);
                }
            } else {
                Arena arena = new Arena(ipc.getArena());
                arena.add(ipc);
                botSpawn(ipc.getArena());
            }
        } else if (event.getObject() instanceof IPCCommand) {
            IPCCommand ipc = (IPCCommand) event.getObject();
            if (ipc.getBot() != null && !ipc.getBot().equalsIgnoreCase(ba.getBotName())) return;
            if (ipc.getType() == Command.ECHO)
                ba.sendChatMessage(ipc.getCommand());
            else if (ipc.getType() == Command.EXPIRED) {
                if (arenas.containsKey(ipc.getCommand()))
                    arenas.get(ipc.getCommand()).expire();
                checkArenas();
            }
        }
    }
    
    public void cmd_help(String name) {
        ArrayList<String> msg = new ArrayList<String>();
        msg.add("- TWD Hub Commands -");
        msg.add(" !alert        - Toggles a PM alert whenever a new TWD match starts");
        msg.add(" !games        - List of games currently in progress");
        msg.add(" !list         - List of current bot values");
        if (oplist.isModerator(name)) {
            msg.add(" !check        - Checks for any arenas needing bots");
            msg.add(" !reset        - Resets all trackers and calls for checkin (goto fix it cmd)");
            msg.add(" !debug        - Toggle debug mode");
            msg.add(" !sdtwd        - Shutdown TWD: kill all matchbots (lets games finish)");
            msg.add(" !die          - Kills bot");
        }
        ba.smartPrivateMessageSpam(name, msg.toArray(new String[msg.size()]));
    }
    
    public void cmd_reset(String name) {
        ba.sendSmartPrivateMessage(name, "Initiating reset and requesting bot checkin...");
        needsBot.clear();
        squads.clear();
        bots.clear();
        arenas.clear();
        checkIn();
    }
    
    public void cmd_alert(String name) {
        if (alerts.remove(low(name)))
            ba.sendSmartPrivateMessage(name, "New game alerts DISABLED");
        else {
            alerts.add(low(name));
            ba.sendSmartPrivateMessage(name, "New game alerts ENABLED");
        }
    }
    
    public void cmd_list(String name) {
        String msg = "needsBot: ";
        for (String n : needsBot)
            msg += n + " ";
        ba.sendSmartPrivateMessage(name, msg);
        msg = "freeBots: ";
        for (String n : freeBots)
            msg += n + " ";
        ba.sendSmartPrivateMessage(name, msg);
        msg = "arenas: ";
        for (String n : arenas.keySet())
            msg += n + " ";
        ba.sendSmartPrivateMessage(name, msg);
        msg = "bots: ";
        for (String n : bots.keySet())
            msg += n + " ";
        ba.sendSmartPrivateMessage(name, msg);
    }
    
    public void cmd_games(String name) {
        boolean idle = true;
        for (Arena arena : arenas.values()) {
            if (arena.hasGame()) {
                idle = false;
                ba.sendSmartPrivateMessage(name, arena.game.toString());
            }
        }
        if (idle)
            ba.sendSmartPrivateMessage(name, "No games are being played at the moment.");
    }
    
    public void cmd_shutdown(String name) {
        shutdown = true;
        ba.sendSmartPrivateMessage(name, "Initiating shutdown of all matchbots.");
        ba.sendChatMessage(2, "Total TWD bot shutdown requested by: " + name);
        ba.sendChatMessage(3, "Total TWD bot shutdown requested by: " + name);
        ba.ipcTransmit(IPC, new IPCCommand(Command.DIE, null));
    }
    
    public void cmd_die(String name) {
        ba.sendChatMessage(3, "Disconnecting as requested by " + name);
        ba.sendChatMessage(2, "Disconnecting as requested by " + name);
        ba.sendChatMessage("Disconnecting as requested by " + name);
        handleDisconnect();
    }
    
    private void cmd_debug(String name) {
        if (!DEBUG) {
            debugger = name;
            DEBUG = true;
            ba.sendSmartPrivateMessage(name, "Debugging ENABLED. You are now set as the debugger.");
        } else if (debugger.equalsIgnoreCase(name)){
            debugger = "";
            DEBUG = false;
            ba.sendSmartPrivateMessage(name, "Debugging DISABLED and debugger reset.");
        } else {
            ba.sendChatMessage(name + " has overriden " + debugger + " as the target of debug messages.");
            ba.sendSmartPrivateMessage(name, "Debugging still ENABLED and you have replaced " + debugger + " as the debugger.");
            debugger = name;
        }
    }
    
    private void debug(String msg) {
        if (DEBUG)
            ba.sendSmartPrivateMessage(debugger, "[DEBUG] " + msg);
    }
    
    private void requestEvents() {
        EventRequester er = ba.getEventRequester();
        er.request(EventRequester.ARENA_JOINED);
        er.request(EventRequester.LOGGED_ON);
        er.request(EventRequester.PLAYER_ENTERED);
        er.request(EventRequester.PLAYER_LEFT);
        er.request(EventRequester.MESSAGE);
    }
    
    private void checkIn() {
        ba.ipcTransmit(IPC, new IPCCommand(Command.CHECKIN, null));
        TimerTask check = new TimerTask() {
            public void run() {
                if (startup)
                    startup = false;
                checkArenas();
            }
        };
        ba.scheduleTask(check, 3000);
    }
    
    private void checkArenas() {
        debug("Checking arenas...");
        checkDiv("twbd");
        checkDiv("twdd");
        checkDiv("twjd");
        checkDiv("twsd");
        checkDiv("twfd");
    }
    
    private void checkDiv(String div) {
        if (startup || shutdown) return;
        Arena arena;
        if (arenas.containsKey(div)) {
            arena = arenas.get(div);
            if (arena.isActive()) {
                
                if (arenas.containsKey(div + "2")) {
                    arena = arenas.get(div + "2");
                    if (arena.isActive()) {
                        botStay(arena.bot);
                        
                        if (arenas.containsKey(div + "3")) {
                            arena = arenas.get(div + "3");
                            if (arena.isActive()) {
                                botStay(arena.bot);
                                
                                if (arenas.containsKey(div + "4")) {
                                    arena = arenas.get(div + "4");
                                    if (arena.isActive()) {
                                        botStay(arena.bot);
                                        
                                        if (arenas.containsKey(div + "5")) {
                                            arena = arenas.get(div + "5");
                                            if (arena.isActive())
                                                botStay(arena.bot);
                                        } else
                                            botSpawn(div + "5");
                                    } else
                                        botRemove(div + "5");
                                } else {
                                    botSpawn(div + "4");
                                    botRemove(div + "5");
                                }
                            } else {
                                botRemove(div + "4");
                                botRemove(div + "5");
                            }
                        } else {
                            botSpawn(div + "3");
                            botRemove(div + "4");
                            botRemove(div + "5");
                        }
                    } else {
                        botRemove(div + "3");
                        botRemove(div + "4");
                        botRemove(div + "5");
                    }
                } else {
                    botSpawn(div + "2");
                    botRemove(div + "3");
                    botRemove(div + "4");
                    botRemove(div + "5");
                }
            } else {
                botRemove(div + "2");
                botRemove(div + "3");
                botRemove(div + "4");
                botRemove(div + "5");
            }
        } else {
            botSpawn(div);
            botRemove(div + "2");
            botRemove(div + "3");
            botRemove(div + "4");
            botRemove(div + "5");
        }
    }
    
    private void lockBots() {
        while (!needsBot.isEmpty() && !freeBots.isEmpty()) {
            String name = needsBot.remove(0);
            sentSpawn.remove(name);
            botLock(freeBots.remove(0), name);
        }
    }
    
    private void botDisconnected(String bot) {
        debug("Bot disconnected: " + bot);
        if (bots.containsKey(bot)) {
            Arena arena = bots.get(bot);
            if (arena.status == ArenaStatus.DYING) {
                arenas.remove(arena.name);
                bots.remove(bot);
            } else if (!shutdown) {
                // UNEXPECTED DISCONNECT
                debug("Unexpected disconnect for " + bot);
                arenas.remove(arena.name);
                bots.remove(bot);
                if (arena != null && arena.game != null) {
                    arena.game.squad1.endGame(arena.name);
                    arena.game.squad2.endGame(arena.name);
                }
            }
            checkArenas();
        }
    }
    
    private void botStay(String name) {
        if (arenas.containsKey(name)) {
            Arena arena = arenas.get(name);
            if (arena.status == ArenaStatus.DYING) {
                debug("Bot stay: " + name);
                arena.status = ArenaStatus.READY;
                ba.ipcTransmit(IPC, new IPCCommand(Command.STAY, name));
            }
        }
    }
    
    private void botSpawn(String name) {
        if (shutdown) return;
        if (!arenas.containsKey(name)) {
            Arena arena = new Arena(name);
            arenas.put(low(name), arena);
        } else if (arenas.get(name).status != ArenaStatus.WAITING)
            return;
        debug("Bot spawn: " + name);
        needsBot.add(name);
        if (freeBots.isEmpty() && !sentSpawn.contains(name)) {
            sentSpawn.add(name);
            ba.sendSmartPrivateMessage(HUB, "!spawn matchbot");
        } else 
            lockBots();
    }
    
    private void botRemove(String name) {
        if (arenas.containsKey(name)) {
            needsBot.remove(name);
            Arena arena = arenas.get(name);
            if (arena.status != ArenaStatus.DYING) { 
                debug("Arena remove: " + name);
                arena.status = ArenaStatus.DYING;
                ba.ipcTransmit(IPC, new IPCCommand(Command.DIE, arena.bot, null));
            }
        } else if (oplist.isBotExact(name)){
            debug("Bot remove: " + name);
            ba.ipcTransmit(IPC, new IPCCommand(Command.DIE, name, null));
        }
    }
    
    /**
     * Gets the Arena object and sets the bot name. Then sets the Arena status to READY
     * and sends !lock for the appropriate division and arena.
     * 
     * @param bot
     * @param arenaName
     */
    private void botLock(String bot, String arenaName) {
        if (shutdown) return;
        debug("Bot lock: " + bot + "-" + arenaName);
        Arena arena = arenas.get(arenaName);
        if (arena.status != ArenaStatus.WAITING) {
            debug("Status was not waiting, so aborting " + arena.name);
            return;
        }
        arena.bot = bot;
        arena.status = ArenaStatus.READY;
        String div = arenaName.substring(0, 4);
        if (div.equals("twbd"))
            ba.sendSmartPrivateMessage(bot, "!lock " + TWBD + ":" + arenaName);
        else if (div.equals("twjd"))
            ba.sendSmartPrivateMessage(bot, "!lock " + TWJD + ":" + arenaName);
        else if (div.equals("twdd"))
            ba.sendSmartPrivateMessage(bot, "!lock " + TWDD + ":" + arenaName);
        else if (div.equals("twsd"))
            ba.sendSmartPrivateMessage(bot, "!lock " + TWSD + ":" + arenaName);
        else if (div.equals("twfd"))
            ba.sendSmartPrivateMessage(bot, "!lock " + TWFD + ":" + arenaName);
        bots.put(bot, arena);
        final Arena farena = arena;
        TimerTask flush = new TimerTask() {
            public void run() {
                farena.flushIPC();
            }
        };
        ba.scheduleTask(flush, 2000);
    }
    
    private void sendAlerts(Game game) {
        for (String name : alerts)
            ba.sendSmartPrivateMessage(name, game.getType() + " (" + game.arena.name + "): " + game.squad1.name + " vs " + game.squad2.name);
    }
    
    private boolean isTWD(String name) {
        name = low(name);
        return name.startsWith("twdd") || name.startsWith("twbd") || name.startsWith("twjd") || name.startsWith("twsd") || name.startsWith("twfd");
    }
    
    class Arena {
        
        String name;
        String bot;
        Game game;
        ArenaStatus status;
        boolean activeChalls, expired;
        HashMap<String, IPCChallenge> challs;
        
        public Arena(String name) {
            this.name = name;
            status = ArenaStatus.WAITING;
            bot = null;
            game = null;
            activeChalls = false;
            expired = false;
            challs = new HashMap<String, IPCChallenge>();
        }
        
        public void expire() {
            activeChalls = false;
            expired = true;
        }
        
        public boolean hasGame() {
            return game != null;
        }
        
        public boolean isActive() {
            return activeChalls || hasGame();
        }
        
        public HashMap<String, IPCChallenge> getChalls() {
            return challs;
        }
        
        public void add(IPCChallenge challenge) {
            if (challs.containsKey(low(challenge.getName())))
                ba.sendSmartPrivateMessage(challenge.getName(), "Previous challenge replaced.");
            challs.put(low(challenge.getName()), challenge);
        }
        
        public void flushIPC() {
            if (challs.isEmpty()) return;
            ipcFlag();
            for (IPCChallenge ipc : challs.values()) {
                ipc.setBot(bot);
                ba.ipcTransmit(IPC, ipc);
            }
            challs.clear();
        }
        
        public void ipcFlag() {
            if (!expired)
                activeChalls = true;
        }
    }
    
    class Game {
        
        Squad squad1, squad2;
        Arena arena;
        int round;
        int score1;
        int score2;
        int state;
        int id;
        HashSet<String> alerted;
        
        public Game(int id, String team1, String team2, String arenaName) {
            this.id = id;
            arena = null;
            squad1 = null;
            squad2 = null;
            score1 = 0;
            score2 = 0;
            round = 0;
            state = 0;
            alerted = new HashSet<String>();
            if (arenas.containsKey(arenaName))
                arena = arenas.get(arenaName);
            else {
                debug("Arena not found.");
                return;
            }
            if (squads.containsKey(low(team1)))
                squad1 = squads.get(low(team1));
            else {
                squad1 = new Squad(team1);
                squads.put(low(team1), squad1);
            }
            if (squads.containsKey(low(team2)))
                squad2 = squads.get(low(team2));
            else {
                squad2 = new Squad(team2);
                squads.put(low(team2), squad2);
            }
            if (squad1 != null && squad2 != null) {
                squad1.addGame(arenaName);
                squad2.addGame(arenaName);
                sendAlerts(this);
            }
        }
        
        public void setID(int id) {
            this.id = id;
        }

        public void alert(String name, String squad) {
            if (!alerted.isEmpty() && alerted.contains(name.toLowerCase()))
                return;
            String nme;
            if (squad.equalsIgnoreCase(squad1.name))
                nme = squad2.name;
            else
                nme = squad1.name;

            String stateS;
            if (state == 0)
                stateS = "preparing";
            else
                stateS = "playing";

            m_botAction.sendSmartPrivateMessage(name, "Your squad is " + stateS + " a " + getType() + " match against " + nme + " in ?go " + arena.name);
            alerted.add(name.toLowerCase());
        }
        
        public String getType() {
            String aname = arena.name.toLowerCase();
            if (aname.startsWith("twbd"))
                return "TWD Basing";
            else if (aname.startsWith("twdd"))
                return "TWD Dueling";
            else if (aname.startsWith("twjd"))
                return "TWD Javelin";
            else if (aname.startsWith("twsd"))
                return "TWD Spider";
            else if (aname.startsWith("twfd"))
                return "TWD Fighter";
            else
                return "TWD";
        }
        
        public void setState(int s) {
            state = s;
        }
        
        public void setRound(int r) {
            round = r;
        }
        
        public void next() {
            if (state == 0)
                state = 1;
            else {
                state = 0;
                round++;
            }
        }

        public String toString() {
            String stateS;
            if (state == 0)
                stateS = "starting";
            else
                stateS = "playing";
            String result = "";
            if (getType().equals("TWD Basing"))
                result += getType() + "(" + arena.name + "): " + squad1.name + " vs " + squad2.name + " (" + stateS + ")";
            else
                result += getType() + "(" + arena.name + "): " + squad1.name + " vs " + squad2.name + " (" + score1 + "-" + score2 + " " + stateS + " round " + round + ")";
            return result;
        }
    }
    
    class Squad {
        
        String name;
        VectorSet<String> games;
        
        public Squad(String name) {
            this.name = name;
            games = new VectorSet<String>();
        }
        
        public VectorSet<String> getGames() {
            return games;
        }
        
        public void addGame(String arena) {
            games.add(arena);
        }
        
        public void endGame(String arena) {
            games.removeElement(arena);
            if (games.isEmpty())
                squads.remove(low(name));
        }
        
    }
    
    @Override
    public void handleDisconnect() {
        ba.cancelTasks();
        TimerTask die = new TimerTask() {
            public void run() {
                ba.die();
            }
        };
        ba.scheduleTask(die, 2000);
    }
    
    private String low(String str) {
        return str.toLowerCase();
    }
}
