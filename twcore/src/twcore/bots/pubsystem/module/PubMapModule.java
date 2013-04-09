package twcore.bots.pubsystem.module;

import java.awt.Point;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.TimerTask;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.module.PubUtilModule.Region;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.util.MapRegions;
import twcore.core.util.Tools;

public class PubMapModule extends AbstractModule {

    
    private static final int SMALL_OBJON = 1001;
    private static final int MED_OBJON = 1002;
    private static final int LARGE_OBJON = 1003;
    private static final int LEFT_SIDE_DOOR = 1004;
    private static final int RIGHT_SIDE_DOOR = 1005;
    private static final int SMALL_BASE = 6;
    private static final int MED_BASE = 9;
    private static final int LARGE_BASE = 8;
    
    private int currentBase;    // current door setting

    private HashMap<Integer, Integer> popTriggers; // door setting mapped to popTrigger
    //private int popTrigger;     // cut off for number of players before door change
    private int popLeeway;      // number of players from trigger required for door change
    private int timeDelay;      // minimum amount of time between door changes
    private long lastChange;   
    
    private BotAction ba;
    private Random random;
    private BaseChange baseChanger;
    private boolean inPub;
    
    private MapRegions regions;
    
    public PubMapModule(BotAction botAction, PubContext context) {
        super(botAction, context, "PubMap");
        ba = botAction;
        inPub = ba.getArenaName().startsWith("(Public");
        random = new Random();
        lastChange = 0;
        currentBase = MED_BASE;
        regions = context.getPubUtil().getRegions();
        reloadConfig();
        ba.setPlayerPositionUpdating(300);
        TimerTask initialize = new TimerTask() {
            public void run() {
                inPub = ba.getArenaName().startsWith("(Public");
                doPopCheck();
            }
        };
        ba.scheduleTask(initialize, 5000);
    }

    @Override
    public void start() {
        doPopCheck();
    }

    @Override
    public void stop() {
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        EventRequester er = eventRequester;
        er.request(EventRequester.PLAYER_ENTERED);
        er.request(EventRequester.PLAYER_LEFT);
        er.request(EventRequester.FREQUENCY_SHIP_CHANGE);
        er.request(EventRequester.ARENA_JOINED);
    }

    @Override
    public void reloadConfig() {
        BotSettings set = ba.getBotSettings();
        int[] trigs = set.getIntArray("PopulationTrigger", ",");
        popTriggers = new HashMap<Integer, Integer>(3);
        popTriggers.put(SMALL_BASE, trigs[0]);
        popTriggers.put(LARGE_BASE, trigs[1]);
        popLeeway = set.getInt("PopulationLeeway");
        timeDelay = set.getInt("TimeDelay");
        enabled = set.getInt("pubmap_enabled") == 1;
        context.getPubUtil().reloadRegions();
        doPopCheck();
    }
    
    @Override
    public void handleEvent(ArenaJoined event) {
        inPub = ba.getArenaName().startsWith("(Public");
    }
    
    public void handleEvent(PlayerEntered event) {
        if (!enabled || !inPub) return;
        if (event.getShipType() > 0)
            doPopCheck();
        switch (currentBase) {
            case SMALL_BASE: 
                ba.showObjectForPlayer(event.getPlayerID(), SMALL_OBJON);
                ba.showObjectForPlayer(event.getPlayerID(), LEFT_SIDE_DOOR);
                ba.showObjectForPlayer(event.getPlayerID(), RIGHT_SIDE_DOOR);
                ba.hideObjectForPlayer(event.getPlayerID(), MED_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), LARGE_OBJON);
                break;
            case MED_BASE:
                ba.showObjectForPlayer(event.getPlayerID(), MED_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), SMALL_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), LARGE_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), LEFT_SIDE_DOOR);
                ba.hideObjectForPlayer(event.getPlayerID(), RIGHT_SIDE_DOOR);
                break;
            case LARGE_BASE: 
                ba.showObjectForPlayer(event.getPlayerID(), LARGE_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), SMALL_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), MED_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), LEFT_SIDE_DOOR);
                ba.hideObjectForPlayer(event.getPlayerID(), RIGHT_SIDE_DOOR);
                break;
        }
    }
    
    public void handleEvent(PlayerLeft event) {
        if (enabled && inPub)
            doPopCheck();
    }
    
    public void handleEvent(FrequencyShipChange event) {
        if (enabled && inPub)
            doPopCheck();
    }
    
    private void doPopCheck() {
        if (!enabled || !inPub) return;
        int pop = ba.getPlayingPlayers().size();
        long now = System.currentTimeMillis();
        if (lastChange != 0 && now - lastChange < timeDelay * Tools.TimeInMillis.MINUTE)
            return;
        if (pop > popTriggers.get(LARGE_BASE) + popLeeway)
            setBase(LARGE_BASE, false);
        else if (pop < popTriggers.get(SMALL_BASE) - popLeeway)
            setBase(SMALL_BASE, false);
        else if (currentBase == SMALL_BASE && pop > popTriggers.get(SMALL_BASE) + popLeeway)
            setBase(MED_BASE, false);
        else if (currentBase == LARGE_BASE && pop < popTriggers.get(LARGE_BASE) - (popLeeway*2))
            setBase(MED_BASE, false);
    }
    
    private String getBase(int base) {
        if (base == SMALL_BASE)
            return "SMALL";
        else if (base == MED_BASE)
            return "MEDIUM";
        else
            return "LARGE";
    }
    
    private void setBase(int base, boolean force) {
        if (!force && ((currentBase == base && lastChange != 0) || baseChanger != null))
            return;
        ba.sendArenaMessage("NOTICE: The map will be changing to " + getBase(base) + " in 5 seconds!", 24);
        baseChanger = new BaseChange(base);
        ba.scheduleTask(baseChanger, 5000);
    }
    
    private void warpForMedium() {
        Iterator<Player> i = ba.getPlayingPlayerIterator();
        while (i.hasNext()) {
            Player p = i.next();
            int reg = regions.getRegion(p);
            if (reg == Region.LARGE_FR.ordinal()) {
                Point coord = getRandomPoint(Region.FLAGROOM.ordinal());
                ba.warpTo(p.getPlayerID(), (int) coord.getX(), (int) coord.getY());
            }
        }
    }
    
    private void warpForSmall() {
        Iterator<Player> i = ba.getPlayingPlayerIterator();
        while (i.hasNext()) {
            Player p = i.next();
            int reg = regions.getRegion(p);
            if (reg == Region.LARGE_FR.ordinal() || reg == Region.MED_FR.ordinal()) {
                Point coord = getRandomPoint(Region.FLAGROOM.ordinal());
                ba.warpTo(p.getPlayerID(), (int) coord.getX(), (int) coord.getY());
            } else if (reg == Region.TUNNELS.ordinal()) {
                Point coord = getRandomPoint(Region.MID.ordinal());
                ba.warpTo(p.getPlayerID(), (int) coord.getX(), (int) coord.getY());
            }
        }
    }

    @Override
    public void handleCommand(String sender, String command) {
    }

    @Override
    public void handleModCommand(String sender, String command) {
    }

    @Override
    public void handleSmodCommand(String sender, String command) {
        if (command.equals("!loadcfg"))
            cmd_reloadConfig(sender);
        else if (command.equals("!getsets"))
            cmd_getSets(sender);
        else if (command.equals("!mapmod"))
            cmd_mapMod(sender);
        else if (command.equals("!setbase"))
            setBase(currentBase, true);
    }
    
    private void cmd_reloadConfig(String name) {
        reloadConfig();
        inPub = ba.getArenaName().startsWith("(Public");
        ba.sendSmartPrivateMessage(name, "PubMapModule settings have been reloaded from cfg file.");
    }
    
    private void cmd_getSets(String name) {
        long dt = (timeDelay * Tools.TimeInMillis.MINUTE - (System.currentTimeMillis() - lastChange));
        int min = (int)(dt / Tools.TimeInMillis.MINUTE);
        int sec = (int) (dt - min * Tools.TimeInMillis.MINUTE) / Tools.TimeInMillis.SECOND;
        ba.sendSmartPrivateMessage(name, "Current MapModule settings> PopTrigger:" + popTriggers.get(SMALL_BASE) + "," + popTriggers.get(LARGE_BASE) + " PopLeeway:" + popLeeway + " TimeDelay:" + timeDelay);
        ba.sendSmartPrivateMessage(name, "Current arena: " + ba.getArenaName() + " inPub: " + inPub + " base: " + getBase(currentBase) + " Pop:" + ba.getNumPlaying() + " Time: " + min + "min " + sec + "sec");

        inPub = ba.getArenaName().startsWith("(Public");
    }
    
    private void cmd_mapMod(String name) {
        enabled = !enabled;
        if (enabled)
            ba.sendSmartPrivateMessage(name, "The pub map population control module is ENABLED.");
        else
            ba.sendSmartPrivateMessage(name, "The pub map population control module is DISABLED.");
    }

    @Override
    public String[] getHelpMessage(String sender) {
        return new String[]{};
    }

    @Override
    public String[] getModHelpMessage(String sender) {
        return new String[]{};
    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        String[] msg = {
                "[PubMap]",
                "   !loadcfg          -- Reloads map module settings from the cfg file.",
                "   !getsets          -- Displays current map module settings.",
                "   !mapmod           -- Enables/Disables the pub map population control.",
                "   !setbase          -- Forces the bot to set for current base settings.",
        };
        return msg;
    }

    /**
     * Generates a random point within the specified region
     * 
     * @param region
     *            the region the point should be in
     * @return A point object, or null if the first 10000 points generated were not within the desired region.
     */
    private Point getRandomPoint(int region) {
        Point p = null;
        int count = 0;
        while (p == null) {
            int x = random.nextInt(590-430) + 430;
            int y = random.nextInt(340-250) + 250;

            p = new Point(x, y);
            if (!regions.checkRegion(x, y, region))
                p = null;
            count++;
            if (count > 100000) {
                if (random.nextBoolean())
                    p = new Point(478, 308);
                else
                    p = new Point(545, 308);
            }
        }
        return p;
    }
    
    private class BaseChange extends TimerTask {
        private int base;
        
        public BaseChange(int base) {
            this.base = base;
        }
        
        public void run() {
            lastChange = System.currentTimeMillis();
            currentBase = base;
            ba.setDoors(currentBase);
            switch (currentBase) {
                case SMALL_BASE:
                    ba.showObject(LEFT_SIDE_DOOR);
                    ba.showObject(RIGHT_SIDE_DOOR);
                    ba.showObject(SMALL_OBJON);
                    ba.hideObject(MED_OBJON);
                    ba.hideObject(LARGE_OBJON);
                    warpForSmall();
                    break;
                case MED_BASE:
                    ba.showObject(MED_OBJON);
                    ba.hideObject(SMALL_OBJON);
                    ba.hideObject(LARGE_OBJON);
                    ba.hideObject(LEFT_SIDE_DOOR);
                    ba.hideObject(RIGHT_SIDE_DOOR);
                    warpForMedium();
                    break;
                case LARGE_BASE:
                    ba.showObject(LARGE_OBJON);
                    ba.hideObject(SMALL_OBJON);
                    ba.hideObject(MED_OBJON);
                    ba.hideObject(LEFT_SIDE_DOOR);
                    ba.hideObject(RIGHT_SIDE_DOOR);
                    break;
            }
            baseChanger = null;
        }
    }

}
