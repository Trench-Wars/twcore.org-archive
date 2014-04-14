package twcore.bots.tournybot;

public class fStats {

	pStats player1;
	pStats player2;
	int dbid;
	int rating;
	int ratingChange = 0;
	int playerRound = 0;
	int playerBox = 0;
	int freqState = 0;
	int playerNro = 0;
	int busy = 0;
	int randNum = 0;
	String lag = "No";
	String realBox = "0";
	int wins;
	int losses;
	boolean qSent = false;

	public fStats() {}

	public void addPlayer(pStats name, int nro) {
		if (nro == 1) { player1 = name; } else { player2 = name; }
	}

	public pStats getP1() { return player1; }

	public pStats getP2() { return player2; }

	public String getName1() { return player1.getName(); }

	public String getName2() { return player2.getName(); }

	public String getNames() {
		if (player2 == null) {
			return player1.getName();
		} else {
			return player1.getName() + " and " + player2.getName();
		}
	}

	public void incrementRound() { playerRound++; }

	public void setDBID(int n) { dbid = n; }

	public int getDBID() { return dbid; }

	public void setRating(int r) { rating = r; }

	public int getRating() { return rating; }

	public void setRatingChange(int r) { ratingChange = r; }

	public int getRatingChange() { return ratingChange; }

	public int getFRating(int prize) {
		if (playerRound == 7) {
			return ratingChange + (prize / 2);
		} else if (playerRound == 6) {
			return ratingChange + (prize / 4);
		} else if (playerRound == 5) {
			return ratingChange + (prize / 8);
		} else {
			return ratingChange;
		}
	}

	public void setRealBox(String box) { realBox = box; }

	public String getRealBox() { return realBox; }

	public int getTotalKills() {
		if (player2 != null) {
			int totalKills = player1.getTotalKills() + player2.getTotalKills();
			return totalKills;
		} else {
			return player1.getTotalKills();
		}
	}

	public int getGameKills() {
		if (player2 != null) {
			int gameKills = player1.getGameKills() + player2.getGameKills();
			return gameKills;
		} else {
			return player1.getGameKills();
		}
	}

	public int getTotalDeaths() {
		if (player2 != null) {
			int totalDeaths = player1.getTotalDeaths() + player2.getTotalDeaths();
			return totalDeaths;
		} else {
			return player1.getTotalDeaths();
		}
	}

	public int getGameDeaths() {
		if (player2 != null) {
			int gameDeaths = player1.getGameDeaths() + player2.getGameDeaths();
			return gameDeaths;
		} else {
			return player1.getGameDeaths();
		}
	}

	public int getFreqState() { return freqState; }

	public int getRound() { return playerRound; }

	public int getBox() { return playerBox; }

	public int getPlayerNro() { return playerNro; }

	public void incrementWins() { wins++; }

	public void incrementLosses() { losses++; }

	public int getWins() { return wins; }

	public int getLosses() { return losses; }

	public int getRandNum() { return randNum; }

	public void setRandNum(int num) { randNum = num; }

	public void laggedOut() { lag = "Yes"; }

	public String getLagOut() { return lag; }

	public void busy(int yn) { busy = yn; }

	public int getBusy() { return busy; }

	public void changeBox(int box) { playerBox = box; }

	public void changeNro(int nro) { playerNro = nro; }

	public void changeRound(int round) { playerRound = round; }

	public void register() {
	    playerRound = 1;
	    freqState = 0;
	}

	public void playing() { freqState = 1; }

	public void sleeping() { freqState = 0; }

	public void remove() { freqState = 2; }

	public boolean getQSent() { return qSent; }

	public void setQSent(boolean q) { qSent = q; }
}
