package twcore.bots.tournybot;

import twcore.core.*;

public class pStats {

	int playerID = 0;
	int totalKills = 0;
	int totalDeaths = 0;
    int gameKills = 0;
    int gameDeaths = 0;
	int registered = 0;
    int playerRound = 0;
	int playerBox = 0;
	int playerState = 0;
	int playerNro = 0;
	int busy = 0;
	int lastDeath = 0;
	int lastReturn = 0;
	int randNum = 0;
	String lag = "No";
	int lagOuts = 0;
	int warps = 0;
	int ship = 0;
	static int players = 0;
	static int playersB = 1;
	boolean smallLag = false;

    public pStats(){
    }
        
    public void incrementKills() {
        totalKills++;
	    gameKills++;
    }

	public void removeKills() {
	    totalKills--;
	    gameKills--;
	}

	public void incrementRound() { playerRound++; }

	public void incrementLagOuts() { lagOuts++; }

	public void incrementWarps() { warps++; }

    public void incrementDeaths() {
        totalDeaths++;
	    gameDeaths++;
	    lastDeath = (int)(System.currentTimeMillis() / 1000);
    }

	public void removeDeaths() {
	    totalDeaths--;
	    gameDeaths--;
	}
        
    public int getTotalKills() { return totalKills; }
        
    public int getGameKills() { return gameKills; }

    public int getTotalDeaths() { return totalDeaths; }

    public int getGameDeaths() { return gameDeaths; }

	public int timeFromLastDeath() { return (int)(System.currentTimeMillis() / 1000) - lastDeath; }

	public int timeFromLastReturn() { return (int)(System.currentTimeMillis() / 1000) - lastReturn; }

	public void setLastReturn(int time) { lastReturn = time; }

	public int getRegistered() { return registered; }

	public int getPlayerState() { return playerState; }

	public int getPlayers() { return players; }

	public int getPlayerID() { return playerID; }

	public int getRound() { return playerRound; }
        
	public int getBox() { return playerBox; }

	public int getPlayerNro() { return playerNro; }

	public int getLagOuts() { return lagOuts; }

	public int getWarps() { return warps; }

	public int getRandNum() { return randNum; }

	public void setRandNum(int num) { randNum = num; }

	public int getShip() { return ship; }

	public void setShip(int num) { ship = num; }

	public boolean getSmallLag() { return smallLag; }

	public void toggleSmallLag(boolean sl) { 
		smallLag = sl;
	}

	public void laggedOut() { lag = "Yes"; }

	public String getLagOut() { return lag; }

    public void reset() {
        gameKills = 0;
        gameDeaths = 0;
		lagOuts = 0;
		warps = 0;
    }

	public void busy(int yn) { busy = yn; }

	public int getBusy() { return busy; }

	public void unRegister() {
	    playerID = 0;
 	    totalKills = 0;
	    totalDeaths = 0;
        gameKills = 0;
        gameDeaths = 0;
	    registered = 0;
        playerRound = 0;
	    playerBox = 0;
	    playerState = 0;
	    playersB = 1;
	    players = 0;
	    playerNro = 0;
	    busy = 0;
	    lag = "No";
		lagOuts = 0;
		warps = 0;
		randNum = 0;
		ship = 0;
		smallLag = false;
    }

	public void changeBox(int box) { playerBox = box; }

	public void changeNro(int nro) { playerNro = nro; }

	public void changeRound(int round) { playerRound = round; }

	public void register() {
        registered = 1;
	    playerRound = 1;
	    playerState = 0;
	    playerID = players;
  	    players++;
    }

	public void playing() { playerState = 1; }

	public void sleeping() { playerState = 0; }

	public void remove() { playerState = 2; }
}
