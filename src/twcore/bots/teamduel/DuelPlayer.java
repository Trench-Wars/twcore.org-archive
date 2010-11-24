package twcore.bots.teamduel;

/**
 * Holds data of player in a duel. Might change to team stats
 */
public class DuelPlayer {

    private String name;
    private int m_userID = -1;
    private int m_toWin = 5;
    private int m_lag = 0;
    private boolean m_noCount = true;
    
    private boolean enabled = false;
    private boolean banned = false;
    private int[] teams = new int[6];
    private int[] pids = new int[6];
    private String[] partners = new String[6];

    /**
     * Constructor should not take a ResultSet it can not close. Reimplemented
     * as straight variables.
     */
    public DuelPlayer(String name, int id, int lag, int killstowin, boolean nc, boolean enable, boolean ban, int[] teamlist, String[] parts, int[] ids) {
        this.name = name;
        m_userID = id;
        m_lag = lag;
        m_toWin = killstowin;
        m_noCount = nc;
        enabled = enable;
        banned = ban;
        teams = teamlist;
        partners = parts;
        pids = ids;
    }
    
    public String getName() {
        return name;
    }
    
    public void setPartner(String name, int division) {
        partners[division] = name;
    }
    
    public void setPartner(DuelPlayer player, int division) {
        partners[division] = player.getName();
        pids[division] = player.getID();
    }
    
    public String getPartner(int division) {
        return partners[division];
    }
    
    public String[] getPartners() {
        return partners;
    }
    
    public int[] getPIDs() {
        return pids;
    }
    
    public int getPID(int div) {
        return pids[div];
    }
    
    public void setTeams(int[] teamlist) {
        teams = teamlist;
    }
    
    public void setTeams(int team, int division) {
        teams[division] = team;
    }
    
    public int[] getTeams() {
        return teams;
    }
    
    public int getTeam(int division) {
        return teams[division];
    }
    
    public void ban() {
        banned = true;
    }
    
    public void unban() {
        banned = false;
    }
    
    public boolean isBanned() {
        return banned;
    }

    public DuelPlayer(int lag) {
        m_lag = lag;
    }
    
    public void disable() {
        enabled = false;
        partners = new String[] {null, null, null, null, null, null};
        pids = new int[] {-1, -1, -1, -1, -1, -1};
    }
    
    public void enable() {
        enabled = true;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public int getID() {
        return m_userID;
    }

    public int getDeaths() {
        return m_toWin;
    }

    public int getAverageLag() {
        return m_lag;
    }
    
    public void setDeaths(int deaths) {
        m_toWin = deaths;
    }
    
    public boolean getNoCount() {
        return m_noCount;
    }
    
    public void setNoCount(boolean nc) {
        m_noCount = nc;
    }
    
    public boolean hasTeams() {
        for (int i = 1; i <= 5; i++) {
            if (teams[i] > -1)
                return true;
        }
        return false;
    }
}