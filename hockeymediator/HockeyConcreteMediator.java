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

    private     HockeyState     hState;
    private     HockeyPractice  hPractice;
    private     HockeyClock     hClock;
    private     HockeyTeam      hTeam1;
    private     HockeyTeam      hTeam0;
    private     HockeyTeam      hTeams[];
    
    private BotAction m_botAction;
    
    private static HockeyConcreteMediator mymediator = null;
    
    /**
     * Singleton of hockeymediator
     * */
    private HockeyConcreteMediator(BotAction botAction){
       
        this.m_botAction = botAction;

        hState = new HockeyState();
        hState.setState(HockeyState.OFF);
        hState.setMediator(this);
    }
    
    public static HockeyConcreteMediator getInstance(BotAction botAction){
        if(mymediator == null)
            mymediator = new HockeyConcreteMediator(botAction);
        
        return mymediator;
    }
    
    public void startPractice(String name, String squadAccepted){
        
        hPractice = HockeyPractice.getInstance(m_botAction);
        hPractice.setMediator(this);
        hPractice.doAcceptGame(name, squadAccepted);
        
        hState.setState(HockeyState.PreStartPeriod);
        
        hClock = new HockeyClock();
        hClock.sethMediator(this);
        hClock.doStart(0);
        m_botAction.scheduleTask( hClock , 100, Tools.TimeInMillis.SECOND);
        
        hTeam1 = new HockeyTeam(1, "Bots", m_botAction);
        hTeam0 = new HockeyTeam(0, "Andre", m_botAction);
        hTeams = new HockeyTeam[2]  ;
        hTeams[0] = hTeam0;
        hTeams[1] = hTeam1;
        
    }
    public boolean gameIsRunning(){
        return hState.getCurrentState() != HockeyState.OFF && hState.getCurrentState() != HockeyState.FaceOff;
    }
    public void checkTeamReady(){
        //if(gethTeam().getTeamSize(1)) ;
    }
    
    /*
    public int getSavePoint(String name){
        return hTeams[0].getSavePoints(name);
    }
    
    public int getNSave(String name){
        return hTeams[0].getNumberSave(name);
    }
    public void giveSavePoint(String name, int freq){
        hTeams[0].givePointGoal(name);
    }
    public void giveGoalPoint(String name, int freq){
        hTeams[0].givePointGoal(name);
    }
    
    
    public int doGetGoalPoints(String name){
        return hTeams[0].getGoalPoints(name);
    }
    
    public int doGetNGoalPoints(String name){
        return hTeams[0].getNPoint(name);
    }
    */
    public void doReadyTeam(String name, String message){
        
    }
    public boolean isReady(int frequence){
        return hTeams[frequence].isReady();
    }
    
    public void cancelGame() throws Throwable{
        //hPractice.cancelGame();
        
        m_botAction.cancelTask(gethClock());
    }
    @Override
    public void setState(int state) {
        // TODO Auto-generated method stub
        if(state == HockeyState.Period_In_Progress){
            
            if(isReady(0) && isReady(1)){
                hState.setState(state);
                m_botAction.sendArenaMessage("State set to "+state);
            }
            else{
                try {
                    cancelGame();
                    hState.setState(HockeyState.OFF);
                    m_botAction.sendArenaMessage("Not enough players to start");
                } catch (Throwable e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            //hTeam1.setReady(true);
            //hTeam2.setReady(true);
        }
        else if(state == HockeyState.FaceOff){
            hState.setState(state);
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

    public void notifyTime(long i, long j){
        //i mins
        //j secs
        m_botAction.showObject((int)i);
        m_botAction.showObject((int) (30+j));
        m_botAction.sendArenaMessage("Mins: "+i+" Secs: "+j);
        
    }
    public void doStartBack(){
        hState.setState(HockeyState.Period_In_Progress);
        hClock.doStartBack();
    }
    public void doPauseGame(){
        hClock.doPause();
        
    }
    public int getCurrentState(){
        return hState.getCurrentState();
    }
    @Override
    public void setTeamReady() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void updatePlayerPoint() {
        // TODO Auto-generated method stub

    }
    
    public void sethState(HockeyState hState) {
        this.hState = hState;
    }
    public HockeyState gethState() {
        return hState;
    }
    public void sethPractice(HockeyPractice hPractice) {
        this.hPractice = hPractice;
    }
    public HockeyPractice gethPractice() {
        return hPractice;
    }
    public void sethClock(HockeyClock hClock) {
        this.hClock = hClock;
    }
    public HockeyClock gethClock() {
        return hClock;
    }

    public void sethTeam1(HockeyTeam hTeam) {
        this.hTeam1 = hTeam;
       // this.hTeams[1]  ;//new HockeyTeam();
    }

    public HockeyTeam gethTeam1() {
        return hTeam1;
    }

    public void sethTeam0(HockeyTeam hTeam0) {
        this.hTeam0 = hTeam0;
    }

    public HockeyTeam gethTeam0() {
        return hTeam0;
    }

    public void addPlayer(String name, int ship, int freq) { 
        // TODO Auto-generated method stub
        if(hTeams[freq].isFull())
        {
            m_botAction.sendPrivateMessage(name, "Team has 6 players already.");
            return;
        }
        if(hTeams[freq].Contains(name)){
            m_botAction.sendPrivateMessage(name, "You're already registered in...wtf are you doing?");
            return;
        }
        m_botAction.setShip(name, ship);
        //check if the teams are made - prac bot
        hTeams[freq].addPlayer(name, ship);
        m_botAction.sendArenaMessage(name+" is registered on ship "+ship);
    }

}
