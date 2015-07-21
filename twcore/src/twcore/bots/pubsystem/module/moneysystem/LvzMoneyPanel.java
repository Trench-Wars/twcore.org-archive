package twcore.bots.pubsystem.module.moneysystem;

import twcore.core.BotAction;
import twcore.core.lvz.Objset;

public class LvzMoneyPanel {
    private int playerID;
    private Objset moneyObjs;
    private BotAction botAction;

    
    // Debug variables
    private static long startedAt = System.currentTimeMillis();
    public static long totalObjSent = 0;
    
    public LvzMoneyPanel(int playerID, Objset moneyObjs, BotAction botAction){
        this.playerID = playerID;
        this.moneyObjs = moneyObjs;
        this.botAction = botAction;
    }
    
    /**
     * Updates money scoreboard using minimal # of object changes, comparing money before and after.
     * 
     * 21/07/15 - Converted to work with LVZ packet. -qan
     */
	public void update(String beforeMoney, String afterMoney, boolean gainedMoney) {

    	if (beforeMoney.equals("0"))
    		beforeMoney = "";
    	
    	// Padding with empty space
        if (afterMoney.length() > beforeMoney.length()) {
        	beforeMoney = String.format("%1$" + afterMoney.length() + "s", beforeMoney);  
        } else if (afterMoney.length() < beforeMoney.length()) {
        	afterMoney = String.format("%1$" + beforeMoney.length() + "s", afterMoney); 
        }
        
        for (int i = 0; i < afterMoney.length(); i++) {
        	if (beforeMoney.charAt(i) == afterMoney.charAt(i)) {
        		continue;
        	}
        	else if (afterMoney.charAt(i) == ' ') {
        	    moneyObjs.hideObject(playerID, Integer.getInteger("502"+(afterMoney.length()-i-1)+beforeMoney.charAt(i)));
                //totalObjSent++;
        	}
        	else {
        		if (beforeMoney.charAt(i) != ' ') {
        		    moneyObjs.hideObject(playerID, Integer.getInteger("502"+(afterMoney.length()-i-1)+beforeMoney.charAt(i)));
        			//totalObjSent++;
        		}
        		moneyObjs.showObject(playerID, Integer.getInteger("502"+(afterMoney.length()-i-1)+afterMoney.charAt(i)));
        	}
        	//totalObjSent++;

        }
        
        if(gainedMoney)
            moneyObjs.showObject(playerID, 50100);
        else
            moneyObjs.showObject(playerID, 50101);
        //totalObjSent++;
        botAction.manuallySetObjects(moneyObjs.getObjects(playerID),playerID);
    }
    
    public void reset(){
        moneyObjs.hideAllObjects();
        botAction.manuallySetObjects(moneyObjs.getObjects(playerID),playerID);
    }
    
    public static int totalObjSentPerMinute() {
        long minute = System.currentTimeMillis()-startedAt;
        minute /= 1000*60;
        return (int)(totalObjSent/minute);
    }
}
