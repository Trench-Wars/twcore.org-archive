package twcore.bots.pubbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
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
 * Move players to the AFK_ARENA when they are idle for too long.
 * The bots checks their idle time each 5 minutes
 * 
 * @author fantus
 */
public class pubbotafk extends PubBotModule {
    // Current settings:
    // - Turn on when arena size is > 89
    // - Warn normal players after 8 minutes
    // - Kick normal players after +2 minutes (= 10 minutes)
    // - Warn staffers after 15 minutes
    // - Kick staffers after +5 minutes (= 20 minutes)
    
    private final static int WARNING_TIME = 28;      // (Time in minutes)
    private final static int MOVE_TIME = 2;         // Time the player gets to get active
                                                    // after the warning (Time in minutes)
    private final static int STAFF_WARNING_TIME = 40;
    private final static int STAFF_MOVE_TIME = 5;
    private final static String AFK_ARENA = "afk";  //Arena to where the players get moved
    private final static String WARNING_MESSAGE = "NOTICE: In order to keep the gameplay high in public arenas " +
    		"being idle for too long is not allowed. If you intend to go away, please type \"?go " + AFK_ARENA + "\"." +
    		" (If you stay inactive you will be moved to the subarena '" + AFK_ARENA + "' automatically.)";
    private final static String WARNING_MESSAGE2 = "To declare yourself not-idle, please talk in either public or team chat. " +
                                                    "Private messages are ignored.";
    private final static String MOVE_MESSAGE = "You've been moved to the away-from-keyboard subarena - 'afk'. Type \"?go\" to return.";
    private final static String STAFF_MOVED_WARNING = "REMINDER: You have been logged out of BanG and your ?obscene has been reset as a result of being moved.";
    
    private boolean status;
    private int size = 20;
    
    private OperatorList opList;
    private List<String> vipList;
    private String sendtoCmd;
    private TreeMap<String, Long> players;
   

    public void add(Player p) {
        if (!opList.isBotExact(p.getPlayerName()) && !opList.isSysopExact(p.getPlayerName()) && p.getShipType() == Tools.Ship.SPECTATOR)
             players.put(p.getPlayerName(), System.currentTimeMillis());
    }
    
    public void cancel() {
        players.clear();
    }

    public void check() {
        if (!players.isEmpty()) {
            for (String name : players.keySet()) {
            	if(vipList.contains(name))
            		continue;
                if (!opList.isZH(name)) {
                    
                    // Normal players
                    if (getIdleTime(name) >= (WARNING_TIME + MOVE_TIME)) {
                        m_botAction.sendPrivateMessage(name, MOVE_MESSAGE);
                        m_botAction.sendUnfilteredPrivateMessage(name, sendtoCmd);
                    } else if (getIdleTime(name) == WARNING_TIME) {
                        m_botAction.sendPrivateMessage(name, WARNING_MESSAGE);
                        m_botAction.sendPrivateMessage(name, WARNING_MESSAGE2);
                    }
                }
            }
        }
    }
    
    public void cmdHelp(String messager) {
        m_botAction.sendSmartPrivateMessage(messager, "Pubbot AFK Module");
        m_botAction.sendSmartPrivateMessage(messager,
                "!listidle      -- Display spectating players and their idle times");
        m_botAction.sendSmartPrivateMessage(messager,
                "!move <player> -- Move <player> to ?go " + AFK_ARENA);
        m_botAction.sendSmartPrivateMessage(messager,
                "!size <number> -- Set the arena size trigger to <number>");
        m_botAction.sendSmartPrivateMessage(messager,
                "!status        -- Display the status of the AFK module");
    }
    
    public void cmdListidle(String messager) {
        ArrayList<String> out = new ArrayList<String>();
        
        out.add(Tools.formatString("<Name>", 23) + " - <idle time/mins>");
        for (String name : players.keySet()) {
            long idleTime = getIdleTime(name);
            String action = "";
            
            if(opList.isER(name)) {
                // staffers
                if(idleTime < STAFF_WARNING_TIME) {
                    action += "Warning in " + (STAFF_WARNING_TIME - idleTime) + " minute(s), ";
                }
                if(idleTime < (STAFF_WARNING_TIME + STAFF_MOVE_TIME)) {
                    action += "Moving in " + ((STAFF_WARNING_TIME + STAFF_MOVE_TIME) - idleTime) + " minute(s)";
                }
            } else {
                // normal players
                if(idleTime < WARNING_TIME) {
                    action += "Warning in " + (WARNING_TIME - idleTime) + " minute(s), ";
                }
                if(idleTime < (WARNING_TIME + MOVE_TIME)) {
                    action += "Moving in " + ((WARNING_TIME + MOVE_TIME) - idleTime) + " minute(s)";
                }
            }
            out.add(Tools.formatString(name, 23) + " - " + Tools.formatString(String.valueOf(idleTime),2) + "  " + action);
        }
        
        m_botAction.smartPrivateMessageSpam(messager, out.toArray(new String[out.size()]));
    }
    
    public void cmdMoveidle(String messager, String message) {
        if (message.length() > 6) {
            String player = m_botAction.getFuzzyPlayerName(message.substring(6));
            
            if (player != null) {
                m_botAction.sendUnfilteredPrivateMessage(player, sendtoCmd);
                m_botAction.sendSmartPrivateMessage(messager, player + " has been moved to the afk arena.");
            } else
                m_botAction.sendSmartPrivateMessage(messager, "Unknown player");
        }
    }
    
    public long getIdleTime(String name) {
        return (System.currentTimeMillis() - players.get(name)) / Tools.TimeInMillis.MINUTE;
    }
    
    public void checkStatus() {
        if (status && m_botAction.getArenaSize() < size) {
            status = false;
            cancel();
        } else if (!status && m_botAction.getArenaSize() > size - 1) {
            status = true;
            
            TimerTask check = new TimerTask() {
                public void run() {
                    check();
                }
            };
            m_botAction.scheduleTaskAtFixedRate(check, 1000, Tools.TimeInMillis.MINUTE);
        }
    }
    
    public void initializeModule() {
        String zoneIP = m_botAction.getGeneralSettings().getString( "Server" );
        String zonePort = m_botAction.getGeneralSettings().getString( "Port" );
        sendtoCmd = "*sendto " + zoneIP + "," + zonePort + "," + AFK_ARENA;
        
        vipList = new ArrayList<String>();
        opList = m_botAction.getOperatorList();
        if (m_botAction.getBotSettings().getString("VIP") != null) {
        	String[] names = m_botAction.getBotSettings().getString("VIP").split(",");
        	vipList = Arrays.asList(names);
        }
        
        players = new TreeMap<String, Long>();
        if (m_botAction.getArenaSize() < size)
            status = false;
        else
            status = true;
        checkStatus();

        /**
        TimerTask check = new TimerTask() {
            public void run() {
                check();
            }
        };
        m_botAction.scheduleTaskAtFixedRate(check, 1000, Tools.TimeInMillis.MINUTE);
        **/
    }

    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.ARENA_JOINED);
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.PLAYER_LEFT);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.FREQUENCY_CHANGE);
        eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
    }
    
    public void handleEvent(ArenaJoined event) {
        players.clear();
        
        for (Iterator<Player> it = m_botAction.getPlayerIterator(); it.hasNext();)
            add(it.next());
    }
    
    public void handleEvent(FrequencyChange event) {
        if (!status)
            return;
        Player p = m_botAction.getPlayer(event.getPlayerID());
        String name = p.getPlayerName();
        
        if (players.containsKey(name) && p.getShipType() != Tools.Ship.SPECTATOR)
            players.remove(name);
        else if (players.containsKey(name))
            players.put(name, System.currentTimeMillis());
        else if (p.getShipType() == Tools.Ship.SPECTATOR)
            add(p);
    }
    
    public void handleEvent(FrequencyShipChange event) {
        if (!status)
            return;
        Player p = m_botAction.getPlayer(event.getPlayerID());
        String name = p.getPlayerName();
        
        if (players.containsKey(name) && p.getShipType() != Tools.Ship.SPECTATOR)
            players.remove(name);
        else if (players.containsKey(name))
            players.put(name, System.currentTimeMillis());
        else if (p.getShipType() == Tools.Ship.SPECTATOR) 
            add(p);
    }
    
    public void handleEvent(Message event) {
        int type = event.getMessageType();
        String message = event.getMessage();
        String name = m_botAction.getPlayerName(event.getPlayerID());
        
        if (name != null) {
            //Do not count private messages, because with ?away a player could fool the bot as if he was still here.
            if (players.containsKey(name) && event.getMessageType() != Message.PRIVATE_MESSAGE)
                players.put(name, System.currentTimeMillis());
        }
        
        if (type == Message.PRIVATE_MESSAGE || type == Message.REMOTE_PRIVATE_MESSAGE) {
            if (opList.isModerator(name)) {
                if (message.startsWith("!listidle"))
                    cmdListidle(name);
                else if (message.startsWith("!move "))
                    cmdMoveidle(name, message);
                else if (message.startsWith("!help"))
                    cmdHelp(name);
                else if (message.startsWith("!status"))
                    cmdStatus(name);
                else if (message.startsWith("!size"))
                    cmdSetSize(name, message);
            }
        }
    }
    
    public void cmdSetSize(String name, String num) {
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
    
    public void cmdStatus(String name) {
        if (status)
            m_botAction.sendSmartPrivateMessage(name, "AFK Status: ON & Size: " + size);
        else
            m_botAction.sendSmartPrivateMessage(name, "AFK Status: OFF & Size: " + size);            
    }
    
    public void handleEvent(PlayerEntered event) {
        checkStatus();
        if (!status)
            return;
        Player p = m_botAction.getPlayer(event.getPlayerID());
        
        if (p.getShipType() == Tools.Ship.SPECTATOR)
            add(p);
    }
    
    public void handleEvent(PlayerLeft event) {
        checkStatus();
        if (!status)
            return;
        String name = m_botAction.getPlayerName(event.getPlayerID());
        
        if (players.containsKey(name))
            players.remove(name);
    }
}