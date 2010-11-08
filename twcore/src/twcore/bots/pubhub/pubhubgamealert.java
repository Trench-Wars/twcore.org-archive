package twcore.bots.pubhub;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.game.Player;
import twcore.core.util.ipc.IPCMessage;

/**
 * GameAlert sends a one time alert upon login if the player's squad is in a twd match
 * 
 * @author WingZero
 */
public class pubhubgamealert extends PubBotModule {

    private String dbConn = "website";
    private HashMap<String, SquadData> games;
    private HashSet<AlertedPlayer> alerted;
    private TimerTask getGames;
    private String m_botName;
    private OperatorList opList;
    
    @Override
    public void initializeModule() {
        scheduleTask();
        m_botName = m_botAction.getBotName();
        games = new HashMap<String, SquadData>();
        alerted = new HashSet<AlertedPlayer>();
        opList = m_botAction.getOperatorList();
    }

    @Override
    public void cancel() {
        m_botAction.cancelTask(getGames);        
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
    }
    
    /**
     * Hidden SMOD commands for potential problem prevention
     */
    public void handleEvent(Message event) {
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (name == null) {
            name = event.getMessager();
        }
        
        if(!opList.isSmod(name)) return;
        
        String message = event.getMessage();
        int messageType = event.getMessageType();

        if(message.equals("!refreshmatches")) {
            refreshMatches(name, messageType);
        }
        else if(message.equals("!cancelrefresh")) {
            cancelRefresh(name, messageType);
        }
        else if(message.equals("!restartrefresh")) {
            restartRefresh(name, messageType);              
        }
    }
    
    public void refreshMatches(String name, int messageType) {
        if ((messageType == Message.PRIVATE_MESSAGE) || (messageType == Message.REMOTE_PRIVATE_MESSAGE)) {
            refreshMatches();
            m_botAction.sendSmartPrivateMessage(name, "Matches refreshed.");
        }
        else if (messageType == Message.CHAT_MESSAGE){
            refreshMatches();
            m_botAction.sendChatMessage("Matches refreshed.");
        }
    }
    
    public void cancelRefresh(String name, int messageType) {
        if ((messageType == Message.PRIVATE_MESSAGE) || (messageType == Message.REMOTE_PRIVATE_MESSAGE)) {
            cancel();
            m_botAction.sendSmartPrivateMessage(name, "TimerTask getGames cancelled.");        
        }
        else if (messageType == Message.CHAT_MESSAGE){
            cancel();
            m_botAction.sendChatMessage("TimerTask getGames cancelled.");
        }
    }
    
    public void restartRefresh(String name, int messageType) {
        if ((messageType == Message.PRIVATE_MESSAGE) || (messageType == Message.REMOTE_PRIVATE_MESSAGE)) {
            scheduleTask();
            m_botAction.sendSmartPrivateMessage(name, "TimerTask getGames restarted.");  
        }
        else if (messageType == Message.CHAT_MESSAGE){
            scheduleTask();
            m_botAction.sendChatMessage("TimerTask getGames restarted.");
        }
    }

    /**
     * This method handles an InterProcess event.
     *
     * @param event is the IPC event to handle.
     */
    public void handleEvent(InterProcessEvent event)
    {
        // If the event.getObject() is anything else then the IPCMessage (pubbotchatIPC f.ex) then return
        if(event.getObject() instanceof IPCMessage == false) {
            return;
        }

        IPCMessage ipcMessage = (IPCMessage) event.getObject();
        String message = ipcMessage.getMessage();

        try
        {
            if(message.startsWith("player ")) {
                playerEntered(message.substring(message.indexOf(' ')+1));
            }
        }
        catch(Exception e)
        {
            m_botAction.sendChatMessage(e.getMessage());
        }
    }
    
    /**
     * This method attends to player information received from pubbot
     * 
     * @param name
     */
    private void playerEntered(String name) {
        Player p = m_botAction.getPlayer(name);
        String squadName = p.getSquadName();
        
        if (squadName.length() > 0) {
            if (games.containsKey(squadName.toLowerCase())) {
                SquadData squad = games.get(squadName.toLowerCase());
                Iterator<String[]> it = squad.getMatches().iterator();
                while (it.hasNext()) {
                    try {
                        boolean toAlert = true;
                        String[] info = it.next();
                        int matchID = Integer.parseInt(info[4]);
                        AlertedPlayer player = new AlertedPlayer(name.toLowerCase(), matchID);
                        Iterator<AlertedPlayer> i = alerted.iterator();
                        while(toAlert && i.hasNext()) {
                            AlertedPlayer ap = i.next();
                            if (name.equalsIgnoreCase(ap.getPlayer()) && ap.getMatchID() == matchID)
                                toAlert = false;
                        }
                        
                        if (toAlert) {
                            m_botAction.ipcSendMessage(getIPCChannel(), "send " + name + ":Your squad is " + info[1].toLowerCase() + " a " + info[2] + " match against " + info[0] + " in ?go " + info[3], null, m_botName);
                            alerted.add(player);
                        }
                    } catch (Exception e) { m_botAction.sendChatMessage("parseInt failed"); }
                }
            }
        }
    }
    
    /**
     * Match list refresh scheduler schedules for every 60 seconds
     */
    private void scheduleTask() {
        getGames = new TimerTask() {
            public void run() {
                refreshMatches();
            }
        };
        m_botAction.scheduleTask(getGames, 0, 60000);
    }
    
    /**
     * Clears the games list and updates it accordingly
     */
    private void refreshMatches() {
        games.clear();
        String[] data = new String[6];
        try {
            String query = "SELECT *, UNIX_TIMESTAMP(NOW())-UNIX_TIMESTAMP(ftTimeStarted) time " +
                    "FROM tblMatch, tblMatchType, tblMatchState " +
                    "WHERE fnMatchID > 1 " +
                    "AND DATE_SUB(NOW(), INTERVAL 60 MINUTE) < ftTimeStarted " +
                    "AND (tblMatch.fnMatchStateID = 2 OR tblMatch.fnMatchStateID = 1) " +
                    "AND tblMatch.fnMatchTypeID IN (4,5,6,13) " +
                    "AND tblMatchType.fnMatchTypeID = tblMatch.fnMatchTypeID " +
                    "AND tblMatchState.fnMatchStateID = tblMatch.fnMatchStateID " +
                    "ORDER BY ftTimeStarted DESC";
            
            ResultSet matches = m_botAction.SQLQuery(dbConn, query);
            while (matches.next()) {
                data[0] = matches.getString("fcTeam1Name");
                data[1] = matches.getString("fcTeam2Name");
                data[2] = matches.getString("fcMatchStateName");
                data[3] = matches.getString("fcMatchTypeName");
                data[4] = matches.getString("fcArenaName");
                data[5] = "" + matches.getInt("fnMatchID");
                if (games.containsKey(data[0].toLowerCase())) {
                    games.get(data[0].toLowerCase()).addMatch(data[1], data[2], data[3], data[4], data[5], data[0]);
                } else {
                    SquadData asquad = new SquadData(data[1], data[2], data[3], data[4], data[5], data[0]);
                    games.put(data[0].toLowerCase(), asquad);
                }
                if (games.containsKey(data[1].toLowerCase())) {
                    games.get(data[1].toLowerCase()).addMatch(data[0], data[2], data[3], data[4], data[5], data[1]);
                } else {
                    SquadData asquad = new SquadData(data[0], data[2], data[3], data[4], data[5], data[1]);
                    games.put(data[1].toLowerCase(), asquad);
                }
            }
            m_botAction.SQLClose(matches);
        } catch (Exception e) { }
        
    }
}

/**
 * SquadData class holds match information by squad name 
 */
class SquadData {
    String name;
    ArrayList<String[]> matches;

    public SquadData(String squad) {
        name = squad;
        matches = new ArrayList<String[]>();
    }
    
    public SquadData(String nme, String state, String type, String arena, String matchID, String squad) {
        name = squad;
        matches = new ArrayList<String[]>();
        String[] info = new String[6];
        info[0] = nme;
        info[1] = state;
        info[2] = type;
        info[3] = arena;
        info[4] = matchID;
        info[5] = squad;
        matches.add(info);
    }
    
    public void addMatch(String nme, String state, String type, String arena, String matchID, String squad) {
        String[] info = new String[6];
        info[0] = nme;
        info[1] = state;
        info[2] = type;
        info[3] = arena;
        info[4] = matchID;
        info[5] = squad;
        matches.add(info);
    }
    
    public ArrayList<String[]> getMatches() {
        return matches;
    }
}

/** 
 * AlertedPlayer class keeps track of which players were alerted to what MatchID 
 */
class AlertedPlayer {
    String player;
    int matchID;
    
    public AlertedPlayer(String name, int ID) {
        player = name;
        matchID = ID;
    }
    
    public String getPlayer() {
        return player;
    }
    
    public int getMatchID() {
        return matchID;
    }
}



