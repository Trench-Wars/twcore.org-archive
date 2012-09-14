package net.SubSpace.SSDR.Main;

import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import javax.swing.ImageIcon;

public class ThreadIcon implements Runnable{

	static TrayIcon trayIcon;
	static PopupMenu popup;
	static SystemTray tray;
	
   
	static boolean isIconInitialized = false;
	
	//Flag booleans
	static boolean isDownloading = false;
	static boolean isDownloadAvaliable = false;
	static boolean isServerReachable = true;
	static boolean isError = false;
	static int iconTick = 0;
	
	public static final String info = "INFO";
	public static final String error = "ERROR";
	
	public static void setHoverMessage(String message)
	{
		trayIcon.setToolTip(message);
	}
	public static void sendMessageToIcon(String Title,String message,String Type){
	    try{
	    	if(Type.equals("INFO"))
	    	{
	    		trayIcon.displayMessage(Title,message,TrayIcon.MessageType.INFO);	    		
	    	}
	    	else if(Type.equals("ERROR"))
	    	{
	    		trayIcon.displayMessage(Title,message,TrayIcon.MessageType.ERROR);	 
	    	}
	    	else
	    	{
	    		trayIcon.displayMessage(Title,message,TrayIcon.MessageType.NONE);	 
	    	}
	    }
	    catch(NullPointerException npe){
	        System.out.println(npe.getLocalizedMessage());
	    }
	}
	@Override
	public void run() 
	{
		while(true)
		{
			String link = System.getenv("APPDATA") + "/SSDR/Icons/Error/";
			if(isDownloading)
			{
				link = "Icons/flagYellow/" + iconTick + ".png";
			}
			else if(!isDownloading && !isDownloadAvaliable)
			{
				link = "Icons/flagBlue/" + iconTick + ".png";	
			}
			else if(!isDownloading && isDownloadAvaliable)
			{
				link = "Icons/Prize/" + iconTick + ".png";
			}
			if(!isServerReachable)
			{
				link = "Icons/Connecting/" + iconTick + ".png";
			}
			if(iconTick < 9)
			{
				iconTick++;
			}
			else
			{
				iconTick = 0;
			}
			if(ThreadIcon.isError)
			{
				link = "Icons/Error/" + iconTick + ".png";
			}
			if(!ThreadCheckForUpdates.runChecks)
			{
				iconTick = 0;
				link = "Icons/Off/" + iconTick + ".png";
			}
	        //trayIcon.setImage((new ImageIcon(link,"tray icon")).getImage());
	        trayIcon.setImage((new ImageIcon(getClass().getResource(link),"tray icon")).getImage());
	        
	        try {Thread.sleep(130L);} catch (InterruptedException e) {e.printStackTrace();}
	        
		}
		
	}
    static void initializeIcon() {
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported");
            return;
        }
        popup = new PopupMenu();
        String link = System.getenv("APPDATA") + "/SSDR/Images/bulb.gif";
        trayIcon =  new TrayIcon((new ImageIcon(link,"tray icon")).getImage());
          
        tray = SystemTray.getSystemTray();
    }
}
