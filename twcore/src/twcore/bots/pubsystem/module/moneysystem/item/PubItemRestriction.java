package twcore.bots.pubsystem.module.moneysystem.item;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.bots.pubsystem.util.PubException;
import twcore.core.util.Tools;

public class PubItemRestriction {

	private List<Integer> ships;
	private int maxPerLife = -1;
	private int maxConsecutive = -1;
	private int maxArenaPerMinute = -1;
	private int maxPerSecond = -1;
	private int maxFreqPerMinute = -1;
	private int globalCooldownBuy = -1;
	private int detachLevTerrCooldown = -1;
	private boolean buyableFromLevTerr = true;
	private boolean buyableFromSpec = false;
	private List<String> itemNotSameTime;
	private TreeMap<Short, Long> freqUsed;
	
	public PubItemRestriction() {
		ships = new ArrayList<Integer>();
		itemNotSameTime = new ArrayList<String>();
		freqUsed = new TreeMap<Short, Long>();
	}
	
	public void addShip(int shipType) {
		ships.add(shipType);
	}
	
	public void addItemNotSameTime(String item) {
		itemNotSameTime.add(item);
	}
	
	public boolean freqUsing(Short freq) {
	    long now = System.currentTimeMillis();
	    if (maxFreqPerMinute == -1)
	        return true;
	    else if (!freqUsed.containsKey(freq)) {
	        freqUsed.put(freq, now);
	        return true;
	    } else if (now - freqUsed.get(freq) > maxFreqPerMinute * Tools.TimeInMillis.MINUTE) {
	        freqUsed.put(freq, now);
	        return true;
	    } else
	        return false;
	}
	
	public boolean canFreqUse(Short freq) {
        if (maxFreqPerMinute == -1)
            return true;
        else if (!freqUsed.containsKey(freq)) {
            return true;
        } else if (System.currentTimeMillis() - freqUsed.get(freq) > maxFreqPerMinute * Tools.TimeInMillis.MINUTE) {
            return true;
        } else
            return false;
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
	
	public void setFreqPerMinute(int max) {
	    this.maxFreqPerMinute = max;
	}
	
	public void setGobalCooldownBuy(int c) {
        this.globalCooldownBuy = c;
    }
	
	public void setDetachLevTerrCooldown(int c) {
	    this.detachLevTerrCooldown = c;
	}
	
	public void buyableFromSpec(boolean b) {
		this.buyableFromSpec = b;
	}
	
	public void setbuyableFromLevTerr() {
	    this.buyableFromLevTerr = false;
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
	
	public int getMaxFreqPerMinute() {
	    return maxFreqPerMinute;
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
	
	public void check(PubItem item, PubPlayer player, int shipType, Short freq) throws PubException {
		
		if (ships.contains(shipType))
			throw new PubException("You cannot buy this item with your current ship.");
		
		if (!buyableFromSpec) {
			if (player.isOnSpec()) {
				throw new PubException("You cannot buy this item if you are a spectator.");
			}
		}
		
		if (!buyableFromLevTerr && player.getLevTerr()) {
		    throw new PubException("Buying has been disabled for LevTerrs.");
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
					throw new PubException("You have bought this item in the past " + maxPerSecond + " seconds, please wait...");
				}
			}
		}
		
		if (globalCooldownBuy!=-1 && shipType == 4) {
            List<PubItemUsed> items = player.getItemsBought();
            for(int i=0; i<items.size(); i++) {
                PubItemUsed itemUsed = items.get(i);
                long diff = System.currentTimeMillis()-itemUsed.getTime();
                if (diff < globalCooldownBuy*Tools.TimeInMillis.SECOND) {
                    throw new PubException("You must wait at least " + globalCooldownBuy + " seconds before you purchase another item , please wait...");
                }
            }
        }
		
		if (detachLevTerrCooldown !=-1 && (shipType == 4 || shipType == 5)) {
                long diff = System.currentTimeMillis()-player.getLastDetachLevTerr();
                if (diff < detachLevTerrCooldown*Tools.TimeInMillis.SECOND) 
                    throw new PubException("You must wait at least " + globalCooldownBuy + " seconds before you purchase another item , please wait...");                
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
        
        if (!canFreqUse(freq))
            throw new PubException("Your freq has bought this item in the past " + maxFreqPerMinute + " minutes, please wait...");

	}	
    
    public void checkForOther(PubItem item, PubPlayer player, int shipType) throws PubException {
        
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
            List<PubItemUsed> items = player.getItemsBoughtForOther();
            for(int i=0; i<items.size(); i++) {
                PubItemUsed itemUsed = items.get(i);
                if (!itemUsed.getItem().getName().equals(item.getName()))
                    continue;
                long diff = System.currentTimeMillis()-itemUsed.getTime();
                if (diff < maxPerSecond*Tools.TimeInMillis.SECOND) {
                    throw new PubException("You have bought this item for someone in the past " + maxPerSecond + " seconds, please wait...");
                }
            }
        }
        
    }
	
}
