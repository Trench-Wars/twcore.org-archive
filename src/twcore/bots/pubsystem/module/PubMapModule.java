package twcore.bots.pubsystem.module;

import java.util.HashMap;

import twcore.bots.pubsystem.PubContext;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.util.Tools;

public class PubMapModule extends AbstractModule {

    
    private static final int SMALL_OBJON = 1001;
    private static final int MED_OBJON = 1002;
    private static final int LARGE_OBJON = 1003;
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
    
    public PubMapModule(BotAction botAction, PubContext context) {
        super(botAction, context, "PubMap");
        ba = botAction;
        reloadConfig();
        currentBase = MED_BASE;
        lastChange = 0;
        setBase();
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
        doPopCheck();
    }
    
    public void handleEvent(PlayerEntered event) {
        if (!enabled) return;
        if (event.getShipType() > 0)
            doPopCheck();
        switch (currentBase) {
            case SMALL_BASE: 
                ba.showObjectForPlayer(event.getPlayerID(), SMALL_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), MED_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), LARGE_OBJON);
                break;
            case MED_BASE:
                ba.showObjectForPlayer(event.getPlayerID(), MED_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), SMALL_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), LARGE_OBJON);
                break;
            case LARGE_BASE: 
                ba.showObjectForPlayer(event.getPlayerID(), LARGE_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), SMALL_OBJON);
                ba.hideObjectForPlayer(event.getPlayerID(), MED_OBJON);
                break;
        }
    }
    
    public void handleEvent(PlayerLeft event) {
        if (enabled)
            doPopCheck();
    }
    
    public void handleEvent(FrequencyShipChange event) {
        if (enabled)
            doPopCheck();
    }
    
    private void doPopCheck() {
        if (!enabled) return;
        int pop = ba.getPlayingPlayers().size();
        long now = System.currentTimeMillis();
        if (lastChange != 0 && now - lastChange < timeDelay * Tools.TimeInMillis.MINUTE)
            return;
        if (pop > popTriggers.get(LARGE_BASE) + popLeeway) {
            lastChange = now;
            currentBase = LARGE_BASE;
            setBase();
        } else if (pop < popTriggers.get(SMALL_BASE) - popLeeway) {
            lastChange = now;
            currentBase = SMALL_BASE;
            setBase();
        } else if (pop > popTriggers.get(SMALL_BASE) + popLeeway && pop < popTriggers.get(LARGE_BASE) - (popLeeway*2)) {
            lastChange = now;
            currentBase = MED_BASE;
            setBase();
        }
    }
    
    private void setBase() {
        ba.setDoors(currentBase);
        switch (currentBase) {
            case SMALL_BASE:
                ba.showObject(SMALL_OBJON);
                ba.hideObject(MED_OBJON);
                ba.hideObject(LARGE_OBJON);
                break;
            case MED_BASE:
                ba.showObject(MED_OBJON);
                ba.hideObject(SMALL_OBJON);
                ba.hideObject(LARGE_OBJON);
                break;
            case LARGE_BASE:
                ba.showObject(LARGE_OBJON);
                ba.hideObject(SMALL_OBJON);
                ba.hideObject(MED_OBJON);
                break;
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
            setBase();
    }
    
    private void cmd_reloadConfig(String name) {
        reloadConfig();
        ba.sendSmartPrivateMessage(name, "PubMapModule settings have been reloaded from cfg file.");
    }
    
    private void cmd_getSets(String name) {
        String str = "";
        if (currentBase == SMALL_BASE)
            str = "SMALL_BASE(" + SMALL_BASE + ")";
        else if (currentBase == MED_BASE)
            str = "MED_BASE(" + MED_BASE + ")";
        else if (currentBase == LARGE_BASE)
            str = "LARGE_BASE(" + LARGE_BASE + ")";
        long dt = (timeDelay * Tools.TimeInMillis.MINUTE - (System.currentTimeMillis() - lastChange));
        int min = (int)(dt / Tools.TimeInMillis.MINUTE);
        int sec = (int) (dt - min * Tools.TimeInMillis.MINUTE) / Tools.TimeInMillis.SECOND;
        ba.sendSmartPrivateMessage(name, "Current MapModule settings> PopTrigger:" + popTriggers.get(SMALL_BASE) + "," + popTriggers.get(LARGE_BASE) + " PopLeeway:" + popLeeway + " TimeDelay:" + timeDelay);
        ba.sendSmartPrivateMessage(name, "Current base: " + str + " Pop:" + ba.getNumPlaying() + " Time: " + min + "min " + sec + "sec");
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
                " [PubMap]",
                " - !loadcfg          -- Reloads map module settings from the cfg file.",
                " - !getsets          -- Displays current map module settings.",
                " - !mapmod           -- Enables/Disables the pub map population control.",
        };
        return msg;
    }

}
