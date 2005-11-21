package twcore.bots.duelbot;



public class DuelChallenge {
	
	//The challenger/challenged players
	private String 	m_challenger;
	private String 	m_challenged;
	
	//Gametype 1-warbird, 2-javelin, 3-spider
	//Other variables relating to the game to play
	private int 	m_gameType;
	private boolean m_winBy2;
	private boolean m_noCount;
	private boolean m_deathWarp;
	private int 	m_toWin;

	//Issued time of challenge
	private int 	m_issueTime;
	
	/** This constructor is for normal league challenges
	 */
	public DuelChallenge( String _challenger, String _challenged, DuelPlayer _player, int _type ) {
		
		m_challenger = 	_challenger;
		m_challenged = 	_challenged;
		
		m_gameType = 	_type;
		m_winBy2 = 		_player.getWinBy2();
		m_noCount = 	_player.getNoCount();
		m_deathWarp = 	_player.getDeathWarp();
		m_toWin = 		_player.getToWin();

		m_issueTime = 	((int)System.currentTimeMillis()/1000);
	}
	
	public String getChallenger() { return m_challenger; }
	public String getChallenged() { return m_challenged; }
	
	public int getGameType() { return m_gameType; }
	public boolean getWinBy2() { return m_winBy2; }
	public boolean getNoCount() { return m_noCount; }
	public boolean getDeathWarp() { return m_deathWarp; }
	public int getToWin() { return m_toWin; }
	
	public int getElapsedTime() {
		return ((int)System.currentTimeMillis()/1000) - m_issueTime;
	}
}