package twcore.bots.pubsystem.module;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Stack;
import java.util.TimerTask;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.events.PlayerLeft;
import twcore.core.events.TurretEvent;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubUtilModule extends AbstractModule {

    // LOCATION
    public static enum Location {

        FLAGROOM,
        MID,
        LOWER,
        ROOF,
        SPACE,
        UNKNOWN,
        SPAWN,
        SAFE
    }
    private HashMap<String, Location> locations;

    // TILESET
    public static enum Tileset {

        DEFAULT,
        BOKI,
        MONOLITH,
        BLUETECH
    };
    public HashMap<Tileset, Integer> tilesetObjects;
    public Tileset defaultTileSet = Tileset.BLUETECH;
    public boolean tilesetEnabled = false;
    private String currentInfoName = "";
    private int currentPing = -1;

    // DOORS
    private static enum DoorMode {

        CLOSED, OPENED, IN_OPERATION, UNKNOW
    };
    private DoorMode doorStatus = DoorMode.UNKNOW;
    private int doorModeDefault;
    private int doorModeThreshold;
    private int doorModeThresholdSetting;
    private boolean doorModeManual = false;
    // PRIV FREQ
    private boolean privFreqEnabled = true;
    // Lev can attach on public freq
    private boolean levAttachEnabled = true;
    private long uptime = 0;
    private HashMap<String, Stack<String[]>> tutorials = new HashMap<String, Stack<String[]>>();
    private HashMap<String, ObjonTimer> objonTimers = new HashMap<String, ObjonTimer>();

    public PubUtilModule(BotAction botAction, PubContext context) {
        super(botAction, context, "Utility");
        this.uptime = System.currentTimeMillis();
        reloadConfig();
    }

    private String coordToString(int x, int y) {
        return x + ":" + y;
    }

    public Location getLocation(int x, int y) {
        Location location = locations.get(coordToString(x, y));
        if (location != null) {
            return location;
        } else {
            return Location.SPACE;
        }
    }

    public boolean isInside(int x, int y, Location location) {
        if (getLocation(x, y).equals(location)) {
            return true;
        }
        return false;
    }

    public boolean isLevAttachEnabled() {
        return levAttachEnabled;
    }

    public void handleEvent(Message event) {
        String pingString = "";
        String message = event.getMessage();
        if (event.getMessageType() == Message.ARENA_MESSAGE) {
            if (message.contains("TypedName:")) {
                currentInfoName = message.substring(message.indexOf("TypedName:") + 10);
                currentInfoName = currentInfoName.substring(0, currentInfoName.indexOf("Demo:")).trim();
            }
            if (message.startsWith("Ping:")) {
                pingString = message.substring(message.indexOf(':') + 1, message.indexOf('m'));
                currentPing = Integer.valueOf(pingString);

                m_botAction.sendUnfilteredPrivateMessage(currentInfoName, "*objon 2010");
                m_botAction.sendPrivateMessage(currentInfoName, "Welcome to Trench Wars! If you'd like to see a brief tutorial, please type !tutorial");
                if (currentPing == 0) {
                    ObjonTimer lagCheck = new ObjonTimer(currentInfoName);
                    m_botAction.scheduleTask(lagCheck, 45000, 15000);
                    objonTimers.put(currentInfoName, lagCheck);
                }
            }
            if (objonTimers.containsKey(currentInfoName) && message.startsWith("PING Current")) {
                int pingCheck = Integer.valueOf(message.substring(message.indexOf(':') + 1, message.indexOf(" m")));
                if (pingCheck > 0) {
                    m_botAction.sendUnfilteredPrivateMessage(currentInfoName, "*objon 2010");
                    m_botAction.cancelTask(objonTimers.get(currentInfoName));
                }
            }
        }
    }

    public void handleNewPlayer(String message) {
        currentInfoName = m_botAction.getFuzzyPlayerName(message.substring(message.indexOf(":") + 2));
        if (currentInfoName != null) {
            m_botAction.sendUnfilteredPrivateMessage(currentInfoName, "*info");
        } else {
            currentInfoName = "";
        }
    }

    public void handleEvent(TurretEvent event) {

        Player p1 = m_botAction.getPlayer(event.getAttacheeID());
        Player p2 = m_botAction.getPlayer(event.getAttacherID());

        if (p1 != null) {
            // Attacher stats
            PubPlayer pubPlayer = context.getPlayerManager().getPlayer(p1.getPlayerName());
            if (pubPlayer != null) {
                pubPlayer.handleAttach();
            }
        }

        if (p2 != null) {
            // Attachee check up
            if (!levAttachEnabled && p2.getShipType() == Tools.Ship.LEVIATHAN && event.isAttaching()) {

                // Public freq?
                if (p2.getFrequency() == 0 || p2.getFrequency() == 1) {
                    m_botAction.specWithoutLock(p2.getPlayerName());
                    m_botAction.setShip(p2.getPlayerName(), Tools.Ship.LEVIATHAN);
                    m_botAction.setFreq(p2.getPlayerName(), p2.getFrequency());
                    m_botAction.sendSmartPrivateMessage(p2.getPlayerName(), "You cannot attach to a Terrier on a public frequency.");
                }
            }
        }

    }

    public void handleEvent(PlayerLeft event) {
        tutorials.remove(m_botAction.getPlayerName(event.getPlayerID()));
        //checkForDoors();
    }

    private void checkForDoors() {

        // Did someone manually changed the doors? if yes.. do nothing
        if (doorModeManual) {
            return;
        }

        if (m_botAction.getNumPlayers() >= doorModeThreshold && !doorStatus.equals(DoorMode.IN_OPERATION)) {
            m_botAction.setDoors(doorModeThresholdSetting);
            doorStatus = DoorMode.IN_OPERATION;
        } else if (!doorStatus.equals(DoorMode.CLOSED)) {
            m_botAction.setDoors(doorModeDefault);
            doorStatus = DoorMode.CLOSED;
        }
    }

    public void setTileset(Tileset tileset, final String playerName, boolean instant) {
        final Tileset playerTileset;

        if (!tilesetEnabled) {
            return;
        }

        if (tileset == Tileset.DEFAULT) {
            playerTileset = defaultTileSet;
        } else {
            playerTileset = tileset;
        }

        TimerTask task = new TimerTask() {

            public void run() {
                PubPlayer pubPlayer = context.getPlayerManager().getPlayer(playerName);
                if (pubPlayer != null) {
                    if (Tileset.BLUETECH == playerTileset) {
                        for (int object : tilesetObjects.values()) {
                            m_botAction.sendUnfilteredPrivateMessage(playerName, "*objoff " + object);
                        }
                    } else {
                        for (int object : tilesetObjects.values()) {
                            m_botAction.sendUnfilteredPrivateMessage(playerName, "*objoff " + object);
                        }
                        m_botAction.sendUnfilteredPrivateMessage(playerName, "*objon " + tilesetObjects.get(playerTileset));
                    }
                    pubPlayer.setTileset(playerTileset);
                }
            }
        };

        if (instant) {
            task.run();
        } else {
            try {
                m_botAction.scheduleTask(task, 1 * Tools.TimeInMillis.SECOND);
            } catch (IllegalStateException e) {
                Tools.printLog("IllegalStateException when setting tileset of " + playerName);
            }
        }

    }

    public void setArenaTileset(Tileset tileset) {
        if (tileset == Tileset.DEFAULT) {
            tileset = defaultTileSet;
        }

        if (!tilesetEnabled) {
            return;
        }

        if (Tileset.BLUETECH == tileset) {
            for (int object : tilesetObjects.values()) {
                m_botAction.sendUnfilteredPublicMessage("*objoff " + object);
            }
        } else {
            for (int object : tilesetObjects.values()) {
                m_botAction.sendUnfilteredPublicMessage("*objoff " + object);
            }
            m_botAction.sendUnfilteredPublicMessage("*objon " + tilesetObjects.get(tileset));
        }
    }

    /**
     * Change the current tileset for a player
     */
    private void doSetTileCmd(String sender, String tileName) {

        if (!tilesetEnabled) {
            m_botAction.sendSmartPrivateMessage(sender, "This command is disabled. It may be due to a special event/day/map.");
            return;
        }

        try {
            Tileset tileset = Tileset.valueOf(tileName.toUpperCase());
            setTileset(tileset, sender, true);
            m_botAction.sendSmartPrivateMessage(sender, "This setting has been saved in your account. Tileset: " + tileName);
        } catch (IllegalArgumentException e) {
            m_botAction.sendSmartPrivateMessage(sender, "The tileset '" + tileName + "' does not exists.");
        }

    }

    private void doOpenDoorCmd(String sender) {
        doorModeManual = true;
        m_botAction.setDoors(0);
        m_botAction.sendSmartPrivateMessage(sender, "Doors opened.");
        doorStatus = DoorMode.OPENED;
    }

    private void doCloseDoorCmd(String sender) {
        doorModeManual = true;
        m_botAction.setDoors(255);
        m_botAction.sendSmartPrivateMessage(sender, "Doors closed.");
        doorStatus = DoorMode.CLOSED;
    }

    private void doToggleDoorCmd(String sender) {
        doorModeManual = true;
        m_botAction.setDoors(-2);
        m_botAction.sendSmartPrivateMessage(sender, "Doors will be toggl.");
        doorStatus = DoorMode.IN_OPERATION;
    }

    private void doAutoDoorCmd(String sender) {
        doorModeManual = false;
        checkForDoors();
        m_botAction.sendSmartPrivateMessage(sender, "Doors will be locked or in operation if the number of players is higher than " + doorModeThreshold + ".");
    }

    /**
     * Moves the bot from one arena to another.  The bot must not be
     * started for it to move.
     *
     * @param sender is the person issuing the command.
     * @param argString is the new arena to go to.
     * @throws RuntimeException if the bot is currently running.
     * @throws IllegalArgumentException if the bot is already in that arena.
     */
    private void doGoCmd(String sender, String argString) {
        String currentArena = m_botAction.getArenaName();

        if (context.isStarted()) {
            m_botAction.sendPrivateMessage(sender, "Bot is currently running pub settings in " + currentArena + ".  Please !stop before trying to move.");
        }
        if (currentArena.equalsIgnoreCase(argString)) {
            m_botAction.sendPrivateMessage(sender, "Bot is already in that arena.");
        }

        m_botAction.changeArena(argString);
        m_botAction.sendSmartPrivateMessage(sender, "Bot going to: " + argString);
    }

    /**
     * Toggles if private frequencies are allowed or not.
     *
     * @param sender is the sender of the command.
     */
    private void doPrivFreqsCmd(String sender) {
        privFreqEnabled = !privFreqEnabled;

        if (privFreqEnabled) {
            context.getPlayerManager().fixFreqs();
            if (!context.hasJustStarted()) {
                m_botAction.sendArenaMessage("[SETTING] Private Frequencies enabled.", 2);
            }
            m_botAction.sendSmartPrivateMessage(sender, "Private frequencies succesfully enabled.");
        } else {
            if (!context.hasJustStarted()) {
                m_botAction.sendArenaMessage("[SETTING] Private Frequencies disabled.", 2);
            }
            m_botAction.sendSmartPrivateMessage(sender, "Private frequencies succesfully disabled.");
        }
    }

    private void doLevAttachCmd(String sender) {
        levAttachEnabled = !levAttachEnabled;

        if (levAttachEnabled) {
            context.getPlayerManager().fixFreqs();
            if (!context.hasJustStarted()) {
                m_botAction.sendArenaMessage("[SETTING] Leviathan attach capability enabled on public frequencies.", 2);
            }
            m_botAction.sendSmartPrivateMessage(sender, "Leviathan can now attach on a ter in public freq.");
        } else {
            if (!context.hasJustStarted()) {
                m_botAction.sendArenaMessage("[SETTING] Leviathan attach capability disabled on public frequencies.", 2);
            }
            m_botAction.sendSmartPrivateMessage(sender, "Leviathan cannot attach anymore on a ter in public freq.");
        }
    }

    private void doBotInfoCmd(String sender) {
        long diff = System.currentTimeMillis() - uptime;
        int minute = (int) (diff / (1000 * 60));

        m_botAction.sendSmartPrivateMessage(sender, "Uptime: " + minute + " minutes");

        int x = m_botAction.getShip().getX() / 16;
        int y = m_botAction.getShip().getY() / 16;

        m_botAction.sendSmartPrivateMessage(sender, "Position: " + x + ", " + y);

    }

    private void doUptimeCmd(String sender) {
        long diff = System.currentTimeMillis() - uptime;
        int minute = (int) (diff / (1000 * 60));

        m_botAction.sendSmartPrivateMessage(sender, "Uptime: " + minute + " minutes");
    }

    /**
     * Logs the bot off if not enabled.
     *
     * @param sender is the person issuing the command.
     * @throws RuntimeException if the bot is running pure pub settings.
     */
    private void doDieCmd(String sender) {
        m_botAction.sendSmartPrivateMessage(sender, "Bot logging off.");
        m_botAction.setObjects();
        m_botAction.scheduleTask(new DieTask(), 300);
    }

    /**
     * Shows last seen location of a given individual.
     */
    private void doWhereIsCmd(String sender, String argString, boolean isStaff) {

        Player p = m_botAction.getPlayer(sender);

        if (p == null) {
            m_botAction.sendSmartPrivateMessage(sender, "Can't find you. Please report this to staff.");
            return;
        }

        if (p.getShipType() == 0 && !isStaff) {
            m_botAction.sendSmartPrivateMessage(sender, "You must be in a ship for this command to work.");
            return;
        }

        Player p2 = m_botAction.getFuzzyPlayer(argString);
        if (p2 == null) {
            m_botAction.sendSmartPrivateMessage(sender, "Player '" + argString + "' not found.");
            return;
        }

        if (!p2.isPlaying()) {
            m_botAction.sendSmartPrivateMessage(sender, p2.getPlayerName() + " last seen: In Spec");
            return;
        }
        if (p.getFrequency() != p2.getFrequency() && !isStaff) {
            m_botAction.sendSmartPrivateMessage(sender, p2.getPlayerName() + " is not on your team.");
            return;
        }

        m_botAction.sendSmartPrivateMessage(sender, p2.getPlayerName() + " last seen: " + getPlayerLocation(p2.getXTileLocation(), p2.getYTileLocation()));
    }

    public String getLocationName(Location location) {

        if (Location.UNKNOWN.equals(location)) {
            return "Unknown";
        }
        if (Location.FLAGROOM.equals(location)) {
            return "Flagroom";
        }
        if (Location.MID.equals(location)) {
            return "Mid Base";
        }
        if (Location.LOWER.equals(location)) {
            return "Lower Base";
        }
        if (Location.ROOF.equals(location)) {
            return "Roof";
        }
        if (Location.SPAWN.equals(location)) {
            return "Spawn";
        }
        if (Location.SAFE.equals(location)) {
            return "Safe";
        }
        if (Location.SPACE.equals(location)) {
            return "Space";
        }

        return "Unknown";
    }

    public String getPlayerLocation(int x, int y) {

        String exact = "";

        String position = "Outside base";

        Location location = getLocation(x, y);

        if (Location.UNKNOWN.equals(location)) {
            return "Not yet spotted";
        }
        if (Location.FLAGROOM.equals(location)) {
            return "in Flagroom";
        }
        if (Location.MID.equals(location)) {
            return "in Mid Base";
        }
        if (Location.LOWER.equals(location)) {
            return "in Lower Base";
        }
        if (Location.ROOF.equals(location)) {
            return "on Roof";
        }
        if (Location.SPAWN.equals(location)) {
            return "in Spawn";
        }
        if (Location.SAFE.equals(location)) {
            return "in Safe";
        }
        if (Location.SPACE.equals(location)) {
            return "in Space";
        }

        return "Not yet spotted";
    }

    /**
     * This class is used for checking the lag status of newbs in order
     * to determine whether or not they will be able to see an objon
     */
    class ObjonTimer extends TimerTask {

        String name;

        public ObjonTimer(String p) {
            name = p;
        }

        public void run() {
            if (m_botAction.getFuzzyPlayerName(name) != null) {
                currentInfoName = name;
                m_botAction.sendUnfilteredPrivateMessage(name, "*lag");
            }
        }
    }

    public void doTutorial(String player) {
        if (!tutorials.containsKey(player)) {
            if (m_botAction.getPlayer(player).getShipType() == 0) {
                m_botAction.setShip(player, 1);
            }
            m_botAction.sendUnfilteredPrivateMessage(player, "*objon 2011");
            m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2010");
            m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2020");
            Stack<String[]> objons = new Stack<String[]>();
            objons.push(new String[]{"*objoff 2017", "*objon 2018", "*objoff 2019"});
            objons.push(new String[]{"*objoff 2016", "*objon 2017"});
            objons.push(new String[]{"*objoff 2015", "*objon 2016"});
            objons.push(new String[]{"*objoff 2014", "*objon 2015"});
            objons.push(new String[]{"*objoff 2013", "*objon 2014"});
            objons.push(new String[]{"*objoff 2012", "*objon 2013"});
            objons.push(new String[]{"*objoff 2011", "*objon 2012", "*objon 2019"});
            tutorials.put(player, objons);
        } else {
            m_botAction.sendPrivateMessage(player, "Use !next");
        }
    }

    public void doNext(String player, boolean pub) {
        if (tutorials.containsKey(player)) {
            Stack<String[]> objects = tutorials.get(player);
            String[] objs = objects.pop();
            m_botAction.sendUnfilteredPrivateMessage(player, objs[0]);
            m_botAction.sendUnfilteredPrivateMessage(player, objs[1]);
            if (objs.length > 2) {
                m_botAction.sendUnfilteredPrivateMessage(player, objs[2]);
            }
            if (objs.length > 3) {
                m_botAction.sendPrivateMessage(player, objs[3]);
            }
            if (pub && !objs[0].equals("*objoff 2017")) {
                m_botAction.sendPrivateMessage(player, "" + player + ", to continue the tutorial, please type ::!next");
            }
            tutorials.put(player, objects);
            if (objects.empty()) {
                tutorials.remove(player);
            }
        } else {
            m_botAction.sendPrivateMessage(player, "You must first type !tutorial");
        }
    }

    public void doEnd(String player) {
        if (tutorials.containsKey(player)) {
            tutorials.remove(player);
        }
        if (objonTimers.containsKey(player)) {
            ObjonTimer timer = objonTimers.remove(player);
            m_botAction.cancelTask(timer);
        }
        m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2010");
        m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2011");
        m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2012");
        m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2013");
        m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2014");
        m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2015");
        m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2016");
        m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2017");
        m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2018");
        m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2019");
        m_botAction.sendUnfilteredPrivateMessage(player, "*objoff 2020");
    }

    public void doQuickHelp(String player) {
        doEnd(player);
        m_botAction.sendUnfilteredPrivateMessage(player, "*objon 2020");
    }

    @Override
    public void handleCommand(String sender, String command) {

        if (command.startsWith("!settile ") || command.startsWith("!tileset ")) {
            //doSetTileCmd(sender, command.substring(9));
        } else if (command.startsWith(
                "!whereis ")) {
            doWhereIsCmd(sender, command.substring(9), m_botAction.getOperatorList().isBot(sender));
        } else if (command.equals("!restrictions")) {
            context.getPlayerManager().doRestrictionsCmd(sender);
        } else if (command.equals("!tutorial")) {
            doTutorial(sender);
        } else if (command.equals("!next")) {
            doNext(sender, false);
        } else if (command.equals("!end")) {
            doEnd(sender);
        } else if (command.equals("!quickhelp")) {
            doQuickHelp(sender);
        }
    }

    @Override
    public void handleModCommand(String sender, String command) {
        boolean dev = m_botAction.getOperatorList().isDeveloper(sender);
        if (command.startsWith("!dooropen") && dev) {
            doOpenDoorCmd(sender);
        } else if (command.startsWith("!doorclose") && dev) {
            doCloseDoorCmd(sender);
        } else if (command.startsWith("!doortoggle") && dev) {
            doToggleDoorCmd(sender);
        } else if (command.startsWith("!doorauto") && dev) {
            doAutoDoorCmd(sender);
        } else if (command.startsWith("!go ") && dev) {
            doGoCmd(sender, command.substring(4));
        } else if (command.equals("!stop") && dev) {
            context.stop();
        } else if (command.startsWith("!newplayer ")) {
            doModNewplayer(sender, command.substring(command.indexOf(" ") + 1));
        } else if (command.startsWith("!next ")) {
            doModNext(sender, command.substring(command.indexOf(" ") + 1));
        } else if (command.startsWith("!end ")) {
            doModEnd(sender, command.substring(command.indexOf(" ") + 1));
        } else if (command.equals("!privfreqs") && dev) {
            doPrivFreqsCmd(sender);
        } else if (command.equals("!levattach") && dev) {
            doLevAttachCmd(sender);
        } else if (command.equals("!uptime")) {
            doUptimeCmd(sender);
        } else if (command.equals("!botinfo") && dev) {
            doBotInfoCmd(sender);
        } else if (command.startsWith("!reloadconfig") && dev) {
            m_botAction.sendSmartPrivateMessage(sender, "Please wait..");
            context.reloadConfig();
            m_botAction.sendSmartPrivateMessage(sender, "Done.");
        } else if (command.startsWith("!set ") && dev) {
            context.getPlayerManager().doSetCmd(sender, command.substring(5));
        } else if (command.equals("!die") && dev) {
            doDieCmd(sender);
        }

    }

    public boolean isPrivateFrequencyEnabled() {
        return privFreqEnabled;
    }

    public void doModNewplayer(String mod, String name) {
        name = m_botAction.getFuzzyPlayerName(name);
        if (name != null) {
            m_botAction.sendUnfilteredPrivateMessage(name, "*objon 2025");
            m_botAction.sendSmartPrivateMessage(mod, "New player objon sent to: " + name);
        } else {
            m_botAction.sendSmartPrivateMessage(mod, "Player not found!");
        }
    }

    public void doModNext(String mod, String name) {
        name = m_botAction.getFuzzyPlayerName(name);
        if (name != null) {
            m_botAction.sendUnfilteredPrivateMessage(name, "*objoff 2025");
            m_botAction.sendUnfilteredPrivateMessage(name, "*objon 2026");
            m_botAction.sendSmartPrivateMessage(mod, "Next objon sent to: " + name);
        } else {
            m_botAction.sendSmartPrivateMessage(mod, "Player not found!");
        }
    }

    public void doModEnd(String mod, String name) {
        name = m_botAction.getFuzzyPlayerName(name);
        if (name != null) {
            m_botAction.sendUnfilteredPrivateMessage(name, "*objoff 2025");
            m_botAction.sendUnfilteredPrivateMessage(name, "*objoff 2026");
            m_botAction.sendSmartPrivateMessage(mod, "All objons removed for: " + name);
        } else {
            m_botAction.sendSmartPrivateMessage(mod, "Player not found!");
        }

    }

    @Override
    public String[] getHelpMessage(String sender) {
        return new String[]{
                    pubsystem.getHelpLine("!whereis <name>   -- Shows last seen location of <name> (if on your team)."),
                    pubsystem.getHelpLine("!restrictions     -- Lists all current ship restrictions."),
                    pubsystem.getHelpLine("!settile <name>   -- Change the current tileset."),
                    pubsystem.getHelpLine("                     Choice: bluetech (default), boki, monolith.")
                };
    }

    @Override
    public String[] getModHelpMessage(String sender) {
        boolean dev = m_botAction.getOperatorList().isDeveloper(sender);
        if (dev) {
            String[] string = {
                pubsystem.getHelpLine("!privfreqs        -- Toggles private frequencies & check for imbalances."),
                pubsystem.getHelpLine("!dooropen         -- Open doors."),
                pubsystem.getHelpLine("!doorclose        -- Close doors."),
                pubsystem.getHelpLine("!doortoggle       -- In operation doors."),
                pubsystem.getHelpLine("!doorauto         -- Auto mode (close if # of players below " + doorModeThreshold + "."),
                pubsystem.getHelpLine("!levattach        -- Toggles lev attach capability on public frequencies."),
                pubsystem.getHelpLine("!set <ship> <#>   -- Sets <ship> to restriction <#>."),
                pubsystem.getHelpLine("                     0=disabled; 1=any amount; other=weighted:"),
                pubsystem.getHelpLine("                     2 = 1/2 of freq can be this ship, 5 = 1/5, ..."),
                pubsystem.getHelpLine("!go <arena>       -- Moves the bot to <arena>."),
                pubsystem.getHelpLine("!reloadconfig     -- Reload the configuration (needed if .cfg has changed)."),
                pubsystem.getHelpLine("!uptime           -- Uptime of the bot in minutes."),
                pubsystem.getHelpLine("!stop             -- Stop the bot (needed when !go)."),
                pubsystem.getHelpLine("!die              -- Logs the bot off of the server."),
                pubsystem.getHelpLine("!newplayer <name> -- Sends new player helper objon to <name>."),
                pubsystem.getHelpLine("!next <name>      -- Sends the next helper objon to <name>."),
                pubsystem.getHelpLine("!end <name>       -- Removes all objons for <name>.")};
            return string;
        } else {
            String[] string = {
                pubsystem.getHelpLine("!uptime           -- Uptime of the bot in minutes."),
                pubsystem.getHelpLine("!newplayer <name> -- Sends new player helper objon to <name>."),
                pubsystem.getHelpLine("!next <name>      -- Sends the next helper objon to <name>."),
                pubsystem.getHelpLine("!end <name>       -- Removes all objons for <name>.")};
            return string;
        }
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.PLAYER_LEFT);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.MESSAGE);
    }

    @Override
    public void start() {
    }

    /**
     * This private class logs the bot off.  It is used to give a slight delay
     * to the log off process.
     */
    private class DieTask extends TimerTask {

        /**
         * This method logs the bot off.
         */
        public void run() {
            m_botAction.die();
        }
    }

    @Override
    public void reloadConfig() {

        this.locations = new LinkedHashMap<String, Location>();

        try {
            if (!m_botAction.getBotSettings().getString("location").isEmpty()) {
                String[] pointsLocation = m_botAction.getBotSettings().getString("location").split(",");
                for (String number : pointsLocation) {
                    String[] data = m_botAction.getBotSettings().getString("location" + number).split(",");
                    String name = data[0];
                    Location loc = Location.valueOf(name.toUpperCase());
                    for (int i = 1; i < data.length; i++) {
                        String[] coords = data[i].split(":");
                        int x = Integer.parseInt(coords[0]);
                        int y = Integer.parseInt(coords[1]);
                        locations.put(coordToString(x, y), loc);
                    }

                }
            }
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }

        doorModeDefault = m_botAction.getBotSettings().getInt("doormode_default");
        doorModeThreshold = m_botAction.getBotSettings().getInt("doormode_threshold");
        doorModeThresholdSetting = m_botAction.getBotSettings().getInt("doormode_threshold_setting");

        String tileSet = m_botAction.getBotSettings().getString("tileset_default");
        try {
            defaultTileSet = Tileset.valueOf(tileSet.toUpperCase());
        } catch (Exception e) {
            defaultTileSet = Tileset.BLUETECH;
        }

        tilesetObjects = new HashMap<Tileset, Integer>();
        tilesetObjects.put(Tileset.BOKI, 0);
        tilesetObjects.put(Tileset.MONOLITH, 1);

        if (m_botAction.getBotSettings().getInt("tileset_enabled") == 1) {
            tilesetEnabled = true;
        }

        if (m_botAction.getBotSettings().getInt("utility_enabled") == 1) {
            enabled = true;
        }


    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub
    }

    @Override
    public void handleSmodCommand(String sender, String command) {
        // TODO Auto-generated method stub
    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[]{};
    }
}
