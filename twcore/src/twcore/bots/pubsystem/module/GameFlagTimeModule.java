package twcore.bots.pubsystem.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.PubUtilModule.Location;
import twcore.bots.pubsystem.module.PubUtilModule.Region;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.TurretEvent;
import twcore.core.game.Player;
import twcore.core.lvz.Objset;
import twcore.core.util.Point;
import twcore.core.util.Tools;

public class GameFlagTimeModule extends AbstractModule {

    private static final int FLAG_CLAIM_SECS = 4;           // Seconds it takes to fully claim a flag
    private static final int INTERMISSION_SECS = 90;        // Seconds between end of round and start of next
    private static final int INTERMISSION_GAME_SECS = 90;   // Seconds between end of game and start of next
    private static final int MAX_FLAGTIME_ROUNDS = 5;       // Max # rounds (odd numbers only)

    // HashMaps to calculte MVPs after each round
    private HashMap<String, Long> playerTimeJoined;         // Time when the player joined this freq in EPOCH
    private HashMap<String, Integer> playerTimes;           // Roundtime of player on freq
    private HashMap<String, Integer> flagClaims;            // Flag claimed during a round
    private HashMap<String, Integer> killsLocationWeigth;   // Total of kill-location-weight after a round
    private HashMap<String, Integer> killsBounty;           // Total of bounty collected by kill after a round
    private HashMap<String, Integer> levKills;              //total number of leviathans killed
    private HashMap<String, Integer> terrKills;             // Number of terr-kill during a round
    private HashMap<String, Integer> kills;                 // Number of kills during a round
    private HashMap<String, Integer> deaths;                // Number of deaths during a round
    private HashMap<String, Integer> tks;                   // Number of tk
    private HashMap<String, Integer> attaches;              // Number of attachee (ter only)
    private HashMap<String, Integer> killsInBase;           // Number of kills inside the base (mid+flagroom)
    private HashMap<String, HashSet<Integer>> ships;        // Type of ships used during a round

    private HashMap<String, LevTerr> levterrs;              // Current lev terrs
    private TimerTask levInfo;                              // Msgs info about LTs as they form
    private TimerTask levCoordReporter;                     // Freq-msgs LT Hunter freq with LT coords
    

    // Kill weight per location
    private static HashMap<Location, Integer> locationWeight;
    static {
        locationWeight = new HashMap<Location, Integer>();
        locationWeight.put(Location.FLAGROOM, 20);
        locationWeight.put(Location.MID, 10);
        locationWeight.put(Location.LOWER, 5);
        locationWeight.put(Location.ROOF, 5);
        locationWeight.put(Location.SPACE, 1);
        locationWeight.put(Location.SPAWN, 1);
    }

    private FlagCountTask flagTimer;                    // Flag time main class
    private StartRoundTask startTimer;                  // TimerTask to start round
    private IntermissionTask intermissionTimer;         // TimerTask for round intermission
    private AuxLvzTask scoreDisplay;                    // Displays score lvz
    private AuxLvzTask scoreRemove;                     // Removes score lvz

    private int flagMinutesRequired;                    // Flag minutes required to win
    private int freq0Score, freq1Score;                 // # rounds won
    //private int minShuffleRound = 3;                    // Minimum number of played rounds before shuffle vote can occur
    private boolean autoVote = false;                   // Automatically start shuffle vote if conditions are met
    //private boolean shuffleVote = false;              // True if player did !shufflevote (unused)
    private boolean votePeriod = false;                 // Time after round in which !shufflevote can be used
    private TimerTask voteWait;                         // Used after a round when waiting for someone to use !shufflevote

    private Objset objs;                                // For keeping track of counter

    // X and Y coords for warp points.  Note that the first X and Y should be
    // the "standard" warp; in TW this is the earwarp.  These coords are used in
    // strict flag time mode.
    private boolean warpCmdsAllowed = true;	// True if players & mods are allowed to use warp cmds
    
    // Warp coords for warp in into FR at the start of a round.
    private Point[] warpPtsLeft;
    private Point[] warpPtsRight;
    
    // Warp coords for safes (for use in strict flag time mode)
    Point warpSafeLeft;
    Point warpSafeRight;

    private boolean warpEnabled = false;
    private boolean autoWarp = false;

    private boolean flagTimeStarted = false;
    private boolean strictFlagTimeMode = false;
    
    // Solo terr incentive: if player is only Terr on freq 0/1, provide a regular bonus
    // NOTE: Values are read from CFG, but these are there as a backup/guide.
    private boolean giveTerrBonus = true;       // True if sole-terr-on-freq, non-LT terrs
                                                // receive a money bonus.
    private int terrBonusFrequency = 117; // How often terrs are given a bonus, in seconds
    private int terrBonusAmt = 250;       // Award amount
    private int terrBonusMinOnFreq = 3;   // Smallest # of players on freq to allow bonus
    
    // LTHunter freq, for those dedicated to LT hunting
    // PROS: Group-shared LT bounties; regularly PMs location of any LTs; provides 1 terr w/ terr bonus
    // CONS: Levis can't join; can't earn round end bonus
    private boolean hunterFreqEnabled = false;
    private int hunterFreq = -1;
    
    private final int LVZ_10TOSTART = 17100;
    private final int LVZ_60TOWIN = 17101;
    private final int LVZ_30TOWIN = 17102;
    private final int LVZ_10TOWIN = 17103;
    private final int LVZ_WINIMMINENT = 17104;
    private final int LVZ_GAMEOVER= 17105;
    private final int LVZ_ROUNDOVER = 17106;
    

    /* Added in and never used?
    private int moneyRoundWin = 0;
    private int moneyGameWin = 0;
    private int moneyMVP = 0;
    */

    public GameFlagTimeModule(BotAction botAction, PubContext context) {
        super(botAction, context, "Game FlagTime");
        objs = m_botAction.getObjectSet();
        playerTimes = new HashMap<String, Integer>();
        levterrs = new HashMap<String, LevTerr>();
        reloadConfig();

    }

    @Override
    public void reloadConfig() {
    	
    	reloadCoordsConfig();
        
        if (m_botAction.getBotSettings().getInt("allow_warp") == 1)
            warpEnabled = true;
        
        if (m_botAction.getBotSettings().getInt("auto_warp") == 1)
            autoWarp = true;

        if (m_botAction.getBotSettings().getInt("flagtime_enabled") == 1)
            enabled = true;
        
        if (m_botAction.getBotSettings().getInt("terr_bonus_enabled") == 1)
            giveTerrBonus = true;
        
        if (giveTerrBonus) {
            int t = m_botAction.getBotSettings().getInt("terr_bonus_amt");
            if (t>0) terrBonusAmt = t;
            t = m_botAction.getBotSettings().getInt("terr_bonus_freq");
            if (t>0) terrBonusFrequency = t;
            t = m_botAction.getBotSettings().getInt("terr_bonus_min_players");
            if (t>0) terrBonusMinOnFreq = t;
        }
        
        if (m_botAction.getBotSettings().getInt("hunter_freq_enabled") == 1)
            hunterFreqEnabled = true;
        
        if (hunterFreqEnabled) {
            int t = m_botAction.getBotSettings().getInt("hunter_freq");
            if (t>0) hunterFreq = t;
            
            levCoordReporter = new TimerTask() {
                String msg = "";
                
                @Override
                public void run() {
                    for (LevTerr lt : levterrs.values()) {
                        if (!lt.isEmpty()) {
                            Player p = m_botAction.getPlayer(lt.terrierName);
                            if (p != null) {}
                               // msg += p.getPlayerName() + " @ " + p.getTextCoords() + "     ";
                        }
                    }
                    if (!msg.equals("")) {
                        // Don't check if anyone's even on the freq; if not, msg will just not send
                        m_botAction.sendOpposingTeamMessageByFrequency(hunterFreq, "LTs:   " + msg);
                        msg = "";
                    }
                }
            };
            m_botAction.scheduleTaskAtFixedRate(levCoordReporter, 5000, 30000);
        }

    }
    
    /**
     * Loads the coordinates from a separate settings file.
     */
    private void reloadCoordsConfig() {
        BotSettings cfg = new BotSettings(m_botAction.getBotSettings().getString("coords_config"));
        
        // Warp points for start of round, left side of base.
        warpPtsLeft = cfg.getPointArray("warp_left", ",", ":");
        
        // Warp points for start of round, right side of base.
        warpPtsRight = cfg.getPointArray("warp_right", ",", ":");
        
        // Coordinates of left safe.
        warpSafeLeft = cfg.getPoint("safe_left", ":");
        
        // Cordinates of right safe.
        warpSafeRight = cfg.getPoint("safe_right", ":");
        
        //TODO: Handle situation in which the warp points aren't located.
        
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.TURRET_EVENT);
    }

    @Override
    public void handleEvent(PlayerLeft event) {
        if (!enabled ) return;

        Player p = m_botAction.getPlayer(event.getPlayerID());
        if (p == null)
            return;

        // LevTerr check
        if (p.getShipType() == Tools.Ship.TERRIER)
            if (levterrs.containsKey(p.getPlayerName()))
                levterrs.remove(p.getPlayerName());
        if (p.getShipType() == Tools.Ship.LEVIATHAN) {
            for (LevTerr lt : levterrs.values()) {
                if (lt.leviathans.contains(p.getPlayerName())) {
                    lt.removeLeviathan(p.getPlayerName());
                    if (lt.isEmpty())
                        lt.allowAlert(true);
                }
            }
        }
    }

    @Override
    public void handleEvent(TurretEvent event) {
        if (!enabled ) return;

        final Player p1 = m_botAction.getPlayer(event.getAttacheeID());
        final Player p2 = m_botAction.getPlayer(event.getAttacherID());

        if (p1 != null && isRunning() && event.isAttaching() && p1.getShipType() == Tools.Ship.TERRIER)
            flagTimer.newAttachee(p1.getPlayerName());

        if (p2 != null)
            // Attachee check up
            if (p2.getShipType() == Tools.Ship.LEVIATHAN)
                if (event.isAttaching()) {

                    final LevTerr levTerr;
                    if (levterrs.containsKey(p1.getPlayerName()))
                        levTerr = levterrs.get(p1.getPlayerName());
                    else {
                        levTerr = new LevTerr(p1.getPlayerName());
                        levterrs.put(p1.getPlayerName(), levTerr);
                    }

                    levTerr.addLeviathan(p2.getPlayerName());

                    try {
                        levInfo.cancel();
                    } catch (Exception e) {                        
                    }
                    
                    TimerTask levInfo = new TimerTask() {
                        @Override
                        public void run() {
                            if (!levTerr.isEmpty() && levTerr.alertAllowed()) {
                                String message = "[LEVTERR Alert] " + p1.getPlayerName() + " (Terrier) with ";
                                String hunterAdv = (hunterFreqEnabled ? "   =" + hunterFreq + " for LT Hunter freq." : "");
                                String lev = "";
                                for (String name : levTerr.getLeviathans())
                                    lev += ", " + name;
                                message += lev.substring(2);
                                int freq = p1.getFrequency();
                                if (freq != 0)
                                    m_botAction.sendOpposingTeamMessageByFrequency(0, message + hunterAdv, 26);
                                if (freq != 1)
                                    m_botAction.sendOpposingTeamMessageByFrequency(1, message + hunterAdv, 26);
                                if (hunterFreqEnabled)
                                    m_botAction.sendOpposingTeamMessageByFrequency(hunterFreq, message, 26);
                                levTerr.allowAlert(false);
                            }
                        }
                    };
                    try {
                        m_botAction.scheduleTask(levInfo, 5 * Tools.TimeInMillis.SECOND);
                    } catch (IllegalStateException e) {
                        Tools.printStackTrace("LevTerr timer exception", e);
                    }

                } else
                    // We can't use AttacheeID (bug)
                    for (LevTerr lt : levterrs.values())
                        if (lt.leviathans.contains(p2.getPlayerName()))
                            lt.leviathans.remove(p2.getPlayerName());
    }

    @Override
    public void handleEvent(FrequencyShipChange event) {
        if (!enabled ) return;

        int playerID = event.getPlayerID();

        Player p = m_botAction.getPlayer(playerID);
        if( p == null )
        	return;
        String playerName = p.getPlayerName();

        PubPlayer pubPlayer = context.getPlayerManager().getPlayer(playerName);
        if (pubPlayer != null) {
            pubPlayer.setLastFreqSwitch(event.getFrequency());
            if (hunterFreqEnabled && pubPlayer.getLastFreq() != event.getFrequency() && event.getFrequency() == hunterFreq)
                m_botAction.sendPrivateMessage(playerID, "[HUNTER]  All LT bounties shared; +$15 for all Levi kills; solo Terrs get regular bonus.");
        }
                
        /* Disabled until hunt re-enabled
        if (context.getPubHunt().isPlayerPlaying(playerName))
            return;
        */

        // Do nothing if the player is dueling
        if (context.getPubChallenge().isEnabled())
            if (context.getPubChallenge().isDueling(playerName))
                return;

        // Stat for a new ship
        if (flagTimer != null)
            flagTimer.newShip(playerName, event.getShipType());

        // Tell the team chat if there is a new Terrier playing
        if (event.getShipType() == Tools.Ship.TERRIER)
            m_botAction.sendOpposingTeamMessageByFrequency(event.getFrequency(), "Player " + p.getPlayerName() + " is now a terr. You may attach.");

        try {
            if (isFlagTimeStarted() && isRunning())
                // Remove player if spec'ing
                if (event.getShipType() == Tools.Ship.SPECTATOR) {
                    String pname = p.getPlayerName();
                    playerTimes.remove(pname);
                }

            /* Should ONLY be done after a player enters, not every shipchange!
            if (warpEnabled && !strictFlagTimeMode && isFlagTimeStarted() && autoWarp && !warpPlayers.containsKey(playerName))
                if (ship != Tools.Ship.SPECTATOR)
                    cmd_warp(playerName);
            */

        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    @Override
    public void handleEvent(FrequencyChange event) {
        if (!enabled ) return;

        if (hunterFreqEnabled && event.getFrequency() == hunterFreq)
            m_botAction.sendPrivateMessage(event.getPlayerID(), "[HUNTER]  LT bounties shared; +$15 for all Levi kills; Terrs get bonus.");
        
        // Reset the time of a player for MVP purpose
        if (isRunning()) {
            Player player = m_botAction.getPlayer(event.getPlayerID());
            if(player != null)
                playerTimeJoined.put(player.getPlayerName(), System.currentTimeMillis());
        }
    }

    @Override
    public void handleEvent(PlayerDeath event) {
        if (!enabled ) return;

        Player killer = m_botAction.getPlayer(event.getKillerID());
        Player killed = m_botAction.getPlayer(event.getKilleeID());

        if (killer == null || killed == null)
            return;

        if (killed.getShipType() == Tools.Ship.TERRIER)
            if (levterrs.containsKey(killed.getPlayerName()))
                levterrs.get(killed.getPlayerName()).allowAlert(true);

        // Check for slain LTs and award bonuses appropriately 
        if (killed.getShipType() == Tools.Ship.LEVIATHAN) {
            for (LevTerr lt : levterrs.values()) {
                if (lt.leviathans.contains(killed.getPlayerName())) {
                    lt.removeLeviathan(killed.getPlayerName());
                    
                    int money = 0;
                    boolean isHunterFreq = (killer.getFrequency() == hunterFreq);
                    
                    // Bonus to killer
                    if (lt.isEmpty()) {
                        lt.allowAlert(true);
                        money = 500 + (event.getKilledPlayerBounty() * 5);                        
                        if (isHunterFreq) {
                            m_botAction.sendPrivateMessage(killer.getPlayerName(), "For killing " + killed.getPlayerName() + ", the last Leviathan of this LevTerr, you get $500 + 5x the Levi's bounty, plus a $250 Hunter Freq bonus!  +$"+ (money + 250));
                            context.getPlayerManager().addMoney(killer.getPlayerName(), money + 250);                            
                        } else {
                            m_botAction.sendPrivateMessage(killer.getPlayerName(), "For killing " + killed.getPlayerName() + ", the last Leviathan of this LevTerr, you get $500 + 5x the Levi's bounty!  +$"+ money);
                            context.getPlayerManager().addMoney(killer.getPlayerName(), money);
                        }
                    } else {
                        money = 250 + (event.getKilledPlayerBounty() * 3);
                        if (isHunterFreq) {
                            m_botAction.sendPrivateMessage(killer.getPlayerName(), "For killing " + killed.getPlayerName() + ", a Leviathan on this LevTerr, you get $250 + 3x the Levi's bounty, plus a $150 Hunter Freq bonus!  +$"+ (money + 150));
                            context.getPlayerManager().addMoney(killer.getPlayerName(), money + 150);                            
                        } else {
                            m_botAction.sendPrivateMessage(killer.getPlayerName(), "For killing " + killed.getPlayerName() + ", a Leviathan on this LevTerr, you get $250 + 3x the Levi's bounty!  +$"+ money);
                            context.getPlayerManager().addMoney(killer.getPlayerName(), money);
                        }
                    }
                    
                    // Hunter freq LT assist: share between all teammates near the Levi
                    if (isHunterFreq) {
                        Iterator<Player> i = m_botAction.getFreqPlayerIterator(hunterFreq);
                        Location locKilled = context.getPubUtil().getLocation(killed.getXTileLocation(), killed.getYTileLocation());
                        Location locHunter;
                        if (locKilled == null)
                            break;

                        while (i.hasNext()) {
                            Player p = i.next();
                            // Assist award for all those on the hunter freq that did not make the actual kill.
                            // Assists must be in the same Location as the Levi to get the award, but kills do not.
                            if( p.getPlayerID() != event.getKillerID() ) {
                                locHunter = context.getPubUtil().getLocation(killed.getXTileLocation(), killed.getYTileLocation());
                                if (locHunter != null && locHunter.equals(locKilled)) {
                                    if (lt.isEmpty()) {
                                        m_botAction.sendPrivateMessage(p.getPlayerName(), "For assisting " + killer.getPlayerName() + " in killing " + killed.getPlayerName() + ", the last Leviathan of this LevTerr, you get $500 + 5x the Levi's bounty!  +$"+ money);
                                        context.getPlayerManager().addMoney(p.getPlayerName(), money);
                                    } else {
                                        m_botAction.sendPrivateMessage(p.getPlayerName(), "For assisting " + killer.getPlayerName() + " in killing " + killed.getPlayerName() + ", a Leviathan on this LevTerr, you get $250 + 3x the Levi's bounty!  +$"+ money);
                                        context.getPlayerManager().addMoney(p.getPlayerName(), money);                                    
                                    }
                                }
                            }
                        }
                    }
                    
                    break;
                }
            }
        }
        
        // Killed by Levi on private freq? Occasionally suggest they join the LT Hunter freq.
        if (killer.getShipType() == Tools.Ship.LEVIATHAN && killer.getFrequency() > 1) {
            Random r = new Random();
            if (r.nextInt(10) == 0)
                m_botAction.sendPrivateMessage(killed.getPlayerID(), "Killed by private-freq Levi. Want revenge? Get bonuses to LT kills on the Hunter freq. Type =99 to join.");
        }
        
        if (isRunning()) {

            if (killer.getPlayerName().equals(m_botAction.getBotName()))
                return;

            // Add kills and check for TK
            if (killer.getFrequency() == killed.getFrequency())
                flagTimer.addTk(killer.getPlayerName());
            else {
                flagTimer.addPlayerKill(killer.getPlayerName(), killed.getShipType(), event.getKilledPlayerBounty(), killer.getXTileLocation(), killer.getYTileLocation());
                flagTimer.addPlayerDeath(killed.getPlayerName());
            }

            // If Terr killed in FR, notify players if another Terr in FR or mid is still alive 
            if (killed.getShipType() == Tools.Ship.TERRIER) {

                Location locKilled = context.getPubUtil().getLocation(killed.getXTileLocation(), killed.getYTileLocation());
                if (locKilled == null || !locKilled.equals(Location.FLAGROOM))
                    return;

                String attachTo = "";

                Iterator<Player> it = m_botAction.getFreqPlayerIterator(killed.getFrequency());
                while (it.hasNext()) {
                    Player p = it.next();
                    if (p.getShipType() != Tools.Ship.TERRIER)
                        continue;
                    if (p.getPlayerName().equals(killed.getPlayerName()))
                        continue;
                    if (context.getPubChallenge().isDueling(p.getPlayerName()))
                        continue;

                    PubPlayer pubPlayer = context.getPlayerManager().getPlayer(p.getPlayerName());
                    if (pubPlayer != null && System.currentTimeMillis() - pubPlayer.getLastDeath() > 5 * Tools.TimeInMillis.SECOND) {
                        Location loc = context.getPubUtil().getLocation(p.getXTileLocation(), p.getYTileLocation());
                        if (loc != null)
                            if (loc.equals(Location.FLAGROOM))
                                attachTo += ", " + p.getPlayerName() + " (" + context.getPubUtil().getLocationName(loc) + ")";
                            else if (loc.equals(Location.MID))
                                attachTo += ", " + p.getPlayerName() + " (" + context.getPubUtil().getLocationName(loc) + ")";
                    }
                }

                if (!attachTo.isEmpty())
                    m_botAction.sendOpposingTeamMessageByFrequency(killed.getFrequency(), killed.getPlayerName() + " is dead. You may attach to "
                            + attachTo.substring(2));
            }
        }
    }

    @Override
    public void handleEvent(PlayerEntered event) {
        if (!enabled ) return;

        int playerID = event.getPlayerID();
        Player player = m_botAction.getPlayer(playerID);
        if (player == null)
            return;
        String playerName = m_botAction.getPlayerName(playerID);

        if (isRunning() ) {
            if( event.getShipType() != Tools.Ship.SPECTATOR )
                flagTimer.newShip(playerName, player.getShipType());
        }
        
        statusMessage(playerName);
    }

    @Override
    public void handleEvent(FlagClaimed event) {
        if (!enabled ) return;

        if (!isFlagTimeStarted())
            return;

        int playerID = event.getPlayerID();
        Player p = m_botAction.getPlayer(playerID);

        if (p.getPlayerName().equals(m_botAction.getBotName()))
            return;

        try {
            if (p != null && flagTimer != null)
                flagTimer.flagClaimed(p.getFrequency(), playerID);
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    @Override
    public void handleCommand(String sender, String command) {

        try {
            if (command.equals("!time"))
                cmd_time(sender);
            else if (command.trim().equals("!team") || command.trim().equals("!tea"))
                cmd_showTeam(sender);
            else if (command.trim().equals("!terr") || command.trim().equals("!t"))
                cmd_terr(sender);
            else if (command.trim().equals("!lt") || command.trim().startsWith("!levter"))
                cmd_levTerr(sender);
            else if (warpCmdsAllowed && (command.trim().equals("!warp") || command.trim().equals("!w")))
                cmd_warp(sender);

        } catch (RuntimeException e) {
            if (e != null && e.getMessage() != null)
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
    }

    @Override
    public void handleModCommand(String sender, String command) {

        try {
            if (command.startsWith("!starttime "))
                cmd_startTime(sender, command.substring(11));
            else if (command.equals("!stricttime"))
                cmd_strictTime(sender);
            else if (command.equals("!stoptime"))
                cmd_stopTime(sender);
            else if (command.trim().equals("!shufflevote"))
                cmd_shuffleVote(sender);

        } catch (RuntimeException e) {
            if (e != null && e.getMessage() != null)
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }

    }

    @Override
    public void handleSmodCommand(String sender, String command) {
        if (command.startsWith("!rounds "))
            cmd_shuffleRounds(sender, command);
        else if (command.trim().equals("!autovote"))
            cmd_autoVote(sender);
        else if (command.equals("!autowarp"))
            cmd_autoWarp(sender);
        else if (command.equals("!allowwarp"))
            cmd_allowWarp(sender);
    }

    @Override
    public String[] getHelpMessage(String sender) {
    	if( warpCmdsAllowed ) {
    		return new String[] { 
                pubsystem.getHelpLine("!warp             -- Warps you inside base at start of next round. (!w)"),
                pubsystem.getHelpLine("!terr             -- Shows terriers on the team and their last seen locations. (!t)"),
                pubsystem.getHelpLine("!lt               -- Shows active levterrs (ter + lev(s) attached)."),
                pubsystem.getHelpLine("!team             -- Tells you which ships your team members are in."),
                pubsystem.getHelpLine("!time             -- Displays info about time remaining in flag time."),

    		};
    	} else {
            return new String[] {
                pubsystem.getHelpLine("!terr             -- Shows terriers on the team and their last seen locations. (!t)"),
                pubsystem.getHelpLine("!lt               -- Shows active levterrs (ter + lev(s) attached)."),
                pubsystem.getHelpLine("!team             -- Tells you which ships your team members are in."),
                pubsystem.getHelpLine("!time             -- Displays info about time remaining in flag time."),

            };    		
    	}
    }

    @Override
    public String[] getModHelpMessage(String sender) {
        return new String[] { pubsystem.getHelpLine("!starttime <#>    -- Starts Flag Time game to <#> minutes"),
                pubsystem.getHelpLine("!stoptime         -- Ends Flag Time mode."),
                pubsystem.getHelpLine("!stricttime       -- Toggles strict mode (all players warped)"),
                pubsystem.getHelpLine("!shufflevote      -- Initiates shuffle teams poll after a round ends.")
        };
    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        if (warpCmdsAllowed) {
            return new String[] { pubsystem.getHelpLine("!autovote         -- Toggles the shuffle vote between auto and manual (!shufflevote)"),
                pubsystem.getHelpLine("!rounds <#>       -- Sets the minumum number of rounds for shuffle vote to <#>"),
                pubsystem.getHelpLine("!autowarp         -- Enables and disables 'opt out' warping style"),
                pubsystem.getHelpLine("!allowwarp        -- Allow/Disallow the !warp command") };
        } else {
            return new String[] { pubsystem.getHelpLine("!autovote         -- Toggles the shuffle vote between auto and manual (!shufflevote)"),
                    pubsystem.getHelpLine("!rounds <#>       -- Sets the minumum number of rounds for shuffle vote to <#>"), };
        }
    }

    /**
     * Shows who on the team is in which ship.
     * 
     * @param sender
     *            is the person issuing the command.
     */
    public void cmd_showTeam(String sender) {
        Player p = m_botAction.getPlayer(sender);
        if (p == null)
            throw new RuntimeException("Can't find you. Please report this to staff.");
        if (p.getShipType() == 0)
            throw new RuntimeException("You must be in a ship for this command to work.");
        ArrayList<Vector<String>> team = getTeamData(p.getFrequency());
        //int players = 0;
        for (int i = 1; i < 9; i++) {
            int num = team.get(i).size();
            String text = num + Tools.formatString((" " + Tools.shipNameSlang(i) + (num == 1 ? "" : "s")), 8);
            text += "   ";
            for (int j = 0; j < team.get(i).size(); j++) {
                text += (j + 1) + ") " + team.get(i).get(j) + "  ";
                //players++;
            }
            m_botAction.sendSmartPrivateMessage(sender, text);
        }
    }

    /**
     * Shows active levterrs.
     */
    public void cmd_levTerr(String sender) {
        Player p = m_botAction.getPlayer(sender);
        if (p == null)
            throw new RuntimeException("Can't find you. Please report this to staff.");

        if (levterrs.isEmpty())
            m_botAction.sendSmartPrivateMessage(sender, "No active LevTerr.");
        else {
            boolean active = false;
            int levNameLength = 15;
            int terNameLength = 19;
            for (LevTerr lt : levterrs.values()) {
                int length = 0;
                if (terNameLength < lt.terrierName.length())
                    terNameLength = lt.terrierName.length();
                if (!lt.isEmpty())
                    for (String name : lt.leviathans)
                        length += name.length();
                if (levNameLength < length)
                    levNameLength = length;
            }
            for (LevTerr lt : levterrs.values())
                if (!lt.isEmpty()) {
                    Player terr = m_botAction.getFuzzyPlayer(lt.terrierName);
                    if (!active) {
                        String header = "Freq      " + Tools.formatString("Name of Terrier", terNameLength + 4);
                        ;
                        header += Tools.formatString("Leviathan(s)", levNameLength + 4);
                        header += "Since";
                        m_botAction.sendSmartPrivateMessage(sender, header);
                    }

                    String freq = (terr.getFrequency() < 100) ? terr.getFrequency() + "" : "(P)";

                    String message = Tools.formatString(freq, 10) + Tools.formatString(terr.getPlayerName(), terNameLength + 4);
                    String levs = "";
                    for (String lev : lt.getLeviathans())
                        levs += ", " + lev;
                    message += Tools.formatString(levs.substring(2), levNameLength + 4);
                    message += Tools.getTimeDiffString(lt.levvingSince, true);
                    m_botAction.sendSmartPrivateMessage(sender, message);
                    active = true;
                }

            if (!active)
                m_botAction.sendSmartPrivateMessage(sender, "No active LevTerr.");

        }
    }

    /**
     * Shows terriers on the team and their last observed locations.
     */
    public void cmd_terr(String sender) {
        Player p = m_botAction.getPlayer(sender);
        if (p == null)
            throw new RuntimeException("Can't find you.  Please report this to staff.");
        if (p.getShipType() == 0)
            throw new RuntimeException("You must be in a ship for this command to work.");
        Iterator<Player> i = m_botAction.getFreqPlayerIterator(p.getFrequency());
        if (!i.hasNext())
            throw new RuntimeException("ERROR: No players detected on your frequency!");
        m_botAction.sendSmartPrivateMessage(sender, "Name of Terrier          Last seen");
        while (i.hasNext()) {
            Player terr = i.next();
            if (terr.getShipType() == Tools.Ship.TERRIER)
                m_botAction.sendSmartPrivateMessage(sender, Tools.formatString(terr.getPlayerName(), 25)
                        + context.getPubUtil().getPlayerLocation(terr.getXTileLocation(), terr.getYTileLocation()));
        }
    }

    /**
     * Displays info about time remaining in flag time round, if applicable.
     */
    public void cmd_time(String sender) {
        if (isFlagTimeStarted())
            if (flagTimer != null)
                flagTimer.sendTimeRemaining(sender);
            else
                throw new RuntimeException("Flag Time mode is just about to start.");
        else
            throw new RuntimeException("Flag Time mode is not currently running.");
    }

    public void cmd_shuffleVote(String name) {
        if (votePeriod) {
            // This var is never checked; probably was going to be used at one point, but then was forgotten?
            //shuffleVote = false;
            votePeriod = false;
            m_botAction.cancelTask(voteWait);
            context.getPlayerManager().checkSizesAndShuffle(name);
        } else {
            m_botAction.sendSmartPrivateMessage(name, "!shufflevote may only be used immediately following the end of a round.");
            return;
        }
    }

    public void cmd_shuffleRounds(String sender, String msg) {
        m_botAction.sendSmartPrivateMessage(sender, "Shuffle is presently disabled.");
        /*
        int r = 3;
        try {
            r = Integer.valueOf(msg.substring(msg.indexOf(" ") + 1));
        } catch (NumberFormatException e) {
            m_botAction.sendSmartPrivateMessage(sender, "Could not convert " + msg);
            return;
        }
        if (r < 1) {
            m_botAction.sendSmartPrivateMessage(sender, "Rounds must be greater than 0");
            return;
        }
        minShuffleRound = r;
        m_botAction.sendSmartPrivateMessage(sender, "Minimum shuffle rounds set to " + r);
        */
    }

    public void cmd_autoVote(String name) {
        autoVote = !autoVote;
        if (!autoVote)
            m_botAction.sendSmartPrivateMessage(name, "Auto shuffle vote has been DISABLED");
        else
            m_botAction.sendSmartPrivateMessage(name, "Auto shuffle vote has been ENABLED");
    }

    /**
     * Starts a "flag time" mode in which a team must hold the flag for a certain consecutive number of minutes in order to win the round.
     * 
     * @param sender
     *            is the person issuing the command.
     * @param argString
     *            is the number of minutes to hold the game to.
     */
    public void cmd_startTime(String sender, String argString) {
        if (isFlagTimeStarted())
            throw new RuntimeException("Flag Time mode has already been started.");

        int min = 0;

        try {
            min = (Integer.valueOf(argString)).intValue();
        } catch (Exception e) {
            throw new RuntimeException("Bad input.  Please supply a number.");
        } 

        if (min < 1 || min > 120)
            throw new RuntimeException("The number of minutes required must be between 1 and 120.");

        flagMinutesRequired = min;

        if (!context.hasJustStarted())
            m_botAction.sendArenaMessage("[FLAG] Flag Time mode has been enabled.");

        m_botAction.sendArenaMessage("[FLAG] Hold for " + flagMinutesRequired + " consecutive minute"
                + (flagMinutesRequired == 1 ? "" : "s") + " to win round. (Best " + (MAX_FLAGTIME_ROUNDS + 1) / 2 + " of " + MAX_FLAGTIME_ROUNDS + ")");
        if (strictFlagTimeMode) {
       		m_botAction.sendArenaMessage("[FLAG] Round 1 begins in 60 seconds.  ALL players warped at round start.");
        } else if (isAutoWarpEnabled()) {
        	if (warpCmdsAllowed)
        		m_botAction.sendArenaMessage("[FLAG] Round 1 begins in 60 seconds.  You will be warped inside base at round start (type :tw-p:!warp to change)." );
        	else
        		m_botAction.sendArenaMessage("[FLAG] Round 1 begins in 60 seconds.  You will be warped inside base at round start." );
        } else {
        	if (warpCmdsAllowed)
        		m_botAction.sendArenaMessage("[FLAG] Round 1 begins in 60 seconds.  Type :tw-p:!warp to warp into base at round start." );
        	else
                m_botAction.sendArenaMessage("[FLAG] Round 1 begins in 60 seconds.");        	        	
        }

        startFlagTimeStarted();
        freq0Score = 0;
        freq1Score = 0;

        m_botAction.scheduleTask(new StartRoundTask(), 60000);
    }

    /**
     * Toggles "strict" flag time mode in which all players are first warped automatically into safe (must be set), and then warped into base.
     * 
     * @param sender
     *            is the person issuing the command.
     */
    public void cmd_strictTime(String sender) {
        if (strictFlagTimeMode) {
            strictFlagTimeMode = false;
            if (isFlagTimeStarted())
                m_botAction.sendSmartPrivateMessage(sender, "Strict flag time mode disabled. Changes will go into effect next round.");
            else
                m_botAction.sendSmartPrivateMessage(sender, "Strict flag time mode disabled. !starttime <minutes> to begin a normal flag time game.");
        } else {
            strictFlagTimeMode = true;
            if (isFlagTimeStarted())
                m_botAction.sendSmartPrivateMessage(sender, "Strict flag time mode enabled. All players will be warped into base next round.");
            else
                m_botAction.sendSmartPrivateMessage(sender, "Strict flag time mode enabled. !starttime <minutes> to begin a strict flag time game.");
        }
    }

    /**
     * Ends "flag time" mode.
     * 
     * @param sender
     *            is the person issuing the command.
     */
    public void cmd_stopTime(String sender) {
        stopTime();
    }

    /**
     * Turns on or off "autowarp" mode, where players opt out of warping into base, rather than opting in.
     * 
     * @param sender
     *            is the person issuing the command.
     */
    public void cmd_autoWarp(String sender) {
        if(!warpCmdsAllowed) {
            m_botAction.sendSmartPrivateMessage(sender, "Sorry, the use of warp commands on this bot are presently disabled. Please speak to Local Dev Union #646 to change this.");
            return;
        }
        
        if (autoWarp) {
            m_botAction.sendSmartPrivateMessage(sender, "Players will no longer automatically be added to the !warp list when they enter the arena.");
            autoWarpDisable();
        } else {
            m_botAction.sendSmartPrivateMessage(sender, "Players will be automatically added to the !warp list when they enter the arena.");
            autoWarpEnable();
        }
    }

    /**
     * Turns on or off allowing players to use !warp to get into base at the start of a round.
     * 
     * @param sender
     *            is the person issuing the command.
     */
    public void cmd_allowWarp(String sender) {
        if(!warpCmdsAllowed) {
            m_botAction.sendSmartPrivateMessage(sender, "Sorry, the use of warp commands on this bot are presently disabled. Please speak to Local Dev Union #646 to change this.");
            return;
        }
        
        if (isWarpEnabled()) {
            m_botAction.sendSmartPrivateMessage(sender, "Players will no longer be able to use !warp.");
            warpDisable();
        } else {
            m_botAction.sendSmartPrivateMessage(sender, "Players will be allowed to use !warp.");
            warpEnable();
        }
    }

    public void cmd_warp(String sender) {

        PubPlayer player = context.getPlayerManager().getPlayer(sender);
        if (player == null) {
            m_botAction.sendPrivateMessage("WingZero", "Null player: " + sender);
            return;
        }

        if (!isFlagTimeStarted()) {
            m_botAction.sendSmartPrivateMessage(sender, "Flag Time mode is not currently running.");
            return;
        } else if (strictFlagTimeMode) {
            m_botAction.sendSmartPrivateMessage(sender, "Strict Flag mode is currently running, !warp has no effect. You will automatically be warped.");
            return;
        } else if (!warpEnabled) {
            m_botAction.sendSmartPrivateMessage(sender, "Warping into base at round start is not currently allowed.");
            return;
        }

        if (!player.setWarp()) {
            m_botAction.sendSmartPrivateMessage(sender, "You will NOT be warped inside the base at the start of each round. Type !warp again to turn back on.");
        } else {
            m_botAction.sendSmartPrivateMessage(sender, "You WILL be warped inside the base at the start of each round. Type !warp again to turn off.");
        }
    }

    /**
     * Shows and hides scores (used at intermission only).
     * 
     * @param time
     *            Time after which the score should be removed
     */
    private void doScores(int time) {
        int[] objs1 = { 2000, (freq0Score < 10 ? 60 + freq0Score : 50 + freq0Score), (freq0Score < 10 ? 80 + freq1Score : 70 + freq1Score) };
        boolean[] objs1Display = { true, true, true };
        scoreDisplay = new AuxLvzTask(objs1, objs1Display);
        int[] objs2 = { 2200, 2000, (freq0Score < 10 ? 60 + freq0Score : 50 + freq0Score), (freq0Score < 10 ? 80 + freq1Score : 70 + freq1Score) };
        boolean[] objs2Display = { true, false, false, false };
        scoreRemove = new AuxLvzTask(objs2, objs2Display);
        m_botAction.scheduleTask(scoreDisplay, 1000);       // Do score display
        m_botAction.scheduleTask(scoreRemove, time - 1000); // do score removal
        m_botAction.showObject(2100);
        // XXX: Shuffle is disabled
        //if (autoVote && freq0Score + freq1Score >= minShuffleRound && Math.abs(freq0Score - freq1Score) > 0)
        //    ;
        context.getPlayerManager().checkFreqSizes(false);
    }

    /**
     * Displays rules and pauses for intermission.
     */
    private void doIntermission() {

        if (!isFlagTimeStarted())
            return;

        int roundNum = freq0Score + freq1Score + 1;

        int intermission = INTERMISSION_SECS;
        //boolean endOfGame = false;
        
        if (roundNum == 1) {
            intermission = INTERMISSION_GAME_SECS;
            
            m_botAction.sendArenaMessage("[FLAG] Hold for " + flagMinutesRequired + " consecutive minute"
                    + (flagMinutesRequired == 1 ? "" : "s") + " to win.  Next game in " + getTimeString(intermission) + ". :tw-p:!warp to warp in" );            
        } else {
            //Let's try not spamming between rounds (only games)
            //m_botAction.sendArenaMessage("[FLAG] " + (roundNum==MAX_FLAGTIME_ROUNDS ? "Final Round" : "Round " + roundNum) + " begins in " + getTimeString(intermission) + ".  (Score: " + freq0Score + " - " + freq1Score + ")"
            //        + (strictFlagTimeMode ? "" : (" :tw-p:!warp to warp in")));
                        
        }

        m_botAction.cancelTask(startTimer);

        /* Disabled until hunt re-enabled
        if (context.getPubHunt().isEnabled() && endOfGame) {
            TimerTask timer = new TimerTask() {
                @Override
                public void run() {
                    context.getPubHunt().prepareGame();
                }
            };
            m_botAction.scheduleTask(timer, 10 * Tools.TimeInMillis.SECOND);
        }
        */

        startTimer = new StartRoundTask();
        m_botAction.scheduleTask(startTimer, intermission * 1000);
    }

    /**
     * Starts a game of flag time mode.
     */
    private void doStartRound() {
        if (!isFlagTimeStarted())
            return;

        try {
            flagTimer.endGame();
            m_botAction.cancelTask(flagTimer);
        } catch (Exception e) {}

        flagTimer = new FlagCountTask();
        m_botAction.showObject(2300); //Turns on coutdown lvz
        m_botAction.hideObject(1000); //Turns off intermission lvz
        m_botAction.scheduleTaskAtFixedRate(flagTimer, 100, 1000);

    }

    /**
     * Ends a round of Flag Time mode & awards prizes. After, sets up an intermission, followed by a new round.
     */
    /*
    @SuppressWarnings("unused")
    private void doEndRound() {

        if (!isFlagTimeStarted() || flagTimer == null)
            return;

        HashSet<String> MVPs = new HashSet<String>();
        boolean gameOver = false;       // Game over, man.. game over!
        int flagholdingFreq = flagTimer.getHoldingFreq();
        int maxScore = (MAX_FLAGTIME_ROUNDS + 1) / 2;  // Score needed to win
        int secs = flagTimer.getTotalSecs();
        int mins = secs / 60;
        int weight = (secs * 3) / 60;

        try {

            // Incremental bounty bonuses
            if (mins >= 90)
                weight += 150;
            else if (mins >= 60)
                weight += 100;
            else if (mins >= 30)
                weight += 45;
            else if (mins >= 15)
                weight += 20;

            if (flagholdingFreq == 0 || flagholdingFreq == 1) {
                if (flagholdingFreq == 0)
                    freq0Score++;
                else
                    freq1Score++;

                if (freq0Score >= maxScore || freq1Score >= maxScore)
                    gameOver = true;
                else {
                    int roundNum = freq0Score + freq1Score;
                    m_botAction.sendArenaMessage("END OF ROUND " + roundNum + ": Freq " + flagholdingFreq + " wins after "
                            + getTimeString(flagTimer.getTotalSecs()) + " (" + weight + " bounty bonus)  Score: " + freq0Score + " - " + freq1Score, 1);
                }

            } else if (flagholdingFreq < 100)
                m_botAction.sendArenaMessage("END ROUND: Freq " + flagholdingFreq + " wins the round after "
                        + getTimeString(flagTimer.getTotalSecs()) + " (" + weight + " bounty bonus)", 1);
            else
                m_botAction.sendArenaMessage("END ROUND: A private freq wins the round after " + getTimeString(flagTimer.getTotalSecs()) + " ("
                        + weight + " bounty bonus)", 1);

            int special = 0;
            // Special prizes for longer battles (add more if you think of any!)
            if (mins > 15) {
                Random r = new Random();
                int chance = r.nextInt(100);

                if (chance == 99)
                    special = 8;
                else if (chance == 98)
                    special = 7;
                else if (chance >= 94)
                    special = 6;
                else if (chance >= 90)
                    special = 5;
                else if (chance >= 75)
                    special = 4;
                else if (chance >= 60)
                    special = 3;
                else if (chance >= 35)
                    special = 2;
                else
                    special = 1;
            }

            Iterator<Player> iterator = m_botAction.getPlayingPlayerIterator();
            Player player;
            while (iterator.hasNext()) {
                player = iterator.next();
                if (player != null)
                    if (player.getFrequency() == flagholdingFreq) {
                        String playerName = player.getPlayerName();

                        Integer i = playerTimes.get(playerName);

                        if (i != null) {
                            // Calculate amount of time actually spent on freq

                            int timeOnFreq = secs - i.intValue();
                            int percentOnFreq = (int) (((float) timeOnFreq / (float) secs) * 100);
                            int modbounty = (int) (weight * ((float) percentOnFreq / 100));

                            if (percentOnFreq == 100) {
                                MVPs.add(playerName);
                                m_botAction.sendSmartPrivateMessage(playerName, "For staying with the same freq the entire match, you are an MVP and receive the full bonus: "
                                        + modbounty);
                                int grabs = flagTimer.getFlagGrabs(playerName);
                                if (special == 4) {
                                    m_botAction.sendSmartPrivateMessage(playerName, "You also receive an additional " + weight
                                            + " bounty as a special prize!");
                                    modbounty *= 2;
                                }
                                if (grabs != 0) {
                                    modbounty += (modbounty * (grabs / 10.0));
                                    m_botAction.sendSmartPrivateMessage(playerName, "For your " + grabs
                                            + " flag grabs, you also receive an additional " + grabs + "0% bounty, for a total of " + modbounty);
                                }

                            } else
                                m_botAction.sendSmartPrivateMessage(playerName, "You were with the same freq and ship for the last "
                                        + getTimeString(timeOnFreq) + ", and receive " + percentOnFreq + "% of the bounty reward: " + modbounty);

                            m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize " + modbounty);
                        }

                        if (MVPs.contains(playerName))
                            switch (special) {
                                case 1:  // "Refreshments" -- replenishes all essentials + gives anti
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #6");
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #15");
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #20");
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21");
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21");
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21");
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #22");
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #27");
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #28");
                                    break;
                                case 2:  // "Full shrap"
                                    for (int j = 0; j < 5; j++)
                                        m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #19");
                                    break;
                                case 3:  // "Trophy" -- decoy given
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #23");
                                    break;
                                case 4:  // "Double bounty reward"
                                    break;
                                case 5:  // "Triple trophy" -- 3 decoys
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #23");
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #23");
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #23");
                                    break;
                                case 6:  // "Techno Dance Party" -- plays victory music :P
                                    break;
                                case 7:  // "Sore Loser's Revenge" -- engine shutdown!
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #14");
                                    break;
                                case 8:  // "Bodyguard" -- shields
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #18");
                                    break;
                            }
                    }
            }

            String[] leaderInfo = flagTimer.getTeamLeader(MVPs);
            if (leaderInfo.length != 3)
                return;
            String name, MVplayers = "";
            MVPs.remove(leaderInfo[0]);
            if (!leaderInfo[2].equals("")) {
                String otherleaders[] = leaderInfo[2].split(", ");
                for (int j = 0; j < otherleaders.length; j++)
                    MVPs.remove(otherleaders[j]);
            }
            Iterator<String> i = MVPs.iterator();

            if (i.hasNext()) {
                switch (special) {
                    case 1:  // "Refreshments" -- replenishes all essentials + gives anti
                        m_botAction.sendArenaMessage("Prize for MVPs: Refreshments! (+ AntiWarp for all loyal spiders)");
                        break;
                    case 2:  // "Full shrap"
                        m_botAction.sendArenaMessage("Prize for MVPs: Full shrap!");
                        break;
                    case 3:  // "Trophy" -- decoy given
                        m_botAction.sendArenaMessage("Prize for MVPs: Life-size Trophies of Themselves!");
                        break;
                    case 4:  // "Double bounty reward"
                        m_botAction.sendArenaMessage("Prize for MVPs: Double Bounty Bonus!  (MVP bounty: " + weight * 2 + ")");
                        break;
                    case 5:  // "Triple trophy" -- 3 decoys
                        m_botAction.sendArenaMessage("Prize for MVPs: The Triple Platinum Trophy!");
                        break;
                    case 6:  // "Techno Dance Party" -- plays victory music :P
                        m_botAction.sendArenaMessage("Prize for MVPs: Ultimate Techno Dance Party!", 102);
                        break;
                    case 7:  // "Sore Loser's Revenge" -- engine shutdown!
                        m_botAction.sendArenaMessage("Prize for MVPs: Sore Loser's REVENGE!");
                        break;
                    case 8:  // "Bodyguard" -- shields
                        m_botAction.sendArenaMessage("Prize for MVPs: Personal Body-Guard!");
                        break;
                }

                MVplayers = i.next();
                int grabs = flagTimer.getFlagGrabs(MVplayers);
                if (grabs > 0)
                    MVplayers += "(" + grabs + ")";
            }
            int grabs = 0;
            while (i.hasNext()) {
                name = i.next();
                grabs = flagTimer.getFlagGrabs(name);
                if (grabs > 0)
                    MVplayers = MVplayers + ", " + name + "(" + grabs + ")";
                else
                    MVplayers = MVplayers + ", " + name;
            }

            if (leaderInfo[0] != "")
                if (leaderInfo[2] == "")
                    m_botAction.sendArenaMessage("Team Leader was " + leaderInfo[0] + "!  (" + leaderInfo[1] + " flag claim(s) + MVP)");
                else
                    m_botAction.sendArenaMessage("Team Leaders were " + leaderInfo[2] + "and " + leaderInfo[0] + "!  (" + leaderInfo[1]
                            + " flag claim(s) + MVP)");
            if (MVplayers != "")
                m_botAction.sendArenaMessage("MVPs (+ claims): " + MVplayers);

        } catch (Exception e) {
            Tools.printStackTrace(e);
        }

        int intermissionTime = 10000;

        if (gameOver) {
            intermissionTime = 20000;
            doScores(intermissionTime);

            int diff = 0;
            String winMsg = "";
            if (freq0Score >= maxScore) {
                if (freq1Score == 0)
                    diff = -1;
                else
                    diff = freq0Score - freq1Score;
            } else if (freq1Score >= maxScore)
                if (freq0Score == 0)
                    diff = -1;
                else
                    diff = freq1Score - freq0Score;
            switch (diff) {
                case -1:
                    winMsg = " for their masterful victory!";
                    break;
                case 1:
                    winMsg = " for their close win!";
                    break;
                case 2:
                    winMsg = " for a well-executed victory!";
                    break;
                default:
                    winMsg = " for their win!";
                    break;
            }
            m_botAction.sendArenaMessage("GAME OVER!  Freq " + flagholdingFreq + " has won the game after " + getTimeString(flagTimer.getTotalSecs())
                    + " (" + weight + " bounty bonus)  Final score: " + freq0Score + " - " + freq1Score, 2);
            m_botAction.sendArenaMessage("Give congratulations to FREQ " + flagholdingFreq + winMsg);

            freq0Score = 0;
            freq1Score = 0;
        } else
            doScores(intermissionTime);

        try {
            flagTimer.endGame();
            m_botAction.cancelTask(flagTimer);
            m_botAction.cancelTask(intermissionTimer);
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }

        intermissionTimer = new IntermissionTask();
        m_botAction.scheduleTask(intermissionTimer, intermissionTime);
    }*/

    private void doEndRoundNew() {

        if (!isFlagTimeStarted() || flagTimer == null)
            return;
        votePeriod = true;
        voteWait = new TimerTask() {
            @Override
            public void run() {
                votePeriod = false;
            }
        };
        m_botAction.scheduleTask(voteWait, 45 * Tools.TimeInMillis.SECOND);

        // Internal variables
        boolean gameOver = false;
        int winnerFreq = flagTimer.getHoldingFreq();
        boolean hunterFreqWon = winnerFreq == hunterFreq; 
        int maxScore = (MAX_FLAGTIME_ROUNDS + 1) / 2;  // Score needed to win
        int secs = flagTimer.getTotalSecs();
        int mins = (secs / 60);

        int moneyBonus = ((flagTimer.freqsSecs.get(winnerFreq)) * 3);
        if (moneyBonus > 2000)
            moneyBonus = 2000;
        
        if (!hunterFreqWon)
            m_botAction.sendOpposingTeamMessageByFrequency(winnerFreq, "Your team won this round. End-round bonus: $" + moneyBonus);
        else
            m_botAction.sendOpposingTeamMessageByFrequency(winnerFreq, "NOTE: The LT Hunter freq won this round, but you receive no bonus for winning.");

        // A normal frequency (0 or 1) won the round?
        if (winnerFreq == 0 || winnerFreq == 1) {
            if (winnerFreq == 0)
                freq0Score++;
            else
                freq1Score++;

            if (freq0Score >= maxScore || freq1Score >= maxScore) {
                gameOver = true;
                moneyBonus *= 1.5;
            }
            
            int roundNumber = freq0Score + freq1Score;
            m_botAction.sendArenaMessage("[FLAG] END OF ROUND " + roundNumber 
                    + ": Freq " + winnerFreq + " wins after " + getTimeString(flagTimer.getTotalSecs()) 
                    + (gameOver?" Final":" Current") + " Score: " + freq0Score + "-" + freq1Score 
                    + " (Bonus: +$" + moneyBonus + ")");
        

        } else if (hunterFreqWon) {
            m_botAction.sendArenaMessage("[FLAG] END ROUND: Levi Hunters (freq " + winnerFreq + ") win the round after " + getTimeString(flagTimer.getTotalSecs())
                    + ". (Bonus: NONE)");
        } else if (winnerFreq < 100) {
            m_botAction.sendArenaMessage("[FLAG] END ROUND: Freq " + winnerFreq + " wins the round after " + getTimeString(flagTimer.getTotalSecs())
                    + ". (Bonus: +$" + moneyBonus + ")");
        } else {
            m_botAction.sendArenaMessage("[FLAG] END ROUND: A PRIVATE FREQ wins the round after " + getTimeString(flagTimer.getTotalSecs()) + "! (Bonus: +$"
                    + moneyBonus + ")");
        }

        // Clear any round restricted buyable items/commands
        context.getMoneySystem().resetRoundRestrictions();
        
        // Achievement part
        // ---------------------------------------

        for (String playerName : playerTimeJoined.keySet()) {
            int timePlayed = (int) ((System.currentTimeMillis() - playerTimeJoined.get(playerName)) / 1000);
            playerTimes.put(playerName, timePlayed);
        }

        LinkedHashMap<String, Integer> killsInBasePercent = new LinkedHashMap<String, Integer>();

        // Recompute some list (get the % instead)
        for (String playerName : killsBounty.keySet())
            killsBounty.put(playerName, (killsBounty.get(playerName) / kills.get(playerName)));
        for (String playerName : killsInBase.keySet())
            if (killsInBase.get(playerName) >= 15)
                killsInBasePercent.put(playerName, (killsInBase.get(playerName) / kills.get(playerName)));
            else
                killsInBasePercent.put(playerName, 0);

        // 3/4 of attaches for non-winners
        for (String playerName : attaches.keySet()) {
            Player p = m_botAction.getPlayer(playerName);
            if (p != null && p.getFrequency() != winnerFreq) {
                Integer i = attaches.get(playerName);
                if (i != null && i > 0 )
                    attaches.put(playerName, new Integer( (int)Math.round((float)i * 0.75)) );
                else
                    attaches.put(playerName, 0);
            }
        }

        LinkedHashMap<String, Integer> lessdeaths = sort(this.deaths, true);
        LinkedHashMap<String, Integer> kills = sort(this.kills, false);
        LinkedHashMap<String, Integer> lvks = sort(this.levKills, false);
        LinkedHashMap<String, Integer> teks = sort(this.terrKills, false);
        LinkedHashMap<String, Integer> flagClaims = sort(this.flagClaims, false);
        LinkedHashMap<String, Integer> killsInBase = sort(this.killsInBase, false);
        LinkedHashMap<String, Integer> attaches = sort(this.attaches, false);
        //LinkedHashMap<String, Integer> deaths = sort(this.deaths, false);
        //LinkedHashMap<String, Integer> tks = sort(this.tks, false);
        killsInBasePercent = sort(killsInBasePercent, false);

        // Achievements composed of more than 1 variable
        LinkedHashMap<String, Integer> bestTerrier = getBestOf(attaches, killsInBasePercent, lessdeaths);
        LinkedHashMap<String, Integer> basingKing = getBestOf(killsInBase, killsInBasePercent);

        // Make sure we have only terrier in bestTerrier
        Iterator<String> it = bestTerrier.keySet().iterator();
        while (it.hasNext()) {
            String name = it.next();
            if (!attaches.containsKey(name))
                it.remove();
            else if (ships.get(name) != null && !ships.get(name).contains(5))
                it.remove();
        }

        // Achievements (get the #1 of each LinkedHashMap)
        String mostKillName = getPosition(kills, 1);
        String basingKingName = getPosition(basingKing, 1);
        //String mostDeath = getPosition(deaths, 1, 8, false);
        //String lessDeath = getPosition(lessdeaths, 1, 5, true);
        String mostFlagClaimed = getPosition(flagClaims, 1);
        //String mostTk = getPosition(tks, 1, 8, false);
        String mostLvk = getPosition(lvks, 1);
        String mostTek = getPosition(teks, 1);
        String bestTerrierName = getPosition(bestTerrier, 1);

        // Compute the money given
        int m10 = 500 + Math.max(0, (mins - 5) * 10);
        int m5 = 250 + Math.max(0, (mins - 5) * 5);
        //int m2 = 100 + Math.max(0, (mins - 5) * 5);
        
        ArrayList<String> lines = new ArrayList<String>();

        if (basingKingName != null) {
            lines.add("- Basing King/Queen       : " + basingKingName + Tools.rightString(" (+$" + m10 + ")", 8) );
            //m_botAction.sendArenaMessage("- Basing King/Queen       : " + basingKingName + Tools.rightString(" (+$" + m10 + ")", 8) );
            context.getPlayerManager().addMoney(basingKingName, m10, true);
        }
        if (mostKillName != null) {
            lines.add("- Most Veteran Like       : " + mostKillName + Tools.rightString(" (+$" + m5 + ")", 8) );
            //m_botAction.sendArenaMessage("- Most Veteran Like       : " + mostKillName + Tools.rightString(" (+$" + m5 + ")", 8) );
            context.getPlayerManager().addMoney(mostKillName, m5, true);
        }
        if (mostFlagClaimed != null) {
            lines.add("- Flag Savior             : " + mostFlagClaimed + Tools.rightString(" (+$" + m5 + ")", 8) );
            //m_botAction.sendArenaMessage("- Flag Savior             : " + mostFlagClaimed + Tools.rightString(" (+$" + m5 + ")", 8) );
            context.getPlayerManager().addMoney(mostFlagClaimed, m5, true);
        }
        if (bestTerrierName != null) {
            lines.add("- Best Terrier            : " + bestTerrierName + Tools.rightString(" (+$" + m5 + ")", 8) );
            //m_botAction.sendArenaMessage("- Best Terrier            : " + bestTerrierName + Tools.rightString(" (+$" + m5 + ")", 8) );
            context.getPlayerManager().addMoney(bestTerrierName, m5);
        }
        if (mostTek != null) {
            lines.add("- Most Terrier Kills      : " + mostTek + Tools.rightString(" (+$" + m5 + ")", 8) );
            //m_botAction.sendArenaMessage("- Most Terrier Kills      : " + mostTek + Tools.rightString(" (+$" + m5 + ")", 8) );
            context.getPlayerManager().addMoney(mostTek, m5);
        }
        if (mostLvk != null) {
            lines.add("- Most Leviathan Kills    : " + mostLvk + Tools.rightString(" (+$" + m5 + ")", 8) );
            //m_botAction.sendArenaMessage("- Most Leviathan Kills    : " + mostLvk + Tools.rightString(" (+$" + m5 + ")", 8) );
            context.getPlayerManager().addMoney(mostLvk, m5);
        }
        /* Unused achievements
        if (lessDeath != null) {
            m_botAction.sendArenaMessage("- Most Cautious           : " + lessDeath + Tools.rightString(" (+$" + m2 + ")", 8) );
            context.getPlayerManager().addMoney(lessDeath, m2);
        }
        if (mostDeath != null) {
            m_botAction.sendArenaMessage(" - Most Reckless      : " + mostDeath);
        }
        if (mostTk != null) {
            m_botAction.sendArenaMessage(" - Least Honorable    : " + mostTk);
            //context.getPlayerManager().addMoney(mostTk, 0);
        }
        */
                
        // Let's have fun. Fake achievements!
        java.util.Random r = new Random();
        if (r.nextInt(10) == 0) {         
            // Get a random
            List <Player>l = m_botAction.getPlayingPlayers();
            if(l.size() > 0) {
                Player p = l.get( r.nextInt(l.size()) );
                if( p != null ) {

                    String text;
                    switch( r.nextInt(110) ) {
                    case  0: text = "- Most Randomly Selected  : "; break;
                    case  1: text = "- Most Pubbux Owed        : "; break;
                    case  2: text = "- Most Extreme Politics   : "; break;
                    case  3: text = "- Scariest                : "; break;
                    case  4: text = "- Most Likely to Succeed  : "; break;
                    case  5: text = "- Best Dressed            : "; break;
                    case  6: text = "- Basement Dweller        : "; break;
                    case  7: text = "- Most Lovable            : "; break;
                    case  8: text = "- AFK Most of the Round   : "; break;
                    case  9: text = "- Spawnkilling King/Queen : "; break;
                    case 10: text = "- Basing Prince/Princess  : "; break;
                    case 11: text = "- Most PMs Sent           : "; break;
                    case 12: text = "- Forgot Mother's Bday    : "; break;
                    case 13: text = "- Most Delicate           : "; break;
                    case 14: text = "- Best Kisser             : "; break;
                    case 15: text = "- Community Leader        : "; break;
                    case 16: text = "- Most Thrust Used        : "; break;
                    case 17: text = "- Mad                     : "; break;
                    case 18: text = "- Best Lead in a Drama    : "; break;
                    case 19: text = "- Most Help from Friends  : "; break;
                    case 20: text = "- Best Gallery Pic        : "; break;
                    case 21: text = "- Best Pubchat Arguer     : "; break;
                    case 22: text = "- Overall Best Ever       : "; break;
                    case 23: text = "- Quietest Riot           : "; break;
                    case 24: text = "- Tiniest Dancer          : "; break;
                    case 25: text = "- Sexy and Knows It       : "; break;
                    case 26: text = "- In Love with a Ghost    : "; break;
                    case 27: text = "- Most Aliases            : "; break;
                    case 28: text = "- Biggest Elitist         : "; break;
                    case 29: text = "- Fewest Moderator Warns  : "; break;
                    case 30: text = "- Possibly Intoxicated    : "; break;
                    case 31: text = "- Most Unintentional TKs  : "; break;
                    case 32: text = "- Everybody Loves         : "; break;
                    case 33: text = "- Biggest God Complex     : "; break;
                    case 34: text = "- Longest Time in Safe    : "; break;
                    case 35: text = "- Most Unduly Respected   : "; break;
                    case 36: text = "- Most Overrated          : "; break;
                    case 37: text = "- Most Underrated         : "; break;
                    case 38: text = "- Least Intelligible      : "; break;
                    case 39: text = "- Most Requests for Money : "; break;
                    case 40: text = "- Most Ragequits          : "; break;
                    case 41: text = "- Most Eats               : "; break;
                    case 42: text = "- Highest Packetloss      : "; break;
                    case 43: text = "- Most Friends            : "; break;
                    case 44: text = "- Most Rock Bounces       : "; break;
                    case 45: text = "- Best at Running Away    : "; break;
                    case 46: text = "- Most Warps              : "; break;
                    case 47: text = "- Most Serious            : "; break;
                    case 48: text = "- Best Miner on Attack    : "; break;
                    case 49: text = "- Least Time Outside Pub  : "; break;
                    case 50: text = "- Most Relaxed            : "; break;
                    case 51: text = "- Most Fragile Ego        : "; break;
                    case 52: text = "- Happiest Overall        : "; break;
                    case 53: text = "- Just Married            : "; break;
                    case 54: text = "- Best Groomed            : "; break;
                    case 55: text = "- Most PMs Sent to Self   : "; break;
                    case 56: text = "- Most Believable Troll   : "; break;
                    case 57: text = "- Just Dropped from Squad : "; break;
                    case 58: text = "- Who, Exactly, is        : "; break;
                    case 59: text = "- Most Titles Earned      : "; break;
                    case 60: text = "- Most Flag-Ambivalent    : "; break;
                    case 61: text = "- Most PMs to Staff       : "; break;
                    case 62: text = "- Softest Skin            : "; break;
                    case 63: text = "- Least Awake             : "; break;
                    case 64: text = "- Most Profanity          : "; break;
                    case 65: text = "- Best in Nightwasp       : "; break;
                    case 66: text = "- Most Commands to Bot    : "; break;
                    case 67: text = "- MOST CAPS USED          : "; break;
                    case 68: text = "- Best Comedic Screenplay : "; break;
                    case 69: text = "- Most Squad Invites      : "; break;
                    case 70: text = "- Just Doesn't Care       : "; break;
                    case 71: text = "- Most Triple Jav Bounces : "; break;
                    case 72: text = "- Smallest                : "; break;
                    case 73: text = "- Tallest                 : "; break;
                    case 74: text = "- Best Supporting Actor   : "; break;
                    case 75: text = "- Biggest Crush           : "; break;
                    case 76: text = "- Takes Lots of Baths     : "; break;
                    case 77: text = "- Most Yoga Classes Given : "; break;
                    case 78: text = "- Philosophy Genius       : "; break;
                    case 79: text = "- Won't Talk to Me :(     : "; break;
                    case 80: text = "- Overall Most Awesome    : "; break;
                    case 81: text = "- Totally the Bestest     : "; break;
                    case 82: text = "- Most Texts While Flying : "; break;
                    case 83: text = "- Hunted Most             : "; break;
                    case 84: text = "- Best Overall Flavor     : "; break;
                    case 85: text = "- Most Kawaii Banner      : "; break;
                    case 86: text = "- never uses shift key    : "; break;
                    case 87: text = "- Most Disturbing Dreams  : "; break;
                    case 88: text = "- Fastest Levi            : "; break;
                    case 89: text = "- Likes Shiny Things Most : "; break;
                    case 90: text = "- Most Gullible           : "; break;
                    case 91: text = "- Most Interest in Trains : "; break;
                    case 92: text = "- Leland                  : "; break;
                    case 93: text = "- Should Know Better      : "; break;
                    case 94: text = "- Nice Hat                : "; break;
                    case 95: text = "- Smallest PM Reply %     : "; break;
                    case 96: text = "- Dies Over and Over      : "; break;
                    case 97: text = "- Possible Sysop Recruit  : "; break;
                    case 98: text = "- Most Time Avoiding Work : "; break;
                    case 99: text = "- TW's Greatest Asset     : "; break;
                    case 100: text= "- Won't Leave a Residue   : "; break;
                    case 101: text= "- Dropped from TWL        : "; break;
                    case 102: text= "- Universal ignore.txt    : "; break;
                    case 103: text= "- hax                     : "; break;
                    case 104: text= "- Great Pair of Legs      : "; break;
                    case 105: text= "- Spiritual Guide         : "; break;
                    case 106: text= "- Closet Non-Conformist   : "; break;
                    case 107: text= "- My New Rap Name         : "; break;
                    case 108: text= "- Most Grandchildren      : "; break;
                    case 109: text= "- Never Ever Pubs         : "; break;
                    default: text = "- Most Non-Conformist     : ";
                    }
                    int amt = r.nextInt(48) + 2;    // Extremely small, awkward amount to show it's only a joke
                    lines.add( text + p.getPlayerName() + Tools.rightString(" (+$" + amt + ")", 8) );
                    //m_botAction.sendArenaMessage(text + p.getPlayerName() + Tools.rightString(" (+$" + amt + ")", 8) );
                    context.getPlayerManager().addMoney(p.getPlayerName(), amt); // ...but yes, actually give it to them
                }
            }
        }
        
        String output = "";
        for ( String l : lines ) {
            if ( output.equals("") ) {
                output += Tools.formatString(l, 56);
            } else {                
                output += l;
                m_botAction.sendArenaMessage(output);
                output = "";
            }
        }
        if ( !output.equals("") )
            m_botAction.sendArenaMessage(output);

        
        if( r.nextInt(200) == 0 )   // Because very rare surprises are fun/festive!
            m_botAction.sendPublicMessage("Leland");

        
        Iterator<Player> iterator = m_botAction.getFreqPlayerIterator(winnerFreq);
        while (iterator.hasNext()) {

            Player player = iterator.next();

            if (player == null)
                continue;

            if (context.getPubChallenge().isDueling(player.getPlayerName()))
                continue;

            // Wait, make sure this player is not a freq hopper
            // Must have played at least 60 seconds on the winning freq
            if (!playerTimes.containsKey(player.getPlayerName()))
                continue;
            int time = playerTimes.get(player.getPlayerName());
            if (time < 60)
                continue;

            // Money bonus for the winner team
            context.getPlayerManager().addMoney(player.getPlayerName(), moneyBonus);

            // Prizes only for the winner team
            if (mins >= 60) { // New: 1 thor
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #6"); // xradar
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #15"); // multifire
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #20"); // antiwarp
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // repel
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // ..
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // ..
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #22"); // burst
                //m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #24"); // thor
                //m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #26"); // brick
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #27"); // rocket
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #28"); // portal

            } else if (mins >= 45) { // New: antiwarp
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #6"); // xradar
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #15"); // multifire
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #20"); // antiwarp
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // repel
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // ..
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // ..
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #22"); // burst
                //m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #26"); // brick
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #27"); // rocket
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #28"); // portal

            } else if (mins >= 30) { // New: xradar
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #6"); // xradar
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #15"); // multifire
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // repel
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // ..
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // ..
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #22"); // burst
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #27"); // rocket
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #28"); // portal

            } else if (mins >= 15) { // New: burst + rocket
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #15"); // multifire
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // repel
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // ..
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // ..
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #22"); // burst
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #27"); // rocket
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #28"); // portal

            } else if (mins >= 10) { // New: repel + portal
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #15"); // multifire
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // repel
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // ..
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21"); // ..
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #28"); // portal

            } else {
                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #15"); // multifire
            }
            
            if (mins >= 45) {
                final TimerTask displayFireworks = new TimerTask() {
                    int iterations = 0;
                    Random r = new Random();
                    public void run() {
                        if (iterations >= 8)
                            try {
                                this.cancel();
                            } catch(Exception e) {}
                        else {
                            iterations++;
                            // TODO: Get better obj#s for fireworks
                            m_botAction.showObject((r.nextInt(8) + 1));
                        }
                    }
                };
                m_botAction.scheduleTaskAtFixedRate(displayFireworks, 2000, 800);
            }

        }

   /*     if (!autoVote && context.getPlayerManager().checkSizes()) {
            m_botAction.sendOpposingTeamMessageByFrequency(0, "[TEAM SHUFFLE] To start a poll for a pub freq shuffle, PM " + m_botAction.getBotName()
                    + " with !shufflevote ");
            m_botAction.sendOpposingTeamMessageByFrequency(1, "[TEAM SHUFFLE] To start a poll for a pub freq shuffle, PM " + m_botAction.getBotName()
                    + " with !shufflevote ");
        }*/

        // MVP TOP 3
        /*
        HashMap<String,Integer> topPlayers = getTopPlayers();
        Iterator<String> iterator = topPlayers.keySet().iterator();
        m_botAction.sendArenaMessage("MVP:");
        int position = 0;
        int div[] = new int[]{ 1,2,4,5,10 };
        while(iterator.hasNext() && position < 3) {
            position++;
            int moneyBonus = (int)(moneyMVP/div[position-1]);
            String playerName = iterator.next();
            String moneyMessage = "";
            if (context.getMoneySystem().isEnabled()) {
                moneyMessage = " (+$"+moneyBonus+")";
                context.getPlayerManager().addMoney(playerName, moneyBonus);
            }
            m_botAction.sendArenaMessage(" " + position + ". " + playerName + moneyMessage);
        }
        */

        // Is gameover?

        int intermissionTime = 10000;

        if (gameOver) {

            intermissionTime = 20000;
            doScores(intermissionTime);

            int diff = 0;
            String winMsg = "";
            if (freq0Score >= maxScore) {
                if (freq1Score == 0)
                    diff = -1;
                else
                    diff = freq0Score - freq1Score;
            } else if (freq1Score >= maxScore)
                if (freq0Score == 0)
                    diff = -1;
                else
                    diff = freq1Score - freq0Score;
            switch (diff) {
                case -1:
                    winMsg = " educates with a masterful victory";
                    break;
                case 1:
                    winMsg = " muscles in a close win";
                    break;
                case 2:
                    winMsg = " delivers a well-executed victory";
                    break;
                default:
                    winMsg = " wins";
                    break;
            }
            m_botAction.sendArenaMessage("[FLAG] GAME OVER!  Freq " + winnerFreq + winMsg + " after " + getTimeString(flagTimer.getTotalSecs())
                    + ". Final score: " + freq0Score + "-" + freq1Score);
            //m_botAction.sendArenaMessage("Give congratulations to FREQ " + winnerFreq + winMsg + " (Bonus: +$" + moneyBonus + ")");
            m_botAction.showObject(LVZ_GAMEOVER);
            
            freq0Score = 0;
            freq1Score = 0;

        } else {
            m_botAction.showObject(LVZ_ROUNDOVER);
            doScores(intermissionTime);
        }

        try {
            flagTimer.endGame();
            m_botAction.cancelTask(flagTimer);
            m_botAction.cancelTask(intermissionTimer);
        } catch (Exception e) {}

        intermissionTimer = new IntermissionTask();
        m_botAction.scheduleTask(intermissionTimer, intermissionTime);

    }

    /**
     * Warps a player within a radius of 2 tiles to provided coord.
     * 
     * @param playerName Name of the player.
     * @param targetCoord Coordinates the player will warp to.
     */
    private void doPlayerWarp(String playerName, Point targetCoord) {
        doPlayerWarp(playerName, targetCoord.x, targetCoord.y);
    }
    
    /**
     * Warps a player within a radius of 2 tiles to provided coord.
     * 
     * @param playerName
     * @param xCoord
     * @param yCoord
     * @param radius
     * @author Cpt.Guano!
     */
    private void doPlayerWarp(String playerName, int xCoord, int yCoord) {

        // Don't warp a player dueling
        if (context.getPubChallenge().isEnabled())
            if (context.getPubChallenge().isDueling(playerName))
                return;

        int radius = 2;
        double randRadians;
        double randRadius;
        int xWarp = -1;
        int yWarp = -1;

        randRadians = Math.random() * 2 * Math.PI;
        randRadius = Math.random() * radius;
        xWarp = calcXCoord(xCoord, randRadians, randRadius);
        yWarp = calcYCoord(yCoord, randRadians, randRadius);

        m_botAction.warpTo(playerName, xWarp, yWarp);
    }

    /**
     * Adds all players to the hashmap which stores the time, in flagTimer time, when they joined their freq.
     */
    public void setupPlayerTimes() {

        playerTimes = new HashMap<String, Integer>();
        playerTimeJoined = new HashMap<String, Long>();

        Iterator<Player> i = m_botAction.getPlayingPlayerIterator();
        Player player;

        try {
            while (i.hasNext()) {
                player = i.next();
                playerTimes.put(player.getPlayerName(), new Integer(0));
                playerTimeJoined.put(player.getPlayerName(), System.currentTimeMillis());
            }
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void startFlagTimeStarted() {
        this.flagTimeStarted = true;
    }

    public void stopFlagTimeStarted() {
        this.flagTimeStarted = false;
    }

    public boolean canWarpPlayer(String name) {
        //return warpPlayers.contains(name);

        PubPlayer player = context.getPlayerManager().getPlayer(name);
        if (player == null)
            return true;
        return player.getWarp();
    }

    public boolean isFlagTimeStarted() {
        return flagTimeStarted;
    }

    public boolean isRunning() {
        return flagTimer != null && flagTimer.isRunning();
    }

    public boolean isStarted() {
        if (flagTimer == null)
            return false;
        return flagTimer.isStarted;
    }

    public boolean isWarpEnabled() {
        return warpEnabled;
    }

    public boolean isAutoWarpEnabled() {
        return autoWarp;
    }
    
    public boolean isHunterFreqEnabled() {
        return hunterFreqEnabled;
    }
    
    public int getHunterFreq() {
        return hunterFreq;
    }

    /**
     * Formats an integer time as a String.
     * 
     * @param time
     *            Time in seconds.
     * @return Formatted string in 0:00 format.
     */
    public String getTimeString(int time) {
        if (time <= 0)
            return "0:00";
        else {
            int minutes = time / 60;
            int seconds = time % 60;
            return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
        }
    }

    /**
     * Collects names of players on a freq into a Vector ArrayList by ship.
     * 
     * @param freq
     *            Frequency to collect info on
     * @return Vector array containing player names on given freq
     */
    public ArrayList<Vector<String>> getTeamData(int freq) {
        ArrayList<Vector<String>> team = new ArrayList<Vector<String>>();
        // 8 ships plus potential spectators
        for (int i = 0; i < 9; i++)
            team.add(new Vector<String>());
        Iterator<Player> i = m_botAction.getFreqPlayerIterator(freq);
        while (i.hasNext()) {
            Player p = i.next();
            team.get(p.getShipType()).add(p.getPlayerName());
        }
        return team;
    }

    public String getShips(HashMap<String, HashSet<Integer>> ships, String playerName) {
        if (ships.containsKey(playerName)) {
            String ship = "";
            for (int shipNumber : ships.get(playerName))
                ship += "," + shipNumber;
            return ship.substring(1);
        } else
            return "";
    }

    public String getPosition(LinkedHashMap<String, Integer> map, int position, int excludeShip, boolean fullRound) {
        int i = 1;
        Iterator<String> it = map.keySet().iterator();
        while (it.hasNext()) {
            String player = it.next();
            int time = 0;
            if (playerTimes.get(player) != null)
                time = flagTimer.getTotalSecs() - playerTimes.get(player).intValue();
            if (ships.get(player) != null && !ships.get(player).contains(excludeShip) && (!fullRound || time == flagTimer.getTotalSecs()))
                if (i == position)
                    return player;
                else if (i > position)
                    return player;
            i++;
        }
        return null;
    }

    public String getPosition(LinkedHashMap<String, Integer> map, int position) {
        return getPosition(map, position, 0, false);
    }

    @SafeVarargs
    final public LinkedHashMap<String, Integer> getBestOf(LinkedHashMap<String, Integer> ... lists) {

        HashMap<String, Integer> playerWeight = new HashMap<String, Integer>();
        for (LinkedHashMap<String, Integer> list : lists) {
            Iterator<String> players = list.keySet().iterator();
            int i = 0;
            while (players.hasNext()) {
                String player = players.next();
                Integer currentWeight = playerWeight.get(player);
                if (currentWeight == null)
                    currentWeight = 0;
                // Top1 (+6), Top2 (+4), Top3 (+2), rest normal weight
                int weight = (list.size() - i) + Math.max(0, (3 - i)) * 2;
                playerWeight.put(player, currentWeight.intValue() + weight);
                i++;
            }
        }

        return sort(playerWeight, false);

    }

    public HashMap<String, Integer> getTopPlayers() {

        // Sort every list ASC or DESC
        // By most kill, less death, etc..
        // High weight = better
        LinkedHashMap<HashMap<String, Integer>, Integer> sortedList = new LinkedHashMap<HashMap<String, Integer>, Integer>();
        sortedList.put(sort(deaths, true), 25);
        sortedList.put(sort(kills, false), 25);
        sortedList.put(sort(killsBounty, false), 25);
        sortedList.put(sort(levKills, false), 40);
        sortedList.put(sort(terrKills, false), 40);
        sortedList.put(sort(killsLocationWeigth, false), 40);
        sortedList.put(sort(flagClaims, false), 50);
        sortedList.put(sort(playerTimes, false), 50);
        sortedList.put(sort(attaches, false), 50);

        // MVP Algorithm
        // -------------
        // For each list, a weight is attached
        // This weight is multiplied by the position of the player on a list
        // Example : A player is the top 2 for terr kills on a list of 15 players
        //           The weight is 5, he's 2 on 15
        //           5 * (15-2) = 65
        // The player with most points is MVP, etc..

        HashMap<String, Integer> playerWeight = new HashMap<String, Integer>();
        for (Entry<HashMap<String, Integer>, Integer> entry : sortedList.entrySet()) {
            Iterator<String> players = entry.getKey().keySet().iterator();
            int i = 0;
            while (players.hasNext()) {
                String player = players.next();
                Integer currentWeight = playerWeight.get(player);
                if (currentWeight == null)
                    currentWeight = 0;
                int weight = (playerWeight.size() - i) * entry.getValue();
                playerWeight.put(player, currentWeight.intValue() + weight);
                i++;
            }
        }

        return sort(playerWeight, false);

    }

    public int getFreqWithFlag() {
        if (isRunning())
            return flagTimer.flagHoldingFreq;
        return -1;
    }
    
    public Long getLastFreqChange(String name) {
        return playerTimeJoined.get(name);
    }
    
    /**
     * Instantly claims the flag while bypassing the FlagClaimed eventhandler.
     * <p>
     * This method is meant to be used if the pubsystem bot needs to claim the flag himself from a different PubSystem module.
     * This will bypass the FlagClaimed event as well as the check if the bot isn't the claiming party and the three second grace period.
     * 
     * @param freq Frequency for which the flag is to be claimed.
     */
    public void remoteFlagClaim(int freq) {
        flagTimer.flagHoldingFreq = freq;
        m_botAction.showObject(2400); // Shows flag claimed lvz
        flagTimer.isBeingClaimed = false;
        flagTimer.flagClaimingFreq = -1;
        flagTimer.secondsHeld = 0;
    }

    public void stopTime() {
        if (!isFlagTimeStarted())
            return;

        m_botAction.sendArenaMessage("[FLAG] Flag Time mode has been disabled.");

        try {
            if (flagTimer != null)
                flagTimer.endGame();
            m_botAction.cancelTask(flagTimer);
            m_botAction.cancelTask(intermissionTimer);
            m_botAction.cancelTask(startTimer);
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }

        stopFlagTimeStarted();
        strictFlagTimeMode = false;
    }

    /** WARP METHODS **/

    public void autoWarpEnable() {
        this.autoWarp = true;
    }

    public void autoWarpDisable() {
        this.autoWarp = false;
    }

    public void warpEnable() {
        this.warpEnabled = true;
    }

    public void warpDisable() {
        this.warpEnabled = false;
    }

    /**
     * Warps all players who have PMed with !warp into FR at start. Ensures !warpers on freqs are warped all to 'their' side, but not predictably.
     */
    public void warpPlayers(boolean allPlayers) {

        Iterator<?> i = m_botAction.getPlayingPlayerIterator();

        Random r = new Random();
        int rand;
        Player p;
        String pname;

        int randomside = r.nextInt(2);

        while (i.hasNext()) {

            p = (Player) i.next();
            if (p == null)
                continue;
            pname = p.getPlayerName();

            // TODO: Check instead if dueling (why wasn't that done in the first place, I do not know)
            // Also, consider not warping if a terr and on a private freq.
            if (p.getFrequency() != 0 && p.getFrequency() != 1)
                continue;

            PubPlayer player = context.getPlayerManager().getPlayer(pname);

            // Check if the player is a lev. If so, do not warp.
            if (p.getShipType() == Tools.Ship.LEVIATHAN && !player.wasFRCleared())
                continue;
            
            // Check player's location for exempt locations.
            if ((player != null && !allPlayers)) {
                Region reg = context.getPubUtil().getRegion(p.getXTileLocation(), p.getYTileLocation());
                //Do not warp the player if they have disabled warping and aren't inside the flagroom
                //or skip the player if he/she's inside a safe, and not there due to a mine clearing warp.
                if(reg != null)
                    // Check unusual circumstances
                    if( !player.getWarp() ) {
                        if( !Region.FLAGROOM.equals(reg) ) {
                            if( Region.SAFE.equals(reg) ) {
                                 if( !player.wasFRCleared() && !player.areMinesCleared() )
                                     continue;
                            } else {
                                continue;
                            }
                        }
                    }
            }
            
            if (allPlayers)
                rand = 0;
            else {
                rand = r.nextInt(warpPtsLeft.length);
            }
            
            if (p.getFrequency() % 2 == randomside)
                doPlayerWarp(pname, warpPtsLeft[rand]);
            else
                doPlayerWarp(pname, warpPtsRight[rand]);
        }

        /* Reimplement as needed... not sure if we need it at present.
        if (!nullPlayers.isEmpty()) {
            i = nullPlayers.iterator();
            while (i.hasNext())
                warpPlayers.remove(i.next());
        }
        */
    }

    /**
     * In Strict Flag Time mode, warp all players to a safe 10 seconds before starting. This gives a semi-official feeling to the game, and resets all
     * mines, etc.
     */
    public void safeWarp() {
        // Prevent pre-laid mines and portals in strict flag time by setting to
        // WB and back again (slightly hacky)
        HashMap<String, Integer> players = new HashMap<String, Integer>();
        HashMap<String, Integer> bounties = new HashMap<String, Integer>();
        Iterator<Player> it = m_botAction.getPlayingPlayerIterator();
        Player p;
        while (it.hasNext()) {
            p = it.next();
            if (p != null)
                if (p.getShipType() == Tools.Ship.SHARK || p.getShipType() == Tools.Ship.TERRIER || p.getShipType() == Tools.Ship.LEVIATHAN) {
                    players.put(p.getPlayerName(), new Integer(p.getShipType()));
                    bounties.put(p.getPlayerName(), new Integer(p.getBounty()));
                    m_botAction.setShip(p.getPlayerName(), 1);
                }
        }
        Iterator<String> it2 = players.keySet().iterator();
        String name;
        Integer ship, bounty;
        while (it2.hasNext()) {
            name = it2.next();
            ship = players.get(name);
            bounty = bounties.get(name);
            if (ship != null)
                m_botAction.setShip(name, ship.intValue());
            if (bounty != null)
                m_botAction.giveBounty(name, bounty.intValue() - 3);
        }

        Iterator<Player> i = m_botAction.getPlayingPlayerIterator();
        while (i.hasNext()) {
            p = i.next();
            if (p != null)
                if (p.getFrequency() % 2 == 0)
                    m_botAction.warpTo(p.getPlayerID(), warpSafeLeft);
                else
                    m_botAction.warpTo(p.getPlayerID(), warpSafeRight);
        }
    }
    
    /**
     * Clears the FR by warping any inside to safe and then back.For public freqs only.
     */
    public void clearFR() {
        Iterator<Player> i = m_botAction.getPlayingPlayerIterator();
        Player p;

        while (i.hasNext()) {
            p = i.next();

            Region reg = context.getPubUtil().getRegion(p.getXTileLocation(), p.getYTileLocation());
            if( context.getPubChallenge().isDueling(p.getPlayerName()))
                continue;
            PubPlayer pp = context.getPlayerManager().getPlayer(p.getPlayerName());

            // Warp all sharks not in safe and all people in flagroom into safe, then force-warp back in at round start
            if (p.getShipType() == Tools.Ship.SHARK && !Region.SAFE.equals(reg)) {                
                if (pp == null)
                    return;
                
                if (p.getFrequency() % 2 == 0)
                    m_botAction.warpTo(p.getPlayerID(), warpSafeLeft);
                else
                    m_botAction.warpTo(p.getPlayerID(), warpSafeRight);
                
                pp.setMinesCleared(true);
            } else if( Region.FLAGROOM.equals(reg) ) {
                if (pp == null)
                    return;
                
                if (p.getFrequency() % 2 == 0)
                    m_botAction.warpTo(p.getPlayerID(), warpSafeLeft);
                else
                    m_botAction.warpTo(p.getPlayerID(), warpSafeRight);
                
                pp.setFRCleared(true);                
            } else {
                pp.setMinesCleared(false);
                pp.setFRCleared(false);
            }
        }
    }

    public void statusMessage(String playerName) {
        if (isFlagTimeStarted())
            if (flagTimer != null)
                m_botAction.sendSmartPrivateMessage(playerName, flagTimer.getTimeInfo());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public LinkedHashMap<String, Integer> sort(HashMap passedMap, boolean ascending) {

        HashMap clonedMap = (HashMap) passedMap.clone();
        List mapKeys = new ArrayList(clonedMap.keySet());
        List mapValues = new ArrayList(clonedMap.values());
        Collections.sort(mapValues);
        Collections.sort(mapKeys);

        if (!ascending)
            Collections.reverse(mapValues);

        LinkedHashMap someMap = new LinkedHashMap();
        Iterator valueIt = mapValues.iterator();
        while (valueIt.hasNext()) {
            Object val = valueIt.next();
            Iterator keyIt = mapKeys.iterator();
            while (keyIt.hasNext()) {
                Object key = keyIt.next();
                if (clonedMap.get(key).toString().equals(val.toString())) {
                    clonedMap.remove(key);
                    mapKeys.remove(key);
                    someMap.put(key, val);
                    break;
                }
            }
        }
        return someMap;
    }

    @Override
    public void stop() {
        stopTime();
    }

    @Override
    public void start() {
    }

    @Override
    public void handleDisconnect() {
        objs.hideAllObjects();
    }

    private int calcXCoord(int xCoord, double randRadians, double randRadius) {
        return xCoord + (int) Math.round(randRadius * Math.sin(randRadians));
    }

    private int calcYCoord(int yCoord, double randRadians, double randRadius) {
        return yCoord + (int) Math.round(randRadius * Math.cos(randRadians));
    }
    
    /**
     * Public access to flagTimer time remaining.
     * @return Time remaining; -1 if flag timer is not running
     */
    public int getTimeRemaining() {
        if (flagTimer != null && flagTimer.isRunning())
            return flagTimer.getTimeRemaining();
        return -1;
    }

    /**
     * Public access to flagTimer time played.
     * @return Time remaining; -1 if flag timer is not running
     */
    public int getTotalSecs() {
        if (flagTimer != null && flagTimer.isRunning())
            return flagTimer.getTotalSecs();
        return -1;
    }

    /**
     * This private class counts the consecutive flag time an individual team racks up. Upon reaching the time needed to win, it fires the end of the
     * round.
     */
    private class FlagCountTask extends TimerTask {

        int flagHoldingFreq, flagClaimingFreq;
        int secondsHeld, totalSecs, claimSecs, preTimeCount;
        int claimerID;

        HashMap<Integer, Integer> freqsSecs;

        boolean isStarted, isRunning, isBeingClaimed;

        /**
         * FlagCountTask Constructor
         */
        public FlagCountTask() {
            flagHoldingFreq = -1;
            secondsHeld = 0;
            totalSecs = 0;
            claimSecs = 0;
            isStarted = false;
            isRunning = false;
            isBeingClaimed = false;
            freqsSecs = new HashMap<Integer, Integer>();
            flagClaims = new HashMap<String, Integer>();
            kills = new HashMap<String, Integer>();
            levKills = new HashMap<String, Integer>();
            terrKills = new HashMap<String, Integer>();
            deaths = new HashMap<String, Integer>();
            killsLocationWeigth = new HashMap<String, Integer>();
            killsInBase = new HashMap<String, Integer>();
            ships = new HashMap<String, HashSet<Integer>>();
            killsBounty = new HashMap<String, Integer>();
            attaches = new HashMap<String, Integer>();
            tks = new HashMap<String, Integer>();
        }

        /**
         * This method is called by the FlagClaimed event, and tracks who currently has or is in the process of claiming the flag. While the flag can
         * physically be claimed in the game, 3 seconds are needed to claim it for the purpose of the game.
         * 
         * @param freq
         *            Frequency of flag claimer
         * @param pid
         *            PlayerID of flag claimer
         */
        public void flagClaimed(int freq, int pid) {
            if (isRunning == false || freq == -1)
                return;

            // Return the flag back to the team that had it if the claim attempt
            // is unsuccessful (countered by the holding team)
            if (freq == flagHoldingFreq) {
                isBeingClaimed = false;
                claimSecs = 0;
                return;
            }

            if (freq != flagHoldingFreq)
                if ((!isBeingClaimed) || (isBeingClaimed && freq != flagClaimingFreq)) {
                    claimerID = pid;
                    flagClaimingFreq = freq;
                    isBeingClaimed = true;
                    claimSecs = 0;
                }
        }

        public void newAttachee(String player) {

            Integer count = attaches.get(player);
            if (count == null)
                count = new Integer(0);
            attaches.put(player, count.intValue() + 1);

        }

        public void newShip(String player, int shipType) {

            HashSet<Integer> list = ships.get(player);
            if (list == null)
                list = new HashSet<Integer>();
            list.add(shipType);
            ships.put(player, list);

        }

        public void addPlayerKill(String player, int shipTypeKilled, int bountyKilled, int x, int y) {

            Location location = context.getPubUtil().getLocation(x, y);

            // +1 kill
            Integer count = kills.get(player);
            if (count == null)
                kills.put(player, new Integer(1));
            else
                kills.put(player, new Integer(count.intValue() + 1));

            // Lev kill?
            if (shipTypeKilled == Tools.Ship.LEVIATHAN && !location.equals(Location.SPACE) && !location.equals(Location.SPAWN)
                    && !location.equals(Location.ROOF)) {
                Integer levKill = levKills.get(player);
                if (levKill == null)
                    levKills.put(player, new Integer(1));
                else
                    levKills.put(player, new Integer(levKill.intValue() + 1));
            }
            
            // Terr kill ?
            if (shipTypeKilled == Tools.Ship.TERRIER && !location.equals(Location.SPACE) && !location.equals(Location.SPAWN)
                    && !location.equals(Location.ROOF)) {
                Integer terrKill = terrKills.get(player);
                if (terrKill == null)
                    terrKills.put(player, new Integer(1));
                else
                    terrKills.put(player, new Integer(terrKill.intValue() + 1));
            }

            // Bounty of the killed
            Integer bountyTotal = killsBounty.get(player);
            if (count == null)
                killsBounty.put(player, new Integer(bountyKilled));
            else
                killsBounty.put(player, new Integer(bountyTotal.intValue() + bountyKilled));

            // Weight of the kill
            int weight = 0;
            if (locationWeight.containsKey(location)) {
                weight = locationWeight.get(location);
                Integer currentWeight = killsLocationWeigth.get(player);
                if (currentWeight == null)
                    killsLocationWeigth.put(player, new Integer(weight));
                else
                    killsLocationWeigth.put(player, new Integer(currentWeight.intValue() + weight));
            }

            // Kill inside the base
            if (location.equals(Location.FLAGROOM) || location.equals(Location.MID)) {
                Integer total = killsInBase.get(player);
                if (total == null)
                    killsInBase.put(player, new Integer(1));
                else
                    killsInBase.put(player, new Integer(total.intValue() + 1));
            }

        }

        public void addPlayerDeath(String player) {
            Integer count = deaths.get(player);
            if (count == null)
                deaths.put(player, new Integer(1));
            else
                deaths.put(player, new Integer(count.intValue() + 1));
        }

        public void addTk(String player) {
            Integer count = tks.get(player);
            if (count == null)
                tks.put(player, new Integer(1));
            else
                tks.put(player, new Integer(count.intValue() + 1));
        }

        /**
         * Assigns flag (internally) to the claiming frequency.
         * 
         */
        public void assignFlag() {
            flagHoldingFreq = flagClaimingFreq;

            int remain = getTimeRemaining();

            Player p = m_botAction.getPlayer(claimerID);

            if (p != null) {

                addFlagClaim(p.getPlayerName());

                if (remain < 60)
                    if (remain < 4) {
                        m_botAction.sendArenaMessage("[FLAG] INCONCEIVABLE!!: " + p.getPlayerName() + " claims flag for "
                                + (flagHoldingFreq < 100 ? "Freq " + flagHoldingFreq : "priv. freq") + " with just " + remain + " second"
                                + (remain == 1 ? "" : "s") + " left!", 65);
                        m_botAction.showObject(2500);
                        m_botAction.showObject(2600);
                        m_botAction.sendPrivateMessage(p.getPlayerName(), "Wow!! I'll give you $1000 for this.");
                        context.getPlayerManager().addMoney(p.getPlayerName(), 1000);

                    } else if (remain < 11) {
                        m_botAction.sendArenaMessage("[FLAG] AMAZING!: " + p.getPlayerName() + " claims flag for "
                                + (flagHoldingFreq < 100 ? "Freq " + flagHoldingFreq : "priv. freq") + " with just " + remain + " sec. left!");
                        m_botAction.showObject(2600); // 'Daym!' lvz
                        m_botAction.sendPrivateMessage(p.getPlayerName(), "Not bad at all! I'll give you $500 for this.");
                        context.getPlayerManager().addMoney(p.getPlayerName(), 500);

                    } else if (remain < 26) {
                        m_botAction.sendArenaMessage("[FLAG] SAVE!: " + p.getPlayerName() + " claims flag for "
                                + (flagHoldingFreq < 100 ? "Freq " + flagHoldingFreq : "priv. freq") + " with " + remain + " sec. left!");
                        m_botAction.sendPrivateMessage(p.getPlayerName(), "Nice work. BONUS: $250");
                        context.getPlayerManager().addMoney(p.getPlayerName(), 250);
                    } else {
                        m_botAction.sendArenaMessage("[FLAG] Save: " + p.getPlayerName() + " claims flag for "
                                + (flagHoldingFreq < 100 ? "Freq " + flagHoldingFreq : "priv. freq") + " with " + remain + " sec. left.");
                    }
            }

            m_botAction.showObject(2400); // Shows flag claimed lvz
            isBeingClaimed = false;
            flagClaimingFreq = -1;
            secondsHeld = 0;

        }

        public int getTimeRemaining() {
            return (flagMinutesRequired * 60) - secondsHeld;
        }

        /**
         * Increments a count for player claiming the flag.
         * 
         * @param name
         *            Name of player.
         */
        public void addFlagClaim(String name) {
            Integer count = flagClaims.get(name);
            if (count == null)
                flagClaims.put(name, new Integer(1));
            else {
                flagClaims.remove(name);
                flagClaims.put(name, new Integer(count.intValue() + 1));
            }
        }

        /**
         * Ends the game for the timer's internal purposes.
         */
        public void endGame() {
            objs.hideAllObjects();
            m_botAction.setObjects();
            isRunning = false;
        }
        
        /**
         * Checks for non-LT terrs on public frequencies that are playing alone,
         * and gives a small bonus every X seconds.
         */
        public void checkTerrBonus() {
            Iterator<Player> i = m_botAction.getPlayerIterator();
            Player p, p2;
            int[] freqsize = {0,0,0};
            boolean[] foundTerr = {false,false,false};
            boolean lt = false, outside = false;
            Player[] terr = {null,null,null};
            while (i.hasNext()) {
                p = i.next();
                if (p != null && p.getShipType() != Tools.Ship.SPECTATOR) {
                    if (p.getFrequency() == 0 || p.getFrequency() == 1 || p.getFrequency() == hunterFreq) {
                        int freq = p.getFrequency();
                        if (freq == hunterFreq)
                            freq = 2;
                    
                        freqsize[freq]++;
                        if (p.getShipType() == Tools.Ship.TERRIER) {
                            
                            // LTs can't get the bonus
                            for ( Integer turretid : p.getTurrets() ) {
                                p2 = m_botAction.getPlayer(turretid);
                                if (p2 != null && p2.getShipType() == Tools.Ship.LEVIATHAN) {
                                    lt = true;
                                    break;
                                }
                            }
                            Location loc = context.getPubUtil().getLocation(p.getXTileLocation(), p.getYTileLocation());
                            if (loc != null)
                                if (freq == hunterFreq) {
                                    if (loc.equals(Location.SAFE) || loc.equals(Location.UNKNOWN) )
                                        outside = true;
                                } else {
                                    if (loc.equals(Location.SAFE) || loc.equals(Location.SPACE) || loc.equals(Location.UNKNOWN) )
                                        outside = true;                                    
                                }

                            if( !lt && !outside ) {
                                if( !foundTerr[freq] ) {
                                    terr[freq] = p;
                                    foundTerr[freq] = true;
                                } else {
                                    //2nd terr, so dump both of them
                                    terr[freq] = null;
                                }
                            }
                        }
                    }
                }
                lt = false;
                outside = false;
            }
            
            for (int freq=0; freq<2; freq++)
                if (terr[freq] != null && freqsize[freq] >= terrBonusMinOnFreq ) {
                    context.getPlayerManager().addMoney(terr[freq].getPlayerName(), terrBonusAmt);
                    m_botAction.sendPrivateMessage(terr[freq].getPlayerID(), "TERR BONUS: +$" + terrBonusAmt + "  (only non-LT Terr)" );
                }
            
            // Hunter freq: must have LT somewhere to give award
            if (terr[2] != null && freqsize[2] >= terrBonusMinOnFreq ) {
                boolean activeLT = false;
                for (LevTerr l : levterrs.values()) {
                    if (!l.isEmpty()) {
                        activeLT = true;
                        break;
                    }
                }
                if (activeLT) {
                    context.getPlayerManager().addMoney(terr[2].getPlayerName(), terrBonusAmt);
                    m_botAction.sendPrivateMessage(terr[2].getPlayerID(), "HUNTER TERR BONUS: +$" + terrBonusAmt + "  (only Terr on Hunter Freq chasing LTs)" );
                }                
            }
            
        }
        
        /**
         * Sends time info to requested player.
         * 
         * @param name
         *            Person to send info to
         */
        public void sendTimeRemaining(String name) {
            m_botAction.sendSmartPrivateMessage(name, getTimeInfo());
        }

        /**
         * @return True if a game is currently running; false if not
         */
        public boolean isRunning() {
            return isRunning;
        }

        /**
         * Gives the name of the top flag claimers out of the MVPs. If there is a tie, does not care because it's only bragging rights anyway. :P
         * 
         * @return Array of size 2, index 0 being the team leader and 1 being # flaggrabs
         */
        @SuppressWarnings("unused")
        public String[] getTeamLeader(HashSet<String> MVPs) {
            String[] leaderInfo = { "", "", "" };
            HashSet<String> ties = new HashSet<String>();

            if (MVPs == null)
                return leaderInfo;
            try {
                Iterator<String> i = MVPs.iterator();
                Integer dummyClaim, highClaim = new Integer(0);
                String leader = "", dummyPlayer;

                while (i.hasNext()) {
                    dummyPlayer = i.next();
                    dummyClaim = flagClaims.get(dummyPlayer);
                    if (dummyClaim != null)
                        if (dummyClaim.intValue() > highClaim.intValue()) {
                            leader = dummyPlayer;
                            highClaim = dummyClaim;
                            ties.clear();
                        } else if (dummyClaim.intValue() == highClaim.intValue())
                            ties.add(dummyPlayer);
                }
                leaderInfo[0] = leader;
                leaderInfo[1] = highClaim.toString();
                i = ties.iterator();
                while (i.hasNext())
                    leaderInfo[2] += i.next() + ", ";
                return leaderInfo;

            } catch (Exception e) {
                Tools.printStackTrace(e);
                return leaderInfo;
            }

        }

        /**
         * Returns number of flag grabs for given player.
         * 
         * @param name
         *            Name of player
         * @return Flag grabs
         */
        @SuppressWarnings("unused")
        public int getFlagGrabs(String name) {
            Integer grabs = flagClaims.get(name);
            if (grabs == null)
                return 0;
            else
                return grabs;
        }

        /**
         * Returns number of kill for given player.
         * 
         * @param name
         *            Name of player
         * @return Flag grabs
         */
        @SuppressWarnings("unused")
        public int getTotalKill(String name) {
            Integer count = kills.get(name);
            if (count == null)
                return 0;
            else
                return count;
        }

        /**
         * Returns number of death for given player.
         * 
         * @param name
         *            Name of player
         * @return Flag grabs
         */
        @SuppressWarnings("unused")
        public int getTotalDeath(String name) {
            Integer count = deaths.get(name);
            if (count == null)
                return 0;
            else
                return count;
        }

        /**
         * Returns number of lev kills for given player.
         * 
         * @param name
         *            Name of player
         * @return Flag grabs
         */
        @SuppressWarnings("unused")
        public int getTotalLevKills(String name) {
            Integer count = levKills.get(name);
            if (count == null)
                return 0;
            else
                return count;
        }
        
        /**
         * Returns number of terr kill for given player.
         * 
         * @param name
         *            Name of player
         * @return Flag grabs
         */
        @SuppressWarnings("unused")
        public int getTotalTerrKill(String name) {
            Integer count = terrKills.get(name);
            if (count == null)
                return 0;
            else
                return count;
        }

        /**
         * Returns kill weight for given player.
         * 
         * @param name
         *            Name of player
         * @return Flag grabs
         */
        @SuppressWarnings("unused")
        public int getKillWeight(String name) {
            Integer count = killsLocationWeigth.get(name);
            if (count == null)
                return 0;
            else
                return count;
        }

        /**
         * Returns number of ship change for given player.
         * 
         * @param name
         *            Name of player
         * @return Flag grabs
         */
        @SuppressWarnings("unused")
        public int getTotalShipChange(String name) {
            HashSet<Integer> count = ships.get(name);
            if (count == null)
                return 0;
            else
                return count.size();
        }

        /**
         * @return Time-based status of game
         */
        public String getTimeInfo() {
            int roundNum = freq0Score + freq1Score + 1;

            if (isRunning == false)
                if (roundNum == 1)
                    return "[FLAG] Round 1 of a new game will start soon.";
                else
                    return "[FLAG] Round " + roundNum + " starting soon. Score: " + freq0Score + "-" + freq1Score;
            return "[FLAG] ROUND " + roundNum + " (" + freq0Score + "-" + freq1Score + ")  " + (flagHoldingFreq == -1 || flagHoldingFreq > 99 ? "?" : "Freq " + flagHoldingFreq)
                    + " holding for " + getTimeString(secondsHeld) + ", needs " + getTimeString((flagMinutesRequired * 60) - secondsHeld)
                    + " more. [Time: " + getTimeString(totalSecs) + "]";
        }

        /**
         * @return Total number of seconds round has been running.
         */
        public int getTotalSecs() {
            return totalSecs;
        }

        /**
         * @return Frequency that currently holds the flag
         */
        public int getHoldingFreq() {
            return flagHoldingFreq;
        }

        /**
         * Timer running once per second that handles the starting of a round, displaying of information updates every 5 minutes, the flag claiming
         * timer, and total flag holding time/round ends.
         */
        @Override
        public void run() {
            if (isStarted == false) {
                int roundNum = freq0Score + freq1Score + 1;
                if (preTimeCount == 0) {
                    m_botAction.sendArenaMessage("[FLAG] Next round begins in 10 seconds . . .");
                    m_botAction.showObject(LVZ_10TOSTART);
                    if (strictFlagTimeMode)
                        safeWarp();
                }
                preTimeCount++;
                
                if (preTimeCount == 9 && !strictFlagTimeMode ) {
                    clearFR();
                }

                if (preTimeCount >= 10) {
                    String message = "[FLAG] " + (roundNum == MAX_FLAGTIME_ROUNDS ? "FINAL ROUND" : "ROUND " + roundNum) + " START!  Hold flag for "
                            + flagMinutesRequired + " consecutive minute" + (flagMinutesRequired == 1 ? "" : "s") + " to win.";
                    //int sound = strictFlagTimeMode ? Tools.Sound.GOGOGO : Tools.Sound.BEEP1;
                    //m_botAction.sendArenaMessage(message, sound);
                    m_botAction.sendArenaMessage(message);
                    setupPlayerTimes();
                    warpPlayers(strictFlagTimeMode);
                    m_botAction.resetFlagGame();
                    Iterator<?> i = m_botAction.getPlayingPlayerIterator();
                    while (i.hasNext()) {
                        Player p = (Player) i.next();
                        flagTimer.newShip(p.getPlayerName(), p.getShipType());
                    }
                    
                    // Clear any round restricted buyable items/commands
                    context.getMoneySystem().resetRoundRestrictions();
                    isStarted = true;
                    isRunning = true;
                    
                    return;
                }
            }

            if (isRunning == false)
                return;

            totalSecs++;

            // Display mode info at 5 min increments, unless we are near the end of a game;
            //   also check for severe freq imbalance every minute
            if ((flagMinutesRequired * 60) - secondsHeld > 30) {
                if (totalSecs % (10 * 60) == 0) { 
                    m_botAction.sendArenaMessage(getTimeInfo());
                    context.getPlayerManager().checkSpecTime();
                }
                if (totalSecs % 30 == 0)
                    context.getPlayerManager().checkFreqSizes(true);
            }


            if (isBeingClaimed) {
                claimSecs++;
                if (claimSecs >= FLAG_CLAIM_SECS) {
                    claimSecs = 0;
                    assignFlag();
                }
                return;
            }
            
            if (giveTerrBonus && totalSecs % terrBonusFrequency == 0) {
                checkTerrBonus();
            }

            Integer freqSecs = freqsSecs.get(flagHoldingFreq);
            if (freqSecs == null)
                freqSecs = new Integer(0);
            freqsSecs.put(flagHoldingFreq, freqSecs + 1);

            if (flagHoldingFreq == -1)
                return;

            secondsHeld++;

            do_updateTimer();

            int flagSecsReq = flagMinutesRequired * 60;
            if (secondsHeld >= flagSecsReq) {
                endGame();
                doEndRoundNew();
            } else if (flagSecsReq - secondsHeld == 60) {
                m_botAction.showObject(LVZ_60TOWIN);
                //m_botAction.sendArenaMessage("[FLAG] " + (flagHoldingFreq < 100 ? "Freq " + flagHoldingFreq : "Private freq") + " will win in 60 seconds.");
            } else if (flagSecsReq - secondsHeld == 30) {
                m_botAction.showObject(LVZ_30TOWIN);                
            } else if (flagSecsReq - secondsHeld == 10) {
                m_botAction.showObject(LVZ_10TOWIN);                
                m_botAction.sendArenaMessage("[FLAG] " + (flagHoldingFreq < 100 ? "Freq " + flagHoldingFreq : "Private freq") + " will win in 10 seconds . . .");
            } else if (flagSecsReq - secondsHeld < 9 && ((flagSecsReq - secondsHeld) % 2) == 0) {
                m_botAction.showObject(LVZ_WINIMMINENT);
            }
        }

        /**
         * Runs the LVZ-based timer.
         */
        private void do_updateTimer() {
            int secsNeeded = flagMinutesRequired * 60 - secondsHeld;
            objs.hideAllObjects();
            int minutes = secsNeeded / 60;
            int seconds = secsNeeded % 60;
            if (minutes < 1)
                objs.showObject(1100);
            if (minutes > 10)
                objs.showObject(10 + ((minutes - minutes % 10) / 10));
            objs.showObject(20 + (minutes % 10));
            objs.showObject(30 + ((seconds - seconds % 10) / 10));
            objs.showObject(40 + (seconds % 10));
            m_botAction.setObjects();
        }
    }

    /**
     * This private class starts the round.
     */
    private class StartRoundTask extends TimerTask {

        /**
         * Starts the round when scheduled.
         */
        @Override
        public void run() {
            doStartRound();
        }
    }

    /**
     * This private class provides a pause before starting the round.
     */
    private class IntermissionTask extends TimerTask {

        /**
         * Starts the intermission/rule display when scheduled.
         */
        @Override
        public void run() {
            doIntermission();
            m_botAction.showObject(1000); //Shows intermission lvz
        }
    }

    /**
     * Used to turn on/off a set of LVZ objects at a particular time.
     */
    private class AuxLvzTask extends TimerTask {
        public int[] objNums;
        public boolean[] showObj;

        /**
         * Creates a new AuxLvzTask, given obj numbers defined in the LVZ and whether or not to turn them on or off. Cardinality of the two arrays
         * must be the same.
         * 
         * @param objNums
         *            Numbers of objs defined in the LVZ to turn on or off
         * @param showObj
         *            For each index, true to show the obj; false to hide it
         */
        public AuxLvzTask(int[] objNums, boolean[] showObj) {
            if (objNums.length != showObj.length)
                throw new RuntimeException("AuxLvzTask constructor error: Arrays must have same cardinality.");
            this.objNums = objNums;
            this.showObj = showObj;
        }

        /**
         * Shows and hides set objects.
         */
        @Override
        public void run() {
            for (int i = 0; i < objNums.length; i++)
                if (showObj[i])
                    m_botAction.showObject(objNums[i]);
                else
                    m_botAction.hideObject(objNums[i]);
        }
    }

    private class LevTerr {

        public String terrierName;
        public List<String> leviathans;
        public boolean allowAlert = true;
        public long levvingSince;

        public LevTerr(String terrierName) {
            this.terrierName = terrierName;
            leviathans = new ArrayList<String>();
        }

        public void removeLeviathan(String name) {
            leviathans.remove(name);
        }

        public void addLeviathan(String name) {
            if (isEmpty())
                levvingSince = System.currentTimeMillis();
            leviathans.add(name);
        }

        public boolean isEmpty() {
            return leviathans.isEmpty();
        }

        public List<String> getLeviathans() {
            return leviathans;
        }

        public boolean alertAllowed() {
            return allowAlert;
        }

        /**
         * @param b True if the bot can alert the arena to the LT's presence
         */
        public void allowAlert(boolean b) {
            allowAlert = b;
        }
    }
}
