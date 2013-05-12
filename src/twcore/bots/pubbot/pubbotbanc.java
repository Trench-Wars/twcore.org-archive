package twcore.bots.pubbot;

import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.ListIterator;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.PubBotModule;
import twcore.bots.staffbot.staffbot_banc.BanCType;
import twcore.core.EventRequester;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCEvent;
import twcore.core.util.ipc.IPCMessage;

/**
 * Pubbot BanC module.
 * 
 * Behaviour: - when a player enters, sends a IPC message to pubhub's banc module with the player's name "entered <playername>"
 * 
 * - when the pubbot's alias module requests *info of the player, catch it and IPC it to pubhub's banc module "info <playername>:<ip>:<mid>" - on
 * receiving IPC text 'silence <playername>', *shutup player - reply with IPC message that playername is silenced - on receiving IPC text 'unsilence
 * <playername>', un-*shutup player - reply with IPC message that playername is unsilenced - on receiving IPC text 'speclock <playername>', *spec
 * player - reply with IPC message that playername is spec-locked - on receiving IPC text 'unspeclock <playername>', un-*spec player - reply with IPC
 * message that playername is unspec-locked - on receiving IPC text 'kick <playername>', *kill player - reply with IPC message that playername is
 * kicked
 * 
 * Dependencies: - pubhub's banc module - pubbot's alias module
 * 
 * @author Maverick
 * 
 */
public class pubbotbanc extends PubBotModule {

    public static final String IPCBANC = "banc";
    private static final String AFK_ARENA = "afk";

    private static final long INFINITE_DURATION = 0;
    private static final int MAX_NAME_LENGTH = 19;
    private static final int MAX_IDLE_TIME = 15; //mins

    private TimerTask initActiveBanCs;
    private Action act;
    private SendElapsed elapsed;

    private TreeMap<String, BanC> bancSilence;
    private TreeMap<String, BanC> bancSpec;
    private TreeMap<String, BanC> bancSuper;
    private Vector<Object> actions;

    private Object current;
    private boolean silentKicks;
    private String sendto;
    
    private boolean proxy;
    
    private boolean DEBUG;
    private String debugger;

    @Override
    public void initializeModule() {
        m_botAction.ipcSubscribe(IPCBANC);
        silentKicks = false;
        DEBUG = false;
        proxy = true;
        debugger = null;
        bancSilence = new TreeMap<String, BanC>(String.CASE_INSENSITIVE_ORDER);
        bancSpec = new TreeMap<String, BanC>(String.CASE_INSENSITIVE_ORDER);
        bancSuper = new TreeMap<String, BanC>(String.CASE_INSENSITIVE_ORDER);
        actions = new Vector<Object>();

        String zoneIP = m_botAction.getGeneralSettings().getString("Server");
        String zonePort = m_botAction.getGeneralSettings().getString("Port");
        sendto = "*sendto " + zoneIP + "," + zonePort + "," + AFK_ARENA;

        // Request active BanCs from StaffBot
        initActiveBanCs = new TimerTask() {
            @Override
            public void run() {
                m_botAction.ipcSendMessage(IPCBANC, "BANC PUBBOT INIT", null, m_botAction.getBotName());
            }
        };
        m_botAction.scheduleTask(initActiveBanCs, 1000);

        act = new Action();
        m_botAction.scheduleTask(act, 2000, 2000);
        elapsed = new SendElapsed();
        m_botAction.scheduleTask(elapsed, 2 * Tools.TimeInMillis.MINUTE, 2 * Tools.TimeInMillis.MINUTE);
    }

    @Override
    public void cancel() {
        m_botAction.ipcUnSubscribe(IPCBANC);
        elapsed.stop();
        m_botAction.cancelTask(initActiveBanCs);
        m_botAction.cancelTask(act);
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.PLAYER_LEFT);
        if (m_botAction.getArenaName().contains("(Public"))
            eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
    }

    @Override
    public void handleEvent(PlayerEntered event) {
        String name = event.getPlayerName();
        if (name == null)
            name = m_botAction.getPlayerName(event.getPlayerID());

        if (!m_botAction.getArenaName().contains("Public"))
            m_botAction.sendUnfilteredPrivateMessage(name, "*einfo");

        if (name.startsWith("^") == false)
            m_botAction.sendUnfilteredPrivateMessage(name, "*info");
    }

    @Override
    public void handleEvent(PlayerLeft event) {
        String name = m_botAction.getPlayerName(event.getPlayerID());
        elapsed.rem(name);
    }

    @Override
    public void handleEvent(FrequencyShipChange event) {
        int ship = event.getShipType();
        if (ship != 2 && ship != 4 && ship != 8)
            return;
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (bancSuper.containsKey(name))
            actions.add(bancSuper.get(name));
        else
            for (BanC b : bancSuper.values())
                if (b.isMatch(name))
                    actions.add(b);
    }

    @Override
    public void handleEvent(InterProcessEvent event) {
        if (IPCBANC.equals(event.getChannel()) && event.getObject() instanceof IPCEvent) {
            IPCEvent ipc = (IPCEvent) event.getObject();
            int bot = ipc.getType();
            if (m_botAction.getBotName().startsWith("TW-Guard"))
                bot = Integer.valueOf(m_botAction.getBotName().substring(8));
            if (ipc.getType() < 0 || ipc.getType() == bot)
                if (ipc.isAll()) {
                    elapsed.stop();
                    @SuppressWarnings("unchecked")
                    ListIterator<String> i = (ListIterator<String>) ipc.getList();
                    while (i.hasNext()) {
                        BanC b = new BanC(i.next());
                        handleBanC(b);
                    }
                } else
                    handleBanC(new BanC(ipc.getName()));
        } else if (IPCBANC.equals(event.getChannel())
                && event.getObject() != null
                && event.getObject() instanceof IPCMessage
                && ((IPCMessage) event.getObject()).getSender() != null
                && ((IPCMessage) event.getObject()).getSender().equalsIgnoreCase("banc")
                && (((IPCMessage) event.getObject()).getRecipient() == null || ((IPCMessage) event.getObject()).getRecipient().equalsIgnoreCase(m_botAction.getBotName()))) {

            IPCMessage ipc = (IPCMessage) event.getObject();
            String command = ipc.getMessage();
            if (command.startsWith("REMOVE"))
                handleRemove(command);
        }
    }

    @Override
    public void handleEvent(Message event) {
        String message = event.getMessage().trim();
        if (event.getMessageType() == Message.ARENA_MESSAGE)
            if ((!proxy && message.contains("Proxy: ") && !message.contains("Not using proxy")) || (proxy && message.contains("Proxy: SOCKS5 proxy"))) {
                String name = message.substring(0, message.indexOf(": U"));
                String proxy = message.substring(message.indexOf("Proxy:"), message.indexOf("Idle:")-1);
                m_botAction.sendUnfilteredPrivateMessage(name, "*kill");
                m_botAction.ipcSendMessage(IPCBANC, "KICKED:Player '" + name + "' has been kicked by " + m_botAction.getBotName() + " for using an unapproved proxy. (" + proxy + ")", "banc", m_botAction.getBotName());
            } else if (message.contains("Proxy: ") && !message.contains("Not using proxy")) {
                String name = message.substring(0, message.indexOf(": U"));
                m_botAction.ipcSendMessage(IPCBANC, "PROXY:Player '" + name + "' detected by " + m_botAction.getBotName() + " using a proxy. (" + proxy + ")", "banc", m_botAction.getBotName());
            } else if (message.startsWith("IP:"))
                checkBanCs(message);
            else if (message.contains("Idle: "))
                elapsed.handleIdle(message);
            else if (current != null && current instanceof BanC) {
                BanC current = (BanC) this.current;
                String name = current.getName();
                if (message.equalsIgnoreCase(current.getName() + " can now speak")) {
                    if (!current.isActive()) {
                        // The bot just unsilenced the player (and it's ok)
                        m_botAction.sendPrivateMessage(current.getName(), "Silence lifted. You can now speak.");
                        m_botAction.ipcSendMessage(IPCBANC, current.getCommand(), "banc", m_botAction.getBotName());
                    } else {
                        // The bot just unsilenced the player, while the player should be silenced.
                        current = null;
                        m_botAction.sendUnfilteredPrivateMessage(name, "*shutup");
                    }
                } else if (message.equalsIgnoreCase(current.getName() + " has been silenced")) {
                    if (!current.isActive()) {
                        // The bot just silenced the player, while the player should be unsilenced.
                        current = null;
                        m_botAction.sendUnfilteredPrivateMessage(name, "*shutup");
                    } else {
                        // The bot just silenced the player (and it's ok)
                        if (current.getTime() == INFINITE_DURATION)
                            m_botAction.sendPrivateMessage(current.getName(), "You've been permanently silenced because of abuse and/or violation of Trench Wars rules.");
                        else
                            m_botAction.sendPrivateMessage(current.getName(), "You've been silenced for " + current.getTime()
                                    + " (" + current.getTime() + " remaining) minutes because of abuse and/or violation of Trench Wars rules.");
                        m_botAction.ipcSendMessage(IPCBANC, current.getCommand(), "banc", m_botAction.getBotName());
                    }
                } else if (message.equalsIgnoreCase("Player locked in spectator mode")) {
                    if (!current.isActive()) {
                        // The bot just spec-locked the player, while the player shouldn't be spec-locked.
                        current = null;
                        m_botAction.spec(name);
                    } else {
                        // The bot just spec-locked the player (and it's ok)
                        if (current.getTime() == INFINITE_DURATION)
                            m_botAction.sendPrivateMessage(current.getName(), "You've been permanently locked into spectator because of abuse and/or violation of Trench Wars rules.");
                        else
                            m_botAction.sendPrivateMessage(current.getName(), "You've been locked into spectator for " + current.getTime()
                                    + " (" + current.getTime() + " remaining) minutes because of abuse and/or violation of Trench Wars rules.");
                        m_botAction.ipcSendMessage(IPCBANC, current.getCommand(), "banc", m_botAction.getBotName());
                    }
                } else if (message.equalsIgnoreCase("Player free to enter arena")) {
                    if (!current.isActive()) {
                        // The bot just unspec-locked the player (and it's ok)
                        m_botAction.sendPrivateMessage(current.getName(), "Spectator-lock removed. You may now enter.");
                        m_botAction.ipcSendMessage(IPCBANC, current.getCommand(), "banc", m_botAction.getBotName());

                    } else {// The bot just unspec-locked the player, while the player should be spec-locked.
                        current = null;
                        m_botAction.spec(name);
                    }
                }/* else if (message.equalsIgnoreCase("Player kicked off"))
                    m_botAction.ipcSendMessage(IPCBANC, current.getCommand(), "banc", m_botAction.getBotName());*/
                else if (message.startsWith("REMOVE"))
                    m_botAction.sendChatMessage("Player " + name + " may now play in bombs-ship");
            }

        if (event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) {
            String name = event.getMessager();
            if (name == null)
                name = m_botAction.getPlayerName(event.getPlayerID());
            if (m_botAction.getOperatorList().isSmod(name))
                if (message.equals("!bancs"))
                    cmd_bancs(name);
                else if (message.equals("!kicks"))
                    cmd_kicks(name);
                else if (message.equals("!banc"))
                    cmd_banc(name);
                else if (message.equals("!proxy"))
                    cmd_proxy(name);
            if (m_botAction.getOperatorList().isModerator(name))
                if (message.startsWith("!move ") || message.startsWith("!bounce "))
                    cmd_move(name, message);
                else if (message.equalsIgnoreCase("!list"))
                    cmd_list(name);
        }
    }
    
    private void cmd_proxy(String name) {
        proxy = !proxy;
        m_botAction.sendSmartPrivateMessage(name, "Proxy kicking is: " + (proxy ? "DISABLED" : "ENABLED"));
    }
    
    private void cmd_banc(String name) {
        DEBUG = !DEBUG;
        if (DEBUG) {
            debugger = name;
            m_botAction.sendSmartPrivateMessage(name, "BanC debugger ENABLED and set you as debugger.");
        } else {
            debugger = null;
            m_botAction.sendSmartPrivateMessage(name, "BanC debugger DISABLED.");
        }
    }

    private void cmd_kicks(String name) {
        silentKicks = !silentKicks;
        m_botAction.sendSmartPrivateMessage(name, "Silent kicks are now " + (silentKicks ? "ENABLED." : "DISABLED."));
    }

    private void cmd_bancs(String name) {
        m_botAction.sendSmartPrivateMessage(name, "Current BanC lists");
        m_botAction.sendSmartPrivateMessage(name, " Silences:");
        for (BanC b : bancSilence.values())
            m_botAction.sendSmartPrivateMessage(name, "  " + b.getName() + " IP=" + (b.ip != null ? b.ip : "") + " MID=" + (b.mid != null ? b.mid : ""));
        m_botAction.sendSmartPrivateMessage(name, " Specs:");
        for (BanC b : bancSpec.values())
            m_botAction.sendSmartPrivateMessage(name, "  " + b.getName() + " IP=" + (b.ip != null ? b.ip : "") + " MID=" + (b.mid != null ? b.mid : ""));
        m_botAction.sendSmartPrivateMessage(name, " SuperSpecs:");
        for (BanC b : bancSuper.values())
            m_botAction.sendSmartPrivateMessage(name, "  " + b.getName() + " IP=" + (b.ip != null ? b.ip : "") + " MID=" + (b.mid != null ? b.mid : ""));
    }

    private void cmd_list(String name) {
        elapsed.list(name);
    }

    private void cmd_move(String name, String msg) {
        if (msg.length() > msg.indexOf(" ") + 1) {
            String p = m_botAction.getFuzzyPlayerName(msg.substring(msg.indexOf(" ") + 1));
            if (p != null) {
                if (isBanced(p)) {
                    sendIdler(p);
                    m_botAction.sendPrivateMessage(name, "Moving '" + p + "' to afk");
                } else
                    m_botAction.sendPrivateMessage(name, "'" + p + "' is not a BanC'd player");
            } else
                m_botAction.sendPrivateMessage(name, "Player not found");
        }
    }

    private void checkBanCs(String info) {
        final String name = getInfo(info, "TypedName:");
        String ip = getInfo(info, "IP:");
        String mid = getInfo(info, "MachineId:");

        if (name.length() > MAX_NAME_LENGTH) {
            Player p = m_botAction.getPlayer(name.substring(0, MAX_NAME_LENGTH));
            if (p != null) {
                final int id = p.getPlayerID();
                TimerTask kick = new TimerTask() {
                    public void run() {
                        m_botAction.sendPrivateMessage(id, "You have been kicked from the server! Names containing more than 19 characters are no longer allowed in SSCU Trench Wars.");
                        m_botAction.sendUnfilteredPrivateMessage(id, "*kill");
                        if (!silentKicks)
                            m_botAction.ipcSendMessage(IPCBANC, "KICKED:Player '" + name + "' has been kicked by " + m_botAction.getBotName() + " for having a name greater than " + MAX_NAME_LENGTH + " characters.", "banc", m_botAction.getBotName());

                    }
                };
                m_botAction.scheduleTask(kick, 3200);
                return;
            }
        }

        if (bancSilence.containsKey(name)) {
            actions.add(bancSilence.get(name).reset());
            elapsed.add(name, bancSilence.get(name));
        } else {
            for (BanC b : bancSilence.values())
                if (b.isMatch(name, ip, mid)) {
                    actions.add(b);
                    elapsed.add(name, b);
                }
        }

        if (bancSpec.containsKey(name)) {
            actions.add(bancSpec.get(name).reset());
            elapsed.add(name, bancSpec.get(name));
        } else {
            for (BanC b : bancSpec.values())
                if (b.isMatch(name, ip, mid)) {
                    actions.add(b);
                    elapsed.add(name, b);
                }
        }

        if (bancSuper.containsKey(name)) {
            actions.add(bancSuper.get(name).reset());
            elapsed.add(name, bancSuper.get(name));
        } else {
            for (BanC b : bancSuper.values())
                if (b.isMatch(name, ip, mid)) {
                    actions.add(b);
                    elapsed.add(name, b);
                }
        }
    }

    private void handleBanC(BanC b) {
        String target = getTarget(b);
        switch (b.getType()) {
            case SILENCE:
                bancSilence.put(b.getName(), b);
                break;
            case SPEC:
                bancSpec.put(b.getName(), b);
                break;
            case SUPERSPEC:
                bancSuper.put(b.getName(), b);
                break;
        }
        if (target != null) {
            actions.add(b);
            elapsed.add(target, b);
        }
    }

    private void handleSuper(BanC b, int shipNumber) {
        if (!m_botAction.getArenaName().startsWith("(Public "))
            return;
        if (shipNumber == 2 || shipNumber == 8 || shipNumber == 4) {
            try {
                if (b != null) {
                    m_botAction.sendPrivateMessage(b.getName(), "You're banned from ship" + shipNumber + " with " + b.getRemaining() + " minutes remaining.");
                    m_botAction.sendPrivateMessage(b.getName(), "You've been put in spider. But you can change to: warbird(1), spider(3), weasel(6) or lancaster(7).");
                    m_botAction.setShip(b.getName(), 3);
                }
            } catch (NullPointerException e) {
                Tools.printStackTrace(e);
                return;
            }
        }
    }

    private void handleRemove(String cmd) {
        String[] args = cmd.split(":");
        BanC b = null;
        if (args[1].equals(BanCType.SILENCE.toString())) {
            if (bancSilence.containsKey(args[2]))
                b = bancSilence.remove(args[2]);
        } else if (args[1].equals(BanCType.SPEC.toString())) {
            if (bancSpec.containsKey(args[2]))
                b = bancSpec.remove(args[2]);
        } else if (args[1].equals(BanCType.SUPERSPEC.toString())) {
            if (bancSuper.containsKey(args[2]))
                b = bancSuper.remove(args[2]);
        } else
            return;

        String name = getTarget(b);
        if (name != null && b != null) {
            b.active = false;
            actions.add(b);
        }

        elapsed.rem(args[2]);
    }

    private void sendIdler(String name) {
        String MOVE_MESSAGE = "You've been moved to the away-from-keyboard subarena - 'afk'. Type \"?go\" to return.";
        m_botAction.sendPrivateMessage(name, MOVE_MESSAGE);
        m_botAction.sendUnfilteredPrivateMessage(name, sendto);
        debug("Bounced: " + name);
    }

    private String getTarget(BanC b) {
        if (b == null)
            return null;
        String target = m_botAction.getFuzzyPlayerName(b.getName());
        if (target != null && target.equalsIgnoreCase(b.getName()))
            b.name = target;
        else
            target = null;
        return target;
    }

    private boolean isBanced(String name) {
        return bancSilence.containsKey(name) || bancSpec.containsKey(name) || bancSuper.containsKey(name);
    }

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
    
    void debug(String msg) {
        if (DEBUG) 
            m_botAction.sendSmartPrivateMessage(debugger, "[BanC] " + msg);
    }

    class Action extends TimerTask {
        @Override
        public void run() {
            if (!actions.isEmpty()) {
                current = actions.remove(0);
                if (current instanceof BanC) {
                    BanC curr = (BanC) current;
                    switch (curr.getType()) {
                        case SILENCE:
                            m_botAction.sendUnfilteredPrivateMessage(curr.getName(), "*shutup");
                            break;
                        case SPEC:
                            m_botAction.spec(curr.getName());
                            break;
                        case SUPERSPEC:
                            Player p = m_botAction.getPlayer(curr.getName());
                            if (p != null)
                                handleSuper(curr, p.getShipType());
                            break;
                    }
                } else if (current instanceof String)
                    m_botAction.sendUnfilteredPrivateMessage((String) current, "*einfo");
            }
        }
    }

    class SendElapsed extends TimerTask {

        TreeMap<String, BanC> silence;
        TreeMap<String, BanC> ship;
        TreeMap<String, BanC> spec;
        TreeSet<String> updated;

        public SendElapsed() {
            updated = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            silence = new TreeMap<String, BanC>(String.CASE_INSENSITIVE_ORDER);
            ship = new TreeMap<String, BanC>(String.CASE_INSENSITIVE_ORDER);
            spec = new TreeMap<String, BanC>(String.CASE_INSENSITIVE_ORDER);
        }

        public void run() {
            updated.clear();
            for (Entry<String, BanC> e : silence.entrySet()) {
                if (!updated.contains(e.getKey())) {
                    e.getValue().sendUpdate(true);
                    actions.add(e.getKey());
                    updated.add(e.getKey());
                } else
                    e.getValue().sendUpdate(false);
            }
            for (Entry<String, BanC> e : ship.entrySet()) {
                if (!updated.contains(e.getKey())) {
                    e.getValue().sendUpdate(true);
                    actions.add(e.getKey());
                    updated.add(e.getKey());
                } else
                    e.getValue().sendUpdate(false);
            }
            for (Entry<String, BanC> e : spec.entrySet()) {
                if (!updated.contains(e.getKey())) {
                    e.getValue().sendUpdate(true);
                    actions.add(e.getKey());
                    updated.add(e.getKey());
                } else
                    e.getValue().sendUpdate(false);
            }
        }

        public void list(String name) {
            m_botAction.sendSmartPrivateMessage(name, "Silences: ");
            for (Entry<String, BanC> e : silence.entrySet())
                m_botAction.sendSmartPrivateMessage(name, "  " + e.getKey() + "(" + e.getValue().getElapsed() + ")");
            m_botAction.sendSmartPrivateMessage(name, "SuperSpecs: ");
            for (Entry<String, BanC> e : ship.entrySet())
                m_botAction.sendSmartPrivateMessage(name, "  " + e.getKey() + "(" + e.getValue().getElapsed() + ")");
            m_botAction.sendSmartPrivateMessage(name, "Specs: ");
            for (Entry<String, BanC> e : spec.entrySet())
                m_botAction.sendSmartPrivateMessage(name, "  " + e.getKey() + "(" + e.getValue().getElapsed() + ")");
        }

        public void handleIdle(String msg) {
            String name = msg.substring(0, msg.indexOf(":"));
            if (!isBanced(name)) return;
            int sec = 0;
            try {
                sec = Integer.valueOf(msg.substring(msg.indexOf("Idle:") + 6, msg.lastIndexOf("s")-1));
            } catch (NumberFormatException e) {
                Tools.printStackTrace(e);
                return;
            }
            if (sec > MAX_IDLE_TIME * 60)
                sendIdler(name);
        }

        public void add(String name, BanC banc) {
            switch (banc.getType()) {
                case SILENCE: 
                    if (!silence.containsKey(name)) {
                        silence.put(name, banc); 
                        banc.setActive(true);
                        debug("Elapser added: " + name);
                    }
                    break;
                case SUPERSPEC: 
                    if (!ship.containsKey(name)) {
                        ship.put(name, banc); 
                        banc.setActive(true);
                        debug("Elapser added: " + name);
                    }
                    break;
                case SPEC: 
                    if (!spec.containsKey(name)) {
                        spec.put(name, banc); 
                        banc.setActive(true);
                        debug("Elapser added: " + name);
                    }
                    break;
            }
        }

        public void rem(String name) {
            if (silence.containsKey(name))
                silence.remove(name).setActive(false);
            if (ship.containsKey(name))
                ship.remove(name).setActive(false);
            if (spec.containsKey(name))
                spec.remove(name).setActive(false);
        }

        public void stop() {
            for (BanC b : silence.values())
                b.setActive(false);
            for (BanC b : spec.values())
                b.setActive(false);
            for (BanC b : ship.values())
                b.setActive(false);
        }
    }

    class BanC {

        String name, originalName;
        String ip;
        String mid;
        long time;
        BanCType type;
        boolean active;
        TreeSet<String> aliases;
        long lastUpdate;
        int elapsed;

        public BanC(String info) {
            // name:ip:mid:time
            String[] args = info.split(":");
            name = args[0];
            lastUpdate = System.currentTimeMillis();
            originalName = name;
            ip = args[1];
            if (ip.length() == 1)
                ip = null;
            mid = args[2];
            if (mid.length() == 1)
                mid = null;
            time = Long.valueOf(args[3]);

            if (args[4].equals("SILENCE"))
                type = BanCType.SILENCE;
            else if (args[4].equals("SPEC"))
                type = BanCType.SPEC;
            else
                type = BanCType.SUPERSPEC;
            elapsed = Integer.valueOf(args[5]);
            active = true;
            aliases = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            aliases.add(originalName);
        }

        public int getElapsed() {
            return (int) (System.currentTimeMillis() - lastUpdate) / Tools.TimeInMillis.MINUTE;
        }
        
        public String getRemaining() {
            return "" + (time - elapsed);
        }

        public String getCommand() {
            return "" + (!active ? "REMOVE:" : "") + type.toString() + ":" + name;
        }

        public String getName() {
            return name;
        }

        public String getIP() {
            return ip;
        }

        public String getMID() {
            return mid;
        }

        public long getTime() {
            return time;
        }

        public BanCType getType() {
            return type;
        }

        public boolean isActive() {
            return active;
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

        public boolean isMatch(String name) {
            if (aliases.contains(name)) {
                this.name = name;
                return true;
            } else
                return false;
        }

        public BanC reset() {
            name = originalName;
            return this;
        }

        public void setActive(boolean a) {
            active = a;
            if (a)
                lastUpdate = System.currentTimeMillis();
            else
                sendUpdate(true);
        }

        public void sendUpdate(boolean ipc) {
            long now = System.currentTimeMillis();
            int mins = (int) (now - lastUpdate) / Tools.TimeInMillis.MINUTE;
            elapsed += mins;
            lastUpdate = now;
            if (ipc) {
                m_botAction.ipcSendMessage(IPCBANC, "ELAPSED:" + originalName + ":" + mins, null, m_botAction.getBotName());
                debug("Sending update: " + originalName + "(" + mins + ")");
            } else
                debug("Updated not sent: " + originalName + "(" + mins + ")");
        }
    }

}
