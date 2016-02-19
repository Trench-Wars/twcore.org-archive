package twcore.bots.pubsystem.module.player;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TimerTask;
import java.util.TreeMap;

import twcore.bots.pubsystem.module.PubUtilModule.Location;
//import twcore.bots.pubsystem.module.PubUtilModule.Tileset;
import twcore.bots.pubsystem.module.moneysystem.LvzMoneyPanel;
import twcore.bots.pubsystem.module.moneysystem.item.PubCommandItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubItemUsed;
//import twcore.bots.pubsystem.module.moneysystem.item.PubPrizeItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubShipItem;
import twcore.core.BotAction;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerDeath;
import twcore.core.game.Player;
import twcore.core.util.Point;
import twcore.core.util.Tools;
import twcore.core.lvz.Objset;


public class PubPlayer implements Comparable<PubPlayer> {

    private static final int MAX_ITEM_USED_HISTORY = 30 * Tools.TimeInMillis.MINUTE;
    private static final int DONATE_DELAY = Tools.TimeInMillis.MINUTE;
    private static final String db = "pubstats";
    
    public int MAX_MID_SPAWN_TINY = 8;      // Max freq size where "tiny"-sized basing coords will be used
                                            // TODO: Push to CFG
    
    // Spawn points for very low populations in midspawn. Uses RADIUS_MIDSPAWN.
    private static final Point[] COORDS_MIDSPAWN_TINY = {
            new Point(539, 323),    // Mid near bottom
            new Point(485, 323),
            new Point(474, 298),    // Mid behind the L blockades
            new Point(550, 298),

            // Experimental points (to decrease spawnkilling issues)
            new Point(512, 330),    // Mid, top of tube
            new Point(512, 350),    // Tube
            new Point(460, 284),    // Mid, L ear
            new Point(564, 284),    // Mid, R ear
            new Point(467, 341),    // Top of lower, side tubes, L side
            new Point(557, 341),    // Top of lower, side tubes, R side
    };
    
    // Spawn points for the low population mid spawn.
    private static final Point[] COORDS_MIDSPAWN = {
        new Point(539, 323),    // Mid near bottom
        new Point(485, 323),
        new Point(474, 298),    // Mid behind the L blockades
        new Point(550, 298),

        // Experimental points (to decrease spawnkilling issues)
        new Point(512, 330),    // Mid, top of tube
        new Point(512, 350),    // Tube
        new Point(460, 284),    // Mid, L ear
        new Point(564, 284),    // Mid, R ear
        new Point(467, 341),    // Top of lower, side tubes, L side
        new Point(557, 341),    // Top of lower, side tubes, R side

        // Experimental lower spawn points (further decrease spawnkilling)
        new Point(435, 333),    // Top lower ear, L side
        new Point(589, 333),    // Top lower ear, R side
        new Point(467, 359),    // Lower drop-circle, L side
        new Point(557, 359),    // Lower drop-circle, R side

        // Very low spawn points
        new Point(512, 377),    // Bottom of tube
        new Point(484, 353),    // Between tube and drop-circle, L side
        new Point(540, 353),    // Between tube and drop-circle, R side
        new Point(450, 382),    // Near entrance to lowest ear, L side
        new Point(574, 382),    // Near entrance to lowest ear, R side
        new Point(512, 403),    // Entrance to lower
    };

    // Radius for the low population mid spawn. Amount must be equal to the amount of Points in COORDS_MIDSPAWN.
    private static final int[] RADIUS_MIDSPAWN = {
        9, 9, 8, 8,
        5, 3, 7, 7, 3, 3,
        6, 6, 2, 2,
        6, 4, 4, 4, 4, 7
    };

    private BotAction m_botAction;

    //private Tileset tileset = Tileset.MONOLITH;

    private String name;
    private int money;
    private int moneyEarnedThisSession;
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
    private long lastSwitchReward = -1;
    private long lastSavedState = System.currentTimeMillis();
    private long lastOptionsUpdate = System.currentTimeMillis();
    private long lastDetachLevTerr = System.currentTimeMillis();
    private long lastFreqSwitch = System.currentTimeMillis();
    private long lastDeath = 0;
    private long lastAttach = 0;
    private long lastThor = 0;
    private long lastSpecTime = -1;

    // Stats
    private int bestStreak = 0;

    // History of the last kill
    private int lastKillShipKiller = -1;
    private int lastKillShipKilled = -1;
    private Location lastKillLocation;
    private String lastKillKilledName;
    private boolean lastKillWithFlag;
    private boolean notifiedAboutEZ = false;
    private TimerTask spawnDelay;

    // Misc vars
    private short lastFreq = 9999;
    private int deploys = 0;
    private boolean warp;
    // Used to track whether a shark is in safe due to the mine clearing warp, or because he was already there.
    private boolean minesCleared = false;
    // Used to track whether a player is in safe due to the FR clearing warp, or because he was already there.
    private boolean FRCleared = false;
    public boolean notifiedOfTKTax = false;

    private long playerID;
    private boolean hasStatsDB = false;

    public static int EZ_PENALTY = 100;

    public PubPlayer(BotAction m_botAction, String name, Objset moneyObjs) {
        this(m_botAction, name, 0, true, -1, moneyObjs);
    }

    public PubPlayer(BotAction m_botAction, String name, int money, boolean warp, Objset moneyObjs)
    {
        this(m_botAction, name, money, warp, -1, moneyObjs);
    }

    public PubPlayer(BotAction m_botAction, String name, int money, boolean warp, long fnid, Objset moneyObjs) {
        this.m_botAction = m_botAction;
        this.name = name;
        this.money = money;
        this.warp = warp;
        this.itemsBought = new LinkedList<PubItemUsed>();
        this.itemsBoughtForOther = new LinkedList<PubItemUsed>();
        this.itemsBoughtThisLife = new LinkedList<PubItem>();
        this.ignoreList = new LinkedList<String>();
        this.donated = new TreeMap<String, Long>(String.CASE_INSENSITIVE_ORDER);
        this.playerID = fnid;

        try {
            this.lastFreq = m_botAction.getPlayer(name).getFrequency();
            this.cashPanel = new LvzMoneyPanel(m_botAction.getPlayer(name).getPlayerID(), moneyObjs, m_botAction);
        } catch (Exception e) {
            this.cashPanel = new LvzMoneyPanel(999999, moneyObjs, m_botAction);
        }

        reloadPanel(false);
    }
    
    // TODO: Reoder methods into proper functions vs basic getters/setters

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

    public int getMoneyEarnedThisSession() {
        return moneyEarnedThisSession;
    }

    public void reloadPanel(boolean fullReset) {
        if (fullReset) {
            cashPanel.reset();
        } else {
            cashPanel.reset();
            cashPanel.update(String.valueOf(0), String.valueOf(money), true);
        }
    }

    public void setMoney(int money) {
        int before = this.money;

        if (money < 0)
            money = 0;

        this.money = money;
        cashPanel.update(String.valueOf(before), String.valueOf(money), (before > money ? false : true));
        this.lastMoneyUpdate = System.currentTimeMillis();
    }

    public void addMoney(int money) {
        money = Math.abs(money);
        setMoney(this.money + money);
        moneyEarnedThisSession += money;
    }

    public void removeMoney(int money) {
        money = Math.abs(money);
        setMoney(this.money - money);
        moneyEarnedThisSession -= money;
    }

    public void resetMoneyEarnedThisSession() {
        moneyEarnedThisSession = 0;
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

    public void setLastDetachLevTerr() {
        this.lastDetachLevTerr = System.currentTimeMillis();
    }

    public boolean getLevTerr() {
        Player p1 = m_botAction.getPlayer(this.name);
        Player p2 = null;
        boolean levTerr = false;

        if(p1 != null) {
            if(p1.isAttached() && p1.getShipType() == 4)  {
                levTerr = true;
            } else if (p1.hasAttachees() && p1.getShipType() == 5) {
                for (int i : p1.getTurrets()) {
                    p2 = m_botAction.getPlayer(i);

                    if (p2 != null && !levTerr) {
                        if (p2.getShipType() == 4)
                            levTerr = true;
                    }
                }
            }
        }

        return levTerr;
    }

    public long getLastDetachLevTerr() {
        return lastDetachLevTerr;
    }

    public long getLastAttach() {
        return lastAttach;
    }

    public boolean getWarp() {
        return warp;
    }

    public boolean setWarp() {
        warp = !warp;
        m_botAction.SQLBackgroundQuery(db, null, "UPDATE tblPlayerStats SET fbWarp = " + (warp ? "1" : "0") + " WHERE fcName = '" + Tools.addSlashesToString(name) + "'");
        return warp;
    }

    public boolean areMinesCleared() {
        return minesCleared;
    }

    public void setMinesCleared(boolean minesCleared) {
        this.minesCleared = minesCleared;
    }

    public boolean wasFRCleared() {
        return FRCleared;
    }

    public void setFRCleared(boolean FRCleared) {
        this.FRCleared = FRCleared;
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

            if (System.currentTimeMillis() - item.getTime() > MAX_ITEM_USED_HISTORY) {
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

            if (System.currentTimeMillis() - item.getTime() > MAX_ITEM_USED_HISTORY) {
                it.remove();
            } else {
                break;
            }
        }
    }

    public boolean hasItemActive(PubItem itemToCheck) {
        if (itemToCheck == null)
            return false;

        Iterator<PubItemUsed> it = itemsBought.descendingIterator();

        while(it.hasNext()) {
            PubItemUsed item = it.next();

            if (!item.getItem().getName().equals(itemToCheck.getName()))
                continue;

            if (itemToCheck.hasDuration() && itemToCheck.getDuration().getSeconds() != -1) {
                int duration = itemToCheck.getDuration().getSeconds();

                if (duration * 1000 > System.currentTimeMillis() - item.getTime()) {
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
        return (m_botAction.getPlayer(name).getShipType()) == 0;
    }

    public boolean isInSafeZone() {
        return m_botAction.getPlayer(name).isInSafe();
    }

    public void doSpawnMid() {
        // Generate a number between 0 and the amount of warp points we have.
        // To prevent an out of boundaries error, pick the smallest of the two sizes of COORDS_MIDSPAWN and RADIUS_MIDSPAWN. (They should be equal)
        Player p = m_botAction.getPlayer(name);

        if (p != null) {
            if (m_botAction.getNumPlaying() <= MAX_MID_SPAWN_TINY) {
                int spawnPoint = (int) (Math.random() * COORDS_MIDSPAWN_TINY.length);
                m_botAction.warpTo(p.getPlayerName(), COORDS_MIDSPAWN_TINY[spawnPoint], RADIUS_MIDSPAWN[spawnPoint]);
            } else {
                int spawnPoint = (int) (Math.random() * Math.min(COORDS_MIDSPAWN.length, RADIUS_MIDSPAWN.length));
                m_botAction.warpTo(p.getPlayerName(), COORDS_MIDSPAWN[spawnPoint], RADIUS_MIDSPAWN[spawnPoint]);
            }
        }
    }

    public void doLowPopSpawn(Boolean deathspawn) {
        Player p = m_botAction.getPlayer(name);

        if (p != null && p.getShipType() != Tools.Ship.LEVIATHAN) {
            if(deathspawn) {
                this.spawnDelay = new TimerTask() {
                    @Override
                    public void run() {
                        doSpawnMid();
                    }
                };
                m_botAction.scheduleTask(this.spawnDelay, Tools.TimeInMillis.SECOND * 5);
            } else
                doSpawnMid();
        }
    }

    public void handleShipChange(FrequencyShipChange event) {
        if (shipItem != null && event.getShipType() != shipItem.getShipNumber())
            resetShipItem();

        resetSpecTime(event.getShipType());
    }

    public void handleDeath(PlayerDeath event) {
        resetItems();

        if (shipItem != null) {
            deathsOnShipItem++;
        }
    }

    public void setLastSwitchReward() {
        this.lastSwitchReward = System.currentTimeMillis();
    }

    public Long getLastSwitchReward() {
        return lastSwitchReward;
    }

    public void setLastFreqSwitch(short newFreq) {
        if(newFreq == lastFreq)
            return;

        lastFreq = newFreq;
        this.lastFreqSwitch = System.currentTimeMillis();
    }

    public Long getLastFreqSwitch() {
        return lastFreqSwitch;
    }

    public int getLastFreq() {
        return lastFreq;
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

    public long getPlayerID() {
        return playerID;
    }

    public void setPlayerID(long fnID) {
        playerID = fnID;
    }

    public boolean hasStatsDB() {
        return hasStatsDB;
    }

    public void setHasStatsDB(boolean stats) {
        hasStatsDB = stats;
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

    public void resetSpecTime( int shipNum ) {
        if (shipNum == Tools.Ship.SPECTATOR)
            lastSpecTime = System.currentTimeMillis();
        else
            lastSpecTime = -1;
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

    public void checkSpecTime() {
        if (lastSpecTime == -1 )
            return;

        if (lastSpecTime < System.currentTimeMillis() - Tools.TimeInMillis.HOUR * 1) {
            lastSpecTime = System.currentTimeMillis();
            m_botAction.sendPrivateMessage( name, "2cool4school Bonus: $100" );
            addMoney( 100 );
        }
    }
    
    public int getDeploys() {
        return deploys;
    }

    public void addDeploy() {
        deploys++;
    }
    
    public void resetDeploys() {
        deploys = 0;
    }

}
