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
import twcore.core.OperatorList;
import twcore.core.events.Message;
import twcore.core.util.Tools;

/**
 * This module reports bad commands (and player kicks for message flooding) that are shown on the *log
 * This module also logs all *log lines to a file. The functionality is copied from the mrarrogant bot.
 * 
 * @author MMaverick
 */
public class staffbot_staffchat_savelog extends Module {
    
	public static final int MINUTE = 5;
    public static final int CHECK_LOG_TIME = MINUTE * Tools.TimeInMillis.MINUTE;

    private SimpleDateFormat chatDateFormat = new SimpleDateFormat("HH:mm", Locale.US);
    private SimpleDateFormat fileNameFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private FileWriter logFile;
    private File path;
    private File currentFile;
    private StringBuffer buffer;
    
    // TimerTasks
    private CheckStaffChat checkStaffChat = new CheckStaffChat();
    private WriteToLog writeToLog = new WriteToLog();
    

    @Override
    public void initializeModule() {
    	
        BotSettings botSettings = m_botAction.getBotSettings();
        path = new File(botSettings.getString("logpath_staffchat"));
        buffer = new StringBuffer();
        
    	currentFile = new File(path, fileNameFormat.format(new Date())+".log");
        try {
			logFile = new FileWriter(currentFile, true);
		} catch (IOException e) {
			Tools.printStackTrace(e);
			cancel();
		}
        
        // Algo to have time looking like 00:05, 00:10, etc..
        // We don't want a log with weird time like 00:03, 00:07..
        // We must synch our timerTasks!
        SimpleDateFormat format = new SimpleDateFormat("mm:ss", Locale.US);
        String[] split = format.format(new Date()).split(":");
        int min = Integer.parseInt(split[0])%MINUTE;
        int sec = Integer.parseInt(split[1]);
        
        int diff = MINUTE*60-(min+1)*60+60-sec;
        if (diff<=10)
        	diff += MINUTE*60;
        diff += 20; // Just to make sure
        
        // Must be executed 10 seconds before "writeToLog"
        m_botAction.scheduleTaskAtFixedRate(checkStaffChat, (diff-10)*Tools.TimeInMillis.SECOND, CHECK_LOG_TIME);
        m_botAction.scheduleTaskAtFixedRate(writeToLog, diff*Tools.TimeInMillis.SECOND, CHECK_LOG_TIME);
    }

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.MESSAGE);
	}

	
	public void handleEvent(Message event) {

		String line = event.getMessage();
        if( event.getMessageType() == Message.ARENA_MESSAGE ) {
        	if (line.contains("(staff) staff: ")) {
        		buffer.append(",");
        		buffer.append(line.substring(15));
        	}
        }        
	}
	
    @Override
    public void cancel() {
        try {
        	if (logFile!=null)
        		logFile.close();
        } catch(IOException ioe) {}
        
        m_botAction.cancelTask(checkStaffChat);
        m_botAction.cancelTask(writeToLog);
    }
	  

	private class CheckStaffChat extends TimerTask {
        public void run() {
            m_botAction.sendUnfilteredPublicMessage("?chat");
        }
    }
	
	private class WriteToLog extends TimerTask {
	    
        public void run() {
            try {

            	File newFile = new File(path, fileNameFormat.format(new Date())+".log");
            	
                if (!currentFile.getAbsolutePath().equals(newFile.getAbsolutePath())) {
                	
                	currentFile = newFile;
                    logFile.close();
                    
                    // Open new file
                    logFile = new FileWriter(currentFile, true);
                }
                
                String line = chatDateFormat.format(new Date()) + " : ";
                
                if (buffer.toString().length()>0)
                	logFile.write(line+buffer.toString().substring(1) + '\n');
                logFile.flush();
                buffer = new StringBuffer();
                
            } catch (IOException e) {
                Tools.printStackTrace("IOException encountered while saving command to log file", e);
                m_botAction.sendChatMessage("IOException encountered while saving command to log file: " + e.getMessage());
            }
        }
    }

}
