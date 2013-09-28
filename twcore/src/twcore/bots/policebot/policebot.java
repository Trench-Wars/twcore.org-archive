package twcore.bots.policebot;

import java.util.ListIterator;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
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
import twcore.core.util.ipc.IPCEvent;
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
    public static final String HOME = "tw";

    private BotSettings sets;                   // BotSettings ease of access ** may not be needed if not used much
    private OperatorList ops;                   // OperatorList ease of access
    
    private TreeMap<String, Silence> silences;  // Current BanC silences
    private Vector<String> perps;               // Perpetrator tracking list as reported by pubbot 
    
    private String perp;                        // Current perpetrator being tracked down
    private Silence current;                    // Current silence being worked
    
    private boolean locating;                   // Toggle used for locating players who may be offline
    
    private Tracker tracker;                    // Main bot loop for rotating perp tracking and silencing
    
    private String debugger;                    // Current debugger
    
    public policebot(BotAction botAction) {
        super(botAction);
        requestEvents();
        silences = new TreeMap<String, Silence>(String.CASE_INSENSITIVE_ORDER);
        perps = new Vector<String>();
        current = null;
        perp = null;
        locating = false;
        debugger = "WingZero";
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
        
        //TODO: Make sure StaffBot is ready for this bot's name of TW-Police
        // Request active BanCs from StaffBot
        TimerTask initActiveBanCs = new TimerTask() {
            @Override
            public void run() {
                m_botAction.ipcSendMessage(IPCBANC, "BANC PUBBOT INIT", null, m_botAction.getBotName());
            }
        };
        m_botAction.scheduleTask(initActiveBanCs, 1000);
        
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
            if (perp != null && message.toLowerCase().startsWith(perp.toLowerCase() + " - ")) {
                // Locate was successful so pursue if private arena, otherwise ignore
                debug("Locate message received: " + message);
                locating = false;
                if (message.contains("#"))
                    ba.changeArena(message.substring(message.indexOf("#")));
                else {
                    perp = null;
                    current = null;
                }
            } else if (message.startsWith("IP:"))
                checkSilences(message);
            else if (current != null) {
                if (message.equalsIgnoreCase(current.getName() + " can now speak")) {
                    // This bot should ALWAYS be silencing players as this means the player was accidentally unsilenced
                    ba.sendUnfilteredPrivateMessage(current.getName(), "*shutup");
                } else if (message.equalsIgnoreCase(current.getName() + " has been silenced")) {
                    if (current.getTime() == 0)
                        m_botAction.sendPrivateMessage(current.getName(), "You've been permanently silenced because of abuse and/or violation of Trench Wars rules.");
                    else
                        m_botAction.sendPrivateMessage(current.getName(), "You've been silenced for " + current.getTime()
                                + " (" + current.getTime() + " remaining) minutes because of abuse and/or violation of Trench Wars rules.");
                    m_botAction.ipcSendMessage(IPCBANC, current.getCommand(), "banc", m_botAction.getBotName());
                    current = null;
                    perp = null;
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
                else if (cmd.equals("!bancs"))
                    cmd_bancs(name);
                else if (cmd.equals("!status"))
                    cmd_status(name);
        }
    }
    
    /**
     * ArenaJoined event handler:
     *  - Upon entering an arena the bot will proceed with tracked player silencing
     */
    public void handleEvent(ArenaJoined event) {
        if (perp != null) {
            String name = ba.getFuzzyPlayerName(perp);
            if (name != null && name.equalsIgnoreCase(perp)) {
                ba.sendUnfilteredPrivateMessage(name, "*info");
                ba.sendArenaMessage("WOOP! WOOP!");
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
        if (IPCBANC.equals(event.getChannel()) && event.getObject() instanceof IPCEvent) {
            debug("Got IPCEvent");
            // This is usually used when StaffBot is sending to ALL pubbots
            IPCEvent ipc = (IPCEvent) event.getObject();
            if (ipc.isAll()) {
                @SuppressWarnings("unchecked")
                ListIterator<String> i = (ListIterator<String>) ipc.getList();
                debug("ipc.getList() " + (ipc.getList() == null ? "null" : "not null"));
                while (i.hasNext())
                    handleSilence(i.next());
            } else {
                debug("ipc.getName() == " + ipc.getName());
                handleSilence(ipc.getName());
            }
        } else if (IPCBANC.equals(event.getChannel())
                && event.getObject() != null
                && event.getObject() instanceof IPCMessage
                && ((IPCMessage) event.getObject()).getSender() != null
                && ((IPCMessage) event.getObject()).getSender().equalsIgnoreCase("banc")) {
            // Specialized for specific banc removals
            debug("Got IPCMessage");
            IPCMessage ipc = (IPCMessage) event.getObject();
            String command = ipc.getMessage();
            if (command.startsWith("REMOVE"))
                handleRemove(command);
        } else if (IPCPOLICE.equals(event.getChannel())) {
            debug("Got IPCMessage for Police");
            IPCMessage ipc = (IPCMessage) event.getObject();
            String perp = ipc.getMessage().toLowerCase();
            // TODO: Add process for creating a new perp tracker
            if (!perps.contains(perp))
                perps.add(perp);
        }
    }
    
    /** Sends help screen **/
    private void cmd_help(String name) {
        String[] msgs = new String[] {
                ",------------- POLICE BOT HELP -------------.",
                "| !debug   - enables debug messages         |",
                "| !bancs   - lists current silence bancs    |",
                "| !status  - lists current bot variables    |",
                "`-------------------------------------------'"
        };
        ba.smartPrivateMessageSpam(name, msgs);
    }
    
    /** Shows current status of bot variables **/
    private void cmd_status(String name) {
        String msg = "Locating: " + locating + " Perp: " + perp + " Current: " + (current != null ? current.getName() : "null");
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
    
    /** Lists current silences **/
    private void cmd_bancs(String name) {
        m_botAction.sendSmartPrivateMessage(name, " Silences:");
        for (Silence b : silences.values())
            m_botAction.sendSmartPrivateMessage(name, "  " + b.getName() + " IP=" + (b.ip != null ? b.ip : "") + " MID=" + (b.mid != null ? b.mid : ""));
    }
    
    /**
     * Takes *info and checks to see if player should be silenced. Sets current silence since
     * perp was found.
     * 
     * @param info
     */
    private void checkSilences(String info) {
        if (current != null) return;
        
        debug("Checking silences with info string: " + info);
        
        String name = getInfo(info, "TypedName:");
        String ip = getInfo(info, "IP:");
        String mid = getInfo(info, "MachineId:");
        
        if (perp.equalsIgnoreCase(name))
            perp = null;
        
        if (silences.containsKey(name))
            current = silences.get(name).reset();
        else
            for (Silence banc : silences.values())
                if (banc.isMatch(name, ip, mid))
                    current = banc;
        
        if (current != null)
            ba.sendUnfilteredPrivateMessage(name, "*shutup");
    }
    
    /**
     * Removes silences as requested by StaffBot from the banc IPC.
     * 
     * @param cmd
     */
    private void handleRemove(String cmd) {
        debug("HandleRemove: " + cmd);
        String[] args = cmd.split(":");
        if (args[1].equals(BanCType.SILENCE.toString()))
            silences.remove(args[2]);
    }
    
    /**
     * Adds silence information to silences list as specified by StaffBot from the BanC IPC.
     * 
     * @param banc
     */
    private void handleSilence(String cmd) {
        debug("HandleSilence: " + cmd);
        String[] args = cmd.split(":");
        if (!args[4].equals("SILENCE"))
            return;
        Silence banc = new Silence(cmd);
        silences.put(banc.getName(), banc);
    }

    /**
     * Helper method used to get the correct player name regardless of capitalization.
     * 
     * @param banc
     * @return
     */
    private String getTarget(Silence banc) {
        if (banc == null)
            return null;
        String target = m_botAction.getFuzzyPlayerName(banc.getName());
        if (target != null && target.equalsIgnoreCase(banc.getName()))
            banc.name = target;
        else
            target = null;
        return target;
    }

    /**
     * Takes lines from an *info and returns the requested information.
     * 
     * @param message
     *          Line from info
     * @param infoName
     *          Type of data requested i.e. TypedName
     * @return
     *          String
     */
    private String getInfo(String message, String infoName) {
        int beginIndex = message.indexOf(infoName);
        int endIndex;

        if (beginIndex == -1)
            return null;
        beginIndex = beginIndex + infoName.length();
        endIndex = message.indexOf("  ", beginIndex);
        if (endIndex == -1)
            endIndex = message.length();
        return message.substring(beginIndex, endIndex);
    }
    
    private void debug(String msg) {
        if (debugger != null)
            ba.sendSmartPrivateMessage(debugger, "[DEBUG] " + msg);
    }
    
    public void handleDisconnect() {
        ba.cancelTasks();
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
            if (locating) {
                // locate failed, player must be offline
                debug("Locating " + perp + " failed");
                locating = false;
                perp = null;
                current = null;
            }
            if (perp == null && current == null && perps.isEmpty() && !ba.getArenaName().equalsIgnoreCase(HOME)) {
                // nothing to do
                debug("Nothing to do: returning home");
                ba.changeArena(HOME);
            } else if (perp == null && current == null && !perps.isEmpty()) {
                perp = perps.remove(0);
                locating = true;
                ba.locatePlayer(perp);
                debug("Locating " + perp);
            } else if (perp != null && current == null) {
                // Perp must have fled to a different arena
                debug("Relocating " + perp);
                locating = true;
                ba.locatePlayer(perp);
            }
        }
        
    }

    /**
     * Stripped down version of pubbotbanc's BanC class designed for silences only.
     *
     * @author WingZero
     */
    class Silence {

        String name, originalName;
        String ip;
        String mid;
        TreeSet<String> aliases;
        Long time;

        public Silence(String info) {
            // name:ip:mid:time  -- time will not be needed for this bot (taken from pubbotbanc)
            String[] args = info.split(":");
            name = args[0];
            originalName = name;
            ip = args[1];
            if (ip.length() == 1)
                ip = null;
            mid = args[2];
            if (mid.length() == 1)
                mid = null;
            time = Long.valueOf(args[3]);
            aliases = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            aliases.add(originalName);
        }

        public String getCommand() {
            return "SILENCE:" + name + ":" + originalName;
        }

        public String getName() {
            return name;
        }

        public long getTime() {
            return time;
        }

        public boolean isMatch(String name, String ip, String mid) {
            boolean match = false;
            if (this.ip != null && ip.equals(this.ip))
                match = true;
            else if (this.mid != null && mid.equals(this.mid))
                match = true;
            if (match) {
                aliases.add(name);
                this.name = name;
            }
            return match;
        }

        public Silence reset() {
            name = originalName;
            return this;
        }
    }
}
