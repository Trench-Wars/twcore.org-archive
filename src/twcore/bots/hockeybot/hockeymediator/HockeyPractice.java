package twcore.bots.hockeybot.hockeymediator;

import java.sql.SQLException;

import twcore.bots.hockeybot.hockeydatabase.HockeyDatabase;
import twcore.bots.hockeybot.hockeyregistrator.HockeyRegistrator;
import twcore.core.BotAction;
import twcore.core.game.Player;


public class HockeyPractice extends HockeyColleague{

    private HockeyDatabase database;
    private BotAction game_action;
    //private static HockeyPractice practice;
    
    //protected HockeyTeam hockeyTeams[];
   // private GameHandler gameTicker;
    
    public HockeyPractice(BotAction botAction){
       
        //state = new HockeyState();
    //    gameTicker = new GameHandler();
        game_action = botAction;
        try {
            database = new HockeyDatabase(game_action);
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }

    /**
     * Singleton
    public static HockeyPractice getInstance(BotAction botAction){
        if(practice == null)
            practice = new HockeyPractice(botAction);
        return practice;
    }*/
    
    protected void giveGoalPoint(Player hockeyPlayer){
        
    }
    
    public void doAcceptGame(String name, String squadAccepted){
        //!accept <game>
        //012345678
        
        String squadAccepter = null;
        squadAccepter = "Andre";//getSquadAccepterName(name);
        
        if(squadAccepter == null){
            game_action.sendPrivateMessage(name, "You are not assistant / captain of a squad.");
            //this.practice = null;
            return;
        }
        
        doPreStart(name, squadAccepted/*squadAccepted*/, squadAccepter);
        //GameRequest.squadRequests.remove(/*squadAccepted*/);
        
    }
    public void addPlayer(String name, String message) throws SQLException{
        //!register #
        //0123456789N
        Player p = game_action.getPlayer(name);
        String squadName = null;
        int ship;
        
        if(message.length() <= 10){
            game_action.sendPrivateMessage(name, "Please specify a ship to register: !register <ship>");
            return;
        }
        
        ship = Integer.parseInt(message.substring(10));
        
        if(ship >=9 || ship <=0){
            game_action.sendPrivateMessage(name, "Please register a ship between the numbers 1 - 8");
            return;
        }
        
        if(p == null){
            return;
        }
        
        squadName = "Bots";//getPlayerSquadName(name);
        
        if(squadName == null){
            game_action.sendPrivateMessage(name, "You're not in a squad. Please join one first.");
            return ;
        }
        
        int frequency = 1;//getFrequency(squadName);
        
        if(frequency != 1 || frequency != 0){
            game_action.sendPrivateMessage(name, "Your squad is not playing in this game, please wait your time: use !put <squad> to make a request");
            return;
        }
        
        //hockeyTeams[frequency].addPlayer(name, ship);

    }
    
    private String getPlayerSquadName(String name) throws SQLException{
        String squadName = new HockeyRegistrator(game_action).getPlayerTeamName(name);
        return squadName;
    }
    /*
    private int getFrequency(String squadName){
        if(hockeyTeams[0].teamName.equals(squadName))
            return 0;
        else if(hockeyTeams[1].teamName.equals(squadName))
            return 1;
        return 2;
    }
    */
     
    public void doPreStart(String name, String teamName1, String teamName2){
        
    
        //hockeyTeams = new HockeyTeam [2];
        //hockeyTeams[0] = new HockeyTeam(0, teamName1, game_action);
        //hockeyTeams[1] = new HockeyTeam(1, teamName2, game_action);
        
        //state.setState(HockeyState.PreStartPeriod);
        
        game_action.sendArenaMessage("Pre-Start: "+teamName1+" vs. "+teamName2, 2);
        game_action.sendArenaMessage("Rostered players can get in the game by !register <ship> now");
       
       // game_action.scheduleTask(new HockeyChronometer(this.game_action, 0), 100, 1000);
    }
    
    public void doCheckLineUp(){
      //  if(hockeyTeams[0].)
    }
    public void cancelGame() throws Throwable{
       /*this.practice = null;
       this.finalize();
    */}
    public String getSquadAccepterName(String nameCaptainAccepting){
        try {
            HockeyRegistrator registrator = new HockeyRegistrator(game_action);
            return registrator.getCaptainTeamName(nameCaptainAccepting);
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
        
    }
    
    /*protected class GameHandler extends TimerTask{
        public void run(){
            
            switch(state.getCurrentState()){
            
                case HockeyState.PreStartPeriod: doWaitingForPlayers();
                case HockeyState.Period_In_Progress: doGameInProgress();
                case HockeyState.FaceOff: doFaceOff();
                
            }
        }
        
        private void doFaceOff(){
           // HockeyChronometer.doPause();
        }
        
        private void doWaitingForPlayers(){
            
            if( System.currentTimeMillis() - state.getTimeStamp() >= Tools.TimeInMillis.MINUTE*5  )
                doCheckLineUp();
        }
        
        private void doGameInProgress(){
            
        }
    }  

    public class HockeyState {

        private int state;
        private long stateTimeStamp;
        
        protected static final int OFF = -1;
        
        protected static final int FaceOff = 0;
        
        protected static final int PreStartPeriod = 1;
        
        protected static final int Period_In_Progress = 2;

        protected static final int EndGame = 3;
        
        
        public HockeyState(){
            this.state = OFF;
        }
        
        protected void setState(int state){
            this.stateTimeStamp = System.currentTimeMillis();
            this.state = state;
        }
        
        protected int getCurrentState(){
            return state;
        }
        
        private long getTimeStamp(){
            return stateTimeStamp;
        }
    }*/
}
