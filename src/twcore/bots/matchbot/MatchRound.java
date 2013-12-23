package twcore.bots.matchbot;

/*
 * MatchRound.java
 *
 * Created on August 19, 2002, 10:51 PM
 */

/**
 *
 * @author  Administrator
 */

import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Random;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.events.BallPosition;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FlagReward;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.Prize;
import twcore.core.events.SoccerGoal;
import twcore.core.events.TurretEvent;
import twcore.core.events.WeaponFired;
import twcore.core.game.Player;
import twcore.core.lvz.Objset;
import twcore.core.stats.LagReport;
import twcore.core.stats.lagHandler;
import twcore.core.util.MapRegions;
import twcore.core.util.Point;
import twcore.core.util.Tools;
import twcore.core.util.ipc.EventType;
import twcore.core.util.ipc.IPCTWD;
import twcore.core.util.json.JSONArray;
import twcore.core.util.json.JSONValue;

public class MatchRound {
    
    private static final int[] DD_AREA = {706 - 319, 655 - 369, 319, 369};
    //private static final int[] DD_AREA = {(706-64) - (319+64), (655-47) - (369+47), (319+64), (369+47)};
    private static final int[] DD_WARP = {512-10, 256-5, 512+10, 256+5};
    private static final String MAP_NAME = "duel";
    private MapRegions regions;
    
    private Random rand;
    private boolean spawnAlert;
    private boolean shields;
    private int maxCount;
    private int radius;

    // holds one round: both teams
    boolean useDatabase;

    Connection m_connection;
    BotAction m_botAction;

    String dbConn = "website";

    MatchGame m_game;
    BotSettings m_rules;
    MatchLogger m_logger;

    // 0 - none, 1 - arranging lineup, 2 - starting, 3 - playing, 4 - finished
    int m_fnRoundState;

    // 0 - still playing, 1 - won by one of the teams, 2 - draw, 3 - cancelled
    int m_fnRoundResult;

    int m_fnMatchRoundID;
    int m_fnRoundNumber;
    int m_fnTeam1Score;
    int m_fnTeam2Score;
    MatchTeam m_team1;
    MatchTeam m_team2;
    java.util.Date m_timeStarted;
    java.util.Date m_timeEnded;
    long m_timeBOEnabled;
    long m_timeStartedms;
    TimerTask m_countdown10Seconds;
    TimerTask m_countdown54321;
    TimerTask m_startGame;
    TimerTask m_endGame;
    TimerTask m_endTieGame;
    TimerTask m_scheduleTimer;
    TimerTask m_signalEndOfRound;
    TimerTask m_announceMVP;
    TimerTask m_closeDoors;
    TimerTask m_moveAround;
    TimerTask m_secondWarp;
    TimerTask updateScores;
    HashMap<String,Long> m_notPlaying;
    private long timeBetweenNPs = (Tools.TimeInMillis.SECOND * 45);

    JSONArray events; // array of MatchRoundEvent

    private Objset m_scoreBoard;
    private int m_generalTime = 0;

    public lagHandler m_lagHandler;

    boolean m_fbAffectsEntireGame = false;
    boolean m_fbExtension = false;
    boolean m_fbExtensionUsed = false;
    // -1 - unknown;  0 - off; 1 - on
    boolean m_blueoutState = false;
    boolean waitingOnBall = false;

    // this is for lagchecking:
    String m_lagPlayerName;
    int m_lagTeam;

    int m_fnAliveCheck = (int) System.currentTimeMillis();

    //time race variables
    private int m_raceTarget = 0;
    TimerTask m_raceTimer;

    // TWSDX ONLY: Flag LVZ status
    boolean flagClaimed = false;

    static final int NOT_PLAYING_FREQ = 200;

    /** Creates a new instance of MatchRound */
    public MatchRound(int fnRoundNumber, String fcTeam1Name, String fcTeam2Name, MatchGame Matchgame) {
        rand = new Random();
        useDatabase = false;
        spawnAlert = false;
        m_game = Matchgame;
        m_botAction = m_game.m_botAction;
        m_scoreBoard = m_botAction.getObjectSet();
        m_rules = m_game.m_rules;
        m_fnRoundNumber = fnRoundNumber;
        m_fnRoundState = 0;
        m_fnRoundResult = 0;
        m_timeStarted = new java.util.Date();
        m_logger = m_game.m_logger;
        
        radius = m_rules.getInt("radius");
        maxCount = m_rules.getInt("maxcount");
        shields = m_rules.getInt("shields") == 1;
        
        regions = m_game.m_bot.regions;
        reloadRegions();

        events = new JSONArray();

        if (!m_rules.getString("name").equalsIgnoreCase("strikeball")) {
            m_team1 = new MatchTeam(fcTeam1Name, (int) Math.floor(Math.random() * 8000 + 1000), 1, this);
            m_team2 = new MatchTeam(fcTeam2Name, (int) Math.floor(Math.random() * 8000 + 1000), 2, this);
        } else {
            m_team1 = new MatchTeam(fcTeam1Name, 0, 1, this);
            m_team2 = new MatchTeam(fcTeam2Name, 1, 2, this);
        }

        m_lagHandler = new lagHandler(m_botAction, m_rules, this, "handleLagReport");

        m_notPlaying = new HashMap<String,Long>();

        Iterator<Player> iterator = m_botAction.getPlayerIterator();
        Player player;

        while (iterator.hasNext()) {
            player = iterator.next();
            if (player.getFrequency() == NOT_PLAYING_FREQ) {
                m_notPlaying.put(player.getPlayerName().toLowerCase(), System.currentTimeMillis());
            }
        }

        if (m_rules.getInt("pickbyturn") == 0) {
            //This is for the time race.  If the person hasn't set the time it is set to default
            String winby = m_rules.getString("winby");
            if (winby.equals("timerace") && (m_raceTarget < 5 * 60 || m_raceTarget > 30 * 30)) // 5 mins and 30 mins in secs
            {
                setRaceTarget(m_rules.getInt("defaulttarget") * 60); //mins to secs
                m_logger.sendArenaMessage("Race set to " + m_rules.getInt("defaulttarget") + " mins");
            }

            m_fnRoundState = 1;
            m_logger.sendArenaMessage("Captains, you have " + m_rules.getInt("lineuptime") + " minutes to set up your lineup correctly");
            m_scheduleTimer = new TimerTask() {
                public void run() {
                    scheduleTimeIsUp();
                };
            };
            m_botAction.scheduleTask(m_scheduleTimer, 60000 * m_rules.getInt("lineuptime"));
            m_botAction.setTimer(m_rules.getInt("lineuptime"));
        }
        specAll();
    }
    
    private void reloadRegions() {
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

    private void specAll() {
        Iterator<Player> iterator = m_botAction.getPlayerIterator();
        Player player;
        int specFreq = getSpecFreq();

        while (iterator.hasNext()) {
            player = iterator.next();
            if (!player.isPlaying() && player.getFrequency() != specFreq && player.getFrequency() != NOT_PLAYING_FREQ)
                placeOnSpecFreq(player.getPlayerName());
        }
        m_botAction.specAll();
    }

    private int getSpecFreq() {
        String botName = m_botAction.getBotName();
        Player bot = m_botAction.getPlayer(botName);
        if (bot == null)
            return 9999;

        placeOnSpecFreq(botName);
        return bot.getFrequency();
    }

    private void placeOnSpecFreq(String playerName) {
        m_botAction.setShip(playerName, 1);

        //repeated because players already on spec frequency need to be put back in
        //you have to do *spec twice.
        m_botAction.spec(playerName);
        m_botAction.spec(playerName);
    }

    /*
     * Create a database record for the round
     */
    public void storeRoundResult() {
        int fnMatchID = m_game.m_fnMatchID;
        int roundstate = 0;
        try {
            if (m_fnRoundResult == 1)
                roundstate = 3;
            if (m_fnRoundResult == 2)
                roundstate = 5;
            if (m_fnRoundResult == 3)
                roundstate = 1;
            String started = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(m_timeStarted);
            String ended = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(m_timeEnded);
            String[] fields = { "fnMatchID", "fnRoundStateID", "ftTimeStarted", "ftTimeEnded", "fnTeam1Score", "fnTeam2Score" };

            String[] values = { Integer.toString(fnMatchID), Integer.toString(roundstate), started, ended, Integer.toString(m_fnTeam1Score),
                    Integer.toString(m_fnTeam2Score) };

            m_botAction.SQLInsertInto(dbConn, "tblMatchRound", fields, values);

            ResultSet s = m_botAction.SQLQuery(dbConn, "select MAX(fnMatchRoundID) as fnMatchRoundID from tblMatchRound");
            if (s.next()) {
                m_fnMatchRoundID = s.getInt("fnMatchRoundID");
                m_team1.storePlayerResults();
                m_team2.storePlayerResults();
            }
            m_botAction.SQLClose(s);

            String[] fields2 = { "fnMatchID", "fnMatchRoundID", "fcEvent" };

            String[] values2 = { Integer.toString(fnMatchID), Integer.toString(m_fnMatchRoundID), JSONValue.escape(JSONValue.toJSONString(events)) };

            m_botAction.SQLBackgroundInsertInto(dbConn, "tblMatchRoundExtra", fields2, values2);

        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    /**
     * Can get various weapon info and the player who used it
     * Get repel used count
     *
     * @param event WeaponFired event
     */
    public void handleEvent(WeaponFired event) {
        try {
            if (m_fnRoundState == 3) {
                String playerName = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();
                if (m_team1.getPlayer(playerName, true) != null)
                    m_team1.handleEvent(event);
                if (m_team2.getPlayer(playerName, true) != null)
                    m_team2.handleEvent(event);
            }
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void handleEvent(Prize event) {
        try {
            if (m_fnRoundState == 3) {
                String playerName = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();
                if (m_team1.getPlayer(playerName, true) != null)
                    m_team1.handleEvent(event);
                if (m_team2.getPlayer(playerName, true) != null)
                    m_team2.handleEvent(event);
            }
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void handleEvent(TurretEvent event) {
        try {
            if (m_fnRoundState == 3 && event.isAttaching()) {

                String playerName = m_botAction.getPlayer(event.getAttacherID()).getPlayerName();
                if (m_team1.getPlayer(playerName, true) != null)
                    m_team1.handleEvent(event);
                if (m_team2.getPlayer(playerName, true) != null)
                    m_team2.handleEvent(event);
            }
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    /**
     * Parses the FrequencyShipChange event to the team in which the player is
     *
     * @param event FrequencyShipChange event
     */
    public void handleEvent(FrequencyShipChange event) {
        try {
            String playerName = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();
            if (m_team1.getPlayer(playerName, true) != null)
                m_team1.handleEvent(event);
            if (m_team2.getPlayer(playerName, true) != null)
                m_team2.handleEvent(event);

            if ((m_team1.isDead() || m_team2.isDead()) && (m_fnRoundState == 3))
                endGame();
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void handleEvent(PlayerEntered event) {

        if (m_blueoutState) {
            m_botAction.sendPrivateMessage(event.getPlayerID(), "This game has blueout enabled.");
        }

        Long exists = m_notPlaying.get(event.getPlayerName().toLowerCase());
        if (exists != null) {
            m_botAction.spec(event.getPlayerName());
            m_botAction.spec(event.getPlayerName());
            m_logger.sendPrivateMessage(event.getPlayerName(), "notplaying mode is still on, captains will be unable to pick you");
            m_logger.setFreq(event.getPlayerName(), NOT_PLAYING_FREQ);
        }

        // TWSDX ONLY:
        if (m_fnRoundState == 3 && m_game.m_fnMatchTypeID == 13 && flagClaimed) {
            m_botAction.showObjectForPlayer(event.getPlayerID(), 744);
        }
        // TWSDX ONLY: Let the player know about the !rules command
        if (m_game.m_fnMatchTypeID == 13) {
            m_botAction.sendPrivateMessage(event.getPlayerID(), "Private message me with \"!rules\" for an explanation on how to play TWSD");
        }
    }

    /*
     * Parses the FlagReward event to the correct team
     */
    public void handleEvent(FlagReward event) {
        if (m_fnRoundState == 3) {
            int freq = event.getFrequency();
            if (m_team1.getFrequency() == freq)
                m_team1.flagReward(event.getPoints());
            if (m_team2.getFrequency() == freq)
                m_team2.flagReward(event.getPoints());
            if (m_team1.wonRace() || m_team2.wonRace())
                endGame();
        }
    }

    /**
     * Parses the FlagClaimed event to the correct team
     * It also check if any of the teams won the race
     *
     * @author Force of Nature
     * @param event The flagClaimed event holding the playerId claiming the flag
     */
    @SuppressWarnings("unchecked")
    public void handleEvent(FlagClaimed event) {
        if (m_fnRoundState == 3) {
            Player player = m_botAction.getPlayer(event.getPlayerID());
            int freq = player.getFrequency();

            // TWSD ONLY:
            if (m_game.m_fnMatchTypeID == 13) {

                if (flagClaimed == false) {
                    // the flag was claimed for the first time, put the spider logo on the phantom flag
                    m_botAction.showObject(744);
                    flagClaimed = true;
                    // restart player cycling
                    m_botAction.resetReliablePositionUpdating();
                }
            }

            if (m_team1.getFrequency() == freq) {
                m_team2.disownFlag(); // Disown flag before reowning
                m_team1.ownFlag(event.getPlayerID()); // .. or else own will not register. -qan 10/04

                MatchPlayer p = getPlayer(player.getPlayerName());
                if (p != null && m_rules.getInt("storegame") != 0)
                    events.add(MatchRoundEvent.flagTouch(1, p.m_dbPlayer.getUserID()));
            }

            if (m_team2.getFrequency() == freq) {
                m_team1.disownFlag();
                m_team2.ownFlag(event.getPlayerID());

                MatchPlayer p = getPlayer(player.getPlayerName());
                if (p != null && m_rules.getInt("storegame") != 0)
                    events.add(MatchRoundEvent.flagTouch(2, p.m_dbPlayer.getUserID()));
            }
        }
    }

    /*
     * Checks if lag reports are sent (in the shape of an arenamessage)
     */
    public void handleEvent(Message event) {

        if (event.getMessageType() == Message.ARENA_MESSAGE) {
            String msg = event.getMessage();

            if (m_fnRoundState == 1) {
                m_team1.handleEvent(event);
                m_team2.handleEvent(event);
            }

            if ((m_fnRoundState == 2 || m_fnRoundState == 3) && msg.contains("Res:")) {
                m_team1.handleEvent(event);
                m_team2.handleEvent(event);
            }

            m_lagHandler.handleLagMessage(msg);

            /*
                        if (msg.startsWith("IP:")) {
                            String[] pieces = msg.split("  ");
                            String pName = pieces[3].substring(10);
                            if (m_team1.getPlayer(pName, true) != null) m_team1.reportLaggerName(pName);
                            if (m_team2.getPlayer(pName, true) != null) m_team2.reportLaggerName(pName);
                        } else if (msg.startsWith("Ping:")) {
                            String[] pieces = msg.split("  ");
                            String lag = pieces[3].substring(8);
                            lag = lag.substring(0, lag.length()-2);
                            try {
                                int msPing = Integer.parseInt(lag);
                                m_team1.reportLaggerLag(msPing);
                                m_team2.reportLaggerLag(msPing);
                            } catch (Exception e) {
                            };
                        };
            */

            if (msg.equals("Public Messages LOCKED")) {
                if (!m_blueoutState)
                    m_botAction.toggleLockPublicChat();
            } else if (msg.equals("Public Messages UNLOCKED")) {
                if (m_blueoutState)
                    m_botAction.toggleLockPublicChat();
            }
        }
        if ((event.getMessageType() == Message.PUBLIC_MESSAGE) && (m_blueoutState) && (m_endGame != null) && this.m_fnRoundState >= 2)//&& (System.currentTimeMillis() - m_timeBOEnabled > 5000))
        {
            String name = m_botAction.getPlayerName(event.getPlayerID());
            m_botAction.sendCheaterMessage(name + " talking in blueout: " + name + "> " + event.getMessage());

            if (m_botAction.getOperatorList().isZH(name)) {
                m_botAction.warnPlayer(event.getPlayerID(), "Do not talk during blueout! Powers revoked, relogin to restore them.");
                m_botAction.sendUnfilteredPrivateMessage(event.getPlayerID(), "*moderator");
            } else {
                m_botAction.warnPlayer(event.getPlayerID(), "Do not talk during blueout!");
            }
        }
    }

    /*
     * Parses the PlayerDeath event to the team in which the player is
     */
    @SuppressWarnings("unchecked")
    public void handleEvent(PlayerDeath event) {
        if (m_fnRoundState == 3) {
            try {
                Player killee = m_botAction.getPlayer(event.getKilleeID());
                Player killer = m_botAction.getPlayer(event.getKillerID());

                String killeeName = killee.getPlayerName();
                String killerName = killer.getPlayerName();

                // Distance between the killer and killee
                double distance = Math.sqrt(Math.pow(killer.getXLocation() - killee.getXLocation(), 2)
                        + Math.pow(killer.getYLocation() - killee.getYLocation(), 2)) / 16;

                MatchPlayer p1 = getPlayer(killerName);
                MatchPlayer p2 = getPlayer(killeeName);

                if (p1 != null && p2 != null) {

                    if (m_rules.getInt("storegame") != 0) {
                        events.add(MatchRoundEvent.death(p2.m_dbPlayer.getUserID(), p1.m_dbPlayer.getUserID(), p2.getShipType(), p1.getShipType()));
                        events.add(MatchRoundEvent.kill(p1.m_dbPlayer.getUserID(), p2.m_dbPlayer.getUserID(), p1.getShipType(), p2.getShipType()));
                    }

                    // count only if not on the same team
                    if (p1.m_team.m_fnTeamNumber != p2.m_team.m_fnTeamNumber) {
                        p1.reportKillShotDistance(distance);
                    }
                }

                if (m_team1.getPlayer(killeeName, true) != null)
                    m_team1.handleEvent(event, killerName, killer.getShipType());
                if (m_team2.getPlayer(killeeName, true) != null)
                    m_team2.handleEvent(event, killerName, killer.getShipType());
                if (m_team1.getPlayer(killerName, true) != null)
                    m_team1.reportKill(event, killeeName, killee.getShipType());
                if (m_team2.getPlayer(killerName, true) != null)
                    m_team2.reportKill(event, killeeName, killee.getShipType());

                // player out?
                if (p1 != null && p2 != null && p2.m_fnPlayerState == 4) {
                    if (m_rules.getInt("storegame") != 0)
                        events.add(MatchRoundEvent.eliminated(p2.m_dbPlayer.getUserID(), p1.m_dbPlayer.getUserID()));
                    p2.reportKO(killerName);
                }

                if (m_team1.isDead() || m_team2.isDead())
                    endGame();
                if (m_team1.wonRace() || m_team2.wonRace())
                    endGame();

                // TWSD ONLY:
                if (m_game.m_fnMatchTypeID == 13) {

                    if (m_team1.hasFlag() && m_team1.getPlayer(killeeName, true) != null && event.getKilleeID() == m_team1.getFlagCarrier()) {
                        // team1 had the flag and the killed one was from team1 and it was the flagcarrier, now the killer is the flagcarrier.
                        m_team1.disownFlag();
                        m_team2.ownFlag(event.getKillerID());
                    }
                    if (m_team2.hasFlag() && m_team2.getPlayer(killeeName, true) != null && event.getKilleeID() == m_team2.getFlagCarrier()) {
                        // team2 had the flag the killed one was from team2 and it was the flagcarrier, now the killer is the flagcarrier.
                        m_team2.disownFlag();
                        m_team1.ownFlag(event.getKillerID());
                    }

                    // Further check in case above goes wrong
                    Iterator<Player> players = m_botAction.getPlayingPlayerIterator();
                    while (players.hasNext()) {
                        Player player = players.next();
                        if (player.getFlagsCarried() > 0) {
                            if (m_team1.getPlayer(player.getPlayerName(), true) != null && m_team1.hasFlag() == false) {
                                // Flagcarrier is in team 1 but team 1 doesn't have the flag - omg! fix this bad situation
                                m_team2.disownFlag();
                                m_team1.ownFlag(player.getPlayerID());
                            } else if (m_team2.getPlayer(player.getPlayerName(), true) != null && m_team2.hasFlag() == false) {
                                // Flagcarrier is in team 2 but team 2 doesn't have the flag - omg! fix this bad situation
                                m_team1.disownFlag();
                                m_team2.ownFlag(player.getPlayerID());
                            }
                        }
                    }

                }
            } catch (Exception e) {
                Tools.printStackTrace(e);
            }
        }
    }

    /*
     * Parses the SoccerGoal event to the team in which the player is
     */
    public void handleEvent(SoccerGoal event) {
        try {
            int freq = event.getFrequency();

            if (freq == 0) {
                m_fnTeam1Score++;
                if (m_fnTeam1Score >= m_rules.getInt("goals"))
                    endGame();
            }
            if (freq == 1) {
                m_fnTeam2Score++;
                if (m_fnTeam2Score >= m_rules.getInt("goals"))
                    endGame();
            }
            System.out.println("Goal by: " + freq);
            System.out.println("Score: " + m_fnTeam1Score + "-" + m_fnTeam2Score);
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void handleEvent(BallPosition event) {
        if (waitingOnBall) {
            startGame();
            waitingOnBall = false;
        }
    }

    /*
     * Parses the PlayerLeft event to the team in which the player is
     */
    public void handleEvent(PlayerLeft event) {
        String playerName = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();
        if (m_team1.getPlayer(playerName, true) != null)
            m_team1.handleEvent(event);
        if (m_team2.getPlayer(playerName, true) != null)
            m_team2.handleEvent(event);

        if (m_team1.isDead() || m_team2.isDead())
            endGame();
    };

    public void handleEvent(PlayerPosition event) {
        /* for round state 2:
         * since the bot is at 0,0. The only position packets it receives are those
         * of player warps, which is illegal when the game is starting
         */
        if (m_fnRoundState == 2) {
            Player p = m_botAction.getPlayer(event.getPlayerID());
            if (p == null)
                return;
            String playerName = p.getPlayerName();
            m_logger.announce("warping " + playerName + " to his team's safe zone");
            if ((m_team1.getPlayer(playerName, true) != null) && (event.getXLocation() / 16 != m_rules.getInt("safe1x"))
                    && (event.getYLocation() / 16 != m_rules.getInt("safe1y")))
                m_botAction.warpTo(event.getPlayerID(), m_rules.getInt("safe1x"), m_rules.getInt("safe1y"));
            if ((m_team2.getPlayer(playerName, true) != null) && (event.getXLocation() / 16 != m_rules.getInt("safe2x"))
                    && (event.getYLocation() / 16 != m_rules.getInt("safe2y")))
                m_botAction.warpTo(event.getPlayerID(), m_rules.getInt("safe2x"), m_rules.getInt("safe2y"));
        } else if (m_fnRoundState == 3) {
            /* for round state 3:
             * bot centers at (centeratx, centeraty), checks if a person is longer than (outofbordertime) below (bordery)
             */
            /*
             * if (position OUTSIDE border) then
             *   if (outsidestart == 0) then outsidestart = systemtimems
             *   else if (systemtimems - outside start) > outofbordertime*500 then give_warning
             *   else if (systemtimems - outside start) > outofbordertime*1000 then kick_out_of_game
             */
            
            // this is the warper for DD's (not to be used until after twl 13')
            if (m_game.m_fnMatchTypeID == 9) {
                int y = event.getYLocation() / 16;
                if (y < DD_WARP[3]) {
                    int[] xy = getSafeSpawnPoint(event.getPlayerID());
                    if (xy != null) {
                        if (shields)
                            m_botAction.specificPrize(event.getPlayerID(), 18);
                        m_botAction.warpTo(event.getPlayerID(), xy[0], xy[1]);
                        //m_botAction.sendPublicMessage("Warped [" + m_botAction.getPlayerName(event.getPlayerID()) + "] to " + x + " " + y);
                    }
                }
                
            } else if (m_rules.getInt("yborder") != 0) {
                //int xpos = event.getXLocation() / 16;
                int ypos = event.getYLocation() / 16;
                int yborder = m_rules.getInt("yborder");
                int outofbordertime = m_rules.getInt("outofbordertime") * 1000;
                String playerName = m_botAction.getPlayerName(event.getPlayerID());

                if (playerName == null)
                    return;

                if (ypos > yborder) {
                    MatchPlayer p = m_team1.getPlayer(playerName);
                    MatchPlayer p2 = m_team2.getPlayer(playerName);

                    // We can't settle for whichever one -- have to find the best match
                    if (p != null && p2 != null) {
                        if (p2.getPlayerName().equalsIgnoreCase(playerName)) {
                            p = p2;
                        }
                    } else if (p == null) {
                        p = p2;
                    }

                    if (p != null) {
                        if (p.getPlayerState() == MatchPlayer.IN_GAME) {
                            long pSysTime = p.getOutOfBorderTime(), sysTime = System.currentTimeMillis();

                            if (pSysTime == 0)
                                p.setOutOfBorderTime();
                            else if (((sysTime - pSysTime) > outofbordertime / 2) && (!p.hasHalfBorderWarning())
                                    && ((sysTime - pSysTime) < outofbordertime)) {
                                p.setHalfBorderWarning();
                                m_logger.sendPrivateMessage(playerName, "Go to base! You have " + outofbordertime / 2000
                                        + " seconds before you'll get a +1 death added!", 26);
                            } else if ((sysTime - pSysTime) > outofbordertime) {
                                // m_rules.getInt() will return 0 by default if the rule doesn't exist.
                                // But 1 is the default value wanted, this is why we check if the rule is not null
                                if (m_rules.getString("outifexceed") != null && m_rules.getInt("outifexceed") == 0) {
                                    m_botAction.sendArenaMessage(playerName + " has been given 1 death for being out of base too long.");
                                    p.reportDeath();
                                } else {
                                    p.kickOutOfGame();
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private int[] getSafeSpawnPoint(int pid) {
        Player p = m_botAction.getPlayer(pid);
        if (p == null || p.getShipType() == 0)
            return null;
        
        if (spawnAlert)
            m_botAction.sendPrivateMessage(pid, "Warp/spawn detected! You will respawn momentarily...");
        
        boolean safe = false;
        int x = DD_WARP[0];
        int y = DD_WARP[1];
        
        int count = 0;
        
        while (!safe && count < maxCount) {
            safe = true;
            count++;
            Point xy = getRegionPoint();
            x = xy.x;
            y = xy.y;
            int freq = p.getFrequency();
            Iterator<Player> i = m_botAction.getPlayingPlayerIterator();
            while (i.hasNext() && safe) {
                p = i.next();
                if (p.getFrequency() != freq) {
                    int[] nme = {p.getXLocation()/16, p.getYLocation()/16};
                    if (nme[0] > radius - x && nme[0] < radius + x && nme[1] > radius - y && nme[1] < radius + y)
                        safe = false;
                }
            }
        }
        
        if (count >= maxCount)
            m_botAction.sendPrivateMessage(pid, "WARNING: Spawn may be unsafe since no safe spot was found in " + count + " attempts.");
        
        return new int[] {x, y};
    }
    
    private Point getRegionPoint() {
        int count = 0;
        int x = 512;
        int y = x;
        while (count < 10000) {
            x = rand.nextInt(DD_AREA[0]) + DD_AREA[2];
            y = rand.nextInt(DD_AREA[1]) + DD_AREA[3];
            if (regions.checkRegion(x,y, 0))
                return new Point(x, y);
        }
        return new Point(512, 512);
    }

    public MatchPlayer getPlayer(String playerName) {

        if (m_fnRoundState == 3) {

            MatchPlayer p;
            p = m_team1.getPlayer(playerName);
            if (p == null)
                p = m_team2.getPlayer(playerName);

            return p;

        }

        return null;

    }

    /*
     * Collects the available help messages for the player of this class and
     * the subclasses
     */
    public ArrayList<String> getHelpMessages(String name, boolean isStaff) {
        ArrayList<String> help = new ArrayList<String>();

        // for everybody
        if (m_rules.getInt("playerclaimcaptain") == 1)
            help.add("!cap                                     - Claim captainship of a team / shows current captains");
        else
            help.add("!cap                                     - Show the captains of both teams");

        help.add("!myfreq                                  - sets you on your team's frequency (short: !mf)");

        if ((m_fnRoundState <= 1) && (m_rules.getInt("pickbyturn") == 1))
            help.add("!notplaying                              - Indicate that you won't play this round");
        help.add("!notplaylist                             - Show all the players who have turned '!notplaying' on (short: !np)");
        if (m_fnRoundState == 3) {
            help.add("!score                                   - Show the current score of both teams");
            help.add("!rating <player>                         - provides realtime stats and rating of the player");
            help.add("!mvp                                     - provides the current mvp");
            help.add("!target (!t)                             - Shows enemy player with the highest deaths");
        }

        // TWSDX ONLY
        if (m_game.m_fnMatchTypeID == 13) {
            help.add("!rules                                   - How to play TWSD");
        }

        // for staff
        if (isStaff) {
            if ((m_fnRoundState == 0) && (m_rules.getInt("pickbyturn") == 1)) {
                help.add("!settime <time in mins>                  - time to racebetween 5 and 30 only for timerace");
                help.add("!startpick                               - start rostering");
            }
            help.add("!lag <player>                            - show <player>'s lag");
            help.add("!startinfo                               - shows who started this game");
            if (m_game.m_fnMatchTypeID == 9) {
                help.add("!radius <tiles> <count>                  - Set spawn radius to <tiles>, max <count> optional");
                help.add("!shields                                 - Toggle shields prized on spawn");
                help.add("!alert                                   - Toggle the spawn detection alert pm");
            }
            if (m_team1 != null) {
                help.add("-- Prepend your command with !t1- for '" + m_team1.getTeamName() + "', !t2- for '" + m_team2.getTeamName() + "' --");
                help.addAll(m_team1.getHelpMessages(name, isStaff));
            }
            // for others
        } else {
            help.addAll(m_team1.getHelpMessages(name, isStaff));
            help.addAll(m_team2.getHelpMessages(name, isStaff));
        }
        return help;
    }

    public void parseCommand(String name, String command, String[] parameters, boolean isStaff, boolean isTWDOP) {
        if (m_rules.getInt("pickbyturn") == 1) {
            if (command.equals("!notplaying") || command.equals("!np"))
                command_notplaying(name, parameters);
            if (command.equals("!notplaylist"))
                command_notplaylist(name, parameters);
        }
        if (isStaff) {
            if ((command.equals("!settime")) && (m_fnRoundState == 0))
                command_setTime(name, parameters);

            if ((command.equals("!startpick")) && (m_fnRoundState == 0))
                command_startpick(name, parameters);

            if (command.equals("!radius"))
                command_radius(name, parameters);

            if (command.equals("!shields"))
                command_shields(name, parameters);

            if (command.equals("!alert"))
                command_alert(name, parameters);

            if (command.equals("!lag") && isStaff)
                command_checklag(name, parameters);

            if (command.equals("!lagstatus"))
                command_lagstatus(name, parameters);
        }

        if (command.equals("!cap")) {

            if (m_rules.getInt("playerclaimcaptain") == 1
                    && !m_team1.isCaptain(name)
                    && !m_team2.isCaptain(name)
                    && ((m_team1.getCaptainsList().isEmpty() || (m_team1.getCaptainsList().size() > 0 && m_botAction.getPlayer(m_team1.getCaptainsList().get(0)) == null)) || (m_team2.getCaptainsList().isEmpty() || (m_team2.getCaptainsList().size() > 0 && m_botAction.getPlayer(m_team2.getCaptainsList().get(0)) == null)))) {

                // - Can players claim captainship with the !cap command?
                // - Is the requesting player not a captain already?
                // - Is there an empty captain spot on one of the teams OR is the captain of the team not in the arena ?

                // - Has the requesting player not been a captain in the previous game ?
                if (name.equalsIgnoreCase(m_game.m_bot.previousCaptain1) || name.equalsIgnoreCase(m_game.m_bot.previousCaptain2)) {

                    m_botAction.sendPrivateMessage(name, "You can't be assigned captain if you have been captain of a team in the previous game.");
                } else {
                    if ((m_team1.getCaptainsList().isEmpty() || (m_team1.getCaptainsList().size() > 0 && m_botAction.getPlayer(m_team1.getCaptainsList().get(0)) == null))) {
                        m_team1.command_setcaptain(name, new String[] { name });
                        m_game.m_bot.previousCaptain1 = name;

                    } else if (m_team2.getCaptainsList().isEmpty()
                            || (m_team2.getCaptainsList().size() > 0 && m_botAction.getPlayer(m_team2.getCaptainsList().get(0)) == null)) {
                        m_team2.command_setcaptain(name, new String[] { name });
                        m_game.m_bot.previousCaptain2 = name;

                    }

                    // if both teams have a captain, start the picking
                    if (m_fnRoundState == 0 && m_team1.getCaptainsList().size() > 0 && m_team2.getCaptainsList().size() > 0) {
                        command_startpick(name, null);
                    }
                }
            } else {
                m_logger.sendPrivateMessage(name, m_team1.getCaptains() + " is/are captain(s) of " + m_team1.getTeamName());
                m_logger.sendPrivateMessage(name, m_team2.getCaptains() + " is/are captain(s) of " + m_team2.getTeamName());
            }
        }

        if (command.equals("!myfreq") || command.equals("!mf"))
            command_myfreq(name, parameters);

        if (command.equalsIgnoreCase("!target") || command.equalsIgnoreCase("!t"))
            command_target(name);

        if (command.equals("!score"))
            command_score(name, parameters);

        if (command.equals("!rating") && (m_fnRoundState == 3))
            command_rating(name, parameters);

        if (command.equals("!mvp") && (m_fnRoundState == 3))
            command_mvp(name, parameters);

        if (command.equals("!rpd") && (m_fnRoundState == 3) && m_rules.getString("winby").equals("timerace"))
            command_rpd(name, parameters);

        //TWSDX ONLY
        if (command.equals("!rules") && m_game.m_fnMatchTypeID == 13)
            command_rules(name, parameters);

        if (command.length() > 3) {
            if (command.startsWith("!t1-") && (command.length() > 4)) {
                command = "!" + command.substring(4);
                m_team1.parseCommand(name, command, parameters, isTWDOP);
            } else if (command.startsWith("!t2-") && (command.length() > 4)) {
                command = "!" + command.substring(4);
                m_team2.parseCommand(name, command, parameters, isTWDOP);
            } else if ((m_team1.getPlayer(name, true) != null) || (m_team1.isCaptain(name))) {
                m_team1.parseCommand(name, command, parameters, isTWDOP);
            } else if ((m_team2.getPlayer(name, true) != null) || (m_team2.isCaptain(name))) {
                m_team2.parseCommand(name, command, parameters, isTWDOP);
            }
        }
    }

    /**
     * Method command_radius.
     * @param name The person who got commanded
     * @param parameters The value for the new safe spawn radius in tiles
     */
    public void command_radius(String name, String[] param) {
        if (param[0] != null && param[0].length() > 0) {
            if (param[0].contains(" "))
                param = new String[] {param[0].substring(0, param[0].indexOf(" ")), param[0].substring(param[0].indexOf(" ") + 1)};
                
            try {
                int r = Integer.valueOf(param[0]);
                if (r > -1 && r < 250) {
                    radius = r;
                    m_botAction.sendPrivateMessage(name, "Safe spawn radius: " + r + " tiles");
                    m_rules.put("radius", r);
                } else
                    m_botAction.sendPrivateMessage(name, "Radius must be between current limits of 0 and 250!");
                if (param.length > 1 && param[1].length() > 0) {
                    int c = Integer.valueOf(param[1]);
                    if (c > 0 && c < 10000) {
                        maxCount = c;
                        m_botAction.sendPrivateMessage(name, "Max count: " + c);
                        m_rules.put("maxcount", c);
                    }
                }
                m_rules.save();
            } catch (Exception e) {
                
            }
        }
    }
    
    public void command_alert(String name, String[] param) {
        spawnAlert = !spawnAlert;
        m_botAction.sendPrivateMessage(name, "Spawn detection alert: " + (spawnAlert ? "ENABLED" : "DISABLED"));
    }
    
    public void command_shields(String name, String[] param) {
        shields = !shields;
        m_botAction.sendPrivateMessage(name, "Shields on spawn: " + (shields ? "ENABLED" : "DISABLED"));
        m_rules.put("shields", (shields ? 1 : 0));
        m_rules.save();
    }

    /**
     * Method command_mvp.
     * @param name The person who got commanded
     * @param parameters
     */
    public void command_mvp(String name, String[] parameters) {
        int NUMBER_OF_MVPS = 3;

        try {
            ArrayList<MatchPlayer> playerList = new ArrayList<MatchPlayer>();
            Iterator<MatchPlayer> it = m_team1.m_players.iterator();

            while (it.hasNext()) {
                playerList.add(it.next());
            }

            it = m_team2.m_players.iterator();

            while (it.hasNext()) {
                playerList.add(it.next());
            }

            Collections.sort(playerList);

            if (NUMBER_OF_MVPS > playerList.size())
                NUMBER_OF_MVPS = playerList.size();

            for (int j = 0; j < NUMBER_OF_MVPS; j++) {
                MatchPlayer mvp = playerList.get(j);
                m_logger.sendPrivateMessage(name, "MVP " + (j + 1) + ": " + mvp.getPlayerName());

                String[] stats = mvp.getStatistics();
                for (int i = 1; i < stats.length; i++)
                    m_logger.sendPrivateMessage(name, stats[i]);
            }
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void command_target(String name) {
        if (!m_rules.getString("winby").equalsIgnoreCase("kills") || m_fnRoundState != 3)
            return;
        Player p = m_botAction.getPlayer(name);
        if (p == null)
            return;
        else if (name.equalsIgnoreCase("amnesti")) {
            m_botAction.sendPrivateMessage(name, "Don't be a d!ck.");
            return;
        }
        String msg = null;
        msg = m_team1.checkHighDeaths();
        if (msg != null && msg.length() > 0)
            m_botAction.sendPrivateMessage(name, msg);
        msg = m_team2.checkHighDeaths();
        if (msg != null && msg.length() > 0)
            m_botAction.sendPrivateMessage(name, msg);
        /* old way would only give high deaths of enemy team.. requested to give both
        if (!m_team1.getTeamName().equalsIgnoreCase(p.getSquadName())) {
            msg = m_team1.checkHighDeaths();
            if (msg != null && msg.length() > 0)
                m_botAction.sendPrivateMessage(name, msg);
        }
        if (!m_team2.getTeamName().equalsIgnoreCase(p.getSquadName())) {
            msg = m_team2.checkHighDeaths();
            if (msg != null && msg.length() > 0)
                m_botAction.sendPrivateMessage(name, msg);
        }
        */
    }

    /*
    private class MvpCompare implements Comparator
    {
        public MvpCompare()
        {
        }

        public int compare(Object o1, Object o2)
        {
            MatchPlayer p1 = (MatchPlayer)o1;
            MatchPlayer p2 = (MatchPlayer)o2;

            //have to compare p2 < p1 if you want desending order

            if (p2.getPoints() < p1.getPoints())
                return -1;
            else if (p1.getPoints() == p2.getPoints())
                return 0;
            else //p2 > p1.
                return 1;
        }

    }
    */

    /**
     * Method command_rating.
     * @param name The person who got commanded
     * @param parameters
     */
    public void command_rating(String name, String[] parameters) {
        String winby = m_rules.getString("winby");
        if (winby.equals("timerace") && m_fnRoundState == 3) {
            try {
                if (parameters.length > 0) {
                    reportRating(name, parameters[0]);
                } else {
                    reportRating(name, name);
                }

            } catch (Exception e) {
                Tools.printStackTrace(e);
            }
        }
    }

    /**
     * @param name The name of the person who messaged the bot
     * @param playerName The name of the player to retreive the rating for
     */
    private void reportRating(String name, String playerName) {
        MatchPlayer player;

        if (m_team1.getPlayer(playerName) != null) {
            player = m_team1.getPlayer(playerName);
            String[] stats = player.getStatistics();
            m_logger.sendPrivateMessage(name, player.getPlayerName());
            for (int i = 0; i < stats.length; i++)
                m_logger.sendPrivateMessage(name, stats[i]);
        } else if (m_team2.getPlayer(playerName) != null) {
            player = m_team2.getPlayer(playerName);
            String[] stats = player.getStatistics();
            m_logger.sendPrivateMessage(name, player.getPlayerName());
            for (int i = 0; i < stats.length; i++)
                m_logger.sendPrivateMessage(name, stats[i]);
        } else
            m_logger.sendPrivateMessage(name, "The player isn't in the game");
    }

    /**
     * Method command_setTime.
     * @param name
     * @param string
     */
    public void command_setTime(String name, String[] parameters) {
        if (parameters.length > 0) {
            String string = parameters[0];
            Integer time = new Integer(string);

            //the time is also set in !startpick command method
            if (time == null || time.intValue() < 5 || time.intValue() > 30) {
                setRaceTarget(m_rules.getInt("defaulttarget") * 60); //mins to secs
                m_logger.sendArenaMessage("Race set to: " + m_rules.getInt("defaulttarget") + " mins");
                m_logger.sendPrivateMessage(name, "Needs to be between 5 mins and 30 mins - setting default race");
            } else {
                setRaceTarget(time.intValue() * 60); //mins to secs
                m_logger.sendArenaMessage("Race set to: " + time.intValue() + " mins");
            }
        } else
            m_logger.sendPrivateMessage(name, "Please specify a time");
    };

    public void command_score(String name, String[] parameters) {
        String winby = m_rules.getString("winby").toLowerCase();

        if (winby.equals("timerace")) {
            String team1leadingZero = "";
            String team2leadingZero = "";

            if (m_team1.getTeamScore() % 60 < 10)
                team1leadingZero = "0";
            if (m_team2.getTeamScore() % 60 < 10)
                team2leadingZero = "0";

            m_logger.sendPrivateMessage(name, m_team1.getTeamName() + " vs. " + m_team2.getTeamName() + ": " + m_team1.getTeamScore() / 60 + ":"
                    + team1leadingZero + m_team1.getTeamScore() % 60 + " - " + m_team2.getTeamScore() / 60 + ":" + team2leadingZero
                    + m_team2.getTeamScore() % 60);
        } else {
            m_logger.sendPrivateMessage(name, m_team1.getTeamName() + " vs. " + m_team2.getTeamName() + ": " + m_team1.getTeamScore() + " - "
                    + m_team2.getTeamScore());
        }
    }

    // myfreq
    public void command_myfreq(String name, String[] parameters) {
        Player p = m_botAction.getPlayer(name);
        int nFrequency = 9999;
        if (p != null) {
            if ((p.getSquadName().equalsIgnoreCase(m_team1.getTeamName())) || (m_team1.getPlayer(name, true) != null) || (m_team1.isCaptain(name)))
                nFrequency = m_team1.getFrequency();
            else if ((p.getSquadName().equalsIgnoreCase(m_team2.getTeamName())) || (m_team2.getPlayer(name, true) != null)
                    || (m_team2.isCaptain(name)))
                nFrequency = m_team2.getFrequency();
            else {
                // Long name hack for those that don't exact-match using getPlayer(String, true)
                // THIS IS NO LONGER APPLICABLE, REVERTING TO , TRUE instead of , false.
                if (m_team1.getPlayer(name, true) != null)
                    nFrequency = m_team1.getFrequency();
                else if (m_team2.getPlayer(name, true) != null)
                    nFrequency = m_team2.getFrequency();
                else
                    m_botAction.sendPrivateMessage(name, "You're not a part of either of the teams!");
            }

            if ((p.getFrequency() != nFrequency) && (nFrequency != 9999)) {
                m_logger.announce("Freqswitch " + name + " to " + nFrequency);
                m_logger.setFreq(name, nFrequency);
            }
        }
    }

    public void command_notplaying(String name, String parameters[]) {
        Long time = m_notPlaying.get(name.toLowerCase());

        if (time == null) {
            m_notPlaying.put(name.toLowerCase(), System.currentTimeMillis());
            String[] tmp = { name };
            if ((m_team1.getPlayer(name, true) != null) && (m_fnRoundState == 1))
                m_team1.command_remove(name, tmp);
            if ((m_team2.getPlayer(name, true) != null) && (m_fnRoundState == 1))
                m_team2.command_remove(name, tmp);
            m_botAction.spec(name);
            m_botAction.spec(name);
            m_logger.sendPrivateMessage(name, "Not Playing mode turned on, captains will be unable to pick you");
            m_logger.setFreq(name, NOT_PLAYING_FREQ);
        } else {
            if( System.currentTimeMillis() < time + timeBetweenNPs ) {  // Delay between toggling !np in-game            
                m_notPlaying.remove(name.toLowerCase());
                m_logger.sendPrivateMessage(name, "notplaying mode turned off, captains will be able to pick you");
                if (m_fnRoundState > 2) {
                    m_logger.sendPrivateMessage(name, "If you wish to get back on the normal spec frequency, rejoin the arena");
                    m_logger.setFreq(name, NOT_PLAYING_FREQ + 1);
                } else {
                    placeOnSpecFreq(name);
                }
            } else {
                m_logger.sendPrivateMessage(name, "Sorry, in the interest of fairness you must wait a short time before turning notplaying back off again.");                
            }
        }
    }

    public void command_notplaylist(String name, String parameters[]) {
        Iterator<String> i = m_notPlaying.keySet().iterator();
        String a = "", pn;
        boolean first = true;
        while (i.hasNext()) {
            pn = m_botAction.getFuzzyPlayerName(i.next());
            if (pn != null) {
                if (first)
                    first = false;
                else
                    a = a + ", ";
                a = a + pn;
            }
        }
        m_logger.sendPrivateMessage(name, "The following players are not playing this game:");
        m_logger.sendPrivateMessage(name, a);
    }

    public void command_startpick(String name, String parameters[]) {
        //This is for the time race.  If the person hasn't set the time it is set to default
        String winby = m_rules.getString("winby");
        if (winby.equals("timerace") && (m_raceTarget < 5 * 60 || m_raceTarget > 30 * 30)) // 5 mins and 30 mins in secs
        {
            setRaceTarget(m_rules.getInt("defaulttarget") * 60); //mins to secs
            m_logger.sendArenaMessage("Race set to " + m_rules.getInt("defaulttarget") + " mins");
        }

        m_fnRoundState = 1;
        m_team1.setTurn();
    }

    public void command_checklag(String name, String parameters[]) {
        if (parameters.length != 0) {
            m_lagHandler.requestLag(parameters[0], name);
        } else {
            m_lagHandler.requestLag(name, name);
        }
    }

    public void command_lagstatus(String name, String parameters[]) {
        m_botAction.sendPrivateMessage(name, m_lagHandler.getStatus());
    }

    public void command_rules(String name, String parameters[]) {
        String rules_output = m_rules.getString("rules_command");

        if (rules_output != null && rules_output.length() > 0) {
            m_botAction.privateMessageSpam(name, rules_output.split("##"));
        }
    }

    public void command_rpd(String name, String args[]) {
        if (args.length != 1 || args[0] == null)
            return;
        MatchPlayer p = getPlayer(args[0]);
        if (p != null) {
            if (p.getShipType() == 8)
                m_botAction.sendPrivateMessage(name, p.getPlayerName() + ": " + p.getRepelsPerDeath() + " rpd");
            else
                m_botAction.sendPrivateMessage(name, p.getPlayerName() + " is not a shark.");
        } else
            m_botAction.sendPrivateMessage(name, args[0] + " could not be found.");
    }

    public void handleLagReport(LagReport report) {
        if (!report.isBotRequest()) {
            String player = report.getRequester();
            String[] lagStats;

            if (report.isOverLimits()) {
                lagStats = new String[2];
                lagStats[0] = report.getLagStats()[0];
                lagStats[1] = report.getLagStats()[1] + "  " + report.getLagReport();
            } else {
                lagStats = report.getLagStats();
            }

            if (player.startsWith("!")) {
                if (m_team1.getTeamName().equals(player.substring(1))) {
                    m_team1.sendPrivateMessageToCaptains(lagStats);
                } else {
                    m_team2.sendPrivateMessageToCaptains(lagStats);
                }
            } else {
                m_botAction.privateMessageSpam(player, lagStats);
            }
        }

        if (report.isOverLimits()) {
            MatchPlayer p = m_team1.getPlayer(report.getName(), true);
            if (p == null) {
                p = m_team2.getPlayer(report.getName(), true);
            }
            Player pbot = m_botAction.getPlayer(report.getName());

            try {
                if (p != null && pbot != null && pbot.getShipType() != 0 && p.getPlayerState() == MatchPlayer.IN_GAME) {
                    m_botAction.sendPrivateMessage(report.getName(), report.getLagReport());

                    if (m_fnRoundState == 3) {
                        p.setLagByBot(true);
                    }

                    m_botAction.spec(report.getName());
                    m_botAction.spec(report.getName());
                }
            } catch (Exception e) {
                Tools.printStackTrace(e);
            }
        }
    }

    public MatchTeam getOtherTeam(int freq) {
        if (m_team1.getFrequency() == freq)
            return m_team2;
        else if (m_team2.getFrequency() == freq)
            return m_team1;
        else
            return null;
    };

    public boolean checkAddPlayer(String team) {
        if (m_team1.addEPlayer() && m_team2.addEPlayer()) {
            m_game.setPlayersNum(m_game.getPlayersNum() + 1);
            m_botAction.sendSquadMessage(m_team1.getTeamName(), "Both teams have agreed to add an extra player. Max players: "
                    + m_game.getPlayersNum());
            m_botAction.sendSquadMessage(m_team2.getTeamName(), "Both teams have agreed to add an extra player. Max players: "
                    + m_game.getPlayersNum());
            m_team1.setAddPlayer(false);
            m_team2.setAddPlayer(false);
            return true;
        } else {
            if (team.equalsIgnoreCase(m_team1.getTeamName())) {
                m_botAction.sendSquadMessage(m_team2.getTeamName(), m_team1.getTeamName()
                        + " has requested to add an extra player. Captains/Assistants reply with !addplayer to accept the request.");
            } else {
                m_botAction.sendSquadMessage(m_team1.getTeamName(), m_team2.getTeamName()
                        + " has requested to add an extra player. Captains/Assistants reply with !addplayer to accept the request.");
            }
            return false;
        }
    }

    public void removeAddPlayer(String team) {
        if (team.equalsIgnoreCase(m_team1.getTeamName())) {
            m_botAction.sendSquadMessage(m_team2.getTeamName(), m_team1.getTeamName() + " has removed their request to add an extra player.");
        } else {
            m_botAction.sendSquadMessage(m_team1.getTeamName(), m_team2.getTeamName() + " has removed their request to add an extra player.");
        }
    }

    public void checkReadyToGo() {
        if ((m_team1.isReadyToGo()) && (m_team2.isReadyToGo())) {

            if (m_scheduleTimer != null) {
                m_botAction.cancelTask(m_scheduleTimer);
            }
            ;
            m_botAction.setTimer(0);
            if (!m_rules.getString("winby").equals("goals")) {
                m_logger.sendArenaMessage("Both teams are ready, game starts in 30 seconds", 2);
                m_logger.setDoors(255);
                m_team1.warpTo(m_rules.getInt("safe1x"), m_rules.getInt("safe1y"));
                m_team2.warpTo(m_rules.getInt("safe2x"), m_rules.getInt("safe2y"));
                m_botAction.move(0, 0);
                m_fnRoundState = 2;
                checkBlueout();

                // TWSDX ONLY
                if (m_game.m_fnMatchTypeID == 13) {
                    m_botAction.resetFlagGame();

                    // Bot enters game at center to center the flag
                    TimerTask botEnterGame = new TimerTask() {
                        public void run() {
                            m_botAction.stopReliablePositionUpdating();
                            m_botAction.getShip().setShip(0);
                            m_botAction.getShip().setFreq(9999);
                            m_botAction.getShip().move(512 * 16, 512 * 16);
                        }
                    };
                    m_botAction.scheduleTask(botEnterGame, 5000);

                    TimerTask botGrabFlag = new TimerTask() {
                        public void run() {
                            Iterator<Integer> it = m_botAction.getFlagIDIterator();
                            while (it.hasNext()) {
                                m_botAction.grabFlag(it.next());
                            }

                            m_botAction.getShip().setShip(8);
                            m_botAction.getShip().setFreq(9999);
                            m_botAction.resetReliablePositionUpdating();
                        }
                    };
                    m_botAction.scheduleTask(botGrabFlag, 7000);

                }

                m_secondWarp = new TimerTask() {
                    public void run() {
                        m_team1.warpTo(m_rules.getInt("safe1x"), m_rules.getInt("safe1y"));
                        m_team2.warpTo(m_rules.getInt("safe2x"), m_rules.getInt("safe2y"));
                    }
                };

                m_countdown10Seconds = new TimerTask() {
                    public void run() {
                        m_botAction.showObject(m_rules.getInt("obj_countdown10"));
                    };
                };

                m_countdown54321 = new TimerTask() {
                    public void run() {
                        m_botAction.showObject(m_rules.getInt("obj_countdown54321"));
                        m_team1.warpTo(m_rules.getInt("safe1x"), m_rules.getInt("safe1y"));
                        m_team2.warpTo(m_rules.getInt("safe2x"), m_rules.getInt("safe2y"));
                    };
                };

                m_startGame = new TimerTask() {
                    public void run() {
                        startGame();
                    };
                };
                m_botAction.scheduleTask(m_secondWarp, 10000);
                m_botAction.scheduleTask(m_countdown10Seconds, 20000);
                m_botAction.scheduleTask(m_countdown54321, 25000);
                m_botAction.scheduleTask(m_startGame, 30000);
            } else {
                m_logger.sendArenaMessage("Both teams are ready, game begins when the ball respawns!", 2);
                m_botAction.sendUnfilteredPublicMessage("*restart");
                m_team1.warpTo(m_rules.getInt("safe1x"), m_rules.getInt("safe1y"));
                m_team2.warpTo(m_rules.getInt("safe2x"), m_rules.getInt("safe2y"));
                waitingOnBall = true;
            }
        }
    };

    public void checkCancel(String team, String name) {
        team = team.toLowerCase();
        if ((m_team1.requestsCancel()) && (m_team2.requestsCancel())) {
            m_game.m_bot.playerKillGame();
        } else {
            if (team.equals(m_team1.getTeamName().toLowerCase())) {
                m_botAction.sendSquadMessage(m_team2.getTeamName(), m_team1.getTeamName() + " has requested to cancel the current game.");
            } else {
                m_botAction.sendSquadMessage(m_team1.getTeamName(), m_team2.getTeamName() + " has requested to cancel the current game.");
            }
        }
    }

    // gets called by m_startGame TimerTask.
    @SuppressWarnings("unchecked")
    public void startGame() {
        if (m_rules.getInt("storegame") != 0)
            events.add(MatchRoundEvent.roundStart());

        m_generalTime = m_rules.getInt("time") * 60;
        m_scoreBoard = m_botAction.getObjectSet();
        updateScores = new TimerTask() {
            public void run() {
                do_updateScoreBoard();

                checkTeamsAlive();
            }
        };
        m_botAction.scheduleTaskAtFixedRate(updateScores, 2000, 1000);

        if ((m_rules.getInt("safe1xout") != 0) && (m_rules.getInt("safe1yout") != 0)) {
            m_team1.warpTo(m_rules.getInt("safe1xout"), m_rules.getInt("safe1yout"));
            if ((m_rules.getInt("safe2xout") != 0) && (m_rules.getInt("safe2yout") != 0)) {
                m_team2.warpTo(m_rules.getInt("safe2xout"), m_rules.getInt("safe2yout"));
            } else {
                m_team1.warpTo(m_rules.getInt("safe1xout"), m_rules.getInt("safe1yout"));
                m_team2.warpTo(m_rules.getInt("safe2xout"), m_rules.getInt("safe2yout"));
                m_logger.setDoors(0);
            }
            ;
        } else {
            m_team1.warpTo(m_rules.getInt("safe1xout"), m_rules.getInt("safe1yout"));
            m_team2.warpTo(m_rules.getInt("safe2xout"), m_rules.getInt("safe2yout"));
            m_logger.setDoors(0);
        }
        ;
        m_team1.signalStartToPlayers();
        m_team2.signalStartToPlayers();
        m_botAction.receiveAllPlayerDeaths();
        m_logger.scoreResetAll();
        m_logger.shipResetAll();

        // TWSDX ONLY
        if (m_game.m_fnMatchTypeID != 13) {
            m_logger.resetFlagGame();
        }

        m_team1.disownFlag();
        m_team2.disownFlag();
        m_logger.sendArenaMessage("Go go go!", 104);
        m_botAction.showObject(m_rules.getInt("obj_gogogo"));

        //Sends match info to TWDBot
        m_botAction.ipcTransmit("MatchBot", new IPCTWD(EventType.STATE, m_botAction.getArenaName(), m_botAction.getBotName(), m_game.m_fcTeam1Name, m_game.m_fcTeam2Name, m_game.m_fnMatchID, m_game.m_fnTeam1Score, m_game.m_fnTeam2Score));
        m_timeStartedms = System.currentTimeMillis();
        flagClaimed = false;

        // TWSDX ONLY: Announce the !rules command after starting the game in public chat
        if (m_game.m_fnMatchTypeID == 13) {
            m_botAction.sendPublicMessage("Private message me with \"!rules\" for an explanation on how to play TWSD");
        }

        // Open special doors if needed
        if (m_rules.getInt("door") > 0) {

            if (m_rules.getInt("dooropen_at") >= m_game.getPlayersNum()) {
                m_botAction.setDoors(m_rules.getInt("door"));
            }
        }

        // This is for timerace only
        if ((m_rules.getString("winby")).equals("timerace")) {
            m_raceTimer = new TimerTask() {
                public void run() {
                    m_team1.addTimePoint();
                    m_team2.addTimePoint();
                };
            };
            m_botAction.scheduleTaskAtFixedRate(m_raceTimer, 1000, 1000);

        }
        
        if (m_game.m_fnMatchTypeID == 9) {
            m_botAction.setPlayerPositionUpdating(300);
            maxCount = m_rules.getInt("maxcount");
        }

        if (m_rules.getInt("pathcount") > 0) {
            m_moveAround = new TimerTask() {
                int pathnr = 1, pathmax = m_rules.getInt("pathcount");

                public void run() {
                    m_botAction.move(m_rules.getInt("path" + pathnr + "x") * 16, m_rules.getInt("path" + pathnr + "y") * 16);
                    if (pathnr == pathmax)
                        pathnr = 1;
                    else
                        pathnr++;
                };
            };
            m_botAction.scheduleTaskAtFixedRate(m_moveAround, 1000, 300);
        }
        ;

        m_closeDoors = new TimerTask() {
            public void run() {
                m_logger.setDoors(255);
            };
        };
        m_botAction.scheduleTask(m_closeDoors, 10000);

        if (m_rules.getInt("time") != 0) {
            m_botAction.setTimer(m_rules.getInt("time"));
            m_endGame = new TimerTask() {
                public void run() {
                    endGame();
                };
            };
            m_botAction.scheduleTask(m_endGame, 60000 * m_rules.getInt("time"));
        }
        ;

        m_fnRoundState = 3;
    };

    // declare winner. End the round.
    public void endGame() {
        if (m_endGame != null)
            m_botAction.cancelTask(m_endGame);

        if (m_endTieGame != null)
            m_botAction.cancelTask(m_endTieGame);

        if (m_raceTimer != null)
            m_botAction.cancelTask(m_raceTimer);

        if (!m_rules.getString("winby").equalsIgnoreCase("goals")) {
            m_fnTeam1Score = m_team1.getTeamScore();
            m_fnTeam2Score = m_team2.getTeamScore();
        }

        if (m_fnTeam1Score == m_fnTeam2Score) {
            String ondraw = m_rules.getString("ondraw");
            if (ondraw == null)
                ondraw = "quit";

            if ((ondraw.equalsIgnoreCase("extension")) && (!m_fbExtension) && (!m_team1.isForfeit())) {
                int extTime = m_rules.getInt("extensiontime");
                if (extTime != 0) {
                    m_logger.sendArenaMessage("The scores are tied. The game will be extended for " + extTime + " minutes.", 2);
                    m_generalTime = extTime * 60;
                    m_fbExtension = true;
                    m_endTieGame = new TimerTask() {
                        public void run() {
                            endGame();
                        }
                    };
                    try {
                        m_botAction.scheduleTask(m_endTieGame, extTime * 1000 * 60);
                    } catch (IllegalStateException e) {
                        System.gc();
                        try {
                            m_botAction.scheduleTask(m_endTieGame, extTime * 1000 * 60);
                        } catch (IllegalStateException e2) {
                            m_logger.sendArenaMessage("There was an error in extending the match.  Please contact a TWDOp for help.", 2);
                        }
                    }
                    m_botAction.setTimer(extTime);
                }
            } else {

                m_game.reportEndOfRound(m_fbAffectsEntireGame);
                m_botAction.showObject(m_rules.getInt("obj_gameover"));
                return;
            }
        } else {

            m_timeEnded = new java.util.Date();

            if (updateScores != null)
                m_botAction.cancelTask(updateScores);

            do_updateScoreBoard();
            m_botAction.showObject(m_rules.getInt("obj_gameover"));

            m_team1.signalEndToPlayers();
            m_team2.signalEndToPlayers();

            toggleBlueout(false);

            // TWSD ONLY: Remove the spider logo from middle & Update the flag status at the scoreboard (turn it off)
            if (m_game.m_fnMatchTypeID == 13) {
                m_botAction.hideObject(744);
            }

            if (m_rules.getString("winby").equals("timerace")) {
                //bug fix
                String team1leadingZero = "";
                String team2leadingZero = "";

                if (m_team1.getTeamScore() % 60 < 10)
                    team1leadingZero = "0";
                if (m_team2.getTeamScore() % 60 < 10)
                    team2leadingZero = "0";

                m_logger.sendArenaMessage("Result of " + m_team1.getTeamName() + " vs. " + m_team2.getTeamName() + ": " + m_fnTeam1Score / 60 + ":"
                        + team1leadingZero + m_team1.getTeamScore() % 60 + " - " + m_fnTeam2Score / 60 + ":" + team2leadingZero
                        + m_team2.getTeamScore() % 60, 5);
            } else
                m_logger.sendArenaMessage("Result of " + m_team1.getTeamName() + " vs. " + m_team2.getTeamName() + ": " + m_fnTeam1Score + " - "
                        + m_fnTeam2Score, 5);

            if (!m_rules.getString("winby").equals("goals"))
                displayScores();

            if (m_team1.isForfeit()) {
                if (m_team1.teamForfeit()) {
                    m_logger.sendArenaMessage(m_team1.getTeamName() + " forfeits this round!");
                } else {
                    m_logger.sendArenaMessage(m_team2.getTeamName() + " forfeits this round!");
                }
            } else {
                if (m_fnTeam1Score > m_fnTeam2Score) {
                    m_fnRoundResult = 1;
                    if (m_rules.getInt("rounds") > 1)
                        m_logger.sendArenaMessage(m_team1.getTeamName() + " wins round " + m_fnRoundNumber + "!");
                    else
                        m_logger.sendArenaMessage(m_team1.getTeamName() + " wins this game!");
                } else if (m_fnTeam2Score > m_fnTeam1Score) {
                    m_fnRoundResult = 1;
                    if (m_rules.getInt("rounds") > 1)
                        m_logger.sendArenaMessage(m_team2.getTeamName() + " wins round " + m_fnRoundNumber + "!");
                    else
                        m_logger.sendArenaMessage(m_team2.getTeamName() + " wins this game!");
                } else {
                    m_fnRoundResult = 2;
                    m_logger.sendArenaMessage("Draw!");
                }
            }

            m_fnRoundState = 4;

            if (m_rules.getInt("storegame") == 1)
                storeRoundResult();

            if (!m_team1.isForfeit()) {
                m_announceMVP = new TimerTask() {
                    public void run() {
                        announceMVP();
                    };
                };
                m_botAction.scheduleTask(m_announceMVP, 5000);
            }

            m_signalEndOfRound = new TimerTask() {
                public void run() {
                    if (m_scoreBoard != null) {
                        m_scoreBoard.hideAllObjects();
                        m_botAction.setObjects();
                    }
                    m_generalTime = 0;

                    signalEndOfRound();
                }
            };
            m_botAction.scheduleTask(m_signalEndOfRound, 10000);
        }
    }

    // announce MVP
    public void announceMVP() {
        MatchPlayer t1b = m_team1.getMVP(), t2b = m_team2.getMVP(), mvp;

        if ((t1b != null) && (t2b != null)) {
            if (t1b.getPoints() > t2b.getPoints())
                mvp = t1b;
            else
                mvp = t2b;
            m_logger.sendArenaMessage("MVP: " + mvp.getPlayerName() + "!", 7);
        }
    }

    // Signal end of round
    public void signalEndOfRound() {
        m_game.reportEndOfRound(m_fbAffectsEntireGame);
    }

    // for turnbased picking
    public void determineNextPick() {
        int pr1 = m_team1.getPlayersRostered(), pr2 = m_team2.getPlayersRostered();

        // only if neither of the teams has the turn, the next turn is determined
        if ((m_fnRoundState == 1) && (!m_team1.m_turn) && (!m_team2.m_turn)) {
            if (pr1 <= pr2)
                m_team1.setTurn();
            if (pr2 < pr1)
                m_team2.setTurn();
        }
    }

    // schedule time is up. Start game when both rosters are ok, otherwise call forfeit
    public void scheduleTimeIsUp() {
        
        if (!m_fbExtensionUsed && (m_team1.hasAddedTime() || m_team2.hasAddedTime())) {        	
        	m_fbExtensionUsed = true;
        	m_botAction.setTimer(m_rules.getInt("lineupextension"));
        	m_botAction.sendArenaMessage("NOTICE: 2 minutes remaining.");
            m_scheduleTimer = new TimerTask() {
                public void run() {
                    scheduleTimeIsUp();
                };
            };
        	m_botAction.scheduleTask(m_scheduleTimer, 60000 * m_rules.getInt("lineupextension"));
        } else {
            String t1a = m_team1.isAllowedToBegin(), t2a = m_team2.isAllowedToBegin();
            // 0 - GO,     1 - TEAM 1 FORFEITS,       2 - TEAM 2 FORFEITS,     3 - BOTH FORFEIT
            int gameResult = 0;

            if (!t1a.equals("yes"))
                gameResult = 1;
            if (!t2a.equals("yes"))
                if (gameResult == 0)
                    gameResult = 2;
                else
                    gameResult = 3;
        	// check if neither of the teams has nobody rostered at all (forfeits entire game)
            if ((m_team1.getPlayersReadyToPlay() == 0) && (m_team2.getPlayersReadyToPlay() != 0)) {
                m_fbAffectsEntireGame = true;
                m_team1.forfeitLoss();
                m_team2.forfeitWin();
                m_logger.sendArenaMessage("Time is up. " + m_team1.getTeamName()
                        + " didn't have any player rostered at all, and thus forfeits the ENTIRE game", 2);
                endGame();
            } else if ((m_team2.getPlayersReadyToPlay() == 0) && (m_team1.getPlayersReadyToPlay() != 0)) {
                m_fbAffectsEntireGame = true;
                m_team1.forfeitWin();
                m_team2.forfeitLoss();
                m_logger.sendArenaMessage("Time is up. " + m_team2.getTeamName()
                        + " didn't have any player rostered at all, and thus forfeits the ENTIRE game", 2);
                endGame();
            } else if ((m_team1.getPlayersReadyToPlay() == 0) && (m_team2.getPlayersReadyToPlay() == 0)) {
                m_fbAffectsEntireGame = true;
                m_team1.forfeitLoss();
                m_team2.forfeitLoss();
                m_logger.sendArenaMessage("Time is up. Both teams didn't have any player rostered at all, the game will be declared void", 5);
                endGame();
            } else if (gameResult == 1) {
                m_team1.forfeitLoss();
                m_team2.forfeitWin();
                m_logger.sendArenaMessage("Time is up. " + m_team1.getTeamName() + "'s roster is NOT OK: " + t1a);
                endGame();
            } else if (gameResult == 2) {
                m_team1.forfeitWin();
                m_team2.forfeitLoss();
                m_logger.sendArenaMessage("Time is up. " + m_team2.getTeamName() + "'s roster is NOT OK: " + t2a);
                endGame();
            } else if (gameResult == 3) {
                m_team1.forfeitLoss();
                m_team2.forfeitLoss();
                m_logger.sendArenaMessage("Time is up. Both rosters are NOT OK:");
                m_logger.sendArenaMessage(m_team1.getTeamName() + " - " + t1a);
                m_logger.sendArenaMessage(m_team2.getTeamName() + " - " + t2a);
                endGame();
            } else {
                m_team1.setReadyToGo();
                m_team2.setReadyToGo();
                m_logger.sendArenaMessage("Time is up. Rosters are OK.");
                checkReadyToGo();
            }
        }

        
    }

    public void toggleBlueout(boolean blueout) {
        if ((!m_blueoutState && blueout) || (m_blueoutState && !blueout)) {
            if (blueout) {
                m_logger.sendArenaMessage("Blueout has been enabled. Staff, please enable the !power command or do not speak in public chat.");
                m_timeBOEnabled = System.currentTimeMillis();
                m_blueoutState = true;
            } else {
                m_logger.sendArenaMessage("Blueout has been disabled. You can speak in public now.");
                m_blueoutState = false;
            }
            m_botAction.toggleLockPublicChat();
        }
    }

    public void requestBlueout(boolean blueout) {
        if (m_fnRoundState >= 2 && (blueout != m_blueoutState && m_team1.getBlueoutState() == blueout && m_team2.getBlueoutState() == blueout))
            toggleBlueout(blueout);
    }

    public void checkBlueout() {
        if (m_rules.getInt("blueout") == 2 || m_rules.getInt("blueout") == 1) {
            m_team1.m_blueoutState = true;
            m_team2.m_blueoutState = true;
            toggleBlueout(true);
        } else {
            m_team1.m_blueoutState = false;
            m_team2.m_blueoutState = false;
            toggleBlueout(false);
        }
    }

    public void do_updateScoreBoard() {
        if (m_scoreBoard != null) {
            m_scoreBoard.hideAllObjects();
            m_generalTime -= 1;
            String team1Score;
            String team2Score;

            team1Score = "" + m_team1.getTeamScore();
            team2Score = "" + m_team2.getTeamScore();

            //If lb display twlb scoreboard
            if (m_rules.getString("winby").equals("timerace")) {
                int t1s = Integer.parseInt(team1Score);
                int t2s = Integer.parseInt(team2Score);

                int team1Minutes = (int) Math.floor(t1s / 60.0);
                int team2Minutes = (int) Math.floor(t2s / 60.0);
                int team1Seconds = t1s - team1Minutes * 60;
                int team2Seconds = t2s - team2Minutes * 60;

                //Team 1
                m_scoreBoard.showObject(100 + team1Seconds % 10);
                m_scoreBoard.showObject(110 + (team1Seconds - team1Seconds % 10) / 10);
                m_scoreBoard.showObject(130 + team1Minutes % 10);
                m_scoreBoard.showObject(140 + (team1Minutes - team1Minutes % 10) / 10);

                //Team 2
                m_scoreBoard.showObject(200 + team2Seconds % 10);
                m_scoreBoard.showObject(210 + (team2Seconds - team2Seconds % 10) / 10);
                m_scoreBoard.showObject(230 + team2Minutes % 10);
                m_scoreBoard.showObject(240 + (team2Minutes - team2Minutes % 10) / 10);
            } else { //Else display ld lj on normal scoreboard
                for (int i = team1Score.length() - 1; i > -1; i--)
                    m_scoreBoard.showObject(Integer.parseInt("" + team1Score.charAt(i)) + 100 + (team1Score.length() - 1 - i) * 10);
                for (int i = team2Score.length() - 1; i > -1; i--)
                    m_scoreBoard.showObject(Integer.parseInt("" + team2Score.charAt(i)) + 200 + (team2Score.length() - 1 - i) * 10);
            }
            if (m_generalTime >= 0) {
                int seconds = m_generalTime % 60;
                int minutes = (m_generalTime - seconds) / 60;
                m_scoreBoard.showObject(730 + ((minutes - minutes % 10) / 10));
                m_scoreBoard.showObject(720 + (minutes % 10));
                m_scoreBoard.showObject(710 + ((seconds - seconds % 10) / 10));
                m_scoreBoard.showObject(700 + (seconds % 10));
            }

            if (m_rules.getInt("scoreboard_flags") == 1) {
                // Flag status
                if (m_team1.hasFlag()) {
                    m_scoreBoard.showObject(740);
                    m_scoreBoard.showObject(743);
                    m_scoreBoard.hideObject(741);
                    m_scoreBoard.hideObject(742);
                } else if (m_team2.hasFlag()) {
                    m_scoreBoard.showObject(741);
                    m_scoreBoard.showObject(742);
                    m_scoreBoard.hideObject(743);
                    m_scoreBoard.hideObject(740);
                } else {
                    m_scoreBoard.showObject(740);
                    m_scoreBoard.showObject(742);
                    m_scoreBoard.hideObject(741);
                    m_scoreBoard.hideObject(743);
                }
            }

            do_showTeamNames(m_team1.getTeamName(), m_team2.getTeamName());
            m_botAction.setObjects();
        }
    }

    public void do_showTeamNames(String n1, String n2) {
        n1 = n1.toLowerCase();
        n2 = n2.toLowerCase();
        if (n1.equalsIgnoreCase("Freq 1")) {
            n1 = "freq1";
        }
        if (n2.equalsIgnoreCase("Freq 2")) {
            n2 = "freq2";
        }
        int i;
        String s1 = "", s2 = "";

        for (i = 0; i < n1.length(); i++)
            if ((n1.charAt(i) >= '0') && (n1.charAt(i) <= 'z') && (s1.length() < 5))
                s1 = s1 + n1.charAt(i);

        for (i = 0; i < n2.length(); i++)
            if ((n2.charAt(i) >= '0') && (n2.charAt(i) <= 'z') && (s2.length() < 5))
                s2 = s2 + n2.charAt(i);

        show_string(s1, 0, 30);

        show_string(s2, 5, 30);
    }

    public void show_string(String new_n, int pos_offs, int alph_offs) {
        int i, t;

        for (i = 0; i < new_n.length(); i++) {
            t = new Integer(Integer.toString(((new_n.getBytes()[i]) - 97) + alph_offs) + Integer.toString(i + pos_offs)).intValue();
            if (t < -89) {
                t = new Integer(Integer.toString(((new_n.getBytes()[i])) + alph_offs) + Integer.toString(i + pos_offs)).intValue();
                t -= 220;
            }
            m_scoreBoard.showObject(t);
        }
    }

    public void checkTeamsAlive() {
        if ((int) System.currentTimeMillis() - m_fnAliveCheck > 5000) {

            m_lagHandler.requestLag(m_team1.getNameToLagCheck());
            m_lagHandler.requestLag(m_team2.getNameToLagCheck());

            if (m_team1.isDead() || m_team2.isDead())
                endGame();

            m_fnAliveCheck = (int) System.currentTimeMillis();
        }
    }

    public void displayScores() {
        boolean duelG = m_rules.getString("winby").equalsIgnoreCase("kills") || m_rules.getString("winby").equalsIgnoreCase("killrace");
        boolean wbG = m_rules.getInt("ship") == 1 || m_rules.getInt("ship") == 3;
        ArrayList<String> out = new ArrayList<String>();

        if (duelG) {
            if (wbG) {
                out.add(",---------------------------------+------+-----------+----.");
                out.add("|                               K |    D |    Rating | LO |");
            } else {
                out.add(",---------------------------------+------+------+-----------+----.");
                out.add("|                               K |    D |   TK |    Rating | LO |");
            }
        } else {
            out.add(",---------------------------------+------+------+-----------+------+------+-----+-----------+----.");
            out.add("|                               K |    D |   TK |    Points |   FT |  TeK | RPD |    Rating | LO |");
        }

        out.addAll(m_team1.getDScores(duelG, wbG));

        if (duelG) {
            if (wbG) {
                out.add("+---------------------------------+------+-----------+----+");
            } else {
                out.add("+---------------------------------+------+------+-----------+----+");
            }
        } else {
            out.add("+---------------------------------+------+------+-----------+------+------+-----+-----------+----+");
        }

        out.addAll(m_team2.getDScores(duelG, wbG));

        if (duelG) {
            if (wbG) {
                out.add("`---------------------------------+------+-----------+----'");
            } else {
                out.add("`---------------------------------+------+------+-----------+----'");
            }
        } else {
            out.add("`---------------------------------+------+------+-----------+------+------+-----+-----------+----'");
        }

        String out2[] = out.toArray(new String[out.size()]);

        for (int i = 0; i < out2.length; i++) {
            m_botAction.sendArenaMessage(out2[i]);
        }
    }

    public void cancel() {
        if (m_countdown10Seconds != null)
            m_botAction.cancelTask(m_countdown10Seconds);
        if (m_startGame != null)
            m_botAction.cancelTask(m_startGame);
        if (m_endGame != null) {
            m_botAction.showObject(m_rules.getInt("obj_gameover"));
            m_botAction.cancelTask(m_endGame);
        }
        if (m_raceTimer != null)
            m_botAction.cancelTask(m_raceTimer);
        if (m_scheduleTimer != null)
            m_botAction.cancelTask(m_scheduleTimer);
        if (m_signalEndOfRound != null)
            m_botAction.cancelTask(m_signalEndOfRound);
        if (m_announceMVP != null)
            m_botAction.cancelTask(m_announceMVP);
        if (m_closeDoors != null)
            m_botAction.cancelTask(m_closeDoors);
        if (m_moveAround != null)
            m_botAction.cancelTask(m_moveAround);

        if (m_blueoutState)
            m_botAction.toggleLockPublicChat();

        if (updateScores != null)
            m_botAction.cancelTask(updateScores);

        if (m_scoreBoard != null)
            m_scoreBoard.hideAllObjects();

        m_botAction.setObjects();
        m_generalTime = 0;
    }

    /**
     * Returns the m_raceTarget.
     * @return int
     */
    public int getRaceTarget() {
        return m_raceTarget;
    }

    /**
    * Sets the m_raceTarget.
    * @param m_raceTarget The m_raceTarget to set
    */
    public void setRaceTarget(int raceTarget) {
        m_raceTarget = raceTarget;
    }

    public MatchGame getGame() {
        return this.m_game;
    }

    /**
     * This class is used to store event statistics about the current round
     * It used JSONArray because it is already serializable, PHP-compatible and lightweight
     * This information will be only used to produce a graphic
     * 
     * EventType number must be in sync with those on the website..
     * Don't even change one.
     * 
     * If you need to add a new event, use the last number and do +1
     * 
     * See Arobas+
     */
    @SuppressWarnings({ "unchecked", "serial" })
    public static class MatchRoundEvent extends JSONArray {

        public final static int KILL = 1;
        public final static int DEATH = 2;
        public final static int SUB_PLAYER = 3;
        public final static int ADD_PLAYER = 4;
        public final static int SWITCH_PLAYER = 5; // BD only
        public final static int LAGOUT = 6;
        public final static int LAGIN = 7;
        public final static int ELIMINATED = 8;
        public final static int ROUND_START = 9;
        public final static int ROUND_END = 10;
        public final static int FLAG_TOUCH = 11;

        private MatchRoundEvent(int eventType) {
            this.add(System.currentTimeMillis()); // timestamp
            this.add(eventType); // event type
        }

        public static MatchRoundEvent kill(int fnUserIDKiller, int fnUserIDKillee, int fnShipIDKiller, int fnShipIDKillee) {
            MatchRoundEvent event = new MatchRoundEvent(KILL);
            event.add(fnUserIDKiller);
            event.add(fnUserIDKillee);
            event.add(fnShipIDKiller);
            event.add(fnShipIDKillee);
            return event;
        }

        public static MatchRoundEvent death(int fnUserIDKillee, int fnUserIDKiller, int fnShipIDKillee, int fnShipIDKiller) {
            MatchRoundEvent event = new MatchRoundEvent(DEATH);
            event.add(fnUserIDKillee);
            event.add(fnUserIDKiller);
            event.add(fnShipIDKillee);
            event.add(fnShipIDKiller);
            return event;
        }

        public static MatchRoundEvent subPlayer(int fnUserIDSubbed, int fnUserIDAdded) {
            MatchRoundEvent event = new MatchRoundEvent(SUB_PLAYER);
            event.add(fnUserIDSubbed);
            event.add(fnUserIDAdded);
            return event;
        }

        public static MatchRoundEvent addPlayer(int fnUserIDAdded, int shipTypeID) {
            MatchRoundEvent event = new MatchRoundEvent(ADD_PLAYER);
            event.add(fnUserIDAdded);
            event.add(shipTypeID);
            return event;
        }

        public static MatchRoundEvent switchPlayer(int fnUserIDPlayer1, int fnUserIDPlayer2) {
            MatchRoundEvent event = new MatchRoundEvent(SWITCH_PLAYER);
            event.add(fnUserIDPlayer1);
            event.add(fnUserIDPlayer2);
            return event;
        }

        public static MatchRoundEvent lagout(int fnUserID, boolean fbOutOfArena) {
            MatchRoundEvent event = new MatchRoundEvent(LAGOUT);
            event.add(fnUserID);
            event.add(fbOutOfArena);
            return event;
        }

        public static MatchRoundEvent lagin(int fnUserID) {
            MatchRoundEvent event = new MatchRoundEvent(LAGIN);
            event.add(fnUserID);
            return event;
        }

        public static MatchRoundEvent eliminated(int fnUserIDEliminated, int fnUserIDKiller) {
            MatchRoundEvent event = new MatchRoundEvent(ELIMINATED);
            event.add(fnUserIDEliminated);
            event.add(fnUserIDKiller);
            return event;
        }

        public static MatchRoundEvent roundStart() {
            MatchRoundEvent event = new MatchRoundEvent(ROUND_START);
            return event;
        }

        public static MatchRoundEvent roundEnd() {
            MatchRoundEvent event = new MatchRoundEvent(ROUND_END);
            return event;
        }

        public static MatchRoundEvent flagTouch(int team, int fnUserID) {
            MatchRoundEvent event = new MatchRoundEvent(FLAG_TOUCH);
            event.add(team);
            event.add(fnUserID);
            return event;
        }

    }

}
