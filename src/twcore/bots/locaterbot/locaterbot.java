package twcore.bots.locaterbot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.Vector;

import twcore.core.util.Tools;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaList;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.stats.DBPlayerData;

/**
 * Locates physical location of players on the Earth by their IP addresses.
 * @author Austin
 */
public class locaterbot extends SubspaceBot {

	//mySQL database to use
	private String m_sqlHost = "local";

	Vector m_playerIPList;

	public locaterbot( BotAction _botAction ) {

		super( _botAction );

		EventRequester req = _botAction.getEventRequester();

		req.request( EventRequester.MESSAGE );
		req.request( EventRequester.PLAYER_ENTERED );
		req.request( EventRequester.ARENA_LIST );

		m_playerIPList = new Vector();
	}

	public void handleEvent( LoggedOn _event ) {

		m_botAction.joinArena( m_botAction.getBotSettings().getString( "InitialArena" ) );

		TimerTask changeArenas = new TimerTask() {
			public void run() {
				m_botAction.requestArenaList();
			}
		};
		m_botAction.scheduleTaskAtFixedRate( changeArenas, 60000, 60000 );
	}

	public void handleEvent( ArenaList event ) {

		String currentPick = "#robopark";
		while( currentPick.startsWith( "#" ) || event.getSizeOfArena( currentPick ) < 10 )  {
			String[] arenaNames = event.getArenaNames();
			int arenaIndex = (int) (Math.random() * arenaNames.length);
			currentPick = arenaNames[arenaIndex];
		}

		m_botAction.changeArena( currentPick );
	}

	public void handleEvent( PlayerEntered _event ) {

		m_botAction.sendUnfilteredPrivateMessage( _event.getPlayerID(), "*info" );
	}

	public void handleEvent( Message _event ) {

		if( _event.getMessageType() == Message.ARENA_MESSAGE ) {

			String message = _event.getMessage();

	    	if( message.startsWith( "IP:" ) ) {
		    	//Sorts information from *info
		        String[] pieces = message.split("  ");
		        String thisName = pieces[3].substring(10);
		        String thisIP = pieces[0].substring(3);
		        String thisID = pieces[5].substring(10);

		        long ip = translateIP( thisIP );
		        storePlayerLocation( thisName, ip );
             }
		}
	}

	private long translateIP( String _ip ) {

		long realIp = 0;

		StringTokenizer tokenizer = new StringTokenizer( _ip, "." );

		realIp += Long.parseLong( tokenizer.nextToken() ) * (long)256 * (long)256 * (long)256;
		realIp += Long.parseLong( tokenizer.nextToken() ) * (long)256 * (long)256;
		realIp += Long.parseLong( tokenizer.nextToken() ) * (long)256;
		realIp += Long.parseLong( tokenizer.nextToken() );

		return realIp;
	}

	private void storePlayerLocation( String _name, long _ip ) {

		int playerID = getPlayerID( _name );

		if( playerIPEntryExists( playerID ) ) return;


        String qry = "SELECT fnCountryID FROM tblIPLocation " +
        		     "WHERE fnStartAddress <= " + _ip + " AND fnEndAddress >= " + _ip;

        try {
			ResultSet result = m_botAction.SQLQuery( m_sqlHost, qry );

			if( result.next() ) {

				int countryID = result.getInt( "fnCountryID" );
				createPlayerIPEntry( playerID, countryID );
			}
                        m_botAction.SQLClose( result );
		} catch (SQLException e) {
			Tools.printStackTrace(e);
		}
	}

	private boolean playerIPEntryExists( int id ) {

		boolean exists = true;

		String qry = "SELECT fnUserID FROM tblUserLocation WHERE fnUserID = " + id;
		try {
			ResultSet result = m_botAction.SQLQuery( m_sqlHost, qry );
			if( !result.next() ) exists = false;
                        m_botAction.SQLClose( result );
		} catch (SQLException e) {
            Tools.printStackTrace(e);
		}

		return exists;
	}

	private void createPlayerIPEntry( int id, int cid ) {

		String qry = "INSERT INTO tblUserLocation VALUES ("+id+", "+cid+")";
		try {
		    m_botAction.SQLQueryAndClose( m_sqlHost, qry );
		} catch (SQLException e) {
            Tools.printStackTrace(e);
		}
	}

	private int getPlayerID( String player ) {

		DBPlayerData dbPlayer = new DBPlayerData( m_botAction, m_sqlHost, player, true );
		return dbPlayer.getUserID();
	}

}
