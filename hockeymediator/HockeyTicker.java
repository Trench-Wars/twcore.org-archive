package twcore.bots.hockeybot.hockeymediator;

import java.util.TimerTask;


public class HockeyTicker
        extends TimerTask{
    
    private HockeyMediator mediator;
    
    
    private final long time_to_start = 59;
    private final long interval = 5;
    private final long end_of_game = 10;
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
    
    /**
     * Constructor to handle the timer, if ER wants to start it by a custom timer
     * 
     * */
    public HockeyTicker(){
        
    }
    
    /**
     * Pauses the current game
     * */
    
    public void doStart(long time){
        
        if(time == 0){
            secs = 0;
            mins = 0;
        }
        
        else if(time > 60){
                mins = (time / 60);
                secs = (time % 60);
            }
        
    }
    
    public void doPause(){
        
        isPaused = true;
        //getMediator().setState(0);
        
    }
    
    /**
     * Starts back and run the clock from where it stopped
     * */
    public void doStartBack(){
    
        isPaused = false;
    }
    
    /**
     * Possible states*/
    public boolean isPaused(){
        return isPaused == true;
    }
    public boolean isInFaceOff(){
        return getMediator().getCurrentState() == HockeyState.Face_Off;
    }
    public boolean isInInterval(){
        return getMediator().getCurrentState() == HockeyState.In_Interval;
    }
    public boolean isInProgress(){
        return getMediator().getCurrentState() == HockeyState.Game_In_Progress;
    }
    public boolean isInEnd(){
        return getMediator().getCurrentState() == HockeyState.End_Game;
    }
    public boolean isInPreStart(){
        return getMediator().getCurrentState() == HockeyState.Pre_Start;
    }
    
    @Override
    public void run() {
        
        if(isPaused() || isInFaceOff() || isInInterval())
            return;
        
        else if(secs == time_to_start && !isInProgress()){
            getMediator().setState(HockeyState.Game_In_Progress);
            mins = 0;
            secs = 0;
        }
        else if(mins == interval && !isInInterval()){
            getMediator().setState(HockeyState.In_Interval);
            doPause();
        }
        else if(mins == end_of_game && !isInEnd()){
            getMediator().setState(HockeyState.End_Game);
            this.cancel();
        }
        
        else if(isInProgress())
        {
            if(secs == 59){
                mins++;
                secs = 0;
            }
            
            else
                secs++;
            
            getMediator().notifyTime(mins, secs);
        }
        /**
         * Falta colocar o intervalo
         * 
         * Falta colocar o final do jogo - game over
         * 
         * 
         * */
        //else if(secs ==)
        /*else if(mins == 10 && getMediator().getCurrentState() != HockeyState.FaceOff)
        {
            
            getMediator().setState(3);
            
        }*/
        
        
        //game_action.sendArenaMessage("Current Time: "+mins+":"+secs+" mins");
    }


    public void setMediator(HockeyMediator hMediator) {
        this.mediator = hMediator;
    }

    public HockeyMediator getMediator() {
        return mediator;
    }
    
    
}
