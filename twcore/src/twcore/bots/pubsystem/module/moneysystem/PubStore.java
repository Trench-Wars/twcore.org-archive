package twcore.bots.pubsystem.module.moneysystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.module.moneysystem.item.PubCommandItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubItemDuration;
import twcore.bots.pubsystem.module.moneysystem.item.PubItemRestriction;
import twcore.bots.pubsystem.module.moneysystem.item.PubPrizeItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubShipItem;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.bots.pubsystem.util.PubException;
import twcore.core.BotAction;
import twcore.core.util.Tools;

public class PubStore {
	
	private BotAction m_botAction;
	private PubContext context;

    private LinkedHashMap<String, PubItem> items;
    
    private boolean opened = true;

    public PubStore(BotAction botAction, PubContext context) {
    	this.m_botAction = botAction;
    	this.context = context;
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
        
        this.items = new LinkedHashMap<String, PubItem>();
        
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
	    			List<Integer> prizes = new ArrayList<Integer>();
	    			if (data[4].trim().startsWith("{")) {
	    				String[] split = data[4].trim().substring(1, data[4].trim().length()-1).split(";");
	    				for(String prize: split) {
	    					prizes.add(Integer.parseInt(prize));
	    				}
	    			}
	    			else {
	    				prizes.add(Integer.parseInt(data[4].trim()));
	    			}
	    			item = new PubPrizeItem(data[0].trim(), data[1].trim(), data[2].trim(), Integer.parseInt(data[3].trim()), prizes);
	    			optionPointer = 5;
	    		} 
	    		else if ("item_ship".equals(type)) {
	    			item = new PubShipItem(data[0].trim(), data[1].trim(), data[2].trim(),Integer.parseInt(data[3].trim()), Integer.parseInt(data[4].trim()));
	    			optionPointer = 5;
	    		}
	    		else if ("item_command".equals(type)) {
	    			item = new PubCommandItem(data[0].trim(), data[1].trim(), data[2].trim(), Integer.parseInt(data[3].trim()), data[4]);
	    			optionPointer = 5;
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
	    				if(option.startsWith("!s") && option.trim().length()==3) {
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
	    				} else if(option.startsWith("!ds")) {
	    					int seconds = Integer.parseInt(option.substring(3));
	    					d.setSeconds(seconds);
	    					hasDuration = true;
	    				} else if(option.startsWith("!abbv")) {
	    					String abbv = option.substring(6);
	    					item.addAbbreviation(abbv);
	    				} else if(option.startsWith("!ri")) {
	    					String itemName = option.substring(4);
	    					r.addItemNotSameTime(itemName);
	    					hasRestriction = true;
	    				} else if(option.startsWith("!player")) {
	    					item.setPlayerOptional(true);
	    				} else if(option.startsWith("!strictplayer")) {
	    					item.setPlayerStrict(true);
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
    
    public PubItem buy(String itemName, PubPlayer buyer, String params) throws PubException {

    	if (!opened)
    		throw new PubException("The store is closed!");
    	
    	PubPlayer player = buyer;
        PubItem item = getItem(itemName);
        
        if (item == null)
        	throw new PubException("This item does not exist.");
        
        if (item.isPlayerStrict() || (item.isPlayerOptional() && !params.trim().isEmpty())) {
        	player = context.getPlayerManager().getPlayer(params.trim());
        	if (item.isPlayerStrict() && params.isEmpty()) {
        		throw new PubException("You must specify a player name for this item (!buy " + itemName + ":PlayerName).");
        	}
        	if (player == null)
        		throw new PubException("Player '" + params.trim()+ "' not found.");
        	
        	if (player.getPlayerName().equals(buyer.getPlayerName()))
        		throw new PubException("You cannot specify your own name.");

        	if (context.getPubChallenge().isDueling(player.getPlayerName()))
        		throw new PubException("'" + params.trim()+ "' is currently dueling. You cannot buy an item for this player.");
        }
        
        if (item.isRestricted()) {
        	PubItemRestriction restriction = item.getRestriction();
        	restriction.check(item, buyer, m_botAction.getPlayer(player.getPlayerName()).getShipType());
        
        	// Let's do another check
        	List<String> itemNotSameTime = restriction.getItemNotSameTime();
        	if (!itemNotSameTime.isEmpty()) {
        		for(String name: itemNotSameTime) {
        			PubItem itemToCheck = getItem(name);
        			if (player.hasItemActive(itemToCheck)) {
        				throw new PubException("You cannot buy this item while you have '" + itemToCheck.getName() + "' active.");
        			}
        		}
        	}
        }

        if (buyer.getMoney() < item.getPrice())
        	throw new PubException("You do not have enough money to buy this item.");
        
        if (item != null) {
        	buyer.setMoney(buyer.getMoney() - item.getPrice());
        	player.addItem(item);
        }
        
        if (buyer != player) {
        	m_botAction.sendPrivateMessage(player.getPlayerName(), buyer.getPlayerName() + " has bought you '" + item.getName() + "' for $" + item.getPrice() + ".");
        	m_botAction.sendPrivateMessage(buyer.getPlayerName(), player.getPlayerName() + " has received the item '" + item.getName() + "'.");
        }
        
        item.hasBeenBought();

        return item;
    }
    
    public void addItem(PubItem item, String itemName) {
    	items.put(itemName.toLowerCase(), item);
    	for(String abbv: item.getAbbreviations()) {
    		items.put(abbv.toLowerCase(), item);
    	}
    }
    
    public PubItem getItem(String itemName) {
    	return items.get(itemName.toLowerCase());
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
    
    public void reloadConfig() {
    	initializeStore();
    }
}
