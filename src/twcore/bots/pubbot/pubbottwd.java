package twcore.bots.pubbot;

import java.util.HashSet;
import java.util.HashMap;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.Message;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

/**
    Performs TWD-related functions:

      1. Notifies players entering of current TWD matches
      2. Lagchecks players on lagwatch who stay in the arena for 5 minutes, for use in TWD/TWL matches later on

*/
public class pubbottwd extends PubBotModule {

    private String PUBBOTS = "pubBots";
    private String webdb = "website";
    private HashSet<String> lagWatched;    // List of players on the lagwatch list
    private HashMap<String, LagWatchTimer> lagWatchTimers;
    private String lagChecking = "";       // Player presently being lagchecked

    public void initializeModule() {
        lagWatched = new HashSet<String>();
        lagWatchTimers = new HashMap<String, LagWatchTimer>();
    }

    public void cancel() {
        lagWatched.clear();
    }

    public void requestEvents( EventRequester eventRequester ) {
        eventRequester.request( EventRequester.PLAYER_ENTERED );
        eventRequester.request( EventRequester.PLAYER_LEFT );
        eventRequester.request( EventRequester.MESSAGE );
    }

    public void handleEvent( PlayerEntered event ) {
        Player p = m_botAction.getPlayer(event.getPlayerID());

        if(p == null)return;

        m_botAction.ipcTransmit(PUBBOTS, new IPCMessage("getgame " + p.getPlayerName() + ":" + p.getSquadName(), "PubHub"));

        if (lagWatched.contains(p.getPlayerName())) {
            LagWatchTimer lwt = new LagWatchTimer(p.getPlayerName());

            try {
                lagWatchTimers.put(p.getPlayerName(), lwt);
                m_botAction.scheduleTask(lwt, Tools.TimeInMillis.MINUTE * 5);
            } catch( Exception e) {
                m_botAction.cancelTask(lwt);
            }
        }
    }

    public void handleEvent( PlayerLeft event ) {
        Player p = m_botAction.getPlayer(event.getPlayerID());

        if(p == null)return;

        if (lagWatched.contains(p.getPlayerName())) {
            try {
                LagWatchTimer lwt = lagWatchTimers.remove(p.getPlayerName());
                m_botAction.cancelTask(lwt);
            } catch (Exception e) {}
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
        catch(Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void handleEvent(Message event) {
        String message = event.getMessage();

        if(message != null && event.getMessageType() == Message.ARENA_MESSAGE) {
            if(message.startsWith("PING Current:") && !lagChecking.equals("")) {
                try {
                    m_botAction.SQLQueryAndClose(webdb, "INSERT INTO tblLagWatchData (fcName,fcLagLine) VALUES ('" + Tools.addSlashesToString(lagChecking) + "','" + message + "'" );
                } catch(Exception e) {
                    Tools.printLog("Trouble logging lagwatched player '" + lagChecking + "' to database." );
                }

                lagChecking = "";
            }
        }

    }

    public void handleBotIPC(String sender, String message) {
        if(message.startsWith("givegame "))
            doGotGame(message.substring(9));
        else if(message.startsWith("lagwatchon "))
            doLagWatchOn(message.substring(11));
        else if(message.startsWith("lagwatchoff "))
            doLagWatchOn(message.substring(12));
    }

    public void doGotGame(String message) {
        String[] msg = message.split(":");
        m_botAction.sendSmartPrivateMessage(msg[0], "Your squad is currently playing a TWD match in ?go " + msg[1] + " -" + msg[2]);
    }

    public void doLagWatchOn(String message) {
        try {
            lagWatched.add(message);
        } catch(Exception e) {}
    }

    public void doLagWatchOff(String message) {
        try {
            lagWatched.remove(message);
        } catch(Exception e) {}
    }

    class LagWatchTimer extends TimerTask {
        String player;

        public LagWatchTimer( String player ) {
            this.player = player;
        }

        public void run() {
            Player p = m_botAction.getPlayer(player);

            if (p == null)
                return;

            lagChecking = player;
            m_botAction.sendUnfilteredPrivateMessage(player, "*lag");
        }
    }
}

