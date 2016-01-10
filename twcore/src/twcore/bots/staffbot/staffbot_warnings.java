package twcore.bots.staffbot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.Module;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.events.Message;
import twcore.core.util.Tools;

public class staffbot_warnings extends Module {

    private String sqlHost = "website";

    private final static int MAX_NAME_SUGGESTIONS = 20;         // Max number of suggestions given
    // for a name match in !warn
    // When altering the expire time, don't forget to check staffbot_banc for:
    // BANC_EXPIRE_TIME, SQL_EXPIRE_TIME and BANC_EXPIRE_TIME_TEXT
    private final static int WARNING_EXPIRE_TIME = Tools.TimeInMillis.DAY * 28;
    // private final static int CHECK_LOG_DELAY = 30000; // Delay when *log is checked for *warnings UNNEEDED FOR RIGHT NOW

    private TimerTask getLog;
    private Vector<String> lastWarnings = new Vector<String>(20);	// Holds track of last 20 warnings

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Helps (strange to redefine each time someone types !help)
    final String[] helpER = { 
            "--------------------[ Warnings: ER+ ]----------------------", 
            " !warnings <player>        - Checks valid red warnings on specified player",
            " ! <player>                - (shortcut for above)", 
            " !allwarnings <player>     - Shows all warnings on player, including expired",
            " !fuzzyname <player>       - Checks for names similar to <player> in database",
            " !addnote <player>         - Adds a note on the specified player",
            " !listnotes <player>       - Lists all notes for the specified player",
            " !removenote <id>          - Removes note of id specified in listnotes" 
        };

    final String[] helpMod = { 
            "--------------------[ Warnings: Mod+ ]---------------------", 
            " !deletelast <player>      - Deletes last warning given to a player." 
        };

    final String[] helpSmod = { 
            "--------------------[ Warnings: SMod+ ]--------------------", 
            " !warningsfrom <player>    - Displays a list of recent warns given to a player.",
            " !manual player:warning    - Adds a manual database warning to player. Use with caution!" 
        };

    private void addManualWarning(String name, String message) {
        StringTokenizer argTokens = new StringTokenizer(message, ":");
        if (argTokens.countTokens() == 2) {
            String player = argTokens.nextToken();
            String warning = argTokens.nextToken();
            String[] paramNames = { "name", "warning", "staffmember", "timeofwarning" };
            Calendar thisTime = Calendar.getInstance();
            java.util.Date day = thisTime.getTime();
            String warntime = sdf.format(day);
            String date = sdf.format(System.currentTimeMillis()).toString();
            String[] data = { Tools.addSlashes(player.toLowerCase().trim()), new String(warntime + ": (" + Tools.addSlashes(name) + ") to (" + player + ")" + " " + warning), Tools.addSlashes(name.toLowerCase().trim()), date };

            m_botAction.SQLInsertInto(sqlHost, "tblWarnings", paramNames, data);
            m_botAction.sendSmartPrivateMessage(name, "Inserted warning (" + warning + ") to player (" + player + ")");
            m_botAction.sendChatMessage(2, "Staffer " + name + " has inserted a manual warning to player " + player);
        } else {
            m_botAction.sendSmartPrivateMessage(name, "Formatting Syntax Error: Please use PlayerName:Warning to proceed.");
        }

    }

    @Override
    public void cancel() {
        m_botAction.cancelTask(getLog);
    }

    /**
     * Deletes a player's last warning by overwriting its text with relevant data.
     * 
     * @param name
     *            Staffer requesting
     * @param message
     *            Name of player whose l
     * @param showExpired
     */
    public void deleteLastWarning(String name, String message) {
        String query = "SELECT * FROM tblWarnings WHERE name = '" + Tools.addSlashes(message.toLowerCase()) + "' ORDER BY timeofwarning DESC";
        try {
            ResultSet set = m_botAction.SQLQuery(sqlHost, query);
            if (set.next()) {
                String warner = set.getString("staffmember");
                if (!name.toLowerCase().equals(warner)) {
                    if (!opList.isSmod(name)) {
                        m_botAction.sendRemotePrivateMessage(name, "You must be SMod+ to delete warnings that you didn't issue yourself.");
                    }
                }
                String warningText = set.getString("warning");
                java.sql.Date date = new java.sql.Date(System.currentTimeMillis());
                m_botAction.SQLQueryAndClose(sqlHost, "UPDATE tblWarnings SET warning='DEL: (Warning deleted by " + name + " on " + new SimpleDateFormat("dd MMM yyyy kk:mm:ss").format(date)
                        + ")' WHERE warning='" + Tools.addSlashesToString(warningText) + "'");
                String[] text;
                if (warningText.contains("Ext: "))
                    text = warningText.split("Ext: ", 2);
                else
                    text = warningText.split(": ", 2);
                if (text.length == 2)
                    m_botAction.sendRemotePrivateMessage(name, "Warning deleted: " + text[1]);
                else
                    m_botAction.sendRemotePrivateMessage(name, "Warning deleted.");
            } else {
                m_botAction.sendRemotePrivateMessage(name, "No warnings found for '" + message + "'.  Use the exact name.");
            }
            m_botAction.SQLClose(set);
        } catch (SQLException e) {
            Tools.printStackTrace(e);
        }

    }

    /**
     * Based on a given name fragment, find other players that start with the fragment.
     * 
     * @param name
     *            Staffer running cmd
     * @param message
     *            Name fragment
     */
    public void getFuzzyNames(String name, String message) {
        ArrayList<String> fuzzynames;

        fuzzynames = getFuzzyNamesDB(message);

        if (fuzzynames.size() > 0) {
            m_botAction.sendRemotePrivateMessage(name, "Names in database starting with '" + message + "':");
            m_botAction.remotePrivateMessageSpam(name, fuzzynames.toArray(new String[fuzzynames.size()]));
            if (fuzzynames.size() == MAX_NAME_SUGGESTIONS)
                m_botAction.sendRemotePrivateMessage(name, "Results limited to " + MAX_NAME_SUGGESTIONS + ", refine your search further if you have not found the desired result.");
        } else {
            m_botAction.sendRemotePrivateMessage(name, "No names found starting with '" + message + "'.");
        }
    }

    private ArrayList<String> getFuzzyNamesDB(String name) {
        ArrayList<String> fuzzynames = new ArrayList<String>();

        String query = "" + "SELECT DISTINCT(name) " + "FROM tblWarnings " + "WHERE name LIKE '" + Tools.addSlashes(name.toLowerCase()) + "%' " + "ORDER BY name LIMIT 0," + MAX_NAME_SUGGESTIONS;

        try {
            ResultSet set = m_botAction.SQLQuery(sqlHost, query);
            while (set.next()) {
                fuzzynames.add(" " + set.getString("name"));
            }
            m_botAction.SQLClose(set);
        } catch (SQLException sqle) {
            Tools.printLog("SQLException encountered in Staffbot.getFuzzyNamesDB(): " + sqle.getMessage());
        }

        return fuzzynames;
    }

    public void queryAddNote(String name, String message) {
        String[] msg = message.split(":");
        if(msg.length != 2){
            m_botAction.sendSmartPrivateMessage( name, "Incorrect usage. Example: !addnote player:note");
            return;
        }
        String query = "INSERT INTO tblWarningsNotes (fcUserName,fcNote,fcStaffer) VALUES ('" +
            Tools.addSlashesToString(msg[0].toLowerCase()) + "','" +
            Tools.addSlashesToString(msg[1]) + "','" +
            Tools.addSlashesToString(name.toLowerCase()) + "')";
        m_botAction.SQLBackgroundQuery(sqlHost, null, query);
    }

    public void queryRemoveNote(String name, String message) {
        int id;
        try {
            id = Integer.parseInt(message);
        } catch (NumberFormatException e) {
            m_botAction.sendSmartPrivateMessage(name, "Incorrect usage. Example: !removenote <id of note>");
            return;
        }
        String query = "DELETE FROM tblWarningsNotes WHERE fnID = '" + Tools.addSlashesToString(message) + "'";
        m_botAction.SQLBackgroundQuery(sqlHost, null, query);
    }

    public void queryListNotes(String name, String message) {
        String query = "SELECT fnId,fcNote FROM tblWarningsNotes WHERE fcUserName = '" + Tools.addSlashes(message.toLowerCase()) + "' ORDER BY fnId";

        try {
            ResultSet set = m_botAction.SQLQuery(sqlHost, query);
            m_botAction.sendSmartPrivateMessage(name, "===== Notes for user: " + message + " =====");
            while (set.next()) {
                m_botAction.sendSmartPrivateMessage(name, "   " + set.getString("fnId") + ": " + set.getString("fcNote"));
            }
            m_botAction.SQLClose(set);
        } catch (SQLException sqle) {
            Tools.printLog("SQLException encountered in Staffbot.queryListNotes(): " + sqle.getMessage());
        }
    }

    @Override
    public void handleEvent(Message event) {
        short sender = event.getPlayerID();
        String message = event.getMessage();
        boolean remote = event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE;
        String name = remote ? event.getMessager() : m_botAction.getPlayerName(sender);
        OperatorList m_opList = m_botAction.getOperatorList();

        // Save *warns into the database
        if (event.getMessageType() == Message.ARENA_MESSAGE) {
            if (message.toLowerCase().indexOf(" *warn ") > 0 && lastWarnings.contains(message) == false) {
                String temp;
                String staffMember;
                String warnedPlayer;
                String warning;
                String time;

                temp = message.substring(message.indexOf(") to ") + 5);
                warnedPlayer = temp.substring(0, temp.indexOf(":"));
                temp = message.substring(message.indexOf("Ext: ") + 5);
                staffMember = temp.substring(0, temp.indexOf(" (")).trim();
                warning = message.substring(message.toLowerCase().indexOf(" *warn ") + 7);
                if (warning.length() > 50) {
                    warning = warning.substring(0, 49).trim();
                    warning += "...";
                }
                time = message.substring(message.lastIndexOf(" ", message.indexOf(":  Ext:")) + 1, message.indexOf(":  Ext:"));

                if (!m_opList.isBot(staffMember))
                    return;

                String[] paramNames = { "name", "warning", "staffmember", "timeofwarning" };
                String date = sdf.format(System.currentTimeMillis()).toString();
                String[] data = { Tools.addSlashes(warnedPlayer.toLowerCase()), Tools.addSlashes(message), Tools.addSlashes(staffMember.toLowerCase()), date };

                m_botAction.SQLInsertInto(sqlHost, "tblWarnings", paramNames, data);

                // Send a chat message to the smod chat stating that staffer warned a player
                m_botAction.sendChatMessage(2, "[" + time + "] " + staffMember + " issued a warning towards " + warnedPlayer + " (\"" + warning + "\")");

                // Add this warning to the lastWarnings Vector so it isn't inserted into the database on the next check
                lastWarnings.add(0, message);
                lastWarnings.setSize(20);
            }

            return;
        }

        // Ignore non-private messages
        if (event.getMessageType() != Message.PRIVATE_MESSAGE && event.getMessageType() != Message.REMOTE_PRIVATE_MESSAGE)
            return;
        // Ignore non-commands
        if (!message.startsWith("!"))
            return;
        // Ignore player's commands
        if (!m_opList.isBot(name))
            return;

        if (message.toLowerCase().startsWith("!help")) {
            if (m_opList.isER(name)) {
                m_botAction.smartPrivateMessageSpam(name, helpER);
            }
            if (m_opList.isModerator(name)) {
                m_botAction.smartPrivateMessageSpam(name, helpMod);
            }
            if (m_opList.isSmod(name)) {
                m_botAction.smartPrivateMessageSpam(name, helpSmod);
            }
        }

        if (m_opList.isER(name)) {
            if (message.toLowerCase().startsWith("!warning ")) {
                queryWarnings(name, message.substring(9), false);
            } else if (message.toLowerCase().startsWith("!warnings ")) {
                queryWarnings(name, message.substring(10), false);
            } else if (message.toLowerCase().startsWith("! ")) {
                queryWarnings(name, message.substring(2), false);
            } else if (message.toLowerCase().startsWith("!allwarnings ")) {
                queryWarnings(name, message.substring(13), true);
            } else if (message.toLowerCase().startsWith("!fuzzyname ")) {
                getFuzzyNames(name, message.substring(11));
            } else if (message.toLowerCase().startsWith("!addnote ")) {
                queryAddNote(name, message.substring(9));
            } else if (message.toLowerCase().startsWith("!listnotes ")) {
                queryListNotes(name, message.substring(11));
            } else if (message.toLowerCase().startsWith("!removenote ")) {
                queryRemoveNote(name, message.substring(12));
            }
        }

        if (m_opList.isModerator(name)) {
            if (message.toLowerCase().startsWith("!deletelast "))
                deleteLastWarning(name, message.substring(12));
        }

        if (m_opList.isSmod(name)) {
            if (message.toLowerCase().startsWith("!warningsfrom "))
                queryWarningsFrom(name, message.substring(14));
            if (message.toLowerCase().startsWith("!manual "))
                addManualWarning(name, message.substring(8).trim());
        }

    }

    @Override
    public void initializeModule() {

        // removed and added to staffbot
        // TimerTask to check the logs for *warnings
        /*
        getLog = new TimerTask() {
            public void run() {
                m_botAction.sendUnfilteredPublicMessage( "*log" );
            }
        };

        m_botAction.scheduleTaskAtFixedRate( getLog, 0, CHECK_LOG_DELAY );
        */

    }

    /**
     * Queries the database for stored warnings on a player.
     * 
     * @param name
     *            Staffer requesting
     * @param message
     *            Player to query
     * @param showExpired
     *            Whether or not to display expired warnings
     */
    public void queryWarnings(String name, String message, boolean showExpired) {
        String query = "SELECT * FROM tblWarnings WHERE name = '" + Tools.addSlashes(message.toLowerCase()) + "' ORDER BY timeofwarning ASC";
        ArrayList<String> warnings = new ArrayList<String>();

        try {
            ResultSet set = m_botAction.SQLQuery(sqlHost, query);

            if (set == null) {
                m_botAction.sendRemotePrivateMessage(name, "ERROR: There is a problem with your query (returned null) or the database is down.  Please report this to bot development.");
                return;
            }

            // Lookup the warnings from the database
            int numExpired = 0;
            int numTotal = 0;

            int splitCount = 0;

            while (set.next()) {
                String warning = set.getString("warning");
                java.sql.Date date = set.getDate("timeofwarning");
                SimpleDateFormat f = new SimpleDateFormat("dd-MM-yyyy");
                java.sql.Date pre = new java.sql.Date(f.parse("15-11-2012").getTime());
                java.sql.Date expireDate = new java.sql.Date(System.currentTimeMillis() - WARNING_EXPIRE_TIME);
                boolean expired = date.before(expireDate) || date.before(pre);
                if (expired)
                    numExpired++;
                if (!expired || showExpired) {
                    String strDate = new SimpleDateFormat("dd MMM yyyy").format(date);

                    String[] text;
                    String tempWarn = "";
                    String tempWarn2 = "";
                    if (warning.contains("Ext: "))
                        text = warning.split("Ext: ", 2);
                    else
                        text = warning.split(": ", 2);

                    if (text.length == 2) {
                        tempWarn = strDate + "  " + text[1];
                        if (tempWarn.length() > 190) {
                            tempWarn2 = tempWarn.substring(190);
                            tempWarn = tempWarn.substring(0, 190);
                            splitCount++;
                            warnings.add(tempWarn);
                            warnings.add(tempWarn2);
                        } else
                            warnings.add(tempWarn);
                    }
                }
                numTotal++;
            }

            m_botAction.SQLClose(set);

            // Respond to the user
            if (numTotal > 0) {
                if (showExpired) {   // !allwarnings
                    m_botAction.sendRemotePrivateMessage(name, "Warnings in database for " + message + ":");
                    m_botAction.remotePrivateMessageSpam(name, warnings.toArray(new String[warnings.size()]));
                    m_botAction.sendRemotePrivateMessage(name, "Displayed " + (warnings.size() - splitCount) + " warnings (including " + numExpired + " expired warnings).");
                } else {              // !warnings
                    if (warnings.size() > 0) {
                        m_botAction.sendRemotePrivateMessage(name, "Warnings in database for " + message + ":");
                        m_botAction.remotePrivateMessageSpam(name, warnings.toArray(new String[warnings.size()]));
                        m_botAction.sendRemotePrivateMessage(name, "Displayed " + (warnings.size() - splitCount) + " valid warnings (suppressed " + numExpired + " expired)."
                                + (numExpired > 0 ? " PM !allwarnings to display all." : ""));
                    } else {
                        m_botAction.sendRemotePrivateMessage(name, "No active warnings for " + message + ".");
                        m_botAction.sendRemotePrivateMessage(name, "There are " + numExpired + " expired warnings. PM !allwarnings to display these.");
                    }

                }
            } else {
                m_botAction.sendRemotePrivateMessage(name, "No warnings found for '" + message + "'.");

                ArrayList<String> fuzzynames = getFuzzyNamesDB(message);
                if (fuzzynames.size() > 0) {
                    m_botAction.sendRemotePrivateMessage(name, "_");
                    m_botAction.sendRemotePrivateMessage(name, "Maybe you were searching for the warnings of one of the following players?");
                    m_botAction.remotePrivateMessageSpam(name, fuzzynames.toArray(new String[fuzzynames.size()]));
                    m_botAction.sendRemotePrivateMessage(name, "PM !warning <name> to see the warnings on one of these names.");
                }
            }
        } catch (SQLException e) {
            Tools.printStackTrace(e);
        } catch (ParseException e) {
            Tools.printStackTrace(e);
        }
    }

    public void queryWarningsFrom(String name, String message) {
        String query = "SELECT * FROM tblWarnings WHERE staffmember = \"" + Tools.addSlashes(message.toLowerCase()) + "\" ORDER BY timeofwarning DESC LIMIT 0,50";

        try {
            ResultSet set = m_botAction.SQLQuery(sqlHost, query);

            m_botAction.sendRemotePrivateMessage(name, "Last (max 50) warnings in database given by " + message + ":");
            while (set.next()) {
                String warning = set.getString("warning");
                String strDate = new SimpleDateFormat("dd MMM yyyy").format(sdf.parse(set.getString("timeofwarning")));
                String[] text = warning.split(": ", 3);
                if (text.length == 3)
                    m_botAction.sendRemotePrivateMessage(name, strDate + "  - " + text[2]);
            }
            m_botAction.sendRemotePrivateMessage(name, "End of list.");
            m_botAction.SQLClose(set);
        } catch (SQLException e) {
            Tools.printStackTrace(e);
        } catch (ParseException e) {
            Tools.printStackTrace(e);
        }
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
    }

}
