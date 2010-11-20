package twcore.bots.staffbot;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimerTask;

import twcore.bots.Module;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

/**
 * StaffBot BanC module
 * 
 *  - automatic-silence
 *  - automatic-spec-lock
 *  - automatic-kick-lock
 *  - automatic-super-spec-lock @author quiles/dexter
 *  - !search feature @author quiles/dexter
 *  - !lifted feature @author quiles
 *  changed lifted banc to a new field instead of deleting.
 *  TODO:
 *   
 *   - Time left in !listban #ID
 * 
 * @author Maverick
 */
public class staffbot_banc extends Module {
    
	// Helps screens
    final String[] helpER = {
            "---------------------- [ BanC: Operators+ ] -------------------------------------------------------------------------",
            " !silence <player>:<time>[mins][d]         - Initiates an automatically enforced",
            "                                               silence on <player> for <time/mins/days>.",
            " !superspec <player>:<time>[mins][d]       - Initiates an automatically enforced",
            "                                               spec-lock on <player> the ships 2,4 and 8",
            "                                               for <time/mins/days>",
            " !spec <player>:<time>[mins][d]            - Initiates an automatically enforced",
            "                                               spectator-lock on <player>",
            "                                               for <time/mins/days>.",
            " !search -help                             - !search command help guide",
            " !search <player>[:<#banC>][:<#Warnings>]  - Search the players history",
            " !listban -help                 - !listban command help guide",
            " !listban [arg] [count]         - Shows last 10/[count] BanCs. Optional arguments see below.",
            " !listban [#id]                 - Shows information about BanC with <id>.",
            " !changeban <#id> <arguments>   - Changes banc with <id>. Arguments see below. Don't forget the #",
            
            //arguments commented by quiles. because I coded the !listban -help to teach how to use !listban easily.
            /*" Arguments:",
            "             -player='<..>'     - Specifies player name",
            "             -d=#               - Specifies duration in minutes",
            "             -a=<...>           - Specifies access requirement, options; mod / smod / sysop",
            "             -ip=<#.#.#.#> -ir  - Specifies IP or remove IP (-ir) so banc is not matched by IP",
            "             -mid=#  -mr        - Specifies MID or remove MID (-mr) so banc is not matched by MID",
            "             -notif=<yes/no>    - Specifies wether a notification is sent on staff chat",
            "             -staffer='<..>'    - Specifies the name who issued the ban. [Only avail. on !listban]",*/
            
            " !bancomment <#id> <comments>   - Adds / Changes comments on BanC with specified #id.",
            " !liftban <#id>                 - Removes ban with #id.",
            " !banaccess                     - Returns the access level restrictions",
            //" !shortcutkeys                  - Shows the available shortcut keys for the commands above"
    };

    final String[] helpSmod = {
            "----------------------[ BanC: SMod+ ]---------------------",
            " !reload                           - Reloads the list of active bancs from the database",
            " !forcedb                          - Forces to connect to the database",
            " !searchip <ip>                    - where ip can be the x. or x.x. or full x.x.x",
            " !addop                            - Adds a Banc Operator",
            " !removeop                         - Removes a Banc Operator and adds them to Revoked list",
            " !listops                          - Displays all Banc Operators and Banc Revoked Operators",
            " !deleteop                         - If removed from staff, use this."
    };
    
    final String[] shortcutKeys = {
    		"Available shortcut keys:",
    		" !silence  -> !s   |  !listban    -> !lb",
    		" !spec     -> !sp  |  !changeban  -> !cb",
    		//" !kick     -> !k   |  !bancomment -> !bc",
    };
    
    // Definition variables
    public enum BanCType {
    	SILENCE, SPEC, SUPERSPEC
    }       
    
   // private staffbot_database Database = new staffbot_database();
    
   // private List<String> bancOps;
    //private ArrayList<String> bancStaffers;
    HashMap <String,String> bancStaffers   = new HashMap<String,String>();

    HashMap <String,String> bancRevoked   = new HashMap<String,String>();
   // private ArrayList<String> bancRevoked;
    
    private final String botsDatabase = "bots";
    private final String trenchDatabase = "website";
    private final String uniqueConnectionID = "banc";
    private final String IPCBANC = "banc";
    private final String IPCALIAS = "pubBots";

    
    private final String MINACCESS_BANCSTAFFER = "BANCSTAFF";
    //private final String MINACCESS_ER = "ER";
    //private final String MINACCESS_MOD = "MOD";
    private final String MINACCESS_SMOD = "SMOD";
    private final String MINACCESS_SYSOP = "SYSOP";
    
    private final Integer[][] BANCLIMITS = {
    		{ 60*24*7, 60*24*7, 0},	// BanC limits
    		{ 60*24*7, 60*24*7, 0},		// [BanCType] [Accesslevel]
    		{ 60*24*7, 60*24*7, 0}
    }; 
    private SimpleDateFormat datetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private final int BANC_MAX_DURATION = 525600;	// (365 days in minutes)
    
    // Operation variables
    private List<BanC> activeBanCs = Collections.synchronizedList(new ArrayList<BanC>());
    
    boolean stop = false;
    
    // PreparedStatements
	private PreparedStatement psListBanCs, psCheckAccessReq, psActiveBanCs, psAddBanC, psUpdateComment, psRemoveBanC, psLookupIPMID, psKeepAlive1, psKeepAlive2;
	
	// Keep database connection alive workaround
	private KeepAliveConnection keepAliveConnection = new KeepAliveConnection();


    
    
    
    @Override
	public void initializeModule() {
        //*/
    	// Initialize Prepared Statements
    	psActiveBanCs = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "SELECT * FROM tblBanc WHERE DATE_ADD(fdCreated, INTERVAL fnDuration MINUTE) > NOW() AND fbLifted = 0 OR fnDuration = 0");
    	//psListBanCs = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "SELECT * FROM tblBanc LIMIT 0,?");
    	psCheckAccessReq = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "SELECT fcMinAccess FROM tblBanc WHERE fnID = ?");
    	psAddBanC = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "INSERT INTO tblBanc(fcType, fcUsername, fcIP, fcMID, fcMinAccess, fnDuration, fcStaffer, fdCreated) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())", true);
    	psUpdateComment = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "UPDATE tblBanc SET fcComment = ? WHERE fnID = ?");
    	//psRemoveBanC = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "DELETE FROM tblBanc WHERE fnID = ?");
    	psRemoveBanC = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "UPDATE tblBanc SET fbLifted = 1 WHERE fnID = ?");
    	psLookupIPMID = m_botAction.createPreparedStatement(trenchDatabase, uniqueConnectionID, "SELECT fcIpString, fnMachineId FROM tblAlias INNER JOIN tblUser ON tblAlias.fnUserID = tblUser.fnUserID WHERE fcUserName = ? ORDER BY fdUpdated DESC LIMIT 0,1");
    	psKeepAlive1 = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "SHOW DATABASES");
    	psKeepAlive2 = m_botAction.createPreparedStatement(trenchDatabase, uniqueConnectionID, "SHOW DATABASES");
    	
    	if( psActiveBanCs == null || psCheckAccessReq == null || psAddBanC == null || psUpdateComment == null || psRemoveBanC == null || psLookupIPMID == null || psKeepAlive1 == null || psKeepAlive2 == null) {
    		Tools.printLog("BanC: One or more PreparedStatements are null! Module BanC disabled.");
	        m_botAction.sendChatMessage(2, "BanC: One or more connections (prepared statements) couldn't be made! Module BanC disabled.");
	        //this.cancel();
	        
    	} else {
    		// Join IPC channels
    		m_botAction.ipcSubscribe(IPCALIAS);
    		m_botAction.ipcSubscribe(IPCBANC);
    		
    		// load active BanCs
    		loadActiveBanCs();
    		
    		// Send out IPC messages for active BanCs
    		sendIPCActiveBanCs(null);
    		
    		
    		// Start TimerTasks
    		CheckExpiredBanCs checkExpiredBanCs = new CheckExpiredBanCs();
    		m_botAction.scheduleTaskAtFixedRate(checkExpiredBanCs, Tools.TimeInMillis.MINUTE, Tools.TimeInMillis.MINUTE);
    		
    		//Schedule the timertask to keep alive the database connection
            m_botAction.scheduleTaskAtFixedRate(keepAliveConnection, 5 * Tools.TimeInMillis.MINUTE, 2 * Tools.TimeInMillis.MINUTE);
            
            //Load the operators
            restart_ops();
    	}
	}
    
    @Override
	public void cancel() {
    	stop = true;
    	
    	m_botAction.ipcUnSubscribe(IPCALIAS);
		m_botAction.ipcUnSubscribe(IPCBANC);
		
    	m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psCheckAccessReq);
	    m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psListBanCs);
	    m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psActiveBanCs);
	    m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psAddBanC);
	    m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psUpdateComment);
	    m_botAction.closePreparedStatement(botsDatabase, uniqueConnectionID, psRemoveBanC);
	    m_botAction.closePreparedStatement(trenchDatabase, uniqueConnectionID, psLookupIPMID);
	    m_botAction.closePreparedStatement(trenchDatabase, uniqueConnectionID, psKeepAlive1);
	    m_botAction.closePreparedStatement(trenchDatabase, uniqueConnectionID, psKeepAlive2);
	    
	    m_botAction.cancelTasks();
	}

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.MESSAGE);
	}
	
	public void handleEvent(Message event) {
		if(stop) return;
		
		if(event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE || event.getMessageType() == Message.PRIVATE_MESSAGE) {
		    
		    String message = event.getMessage();
			String messageLc = message.toLowerCase();
	        String name = event.getMessager() == null ? m_botAction.getPlayerName(event.getPlayerID()) : event.getMessager();
	        OperatorList opList = m_botAction.getOperatorList();
	        
	        
	                    // Minimum BANC STAFFER access requirement for all !commands
                        //Everytime a someone tries a command, reload the ops and see if they have access
	                    restart_ops();
	                 if(!bancStaffers.containsKey(name.toLowerCase())){
	                      m_botAction.sendSmartPrivateMessage(name, "Sorry, Your access to BanC has been revoked");
	                      return; 
	                      
	            
	            }
	        
	        
	        if( messageLc.startsWith("!addop"))
	            //!addop
	            addBancOperator(name, messageLc.substring(7));
	        if( messageLc.startsWith("!removeop"))
	            //!revokeop
	            removeBancStaffer(name, messageLc.substring(10));
	        if( messageLc.equals("!listops"))
	            showBancPeople(name);
	        if( messageLc.startsWith("!deleteop"))
	            deleteBancOperator(name, messageLc.substring(10));
	        if( messageLc.startsWith("!isop"))
	            isOp(name, messageLc.substring(6));
	        if( messageLc.equals("!reloadops"))
	            if(!opList.isOwner(name))
	                return;
	            restart_ops();
	        // !help
	        if(messageLc.startsWith("!help")) {
	        	cmdHelp(name, message.substring(5).trim());
	        }
	        else if(messageLc.startsWith("!lifted"))
	            searchByLiftedBancs(name);
	        
	        else if( messageLc.startsWith("!searchip"))
	            searchByIp(name, message.substring(9));
	        
	        else if( messageLc.startsWith("!search -help"))
	            searchByNameHelp(name);
	        
	        else if( messageLc.startsWith("!search"))
            {
                String commandTillName = messageLc.split(":")[0];
                String nameToSearch = commandTillName.substring(8);
                int limits[] = getLimits(messageLc);
                this.searchByName(name, nameToSearch, limits[0], limits[1]);
            
            }
        
	        // !banaccesss
	        else if(messageLc.startsWith("!banaccess")) {
	        	cmdBanAccess(name, message.substring(10).trim());
	        }
	        
	        // !shortcutkeys
	        else if(messageLc.startsWith("!shortcutkeys")) {
	        	cmdShortcutkeys(name);
	        }
	        
	        // !silence <player>:<time/mins>
	        // !spec <player>:<time/mins>
	        // !kick <player>:<time/mins>	[mod+]
	        else if( messageLc.startsWith("!silence") || messageLc.startsWith("!s") ||
	        		messageLc.startsWith("!spec") || messageLc.startsWith("!sp ") ||
	        		messageLc.startsWith("!superspec")) {
			   //(messageLc.startsWith("!kick") && opList.isModerator(name)) ||
			   //(messageLc.startsWith("!k") && opList.isModerator(name))) {
				cmdSilenceSpecKick(name, message);
			}
	        
	        else if( messageLc.startsWith("!listban -help")) {
                cmdListBanHelp(name);
            }
	        // !listban [arg] [count]
            // !listban [#id]
	        else if( messageLc.startsWith("!listban")) {
	        	cmdListBan(name, message.substring(8).trim(), true);
	        }
	        else if( messageLc.startsWith("!lb")) {
	        	cmdListBan(name, message.substring(3).trim(), true);
	        }
	        
	        // !changeban <#id> <arguments>
	        else if( messageLc.startsWith("!changeban")) {
	        	cmdChangeBan(name, message.substring(10).trim());
	        }
	        else if( messageLc.startsWith("!cb")) {
	        	cmdChangeBan(name, message.substring(3).trim());
	        }
	        
	        // !bancomment <#id> <comments>
	        else if( messageLc.startsWith("!bancomment")) {
	        	cmdBancomment(name, message.substring(11).trim());
	        }
	        else if( messageLc.startsWith("!bc")) {
	        	cmdBancomment(name, message.substring(3).trim());
	        }
	        
	        // !liftban <#id>
	        else if( messageLc.startsWith("!liftban")) {
	        	cmdLiftban(name, message.substring(8).trim());
	        }
	        // !reload [Smod+]
	        else if( messageLc.startsWith("!reload") && opList.isDeveloper(name)) {
	        	cmdReload(name);
	        }
	        
	        else if( messageLc.startsWith("!listactive") && opList.isDeveloper(name)) {
	        	cmdListActiveBanCs(name);
	        }
	        
	        else if( messageLc.startsWith("!forcedb") && opList.isDeveloper(name) ){
	            doForceDBConnection(name);
	        
	        }}
		
	}
	private void isOp(String name, String substring) {
        if(!opList.isSmod(name))
            return;
        restart_ops();
        BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("BancStaffers");
        if(ops.contains(substring.toLowerCase())){
            m_botAction.sendSmartPrivateMessage(name, "Staffer " +substring+ " is a Banc Operator");
        } else
                m_botAction.sendSmartPrivateMessage(name, "Sorry, " +substring+ " is not a Banc Operator");
            }
        

        
    

    private void deleteBancOperator(String name, String message) {
	    if(!opList.isSmod(name))
            return;
        restart_ops();
        BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("BancStaffers");

        
        
        int spot = ops.indexOf(message);
        if (spot == 0 && ops.length() == message.length()) {
            ops = "";
            m_botAction.sendSmartPrivateMessage(name, "Delete Op: " + message + " successful");
        }
        else if (spot == 0 && ops.length() > message.length()) {
            ops = ops.substring(message.length() + 1);
            m_botAction.sendSmartPrivateMessage(name, "Delete Op: " + message + " successful");
        } 
        else if (spot > 0 && spot + message.length() < ops.length()) {
            ops = ops.substring(0, spot) + ops.substring(spot + message.length() + 1);
            m_botAction.sendSmartPrivateMessage(name, "Delete Op: " + message + " successful");
        }
        else if (spot > 0 && spot == ops.length() - message.length()) {
            ops = ops.substring(0, spot - 1);
            m_botAction.sendSmartPrivateMessage(name, "Delete Op: " + message + " successful");
        }
        else {
            m_botAction.sendSmartPrivateMessage(name, "Delete Op: " + message + " successful");
        }  
        
        m_botSettings.put("BancStaffers", ops);
        m_botSettings.save();
        m_botAction.sendChatMessage(2, "Staffer " +name+ " deleted operator " +message);
        is_revoked(name, message);
        restart_ops();
        }
        
    

    /**
	 * !addop
	 * @param name
	 * @param substring
	 */
	
	private void addBancOperator(String name, String substring) {
	    //SMod+ only command.
	    if(!opList.isSmod(name))
	        return;
        
	    BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("BancStaffers");


        if(ops.contains(substring)){
            m_botAction.sendSmartPrivateMessage(name, substring + " is already an operator.");
            return;
            }
        if (ops.length() < 1)
            m_botSettings.put("BancStaffers", substring);
        else
            m_botSettings.put("BancStaffers", ops + "," + substring);
        m_botAction.sendSmartPrivateMessage(name, "Add Op: " + substring + " successful");
        m_botSettings.save();
        m_botAction.sendChatMessage(2, "Staffer " +name+ " added operator " +substring);
        is_revoked(name, substring);
        restart_ops();
        }
    
        
    
	private void is_revoked(String name, String substring) {
	    //if they operator is on revoked list, remove them from it
	    BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("BancRevoked");
         int spot = ops.indexOf(substring);
        if (spot == 0 && ops.length() == substring.length()) {
            ops = "";
        }
        else if (spot == 0 && ops.length() > substring.length()) {
            ops = ops.substring(substring.length() + 1);
        } 
        else if (spot > 0 && spot + substring.length() < ops.length()) {
            ops = ops.substring(0, spot) + ops.substring(spot + substring.length() + 1);
        }
        else if (spot > 0 && spot == ops.length() - substring.length()) {
            ops = ops.substring(0, spot - 1);
            
        }
        else {
            m_botAction.sendSmartPrivateMessage(name, "This person was NOT revoked.");
        }
        m_botSettings.put("BancRevoked", ops);
        m_botSettings.save();
        restart_ops();
        return;
        
    }

    public void restart_ops() {
        //Load the operators and add them
	    try {
	        BotSettings m_botSettings = m_botAction.getBotSettings();
        bancStaffers.clear();
        bancRevoked.clear();
        //
        String ops[] = m_botSettings.getString( "BancStaffers" ).split( "," );
        for( int i = 0; i < ops.length; i++ )
           bancStaffers.put(ops[i].toLowerCase(), ops[i]);
        
        //
        String revoked[] = m_botSettings.getString( "BancRevoked" ).split( "," );
        for( int j = 0; j < revoked.length; j++ )
            bancRevoked.put(revoked[j].toLowerCase(), revoked[j]);
	    } catch (Exception e) { Tools.printStackTrace( "Method Failed: ", e ); }
	    


    }
    /**
     * !listops
     * @param name
     */
	
	 public void showBancPeople( String name ) {
	     //SMod Only command
	     if(!opList.isSmod(name))
	         return;
	        restart_ops();
            String bancs = "Banc Access: ";
	        Iterator<String> list = bancStaffers.values().iterator();
	        
	        
	        while( list.hasNext() ) {
	            if( list.hasNext() )
	                bancs += (String)list.next() + ", ";
	            else
	                bancs += (String)list.next();
	        }
	        String bancs1 = "Revoked Access: ";
	        Iterator<String> list1 = bancRevoked.values().iterator();
	        
            while( list1.hasNext() ) {
                if( list1.hasNext() )
                    bancs1 += (String)list1.next() + ", ";
                else
                    bancs1 += (String)list1.next();
            }
            
	        
	        bancs  = bancs.substring(0, bancs.length() - 2);
	        bancs1 = bancs1.substring(0, bancs1.length() - 2);
	        m_botAction.sendSmartPrivateMessage( name, bancs  );
            m_botAction.sendSmartPrivateMessage( name, bancs1 );
	 }
	 
/**
 * !removeop
 * @param name
 * @param message
 */
	
	private void removeBancStaffer(String name, String message) {
	    if(!opList.isSmod(name))
	        return;
	    restart_ops();
	    BotSettings m_botSettings = m_botAction.getBotSettings();
	    String ops = m_botSettings.getString("BancStaffers");
	    String ops1 = m_botSettings.getString("BancRevoked");

	    
	    
	    if(ops1.contains(message)){
	    m_botAction.sendSmartPrivateMessage(name, "Operator is already revoked.");
	    return;
	    }
	    
        int spot = ops.indexOf(message);
        if (spot == 0 && ops.length() == message.length()) {
            ops = "";
            m_botAction.sendSmartPrivateMessage(name, "Remove Op: " + message + " successful");
        }
        else if (spot == 0 && ops.length() > message.length()) {
            ops = ops.substring(message.length() + 1);
            m_botAction.sendSmartPrivateMessage(name, "Remove Op: " + message + " successful");
        } 
        else if (spot > 0 && spot + message.length() < ops.length()) {
            ops = ops.substring(0, spot) + ops.substring(spot + message.length() + 1);
            m_botAction.sendSmartPrivateMessage(name, "Remove Op: " + message + " successful");
        }
        else if (spot > 0 && spot == ops.length() - message.length()) {
            ops = ops.substring(0, spot - 1);
            m_botAction.sendSmartPrivateMessage(name, "Remove Op: " + message + " successful");
        }
        else {
            m_botAction.sendSmartPrivateMessage(name, "Remove Op: " + message + " failed, operator doesn't exist");
            return;
        }
        m_botSettings.put("BancStaffers", ops);
        m_botSettings.save();
        m_botAction.sendChatMessage(2, "Staffer " +name+ " removed operator " +message);
        remove_op(name, message);
        restart_ops();
        }
	
	   
	
/**
 * 
 * @param name
 * @param message
 */
	private void remove_op(String name, String message) {
	    //If operator is operator list, remove them from it
        BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("BancRevoked");
        if (ops.length() < 1)
            m_botSettings.put("BancRevoked", message);
        else
            m_botSettings.put("BancRevoked", ops + "," + message);
        m_botAction.sendSmartPrivateMessage(name, "Operator added to Revoke Power list");
        m_botSettings.save();
       restart_ops();
       return;
    }
        
    

    private void searchByLiftedBancs(String name){
	    cmdListBan(name, "-lifted", true);
	}
    /***
	 * !search -help command explaining how to use it.
	 * @author quiles
	 */
	private void searchByNameHelp(String name) {
        // TODO Auto-generated method stub
	    ArrayList<String> list = new ArrayList<String>();
	    
	    String helpSearch = "Hi, I'll explain you how to use !search feature.";
	    list.add(helpSearch);
	    
	    helpSearch = "The main functionality is to search the whole player's history with this command.";
	    list.add(helpSearch);
	    
	    helpSearch = "Try !search quiles:-1:-1 to search everything about quiles (All banCs and warnings - latests and expireds)";
	    list.add(helpSearch);
	    
	    helpSearch = "But you can customizable it: Try !search quiles:5:5 (Latest 5 banCs and 5 warnings)";
	    list.add(helpSearch);
	    
	    helpSearch = "And then if you just use !search quiles, it'll give you all banCs and just the active warnings.";
	    list.add(helpSearch);
	    
	    helpSearch = "Simple like that, enjoy!";
	    list.add(helpSearch);
	    
	    m_botAction.remotePrivateMessageSpam(name, list.toArray(new String[list.size()]));
	}

    /**
	 * 
	 * @author quiles/dexter
	 * Search feature
	 * Extract'd method here: into sendBancs and sendWarnings.
	 * 
	 * @see getLimits method
	 * */
	private void searchByName(String stafferName, String name, int limitBanCs, int limitWarnings){
        try{
            sendBanCs(stafferName, name, limitBanCs);
            sendWarnings(stafferName, name, limitWarnings);
            
        }catch(SQLException e){
            e.printStackTrace();
            m_botAction.sendPrivateMessage("quiles", e.toString());
        }catch(Exception e){
            e.printStackTrace();
        }
        
    }

    private boolean sendBanCs(String stafferName, String name, int limit) throws SQLException{
        this.cmdListBan(stafferName, "-player='"+name+"'",false);
        /* List<String> list = new ArrayList<String>();
        
        String query;
        
        if(limit == -1)
            query = "SELECT * from tblBanc WHERE fcUsername = '"+name+"' ORDER BY fdCreated ASC";
        else
            query = "SELECT * from tblBanc WHERE fcUsername = '"+name+"' ORDER BY fdCreated ASC "+" LIMIT 0,"+limit;
            
        ResultSet rs = m_botAction.SQLQuery(botsDatabase, query);
    
        if(rs == null )
            m_botAction.sendSmartPrivateMessage(stafferName, "No banCs made on the player "+name);
    
        else{
            String result = "BanCs: ";
            
            while(rs.next()){
                result += Tools.formatString(rs.getString("fcUsername"), 10);
                result += Tools.formatString(rs.getString("fcType"), 10);
                
                String IP = rs.getString("fcIp");
                
                if(IP == null)
                    IP = "(UNKNOWN)";
                
                result += "IP: "+Tools.formatString(IP, 15);
                
                String MID = rs.getString("fcMID");
                if(MID == null)
                    MID = "(UNKNOWN)";
                
                result += "MID: "+Tools.formatString(MID, 10);
                
                int duration = rs.getInt("fnDuration");
                boolean isDay = duration >= 1440? true:false;
                
                if(isDay){
                    duration = (duration/60)/24;
                    result += Tools.formatString(" Duration: "+duration+" days", 19);
                    }
                else
                    result += Tools.formatString(" Duration: "+duration+" mins", 19);
                
                result += Tools.formatString(" by: " + rs.getString("fcStaffer"), 17);
                String comments = rs.getString("fcComment");
                
                if(comments == null)
                    comments = "No Comments";
                
                list.add(result);
                list.add(comments);
            }
            String strSpam[] = list.toArray(new String[list.size()]);
            m_botAction.remotePrivateMessageSpam(stafferName, strSpam);
            
            return true;
        }*/
        return true;
    }

    private boolean sendWarnings(String stafferName, String name, int limit) throws SQLException{
        
        String query;
        
        if(limit == 0 || limit == -1)
            query = "SELECT * FROM tblWarnings WHERE name = '"+name+"' ORDER BY timeofwarning ASC";
        else
            query  = "SELECT * FROM tblWarnings WHERE name = '"+name+"' ORDER BY timeofwarning ASC LIMIT 0,"+limit;
        
        ResultSet rs = m_botAction.SQLQuery(this.trenchDatabase, query);
        
        List<String> lastestWarnings = new ArrayList<String>();
        List<String> expiredWarnings;
        expiredWarnings = new ArrayList<String>();
        
        while(rs.next()){
            
            String warningStr = rs.getString("warning");
            String stringDateExpired = "";
            String stringDateNotExpired = "";
            Date date = rs.getDate("timeofwarning");
            int expiredTime = Tools.TimeInMillis.WEEK * 2; //last month
            
            Date expireDate = new java.sql.Date(System.currentTimeMillis() - expiredTime);
            
            
            if( date.before(expireDate) )
                stringDateExpired = new SimpleDateFormat("dd MMM yyyy").format(date);
            else
                stringDateNotExpired = new SimpleDateFormat("dd MMM yyyy").format(date);
            
            String warningSplitBecauseOfExt[];
            
            if(warningStr.contains("Ext: "))
                warningSplitBecauseOfExt = warningStr.split("Ext: ",2);
            else
                warningSplitBecauseOfExt = warningStr.split(": ",2);
            
            if(date.before(expireDate) && warningSplitBecauseOfExt.length == 2){ //expired warnings AND warnings done correctly in database
                expiredWarnings.add(stringDateExpired + " " + warningSplitBecauseOfExt[1]);
            }else if( warningSplitBecauseOfExt.length == 2) //lastest warnings AND warnings done correctly in database
                lastestWarnings.add(stringDateNotExpired + " " + warningSplitBecauseOfExt[1]);
            
        }
        
        if(lastestWarnings.size() > 0)
        {
            
            m_botAction.sendRemotePrivateMessage(stafferName, " ------ Lastest warnings (last 2 weeks): ");
            m_botAction.remotePrivateMessageSpam(stafferName, lastestWarnings.toArray(new String[lastestWarnings.size()]));
        }
        
        if(limit == 0){
            m_botAction.sendRemotePrivateMessage(stafferName, "There are "+expiredWarnings.size()+" expired warnings. Use !search <player>:[limits]:[limitWarning] to see");
            m_botAction.sendRemotePrivateMessage(stafferName, "You can see all the player's history too typing !search player:-1:-1");
        }
        else if(expiredWarnings.size() > 0){
            m_botAction.sendRemotePrivateMessage(stafferName, " ------ Expired warnings (more than 2 weeks): ");
            m_botAction.remotePrivateMessageSpam(stafferName, expiredWarnings.toArray(new String[lastestWarnings.size()]));
        }
        
        return true;
    }

    private int[] getLimits(String commandSearch){

        //vector to limits of banc and warning. [0] = #banc. [1] = #warnings
        int limits[] = {0,0};
	    
	    if( commandSearch.contains(":") )
        {
	        String stPieces [] = commandSearch.split(":");
	        if(stPieces.length == 3){
	            //to limit #bancs and #warnings
	            limits[0] = Integer.parseInt(stPieces[1]);
	            limits[1] = Integer.parseInt(stPieces[2]);
	        }
	        else if(stPieces.length == 2){
	            //to limits just #bancs and see all warnings
	            limits[0] = Integer.parseInt(stPieces[1]);
	        }
        }
	    return limits;
	}
	
    /**
     * Search ip feature - shortkut to !listban -ip=
     * Changed the query in listban to find ips starting with substring. "x." - where like 'ipstr%'
     * */
    private void searchByIp(String stafferName, String ipString){
        this.cmdListBan(stafferName, "-ip="+ipString, true);
    }
    
	private void cmdListBanHelp(String name) {
        // TODO Auto-generated method stub
        //!listban -player='name'
	    List<String> listBanHelp = new ArrayList<String>();
	    String helpStr = "";
	    
	    helpStr = "    Hi, I'm your help guide. How to use !listban in the best way, so it can be useful?";
	    listBanHelp.add(helpStr);
	    
	    helpStr = "    There are few arguments you can do and model your own !listban: ";
	    listBanHelp.add(helpStr);
	    
	    helpStr = "    !listban -player='quiles'        -   to search all bancS of the playername quiles, for example.";
	    listBanHelp.add(helpStr);
	    
	    helpStr = "    Don't forget the ''";
	    listBanHelp.add(helpStr);

	    helpStr = "    !listban -d=60                   -   to search lastest banCs with duration of 60.";
	    listBanHelp.add(helpStr);

	    helpStr = "    try !listban -d=30 too, 20, 15, etc!";
	    listBanHelp.add(helpStr);

	    helpStr = "    You can also combine those both above. Try !listban -player='quiles' -d=60";
	    listBanHelp.add(helpStr);
	    
	    helpStr = "    !listban -ip=74.243.233.254      -   to search all bancs of the ip 74.243.233.254. ";
	    listBanHelp.add(helpStr);
	    
	    helpStr = "    !listban -staffer='quiles'       -   to search all bancs done by the staffer quiles.";
	    listBanHelp.add(helpStr);
        
	    helpStr = "    Don't forget the ''";
	    listBanHelp.add(helpStr);
	    
	    helpStr = "    You can combine all those arguments above into:";
	    listBanHelp.add(helpStr);
        	    
	    helpStr = "    Check out !listban -player='Mime' -staffer='Dexter' to see all bancs done on Mime by Dexter.";
	    listBanHelp.add(helpStr);
	    
	    helpStr = "    Some examples of !listban combinations: ";
	    listBanHelp.add(helpStr);
        
	    helpStr = "    !listban -ip=x.x.x -player='playername' -d=mins -staffer='staffername'";
	    listBanHelp.add(helpStr);
	    
	    String spamPM[] = listBanHelp.toArray(new String[listBanHelp.size()]);
        m_botAction.remotePrivateMessageSpam(name, spamPM);
	}

    /* (non-Javadoc)
	 * @see twcore.bots.Module#handleEvent(twcore.core.events.InterProcessEvent)
	 */
	@Override
	public void handleEvent(InterProcessEvent event) {
		if(stop) return;
		if(!(event.getObject() instanceof IPCMessage)) return;
		
		String altNickToSuperSpec;
		if(IPCALIAS.equals(event.getChannel()) && ((IPCMessage)event.getObject()).getMessage().startsWith("info ")) {
			IPCMessage message = (IPCMessage)event.getObject();
			StringTokenizer arguments = new StringTokenizer(message.getMessage().substring(5), ":");
			
			if(arguments.countTokens() == 3) {
				String playerName = arguments.nextToken();
				String playerIP = arguments.nextToken();
				String playerMID = arguments.nextToken();
				
				// Look trough active bans if it matches
				for(BanC banc : this.activeBanCs) {
					boolean match = false;
					
					if(banc.playername != null && banc.playername.equalsIgnoreCase(playerName)) {
						// username match
						match = true;
						
						//Tools.printLog("BanC username match on '"+playerName+"', IP:"+playerIP+", MID:"+playerMID+". BanC #"+banc.id+" ; ("+banc.playername+","+banc.IP+","+banc.MID+") duration="+banc.duration+", start="+banc.created.getTime());
					} else if((banc.IP != null && banc.IP.equals(playerIP)) && (banc.MID != null && banc.MID.equals(playerMID))) {
						// IP and MID match
						match = true;
						
						//Tools.printLog("BanC IP & MID match on '"+playerName+"', IP:"+playerIP+", MID:"+playerMID+". BanC #"+banc.id+" ; ("+banc.playername+","+banc.IP+","+banc.MID+") duration="+banc.duration+", start="+banc.created.getTime());
					} else if((banc.IP != null && banc.IP.equals(playerIP)) && banc.MID == null) {
						// IP and unknown MID match
						match = true;
						
						//Tools.printLog("BanC IP & ?MID? match on '"+playerName+"', IP:"+playerIP+", MID:"+playerMID+". BanC #"+banc.id+" ; ("+banc.playername+","+banc.IP+","+banc.MID+") duration="+banc.duration+", start="+banc.created.getTime());
					} else if(banc.IP == null && (banc.MID != null && banc.MID.equals(playerMID))) {
						// unknown IP and MID match
						match = true;
						
						//Tools.printLog("BanC ?IP? & MID match on '"+playerName+"', IP:"+playerIP+", MID:"+playerMID+". BanC #"+banc.id+" ; ("+banc.playername+","+banc.IP+","+banc.MID+") duration="+banc.duration+", start="+banc.created.getTime());
					}
					
					if(match) {
						// Match Ffound on one or more properties
						// Send BanC object to pubbotbanc to BanC the player
						banc.calculateExpired();
						altNickToSuperSpec = playerName;//banc.playername;
						//SUPERSPEC TIME:OLDNICK:NEWNICK
						if(banc.type.equals(BanCType.SUPERSPEC) && !banc.playername.equals(altNickToSuperSpec)){
                            m_botAction.ipcSendMessage(IPCBANC, banc.getType().toString()+" "+banc.duration+":"+banc.playername+":"+altNickToSuperSpec, null, "banc");
                            m_botAction.sendSmartPrivateMessage("quiles", "ALT NICK: "+altNickToSuperSpec);
                            banc.playername = altNickToSuperSpec; //updating last nickname to the !liftban
						}else if(!banc.type.equals(BanCType.SUPERSPEC))
						    m_botAction.ipcSendMessage(IPCBANC, banc.getType().toString()+" "+banc.duration+":"+playerName, null, "banc");
					}
				}
			}
		}
		if(IPCBANC.equals(event.getChannel()) && event.getSenderName().toLowerCase().startsWith("tw-guard")) {
			IPCMessage ipc = (IPCMessage)event.getObject();
			String command = ipc.getMessage();
			
			// On initilization of a pubbot, send the active bancs to that pubbot
			if(command.equals("BANC PUBBOT INIT")) {
				sendIPCActiveBanCs(ipc.getSender());
			}
			else if(command.startsWith(BanCType.SILENCE.toString())) {
				BanC banc = lookupActiveBanC(BanCType.SILENCE, command.substring(8));
				if(banc != null && banc.isNotification()) {
					m_botAction.sendChatMessage("Player '"+banc.getPlayername()+"' has been (re)silenced.");
					
				} else if(banc == null) {
					m_botAction.sendChatMessage("Player '"+command.substring(8)+"' has been (re)silenced.");
				}
				
			} else if(command.startsWith("REMOVE "+BanCType.SILENCE.toString())) {
				BanC banc = lookupActiveBanC(BanCType.SILENCE, command.substring(15));
				if(banc != null && banc.isNotification()) {
					m_botAction.sendChatMessage("Player '"+banc.getPlayername()+"' has been unsilenced.");
				} else if(banc == null) {
					m_botAction.sendChatMessage("Player '"+command.substring(15)+"' has been unsilenced.");
				}
			} else if(command.startsWith(BanCType.SPEC.toString())) {
				BanC banc = lookupActiveBanC(BanCType.SPEC, command.substring(5));
				if(banc != null && banc.isNotification()) {
					m_botAction.sendChatMessage("Player '"+banc.getPlayername()+"' has been (re)locked in spectator.");
				} else if(banc == null) {
					m_botAction.sendChatMessage("Player '"+command.substring(5)+"' has been (re)locked in spectator.");
				}//SPEC PLAYER
				 //012345
			} else if(command.startsWith(BanCType.SUPERSPEC.toString())){
			    //SUPERSPEC PLAYER
			    //0123456789T
			    BanC banc = lookupActiveBanC(BanCType.SUPERSPEC, command.substring(10));
			    if(banc != null && banc.isNotification()){
			        m_botAction.sendChatMessage("Player '"+banc.getPlayername()+"' has been (re)superlocked in spectator. He tried to enter in ship: 2, 4 or 8.");
			    } else if(banc == null){
			        m_botAction.sendChatMessage("Player '"+command.substring(10)+"' has been (re)superlocked in spectator. He tried to enter in ship: 2, 4 or 8.");
			    }
			}else if(command.startsWith("REMOVE "+BanCType.SUPERSPEC.toString())) {
			    //REMOVE SUPERSPEC
                BanC banc = lookupActiveBanC(BanCType.SUPERSPEC, command.substring(17));
                if(banc != null && banc.isNotification()) {
                    m_botAction.sendChatMessage("Player '"+banc.getPlayername()+"' has been unsuper-specced.");
                } else if(banc == null) {
                    m_botAction.sendChatMessage("Player '"+command.substring(15)+"' has been unsuper-specced.");
                }
            } else if(command.startsWith("REMOVE "+BanCType.SPEC.toString())) {
				BanC banc = lookupActiveBanC(BanCType.SPEC, command.substring(12));
				if(banc != null && banc.isNotification()) {
					m_botAction.sendChatMessage("Player '"+banc.getPlayername()+"' has had the speclock removed.");
				} else if(banc == null) {
					m_botAction.sendChatMessage("Player '"+command.substring(12)+"' has had the speclock removed.");
				}
			//} else if(command.startsWith(BanCType.KICK.toString())) {
				//BanC banc = lookupActiveBanC(BanCType.KICK, command.substring(5));
				//if(banc != null && banc.isNotification()) {
					//m_botAction.sendChatMessage("Player '"+banc.getPlayername()+"' has been kicked.");
				//} else if(banc == null) {
					//m_botAction.sendChatMessage("Player '"+command.substring(5)+"' has been kicked.");
				}
			}
		}
	//}
	
	/**
	 * Handles the !help command
	 * @param name player who issued the command
	 * @param parameters any command parameters
	 */
	private void cmdHelp(String name, String parameters) {
	    
		m_botAction.smartPrivateMessageSpam(name, helpER);
    	
    	if(opList.isSmod(name))
    		m_botAction.smartPrivateMessageSpam(name, helpSmod);
	}
	
	/**
	 * Handles the !banaccess command
	 * @param name player who issued the command
	 * @param parameters any command parameters
	 */
	private void cmdBanAccess(String name, String parameters) {
        /*		Limitations on BanC by access level
	            		 
        		                       ER      MOD     SMOD    SYSOP
        		 Silence time/mins     10      60      240     none
        		 Speclock time/mins    30      60      120     none
        		 Auto-kick time/mins   n/a     30      60      none
        */
		
		m_botAction.sendRemotePrivateMessage(name, "Limitations on BanC by access level");
    	m_botAction.sendRemotePrivateMessage(name, " ");
    	m_botAction.sendRemotePrivateMessage(name, "                       OPS     SMOD    SYSOP");
    	
    	for(int type = 0 ; type < BANCLIMITS.length ; type++) {
    		String line = "";
			switch(type) {
				case 0: line += " Silence time/mins"; break;
				case 1: line += " Speclock time/mins"; break;
				case 2: line += " SuperSpec time/mins"; break;
				//case 2: line += " Auto-kick time/mins"; break; 
			}
			line = Tools.formatString(line, 23);
			
    		for(int level = 0 ; level < BANCLIMITS[0].length ; level++) {
    			String limit = "";
    			if(BANCLIMITS[type][level] == null) {
    				limit += "n/a";
    			} else if(BANCLIMITS[type][level].intValue() == 0) {
    				limit += "none";
    			} else {
    				limit += String.valueOf(BANCLIMITS[type][level]);
    			}
    			if(level < (BANCLIMITS[0].length-1)) {
    				limit = Tools.formatString(limit, 8);
    			}
    			line += limit;
    		}
    		
    		m_botAction.sendRemotePrivateMessage(name, line);
    	}
	}
	
	/**
	 * Handles the !shortcutkeys command
	 * 
	 * @param name player who issued the command
	 */
	private void cmdShortcutkeys(String name) {
		m_botAction.smartPrivateMessageSpam(name, shortcutKeys);
	}
	
	/**
	 * Handles the !silence, !spec and !kick command
	 * @param name player who issed the command
	 * @param message full message that the player sent
	 */
	private void cmdSilenceSpecKick(String name, String message) {
		String timeStr = "10";
		String parameters = "";
		BanCType bancType = BanCType.SILENCE;
		String bancName = "";
		String messageLc = message.toLowerCase();
		
		if(messageLc.startsWith("!silence")) {
			parameters = message.substring(8).trim();
			bancType = BanCType.SILENCE;
			bancName = "auto-silence";
		} else if(messageLc.startsWith("!s ")) {
			parameters = message.substring(2).trim();
			bancType = BanCType.SILENCE;
			bancName = "auto-silence";
			
		} else if(messageLc.startsWith("!spec")) {
			parameters = message.substring(5).trim();
			bancType = BanCType.SPEC;
			bancName = "auto-speclock";
		} else if(messageLc.startsWith("!sp ")) {
			parameters = message.substring(3).trim();
			bancType = BanCType.SPEC;
			bancName = "auto-speclock";
		
		} else if(messageLc.startsWith("!superspec")){
		    //!superspec
		    //0123456789T
		    parameters = message.substring(10).trim();
		    bancType = BanCType.SUPERSPEC;
		    bancName = "auto-superspec";
		    
		//} else if(messageLc.startsWith("!kick")) {
			//parameters = message.substring(5).trim();
			//bancType = BanCType.KICK;
			//bancName = "auto-kick";
		//} else if(messageLc.startsWith("!k ")) {
			//parameters = message.substring(2).trim();
			//bancType = BanCType.KICK;
			//bancName = "auto-kick";
			
		}
		
		
		if(parameters.length() > 2 && parameters.contains(":")) {
			timeStr = parameters.split(":")[1];
			parameters = parameters.split(":")[0];
		}  else {
			m_botAction.sendRemotePrivateMessage(name, "Syntax error. Please specify <playername>:<time/mins> or PM !help for more information.");
			return;
		}
		/*
		if( !Tools.isAllDigits(timeStr) && !timeStr.contains("d")){//|| !Tools.isAllDigits(timeStr) ) {
			m_botAction.sendRemotePrivateMessage(name, "Syntax error. Please specify <playername>:<time/mins> or PM !help for more information.");
			return;
		}
		else */if(timeStr.length() > 6) {
			timeStr = timeStr.substring(0,5);
		}

		final String target = parameters;
		int time;
		int timeToTell = 0;
		if(timeStr.contains("d"))
		{
		    String justTime = timeStr.substring(0, timeStr.indexOf("d"));
		    timeToTell = Integer.parseInt(justTime);
		    time = Integer.parseInt(justTime)*1440;
		    
		}
		else
		    time = Integer.parseInt(timeStr);
		
		if(time > BANC_MAX_DURATION) {
			m_botAction.sendRemotePrivateMessage(name, "The maximum amount of minutes for a BanC is "+BANC_MAX_DURATION+" minutes (365 days). Duration changed to this maximum.");
			time = BANC_MAX_DURATION;
			timeToTell = (BANC_MAX_DURATION/24)/60;
			
		}
		
		// Check target
		// Already banced?
		if(isBanCed(target, bancType)) {
			m_botAction.sendRemotePrivateMessage(name, "Player '"+target+"' is already banced. Check !listban.");
			return;
		} else
		if(m_botAction.getOperatorList().isBotExact(target)) {
			m_botAction.sendRemotePrivateMessage(name, "You can't place a BanC on '"+target+"' as it is a bot.");
			return;
		} else
		// staff member?
		if(m_botAction.getOperatorList().isBot(target)) {
			m_botAction.sendRemotePrivateMessage(name, "Player '"+target+"' is staff, staff can't be banced.");
			return;
		}
			
		// limit != 0	&	limit < time 	>> change
		// limit != 0	&	time == 0		>> change
		if(		getBanCAccessDurationLimit(bancType, opList.getAccessLevel(name)) != 0 &&
				(getBanCAccessDurationLimit(bancType, opList.getAccessLevel(name)) < time || time == 0)) {
			time = getBanCAccessDurationLimit(bancType, opList.getAccessLevel(name));
			m_botAction.sendRemotePrivateMessage(name, "You are not allowed to issue an "+bancName+" of that duration.");
			m_botAction.sendRemotePrivateMessage(name, "The duration has been changed to the maximum duration of your access level: "+time+" mins.");
			timeToTell = 7;
		}
		
		BanC banc;
	    banc = new BanC(bancType, target, time);
		banc.staffer = name;
		dbLookupIPMID(banc);
		dbAddBan(banc);
		activeBanCs.add(banc);
		
		if(time >= 24*60*7 && timeToTell > 0){
		    
		    m_botAction.sendChatMessage( name+" initiated an "+bancName+" on '"+target+"' for "+timeToTell+" days("+time+" mins)." );
		    m_botAction.sendRemotePrivateMessage(name, "BanC #"+banc.id+": "+bancName+" on '"+target+"' for "+timeToTell+" days("+time+" mins) initiated.");
		}
		else if(time > 0) {
			m_botAction.sendChatMessage( name+" initiated an "+bancName+" on '"+target+"' for "+time+" minutes." );
			m_botAction.sendRemotePrivateMessage(name, "BanC #"+banc.id+": "+bancName+" on '"+target+"' for "+time+" minutes initiated.");
		} else {
			m_botAction.sendChatMessage( name+" initiated an infinite/permanent "+bancName+" on '"+target+"'." );
			m_botAction.sendRemotePrivateMessage(name, "BanC #"+banc.id+": "+bancName+" on '"+target+"' for infinite amount of time initiated.");
		}
		m_botAction.sendRemotePrivateMessage(name, "Please do not forget to add comments to your BanC with !bancomment <#id> <comments>.");
		m_botAction.ipcSendMessage(IPCBANC, bancType.toString()+" "+time+":"+target, null, "banc");
	}
	
	/**
	 * Handles the !listban command
	 * @param name player who issued the command
	 * @param parameters any command parameters
	 */
	private void cmdListBan(String name, String parameters, boolean showLBHelp) {
		int viewcount = 10;
		parameters = parameters.toLowerCase();
		String sqlWhere = "";
		boolean showLifted = false;
		/*
			!listban [arg] [#id] [count]   - Shows last 10/[count] BanCs or info about BanC with <id>. Arguments see below.
        	!listban <player>:[#]          - Shows the last [#]/10 BanCs applied on <player>
        	Arguments:
                    	-player='<..>'     - Specifies player name
                    	-d=#               - Specifies duration in minutes
                    	-a=<...>           - Specifies access requirement, options; mod / smod / sysop
                    	-ip=<#.#.#.#> -ir  - Specifies IP or remove IP (-ir) so banc is not matched by IP
                    	-mid=#  -mr        - Specifies MID or remove MID (-mr) so banc is not matched by MID
                    	-notif=<yes/no>    - Specifies wether a notification is sent on staff chat
        */
        
		if(parameters.length() > 0) {
			// ?listban
			// #19861 by PUNK rock  2009-09-26 18:14 days:1   ~98.194.169.186:8    AmoresPerros

			// ?listban #..
			// #19861 access:4 by PUNK rock 2009-09-26 17:14 days:1 ~98.194.169.186:8 AmoresPerros
			// #19861 access:4 by PUNK rock 2009-09-26 18:14 days:1 ~98.194.169.186:8  ~mid:462587662* AmoresPerros
			
			boolean playerArgument = false, stafferArgument = false;
			
			for(String argument : parameters.split(" ")) {
				
				if(!playerArgument && !stafferArgument) {
					// [#id]
					if(argument.startsWith("#") && Tools.isAllDigits(argument.substring(1))) {
						if(!sqlWhere.isEmpty())
							sqlWhere += " AND ";
						sqlWhere += "fnID="+argument.substring(1);
						
					} else
					// [count]
					if(Tools.isAllDigits(argument)) {
						viewcount = Integer.parseInt(argument);
						if(viewcount < 1)	viewcount = 1;
						if(viewcount > 100)	viewcount = 100;
						
					} else
					// -player='<..>'
					if(argument.startsWith("-player='")){//argument.startsWith("-player=")) {
						String playerString = argument.substring(9);
						
						if(!sqlWhere.isEmpty())
							sqlWhere += " AND ";
						//sqlWhere += "fcUsername='"+playerString+"'";
						if(playerString.endsWith("'")) {
							sqlWhere += "fcUsername='"+playerString.replace("'", "")+"'";
						
						} else {
							sqlWhere += "fcUsername='"+Tools.addSlashes(playerString);
							playerArgument = true;
						}
						
					} else
					// -d=#
					if(argument.startsWith("-d=") && Tools.isAllDigits(argument.substring(3))) {
						if(!sqlWhere.isEmpty())
							sqlWhere += " AND ";
						sqlWhere += "fnDuration="+argument.substring(3);
						
					} else
					// -a=<...>
					if(argument.startsWith("-a=")) {
						String accessString = argument.substring(3);
						
						if(accessString.trim().length() == 0)
							continue;
						
						if(!sqlWhere.isEmpty())
							sqlWhere += " AND ";
						
						sqlWhere += "fcMinAccess='"+accessString.toUpperCase()+"'";
						
					} else 
					// -ip=<#.#.#.#>
					if(argument.startsWith("-ip=")) {
						String ipString = argument.substring(4);
						
						if(ipString.trim().length() == 0 || Tools.isAllDigits(ipString.replace(".", "")) == false)
							continue;
						
						if(!sqlWhere.isEmpty())
							sqlWhere += " AND ";
						
						sqlWhere += "fcIP LIKE '"+Tools.addSlashes(ipString)+"%'";
						
					} else 
					// -mid=#
					if(argument.startsWith("-mid=") && Tools.isAllDigits(argument.substring(5))) {
						if(!sqlWhere.isEmpty())
							sqlWhere += " AND ";
						sqlWhere += "fcMID="+argument.substring(5);
						
					} else 
					// -notif=
					if(argument.startsWith("-notif=") && (argument.substring(7).equalsIgnoreCase("yes") || argument.substring(7).equalsIgnoreCase("no"))) {
						if(!sqlWhere.isEmpty())
							sqlWhere += " AND ";
						sqlWhere += "fbNotification="+(argument.substring(7).equalsIgnoreCase("yes") ? "1" : "0");
						
					} else 
					// -staffer='<..>'
					if(argument.startsWith("-staffer='")){//argument.startsWith("-staffer=")) {
						String stafferString = argument.substring(10);
						if(!sqlWhere.isEmpty())
							sqlWhere += " AND ";
						//sqlWhere += "fcStaffer='"+stafferString+"'";
						
						if(stafferString.endsWith("'")) {
							sqlWhere += "fcStaffer='"+stafferString.replace("'", "")+"'";
						} else {
							sqlWhere += "fcStaffer='"+Tools.addSlashes(stafferString);
							stafferArgument = true;
						}
					}
					else
					    if(argument.startsWith("-lifted")){
					        if(!sqlWhere.isEmpty())
					            sqlWhere += " AND ";
					        sqlWhere += "fbLifted=1";
					        showLifted = true;
					    }
				}   
				// -player='<...>' or -staffer='<...>' extra name parts	
				else {
					if(argument.endsWith("'")) {
						playerArgument = false;
						stafferArgument = false;
						sqlWhere += " " + argument.replace("'", "") + "'";
						
					} else {
						sqlWhere += " " + Tools.addSlashes(argument);
					}
				}
				
			
			}
			
			if(playerArgument || stafferArgument) {
				sqlWhere += "'";
			}
		}
			
		String sqlQuery;
		
		try {
		    
		    if(!showLifted){
                if( !sqlWhere.isEmpty() )
                    sqlWhere += " AND ";
                sqlWhere += "fbLifted=0";
            }
		    
			if(sqlWhere.contains("fnID")) {
				sqlQuery = "SELECT (DATE_ADD(fdCreated, INTERVAL fnDuration MINUTE) > NOW() OR fnDuration = 0) AS active, fnID, fcType, fcUsername, fcIP, fcMID, fcMinAccess, fnDuration, fcStaffer, fcComment, fbNotification, fdCreated, fbLifted FROM tblBanc WHERE "+sqlWhere+" LIMIT 0,1";
				ResultSet rs = m_botAction.SQLQuery(botsDatabase, sqlQuery);
				
				if(rs.next()) {
					
				    String result = "";
					result += (rs.getBoolean("active")?"#":"^");
					result += rs.getString("fnID") + " ";
					if(rs.getString("fcMinAccess") != null) {
						result += "access:" + rs.getString("fcMinAccess") + " ";
					}
					result += "by " + Tools.formatString(rs.getString("fcStaffer"), 10) + " ";
					result += datetimeFormat.format(rs.getTimestamp("fdCreated")) + "  ";
					result += Tools.formatString(rs.getString("fcType"),7) + "  ";
					result += "mins:"+Tools.formatString(rs.getString("fnDuration"), 5) + "  ";
					result += rs.getString("fcUsername");
				
					m_botAction.sendRemotePrivateMessage(name, result);
					
					if(m_botAction.getOperatorList().isModerator(name)) {
						String IP = rs.getString("fcIP");
						if(IP == null)	IP = "(UNKNOWN)";
						result = " IP: "+Tools.formatString(IP, 15) + "   ";
					} else {
						result = " ";
					}
					if(m_botAction.getOperatorList().isSmod(name)) {
						String MID = rs.getString("fcMID");
						if(MID == null)	MID = "(UNKNOWN)";
						result += "MID: "+Tools.formatString(MID, 10) + "   ";
					} else {
						result += " ";
					}
					result += "Notification: "+(rs.getBoolean("fbNotification")?"enabled":"disabled");
					m_botAction.sendRemotePrivateMessage(name, result);
					
					String comments = rs.getString("fcComment");
					if(comments != null) {
						m_botAction.sendRemotePrivateMessage(name, " " + comments);
					} else {
						m_botAction.sendRemotePrivateMessage(name, " (no BanC comments)");
					}
				} else {
					m_botAction.sendRemotePrivateMessage(name, "No BanC with that ID found.");
				}
				
				rs.close();
				
			} else {
				if(sqlWhere.length() > 0) {
					sqlWhere = "WHERE "+sqlWhere;
				}
				sqlQuery = "SELECT (DATE_ADD(fdCreated, INTERVAL fnDuration MINUTE) > NOW() OR fnDuration = 0) AS active, fnID, fcType, fcUsername, fcIP, fcMID, fcMinAccess, fnDuration, fcStaffer, fdCreated, fbLifted FROM tblBanc "+sqlWhere+" ORDER BY fnID DESC LIMIT 0,"+viewcount;
				ResultSet rs = m_botAction.SQLQuery(botsDatabase, sqlQuery);
				
				if(rs != null) {
					rs.afterLast();
					if(rs.previous()) {
						do {
						    
						    String result = "";
							result += (rs.getBoolean("active")?"#":"^");
							result += Tools.formatString(rs.getString("fnID"), 4) + " ";
							result += "by " + Tools.formatString(rs.getString("fcStaffer"), 10) + " ";
							result += datetimeFormat.format(rs.getTimestamp("fdCreated")) + " ";
							result += Tools.formatString(rs.getString("fcType"),7) + " ";
							int time = Integer.parseInt( rs.getString("fnDuration") );
							if(time >= 24*60){
							    int days = (time/24)/60;
							    String daysNumber = days+"";
							    result += " days: "+Tools.formatString(daysNumber, 5);
							    
							}
							else 
							    result += " mins:"+Tools.formatString(rs.getString("fnDuration"), 5) + " ";
							if(m_botAction.getOperatorList().isModerator(name))
								result += " "+Tools.formatString(rs.getString("fcIP"), 15) + "  ";
							result += rs.getString("fcUsername");
							
							m_botAction.sendRemotePrivateMessage(name, result);
						    
						} while(rs.previous());
						if(showLBHelp)  
						    m_botAction.sendRemotePrivateMessage(name, "!listban -help for more info");
	                        
					} else {
						// Empty resultset - nothing found
						m_botAction.sendRemotePrivateMessage(name, "No BanCs matching given arguments found.");
					}
				} else {
					// Empty resultset - nothing found
					m_botAction.sendRemotePrivateMessage(name, "No BanCs matching given arguments found.");
				}
			}
			
		} catch(SQLException sqle) {
			m_botAction.sendRemotePrivateMessage(name, "A problem occured while retrieving ban listing from the database. Please try again or report the problem.");
			Tools.printStackTrace("SQLException while querying the database for BanCs", sqle);
		}
	}
	
	/**
	 * Handles the !changeban command
	 * @param name player who issued the command
	 * @param parameters any command parameters
	 */
	private void cmdChangeBan(String name, String parameters) {
		String sqlSet = "";
		int banID;
		OperatorList opList = m_botAction.getOperatorList();
		BanC banChange = new BanC();
		
		/*
			!changeban <#id> <arguments>   - Changes banc with <id>. Arguments see below.
             Arguments:
                         -player=<..>       - Specifies player name
                         -d=#               - Specifies duration in minutes
                         -a=<...>           - Specifies access requirement, options; mod / smod / sysop
                         -ip=<#.#.#.#> -ir  - Specifies IP or remove IP (-ir) so banc is not matched by IP
                         -mid=#  -mr        - Specifies MID or remove MID (-mr) so banc is not matched by MID
                         -notif=<yes/no>    - Specifies wether a notification is sent on staff chat
        */
		
		if(parameters.length() > 0 && parameters.startsWith("#") && parameters.contains(" ") && Tools.isAllDigits(parameters.substring(1).split(" ")[0])) {
			boolean playerArgument = false;
			
			
			// Extract given #id
			String id = parameters.substring(1, parameters.indexOf(" ", 1));
			if(Tools.isAllDigits(id)) {
				banID = Integer.parseInt(id);
			} else {
				m_botAction.sendRemotePrivateMessage(name, "Syntax error. Please specify #id and arguments. For more information, PM !help.");
				return;
			}
			
			for(String argument : parameters.split(" ")) {
				
				if(!playerArgument) {
					// -player='<..>'
					if(argument.startsWith("-player='")) {
						String playerString = argument.substring(9);
						playerArgument = true;
						
						if(!sqlSet.isEmpty())
							sqlSet += ", ";
							
						if(playerString.endsWith("'")) {
							sqlSet += "fcUsername='"+playerString.replace("'", "")+"'";
							banChange.setPlayername(playerString.replace("'", ""));
						} else {
							sqlSet += "fcUsername='"+Tools.addSlashes(playerString);
							banChange.setPlayername(playerString);
						}
						
					} else
					// -d=#
					if(argument.startsWith("-d=") && Tools.isAllDigits(argument.substring(3))) {
						if(!sqlSet.isEmpty())
							sqlSet += ", ";
						sqlSet += "fnDuration="+argument.substring(3);
						banChange.setDuration(Integer.parseInt(argument.substring(3)));
						
					} else
					// -a=<...>
					if(argument.startsWith("-a=")) {
						String accessRequirement = argument.substring(3);
						if( //(MINACCESS_ER.equalsIgnoreCase(accessRequirement) && !opList.isER(name)) ||
							//(MINACCESS_MOD.equalsIgnoreCase(accessRequirement) && !opList.isModerator(name)) ||
						    (MINACCESS_BANCSTAFFER.equalsIgnoreCase(accessRequirement) && !bancStaffers.containsKey(name.toLowerCase()))  ||
							(MINACCESS_SMOD.equalsIgnoreCase(accessRequirement) && !opList.isSmod(name)) ||
							(MINACCESS_SYSOP.equalsIgnoreCase(accessRequirement) && !opList.isSysop(name))) {
							m_botAction.sendRemotePrivateMessage(name, "You can't set the access requirement higher then your own access. (Argument ignored.)");
						} else 
						if(	!bancStaffers.containsKey(name.toLowerCase())  ||
						    //MINACCESS_ER.equalsIgnoreCase(accessRequirement) ||
							//MINACCESS_MOD.equalsIgnoreCase(accessRequirement) ||
							MINACCESS_SMOD.equalsIgnoreCase(accessRequirement) ||
							MINACCESS_SYSOP.equalsIgnoreCase(accessRequirement)) {
							if(!sqlSet.isEmpty())
								sqlSet += ", ";
							
							sqlSet += "fcMinAccess='"+accessRequirement.toUpperCase()+"'";
						}
						
					} else 
					// -ip=<#.#.#.#>
					if(argument.startsWith("-ip=")) {
						if(!sqlSet.isEmpty())
							sqlSet += ", ";
						sqlSet += "fcIP='"+argument.substring(4)+"'";
						banChange.setIP(argument.substring(4));
						
					} else 
					// -ir
					if(argument.startsWith("-ir")) {
						if(!sqlSet.isEmpty())
							sqlSet += ", ";
						sqlSet += "fcIP=NULL";
						banChange.setIP("NULL");
						
					} else
					// -mid=#
					if(argument.startsWith("-mid=") && Tools.isAllDigits(argument.substring(5))) {
						if(!sqlSet.isEmpty())
							sqlSet += ", ";
						sqlSet += "fcMID="+argument.substring(5);
						banChange.setMID(argument.substring(5));
						
					} else 
					// -mr
					if(argument.startsWith("-mr")) {
						if(!sqlSet.isEmpty())
							sqlSet += ", ";
						sqlSet += "fcMID=NULL";
						banChange.setMID("NULL");
						
					} else
					// -notif=
					if(argument.startsWith("-notif=")) {
						String notification = argument.substring(7);
						if(notification.equalsIgnoreCase("yes")) {
							if(!sqlSet.isEmpty())
								sqlSet += ", ";
							
							sqlSet += "fbNotification=1";
							banChange.setNotification(true);
						} else if(notification.equalsIgnoreCase("no")) {
							if(!sqlSet.isEmpty())
								sqlSet += ", ";
							
							sqlSet += "fbNotification=0";
							banChange.setNotification(false);
						} else {
							m_botAction.sendRemotePrivateMessage(name, "Syntax error on the -notif argument. (Argument ignored.)");
						}
					}
				}
				// -player='<...>' extra name parts
				else {
					if(argument.endsWith("'")) {
						sqlSet += " " + argument.replace("'", "") + "'";
						banChange.setPlayername(banChange.getPlayername()+" "+argument.replace("'", ""));
						playerArgument = false;
					} else {
						sqlSet += " " + Tools.addSlashes(argument);
						banChange.setPlayername(banChange.getPlayername()+" "+argument);
					}
				}
			}
		} else {
			// No parameters
			m_botAction.sendRemotePrivateMessage(name, "Syntax error. Please specify #id and arguments. For more information, PM !help.");
			return;
		}
		
		if(sqlSet.isEmpty()) {
			// No arguments
			m_botAction.sendRemotePrivateMessage(name, "Syntax error (no arguments specified). Please specify #id and arguments. For more information, PM !help.");
			return;
		}
		
		
		// Retrieve ban with id and check access requirement
		// Modify ban
		
		String sqlUpdate;
		
		try {
			psCheckAccessReq.setInt(1, banID);
			ResultSet rsAccessReq = psCheckAccessReq.executeQuery();
			
			if(rsAccessReq.next()) {
				String accessReq = rsAccessReq.getString(1);
				if(MINACCESS_BANCSTAFFER.equalsIgnoreCase(accessReq) && !bancStaffers.containsKey(name.toLowerCase())  ||
				    //(MINACCESS_ER.equals(accessReq) && !opList.isER(name)) ||
					//(MINACCESS_MOD.equals(accessReq) && !opList.isModerator(name)) || 
				   	(MINACCESS_SMOD.equals(accessReq) && !opList.isSmod(name)) ||
				   	(MINACCESS_SYSOP.equals(accessReq) && !opList.isSysop(name))) {
					m_botAction.sendRemotePrivateMessage(name, "You don't have enough access to modify this BanC.");
					return;
				}
			}
			
			sqlUpdate = "UPDATE tblBanc SET "+sqlSet+" WHERE fnID="+banID;
			m_botAction.SQLQueryAndClose(botsDatabase, sqlUpdate);
			
			// Retrieve active banc if it exists and let expire if it by changes becomes expired
			synchronized(activeBanCs) {
				Iterator<BanC> iterator = activeBanCs.iterator(); // Must be in synchronized block
				while (iterator.hasNext()) {
					BanC banc = iterator.next();
					
					if(banc.getId() == banID) {
						banc.applyChanges(banChange);
						banc.calculateExpired();
						if(banc.isExpired()) {
							switch(banc.type) {
								case SILENCE : 	m_botAction.sendChatMessage("Auto-silence BanC #"+banc.id+" ("+banc.playername+") has expired."); break;
								case SPEC : 	m_botAction.sendChatMessage("Auto-speclock BanC #"+banc.id+" ("+banc.playername+") has expired."); break;
								//case KICK : 	m_botAction.sendChatMessage("Auto-kick BanC #"+banc.id+" ("+banc.playername+") has expired."); break;
								case SUPERSPEC: m_botAction.sendChatMessage("Auto-superspeclock BanC #"+banc.id+" ("+banc.playername+") has expired."); break;
							}
							m_botAction.ipcSendMessage(IPCBANC, "REMOVE "+banc.type.toString()+" "+banc.playername, null, "banc");
							iterator.remove();
						}
					}
				}
			}
			
			m_botAction.sendRemotePrivateMessage(name, "BanC #"+banID+" changed.");
			
		} catch(SQLException sqle) {
			m_botAction.sendRemotePrivateMessage(name, "A problem occured while modifying the ban in the database. Please try again or report the problem.");
			Tools.printStackTrace("SQLException while modifying the database", sqle);
		}
	}
	
	/**
	 * Handles the !bancomment command
	 * @param name player who issued the command
	 * @param parameters any command parameters
	 */
	private void cmdBancomment(String name, String message) {
		// !bancomment <#id> <comments>   - Adds comments to BanC with specified #id.
		
		int id = -1;
		String comments = null;
		
		if(message.length() == 0 || message.startsWith("#") == false || message.contains(" ") == false || Tools.isAllDigits(message.substring(1).split(" ")[0]) == false || message.split(" ")[1].isEmpty()) {
			m_botAction.sendRemotePrivateMessage(name, "Syntax error. Please specify #id and comments. For more information, PM !help.");
			return;
		} else {
			id = Integer.parseInt(message.substring(1).split(" ")[0]);
			comments = message.substring(message.indexOf(" ")+1);
		}
		
		// failsafe
		if(id == -1 || comments.isEmpty()) return;
		
		// "UPDATE tblBanc SET fcComment = ? WHERE fnID = ?"
		try {
			psUpdateComment.setString(1, comments);
			psUpdateComment.setInt(2, id);
			psUpdateComment.executeUpdate();
			
			if(psUpdateComment.executeUpdate() == 1) {
				m_botAction.sendRemotePrivateMessage(name, "BanC #"+id+" modified");
			} else {
				m_botAction.sendRemotePrivateMessage(name, "BanC #"+id+" doesn't exist.");
			}
			
			// Apply the banc comment to the active banc
			BanC activeBanc = lookupActiveBanC(id);
			if(activeBanc != null) {
				activeBanc.comment = comments;
			}
		} catch(SQLException sqle) {
			m_botAction.sendRemotePrivateMessage(name, "A problem occured while modifying the ban in the database. Please try again or report the problem.");
			Tools.printStackTrace("SQLException while modifying the database", sqle);
		}
		
	}
	
	private void doForceDBConnection(String name){
	    try{
	        
	        this.psKeepAlive1.execute();
	        this.psKeepAlive2.execute();

	        if( !psKeepAlive1.isClosed() && !psKeepAlive2.isClosed() ){
	            m_botAction.sendPrivateMessage(name, "Force-Connected to the database successfuly,");
	            m_botAction.sendPrivateMessage(name, "now try to !lb, !bc and others banc commands to check");
	        }
	        
	    }catch(SQLException e){
	        m_botAction.sendPrivateMessage(name, "I had a problem to force the DB Connection, try again in some minutes. StackTrace:" +
	        		" "+e.toString());
	        e.printStackTrace();
	    }
	}
	
	private void cmdLiftban(String name, String message) {
		// !liftban <#id>                 - Removes ban with #id.
		int id = -1;
		
		if(message.length() == 0 || !message.startsWith("#") || !Tools.isAllDigits(message.substring(1))) {
			m_botAction.sendRemotePrivateMessage(name, "Syntax error. Please specify #id. For more information, PM !help.");
			return;
		} else {
			id = Integer.parseInt(message.substring(1));
		}
		
		// failsafe
		if(id == -1) return;
		
		try {
			psRemoveBanC.setInt(1, id);
			psRemoveBanC.executeUpdate();
			
			m_botAction.sendRemotePrivateMessage(name, "BanC #"+id+" removed");
			m_botAction.sendChatMessage("BanC #"+id+" has been lifted by "+name);
			
			// Make the banc expired so it's removed from the player if still active.
			BanC activeBanc = lookupActiveBanC(id);
			if(activeBanc != null) {
				m_botAction.ipcSendMessage(IPCBANC, "REMOVE "+activeBanc.type.toString()+" "+activeBanc.playername, null, "banc");
				m_botAction.sendSmartPrivateMessage("quiles", "REMOVE "+activeBanc.type.toString()+" "+activeBanc.playername);
				activeBanCs.remove(activeBanc);
			}
		} catch(SQLException sqle) {
			m_botAction.sendRemotePrivateMessage(name, "A problem occured while deleting the banc from the database. Please try again or report the problem.");
			Tools.printStackTrace("SQLException while modifying the database", sqle);
		}
	}
	
	private void cmdReload(String name) {
		activeBanCs.clear();
		this.loadActiveBanCs();
		m_botAction.sendRemotePrivateMessage(name, "Bans reloaded from database.");
		this.sendIPCActiveBanCs(null);
	}
	
	private void cmdListActiveBanCs(String name) {
		for(BanC banc : activeBanCs) {
			m_botAction.sendRemotePrivateMessage(name, "#"+banc.getId()+" "+banc.getType()+" "+banc.getDuration()+"mins on "+banc.getPlayername());
		}
	}

	/**
	 * @return the maximum duration amount (in minutes) for the given access level
	 */
	private Integer getBanCAccessDurationLimit(BanCType banCType, int accessLevel) {
		int level = 0;
		
		switch(accessLevel) {
			case OperatorList.ER_LEVEL : 		level = 0;	break;
			case OperatorList.MODERATOR_LEVEL :
			case OperatorList.HIGHMOD_LEVEL :
			case OperatorList.DEV_LEVEL :		level = 1;	break;
			case OperatorList.SMOD_LEVEL : 		level = 2;	break;
			case OperatorList.SYSOP_LEVEL :		
			case OperatorList.OWNER_LEVEL : 	level = 3;	break;
		}
		return BANCLIMITS[banCType.ordinal()][level];
	}
	
	
	/**
	 * Loads active BanCs from the database
	 */
	private void loadActiveBanCs() {
		try {
            ResultSet rs = psActiveBanCs.executeQuery();
            
            if(rs != null) {
	            while(rs.next()) {
	                BanC banc = new BanC();
	                banc.id = rs.getInt("fnID");
	                String banCType = rs.getString("fcType");
	                
	                if(banCType.equals("S-SPEC"))
	                    banc.type = BanCType.valueOf("SUPERSPEC");
	                else
	                    banc.type = BanCType.valueOf(rs.getString("fcType"));
	                String playerName = rs.getString("fcUsername");
	                banc.addNickName(playerName);
	                banc.IP = rs.getString("fcIP");
	                banc.MID = rs.getString("fcMID");
	                banc.notification = rs.getBoolean("fbNotification");
	                banc.created = rs.getTimestamp("fdCreated");
	                banc.duration = rs.getInt("fnDuration");
	                activeBanCs.add(banc);
	            }
            }
	    } catch(SQLException sqle) {
	        Tools.printLog("SQLException occured while retrieving active BanCs: "+sqle.getMessage());
	        m_botAction.sendChatMessage(2, "BANC WARNING: Problem occured while retrieving active BanCs: "+sqle.getMessage());
            Tools.printStackTrace(sqle);
	    }
	}
	
	/**
	 * Sends out all active BanCs trough IPC Messages to the pubbots so they are applied
	 * @param receiver Receiving bot in case a certain pubbot needs to be initialized, else NULL for all pubbots
	 */
	private void sendIPCActiveBanCs(String receiver) {
		for(BanC banc : activeBanCs) {
			m_botAction.ipcSendMessage(IPCBANC, banc.type.toString()+" "+banc.duration+":"+banc.playername, receiver, "banc");
		}
	}
	
	/**
	 * Checks if the player already has a BanC of the given type
	 *  
	 * @param playername
	 * @param bancType
	 * @return
	 */
	private boolean isBanCed(String playername, BanCType bancType) {
		for(BanC banc : activeBanCs) {
			if(banc.type.equals(bancType) && banc.playername.equalsIgnoreCase(playername))
				return true;
		}
		return false;
	}
	
	/**
	 * Lookups a BanC from the list of active BanCs by the given id.
	 * @param id
	 * @return an active BanC matching the given id or NULL if not found
	 */
	private BanC lookupActiveBanC(int id) {
		for(BanC banc:this.activeBanCs) {
			if(banc.id == id) {
				return banc;
			}
		}
		return null;
	}
	
	/**
	 * Lookups a BanC from the list of active BanCs that matches the type and playername
	 * @param bancType
	 * @param name
	 * @return an active BanC matching the given type and playername or NULL if not found
	 */
	private BanC lookupActiveBanC(BanCType bancType, String name) {
		for(BanC banc:this.activeBanCs) {
			if(banc.type.equals(bancType) && banc.playername.equalsIgnoreCase(name)) {
				return banc;
			}
		}
		return null;
	}
	
	/**
	 * Saves the BanC to the database and stores the database ID in the BanC
	 */
	private void dbAddBan(BanC banc) {
		
		try {
			// INSERT INTO tblBanc(fcType, fcUsername, fcIP, fcMID, fcMinAccess, fnDuration, fcStaffer, fdCreated) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
			//                       1         2         3     4         5           6           7          
			if(banc.type.name().equals("SUPERSPEC"))
			    psAddBanC.setString(1, "S-SPEC");
            
			else
			    psAddBanC.setString(1, banc.type.name());
			
			psAddBanC.setString(2, banc.playername);
			psAddBanC.setString(3, banc.IP);
			psAddBanC.setString(4, banc.MID);
			psAddBanC.setString(5, MINACCESS_BANCSTAFFER);
			psAddBanC.setLong(6, banc.duration);
			psAddBanC.setString(7, banc.staffer);
			psAddBanC.execute();
			
			ResultSet rsKeys = psAddBanC.getGeneratedKeys();
			if(rsKeys.next()) {
				banc.id = rsKeys.getInt(1);
			}
			rsKeys.close();
			
		} catch (SQLException sqle) {
			m_botAction.sendChatMessage(2, "BANC WARNING: Unable to save BanC ("+banc.type.name()+","+banc.playername+") to database. Please try to reinstate the banc.");
			Tools.printStackTrace("SQLException encountered while saving BanC ("+banc.type.name()+","+banc.playername+")", sqle);
		}
	}
	
	private void dbLookupIPMID(BanC banc) {
		try {
			psLookupIPMID.setString(1, banc.playername);
			ResultSet rsLookup = psLookupIPMID.executeQuery();
			if(rsLookup.next()) {
				banc.IP = rsLookup.getString(1);
				banc.MID = rsLookup.getString(2);
			}
		} catch(SQLException sqle) {
			m_botAction.sendChatMessage(2, "BANC WARNING: Unable to lookup the IP / MID data for BanC ("+banc.type.name()+","+banc.playername+") from the database. BanC aliassing disabled for this BanC.");
			Tools.printStackTrace("SQLException encountered while retrieving IP/MID details for BanC ("+banc.type.name()+","+banc.playername+")", sqle);
		}
	}
	
	/**
	 * This TimerTask periodically runs over all the active BanCs in the activeBanCs arrayList and (1) removes 
	 * already expired BanCs and (2) checks if BanCs has expired. If a BanC has expired, a chat message is given
	 * and the BanC is sent to the pubbots to lift the silence/spec if necessary.
	 * 
	 * @author Maverick
	 */
	public class CheckExpiredBanCs extends TimerTask {
		
		public void run() {
			
			// Run trough all the active BanCs
			// Check for expired bancs
			// Notify of banc expired
			// remove BanC
			synchronized(activeBanCs) {
				Iterator<BanC> iterator = activeBanCs.iterator(); // Must be in synchronized block
				while (iterator.hasNext()) {
					BanC banc = iterator.next();
					
					banc.calculateExpired();
					if(banc.isExpired()) {
						switch(banc.type) {
							case SILENCE : 	m_botAction.sendChatMessage("Auto-silence BanC #"+banc.id+" ("+banc.playername+") has expired."); break;
							case SPEC : 	m_botAction.sendChatMessage("Auto-speclock BanC #"+banc.id+" ("+banc.playername+") has expired."); break;
							//case KICK : 	m_botAction.sendChatMessage("Auto-kick BanC #"+banc.id+" ("+banc.playername+") has expired."); break;
							case SUPERSPEC: m_botAction.sendChatMessage("Auto-superspec BanC #"+banc.id+" ("+banc.playername+") has expired."); break;
						}
						m_botAction.ipcSendMessage(IPCBANC, "REMOVE "+banc.type.toString()+" "+banc.playername, null, "banc");
						iterator.remove();
					}
				}
			}
		}
	}
	
	/**
     * This TimerTask executes psKeepAlive which just sends a query to the database to keep the connection alive  
     */
    private class KeepAliveConnection extends TimerTask {
        public void run() {
        	try {
        		psKeepAlive1.execute();
        		psKeepAlive2.execute();
        	} catch(SQLException sqle) {
    			Tools.printStackTrace("SQLException encountered while executing queries to keep alive the database connection", sqle);
        	}
        }
    }
    
	public class BanC {
		
		public BanC() {}
		
		public BanC(BanCType type, String username, int duration) {
			this.type = type;
			this.playername = username;
			this.duration = duration;
			created = new Date();
		}
		
		private int id;
		private BanCType type;
		private String playername;
		private String IP;
		private String MID;
		private Date created;
		/** Duration of the BanC in minutes */
		private long duration = -1;
		private Boolean notification = false;
		
		private String staffer;
		private String comment;
		
		private boolean applied = false;
		private boolean expired = false;
		
		public void calculateExpired() {
			if(duration == 0) {
				expired = false;
			} else {
				Date now = new Date();
				Date expiration = new Date(created.getTime()+(duration*Tools.TimeInMillis.MINUTE));
				expired = now.equals(expiration) || now.after(expiration);
			}
		}
		public void addNickName(String name){
	            this.playername = name;
        }
		public void applyChanges(BanC changes) {
			if(changes.playername != null)
				this.playername = changes.playername;
			if(changes.IP != null)
				this.IP = changes.IP;
			if(changes.IP != null && changes.IP.equals("NULL"))
				this.IP = null;
			if(changes.MID != null)
				this.MID = changes.MID;
			if(changes.MID != null && changes.MID.equals("NULL"))
				this.MID = null;
			if(changes.duration > 0)
				this.duration = changes.duration;
			if(changes.notification != null)
				this.notification = changes.notification;
			if(changes.comment != null)
				this.comment = changes.comment;
		}
		
		/**
         * @return the id
         */
        public int getId() {
        	return id;
        }

		/**
         * @param id the id to set
         */
        public void setId(int id) {
        	this.id = id;
        }

		/**
         * @return the type
         */
        public BanCType getType() {
        	return type;
        }

		/**
         * @param type the type to set
         */
        public void setType(BanCType type) {
        	this.type = type;
        }

		/**
         * @return the playername
         */
        public String getPlayername() {
        	return playername;
        }

		/**
         * @param playername the playername to set
         */
        public void setPlayername(String playername) {
        	this.playername = playername;
        }

		/**
         * @return the iP
         */
        public String getIP() {
        	return IP;
        }

		/**
         * @param iP the iP to set
         */
        public void setIP(String iP) {
        	IP = iP;
        }

		/**
         * @return the mID
         */
        public String getMID() {
        	return MID;
        }

		/**
         * @param mID the mID to set
         */
        public void setMID(String mID) {
        	MID = mID;
        }

		/**
         * @return the created
         */
        public Date getCreated() {
        	return created;
        }

		/**
         * @param created the created to set
         */
        public void setCreated(Date created) {
        	this.created = created;
        }

		/**
         * @return the duration
         */
        public long getDuration() {
        	return duration;
        }

		/**
         * @param duration the duration to set
         */
        public void setDuration(long duration) {
        	this.duration = duration;
        }

		/**
         * @return the notification
         */
        public boolean isNotification() {
        	return notification;
        }

		/**
         * @param notification the notification to set
         */
        public void setNotification(boolean notification) {
        	this.notification = notification;
        }

		/**
         * @return the notification
         */
        public Boolean getNotification() {
        	return notification;
        }

		/**
         * @param notification the notification to set
         */
        public void setNotification(Boolean notification) {
        	this.notification = notification;
        }

		/**
         * @return the staffer
         */
        public String getStaffer() {
        	return staffer;
        }

		/**
         * @param staffer the staffer to set
         */
        public void setStaffer(String staffer) {
        	this.staffer = staffer;
        }

		/**
         * @return the comment
         */
        public String getComment() {
        	return comment;
        }

		/**
         * @param comment the comment to set
         */
        public void setComment(String comment) {
        	this.comment = comment;
        }

		/**
         * @return the applied
         */
        public boolean isApplied() {
        	return applied;
        }

		/**
         * @param applied the applied to set
         */
        public void setApplied(boolean applied) {
        	this.applied = applied;
        }

		/**
         * @return the expired
         */
        public boolean isExpired() {
        	return expired;
        }

		/**
         * @param expired the expired to set
         */
        public void setExpired(boolean expired) {
        	this.expired = expired;
        }

		/* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
	        final int prime = 31;
	        int result = 1;
	        result = prime * result + getOuterType().hashCode();
	        result = prime * result + ((IP == null) ? 0 : IP.hashCode());
	        result = prime * result + ((MID == null) ? 0 : MID.hashCode());
	        result = prime * result + (applied ? 1231 : 1237);
	        result = prime * result
	                + ((created == null) ? 0 : created.hashCode());
	        result = (int) (prime * result + duration);
	        result = prime * result + id;
	        result = prime * result
	                + ((notification == null) ? 0 : notification.hashCode());
	        result = prime * result
	                + ((playername == null) ? 0 : playername.hashCode());
	        result = prime * result + ((type == null) ? 0 : type.hashCode());
	        return result;
        }

		/* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
	        if (this == obj)
		        return true;
	        if (obj == null)
		        return false;
	        if (getClass() != obj.getClass())
		        return false;
	        BanC other = (BanC) obj;
	        if (!getOuterType().equals(other.getOuterType()))
		        return false;
	        if (IP == null) {
		        if (other.IP != null)
			        return false;
	        } else if (!IP.equals(other.IP))
		        return false;
	        if (MID == null) {
		        if (other.MID != null)
			        return false;
	        } else if (!MID.equals(other.MID))
		        return false;
	        if (applied != other.applied)
		        return false;
	        if (created == null) {
		        if (other.created != null)
			        return false;
	        } else if (!created.equals(other.created))
		        return false;
	        if (duration != other.duration)
		        return false;
	        if (id != other.id)
		        return false;
	        if (notification == null) {
		        if (other.notification != null)
			        return false;
	        } else if (!notification.equals(other.notification))
		        return false;
	        if (playername == null) {
		        if (other.playername != null)
			        return false;
	        } else if (!playername.equals(other.playername))
		        return false;
	        if (type == null) {
		        if (other.type != null)
			        return false;
	        } else if (!type.equals(other.type))
		        return false;
	        return true;
        }

		private staffbot_banc getOuterType() {
	        return staffbot_banc.this;
        }
        
        

        
		
	}
	
}