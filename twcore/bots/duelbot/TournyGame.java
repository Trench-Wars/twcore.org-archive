package twcore.bots.duelbot;

import twcore.core.*;


public class TournyGame {
	
	
	private int gameId;
	
	private String playerOne;
	private String playerTwo;
	private int playerOneId;
	private int playerTwoId;
	private int leagueType;
	private int realGameId;
	private int players;
	private int round;
	
	private boolean replyOne = false;
	private boolean replyTwo = false;
	
	private int expiration;
	
	public TournyGame( int gId, String pOne, String pTwo, int idOne, 
					   int idTwo, int id, int realId, int p ) {
		
		gameId = gId;
		playerOne = pOne;
		playerTwo = pTwo;
		playerOneId = idOne;
		playerTwoId = idTwo;
		leagueType = id;
		realGameId = realId;
		players = p;
		
		expiration = (int)(System.currentTimeMillis()/1000)+60*5;
	}
	
	public boolean hasExpired() {
		
		if( (System.currentTimeMillis()/1000)-expiration > 300 ) return true;
		else return false;
	}
	
	public String getType() {
		
		if( leagueType == 1 ) return "Warbird";
		if( leagueType == 2 ) return "Javelin";
		else return "Spider";
	}
	
	public boolean hasPlayer( String name ) {
		
		name = name.toLowerCase();
		
		if( name.equals( playerOne.toLowerCase() ) || name.equals( playerTwo.toLowerCase() ) )
			return true;
		else
			return false;
	}
	
	public boolean setResponse( String name ) {
		
		if( name.equals( playerOne.toLowerCase() ) ) {
			if( replyOne ) return true;
			replyOne = true;
			return false;
		} else if( name.equals( playerTwo.toLowerCase() ) ) {
			if( replyTwo ) return true;
			replyTwo = true;
			return false;
		} else return true;
	}
	
	public int getPlayerNumber( String name ) {
		if( name.equals( playerOne ) ) {
			return 1;
		} else if( name.equals( playerTwo ) ) {
			return 2;
		}
		return 1;
		
	}
	
	public String getOpponent( String name ) {
		if( name.equals( playerOne ) ) return playerTwo;
		else return playerOne;
	}
	
	public boolean bothReady() {
		return replyOne && replyTwo;
	}
	
	public int getGameType() { return leagueType; }
	public String getPlayerOne() { return playerOne; }
	public String getPlayerTwo() { return playerTwo; }
	
	public int getGameId() { return gameId; }
	public int getRealGameId() { return realGameId; }
	public int getPlayers() { return players; }
	
	public void setPlayerOne( String p ) { playerOne = p; }
	public void setPlayerTwo( String p ) { playerTwo = p; }
}