package twcore.bots.hockeybot.hockeymediator;

import java.util.ArrayList;
import java.util.TimerTask;

import twcore.bots.hockeybot.hockeyteam.HockeyTeam;
import twcore.core.BotAction;
import twcore.core.util.Tools;


/**
 * @author Dexter
 *
 * Hockey - Mediator design pattern
 * 
 * hockey concrete mediator is the heart of the game. 
 * 
 * It communicates to all the other classes: teams, practice, clock, states
 * 
 * ERRO: faltou break; nos switch pra adicionar pontos
 * */    
public class HockeyConcreteMediator implements HockeyMediator {

    private     HockeyState     stateController;
    private     HockeyPractice  practice;
    private     HockeyTicker    ticker;
    private     HockeyTeam      team1;
    private     HockeyTeam      team0;
    private     HockeyTeam      teams[];
    
    
    private BotAction m_botAction;

    private  TimerTask getBack = new TimerTask(){
        public void run(){
            doStartBack();
        }
    };
    
    public HockeyConcreteMediator(BotAction botAction){
       
        this.m_botAction = botAction;

        stateController = new HockeyState();
        stateController.setState(HockeyState.OFF);
        stateController.setMediator(this);
    }
 
    public void startPractice(String name, String squadAccepted){
        
        practice = new HockeyPractice(m_botAction);
        practice.setMediator(this);
        practice.doAcceptGame(name, squadAccepted);
        
        stateController.setState(HockeyState.Pre_Start);
        
        ticker = new HockeyTicker();
        ticker.setMediator(this);
        ticker.doStart(0);
        m_botAction.scheduleTask( ticker , 100, Tools.TimeInMillis.SECOND);
        
        /**
         * falta Selecionar a squad por uma query
         * */
        team1 = new HockeyTeam(1, "Bots", m_botAction);
        team0 = new HockeyTeam(0, "Andre", m_botAction);
        teams = new HockeyTeam[2]  ;
        teams[0] = team0;
        teams[1] = team1;
        
    }
    public boolean gameIsRunning(){
        return stateController.getCurrentState() != HockeyState.In_Interval && stateController.getCurrentState() != HockeyState.OFF && stateController.getCurrentState() != HockeyState.Face_Off;
    }
    public void checkTeamReady(){
        //if(getteam().getTeamSize(1)) ;
    }
    
    public void doReadyTeam(String name, String message){
        
    }
    public boolean isReady(int frequence){
        return teams[frequence].isReady();
    }
    
    public void cancelGame() throws Throwable{
   
        m_botAction.cancelTask(getTicker());
        m_botAction.cancelTasks();
        
    }
    
    @Override
    public void setState(int state) {
        // TODO Auto-generated method stub
        if(state == HockeyState.Game_In_Progress){
            
            if(isReady(0) && isReady(1) || ( teams[0].hasMin() && teams[1].hasMin() ) ){
                stateController.setState(state);
                m_botAction.sendArenaMessage("State set to "+state);
            }
            
            else{
                try {
                    cancelGame();
                    stateController.setState(HockeyState.OFF);
                    m_botAction.sendArenaMessage("Not enough players to start");
                } catch (Throwable e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            //team1.setReady(true);
            //team2.setReady(true);
        }
        
        else if(state == HockeyState.In_Interval){
            stateController.setState(state);
            m_botAction.scheduleTask(getBack, Tools.TimeInMillis.MINUTE*2);
            m_botAction.sendArenaMessage("INTERVAL! 2 mins and game starts back!", 2);
            //INVERT TEAMS POSITIONS
            
        }
        
        else if(state == HockeyState.Face_Off){
            stateController.setState(state);
            m_botAction.scheduleTask(getBack, Tools.TimeInMillis.SECOND*30);
            m_botAction.sendArenaMessage("Face off! 30 secs and time runs again!", 2);
        }
        
        else if(state == HockeyState.End_Game){
            stateController.setState(state);
            displayStatistics();
            doCleanTeams();
            stateController.setState(HockeyState.OFF);
        }
        
    }
    
    public void doReady(String name, int freq){
        if(teams[freq].getCaptainName().equals(name) || isAssistant(teams[freq].getTeamName(), name))
            teams[freq].setReady(true);
        
        else
            m_botAction.sendPrivateMessage(name, "You're not captain / assistant to ready your team.");
        
    }
    private boolean isAssistant(String teamName, String name) {
        // TODO Auto-generated method stub
        //make a query
        return true;
    }

    public void doCleanTeams(){
        teams[0].resetPlayers();
        teams[1].resetPlayers();
    }
    
    public void notifyTime(long mins, long secs){
    
        //m_botAction.showObject((int)mins);
        //m_botAction.showObject((int) (30+secs));
        m_botAction.sendArenaMessage("Mins: "+mins+" Secs: "+secs);
        
    }
    
    public void doStartBack(){
        stateController.setState(HockeyState.Game_In_Progress);
       
    }
    public void doPauseGame(){
        ticker.doPause();
        
    }
    public int getCurrentState(){
        return stateController.getCurrentState();
    }
    @Override
    public void setTeamReady() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void updatePlayerPoint() {
        // TODO Auto-generated method stub

    }
    
    public void setPractice(HockeyPractice practice) {
        this.practice = practice;
    }
    public HockeyPractice getPractice() {
        return practice;
    }
    public void setTicker(HockeyTicker ticker) {
        this.ticker = ticker;
    }
    public HockeyTicker getTicker() {
        return ticker;
    }

    public void setTeam1(HockeyTeam team) {
        this.team1 = team;
       // this.teams[1]  ;//new HockeyTeam();
    }

    public HockeyTeam getTeam1() {
        return team1;
    }

    public void setTeam0(HockeyTeam team0) {
        this.team0 = team0;
    }

    public HockeyTeam getTeam0() {
        return team0;
    }

    public void addPlayer(String name, int ship, int freq) { 
        // TODO Auto-generated method stub
        if(teams[freq].isFull())
        {
            m_botAction.sendPrivateMessage(name, "Team has 6 players already.");
            return;
        }
        if(teams[freq].Contains(name)){
            m_botAction.sendPrivateMessage(name, "You're already registered in...wtf are you doing?");
            return;
        }
        m_botAction.setShip(name, ship);
        
        /**
         * Falta selecionar o time certo do jogador por uma query
         * 
         * */
        
        //check if the teams are made - prac bot
        teams[freq].addPlayer(name, ship);
        m_botAction.sendArenaMessage(name+" is registered on ship "+ship);
    }

	@Override
	public void addPlayerPoint(String namePlayer, int freq, int pointType) {
		// TODO Auto-generated method stub
		final int typeGoal = 1;
		final int typeAssist = 2;
		final int typeSave = 3;
			
		switch (pointType){
			case typeGoal: addGoalPoint(namePlayer, freq); break;
			case typeAssist: addAssistPoint(namePlayer, freq); break;
			case typeSave: addSavePoint(namePlayer, freq); break;
		}
	}

	private void addSavePoint(String namePlayer, int freq) {
		// TODO Auto-generated method stub
		teams[freq].addSavePoint(namePlayer);
	}

	private void addAssistPoint(String namePlayer, int freq) {
		// TODO Auto-generated method stub
		teams[freq].addAssistPoint(namePlayer);
	}

	private void addGoalPoint(String namePlayer, int freq) {
		// TODO Auto-generated method stub
		teams[freq].addGoalPoint(namePlayer);
	}
	
	public void cancelTasks(){
	    this.m_botAction.cancelTasks();
	}

	@Override
	public void updateScore(int freq) {
		// TODO Auto-generated method stub
		teams[freq].setTeamGoals(teams[freq].getTeamGoals()+1);
	}

	@Override
	public void displayStatistics() {
		// TABLE RESULT
    	ArrayList<String> spam = new ArrayList<String>();
    	spam.add(" _______________________________________________________________");
    	spam.add("|                   Results / Statistics                        |");
    	spam.add("|                                                               |");
    	spam.add("|Team 1________/************************************************|");
    	spam.add("|                       |Goals  |Saves  |Assists|  Total Points |");
    	        
    	spam.add("|"+teams[0].getTeamName());
    	
    	spam.add("|---------------------------------------------------------------|");
    
    	spam.addAll(teams[0].displayStatistics());
    	
    	spam.add("|---------------------------------------------------------------|");
    	
        spam.add("|Team 2________/************************************************|");
        spam.add("|                       |Goals  |Saves  |Assists|  Total Points |");
        spam.add("|"+teams[1].getTeamName());
        
        spam.add("|---------------------------------------------------------------|");
        
    	spam.addAll(teams[1].displayStatistics());
    	
    	spam.add("|---------------------------------------------------------------|");
    	spam.add("________________________________________________________________");
        m_botAction.arenaMessageSpam(spam.toArray(new String[spam.size()]));
    	
    }

}
