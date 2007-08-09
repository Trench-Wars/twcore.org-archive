package twcore.bots.pubhub;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TimerTask;
import java.util.Vector;
import java.util.Map.Entry;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCChatMessage;
import twcore.core.util.ipc.IPCMessage;

/**
 * Pubhubchat module currently uses a ServerSocket to provide the chatlines to the website.
 * I've chosen for this approach because the database couldn't handle the load when this module
 * used the database to store the chatlines for the website. (Revision 1716)
 * 
 * @author Koen Werdler
 */
public class pubhubchat extends PubBotModule {

	protected HashMap<String, Vector<String>> arenaChat = new HashMap<String, Vector<String>>(10);
	protected HashMap<String, HashMap<Integer,Player>> arenaPlayer = new HashMap<String, HashMap<Integer,Player>>();
	private HashMap<String, Date> arenaLastUpdate = new HashMap<String, Date>(10);
	private TimerTask cleanup;
	
	private final String seperator = ":";
	
	private final int maxSavedLines = 20;
	private final int ARENA_PURGE_TIME = (15 * 60 * 1000);
	
	private ChatServer chatServer;
	
	
	
	public pubhubchat() {
		chatServer = new ChatServer(this);
	}
	
	@Override
	public void cancel() {
		chatServer.die();
		m_botAction.cancelTask(cleanup);
	}

	@Override
	public void initializeModule() {
		
		cleanup = new TimerTask() {
			public void run() {
				cleanupArenas();
			}
		};
		
		int one_minute = 60 * 1000;
		m_botAction.scheduleTaskAtFixedRate(cleanup, 5 * one_minute, one_minute);
	}

	@Override
	public void requestEvents(EventRequester eventRequester) {
	}

	@Override
	public void handleEvent(InterProcessEvent event) {
		if(event.getChannel().equals(pubhub.IPCCHAT) && event.getObject() instanceof IPCChatMessage) {
			IPCChatMessage ipc = (IPCChatMessage)event.getObject();
			String arena = Tools.addSlashesToString(ipc.getArena());
			String message = Tools.addSlashesToString(ipc.getMessage());
			String sender = (ipc.getMessageType() == Message.ARENA_MESSAGE) ? null : ipc.getSender();
			
			Vector<String> chatlines;
			int chatLineId=0;
			
			// Check if the HashSet for this arena already exists, if not, create it
			if(arenaChat.containsKey(arena)) {
				chatlines = arenaChat.get(arena);
				
				// Gets the last element of chatlines and extracts the ID of it
				chatLineId = Integer.parseInt(chatlines.get(0).split(seperator)[0]);
				
			} else {	// Create the HashSet and put it to the HashMap
				chatlines = new Vector<String>(maxSavedLines);
				arenaChat.put(arena, chatlines);
				chatLineId=0;
			}
			
			// Save
			chatLineId++;
			chatlines.add(0, chatLineId + seperator + ipc.getMessageType() + seperator + sender + seperator + message);
			chatlines.setSize(maxSavedLines);
			arenaLastUpdate.put(arena, new Date());
			
		} else if(event.getChannel().equals(pubhub.IPCCHAT) && event.getObject() instanceof Player) {
			Player player = (Player)event.getObject();
			//player.
			
		} else if(event.getChannel().equals(pubhub.IPCCHANNEL) && event.getObject() instanceof IPCMessage) {
			IPCMessage msg = (IPCMessage)event.getObject();
			if(msg.getMessage().equals("die")) {
				this.cancel();
			}
			
		}
	}
	
	public void cleanupArenas() {
		// Cycle all the arena's and check if the last update is longer then 15 minutes ago
		// if yes then delete that arena
		
		Set<Entry<String, Date>> arenas = arenaLastUpdate.entrySet();
		Iterator<Entry<String, Date>> it = arenas.iterator();
		
		while(it.hasNext()) {
			Entry<String, Date> mapEntry = it.next();
			Date lastUpdate = mapEntry.getValue();
			String arena = mapEntry.getKey();
			
			if((new Date().getTime() - lastUpdate.getTime()) > ARENA_PURGE_TIME) {
				// Arena has been idle too long, delete it.
				arenaChat.remove(arena);
				arenaLastUpdate.remove(arena);
			}
		}
		
	}
	
	
}

/**
 * This class creates a ServerSocket server which deals with requests from PHP for the chat
 * 
 * Protocol:
 * PHP -> Server
 * 	- getArenaList
 *     Returns the available arenas from the bot module pubhubchat
 *  - getChatMessages <arena> [id]
 *     Returns the available chat lines from the specified arena (mandatory) or only the new chat lines
 *     from the [id] (optional).
 * 
 * @author Maverick
 */
class ChatServer extends Thread {

    private pubhubchat			bot; 
    private ServerSocket        servsock;
    private boolean             running = true;
    
    private int 				port = 7000;
    private int 				timeout = (2 * 1000); 	// 2 seconds
    private boolean				debug = true;

    /** Creates a new instance of RadioStatusServer */
    public ChatServer( pubhubchat bot) {
    	
    	this.bot = bot;

    	try {
    		servsock = new ServerSocket( port );
    		servsock.setSoTimeout( timeout );
    	} catch(IOException ioe) {
    		Tools.printLog("IOException occured opening port ("+port+"):"+ioe.getMessage());
    		Tools.printStackTrace(ioe);
    	}
    	
        Tools.printLog("pubhubchat SocketServer server started on port "+port);
        start();
    }

    public void run(){

        while( running ){

            try{
                Socket socket = servsock.accept();
                socket.setSoTimeout(timeout);
                DataOutputStream out = new DataOutputStream( socket.getOutputStream() );
                BufferedReader in = new BufferedReader(new InputStreamReader( socket.getInputStream() ));
                String response = "";
                
                if(debug) {
                	Tools.printLog("pubhubchat SocketServer: Client connected ("+socket.getInetAddress()+":"+socket.getPort()+")"); 
                }
                
                String command = in.readLine();
                
                if(command != null) 
                	command = command.trim();
                
                if(debug) {
                	Tools.printLog("pubhubchat SocketServer: -> "+command); 
                }
                
                if(command != null && command.equals("getArenaList")) {
                	if(debug) Tools.printLog("command recognized, responding..."); 
                	
                	Iterator<String> iter = bot.arenaChat.keySet().iterator();
                	
                	while(iter.hasNext()) {
                		response += iter.next().trim() + "\n";
                	}
                } else if(command != null && command.startsWith("getChatMessages ")) {
                	if(debug) Tools.printLog("command recognized, responding...");
                	
                	String arena = command.substring(16);
                	Vector<String> chatLines = bot.arenaChat.get(arena);
                	int id = 0;
                	                	
                	if(arena != null && arena.length()>1 && arena.contains(":")) {
                		String _id = arena.split(":")[1];
                		if(Tools.isAllDigits(_id)) {
                			id = Integer.parseInt(_id);
                		}
                		arena = arena.split(":")[0];
                	}
                	
                	if(bot.arenaChat.containsKey(arena)) {
                		for(int i = 0 ; i < chatLines.size(); i++) {
                			if(chatLines.get(i) != null) {
                				String chatLine = chatLines.get(i);
                				String currentId = chatLine.split(":")[1];
                				
                				if(Integer.parseInt(currentId) > id) {
                					response += chatLines.get(i) + "\n";
                				}
                			}
                		}
                	} else {
                		response = "ERROR:Selected arena doesn't exist";
                	}
                }
                
                if(response != null) {
                	out.writeUTF(response);
                }
                
                socket.close();
                
                if(debug) {
                	Tools.printLog("pubhubchat SocketServer: Client disconnected"); 
                }
                
            } catch( SocketTimeoutException ste ){

            } catch( IOException ioe ){
            	Tools.printLog("pubhubchat SocketServer TCP Communication Error: "+ioe.getMessage());
            }
        }
    }
    
    public void die(){

        Tools.printLog("pubhubchat SocketServer: Attempting to kill all connections..." );
        running = false;
        try{
            servsock.close();
        } catch( IOException ioe ){
        	Tools.printLog("pubhubchat SocketServer: Closed the block accept thread." );
        }
    }
}

