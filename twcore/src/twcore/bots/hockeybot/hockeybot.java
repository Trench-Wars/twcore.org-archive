package twcore.bots.hockeybot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.TimerTask;


import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.BallPosition;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerPosition;
import twcore.core.events.SoccerGoal;
import twcore.core.game.Player;
import twcore.core.util.Tools;

//goal_datetime - start_datetime = minutes in game
//ref decides:"clean/lag/phase/cr/bcr"
//"No goal: lag", No goal: shot inside crease", no goal: phase"
//ball intent..etc
//cr = goal shot in crease
//bcr = ball intentionally shot in crease
/**
 * @author: Dexter
 * Bot project to Hockey - !teamsignup ready to signup squads on the trenchwars.org/twht site
 * */

public class hockeybot
        extends SubspaceBot {

    
    private OperatorList op;
    private EventRequester events;
    
    private HZSQL hzsql;
    
    private HZTeam hzTeams[];
    
    private String team1 = "";
    private String team2 = "";
    //private HockeyGame game;
    //private HockeyRegistrator registrator;

    private String pubHelp [] = {
      "Hi, I'm a bot in development to the TW-Hockey-Tournament,",
      "you can register your squad already!",
      "| Commands --------------------------------------------------------",
      "| !teamsignup <squadName>    -  Registers your squad on TWHT's site",
      "| check TWHT'S Site: www.trenchwars.org/twht"
    };
    
    public void requestEvents(EventRequester eventRequester){
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.LOGGED_ON);
        eventRequester.request(EventRequester.BALL_POSITION);
        eventRequester.request(EventRequester.SOCCER_GOAL);
    }

    public hockeybot(BotAction botAction) throws SQLException {
        super(botAction);
        doStartBot();     
    
    }
    
    public void doStartBot() throws SQLException{
        this.events = m_botAction.getEventRequester();
        this.requestEvents(events);
        this.op = m_botAction.getOperatorList();
        this.hzsql = new HZSQL();
        this.hzTeams = new HZTeam[2];
        m_botAction.scheduleTask(new KeepAliveConnection(), 2* Tools.TimeInMillis.MINUTE, 5*Tools.TimeInMillis.MINUTE);
        // if(m_botAction.)
       // game = new HockeyGame();
       // registrator = new HockeyRegistrator();
     //   Period p = new Period();
     //   m_botAction.scheduleTask(p, 10000);
    }

    public void doLoadGame(String name, String message){
        //this.hzsql.
        //!load id
        //123456
        int matchId = Integer.parseInt( message.substring(6) );
        this.hzTeams[0] = new HZTeam(0);
        this.hzTeams[1] = new HZTeam(1);
        HZGame hzGame = new HZGame( hzTeams[0], hzTeams[1] );
    }

    @Override
    public void handleEvent(BallPosition event){
        //gets the carrier and keeps getting him in a loop..
        Player p;
        
        if(event.getCarrier() == -1)
            return;
        
        p = m_botAction.getPlayer(event.getPlayerID());
        
    }
    
    @Override
    public void handleEvent(SoccerGoal event){
       //gets the frequence number of teams goal
        m_botAction.sendArenaMessage("woo Freq "+
                event.getFrequency()+"!");
        
        
    }
    
    public void handleEvent(PlayerPosition event){
       
    }
    @Override
    public void handleEvent(LoggedOn event){
        BotSettings botSettings = m_botAction.getBotSettings();
        String initialSpawn = botSettings.getString("InitialArena");
        m_botAction.joinArena(initialSpawn);
    }
    
    @Override
    public void handleEvent(Message event){
        
        String name = m_botAction.getPlayerName( event.getPlayerID() );
        String message = event.getMessage();
        int messageType = event.getMessageType();
        
        if( messageType == Message.PRIVATE_MESSAGE){
            if(name == null)
                return;
            
            handleCommand(name, message);
        }
        
    }
    
    private void handleCommand(String name, String message) {
        
        if(message.startsWith("!help"))
            m_botAction.privateMessageSpam(name, this.pubHelp);
        
        if(message.startsWith("!loadgame"))
            doLoadGame(name, message);
        else if(message.startsWith("!teamsignup"))
            doCreateTeam(name, message);
        //else if(message.startsWith("!squads"))
          //  doDisplaySquads(name, message);
    }

    public void doCreateTeam(String name, String message){
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
            m_botAction.sendPrivateMessage(squadName, squadName+" has been registered on the site already. Please try an other one.");
            return;
        }
        
        signupSquad(name, squadName);
        
  }
    
    public boolean isAlreadyRegistered(String squadName){
            if(hzsql.isTeam(squadName))
                return true;
            
        return false;
    }
    
    public void signupSquad(String name, String squadName){
        hzsql.putTeam( name, squadName);
    }

    public boolean isRostered(String name){
       
        int userId = hzsql.getCaptainUserId(name);
        
        if(hzsql.getTeamUserIdIsRostered(userId))
            return true;
      
        return false;
    }

    public void doDisplaySquads(String name, String message){
        hzsql.getCurrentSquads();
    }
    
    //data access object - DAO
    private class HZSQL{
        
        private String connectionName = "website";
        private String uniqueId = "hz";
        private PreparedStatement psGetUserId;
        //private PreparedStatement psGetTeamId;
        private PreparedStatement psPutExtendedLogTeamSignup;
        private PreparedStatement psGetCurrentSquads;
        private PreparedStatement psGetMatchId;
        private PreparedStatement psGetTeam;
        private PreparedStatement psGetTeamUserId;
        private PreparedStatement psKeepAlive;
        
        private HZSQL() throws SQLException{
            psGetUserId = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId, "SELECT fnUserId FROM tblUser where fcUserName = ?");
        
            psPutExtendedLogTeamSignup = 
                m_botAction.createPreparedStatement(this.connectionName, this.uniqueId, 
                    "INSERT INTO tblTWHT__Team ("+
                    "fsName, " +
                    "fnCaptainID, " +
                    "fdCreated, " +
                    "fdApproved) " +
                    "VALUES( ?,?,NOW(),NOW() )" );
            
            psGetCurrentSquads = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId, 
                    "SELECT fsName from tblTWHT__Team");
            
            
            psGetTeam = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId, 
                    "SELECT fnTWHTTeamId FROM tblTWHT__Team where fsName = ?");
            
            psGetMatchId = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId,  
                    "SELECT fnTeam1ID, fnTeam2ID FROM tblTWHT__Match where fnMatchId = ?");
            
            psGetTeamUserId = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId,  
                    "SELECT fnTeamUserId FROM tblTWHT__TeamUser where fnUserId = ? " +
            		"AND fdQuit IS NULL");
            
            psKeepAlive = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId,  "SHOW DATABASES");
        }
        
        private boolean isTeam(String squadName){
            
            try{
                psGetTeam.setString(1, squadName);
                ResultSet rs = psGetTeam.executeQuery();
                
                if(rs.next())
                    return true;
            
            }catch(SQLException e){
                Tools.printLog(e.toString());
            }
            
            return false;
        }
        
        private int getCaptainUserId(String captainName){
            int userId = -1;
            
            try{
                psGetUserId.setString(1, captainName);
                ResultSet rs = psGetUserId.executeQuery();
                
                if(rs.next() && rs != null)
                    userId = rs.getInt(1);
                //setUserId();
                
            }catch(SQLException e){
                Tools.printLog(e.getMessage());
            }
            
            return userId;
        } 

        private boolean getTeamUserIdIsRostered(int userId){
                try{
                    psGetTeamUserId.setInt(1, userId);
                    ResultSet rs = psGetTeamUserId.executeQuery();
                    
                    if(rs.next())
                        return true; //already rostered
                    
                }catch(SQLException e){
                    Tools.printLog(e.toString());
                }
            return false;
        }

        private void getCurrentSquads(){
        
            try{
                ResultSet rs = psGetCurrentSquads.executeQuery();
                
                while(rs.next())
                    m_botAction.sendArenaMessage("Current Squad on Database: "+rs.getString("fsName"));
                
            }catch(SQLException e){
                Tools.printLog(e.toString());
                }
        }

        private void putTeam(String name, String teamName){
                
            try{
            
                psPutExtendedLogTeamSignup.setString(1, teamName);
                psPutExtendedLogTeamSignup.setInt(2, getCaptainUserId(name));
                psPutExtendedLogTeamSignup.executeUpdate();
                m_botAction.sendPrivateMessage(name, "You've applied "+ teamName+" on the site successfuly! Just wait a TWH-Op to accept it.");
                
                
            }catch(SQLException e){
                Tools.printLog(e.getMessage());
            }
            
        }
        
        private void closePreparedStatements(){
            try{
                psGetCurrentSquads.close();
                psGetUserId.close();
                psPutExtendedLogTeamSignup.close();
                
            }catch(SQLException e){
                Tools.printLog(e.toString());
                }
        }
        
        private void keepAlive(){
            try{
                psKeepAlive.execute();
            }catch(SQLException e){
                Tools.printLog(e.toString());
            }
        }
    }
    
    //Keeps alive the connection
    private class KeepAliveConnection extends TimerTask{
        public void run(){
            hzsql.keepAlive();
        }
    }
    
    private class HZGame{
        
        public HZGame(HZTeam team1, HZTeam t2){
            
        }
    }
    private class HZTeam{
        
        private String T_teamName;
        private int T_frequency;
        private int T_points;
        private int T_goals;
        private int T_teamSizeMin;
        private int I_teamSizeMax;
        private boolean isReady;
        
        
        public HZTeam(int frequency){
            T_frequency = frequency;
        }
        public void setT_teamName(String t_teamName) {
            T_teamName = t_teamName;
        }
        public String getT_teamName() {
            return T_teamName;
        }
        public void setT_frequency(int t_frequency) {
            T_frequency = t_frequency;
        }
        public int getT_frequency() {
            return T_frequency;
        }
        public void setT_points(int t_points) {
            T_points = t_points;
        }
        public int getT_points() {
            return T_points;
        }
        public void setT_goals(int t_goals) {
            T_goals = t_goals;
        }
        public int getT_goals() {
            return T_goals;
        }
        public void setT_teamSizeMin(int t_teamSizeMin) {
            T_teamSizeMin = t_teamSizeMin;
        }
        public int getT_teamSizeMin() {
            return T_teamSizeMin;
        }
        public void setI_teamSizeMax(int i_teamSizeMax) {
            I_teamSizeMax = i_teamSizeMax;
        }
        public int getI_teamSizeMax() {
            return I_teamSizeMax;
        }
        public void setReady(boolean isReady) {
            this.isReady = isReady;
        }
        public boolean isReady() {
            return isReady;
        }
        
    }
    private class Period extends TimerTask{
        
        int state;
        
        public Period(){
            state = 0;
            m_botAction.sendArenaMessage("Pre-game is starting in 10 seconds. Get ready to play!");
        }
        
        private void setState(int state){
            this.state = state;
        }
        
        private int getState(){
            return state;
        }
        
        public void run(){
            //if( game.checkFullTeam() && game.checkReady() && getState() == 0 )
            setState(getState()+1);
        }
    }

}
