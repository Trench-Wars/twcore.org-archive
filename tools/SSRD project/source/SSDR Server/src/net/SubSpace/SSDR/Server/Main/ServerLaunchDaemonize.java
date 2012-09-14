package net.SubSpace.SSDR.Server.Main;

import java.io.File;
import java.util.Scanner;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;

public class ServerLaunchDaemonize implements Daemon {

	static boolean isGUIDisabled = false;
	static ServerThread server;
	static ServerGui gui;
    private static ServerLaunchDaemonize engineLauncherInstance = new ServerLaunchDaemonize();

    public static void main(String[] args) {
    	for(int j = 0; j < args.length;j++)
    	{
    		if(args[j].equals("NOGUI"))
    		{
    			isGUIDisabled = true;
    		}
    	}
        engineLauncherInstance.initialize();
        Scanner sc = new Scanner(System.in);
        System.out.printf("Enter 'stop' to halt: ");
        while(!sc.nextLine().toLowerCase().equals("stop"))
        {
        	if(sc.nextLine().toLowerCase().equals("stop"))
        	{
        		break;
        	}
        }
        System.exit(0);
    }

    public static void windowsService(String args[])
    {
        String cmd = "start";
        if (args.length > 0)
        {
            cmd = args[0];
        }
        if ("start".equals(cmd))
        {
            engineLauncherInstance.windowsStart();
        }
        else
        {
            engineLauncherInstance.windowsStop();
        }
    }
    public void windowsStart()
    {
        initialize();
    }
    public void windowsStop() 
    {
        terminate();
        synchronized(this) 
        {
        this.notify();
        }
    }
    public void start()
    {
        initialize();
    }
    public void stop()
    {
        terminate();
    }
    public void destroy(){}
    private void initialize()
    {
    	File ZoneDataDir =new File("SSDR/ZoneData/");
		boolean exists = ZoneDataDir.exists();
		if(!exists)
		{
			ZoneDataDir.mkdirs();
			Server.sendMessage(ServerGui.MESSAGE_NONE,"Directory created");
		}
		if(!isGUIDisabled)
		{
			gui = new ServerGui();			
		}
		Server.InitializeLibrary();
		Thread serverMain = new Thread(new Server(),"Server Main Thread");
		serverMain.start();
		Scanner sc = new Scanner(System.in);
        System.out.printf("Enter 'stop' to halt: ");
        while(!sc.nextLine().toLowerCase().equals("stop"));
    }

    public void terminate(){}
	public void init(DaemonContext arg0) throws DaemonInitException, Exception {}
}