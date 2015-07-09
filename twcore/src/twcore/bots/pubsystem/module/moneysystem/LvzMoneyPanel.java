package twcore.bots.pubsystem.module.moneysystem;

import twcore.core.BotAction;

public class LvzMoneyPanel {

    //private BotAction botAction;
    
    // debug purpose only
    private static long startedAt = System.currentTimeMillis();
    public static long totalObjSent = 0;
    
    public LvzMoneyPanel(BotAction botAction){
        //this.botAction = botAction;
    }
    
    /* This algorithm try to avoid unnecessary changes when the object is already on/off
     * It works by comparing the money before and after
     */
	public void update(int playerId, String beforeMoney, String afterMoney, boolean gainedMoney){

    	return;
    }
    
    public void reset(String playerName, int oldValue) {
        return;
    	
    }
    
    public static int totalObjSentPerMinute() {
    	long minute = System.currentTimeMillis()-startedAt;
    	minute /= 1000*60;
    	return (int)(totalObjSent/minute);
    }
    
    public void reset(String playerName){
		return;
    }

    
}
