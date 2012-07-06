package twcore.bots.twdop;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import twcore.bots.ModuleHandler;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.ArenaList;
import twcore.core.events.BallPosition;
import twcore.core.events.FileArrived;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FlagDropped;
import twcore.core.events.FlagPosition;
import twcore.core.events.FlagReward;
import twcore.core.events.FlagVictory;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.KotHReset;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerBanner;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.Prize;
import twcore.core.events.SQLResultEvent;
import twcore.core.events.ScoreReset;
import twcore.core.events.ScoreUpdate;
import twcore.core.events.SoccerGoal;
import twcore.core.events.SubspaceEvent;
import twcore.core.events.TurfFlagUpdate;
import twcore.core.events.TurretEvent;
import twcore.core.events.WatchDamage;
import twcore.core.events.WeaponFired;
import twcore.core.util.Tools;

public class twdop extends SubspaceBot {
    public static final String DATABASE = "website";
    private ModuleHandler moduleHandler;
    private HashMap<String, String> twdops = new HashMap<String, String>();
    OperatorList m_opList;
    BotAction m_botAction;
    BotSettings m_botSettings;

    /* Initialization code */
    public twdop(BotAction botAction) {
        super(botAction);

        moduleHandler = new ModuleHandler(botAction, botAction.getGeneralSettings().getString("Core Location") + "/twcore/bots/twdop", "twdop");

        m_botAction = botAction;
        m_botSettings = m_botAction.getBotSettings();

        // Request Events
        EventRequester req = botAction.getEventRequester();
        req.requestAll();
    }

    @Override
    public void handleDisconnect() {
        moduleHandler.unloadAllModules();
    }

    @Override
    public void handleEvent(LoggedOn event) {

        m_botAction.joinArena(m_botSettings.getString("InitialArena"));

        String twdop = m_botSettings.getString("TWDOp Chat");
        String staff = m_botAction.getGeneralSettings().getString("Staff Chat");
        //String dev = m_botAction.getGeneralSettings().getString( "Chat Name" );
        String twd = "twd";
        m_botAction.sendUnfilteredPublicMessage("?chat=" + twdop + "," + staff + "," + twd);

        // load modules
        moduleHandler.loadModule("alias");
        moduleHandler.loadModule("stats");
        moduleHandler.loadModule("racism");
        updateTWDOps();

    }

    @Override
    public void handleEvent(Message event) {
        if ((event.getMessageType() == Message.CHAT_MESSAGE || event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) && event.getMessage().startsWith("!")) {
            // Commands
            String message = event.getMessage().toLowerCase();
            short sender = event.getPlayerID();
            String senderName = event.getMessageType() == Message.CHAT_MESSAGE ? event.getMessager() : m_botAction.getPlayerName(sender);

            // Ignore player's commands
            if (!m_botAction.getOperatorList().isSmod(senderName) && !isTWDOp(senderName)) {
                return;
            }

            // !help
            if (message.startsWith("!help")) {
                String[] help = { "-----------------------[ TWDOp Bot ]-----------------------",
                        " !status                           - Recalls DB status and TWDOp count",
                        " !report <player>:<comment>        - Claims a call for helping <player> with <comment>", "   or !r <player>:<comment>",
                        " !addnote <player>:<comment>       - Insert a note for this player",
                        " !listnotes <name>                 - List the notes assosiated with <name>",
                        " !delnote <id>                     - Delete the note assosiated with <id>",

                };

                String[] smodHelp = { " !die                               - Disconnects TWDOp",
                        " !update                            - Reloads TWDOps for use.",
                        " !claimsfrom <TWD Op>:<#>           - Displays the last <#> calls reported by <TWD Op>.", "   or !claims",
                        " !claimsfor <player>:<#>            - Displays the last <#> calls reported about <player>.", "   or !calls" };

                m_botAction.smartPrivateMessageSpam(senderName, help);

                if (m_botAction.getOperatorList().isSmod(senderName)) {
                    m_botAction.smartPrivateMessageSpam(senderName, smodHelp);
                }
            }
            if (m_botAction.getOperatorList().isSmod(senderName)) {
                if (message.equalsIgnoreCase("!die")) {
                    moduleHandler.unloadAllModules();
                    this.handleDisconnect();
                    m_botAction.die();
                }
                if (message.equalsIgnoreCase("!update")) {
                    updateTWDOps();
                    m_botAction.sendChatMessage("Reloading TWDOps at " + senderName + "'s request.");
                }
            }
            if (message.equalsIgnoreCase("!status")) {
                m_botAction.sendChatMessage("[ONLINE]");
                m_botAction.sendChatMessage("[TWDOps] - " + twdops.size() + " TWDOp access.");
                dbcheck();
            }
            if (message.startsWith("!addnote ")) {
                String[] msg = message.substring(9).split(":");
                String note = msg[1];
                String player = msg[0];

                try {
                    m_botAction.SQLQueryAndClose(DATABASE, "INSERT INTO tblTWDNote (fcUserName, fcNote, fcStaffer, fdCreated) VALUES ('"
                            + Tools.addSlashesToString(player) + "', '" + Tools.addSlashesToString(note) + "', '"
                            + Tools.addSlashesToString(senderName) + "', NOW())");
                    m_botAction.sendChatMessage("Note saved successfully for player " + player);
                } catch (SQLException e) {
                    m_botAction.sendChatMessage("Error! Please report to botdev: " + e);
                    Tools.printStackTrace(e);
                }
            }
            if (message.startsWith("!listnotes ")) {
                String player = message.substring(11);
                try {
                    ResultSet result = m_botAction.SQLQuery(DATABASE, "SELECT fnID, fcNote, fcStaffer, fdCreated FROM tblTWDNote WHERE fcUserName = '"
                            + Tools.addSlashesToString(player) + "'");
                    m_botAction.sendChatMessage("The format of the listing notes is ID:NOTE:STAFFER:DATE.");

                    while (result.next()) {
                        int id = result.getInt("fnID");
                        String note = result.getString("fcNote");
                        String staffer = result.getString("fcStaffer");
                        String created = result.getString("fdCreated").substring(0, 11);

                        m_botAction.sendChatMessage(id + ") " + note + " - " + staffer + " - " + created);

                    }

                    m_botAction.SQLClose(result);
                } catch (SQLException e) {
                    m_botAction.sendChatMessage("Error! Please report to botdev: " + e);
                    Tools.printStackTrace(e);
                }
            }
            if (message.startsWith("!delnote ")){
                String id = message.substring(9);
                if(Tools.isAllDigits(id)){
                    try {
                        m_botAction.SQLQueryAndClose(DATABASE, "DELETE FROM tblTWDNote WHERE fnID = '" + Tools.addSlashesToString(id) + "'");
                        m_botAction.sendChatMessage("Note ID " + id + " has been removed.");
                    } catch (SQLException e) {
                        m_botAction.sendChatMessage("Error! Please report to botdev: " + e);
                        Tools.printStackTrace(e);
                    }
                }
                
            }

        }

        moduleHandler.handleEvent(event);
    }

    private void dbcheck() {
        if (!m_botAction.SQLisOperational()) {
            m_botAction.sendChatMessage("[DB] - Connection is down.");
            return;
        }

        try {
            m_botAction.SQLQueryAndClose(DATABASE, "SELECT * FROM tblCall LIMIT 0,1");
            m_botAction.sendChatMessage("[DB] - DataBase connection is online.");

        } catch (Exception e) {
            m_botAction.sendChatMessage("[DB] - DataBase connection is down.");
        }
    }

    private void updateTWDOps() {
        try {
            ResultSet r = m_botAction.SQLQuery(DATABASE, "SELECT tblUser.fcUsername FROM `tblUserRank`, `tblUser` WHERE `fnRankID` = '14' AND tblUser.fnUserID = tblUserRank.fnUserID");

            if (r == null) {
                return;
            }

            twdops.clear();

            while (r.next()) {
                String name = r.getString("fcUsername");
                twdops.put(name.toLowerCase(), name);
            }

            m_botAction.SQLClose(r);
        } catch (SQLException e) {
            throw new RuntimeException("ERROR: Unable to update twdop list.");
        }
    }

    private boolean isTWDOp(String senderName) {
        if (twdops.containsKey(senderName.toLowerCase())) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void handleEvent(SubspaceEvent event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(ArenaJoined event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(ArenaList event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(BallPosition event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(FileArrived event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(FlagClaimed event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(FlagDropped event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(FlagPosition event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(FlagReward event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(FlagVictory event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(FrequencyChange event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(FrequencyShipChange event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(InterProcessEvent event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(KotHReset event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(PlayerBanner event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(PlayerDeath event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(PlayerEntered event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(PlayerLeft event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(PlayerPosition event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(Prize event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(ScoreReset event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(ScoreUpdate event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(SoccerGoal event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(SQLResultEvent event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(TurfFlagUpdate event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(TurretEvent event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(WatchDamage event) {
        moduleHandler.handleEvent(event);
    }

    @Override
    public void handleEvent(WeaponFired event) {
        moduleHandler.handleEvent(event);
    }

}