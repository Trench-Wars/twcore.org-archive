package twcore.bots.duel1bot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerPosition;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class DuelPlayer {

    boolean create, registered, banned, enabled;
    BotAction ba;
    Player player;
    BotSettings rules;

    private static final String db = "website";

    // Regular game stats
    PlayerStats stats;
    int ship = 1;
    int specAt = 5;
    int freq = 0;
    int status = 0;
    int out = -1;

    int rating;
    int userID;
    int userMID;
    String userIP;
    
    String staffer;

    UserData user;
    duel1bot bot;

    // holds all registered league team IDs
    // index = league, 0 = userID
    int[] teams;

    String name;

    // TWEL Info
    int d_noCount;
    static int d_season;
    static int d_deathTime;
    static int d_spawnTime;
    static int d_spawnLimit;
    static int d_maxLagouts;

    // constant player state values for status
    static final int SPEC = -1;
    static final int IN = 0;
    static final int PLAYING = 1;
    static final int WARPING = 2;
    static final int LAGGED = 3;
    static final int OUT = 4;
    static final int REOUT = 5;
    static final int RETURN = 6;

    // constant player values for removal reason
    static final int NORMAL = 0;
    static final int WARPS = 1;
    static final int LAGOUTS = 2;
    static final int SPAWNS = 3;

    private long lastFoul = 0;
    private long lastDeath = 0;
    private long lastSpec = 0;
    private String lastKiller = "";

    String partner = null;
    int duelFreq = -1;

    TimerTask spawner, dying;
    TimerTask lagout;

    public DuelPlayer(Player p, duel1bot bot) {
        staffer = null;
        name = p.getPlayerName();
        this.bot = bot;
        teams = new int[6];
        ba = bot.ba;
        rules = null;
        freq = p.getFrequency();
        ship = p.getShipType();
        rating = 1000;
        if (ship > 0)
            status = IN;
        else
            status = SPEC;
        create = false;
        registered = false;
        banned = false;
        enabled = false;
        userMID = -1;
        userIP = "";
        bot.lagChecks.add(name.toLowerCase());
        user = new UserData(ba, db, name, true);
        getRules();
        sql_setupUser();
    }

    public DuelPlayer(String p, duel1bot bot) {
        staffer = null;
        name = p;
        this.bot = bot;
        teams = new int[6];
        ba = bot.ba;
        rules = null;
        freq = -1;
        ship = -1;
        rating = -1;
        if (ship > 0)
            status = IN;
        else
            status = SPEC;
        create = false;
        registered = false;
        banned = false;
        enabled = false;
        userMID = -1;
        userIP = "";
        getRules();
        user = new UserData(ba, db, name);
        if (user == null || user.getUserID() < 1)
            user = null;
        else
            sql_setupUser();
    }

    private void getRules() {
        d_season = bot.d_season;
        d_noCount = bot.d_noCount;
        d_deathTime = bot.d_deathTime;
        d_spawnTime = bot.d_spawnTime;
        d_spawnLimit = bot.d_spawnLimit;
        d_maxLagouts = bot.d_maxLagouts;
    }
    
    
    /** Handles position events  */
    public void handlePosition(PlayerPosition event) {
        if (status == WARPING || status == LAGGED || status == OUT || status == REOUT
                || status == RETURN) return;

        int x = event.getXLocation() / 16;
        int y = event.getYLocation() / 16;
        // Player p = ba.getPlayer(name);
        // 416 591
        
        // CHECK FOR WARP
                handleWarp(true);
    }

    public void handleFreq(FrequencyChange event) {
        if (status == WARPING || status == RETURN) return;
        int f = event.getFrequency();

        //TODO: modify
        if (freq == 1) {
            if (f != freq)
                if (status == LAGGED) {
                    setStatus(WARPING);
                    ba.setFreq(name, freq);
                    setStatus(LAGGED);
                } else if (status == PLAYING) {
                    setStatus(WARPING);
                    ba.setFreq(name, freq);
                    ba.specificPrize(name, -13);
                    setStatus(PLAYING);
                    handleWarp(false);
                } else if (status == OUT) {
                    ba.sendPrivateMessage(name, "Please stay on your freq until your duel is finished.");
                    ba.setFreq(name, freq);
                    setStatus(OUT);
                }
        } else if (bot.freqs.contains(f)) {
            if (freq == 9999) ba.specWithoutLock(name);
            ba.setFreq(name, freq);
        } else {
            if (f != duelFreq) {
                bot.removeChalls(duelFreq);
                partner = null;
                duelFreq = -1;
            }
            freq = f;
        }
    }

    public void handleFSC(FrequencyShipChange event) {
        int shipNum = event.getShipType();
        if (status == WARPING || ((status == LAGGED || status == OUT) && shipNum == 0)
                || status == RETURN) return;
    }

    
    /**
     * Sends a message to a lagged out player with return instructions.
     */
    public void handleReturn() {
        setStatus(RETURN);
        ba.specWithoutLock(name);
        ba.setFreq(name, freq);
        ba.sendPrivateMessage(name, "To return to your duel, reply with !lagout");
        setStatus(LAGGED);
    }

    public void handleDeath(String killerName) {
        setStatus(WARPING);
        long now = System.currentTimeMillis();
        DuelPlayer killer = bot.players.get(killerName.toLowerCase());

        //team.warpToSafe(this);
        // DoubleKill check - remember to add a timer in case its the last death
        if ((killer != null) && (killer.getTimeFromLastDeath() < 2001)
                && (name.equalsIgnoreCase(killer.getLastKiller()))) {
            ba.sendSmartPrivateMessage(name, "Double kill, doesn't count.");
            ba.sendSmartPrivateMessage(killerName, "Double kill, doesn't count.");
            killer.removeDeath();
            stats.decrementStat(StatType.KILLS);
        }

        lastDeath = now;
        lastKiller = killerName;

        if (stats.getStat(StatType.DEATHS) >= specAt) {
            setStatus(OUT);
            dying = new TimerTask() {
                @Override
                public void run() {
                    if (status == OUT) {
                        remove(NORMAL);
                        ba.cancelTask(spawner);
                    }
                }
            };
            ba.scheduleTask(dying, 2000);
        }

        spawner = new TimerTask() {
            @Override
            public void run() {
                // BACKTRACK
                if (status == PLAYING)
                    ;//team.warpToSpawn(DuelPlayer.this);
                else if (status == OUT) remove(NORMAL);
            }
        };
        ba.scheduleTask(spawner, d_deathTime * 1000);
        // BACKTRACK
        //team.game.updateScore();
    }

    public void handleSpawnKill() {
        stats.handleSpawn();
        if (stats.getStat(StatType.SPAWNS) < d_spawnLimit)
            ba.sendPrivateMessage(name,
                    "Spawn killing is illegal. If you should continue to spawn kill you will forfeit your match.");
        else
            remove(SPAWNS);
    }

    /**
     * Handles the event of a player warping.
     */
    public void handleWarp(boolean pos) {
        if (status == WARPING || status == RETURN) return;

        setStatus(WARPING);
    }

    /**
     * Called when a player lags out of a game.
     */
    public void handleLagout() {
    	
    }

    /**
     * Handles a player's !lagout command
     */
    public void doLagout() {
        if (status != LAGGED) {
            ba.sendPrivateMessage(name, "You are not lagged out.");
            return;
        }
        setStatus(RETURN);
        ba.cancelTask(lagout);
        bot.laggers.remove(name.toLowerCase());
        ba.sendPrivateMessage(name, "You have " + (d_maxLagouts - stats.getStat(StatType.LAGOUTS))
                + " lagouts remaining.");
        lastFoul = System.currentTimeMillis();
        ba.setShip(name, ship);
        ba.setFreq(name, freq);
        //TODO: modify
        /*
        if (team.game.state == DuelGame.IN_PROGRESS) {
            lastSpec = lastFoul;
            team.warpPlayer(this);
        } else if (team.game.state == DuelGame.SETUP) 
            team.warpToSafe(this);
            */
    }
    
    /** Handles a player !signup command */
    public void doSignup() {
        if (registered) {
            ba.sendSmartPrivateMessage(name, "You have already registered to play.");
        } else if (!banned) {
            create = true;
            bot.debug("[signup] Attempting to signup player: " + name);
            ba.sendUnfilteredPrivateMessage(name, "*info");
        }
    }
    
    /** Handles a staffer force !signup command */
    public void doSignup(String staff) {
        if (registered)
            ba.sendSmartPrivateMessage(staff, name + " is already registered to play.");
        else if (!banned) {
            create = true;
            staffer = staff;
            bot.debug("[signup] Attempting to signup player: " + name);
            ba.sendUnfilteredPrivateMessage(name, "*info");
        }        
    }
    
    /** Handles a player !disable command */
    public void doDisable(String staff) {
        if (staff != null && staffer != null) {
            ba.sendSmartPrivateMessage(staff, "A separate command is currently in process, try again later.");
            return;
        } else if (staff != null)
            staffer = staff;
        
        if (registered && enabled && !banned)
            sql_disablePlayer();
        else if (staff == null)  
            ba.sendSmartPrivateMessage(name, "Could not disable because name is not registered/enabled or is banned.");
        else {
            staffer = null;
            ba.sendSmartPrivateMessage(staff, "Could not disable because name is not registered/enabled or is banned.");
        }
    }

    /** Handles a player !enable command */
    public void doEnable(String staff) {
        if (staff != null && staffer != null) {
            ba.sendSmartPrivateMessage(staff, "A separate command is currently in process, try again later.");
            return;
        } else if (staff != null)
            staffer = staff;
        
        if (registered && !enabled && !banned)
            sql_enablePlayer();
        else if (staff == null)  
            ba.sendSmartPrivateMessage(name, "A name can only be enabled if not banned and already registered but disabled");
        else {
            staffer = null;
            ba.sendSmartPrivateMessage(staff, "A name can only be enabled if not banned and already registered but disabled");
        }
    }
    
    public void doRating() {
        ba.sendPrivateMessage(name, "Current rating: " + rating);
    }
    
    public void doRec() {
        ba.sendPrivateMessage(name, "" + getKills() + ":" + getDeaths());
    }

    /**
     * Assigns the player's duel partner and freq.
     *
     * @param name
     * @param freq
     */
    public void setDuel(String name, int freq) {
        partner = name;
        duelFreq = freq;
    }
    
    public void setRating(int r) {
        rating = r;
    }
    
    /** Resets partner and duel freq (-1). */
    public void cancelDuel() {
        partner = null;
        duelFreq = -1;
    }

    public String getName() {
        return name;
    }
    
    /** Determines if a player is eligible for league play */
    public boolean canPlay() {
        return registered && enabled && !banned;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isRegistered() {
        return registered;
    }
    
    public boolean isBanned() {
        return banned;
    }
    
    public boolean isSpecced() {
        return status != SPEC && status != OUT && status != REOUT && status != LAGGED;
    }

    /** Returns the number of milliseconds since the last death */
    public long getTimeFromLastDeath() {
        return System.currentTimeMillis() - lastDeath;
    }

    /** Returns the name of the last player to kill this player */
    public String getLastKiller() {
        return lastKiller;
    }
    
    public void startGame(int[] spawn) {
        if (!bot.laggers.containsKey(name.toLowerCase())) {
            warp(spawn[0], spawn[1]);
            ba.sendPrivateMessage(name, "GO GO GO!!!", 104);
        }
        lastSpec = System.currentTimeMillis();
    }
    
    public void endTimePlayed() {
        if (out == -1 && lastSpec > 0) {
            int secs = (int) (System.currentTimeMillis() - lastSpec) / 1000;
            stats.handleTimePlayed(secs);
        }
        lastSpec = 0;
    }

    /** Resets the player tasks and warps to middle */
    public void endGame() {
        if (ship > 0)
            status = SPEC;
        else
            status = IN;
        ba.cancelTask(lagout);
        ba.cancelTask(spawner);
        ba.cancelTask(dying);
        ba.shipReset(name);
        ba.warpTo(name, 512, 502);
        out = -1;
    }

    /** Decrements a death and sets status accordingly */
    public void removeDeath() {
        if (stats.getStat(StatType.DEATHS) == specAt) setStatus(PLAYING);
        if (stats.getStat(StatType.DEATHS) > 0) stats.decrementStat(StatType.DEATHS);
    }

    /** Decrements a kill */
    public void removeKill() {
        if (stats.getStat(StatType.KILLS) > 0) stats.decrementStat(StatType.KILLS);
    }

    /** Increments a kill */
    public void addKill() {
        stats.handleKill();
    }

    /** Returns kills */
    public int getKills() {
        return stats.getStat(StatType.KILLS);
    }

    /** Returns deaths */
    public int getDeaths() {
        return stats.getStat(StatType.DEATHS);
    }
    
    public int getLagouts() {
        return stats.getStat(StatType.LAGOUTS);
    }

    /** Returns player status */
    public int getStatus() {
        return status;
    }
    
    public int getRating() {
        return rating;
    }
    
    public int getTime() {
        return stats.getStat(StatType.PLAYTIME);
    }

    /** Sets the player status */
    public void setStatus(int s) {
        status = s;
    }

    /**
     * Removes the player from the duel and reports the reason for it.
     *
     * @param reason
     */
    public void remove(int reason) {
        ba.specWithoutLock(name);
        ba.setFreq(name, freq);
        endTimePlayed();
        if (status == REOUT) {
            setStatus(OUT);
            return;
        }
        out = reason;
        if (stats.getStat(StatType.DEATHS) != specAt) 
            stats.setStat(StatType.DEATHS, specAt);
        setStatus(OUT);
        //TODO: team.playerOut(this);
    }
    
    private void doPlaytime() {
    	//TODO: check in progress
        long now = System.currentTimeMillis();
        if (lastSpec > 0) {
            int secs = (int) (now - lastSpec) / 1000;
            stats.handleTimePlayed(secs);
            lastSpec = now;
        }
    }

    /** Warps the player to the specified coordinates (in tiles) */
    public void warp(int x, int y) {
        setStatus(WARPING);
        Player p1 = ba.getPlayer(name);
        ba.shipReset(name);
        ba.warpTo(name, x, y);
        p1.updatePlayerPositionManuallyAfterWarp(x, y);
        setStatus(PLAYING);
    }

    /** Warps the player after the player just warped */
    public void warpWarper(int x, int y) {
        setStatus(WARPING);
        Player p1 = ba.getPlayer(name);
        ba.warpTo(name, x, y);
        p1.updatePlayerPositionManuallyAfterWarp(x, y);
        setStatus(PLAYING);
    }

    /** Prepares the player for a duel in the given ship and coordinates. */
    public void starting(int div, int shipNum, int x, int y) {
        if (status == LAGGED) return;
        if (div != -1)
            sql_checkDivision(div);
        setStatus(WARPING);
        if (shipNum > -1)
            ba.setShip(name, ship);
        else if (ship == 0) {
            ba.setShip(name, 1);
            ship = 1;
        }
        ba.setFreq(name, freq);

        stats = new PlayerStats(ship);

        warp(x, y);
    }

    /** Returns the ID of the removal reason */
    public int getReason() {
        return out;
    }

    /** Cancels the duel */
    public void cancelGame(String name) {
    }
    
    public void sql_checkDivision(int div) {
        String query = "SELECT fnUserID, fnRating FROM tblDuel2__league WHERE fnSeason = " + d_season + " AND fnDivision = " + div + " AND fnUserID = " + userID + " LIMIT 1";
        ba.SQLBackgroundQuery(db, "league:" + userID + ":" + div + ":" + name, query);
    }

    public void sql_updateDivision(int div, boolean won) {
        String query = "UPDATE tblDuel2__league SET ";
        query += (won ? ("fnWins = fnWins + 1, ") : ("fnLosses = fnLosses + 1, "));
        query += "fnRating = " + rating + ", ";
        query += "fnKills = fnKills + " + stats.getStat(StatType.KILLS) + ", fnDeaths = fnDeaths + " + stats.getStat(StatType.DEATHS) + ", ";
        // TODO: add other fields (streaks)
        query += "fnLagouts = fnLagouts + " + stats.getStat(StatType.LAGOUTS) + " ";
        query += "WHERE fnSeason = " + d_season + " AND fnUserID = " + userID + " AND fnDivision = " + div;

        try {
            ba.SQLQueryAndClose(db, query);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Stores the player statistics using the specified team ID.
     *
     * @param teamID
     */
    public void sql_storeStats(int teamID, boolean won) {
        String date = new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime());
        String query = "INSERT INTO tblDuel2__stats (fnTeamID, fnUserID, fnShip, fnKills, fnDeaths, fnShots, fnKillJoys, " + 
                "fnKnockOuts, fnKillStreak, fnDeathStreak, fnLagouts, fnTimePlayed) VALUES(" + teamID + ", " + userID + ", " + ship + ", " + stats.getStat(StatType.KILLS) + ", " + stats.getStat(StatType.DEATHS) + ", " +
                stats.getStat(StatType.SHOTS) + ", " + stats.getStat(StatType.KILL_JOYS) + ", " + 
                stats.getStat(StatType.KNOCK_OUTS) + ", " + 
                stats.getStat(StatType.BEST_KILL_STREAK) + ", " + stats.getStat(StatType.WORST_DEATH_STREAK) + ", " +
                stats.getStat(StatType.LAGOUTS) + ", " + stats.getStat(StatType.PLAYTIME) + ")";
        try {
            ba.SQLQueryAndClose(db, query);
            query = "UPDATE tblDuel2__player SET fdLastPlayed = '" + date + "' WHERE fnUserID = " + userID;
            ba.SQLBackgroundQuery(db, null, query);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        //sql_updateDivision(team.div, won);
    }

    /**
     * Creates a player profile in the database using the given
     * IP address and MID as long as no active aliases are found
     * (unless a staffer is set to have issued the signup).
     *
     * @param ip
     *      IP String
     * @param mid
     *      machine ID number
     */
    public void sql_createPlayer(String ip, String mid) {
        if (!create) return;
        create = false;
        String query = "SELECT fnUserID FROM tblDuel2__player WHERE fnEnabled = 1 AND (fcIP = '" + ip + "' OR (fcIP = '" + ip + "' AND fnMID = " + mid + ")) OR fnUserID = " + userID;
        ResultSet rs = null;
        try {
            rs = ba.SQLQuery(db, query);
            if (staffer == null && rs.next()) {
                String ids = "" + rs.getInt(1);
                while (rs.next())
                    ids += ", " + rs.getInt(1);
                sql_reportAlias(ids);
            } else {
                query = "INSERT INTO tblDuel2__player (fnUserID, fcIP, fnMID) VALUES(" + userID + ", '" + Tools.addSlashesToString(ip) + "', " + mid + ")";
                ba.SQLQueryAndClose(db, query);
                registered = true;
                enabled = true;
                ba.sendSmartPrivateMessage(name, "You have been successfully registered to play ranked team duels!");
                if (staffer != null) {
                    ba.sendSmartPrivateMessage(staffer, "Registration successful for " + name);
                    staffer = null;
                }
            }
        } catch (SQLException e) {
            staffer = null;
            bot.debug("[sql_createPlayer] Error creating player: " + name + " IP: " + ip + " MID: " + mid);
            e.printStackTrace();
        } finally {
            ba.SQLClose(rs);
        }
    }
    
    /** Disables this player from league play and updates database */
    private void sql_disablePlayer() {
        String query = "UPDATE tblDuel2__player SET fnEnabled = 0 WHERE fnUserID = " + userID;
        try {
            ba.SQLQueryAndClose(db, query);
            enabled = false;
            create = false;
            ba.sendSmartPrivateMessage(name, "Successfully disabled name from use in 2v2 TWEL duels. ");
            if (staffer != null) {
                ba.sendSmartPrivateMessage(staffer, "Successfully disabled '" + name + "' from use in 2v2 TWEL duels. ");
                staffer = null;
            }
        } catch (SQLException e) {
            bot.debug("[sql_disablePlayer] Could not disable: " + name);
            e.printStackTrace();
        }
    }
    
    /** Enables the player and updates it in the database */
    private void sql_enablePlayer() {
        String query = "SELECT fnUserID FROM tblDuel2__player WHERE fnEnabled = 1 AND (fcIP = '" + userIP + "' OR (fcIP = '" + userIP + "' AND fnMID = " + userMID + ")) OR fnUserID = " + userID;
        ResultSet rs = null;
        try {
            rs = ba.SQLQuery(db, query);
            if (staffer == null && rs.next()) {
                String ids = "" + rs.getInt(1);
                while (rs.next())
                    ids += ", " + rs.getInt(1);
                sql_reportAlias(ids);
            } else {
                query = "UPDATE tblDuel2__player SET fnEnabled = 1 WHERE fnUserID = " + userID;
                ba.SQLQueryAndClose(db, query);
                enabled = true;
                ba.sendSmartPrivateMessage(name, "You have been successfully registered to play ranked team duels!");
                if (staffer != null) {
                    ba.sendSmartPrivateMessage(staffer, "Successfully enabled '" + name + "' for use in 2v2 TWEL duels. ");
                    staffer = null;
                }
            }
        } catch (SQLException e) {
            bot.debug("[sql_enablePlayer] Could not enable: " + name);
            e.printStackTrace();
        } finally {
            ba.SQLClose(rs);
        }
    }
    
    /**
     * Reports a list of aliases preventing the registration of the player.
     *
     * @param ids
     *      a String of user IDs of all the player's active aliases
     */
    private void sql_reportAlias(String ids) {
        String query = "SELECT fcUserName FROM tblUser WHERE fnUserID IN (" + ids + ")";
        ResultSet rs = null;
        try {
            rs = ba.SQLQuery(db, query);
            if (rs.next()) {
                String msg = "The following name(s) must first be disabled before you can register a different name: ";
                msg += rs.getString(1);
                while (rs.next())
                    msg += ", " + rs.getString(1);
                ba.sendSmartPrivateMessage(name, msg);
            } else
                ba.sendSmartPrivateMessage(name, "[ERROR] Failed to find alias names and/or register player.");
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            ba.SQLClose(rs);
        }
    }
    
    /** Prepares player information collected from the database */
    private void sql_setupUser() {
        userID = user.getUserID();
        name = user.getUserName();
        // check if registered
        String query = "SELECT fnEnabled, fcIP, fnMID FROM tblDuel2__player WHERE fnUserID = " + userID + " LIMIT 1";
        ResultSet rs = null;
        try {
            rs = ba.SQLQuery(db, query);
            if (rs.next()) {
                registered = true;
                if (rs.getInt("fnEnabled") == 1)
                    enabled = true;
                userIP = rs.getString("fcIP");
                userMID = rs.getInt("fnMID");
            }
            ba.SQLClose(rs);
            query = "SELECT fnActive FROM tblDuel2__ban WHERE fnUserID = " + userID + " AND fnActive = 1";
            rs = ba.SQLQuery(db, query);
            if (rs.next())
                banned = true;
        } catch (SQLException e) {
            bot.debug("[sql_setupUser] Exception when checking if registered: " + name);
            e.printStackTrace();
        } finally {
            ba.SQLClose(rs);
        }
    }

    public void sql_getRating(String div) {
        String query = "SELECT fnRating FROM tblDuel2__league WHERE fnUserID = " + userID + " AND fnDivision = " + div + " LIMIT 1";
        ResultSet rs = null;
        try {
            rs = ba.SQLQuery(db, query);
            if (rs.next())
                rating = rs.getInt("fnRating");
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            ba.SQLClose(rs);
        }
    }
}