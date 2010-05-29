package twcore.bots.hockeybot.hockeymediator;


public class HockeyState extends HockeyColleague {


    private int state;
    private long stateTimeStamp;
    
    public static final int OFF = -1;
    public static final int Face_Off = 0;
    public static final int Pre_Start = 1;
    public static final int Game_In_Progress = 2;
    public static final int In_Interval = 3;
    public static final int End_Game = 4;
    
    public HockeyState(){
        this.state = OFF;
    }
    
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

}
