package twcore.bots.teamduel;

/**
 * Holds data of player in a duel. Might change to team stats
 */
public class DuelPlayer {

    private int m_toWin = 5;
    private int m_lag = 0;
    private boolean m_noCount = true;

    /**
     * Constructor should not take a ResultSet it can not close. Reimplemented
     * as straight variables.
     */
    public DuelPlayer(int lag, int killstowin, boolean nc) {
        m_lag = lag;
        m_toWin = killstowin;
        m_noCount = nc;
    }
    
    public DuelPlayer(int lag, int killstowin) {
        m_lag = lag;
        m_toWin = killstowin;
    }

    public DuelPlayer(int lag) {
        m_lag = lag;
    }

    public int getToWin() {
        return m_toWin;
    }

    public int getAverageLag() {
        return m_lag;
    }
    
    public boolean getNoCount() {
        return m_noCount;
    }
}