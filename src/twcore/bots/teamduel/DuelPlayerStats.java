package twcore.bots.teamduel;

public class DuelPlayerStats {

    // Information on this player
    private String m_name;
    private int m_team;
    private int m_division;
    private int m_ship;
    private int m_freq;

    // Tracking for stats
    private int m_kills = 0;
    private int m_deaths = 0;
    private int m_lagouts = 0;
    private int m_spawns = 0;
    private int m_spawned = 0;
    private int m_warps = 0;

    // State variables for a player
    private int m_timeOfLastDeath;
    private int m_timeOfLastSpawn;
    private int m_timeOfLastReturn = 0;
    private int m_timeOfLastWarp = 0;
    private int m_time;
    private String m_lastKiller;
    private int[] m_safeCoords;
    private int[] m_coords;
    private boolean m_isOut;

    public DuelPlayerStats(String name, int team, int div, int ship, int freq) {
        m_name = name;
        m_team = team;
        m_division = div;
        m_ship = ship;
        m_freq = freq;
        m_isOut = false;
    }

    public DuelPlayerStats(String name, int team, int div, int ship, int freq, int[] safe, int[] coord) {
        m_name = name;
        m_team = team;
        m_division = div;
        m_ship = ship;
        m_freq = freq;
        m_isOut = false;
        m_safeCoords = safe;
        m_coords = coord;
    }

    public DuelPlayerStats(String name, int team, int div, int freq, int[] safe, int[] coord) {
        m_name = name;
        m_team = team;
        m_division = div;
        m_freq = freq;
        m_isOut = false;
        m_safeCoords = safe;
        m_coords = coord;
    }

    public String getName() {
        return m_name;
    }

    public int getTeam() {
        return m_team;
    }

    public int getDivision() {
        return m_division;
    }

    public int getShip() {
        return m_ship;
    }

    public void setShip(int ship) {
        m_ship = ship;
    }

    public int getFreq() {
        return m_freq;
    }

    public void setFreq(int freq) {
        m_freq = freq;
    }
    
    public int[] getSafeCoords() {
        return m_safeCoords;
    }
    
    public int[] getCoords() {
        return m_coords;
    }

    public int getKills() {
        return m_kills;
    }

    public void addKill() {
        m_kills++;
    }

    public void removeKill() {
        m_kills--;
    }

    public int getDeaths() {
        return m_deaths;
    }

    public void setDeaths(int d) {
        m_deaths = d;
    }

    public void addDeath() {
        m_deaths++;
        m_timeOfLastDeath = (int) (System.currentTimeMillis() / 1000);
    }
    
    public int getTimeOfLastDeath() {
        return m_timeOfLastDeath;
    }
    
    public void setLastReturn(int time) {
        m_timeOfLastReturn = time;
    }
    
    public int getTimeFromLastReturn() {
        return (int)(System.currentTimeMillis() / 1000) - m_timeOfLastReturn;
    }

    public void removeDeath() {
        m_deaths--;
    }

    public void setLastDeath() {
        m_timeOfLastDeath = (int) (System.currentTimeMillis() / 1000);
    }

    public int getTimeFromLastDeath() {
        return (int)(System.currentTimeMillis() / 1000) - m_timeOfLastDeath;
    }

    public void setLastWarp(int time) {
        m_timeOfLastWarp = time;
    }

    public int getTimeFromLastWarp() {
        return (int)(System.currentTimeMillis() / 1000) - m_timeOfLastWarp;
    }

    public void setLastKiller(String name) {
        m_lastKiller = name;
    }

    public String getLastKiller() {
        return m_lastKiller;
    }

    public void addLagout() {
        m_lagouts++;
    }

    public int getLagouts() {
        return m_lagouts;
    }
    
    public boolean isOut() {
        return m_isOut;
    }
    
    public void setOut() {
        m_isOut = true;
    }

    public void setTime( int t ) { m_time = t; }
    
    public int  getTime()   { return m_time; }
    
    public void setSpawn(int t) {
        m_timeOfLastSpawn = t;
    }
    
    public int getLastSpawn() {
        return m_timeOfLastSpawn;
    }

    public int getSpawns() {
        return m_spawns;
    }

    public void addSpawn() {
        m_spawns++;
    }

    public int getSpawned() {
        return m_spawned;
    }

    public void addSpawned() {
        m_spawned++;
    }

    public int getWarps() {
        return m_warps;
    }

    public void addWarp() {
        m_warps++;
    }

    public void removeWarp() {
        m_warps--;
    }
}
