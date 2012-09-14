package net.SubSpace.SSDR.Server.Main;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
 
public class ServerThread extends Thread implements Runnable{
	String inputLine;
	Socket passedSocket;
	List<File> LVLlist;
	List<File> LVZlist;
		
	public ServerThread(Socket servSock)
	{
		this.LVLlist = new ArrayList<File>();
		this.LVZlist = new ArrayList<File>();
		this.LVLlist = cloneList(Server.LVLlist);
		this.LVZlist = cloneList(Server.LVZlist);
		//this.LVLlist = Server.LVLlist;
		//this.LVZlist = Server.LVZlist;
    	this.passedSocket = servSock;
	}
    public void sendBytes(byte[] myByteArray, Socket socket) throws IOException {
        sendBytes(myByteArray, 0, myByteArray.length,socket);
    }

    public void sendBytes(byte[] myByteArray, int start, int len, Socket socket) throws IOException {
        if (len < 0)
            throw new IllegalArgumentException("Negative length not allowed");
        if (start < 0 || start >= myByteArray.length)
            throw new IndexOutOfBoundsException("Out of bounds: " + start);
        OutputStream out = socket.getOutputStream(); 
        DataOutputStream dos = new DataOutputStream(out);

        dos.writeInt(len);
        if (len > 0) {
            dos.write(myByteArray, start, len);
        }
    }

	@Override
	public void run() 
	{
		try {
			System.out.println("Starting thread for updating...["  + passedSocket.getInetAddress() + "]");
			running();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void running() throws IOException
	{
		ServerProtocol p = new ServerProtocol();
		Socket clientSocket = this.passedSocket;
		PrintWriter out = null;
		BufferedReader in = null;
		try
		{
        //ServerSocket serverSocket = null;
        
        this.passedSocket = null;
        //clientSocket = passedSocket.accept();
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(
                new InputStreamReader(
                clientSocket.getInputStream()));
        out.println("Ready");
        	
        	boolean hasRead = false;
        	boolean StayConnected = true;
        	File file = null;	
			byte[] fileInBytes = null;
			boolean passLVL = false;
			boolean passLVZ = false;
			out.flush();
			Server.sendMessage(ServerGui.MESSAGE_CONNECTION,"Connected to:" + clientSocket.getInetAddress());
			System.out.println("DEBUG: Entering loop with client...");
			while (StayConnected) 
			{
				inputLine = in.readLine();
				System.out.println("DEBUG: ClientInput:" + inputLine);
				//System.out.println(inputLine);
				
				if(inputLine.equals("Read") && hasRead == false)
				{
					System.out.println("DEBUG: Client responded with \"Read\"...");
					//Server.sendMessage("Pass: Check file.");
					if(!passLVL)
					{
						//Server.sendMessage("Pass: IN LVL check.");
						if(this.LVLlist.isEmpty() || LVLlist == null)
						{
							System.out.println("DEBUG: LVL list is now empty...");
							//Server.sendMessage("Pass:LVL Done.");
							passLVL = true;
							//out.println("Done");
							//break;
						}
						else
						{	
							//Server.sendMessage("Pass: LVL Check file name.");
							file = this.LVLlist.get(0);
							//GUI.send_message_to_window("Checking LVL File: " + file.getName());
							out.println((file = (File)this.LVLlist.get(0)).getName());
							this.LVLlist.remove(0);
							hasRead = true;
						}
					}
					if(!passLVZ && passLVL)
					{
						//Server.sendMessage("Pass: LVZ Check file.");
						if(this.LVZlist.isEmpty() || this.LVZlist == null)
						{
							System.out.println("DEBUG: LVZ list is now empty...");

							//Server.sendMessage("Pass: LVZ Done.");
							passLVZ = true;
						}
						else
						{				
							//Server.sendMessage("Pass: LVZ Check file name.");
							file = this.LVZlist.get(0);
							//GUI.send_message_to_window("Checking LVZ File: " + file.getName());
							out.println((file = (File)this.LVZlist.get(0)).getName());
							this.LVZlist.remove(0);
							hasRead = true;
						}
					}
					if(passLVZ && passLVL)
					{
						System.out.println("DEBUG: LVL/LVZ list is now empty... breaking connection.");

						StayConnected = false;
						out.println("Done");
						out.println("Ready");
						break;
					}
				}
				else if(hasRead)
				{
					System.out.println("DEBUG: hasread is true. sending File Size.");

					out.println("FILE SIZE:" + file.length());
					hasRead = false;
				}
				else if(inputLine.equals("Done"))
				{
					System.out.println("DEBUG: client done with inteval, readying...");

					this.inputLine = null;
			        out.println("Ready");
				}

				else if(inputLine.startsWith("FILE"))// Returns long value for processing file. After this is ran it should return a read.
				{
					System.out.println("DEBUG: Client requested file...");

					String temp = inputLine;
					String[] temp2 = temp.split(":");
					long userFileSize = Long.parseLong(temp2[1]);
					if(p.shouldFileBeUpdated(userFileSize, file))
					{
						System.out.println("DEBUG: Preparing client for file...");

						out.println("PREPARE");
					}
					else
					{
						System.out.println("DEBUG: File size matches, restarting to next interval...");
						out.println("Ready");
						try {
							Thread.sleep(10L);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				else if(this.inputLine.equals("GO"))
				{	
					System.out.println("DEBUG: Sending client file....");
					fileInBytes = p.sendFile(file);
					/*out.flush();
					DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream());
					dout.write(fileInBytes, 0, fileInBytes.length);
					
					dout.flush();
					//System.out.println("Server is waiting on response of Client.");
					out.flush();
					fileInBytes = null; */
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					DataOutputStream w = new DataOutputStream(baos);

					/*w.writeInt(100);
					w.write(fileInBytes);*/
					sendBytes(fileInBytes,clientSocket);
					w.flush();

					System.out.println("DEBUG:Preparing client with \"Ready\"...");
					out.println("Ready");
					try {
						Thread.sleep(10L);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
				}
			}
			}
			finally
			{	
				Server.sendMessage(ServerGui.MESSAGE_CONNECTION,"Disconnected from:" + clientSocket.getInetAddress());
				out.print("uhh...");
				 out.close();
			     in.close();
			        clientSocket.close();
			       //serverSocket.close();
			}
	}
	public static List<File> cloneList(List<File> list) {
	    List<File> clone = new ArrayList<File>(list.size());
	    for(int j = 0;j < list.size();j++)
	    {
	    	clone.add(list.get(j));
	    	//clone.add(item.clone());	    	
	    }
	    return clone;
	}
}	
   

