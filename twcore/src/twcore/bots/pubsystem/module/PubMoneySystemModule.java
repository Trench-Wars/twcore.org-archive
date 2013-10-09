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

    // Arena
    //private String arenaNumber = "0";

    private boolean donationEnabled = false;
    private boolean leviBuyRestricted = true;
    private String database;
    private MapRegions regions;
    private static final String MAP_NAME = "pubmap";
    
    PreparedStatement updateMoney;

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

        try {

            if (playerManager.isPlayerExists(playerName)) {

                // Wait, is this player dueling?
                if (context.getPubChallenge().isDueling(playerName)) {
                    m_botAction.sendSmartPrivateMessage(playerName, "You cannot buy an item while dueling.");
                    return;
                }

                // Kill-o-thon running and he's the leader?
                if (context.getPubKillSession().isLeader(playerName)) {
                    m_botAction.sendSmartPrivateMessage(playerName, "You cannot buy an item while being a leader of kill-o-thon.");
                    return;
                }

                /* Oct-5-2013 Disabled restriction by request.
                Player p = m_botAction.getPlayer(playerName);
                if (p != null && p.getShipType() == 4 && leviBuyRestricted) {
                    // Levis can only buy in safe
                    Region r = context.getPubUtil().getRegion(p.getXTileLocation(), p.getYTileLocation());
                    if (r != null && !(Region.SAFE.equals(r))) {
                        // No buying except in safe!
                        m_botAction.sendSmartPrivateMessage(playerName, "Leviathans can only !buy in safe.");
                        return;
                    }
                }*/

                PubPlayer buyer = playerManager.getPlayer(playerName);

                // Retrieve the actual item. This inherently also checks for restrictions.
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
                    m_botAction.sendArenaMessage(playerName + " just bought a " + item.getDisplayName() + " for $" + item.getPrice() + ".", 21);
                }

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
     * Handles the !award command. (SMod+ or coupon owner)<br>
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
                line += " -- " + (item.getDescription() + " ($" + item.getPrice() + ")");
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

        /* LIMIT LTs TO SAFE */
        /* http://www.twcore.org/ticket/981 */
        if (!p.isInSafe()) {
            if (p.isShip(Ship.LEVIATHAN) && p.isAttached()) {
                m_botAction.sendPrivateMessage(sender, "LTs must be in a safety zone to purchase items.");
                return;
            } else if (p.isShip(Ship.TERRIER) && p.hasAttachees()) {
                LinkedList<Integer> playerIDs = p.getTurrets();
                for (Integer i : playerIDs) {
                    Player a = m_botAction.getPlayer(i);
                    if (a != null && a.isShip(Ship.LEVIATHAN)) {
                        m_botAction.sendPrivateMessage(sender, "LTs must be in a safety zone to purchase items.");
                        return;
                    }
                }
            }
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
     * Handles the !bankrupt command. (Mod+)
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
            m_botAction.sendSmartPrivateMessage(sender, ++count + ". " + entry.getKey() + " with $" + entry.getValue());
        }
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

    
    private void doCmdCouponListOps(String sender) {

        List<String> lines = new ArrayList<String>();
        lines.add("List of Operators:");
        for (String name : couponOperators) {
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
        } else if (!code.isEnabled()) {
            m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' is already disabled.");
        } else if (!code.isValid()) {
            m_botAction.sendSmartPrivateMessage(sender, "Code '" + codeString + "' is not valid anymore, useless.");
        } else {
            code.setEnabled(false);
            updateCouponDB(code, "update:" + codeString + ":" + sender);
        }

    }

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

            code.setEndAt(date);
            updateCouponDB(code, "update:" + codeString + ":" + sender);

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
        } else {
            if (limit > 0) {
                code.setMaxUsed(limit);
                updateCouponDB(code, "update:" + codeString + ":" + sender);

            } else {
                m_botAction.sendSmartPrivateMessage(sender, "Must be a number higher than 0.");
            }

        }
    }

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

    private CouponCode getCouponCode(String codeString) {
        return getCouponCode(codeString, false);
    }

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

    private void updateCouponDB(CouponCode code, String params) {

        String startAtString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(code.getStartAt());
        String endAtString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(code.getEndAt());

        m_botAction.SQLBackgroundQuery(database, "coupon:" + params, "UPDATE tblMoneyCode SET " + "fcDescription = '" + Tools.addSlashes(code.getDescription()) + "', " + "fnUsed = " + code.getUsed()
                + ", " + "fnMaxUsed = " + code.getMaxUsed() + ", " + "fdStartAt = '" + startAtString + "', " + "fdEndAt = '" + endAtString + "', " + "fbEnabled = " + (code.isEnabled() ? 1 : 0) + " "
                + "WHERE fnMoneyCodeId='" + code.getId() + "'");

    }

    public boolean isStoreOpened() {
        return store.isOpened();
    }
    
    public void resetRoundRestrictions() {
        store.resetRoundRestrictedItems();
    }

    public boolean isDatabaseOn() {
        return m_botAction.getBotSettings().getString("database") != null;
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

                if (pieces.length == 4 && pieces[1].equals("update")) {
                    m_botAction.sendSmartPrivateMessage(pieces[3], "Code '" + pieces[2] + "' updated.");
                }

                else if (pieces.length == 4 && pieces[1].equals("updateredeem")) {

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
                    m_botAction.sendSmartPrivateMessage(pieces[3], "Code '" + pieces[2] + "' created.");
                }
            }

            m_botAction.SQLClose(event.getResultSet());

        }

    }

    public void handleEvent(InterProcessEvent event) {
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

    public void handleEvent(PlayerDeath event) {

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
                            m_botAction.sendSmartPrivateMessage(killed.getPlayerName(), "You lost your ship after " + duration.getDeaths() + " death(s).", 22);
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

    public void handleCommand(String sender, String command) {

        if (command.startsWith("!items") || command.trim().equals("!i")) {
            doCmdItems(sender, false);
        } else if (command.trim().equals("!buy") || command.trim().equals("!b")) {
            doCmdItems(sender, false);
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
        } else if (command.equals("!lastkill")) {
            doCmdLastKill(sender);
        } else if (command.startsWith("!richest")) {
            doCmdRichest(sender, command);
        } else if (command.startsWith("!coupon") || command.startsWith("!c")) {

            // Coupon System commands
            boolean operator = couponOperators.contains(sender.toLowerCase());
            boolean smod = m_botAction.getOperatorList().isSmod(sender);

            // (Operator/SMOD only)
            if (operator || smod) {

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
        }

        if (m_botAction.getOperatorList().isSmod(sender) || couponOperators.contains(sender.toLowerCase())) {
            if (command.startsWith("!award ")) {
                doCmdAward(sender, command);
            } else if (command.equals("!pot")) {
                m_botAction.sendSmartPrivateMessage(sender, "$" + getMoneyPot());
            }
        }
    }

    public void handleModCommand(String sender, String command) {

        if (command.startsWith("!bankrupt")) {
            doCmdBankrupt(sender, command);
        } else if (command.startsWith("!debugobj")) {
            doCmdDebugObj(sender, command);
        } else if (command.equals("!toggledonation")) {
            doCmdToggleDonation(sender);
        } else if (m_botAction.getOperatorList().isSysop(sender) && command.startsWith("!addmoney")) {
            doCmdAddMoney(sender, command);
        }
    }

    public void handleSmodCommand(String sender, String command) {
        if (command.startsWith("!couponaddop "))
            doCmdCouponAddOp(sender, command.substring(12).trim());
        else if (command.equals("!couponlistops"))
            doCmdCouponListOps(sender);
        else if (m_botAction.getOperatorList().isSysop(sender))
            handleSysopCommand(sender, command);
    }
    
    public void handleSysopCommand(String sender, String command) {
        if (command.equals("!storehelp"))
            doCmdStoreCfgHelp(sender);
        else if (command.equals("!storecfg"))
            doCmdViewStoreCfg(sender);
        else if (command.startsWith("!edit "))
            doCmdEditCfg(sender, command);
    }

    /**
     * A convenient way to handle "ez" when we're trying to improve the level of sportsmanship pre-Steam.
     * 
     * @param sender
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

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[] {
                pubsystem.getHelpLine("!storehelp                             -- Displays the PubStore CFG help located in the CFG file."),
                pubsystem.getHelpLine("!storecfg                              -- Displays the PubStore CFG values."),
                pubsystem.getHelpLine("!edit <key>=<value>                    -- Modifies the pubsystem store configuration file. BE CAREFUL!"),
        };
    }

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
                pubsystem.getHelpLine("!lastkill           -- How much you earned for your last kill (+ algorithm)."), };
    }

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

    private String getSender(Message event) {
        if (event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE)
            return event.getMessager();

        int senderID = event.getPlayerID();
        return m_botAction.getPlayerName(senderID);
    }

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
     * Not always working.. need to find out why.
     */
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
        int x = 640;
        int y = 610;
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

    private void itemCommandBaseBlast(String sender, String params) {

        Player p = m_botAction.getPlayer(sender);

        m_botAction.getShip().setShip(0);
        m_botAction.getShip().setFreq(p.getFrequency());
        m_botAction.sendUnfilteredPrivateMessage(m_botAction.getBotName(), "*super");
        m_botAction.specificPrize(m_botAction.getBotName(), Tools.Prize.SHIELDS);

        m_botAction.sendArenaMessage(sender + " has sent a blast of bombs inside the flagroom!", Tools.Sound.HALLELUJAH);
        final TimerTask timerFire = new TimerTask() {
            public void run() {
                m_botAction.getShip().move(512 * 16 + 8, 270 * 16 + 8);
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
                m_botAction.move(512 * 16, 285 * 16);
                m_botAction.setPlayerPositionUpdating(300);
                m_botAction.getShip().setSpectatorUpdateTime(100);
                //Iterator<Integer> i = m_botAction.getFreqIDIterator(freq);
                for (Warper w : warps)
                    w.back();
            }
        };
        m_botAction.scheduleTask(timer, 5500);
    }

    private class Warper {
        int id, x, y;

        public Warper(int id, int x, int y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }

        public void save() {
            m_botAction.warpTo(id, 512, 141);
        }

        public void back() {
            m_botAction.warpTo(id, x, y);
        }
    }

    class Shot {
        int a, x, y;

        public Shot(int a, int x, int y) {
            this.a = a;
            this.x = x * 16;
            this.y = y * 16;
        }
    }

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

    private void itemCommandRoofTurret(String sender, String params) {

        m_botAction.sendSmartPrivateMessage(sender, "Please wait while looking for a bot..");

        Thread t = new AutobotRoofThread(sender, params, m_botAction, IPC_CHANNEL);
        ipcReceivers.add((IPCReceiver) t);
        t.start();

    }

    private void itemCommandBaseTerr(String sender, String params) {

        m_botAction.sendSmartPrivateMessage(sender, "Please wait while looking for a bot..");

        Thread t1 = new AutobotBaseTerThread(sender, params, m_botAction, IPC_CHANNEL);
        ipcReceivers.add((IPCReceiver) t1);
        t1.start();

        /*
        Thread t2 = new AutobotBaseTerThread(sender, params, m_botAction, IPC_CHANNEL);
        ipcReceivers.add((IPCReceiver)t2);
        t2.start();
        */

    }

    private void itemCommandBaseStrike(String sender, String params) {
        Short freq = null;

        int[][] coords = new int[][] { 
                new int[] { 501, 261 }, // Top right
                new int[] { 512, 257 }, // Top middle
                new int[] { 523, 261 }, // Top left
                new int[] { 536, 255 }, // Ear right
                new int[] { 488, 255 }, // Ear left
                new int[] { 493, 268 }, // Middle right
                new int[] { 531, 268 }, // Middle left
        //new int[] { 500, 287 }, // Bottom right
        //new int[] { 526, 287 }, // Bottom left
        };

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
                m_botAction.warpTo(player.getPlayerName(), coords[1][0], coords[1][1]);
                // Shark always warped on top
            } else if (player.getShipType() == Tools.Ship.SHARK) {
                int num = (int) Math.floor(Math.random() * 3);
                m_botAction.warpTo(player.getPlayerName(), coords[num][0], coords[num][1]);
                // The rest is random..
            } else {
                int num = (int) Math.floor(Math.random() * coords.length);
                    if (num == 1)
                        m_botAction.warpTo(player.getPlayerName(), coords[num][0], coords[num][1]);
                    else if (num == 0 || num == 2)
                        m_botAction.warpTo(player.getPlayerName(), coords[num][0], coords[num][1],1);
                    else if (num >= 3)
                        m_botAction.warpTo(player.getPlayerName(), coords[num][0], coords[num][1], 3);
            }
        }
        if (freq < 100)
            m_botAction.sendArenaMessage("FREQ " + freq + " is striking the flag room! Commanded by " + sender + ".", Tools.Sound.CROWD_OHH);
        else
            m_botAction.sendArenaMessage("A private freq is striking the flag room! Commanded by " + sender + ".", Tools.Sound.CROWD_OHH);

    }

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
        m_botAction.getShip().move(512 * 16 + 8, 269 * 16 + 8);

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
            m_botAction.sendArenaMessage(m_botAction.getBotName() + " got the flag for FREQ " + freq + ", thanks to " + sender + "!", Tools.Sound.CROWD_OHH);
        else
            m_botAction.sendArenaMessage(m_botAction.getBotName() + " got the flag for a private freq, thanks to " + sender + "!", Tools.Sound.CROWD_OHH);

    }

    private void itemCommandBlindness(String sender, String params) throws PubException {

        Player p1 = m_botAction.getPlayer(sender);
        Player p2 = m_botAction.getPlayer(params);

        m_botAction.sendPrivateMessage(params, "You will be soon struck with a mysterious case of sudden blindness gave by " + p1.getPlayerName() + ".", Tools.Sound.CRYING);
        m_botAction.sendPrivateMessage(sender, "Blindness gave to " + p2.getPlayerName() + ".");
        m_botAction.scheduleTask(new BlindnessTask(params, 15, m_botAction), 4 * Tools.TimeInMillis.SECOND);

    }

    private void itemCommandSphere(String sender, String params) {

        updateFreqImmunity();
        Player p = m_botAction.getPlayer(sender);

        String message = "";
        int privFreqs = 0;

        long now = System.currentTimeMillis();

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

        m_botAction.scheduleTask(new SphereSeclusionTask(freqs, true), 0);
        m_botAction.scheduleTask(new SphereSeclusionTask(freqs, false), 30 * Tools.TimeInMillis.SECOND);

    }

    private void itemCommandEpidemic(String sender, String params) {

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
        message = message.substring(2);

        final Integer[] freqs = freqList.toArray(new Integer[freqList.size()]);

        m_botAction.sendArenaMessage(sender + " has started an epidemic on FREQ " + message + ".", 17);

        int timeElapsed = 0;
        for (int i = 1; i < 10; i++) {
            timeElapsed += 2300 - (int) (Math.log(i) * 1000);
            m_botAction.scheduleTask(new EnergyDeplitedTask(freqs), timeElapsed);
        }
        m_botAction.scheduleTask(new EnergyDeplitedTask(freqs), timeElapsed + 150);
        m_botAction.scheduleTask(new EnergyDeplitedTask(freqs), timeElapsed + 300);
        m_botAction.scheduleTask(new EnergyDeplitedTask(freqs), timeElapsed + 450);
        m_botAction.scheduleTask(new EnergyDeplitedTask(freqs), timeElapsed + 600);
        m_botAction.scheduleTask(new EnergyDeplitedTask(freqs), timeElapsed + 750);
        m_botAction.scheduleTask(new EngineShutdownExtendedTask(freqs), timeElapsed + 750);

    }

    @Override
    public void start() {
        // TODO Auto-generated method stub

    }

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
        if (m_botAction.getBotSettings().getInt("levi_buy_in_safe") == 1) {
            leviBuyRestricted = true;
        }
        database = m_botAction.getBotSettings().getString("database");
    }

    private class PrizeTask extends TimerTask {

        private PubPrizeItem item;
        private String receiver;
        private List<Integer> prizes;
        private long startAt = System.currentTimeMillis();

        public PrizeTask(PubPrizeItem item, String receiver) {
            this.item = item;
            this.prizes = item.getPrizes();
            this.receiver = receiver;
        }

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

    private class AutobotBaseTerThread extends AutobotThread {

        public AutobotBaseTerThread(String sender, String parameters, BotAction m_botAction, String ipcChannel) {
            super(sender, parameters, m_botAction, ipcChannel);
        }

        protected void ready() {
            m_botAction.sendTeamMessage("");
        }

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
            commandBot("!SetFreq " + freq);
            commandBot("!SetShip 5");
            commandBot("!SetFreq " + freq);
            if (freq == 0) {
                commandBot("!WarpTo 488 254");
                commandBot("!Face 15");
            } else if (freq == 1) {
                commandBot("!WarpTo 536 254");
                commandBot("!Face 25");
            } else {
                commandBot("!WarpTo 512 257");
                commandBot("!Face 20");
            }
            commandBot("!Timeout 300");
            commandBot("!Killable");
            commandBot("!DieAtXShots 20");
            commandBot("!QuitOnDeath");
            commandBot("!baseterr");
        }
    }

    private class AutobotRoofThread extends AutobotThread {

        public AutobotRoofThread(String sender, String parameters, BotAction m_botAction, String ipcChannel) {
            super(sender, parameters, m_botAction, ipcChannel);
        }

        protected void ready() {
            m_botAction.sendArenaMessage(sender + " has bought a turret that will occupy the roof for 5 minutes.", 21);
        }

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
            commandBot("!SetShip 1");
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {}
            commandBot("!SetFreq " + freq);
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {}
            commandBot("!WarpTo 512 239");
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

    private class BlindnessTask extends TimerTask {
        private BotAction m_botAction;
        private String playerName;
        private int durationSecond;
        private long startedAt;

        public BlindnessTask(String playerName, int durationSecond, BotAction m_botAction) {
            this.playerName = playerName;
            this.durationSecond = durationSecond;
            this.m_botAction = m_botAction;
            this.startedAt = System.currentTimeMillis();
        }

        public void run() {
            Runnable r = new Runnable() {
                public void run() {
                    while (System.currentTimeMillis() - startedAt < durationSecond * 1000) {
                        m_botAction.sendUnfilteredPrivateMessage(playerName, "*objon 562");
                        try {
                            Thread.sleep(1 * Tools.TimeInMillis.SECOND);
                        } catch (InterruptedException e) {}
                    }
                    m_botAction.sendUnfilteredPrivateMessage(playerName, "*objoff 562");
                }
            };
            Thread t = new Thread(r);
            t.start();

        }
    };

    private class SphereSeclusionTask extends TimerTask {
        private Integer[] freqs;
        private boolean enable = false;

        public SphereSeclusionTask(Integer[] freqs, boolean enable) {
            this.freqs = freqs;
            this.enable = enable;
        }

        public void run() {
            for (int freq : freqs) {
                for (Iterator<Player> i = m_botAction.getFreqPlayerIterator(freq); i.hasNext();) {
                    Player p = i.next();
                    if (store.hasImmunity(p.getPlayerName()))
                        continue;
                    if (context.getPubChallenge().isDueling(p.getPlayerName()))
                        continue;
                    if (enable)
                        m_botAction.sendUnfilteredPrivateMessage(p.getPlayerID(), "*objon 561");
                    else
                        m_botAction.sendUnfilteredPrivateMessage(p.getPlayerID(), "*objoff 561");
                }
            }
        }
    };

    private class EnergyDeplitedTask extends TimerTask {
        private Integer[] freqs;

        public EnergyDeplitedTask(Integer[] freqs) {
            this.freqs = freqs;
        }

        public void run() {
            for (int freq : freqs) {
                try {
                    for (Iterator<Player> i = m_botAction.getFreqPlayerIterator(freq); i.hasNext();) {
                        Player p = i.next();
                        if (store.hasImmunity(p.getPlayerName()))
                            continue;
                        if (context.getPubChallenge().isDueling(p.getPlayerName()))
                            continue;
                        m_botAction.specificPrize(p.getPlayerID(), Tools.Prize.ENERGY_DEPLETED);
                    }
                } catch (Exception e) {}
            }
        }
    };

    private class EngineShutdownExtendedTask extends TimerTask {
        private Integer[] freqs;

        public EngineShutdownExtendedTask(Integer[] freqs) {
            this.freqs = freqs;
        }

        public void run() {
            for (int freq : freqs) {
                try {
                    for (Iterator<Player> i = m_botAction.getFreqPlayerIterator(freq); i.hasNext();) {
                        Player p = i.next();
                        if (store.hasImmunity(p.getPlayerName()))
                            continue;
                        if (context.getPubChallenge().isDueling(p.getPlayerName()))
                            continue;
                        m_botAction.specificPrize(p.getPlayerID(), Tools.Prize.ENGINE_SHUTDOWN_EXTENDED);
                    }
                } catch (Exception e) {}
            }
        }
    }

    public LinkedHashMap<String, Integer> sort(HashMap passedMap, boolean ascending) {

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

    }
}
