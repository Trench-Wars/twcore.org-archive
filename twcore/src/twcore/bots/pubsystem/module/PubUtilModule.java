package twcore.bots.pubsystem.module;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
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
import twcore.core.util.MapRegions;
import twcore.core.util.Tools;

public class PubUtilModule extends AbstractModule {

    private static final String MAP_NAME = "pubmap";
    
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
    
    public static enum Region {
        MID,
        FLAGROOM,
        LARGE_FR,
        MED_FR,
        TUNNELS,
        CRAM,
        LOWER,
        ROOF,    
        SPAWN,    
        
        SPACE,
        UNKNOWN,
        SAFE
    }
    private MapRegions regions;
    private HashSet<Region> locals;
    
    private HashMap<String, Location> locations;
    
    public boolean tilesetEnabled = false;
    
    // PRIV FREQ
    private boolean privFreqEnabled = true;
    // Lev can attach on public freq
    private boolean levAttachEnabled = true;
    private long uptime = 0;
    private String dummy;   // So it's not reinstantiated w/ the constantly-used method getLocation

    public PubUtilModule(BotAction botAction, PubContext context) {
        super(botAction, context, "Utility");
        this.uptime = System.currentTimeMillis();
        regions = new MapRegions();
        locals = new HashSet<Region>();
        reloadConfig();
        
    }
    
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
    
    public MapRegions getRegions() {
        return regions;
    }

    private String coordToString(int x, int y) {
        return x + ":" + y;
    }

    // Support added for additional regions not found in Location. Fixes killothon/timedgame issues.
    // Due to how Location is used (testing if Loc =/!= X), we can't just add new Locations
    // without changing logic in many places.
    // TODO: REMOVE LOCATION AND ONLY WORK BASED ON REGION. LOCATION IS UNNECESSARY AND RESULTS IN BLOAT
    public Location getLocation(int x, int y) {
        Region region = getRegion(x, y);
        Location location = null;
        if (region != null) {
            try {
                location = Location.valueOf(region.toString());
            } catch (IllegalArgumentException e) {
                if (region.equals(Region.CRAM) || region.equals(Region.LARGE_FR) || region.equals(Region.MED_FR))
                    location = Location.valueOf("FLAGROOM");
                else if (region.equals(Region.TUNNELS))
                    location = Location.valueOf("LOWER");
                else
                    location = null;
            }
        }
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
    }

    public void handleEvent(TurretEvent event) {

        Player p1 = m_botAction.getPlayer(event.getAttacheeID());
        Player p2 = m_botAction.getPlayer(event.getAttacherID());

        if (p1 != null) {
            // Attacher stats
            PubPlayer pubPlayer = context.getPlayerManager().getPlayer(p1.getPlayerName());
            if (pubPlayer != null) {
                if (event.isAttaching()) {
                    pubPlayer.handleAttach();
                        if (p1.getShipType() == 4)
                            pubPlayer.setLevTerr(true);
                } else if (!event.isAttaching() && p1.getShipType() == 4){
                    pubPlayer.setLastDetachLevTerr();
                    pubPlayer.setLevTerr(false);
                }
            }
        }

        if (p2 != null) {
            PubPlayer pubPlayer2 = context.getPlayerManager().getPlayer(p1.getPlayerName());
            if (pubPlayer2 != null && p2.getShipType() == Tools.Ship.LEVIATHAN) {                
                // Attachee check up
                if (event.isAttaching()) {
                    pubPlayer2.setLevTerr(true);
                    
                    if (!levAttachEnabled ) {
                        // Public freq?
                        if (p2.getFrequency() == 0 || p2.getFrequency() == 1) {
                            m_botAction.specWithoutLock(p2.getPlayerName());
                            m_botAction.setShip(p2.getPlayerName(), Tools.Ship.LEVIATHAN);
                            m_botAction.setFreq(p2.getPlayerName(), p2.getFrequency());
                            m_botAction.sendSmartPrivateMessage(p2.getPlayerName(), "You cannot attach to a Terrier on a public frequency.");
                        }
                    }
                } else if (!event.isAttaching()) {
                    pubPlayer2.setLastDetachLevTerr();
                    pubPlayer2.setLevTerr(false);
                }
            }
        }
    }

    public void handleEvent(PlayerLeft event) {
        //tutorials.remove(m_botAction.getPlayerName(event.getPlayerID()));
        //checkForDoors();
    }

    /*
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
    */
    /*
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
    /*
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
        //checkForDoors();
        m_botAction.sendSmartPrivateMessage(sender, "Doors will be locked or in operation if the number of players is higher than " + doorModeThreshold + ".");
    }
    */
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
    private void doPrivFreqsCmd(String sender, String verbose ) {
        privFreqEnabled = !privFreqEnabled;

        if (privFreqEnabled) {
            context.getPlayerManager().fixFreqs();
            if (!context.hasJustStarted() && verbose.equals("on")) {
                m_botAction.sendArenaMessage("[SETTING] Private Frequencies enabled.", 2);
            }
            m_botAction.sendSmartPrivateMessage(sender, "Private frequencies succesfully enabled.");
        } else {
            if (!context.hasJustStarted() && verbose.equals("on")) {
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
    
    public Region getRegion(int x, int y) {
        int r = regions.getRegion(x, y);
        Region region = null;
        if (r >= 0)
            region = Region.values()[r];
        return region;
    }

    public String getPlayerLocation(int x, int y) {
        Region location = getRegion(x, y);

        if (Region.UNKNOWN.equals(location)) {
            return "Not yet spotted";
        }
        if (Region.FLAGROOM.equals(location)) {
            return "in Flagroom";
        }
        if (Region.MID.equals(location)) {
            return "in Mid Base";
        }
        if (Region.LOWER.equals(location)) {
            return "in Lower Base";
        }
        if (Region.ROOF.equals(location)) {
            return "on Roof";
        }
        if (Region.SPAWN.equals(location)) {
            return "in Spawn";
        }
        if (Region.SAFE.equals(location)) {
            return "in Safe";
        }
        if (Region.SPACE.equals(location)) {
            return "in Space";
        }

        return "Not yet spotted";
    }

    @Override
    public void handleCommand(String sender, String command) {

        if (command.startsWith("!settile ") || command.startsWith("!tileset ")) {
            //doSetTileCmd(sender, command.substring(9));
        } else if (command.startsWith("!whereis ")) {
            doWhereIsCmd(sender, command.substring(9), m_botAction.getOperatorList().isBot(sender));
        } else if (command.equals("!restrictions")) 
            context.getPlayerManager().doRestrictionsCmd(sender);
    }

    @Override
    public void handleModCommand(String sender, String command) {
        boolean dev = m_botAction.getOperatorList().isDeveloper(sender);
        if (command.startsWith("!go ") && dev) {
            doGoCmd(sender, command.substring(4));
        } else if (command.equals("!stop") && dev) {
            context.stop();
        } else if (command.equals("!privfreqs") && dev) {
            doPrivFreqsCmd(sender, "on" );
        } else if (command.startsWith("!privfreqs") && dev) {
            try {
                doPrivFreqsCmd(sender, command.substring(11) );
            } catch (Exception e) {
                m_botAction.sendPrivateMessage(sender, "Syntax: !privfreqs [(verbose)on|off] (Cmd still a toggle!) Ex: !privfreqs off (no output to arena)");
            }
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

    @Override
    public String[] getHelpMessage(String sender) {
        return new String[]{
                    pubsystem.getHelpLine("!whereis <name>   -- Shows last seen location of <name> (if on your team)."),
                    pubsystem.getHelpLine("!restrictions     -- Lists all current ship restrictions."),
                    //pubsystem.getHelpLine("!settile <name>   -- Change the current tileset."),
                    //pubsystem.getHelpLine("                     Choice: bluetech (default), boki, monolith.")
                };
    }

    @Override
    public String[] getModHelpMessage(String sender) {
        boolean dev = m_botAction.getOperatorList().isDeveloper(sender);
        if (dev) {
            String[] string = {
                pubsystem.getHelpLine("!privfreqs        -- Toggles private frequencies & check for imbalances."),
                //pubsystem.getHelpLine("!dooropen         -- Open doors."),
                //pubsystem.getHelpLine("!doorclose        -- Close doors."),
                //pubsystem.getHelpLine("!doortoggle       -- In operation doors."),
                //pubsystem.getHelpLine("!doorauto         -- Auto mode (close if # of players below " + doorModeThreshold + "."),
                pubsystem.getHelpLine("!levattach        -- Toggles lev attach capability on public frequencies."),
                pubsystem.getHelpLine("!set <ship> <#>   -- Sets <ship> to restriction <#>."),
                pubsystem.getHelpLine("                     0=disabled; 1=any amount; other=weighted:"),
                pubsystem.getHelpLine("                     2 = 1/2 of freq can be this ship, 5 = 1/5, ..."),
                pubsystem.getHelpLine("!go <arena>       -- Moves the bot to <arena>."),
                pubsystem.getHelpLine("!reloadconfig     -- Reload the configuration (needed if .cfg has changed)."),
                pubsystem.getHelpLine("!uptime           -- Uptime of the bot in minutes."),
                pubsystem.getHelpLine("!stop             -- Stop the bot (needed when !go)."),
                pubsystem.getHelpLine("!die              -- Logs the bot off of the server."),
                //pubsystem.getHelpLine("!newplayer <name> -- Sends new player helper objon to <name>."),
                //pubsystem.getHelpLine("!next <name>      -- Sends the next helper objon to <name>."),
                //pubsystem.getHelpLine("!end <name>       -- Removes all objons for <name>.")
                };
            return string;
        } else {
            String[] string = {
                pubsystem.getHelpLine("!uptime           -- Uptime of the bot in minutes."),
                //pubsystem.getHelpLine("!newplayer <name> -- Sends new player helper objon to <name>."),
                //pubsystem.getHelpLine("!next <name>      -- Sends the next helper objon to <name>."),
                //pubsystem.getHelpLine("!end <name>       -- Removes all objons for <name>.")
                };
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

        /*
        String tileSet = m_botAction.getBotSettings().getString("tileset_default");
        try {
            defaultTileSet = Tileset.valueOf(tileSet.toUpperCase());
        } catch (Exception e) {
            defaultTileSet = Tileset.BLUETECH;
        }

        tilesetObjects = new HashMap<Tileset, Integer>();
        tilesetObjects.put(Tileset.BOKI, 0);
        tilesetObjects.put(Tileset.MONOLITH, 1);
        */
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
