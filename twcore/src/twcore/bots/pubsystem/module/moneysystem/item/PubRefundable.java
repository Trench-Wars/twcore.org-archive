package twcore.bots.pubsystem.module.moneysystem.item;

import twcore.core.util.Tools;

/**
    Class representing a refundable item. Refundables are cancelled and given back to
    the player when their expiry time is reached. Generally this is two-stage spending that
    requires a different player or staffer to confirm in order for the purchase to be valid.
    Examples include event buys, rock/paper/scissors, etc.

    @author qan
*/
public class PubRefundable {
    public String buyer;
    public int amount;
    private long timePurchased;
    public String desc;
    public static int REFUND_AFTER_MINUTES = 10;    // Time before item is refunded

    /**
     * Create new refundable item or command.
     * @param buyer Name of player who bought it
     * @param amount How much they paid for it
     * @param desc A description string included in the refund msg sent to a player
     */
    public PubRefundable(String buyer, int amount, String desc) {
        this.buyer = buyer;
        this.amount = amount;
        this.desc = desc;
        timePurchased = System.currentTimeMillis();
    }

    /** 
     * @return True if the item has expired and should be refunded to the player.
     */
    public boolean hasBuyExpired() {
        if( timePurchased + (Tools.TimeInMillis.MINUTE * REFUND_AFTER_MINUTES) < System.currentTimeMillis() )
            return true;

        return false;
    }
}
