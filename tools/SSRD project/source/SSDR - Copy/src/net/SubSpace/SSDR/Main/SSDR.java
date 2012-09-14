package net.SubSpace.SSDR.Main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Scanner;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class SSDR {
	public static String Directory;
	public static boolean forceRun = false;
	String appName = "SSDR Client";
	static JOptionPane jPane = new JOptionPane();
	static Thread threadicon;
	static Thread threadmenu;
	static Thread threadCheckForUpdates;
	//##Zone List Info##//
	static int[] ZonePort = new int[512];
	static String[] ZoneIP = new String[512];
	static String[] ZoneName = new String[512];
	static boolean[] ZoneUpdate = new boolean[512];
	 static int zoneCount;
	Runtime runtime = Runtime.getRuntime();
	
	static File fileLock;
	
    public static void main(String[] args) {
    	 try {
         UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
     } 
     catch (UnsupportedLookAndFeelException e) {
        // handle exception
     }
     catch (ClassNotFoundException e) {
        // handle exception
     }
     catch (InstantiationException e) {
        // handle exception
     }
     catch (IllegalAccessException e) {
        // handle exception
     }
     for(String s : args)
     {
    	 if(s.equals("FORCERUN"))
    	 {
    		 forceRun = true;
    	 }
     }
    	threadicon = (new Thread(new ThreadIcon(),"Icon Thread"));
    	threadmenu = (new Thread(new ThreadMenu(),"Menu Thread"));
    	threadCheckForUpdates = (new Thread(new ThreadCheckForUpdates(),"Updating Thread"));
    	Initialize();
    	ThreadIcon.initializeIcon();
    	ThreadMenu.initializeMenu();
    	//threadCheckForUpdates.start();
    	threadicon.start();
    	threadmenu.start();
    }    
    
    public static void Initialize()
    {
    	//Some code i found to keep process from duplicating---\
    	if(!forceRun)                                        //|
    	{                                                    //|
    		AppCheckLock check = new AppCheckLock();         //|
    		check.test();                                    //|    		
    	}                                                    //|
    	AppLock lock = new AppLock("SSDR Client");           //|
    	lock.isAppActive();                                  //|
    	//-----------------------------------------------------/
    	
    	File file = new File(System.getenv("APPDATA") + "/SSDR/");
        if(!file.exists())
        {
       	 file.mkdirs();
        }
        File file2 = new File(System.getenv("APPDATA") + "/SSDR/ZoneData/");
        if(!file2.exists())
        {
       	 file2.mkdirs();
        }
        File file3 = new File(System.getenv("APPDATA") + "/SSDR/AppData/");
        if(!file3.exists())
        {
       	 file3.mkdirs();
        }
    	File dirtxt = new File(System.getenv("APPDATA") + "/SSDR/AppData/Directory.txt");
    	if(!dirtxt.exists())
    	{
    		retrieveDirectory();
    	}
    	else
    	{
    		try {
    			retrieveDirectory();
				Directory = ThreadReadWrite.readPropertyFile(dirtxt);
			} catch (IOException e) {
			}
    	}
    
    	try {read();} catch (IOException e) {return;}
    	for(int j = 0; j < zoneCount; j++)
    	{
			//zoneCount++;
			System.out.println(Zonelist[j]);
			String[] temp = Zonelist[j].split(",");
			ZoneName[j] = temp[0];
			File ZoneInfo = new File(System.getenv("APPDATA") + "/SSDR/ZoneData/" + ZoneName[j] + ".txt");
			if(!ZoneInfo.exists())
			{
				try {
					ThreadReadWrite.writeNewPropertyFile(ZoneInfo);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			else
			{
				try {
					if(ThreadReadWrite.readPropertyFile(ZoneInfo).equals("true"))
					{
						ZoneUpdate[j] = true;
					}
					else if(ThreadReadWrite.readPropertyFile(ZoneInfo).equals("false"))
					{
						ZoneUpdate[j] = false;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ZoneIP[j] = temp[1]; 			
    	}
    	ThreadMenu.fetchZoneExceptions(false);
    }
    public static void retrieveDirectory()
    {
    	File attempt1 = null;
    	File attempt2 = null;
    	boolean validatedDirectory = false;
    	attempt1 = new File(("C:/Program Files/Continuum"));

    	if(!attempt1.exists())
    	{
    		attempt2 = new File("C:/Continuum");
    		if(!attempt2.exists())
    		{
    			boolean triedOnce = false;
    			while(!validatedDirectory)
    			{
    				if(!triedOnce)
    				{    					
    					Directory = JOptionPane.showInputDialog("Enter Continuum Directory. This will be the only time you need to do so, unless you remove this program.");
    				}
    				else
    				{
    					Directory = JOptionPane.showInputDialog("INVALID DIRECTORY:" + Directory + ". Enter Continuum Directory");
    				}
    				File dir = new File(Directory);
    				if(dir.exists())
    				{
    					if(Directory.endsWith("Continuum/") || Directory.endsWith("Continuum\\") || Directory.endsWith("Continuum"))
    					{
    						if(Directory.endsWith("m") || Directory.endsWith("M"))
    						{
    							Directory = Directory + "/";
    						}
    						validatedDirectory = true;
    		    			try {
    		    				File out = new File(System.getenv("APPDATA") + "/SSDR/AppData/Directory.txt");
    		    				out.mkdirs();
    							ThreadReadWrite.writeProperty(out, Directory);
    						} catch (IOException e) {
    							e.printStackTrace();
    						}
    					}
    				}   	
    				triedOnce = true;
    			}    			
    		}
    		else
    		{
    			validatedDirectory = true;
    			try {
    				File out = new File(System.getenv("APPDATA") + "/SSDR/AppData/Directory.txt");
    				Directory = attempt2.getAbsolutePath();
    				ThreadReadWrite.writeProperty(out, attempt2.getAbsolutePath());
				} catch (IOException e) {
					e.printStackTrace();
				}
    		}
    	}
    	else
    	{
			try {
				File out = new File(System.getenv("APPDATA") + "/SSDR/AppData/Directory.txt");
				out.mkdirs();
				Directory = attempt1.getAbsolutePath();
				ThreadReadWrite.writeProperty(out, attempt1.getAbsolutePath());
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    }
    
	static String[] Zonelist = new String[256];
	 static void read() throws IOException {
		 	File file = new File(Directory + "/Zone.dat");
		    Scanner scanner = new Scanner(new FileInputStream(file), "UTF8");
		    try {
		    	int count = 0;
		      while (scanner.hasNextLine())
		      { 
		    	  String temp = (scanner.nextLine() /*+ NL*/);
		    	  if(!temp.startsWith("#"))
		    	  {
		    		  Zonelist[count] = temp;		        	
		    		  count++;
		    	  }
		    	zoneCount = count; 
		      }
		    }
		    finally{
		      scanner.close();
		    }
		  }
}
