package twcore.bots.pubsystem.module;

import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.TimerTask;
import java.util.TreeMap;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerEntered;
import twcore.core.events.TurretEvent;
import twcore.core.game.Player;
import twcore.core.util.MapRegions;
import twcore.core.util.Tools;

public class PubUtilModule extends AbstractModule {

    private static final String MAP_NAME = "pubmap";
    private static final String db = "pubstats";
    
    //private PreparedStatement psPlayTime;
    
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
    
    // XXX: If you change this enum, you will also have to match the Location to Region manually in getLocation
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
        SAFE,
        BUYZONE,
        
        UNKNOWN,
    }
    private MapRegions regions;
    
    private HashMap<String, Location> locations;
    private TreeMap<String, Staffer> staffers;
    
    //Part of anti in spawn check.
    //private TimerTask checkForAntiInSpawn;
    
    private boolean privFreqEnabled = true;     // True if private frequencies are enabled    
    private boolean levAttachEnabled = true;    // True if Lev can attach on public freq
    private long uptime = 0;
    //private String dummy;   // So it's not reinstantiated w/ the constantly-used method getLocation
    //private boolean tilesetEnabled = false;
    //private HashSet<Region> locals;

    public PubUtilModule(BotAction botAction, PubContext context) {
        super(botAction, context, "Utility");
        this.uptime = System.currentTimeMillis();
        regions = new MapRegions();
        //locals = new HashSet<Region>();
        reloadConfig();
        staffers = new TreeMap<String, Staffer>(String.CASE_INSENSITIVE_ORDER);
        SendTime sendTimes = new SendTime();
        m_botAction.scheduleTask(sendTimes, 5 * Tools.TimeInMillis.MINUTE, 5 * Tools.TimeInMillis.MINUTE);
        //psPlayTime = m_botAction.createPreparedStatement(db, "playtime", "INSERT INTO tblStaffer (fcName, fnPlayTime) VALUES(?,?) ON DUPLICATE KEY UPDATE fnPlayTime = fnPlayTime + VALUES(fnPlayTime)");
        
       /* checkForAntiInSpawn = new TimerTask() {
            public void run() {
                for( Player p : m_botAction.getPlayingPlayers() ) {
                    //Player p = m_botAction.getPlayer(event.getPlayerID());
                    if (p!=null) {
                        int reg = regions.getRegion(p);
                        if (reg == Region.SPAWN.ordinal() && p.hasAntiwarpOn()) {
                            m_botAction.specificPrize(p.getPlayerID(), -Tools.Prize.ANTIWARP);
                            m_botAction.sendPrivateMessage(p.getPlayerID(), "ANTI-WARP is ILLEGAL in spawn area and has been removed from your ship. Continued use of antiwarp in spawn may result in a ban from purchasing antiwarp.");
                        }
                    }
                }                
            }
        };
        // Check anti status of all players every 3sec. This won't instantly catch everyone, but if
        // someone is hanging around spawn using anti, it will see it. Furthermore, using anti in
        // spawn is rather obvious and can be reported by old-fashioned means, like any other cheating.
        // Good compromise between performance and efficacy. Checking each position packet slows bot down.
        m_botAction.scheduleTask( checkForAntiInSpawn, 3 * Tools.TimeInMillis.SECOND, 3 * Tools.TimeInMillis.SECOND ); */
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

    /**
     * Support added for additional regions not found in Location. Fixes killothon/timedgame issues.    
     * Due to how Location is used (testing if Loc =/!= X), we can't just add new Locations
     * without changing logic in many places.
     * @param x xTileLocation
     * @param y yTileLocation
     * @return The location that matches the specific coordinates. When nothing is found: Location.SPACE
     * @deprecated {@link Region} and {@link #getRegion(int, int)} are favoured above this and should be used instead.
     */
    @Deprecated
    //TODO: REMOVE LOCATION AND ONLY WORK BASED ON REGION. LOCATION IS UNNECESSARY AND RESULTS IN BLOAT
    public Location getLocation(int x, int y) {
        Region region = getRegion(x, y);
        Location location = null;
        if (region != null) {
            try {
                location = Location.valueOf(region.toString());
            } catch (IllegalArgumentException e) {
                if (region.equals(Region.CRAM) || region.equals(Region.LARGE_FR) || region.equals(Region.MED_FR))
                    location = Location.valueOf("FLAGROOM");
                else if (region.equals(Region.TUNNELS) || region.equals(Region.BUYZONE))
                    location = Location.valueOf("LOWER");
                else if (region.equals(Region.SAFE))
                    location = Location.valueOf("SAFE");
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
    
    @Override
    public void handleEvent(TurretEvent event) {
        if (!enabled) return;
        
        Player p1 = m_botAction.getPlayer(event.getAttacheeID());
        Player p2 = m_botAction.getPlayer(event.getAttacherID());
        
        if (p1 != null) {
            // Attacher stats
            PubPlayer pubPlayer = context.getPlayerManager().getPlayer(p1.getPlayerName());
            if (pubPlayer != null) {
                if (event.isAttaching()) {
                    pubPlayer.handleAttach();
                } else if (p2 != null && !event.isAttaching() && p2.getShipType() == 4){
                    pubPlayer.setLastDetachLevTerr();
                }
            }
        }

        if (p2 != null) {
            PubPlayer pubPlayer2 = context.getPlayerManager().getPlayer(p2.getPlayerName());
            if (pubPlayer2 != null && p2.getShipType() == Tools.Ship.LEVIATHAN) {
                if(event.isAttaching()) {
                // Attachee check up                    
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
                }
            }
        }
    }
    
    public void handleEvent(FrequencyShipChange event) {
        if (!enabled) return;
        
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (name == null) return;
        if (m_botAction.getOperatorList().isZH(name)) {
            int ship = event.getShipType();
            if (ship > 0) {
                if (staffers.containsKey(name))
                    staffers.get(name).enter();
                else
                    staffers.put(name, new Staffer(name));
            } else if (staffers.containsKey(name)) {
                    staffers.get(name).exit();
            }
        }
    }
    
    public void handleEvent(PlayerEntered event) {
        if (!enabled) return;

        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (name == null)
            return;
        if (m_botAction.getOperatorList().isZH(name)) {
            int ship = event.getShipType();
            if (ship > 0) {
                if (staffers.containsKey(name))
                    staffers.get(name).enter();
                else
                    staffers.put(name, new Staffer(name));
            }
        }
    }

    public void handleEvent(PlayerLeft event) {
        if (!enabled) return;

        //tutorials.remove(m_botAction.getPlayerName(event.getPlayerID()));
        //checkForDoors();
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if (name != null && staffers.containsKey(name))
            staffers.remove(name).exit();
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
        context.start();
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
                m_botAction.sendArenaMessage("[SETTING] Private Frequencies enabled.");
            }
            m_botAction.sendSmartPrivateMessage(sender, "Private frequencies succesfully enabled.");
        } else {
            if (!context.hasJustStarted() && verbose.equals("on")) {
                m_botAction.sendArenaMessage("[SETTING] Private Frequencies disabled.");
            }
            m_botAction.sendSmartPrivateMessage(sender, "Private frequencies succesfully disabled.");
        }
    }

    private void doLevAttachCmd(String sender) {
        levAttachEnabled = !levAttachEnabled;

        if (levAttachEnabled) {
            context.getPlayerManager().fixFreqs();
            if (!context.hasJustStarted()) {
                m_botAction.sendArenaMessage("[SETTING] Leviathan attach capability enabled on public frequencies.");
            }
            m_botAction.sendSmartPrivateMessage(sender, "Leviathan can now attach on a ter in public freq.");
        } else {
            if (!context.hasJustStarted()) {
                m_botAction.sendArenaMessage("[SETTING] Leviathan attach capability disabled on public frequencies.");
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
    	try {
    		context.getMoneySystem().printFruitStatsToLog();
    	} catch (Exception e) {}
        m_botAction.sendSmartPrivateMessage(sender, "Bot logging off.");
        m_botAction.setObjects();
        try {
            m_botAction.scheduleTask(new DieTask(), 300);
        } catch( IllegalStateException e) {
            m_botAction.cancelTasks();
            m_botAction.die("Mod initiated !die");
        }
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
        if (Region.BUYZONE.equals(location)) {
            return "in Buyzone";
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
                pubsystem.getHelpLine("!levattach        -- Toggles lev attach capability on public frequencies."),
                pubsystem.getHelpLine("!set <ship> <#>   -- Sets <ship> to max 1/<#> freq (!set 4 5=20% levi); 0=off"),
                pubsystem.getHelpLine("!go <arena>       -- Moves the bot to <arena>."),
                pubsystem.getHelpLine("!reloadconfig     -- Reload the configuration (needed if .cfg has changed)."),
                pubsystem.getHelpLine("!uptime           -- Uptime of the bot in minutes."),
                pubsystem.getHelpLine("!stop             -- Stop the bot (needed when !go)."),
                pubsystem.getHelpLine("!die              -- Logs the bot off of the server."),
                //pubsystem.getHelpLine("!dooropen         -- Open doors."),
                //pubsystem.getHelpLine("!doorclose        -- Close doors."),
                //pubsystem.getHelpLine("!doortoggle       -- In operation doors."),
                //pubsystem.getHelpLine("!doorauto         -- Auto mode (close if # of players below " + doorModeThreshold + "."),
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
        eventRequester.request(EventRequester.TURRET_EVENT);
        eventRequester.request(EventRequester.PLAYER_LEFT);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
        eventRequester.request(EventRequester.MESSAGE);
    }

    @Override
    public void start() {
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
    }

    @Override
    public void handleSmodCommand(String sender, String command) {
    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[]{};
    }
    
    private class SendTime extends TimerTask {

        @Override
        public void run() {
            for (Staffer s : staffers.values()) {
                if (s.playing) {
                    s.exit();
                    s.enter();
                }
            }
        }
        
    }
    
    /**
     * Staffer class is used to track the play time of staff members.
     *
     * @author WingZero
     */
    private class Staffer {
        
        String name;
        long entryTime;
        long exitTime;
        int playTime;
        boolean playing;
        String date;
        
        public Staffer(String name) {
            this.name = name;
            entryTime = System.currentTimeMillis();
            exitTime = -1;
            playTime = 0;
            playing = true;
            //YYYY-MM-DD
            DateFormat f = new SimpleDateFormat("yyyy-MM-dd");
            date = f.format(entryTime);
            String q = "INSERT INTO tblStaffer (fcName, fnPlayTime, fdDate) " +
        		        "SELECT * FROM (SELECT '" + Tools.addSlashesToString(name) + "', 0, '" + date + "') as tmp " +
        				"WHERE NOT EXISTS (SELECT '" + Tools.addSlashesToString(name) + "' FROM tblStaffer WHERE fcName = '" + Tools.addSlashesToString(name) + "' AND fdDate = '" + date + "')";
            try {
                m_botAction.SQLQueryAndClose(db, q);
            } catch (SQLException e) {
                Tools.printStackTrace(e);
            }
        }
        
        public void exit() {
            if (playing) {
                playing = false;
                exitTime = System.currentTimeMillis();
                playTime += (int) (exitTime - entryTime) / Tools.TimeInMillis.MINUTE;
                entryTime = -1;
                sendTime();
            }
        }
        
        public void enter() {
            if (!playing) {
                playing = true;
                entryTime = System.currentTimeMillis();
                exitTime = -1;
            }
        }
        
        public void sendTime() {
            if (playTime > 0) {
                m_botAction.SQLBackgroundQuery(db, null, "UPDATE tblStaffer SET fnPlayTime = fnPlayTime + " + playTime + " WHERE fcName = '"  + Tools.addSlashesToString(name) + "' AND fdDate = '" + date + "'");
                //m_botAction.SQLBackgroundQuery(db, null, "INSERT INTO tblStaffer (fcName, fnPlayTime) VALUES('" + Tools.addSlashesToString(name) + "'," + playTime + ") ON DUPLICATE KEY UPDATE fnPlayTime = fnPlayTime + VALUES(fnPlayTime)");
                playTime = 0;
            }
        }
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
            m_botAction.cancelTasks();
            m_botAction.die("DieTask initiated");
        }
    }
}
