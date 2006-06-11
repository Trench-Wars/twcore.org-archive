
package twcore.bots.duelbot;

public class DuelPlayerStats {

	//Information on this player
	private String 	m_name;
	private int 	m_ship;
	private int 	m_freq;

	//Tracking for stats
	private int		m_kills		= 0;
	private int		m_deaths	= 0;
	private int 	m_lagouts	= 0;
	private int 	m_spawns 	= 0;
	private int		m_warps		= 0;

	private int 	m_timeOfLastDeath;
	private int		m_time;

	//State variables for a player
	private boolean m_warping;

	public DuelPlayerStats( String _name, int _ship, int _freq ) {

		m_name = _name;
    	m_ship = _ship;
    	m_freq = _freq;
    }

	public String getName() { return m_name; }

	public int    getShip() { return m_ship; }
	public void   setShip( int _ship ) { m_ship = _ship; }

	public int	  getFreq() { return m_freq; }
	public void   setFreq( int _freq ) { m_freq = _freq; }

    public int  getKills()  { return m_kills; }
	public void addKill()   { m_kills++; }
	public void removeKill() { m_kills--; }

    public int  getDeaths() { return m_deaths; }
    public void setDeaths( int d ) { m_deaths = d; }
    public void addDeath()  {
    	m_deaths++;
    	m_timeOfLastDeath = (int)(System.currentTimeMillis() / 1000);
    }
    public void removeDeath() { m_deaths--; }

    public void addLagout() { m_lagouts++; }
    public int  getLagouts() { return m_lagouts; }

    public int timeFromLastDeath() {
    	return (int)(System.currentTimeMillis() / 1000) - m_timeOfLastDeath;
    }

    public void setTime( int t ) { m_time = t; }
    public int  getTime()   { return m_time; }

    public boolean isWarping() { return m_warping; }
    public void setWarping( boolean _warping ) { m_warping = _warping; }

    public int getSpawns() { return m_spawns; }
    public void addSpawn()  { m_spawns++; }

    public int getWarps() { return m_warps; }
    public void addWarp() { m_warps++; }
    public void removeWarp() { m_warps--; }
}
