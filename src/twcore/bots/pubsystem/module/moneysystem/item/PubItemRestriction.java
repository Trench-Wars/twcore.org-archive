package twcore.bots.pubsystem.module.moneysystem.item;

import java.util.ArrayList;
import java.util.List;

import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.bots.pubsystem.util.PubException;
import twcore.core.util.Tools;

public class PubItemRestriction {

	private List<Integer> ships;
	private int maxPerLife = -1;
	private int maxConsecutive = -1;
	private int maxArenaPerMinute = -1;
	private int maxPerSecond = -1;
	private boolean buyableFromSpec = false;
	private List<String> itemNotSameTime;
	
	public PubItemRestriction() {
		ships = new ArrayList<Integer>();
		itemNotSameTime = new ArrayList<String>();
	}
	
	public void addShip(int shipType) {
		ships.add(shipType);
	}
	
	public void addItemNotSameTime(String item) {
		itemNotSameTime.add(item);
	}
	
	public void setMaxPerLife(int max) {
		this.maxPerLife = max;
	}
	
	public void setMaxConsecutive(int max) {
		this.maxConsecutive = max;
	}
	
	public void setMaxArenaPerMinute(int max) {
		this.maxArenaPerMinute = max;
	}
	
	public void setMaxPerSecond(int max) {
		this.maxPerSecond = max;
	}
	
	public void buyableFromSpec(boolean b) {
		this.buyableFromSpec = b;
	}
	
	public boolean isBuyableFromSpec() {
		return buyableFromSpec;
	}
	
	public int getMaxArenaPerMinute() {
		return maxArenaPerMinute;
	}
	
	public int getMaxPerSecond() {
		return maxPerSecond;
	}
	
	public int getMaxPerLife() {
		return maxPerLife;
	}
	
	public int getMaxConsecutive() {
		return maxConsecutive;
	}
	
	public List<String> getItemNotSameTime() {
		return itemNotSameTime;
	}
	
	public List<Integer> getRestrictedShips() {
		return ships;
	}
	
	public void check(PubItem item, PubPlayer player, int shipType) throws PubException {
		
		if (ships.contains(shipType))
			throw new PubException("You cannot buy this item with your current ship.");
		
		if (!buyableFromSpec) {
			if (player.isOnSpec()) {
				throw new PubException("You cannot buy this item if you are a spectator.");
			}
		}
		
		if (maxArenaPerMinute!=-1) {
			long diff = System.currentTimeMillis()-item.getLastTimeUsed();
			if (diff < maxArenaPerMinute*Tools.TimeInMillis.MINUTE) {
				throw new PubException("This item has been bought in the past " + maxArenaPerMinute + " minutes, please wait..");
			}
		}
		
		if (maxPerSecond!=-1) {
			List<PubItemUsed> items = player.getItemsBought();
			for(int i=0; i<items.size(); i++) {
				PubItemUsed itemUsed = items.get(i);
				if (!itemUsed.getItem().getName().equals(item.getName()))
					continue;
				long diff = System.currentTimeMillis()-itemUsed.getTime();
				if (diff < maxPerSecond*Tools.TimeInMillis.SECOND) {
					throw new PubException("You have bought this item in the past " + maxPerSecond + " seconds, please wait..");
				}
			}
		}
		
		if (maxPerLife!=-1) {
			List<PubItem> items = player.getItemsBoughtThisLife();
			int count = 1;
			for(int i=0; i<items.size(); i++) {
				if (items.get(items.size()-1-i)==item)
					count++;
				if (count>maxPerLife)
					throw new PubException("This item is limited to " + maxPerLife + " per life.");
			}
		}
		
		if (maxConsecutive!=-1) {
			List<PubItem> items = player.getItemsBoughtThisLife();
			int count = 0;
			for(int i=0; i<Math.min(items.size(), maxConsecutive); i++) {
				if (items.get(items.size()-1-i)==item)
					count++;
				else if (count<maxConsecutive)
					break;
				if (count>=maxConsecutive)
					throw new PubException("Only " + maxConsecutive + " consecutive buy of this item allowed.");
			}
		}

	}
	
}
