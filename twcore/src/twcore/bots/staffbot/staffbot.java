package twcore.bots.staffbot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.Vector;

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

public class staffbot extends SubspaceBot {

    private ModuleHandler moduleHandler;

    OperatorList m_opList;
    BotAction m_botAction;
    BotSettings m_botSettings;
    TimerTask getLog;
    String fileUser = "";
    private final static int CHECK_LOG_DELAY = 2000;
    
    private HashMap<String, Long> energyTracker = new HashMap<String, Long>();
    private Vector<EnergyCheck> energyChecks = new Vector<EnergyCheck>();
    private boolean isCheckingEnergy = false;

    /* Initialization code */
    public staffbot(BotAction botAction) {
        super(botAction);

        moduleHandler = new ModuleHandler(botAction, botAction.getGeneralSettings().getString("Core Location") + "/twcore/bots/staffbot", "staffbot");

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
        // join arena
        m_botAction.joinArena(m_botSettings.getString("InitialArena"));

        // join chats
        // 1 = staff chat
        // 2 = smod chat
        String staffchat = m_botAction.getGeneralSettings().getString("Staff Chat");
        String smodchat = m_botAction.getGeneralSettings().getString("Smod Chat");
        m_botAction.sendUnfilteredPublicMessage("?chat=" + staffchat + "," + smodchat + ",robodev,banmods");

        // load modules
        moduleHandler.loadModule("_serverwarningecho");
        //moduleHandler.loadModule("_racismignore");
        moduleHandler.loadModule("_warnings");
        moduleHandler.loadModule("_badcommand_savelog");
        moduleHandler.loadModule("_banc");
        moduleHandler.loadModule("_staffchat_savelog");
        //moduleHandler.loadModule("_commands");
        moduleHandler.loadModule("_loginwatch");
        moduleHandler.loadModule("_obscene");

        // start the log checking timer task for all modules

        // TimerTask to check the logs for *commands
        getLog = new TimerTask() {
            public void run() {
                m_botAction.sendUnfilteredPublicMessage("*log");
            }
        };
        m_botAction.scheduleTaskAtFixedRate(getLog, 0, CHECK_LOG_DELAY);

    }

    @Override
    public void handleEvent(Message event) {

        if ((event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) && event.getMessage().startsWith("!")) {
            // Commands
            String message = event.getMessage().toLowerCase();
            short sender = event.getPlayerID();
            String senderName = event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ? event.getMessager() : m_botAction.getPlayerName(sender);
            int operatorLevel = m_botAction.getOperatorList().getAccessLevel(senderName);

            // Ignore player's commands
            if (operatorLevel == OperatorList.PLAYER_LEVEL) {
                return;
            }

            // !help
            if (message.startsWith("!help")) {
                String[] help = { " Op: " + senderName + " (" + Tools.staffName(operatorLevel) + ")", "-----------------------[ Staff Bot ]-----------------------",
                        " !isStaff <name>           - Checks if <name> is a member of staff" };

                String[] smodHelp = { " !die                      - Disconnects Staffbot" };

                String[] sysopHelp = { " !listenergy               - Shows everyone who is currently using the !energy/*energy command." };
                String[] ownerHelp = { " !putfile                  - Uploads file to the zone (RARE CASE)" };

                m_botAction.smartPrivateMessageSpam(senderName, help);

                if (m_botAction.getOperatorList().isSmod(senderName)) {
                    m_botAction.smartPrivateMessageSpam(senderName, smodHelp);
                }
                if (m_botAction.getOperatorList().isSysop(senderName)) {
                    m_botAction.smartPrivateMessageSpam(senderName, sysopHelp);
                }
                if (m_botAction.getOperatorList().isOwner(senderName)) {
                    m_botAction.smartPrivateMessageSpam(senderName, ownerHelp);
                }
            }
            if (m_botAction.getOperatorList().isSmod(senderName)) {
                if (message.equals("!die")) {
                    moduleHandler.unloadAllModules();
                    this.handleDisconnect();
                    m_botAction.die("!die by " + senderName);
                } else if (message.equals("!energy")) {
                    cmd_energy(senderName);
                }
            }
            
            /* Disabling for now. Too unstable to properly test.
            if (m_botAction.getOperatorList().isSysop(senderName)) {
                if (message.equals("!listenergy")) {
                    cmd_listEnergy(senderName);
                }
            } */
            if (m_botAction.getOperatorList().isOwner(senderName)) {
                if (message.startsWith("!putfile ")) {
                    String msg = message.substring(9);
                    m_botAction.putFile(msg);
                    fileUser = senderName;
                    m_botAction.sendSmartPrivateMessage(senderName, "Putting file " + msg + "..." );
                } else if (message.startsWith("!getfile ")) {
                    String msg = message.substring(9);
                    m_botAction.getServerFile(msg);
                    fileUser = senderName;
                    m_botAction.sendSmartPrivateMessage(senderName, "Getting file " + msg + "..." );
                }
            }

            if (message.toLowerCase().startsWith("!isstaff")) {
                String[] parse = message.split(" ", 2);
                if (parse.length == 2) {
                    int accessLevel = m_botAction.getOperatorList().getAccessLevel(parse[1]);
                    if (accessLevel == 0) {
                        m_botAction.sendSmartPrivateMessage(senderName, "'" + parse[1] + "' is not a member of staff, or the name was not found (use exact case, i.e., 'DoCk>').");
                    } else {
                        if (m_botAction.getOperatorList().isHighmod(senderName)) {
                            m_botAction.sendSmartPrivateMessage(senderName, "'" + parse[1] + "' is staff: " + m_botAction.getOperatorList().getAccessLevelName(accessLevel));
                        } else {
                            m_botAction.sendSmartPrivateMessage(senderName, "'" + parse[1] + "' is a member of staff.");
                        }

                    }
                }
            }
        } else if (event.getMessageType() == Message.ARENA_MESSAGE && !fileUser.equals("")) {
        	if (event.getMessage().startsWith("File received:")) {
        		m_botAction.sendSmartPrivateMessage(fileUser, event.getMessage());
        		fileUser = "";
        	} else if(event.getMessage().contains("> ENERGY VIEWING TURNED ON/OFF")) {
                // Example: Thu Mar 20 04:23:52:  Beasty> ENERGY VIEWING TURNED ON/OFF
        	    
        	    // Disabling for now. Too unstable to properly test.
        	    // Fetch the name.
        	    //String name = event.getMessage().substring(22, event.getMessage().indexOf(">")).trim();
        	    // energyCheck(name);
        	}
        } else if ((event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) && event.getMessage().startsWith("TIME:")) {

            // Disabling for now. Too unstable to properly test.
            /*
            // Commands
            String message = event.getMessage().toLowerCase();
            short sender = event.getPlayerID();
            String senderName = event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ? event.getMessager() : m_botAction.getPlayerName(sender);

            if (senderName.equalsIgnoreCase("TWDBot") && m_botAction.getOperatorList().isSysop(senderName)) {
                energyResponse(message);
            }*/
        }
        
        moduleHandler.handleEvent(event);
    }
    
    /**
     * Handles the !energy command (SMod+)
     * Additionally reports the status.
     * @param name Person who issued the command.
     */
    private void cmd_energy(String name) {
        m_botAction.sendUnfilteredPrivateMessage(name, "*energy");
        m_botAction.sendPrivateMessage(name, "Done. Do not abuse this.");
        
        // Disabling for now, too unstable to properly test.
        //energyCheck(name);
    }
    
    /**
     * Handles the !listenergy command (Sysop+)
     * Updates the list, before spamming it to the requester.
     * @param name Issuer of the command.
     */
    @SuppressWarnings("unused")
    private void cmd_listEnergy(String name) {
        if(energyTracker == null || energyTracker.isEmpty()) {
            m_botAction.sendSmartPrivateMessage(name, "No one is currently using the energy command.");
            return;
        }
        
        m_botAction.sendSmartPrivateMessage(name, "Please hold... Updating the status of the list.");
        Long time = System.currentTimeMillis();
        Iterator<String> it = energyTracker.keySet().iterator();
        while(it.hasNext()) {
            energyChecks.add(new EnergyCheck(it.next(), time, true));
        }
        
        if(!isCheckingEnergy) {
            isCheckingEnergy = true;
            debug("[ENERGY] Sending !usage " + energyChecks.firstElement().name);
            m_botAction.sendSmartPrivateMessage("TWDBot", "!usage " + energyChecks.firstElement().name);
        }
        
        SendResponse ttSendResponse = new SendResponse(name);
        m_botAction.scheduleTask(ttSendResponse, 0, Tools.TimeInMillis.SECOND);
    }

    /**
     * Adds a user to the list of energy checks that need to be done.
     * @param name
     */
    @SuppressWarnings("unused")
    private void energyCheck(String name) {
        String lcName = name.toLowerCase();
        if(!energyTracker.containsKey(lcName)) {
            energyTracker.put(lcName, System.currentTimeMillis());
            m_botAction.sendSmartPrivateMessage("MessageBot", "!announce deans: [ENERGY] " + name + " has enabled energy tracking.");
            debug("[ENERGY] " + name + " has enabled energy tracking.");
        } else {
            energyChecks.add(new EnergyCheck(name, System.currentTimeMillis(), false));
            if(!isCheckingEnergy
                    || (!energyChecks.isEmpty()
                            && energyChecks.firstElement().time - System.currentTimeMillis() >= Tools.TimeInMillis.SECOND * 5)) {
                isCheckingEnergy = true;
                debug("[ENERGY] Sending !usage " + energyChecks.firstElement().name);
                m_botAction.sendSmartPrivateMessage("TWDBot", "!usage " + energyChecks.firstElement().name);
            }
        }
    }
    /**
     * Checks the energy status of a player for a few scenarios:
     * Player used the energy command and was already on the active list:
     * <ul>
     *  <li>If the player has relogged in the meantime, if so, status is again active.
     *  <li>If the player hasn't relogged in the meantime, the status is set to inactive.
     * </ul>
     * The list command was used:
     * <ul>
     *  <li>Player has relogged in the meantime, energy status is disabled.
     *  <li>Player hasn't relogged in the meantime, energy status is still active.
     * </ul>
     * @param message The message that is used to determine the time of the last relog.
     */
    @SuppressWarnings("unused")
    private void energyResponse(String message) {
        // A few possible scenarios:
        // 1. We are not checking and our checking list is empty.
        // 2. We are checking but our checking list is empty.
        // 3. We are not checking but our checking list is not empty.
        // 4. We are checking and our checking list is not empty.
        if(energyChecks == null || energyChecks.isEmpty()) {
            // Scenarios 1 and 2 - Ignore message.
            debug("[ENERGY] Scenario 1 or 2");
            if(isCheckingEnergy)
                isCheckingEnergy = false;
            return;
        } else if(!isCheckingEnergy) {
            // Scenario 3 - Send out a request.
            isCheckingEnergy = true;
            debug("[ENERGY] Scenario 3");
            debug("[ENERGY] Sending !usage " + energyChecks.firstElement().name);
            m_botAction.sendSmartPrivateMessage("TWDBot", "!usage " + energyChecks.firstElement().name);
            return;
        } else {
            // Scenario 4.
            debug("[ENERGY] Scenario 4");
            EnergyCheck ec = energyChecks.remove(0);
            String lcName = ec.name.toLowerCase();
            if(message.startsWith("TIME:")) {
                // User found.
                //     TWDBot> TIME: Session:    5:19:00  Total: 5495:50:00  Created: 10-14-2003 21:34:46
                int start = message.indexOf("Session:");
                int end = message.indexOf("Total:");
                if(start < 0 || end < 0) {
                    debug("Could not locate Session or Total.");
                }
                
                String[] splitTime = message.substring(start + 8, end).trim().split(":");
                long time = 0;
                int i = 0;
                try {
                    switch(splitTime.length) {
                    case 4:
                        time += Long.parseLong(splitTime[i++]) * Tools.TimeInMillis.DAY;
                    case 3:
                        time += Long.parseLong(splitTime[i++]) * Tools.TimeInMillis.HOUR;
                    case 2:
                        time += Long.parseLong(splitTime[i++]) * Tools.TimeInMillis.MINUTE;
                    case 1:
                    default:
                        time += Long.parseLong(splitTime[i++]) * Tools.TimeInMillis.SECOND;
                    }
                } catch (NumberFormatException nfe) {
                    // Do nothing. Worst case scenario, time = 0;
                }
                
                if(ec.silent) {
                    // Check is done upon the list command. In this case we need to check
                    // if the player relogged after the command was done and if so, remove from the list.
                    if(ec.time - time > energyTracker.get(lcName)) {
                        debug("[ENERGY] Removing " + lcName);
                        energyTracker.remove(lcName);
                    }
                } else {
                    // Check is done upon the energy command.
                    if(System.currentTimeMillis() - time > energyTracker.get(lcName)) {
                        // User has relogged.
                        energyTracker.put(lcName, ec.time);
                        m_botAction.sendSmartPrivateMessage("MessageBot", "!announce deans: [ENERGY] " + ec.name + " has enabled energy tracking.");
                        debug("[ENERGY] " + ec.name + " has enabled energy tracking.");
                    } else {
                        // User has disabled energy.
                        energyTracker.remove(lcName);
                        m_botAction.sendSmartPrivateMessage("MessageBot", "!announce deans: [ENERGY] " + ec.name + " has disabled energy tracking.");
                        debug("[ENERGY] " + ec.name + " has disabled energy tracking.");
                    }
                }
            } else if(message.startsWith("Could not locate")) {
                // User not found.
                energyTracker.remove(lcName);
                if(!ec.silent) {
                    m_botAction.sendSmartPrivateMessage("MessageBot", "!announce deans: [ENERGY] " + ec.name + " has used the energy command, but the new status could not be determined.");
                    debug("[ENERGY] " + ec.name + " has used the energy command, but the new status could not be determined.");
                }
            }
        }
        
        // Finally, check if a new check is to be made.
        if(energyChecks != null && !energyChecks.isEmpty()) {
            isCheckingEnergy = true;
            debug("[ENERGY] Sending !usage " + energyChecks.firstElement().name);
            m_botAction.sendSmartPrivateMessage("TWDBot", "!usage " + energyChecks.firstElement().name);
        } else {
            debug("[ENERGY] Disabling checks.");
            isCheckingEnergy = false;
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

    /**
     * Helper class for doing energy checks.
     * @author Trancid
     *
     */
    private class EnergyCheck {
        protected String name;
        protected long time;
        protected boolean silent;
        
        protected EnergyCheck(String name, long time, boolean silent) {
            this.name = name;
            this.time = time;
            this.silent = silent;
        }
    }
    
    private class SendResponse extends TimerTask {
        private String name;
        
        protected SendResponse(String name) {
            this.name = name;
        }
        
        @Override
        public void run() {
            debug("[ENERGY] ttSR running.");
            if(!isCheckingEnergy) {
                this.cancel();
            }
        }
        
        @Override
        public boolean cancel() {
            if(energyTracker == null || energyTracker.isEmpty()) {
                debug("[ENERGY] ttSR: no one using command.");
                m_botAction.sendSmartPrivateMessage(name, "No one is currently using the energy command.");
            } else {
                debug("[ENERGY] ttSR: Listing users.");
                m_botAction.sendSmartPrivateMessage(name, "The following users have the energy command active:");
                Iterator<String> it = energyTracker.keySet().iterator();
                while(it.hasNext()) {
                    m_botAction.sendSmartPrivateMessage(name, it.next());
                }
            }
            return super.cancel();
        }
    }
    
    private void debug(String message) {
        m_botAction.sendSmartPrivateMessage("ThePAP", message);
    }
}