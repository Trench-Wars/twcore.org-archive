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

    HashMap <String, SessionPlayer>ps = new HashMap<String, SessionPlayer>();
    PubContext context;

    //private boolean moneyEnabled = false;

    //default tax deduction for TKs
    //int tax = 10;

    // LandMark system
    static int LM_COMP_KILLS_EQUAL = 0;
    static int LM_COMP_RATIO_BETTER_THAN = 1;
    static int LM_COMP_DEATHS_EQUAL = 2;
    static int LM_SHIP_ANY = -1;

    public PubSessionModule(BotAction botAction, PubContext context) {
        super(botAction, context, "Session");
        this.context = context;
        reloadConfig();
    }

    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.PLAYER_DEATH);
        eventRequester.request(EventRequester.PLAYER_LEFT);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
    }

    public void handleEvent( PlayerEntered event ) {
        if (!enabled) return;

        Player p = m_botAction.getPlayer(event.getPlayerID());

        if (p == null)
            return;

        SessionPlayer sp = new SessionPlayer( p.getPlayerName() );
        ps.put(  p.getPlayerName(), sp );
    }

    public void handleEvent(PlayerLeft event) {
        if (!enabled) return;

        Player p = m_botAction.getPlayer(event.getPlayerID());

        if (p == null)
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
        else {           
            SessionPlayer sKiller = ps.get(killer.getPlayerName());

            if (sKiller != null) {
                if (killer.getFrequency() != killed.getFrequency()) {
                    sKiller.addKill(killer.getShipType(), killed.getShipType());
                }
            }

            SessionPlayer sKilled = ps.get(killed.getPlayerName());

            if (sKilled != null)
                sKilled.addDeath(killer.getShipType(), killed.getShipType());
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

        m_botAction.sendPrivateMessage(  requester, "SESSION RECORD of: " + player + "    Kills: " + k + "  Deaths: " + d + "  Ratio: " + getRatio(k, d) );

        for( int i = 1; i < 9; i++ ) {
            n =  Tools.shipNameSlang( i ).toUpperCase();
            k = p.getTotalKillsInShip( i );
            d = p.getTotalDeathsInShip( i );
            k2 = p.getTotalKillsOfShip( i );
            d2 = p.getTotalDeathsToShip( i );
            // |  AS SHARK ...   k[10] d[30] r[2.30:1]            VS WB ...   k/d 34:20  r 2.30:1  |
            s = Tools.formatString( "|  AS " + n + " ...   ", 16 );
            s += Tools.formatString( "k/d " + k + ":" + d + "  ", 13 );
            s += Tools.formatString( "r " + getRatio(k, d) + "   ", 9 );
            s += Tools.formatString( "|  VS " + n + " ...   ", 16 );
            s += Tools.formatString( "k/d " + k2 + ":" + d2 + "  ", 13 );
            s += "r " + getRatio(k2, d2);

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

        m_botAction.sendPrivateMessage( sender, "DETAIL SESSION RECORD of " + sender + " - " + Tools.shipName( ship ) + ".  Overall as " + ndetail + "...  K: " + k + "  D: " + d + "  R: " + getRatio(k, d) );
        m_botAction.sendPrivateMessage( sender, ".               Record PLAYING " + ndetail + "                 Record VERSUS" + ndetail );


        for( int i = 0; i < 8; i++ ) {
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
            s += Tools.formatString( "r " + getRatio(k, d) + "   ", 9 );
            s += Tools.formatString( "|  VS " + n + " ...   ", 16 );
            s += Tools.formatString( "k/d " + k2 + ":" + d2 + "  ", 13 );
            s += "r " + getRatio(k2, d2);

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
        if( k == 0 )
            return "0:" + d;

        if( d == 0 )
            return k + ":0";

        return String.format( "%.2f", ((float)k / (float)d) ) + ":1";
    }

    public double getRatioFloat( int k, int d ) {
        if( k == 0 )
            return 0.0;

        if( d == 0 )
            return k;

        return ( (double)k / (double)d );
    }

    @Override
    public void handleCommand(String sender, String command) {

        if( command.equalsIgnoreCase("!session") ) {
            doSessionCmd( sender, sender );
        } else if( command.startsWith( "!session ship " ) ) {
            try {
                Integer ship = Integer.decode( command.substring(14) );

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
        //  enabled = true;
        //}

        enabled = true;
        /*  TODO: Uncomment if you ever use money (rewarding people for certain feats for example)
            if (m_botAction.getBotSettings().getInt("session_money_enabled")==1) {
            moneyEnabled = true;
            }
        */
    }

    private class SessionPlayer {

        String name;
        boolean tracking;

        // First index is killing ship#; 2nd is killed.
        // In kills, first index is you; in killedby, second index is you.
        int[][] kills = new int[8][8];
        int[][] killedby = new int[8][8];
        byte[] landmarks = new byte[100];
        int bonus = 0;

        public SessionPlayer( String name ) {
            this.name = name;
            tracking = true;

            for( int i = 0; i < 8; i++ ) {
                for( int j = 0; j < 8; j++ ) {
                    kills[i][j] = 0;
                    killedby[i][j] = 0;
                }
            }
        }

        public void doReset() {
            for( int i = 0; i < 8; i++ ) {
                for( int j = 0; j < 8; j++ ) {
                    kills[i][j] = 0;
                    killedby[i][j] = 0;
                }
            }
        }

        public void addKill( int wship, int lship ) {
            if( tracking && wship > 0 && wship < 9 && lship > 0 && lship < 9 ) {
                kills[ --wship ][ --lship ]++;
                checkForLandmarkKills( wship, lship );
            }
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

        public int getKills( int x, int y ) {
            return kills[x - 1][y - 1];
        }

        public int getDeaths( int x, int y ) {
            return killedby[x - 1][y - 1];
        }

        // # kills
        public int getTotalKills() {
            int amt = 0;

            for( int i = 0; i < 8; i++ )
                for( int j = 0; j < 8; j++ )
                    amt += kills[i][j];

            return amt;
        }

        // # deaths
        public int getTotalDeaths() {
            int amt = 0;

            for( int i = 0; i < 8; i++ )
                for( int j = 0; j < 8; j++ )
                    amt += killedby[i][j];

            return amt;
        }

        // # kills in ship x
        public int getTotalKillsInShip( int ship ) {
            if( ship < 1 || ship > 8 )
                return 0;

            int amt = 0;
            ship--;

            for( int i = 0; i < 8; i++ )
                amt += kills[ship][i];

            return amt;
        }

        // # deaths in ship x
        public int getTotalDeathsInShip( int ship ) {
            if( ship < 1 || ship > 8 )
                return 0;

            int amt = 0;
            ship--;

            for( int i = 0; i < 8; i++ )
                amt += killedby[i][ship];

            return amt;
        }

        // # times player has killed ship x
        public int getTotalKillsOfShip( int ship ) {
            if( ship < 1 || ship > 8 )
                return 0;

            int amt = 0;
            ship--;

            for( int i = 0; i < 8; i++ )
                amt += kills[i][ship];

            return amt;
        }


        // # times killed by ship x
        public int getTotalDeathsToShip( int ship ) {
            if( ship < 1 || ship > 8 )
                return 0;

            int amt = 0;
            ship--;

            for( int i = 0; i < 8; i++ )
                amt += killedby[ship][i];

            return amt;
        }

        public boolean isTracking() {
            return tracking;
        }

        public void checkForLandmarkKills( int playership, int enemyship ) {
            int kills = getTotalKills();

            if( landmarks[0] == 0 ) {
                if( kills == 10000 ) {
                    m_botAction.sendArenaMessage( "HUGE FREAKIN' LANDMARK:  " + name + " has just made - 10000 KILLS - in this session!!" );
                    bonus = 25000;
                    send( "10000 kills this session!! You are a machine!" );
                    landmarks[0] = 1;
                }
            }

            if( landmarks[1] == 0 ) {
                if( kills == 5000 ) {
                    m_botAction.sendArenaMessage( "BIG BIG LANDMARK:  " + name + " has just made  5000 KILLS  in this session!!" );
                    bonus = 10000;
                    send( "5000 kills this session! Very freakin' nice!" );
                    landmarks[1] = 1;
                }
            }

            if( landmarks[2] == 0 ) {
                if( kills == 1000 ) {
                    m_botAction.sendArenaMessage( "BIG LANDMARK:  " + name + " has just made  1000 KILLS  in this session!!" );
                    bonus = 5000;
                    send( "1000 kills this session! Nice one!!" );
                    landmarks[2] = 1;
                }
            }

            if( landmarks[3] == 0 ) {
                if( kills == 500 ) {
                    bonus = 4000;
                    send( "500 kills this session!" );
                    landmarks[3] = 1;
                }
            }

            if( landmarks[4] == 0 ) {
                if( kills == 400 ) {
                    bonus = 3000;
                    send( "400 kills this session!" );
                    landmarks[4] = 1;
                }
            }

            if( landmarks[5] == 0 ) {
                if( kills == 300 ) {
                    bonus = 2000;
                    send( "300 kills this session!" );
                    landmarks[5] = 1;
                }
            }

            if( landmarks[6] == 0 ) {
                if( kills == 200 ) {
                    bonus = 1000;
                    send( "200 kills this session!" );
                    landmarks[6] = 1;
                }
            }

            if( landmarks[7] == 0 ) {
                if( kills == 100 ) {
                    bonus = 500;
                    send( "100 kills this session!" );
                    landmarks[7] = 1;
                }
            }

            if( landmarks[41] == 0 ) {
                if( kills == 75 ) {
                    bonus = 350;
                    send( "75 kills this session!" );
                    landmarks[41] = 1;
                }
            }

            if( landmarks[8] == 0 ) {
                if( kills == 50 ) {
                    bonus = 250;
                    send( "50 kills this session!" );
                    landmarks[8] = 1;
                }
            }

            if( landmarks[48] == 0 ) {
                if( kills == 25 ) {
                    bonus = 100;
                    send( "25 kills this session.  (PM !session to see details.)" );
                    landmarks[48] = 1;
                }
            }

            if( landmarks[39] == 0 ) {
                if( landmarkEventCheck( LM_SHIP_ANY, LM_SHIP_ANY, LM_COMP_DEATHS_EQUAL, 500 ) ) {
                    bonus = 500;
                    send( "500 deaths this session.  (Anyone can die.  But you?  You die with class.)" );
                    landmarks[39] = 1;
                }
            }

            if( landmarks[40] == 0 ) {
                if( landmarkEventCheck( LM_SHIP_ANY, LM_SHIP_ANY, LM_COMP_DEATHS_EQUAL, 100 ) ) {
                    bonus = 150;
                    send( "100 deaths this session.  Keep up the dead work!" );
                    landmarks[40] = 1;
                }
            }


            // ***  LANDMARKS for being in a certain ship  ***
            switch( playership ) {

            // WB
            case 1:
                if( landmarks[9] == 0 ) {
                    if( landmarkEventCheck( 1, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 500 ) ) {
                        m_botAction.sendArenaMessage( "BIG BIG LANDMARK:  " + name + " has just made  500 KILLS  in JUST a WARBIRD ALONE this session!!", 1 );
                        bonus = 5000;
                        send( "500 kills in a Warbird this session!  You are one badass sniper!" );
                        landmarks[9] = 1;
                    }
                }

                if( landmarks[10] == 0 ) {
                    if( landmarkEventCheck( 1, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 250 ) ) {
                        m_botAction.sendArenaMessage( "BIG LANDMARK:  " + name + " has just made  250 KILLS  in JUST a WARBIRD ALONE this session!", 1 );
                        bonus = 2500;
                        send( "250 kills in a Warbird this session!  You are the [wo]man." );
                        landmarks[10] = 1;
                    }
                }

                if( landmarks[11] == 0 ) {
                    if( landmarkEventCheck( 1, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 100 ) ) {
                        bonus = 1000;
                        send( "100 kills in a Warbird this session!" );
                        landmarks[11] = 1;
                    }
                }

                if( enemyship == 1 && landmarks[34] == 0 ) {
                    if( landmarkEventCheck( 1, 1, LM_COMP_KILLS_EQUAL, 50 ) ) {
                        bonus = 1000;
                        send( "Killed 100 Warbirds while in a Warbird this session!" );
                        landmarks[34] = 1;
                    }
                }

                if( landmarks[38] == 0 ) {
                    if( landmarkEventCheck( 1, LM_SHIP_ANY, LM_COMP_RATIO_BETTER_THAN, 2.0 ) ) {
                        if( getTotalKills() > 100 ) {
                            bonus = 4000;
                            send( "Held at least a 2:1 ratio in WB with more than 100 kills!" );
                            landmarks[38] = 1;
                        }
                    }
                }

                break;

            // JAV
            case 2:
                if( landmarks[12] == 0 ) {
                    if( landmarkEventCheck( 2, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 500 ) ) {
                        m_botAction.sendArenaMessage( "BIG BIG LANDMARK:  " + name + " has just made  500 KILLS  in JUST a JAVELIN ALONE this session!!", 1 );
                        bonus = 6000;
                        send( "500 kills in a Javelin this session!  Are you the bounce, or is the bounce... YOU?" );
                        landmarks[12] = 1;
                    }
                }

                if( landmarks[13] == 0 ) {
                    if( landmarkEventCheck( 2, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 250 ) ) {
                        m_botAction.sendArenaMessage( "BIG LANDMARK:  " + name + " has just made  250 KILLS  in JUST a JAVELIN ALONE this session!", 1 );
                        bonus = 4000;
                        send( "250 kills in a Javelin this session!  Been working on your angles in a private arena, haven't you?" );
                        landmarks[13] = 1;
                    }
                }

                if( landmarks[14] == 0 ) {
                    if( landmarkEventCheck( 2, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 100 ) ) {
                        bonus = 1500;
                        send( "100 kills in a Javelin this session!" );
                        landmarks[14] = 1;
                    }
                }

                if( landmarks[42] == 0 ) {
                    if( landmarkEventCheck( 2, 5, LM_COMP_KILLS_EQUAL, 25 ) ) {
                        bonus = 2000;
                        send( "Killed 25 Terrs while in a Jav this session!" );
                        landmarks[42] = 1;
                    }
                }

                break;



            // SPIDER
            case 3:
                if( landmarks[15] == 0 ) {
                    if( landmarkEventCheck( 3, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 500 ) ) {
                        m_botAction.sendArenaMessage( "BIG BIG LANDMARK:  " + name + " has just made  500 KILLS  in JUST a SPIDER ALONE this session!!", 1 );
                        bonus = 5000;
                        send( "500 kills in a Spider this session!  Redrum... redrum..." );
                        landmarks[15] = 1;
                    }
                }

                if( landmarks[16] == 0 ) {
                    if( landmarkEventCheck( 3, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 250 ) ) {
                        m_botAction.sendArenaMessage( "BIG LANDMARK:  " + name + " has just made  250 KILLS  in JUST a SPIDER ALONE this session!", 1 );
                        bonus = 2500;
                        send( "250 kills in a Spider this session!  There is no base you CAN'T defend." );
                        landmarks[16] = 1;
                    }
                }

                if( landmarks[17] == 0 ) {
                    if( landmarkEventCheck( 3, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 100 ) ) {
                        bonus = 1000;
                        send( "100 kills in a Spider this session!" );
                        landmarks[17] = 1;
                    }
                }

                if( landmarks[43] == 0 ) {
                    if( landmarkEventCheck( 3, 5, LM_COMP_KILLS_EQUAL, 25 ) ) {
                        bonus = 2000;
                        send( "Killed 25 Terrs while in a Spider this session!" );
                        landmarks[43] = 1;
                    }
                }

                break;

            // LEVI
            case 4:
                if( landmarks[18] == 0 ) {
                    if( landmarkEventCheck( 4, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 500 ) ) {
                        m_botAction.sendArenaMessage( "BIG BIG LANDMARK:  " + name + " has just made  500 KILLS  in JUST a LEVI ALONE this session!!", 1 );
                        bonus = 4000;
                        send( "500 kills in a Leviathan this session!  Pop goes the ba-ase..." );
                        landmarks[18] = 1;
                    }
                }

                if( landmarks[19] == 0 ) {
                    if( landmarkEventCheck( 4, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 250 ) ) {
                        m_botAction.sendArenaMessage( "BIG LANDMARK:  " + name + " has just made  250 KILLS  in JUST a LEVI ALONE this session!", 1 );
                        bonus = 2000;
                        send( "250 kills in a Leviathan this session!  You like esploshin?" );
                        landmarks[19] = 1;
                    }
                }

                if( landmarks[20] == 0 ) {
                    if( landmarkEventCheck( 4, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 100 ) ) {
                        bonus = 1000;
                        send( "100 kills in a Leviathan this session!" );
                        m_botAction.sendArenaMessage(name + " has made 100 kills in Leviathan this session!");
                        landmarks[20] = 1;
                    }
                }

                if( landmarks[35] == 0 ) {
                    if( landmarkEventCheck( 4, 6, LM_COMP_KILLS_EQUAL, 25 ) ) {
                        bonus = 1500;
                        send( "Killed 25 Weasels while in a Levi this session!" );
                        landmarks[35] = 1;
                    }
                }

                break;

            // TERR
            case 5:
                if( landmarks[21] == 0 ) {
                    if( landmarkEventCheck( 5, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 250 ) ) {
                        m_botAction.sendArenaMessage( "BIG BIG LANDMARK:  " + name + " has just made  500 KILLS  in JUST a TERR ALONE this session!!", 1 );
                        bonus = 10000;
                        send( "250 kills in a Terrier this session!  Those guys have GOT to be getting sick of bursts to the face..." );
                        landmarks[21] = 1;
                    }
                }

                if( landmarks[22] == 0 ) {
                    if( landmarkEventCheck( 5, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 100 ) ) {
                        m_botAction.sendArenaMessage( "BIG LANDMARK:  " + name + " has just made  250 KILLS  in a TERR this session!", 1 );
                        bonus = 5000;
                        send( "100 kills in a Terrier this session!  Make 'em see red..." );
                        landmarks[22] = 1;
                    }
                }

                if( landmarks[23] == 0 ) {
                    if( landmarkEventCheck( 5, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 50 ) ) {
                        bonus = 2000;
                        send( "50 kills in a Terrier this session!" );
                        landmarks[23] = 1;
                    }
                }

                if( landmarks[44] == 0 ) {
                    if( landmarkEventCheck( 5, 5, LM_COMP_KILLS_EQUAL, 25 ) ) {
                        bonus = 2000;
                        send( "Killed 25 Terrs while also in a Terr this session!" );
                        landmarks[44] = 1;
                    }
                }

                break;

            // WEASEL
            case 6:
                if( landmarks[24] == 0 ) {
                    if( landmarkEventCheck( 6, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 750 ) ) {
                        m_botAction.sendArenaMessage( "BIG BIG LANDMARK:  " + name + " has just made  1000 KILLS  in JUST a WEASEL ALONE this session!!  Kill that guy for me, will ya?", 1 );
                        bonus = 10000;
                        send( "750 kills in a Weasel this session!  Man, you have got to be the most annoying person to ever have lived, am I right?" );
                        landmarks[24] = 1;
                    }
                }

                if( landmarks[25] == 0 ) {
                    if( landmarkEventCheck( 6, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 500 ) ) {
                        m_botAction.sendArenaMessage( "BIG LANDMARK:  " + name + " has just made  500 KILLS  in JUST a WEASEL ALONE this session!  The tears of fallen Levis and Terrs blanket the ground.", 1 );
                        bonus = 5000;
                        send( "500 kills in a Weasel this session!  Pew pew pew" );
                        landmarks[25] = 1;
                    }
                }

                if( landmarks[26] == 0 ) {
                    if( landmarkEventCheck( 6, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 250 ) ) {
                        bonus = 2500;
                        send( "250 kills in a Weasel this session!  Have they started to talk about you in pubchat yet?" );
                        landmarks[26] = 1;
                    }
                }

                if( landmarks[27] == 0 ) {
                    if( landmarkEventCheck( 6, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 100 ) ) {
                        bonus = 1000;
                        send( "100 kills in a Weasel this session!  Keep it up, why don't you?" );
                        landmarks[27] = 1;
                    }
                }

                if( landmarks[36] == 0 ) {
                    if( landmarkEventCheck( 6, 4, LM_COMP_KILLS_EQUAL, 50 ) ) {
                        bonus = 1500;
                        send( "Killed 50 Levis while in a Weasel this session!" );
                        landmarks[36] = 1;
                    }
                }

                if( landmarks[45] == 0 ) {
                    if( landmarkEventCheck( 6, 5, LM_COMP_KILLS_EQUAL, 25 ) ) {
                        bonus = 2000;
                        send( "Killed 25 Terrs while in a Weasel this session!" );
                        landmarks[45] = 1;
                    }
                }


                break;

            // LANC
            case 7:
                if( landmarks[28] == 0 ) {
                    if( landmarkEventCheck( 7, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 500 ) ) {
                        m_botAction.sendArenaMessage( "BIG BIG LANDMARK:  " + name + " has just made  500 KILLS  in JUST a LANC ALONE this session!!", 1 );
                        bonus = 5000;
                        send( "500 kills in a Lancaster this session!  And that's how they do it in the big leagues." );
                        landmarks[28] = 1;
                    }
                }

                if( landmarks[29] == 0 ) {
                    if( landmarkEventCheck( 7, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 250 ) ) {
                        m_botAction.sendArenaMessage( "BIG LANDMARK:  " + name + " has just made  250 KILLS  in JUST a LANC ALONE this session!", 1 );
                        bonus = 2500;
                        send( "250 kills in a Lancaster this session!  Will you need two seats for your gun, or four?" );
                        landmarks[29] = 1;
                    }
                }

                if( landmarks[30] == 0 ) {
                    if( landmarkEventCheck( 7, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 100 ) ) {
                        bonus = 1000;
                        send( "100 kills in a Lancaster this session!" );
                        landmarks[30] = 1;
                    }
                }

                if( landmarks[46] == 0 ) {
                    if( landmarkEventCheck( 7, 5, LM_COMP_KILLS_EQUAL, 25 ) ) {
                        bonus = 2000;
                        send( "Killed 25 Terrs while in a Lanc this session!" );
                        landmarks[46] = 1;
                    }
                }

                break;

            // SHARK, MOTHERFUCKER!
            case 8:
                if( landmarks[31] == 0 ) {
                    if( landmarkEventCheck( 4, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 250 ) ) {
                        m_botAction.sendArenaMessage( "BIG BIG LANDMARK:  " + name + " has just made  250 KILLS  in JUST a MOTORFREAKIN' SHARK this session!!!", 1 );
                        bonus = 5000;
                        send( "250 kills in a Shark this session!  And they said it couldn't be done." );
                        landmarks[31] = 1;
                    }
                }

                if( landmarks[32] == 0 ) {
                    if( landmarkEventCheck( 4, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 100 ) ) {
                        m_botAction.sendArenaMessage( "BIG LANDMARK:  " + name + " has just made  100 KILLS  in JUST a FREAKIN' SHARK this session!!", 1 );
                        bonus = 2500;
                        send( "100 kills in a Shark this session!  Somewhere in between rep-rep-rep-die/rep-rep-rep-die you managed to kill some people?!" );
                        landmarks[32] = 1;
                    }
                }

                if( landmarks[33] == 0 ) {
                    if( landmarkEventCheck( 4, LM_SHIP_ANY, LM_COMP_KILLS_EQUAL, 50 ) ) {
                        bonus = 1000;
                        send( "50 kills in a Shark this session, you badass!" );
                        landmarks[33] = 1;
                    }
                }

                if( landmarks[37] == 0 ) {
                    if( landmarkEventCheck( 8, 5, LM_COMP_KILLS_EQUAL, 25 ) ) {
                        bonus = 2000;
                        send( "Killed 25 Terrs while in a Shark this session!" );
                        landmarks[37] = 1;
                    }
                }

                break;

            }



            // ***  LANDMARKS for # ships of type KILLED  ***
            switch( enemyship ) {
            /*
                case 1:

                break;
                case 2:

                break;
                case 3:

                break;
                case 4:

                break;
            */
            case 5:
                if( landmarks[47] == 0 ) {
                    if( landmarkEventCheck( LM_SHIP_ANY, 5, LM_COMP_KILLS_EQUAL, 50 ) ) {
                        bonus = 2000;
                        send( "Killed 50 Terrs this session!" );
                        landmarks[47] = 1;
                    }
                }

                break;
                /*
                    case 6:

                    break;
                    case 7:

                    break;
                    case 8:

                    break;
                */
            }
        }


        public boolean landmarkEventCheck( int pship, int eship, int comparison, int value ) {

            // Compare number of kills
            if( comparison == LM_COMP_KILLS_EQUAL ) {
                if( eship == LM_SHIP_ANY ) {
                    // vs. any ship

                    if( pship == LM_SHIP_ANY )
                        // as any ship
                        return ( getTotalKills() == value );
                    else
                        // as specific ship
                        return ( getTotalKillsInShip( pship ) == value );
                } else {
                    // vs. specific ship

                    if( pship == LM_SHIP_ANY )
                        // as any ship
                        return ( getTotalKillsOfShip( eship ) == value );
                    else
                        // as specific ship
                        return ( getKills( pship, eship ) == value );
                }
            } else if( comparison == LM_COMP_DEATHS_EQUAL ) {
                if( eship == LM_SHIP_ANY ) {
                    // vs. any ship

                    if( pship == LM_SHIP_ANY )
                        // as any ship
                        return ( getTotalDeaths() == value );
                    else
                        // as specific ship
                        return ( getTotalDeathsInShip( pship ) == value );
                } else {
                    // vs. specific ship

                    if( pship == LM_SHIP_ANY )
                        // as any ship
                        return ( getTotalDeathsToShip( eship ) == value );
                    else
                        // as specific ship
                        return ( getDeaths( eship, pship ) == value );
                }
            }

            return false;
        }

        public boolean landmarkEventCheck( int pship, int eship, int comparison, double value ) {
            if( comparison == LM_COMP_RATIO_BETTER_THAN ) {
                if( eship == LM_SHIP_ANY ) {
                    // vs. any ship

                    if( pship == LM_SHIP_ANY )
                        // as any ship
                        return ( getRatioFloat( getTotalKills(), getTotalDeaths() ) >= value );
                    else
                        // as specific ship
                        return ( getRatioFloat( getTotalKillsInShip( pship ), getTotalDeathsInShip( pship ) ) >= value );
                } else {
                    // vs. specific ship

                    if( pship == LM_SHIP_ANY )
                        // as any ship
                        return ( getRatioFloat( getTotalKillsOfShip( eship ), getTotalDeathsToShip( eship ) ) >= value );
                    else
                        // as specific ship
                        return ( getRatioFloat( getKills( pship, eship ), getDeaths( pship, eship ) ) >= value );
                }
            }

            return false;

        }


        public void send( String msg ) {
            if (bonus > 0) {
                m_botAction.sendPrivateMessage( name, "LANDMARK!  " + msg + "  [AWARD: $" + bonus + "]");
                context.getPlayerManager().addMoney(name, bonus);
                bonus = 0;
            } else {
                m_botAction.sendPrivateMessage( name, "LANDMARK!  " + msg );
            }
        }

    }

    @Override
    public void handleSmodCommand(String sender, String command) {
        // TODO Auto-generated method stub

    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[] {};
    }

}
