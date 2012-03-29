package twcore.bots.pubbot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Set;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.PubBotModule;
import twcore.bots.staffbot.staffbot_banc.BanCType;
import twcore.core.EventRequester;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
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

    private Set<String> hashSuperSpec;

    public static final String IPCBANC = "banc";

    private final static long INFINTE_DURATION = 0;

    private TimerTask initActiveBanCs;
    private Action act;
    
    private HashMap<String, BanC> bancSilence;
    private HashMap<String, BanC> bancSpec;
    private HashMap<String, BanC> bancSuper;
    private Vector<BanC>          actions;
    
    private BanC current;

    @Override
    public void initializeModule() {
        m_botAction.ipcSubscribe(IPCBANC);

        hashSuperSpec = new HashSet<String>();
        
        bancSilence = new HashMap<String, BanC>();
        bancSpec = new HashMap<String, BanC>();
        bancSuper = new HashMap<String, BanC>();
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
        eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
    }

    @Override
    public void handleEvent(PlayerEntered event) {
        try {
            String name = event.getPlayerName();
            if (name == null)
                name = m_botAction.getPlayerName(event.getPlayerID());

            String playerName = event.getPlayerName();
            if (playerName == null)
                playerName = m_botAction.getPlayerName(event.getPlayerID());
            if (playerName.startsWith("^") == false) {
                m_botAction.sendUnfilteredPrivateMessage(playerName, "*info");
            }
            /*
            if (this.hashSuperSpec.contains(name.toLowerCase()))
                superLockMethod(name, event.getShipType());
                
            */

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleEvent(FrequencyShipChange event) {
        try {
            String namePlayer = m_botAction.getPlayerName(event.getPlayerID());

            if (this.hashSuperSpec.contains(namePlayer.toLowerCase()))
                superLockMethod(namePlayer, event.getShipType());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleEvent(InterProcessEvent event) {
        if (IPCBANC.equals(event.getChannel()) && event.getObject() instanceof IPCEvent) {
            IPCEvent ipc = (IPCEvent) event.getObject();
            int bot = ipc.getType();
            if (m_botAction.getBotName().startsWith("TW-Guard"))
                bot = Integer.valueOf(m_botAction.getBotName().substring(8));
            if (ipc.getType() < 0 || ipc.getType() == bot) {
                if (ipc.isAll()) {
                    @SuppressWarnings("unchecked")
                    ListIterator<String> i = (ListIterator<String>) ipc.getList();
                    while (i.hasNext()) {
                        BanC b = new BanC(i.next());
                        handleBanC(b);
                    }
                } else
                    handleBanC(new BanC(ipc.getName()));
            }
        } else if (IPCBANC.equals(event.getChannel())
                && event.getObject() != null
                && event.getObject() instanceof IPCMessage
                && ((IPCMessage) event.getObject()).getSender() != null && ((IPCMessage) event.getObject()).getSender().equalsIgnoreCase("banc")
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
        if (event.getMessageType() == Message.ARENA_MESSAGE) {
            if (message.startsWith("IP:"))
                checkBanCs(message);
            else if (current != null) {
                if (message.equalsIgnoreCase(current.getName() + " can now speak")) {
                    if (!current.isActive()) {
                        // The bot just unsilenced the player (and it's ok)
                        m_botAction.sendPrivateMessage(current.getName(), "Silence lifted. You can now speak.");
                        m_botAction.ipcSendMessage(IPCBANC, current.getCommand(), "banc", m_botAction.getBotName());
                        
                    } else
                        // The bot just unsilenced the player, while the player should be silenced.
                        m_botAction.sendUnfilteredPrivateMessage(current.getName(), "*shutup");
                } else if (message.equalsIgnoreCase(current.getName() + " has been silenced")) {
                    if (!current.isActive())
                        // The bot just silenced the player, while the player should be unsilenced.
                        m_botAction.sendUnfilteredPrivateMessage(current.getName(), "*shutup");
                    else {
                        // The bot just silenced the player (and it's ok)
                        if (current.getTime() == INFINTE_DURATION)
                            m_botAction.sendPrivateMessage(current.getName(), "You've been permanently silenced because of abuse and/or violation of Trench Wars rules.");
                        else
                            m_botAction.sendPrivateMessage(current.getName(), "You've been silenced for " + current.getTime() + " minutes because of abuse and/or violation of Trench Wars rules.");
                        m_botAction.ipcSendMessage(IPCBANC, current.getCommand(), "banc", m_botAction.getBotName());
                        
                    }
                } else if (message.equalsIgnoreCase("Player locked in spectator mode")) {
                    if (!current.isActive())
                        // The bot just spec-locked the player, while the player shouldn't be spec-locked.
                        m_botAction.spec(current.getName());
                    else {
                        // The bot just spec-locked the player (and it's ok)
                        if (current.getTime() == INFINTE_DURATION)
                            m_botAction.sendPrivateMessage(current.getName(), "You've been permanently locked into spectator because of abuse and/or violation of Trench Wars rules.");
                        else
                            m_botAction.sendPrivateMessage(current.getName(), "You've been locked into spectator for " + current.getTime() + " minutes because of abuse and/or violation of Trench Wars rules.");
                        m_botAction.ipcSendMessage(IPCBANC, current.getCommand(), "banc", m_botAction.getBotName());
                        
                    }
                } else if (message.equalsIgnoreCase("Player free to enter arena")) {
                    if (!current.isActive()) {
                        // The bot just unspec-locked the player (and it's ok)
                        m_botAction.sendPrivateMessage(current.getName(), "Spectator-lock removed. You may now enter.");
                        m_botAction.ipcSendMessage(IPCBANC, current.getCommand(), "banc", m_botAction.getBotName());
                        
                    } else // The bot just unspec-locked the player, while the player should be spec-locked.
                        m_botAction.spec(current.getName());
                } else if (message.equalsIgnoreCase("Player kicked off")) {
                    m_botAction.ipcSendMessage(IPCBANC, current.getCommand(), "banc", m_botAction.getBotName());
                    
                } else if (message.startsWith("REMOVE")) {
                    String playerName = message.substring(17);
                    this.hashSuperSpec.remove(playerName.toLowerCase());
                    m_botAction.sendChatMessage("Player " + playerName + " may now play in bombs-ship");
                }
            }
        }
        
        if (event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) {
            String name = event.getMessager();
            if (name == null)
                name = m_botAction.getPlayerName(event.getPlayerID());
            if (m_botAction.getOperatorList().isSysop(name))
                if (message.equals("!bancs"))
                    cmd_bancs(name);
        }
    }
    
    private void cmd_bancs(String name) {
        m_botAction.sendSmartPrivateMessage(name, "Current BanC list:");
        for (BanC b : bancSilence.values())
            m_botAction.sendSmartPrivateMessage(name, " " + b.getName());
    }
    
    private void checkBanCs(String info) {
        String name = getInfo(info, "TypedName:");
        String ip = getInfo(info, "IP:");
        String mid = getInfo(info, "MachineId:");
        
        BanC banc = null;
        if (bancSilence.containsKey(low(name)))
            banc = bancSilence.get(low(name));
        else if (bancSpec.containsKey(low(name)))
            banc = bancSpec.get(low(name));
        else if (bancSuper.containsKey(low(name)))
            banc = bancSuper.get(low(name));
        else {
            for (BanC b : bancSilence.values()) {
                if (b.getIP() != null && ip.equals(b.getIP())) {
                    banc = b;
                    break;
                } else if (b.getMID() != null && mid.equals(b.getMID())) {
                    banc = b;
                    break;
                } else if (b.getIP() != null && b.getMID() == null && ip.equals(b.getIP())) {
                    banc = b;
                    break;
                } else if (b.getMID() != null && b.getIP() == null && mid.equals(b.getMID())) {
                    banc = b;
                    break;
                }
            }
        }
        if (banc != null)
            actions.add(banc);
        
        m_botAction.ipcSendMessage(getIPCChannel(), "info " + name + ":" + ip + ":" + mid, getPubHubName(), "pubbotalias");
        m_botAction.ipcSendMessage("TWDOp Alias", "info " + name + ":" + ip + ":" + mid, "TWDOpBot", "pubbotalias");   
    }
    
    private String low(String msg) {
        return msg.toLowerCase();
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

    private void superLockMethod(String namePlayer, int shipNumber) {
        if (!m_botAction.getArenaName().startsWith("(Public "))
            return;
        if (shipNumber == 2 || shipNumber == 8 || shipNumber == 4) {
            m_botAction.sendPrivateMessage(namePlayer, "You're banned from ship" + shipNumber);
            m_botAction.sendPrivateMessage(namePlayer, "You'be been put in spider. But you can change to: warbird(1), spider(3), weasel(6) or lancaster(7).");
            m_botAction.setShip(namePlayer, 3);
        }
    }
    
    private void handleBanC(BanC b) {

        String target = m_botAction.getFuzzyPlayerName(b.getName());
        switch (b.getType()) {
            case SILENCE: 
                bancSilence.put(low(b.getName()), b);
                if (target != null && target.equalsIgnoreCase(b.getName())) {
                    b.name = target;
                    if (current != null) {
                        actions.add(b);
                    } else {
                        current = b;
                        m_botAction.sendUnfilteredPrivateMessage(b.getName(), "*shutup");
                    }
                }
                break;
            case SPEC:
                bancSpec.put(low(b.getName()), b);
                if (target != null && target.equalsIgnoreCase(b.getName())) {
                    b.name = target;
                    if (m_botAction.getPlayer(target).getShipType() > 0) {
                        if (current != null) {
                            actions.add(b);
                        } else {
                            current = b;
                            m_botAction.spec(b.getName());
                            m_botAction.spec(b.getName());
                        }
                    }
                }
                break;
            case SUPERSPEC: 
                bancSuper.put(low(b.getName()), b);
                if (target != null && target.equalsIgnoreCase(b.getName())) {
                    b.name = target;
                    int s = m_botAction.getPlayer(target).getShipType();
                    if (s == 2 || s == 4 || s == 8) {
                        if (current != null) {
                            actions.add(b);
                        } else {
                            m_botAction.setShip(b.getName(), 3);
                        }
                    }
                }
                break;
        }
    }
    
    private void handleRemove(String cmd) {
        String[] args = cmd.split(" ");
        BanC b = null;
        if (args[1].equals(BanCType.SILENCE.toString())) {
            if (bancSilence.containsKey(low(args[2])))
                b = bancSilence.remove(low(args[2]));
        } else if (args[1].equals(BanCType.SPEC.toString())) {
            if (bancSpec.containsKey(low(args[2])))
                b = bancSpec.remove(low(args[2]));
        } else if (args[1].equals(BanCType.SUPERSPEC.toString())) {
            if (bancSuper.containsKey(low(args[2])))
                b = bancSuper.remove(low(args[2]));
        } else return;
        
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

    /*
    private void handleIPCMessage(String command) {
        if (command.startsWith(BanCType.SILENCE.toString())) {
            // silence player in arena
            bancCommand = BanCType.SILENCE.toString();
            bancTime = command.substring(8).split(":")[0];
            bancPlayer = command.substring(8).split(":")[1];
            m_botAction.sendUnfilteredPrivateMessage(bancPlayer, "*shutup");
        } else if (command.startsWith("REMOVE " + BanCType.SILENCE.toString())) {
            // unsilence player in arena
            bancCommand = "REMOVE " + BanCType.SILENCE.toString();
            bancTime = null;
            bancPlayer = command.substring(15);
            bancSilence.remove(low(bancPlayer));
            m_botAction.sendUnfilteredPrivateMessage(bancPlayer, "*shutup");
        } else if (command.startsWith(BanCType.SPEC.toString())) {
            // speclock player in arena
            bancCommand = BanCType.SPEC.toString();
            bancTime = command.substring(5).split(":")[0];
            bancPlayer = command.substring(5).split(":")[1];
            m_botAction.spec(bancPlayer);
        } else if (command.startsWith(BanCType.SUPERSPEC.toString())) {
            //superspec lock player in arena
            handleSuperSpec(command);
            bancCommand = BanCType.SUPERSPEC.toString();
            //!spec player:time
            //SPEC time:target
            //SUPERSPEC time:target
            bancTime = command.substring(10).split(":")[0];
            handleSuperSpec(command);
            //tempBanCPlayer = command.substring(10).split(":")[1];
            m_botAction.setShip(bancPlayer, 3);
        } else if (command.startsWith("REMOVE " + BanCType.SPEC.toString())) {
            // remove speclock of player in arena
            bancCommand = "REMOVE " + BanCType.SPEC.toString();
            bancTime = null;
            bancPlayer = command.substring(12);
            m_botAction.ipcSendMessage(IPCBANC, "remspec " + bancPlayer, null, null);
            m_botAction.spec(bancPlayer);
            //need to make remove for super spec
            //REMOVE SPEC PLAYER
        } else if (command.startsWith("REMOVE " + BanCType.SUPERSPEC.toString())) {
            bancCommand = "REMOVE " + BanCType.SUPERSPEC.toString();
            hashSuperSpec.remove(command.substring(17).toLowerCase());
            bancTime = null;
            //REMOVE a
            //REMOVE SUPERSPEC PLAYER
            bancPlayer = command.substring(17);
            //maybe pm the player here?
        }
    }
    */
    
    class BanC {
        
        String name;
        String ip;
        String mid;
        long time;
        BanCType type;
        boolean active;
        
        public BanC(String info) {
            // name:ip:mid:time
            String[] args = info.split(":");
            name = args[0];
            ip = args[1];
            mid = args[2];
            time = Long.valueOf(args[3]);
            
            if (args[4].equals("SILENCE"))
                type = BanCType.SILENCE;
            else if (args[4].equals("SPEC"))
                type = BanCType.SPEC;
            else
                type = BanCType.SUPERSPEC;
            active = true;
        }
        
        public String getCommand() {
            return "" + (!active ? "REMOVE " : "") + type.toString() + " " + name;
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
    }
    
    class Action extends TimerTask {
        @Override
        public void run() {
            if (!actions.isEmpty()) {
                current = actions.remove(0);
                if (current.isActive()) {
                    switch (current.getType()) {
                        case SILENCE:
                            m_botAction.sendUnfilteredPrivateMessage(current.getName(), "*shutup");
                            break;
                        case SPEC:
                            m_botAction.spec(current.getName());
                            break;
                        case SUPERSPEC:
                            hashSuperSpec.remove(low(current.getName()));
                            break;
                    }
                }
            }
        }
    }

}
