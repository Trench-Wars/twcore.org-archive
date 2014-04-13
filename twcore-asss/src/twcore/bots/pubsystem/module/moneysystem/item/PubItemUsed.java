package twcore.bots.pubsystem.module.moneysystem.item;

public class PubItemUsed {

	private PubItem itemUsed;
	
	// Time when this item has been bought
	private long timestamp;
	
	public PubItemUsed(PubItem item) {
		this.itemUsed = item;
		this.timestamp = System.currentTimeMillis();
	}
	
	public PubItem getItem() {
		return itemUsed;
	}
	
	public long getTime() {
		return timestamp;
	}
}
