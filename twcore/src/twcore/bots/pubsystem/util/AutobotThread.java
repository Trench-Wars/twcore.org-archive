package twcore.bots.pubsystem.util;

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
		
		protected boolean locked = false;
		protected String autobotName = null;
		
		public AutobotThread(String sender, String parameters, BotAction m_botAction, String ipcChannel) {
			this.sender = sender;
			this.parameters = parameters;
			this.m_botAction = m_botAction;
			this.IPC_CHANNEL = ipcChannel;
		}
		
		public void run() 
		{
			super.run();
	    	String hubName = m_botAction.getBotSettings().getString("HubName");
	    	m_botAction.requestArenaList();
	    	m_botAction.sendSmartPrivateMessage(hubName, "!spawn pubautobot");
	    	m_botAction.sendSmartPrivateMessage(sender, "Please wait while spawning the turret..");
		}

		public void handleInterProcessEvent(InterProcessEvent event) {
			if (event.getChannel().equals(IPC_CHANNEL)) {
				
				IPCMessage object = (IPCMessage)event.getObject();
				String message = object.getMessage();
				String sender = object.getSender();
				
				if (!locked && message.equals("loggedon")) {
					m_botAction.ipcSendMessage(IPC_CHANNEL, "looking", null, m_botAction.getBotName());
					
				} else if (!locked && message.equals("locked")) {
					locked = true;
					autobotName = sender;
					m_botAction.ipcSendMessage(IPC_CHANNEL, "confirm_lock", null, m_botAction.getBotName()+":"+this.sender);
					m_botAction.sendSmartPrivateMessage(this.sender, sender + " has spawned, setting up the turret..");
					try { Thread.sleep(1*Tools.TimeInMillis.SECOND); } catch (InterruptedException e) {}
					ready();
				}
				
			}
		}
		
		public abstract void ready();
		
		public void commandBot(String command) {
			m_botAction.sendSmartPrivateMessage(autobotName, command);
		}
	
	}