package twcore.bots.pubsystem.module;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.Map.Entry;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.PubUtilModule.Location;
import twcore.bots.pubsystem.module.moneysystem.CouponCode;
import twcore.bots.pubsystem.module.moneysystem.LvzMoneyPanel;
import twcore.bots.pubsystem.module.moneysystem.PubStore;
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
import twcore.core.EventRequester;
import twcore.core.events.FrequencyChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.events.SQLResultEvent;
import twcore.core.events.TurretEvent;
import twcore.core.events.WeaponFired;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubMoneySystemModule extends AbstractModule {

	private PubStore store;
	
	private PubPlayerManagerModule playerManager;
    
    private Map<PubPlayer, PubItemDuration> playersWithDurationItem;
    
    // These variables are used to calculate the money earned for a kill
    private Map<Integer, Integer> shipKillerPoints;
    private Map<Integer, Integer> shipKilledPoints;
    
    // Money earned by location
    private Map<Location, Integer> locationPoints;
    
    // Time passed on the same frequency
    private HashMap<String, Long> frequencyTimes;
    
    // Coupon system
    private HashSet<String> couponOperators;
    private HashMap<String,CouponCode> coupons; // cache system
    
    private String database;
    

    public PubMoneySystemModule(BotAction botAction, PubContext context) {
    	
    	super(botAction, context, "Money/Store");
    	
    	this.playerManager = context.getPlayerManager();

        this.playersWithDurationItem = new HashMap<PubPlayer, PubItemDuration>();
        this.shipKillerPoints = new HashMap<Integer, Integer>();
        this.shipKilledPoints = new HashMap<Integer, Integer>();
        this.locationPoints = new HashMap<Location, Integer>();
        this.frequencyTimes = new HashMap<String, Long>();
        
        this.coupons = new HashMap<String,CouponCode>();

        try {
        	this.store = new PubStore(m_botAction, context);
        	initializePoints();
	    } catch (Exception e) {
	    	Tools.printStackTrace("Error while initializing the money system", e);
		}

	    reloadConfig();
    	
    }
    
    
	public void requestEvents(EventRequester eventRequester)
	{
		eventRequester.request(EventRequester.PLAYER_DEATH);
	}
    
    /**
     * Gets default settings for the points: area and ship
     * By points, we mean "money".
     * */
    private void initializePoints(){
        
    	String[] locations = m_botAction.getBotSettings().getString("point_location").split(",");
    	for(String loc: locations) {
    		String[] split =  m_botAction.getBotSettings().getString("point_location" + loc).split(",");
    		Location location = Location.valueOf(split[0].toUpperCase());
    		locationPoints.put(location, Integer.parseInt(split[1]));
    	}
    	
        String[] pointsKiller =  m_botAction.getBotSettings().getString("point_killer").split(",");
        String[] pointsKilled =  m_botAction.getBotSettings().getString("point_killed").split(",");
        
        for(int i=1; i<=8; i++) {
        	shipKillerPoints.put(i, Integer.parseInt(pointsKiller[i-1]));
        	shipKilledPoints.put(i, Integer.parseInt(pointsKilled[i-1]));
        }

    }
    

    private void buyItem(final String playerName, String itemName, String params){
    	
        try{

            if (playerManager.isPlayerExists(playerName)){
            	
            	// Wait, is this player dueling?
            	if (context.getPubChallenge().isDueling(playerName)) {
            		m_botAction.sendSmartPrivateMessage(playerName, "You cannot buy an item while dueling.");
            		return;
            	}
            	
            	PubPlayer buyer = playerManager.getPlayer(playerName);
            	final PubItem item = store.buy(itemName, buyer, params);
            	final PubPlayer receiver;

            	// Is it an item bought for someone else?
            	// If yes, change the receiver for this player and not the buyer
                if (item.isPlayerStrict() || (item.isPlayerOptional() && !params.trim().isEmpty())) {
                	receiver = context.getPlayerManager().getPlayer(params.trim());
                } else {
                	receiver = buyer;
                }
            	
                // Execute the item!!
                executeItem(item, receiver, params);
                
                // Tell the world?
                if (item.isArenaItem()) {
                	 m_botAction.sendArenaMessage(playerName + " just bought a " + item.getDisplayName() + " for $" + item.getPrice() + ".",21);
                }
                
                // Save this purchase
        		int shipType = m_botAction.getPlayer(receiver.getPlayerName()).getShipType();
        		// The query will be closed by PlayerManagerModule
        		if (database!=null)
        		m_botAction.SQLBackgroundQuery(database, "", "INSERT INTO tblPurchaseHistory "
    				+ "(fcItemName, fcBuyerName, fcReceiverName, fcArguments, fnPrice, fnReceiverShipType, fdDate) "
    				+ "VALUES ('"+Tools.addSlashes(item.getName())+"','"+Tools.addSlashes(buyer.getPlayerName())+"','"+Tools.addSlashes(receiver.getPlayerName())+"','"+Tools.addSlashes(params)+"','"+item.getPrice()+"','"+shipType+"',NOW())");

            } 
            else {
                m_botAction.sendSmartPrivateMessage(playerName, "You're not in the system to use !buy.");
            }
            
        }
        catch(PubException e) {
        	 m_botAction.sendSmartPrivateMessage(playerName, e.getMessage());
        }
        catch(Exception e){
            Tools.printStackTrace(e);
        }
        
    }
    
    public void executeItem(final PubItem item, final PubPlayer receiver, final String params) {
    	
    	Player player = m_botAction.getPlayer(receiver.getPlayerName());
    	
    	try {
    	
	    	// PRIZE ITEM
	        if (item instanceof PubPrizeItem) {
	        	List<Integer> prizes = ((PubPrizeItem) item).getPrizes();
	        	for(int prizeNumber: prizes) {
	        		m_botAction.specificPrize(receiver.getPlayerName(), prizeNumber);
	        	}
	        	if (item.hasDuration()) {
	            	final PubItemDuration duration = item.getDuration();
	            	m_botAction.sendSmartPrivateMessage(receiver.getPlayerName(), "You have " + duration.getSeconds() + " seconds to use your item.");
	            	if (duration.hasTime()) {
	            		TimerTask timer = new TimerTask() {
	                        public void run() {
	                        	Player player = m_botAction.getPlayer(receiver.getPlayerName());
	                        	if (player == null)
	                        		return;
	                        	int bounty = player.getBounty();
	                        	if (System.currentTimeMillis()-receiver.getLastDeath() > duration.getSeconds()*1000) {
	                            	m_botAction.sendUnfilteredPrivateMessage(receiver.getPlayerName(), "*shipreset");
	                            	m_botAction.giveBounty(receiver.getPlayerName(), bounty);
	                        	}
	                        	m_botAction.sendSmartPrivateMessage(receiver.getPlayerName(), "Item '" + item.getName() + "' lost.");
	                        }
	                    };
	                    m_botAction.scheduleTask(timer, duration.getSeconds()*1000);
	            	}
	        	}
	        }
	        
	    	// SHIP UPGRADE ITEM (same as PubPrizeItem)
	        if (item instanceof PubShipUpgradeItem) {
	        	List<Integer> prizes = ((PubShipUpgradeItem) item).getPrizes();
	        	for(int prizeNumber: prizes) {
	        		m_botAction.specificPrize(receiver.getPlayerName(), prizeNumber);
	        	}
	        	if (item.hasDuration()) {
	            	final PubItemDuration duration = item.getDuration();
	            	m_botAction.sendSmartPrivateMessage(receiver.getPlayerName(), "You have " + duration.getSeconds() + " seconds to use your item.");
	            	if (duration.hasTime()) {
	            		TimerTask timer = new TimerTask() {
	                        public void run() {
	                        	int bounty = m_botAction.getPlayer(receiver.getPlayerName()).getBounty();
	                        	if (System.currentTimeMillis()-receiver.getLastDeath() > duration.getSeconds()*1000) {
	                            	m_botAction.sendUnfilteredPrivateMessage(receiver.getPlayerName(), "*shipreset");
	                            	m_botAction.giveBounty(receiver.getPlayerName(), bounty);
	                        	}
	                        	m_botAction.sendSmartPrivateMessage(receiver.getPlayerName(), "Item '" + item.getName() + "' lost.");
	                        }
	                    };
	                    m_botAction.scheduleTask(timer, duration.getSeconds()*1000);
	            	}
	        	}
	        }
	        
	        // COMMAND ITEM
	        else if (item instanceof PubCommandItem) {
	        	String command = ((PubCommandItem)item).getCommand();
	    		Method method = this.getClass().getDeclaredMethod("itemCommand"+command, String.class, String.class);
	    		method.invoke(this, receiver.getPlayerName(), params);
	        } 
	        
	        // SHIP ITEM
	        else if (item instanceof PubShipItem) {
	        	
	            if (item.hasDuration()) {
	            	PubItemDuration duration = item.getDuration();
	            	if (duration.hasTime()) {
	            		final int currentShip = (int)player.getShipType();
	                	TimerTask timer = new TimerTask() {
	                        public void run() {
	                        	m_botAction.setShip(receiver.getPlayerName(), currentShip);
	                        }
	                    };
	                    m_botAction.scheduleTask(timer, duration.getSeconds()*1000);
	            	}
	            	else if (duration.hasDeaths()) {
	            		playersWithDurationItem.put(receiver, duration);
	            	}
	            }
	            
	            receiver.setShipItem((PubShipItem)item);
	            m_botAction.setShip(receiver.getPlayerName(), ((PubShipItem) item).getShipNumber());
	        	
	        } 
	        
    	} catch (Exception e) {
    		Tools.printStackTrace(e);
    	}
    	
    }
    
    private void doCmdDonate(String sender, String command) {
    	
    	if (command.length()<8) {
    		m_botAction.sendSmartPrivateMessage(sender, "Try !donate <name>.");
    		return;
    	}
    	
    	command = command.substring(8).trim();
    	if (command.contains(":")) {
    		String[] split = command.split("\\s*:\\s*");
    		String name = split[0];
    		String money = split[1];
    		
    		try {
    			Integer.valueOf(money);
    		} catch (NumberFormatException e) {
    			m_botAction.sendSmartPrivateMessage(sender, "You must specify a number. !donate playerA:1000");
    			return;
    		}
    		
    		if (context.getPubChallenge().isDueling(sender)) {
    			m_botAction.sendSmartPrivateMessage(sender, "You cannot donate while dueling.");
    			return;
    		}
    		
    		if (Integer.valueOf(money) < 0) {
    			m_botAction.sendSmartPrivateMessage(sender, "What are you trying to do here?");
    			return;
    		}
    		
    		if (Integer.valueOf(money) < 10) {
    			m_botAction.sendSmartPrivateMessage(sender, "You cannot donate for less than $10.");
    			return;
    		}
    		
    		/* Not needed
    		if (context.getPubChallenge().hasChallenged(sender)) {
    			m_botAction.sendSmartPrivateMessage(sender, "You cannot donate while challenging a player for a duel.");
    			return;
    		}
    		*/
    		
    		Player p = m_botAction.getFuzzyPlayer(name);
    		if (p == null) {
    			m_botAction.sendSmartPrivateMessage(sender, "Player not found.");
    			return;
    		}
    		name = p.getPlayerName();
    		
    		if (name.equals(sender)) {
    			m_botAction.sendSmartPrivateMessage(sender, "You cannot donate to yourself.");
    			return;
    		}

    		PubPlayer pubPlayer = playerManager.getPlayer(name,false);
    		PubPlayer pubPlayerDonater = playerManager.getPlayer(sender,false);
    		if (pubPlayer != null && pubPlayerDonater != null) {
    			
    			if (pubPlayerDonater.getMoney() < Integer.valueOf(money)) {
    				m_botAction.sendSmartPrivateMessage(sender, "You don't have $" + Integer.valueOf(money) + " to donate.");
    				return;
    			}
    			
    			int currentMoney = pubPlayer.getMoney();
    			int moneyToDonate = Integer.valueOf(money);
    			
    			pubPlayer.addMoney(moneyToDonate);
    			pubPlayerDonater.removeMoney(moneyToDonate);
    			m_botAction.sendSmartPrivateMessage(sender, "$" + moneyToDonate + " sent to " + pubPlayer.getPlayerName() + ".");
    			m_botAction.sendSmartPrivateMessage(pubPlayer.getPlayerName(), sender + " sent you $" + moneyToDonate + ", you have now $" + (moneyToDonate+currentMoney) + ".");

        		// The query will be closed by PlayerManagerModule
        		if (database!=null)
        		m_botAction.SQLBackgroundQuery(database, "", "INSERT INTO tblPlayerDonations "
    				+ "(fcName, fcNameTo, fnMoney, fdDate) "
    				+ "VALUES ('"+Tools.addSlashes(sender)+"','"+Tools.addSlashes(pubPlayer.getPlayerName())+"','"+moneyToDonate+"',NOW())");
        		
    			
    		} else {
    			m_botAction.sendSmartPrivateMessage(sender, "Player not found.");
    		}
    	}
    	else {
    		m_botAction.sendSmartPrivateMessage(sender, "Invalid argument");
    	}
    }
    
    public void doCmdAddMoney(String sender, String command) {
    	
    	command = command.substring(10).trim();
    	if (command.contains(":")) {
    		String[] split = command.split("\\s*:\\s*");
    		String name = split[0];
    		String money = split[1];
    		int moneyInt = Integer.valueOf(money);
    		PubPlayer pubPlayer = playerManager.getPlayer(name,false);
    		if (pubPlayer != null) {
    			int currentMoney = pubPlayer.getMoney();
    			if (moneyInt > 0)
    				pubPlayer.addMoney(moneyInt);
    			else
    				pubPlayer.removeMoney(moneyInt);
    			
    			m_botAction.sendSmartPrivateMessage(sender, pubPlayer.getPlayerName() + " has now $" + (currentMoney+Integer.valueOf(money)) + " (before: $" + currentMoney + ")");
    		
    		} else {
    			playerManager.addMoney(name, Integer.valueOf(money), true);
    			m_botAction.sendSmartPrivateMessage(sender, name + " has now $" + money + " more money.");
    		}
    	}
    	else {
    		m_botAction.sendSmartPrivateMessage(sender, "Invalid argument");
    	}
    }

    private void sendMoneyToPlayer(String playerName, int amount, String message) {
    	PubPlayer player = playerManager.getPlayer(playerName);
    	player.addMoney(amount);
        if (message!=null) {
        	m_botAction.sendSmartPrivateMessage(playerName, message);
        }
    }
    
    private void doCmdItems(String sender){
    	
        Player p = m_botAction.getPlayer(sender);
        
        ArrayList<String> lines = new ArrayList<String>();
        
        Class currentClass = this.getClass();
        
        if (!store.isOpened())
        {
        	lines.add("The store is closed, no items available.");
    	} 
        else 
    	{
        	
        	
        	
        	lines.add("List of items you can buy. Each item has a set of restrictions.");
	        for(String itemName: store.getItems().keySet()) {
	        	
	        	PubItem item = store.getItems().get(itemName);
	        	if (item.getAbbreviations().contains(itemName))
	        		continue;
	        	
	        	if (item instanceof PubPrizeItem) {
	        		if (!currentClass.equals(PubPrizeItem.class))
	        			lines.add("Prizes:");
	        		currentClass = PubPrizeItem.class;
	        	} else if (item instanceof PubShipUpgradeItem) {
	        		lines.add("");
	        		if (!currentClass.equals(PubShipUpgradeItem.class))
	        			lines.add("Ship Upgrades:");
	        		currentClass = PubShipUpgradeItem.class;
	        	} else if (item instanceof PubShipItem) {
	        		lines.add("");
	        		if (!currentClass.equals(PubShipItem.class))
	        			lines.add("Ships:");
	        		currentClass = PubShipItem.class;
	        	} else if (item instanceof PubCommandItem) {
	        		lines.add("");
	        		if (!currentClass.equals(PubCommandItem.class))
	        			lines.add("Specials:");
	        		currentClass = PubCommandItem.class;
	        	}

	        	String line = " !buy " + item.getName();
	        	if (item.isPlayerOptional()) {
	        		line += "*";
	        	} else if (item.isPlayerStrict()) {
	        		line += "**";
	        	}
		        line = Tools.formatString(line, 21);
	        	line += " -- " + (item.getDescription() + " ($" + item.getPrice() + ")");
	        	lines.add(line);
	        }
	        
	        lines.add("Legend: *Target optional **Target required (!buy item:PlayerName)");
	    	lines.add("Use !iteminfo <item> for more info about the specified item and its restrictions.");
	    	
	    } 

        m_botAction.smartPrivateMessageSpam(sender, lines.toArray(new String[lines.size()]));
        
    }

    private void doCmdBuy(String sender, String command) 
    {
        Player p = m_botAction.getPlayer(sender);
        if(p == null)
            return;
        if (!command.contains(" ")) {
        	doCmdItems(sender);
        	return;
        }
        command = command.substring(command.indexOf(" ")).trim();
        if (command.indexOf(":")!=-1) {
        	String params = command.substring(command.indexOf(":")+1).trim();
        	command = command.substring(0, command.indexOf(":")).trim();
        	buyItem(sender, command, params);
        } else {
        	buyItem(sender, command, "");
        }
    }
    
    private void doCmdDebugObj(String sender, String command) {
    	
    	m_botAction.sendSmartPrivateMessage(sender, "Average of " + LvzMoneyPanel.totalObjSentPerMinute() + " *obj sent per minute.");
    }
    
    private void doCmdBankrupt(String sender, String command) {
    	
    	String name = sender;
    	if (command.contains(" ")) {
    		name = command.substring(command.indexOf(" ")).trim();
			PubPlayer pubPlayer = playerManager.getPlayer(name,false);
			if (pubPlayer != null) {
				int money = pubPlayer.getMoney();
				pubPlayer.setMoney(0);
				m_botAction.sendSmartPrivateMessage(sender, pubPlayer.getPlayerName() + " has now $0 (before: $" + money + ")");
			} else {
				m_botAction.sendSmartPrivateMessage(sender, "Player '" + name + "' not found.");
			}
    	}
    }
    
    
    private void doCmdItemInfo(String sender, String command) 
    {
        Player p = m_botAction.getPlayer(sender);
        if(p == null)
            return;
        if (!command.contains(" ")) {
        	m_botAction.sendSmartPrivateMessage(sender, "You need to supply an item.");
        	return;
        }
        String itemName = command.substring(command.indexOf(" ")).trim();
        PubItem item = store.getItem(itemName);
        if (item == null) {
        	m_botAction.sendSmartPrivateMessage(sender, "Item '" + itemName + "' not found.");
        }
        else {
        	
        	m_botAction.sendSmartPrivateMessage(sender, "Item: " + item.getName() + " (" + item.getDescription() + ")");
        	m_botAction.sendSmartPrivateMessage(sender, "Price: $" + item.getPrice());
        	
        	if (item.isPlayerOptional()) {
        		m_botAction.sendSmartPrivateMessage(sender, "Targetable: Optional");
        	} else if (item.isPlayerStrict()) {
        		m_botAction.sendSmartPrivateMessage(sender, "Targetable: Required");
        	} else {
        		m_botAction.sendSmartPrivateMessage(sender, "Targetable: No");
        	}

        	if (item.isRestricted()) {
        		m_botAction.sendSmartPrivateMessage(sender, "Restrictions:");
        		String info = "";
        		PubItemRestriction r = item.getRestriction();
        		if (r.getRestrictedShips().size()==8) {
	        		info += "Cannot be bought while playing"; 
	        		m_botAction.sendSmartPrivateMessage(sender, "  - Cannot be bought while playing");
        		} else {
        			String ships = "";
        			for(int i=1; i<9; i++) {
        				if (!r.getRestrictedShips().contains(i)) {
        					ships += i+",";
        				}
        			}
        			m_botAction.sendSmartPrivateMessage(sender, "  - Available only for ship(s) : " + ships.substring(0, ships.length()-1));
        		}
        		if (r.getMaxConsecutive()!=-1) {
        			m_botAction.sendSmartPrivateMessage(sender, "  - Maximum of " + r.getMaxConsecutive()+" consecutive purchase(s)");
        		}
        		if (r.getMaxPerLife()!=-1) {
        			m_botAction.sendSmartPrivateMessage(sender, "  - Maximum of " + r.getMaxPerLife()+" per life");
        		}
        		if (r.getMaxPerSecond()!=-1) {
        			m_botAction.sendSmartPrivateMessage(sender, "  - Maximum of 1 every "+r.getMaxPerSecond()+" seconds (player only)");
        		}
        		if (r.getMaxArenaPerMinute()!=-1) {
        			m_botAction.sendSmartPrivateMessage(sender, "  - Maximum of 1 every "+r.getMaxArenaPerMinute()+" minutes for the whole arena");
        		}
        		if (!r.isBuyableFromSpec()) {
        			m_botAction.sendSmartPrivateMessage(sender, "  - Cannot be bought while spectating");
        		}
        	}

        	if (item.hasDuration()) {
        		m_botAction.sendSmartPrivateMessage(sender, "Durations:");
        		PubItemDuration d = item.getDuration();
        		if (d.getDeaths()!=-1 && d.getSeconds()!=-1 && d.getSeconds() > 60) {
        			m_botAction.sendSmartPrivateMessage(sender, "  - " + d.getDeaths()+" life(s) or "+(int)(d.getSeconds()/60)+" minutes");
        		}
        		else if (d.getDeaths()!=-1 && d.getSeconds()!=-1 && d.getSeconds() <= 60) {
        			m_botAction.sendSmartPrivateMessage(sender, "  - " + d.getDeaths()+" life(s) or "+(int)(d.getSeconds())+" seconds");
        		}
        		else if (d.getDeaths()!=-1) {
        			m_botAction.sendSmartPrivateMessage(sender, "  - " + d.getDeaths()+" life(s)");
        		}
        		else if (d.getSeconds()!=-1 && d.getSeconds() > 60) {
        			m_botAction.sendSmartPrivateMessage(sender, "  - " + (int)(d.getSeconds()/60)+" minutes");
        		}
        		else if (d.getSeconds()!=-1 && d.getSeconds() <= 60) {
        			m_botAction.sendSmartPrivateMessage(sender, "  - " + (int)(d.getSeconds())+" seconds");
        		}

        	}

        }
    }
    
    private void doCmdRichest(String sender, String command)
    {
    	HashMap<String,Integer> players = new HashMap<String,Integer>();
    	
    	Iterator<Player> it = m_botAction.getPlayerIterator();
    	while(it.hasNext()) {
    		PubPlayer player = playerManager.getPlayer(it.next().getPlayerName());
    		if (player != null) {
    			players.put(player.getPlayerName(), player.getMoney());
    		}
    	}
    	LinkedHashMap<String,Integer> richest = sort(players,false);
    	
    	Iterator<Entry<String,Integer>> it2 = richest.entrySet().iterator();
    	int count = 0;
    	while(it2.hasNext() && count < 3) {
    		Entry<String,Integer> entry = it2.next();
    		m_botAction.sendSmartPrivateMessage(sender, ++count + ". " + entry.getKey() + " with $" + entry.getValue());
    	}
    }
    
    private void doCmdLastKill(String sender)
    {
    	PubPlayer player = playerManager.getPlayer(sender);
    	if (player != null) {
    		
    		if (player.getLastKillKillerShip() == -1) {
    			m_botAction.sendSmartPrivateMessage(sender, "You don't have killed anyone yet.");
    			return;
    		}
    		
    		int shipKiller = player.getLastKillKillerShip();
    		int shipKilled = player.getLastKillKilledShip();
    		Location location = player.getLastKillLocation();

            // Money from the ship
            int moneyKiller = shipKillerPoints.get(shipKiller);
            int moneyKilled = shipKillerPoints.get(shipKilled);

            int moneyByLocation = 0;
            if (locationPoints.containsKey(location)) {
            	moneyByLocation = locationPoints.get(location);
            }
            
            int total = moneyKiller+moneyKilled+moneyByLocation;
            
            String msg = "You were a " + Tools.shipName(shipKiller) + " (+$"+moneyKiller+")";
            msg += ", killed a " + Tools.shipName(shipKilled) + " (+$"+moneyKilled+"). ";
            msg += "Location: " + context.getPubUtil().getLocationName(location) + " (+$"+moneyByLocation+").";
            
            // Overide if kill in space
            if (location.equals(Location.SPACE)) {
            	total = 0;
            	msg = "Kills outside of the base are worthless.";
            }
            
            m_botAction.sendSmartPrivateMessage(sender, "You earned $" + total + " by killing " + player.getLastKillKilledName() + ".");
            m_botAction.sendSmartPrivateMessage(sender, msg);
    		
    	} else {
    		m_botAction.sendSmartPrivateMessage(sender, "You're still not in the system. Wait a bit to be added.");
    	}
    }
    
    private void doCmdDisplayMoney(String sender, String command)
    {
    	String name = sender;
    	if (command.contains(" ")) {
    		name = command.substring(command.indexOf(" ")).trim();
			PubPlayer pubPlayer = playerManager.getPlayer(name,false);
			if (pubPlayer != null) {
				m_botAction.sendSmartPrivateMessage(sender, pubPlayer.getPlayerName() + " has $"+pubPlayer.getMoney() + ".");
			} else {
				m_botAction.sendSmartPrivateMessage(sender, "Player '" + name + "' not found.");
			}
    	}
    	else if(playerManager.isPlayerExists(name)) {
            PubPlayer pubPlayer = playerManager.getPlayer(sender);
            m_botAction.sendSmartPrivateMessage(sender, "You have $"+pubPlayer.getMoney() + " in your bank.");
        } else {
            m_botAction.sendSmartPrivateMessage(sender, "You're still not in the system. Wait a bit to be added.");
        }
    }
    
	private void doCmdCouponListOps(String sender) {
		
		List<String> lines = new ArrayList<String>();
		lines.add("List of Operators:");
		for(String name: couponOperators) {
			lines.add("- " + name);
		}
		m_botAction.smartPrivateMessageSpam(sender, lines.toArray(new String[lines.size()]));
	}

	private void doCmdCouponAddOp(String sender, String name) {
		
		if (!couponOperators.contains(name.toLowerCase())) {
			couponOperators.add(name.toLowerCase());
			m_botAction.sendSmartPrivateMessage(sender, name + " is now an operator (temporary until the bot respawn).");
			
			/*
			String operatorsString = "";
			for(String operator: operators) {
				operatorsString += "," + operator;
			}

			m_botAction.getBotSettings().put("Operators", operatorsString.substring(1));
			m_botAction.getBotSettings().save();
			*/
			
		} else {
			m_botAction.sendSmartPrivateMessage(sender, name + " is already an operator.");
		}
	}

	private void doCmdCouponDisable(String sender, String codeString) {
		
		CouponCode code = getCouponCode(codeString);
		if (code == null) {
			m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' not found.");
		}
		else if (!code.isEnabled()) {
			m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' is already disabled.");
		} 
		else if (!code.isValid()) {
			m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' is not valid anymore, useless.");
		} 
		else {
			code.setEnabled(false);
			updateCouponDB(code,"update:"+codeString+":"+sender);
		}
		
	}

	private void doCmdCouponEnable(String sender, String codeString) {
		
		CouponCode code = getCouponCode(codeString);
		if (code == null) {
			m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' not found.");
		}
		else if (code.isEnabled()) {
			m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' is already enabled.");
		} 
		else if (!code.isValid()) {
			m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' is not valid anymore, useless.");
		} 
		else {
			code.setEnabled(true);
			updateCouponDB(code,"update:"+codeString+":"+sender);
		}
	}

	private void doCmdCouponInfo(String sender, String codeString) {
		
		CouponCode code = getCouponCode(codeString);
		if (code == null) {
			m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' not found.");
		}
		else {
	    	String generation[] = new String[] {
        		"Code: " + codeString.toUpperCase() + "  (Generated by " + code.getCreatedBy() + ", " + new SimpleDateFormat("yyyy-MM-dd").format(code.getCreatedAt()) + ")",
        		" - Valid: " + (code.isValid()? "Yes" : "No (Reason: " + code.getInvalidReason() + ")"),
        		" - Money: $" + code.getMoney(),
        		" - " + (code.getUsed() > 0 ? "Used: " + code.getUsed() + " time(s)" : "Not used yet"),
        		"[Limitation]",
        		" - Maximum of use: " + code.getMaxUsed(),
        		" - Start date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(code.getStartAt()),
        		" - Expiration date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(code.getEndAt()),
        	};
	    	
	    	m_botAction.smartPrivateMessageSpam(sender, generation);
		}
	}
	
	private void doCmdCouponUsers(String sender, String codeString) {
		
		CouponCode code = getCouponCode(codeString);
		if (code == null) {
			m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' not found.");
		}
		else {
	    	
			try {
				
				ResultSet rs = m_botAction.SQLQuery(database, "SELECT * FROM tblMoneyCodeUsed WHERE fnMoneyCodeId = '" + code.getId() + "'");

				int count = 0;
				while (rs.next()) {
					count++;
					String name = count + ". " + rs.getString("fcName");
					String date = new SimpleDateFormat("yyyy-MM-dd").format(rs.getDate("fdCreated"));
					String message = Tools.formatString(name, 23, " ");
					message += " " + date;
					m_botAction.sendSmartPrivateMessage(sender, message);
				}
				rs.close();

				if (count == 0) {
					m_botAction.sendSmartPrivateMessage(sender, "This code has not been used yet.");
				}
				
			} catch (SQLException e) {
				Tools.printStackTrace(e);
				m_botAction.sendSmartPrivateMessage(sender, "An error has occured.");
			}

		}
	}

	private void doCmdCouponExpireDate(String sender, String command) {
		
		String[] pieces = command.split(":");
		if (pieces.length != 2) {
			m_botAction.sendSmartPrivateMessage(sender, "Bad argument");
			return;
		}
		
		String codeString = pieces[0];
		String dateString = pieces[1];
		
		CouponCode code = getCouponCode(codeString);
		if (code == null) {
			m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' not found.");
		}
		else {
	    	
			DateFormat df = new SimpleDateFormat("yyyy/MM/dd");
			java.util.Date date;
			try {
				date = df.parse(dateString);
			} catch (ParseException e) {
				m_botAction.sendSmartPrivateMessage(sender, "Bad date");
				return;
			}  
			
			code.setEndAt(date);
			updateCouponDB(code,"update:"+codeString+":"+sender);
			
		}
	}

	private void doCmdCouponLimitUse(String sender, String command) {
		
		String[] pieces = command.split(":");
		if (pieces.length != 2) {
			m_botAction.sendSmartPrivateMessage(sender, "Bad argument");
			return;
		}
		
		String codeString = pieces[0];
		String limitString = pieces[1];
		int limit;
		try {
			limit = Integer.valueOf(limitString);
		} catch (NumberFormatException e) {
			m_botAction.sendSmartPrivateMessage(sender, "Bad number");
			return;
		}
		
		CouponCode code = getCouponCode(codeString);
		if (code == null) {
			m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' not found.");
		}
		else {
			if (limit > 0) {
				code.setMaxUsed(limit);
				updateCouponDB(code,"update:"+codeString+":"+sender);

			} else {
				m_botAction.sendSmartPrivateMessage(sender, "Must be a number higher than 0.");
			}
			
		}
	}

	private void doCmdCouponCreate(String sender, String command) {
		
		String[] pieces = command.split("\\s*:\\s*");
		
		// Automatic code
		if (pieces.length == 1) {
		
			int money;
			try {
				money = Integer.parseInt(pieces[0]);
			} catch (NumberFormatException e) {
				m_botAction.sendSmartPrivateMessage(sender, "Bad number.");
				return;
			}
			
			String codeString = null;
			
			while (codeString == null || getCouponCode(codeString) != null) {
				// Genereate a random code using the date and md5
				String s = (new java.util.Date()).toString();
				MessageDigest m;
				try {
					m = MessageDigest.getInstance("MD5");
					m.update(s.getBytes(), 0, s.length());
					String codeTemp = new BigInteger(1, m.digest()).toString(16);
					
					if (getCouponCode(codeTemp) == null)
						codeString = codeTemp.substring(0,8).toUpperCase();
					
				} catch (NoSuchAlgorithmException e) {
					return;
				}
			}
			
			CouponCode code = new CouponCode(codeString, money, sender);
			insertCouponDB(code,"create:"+codeString+":"+sender);
			
		// Custom code
		} else if (pieces.length == 2) {
			
			String codeString = pieces[0];
			
			int money;
			try {
				money = Integer.parseInt(pieces[1]);
			} catch (NumberFormatException e) {
				m_botAction.sendSmartPrivateMessage(sender, "Bad number.");
				return;
			}
			
			if (getCouponCode(codeString) != null) {
				m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' already exists.");
				return;
			}
			
			CouponCode code = new CouponCode(codeString, money, sender);
			insertCouponDB(code,"create:"+codeString+":"+sender);
			
		} else {
			m_botAction.sendSmartPrivateMessage(sender, "Bad argument.");
			return;
		}
	}

	private void doCmdCoupon(String sender, String codeString) {

		CouponCode code = getCouponCode(codeString);
		if (code == null) {
			// no feedback to avoid bruteforce!!
			// m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' not found.");
		} 
		else if (isPlayerRedeemAlready(sender, code)) {
			m_botAction.sendSmartPrivateMessage(sender, "You have already used this code.");
			return;
		} 
		else if (!code.isValid()) {
			// no feedback to avoid bruteforce!!
			return;
		}
		else {

			code.gotUsed();
			updateCouponDB(code,"updateredeem:"+codeString+":"+sender);
			
		}
	}
	
	private boolean isPlayerRedeemAlready(String playerName, CouponCode code) {
		
		ResultSet rs;
		try {
			rs = m_botAction.SQLQuery(database, "SELECT * FROM tblMoneyCodeUsed WHERE fnMoneyCodeId = '" + code.getId() + "' AND fcName = '" + Tools.addSlashes(playerName) + "'");
			if (rs.first()) {
				rs.close();
				return true;
			} else {
				rs.close();
				return false;
			}
			
		} catch (SQLException e) {
			Tools.printStackTrace(e);
			return false;
		}
		
	}
    
	private CouponCode getCouponCode(String codeString) {
    	
		if (coupons.containsKey(codeString))
			return coupons.get(codeString);
		
		try {
			
			ResultSet rs = m_botAction.SQLQuery(database, "SELECT * FROM tblMoneyCode WHERE fcCode = '" + Tools.addSlashes(codeString) + "'");

			if (rs.next()) {
	
				int id = rs.getInt("fnMoneyCodeId");
				String description = rs.getString("fcDescription");
				String createdBy = rs.getString("fcCreatedBy");
				int money = rs.getInt("fnMoney");
				int used = rs.getInt("fnUsed");
				int maxUsed = rs.getInt("fnMaxUsed");
				boolean enabled = rs.getBoolean("fbEnabled");
				Date startAt = rs.getDate("fdStartAt");
				Date endAt = rs.getDate("fdEndAt");
				Date createdAt = rs.getDate("fdCreated");
				
				CouponCode code = new CouponCode(codeString, money, createdAt, createdBy);
				code.setId(id);
				code.setDescription(description);
				code.setEnabled(enabled);
				code.setMaxUsed(maxUsed);
				code.setStartAt(startAt);
				code.setEndAt(endAt);
				code.setUsed(used);
				rs.close();
				
				coupons.put(codeString, code);
				return code;
			}
			else {
				rs.close();
				return null;
			}
			
		} catch (SQLException e) {
			Tools.printStackTrace(e);
			return null;
		}

    	
    }
	
	private void insertCouponDB(CouponCode code, String params) {
		
		String startAtString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(code.getStartAt());
		String endAtString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(code.getEndAt());
		

		m_botAction.SQLBackgroundQuery(database, "coupon:"+params,
				"INSERT INTO tblMoneyCode " +
				"(fcCode, fcDescription, fnMoney, fcCreatedBy, fnUsed, fnMaxUsed, fdStartAt, fdEndAt, fbEnabled, fdCreated) " +
				"VALUES (" +
				"'" + code.getCode() + "'," +
				"'" + Tools.addSlashes(code.getDescription()) + "'," +
				"'" + code.getMoney() + "'," +
				"'" + Tools.addSlashes(code.getCreatedBy()) + "'," +
				"'" + code.getUsed() + "'," +
				"'" + code.getMaxUsed() + "'," +
				"'" + startAtString + "'," +
				"'" + endAtString + "'," +
				"" + (code.isEnabled() ? 1 : 0) + "," +
				"NOW()" +
				")");
		
	}
	
	private void updateCouponDB(CouponCode code, String params) {
		
		String startAtString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(code.getStartAt());
		String endAtString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(code.getEndAt());

		m_botAction.SQLBackgroundQuery(database, "coupon:"+params,
				"UPDATE tblMoneyCode SET " +
				"fcDescription = '" + Tools.addSlashes(code.getDescription()) + "', " +
				"fnUsed = " + code.getUsed() + ", " +
				"fnMaxUsed = " + code.getMaxUsed() + ", " +
				"fdStartAt = '" + startAtString + "', " +
				"fdEndAt = '" + endAtString + "', " +
				"fbEnabled = " + (code.isEnabled() ? 1 : 0) + " " +
				"WHERE fnMoneyCodeId='" + code.getId() + "'");

	}

    public boolean isStoreOpened() {
    	return store.isOpened();
    }
    
    public boolean isDatabaseOn() {
    	return  m_botAction.getBotSettings().getString("database") != null;
    }
    
    public void handleTK(Player killer) {
    	
    	
    	
    }
    
    public void handleDisconnect() {
    	
    }
    
    public void handleEvent(SQLResultEvent event) {
    	
    	// Coupon system
    	if (event.getIdentifier().startsWith("coupon")) {

    		String[] pieces = event.getIdentifier().split(":");
    		if (pieces.length > 1) {
    			
    			if (pieces.length==4 && pieces[1].equals("update")) {
    				m_botAction.sendSmartPrivateMessage(pieces[3], "Code '" + pieces[2] + "' updated.");
    			}
    		
    			else if (pieces.length == 4 && pieces[1].equals("updateredeem")) {
    				
    				CouponCode code = getCouponCode(pieces[2]);
    				if (code == null)
    					return;
    				
    				m_botAction.SQLBackgroundQuery(database, null, "INSERT INTO tblMoneyCodeUsed "
    						+ "(fnMoneyCodeId, fcName, fdCreated) "
    						+ "VALUES ('" + code.getId() + "', '" + Tools.addSlashes(pieces[3]) + "', NOW())");
    			
    				if (context.getPlayerManager().addMoney(pieces[3], code.getMoney(), true)) {
    					m_botAction.sendSmartPrivateMessage(pieces[3], "$" + code.getMoney() + " has been added to your account.");
    				} else {
    					m_botAction.sendSmartPrivateMessage(pieces[3], "A problem has occured. Please contact someone from the staff by using ?help. (err:03)");
    				}

    			}
    			
    			else if(pieces.length == 4 && pieces[1].equals("create")) {
    				m_botAction.sendSmartPrivateMessage(pieces[3], "Code '" + pieces[2] + "' created.");
    			}
    		}
    		
    	}
    	
    }

    public void handleEvent(FrequencyChange event) {
    	Player p = m_botAction.getPlayer(event.getPlayerID());
    	if (p == null)
    		return;
    	
    	frequencyTimes.put(p.getPlayerName(), System.currentTimeMillis());
    }
    
    public void handleEvent(PlayerLeft event) {
    	Player p = m_botAction.getPlayer(event.getPlayerID());
    	if (p == null)
    		return;
    	
    	frequencyTimes.remove(p.getPlayerName());
    }
   
    public void handleEvent(PlayerDeath event) {

    	final Player killer = m_botAction.getPlayer( event.getKillerID() );
        final Player killed = m_botAction.getPlayer( event.getKilleeID() );

        if( killer == null || killed == null )
            return;
        
        // A TK, do nothing for now
        if ( killer.getFrequency() == killed.getFrequency() ) {
        	handleTK(killer);
        	return;
        }

        // Disable if the player is dueling
    	if (context.getPubChallenge().isDueling(killer.getPlayerName())
    			|| context.getPubChallenge().isDueling(killed.getPlayerName())) {
    		return;
    	}

        try{

        	final PubPlayer pubPlayerKilled = playerManager.getPlayer(killed.getPlayerName());
        	PubPlayer pubPlayerKiller = playerManager.getPlayer(killer.getPlayerName());
            // Is the player not on the system? (happens when someone loggon and get killed in 1-2 seconds)
            if (pubPlayerKiller == null || pubPlayerKilled == null) {
            	return;
            }
            
            pubPlayerKilled.handleDeath(event);
            
            // Duration check for Ship Item
            if (pubPlayerKilled.hasShipItem() && playersWithDurationItem.containsKey(pubPlayerKilled)) {
            	final PubItemDuration duration = playersWithDurationItem.get(pubPlayerKilled);
            	if (duration.getDeaths() <= pubPlayerKilled.getDeathsOnShipItem()) {
            		// Let the player wait before setShip().. (the 4 seconds after each death)
                	final TimerTask timer = new TimerTask() {
                        public void run() {
                    		pubPlayerKilled.resetShipItem();
                    		m_botAction.setShip(killed.getPlayerName(), 1);
                    		playersWithDurationItem.remove(pubPlayerKilled);
                    		m_botAction.sendSmartPrivateMessage(killed.getPlayerName(), "You lost your ship after " + duration.getDeaths() + " death(s).",22);
                        }
                    };
                    m_botAction.scheduleTask(timer, 4300);
            	}
            	else {
            		// TODO - Give the PubShipItemSettings
            		// i.e: A player buys a special ship with 10 repel (PubShipItemSettings)
            		//      for 5 deaths (PubItemDuration)
            	}
            }
 
            int x = killer.getXTileLocation();
            int y = killer.getYTileLocation(); 
            Location location = context.getPubUtil().getLocation(x, y);
            
            // Add money if kill inside the base (aka not in space)
            if (location != null) 
            {
	            int money = 0;
	
	            // Money from the ship
	            int moneyKiller = shipKillerPoints.get((int)killer.getShipType());
	            int moneyKilled = shipKillerPoints.get((int)killed.getShipType());
	            money += moneyKiller;
	            money += moneyKilled;
	            
	            // Money from the location
	            int moneyByLocation = 0;
	            if (locationPoints.containsKey(location)) {
	            	moneyByLocation = locationPoints.get(location);
	            	money += moneyByLocation;
	            }
	
	            String playerName = killer.getPlayerName();
	            if (!location.equals(Location.SPACE)) {
	            	context.getPlayerManager().addMoney(playerName, money);
	            }
	            pubPlayerKiller.setLastKillShips((int)killer.getShipType(), (int)killed.getShipType());
	            pubPlayerKiller.setLastKillLocation(location);
	            pubPlayerKiller.setLastKillKilledName(killed.getPlayerName());
            }

        } catch(Exception e){
            Tools.printStackTrace(e);
        }
    	
    }

    public void handleCommand(String sender, String command) {
    	
        if(command.startsWith("!items") || command.trim().equals("!i")) {
            doCmdItems(sender);
        }
        else if(command.trim().equals("!buy") || command.trim().equals("!b")){
        	doCmdItems(sender);
        }
        else if(command.startsWith("!$") || command.startsWith("!money") ) {
            doCmdDisplayMoney(sender, command);
        }
        else if(command.startsWith("!iteminfo") || command.startsWith("!buyinfo")){
        	doCmdItemInfo(sender, command);
        }
        else if(command.startsWith("!buy") || command.equals("!b")){
        	doCmdBuy(sender, command);
        }
        else if(command.startsWith("!donate")){
        	doCmdDonate(sender, command);
        }
        else  if (command.startsWith("!coupon")) {
    		doCmdCoupon(sender, command.substring(8).trim());
        }
        else if(command.equals("!lastkill")){
        	doCmdLastKill(sender);
        }
        else if(command.startsWith("!richest")){
        	doCmdRichest(sender, command);
        }
        else if( m_botAction.getOperatorList().isOwner(sender) && command.startsWith("!addmoney")) {
        	doCmdAddMoney(sender,command);
        }

	}

    public void handleModCommand(String sender, String command) {
    	
		if (command.startsWith("!bankrupt")) {
            doCmdBankrupt(sender, command);
        } else if(command.startsWith("!debugobj")) {
        	doCmdDebugObj(sender, command);
        }
		
		// Coupon System commands
    	boolean operator = couponOperators.contains(sender.toLowerCase());
    	boolean smod = m_botAction.getOperatorList().isSmod(sender);
    	
    	// (Operator/SMOD only)
    	if (operator || smod) {
    		
			if (command.startsWith("!couponcreate ")) {
				doCmdCouponCreate(sender, command.substring(14).trim());
			} else if (command.startsWith("!couponlimituse ")) {
				doCmdCouponLimitUse(sender, command.substring(16).trim());
			} else if (command.startsWith("!couponexpiredate ")) {
				doCmdCouponExpireDate(sender, command.substring(18).trim());
			} else if (command.startsWith("!couponinfo ")) {
				doCmdCouponInfo(sender, command.substring(12).trim());
			} else if (command.startsWith("!couponusers ")) {
				doCmdCouponUsers(sender, command.substring(13).trim());
			} else if (command.startsWith("!couponenable ")) {
				doCmdCouponEnable(sender, command.substring(14).trim());
			} else if (command.startsWith("!coupondisable ")) {
				doCmdCouponDisable(sender, command.substring(15).trim());
			}
			
			// (SMOD only)
			if (smod && command.startsWith("!couponaddop ")) {
				doCmdCouponAddOp(sender, command.substring(12).trim());
			} else if (smod && command.equals("!couponlistops")) {
				doCmdCouponListOps(sender);
			}
    	}
		
    }
    
	@Override
	public String[] getHelpMessage(String sender) {
		return new String[] {
			pubsystem.getHelpLine("!buy                -- Display the list of items. (!items, !i)"),
			pubsystem.getHelpLine("!buy <item>         -- Item to buy. (!b)"),
			pubsystem.getHelpLine("!iteminfo <item>    -- Information about this item. (restriction, duration, etc.)"),
	        pubsystem.getHelpLine("!money <name>       -- Display your money or for a given player name. (!$)"),
	        pubsystem.getHelpLine("!donate <name>:<$>  -- Donate money to a player."),
	        pubsystem.getHelpLine("!coupon <code>      -- Redeem your <code>."),
	        pubsystem.getHelpLine("!richest            -- Top 3 richest players currently playing."),
	        pubsystem.getHelpLine("!lastkill           -- How much you earned for your last kill (+ algorithm)."),
		};
	}

	@Override
	public String[] getModHelpMessage(String sender) {

    	String generation[] = new String[] {
    		pubsystem.getHelpLine("!couponcreate <money>            -- Create a random code for <money>. Use !limituse/!expiredate for more options."),
    		pubsystem.getHelpLine("!couponcreate <code>:<money>     -- Create a custom code for <money>. Max of 32 characters."),
    		pubsystem.getHelpLine("!couponlimituse <code>:<max>     -- Set how many players <max> can get this <code>."),
    		pubsystem.getHelpLine("!couponexpiredate <code>:<date>  -- Set an expiration <date> (format: yyyy/mm/dd) for <code>."),
    	};
    	
    	String maintenance[] = new String[] {
    		pubsystem.getHelpLine("!couponinfo <code>               -- Information about this <code>."),
    		pubsystem.getHelpLine("!couponusers <code>              -- Who used this code."),
    		pubsystem.getHelpLine("!couponenable <code>             -- Enable a <code> previously disabled."),
    		pubsystem.getHelpLine("!coupondisable <code>            -- Disable a <code>."),
    	};
    	
    	String bot[] = new String[] {
    		pubsystem.getHelpLine("!couponaddop <name>              -- Add an operator (an operator can generate a code to be used)."),
    		pubsystem.getHelpLine("!couponlistops                   -- List of operators."),
    	};
    	
    	List<String> lines = new ArrayList<String>();
    	if (m_botAction.getOperatorList().isSmod(sender)) {
    		lines.addAll(Arrays.asList(generation));
    		lines.addAll(Arrays.asList(maintenance));
    		lines.addAll(Arrays.asList(bot));
    		
    	} else if (couponOperators.contains(sender.toLowerCase())) {
    		lines.addAll(Arrays.asList(generation));
    		lines.addAll(Arrays.asList(maintenance));
    	}
	    	
	    return lines.toArray(new String[lines.size()]);
		
	}

    private String getSender(Message event)
    {
        if(event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE)
            return event.getMessager();

        int senderID = event.getPlayerID();
        return m_botAction.getPlayerName(senderID);
    }
    
    /**
     * Format a number to currency with the dollar sign
     * 100000 -> $100,000
     */
    public static String formatMoney(int money) {
    	NumberFormat nf = NumberFormat.getCurrencyInstance();
    	String result =  nf.format(money);
    	result = result.substring(0, result.length()-3);
        return result;
    }
    
    /**
     * Not always working.. need to find out why.
     */
    private void itemCommandSuddenDeath(final String sender, String params) throws PubException{

    	if (params.equals("")) {
    		throw new PubException("You must add 1 parameter when you buy this item.");
    	}
    	
    	final String playerName = params;

    	m_botAction.spectatePlayerImmediately(playerName);

    	Player p = m_botAction.getPlayer(playerName);
    	Player psender = m_botAction.getPlayer(sender);
    	int distance = 10*16; // distance from the player and the bot
    	
    	int x = p.getXLocation();
    	int y = p.getYLocation();
    	int angle = (int)p.getRotation()*9;

    	int bot_x = x + (int)(-distance*Math.sin(Math.toRadians(angle)));
    	int bot_y = y + (int)(distance*Math.cos(Math.toRadians(angle)));

    	m_botAction.getShip().setShip(0);
    	m_botAction.getShip().setFreq(psender.getFrequency());
    	m_botAction.getShip().rotateDegrees(angle-90);
    	m_botAction.getShip().move(bot_x, bot_y);
    	m_botAction.getShip().sendPositionPacket();
    	m_botAction.getShip().fire(WeaponFired.WEAPON_THOR);
    	m_botAction.getShip().fire(WeaponFired.WEAPON_THOR);
    	
    	TimerTask timer = new TimerTask() {
            public void run() {
            	m_botAction.specWithoutLock(m_botAction.getBotName());
            	m_botAction.sendUnfilteredPrivateMessage(playerName, "I've been ordered by " + sender + " to kill you.",7);
            }
        };
        m_botAction.scheduleTask(timer, 500);


    }
    
    private void itemCommandMegaWarp(String sender, String params) {

	   	Player p = m_botAction.getPlayer(sender);
	   	
	   	String message = "";
	   	int privFreqs = 0;
	   	
	   	List<Integer> freqList = new ArrayList<Integer>();
	   	for(int i=0; i<10000; i++) {
	   		int size = m_botAction.getPlayingFrequencySize(i);
	   		if (size>0 && i!=p.getFrequency()) {
	   			freqList.add(i);
	   			if (i<100) {
	   				message += ", "+i;
	   			} else {
	   				privFreqs++;
	   			}
	   		}
	   	}
	   	
	   	if (privFreqs > 0) {
	   		message += " and " + privFreqs + " private freq(s)";
	   	}
	   	message = message.substring(1);
	   	
	   	final Integer[] freqs = freqList.toArray(new Integer[freqList.size()]);
	   	
	   	m_botAction.sendArenaMessage(sender + " has warped to death freq " + message + ".",17);
	   	
	   	int toExclude = m_botAction.getPlayingFrequencySize(p.getFrequency());
	   	int total = m_botAction.getNumPlaying()-toExclude;
	   	int jump = (int)(360/total);
	   	Iterator<Player> it = m_botAction.getPlayingPlayerIterator();
	   	
	   	// Center of the circle (wormhole) + diameter
	   	int x = 640;
	   	int y = 610;
	   	int d = 25;
	   	
	   	int i = 0;
	   	while(it.hasNext()) {
	   		Player player = it.next();
	   		if (p.getFrequency()==player.getFrequency())
	   			continue;
	   		
	   		int posX = x+(int)(d*Math.cos(i*jump));
	   		int posY = y+(int)(d*Math.sin(i*jump));

	   		m_botAction.warpTo(player.getPlayerName(), posX, posY);
	   		m_botAction.specificPrize(player.getPlayerName(), Tools.Prize.ENERGY_DEPLETED);
	   		
	   		i++;
	   	}
    	
    }
    
    private void itemCommandBaseBlast(String sender, String params) {

	   	Player p = m_botAction.getPlayer(sender);

    	m_botAction.getShip().setShip(0);
    	m_botAction.getShip().setFreq(p.getFrequency());
    	m_botAction.sendUnfilteredPrivateMessage(m_botAction.getBotName(), "*super");
    	m_botAction.specificPrize(m_botAction.getBotName(), Tools.Prize.SHIELDS);

    	m_botAction.sendArenaMessage(sender + " has sent a blast of bombs inside the flagroom!", Tools.Sound.HALLELUJAH);
        final TimerTask timerFire = new TimerTask() {
            public void run() {
            	m_botAction.getShip().move(512*16+8, 270*16+8);
            	for(int j=0; j<2; j++) {
	            	for(int i=0; i<360/5; i++) {
	            		
	                	m_botAction.getShip().rotateDegrees(i*5);
	                	m_botAction.getShip().sendPositionPacket();
	                	m_botAction.getShip().fire(34);
	                	try { Thread.sleep(5); } catch (InterruptedException e) {}
	                	m_botAction.getShip().fire(35);
		            	try { Thread.sleep(5); } catch (InterruptedException e) {}
	            	}
            	}
            }
        };
    	timerFire.run();
    	
    	TimerTask timer = new TimerTask() {
            public void run() {
            	m_botAction.specWithoutLock(m_botAction.getBotName());
            	//m_botAction.move(512*16, 350*16);
            	m_botAction.getShip().setSpectatorUpdateTime(100);
            }
        };
        m_botAction.scheduleTask(timer, 7500);
    	
    }
    
    private void itemCommandNukeBase(String sender, String params) {

	   	Player p = m_botAction.getPlayer(sender);

    	m_botAction.getShip().setShip(0);
    	m_botAction.getShip().setFreq(p.getFrequency());
    	m_botAction.specificPrize(m_botAction.getBotName(), Tools.Prize.SHIELDS);
    	m_botAction.getShip().rotateDegrees(90);
    	m_botAction.getShip().sendPositionPacket();

    	m_botAction.sendArenaMessage(sender + " has sent a nuke in the direction of the flagroom! Impact is imminent!",17);
        final TimerTask timerFire = new TimerTask() {
            public void run() {
            	//for(int i=0; i<2; i++) { // Number of waves
	            	for(int j=0; j<7; j++) {
		            	m_botAction.getShip().move((482+(j*10))*16+8, 100*16);
		            	m_botAction.getShip().sendPositionPacket();
		            	m_botAction.getShip().fire(WeaponFired.WEAPON_THOR);
		            	m_botAction.getShip().fire(WeaponFired.WEAPON_THOR);
		            	try { Thread.sleep(50); } catch (InterruptedException e) {}
	            	}
	            	try { Thread.sleep(50); } catch (InterruptedException e) {}
            	//}
            }
        };
    	timerFire.run();
    	
    	TimerTask timer = new TimerTask() {
            public void run() {
            	m_botAction.specWithoutLock(m_botAction.getBotName());
            	//m_botAction.move(512*16, 350*16);
            	m_botAction.getShip().setSpectatorUpdateTime(100);
            }
        };
        m_botAction.scheduleTask(timer, 7500);
    	
    }
    
    private void itemCommandBaseStrike(String sender, String params) {

    	int[][] coords = new int[][] {
    			new int[] { 500, 256 }, // Top right
    			new int[] { 512, 253 }, // Top middle
    			new int[] { 524, 256 }, // Top left
    			new int[] { 538, 260 }, // Ear right
    			new int[] { 486, 260 }, // Ear left
    			new int[] { 492, 273 }, // Middle right
    			new int[] { 532, 273 }, // Middle left
    			//new int[] { 500, 287 }, // Bottom right
    			//new int[] { 526, 287 }, // Bottom left
    	};
    	
	   	Player commander = m_botAction.getPlayer(sender);
	   	
	   	Iterator<Player> it = m_botAction.getPlayingPlayerIterator();
	   	while(it.hasNext()) {
	   		
	   		Player player = it.next();

	   		if (player.getFrequency() != commander.getFrequency())
	   			continue;
	   		if (context.getPubChallenge().isDueling(player.getPlayerName()))
	   			continue;
	   		// Ter always warped on the middle
	   		if (player.getShipType() == Tools.Ship.TERRIER) {
	   			m_botAction.warpTo(player.getPlayerName(), coords[1][0], coords[1][1]);
	   		// Shark always warped on top
	   		} else if (player.getShipType() == Tools.Ship.SHARK) {
	   			int num = (int)Math.floor(Math.random()*3);
	   			m_botAction.warpTo(player.getPlayerName(), coords[num][0], coords[num][1]);
	   		// The rest is random..
	   		} else {
	   			int num = (int)Math.floor(Math.random()*coords.length);
	   			m_botAction.warpTo(player.getPlayerName(), coords[num][0], coords[num][1], 3);
	   		}
	   	}
	   	if (commander.getFrequency() < 100)
	   		m_botAction.sendArenaMessage("Freq " + commander.getFrequency() + " is striking the flag room! Commanded by " + sender + ".", Tools.Sound.CROWD_OHH);
	   	else
	   		m_botAction.sendArenaMessage("A private freq is striking the flag room! Commanded by " + sender + ".", Tools.Sound.CROWD_OHH);
	   	
    }
    
    private void itemCommandFlagSaver(String sender, String params) {

	   	Player p = m_botAction.getPlayer(sender);
	   	
    	m_botAction.getShip().setShip(1);
    	m_botAction.getShip().setFreq(p.getFrequency());
    	m_botAction.getShip().rotateDegrees(270);
    	m_botAction.specificPrize(m_botAction.getBotName(), Tools.Prize.SHIELDS);
    	m_botAction.getShip().move(512*16+8, 265*16+8);
	   	
    	TimerTask timer = new TimerTask() {
            public void run() {
            	m_botAction.specWithoutLock(m_botAction.getBotName());
            }
        };
        m_botAction.scheduleTask(timer, 4000);
        if (p.getFrequency() < 100)
        	m_botAction.sendArenaMessage(m_botAction.getBotName() + " got the flag for freq " + p.getFrequency() + ", thanks to " + sender + "!", Tools.Sound.CROWD_OHH);
        else
        	m_botAction.sendArenaMessage(m_botAction.getBotName() + " got the flag for a private freq, thanks to " + sender + "!", Tools.Sound.CROWD_OHH);
	   	
    }
    
    private void itemCommandEpidemic(String sender, String params) {

	   	Player p = m_botAction.getPlayer(sender);
	   	
	   	String message = "";
	   	int privFreqs = 0;
	   	
	   	List<Integer> freqList = new ArrayList<Integer>();
	   	for(int i=0; i<10000; i++) {
	   		int size = m_botAction.getPlayingFrequencySize(i);
	   		if (size>0 && i!=p.getFrequency()) {
	   			freqList.add(i);
	   			if (i<100) {
	   				message += ", "+i;
	   			} else {
	   				privFreqs++;
	   			}
	   		}
	   	}
	   	
	   	if (privFreqs > 0) {
	   		message += " and " + privFreqs + " private freq(s)";
	   	}
	   	message = message.substring(2);
	   	
	   	final Integer[] freqs = freqList.toArray(new Integer[freqList.size()]);
	   	
	   	m_botAction.sendArenaMessage(sender + " has started an epidemic on freq " + message + ".",17);
	   	
	   	int timeElapsed = 0;
		for(int i=1; i<10; i++) {
			timeElapsed += 2300-(int)(Math.log(i)*1000);
	   		m_botAction.scheduleTask(new EnergyDeplitedTask(freqs), timeElapsed);
		}
		m_botAction.scheduleTask(new EnergyDeplitedTask(freqs), timeElapsed+150);
		m_botAction.scheduleTask(new EnergyDeplitedTask(freqs), timeElapsed+300);
		m_botAction.scheduleTask(new EnergyDeplitedTask(freqs), timeElapsed+450);
		m_botAction.scheduleTask(new EnergyDeplitedTask(freqs), timeElapsed+600);
		m_botAction.scheduleTask(new EnergyDeplitedTask(freqs), timeElapsed+750);
		m_botAction.scheduleTask(new EngineShutdownExtendedTask(freqs), timeElapsed+750);
		
	   	
    }

	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void reloadConfig() {
		store.reloadConfig();
		if (m_botAction.getBotSettings().getInt("money_enabled")==1) {
			enabled = true;
		} else {
			store.turnOff();
		}
		couponOperators = new HashSet<String>();
    	if (m_botAction.getBotSettings().getString("coupon_operators") != null) {
    		List<String> list = Arrays.asList(m_botAction.getBotSettings().getString("coupon_operators").split("\\s*,\\s*"));
    		for(String name: list) {
    			couponOperators.add(name.toLowerCase());
    		}
    	}
    	database = m_botAction.getBotSettings().getString("database");
	}
	
   	private class EnergyDeplitedTask extends TimerTask {
   		private Integer[] freqs;
   		public EnergyDeplitedTask(Integer[] freqs) {
   			this.freqs = freqs;
   		}
		public void run() {
			for(int freq: freqs)
				m_botAction.prizeFreq(freq, Tools.Prize.ENERGY_DEPLETED);
		}
	};
	
 	private class EngineShutdownExtendedTask extends TimerTask {
   		private Integer[] freqs;
   		public EngineShutdownExtendedTask(Integer[] freqs) {
   			this.freqs = freqs;
   		}
		public void run() {
			for(int freq: freqs)
				m_botAction.prizeFreq(freq, Tools.Prize.ENGINE_SHUTDOWN_EXTENDED);
		}
	}
 	
	public LinkedHashMap<String,Integer> sort(HashMap passedMap, boolean ascending) {

		List mapKeys = new ArrayList(passedMap.keySet());
		List mapValues = new ArrayList(passedMap.values());
		Collections.sort(mapValues);
		Collections.sort(mapKeys);

		if (!ascending)
			Collections.reverse(mapValues);

		LinkedHashMap someMap = new LinkedHashMap();
		Iterator valueIt = mapValues.iterator();
		while (valueIt.hasNext()) {
			Object val = valueIt.next();
			Iterator keyIt = mapKeys.iterator();
			while (keyIt.hasNext()) {
				Object key = keyIt.next();
				if (passedMap.get(key).toString().equals(val.toString())) {
					passedMap.remove(key);
					mapKeys.remove(key);
					someMap.put(key, val);
					break;
				}
			}
		}
		return someMap;
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	};
	
}
