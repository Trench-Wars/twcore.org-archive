package twcore.bots.pubsystem.module.moneysystem;

import twcore.core.BotAction;

public class LvzMoneyPanel {

    private BotAction botAction;
    
    // debug purpose only
    private static long startedAt = System.currentTimeMillis();
    public static long totalObjSent = 0;
    
    public LvzMoneyPanel(BotAction botAction){
        this.botAction = botAction;
    }
    
    /* This algorithm try to avoid unnecessary changes when the object is already on/off
     * It works by comparing the money before and after
     */
	public void update(int playerId, String beforeMoney, String afterMoney, boolean gainedMoney){

    	if (beforeMoney.equals("0"))
    		beforeMoney = "";
    	
    	// Padding with empty space
        if (afterMoney.length() > beforeMoney.length()) {
        	beforeMoney = String.format("%1$#" + afterMoney.length() + "s", beforeMoney);  
        } else if (afterMoney.length() < beforeMoney.length()) {
        	afterMoney = String.format("%1$#" + beforeMoney.length() + "s", afterMoney); 
        }

        String playerName = botAction.getPlayerName(playerId);
        
        int length = afterMoney.length();
        
        for(int i = 0; i < afterMoney.length(); i++)
        {
 
        	if (beforeMoney.charAt(i) == afterMoney.charAt(i)) {
        		continue;
        	}
        	else if (afterMoney.charAt(i) == ' ') {
        		botAction.sendUnfilteredPrivateMessage(playerName, "*objoff "+502+(length-i-1)+beforeMoney.charAt(i));
        	}
        	else {
        		if (beforeMoney.charAt(i) != ' ') {
        			botAction.sendUnfilteredPrivateMessage(playerName, "*objoff "+502+(length-i-1)+beforeMoney.charAt(i));
        			totalObjSent++;
        		}
        		botAction.sendUnfilteredPrivateMessage(playerName, "*objon "+502+(length-i-1)+afterMoney.charAt(i));
        	}
        	totalObjSent++;

        }
        
        if(gainedMoney)
            botAction.sendUnfilteredPrivateMessage(playerName, "*objon 50100");
        else
            botAction.sendUnfilteredPrivateMessage(playerName, "*objon 50101");
        totalObjSent++;
    }
    
    public void reset(String playerName, int oldValue) {
    	
    	String value = String.valueOf(oldValue);
    	for(int i = 0; i < value.length(); i++) {
    		botAction.sendUnfilteredPrivateMessage(playerName, "*objoff "+502+(value.length()-i-1)+value.charAt(i));
    	}
    	
    }
    
    public static int totalObjSentPerMinute() {
    	long minute = System.currentTimeMillis()-startedAt;
    	minute /= 1000*60;
    	return (int)(totalObjSent/minute);
    }
    
    public void reset(String playerName){
		for (int i = 0; i < 7; i++) {
			for (int j = 0; j < 10; j++) {
				botAction.sendUnfilteredPrivateMessage(playerName, "*objoff " + 502 + i + j);
			}
		}
    }

    
}
