package twcore.bots.duel2bot;

import java.sql.ResultSet;
import java.util.TimerTask;

import twcore.bots.duel2bot.DuelGame;
import twcore.bots.duel2bot.DuelTeam;
import twcore.bots.duel2bot.duel2bot;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerPosition;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class DuelPlayer {

    boolean record;
    BotAction ba;
    Player player;
    BotSettings rules;
    
    private static final String DB = "website";

    // Regular game stats
    PlayerStats stats;
    int ship = 1;
    int specAt = 5;
    int freq = 0;
    int status = 0;
    int out = -1;
    
    int userID;
    
    DuelTeam team;
    duel2bot bot;
    
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
    private String lastKiller = "";
    
    String partner = null;
    int duelFreq = -1;
    
    TimerTask spawner, dying;
    TimerTask lagout;
    
    public DuelPlayer(Player p, duel2bot bot) {
        name = p.getPlayerName();
        this.bot = bot;
        team = null;
        teams = new int[6];
        ba = bot.ba;
        rules = null;
        freq = p.getFrequency();
        ship = p.getShipType();
        if (ship > 0) 
            status = IN;
        else 
            status = SPEC;
        getRules();
    }
    
    public DuelPlayer(String name, DuelTeam team, duel2bot bot) {
        this.name = name;
        this.bot = bot;
        this.team = team;
        teams = new int[6];
        ba = bot.ba;
        rules = team.rules;
        freq = team.freq;
        getRules();
    }
    
    public DuelPlayer(String name, duel2bot bot) {
        this.name = name;
        this.bot = bot;
        team = null;
        teams = new int[6];
        ba = bot.ba;
        rules = null;
        freq = 9999;
        getRules();
    }
    
    private void getRules() {
        d_season = bot.d_season;
        d_noCount = bot.d_noCount;
        d_deathTime = bot.d_deathTime;
        d_spawnTime = bot.d_spawnTime;
        d_spawnLimit = bot.d_spawnLimit;
        d_maxLagouts = bot.d_maxLagouts;
    }
    
    public void handlePosition(PlayerPosition event) {
        if (status == WARPING || status == LAGGED || status == OUT || status == REOUT || status == RETURN)
            return;
        
        int x = event.getXLocation() / 16;
        int y = event.getYLocation() / 16;
        //Player p = ba.getPlayer(name);
        // 416 591
        if (team != null) {
            if ((x < team.game.box.getAreaMinX()) || (y < team.game.box.getAreaMinY()) || 
                    (x > team.game.box.getAreaMaxX()) || 
                    (y > team.game.box.getAreaMaxY()))
                handleWarp(true);
        }
    }
    
    public void handleFreq(FrequencyChange event) {
        if (status == WARPING || status == RETURN)
            return;
        int f = event.getFrequency();
        
        if (team != null && team.game != null) {
            if (f != freq) {
                if (status == LAGGED) {
                    setStatus(WARPING);
                    ba.setFreq(name, freq);
                    setStatus(LAGGED);
                } else if (status == PLAYING){
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
            }
        } else if (bot.freqs.contains(f)) {
            if (freq == 9999)
                ba.specWithoutLock(name);
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
        if (status == WARPING || ((status == LAGGED || status == OUT) && shipNum == 0) || status == RETURN)
            return;
        int f = event.getFrequency();
        int statusID = status;
        setStatus(WARPING);
        if (statusID == OUT) {
            ba.sendPrivateMessage(name, "Please stay in spec and on your freq until your duel is finished.");
            ba.specWithoutLock(name);
            if (freq != f)
                ba.setFreq(name, freq);
            setStatus(OUT);
            return;
        } else if (statusID == LAGGED) {
            ba.specWithoutLock(name);
            if (freq != f)
                ba.setFreq(name, freq);
            ba.sendPrivateMessage(name, "Please use !lagout to return to your duel.");
            setStatus(LAGGED);
            return;                
        }
        if (team == null) {
            if (shipNum == 4 || shipNum == 6) {
                if (ship != 0)
                    ba.setShip(name, ship);
                else
                    ba.specWithoutLock(name);
                ba.sendPrivateMessage(name, "Invalid ship!");
            } else if (shipNum == 0 && duelFreq > -1 && partner != null) {
                bot.removeChalls(duelFreq);
                duelFreq = -1;
                partner = null;
                ship = shipNum;
            } else 
                ship = shipNum;
            
            /*
            if (bot.freqs.contains(freq))
                ba.setFreq(name, freq);
            else
                freq = freq;
            */
                
        } else {
            
            boolean foul = false;
            if (shipNum == 0 && (statusID == PLAYING)) {
                ba.setFreq(name, freq);
                handleLagout();
                return;
            }
            
            /*
            if (freq != freq) {
                foul = true;
                ba.setFreq(name, freq);
            }
            */
            
            if ((shipNum != ship) && (team.game.state != DuelGame.SETUP)) {
                foul = true;
                ba.setShip(name, ship);
                ba.specificPrize(name, -13);
            } else if ((team.game.type == 5) && (team.game.state == DuelGame.SETUP)) {
                if (shipNum == 6 || shipNum == 4)
                    ba.setShip(name, ship);
                else
                    ship = shipNum;
            } else if ((shipNum != ship) && (team.game.type != 5) && (team.game.state == DuelGame.SETUP)) {
                ba.setShip(name, ship);
            }
            
            if (foul || team.game.state == DuelGame.IN_PROGRESS) {
                status = statusID;
                handleWarp(false);
                return;
            } else 
                team.safe(this);
        }
        status = statusID;
    }
    
    public void handleReturn() {
        setStatus(RETURN);
        ba.specWithoutLock(name);
        ba.setFreq(name, freq);
        ba.sendPrivateMessage(name, "To return to your duel, reply with !lagout");
        setStatus(LAGGED);
    }
    
    public void handleDeath(String killerName) {
        if (team == null)
            return;
        setStatus(WARPING);
        long now = System.currentTimeMillis();
        DuelPlayer killer = bot.players.get(killerName.toLowerCase());

        team.safe(this);
        // DoubleKill check - remember to add a timer in case its the last death
        if ((killer != null) && (killer.timeFromLastDeath() < 2001) && (name.equalsIgnoreCase(killer.getLastKiller()))) {
            ba.sendSmartPrivateMessage(name, "Double kill, doesn't count.");
            ba.sendSmartPrivateMessage(killerName, "Double kill, doesn't count.");
            killer.removeDeath();
            stats.decrementStat(StatType.KILLS);
        } else if (!team.wasTK(name, killerName)) {
            if ((now - lastDeath) < ((d_spawnTime + d_deathTime) * 1000)) {
                ba.sendPrivateMessage(name, "Spawn Kill, doesn't count.");
                killer.handleSpawnKill();
            } else {
                stats.handleDeath();
                killer.addKill();
            }
        } else if (team.wasTK(name, killerName))
            stats.handleDeath();

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
                    team.spawn(DuelPlayer.this);
                else if (status == OUT)
                    remove(NORMAL);
            }
        };
        ba.scheduleTask(spawner, d_deathTime * 1000);
        // BACKTRACK
        team.game.updateScore();
    }
    
    public void handleSpawnKill() {
        if (team == null)
            return;
        stats.handleSpawn();
        if (stats.getStat(StatType.SPAWNS) < d_spawnLimit)
            ba.sendPrivateMessage(name, "Spawn killing is illegal. If you should continue to spawn kill you will forfeit your match.");
        else
            remove(SPAWNS);
    }
    
    public void handleWarp(boolean pos) {
        if (status == WARPING || status == RETURN)
            return;        

        setStatus(WARPING);

        long now = System.currentTimeMillis();
        if ((now - lastFoul > 500) && (team.game.state == DuelGame.IN_PROGRESS))
            stats.handleWarp();
        
        if (stats.getStat(StatType.WARPS) < 5 && team.game.state == DuelGame.IN_PROGRESS) {
            if (now - lastFoul > 500) {
                if (pos)
                    ba.sendPrivateMessage(name, "Warping is illegal in this league and if you warp again, you will forfeit.");
                else {
                    ba.sendPrivateMessage(name, "Changing freq or ship is illegal in this league and if you do this again, you will forfeit.");
                }
            }
            // BACKTRACK
            team.warpWarper(this);
        } else if (team.game.state != DuelGame.IN_PROGRESS) {
            // BACKTRACK
            team.safe(this);
        }else {
            ba.sendPrivateMessage(name, "You have forfeited due to warp abuse.");
            remove(WARPS);
        }
        
        lastFoul = now;
    }
    
    public void handleLagout() {
        if (team == null)
            return;

        setStatus(LAGGED);
        
        if (team.game.state == DuelGame.IN_PROGRESS)
            stats.handleLagout();
        
        if (stats.getStat(StatType.LAGOUTS) <= d_maxLagouts) {
            ba.sendSmartPrivateMessage(name, "You have 1 minute to return (!lagout) to your duel or you will forfeit! (!lagout)");
            lagout = new TimerTask() {
                @Override
                public void run() {
                    bot.laggers.remove(name.toLowerCase());
                    ba.sendSmartPrivateMessage(name, "You have forfeited since you have been lagged out for over a minute.");
                    remove(LAGOUTS);                    
                }
            };
            ba.scheduleTask(lagout, 60000);
            // BACKTRACK
            bot.laggers.put(name.toLowerCase(), this);
            team.partnerLagout(name);
        } else {
            ba.sendSmartPrivateMessage(name, "You have exceeded the lagout limit and forfeit your duel.");
            remove(LAGOUTS);
        }
    }
    
    public void doLagout() {
        if (status != LAGGED) {
            ba.sendPrivateMessage(name, "You are not lagged out.");
            return;
        }
        setStatus(RETURN);
        ba.cancelTask(lagout);
        bot.laggers.remove(name.toLowerCase());
        ba.sendPrivateMessage(name, "You have " + (d_maxLagouts - stats.getStat(StatType.LAGOUTS)) + " lagouts remaining.");
        lastFoul = System.currentTimeMillis();
        ba.setShip(name, ship);
        ba.setFreq(name, freq);
        if (team.game.state == DuelGame.IN_PROGRESS)
            team.warpPlayer(this);
        else if (team.game.state == DuelGame.SETUP)
            team.safe(this);
    }
    
    public void setTeam(DuelTeam team) {
        this.team = team;
        rules = team.rules;
        freq = team.freq;
        if (team.div != 5)
            ship = team.ship;
        else {
            Player p = ba.getPlayer(name);
            ship = p.getShipType();
        }
    }
    
    public void setDuel(String name, int freq) {
        partner = name;
        duelFreq = freq;
    }
    
    public void cancelDuel() {
        partner = null;
        duelFreq = -1;
    }
    
    public String getName() {
        return name;
    }
    
    public void warpDelay(DuelPlayer p) {
        setStatus(WARPING);
        team.safe(this);
        
        spawner = new TimerTask() {
            @Override
            public void run() {
                if (status == PLAYING)
                    team.warpPlayer(DuelPlayer.this);
                else if (status == OUT)
                    remove(NORMAL);
            }
        };
        ba.scheduleTask(spawner, d_deathTime * 1000);
        
    }
    
    public long timeFromLastDeath() {
        return System.currentTimeMillis() - lastDeath;
    }
    
    public String getLastKiller() {
        return lastKiller;
    }
    
    public void endGame() {
        if (ship > 0)
            status = SPEC;
        else
            status = IN;
        team = null;
        ba.cancelTask(lagout);
        ba.cancelTask(spawner);
        ba.cancelTask(dying);
        ba.shipReset(name);
        ba.warpTo(name, 512, 502);
        out = -1;
    }
    
    public void removeDeath() {
        if (stats.getStat(StatType.DEATHS) == specAt)
            setStatus(PLAYING);
        if (stats.getStat(StatType.DEATHS) > 0)
            stats.decrementStat(StatType.DEATHS);
    }
    
    public void removeKill() {
        if (stats.getStat(StatType.KILLS) > 0) 
            stats.decrementStat(StatType.KILLS);
    }
    
    public void addKill() {
        stats.handleKill();
    }
    
    public int getKills() {
        return stats.getStat(StatType.KILLS);
    }
    
    public int getDeaths() {
        return stats.getStat(StatType.DEATHS);
    }
    
    public int getStatus() {
        return status;
    }
    
    public void setStatus(int s) {
        status = s;
    }
    
    public void remove(int reason) {
        ba.specWithoutLock(name);
        ba.setFreq(name, freq);
        if (status == REOUT) {
            setStatus(OUT);
            return;
        }
        out = reason;
        if (stats.getStat(StatType.DEATHS) != specAt)
            stats.setStat(StatType.DEATHS, specAt);
        setStatus(OUT);
        team.playerOut(this);
    }
    
    public void warp(int x, int y) {
        setStatus(WARPING);
        Player p1 = ba.getPlayer(name);
        ba.shipReset(name);
        ba.warpTo(name, x, y);
        p1.updatePlayerPositionManuallyAfterWarp(x, y);
        setStatus(PLAYING);
    }
    
    public void warpWarper(int x, int y) {
        setStatus(WARPING);
        Player p1 = ba.getPlayer(name);
        ba.warpTo(name, x, y);
        p1.updatePlayerPositionManuallyAfterWarp(x, y);
        setStatus(PLAYING);
    }
    
    public void starting(int shipNum, int x, int y) {
        if (status == LAGGED)
            return;
        setStatus(WARPING);
        if (shipNum > -1) {
            ba.setShip(name, ship);
        } else if (ship == 0) {
            ba.setShip(name, 1);
            ship = 1;
        }
        ba.setFreq(name, freq);

        stats = new PlayerStats(ship);
        
        warp(x, y);
    }

    public int getReason() {
        return out;
    }
    
    public void cancelGame(String name) {
        if (team == null || team.game == null) {
            ba.sendPrivateMessage(name, "No game found.");
            return;
        } else {
            team.game.cancelGame(name);
        }
    }
    
    public void sql_teamPop() {
        ResultSet rs;
        String query = "SELECT fnUserID AS u, fnTeamID AS id, fnLeagueTypeID AS type FROM tblDuel__2league WHERE fnSeason = " + d_season + " AND fnStatus = 1 AND fnUserID = (SELECT fnUserID FROM tblUser WHERE fcUserName = '" + Tools.addSlashesToString(name) + "' ORDER BY fnUserID ASC LIMIT 1)";
        
        try {
            rs = ba.SQLQuery(DB, query);

            if (rs.next()) {
                userID = rs.getInt("u");
                do {
                    teams[rs.getInt("type")] = rs.getInt("id");                    
                } while (rs.next());
                teams[0] = userID;
            }

            ba.SQLClose(rs);
        } catch (Exception e) {
            System.out.println("SQLException teamPop for " + name);
        }
    }
    
}
