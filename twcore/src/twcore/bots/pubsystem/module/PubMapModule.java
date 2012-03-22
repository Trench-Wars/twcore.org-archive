package twcore.bots.pubsystem.module;

import twcore.bots.pubsystem.PubContext;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.util.Tools;

public class PubMapModule extends AbstractModule {

    
    private static final int SMALL_OBJON = 1002;
    private static final int LARGE_OBJON = 1001;
    private static final int SMALL_BASE = 2;
    private static final int LARGE_BASE = 8;
    //private static final int ANTI_LEV   = 14;
    
    private int currentBase;    // current door setting
    
    private int popTrigger;     // cut off for number of players before door change
    private int popLeeway;      // number of players from trigger required for door change
    private int timeDelay;      // minimum amount of time between door changes
    private long lastChange;   
    
    private BotAction ba;
    
    public PubMapModule(BotAction botAction, PubContext context) {
        super(botAction, context, "PubMap");
        ba = botAction;
        currentBase = SMALL_BASE;
        lastChange = 0;
        setBase();
        reloadConfig();
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
        popTrigger = set.getInt("PopulationTrigger");
        popLeeway = set.getInt("PopulationLeeway");
        timeDelay = set.getInt("TimeDelay");
        enabled = set.getInt("pubmap_enabled") == 1;
        doPopCheck();
    }
    
    public void handleEvent(PlayerEntered event) {
        if (!enabled) return;
        if (event.getShipType() > 0)
            doPopCheck();
        if (currentBase == LARGE_BASE) {
            ba.showObjectForPlayer(event.getPlayerID(), LARGE_OBJON);
            ba.hideObjectForPlayer(event.getPlayerID(), SMALL_OBJON);
        } else {
            ba.showObjectForPlayer(event.getPlayerID(), SMALL_OBJON);
            ba.hideObjectForPlayer(event.getPlayerID(), LARGE_OBJON);
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
        if (now - lastChange < timeDelay * Tools.TimeInMillis.MINUTE)
            return;
        if (pop > popTrigger + popLeeway) {
            lastChange = now;
            currentBase = LARGE_BASE;
            setBase();
        } else if (pop < popTrigger - popLeeway) {
            lastChange = now;
            currentBase = SMALL_BASE;
            setBase();
        }
    }
    
    private void setBase() {
        ba.setDoors(currentBase);
        if (currentBase == SMALL_BASE) {
            ba.showObject(SMALL_OBJON);
            ba.hideObject(LARGE_OBJON);
        } else {
            ba.showObject(LARGE_OBJON);
            ba.hideObject(SMALL_OBJON);
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
        ba.sendSmartPrivateMessage(name, "Current MapModule settings> PopTrigger:" + popTrigger + " PopLeeway:" + popLeeway + " TimeDelay:" + timeDelay);
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
