package net.SubSpace.SSDR.Main;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class ThreadCheck extends Thread implements Runnable {

	public static boolean isConnectedTo;
	public static boolean kill = false;
	private String IPAddress;
	private String name;
    long start = System.currentTimeMillis();
    int bytesRead;
    int current = 0;
    int portServer;
    boolean forced;

	public ThreadCheck(String ZoneName,String IP, int port, boolean force)
	{
		this.forced = force;
		this.name = ZoneName;
		this.IPAddress = IP;
		this.portServer = port;
	}
	
	@Override
	public void run() {
     try {
		running();
	} catch (IOException e) {
		System.out.println("Could not run " + name + "'s update check.");
		ThreadCheckForUpdates.wait = false;
		//this.interrupt();
		kill = true;
		e.printStackTrace();
		setPostHoverMessage();
	}   
	}
	
	@SuppressWarnings("unused")
	public void running() throws IOException
	{
		Socket kkSocket = null;
        PrintWriter out = null;
        BufferedReader in = null;
        if(forced)
        {
        	ThreadIcon.sendMessageToIcon(name, "Connecting to " + name + "'s repository", ThreadIcon.info);
        }
        try {
        	System.out.println("KKSOCKET CONNECTION: IP:" + IPAddress + " PORT:" + this.portServer);
            kkSocket = new Socket(this.IPAddress, this.portServer);
        } catch (UnknownHostException e) {
        	 if(forced)
             {
             	ThreadIcon.sendMessageToIcon(name, "Connection to " + name + "'s repository failed. The IP address for the zone could not be reached. Consider contacting the zone's administrator for more information about this issue.", ThreadIcon.error);
             }
     		setPostHoverMessage();
    		System.out.println("Could not run " + name + "'s update check. (Connection Failed)");
    		ThreadCheckForUpdates.wait = false;
    		kill = true;
    		//this.interrupt();
    		return;
        } catch (IOException e) {
        	 if(forced)
             {
             	ThreadIcon.sendMessageToIcon(name, "Connection to " + name + "'s repository was refused. This happens when the repository server is not up. Some zones may not use this software for their zone, and throws this error.", ThreadIcon.error);
             }
     		setPostHoverMessage();
        	System.out.println("Could not run " + name + "'s update check. (Connection Refused: " + IPAddress + ")");
    		ThreadCheckForUpdates.wait = false;
    		kill = true;
    		//this.interrupt();
    		return;
		}
        try {
        	if(kkSocket != null)
        	{
        		out = new PrintWriter(kkSocket.getOutputStream(), true);        		
        	}
        	else
        	{
        		ThreadCheckForUpdates.wait = false;
        		kill = true;
        		//this.interrupt();
        		setPostHoverMessage();
        		return;
        	}
        } catch (IOException e1) {
    		ThreadCheckForUpdates.wait = false;
    		kill = true;
    		//this.interrupt();
    		setPostHoverMessage();
    		return;
        }
            try {
            	if(kkSocket != null)
            	{    
            		in = new BufferedReader(new InputStreamReader(kkSocket.getInputStream()));
            	}
            	else
            	{
            		ThreadCheckForUpdates.wait = false;
            		kill = true;
            		//this.interrupt();
            		setPostHoverMessage();
            		return;
            	}
			} catch (IOException e2) {
	    		ThreadCheckForUpdates.wait = false;
	    		kill = true;
	    		//this.interrupt();
	    		setPostHoverMessage();
	    		return;
			}
        
 
        //BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        String fromServer;
        	boolean Ready = false;
        	byte[] fileData = null;
        	boolean Dumptime = false;
        	boolean StayConnected = true;
        	String FileName = null;
        	File file = null;
			int bytes;
    		ThreadIcon.isServerReachable = true;
    		ThreadIcon.isDownloading = true;
    		 if(this.forced)
    	        {
	    		 	System.out.println("DEBUG: Entering the loop.");
    	        	ThreadIcon.sendMessageToIcon(this.name, "Downloading from " + this.name + "'s repository.", ThreadIcon.info);
    	        }
    		 out.flush();
					while (StayConnected) {
						fromServer = in.readLine();
					   // System.out.println("Server: " + fromServer);
					    if (fromServer.equals("Done"))
					    {
					    	 if(forced)
					         {
					    		 System.out.println("DEBUG: Downloading has been finished.");
					         	ThreadIcon.sendMessageToIcon(this.name, "Downloads from " + this.name + "'s repository has been completed.", ThreadIcon.info);
					         }
					 		setPostHoverMessage();
					    	System.out.println("Downloading is all done.");
					    	out.close();
				        	in.close();
				        	kkSocket.close();
					    	StayConnected = false;		    	
					    }
					    if (fromServer.equals("Ready"))
					    {
			    		 	System.out.println("DEBUG: Server has responded with ready.");
					    	//System.out.println("Stage 1.");
					    	//System.out.println("Client: Read");
					    	out.println("Read");
					    	Ready = true;
					    }
					    else if(fromServer.endsWith(".lvl") || fromServer.endsWith(".lvz") || fromServer.endsWith(".LVL") || fromServer.endsWith(".LVZ"))
					    {
			    		 	System.out.println("DEBUG: Server has responded with file name of:" + fromServer);
					    	//System.out.println("Stage 2.");
					    	//System.out.println("Directory Link: " + SSDR.Directory + "Zones/" + name + "/" + fromServer);
					    	file = new File(SSDR.Directory + "/Zones/" + this.name + "/" + (FileName = fromServer)); 
					    	if(file.exists())
					    	{			    		
					    		out.println(file.length());
					    	}
					    	else
					    	{
					    		out.println(0);
					    	}
					    	Ready = false;
					    }
					    else if(fromServer.startsWith("FILE SIZE:"))
					    {
			    		 	System.out.println("DEBUG: Server has responded with filesize that of:" + fromServer);
					    	//System.out.println("Stage 3.");
					    	String[] temp = fromServer.split(":");
					    	int sizeOfFile = Integer.parseInt(temp[1]);
					    	fileData = new byte[sizeOfFile];
					    	bytes = sizeOfFile;
					    	out.println("FILE SIZE:" + file.length());
					    	Dumptime = true;
					    }
					    else if(fromServer.equals("PREPARE"))
					    {
			    		 	System.out.println("DEBUG: Server has prompted preparations for downloading the file. The client is now waiting for a stream of bytes...");
					    	out.println("GO");
					    	out.flush();
					    	Dumptime = false;
								fileData = readBytes(kkSocket);

								if(fileData == null)
								{
									System.out.println("Data is null...");
								}
								//System.out.println("Stage 4.");
								out.flush();
				    		 	System.out.println("DEBUG: Saving file:" + FileName);
								FileOutputStream fos = new FileOutputStream(SSDR.Directory + "/Zones/" + name + "/" + FileName);
								
								fos.write(fileData);
								fos.close(); 
								System.out.println(FileName + " has been saved.");
								out.flush(); 
				    		 	System.out.println("DEBUG: Client has responded with Done (Done from client means ready for next interval).");
								out.println("Done");
								fileData = null;
								try {
									Thread.sleep(10L);
								} catch (InterruptedException e) {
						    		ThreadCheckForUpdates.wait = false;
						    		kill = true;
						    		//this.interrupt();
						    		return;
								}
					    }
					}
					out.flush();
        	out.close();
        	in.close();
        	kkSocket.close();
    		ThreadIcon.isDownloading = false;
    		setPostHoverMessage();

	}
	
	public byte[] getFileData(InputStream in) {
	    try {
	        ByteArrayOutputStream out = new ByteArrayOutputStream();
	        int data;
	        while ((data = in.read())>=0) {
	            out.write(data);
	        }
	        return out.toByteArray();
	    } catch(IOException ioe) {
			this.interrupt();
	    }
	    return new byte[]{};
	}


	
	public static void readInputStreamToFile(InputStream is, FileOutputStream fout,
	        long size, int bufferSize) throws Exception
	{
	    byte[] buffer = new byte[bufferSize];
	    long curRead = 0;
	    long totalRead = 0;
	    long sizeToRead = size;
	    while(totalRead < sizeToRead)
	    {
	        if(totalRead + buffer.length <= sizeToRead)
	        {
	            curRead = is.read(buffer);
	        }
	        else
	        {
	            curRead = is.read(buffer, 0, (int)(sizeToRead - totalRead));
	        }
	        totalRead = totalRead + curRead;
	        fout.write(buffer, 0, (int) curRead);
	    }
	}

	public void setPostHoverMessage()
	{
		if(ThreadCheckForUpdates.runChecks)
		{
			ThreadIcon.setHoverMessage("(Force an update through " + System.getProperty("line.separator")+ "the list of zones by right-clicking" + System.getProperty("line.separator")+ " this icon and going " + System.getProperty("line.separator")+ "Check Zone>\"Zone Name\".)");
		}
		else
		{
			ThreadIcon.setHoverMessage("This App is Set to" + System.getProperty("line.separator") + "\"NOT UPDATING\" Status." + System.getProperty("line.separator") + "To Start Auto Updates," + System.getProperty("line.separator") + "Go to \"Auto Updates\"." + System.getProperty("line.separator") +"You can still force updates.");
		}
		ThreadIcon.isServerReachable = true;
		ThreadIcon.isDownloading = false;
	}
	
	public byte[] readBytes(Socket socket) throws IOException {
	    // Again, probably better to store these objects references in the support class
	    InputStream in = socket.getInputStream();
	    DataInputStream dis = new DataInputStream(in);

	    int len = dis.readInt();
	    byte[] data = new byte[len];
	    if (len > 0) {
	        dis.readFully(data);
	    }
	    return data;
	}

	
}
