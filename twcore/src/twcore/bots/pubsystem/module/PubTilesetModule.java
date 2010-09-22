package twcore.bots.pubsystem.module;

import java.util.HashMap;

import twcore.core.BotAction;
import twcore.core.game.Player;

/* This class is used to set a particular tileset via an object
 * To add a new tileset, add the name on the enum and make sure to add
 * the object ID in tilesetObjects.
 * 
 * Idea by Flared
 */
public class PubTilesetModule extends AbstractModule {

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
	
	public BotAction m_botAction;
	
	public PubTilesetModule(BotAction botAction) {
		this.m_botAction = botAction;
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

	@Override
	public void handleCommand(String sender, String command) {
		
        try {
        	
            if(command.startsWith("!settile ") || command.startsWith("!tileset "))
            	doSetTileCmd(sender, command.substring(9));
            
        } catch(RuntimeException e) {
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
	}

	@Override
	public void handleModCommand(String sender, String command) {
		// TODO Auto-generated method stub
		
	}
	
}
