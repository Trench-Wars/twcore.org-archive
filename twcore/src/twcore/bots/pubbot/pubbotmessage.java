package twcore.bots.pubbot;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.PlayerEntered;
import twcore.core.util.ipc.IPCMessage;

public class pubbotmessage extends PubBotModule {
	
	private String botName;
	private Queue<String> checkQueue;
	Timer queueTimer;
	private long CHECKTIME = 2; // Time in seconds how fast names are passed to
								// MessageBot
	private long START_IDLE = 10; // Time in seconds how long bot waits before
									// passing names to MessageBot after module
									// loaded
	private queueTimerTask tTask;

	public void initializeModule() {
		checkQueue = new Queue<String>();
		botName = m_botAction.getBotName();
		tTask = new queueTimerTask();
		queueTimer = new Timer();
		queueTimer.schedule(tTask, START_IDLE * 1000);
	}

	public void requestEvents(EventRequester eventRequester) {
		// eventRequester.request(EventRequester.MESSAGE);
		eventRequester.request(EventRequester.PLAYER_ENTERED);
	}

	/*
	public void handleEvent(Message event) {
		
		String message = event.getMessage();
		int messageType = event.getMessageType();

		if (messageType == Message.ARENA_MESSAGE)
			if (message.toLowerCase().startsWith("time:")) {
				String name = checkQueue.next();
				if (name == null)
					return;
				String pieces[] = message.split(":");
				int mins = Integer.parseInt(pieces[3]);
				if (mins < 1)
					// m_botAction.sendSmartPrivateMessage("MessageBot",
					// "!login " + name);
					m_botAction.ipcTransmit("messages", new IPCMessage(name, "MessageBot"));
			}
	}
	*/

	public void handleEvent(PlayerEntered event) {
		String name = m_botAction.getPlayerName(event.getPlayerID()).toLowerCase();
		if (name != null)
			checkQueue.add(name);
	}

	private class queueTimerTask extends TimerTask {
		public void run() {
			String name = checkQueue.next();
			if (name == null) {
				queueTimer.schedule(tTask, CHECKTIME * 1000);
				return;
			}
			m_botAction.ipcTransmit("messages", new IPCMessage(name, "MessageBot"));
			queueTimer.schedule(tTask, CHECKTIME * 1000);
		}
	}
	
	@Override
	public void cancel() {
		// TODO Auto-generated method stub
		
	}
	
	private class Queue<T> {
		
		ArrayList<T> objects;

		public Queue() {
			objects = new ArrayList<T>();
		}

		public T next() {
			T obj = null;

			try {
				obj = objects.remove(0);
			} catch (IndexOutOfBoundsException ioobe) {
			}
			return obj;
		}

		public void add(T obj) {
			objects.add(obj);
		}

		public int size() {
			return objects.size();
		}
	}

}

