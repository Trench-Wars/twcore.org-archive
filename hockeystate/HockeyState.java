package twcore.bots.hockeybot.hockeystate;

import twcore.bots.hockeybot.hockeydatabase.HockeyDatabase;
import twcore.bots.hockeybot.hockeyteam.HockeyTeam;
import twcore.core.BotAction;


public abstract class HockeyState //extends HockeyColleague {
{

/**
 * STATES ;
 * FACE OFF = AVOID GOALS - ADDS GOALS
 * PRE START = AVOID GOALS - ALLOW REGISTRATIONS - CREATES TEAM
 * GAME IN PROGRESS = ALLOW GOALS - ALLOW REGISTRATIONS IF ( HANDLE LAGOUT SUB ) 
 * IN INTERVAL = AVOID GOOALS
 * 
 * */
    protected BotAction bot;
    public abstract boolean tryToRegisterPlayer(String name);
    public abstract boolean tryToChallengeSquad(String name);
    public abstract boolean tryToAcceptSquad(String name);
    public abstract boolean tryToStartGame(String name);
    public abstract boolean tryToReadyTeam(HockeyTeam teams[], HockeyTeam team, String name);
    //private int state;
    //private long stateTimeStamp;
    
    
    /*
    public static final int OFF = -1;
    public static final int Face_Off = 0;
    public static final int Pre_Start = 1;
    public static final int Game_In_Progress = 2;
    public static final int In_Interval = 3;
    public static final int End_Game = 4;
    
    public HockeyState(){
        this.state = OFF;
    }*/
    /*
    public void setState(int state){
        this.stateTimeStamp = System.currentTimeMillis();
        this.state = state;
    }
    
    public int getCurrentState(){
        return state;
    }
    
    private long getTimeStamp(){
        return stateTimeStamp;
    }
     */
}
