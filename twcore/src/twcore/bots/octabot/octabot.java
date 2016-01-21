package twcore.bots.octabot;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FlagDropped;
import twcore.core.events.FlagReward;
import twcore.core.events.FlagVictory;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.SQLResultEvent;
import twcore.core.game.Player;
import twcore.core.util.Tools;

/**
    Hosts the OctaBase event.
*/
public class octabot extends SubspaceBot {

    private HashMap<String, OctaPlayer> players;
    private boolean running = false;
    private boolean prestart = false;

    String database = "website";

    java.util.Date d;
    int timeStart;

    private final static int LVZ_RULES = 10;
    private final static int LVZ_GETREADY = 11;
    private final static int LVZ_GAMESTARTED = 12;
    private final static int LVZ_GAMEOVER = 13;
    private final static int LVZ_FREQ1WON = 14;
    private final static int LVZ_FREQ2WON = 15;


    public octabot( BotAction botAction ) {
        super( botAction );

        players = new HashMap<String, OctaPlayer>();

        EventRequester events = m_botAction.getEventRequester();
        events.request( EventRequester.MESSAGE );
        events.request( EventRequester.ARENA_JOINED );
        events.request( EventRequester.FREQUENCY_SHIP_CHANGE );
        events.request( EventRequester.FREQUENCY_CHANGE );
        events.request( EventRequester.FLAG_VICTORY );
        events.request( EventRequester.PLAYER_ENTERED );
        events.request( EventRequester.PLAYER_DEATH );
        events.request( EventRequester.FLAG_REWARD );
        events.request( EventRequester.FLAG_CLAIMED );
        events.request( EventRequester.FLAG_DROPPED );
    }

    public boolean isIdle() {
        return !running;
    }

    public void handleEvent( Message event ) {

        String message = event.getMessage();
        String name = m_botAction.getPlayerName( event.getPlayerID() );

        // Arena messages
        if( message.startsWith( "Arena LOCKED" ) ) {
            handleLockedState( true );
            return;
        }
        else if( message.startsWith( "Arena UNLOCKED" ) ) {
            handleLockedState( false );
            return;
        }

        message = event.getMessage().toLowerCase();

        // Player commands
        if( message.startsWith( "!rules" ) ) {
            m_botAction.showObjectForPlayer(event.getPlayerID(), LVZ_RULES);
        } else

            // Staff commands (ER+)
            if(m_botAction.getOperatorList().isER( name )) {
                if( message.startsWith( "!start" ))
                    startGame();
                else if( message.startsWith( "!cancel" ))
                    stopGame();
                else if( message.startsWith( "!help" ))
                    displayAccessHelp( name );
                else if( message.startsWith ( "!die" ) && running)
                    m_botAction.sendPrivateMessage(event.getPlayerID(), "The game is still running, !cancel the game first.");
                else if( message.startsWith ( "!die" ) && !running)
                    m_botAction.die("!die by " + name);
            }

    }

    public void handleEvent( FrequencyShipChange event ) {

        int playerId = event.getPlayerID();
        int freq = event.getFrequency();
        Player p = m_botAction.getPlayer( playerId );

        if( !running ) {
            m_botAction.specAll();
            return;
        } else if( p.getShipType() == 0 ) return;

        if( !players.containsKey( p.getPlayerName() ) )
            players.put( p.getPlayerName(), new OctaPlayer( p ) );
        else {
            OctaPlayer oP = players.get( p.getPlayerName() );
            oP.update( p );
        }

        if( event.getShipType() == 6 && getHighScorer( freq ) != playerId ) {
            m_botAction.setShip( playerId, 1 );
            m_botAction.sendPrivateMessage( playerId, "This ship is reserved for the player with the highest score on each team." );
        } else ensureNoneInSpecialShip( getHighScorer( freq ), freq );

        if( event.getFrequency() != 0 && event.getFrequency() != 1 )
            setToLowerTeam( playerId, event.getFrequency() );

        if( prestart )
            warpPlayer( m_botAction.getPlayer( event.getPlayerID() ) );
    }

    public void handleEvent( FrequencyChange event ) {

        if( !running ) return;

        int playerId = event.getPlayerID();
        Player p = m_botAction.getPlayer( playerId );

        if( p.getShipType() == 0 ) return;

        if( !players.containsKey( p.getPlayerName() ) )
            players.put( p.getPlayerName(), new OctaPlayer( p ) );
        else {
            OctaPlayer oP = players.get( p.getPlayerName() );
            oP.update( p );
        }

        if( event.getFrequency() != 0 && event.getFrequency() != 1 )
            setToLowerTeam( playerId, event.getFrequency() );
    }

    public void handleEvent( ArenaJoined event ) {
        running = true;
    }

    public void handleEvent( LoggedOn event ) {

        BotSettings m_botSettings = m_botAction.getBotSettings();

        m_botAction.joinArena( m_botSettings.getString("Arena") );

    }

    public void handleEvent( FlagVictory event ) {

        if( !running || event.getReward() < 1 )
            return;

        storeGameResult( event.getFrequency(), event.getReward() );
        players.clear();
        m_botAction.showObject( LVZ_GAMEOVER );

        final short winFreq = event.getFrequency();

        // Timertask to end the game
        TimerTask endGame = new TimerTask() {
            public void run() {
                if( winFreq == 1)
                    m_botAction.showObject( LVZ_FREQ1WON );

                if( winFreq == 2)
                    m_botAction.showObject( LVZ_FREQ2WON );

                m_botAction.specAll();
                running = false;
                m_botAction.toggleLocked();
            }
        };
        m_botAction.scheduleTask( endGame, 5 * Tools.TimeInMillis.SECOND );

    }

    public void handleEvent( PlayerEntered event ) {

        String say = "Welcome to OctaBase. The arena is currently locked until a game is started.";

        if( running ) {
            say = "Welcome to OctaBase. The game is currently IN PROGRESS. Enter if you would like to play. " +
                  "Type :OctaBot:!rules to see the rules of the game";

            m_botAction.sendPrivateMessage( event.getPlayerID(), say );
        }

    }

    public void handleEvent( PlayerDeath event ) {

        if( !running ) return;

        String killer = m_botAction.getPlayerName( event.getKillerID() );
        String killee = m_botAction.getPlayerName( event.getKilleeID() );

        if( !players.containsKey( killer ) )
            players.put( killer, new OctaPlayer( m_botAction.getPlayer( killer ) ) );

        if( !players.containsKey( killee ) )
            players.put( killee, new OctaPlayer( m_botAction.getPlayer( killee ) ) );

        if( players.containsKey( killer ) ) {
            OctaPlayer p = players.get( killer );
            OctaPlayer o = players.get( killee );
            p.handleEvent( event, true, m_botAction, o );
        }

        if( players.containsKey( killee ) ) {
            OctaPlayer p = players.get( killee );
            OctaPlayer o = players.get( killer );
            p.handleEvent( event, false, m_botAction, o );
        }
    }

    public void handleEvent( FlagReward event ) {

        if( !running ) return;

        int freq = event.getFrequency();
        Iterator<String> it = players.keySet().iterator();

        while( it.hasNext() ) {
            String name = (String)it.next();
            OctaPlayer p = players.get( name );

            if( p.getFrequency() == freq )
                p.addFlagPoints( event.getPoints() );
        }
    }

    public void handleEvent( FlagClaimed event ) {

        if( !running ) return;

        String name = m_botAction.getPlayerName( event.getPlayerID() );

        if( !players.containsKey( name ) )
            players.put( name, new OctaPlayer( m_botAction.getPlayer( name ) ) );

        OctaPlayer p = players.get( name );
        p.update( event );
    }

    public void handleEvent( FlagDropped event ) {

        if( !running ) return;

        String name = m_botAction.getPlayerName( event.getPlayerID() );

        if( !players.containsKey( name ) )
            players.put( name, new OctaPlayer( m_botAction.getPlayer( name ) ) );

        OctaPlayer p = players.get( name );
        p.update( event );
    }

    public void handleEvent(SQLResultEvent event) {
        // Only close query if it belongs to us.
        if( event.getIdentifier().startsWith( "%octa" ) )
            m_botAction.SQLClose( event.getResultSet() );
    }

    public void setToLowerTeam( int playerId, int playerFreq ) {

        Iterator<Player> it = m_botAction.getPlayingPlayerIterator();
        int one = 0;
        int two = 0;

        while( it.hasNext() ) {
            Player p = (Player)it.next();

            if( p.getFrequency() == 0 ) one++;
            else if( p.getFrequency() == 1 ) two++;
        }

        int freq = 0;

        if( one > two ) freq = 1;

        m_botAction.setFreq( playerId, freq );
    }

    public int getHighScorer( int freq ) {

        Iterator<Player> it = m_botAction.getPlayingPlayerIterator();
        int score = 0;
        int topPlayerId = 0;

        while( it.hasNext() ) {
            Player p = (Player)it.next();

            if( p.getScore() > score && freq == p.getFrequency() ) {
                score = p.getScore();
                topPlayerId = p.getPlayerID();
            }
        }

        return topPlayerId;
    }

    public void ensureNoneInSpecialShip( int playerId, int freq ) {

        Iterator<Player> it = m_botAction.getPlayingPlayerIterator();

        while( it.hasNext() ) {
            Player p = (Player)it.next();

            if( p.getShipType() == 6 && p.getPlayerID() != playerId && p.getFrequency() == freq ) {
                m_botAction.setShip( p.getPlayerID(), 1 );
                m_botAction.sendPrivateMessage( p.getPlayerID(), "You are no longer the high scorer." );
            }
        }
    }

    public void startGame() {

        if( running ) return;

        m_botAction.toggleLocked();
        m_botAction.resetFlagGame();
        prestart = true;
        running = true;
        m_botAction.showObject( LVZ_GETREADY );
        m_botAction.sendArenaMessage( "GET READY", LVZ_GETREADY );


        TimerTask startGame = new TimerTask() {
            public void run() {
                m_botAction.showObject( LVZ_GAMESTARTED );
                m_botAction.sendArenaMessage( "GAME STARTED!", Tools.Sound.GOGOGO );
                prestart = false;
                timeStart = (int)(System.currentTimeMillis() / 1000);
                d = new java.util.Date();
            }
        };
        m_botAction.scheduleTask( startGame, 10500 );
    }

    public void stopGame() {

        if( !running ) return;

        running = false;
        m_botAction.toggleLocked();
        m_botAction.specAll();
        m_botAction.sendArenaMessage( "Game has been canceled." );
        players.clear();
    }

    public void handleLockedState( boolean locked ) {

        if( running && locked )
            m_botAction.toggleLocked();

        if( !running && !locked )
            m_botAction.toggleLocked();
    }

    public void displayAccessHelp( String name ) {

        String help[] = {
            "-------------- OctaBot v1.0 ----------------------------",
            "| !start               - starts an Octabase game!!!    |",
            "| !cancel              - stops a game if running       |",
            "| !rules               - displays game 'quick rules'   |",
            "| !die                 - disconnects bot               |",
            "--------------------------------------------------------"
        };
        m_botAction.privateMessageSpam( name, help );
    }

    public void warpPlayer( Player p ) {

        if( p.getFrequency() == 0 )
            m_botAction.warpTo( p.getPlayerID(), 450, 512 );
        else
            m_botAction.warpTo( p.getPlayerID(), 572, 512 );
    }

    public void storeGameResult( int team, int jackpot ) {

        int length = ((int)(System.currentTimeMillis() / 1000)) - timeStart;

        String query = "INSERT INTO `tblOctaGame` (fnWinner, fnJackpot, fnLength, fdDate) VALUES ";
        query += "(" + team + ", " + jackpot + ", " + length + ", NOW())";

        try {
            m_botAction.SQLQueryAndClose( database, query );
        } catch (Exception e) {
            Tools.printStackTrace( "Unable to store game:" , e );
        }

        int id = getGameID();

        Iterator<String> it = players.keySet().iterator();

        while( it.hasNext() ) {
            String name = (String)it.next();
            OctaPlayer p = players.get( name );

            try {
                m_botAction.SQLBackgroundQuery( database, "%octa" + name, p.getQueryString( getUserId( name ), id ) );
            } catch (Exception e) {
                Tools.printStackTrace( "Unable to store player:", e );
            }
        }
    }

    public int getGameID() {

        try {
            String query = "SELECT fnGameID FROM  `tblOctaGame` WHERE 1  ORDER BY fnGameID DESC LIMIT 1";
            ResultSet result = m_botAction.SQLQuery( database, query );
            int gameid = 0;

            if( result.next() )
                gameid = result.getInt( "fnGameID" );

            m_botAction.SQLClose( result );
            return gameid;
        } catch (Exception e) {
            Tools.printStackTrace( "Unable to get gameID:", e );
        }

        return 0;
    }

    public int getUserId( String name ) {

        try {
            ResultSet result = m_botAction.SQLQuery( database, "SELECT fnUserID FROM tblUser WHERE fcUserName = '" + Tools.addSlashesToString(name) + "'" );
            int gameid = 0;

            if( result == null )
                return 0;

            if( result.next() )
                gameid = result.getInt( "fnUserID" );

            m_botAction.SQLClose( result );
            return gameid;
        } catch (Exception e) {
            Tools.printStackTrace( "Unable to get userID:", e );
        }

        return 0;
    }
}
