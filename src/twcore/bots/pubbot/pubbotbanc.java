package twcore.bots.pubbot;

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

    private static final long INFINTE_DURATION = 0;
    private static final int MAX_NAME_LENGTH = 19;
    private static final String db = "bots";

    private TimerTask initActiveBanCs;
    private Action act;

    private TreeMap<String, BanC> bancSilence;
    private TreeMap<String, BanC> bancSpec;
    private TreeMap<String, BanC> bancSuper;
    private Vector<BanC> actions;

    private BanC current;
    private boolean silentKicks;

    @Override
    public void initializeModule() {
        m_botAction.ipcSubscribe(IPCBANC);
        silentKicks = false;
        bancSilence = new TreeMap<String, BanC>(String.CASE_INSENSITIVE_ORDER);
        bancSpec = new TreeMap<String, BanC>(String.CASE_INSENSITIVE_ORDER);
        bancSuper = new TreeMap<String, BanC>(String.CASE_INSENSITIVE_ORDER);
        actions = new Vector<BanC>();
        
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
    }

    @Override
    public void cancel() {
        m_botAction.ipcUnSubscribe(IPCBANC);
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
            if (message.contains("Proxy: SOCKS5 proxy")) {
                String name = message.substring(0, message.indexOf(": U"));
                m_botAction.sendUnfilteredPrivateMessage(name, "*kill");
                m_botAction.ipcSendMessage(IPCBANC, "KICKED:Player '" + name + "' has been kicked by " + m_botAction.getBotName() + " for using an unapproved client.", "banc", m_botAction.getBotName());
            } else if (message.startsWith("IP:"))
                checkBanCs(message);
            else if (current != null) {
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
                        if (current.getTime() == INFINTE_DURATION)
                            m_botAction.sendPrivateMessage(current.getName(), "You've been permanently silenced because of abuse and/or violation of Trench Wars rules.");
                        else
                            m_botAction.sendPrivateMessage(current.getName(), "You've been silenced for " + current.getTime()
                                    + " minutes because of abuse and/or violation of Trench Wars rules.");
                        m_botAction.ipcSendMessage(IPCBANC, current.getCommand(), "banc", m_botAction.getBotName());
                    }
                } else if (message.equalsIgnoreCase("Player locked in spectator mode")) {
                    if (!current.isActive()) {
                        // The bot just spec-locked the player, while the player shouldn't be spec-locked.
                        current = null;
                        m_botAction.spec(name);
                    } else {
                        // The bot just spec-locked the player (and it's ok)
                        if (current.getTime() == INFINTE_DURATION)
                            m_botAction.sendPrivateMessage(current.getName(), "You've been permanently locked into spectator because of abuse and/or violation of Trench Wars rules.");
                        else
                            m_botAction.sendPrivateMessage(current.getName(), "You've been locked into spectator for " + current.getTime()
                                    + " minutes because of abuse and/or violation of Trench Wars rules.");
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
        
        if (bancSilence.containsKey(name))
            actions.add(bancSilence.get(name).reset());
        else
            for (BanC b : bancSilence.values())
                if (b.isMatch(name, ip, mid))
                    actions.add(b);

        if (bancSpec.containsKey(name))
            actions.add(bancSpec.get(name).reset());
        else
            for (BanC b : bancSpec.values())
                if (b.isMatch(name, ip, mid))
                    actions.add(b);

        if (bancSuper.containsKey(name))
            actions.add(bancSuper.get(name).reset());
        else
            for (BanC b : bancSuper.values())
                if (b.isMatch(name, ip, mid))
                    actions.add(b);
    }
    
    class SendElapsed extends TimerTask {
        
        public SendElapsed() {
            
        }
        
        public void run() {
            
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
        if (target != null)
            actions.add(b);
    }

    private void handleSuper(String namePlayer, int shipNumber) {
        if (!m_botAction.getArenaName().startsWith("(Public "))
            return;
        if (shipNumber == 2 || shipNumber == 8 || shipNumber == 4) {
            m_botAction.sendPrivateMessage(namePlayer, "You're banned from ship" + shipNumber);
            m_botAction.sendPrivateMessage(namePlayer, "You'be been put in spider. But you can change to: warbird(1), spider(3), weasel(6) or lancaster(7).");
            m_botAction.setShip(namePlayer, 3);
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
    }

    private String getTarget(BanC b) {
        String target = m_botAction.getFuzzyPlayerName(b.getName());
        if (target != null && target.equalsIgnoreCase(b.getName()))
            b.name = target;
        else
            target = null;
        return target;
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

    class Action extends TimerTask {
        @Override
        public void run() {
            if (!actions.isEmpty()) {
                current = actions.remove(0);
                switch (current.getType()) {
                    case SILENCE:
                        m_botAction.sendUnfilteredPrivateMessage(current.getName(), "*shutup");
                        break;
                    case SPEC:
                        m_botAction.spec(current.getName());
                        break;
                    case SUPERSPEC:
                        Player p = m_botAction.getPlayer(current.getName());
                        if (p != null)
                            handleSuper(current.getName(), p.getShipType());
                        break;
                }
            }
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

        public BanC(String info) {
            // name:ip:mid:time
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

            if (args[4].equals("SILENCE"))
                type = BanCType.SILENCE;
            else if (args[4].equals("SPEC"))
                type = BanCType.SPEC;
            else
                type = BanCType.SUPERSPEC;
            active = true;
            aliases = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
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
        
        void update() {
            long now = System.currentTimeMillis();
            int mins = (int) (now - lastUpdate) / Tools.TimeInMillis.MINUTE;
            lastUpdate = now;
            m_botAction.ipcSendMessage(IPCBANC, "ELAPSED:" + originalName + ":" + mins, null, m_botAction.getBotName());
        }
    }

}
