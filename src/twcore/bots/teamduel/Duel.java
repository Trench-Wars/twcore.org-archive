package twcore.bots.teamduel;

//WHEN fnCommentID = 3 the time sometimes gets very large.
//Scores are null/void

public class Duel {

    // The duel box the duel is being played in
    private DuelBox m_duelBox;
    private int m_boxType;

    // Team IDs for the duel
    private int m_challengerTeam;
    private int m_challengedTeam;
    
    // Team Freqs for the duel
    private int m_challengerFreq;
    private int m_challengedFreq;

    // The team player names for the duel
    private String[] m_challenger = new String[2];
    private String[] m_challenged = new String[2];

    // Ruleset for the duel
    private int m_division;
    private int m_toWin;
    private boolean m_noCount;

    // Stat tracking for players
    private DuelPlayerStats[] m_challengerStats = new DuelPlayerStats[2];
    private DuelPlayerStats[] m_challengedStats = new DuelPlayerStats[2];

    private boolean m_cancelChallenger = false;
    private boolean m_cancelChallenged = false;
    private boolean[] m_scoreChallenger = { true, true };
    private boolean[] m_scoreChallenged = { true, true };

    private boolean m_gameStarted = false;
    private int m_startTime = 0;
    private int m_time = 0;
    private boolean m_locked;
    private boolean m_settingUp;

    /**
     * Basic constructor for duel which holds information over a duel between
     * two players.
     * 
     * @param _duelBox
     *            A DuelBox object for the box the duel is being held in
     * @param _challenge
     *            A DuelChallenge object for the players
     */
    public Duel(DuelBox duelBox, DuelChallenge challenge) {

        // Set the duelbox to be in use
        duelBox.toggleUse();
        m_duelBox = duelBox;
        m_boxType = challenge.getBoxType();

        // Save Teams
        m_challengerTeam = challenge.getChallengerTeam();
        m_challengedTeam = challenge.getChallengedTeam();
        // Save players
        m_challenger = challenge.getChallenger();
        m_challenged = challenge.getChallenged();

        // Save ruleset
        m_division = challenge.getDivision();
        m_toWin = challenge.getToWin();
        m_noCount = challenge.getNoCount();
        m_challengerFreq = getBoxFreq();
        m_challengedFreq = getBoxFreq() + 1;
        
        m_settingUp = true;

        // Create stat tracking objects
        m_challengerStats[0] = new DuelPlayerStats(m_challenger[0], m_challengerTeam, m_division, m_challengerFreq, getSafeA1(), getA1());
        m_challengerStats[1] = new DuelPlayerStats(m_challenger[1], m_challengerTeam, m_division, m_challengerFreq, getSafeA2(), getA2());
        m_challengedStats[0] = new DuelPlayerStats(m_challenged[0], m_challengedTeam, m_division, m_challengedFreq, getSafeB1(), getB1());
        m_challengedStats[1] = new DuelPlayerStats(m_challenged[1], m_challengedTeam, m_division, m_challengedFreq, getSafeB2(), getB2());

        // Save Ships 
        if (m_division == 4 || m_division == 7) {
            m_challengerStats[0].setShip(7);
            m_challengerStats[1].setShip(7);
            m_challengedStats[0].setShip(7);
            m_challengedStats[1].setShip(7);
        } else if (m_division == 2) {
            m_challengerStats[0].setShip(2);
            m_challengerStats[1].setShip(2);
            m_challengedStats[0].setShip(2);
            m_challengedStats[1].setShip(2);
        }else if (m_division == 3) {
            m_challengerStats[0].setShip(3);
            m_challengerStats[1].setShip(3);
            m_challengedStats[0].setShip(3);
            m_challengedStats[1].setShip(3);
        } else {
            m_challengerStats[0].setShip(1);
            m_challengerStats[1].setShip(1);
            m_challengedStats[0].setShip(1);
            m_challengedStats[1].setShip(1);
        }
        
        m_locked = true;
    }
    
    public boolean isSettingUp() {
        return m_settingUp;
    }
    
    public void settingUpOn() {
        m_settingUp = true;
    }
    
    public void settingUpOff() {
        m_settingUp = false;
    }

    /**
     * Returns the current name of the league being played
     * 
     * @return A String representation of the league being played.
     */
    public String getDivision() {

        if (m_division == 1)
            return "Warbird";
        else if (m_division == 2)
            return "Javelin";
        else if (m_division == 3)
            return "Spider";
        else if (m_division == 4)
            return "Lancaster";
        else if (m_division == 5)
            return "Mixed";
        else if (m_division == 7)
            return "Lancaster";
        else
            return "";
    }

    /**
     * Returns an integer value representing the league being played
     * 
     * @return An int representation of the league being played.
     */
    public int getDivisionID() {
        return m_division;
    }

    public void started() {
        m_settingUp = false;
        m_gameStarted = true;
        m_startTime = (int) (System.currentTimeMillis() / 1000);
        m_locked = true;
    }

    public boolean hasStarted() {
        return m_gameStarted;
    }
    
    public boolean getNoCount() {
        return m_noCount;
    }
    
    public boolean isLocked() {
        return m_locked;
    }
    
    public void setLockOn() {
        m_locked = true;
    }
    
    public void setLockOff() {
        m_locked = false;
    }

    public void endTime() {
        m_time = (int) (System.currentTimeMillis() / 1000) - m_startTime;
    }
    
    public int getTime() {
        return m_time;
    }

    public String showScore() {
        String score = m_challenger[0] + " and " + m_challenger[1] + " vs " + m_challenged[0] + " and " + m_challenged[1] + ": ";
        int score1 = m_challengerStats[0].getDeaths() + m_challengerStats[1].getDeaths();
        int score2 = m_challengedStats[0].getDeaths() + m_challengedStats[1].getDeaths();
        score += score1 + "-" + score2;
        return score;
    }

    public String[] getOpponent(String name) {
        if (name.equalsIgnoreCase(m_challenger[0]) || name.equalsIgnoreCase(m_challenger[1]))
            return m_challenged;
        else
            return m_challenger;
    }
    
    public String toString() {
        String duel = "|";
        int box = getBoxNumber();
        if (box / 10 > 0) {
                 // | Box | Division | Teams
            duel += "  " + box + " |";
        } else
            duel += " " + box + "   |";

        if (m_division== 1 || m_division== 2) 
            duel += " " + getDivision() + "  | ";
        else if (m_division== 3)
            duel += "  " + getDivision() + "  | ";
        else if (m_division== 4 || m_division== 7)
            duel += " " + getDivision() + "| ";
        else if (m_division== 5)
            duel += "  " + getDivision() + "   | ";
        
        duel += m_challenger[0] + " and " + m_challenger[1] + " vs " + m_challenged[0] + " and " + m_challenged[1];
        
        while (duel.length() < 82) {
            duel += " ";
        }
        
        return duel + "|";
    }

    public void addDeath(String name) {
        if (name.equalsIgnoreCase(m_challenger[0])) {
            m_challengerStats[0].addDeath();
        }
        else if (name.equalsIgnoreCase(m_challenger[1])) {
            m_challengerStats[1].addDeath();
        }
        else if (name.equalsIgnoreCase(m_challenged[0])) {
            m_challengedStats[0].addDeath();
        }
        else if (name.equalsIgnoreCase(m_challenged[1])) {
            m_challengedStats[1].addDeath();
        }
    }

    public void addKill(String name) {
        if (name.equalsIgnoreCase(m_challenger[0]))
            m_challengerStats[0].addKill();
        else if (name.equalsIgnoreCase(m_challenger[1]))
            m_challengerStats[1].addKill();
        else if (name.equalsIgnoreCase(m_challenged[0]))
            m_challengedStats[0].addKill();
        else if (name.equalsIgnoreCase(m_challenged[1]))
            m_challengedStats[1].addKill();
    }

    public DuelPlayerStats getPlayer(String name) {
        if (name.equalsIgnoreCase(m_challenger[0]))
            return m_challengerStats[0];
        else if (name.equalsIgnoreCase(m_challenger[1]))
            return m_challengerStats[1];
        else if (name.equalsIgnoreCase(m_challenged[0]))
            return m_challengedStats[0];
        else if (name.equalsIgnoreCase(m_challenged[1]))
            return m_challengedStats[1];
        else
            return null;
    }

    public DuelPlayerStats[] getChallengerStats() {
        return m_challengerStats;
    }

    public DuelPlayerStats[] getChallengedStats() {
        return m_challengedStats;
    }
    
    public int getChallengerFreq() {
        return m_challengerFreq;
    }
    
    public int getChallengedFreq() {
        return m_challengedFreq;
    }

    public DuelPlayerStats getPlayerOne() {
        return m_challengerStats[0];
    }

    public DuelPlayerStats getPlayerTwo() {
        return m_challengerStats[1];
    }

    public DuelPlayerStats getPlayerThree() {
        return m_challengedStats[0];
    }

    public DuelPlayerStats getPlayerFour() {
        return m_challengedStats[1];
    }

    public int getBoxFreq() {
        return m_duelBox.getBoxNumber() * 2;
    }

    public int getBoxNumber() {
        return m_duelBox.getBoxNumber();
    }
    
    public int getTeamID(String name) {
        if (name.equalsIgnoreCase(m_challenger[0]) || name.equalsIgnoreCase(m_challenger[1])) 
            return m_challengerTeam;
        else
            return m_challengedTeam;
    }

    public boolean toggleCancelGame(String name) {
        if (name.equalsIgnoreCase(m_challenger[0]) || name.equalsIgnoreCase(m_challenger[1]))
            return m_cancelChallenger = !m_cancelChallenger;
        else if (name.equalsIgnoreCase(m_challenged[0]) || name.equalsIgnoreCase(m_challenged[1]))
            return m_cancelChallenged = !m_cancelChallenged;
        else
            return false;
    }

    public boolean toggleScoreboard(String name) {
        if (name.equalsIgnoreCase(m_challenger[0]))
            return m_scoreChallenger[0] = !m_scoreChallenger[0];
        if (name.equalsIgnoreCase(m_challenger[1]))
            return m_scoreChallenger[1] = !m_scoreChallenger[1];
        else if (name.equalsIgnoreCase(m_challenged[0]))
            return m_scoreChallenged[0] = !m_scoreChallenged[0];
        else if (name.equalsIgnoreCase(m_challenged[1]))
            return m_scoreChallenged[1] = !m_scoreChallenged[1];
        else
            return true;
    }

    public boolean scoreboard(int i) {
        if (i == 1)
            return m_scoreChallenger[0];
        else if (i == 2)
            return m_scoreChallenger[1];
        else if (i == 3)
            return m_scoreChallenged[0];
        else if (i == 4)
            return m_scoreChallenged[1];
        else
            return true;
    }

    public boolean getCancelState(String name) {
        if (name.equalsIgnoreCase(m_challenger[0]) || name.equalsIgnoreCase(m_challenger[1]))
            return m_cancelChallenger;
        else if (name.equalsIgnoreCase(m_challenged[0]) || name.equalsIgnoreCase(m_challenged[1]))
            return m_cancelChallenged;
        else
            return false;
    }

    public int getPlayerNumber(String name) {
        if (name.equalsIgnoreCase(m_challenger[0]) || name.equalsIgnoreCase(m_challenger[1]))
            return 1;
        else if (name.equalsIgnoreCase(m_challenged[0]) || name.equalsIgnoreCase(m_challenged[1]))
            return 2;
        else
            return -1;
    }

    public int[] getChallengerShip() {
        return new int[] {m_challengerStats[0].getShip(), m_challengerStats[1].getShip()};
    }

    public int[] getChallengedShip() {
        return new int[] {m_challengedStats[0].getShip(), m_challengedStats[1].getShip()};
    }
    
    public void updateShips(int[] challenger, int[] challenged) {
        m_challengerStats[0].setShip(challenger[0]);
        m_challengerStats[1].setShip(challenger[1]);
        m_challengedStats[0].setShip(challenged[0]);
        m_challengedStats[1].setShip(challenged[1]);
    }

    public int getChallengerTeam() {
        return m_challengerTeam;
    }

    public int getChallengedTeam() {
        return m_challengedTeam;
    }

    public String[] getChallenger() {
        return m_challenger;
    }

    public String[] getChallenged() {
        return m_challenged;
    }
    
    public int[] getA1() {
        return new int[] { m_duelBox.getAXOne(), m_duelBox.getAYOne() };
    }
    
    public int[] getA2() {
        return new int[] { m_duelBox.getAXTwo(), m_duelBox.getAYTwo() };
    }
    
    public int[] getB1() {
        return new int[] { m_duelBox.getBXOne(), m_duelBox.getBYOne() };
    }
    
    public int[] getB2() {
        return new int[] { m_duelBox.getBXTwo(), m_duelBox.getBYTwo() };
    }

    public int[] getSafeA1() {
        return new int[] { m_duelBox.getSafeAXOne(), m_duelBox.getSafeAYOne() };
    }

    public int[] getSafeA2() {
        return new int[] { m_duelBox.getSafeAXTwo(), m_duelBox.getSafeAYTwo() };
    }

    public int[] getSafeB1() {
        return new int[] { m_duelBox.getSafeBXOne(), m_duelBox.getSafeBYOne() };
    }

    public int[] getSafeB2() {
        return new int[] { m_duelBox.getSafeBXTwo(), m_duelBox.getSafeBYTwo() };
    }
    
    public int getAXOne() {
        return m_duelBox.getAXOne();
    }

    public int getAXTwo() {
        return m_duelBox.getAXTwo();
    }

    public int getAYOne() {
        return m_duelBox.getAYOne();
    }

    public int getAYTwo() {
        return m_duelBox.getAYTwo();
    }

    public int getBXOne() {
        return m_duelBox.getAXOne();
    }

    public int getBXTwo() {
        return m_duelBox.getAXTwo();
    }

    public int getBYOne() {
        return m_duelBox.getAYOne();
    }

    public int getBYTwo() {
        return m_duelBox.getAYTwo();
    }

    public int getSafeAXOne() {
        return m_duelBox.getSafeAXOne();
    }

    public int getSafeAXTwo() {
        return m_duelBox.getSafeAXTwo();
    }

    public int getSafeAYOne() {
        return m_duelBox.getSafeAYOne();
    }

    public int getSafeAYTwo() {
        return m_duelBox.getSafeAYTwo();
    }

    public int getSafeBXOne() {
        return m_duelBox.getSafeBXOne();
    }

    public int getSafeBXTwo() {
        return m_duelBox.getSafeBXTwo();
    }

    public int getSafeBYOne() {
        return m_duelBox.getSafeBYOne();
    }

    public int getSafeBYTwo() {
        return m_duelBox.getSafeBYTwo();
    }
    
    public int getAreaMinX() {
        return m_duelBox.getAreaMinX();
    }
    
    public int getAreaMinY() {
        return m_duelBox.getAreaMinY();
    }
    
    public int getAreaMaxX() {
        return m_duelBox.getAreaMaxX();
    }
    
    public int getAreaMaxY() {
        return m_duelBox.getAreaMaxY();
    }

    public int toWin() {
        return m_toWin;
    }
    
    public int[] getCoords(String name) {
        if (name.equalsIgnoreCase(m_challenger[0]) || name.equalsIgnoreCase(m_challenger[1]))
            return new int[] { getAXOne(), getAYOne(), getAXTwo(), getAYTwo() };
        else
            return new int[] { getBXOne(), getBYOne(), getBXTwo(), getBYTwo() };
    }
    
    public int[] getSafeCoords(String name) {
        if (name.equalsIgnoreCase(m_challenger[0]) || name.equalsIgnoreCase(m_challenger[1]))
            return new int[] { getSafeAXOne(), getSafeAYOne(), getSafeAXTwo(), getSafeAYTwo() };
        else
            return new int[] { getSafeBXOne(), getSafeBYTwo(), getSafeBXTwo(), getSafeBYTwo() };
    }

    public WarpPoint getRandomWarpPoint() {
        return m_duelBox.getRandomWarpPoint();
    }

    public void toggleDuelBox() {
        m_duelBox.toggleUse();
    }
    
    public int getBoxType() {
        return m_boxType;
    }
}