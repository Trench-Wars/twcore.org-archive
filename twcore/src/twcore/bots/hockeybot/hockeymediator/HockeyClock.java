package twcore.bots.hockeybot.hockeymediator;

import java.util.TimerTask;

import twcore.core.BotAction;

public class HockeyClock
        extends TimerTask{
    
    private HockeyMediator hMediator;
    
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
    public HockeyClock(/*BotAction botAction, */){
        //game_action = botAction;
        
        /**
         * sets the mediator that'll handle the game
         * 
         * */
        
        /*if(time == 0){
            secs = 0;
            mins = 0;
        }
        else{
            if(time > 60){
                mins = time / 60;
                secs = time % 60;
            }
        }*/
    }
    
    /**
     * Pauses the current game
     * */
    
    public void doStart(int time){
        
        if(time == 0){
            secs = 0;
            mins = 0;
        }
        
        else if(time > 60){
                mins = time / 60;
                secs = time % 60;
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
            
            else if(mins == 1 && gethMediator().getCurrentState() != HockeyState.Period_In_Progress ){
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
        this.hMediator = hMediator;
    }

    public HockeyMediator gethMediator() {
        return hMediator;
    }
    
    
}
