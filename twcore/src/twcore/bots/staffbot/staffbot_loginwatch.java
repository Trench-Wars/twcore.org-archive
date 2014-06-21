package twcore.bots.staffbot;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import twcore.bots.Module;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

/**
 * This module reports bad commands (and player kicks for message flooding) that are shown on the *log
 * This module also logs all *log lines to a file. The functionality is copied from the mrarrogant bot.
 * 
 * @author MMaverick
 */
public class staffbot_loginwatch extends Module {
	
    private Map<String, WatchComment> watchedIPs;
    private Map<String, WatchComment> watchedNames;
    private Map<String, WatchComment> watchedMIDs;
    
    private SimpleDateFormat sdf = new SimpleDateFormat("MM/dd");
    private static enum SortField { NONE, DATE, TRIGGER, ISSUER };

	@Override
	public void initializeModule() {
        watchedIPs = Collections.synchronizedMap(new HashMap<String, WatchComment>());
        watchedNames = Collections.synchronizedMap(new HashMap<String, WatchComment>());
        watchedMIDs = Collections.synchronizedMap(new HashMap<String, WatchComment>());
        m_botAction.ipcSubscribe("StaffBot Watch");


        loadWatches();
		
	}

	@Override
	public void cancel() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
		
	}
	
	public void doHelpCmd(String sender) {
        String[] message = { "                                                       ", " [STAFFBOT LOGINWATCH MODULE]", "                                                       ",
               
                " !nameWatch <Name>:<reason>     - Watches logins for <Name> with the specified <reason>", " !nameWatch <Name>              - Disables the login watch for <Name>",
                " !IPWatch   <IP>:<reason>       - Watches logins for <IP> with the specified <reason>", " !IPWatch   <IP>                - Disables the login watch for <IP>",
                " !MIDWatch  <MID>:<reason>      - Watches logins for <MID> with the specified <reason>", " !MIDWatch  <MID>               - Disables the login watch for <MID>",
                " !clearNameWatch                - Clears all login watches for names", " !clearIPWatch                  - Clears all login watches for IPs",
                " !clearMIDWatch                 - Clears all login watches for MIDs", 
                " !showWatches [<Sort>:<Dir>]    - Shows all current login watches",
                "                                  Sort options: d(ate), t(rigger), i(ssuer); Dir: A(scending), D(escending)"
        };
        m_botAction.smartPrivateMessageSpam(sender, message);
    }
	
    public void handleChatMessage(String sender, String message) {
        String command = message.toLowerCase();

        /*
         * Extra check for smod and twdop added
         * -fantus
         */
        if (!m_botAction.getOperatorList().isModerator(sender)) {
            return;
        }

        try {

           if (command.startsWith("!ipwatch ")){
                doIPWatchCmd(sender, message.substring(9).trim());
           }  else if (command.startsWith("!namewatch "))
                doNameWatchCmd(sender, message.substring(11).trim());
            else if (command.startsWith("!midwatch ")) {
                doMIDWatchCmd(sender, message.substring(10).trim());
            }  else if (command.equals("!clearipwatch"))
                doClearIPWatchCmd();
            else if (command.equals("!clearnamewatch")) {
                doClearNameWatchCmd();
            }  else if (command.equals("!clearmidwatch"))
                doClearMIDWatchCmd();
            else if (command.equals("!showwatches")) {
                doShowWatchesCmd(sender, "", true);
            } else if (command.startsWith("!showwatches ")) {
            	doShowWatchesCmd(sender, command.substring(13), true);
            } else if (command.equals("!help")){
            	doHelpCmd(sender);
            }
        } catch (Exception e) {
            m_botAction.sendChatMessage(e.getMessage());
        }
    }

    public void handleEvent(Message event) {
        String sender = event.getMessager();
        String message = event.getMessage();
        int messageType = event.getMessageType();

        if (messageType == Message.CHAT_MESSAGE) 
        	if (event.getChatNumber() == 4)
            handleChatMessage(sender, message);
    }
    
    /**
     * Check if a name is being watched for, and notify on chat if so.
     * 
     * @param name
     *            Name to check
     * @param IP
     *            IP of player
     * @param MacId
     *            MacID of player
     */
    public void checkName(String name, String IP, String MacID) {
        if (watchedNames.containsKey(name.toLowerCase())) {
            m_botAction.sendChatMessage(4,"NAMEWATCH: '" + name + "' logged in.  (IP: " + IP + ", MID: " + MacID + ")");
            m_botAction.sendChatMessage(4,"           " + watchedNames.get(name.toLowerCase()).toString());
        }
    }

    /**
     * Check if an IP is being watched for, and notify on chat if so.
     * 
     * @param name
     *            Name of player
     * @param IP
     *            IP to check
     * @param MacId
     *            MacID of player
     */
    public void checkIP(String name, String IP, String MacID) {
        for (String IPfragment : watchedIPs.keySet()) {
            if (IP.startsWith(IPfragment)) {
                m_botAction.sendChatMessage(4,"IPWATCH: Match on '" + name + "' - " + IP + " (matches " + IPfragment + "*)  MID: " + MacID);
                m_botAction.sendChatMessage(4,"         " + watchedIPs.get(IPfragment).toString());
            }
        }
    }

    /**
     * Check if an MID is being watched for, and notify on chat if so.
     * 
     * @param name
     *            Name of player
     * @param IP
     *            IP of player
     * @param MacId
     *            MacID to check
     */
    public void checkMID(String name, String IP, String MacID) {
        if (watchedMIDs.containsKey(MacID)) {
            m_botAction.sendChatMessage(4,"MIDWATCH: Match on '" + name + "' - " + MacID + "  IP: " + IP);
            m_botAction.sendChatMessage(4,"          " + watchedMIDs.get(MacID).toString());
        }
    }
	
	 /**
     * Starts watching for a name to log on.
     * 
     * @param name
     *            Name to watch for
     */
    public void doNameWatchCmd(String sender, String message) {
        String[] params = message.split(":");
        String name = params[0].trim();

        if (watchedNames.containsKey(name.toLowerCase()) && (params.length == 1 || params[1] == null || params[1].length() == 0)) {
            watchedNames.remove(name.toLowerCase());
            m_botAction.sendChatMessage(4,"Login watching disabled for '" + name + "'.");
            saveWatches();
        } else if (params.length == 1 || params[1] == null || params[1].length() == 0) {
            m_botAction.sendChatMessage(4,"Please specify a comment/reason after the name seperated by a : . For example, !NameWatch Pure_Luck:Bad boy .");
        } else {
            String comment = params[1].trim();

            if (watchedNames.containsKey(name.toLowerCase())) {
                m_botAction.sendChatMessage(4,"Login watching for '" + name + "' reason changed.");
            } else {
                m_botAction.sendChatMessage(4,"Login watching enabled for '" + name + "'.");
            }
            String date = sdf.format(System.currentTimeMillis());
            watchedNames.put(name.toLowerCase(), new WatchComment(date, sender + ": " + comment));
            saveWatches();
        }
    }

    /**
     * Starts watching for a given MacID.
     * 
     * @param MID
     *            MID to watch for
     */
    public void doMIDWatchCmd(String sender, String message) {
        String[] params = message.split(":");
        String MID = params[0].trim();

        if (watchedMIDs.containsKey(MID) && (params.length == 1 || params[1] == null || params[1].length() == 0)) {
            watchedMIDs.remove(MID);
            m_botAction.sendChatMessage(4,"Login watching disabled for MID: " + MID);
            saveWatches();
        } else if (params.length == 1 || params[1] == null || params[1].length() == 0) {
            m_botAction.sendChatMessage(4,"Please specify a comment/reason after the MID seperated by a : . For example, !MIDWatch 777777777:I like the number .");
        } else {
            String comment = params[1].trim();

            if (watchedMIDs.containsKey(MID)) {
                m_botAction.sendChatMessage(4,"Login watching for MID " + MID + " reason changed.");
            } else {
                m_botAction.sendChatMessage(4,"Login watching enabled for MID: " + MID);
            }
            String date = sdf.format(System.currentTimeMillis());
            watchedMIDs.put(MID, new WatchComment(date, sender + ": " + comment));
            saveWatches();

        }
    }
    
    /**
     * Starts watching for an IP starting with a given string.
     * 
     * @param IP
     *            IP to watch for
     */
    public void doIPWatchCmd(String sender, String message) {
        String[] params = message.split(":");
        String IP = params[0].trim();

        if (watchedIPs.containsKey(IP) && (params.length == 1 || params[1] == null || params[1].length() == 0)) {
            watchedIPs.remove(IP);
            m_botAction.sendChatMessage(4,"Login watching disabled for IPs starting with " + IP);
            saveWatches();
        } else if (params.length == 1 || params[1] == null || params[1].length() == 0) {
            m_botAction.sendChatMessage(4,"Please specify a comment/reason after the IP seperated by a : . For example, !IPWatch 123.123.123.9:Possible hacker .");
        } else {
            String comment = params[1].trim();

            if (watchedIPs.containsKey(IP)) {
                m_botAction.sendChatMessage(4,"Login watching for (partial) IP " + IP + " reason changed.");
            } else {
                m_botAction.sendChatMessage(4,"Login watching enabled for IPs starting with " + IP);
            }
            String date = sdf.format(System.currentTimeMillis());
            watchedIPs.put(IP, new WatchComment(date, sender + ": " + comment));
            saveWatches();
        }
    }


    /**
     * Stops all IP watching.
     */
    public void doClearIPWatchCmd() {
        watchedIPs.clear();
        m_botAction.sendChatMessage(4,"All watched IPs cleared.");
        saveWatches();
    }

    /**
     * Stops all name watching.
     */
    public void doClearNameWatchCmd() {
        watchedNames.clear();
        m_botAction.sendChatMessage(4,"All watched names cleared.");
        saveWatches();
    }

    /**
     * Stops all MacID watching.
     */
    public void doClearMIDWatchCmd() {
        watchedMIDs.clear();
        m_botAction.sendChatMessage(4,"All watched MIDs cleared.");
        saveWatches();
    }

    /**
     * New version for {@link #doShowWatchesCmd()}, handles the !showwatches and !showmywatches commands.
     * <p>
     * This version combines the standard result provided by !showwatches with optional parameters, being:
     * <ul>
     *  <li>Sort by date issued;
     *  <li>Sort by trigger;
     *  <li>Sort by issuer.
     * </ul>
     * The above can be combined by any of the following:
     * <ul>
     *  <li>Ascending or descending sort;
     *  <li>Only show the watches issued by the user of the command.
     * </ul>
     * <p>
     * Please do note that any sorting is done per "group" and that the result is still displayed in the appropriate chat.
     * @param name Issuer of the command.
     * @param args Optional arguments: [<code><</code>{d(ate), t(rigger), i(ssuer)}>:<code><</code>{a(scending), d(escending)}>]
     * @param showAll True if all watches are to be shown, false if only the issuer's watches are to be shown.
     */
    public void doShowWatchesCmd(String name, String args, boolean showAll) {
        SortField sortBy = SortField.NONE;
        boolean sortDirection = false;
        boolean nothingFound = true;
        Map<String, WatchComment> tmpWatchComments = new TreeMap<String, WatchComment>();
        
        if(!args.isEmpty()) {
            String[] splitArgs = args.toLowerCase().split(":");
            
            if(splitArgs.length != 2) {
                m_botAction.sendSmartPrivateMessage(name, "Invalid arguments, please consult !help.");
                return;
            }
            
            if(splitArgs[0].startsWith("d"))
                sortBy = SortField.DATE;
            else if(splitArgs[0].startsWith("t"))
                sortBy = SortField.TRIGGER;
            else if(splitArgs[0].startsWith("i"))
                sortBy = SortField.ISSUER;
            else
                sortBy = SortField.NONE;
            
            sortDirection = splitArgs[1].startsWith("a");
        }
        
        if (watchedIPs.size() == 0) {
            m_botAction.sendSmartPrivateMessage(name, "IP:   (none)");
        } else {
            if(sortBy != SortField.NONE)
                tmpWatchComments = WatchListSorter.sortByValue(watchedIPs, sortBy, sortDirection);
            else
                tmpWatchComments = watchedIPs;
            for (String IP : tmpWatchComments.keySet()) {
                WatchComment com = tmpWatchComments.get(IP);
                if(!showAll && !com.comment.startsWith(name))
                    continue;
                
                m_botAction.sendSmartPrivateMessage(name, com.date + " IP:   " + Tools.formatString(IP, 19) + "  ( " + com.comment + " )");
                nothingFound = false;
            }
            if(nothingFound)
                m_botAction.sendSmartPrivateMessage(name, "IP:   (none)");
        }
        
        nothingFound = true;
        
        if (watchedMIDs.size() == 0) {
            m_botAction.sendSmartPrivateMessage(name, "MID:  (none)");
        } else {
            if(sortBy != SortField.NONE)
                tmpWatchComments = WatchListSorter.sortByValue(watchedMIDs, sortBy, sortDirection);
            else
                tmpWatchComments = watchedMIDs;
            for (String MID : tmpWatchComments.keySet()) {
                WatchComment com = tmpWatchComments.get(MID);
                if(!showAll && !com.comment.startsWith(name))
                    continue;
                
                m_botAction.sendSmartPrivateMessage(name, com.date + " MID:  " + Tools.formatString(MID, 19) + "  ( " + com.comment + " )");
                nothingFound = false;
            }
            if(nothingFound)
                m_botAction.sendSmartPrivateMessage(name, "MID:  (none)");
        }
        
        nothingFound = true;
        
        if (watchedNames.size() == 0) {
            m_botAction.sendSmartPrivateMessage(name, "Name: (none)");
        } else {
            if(sortBy != SortField.NONE) 
                tmpWatchComments = WatchListSorter.sortByValue(watchedNames, sortBy, sortDirection);
            else
                tmpWatchComments = watchedNames;
            for (String Name : tmpWatchComments.keySet()) {
                WatchComment com = tmpWatchComments.get(Name);
                if(!showAll && !com.comment.startsWith(name))
                    continue;
                
                m_botAction.sendSmartPrivateMessage(name, com.date + " Name: " + Tools.formatString(Name, 19) + "  ( " + com.comment + " )");
                nothingFound = false;
            }
            if(nothingFound)
                m_botAction.sendSmartPrivateMessage(name, "Name: (none)");
        }
    }
    
    public void handleEvent(InterProcessEvent event) {
        // If the event.getObject() is anything else then the IPCMessage (pubbotchatIPC f.ex) then return
        if (event.getObject() instanceof IPCMessage == false) {
            return;
        }

        IPCMessage ipcMessage = (IPCMessage) event.getObject();
        String botName = m_botAction.getBotName();
        String message = ipcMessage.getMessage();

        try {
            if (botName.equals(ipcMessage.getRecipient())) {
                if (message.startsWith("info "))
                    gotRecord(message.substring(5));
            }
        } catch (Exception e) {
            m_botAction.sendChatMessage(e.getMessage());
        }
    }
    

    private void gotRecord(String argString) {

        StringTokenizer recordArgs = new StringTokenizer(argString, ":");
        if (recordArgs.countTokens() != 3)
            throw new IllegalArgumentException("ERROR: Could not write player information.");
        String playerName = recordArgs.nextToken();
        String playerIP = recordArgs.nextToken();
        String playerMacID = recordArgs.nextToken();

        checkName(playerName, playerIP, playerMacID);
        checkIP(playerName, playerIP, playerMacID);
        checkMID(playerName, playerIP, playerMacID);		
	}

	private void saveWatches() {
        BotSettings cfg = m_botAction.getBotSettings();
        boolean loop = true;
        int i = 1;

        // Save IP watches
        for (String IP : watchedIPs.keySet()) {
            WatchComment com = watchedIPs.get(IP);
            cfg.put("IPWatch" + i, com.date + ":" + IP + ":" + com.comment);
            i++;
        }
        // Clear any other still stored IP watches
        while (loop) {
            if (cfg.getString("IPWatch" + i) != null) {
                cfg.remove("IPWatch" + i);
                i++;
            } else {
                loop = false;
            }
        }

        i = 1;
        loop = true;

        // Save MID watches
        for (String MID : watchedMIDs.keySet()) {
            WatchComment com = watchedMIDs.get(MID);
            cfg.put("MIDWatch" + i, com.date + ":" + MID + ":" + com.comment);
            i++;
        }
        // Clear any other still stored MID watches
        while (loop) {
            if (cfg.getString("MIDWatch" + i) != null) {
                cfg.remove("MIDWatch" + i);
                i++;
            } else {
                loop = false;
            }
        }

        i = 1;
        loop = true;

        // Save Name watches
        for (String Name : watchedNames.keySet()) {
            WatchComment com = watchedNames.get(Name);
            cfg.put("NameWatch" + i, com.date + ":" + Name + ":" + com.comment);
            i++;
        }
        // Clear any other still stored Name watches
        while (loop) {
            if (cfg.getString("NameWatch" + i) != null) {
                cfg.remove("NameWatch" + i);
                i++;
            } else {
                loop = false;
            }
        }
        
        cfg.save();
    }
    
    private void loadWatches() {
        BotSettings cfg = m_botAction.getBotSettings();
        boolean loop = true;
        int i = 1;

        watchedIPs.clear();
        watchedMIDs.clear();
        watchedNames.clear();

        // Load the IP watches from the configuration
        while (loop) {
            String IPWatch = cfg.getString("IPWatch" + i);
            if (IPWatch != null && IPWatch.trim().length() > 0) {
                String[] IPWatchSplit = IPWatch.split(":", 3);
                if (IPWatchSplit.length == 3)       // Check for corrupted data
                    watchedIPs.put(IPWatchSplit[1], new WatchComment(IPWatchSplit[0], IPWatchSplit[2]));
                i++;
            } else {
                loop = false;
            }
        }

        // Load the MID watches from the configuration
        loop = true;
        i = 1;

        while (loop) {
            String MIDWatch = cfg.getString("MIDWatch" + i);
            if (MIDWatch != null && MIDWatch.trim().length() > 0) {
                String[] MIDWatchSplit = MIDWatch.split(":", 3);
                if (MIDWatchSplit.length == 3)       // Check for corrupted data
                    watchedMIDs.put(MIDWatchSplit[1], new WatchComment(MIDWatchSplit[0], MIDWatchSplit[2]));
                i++;
            } else {
                loop = false;
            }
        }

        // Load the Name watches from the configuration
        loop = true;
        i = 1;

        while (loop) {
            String NameWatch = cfg.getString("NameWatch" + i);
            if (NameWatch != null && NameWatch.trim().length() > 0) {
                String[] NameWatchSplit = NameWatch.split(":", 3);
                if (NameWatchSplit.length == 3)       // Check for corrupted data
                    watchedNames.put(NameWatchSplit[1], new WatchComment(NameWatchSplit[0], NameWatchSplit[2]));
                i++;
            } else {
                loop = false;
            }
        }
    }
    
    private class WatchComment {
        String comment;
        String date;

        public WatchComment(String date, String comment) {
            this.date = date;
            this.comment = comment;
        }

        public String toString() {
            return date + " " + comment;
        }
    }
    
    private static class WatchListSorter {

        /**
         * Sorts the given map according to the given parameters.
         * @param map The map that needs to be sorted. Must be of the type Map<{@link String}, {@link WatchComment}>
         * @param sortBy The field that is used to sort by.
         * @param direction True for ascending, false for descending.
         * @return
         */
        public static Map<String, WatchComment> sortByValue(Map<String, WatchComment> map, final SortField sortBy, final boolean direction) {
            // Create a list of all the entries in the given map.
            List<Map.Entry<String, WatchComment>> list = new LinkedList<Map.Entry<String, WatchComment>>(map.entrySet());

            // Sort the list according to the rules inside the compare function.
            Collections.sort( list, new Comparator<Map.Entry<String, WatchComment>>() {
                public int compare( Map.Entry<String, WatchComment> wc1, Map.Entry<String, WatchComment> wc2 ) {
                    switch(sortBy) {
                    case DATE:
                        return (wc1.getValue().date).compareTo( wc2.getValue().date ) * (direction?1:-1);
                    case ISSUER:
                        return (wc1.getValue().comment).compareTo( wc2.getValue().comment ) * (direction?1:-1);
                    case TRIGGER:
                        return (wc1.getKey().compareTo(wc2.getKey())* (direction?1:-1));
                    case NONE:
                    default:
                        return (direction?1:-1);
                    }
                }
            } );

            // Put the sorted list back into a map, and return the result.
            Map<String, WatchComment> result = new LinkedHashMap<String, WatchComment>();
            for (Map.Entry<String, WatchComment> entry : list) {
                result.put( entry.getKey(), entry.getValue() );
            }
            return result;
        }
    }
}

