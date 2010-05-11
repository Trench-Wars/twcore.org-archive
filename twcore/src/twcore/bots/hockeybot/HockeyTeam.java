package twcore.bots.hockeybot;

import java.util.TreeMap;

import twcore.core.BotAction;

public class HockeyTeam {

    private BotAction hockeyTeam_botAction;
    
    protected int teamFreqNumber;
    protected int teamGoals;
    protected String teamName;
    
    private final int teamMaxPlayers = 6;
    private final int teamMinPlayers = 4;
    
    private TreeMap<String, HockeyPlayer> hockeyPlayers;
    //private HockeyPlayer hockeyPlayers[];
    
    
    public HockeyTeam(int frequence, String teamName, BotAction botAction){
        hockeyTeam_botAction = botAction;
        this.teamFreqNumber = frequence;
        this.hockeyPlayers = new TreeMap<String, HockeyPlayer>();
        this.setTeamName(teamName);
        
    }

    public void addPlayer(String hockeyPlayerName, int shipType){

        if(hockeyPlayers.size() >= getTeamMaxPlayers()){
            this.hockeyTeam_botAction.sendPrivateMessage(hockeyPlayerName, "This team has reach the max playing players (6) already. Please wait a bit.");
            return;
        }
        
        if(hockeyPlayers.containsKey(hockeyPlayerName)){
            this.hockeyTeam_botAction.sendPrivateMessage(hockeyPlayerName, "You're already in, wtf are you doing?");
            return;
        }
        
        hockeyPlayers.put(hockeyPlayerName, new HockeyPlayer(hockeyPlayerName, shipType, teamFreqNumber, hockeyTeam_botAction));
        
        hockeyTeam_botAction.sendPrivateMessage(hockeyPlayerName, "You got registered at "+shipType+" successfuly.");
        hockeyTeam_botAction.sendArenaMessage(hockeyPlayerName+" is in for "+teamName+" as ship #"+shipType);
    }
    
    private int getTeamMaxPlayers() {
        // TODO Auto-generated method stub
        return this.teamMaxPlayers;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamName() {
        return teamName;
    }
    
}
