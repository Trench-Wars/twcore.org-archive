package twcore.bots.pubsystem.module;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.PreparedStatement;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimerTask;
import java.util.Vector;
import java.util.Random;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.PubUtilModule.Location;
import twcore.bots.pubsystem.module.PubUtilModule.Region;
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
import twcore.bots.pubsystem.util.AutobotThread;
import twcore.bots.pubsystem.util.IPCReceiver;
import twcore.bots.pubsystem.util.PubException;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.events.ArenaList;
import twcore.core.events.FrequencyChange;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.events.SQLResultEvent;
import twcore.core.events.WeaponFired;
import twcore.core.game.Player;
import twcore.core.util.MapRegions;
import twcore.core.util.Point;
import twcore.core.util.Tools;
import twcore.core.util.Tools.Ship;

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

    // Last time sphere was used on freq
    private HashMap<Integer, Long> immuneFreqs;

    // IPC Receivers (used by autobots thread)
    private List<IPCReceiver> ipcReceivers;
    private String IPC_CHANNEL = "pubautobot";

    // Coupon system
    private HashSet<String> couponOperators;
    private HashMap<String, CouponCode> coupons; // cache system

    private HashMap<String, ArrayList<ItemBan>> itemBans;
    private HashMap<String, ShopBan> shopBans;
    
    // Coordinates
    private Point coordBaseBlast;
    private Point[] coordsBaseStrike;
    private Point[] coordsBaseTerrier;
    private Point coordFlagSaver;
    private Point coordMegaWarp;
    private Point coordNukeBase;
    private Point coordRoofTurret;
    
    // Arena
    //private String arenaNumber = "0";

    private ArrayList<Integer> canBuyAnywhere;
    private boolean canLTBuyAnywhere = false;
    private boolean donationEnabled = false;

    private String database;
    private MapRegions regions;
    private static final String MAP_NAME = "pubmap";
    
    PreparedStatement updateMoney;
    private int[] fruitStats = {0,0};

    /** PubMoneySystemModule constructor */
    public PubMoneySystemModule(BotAction botAction, PubContext context) {

        super(botAction, context, "Money/Store");
        
        this.updateMoney = m_botAction.createPreparedStatement(database, "pubsystem", "UPDATE tblMoneyCode SET fnMoney = (fnMoney + ?) WHERE fcCode = 'OWNER'");

        this.playerManager = context.getPlayerManager();

        this.playersWithDurationItem = new HashMap<PubPlayer, PubItemDuration>();
        this.shipKillerPoints = new HashMap<Integer, Integer>();
        this.shipKilledPoints = new HashMap<Integer, Integer>();
        this.locationPoints = new HashMap<Location, Integer>();
        this.frequencyTimes = new HashMap<String, Long>();
        this.immuneFreqs = new HashMap<Integer, Long>();

        this.coupons = new HashMap<String, CouponCode>();
        
        this.itemBans = new HashMap<String, ArrayList<ItemBan>>();
        this.shopBans = new HashMap<String, ShopBan>();

        this.ipcReceivers = Collections.synchronizedList(new ArrayList<IPCReceiver>());

        try {
            this.store = new PubStore(m_botAction, context);
            initializePoints();
        } catch (Exception e) {
            Tools.printStackTrace("Error while initializing the money system", e);
        }

        m_botAction.ipcSubscribe(IPC_CHANNEL);

        regions = new MapRegions();
        reloadRegions();
        reloadCoords();
        reloadConfig();

    }

    /**
     * Reloads the used regions.
     * @see MapRegions
     */
    public void reloadRegions() {
        try {
            regions.clearRegions();
            regions.loadRegionImage(MAP_NAME + ".png");
            regions.loadRegionCfg(MAP_NAME + ".cfg");
        } catch (FileNotFoundException fnf) {
            Tools.printLog("Error: " + MAP_NAME + ".png and " + MAP_NAME + ".cfg must be in the data/maps folder.");
        } catch (javax.imageio.IIOException iie) {
            Tools.printLog("Error: couldn't read image");
        } catch (Exception e) {
            Tools.printLog("Could not load warps for " + MAP_NAME);
            Tools.printStackTrace(e);
        }
    }
    
    /**
     * Load all the coordinate related settings from a separate configuration file.
     */
    public void reloadCoords() {
        BotSettings cfg = new BotSettings(m_botAction.getBotSettings().getString("coords_config"));

        // Base blast spawn location in points
        coordBaseBlast=cfg.getPoint("BaseBlast", ":");
        // Base strike warp locations in tiles.
        coordsBaseStrike=cfg.getPointArray("BaseStrike", ",", ":");
        // Base terrier spawn location in tiles. (Frequency 0, 1, X)
        coordsBaseTerrier=cfg.getPointArray("BaseTerrier", ",", ":");
        // Flag saver spawn location in points.
        coordFlagSaver=cfg.getPoint("FlagSaver", ":");
        // Mega warp wormhole location in tiles.
        coordMegaWarp=cfg.getPoint("MegaWarp", ":");
        // Nuke base safe location in points.
        coordNukeBase=cfg.getPoint("NukeBase", ":");
        // Roof turret spawn location in tiles.
        coordRoofTurret=cfg.getPoint("RoofTurret", ":");
        
        //TODO: Handle situation in which the warp points aren't located.        
    }

    /** Default event requester */
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.PLAYER_DEATH);
    }

    /**
     * Gets default settings for the points: area and ship By points, we mean "money".
     */
    private void initializePoints() {

        String[] locations = m_botAction.getBotSettings().getString("point_location").split(",");
        for (String loc : locations) {
            String[] split = m_botAction.getBotSettings().getString("point_location" + loc).split(",");
            Location location = Location.valueOf(split[0].toUpperCase());
            locationPoints.put(location, Integer.parseInt(split[1]));
        }

        String[] pointsKiller = m_botAction.getBotSettings().getString("point_killer").split(",");
        String[] pointsKilled = m_botAction.getBotSettings().getString("point_killed").split(",");

        for (int i = 1; i <= 8; i++) {
            shipKillerPoints.put(i, Integer.parseInt(pointsKiller[i - 1]));
            shipKilledPoints.put(i, Integer.parseInt(pointsKilled[i - 1]));
        }

    }

    /**
     * The first shell used to buy an item from the {@link PubStore}.
     * <p>
     * This function does the initial checks if the player is currently dueling or if the player is the leader of the Kill-o-thon.
     * Depending on whether it's enabled, it will also do a check if the person is in a LT.
     * <br>
     * Ship and item dependent restrictions will be checked on later.
     * 
     * @param playerName Name of the person who wants to buy an item.
     * @param itemName Name of the product to be bought.
     * @param params Generally, only used for a target name if bought for someone else.
     * 
     * @see PubStore#buy(String, PubPlayer, String)
     * @see #executeItem(PubItem, PubPlayer, String)
     */
    private void buyItem(final String playerName, String itemName, String params) {
        boolean itemBanUpdateNeeded = false;
        boolean buyingForOther = false;
        if (!params.trim().isEmpty()) {
            PubItem prefetch = store.getItem(itemName);
            if (prefetch != null && (prefetch.isPlayerStrict() || prefetch.isPlayerOptional()))
                buyingForOther = true;
        }

        try {

            if (playerManager.isPlayerExists(playerName)) {

                // Wait, is this player dueling?
                if (context.getPubChallenge().isDueling(playerName)) {
                    m_botAction.sendSmartPrivateMessage(playerName, "You cannot buy an item while dueling.");
                    return;
                }

                // Kill-o-thon running and he's the leader?
                if (context.getPubKillSession().isLeader(playerName) && !buyingForOther) {
                    m_botAction.sendSmartPrivateMessage(playerName, "You cannot buy an item while being a leader of kill-o-thon.");
                    return;
                }
                
                // Is the player's ship on the list of ships that is restricted to only buy in safe?
                Player p = m_botAction.getPlayer(playerName);
                if(p != null && p.getShipType() != Tools.Ship.SPECTATOR && !buyingForOther) {
                    // Only check this global restriction for players that are actually in a ship.
                    if(canBuyAnywhere.isEmpty() || !canBuyAnywhere.contains((int) p.getShipType())) {
                        Region r = context.getPubUtil().getRegion(p.getXTileLocation(), p.getYTileLocation());
                        if (r != null && !(Region.SAFE.equals(r))) {
                            // No buying except in safe!
                            m_botAction.sendSmartPrivateMessage(playerName, Tools.shipName(p.getShipType()) + " can only !buy in safe.");
                            return;
                        }                        
                    }                    
                }
                
                // Special case of the above. Is the player on a LT, while outside a safe and LTs being restricted to only be able to buy in a safe.
                if(!canLTBuyAnywhere) {
                    if (p.isShip(Ship.LEVIATHAN) && p.isAttached()) {
                        Region r = context.getPubUtil().getRegion(p.getXTileLocation(), p.getYTileLocation());
                        if (r != null && !(Region.SAFE.equals(r))) {
                            m_botAction.sendPrivateMessage(playerName, "LTs must be in a safety zone to purchase items.");
                            return;
                        }
                    } else if (p.isShip(Ship.TERRIER) && p.hasAttachees()) {
                        Region r = context.getPubUtil().getRegion(p.getXTileLocation(), p.getYTileLocation());
                        if (r != null && !(Region.SAFE.equals(r))) {
                            LinkedList<Integer> playerIDs = p.getTurrets();
                            for (Integer i : playerIDs) {
                                Player a = m_botAction.getPlayer(i);
                                if (a != null && a.isShip(Ship.LEVIATHAN)) {
                                    m_botAction.sendPrivateMessage(playerName, "LTs must be in a safety zone to purchase items.");
                                    return;
                                }
                            }
                        }
                    }
                }
                
                // Is the player currently banned from using the shop?
                if(!shopBans.isEmpty() && shopBans.containsKey(playerName.toLowerCase())) {
                    if(shopBans.get(playerName.toLowerCase()).isShopBanned()) {
                        m_botAction.sendSmartPrivateMessage(playerName, "You have been banned from using the item shop.");
                        return;
                    } else {
                        shopBans.remove(playerName.toLowerCase());
                        saveBans();
                    }
                }

                // Is the player currently banned from using this specific item?
                if(!itemBans.isEmpty() && itemBans.containsKey(playerName.toLowerCase())) {
                    ArrayList<ItemBan> itemBanList = itemBans.get(playerName.toLowerCase());
                    PubItem itemWanted = store.getItem(itemName);
                    for(ItemBan itemban : itemBanList) {
                        if(itemWanted.equals(store.getItem(itemban.item))) {
                            if(itemban.isItemBanned()) {
                                m_botAction.sendSmartPrivateMessage(playerName, "You have been banned from buying this item from the shop.");
                                return;
                            } else {
                                itemBanList.remove(itemban);
                                itemBanUpdateNeeded = true;
                                break;
                            }
                        }
                    }
                    
                    if(itemBanUpdateNeeded) {
                        itemBans.put(playerName.toLowerCase(), itemBanList);
                        saveBans();
                    }
                }

                PubPlayer buyer = playerManager.getPlayer(playerName);

                // Retrieve the actual item. This inherently also checks for restrictions.
                final PubItem item = store.buy(itemName, buyer, params);
                final PubPlayer receiver;

                // Is it an item bought for someone else?
                // If yes, change the receiver for this player and not the buyer
                if (buyingForOther) {
                    receiver = context.getPlayerManager().getPlayer(params.trim());
                } else {
                    receiver = buyer;
                }

                // Execute the item!!
                executeItem(item, receiver, params);

                // Tell the world?
                if (item.isArenaItem()) {
                    m_botAction.sendArenaMessage("[BUY] " + playerName + " just bought a " + item.getDisplayName() + " for $" + item.getPrice() + ".", Tools.Sound.CROWD_OHH);
                }
                
                context.moneyLog("[BUY] " + playerName + " bought a " + item.getDisplayName() + " for $" + item.getPrice() + ".");

                // Querying once every !buy (!!!)
                // TODO: Possibly make a system that stores the info every 15 min?        		

                /*
                // Save this purchase
                int shipType = m_botAction.getPlayer(receiver.getPlayerName()).getShipType();
                // The query will be closed by PlayerManagerModule
                if (database!=null)
                m_botAction.SQLBackgroundQuery(database, null, "INSERT INTO tblPurchaseHistory "
                	+ "(fcItemName, fcBuyerName, fcReceiverName, fcArguments, fnPrice, fnReceiverShipType, fdDate) "
                	+ "VALUES ('"+Tools.addSlashes(item.getName())+"','"+Tools.addSlashes(buyer.getPlayerName())+"','"+Tools.addSlashes(receiver.getPlayerName())+"','"+Tools.addSlashes(params)+"','"+item.getPrice()+"','"+shipType+"',NOW())");
                */

            } else {
                m_botAction.sendSmartPrivateMessage(playerName, "You're not in the system to use !buy.");
            }

        } catch (PubException e) {
            m_botAction.sendSmartPrivateMessage(playerName, e.getMessage());
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }

    }

    /**
     * Executes a purchased item. The way this is done depends on the type of item that is bought.
     * <ul>
     *  <li>{@link PubPrizeItem}:       One or more timers are started to execute a *prize;
     *  <li>{@link PubShipUpgradeItem}: Gives a specific ship upgrade as *prize and starts a timer to remove it;
     *  <li>{@link PubCommandItem}:     Invokes a method that executes the needed actions;
     *  <li>{@link PubShipItem}:        Changes the player's ship and starts a timer to cancel it.
     * </ul>
     * @param item Item to be executed
     * @param receiver The person who receives the item. (Often equals the buyer of the item.)
     * @param params If the receiver isn't the person who bought it, his/her name will be in here as well.
     */
    public void executeItem(final PubItem item, final PubPlayer receiver, final String params) {

        Player player = m_botAction.getPlayer(receiver.getPlayerName());

        try {

            // PRIZE ITEM
            if (item instanceof PubPrizeItem) {

                PubPrizeItem itemPrize = (PubPrizeItem) item;

                //List<Integer> prizes = ((PubPrizeItem) item).getPrizes();
                final TimerTask task = new PrizeTask(itemPrize, receiver.getPlayerName());

                // Prize items every X seconds? (super/shield)
                try {
                    if (itemPrize.getPrizeSeconds() != 0 && item.hasDuration()) {
                        m_botAction.scheduleTask(task, 0, itemPrize.getPrizeSeconds() * Tools.TimeInMillis.SECOND);
                        // Or one shot?
                    } else {
                        task.run();
                    }
                } catch (IllegalStateException e) {
                    Tools.printLog("Exception ExecuteItem: " + item.getName() + " (params:" + params + ")");
                    return;
                }

                // If the item has a duration, start a timer to remove it when the item expires.
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
                                if (System.currentTimeMillis() - receiver.getLastDeath() > duration.getSeconds() * 1000) {
                                    m_botAction.sendUnfilteredPrivateMessage(receiver.getPlayerName(), "*shipreset");
                                    m_botAction.giveBounty(receiver.getPlayerName(), bounty);
                                }
                                m_botAction.sendSmartPrivateMessage(receiver.getPlayerName(), "Item '" + item.getName() + "' lost.");
                            }
                        };
                        m_botAction.scheduleTask(timer, duration.getSeconds() * 1000);
                    }
                }
            }

            // SHIP UPGRADE ITEM (same as PubPrizeItem)
            if (item instanceof PubShipUpgradeItem) {
                List<Integer> prizes = ((PubShipUpgradeItem) item).getPrizes();
                for (int prizeNumber : prizes) {
                    m_botAction.specificPrize(receiver.getPlayerName(), prizeNumber);
                }
                if (item.hasDuration()) {
                    final PubItemDuration duration = item.getDuration();
                    m_botAction.sendSmartPrivateMessage(receiver.getPlayerName(), "You have " + duration.getSeconds() + " seconds to use your item.");
                    if (duration.hasTime()) {
                        TimerTask timer = new TimerTask() {
                            public void run() {
                                int bounty = m_botAction.getPlayer(receiver.getPlayerName()).getBounty();
                                if (System.currentTimeMillis() - receiver.getLastDeath() > duration.getSeconds() * 1000) {
                                    m_botAction.sendUnfilteredPrivateMessage(receiver.getPlayerName(), "*shipreset");
                                    m_botAction.giveBounty(receiver.getPlayerName(), bounty);
                                }
                                m_botAction.sendSmartPrivateMessage(receiver.getPlayerName(), "Item '" + item.getName() + "' lost.");
                            }
                        };
                        m_botAction.scheduleTask(timer, duration.getSeconds() * 1000);
                    }
                }
            }

            // COMMAND ITEM
            else if (item instanceof PubCommandItem) {
                String command = ((PubCommandItem) item).getCommand();
                // Find the accompanying method.
                Method method = this.getClass().getDeclaredMethod("itemCommand" + command, String.class, String.class);
                // And run it.
                method.invoke(this, receiver.getPlayerName(), params);
            }

            // SHIP ITEM
            else if (item instanceof PubShipItem) {

                if (item.hasDuration()) {
                    PubItemDuration duration = item.getDuration();
                    if (duration.hasTime()) {
                        final int currentShip = (int) player.getShipType();
                        TimerTask timer = new TimerTask() {
                            public void run() {
                                m_botAction.setShip(receiver.getPlayerName(), currentShip);
                            }
                        };
                        m_botAction.scheduleTask(timer, duration.getSeconds() * 1000);
                    } else if (duration.hasDeaths()) {
                        playersWithDurationItem.put(receiver, duration);
                    }
                }

                receiver.setShipItem((PubShipItem) item);
                m_botAction.setShip(receiver.getPlayerName(), ((PubShipItem) item).getShipNumber());

            }

        } catch (Exception e) {
            Tools.printStackTrace(e);
        }

    }

    /**
     * Handles the !donate command. (Anyone)<br>
     * This command uses two arguments, being the targetname and the amount to be donated.
     * @param sender Player who sent the !donate command
     * @param command The command including its arguments
     */
    private void doCmdDonate(String sender, String command) {

        if (!donationEnabled) {
            m_botAction.sendSmartPrivateMessage(sender, "You cannot donate at this time, feature disabled.");
            return;
        }

        if (command.length() < 8) {
            m_botAction.sendSmartPrivateMessage(sender, "Try !donate <name>.");
            return;
        }

        command = command.substring(8).trim();
        if (command.contains(":")) {
            String[] split = command.split("\\s*:\\s*");
            if (split.length != 2) {
                m_botAction.sendSmartPrivateMessage(sender, "You must specify both a player and an amount to donate. Example: !donate playerA:1000");
                return;
            }
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

            // Most checks are done now. Time to do the actual donating.
            PubPlayer pubPlayer = playerManager.getPlayer(name, false);
            PubPlayer pubPlayerDonater = playerManager.getPlayer(sender, false);
            if (pubPlayer != null && pubPlayerDonater != null) {
                if (pubPlayerDonater.getMoney() < Integer.valueOf(money)) {
                    m_botAction.sendSmartPrivateMessage(sender, "You don't have $" + Integer.valueOf(money) + " to donate.");
                    return;
                }
                if (!pubPlayerDonater.donate(name)) {
                    m_botAction.sendSmartPrivateMessage(sender, "You have to wait before donating to this player again.");
                    return;
                }

                int currentMoney = pubPlayer.getMoney();
                int moneyToDonate = Integer.valueOf(money);

                pubPlayer.addMoney(moneyToDonate);
                pubPlayerDonater.removeMoney(moneyToDonate);
                m_botAction.sendSmartPrivateMessage(sender, "$" + moneyToDonate + " sent to " + pubPlayer.getPlayerName() + ".");
                m_botAction.sendSmartPrivateMessage(pubPlayer.getPlayerName(), sender + " sent you $" + moneyToDonate + ", you have now $" + (moneyToDonate + currentMoney) + ".");

                context.moneyLog("[DONATE] " + sender + " donated $" + moneyToDonate + " to " + pubPlayer.getPlayerName() + ".");
                // The query will be closed by PlayerManagerModule
                if (database != null)
                    m_botAction.SQLBackgroundQuery(database, null, "INSERT INTO tblPlayerDonations " + "(fcName, fcNameTo, fnMoney, fdDate) " + "VALUES ('" + Tools.addSlashes(sender) + "','"
                            + Tools.addSlashes(pubPlayer.getPlayerName()) + "','" + moneyToDonate + "',NOW())");

            } else {
                m_botAction.sendSmartPrivateMessage(sender, "Player not found.");
            }
        } else {
            m_botAction.sendSmartPrivateMessage(sender, "Invalid argument");
        }
    }

    /**
     * Handles the undocumented !addmoney command. (Sysop+)<br>
     * This generates money from nothing, with the main intent being debugging.
     * 
     * @param sender The person who sent the command.
     * @param command The actual command including parameters.
     */
    public void doCmdAddMoney(String sender, String command) {

        command = command.substring(10).trim();
        if (command.contains(":")) {
            // Fetch the arguments.
            String[] split = command.split("\\s*:\\s*");
            String name = split[0];
            String money = split[1];
            int moneyInt = Integer.valueOf(money);
            
            // Do the magic.
            PubPlayer pubPlayer = playerManager.getPlayer(name, false);
            if (pubPlayer != null) {
                int currentMoney = pubPlayer.getMoney();
                if (moneyInt > 0) {
                    pubPlayer.addMoney(moneyInt);
                    sqlMoney(moneyInt);
                    m_botAction.sendSmartPrivateMessage(pubPlayer.getPlayerName(), sender + " added you $" + (Integer.valueOf(money)) + ".");
                } else {
                    pubPlayer.removeMoney(moneyInt);
                    sqlMoney(-Math.abs(moneyInt));
                    m_botAction.sendSmartPrivateMessage(pubPlayer.getPlayerName(), sender + " removed you $" + (Integer.valueOf(money)) + ".");
                }

                m_botAction.sendSmartPrivateMessage(sender, pubPlayer.getPlayerName() + " has now $" + (currentMoney + Integer.valueOf(money)) + " (before: $" + currentMoney + ")");

            } else {
                playerManager.addMoney(name, Integer.valueOf(money), true);
                sqlMoney(Integer.valueOf(money));
                m_botAction.sendSmartPrivateMessage(sender, name + " has now $" + money + " more money.");
            }
        } else {
            m_botAction.sendSmartPrivateMessage(sender, "Invalid argument");
        }
    }

    /**
     * Handles the !award command. (SMod+ or coupon operator)<br>
     * Awards money from the pot to a player.
     * @param name Name of the person who initiated the command.
     * @param cmd The command including arguments. These include a name and an amount.
     */
    public void doCmdAward(String name, String cmd) {
        if (!cmd.contains(":"))
            return;
        String[] args = cmd.substring(cmd.indexOf(" ") + 1).split(":");
        if (args.length != 2)
            return;
        try {
            int amount = Integer.valueOf(args[1]);
            if (amount < 1 || amount > 100000)
                m_botAction.sendSmartPrivateMessage(name, "Amount must be greater than 0 and less than $100000 and !pot");
            else if (amount > getMoneyPot())
                m_botAction.sendSmartPrivateMessage(name, "I only have $" + getMoneyPot() + " available in the pot.");
            else {
                playerManager.addMoney(args[0], amount, true);
                playerManager.getPlayer(m_botAction.getBotName(), false).removeMoney(amount);
                m_botAction.sendChatMessage(name + " has awarded " + args[0] + " $" + amount);
                m_botAction.sendSmartPrivateMessage(name, "" + args[0] + " has been awarded $" + amount + " from my money pot ($" + getMoneyPot() + " left)");
                m_botAction.sendSmartPrivateMessage(args[0], "Congratulations, you have been awarded $" + amount + "!");
                if (database != null)
                    m_botAction.SQLBackgroundQuery(database, null, "INSERT INTO tblPlayerDonations " + "(fcName, fcNameTo, fnMoney, fdDate) " + "VALUES ('" + Tools.addSlashes("BOTPOT-" + name)
                            + "','" + Tools.addSlashes(args[0]) + "','" + amount + "',NOW())");
            }
        } catch (NumberFormatException e) {
            m_botAction.sendSmartPrivateMessage(name, "Invalid dollar amount.");
        }
    }

    /**
     * Updates the total amount of money(?)
     * @param money Amount that is added or removed.
     */
    private void sqlMoney(int money) {
        if (updateMoney != null) {
            try {
                updateMoney.setInt(0, money);
                updateMoney.executeUpdate();
            } catch (SQLException e) {
                Tools.printStackTrace(e);
            }
        } else {
            String query = "UPDATE tblMoneyCode SET fnMoney = (fnMoney + " + money + ") WHERE fcCode = 'OWNER'";
            m_botAction.SQLBackgroundQuery(database, null, query);
        }
    }
    
    /**
     * Edits a particular CFG setting value (meant to be used for on-the-fly PubStore modification).
     * Dangerous!
     * 
     * @param sender
     * @param msg
     *          The command with syntax !edit CFG_key=Value
     */
    private void doCmdEditCfg(String sender, String msg) {
        msg = msg.substring(6);
        String key = msg.substring(0, msg.indexOf("="));
        String val = msg.substring(msg.indexOf("=") + 1);
        if (key == null || val == null) return;
        BotSettings cfg = m_botAction.getBotSettings();
        String oldValue = cfg.getString(key);
        if (oldValue != null) {
            cfg.put(key, val);
            m_botAction.sendPrivateMessage(sender, "Updated key " + key + " was successful... ");
            m_botAction.sendPrivateMessage(sender, "Old value: " + oldValue);
            m_botAction.sendPrivateMessage(sender, "New value: " + val);
            cfg.save();
            reloadConfig();
            m_botAction.sendPrivateMessage(sender, "Store config reloaded...");
        } else
            m_botAction.sendPrivateMessage(sender, "Key not found: " + key);
    }
    
    /**
     * Lists the configuration values for the pub store items.
     * 
     * @param sender
     */
    private void doCmdViewStoreCfg(String sender) {
        ArrayList<String> cfg = new ArrayList<String>();
        
        File f = m_botAction.getBotSettingsPath();
        try {
            // read the actual cfg file line by line and store the relevant information we want to relay
            BufferedReader read = new BufferedReader(new FileReader(f));
            
            boolean begin = false;
            String line = read.readLine();
            while (line != null) {
                if (line.contains("END STORE HELP"))
                    begin = true;
                else if (begin)
                    cfg.add(line);
                if (line.contains("END STORE ITEMS"))
                    line = null;
                if (line != null)
                    line = read.readLine();
            }
            
            read.close();
            
            m_botAction.privateMessageSpam(sender, cfg.toArray(new String[cfg.size()]));
            
        } catch (Exception e) {
            Tools.printStackTrace(e);
            m_botAction.sendPrivateMessage(sender, "Error reading configuration file.");
        }        
    }

    /**
     * Lists the configuration guide for the pub store items.
     * 
     * @param sender
     */
    private void doCmdStoreCfgHelp(String sender) {     
        ArrayList<String> cfg = new ArrayList<String>();
        
        File f = m_botAction.getBotSettingsPath();
        try {
            // read the actual cfg file line by line and store the relevant information we want to relay
            BufferedReader read = new BufferedReader(new FileReader(f));
            
            boolean begin = false;
            String line = read.readLine();
            while (line != null) {
                if (line.contains("BEGIN STORE HELP"))
                    begin = true;
                else if (begin)
                    cfg.add(line);
                if (line.contains("END STORE HELP"))
                    line = null;
                if (line != null)
                    line = read.readLine();
            }
            
            read.close();
            
            m_botAction.privateMessageSpam(sender, cfg.toArray(new String[cfg.size()]));
            
        } catch (Exception e) {
            Tools.printStackTrace(e);
            m_botAction.sendPrivateMessage(sender, "Error reading configuration file.");
        }          
    }

    /**
     * Handles the !buy command when absent of arguments. (Anyone)
     * <p>
     * This method displays a list of buyable items to the player. Generally this will be only the items
     * that can be bought for the ship the player is currently using (including spectator). This is done
     * to make the bot less spammy with its messages. When a full list view is requested, then this is also
     * handled by this function.
     * <p>
     * Besides !buy, this function is also used by !items, !fullbuylist and !fullitemlist and their abbreviations.
     * @param sender The person who used the command.
     * @param displayAll Whether or not to display the full list of items.
     */
    private void doCmdItems(String sender, boolean displayAll) {
        Class<? extends PubItem> currentClass = PubItem.class;
        ArrayList<String> lines = new ArrayList<String>();
        
        Player p = m_botAction.getPlayer(sender);
        // Store the shiptype to minimize CPU usage. If no player was found, treat it as if the player is a spectator.
        Integer shipType = (int) (p == null ? Tools.Ship.SPECTATOR : p.getShipType());

        if (!store.isOpened()) {
            lines.add("The store is closed, no items available.");
        } else {
            if(displayAll)
                lines.add("List of all our store items. Each item has a set of restrictions.");
            else
                lines.add("As a " + Tools.shipName(shipType) + " you can buy the following store items. Each item has a set of restrictions.");
            
            // Iterate through all of the available store items.
            for (String itemName : store.getItems().keySet()) {

                PubItem item = store.getItems().get(itemName);
                if (item.getAbbreviations().contains(itemName))
                    continue;

                // Check if the item should be displayed.
                // This is done before the change of type detection to avoid headers with empty lists.
                if(!displayAll) {
                    PubItemRestriction restriction = item.getRestriction();
                    
                    // We need to skip displaying the item in the following situation:
                    // When there are restrictions on the item, and the player is in spec, but it's not buyable from spec
                    // or when there are restrictions, there are ship restrictions, the player is in a ship and his ship is on the list. 
                    if(restriction != null
                            && ((shipType == Tools.Ship.SPECTATOR && !restriction.isBuyableFromSpec())
                                    || (shipType != Tools.Ship.SPECTATOR 
                                        && restriction.getRestrictedShips().contains(shipType)))) {
                        continue;
                    }
                }
                
                // Whenever the item is an instance of a different class than the current class, 
                // update the current class and add a header for a new section.
                if (item instanceof PubPrizeItem && !currentClass.equals(PubPrizeItem.class)) {
                    lines.add("Prizes:");
                    currentClass = PubPrizeItem.class;
                } else if (item instanceof PubShipUpgradeItem && !currentClass.equals(PubShipUpgradeItem.class)) {
                    lines.add("Ship Upgrades:");
                    currentClass = PubShipUpgradeItem.class;
                } else if (item instanceof PubShipItem && !currentClass.equals(PubShipItem.class)) {
                    lines.add("Ships:");
                    currentClass = PubShipItem.class;
                } else if (item instanceof PubCommandItem && !currentClass.equals(PubCommandItem.class)) {
                    lines.add("Specials:");
                    currentClass = PubCommandItem.class;
                }

                // Add a line specific for this item, detailing the command, description and price.
                String line = " !buy " + item.getName();
                if (item.isPlayerOptional()) {
                    line += "*";
                } else if (item.isPlayerStrict()) {
                    line += "**";
                }
                line = Tools.formatString(line, 21);
                line += " -- " + (item.getDescription() + " (" + formatMoney(item.getPrice()) + ")");
                lines.add(line);
            }

            // Some final notes.
            lines.add("Legend: *Target optional **Target required (!buy item:PlayerName)");
            lines.add("Use !iteminfo <item> for more info about the specified item and its restrictions.");
            if(!displayAll)
                lines.add("Use !fullbuylist to display all the available items in store.");

        }

        m_botAction.smartPrivateMessageSpam(sender, lines.toArray(new String[lines.size()]));

    }

    /**
     * Handles the initial !buy command. (Anyone)<p>
     * Does an initial check on whether the person is part of a LT. Following this, a check is done 
     * if an argument has been passed by the initiator. If no arguments are passed, {@link #doCmdItems(String, boolean) doCmdItems}
     * is called upon. Otherwise the function {@link #buyItem(String, String, String) buyItem} is started.
     * @param sender The person who sent the command.
     * @param command The actual command including optional parameters.
     */
    private void doCmdBuy(String sender, String command) {
        Player p = m_botAction.getPlayer(sender);
        if (p == null) {
            return;
        }
        
        if (!command.contains(" ")) {
            doCmdItems(sender, false);
            return;
        }
        command = command.substring(command.indexOf(" ")).trim();
        if (command.indexOf(":") != -1) {
            String params = command.substring(command.indexOf(":") + 1).trim();
            command = command.substring(0, command.indexOf(":")).trim();
            buyItem(sender, command, params);
        } else {
            buyItem(sender, command, "");
        }
    }

    /**
     * Handles the !debugobj command. (Mod+)
     * <p>
     * This command is meant to be used for debugging purposes. It displays the average amount of *obj sent per minute.
     * @param sender Person who sent the command.
     * @param command The actual command, no parameters.
     */
    private void doCmdDebugObj(String sender, String command) {

        m_botAction.sendSmartPrivateMessage(sender, "Average of " + LvzMoneyPanel.totalObjSentPerMinute() + " *obj sent per minute.");
    }

    /**
     * Handles the !bankrupt command. (Sysop+)
     * <p>
     * This removes all of the money from a player.
     * @param sender Person who sent the command.
     * @param command The command, including parameters.
     */
    private void doCmdBankrupt(String sender, String command) {

        String name = sender;
        if (command.contains(" ")) {
            name = command.substring(command.indexOf(" ")).trim();
            PubPlayer pubPlayer = playerManager.getPlayer(name, false);
            if (pubPlayer != null) {
                int money = pubPlayer.getMoney();
                pubPlayer.setMoney(0);
                m_botAction.sendSmartPrivateMessage(sender, pubPlayer.getPlayerName() + " has now $0 (before: $" + money + ")");
            } else {
                m_botAction.sendSmartPrivateMessage(sender, "Player '" + name + "' not found.");
            }
        }
    }

    /**
     * Handles the !toggledonation command. (Mod+)
     * <p>
     * Enables or disables the !donate command.
     * @param sender
     */
    private void doCmdToggleDonation(String sender) {

        donationEnabled = !donationEnabled;
        if (donationEnabled) {
            m_botAction.sendSmartPrivateMessage(sender, "!donate is now enabled.");
        } else {
            m_botAction.sendSmartPrivateMessage(sender, "!donate is now disabled.");
        }
    }

    /**
     * Handles the !iteminfo command. (Anyone)
     * <p>
     * Displays detailed item info on an item from the PubStore.
     * This includes, but is not limited to, a detailed description, price and restrictions.
     * @param sender The person who issued the command.
     * @param command The command, including parameter
     */
    private void doCmdItemInfo(String sender, String command) {
        Player p = m_botAction.getPlayer(sender);
        if (p == null)
            return;
        
        if (!command.contains(" ")) {
            m_botAction.sendSmartPrivateMessage(sender, "You need to supply an item.");
            return;
        }
        
        // Fetch the item.
        String itemName = command.substring(command.indexOf(" ")).trim();
        PubItem item = store.getItem(itemName);
        if (item == null) {
            m_botAction.sendSmartPrivateMessage(sender, "Item '" + itemName + "' not found.");
        } else {
            // Display all the properties of this item.
            m_botAction.sendSmartPrivateMessage(sender, "Item: " + item.getName() + " (" + item.getDescription() + ")");
            m_botAction.sendSmartPrivateMessage(sender, "Price: $" + item.getPrice());

            if (item.isPlayerOptional()) {
                m_botAction.sendSmartPrivateMessage(sender, "Targetable: Optional");
            } else if (item.isPlayerStrict()) {
                m_botAction.sendSmartPrivateMessage(sender, "Targetable: Required");
            } else {
                m_botAction.sendSmartPrivateMessage(sender, "Targetable: No");
            }

            // When it has the restriction flag, display the restrictions.
            if (item.isRestricted()) {

                PubItemRestriction r = item.getRestriction();

                // Not really a restriction, that's why this is before.
                if (!r.isBuyableFromSpec()) {
                    m_botAction.sendSmartPrivateMessage(sender, "Buyable from spec: No");
                } else {
                    m_botAction.sendSmartPrivateMessage(sender, "Buyable from spec: Yes");
                }

                // Real restrictions
                m_botAction.sendSmartPrivateMessage(sender, "Restrictions:");

                if (r.getRestrictedShips().size() == 8) {
                    m_botAction.sendSmartPrivateMessage(sender, " - Cannot be bought while playing");
                } else {
                    String ships = "";
                    for (int i = 1; i < 9; i++) {
                        if (!r.getRestrictedShips().contains(i)) {
                            ships += i + ",";
                        }
                    }
                    m_botAction.sendSmartPrivateMessage(sender, " - Available only for ship(s) : " + ships.substring(0, ships.length() - 1));
                }
                if (r.getMaxConsecutive() != -1) {
                    m_botAction.sendSmartPrivateMessage(sender, " - Maximum of " + r.getMaxConsecutive() + " consecutive purchase(s)");
                }
                if (r.getMaxPerLife() != -1) {
                    m_botAction.sendSmartPrivateMessage(sender, " - Maximum of " + r.getMaxPerLife() + " per life");
                }
                if (r.getMaxPerSecond() != -1) {
                    m_botAction.sendSmartPrivateMessage(sender, " - Maximum of 1 every " + r.getMaxPerSecond() + " seconds");
                }
                if (r.getMaxArenaPerMinute() != -1) {
                    m_botAction.sendSmartPrivateMessage(sender, " - Maximum of 1 every " + r.getMaxArenaPerMinute() + " minutes for the whole arena");
                }
                if (r.getMaxFreqPerMinute() != -1) {
                    m_botAction.sendSmartPrivateMessage(sender, " - Maximum of 1 every " + r.getMaxFreqPerMinute() + " minutes for the freq that bought it");
                }
                if (r.getMaxFreqPerRound() != -1) {
                    m_botAction.sendSmartPrivateMessage(sender, " - Maximum of " + r.getMaxFreqPerRound() + " per round for the freq that bought it");
                }
                if (r.isPublicFreqOnly()) {
                    m_botAction.sendSmartPrivateMessage(sender, " - Available only for frequencies 0 and 1");
                }

            }

            // If an item has a duration, display the duration.
            if (item.hasDuration()) {
                m_botAction.sendSmartPrivateMessage(sender, "Durations:");
                PubItemDuration d = item.getDuration();
                if (d.getDeaths() != -1 && d.getSeconds() != -1 && d.getSeconds() > 60) {
                    m_botAction.sendSmartPrivateMessage(sender, " - " + d.getDeaths() + " life(s) or " + (int) (d.getSeconds() / 60) + " minutes");
                } else if (d.getDeaths() != -1 && d.getSeconds() != -1 && d.getSeconds() <= 60) {
                    m_botAction.sendSmartPrivateMessage(sender, " - " + d.getDeaths() + " life(s) or " + (int) (d.getSeconds()) + " seconds");
                } else if (d.getDeaths() != -1) {
                    m_botAction.sendSmartPrivateMessage(sender, " - " + d.getDeaths() + " life(s)");
                } else if (d.getSeconds() != -1 && d.getSeconds() > 60) {
                    m_botAction.sendSmartPrivateMessage(sender, " - " + (int) (d.getSeconds() / 60) + " minutes");
                } else if (d.getSeconds() != -1 && d.getSeconds() <= 60) {
                    m_botAction.sendSmartPrivateMessage(sender, " - " + (int) (d.getSeconds()) + " seconds");
                }
            }

            // Other item properties.
            if (item.getImmuneTime() > 0)
                m_botAction.sendSmartPrivateMessage(sender, " - The affected freq(s) become immune to this item for " + item.getImmuneTime() + " seconds");

        }
    }

    /**
     * Handles the !richest command. (Anyone)
     * <p>
     * Displays the five richest players who are currently online.
     * @param sender Sender of the command.
     * @param command The command, no parameters.
     */
    private void doCmdRichest(String sender, String command) {
        HashMap<String, Integer> players = new HashMap<String, Integer>();

        // Copy over the current list into a new list. Mainly to avoid concurrent modification exceptions.
        Iterator<Player> it = m_botAction.getPlayerIterator();
        while (it.hasNext()) {
            PubPlayer player = playerManager.getPlayer(it.next().getPlayerName());
            if (player != null) {
                players.put(player.getPlayerName(), player.getMoney());
            }
        }
        
        // Sort the copy.
        LinkedHashMap<String, Integer> richest = sort(players, false);

        // And get the top five.
        Iterator<Entry<String, Integer>> it2 = richest.entrySet().iterator();
        int count = 0;
        while (it2.hasNext() && count < 5) {
            Entry<String, Integer> entry = it2.next();
            m_botAction.sendSmartPrivateMessage(sender, 
            		Tools.formatString( (++count + ") " + entry.getKey()), 25 ) + " ..."
            		+ Tools.rightString( ("$" + entry.getValue()), 15) );
        }
    }
    
    /**
     * Handles the !richestall command. (Anyone)
     * <p>
     * Displays the five richest players, on or offline.
     * @param sender Sender of the command.
     * @param command The command, no parameters.
     */
    private void doCmdRichestAll(String sender, String command) {
    	String query = "SELECT fcName, fnMoney FROM tblPlayerStats ORDER BY fnMoney DESC LIMIT 0, 10";
    	try {
    		ResultSet r = m_botAction.SQLQuery("pubstats", query);
    		int count = 0;
    		while (r.next()) {
                m_botAction.sendSmartPrivateMessage(sender, 
                		Tools.formatString( ++count + ") " + r.getString("fcName"), 25 ) + " ..."
                		+ Tools.rightString( ("$" + r.getInt("fnMoney") ), 15 ) );
    		}
    		m_botAction.SQLClose( r );
    	} catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(sender, "Error while loading results. Please try again later.");    		
    	}    	
    }

    
    /**
     * Handles the !fruit command. (Anyone)
     * <p>
     * Pulls the handle of the one-armed bandit (aka fruit machine)
     * in an ill-fated attempt to earn more money.
     * @param sender Sender of the command.
     * @param command Amount to throw away
     */
    private void doCmdFruit(String sender, String command) {
        int bet = 0;
        int iterations = 1;
        int winnings = 0;
        
        if (command.contains(":")) {
            String[] parsed = command.split(":");
            if (parsed.length != 2) {
                m_botAction.sendPrivateMessage(sender, "Format:  !fruit 10:5  (Bet 10 x5 times) or !fruit 50  (Bet 50)");
                return;                
            }
            try {
                bet = Integer.parseInt(parsed[0]);
                iterations = Integer.parseInt(parsed[1]);
            } catch(Exception e) {
                m_botAction.sendPrivateMessage(sender, "Format:  !fruit 10:5  (Bet 10 x5 times) or !fruit 50  (Bet 50)");
                return;
            }
        } else {        
            try {
                bet = Integer.parseInt(command);
            } catch(Exception e) {
                m_botAction.sendPrivateMessage(sender, "Provide a # to use the fruit machine (between 10 and 1000). E.g., !fruit 50, or !fruit 50:5 to play 5 times for 50.");
                return;
            }
        }
        
        if (bet < 10 || bet > 500) {
            m_botAction.sendPrivateMessage(sender, "Provide an amount between 10 and 500. (To bet larger amounts, use !fruit amt:times, e.g., !fruit 100:10 to bet 100 for 10 total pulls of the fruit machine)");
            return;
        }
        
        if (iterations > 10 || iterations < 1) {
            m_botAction.sendPrivateMessage(sender, "Bot only accepts 10 bets at a time maximum (and, of course, 1 minimum).");
            return;
        }
        
        PubPlayer pp = playerManager.getPlayer(sender, false);
        if (pp == null ) {
            m_botAction.sendPrivateMessage(sender, "Can't locate you. Try entering pub.");
            return;
        }
        
        if (pp.getMoney() < (bet * iterations)) {
            m_botAction.sendSmartPrivateMessage(sender, "You don't have $" + bet + " to bet.");
            return;
        }
        
        for (int j=0; j<iterations; j++) {
            Random r = new Random();
            int[] slots = new int[3];
            for (int i=0; i<3; i++)
                slots[i] = r.nextInt(10);
            int winFactor = 0;
            String winMsg = "";

            if (slots[0] == slots[1] && slots[1] == slots[2]) {
                switch (slots[0]) {
                case 0:
                    winFactor = 5;
                    winMsg = "YOU'RE BEING WATCHED JACKPOT!";
                    break;
                case 1:
                    winFactor = 15;
                    winMsg = "> WARBIRD JACKPOT! <";
                    break;
                case 2:
                    winFactor = 80;
                    winMsg = ">>>> !!! JAVELIN JACKPOT !!! <<<";
                    break;
                case 3:
                    winFactor = 50;
                    winMsg = ">> SPIDER JACKPOT!! <<";
                    break;
                case 4:
                    winFactor = 200;
                    winMsg = ">>>>>>>>> !!!! OMGOMGOMG .. YES!! LEVIATHAN JACKPOT !!!!! <<<<<<<<";
                    break;
                case 5:
                    winFactor = 60;
                    winMsg = ">>> !!!TERRIER JACKPOT!!! <<<";
                    break;
                case 6:
                    winFactor = 8;
                    winMsg = "WEASEL JACKPOT!!";
                    break;
                case 7:
                    winFactor = 30;
                    winMsg = ">> LANCASTER JACKPOT!! <<";
                    break;
                case 8:
                    winFactor = 10;
                    winMsg = "SHARK JACKPOT!";
                    break;
                case 9:
                    winFactor = 100;
                    winMsg = ">>>>>>> !!!! YEAHHHHHH!! NIGHWASP JACKPOT !!!! <<<<<<";
                    break;
                }
            } else {
                int[] hits = new int[10];
                for (int i=0; i<10; i++) {
                    for (int y=0; y<3; y++)
                        if (slots[y] == i)
                            hits[i]++;
                }

                if (hits[1] == 1 && hits[3] == 1 && hits[7] == 1) {
                    winFactor = 5;
                    winMsg = "All Fighter Matchup!";                
                } else if (hits[3] + hits[7] == 3) {
                    winFactor = 3;
                    winMsg = "Basefighter Matchup!";
                } else if (hits[5] == 1 && hits[3] == 1 && hits[8] == 1) {
                    winFactor = 8;
                    winMsg = "Basing Team Matchup!";
                } else if (hits[5] == 1 && hits[7] == 1 && hits[8] == 1) {
                    winFactor = 7;
                    winMsg = "Alt. Basing Matchup!";
                } else if (hits[2] == 1 && hits[4] == 1 && hits[9] == 1) {
                    winFactor = 6;
                    winMsg = "Bombing Run Matchup!";
                } else if (hits[4] == 2 && hits[5] == 1 ) {
                    winFactor = 3;
                    winMsg = "Double LeviTerr Matchup!";
                } else if (hits[4] == 1 && hits[5] >= 1 ) {
                    winFactor = 2;
                    winMsg = "LeviTerr Matchup!";
                } else if (hits[5] >= 1) {
                    // Each Terr has a 50% chance of giving a free play
                    for (int k=0; k<hits[5]; k++)
                        if (r.nextInt(3) == 0)
                            winFactor = 1;
                }
            }

            String rollmsg =
                "[" + Tools.centerString( getShipNameSpecial(slots[0]), 8 ).toUpperCase() + "]   " +
                "[" + Tools.centerString( getShipNameSpecial(slots[1]), 8 ).toUpperCase() + "]   " +
                "[" + Tools.centerString( getShipNameSpecial(slots[2]), 8 ).toUpperCase() + "]   ";
                                            
            if (winFactor > 0) {
                if (winFactor > 1) {
                    rollmsg += " $$ " + (bet * winFactor) + " $$";
                    //                                        
                    //[  SPID  ]   [  SPID  ]   [  LANC  ]    
                    m_botAction.sendPrivateMessage(sender, 
                            Tools.centerString( "WIN!  " + winMsg + "  WIN!", 50 ),
                            Tools.Sound.VICTORY_BELL );
                    winnings += ((bet * winFactor) - bet);
                    fruitStats[0] += ((bet * winFactor) - bet);
                } else {
                    rollmsg += "(free play)";
                    //m_botAction.sendPrivateMessage(sender, "A Terr has ported you to safety; you keep your bet." );
                }
            } else {
                rollmsg += "(no win)";
                winnings -= bet;
                fruitStats[1] += bet;
            }
            m_botAction.sendPrivateMessage(sender, rollmsg);
        }
        pp.setMoney( pp.getMoney() + winnings );
        
    }
    
    private String getShipNameSpecial( int shipNumber ) {
        switch( shipNumber ){
        case Tools.Ship.SPECTATOR:
            return "Spec";
        case Tools.Ship.WARBIRD:
            return "WB";
        case Tools.Ship.JAVELIN:
            return "Jav";
        case Tools.Ship.SPIDER:
            return "Spid";
        case Tools.Ship.LEVIATHAN:
            return "Levi";
        case Tools.Ship.TERRIER:
            return "Terr";
        case Tools.Ship.WEASEL:
            return "X";
        case Tools.Ship.LANCASTER:
            return "Lanc";
        case Tools.Ship.SHARK:
            return "Shark";
        case 9:
            return "N'Wasp";
        }
        return "UFO";
    }

    /**
     * Handles the !fruitinfo command. (Anyone)
     * <p>
     * Shows information related to the fruit machine/slot machine.
     * @param sender Sender of the command.
     * @param command Amount to throw away
     */
    private void doCmdFruitInfo(String sender) {
        String[] msg = {
                "      TRENCH WARS Fruit Machine: Revenge of the Levi",
                "[PAYOUT TABLE] - Given as a multiplier of amount bet",
                "3 SPECTATORS ... x5            3 SPIDERS    ... x50",
                "3 WEASELS    ... x8            3 TERRIERS   ... x60",
                "3 SHARKS     ... x10           3 JAVELINS   ... x80",
                "3 WARBIRDS   ... x15           3 NIGHTWASPS ... x100",
                "3 LANCS      ... x30           3 LEVIATHANS ... x200",
                "[OTHER PAYOUTS]",
                "Basing Team (Terr, Shark, Spider)           ... x8",
                "Alternate Basing Team (Terr, Shark, Lanc)   ... x7",
                "Bombing Run (Jav, NWasp, Levi)              ... x6",
                "All Fighter (WB, Lanc, Spider)              ... x5",
                "Double LeviTerr (Terr, 2 Levis)             ... x5",
                "Base Fighter (any 3 Lancs or Spiders)       ... x3",
                "LeviTerr (Terr, Levi)                       ... x2",
                "Portal (every Terr)   ... 33% CHANCE FOR FREE PLAY",
        };
        m_botAction.privateMessageSpam(sender, msg);
    }

    /**
     * Handles the !lastkill command. (Anyone)
     * <p>
     * Displays the last kill made by this player, including various details like money earned.
     * @param sender Player who issued the command.
     */
    private void doCmdLastKill(String sender) {
        PubPlayer player = playerManager.getPlayer(sender);
        if (player != null) {

            if (player.getLastKillKillerShip() == -1) {
                m_botAction.sendSmartPrivateMessage(sender, "You haven't killed anyone yet.");
                return;
            }

            int shipKiller = player.getLastKillKillerShip();
            int shipKilled = player.getLastKillKilledShip();
            Location location = player.getLastKillLocation();

            // Money from the ship
            int moneyKiller = shipKillerPoints.get(shipKiller);
            int moneyKilled = shipKillerPoints.get(shipKilled);

            // Bonus money earned from the location.
            int moneyByLocation = 0;
            if (locationPoints.containsKey(location)) {
                moneyByLocation = locationPoints.get(location);
            }
            // Bonus money earned by holding the flag.
            int moneyByFlag = 0;
            if (player.getLastKillWithFlag()) {
                moneyByFlag = 3;
            }

            int total = moneyKiller + moneyKilled + moneyByLocation + moneyByFlag;

            String msg = "You were a " + Tools.shipName(shipKiller) + " (+$" + moneyKiller + ")";
            msg += ", killed a " + Tools.shipName(shipKilled) + " (+$" + moneyKilled + "). ";
            msg += "Location: " + context.getPubUtil().getLocationName(location) + " (+$" + moneyByLocation + ").";

            // Overide if kill in space
            //if (location.equals(Location.SPACE)) {
            //	total = 0;
            //	msg = "Kills outside of the base are worthless.";
            //}

            m_botAction.sendSmartPrivateMessage(sender, "You earned $" + total + " by killing " + player.getLastKillKilledName() + ".");
            m_botAction.sendSmartPrivateMessage(sender, msg);
            if (player.getLastKillWithFlag()) {
                m_botAction.sendSmartPrivateMessage(sender, "Bonus: Your team had the flag (+$3).");
            }

        } else {
            m_botAction.sendSmartPrivateMessage(sender, "You're still not in the system. Wait a bit to be added.");
        }
    }

    /**
     * Retrieves the amount of money in the bot's pot.
     * @return Total amount of cash the pubsystem owns.
     */
    private int getMoneyPot() {
        return playerManager.getPlayer(m_botAction.getBotName(), false).getMoney();
    }

    /*
     * Coupon related commands.
     */
    /**
     * Handles the !money command. (Anyone)
     * <p>
     * Displays the amount of money a person has. When used with a name as argument, it will try
     * to lookup that person's current amount of money. This can only be done for players who are in the pub.
     * @param sender Person who issued the command.
     * @param command The command, including parameter.
     */
    private void doCmdDisplayMoney(String sender, String command) {
        String name = sender;
        if (command.contains(" ")) {
            name = command.substring(command.indexOf(" ")).trim();
            PubPlayer pubPlayer = playerManager.getPlayer(name, false);
            if (pubPlayer != null) {
                m_botAction.sendSmartPrivateMessage(sender, pubPlayer.getPlayerName() + " has $" + pubPlayer.getMoney() + ".");
            } else {
                m_botAction.sendSmartPrivateMessage(sender, "Player '" + name + "' not found.");
            }
        } else if (playerManager.isPlayerExists(name)) {
            PubPlayer pubPlayer = playerManager.getPlayer(sender);
            m_botAction.sendSmartPrivateMessage(sender, "You have $" + pubPlayer.getMoney() + " in your bank.");
        } else {
            m_botAction.sendSmartPrivateMessage(sender, "You're still not in the system. Wait a bit to be added.");
        }
    }

    /**
     * Handles the !couponlistops command. (Smod+)
     * <p>
     * Displays the current coupon Operators.
     * @param sender Person who issued the command.
     */
    private void doCmdCouponListOps(String sender) {

        List<String> lines = new ArrayList<String>();
        lines.add("List of Operators:");
        for (String name : couponOperators) {
            lines.add("- " + name);
        }
        m_botAction.smartPrivateMessageSpam(sender, lines.toArray(new String[lines.size()]));
    }

    /**
     * Handles the !couponaddop command. (Smod+)
     * <p>
     * Temporary adds a coupon operator. Change is reverted when the bot respawns.
     * If the person is already a coupon operator, nothing is changed.
     * @param sender Person who issued the command.
     * @param name Name of the temporary coupon operator.
     */
    private void doCmdCouponAddOp(String sender, String name) {

        if (!couponOperators.contains(name.toLowerCase())) {
            couponOperators.add(name.toLowerCase());
            m_botAction.sendSmartPrivateMessage(sender, name + " is now an operator (temporary until the bot respawn).");

            /* Old code for permanently adding a coupon operator.
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

    /**
     * Handles the !coupondisable command. (Smod+ or coupon operator)
     * <p>
     * Disables a coupon with a specific code, if possible.
     * @param sender Person who issued the command.
     * @param codeString Code of the coupon that is to be disabled.
     */
    private void doCmdCouponDisable(String sender, String codeString) {

        CouponCode code = getCouponCode(codeString);
        if (code == null) {
            m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' not found.");
        } else if (!code.isEnabled()) {
            m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' is already disabled.");
        } else if (!code.isValid()) {
            m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' is not valid anymore, useless.");
        } else {
            code.setEnabled(false);
            updateCouponDB(code, "update:" + codeString + ":" + sender);
        }

    }

    /**
     * Handles the !couponenable command. (Smod+ or coupon operator)
     * <p>
     * Enables a coupon with a specific code, if possible.
     * @param sender Person who issued the command.
     * @param codeString Code of the coupon that is to be enabled.
     */
    private void doCmdCouponEnable(String sender, String codeString) {

        CouponCode code = getCouponCode(codeString);
        if (code == null) {
            m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' not found.");
        } else if (code.isEnabled()) {
            m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' is already enabled.");
        } else if (!code.isValid()) {
            m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' is not valid anymore, useless.");
        } else {
            code.setEnabled(true);
            updateCouponDB(code, "update:" + codeString + ":" + sender);
        }
    }

    /**
     * Handles the !couponinfo command. (Smod+ or coupon operator)
     * <p>
     * Displays all the known details on a specific coupon, if it exists.
     * @param sender Person who issued the command.
     * @param codeString Code of the coupon that is looked up.
     */
    private void doCmdCouponInfo(String sender, String codeString) {

        CouponCode code = getCouponCode(codeString);
        if (code == null) {
            m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' not found.");
        } else {
            String generation[] = new String[] {
                    "Code: " + codeString.toUpperCase() + "  (Generated by " + code.getCreatedBy() + ", " + new SimpleDateFormat("yyyy-MM-dd").format(code.getCreatedAt()) + ")",
                    " - Valid: " + (code.isValid() ? "Yes" : "No (Reason: " + code.getInvalidReason() + ")"), " - Money: $" + code.getMoney(),
                    " - " + (code.getUsed() > 0 ? "Used: " + code.getUsed() + " time(s)" : "Not used yet"), "[Limitation]", " - Maximum of use: " + code.getMaxUsed(),
                    " - Start date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(code.getStartAt()), " - Expiration date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(code.getEndAt()), };

            m_botAction.smartPrivateMessageSpam(sender, generation);
        }
    }

    /**
     * Handles the !couponusers command. (Smod+ or coupon operator)
     * <p>
     * Checks whether a certain coupon code has already been used, and if so, displays the details about it.
     * @param sender Person who issued the command.
     * @param codeString Code of the coupon that is being looked up.
     */
    private void doCmdCouponUsers(String sender, String codeString) {

        CouponCode code = getCouponCode(codeString);
        if (code == null) {
            m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' not found.");
        } else {

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
                m_botAction.SQLClose(rs);

                if (count == 0) {
                    m_botAction.sendSmartPrivateMessage(sender, "This code has not been used yet.");
                }

            } catch (SQLException e) {
                Tools.printStackTrace(e);
                m_botAction.sendSmartPrivateMessage(sender, "An error has occured.");
            }

        }
    }

    /**
     * Handles the !couponexpiredate command. (Smod+ or coupon operator)
     * <p>
     * Alters the expiration date for a specific coupon. The date should be formatted as yyyy/MM/dd.
     * @param sender Person who issued the command.
     * @param command The arguments used; couponcode:expirationdate
     */
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
        } else {

            DateFormat df = new SimpleDateFormat("yyyy/MM/dd");
            java.util.Date date;
            try {
                date = df.parse(dateString);
            } catch (ParseException e) {
                m_botAction.sendSmartPrivateMessage(sender, "Bad date");
                return;
            }

            // Update the coupon and log the update.
            code.setEndAt(date);
            updateCouponDB(code, "update:" + codeString + ":" + sender);

        }
    }

    /**
     * Handles the !couponlimituse command. (Smod+ or coupon operator)
     * <p>
     * Sets the amount of uses available for this coupon. The person cannot disable the coupon
     * by setting this value to 0. Instead, {@link #doCmdCouponDisable(String, String) doCmdCouponDisable}
     * should be used.
     * @param sender Person who issued the command.
     * @param command Arguments given; couponcode:maxuses
     */
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
        } else {
            if (limit > 0) {
                code.setMaxUsed(limit);
                updateCouponDB(code, "update:" + codeString + ":" + sender);

            } else {
                m_botAction.sendSmartPrivateMessage(sender, "Must be a number higher than 0.");
            }

        }
    }

    /**
     * Handles the !couponcreate command. (Smod+ or coupon operator)
     * <p>
     * Creates a new coupon for a specific amount of money with a provided reason. 
     * This function will also generate a coupon code for the new coupon if none is provided.
     * @param sender Person who issued the command.
     * @param command Arguments provided; money:reason[:couponcode]
     */
    private void doCmdCouponCreate(String sender, String command) {

        String[] pieces = command.split("\\s*:\\s*");
        if (pieces.length < 2) {
            m_botAction.sendSmartPrivateMessage(sender, "You must include a reason for creating this coupon. !cc <money>:<reason>");
            return;
            // Automatic code
        } else if (pieces.length == 2) {

            int money;
            try {
                money = Integer.parseInt(pieces[0]);
            } catch (NumberFormatException e) {
                m_botAction.sendSmartPrivateMessage(sender, "Bad number.");
                return;
            }

            if (pieces[1].length() < 5) {
                m_botAction.sendSmartPrivateMessage(sender, "Insufficient comment for <reason>.");
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
                        codeString = codeTemp.substring(0, 8).toUpperCase();

                } catch (NoSuchAlgorithmException e) {
                    return;
                }
            }

            CouponCode code = new CouponCode(codeString, money, sender, pieces[1]);
            insertCouponDB(code, "create:" + codeString + ":" + sender);

            // Custom code
        } else if (pieces.length == 3) {

            String codeString = pieces[0];

            int money;
            try {
                money = Integer.parseInt(pieces[1]);
            } catch (NumberFormatException e) {
                m_botAction.sendSmartPrivateMessage(sender, "Bad number.");
                return;
            }

            if (pieces[2].length() < 5) {
                m_botAction.sendSmartPrivateMessage(sender, "Insufficient comment for <reason>.");
                return;
            }

            if (getCouponCode(codeString) != null) {
                m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' already exists.");
                return;
            }

            CouponCode code = new CouponCode(codeString, money, sender, pieces[2]);
            insertCouponDB(code, "create:" + codeString + ":" + sender);

        } else {
            m_botAction.sendSmartPrivateMessage(sender, "Bad argument.");
            return;
        }
    }

    /**
     * Handles the !coupon command. (Anyone)
     * <p>
     * Redeems the coupon with the provided coupon code. This is done silently to prevent brute force methods.
     * The only feedback given is when a person has already used up the coupon.
     * <p>
     * To trigger this specific command, there must be a coupon code provided as argument with the command.
     * @param sender Person who issued the command.
     * @param codeString Coupon code.
     */
    private void doCmdCoupon(String sender, String codeString) {

        CouponCode code = getCouponCode(codeString, true);
        if (code == null) {
            // no feedback to avoid bruteforce!!
            // m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' not found.");
        } else if (isPlayerRedeemAlready(sender, code)) {
            m_botAction.sendSmartPrivateMessage(sender, "You have already used this code.");
            return;
        } else if (!code.isValid()) {
            // no feedback to avoid bruteforce!!
            return;
        } else {

            code.gotUsed();
            updateCouponDB(code, "updateredeem:" + codeString + ":" + sender);

        }
    }

    /**
     * Checks whether a coupon has already been redeemed.
     * @param playerName Player for who to check.
     * @param code Coupon code for which to check.
     * @return True if the coupon code has been used up completely. False otherwise.
     */
    private boolean isPlayerRedeemAlready(String playerName, CouponCode code) {

        ResultSet rs;
        try {
            rs = m_botAction.SQLQuery(database, "SELECT * FROM tblMoneyCodeUsed WHERE fnMoneyCodeId = '" + code.getId() + "' AND fcName = '" + Tools.addSlashes(playerName) + "'");
            if (rs.first()) {
                m_botAction.SQLClose(rs);
                return true;
            } else {
                m_botAction.SQLClose(rs);
                return false;
            }

        } catch (SQLException e) {
            Tools.printStackTrace(e);
            return false;
        }

    }

    /**
     * Retrieves a coupon for the provided code, without forcing an update to the local storage.
     * @param codeString Code of the coupon to be retrieved
     * @return The retrieved coupon when found, otherwise null.
     * @see #getCouponCode(String, boolean)
     */
    private CouponCode getCouponCode(String codeString) {
        return getCouponCode(codeString, false);
    }

    /**
     * Retrieves a coupon for the provided code.
     * @param codeString Code of the coupon to be retrieved.
     * @param forceUpdate When true, force an update of the local database from the general database. 
     * @return The retrieved coupon when found, otherwise null.
     */
    private CouponCode getCouponCode(String codeString, boolean forceUpdate) {

        if (!forceUpdate && coupons.containsKey(codeString))
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

                CouponCode code = new CouponCode(codeString, money, createdAt, createdBy, description);
                code.setId(id);
                code.setEnabled(enabled);
                code.setMaxUsed(maxUsed);
                code.setStartAt(startAt);
                code.setEndAt(endAt);
                code.setUsed(used);
                m_botAction.SQLClose(rs);

                coupons.put(codeString, code);
                return code;
            } else {
                m_botAction.SQLClose(rs);
                return null;
            }

        } catch (SQLException e) {
            Tools.printStackTrace(e);
            return null;
        }

    }

    /**
     * Inserts a coupon into the general database.
     * @param code The coupon that needs to be inserted.
     * @param params Any additional information. Mainly who created the coupon.
     */
    private void insertCouponDB(CouponCode code, String params) {

        String startAtString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(code.getStartAt());
        String endAtString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(code.getEndAt());

        m_botAction.SQLBackgroundQuery(database, "coupon:" + params, "INSERT INTO tblMoneyCode "
                + "(fcCode, fcDescription, fnMoney, fcCreatedBy, fnUsed, fnMaxUsed, fdStartAt, fdEndAt, fbEnabled, fdCreated) " + "VALUES (" + "'"
                + code.getCode()
                + "',"
                + "'"
                + Tools.addSlashes(code.getDescription())
                + "',"
                + "'"
                + code.getMoney()
                + "',"
                + "'"
                + Tools.addSlashes(code.getCreatedBy())
                + "',"
                + "'"
                + code.getUsed() + "'," + "'" + code.getMaxUsed() + "'," + "'" + startAtString + "'," + "'" + endAtString + "'," + "" + (code.isEnabled() ? 1 : 0) + "," + "NOW()" + ")");

    }

    /**
     * Updates a coupon in the general database.
     * @param code The coupon that needs to be updated.
     * @param params Any additional parameters, for example the person who triggered the update.
     */
    private void updateCouponDB(CouponCode code, String params) {

        String startAtString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(code.getStartAt());
        String endAtString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(code.getEndAt());

        m_botAction.SQLBackgroundQuery(database, "coupon:" + params, "UPDATE tblMoneyCode SET " + "fcDescription = '" + Tools.addSlashes(code.getDescription()) + "', " + "fnUsed = " + code.getUsed()
                + ", " + "fnMaxUsed = " + code.getMaxUsed() + ", " + "fdStartAt = '" + startAtString + "', " + "fdEndAt = '" + endAtString + "', " + "fbEnabled = " + (code.isEnabled() ? 1 : 0) + " "
                + "WHERE fnMoneyCodeId='" + code.getId() + "'");

    }

    /*
     * Item and shop ban commands.
     */
    /**
     * Handles the !additemban command. (Smod+)
     * <p>
     * This will add an item ban to a player. The person who issues the command, must provide the following parameters:
     * <ul>
     *  <li>Name of the person who receives the ban;
     *  <li>Name of the item that is being restricted;
     *  <li>Duration of the ban, in hours;
     *  <li>Reason of the ban.
     * </ul>
     * The issued ban persists through restarts of the pubsystem, however, the player doesn't need to be online to make the time tick.
     * @param sender Person who issued the command.
     * @param args The arguments of the command.
     */
    private void doCmdAddItemBan(String sender, String args) {
        Integer duration = null;
        ArrayList<ItemBan> itemBanList = null;
        
        // Silent return.
        if(args.isEmpty())
            return;
        
        String splitArgs[] = args.split(":",4);
        
        // Not enough information given by the issuer.
        if(splitArgs.length != 4) {
            m_botAction.sendSmartPrivateMessage(sender, "Please provide all the needed parameters. (!aib <name>:<item>:<duration>:<reason>)");
            return;
        }
        
        // Convert the duration.
        try {
            duration = Integer.parseInt(splitArgs[2]);
        } catch (NumberFormatException e) {
            m_botAction.sendSmartPrivateMessage(sender, "Please provide a valid duration, in hours.");
            return;
        }
        
        // Create the new ban.
        ItemBan itemban = new ItemBan(splitArgs[0], sender, splitArgs[1], duration, splitArgs[3]);
        
        // Add the new ban to the current list of bans for this user.
        //TODO Check whether the new itemban doesn't already match an existing ban for the same item.
        if(!itemBans.isEmpty() && itemBans.containsKey(splitArgs[0].toLowerCase())) {
            itemBanList = itemBans.get(splitArgs[0].toLowerCase());
            itemBanList.add(itemban);
        } else {
            itemBanList = new ArrayList<ItemBan>();
            itemBanList.add(itemban);
        }
        
        // Add the ban to the global list and save the changes to file to make it persistant.
        itemBans.put(splitArgs[0].toLowerCase(), itemBanList);
        saveBans();
        
        // Send the information to the player and the person who issued the ban.
        m_botAction.sendSmartPrivateMessage(splitArgs[0], "You have been banned from buying " + splitArgs[1] + " from the shop for " + duration + " hours.");
        m_botAction.sendSmartPrivateMessage(splitArgs[0], "This ban was issued by " + sender + " for the following reason: " + splitArgs[3]);
        m_botAction.sendSmartPrivateMessage(sender, splitArgs[0] + " has been banned from buying " + splitArgs[1] + " for " + duration + " hours.");
        
    }
    
    /**
     * Handles the !listitembans command. (Smod+)
     * <p>
     * This will display all of the currently active item bans.
     * At the same time, this function will also check if none of the item bans have been lifted. If they have been lifted,
     * then it will remove them from the list and save the changes.
     * @param sender Person who issued the command.
     */
    private void doCmdListItemBans(String sender) {
        boolean needsUpdate = false;
        HashMap<String, ArrayList<ItemBan>> newItemBans = new HashMap<String, ArrayList<ItemBan>>(); 
        
        // No active bans.
        if(itemBans.isEmpty()) {
            m_botAction.sendSmartPrivateMessage(sender, "No one is currently banned from specific items in the shop.");
            return;
        }
        
        // Iterate through all the item bans.
        for(String name : itemBans.keySet()) {
            ArrayList<ItemBan> newItemBanList = new ArrayList<ItemBan>();
            for(ItemBan itemban : itemBans.get(name.toLowerCase())) {
                if(itemban.isItemBanned()) {
                    // Display the ban if it's still active, and copy it over to the new list, to prevent concurrent modification.
                    newItemBanList.add(itemban);
                    m_botAction.sendSmartPrivateMessage(sender, itemban.getStatusMessage());
                } else {
                    // Flag the need for an update and do not copy the entry over when it has expired.
                    needsUpdate = true;
                }
            }
            // Copy over the new ban list for a specific player into the new global list.
            if(!newItemBanList.isEmpty())
                newItemBans.put(name.toLowerCase(), newItemBanList);
        }

        // If an update was needed, update the real global list and save the changes to file.
        if(needsUpdate) {
            itemBans.clear();
            itemBans.putAll(newItemBans);

            saveBans();
        }
    }
    
    /**
     * Handles the !removeitemban command. (Smod+)
     * <p>
     * Removes a specific item ban from a player or all of their item bans, depending on the parameters provided.
     * The removal only happens when the issuer has a sufficiently high access level. This is compared for each individual
     * item ban.
     * @param sender Person who issued the command.
     * @param args Name of the player whose shop ban will be lifted.
     */
    private void doCmdRemoveItemBan(String sender, String args) {
        // Silent return.
        if(args.isEmpty())
            return;
        
        ArrayList<ItemBan> updatedList = new ArrayList<ItemBan>();
        String splitArgs[] = args.split(":",2);
        int level = m_botAction.getOperatorList().getAccessLevel(sender);
        int counter = 0;
        int initialSize = 0;
        boolean updateNeeded = false;
        
        // There is no active ban for this player.
        if(itemBans.isEmpty() || !itemBans.containsKey(splitArgs[0].toLowerCase())) {
            m_botAction.sendSmartPrivateMessage(sender, "Could not find " + splitArgs[0] + " in the current list of itembans.");
            return;
        }
        
        updatedList = itemBans.get(splitArgs[0].toLowerCase());
        initialSize = updatedList.size();
        
        // Iterate through the current item ban list of the targeted person.
        for(ItemBan itemban : updatedList) {
            // Remove the entry when the following is valid:
            // Access level high enough AND one out of the following two situations is true:
            // - All item bans are to be removed for this person
            // - A specific item ban needs to be removed and this item ban matches the name.
            if((splitArgs.length == 1 
                    || (splitArgs.length == 2 && itemban.item.equalsIgnoreCase(splitArgs[1]))) 
                    && level >= m_botAction.getOperatorList().getAccessLevel(itemban.issuer)) {
                counter++;
                updateNeeded = true;
                m_botAction.sendSmartPrivateMessage(sender, "Removing: " + itemban.getStatusMessage());
                updatedList.remove(itemban);
            }
        }

        // If an update has been made, then update the global list and save the changes to file.
        if(updateNeeded) {
            if(updatedList.isEmpty())
                itemBans.remove(splitArgs[0].toLowerCase());
            else
                itemBans.put(splitArgs[0].toLowerCase(), updatedList);
            
            saveBans();
        }
        
        // Inform the issuer on the made changes.
        if(counter == 0) {
            m_botAction.sendSmartPrivateMessage(sender, "No itemban found or access level was not sufficient enough to remove the itemban.");
        } else if(counter == initialSize) {
            m_botAction.sendSmartPrivateMessage(sender, "All itembans for " + splitArgs[0] + " have been lifted.");
        } else {
            m_botAction.sendSmartPrivateMessage(sender, "Removed " + counter + " out of " + initialSize + " itembans for " + splitArgs[0] + ".");
        }
    }
    
    /**
     * Handles the !addshopban command. (Smod+)
     * <p>
     * This will add a shop ban to a player. The person who issues the command, must provide the following parameters:
     * <ul>
     *  <li>Name of the person who receives the ban;
     *  <li>Duration of the ban, in hours;
     *  <li>Reason of the ban.
     * </ul>
     * The issued ban persists through restarts of the pubsystem, however, the player doesn't need to be online to make the time tick.
     * @param sender Person who issued the command.
     * @param args The arguments of the command.
     */
    private void doCmdAddShopBan(String sender, String args) {
        Integer duration = null;
        
        // Silent return.
        if(args.isEmpty())
            return;
        
        String splitArgs[] = args.split(":",3);
        
        // Not enough information given by the issuer.
        if(splitArgs.length != 3) {
            m_botAction.sendSmartPrivateMessage(sender, "Please provide all the needed parameters. (!asb <name>:<duration>:<reason>)");
            return;
        }
        
        // Convert the duration.
        try {
            duration = Integer.parseInt(splitArgs[1]);
        } catch (NumberFormatException e) {
            m_botAction.sendSmartPrivateMessage(sender, "Please provide a valid duration, in hours.");
            return;
        }
        
        // Check whether this person already has an active shop ban.
        if(!shopBans.isEmpty() && shopBans.containsKey(splitArgs[0].toLowerCase())) {
            if(shopBans.get(splitArgs[0].toLowerCase()).isShopBanned()) {
                m_botAction.sendSmartPrivateMessage(sender, splitArgs[0] + " already has a shop ban. Please remove the current one first before applying a new one.");
                return;
            }
        }
        
        // Create a new shop ban.
        ShopBan shopban = new ShopBan(splitArgs[0], sender, duration, splitArgs[2]);
        
        // Update the global list and save the changes to file to make them persistent.
        shopBans.put(splitArgs[0].toLowerCase(), shopban);
        saveBans();
        
        // Send the information to the player and the person who issued the ban.
        m_botAction.sendSmartPrivateMessage(splitArgs[0], "You have been banned from buying any item from the shop for " + duration + " hours.");
        m_botAction.sendSmartPrivateMessage(splitArgs[0], "This ban was issued by " + sender + " for the following reason: " + splitArgs[2]);
        m_botAction.sendSmartPrivateMessage(sender, splitArgs[0] + " has been banned from using the shop for " + duration + " hours.");        
    }
    
    /**
     * Handles the !listshopbans command. (Smod+)
     * <p>
     * This will display all of the currently active shop bans.
     * Simultaniously it will check if any of the bans have expired, and if so, remove them from the list.
     * @param sender Person who issued the command.
     */
    private void doCmdListShopBans(String sender) {
        boolean needsUpdate = false;
        
        // No active bans.
        if(shopBans.isEmpty()) {
            m_botAction.sendSmartPrivateMessage(sender, "No one is currently banned from using the shop.");
            return;
        }
        
        // Iterate through the ban list.
        for(String name : shopBans.keySet()) {
            if(!shopBans.get(name.toLowerCase()).isShopBanned()) {
                // If a ban has expired, remove it from the list and flag it for an update.
                shopBans.remove(name.toLowerCase());
                needsUpdate = true;
            } else {
                // Display the ban.
                m_botAction.sendSmartPrivateMessage(sender, shopBans.get(name.toLowerCase()).getStatusMessage());
            }
        }
        
        // If changes have been made, save them.
        if(needsUpdate)
            saveBans();
    }
    
    /**
     * Handles the !removeshopban command. (Smod+)
     * <p>
     * Removes a player's shop ban from the list, but only if the access level of the issuer is sufficient enough.
     * @param sender Person who issued the command.
     * @param args Name of the player whose shop ban will be lifted.
     */
    private void doCmdRemoveShopBan(String sender, String args) {
        // Silent return.
        if(args.isEmpty())
            return;
        
        // There is no active ban for this player.
        if(shopBans.isEmpty() || !shopBans.containsKey(args.toLowerCase())) {
            m_botAction.sendSmartPrivateMessage(sender, "Could not find " + args + " in the current list of shopbans.");
            return;
        }
        
        // To remove a ban, the access level of the issuer needs to be equal to or higher than that of the person who created the ban.
        if(m_botAction.getOperatorList().getAccessLevel(sender) < m_botAction.getOperatorList().getAccessLevel(shopBans.get(args.toLowerCase()).issuer)) {
            m_botAction.sendSmartPrivateMessage(sender, "Sorry, this ban is above your paygrade.");
            return;
        }
        
        // Remove the ban and save the changes.
        shopBans.remove(args.toLowerCase());
        saveBans();
        
        // Inform the issuer of the removal.
        m_botAction.sendSmartPrivateMessage(sender, "The shopban for " + args + " have been lifted.");
    }
    
    /**
     * Checks whether the pub store is open.
     * @return True if the pubstore is open, otherwise fals.
     * @see PubStore
     */
    public boolean isStoreOpened() {
        return store.isOpened();
    }
    
    /**
     * Resets the counter used to track the usages of round restricted items.
     * @see PubStore
     */
    public void resetRoundRestrictions() {
        store.resetRoundRestrictedItems();
    }

    /**
     * Checks if there is an entry for database in the configuration.
     * @return True if there is a database entry, otherwise false.
     */
    public boolean isDatabaseOn() {
        return m_botAction.getBotSettings().getString("database") != null;
    }

    /**
     * Unknown. Seems to be a dummy function that never got implemented.
     * @param killer Probably the person who committed the TK.
     */
    public void handleTK(Player killer) {

    }

    /**
     * Dummy function.
     */
    public void handleDisconnect() {

    }

    /**
     * Handles the SQLResultEvent
     * <p>
     * Mainly used to give feedback on actions related to coupon creation, updating and redeeming.
     */
    public void handleEvent(SQLResultEvent event) {
        if (!enabled ) return;

        // Coupon system
        if (event.getIdentifier().startsWith("coupon")) {

            String[] pieces = event.getIdentifier().split(":");
            if (pieces.length > 1) {

                if (pieces.length == 4 && pieces[1].equals("update")) {
                    // General update
                    m_botAction.sendSmartPrivateMessage(pieces[3], "Code '" + pieces[2] + "' updated.");
                }

                else if (pieces.length == 4 && pieces[1].equals("updateredeem")) {
                    // Coupon redeem update
                    CouponCode code = getCouponCode(pieces[2]);
                    if (code == null)
                        return;

                    m_botAction.SQLBackgroundQuery(database, null, "INSERT INTO tblMoneyCodeUsed " + "(fnMoneyCodeId, fcName, fdCreated) " + "VALUES ('" + code.getId() + "', '"
                            + Tools.addSlashes(pieces[3]) + "', NOW())");

                    if (context.getPlayerManager().addMoney(pieces[3], code.getMoney(), true)) {
                        m_botAction.sendSmartPrivateMessage(pieces[3], "$" + code.getMoney() + " has been added to your account.");
                    } else {
                        m_botAction.sendSmartPrivateMessage(pieces[3], "A problem has occured. Please contact someone from the staff by using ?help. (err:03)");
                    }

                }

                else if (pieces.length == 4 && pieces[1].equals("create")) {
                    // Coupon creation
                    m_botAction.sendSmartPrivateMessage(pieces[3], "Code '" + pieces[2] + "' created.");
                }
            }

            m_botAction.SQLClose(event.getResultSet());

        }

    }

    /**
     * Handles any messages that come through the subscribed IPC channels.
     */
    public void handleEvent(InterProcessEvent event) {
        if (!enabled ) return;
        List<IPCReceiver> ipcReceiversCopy;
        
        // Since executing the IPC messages might take a bit, synchronizing the receiver.handleInterProcessEvent could lock things up.
        // So instead, just temporary sync the list, to copy over the references of the values it currently has.
        // Then release the sync and do the real iteration over the copied references.
        if(ipcReceivers != null) {
            synchronized(ipcReceivers) {
                ipcReceiversCopy = new ArrayList<IPCReceiver>(ipcReceivers);
            }
            for (IPCReceiver receiver : ipcReceiversCopy) {
                receiver.handleInterProcessEvent(event);
            }
        }
    }

    /**
     * Handles the frequency change of a player.
     */
    public void handleEvent(FrequencyChange event) {
        if (!enabled ) return;
        Player p = m_botAction.getPlayer(event.getPlayerID());
        if (p == null)
            return;

        frequencyTimes.put(p.getPlayerName(), System.currentTimeMillis());
    }

    /**
     * Handles the PlayerLeft event
     */
    public void handleEvent(PlayerLeft event) {
        if (!enabled ) return;
        Player p = m_botAction.getPlayer(event.getPlayerID());
        if (p == null)
            return;

        frequencyTimes.remove(p.getPlayerName());
    }

    /**
     * Handles the ArenaList event. Currently empty.
     */
    public void handleEvent(ArenaList event) {

        /*
        String thisArena = m_botAction.getArenaName();
        if (thisArena.contains("Public"))
        {
        	thisArena = thisArena.substring(8,9);

        	int i=0;
        	for(String arena: event.getArenaNames()) {
        		System.out.println(i + ": " + arena);
        		if(arena.equals(thisArena)) {
        			this.arenaNumber = String.valueOf(i);
        		}
        		i++;
        	}
        }
        */

    }

    /**
     * Handles the PlayerDeath event.
     * <p>
     * Main purposes is to give out money rewards to the killers. This reward is dependent on things like location and if
     * the killer's freq is holding the flag.
     * Currently disabled, but it can also give out money penalties on team kills.
     * <p>
     * This function is set to ignore duelling players.
     */
    public void handleEvent(PlayerDeath event) {
        if (!enabled ) return;
        
        final Player killer = m_botAction.getPlayer(event.getKillerID());
        final Player killed = m_botAction.getPlayer(event.getKilleeID());

        if (killer == null || killed == null)
            return;

        // A TK, do nothing for now
        if (killer.getFrequency() == killed.getFrequency()) {
            handleTK(killer);
            PubPlayer pp = playerManager.getPlayer(killed.getPlayerName());
            if (pp != null)
                pp.handleDeath(event);
            return;
        }

        // Disable if the player is dueling
        if (context.getPubChallenge().isDueling(killer.getPlayerName()) || context.getPubChallenge().isDueling(killed.getPlayerName())) {
            return;
        }

        try {

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
                            m_botAction.sendSmartPrivateMessage(killed.getPlayerName(), "You lost your item after " + duration.getDeaths() + " death(s).", 22);
                        }
                    };
                    m_botAction.scheduleTask(timer, 4300);
                } else {
                    // TODO - Give the PubShipItemSettings
                    // i.e: A player buys a special ship with 10 repel (PubShipItemSettings)
                    //      for 5 deaths (PubItemDuration)
                }
            }

            int x = killer.getXTileLocation();
            int y = killer.getYTileLocation();
            Location location = context.getPubUtil().getLocation(x, y);

            if (location != null) {
                int money = 0;
                boolean withFlag = false;

                // Money if team with flag
                if (context.getGameFlagTime().isRunning()) {
                    int freqWithFlag = context.getGameFlagTime().getFreqWithFlag();
                    if (freqWithFlag == killer.getFrequency()) {
                        money += 3;
                        withFlag = true;
                    }
                }

                // Money from the ship
                int moneyKiller = 0;
                int moneyKilled = 0;
                try {
                    moneyKiller = shipKillerPoints.get((int) killer.getShipType());
                    moneyKilled = shipKillerPoints.get((int) killed.getShipType());
                } catch (NullPointerException e) {}

                money += moneyKiller;
                money += moneyKilled;

                // Money from the location
                int moneyByLocation = 0;
                if (locationPoints.containsKey(location)) {
                    moneyByLocation = locationPoints.get(location);
                    money += moneyByLocation;
                }

                // Add money
                String playerName = killer.getPlayerName();
                //context.getPlayerManager().addMoney(playerName, money);
                PubPlayer p = context.getPlayerManager().getPlayer(playerName);
                if (p != null) {
                    p.addMoney(money);
                    // [SPACEBUX] $1000 milestone!  Balance ... $5002  Last kill ... +$10  (!lastkill for details)
                    if (p.getMoney() % 1000 < money) {
                        m_botAction.sendPrivateMessage(playerName, "[PUBBUX] $1000 milestone.  Balance ... $" + p.getMoney() + "  Last kill ... +$" + money + "  (!lastkill for details)");
                    }
                }

                pubPlayerKiller.setLastKillShips((int) killer.getShipType(), (int) killed.getShipType());
                pubPlayerKiller.setLastKillLocation(location);
                pubPlayerKiller.setLastKillKilledName(killed.getPlayerName());
                pubPlayerKiller.setLastKillWithFlag(withFlag);
            }

        } catch (Exception e) {
            Tools.printStackTrace(e);
        }

    }

    /**
     * Handles the low level commands as well as various coupon related commands.
     * 
     * @param sender Player who issued the command.
     * @param command The full command, including parameters.
     */
    public void handleCommand(String sender, String command) {

        if (command.startsWith("!items") || command.trim().equals("!i")) {
            doCmdItems(sender, false);
        } else if (command.trim().equals("!buy") || command.trim().equals("!b")) {
            doCmdItems(sender, true);
        } else if (command.trim().equals("!fullbuylist") || command.trim().equals("!fbl") 
                || command.trim().equals("!fullitemlist") || command.trim().equals("!fil") ) {
            doCmdItems(sender, true);
        } else if (command.startsWith("!$") || command.startsWith("!money")) {
            doCmdDisplayMoney(sender, command);
        } else if (command.startsWith("!iteminfo") || command.startsWith("!buyinfo")) {
            doCmdItemInfo(sender, command);
        } else if (command.startsWith("!buy") || command.equals("!b")) {
            doCmdBuy(sender, command);
        } else if (command.startsWith("!donate")) {
            doCmdDonate(sender, command);
        } else if (command.startsWith("!coupon ")) {
            doCmdCoupon(sender, command.substring(8).trim());
        } else if (command.startsWith("!lastkill") || command.equals("!lk")) {
            doCmdLastKill(sender);
        } else if (command.startsWith("!richestall")) {
            doCmdRichestAll(sender, command);
        } else if (command.startsWith("!richest")) {
            doCmdRichest(sender, command);
        } else if (command.startsWith("!fruit ")) {
            doCmdFruit(sender, command.substring(6).trim());
        } else if (command.startsWith("!fruitinfo")) {
            doCmdFruitInfo(sender);
        }
        
        if (m_botAction.getOperatorList().isSmod(sender) || couponOperators.contains(sender.toLowerCase())) {


            // Coupon System commands
            boolean operator = couponOperators.contains(sender.toLowerCase());
            boolean smod = m_botAction.getOperatorList().isSmod(sender);

            // (Operator/SMOD only)
            if (command.startsWith("!coupon") || command.startsWith("!c") ) {

                if (command.startsWith("!couponcreate ") || command.startsWith("!cc ")) {
                    doCmdCouponCreate(sender, command.substring(command.indexOf(" ") + 1).trim());
                } else if (command.startsWith("!couponlimituse ") || command.startsWith("!clu ")) {
                    doCmdCouponLimitUse(sender, command.substring(command.indexOf(" ") + 1).trim());
                } else if (command.startsWith("!couponexpiredate ") || command.startsWith("!ced ")) {
                    doCmdCouponExpireDate(sender, command.substring(command.indexOf(" ") + 1).trim());
                } else if (command.startsWith("!couponinfo ") || command.startsWith("!ci ")) {
                    doCmdCouponInfo(sender, command.substring(command.indexOf(" ") + 1).trim());
                } else if (command.startsWith("!couponusers ") || command.startsWith("!cu ")) {
                    doCmdCouponUsers(sender, command.substring(command.indexOf(" ") + 1).trim());
                } else if (command.startsWith("!couponenable ") || command.startsWith("!ce ")) {
                    doCmdCouponEnable(sender, command.substring(command.indexOf(" ") + 1).trim());
                } else if (command.startsWith("!coupondisable ") || command.startsWith("!cd ")) {
                    doCmdCouponDisable(sender, command.substring(command.indexOf(" ") + 1).trim());
                }
            }
            
            if (command.startsWith("!award ")) {
                doCmdAward(sender, command);
            } else if (command.equals("!pot")) {
                m_botAction.sendSmartPrivateMessage(sender, "$" + getMoneyPot());
            } else if (command.startsWith("!fruitstats")) {
                m_botAction.sendSmartPrivateMessage( sender, "Players have ... Won: $" + fruitStats[0] + "  Lost: $" + fruitStats[1] + "  House Earnings: " + (fruitStats[1] - fruitStats[0]) );
            }
        }
    }

    /**
     * Handles the Mod+ commands.
     * 
     * @param sender Mod+ who issued the command.
     * @param command The full command, including parameters.
     */
    public void handleModCommand(String sender, String command) {

        if (command.startsWith("!debugobj")) {
            doCmdDebugObj(sender, command);
        } else if (command.equals("!toggledonation")) {
            doCmdToggleDonation(sender);
        } else if (m_botAction.getOperatorList().isSysop(sender) && command.startsWith("!addmoney")) {
            doCmdAddMoney(sender, command);
        }
    }

    /**
     * Handles the Smod+ commands.
     * 
     * @param sender Smod+ who issued the command.
     * @param command The full command, including parameters.
     */
    public void handleSmodCommand(String sender, String command) {
        if (command.startsWith("!couponaddop "))
            doCmdCouponAddOp(sender, command.substring(12).trim());
        else if (command.equals("!couponlistops"))
            doCmdCouponListOps(sender);
        else if (command.startsWith("!additemban ") || command.startsWith("!aib "))
            doCmdAddItemBan(sender, command.substring(command.indexOf(" ") + 1).trim());
        else if (command.equals("!listitembans") || command.equals("!lib"))
            doCmdListItemBans(sender);
        else if (command.startsWith("!removeitemban ") || command.startsWith("!rib "))
            doCmdRemoveItemBan(sender, command.substring(command.indexOf(" ") + 1).trim());
        else if (command.startsWith("!addshopban ") || command.startsWith("!asb "))
            doCmdAddShopBan(sender, command.substring(command.indexOf(" ") + 1).trim());
        else if (command.equals("!listshopbans") || command.equals("!lsb"))
            doCmdListShopBans(sender);
        else if (command.startsWith("!removeshopban ") || command.startsWith("!rsb "))
            doCmdRemoveShopBan(sender, command.substring(command.indexOf(" ") + 1).trim());
        else if (m_botAction.getOperatorList().isSysop(sender))
            handleSysopCommand(sender, command);
    }
    
    /**
     * Handles the Sysop commands.
     * @param sender Sysop who issued the command.
     * @param command The command including parameters.
     */
    public void handleSysopCommand(String sender, String command) {
        if (command.equals("!storehelp"))
            doCmdStoreCfgHelp(sender);
        else if (command.equals("!storecfg"))
            doCmdViewStoreCfg(sender);
        else if (command.startsWith("!edit "))
            doCmdEditCfg(sender, command);
        else if (command.startsWith("!bankrupt"))
            doCmdBankrupt(sender, command);
    }

    /**
     * A convenient way to handle "ez" when we're trying to improve the level of sportsmanship pre-Steam.
     * 
     * @param sender The player who said "ez".
     */
    public void handleEZ(String sender) {
        PubPlayer pp = playerManager.getPlayer(sender);
        if (pp != null) {
            pp.ezPenalty(true);
            PubPlayer pplast = playerManager.getPlayer(pp.getLastKillKilledName());
            if (pplast != null) {
                pplast.ezPenalty(false);
            }
        }
    }

    /**
     * Smod command related help messages.
     * @param sender Person who issued the command
     * @return All the Smod related help messages.
     */
    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[] {
                pubsystem.getHelpLine("!aib <name>:<item>:<duration>:<reason> -- Adds an item ban on <item> for <name> for <duration> hours with <reason>. (!additemban)"),
                pubsystem.getHelpLine("!listitembans                          -- Lists all the currently active item bans. (!lib)"),
                pubsystem.getHelpLine("!removeitemban <name>[:<item>]         -- Lifts all item bans for <name>. Optional: Lifts item bans specifically for <item>. (!rib)"),
                pubsystem.getHelpLine("!addshopban <name>:<duration>:<reason> -- Adds an item ban on <item> for <name> for <duration> hours with <reason>. (!asb)"),
                pubsystem.getHelpLine("!listshopbans                          -- Lists all the currently active shop bans. (!lsb)"),
                pubsystem.getHelpLine("!removeshopban <name>                  -- Lifts the shop ban for <name>. (!rsb)"),
                pubsystem.getHelpLine("!storehelp                             -- Displays the PubStore CFG help located in the CFG file."),
                pubsystem.getHelpLine("!storecfg                              -- Displays the PubStore CFG values."),
                pubsystem.getHelpLine("!edit <key>=<value>                    -- Modifies the pubsystem store configuration file. BE CAREFUL!"),
        };
    }

    /**
     * General help messages.
     * @param sender Person who issued the command
     * @return All general help messages.
     */
    @Override
    public String[] getHelpMessage(String sender) {
        return new String[] { 
                pubsystem.getHelpLine("!buy                -- Display the list of items you can buy. (!items, !b, !i)"),
                pubsystem.getHelpLine("!fullbuylist        -- Displays all the available store items. (!fullitemlist, !fbl, !fil)"),
                pubsystem.getHelpLine("!buy <item>         -- Item to buy. (!b)"),
                pubsystem.getHelpLine("!iteminfo <item>    -- Information about this item. (restriction, duration, etc.)"),
                pubsystem.getHelpLine("!money <name>       -- Display your money or for a given player name. (!$)"), 
                pubsystem.getHelpLine("!donate <name>:<$>  -- Donate money to a player."),
                pubsystem.getHelpLine("!coupon <code>      -- Redeem your <code>."), 
                pubsystem.getHelpLine("!richest            -- Top 5 richest players currently playing."),
                pubsystem.getHelpLine("!richestall         -- Top 10 richest players in all of TW."),
                pubsystem.getHelpLine("!lastkill           -- How much you earned for your last kill (+ algorithm). (!lk)"),
                pubsystem.getHelpLine("!fruit <$>[:#]      -- Play the slot machine for <$>, optionally # times. (!fruit 10:5)"),
                pubsystem.getHelpLine("!fruitinfo          -- Payout table for the fruit machine."), };
    }

    /**
     * Mod+ command related help messages.
     * @param sender Person who issued the command
     * @return All the Mod+ related help messages.
     */
    @Override
    public String[] getModHelpMessage(String sender) {

        String normal[] = new String[] { 
                pubsystem.getHelpLine("!award <name>:<amount>                 -- Awards <name> with <amount> from the bot's bank"),
                pubsystem.getHelpLine("!pot                                   -- Displays money available for awards"),
                pubsystem.getHelpLine("!toggledonation                        -- Toggle on/off !donation."), };

        String generation[] = new String[] {
                pubsystem.getHelpLine("!couponcreate <money>:<reason>         -- (!cc) Create a random code for <money> justified with a <reason>. Use !limituse/!expiredate for more options."),
                pubsystem.getHelpLine("!couponcreate <code>:<money>:<reason>  -- (!cc) Create a custom code for <money> justified with a <reason>. Max of 32 characters."),
                pubsystem.getHelpLine("!couponlimituse <code>:<max>           -- (!clu) Set how many players <max> can get this <code>."),
                pubsystem.getHelpLine("!couponexpiredate <code>:<date>        -- (!ced) Set an expiration <date> (format: yyyy/mm/dd) for <code>."), };

        String maintenance[] = new String[] { 
                pubsystem.getHelpLine("!couponinfo <code>                     -- (!ci) Information about this <code>."),
                pubsystem.getHelpLine("!couponusers <code>                    -- (!cu) Who used this code."),
                pubsystem.getHelpLine("!couponenable / !coupondisable <code>  -- (!ce/!cd) Enable/disable <code>."), };

        String bot[] = new String[] { 
                pubsystem.getHelpLine("!couponaddop <name>                    -- Add an operator (temporary, permanant via .cfg)."),
                pubsystem.getHelpLine("!couponlistops                         -- List of operators."), };

        List<String> lines = new ArrayList<String>();
        lines.addAll(Arrays.asList(normal));
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

    /**
     * Fetches the sender of a message, no matter if it's a normal private message or a remote private message.
     * @param event The original Message event
     * @return Name of the sender
     */
    @SuppressWarnings("unused")
    private String getSender(Message event) {
        if (event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE)
            return event.getMessager();

        int senderID = event.getPlayerID();
        return m_botAction.getPlayerName(senderID);
    }

    /**
     * Updates the immunity a freq has, while it has not expired.
     */
    private void updateFreqImmunity() {
        int immuneTime = store.getFreqImmuneTime();
        Iterator<Integer> i = immuneFreqs.keySet().iterator();
        long now = System.currentTimeMillis();
        while (i.hasNext()) {
            Integer freq = i.next();
            Long t = immuneFreqs.get(freq);
            if (now - t > immuneTime * Tools.TimeInMillis.SECOND)
                i.remove();
        }
    }

    /**
     * Format a number to currency with the dollar sign 100000 -> $100,000
     */
    public static String formatMoney(int money) {
        NumberFormat nf = NumberFormat.getCurrencyInstance();
        String result = nf.format(money);
        result = result.substring(0, result.length() - 3);
        return result;
    }

    /**
     * Executes the special shop item SuddenDeath.
     * <p>
     * Not always working.. need to find out why.
     * The original intent seems to have this function as if the bot is a hired assassin.
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender Person who bought the Sudden Death.
     * @param params The victim of the sudden death.
     * @throws PubException Used to relay information on why this method has failed to execute properly.
     */
    @SuppressWarnings("unused")
    private void itemCommandSuddenDeath(final String sender, String params) throws PubException {

        if (params.equals("")) {
            throw new PubException("You must add 1 parameter when you buy this item.");
        }

        final String playerName = params;

        m_botAction.spectatePlayerImmediately(playerName);

        Player p = m_botAction.getPlayer(playerName);
        Player psender = m_botAction.getPlayer(sender);
        int distance = 10 * 16; // distance from the player and the bot

        int x = p.getXLocation();
        int y = p.getYLocation();
        int angle = (int) p.getRotation() * 9;

        int bot_x = x + (int) (-distance * Math.sin(Math.toRadians(angle)));
        int bot_y = y + (int) (distance * Math.cos(Math.toRadians(angle)));

        m_botAction.getShip().setShip(0);
        m_botAction.getShip().setFreq(psender.getFrequency());
        m_botAction.getShip().rotateDegrees(angle - 90);
        m_botAction.getShip().move(bot_x, bot_y);
        m_botAction.getShip().sendPositionPacket();
        m_botAction.getShip().fire(WeaponFired.WEAPON_THOR);
        m_botAction.getShip().fire(WeaponFired.WEAPON_THOR);

        TimerTask timer = new TimerTask() {
            public void run() {
                m_botAction.specWithoutLock(m_botAction.getBotName());
                m_botAction.sendUnfilteredPrivateMessage(playerName, "I've been ordered by " + sender + " to kill you.", 7);
            }
        };
        m_botAction.scheduleTask(timer, 500);

    }

    /**
     * Executes the special shop item MegaWarp.
     * <p>
     * This item seems to be warping every player that is not dueling nor is on the buyer's freq to a single location.
     * Every warped player also has its energy depleted, making them extremely vulnerable.
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender Person who bought the MegaWarp
     * @param params Unused.
     */
    @SuppressWarnings("unused")
    private void itemCommandMegaWarp(String sender, String params) {

        Player p = m_botAction.getPlayer(sender);

        String message = "";
        int privFreqs = 0;

        List<Integer> freqList = new ArrayList<Integer>();
        for (int i = 0; i < 10000; i++) {
            int size = m_botAction.getPlayingFrequencySize(i);
            if (size > 0 && i != p.getFrequency()) {
                freqList.add(i);
                if (i < 100) {
                    message += ", " + i;
                } else {
                    privFreqs++;
                }
            }
        }

        if (privFreqs > 0) {
            message += " and " + privFreqs + " private freq(s)";
        }
        message = message.substring(1);

        m_botAction.sendArenaMessage(sender + " has warped to death FREQ " + message + ".", 17);

        int toExclude = m_botAction.getPlayingFrequencySize(p.getFrequency());
        int total = m_botAction.getNumPlaying() - toExclude;
        int jump = (int) (360 / total);
        Iterator<Player> it = m_botAction.getPlayingPlayerIterator();

        // Center of the circle (wormhole) + diameter
        int x = coordMegaWarp.x;
        int y = coordMegaWarp.y;
        int d = 25;

        int i = 0;
        while (it.hasNext()) {
            Player player = it.next();
            if (p.getFrequency() == player.getFrequency() || context.getPubChallenge().isDueling(player.getPlayerName()))
                continue;

            int posX = x + (int) (d * Math.cos(i * jump));
            int posY = y + (int) (d * Math.sin(i * jump));

            m_botAction.warpTo(player.getPlayerName(), posX, posY);
            m_botAction.specificPrize(player.getPlayerName(), Tools.Prize.ENERGY_DEPLETED);

            i++;
        }

    }

    /**
     * Executes the special shop item Immunity.
     * <p>
     * This method provides the buyer with 4 minutes of immunity through {@link PubStore#addImmunity(String)} 
     * and a timer that removes the immunity after the set time has passed.
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender Person who bought the immunity.
     * @param params Unused at the moment.
     */
    @SuppressWarnings("unused")
    private void itemCommandImmunity(final String sender, String params) {

        m_botAction.sendSmartPrivateMessage(sender, "You have now an immunity for 4 minutes.");

        store.addImmunity(sender);
        TimerTask task = new TimerTask() {
            public void run() {
                m_botAction.sendSmartPrivateMessage(sender, "Immunity lost.");
                store.removeImmunity(sender);
            }
        };
        m_botAction.scheduleTask(task, 4 * Tools.TimeInMillis.MINUTE);

    }

    /**
     * Executes the special shop item BaseBlast.
     * <p>
     * This special item puts the pubsystem bot inside the flagroom and sends out projectiles in all directions.
     * It does this in two full circles at 5 degree angles. Per angle per circle two projectiles are fired, which
     * seem to be a level 1 and level 2 single bullet?
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender
     * @param params
     */
    @SuppressWarnings("unused")
    private void itemCommandBaseBlast(String sender, String params) {

        Player p = m_botAction.getPlayer(sender);

        m_botAction.getShip().setShip(0);
        m_botAction.getShip().setFreq(p.getFrequency());
        m_botAction.sendUnfilteredPrivateMessage(m_botAction.getBotName(), "*super");
        m_botAction.specificPrize(m_botAction.getBotName(), Tools.Prize.SHIELDS);

        m_botAction.sendArenaMessage(sender + " has sent a blast of bombs inside the flagroom!", Tools.Sound.HALLELUJAH);
        final TimerTask timerFire = new TimerTask() {
            public void run() {
                m_botAction.getShip().move(coordBaseBlast.x, coordBaseBlast.y);
                for (int j = 0; j < 2; j++) {
                    for (int i = 0; i < 360 / 5; i++) {

                        m_botAction.getShip().rotateDegrees(i * 5);
                        m_botAction.getShip().sendPositionPacket();
                        m_botAction.getShip().fire(34);
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {}
                        m_botAction.getShip().fire(35);
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {}
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

    /**
     * Executes the special shop item NukeBase.
     * <p>
     * This special command warps any team mates of the buyer who are in the flag room to a safe location.
     * As soon as the players have been warped out, the bot enters a ship and fires a Thor into the flag room.
     * After the Thor has detonated, the players who previously got warped out will be warped back in.
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender Person who bought the NukeBase
     * @param params Currently unused
     */
    @SuppressWarnings("unused")
    private void itemCommandNukeBase(String sender, String params) {
        Player p = m_botAction.getPlayer(sender);
        final int freq = p.getFrequency();

        final Vector<Shot> shots = getShots();
        final Vector<Warper> warps = new Vector<Warper>();
        Iterator<Integer> i = m_botAction.getFreqIDIterator(freq);
        while (i.hasNext()) {
            int id = i.next();
            Player pl = m_botAction.getPlayer(id);
            int reg = regions.getRegion(pl);
            if (reg == 1 || reg == 2 || reg == 3 || reg == 5)
                warps.add(new Warper(id, pl.getXTileLocation(), pl.getYTileLocation()));
        }

        m_botAction.getShip().setShip(0);
        m_botAction.getShip().setFreq(freq);
        m_botAction.specificPrize(m_botAction.getBotName(), Tools.Prize.SHIELDS);

        m_botAction.sendTeamMessage("Incoming nuke! Anyone inside the FLAGROOM will be WARPED for a moment and then returned when safe.");
        m_botAction.sendArenaMessage(sender + " has sent a nuke in the direction of the flagroom! Impact is imminent!", 17);
        final TimerTask timerFire = new TimerTask() {
            public void run() {
                while (!shots.isEmpty()) {
                    m_botAction.getShip().sendPositionPacket();
                    Shot s = shots.remove(0);
                    m_botAction.getShip().rotateDegrees(s.a);
                    m_botAction.getShip().sendPositionPacket();
                    m_botAction.getShip().move(s.x, s.y);
                    m_botAction.getShip().sendPositionPacket();
                    m_botAction.getShip().fire(WeaponFired.WEAPON_THOR);
                    try {
                        Thread.sleep(75);
                    } catch (InterruptedException e) {}
                }
            }
        };
        timerFire.run();

        TimerTask shields = new TimerTask() {
            public void run() {
                for (Warper w : warps)
                    w.save();
            }
        };
        m_botAction.scheduleTask(shields, 3100);

        TimerTask timer = new TimerTask() {
            public void run() {
                m_botAction.specWithoutLock(m_botAction.getBotName());
                m_botAction.move(coordNukeBase.x, coordNukeBase.y);
                m_botAction.setPlayerPositionUpdating(300);
                m_botAction.getShip().setSpectatorUpdateTime(100);
                //Iterator<Integer> i = m_botAction.getFreqIDIterator(freq);
                for (Warper w : warps)
                    w.back();
            }
        };
        m_botAction.scheduleTask(timer, 5500);
    }
    
    /**
     * Executes the special shop item Fireworks.
     * <p>
     * This special command displays fireworks to all players.
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender Person who bought the Fireworks
     * @param params Currently unused
     */
    @SuppressWarnings("unused")
    private void itemCommandFireworks(String sender, String params) {
        m_botAction.sendArenaMessage(".-=( IT'S A CELEBRATION, SNITCHES )=-.  The Grand Royale " + sender.toUpperCase() + " has ordered a fireworks display, to commence forthwith!!", Tools.Sound.CROWD_OOO);
        final TimerTask displayFireworks = new TimerTask() {
            int iterations = 0;
            Random r = new Random();
            public void run() {
                if (iterations >= 100)
                    try {
                        this.cancel();
                    } catch(Exception e) {}
                else {
                    iterations++;
                    // TODO: Get better obj#s for fireworks
                    m_botAction.showObject((r.nextInt(8) + 1));
                }
            }
        };
        m_botAction.scheduleTaskAtFixedRate(displayFireworks, 2000, 150);
    }
    
    /**
     * This class is used by the {@link PubMoneySystemModule#itemCommandNukeBase(String, String) NukeBase} command.
     * It's main purpose is to temporary store the location of a group of players, warp these players to safety
     * and afterwards warp them back to their original coordinates.
     * @author unknown
     *
     */
    private class Warper {
        int id, x, y;

        /** Warper constructor */
        public Warper(int id, int x, int y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }

        /**
         * Warps a specific player to a safe location.
         */
        public void save() {
            m_botAction.warpTo(id, 512, 141);
        }

        /**
         * Warps a specific player back to its previous location.
         */
        public void back() {
            m_botAction.warpTo(id, x, y);
        }
    }

    /**
     * The purpose of this class is to track projectiles(?)
     * @author unknown
     *
     */
    class Shot {
        int a, x, y;

        /** Shot constructor */
        public Shot(int a, int x, int y) {
            this.a = a;
            this.x = x * 16;
            this.y = y * 16;
        }
    }

    /**
     * A vector of a variety of projectiles with a specific angle, x- and y-coordinate.
     * @return A vector of a lot of shots.
     */
    private Vector<Shot> getShots() {
        Vector<Shot> s = new Vector<Shot>();
        s.add(new Shot(15, 396, 219));
        s.add(new Shot(165, 628, 219));
        s.add(new Shot(30, 407, 198));
        s.add(new Shot(150, 617, 198));
        s.add(new Shot(47, 425, 180));
        s.add(new Shot(135, 599, 180));
        s.add(new Shot(65, 450, 160));
        s.add(new Shot(115, 575, 160));
        s.add(new Shot(80, 472, 150));
        s.add(new Shot(100, 552, 150));
        //s.add(new Shot(90, 492, 147));
        s.add(new Shot(90, 496, 154));
        s.add(new Shot(90, 531, 147));
        return s;
    }

    /**
     * Executes the special shop item RoofTurret.
     * <p>
     * This function tries to spawn in a new TW-Bot of the type pubautobot.
     * When done, it will send some configuration commands to the new bot, so that it will act like a roof turret.
     * To prevent exploits, the communcation between the bots is done over IPC.
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender Person who bought the Roof Turret.
     * @param params Any additional parameters.
     * @see AutobotRoofThread
     * @see pubautobot
     */
    @SuppressWarnings("unused")
    private void itemCommandRoofTurret(String sender, String params) {

        m_botAction.sendSmartPrivateMessage(sender, "Please wait while looking for a bot..");

        Thread t = new AutobotRoofThread(sender, params, m_botAction, IPC_CHANNEL);
        ipcReceivers.add((IPCReceiver) t);
        t.start();

    }

    /**
     * Executes the special shop item BaseTerr.
     * <p>
     * This function tries to spawn in a new TW-Bot of the type pubautobot.
     * When done, it will send some configuration commands to the new bot, so that it will act like a base terrier.
     * To prevent exploits, the communcation between the bots is done over IPC.
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender Person who bought the base terrier.
     * @param params Any additional parameters.
     * @see AutobotBaseTerThread
     * @see pubautobot
     */
    @SuppressWarnings("unused")
    private void itemCommandBaseTerr(String sender, String params) {

        m_botAction.sendSmartPrivateMessage(sender, "Please wait while looking for a bot..");

        Thread t1 = new AutobotBaseTerThread(sender, params, m_botAction, IPC_CHANNEL);
        ipcReceivers.add((IPCReceiver) t1);
        t1.start();
    }

    /**
     * Executes the special shop item BaseStrike.
     * <p>
     * This command attempts to warp an entire freq into the base at once. The location where the team is warped to
     * is randomly chosen from a list of base coordinates and is dependent on ship type. The Terriers will spawn
     * at the center of the warp in location, with Sharks on top of them for protection. Any other type of ship
     * will be spawned in the vicinity of the center, acting as a protective shell. People who are on the same freq
     * but reside either in a safe or are dueling or are piloting a Leviathan are excluded from the warp.
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender Person who bougth the BaseStrike.
     * @param params Currently unused.
     */
    @SuppressWarnings("unused")
    private void itemCommandBaseStrike(String sender, String params) {
        Short freq = null;

        Player commander = m_botAction.getPlayer(sender);
        
        // Default null check
        if(commander == null)
            return;
        
        // Since it might take a bit to execute this routine, prefetch the command's frequency.
        freq = commander.getFrequency();
        if(freq == null)
            return;
        
        Iterator<Player> it = m_botAction.getPlayingPlayerIterator();
        while (it.hasNext()) {

            Player player = it.next();

            if (player.getFrequency() != freq)
                continue;
            if (context.getPubChallenge().isDueling(player.getPlayerName()))
                continue;
            // Check if the player is a lev. If so, do not warp.
            if (player.getShipType() == Tools.Ship.LEVIATHAN)
                continue;
            // Do not warp players that are in a safe.
            Region reg = context.getPubUtil().getRegion(player.getXTileLocation(), player.getYTileLocation());
            if(reg != null && Region.SAFE.equals(reg))
                continue;
            
            // Terr always warped on the middle
            if (player.getShipType() == Tools.Ship.TERRIER) {
                m_botAction.warpTo(player.getPlayerName(), coordsBaseStrike[1]);
                // Shark always warped on top
            } else if (player.getShipType() == Tools.Ship.SHARK) {
                int num = (int) Math.floor(Math.random() * 3);
                m_botAction.warpTo(player.getPlayerName(), coordsBaseStrike[num]);
                // The rest is random..
            } else {
                int num = (int) Math.floor(Math.random() * coordsBaseStrike.length);
                    if (num == 1)
                        m_botAction.warpTo(player.getPlayerName(), coordsBaseStrike[num]);
                    else if (num == 0 || num == 2)
                        m_botAction.warpTo(player.getPlayerName(), coordsBaseStrike[num], 1);
                    else if (num >= 3)
                        m_botAction.warpTo(player.getPlayerName(), coordsBaseStrike[num], 3);
            }
        }
        if (freq < 100)
            m_botAction.sendArenaMessage("FREQ " + freq + " is striking the flag room! Commanded by " + sender + ".", Tools.Sound.CROWD_OHH);
        else
            m_botAction.sendArenaMessage("A private freq is striking the flag room! Commanded by " + sender + ".", Tools.Sound.CROWD_OHH);

    }

    /**
     * Executes the special shop item FlagSaver.
     * <p>
     * This function puts the pubsystem bot into play, to claim the flag for the team of the purchaser.
     * Due to the way the bots and the server work, it is nescessary to send a capture packet out. On top of that,
     * to update everything on the botside of things, the {@link GameFlagTimeModule} needs to be informed as well.
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender Person who bought the flag saver.
     * @param params Unused at the moment.
     */
    @SuppressWarnings("unused")
    private void itemCommandFlagSaver(String sender, String params) {
        Iterator<Integer> flagIt;
        Player p = m_botAction.getPlayer(sender);
        int freq;
        
        if(p == null) {
            // We will be unable to determine the target frequency without a Player object.
            return;
        }
        
        //Store the freq to avoid null pointer exceptions later on.
        freq = p.getFrequency();
        
        m_botAction.getShip().setShip(1);
        m_botAction.getShip().setFreq(freq);
        m_botAction.getShip().rotateDegrees(270);
        m_botAction.specificPrize(m_botAction.getBotName(), Tools.Prize.SHIELDS);
        m_botAction.getShip().move(coordFlagSaver.x, coordFlagSaver.y);

        TimerTask timer = new TimerTask() {
            public void run() {
                m_botAction.specWithoutLock(m_botAction.getBotName());
                m_botAction.setPlayerPositionUpdating(300);
            }
        };
        m_botAction.scheduleTask(timer, 4000);
        
        // Botside, we need to claim the flag
        context.getGameFlagTime().remoteFlagClaim(freq);
        // Serverside, we need to claim the flag.
        flagIt = m_botAction.getFlagIDIterator();
        while(flagIt.hasNext()) {
            m_botAction.getFlag(flagIt.next());
        }

        if (freq < 100)
            m_botAction.sendArenaMessage("[BUY] " + m_botAction.getBotName() + " got the flag for FREQ " + freq + ", thanks to " + sender + "!");
        else
            m_botAction.sendArenaMessage("[BUY] " + m_botAction.getBotName() + " got the flag for a PRIVATE FREQ, thanks to " + sender + "!");

    }

    /**
     * Executes the special shop item Blindness.
     * <p>
     * This method will cast blindness onto the targetted player for 15 seconds, starting 4 seconds after executing this function.
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender Person who bought the blindness.
     * @param params The targeted player who will receive the blindness.
     * @throws PubException Various exceptions can happen. The main ones will be {@link NullPointerException NPE's} and {@link IllegalStateException ISE's}.
     * @see BlindnessTask
     */
    @SuppressWarnings("unused")
    private void itemCommandBlindness(String sender, String params) throws PubException {

        Player p1 = m_botAction.getPlayer(sender);
        Player p2 = m_botAction.getPlayer(params);

        m_botAction.sendPrivateMessage(params, "You will be soon struck with a mysterious case of sudden blindness gave by " + p1.getPlayerName() + ".", Tools.Sound.CRYING);
        m_botAction.sendPrivateMessage(sender, "Blindness gave to " + p2.getPlayerName() + ".");
        // Start the actual blindness task.
        m_botAction.scheduleTask(new BlindnessTask(params, 15, m_botAction), 4 * Tools.TimeInMillis.SECOND);

    }

    /**
     * Executes the special shop item Sphere.
     * <p>
     * This casts a sphere of seclusion on any player who isn't on the buyer's frequency, unless that frequency
     * has {@link #itemCommandImmunity(String, String) Immunity} active, or the player is currently in a duel.
     * The sphere of seclusion will last for 30 seconds.
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender Person who bought the sphere.
     * @param params Currently unused.
     * @see SphereSeclusionTask
     */
    @SuppressWarnings("unused")
    private void itemCommandSphere(String sender, String params) {

        updateFreqImmunity();
        Player p = m_botAction.getPlayer(sender);

        String message = "";
        int privFreqs = 0;

        long now = System.currentTimeMillis();

        // Get a list of all the non-immune frequencies.
        List<Integer> freqList = new ArrayList<Integer>();
        for (int i = 0; i < 10000; i++) {
            int size = m_botAction.getPlayingFrequencySize(i);
            if (size > 0 && i != p.getFrequency() && !immuneFreqs.containsKey(i)) {
                freqList.add(i);
                immuneFreqs.put(i, now);
                if (i < 100) {
                    message += ", " + i;
                } else {
                    privFreqs++;
                }
            }
        }
        if (message.length() > 2) {
            message = "FREQ " + message.substring(2);
            if (privFreqs > 0)
                message += " AND ";
        }

        if (privFreqs > 0)
            message += privFreqs + " private freq(s)";

        final Integer[] freqs = freqList.toArray(new Integer[freqList.size()]);

        if (!freqList.isEmpty() || privFreqs > 0)
            m_botAction.sendArenaMessage(sender + " has bought a Sphere of Seclusion for " + message + ".", 17);
        else
            m_botAction.sendArenaMessage(sender + " has bought a Sphere of Seclusion DUD (all freqs immune).", 17);

        // Start a TimerTask for starting and removing the Sphere of Seclusion.
        m_botAction.scheduleTask(new SphereSeclusionTask(freqs, true), 0);
        m_botAction.scheduleTask(new SphereSeclusionTask(freqs, false), 30 * Tools.TimeInMillis.SECOND);

    }

    /**
     * Executes the special shop item Epidemic.
     * <p>
     * This method will cast a serie of "debuffs" on the players of the opponent frequencies. These debuffs
     * consist out of energy depletions and engine shutdowns.
     * <p>
     * Do not remove this function despite the unused warning. This method can be called upon through an invoke, 
     * which is not detected by Eclipse.
     * @param sender Person who bought the epidemic.
     * @param params Currently unused.
     * @see EnergyDepletedTask
     * @see EngineShutdownExtendedTask
     */
    @SuppressWarnings("unused")
    private void itemCommandEpidemic(String sender, String params) {

        Player p = m_botAction.getPlayer(sender);

        String message = "";
        int privFreqs = 0;

        // Compile a list of targetted freqs.
        List<Integer> freqList = new ArrayList<Integer>();
        for (int i = 0; i < 10000; i++) {
            int size = m_botAction.getPlayingFrequencySize(i);
            if (size > 0 && i != p.getFrequency()) {
                freqList.add(i);
                if (i < 100) {
                    message += ", " + i;
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

        m_botAction.sendArenaMessage("[BUY] " + sender + " has started an epidemic on FREQ " + message + ".", Tools.Sound.UNDER_ATTACK);

        // Initiate the TimerTasks that handle the energy depletion and engine shutdown.
        int timeElapsed = 0;
        for (int i = 1; i < 10; i++) {
            timeElapsed += 2300 - (int) (Math.log(i) * 1000);
            m_botAction.scheduleTask(new EnergyDepletedTask(freqs), timeElapsed);
        }
        m_botAction.scheduleTask(new EnergyDepletedTask(freqs), timeElapsed + 150);
        m_botAction.scheduleTask(new EnergyDepletedTask(freqs), timeElapsed + 300);
        m_botAction.scheduleTask(new EnergyDepletedTask(freqs), timeElapsed + 450);
        m_botAction.scheduleTask(new EnergyDepletedTask(freqs), timeElapsed + 600);
        m_botAction.scheduleTask(new EnergyDepletedTask(freqs), timeElapsed + 750);
        m_botAction.scheduleTask(new EngineShutdownExtendedTask(freqs), timeElapsed + 750);

    }

    /**
     * Currently empty.
     */
    @Override
    public void start() {
    }

    /**
     * Reloads the various configuration settings.
     */
    @Override
    public void reloadConfig() {
        store.reloadConfig();
        if (m_botAction.getBotSettings().getInt("money_enabled") == 1) {
            enabled = true;
        } else {
            store.turnOff();
        }
        if (m_botAction.getBotSettings().getInt("donation_enabled") == 1) {
            donationEnabled = true;
        }
        couponOperators = new HashSet<String>();
        if (m_botAction.getBotSettings().getString("coupon_operators") != null) {
            List<String> list = Arrays.asList(m_botAction.getBotSettings().getString("coupon_operators").split("\\s*,\\s*"));
            for (String name : list) {
                couponOperators.add(name.toLowerCase());
            }
        }
        
        // Which ships are not safezone restricted.
        canBuyAnywhere = new ArrayList<Integer>();
        for(Integer ship : m_botAction.getBotSettings().getIntArray("AllowBuyOutsideSafe", ",")) {
            canBuyAnywhere.add(ship);
        }
        
        // Are LTs able to buy outside the safezone?
        canLTBuyAnywhere = (m_botAction.getBotSettings().getInt("AllowLTOutsideSafe") == 1);
                
        database = m_botAction.getBotSettings().getString("database");
        
        loadBans();
    }
    
    /**
     * Loads the active item and shop bans from file.
     */
    private void loadBans() {
        BotSettings cfg = m_botAction.getBotSettings();
        ArrayList<ItemBan> itemBanList;

        int i = 1;
        boolean entriesLeft = true;
        
        itemBans.clear();
        shopBans.clear();
        
        // Load item bans.
        while(entriesLeft) {
            String itemban = cfg.getString("itemban" + i);
            if (itemban != null && itemban.trim().length() > 0) {
                String[] itemBanSplit = itemban.split(":", 6);
                if (itemBanSplit.length == 6) {      // Check for corrupted data
                    if(!itemBans.isEmpty() && itemBans.containsKey(itemBanSplit[0])) {
                        itemBanList = itemBans.get(itemBanSplit[0]);
                        itemBanList.add(new ItemBan(itemBanSplit));
                    } else {
                        itemBanList = new ArrayList<ItemBan>();
                        itemBanList.add(new ItemBan(itemBanSplit));
                    }
                    itemBans.put(itemBanSplit[0], itemBanList);
                }
                i++;
            } else {
                entriesLeft = false;
            }
        }
        
        entriesLeft = true;
        i = 1;
        
        // Load shop bans.
        while(entriesLeft) {
            String shopban = cfg.getString("shopban" + i);
            if (shopban != null && shopban.trim().length() > 0) {
                String[] shopBanSplit = shopban.split(":", 5);
                if (shopBanSplit.length == 5) {      // Check for corrupted data
                    shopBans.put(shopBanSplit[0], new ShopBan(shopBanSplit));
                }
                i++;
            } else {
                entriesLeft = false;
            }
        }
    }

    /**
     * Saves the active item and shop bans to file.
     */
    private void saveBans() {
        BotSettings cfg = m_botAction.getBotSettings();
        boolean loop = true;
        int i = 1;

        // Save item bans
        for (String name : itemBans.keySet()) {
            ArrayList<ItemBan> itemBanList = itemBans.get(name);
            for(ItemBan itemban : itemBanList) {
                cfg.put("itemban" + i, itemban.toString());
                i++;
            }
        }
        // Clear any other still stored item bans
        while (loop) {
            if (cfg.getString("itemban" + i) != null) {
                cfg.remove("itemban" + i);
                i++;
            } else {
                loop = false;
            }
        }
        
        loop = true;
        i = 1;
        
        // Save shop bans
        for (String name : shopBans.keySet()) {
            ShopBan shopban = shopBans.get(name);
            cfg.put("shopban" + i, shopban.toString());
            i++;
        }
        // Clear any other still stored shop bans
        while (loop) {
            if (cfg.getString("shopban" + i) != null) {
                cfg.remove("shopban" + i);
                i++;
            } else {
                loop = false;
            }
        }
        
        cfg.save();
    }
    
    /**
     * Timertask that executes the prizing of items bought through the {@link PubShop}.
     * @author unknown
     *
     */
    private class PrizeTask extends TimerTask {

        private PubPrizeItem item;
        private String receiver;
        private List<Integer> prizes;
        private long startAt = System.currentTimeMillis();

        /** PrizeTask constructor */
        public PrizeTask(PubPrizeItem item, String receiver) {
            this.item = item;
            this.prizes = item.getPrizes();
            this.receiver = receiver;
        }

        /**
         * The executed code when this timertask fires, being the prizing of a player and resetting it when it ends.
         */
        public void run() {
            for (int prizeNumber : prizes) {
                m_botAction.specificPrize(receiver, prizeNumber);
            }
            if (item.hasDuration()) {
                if (System.currentTimeMillis() - startAt >= item.getDuration().getSeconds() * Tools.TimeInMillis.SECOND) {
                    m_botAction.sendUnfilteredPrivateMessage(receiver, "*shipreset");
                    cancel();
                }
            }
        }
    };

    /**
     * This class sets up a spawned in TW-Bot to act like a base terrier.
     * @author unknown
     * @see AutobotThread
     * @see pubautobot
     */
    private class AutobotBaseTerThread extends AutobotThread {

        /** AutobotBaseTerThread constructor */
        public AutobotBaseTerThread(String sender, String parameters, BotAction m_botAction, String ipcChannel) {
            super(sender, parameters, m_botAction, ipcChannel);
        }

        /**
         * This is called after the preparations have been done.
         */
        protected void ready() {
            m_botAction.sendTeamMessage("");
        }

        /**
         * Prepares the spawned in pubautobot to behave like it should do.
         * This includes things like the correct frequency, location, expiration time, amount of hits it can endure, etc. 
         */
        protected void prepare() {
            int freq;
            Player p = m_botAction.getPlayer(sender);
            if (p == null) {
                commandBot("!Die");
                return;
            }
            // Store frequency in case the player decides to DC within the next two seconds.
            freq = p.getFrequency();
            
            commandBot("!Go " + m_botAction.getArenaName().substring(8, 9));
            try {
                Thread.sleep(2 * Tools.TimeInMillis.SECOND);
            } catch (InterruptedException e) {}
            commandBot("!SetShipFreq 5:" + freq);
            if (freq == 0) {
                commandBot("!WarpTo " + coordsBaseTerrier[0].x + " " + coordsBaseTerrier[0].y);
                commandBot("!Face 15");
            } else if (freq == 1) {
                commandBot("!WarpTo " + coordsBaseTerrier[1].x + " " + coordsBaseTerrier[1].y);
                commandBot("!Face 25");
            } else {
                commandBot("!WarpTo " + coordsBaseTerrier[2].x + " " + coordsBaseTerrier[2].y);
                commandBot("!Face 20");
            }
            commandBot("!Timeout 300");
            commandBot("!Killable");
            commandBot("!DieAtXShots 20");
            commandBot("!QuitOnDeath");
            commandBot("!baseterr");
        }
    }

    /**
     * This class sets up a spawned in TW-Bot to act like a roof turret.
     * @author unknown
     * @see AutobotThread
     * @see pubautobot
     */
    private class AutobotRoofThread extends AutobotThread {

        /** AutobotRoofThread constructor */
        public AutobotRoofThread(String sender, String parameters, BotAction m_botAction, String ipcChannel) {
            super(sender, parameters, m_botAction, ipcChannel);
        }

        /**
         * After the perparations are done, send out a message to inform everyone.
         */
        protected void ready() {
            m_botAction.sendArenaMessage("[BUY] " + sender + " has bought a turret that will occupy the roof for 5 minutes.");
        }

        /**
         * Prepares the spawned in pubautobot to behave like it should do.
         * This includes things like the correct frequency, location, expiration time, firing methods, etc. 
         */
        protected void prepare() {
            int freq;
            
            Player p = m_botAction.getPlayer(sender);           
            if (p == null) {
                commandBot("!Die");
                return;
            }
            // Store frequency in case the player decides to DC within the next 2.5 seconds.
            freq = p.getFrequency();
            
            commandBot("!Go " + m_botAction.getArenaName().substring(8, 9));
            try {
                Thread.sleep(2 * Tools.TimeInMillis.SECOND);
            } catch (InterruptedException e) {}
            commandBot("!SetShipFreq 1:" + freq);
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {}
            commandBot("!WarpTo " + coordRoofTurret.x + " " + coordRoofTurret.y);
            commandBot("!RepeatFireOnSight 65 500");
            commandBot("!AimingAtEnemy");
            commandBot("!FastRotation");
            commandBot("!Timeout 300");
            commandBot("!Aim");

            // The autobot needs to know what is the roof
            if (!m_botAction.getBotSettings().getString("location").isEmpty()) {
                String[] pointsLocation = m_botAction.getBotSettings().getString("location").split(",");
                for (String number : pointsLocation) {
                    String data = m_botAction.getBotSettings().getString("location" + number);
                    if (data.startsWith("roof")) {
                        m_botAction.ipcSendMessage(IPC_CHANNEL, "locations:" + data.substring(5), autobotName, m_botAction.getBotName());
                    }
                }
            }

        }

    }

    /**
     * TimerTask that handles the prizing and removal of Blindness. 
     * @author unknown
     *
     */
    private class BlindnessTask extends TimerTask {
        private BotAction m_botAction;
        private String playerName;
        private int durationSecond;
        private long startedAt;

        /** BlindnessTask constructor */
        public BlindnessTask(String playerName, int durationSecond, BotAction m_botAction) {
            this.playerName = playerName;
            this.durationSecond = durationSecond;
            this.m_botAction = m_botAction;
            this.startedAt = System.currentTimeMillis();
        }

        /**
         * Main run routine. While the current duration is less than the total duration, it sends out
         * an *objon to give this player blindness. When the duration has exceeded the total duration, 
         * it will send out an *objoff, disabling the blindness.
         */
        public void run() {
            Runnable r = new Runnable() {
                public void run() {
                    // Enable blindness and keep it up for the entire duration.
                    while (System.currentTimeMillis() - startedAt < durationSecond * 1000) {
                        m_botAction.sendUnfilteredPrivateMessage(playerName, "*objon 562");
                        try {
                            Thread.sleep(1 * Tools.TimeInMillis.SECOND);
                        } catch (InterruptedException e) {}
                    }
                    // Disable the blindness.
                    m_botAction.sendUnfilteredPrivateMessage(playerName, "*objoff 562");
                }
            };
            Thread t = new Thread(r);
            t.start();

        }
    };

    /**
     * TimerTask which handles the sphere of seclusion "prizing".
     * @author unknown
     *
     */
    private class SphereSeclusionTask extends TimerTask {
        private Integer[] freqs;
        private boolean enable = false;

        /** SphereSeclusionTask constructor */
        public SphereSeclusionTask(Integer[] freqs, boolean enable) {
            this.freqs = freqs;
            this.enable = enable;
        }

        /**
         * Main run routine, which does the actual *objon and *objoff to give and remove the sphere.
         */
        public void run() {
            for (int freq : freqs) {
                for (Iterator<Player> i = m_botAction.getFreqPlayerIterator(freq); i.hasNext();) {
                    Player p = i.next();
                    // Exclude anyone who is immune.
                    if (store.hasImmunity(p.getPlayerName()))
                        continue;
                    // Exclude anyone who is dueling.
                    if (context.getPubChallenge().isDueling(p.getPlayerName()))
                        continue;
                    if (enable)
                        // Enable the sphere.
                        m_botAction.sendUnfilteredPrivateMessage(p.getPlayerID(), "*objon 561");
                    else
                        // Disable the sphere.
                        m_botAction.sendUnfilteredPrivateMessage(p.getPlayerID(), "*objoff 561");
                }
            }
        }
    };

    /**
     * TimerTask which handles the prizing of energy depletions.
     * @author unknown
     *
     */
    private class EnergyDepletedTask extends TimerTask {
        private Integer[] freqs;

        /** EnergyDepletedTask constructor */
        public EnergyDepletedTask(Integer[] freqs) {
            this.freqs = freqs;
        }

        /**
         * Main run routine. This does a one-time prizing of the energy depletion for all of the stored frequencies.
         */
        public void run() {
            for (int freq : freqs) {
                try {
                    for (Iterator<Player> i = m_botAction.getFreqPlayerIterator(freq); i.hasNext();) {
                        Player p = i.next();
                        // Exclude immune players.
                        if (store.hasImmunity(p.getPlayerName()))
                            continue;
                        // Exclude anyone who is dueling.
                        if (context.getPubChallenge().isDueling(p.getPlayerName()))
                            continue;
                        // Prize the energy depletion.
                        m_botAction.specificPrize(p.getPlayerID(), Tools.Prize.ENERGY_DEPLETED);
                    }
                } catch (Exception e) {}
            }
        }
    };

    /**
     * TimerTask which handles the prizing of the engine shutdown.
     * @author unknown
     *
     */
    private class EngineShutdownExtendedTask extends TimerTask {
        private Integer[] freqs;

        /** EngineShutdownExtendedTask constructor */
        public EngineShutdownExtendedTask(Integer[] freqs) {
            this.freqs = freqs;
        }

        /**
         * Main run routine. This prizes the extended engine shutdown to all of the stored frequencies.
         */
        public void run() {
            for (int freq : freqs) {
                try {
                    for (Iterator<Player> i = m_botAction.getFreqPlayerIterator(freq); i.hasNext();) {
                        Player p = i.next();
                        // Exclude immune players.
                        if (store.hasImmunity(p.getPlayerName()))
                            continue;
                        // Exclude players who are dueling.
                        if (context.getPubChallenge().isDueling(p.getPlayerName()))
                            continue;
                        // Prize the extended engine shutdown.
                        m_botAction.specificPrize(p.getPlayerID(), Tools.Prize.ENGINE_SHUTDOWN_EXTENDED);
                    }
                } catch (Exception e) {}
            }
        }
    }

    /**
     * Tracker class for individual shopbans. Disallows a player to completely use the shop.
     * @author Trancid
     *
     */
    private class ShopBan {
        private String name;        // Name of the banned person.
        private String issuer;      // Person who issued the ban.
        private Long startTime;     // Time when the ban was issued, in ms.
        private Integer duration;   // Duration of the ban, in hours.
        private String reason;      // Reason for the ban.
        
        /**
         * ShopBan constructor when a SMod+ issues a new shopban.
         * @param name Name of the person who will be shopbanned.
         * @param issuer Person who issued the ban.
         * @param duration The length of the ban, in hours.
         * @param reason The reason for the ban.
         */
        public ShopBan(String name, String issuer, Integer duration, String reason) {
            this.name = name;
            this.issuer = issuer;
            this.duration = duration;
            this.reason = reason;
            this.startTime = System.currentTimeMillis();
        }
        
        /**
         * ShopBan constructor when the information is loaded from a file.
         * @param itemBanSplit Array of the variables loaded. Must be 5 in total!
         */
        public ShopBan(String[] itemBanSplit) {
            this.name = itemBanSplit[0];
            this.issuer = itemBanSplit[1];
            try {
                this.startTime = Long.parseLong(itemBanSplit[2]);
            } catch (NumberFormatException e) {
                this.startTime = System.currentTimeMillis();
            }
            try {
                this.duration = Integer.parseInt(itemBanSplit[3]);
            } catch (NumberFormatException e) {
                this.duration = 1;
            }
            this.reason = itemBanSplit[4];
        }
        
        /**
         * Checks if the player is still banned from using the shop.
         * @return True when the ban period hasn't expired yet, otherwise false.
         */
        public boolean isShopBanned() {
            return (System.currentTimeMillis() - startTime < duration * Tools.TimeInMillis.HOUR);
        }
 
        /**
         * Preformatted status message used as feedback in look up functions.
         * @return Preformatted string which holds all the information on this specific ban.
         */
        public String getStatusMessage() {
            return ("Name: " + name + "; Issued by: " + issuer
                    + "; Expires in: " + Tools.getTimeDiffString(startTime + (duration * Tools.TimeInMillis.HOUR), true)
                    + "; Reason: " + reason);
        }
        
        /**
         * Method used to store the information to file in a preformatted method.
         */
        public String toString() {
            return (name + ":" + issuer + ":" + startTime + ":" + duration + ":" + reason);
        }
    }
 
    /**
     * Tracker class for individual itembans. Disallows a player from buying a specific item from the shop.
     * @author Trancid
     *
     */
    private class ItemBan {
        private String name;        // Name of the banned person.
        private String issuer;      // Person who issued the ban.
        private String item;        // Item of which the player is banned from.
        private Long startTime;     // Time when the ban got issued, in ms.
        private Integer duration;   // Duration of the ban, in hours.
        private String reason;      // Reason for the ban.
        
        /**
         * ItemBan constructor when a SMod+ issues a new itemban.
         * @param name Name of the person who will be shopbanned.
         * @param issuer Person who issued the ban.
         * @param item Item for which the user is banned from.
         * @param duration The length of the ban, in hours.
         * @param reason The reason for the ban.
         */
        public ItemBan(String name, String issuer, String item, Integer duration, String reason) {
            this.name = name;
            this.issuer = issuer;
            this.item = item;
            this.duration = duration;
            this.reason = reason;
            this.startTime = System.currentTimeMillis();
        }
        
        /**
         * ItemBan constructor when the information is loaded from a file.
         * @param itemBanSplit Array of the variables loaded. Must be 6 in total!
         */
        public ItemBan(String[] itemBanSplit) {
            this.name = itemBanSplit[0];
            this.issuer = itemBanSplit[1];
            this.item = itemBanSplit[2];
            try {
                this.startTime = Long.parseLong(itemBanSplit[3]);
            } catch (NumberFormatException e) {
                this.startTime = System.currentTimeMillis();
            }
            try {
                this.duration = Integer.parseInt(itemBanSplit[4]);
            } catch (NumberFormatException e) {
                this.duration = 1;
            }
            this.reason = itemBanSplit[5];
        }
        
        /**
         * Checks if the player is still banned from using this specific item.
         * @return True when the ban period hasn't expired yet, otherwise false.
         */
        public boolean isItemBanned() {
            return (System.currentTimeMillis() - startTime < duration * Tools.TimeInMillis.HOUR);
        }
        
        /**
         * Preformatted status message used as feedback in look up functions.
         * @return Preformatted string which holds all the information on this specific ban.
         */
        public String getStatusMessage() {
            return ("Name: " + name + "; Issued by: " + issuer + "; Item: " + item
                    + "; Expires in: " + Tools.getTimeDiffString(startTime + (duration * Tools.TimeInMillis.HOUR), true)
                    + "; Reason: " + reason);
        }
        
        /**
         * Method used to store the information to file in a preformatted method.
         */
        public String toString() {
            return (name + ":" + issuer + ":" + item + ":" + startTime + ":" + duration + ":" + reason);
        }
    }
    
    /**
     * Sorts a Hashmap into a LinkedHashMap by order of the values.
     * @param passedMap The map that needs to be sorted.
     * @param ascending The type of sorting. True for ascending, false for descending.
     * @return The sorted hashmap.
     */
    public LinkedHashMap<String, Integer> sort(HashMap<String, Integer> passedMap, boolean ascending) {

        List<String> mapKeys = new ArrayList<String>(passedMap.keySet());
        List<Integer> mapValues = new ArrayList<Integer>(passedMap.values());
        Collections.sort(mapValues);
        Collections.sort(mapKeys);

        if (!ascending)
            Collections.reverse(mapValues);

        LinkedHashMap<String, Integer> someMap = new LinkedHashMap<String, Integer>();
        Iterator<Integer> valueIt = mapValues.iterator();
        while (valueIt.hasNext()) {
            Integer val = valueIt.next();
            Iterator<String> keyIt = mapKeys.iterator();
            while (keyIt.hasNext()) {
                String key = keyIt.next();
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

    /**
     * Empty for now.
     */
    @Override
    public void stop() {
    }
    
}
