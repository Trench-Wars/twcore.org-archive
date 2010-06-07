package twcore.bots.hockeybot.hockeymediator;

import java.util.Timer;
import java.util.TimerTask;


public class HockeyTicker
        extends TimerTask{
    
    private HockeyMediator mediator;
    
    private final long time_to_start = 10;
    private final long interval = 12;
    private final long end_of_game = 24;
    
    /**
     * looks up for each second*/
    private long secs = 0;
    
    /**
     * looks up for each minute
     * */
    private long mins = 0;
    
    /**
     * to Pause the game
     * */
    private boolean isPaused = false;
    
    private boolean hadInterval = false;
    /**
     * Constructor to handle the timer, if ER wants to start it by a custom timer
     * 
     * */
    public HockeyTicker(){
        secs = 0;
        mins = 0;
       
    }
    
    /**
     * Pauses the current game
     * */

    public void pause(){
        
        isPaused = true;
        //getMediator().setState(0);
        
    }
    
    /**
     * Starts back and run the clock from where it stopped
     * */
    public void startBack(){
    
        isPaused = false;
    }
    
    /**
     * Possible states*/
    public boolean isPaused(){
        return isPaused == true;
    }
    public boolean isInFaceOff(){
        return mediator.getCurrentState() == HockeyState.Face_Off;
    }
    public boolean isInInterval(){
        return mediator.getCurrentState() == HockeyState.In_Interval;
    }
    public boolean isInProgress(){
        return mediator.getCurrentState() == HockeyState.Game_In_Progress;
    }
    public boolean isInEnd(){
        return mediator.getCurrentState() == HockeyState.End_Game;
    }
    public boolean isInPreStart(){
        return mediator.getCurrentState() == HockeyState.Pre_Start;
    }
    
    @Override
    public void run() {
     
        
        if(mins == time_to_start && !isInProgress() &&!isInFaceOff() && !isInInterval()){
            mediator.setState(HockeyState.Game_In_Progress);
            mins = 0;
            secs = 0;
        }
        /*else if(mins == interval && !isInInterval()){
            getMediator().setState(HockeyState.In_Interval);
            pause();
        }*/
        else if(mins == end_of_game && !isInEnd()){
            mediator.setState(HockeyState.End_Game);
            this.cancel();
        }
        else if(mins == interval && !hadInterval){
            mediator.setState(HockeyState.In_Interval);
            hadInterval = true;
            pause();
        }
        
        if(!isPaused()){
            if(secs == 59){
                mins++;
                secs = 0;
            }
                
            else
                secs++;
        }
        
        //mediator.notifyTime(mins, secs);
    }
        /**
         * Falta colocar o intervalo
         * 
         * Falta colocar o final  jogo - game over
         * 
         * 
         * */

    public long getMins(){
        return mins;
    }
    public long getSecs(){
        return secs;
    }
    
    public void resetTime(){
        mins = 0;
        secs = 0;
    }
    public void setMediator(HockeyMediator hMediator) {
        this.mediator = hMediator;
    }
    
}
