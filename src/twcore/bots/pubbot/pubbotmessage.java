package twcore.bots.pubbot;

import java.util.ArrayList;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.util.ipc.IPCMessage;

public class pubbotmessage extends PubBotModule
{
  private String botName;
  Queue<String> checkQueue;

  public void initializeModule()
  {
  	checkQueue = new Queue<String>();
    botName = m_botAction.getBotName();
  }

  public void requestEvents(EventRequester eventRequester)
  {
    eventRequester.request(EventRequester.MESSAGE);
  }

  public void handleEvent(Message event)
  {
    String message = event.getMessage();
    int messageType = event.getMessageType();

    if(messageType == Message.ARENA_MESSAGE)
    	if(message.toLowerCase().startsWith("time:"))
    	{
    		String name = checkQueue.next();
    		if(name == null) return; 
    		String pieces[] = message.split(":");
    		int mins = Integer.parseInt(pieces[3]);
    		if(mins < 1)
    		//	m_botAction.sendSmartPrivateMessage("MessageBot", "!login " + name);
    			m_botAction.ipcTransmit("messages", new IPCMessage(name, "MessageBot"));
    	}
  }


  public void gotNotRecordedCmd(String argString)
  {
  	checkQueue.add(argString.toLowerCase());
  }

  public void handleBotIPC(String botSender, String recipient, String sender, String message)
  {
    String command = message.toLowerCase();

    try
    {
      if(command.startsWith("notrecorded "))
        gotNotRecordedCmd(message.substring(12));
    }
    catch(Exception e)
    {
      m_botAction.sendChatMessage(e.getMessage());
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
    String sender = ipcMessage.getSender();
    String botSender = event.getSenderName();

    try
    {
      if(recipient == null || recipient.equals(botName))
      {
        if(sender == null)
          handleBotIPC(botSender, recipient, sender, message);
      }
    }
    catch(Exception e)
    {
      m_botAction.sendChatMessage(e.getMessage());
    }
  }

  public void cancel()
  {
  }
}

class Queue<T>
{
	ArrayList<T> objects;

	public Queue()
	{
		objects = new ArrayList<T>();
	}

	public T next()
	{
	    T obj = null;

	    try {
	        obj = objects.get(0);
	        objects.remove(0);
	    } catch(IndexOutOfBoundsException ioobe) {}
		return obj;
	}

	public void add(T obj)
	{
		objects.add(obj);
	}

	public int size()
	{
		return objects.size();
	}
}