package twcore.bots.duelbot;

import java.sql.ResultSet;

/**
 * Holds data of player in a duel. 
 */
public class DuelPlayer {

	private boolean m_winBy2 	= false;
	private boolean m_noCount 	= false;
	private boolean m_deathWarp = false;
	private int     m_toWin 	= 10;
	private int     m_lag       = 0;

        /**
         * Constructor should not take a ResultSet it can not close.  Reimplemented
         * as straight variables.
         */
        public DuelPlayer( boolean winby2, boolean nocount, boolean deathwarp, int lag, int killstowin ) {
            m_winBy2 = winby2;
            m_noCount = nocount;
            m_deathWarp = deathwarp;
            m_lag = lag;
            m_toWin = killstowin;
        }

	public boolean getWinBy2() { return m_winBy2; }
	public boolean getNoCount() { return m_noCount; }
	public boolean getDeathWarp() { return m_deathWarp; }
	public int getToWin() { return m_toWin; }
	public int getAverageLag() { return m_lag; }
}