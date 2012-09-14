package net.SubSpace.SSDR.Server.Main;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
public class Server implements Runnable{
	
	//Files
	File ZoneList;
	File ZoneData;
	File AppData;
	File Data;

	//Lists
	public static List<File> LVLlist = new ArrayList<File>();
	public static List<File> LVZlist = new ArrayList<File>();
	public static List<File> MISClist = new ArrayList<File>();
	
	public Server(){}
	
	 static void sendMessage(String messageType, String s)
	 {
		 System.out.println(s);
		 if(ServerGui.text != null)
		 {
			 while(!ServerGui.text.isVisible())
			 {
				 //Wait
			 }
			 final int totalMessageLength = 54;
			 int messageLength = s.length();
			 int takenLengthOfMessage;
			 int mergedLength;
			 int leftLength;
			 if(messageType.equals("NONE"))
			 {			 
				 ServerGui.text.append(s + "\n");
				 return;
			 }
			 else if(messageType.equals("WARNING"))
			 {			 
				 takenLengthOfMessage = 13;
				 mergedLength = messageLength + takenLengthOfMessage;
				 leftLength = totalMessageLength - mergedLength;			 
				 ServerGui.text.append("((!!!WARNING:" + s);
				 for(int j = 0; j < leftLength - 5; j++)
				 {
					 ServerGui.text.append(" ");
				 }
				 ServerGui.text.append("!!!))\n");
			 }
			 else if(messageType.equals("CONNECTION"))
			 {			 
				 takenLengthOfMessage = 17;
				 mergedLength = messageLength + takenLengthOfMessage;
				 leftLength = totalMessageLength - mergedLength;			 
				 ServerGui.text.append("<==((CONNECTION: " + s);
				 for(int j = 0; j < leftLength - 5; j++)
				 {
					 ServerGui.text.append(" ");
				 }
				 ServerGui.text.append("))==>" + "\n");
			 }
			 else if(messageType.equals("NOTICE"))
			 {			 
				 takenLengthOfMessage = 11;
				 mergedLength = messageLength + takenLengthOfMessage;
				 leftLength = totalMessageLength - mergedLength;			 
				 ServerGui.text.append("<<|NOTICE: " + s);
				 for(int j = 0; j < leftLength - 3; j++)
				 {
					 ServerGui.text.append(" ");
				 }
				 ServerGui.text.append("|>>" + "\n");
			 }
			 else if(messageType.equals("ERROR"))
			 {			 
				 takenLengthOfMessage = 9;
				 mergedLength = messageLength + takenLengthOfMessage;
				 leftLength = totalMessageLength - mergedLength;			 
				 ServerGui.text.append("((ERROR: " + s);
				 for(int j = 0; j < leftLength - 2; j++)
				 {
					 ServerGui.text.append(" ");
				 }
				 ServerGui.text.append("))" + "\n");
			 }
			 ServerGui.text.setCaretPosition(ServerGui.text.getDocument().getLength());
		 }
	}

	@Override
	public void run() 
	{
		try {
			setupStuff(39949);
		} catch (IOException e) {
			
			e.printStackTrace();
		}
	}
	
	void setupStuff(int listen_port) throws IOException
	{
		ServerSocket serverSocket = null;
        boolean listening = true;
 
        try {
            serverSocket = new ServerSocket(39949);
        } catch (IOException e) {
            System.err.println("Could not listen on port: 39949.");
            System.exit(-1);
        }
        if(LVLlist.isEmpty() && LVZlist.isEmpty() && MISClist.isEmpty())
        {
        	sendMessage(ServerGui.MESSAGE_WARNING,"NO FILES FOUND IN REPOSITORY");
        	sendMessage(ServerGui.MESSAGE_WARNING,"NO FILES WILL BE GIVEN ON CHECK");
        	sendMessage(ServerGui.MESSAGE_WARNING,"RESTART WITH FILES IN /SSDR/ZONEDATA");
        }
        while (listening)
        {
        	new ServerThread(serverSocket.accept()).start();
		}
        serverSocket.close();
		
	}
	public static void InitializeLibrary()
	{
		String files;
		File folder = new File("SSDR/ZoneData/");
		try {
			System.out.println("Getting all files in " + folder.getCanonicalPath() + " including those in subdirectories");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		List<File> filesTotal = (List<File>) FileUtils.listFiles(folder, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
		int countFiles = 0;
		File[] listOfFiles = new File[filesTotal.size()];               
		for (File file : filesTotal) {
			if(file != null)
			{
				listOfFiles[countFiles] = file;
				countFiles++;				
			}
		}
		int count = 0;
		for (int i = 0; i < listOfFiles.length; i++) 
		{	
			if (listOfFiles[i].isFile())
			{
				files = listOfFiles[i].getName();
				if(files.endsWith(".lvl") || files.endsWith(".lvz") || files.endsWith(".bmp") || files.endsWith(".wav") || files.endsWith(".gif") || files.endsWith(".png") || files.endsWith(".jpg"))
				{		
					count++;
					if(files.endsWith(".lvl"))
					{
						LVLlist.add(listOfFiles[i]);
					}
					else if(files.endsWith(".lvz"))
					{
						LVZlist.add(listOfFiles[i]);
					}
					else if(!isHarmfulFile(files))
					{
						MISClist.add(listOfFiles[i]);
					}
				}
			}
		}
		sendMessage(ServerGui.MESSAGE_NONE,count + " Files have been added to the checklist.");	
	}
	private static boolean isHarmfulFile(String files) {
		String temp = files.toLowerCase();
		if(temp.endsWith(".wav")
		|| temp.endsWith(".wa2") 
		|| temp.endsWith(".bmp")
		|| temp.endsWith(".bm2") 
		|| temp.endsWith(".png") 
		|| temp.endsWith(".gif") 
		|| temp.endsWith(".jpg") || temp.endsWith(".jpeg")
		)
		{
		return false;	
		}
		return true;
	}

}
