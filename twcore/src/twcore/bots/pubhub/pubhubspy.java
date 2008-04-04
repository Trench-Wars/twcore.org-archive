package twcore.bots.pubhub;

import java.util.HashMap;
import java.util.HashSet;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.ipc.IPCMessage;

public class pubhubspy extends PubBotModule
{
  public static final int IGNORE_TIME = 10;

  private HashSet<String> watchList;
  private HashMap<String, IgnoreTask> ignoreList;
  private String botName;

  /**
   * This method initializes the pubhubspy module.  It is called after
   * m_botAction has been initialized.
   */

  public void initializeModule()
  {
    watchList = new HashSet<String>();
    ignoreList = new HashMap<String, IgnoreTask>();
    botName = m_botAction.getBotName();
  }
  
  @Override
  public void requestEvents(EventRequester eventRequester) {
      eventRequester.request(EventRequester.MESSAGE);
  }
  
  public void cancel() {
      
  }

  public void handleEvent(Message event)
  {
    String sender = event.getMessager();
    String message = event.getMessage();
    int messageType = event.getMessageType();

    if(messageType == Message.CHAT_MESSAGE) {
        
        // !help
        if(message.startsWith("!help ")) {
            String[] help = { 
                    "SPY CHAT COMMANDS:",
                    "!watch <player>                - Relays any chat messages from <player> to chat",
                    "!watchlist                     - Shows players being !watch'ed",
                    "!ignore <player>               - Changes ?cheater notification of <player> on racist words to a chat notification only",
                    "!ignorelist                    - Shows players being !ignore'ed"
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