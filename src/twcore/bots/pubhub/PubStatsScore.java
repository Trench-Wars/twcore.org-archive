package twcore.bots.pubhub;

import java.util.Date;

/**
 * Domain object for storing the (public) statistics
 * This object is used by the pubbotstats and pubhubstats modules
 * 
 * @author Maverick
 */
public class PubStatsScore {
	private PubStatsPlayer player;
	
	/** Ship number (1-8, 0 = spectator) */
	private int ship;
	
	private int flagPoints;
	private int killPoints;
	private int wins;
	private int losses;
	private int rate;
	private float average;
	
	private Date date;
	private boolean scorereset = false;
	
	public PubStatsScore(PubStatsPlayer player, int ship, int flagPoints, int killPoints, int wins, int losses, int rate, float average) {
		this.player = player;
		this.ship = ship;
		this.flagPoints = flagPoints;
		this.killPoints = killPoints;
		this.wins = wins;
		this.losses = losses;
		this.rate = rate;
		this.average = average;
		
		this.date = new Date();
	}
	
	//******************************* Getters & Setters *************************************//
	
	
	/**
	 * @return the player
	 */
	public PubStatsPlayer getPlayer() {
		return player;
	}

	/**
	 * @param player the player to set
	 */
	public void setPlayer(PubStatsPlayer player) {
		this.player = player;
	}

	/**
	 * @return the ship
	 */
	public int getShip() {
		return ship;
	}

	/**
	 * @param ship the ship to set
	 */
	public void setShip(int ship) {
		this.ship = ship;
	}

	/**
	 * @return the flagPoints
	 */
	public int getFlagPoints() {
		return flagPoints;
	}

	/**
	 * @param flagPoints the flagPoints to set
	 */
	public void setFlagPoints(int flagPoints) {
		this.flagPoints = flagPoints;
	}

	/**
	 * @return the killPoints
	 */
	public int getKillPoints() {
		return killPoints;
	}

	/**
	 * @param killPoints the killPoints to set
	 */
	public void setKillPoints(int killPoints) {
		this.killPoints = killPoints;
	}

	/**
	 * @return the wins
	 */
	public int getWins() {
		return wins;
	}

	/**
	 * @param wins the wins to set
	 */
	public void setWins(int wins) {
		this.wins = wins;
	}

	/**
	 * @return the losses
	 */
	public int getLosses() {
		return losses;
	}

	/**
	 * @param losses the losses to set
	 */
	public void setLosses(int losses) {
		this.losses = losses;
	}

	/**
	 * @return the rate
	 */
	public int getRate() {
		return rate;
	}

	/**
	 * @param rate the rate to set
	 */
	public void setRate(int rate) {
		this.rate = rate;
	}

	/**
	 * @return the average
	 */
	public float getAverage() {
		return average;
	}

	/**
	 * @param average the average to set
	 */
	public void setAverage(float average) {
		this.average = average;
	}

	/**
	 * @return the date
	 */
	public Date getDate() {
		return date;
	}

	/**
	 * @param date the date to set
	 */
	public void setDate(Date date) {
		this.date = date;
	}

	/**
	 * @return the scorereset
	 */
	public boolean isScorereset() {
		return scorereset;
	}

	/**
	 * @param scorereset the scorereset to set
	 */
	public void setScorereset(boolean scorereset) {
		this.scorereset = scorereset;
	}
	
	
	
}
