package twcore.bots.hockeybot.hockeymediator;

import java.util.TimerTask;

import twcore.core.BotAction;

public class HockeyTicker
        extends TimerTask{
    
    private HockeyMediator mediator;
    
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
    
    @Override
    public void run() {
        // TODO Auto-generated method stub
        if(!isPaused){
            
            if(secs == 59){
                mins++;
                secs = 0;
            }
            
            else if(secs == 0)
                getMediator().setState(1);
            
            else if(secs == 40 && getMediator().getCurrentState() == HockeyState.Pre_Start ){
                    getMediator().setState(HockeyState.Game_In_Progress); //Starts the game
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
            
            secs++;
            getMediator().notifyTime(mins, secs);
            //game_action.sendArenaMessage("Current Time: "+mins+":"+secs+" mins");
            
        }
    }

    public void setMediator(HockeyMediator hMediator) {
        this.mediator = hMediator;
    }

    public HockeyMediator getMediator() {
        return mediator;
    }
    
    
}
