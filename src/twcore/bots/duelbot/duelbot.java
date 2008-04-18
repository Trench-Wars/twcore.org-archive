//Need to store ALL players by name, no case crap

package twcore.bots.duelbot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.TimerTask;
import java.util.Vector;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.SubspaceBot;
import twcore.core.command.CommandInterpreter;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.game.Player;
import twcore.core.lvz.Objset;
import twcore.core.util.Tools;

/**
 * Handles all parts of PvP dueling as found in ?go duel.
 */
public class duelbot extends SubspaceBot {

	CommandInterpreter  m_commandInterpreter;
	final String		mySQLHost = "website";
    final int           MSG_LIMIT = 8;
	// Used to 'shutdown' the bot and allow no new duels.
	boolean 			shutDown    = false;
    // Disallows new duels and shuts bot down once all dueling has stopped
    boolean             shutDownDie = false;
	String aliasChecker = "";

	Objset objects = m_botAction.getObjectSet();

	//Duel variables read from CGI
	int s_season;			//current season
	int s_spawnLimit;		//# of spawns allowed before Forfeit
    int s_spawnTime;		//Time after death considered a spawn
    int s_noCount;			//Time after double death considered a NC
    int s_lagLimit;			//# of lagouts allowed before Forfeit
    int s_challengeTime;	//How long before a challenge expires
    int s_duelLimit;		//Number of duels allowed per s_duelDays
    int s_duelDays;			//Number of days restriction is put on duels
    long lastZoner;			//Last time someone's streak was zoned.
    String from = "", to = "";
    String shutDownMessage = "";

	//Contains the list of current duels in progress
	HashMap	<Integer,Duel>duels = new HashMap<Integer,Duel>();
	//Contains the list of duel boxes available for dueling
	HashMap	<String,DuelBox>duelBoxes = new HashMap<String,DuelBox>();
	//Contains the list of players with !notplaying ON
	HashMap	<String,NotPlaying>notPlaying = new HashMap<String,NotPlaying>();
	//Contains the list of players currently playing
	HashMap	<String,Duel>playing = new HashMap<String,Duel>();
	//Contains the list of currently issued challenges
	HashMap	<String,DuelChallenge>challenges = new HashMap<String,DuelChallenge>();
	//Contains the list of players lagged out
	HashMap	<String,Lagger>laggers = new HashMap<String,Lagger>();
	//Contains the list of duels scores to update
	HashMap	<Duel,ScoreReport>updates = new HashMap<Duel,ScoreReport>();
	//Contains the list of players that are allowed to !signup.
	HashMap	<String,String>allowedNames = new HashMap<String,String>();
    //Contains the list of players that were allowed to !signup and can now be !enable
    HashSet <String>canEnableNames = new HashSet<String>();
	//Contains the list of league Operators *** SHOULD BE HASHSET ... ***
	HashMap	<String,String>leagueOps    = new HashMap<String,String>();
	//Contains the list of tourny games
	HashMap <Integer,TournyGame>tournyGames	= new HashMap<Integer,TournyGame>();
	//Contains the list of tourny games running.
	HashMap	<Integer,Integer>tournyGamesRunning = new HashMap<Integer,Integer>();

	public duelbot( BotAction botAction ) {
		super( botAction );
		EventRequester events = m_botAction.getEventRequester();
        events.request( EventRequester.MESSAGE );
        events.request( EventRequester.PLAYER_DEATH );
        events.request( EventRequester.PLAYER_POSITION );
        events.request( EventRequester.FREQUENCY_SHIP_CHANGE );
        events.request( EventRequester.PLAYER_LEFT );
        events.request( EventRequester.PLAYER_ENTERED );

        m_commandInterpreter = new CommandInterpreter( m_botAction );
        registerCommands();
	}

	public void registerCommands() {
        int acceptedMessages;

        acceptedMessages = Message.PRIVATE_MESSAGE;
        /*********Player Commands*********/
        m_commandInterpreter.registerCommand( "!yes", acceptedMessages, this, "do_checkTournyDuel" );
        m_commandInterpreter.registerCommand( "!challenge", acceptedMessages, this, "do_issueChallenge" );
        m_commandInterpreter.registerCommand( "!tchallenge", acceptedMessages, this, "do_issueTournyChallenge" );
        m_commandInterpreter.registerCommand( "!removechallenge", acceptedMessages, this, "do_removeChallenge" );
        m_commandInterpreter.registerCommand( "!accept", acceptedMessages, this, "do_acceptChallenge" );
        m_commandInterpreter.registerCommand( "!disable", acceptedMessages, this, "do_disableName" );
        m_commandInterpreter.registerCommand( "!enable", acceptedMessages, this, "do_enableName" );
        m_commandInterpreter.registerCommand( "!score", acceptedMessages, this, "do_showScore" );
        m_commandInterpreter.registerCommand( "!duels", acceptedMessages, this, "do_showDuels" );
        m_commandInterpreter.registerCommand( "!lagout", acceptedMessages, this, "do_lagOut" );
        m_commandInterpreter.registerCommand( "!cancel", acceptedMessages, this, "do_cancelDuel" );
        m_commandInterpreter.registerCommand( "!signup", acceptedMessages, this, "do_signUp" );
    	m_commandInterpreter.registerCommand( "!setrules", acceptedMessages, this, "do_setRules" );
    	m_commandInterpreter.registerCommand( "!notplaying", acceptedMessages, this, "do_setNotPlaying" );
    	m_commandInterpreter.registerCommand( "!scoreboard", acceptedMessages, this, "do_toggleScoreboard" );
    	m_commandInterpreter.registerCommand( "!ops", acceptedMessages, this, "do_showLeagueOps" );
    	m_commandInterpreter.registerCommand( "!lag", acceptedMessages, this, "do_showLag" );
    	m_commandInterpreter.registerCommand( "!rank", acceptedMessages, this, "do_showRank");
    	m_commandInterpreter.registerCommand( "!help", acceptedMessages, this, "do_showHelp" );
    	/*********LeagueOp Commands*********/
    	m_commandInterpreter.registerCommand( "!limit", acceptedMessages, this, "do_setMessageLimit" );
    	m_commandInterpreter.registerCommand( "!version", acceptedMessages, this, "do_showVersion" );
    	m_commandInterpreter.registerCommand( "!allowuser", acceptedMessages, this, "do_allowUser" );
    	m_commandInterpreter.registerCommand( "!banuser", acceptedMessages, this, "do_banUser" );
    	m_commandInterpreter.registerCommand( "!unbanuser", acceptedMessages, this, "do_unbanUser" );
    	m_commandInterpreter.registerCommand( "!banned", acceptedMessages, this, "do_sayBanned" );
    	m_commandInterpreter.registerCommand( "!comment", acceptedMessages, this, "do_reComment" );
    	m_commandInterpreter.registerCommand( "!readcomment", acceptedMessages, this, "do_getComment" );
    	m_commandInterpreter.registerCommand( "!setgreet", acceptedMessages, this, "do_setGreetMessage" );
    	m_commandInterpreter.registerCommand( "!shutdown", acceptedMessages, this, "do_shutDown" );
    	m_commandInterpreter.registerCommand( "!alias", acceptedMessages, this, "do_aliasCheck" );
    	m_commandInterpreter.registerCommand( "!disableuser", acceptedMessages, this, "do_opDisableName" );
        m_commandInterpreter.registerCommand( "!shutdowndie", acceptedMessages, this, "do_shutDownDie" );
    	m_commandInterpreter.registerCommand( "!die", acceptedMessages, this, "do_die" );
    	m_commandInterpreter.registerCommand( "!zone", acceptedMessages, this, "do_twelZoner" );


    	m_commandInterpreter.registerDefaultCommand( Message.ARENA_MESSAGE, this, "do_checkArena" );
    }

	public void do_showVersion( String name, String message ) {
		m_botAction.sendSmartPrivateMessage( name, "1.40" );
	}


    /***********************************************
    *              Player Commands                 *
    ***********************************************/

    public void do_signUp( String name, String message ) {
    	m_botAction.sendUnfilteredPrivateMessage( name, "*info" );
    }

    public void do_setRules( String name, String message ) {
    	String pieces[] = message.toLowerCase().split(" ");
    	int winby2 = 0;
    	int nc = 0;
    	int warp = 0;
    	int kills = 10;
    	for( int i = 0; i < pieces.length; i++ ){
    	    try{
    	        int numKills = Integer.parseInt(pieces[i]);
    	        if(numKills <= 15 && numKills > 0)
    	            kills = numKills;
    	    }catch(NumberFormatException e){
    	        if( pieces[i].equals( "winby2" ) ) winby2 = 1;
    	        else if( pieces[i].equals( "nc" ) ) nc = 1;
    	        else if( pieces[i].equals( "warp" ) ) warp = 1;
    	    }
    	}
    	try {
    		ResultSet result = m_botAction.SQLQuery( mySQLHost, "SELECT * FROM tblDuelPlayer WHERE fcUserName = '"+Tools.addSlashesToString(name)+"'" );
    		if( result.next() ) {
    			m_botAction.SQLQuery( mySQLHost, "UPDATE tblDuelPlayer SET fnGameKills = "+kills+", fnWinBy2 = "+winby2+", fnNoCount = "+nc+", fnDeathWarp = "+warp+" WHERE fcUserName = '"+Tools.addSlashesToString(name)+"'" );
    			String rules = "Rules: First to "+kills;
    			if( winby2 == 1 ) rules += ", Win By 2";
    			if( nc == 1 ) rules += ", No Count (nc) Double Kills";
    			if( warp == 1 ) rules += ", Warp On Deaths";
    			m_botAction.sendSmartPrivateMessage( name, rules );
    		} else m_botAction.sendSmartPrivateMessage( name, "You must be signed up before you can change your rules." );
                m_botAction.SQLClose( result );
    	} catch (Exception e) { m_botAction.sendSmartPrivateMessage( name, "Unable to change rules." ); }

    }

    public void do_issueChallenge( String _name, String _message ) {
    	_name = _name.toLowerCase();
    	//Shutdown mode check
    	if( shutDown ) {
    		m_botAction.sendPrivateMessage( _name, "Currently in 'ShutDown' mode, no new duels may begin at this time: " + shutDownMessage );
    		return;
    	}
    	//Player banned check
    	if( sql_banned( _name ) ) {
    		m_botAction.sendPrivateMessage( _name, "You have been banned from this league." );
    		return;
    	}

    	//Get this player from the database
    	DuelPlayer player = sql_getPlayer( _name );

    	//Signed up check
    	if( player == null ) {
    		m_botAction.sendPrivateMessage( _name, "Unable to issue challenge, you have not signed up or have disabled this name." );
    		return;
    	}
    	//Notplaying challenger check
    	if( notPlaying.containsKey( _name ) ) {
    		m_botAction.sendPrivateMessage( _name, "Unable to issue challenge, you have enabled 'notplaying', toggle it off with !notplaying." );
    		return;
    	}

    	//Get the gametype requested and the opponent
    	String pieces[] = _message.split( ":" );
    	int gameType = 0;
    	String opponent = pieces[0];
    	try { gameType = Integer.parseInt( pieces[1] ); } catch (Exception e) {}
    	opponent = m_botAction.getFuzzyPlayerName( opponent );

    	//Can't challenge a player who does not exist
    	if( opponent == null ) {
    		m_botAction.sendPrivateMessage( _name, "Unable to issue challenge, "+pieces[0] +" is not in this arena." );
    		return;
    	}
    	opponent = opponent.toLowerCase();
    	if( opponent.equals( _name ) ) {
    		m_botAction.sendPrivateMessage( _name, "Unable to issue challenge, you cannot challenge yourself." );
    		return;
    	}
    	if( notPlaying.containsKey( opponent ) ) {
    		NotPlaying np = notPlaying.get( opponent );
    		if( !np.timeUp( ((int)System.currentTimeMillis() / 1000 )) ) {
	    		m_botAction.sendPrivateMessage( _name, "Unable to issue challenge, "+opponent+ " has enabled notplaying." );
	    		return;
	    	} else notPlaying.remove( opponent );
    	}
    	if( gameType < 1 || gameType > 3 ) {
    		m_botAction.sendPrivateMessage( _name, "Unable to issue challenge, invalid league entered, valid leagues: (1-warbird, 2-javelin, 3-spider.)" );
    		return;
    	}
    	if( playing.containsKey( _name ) ) {
    		m_botAction.sendPrivateMessage( _name, "Unable to issue challenge, you are already dueling." );
    		return;
    	}
    	if( playing.containsKey( opponent ) ) {
    		m_botAction.sendPrivateMessage( _name, "Unable to issue challenge, "+opponent +" is already dueling." );
    		return;
    	}
    	if( !boxOpen( gameType ) ) {
    		m_botAction.sendPrivateMessage( _name, "There is currently no duel box open for that league, please challenge again when a box opens up." );
    		return;
    	}
    	DuelPlayer enemy = sql_getPlayer( opponent );
    	if( enemy == null ) {
    		m_botAction.sendPrivateMessage( _name, opponent + " has not signed up for this league." );
    		return;
    	}
    	if( sql_gameLimitReached( _name, opponent, gameType ) ) {
    		m_botAction.sendPrivateMessage( _name, "Unable to issue challenge, you are only allowed "+s_duelLimit+" duels per user per "+s_duelDays+" day(s)." );
    		return;
    	}
    	if( challenges.containsKey( _name+opponent ) )
    		challenges.remove( _name+opponent );
    	String type = "";
    	if( gameType == 1 ) type = "Warbird";
    	else if( gameType == 2 ) type = "Javelin";
    	else if( gameType == 3 ) type = "Spider";
    	String rules = "Rules: First to " + player.getToWin();
    	if( player.getWinBy2() ) rules += ", Win By 2";
    	if( player.getNoCount() ) rules += ", No Count (nc) Double Kills";
    	if( player.getDeathWarp() ) rules += ", Warp On Deaths";

    	int lag1 = player.getAverageLag();
    	int lag2 = enemy.getAverageLag();
    	String avgLag1, avgLag2;
    	if(lag1 > 0)
    		avgLag1 = String.valueOf(lag1);
    	else
    		avgLag1 = "Unknown";
    	if(lag2 > 0)
    		avgLag2 = String.valueOf(lag2);
    	else
    		avgLag2 = "Unknown";

    	challenges.put( _name+opponent, new DuelChallenge( _name, opponent, player, gameType ) );
    	m_botAction.sendPrivateMessage( _name, "Your challenge has been issued to '" + opponent + "'" );
    	m_botAction.sendPrivateMessage( _name, rules );
    	m_botAction.sendPrivateMessage( _name, opponent + "'s average lag: " + avgLag2);
    	m_botAction.sendPrivateMessage( opponent, _name + " is challenging you to a "+type+" duel. PM me with, !accept "+_name+" to accept." );
    	m_botAction.sendPrivateMessage( opponent, rules );
    	m_botAction.sendPrivateMessage( opponent, _name + "'s average lag: " + avgLag1);

    }

    public void do_issueTournyChallenge( String name, String message ) {
    	if( shutDown ) {
    		m_botAction.sendPrivateMessage( name, "Currently in 'ShutDown' mode, no new duels may begin at this time: " + shutDownMessage );
    		return;
    	}
    	if( sql_banned( name ) ) {
    		m_botAction.sendPrivateMessage( name, "You have been banned from this league." );
    		return;
    	}

    	DuelPlayer player = sql_getPlayer( name );
    	if( player == null ) {
    		m_botAction.sendPrivateMessage( name, "Unable to issue challenge, you have not signed up or have disabled this name." );
    		return;
    	}
    	if( notPlaying.containsKey( name ) ) {
    		m_botAction.sendPrivateMessage( name, "Unable to issue challenge, you have enabled 'notplaying', toggle it off with !notplaying." );
    		return;
    	}



    	//Check for the proper game
    	int gid = -1;
    	int idOne = -1;
    	int idTwo = -1;
    	String pOne;
    	String pTwo;
    	int gameType;
    	int realGameId;
    	int players;
    	if(message.indexOf(":") > -1) {
    		String pieces[] = message.split(":");
    		try {
    			gid = sql_getTournyGameID(Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1]));
    		} catch(Exception e) {}
    	} else {
    		try { gid = Integer.parseInt( message ); } catch (Exception e) {}
    	}

    	//Pull the appropriate game.
    	try {
			String query = "SELECT fnGameId, fnLeagueTypeID, fnTournyUserOne, fnTournyUserTwo, fnTotalPlayers, fnGameNumber FROM tblDuelTournyGame AS TG, tblDuelTourny T WHERE T.fnTournyID = TG.fnTournyID AND fnGameID = "+gid+" AND TG.fnStatus = 0";
			ResultSet result = m_botAction.SQLQuery( "local", query );
			if( result.next() ) {

				idOne = result.getInt( "fnTournyUserOne" );
				idTwo = result.getInt( "fnTournyUserTwo" );
				pOne = sql_getName( result.getInt( "fnTournyUserOne" ) );
				pTwo = sql_getName( result.getInt( "fnTournyUserTwo" ) );
				gameType = result.getInt( "fnLeagueTypeID" );
				realGameId = result.getInt( "fnGameNumber" );
				players = result.getInt( "fnTotalPlayers" );
			} else {
				m_botAction.sendSmartPrivateMessage( name, "That game ID does not exist." );
				return;
			}
                        m_botAction.SQLClose( result );
		} catch (Exception e) {
			m_botAction.sendSmartPrivateMessage( name, "An error has occured, please contact a league op."+e );
			return;
		}

    	//Figure out who to challenge
    	String opponent;
    	if( name.equalsIgnoreCase( pOne ) )
    		opponent = pTwo;
    	else if( name.equalsIgnoreCase( pTwo ) )
    		opponent = pOne;
    	else {
    		m_botAction.sendSmartPrivateMessage( name, "You are not a player in this duel." );
    		return;
    	}

    	opponent = m_botAction.getFuzzyPlayerName( opponent );
    	if( opponent == null ) {
    		m_botAction.sendPrivateMessage( name, "Unable to issue challenge, your opponent is not in this arena." );
    		return;
    	}
    	opponent = opponent.toLowerCase();
    	if( notPlaying.containsKey( opponent ) ) {
    		NotPlaying np = notPlaying.get( opponent );
    		if( !np.timeUp( ((int)System.currentTimeMillis() / 1000 )) ) {
	    		m_botAction.sendPrivateMessage( name, "Unable to issue challenge, "+opponent+ " has enabled notplaying." );
	    		return;
	    	} else notPlaying.remove( opponent );
    	}
    	if( playing.containsKey( name ) ) {
    		m_botAction.sendPrivateMessage( name, "Unable to issue challenge, you are already dueling." );
    		return;
    	}
    	if( playing.containsKey( opponent ) ) {
    		m_botAction.sendPrivateMessage( name, "Unable to issue challenge, "+opponent +" is already dueling." );
    		return;
    	}
    	if( !boxOpen( gameType ) ) {
    		m_botAction.sendPrivateMessage( name, "There is currently no duel box open for that league, please challenge again when a box opens up." );
    		return;
    	}
    	if( challenges.containsKey( name+opponent ) )
    		challenges.remove( name+opponent );
    	String type = "";
    	if( gameType == 1 ) type = "Warbird";
    	else if( gameType == 2 ) type = "Javelin";
    	else if( gameType == 3 ) type = "Spider";
    	String rules = "Rules: First to " + 20;
    	rules += ", Win By 2";
    	rules += ", No Count (nc) Double Kills";
    	rules += ", Warp On Deaths";

    	name = m_botAction.getFuzzyPlayerName( name );
    	opponent = m_botAction.getFuzzyPlayerName( opponent );
    	if(name == null || opponent == null) return;
    	name = name.toLowerCase();
    	opponent = opponent.toLowerCase();
    	pOne = m_botAction.getFuzzyPlayerName( pOne );
    	pTwo = m_botAction.getFuzzyPlayerName( pTwo );
    	pOne = pOne.toLowerCase();
    	pTwo = pTwo.toLowerCase();
    	TournyGame tg = new TournyGame( gid, pOne, pTwo, idOne, idTwo, gameType, realGameId, players );
    	tg.setResponse(name.toLowerCase());
    	tournyGames.put(gid, tg);
    	m_botAction.sendPrivateMessage( name, "Your challenge has been issued to '" + opponent + "' (TOURNAMENT DUEL)" );
    	m_botAction.sendPrivateMessage( name, rules );
    	m_botAction.sendPrivateMessage( opponent, name + " is challenging you to a "+type+" duel. PM me with, !yes "+gid+" to accept. (TOURNAMENT DUEL)" );
    	m_botAction.sendPrivateMessage( opponent, rules );

    }

    public void do_acceptChallenge( String name, String message ) {
    	name = name.toLowerCase();
    	if( shutDown ) {
    		m_botAction.sendPrivateMessage( name, "Currently in 'ShutDown' mode, no new duels may begin at this time: " + shutDownMessage );
    		return;
    	}
    	if( sql_banned( name ) ) {
    		m_botAction.sendPrivateMessage( name, "You have been banned from this league." );
    		return;
    	}
    	String challenger = m_botAction.getFuzzyPlayerName( message );
    	if( notPlaying.containsKey( name ) ) {
    		m_botAction.sendPrivateMessage( name, "Unable to accept challenge, you have enabled 'notplaying', toggle it off with !notplaying." );
    		return;
    	}
    	if( challenger == null ) {
    		m_botAction.sendPrivateMessage( name, "Unable to accept challenge, `"+message+"` has not challenged you or is not in this arena." );
    		return;
    	}
    	challenger = challenger.toLowerCase();
    	if( !challenges.containsKey( challenger+name ) ) {
    		m_botAction.sendPrivateMessage( name, "Unable to accept challenge, `"+challenger+"` has not challenged you" );
    		return;
    	}
    	if( notPlaying.containsKey( challenger ) ) {
    		m_botAction.sendPrivateMessage( name, "Unable to accept challenge, `"+challenger+"` has enabled notplaying." );
    		return;
    	}
    	if( playing.containsKey( name ) ) {
    		m_botAction.sendPrivateMessage( name, "Unable to accept challenge, you are already dueling." );
    		return;
    	}
    	if( playing.containsKey( challenger ) ) {
    		m_botAction.sendPrivateMessage( name, "Unable to accept challenge, `"+challenger+"` is already dueling." );
    		return;
    	}
    	DuelChallenge thisChallenge = challenges.get( challenger+name );
    	if( thisChallenge == null ) {
    		m_botAction.sendPrivateMessage( name, "Unable to accept challenge, `"+challenger+"` has not challenged you." );
    		return;
    	}
    	if( thisChallenge.getElapsedTime() > s_challengeTime ) {
    		challenges.remove( challenger+name );
    		m_botAction.sendPrivateMessage( name, "Unable to accept challenge, the challenge has expired." );
    		return;
    	}
    	DuelBox thisBox = getDuelBox( thisChallenge.getGameType() );
    	//Add queue system for dueling
    	if( thisBox == null ) {
    		m_botAction.sendPrivateMessage( name, "Unable to accept challenge, all duel boxes are full." );
    		return;
    	}

    	duels.put( new Integer( thisBox.getBoxNumber() ), new Duel( thisBox, thisChallenge ) );

    	startDuel( duels.get( new Integer( thisBox.getBoxNumber() ) ), challenger, name );
    	playing.put( challenger, duels.get( new Integer( thisBox.getBoxNumber() ) ) );
    	playing.put( name, duels.get( new Integer( thisBox.getBoxNumber() ) ) );

    }

    public void do_removeChallenge( String name, String message ) {
    	name = name.toLowerCase();
    	String opponent = m_botAction.getFuzzyPlayerName( message );
    	if(opponent == null) return;
    	opponent = opponent.toLowerCase();
    	if( !challenges.containsKey( name+opponent ) ) {
    		m_botAction.sendPrivateMessage( name, "Unable to remove challenge, no such challenge exists." );
    		return;
    	}
    	challenges.remove( name+opponent );
    	m_botAction.sendPrivateMessage( name, "Your challenge to " + opponent + " has been removed." );
    }

    public void do_disableName( String name, String message ) {

    	if( !sql_enabledUser( name ) ) {
    		m_botAction.sendSmartPrivateMessage( name, "This account is already disabled or does not exist." );
    		return;
    	}

    	if( sql_banned( name ) ) {
    		m_botAction.sendPrivateMessage( name, "You have been banned from this league." );
    		return;
    	}

    	sql_disableUser( name, true );
    	m_botAction.sendSmartPrivateMessage( name, "Your username has been disabled and has suffered a -300 rating loss in each league." );
    	m_botAction.sendSmartPrivateMessage( name, "To reenable this account use !enable, please note all other accounts must be disabled first." );

    }

    public void do_enableName( String name, String message ) {

    	if( sql_enabledUser( name ) ) {
    		m_botAction.sendSmartPrivateMessage( name, "This name is already enabled for play." );
    		return;
    	}

    	ResultSet info = sql_getUserIPMID( name );

    	if( info == null ) {
    		m_botAction.sendSmartPrivateMessage( name, "Problem accessing info from database.  Please make sure you have done !signup before !enable." );
            return;
        }

    	try {
    		String IP = info.getString( "fcIP" );
    		String MID = info.getString( "fnMID" );
    		ResultSet result = m_botAction.SQLQuery( mySQLHost, "SELECT fcUserName FROM tblDuelPlayer WHERE fnEnabled = 1 AND fcIP = '"+IP+"' AND fnMID = '"+MID+"'" );
            boolean okToEnable = canEnableNames.remove( name );
    		if( result.next() && !okToEnable ) {
    			String extras = "";
    			do {
    				extras += " " + result.getString( "fcUserName" ) + " ";
    			} while( result.next() );
    			m_botAction.sendSmartPrivateMessage( name, "To enable this name you must disable: ("+extras+")" );
    		} else {
    			sql_enableUser( name );
    			m_botAction.sendSmartPrivateMessage( name, "Your name has been enabled to play." );
    		}
            m_botAction.SQLClose( result );
    	} catch (Exception e) {
    	    // This exception is caught frequently.  Removed stack trace print
			// Don't need to see it anymore until we take the time to deal w/ it.
    		Tools.printStackTrace(e);
			m_botAction.sendSmartPrivateMessage( name, "Problem retreiving your info from database.  Please try again later, or talk to a staff member." );
    	}
        m_botAction.SQLClose( info );
    }

    public void do_cancelDuel( String name, String message ) {
    	name = name.toLowerCase();
    	if( !playing.containsKey( name ) ) {
    		m_botAction.sendPrivateMessage( name, "You are not playing a duel." );
    		return;
    	}
    	Duel d = playing.get( name );
    	String opponent = d.getOpponent( name );
    	boolean state = d.toggleCancelGame( name );
    	if( state && d.getCancelState( opponent ) ) {
	    	m_botAction.sendPrivateMessage( name, "Your duel has been cancelled.", 103 );
			m_botAction.sendPrivateMessage( opponent, "Your duel has been cancelled.", 103 );
			m_botAction.sendTeamMessage( name + " and " + opponent + " have cancelled their " + d.getLeagueType() + " duel");
			d.toggleDuelBox();
			duels.remove( new Integer( d.getBoxNumber() ) );
			playing.remove( name );
			playing.remove( opponent );
			m_botAction.spec( name );
			m_botAction.spec( name );
			m_botAction.spec( opponent );
			m_botAction.spec( opponent );
			if( laggers.containsKey( name ) ) {
				m_botAction.cancelTask(laggers.get( name ));
				laggers.remove( name );
			}
			if( laggers.containsKey( opponent ) ) {
                m_botAction.cancelTask(laggers.get( opponent ));
				laggers.remove( opponent );
			}
			clearScoreboard( d );
    	} else if( state ) {
    		m_botAction.sendPrivateMessage( name, "You have opted to end this duel. Your opponent must also use !cancel to cancel the duel." );
    		m_botAction.sendPrivateMessage( opponent, name + " has opted to end this duel. Use !cancel to end the duel." );
    	} else {
    		m_botAction.sendPrivateMessage( name, "You have removed your decision to cancel this duel." );
    		m_botAction.sendPrivateMessage( opponent, name + " has removed his decision to cancel this duel." );
    	}
    }

    public void do_setNotPlaying( String name, String message ) {
    	name = name.toLowerCase();
    	if( notPlaying.containsKey( name ) ) {
    		m_botAction.sendPrivateMessage( name, "Notplaying has been turned off." );
    		notPlaying.remove( name );
    		return;
    	}
    	int time = 0;
    	try { time = Integer.parseInt( message ); } catch (Exception e ) {}
    	if( time == 0 ) {
    		m_botAction.sendPrivateMessage( name, "Incorrect Usage of !notplaying:    !notplaying <time>" );
    		return;
    	}
    	if( time < 10 ) time = 10;
    	if( time > 360 ) time = 360;
    	notPlaying.put( name, new NotPlaying((int)System.currentTimeMillis() / 1000 , time ) );
    	m_botAction.sendPrivateMessage( name, "Notplaying mode has been turned on for " + time + " minutes. Send !notplaying again to toggle it off." );
    }

    public void do_toggleScoreboard( String name, String message ) {
    	name = name.toLowerCase();
    	if( !playing.containsKey( name ) ) {
    		m_botAction.sendPrivateMessage( name, "You are not playing a duel." );
    		return;
    	}
    	Duel d = playing.get( name );
    	if( d.toggleScoreboard( name ) )
  			m_botAction.sendPrivateMessage( name, "Scoreboard on." );
  		else
  			m_botAction.sendPrivateMessage( name, "Scoreboard off." );
  		setScoreboard( d, 0 );

    }

    public void do_showLeagueOps( String name, String message ) {
    	String ops = "League Operators:  ";
    	Iterator<String> it = leagueOps.keySet().iterator();
    	while( it.hasNext() ) {
    		if( it.hasNext() )
    			ops += (String)it.next() + ", ";
    		else
    			ops += (String)it.next();
    	}
    	m_botAction.sendPrivateMessage( name, ops );
    }

    public void do_showLag( String name, String message ) {
    	String player = m_botAction.getFuzzyPlayerName( message );
    	if( player == null ) {
    		m_botAction.sendPrivateMessage( name, "Unable to find player." );
    		return;
    	}
    	player = player.toLowerCase();
    	if( !from.equals("") ) {
    		m_botAction.sendPrivateMessage( name, "Please try again in a few seconds." );
    		return;
    	}
    	from = player; to = name;
    	m_botAction.sendUnfilteredPrivateMessage( player, "*lag" );
    }

    public void do_showScore( String name, String message ) {
    	String player = m_botAction.getFuzzyPlayerName( message );
        int i = -1;
        if( player == null ) {
            try {
                i = Integer.parseInt( message );
            } catch (Exception e) {
                m_botAction.sendPrivateMessage( name, "Can't read a player name or box # from your input.  Please check the name/number and try again." );
                return;
            }
            if( duels.containsKey( new Integer( i ) ) ) {
                m_botAction.sendPrivateMessage( name, duels.get( new Integer( Integer.parseInt(message) ) ).showScore() );
            } else {
                m_botAction.sendPrivateMessage( name, "No score available for requested duel (box is probably empty)." );
            }
        } else {
            player = player.toLowerCase();
            if( playing.containsKey( player ) ) {
                m_botAction.sendPrivateMessage( name, playing.get( player ).showScore() );
            } else {
                m_botAction.sendPrivateMessage( name, "No score available for requested duel (player is probably not playing)." );
            }
        }
    }

    public void do_showDuels( String name, String message ) {
    	String wbOut = "Warbird Duels: ";
    	String javOut = "Javelin Duels: ";
    	String spiOut = "Spider Duels : ";
    	int wb = 0, jav = 0, spi = 0;
    	Set<Integer> set = duels.keySet();
    	Iterator<Integer> it = set.iterator();
    	while( it.hasNext() ) {
    		Integer duel = (Integer)it.next();
    		Duel d = duels.get( duel );
    		if( d.getShipType() == 1 ) {
    			wb++;
    			wbOut += d.getBoxNumber() + "  ";
    		} else if( d.getShipType() == 2 ) {
    			jav++;
    			javOut += d.getBoxNumber() + "  ";
    		} else if( d.getShipType() == 3 ) {
    			spi++;
    			spiOut += d.getBoxNumber() + "  ";
    		}
    	}
    	m_botAction.sendPrivateMessage( name, Tools.formatString( wbOut, 60 ) + wb + " duels" );
    	m_botAction.sendPrivateMessage( name, Tools.formatString( javOut, 60 ) + jav + " duels" );
    	m_botAction.sendPrivateMessage( name, Tools.formatString( spiOut, 60 ) + spi + " duels" );
    	m_botAction.sendPrivateMessage( name, "Use !score <box#> for more info on a duel." );

    }

    /** Called from a !lagout command, used to place a player back into a duel.
     * To use this command:
     * 	- must be playing in a duel
     *  - must be a spectator
     * @param _name Name of the player requesting a lagout
     * @param _message Anything else the player may have sent with the command
     */
    public void do_lagOut( String _name, String _message ) {
    	_name = _name.toLowerCase();
    	//Check for rules on using this command
    	if( !playing.containsKey( _name ) ) {
    		m_botAction.sendPrivateMessage( _name, "You are not playing a duel." );
    		return;
    	}
    	if( m_botAction.getPlayer( _name ).getShipType() > 0 ) {
			m_botAction.sendPrivateMessage( _name, "You are already in. " );
			return;
		}

    	//Get the duel associated with this player
    	Duel duel = playing.get( _name );

    	//Get the stats object associated with this player
    	DuelPlayerStats playerStats = duel.getPlayer( _name );

    	//Set the player to warping
    	playerStats.setWarping( true );

    	//Put the player back into the game
    	m_botAction.setShip( _name, duel.getPlayer( _name ).getShip() );
    	m_botAction.setFreq( _name, duel.getPlayer( _name ).getFreq() );
    	WarpPoint p = duel.getRandomWarpPoint();
		m_botAction.warpTo( _name, p.getXCoord(), p.getYCoord() );

		//Set the player to not warping
		//duel.getPlayer( _name ).setData( 9, 0 );

		//Remove any lag timers for this player
		if( laggers.containsKey( _name ) ) {
            m_botAction.cancelTask(laggers.get( _name ));
			laggers.remove( _name );
		}

		setScoreboard( duel, 0 );
    }

    public void do_checkTournyDuel( String name, String message ) {
    	name = name.toLowerCase();
    	System.out.println( "CHECK: "+name );

    	int gid = -1;
    	try { gid = Integer.parseInt( message ); } catch (Exception e) {}
    	if( tournyGames.containsKey( new Integer( gid ) ) ) {

    		TournyGame tg = tournyGames.get( new Integer( gid ) );
    		if( !tg.hasPlayer( name ) ) {
    			m_botAction.sendSmartPrivateMessage( name, "You are not a player in duel #"+gid );
    			return;
    		}
    		if( tg.hasExpired() ) {
    			m_botAction.sendSmartPrivateMessage( name, "This automated tourny duel has expired, please use !tchallenge <gid>" );
    			tournyGames.remove( new Integer( gid ) );
    			return;
    		}
    		if( tg.setResponse( name.toLowerCase() ) ) {
    			m_botAction.sendSmartPrivateMessage( name, "You have already responded as being available for this duel." );
    		} else {
    			m_botAction.sendSmartPrivateMessage( name, "You have responded as being available for this duel. It will start automatically if your opponent is also available within 5 minutes." );
    			sql_updateTournyAvailability( tg.getGameId(), tg.getPlayerNumber( name ) );
    			if( tg.bothReady() ) {
    				m_botAction.sendSmartPrivateMessage( name, "Your "+tg.getType()+" tournament duel will begin in 60 seconds. If you do not show you will forfeit." );
    				m_botAction.sendSmartPrivateMessage( tg.getOpponent( name ), "Your "+tg.getType()+" tournament duel will begin in 60 seconds. If you do not show you will forfeit." );
    				StartDuel d = new StartDuel( tg, this );
    				m_botAction.scheduleTask( d, 60000 );
    			}
    		}
    	} else m_botAction.sendSmartPrivateMessage( name, "That game ID does not exist."+gid );
    }

    public void do_showRank(String name, String message) {
        // Old rank code too taxing for a table with high number of rows.
        // To compensate, !rank could offer the player more stats instead,
        // including streak, spawns, etc.
    	try {
    		String rankCheck = name;
            boolean ranked = false;
	    	if(message != null && !(message.equals("") || message.equals(" "))) rankCheck = message;
	    	ResultSet results = m_botAction.SQLQuery(mySQLHost, "SELECT fnLeagueTypeID, fnRating FROM tblDuelLeague WHERE fcUserName = '" + Tools.addSlashesToString(rankCheck) +"' AND fnSeason = "+ s_season + " ORDER BY fnLeagueTypeID");
	    	while( results.next() ) {
	    	    if( results.getInt("fnLeagueTypeID") == 1 ) {
	    	        m_botAction.sendPrivateMessage(name, "TWE-D Rating: " + results.getInt("fnRating") );
                    ranked = true;
	    	    } else if( results.getInt("fnLeagueTypeID") == 2 ) {
	    	        m_botAction.sendPrivateMessage(name, "TWE-J Rating: " + results.getInt("fnRating") );
                    ranked = true;
	    	    } else if( results.getInt("fnLeagueTypeID") == 3 ) {
	    	        m_botAction.sendPrivateMessage(name, "TWE-S Rating: " + results.getInt("fnRating") );
                    ranked = true;
	    	    }
	    	}
            if( !ranked )
                m_botAction.sendPrivateMessage(name, rankCheck + " is not currently rated." );
	    	m_botAction.SQLClose( results );
	    } catch(Exception e) {e.printStackTrace();}
    }

    /***********************************************
    *                 Help Messages                *
    ***********************************************/

    public void do_showHelp( String name, String message ) {
		String help[] = {
			"--Player commands-------------------------------------------------------------",
			"| !signup                      - signs you up for the dueling league.        |",
			"| !setrules <included rules>   - sets rules, include rules you wish to use   |",
			"|   available rules: winby2, nc, warp, 5/10 : Ex  !setrules warp 5           |",
			"| !challenge <name>:<type>     - challenges <name> to a duel wb=1/jav=2/sp=3 |",
			"| !tchallenge <gID>            - challenges your opponent for game #<gID>    |",
			"| !tchallenge <gN>:<league>    - challenges your opponent in playoffs        |",
			"| !accept    <name>            - accepts a challenge from <name>             |",
			"| !removechallenge <name>      - removes the challenge issued to <name>      |",
			"| !notplaying <time>           - turns on notplaying for requested <time>    |",
			"| !notplaying                  - toggles off notplaying                      |",
			"| !lagout                      - puts you back in to your duel               |",
			"| !cancel                      - toggles your decision to cancel your duel   |",
			"| !lag <name>                  - returns the lag of player <name>            |",
			"| !ops                         - shows list of league operators              |",
			"| !score <box#> or <player>    - shows the score of a duel # or a player     |",
			"| !duels                       - shows the current duels being played        |",
			"| !scoreboard                  - turns the scoreboard on/off                 |",
			"| !enable                      - enables your username                       |",
			"| !disable                     - disables your username                      |",
			"| !rank                        - displays your ranks for all leagues         |",
			"| !rank <name>                 - displays <name>'s ranks for all leagues     |",
			"------------------------------------------------------------------------------"

			};
		m_botAction.privateMessageSpam( name, help );
		if(leagueOps.containsKey(name.toLowerCase())) {
			String help2[] = {
				" ",
				"--Operator commands-----------------------------------------------------------",
				"| !allowuser <name>         - Allows <name> to register.                     |",
				"| !banuser <name>:<comment> - Bans <name> from TWEL.                         |",
				"| !unbanuser <name>         - Unbans <name>                                  |",
				"| !banned                   - Lists all banned  users.                       |",
				"| !comment <name>:<comment> - Recomments <name>'s ban.                       |",
				"| !readcomment <name>       - Gets the ban comment for <name>                |",
				"| !alias <name>             - Checks the database for <name>'s aliases.      |",
				"| !disableuser <name>       - Disables <name> in the database.               |",
                "| OTHERS: !die (kills bot)    !shutdown (prevents new duels)                 |",
                "|      !shutdowndie  (prevents new games, kills bot when no duels are active)|",
				"------------------------------------------------------------------------------"
				};
			m_botAction.privateMessageSpam( name, help2 );
		}

	}

    /***********************************************
    *             Operator Commands                *
    ***********************************************/

    public void do_die( String name, String message ) {
    	if( !(leagueOps.containsKey( name.toLowerCase() ) || m_botAction.getOperatorList().isSmod(name)) ) return;
		//Removes the bot from the server.
    	m_botAction.die();
    }

    public void do_twelZoner( String name, String message ) {
    	if( !(leagueOps.containsKey( name.toLowerCase() ) || m_botAction.getOperatorList().isSmod(name)) ) return;
    	m_botAction.sendZoneMessage(message + " -TWEL Staff",2);
    }

    public void do_setMessageLimit(String name, String message) {
    	if( !(leagueOps.containsKey( name.toLowerCase() ))) return;

    	int limit = MSG_LIMIT;
    	try {
    		limit = Integer.parseInt(message);
    	} catch(Exception e) {}

    	m_botAction.setMessageLimit(limit);
    }

    public void do_shutDown( String name, String message ) {
    	if( !(leagueOps.containsKey( name.toLowerCase() ) || m_botAction.getOperatorList().isSmod(name)) ) return;
    	shutDownMessage = message;
    	if( shutDown ) {
    		m_botAction.sendPrivateMessage( name, "Shutdown mode turned off." );
    		shutDown = false;
    	} else {
    		m_botAction.sendPrivateMessage( name, "Shutdown mode turned on." );
    		shutDown = true;
    	}
    }

    public void do_shutDownDie( String name, String message ) {
        if( !(leagueOps.containsKey( name.toLowerCase() ) || m_botAction.getOperatorList().isSmod(name)) ) return;
        shutDownMessage = message;
        if( shutDownDie ) {
            m_botAction.sendPrivateMessage( name, "Shutdown+Die mode turned off." );
            shutDown = false;
            shutDownDie = false;
        } else {
            m_botAction.sendPrivateMessage( name, "Shutdown+Die mode turned on." );
            shutDown = true;
            shutDownDie = true;
            if( duels.size() == 0 ) {
                m_botAction.sendArenaMessage( "Shutting down for core maintenance.", 1 );
                TimerTask dieTask = new TimerTask() {
                    public void run() {
                        m_botAction.die();
                    }
                };
                m_botAction.scheduleTask(dieTask, 5000);
            }
        }
    }

    public void do_allowUser( String name, String message ) {
    	if( !leagueOps.containsKey( name.toLowerCase() ) ) return;
    	try{
    	    ResultSet result = m_botAction.SQLQuery("local", "SELECT * FROM tblDuelPlayer WHERE fcUserName='" + Tools.addSlashesToString(message) + "'");
    	    if(result.next()){
    	        if(result.getInt("fnEnabled") == 1)
    	            m_botAction.sendSmartPrivateMessage( name, "This user is already enabled to play.");
    	        else{
    	            sql_enableUser(message);
    	            m_botAction.sendSmartPrivateMessage( name, message + " should now be enabled.");
    	        }
    	    }else{    	    
    	        allowedNames.put( message, message );
    	        m_botAction.sendPrivateMessage( name, message + " should now be able to !signup." );
    	    }
    	    m_botAction.SQLClose(result);
    	}catch(SQLException e){
    	    m_botAction.sendSmartPrivateMessage( name, "Error querying database.");
    	    Tools.printStackTrace(e);
    	}    	
    }

    public void do_banUser( String name, String message ) {
    	if( !leagueOps.containsKey( name.toLowerCase() ) ) return;

    	String pieces[] = message.split(":", 2);
    	if(pieces.length != 2) {
    		m_botAction.sendPrivateMessage(name, "Please provide a ban comment (!banuser name:comment).");
    		return;
    	}

    	String player = m_botAction.getFuzzyPlayerName( pieces[0] );
    	if( player == null ) player = pieces[0];
    	if( sql_banPlayer( player, pieces[1] ) )
    		m_botAction.sendPrivateMessage( name, player + " has been banned." );
    	else
    		m_botAction.sendPrivateMessage( name, "Unable to ban user " + player );
    }

    public void do_unbanUser( String name, String message ) {
        if( !leagueOps.containsKey( name.toLowerCase() ) ) return;

        String player = m_botAction.getFuzzyPlayerName( message );
        if( player == null ) player = message;
        if( sql_unbanPlayer( player ) )
            m_botAction.sendPrivateMessage( name, player + " has been unbanned." );
        else
            m_botAction.sendPrivateMessage( name, "Unable to unban user " + player );
    }

    public void do_sayBanned( String name, String message) {
    	if( !leagueOps.containsKey( name.toLowerCase() ) ) return;

    	m_botAction.sendPrivateMessage(name, "Banned players: ");
    	sql_bannedPlayers(name);
    }

    public void do_reComment( String name, String message) {
    	if( !leagueOps.containsKey( name.toLowerCase() ) ) return;

    	String pieces[] = message.split(":", 2);
    	if(pieces.length != 2) {
    		m_botAction.sendPrivateMessage(name, "Please provide a ban comment (!banuser name:comment).");
    		return;
    	}

    	String player = m_botAction.getFuzzyPlayerName( pieces[0] );
    	if( player == null ) player = pieces[0];

    	sql_recommentBan(name, player, pieces[1]);
    }

    public void do_getComment( String name, String message) {
    	if( !leagueOps.containsKey( name.toLowerCase() ) ) return;

    	String player = m_botAction.getFuzzyPlayerName( message );
    	if( player == null ) player = message;
    	sql_getComment( name, message );
    }

    public void do_setGreetMessage( String name, String message ) {
    	if( !leagueOps.containsKey( name.toLowerCase() ) ) return;

    	m_botAction.sendUnfilteredPublicMessage( "?set misc:greetmessage:"+message );
    	m_botAction.sendPrivateMessage( name, "Greet Set: " + message );
    }

    public void do_aliasCheck(String name, String message) {
    	if( !leagueOps.containsKey( name.toLowerCase() ) ) return;

    	if( sql_enabledUser( message ) ) {
    		m_botAction.sendSmartPrivateMessage( name, "This name is already enabled for play." );
    		return;
    	}

    	ResultSet info = sql_getUserIPMID( message );

    	if( info == null ) {
    		String player = m_botAction.getFuzzyPlayerName( message );
		   	if(player == null) {
		   		m_botAction.sendSmartPrivateMessage(name, "This player is not in the arena right now.");
		   		return;
		   	}
		   	aliasChecker = name;
		   	m_botAction.sendUnfilteredPrivateMessage(player, "*info");
            return;
    	}


    	try {
    		String IP = info.getString( "fcIP" );
    		String MID = info.getString( "fnMID" );
    		ResultSet result = m_botAction.SQLQuery( mySQLHost, "SELECT fcUserName FROM tblDuelPlayer WHERE fnEnabled = 1 AND fcIP = '"+IP+"' AND fnMID = '"+MID+"'" );
    		if( result.next() ) {
    			String extras = "";
    			do {
    				extras += " " + result.getString( "fcUserName" ) + " ";
    			} while( result.next() );
    			m_botAction.sendSmartPrivateMessage( name, "Aliases registered: " + extras );
                m_botAction.SQLClose( result );
    			return;
    		}
            m_botAction.SQLClose( result );

    	} catch (Exception e) {
    	    // This exception is caught frequently.  Removed stack trace print
			// Don't need to see it anymore until we take the time to deal w/ it.
    		Tools.printStackTrace(e);
			m_botAction.sendSmartPrivateMessage( name, "Problem retreiving your info from database.  Please try again later, or talk to a staff member." );
    	}
        m_botAction.SQLClose( info );
    }

    public void do_opDisableName(String name, String message) {
    	if( !leagueOps.containsKey( name.toLowerCase() ) ) return;
    	String player = m_botAction.getFuzzyPlayerName( message );
    	if(player == null) player = message;
    	sql_disableUser(player,false);
    	m_botAction.sendSmartPrivateMessage(name, player + " disabled.");
    }

    /***********************************************
    *          Various Bot Called Methods          *
    ***********************************************/

    public void do_checkArena( String name, String message ) {
    	if( message.startsWith( "IP:" ) ) {
	    	//Sorts information from *info
	        String[] pieces = message.split("  ");
	        String thisName = pieces[3].substring(10);
	        String thisIP = pieces[0].substring(3);
	        String thisID = pieces[5].substring(10);
	        do_addPlayer( thisName, thisIP, thisID );
	     } else if( message.equals( "Arena UNLOCKED" ) ) {
	     	m_botAction.toggleLocked();
	     } else if( message.startsWith( "PING Current:" ) ) {
	     	String pieces[] = message.split(" ");
	     	String output = pieces[4] + " ms  " + pieces[10] + " ms";
	     	output = message.substring( 0, message.length()-16 );
	     	if(to != null)
	     		m_botAction.sendPrivateMessage( to, from + ":  " + output );
	     	int average = Integer.parseInt(pieces[4].substring(pieces[4].indexOf(":") + 1));
	     	sql_lagInfo(from, average);
	     	to = "";
	     	from = "";
	     }
    }

    public void do_addPlayer( String name, String IP, String MID ) {
    	try {
    		ResultSet result = m_botAction.SQLQuery( mySQLHost, "SELECT fcUserName FROM tblDuelPlayer WHERE fnEnabled = 1 AND fcIP = '"+IP+"' AND fnMID = '"+MID+"' OR fcUserName = '"+Tools.addSlashesToString(name)+"'" );
    		if( !result.next() ) {
    			DBPlayerData player = new DBPlayerData( m_botAction, "local", name, true );
    			m_botAction.SQLQueryAndClose( mySQLHost, "INSERT INTO tblDuelPlayer (`fnUserID`, `fcUserName`, `fcIP`, `fnMID`, `fnLag`, `fnLagCheckCount`, `fdLastPlayed`) VALUES ("+player.getUserID()+", '"+Tools.addSlashesToString(name)+"', '"+IP+"', '"+MID+"', 0, 0, NOW())" );

    			//Removed as of season 2
    			//sql_createLeagueData( player );
    			if(aliasChecker.equals(""))
    				m_botAction.sendPrivateMessage( name, "You have been registered to use this bot. It is advised you set your personal dueling rules, for further information use !help" );
    			else {
    				m_botAction.sendPrivateMessage(aliasChecker, "This player has no registered aliases.");
    				aliasChecker = "";
    			}
    		} else {
    			if( result.getString( "fcUserName" ).equalsIgnoreCase( name )) {
    				if(aliasChecker.equals(""))
    					m_botAction.sendSmartPrivateMessage( name, "You have already signed up." );
    				else {
    					m_botAction.sendSmartPrivateMessage( aliasChecker, "This player is already signed up.");
    					aliasChecker = "";
    				}
    			} else {
    				if( allowedNames.containsKey( name ) && aliasChecker.equals("") ) {
    					DBPlayerData player = new DBPlayerData( m_botAction, "local", name, true );
		    			m_botAction.SQLQuery( mySQLHost, "INSERT INTO tblDuelPlayer (`fnUserID`, `fcUserName`, `fcIP`, `fnMID`, `fnLag`, `fnLagCheckCount`, `fdLastPlayed`) VALUES ("+player.getUserID()+", '"+Tools.addSlashesToString(name)+"', '"+IP+"', '"+MID+"', 0, 0, NOW())" );

		    			//Removed as of season 2
		    			//sql_createLeagueData( player );

		    			m_botAction.sendPrivateMessage( name, "You have been registered to use this bot. It is advised you set your personal dueling rules, for further information use !help" );
                        allowedNames.remove( name );
                        canEnableNames.add( name );
    				} else {
						String extras = "";
    					do {
    						extras += " " + result.getString( "fcUserName" ) + " ";
    					} while( result.next() );
    					if(aliasChecker.equals("")) {
    					    m_botAction.sendSmartPrivateMessage( name, "It appears you already have other names signed up for TWEL or have registered this name already." );
    						m_botAction.sendSmartPrivateMessage( name, "Please login with these names: ("+extras+") and use the command !disable, you will then be able to signup a new name." );
    						m_botAction.sendSmartPrivateMessage( name, "All names disabled suffer a 300 point rating loss. If you have further problems please contact a league op." );
    					} else {
    						m_botAction.sendSmartPrivateMessage( aliasChecker, "Aliases registered: " + extras);
    						aliasChecker = "";
    					}
    				}
    			}
    		}
                m_botAction.SQLClose( result );
    	} catch (Exception e) { Tools.printStackTrace( "Failed to signup new user", e ); }
	}

	public boolean boxOpen( int gameType ) {
    	int i = 0;
    	Iterator<String> it = duelBoxes.keySet().iterator();
    	while( it.hasNext() ) {
    		String key = (String)it.next();
    		DuelBox b = duelBoxes.get( key );
    		if( !b.inUse() && b.gameType( gameType ) ) i++;
    	}
    	if( i == 0 ) return false;
    	else return true;
    }

    public DuelBox getDuelBox( int gameType ) {
    	Vector <DuelBox>v = new Vector<DuelBox>();
    	Iterator<String> it = duelBoxes.keySet().iterator();
    	while( it.hasNext() ) {
    		String key = (String)it.next();
    		DuelBox b = duelBoxes.get( key );
    		if( !b.inUse() && b.gameType( gameType ) ) v.add( b );
    	}
    	if( v.size() == 0 )
    		return null;
    	else {
    		Random generator = new Random();
    		return v.elementAt( generator.nextInt( v.size() ) );
    	}
    }

    public void startDuel( Duel d, String p1, String p2 ) {

    	int freq = d.getBoxFreq();
    	int ship = d.getShipType();
    	m_botAction.setShip( p1, ship );
    	m_botAction.setFreq( p1, freq );
    	m_botAction.setShip( p2, ship );
    	m_botAction.setFreq( p2, freq+1 );
    	m_botAction.warpTo( p1, d.getSafeXOne(), d.getSafeYOne() );
    	m_botAction.warpTo( p2, d.getSafeXTwo(), d.getSafeYTwo() );
    	m_botAction.sendPrivateMessage( p1, "Duel Begins in 15 Seconds Against '"+p2+"'", 2 );
    	m_botAction.sendUnfilteredPrivateMessage( p1, "*lag");
    	to = null;
    	from = p1;
    	m_botAction.sendPrivateMessage( p2, "Duel Begins in 15 Seconds Against '"+p1+"'", 2 );
    	m_botAction.scheduleTask( new GameStartTimer( d, p1, p2, m_botAction ), 15000 );


    	m_botAction.sendTeamMessage( "A "+d.getLeagueType()+" duel is starting between " + p1 + " and " + p2 + " in box # " +(d.getBoxFreq()/2) );
    	setScoreboard( d, 0 );

    }

    public void endDuel( Duel d, String winner, String loser, int type ) {
    	//0 - normal, 1 - spawning, 2 - warping, 3 - lagouts, 4 - 1 min lagout
    	finalOff(d.getLeagueId(), d.getBoxNumber(), d.getPlayerOne().getKills(), d.getPlayerTwo().getKills());
    	m_botAction.sendUnfilteredPrivateMessage(d.getPlayerTwo().getName(), "*lag");
    	to = null;
    	from = d.getPlayerTwo().getName();

    	if( laggers.containsKey( winner ) ) {
            m_botAction.cancelTask(laggers.get( winner ));
			laggers.remove( winner );
		}
    	if( laggers.containsKey( loser ) ) {
            m_botAction.cancelTask(laggers.get( loser ));
			laggers.remove( loser );
		}

    	int winnerScore = d.getPlayer( winner ).getKills();
    	int loserScore = d.getPlayer( loser ).getKills();

    	if( type == 0 ) {
	    	m_botAction.sendPrivateMessage( winner, "You have defeated '" + loser + "' score: ("+winnerScore+"-"+loserScore+")");
			m_botAction.sendPrivateMessage( loser, "You have been defeated by '" + winner + "' score: ("+loserScore+"-"+winnerScore+")");
			m_botAction.sendTeamMessage( winner + " defeats " + loser + " in " + d.getLeagueType() + " score: ("+winnerScore+"-"+loserScore+")");
		} else if( type == 1 ) {
			m_botAction.sendPrivateMessage( loser, "You have forfeited your duel because you abused the spawning rule.", 103 );
			m_botAction.sendPrivateMessage( winner, loser + " has forfeited due to abusing spawning.", 103 );
			m_botAction.sendTeamMessage( loser + " forfeits to " + winner + " (SPAWNING) in their " + d.getLeagueType() + " duel");
		} else if( type == 2 ) {
			m_botAction.sendPrivateMessage( loser, "You have forfeited your duel due to abuse of warping.", 103 );
			m_botAction.sendPrivateMessage( winner, loser + " has forfeited due to abuse of warping.", 103 );
			m_botAction.sendTeamMessage( loser + " forfeits to " + winner + " (WARPING) in their " + d.getLeagueType() + " duel");
		} else if( type == 3 ) {
			m_botAction.sendPrivateMessage( loser, "You have forfeited your duel due to lagging out too many times.", 103 );
			m_botAction.sendPrivateMessage( winner, loser + " has forfeited due to lagging out too many times.", 103 );
			m_botAction.sendTeamMessage( loser + " forfeits to " + winner + " (LAGOUTS) in their " + d.getLeagueType() + " duel");
		} else if( type == 4 ) {
			m_botAction.sendPrivateMessage( loser, "You have been lagged out for over 1 minute and forfeit your duel.", 103 );
			m_botAction.sendPrivateMessage( winner, loser + " has been lagged out for over 1 minute and forfeits.", 103 );
			m_botAction.sendTeamMessage( loser + " forfeits to " + winner + " (1 MIN LAGOUT) in their " + d.getLeagueType() + " duel");
		}


		DBPlayerData winnerInfo = new DBPlayerData( m_botAction, "local", winner, true );
		DBPlayerData loserInfo = new DBPlayerData( m_botAction, "local", loser, true );



		int matchType = d.getLeagueId();
		d.toggleDuelBox();
		duels.remove( new Integer( d.getBoxNumber() ) );
		playing.remove( winner );
		playing.remove( loser );
		m_botAction.spec( winner );
		m_botAction.spec( winner );
		m_botAction.spec( loser );
		m_botAction.spec( loser );


		sql_verifyRecord( loser, loserInfo.getUserID(), matchType );
		sql_verifyRecord( winner, winnerInfo.getUserID(), matchType );

		ResultSet player1 = sql_getUserInfo( loserInfo.getUserID(), matchType );
		ResultSet player2 = sql_getUserInfo( winnerInfo.getUserID(), matchType );

		try {
			//Calculate new streaks.
			int loserStreak = player1.getInt( "fnLossStreak" );
			int loserCurStreak = player1.getInt( "fnCurrentLossStreak" ) + 1;
			if( loserStreak < loserCurStreak ) loserStreak = loserCurStreak;
			int winnerStreak = player2.getInt( "fnWinStreak" );
			int winnerCurStreak = player2.getInt( "fnCurrentWinStreak" ) + 1;
			if( winnerStreak < winnerCurStreak ) winnerStreak = winnerCurStreak;

			if((lastZoner + 60 * 60 * 1000) < System.currentTimeMillis() && winnerCurStreak > 5)
				streakZoner(winner, winnerCurStreak, d.getLeagueType().toLowerCase());

			boolean aced = false;
			if( d.getPlayer( loser ).getKills() == 0 ) aced = true;

			//Calculate new ratings.

			int loserRatingBefore = player1.getInt( "fnRating" );
			int winnerRatingBefore = player2.getInt( "fnRating" );
			int ratingDifference = loserRatingBefore - winnerRatingBefore;
			double p1 = 1.0 / ( 1 + Math.pow( 10.0, -ratingDifference / 400.0 ) );
			double p2 = 1.0 - p1;
			int loserRatingAfter = (int)(loserRatingBefore + d.toWin()*5.0*(0.0 - p1 ));
			int winnerRatingAfter = (int)(winnerRatingBefore + d.toWin()*5.0*(1.0 - p2 ));

			if( d.toWin() == 5 )
				aced = false;

			//Store loser information
			sql_storeUserLoseRating( loser, loserInfo.getUserID(), matchType, d.getPlayer( loser ).getKills(), d.getPlayer( loser ).getDeaths(), loserStreak, loserCurStreak, winner, d.getPlayer( loser ).getSpawns(), d.getPlayer( winner ).getSpawns(), d.getPlayer( loser ).getLagouts(), d.getTime(), loserRatingAfter, aced );
			//Stores winner information
			sql_storeUserWinRating( winner, winnerInfo.getUserID(), matchType, d.getPlayer( winner ).getKills(), d.getPlayer( winner ).getDeaths(), winnerStreak, winnerCurStreak, loser, d.getPlayer( winner ).getSpawns(), d.getPlayer( loser ).getSpawns(), d.getPlayer( winner ).getLagouts(), d.getTime(), winnerRatingAfter, aced );

			String query = "INSERT INTO `tblDuelMatch` (`fnSeason`, `fnLeagueTypeID`, `fnWinnerScore`, `fnLoserScore`, `fcWinnerName`, `fcLoserName`, `fnWinnerUserID`, `fnLoserUserID`, `fnCommentID`, `fnWinBy2`, `fnNoCount`, `fnDeathWarp`, `fnDeaths`, `fnDuration`, `fnUser1RatingBefore`, `fnUser1RatingAfter`, `fnUser2RatingBefore`, `fnUser2RatingAfter` ) VALUES (";
			query += s_season+", "+matchType+", "+winnerScore+", "+loserScore+", ";
			query += "'"+Tools.addSlashesToString(winner)+"', '"+Tools.addSlashesToString(loser)+"', ";
			query += winnerInfo.getUserID()+", "+loserInfo.getUserID()+", ";
			query += type+", ";
			if( d.winBy2() ) query+="1, "; else query+="0, ";
			if( d.noCount() ) query+="1, "; else query+="0, ";
			if( d.deathWarp() ) query+="1, "; else query+="0, ";
			query+=d.toWin()+", "+d.getTime()+", ";
			query += winnerRatingBefore+", "+winnerRatingAfter+", ";
			query += loserRatingBefore+", "+loserRatingAfter+")";
                         m_botAction.SQLQueryAndClose( mySQLHost, query );
		} catch (Exception e) { Tools.printStackTrace( "Error ending duel", e );}
                m_botAction.SQLClose( player1 );
                m_botAction.SQLClose( player2 );
		try {
			if(tournyGamesRunning.containsKey((d.getBoxFreq() / 2))) {
				int gID = tournyGamesRunning.get((d.getBoxFreq() / 2));
				tournyGamesRunning.remove((d.getBoxFreq() / 2));
				TournyGame tg = tournyGames.get(gID);
				ResultSet results = m_botAction.SQLQuery( mySQLHost, "SELECT fnMatchID FROM `tblDuelMatch` ORDER BY fnMatchID DESC LIMIT 0,1");
				results.next();
				sql_updateTournyMatchData(gID, results.getInt("fnMatchID"), tg.getPlayerNumber(winner));
                                m_botAction.SQLClose( results );
				updatePlayoffBracket(winner, loser, d.getLeagueId(), gID);  // 191252 vs 1637
                                m_botAction.SQLClose( results );															// 292939 vs 3635
			}
                         m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuelLeague SET fnInactive = 0 WHERE fnUserID = "+winnerInfo.getUserID()+" AND fnLeagueTypeID = "+type);
                         m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuelLeague SET fnInactive = 0 WHERE fnUserID = "+loserInfo.getUserID()+" AND fnLeagueTypeID = "+type);
                         m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuelPlayer SET fdLastPlayed = NOW() WHERE fcUserName = '"+Tools.addSlashesToString(winner)+"' OR fcUserName = '"+Tools.addSlashesToString(loser)+"'");

		} catch(Exception e) {}

		clearScoreboard( d );

        if( shutDownDie && duels.size() == 0 ) {
            m_botAction.sendArenaMessage( "Shutting down for core maintenance.", 1 );
            TimerTask dieTask = new TimerTask() {
                public void run() {
                    m_botAction.die();
                }
            };
            m_botAction.scheduleTask(dieTask, 5000);
        }
    }

    public void do_testTourny(String name, String message) {
    	if(!leagueOps.containsKey(name.toLowerCase())) return;
    	try {
    		String pieces[] = message.split(":");
	    	int gID = Integer.parseInt(pieces[0]);
	    	int winID = Integer.parseInt(pieces[1]);
	    	String winner = pieces[2];
	    	String loser = pieces[3];
	    	int type = Integer.parseInt(pieces[4]);
	    	sql_updateTournyMatchData(gID, 0, winID);
	    	updatePlayoffBracket(winner, loser, type, gID);
	    } catch(Exception e) {}
    }

    public void updateInactives() {
    	int selects = 1;
    	int updates = 0;
    	try {
    		ResultSet results = m_botAction.SQLQuery(mySQLHost, "SELECT fnUserID FROM tblDuelPlayer WHERE fdLastPlayed < SUBDATE(NOW(), INTERVAL 14 DAY)");
	    	while(results.next()) {
	    		m_botAction.SQLQuery(mySQLHost, "UPDATE tblDuelLeague SET fnInactive = 1 WHERE fnUserID = "+results.getInt("fnUserID"));
	    		updates++;
	    		for(int i = 1;i <= 3;i++) {
	    			ResultSet result = m_botAction.SQLQuery(mySQLHost, "SELECT fnRating FROM tblDuelLeague WHERE fnLeagueTypeID = "+i+" AND fnUserID = "+results.getInt("fnUserID"));
	    			selects++;
	    			if(result.next())
		    			if(result.getInt("fnRating") > 1000) {
		    				m_botAction.SQLQuery(mySQLHost, "UPDATE tblDuelLeague SET fnRating = fnRating - 1 WHERE fnLeagueTypeID = "+i+" AND fnUserID = "+results.getInt("fnUserID"));
	    					updates++;
	    				}
                                m_botAction.SQLClose( result );
		    	}
	    	}
                m_botAction.SQLClose( results );
	    } catch(Exception e) {}
    }


    public void setScoreboard( Duel d, int extra ) {

    	int player1 = m_botAction.getPlayerID( d.getPlayerOne().getName() );
    	int player2 = m_botAction.getPlayerID( d.getPlayerTwo().getName() );

    	int leftScore = d.getPlayerOne().getKills();
    	int rightScore = d.getPlayerTwo().getKills();

    	//Reset board
    	objects.hideAllObjects( player1 );
    	objects.hideAllObjects( player2 );

    	//Show scoreboard
    	objects.showObject( player1, 5 );
    	objects.showObject( player2, 5 );


    	//Winby2
    	if( d.winBy2() ) {
    		objects.showObject( player1, 10 );
    		objects.showObject( player2, 10 );
    	}
    	//Warp
    	if( d.deathWarp() ) {
    		objects.showObject( player1, 11 );
    		objects.showObject( player2, 11 );
    	}
    	//No Count
    	if( d.noCount() ) {
    		objects.showObject( player1, 12 );
    		objects.showObject( player2, 12 );
    	}

    	//Spawn or double kill
    	if( extra == 1 ) {
    		objects.showObject( player1, 0 );
    		objects.showObject( player2, 0 );
    	} else if( extra == 2 ) {
    		objects.showObject( player1, 1 );
    		objects.showObject( player2, 1 );
    	}

    	//x- --
    	objects.showObject( player1, 110 + (leftScore - leftScore%10)/10 );
    	objects.showObject( player2, 110 + (rightScore - rightScore%10)/10 );


    	//-x --
    	objects.showObject( player1, 100 + leftScore%10 );
    	objects.showObject( player2, 100 + rightScore%10 );

    	//-- x-
    	objects.showObject( player1, 210 + (rightScore - rightScore%10)/10 );
    	objects.showObject( player2, 210 + (leftScore - leftScore%10)/10 );

    	//-- -x

    	objects.showObject( player1, 200 + rightScore%10 );
    	objects.showObject( player2, 200 + leftScore%10 );

    	if( !d.scoreboard( 1 ) )
    		objects.hideAllObjects( player1 );
    	m_botAction.setObjects( player1 );


    	if( !d.scoreboard( 2 ) )
    		objects.hideAllObjects( player2 );
    	m_botAction.setObjects( player2 );

    }

    public void clearScoreboard( Duel d ) {
    	//Shutoff scoreboard
		int player1 = m_botAction.getPlayerID( d.getPlayerOne().getName() );
    	int player2 = m_botAction.getPlayerID( d.getPlayerTwo().getName() );
    	objects.hideAllObjects( player1 );
    	objects.hideAllObjects( player2 );
    	m_botAction.setObjects( player1 );
    	m_botAction.setObjects( player2 );
    }

    public void streakZoner(String name, int streak, String ship)
    {
    	if(streak >= 10 && streak < 15)
    		m_botAction.sendZoneMessage(name + " is on a roll, ?go "+ m_botAction.getArenaName() + " to try to stop this " + ship + "'s " + streak + " game winning streak! -" + m_botAction.getBotName(), 2);
    	else if(streak >= 15 && streak < 20)
    		m_botAction.sendZoneMessage(name + " is on fire with a " + streak + " game winning streak in " + ship + ". Come stop him before he burns down ?go " + m_botAction.getArenaName() + " -"+ m_botAction.getBotName(), 2);
    	else if(streak >= 20)
    		m_botAction.sendZoneMessage("Someone bring the kryptonite to ?go "+ m_botAction.getArenaName() + ", " + name + " has a " + streak + " game winning streak in " + ship + "! -" + m_botAction.getBotName(), 2);

    	lastZoner = System.currentTimeMillis();
    }

    public void warpPlayers(String one, String two)
    {
    	Duel d = playing.get(one);

    	if(d.getPlayerNumber( one ) == 1) {
    		m_botAction.warpTo(one, d.getXOne(), d.getYOne());
    		m_botAction.warpTo(two, d.getXTwo(), d.getYTwo());
    	}
    	else {
    		m_botAction.warpTo(two, d.getXOne(), d.getYOne());
    		m_botAction.warpTo(one, d.getXTwo(), d.getYTwo());
    	}
    }

    /***********************************************
    *                 Events                       *
    ***********************************************/

    public void handleEvent( Message event ) {
		m_commandInterpreter.handleEvent( event );

		String message = event.getMessage();
		if(event.getMessageType() == Message.PRIVATE_MESSAGE) {
			String name = m_botAction.getPlayerName(event.getPlayerID());
			if(name != null && message != null && leagueOps.containsKey(name.toLowerCase())) {
				if(message.toLowerCase().startsWith("!test ")) {
					do_testTourny(name, message.substring(6));
				}
			}
		}
		//if( message.startsWith( "!yes " ) && event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE )
		//	do_checkTournyDuel( event.getMessager(), message.substring( 5, message.length() ) );
    }

    public void handleEvent( LoggedOn event ) {
    	//join initial arena
    	BotSettings m_botSettings = m_botAction.getBotSettings();
        m_botAction.joinArena( m_botSettings.getString( "Arena" + m_botAction.getBotNumber()) );

        //Sets up all variables for new features that I can't think of a good comment for
        lastZoner = System.currentTimeMillis() - (30 * 60 * 1000);

        //Create new box Objects
        int boxCount = m_botSettings.getInt( "BoxCount" );
        for( int i = 1; i <= boxCount; i++ ) {
        	String boxset[] = m_botSettings.getString( "Box"+ i ).split( "," );
        	String warps[] = m_botSettings.getString( "Warp" + i ).split( "," );
        	if( boxset.length == 9 )
        		duelBoxes.put( ""+i, new DuelBox( boxset, warps, i ) );
        }
        //Reads in general settings for dueling
        s_season = m_botSettings.getInt( "Season" );
        s_spawnLimit = m_botSettings.getInt( "SpawnLimit" );
        s_spawnTime = m_botSettings.getInt( "SpawnTime" );
        s_noCount = m_botSettings.getInt( "NoCount" );
        s_lagLimit = m_botSettings.getInt( "LagLimit" );
        s_challengeTime = m_botSettings.getInt( "ChallengeTime" );
        s_duelLimit = m_botSettings.getInt( "DuelLimit" );
        s_duelDays = m_botSettings.getInt( "DuelDays" );
        //Reads in the league operators
        String ops[] = m_botSettings.getString( "LeagueOps" ).split( "," );
        for( int i = 0; i < ops.length; i++ )
        	leagueOps.put( ops[i].toLowerCase(), ops[i] );
        //Puts the arena in 'ready' state
        m_botAction.toggleLocked();
        m_botAction.specAll();
        m_botAction.setReliableKills( 1 );
        m_botAction.setMessageLimit( MSG_LIMIT );

        setupTournyTask();
    }

    public void setupTournyTask() {
        /*
    	TimerTask tournyTalk = new TimerTask() {
    		public void run() {
    			try {
    				String query = "SELECT fnGameId, fnLeagueTypeID, fnTournyUserOne, fnTournyUserTwo, fnTotalPlayers, fnGameNumber FROM tblDuelTournyGame AS TG, tblDuelTourny T WHERE T.fnTournyID = TG.fnTournyID AND TG.fnStatus = 0 AND TG.fnTournyUserOne > 0 AND TG.fnTournyUserTwo > 0 AND TG.fnGameRound > 0";
    				ResultSet result = m_botAction.SQLQuery( "local", query );
    				while( result.next() ) {

    					int gid = result.getInt( "fnGameId" );
    					int idOne = result.getInt( "fnTournyUserOne" );
    					int idTwo = result.getInt( "fnTournyUserTwo" );
    					String pOne = sql_getName( result.getInt( "fnTournyUserOne" ) );
    					String pTwo = sql_getName( result.getInt( "fnTournyUserTwo" ) );
    					int leagueId = result.getInt( "fnLeagueTypeID" );
    					int realGameId = result.getInt( "fnGameNumber" );
    					int players = result.getInt( "fnTotalPlayers" );
    					TournyGame tg = new TournyGame( gid, pOne, pTwo, idOne, idTwo, leagueId, realGameId, players );
    					tournyGames.put( new Integer( gid ), tg );
    					//m_botAction.sendSmartPrivateMessage( "2dragons", "Game #"+gid+"   "+ pOne + "  vs   " + pTwo + "  League:"+leagueId);
    					m_botAction.sendSmartPrivateMessage( pOne, "You have a "+tg.getType()+" Tournament duel versus "+pTwo+". If you are available please reply with '!yes "+gid+"'" );
    					m_botAction.sendSmartPrivateMessage( pTwo, "You have a "+tg.getType()+" Tournament duel versus "+pOne+". If you are available please reply with '!yes "+gid+"'" );
    				}
                                m_botAction.SQLClose( result );
    			} catch (Exception e) {System.out.println("ERROR"+e);}
    			updateInactives();
    		}
    	};
    	m_botAction.scheduleTaskAtFixedRate( tournyTalk, getDelay(), 30 * 60 * 1000 );
        */
    	TimerTask tournyStuff = new TimerTask() {
    		public void run() {
    			tournyTalk();
    			checkForfeits();
    		}
    	};
    	m_botAction.scheduleTaskAtFixedRate( tournyStuff, 0, 5 * 60 * 1000 );
    }

    public void tournyTalk() {
    	try {
    		String query = "SELECT fnGameId, fnLeagueTypeID, fnTournyUserOne, fnTournyUserTwo, fnTotalPlayers, fnGameNumber FROM tblDuelTournyGame AS TG, tblDuelTourny T WHERE T.fnTournyID = TG.fnTournyID AND TG.fnStatus = 0 AND TG.fnTournyUserOne > 0 AND TG.fnTournyUserTwo > 0 AND TG.fnGameRound > 0 ORDER BY TG.fdLastCall LIMIT 0,20";
    		ResultSet result = m_botAction.SQLQuery("local", query);
    		while(result.next()) {
    			int gid = result.getInt( "fnGameId" );
    			int idOne = result.getInt( "fnTournyUserOne" );
    			int idTwo = result.getInt( "fnTournyUserTwo" );
    			String pOne = sql_getName( result.getInt( "fnTournyUserOne" ) );
    			String pTwo = sql_getName( result.getInt( "fnTournyUserTwo" ) );
    			int leagueId = result.getInt( "fnLeagueTypeID" );
    			int realGameId = result.getInt( "fnGameNumber" );
    			int players = result.getInt( "fnTotalPlayers" );
                m_botAction.SQLQueryAndClose("local", "UPDATE tblDuelTournyGame SET fdLastCall = NOW() WHERE fnGameID = "+gid);
    			TournyGame tg = new TournyGame( gid, pOne, pTwo, idOne, idTwo, leagueId, realGameId, players );
    			tournyGames.put( new Integer( gid ), tg );
                if( tg != null ) {
                    m_botAction.sendSmartPrivateMessage( pOne, "You have a "+tg.getType()+" Tournament duel versus "+pTwo+". If you are available please reply with '!yes "+gid+"'" );
                    m_botAction.sendSmartPrivateMessage( pTwo, "You have a "+tg.getType()+" Tournament duel versus "+pOne+". If you are available please reply with '!yes "+gid+"'" );
                } else {
                    Tools.printLog("*** DuelBot WARNING: Tournament duel between " + pOne + " and " + pTwo + " returned null. ***" );
                    m_botAction.sendSmartPrivateMessage( pOne, "ERROR in tournament duel vs " + pTwo + ".  Please report this to a staff member." );
                    m_botAction.sendSmartPrivateMessage( pTwo, "ERROR in tournament duel vs " + pOne + ".  Please report this to a staff member." );
                }
    		}
        m_botAction.SQLClose( result );
    	} catch(Exception e) { e.printStackTrace(); }
    }

    public void checkForfeits() {
    	try {
    		String query = "SELECT * FROM tblDuelTournyGame AS DTG INNER JOIN tblDuelTourny AS DT ON DT.fnTournyID = DTG.fnTournyID WHERE DTG.fnStatus = 0 AND DTG.fnTournyUserOne > 0 AND DTG.fnTournyUserTwo > 0 AND SUBDATE(NOW(), INTERVAL 14 DAY) > DTG.fdTimeStarted";
	    	ResultSet results = m_botAction.SQLQuery("local", query);
	    	while(results.next()) {
	    		int gid = results.getInt("fnGameID");
	    		int p1av = results.getInt("fnOneActivity");
	    		int p2av = results.getInt("fnTwoActivity");
	    		int id1 = results.getInt("fnTournyUserOne");
	    		int id2 = results.getInt("fnTournyUserTwo");
	    		String pOne = sql_getName(id1);
	    		String pTwo = sql_getName(id2);
	    		int leagueId = results.getInt("fnLeagueTypeID");
	    		if(p1av > p2av) {
				sql_updateTournyMatchData(gid, 0, 1);
	    			updatePlayoffBracket(pOne, pTwo, leagueId, gid);
	    		} else if(p2av > p1av) {
				sql_updateTournyMatchData(gid, 0, 2);
	    			updatePlayoffBracket(pTwo, pOne, leagueId, gid);
	    		} else {
	    			Random rand = new Random();
	    			int random = rand.nextInt(1);
	    			if(random == 0) {
					sql_updateTournyMatchData(gid, 0, 2);
	    				updatePlayoffBracket(pOne, pTwo, leagueId, gid);
	    			} else {
					sql_updateTournyMatchData(gid, 0, 1);
	    				updatePlayoffBracket(pTwo, pOne, leagueId, gid);
	    			}
	    		}
	    	}
                m_botAction.SQLClose( results );
	    } catch(Exception e) { e.printStackTrace(); }
    }

    public void handleEvent( PlayerDeath event ) {
    	Player p1 = m_botAction.getPlayer( event.getKilleeID() );
    	Player p2 = m_botAction.getPlayer( event.getKillerID() );
    	if( p1 == null || p2 == null )
    	    return;
        String name = p1.getPlayerName().toLowerCase();
        String killer = p2.getPlayerName().toLowerCase();

		if( playing.containsKey( name ) ) {

			Duel d = playing.get( name );
			ScoreReport scoreReport = new ScoreReport( d );
			int s_extra = 0;
			if( d.getLeagueId() == 2 ) s_extra = 2;


			//Checks for a spawn
			if( d.getPlayer( name ).timeFromLastDeath() < s_spawnTime + s_extra ) {
				if( d.getPlayer( killer ).getSpawns() >= s_spawnLimit - 1 ) {
					endDuel( d, name, killer, 1 );
					if( updates.containsKey( d ) ) {
						ScoreReport report = updates.get( d );
                        m_botAction.cancelTask(report);
					}
					return;
				} else {
					d.getPlayer( killer ).addSpawn();
					//d.getPlayer( name ).setTimer();
					m_botAction.sendPrivateMessage( killer, "Spawns are illegal in this league. If you should continue to spawn you will forfeit your match. (NC)" );
					m_botAction.sendPrivateMessage( name, "The kill was considered a spawn and does not count." );
					int scoreK = d.getPlayer( killer ).getKills();
					int scoreD = d.getPlayer( name ).getKills();
					m_botAction.sendPrivateMessage( killer, "Score: " + scoreK + "-" + scoreD );
					m_botAction.sendPrivateMessage( name, "Score: " + scoreD + "-" + scoreK );
					setScoreboard( d, 1 );
				}
			//Checks for double kill
			} else if( d.getPlayer( killer ).timeFromLastDeath() < s_noCount && d.noCount() ) {
				m_botAction.sendPrivateMessage( name, "Double Kill, No Count (NC)" );
				m_botAction.sendPrivateMessage( killer, "Double Kill, No Count (NC)" );
				d.getPlayer( name ).removeKill();
				d.getPlayer( killer ).removeDeath();
				int scoreK = d.getPlayer( killer ).getKills();
				int scoreD = d.getPlayer( name ).getKills();
				m_botAction.sendPrivateMessage( killer, "Score: " + scoreK + "-" + scoreD );
				m_botAction.sendPrivateMessage( name, "Score: " + scoreD + "-" + scoreK );
				setScoreboard( d, 2 );
			//Spawnkill
			} else if( d.getPlayer( killer ).timeFromLastDeath() < s_spawnTime+s_extra && d.noCount() ) {
				if( d.getPlayer( killer ).getSpawns() >= s_spawnLimit - 1 ) {
					endDuel( d, name, killer, 1 );
					if( updates.containsKey( d ) ) {
						ScoreReport report = updates.get( d );
                        m_botAction.cancelTask(report);
					}
					return;
				} else {
					d.getPlayer( killer ).addSpawn();
					//d.getPlayer( name ).setTimer();
					m_botAction.sendPrivateMessage( killer, "Spawns are illegal in this league. If you should continue to spawn you will forfeit your match. (NC)" );
					m_botAction.sendPrivateMessage( name, "The kill was considered a spawn and does not count." );
					int scoreK = d.getPlayer( killer ).getKills();
					int scoreD = d.getPlayer( name ).getKills();
					m_botAction.sendPrivateMessage( killer, "Score: " + scoreK + "-" + scoreD );
					m_botAction.sendPrivateMessage( name, "Score: " + scoreD + "-" + scoreK );
					setScoreboard( d, 1 );
				}
			//Normalkill
			} else {
				d.addDeath( name );
				d.addKill( killer );
				setScoreboard( d, 0 );
				int scoreK = d.getPlayer( killer ).getKills();
				int scoreD = d.getPlayer( name ).getKills();
				m_botAction.sendPrivateMessage( killer, "Score: " + scoreK + "-" + scoreD );
				m_botAction.sendPrivateMessage( name, "Score: " + scoreD + "-" + scoreK );

				/*int spread = 2;
				if( d.winBy2() ) spread = scoreK - scoreD;

				if( scoreK >= d.toWin() && spread > 1 ) {
					endDuel( d, killer, name, 0 );
					return;
				} */
			}
			if( d.deathWarp() ) {
				if(d.getPlayerNumber(name) == 1)
					m_botAction.warpTo( name, d.getSafeXOne(), d.getSafeYOne() );
				else
					m_botAction.warpTo( name, d.getSafeXTwo(), d.getSafeYTwo() );

				scoreReport.addDeathWarp( name, killer );
			}

			if( d.getCancelState(name)) {
				d.toggleCancelGame(name);
				m_botAction.sendPrivateMessage(name, "Your cancel request has been voided because " + killer + " killed you.");
			}
			if( d.getCancelState(killer)) {
				d.toggleCancelGame(killer);
				m_botAction.sendPrivateMessage(killer, "Your cancel request has been voided becase you killed " + name + ".");
			}

			//update scorereports
			if( updates.containsKey( d ) ) {
				ScoreReport report = updates.get( d );
				// Handle exception if it has been unexpectedly cancelled
				try {
                    m_botAction.cancelTask(report);
				} catch (Exception e) {
				}
			}
		    updates.put( d, scoreReport );
			try {
			    m_botAction.scheduleTask( scoreReport, 1000 * s_noCount );
			} catch (Exception e) {
			}
			redoBox(d.getLeagueId(), d.getBoxNumber(), d.getPlayerOne().getKills(), d.getPlayerTwo().getKills());
		}
    }

    public void handleEvent( PlayerPosition event ) {
        Player ptest = m_botAction.getPlayer( event.getPlayerID() );
        /* Sometimes a player leaves the arena just after a position packet is received;
           while the position event is distributed, the PlayerLeft event is given to
           Arena, causing all information about the player to be wiped from record.
           This also occurs frequently with the PlayerDeath event.  Moral: check for null.
         */
        if( ptest == null )
            return;

    	String name = ptest.getPlayerName().toLowerCase();
    	if( !playing.containsKey( name ) ) return;
    	int x = event.getXLocation();
    	int y = event.getYLocation();
    	double dist = Math.sqrt( Math.pow(( 8192 - x ), 2) + Math.pow(( 8192 - y ), 2) );

    	if( dist < 600 ) {

    		//Get the associated duel
    		Duel duel = playing.get( name );

    		//Get the associated player
    		DuelPlayerStats player = duel.getPlayer( name );

    		//Make sure the player didn't lagout
    		if( player.isWarping() ) {

    			player.setWarping( false );
    			return;
    		}

    		//Increment the count of warpings
    		player.addWarp();

    		m_botAction.sendPrivateMessage( name, "Warping is not allowed. Do not warp again else you will forfeit your duel." );
    		if( player.getWarps() > 1 ) {
		    	String opponent = duel.getOpponent( name );
				endDuel( duel, opponent, name, 2 );
				return;
    		}
    		if( duel.hasStarted() ) {
	    		WarpPoint p = duel.getRandomWarpPoint();
				m_botAction.warpTo( name, p.getXCoord(), p.getYCoord() );
			} else if( duel.getPlayerNumber( name ) == 1 ) {
				m_botAction.warpTo( name, duel.getSafeXOne(), duel.getSafeYOne() );
				player.removeWarp();
			} else {
				m_botAction.warpTo( name, duel.getSafeXTwo(), duel.getSafeYTwo() );
				player.removeWarp();
			}
		}

    }

    public void handleEvent( PlayerEntered event ) {
    	String name = m_botAction.getPlayerName( event.getPlayerID() );
    	m_botAction.sendPrivateMessage( name, "Welcome, if you are new PM me with !help for more information." );
    }

    public void handleEvent( FrequencyShipChange _event ) {

    	//Get the player name for this event
    	String name = m_botAction.getPlayerName( _event.getPlayerID() ).toLowerCase();

    	//Make sure the player is playing and in spectator mode
    	if( !playing.containsKey( name ) ) return;
    	if( _event.getShipType() != 0 ) return;

    	//Get the associated duel for this player
    	Duel duel = playing.get( name );

    	//Get the associated stats object for the player
    	DuelPlayerStats player = duel.getPlayer( name );

    	m_botAction.sendPrivateMessage( duel.getOpponent( name ), "Your opponent has lagged out or specced, if he/she does not return in 1 minute you win by forfeit." );
    	m_botAction.sendPrivateMessage( name, "You have 1 minute to return to your duel or you forfeit (!lagout)" );
    	//duel.getPlayer( name ).setData( 8, ((int)System.currentTimeMillis() / 1000 ) );
    	player.addLagout();

    	if( player.getLagouts() >= s_lagLimit ) {
    		String opponent = duel.getOpponent( name );
			endDuel( duel, opponent, name, 3 );
			return;
    	}

		if( laggers.containsKey( name ) ) {
            TimerTask t = laggers.get( name );
            try {
                m_botAction.cancelTask(t);
            } catch( IllegalStateException e) {
                t = null;
            }
			laggers.remove( name );
		}
		laggers.put( name, new Lagger( name, duel, laggers ) );
		Lagger l = laggers.get( name );
		m_botAction.scheduleTask( l, 60000 );
    }

    public void handleEvent( PlayerLeft _event ) {

    	//Get the player name for this event
    	String name = m_botAction.getPlayerName( _event.getPlayerID() ).toLowerCase();

    	//Make sure the player is playing
    	if( !playing.containsKey( name ) ) return;

    	//Get the associated duel for this player
    	Duel duel = playing.get( name );

    	//Get the associated stats object for the player
    	DuelPlayerStats player = duel.getPlayer( name );

    	m_botAction.sendPrivateMessage( duel.getOpponent( name ), "Your opponent has lagged out or specced, if he/she does not return in 1 minute you win by forfeit." );
    	m_botAction.sendPrivateMessage( name, "You have 1 minute to return to your duel or you forfeit (!lagout)" );
    	//duel.getPlayer( name ).setData( 8, ((int)System.currentTimeMillis() / 1000 ) );
    	player.addLagout();

    	if( player.getLagouts() >= s_lagLimit ) {
    		String opponent = duel.getOpponent( name );
			endDuel( duel, opponent, name, 3 );
			return;
    	}

		if( laggers.containsKey( name ) ) {
            m_botAction.cancelTask(laggers.get( name ));
			laggers.remove( name );
		}
		laggers.put( name, new Lagger( name, duel, laggers ) );
		Lagger l = laggers.get( name );
		m_botAction.scheduleTask( l, 60000 );

    }


    /**
     * Returns idle status and enables shutdown mode to facilitate rapid restart of core..
     */
    public boolean isIdle() {
        shutDown = true;
        shutDownDie = true;
        return ( duels.size() == 0 );
    }


    /***********************************************
     *              Internal Classes               *
     ***********************************************/

    class Lagger extends TimerTask {
        String player;
        HashMap<String, Lagger> laggers;
        Duel duel;

        public Lagger( String name, Duel d, HashMap<String, Lagger> l ) {
            player = name;
            duel = d;
            laggers = l;
        }

        public void run() {
            String opponent = duel.getOpponent( player );
            endDuel( duel, opponent, player, 4 );
        }
    }

    class StartDuel extends TimerTask {

        TournyGame game;
        duelbot dbot;

        public StartDuel( TournyGame tg, duelbot d ) {
            game = tg;
            dbot = d;
        }

        public void run() {
            DuelBox thisBox = dbot.getDuelBox( game.getGameType() );
            //Add queue system for dueling
            if( thisBox == null ) {
                m_botAction.sendPrivateMessage( game.getPlayerOne(), "Unable to start tournament duel, all duel boxes are full." );
                m_botAction.sendPrivateMessage( game.getPlayerTwo(), "Unable to start tournament duel, all duel boxes are full." );
                return;
            }
            String p1 = m_botAction.getFuzzyPlayerName( game.getPlayerOne() );
            String p2 = m_botAction.getFuzzyPlayerName( game.getPlayerTwo() );
            if(p1 != null && p2 != null) {
                game.setPlayerOne( p1.toLowerCase() );
                game.setPlayerTwo( p2.toLowerCase() );
            }
            duels.put( new Integer( thisBox.getBoxNumber() ), new Duel( thisBox, game ) );
            startDuel( duels.get( new Integer( thisBox.getBoxNumber() ) ), game.getPlayerOne(), game.getPlayerTwo() );
            playing.put( game.getPlayerOne(), duels.get( new Integer( thisBox.getBoxNumber() ) ) );
            playing.put( game.getPlayerTwo(), duels.get( new Integer( thisBox.getBoxNumber() ) ) );
            tournyGamesRunning.put(thisBox.getBoxNumber(), game.getGameId());
        }
    }

    class ScoreReport extends TimerTask {

        private Duel 	duel;
        private boolean warpOnKill;
        private String 	player1;
        private String 	player2;

        public ScoreReport( Duel d ) {
            duel = d;
            warpOnKill = false;
        }

        public void run() {

            int scoreWinner = duel.getPlayerOne().getKills();
            int scoreLoser = duel.getPlayerTwo().getKills();
            String winner = duel.getPlayerOne().getName();
            String loser = duel.getPlayerTwo().getName();

            if( scoreWinner < scoreLoser ) {
                winner = loser;
                loser = duel.getPlayerOne().getName();
                scoreWinner = scoreLoser;
                scoreLoser = duel.getPlayerOne().getKills();
            }
            int spread = 2;
            if( duel.winBy2() ) spread = Math.abs( scoreWinner - scoreLoser );

            if( scoreWinner >= duel.toWin() && spread > 1 ) {
                endDuel( duel, winner, loser, 0 );
                return;
            }

            if( warpOnKill )
                warpPlayers( player1, player2 );
        }

        public void addDeathWarp( String p1, String p2 ) {

            player1 = p1;
            player2 = p2;
            warpOnKill = true;

        }
    }

    /***********************************************
     *                SQL Related                   *
     ***********************************************/

    public DuelPlayer sql_getPlayer( String name ) {
        try {
            ResultSet result = m_botAction.SQLQuery( mySQLHost, "SELECT * FROM tblDuelPlayer WHERE fcUserName = '"+Tools.addSlashesToString(name)+"' AND fnEnabled = 1" );
            DuelPlayer dp = null;
            if( result.next() ) {
                try {
                    dp = new DuelPlayer( (result.getInt( "fnWinBy2" ) == 1), (result.getInt( "fnNoCount" ) == 1), (result.getInt( "fnDeathWarp" ) == 1), result.getInt("fnLag"), result.getInt( "fnGameKills" ) );
                } catch (SQLException e) {}
            }
            m_botAction.SQLClose( result );
            return dp;
        } catch (Exception e) {
            Tools.printStackTrace( "Failed to get player information", e );
            return null;
        }
    }

    public void sql_createLeagueData( DBPlayerData player ) {
        try {
            for( int i = 1; i <= 3; i++ )
                m_botAction.SQLQueryAndClose( mySQLHost, "INSERT INTO tblDuelLeague (`fnUserID`, `fcUserName`, `fnLeagueTypeID`) VALUES ("+player.getUserID()+", '"+Tools.addSlashesToString( player.getUserName() )+"', "+i+")" );
        } catch (Exception e) {
            Tools.printStackTrace( "Failed to add league data information", e );
        }
    }

    public boolean sql_banned( String name ) {
        try {
            boolean banned = false;
            ResultSet result = m_botAction.SQLQuery( mySQLHost, "SELECT * FROM tblDuelBan WHERE fcUserName = '"+Tools.addSlashesToString( name )+"'" );
            if( result.next() ) banned = true;
            m_botAction.SQLClose( result );
            return banned;
        } catch (Exception e) { return false; }
    }

    public boolean sql_banPlayer( String name, String comment ) {
        try {
            m_botAction.SQLQueryAndClose( mySQLHost, "INSERT INTO tblDuelBan (fcUserName, fcComment) VALUES ('"+Tools.addSlashesToString(name)+"', '"+Tools.addSlashesToString(comment)+"')" );
            return true;
        } catch (Exception e) { return false; }
    }

    public void sql_bannedPlayers( String name ) {
        try {
            ResultSet results = m_botAction.SQLQuery( mySQLHost, "SELECT * FROM tblDuelBan WHERE 1");
            while(results.next())
                m_botAction.sendPrivateMessage(name, results.getString("fcUserName"));
            m_botAction.SQLClose( results );
        } catch (Exception e) { Tools.printStackTrace(e); }
    }

    public void sql_recommentBan( String name, String player, String comment ) {
        try {
            if(sql_banned(player)) {
                m_botAction.SQLQueryAndClose( mySQLHost, "UPDATE tblDuelBan SET fcComment = '"+Tools.addSlashesToString(comment)+"' WHERE fcUserName = '"+Tools.addSlashesToString(player)+"'");
                m_botAction.sendPrivateMessage(name, "Set " + player + "'s ban comment to: " + comment);
            }
            else
                m_botAction.sendPrivateMessage(name, "That player is not banned.");
        } catch(Exception e) { Tools.printStackTrace(e); }
    }

    public void sql_getComment(String name, String player) {
        try {
            if(sql_banned(player)) {
                ResultSet results = m_botAction.SQLQuery( mySQLHost, "SELECT * FROM tblDuelBan WHERE fcUserName = '"+Tools.addSlashesToString(player)+"'");
                if(results.next())
                    m_botAction.sendPrivateMessage(name, "Ban comment: " + results.getString("fcComment"));
                else
                    m_botAction.sendPrivateMessage(name, "Sorry, I could not find that comment.");
                m_botAction.SQLClose( results );
            }
            else
                m_botAction.sendPrivateMessage(name, "That player is not banned.");
        } catch(Exception e) { Tools.printStackTrace(e); }
    }


    public boolean sql_unbanPlayer( String name ) {
        try {
            m_botAction.SQLQueryAndClose( mySQLHost, "DELETE FROM tblDuelBan WHERE fcUserName = '"+Tools.addSlashesToString(name)+"'" );
            return true;
        } catch (Exception e) { return false; }
    }

    public ResultSet sql_getUserInfo( int userId, int leagueId ) {
        try {
            ResultSet result = m_botAction.SQLQuery( mySQLHost, "SELECT * FROM tblDuelLeague WHERE fnSeason = "+s_season+" AND fnUserID = "+userId+" AND fnLeagueTypeID = "+leagueId );
            if( result.next() )
                return result;
            else return null;
        } catch (Exception e) {
            Tools.printStackTrace( "Failed to get user information", e );
            return null;
        }
    }

    public void sql_storeUserLoseRating( String name, int userId, int leagueId, int kills, int deaths, int loserStreak, int loserCurStreak, String lostTo, int spawns, int spawned, int lagouts, int time, int rating, boolean aced) {

        try {
            String query = "UPDATE tblDuelLeague SET fnLosses = fnLosses+1, fnKills = fnKills + "+kills+", fnDeaths = fnDeaths + "+deaths+", ";
            query += "fnLossStreak = "+loserStreak+", fnCurrentLossStreak = "+loserCurStreak+", ";
            query += "fnCurrentWinStreak = 0, ";
            if( aced ) query += "fnAced = fnAced+1, ";
            query += "fnTimePlayed = fnTimePlayed + "+ time + ", ";
            query += "fcLastLoss = '"+Tools.addSlashesToString( lostTo )+"', ";
            query += "fnSpawns = fnSpawns +"+spawns+", fnSpawned = fnSpawned +"+spawned+", fnLagouts = fnLagouts +"+lagouts+", ";
            query += "fnRating = "+rating+" WHERE fnSeason = "+s_season+" AND fnUserID = "+userId+" AND fnLeagueTypeID = "+leagueId;
            m_botAction.SQLQueryAndClose( mySQLHost, query );
        } catch (Exception e) {
            Tools.printStackTrace( "Failed to store user loss", e );
        };
    }

    public void sql_storeUserWinRating( String name, int userId, int leagueId, int kills, int deaths, int winnerStreak, int winnerCurStreak, String wonAgainst, int spawns, int spawned, int lagouts, int time, int rating, boolean aced ) {

        try {
            String query = "UPDATE tblDuelLeague SET fnWins = fnWins+1, fnKills = fnKills + "+kills+", fnDeaths = fnDeaths + "+deaths+", ";
            query += "fnWinStreak = "+winnerStreak+", fnCurrentWinStreak = "+winnerCurStreak+", ";
            query += "fnCurrentLossStreak = 0, ";
            if( aced ) query += "fnAces = fnAces+1, ";
            query += "fnTimePlayed = fnTimePlayed + "+ time + ", ";
            query += "fcLastWin = '"+Tools.addSlashesToString( wonAgainst )+"', ";
            query += "fnSpawns = fnSpawns +"+spawns+", fnSpawned = fnSpawned +"+spawned+", fnLagouts = fnLagouts +"+lagouts+", ";
            query += "fnRating = "+rating+" WHERE fnSeason = "+s_season+" AND fnUserID = "+userId+" AND fnLeagueTypeID = "+leagueId;
            m_botAction.SQLQueryAndClose( mySQLHost, query );
        } catch (Exception e) {
            Tools.printStackTrace( "Failed to store user win", e );
        };
    }

    public void sql_verifyRecord( String name, int userId, int leagueId ) {

        try {
            String query = "SELECT * FROM tblDuelLeague WHERE fnUserID = "+userId+" AND fnSeason ="+s_season+" AND fnLeagueTypeID ="+leagueId;
            ResultSet result = m_botAction.SQLQuery( mySQLHost, query );
            if( !result.next() )
                m_botAction.SQLQueryAndClose( mySQLHost, "INSERT INTO tblDuelLeague (fnUserID, fcUserName, fnSeason, fnLeagueTypeID) VALUES ("+
                        userId+", '"+Tools.addSlashesToString(name)+"', "+s_season+", "+leagueId+")" );
            m_botAction.SQLClose( result );
        } catch (Exception e) {
            Tools.printStackTrace( "Failed to verify account", e );
        }
    }

    public boolean sql_enabledUser( String name ) {

        try {
            String query = "SELECT fnUserID FROM tblDuelPlayer WHERE fnEnabled = 1 AND fcUserName = '"+Tools.addSlashesToString(name)+"'";
            ResultSet result = m_botAction.SQLQuery( mySQLHost, query );
            boolean hasNext = result.next();
            m_botAction.SQLClose( result );
            return hasNext;
        } catch (Exception e) {
            Tools.printStackTrace( "Failed to check for enabled user", e );
            return false;
        }
    }

    public void sql_enableUser( String name ) {

        DBPlayerData player = new DBPlayerData( m_botAction, "local", name, true );

        try {
            String query = "UPDATE tblDuelPlayer SET fnEnabled = 1 WHERE fnUserID = "+player.getUserID();
            m_botAction.SQLQueryAndClose( mySQLHost, query );
        } catch (Exception e) {
            Tools.printStackTrace( "Error enabling user", e );
        }
    }

    public void sql_disableUser( String name, boolean ratingL ) {

        DBPlayerData player = new DBPlayerData( m_botAction, "local", name, true );

        for( int i = 1; i <= 3; i++ )
            sql_verifyRecord( name, player.getUserID(), i );

        try {
            String query = "UPDATE tblDuelPlayer SET fnEnabled = 0 WHERE fnUserID = "+player.getUserID();
            m_botAction.SQLQueryAndClose( mySQLHost, query );
            if(ratingL) {
                for(int i = 1; i <= 3;i++) {
                    DBPlayerData playerinfo = new DBPlayerData( m_botAction, "local", name, true );
                    ResultSet playerRS = sql_getUserInfo( playerinfo.getUserID(), i );
                    int rating = playerRS.getInt( "fnRating" );
                    if(rating > 1300) rating -= 300;
                    else if(rating > 1000) rating = 1000;
                    query = "UPDATE tblDuelLeague SET fnRating = "+rating+" WHERE fnLeagueTypeID = "+i+" AND fnSeason = "+s_season+" AND fnUserID = "+player.getUserID();
                    m_botAction.SQLQueryAndClose( mySQLHost, query );
                }
            }
        } catch (Exception e) {
            Tools.printStackTrace( "Error disabling user", e );
        }
    }

    public ResultSet sql_getUserIPMID( String name ) {

        try {
            String query = "SELECT fcIP, fnMID FROM tblDuelPlayer WHERE fcUserName = '"+Tools.addSlashesToString(name)+"'";

            ResultSet result = m_botAction.SQLQuery( mySQLHost, query );
            boolean hasNext = result.next();
            if( hasNext ) return result;
        } catch (Exception e) {
            Tools.printStackTrace( "Problem getting user IP/MID", e );
        }
        return null;
    }

    public boolean sql_gameLimitReached( String a, String b, int leagueId ) {

        a = Tools.addSlashesToString(a);
        b = Tools.addSlashesToString(b);

        try {
            String query = "SELECT COUNT(fnMatchID) AS count FROM `tblDuelMatch` WHERE"+
            " ((fcWinnerName = '"+a+"' AND fcLoserName = '"+b+"')"+
            " OR (fcWinnerName = '"+b+"' AND fcLoserName = '"+a+"'))"+
            " AND TO_DAYS(NOW()) - TO_DAYS(ftUpdated ) <= "+s_duelDays+
            " AND fnLeagueTypeID = "+leagueId;
            ResultSet result = m_botAction.SQLQuery( mySQLHost, query );
            if( result.next() ) {
                if( result.getInt( "count" ) < s_duelLimit ) {
                    m_botAction.SQLClose( result );
                    return false;
                } else {
                    m_botAction.SQLClose( result );
                    return true;
                }
            } else {
                m_botAction.SQLClose( result );
                return false;
            }
        } catch (Exception e) { return false; }
    }

    public String sql_getName( int tournyId ) {

        try {
            String query = "SELECT fcUserName FROM tblUser AS U WHERE fnUserID = "+tournyId;
            ResultSet result = m_botAction.SQLQuery( "local", query );
            String name;
            if( result.next() )
                name = result.getString( "fcUserName" );
            else
                name = "";
            m_botAction.SQLClose( result );
            return name;
        } catch (Exception e) { return ""; }
    }

    public void sql_updateTournyAvailability( int gameId, int playerId ) {

        String extra;
        if( playerId == 1 )
            extra = "fnOneActivity = fnOneActivity + 1";
        else
            extra = "fnTwoActivity = fnTwoActivity + 1";

        try {
            System.out.println( "UPDATE tblDuelTournyGame SET "+extra+" WHERE fnGameID = "+gameId );
            String query = "UPDATE tblDuelTournyGame SET "+extra+" WHERE fnGameID = "+gameId;
            m_botAction.SQLQueryAndClose( "local", query );
        } catch (Exception e) {
            System.out.println( "Error Updating avail:"+e );
        }
    }

    public void sql_updateTournyMatchData( int gameId, int matchId, int winner) {
        try {
            String query = "UPDATE tblDuelTournyGame SET fnStatus = "+winner+", fnDuelMatchID = "+matchId+" WHERE fnGameID = "+gameId;
            m_botAction.SQLQueryAndClose("local", query);
        } catch(Exception e) {}
    }

    public int sql_getTournyGameID(int gameNumber, int leagueId) {
        try {
            ResultSet results = m_botAction.SQLQuery( mySQLHost, "SELECT fnGameID FROM tblDuelTournyGame AS DTG, tblDuelTourny AS DT WHERE DT.fnTournyID = DTG.fnTournyID AND DT.fnLeagueTypeID = "+leagueId+" AND DTG.fnGameNumber = "+gameNumber);
            int gameid = -1;
            if(results.next())
                gameid = results.getInt("fnGameID");
            m_botAction.SQLClose( results );
            return gameid;
        } catch(Exception e) {}
        return -1;
    }

    public void sql_lagInfo(String name, int average)
    {
        try {
            ResultSet result = m_botAction.SQLQuery( mySQLHost, "SELECT fnUserID, fnLagCheckCount, fnLag FROM tblDuelPlayer WHERE fcUserName = '"+Tools.addSlashesToString(name)+"'" );
            if( result != null ) {
                if(result.next()) {
                    int userID = result.getInt("fnUserID");
                    int totalLag = result.getInt("fnLag") * result.getInt("fnLagCheckCount");
                    int average2 = (totalLag + average) / (result.getInt("fnLagCheckCount") + 1);
                    m_botAction.SQLQueryAndClose( mySQLHost, "UPDATE tblDuelPlayer SET fnLag = " + average2 + ", fnLagCheckCount = fnLagCheckCount + 1 WHERE fnUserID = "+userID);
                }
            }
            m_botAction.SQLClose( result );
        } catch(Exception e) { Tools.printStackTrace(e); }
    }

    public void updatePlayoffBracket(String winner, String loser, int league, int gameId) {
        try {
            ResultSet results = m_botAction.SQLQuery( mySQLHost, "SELECT * FROM tblDuelTournyGame WHERE fnGameID = "+gameId);
            results.next();
            int round = results.getInt("fnGameRound");
            int gameNumber = results.getInt("fnGameNumber");
            int winnerNum = results.getInt("fnStatus");
            int userNum;
            if(winnerNum == 1) {
                userNum = results.getInt("fnTournyUserOne");
            } else {
                userNum = results.getInt("fnTournyUserTwo");
            }
            m_botAction.SQLClose( results );
            results = m_botAction.SQLQuery( mySQLHost, "SELECT * FROM tblDuelTourny WHERE fnLeagueTypeID = "+league);
            results.next();
            int players = results.getInt("fnTotalPlayers");
            if(gameNumber < (players - 1)) {
                int totalMatches = 0;
                int matches = players;
                for(int k = 1;k < round;k++) {
                    matches /= 2;
                    totalMatches += matches;
                }
                int winnerNext = (gameNumber - totalMatches + 1) / 2 + (totalMatches + (matches / 2));
                advancePlayer(userNum, winner, winnerNext, league);
                m_botAction.sendPrivateMessage(loser, "Sorry, you have been eliminated.");
                m_botAction.SQLQueryAndClose(mySQLHost, "INSERT INTO tblMessageSystem (fnID, fcName, fcMessage, fcSender, fnRead, fdTimeStamp) VALUES (0, '"+Tools.addSlashesToString(loser.toLowerCase())+"', 'You have been eliminated from the TWEL Playoffs. Thanks for playing.', 'TWEL Staff', 0, NOW())");
            } else {
                String leagueName = "";
                switch(league) {
                    case 1:
                        leagueName = "Warbird";
                        break;
                    case 2:
                        leagueName = "Javelin";
                        break;
                    case 3:
                        leagueName = "Spider";
                        break;
                };
                m_botAction.sendZoneMessage(winner + " has just won the " + leagueName + " TWEL Championship. Congratulate him/her next time you see him/her. -TWEL Staff", 2);
            }
            m_botAction.SQLClose( results );
        } catch(Exception e) { e.printStackTrace(); }
    }

    public void advancePlayer(int userId, String name, int matchId, int leagueId) {
        try {
            ResultSet results = m_botAction.SQLQuery(mySQLHost, "SELECT * FROM tblDuelTournyGame AS DTG, tblDuelTourny AS DT WHERE DTG.fnGameNumber = "+matchId+" AND DTG.fnTournyID = DT.fnTournyID AND DT.fnLeagueTypeID = "+leagueId);
            results.next();
            if(results.getInt("fnTournyUserOne") == -1) {
                m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuelTournyGame SET fnTournyUserOne = "+userId+" WHERE fnGameID = "+results.getInt("fnGameID"));
                m_botAction.sendPrivateMessage(name, "Your opponent has not advanced yet. You will receive a message via MessageBot when he/she advances.");
            } else {
                m_botAction.SQLQueryAndClose(mySQLHost, "UPDATE tblDuelTournyGame SET fnTournyUserTwo = "+userId+", fdLastCall = NOW(), fdTimeStarted = NOW() WHERE fnGameID = "+results.getInt("fnGameID"));
                ResultSet results2 = m_botAction.SQLQuery(mySQLHost, "SELECT * FROM tblDuelPlayer WHERE fnUserID = "+results.getInt("fnTournyUserOne"));
                results2.next();
                String otherPlayer = results2.getString("fcUserName");
                m_botAction.sendSmartPrivateMessage(otherPlayer, "Your opponent ("+name+") has just advanced. PM me with !tchallenge "+results.getInt("fnGameID")+" to challenge him/her.");
                m_botAction.sendSmartPrivateMessage(name, "Your opponent ("+otherPlayer+") has already advanced. PM me with !tchallenge "+results.getInt("fnGameID")+" to challenge him/her.");
                m_botAction.SQLQueryAndClose(mySQLHost, "INSERT INTO tblMessageSystem (fnID, fcName, fcMessage, fcSender, fnRead, fdTimeStamp) VALUES (0, '"+Tools.addSlashesToString(name.toLowerCase())+"', 'Your match for the TWEL Playoffs is available. Your opponent is "+Tools.addSlashesToString(otherPlayer)+". PM DuelBot with !tchallenge "+results.getInt("fnGameID")+" to challenge him/her.', 'TWEL Staff', 0, NOW())");
                m_botAction.SQLQueryAndClose(mySQLHost, "INSERT INTO tblMessageSystem (fnID, fcName, fcMessage, fcSender, fnRead, fdTimeStamp) VALUES (0, '"+Tools.addSlashesToString(otherPlayer.toLowerCase())+"', 'Your match for the TWEL Playoffs is available. Your opponent is "+Tools.addSlashesToString(name)+". PM DuelBot with !tchallenge "+results.getInt("fnGameID")+" to challenge him/her.', 'TWEL Staff', 0, NOW())");
                m_botAction.SQLClose( results2 );
            }
            m_botAction.SQLClose( results );
        } catch(Exception e) { e.printStackTrace(); }
    }

    class CornerWarp extends TimerTask {

        long warpTime;
        String one, two;

        public CornerWarp(String n1, String n2, long time) {
            one = n1;
            two = n2;
            warpTime = time;
        }

        public void run() {
            warpPlayers(one, two);
        }
    }

    /***************************************
     * Real-time scoreboard related stuff. *
     ***************************************/

    String boxToRow[] = {"","01","01","02","03","02","04","03","05","04","06","05","07","08","06","09","07","10","08","11","09","12","13","14","10"};

    public void redoBox(int gameType, int box, int score1, int score2)
    {
        String row = boxToRow[box];
        oldOff(gameType, row, 0, score1);
        oldOff(gameType, row, 2, score2);
        newOn(gameType, row, 0, score1);
        newOn(gameType, row, 2, score2);
    }

    public void oldOff(int gameType, String row, int pos, int score)
    {
        int tensScore = score / 10;
        int onesScore = score % 10;
        if(onesScore != 0)
        {
            m_botAction.sendUnfilteredPublicMessage("*objoff " + gameType + row + pos + tensScore);
            m_botAction.sendUnfilteredPublicMessage("*objoff " + gameType + row + (pos + 1) + (onesScore - 1));
        }
        else
        {
            m_botAction.sendUnfilteredPublicMessage("*objoff " + gameType + row + pos + (tensScore - 1));
            m_botAction.sendUnfilteredPublicMessage("*objoff " + gameType + row + (pos + 1) + 9);
        }
    }

    public void newOn(int gameType, String row, int pos, int score)
    {
        int tensScore = score / 10;
        int onesScore = score % 10;
        m_botAction.sendUnfilteredPublicMessage("*objon " + gameType + row + pos + tensScore);
        m_botAction.sendUnfilteredPublicMessage("*objon " + gameType + row + (pos + 1) + onesScore);
    }

    public void finalOff(int gameType, int box, int score1, int score2)
    {
        int tensScore = score1 / 10;
        int onesScore = score1 % 10;
        m_botAction.sendUnfilteredPublicMessage("*objoff " + gameType + boxToRow[box] +""+ 0 +""+ tensScore);
        m_botAction.sendUnfilteredPublicMessage("*objoff " + gameType + boxToRow[box] +""+ 1 +""+ onesScore);

        tensScore = score2 / 10;
        onesScore = score2 % 10;
        m_botAction.sendUnfilteredPublicMessage("*objoff " + gameType + boxToRow[box] +""+ 2 +""+ tensScore);
        m_botAction.sendUnfilteredPublicMessage("*objoff " + gameType + boxToRow[box] +""+ 3 +""+ onesScore);
    }
}


class NotPlaying {
    int time = 0;
    int period = 0;

    public NotPlaying( int t, int p ) {
        time = t;
        period = p;
    }

    public boolean timeUp( int t ) {
        if( (t - time) / 60 > period ) return true;
        else return false;
    }
}