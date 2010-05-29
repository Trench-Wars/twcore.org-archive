package twcore.bots.hockeybot;

import java.util.ArrayList;

public class GameRequest<HockeyTeam> {

    private ArrayList<HockeyTeam> requestingTeams;
    
    public GameRequest(){
        requestingTeams = new ArrayList<HockeyTeam>();
    }
    
    public void doRequest(HockeyTeam requestingTeamName){
        requestingTeams.add(requestingTeamName);
    }
    
    public void doRemoveRequest(HockeyTeam teamName){
        requestingTeams.remove(teamName);
    }
    
    public HockeyTeam getTeam(String name){
        return null;
    }
}
