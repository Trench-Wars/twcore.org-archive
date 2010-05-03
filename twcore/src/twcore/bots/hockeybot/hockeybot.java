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
    
    private ArrayList<SquadOwner> squadOwners;
    
    private HZTeam hzTeams[];
    
    private String team1 = "";
    private String team2 = "";
    //private HockeyGame game;
    //private HockeyRegistrator registrator;

    private String pubHelp [] = {
      "Hi, I'm a bot in development to the TW-Hockey-Tournament,",
      "you can register your squad already!",
      "| Commands --------------------------------------------------",
      "| !teamsignup      -       Registers your squad on TWHT's site"
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
        this.squadOwners = new ArrayList<SquadOwner>();
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
        hzsql.getMatch(matchId);//ive stopped here!
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
        
        if( messageType == Message.ARENA_MESSAGE){
            
            if (event.getMessage().startsWith("Owner is ")){ 
            
                String squadOwnerName = event.getMessage().substring(9);
                this.checkArenaMessageOwnerIs(name, squadOwnerName);
            
            }
        }
        
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
        else if(message.equals("!teamsignup"))
            doCreateTeam(name, message);
        //else if(message.startsWith("!squads"))
          //  doDisplaySquads(name, message);
    }

    public void doCreateTeam(String name, String message){
        
        Player player = m_botAction.getPlayer(name);
        
        if(player.getSquadName().equals(""))
        {
            m_botAction.sendPrivateMessage(name, "You're not in a squad. Use ?squadcreate=squadname:password to create one,");
            m_botAction.sendPrivateMessage(name, "then come back and !teamsignup please!");
            
        }
        
        else if(isNotRostered(name))
            CheckSquadOwner(name, player.getPlayerID(),  player.getSquadName());
        
        else
            m_botAction.sendPrivateMessage(name, "You're in a squad already. Leave this one first or contact a TWHT-Op / or the coders.");
    }
    
    public void CheckSquadOwner(String name, int playerId, String squadName){
        
        squadOwners.add(
                new SquadOwner(
                        name,
                        squadName,
                        playerId ) );
        
        m_botAction.sendUnfilteredPublicMessage("?squadowner " +squadName);

    }
    
    
    public void checkArenaMessageOwnerIs(String name, String squadOwnerName){
    
         for(SquadOwner t : squadOwners){
             /*
              * method used in case of lots of people !teamsignup 'ing
             it'll catch the right capt to the right squad ( since lots are doing !teamsignup and bot is doing lots of ?squadowners
            */
            if (t.getOwner().equalsIgnoreCase(squadOwnerName)){
                hzsql.putTeamSignup( t.getOwner(), t.getSquad());
                break;
            }
            else 
                m_botAction.sendSmartPrivateMessage(t.getOwner(), "You are not the owner of the squad " + t.getSquad());
            
        }
    }

    public boolean isNotRostered(String name){
       
        int userId = hzsql.getCaptainUserId(name);
        
        if(hzsql.getTeamUserIdIsNotRostered(userId))
            return true;
            /*for(SquadOwner i:squadOwners)
            if( i.getOwner().equals(name) )
                return true;
        *///checks if he has already created a nickname (list that bot keeps while online..) temporarily method / should be worked with query-db
        return false;
    }

    public void doDisplaySquads(String name, String message){
        hzsql.getCurrentSquads();
    }
    
    private class SquadOwner{
        
        private String owner = "";
        private String squad = "";
        private int id;
        
        public SquadOwner(String owner, String squad, int id){
            setOwner(owner);
            setSquad(squad);
            setId(id);
        }
        public String getSquad() {
            return squad;
        }
        public void setSquad(String squad) {
            this.squad = squad;
        }
        public void setOwner(String owner) {
            this.owner = owner;
        }
        public String getOwner() {
            return owner;
        }
       
        public void setId(int id) {
            this.id = id;
        }
        public int getId() {
            return id;
        }
        
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
        private PreparedStatement psGetTeamName;
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
            
            
            psGetTeamName = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId, 
                    "SELECT fsName FROM tblTWHT__Match where fnTWHTTeamId = ?");
            
            psGetMatchId = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId,  
                    "SELECT fnTeam1ID, fnTeam2ID FROM tblTWHT__Match where fnMatchId = ?");
            
            psGetTeamUserId = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId,  
                    "SELECT fnTeamUserId FROM tblTWHT__TeamUser where fnUserId = ? " +
            		"AND fdQuit IS NULL");
            
            psKeepAlive = m_botAction.createPreparedStatement(this.connectionName, this.uniqueId,  "SHOW DATABASES");
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
       /* private int getTeamId(String squadName){
            try{
                
            }catch(SQLException e){
                Tools.printLog(e.toString());
                }
            return 0;
            this should be used to don't allow a capt teamsignup a 2nd squad.
        }*/
        
        private String getTeamName(int teamId){
            
            String teamName = "";
            
            try{
                
                psGetTeamName.setInt(1, teamId);
                ResultSet rs = psGetTeamName.executeQuery();
                
                while(rs.next())
                    teamName = rs.getString(1);
                
                
            }catch(SQLException e){
                Tools.printLog(e.toString());
            }
            return teamName;
        }
        private boolean getTeamUserIdIsNotRostered(int userId){
                try{
                    psGetTeamUserId.setInt(1, userId);
                    ResultSet rs = psGetTeamUserId.executeQuery();
                    
                    if(rs.next())
                        return false; //already rostered
                    
                }catch(SQLException e){
                    Tools.printLog(e.toString());
                }
            return true;
        }
        private void getMatch(int matchId){
            try{
                psGetMatchId.setInt(1, matchId);
                ResultSet rs = psGetMatchId.executeQuery();
                
                if(rs.next()){
                    
                    team1 = getTeamName(rs.getInt(1));
                    team2 = getTeamName(rs.getInt(2));   
                }
                
            }catch(SQLException e){
                Tools.printLog(e.toString());
            }
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

        private void putTeamSignup(String name, String teamName){
                
            try{
            
                psPutExtendedLogTeamSignup.setString(1, teamName);
                psPutExtendedLogTeamSignup.setInt(2, getCaptainUserId(name));
                psPutExtendedLogTeamSignup.executeUpdate();
                m_botAction.sendPrivateMessage(name, "Your new squad "+ teamName+" got signed up successfuly!");
                
                
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
