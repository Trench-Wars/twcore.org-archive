package twcore.bots.twl;

/*
    MatchRound.java

    Created on August 19, 2002, 10:51 PM
*/

/**

    @author  Administrator
*/

import java.sql.Connection;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.ListIterator;
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
import twcore.core.util.Tools;
import twcore.core.util.json.JSONArray;
import twcore.core.util.json.JSONValue;

public class MatchRound {

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

    // 0 - still playing, 1 - won by one of the teams, 2 - draw, 3 - canceled
    int m_fnRoundResult;

    // RoundID used to determine if playoff or not... playoff roundids are hardcoded
    int m_fnRoundID;
    int m_fnMatchRoundID;
    int m_fnRoundNumber;
    boolean m_playoff;
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
    TimerTask m_scheduleTimer;
    TimerTask m_signalEndOfRound;
    TimerTask m_announceMVP;
    TimerTask m_closeDoors;
    TimerTask m_moveAround;
    TimerTask m_secondWarp;
    TimerTask updateScores;
    ArrayList<String> m_notPlaying;

    JSONArray events; // array of MatchRoundEvent

    private Objset m_myObjects;
    private int m_generalTime = 0;

    private lagHandler m_lagHandler;

    boolean m_fbAffectsEntireGame = false;
    boolean m_fbExtension = false;
    // -1 - unknown;  0 - off; 1 - on
    int m_blueoutState = -1;
    boolean m_blueoutDesiredState = false;
    boolean m_lineUpExtension = false;
    boolean waitingOnBall = false;

    // this is for lagchecking:
    String m_lagPlayerName;
    int m_lagTeam;


    //time race variables
    private int m_raceTarget = 0;
    TimerTask m_raceTimer;

    static final int NOT_PLAYING_FREQ = 200;

    /** Creates a new instance of MatchRound */
    public MatchRound(int fnRoundNumber, String fcTeam1Name, String fcTeam2Name, MatchGame Matchgame) {
        useDatabase = false;
        m_game = Matchgame;
        m_botAction = m_game.m_botAction;
        m_botAction.getObjectSet();
        m_rules = m_game.m_rules;
        m_fnRoundNumber = fnRoundNumber;
        m_fnRoundID = getRoundID();
        m_fnRoundState = 0;
        m_fnRoundResult = 0;
        m_timeStarted = new java.util.Date();
        m_logger = m_game.m_logger;
        m_team1 = new MatchTeam(fcTeam1Name, 1, 1, this);
        m_team2 = new MatchTeam(fcTeam2Name, 2, 2, this);


        m_playoff = isPlayoff();

        events = new JSONArray();

        m_lagHandler = new lagHandler(m_botAction, m_rules, this, "handleLagReport");

        m_notPlaying = new ArrayList<String>();

        Iterator<Player> iterator = m_botAction.getPlayerIterator();
        Player player;

        while( iterator.hasNext() ) {
            player = iterator.next();

            if( player.getFrequency() == NOT_PLAYING_FREQ ) {
                m_notPlaying.add(player.getPlayerName().toLowerCase());
            }
        }

        if (m_rules.getInt("pickbyturn") == 0) {
            //This is for the time race.  If the person hasn't set the time it is set to default
            String winby = m_rules.getString("winby");

            if (winby.equals("timerace")) // 5 mins and 30 mins in secs
            {
                if (!m_playoff && (m_raceTarget < 5 * 60 || m_raceTarget > 30 * 30)) {
                    setRaceTarget(m_rules.getInt("defaulttarget") * 60); //mins to secs
                    m_logger.sendArenaMessage("Race set to " + m_rules.getInt("defaulttarget") + " mins");
                } else if (m_playoff && (m_raceTarget < 5 * 60 || m_raceTarget > 40 * 30)) {
                    setRaceTarget(m_rules.getInt("playofftarget") * 60); //mins to secs
                    m_logger.sendArenaMessage("Race set to " + m_rules.getInt("playofftarget") + " mins");
                }
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

    public int getRoundID() {
        int id = 1;

        try {
            String query = "SELECT fnTWL__RoundId as id FROM tblTWL__Match WHERE fnMatchID = " + m_game.m_fnMatchID + " LIMIT 1";
            ResultSet rs = m_botAction.SQLQuery(dbConn, query);

            if (rs.next())
                id = rs.getInt("id");

            m_botAction.SQLClose(rs);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        return id;
    }

    public boolean isPlayoff() {
        for (int i : m_game.m_playoffs) {
            if (m_fnRoundID == i)
                return true;
        }

        return false;
    }

    public void specAll() {
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
        Create a database record for the round
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

            String[] fields = {
                "fnMatchID",
                "fnRoundStateID",
                "ftTimeStarted",
                "ftTimeEnded",
                "fnTeam1Score",
                "fnTeam2Score"
            };

            String[] values = {
                Integer.toString(fnMatchID),
                Integer.toString(roundstate),
                started,
                ended,
                Integer.toString(m_fnTeam1Score),
                Integer.toString(m_fnTeam2Score)
            };

            m_botAction.SQLInsertInto(dbConn, "tblTWL__MatchRound", fields, values);
            //            ResultSet s = m_botAction.SQLQuery(dbConn, "select fnMatchRoundID from tblMatchRound where ftTimeStarted = '"+started+"' and ftTimeEnded = '"+ended+"' and fnTeam1Score = "+m_fnTeam1Score+" and fnTeam2Score = "+m_fnTeam2Score);
            ResultSet s = m_botAction.SQLQuery(dbConn, "select MAX(fnMatchRoundID) as fnMatchRoundID from tblTWL__MatchRound");

            if (s.next()) {
                m_fnMatchRoundID = s.getInt("fnMatchRoundID");
                m_team1.storePlayerResults();
                m_team2.storePlayerResults();
            }

            m_botAction.SQLClose(s);

            String[] fields2 = {
                "fnMatchID",
                "fnMatchRoundID",
                "fcEvent"
            };

            String[] values2 = {
                Integer.toString(fnMatchID),
                Integer.toString(m_fnMatchRoundID),
                JSONValue.escape(JSONValue.toJSONString(events))
            };

            m_botAction.SQLBackgroundInsertInto(dbConn, "tblTWL__MatchRoundExtra", fields2, values2);

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

    /**
        Parses the FlagClaimed event to the correct team
        It also check if any of the teams won the race

        @author Force of Nature
        @param event The flagClaimed event holding the playerId claiming the flag
    */
    @SuppressWarnings("unchecked")
    public void handleEvent(FlagClaimed event) {
        if (m_fnRoundState == 3) {
            Player player = m_botAction.getPlayer(event.getPlayerID());
            int freq = player.getFrequency();

            if (m_team1.getFrequency() == freq) {
                m_team2.disownFlag();                 // Disown flag before reowning
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
        Parses the FlagReward event to the correct team
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
        Parses the FrequencyShipChange event to the team in which the player is

        @param event FrequencyShipChange event
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

    /*
        Parses the PlayerDeath event to the team in which the player is
    */
    @SuppressWarnings("unchecked")
    public void handleEvent(PlayerDeath event) {
        try {
            Player killee = m_botAction.getPlayer(event.getKilleeID());
            Player killer = m_botAction.getPlayer(event.getKillerID());

            String killeeName = killee.getPlayerName();
            String killerName = killer.getPlayerName();

            if (m_fnRoundState == 3)
                m_lagHandler.requestLag(killerName);

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
        } catch (Exception e) {
            Tools.printStackTrace(e);
        }
    }

    public void handleEvent( PlayerEntered event ) {

        if (m_blueoutState == 1) {
            m_botAction.sendPrivateMessage(event.getPlayerID(), "This game has blueout enabled.");
        }

        int exists = m_notPlaying.indexOf( event.getPlayerName().toLowerCase());

        if( exists != -1 ) {
            m_botAction.spec( event.getPlayerName() );
            m_botAction.spec( event.getPlayerName() );
            m_logger.sendPrivateMessage( event.getPlayerName(), "notplaying mode is still on, captains will be unable to pick you");
            m_logger.setFreq( event.getPlayerName(), NOT_PLAYING_FREQ);
        }
    }

    /*
        Parses the PlayerLeft event to the team in which the player is
    */
    public void handleEvent(PlayerLeft event) {
        String playerName = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();

        if (m_team1.getPlayer(playerName, true) != null)
            m_team1.handleEvent(event);

        if (m_team2.getPlayer(playerName, true) != null)
            m_team2.handleEvent(event);

        if (m_team1.isDead() || m_team2.isDead())
            endGame();
    }

    public void handleEvent(PlayerPosition event) {
        /*  for round state 2:
            since the bot is at 0,0. The only position packets it receives are those
            of player warps, which is illegal when the game is starting
        */
        if (m_fnRoundState == 2) {
            Player p = m_botAction.getPlayer(event.getPlayerID());

            if (p == null)
                return;

            String playerName = p.getPlayerName();
            m_logger.announce("warping " + playerName + " to his team's safe zone");

            if ((m_team1.getPlayer(playerName, true) != null)
                    && (event.getXLocation() / 16 != m_rules.getInt("safe1x"))
                    && (event.getYLocation() / 16 != m_rules.getInt("safe1y")))
                m_botAction.warpTo(event.getPlayerID(), m_rules.getInt("safe1x"), m_rules.getInt("safe1y"));

            if ((m_team2.getPlayer(playerName, true) != null)
                    && (event.getXLocation() / 16 != m_rules.getInt("safe2x"))
                    && (event.getYLocation() / 16 != m_rules.getInt("safe2y")))
                m_botAction.warpTo(event.getPlayerID(), m_rules.getInt("safe2x"), m_rules.getInt("safe2y"));
        } else if (m_fnRoundState == 3) {
            /*  for round state 3:
                bot centers at (centeratx, centeraty), checks if a person is longer than (outofbordertime) below (bordery)
            */
            /*
                if (position OUTSIDE border) then
                 if (outsidestart == 0) then outsidestart = systemtimems
                 else if (systemtimems - outside start) > outofbordertime*500 then give_warning
                 else if (systemtimems - outside start) > outofbordertime*1000 then kick_out_of_game
            */
            if (m_rules.getInt("yborder") != 0) {
                //int xpos = event.getXLocation() / 16;
                int ypos = event.getYLocation() / 16;
                int yborder = m_rules.getInt("yborder");
                int outofbordertime = m_rules.getInt("outofbordertime") * 1000;
                String playerName = m_botAction.getPlayerName(event.getPlayerID());

                if ( playerName == null )
                    return;

                if (ypos > yborder) {
                    MatchPlayer p = m_team1.getPlayer(playerName);
                    MatchPlayer p2 = m_team2.getPlayer(playerName);

                    // We can't settle for whichever one -- have to find the best match
                    if( p != null && p2 != null ) {
                        if( p2.getPlayerName().equalsIgnoreCase(playerName) ) {
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
                                m_logger.sendPrivateMessage(
                                    playerName,
                                    "Go to base! You have " + outofbordertime / 2000 + " seconds before you'll get +1 death",
                                    26);
                            } else if ((sysTime - pSysTime) > outofbordertime) {
                                m_botAction.sendArenaMessage(playerName + " has been given 1 death for being out of base too long.");
                                p.reportDeath();

                                if (m_team1.isDead() || m_team2.isDead())
                                    endGame();
                            }
                        }
                    }
                }
            }
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

    /*
        Parses the SoccerGoal event to the team in which the player is
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
        Can get various weapon info and the player who used it
        Get repel used count

        @param event WeaponFired event
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

    /*
        Checks if lag reports are sent (in the shape of an arenamessage)
    */
    public void handleEvent(Message event) {
        if ((event.getMessageType() == Message.PUBLIC_MESSAGE) && (m_blueoutState == 1) && (m_endGame != null) && (System.currentTimeMillis() - m_timeBOEnabled > 5000)) {
            String name = m_botAction.getPlayerName(event.getPlayerID());
            m_botAction.sendUnfilteredPublicMessage("?cheater " + name + " talking in blueout: " + name + "> " + event.getMessage());
            m_botAction.sendUnfilteredPrivateMessage(event.getPlayerID(), "*warn Do not talk during blueout!");
        }

        if (event.getMessageType() == Message.ARENA_MESSAGE) {
            String msg = event.getMessage();


            if (m_fnRoundState == 1) {
                m_team1.handleEvent(event);
                m_team2.handleEvent(event);
            } else if (m_fnRoundState == 2 && msg.contains("Res:")) {
                m_team1.handleEvent(event);
                m_team2.handleEvent(event);
            } else if (m_fnRoundState == 3) {
                m_lagHandler.handleLagMessage(msg);

                if(msg.contains("Res:")) {
                    m_team1.handleEvent(event);
                    m_team2.handleEvent(event);
                }
            }

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
                if (!m_blueoutDesiredState)
                    m_botAction.toggleBlueOut();
            } else if (msg.equals("Public Messages UNLOCKED")) {
                if (m_blueoutDesiredState)
                    m_botAction.toggleBlueOut();
            }
        }
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
        Collects the available help messages for the player of this class and
        the subclasses
    */
    public ArrayList<String> getHelpMessages(String name, boolean isStaff) {
        ArrayList<String> help = new ArrayList<String>();

        // for everybody
        help.add("!cap                                     - Show the captains of both teams");
        help.add("!myfreq                                  - sets you on your team's frequency");

        if ((m_fnRoundState <= 1) && (m_rules.getInt("pickbyturn") == 1))
            help.add("!notplaying                              - Indicate that you won't play this round");

        help.add("!notplaylist                             - Show all the players who have turned '!notplaying' on");

        if (m_fnRoundState == 3) {
            help.add("!score                                   - Show the current score of both teams");
            help.add("!rating <player>                         - provides realtime stats and rating of the player");
            help.add("!mvp                                     - provides the current mvp");
        };

        // for staff
        if (isStaff) {
            if ((m_fnRoundState == 0) && (m_rules.getInt("pickbyturn") == 1)) {
                help.add("!settime <time in mins>                  - time to racebetween 5 and 30 only for timerace");
                help.add("!startpick                               - start rostering");
            }

            if ((m_fnRoundState == 1)) {
                if (m_team1.isReadyToGo() && m_team2.isReadyToGo()) {
                    help.add("!startgame                               - start the game");
                }

                if (!m_lineUpExtension) {
                    help.add("!add2mins                                - adds 2 mins to lineup submission");
                }
            }

            if (m_fnRoundState == 3)
                help.add("!lag <player>                            - show <player> lag");

            if (m_team1 != null) {
                help.add("-- Prepend your command with !t1- for '" + m_team1.getTeamName() + "', !t2- for '" + m_team2.getTeamName() + "' --");
                help.addAll(m_team1.getHelpMessages(name, isStaff));
            };

            // for others
        } else {
            help.addAll(m_team1.getHelpMessages(name, isStaff));
            help.addAll(m_team2.getHelpMessages(name, isStaff));
        };

        return help;
    };

    public void parseCommand(String name, String command, String[] parameters, boolean isStaff) {
        if (m_rules.getInt("pickbyturn") == 1) {
            if (command.equals("!notplaying"))
                command_notplaying(name, parameters);

            if (command.equals("!notplaylist"))
                command_notplaylist(name, parameters);
        };

        if ((command.equals("!settime")) && (m_fnRoundState == 0) && isStaff)
            command_setTime(name, parameters);

        if ((command.equals("!startpick")) && (m_fnRoundState == 0) && isStaff)
            command_startpick(name, parameters);

        if ((command.equals("!startgame")) && (m_fnRoundState == 1) && isStaff)
            command_startgame(name, parameters);

        if ((command.equals("!add2mins")) && (m_fnRoundState == 1) && isStaff)
            command_add2mins(name, parameters);

        if ((command.equals("!lag")) && (m_fnRoundState == 3) && isStaff)
            command_checklag(name, parameters);

        if ((command.equals("!lagstatus")) && isStaff)
            command_lagstatus(name, parameters);

        if (command.equals("!cap")) {
            m_logger.sendPrivateMessage(name, m_team1.getCaptains() + " is/are captain(s) of " + m_team1.getTeamName());
            m_logger.sendPrivateMessage(name, m_team2.getCaptains() + " is/are captain(s) of " + m_team2.getTeamName());
        };

        if (command.equals("!myfreq"))
            command_myfreq(name, parameters);

        if (command.equals("!score"))
            command_score(name, parameters);

        if (command.equals("!rating") && (m_fnRoundState == 3))
            command_rating(name, parameters);

        if (command.equals("!mvp") && (m_fnRoundState == 3))
            command_mvp(name, parameters);

        if (command.length() > 3) {
            if (command.startsWith("!t1-") && (command.length() > 4)) {
                command = "!" + command.substring(4);
                m_team1.parseCommand(name, command, parameters, isStaff);
            } else if (command.startsWith("!t2-") && (command.length() > 4)) {
                command = "!" + command.substring(4);
                m_team2.parseCommand(name, command, parameters, isStaff);
            } else if ((m_team1.getPlayer(name, true) != null) || (m_team1.isCaptain(name))) {
                m_team1.parseCommand(name, command, parameters, isStaff);
            } else if ((m_team2.getPlayer(name, true) != null) || (m_team2.isCaptain(name))) {
                m_team2.parseCommand(name, command, parameters, isStaff);
            }
        };
    }

    /**
        Method command_mvp.
        @param name The person who got commanded
        @param parameters
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

    /*
        private class MvpCompare implements Comparator
        {
        public MvpCompare() {
        }

        public int compare(Object o1, Object o2) {
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
        Method command_rating.
        @param name The person who got commanded
        @param parameters
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
        @param name The name of the person who messaged the bot
        @param playerName The name of the player to retreive the rating for
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
        Method command_setTime.
        @param name String
        @param parameters String[]
    */
    public void command_setTime(String name, String[] parameters) {
        if (parameters.length > 0) {
            String string = parameters[0];
            Integer time = new Integer(string);

            //the time is also set in !startpick command method
            if (time == null || time.intValue() < 5 || time.intValue() > 40) {
                if (!m_playoff) {
                    setRaceTarget(m_rules.getInt("defaulttarget") * 60); //mins to secs
                    m_logger.sendArenaMessage("Race set to: " + m_rules.getInt("defaulttarget") + " mins");
                    m_logger.sendPrivateMessage(name, "Needs to be between 5 mins and 30 mins - setting default race");
                } else {
                    setRaceTarget(m_rules.getInt("playofftarget") * 60); //mins to secs
                    m_logger.sendArenaMessage("Race set to: " + m_rules.getInt("playofftarget") + " mins");
                    m_logger.sendPrivateMessage(name, "Needs to be between 5 mins and 40 mins - setting default race");
                }
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

            m_logger.sendPrivateMessage(
                name,
                m_team1.getTeamName()
                + " vs. "
                + m_team2.getTeamName()
                + ": "
                + m_team1.getTeamScore() / 60
                + ":"
                + team1leadingZero
                + m_team1.getTeamScore() % 60
                + " - "
                + m_team2.getTeamScore() / 60
                + ":"
                + team2leadingZero
                + m_team2.getTeamScore() % 60);
        } else {
            m_logger.sendPrivateMessage(
                name,
                m_team1.getTeamName() + " vs. " + m_team2.getTeamName() + ": " + m_team1.getTeamScore() + " - " + m_team2.getTeamScore());
        }
    }

    // myfreq
    public void command_myfreq(String name, String[] parameters) {
        Player p = m_botAction.getPlayer(name);
        int nFrequency = 9999;

        if (p != null) {
            if ((p.getSquadName().equalsIgnoreCase(m_team1.getTeamName())) || (m_team1.getPlayer(name, true) != null) || (m_team1.isCaptain(name)))
                nFrequency = m_team1.getFrequency();
            else if ((p.getSquadName().equalsIgnoreCase(m_team2.getTeamName())) || (m_team2.getPlayer(name, true) != null) || (m_team2.isCaptain(name)))
                nFrequency = m_team2.getFrequency();
            else {
                // Long name hack for those that don't exact-match using getPlayer(String, true)
                if( m_team1.getPlayer(name, false) != null )
                    nFrequency = m_team1.getFrequency();
                else if( m_team2.getPlayer(name, false) != null )
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
        int exists = m_notPlaying.indexOf(name.toLowerCase());

        if (exists == -1) {
            m_notPlaying.add(name.toLowerCase());
            String[] tmp = { name };

            if ((m_team1.getPlayer(name, true) != null) && (m_fnRoundState == 1))
                m_team1.command_remove(name, tmp);

            if ((m_team2.getPlayer(name, true) != null) && (m_fnRoundState == 1))
                m_team2.command_remove(name, tmp);

            m_botAction.spec( name );
            m_botAction.spec( name );
            m_logger.sendPrivateMessage(name, "Not Playing mode turned on, captains will be unable to pick you");
            m_logger.setFreq(name, NOT_PLAYING_FREQ);
        } else {
            m_notPlaying.remove(exists);
            m_logger.sendPrivateMessage(name, "notplaying mode turned off, captains will be able to pick you");

            if( m_fnRoundState > 2 ) {
                m_logger.sendPrivateMessage( name, "If you wish to get back on the normal spec frequency, rejoin the arena" );
                m_logger.setFreq( name, NOT_PLAYING_FREQ + 1 );
            } else {
                placeOnSpecFreq( name );
            }
        }
    }

    public void command_notplaylist(String name, String parameters[]) {
        ListIterator<String> i = m_notPlaying.listIterator();
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

        if (winby.equals("timerace")) // 5 mins and 30 mins in secs
        {
            if (!m_playoff && (m_raceTarget < 5 * 60 || m_raceTarget > 30 * 30)) {
                setRaceTarget(m_rules.getInt("defaulttarget") * 60); //mins to secs
                m_logger.sendArenaMessage("Race set to " + m_rules.getInt("defaulttarget") + " mins");
            } else if (m_playoff && (m_raceTarget < 5 * 60 || m_raceTarget > 40 * 30)) {
                setRaceTarget(m_rules.getInt("playofftarget") * 60); //mins to secs
                m_logger.sendArenaMessage("Race set to " + m_rules.getInt("playofftarget") + " mins");
            }
        }

        m_fnRoundState = 1;

        if (m_game.m_fnMatchTypeID > MatchTypeID.NONE && m_game.m_fnMatchTypeID < MatchTypeID.MAX) {
            m_logger.sendArenaMessage("Captains, you have " + m_rules.getInt("lineuptime") + " minutes to set up your lineup correctly");
            m_scheduleTimer = new TimerTask() {
                public void run() {
                    scheduleTimeIsUp();
                }
            };
            m_botAction.scheduleTask(m_scheduleTimer, 60000 * m_rules.getInt("lineuptime"));
            m_botAction.setTimer(m_rules.getInt("lineuptime"));
        } else {
            m_team1.setTurn();
        }
    }

    public void command_startgame(String name, String parameters[]) {
        if ((m_team1.isReadyToGo()) && (m_team2.isReadyToGo())) {
            checkReadyToGo();
        } else {
            m_botAction.sendPrivateMessage(name, "Both of the teams are not ready..");
        }
    }

    public void command_add2mins(String name, String parameters[]) {
        if (!m_lineUpExtension) {
            m_lineUpExtension = true;
            m_botAction.sendArenaMessage("2 minutes will be added to the lineup submission when the timer runs out.", 1);
        } else {
            m_botAction.sendPrivateMessage(name, "2 minute extension has already been enabled, try again during the extension if you need to delay more.");
        }
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

    public void handleLagReport(LagReport report) {
        if (!report.isBotRequest()) {
            m_botAction.privateMessageSpam(report.getRequester(), report.getLagStats());
        }

        MatchPlayer p = m_team1.getPlayer(report.getName(), true);

        if (p == null) {
            p = m_team2.getPlayer(report.getName(), true);
        }

        if (report.isOverLimits()) {
            if (!report.isBotRequest()) {
                m_botAction.sendPrivateMessage(report.getRequester(), report.getLagReport());
            }

            if (p != null && m_botAction.getPlayer(report.getName()).getShipType() != 0 && p.getPlayerState() == MatchPlayer.IN_GAME) {
                m_botAction.sendPrivateMessage(report.getName(), report.getLagReport());
                p.setLagByBot(true);
                m_botAction.spec(report.getName());
                m_botAction.spec(report.getName());
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
            m_botAction.sendSquadMessage(m_team1.getTeamName(), "Both teams have agreed to add an extra player. Max players: " + m_game.getPlayersNum());
            m_botAction.sendSquadMessage(m_team2.getTeamName(), "Both teams have agreed to add an extra player. Max players: " + m_game.getPlayersNum());
            m_team1.setAddPlayer(false);
            m_team2.setAddPlayer(false);
            return true;
        } else {
            if (team.equalsIgnoreCase(m_team1.getTeamName())) {
                m_botAction.sendSquadMessage(m_team2.getTeamName(), m_team1.getTeamName() + " has requested to add an extra player. Captains/Assistants reply with !addplayer to accept the request.");
            } else {
                m_botAction.sendSquadMessage(m_team1.getTeamName(), m_team2.getTeamName() + " has requested to add an extra player. Captains/Assistants reply with !addplayer to accept the request.");
            }

            return false;
        }
    }

    public void checkReadyToGo() {
        if ((m_team1.isReadyToGo()) && (m_team2.isReadyToGo())) {

            if (m_scheduleTimer != null) {
                m_botAction.cancelTask(m_scheduleTimer);
            };

            m_botAction.setTimer(0);

            m_logger.sendArenaMessage("Both teams are ready, game starts in 30 seconds", 2);

            m_logger.setDoors(255);

            m_team1.warpTo(m_rules.getInt("safe1x"), m_rules.getInt("safe1y"));

            m_team2.warpTo(m_rules.getInt("safe2x"), m_rules.getInt("safe2y"));

            m_botAction.move(0, 0);

            m_fnRoundState = 2;

            checkBlueout();

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
        };
    };

    // gets called by m_startGame TimerTask.
    @SuppressWarnings("unchecked")
    public void startGame() {
        if (m_rules.getInt("storegame") != 0)
            events.add(MatchRoundEvent.roundStart());

        if (!m_playoff || !m_rules.getString("winby").equals("timerace"))
            m_generalTime = m_rules.getInt("time") * 60;
        else if (m_playoff && m_rules.getString("winby").equals("timerace"))
            m_generalTime = m_rules.getInt("playofftime") * 60;

        m_myObjects = m_botAction.getObjectSet();
        updateScores = new TimerTask() {
            public void run() {
                do_updateScoreBoard();
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
            };
        } else {
            m_team1.warpTo(m_rules.getInt("safe1xout"), m_rules.getInt("safe1yout"));
            m_team2.warpTo(m_rules.getInt("safe2xout"), m_rules.getInt("safe2yout"));
            m_logger.setDoors(0);
        };

        m_team1.signalStartToPlayers();

        m_team2.signalStartToPlayers();

        m_botAction.setReliableKills(1);

        m_logger.scoreResetAll();

        m_logger.shipResetAll();

        m_logger.resetFlagGame();

        m_logger.sendArenaMessage("Go go go!", 104);

        m_botAction.showObject(m_rules.getInt("obj_gogogo"));

        m_timeStartedms = System.currentTimeMillis();

        //this is for timerace only
        if ((m_rules.getString("winby")).equals("timerace")) {
            m_raceTimer = new TimerTask() {
                public void run() {
                    m_team1.addTimePoint();
                    m_team2.addTimePoint();
                };
            };
            m_botAction.scheduleTaskAtFixedRate(m_raceTimer, 1000, 1000);
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
        };

        m_closeDoors = new TimerTask() {
            public void run() {
                m_logger.setDoors(255);
            };
        };

        m_botAction.scheduleTask(m_closeDoors, 10000);

        if (m_rules.getInt("time") != 0) {
            if (!m_playoff || !m_rules.getString("winby").equals("timerace"))
                m_botAction.setTimer(m_rules.getInt("time"));
            else if (m_playoff && m_rules.getString("winby").equals("timerace"))
                m_botAction.setTimer(m_rules.getInt("playofftime"));

            m_endGame = new TimerTask() {
                public void run() {
                    endGame();
                };
            };

            if (!m_playoff || !m_rules.getString("winby").equals("timerace"))
                m_botAction.scheduleTask(m_endGame, 60000 * m_rules.getInt("time"));
            else if (m_playoff && m_rules.getString("winby").equals("timerace"))
                m_botAction.scheduleTask(m_endGame, 60000 * m_rules.getInt("playofftime"));
        };

        m_fnRoundState = 3;
    };

    // declare winner. End the round.
    public void endGame() {
        if (m_endGame != null)
            m_botAction.cancelTask(m_endGame);

        if (m_raceTimer != null)
            m_botAction.cancelTask(m_raceTimer);

        m_fnTeam1Score = m_team1.getTeamScore();
        m_fnTeam2Score = m_team2.getTeamScore();

        if (m_fnTeam1Score == m_fnTeam2Score) {
            String ondraw = m_rules.getString("ondraw");

            if (ondraw == null) ondraw = "quit";

            if ((ondraw.equalsIgnoreCase("extension")) && (!m_fbExtension) && (!m_team1.isForfeit())) {
                int extTime = m_rules.getInt("extensiontime");

                if (extTime != 0) {
                    m_logger.sendArenaMessage("The scores are tied. The game will be extended for " + extTime + " minutes.", 2);
                    m_generalTime = extTime * 60;
                    m_fbExtension = true;
                    m_endGame = new TimerTask() {
                        public void run() {
                            endGame();
                        };
                    };
                    m_botAction.scheduleTask(m_endGame, extTime * 1000 * 60);
                    m_botAction.setTimer(extTime);
                };
            } else {
                m_game.reportEndOfRound(m_fbAffectsEntireGame);
                m_botAction.showObject(m_rules.getInt("obj_gameover"));
                return;
            };
        } else {

            m_timeEnded = new java.util.Date();

            if (updateScores != null)
                m_botAction.cancelTask(updateScores);

            do_updateScoreBoard();
            m_botAction.showObject(m_rules.getInt("obj_gameover"));

            m_team1.signalEndToPlayers();
            m_team2.signalEndToPlayers();

            toggleBlueout(false);

            if (m_rules.getString("winby").equals("timerace")) {
                //bug fix
                String team1leadingZero = "";
                String team2leadingZero = "";

                if (m_team1.getTeamScore() % 60 < 10)
                    team1leadingZero = "0";

                if (m_team2.getTeamScore() % 60 < 10)
                    team2leadingZero = "0";

                m_logger.sendArenaMessage(
                    "Result of "
                    + m_team1.getTeamName()
                    + " vs. "
                    + m_team2.getTeamName()
                    + ": "
                    + m_fnTeam1Score / 60
                    + ":"
                    + team1leadingZero
                    + m_team1.getTeamScore() % 60
                    + " - "
                    + m_fnTeam2Score / 60
                    + ":"
                    + team2leadingZero
                    + m_team2.getTeamScore() % 60,
                    5);
            } else
                m_logger.sendArenaMessage("Result of " + m_team1.getTeamName() + " vs. " + m_team2.getTeamName() + ": " + m_fnTeam1Score + " - " + m_fnTeam2Score, 5);

            displayScores();

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
            };

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
            };

            m_signalEndOfRound = new TimerTask() {
                public void run() {
                    if (m_myObjects != null) {
                        m_myObjects.hideAllObjects();
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
        if (m_lineUpExtension) {
            m_botAction.setTimer(2);
            m_scheduleTimer = new TimerTask() {
                public void run() {
                    scheduleTimeIsUp();
                };
            };
            m_botAction.scheduleTask(m_scheduleTimer, 120000);
            m_botAction.sendArenaMessage("Lineup submission has been extended by 2 mins", 2);
            m_lineUpExtension = false;
            return;
        }

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
            m_logger.sendArenaMessage("Time is up. " + m_team1.getTeamName() + " didn't have any player rostered at all, and thus forfeits the ENTIRE game", 2);
            endGame();
        } else if ((m_team2.getPlayersReadyToPlay() == 0) && (m_team1.getPlayersReadyToPlay() != 0)) {
            m_fbAffectsEntireGame = true;
            m_team1.forfeitWin();
            m_team2.forfeitLoss();
            m_logger.sendArenaMessage("Time is up. " + m_team2.getTeamName() + " didn't have any player rostered at all, and thus forfeits the ENTIRE game", 2);
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
            m_logger.sendArenaMessage("Time is up. roster are OK.");
            checkReadyToGo();
        }

    }

    public void toggleBlueout(boolean blueout) {
        m_blueoutDesiredState = blueout;

        if((m_blueoutState == -1) || ((m_blueoutState == 0) && (m_blueoutDesiredState)) || ((m_blueoutState == 1) && (!m_blueoutDesiredState))) {
            if (m_blueoutDesiredState) {
                m_logger.sendArenaMessage("Blueout has been enabled. Staff, don't speak in public from now on.");
                m_timeBOEnabled = System.currentTimeMillis();
                m_blueoutState = 1;
            } else if (m_blueoutState == 1) {
                m_logger.sendArenaMessage("Blueout has been disabled. You can speak in public now.");
                m_blueoutState = 0;
            }

            m_botAction.toggleBlueOut();
        }
    }

    public void requestBlueout(boolean blueout) {
        m_blueoutDesiredState = blueout;

        if (m_fnRoundState >= 2)
            toggleBlueout(m_blueoutDesiredState);
    }

    public void checkBlueout() {
        if (m_rules.getInt("blueout") == 2)
            toggleBlueout(true);
        else if ((m_rules.getInt("blueout") == 1) && (m_blueoutDesiredState))
            toggleBlueout(true);
        else
            toggleBlueout(false);
    }

    public void do_updateScoreBoard() {
        if (m_myObjects != null) {
            m_myObjects.hideAllObjects();
            m_generalTime -= 1;
            String team1Score;
            String team2Score;

            team1Score = "" + m_team1.getTeamScore();
            team2Score = "" + m_team2.getTeamScore();

            //If lb display twlb scoreboard
            if( m_rules.getString("winby").equals("timerace") ) {
                int t1s = Integer.parseInt( team1Score );
                int t2s = Integer.parseInt( team2Score );

                int team1Minutes = (int)Math.floor( t1s / 60.0 );
                int team2Minutes = (int)Math.floor( t2s / 60.0 );
                int team1Seconds = t1s - team1Minutes * 60;
                int team2Seconds = t2s - team2Minutes * 60;

                //Team 1
                m_myObjects.showObject( 100 + team1Seconds % 10 );
                m_myObjects.showObject( 110 + (team1Seconds - team1Seconds % 10) / 10 );
                m_myObjects.showObject( 130 + team1Minutes % 10 );
                m_myObjects.showObject( 140 + (team1Minutes - team1Minutes % 10) / 10 );

                //Team 2
                m_myObjects.showObject( 200 + team2Seconds % 10 );
                m_myObjects.showObject( 210 + (team2Seconds - team2Seconds % 10) / 10 );
                m_myObjects.showObject( 230 + team2Minutes % 10 );
                m_myObjects.showObject( 240 + (team2Minutes - team2Minutes % 10) / 10 );
            }
            //Else display ld lj on normal scoreboard
            // Note: Scoreboard is not logical, scores for team 1 are object ids 200+, scores for team 2 are object ids 100+
            //       This is only caused in TWLD and TWLJ arenas as they are using the lvz 'twl.lvz'
            else {
                for (int i = team1Score.length() - 1; i > -1; i--)
                    m_myObjects.showObject(Integer.parseInt("" + team1Score.charAt(i)) + 200 + (team1Score.length() - 1 - i) * 10);

                for (int i = team2Score.length() - 1; i > -1; i--)
                    m_myObjects.showObject(Integer.parseInt("" + team2Score.charAt(i)) + 100 + (team2Score.length() - 1 - i) * 10);
            }

            if (m_generalTime >= 0) {
                int seconds = m_generalTime % 60;
                int minutes = (m_generalTime - seconds) / 60;
                m_myObjects.showObject(730 + ((minutes - minutes % 10) / 10));
                m_myObjects.showObject(720 + (minutes % 10));
                m_myObjects.showObject(710 + ((seconds - seconds % 10) / 10));
                m_myObjects.showObject(700 + (seconds % 10));
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

            m_myObjects.showObject(t);
        }

    }

    public void displayScores() {
        boolean duelG = m_rules.getString("winby").equalsIgnoreCase("kills");
        boolean wbG = m_rules.getInt("ship") == 1;
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

        if (m_blueoutState == 1)
            m_botAction.toggleBlueOut();

        if (updateScores != null)
            m_botAction.cancelTask(updateScores);

        if (m_myObjects != null)
            m_myObjects.hideAllObjects();

        m_botAction.setObjects();
        m_generalTime = 0;
    }

    /**
        Returns the m_raceTarget.
        @return int
    */
    public int getRaceTarget() {
        return m_raceTarget;
    }

    /**
        Sets the m_raceTarget.
        @param raceTarget The raceTarget to set
    */
    public void setRaceTarget(int raceTarget) {
        m_raceTarget = raceTarget;
    }

    /**
        This class is used to store event statistics about the current round
        It used JSONArray because it is already serializable, PHP-compatible and lightweight
        This information will be only used to produce a graphic

        EventType number must be in sync with those on the website..
        Don't even change one.

        If you need to add a new event, use the last number and do +1

        See Arobas+
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

