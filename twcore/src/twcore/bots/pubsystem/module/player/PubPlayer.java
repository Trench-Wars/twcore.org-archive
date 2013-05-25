package twcore.bots.pubsystem.module.player;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import twcore.bots.pubsystem.module.PubUtilModule.Location;
//import twcore.bots.pubsystem.module.PubUtilModule.Tileset;
import twcore.bots.pubsystem.module.moneysystem.LvzMoneyPanel;
import twcore.bots.pubsystem.module.moneysystem.item.PubItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubItemUsed;
//import twcore.bots.pubsystem.module.moneysystem.item.PubPrizeItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubShipItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubCommandItem;
import twcore.core.BotAction;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerDeath;
//import twcore.core.game.Player;
import twcore.core.util.Tools;


public class PubPlayer implements Comparable<PubPlayer>{
    
	private static final int MAX_ITEM_USED_HISTORY = 30 * Tools.TimeInMillis.MINUTE;
    private static final int DONATE_DELAY = Tools.TimeInMillis.MINUTE;
	
	private BotAction m_botAction;

	//private Tileset tileset = Tileset.MONOLITH;
	
    private String name;
    private int money;
    private LinkedList<PubItemUsed> itemsBought;
    private LinkedList<PubItemUsed> itemsBoughtForOther;
    private LinkedList<PubItem> itemsBoughtThisLife;
    private LinkedList<String> ignoreList;
    private TreeMap<String, Long> donated;
    
    private LvzMoneyPanel cashPanel;
    
    // A player can only have 1 ShipItem at a time
    private PubShipItem shipItem;
    private int deathsOnShipItem = 0;
    
    // Epoch time
    private long lastBigItemUsed = 0;
    private long lastMoneyUpdate = System.currentTimeMillis();
    private long lastMoneySavedState = System.currentTimeMillis();
    private long lastSavedState = System.currentTimeMillis();
    private long lastOptionsUpdate = System.currentTimeMillis();
    private long lastDeath = 0;
    private long lastAttach = 0;
    private long lastThor = 0;
    
    // Stats
    private int bestStreak = 0;
    
    // History of the last kill
	private int lastKillShipKiller = -1;
	private int lastKillShipKilled = -1;
	private Location lastKillLocation;
	private String lastKillKilledName;
	private boolean lastKillWithFlag;
    private boolean notifiedAboutEZ = false; 
	
	public static int EZ_PENALTY = 100;

    public PubPlayer(BotAction m_botAction, String name) {
    	this(m_botAction, name, 0);
    }
    
    public PubPlayer(BotAction m_botAction, String name, int money) {
    	this.m_botAction = m_botAction;
        this.name = name;
        this.money = money;
        this.itemsBought = new LinkedList<PubItemUsed>();
        this.itemsBoughtForOther = new LinkedList<PubItemUsed>();
        this.itemsBoughtThisLife = new LinkedList<PubItem>();
        this.cashPanel = new LvzMoneyPanel(m_botAction);
        this.ignoreList = new LinkedList<String>();
        this.donated = new TreeMap<String, Long>(String.CASE_INSENSITIVE_ORDER);
        reloadPanel(false);
    }

    public void setName(String name) {
    	this.name = name;
    }
    
    public boolean donate(String name) {
        long now = System.currentTimeMillis();
        if (donated.containsKey(name)) {
            if (now - donated.get(name) > DONATE_DELAY) {
                donated.put(name, now);
                return true;
            } else
                return false;
        } else {
            donated.put(name, now);
            return true;
        }
    }
    
    public String getPlayerName() {
        return name;
    }
    
    /*
    public Tileset getTileset() {
    	return tileset;
    }
    
    public void setTileset(Tileset tileset) {
    	if (!this.tileset.equals(tileset)) {
    		lastOptionsUpdate = System.currentTimeMillis();
    	}
    	this.tileset = tileset;
    }
    */

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
    	money = Math.abs(money);
    	setMoney(this.money+money);
    }
    
    public void removeMoney(int money) {
    	money = Math.abs(money);
    	setMoney(this.money-money);
    }

    public void addItem(PubItem item, String param) {
    	purgeItemBoughtHistory();
    	if (item instanceof PubCommandItem)
    	    lastBigItemUsed = System.currentTimeMillis();
    	
        this.itemsBought.add(new PubItemUsed(item));
        this.itemsBoughtThisLife.add(item);
    }

    public void addItemForOther(PubItem item, String param) {
        purgeItemBoughtForOtherHistory();
        this.itemsBoughtForOther.add(new PubItemUsed(item));
    }
    
    public void addDeath() {
    	this.lastDeath = System.currentTimeMillis();
    }
    
    public long getLastDeath() {
    	return lastDeath;
    }
    
    public long getLastAttach() {
    	return lastAttach;
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
    
    private void purgeItemBoughtForOtherHistory() 
    {
        Iterator<PubItemUsed> it = itemsBoughtForOther.iterator();
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
    		if (!item.getItem().getName().equals(itemToCheck.getName()))
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

    public List<PubItemUsed> getItemsBoughtForOther() {
        return itemsBoughtForOther;
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
    
    public void moneySavedState() {
    	this.lastMoneySavedState = System.currentTimeMillis();
    }
    
    public boolean isOnSpec() {
    	return ((int)m_botAction.getPlayer(name).getShipType()) == 0;
    }

    public void handleShipChange(FrequencyShipChange event) {
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
    
    public PubShipItem getShipItem() {
    	return shipItem;
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
    
    public long getLastMoneySavedState() {
    	return lastMoneySavedState;
    }
    
    public long getLastSavedState() {
    	return lastSavedState;
    }
    
    public long getLastThorUsed() { 
        return lastThor;
    }
    
    public void setLastThorUsed(long time) {
        lastThor = time;
    }
    
    public long getLastBigItemUsed() {
        return lastBigItemUsed;
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

	public void handleAttach() {
		this.lastAttach = System.currentTimeMillis();
	}

	public int getLastKillKillerShip() {
		return lastKillShipKiller;
	}
	
	public int getLastKillKilledShip() {
		return lastKillShipKilled;
	}
	
	public Location getLastKillLocation() {
		return lastKillLocation;
	}
	
	public String getLastKillKilledName() {
		return lastKillKilledName;
	}
	
	public boolean getLastKillWithFlag() {
		return lastKillWithFlag;
	}
	
	public void setLastKillShips(int killer, int killed) {
		this.lastKillShipKiller = killer;
		this.lastKillShipKilled = killed;
	}

	public void setLastKillLocation(Location location) {
		this.lastKillLocation = location;
	}

	public void setLastKillKilledName(String playerName) {
		this.lastKillKilledName = playerName;
	}

	public void setLastKillWithFlag(boolean withFlag) {
		this.lastKillWithFlag = withFlag;
	}
	
	public void ignorePlayer(String player) {
	    if (!ignoreList.contains(player.toLowerCase())) {
	        ignoreList.add(player.toLowerCase());
	        m_botAction.sendPrivateMessage(this.name, "" + player + " has been added to your ignore list.");
	    } else {
	        ignoreList.remove(player.toLowerCase());
            m_botAction.sendPrivateMessage(this.name, "" + player + " has been removed from your ignore list.");
	    }
	}
	
	public boolean isIgnored(String player) {
	    if (ignoreList.contains(player.toLowerCase()))
	        return true;
	    else return false;
	}
	
	public void getIgnores() {
	    String msg = "Ignoring: ";
	    for (String i : ignoreList)
	        msg += i + ", ";
	    msg = msg.substring(0, msg.length() - 2);
	    m_botAction.sendPrivateMessage(name, msg);
	}
	
	public void ezPenalty( boolean killer ) {
        if( killer ) {
            removeMoney( EZ_PENALTY );
        } else {
            addMoney( EZ_PENALTY );            
        }
	    if( notifiedAboutEZ == false ) {
	        if( killer ) {
	            m_botAction.sendPrivateMessage( name, "[SPORTSMANSHIP FINE]  They were that easy? You won't mind donating $" + EZ_PENALTY + " to help them out, then.  [-" + EZ_PENALTY + "/'ez'; msg will not repeat]" );
	        } else {
                m_botAction.sendPrivateMessage( name, "You have been given +$" + EZ_PENALTY + " by your killer.  [-" + EZ_PENALTY + "/'ez'; msg will not repeat]" );
	        }
	        notifiedAboutEZ = true;
	    }
	}

}
