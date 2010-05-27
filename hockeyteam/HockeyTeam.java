package twcore.bots.hockeybot.hockeyteam;

import java.util.TreeMap;

import twcore.core.BotAction;

public class HockeyTeam {

    private BotAction hockeyTeam_botAction;
    
    private     boolean     isReady;
    private     String      captainName;
    private     int         teamFreqNumber;
    private     int         teamGoals;
    private     String       teamName;
    
    private final int teamMaxPlayers = 6;
    private final int teamMinPlayers = 4;
    
    private TreeMap<String, HockeyPlayer> hockeyPlayers;
    
    public HockeyTeam(int frequence, String teamName, BotAction botAction){
        hockeyTeam_botAction = botAction;
        this.teamFreqNumber = frequence;
        this.hockeyPlayers = new TreeMap<String, HockeyPlayer>();
        this.setTeamName(teamName);
        this.isReady = false;
    }

    public void setCaptainName(String name){
        this.captainName = name;
    }
    
    public String getCaptainName(){
        return captainName;
    }
    public void addPlayer(String hockeyPlayerName, int shipType){

        //if(isFull())
          //  hockeyTeam_botAction.sendPrivateMessage(hockeyPlayerName, "This team has reach the max playing players (6) already. Please wait a bit.");
        
        //else{ 
            //Needs to check if he's registered already.
            hockeyPlayers.put(hockeyPlayerName, new HockeyPlayer(this, hockeyPlayerName, shipType, teamFreqNumber, hockeyTeam_botAction));
            
            hockeyTeam_botAction.sendPrivateMessage(hockeyPlayerName, "You got registered at "+shipType+" successfuly.");
            hockeyTeam_botAction.sendArenaMessage(hockeyPlayerName+" is in for "+teamName+" as ship #"+shipType);
        //}
    }
    /*
    public int getSavePoints(String name){
        HockeyPlayer hp = hockeyPlayers.get(name);
        return hp.getSavePoint();
    }
    
    public int getNumberSave(String name){
        HockeyPlayer hp = hockeyPlayers.get(name);
        return hp.getNumberSaves();
    }
    public void giveSavePoint(String name){
        if(Contains(name)){
            HockeyPlayer hPlayer = hockeyPlayers.get(name);
            hPlayer.addGoalPoint(2);
            hockeyPlayers.put(name, hPlayer);
        }
    }
    public void givePointGoal(String name){
        if(Contains(name)){
            HockeyPlayer hPlayer = hockeyPlayers.get(name);
            hPlayer.addGoalPoint( 5 );
            hockeyPlayers.put(name, hPlayer);
        }
    }
    
    public int getGoalPoints(String name){
        HockeyPlayer hp = hockeyPlayers.get(name);
        return hp.getGoalPoints();
    }
    public int getNPoint(String name){
        HockeyPlayer hp = hockeyPlayers.get(name);
        return hp.getNumberGoals();
    }*/
    
    public boolean Contains(String hockeyPlayerName){
        return hockeyPlayers.containsKey(hockeyPlayerName);
        
    }
    public int getTeamSize(){
        return hockeyPlayers.size();
    }
    
    public boolean isFull(){
        return hockeyPlayers.size() >= getTeamMaxPlayers();
    }
    public boolean isIn(String name){
        return hockeyPlayers.containsKey(name);
    }
    public int getTeamMaxPlayers() {
        // TODO Auto-generated method stub
        return this.teamMaxPlayers;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setReady(boolean isReady) {
        this.isReady = isReady;
    }

    public boolean isReady() {
        return isReady;
    }
    
    public int getTeamFreq(){
        return teamFreqNumber;
    }
    
}
