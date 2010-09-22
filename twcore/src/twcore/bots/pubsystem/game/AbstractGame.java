package twcore.bots.pubsystem.game;

import twcore.core.events.FlagClaimed;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;

public abstract class AbstractGame {

	protected String name;
	
	public AbstractGame(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public abstract void start(String argument);
	public abstract void stop();
	public abstract boolean isStarted();
	
	public abstract void statusMessage(String playerName);
	//public abstract void getCommands();
	
	public abstract void handleCommand(String sender, String command);
	public abstract void handleModCommand(String sender, String command);
	
	public abstract void die();
	public abstract boolean isIdle();
	
	public void handleEvent(FlagClaimed event) {
		
	}
	
	public void handleEvent(FrequencyShipChange event) {
		
	}
	
	public void handleEvent(FrequencyChange event) {
		
	}
	
}
