package twcore.bots.pubsystem.module.moneysystem.item;

import java.util.ArrayList;
import java.util.List;

/**
 * Pub item class.
 * 
 * TODO: Fully comment. There are a lot of so-so naming choices made for this file
 * and it could really use it.
 */
public abstract class PubItem {
    
    protected String name;
    protected String displayName;
    protected String description;
    protected int price;
    protected boolean arenaItem;
    protected PubItemDuration duration;
    protected PubItemRestriction restriction;
    protected int immuneTime = 0; // for freq in seconds
    protected long lastTimeUsed = 0;
    protected List<String> abbreviations;
    protected boolean playerOptional = false;
    protected boolean playerStrict = false;
    protected boolean hidden = false;
    protected boolean buyBlockImmune = false;

    public PubItem(String name, String displayName, String description, int price) {
        this.name = name;
        this.displayName = displayName;
        this.price = price;
        this.description = description;
        this.arenaItem = false;
        this.abbreviations = new ArrayList<String>();
    }

    public String getName() {
        return name;
    }
    
    public String getDisplayName() {
    	return displayName;
    }
    
    public boolean isHidden() {
    	return hidden;
    }
    
    public int getPrice() {
        return price;
    }
    
    public String getDescription() {
    	return description;
    }
    
    public void addAbbreviation(String abbv) {
    	this.abbreviations.add(abbv);
    }
    
    public List<String> getAbbreviations() {
    	return abbreviations;
    }
    
    public void setHidden() {
    	this.hidden = true;
    }
    
    public void setArenaItem(boolean b) {
    	this.arenaItem = b;
    }
    
	public void setDuration(PubItemDuration d) {
		this.duration = d;
	}

	public void setRestriction(PubItemRestriction r) {
		this.restriction = r;
	}
	
	public void setPlayerStrict(boolean b) {
		this.playerStrict = b;
	}
	
	public void setPlayerOptional(boolean b) {
		this.playerOptional = b;
	}
	
    public void setBuyBlockImmune(boolean b) {
        this.buyBlockImmune = b;
    }
    
	public boolean isRestricted() {
		return restriction != null;
	}
	
	public boolean hasDuration() {
		return duration != null;
	}
	
	public boolean isArenaItem() {
		return arenaItem;
	}
	
	public PubItemRestriction getRestriction() {
		return restriction;	
	}
	
	public PubItemDuration getDuration() {
		return duration;
	}
	
	public void hasBeenBought() {
		this.lastTimeUsed = System.currentTimeMillis();
	}
	
	public long getLastTimeUsed() {
		return lastTimeUsed;
	}
	
	/**
	 * @return True if you can gift this item to another player (but are not required to)
	 */
	public boolean isPlayerOptional() {
		return playerOptional;
	}
	
	/**
	 * @return True if you MUST gift this item to another player in order to buy it
	 */
	public boolean isPlayerStrict() {
		return playerStrict;
	}
	
	public void setImmuneTime(int t) {
	    this.immuneTime = t;
	}
	
	public int getImmuneTime() {
	    return immuneTime;
	}
	
	public boolean isBuyBlockImmune() {
	    return buyBlockImmune;
	}

}
