package twcore.bots.pubsystem.module.player;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import twcore.bots.pubsystem.module.PubUtilModule.Tileset;
import twcore.bots.pubsystem.module.moneysystem.LvzMoneyPanel;
import twcore.bots.pubsystem.module.moneysystem.item.PubItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubItemUsed;
import twcore.bots.pubsystem.module.moneysystem.item.PubPrizeItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubShipItem;
import twcore.core.BotAction;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerDeath;
import twcore.core.game.Player;
import twcore.core.util.Tools;


public class PubPlayer implements Comparable<PubPlayer>{
    
	private static final int MAX_ITEM_USED_HISTORY = 30 * Tools.TimeInMillis.MINUTE;
	
	private BotAction m_botAction;

	private Tileset tileset = Tileset.MONOLITH;
	
    private String name;
    private int money;
    private LinkedList<PubItemUsed> itemsBought;
    private LinkedList<PubItem> itemsBoughtThisLife;
    
    private LvzMoneyPanel cashPanel;
    
    // A player can only have 1 ShipItem at a time
    private PubShipItem shipItem;
    private int deathsOnShipItem = 0;
    
    // Epoch time
    private long lastMoneyUpdate = 0;
    private long lastSavedState = 0;
    private long lastOptionsUpdate = 0;
    private long lastDeath = 0;
    
    // Stats
    private int bestStreak = 0;
    
    private boolean isOnline = false; // If online, on the same arena

    public PubPlayer(BotAction m_botAction, String name) {
    	this(m_botAction, name, 0);
    }
    
    public PubPlayer(BotAction m_botAction, String name, int money) {
    	this.m_botAction = m_botAction;
        this.name = name;
        this.money = money;
        this.itemsBought = new LinkedList<PubItemUsed>();
        this.itemsBoughtThisLife = new LinkedList<PubItem>();
        this.cashPanel = new LvzMoneyPanel(m_botAction);
        reloadPanel(false);
    }

    public String getPlayerName() {
        return name;
    }
    
    public Tileset getTileset() {
    	return tileset;
    }
    
    public void setTileset(Tileset tileset) {
    	if (!this.tileset.equals(tileset)) {
    		lastOptionsUpdate = System.currentTimeMillis();
    	}
    	this.tileset = tileset;
    }

    public int getMoney() {
        return money;
    }
    
    public void reloadPanel(boolean fullReset) {
    	if (fullReset) {
    		cashPanel.reset(name);
    	} else {
    		cashPanel.reset(name, money);
    		cashPanel.update(m_botAction.getPlayerID(name), String.valueOf(0), String.valueOf(money), true);
    	}
    }
    
    public void setMoney(int money) {
    	int before = this.money;
    	if (money < 0)
    		money = 0;
        this.money = money;
        boolean gained = before > money ? false : true;
        cashPanel.update(m_botAction.getPlayerID(name), String.valueOf(before), String.valueOf(money), gained);
        this.lastMoneyUpdate = System.currentTimeMillis();
    }
    
    public void addMoney(int money) {
    	setMoney(this.money+money);
    }
    
    public void removeMoney(int money) {
    	setMoney(this.money-money);
    }

    public void addItem(PubItem item, String param) {
    	purgeItemBoughtHistory();
        this.itemsBought.add(new PubItemUsed(item));
        this.itemsBoughtThisLife.add(item);
    }
    
    public void addDeath() {
    	this.lastDeath = System.currentTimeMillis();
    }
    
    public long getLastDeath() {
    	return lastDeath;
    }
    
    public int getBestStreak() {
    	return bestStreak;
    }
    
    public void setBestStreak(int bestStreak) {
    	this.bestStreak = bestStreak;
    }
    
    private void purgeItemBoughtHistory() 
    {
    	Iterator<PubItemUsed> it = itemsBought.iterator();
    	while(it.hasNext()) {
    		PubItemUsed item = it.next();
    		if (System.currentTimeMillis()-item.getTime() > MAX_ITEM_USED_HISTORY) {
    			it.remove();
    		} else {
    			break;
    		}
    	}
    }
    
    public boolean hasItemActive(PubItem itemToCheck) {
    	if (itemToCheck==null)
    		return false;

    	Iterator<PubItemUsed> it = itemsBought.descendingIterator();
    	
    	while(it.hasNext()) {
    		PubItemUsed item = it.next();
    		if (!item.getItem().getClass().equals(itemToCheck.getClass()))
    			continue;

    		if (itemToCheck.hasDuration() && itemToCheck.getDuration().getSeconds()!=-1) {
	    		int duration = itemToCheck.getDuration().getSeconds();
	    		if (duration*1000 > System.currentTimeMillis()-item.getTime()) {
	    			return true;
	    		} else {
	    			return false;
	    		}
    		}
    	}
    	return false;
    }
    
    private void resetItems() {
    	this.itemsBoughtThisLife.clear();
    }

    public List<PubItemUsed> getItemsBought() {
    	return itemsBought;
    }
    
    public List<PubItem> getItemsBoughtThisLife() {
    	return itemsBoughtThisLife;
    }
    
    public void resetShipItem() {
    	shipItem = null;
    	deathsOnShipItem = 0;
    }
    
    public void savedState() {
    	this.lastSavedState = System.currentTimeMillis();
    }
    
    public boolean isOnSpec() {
    	return ((int)m_botAction.getPlayer(name).getShipType()) == 0;
    }
    
    public boolean isOnline() {
    	return isOnline;
    }
    
    public void setIsOnline(boolean b) {
    	this.isOnline = b;
    }
    
    public void handleShipChange(FrequencyShipChange event) {
    	resetItems();
    	if (shipItem != null && event.getShipType() != shipItem.getShipNumber())
    		resetShipItem();
    }

    public void handleDeath(PlayerDeath event) {
    	resetItems();
    	if (shipItem != null) {
    		deathsOnShipItem++;
    	}
    }
    
    public int getDeathsOnShipItem() {
    	return deathsOnShipItem;
    }
    
    public boolean hasShipItem() {
    	return shipItem != null;
    }
    
    public void setShipItem(PubShipItem item) {
    	this.shipItem = item;
    }
    
    public long getLastMoneyUpdate() {
    	return lastMoneyUpdate;
    }
    
    public long getLastOptionsUpdate() {
    	return lastOptionsUpdate;
    }
    
    public long getLastSavedState() {
    	return lastSavedState;
    }

    @Override
	public boolean equals(Object obj) {
		return name.equals(((PubPlayer)obj).name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
    public int compareTo(PubPlayer o) {
        // TODO Auto-generated method stub
        if(o.getMoney() > getMoney()) return 1;
        if(o.getMoney() < getMoney()) return 0;
        
        return -1;
    }

}
