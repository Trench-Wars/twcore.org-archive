package twcore.bots.hockeybot.hockeyteam;

import java.util.ArrayList;
import java.util.TreeMap;

import twcore.core.BotAction;

public class HockeyTeam {

    private BotAction hockeyTeam_botAction;
    
    private     boolean     isReady;
    private     String      captainName;
    private     int         teamFreqNumber;
    private     int         teamGoals;
    private     String      teamName;
    
    private final int teamMaxPlayers = 6;
    private final int teamMinPlayers = 4;
    
    private TreeMap<String, HockeyPlayer> hockeyPlayers;
    private ArrayList<String> waitingList;
    
    public HockeyTeam(int frequence, String teamName, BotAction botAction){
        hockeyTeam_botAction = botAction;
        this.teamFreqNumber = frequence;
        this.hockeyPlayers = new TreeMap<String, HockeyPlayer>();
        this.setTeamName(teamName);
        this.isReady = false;
        waitingList = new ArrayList<String>();
        this.setTeamGoals(0);
    }
    
    public void addWaitingTeam(String waitingTeamName){
        waitingList.add(waitingTeamName);
    }
    
    public void removeWaitingTeam(String waitingTeamName){
        waitingList.remove(waitingTeamName);
    }
    
    public HockeyPlayer getPlayerInstance(String name){
    	return hockeyPlayers.get(name);
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
            //hockeyTeam_botAction.sendArenaMessage(hockeyPlayerName+" is in for "+teamName+" as ship #"+shipType);
        //}
    }
    
   
    public void addSavePoint(String namePlayer){
    	HockeyPlayer p = this.hockeyPlayers.get(namePlayer);
    	p.addSavePoint();
    	hockeyPlayers.put(namePlayer, p);
    }
    
    public void addGoalPoint(String namePlayer){
    	HockeyPlayer p = this.hockeyPlayers.get(namePlayer);
    	p.addGoalPoint();
    	hockeyPlayers.put(namePlayer, p);
    	
    }
    
    public void addAssistPoint(String namePlayer){
    	HockeyPlayer p = this.hockeyPlayers.get(namePlayer);
    	p.addAssistPoint();
    	hockeyPlayers.put(namePlayer, p);
    }
    
    public void displayResult(){
        //String s[] = new String[6];
        //int i = 0;
        if(this.Contains("Dexter"))
            System.out.println("GOALS: "+hockeyPlayers.get("Dexter").getNumberOfGoals() + " "+hockeyPlayers.get("Dexter").getPoint()+ " Points");
        /*for(HockeyPlayer p: hockeyPlayers.values())
        {   
            s[i] = "Number of Goals: "+ p.getGoalPoint();
            i++;
        }*/
        
    }
    
    public void displayStatistics(){
    	ArrayList<String> display = new ArrayList<String>();
    	display.add("--------------------------------------------------------------------");
    	for (HockeyPlayer p : hockeyPlayers.values()) {
			display.add(p.getName());
			display.add(Integer.toString(p.getNumberOfGoals()));
			display.add(Integer.toString(p.getNumberOfAssists()));
			display.add(Integer.toString(p.getNumbersOfSaves()));
			display.add(Double.toString(p.getPoint()));
		}
    	display.add("--------------------------------------------------------------------");
    	for (String string : display) {
			System.out.println(string);
		}
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
        return this.teamMaxPlayers;
    }
    
    public int getTeamMinPlayers(){
    	return this.teamMinPlayers;
    }
    
    public boolean hasMin(){
    	return hockeyPlayers.size() >= getTeamMinPlayers();
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

	public void setTeamGoals(int teamGoals) {
		this.teamGoals = teamGoals;
	}

	public int getTeamGoals() {
		return teamGoals;
	}
    
}
