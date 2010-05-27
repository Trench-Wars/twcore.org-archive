package twcore.bots.hockeybot.hockeymediator;

import java.util.Observable;
import java.util.Observer;

public class HockeyState extends HockeyColleague {


    private int state;
    private long stateTimeStamp;
    
    public static final int OFF = -1;
    
    public static final int FaceOff = 0;
    
    public static final int PreStartPeriod = 1;
    
    public static final int Period_In_Progress = 2;

    public static final int EndGame = 3;
    
    
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
