package twcore.bots.pubhub;

import java.util.HashMap;
import java.util.HashSet;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Iterator;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.ipc.IPCMessage;

public class pubhubspy extends PubBotModule
{
  public static final int IGNORE_TIME = 10;

  private HashSet<String> watchList;
  private TreeMap<String, ArrayList<String>> pWatchList;
  private HashMap<String, IgnoreTask> ignoreList;
  private HashSet<String> away;
  private String botName;

  /**
   * This method initializes the pubhubspy module.  It is called after
   * m_botAction has been initialized.
   */

  public void initializeModule()
  {
    watchList = new HashSet<String>();
    pWatchList = new TreeMap<String, ArrayList<String>>();
    ignoreList = new HashMap<String, IgnoreTask>();
    away = new HashSet<String>();
    botName = m_botAction.getBotName();
  }
  
  @Override
  public void requestEvents(EventRequester eventRequester) {
      eventRequester.request(EventRequester.MESSAGE);
  }
  
  public void cancel() {
      // Cancel all current running IgnoreTasks
      for(TimerTask ignoreTask:ignoreList.values()) {
          m_botAction.cancelTask(ignoreTask);
      }
      watchList.clear();
      pWatchList.clear();
      ignoreList.clear();
      away.clear();
  }

  public void handleEvent(Message event)
  {
    String sender = event.getMessager() == null ? m_botAction.getPlayerName(event.getPlayerID()) : event.getMessager();
    String message = event.getMessage();
    int messageType = event.getMessageType();

    if(messageType == Message.CHAT_MESSAGE){
    	if(!opList.isSmod(sender))return;
    	if(message.startsWith("!pwatch ")) {
    		String playerName = message.substring(8).toLowerCase();

            if(pWatchList.containsKey(playerName) && pWatchList.get(playerName).contains(sender)){
            	ArrayList<String> staffNames = pWatchList.get(playerName);
            	if(staffNames.size() == 1)
            		pWatchList.remove(playerName);
            	else{
            		staffNames.remove(sender);
            		pWatchList.remove(playerName);
            		pWatchList.put(playerName, staffNames);
            	}
                // Stop watching the player
                m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("pwatch " + playerName + ":" + sender));
                m_botAction.sendSmartPrivateMessage(sender, "Watching player: " + playerName + " disabled.");
            } else if(pWatchList.containsKey(playerName) && !pWatchList.get(playerName).contains(sender)){
            	ArrayList<String> staffNames = pWatchList.get(playerName);
            	staffNames.add(sender);
            	pWatchList.remove(playerName);
            	pWatchList.put(playerName, staffNames);
            	// Start watching the player
            	m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("pwatch " + playerName + ":" + sender));
                m_botAction.sendSmartPrivateMessage(sender, "Watching player: " + playerName + " enabled.");
            }else {
            	ArrayList<String> staffNames = new ArrayList<String>();
            	staffNames.add(sender);
                pWatchList.put(playerName, staffNames);
            	// Start watching the player
                m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("pwatch " + playerName + ":" + sender));
                m_botAction.sendSmartPrivateMessage(sender, "Watching player: " + playerName + " enabled.");
            }
    	}
    	else if(message.equalsIgnoreCase("!pwatchlist")) {
        	m_botAction.sendSmartPrivateMessage(sender, "Current players on !pwatch:");
            Iterator<String> i = pWatchList.keySet().iterator();
            while(i.hasNext()){
            	String n = i.next();
            	ArrayList<String> staffNames = pWatchList.get(n);
            	Iterator<String> it = staffNames.iterator();
            	while(it.hasNext()){
            		String s = it.next();
            		if(s.equalsIgnoreCase(sender))
            			m_botAction.sendSmartPrivateMessage(sender, n);
            	}
            }}
            else if(message.startsWith("!impersonate ")) {
                String playerName = message.substring(13).toLowerCase();

                if(away.contains(playerName)){
                       
                        away.remove(playerName);
                        m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("saway " + playerName));
                        m_botAction.sendChatMessage("I have stopped ignoring "+playerName+" from entering Trench Wars.");
                    } else {
                       
                        away.add(playerName);
                        m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("away " + playerName));
                        m_botAction.sendChatMessage("I will be ignoring "+playerName+" when entering Trench Wars.");
                    }
                }
            }

        
    
        
    	
    
    
    if(messageType == Message.CHAT_MESSAGE) {
        
        // !help
        if(message.startsWith("!help")) {
            String[] help = { 
                    "SPY CHAT COMMANDS:",
                    "!watch <player>                - Relays any chat messages from <player> to chat",
                    "!watchlist                     - Shows players being !watch'ed",
                    "!ignore <player>               - Changes ?cheater notification of <player> on racist words to a chat notification only",
                    "!ignorelist                    - Shows players being !ignore'ed",
                    "!pwatch <player>               - Relays any chat messages from <player> to you privately",
                    "!pwatchlist                    - Shows players being !pwatch'ed",
                    "!impersonate                   - Stops the impersonation message on player",
                    "!list                          - Shows people being ignored on ^"
            };
            m_botAction.smartPrivateMessageSpam(sender, help);
        }
        
        // !watch
        if(message.startsWith("!watch ")) {
            String playerName = message.substring(7).toLowerCase();

            if(watchList.contains(playerName)) {
                // Stop watching the player
                watchList.remove(playerName);
                m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("unwatch " + playerName));
                m_botAction.sendChatMessage("Watching player: " + playerName + " disabled.");
            } else {
                // Start watching the player
                watchList.add(playerName);
                m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("watch " + playerName));
                m_botAction.sendChatMessage("Watching player: " + playerName + " enabled.");
            }
        }
        
        // !watchlist
        if(message.equals("!watchlist")) {
            if(watchList.isEmpty())
                m_botAction.sendChatMessage("Not currently watching any players.");
            
            m_botAction.sendChatMessage("Current players on !watch:");
            for(String watchedPlayer:watchList) {
                m_botAction.sendChatMessage(" "+watchedPlayer);
            }
        }
        
        else if(message.equalsIgnoreCase("!list")){
            if(away.isEmpty())
                m_botAction.sendChatMessage("Nobody on my ignore list, I want to add you tho");
            else {
            m_botAction.sendChatMessage("I'm ignoring:");
            for(String ignore:away) {
                m_botAction.sendChatMessage(" "+ignore);
            }
        }
        
        
        // !ignore
        if(message.startsWith("!ignore ")) {
            String playerName = message.substring(8).toLowerCase();
            
            if(ignoreList.containsKey(playerName)) {
                IgnoreTask ignoreTask = ignoreList.get(playerName);
                m_botAction.cancelTask(ignoreTask);
                m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("unignore " + playerName));
                m_botAction.sendChatMessage("Listening to racist words from " + playerName + ".");
                ignoreList.remove(playerName);
            } else {
                m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("ignore " + playerName));
                IgnoreTask ignoreTask = new IgnoreTask(playerName);
                ignoreList.put(playerName, ignoreTask);
                m_botAction.scheduleTask(ignoreTask, IGNORE_TIME * 60 * 1000);
                m_botAction.sendChatMessage("Ignoring " + playerName + " for " + IGNORE_TIME + " minutes.");
            }
        }
        
        // !ignorelist
        if(message.equals("!ignorelist")) {
            if(ignoreList.isEmpty())
                m_botAction.sendChatMessage("Not currently ignoring any players.");

            m_botAction.sendChatMessage("Current players on !ignore:");
            
            for(String ignoredPlayer:ignoreList.keySet()) {
                m_botAction.sendChatMessage(ignoredPlayer);
            }
        }
    }
  }
  }

  /**
   * This method handles an InterProcessEvent.
   *
   * @param event is the InterProcessEvent to handle.
   */

  public void handleEvent(InterProcessEvent event)
  {
	  // If the event.getObject() is anything else then the IPCMessage (pubbotchatIPC f.ex) then return
	  if(event.getObject() instanceof IPCMessage == false) {
		  return;
	  }

    IPCMessage ipcMessage = (IPCMessage) event.getObject();
    String message = ipcMessage.getMessage();
    String recipient = ipcMessage.getRecipient();
    String botSender = event.getSenderName();

    if(recipient == null || recipient.equals(botName)) {
        
        if(message.equals("loadedspy")) {
            // notify the new pubbot of currently watched players
            for(String watchedPlayer:watchList) {
                m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("watch " + watchedPlayer, botSender));
            }
            Iterator<String> i = pWatchList.keySet().iterator();
            while(i.hasNext()){
            	String n = i.next();
            	ArrayList<String> staffNames = pWatchList.get(n);
            	Iterator<String> it = staffNames.iterator();
            	while(it.hasNext())
            		m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("pwatch " + n + ":" + i.next(), botSender));
            }
        }
    }
  }

  private class IgnoreTask extends TimerTask {
    private String playerName;

    public IgnoreTask(String playerName)
    {
      this.playerName = playerName;
    }

    public void run() {
      m_botAction.ipcTransmit(getIPCChannel(), new IPCMessage("unignore " + playerName));
      m_botAction.sendChatMessage("The !ignore on "+playerName+" expired. Listening to racist words from " + playerName + ".");
    }
  }
}