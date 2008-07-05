package twcore.bots.pubhub;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import twcore.bots.ModuleHandler;
import twcore.bots.PubBotModule;
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
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.Prize;
import twcore.core.events.ScoreReset;
import twcore.core.events.ScoreUpdate;
import twcore.core.events.SoccerGoal;
import twcore.core.events.WatchDamage;
import twcore.core.events.WeaponFired;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

public class pubhub extends SubspaceBot {
    public static final String IPCCHANNEL = "pubBots";
    public static final String IPCCHAT = "pubbotChat";
    public static final String IPCSILENCE = "pubbotsilence";
    public static final String IPCPUBSTATS = "pubstats";

    // Configuration
    private ModuleHandler moduleHandler;
    private OperatorList opList;
    private String cfg_hubbot;
    private String cfg_chat;
    private String pubhub;
    private HashSet<String> cfg_arenas = new HashSet<String>();
    private HashSet<String> cfg_autoloadModules = new HashSet<String>();
    private HashMap<String, HashSet<String>> cfg_arenaModules = new HashMap<String, HashSet<String>>();
    private HashSet<String> cfg_access = new HashSet<String>();

    private static final int CHECKARENALIST_DELAY = 60 * 1000;  // How often the arena list is checked (60 seconds)
    private static final int CHECKPUBBOTSARENA_DELAY = 30 * 1000; // How often the location of the pubbots are checked
    private static final int SPAWN_GO_DELAY = 1*1000;           // How long after a bot has spawned it is send to the correct arena (1 second)
    public static final int LOG_OFF_DELAY = 5*1000;             // How long after issuing the !off command, this bot is disconnected (5 seconds)

    private ArenaListTask arenaListTask = new ArenaListTask();
    private PubbotsLocationTask pubbotsLocationTask = new PubbotsLocationTask();
    private LogOffTask logoffTask = new LogOffTask();

    private ConcurrentHashMap<String, String> pubbots;
                 //<PubBot, Arena>
                 // Arena = lowercase

    /**
     * This method initializes the bot.
     *
     * @param botAction is the BotAction object of the bot.
     */
    public pubhub(BotAction botAction) {
        super(botAction);
        moduleHandler = new ModuleHandler(m_botAction, m_botAction.getGeneralSettings().getString("Core Location") + "/twcore/bots/pubhub", "pubhub");
        pubbots = new ConcurrentHashMap<String,String>();
    }

    @Override
    public void handleDisconnect() {
        m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("die"));
        moduleHandler.unloadAllModules();
        m_botAction.cancelTasks();
        m_botAction.ipcDestroyChannel(IPCCHANNEL);
        m_botAction.ipcDestroyChannel(IPCCHAT);
        m_botAction.ipcDestroyChannel(IPCSILENCE);
        m_botAction.ipcDestroyChannel(IPCPUBSTATS);
    }

    /**
     * This method handles a LoggedOn event.
     *
     * @param event is the message event to handle.
     */
    public void handleEvent(LoggedOn event) {
        loadConfiguration();

        BotSettings botSettings = m_botAction.getBotSettings();
        opList = m_botAction.getOperatorList();
        pubhub = m_botAction.getBotName();

        // Change to the configured arena
        m_botAction.changeArena(botSettings.getString("InitialArena"));

        // Join IPC channels
        m_botAction.ipcSubscribe(IPCCHANNEL);
        m_botAction.ipcSubscribe(IPCCHAT);
        m_botAction.ipcSubscribe(IPCSILENCE);
        m_botAction.ipcSubscribe(IPCPUBSTATS);
        // Join chat
        m_botAction.sendUnfilteredPublicMessage("?chat=" + cfg_chat );

        // Request events
        EventRequester eventRequester = m_botAction.getEventRequester();
        eventRequester.request(EventRequester.ARENA_LIST);
        eventRequester.request(EventRequester.MESSAGE);

        // Start the task of getting the arena list by which pubbots will be spawned
        m_botAction.scheduleTaskAtFixedRate(arenaListTask, 1000, CHECKARENALIST_DELAY);
        m_botAction.scheduleTaskAtFixedRate(pubbotsLocationTask, CHECKPUBBOTSARENA_DELAY, CHECKPUBBOTSARENA_DELAY);
    }

    /**
     *
     * @param event is the ArenaList event.
     */
    public void handleEvent(ArenaList event) {
        String[] arenas = event.getArenaNames();
        boolean startup = pubbots.size()==0;

        if(countUnspawnedArenas() > 0) {
            // Spawn the pubbot
            m_botAction.sendSmartPrivateMessage(cfg_hubbot, "!spawn PubBot");
        }
        
        for (int i = 0; i < arenas.length; i++) {
            String arena = arenas[i].toLowerCase();

            if( Tools.isAllDigits(arena) || cfg_arenas.contains(arena)) {
                if(!pubbots.containsValue(arena)) {
                    String key = "SPAWNING"+(countUnspawnedArenas()+1);
                    pubbots.put(key, arena);
                }
            }
        }
        
        // Only spawn a pubbot after the arena check when bot is starting up
        if(startup && countUnspawnedArenas() > 0) {
            // Spawn the pubbot
            m_botAction.sendSmartPrivateMessage(cfg_hubbot, "!spawn PubBot");
        }

        moduleHandler.handleEvent(event);
    }

    /**
     * This method handles a message event.
     * @param event is the message event to handle.
     */
    public void handleEvent(Message event) {
        String message = event.getMessage();
        int messageType = event.getMessageType();
        String sender = event.getMessager();

        if(sender == null)  // try looking up the sender's name by playerid
            sender = m_botAction.getPlayerName(event.getPlayerID());
        if(sender == null)
            sender = "-";

        // Message from the hubbot
        if(sender.equalsIgnoreCase(cfg_hubbot)) {
            if (message.startsWith("Maximum number of bots of this type ") || message.equals("Bot failed to log in.")) {
                String arena = disableUnspawnedArena();
                m_botAction.sendChatMessage("Unable to spawn pubbot in arena '"+arena+"'.");
            }
        }

        // Chat commands
        if (messageType == Message.CHAT_MESSAGE &&
            (opList.isSmod(sender) || cfg_access.contains(sender.toLowerCase()))
            ) {
            if (message.equalsIgnoreCase("!respawn")) {
                m_botAction.sendChatMessage("Respawning pub bots.");
                m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("die"));
                
                // Restart respawning pubbots after 10 seconds
                m_botAction.cancelTask(arenaListTask);
                arenaListTask = new ArenaListTask();
                m_botAction.scheduleTaskAtFixedRate(arenaListTask, 10000, CHECKARENALIST_DELAY);
            }
            if (message.equalsIgnoreCase("!reloadconfig")) {
                loadConfiguration();
                m_botAction.sendChatMessage("Configuration reloaded.");
            }
            if (message.equalsIgnoreCase("!where")) {
                Vector<String> botNames = new Vector<String>(pubbots.keySet());
                Collections.sort(botNames);

                for (int index = 0; index < botNames.size(); index++) {
                    String name = botNames.get(index);
                    String arena = pubbots.get(name);

                    if(Tools.isAllDigits(arena)) {
                        m_botAction.sendChatMessage(" "+name+": (Public "+arena+")");
                    } else {
                        m_botAction.sendChatMessage(" "+name+": "+arena);
                    }

                }
            }
            if (message.equalsIgnoreCase("!listmodules")) {
                Vector<String> moduleNames = new Vector<String>(moduleHandler.getModuleNames());
                String moduleName;

                if (moduleNames.isEmpty())
                    throw new RuntimeException("ERROR: There are no modules available to this bot.");

                m_botAction.sendSmartPrivateMessage(sender, "A * indicates a loaded module.");
                for (int index = 0; index < moduleNames.size(); index++) {
                    moduleName = moduleNames.get(index);
                    if (moduleHandler.isLoaded(moduleName))
                        moduleName.concat(" *");
                    m_botAction.sendSmartPrivateMessage(sender, moduleName);
                }
            }
            if (message.equalsIgnoreCase("!off")) {
                m_botAction.sendChatMessage("Disconnecting pubhub and pubbots...");
                m_botAction.cancelTasks();

                m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("die"));
                moduleHandler.unloadAllModules();
                m_botAction.scheduleTask(logoffTask, LOG_OFF_DELAY);
            }
            if (message.equalsIgnoreCase("!help")) {
                m_botAction.sendSmartPrivateMessage(sender, "PUBHUB CHAT COMMANDS:");
                m_botAction.sendSmartPrivateMessage(sender, "!respawn      - Respawns all PubBots.");
                m_botAction.sendSmartPrivateMessage(sender, "!where        - Shows location of all PubBots.");
                m_botAction.sendSmartPrivateMessage(sender, "!listmodules  - Gives a list of pub modules available.");
                m_botAction.sendSmartPrivateMessage(sender, "!reloadconfig - Reloads pubhub configuration from .cfg file.");
                m_botAction.sendSmartPrivateMessage(sender, "!off          - Disconnects the pubhub and pubbots.");
            }
        }
        
        // Messages from the *locate commands
        if(messageType == Message.ARENA_MESSAGE) {
            // Use a regular expression to determine if this arena message is the result of the *locate command
            // If yes, then the result of this regular expression can also be used to get the player and arena name 
            // from the arena message.
            // The regular expression without java-specific escapes = (.+)\s-\s([#\w\d\p{Z}]+)
            // Small explanation:                                     [name] - [arena]
            
            Pattern p = Pattern.compile("(.+)\\s-\\s([#\\w\\d\\p{Z}]+)");
            Matcher m = p.matcher(message);
            
            if(m.matches()) {
                String name = m.group(1);
                String arena = m.group(2);
                
                if(name != null && arena != null) {
                    
                    if(arena.startsWith("Public ")) {
                        arena = arena.replaceAll("Public ", "");
                    }
                    
                    if(pubbots.containsKey(name)) {
                        pubbots.put(name, arena.toLowerCase());
                        
                        // let the pubbot know it's (new?) location
                        m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("location "+arena, name));
                        
                        checkPubbots();
                    }
                }
                
            }
        }

        moduleHandler.handleEvent(event);
    }


    /**
     * This method handles an InterProcessEvent.
     * @param event is the InterProcessEvent to handle.
     */
    public void handleEvent(InterProcessEvent event) {
        // Only handle the IPC messages containing the IPCMessage object

        if (event.getObject() instanceof IPCMessage) {
            IPCMessage ipcMessage = (IPCMessage) event.getObject();
            String message = ipcMessage.getMessage();
            String recipient = ipcMessage.getRecipient();
            String botSender = event.getSenderName();

            if (recipient == null || recipient.equals(pubhub)) {
                if (message.equalsIgnoreCase("dying")) {       // A pubbot is going away
                    pubbots.remove(botSender);
                }
                if (message.equalsIgnoreCase("spawned")) {     // A pubbot has spawned
                    m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("initialize", botSender));
                    m_botAction.scheduleTask(new SpawnGoTask(botSender), SPAWN_GO_DELAY);

                    // After a pubbot has been spawned, recheck arena list to spawn another one
                    // do this by rescheduling the arenaListTask to prevent double pubbot spawns
                    m_botAction.cancelTask(arenaListTask);
                    arenaListTask = new ArenaListTask();
                    m_botAction.scheduleTaskAtFixedRate(arenaListTask, 5000, CHECKARENALIST_DELAY);
                }
                if (message.equalsIgnoreCase("arrivedarena")) {// A pubbot has arrived at the arena
                    sendAutoLoadModules(botSender);
                }
            }

        }

        moduleHandler.handleEvent(event);
    }

    /**
     *
     */
    private void loadConfiguration() {
        BotSettings botSettings = m_botAction.getBotSettings();
        cfg_hubbot = m_botAction.getGeneralSettings().getString("Main Login");
        cfg_chat =   botSettings.getString("chat");

        cfg_arenas.clear();
        cfg_autoloadModules.clear();
        cfg_arenaModules.clear();
        cfg_access.clear();

        // AutoloadArenas
        StringTokenizer arenas = new StringTokenizer(botSettings.getString("AutoloadArenas"));
        while (arenas.hasMoreTokens()) {
            String arena = arenas.nextToken().toLowerCase();
            cfg_arenas.add(arena);
        }

        // AutoloadModules
        StringTokenizer modules = new StringTokenizer(botSettings.getString("AutoloadModules"));
        while (modules.hasMoreTokens()) {
            String module = modules.nextToken().toLowerCase();
            if (moduleHandler.isModule(module))
                cfg_autoloadModules.add(module);
        }

        // Modules-<arena>
        for (String arena : cfg_arenas) {
            String modulesSetting = botSettings.getString("Modules-" + arena);

            if (modulesSetting != null && modulesSetting.length() > 0) {
                StringTokenizer moduless = new StringTokenizer(modulesSetting);

                // Cycle through the modules and add each module to the HashMap, the arena as key
                while (moduless.hasMoreTokens()) {
                    String module = moduless.nextToken().toLowerCase();
                    if (moduleHandler.isModule(module)) {
                        HashSet<String> moduleSet;

                        if (cfg_arenaModules.containsKey(arena)) {
                            moduleSet = cfg_arenaModules.get(arena);
                        } else {
                            moduleSet = new HashSet<String>();
                        }
                        moduleSet.add(module);
                        cfg_arenaModules.put(arena, moduleSet);
                    }
                }
            }
        }

        // Access
        StringTokenizer accessUsers = new StringTokenizer(botSettings.getString("Access"), ":");
        while (accessUsers.hasMoreTokens()) {
            String name = accessUsers.nextToken().toLowerCase();
            cfg_access.add(name);
        }
    }

    /**
     * Counts all the keys in the HashMap pubbots that start with "SPAWNING"
     * All keys starting with "SPAWNING" means there is an arena without a pubbot spawned in there.
     *
     * @return the number of known arenas without a pubbot
     */
    private int countUnspawnedArenas() {
        int count = 0;
        for(String bot:pubbots.keySet()) {
            if(bot.startsWith("SPAWNING")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Removes the specified arena from the pubbots list
     *
     * @param arena the arena to be removed
     * @return the arena which has been removed or null if not found
     */
    private String removeUnspawnedArena(String arena) {
        String removedArena = null;

        for(String bot:pubbots.keySet()) {
            String aarena = pubbots.get(bot);

            if(bot.startsWith("SPAWNING") && aarena.equals(arena)) {
                removedArena = pubbots.remove(bot);
                break;
            }
        }

        return removedArena;
    }

    private String disableUnspawnedArena() {
        String removedArena = null;

        for(String bot:pubbots.keySet()) {
            String arena = pubbots.get(bot);

            if(bot.startsWith("SPAWNING")) {
                pubbots.remove(bot);
                pubbots.put("DISABLED"+bot.substring(1), arena);
                removedArena = arena;
                break;
            }
        }

        return removedArena;
    }

    /**
     */
    private String getUnspawnedArena() {
        String arena = null;

        for(String bot:pubbots.keySet()) {
            if(bot.startsWith("SPAWNING")) {
                arena = pubbots.get(bot);
            }
        }

        return arena;
    }

    /**
     * This method sends all of the appropriate IPC messages required to autoload all of the modules.
     *
     * @param pubBot is the name of the bot to send the messages to.
     */
    private void sendAutoLoadModules(String pubBot) {
        String arena = pubbots.get(pubBot);
        HashSet<String> modules;

        if (arena != null) {
            if (cfg_arenaModules.containsKey(arena)) { // Autoload predefined specific modules
                modules = cfg_arenaModules.get(arena);
            } else { // Autoload default modules
                modules = cfg_autoloadModules;
            }
        } else {
            modules = cfg_autoloadModules;
        }

        // Load the Pubhub modules (if not loaded) and send the IPC Messages for loading the modules to the Pubbot
        for (String module:modules) {
            if (!moduleHandler.isLoaded(module)) {
                moduleHandler.loadModule(module);
                PubBotModule pubhubModule = (PubBotModule) moduleHandler.getModule(module);
                pubhubModule.initializeModule(IPCCHANNEL, pubhub);
            }

            // load the module on the pubbot
            m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("load " + module, pubBot));
        }
    }
    
    
    private void checkPubbots() {

        // Check for double pubbots in arena
        synchronized( pubbots ) {
            
            Set<Map.Entry <String,String>> bots1 = pubbots.entrySet();
            Set<Map.Entry <String,String>> bots2 = pubbots.entrySet();
                        //<Pubbot, Arena>
                        // key   , value
            
            for(Map.Entry<String, String> bot1:bots1) {
                for(Map.Entry<String, String> bot2:bots2) {
                    
                    // if the bot names are different but both are in the same arena
                    if(bot1.getKey().equalsIgnoreCase(bot2.getKey())== false && bot1.getValue().equalsIgnoreCase(bot2.getValue())) {
                        
                        killPubbot(bot1.getKey());
                        bots1.remove(bot1);
                        bots2.remove(bot1);
                        break;
                    }
                }
            }
        }
        
        // Check if a pubbot is not in the configured public arena
        synchronized( pubbots ) {
            
            for(Map.Entry<String,String> pubbot:pubbots.entrySet()) {
                String bot = pubbot.getKey();
                String arena = pubbot.getValue();
                
                if( Tools.isAllDigits(arena) == false && cfg_arenas.contains(arena) == false) {
                    // This pubbot is in a wrong arena, disconnect it
                    killPubbot(bot);
                }
            }
        }
    }
    
    /**
     * Issues the IPC message to disconnect the specified bot
     * 
     * @param bot
     */
    private void killPubbot(String bot) {
        m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("die", bot));
    }
    

    //************ TIMERTASKS ****************\\

    private class PubbotsLocationTask extends TimerTask {
        public void run() {
            
            synchronized( pubbots ) {
                // *locate all the registered pubbots
                for(String pubbot:pubbots.keySet()) {
                    if(pubbot.startsWith("SPAWNING") == false)
                        m_botAction.locatePlayer(pubbot);
                }
            }
        }
    }

    private class ArenaListTask extends TimerTask {
        public void run() {
            m_botAction.requestArenaList();
        }
    }

    /**
     * This class moves a newly spawned bot to where it needs to go.
     */
    private class SpawnGoTask extends TimerTask {
        private String pubBot;

        public SpawnGoTask(String pubBot) {
            this.pubBot = pubBot;
        }

        public void run() {
            String destinationArena = getUnspawnedArena();

            if (destinationArena == null) {
                // Kill the pubbot if no arena is found
                killPubbot(pubBot);
            } else {
                m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("joinchat " + cfg_chat, pubBot));
                m_botAction.ipcTransmit(IPCCHANNEL, new IPCMessage("go " + destinationArena, pubBot));

                removeUnspawnedArena(destinationArena);
                pubbots.put(pubBot, destinationArena);
                this.cancel();
            }
        }
    }

    /**
     * This class logs the bot off of the server. It is used to put a bit of
     * delay between the last command and the die command.
     */
    private class LogOffTask extends TimerTask {
        public void run() {
            m_botAction.ipcDestroyChannel(IPCCHANNEL);
            m_botAction.ipcDestroyChannel(IPCCHAT);
            m_botAction.ipcDestroyChannel(IPCSILENCE);
            m_botAction.ipcDestroyChannel(IPCPUBSTATS);
            m_botAction.die();
        }

    }






    public void handleEvent(PlayerEntered event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(PlayerPosition event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(PlayerLeft event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(PlayerDeath event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(Prize event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(ScoreUpdate event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(WeaponFired event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(FrequencyChange event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(FrequencyShipChange event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(FileArrived event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(ArenaJoined event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(FlagVictory event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(FlagReward event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(ScoreReset event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(WatchDamage event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(SoccerGoal event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(BallPosition event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(FlagPosition event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(FlagDropped event) {
        moduleHandler.handleEvent(event);
    }

    public void handleEvent(FlagClaimed event) {
        moduleHandler.handleEvent(event);
    }
}
