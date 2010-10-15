package twcore.bots.pubsystem.module;

import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.Map.Entry;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.PubUtilModule.Location;
import twcore.bots.pubsystem.module.moneysystem.LvzMoneyPanel;
import twcore.bots.pubsystem.module.moneysystem.PubStore;
import twcore.bots.pubsystem.module.moneysystem.item.PubCommandItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubItemDuration;
import twcore.bots.pubsystem.module.moneysystem.item.PubItemRestriction;
import twcore.bots.pubsystem.module.moneysystem.item.PubPrizeItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubShipItem;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.bots.pubsystem.util.PubException;
import twcore.bots.pubsystem.util.PubLogSystem;
import twcore.bots.pubsystem.util.PubLogSystem.LogType;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
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

    public PubMoneySystemModule(BotAction botAction, PubContext context) {
    	
    	super(botAction, context, "Store");
    	
    	this.playerManager = context.getPlayerManager();

        this.playersWithDurationItem = new HashMap<PubPlayer, PubItemDuration>();
        this.shipKillerPoints = new HashMap<Integer, Integer>();
        this.shipKilledPoints = new HashMap<Integer, Integer>();
        this.locationPoints = new HashMap<Location, Integer>();
        
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
            		m_botAction.sendPrivateMessage(playerName, "You cannot buy an item while dueling.");
            		return;
            	}
            	
            	Player player = m_botAction.getPlayer(playerName);
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
            	
                // PRIZE ITEM
                if (item instanceof PubPrizeItem) {
                	List<Integer> prizes = ((PubPrizeItem) item).getPrizes();
                	for(int prizeNumber: prizes) {
                		m_botAction.specificPrize(receiver.getPlayerName(), prizeNumber);
                	}
                	if (item.hasDuration()) {
                    	final PubItemDuration duration = item.getDuration();
                    	m_botAction.sendPrivateMessage(receiver.getPlayerName(), "You have " + duration.getSeconds() + " seconds to use your item.");
                    	if (duration.hasTime()) {
                    		TimerTask timer = new TimerTask() {
                                public void run() {
                                	int bounty = m_botAction.getPlayer(receiver.getPlayerName()).getBounty();
                                	if (System.currentTimeMillis()-receiver.getLastDeath() > duration.getSeconds()*1000) {
	                                	m_botAction.sendUnfilteredPrivateMessage(receiver.getPlayerName(), "*shipreset");
	                                	m_botAction.giveBounty(receiver.getPlayerName(), bounty);
                                	}
                                	m_botAction.sendPrivateMessage(receiver.getPlayerName(), "Item '" + item.getName() + "' lost.");
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
            		method.invoke(this, playerName, params);
                } 
                
                // SHIP ITEM
                else if (item instanceof PubShipItem) {
                	
                    if (item.hasDuration()) {
                    	PubItemDuration duration = item.getDuration();
                    	if (duration.hasTime()) {
                    		final int currentShip = (int)player.getShipType();
                        	TimerTask timer = new TimerTask() {
                                public void run() {
                                	m_botAction.setShip(playerName, currentShip);
                                }
                            };
                            m_botAction.scheduleTask(timer, duration.getSeconds()*1000);
                    	}
                    	else if (duration.hasDeaths()) {
                    		playersWithDurationItem.put(receiver, duration);
                    	}
                    }
                    
                    receiver.setShipItem((PubShipItem)item);
                    m_botAction.setShip(playerName, ((PubShipItem) item).getShipNumber());
                	
                } 
                
                PubLogSystem.write(LogType.ITEM, playerName + "("+player.getShipType()+"): " + item.getName() + " " + item.getPrice() + "\n");
                
                if (item.isArenaItem()) {
                	 m_botAction.sendArenaMessage(playerName + " just bought a " + item.getDisplayName() + " for $" + item.getPrice() + ".",21);
                }
                
        		String database = m_botAction.getBotSettings().getString("database");
        		
        		// The query will be closed by PlayerManagerModule
        		
        		int shipType = m_botAction.getPlayer(receiver.getPlayerName()).getShipType();
        		
        		if (database!=null)
        		m_botAction.SQLBackgroundQuery(database, "", "INSERT INTO tblPurchaseHistory "
    				+ "(fcItemName, fcBuyerName, fcReceiverName, fcArguments, fnPrice, fnReceiverShipType, fdDate) "
    				+ "VALUES ('"+Tools.addSlashes(item.getName())+"','"+Tools.addSlashes(buyer.getPlayerName())+"','"+Tools.addSlashes(receiver.getPlayerName())+"','"+Tools.addSlashes(params)+"','"+item.getPrice()+"','"+shipType+"',NOW())");
        		

            } 
            else {
                m_botAction.sendPrivateMessage(playerName, "You're not in the system to use !buy.");
            }
            
        }
        catch(PubException e) {
        	 m_botAction.sendPrivateMessage(playerName, e.getMessage());
        }
        catch(Exception e){
            Tools.printStackTrace(e);
        }
        
    }
    
    public void doCmdDonate(String sender, String command) {
    	
    	if (command.length()<8) {
    		m_botAction.sendPrivateMessage(sender, "Try !donate <name>.");
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
    			m_botAction.sendPrivateMessage(sender, "You must specify a number. !donate playerA:1000");
    			return;
    		}
    		
    		if (context.getPubChallenge().isDueling(sender)) {
    			m_botAction.sendPrivateMessage(sender, "You cannot donate while dueling.");
    			return;
    		}
    		
    		if (Integer.valueOf(money) < 0) {
    			m_botAction.sendPrivateMessage(sender, "What are you trying to do here?");
    			return;
    		}
    		
    		if (Integer.valueOf(money) < 250) {
    			m_botAction.sendPrivateMessage(sender, "You cannot donate for less than $250.");
    			return;
    		}
    		
    		/* Not needed
    		if (context.getPubChallenge().hasChallenged(sender)) {
    			m_botAction.sendPrivateMessage(sender, "You cannot donate while challenging a player for a duel.");
    			return;
    		}
    		*/
    		
    		Player p = m_botAction.getFuzzyPlayer(name);
    		if (p == null) {
    			m_botAction.sendPrivateMessage(sender, "Player not found.");
    			return;
    		}
    		name = p.getPlayerName();
    		
    		if (name.equals(sender)) {
    			m_botAction.sendPrivateMessage(sender, "You cannot donate to yourself.");
    			return;
    		}

    		PubPlayer pubPlayer = playerManager.getPlayer(name,false);
    		PubPlayer pubPlayerDonater = playerManager.getPlayer(sender,false);
    		if (pubPlayer != null && pubPlayerDonater != null) {
    			
    			if (pubPlayerDonater.getMoney() < Integer.valueOf(money)) {
    				m_botAction.sendPrivateMessage(sender, "You don't have $" + Integer.valueOf(money) + " to donate.");
    				return;
    			}
    			
    			int currentMoney = pubPlayer.getMoney();
    			int moneyToDonate = Integer.valueOf(money);
    			
    			pubPlayer.addMoney(moneyToDonate);
    			pubPlayerDonater.removeMoney(moneyToDonate);
    			m_botAction.sendPrivateMessage(sender, "$" + moneyToDonate + " sent to " + pubPlayer.getPlayerName() + ".");
    			m_botAction.sendPrivateMessage(pubPlayer.getPlayerName(), sender + " sent you $" + moneyToDonate + ", you have now $" + (moneyToDonate+currentMoney) + ".");

        		String database = m_botAction.getBotSettings().getString("database");
        		
        		// The query will be closed by PlayerManagerModule
        		if (database!=null)
        		m_botAction.SQLBackgroundQuery(database, "", "INSERT INTO tblPlayerDonations "
    				+ "(fcName, fcNameTo, fnMoney, fdDate) "
    				+ "VALUES ('"+Tools.addSlashes(sender)+"','"+Tools.addSlashes(pubPlayer.getPlayerName())+"','"+moneyToDonate+"',NOW())");
        		
    			
    		} else {
    			m_botAction.sendPrivateMessage(sender, "Player not found.");
    		}
    	}
    	else {
    		m_botAction.sendPrivateMessage(sender, "Invalid argument");
    	}
    }
    
    public void doCmdAddMoney(String sender, String command) {
    	
    	command = command.substring(10).trim();
    	if (command.contains(":")) {
    		String[] split = command.split("\\s*:\\s*");
    		String name = split[0];
    		String money = split[1];
    		PubPlayer pubPlayer = playerManager.getPlayer(name,false);
    		if (pubPlayer != null) {
    			int currentMoney = pubPlayer.getMoney();
    			pubPlayer.addMoney(Integer.valueOf(money));
    			m_botAction.sendPrivateMessage(sender, pubPlayer.getPlayerName() + " has now $" + (currentMoney+Integer.valueOf(money)) + " (before: $" + currentMoney + ")");
    		
    		} else {
    			playerManager.addMoney(name, Integer.valueOf(money), true);
    			m_botAction.sendPrivateMessage(sender, name + " has now $" + money + " more money.");
    		}
    	}
    	else {
    		m_botAction.sendPrivateMessage(sender, "Invalid argument");
    	}
    }

    private void sendMoneyToPlayer(String playerName, int amount, String message) {
    	PubPlayer player = playerManager.getPlayer(playerName);
    	player.addMoney(amount);
        if (message!=null) {
        	m_botAction.sendPrivateMessage(playerName, message);
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
    	
    	m_botAction.sendPrivateMessage(sender, "Average of " + LvzMoneyPanel.totalObjSentPerMinute() + " *obj sent per minute.");
    }
    
    private void doCmdBankrupt(String sender, String command) {
    	
    	String name = sender;
    	if (command.contains(" ")) {
    		name = command.substring(command.indexOf(" ")).trim();
			PubPlayer pubPlayer = playerManager.getPlayer(name,false);
			if (pubPlayer != null) {
				int money = pubPlayer.getMoney();
				pubPlayer.setMoney(0);
				m_botAction.sendPrivateMessage(sender, pubPlayer.getPlayerName() + " has now $0 (before: $" + money + ")");
			} else {
				m_botAction.sendPrivateMessage(sender, "Player '" + name + "' not found.");
			}
    	}
    }
    
    
    private void doCmdItemInfo(String sender, String command) 
    {
        Player p = m_botAction.getPlayer(sender);
        if(p == null)
            return;
        if (!command.contains(" ")) {
        	m_botAction.sendPrivateMessage(sender, "You need to supply an item.");
        	return;
        }
        String itemName = command.substring(command.indexOf(" ")).trim();
        PubItem item = store.getItem(itemName);
        if (item == null) {
        	m_botAction.sendPrivateMessage(sender, "Item '" + itemName + "' not found.");
        }
        else {
        	
        	m_botAction.sendPrivateMessage(sender, "Item: " + item.getName() + " (" + item.getDescription() + ")");
        	m_botAction.sendPrivateMessage(sender, "Price: $" + item.getPrice());
        	
        	if (item.isPlayerOptional()) {
        		m_botAction.sendPrivateMessage(sender, "Targetable: Optional");
        	} else if (item.isPlayerStrict()) {
        		m_botAction.sendPrivateMessage(sender, "Targetable: Required");
        	} else {
        		m_botAction.sendPrivateMessage(sender, "Targetable: No");
        	}

        	if (item.isRestricted()) {
        		m_botAction.sendPrivateMessage(sender, "Restrictions:");
        		String info = "";
        		PubItemRestriction r = item.getRestriction();
        		if (r.getRestrictedShips().size()==8) {
	        		info += "Cannot be bought while playing"; 
	        		m_botAction.sendPrivateMessage(sender, "  - Cannot be bought while playing");
        		} else {
        			String ships = "";
        			for(int i=1; i<9; i++) {
        				if (!r.getRestrictedShips().contains(i)) {
        					ships += i+",";
        				}
        			}
        			m_botAction.sendPrivateMessage(sender, "  - Available only for ship(s) : " + ships.substring(0, ships.length()-1));
        		}
        		if (r.getMaxConsecutive()!=-1) {
        			m_botAction.sendPrivateMessage(sender, "  - Maximum of " + r.getMaxConsecutive()+" consecutive purchase(s)");
        		}
        		if (r.getMaxPerLife()!=-1) {
        			m_botAction.sendPrivateMessage(sender, "  - Maximum of " + r.getMaxPerLife()+" per life");
        		}
        		if (r.getMaxPerSecond()!=-1) {
        			m_botAction.sendPrivateMessage(sender, "  - Maximum of 1 every "+r.getMaxPerSecond()+" seconds (player only)");
        		}
        		if (r.getMaxArenaPerMinute()!=-1) {
        			m_botAction.sendPrivateMessage(sender, "  - Maximum of 1 every "+r.getMaxArenaPerMinute()+" minutes for the whole arena");
        		}
        		if (!r.isBuyableFromSpec()) {
        			m_botAction.sendPrivateMessage(sender, "  - Cannot be bought while spectating");
        		}
        	}

        	if (item.hasDuration()) {
        		m_botAction.sendPrivateMessage(sender, "Durations:");
        		PubItemDuration d = item.getDuration();
        		if (d.getDeaths()!=-1 && d.getSeconds()!=-1 && d.getSeconds() > 60) {
        			m_botAction.sendPrivateMessage(sender, "  - " + d.getDeaths()+" life(s) or "+(int)(d.getSeconds()/60)+" minutes");
        		}
        		else if (d.getDeaths()!=-1 && d.getSeconds()!=-1 && d.getSeconds() <= 60) {
        			m_botAction.sendPrivateMessage(sender, "  - " + d.getDeaths()+" life(s) or "+(int)(d.getSeconds())+" seconds");
        		}
        		else if (d.getDeaths()!=-1) {
        			m_botAction.sendPrivateMessage(sender, "  - " + d.getDeaths()+" life(s)");
        		}
        		else if (d.getSeconds()!=-1 && d.getSeconds() > 60) {
        			m_botAction.sendPrivateMessage(sender, "  - " + (int)(d.getSeconds()/60)+" minutes");
        		}
        		else if (d.getSeconds()!=-1 && d.getSeconds() <= 60) {
        			m_botAction.sendPrivateMessage(sender, "  - " + (int)(d.getSeconds())+" seconds");
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
    		m_botAction.sendPrivateMessage(sender, ++count + ". " + entry.getKey() + " with $" + entry.getValue());
    	}
    }
    
    private void doCmdDisplayMoney(String sender, String command)
    {
    	String name = sender;
    	if (command.contains(" ")) {
    		name = command.substring(command.indexOf(" ")).trim();
			PubPlayer pubPlayer = playerManager.getPlayer(name,false);
			if (pubPlayer != null) {
				m_botAction.sendPrivateMessage(sender, pubPlayer.getPlayerName() + " has $"+pubPlayer.getMoney() + ".");
			} else {
				m_botAction.sendPrivateMessage(sender, "Player '" + name + "' not found.");
			}
    	}
    	else if(playerManager.isPlayerExists(name)) {
            PubPlayer pubPlayer = playerManager.getPlayer(sender);
            m_botAction.sendPrivateMessage(sender, "You have $"+pubPlayer.getMoney() + " in your bank.");
        } else {
            m_botAction.sendPrivateMessage(sender, "You're still not in the system. Wait a bit to be added.");
        }
    }
    
    public boolean isStoreOpened() {
    	return store.isOpened();
    }
    
    public boolean isDatabaseOn() {
    	return  m_botAction.getBotSettings().getString("database") != null;
    }

    public void handleEvent(PlayerDeath event) {

    	final Player killer = m_botAction.getPlayer( event.getKillerID() );
        final Player killed = m_botAction.getPlayer( event.getKilleeID() );

        if( killer == null || killed == null )
            return;
        
        // A TK, do nothing for now
        if ( killer.getFrequency() == killed.getFrequency() ) {

        	/*
        	if ( killer.getShipType() == Tools.Ship.SHARK )
        		return;

        	PubPlayer pubPlayerKiller = playerManager.getPlayer(killer.getPlayerName());
        	if (pubPlayerKiller != null) {
        		pubPlayerKiller.removeMoney(10);
        	}
        	*/
        	return;
        }

        // Disable if the player is dueling
    	if (context.getPubChallenge().isDueling(killer.getPlayerName())
    			|| context.getPubChallenge().isDueling(killed.getPlayerName())) {
    		return;
    	}

        try{

            final PubPlayer pubPlayerKilled = playerManager.getPlayer(killed.getPlayerName());
            // Is the player not on the system? (happens when someone loggon and get killed in 1-2 seconds)
            if (pubPlayerKilled == null) {
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
                    		m_botAction.sendPrivateMessage(killed.getPlayerName(), "You lost your ship after " + duration.getDeaths() + " death(s).",22);
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
 
            int money = 0;

            // Money from the ship
            int moneyKiller = shipKillerPoints.get((int)killer.getShipType());
            int moneyKilled = shipKillerPoints.get((int)killed.getShipType());
            money += moneyKiller;
            money += moneyKilled;
            
            // Money from the location
            int x = killer.getXTileLocation();
            int y = killer.getYTileLocation();
            Location location = context.getPubUtil().getLocation(x, y);
            int moneyByLocation = 0;
            if (locationPoints.containsKey(location)) {
            	moneyByLocation = locationPoints.get(location);
            	money += moneyByLocation;
            }

            String playerName = killer.getPlayerName();
            context.getPlayerManager().addMoney(playerName, money);

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
        else if(command.startsWith("!richest")){
        	doCmdRichest(sender, command);
        }
        else if( m_botAction.getOperatorList().isOwner(sender) && command.startsWith("!addmoney")) {
        	doCmdAddMoney(sender,command);
        }

    }
    
    public void handleModCommand(String sender, String command) {
        if(command.startsWith("!bankrupt")) {
            doCmdBankrupt(sender, command);
        }
        else  if(command.startsWith("!debugobj")) {
        	doCmdDebugObj(sender, command);
        }
    }
    
	@Override
	public String[] getHelpMessage() {
		return new String[] {
			pubsystem.getHelpLine("!buy                -- Display the list of items. (!items, !i)"),
			pubsystem.getHelpLine("!buy <item>         -- Item to buy. (!b)"),
			pubsystem.getHelpLine("!iteminfo <item>    -- Information about this item. (restriction, duration, etc.)"),
	        pubsystem.getHelpLine("!money <name>       -- Display your money or for a given player name. (!$)"),
	        pubsystem.getHelpLine("!donate <name>:<$>  -- Donate money to a player."),
	        pubsystem.getHelpLine("!richest            -- Top 3 richest players currently playing."),
        };
	}

	@Override
	public String[] getModHelpMessage() {
		return new String[] {    
				pubsystem.getHelpLine("!bankrupt <name>       -- Set money to $0 for this player."),
				pubsystem.getHelpLine("!addmoney <name>:<$>   -- Add money for a given player name. (Owner only)."),
        };
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
    	int distance = 10*16; // distance from the player and the bot
    	
    	int x = p.getXLocation();
    	int y = p.getYLocation();
    	int angle = (int)p.getRotation()*9;

    	int bot_x = x + (int)(-distance*Math.sin(Math.toRadians(angle)));
    	int bot_y = y + (int)(distance*Math.cos(Math.toRadians(angle)));

    	m_botAction.getShip().setShip(0);
    	m_botAction.getShip().rotateDegrees(angle-90);
    	m_botAction.getShip().move(bot_x, bot_y);
    	m_botAction.getShip().sendPositionPacket();
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
    
    private void itemCommandBombBlast(String sender, String params) {

	   	Player p = m_botAction.getPlayer(sender);

    	m_botAction.getShip().setShip(0);
    	m_botAction.getShip().setFreq(p.getFrequency());
    	m_botAction.sendUnfilteredPrivateMessage(m_botAction.getBotName(), "*super");
    	m_botAction.specificPrize(m_botAction.getBotName(), Tools.Prize.SHIELDS);

    	m_botAction.sendArenaMessage(sender + " has sent a blast of bombs inside the flagroom!", Tools.Sound.UNDER_ATTACK);
        final TimerTask timerFire = new TimerTask() {
            public void run() {
            	m_botAction.getShip().move(512*16+8, 270*16+8);
            	for(int i=0; i<360/5; i++) {
            		
                	m_botAction.getShip().rotateDegrees(i*5);
                	m_botAction.getShip().sendPositionPacket();
                	m_botAction.getShip().fire(WeaponFired.WEAPON_BOMB);
                	m_botAction.getShip().fire(WeaponFired.WEAPON_BULLET_BOUNCING);
                	m_botAction.getShip().fire(WeaponFired.WEAPON_BULLET_BOUNCING);
                	m_botAction.getShip().fire(WeaponFired.WEAPON_BULLET_BOUNCING);
	            	try { Thread.sleep(10); } catch (InterruptedException e) {}
            	}
            }
        };
    	timerFire.run();
    	
    	TimerTask timer = new TimerTask() {
            public void run() {
            	m_botAction.specWithoutLock(m_botAction.getBotName());
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
    			new int[] { 500, 287 }, // Bottom right
    			new int[] { 526, 287 }, // Bottom left
    			new int[] { 492, 273 }, // Middle right
    			new int[] { 532, 273 }, // Middle left
    	};
    	
	   	Player p = m_botAction.getPlayer(sender);
	   	Iterator<Player> it = m_botAction.getPlayingPlayerIterator();
	   	while(it.hasNext()) {
	   		Player player = it.next();
	   		if (player.getFrequency() != p.getFrequency())
	   			continue;
	   		if (context.getPubChallenge().isDueling(player.getPlayerName()))
	   			continue;
	   		// Ter always warped on the middle
	   		if (p.getShipType() == Tools.Ship.TERRIER) {
	   			m_botAction.warpTo(p.getPlayerName(), coords[1][0], coords[1][1]);
	   		// Shark always warped on top
	   		} else if (p.getShipType() == Tools.Ship.SHARK) {
	   			int num = (int)Math.floor(Math.random()*3);
	   			m_botAction.warpTo(p.getPlayerName(), coords[num][0], coords[num][1]);
	   		// The rest is random..
	   		} else {
	   			int num = (int)Math.floor(Math.random()*coords.length);
	   			m_botAction.warpTo(p.getPlayerName(), coords[num][0], coords[num][1]);
	   		}
	   	}
	   	if (p.getFrequency() < 100)
	   		m_botAction.sendArenaMessage("Freq " + p.getFrequency() + " is striking the flag room! Commanded by " + sender + ".", Tools.Sound.CROWD_OHH);
	   	else
	   		m_botAction.sendArenaMessage("A private freq is striking the flag room! Commanded by " + sender + ".", Tools.Sound.CROWD_OHH);
	   	
    }
    
    private void itemCommandFlagSaver(String sender, String params) {

	   	Player p = m_botAction.getPlayer(sender);
	   	
    	m_botAction.getShip().setShip(1);
    	m_botAction.getShip().setFreq(p.getFrequency());
    	m_botAction.getShip().rotateDegrees(270);
    	m_botAction.getShip().sendPositionPacket();
    	m_botAction.specificPrize(m_botAction.getBotName(), Tools.Prize.SHIELDS);
    	m_botAction.getShip().move(512*16+8, 265*16+8);
    	m_botAction.getShip().sendPositionPacket();
	   	
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
