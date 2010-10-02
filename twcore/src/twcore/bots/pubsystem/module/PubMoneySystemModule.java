package twcore.bots.pubsystem.module;

import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.PubUtilModule.Location;
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
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.WeaponFired;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubMoneySystemModule extends AbstractModule {

	private OperatorList opList;
	private BotSettings m_botSettings;

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
    	
    	this.m_botSettings = botAction.getBotSettings();
    	this.opList = m_botAction.getOperatorList();
    	
    	this.playerManager = context.getPlayerManager();

        this.store = new PubStore(m_botAction, context);
        this.context = context;

        this.playersWithDurationItem = new HashMap<PubPlayer, PubItemDuration>();
        
        this.shipKillerPoints = new HashMap<Integer, Integer>();
        this.shipKilledPoints = new HashMap<Integer, Integer>();
        this.locationPoints = new HashMap<Location, Integer>();
        try {
        	initializePoints();
	    } catch (Exception e) {
	    	Tools.printStackTrace("Error while initializing the money system", e);
		}
    	
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
        
    	String[] locations = m_botSettings.getString("point_location").split(",");
    	for(String loc: locations) {
    		String[] split = m_botSettings.getString("point_location" + loc).split(",");
    		Location location = Location.valueOf(split[0].toUpperCase());
    		locationPoints.put(location, Integer.parseInt(split[1]));
    	}
    	
        String[] pointsKiller = m_botSettings.getString("point_killer").split(",");
        String[] pointsKilled = m_botSettings.getString("point_killed").split(",");
        
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
                    	PubItemDuration duration = item.getDuration();
                    	m_botAction.sendPrivateMessage(receiver.getPlayerName(), "You have " + duration.getSeconds() + " seconds to use your item.");
                    	if (duration.hasTime()) {
                    		TimerTask timer = new TimerTask() {
                                public void run() {
                                	m_botAction.sendUnfilteredPrivateMessage(receiver.getPlayerName(), "*shipreset");
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
    
    public void doCmdSetMoney(String sender, String command) {
    	
    	command = command.substring(10).trim();
    	if (command.contains(":")) {
    		String[] split = command.split("\\s*:\\s*");
    		String name = split[0];
    		String money = split[1];
    		PubPlayer pubPlayer = playerManager.getPlayer(name,false);
    		if (pubPlayer != null) {
    			int currentMoney = pubPlayer.getMoney();
    			pubPlayer.setMoney(Integer.valueOf(money));
    			m_botAction.sendPrivateMessage(sender, pubPlayer.getPlayerName() + " has now $" + money + " (before: $" + currentMoney + ")");
    		
    			PubLogSystem.write(LogType.MOD, "!setmoney " + pubPlayer.getPlayerName() + ":" + money + " by " + sender + "\n");
    		
    		} else {
    			m_botAction.sendPrivateMessage(sender, "Player not found.");
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
        	lines.add("List of items you can buy. Each items has a set of restrictions/durations.");
        	lines.add(" *Target optional **Target required (!buy item:PlayerName)");
        	lines.add("");
        	
	        for(PubItem item: store.getItems().values()) {
	        	
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
        			m_botAction.sendPrivateMessage(sender, "  - Maximum of " + r.getMaxConsecutive()+" purchase(s) consecutive");
        		}
        		if (r.getMaxPerLife()!=-1) {
        			m_botAction.sendPrivateMessage(sender, "  - Maximum of " + r.getMaxPerLife()+" per life");
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
    
    private void doCmdDisplayMoney(String sender, String command)
    {
    	String name = sender;
    	if (command.contains(" ")) {
    		name = command.substring(command.indexOf(" ")).trim();
			PubPlayer pubPlayer = playerManager.getPlayer(name,false);
			if (pubPlayer != null) {
				m_botAction.sendPrivateMessage(sender, pubPlayer.getPlayerName() + " has $"+pubPlayer.getMoney() + ".");
			} else {
				m_botAction.sendPrivateMessage(sender, pubPlayer.getPlayerName() + " does not exist on the system. Oh noes!!!");
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
    	return m_botSettings.getString("database") != null;
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
        if (!context.getPubChallenge().isEnabled()) {
        	if (context.getPubChallenge().isDueling(killer.getPlayerName())) {
        		return;
        	} else if (context.getPubChallenge().isDueling(killed.getPlayerName())) {
        		return;
        	}
        }

        try{

            final PubPlayer pubPlayerKilled = playerManager.getPlayer(killed.getPlayerName());
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
            PubPlayer pubPlayer = playerManager.getPlayer(playerName);
            pubPlayer.addMoney(money);
            
            PubLogSystem.write(LogType.MONEY, playerName + "("+killer.getShipType()+"): " + pubPlayer.getMoney() + " +"+money+"\n");

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
        else if(command.startsWith("!$") || command.startsWith("!money") || command.startsWith("!cash")) {
            doCmdDisplayMoney(sender, command);
        }
        else if(command.startsWith("!iteminfo") || command.startsWith("!buyinfo")){
        	doCmdItemInfo(sender, command);
        }
        else if(command.startsWith("!buy") || command.startsWith("!b")){
        	doCmdBuy(sender, command);
        }
        else if(opList.isSmod(sender) && command.startsWith("!setmoney")) {
        	doCmdSetMoney(sender,command);
        }

    }
    
    public void handleModCommand(String sender, String command) {

    }
    
	@Override
	public String[] getHelpMessage() {
		return new String[] {
			pubsystem.getHelpLine("!buy                   -- Display the list of items. (also !items)"),
			pubsystem.getHelpLine("!buy <item_name>       -- Item to buy. (also !b)"),
			pubsystem.getHelpLine("!iteminfo <item_name>  -- Information about this item. (restriction, duration, etc.)"),
	        pubsystem.getHelpLine("!money <name>          -- Display your money or for a given player name. (also !$, !cash)"),
        };
	}

	@Override
	public String[] getModHelpMessage() {
		return new String[] {    
				pubsystem.getHelpLine("!setmoney <name>:<$>   -- Set the money for a given player name. (Smod+ only)."),
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
    
    private void itemCommandNukeBase(String sender, String params) {

	   	Player p = m_botAction.getPlayer(sender);
	   	
	    //m_botAction.setFreq(m_botAction.getBotName(),(int)p.getFrequency());
    	m_botAction.getShip().setShip(1);
    	//m_botAction.getShip().setFreq(9999);
    	m_botAction.getShip().rotateDegrees(90);
    	m_botAction.getShip().sendPositionPacket();
    	//m_botAction.setThorAdjust(5);
    	m_botAction.sendArenaMessage(sender + " has sent a nuke in the direction of the flagroom! Impact is imminent!",17);
        final TimerTask timerFire = new TimerTask() {
            public void run() {
            	//for(int i=0; i<2; i++) { // Number of waves
	            	for(int j=0; j<7; j++) {
		            	m_botAction.getShip().move((482+(j*10))*16+8, 100*16);
		            	m_botAction.getShip().sendPositionPacket();
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
        m_botAction.scheduleTask(timer, 13000);
    	
    }

	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void reloadConfig() {
		store.reloadConfig();
	}


}
