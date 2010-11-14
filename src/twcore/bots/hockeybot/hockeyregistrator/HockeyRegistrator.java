package twcore.bots.hockeybot.hockeyregistrator;

import java.sql.SQLException;
import java.util.LinkedList;

import twcore.bots.hockeybot.hockeybot;
import twcore.bots.hockeybot.hockeydatabase.HockeyDatabase;
import twcore.core.BotAction;

public class HockeyRegistrator {

    private HockeyDatabase hockeyDatabase;
    
    private final int second = 1000;
    private final int minutes = 60*second;
    
    private BotAction m_botAction;
    
    public HockeyRegistrator(BotAction botAction) throws SQLException {
        this.hockeyDatabase = new HockeyDatabase(botAction);
        m_botAction = botAction;
       
    }
     
    public void createTeam(String name, String squadName){
      
        signupSquad(name, squadName);
    }
    
    public boolean isAlreadyRegistered(String squadName){
            if(hockeyDatabase.isTeam(squadName))
                return true;
            
        return false;
    }
    
    public void signupSquad(String name, String squadName){
        hockeyDatabase.putTeam( name, squadName);
    }

    public boolean isRostered(String name){
       
        int userId = hockeyDatabase.getPlayerUserId(name);
        
        if(hockeyDatabase.getTeamUserIdIsRostered(userId))
            return true;
      
        return false;
    }

    public String getCaptainTeamName(String captainName){
        return hockeyDatabase.getCaptainTeamName(captainName);
    }
    
    public String getPlayerTeamName(String playerName){
        return hockeyDatabase.getPlayerTeamName(playerName);
    }
    
    public void doDisplaySquads(String name, String message){
        hockeyDatabase.getCurrentSquads();
    }

    public void forceDB(String name) {
        hockeyDatabase.doForceDBConnection(name);
        
    }
    
}
