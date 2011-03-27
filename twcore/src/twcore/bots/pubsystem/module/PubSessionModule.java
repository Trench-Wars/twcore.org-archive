package twcore.bots.pubsystem.module;

import java.util.HashMap;
import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.util.Tools;

// Session module - provides info on player's current play session

// Basic structure borrowed from the pub streak module.
// @author qan
public class PubSessionModule extends AbstractModule {
	
	HashMap <String,SessionPlayer>ps = new HashMap<String,SessionPlayer>();
	//private boolean moneyEnabled = false;
	
	public PubSessionModule(BotAction botAction, PubContext context) {
		super(botAction, context, "Session");
		
		reloadConfig();
	}
	
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.PLAYER_DEATH);
		eventRequester.request(EventRequester.PLAYER_LEFT);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
	}

	public void handleEvent( PlayerEntered event ) {
        Player p = m_botAction.getPlayer(event.getPlayerID());
        if (p==null)
            return;	    
        SessionPlayer sp = new SessionPlayer();
        ps.put(  p.getPlayerName(), sp );
	}
	
	public void handleEvent(PlayerLeft event) {
		Player p = m_botAction.getPlayer(event.getPlayerID());
		if (p==null)
			return;
		@SuppressWarnings("unused")
        SessionPlayer sp = ps.remove( p.getPlayerName() );
		sp = null;
	}
    
    public void handleEvent(PlayerDeath event) {
    	if( !enabled )
    		return;
    	
    	Player killer = m_botAction.getPlayer(event.getKillerID());
        Player killed = m_botAction.getPlayer(event.getKilleeID());
        
		if( killer == null || killed == null )
			return;
		
        // TODO: TK
        if( killer.getFrequency() == killed.getFrequency() ) {
           // Count em up and display
        } else {
            SessionPlayer sKiller = ps.get( killer.getPlayerName() );
            if( sKiller != null )
                sKiller.addKill( killer.getShipType(), killed.getShipType() );
        
            SessionPlayer sKilled = ps.get( killed.getPlayerName() );
            if( sKilled != null )
                sKilled.addDeath( killer.getShipType(), killed.getShipType() );
        }
  
    }
    
    
    
    public void doSessionCmd( String player, String requester ) {
        SessionPlayer p = ps.get( player );
        if( p == null ) {
            Player p2 = m_botAction.getFuzzyPlayer( player );
            if( p2 == null ) {
                m_botAction.sendPrivateMessage( requester, "Sorry, can't find that player. Check the name and try again." );
                return;
            }
            p = ps.get( p2.getPlayerName() );
            if( p == null ) {
                m_botAction.sendPrivateMessage( requester, "Sorry, can't find that player. Check the name and try again." );
                return;
            }
        }
        
        if( p.isTracking() == false ) {
            if( player.equals( requester ) )
                m_botAction.sendPrivateMessage( requester, "You currently are not tracking this session.  !session on to enable." );
            else
                m_botAction.sendPrivateMessage( requester, "That player is currently not tracking this session." );
            return;
        }
        
        int k = p.getTotalKills();
        int d = p.getTotalDeaths();
        int k2, d2;
        String s, n;
        
        m_botAction.sendPrivateMessage(  requester, "SESSION RECORD of: " + player + "    Kills: " + k + "  Deaths: " + d + "  Ratio: " + getRatio(k,d) );
        
        for( int i=1; i<9; i++ ) {
            n =  Tools.shipNameSlang( i ).toUpperCase();
            k = p.getTotalKillsInShip( i );
            d = p.getTotalDeathsInShip( i );
            k2 = p.getTotalKillsOfShip( i );
            d2 = p.getTotalDeathsToShip( i );
            // |  AS SHARK ...   k[10] d[30] r[2.30:1]            VS WB ...   k/d 34:20  r 2.30:1  |
            s = Tools.formatString( "|  AS " + n + " ...   ", 16 );
            s += Tools.formatString( "k/d " + k + ":" + d + "  ", 13 );
            s += Tools.formatString( "r " + getRatio(k,d) + "   ", 9 );
            s += Tools.formatString( "|  VS " + n + " ...   ", 16 );
            s += Tools.formatString( "k/d " + k2 + ":" + d2 + "  ", 13 );
            s += "r " + getRatio(k2,d2);

            m_botAction.sendPrivateMessage(  requester, s );            
        }
    }
    
    public void doSessionShipCmd( String sender, int ship ) {
        SessionPlayer p = ps.get( sender );
        if( p == null ) {
            m_botAction.sendPrivateMessage( sender, "Can't find your session record.  Please contact a member of staff." );
            return;
        }
        
        if( p.isTracking() == false ) {
            m_botAction.sendPrivateMessage( sender, "You currently are not tracking this session.  !session on to enable." );
            return;
        }
        
        int k = p.getTotalKillsInShip( ship );
        int d = p.getTotalDeathsInShip( ship );
        int k2, d2;
        String s, n;
        String ndetail = Tools.shipNameSlang( ship );
        
        m_botAction.sendPrivateMessage( sender, "DETAIL SESSION RECORD of " + sender + " - " + Tools.shipName( ship ) + ".  Overall as " + ndetail + "...  K: " + k + "  D: " + d + "  R: " + getRatio(k,d) );
        m_botAction.sendPrivateMessage( sender, ".               Record PLAYING " + ndetail + "                 Record VERSUS" + ndetail );
       

        for( int i=0; i<8; i++ ) {
            n =  Tools.shipNameSlang( i ).toUpperCase();
            k = p.getKillsRaw( ship, i );
            d = p.getDeathsRaw( i, ship );
            k2 = p.getDeathsRaw( ship, i );
            d2 = p.getKillsRaw( i, ship );
            // |               Record PLAYING Jav                 Record VERSUS Jav
            // |  SHARK  |     k[10] d[30] r[2.30:1]              k/d  34 : 20  r 2.30:1
            // |  SHARK  |     k[10] d[30] r[2.30:1]              k/d  34 : 20  r 2.30:1

            // |  AS SHARK ...   k[10] d[30] r[2.30:1]            VS WB ...   k/d 34:20  r 2.30:1  |
            
            s = Tools.formatString( "|  " + n + "  |", 16 );
            s += Tools.formatString( "k/d " + k + ":" + d + "  ", 13 );
            s += Tools.formatString( "r " + getRatio(k,d) + "   ", 9 );
            s += Tools.formatString( "|  VS " + n + " ...   ", 16 );
            s += Tools.formatString( "k/d " + k2 + ":" + d2 + "  ", 13 );
            s += "r " + getRatio(k2,d2);

            m_botAction.sendPrivateMessage( sender, s );            
        }
        
    }
    
    public void doSessionOnOffCmd( String sender, boolean turnOn ) {
        SessionPlayer p = ps.get( sender );
        if( p == null ) {
            m_botAction.sendPrivateMessage( sender, "Can't find your session record.  Please contact a member of staff." );
            return;
        }
        p.setTracking( turnOn );
        m_botAction.sendPrivateMessage( sender, "Session kill/death tracking [ " + (turnOn ? "ON" : "OFF") + " ]" );
    }
    
    public void doSessionResetCmd( String sender ) {
        SessionPlayer p = ps.get( sender );
        if( p == null ) {
            m_botAction.sendPrivateMessage( sender, "Can't find your session record.  Please contact a member of staff." );
            return;
        }
        p.doReset();
        m_botAction.sendPrivateMessage( sender, "Session kill/death record has been RESET.  Fresh kill session beginning now." );
    }
    
    // Helper methods
    public String getRatio( int k, int d ) {
        if( k==0 )
            return "0:" + d;
        if( d==0 )
            return k + ":0";
        return String.format( "%.2f", ((float)k / (float)d) ) + ":1";
    }

	@Override
	public void handleCommand(String sender, String command) {

        if( command.equalsIgnoreCase("!session") ) {
            doSessionCmd( sender, sender );
        } else if( command.startsWith( "!session ship " ) ) {
            try {
                Integer ship = Integer.getInteger( command.substring(14) );
                if( ship < 1 || ship > 8 ) {
                    m_botAction.sendPrivateMessage( sender, "Ship number must be between 1 and 8." );
                    return;
                }
                doSessionShipCmd( sender, ship );
            } catch (Exception e) {
                m_botAction.sendPrivateMessage( sender, "Usage:  !session ship [shipnum]  (e.g.  !session ship 3  shows how well you've done as spider and vs spider in EVERY ship.)" );
                return;
            }
        } else if( command.equalsIgnoreCase("!session on") ) {
            doSessionOnOffCmd( sender, true );
	    } else if( command.equalsIgnoreCase("!session off") ) {
            doSessionOnOffCmd( sender, false );
        } else if( command.equalsIgnoreCase("!session reset") ) {
            doSessionResetCmd(sender);
        } else if( command.startsWith("!session ") ) {
	        doSessionCmd( command.substring(9), sender );
	    }

	}

	@Override
	public void handleModCommand(String sender, String command) {
	}
	
	@Override
	public String[] getHelpMessage(String sender) {
		return new String[] {
			pubsystem.getHelpLine("!session [player]  -- Stats for [player] in each ship. Blank=yourself"),
            pubsystem.getHelpLine("!session ship [#]  -- Stats for ship#, broken down per-ship (as & vs)"),
            pubsystem.getHelpLine("!session [on|off|reset] -- Turn on/off record; reset rec for session")
        };
	}

	@Override
	public String[] getModHelpMessage(String sender) {
		return new String[] {
        };
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void stop() {

	}

	@Override
	public void reloadConfig() {
	    // TODO: Uncomment this section when this line is added to CFG, and then remove the force-enable
		//if (m_botAction.getBotSettings().getInt("session_enabled")==1) {
		//	enabled = true;
		//}
	    
	    enabled = true;
		/* TODO: Uncomment if you ever use money (rewarding people for certain feats for example)
		if (m_botAction.getBotSettings().getInt("session_money_enabled")==1) {
			moneyEnabled = true;
		}
		*/
	}
	
	private class SessionPlayer {
	    boolean tracking;
	    
	    // First index is killing ship#; 2nd is killed.
	    // In kills, first index is you; in killedby, second index is you. 
	    int[][] kills = new int[8][8];
        int[][] killedby = new int[8][8];
	    
	    public SessionPlayer( ) {
	        tracking = true;
	        for( int i=0; i<8; i++ ) {
	            for( int j=0; j<8; j++ ) {
	                kills[i][j] = 0;
	                killedby[i][j] = 0;
	            }
	        }
	    }
	    
	    public void doReset() {
            for( int i=0; i<8; i++ ) {
                for( int j=0; j<8; j++ ) {
                    kills[i][j] = 0;
                    killedby[i][j] = 0;
                }
            }
	    }
	    
	    public void addKill( int wship, int lship ) {
	        if( tracking && wship > 0 && wship < 9 && lship > 0 && lship < 9 )
	            kills[ --wship ][ --lship ]++;
	    }

        public void addDeath( int wship, int lship ) {
            if( tracking && wship > 0 && wship < 9 && lship > 0 && lship < 9 )
                killedby[ --wship ][ --lship ]++;
        }
        
        public boolean setTracking( boolean t ) {
            if( tracking == t )
                return false;
            tracking = t;
            return true;
        }
        
        /*
        public int[][] getKills() {
            return kills;
        }
        
        public int[][] getKilledBy() {
            return killedby;
        }
        */
        
        public int getKillsRaw( int x, int y ) {
            return kills[x][y];
        }
        
        public int getDeathsRaw( int x, int y ) {
            return killedby[x][y];
        }
        
        // # kills
        public int getTotalKills() {
            int amt = 0;
            for( int i=0; i<8; i++ )
                for( int j=0; j<8; j++ )
                    amt += kills[i][j];
            return amt;
        }
        
        // # deaths
        public int getTotalDeaths() {
            int amt = 0;
            for( int i=0; i<8; i++ )
                for( int j=0; j<8; j++ )
                    amt += killedby[i][j];
            return amt;
        }
        
        // # kills in ship x
        public int getTotalKillsInShip( int ship ) {
            if( ship < 1 || ship > 8 )
                return 0;

            int amt = 0;
            ship--;
            for( int i=0; i<8; i++ )
                amt += kills[ship][i];
            return amt;
        }
        
        // # deaths in ship x
        public int getTotalDeathsInShip( int ship ) {
            if( ship < 1 || ship > 8 )
                return 0;

            int amt = 0;
            ship--;
            for( int i=0; i<8; i++ )
                amt += killedby[i][ship];
            return amt;
        }
        
        // # times player has killed ship x
        public int getTotalKillsOfShip( int ship ) {
            if( ship < 1 || ship > 8 )
                return 0;

            int amt = 0;
            ship--;
            for( int i=0; i<8; i++ )
                amt += kills[i][ship];
            return amt;
        }
        
        
        // # times killed by ship x
        public int getTotalDeathsToShip( int ship ) {
            if( ship < 1 || ship > 8 )
                return 0;

            int amt = 0;
            ship--;
            for( int i=0; i<8; i++ )
                amt += killedby[ship][i];
            return amt;
        }
        
        public boolean isTracking() {
            return tracking;
        }

	}
}
