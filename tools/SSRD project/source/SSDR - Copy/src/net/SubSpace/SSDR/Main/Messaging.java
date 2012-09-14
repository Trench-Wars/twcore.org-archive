package net.SubSpace.SSDR.Main;

import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;

public class Messaging {
	
	public static final MessageType Error = TrayIcon.MessageType.ERROR;
    public static final MessageType Info = TrayIcon.MessageType.INFO;
    public static final MessageType Warn = TrayIcon.MessageType.WARNING;
    public static final MessageType None = TrayIcon.MessageType.NONE;
    
	public static void displayMessage(String Title, String message, MessageType type)
    {
    	if(type == null)
    	{
    		type = TrayIcon.MessageType.NONE;
    	}
    	else
    	{    		
    		ThreadIcon.trayIcon.displayMessage(Title,message, type);	
    	}
    }
}
