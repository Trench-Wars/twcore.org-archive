package twcore.bots.pubsystem.util;

import twcore.core.events.InterProcessEvent;

public interface IPCReceiver {

	public void handleInterProcessEvent(InterProcessEvent event);
	
}
