package twcore.bots.staffbot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimerTask;

import twcore.bots.Module;
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
 *  
 *  TODO:
 *   - speclocking in a ship
 *   - Time left in !listban #ID
 * 
 * @author Maverick
 */
public class staffbot_banc extends Module {
    
	// Helps screens
    final String[] helpER = {
            "----------------------[ BanC: ER+ ]-----------------------",
            " !silence <player>:<time/mins>  - Initiates an automatically enforced",
            "                                  silence on <player> for <time/mins>.",
            " !spec <player>:<time/mins>     - Initiates an automatically enforced",
            "                                  spectator-lock on <player> for <time/mins>.",
            " !kick <player>:<time/mins>     - Initiates an automatically issued",
            "                                  kick on <player> for <time/mins>. Mod+ only.",
            " !listban [arg] [count]         - Shows last 10/[count] BanCs. Optional arguments see below.",
            " !listban [#id]                 - Shows information about BanC with <id>.",
            " !changeban <#id> <arguments>   - Changes banc with <id>. Arguments see below.",
            " Arguments:",
            "             -player='<..>'     - Specifies player name",
            "             -d=#               - Specifies duration in minutes",
            "             -a=<...>           - Specifies access requirement, options; mod / smod / sysop",
            "             -ip=<#.#.#.#> -ir  - Specifies IP or remove IP (-ir) so banc is not matched by IP",
            "             -mid=#  -mr        - Specifies MID or remove MID (-mr) so banc is not matched by MID",
            "             -notif=<yes/no>    - Specifies wether a notification is sent on staff chat",
            "             -staffer='<..>'    - Specifies the name who issued the ban. [Only avail. on !listban]",
            " !bancomment <#id> <comments>   - Adds / changes comments on BanC with specified #id.",
            " !liftban <#id>                 - Removes ban with #id.",
            " !banaccess                     - Returns the access level restrictions",
            " !shortcutkeys                  - Shows the available shortcut keys for the commands above"
    };

    final String[] helpSmod = {
            "----------------------[ BanC: SMod+ ]---------------------",
            " !reload                           - Reloads the list of active bancs from the database",
            " !forcedb                          - Forces to connect to the database"
    };
    
    final String[] shortcutKeys = {
    		"Available shortcut keys:",
    		" !silence  -> !s   |  !listban    -> !lb",
    		" !spec     -> !sp  |  !changeban  -> !cb",
    		" !kick     -> !k   |  !bancomment -> !bc",
    };
    
    // Definition variables
    public enum BanCType {
    	SILENCE, SPEC, KICK, SUPERSPEC
    }       
    
   // private staffbot_database Database = new staffbot_database();
    
    private final String botsDatabase = "bots";
    private final String trenchDatabase = "website";
    private final String uniqueConnectionID = "banc";
    private final String IPCBANC = "banc";
    private final String IPCALIAS = "pubBots";
    
    private final String MINACCESS_ER = "ER";
    private final String MINACCESS_MOD = "MOD";
    private final String MINACCESS_SMOD = "SMOD";
    private final String MINACCESS_SYSOP = "SYSOP";
    
    private final Integer[][] BANCLIMITS = {
    		{ 60,  120, 240, 0},	// BanC limits
    		{ 30,  60, 120, 0},		// [BanCType] [Accesslevel]
    		{ null,30, 60,  0},
    		{30, 60, 120, 0}
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
    	
    	// Initialize Prepared Statements
    	psActiveBanCs = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "SELECT * FROM tblBanc WHERE DATE_ADD(fdCreated, INTERVAL fnDuration MINUTE) > NOW() OR fnDuration = 0");
    	//psListBanCs = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "SELECT * FROM tblBanc LIMIT 0,?");
    	psCheckAccessReq = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "SELECT fcMinAccess FROM tblBanc WHERE fnID = ?");
    	psAddBanC = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "INSERT INTO tblBanc(fcType, fcUsername, fcIP, fcMID, fcMinAccess, fnDuration, fcStaffer, fdCreated) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())", true);
    	psUpdateComment = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "UPDATE tblBanc SET fcComment = ? WHERE fnID = ?");
    	psRemoveBanC = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "DELETE FROM tblBanc WHERE fnID = ?");
    	psLookupIPMID = m_botAction.createPreparedStatement(trenchDatabase, uniqueConnectionID, "SELECT fcIpString, fnMachineId FROM tblAlias INNER JOIN tblUser ON tblAlias.fnUserID = tblUser.fnUserID WHERE fcUserName = ? ORDER BY fdUpdated DESC LIMIT 0,1");
    	psKeepAlive1 = m_botAction.createPreparedStatement(botsDatabase, uniqueConnectionID, "SHOW DATABASES");
    	psKeepAlive2 = m_botAction.createPreparedStatement(trenchDatabase, uniqueConnectionID, "SHOW DATABASES");
    	
    	if( psActiveBanCs == null || psCheckAccessReq == null || psAddBanC == null || psUpdateComment == null || psRemoveBanC == null || psLookupIPMID == null || psKeepAlive1 == null || psKeepAlive2 == null) {
    		Tools.printLog("BanC: One or more PreparedStatements are null! Module BanC disabled.");
	        m_botAction.sendChatMessage(2, "BanC: One or more connections (prepared statements) couldn't be made! Module BanC disabled.");
	        this.cancel();
	        
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
	        
	        // Minimum ER access requirement for all !commands
	        if(!opList.isER(name)) {
	            return;
	        }
	        
	        // !help
	        if(messageLc.startsWith("!help")) {
	        	cmdHelp(name, message.substring(5).trim());
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
	        		messageLc.startsWith("!superspec") ||
			   (messageLc.startsWith("!kick") && opList.isModerator(name)) ||
			   (messageLc.startsWith("!k") && opList.isModerator(name))) {
				cmdSilenceSpecKick(name, message);
			}
	        
	        // !listban [arg] [count]
            // !listban [#id]
	        else if( messageLc.startsWith("!listban")) {
	        	cmdListBan(name, message.substring(8).trim());
	        }
	        else if( messageLc.startsWith("!lb")) {
	        	cmdListBan(name, message.substring(3).trim());
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
	        else if( messageLc.startsWith("!reload") && opList.isSmod(name)) {
	        	cmdReload(name);
	        }
	        
	        else if( messageLc.startsWith("!listactive") && opList.isDeveloper(name)) {
	        	cmdListActiveBanCs(name);
	        }
	        
	        else if( messageLc.startsWith("!forcedb") && opList.isSmod(name) ){
	            doForceDBConnection(name);
	        }

		}
	}
	
	/* (non-Javadoc)
	 * @see twcore.bots.Module#handleEvent(twcore.core.events.InterProcessEvent)
	 */
	@Override
	public void handleEvent(InterProcessEvent event) {
		if(stop) return;
		if(!(event.getObject() instanceof IPCMessage)) return;
		
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
						// Match found on one or more properties
						// Send BanC object to pubbotbanc to BanC the player
						banc.calculateExpired();
						m_botAction.ipcSendMessage(IPCBANC, banc.getType().toString()+" "+banc.duration+":"+playerName, null, "banc");
					}
				}
			}
		}
		if(IPCBANC.equals(event.getChannel()) && event.getSenderName().toLowerCase().startsWith("pubbot")) {
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
			}
			else if(command.startsWith("REMOVE "+BanCType.SPEC.toString())) {
				BanC banc = lookupActiveBanC(BanCType.SPEC, command.substring(12));
				if(banc != null && banc.isNotification()) {
					m_botAction.sendChatMessage("Player '"+banc.getPlayername()+"' has had the speclock removed.");
				} else if(banc == null) {
					m_botAction.sendChatMessage("Player '"+command.substring(12)+"' has had the speclock removed.");
				}
			} else if(command.startsWith(BanCType.KICK.toString())) {
				BanC banc = lookupActiveBanC(BanCType.KICK, command.substring(5));
				if(banc != null && banc.isNotification()) {
					m_botAction.sendChatMessage("Player '"+banc.getPlayername()+"' has been kicked.");
				} else if(banc == null) {
					m_botAction.sendChatMessage("Player '"+command.substring(5)+"' has been kicked.");
				}
			}
		}
	}
	
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
		
		m_botAction.sendSmartPrivateMessage(name, "Limitations on BanC by access level");
    	m_botAction.sendSmartPrivateMessage(name, " ");
    	m_botAction.sendSmartPrivateMessage(name, "                       ER      MOD     SMOD    SYSOP");
    	
    	for(int type = 0 ; type < BANCLIMITS.length ; type++) {
    		String line = "";
			switch(type) {
				case 0: line += " Silence time/mins"; break;
				case 1: line += " Speclock time/mins"; break;
				case 2: line += " Auto-kick time/mins"; break; 
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
    		
    		m_botAction.sendSmartPrivateMessage(name, line);
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
		    
		} else if(messageLc.startsWith("!kick")) {
			parameters = message.substring(5).trim();
			bancType = BanCType.KICK;
			bancName = "auto-kick";
		} else if(messageLc.startsWith("!k ")) {
			parameters = message.substring(2).trim();
			bancType = BanCType.KICK;
			bancName = "auto-kick";
			
		}
		
		
		if(parameters.length() > 2 && parameters.contains(":")) {
			timeStr = parameters.split(":")[1];
			parameters = parameters.split(":")[0];
		}  else {
			m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify <playername>:<time/mins> or PM !help for more information.");
			return;
		}
		/*
		if( !Tools.isAllDigits(timeStr) && !timeStr.contains("d")){//|| !Tools.isAllDigits(timeStr) ) {
			m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify <playername>:<time/mins> or PM !help for more information.");
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
		
		/*if(time > BANC_MAX_DURATION) {
			m_botAction.sendSmartPrivateMessage(name, "The maximum amount of minutes for a BanC is "+BANC_MAX_DURATION+" minutes (365 days). Duration changed to this maximum.");
			time = BANC_MAX_DURATION;
		}
		*/
		// Check target
		// Already banced?
		if(isBanCed(target, bancType)) {
			m_botAction.sendSmartPrivateMessage(name, "Player '"+target+"' is already banced. Check !listban.");
			return;
		} else
		if(m_botAction.getOperatorList().isBotExact(target)) {
			m_botAction.sendSmartPrivateMessage(name, "You can't place a BanC on '"+target+"' as it is a bot.");
			return;
		} else
		// staff member?
		if(m_botAction.getOperatorList().isBot(target)) {
			m_botAction.sendSmartPrivateMessage(name, "Player '"+target+"' is staff, staff can't be banced.");
			return;
		}
			
		// limit != 0	&	limit < time 	>> change
		// limit != 0	&	time == 0		>> change
		if(		getBanCAccessDurationLimit(bancType, opList.getAccessLevel(name)) != 0 &&
				(getBanCAccessDurationLimit(bancType, opList.getAccessLevel(name)) < time || time == 0)) {
			time = getBanCAccessDurationLimit(bancType, opList.getAccessLevel(name));
			m_botAction.sendSmartPrivateMessage(name, "You are not allowed to issue an "+bancName+" of that duration.");
			m_botAction.sendSmartPrivateMessage(name, "The duration has been changed to the maximum duration of your access level: "+time+" mins.");
		}
		
		BanC banc = new BanC(bancType, target, time);
		banc.staffer = name;
		dbLookupIPMID(banc);
		dbAddBan(banc);
		activeBanCs.add(banc);
		
		if(timeToTell > 0){
		    m_botAction.sendChatMessage( name+" initiated an "+bancName+" on '"+target+"' for "+timeToTell+" days." );
		    m_botAction.sendSmartPrivateMessage(name, "BanC #"+banc.id+": "+bancName+" on '"+target+"' for "+timeToTell+" days("+time+" mins) initiated.");
		}
		else if(time > 0) {
			m_botAction.sendChatMessage( name+" initiated an "+bancName+" on '"+target+"' for "+time+" minutes." );
			m_botAction.sendSmartPrivateMessage(name, "BanC #"+banc.id+": "+bancName+" on '"+target+"' for "+time+" minutes initiated.");
		} else {
			m_botAction.sendChatMessage( name+" initiated an infinite/permanent "+bancName+" on '"+target+"'." );
			m_botAction.sendSmartPrivateMessage(name, "BanC #"+banc.id+": "+bancName+" on '"+target+"' for infinite amount of time initiated.");
		}
		m_botAction.sendSmartPrivateMessage(name, "Please do not forget to add comments to your BanC with !bancomment <#id> <comments>.");
		m_botAction.ipcSendMessage(IPCBANC, bancType.toString()+" "+time+":"+target, null, "banc");
	}
	
	/**
	 * Handles the !listban command
	 * @param name player who issued the command
	 * @param parameters any command parameters
	 */
	private void cmdListBan(String name, String parameters) {
		int viewcount = 10;
		parameters = parameters.toLowerCase();
		String sqlWhere = "";
		
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
					if(argument.startsWith("-player='")) {
						String playerString = argument.substring(9);
						
						if(!sqlWhere.isEmpty())
							sqlWhere += " AND ";
							
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
						
						sqlWhere += "fcIP='"+Tools.addSlashes(ipString)+"'";
						
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
					if(argument.startsWith("-staffer='")) {
						String stafferString = argument.substring(10);
						
						if(!sqlWhere.isEmpty())
							sqlWhere += " AND ";
							
						if(stafferString.endsWith("'")) {
							sqlWhere += "fcStaffer='"+stafferString.replace("'", "")+"'";;
						} else {
							sqlWhere += "fcStaffer='"+Tools.addSlashes(stafferString);
							stafferArgument = true;
						}
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
			if(sqlWhere.contains("fnID")) {
				sqlQuery = "SELECT (DATE_ADD(fdCreated, INTERVAL fnDuration MINUTE) > NOW() OR fnDuration = 0) AS active, fnID, fcType, fcUsername, fcIP, fcMID, fcMinAccess, fnDuration, fcStaffer, fcComment, fbNotification, fdCreated FROM tblBanc WHERE "+sqlWhere+" LIMIT 0,1";
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
					result += "mins:"+Tools.formatString(rs.getString("fnDuration"), 3) + "  ";
					result += rs.getString("fcUsername");
					m_botAction.sendSmartPrivateMessage(name, result);
					
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
					m_botAction.sendSmartPrivateMessage(name, result);
					
					String comments = rs.getString("fcComment");
					if(comments != null) {
						m_botAction.sendSmartPrivateMessage(name, " " + comments);
					} else {
						m_botAction.sendSmartPrivateMessage(name, " (no BanC comments)");
					}
				} else {
					m_botAction.sendSmartPrivateMessage(name, "No BanC with that ID found.");
				}
				
				rs.close();
				
			} else {
				if(sqlWhere.length() > 0) {
					sqlWhere = "WHERE "+sqlWhere;
				}
				sqlQuery = "SELECT (DATE_ADD(fdCreated, INTERVAL fnDuration MINUTE) > NOW() OR fnDuration = 0) AS active, fnID, fcType, fcUsername, fcIP, fcMID, fcMinAccess, fnDuration, fcStaffer, fdCreated FROM tblBanc "+sqlWhere+" ORDER BY fnID DESC LIMIT 0,"+viewcount;
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
							result += " mins:"+Tools.formatString(rs.getString("fnDuration"), 3) + " ";
							if(m_botAction.getOperatorList().isModerator(name))
								result += " "+Tools.formatString(rs.getString("fcIP"), 15) + "  ";
							result += rs.getString("fcUsername");
							
							m_botAction.sendSmartPrivateMessage(name, result);
						} while(rs.previous());
					} else {
						// Empty resultset - nothing found
						m_botAction.sendSmartPrivateMessage(name, "No BanCs matching given arguments found.");
					}
				} else {
					// Empty resultset - nothing found
					m_botAction.sendSmartPrivateMessage(name, "No BanCs matching given arguments found.");
				}
			}
			
		} catch(SQLException sqle) {
			m_botAction.sendSmartPrivateMessage(name, "A problem occured while retrieving ban listing from the database. Please try again or report the problem.");
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
				m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify #id and arguments. For more information, PM !help.");
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
						if( (MINACCESS_ER.equalsIgnoreCase(accessRequirement) && !opList.isER(name)) ||
							(MINACCESS_MOD.equalsIgnoreCase(accessRequirement) && !opList.isModerator(name)) ||
							(MINACCESS_SMOD.equalsIgnoreCase(accessRequirement) && !opList.isSmod(name)) ||
							(MINACCESS_SYSOP.equalsIgnoreCase(accessRequirement) && !opList.isSysop(name))) {
							m_botAction.sendSmartPrivateMessage(name, "You can't set the access requirement higher then your own access. (Argument ignored.)");
						} else 
						if(	MINACCESS_ER.equalsIgnoreCase(accessRequirement) ||
							MINACCESS_MOD.equalsIgnoreCase(accessRequirement) ||
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
							m_botAction.sendSmartPrivateMessage(name, "Syntax error on the -notif argument. (Argument ignored.)");
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
			m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify #id and arguments. For more information, PM !help.");
			return;
		}
		
		if(sqlSet.isEmpty()) {
			// No arguments
			m_botAction.sendSmartPrivateMessage(name, "Syntax error (no arguments specified). Please specify #id and arguments. For more information, PM !help.");
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
				if(	(MINACCESS_ER.equals(accessReq) && !opList.isER(name)) ||
					(MINACCESS_MOD.equals(accessReq) && !opList.isModerator(name)) || 
				   	(MINACCESS_SMOD.equals(accessReq) && !opList.isSmod(name)) ||
				   	(MINACCESS_SYSOP.equals(accessReq) && !opList.isSysop(name))) {
					m_botAction.sendSmartPrivateMessage(name, "You don't have enough access to modify this BanC.");
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
								case KICK : 	m_botAction.sendChatMessage("Auto-kick BanC #"+banc.id+" ("+banc.playername+") has expired."); break;
							}
							m_botAction.ipcSendMessage(IPCBANC, "REMOVE "+banc.type.toString()+" "+banc.playername, null, "banc");
							iterator.remove();
						}
					}
				}
			}
			
			m_botAction.sendSmartPrivateMessage(name, "BanC #"+banID+" changed.");
			
		} catch(SQLException sqle) {
			m_botAction.sendSmartPrivateMessage(name, "A problem occured while modifying the ban in the database. Please try again or report the problem.");
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
			m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify #id and comments. For more information, PM !help.");
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
				m_botAction.sendSmartPrivateMessage(name, "BanC #"+id+" modified");
			} else {
				m_botAction.sendSmartPrivateMessage(name, "BanC #"+id+" doesn't exist.");
			}
			
			// Apply the banc comment to the active banc
			BanC activeBanc = lookupActiveBanC(id);
			if(activeBanc != null) {
				activeBanc.comment = comments;
			}
		} catch(SQLException sqle) {
			m_botAction.sendSmartPrivateMessage(name, "A problem occured while modifying the ban in the database. Please try again or report the problem.");
			Tools.printStackTrace("SQLException while modifying the database", sqle);
		}
		
	}
	
	private void doForceDBConnection(String name){
	    try{
	        
	        this.psKeepAlive1.execute();
	        this.psKeepAlive2.execute();
	        this.psLookupIPMID.execute();
        
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
			m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please specify #id. For more information, PM !help.");
			return;
		} else {
			id = Integer.parseInt(message.substring(1));
		}
		
		// failsafe
		if(id == -1) return;
		
		try {
			psRemoveBanC.setInt(1, id);
			psRemoveBanC.executeUpdate();
			
			m_botAction.sendSmartPrivateMessage(name, "BanC #"+id+" removed");
			m_botAction.sendChatMessage("BanC #"+id+" has been lifted by "+name);
			
			// Make the banc expired so it's removed from the player if still active.
			BanC activeBanc = lookupActiveBanC(id);
			if(activeBanc != null) {
				m_botAction.ipcSendMessage(IPCBANC, "REMOVE "+activeBanc.type.toString()+" "+activeBanc.playername, null, "banc");
				activeBanCs.remove(activeBanc);
			}
		} catch(SQLException sqle) {
			m_botAction.sendSmartPrivateMessage(name, "A problem occured while deleting the banc from the database. Please try again or report the problem.");
			Tools.printStackTrace("SQLException while modifying the database", sqle);
		}
	}
	
	private void cmdReload(String name) {
		activeBanCs.clear();
		this.loadActiveBanCs();
		m_botAction.sendSmartPrivateMessage(name, "Bans reloaded from database.");
		this.sendIPCActiveBanCs(null);
	}
	
	private void cmdListActiveBanCs(String name) {
		for(BanC banc : activeBanCs) {
			m_botAction.sendSmartPrivateMessage(name, "#"+banc.getId()+" "+banc.getType()+" "+banc.getDuration()+"mins on "+banc.getPlayername());
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
	                banc.type = BanCType.valueOf(rs.getString("fcType"));
	                banc.playername = rs.getString("fcUsername");
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
			    psAddBanC.setString(1, "SPEC");
            
			else
			    psAddBanC.setString(1, banc.type.name());
			
			psAddBanC.setString(2, banc.playername);
			psAddBanC.setString(3, banc.IP);
			psAddBanC.setString(4, banc.MID);
			psAddBanC.setString(5, MINACCESS_ER);
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
							case KICK : 	m_botAction.sendChatMessage("Auto-kick BanC #"+banc.id+" ("+banc.playername+") has expired."); break;
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
        		psLookupIPMID.execute();
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
		private Boolean notification = true;
		
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