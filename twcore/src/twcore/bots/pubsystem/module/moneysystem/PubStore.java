package twcore.bots.pubsystem.module.moneysystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.module.moneysystem.item.PubCommandItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubItemDuration;
import twcore.bots.pubsystem.module.moneysystem.item.PubItemRestriction;
import twcore.bots.pubsystem.module.moneysystem.item.PubPrizeItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubShipItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubShipUpgradeItem;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.bots.pubsystem.util.PubException;
import twcore.core.BotAction;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubStore {
	
	private BotAction m_botAction;
	private PubContext context;
	
	private int commandCooldown = 0;
	private int prizecooldown= 0;
	private int detachLevTerrCooldown = 0;
	private boolean buyableFromLevTerr = true;
	
	private int loyaltyTime = 120 * Tools.TimeInMillis.SECOND;
	
	private boolean opened = true;
    private LinkedHashMap<String, PubItem> items;
    private ArrayList<PubItem> roundRestrictedItems;
    
    // Immunity list
    private HashMap<String, Long> immunity;
    

    public PubStore(BotAction botAction, PubContext context) {
    	this.m_botAction = botAction;
    	this.context = context;
    	this.immunity = new HashMap<String, Long>();
        this.items = new LinkedHashMap<String, PubItem>();
        this.roundRestrictedItems = new ArrayList<PubItem>();
    }
    
    private void initializeStore() {
    	
        if (m_botAction.getBotSettings().getInt("store_enabled")==0)
        	turnOff();
        
        this.items = new LinkedHashMap<String, PubItem>();
        this.roundRestrictedItems = new ArrayList<PubItem>();
        
        commandCooldown = m_botAction.getBotSettings().getInt("command_cd");
        prizecooldown = m_botAction.getBotSettings().getInt("prize_cd");
        buyableFromLevTerr = (m_botAction.getBotSettings().getInt("buy_from_levterr") == 1);
        detachLevTerrCooldown = m_botAction.getBotSettings().getInt("levterr_detach_cd");
        loyaltyTime = m_botAction.getBotSettings().getInt("loyalty_time") * Tools.TimeInMillis.SECOND;

        String[] itemTypes = { "item_prize" , "item_ship_upgrade" , "item_ship" , "item_command" };
        for(String type: itemTypes) {
        	String ti = "" + type;// + (m_botAction.getBotName().endsWith("1") ? "" : "+");
	    	String[] items = m_botAction.getBotSettings().getString(ti).split(",");
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
	    		else if ("item_ship_upgrade".equals(type)) {
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
	    			item = new PubShipUpgradeItem(data[0].trim(), data[1].trim(), data[2].trim(),Integer.parseInt(data[3].trim()), prizes);
	    			optionPointer = 5;
	    		}
	    		else if ("item_ship".equals(type)) {
	    			item = new PubShipItem(data[0].trim(), data[1].trim(), data[2].trim(),Integer.parseInt(data[3].trim()), Integer.parseInt(data[4].trim()));
	    			optionPointer = 5;
	    		}    
	    		else if ("item_command".equals(type)) {
	    			item = new PubCommandItem(data[0].trim(), data[1].trim(), data[2].trim(), Integer.parseInt(data[3].trim()), data[4]);
	    			optionPointer = 4;
	    		}

	    		// Options?
	    		if (data.length > optionPointer) {
	    			
	    			PubItemRestriction r = new PubItemRestriction();
	    			PubItemDuration d = new PubItemDuration();
	    			
	    			boolean hasRestriction = false;
	    			boolean hasDuration = false;
	    			
	    			if (prizecooldown != 0) { 
	    			    r.setGobalCooldownBuy(prizecooldown);
	    			    hasRestriction = true;
	    			}
	    			if (buyableFromLevTerr) {
	    			    r.setbuyableFromLevTerr();
	    			    hasRestriction = true;
	    			} else if (!buyableFromLevTerr && detachLevTerrCooldown != 0) {
	    			    r.setDetachLevTerrCooldown(detachLevTerrCooldown);
	    			    hasRestriction = true;
	    			}
	    			
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
	    				} else if(option.startsWith("!dsm")) {
	    					int max = Integer.parseInt(option.substring(4));
	    					r.setMaxPerSecond(max);
	    					hasRestriction = true;
	    				} else if (option.startsWith("!fdm")) {
	    				    int max = Integer.parseInt(option.substring(4));
	    				    r.setFreqPerMinute(max);
	    				    hasRestriction = true;
	    				} else if (option.startsWith("!fdr")) {
	    				    int max = Integer.parseInt(option.substring(4));
	    				    r.setFreqPerRound(max);
	    				    if(item != null)
	    				        roundRestrictedItems.add(item);
	    				    hasRestriction = true;
	    				} else if (option.startsWith("!pf")) {
	    				    r.setPublicFreqOnly();
	    				    hasRestriction = true;
	    				} else if (option.startsWith("!fit")) {
	    				    int t = Integer.parseInt(option.substring(4));
	    				    item.setImmuneTime(t);
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
	    				} else if(option.startsWith("!hidden")) {
	    					item.setHidden();
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
                        } else if(option.startsWith("!bbimmune")) {
                            item.setBuyBlockImmune(true);
                        } else if(option.startsWith("!noendround")) {
                            item.setEndRoundBlocked(true);
	    				} else if(option.startsWith("!prizesec")) {
	    					int seconds = Integer.parseInt(option.substring(9));
	    					if (item instanceof PubPrizeItem) {
	    						((PubPrizeItem)item).setPrizeSeconds(seconds);
	    					}
	    				}
	    			}
	    			
	    			if (hasRestriction)
	    				item.setRestriction(r);
	    			if (hasDuration)
	    				item.setDuration(d);

	    		}
	    		
	    		addItem(item, data[0]);
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
        
        if (item instanceof PubCommandItem) {
            // Command item cooldown
            long timePassed = System.currentTimeMillis() - player.getLastBigItemUsed(); 
            if (timePassed < commandCooldown * Tools.TimeInMillis.SECOND) {
                timePassed = commandCooldown - (timePassed / 1000);
                throw new PubException("You must wait at least " + timePassed + " seconds before buying another special item.");
            }
          
            timePassed = System.currentTimeMillis() - player.getLastFreqSwitch();
            if (timePassed < loyaltyTime) {
                timePassed = (loyaltyTime - timePassed) / 1000;
                throw new PubException("You must stay at least " + timePassed + " more seconds in your freq before you can buy a special item.");
            }
        }
        
        if (item.isPlayerStrict() || (item.isPlayerOptional() && !params.trim().isEmpty())) {
        	
        	Player receiver = m_botAction.getPlayer(params.trim());
        	if (receiver == null) {
        		if (item.isPlayerStrict() && params.trim().equals(""))
        			throw new PubException("You must specify a player name (!buy " + item.getName() + ":PlayerA).");
        		else
        			throw new PubException("Player '" + params.trim()+ "' not found.");
        	}
        	
        	player = context.getPlayerManager().getPlayer(receiver.getPlayerName());
        	if (item.isPlayerStrict() && params.isEmpty()) {
        		throw new PubException("You must specify a player name for this item (!buy " + itemName + ":PlayerA).");
        	}
        	
        	if (item.isPlayerStrict() && immunity.containsKey(receiver.getPlayerName()))
        		throw new PubException(receiver.getPlayerName()+ " has an immunity.");
        	
        	if (player == null)
        		throw new PubException("Player '" + params.trim()+ "' not found.");

        	if (player.getPlayerName().equals(buyer.getPlayerName()))
        		throw new PubException("You cannot specify your own name.");
        	
        	Player p = m_botAction.getPlayer(player.getPlayerName());
        	if (!p.isPlaying()) {
        		throw new PubException("You cannot buy an item for a spectator.");
        	}

        	if (context.getPubChallenge().isDueling(player.getPlayerName()))
        		throw new PubException("'" + player.getPlayerName() + "' is currently dueling. You cannot buy an item for this player.");
        }
        
        if (item.isRestricted()) {
            //TODO: Properly fix this when an item is bought for someone else, in regard to which checks are done.
        	PubItemRestriction restriction = item.getRestriction();
        	restriction.check(item, buyer, m_botAction.getPlayer(player.getPlayerName()).getShipType(), m_botAction.getPlayer(player.getPlayerName()).getFrequency());
        
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
        	
            restriction.checkForOther(item, buyer, m_botAction.getPlayer(player.getPlayerName()).getShipType());
        }
        
        if (buyer.getMoney() < item.getPrice())
        	throw new PubException("You do not have enough money to buy this item.");
        
        if (item != null) {
        	buyer.setMoney(buyer.getMoney() - item.getPrice());
        	player.addItem(item, params);
        }
        
        if (buyer != player) {   
            buyer.addItemForOther(item, params);         
            
        	m_botAction.sendSmartPrivateMessage(player.getPlayerName(), buyer.getPlayerName() + " has bought you '" + item.getName() + "' for $" + item.getPrice() + ".");
        	m_botAction.sendSmartPrivateMessage(buyer.getPlayerName(), player.getPlayerName() + " has received the item '" + item.getName() + "'.");
        }
        
        item.hasBeenBought();
        if (item.isRestricted()) {
            item.getRestriction().freqUsing(m_botAction.getPlayer(player.getPlayerName()).getFrequency());
            item.getRestriction().addFreqUsedRound(m_botAction.getPlayer(player.getPlayerName()).getFrequency());
        }
        
        return item;
    }
    
    
    public void addImmunity(String playerName) {
    	immunity.put(playerName, System.currentTimeMillis());
    }
    
    public void removeImmunity(String playerName) {
    	immunity.remove(playerName);
    }
    
    public boolean hasImmunity(String playerName) {
    	return immunity.containsKey(playerName);
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
    
    public int getFreqImmuneTime() {
        if (items.containsKey("sphere"))
            return items.get("sphere").getImmuneTime();
        else
            return -1;
    }
    
    /*
     * Clears the tracking lists of items that have limited buys per freq per round.
     */
    public void resetRoundRestrictedItems() {
        if(roundRestrictedItems.isEmpty())
            return;
        
        for(PubItem item : roundRestrictedItems) {
            item.getRestriction().resetFreqRoundUses();
        }
    }
}
