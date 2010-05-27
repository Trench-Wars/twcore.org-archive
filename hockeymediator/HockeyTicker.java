package twcore.bots.hockeybot.hockeymediator;

import java.util.TimerTask;

import twcore.core.BotAction;

public class HockeyTicker
        extends TimerTask{
    
    private HockeyMediator mediator;
    
    /**
     * looks up for each second*/
    private short secs = 0;
    
    /**
     * looks up for each minute
     * */
    private short mins = 0;
    
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
    
    public void doStart(short time){
        
        if(time == 0){
            secs = 0;
            mins = 0;
        }
        
        else if(time > 60){
                mins = (short) (time / 60);
                secs = (short) (time % 60);
            }
        
    }
    
    public void doPause(){
        
        isPaused = true;
        //gethMediator().setState(0);
        
    }
    
    /**
     * Starts back and run the clock from where it stopped
     * */
    public void doStartBack(){
    
        isPaused = false;
    }
    
    @Override
    public void run() {
        // TODO Auto-generated method stub
        if(!isPaused){
            
            if(secs == 59){
                mins++;
                secs = 0;
            }
            
            else if(secs == 0)
                gethMediator().setState(1);
            
            else if(mins == 1 && gethMediator().getCurrentState() != HockeyState.Game_In_Progress ){
                    gethMediator().setState(2); //Starts the game
            }
            
            /*else if(mins == 10 && gethMediator().getCurrentState() != HockeyState.FaceOff)
            {
                
                gethMediator().setState(3);
                
            }*/
            
            secs++;
            gethMediator().notifyTime(mins, secs);
            //game_action.sendArenaMessage("Current Time: "+mins+":"+secs+" mins");
            
        }
    }

    public void sethMediator(HockeyMediator hMediator) {
        this.mediator = hMediator;
    }

    public HockeyMediator gethMediator() {
        return mediator;
    }
    
    
}
