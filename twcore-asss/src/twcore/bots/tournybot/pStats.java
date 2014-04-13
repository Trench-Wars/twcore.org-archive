package twcore.bots.tournybot;

public class pStats {

	String name;
	String freq;
	String notes = "-";
	int playerID = 0;
	int totalKills = 0;
	int totalDeaths = 0;
	int gameKills = 0;
	int gameDeaths = 0;
	int playerState = 0;
	int busy = 0;
	int lastDeath = 0;
	int lastReturn = 0;
	int id = 0;
	String lastKiller = "";
	String lag = "No";
	int lagOuts = 0;
	int fouls = 0;
	int ship = 0;
	boolean trueLag = false;
	boolean smallLag = false;
	boolean randomT = false;
	static int players = 0;

	boolean qSent = false;

	public pStats(String tName) {
		name = tName;
	}

	public String getName() { return name; }

	public String getNotes() { return notes; }

	public void setNotes(String note) { notes = note; }

	public void endTournament() { players = 0; }

	public void setRandomT(boolean r) { randomT = r; }

	public boolean getRandomT() { return randomT; }

	public void setDBID(int i) { id = i; }

	public int getDBID() {return id; }

	public void setFreq(String sFreq) {	freq = sFreq; }

	public String getFreq() { return freq; }

	public void setLastKiller(String name) { lastKiller = name; }

	public String getLastKiller() { return lastKiller; }

	public void incrementKills() {
		totalKills++;
		gameKills++;
	}

	public void removeKills() {
		totalKills--;
		gameKills--;
	}

	public void incrementLagOuts() { lagOuts++; }

	public void incrementFouls() { fouls++; }

	public void incrementDeaths() {
		totalDeaths++;
		gameDeaths++;
		lastDeath = (int)(System.currentTimeMillis() / 1000);
	}

	public void setLastDeath() { lastDeath = (int)(System.currentTimeMillis() / 1000); }

	public void removeDeaths() {
	    totalDeaths--;
	    gameDeaths--;
	}

	public int getPlayerID() { return playerID; }

	public int getTotalKills() { return totalKills; }

	public int getGameKills() { return gameKills; }

	public int getTotalDeaths() { return totalDeaths; }

	public int getGameDeaths() { return gameDeaths; }

	public int timeFromLastDeath() { return (int)(System.currentTimeMillis() / 1000) - lastDeath; }

	public int timeFromLastReturn() { return (int)(System.currentTimeMillis() / 1000) - lastReturn; }

	public void setLastReturn(int time) { lastReturn = time; }

	public int getPlayerState() { return playerState; }

	public int getLagOuts() { return lagOuts; }

	public int getFouls() { return fouls; }

	public int getShip() { return ship; }

	public void setShip(int num) { ship = num; }

	public boolean getTrueLag() { return trueLag; }

	public void toggleTrueLag(boolean sl) {
		trueLag = sl;
	}

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
		fouls = 0;
		notes = "-";
	}

	public void busy(int yn) { busy = yn; }

	public int getBusy() { return busy; }

	public void register() {
	    playerState = 0;
	    playerID = players;
  	    players++;
	}

	public void playing() { playerState = 1; }

	public void sleeping() { playerState = 0; }

	public void remove() { playerState = 2; }

	public boolean getQSent() { return qSent; }

	public void setQSent(boolean q) { qSent = q; }
}
