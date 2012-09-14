package net.SubSpace.SSDR.Main;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.swing.JOptionPane;

public class ThreadMenu implements Runnable{

	static MenuItem ZoneList[] = new MenuItem[512];
	static CheckboxMenuItem checkZoneList[] = new CheckboxMenuItem[512];
	static ArrayList<MenuItem> listZone = new ArrayList<MenuItem>();
    static Menu ZoneListMenu = new Menu("Check Zone");
    static Menu checkZoneListMenu = new Menu("Toggle Check Zone");
    static String[] ZoneListNames = new String[1024];
    static File updateFile = new File(System.getenv("APPDATA") + "/SSDR/AppData/Update.txt");
	@Override
	public void run() {}
	


    public static void initializeMenu()
    {
    	for(int j = 0; j < SSDR.ZoneName.length;j++)
    	{
    		if(SSDR.ZoneName[j] != null)
    		{
    			ZoneList[j] = new MenuItem(SSDR.ZoneName[j]);    			
    		}
    	}
    	for(int j = 0; j < SSDR.ZoneName.length;j++)
    	{
    		if(SSDR.ZoneName[j] != null)
    		{
    			checkZoneList[j] = new CheckboxMenuItem(SSDR.ZoneName[j]);    			
    		}
    	}
        MenuItem aboutItem = new MenuItem("About");
        MenuItem updateExceptionList = new MenuItem("Update Exceptions");
        ThreadIcon.popup.add(checkZoneListMenu);
        MenuItem exitItem = new MenuItem("Exit");
        CheckboxMenuItem toggleUpdates = new CheckboxMenuItem("Toggle Updating");
        
        //Add components to popup menu
        ThreadIcon.popup.add(aboutItem);
        ThreadIcon.popup.addSeparator();
        ThreadIcon.popup.add(toggleUpdates);
        ThreadIcon.popup.add(ZoneListMenu);
        //ThreadIcon.popup.add(update);
        ThreadIcon.popup.addSeparator();
        ThreadIcon.popup.add(updateExceptionList);
        ThreadIcon.popup.addSeparator();
        //Add to menu
        
        for(int j = 0; j < ZoneList.length;j++)
        {
        	if(ZoneList[j] != null)
        	{        		
        		ZoneListMenu.add(ZoneList[j]);
        	}
        	if(ZoneList[j] != null)
        	{
        		final int b = j;
        		ZoneList[j].addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
						{							System.out.println("Server:" + "192.168.1.3" + " Port:" + SSDR.ZonePort[b]);
                    		//new Thread(new ThreadCheck(SSDR.ZoneName[b],SSDR.ZoneIP[b], SSDR.ZonePort[b], true), SSDR.ZoneName[b] + "'s Downloading Thread")
							new Thread(new ThreadCheck(SSDR.ZoneName[b],"192.168.1.3", SSDR.ZonePort[b], true), SSDR.ZoneName[b] + "'s Downloading Thread")
                    		.start();
							//.start();
						}
                    	
                    }
                });
        	}
        }
        for(int j = 0; j < checkZoneList.length;j++)
        {
        	final File file = new File(System.getenv("APPDATA") + "/SSDR/ZoneData/" + SSDR.ZoneName[j] + ".txt");
        	if(checkZoneList[j] != null)
        	{        		
        		checkZoneListMenu.add(checkZoneList[j]);
                boolean isCheckingForUpdatesAlready = true;
                if(file.exists())
                {
                	String temp = null;
                	try {
						temp = ThreadReadWrite.readPropertyFile(file);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
                	if(temp.equals("false"))
                	{
                		isCheckingForUpdatesAlready = false;
                	}
                }
                if(isCheckingForUpdatesAlready)
                {
                	SSDR.ZoneUpdate[j] = true;
                	checkZoneList[j].setState(true);
                }
                else
                {
                	SSDR.ZoneUpdate[j] = false;
                	checkZoneList[j].setState(false);
                }
        	}
        	if(checkZoneList[j] != null)
        	{
        		final int b = j;
        		checkZoneList[j].addItemListener(new ItemListener() {
                    public void itemStateChanged(ItemEvent e) {
                        int cb1Id = e.getStateChange();
                        if (cb1Id == ItemEvent.SELECTED)
                        {
                        	try {
								ThreadReadWrite.writeProperty(file, "true");
								SSDR.ZoneUpdate[b] = true;
                        	} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
                        }
                        else
                        {
                        	try {
								ThreadReadWrite.writeProperty(file, "false");
								SSDR.ZoneUpdate[b] = false;
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
            			}
                    }
                });
        	}
        }
        ThreadIcon.popup.add(exitItem);
        
        ThreadIcon.trayIcon.setPopupMenu(ThreadIcon.popup);
        
        try {
        	ThreadIcon.tray.add(ThreadIcon.trayIcon);
        } catch (AWTException e) {
            System.out.println("TrayIcon could not be added.");
            return;
        }
        String AllowUpdate = "null";
        if(updateFile.exists())
        {
        	try {
				AllowUpdate = ThreadReadWrite.readPropertyFile(updateFile);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			if(AllowUpdate.equals("true"))
			{
				ThreadIcon.setHoverMessage("(Force an update through " + System.getProperty("line.separator")+ "the list of zones by right-clicking" + System.getProperty("line.separator")+ " this icon and going " + System.getProperty("line.separator")+ "Check Zone>\"Zone Name\".)");
				ThreadCheckForUpdates.runChecks = true;
        		SSDR.threadCheckForUpdates = (new Thread(new ThreadCheckForUpdates(),"Updating Thread"));
        		SSDR.threadCheckForUpdates.start();
				toggleUpdates.setState(true);
			}
			else
			{
				ThreadIcon.setHoverMessage("This App is Set to" + System.getProperty("line.separator") + "\"NOT UPDATING\" Status." + System.getProperty("line.separator") + "To Start Auto Updates," + System.getProperty("line.separator") + "Go to \"Auto Updates\"." + System.getProperty("line.separator") +"You can still force updates.");
				ThreadCheckForUpdates.runChecks = false;
				toggleUpdates.setState(false);
			}
        }
        else
        {
        	try {
				ThreadReadWrite.writeProperty(updateFile, "true");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			ThreadIcon.setHoverMessage("(Force an update through " + System.getProperty("line.separator")+ "the list of zones by right-clicking" + System.getProperty("line.separator")+ " this icon and going " + System.getProperty("line.separator")+ "Check Zone>\"Zone Name\".)");
			ThreadCheckForUpdates.runChecks = true;
    		SSDR.threadCheckForUpdates = (new Thread(new ThreadCheckForUpdates(),"Updating Thread"));
    		SSDR.threadCheckForUpdates.start();
			toggleUpdates.setState(true);
			
        }
        toggleUpdates.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                int cb1Id = e.getStateChange();
                if (cb1Id == ItemEvent.SELECTED){
                	if(!ThreadCheckForUpdates.runChecks == true)
                	{      
        				ThreadIcon.setHoverMessage("(Force an update through " + System.getProperty("line.separator")+ "the list of zones by right-clicking" + System.getProperty("line.separator")+ " this icon and going " + System.getProperty("line.separator")+ "Check Zone>\"Zone Name\".)");
                		ThreadCheckForUpdates.runChecks = true;
                		SSDR.threadCheckForUpdates = (new Thread(new ThreadCheckForUpdates(),"Updating Thread"));
                		SSDR.threadCheckForUpdates.start();
                		try {
							ThreadReadWrite.writeProperty(updateFile, "true");
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
                	}
                } else {
    				ThreadIcon.setHoverMessage("This App is Set to" + System.getProperty("line.separator") + "\"NOT UPDATING\" Status." + System.getProperty("line.separator") + "To Start Auto Updates," + System.getProperty("line.separator") + "Go to \"Auto Updates\"." + System.getProperty("line.separator") +"You can still force updates.");
                	ThreadCheckForUpdates.runChecks = false;
                	try {
						ThreadReadWrite.writeProperty(updateFile, "false");
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
                }
            }
        });
        ThreadIcon.trayIcon.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                
            	//IF CLICKED TWICE.
            	
            	//JOptionPane.showMessageDialog(null,"This dialog box is run from System Tray");
            }
        });
        
        aboutItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(null,
                        "This is a Application that supports unlimited downloading speeds for SubSpace files. Made by JabJabJab");
            }
        });
        
        updateExceptionList.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fetchZoneExceptions(true);
            }
        });
        
        exitItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	ThreadIcon.tray.remove(ThreadIcon.trayIcon);
                System.exit(0);
            }
        });
    }
	public static void fetchZoneExceptions(boolean forced)
	{
		URL u;
	      InputStream is = null;
	      String s;
	      try {
			u = new URL("http://dl.dropbox.com/u/31589881/ZoneExceptions.txt");
			is = u.openStream();
			new DataInputStream(is); 
			BufferedReader ds = new BufferedReader(new InputStreamReader(is));

			String out = "";
			for(int j = 0; j < 512;j++)
	    	{
	    		SSDR.ZonePort[j] = 39949;
	    	}
			while ((s = ds.readLine()) != null)
			{
				out.concat(s);
				out.concat(";");
				if(s.contains(":"))
				{					
					for(int j = 0;j < SSDR.ZoneIP.length;j++)
					{
						
						String temp[] = s.split(":");
						if(SSDR.ZoneIP[j] != null)
						{
							if(SSDR.ZoneIP[j].equals(temp[0]))
							{
								System.out.println(SSDR.ZoneName[j] + "'s IP is now:" + temp[1] + " Port:" + temp[2] + ".");
								SSDR.ZoneIP[j] = temp[1];

									int tempPort = Integer.parseInt(temp[2]);
									SSDR.ZonePort[j] = tempPort;								
							}							
						}
					}
				}
			}
			for(int x = 0; x < SSDR.ZoneName.length;x++)
			{
				if(SSDR.ZoneName[x] != null)
				{
					System.out.println("Name:" + SSDR.ZoneName[x] + " IP:" + SSDR.ZoneIP[x] + " Port:" + SSDR.ZonePort[x]);
				}
			}
			ThreadReadWrite.writeProperty(new File(System.getenv("APPDATA") + "/SSDR/AppData/ZoneExceptions.txt"), out);
			if(forced)
			{				
				ThreadIcon.sendMessageToIcon("Update ZoneIP Exceptions List","Zones have been updated to the latest directory.", ThreadIcon.info);
			}
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
            is.close();
         } catch (IOException ioe) {
            // just going to ignore this one
         }
	}
}
