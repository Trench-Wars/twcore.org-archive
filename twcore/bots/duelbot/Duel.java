package twcore.bots.duelbot;

import twcore.core.*;


//WHEN fnCommentID = 3 the time sometimes gets very large.
//Scores are null/void

public class Duel {
	
	DuelBox duelBox;
	String 	player1;
	String 	player2;
	boolean cancel1 = false;
	boolean cancel2 = false;
	boolean score1 = true;
	boolean score2 = true;
	PlayerProfile profile1;
	PlayerProfile profile2;
	int 	gameType;
	boolean winBy2 = false;
	boolean noCount = false;
	boolean deathWarp = false;
	int toWin = 10;
	boolean started = false;
	int startTime = 0;
	int duelType;
	TournyGame tournyGame;
	
	public Duel( DuelBox d, String p1, String p2, DuelChallenge c, int duel) {
		d.toggleUse();
		duelBox = d;
		player1 = p1;
		player2 = p2;
		if( duel == 1 ) {
			winBy2 = c.winBy2();
			noCount = c.noCount();
			deathWarp = c.deathWarp();
			toWin = c.toWin();
		} else {
			winBy2 = true;
			noCount = true;
			deathWarp = true;
			toWin = 10;
		}
		profile1 = new PlayerProfile( p1, gameType, getBoxFreq() );
		profile2 = new PlayerProfile( p2, gameType, getBoxFreq() + 1 );
		duelType = duel;
		gameType = c.getGameType();
		
	}
	
	public Duel( DuelBox d, TournyGame tg ) {
		
		d.toggleUse();
		duelBox = d;
		player1 = tg.getPlayerOne().toLowerCase();;
		player2 = tg.getPlayerTwo().toLowerCase();;
		duelType = 2;
		winBy2 = true;
		noCount = true;
		deathWarp = true;
		toWin = 10;
		profile1 = new PlayerProfile( player1, tg.getGameType(), getBoxFreq() );
		profile2 = new PlayerProfile( player2, tg.getGameType(), getBoxFreq() + 1 );
		gameType = tg.getGameType();
		tournyGame = tg;
	}
	
	public String getLeagueType() {
		if( gameType == 1 ) return "Warbird";
		else if( gameType == 2 ) return "Javelin";
		else if( gameType == 3 ) return "Spider";
		else if( gameType == 4 ) return "Leviathan";
		else if( gameType == 5 ) return "Terrier";
		else if( gameType == 6 ) return "Weasel";
		else if( gameType == 7 ) return "Lancaster";
		else if( gameType == 8 ) return "Shark";
		else return "";
	}
	
	public int getLeagueId() { return gameType; }
	
	public void started() { 
		started = true;	
		startTime = (int)(System.currentTimeMillis()/1000);
	}
	public boolean hasStarted() { return started; }
	public int getTime() {
		return (int)(System.currentTimeMillis()/1000) - startTime;
	}
	
	public String showScore() {
		String score = player1 + " vs " + player2 + ": ";
		score += profile1.getKills() + "-" + profile2.getKills();
		return score;
	}
	
	public String getOpponent( String name ) {
		if( name.equals( player1 ) ) return player2;
		else return player1;
	}	
	
	public void addDeath( String name ) {
		if( name.equals( player1 ) ) profile1.addDeath();
		else if( name.equals( player2 ) ) profile2.addDeath();
	}
	
	public void addKill( String name ) {
		if( name.equals( player1 ) ) profile1.addKill();
		else if( name.equals( player2 ) ) profile2.addKill();
	}
	
	public PlayerProfile getPlayer( String name ) {
		name = name.toLowerCase();
		if( name.equals( player1 ) ) return profile1;
		else if( name.equals( player2 ) ) return profile2;
		else return null;
	}
	
	public PlayerProfile getPlayerOne() {
		return profile1;
	}
	
	public PlayerProfile getPlayerTwo() {
		return profile2;
	}
	
	public int getBoxFreq() {
		return duelBox.getBoxNumber() * 2;
	}
	
	public int getBoxNumber() { return duelBox.getBoxNumber(); }
	
	public boolean toggleCancelGame( String name ) {
		if( name.equals( player1 ) )
			return cancel1 = !cancel1;
		else if( name.equals( player2 ) ) 
			return cancel2 = !cancel2;
		else return false;
	}
	
	public boolean toggleScoreboard( String name ) {
		if( name.equals( player1 ) )
			return score1 = !score1;
		else if( name.equals( player2 ) ) 
			return score2 = !score2;
		else return true;
	}
	
	public boolean scoreboard( int i ) {
		if( i == 1 ) return score1;
		else if( i == 2 ) return score2;
		else return true;
	}
	
	public boolean getCancelState( String name ) {
		if( name.equals( player1 ) ) return cancel1;
		else if( name.equals( player2 ) ) return cancel2;
		else return false;
	}
	
	public int getPlayerNumber( String name ) {
		if( name.equals( player1 ) ) return 1;
		else return 2;
	}
	
	public int getShipType() { return gameType; }
	public int getXOne() { return duelBox.getXOne(); }
	public int getXTwo() { return duelBox.getXTwo(); }
	public int getYOne() { return duelBox.getYOne(); }
	public int getYTwo() { return duelBox.getYTwo(); }
	public int getSafeXOne() { return duelBox.getSafeXOne(); }
	public int getSafeXTwo() { return duelBox.getSafeXTwo(); }
	public int getSafeYOne() { return duelBox.getSafeYOne(); }
	public int getSafeYTwo() { return duelBox.getSafeYTwo(); }
	public boolean winBy2() { return winBy2; }
	public boolean noCount() { return noCount; }
	public boolean deathWarp() { return deathWarp; }
	public int toWin() { return toWin; }
	
	public WarpPoint getRandomWarpPoint() {
		return duelBox.getRandomWarpPoint();
	}
	
	public void toggleDuelBox() {
		duelBox.toggleUse();
	}
	
	public int getDuelType() { return duelType; }
	
	public TournyGame getTournyGame() { return tournyGame; }
}