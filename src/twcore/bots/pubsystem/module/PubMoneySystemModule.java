package twcore.bots.pubsystem.module;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.module.moneysystem.PubStore;
import twcore.bots.pubsystem.module.moneysystem.item.PubCommandItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubItemDuration;
import twcore.bots.pubsystem.module.moneysystem.item.PubItemRestriction;
import twcore.bots.pubsystem.module.moneysystem.item.PubPrizeItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubShipItem;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.bots.pubsystem.util.PubException;
import twcore.bots.pubsystem.util.PubLocation;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.SQLResultEvent;
import twcore.core.events.WeaponFired;
import twcore.core.game.Player;
import twcore.core.util.Point;
import twcore.core.util.PointLocation;
import twcore.core.util.Tools;

public class PubMoneySystemModule extends AbstractModule {

	private OperatorList opList;
	private BotSettings m_botSettings;
	private PubContext context;

	private PubStore store;
	
	private PubPlayerManagerModule playerManager;
    
    private Map<PubPlayer, PubItemDuration> playersWithDurationItem;
    
    // These variables are used to calculate the money earned for a kill
    private Map<Integer, Integer> shipKillerPoints;
    private Map<Integer, Integer> shipKilledPoints;
    private LinkedHashMap<PubLocation, Integer> locationPoints;
    
    private FileWriter itemsLog;
    private FileWriter moneyLog;
	
    public PubMoneySystemModule(BotAction botAction, PubContext context) {
    	
    	super(botAction);
    	
    	this.m_botSettings = botAction.getBotSettings();
    	this.opList = m_botAction.getOperatorList();
    	
    	this.playerManager = context.getPlayerManager();

        this.store = new PubStore(m_botAction);
        this.context = context;

        this.playersWithDurationItem = new HashMap<PubPlayer, PubItemDuration>();
        
        this.shipKillerPoints = new HashMap<Integer, Integer>();
        this.shipKilledPoints = new HashMap<Integer, Integer>();
        this.locationPoints = new LinkedHashMap<PubLocation, Integer>();
        try {
        	initializePoints();
	    } catch (Exception e) {
	    	Tools.printStackTrace("Error while initializing the money system", e);
		}
	    
	    if (m_botSettings.getString("store_log") != null)
	    {
		    File file = new File(m_botSettings.getString("store_log"));
		    if (file.isDirectory()) {
		    	try {
			    	moneyLog = new FileWriter(new File(file, "money.log"), true);
			    	itemsLog = new FileWriter(new File(file, "items.log"), true);
				} catch (IOException e) {
					e.printStackTrace();
				}
		    } else {
		    	Tools.printLog("Cannot store logs for the pub system, " + file + " does not exist.");
		    }
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

        String[] pointsLocation = m_botSettings.getString("point_location").split(",");
        for(String number: pointsLocation) {
        	String[] data = m_botSettings.getString("point_location"+number).split(",");
        	String name = data[0];
        	int points = Integer.parseInt(data[1]);
        	Vector<Point> listPoints = new Vector<Point>();
        	for(int i=2; i<data.length; i++) {
        		String[] coords = data[i].split(":");
        		int x = Integer.parseInt(coords[0]);
        		int y = Integer.parseInt(coords[1]);
        		listPoints.add(new Point(x, y));
        	}
        	PointLocation p = new PointLocation(listPoints, false);
        	PubLocation location = new PubLocation(p, name);
        	locationPoints.put(location, points);
        }
        
        String[] pointsKiller = m_botSettings.getString("point_killer").split(",");
        String[] pointsKilled = m_botSettings.getString("point_killed").split(",");
        
        for(int i=1; i<=8; i++) {
        	shipKillerPoints.put(i, Integer.parseInt(pointsKiller[i-1]));
        	shipKilledPoints.put(i, Integer.parseInt(pointsKilled[i-1]));
        }

    }
    

    private void buyItem(final String playerName, String itemName, String params, int shipType){
    	
        try{

            if (playerManager.isPlayerExists(playerName)){
            	
            	Player player = m_botAction.getPlayer(playerName);
            	PubPlayer pubPlayer = playerManager.getPlayer(playerName);
            	
            	PubItem item = store.buy(itemName, pubPlayer, shipType);

                // PRIZE ITEM
                if (item instanceof PubPrizeItem) {
                	m_botAction.specificPrize(pubPlayer.getPlayerName(), ((PubPrizeItem) item).getPrizeNumber());
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
                    		playersWithDurationItem.put(pubPlayer, duration);
                    	}
                    }
                    
                    pubPlayer.setShipItem((PubShipItem)item);
                    m_botAction.setShip(playerName, ((PubShipItem) item).getShipNumber());
                	
                } 
                
                if (itemsLog != null) {
        		    itemsLog.write(Tools.getTimeStamp() + " " + playerName + "("+player.getShipType()+"): " + item.getName() + " " + item.getPrice() + "\n");
                	itemsLog.flush();
                }
                
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
    			pubPlayer.setMoney(Integer.valueOf(money));
    			m_botAction.sendPrivateMessage(sender, pubPlayer.getPlayerName() + " has now $" + money);
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
	        for(PubItem item: store.getItems().values()) {
	        	
	        	if (item instanceof PubPrizeItem) {
	        		if (!currentClass.equals(PubPrizeItem.class))
	        			lines.add("== PRIZES ===========================================================");
	        		currentClass = PubPrizeItem.class;
	        	} else if (item instanceof PubShipItem) {
	        		if (!currentClass.equals(PubShipItem.class))
	        			lines.add("== SHIPS ============================================================");
	        		currentClass = PubShipItem.class;
	        	} else if (item instanceof PubCommandItem) {
	        		if (!currentClass.equals(PubCommandItem.class))
	        			lines.add("== SPECIALS =========================================================");
	        		currentClass = PubCommandItem.class;
	        	}
	        	
	        	/*
	        	String abbv = "";
		        if (item.getAbbreviations().size()>0) {
		        	abbv+="  (";
		        	for(String str: item.getAbbreviations()) {
		        		abbv += "!"+str+",";
		        	}
		        	abbv=abbv.substring(0,abbv.length()-1)+")";
	        	}
	        	*/
	        	
		        String line = Tools.formatString("!buy "+item.getName(), 20);
		        //line += Tools.formatString(abbv, 12);
	        	line += Tools.formatString("$"+item.getPrice()+"", 10);
	        	
	        	String info = "";
	        	
	        	if (item.isRestricted()) {
	        		PubItemRestriction r = item.getRestriction();
	        		if (r.getRestrictedShips().size()==0) 
	        			info += "All ships";
	        		else if (r.getRestrictedShips().size()==8) {
		        		info += "None"; // Just in case
	        		} else {
	        			String ships = "Ships:";
	        			for(int i=1; i<9; i++) {
	        				if (!r.getRestrictedShips().contains(i)) {
	        					ships += i+",";
	        				}
	        			}
	        			info += ships.substring(0, ships.length()-1);
	        		}
	        		info = Tools.formatString(info, 20);
	        		if (r.getMaxPerLife()!=-1) {
	        			info += r.getMaxPerLife()+" per life. ";
	        		}
	        		if (r.getMaxArenaPerMinute()!=-1) {
	        			info += "1 every "+r.getMaxArenaPerMinute()+" minutes. ";
	        		}
	        		
	        	}
	        	
	        	if (item.hasDuration()) {
	        		PubItemDuration d = item.getDuration();
	        		if (d.getDeaths()!=-1 && d.getSeconds()!=-1) {
	        			info += "Last "+d.getDeaths()+" life(s) or "+(int)(d.getSeconds()/60)+" minute(s). ";
	        		}
	        		else if (d.getDeaths()!=-1) {
	        			info += "Last "+d.getDeaths()+" life(s). ";
	        		}
	        		else if (d.getSeconds()!=-1) {
	        			info += "Last "+(int)(d.getSeconds()/60)+" minute(s). ";
	        		}
	        		
	        	}
	        	
	        	lines.add(line+info);
	        }
	    } 

        m_botAction.smartPrivateMessageSpam(sender, lines.toArray(new String[lines.size()]));
        
    }

    private void doCmdBuy(String sender, String command) 
    {
        Player p = m_botAction.getPlayer(sender);
        if(p == null)
            return;
        command = command.substring(command.indexOf(" ")).trim();
        if (command.indexOf(" ")!=-1) {
        	String params = command.substring(command.indexOf(" ")).trim();
        	command = command.substring(0, command.indexOf(" ")).trim();
        	buyItem(sender, command, params, p.getShipType());
        } else {
        	buyItem(sender, command, "", p.getShipType());
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
            money += shipKillerPoints.get((int)killer.getShipType());
            money += shipKilledPoints.get((int)killed.getShipType());
            
            // Money from the location
            Point pointXY = new Point(killer.getXTileLocation(), killer.getYTileLocation());
            for(PubLocation location: locationPoints.keySet()) {
            	if (location.isInside(pointXY)) {
            		money += locationPoints.get(location);
            		break;
            	}
            }

            String playerName = killer.getPlayerName();
            PubPlayer pubPlayer = playerManager.getPlayer(playerName);
            pubPlayer.addMoney(money);
            
            if (moneyLog != null) {
            	moneyLog.write(Tools.getTimeStamp() + " " + playerName + "("+killer.getShipType()+"): " + pubPlayer.getMoney() + " +"+money+"\n");
            	moneyLog.flush();
            }

        } catch(Exception e){
            Tools.printStackTrace(e);
        }
    	
    }

    public void handleCommand(String sender, String command) {
        try {
            if(command.startsWith("!items") || command.trim().equals("!i")) {
                doCmdItems(sender);
            }
            else if(command.trim().equals("!buy") || command.trim().equals("!b")){
            	doCmdItems(sender);
            }
            else if(command.startsWith("!$") || command.startsWith("!money") || command.startsWith("!cash")) {
                doCmdDisplayMoney(sender, command);
            }
            else if(command.startsWith("!buy") || command.startsWith("!b")){
            	doCmdBuy(sender, command);
            }
            else if(opList.isOwner(sender) && command.startsWith("!setmoney")) {
            	doCmdSetMoney(sender,command);
            }
        } catch(RuntimeException e) {
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
    }
    
    public void handleModCommand(String sender, String command) {

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


}
