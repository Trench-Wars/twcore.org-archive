package twcore.bots.duelbot;

import twcore.core.*;


public class DuelChallenge {
	
	private String challenger;
	private String challenged;
	private DuelPlayer p;
	private int gameType;
	private int duelType;
	private int time;
	private TournyGame tournyGame;
	
	public DuelChallenge( String challenger, String challenged, DuelPlayer player, int type, int duel ) {
		this.challenger = challenger;
		this.challenged = challenged;
		p = player;
		gameType = type;
		duelType = duel;
		time = ((int)System.currentTimeMillis()/1000);
	}
	
	public DuelChallenge( String challenger, String challenged, DuelPlayer player, int type, TournyGame tg ) {
		this.challenger = challenger;
		this.challenged = challenged;
		p = player;
		gameType = type;
		duelType = 2;
		tournyGame = tg;
		time = ((int)System.currentTimeMillis()/1000);
	}
	
	public int getGameType() { return gameType; }
	public boolean winBy2() { return p.winBy2(); }
	public boolean noCount() { return p.noCount(); }
	public boolean deathWarp() { return p.deathWarp(); }
	public int toWin() { return p.toWin(); }
	public int getElapsedTime() {
		return ((int)System.currentTimeMillis()/1000) - time;
	}
	public int getDuelType() { return duelType; }
	
	public TournyGame getTournyGame() { return tournyGame; }
}