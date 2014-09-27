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

public class pubhublagwatch extends PubBotModule {

    /*
     * String[] object of games
     *0 - Arena Game is In
     *1 - Freq 1 ID
     *2 - Freq 2 ID
     *3 - MatchBot hosting game
     */
    private String webdb = "website";
    private String PUBBOTS = "pubBots";
    private HashSet<String> lagWatched;
    private Date date;
    private SimpleDateFormat datetimeFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm] ");

    public void initializeModule(){
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
        lagWatched.clear();
    }

    public void handleEvent(Message event) {
        String name = event.getMessager() == null ? m_botAction.getPlayerName(event.getPlayerID()) : event.getMessager();
        String message = event.getMessage();
        int messageType = event.getMessageType();
        if (!opList.isSmod(name))
            return;

        if(messageType == Message.PRIVATE_MESSAGE || messageType == Message.REMOTE_PRIVATE_MESSAGE || messageType == Message.CHAT_MESSAGE ){
            if(message.startsWith("!lagwatchinfo "))
                doLagWatchInfo(name, message.substring(14));
            if(message.startsWith("!lagwatchon "))
                doLagWatchOn(name, message.substring(12));
            else if(message.startsWith("!lagwatchoff "))
                doLagWatchOff(name, message.substring(13));
            else if(message.startsWith("!lagwatchclear "))
                doLagWatchClear(name, message.substring(15));
            else if(message.equalsIgnoreCase("!lagwatches"))
                doLagWatches(name);
        }
    }
    
    public void doLagWatchOn(String staffer, String player) {
        try {
            lagWatched.add(player);
            m_botAction.SQLQueryAndClose(webdb, "INSERT INTO tblLagWatch (fcName) VALUES ('" + Tools.addSlashesToString(player) + "')");
            m_botAction.ipcTransmit(PUBBOTS, new IPCMessage("lagwatchon " + player));           
            m_botAction.sendSmartPrivateMessage(staffer, "Recording lag info for '" + player + "' after entering any pubbot-enabled arena. !lagwatchoff to disable.");
        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(staffer, "Could not add player '" + player + "' -- already watched? Check !lagwatches.");
        }
    }

    public void doLagWatchOff(String staffer, String player) {
        if (!lagWatched.contains(player)) {
            m_botAction.sendSmartPrivateMessage(staffer, "Not currently watching '" + player + "' -- please check spelling.");
            return;
        }
        try {
            lagWatched.remove(player);
            m_botAction.SQLQueryAndClose(webdb, "DELETE FROM tblLagWatch WHERE fcName='" + Tools.addSlashesToString(player) + "'");
            m_botAction.ipcTransmit(PUBBOTS, new IPCMessage("lagwatchoff " + player));           
            m_botAction.sendSmartPrivateMessage(staffer, "No longer recording lag info for '" + player + "'. !lagwatchinfo to see all recorded data, or !lagwatchclear to remove data when no longer needed.");
        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(staffer, "Problem when removing player '" + player + "'.");
        }
    }
    
    public void doLagWatchClear(String staffer, String player) {
        try {
            m_botAction.SQLQueryAndClose(webdb, "DELETE FROM tblLagWatchData WHERE fcName='" + Tools.addSlashesToString(player) + "'");
            m_botAction.sendSmartPrivateMessage(staffer, "All data for '" + player + "' removed.");
        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(staffer, "Problem when removing data for player '" + player + "'.");
        }
    }

    public void doLagWatches(String staffer) {
        try {
            String watchedList = "Currently watching: ";
            for( String watched : lagWatched)
                watchedList += (watched + " ");
            m_botAction.sendSmartPrivateMessage(staffer, watchedList);
        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(staffer, "Problem encountered listing watched players.");
        }
    }
    
    public void doLagWatchInfo(String staffer, String player) {
        try {
            ResultSet r = m_botAction.SQLQuery(webdb, "SELECT * FROM tblLagWatchData WHERE fcName='" + Tools.addSlashesToString(player) + "'");
            while (r.next()) {
                String result = "";
                result += datetimeFormat.format(r.getTimestamp("fdCreated"));
                result += r.getString("fcLagLine");
                m_botAction.sendSmartPrivateMessage(staffer, result);
            }
            m_botAction.SQLClose(r);
        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(staffer, "Problem getting lagwatch info.");
        }

    }

}