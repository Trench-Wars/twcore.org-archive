package twcore.bots.hockeybot.hockeydatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import twcore.core.BotAction;
import twcore.core.util.Tools;

//Data Access Object to Hockeybot's inheritance
public class HockeyDatabase {

    private BotAction m_botAction;
    
    private String connectionName = "website";
    private String uniqueId = "hz";
    
    
    //private PreparedStatement psGetTeamId;
    private PreparedStatement psGetCurrentSquads;
    private PreparedStatement psGetMatchId;
    private PreparedStatement psKeepAlive;
    
    public HockeyDatabase(BotAction botAction) throws SQLException{
        this.m_botAction = botAction;
     
        psGetCurrentSquads = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId, 
                "SELECT fsName from tblTWHT__Team");
        
        
       
        psGetMatchId = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId,  
                "SELECT fnTeam1ID, fnTeam2ID FROM tblTWHT__Match where fnMatchId = ?");
        
     
        
    }
    
    public boolean isTeam(String squadName){
        
        try{
            
            PreparedStatement psGetTeam;
            psGetTeam = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId, 
            "SELECT fnTWHTTeamId FROM tblTWHT__Team where fsName = ?");
    
            psGetTeam.setString(1, squadName);
            ResultSet rs = psGetTeam.executeQuery();
            
            if(rs.next()){
                psGetTeam.close();
                return true;
            }
        }catch(SQLException e){
            Tools.printLog(e.toString());
        }
        
        return false;
    }
    
    public int getPlayerUserId(String captainName){
        int userId = -1;
        
        try{
            
            PreparedStatement psGetUserId;
            psGetUserId = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId, "SELECT fnUserId FROM tblUser where fcUserName = ?");
            psGetUserId.setString(1, captainName);
            
            ResultSet rs = psGetUserId.executeQuery();
            
            if(rs.next() && rs != null)
                userId = rs.getInt(1);
            //setUserId();
            
            psGetUserId.close();
            
        }catch(SQLException e){
            Tools.printLog(e.getMessage());
        }
        
        return userId;
    } 
    /*public int getTeamUserId(String name){
        
        try{
            int playerId = getPlayerUserId(name);
            String query = "SELECT fnTeamUserId from tblTWHT__TeamUser where fnUserId = ?";
            PreparedStatement psGetTeamUserId = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId, query);
            
            psGetTeamUserId.setInt(1, playerId);
            
            ResultSet rs = psGetTeamUserId.executeQuery();
            
            while(rs.next()){
               int teamUserId = rs.getInt(1);
               return teamUserId;
            }
        }catch(SQLException e){
            Tools.printLog(e.toString());
        }
        return 0;
    }*/
    public String getPlayerTeamName(String playerName){
        String squadName = null;
        try{
            
            PreparedStatement psGetTeamName;
            
            String query = "SELECT DISTINCT t.fsName " +
                    "FROM tblTWHT__Team t, tblTWHT__TeamUser tu, tblUser u  " +
                    "WHERE t.fnTWHTTeamID = tu.fnTeamID " +
                    "AND tu.fnUserID = u.fnUserID "+
                    "AND tu.fdQuit IS NULL "+
                    "AND u.fcUserName = ?";
            psGetTeamName = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId, query);
            psGetTeamName.setString(1, playerName );
            
            ResultSet rs = psGetTeamName.executeQuery();
            while(rs.next()){
                squadName = rs.getString(1);
                return squadName;
            }
        }catch(SQLException e){
            Tools.printLog(e.toString());
        }
        return squadName;
    }
    public String getCaptainTeamName(String captainName){
        
        try{
         
            PreparedStatement psGetTeamName;
            
            String query = "SELECT DISTINCT t.fsName " +
            		"FROM tblTWHT__Team t, tblTWHT__TeamUser tu, tblUser u, tblTWHT__UserRank ur " +
            		"WHERE t.fnTWHTTeamID = tu.fnTeamID " +
            		"AND tu.fnUserID = u.fnUserID " +
            		"AND ur.fnUserID = tu.fnUserID " +
            		"AND (ur.fnRankID = 3 OR ur.fnRankID = 4) "+
            		"AND tu.fdQuit IS NULL "+
            		"AND u.fcUserName = ?";
            psGetTeamName = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId, query);
            psGetTeamName.setString(1, captainName);
            
            ResultSet rs = psGetTeamName.executeQuery();
            while(rs.next()){
                String squadName = rs.getString(1);
                return squadName;
            }
        }catch(SQLException e){
            Tools.printLog(e.toString());
        }
        
        return null;
    }
    public boolean getTeamUserIdIsRostered(int userId){
            try{
                
                PreparedStatement psGetTeamUserId;
                psGetTeamUserId = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId,  
                        "SELECT fnTeamUserId FROM tblTWHT__TeamUser where fnUserId = ? " +
                        "AND fdQuit IS NULL");
                
                psGetTeamUserId.setInt(1, userId);
                ResultSet rs = psGetTeamUserId.executeQuery();
                
                if(rs.next()){
                    psGetTeamUserId.close();
                    return true; //already rostered
                }
            }catch(SQLException e){
                Tools.printLog(e.toString());
            }
        return false;
    }

    public void getCurrentSquads(){
    
        try{
            ResultSet rs = psGetCurrentSquads.executeQuery();
            
            while(rs.next())
                m_botAction.sendArenaMessage("Current Squad on Database: "+rs.getString("fsName"));
            
        }catch(SQLException e){
            Tools.printLog(e.toString());
            }
    }

    public void putTeam(String name, String teamName){
            
        try{
            PreparedStatement psPutExtendedLogTeamSignup;
            
            psPutExtendedLogTeamSignup = 
                m_botAction.createPreparedStatement(this.connectionName, this.uniqueId, 
                    "INSERT INTO tblTWHT__Team ("+
                    "fsName, " +
                    "fnCaptainID, " +
                    "fdCreated, " +
                    "fdApproved) " +
                    "VALUES( ?,?,NOW(),NOW() )" );
            
            psPutExtendedLogTeamSignup.setString(1, teamName);
            psPutExtendedLogTeamSignup.setInt(2, getPlayerUserId(name));
            psPutExtendedLogTeamSignup.executeUpdate();
            m_botAction.sendPrivateMessage(name, "You've applied "+ teamName+" on the site successfuly! Just wait a TWH-Op to accept it.");
            
            psPutExtendedLogTeamSignup.close();
        }catch(SQLException e){
            Tools.printLog(e.getMessage());
        }
        
    }
    
    private void closePreparedStatements(){
        try{
            psGetCurrentSquads.close();
          
        }catch(SQLException e){
            Tools.printLog(e.toString());
            }
    }
    
    public void keepAlive(){
        try{
            psKeepAlive = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId,  "SHOW DATABASES");
            psKeepAlive.execute();
        }catch(SQLException e){
            Tools.printLog(e.toString());
        }
    }
}
