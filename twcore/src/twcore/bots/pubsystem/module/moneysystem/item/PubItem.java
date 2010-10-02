package twcore.bots.pubsystem.module.moneysystem.item;

import java.util.ArrayList;
import java.util.List;


public abstract class PubItem {
    
    protected String name;
    protected String displayName;
    protected String description;
    protected int price;
    protected boolean arenaItem;
    protected PubItemDuration duration;
    protected PubItemRestriction restriction;
    protected long lastTimeUsed = 0;
    protected List<String> abbreviations;
    protected boolean playerOptional = false;
    protected boolean playerStrict = false;
    protected boolean hidden = false;

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
	
	public boolean isPlayerOptional() {
		return playerOptional;
	}
	
	public boolean isPlayerStrict() {
		return playerStrict;
	}

}
