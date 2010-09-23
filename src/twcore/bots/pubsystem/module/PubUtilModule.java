package twcore.bots.pubsystem.module;

import twcore.bots.pubsystem.PubContext;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.util.Tools;

public class PubUtilModule extends AbstractModule {

	private PubContext context;
	
	private static enum DoorMode { CLOSED, OPENED, IN_OPERATION, UNKNOW };
	private DoorMode doorStatus = DoorMode.UNKNOW;
	
	private int doorModeDefault;
	private int doorModeThreshold;
	private int doorModeThresholdSetting;
	private boolean doorArenaOnChange = false;
	
	private boolean doorModeManual = false;
	
	public PubUtilModule(BotAction botAction, PubContext context) {
		super(botAction);
		
		this.context = context;
		
		doorModeDefault = m_botAction.getBotSettings().getInt("doormode_default");
		doorModeThreshold = m_botAction.getBotSettings().getInt("doormode_threshold");
		doorModeThresholdSetting = m_botAction.getBotSettings().getInt("doormode_threshold_setting");
		
		if (m_botAction.getBotSettings().getInt("door_arena_on_change")==1) {
			doorArenaOnChange = true;
		}
	}

	public void handleEvent(PlayerEntered event) {
		checkForDoors();
	}
	
	public void handleEvent(PlayerLeft event) {
		checkForDoors();
	}

	private void checkForDoors() {
		
		// Did someone manually changed the doors? if yes.. do nothing
		if (doorModeManual)
			return;
		
		if (m_botAction.getNumPlayers() >= doorModeThreshold && !doorStatus.equals(DoorMode.IN_OPERATION)) {
			m_botAction.setDoors(doorModeThresholdSetting);
			if (doorArenaOnChange) {
				m_botAction.sendArenaMessage("[SETTING] Doors are now in operation.", Tools.Sound.BEEP1);
			}
			doorStatus = DoorMode.IN_OPERATION;
		} else if (!doorStatus.equals(DoorMode.CLOSED)) {
			m_botAction.setDoors(doorModeDefault);
			if (doorArenaOnChange) {
				m_botAction.sendArenaMessage("[SETTING] Doors are now lock.", Tools.Sound.BEEP1);
			}
			doorStatus = DoorMode.CLOSED;
		}
	}

	@Override
	public void handleCommand(String sender, String command) {
		// TODO Auto-generated method stub
		
	}

	private void doOpenDoorCmd(String sender) {
		doorModeManual = true;
		m_botAction.setDoors(0);
		m_botAction.sendSmartPrivateMessage(sender, "Doors opened.");
		if (doorArenaOnChange) {
			m_botAction.sendArenaMessage("[SETTING] Doors are now open.", Tools.Sound.BEEP1);
		}
		doorStatus = DoorMode.OPENED;
	}
	
	private void doCloseDoorCmd(String sender) {
		doorModeManual = true;
		m_botAction.setDoors(255);
		m_botAction.sendSmartPrivateMessage(sender, "Doors closed.");
		if (doorArenaOnChange) {
			m_botAction.sendArenaMessage("[SETTING] Doors are now lock.", Tools.Sound.BEEP1);
		}
		doorStatus = DoorMode.CLOSED;
	}
	
	private void doToggleDoorCmd(String sender) {
		doorModeManual = true;
		m_botAction.setDoors(-2);
		m_botAction.sendSmartPrivateMessage(sender, "Doors will be toggl.");
		if (doorArenaOnChange) {
			m_botAction.sendArenaMessage("[SETTING] Doors are now in operation.", Tools.Sound.BEEP1);
		}
		doorStatus = DoorMode.IN_OPERATION;
	}
	
	private void doAutoDoorCmd(String sender) {
		doorModeManual = false;
		checkForDoors();
		m_botAction.sendSmartPrivateMessage(sender, "Doors will be locked or in operation if the number of players is higher than " + doorModeThreshold + ".");
	}

	@Override
	public void handleModCommand(String sender, String command) {
		
        try {
        	
            if(command.startsWith("!opendoors"))
            	doOpenDoorCmd(sender);
            else if(command.startsWith("!closedoors"))
            	doCloseDoorCmd(sender);
            else if(command.startsWith("!toggledoors"))
            	doToggleDoorCmd(sender);
            else if(command.startsWith("!autodoors"))
            	doAutoDoorCmd(sender);
            
        } catch(RuntimeException e) {
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
	}

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.PLAYER_LEFT);
		eventRequester.request(EventRequester.PLAYER_ENTERED);
	}

	@Override
	public void start() {

	}

}
