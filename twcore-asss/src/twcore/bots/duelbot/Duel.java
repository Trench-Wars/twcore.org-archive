package twcore.bots.duelbot;


//WHEN fnCommentID = 3 the time sometimes gets very large.
//Scores are null/void

public class Duel {

	//The duel box the duel is being played in
	private DuelBox m_duelBox;

	//The players for the duel
	private String 	m_challenger;
	private String 	m_challenged;

	//Ruleset for the duel
	private int 	m_gameType;
	private boolean m_winBy2;
	private boolean m_noCount;
	private boolean m_deathWarp;
	private int 	m_toWin;

	//Stat tracking for players
	private DuelPlayerStats m_challengerStats;
	private DuelPlayerStats m_challengedStats;



	private boolean m_cancelChallenger = false;
	private boolean m_cancelChallenged = false;
	private boolean m_scoreChallenger  = true;
	private boolean m_scoreChallenged  = true;

	private boolean m_gameStarted = false;
	private int 	m_startTime = 0;

	/** Basic constructor for duel which holds information over a duel between
	 * two players.
	 * @param _duelBox A DuelBox object for the box the duel is being held in
	 * @param _challenge A DuelChallenge object for the players
	 */
	public Duel( DuelBox _duelBox, DuelChallenge _challenge ) {

		//Set the duelbox to be in use
		_duelBox.toggleUse();
		m_duelBox = _duelBox;

		//Save players
		m_challenger = _challenge.getChallenger();
		m_challenged = _challenge.getChallenged();

		//Save ruleset
		m_gameType = _challenge.getGameType();
		m_winBy2 = _challenge.getWinBy2();
		m_noCount = _challenge.getNoCount();
		m_deathWarp = _challenge.getDeathWarp();
		m_toWin = _challenge.getToWin();
		m_startTime = (int)System.currentTimeMillis();

		//Create stat tracking objects
		m_challengerStats = new DuelPlayerStats( m_challenger, m_gameType, getBoxFreq() );
		m_challengedStats = new DuelPlayerStats( m_challenged, m_gameType, getBoxFreq() + 1 );
	}

	/** Constructor for a tourny duel.
	 * @param _duelBox A DuelBox object for the box the duel is being held in
	 * @param _tournyGame A TournyGame object for the match
	 */
	public Duel( DuelBox _duelBox, TournyGame _tournyGame) {

		//Set the duelbox to be in use
		_duelBox.toggleUse();
		m_duelBox = _duelBox;

		//Save players
		m_challenger = _tournyGame.getPlayerOne();
		m_challenged = _tournyGame.getPlayerTwo();

		//Set tourny ruleset
		m_gameType = _tournyGame.getGameType();
		m_winBy2 = true;
		m_noCount = true;
		m_deathWarp = true;
		m_toWin = 10;
		m_startTime = (int)System.currentTimeMillis();

		//Create stat tracking objects
		m_challengerStats = new DuelPlayerStats( m_challenger, m_gameType, getBoxFreq() );
		m_challengedStats = new DuelPlayerStats( m_challenged, m_gameType, getBoxFreq() + 1 );
	}

	/** Returns the current name of the league being played
	 * @return A String representation of the league being played.
	 */
	public String getLeagueType() {

		if( m_gameType == 1 ) return "Warbird";
		else if( m_gameType == 2 ) return "Javelin";
		else if( m_gameType == 3 ) return "Spider";
		else if( m_gameType == 4 ) return "Leviathan";
		else if( m_gameType == 5 ) return "Terrier";
		else if( m_gameType == 6 ) return "Weasel";
		else if( m_gameType == 7 ) return "Lancaster";
		else if( m_gameType == 8 ) return "Shark";
		else return "";
	}

	/** Returns an integer value representing the league being played
	 * @return An int representation of the league being played.
	 */
	public int getLeagueId() { return m_gameType; }

	public void started() {
		m_gameStarted = true;
		m_startTime = (int)(System.currentTimeMillis()/1000);
	}
	public boolean hasStarted() { return m_gameStarted; }
	public int getTime() {
			return (int)(System.currentTimeMillis()/1000) - m_startTime;
	}

	public String showScore() {
		String score = m_challenger + " vs " + m_challenged + ": ";
		score += m_challengerStats.getKills() + "-" + m_challengedStats.getKills();
		return score;
	}

	public String getOpponent( String name ) {
		if( name.equals( m_challenger ) ) return m_challenged;
		else return m_challenger;
	}

	public void addDeath( String name ) {
		if( name.equals( m_challenger ) ) m_challengerStats.addDeath();
		else if( name.equals( m_challenged ) ) m_challengedStats.addDeath();
	}

	public void addKill( String name ) {
		if( name.equals( m_challenger ) ) m_challengerStats.addKill();
		else if( name.equals( m_challenged ) ) m_challengedStats.addKill();
	}

	public DuelPlayerStats getPlayer( String name ) {
		if( name.equals( m_challenger ) ) return m_challengerStats;
		else if( name.equals( m_challenged ) ) return m_challengedStats;
		else return null;
	}

	public DuelPlayerStats getPlayerOne() {
		return m_challengerStats;
	}

	public DuelPlayerStats getPlayerTwo() {
		return m_challengedStats;
	}

	public int getBoxFreq() {
		return m_duelBox.getBoxNumber() * 2;
	}

	public int getBoxNumber() { return m_duelBox.getBoxNumber(); }

	public boolean toggleCancelGame( String name ) {
		if( name.equals( m_challenger ) )
			return m_cancelChallenger = !m_cancelChallenger;
		else if( name.equals( m_challenged ) )
			return m_cancelChallenged = !m_cancelChallenged;
		else return false;
	}

	public boolean toggleScoreboard( String name ) {
		if( name.equals( m_challenger ) )
			return m_scoreChallenger = !m_scoreChallenger;
		else if( name.equals( m_challenged ) )
			return m_scoreChallenged = !m_scoreChallenged;
		else return true;
	}

	public boolean scoreboard( int i ) {
		if( i == 1 ) return m_scoreChallenger;
		else if( i == 2 ) return m_scoreChallenged;
		else return true;
	}

	public boolean getCancelState( String name ) {
		if( name.equals( m_challenger ) ) return m_cancelChallenger;
		else if( name.equals( m_challenged ) ) return m_cancelChallenged;
		else return false;
	}

	public int getPlayerNumber( String name ) {
		if( name.equals( m_challenger ) ) return 1;
		else return 2;
	}

	public int getShipType() { return m_gameType; }
	public int getXOne() { return m_duelBox.getXOne(); }
	public int getXTwo() { return m_duelBox.getXTwo(); }
	public int getYOne() { return m_duelBox.getYOne(); }
	public int getYTwo() { return m_duelBox.getYTwo(); }
	public int getSafeXOne() { return m_duelBox.getSafeXOne(); }
	public int getSafeXTwo() { return m_duelBox.getSafeXTwo(); }
	public int getSafeYOne() { return m_duelBox.getSafeYOne(); }
	public int getSafeYTwo() { return m_duelBox.getSafeYTwo(); }
	public boolean winBy2() { return m_winBy2; }
	public boolean noCount() { return m_noCount; }
	public boolean deathWarp() { return m_deathWarp; }
	public int toWin() { return m_toWin; }

	public WarpPoint getRandomWarpPoint() {
		return m_duelBox.getRandomWarpPoint();
	}

	public void toggleDuelBox() {
		m_duelBox.toggleUse();
	}
}