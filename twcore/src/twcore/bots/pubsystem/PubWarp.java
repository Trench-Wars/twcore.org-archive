package twcore.bots.pubsystem;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import twcore.bots.pubsystem.game.GameContext;
import twcore.bots.pubsystem.game.GameContext.Mode;
import twcore.core.BotAction;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.util.Tools;

/*
 * This class handles the !warp feature 
 */
public class PubWarp {
	
	private BotAction m_botAction;
	private PubPlayerManager playerManager;
	private GameContext context;
	
	private HashMap<String,PubPlayer> warpPlayers;
	
    // X and Y coords for warp points.  Note that the first X and Y should be
    // the "standard" warp; in TW this is the earwarp.  These coords are used in
    // strict flag time mode.
    private int warpPtsLeftX[];
    private int warpPtsLeftY[];
    private int warpPtsRightX[];
    private int warpPtsRightY[];
    
    // Warp coords for safes (for use in strict flag time mode)
    private int warpSafeLeftX;
    private int warpSafeLeftY;
    private int warpSafeRightX;
    private int warpSafeRightY;

    private boolean autoWarp = false;
	private boolean enabled = false;
	
	public PubWarp(BotAction botAction, PubPlayerManager manager, GameContext context) {
		this.m_botAction = botAction;
		this.playerManager = manager;
		this.context = context;
		this.warpPlayers = new HashMap<String,PubPlayer>();
		
        warpPtsLeftX = m_botAction.getBotSettings().getIntArray("warpLeftX", ",");
        warpPtsLeftY = m_botAction.getBotSettings().getIntArray("warpLeftY", ",");
        warpPtsRightX = m_botAction.getBotSettings().getIntArray("warpRightX", ",");
        warpPtsRightY = m_botAction.getBotSettings().getIntArray("warpRightY", ",");
        
        warpSafeLeftX = m_botAction.getBotSettings().getInt("warpSafeLeftX");
        warpSafeLeftY = m_botAction.getBotSettings().getInt("warpSafeLeftY");
        warpSafeRightX = m_botAction.getBotSettings().getInt("warpSafeRightX");
        warpSafeRightY = m_botAction.getBotSettings().getInt("warpSafeRightY");
        
		if (m_botAction.getBotSettings().getInt("auto_warp")==1) {
			autoWarp = true;
		}
	}
	
	public void enable() {
		this.enabled = true;
	}
	
	public void disable() {
		this.enabled = false;
	}
	
	public void autoWarpEnable() {
		this.autoWarp = true;
	}
	
	public void autoWarpDisable() {
		this.autoWarp = false;
	}
	
	public boolean isWarpEnabled() {
		return enabled;
	}
	
	public boolean isAutoWarpEnabled() {
		return autoWarp;
	}
	
    /**
     * Turns on or off "autowarp" mode, where players opt out of warping into base,
     * rather than opting in.
     *
     * @param sender is the person issuing the command.
     */
    public void doAutowarpCmd(String sender) {
        if( autoWarp ) {
            m_botAction.sendPrivateMessage(sender, "Players will no longer automatically be added to the !warp list when they enter the arena.");
            autoWarpDisable();
        } else {
            m_botAction.sendPrivateMessage(sender, "Players will be automatically added to the !warp list when they enter the arena.");
            autoWarpEnable();
        }
    }
    
    /**
     * Turns on or off allowing players to use !warp to get into base at the start of a round.
     *
     * @param sender is the person issuing the command.
     */
    public void doAllowWarpCmd(String sender) {
        if( isWarpEnabled() ) {
            m_botAction.sendPrivateMessage(sender, "Players will no longer be able to use !warp.");
            warpPlayers.clear();
            disable();
        } else {
            m_botAction.sendPrivateMessage(sender, "Players will be allowed to use !warp.");
            enable();
        }
    }
    
    public void handleEvent(PlayerEntered event) {
    	
        int playerID = event.getPlayerID();
        Player player = m_botAction.getPlayer(playerID);
        String playerName = m_botAction.getPlayerName(playerID);
    	
        if(context.isFlagTimeStarted() && isAutoWarpEnabled()) {
        	if( player.getShipType() != Tools.Ship.SPECTATOR )
        		doWarpCmd(playerName);
        }
    }
	
	public void handleEvent(FrequencyChange event) {
		
		if (!enabled)
			return;

	}
	
	public void handleEvent(FrequencyShipChange event) {
		
		if (!enabled)
			return;
		
        String playerName = m_botAction.getPlayerName(event.getPlayerID());
        int ship = event.getShipType();
        
        PubPlayer player = playerManager.getPlayer(m_botAction.getPlayerName(event.getPlayerID()));
        
        if (autoWarp && !warpPlayers.containsKey(playerName)) {
	        if(ship != Tools.Ship.SPECTATOR)
	        	doWarpCmd(playerName); 
        }

        // Terrs and Levis can't warp into base if Levis are enabled
        if (!playerManager.isShipRestricted(Tools.Ship.LEVIATHAN)) {
	        if (ship == Tools.Ship.LEVIATHAN || ship == Tools.Ship.TERRIER) {         
	        	warpPlayers.remove(playerName);
	        }   
        }     
	}
	
	public void handleEvent(PlayerLeft event) {
		String playerName = m_botAction.getPlayerName(event.getPlayerID());
		warpPlayers.remove(playerName);
	}
	
    public void doWarpCmd(String sender)
    {
    	PubPlayer player = playerManager.getPlayer(sender);
    	if (player == null)
    		return;
    	
        if(!context.isFlagTimeStarted()) {
        	m_botAction.sendSmartPrivateMessage(sender, "Flag Time mode is not currently running." );
        	return;
        } else if( context.isMode(Mode.STRICT_FLAG_TIME) ) {
        	m_botAction.sendSmartPrivateMessage(sender, "Strict Flag mode is currently running, !warp has no effect. You will automatically be warped.");
        	return;
        } else if( enabled ) {
        	m_botAction.sendSmartPrivateMessage(sender, "Warping into base at round start is not currently allowed." );
        	return;
        }
        
        // Terrs and Levis can't warp into base if Levis are enabled
        if( playerManager.isShipRestricted(Tools.Ship.LEVIATHAN) )
        {
            Player p = m_botAction.getPlayer( sender );
            if( p.getShipType() == Tools.Ship.LEVIATHAN ) {    
            	m_botAction.sendSmartPrivateMessage(sender, "Leviathans can not warp in to base at round start." );
            	return;
            }
            if( p.getShipType() == Tools.Ship.TERRIER ) {
            	m_botAction.sendSmartPrivateMessage(sender, "Terriers can not warp into base at round start while Leviathans are enabled.");                
            	return;
            }
        }

        if( warpPlayers.containsKey(sender)) {
            warpPlayers.remove( sender );
            m_botAction.sendSmartPrivateMessage(sender, "You will NOT be warped inside FR at every round start. !warp again to turn back on.");
        } else {
            warpPlayers.put(sender,player);
            m_botAction.sendSmartPrivateMessage(sender, "You will be warped inside FR at every round start. Type !warp to turn off.");
        }
    }
    
    /**
     * Warps a player within a radius of 2 tiles to provided coord.
     *
     * @param playerName
     * @param xCoord
     * @param yCoord
     * @param radius
     * @author Cpt.Guano!
     */
    private void doPlayerWarp(String playerName, int xCoord, int yCoord) 
    {
        int radius = 2;
        double randRadians;
        double randRadius;
        int xWarp = -1;
        int yWarp = -1;

        randRadians = Math.random() * 2 * Math.PI;
        randRadius = Math.random() * radius;
        xWarp = calcXCoord(xCoord, randRadians, randRadius);
        yWarp = calcYCoord(yCoord, randRadians, randRadius);

        m_botAction.warpTo(playerName, xWarp, yWarp);
    }
    
    /**
     * Warps all players who have PMed with !warp into FR at start.
     * Ensures !warpers on freqs are warped all to 'their' side, but not predictably.
     */
    public void warpPlayers(boolean allPlayers) {
    	
        Iterator<?> i;

        if(allPlayers)
            i = m_botAction.getPlayingPlayerIterator();
        else
            i = warpPlayers.keySet().iterator();

        Random r = new Random();
        int rand;
        Player p;
        String pname;
        LinkedList <String>nullPlayers = new LinkedList<String>();

        int randomside = r.nextInt( 2 );

        while( i.hasNext() ) {
            if(allPlayers) {
                p = (Player)i.next();
                pname = p.getPlayerName();
            } else {
                pname = (String)i.next();
                p = m_botAction.getPlayer( pname );
            }

            if( p != null ) {
                if(allPlayers)
                    rand = 0;           // Warp freqmates to same spot in strict mode.
                                        // The warppoints @ index 0 must be set up
                                        // to default/earwarps for this to work properly.
                else
                    rand = r.nextInt( warpPtsLeftX.length );
                if( p.getFrequency() % 2 == randomside )
                    doPlayerWarp( pname, warpPtsLeftX[rand], warpPtsLeftY[rand] );
                else
                    doPlayerWarp( pname, warpPtsRightX[rand], warpPtsRightY[rand] );
            } else {
                if(!allPlayers) {
                    nullPlayers.add(pname);
                }
            }
        }

        if( ! nullPlayers.isEmpty() ) {
            i = nullPlayers.iterator();
            while( i.hasNext() ) {
                warpPlayers.remove( (String)i.next() );
            }
        }
    }
    
    /**
     * In Strict Flag Time mode, warp all players to a safe 10 seconds before
     * starting.  This gives a semi-official feeling to the game, and resets
     * all mines, etc.
     */
    public void safeWarp() 
    {
        // Prevent pre-laid mines and portals in strict flag time by setting to WB and back again (slightly hacky)
        HashMap<String,Integer> players = new HashMap<String,Integer>();
        HashMap<String,Integer> bounties = new HashMap<String,Integer>();
        Iterator<Player> it = m_botAction.getPlayingPlayerIterator();
        Player p;
        while( it.hasNext() ) {
            p = it.next();
            if( p != null ) {
                if( p.getShipType() == Tools.Ship.SHARK || p.getShipType() == Tools.Ship.TERRIER || p.getShipType() == Tools.Ship.LEVIATHAN ) {
                    players.put( p.getPlayerName(), new Integer(p.getShipType()) );
                    bounties.put( p.getPlayerName(), new Integer(p.getBounty()) );
                    m_botAction.setShip(p.getPlayerName(), 1);
                }
            }
        }
        Iterator<String> it2 = players.keySet().iterator();
        String name;
        Integer ship, bounty;
        while( it2.hasNext() ) {
            name = it2.next();
            ship = players.get(name);
            bounty = bounties.get(name);
            if( ship != null )
                m_botAction.setShip( name, ship.intValue() );
            if( bounty != null )
                m_botAction.giveBounty( name, bounty.intValue() - 3 );
        }

        Iterator<Player> i = m_botAction.getPlayingPlayerIterator();
        while( i.hasNext() ) {
            p = i.next();
            if( p != null ) {
                if( p.getFrequency() % 2 == 0 )
                    m_botAction.warpTo( p.getPlayerID(), warpSafeLeftX, warpSafeLeftY);
                else
                    m_botAction.warpTo( p.getPlayerID(), warpSafeRightX, warpSafeRightY);
            }
        }
    }
    
    private int calcXCoord(int xCoord, double randRadians, double randRadius) {
        return xCoord + (int) Math.round(randRadius * Math.sin(randRadians));
    }


    private int calcYCoord(int yCoord, double randRadians, double randRadius) {
        return yCoord + (int) Math.round(randRadius * Math.cos(randRadians));
    }

}
