package twcore.bots.staffbot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import twcore.bots.Module;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.events.Message;
import twcore.core.util.Tools;

public class staffbot_commands extends Module {
	private final static int CHECK_LOG_DELAY = 5000; // Delay when *log is checked for *warnings originally 30000
	
	private TimerTask getLog;
	private int[] m_commandWatch = { 0, 0, 0 }; // 0-ArenaCommands 1-StafferCommands 2-StafferArenaCommands
	private LinkedList<CommandWatch> watches = new LinkedList<CommandWatch>();
	private int nextID = 1;
	private boolean m_watchAll = false;
	private boolean m_timerStatus = true;
	private boolean m_chat = false;
	private String m_lastWatchAllUser = "";
	private Date logDate;
	private Vector<String> m_log = new Vector<String>(60);
	private final static TimeZone CST = TimeZone.getTimeZone("CST"); 
    private int YEAR;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy EEE MMM dd HH:mm:ss", Locale.US);
    private SimpleDateFormat chatFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.US);

	
	// Helps (strange to redefine each time someone types !help)
    
    final String[] helpSmod = {
            "-------------[ Command Watch Commands: SMod+ ]--------------------------------------------",
            " !watchall                          -- relays all *commands to upperstaff-chat", 
            " !watcharena <arena>                -- relays *commands done in <arena> to upperstaff-chat", 
            " !watcharena Public <#>             -- relays *commands done in one of the Public Arenas ",
            "                                       to upperstaff-chat", 
            " !watcharena <arena>:<reason>       -- relays *commands done in <arena> to upperstaff-chat ",
            "                                       (reason optional)",     
            " !watchstaffer <staffer>            -- relays *commands of <staffer> to upperstaff-chat",  
            " !watchstaffer <staffer>:<reason>   -- relays *commands of <staffer> to upperstaff-chat ",
            "                                       (reason optional)",  
            " !watch <staffer>:<arena>           -- relays *commands of <staffer> done in <arena> to ",
            "                                       upperstaff-chat",  
            " !watch <staffer>:Public <#>        -- relays *commands of <staffer> done in one of the ",
            "                                       Public Arenas to upperstaff-chat",
            " !watch <staffer>:<arena>:<reason>  -- relays *commands of <staffer> done in <arena> to ",
            "                                       upperstaff-chat (reason optional)",    
            " !watches                           -- Displays all active *command watches",
            " !watchinfo <WatchID>               -- Displays all information for a given Command Watch",
            "                                       ID number <WatchID>",
            " !removewatch <WatchID>             -- Removes the command watch with ID number <WatchID>",
            " !clearwatches                      -- Removes all watches and resets watchall to off"
    };
	
	@Override
	public void initializeModule() {
        GregorianCalendar c = new GregorianCalendar(CST);
        YEAR = c.get(GregorianCalendar.YEAR);
		// TimerTask to check the logs for *commands
        /*
        getLog = new TimerTask() {
            public void run() {
                m_botAction.sendUnfilteredPublicMessage( "*log" );
            }
        };
        m_botAction.scheduleTaskAtFixedRate( getLog, 0, CHECK_LOG_DELAY );
        */
	}
	
	@Override
	public void cancel() {
		m_botAction.cancelTask(getLog);
	}
	

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.MESSAGE);
	}
	
	public void resetTimer() {
	    if (!m_timerStatus && (m_watchAll || m_commandWatch[0] > 0 || m_commandWatch[1] > 0 || m_commandWatch[2] > 0)) {
	        m_timerStatus = true;
	        m_botAction.scheduleTaskAtFixedRate( getLog, 0, CHECK_LOG_DELAY );
	    } else if (m_timerStatus && !m_watchAll && m_commandWatch[0] < 1 && m_commandWatch[1] < 1 && m_commandWatch[2] < 1) {
	        m_timerStatus = false;
	        getLog.cancel();
            m_botAction.sendUnfilteredPublicMessage("*log");
	    }
	}
	
	public void handleEvent(Message event) {
        String message = event.getMessage();
        int messageType = event.getMessageType();
        String name = event.getMessager();

        if(name == null)  // try looking up the sender's name by playerid
            name = m_botAction.getPlayerName(event.getPlayerID());
        if(name == null)
            name = "-";
        
        OperatorList m_opList = m_botAction.getOperatorList();
        
        if( event.getMessageType() == Message.ARENA_MESSAGE && message.indexOf('*') >= 0 && message.toLowerCase().indexOf( " *warn " ) == -1){
            if (!m_timerStatus)
                return;
            
            boolean toAlert = false;
            Date eventDate = null;
            try {
                eventDate = dateFormat.parse(YEAR + " " + message.substring(0, 19));
            } catch(ParseException pe) {}
            
            
            if (eventDate == null) {
                m_botAction.sendChatMessage(2, "Time conversion problem, contact BotDev");
                return;
            } 
           
            String eventMessage = message.substring(message.indexOf(')') + 2);
            String eventStaffer = message.substring(message.indexOf("Ext: ") + 5, message.indexOf('(')).trim();
            String eventArena = message.substring(message.indexOf('(') + 1, message.indexOf(')'));
            
            if (m_opList.isBotExact( eventStaffer ) && eventMessage.indexOf("*log") > -1 && (logDate == null || eventDate.after(logDate))) {
                logDate = eventDate;
                return;
            }
            
            if (m_log.contains(message) || logDate == null || m_opList.isBotExact( eventStaffer ) || eventDate.before(logDate))
                return;
            
            if (m_watchAll)
                toAlert = true;
            else {
                Iterator<CommandWatch> i = watches.iterator();
                while (!toAlert && i.hasNext()) {
                    CommandWatch thisWatch = i.next();
                    if (m_commandWatch[0] > 0 && eventArena.equalsIgnoreCase(thisWatch.getArena()))
                        toAlert = true;
                    else if (m_commandWatch[1] > 0 && eventStaffer.equalsIgnoreCase(thisWatch.getStaffer()))
                        toAlert = true; 
                    else if (m_commandWatch[2] > 0 && eventStaffer.equalsIgnoreCase(thisWatch.getStaffer()) && eventArena.equalsIgnoreCase(thisWatch.getArena()))
                        toAlert = true;
                }
            }
            
            if (toAlert) {
                m_log.add(0, message);
                m_log.setSize(60);
                if (message.contains(") to"))
                    eventArena += ") ";
                else
                    eventArena += "):";
                String eventTime = chatFormat.format(eventDate);
                
                m_botAction.sendChatMessage(2, eventTime+ " " + eventStaffer + " (" + eventArena + eventMessage);
            }
            return;
	    }
        // Ignore non-private messages + Ignore non-commands + Ignore player's commands
	    if( (messageType != Message.PRIVATE_MESSAGE && messageType != Message.REMOTE_PRIVATE_MESSAGE && messageType != Message.CHAT_MESSAGE) || !message.startsWith("!") || !m_opList.isBot(name))
	        return;
	    
        if (messageType == Message.CHAT_MESSAGE) 
            m_chat = true;
        else if (messageType == Message.PRIVATE_MESSAGE)
            m_chat = false;
	    
        if( message.toLowerCase().startsWith("!help") && m_opList.isSmod(name) ) {
            prepareMessage(name, helpSmod);
	        return;
        }
        
        if( m_opList.isSmod( name ) ) {
            CommandWatch watch;
            if (message.toLowerCase().startsWith("!watchall")) {
                if (m_watchAll) {
                    m_watchAll = false;
                    prepareMessage(name, "Watch all commands: [DISABLED]");
                } else {
                    m_watchAll = true;   
                    prepareMessage(name, "Watch all commands: [ENABLED]");
                }     
                m_lastWatchAllUser = name;
                resetTimer();
            } else if (message.toLowerCase().startsWith("!watchstaffer ")) {
                if (message.indexOf(':') != -1 
                        && !" ".equals(message.substring(message.indexOf(':')-1, message.indexOf(':'))) 
                        && message.length() > message.indexOf(':')+2)  {
                    watch = createWatch(name, 1, message.substring(message.indexOf(' ') + 1, message.indexOf(':')), "_any", message.substring(message.indexOf(':') + 1));
                    if (watch != null)
                        prepareMessage(name, "Command Watch " + watch.getID() + " created successfully");
                    else
                        prepareMessage(name, "This command watch is all ready active.");
                } else if (message.indexOf(':') == -1 && message.indexOf(' ') < message.length() - 1){
                    watch = createWatch(name, 1, message.substring(message.indexOf(' ') + 1), "_any");
                    if (watch != null)
                        prepareMessage(name, "Command Watch " + watch.getID() + " created successfully");
                    else
                        prepareMessage(name, "This command watch is all ready active.");
                } else {
                    prepareMessage(name, "Syntax error, please use the following format: !watchstaffer <Name> or !watchstaffer <Name>:<Reason>");
                    return;                          
                }
            } else if (message.toLowerCase().startsWith("!watcharena ")) {
                // Syntax check !watcharena a:d
                if (message.indexOf(':') != -1 
                        && (message.indexOf(' ') == message.lastIndexOf(' ', message.indexOf(':')) || message.toLowerCase().contains("public ")) 
                        && message.length() > message.indexOf(':') + 1) {
                    watch = createWatch(name, 0, "~", 
                            message.substring(message.indexOf(' ') + 1, message.indexOf(':')), 
                            message.substring(message.indexOf(':') + 1));
                    if (watch != null)
                        prepareMessage(name, "Command Watch " + watch.getID() + " created successfully");
                    else
                        prepareMessage(name, "This command watch is all ready active.");
                // Syntax check !watcharena a
                } else if (message.indexOf(':') == -1 
                        && message.length() > 12 
                        && (message.indexOf(' ') == message.lastIndexOf(' ', message.length()-1)) ||  message.toLowerCase().contains("public ")) {
                    watch = createWatch(name, 0, "~", message.substring(message.indexOf(' ') + 1));
                    if (watch != null)
                        prepareMessage(name, "Command Watch " + watch.getID() + " created successfully");
                    else
                        prepareMessage(name, "This command watch is all ready active.");
                } else {
                    prepareMessage(name, "Syntax error, please use the following format: !watcharena <Arena> or !watcharena <Arena>:<Reason>");
                    return;                      
                }
            } else if (message.toLowerCase().startsWith("!watch ")) {
                // Syntax check !watch n:a:d
                if (message.indexOf(':') != -1 
                        && message.indexOf(':') != message.lastIndexOf(':', message.length() - 1) 
                        && !" ".equals(message.substring(message.indexOf(':') - 1, message.indexOf(':'))) 
                        && (message.lastIndexOf(' ', message.indexOf(':')) == message.lastIndexOf(' ', message.indexOf(':', message.indexOf(':') + 1)) 
                                || message.toLowerCase().contains("public ")) 
                        && message.indexOf(':') + 1 != message.lastIndexOf(':')
                        && message.length() > message.lastIndexOf(':') + 1) {
                    watch = createWatch(name, 2, message.substring(message.indexOf(' ') + 1, message.indexOf(':')), message.substring(message.indexOf(':') + 1, message.lastIndexOf(':', message.length()-1)), message.substring(message.lastIndexOf(':', message.length()-1) + 1));
                    if (watch != null)
                        prepareMessage(name, "Command Watch " + watch.getID() + " created successfully");
                    else
                        prepareMessage(name, "This command watch is all ready active.");
                // Syntax check !watch n:a
                } else  if (message.indexOf(':') != -1 
                        && message.indexOf(':') + 1 < message.length() 
                        && message.indexOf(':') + 1 != message.lastIndexOf(':')
                        && (message.lastIndexOf(' ', message.indexOf(':')) == message.lastIndexOf(' ') || message.toLowerCase().contains("public "))
                        && !" ".equals(message.substring(message.indexOf(':') - 1, message.indexOf(':')))){
                    watch = createWatch(name, 2, message.substring(message.indexOf(' ') + 1, message.indexOf(':')), message.substring(message.indexOf(':') + 1));
                    if (watch != null)
                        prepareMessage(name, "Command Watch " + watch.getID() + " created successfully");
                    else
                        prepareMessage(name, "This command watch is all ready active.");
                } else {
                    prepareMessage(name, "Syntax error, please use the following format: !watch <Name>:<Arena> or !watch <Name>:<Arena>:<Reason>");
                    return;  
                }
            } else if (message.toLowerCase().startsWith("!removewatch ")) {
                if (message.length() < 12) {
                    prepareMessage(name, "Syntax error, please use the following format: !removewatch <WatchID>");
                    return;
                }
                int id = -1;
                try {
                    id = Integer.parseInt(message.substring(message.indexOf(' ') + 1));
                } catch (Exception NumberFormatException) {
                    prepareMessage(name, "Syntax error, please use the following format: !removewatch <WatchID>");
                    return;                    
                }
                if (id == 0) {
                    prepareMessage(name, "Could not remove Command Watch 0 -- to enable/disable it, use !watchall");                    
                } else if(removeWatch(id))
                    prepareMessage(name, "Command Watch " + id + " has been removed");
                else
                    prepareMessage(name, "Command Watch " + id + " does not exist");
            } else if (message.toLowerCase().startsWith("!watches"))
                listWatches(name);
            else if (message.toLowerCase().startsWith("!watchinfo ")) {
                int id = -1;
                try {
                    id = Integer.parseInt(message.substring(11));
                } catch (Exception NumberFormatException) {
                    prepareMessage(name, "Syntax error, please use the following format: !watchinfo <WatchID>");
                    return;                    
                }
                watchInfo(name, id);
            } else if (message.toLowerCase().startsWith("!forcecheck")) {
                m_botAction.sendPublicMessage("*log");
                prepareMessage(name, "" + Tools.getTimeStamp() + " Log force checked");
            } else if (message.toLowerCase().startsWith("!clearwatches"))
                clearWatches(name);
            // else don't do shit
        }
	} 

	public CommandWatch createWatch(String requester, int type, String staffer, String arena) {
	    boolean duplicate = false;
	    
	    Iterator<CommandWatch> i = watches.iterator();
	    while (i.hasNext()) {
	        if (i.next().isDuplicate(type, staffer, arena))
	            duplicate = true;
	    }
	    
    	if (!duplicate) {    
    	    CommandWatch watch = new CommandWatch(nextID, requester, type, staffer, arena);
    	    watches.add(watch);    	    
    	    nextID++;
            m_commandWatch[type]++;
            resetTimer();
            return watch;
    	} else {
            return null;
    	}	    
	}
    
    public CommandWatch createWatch(String requester, int type, String staffer, String arena, String reason) {
        boolean duplicate = false;
        
        Iterator<CommandWatch> i = watches.iterator();
        while (i.hasNext()) {
            if (i.next().isDuplicate(type, staffer, arena))
                duplicate = true;
        }
        
        if (!duplicate) {    
            CommandWatch watch = new CommandWatch(nextID, requester, type, staffer, arena, reason);
            watches.add(watch);         
            nextID++;
            m_commandWatch[type]++;
            resetTimer();
            return watch;
        } else {
            return null;
        }       
    }
	
	public boolean removeWatch(int id) {
	    if (id > 0 && id < nextID) {
	        CommandWatch thisWatch;
	        boolean removed = false;
	        
	        Iterator<CommandWatch> i = watches.iterator();
	        while (!removed && i.hasNext()) {
	            thisWatch = i.next();
	            if (thisWatch.getID() == id) {
	                m_commandWatch[thisWatch.getType()]--;
	                watches.remove(thisWatch);
	                removed = true;
	                nextID--;
	                resetTimer();
	                refreshWatchList();
	            }
	        }
	        if (removed)
	            return true;
	        else
	            return false;
	    } else
	        return false;
	}
	
	public void clearWatches(String name) {
	    watches.clear();
	    m_watchAll = false;
	    m_lastWatchAllUser = name;
	    nextID = 1;
	    resetTimer();	
        refreshWatchList();    
        prepareMessage(name, "All watches have been cleared and reset");
	}
	
	private void refreshWatchList() {
	    int currentID = 1;
        Iterator<CommandWatch> i = watches.iterator();
        while (i.hasNext()) {
            CommandWatch thisWatch = i.next();
            thisWatch.setID(currentID);
            currentID++;
        }
	}
	
	public void listWatches(String name) {
        prepareMessage(name, "--------------------------------");
	    prepareMessage(name, "ID - Command Watch Description -");
        prepareMessage(name, "--------------------------------");
        if (m_watchAll)
            prepareMessage(name, " 0 - Watch all commands: [ENABLED]");        
        else
            prepareMessage(name, " 0 - Watch all commands: [DISABLED]");    

        Iterator<CommandWatch> i = watches.iterator();
        while (i.hasNext()) {
            prepareMessage(name, i.next().toString());
        } 
	}
	
	public void watchInfo(String name, int id) {
	    if (id == 0) {
	        String reply = "Watch all commands is ";
	        if (m_watchAll)
	            reply += "[ENABLED] ";
	        else
                reply += "[DISABLED] ";
            
	        if (m_lastWatchAllUser.length() > 0)
	            reply += "and was last modified by: " + m_lastWatchAllUser;
	        else
                reply += "and has yet to be modified";

            prepareMessage(name, reply);
	        return;
	    }
	    
	    
	    CommandWatch watch = null;
	    Iterator<CommandWatch> i = watches.iterator();
	    while (i.hasNext() && (watch == null || watch.getID() != id)) {
	        watch = i.next();
	    }
	    
	    if (watch != null && watch.getID() == id) {
	        String[] info = watch.getInfo();
	        for (int x = 0; x < info.length; x++) {
	            prepareMessage(name, info[x]);
	        }
	    } else {
	        prepareMessage(name, "Command Watch " + id + " not found");
	    }
	}
    
    private void prepareMessage(String name, String[] message) {
        if (m_chat) {
            for (int i = 0; i < message.length; i++) {
                m_botAction.sendChatMessage(2, message[i]);
            }
        } else {
            m_botAction.smartPrivateMessageSpam(name, message);
        }
    }
    
    private void prepareMessage(String name, String message) {
        if (m_chat) {
            m_botAction.sendChatMessage(2, message);
        } else {
            m_botAction.sendSmartPrivateMessage(name, message);
        }
    }
    
}

class CommandWatch {
    // 0-ArenaCommands 1-StafferCommands 2-StafferArenaCommands 
    boolean[] m_type = { false, false, false }; 
    int m_id = -1;
    String m_requester, m_staffer, m_arena, m_reason, m_date;
    
    //no arena == _any | no staffer = all
    public CommandWatch(int id, String requester, int type, String staffer, String arena) {
        m_id = id;
        m_requester = requester;
        m_type[type] = true;
        m_staffer = staffer;
        m_arena = arena;
        m_reason = "None given";
        m_date = Tools.getTimeStamp();
    }
    
    public CommandWatch(int id, String requester, int type, String staffer, String arena, String reason) {
        m_id = id;
        m_requester = requester;
        m_type[type] = true;
        m_staffer = staffer;
        m_arena = arena;
        m_reason = reason;
        m_date = Tools.getTimeStamp();
    }
    
    public String toString() {
        String string = "";
        if (m_id < 10)
            string += " " + m_id + " - Watching ";
        else
            string += m_id + " - Watching ";

        if ("~".equals(m_staffer))
            string += "all commands in arena [" + m_arena + "]";
        else {
            if ("_any".equals(m_arena))
                string += "all commands from [" + m_staffer + "]";
            else
                string += "all commands from [" + m_staffer + "] in arena [" + m_arena + "]";
        }
        return string;
    }
    
    public String[] getInfo() {
        String[] info;
        
        LinkedList<String> builder = new LinkedList<String>();
            builder.add("+-----------------------------------------------+");
        if (m_id < 10)
            builder.add("+         Command Watch " + m_id + ": Full Details         +");
        else
            builder.add("+         Command Watch " + m_id + ": Full Details        +");
            builder.add("+-----------------------------------------------+");
            builder.add(" Date Created:    " + m_date);
            builder.add(" Requested by:    " + m_requester);
        if(m_type[0]) {
            builder.add(" Watch Type:      Arena Specific");
            builder.add(" Arena:           " + m_arena);
        } else if (m_type[1]) {
            builder.add(" Watch Type:      Staff Member Specific");
            builder.add(" Staff Member:    " + m_staffer);            
        } else if (m_type[2]) {
            builder.add(" Watch Type:      Staff Member & Arena Specific");
            builder.add(" Staff Member:    " + m_staffer);          
            builder.add(" Arena:           " + m_arena);
        }
            builder.add(" Reason:          " + m_reason);   
            builder.add("+-----------------------------------------------+");
        
        info = builder.toArray(new String[builder.size()]);
        return info;
    }
    
    public boolean isDuplicate(int type, String staffer, String arena) {
        if (m_type[type] && m_staffer.equals(staffer) && m_arena.equals(arena))
            return true;
        else
            return false;
    }
    
    public int getID() {
        return m_id;
    }
    
    public void setID(int id) {
        m_id = id;
    }
    
    public String getRequester() {
        return m_requester;
    }
    
    public int getType() {
        for (int i = 0; i < 3; i++) {
            if (m_type[i])
                return i;
        }
        return -1;
    }
    
    public String getStaffer() {
        return m_staffer;
    }
    
    public String getArena() {
        return m_arena;
    }
}
