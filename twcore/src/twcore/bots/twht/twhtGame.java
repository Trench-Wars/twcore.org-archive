/**
 * 
 */
package twcore.bots.twht;

import java.util.LinkedList;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;

import twcore.core.BotAction;
import twcore.core.events.BallPosition;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.SoccerGoal;
import twcore.core.game.Player;
import twcore.core.util.Tools;

/**
 * @author Ian
 * 
 */
public class twhtGame {

    BotAction ba;

    TimerTask intermission;
    TimerTask delay;
    TimerTask goalDelay;
    TimerTask taskDelay;

    twhtTeam m_team1;
    twhtTeam m_team2;
    twht twht;

    int m_fnTeam1ID;
    int m_fnTeam2ID;
    int m_matchID;
    int fnTeam1Score;
    int fnTeam2Score;
    int requestRecordNumber;

    String m_fcTeam1Name;
    String m_fcTeam2Name;
    String m_fcRefName;

    String goalScorer;
    String assistOne = "";
    String assistTwo = "";

    boolean voteInProgress;
    boolean isDatabase;
    boolean isIntermission;

    TreeSet<String> m_judge = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    TreeMap<String, Integer> m_penalties = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
    TreeMap<String, String> m_officials = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    TreeMap<Integer, RefRequest> refRequest = new TreeMap<Integer, RefRequest>();

    LinkedList<twhtRound> m_rounds = new LinkedList<twhtRound>();
    twhtRound m_curRound;

    public twhtGame(int matchID, String fcRefName, String fcTeam1Name, int fnTeam1ID, int fnTeam2ID, String fcTeam2Name, BotAction botAction, twht TWHT, boolean noDatabase) {
        ba = botAction;
        twht = TWHT;
        isDatabase = noDatabase;
        m_fnTeam1ID = fnTeam1ID;
        m_fnTeam2ID = fnTeam2ID;
        m_matchID = matchID;
        m_fcTeam1Name = fcTeam1Name;
        m_fcTeam2Name = fcTeam2Name;
        m_fcRefName = fcRefName;
        setupTeams();
        setupRound(1);
    }

    public void setupTeams() {
        m_team1 = new twhtTeam(m_fcTeam1Name, m_fnTeam1ID, 0, 1, this, ba);
        m_team2 = new twhtTeam(m_fcTeam2Name, m_fnTeam2ID, 1, 2, this, ba);
    }

    public void setupRound(int roundNumber) {
        if (roundNumber == 1) {
            m_curRound = new twhtRound(m_matchID, m_fnTeam1ID, m_fnTeam2ID, roundNumber, m_fcTeam1Name, m_fcTeam2Name, m_fcRefName, this);
            m_rounds.add(m_curRound);
        } else {
            twhtRound newRound = new twhtRound(m_matchID, m_fnTeam1ID, m_fnTeam2ID, roundNumber, m_fcTeam1Name, m_fcTeam2Name, m_fcRefName, this);
            m_curRound = newRound;
            m_rounds.add(newRound);
        }
        m_curRound.addPlayers();
    }

    public void reportEndOfRound(int roundNum) {
        final int roundNumber = roundNum;
        m_curRound.cancel();

        if (roundNumber == 1) {
            ba.sendArenaMessage("Time is up! Round one has ended. ", 5);
            ba.sendArenaMessage("Score after one period: " + m_fcTeam1Name + ": " + fnTeam1Score + " vs " + m_fcTeam2Name + ": " + fnTeam2Score);

            delay = new TimerTask() {
                @Override
                public void run() {
                    ba.sendArenaMessage("Round two will begin after a 2 minute intermission", 2);
                    isIntermission = true;

                    intermission = new TimerTask() {
                        public void run() {
                            setupRound(roundNumber + 1);
                            isIntermission = false;
                        }
                    };
                    ba.scheduleTask(intermission, Tools.TimeInMillis.MINUTE * 2);
                }
            };
            ba.scheduleTask(delay, Tools.TimeInMillis.SECOND * 10);

        } else if (roundNumber == 2) {
            if (fnTeam1Score > fnTeam2Score) {
                doEndGame(m_fcTeam1Name, m_fcTeam2Name, fnTeam1Score, fnTeam2Score);
            } else if (fnTeam2Score > fnTeam1Score) {
                doEndGame(m_fcTeam2Name, m_fcTeam1Name, fnTeam2Score, fnTeam1Score);
            } else if (fnTeam2Score == fnTeam1Score) {
                ba.sendArenaMessage("Time is up! The round is over.", 5);
                ba.sendArenaMessage("Score is tied! This game is going into overtime.");
                isIntermission = true;

                delay = new TimerTask() {
                    @Override
                    public void run() {
                        ba.sendArenaMessage("Round two will begin after a 2 minute intermission", 2);

                        intermission = new TimerTask() {
                            public void run() {
                                setupRound(roundNumber + 1);
                                isIntermission = false;
                            }
                        };
                        ba.scheduleTask(intermission, Tools.TimeInMillis.MINUTE * 2);
                    }
                };
                ba.scheduleTask(delay, Tools.TimeInMillis.SECOND * 10);
            }
        } else if (roundNumber == 3) {
            if (fnTeam1Score > fnTeam2Score) {
                doEndGame(m_fcTeam1Name, m_fcTeam2Name, fnTeam1Score, fnTeam2Score);
            } else if (fnTeam2Score > fnTeam1Score) {
                doEndGame(m_fcTeam2Name, m_fcTeam1Name, fnTeam2Score, fnTeam1Score);
            } else if (fnTeam2Score == fnTeam1Score) {
                ba.sendArenaMessage("Time is up! Overtime has ended", 5);
                ba.sendArenaMessage("The score is still tied, Prepare for a shootout.", 2);
                delay = new TimerTask() {
                    @Override
                    public void run() {
                        setupRound(roundNumber + 1);
                    }
                };
                ba.scheduleTask(delay, Tools.TimeInMillis.SECOND * 10);
            }

        }
    }

    public void doEndGame(String winner, String loser, int winScore, int loseScore) {
        ba.sendArenaMessage(" ------- GAME OVER ------- ", 5);
        ba.sendArenaMessage(winner + " vs. " + loser + ": " + winScore + " - " + loseScore);
        ba.sendArenaMessage(winner + " wins this game!");
        resetVariables();
        twht.endGame();
    }

    /**
     * The event that is triggered at the time a player leaves the arena
     */
    public void handleEvent(FrequencyShipChange event) {
        if (m_curRound != null) {
            int shipType;
            Player p;
            int playerID;

            playerID = event.getPlayerID();
            p = ba.getPlayer(playerID);

            if (p == null)
                return;

            if (p.equals(ba.getBotName()))
                return;

            shipType = p.getShipType();
            if (shipType == Tools.Ship.SPECTATOR) {

                twhtTeam t = null;

                if (m_team1.isPlayer(p.getPlayerName())) {
                    t = m_team1;
                } else if (m_team2.isPlayer(p.getPlayerName())) {
                    t = m_team1;
                } else {
                    return;
                }

                twhtPlayer pA;
                pA = t.searchPlayer(p.getPlayerName());

                if (pA != null) {
                    if (pA.getPlayerState() == 1 || pA.getPlayerState() == 4) {
                        t.doLagout(p.getPlayerName());
                    }
                }
            }
        }
    }

    /**
     * The event that is triggered at the time a player leaves the arena
     */
    public void handleEvent(PlayerEntered event) {

    }

    /**
     * The event that is triggered at the time a player leaves the arena
     */
    public void handleEvent(PlayerLeft event) {
        if (m_curRound != null) {
            int playerID;
            String player;

            playerID = event.getPlayerID();
            player = ba.getPlayerName(playerID);

            if (player == null)
                return;

            if (player.equals(ba.getBotName()))
                return;

            twhtTeam t = null;

            if (m_team1.isPlayer(player)) {
                t = m_team1;
            } else if (m_team2.isPlayer(player)) {
                t = m_team1;
            } else {
                return;
            }

            twhtPlayer pA;
            pA = t.searchPlayer(player);

            if (pA != null) {
                if (pA.getPlayerState() == 1 || pA.getPlayerState() == 4) {
                    t.doLagout(pA.getPlayerName());
                }
            }
        }
    }

    /**
     * The event that is triggered at the time a player dies
     */
    public void handleEvent(PlayerDeath event) {

    }

    /**
     * The event that is triggered at the time a a soccer goal is scored
     */
    public void handleEvent(SoccerGoal event) {
        if (m_curRound != null) {
            if (m_curRound.getRoundState() == 1 && !m_curRound.ballPlayer.isEmpty()) {
                m_curRound.m_fnRoundState = 3;
                goalScorer = m_curRound.ballPlayer.pop();
                ba.sendArenaMessage("Goal is under review.", 2);
                startReview();
                if (m_curRound.ballPlayer.size() > 1) {
                    m_curRound.ballPlayer.pop();
                    assistOne = m_curRound.ballPlayer.pop();
                    if (assistOne == goalScorer)
                        assistOne = " ";
                    if (m_curRound.ballPlayer.size() > 1) {
                        m_curRound.ballPlayer.pop();
                        assistTwo = m_curRound.ballPlayer.pop();
                        if (assistTwo == goalScorer || assistTwo == assistOne)
                            assistTwo = " ";
                        if (assistOne == " ") {
                            assistOne = assistTwo;
                            assistTwo = " ";
                        }
                        if (assistTwo == " " && m_curRound.ballPlayer.size() > 1) {
                            m_curRound.ballPlayer.pop();
                            assistTwo = m_curRound.ballPlayer.pop();
                            if (assistTwo == goalScorer || assistTwo == assistOne) {
                                assistTwo = " ";
                            }
                        }
                        m_curRound.ballPlayer.clear();
                    }
                }
            }
        }
    }

    /**
     * The event that is triggered at the time the ball moves.
     */
    public void handleEvent(BallPosition event) {
        if (m_curRound != null) {
            m_curRound.handleEvent(event);
        }
    }

    /**
     * This method checks if a lagout request is legal and if so, it will send the request to the referee.
     * 
     * @param name
     * @param msg
     */
    public void reqLagoutPlayer(String name, String msg) {
        twhtTeam team;
        twhtPlayer pA;

        team = getPlayerTeam(name);

        if (team != null) {
            pA = team.searchPlayer(name);

            if (pA.getPlayerState() == 3) {
                refRequest.put(getNextRecordNumber(), new RefRequest(getNextRecordNumber(), m_curRound.getRoundNum(), 6, name, team.getTeamName(), name));
            } else {
                ba.sendPrivateMessage(name, "You are not lagged out.");
            }
        }
    }

    /**
     * This method checks if a add player request is legal and if so, it will send the request to the referee.
     * 
     * @param name
     * @param msg
     */
    public void reqAddPlayer(String name, String msg) {
        String teamName;
        String playerName;
        String[] splitCmd;
        int shipNum;

        if (m_team1.isCaptain(name)) {
            teamName = m_fcTeam1Name;
        } else if (m_team2.isCaptain(name)) {
            teamName = m_fcTeam2Name;
        } else {
            return;
        }

        if (msg.contains(":")) {
            splitCmd = msg.split(":");
            if (splitCmd.length == 2) {
                try {
                    shipNum = Integer.parseInt(splitCmd[1]);
                } catch (NumberFormatException e) {
                    return;
                }

                playerName = ba.getFuzzyPlayerName(splitCmd[0]);
                //            if (ba.getOperatorList().isBotExact(playerName)) {
                //                ba.sendPrivateMessage(name, "Error: Pick again, bots are not allowed to play.");
                //                return;
                //            }
                if (playerName == null || shipNum > 8 || shipNum < 1) {
                    ba.sendPrivateMessage(name, "Player not found or Invalid Ship. Please try again.");
                    return;
                }
                if ((m_team1.isPlayer(playerName) && teamName == m_fcTeam2Name) || (m_team2.isPlayer(playerName) && teamName == m_fcTeam1Name)) {
                    ba.sendPrivateMessage(name, "Player is already on the other team");
                    return;
                } else if ((m_team1.isPlayer(playerName) && teamName == m_fcTeam1Name) || (m_team2.isPlayer(playerName) && teamName == m_fcTeam2Name)) {
                    ba.sendPrivateMessage(name, "Player is already on the team.");
                    return;
                }
                refRequest.put(getNextRecordNumber(), new RefRequest(getNextRecordNumber(), m_curRound.getRoundNum(), 0, name, teamName, playerName + ":" + shipNum));
                ba.sendPrivateMessage(name, "Request to add: " + playerName + " in ship " + shipNum + " has been sent to the referee.");
            }
        } else {
            ba.sendPrivateMessage(name, "Invalid format. Please use !add <name>:ship#");
        }
    }

    /**
     * This method checks if a remove is legal and if so, it will send the request to the referee.
     * 
     * @param name
     * @param msg
     */
    public void reqRemovePlayer(String name, String msg) {
        twhtTeam team;
        twhtPlayer playerA;

        if (m_team1.isCaptain(name)) {
            team = m_team1;
        } else if (m_team2.isCaptain(name)) {
            team = m_team2;
        } else {
            return;
        }

        playerA = team.searchPlayer(msg);

        if (playerA != null) {
            refRequest.put(getNextRecordNumber(), new RefRequest(getNextRecordNumber(), m_curRound.getRoundNum(), 4, name, team.getTeamName(), playerA.getPlayerName()));
            ba.sendPrivateMessage(name, "Request to remove player: " + playerA.getPlayerName() + " has been sent to the referee.");
        } else {
            ba.sendPrivateMessage(name, "Player trying to be removed cannot be found on your team.");
            return;
        }
    }

    /**
     * This method checks if a switch is legal and if so, it will send the request to the referee.
     * 
     * @param name
     * @param msg
     */
    public void reqSwitchPlayer(String name, String msg) {
        twhtTeam team;
        twhtPlayer playerA;
        twhtPlayer playerB;

        String[] splitCmd;

        if (m_team1.isCaptain(name)) {
            team = m_team1;
        } else if (m_team2.isCaptain(name)) {
            team = m_team2;
        } else {
            return;
        }

        if (msg.contains(":")) {

            splitCmd = msg.split(":");

            if (splitCmd.length == 2) {
                String playerAName = splitCmd[0];
                String playerBName = splitCmd[1];

                playerA = team.searchPlayer(playerAName);
                playerB = team.searchPlayer(playerBName);

                if (playerA != null && playerB != null) {
                    if ((playerA.getPlayerState() == 1 || playerA.getPlayerState() == 4) && (playerB.getPlayerState() == 1 || playerB.getPlayerState() == 4)) {
                        refRequest.put(getNextRecordNumber(), new RefRequest(getNextRecordNumber(), m_curRound.getRoundNum(), 2, name, team.getTeamName(), playerA.getPlayerName() + ":"
                                + playerB.getPlayerName()));
                        ba.sendPrivateMessage(name, "Request to switch: " + playerA.getPlayerName() + " with " + playerB.getPlayerName() + "has been sent to the referee.");
                    }
                } else {
                    ba.sendPrivateMessage(name, "One of the players cannot be found or cannot be switched at this moment.");
                }
            } else {
                ba.sendPrivateMessage(name, "Invalid format. Please use !switch <name>:<name>");
            }
        }
    }

    /**
     * This method checks if a substitution is legal and if so, it will send the request to the referee.
     * 
     * @param name
     * @param msg
     */
    public void reqSubPlayer(String name, String msg) {
        twhtTeam team;
        twhtTeam team2;
        twhtPlayer playerA;

        String[] splitCmd;
        int shipNum;

        if (m_team1.isCaptain(name)) {
            team = m_team1;
        } else if (m_team2.isCaptain(name)) {
            team = m_team2;
        } else {
            return;
        }

        if (msg.contains(":")) {

            splitCmd = msg.split(":");
            if (splitCmd.length == 3) {

                try {
                    shipNum = Integer.parseInt(splitCmd[2]);
                } catch (NumberFormatException e) {
                    return;
                }

                String playerAName = splitCmd[0];
                String playerBName;

                playerA = team.searchPlayer(playerAName);

                if (playerA != null) {
                    if (playerA.getPlayerState() == 1 || playerA.getPlayerState() == 3) {
                        playerBName = ba.getFuzzyPlayerName(splitCmd[1]);
                        team2 = getPlayerTeam(playerBName);
                        if (team2 == team)
                            team = null;

                        if (playerBName != null && team2 == null) {
                            //                        if (ba.getOperatorList().isBotExact(playerBName)) {
                            //                            ba.sendPrivateMessage(name, "Error: Pick again, bots are not allowed to play.");
                            //                            return;
                            //                        }

                            if (shipNum <= 8 && shipNum >= 1) {
                                refRequest.put(getNextRecordNumber(), new RefRequest(getNextRecordNumber(), m_curRound.m_roundNum, 1, name, team.getTeamName(), playerA.getPlayerName() + ":"
                                        + playerBName + ":" + shipNum));
                                ba.sendPrivateMessage(name, "Request to sub: " + playerA.getPlayerName() + " with " + playerBName + " has been sent to the referee.");
                            } else {
                                ba.sendPrivateMessage(name, "Invalid ship number please try again..");
                                return;
                            }
                        } else {
                            ba.sendPrivateMessage(name, "Player trying to be subbed in cannot be found or is already playing.");
                            return;
                        }
                    }
                } else {
                    ba.sendPrivateMessage(name, "Cannot sub for player, player is not on your team.");
                }
            } else {
                ba.sendPrivateMessage(name, "Invalid format. Please use !sub <name>:<name>:ship#");
            }
        }
    }

    /**
     * This method checks if a ship change is legal and if so, it will send the request to the referee.
     * 
     * @param name
     * @param msg
     */
    public void reqChangePlayer(String name, String msg) {
        twhtTeam team;
        twhtPlayer playerA;

        String[] splitCmd;
        int shipNum;

        if (m_team1.isCaptain(name)) {
            team = m_team1;
        } else if (m_team2.isCaptain(name)) {
            team = m_team2;
        } else {
            return;
        }

        if (msg.contains(":")) {
            splitCmd = msg.split(":");

            if (splitCmd.length == 2) {

                try {
                    shipNum = Integer.parseInt(splitCmd[1]);
                } catch (NumberFormatException e) {
                    return;
                }

                String playerAName = splitCmd[0];

                playerA = team.searchPlayer(playerAName);

                if (playerA != null && (shipNum <= 8 && shipNum >= 1)) {
                    if (playerA.getPlayerState() == 1 || playerA.getPlayerState() == 4) {
                        refRequest.put(getNextRecordNumber(), new RefRequest(getNextRecordNumber(), m_curRound.m_roundNum, 3, name, team.getTeamName(), playerA.getPlayerName() + ":" + shipNum));
                        ba.sendPrivateMessage(name, "Request to change: " + playerA.getPlayerName() + " to " + shipNum + " has been sent to the referee.");
                    }
                } else {
                    ba.sendPrivateMessage(name, "PLayer cannot be changed, please try again.");
                    return;
                }
            } else {
                ba.sendPrivateMessage(name, "Invalid format. Please use !change <name>:ship#");
            }
        }
    }

    /**
     * Adds a penalty on a player.
     * 
     * @param name
     * @param msg
     */
    public void setPenalty(String name, String msg) {
        if (m_curRound != null) {
            twhtTeam team;
            twhtPlayer playerA;
            int freq;
            int penaltyTime;
            int penaltySeconds;
            String[] splitCmd;
            if (m_curRound.getRoundState() != 0) {
                if (msg.contains(":")) {
                    splitCmd = msg.split(":");

                    if (splitCmd.length == 3) {

                        try {
                            penaltySeconds = Integer.parseInt(splitCmd[1]);
                        } catch (NumberFormatException e) {
                            return;
                        }

                        team = getPlayerTeam(splitCmd[0]);

                        if (team != null) {
                            playerA = team.searchPlayer(splitCmd[0]);
                            freq = team.getFrequency();

                            penaltyTime = m_curRound.getIntTime() + penaltySeconds;

                            playerA.setPenalty(penaltyTime);

                            ba.sendArenaMessage("Penalty set for " + penaltySeconds + " seconds on " + playerA.getPlayerName() + " for " + splitCmd[2], 23);

                            if (freq == 0)
                                ba.warpTo(playerA.getPlayerName(), 500, 442);
                            if (freq == 1)
                                ba.warpTo(playerA.getPlayerName(), 520, 442);
                        } else {
                            ba.sendPrivateMessage(name, "Player not found on either team.");
                        }
                    } else {
                        ba.sendPrivateMessage(name, "Invalid format. Please use !penalty <name>:seconds#:reason");
                    }
                }
            }
        }
    }

    /**
     * Removes a penalty on a player.
     * 
     * @param name
     * @param msg
     */
    public void removePenalty(String name, String msg) {
        twhtTeam team;
        twhtPlayer playerA;
        Player p;
        int freq;

        team = getPlayerTeam(msg);

        if (team != null) {
            playerA = team.searchPlayer(msg);
            freq = team.getFrequency();

            if (playerA.getPenalty() > 0) {
                ba.sendArenaMessage("Penalty has expired for: " + playerA.getPlayerName(), 2);

                if (playerA.getPreviousState() == 1) {
                    p = ba.getPlayer(playerA.getPlayerName());

                    if (p != null) {
                        if (freq == 0)
                            ba.warpTo(playerA.getPlayerName(), 508, 449);
                        if (freq == 1)
                            ba.warpTo(playerA.getPlayerName(), 515, 449);

                        playerA.resetPenalty();
                        playerA.returnedToGame();
                    }
                } else if (playerA.getPreviousState() == 3 || playerA.getPlayerState() == 3 || playerA.getPlayerState() == 2) {
                    playerA.resetPenalty();
                }
            } else {
                ba.sendPrivateMessage(name, "Player does not have a penalty against them.");
            }
        } else {
            ba.sendPrivateMessage(name, "Player not found on either team.");
        }
    }

    /**
     * Warps player to the penalty box.
     */
    public void warpPenalty(String name, String msg) {
        twhtTeam team;
        twhtPlayer playerA;
        Player p;
        int freq;

        team = getPlayerTeam(msg);

        if (team != null) {
            playerA = team.searchPlayer(msg);
            freq = team.getFrequency();
            p = ba.getPlayer(playerA.getPlayerName());

            if (p != null) {
                if (freq == 0)
                    ba.warpTo(playerA.getPlayerName(), 500, 442);
                if (freq == 1)
                    ba.warpTo(playerA.getPlayerName(), 520, 442);
            }
        } else {
            ba.sendPrivateMessage(name, "Player not found on either team.");
        }
    }

    /**
     * This method handles the timeout when requested by a team.
     * 
     * @param name
     * @param msg
     */
    public void cmd_timeOut(String name, String msg) {
        String teamName;
        if (m_team1.isCaptain(name)) {
            teamName = m_fcTeam1Name;
            refRequest.put(getNextRecordNumber(), new RefRequest(getNextRecordNumber(), m_curRound.getRoundNum(), 5, name, teamName, name));
        } else if (m_team2.isCaptain(name)) {
            teamName = m_fcTeam2Name;
            refRequest.put(getNextRecordNumber(), new RefRequest(getNextRecordNumber(), m_curRound.getRoundNum(), 5, name, teamName, name));
        } else {
            return;
        }
    }

    public void setRound(String name, String msg) {
        int roundNumber;

        if (m_curRound != null && m_curRound.getRoundState() == 0) {

            try {
                roundNumber = Integer.parseInt(msg);
            } catch (NumberFormatException e) {
                return;
            }

            if (roundNumber > 0 && roundNumber < 3)
                setupRound(roundNumber);
        }

    }

    public void setTime(String name, String msg) {

        if (m_curRound != null) {
            m_curRound.changeTime(name, msg);
        }
    }

    public void setFreqs(int roundNum) {
        if (roundNum == 1 || roundNum == 3) {
            m_team1.setFrequency(0);
            m_team2.setFrequency(1);
        } else if (roundNum == 2) {
            m_team1.setFrequency(1);
            m_team2.setFrequency(0);
        }
    }

    /**
     * This method will get the votes of the different players.
     * 
     * @param name
     * @param msg
     */
    public void getVote(String name, String vote) {
        if (m_curRound != null) {
            if (voteInProgress && m_judge.contains(name)) {
                int clVote = 0;
                int lagVote = 0;
                int gkVote = 0;
                int crVote = 0;
                int ogVote = 0;

                m_officials.put(name, vote);

                for (String votes : m_officials.values()) {
                    if (votes == "clean")
                        clVote++;
                    if (votes == "lag")
                        lagVote++;
                    if (votes == "goalieKill")
                        gkVote++;
                    if (votes == "crease")
                        crVote++;
                    if (votes == "ownGoal")
                        ogVote++;
                }
                ba.sendTeamMessage(name + " has voted " + vote + ".");
                ba.sendTeamMessage("Totals: CLEAN(" + clVote + ")  LAG(" + lagVote + ") GK(" + gkVote + ")  CR(" + crVote + ") OG(" + ogVote + ")");
            }
        }
    }

    /**
     * This method checks if player is on a team and then returns the frequency that the team is currently
     * 
     * @param name
     * @return
     */
    public Integer getPlayerFreqency(String name) {
        int frequency = -1;

        if (m_team1.isPlayer(name))
            frequency = m_team1.getFrequency();
        else if (m_team2.isPlayer(name))
            frequency = m_team2.getFrequency();

        return frequency;
    }

    public twhtTeam getPlayerTeam(String name) {
        twhtTeam team = null;
        twhtPlayer playerA = null;

        playerA = m_team1.searchPlayer(name);
        if (playerA != null)
            team = m_team1;

        playerA = m_team2.searchPlayer(name);
        if (playerA != null)
            team = m_team2;

        return team;
    }

    public Integer getNextRecordNumber() {
        boolean nextRecord = false;
        int i = 1;

        while (!nextRecord) {
            if (refRequest.containsKey(i)) {
                i++;
            } else {
                requestRecordNumber = i;
                return requestRecordNumber;
            }
        }

        return -1;
    }

    public void getStatus(String name, String msg) {
        if (m_curRound != null) {
            ba.sendPrivateMessage(name, "Currently in round " + m_curRound.getRoundNum() + " with " + m_fcTeam1Name + "(" + fnTeam1Score + ") vs " + m_fcTeam2Name + "(" + fnTeam2Score + ")");
        }
    }

    public void doClearPenalties() {
        m_team1.clearTeamPenalties();
        m_team2.clearTeamPenalties();
    }

    public void doMyfreq(String name, String msg) {
        twhtTeam team = null;
        int frequency;

        if (m_team1.isPlayer(name) || m_team1.isCaptain(name)) {
            team = m_team1;
        } else if (m_team2.isPlayer(name) || m_team2.isCaptain(name)) {
            team = m_team2;
        }

        if (team != null) {
            frequency = team.getFrequency();
            ba.setFreq(name, frequency);
        }
    }

    public void doAddPlayer(String teamName, String player, int shipType) {
        Player p;
        p = ba.getPlayer(player);

        if (p != null) {
            if (m_team1.isPlayer(player) || m_team2.isPlayer(player)) {
                ba.sendPrivateMessage(m_fcRefName, "Cannot add player. Player is already in for one of the teams.");
                return;
            } else {
                if (teamName == m_fcTeam1Name)
                    m_team1.addPlayer(player, shipType);
                if (teamName == m_fcTeam2Name)
                    m_team2.addPlayer(player, shipType);
            }
        } else {
            ba.sendPrivateMessage(m_fcRefName, "Cannot add player. Player is no longer here.");
        }
    }

    public void doSubPlayer(String teamName, String playerA, String playerB, int shipType) {
        Player p;
        p = ba.getPlayer(playerB);
        twhtTeam t = null;

        if (m_fcTeam1Name == teamName)
            t = m_team1;
        if (m_fcTeam2Name == teamName)
            t = m_team2;

        if (p != null) {
            if (m_team1.isPlayer(playerB) || m_team2.isPlayer(playerB)) {
                ba.sendPrivateMessage(m_fcRefName, "Cannot sub player in. Already on one of the teams.");
                return;
            } else if (t.searchPlayer(playerA) == null || t.getPlayerState(playerA) == 2) {
                ba.sendPrivateMessage(m_fcRefName, "Cannot sub player out. Player is no longer on the team.");
                return;
            } else {
                t.subPlayer(playerA, playerB, shipType);
            }
        } else {
            ba.sendPrivateMessage(m_fcRefName, "Cannot sub player. Player is no longer here.");
        }
    }

    public void doSwitchPlayer(String teamName, String playerA, String playerB) {
        Player pA;
        Player pB;
        twhtTeam t = null;

        pA = ba.getPlayer(playerA);
        pB = ba.getPlayer(playerB);

        if (m_fcTeam1Name == teamName)
            t = m_team1;
        if (m_fcTeam2Name == teamName)
            t = m_team2;

        if (pA != null && pB != null) {
            if (t.searchPlayer(playerA) == null || t.searchPlayer(playerB) == null) {
                ba.sendPrivateMessage(m_fcRefName, "Cannot switch players. One of the players is no longer on the team.");
                return;
            } else if ((t.getPlayerState(playerA) == 3 || t.getPlayerState(playerA) == 2) || (t.getPlayerState(playerB) == 3 || t.getPlayerState(playerB) == 2)) {
                ba.sendPrivateMessage(m_fcRefName, "Cannot switch players. One of the players is lagged out or subbed out.");
                return;
            } else {
                t.switchPlayer(playerA, playerB);
            }
        } else {
            ba.sendPrivateMessage(m_fcRefName, "Cannot switch players. One of the players is lagged out.");
        }
    }

    public void doChangePlayer(String teamName, String player, int shipType) {
        Player p;
        twhtTeam t = null;

        p = ba.getPlayer(player);

        if (m_fcTeam1Name == teamName)
            t = m_team1;
        if (m_fcTeam2Name == teamName)
            t = m_team2;

        if (p != null) {
            if (t.searchPlayer(player) == null) {
                ba.sendPrivateMessage(m_fcRefName, "Cannot change player. Player is no longer on the team.");
                return;
            } else if (t.getPlayerState(player) != 1 && t.getPlayerState(player) != 4) {
                ba.sendPrivateMessage(m_fcRefName, "Cannot change player. Player is lagged out or substitued.");
                return;
            } else {
                t.changePlayer(player, shipType);
            }
        } else {
            ba.sendPrivateMessage(m_fcRefName, "Cannot Change player. Player is no longer in the game.");
        }
    }

    public void doRemovePlayer(String teamName, String player) {
        twhtTeam t = null;

        if (m_fcTeam1Name == teamName)
            t = m_team1;
        if (m_fcTeam2Name == teamName)
            t = m_team2;

        if (t.searchPlayer(player) == null) {
            ba.sendPrivateMessage(m_fcRefName, "Player is already removed from the team.");
            return;
        } else {
            t.removePlayer(player);
        }
    }

    public void doTimeout(String teamName) {
        ba.sendArenaMessage(teamName + " has called a timeout.", 2);
        if (m_curRound != null)
            m_curRound.pause();
    }

    public void doLagOut(String teamName, String playerName) {
        Player p;
        p = ba.getPlayer(playerName);

        if (p != null) {
            if (teamName == m_fcTeam1Name)
                m_team1.lagOut(playerName);
            if (teamName == m_fcTeam2Name)
                m_team2.lagOut(playerName);
        } else {
            ba.sendPrivateMessage(m_fcRefName, "Cannot !lagout player. Player is no longer in the arena.");
        }
    }

    public void doAddCenter(String name, String msg) {
        twhtTeam team;
        twhtPlayer pA;

        if (m_team1.isCaptain(name)) {
            team = m_team1;
        } else if (m_team2.isCaptain(name)) {
            team = m_team2;
        } else {
            return;
        }

        pA = team.searchPlayer(msg);

        if (pA != null && pA.getPlayerState() == 1 && !pA.getIsGoalie()) {
            team.setCenter(pA.getPlayerName());
            ba.sendPrivateMessage(name, pA.getPlayerName() + " has been set as your center.");
        } else {
            ba.sendPrivateMessage(name, "Player is unable to be set as center.");
            return;
        }
    }

    public void doCancelIntermission() {
        if (isIntermission) {
            ba.cancelTask(intermission);
            isIntermission = false;
            ba.sendArenaMessage("Round intermission has been canceled by the Referee.");
            setupRound(m_curRound.getRoundNum() + 1);
        }
    }
    
    public void getTeamList() {
        twhtPlayer p;
        
//        for (twhtPlayer p : team1.m_players)
    }

    /**
     * This method will add a judge to the list
     * 
     * @param name
     * @param msg
     */
    public void addJudge(String name, String msg) {
        String p = ba.getFuzzyPlayerName(msg);
        if (p != null) {
            if (m_judge.contains(p)) {
                ba.sendPrivateMessage(name, "Player is already a judge");
            } else {
                m_judge.add(p);
                ba.sendPrivateMessage(name, "Judge Added: " + p);
                ba.setFreq(p,2);
            }
        } else {
            ba.sendPrivateMessage(name, "Player not found.");
        }
    }

    /**
     * This method will remove a judge from the list
     * 
     * @param name
     * @param msg
     */
    public void removeJudge(String name, String msg) {
        String p = ba.getFuzzyPlayerName(msg);
        if (p != null) {
            if (m_judge.contains(p)) {
                m_judge.remove(p);
                ba.sendPrivateMessage(name, "Judge Removed: " + p);
            } else {
                ba.sendPrivateMessage(name, "Player is not a judge.");
            }
        } else {
            ba.sendPrivateMessage(name, "Player not found.");
        }
    }

    public void startReview() {

        voteInProgress = true;
        goalDelay = new TimerTask() {
            @Override
            public void run() {
                m_curRound.stopTimer();
                m_curRound.doGetBall(4800, 4800);
                m_curRound.doDropBall();
            }
        };
        ba.scheduleTask(goalDelay, 2 * Tools.TimeInMillis.SECOND);

        ba.sendTeamMessage("Review for the last goal has started. Please private message me your vote.");
        ba.sendTeamMessage("Commands: !cl (Clean), !lag (Lag), !gk (Goalie Kill), !og (Own Goal)");
    }

    public void doReady(String name) {
        if (m_curRound != null && (m_curRound.getRoundState() == 0 || m_curRound.getRoundState() == 2)) {
            twhtTeam team;

            if (m_team1.isCaptain(name)) {
                team = m_team1;
                ba.sendArenaMessage("" + team.getTeamName() + " is ready to begin.", 2);
            }
            if (m_team2.isCaptain(name)) {
                team = m_team2;
                ba.sendArenaMessage("" + team.getTeamName() + " is ready to begin.", 2);
            }
            if (name.equals(m_fcRefName)) {
                m_curRound.ready();
            }
        }
    }

    public void setFrequencyAndSide() {
        m_team1.setFreqAndSide();
        m_team2.setFreqAndSide();
    }

    public void doPause(String name, String msg) {
        if (m_curRound != null)
            m_curRound.pause();
    }

    public void goal(String msg) {
        if (voteInProgress) {
            if (msg.equals("cl")) {
                ba.sendArenaMessage("Goal was considered clean.", 2);
                ba.sendArenaMessage(getPlayerTeam(goalScorer).getTeamName() + "'s Goal by: " + goalScorer);
                
                if (!assistOne.equals(" ") && !assistTwo.equals(" "))                  
                    ba.sendArenaMessage("Assist: " + assistOne + " " + assistTwo);
                
                if (m_team1.isPlayer(goalScorer)) {
                    fnTeam1Score++;
                    m_team1.setScoreFor();
                    m_team2.setScoreAgainst();
                } else if (m_team2.isPlayer(goalScorer)) {
                    fnTeam2Score++;
                    m_team2.setScoreFor();
                    m_team1.setScoreAgainst();
                }
                m_curRound.doUpdateScoreBoard();
                ba.sendArenaMessage("Score: " + m_fcTeam1Name + " " + fnTeam1Score + " - " + m_fcTeam2Name + " " + fnTeam2Score);
                m_curRound.m_fnRoundState = 3;
            } else if (msg.equals("lag")) {
                ba.sendArenaMessage("Goal was considered lag and will be voided.", 2);
            } else if (msg.equals("cr")) {
                ba.sendArenaMessage("Goal was considered crease and will be voided.", 2);
            } else if (msg.equals("gk")) {
                ba.sendArenaMessage("Goal was considered a goalie kill and will be void.", 2);
            } else if (msg.equals("og")) {
                ba.sendArenaMessage("Goal was considered an own goal and the other team will be rewarded a goal.", 2);
                if (m_team1.isPlayer(goalScorer)) {
                    fnTeam2Score++;
                    m_team2.setScoreFor();
                    m_team1.setScoreAgainst();
                } else if (m_team2.isPlayer(goalScorer)) {                    
                    fnTeam1Score++;
                    m_team1.setScoreFor();
                    m_team2.setScoreAgainst();
                }
                m_curRound.doUpdateScoreBoard();
                ba.sendArenaMessage("Score: " + m_fcTeam1Name + " " + fnTeam1Score + " - " + m_fcTeam2Name + " " + fnTeam2Score);
                m_curRound.m_fnRoundState = 3;
            }
            voteInProgress = false;
            assistOne = "";
            assistTwo = "";
        }
    }

    public void getRequestList(String name, String msg) {
        if (msg.equals("all")) {
            for (RefRequest i : refRequest.values()) {
                i.pmRequestRef();
            }
        } else if (msg.equals("denied")) {
            for (RefRequest i : refRequest.values()) {
                if (i.getRequestState() == 2)
                    i.pmRequestRef();
            }
        } else if (msg.equals("accepted")) {
            for (RefRequest i : refRequest.values()) {
                if (i.getRequestState() == 1)
                    i.pmRequestRef();
            }
        } else {
            for (RefRequest i : refRequest.values()) {
                if (i.getRequestState() == 0)
                    i.pmRequestRef();
            }
        }
    }

    public void acceptRequest(String name, String msg) {
        RefRequest number;

        int recordNumber;

        try {
            recordNumber = Integer.parseInt(msg);
        } catch (NumberFormatException e) {
            return;
        }

        if (recordNumber > 0 && recordNumber < 500) {
            if (refRequest.containsKey(recordNumber)) {
                number = refRequest.get(recordNumber);
                number.executeRequest();
            }
        } else {
            ba.sendPrivateMessage(name, "Please only use valid numbers.");
        }
    }

    public void openRequest(String name, String msg) {
        RefRequest number;
        int recordNumber;

        try {
            recordNumber = Integer.parseInt(msg);
        } catch (NumberFormatException e) {
            return;
        }

        if (recordNumber > 0 && recordNumber < 500) {
            if (refRequest.containsKey(recordNumber)) {
                number = refRequest.get(recordNumber);
                number.openRequest();
            }
        } else {
            ba.sendPrivateMessage(name, "Please only use valid numbers.");
        }

    }

    public void denyRequest(String name, String msg) {
        RefRequest number;
        int recordNumber;

        try {
            recordNumber = Integer.parseInt(msg);
        } catch (NumberFormatException e) {
            return;
        }

        if (recordNumber > 0 && recordNumber < 500) {
            if (refRequest.containsKey(recordNumber)) {
                number = refRequest.get(recordNumber);
                number.denyRequest();
            }
        } else {
            ba.sendPrivateMessage(name, "Please only use valid numbers.");
        }
    }

    public void resetVariables() {
        m_judge.clear();
        m_penalties.clear();
        m_officials.clear();
        refRequest.clear();
        m_rounds.clear();
    }

    public class RefRequest {

        /*Request Types:
         * 0 - Add
         * 1 - Sub 
         * 2 - Switch
         * 3 - Change
         * 4 - Remove
         * 5 - Timeout
         * 6 - Lagout
         */
        int reqType;

        /*Request State: 
         * 0 - Open
         * 1 - Accepted
         * 2 - Denied
         */
        int requestState;
        int callNumber;
        int roundNumber;
        int shipType;
        String roundTime;
        String requester;
        String teamName;
        String reqString;
        String playerA = "";
        String playerB = "";

        public RefRequest(int callNum, int roundNum, int requestType, String name, String team, String RequestString) {
            this.callNumber = callNum;
            this.roundNumber = roundNum;
            this.reqType = requestType;
            this.roundTime = m_curRound.getStringTime();
            this.teamName = team;
            this.requester = name;
            this.reqString = RequestString;
            this.requestState = 0;
            breakDownString();
        }

        private void breakDownString() {
            String[] splitCmd;
            splitCmd = reqString.split(":");

            if (reqType == 0) {
                playerA = splitCmd[0];
                shipType = Integer.parseInt(splitCmd[1]);
            } else if (reqType == 1) {
                playerA = splitCmd[0];
                playerB = splitCmd[1];
                shipType = Integer.parseInt(splitCmd[2]);
            } else if (reqType == 2) {
                playerA = splitCmd[0];
                playerB = splitCmd[1];
            } else if (reqType == 3) {
                playerA = splitCmd[0];
                shipType = Integer.parseInt(splitCmd[1]);
            } else if (reqType == 4) {
                playerA = reqString;
            } else if (reqType == 6) {
                playerA = requester;
            }
            ba.sendPrivateMessage(m_fcRefName, "New Request Recieved:");
            pmRequestRef();
        }

        private void pmRequestRef() {

            if (reqType == 0) {
                ba.sendPrivateMessage(m_fcRefName, "#" + callNumber + " - Round " + roundNumber + " " + roundTime + " - " + teamName + " - ADD: " + playerA + " in " + shipType);
            } else if (reqType == 1) {
                ba.sendPrivateMessage(m_fcRefName, "#" + callNumber + " - Round " + roundNumber + " " + roundTime + " - " + teamName + " - SUB: " + playerA + " for " + playerB + " in " + shipType);
            } else if (reqType == 2) {
                ba.sendPrivateMessage(m_fcRefName, "#" + callNumber + " - Round " + roundNumber + " " + roundTime + " - " + teamName + " - SWITCH: " + playerA + " for " + playerB);
            } else if (reqType == 3) {
                ba.sendPrivateMessage(m_fcRefName, "#" + callNumber + " - Round " + roundNumber + " " + roundTime + " - " + teamName + " - CHANGE: " + playerA + " to " + shipType);
            } else if (reqType == 4) {
                ba.sendPrivateMessage(m_fcRefName, "#" + callNumber + " - Round " + roundNumber + " " + roundTime + " - " + teamName + " - REMOVE: " + playerA);
            } else if (reqType == 5) {
                ba.sendPrivateMessage(m_fcRefName, "#" + callNumber + " - Round " + roundNumber + " " + roundTime + " - " + teamName + " - TIMEOUT ");
            } else if (reqType == 6) {
                ba.sendPrivateMessage(m_fcRefName, "#" + callNumber + " - Round " + roundNumber + " " + roundTime + " - " + teamName + " - LAGOUT " + requester);
            }
        }

        private void executeRequest() {
            if (this.requestState != 1) {
                this.requestState = 1;
                ba.sendPrivateMessage(m_fcRefName, " Request #" + callNumber + " has been accepted.");
                if (reqType == 0) {
                    doAddPlayer(teamName, playerA, shipType);
                } else if (reqType == 1) {
                    doSubPlayer(teamName, playerA, playerB, shipType);
                } else if (reqType == 2) {
                    doSwitchPlayer(teamName, playerA, playerB);
                } else if (reqType == 3) {
                    doChangePlayer(teamName, playerA, shipType);
                } else if (reqType == 4) {
                    doRemovePlayer(teamName, playerA);
                } else if (reqType == 5) {
                    doTimeout(teamName);
                } else if (reqType == 6) {
                    doLagOut(teamName, requester);
                }
            } else {
                ba.sendPrivateMessage(m_fcRefName, "Request has already been executed once and cannot be executed again.");
            }
        }

        private void denyRequest() {
            ba.sendPrivateMessage(m_fcRefName, " Request #" + callNumber + " has been denied.");
            this.requestState = 2;
        }

        private void openRequest() {
            ba.sendPrivateMessage(m_fcRefName, " Request #" + callNumber + " has been opened.");
            this.requestState = 0;
        }

        private Integer getRequestState() {
            return requestState;
        }

        //        private String getRequester() {
        //            return requester;
        //        }
        //
        //        private String getRequestTeam() {
        //            return teamName;
        //        }
        //
        //
        //        private Integer getRoundNumber() {
        //            return roundNumber;
        //        }
        //
        //        private Integer getCallNumber() {
        //            return callNumber;
        //        }
    }
}