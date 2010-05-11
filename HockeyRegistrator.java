package twcore.bots.hockeybot;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.util.Tools;

public class HockeyRegistrator extends hockeybot{

    private HockeyDatabase hockeyDatabase;
    
    private final int second = 1000;
    private final int minutes = 60*second;
    
    public HockeyRegistrator(BotAction botAction) throws SQLException {
        super(botAction);
        this.hockeyDatabase = new HockeyDatabase(botAction);
      
       
    }
      
    public void putPlayer(String name, String message){
        
    }
    
    @Override
    public void createTeam(String name, String message){
        //!teamsignup squadname
        //0123456789TE
        //123456789DOD
        if(message.length() <= 12 ){
            m_botAction.sendPrivateMessage(name, "Please, use the command !teamsignup <squadName> to register a squad into TWHT.");
            return;
        }
        if(isRostered(name)){
            m_botAction.sendPrivateMessage(name, "You're in a squad already. Leave this one first please.");
            return;
        }
        
        String squadName = message.substring(12);
        
        if(isAlreadyRegistered(squadName)){
            m_botAction.sendPrivateMessage(name, squadName+" has been registered on the site already. Please try an other one.");
            return;
        }
        
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
    
    public void talk(String name){
        m_botAction.sendPrivateMessage(name, "HockeyRegistrator talking...");
    }
    
    @Override
    public void requestGame(String name, String message){
        
        String squadName = getCaptainTeamName(name);
        
        if(squadName == null){
            m_botAction.sendPrivateMessage(name, "You're not rostered in a squad.");
            return;
        }
        
        m_botAction.sendPrivateMessage(name, squadName+" added into the request list. Wait for other captain to accept it.");
        
       GameRequest.addRequest(squadName);
        
    }
 
    
    public static class GameRequest{
        
        public static LinkedList<String> squadRequests = new LinkedList<String>();
        
        public static void addRequest(String squadName){
            squadRequests.addLast(squadName);
        }
        
        public static String deque(){
            if(!squadRequests.isEmpty()){
                return squadRequests.pollFirst(); 
            }
            return null;
        }
        
        public static void removeRequest(String squadName){
            squadRequests.remove(squadName);
        }
    }

}
