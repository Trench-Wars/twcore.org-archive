package twcore.bots.hockeybot.hockeymediator;

import twcore.bots.hockeybot.hockeyteam.HockeyPlayer;

public interface HockeyMediator {
    
    public void setState(int state);
    public void setTeamReady();
    public void updatePlayerPoint();
    public int getCurrentState();
    public void addPlayer(String name, int ship, int freq);
    public void addPlayerPoint(String namePlayer, int freq, int numberPoint);
   
    /*public void giveSavePoint(String name, int freq);
    public void giveGoalPoint(String name, int freq);
    public int doGetGoalPoints(String name);
    */
    
    public void doReadyTeam(String name, String message);
    public void notifyTime(long mins, long secs);
    public void startPractice(String name, String squadAccepted);
    
    
    
}
