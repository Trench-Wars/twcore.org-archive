package twcore.bots.pubsystem;

import java.util.LinkedHashMap;

import twcore.bots.pubsystem.item.PubCommandItem;
import twcore.bots.pubsystem.item.PubItem;
import twcore.bots.pubsystem.item.PubItemDuration;
import twcore.bots.pubsystem.item.PubItemRestriction;
import twcore.bots.pubsystem.item.PubPrizeItem;
import twcore.bots.pubsystem.item.PubShipItem;
import twcore.core.BotAction;
import twcore.core.util.Tools;

public class PubStore {
	
	private BotAction m_botAction;

    private LinkedHashMap<String, PubItem> items;
    
    private boolean opened = true;

    public PubStore(BotAction botAction) {
    	this.m_botAction = botAction;
        this.items = new LinkedHashMap<String, PubItem>();
        try {
        	initializeStore();
        } catch (Exception e) {
			Tools.printStackTrace("Error while initializing the store", e);
		}
    }
    
    private void initializeStore() {
    	
        if (m_botAction.getBotSettings().getInt("store_enabled")==0) {
        	turnOff();
    	}
        
        String[] itemTypes = { "item_prize", "item_ship", "item_command" };
        for(String type: itemTypes) {
        	
	    	String[] items = m_botAction.getBotSettings().getString(type).split(",");
	    	for(String number: items) {
	    		
	    		String[] data = m_botAction.getBotSettings().getString(type+number).split(",");
	    		
	    		if (data.length<=1)
	    			continue;
	    		
	    		int optionPointer = 0;
	    		PubItem item = null;
	    		if ("item_prize".equals(type)) {
	    			item = new PubPrizeItem(data[0], data[1], Integer.parseInt(data[2]), Integer.parseInt(data[3]));
	    			optionPointer = 4;
	    		} 
	    		else if ("item_ship".equals(type)) {
	    			item = new PubShipItem(data[0], data[1], Integer.parseInt(data[2]), Integer.parseInt(data[3]));
	    			optionPointer = 4;
	    		}
	    		else if ("item_command".equals(type)) {
	    			item = new PubCommandItem(data[0], data[1], Integer.parseInt(data[2]), data[3]);
	    			optionPointer = 4;
	    		}
	    		addItem(item, data[0]);
	
	    		// Options?
	    		if (data.length > optionPointer) {
	    			
	    			PubItemRestriction r = new PubItemRestriction();
	    			PubItemDuration d = new PubItemDuration();
	    			
	    			boolean hasRestriction = false;
	    			boolean hasDuration = false;
	    			
	    			for(int i=optionPointer; i<data.length; i++) {
	    				String option = data[i];
	    				if(option.startsWith("!s")) {
	    					int ship = Integer.parseInt(option.substring(2));
	    					r.addShip(ship);
	    					hasRestriction = true;
	    				} else if(option.startsWith("!mp")) {
	    					int max = Integer.parseInt(option.substring(3));
	    					r.setMaxPerLife(max);
	    					hasRestriction = true;
	    				} else if(option.startsWith("!mc")) {
	    					int max = Integer.parseInt(option.substring(3));
	    					r.setMaxConsecutive(max);
	    					hasRestriction = true;
	    				} else if(option.startsWith("!adm")) {
	    					int max = Integer.parseInt(option.substring(4));
	    					r.setMaxArenaPerMinute(max);
	    					hasRestriction = true;
	    				} else if(option.startsWith("!arena")) {
	    					item.setArenaItem(true);
	    				} else if(option.startsWith("!fromspec")) {
	    					r.buyableFromSpec(true);
	    				} else if(option.startsWith("!dd")) {
	    					int death = Integer.parseInt(option.substring(3));
	    					d.setDeaths(death);
	    					hasDuration = true;
	    				} else if(option.startsWith("!dm")) {
	    					int minutes = Integer.parseInt(option.substring(3));
	    					d.setMinutes(minutes);
	    					hasDuration = true;
	    				} else if(option.startsWith("!abbv")) {
	    					String abbv = option.substring(6);
	    					item.addAbbreviation(abbv);
	    				}
	    			}
	    			
	    			if (hasRestriction)
	    				item.setRestriction(r);
	    			if (hasDuration)
	    				item.setDuration(d);
	    		}
	    	}
        }
        
    }
    
    public PubItem buy(String itemName, PubPlayer player, int shipType) throws PubException {

    	if (!opened)
    		throw new PubException("The store is closed!");
    	
        PubItem item = items.get(itemName);

        if (item == null)
        	throw new PubException("This item does not exist.");
        
        if (item.isRestricted()) {
        	PubItemRestriction restriction = item.getRestriction();
        	restriction.check(item, player, shipType);
        }

        if (player.getMoney() < item.getPrice())
        	throw new PubException("You do not have enough money to buy this item.");
        
        if (item != null) {
	        player.setMoney(player.getMoney() - item.getPrice());
	        player.addItem(item);
        }
        
        item.hasBeenBought();

        return item;
    }
    
    public void addItem(PubItem item, String itemName) {
    	items.put(itemName, item);
    	for(String abbv: item.getAbbreviations()) {
    		items.put(abbv, item);
    	}
    }
    
    public LinkedHashMap<String, PubItem> getItems() {
    	return items;
    }
    
    public void turnOn() {
    	this.opened = true;
    }
    
    public void turnOff() {
    	this.opened = false;
    }
    
    public boolean isOpened() {
    	return opened;
    }
}
