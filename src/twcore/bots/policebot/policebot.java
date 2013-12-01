package twcore.bots.policebot;

import java.util.Random;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.Vector;


import twcore.bots.staffbot.staffbot_banc.BanCType;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

/**
 * A simple bot designed for one thing: search & destroy
 * Solves the problem posed by players entering from a different zone and
 * changing arenas before the pubbot can go through banc silence routines. 
 * It seeks out the player and follows through with the banc silence procedures.
 *
 * @author WingZero
 */
public class policebot extends SubspaceBot {
    
    public static final String IPCBANC = "banc";
    public static final String IPCPOLICE = "police";
    public static final String HOME = "#robopark";
    public static final int LOCATES = 5;

    private BotSettings sets;                   // BotSettings ease of access ** may not be needed if not used much
    private OperatorList ops;                   // OperatorList ease of access
    
    private TreeMap<String, BanC> bancs;        // Current BanCs 
    private TreeMap<String, String> guards;     // Quick fix for getting the guard requesting the *info
    private Vector<String> perps;               // Perpetrator tracking list as reported by pubbot 
    
    private String perp;                        // Current perp being worked
    private Status status;                      // Current status/operation
    private String debugger;                    // Current debugger
    private int locateCount;                    // Number of locate commands since last wait period
    private Random rand;                        // RNG for locate wait timer period
    
    private LocateWait locateWait;              // Wait timer to prevent DC's from too many locates
    private Tracker tracker;                    // Main bot loop for rotating perp tracking and silencing
    
    public policebot(BotAction botAction) {
        super(botAction);
        requestEvents();
        bancs = new TreeMap<String, BanC>(String.CASE_INSENSITIVE_ORDER);
        guards = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        perps = new Vector<String>();
        perp = null;
        status = Status.IDLE;
        debugger = "WingZero";
        locateCount = 0;
        locateWait = null;
        rand = new Random();
    }

    /** Request events **/
    private void requestEvents() {
        EventRequester er = ba.getEventRequester();
        er.request(EventRequester.MESSAGE);
        er.request(EventRequester.ARENA_JOINED);
    }
    
    /** Logged on event handler **/
    public void handleEvent(LoggedOn event) {
        ba.ipcSubscribe(IPCBANC);
        ba.ipcSubscribe(IPCPOLICE);
        
        sets = ba.getBotSettings();
        ops = ba.getOperatorList();
        
        ba.joinArena(sets.getString("InitialArena"));
        
        tracker = new Tracker();
        ba.scheduleTask(tracker, 5000, 5000);
    }

    /**
     * Message event handler:
     *  - Handles *info results
     *  - Handles *locate results for tracking down player arenas
     *  - Handles silenced/unsilenced confirmations
     *  - Handles commands
     */
    public void handleEvent(Message event) {
        String message = event.getMessage().trim();
        if (event.getMessageType() == Message.ARENA_MESSAGE) {
            // Command result returns (arena messages)
            if (status == Status.LOCATE && perp != null && message.toLowerCase().startsWith(perp.toLowerCase() + " - ")) {
                // Locate was successful so pursue if private arena, otherwise ignore
                debug("Locate message received: " + message);
                status = Status.APPREHEND;
                if (!message.contains("Public "))
                    ba.changeArena(message.substring(perp.length() + 3));
                else
                    status = Status.IDLE;
            } else if (status == Status.CONFIRM && perp != null) {
                BanC banc = bancs.get(perp);
                if (banc == null) {
                    if (message.startsWith("IP:")) {
                        String guard = guards.remove(perp);
                        ba.ipcSendMessage(IPCPOLICE, message, guard, ba.getBotName());
                        debug("Sending info to " + guard + ":" + message);
                        status = Status.IDLE;
                    } else if (message.startsWith("Bytes/Sec:"))
                        status = Status.IDLE;
                } else if (message.equalsIgnoreCase(banc.getName() + " can now speak")) {
                    // This bot should ALWAYS be silencing players as this means the player was accidentally unsilenced
                    ba.sendUnfilteredPrivateMessage(banc.getName(), "*shutup");
                } else if (message.equalsIgnoreCase(banc.getName() + " has been silenced")) {
                    if (banc.getTime() == 0)
                        m_botAction.sendPrivateMessage(banc.getName(), "You've been permanently silenced because of abuse and/or violation of Trench Wars rules.");
                    else
                        m_botAction.sendPrivateMessage(banc.getName(), "You've been silenced for " + banc.getTime()
                                + " (" + banc.getTime() + " remaining) minutes because of abuse and/or violation of Trench Wars rules.");
                    m_botAction.ipcSendMessage(IPCBANC, banc.getCommand(), "banc", m_botAction.getBotName());
                    if (perp != null)
                        bancs.remove(perp);
                    status = Status.IDLE;
                } else if (message.equalsIgnoreCase("Player locked in spectator mode")) {
                    // The bot just spec-locked the player (and it's ok)
                    if (banc.getTime() == 0)
                        m_botAction.sendPrivateMessage(banc.getName(), "You've been permanently locked into spectator because of abuse and/or violation of Trench Wars rules.");
                    else
                        m_botAction.sendPrivateMessage(banc.getName(), "You've been locked into spectator for " + banc.getTime()
                                + " (" + banc.getTime() + " remaining) minutes because of abuse and/or violation of Trench Wars rules.");
                    m_botAction.ipcSendMessage(IPCBANC, banc.getCommand(), "banc", m_botAction.getBotName());
                    if (perp != null)
                        bancs.remove(perp);
                    status = Status.IDLE;
                } else if (message.equalsIgnoreCase("Player free to enter arena")) {// The bot just unspec-locked the player, while the player should be spec-locked.
                    ba.spec(perp);
                }
            }
        } else if (event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE || event.getMessageType() == Message.PRIVATE_MESSAGE) {
            // Command and message interpreter
            String name = event.getMessager();
            if (name == null || name.length() < 1)
                name = m_botAction.getPlayerName(event.getPlayerID());
            
            String cmd = message.toLowerCase();
            
            if (ops.isSmod(name))
                if (cmd.startsWith("!die"))
                    cmd_die(name);
                else if (cmd.equals("!help"))
                    cmd_help(name);
                else if (cmd.startsWith("!debug"))
                    cmd_debug(name);
                else if (cmd.equals("!status"))
                    cmd_status(name);
        }
    }
    
    /**
     * ArenaJoined event handler:
     *  - Upon entering an arena the bot will proceed with tracked player silencing
     */
    public void handleEvent(ArenaJoined event) {
        // status should be apprehend
        if (status == Status.APPREHEND && perp != null) {
            String name = ba.getFuzzyPlayerName(perp);
            if (name != null && name.equalsIgnoreCase(perp)) {
                status = Status.CONFIRM;
                BanC banc = bancs.get(perp);
                if (banc == null) {
                    debug("BanC was null so getting info for: " + perp);
                    ba.sendUnfilteredPrivateMessage(perp, "*info");
                } else {
                    switch (banc.getType()) {
                        case SILENCE:
                            ba.sendUnfilteredPrivateMessage(name, "*shutup");
                            break;
                        case SPEC:
                            ba.sendUnfilteredPrivateMessage(name, "*spec");
                            break;
                        default:
                            status = Status.IDLE;
                            break;
                    }
                    if (ba.getArenaSize() < 3)
                        ba.sendArenaMessage("WOOP! WOOP!");
                    debug("Apprehended " + banc.getType().toString() + " suspect: " + banc.getName());
                }
            }
        }
    }
    
    /**
     * IPCMessage event handler is used by pubbots and staffbot to relay banc information.
     *  - Adds silenced player information
     *  - Removes silenced information 
     *  - Tracks down players reported by StaffBot to have entered from cross-zone
     */
    public void handleEvent(InterProcessEvent event) {
        if (IPCPOLICE.equals(event.getChannel())) {
            debug("Got IPCMessage on IPCPolice channel");
            IPCMessage ipc = (IPCMessage) event.getObject();
            String info = ipc.getMessage().toLowerCase();
            if (info.toLowerCase().startsWith("banc:")) {
                BanC b = new BanC(info);
                if(!ba.getOperatorList().isBotExact(b.getName())) {
                    bancs.put(b.getName(), b);
                    perps.add(b.getName());
                }
            } else if (info.toLowerCase().startsWith("info:")) {
                String[] args = info.split(":");
                if(!ba.getOperatorList().isBotExact(args[1])) {
                    perps.add(args[1]);
                    guards.put(args[1], ipc.getSender());
                }
            } else {
                debug("Unknown error: " + info);
            }
        }
    }
    
    /** Sends help screen **/
    private void cmd_help(String name) {
        String[] msgs = new String[] {
                ",------------- POLICE BOT HELP -------------.",
                "| !debug   - enables debug messages         |",
                "| !status  - lists current bot variables    |",
                "`-------------------------------------------'"
        };
        ba.smartPrivateMessageSpam(name, msgs);
    }
    
    /** Shows current status of bot variables **/
    private void cmd_status(String name) {
        String msg = "Status: " + status.toString() + " Current perp: " + (perp != null ? perp : "null");
        ba.sendSmartPrivateMessage(name, msg);
        msg = "Perps: ";
        for (String s : perps)
            msg += s + ", ";
        ba.sendSmartPrivateMessage(name, msg);
    }
    
    /** Toggles debug messages **/
    private void cmd_debug(String name) {
        if (debugger != null && debugger.equalsIgnoreCase(name)) {
            debugger = null;
            ba.sendSmartPrivateMessage(name, "Debugging DISABLED");
        } else {
            debugger = name;
            ba.sendSmartPrivateMessage(name, "Debugging ENABLED");
        }
    }
    
    /** Kills the bot **/
    private void cmd_die(String name) {
        ba.cancelTasks();
        if (name.length() > 0) {
            ba.sendSmartPrivateMessage(name, "Goodbye!");
            ba.die(name + " the douchebag killed me.");
        } else
            ba.die();
    }
    
    /**
     * Determines if it is safe to do a *locate command and returns false if so.
     *
     * @return false if okay to *locate
     */
    private boolean locateWait() {
        if (locateWait != null)
            return true;
        else if (locateCount >= LOCATES) {
            locateWait = new LocateWait();
            int time = rand.nextInt(3 * 60) + 30;  // in between 30 seconds and 3 minutes
            ba.scheduleTask(locateWait, time * Tools.TimeInMillis.SECOND);
            debug("New locate wait timer set for: " + time + " secs");
            return true;
        } else
            return false;
    }
    
    private void debug(String msg) {
        if (debugger != null)
            ba.sendSmartPrivateMessage(debugger, "[DEBUG] " + msg);
    }
    
    public void handleDisconnect() {
        ba.cancelTasks();
    }
    
    enum Status {
        // more like POST status
        LOCATE,     // attempted to find player but no result returned
        APPREHEND,  // entered arena so issue silence/spec and wait for confirmation
        CONFIRM,    // awaiting confirmation meaning none was given yet so go back to locating
        IDLE        // previous cycle completed so either get next perp or return home
    }
    
    /**
     * Tracker is the main loop which systematically cycles through a list of perpetrators
     * as reported by pubbot to pursue and potentially silence.
     *
     * @author WingZero
     */
    private class Tracker extends TimerTask {

        @Override
        public void run() {
            switch (status) {
                case APPREHEND:
                    // handled by ArenaJoined
                    break;
                case CONFIRM:
                    // handled by Message event
                    // awaiting confirmation meaning none was given yet so go back to locating
                    // Perp must have fled to a different arena
                    if (!locateWait()) {
                        debug("Relocating " + perp);
                        status = Status.LOCATE;
                        ba.locatePlayer(perp);
                        locateCount++;
                    }
                    break;
                case LOCATE:
                    // locate failed, player must be offline
                    debug("Locating " + perp + " failed");
                    status = Status.IDLE;
                case IDLE:
                    if (perps.isEmpty() && !ba.getArenaName().equalsIgnoreCase(HOME)) {
                        // nothing to do
                        debug("Nothing to do: returning home");
                        perp = null;
                        ba.changeArena(HOME);
                    } else if (!perps.isEmpty()) {
                        if (!locateWait()) {
                            perp = perps.remove(0);
                            debug("Locating " + perp);
                            status = Status.LOCATE;
                            ba.locatePlayer(perp);
                            locateCount++;
                        }
                    }
                    break;
            }
        }
        
    }
    
    private class LocateWait extends TimerTask {
        
        @Override
        public void run() {
            locateCount = 0;
            locateWait = null;
        }
    }

    /**
     * BanC - stripped down from pubbotbanc's version to include only pertinent information related to policebot.
     *
     * @author WingZero
     */
    class BanC {

        String name;
        long time;
        BanCType type;
        int elapsed;

        public BanC(String info) {
            // name:type:time
            debug("Creating BanC: " + info);
            String[] args = info.split(":");
            name = args[1];
            time = Long.valueOf(args[3]);

            if (args[2].equalsIgnoreCase("SILENCE"))
                type = BanCType.SILENCE;
            else if (args[2].equalsIgnoreCase("SPEC"))
                type = BanCType.SPEC;
            else
                type = BanCType.SUPERSPEC;
        }
        
        public String getRemaining() {
            return "" + (time);
        }

        public String getCommand() {
            return "" + type.toString() + ":" + name + ":" + name;
        }

        public String getName() {
            return name;
        }

        public long getTime() {
            return time;
        }

        public BanCType getType() {
            return type;
        }
    }
}
