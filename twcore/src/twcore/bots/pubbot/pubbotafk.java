package twcore.bots.pubbot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.TreeMap;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.util.Tools;

/**
 * Move players to the AFK_ARENA when they are idle for too long. The bots checks their idle time each 5 minutes
 * 
 * @author fantus
 */
public class pubbotafk extends PubBotModule {

    private final static int WARNING_TIME = 28;     // (Time in minutes)
    private final static int MOVE_TIME = 2;         // Time the player gets to get active
                                                    // after the warning (Time in minutes)
    private final static String AFK_ARENA = "afk";  // Arena to where the players get moved
    private final static String WARNING_MESSAGE = "NOTICE: In order to keep the gameplay high in public arenas "
            + "being idle for too long is not allowed. If you intend to go away, please type \"?go " + AFK_ARENA + "\"." + " (If you stay inactive you will be moved to the subarena '" + AFK_ARENA
            + "' automatically.)";
    private final static String WARNING_MESSAGE2 = "To declare yourself not-idle, please talk in either public or team chat. " + "Private messages are ignored.";
    private final static String MOVE_MESSAGE = "You've been moved to the away-from-keyboard subarena - 'afk'. Type \"?go\" to return.";
    
    private boolean enabled;
    private boolean status;
    private int size = 20;

    private OperatorList opList;
    private String sendto;
    private TreeMap<String, Idler> players;
    
    TimerTask check;

    @Override
    public void initializeModule() {
        String zoneIP = m_botAction.getGeneralSettings().getString("Server");
        String zonePort = m_botAction.getGeneralSettings().getString("Port");
        sendto = "*sendto " + zoneIP + "," + zonePort + "," + AFK_ARENA;
        enabled = true;
        opList = m_botAction.getOperatorList();

        players = new TreeMap<String, Idler>();
        
        if (m_botAction.getArenaSize() < size)
            status = false;
        else
            status = true;
        checkStatus();
    }

    @Override
    public void cancel() {
        if (!enabled) return;
        players.clear();
        populateList();
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.ARENA_JOINED);
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.PLAYER_LEFT);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.FREQUENCY_CHANGE);
        eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
    }

    @Override
    public void handleEvent(ArenaJoined event) {
        populateList();
    }

    @Override
    public void handleEvent(FrequencyChange event) {
        if (!enabled) return;
        Player p = m_botAction.getPlayer(event.getPlayerID());
        String name = low(p.getPlayerName());
        if (players.containsKey(name) && p.getShipType() != Tools.Ship.SPECTATOR)
            players.remove(name);
        else if (players.containsKey(name))
            players.get(name).active();
        else if (p.getShipType() == Tools.Ship.SPECTATOR)
            addIdler(p);
    }

    @Override
    public void handleEvent(FrequencyShipChange event) {
        if (!enabled) return;
        Player p = m_botAction.getPlayer(event.getPlayerID());
        String name = low(p.getPlayerName());
        if (players.containsKey(name) && p.getShipType() != Tools.Ship.SPECTATOR)
            players.remove(name);
        else if (players.containsKey(name))
            players.get(name).active();
        else if (p.getShipType() == Tools.Ship.SPECTATOR)
            addIdler(p);
    }

    @Override
    public void handleEvent(PlayerEntered event) {
        if (!enabled) return;
        Player p = m_botAction.getPlayer(event.getPlayerID());
        if (p.getShipType() == Tools.Ship.SPECTATOR)
            addIdler(p);
    }

    @Override
    public void handleEvent(PlayerLeft event) {
        if (!enabled) return;
        checkStatus();
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (players.containsKey(low(name)))
            players.remove(low(name));
    }

    @Override
    public void handleEvent(Message event) {
        int type = event.getMessageType();
        String message = event.getMessage();
        String name = m_botAction.getPlayerName(event.getPlayerID());

        if (name != null)
            //Do not count private messages, because with ?away a player could fool the bot as if he was still here.
            if (players.containsKey(low(name)) && event.getMessageType() != Message.PRIVATE_MESSAGE)
                players.get(low(name)).active();

        if (type == Message.PRIVATE_MESSAGE || type == Message.REMOTE_PRIVATE_MESSAGE)
            if (opList.isModerator(name))
                if (message.startsWith("!listidle"))
                    cmd_listIdle(name);
                else if (message.startsWith("!move "))
                    cmd_moveIdle(name, message);
                else if (message.startsWith("!help"))
                    cmd_help(name);
                else if (message.startsWith("!status"))
                    cmd_status(name);
                else if (message.startsWith("!size"))
                    cmd_setSize(name, message);
                else if (message.startsWith("!setafk"))
                    cmd_setState(name);
    }
    
    private void populateList() {
        players.clear();
        for (Iterator<Player> it = m_botAction.getPlayerIterator(); it.hasNext();) {
            Player p = it.next();
            if (p.getShipType() == 0)
                addIdler(p);
        }
    }

    private void addIdler(Player p) {
        if (!opList.isBotExact(p.getPlayerName()) && !opList.isSysopExact(p.getPlayerName()) && p.getShipType() == Tools.Ship.SPECTATOR) {
            String n = p.getPlayerName();
            if (players.containsKey(low(n)))
                players.get(low(n)).active();
            else
                players.put(low(n), new Idler(n));
        }
    }

    private void checkStatus() {
        if (!enabled) return;
        if (status && m_botAction.getArenaSize() < size) {
            status = false;
            if (check != null) {
                m_botAction.cancelTask(check);
                check = null;
            }
        } else if (!status && m_botAction.getArenaSize() > size - 1) {
            status = true;
            populateList();
            if (check != null)
                return;
            
            check = new TimerTask() {
                @Override
                public void run() {
                    check();
                }
            };
            m_botAction.scheduleTaskAtFixedRate(check, 5000, Tools.TimeInMillis.MINUTE);
        }
    }

    private void check() {
        if (status && !players.isEmpty()) {
            for (Idler idler : players.values()) {
                idler.check();
            }
        }
    }

    private void cmd_help(String messager) {
        m_botAction.sendSmartPrivateMessage(messager, "Pubbot AFK Module");
        m_botAction.sendSmartPrivateMessage(messager, "!setafk        -- Toggles the AFK module on or off");
        m_botAction.sendSmartPrivateMessage(messager, "!listidle      -- Display spectating players and their idle times");
        m_botAction.sendSmartPrivateMessage(messager, "!move <player> -- Move <player> to ?go " + AFK_ARENA);
        m_botAction.sendSmartPrivateMessage(messager, "!size <number> -- Set the arena size trigger to <number>");
        m_botAction.sendSmartPrivateMessage(messager, "!status        -- Display the status of the AFK module");
    }

    private void cmd_listIdle(String messager) {
        ArrayList<String> out = new ArrayList<String>();
        out.add(Tools.formatString("<Name>", 23) + " - <idle time/mins>");
        for (Idler idler : players.values()) {
            long idleTime = idler.getIdleTime();
            String action = "";
            if (idleTime < WARNING_TIME)
                action += "Warning in " + (WARNING_TIME - idleTime) + " minute(s), ";
            if (idleTime < (WARNING_TIME + MOVE_TIME))
                action += "Moving in " + ((WARNING_TIME + MOVE_TIME) - idleTime) + " minute(s)";
            out.add(Tools.formatString(idler.name, 23) + " - " + Tools.formatString(String.valueOf(idleTime), 2) + "  " + action);
        }

        m_botAction.smartPrivateMessageSpam(messager, out.toArray(new String[out.size()]));
    }

    private void cmd_moveIdle(String messager, String message) {
        if (message.length() > 6) {
            String player = m_botAction.getFuzzyPlayerName(message.substring(6));
            if (player != null) {
                m_botAction.sendUnfilteredPrivateMessage(player, sendto);
                m_botAction.sendSmartPrivateMessage(messager, player + " has been moved to the afk arena.");
            } else
                m_botAction.sendSmartPrivateMessage(messager, "Unknown player");
        }
    }

    private void cmd_setSize(String name, String num) {
        int s = 0;
        try {
            s = Integer.valueOf(num.substring(6));
        } catch (NumberFormatException e) {
            return;
        }
        this.size = s;
        m_botAction.sendSmartPrivateMessage(name, "AFK population trigger size is now " + size);
        checkStatus();
    }

    private void cmd_status(String name) {
        if (status)
            m_botAction.sendSmartPrivateMessage(name, "AFK Status: ON & Size: " + size);
        else
            m_botAction.sendSmartPrivateMessage(name, "AFK Status: OFF & Size: " + size);
    }
    
    private void cmd_setState(String name) {
        enabled = !enabled;
        if (enabled) {
            players.clear();
            populateList();
            if (check != null)
                m_botAction.cancelTask(check);
                
            check = new TimerTask() {
                public void run() {
                    check();
                }
            };
            m_botAction.scheduleTask(check, 1000, Tools.TimeInMillis.MINUTE);
            m_botAction.sendSmartPrivateMessage(name, "AFK mover module has been ENABLED.");
        } else {
            players.clear();
            if (check != null) {
                m_botAction.cancelTask(check);
                check = null;
            }
            m_botAction.sendSmartPrivateMessage(name, "AFK mover module has been DISABLED.");
        }
    }
    
    class Idler {
        String name;
        long lastActive;
        boolean warned;
        
        public Idler(String n) {
            name = n;
            warned = false;
            lastActive = System.currentTimeMillis();
        }
        
        public void check() {
            long time = getIdleTime();
            if (time > WARNING_TIME)
                warn();
            if (time >= WARNING_TIME + MOVE_TIME) 
                send();
        }
        
        public void active() {
            lastActive = System.currentTimeMillis();
            warned = false;
        }
        
        public long getIdleTime() {
            return (System.currentTimeMillis() - lastActive) / Tools.TimeInMillis.MINUTE;
        }
        
        public void warn() {
            if (!warned) {
                m_botAction.sendPrivateMessage(name, WARNING_MESSAGE);
                m_botAction.sendPrivateMessage(name, WARNING_MESSAGE2);
                warned = true;
            }
        }
        
        public void send() {
            players.remove(low(name));
            m_botAction.sendPrivateMessage(name, MOVE_MESSAGE);
            m_botAction.sendUnfilteredPrivateMessage(name, sendto);
        }
        
    }

    private String low(String msg) {
        return msg.toLowerCase();
    }
}
