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
 * This module logs who is on the "staff" chat every 5 minutes on a file.
 * Recently, this module has been tweaked to log also the whole chat (text)
 * 
 * The name monitoring system uses a buffer and write to the file every 5 minutes
 * while the whole chat is written as soon as a text is seen
 * 
 * @author Arobas+
 */
public class staffbot_staffchat_savelog extends Module {
    
	// static variable used for names monitoring
	public static final int MINUTE = 5;
    public static final int CHECK_LOG_TIME = MINUTE * Tools.TimeInMillis.MINUTE;

    private SimpleDateFormat chatDateFormat = new SimpleDateFormat("HH:mm", Locale.US);
    private SimpleDateFormat fileNameFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);

    private File path;
    
    private File namesFile;
    private FileWriter namesWriter;
    private StringBuffer bufferName;
    
    private File textFile;
    private FileWriter textWriter;
    
    // TimerTasks
    private CheckStaffChat checkStaffChat = new CheckStaffChat();
    private WriteToLog writeToLog = new WriteToLog();
    

    @Override
    public void initializeModule() {
    	
        BotSettings botSettings = m_botAction.getBotSettings();
        path = new File(botSettings.getString("logpath_staffchat"));
        
        bufferName = new StringBuffer();
        namesFile = new File(path, fileNameFormat.format(new Date())+".log");

        textFile = new File(path, fileNameFormat.format(new Date())+"_text.log");

        try {
        	namesWriter = new FileWriter(namesFile, true);
        	textWriter = new FileWriter(textFile, true);
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
		String playerName = event.getMessager();
		
        if( event.getMessageType() == Message.ARENA_MESSAGE ) {

        	if (line.contains("(staff) staff: ")) {
        		bufferName.append(",");
        		bufferName.append(line.substring(15));
        	}
        }   
        
        else if( event.getMessageType() == Message.ALERT_MESSAGE ){

            String command = event.getAlertCommandType().toLowerCase();
            if( command.equals( "help" )){
            	writeText("help: " + "(" + playerName + ") " + line);
            } else if( command.equals( "cheater" )){
            	writeText("advert: " + "(" + playerName + ") " + line);
            } else if( command.equals( "advert" )){
            	writeText("cheater: " + "(" + playerName + ") " + line);
            }
        }
        
        else if ( event.getMessageType() == Message.CHAT_MESSAGE ) {

        	if (event.getChatNumber() == 1) { // Staff chat
        		writeText(playerName + "> " + line);
        	}
        	else if (event.getChatNumber() == 1) { // SMOD chat
        		// nothing
        	}
        }
	}
	
	public void writeText(String line) {
		
        try {

        	File newFile = new File(path, fileNameFormat.format(new Date())+"_text.log");
        	
            if (!textFile.getAbsolutePath().equals(newFile.getAbsolutePath())) {
            	
            	textFile = newFile;
                textWriter.close();
                
                // Open new file
                textWriter = new FileWriter(textFile, true);
            }

            textWriter.write(Tools.getTimeStamp() + " - " + line + "\n");
            textWriter.flush();

        } catch (IOException e) {
            Tools.printStackTrace("IOException encountered while saving command to log file", e);
            m_botAction.sendChatMessage("IOException encountered while saving command to log file: " + e.getMessage());
        }
		
	}
	
    @Override
    public void cancel() {
        try {
        	if (namesWriter!=null)
        		namesWriter.close();
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
            	
                if (!namesFile.getAbsolutePath().equals(newFile.getAbsolutePath())) {
                	
                	namesFile = newFile;
                    namesWriter.close();
                    
                    // Open new file
                    namesWriter = new FileWriter(namesFile, true);
                }
                
                String line = chatDateFormat.format(new Date()) + " : ";
                
                if (bufferName.toString().length()>0)
                	namesWriter.write(line+bufferName.toString().substring(1) + '\n');
                namesWriter.flush();
                bufferName = new StringBuffer();
                
            } catch (IOException e) {
                Tools.printStackTrace("IOException encountered while saving command to log file", e);
                m_botAction.sendChatMessage("IOException encountered while saving command to log file: " + e.getMessage());
            }
        }
    }

}
