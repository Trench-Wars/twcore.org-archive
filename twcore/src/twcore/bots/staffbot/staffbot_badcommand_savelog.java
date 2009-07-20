package twcore.bots.staffbot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.Module;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.util.Tools;

/**
 * This module reports bad commands (and player kicks for message flooding) that are shown on the *log
 * This module also logs all *log lines to a file. The functionality is copied from the mrarrogant bot.
 * 
 * @author MMaverick
 */
public class staffbot_badcommand_savelog extends Module {
    
    public static final int CHECK_LOG_TIME = 30 * 1000;
    public static final int COMMAND_CLEAR_TIME = 60 * 60 * 1000;
    
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy EEE MMM dd HH:mm:ss", Locale.US);
    private SimpleDateFormat fileNameFormat;
    private Vector<CommandLog> commandQueue = new Vector<CommandLog>();
    private FileWriter logFile;
    private String logFileName;
    private Date lastLogDate;
    private int year;
    
    // TimerTasks
    private CheckLogTask checkLogTask = new CheckLogTask();
    
    @Override
    public void cancel() {
        try {
            logFile.close();
        } catch(IOException ioe) {}
        
        m_botAction.cancelTask(checkLogTask);
    }

    @Override
    public void initializeModule() {
        BotSettings botSettings = m_botAction.getBotSettings();
        fileNameFormat = new SimpleDateFormat("'" + botSettings.getString("logpath") + "/'ddMMMyyyy'.log'");
        logFileName = fileNameFormat.format(new Date());
        
        try {
            logFile = new FileWriter(new File(logFileName), true);
        } catch(IOException ioe) {
            m_botAction.sendChatMessage("Error while opening log file. Disabling module functionality.");
            Tools.printStackTrace("Error while opening log file. Disabling module functionality.", ioe);
            this.cancel();
        }
        
        m_botAction.scheduleTaskAtFixedRate(checkLogTask, 0, CHECK_LOG_TIME);
    }

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.MESSAGE);
	}
	
	@Override
	public void handleEvent(Message event) {
	    String message = event.getMessage();
	    String name = event.getMessager();
	    if(event.getMessager() == null) {
	        name = m_botAction.getPlayerName(event.getPlayerID());
	    }
	    
	    if(event.getMessageType() == Message.ARENA_MESSAGE &&
	            !message.startsWith("Ping:") &&         // Exclude all the info from *info
	            !message.startsWith("LOSS: S2C:") &&
	            !message.startsWith("S2C:") &&
	            !message.startsWith("C2S CURRENT: Slow:") &&
	            !message.startsWith("S2C CURRENT: Slow:") &&
	            !message.startsWith("TIME: Session:") &&
	            !message.startsWith("Bytes/Sec:") &&
	            message.length() > 20) {
	        
	        // Check if the log line is already processed by using the date at the beginning of the line
	        Date date = null;
	        try {
	            date = dateFormat.parse(year + " " + message.substring(0, 19));
	        } catch(ParseException pe) {}
	        
	        if(date != null) {
                if(lastLogDate == null)
                    lastLogDate = date;
                
                // Fri May 01 01:06:44:  Played kicked off for message flooding: nliE
                // Ext: MMaverick (#robopark): *arena jkshdfs

                if(date.after(lastLogDate)) {
                    String logmessage = message.substring(22);  // Remove the timestamp
                    
                    if(logmessage.startsWith("Ext: ")) {
                        handleLogCommand(date, logmessage.substring(5));  
                    }
                    if(logmessage.startsWith("Played kicked off for message flooding:")) {
                        m_botAction.sendChatMessage(2,logmessage.replaceFirst("Played", "Player")); // Fix typo
                    }
                    
                    writeLog(message, date);
                    lastLogDate = date;
                }
	        }
	    }
	    
	    if( event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ) {
	            
	        // SMod+ commands
            if (m_botAction.getOperatorList().isSmod(name)) {

                // !help
                if (message.toLowerCase().startsWith("!help")) {
                    final String[] helpSmod = {
                            "----------------------[ Log: SMod+ ]-----------------------",
                            " Saves commands up to " + ((COMMAND_CLEAR_TIME / 1000) / 60)
                                    + " minutes and watches for bad commands.",
                            " !log [time/minutes]           - Displays last commands of last specified minutes.",
                            " !logfrom <name>[:cmd][:time]  - Displays last commands from <name>.",
                            " !logto <name>[:cmd][:time]    - Displays last commands to <name>.",
                            };

                    m_botAction.smartPrivateMessageSpam(name, helpSmod);
                }

                // !log
                if (message.toLowerCase().startsWith("!log ") || message.equalsIgnoreCase("!log")) {
                    // !log [time/minutes] - Displays last commands of last
                    // specified minutes.
                    String arguments = message.substring(4).trim();

                    if (Tools.isAllDigits(arguments) == false) {
                        m_botAction.sendSmartPrivateMessage(name, "Syntax error. Please use the following format: !log <minutes>");
                        return;
                    }

                    int time = 10;

                    try {
                        time = Integer.parseInt(arguments);
                    } catch (NumberFormatException e) {
                    }

                    m_botAction.sendSmartPrivateMessage(name, "Command log of the past " + time + " minute(s):   ("+commandQueue.size()+" commands total)");
                    displayLog(name, "", "", "", time);
                }

                // !logfrom
                if (message.toLowerCase().startsWith("!logfrom ")) {
                    String arguments = message.substring(9).trim();
                    String[] argTokens = arguments.split(":");

                    if (argTokens.length < 1 || argTokens.length > 3) {
                        m_botAction.sendSmartPrivateMessage(name, "Please use the following format: !LogFrom <PlayerName>:<Command>:<Minutes>");
                        return;
                    }

                    String fromPlayer = argTokens[0];
                    String command = "";
                    int time = 10;

                    switch (argTokens.length) {
                        case 3:
                            command = argTokens[1];
                            time = Integer.parseInt(argTokens[2]);
                            break;
                        case 2:
                            if (argTokens[1].startsWith("*")) {
                                command = argTokens[1];
                            } else {
                                try {
                                    time = Integer.parseInt(argTokens[1]);
                                } catch(NumberFormatException e) {}
                            }
                            break;
                    }
                    if (!command.startsWith("*") && !command.equals("")) {
                        m_botAction.sendSmartPrivateMessage(name, "Please use the following format: !LogFrom <PlayerName>:<Command>:<Minutes>");
                        return;
                    }

                    displayLog(name,fromPlayer, "", command, time);
                }
                
                // !logto
                if (message.toLowerCase().startsWith("!logto ")) {
                    // !logto <name>[:cmd][:time] - Displays last commands to
                    // <name>.
                    String arguments = message.substring(7).trim();
                    String[] argTokens = arguments.split(":");

                    if (argTokens.length < 1 || argTokens.length > 3) {
                        m_botAction.sendSmartPrivateMessage(name, "Please use the following format: !LogTo <PlayerName>:<Command>:<Minutes>");
                        return;
                    }

                    String toPlayer = argTokens[0];
                    String command = "";
                    int time = 10;

                    switch (argTokens.length) {
                        case 3:
                            command = argTokens[1];
                            try {
                                time = Integer.parseInt(argTokens[2]);
                            } catch (NumberFormatException e) {}
                            break;
                        case 2:
                            if (argTokens[1].startsWith("*"))
                                command = argTokens[1];
                            else
                                time = Integer.parseInt(argTokens[1]);
                            break;
                    }
                    if (!command.startsWith("*") && !command.equals("")) {
                        m_botAction.sendSmartPrivateMessage(name, "Please use the following format: !LogTo <PlayerName>:<Command>:<Minutes>");
                        return;
                    }

                    displayLog(name, "", toPlayer, command, time);
                }
            }
	    }
	}
	
	private void displayLog(String commander, String fromPlayer, String toPlayer, String command, int time) {
        Date lastDate = new Date(this.lastLogDate.getTime() - time * 60 * 1000);
        int displayed = 0;

        for( CommandLog commandLog : commandQueue) {
            if (commandLog.isMatch(lastDate, fromPlayer, toPlayer, command)) {
                displayed++;
                m_botAction.sendSmartPrivateMessage(commander, commandLog.toString());
            }
        }

        if (displayed == 0)
            m_botAction.sendSmartPrivateMessage(commander, "No commands matching your search criteria were recorded.");
    }
	
	/**
	 * This method handles a log command.
	 *
	 * @param date is the date that the command was issued.
	 * @param command is the command that was issued.
	 */
	private void handleLogCommand(Date date, String logMessage) {
	    String arena = getArena(logMessage);
	    String fromPlayer = getFromPlayer(logMessage);
	    String toPlayer = getToPlayer(logMessage);
	    String command = getCommand(logMessage);
	    
	    CommandLog commandLog;

	    if (fromPlayer != null && command != null && opList.isER(fromPlayer)) {
	        commandLog = new CommandLog(date, arena, fromPlayer, toPlayer, command);
	      
	        if (isBadCommand(command)) {
	            m_botAction.sendChatMessage(2, "Illegal command: " + commandLog.toString());
	        }
	        commandQueue.add(commandLog);
	    }
	  }
	  
    /**
     * This method gets the sender of a command.
     *
     * @param message is the message to parse.
     * @return the sender of the command is returned.
     */
	private String getFromPlayer(String message) {
	    int endIndex = message.indexOf(" (");
	    if(endIndex == -1)
	        return null;
	    return message.substring(0, endIndex);
	}

	/**
	 * This method gets the player that the command is destined for.  If there is
	 * no target of the command, then an empty string is returned.
	 *
	 * @param message is the log message to parse.
	 * @return the name of the target of the command is returned.  If there is
	 * no target then an empty string is returned.
	 */
	private String getToPlayer(String message) {
	    int beginIndex = 0;
	    int endIndex;

	    for(;;) {
	      beginIndex = message.indexOf(") to ", beginIndex);
	      if(beginIndex == -1)
	        return "";
	      beginIndex += 5;
	      endIndex = message.indexOf(":", beginIndex);
	      if(endIndex != -1)
	        break;
	    }
	    return message.substring(beginIndex, endIndex);
	}
	

	/**
	 * This method returns the arena name from the log message.
	 *
	 * @param message is the log message to parse.
	 * @return the arena name is returned.
	 */
	public String getArena(String message) {
	    int beginIndex = message.indexOf(" (");
	    int endIndex;

	    if(beginIndex == -1)
	      return null;
	    beginIndex += 2;
	    endIndex = message.indexOf(")", beginIndex);
	    if(endIndex == -1)
	      return null;
	    return message.substring(beginIndex, endIndex);
	}

	private String getCommand(String message) {
	    int beginIndex = message.lastIndexOf(": *");

	    if(beginIndex == -1)
	      return null;
	    return message.substring(beginIndex + 2);
	}
    
	private boolean isBadCommand(String command) {
	    return command.startsWith("*kill") || command.startsWith("*shutup");
	}
      
    /**
     * This method clears commands older than COMMAND_CLEAR_TIME
     */
	private void clearOldCommands() {
        CommandLog commandLog;
        Date commandDate;
        
        if(this.lastLogDate == null)
            return;
        
        Date removeDate = new Date(this.lastLogDate.getTime() - COMMAND_CLEAR_TIME);

        while(!commandQueue.isEmpty())
        {
          commandLog = commandQueue.get(0);
          commandDate = commandLog.getDate();
          if(removeDate.before(commandDate))
            break;
          commandQueue.remove(0);
        }
    }
	
	/**
	 * Writes a line to the log
	 * 
	 * @param line
	 * @param date
	 */
	private void writeLog(String line, Date date) {
	    try {
            String newFileName = fileNameFormat.format(date);

            // Date changed, start logging to a new file
            if (!logFileName.equals(newFileName)) {
                logFileName = newFileName;
                
                // Close current file
                logFile.close();
                
                // Open new file
                File file = new File(newFileName);
                logFile = new FileWriter(file, true);
            }

            logFile.write(line + '\n');
            logFile.flush();
        } catch (IOException e) {
            Tools.printStackTrace("IOException encountered while saving command to log file", e);
            m_botAction.sendChatMessage("IOException encountered while saving command to log file: " + e.getMessage());
        }
    }

	  
	
	/**
	 * CheckLogTask performes the following commands:
	 *  - Saves the current year
	 *  - Issues the *log commmand to view the latest log lines
	 *  - Clears old commands from the cache
	 */
	private class CheckLogTask extends TimerTask {
	    
        public void run() {
            GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
            year = calendar.get(GregorianCalendar.YEAR);

            m_botAction.sendUnfilteredPublicMessage("*log");
            clearOldCommands();
        }
    }

	private class CommandLog {
        private Date date;
        private String arena;
        private String fromPlayer;
        private String toPlayer;
        private String command;

        public CommandLog(Date date, String arena, String fromPlayer, String toPlayer, String command) {
            this.date = date;
            this.arena = arena;
            this.fromPlayer = fromPlayer;
            this.toPlayer = toPlayer;
            this.command = command;
        }

        public Date getDate() {
            return date;
        }

        public String getArena() {
            return arena;
        }

        public String getFromPlayer() {
            return fromPlayer;
        }

        public String getToPlayer() {
            return toPlayer;
        }

        public String getCommand() {
            return command;
        }

        public String toString() {
            if (toPlayer.equals(""))
                return date + ":  " + fromPlayer + " (" + arena + ") " + command;
            return date + ":  " + fromPlayer + " (" + arena + ") to " + toPlayer + ": " + command;
        }

        public boolean isMatch(Date currentDate, String fromPlayerMask, String toPlayerMask, String commandMask) {
            return currentDate.before(date) && contains(fromPlayer, fromPlayerMask) && contains(toPlayer, toPlayerMask)
                    && contains(command, commandMask);
        }

        private boolean contains(String string1, String string2) {
            String lower1 = string1.toLowerCase();
            String lower2 = string2.toLowerCase();

            return lower1.indexOf(lower2) != -1;
        }
    }
}
