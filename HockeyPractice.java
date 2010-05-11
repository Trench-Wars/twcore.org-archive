package twcore.bots.hockeybot;

import java.sql.SQLException;
import java.util.TimerTask;

import twcore.bots.hockeybot.HockeyRegistrator.GameRequest;
import twcore.core.BotAction;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class HockeyPractice extends hockeybot{

    private HockeyDatabase database;
    
    protected HockeyTeam hockeyTeams[];
    protected static HockeyState state;
    
    private GameHandler gameTicker;
    
    public HockeyPractice(BotAction botAction) throws SQLException{
        super(botAction);
        state = new HockeyState();
        gameTicker = new GameHandler();
        database = new HockeyDatabase(botAction);
    }
    
    
    @Override
    public void doAcceptGame(String name, String message){
        //!accept <game>
        //012345678
        String squadAccepted = message.substring(8);
        String squadAccepter = null;
        
        if(!GameRequest.squadRequests.contains(squadAccepted)){
            m_botAction.sendPrivateMessage(name, "The squad "+squadAccepted+" is not on the list, try to accept other one: look !challenges");
            return;
        }
        
        squadAccepter = getSquadAccepterName(name);
        
        if(squadAccepter == null){
            m_botAction.sendPrivateMessage(name, "You are not assistant / captain of a squad.");
            this.practice = null;
            return;
        }
        
        doPreStart(name, squadAccepted, squadAccepter);
        GameRequest.squadRequests.remove(squadAccepted);
        
    }
    
    @Override
    public void addPlayer(String name, String message) throws SQLException{
        //!register #
        //0123456789N
        Player p = m_botAction.getPlayer(name);
        String squadName = null;
        int ship;
        
        if(message.length() <= 10){
            m_botAction.sendPrivateMessage(name, "Please specify a ship to register: !register <ship>");
            return;
        }
        
        ship = Integer.parseInt(message.substring(10));
        
        if(ship >=9 || ship <=0){
            m_botAction.sendPrivateMessage(name, "Please register a ship between the numbers 1 - 8");
            return;
        }
        
        if(p == null){
            return;
        }
        
        squadName = getPlayerSquadName(name);
        
        if(squadName == null){
            m_botAction.sendPrivateMessage(name, "You're not in a squad. Please join one first.");
            return ;
        }
        
        int frequency = getFrequency(squadName);
        
        if(frequency != 1 || frequency != 0){
            m_botAction.sendPrivateMessage(name, "Your squad is not playing in this game, please wait your time: use !put <squad> to make a request");
            return;
        }
        
        hockeyTeams[frequency].addPlayer(name, ship);

    }
    
    private String getPlayerSquadName(String name) throws SQLException{
        String squadName = new HockeyRegistrator(m_botAction).getPlayerTeamName(name);
        return squadName;
    }
    
    private int getFrequency(String squadName){
        if(hockeyTeams[0].teamName.equals(squadName))
            return 0;
        else if(hockeyTeams[1].teamName.equals(squadName))
            return 1;
        return 2;
    }
    
    @Override
    public void doPreStart(String name, String teamName1, String teamName2){
        
        m_botAction.sendPrivateMessage(name, "Prac started");
        
        hockeyTeams = new HockeyTeam [2];
        hockeyTeams[0] = new HockeyTeam(0, teamName1, m_botAction);
        hockeyTeams[1] = new HockeyTeam(1, teamName2, m_botAction);
        
        state.setState(HockeyState.PreStartPeriod);
        
        m_botAction.sendArenaMessage("Pre-Start: "+teamName1+" vs. "+teamName2, 2);
        m_botAction.sendArenaMessage("Rostered players can get in the game by !register <ship> now");
       
        m_botAction.scheduleTask(new HockeyChronometer(this.m_botAction, 0), 100, 1000);
    }
    
    public void doCheckLineUp(){
      //  if(hockeyTeams[0].)
    }
    
    public String getSquadAccepterName(String nameCaptainAccepting){
        try {
            HockeyRegistrator registrator = new HockeyRegistrator(m_botAction);
            return registrator.getCaptainTeamName(nameCaptainAccepting);
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
        
    }
    
   
    protected class GameHandler extends TimerTask{
        public void run(){
            
            switch(state.getCurrentState()){
            
                case HockeyState.PreStartPeriod: doWaitingForPlayers();
                case HockeyState.Period_In_Progress: doGameInProgress();
                case HockeyState.FaceOff: doFaceOff();
                
            }
        }
        
        private void doFaceOff(){
            HockeyChronometer.doPause();
        }
        
        private void doWaitingForPlayers(){
            
            if( System.currentTimeMillis() - state.getTimeStamp() >= Tools.TimeInMillis.MINUTE*5  )
                doCheckLineUp();
        }
        
        private void doGameInProgress(){
            
        }
    }
    protected static class HockeyChronometer extends TimerTask
    {
        private BotAction m_botAction;
        
        /**
         * looks up for each second*/
        private long secs;
        
        /**
         * looks up for each minute
         * */
        private long mins;
        
        /**
         * to Pause the game
         * */
        private static boolean isPaused = false;
        
        /**
         * Constructor to handle the timer, if ER wants to start it by a custom timer
         * 
         * */
        public HockeyChronometer(BotAction botAction, long time){
            m_botAction = botAction;
            if(time == 0){
                secs = 0;
                mins = 0;
            }
            else{
                if(time > 60){
                    mins = time / 60;
                    secs = time % 60;
                }
            }
        }
        
        /**
         * Pauses the current game
         * */
        public static void doPause(){
            
            isPaused = true;
            
        }
        
        /**
         * Starts back and run the clock from where it stopped
         * */
        public static void doStartBack(){
        
            isPaused = false;
        }
        
        public void period(){
            
        }
        
        /**
         * Timer running after the schedule set*/
        public void run(){
            
            if(!isPaused){
                secs++;
                
                if(secs == 59){
                    mins++;
                    secs = 0;
                }
                m_botAction.sendArenaMessage("Current Time: "+mins+":"+secs+" mins");
                
                if(mins == 10){
                    m_botAction.sendArenaMessage("Period 2 will start soon, get ready!");
                }
                
            }
        }
        
    }
    

    protected class HockeyState {

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
    }
}
