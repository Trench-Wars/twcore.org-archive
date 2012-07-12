package twcore.bots.twdtbot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.SQLResultEvent;
import twcore.core.stats.DBPlayerData;
import twcore.core.util.Tools;

/**
 * Simple bot used for the TWDT sign up process.
 *
 * @author WingZero
 */
public class twdtbot extends SubspaceBot {

    private static final int MAX_CHARS = 220;
    private static final String db = "website";
    private int season;
    
    private BotAction ba;
    private OperatorList oplist;
    
    public twdtbot(BotAction botAction) {
        super(botAction);
        ba = botAction;
        requestEvents();
    }

    public void requestEvents() {
        EventRequester req = m_botAction.getEventRequester();
        req.request(EventRequester.LOGGED_ON);
        req.request(EventRequester.ARENA_JOINED);
        req.request(EventRequester.MESSAGE);
    }
    
    public void handleEvent(LoggedOn event) {
        ba.joinArena(ba.getBotSettings().getString("InitialArena"));
        season = ba.getBotSettings().getInt("Season");
        oplist = ba.getOperatorList();
    }
    
    public void handleEvent(ArenaJoined event) {
    }
    
    public void handleEvent(SQLResultEvent event) {
        String name = event.getIdentifier();
        String msg = "";
        ResultSet rs = event.getResultSet();
        try {
            if (rs.next()) {
                do
                    msg += rs.getString("fcName") + ", ";
                while (rs.next());
                msg = msg.substring(0, msg.lastIndexOf(','));
                ba.sendSmartPrivateMessage(name, "Registerd players:");
                ba.smartPrivateMessageSpam(name, wrapLines(msg));
            } else
                ba.sendSmartPrivateMessage(name, "No registered players found.");
        } catch (SQLException e) {
            Tools.printStackTrace(e);
        } finally {
            ba.SQLClose(rs);
        }
    }
    
    public void handleEvent(Message event) {
        String name = event.getMessager();
        if (name == null || name.length() < 1)
            name = ba.getPlayerName(event.getPlayerID());
        String msg = event.getMessage();
        String cmd = msg.toLowerCase();
        int type = event.getMessageType();
        
        if (type == Message.PRIVATE_MESSAGE || type == Message.REMOTE_PRIVATE_MESSAGE) { 
            if (cmd.equals("!signup"))
                cmd_signup(name);
            else if (cmd.equals("!help"))
                cmd_help(name);
            
            if (!oplist.isSmod(name))
                return;
            else if (cmd.equals("!count"))
                cmd_count(name);
            else if (cmd.equals("!reg"))
                cmd_registered(name);
            else if (cmd.equals("!die"))
                cmd_die(name);
        }
    }
    
    private void cmd_help(String name) {
        String[] help = {
                     "+-- Trench Wars Draft Tournament Signup ----------------------------------------.",
                     "| !signup                  - Registers for participation in TWDT                |",
                     "|                                                                               |",
        };
        
        String[] smod = {
                     "+-- SMod Commands --------------------------------------------------------------|",
                     "| !count                   - Counts the number of players currently registered  |",
                     "| !reg                     - Lists all the players registered to play           |",
                     "| !die                     - Kills the bot                                      |",
                     "`-------------------------------------------------------------------------------'"
        };
        String end = "`-------------------------------------------------------------------------------'";
        ba.smartPrivateMessageSpam(name, help);
        if (oplist.isSmod(name))
            ba.smartPrivateMessageSpam(name, smod);
        else
            ba.sendSmartPrivateMessage(name, end);
    }
    
    private void cmd_signup(String name) {
        DBPlayerData dbP = new DBPlayerData(m_botAction, db, name);

        // a name has to be registered
        if (!dbP.isRegistered())
            m_botAction.sendPrivateMessage(name, "Your name is not registered. You must send !register to TWDBot in ?go twd before you signup for TWDT.");
        else if (!dbP.isEnabled())
            m_botAction.sendPrivateMessage(name, "Your name is not enabled. You must be registered and enabled in TWD to signup for TWDT.");
        else {
            ResultSet rs = null;
            
            try {
                rs = ba.SQLQuery(db, "SELECT ftUpdated as t FROM tblDraft__Player WHERE fnSeason = " + season + " AND fcName = '" + Tools.addSlashesToString(name) + "' LIMIT 1");
                if (rs.next()) {
                    String t = rs.getString("t");
                    t = t.substring(0, 10) + " at " + t.substring(11, 16);
                    ba.sendSmartPrivateMessage(name, "You already signed up on " + t + ".");
                } else {
                    ba.SQLBackgroundQuery(db, null, "INSERT INTO tblDraft__Player (fnPlayerID, fnSeason, fcName) VALUES(" + dbP.getUserID() + ", " + season + ", '" + Tools.addSlashesToString(name) + "')");
                    ba.SQLBackgroundQuery(db, null, "INSERT INTO tblDraft__PlayerReg (fcName, fnSeason) VALUES('" + Tools.addSlashesToString(name) + "', " + season + ")");
                    ba.sendSmartPrivateMessage(name, "Signup successful!");
                }
            } catch (SQLException e) {
                ba.sendSmartPrivateMessage(name, "An error occured and your signup did not complete.");
                Tools.printStackTrace(e);
            } finally {
                ba.SQLClose(rs);
            }
        }
    }
    
    private void cmd_count(String name) {
        ResultSet rs = null;
        try {
            rs = ba.SQLQuery(db, "SELECT COUNT(fnPlayerID) as c FROM tblDraft__Player WHERE fnSeason = " + season + "");
            
            if (rs.next())
                ba.sendSmartPrivateMessage(name, "Total players registerd: " + rs.getInt("c"));
            else
                ba.sendSmartPrivateMessage(name, "No players have signed up.");
        } catch (SQLException e) {
            ba.sendSmartPrivateMessage(name, "An error occured and your request could not be completed.");
            Tools.printStackTrace(e);
        } finally {
            ba.SQLClose(rs);
        }
    }
    
    private void cmd_registered(String name) {
        ba.sendSmartPrivateMessage(name, "Processing request...");
        ba.SQLBackgroundQuery(db, name, "SELECT fcName FROM tblDraft__Player WHERE fnSeason = " + season + " ORDER BY ftUpdated ASC");
    }
    
    private void cmd_die(String name) {
        ba.sendSmartPrivateMessage(name, "Goodbye, and have a nice day! After all, you get to LIVE. You're not dead or dying... yet");
        new Die();
    }
    
    private String[] wrapLines(String msg) {
        ArrayList<String> lines = new ArrayList<String>();
        while (msg.length() > 0) {
            if (msg.length() > MAX_CHARS) {
                int end = msg.lastIndexOf(' ', MAX_CHARS);
                lines.add(msg.substring(0, end));
                msg = msg.substring(end + 1);
            } else {
                lines.add(msg);
                msg = "";
            }
        }
        return lines.toArray(new String[lines.size()]);
    }
    
    private class Die extends TimerTask {
        
        public Die() {
            ba.scheduleTask(this, 3000);
        }
        
        public void run() {
            ba.die();
        }
        
    }

}
