package twcore.bots.hockeybot.hockeymediator;

import java.util.TimerTask;

import twcore.bots.hockeybot.hockeybot;
import twcore.bots.hockeybot.hockeyteam.HockeyPlayer;
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
 * */    
public class HockeyConcreteMediator implements HockeyMediator {

    private     HockeyState     state;
    private     HockeyPractice  practice;
    private     HockeyTicker    ticker;
    private     HockeyTeam      team1;
    private     HockeyTeam      team0;
    private     HockeyTeam      teams[];
    
    
    private BotAction m_botAction;
    
    private static HockeyConcreteMediator mediator;
    
  
    /**
     * Singleton of hockeymediator
     * */
    private HockeyConcreteMediator(BotAction botAction){
       
        this.m_botAction = botAction;

        state = new HockeyState();
        state.setState(HockeyState.OFF);
        state.setMediator(this);
    }
    
    public static HockeyConcreteMediator getInstance(BotAction botAction){
        if(mediator == null)
            mediator = new HockeyConcreteMediator(botAction);
        
        return mediator;
    }
    
    public void startPractice(String name, String squadAccepted){
        
        practice = HockeyPractice.getInstance(m_botAction);
        practice.setMediator(this);
        practice.doAcceptGame(name, squadAccepted);
        
        state.setState(HockeyState.Pre_Start);
        
        ticker = new HockeyTicker();
        ticker.sethMediator(this);
        ticker.doStart((short) 0);
        m_botAction.scheduleTask( ticker , 100, Tools.TimeInMillis.SECOND);
        
        team1 = new HockeyTeam(1, "Bots", m_botAction);
        team0 = new HockeyTeam(0, "Andre", m_botAction);
        teams = new HockeyTeam[2]  ;
        teams[0] = team0;
        teams[1] = team1;
        
    }
    public boolean gameIsRunning(){
        return state.getCurrentState() != HockeyState.OFF && state.getCurrentState() != HockeyState.Face_Off;
    }
    public void checkTeamReady(){
        //if(getteam().getTeamSize(1)) ;
    }
    
    /*
    public int getSavePoint(String name){
        return teams[0].getSavePoints(name);
    }
    
    public int getNSave(String name){
        return teams[0].getNumberSave(name);
    }
    public void giveSavePoint(String name, int freq){
        teams[0].givePointGoal(name);
    }
    public void giveGoalPoint(String name, int freq){
        teams[0].givePointGoal(name);
    }
    
    
    public int doGetGoalPoints(String name){
        return teams[0].getGoalPoints(name);
    }
    
    public int doGetNGoalPoints(String name){
        return teams[0].getNPoint(name);
    }
    */
    public void doReadyTeam(String name, String message){
        
    }
    public boolean isReady(int frequence){
        return teams[frequence].isReady();
    }
    
    public void cancelGame() throws Throwable{
        //practice.cancelGame();
        
        m_botAction.cancelTask(getTicker());
    }
    
    @Override
    public void setState(int state) {
        // TODO Auto-generated method stub
        if(state == HockeyState.Game_In_Progress){
            
            if(isReady(0) && isReady(1)){
                this.state.setState(state);
                m_botAction.sendArenaMessage("State set to "+state);
            }
            else{
                try {
                    cancelGame();
                    this.state.setState(HockeyState.OFF);
                    m_botAction.sendArenaMessage("Not enough players to start");
                } catch (Throwable e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            //team1.setReady(true);
            //team2.setReady(true);
        }
        else if(state == HockeyState.Face_Off){
            this.state.setState(state);
            doPauseGame();  
            TimerTask getBack = new TimerTask(){
                public void run(){
                    doStartBack();
                }
            }   ;
            m_botAction.scheduleTask(getBack, Tools.TimeInMillis.SECOND*30);
            m_botAction.sendArenaMessage("Face off! 30 secs and time runs again!", 2);
        }
        
    }

    public void notifyTime(short i, short j){
        //i mins
        //j secs
        m_botAction.showObject((int)i);
        m_botAction.showObject((int) (30+j));
        m_botAction.sendArenaMessage("Mins: "+i+" Secs: "+j);
        
    }
    public void doStartBack(){
        state.setState(HockeyState.Game_In_Progress);
        ticker.doStartBack();
    }
    public void doPauseGame(){
        ticker.doPause();
        
    }
    public int getCurrentState(){
        return state.getCurrentState();
    }
    @Override
    public void setTeamReady() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void updatePlayerPoint() {
        // TODO Auto-generated method stub

    }
    
    public void setState(HockeyState state) {
        this.state = state;
    }
    public HockeyState getState() {
        return state;
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
			case typeGoal: addGoalPoint(namePlayer, freq);
			case typeAssist: addAssistPoint(namePlayer, freq);
			case typeSave: addSavePoint(namePlayer, freq);
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

}
