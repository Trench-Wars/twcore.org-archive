package twcore.bots.pubsystem.module.moneysystem.item;

import twcore.core.util.Tools;

/**
 * Class representing an event bought through !buy event.
 * 
 * @author qan
 */
public class PubEventBuy {
    public String buyer;
    public String event;
    public int amount;
    private long timePurchased;
    public static int EVENT_BUY_EXPIRE_MIN = 10;    // Time before event buys expire
    
    public PubEventBuy(String buyer, String event, int amount) {
        this.buyer = buyer;
        this.event = event;
        this.amount = amount;
        timePurchased = System.currentTimeMillis();
    }
    
    public boolean hasBuyExpired() {
        if( timePurchased + Tools.TimeInMillis.MINUTE * EVENT_BUY_EXPIRE_MIN < System.currentTimeMillis() )
            return true;
        return false;
    }
}
