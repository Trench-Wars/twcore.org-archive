package twcore.bots.pubbot;

import java.util.*;
import twcore.core.*;
import twcore.misc.pubcommon.*;

public class pubbotmessage extends PubBotModule
{
  private String botName;
  Queue checkQueue;
  
  public void initializeModule()
  {
  	checkQueue = new Queue();
    botName = m_botAction.getBotName();
  }

  public void requestEvents(EventRequester eventRequester)
  {
    eventRequester.request(EventRequester.MESSAGE);
    eventRequester.request(EventRequester.PLAYER_ENTERED);
  }

  public void handleEvent(Message event)
  {
    String message = event.getMessage();
    int messageType = event.getMessageType();

    if(messageType == Message.ARENA_MESSAGE)
    	if(message.toLowerCase().startsWith("time:"))
    	{
    		String name = (String)checkQueue.next();
    		String pieces[] = message.split(":");
    		int mins = Integer.parseInt(pieces[3]);
    		if(mins < 1)
    			m_botAction.ipcTransmit("messages", new IPCMessage(name, "MessageBot"));
    	}
  }

  /** Checks for a recently joined player so the MessageBot
   *  can check if they have new messages.
   */
  public void handleEvent(PlayerEntered event)
  {
  	String name = m_botAction.getPlayerName(event.getPlayerID()).toLowerCase();
  	checkQueue.add(name);
  	m_botAction.sendUnfilteredPrivateMessage(name, "*info");
  }

  public void cancel()
  {
  }
}

class Queue
{
	ArrayList objects;
	
	public Queue()
	{
		objects = new ArrayList();
	}
	
	public Object next()
	{
		Object obj = objects.get(0);
		objects.remove(0);
		return obj;
	}
	
	public void add(Object obj)
	{
		objects.add(obj);
	}
	
	public int size()
	{
		return objects.size();
	}
}