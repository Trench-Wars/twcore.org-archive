package twcore.bots.pubsystem.util;

import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.events.InterProcessEvent;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

public abstract class AutobotThread extends Thread implements IPCReceiver {

		protected String IPC_CHANNEL;
		protected BotAction m_botAction;
		protected String sender;
		protected String parameters;
		protected int numberOfTry = 0;
		
		protected boolean locked = false;
		protected String autobotName = null;
		protected String botID;
		
		public AutobotThread(String sender, String parameters, BotAction m_botAction, String ipcChannel) {
			this.sender = sender;
			this.parameters = parameters;
			this.m_botAction = m_botAction;
			this.IPC_CHANNEL = ipcChannel;
			this.botID = String.valueOf(Math.random()*100000000);
		}
		
		public void run() 
		{
			super.run();
			
	    	final String hubName = m_botAction.getBotSettings().getString("HubName");

	    	/* Step 1: Look if there is a bot avalaible
	    	 * Step 2: If not, spawn a new one
	    	 * Step 3: Try until numberOfTry reach 10
	    	 */
	    	TimerTask task = new TimerTask() {
				public void run() {
					if (!locked && numberOfTry==1) {
						m_botAction.sendSmartPrivateMessage(hubName, "!spawn pubautobot");
					}
					else if (!locked && numberOfTry<=10) {
						m_botAction.ipcSendMessage(IPC_CHANNEL, "looking", null, m_botAction.getBotName()+"-"+botID);
					} 
					else {
						m_botAction.cancelTask(this);
					}
					numberOfTry++;
				}
			};
			m_botAction.scheduleTaskAtFixedRate(task, 0, 5*Tools.TimeInMillis.SECOND);
		
		}

		public void handleInterProcessEvent(InterProcessEvent event) {
			if (event.getChannel().equals(IPC_CHANNEL)) {
				
				IPCMessage ipc = (IPCMessage)event.getObject();
				String message = ipc.getMessage();
				String sender = ipc.getSender();
				
				if (!locked && message.equals("loggedon")) {
					m_botAction.ipcSendMessage(IPC_CHANNEL, "looking", null, m_botAction.getBotName()+"-"+botID);
					
				} else if (!locked && message.equals("locked") && ipc.getRecipient().contains(botID)) {
					locked = true;
					autobotName = sender;
					m_botAction.ipcSendMessage(IPC_CHANNEL, "confirm_lock", event.getSenderName(), m_botAction.getBotName()+":"+this.sender);
					m_botAction.sendSmartPrivateMessage(this.sender, sender + " has spawned, setting up its configuration..");
					try { Thread.sleep(1*Tools.TimeInMillis.SECOND); } catch (InterruptedException e) {}
					execute();
				}
				
			}
		}
		
		public void execute() {
			prepare();
			try { Thread.sleep(2*Tools.TimeInMillis.SECOND); } catch (InterruptedException e) {}
			ready();
		}
		
		protected abstract void prepare();
		protected abstract void ready();
		
		public void commandBot(String command) {
			m_botAction.sendSmartPrivateMessage(autobotName, command);
		}
	
	}