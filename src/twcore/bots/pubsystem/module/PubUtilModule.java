package twcore.bots.pubsystem.module;

import java.util.HashMap;
import java.util.TimerTask;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.util.PubLocation;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubUtilModule extends AbstractModule {

	// TILESET
	
	public static Tileset DEFAULT_TILESET = Tileset.BLUETECH;
	public static enum Tileset { 
		BOKI,
		MONOLITH,
		BLUETECH
	};
	
	public static HashMap<Tileset,Integer> tilesetObjects;
	static {
		tilesetObjects = new HashMap<Tileset,Integer>();
		tilesetObjects.put(Tileset.BOKI, 0);
		tilesetObjects.put(Tileset.MONOLITH, 1);
	}
	
	// DOORS
	
	private static enum DoorMode { CLOSED, OPENED, IN_OPERATION, UNKNOW };
	private DoorMode doorStatus = DoorMode.UNKNOW;
	
	private int doorModeDefault;
	private int doorModeThreshold;
	private int doorModeThresholdSetting;
	private boolean doorArenaOnChange = false;
	
	private boolean doorModeManual = false;
	
	public PubUtilModule(BotAction botAction, PubContext context) {
		super(botAction, context, "Utility");
		
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

	public void setTileset(Tileset tileset, String playerName) 
	{
		Player p = m_botAction.getPlayer(playerName);
		if (p != null) {
			
			if (DEFAULT_TILESET == tileset) {
				for(int object: tilesetObjects.values()) {
					m_botAction.sendUnfilteredPrivateMessage(p.getPlayerID(), "*objoff " + object);
				}
			}
			else {
				for(int object: tilesetObjects.values()) {
					m_botAction.sendUnfilteredPrivateMessage(p.getPlayerID(), "*objoff " + object);
				}
				m_botAction.sendUnfilteredPrivateMessage(p.getPlayerID(), "*objon " + tilesetObjects.get(tileset));
			}
		}
	}
	
	public void setArenaTileset(Tileset tileset) 
	{
		if (DEFAULT_TILESET == tileset) {
			for(int object: tilesetObjects.values()) {
				m_botAction.sendArenaMessage("*objoff " + object);
			}
		}
		else {
			for(int object: tilesetObjects.values()) {
				m_botAction.sendArenaMessage("*objoff " + object);
			}
			m_botAction.sendArenaMessage("*objon " + tilesetObjects.get(tileset));
		}
	}
	
    /**
     * Change the current tileset for a player
     */
    public void doSetTileCmd( String sender, String tileName ) {

    	try {
    		Tileset tileset = Tileset.valueOf(tileName.toUpperCase());
    		setTileset(tileset, sender);
    	} catch (IllegalArgumentException e) {
    		m_botAction.sendPrivateMessage(sender, "The tileset '" + tileName + "' does not exists.");
    	}

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
	
	   /**
     * Moves the bot from one arena to another.  The bot must not be
     * started for it to move.
     *
     * @param sender is the person issuing the command.
     * @param argString is the new arena to go to.
     * @throws RuntimeException if the bot is currently running.
     * @throws IllegalArgumentException if the bot is already in that arena.
     */
    public void doGoCmd(String sender, String argString)
    {
        String currentArena = m_botAction.getArenaName();

        if(context.isStarted())
            throw new RuntimeException("Bot is currently running pub settings in " + currentArena + ".  Please !stop before trying to move.");
        if(currentArena.equalsIgnoreCase(argString))
            throw new IllegalArgumentException("Bot is already in that arena.");

        m_botAction.changeArena(argString);
        m_botAction.sendSmartPrivateMessage(sender, "Bot going to: " + argString);
    }

    /**
     * Toggles if private frequencies are allowed or not.
     *
     * @param sender is the sender of the command.
     */
    public void doPrivFreqsCmd(String sender)
    {
        if(!context.isPrivFreqEnabled())
        {
            m_botAction.sendArenaMessage("[SETTING] Private Frequencies enabled.", 2);
            m_botAction.sendSmartPrivateMessage(sender, "Private frequencies succesfully enabled.");
        }
        else
        {
            context.getPlayerManager().fixFreqs();
            m_botAction.sendArenaMessage("[SETTING] Private Frequencies disabled.", 2);
            m_botAction.sendSmartPrivateMessage(sender, "Private frequencies succesfully disabled.");
        }
        context.setPrivFreqEnabled(!context.isPrivFreqEnabled());
    }



    /**
     * Logs the bot off if not enabled.
     *
     * @param sender is the person issuing the command.
     * @throws RuntimeException if the bot is running pure pub settings.
     */
    public void doDieCmd(String sender)
    {
        m_botAction.sendSmartPrivateMessage(sender, "Bot logging off.");
        m_botAction.setObjects();
        m_botAction.scheduleTask(new DieTask(), 300);
    }

    /**
     * Shows last seen location of a given individual.
     */
    public void doWhereIsCmd( String sender, String argString, boolean isStaff ) {
        Player p = m_botAction.getPlayer(sender);
        if( p == null )
            throw new RuntimeException("Can't find you.  Please report this to staff.");
        if( p.getShipType() == 0 && !isStaff )
            throw new RuntimeException("You must be in a ship for this command to work.");
        Player p2;
        p2 = m_botAction.getPlayer( argString );
        if( p2 == null )
            p2 = m_botAction.getFuzzyPlayer( argString );
        if( p2 == null )
            throw new RuntimeException("I can't find the player '" + argString + "'.  Tough shit, bucko.");
        if( p.getFrequency() != p2.getFrequency() && !isStaff )
            throw new RuntimeException(p2.getPlayerName() + " is not on your team!");
        m_botAction.sendPrivateMessage( sender, p2.getPlayerName() + " last seen: " + PubLocation.getPlayerLocation( p2, isStaff ));
    }

	@Override
	public void handleCommand(String sender, String command) {

        try {
        	
            if(command.startsWith("!settile ") || command.startsWith("!tileset "))
            	doSetTileCmd(sender, command.substring(9));
            else if(command.startsWith("!whereis "))
                doWhereIsCmd(sender, command.substring(9), m_botAction.getOperatorList().isBot(sender));
            else if(command.equals("!restrictions"))
            	context.getPlayerManager().doRestrictionsCmd(sender);
            
        } catch(RuntimeException e) {
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
	}
	
	
	@Override
	public void handleModCommand(String sender, String command) {
		
        try {
        	
            if(command.startsWith("!dooropen"))
            	doOpenDoorCmd(sender);
            else if(command.startsWith("!doorclose"))
            	doCloseDoorCmd(sender);
            else if(command.startsWith("!doortoggle"))
            	doToggleDoorCmd(sender);
            else if(command.startsWith("!doorauto"))
            	doAutoDoorCmd(sender);
            else if(command.startsWith("!go "))
                doGoCmd(sender, command.substring(4));
            else if(command.equals("!privfreqs"))
                doPrivFreqsCmd(sender);
            else if(command.startsWith("!set "))
                context.getPlayerManager().doSetCmd(sender, command.substring(5));
            else if(command.equals("!die"))
                doDieCmd(sender);
            
        } catch(RuntimeException e) {
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
	}
	
	@Override
	public String[] getHelpMessage() {
		return new String[]{
			pubsystem.getHelpLine("!whereis <name>   -- Shows last seen location of <name> (if on your team)."),
            pubsystem.getHelpLine("!restrictions     -- Lists all current ship restrictions."),
			pubsystem.getHelpLine("!settile <name>   -- Change the current tileset."),
			pubsystem.getHelpLine("                     Choice: bluetech (default), boki, monolith.")
		};
	}

	@Override
	public String[] getModHelpMessage() {
		return new String[] {
			pubsystem.getHelpLine("!privfreqs   -- Toggles private frequencies & check for imbalances."),
            pubsystem.getHelpLine("!dooropen    -- Open doors."),
            pubsystem.getHelpLine("!doorclose   -- Close doors."),
            pubsystem.getHelpLine("!doortoggle  -- In operation doors."),
            pubsystem.getHelpLine("!doorauto    -- Auto mode (close if # of players below " + doorModeThreshold + "."),
			pubsystem.getHelpLine("!set <ship> <#>   -- Sets <ship> to restriction <#>."),
            pubsystem.getHelpLine("                     0=disabled; 1=any amount; other=weighted:"),
            pubsystem.getHelpLine("                     2 = 1/2 of freq can be this ship, 5 = 1/5, ..."),
            pubsystem.getHelpLine("!go <arena>  -- Moves the bot to <arena>."),
            pubsystem.getHelpLine("!die         -- Logs the bot off of the server."),
		};
	}

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.PLAYER_LEFT);
		eventRequester.request(EventRequester.PLAYER_ENTERED);
	}

	@Override
	public void start() {

	}
	
    /**
     * This private class logs the bot off.  It is used to give a slight delay
     * to the log off process.
     */
    private class DieTask extends TimerTask
    {

        /**
         * This method logs the bot off.
         */
        public void run()
        {
            m_botAction.die();
        }
    }


}
