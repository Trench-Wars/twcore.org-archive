package twcore.bots.pubbot;

import java.sql.ResultSet;
//import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
//import twcore.core.events.SQLResultEvent;
import twcore.core.util.Tools;

public class pubbotnewbie extends PubBotModule{
    public static final String ZONE_CHANNEL = "Zone Channel";
    public static final String PUBSYSTEM = "TW-Pub1";
    
    private String currentInfoName = "";
    private String database = "website";

    private HashMap<String, Integer> loopCatcher;
    private HashMap<String, AliasCheck> aliases;
    private boolean pub;

    @Override
    public void initializeModule() {
        this.aliases = new HashMap<String, AliasCheck>();
        this.loopCatcher = new HashMap<String, Integer>();
        pubCheck();
    }

    @Override
    public void cancel() {
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.MESSAGE);
    }
    
    private void pubCheck() {
        if (PUBSYSTEM.equals(m_botAction.getFuzzyPlayerName(PUBSYSTEM)))
            this.pub = true;
        else
            this.pub = false;
    }
    
    public void handleEvent(PlayerEntered event) {
        pubCheck();
    }

    public void handleEvent(Message event) {
        String message = event.getMessage();
        if (pub && event.getMessageType() == Message.ARENA_MESSAGE)
        {
            if (message.contains("TypedName:")) {
                currentInfoName = message.substring(message.indexOf("TypedName:")+10);
                currentInfoName = currentInfoName.substring(0, currentInfoName.indexOf("Demo:")).trim();
            }
            
            if (message.startsWith("TIME: Session:")) {
                String time = message.substring(message.indexOf("Total:")+6);
                time = time.substring(0, time.indexOf("Created")).trim();
                
                String[] pieces = time.split(":");
                
                if (pieces.length==3) {
                    if (pieces[0].equals("0")) { // if usage less than 1 hour
                        
                        int hour = Integer.valueOf(pieces[0]);
                        int min = Integer.valueOf(pieces[1]);

                        if (aliases.containsKey(currentInfoName)) {
                            AliasCheck alias = aliases.get(currentInfoName);
                            alias.setUsage(hour * 60 + min);
                            System.out.println("[ALIAS] " + alias.getName() + " in array already.");
                            if (alias.getTime() > 900000) {
                                alias.resetTime();
                                doAliasCheck(alias);
                            }
                        } else {
                            AliasCheck alias = new AliasCheck(currentInfoName, hour *  60 + min);
                            alias.setUsage(hour * 60 + min);
                            dataCheck(alias);
                        }
                    }
                }
            }
        }
    }
    
    /** old method
    public void handleEvent(SQLResultEvent event) {

        m_botAction.sendSmartPrivateMessage("WingZero", "got SQLResultEvent");
        ResultSet resultSet = event.getResultSet();
        if(resultSet == null)
            return;
        
        if (event.getIdentifier().startsWith("alias:")) {

            String name = event.getIdentifier().substring(event.getIdentifier().lastIndexOf(":")+1);
            AliasCheck alias = aliases.get(name);
            if (alias==null)
                return;
            
            // GET IP + MID
            if (event.getIdentifier().startsWith("alias:ip:")) {

                StringBuffer buffer = new StringBuffer();
                try {
                    resultSet.beforeFirst();
                    while(resultSet.next()) {
                        buffer.append(", ");
                        buffer.append(resultSet.getString("fnIP"));
                    }
                } catch (Exception e) { e.printStackTrace(); }
                
                if (buffer.length()>2)
                    alias.setIpResults("(" + buffer.toString().substring(2) + ") ");
                else {
                    System.out.println("[ALIAS] " + buffer.toString());
                    final String aliasIP = alias.getName();
                    Integer count = 0;
                    if (loopCatcher.containsKey(aliasIP)) {
                        Integer[] tasks = loopCatcher.get(aliasIP);
                        if (tasks.length < 2)
                            tasks = new Integer[] {1, 0};
                        else
                            count = tasks[0] + 1;
                    }
                    if (count > 5)
                        alias.setIpResults("");
                    if (alias.getIpResults() == null && database!=null) {
                        TimerTask delayIP = new TimerTask() {
                            @Override
                            public void run() {
                                System.out.println("[ALIAS] Blank IP: " + aliasIP + " Task Scheduled.");
                                m_botAction.SQLBackgroundQuery(database, "alias:ip:"+aliasIP,
                                        "SELECT DISTINCT(fnIP) " +
                                        "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " +
                                        "WHERE fcUserName = '" + Tools.addSlashes(aliasIP) + "'");
                            }
                        };
                        m_botAction.scheduleTask(delayIP, 10000);
                    }
                }
                
            }
            else if (event.getIdentifier().startsWith("alias:mid:")) {

                StringBuffer buffer = new StringBuffer();
                try {
                    resultSet.beforeFirst();
                    while(resultSet.next()) {
                        buffer.append(", ");
                        buffer.append(resultSet.getString("fnMachineId"));
                    }
                } catch (Exception e) { e.printStackTrace(); }

                if (buffer.length()>2)
                    alias.setMidResults("(" + buffer.toString().substring(2) + ") ");
                else {
                    final String aliasMID = alias.getName();
                    Integer count = 0;
                    if (loopCatcher.containsKey(aliasMID)) {
                        Integer[] tasks = loopCatcher.get(aliasMID);
                        if (tasks.length < 2)
                            tasks = new Integer[] {0, 1};
                        else
                            count = tasks[1] + 1;
                    }
                    if (count > 5)
                        alias.setMidResults("");
                    if (alias.getMidResults() == null && database!=null) {
                        TimerTask delayMID = new TimerTask() {
                            @Override
                            public void run() {
                                System.out.println("[ALIAS] Blank MID: " + aliasMID + " Task Scheduled.");
                                m_botAction.SQLBackgroundQuery(database, "alias:mid:"+aliasMID,
                                        "SELECT DISTINCT(fnMachineId) " +
                                        "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " +
                                        "WHERE fcUserName = '" + Tools.addSlashes(aliasMID) + "'");
                            }
                        };
                        m_botAction.scheduleTask(delayMID, 13000);
                    }
                }

            }
            
            // Retrieve the final query using IP+MID
            if (event.getIdentifier().startsWith("alias:final:")) {

                HashSet<String> prevResults = new HashSet<String>();
                int numResults = 0;

                try {
                    while(resultSet.next()) {
                        String username = resultSet.getString("fcUserName");
                        if(!prevResults.contains(username)){
                            prevResults.add(username);
                            numResults++;
                        }
                    }
                } catch (Exception e) { }

                alias.setAliasCount(numResults);
                sendNewPlayerAlert(alias);              
                
            }
            // Send final query if we have IP+MID
            else if (alias.getIpResults() != null && alias.getMidResults() != null) {
                
                if (alias.getIpResults().equals("") || alias.getMidResults().equals("")) {
                    alias.setAliasCount(0);
                    String reason = alias.getIpResults().equals("") ? "ip" : "mid";
                    if (alias.getIpResults().equals("") && alias.getMidResults().equals(""))
                        reason = "ip&mid";
                    System.out.println("[ALIAS] " + alias.getName() + " (empty:" + reason + ")");
                    sendNewPlayerAlert(alias);      
                } else {
                    m_botAction.SQLBackgroundQuery(database, "alias:final:"+name,
                            "SELECT * " +
                            "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " +
                            "WHERE fnIP IN " + alias.getIpResults() + " " +
                            "AND fnMachineID IN " + alias.getMidResults() + " ORDER BY fdUpdated DESC");
                }
                
            }
            
            m_botAction.SQLClose(event.getResultSet());
        }

    }
    **/

    private void sendNewPlayerAlert(AliasCheck alias) {

        System.out.print("[ALIAS] " + alias.getName() + ":" + alias.getUsage() + ":" + alias.getAliasCount());
        if (alias.getUsage() < 15 && alias.getAliasCount() < 20 && alias.getAliasCount() >= 0) {
            m_botAction.sendSmartPrivateMessage(PUBSYSTEM, "New Player: " + alias.getName());
            m_botAction.ipcSendMessage(ZONE_CHANNEL, "alert >>>>>> New player alert(" + alias.getAliasCount() + "): " + alias.getName(), PUBSYSTEM, m_botAction.getBotName());
            System.out.println(":YES");
        } else {
            System.out.println(":NO");
        }

    }
    
    private void dataCheck(AliasCheck alias) {
        try {
            if (alias.getUserID() < 0) {
                ResultSet rs = m_botAction.SQLQuery(database, "SELECT fnUserID FROM tblUser WHERE fcUserName = '" + Tools.addSlashesToString(alias.getName()) + "' ORDER BY fnUserID ASC LIMIT 1");
                if (rs.next()) {
                    alias.setUserID(rs.getInt("fnUserID"));
                }
                m_botAction.SQLClose(rs);
            }
            
            if (alias.getUserID() > 0) {
                if (alias.getIpResults() == null) {
                    ResultSet rs = m_botAction.SQLQuery(database, "SELECT DISTINCT(fnIP) FROM tblAlias WHERE fnUserID = " + alias.getUserID());

                    StringBuffer buffer = new StringBuffer();
                    rs.beforeFirst();
                    while(rs.next()) {
                        buffer.append(", ");
                        buffer.append(rs.getString("fnIP"));
                    }
                    m_botAction.SQLClose(rs);
                    
                    if (buffer.length()>2)
                        alias.setIpResults("(" + buffer.toString().substring(2) + ") ");
                }
                
                if (alias.getMidResults() == null) {
                    ResultSet rs = m_botAction.SQLQuery(database, "SELECT DISTINCT(fnMachineID) FROM tblAlias WHERE fnUserID = " + alias.getUserID());

                    StringBuffer buffer = new StringBuffer();
                    rs.beforeFirst();
                    while(rs.next()) {
                        buffer.append(", ");
                        buffer.append(rs.getString("fnMachineID"));
                    }
                    m_botAction.SQLClose(rs);
                    
                    if (buffer.length()>2)
                        alias.setMidResults("(" + buffer.toString().substring(2) + ") ");
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        
        if (alias.getUserID() < 0 || alias.getIpResults() == null || alias.getMidResults() == null) {
            int count = 1;
            if (loopCatcher.containsKey(alias.getName()))
                count = loopCatcher.get(alias.getName()) + 1;
            else
                loopCatcher.put(alias.getName(), count);
            
            if (count < 5) {
                final AliasCheck ac = alias;
                TimerTask delay = new TimerTask() {
                    @Override
                    public void run() {
                        dataCheck(ac);
                    }
                };
                m_botAction.scheduleTask(delay, 10000);
            } else
                loopCatcher.remove(alias.getName());
        } else {
            doAliasCheck(alias);
        }
    }

    // Alias check using background queries
    private void doAliasCheck(AliasCheck alias) {
        aliases.put(alias.getName(), alias);
        //m_botAction.SQLBackgroundQuery(database, "alias:ip:" + alias.getName(), "SELECT DISTINCT(fnIP) " + "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " + "WHERE fcUserName = '" + Tools.addSlashes(alias.getName()) + "'");
        //m_botAction.SQLBackgroundQuery(database, "alias:mid:" + alias.getName(), "SELECT DISTINCT(fnMachineId) " + "FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID " + "WHERE fcUserName = '" + Tools.addSlashes(alias.getName()) + "'");
        if (alias.getIpResults() != null && alias.getMidResults() != null) {
            try {
                ResultSet rs = m_botAction.SQLQuery(database, "SELECT * FROM `tblAlias` INNER JOIN `tblUser` ON `tblAlias`.fnUserID = `tblUser`.fnUserID WHERE fnIP IN " + alias.getIpResults() + "AND fnMachineID IN " + alias.getMidResults() + " ORDER BY fdUpdated DESC");

                HashSet<String> prevResults = new HashSet<String>();
                int numResults = 0;
                while(rs.next()) {
                    String username = rs.getString("fcUserName");
                    if(!prevResults.contains(username)){
                        prevResults.add(username);
                        numResults++;
                    }
                }
                m_botAction.SQLClose(rs);

                alias.setAliasCount(numResults);
                sendNewPlayerAlert(alias); 

            } catch (Exception e) { e.printStackTrace(); }
        }
        
    }

    private class AliasCheck {
        private String name;
        private int userID = -1;
        private String ipResults;
        private String midResults;
        private int usage; // in minutes
        private int aliasCount = -1;
        private long time;

        public AliasCheck(String name, int usage) {
            this.name = name;
            this.usage = usage;
            this.time = System.currentTimeMillis();
        }
        
        public long getTime() {
            return System.currentTimeMillis() - time;
        }
        
        public void resetTime() {
            time = System.currentTimeMillis();
        }
        
        public int getUserID() {
            return userID;
        }
        
        public void setUserID(int id) {
            this.userID = id;
        }

        public String getName() {
            return name;
        }

        public int getUsage() {
            return usage;
        }

        public void setUsage(int usage) {
            this.usage = usage;
        }

        public int getAliasCount() {
            return aliasCount;
        }

        public void setAliasCount(int count) {
            this.aliasCount = count;
        }

        public String getIpResults() {
            return ipResults;
        }

        public void setIpResults(String ipResults) {
            this.ipResults = ipResults;
        }

        public String getMidResults() {
            return midResults;
        }

        public void setMidResults(String midResults) {
            this.midResults = midResults;
        }
    }

}
