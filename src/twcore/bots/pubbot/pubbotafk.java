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
 * Move players to the AFK_ARENA when they are idle for too long.
 * 
 * 
 * @author fantus
 */
public class pubbotafk extends PubBotModule {
    private final static int WARNING_TIME = 10;     //(Time in minutes)
    private final static int MOVE_TIME = 5;         //Time the player gets to get active
                                                    //after the warning (Time in minutes)
    private final static String AFK_ARENA = "afk";  //Arena to where the players get moved
    private final static String WARNING_MESSAGE = "NOTICE: In order to keep the gameplay high in public arena's " +
    		"being idle for too long is not allowed. If you intend to go afk, please type \"?go " + AFK_ARENA + "\"." +
    		" (If you stay inactive you will be moved to ?go " + AFK_ARENA + ".)";
    
    private OperatorList opList;
    private String sendtoCmd;
    private TreeMap<String, IdlePlayer> players;

    public void add(Player p) {
        if (!opList.isBotExact(p.getPlayerName()) && p.getShipType() == Tools.Ship.SPECTATOR)
             players.put(p.getPlayerName(), new IdlePlayer(p.getPlayerName()));
    }
    
    public void cancel() {
        players.clear();
    }

    public void cmdHelp(String messager) {
        m_botAction.sendSmartPrivateMessage(messager, "Pubbot AFK Module");
        m_botAction.sendSmartPrivateMessage(messager,
                "!listidle      -- Display spectating players and their idle times");
        m_botAction.sendSmartPrivateMessage(messager,
                "!move <player> -- Move <player> to ?go " + AFK_ARENA);
    }
    
    public void cmdListidle(String messager) {
        ArrayList<String> out = new ArrayList<String>();
        out.add(Tools.formatString("<Name>", 23) + " - <idle time>");
        for (IdlePlayer p : players.values())
            out.add(Tools.formatString(p.name, 23) + " - " + p.idleTime);
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
    
    public void initializeModule() {
        String zoneIP = m_botAction.getGeneralSettings().getString( "Server" );
        String zonePort = m_botAction.getGeneralSettings().getString( "Port" );
        sendtoCmd = "*sendto " + zoneIP + "," + zonePort + "," + AFK_ARENA;
        
        opList = m_botAction.getOperatorList();
        
        players = new TreeMap<String, IdlePlayer>();
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
        Player p = m_botAction.getPlayer(event.getPlayerID());
        String name = p.getPlayerName();
        
        if (players.containsKey(name) && p.getShipType() != Tools.Ship.SPECTATOR)
            players.remove(name);
        else if (players.containsKey(name))
            players.get(name).reset();
        else if (p.getShipType() == Tools.Ship.SPECTATOR)
            add(p);
    }
    
    public void handleEvent(FrequencyShipChange event) {
        Player p = m_botAction.getPlayer(event.getPlayerID());
        String name = p.getPlayerName();
        
        if (players.containsKey(name) && p.getShipType() != Tools.Ship.SPECTATOR)
            players.remove(name);
        else if (players.containsKey(name))
            players.get(name).reset();
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
                players.get(name).reset();
        }
        
        if (type == Message.PRIVATE_MESSAGE || type == Message.REMOTE_PRIVATE_MESSAGE) {
            if (opList.isModerator(name)) {
                if (message.startsWith("!listidle"))
                    cmdListidle(name);
                else if (message.startsWith("!move "))
                    cmdMoveidle(name, message);
                else if (message.startsWith("!help"))
                    cmdHelp(name);
            }
        }
    }
    
    public void handleEvent(PlayerEntered event) {
        Player p = m_botAction.getPlayer(event.getPlayerID());
        
        if (p.getShipType() == Tools.Ship.SPECTATOR)
            add(p);
    }
    
    public void handleEvent(PlayerLeft event) {
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (players.containsKey(name))
            players.remove(name);
    }
    
    private class IdlePlayer {
        private boolean warned;
        private Short idleTime; //In minutes
        private TimerTask updater;
        private String name;
        
        public IdlePlayer(String name) {
            this.name = name;
            idleTime = 0;
            warned = false;
            
            updater = new TimerTask() {
              public void run() {
                  idleTime++;
                  check();
              }
            };
            m_botAction.scheduleTaskAtFixedRate(updater, Tools.TimeInMillis.MINUTE, Tools.TimeInMillis.MINUTE);  
        }
        
        public void check() {
            if (!warned && idleTime >= WARNING_TIME) {
                warned = true;
                m_botAction.sendPrivateMessage(name, WARNING_MESSAGE);
            }
            else if (warned && idleTime >= (WARNING_TIME + MOVE_TIME))
                m_botAction.sendUnfilteredPrivateMessage(name, sendtoCmd);
        }
        
        public void reset() {
            warned = false;
            idleTime = 0;
        }
    }
}
