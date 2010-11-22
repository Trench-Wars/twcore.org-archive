package twcore.bots.teamduel;

public class DuelChallenge {

    // Team IDs
    private int m_challengerTeam;
    private int m_challengedTeam;
    
    // Team Ships
    private int m_challengerShip;
    private int m_challengedShip;

    // The challenger/challenged players
    private DuelPlayer initiater;
    private String[] m_challenger;
    private String[] m_challenged;

    // Gametype 1-warbird, 2-javelin, 3-spider
    // Other variables relating to the game to play
    private int m_division;
    private int m_toWin;
    private boolean m_noCount;
    private int m_boxType;

    // Issued time of challenge
    private int m_issueTime;
    
    // Keep track of who has accepted the challenge
    private boolean[] accepted = { false, false };

    /**
     * This constructor is for normal league challenges
     */
    public DuelChallenge(int team1, int team2, String challenger[], String challenged[], DuelPlayer player, int type, int box) {
        m_challengerTeam = team1;
        m_challengedTeam = team2;
        m_challenger = challenger;
        m_challenged = challenged;

        m_division = type;
        m_challengerShip = type;
        m_challengedShip = type;
        m_toWin = player.getToWin();
        m_noCount = player.getNoCount();
        m_boxType = box;
        initiater = player;

        m_issueTime = ((int) System.currentTimeMillis() / 1000);
    }
    
    public DuelPlayer getInitiater() {
        return initiater;
    }
    
    public int getBoxType() {
        return m_boxType;
    }

    public int getChallengerTeam() {
        return m_challengerTeam;
    }

    public int getChallengedTeam() {
        return m_challengedTeam;
    }

    public int getChallengerShip() {
        return m_challengerShip;
    }

    public int getChallengedShip() {
        return m_challengedShip;
    }

    public String[] getChallenger() {
        return m_challenger;
    }

    public String[] getChallenged() {
        return m_challenged;
    }

    public int getDivision() {
        return m_division;
    }

    public int getToWin() {
        return m_toWin;
    }
    
    public boolean getNoCount() {
        return m_noCount;
    }

    public void acceptOne() {
        accepted[0] = true;
    }

    public void acceptTwo() {
        accepted[1] = true;
    }
    
    public boolean[] getAccepted() {
        return accepted;
    }

    public int getElapsedTime() {
        return ((int) System.currentTimeMillis() / 1000) - m_issueTime;
    }
}