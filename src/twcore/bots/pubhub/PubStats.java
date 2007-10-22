package twcore.bots.pubhub;

import java.util.Date;

/**
 * Domain object for storing the (public) statistics
 * This object is used by the pubbotstats and pubhubstats modules
 * 
 * @author Maverick
 */
public class PubStats {
	private String playername;
	
	/** Ship number (1-8, 0 = spectating) */
	private int ship;
	private String squad;
	
	private int flagPoints;
	private int killPoints;
	private int wins;
	private int losses;
	private int rate;
	private float average;
	
	private Date date;
	
	public PubStats() {
		this.date = new Date();
	}
	
	//******************************* Getters & Setters *************************************//
	
	
	/**
	 * @return the playername
	 */
	public String getPlayername() {
		return playername;
	}
	/**
	 * @param playername the playername to set
	 */
	public void setPlayername(String playername) {
		this.playername = playername;
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
	 * @return the squad
	 */
	public String getSquad() {
		return squad;
	}
	/**
	 * @param squad the squad to set
	 */
	public void setSquad(String squad) {
		this.squad = squad;
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
}
