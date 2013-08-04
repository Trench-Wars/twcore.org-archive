package twcore.bots.pubsystem;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.TimerTask;
import twcore.bots.pubsystem.module.*;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.events.SQLResultEvent;
import twcore.core.events.SubspaceEvent;
import twcore.core.util.Tools;

public class PubContext {

    private BotAction m_botAction;

    private boolean started = false;

    // True during the first 5 seconds
    // Reason: to avoid spamming the arena when the bot spawn
    private boolean hasJustStarted = true;

    private LinkedHashMap<String, AbstractModule> modules;

    // Modules
    private PubMapModule pubMap;
    private PubPlayerManagerModule playerManager;
    private PubMoneySystemModule moneySystem;
    private PubChallengeModule pubChallenge;
    private PubKillSessionModule pubKillSession;
    private PubStreakModule pubStreak;
    private PubUtilModule pubUtil;
    private PubHuntModule pubHunt;
    private PubSessionModule pubSession;
    private PubLotteryModule pubLottery;
    private GaugeModule gaugeModule;
    private BountyModule bountyModule;

    // Game module
    private GameFlagTimeModule gameFlagTime;

    public PubContext(BotAction botAction) {
        this.m_botAction = botAction;

        this.modules = new LinkedHashMap<String, AbstractModule>();

        // Instanciate (order matter)

        long start = System.currentTimeMillis();

        // Order matter (!help)

        getGameFlagTime();
        getPlayerManager();
        getMoneySystem();
        getPubChallenge();
        //getPubHunt();
        getPubLottery();
        getPubStreak();
        getPubKillSession();
        getPubSession();
        getPubUtil();
        getPubMap();
        getGauge();
        //getBounty();

        int seconds = (int) (System.currentTimeMillis() - start) / 1000;
        Tools.printLog("Modules(" + modules.size() + ") for pubsystem loaded in " + seconds + " seconds.");
    }

    public void start() {
        this.started = true;
        for (AbstractModule module : modules.values()) {
            module.start();
        }
        TimerTask timer = new TimerTask() {
            public void run() {
                hasJustStarted = false;
            }
        };
        m_botAction.scheduleTask(timer, 5 * Tools.TimeInMillis.SECOND);
    }

    public void stop() {
        this.started = false;
        for (AbstractModule module : modules.values()) {
            module.stop();
        }
    }

    public void reloadConfig() {
        m_botAction.getBotSettings().reloadFile();
        for (AbstractModule module : modules.values()) {
            try {
                module.reloadConfig();
            } catch (Exception e) {
                displayException(e);
            }
        }
    }

    public boolean hasJustStarted() {
        return hasJustStarted;
    }

    public boolean isStarted() {
        return started;
    }

    public Collection<AbstractModule> getModules() {
        return modules.values();
    }

    // TODO: The code below must be modularized. We need to read
    // from CFG modules to be loaded, rather than hard-coding.
    // Anything that can vary externally *must* be put in a
    // CFG, is basically the rule.

    public GameFlagTimeModule getGameFlagTime() {
        if (gameFlagTime == null) {
            gameFlagTime = new GameFlagTimeModule(m_botAction, this);
            modules.put("flagtime", gameFlagTime);
        }
        return gameFlagTime;
    }

    public PubMapModule getPubMap() {
        if (pubMap == null) {
            pubMap = new PubMapModule(m_botAction, this);
            modules.put("pubmap", pubMap);
        }
        return pubMap;
    }

    public PubPlayerManagerModule getPlayerManager() {
        if (playerManager == null) {
            playerManager = new PubPlayerManagerModule(m_botAction, this);
            modules.put("playermanager", playerManager);
        }
        return playerManager;
    }

    public GaugeModule getGauge() {
        if (gaugeModule == null) {
            gaugeModule = new GaugeModule(m_botAction, this);
            modules.put("gaugemodule", gaugeModule);
        }
        return gaugeModule;
    }
    
    public BountyModule getBounty() {
        if (bountyModule == null) {
            bountyModule = new BountyModule(m_botAction, this);
            modules.put("bounty", bountyModule);
        }
        return bountyModule;
    }
    
    public PubMoneySystemModule getMoneySystem() {
        if (moneySystem == null) {
            moneySystem = new PubMoneySystemModule(m_botAction, this);
            modules.put("moneysystem", moneySystem);
        }
        return moneySystem;
    }

    public PubHuntModule getPubHunt() {
        if (pubHunt == null) {
            pubHunt = new PubHuntModule(m_botAction, this);
            modules.put("hunt", pubHunt);
        }
        return pubHunt;
    }

    public PubKillSessionModule getPubKillSession() {
        if (pubKillSession == null) {
            pubKillSession = new PubKillSessionModule(m_botAction, this);
            modules.put("killothon", pubKillSession);
        }
        return pubKillSession;
    }

    public PubStreakModule getPubStreak() {
        if (pubStreak == null) {
            pubStreak = new PubStreakModule(m_botAction, this);
            modules.put("streak", pubStreak);
        }
        return pubStreak;
    }

    public PubUtilModule getPubUtil() {
        if (pubUtil == null) {
            pubUtil = new PubUtilModule(m_botAction, this);
            modules.put("utility", pubUtil);
        }
        return pubUtil;
    }

    public PubChallengeModule getPubChallenge() {
        if (pubChallenge == null) {
            pubChallenge = new PubChallengeModule(m_botAction, this);
            modules.put("challenge", pubChallenge);
        }
        return pubChallenge;
    }

    public PubSessionModule getPubSession() {
        if (pubSession == null) {
            pubSession = new PubSessionModule(m_botAction, this);
            modules.put("session", pubSession);
        }
        return pubSession;
    }

    public PubLotteryModule getPubLottery() {
        if (pubLottery == null) {
            pubLottery = new PubLotteryModule(m_botAction, this);
            modules.put("lottery", pubLottery);
        }
        return pubLottery;
    }

    public void handleEvent(SubspaceEvent event) {

        Iterator<AbstractModule> iterator = modules.values().iterator();
        AbstractModule module;

        while (iterator.hasNext()) {
            module = (AbstractModule) iterator.next();
            if (!module.isEnabled())
                continue;
            try {
                module.handleEvent(event);
            } catch (Exception e) {
                displayException(e);
            }
        }

    }

    public void handleCommand(String sender, String command) {

        Iterator<AbstractModule> iterator = modules.values().iterator();
        AbstractModule module;

        while (iterator.hasNext()) {
            module = (AbstractModule) iterator.next();
            if (!module.isEnabled())
                continue;
            try {
                module.handleCommand(sender, command);
            } catch (Exception e) {
                displayException(e);
            }
        }

    }

    public void handleModCommand(String sender, String command) {

        Iterator<AbstractModule> iterator = modules.values().iterator();
        AbstractModule module;

        if (command.startsWith("!enable_") || command.startsWith("!disable_")) {
            boolean enable = command.startsWith("!enable") ? true : false;
            String moduleName = command.substring(command.indexOf("_") + 1).toLowerCase();
            if (modules.containsKey(moduleName)) {
                if (enable) {
                    modules.get(moduleName).enable();
                    m_botAction.sendSmartPrivateMessage(sender, "Module '" + moduleName + "' enabled.");
                } else {
                    modules.get(moduleName).disable();
                    m_botAction.sendSmartPrivateMessage(sender, "Module '" + moduleName + "' disabled.");
                }
                if (moduleName.equalsIgnoreCase("pubmap")) {
                    BotSettings sets = m_botAction.getBotSettings();
                    if (enable)
                        sets.put("pubmap_enabled", "1");
                    else
                        sets.put("pubmap_enabled", "0");
                    sets.save();
                }
            } else {
                m_botAction.sendSmartPrivateMessage(sender, "Module '" + moduleName + "' not found.");
                String moduleNames = "";
                for (String name : modules.keySet()) {
                    moduleNames += ", " + name;
                }
                m_botAction.sendSmartPrivateMessage(sender, "Avalaible: " + moduleNames.substring(1));
            }
        }

        while (iterator.hasNext()) {
            module = (AbstractModule) iterator.next();
            if (!module.isEnabled())
                continue;
            try {
                module.handleModCommand(sender, command);
            } catch (Exception e) {
                displayException(e);
            }
        }

    }

    public void handleSmodCommand(String sender, String command) {

        Iterator<AbstractModule> iterator = modules.values().iterator();
        AbstractModule module;

        while (iterator.hasNext()) {
            module = (AbstractModule) iterator.next();
            if (!module.isEnabled())
                continue;
            try {
                module.handleSmodCommand(sender, command);
            } catch (Exception e) {
                displayException(e);
            }
        }

    }

    public void handleEvent(SQLResultEvent event) {

        Iterator<AbstractModule> iterator = modules.values().iterator();
        AbstractModule module;

        while (iterator.hasNext()) {
            module = (AbstractModule) iterator.next();
            if (!module.isEnabled())
                continue;
            try {
                module.handleEvent(event);
            } catch (Exception e) {
                displayException(e);
            }
        }

    }

    public void handleDisconnect() {

        Iterator<AbstractModule> iterator = modules.values().iterator();
        AbstractModule module;

        while (iterator.hasNext()) {
            module = (AbstractModule) iterator.next();
            try {
                module.handleDisconnect();
            } catch (Exception e) {
                displayException(e);
            }
        }

    }

    private void displayException(Exception e) {
        if (e.getStackTrace().length > 0) {
            String method = e.getStackTrace()[0].getMethodName();
            String className = e.getStackTrace()[0].getClassName();
            int line = e.getStackTrace()[0].getLineNumber();
            m_botAction.sendChatMessage(1, e.getClass().getSimpleName() + " caught on " + className + ", " + method + " at line " + line);
        }
        Tools.printStackTrace(e);
    }

}
