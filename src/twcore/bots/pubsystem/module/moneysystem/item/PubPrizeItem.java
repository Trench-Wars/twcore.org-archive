package twcore.bots.pubsystem.module.moneysystem.item;

import java.util.List;


public class PubPrizeItem extends PubItem {

	private List<Integer> prizes;
	private int prizeSeconds = 0;

	public PubPrizeItem(String name, String displayName, String description, int price, List<Integer> prizes) {
		super(name, displayName, description, price);
		this.prizes = prizes;
	}
	
	public List<Integer> getPrizes() {
		return prizes;
	}
	
	public int getPrizeSeconds() {
		return prizeSeconds;
	}

	public void setPrizeSeconds(int seconds) {
		this.prizeSeconds = seconds;
	}

}
