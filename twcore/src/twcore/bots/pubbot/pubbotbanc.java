package twcore.bots.pubbot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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

    private Set<String> hashSuperSpec;

    private String tempBanCCommand = null;
    private String tempBanCTime = null;
    private String tempBanCPlayer = null;

    public static final String IPCBANC = "banc";

    private final static String INFINTE_DURATION = "0";

    private Vector<String> IPCQueue = new Vector<String>();

    private TimerTask checkIPCQueue;
    private TimerTask initActiveBanCs;
    
    private HashMap<String, BanC> bancs;

    @Override
    public void initializeModule() {
        m_botAction.ipcSubscribe(IPCBANC);

        bancs = new HashMap<String, BanC>();
        // Request active BanCs from StaffBot
        initActiveBanCs = new TimerTask() {
            @Override
            public void run() {
                m_botAction.ipcSendMessage(IPCBANC, "BANC PUBBOT INIT", null, m_botAction.getBotName());
            }
        };
        m_botAction.scheduleTask(initActiveBanCs, 1000);

        checkIPCQueue = new TimerTask() {
            @Override
            public void run() {
                if (IPCQueue.size() != 0)
                    handleIPCMessage(IPCQueue.remove(0));
            }
        };
        m_botAction.scheduleTaskAtFixedRate(checkIPCQueue, 5 * Tools.TimeInMillis.SECOND, 5 * Tools.TimeInMillis.SECOND);
        hashSuperSpec = new HashSet<String>();

    }

    @Override
    public void cancel() {
        m_botAction.ipcUnSubscribe(IPCBANC);
        m_botAction.cancelTask(initActiveBanCs);
        m_botAction.cancelTask(checkIPCQueue);
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
    }

    @Override
    public void handleEvent(InterProcessEvent event) {
        // IPCMessage.recipient null		==> All pubbots
        // IPCMessage.recipient "PubBotX" 	==> Specific Pubbot X
        
        if (IPCBANC.equals(event.getChannel()) && event.getObject() instanceof IPCEvent) {
            IPCEvent ipc = (IPCEvent) event.getObject();
            int bot = ipc.getType();
            if (m_botAction.getBotName().startsWith("TW-Guard"))
                bot = Integer.valueOf(m_botAction.getBotName().substring(8));
            if (ipc.getType() < 0 || ipc.getType() == bot) {
                if (ipc.getList() instanceof ListIterator || !(ipc.getList() instanceof BanC)) {
                    m_botAction.sendPrivateMessage("WingZero", "Got list!");
                    @SuppressWarnings("unchecked")
                    ListIterator<String> i = (ListIterator<String>) ipc.getList();
                    if (bancs.isEmpty()) {
                        while (i.hasNext()) {
                            BanC b = new BanC(i.next());
                            m_botAction.sendPrivateMessage("WingZero", "Iterate banc: " + b.getPlayername());
                            if (b.getType() == BanCType.SILENCE)
                                bancs.put(low(b.getPlayername()), b);
                        }
                    } else {
                        @SuppressWarnings("unchecked")
                        HashMap<String, BanC> temps = (HashMap<String, BanC>) bancs.clone();
                        bancs.clear();
                        while (i.hasNext()) {
                            BanC b = new BanC(i.next());
                            m_botAction.sendPrivateMessage("WingZero", "Iterate banc: " + b.getPlayername());
                            if (b.getType() == BanCType.SILENCE) {
                                String name = b.getPlayername();
                                if (temps.containsKey(low(name))) {
                                    temps.remove(low(name));
                                    bancs.put(low(name), b);
                                }
                            }
                        }

                        for (BanC b : temps.values()) {
                            if (b.getType() == BanCType.SILENCE) {
                                tempBanCCommand = "REMOVE";
                                tempBanCPlayer = b.getPlayername();
                                m_botAction.sendUnfilteredPrivateMessage(b.getPlayername(), "*shutup");
                            }
                        }

                    }
                } else if (ipc.getList() instanceof BanC) {
                    m_botAction.sendSmartPrivateMessage("WingZero", "Got banc!");
                    BanC b = (BanC) ipc.getList();
                    if (b.getType() == BanCType.SILENCE)
                        bancs.put(low(b.getPlayername()), b);
                }
            }
        }
        if (IPCBANC.equals(event.getChannel())
                && event.getObject() != null
                && event.getObject() instanceof IPCMessage
                && ((IPCMessage) event.getObject()).getSender() != null
                && ((IPCMessage) event.getObject()).getSender().equalsIgnoreCase("banc")
                && (((IPCMessage) event.getObject()).getRecipient() == null 
                    || ((IPCMessage) event.getObject()).getRecipient().equalsIgnoreCase(m_botAction.getBotName()))) {

            IPCMessage ipc = (IPCMessage) event.getObject();
            String command = ipc.getMessage();

            // Are we still busy waiting for an answer?
            if (tempBanCCommand != null)
                IPCQueue.add(command);
            else
                handleIPCMessage(command);

        }
    }

    @Override
    public void handleEvent(PlayerEntered event) {
        try {
            String namePlayer = m_botAction.getPlayerName(event.getPlayerID());

            if (this.hashSuperSpec.contains(namePlayer.toLowerCase()))
                superLockMethod(namePlayer, event.getShipType());

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
    public void handleEvent(Message event) {
        String message = event.getMessage().trim();
        if (event.getMessageType() == Message.ARENA_MESSAGE) {
            if (message.startsWith("IP"))
                checkBanCs(message);
            
            if (tempBanCCommand != null) {
                // <player> can now speak
                if (message.equalsIgnoreCase(tempBanCPlayer + " can now speak")) {

                    if (tempBanCCommand.startsWith("REMOVE")) {
                        // The bot just unsilenced the player (and it's ok)
                        m_botAction.sendPrivateMessage(tempBanCPlayer, "Silence lifted. You can now speak.");
                        m_botAction.ipcSendMessage(IPCBANC, tempBanCCommand + " " + tempBanCPlayer, "banc", m_botAction.getBotName());
                        tempBanCCommand = null;
                        tempBanCPlayer = null;
                    } else
                        // The bot just unsilenced the player, while the player should be silenced.
                        m_botAction.sendUnfilteredPrivateMessage(tempBanCPlayer, "*shutup");
                } else if (message.equalsIgnoreCase(tempBanCPlayer + " has been silenced")) {

                    if (tempBanCCommand.startsWith("REMOVE"))
                        // The bot just silenced the player, while the player should be unsilenced.
                        m_botAction.sendUnfilteredPrivateMessage(tempBanCPlayer, "*shutup");
                    else {
                        // The bot just silenced the player (and it's ok)
                        if (tempBanCTime.equals(INFINTE_DURATION))
                            m_botAction.sendPrivateMessage(tempBanCPlayer, "You've been permanently silenced because of abuse and/or violation of Trench Wars rules.");
                        else
                            m_botAction.sendPrivateMessage(tempBanCPlayer, "You've been silenced for " + tempBanCTime
                                    + " minutes because of abuse and/or violation of Trench Wars rules.");

                        m_botAction.ipcSendMessage(IPCBANC, tempBanCCommand + " " + tempBanCPlayer, "banc", m_botAction.getBotName());
                        tempBanCCommand = null;
                        tempBanCPlayer = null;
                    }
                } else if (message.equalsIgnoreCase("Player locked in spectator mode")) {

                    if (tempBanCCommand.startsWith("REMOVE"))
                        // The bot just spec-locked the player, while the player shouldn't be spec-locked.
                        m_botAction.spec(tempBanCPlayer);
                    else {
                        // The bot just spec-locked the player (and it's ok)
                        if (tempBanCTime.equals(INFINTE_DURATION))
                            m_botAction.sendPrivateMessage(tempBanCPlayer, "You've been permanently locked into spectator because of abuse and/or violation of Trench Wars rules.");
                        else
                            m_botAction.sendPrivateMessage(tempBanCPlayer, "You've been locked into spectator for " + tempBanCTime
                                    + " minutes because of abuse and/or violation of Trench Wars rules.");

                        m_botAction.ipcSendMessage(IPCBANC, tempBanCCommand + " " + tempBanCPlayer, "banc", m_botAction.getBotName());
                        tempBanCCommand = null;
                        tempBanCPlayer = null;
                    }
                } else if (message.equalsIgnoreCase("Player free to enter arena")) {
                    if (tempBanCCommand.startsWith("REMOVE")) {
                        // The bot just unspec-locked the player (and it's ok)
                        m_botAction.sendPrivateMessage(tempBanCPlayer, "Spectator-lock removed. You may now enter.");
                        m_botAction.ipcSendMessage(IPCBANC, tempBanCCommand + " " + tempBanCPlayer, "banc", m_botAction.getBotName());
                        tempBanCCommand = null;
                        tempBanCPlayer = null;
                    } else // The bot just unspec-locked the player, while the player should be spec-locked.
                        m_botAction.spec(tempBanCPlayer);
                } else if (message.equalsIgnoreCase("Player kicked off")) {
                    m_botAction.ipcSendMessage(IPCBANC, tempBanCCommand + " " + tempBanCPlayer, "banc", m_botAction.getBotName());
                    tempBanCCommand = null;
                    tempBanCPlayer = null;
                } else if (message.startsWith("REMOVE")) {
                    String playerName = message.substring(17);
                    this.hashSuperSpec.remove(playerName.toLowerCase());
                    m_botAction.sendChatMessage("Player " + playerName + " may now play in bombs-ship");
                }

                if (tempBanCCommand == null && IPCQueue.size() != 0)
                    handleIPCMessage(IPCQueue.remove(0));
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
        for (BanC b : bancs.values())
            m_botAction.sendSmartPrivateMessage(name, " " + b.getPlayername());
    }
    
    private void checkBanCs(String info) {
        String name = getInfo(info, "TypedName:");
        String ip = getInfo(info, "IP:");
        String mid = getInfo(info, "MachineId:");
        
        BanC banc = null;
        if (bancs.containsKey(low(name)))
            banc = bancs.get(low(name));
        else {
            for (BanC b : bancs.values()) {
                if (b.getType() == BanCType.SILENCE && (ip.equals(b.getIP()) || mid.equals(b.getMID()))) {
                    banc = b;
                    break;
                }
            }
        }
        if (banc != null && banc.getType() == BanCType.SILENCE) {
            tempBanCCommand = banc.getType().toString();
            tempBanCPlayer = m_botAction.getFuzzyPlayerName(name);
            if (tempBanCPlayer == null)
                tempBanCPlayer = name;
            m_botAction.sendUnfilteredPrivateMessage(name, "*shutup");
        }
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

    private void handleIPCMessage(String command) {
        if (command.startsWith(BanCType.SILENCE.toString())) {
            // silence player in arena
            tempBanCCommand = BanCType.SILENCE.toString();
            tempBanCTime = command.substring(8).split(":")[0];
            tempBanCPlayer = command.substring(8).split(":")[1];
            m_botAction.sendUnfilteredPrivateMessage(tempBanCPlayer, "*shutup");
        } else if (command.startsWith("REMOVE " + BanCType.SILENCE.toString())) {
            // unsilence player in arena
            tempBanCCommand = "REMOVE " + BanCType.SILENCE.toString();
            tempBanCTime = null;
            tempBanCPlayer = command.substring(15);
            bancs.remove(low(tempBanCPlayer));
            m_botAction.sendUnfilteredPrivateMessage(tempBanCPlayer, "*shutup");
        } else if (command.startsWith(BanCType.SPEC.toString())) {
            // speclock player in arena
            tempBanCCommand = BanCType.SPEC.toString();
            tempBanCTime = command.substring(5).split(":")[0];
            tempBanCPlayer = command.substring(5).split(":")[1];
            m_botAction.spec(tempBanCPlayer);
        } else if (command.startsWith(BanCType.SUPERSPEC.toString())) {
            //superspec lock player in arena
            handleSuperSpec(command);
            tempBanCCommand = BanCType.SUPERSPEC.toString();
            //!spec player:time
            //SPEC time:target
            //SUPERSPEC time:target
            tempBanCTime = command.substring(10).split(":")[0];
            handleSuperSpec(command);
            //tempBanCPlayer = command.substring(10).split(":")[1];
            m_botAction.setShip(tempBanCPlayer, 3);
        } else if (command.startsWith("REMOVE " + BanCType.SPEC.toString())) {
            // remove speclock of player in arena
            tempBanCCommand = "REMOVE " + BanCType.SPEC.toString();
            tempBanCTime = null;
            tempBanCPlayer = command.substring(12);
            m_botAction.ipcSendMessage(IPCBANC, "remspec " + tempBanCPlayer, null, null);
            m_botAction.spec(tempBanCPlayer);
            //need to make remove for super spec
            //REMOVE SPEC PLAYER
        } else if (command.startsWith("REMOVE " + BanCType.SUPERSPEC.toString())) {
            tempBanCCommand = "REMOVE " + BanCType.SUPERSPEC.toString();
            hashSuperSpec.remove(command.substring(17).toLowerCase());
            tempBanCTime = null;
            //REMOVE a
            //REMOVE SUPERSPEC PLAYER
            tempBanCPlayer = command.substring(17);
            //maybe pm the player here?
        }
    }

    private void handleSuperSpec(String command) {
        // TODO Auto-generated method stub
        //SUPERSPEC TIME:OLDNICK:NEWNICK
        String cmdSplit[] = command.split(":");
        if (cmdSplit.length == 3) {
            String oldNickString = cmdSplit[1].toLowerCase();
            String newNickString = cmdSplit[2].toLowerCase();
            this.tempBanCPlayer = newNickString;
            if (!newNickString.equals(oldNickString)) {
                this.hashSuperSpec.add(newNickString);
                this.hashSuperSpec.remove(oldNickString);
            }
        } else {
            this.tempBanCPlayer = cmdSplit[1].toLowerCase();
            //if(!this.hashSuperSpec.contains(cmdSplit[1]))
            this.hashSuperSpec.add(cmdSplit[1].toLowerCase());
        }
    }
    
    class BanC {
        
        String name;
        String ip;
        String mid;
        BanCType type;
        
        public BanC(String info) {
            // name:ip:mid
            String[] args = info.split(":");
            name = args[0];
            ip = args[1];
            mid = args[2];
            if (args[3].equals("SILENCE"))
                type = BanCType.SILENCE;
            else
                type = BanCType.SUPERSPEC;
        }
        
        public String getPlayername() {
            return name;
        }
        
        public String getIP() {
            return ip;
        }
        
        public String getMID() {
            return mid;
        }
        
        public BanCType getType() {
            return type;
        }
    }

}
