package twcore.bots.staffbot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import twcore.bots.Module;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.ipc.IPCMessage;

/**
 * This module reports bad commands (and player kicks for message flooding) that are shown on the *log
 * This module also logs all *log lines to a file. The functionality is copied from the mrarrogant bot.
 * 
 * @author MMaverick
 */
public class staffbot_loginwatch extends Module {
	
    private Map<String, String> watchedIPs;
    private Map<String, String> watchedNames;
    private Map<String, String> watchedMIDs;

	@Override
	public void initializeModule() {
        watchedIPs = Collections.synchronizedMap(new HashMap<String, String>());
        watchedNames = Collections.synchronizedMap(new HashMap<String, String>());
        watchedMIDs = Collections.synchronizedMap(new HashMap<String, String>());
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
                " !clearMIDWatch                 - Clears all login watches for MIDs", " !showWatches                   - Shows all current login watches"

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
                doShowWatchesCmd();
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
            m_botAction.sendChatMessage(4,"           " + watchedNames.get(name.toLowerCase()));
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
                m_botAction.sendChatMessage(4,"         " + watchedIPs.get(IPfragment));
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
            m_botAction.sendChatMessage(4,"          " + watchedMIDs.get(MacID));
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
            watchedNames.put(name.toLowerCase(), sender + ": " + comment);
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
            watchedMIDs.put(MID, sender + ": " + comment);
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
            watchedIPs.put(IP, sender + ": " + comment);
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
     * Shows current watches.
     */
    public void doShowWatchesCmd() {
        if (watchedIPs.size() == 0) {
            m_botAction.sendChatMessage(4,"IP:   (none)");
        }
        for (String IP : watchedIPs.keySet()) {
            m_botAction.sendChatMessage(4,"IP:   " + IP + "  ( " + watchedIPs.get(IP) + " )");
        }

        if (watchedMIDs.size() == 0) {
            m_botAction.sendChatMessage(4,"MID:  (none)");
        }
        for (String MID : watchedMIDs.keySet()) {
            m_botAction.sendChatMessage(4,"MID:  " + MID + "  ( " + watchedMIDs.get(MID) + " )");
        }

        if (watchedNames.size() == 0) {
            m_botAction.sendChatMessage(4,"Name: (none)");
        }
        for (String Name : watchedNames.keySet()) {
            m_botAction.sendChatMessage(4,"Name: " + Name + "  ( " + watchedNames.get(Name) + " )");
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
        //checkLName(playerName, playerIP, playerMacID);
        //checkRName(playerName, playerIP, playerMacID);
        //checkPName(playerName, playerIP, playerMacID);
		
	}

	private void saveWatches() {
        BotSettings cfg = m_botAction.getBotSettings();
        boolean loop = true;
        int i = 1;

        // Save IP watches
        for (String IP : watchedIPs.keySet()) {
            cfg.put("IPWatch" + i, IP + ":" + watchedIPs.get(IP));
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
            cfg.put("MIDWatch" + i, MID + ":" + watchedMIDs.get(MID));
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
            cfg.put("NameWatch" + i, Name + ":" + watchedNames.get(Name));
            i++;
        }
        // Clear any other still stored MID watches
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
                String[] IPWatchSplit = IPWatch.split(":", 2);
                if (IPWatchSplit.length == 2)       // Check for corrupted data
                    watchedIPs.put(IPWatchSplit[0], IPWatchSplit[1]);
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
                String[] MIDWatchSplit = MIDWatch.split(":", 2);
                if (MIDWatchSplit.length == 2)       // Check for corrupted data
                    watchedMIDs.put(MIDWatchSplit[0], MIDWatchSplit[1]);
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
                String[] NameWatchSplit = NameWatch.split(":", 2);
                if (NameWatchSplit.length == 2)       // Check for corrupted data
                    watchedNames.put(NameWatchSplit[0], NameWatchSplit[1]);
                i++;
            } else {
                loop = false;
            }
        }

        // Done loading watches
    }
}