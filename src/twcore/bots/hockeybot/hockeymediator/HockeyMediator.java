package twcore.bots.hockeybot.hockeymediator;

import java.sql.SQLException;


public interface HockeyMediator {
    
    public void setState(int state);
   
    public int getCurrentState();
    public void addPlayer(String name, int ship) throws SQLException;
    public void addPlayerPoint(String namePlayer, int freq, int numberPoint);

    
    public void readyTeam(String name, String message) throws SQLException;
    public void notifyTime(long mins, long secs);
    public void startGame(int idTeamAccepter, int idTeamAccepted, String captainTeamAccepter, String requester, String teamAccepter, String squadAccepted, String chatTeamAccepter, String chatTeamAccepted) throws SQLException;
    public void updateScore(int freq); 
    public void displayStatistics();

    /**
     * news
     * @throws SQLException 
     * */
    
    public void acceptGame(String captainAccepter, String teamAccepted) throws SQLException;
    
}
