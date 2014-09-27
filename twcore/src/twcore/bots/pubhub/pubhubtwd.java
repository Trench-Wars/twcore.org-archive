package twcore.bots.pubhub;

import java.text.SimpleDateFormat;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Iterator;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.Date;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.events.InterProcessEvent;
import twcore.core.util.ipc.IPCMessage;
import twcore.core.util.Tools;

public class pubhubtwd extends PubBotModule {

    /*
     * String[] object of games
     *0 - Arena Game is In
     *1 - Freq 1 ID
     *2 - Freq 2 ID
     *3 - MatchBot hosting game
     */
    private HashSet<String[]> games;
    private TreeMap<String, Integer> teams;
    private long cfg_time;
    private String webdb = "website";
    private String PUBBOTS = "pubBots";
    private HashSet<String> lagWatched;
    private Date date;
    private SimpleDateFormat datetimeFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm] ");

    public void initializeModule(){
        games = new HashSet<String[]>();
        teams = new TreeMap<String, Integer>();
        cfg_time = m_botAction.getBotSettings().getInt("TimeInMillis");
        lagWatched = new HashSet<String>();

        try {
            ResultSet r = m_botAction.SQLQuery(webdb, "SELECT fcName FROM tblLagWatch");
            String player;
            while (r.next()) {
                player = r.getString("fcName");
                lagWatched.add(player);
                m_botAction.ipcTransmit(PUBBOTS, new IPCMessage("lagwatchon " + player));           
            }
            m_botAction.SQLClose(r);
        } catch (Exception e) {
            Tools.printLog("Problem loading LagWatch names from tblLagWatch.");
        }
    }

    public void requestEvents(EventRequester r){
        r.request(EventRequester.MESSAGE);
    }

    public void cancel() {
        games.clear();
        teams.clear();
        lagWatched.clear();
    }

    public void handleEvent(Message event) {
        String name = event.getMessager() == null ? m_botAction.getPlayerName(event.getPlayerID()) : event.getMessager();
        String message = event.getMessage();
        int messageType = event.getMessageType();

        if(messageType == Message.PRIVATE_MESSAGE || messageType == Message.REMOTE_PRIVATE_MESSAGE){
            if(message.equalsIgnoreCase("!showgames") && opList.isSmod(name)){
                if(!games.isEmpty()){
                    Iterator<String[]> i = games.iterator();
                    while(i.hasNext()){
                        m_botAction.sendSmartPrivateMessage( name, "-------------------");
                        m_botAction.smartPrivateMessageSpam(name, i.next());
                    }
                }else
                    m_botAction.sendSmartPrivateMessage( name, "No games found.");
            } else if(message.equalsIgnoreCase("!lagwatchinfo "))
                doLagWatchInfo(name, message.substring(14));
        } else if(messageType == Message.CHAT_MESSAGE && opList.isSmod(name)) {
            if(message.equalsIgnoreCase("!lagwatchon "))
                doLagWatchOn(message.substring(12));
            else if(message.equalsIgnoreCase("!lagwatchoff "))
                doLagWatchOff(message.substring(13));
            else if(message.equalsIgnoreCase("!lagwatchclear "))
                doLagWatchClear(message.substring(15));
            else if(message.equalsIgnoreCase("!lagwatchinfo "))
                doLagWatchInfo(name, message.substring(14));
            else if(message.equalsIgnoreCase("!lagwatches"))
                doLagWatches();
        }
    }
    
    public void doLagWatchOn(String player) {
        try {
            lagWatched.add(player);
            m_botAction.SQLQueryAndClose(webdb, "INSERT INTO tblLagWatch (fcName) VALUES ('" + Tools.addSlashesToString(player) + "')");
            m_botAction.ipcTransmit(PUBBOTS, new IPCMessage("lagwatchon " + player));           
            m_botAction.sendChatMessage("Recording lag info for '" + player + "' after entering any pubbot-enabled arena. !lagwatchoff to disable.");
        } catch (Exception e) {
            m_botAction.sendChatMessage("Could not add player '" + player + "' -- already watched? Check !lagwatches.");
        }
    }

    public void doLagWatchOff(String player) {
        if (!lagWatched.contains(player)) {
            m_botAction.sendChatMessage("Not currently watching '" + player + "' -- please check spelling.");
            return;
        }
        try {
            lagWatched.remove(player);
            m_botAction.SQLQueryAndClose(webdb, "DELETE FROM tblLagWatch WHERE fcName='" + Tools.addSlashesToString(player) + "'");
            m_botAction.ipcTransmit(PUBBOTS, new IPCMessage("lagwatchoff " + player));           
            m_botAction.sendChatMessage("No longer recording lag info for '" + player + "'. !lagwatchinfo to see all recorded data, or !lagwatchclear to remove data when no longer needed.");
        } catch (Exception e) {
            m_botAction.sendChatMessage("Problem when removing player '" + player + "'.");
        }
    }
    
    public void doLagWatchClear(String player) {
        try {
            m_botAction.SQLQueryAndClose(webdb, "DELETE FROM tblLagWatchData WHERE fcName='" + Tools.addSlashesToString(player) + "'");
            m_botAction.sendChatMessage("All data for '" + player + "' removed.");
        } catch (Exception e) {
            m_botAction.sendChatMessage("Problem when removing data for player '" + player + "'.");
        }
    }

    public void doLagWatches() {
        try {
            String watchedList = "Currently watching: ";
            for( String watched : lagWatched)
                watchedList += (watched + " ");
            m_botAction.sendChatMessage(watchedList);
        } catch (Exception e) {
            m_botAction.sendChatMessage("Problem encountered listing watched players.");
        }
    }
    
    public void doLagWatchInfo(String staffer, String player) {
        try {
            ResultSet r = m_botAction.SQLQuery(webdb, "SELECT * FROM tblLagWatchData WHERE fcName='" + Tools.addSlashesToString(player) + "'");
            while (r.next()) {
                String result = "";
                result += datetimeFormat.format(r.getTimestamp("fdCreated"));
                result += r.getString("fcLag");
                m_botAction.sendSmartPrivateMessage(staffer, result);
            }
            m_botAction.SQLClose(r);
        } catch (Exception e) {
            m_botAction.sendChatMessage("Problem getting lagwatch info.");
        }

    }
    
    public void handleEvent(InterProcessEvent event) {
        try
        {
            if(!(event.getObject() instanceof IPCMessage))return;
            IPCMessage ipcMessage = (IPCMessage) event.getObject();
            String message = ipcMessage.getMessage();
            String recipient = ipcMessage.getRecipient();
            String sender = ipcMessage.getSender();
            if(recipient == null || recipient.equals(m_botAction.getBotName()))
                handleBotIPC(sender, message);
        }
        catch(Exception e){
            Tools.printStackTrace(e);
        }
    }

    public void handleBotIPC(String sender, String message){
        if(message.startsWith("twdgame "))
            gotTWDGameCmd(message.substring(8), true);
        else if(message.startsWith("endtwdgame "))
            gotTWDGameCmd(message.substring(11), false);
        else if(message.startsWith("getgame "))
            giveGame(sender, message.substring(10));

    }

    public void gotTWDGameCmd(String message, boolean isStartOfGame){
        String[] msg = message.split(":");
        if(isStartOfGame && !games.contains(msg))
            games.add(msg);
        else games.remove(msg[0]);
    }

    public void giveGame(String pubbot, String message){
        date = new Date();
        if((date.getTime() - cfg_time) > (24 * Tools.TimeInMillis.HOUR)){
            populateTeams();
            m_botAction.getBotSettings().put("TimeInMillis", date.getTime());
        }
        String[] temp = message.split(":");
        if(temp.length != 2)return;
        String playerName = temp[0];
        String squadName = temp[1];
        if(teams.containsKey(squadName.toLowerCase())){
            String teamID = Integer.toString(teams.get(squadName.toLowerCase()));
            Iterator<String[]> i = games.iterator();
            while(i.hasNext()){
                String[] msg = i.next();
                if(teamID.equals(msg[1]) || teamID.equals(msg[2]))
                    m_botAction.ipcTransmit(PUBBOTS, new IPCMessage("givegame " + playerName + ":" + msg[0] + ":" + msg[3], pubbot));			  
            }
        }
    }

    public void populateTeams(){
        try{
            teams.clear();
            ResultSet rs = m_botAction.SQLQuery(webdb, "SELECT fnTeamID, fcTeamName FROM tblTeam WHERE fdDeleted IS NULL OR fdDeleted = 0");
            while(rs != null && rs.next()){
                teams.put(rs.getString("fcTeamName").toLowerCase(), rs.getInt("fnTeamID"));
            }
            m_botAction.SQLClose(rs);
        }catch(SQLException e){
            Tools.printStackTrace(e);
        }
    }

}