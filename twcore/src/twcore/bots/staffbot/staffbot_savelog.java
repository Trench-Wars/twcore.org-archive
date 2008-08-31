package twcore.bots.staffbot;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import twcore.bots.Module;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.events.FileArrived;
import twcore.core.events.Message;
import twcore.core.util.Tools;

public class staffbot_savelog extends Module {
	
	private boolean 	m_logArchivingEnabled;
	boolean             m_logActive = false;
	java.util.Date      m_logTimeStamp;
	final String        m_LOGFILENAME = "subgame.log";
	String              m_logNotifyPlayer;
	
	private TimerTask getLogTask;
	
	@Override
	public void initializeModule() {
		
		BotSettings m_botSettings = m_botAction.getBotSettings();
		
		if ( m_botSettings.getString( "LogArchivingEnabled" ).toLowerCase().equals("true") ){
            m_logArchivingEnabled = true;
        } else {
            m_logArchivingEnabled = false;
        }
		
		if( m_logArchivingEnabled ){
            getLogTask = new TimerTask(){
                public void run(){
                    m_botAction.sendChatMessage(2, "Automatically processing server log." );
                    getLog();
                }
            };

            try{
                Date dateNow = new Date();

                String strDate = new SimpleDateFormat("MM/dd/yyyy").format( dateNow );
                strDate += " " + m_botSettings.getString( "AutoLogTime" );
                Date timeToActivate = new SimpleDateFormat("MM/dd/yyyy k:m").parse( strDate );

                //If scheduled time for today is passed, schedule for tommorow
                if( dateNow.after( timeToActivate ) ){
                    // add a day
                    timeToActivate.setTime(timeToActivate.getTime() + Tools.TimeInMillis.DAY);
                }

                m_botAction.scheduleTaskAtFixedRate( getLogTask, timeToActivate.getTime(), Tools.TimeInMillis.DAY );
                Tools.printLog( m_botAction.getBotName() + "> Autolog at: " + timeToActivate );
            } catch( Exception e ){
                Tools.printStackTrace( e );
            }
        }
	}
	
	@Override
	public void cancel() {
		m_botAction.cancelTask(getLogTask);
	}
	

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.MESSAGE);
		eventRequester.request(EventRequester.FILE_ARRIVED);
	}
	
	public void handleEvent(Message event) {
		short sender = event.getPlayerID();
        String message = event.getMessage();
        boolean remote = event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE;
        String name = remote ? event.getMessager() : m_botAction.getPlayerName(sender);
        OperatorList m_opList = m_botAction.getOperatorList();
        
		
        if( event.getMessageType() == Message.PRIVATE_MESSAGE || 
            event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ) {
            
            // SMod+ commands
            if(m_opList.isSmod(name)) {
                
                // !help
                if(message.toLowerCase().startsWith("!help")) {
                    final String[] helpSmod = {
                            "--------------------[ SaveLog: SMod+ ]---------------------",
                            " !getlog                   - Downloads server log",
                            " !getAutologTime           - Returns the time when log is automatically backed up"
                    };

                    m_botAction.smartPrivateMessageSpam( name, helpSmod );
                }
                
                // !getlog
                if( message.toLowerCase().startsWith("!getlog")) {
                    getLog(name);
                }
                // !getAutologTime
                if( message.toLowerCase().startsWith("!getautologtime") ){
                    long time = getLogTask.scheduledExecutionTime();
                    
                    m_botAction.sendSmartPrivateMessage(name, 
                               "Log will automatically be backed up at: " +
                               new Date(time)+ 
                               "["+Tools.getTimeDiffString(time, true)+"]"
                               );
                }
            }
        }
        
        
        
        
	}
	
	public void handleEvent( FileArrived event ){
        if ( event.getFileName().equals(m_LOGFILENAME) ){
            logArrived();
        }
    }
	
	/* Log Archiving Code */
    public void getLog(){
        if(!m_logArchivingEnabled)return;
        if(m_logActive)return;

        m_logActive = true;

        m_logTimeStamp = new java.util.Date();
        m_botAction.sendUnfilteredPublicMessage( "*getfile " + m_LOGFILENAME );
        Tools.printLog("Downloading server log.");
    }

    public void getLog( String name){
        if(!m_logArchivingEnabled){
            m_botAction.sendSmartPrivateMessage( name, "Sorry, all log archiving functions for this bot have been disabled." );
            return;
        }

        if(!m_logActive){
            Tools.printLog( m_botAction.getBotName() + "> Processing server log at " + name + "'s request." );
            m_botAction.sendChatMessage(2, "Processing server log at " + name + "'s request." );
            m_botAction.sendSmartPrivateMessage( name, "Downloading server log. You will be notified when completed." );

            m_logNotifyPlayer = name;
            getLog();
        } else {
        	m_botAction.sendSmartPrivateMessage( name, "Already processing a log. Please try again when finished." );
        }
    }

    public void logArrived(){
        String archivePath = m_botAction.getBotSettings().getString( "ArchivePath" );
        compressToZip( "data/"+m_LOGFILENAME, archivePath + "/TWLog " + new SimpleDateFormat("MMM-dd-yyyy (HH-mm-ss z)").format(m_logTimeStamp) + ".zip" );

        if( m_logNotifyPlayer != null ){
            m_botAction.sendRemotePrivateMessage( m_logNotifyPlayer, "Server log successfully downloaded and archived." );
            m_logNotifyPlayer = null;
        }

        m_botAction.sendChatMessage(2, "Server log successfully downloaded and archived." );
        Tools.printLog("Server log successfully downloaded and archived.");
        m_logActive = false;
    }
    
    public void compressToZip( String inFileName, String outFileName){
        try {
            FileInputStream fileIn = new FileInputStream( inFileName );
            ZipOutputStream fileOut = new ZipOutputStream( new FileOutputStream( outFileName ));

            fileOut.putNextEntry(new ZipEntry( inFileName ));

            byte[] buf = new byte[1024];
            int len;

            while ((len = fileIn.read(buf)) > 0) {
                fileOut.write(buf, 0, len);
            }

            fileIn.close();
            fileOut.closeEntry();
            fileOut.close();

        } catch( Exception e ){
            Tools.printStackTrace( e );
        }
    }

}
