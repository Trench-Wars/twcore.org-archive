package twcore.bots.tournybot;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.SQLResultEvent;
import twcore.core.game.Player;
import twcore.core.stats.LagReport;
import twcore.core.stats.lagHandler;
import twcore.core.util.Tools;

public class tournybot extends SubspaceBot {

	private BotSettings m_botSettings;
	private lagHandler m_lagHandler;

	HashMap<String, dStats> duels;
	HashMap<String, fStats> freqs;
	HashMap<String, pStats> players;
	HashMap<String, Integer> votes;
	HashMap<String, Lagger> laggers;
	HashMap<String, duelDelay> delayers;

	TimerTask m_20Seconds;
	TimerTask m_10Seconds;
	TimerTask m_3Seconds;
	TimerTask m_startGame;
	TimerTask m_endGame;
	TimerTask m_announceVote;
	TimerTask m_announcePrize;
	TimerTask m_voteDeaths;
	TimerTask m_stop;
	TimerTask m_start;
	TimerTask checkQueries;

	Iterator<Player> ppIterator;

	String dbConn = "website";

	String ship;		// Shipname used in tourny
	int shipType = 0;	// Shipnumber used in tourny
	int deaths = 0;		// Deathlimit in tourny
	int playersNum = 0;	// # of players still in the tourny
	int trState = -1;	// Tournament state. -1 = stopped | 0 = less than 2 players have entered | 1 = voting on shiptype
				// 2 = voting on deathlimit | 3 = starting in 10 seconds | 4 = tourney in progress
	int maxLagOuts = 2;	// Max lagouts
	int maxFouls = 3;	// Max fouls (warping/spawning)
	int maxPerFreq = 1;
	int trPrize = 0;
	int tournyCount = 0;
	int tournyID;
	int startHour = 9;
	int stopHour = 23;

	boolean lagWorks = true;
	boolean stopped = false;
	boolean even;
	boolean zonerLock = false;
	boolean fZoner = false;
	boolean dbAvailable = true;
	boolean base = false;

	boolean forced = false;
	int fShipType = 0;
	int fDeaths = 0;
	String forcer;

	int firstRound;
	int maxBox;

	int nChecks;
	int qSent;
	int qReceived;

	public tournybot(BotAction botAction) {
		super(botAction);
		duels = new HashMap<String, dStats>();
		freqs = new HashMap<String, fStats>();
		players = new HashMap<String, pStats>();
		votes = new HashMap<String, Integer>();
		laggers = new HashMap<String, Lagger>();
		delayers = new HashMap<String, duelDelay>();

		requestEvents();
		m_botSettings = m_botAction.getBotSettings();
		m_lagHandler = new lagHandler(botAction, m_botSettings, this, "handleLagReport");
	}

    public boolean isIdle() {
        return trState != 4;
    }

	public void requestEvents() {
		EventRequester req = m_botAction.getEventRequester();
		req.request(EventRequester.MESSAGE);
		req.request(EventRequester.ARENA_JOINED);
		req.request(EventRequester.PLAYER_ENTERED);
		req.request(EventRequester.PLAYER_POSITION);
		req.request(EventRequester.PLAYER_LEFT);
		req.request(EventRequester.PLAYER_DEATH);
		req.request(EventRequester.FREQUENCY_SHIP_CHANGE);
		req.request(EventRequester.LOGGED_ON);
	}

	public void handleEvent(Message event) {
		String name = event.getMessager() != null ? event.getMessager() : m_botAction.getPlayerName(event.getPlayerID());
		if (name == null) name = "-anonymous-";

		String message = event.getMessage();

		if (event.getMessageType() == Message.ARENA_MESSAGE) {
			if (message.equals("Arena LOCKED") && trState > -2 && trState < 3) {
				m_botAction.toggleLocked();
			} else if (message.equals("Arena UNLOCKED") && trState > 2) {
				m_botAction.toggleLocked();
			} else {
				m_lagHandler.handleLagMessage(message);
			}
		}

		if (event.getMessageType() == Message.PUBLIC_MESSAGE || event.getMessageType() == Message.PRIVATE_MESSAGE) {
			if (trState == 1) {
				handleVote( name, event.getMessage(), 10);
			}
			if (trState == 2) {
				handleVote( name, event.getMessage(), 5);
			}
		}

		if (event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) {
			if (message.equalsIgnoreCase("!status")) {
				handleStatus(name);
			} else if (message.startsWith("!rank")) {
				if (message.length() > 5) {
					String piece = message.substring(6);
					String pieces2[] = piece.split(":");
					if (pieces2.length == 1) {
						handleSRank(name, pieces2[0]);
					} else if (pieces2.length == 2) {
						handleTRank(name, pieces2[0], pieces2[1]);
					} else {
						m_botAction.sendPrivateMessage(name, "Correct usage of !rank:  !rank, !rank <name> OR !rank <name>:<name2>");
					}
				} else {
					handleSRank(name, name);
				}
			}
		}

		if (event.getMessageType() == Message.PRIVATE_MESSAGE) {
			if (message.equalsIgnoreCase("!about") || message.equalsIgnoreCase("!help")) {
				handleHelp(name);
			} else if (message.equalsIgnoreCase("!myfreq")) {
				handleMyfreq(name);
			} else if (message.startsWith("!score")) {
				String scoreof;
				String temp = name;
				try
				{
					String pieces[] = message.split(" ");
					temp = pieces[1];
				} catch(Exception e) {}
				scoreof = temp;
				handleScore(name, scoreof);
			} else if (message.equalsIgnoreCase("!return") || message.equalsIgnoreCase("!lagout")) {
				handleReturn(name);
			} else if (message.equalsIgnoreCase("!duels")) {
				m_botAction.privateMessageSpam(name, getDuels());
			}

			if (!m_botAction.getOperatorList().isER(name)) {
				return;
			}

			if (message.equalsIgnoreCase("!die")) {
				try { Thread.sleep(50); } catch (Exception e) {};
		   		m_botAction.die();
			} else if (message.equalsIgnoreCase("!start")) {
				if (trState == -1) {
					startTournament();
				} else {
					m_botAction.sendPrivateMessage(name, "Tournament already in progress.");
				}
			} else if (message.equalsIgnoreCase("!stop")) {
				if (trState == -1) {
					m_botAction.sendPrivateMessage(name, "TournyBot already disabled.");
				} else if (trState < 4) {
					trState = -1;
					m_botAction.cancelTasks();
					m_botAction.sendArenaMessage("TournyBot disabled");
				} else {
					stopped = true;
					m_botAction.sendPrivateMessage(name, "When the current tournament ends, no new tournament will start.");
				}
			}

			if (!m_botAction.getOperatorList().isSmod(name)) {
				return;
			}

			if (message.startsWith("!zone")) {

			    	if (message.length() > 5) {
					String pZ = message.substring(6);

					if (pZ.equalsIgnoreCase("on")) {
						zonerLock = false;
						m_botAction.sendPrivateMessage(name, "Zone messages enabled.");
					} else if (pZ.equalsIgnoreCase("off")) {
	    					zonerLock = true;
						m_botAction.sendPrivateMessage(name, "Zone messages disabled.");
					}
				} else {
					fZoner = true;
					m_botAction.sendPrivateMessage(name, "Ad for next tournament quaranteeded.");
				}
			} else if (message.startsWith("!setstart")) {
				changeStart(message);
			} else if (message.startsWith("!setstop")) {
				changeStop(message);
			} else if (message.startsWith("!force ")) {
				String piece = message.substring(7);
				String pieces2[] = piece.split(":");
				if (pieces2.length == 2) {
					if (Tools.isAllDigits(pieces2[0]) && Tools.isAllDigits(pieces2[1])) {
						forceGame(Integer.parseInt(pieces2[0]), Integer.parseInt(pieces2[1]), name);
					}
				} else {
					m_botAction.sendPrivateMessage(name, "Correct usage of !force:  !force <gametype>:<deaths>");
				}
			} else if (message.startsWith("!lag")) {
				if (message.length() > 4) {
					String piece = message.substring(5);
					m_lagHandler.requestLag(piece, name);
				} else {
					m_lagHandler.requestLag(name, name);
				}
			} else if (message.startsWith("!lagoff")) {
				if (lagWorks) {
					lagWorks = false;
					m_botAction.sendPrivateMessage(name, "Bot's lag checking disabled.");
				} else {
					lagWorks = true;
					m_botAction.sendPrivateMessage(name, "Bot's lag checking enabled.");
				}
			}
		}
	}

	public void handleEvent(PlayerLeft event) {
		String name = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();

		if (trState > 3 && players.containsKey( name ) && playerStillIn( name )) {
			handleLagOut(name);
		} else if (trState == 0 && playersNum > 0) {
			playersNum--;
		}
	}

	public void handleEvent(FrequencyShipChange event) {
		String name = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();

		if (trState == 0 && event.getShipType() != 0) {
			playersNum++;

			if (playersNum >= 2 && trState == 0) {
				startTournament();
			}
		}

		if (event.getShipType() == 0) {

			if (trState > 3 && players.containsKey( name ) && playerStillIn( name )) {
				handleLagOut(name);
			} else if (trState == 0 && playersNum > 0) {
				playersNum--;
			}
		}
	}

	public void handleEvent( PlayerEntered event ) {
		handleStatus(event.getPlayerName());
	}

	public void handleEvent(PlayerPosition event) {


		/*
		 * Detects warping and warps them back if there is a tournament in progress.
		 * Also gives warnings for illegal warping.
		 */

		if (trState == 4) {
			Player p = m_botAction.getPlayer(event.getPlayerID());
				if (p == null) return;
				String name = p.getPlayerName();

			if (!playerStillIn(name)) {
				return;
			}

			pStats warper = players.get(name);

			if (warper.getFreq() == null || !freqs.containsKey(warper.getFreq())) {
				return;
			}

			fStats out = freqs.get(warper.getFreq());
			int maxx = 1024;
			int minx = 0;
			int maxy = 1024;
			int miny = 0;
			int plyrNo = out.getPlayerNro();

			if (plyrNo == 1) {
				minx = playerXPos(name) - 11;
				miny = playerYPos(name) - 11;
				maxx = minx + 125;
				maxy = miny + 125;
			} else if (plyrNo == 2) {
				maxx = playerXPos(name) + 11;
				maxy = playerYPos(name) + 11;
				minx = maxx - 125;
				miny = maxy - 125;
			}

			if (event.getXLocation() / 16 > maxx || event.getXLocation() / 16 < minx || event.getYLocation() / 16 > maxy || event.getYLocation() / 16 < miny) {

				if (warper.getPlayerState() == 1 && warper.timeFromLastDeath() > 3 && warper.timeFromLastReturn() > 3) {
					warper.incrementFouls();

					if (warper.getFouls() >= maxFouls) {
						String dqM = "disqualified for not obeying the rules (warping/spawning).";
						m_botAction.sendArenaMessage( name + " has been " + dqM);
						warper.setNotes(dqM);
						warper.laggedOut();
						removePlayer( name, true );
					} else {
						int rFouls = maxFouls - warper.getFouls();
						m_botAction.sendPrivateMessage( name, "Warping is not allowed. (" + rFouls + " more foul(s) = forfeit)");
					}
				}
				warpPlayer( name );
			}
		}
	}

	public void handleEvent(PlayerDeath event) {
		if (trState == 4) {
			String killerName = m_botAction.getPlayerName(event.getKillerID());
	 	  	String killeeName = m_botAction.getPlayerName(event.getKilleeID());
			if (lagWorks) { m_lagHandler.requestLag(killerName); }

			if (playerStillIn(killerName) && playerStillIn(killeeName)) {
				pStats info = players.get( killerName );
				pStats info2 = players.get( killeeName );

				if (findOpponent(info2.getFreq()) != null && (findOpponent(info2.getFreq()).equals(info.getFreq()) || info2.getFreq().equals(info.getFreq()))) {


					/*
					 * Kills don't count if the victim had killed the killer 2 or less seconds earlier. (DoubleKill)
					 */

					if (info.timeFromLastDeath() < 2 && info.getLastKiller().equals(killeeName)) {
						info2.setLastDeath();
						info2.removeKills();
						info.removeDeaths();
						m_botAction.sendPrivateMessage( killerName, "Double Kill, doesn't count.");
						m_botAction.sendPrivateMessage( killeeName, "Double Kill, doesn't count.");

						if (maxPerFreq == 2) {
							info2.sleeping();
							m_botAction.shipReset(killeeName);
							warpPlayer(killeeName);
							info2.playing();

							String name = killeeName + killerName;
							if (delayers.containsKey( name )) {
                                m_botAction.cancelTask( delayers.get(name) );
								delayers.remove(name);
							}
							duelDelay d = new duelDelay( name, killeeName, killerName, delayers, true, System.currentTimeMillis());
							m_botAction.scheduleTask( d, 3000 );
							delayers.put(name, d);
						}
						return;
					}


					/*
					 * Kills don't count if the victim had died 6 or less seconds earlier. (SpawnKill)
					 * Note: first 3 seconds of the 6 second timer are spent in the corner.
					 */

					if (info2.timeFromLastDeath() < 6) {
						info.incrementFouls();
						info2.setLastDeath();

						if (info.getFouls() >= maxFouls) {
							String dqM = "disqualified for not obeying the rules (warping/spawning).";
							m_botAction.sendArenaMessage( killerName + " has been " + dqM);
							info.setNotes(dqM);
							info.laggedOut();
							removePlayer( killerName, true );
						} else {
							int rFouls = maxFouls - info.getFouls();
							m_botAction.sendPrivateMessage( killerName, "Spawn Kill, doesn't count. Spawning is not allowed. (" + rFouls + " more foul(s) = forfeit)");
						}

						m_botAction.sendPrivateMessage( killeeName, "Spawn Kill, doesn't count.");
						m_botAction.shipReset(killeeName);

						return;
					}

					if (info2.getPlayerState() == 1 && info.getPlayerState() == 1) {
						info2.incrementDeaths();
						info2.setLastKiller(killerName);

						// Increment kills if the kill was not a teamkill
						if (!info.getFreq().equals(info2.getFreq())) {
							info.incrementKills();
						}

						// If the victim has reached the deathlimit, this task is scheduled to check doublekills
						if (info2.getGameDeaths() >= deaths) {
							m_botAction.scheduleTask( new aboutToDieOut( killeeName ), 2000 );
						}

						info2.sleeping();
						m_botAction.shipReset(killeeName);
						warpPlayer(killeeName);
						info2.playing();

						String name = killeeName + killerName;
						if (delayers.containsKey( name )) {
                            m_botAction.cancelTask( delayers.get(name) );
							delayers.remove(name);
						}

						//delayers.put( name, new duelDelay( name, killeeName, killerName, delayers, false ));
						//duelDelay d = (duelDelay)delayers.get( name );

						//If you get timesliced before this line, but after either of the 2 previous, could cause
						//some major issues.  Consider making this function synchronized.

						//m_botAction.scheduleTask( d, 3000 );

						//much more efficient like this, since you dont need the hash lookup
						//-AlienKing


						duelDelay d = new duelDelay( name, killeeName, killerName, delayers, false, System.currentTimeMillis());
						try {
						    m_botAction.scheduleTask( d, 3000 );
							delayers.put( name, d);
						} catch ( Exception e ) {
						    // NOOP
						}
					}
				}
			}
		}
	}

	public void handleEvent(LoggedOn event) {
		trState = -1;
		m_botAction.joinArena(m_botSettings.getString("arena"));
		m_botAction.sendUnfilteredPublicMessage("?chat=alerts,uberalerts,tourny");
		setupStart();
		setupStop();
		m_botAction.scheduleTaskAtFixedRate(m_start, getStartTime(), 7 * 24 * 60 * 60 * 1000);
		m_botAction.scheduleTaskAtFixedRate(m_stop, getStopTime(), 7 * 24 * 60 * 60 * 1000);

		m_botAction.setMessageLimit(10);
		m_botAction.toggleLocked();
	}

	public void handleEvent(ArenaJoined event) {
		m_botAction.setReliableKills(1);
	}

	public void handleEvent(SQLResultEvent event) {
		String ranks[] = event.getIdentifier().split(":");
		if (ranks.length == 4 && ranks[0].equals("sRank")) {

			try {
				if (event.getResultSet().next()) {

					if (Integer.parseInt(ranks[3]) == 1) {
						String profile[] = {
							"SOLO: Rank: " + event.getResultSet().getInt("rank") + "   Rating: " + event.getResultSet().getInt("rating") + "   RANDOM: Rank: " + event.getResultSet().getInt("rRank") + "   Rating: " + event.getResultSet().getInt("rRating"),
							"Complete player profile: " + m_botSettings.getString( "websiteP" ) + event.getResultSet().getInt("player_id")
						};
						m_botAction.smartPrivateMessageSpam(ranks[1], profile);
					} else if (Integer.parseInt(ranks[3]) == 2) {
						String profile[] = {
							"SOLO: Rank: " + event.getResultSet().getInt("rank") + "   Rating: " + event.getResultSet().getInt("rating") + "   RANDOM: Rank: N/A   Rating: " + event.getResultSet().getInt("rRating"),
							"Complete player profile: " + m_botSettings.getString( "websiteP" ) + event.getResultSet().getInt("player_id")
						};
						m_botAction.smartPrivateMessageSpam(ranks[1], profile);
					} else if (Integer.parseInt(ranks[3]) == 3) {
						String profile[] = {
							"SOLO: Rank: N/A   Rating: " + event.getResultSet().getInt("rating") + "   RANDOM: Rank: " + event.getResultSet().getInt("rRank") + "   Rating: " + event.getResultSet().getInt("rRating"),
							"Complete player profile: " + m_botSettings.getString( "websiteP" ) + event.getResultSet().getInt("player_id")
						};
						m_botAction.smartPrivateMessageSpam(ranks[1], profile);
					} else if (Integer.parseInt(ranks[3]) == 4) {
						String profile[] = {
							"SOLO: Rank: N/A   Rating: " + event.getResultSet().getInt("rating") + "   RANDOM: Rank: N/A   Rating: " + event.getResultSet().getInt("rRating"),
							"Complete player profile: " + m_botSettings.getString( "websiteP" ) + event.getResultSet().getInt("player_id")
						};
						m_botAction.smartPrivateMessageSpam(ranks[1], profile);
					}
				} else {
					if (Integer.parseInt(ranks[3]) == 1) {
						m_botAction.SQLBackgroundQuery(dbConn, "sRank:" + ranks[1] + ":" + ranks[2] + ":2", "SELECT pS.*, sR.rank FROM tblTournyPlayerStats AS pS, tblTourny1v1Ranks AS sR WHERE pS.playerName = '" + Tools.addSlashesToString(ranks[2]) + "' AND pS.player_id = sR.player_id");
					} else if (Integer.parseInt(ranks[3]) == 2) {
						m_botAction.SQLBackgroundQuery(dbConn, "sRank:" + ranks[1] + ":" + ranks[2] + ":3", "SELECT pS.*, rR.rRank FROM tblTournyPlayerStats AS pS, tblTournyRRanks AS rR WHERE pS.playerName = '" + Tools.addSlashesToString(ranks[2]) + "' AND pS.player_id = rR.player_id");
					} else if (Integer.parseInt(ranks[3]) == 3) {
						m_botAction.SQLBackgroundQuery(dbConn, "sRank:" + ranks[1] + ":" + ranks[2] + ":4", "SELECT * FROM tblTournyPlayerStats WHERE playerName = '" + Tools.addSlashesToString(ranks[2]) + "'");
					} else if (Integer.parseInt(ranks[3]) == 4) {
						m_botAction.sendSmartPrivateMessage(ranks[1], "No record of " + ranks[2]);
					}
				}
			} catch (Exception e) {}
                        m_botAction.SQLClose( event.getResultSet() );
			return;
		} else if (ranks.length == 5 && ranks[0].equals("tRank")) {

			try {
				if (event.getResultSet().next()) {

					if (Integer.parseInt(ranks[4]) == 1) {
						String profile[] = {
							"Rank: " + event.getResultSet().getInt("rank") + "   Rating: " + event.getResultSet().getInt("rating"),
							"Complete team profile: " + m_botSettings.getString( "websiteTP" ) + event.getResultSet().getInt("team_id")
						};
						m_botAction.smartPrivateMessageSpam(ranks[1], profile);
					} else if (Integer.parseInt(ranks[4]) == 2) {
						String profile[] = {
							"Rank: N/A   Rating: " + event.getResultSet().getInt("rating"),
							"Complete team profile: " + m_botSettings.getString( "websiteTP" ) + event.getResultSet().getInt("team_id")
						};
						m_botAction.smartPrivateMessageSpam(ranks[1], profile);
					}
				} else {

					if (Integer.parseInt(ranks[4]) == 1) {
						m_botAction.SQLBackgroundQuery(dbConn, "tRank:" + ranks[1] + ":" + ranks[2] + ":" + ranks[3] + ":2", "SELECT * FROM tblTournyTeamStats WHERE (player1Name = '" + Tools.addSlashesToString(ranks[2]) + "' AND player2Name = '" + Tools.addSlashesToString(ranks[3]) + "') OR (player1Name = '" + Tools.addSlashesToString(ranks[3]) + "' AND player2Name = '" + Tools.addSlashesToString(ranks[2]) + "')");
					} else if (Integer.parseInt(ranks[4]) == 2) {
						m_botAction.sendSmartPrivateMessage(ranks[1], "No record of " + ranks[2] + " and " + ranks[3]);
					}
				}
			} catch (Exception e) {}
                        m_botAction.SQLClose( event.getResultSet() );
			return;
		}

		if (maxPerFreq == 1) {

			if (!freqs.containsKey(event.getIdentifier())) {
			    return;
			}

			fStats rand = freqs.get( event.getIdentifier() );

			try {
				if (event.getResultSet().next()) {
					rand.setDBID(event.getResultSet().getInt("player_id"));
					rand.setRating(event.getResultSet().getInt("rating"));

					if (rand.getRating() < 0) {
						rand.setRating(1);
					}

					trPrize += rand.getRating();
					qReceived++;
				} else {
					if (!rand.getP1().getQSent()) {
						rand.getP1().setQSent(true);
						m_botAction.SQLHighPriorityBackgroundQuery(dbConn, null, "INSERT INTO tblTournyPlayerStats (playerName) VALUES ('" + Tools.addSlashesToString(rand.getName1()) + "')");
					}
					m_botAction.SQLBackgroundQuery(dbConn, event.getIdentifier(), "SELECT player_id, rating FROM tblTournyPlayerStats WHERE playerName = '" + Tools.addSlashesToString(rand.getName1()) + "'");
				}
			} catch (Exception e) {
				dbAvailable = false;
			}
                        m_botAction.SQLClose( event.getResultSet() );
		} else {
			String team[] = event.getIdentifier().split(":");
			if (team.length == 2) {

				if (!freqs.containsKey(team[0])) {
				    return;
				}

				fStats rand = freqs.get( team[0] );

				pStats rP;

				if (Integer.parseInt(team[1]) == 1) {
					rP = rand.getP1();
				} else {
					rP = rand.getP2();
				}

				try {
					if (event.getResultSet().next()) {
						rP.setDBID(event.getResultSet().getInt("player_id"));

						if (Integer.parseInt(team[1]) == 1) {
							rand.setRating(event.getResultSet().getInt("rRating"));
						} else {
							double rTing = (rand.getRating() + event.getResultSet().getInt("rRating")) / 2;
							rand.setRating((int)rTing);

							if (rand.getRating() < 0) {
								rand.setRating(1);
							}

							trPrize += rand.getRating();
						}

						qReceived++;
					} else {
						if (!rP.getQSent()) {
							rP.setQSent(true);
							m_botAction.SQLHighPriorityBackgroundQuery(dbConn, null, "INSERT INTO tblTournyPlayerStats (playerName) VALUES ('" + Tools.addSlashesToString(rP.getName()) + "')");
						}
						m_botAction.SQLBackgroundQuery(dbConn, event.getIdentifier(), "SELECT player_id, rRating FROM tblTournyPlayerStats WHERE playerName = '" + Tools.addSlashesToString(rP.getName()) + "'");
					}
				} catch (Exception e) {
					dbAvailable = false;
				}
                                m_botAction.SQLClose( event.getResultSet() );
			} else {

				if (!freqs.containsKey(event.getIdentifier())) {
				    return;
				}

				fStats rand = freqs.get( event.getIdentifier() );

				try {
					if (event.getResultSet().next()) {
						rand.setDBID(event.getResultSet().getInt("team_id"));
						rand.setRating(event.getResultSet().getInt("rating"));

						if (!rand.getName1().equalsIgnoreCase(event.getResultSet().getString("player1Name"))) {
							pStats tmp = rand.getP1();
							rand.addPlayer(rand.getP2(), 1);
							rand.addPlayer(tmp, 2);
						}

						if (rand.getRating() < 0) {
							rand.setRating(1);
						}

						trPrize += rand.getRating();
						qReceived++;
					} else {
						if (!rand.getQSent()) {
							rand.setQSent(true);
							m_botAction.SQLHighPriorityBackgroundQuery(dbConn, null, "INSERT INTO tblTournyTeamStats (player1Name, player2Name) VALUES ('" + Tools.addSlashesToString(rand.getName1()) + "', '" + Tools.addSlashesToString(rand.getName2()) + "')");
						}
						m_botAction.SQLBackgroundQuery(dbConn, event.getIdentifier(), "SELECT team_id, rating, player1Name, player2Name FROM tblTournyTeamStats WHERE player1Name = '" + Tools.addSlashesToString(rand.getName1()) + "' AND player2Name = '" + Tools.addSlashesToString(rand.getName2()) + "'");
					}
				} catch (Exception e) {
					dbAvailable = false;
				}
                                m_botAction.SQLClose( event.getResultSet() );
			}
		}
	}

	public void handleLagReport(LagReport report) {
		if (!report.isBotRequest()) {
			m_botAction.privateMessageSpam(report.getRequester(), report.getLagStats());
		}

		if (report.isOverLimits())
		{
			if (!report.isBotRequest())
            {
				m_botAction.sendPrivateMessage(report.getRequester(), report.getLagReport());
			}
			Player p = m_botAction.getPlayer(report.getName());
			if (p != null && p.getShipType() != 0)
			{
				m_botAction.sendPrivateMessage(report.getName(), report.getLagReport());
				m_botAction.spec(report.getName());
				m_botAction.spec(report.getName());
			}
		}
	}

	public void handleHelp(String name) {
		String about[] = {
			"+------------------------------------------------------------+",
			"| TournyBot v.0.99b                           - author Sika  |",
			"+------------------------------------------------------------+",
			"| Hosts an automated tournament with up to 64 teams.         |",
			"| Tournament begins when 2 or more players enter the game.   |",
			"+------------------------------------------------------------+",
			"| !help OR !about       - Brings up this message             |",
			"| !status               - Tournament status                  |",
			"| !duels                - Displays all ongoing duels         |",
			"| !score <name>         - Displays the person's score        |",
			"| !rank <name>          - Displays the person's rating       |",
			"| !rank <name>:<name2>  - Displays the team's rating         |",
			"| !return OR !lagout    - Return to the game                 |",
			"| !myfreq               - Join your freq                     |",
			"+------------------------------------------------------------+"
		};
		String aboutER[] = {
			"| !start                - Enables the tournybot              |",
			"| !stop                 - Stops the tournybot. Note: Doesn't |",
			"|                         stop the ongoing tourny. Prevents  |",
			"|                         new tournies from starting.        |",
			"| !die	                 - Kills the bot                      |",
			"+------------------------------------------------------------+"
		};
		String aboutSMOD[] = {
			"| !setstart <#>         - Sets the hour it starts on Friday  |",
			"| !setstop <#>          - Sets the hour it stops on Sunday   |",
			"| !zone on/off          - Enables/Disables *zoners           |",
			"| !zone                 - Enables Ad for next tournament     |",
			"| !lag <name>           - Displays the person's lag          |",
			"| !lagoff               - Enables/Disables bot's lag checks  |",
			"+------------------------------------------------------------+"
		};
		m_botAction.privateMessageSpam(name, about);
		if ( m_botAction.getOperatorList().isER( name ) ) { m_botAction.privateMessageSpam(name, aboutER); }
		if ( m_botAction.getOperatorList().isSmod( name ) ) { m_botAction.privateMessageSpam(name, aboutSMOD); }
	}

	public void handleSRank(String name, String tempPlayer) {

		if (dbAvailable) {
			m_botAction.SQLBackgroundQuery(dbConn, "sRank:" + name + ":" + tempPlayer + ":1", "SELECT pS.*, sR.rank, rR.rRank FROM tblTournyPlayerStats AS pS, tblTourny1v1Ranks AS sR, tblTournyRRanks AS rR WHERE pS.playerName = '" + Tools.addSlashesToString(tempPlayer) + "' AND pS.player_id = sR.player_id AND pS.player_id = rR.player_id;");
		} else {
			m_botAction.sendPrivateMessage(name, "Database unavailable.");
		}
	}


	public void handleTRank(String name, String tempPlayer1, String tempPlayer2) {

		if (dbAvailable) {
			m_botAction.SQLBackgroundQuery(dbConn, "tRank:" + name + ":" + tempPlayer1 + ":" + tempPlayer2 + ":1", "SELECT * FROM tblTournyTeamStats AS tS, tblTourny2v2Ranks AS 2v2 WHERE ((tS.player1Name = '" + Tools.addSlashesToString(tempPlayer1) + "' AND tS.player2Name = '" + Tools.addSlashesToString(tempPlayer2) + "') OR (tS.player1Name = '" + Tools.addSlashesToString(tempPlayer2) + "' AND tS.player2Name = '" + Tools.addSlashesToString(tempPlayer1) + "')) AND tS.team_id = 2v2.team_id");
		} else {
			m_botAction.sendPrivateMessage(name, "Database unavailable.");
		}
	}

	public void handleStatus(String name) {
		String status;
		if (trState == -1) {
			status = "Tournament is currently stopped. Tournament will run automatically Friday "+startHour+":00 until Sunday "+stopHour+":00 .";
		} else if (trState == 0) {
			status = "Tournament about to begin (When 2 or more players enter).";
		} else if (trState == 1 || trState == 2) {
			status = "Voting on gametype.";
		} else if (trState == 3) {
			status = ship + " tournament to " + deaths + " death(s) starting.";
		} else {
			String figure;
			if (maxPerFreq == 2) {
				figure = "teams";
			} else {
				figure = "players";
			}
			status = ship + " tournament to " + deaths + " death(s) in progress with " + playersNum + " " + figure + " in.";
			if (dbAvailable) {
				status += " Prize: " + trPrize;
			} else {
				status += " Database unavailable, current tournament is not being recorded.";
			}
		}
		m_botAction.sendSmartPrivateMessage(name, "Tournament Status: " + status);
	}

	public void handleMyfreq(String name) {

		if (trState == 4 && maxPerFreq == 2 && players.containsKey(name) && m_botAction.getPlayer(name).getShipType() == 0) {
			pStats messager = players.get( name );
			if (freqStillIn(messager.getFreq()))	{
				m_botAction.setFreq(name, Integer.parseInt(messager.getFreq()));
			}
		}
	}

	public void handleScore(String name, String tempPlayer) {
		String player = m_botAction.getFuzzyPlayerName(tempPlayer);

		if (player == null) {
		    player = tempPlayer;
		}

		if (players.containsKey(player) && trState == 4) {
			pStats score = players.get( player );
			fStats fScore = freqs.get( score.getFreq() );

			int sRound = fScore.getRound();

			if (fScore.getFreqState() == 2) {

				if (maxPerFreq == 2) {
					m_botAction.sendPrivateMessage( name, fScore.getNames() + " were eliminated in " + getRoundName(sRound));
				} else {
					m_botAction.sendPrivateMessage( name, fScore.getNames() + " was eliminated in " + getRoundName(sRound));
				}
				return;
			}

			if (fScore.getBusy() == 0) {

				if (maxPerFreq == 2) {
					m_botAction.sendPrivateMessage( name, fScore.getNames() + " are waiting for an opponent in " + getRoundName(sRound));
				} else {
					m_botAction.sendPrivateMessage( name, fScore.getNames() + " is waiting for an opponent in " + getRoundName(sRound));
				}
			} else {
				fStats ops = freqs.get( findOpponent(score.getFreq()) );
				m_botAction.sendPrivateMessage( name, getRoundName(sRound) + ": [" + fScore.getNames() + " " + ops.getGameDeaths() + " - " + fScore.getGameDeaths() + " " + ops.getNames() + "]");

				if (maxPerFreq == 2) {
					m_botAction.sendPrivateMessage( name, Tools.formatString("", getRoundName(sRound).length(), "-") + "  " + fScore.getName1() + "(" + fScore.getP1().getGameKills() + " - " + fScore.getP1().getGameDeaths() + ")    " + fScore.getName2() + "(" + fScore.getP2().getGameKills() + "-" + fScore.getP2().getGameDeaths() + ")");
				}
			}
		}
	}

	public String[] getDuels() {
		ArrayList<String> duelList = new ArrayList<String>();

		if (trState == 4) {

			for (int i = 1; i < 7; i++) {
				Iterator<String> d = getDuelList();
				while (d.hasNext()) {
					String duel = d.next();
					dStats dU = duels.get( duel );

					if (!dU.getFinished() && i == dU.getF1().getRound()) {
						duelList.add(getRoundName(dU.getF1().getRound()) + ": [" + dU.getF1().getNames() + " " + dU.getF2().getGameDeaths() + " - " + dU.getF1().getGameDeaths() + " " + dU.getF2().getNames() + "]");
					}
				}
			}
		}
		return duelList.toArray(new String[duelList.size()]);
	}

	public void handleReturn(String name) {

		if (playerStillIn(name) && trState == 4 && m_botAction.getPlayer(name).getShipType() == 0) {
			pStats messager = players.get( name );

			if (laggers.containsKey(name)) {
                m_botAction.cancelTask(laggers.get( name ));
				laggers.remove( name );

				if (findOpponent(messager.getFreq()) != null) {
					int remainingLO = maxLagOuts - messager.getLagOuts();

					if (messager.getPlayerState() == 1) {
						fStats ops = freqs.get( findOpponent(messager.getFreq()) );
						m_botAction.sendPrivateMessage( name, "Go! (You have " + remainingLO + " lagout(s) left)", 104);
						if (maxPerFreq == 1) {
							m_botAction.sendPrivateMessage( ops.getName1(), "Your opponent has returned from lagout, Go!", 104);
							warpFreq(findOpponent(messager.getFreq()));
						} else {
							m_botAction.sendPrivateMessage( getPartner(name), "Your partner (" + name + ") has returned from lagout!");
							m_botAction.sendPrivateMessage( ops.getName1(), "Your opponent (" + name + ") has returned from lagout!");
							m_botAction.sendPrivateMessage( ops.getName2(), "Your opponent (" + name + ") has returned from lagout!");
						}
						messager.setLastReturn((int)(System.currentTimeMillis() / 1000));
					}
				}
			}
			messager.toggleSmallLag(false);
			messager.toggleTrueLag(false);
			m_botAction.setShip(name, messager.getShip());
			m_botAction.setFreq(name, Integer.parseInt(messager.getFreq()));
		}
	}


	/*
	 * Handles lagouts. If the player doesn't have an opponent he doesn't get any penalty.
	 */

	public void handleLagOut(String name) {
		pStats lagi = players.get( name );

		if (lagi.getPlayerState() == 0) {
		    lagi.toggleSmallLag(true);
		    return;
		}

		if (lagi.getTrueLag() || lagi.getPlayerState() == 2) {
		    return;
		}

		lagi.incrementLagOuts();

		if (lagi.getLagOuts() > maxLagOuts) {
			String dqM = "exceeded the lagout limit and was disqualified.";

			m_botAction.sendArenaMessage( name + " " + dqM);
			lagi.laggedOut();
			lagi.setNotes(dqM);
			removePlayer( name, true );
		} else {
			lagi.toggleTrueLag(true);

			if (laggers.containsKey(name)) {
                m_botAction.cancelTask( laggers.get(name));
				laggers.remove(name);
			}
			laggers.put( name, new Lagger( name, laggers ) );
			Lagger l = laggers.get( name );
			m_botAction.scheduleTask( l, 60000 );

			fStats ops = freqs.get( findOpponent(lagi.getFreq()));
			m_botAction.sendSmartPrivateMessage( name, "You have 60 seconds to return to the game, send me !return to enter.");
			m_botAction.sendPrivateMessage( ops.getName1(), "Your opponent (" + name + ") has lagged out, and has 60 seconds to return. Total " + lagi.getLagOuts() + " lagouts.");

			if (maxPerFreq == 2) {
				m_botAction.sendPrivateMessage( getPartner(name), "Your partner (" + name + ") has lagged out, and has 60 seconds to return. Total " + lagi.getLagOuts() + " lagouts.");
				m_botAction.sendPrivateMessage( ops.getName2(), "Your opponent (" + name + ") has lagged out, and has 60 seconds to return. Total " + lagi.getLagOuts() + " lagouts.");
			}
		}
	}


	/*
	 * Calculates the X coord for name based on the 2 coordinates in .cfg
	 */

	public int playerXPos(String name) {

		if (!playerStillIn( name )) {
		    return 512;
		}

		pStats tPos = players.get( name );
		fStats pPos = freqs.get( tPos.getFreq() );

		int nroer = pPos.getPlayerNro();

		// pPos.getRealBox() contains a 2 digit number. First number is the number of the col where  name's  box is.
		int box1 = Integer.parseInt(pPos.getRealBox().substring(0, 1));

		String tx;

		if (nroer == 1) {
			tx = m_botSettings.getString( "player1" );
		} else {
			tx = m_botSettings.getString( "player2" );
		}

		int x2 = Integer.parseInt(tx);
		int x;

		// If it is a base-tournament, the boxes on the right are used.
		if (base) {
			x = x2 + (127 * box1) + 517;
		} else {
			x = x2 + (127 * box1);
		}

		if (tPos.getPlayerState() == 0) {

			if (nroer == 1) {
				x -= 8;
			} else {
				x += 8;
			}
		}
		return x;
	}


	/*
	 * Calculates the X coord for name based on the 2 coordinates in .cfg
	 */

	public int playerYPos(String name) {

		if (!playerStillIn( name )) {
		    return 512;
		}

		pStats tPos = players.get( name );
		fStats pPos = freqs.get( tPos.getFreq() );

		int nroer = pPos.getPlayerNro();

		// pPos.getRealBox() contains a 2 digit number. Second number is the number of the row where  name's  box is.
		int box1 = Integer.parseInt(pPos.getRealBox().substring(1, 2));

		String ty;

		if (nroer == 1) {
			ty = m_botSettings.getString( "player1" );
		} else {
			ty = m_botSettings.getString( "player2" );
		}

		int y2 = Integer.parseInt(ty);
		int y;

		// There is a small tunnel after the 4th row of boxes, need to take it into account..
		if (box1 > 3) {
			y = y2 + (127 * box1) + 9;
		} else {
			y = y2 + (127 * box1);
		}

		if (tPos.getPlayerState() == 0) {

			if (nroer == 1) {
				y -= 8;
			} else {
				y += 8;
			}
		}
		return y;
	}


	/*
	 * First handles voting (unless !force is used) and then sets the players in correct ships and forms the teams.
	 */

	public void startTournament() {

		m_voteDeaths = new TimerTask()
		{
			public void run() {
				trState = 2;
				shipType = countVote(10);

				if (shipType == 0) {
				    shipType = 1;
				}

				votes.clear();
				setRealShipType(shipType);
				m_botAction.sendArenaMessage( ship + " tournament. Vote: How many deaths? (1-5)");
			};
		};

		m_announceVote = new TimerTask()
		{
			public void run()
			{
				trState = 3;
				deaths = countVote(5);

				if (deaths == 0) {
				    deaths = 5;
				}
				votes.clear();
				m_botAction.sendArenaMessage( ship + " tournament to " + deaths + " death(s)!", 2);
				m_botAction.sendArenaMessage( "Rules: No warping or spawning! " + maxLagOuts + " lagouts/duel allowed. You may spec/leave arena if you don't have an opponent.");

				if (maxPerFreq == 2) {
					m_botAction.sendArenaMessage( "1 minute extension given to arrange teams.");
					m_botAction.scheduleTask(m_startGame, 60000);
				} else {
					m_botAction.scheduleTask(m_startGame, 20000);
				}
			};
		};

		m_3Seconds = new TimerTask()
		{
			public void run()
			{
				announcePlayers();
			};
		};

		m_startGame = new TimerTask()
		{
			public void run()
			{
				m_botAction.toggleLocked();
				ppIterator = m_botAction.getPlayingPlayerIterator();
				playersNum = 0;

				Iterator<Player> i3 = m_botAction.getPlayingPlayerIterator();

				while (i3.hasNext()) {
					Player p = i3.next();
					String name = p.getPlayerName();
					int tFreq = p.getFrequency();
					String freq = Integer.toString(tFreq);

					players.put( name, new pStats(name) );
					pStats check = players.get( name );

					playersNum++;
					check.register();
					check.setFreq(freq);
					if (shipType == 10) {
						if (p.getShipType() == 1 || p.getShipType() == 3 || p.getShipType() == 4 || p.getShipType() == 7) {
							check.setShip(p.getShipType());
						} else {
							check.setShip(1);
							m_botAction.setShip(name, 1);
						}
					} else if (shipType == 11) {
						if (p.getShipType() == 2 || p.getShipType() == 5 || p.getShipType() == 6 || p.getShipType() == 8) {
							check.setShip(p.getShipType());
						} else {
							check.setShip(2);
							m_botAction.setShip(name, 2);
						}
					} else if (shipType == 12) {
						if (p.getShipType() == 4 || p.getShipType() == 5) {
							check.setShip(p.getShipType());
						} else {
							check.setShip(5);
						}
					} else {
						check.setShip(shipType);
						m_botAction.setShip(name, shipType);
					}
				}

				if (maxPerFreq != 2 && playersNum > 64)	{
					maxPerFreq = 2;
					m_botAction.sendArenaMessage("Too many players for a normal tournament. Forcing a 2vs2 tournament (Random Teams).");
				}

				if (maxPerFreq == 2) {
					while (ppIterator.hasNext()) {
						Player p = ppIterator.next();
						String name = p.getPlayerName();

						if (!hasPartner(name)) {
							findPartner(name);
						}
					}

					Iterator<String> i2 = getPlayerList();

					while (i2.hasNext()) {
						String name2 = i2.next();
						pStats check = players.get( name2 );

						String freq = check.getFreq();

						if (!freqs.containsKey(freq)) {
							freqs.put( freq, new fStats() );
							fStats fCheck = freqs.get( freq );

							fCheck.addPlayer(check, 1);
						} else {
							fStats fCheck = freqs.get( freq );
							fCheck.addPlayer(check, 2);

							if (shipType == 12) {
								if (fCheck.getP1().getShip() == 4) {
									fCheck.getP2().setShip(5);
								} else {
									fCheck.getP2().setShip(4);
								}
							}
						}
					}
				} else {
					Iterator<String> i2 = getPlayerList();

					while (i2.hasNext()) {
						String name2 = i2.next();
						pStats check = players.get( name2 );

						String freq = Integer.toString(check.getPlayerID());

						if (!freqs.containsKey(freq)) {
							freqs.put( freq, new fStats() );
							fStats fCheck = freqs.get( freq );

							check.setFreq(freq);
							m_botAction.setFreq(name2, check.getPlayerID());
							fCheck.addPlayer(check, 1);
						}
					}
				}

				playersNum = playersNum / maxPerFreq;

				if (playersNum <= 1) {
					endTournament("hihi", 0);	// Not enough players, restarting..
					return;
				} else if (playersNum == 2) {
					firstRound = 6;
					maxBox = 1;
					m_botAction.sendArenaMessage("Registrations locked! Skipping first 5 rounds due to lack of players.", 2);
				} else if (playersNum <= 4 && playersNum >= 3) {
					firstRound = 5;
					maxBox = 2;
					m_botAction.sendArenaMessage("Registrations locked! Skipping first 4 rounds due to lack of players.", 2);
				} else if (playersNum <= 8 && playersNum >= 5) {
					firstRound = 4;
					maxBox = 4;
					m_botAction.sendArenaMessage("Registrations locked! Skipping first 3 rounds due to lack of players.", 2);
				} else if (playersNum <= 16 && playersNum >= 9) {
					firstRound = 3;
					maxBox = 8;
					m_botAction.sendArenaMessage("Registrations locked! Skipping first 2 rounds due to lack of players.", 2);
				} else if (playersNum <= 32 && playersNum >= 17) {
					firstRound = 2;
					maxBox = 16;
					m_botAction.sendArenaMessage("Registrations locked! Skipping first round due to lack of players.", 2);
				} else {
					firstRound = 1;
					maxBox = 32;
					m_botAction.sendArenaMessage("Registartions locked!", 2);
				}

				m_botAction.scheduleTask(m_3Seconds, 3000);
			};
		};

		trState = 1;
		if (forced) {
			setRealShipType(fShipType);
			deaths = fDeaths;
			trState = 3;
			if (forcer == m_botAction.getBotName()) {
				m_botAction.sendArenaMessage("Random pick! " + ship + " tournament to " + deaths + "!", 2);
			} else {
				m_botAction.sendArenaMessage("Forced tournament! " + forcer + " has forced a " + ship + " tournament to " + deaths + "!", 2);
			}

			m_botAction.sendArenaMessage( "Rules: No warping or spawning! " + maxLagOuts + " lagouts/duel allowed. You may spec/leave arena if you don't have an opponent.");

			if (maxPerFreq == 2) {
				m_botAction.sendArenaMessage( "1 minute extension given to arrange teams.");
				m_botAction.scheduleTask(m_startGame, 80000);
			} else {
				m_botAction.scheduleTask(m_startGame, 40000);
			}

			forced = false;
		} else {
			m_botAction.sendArenaMessage("Vote: 1vs1: 1-Warbird  2-Javelin  3-Spider  4-Any Fighter Ship  5-Any Base Ship");
			m_botAction.sendArenaMessage("      2vs2: 6-Warbird  7-Javelin  8-Any Fighter ship  9-Any Base Ship  10-LevTerr");

			m_botAction.scheduleTask(m_voteDeaths, 10000);
			m_botAction.scheduleTask(m_announceVote, 20000);
		}
	}


	/*
	 * This is used to set the real gametype.
	 */

	public void setRealShipType(int sType) {

		if (sType == 1) {
			shipType = 1;
			ship = "Warbird";
			base = false;
			maxPerFreq = 1;
		} else if (sType == 2) {
			shipType = 2;
			ship = "Javelin";
			base = true;
			maxPerFreq = 1;
		} else if (sType == 3) {
			shipType = 3;
			ship = "Spider";
			base = false;
			maxPerFreq = 1;
		} else if (sType == 4) {
			shipType = 10;
			ship = "AFS";
			base = false;
			maxPerFreq = 1;
		} else if (sType == 5) {
			shipType = 11;
			ship = "ABS";
			base = true;
			maxPerFreq = 1;
		} else if (sType == 6) {
			shipType = 1;
			ship = "2vs2 Warbird";
			base = false;
			maxPerFreq = 2;
		} else if (sType == 7) {
			shipType = 2;
			ship = "2vs2 Javelin";
			base = true;
			maxPerFreq = 2;
		} else if (sType == 8) {
			shipType = 10;
			ship = "2vs2 AFS";
			base = false;
			maxPerFreq = 2;
		} else if (sType == 9) {
			shipType = 11;
			ship = "2vs2 ABS";
			base = true;
			maxPerFreq = 2;
		} else if (sType == 10) {
			shipType = 12;
			ship = "2vs2 LevTerr";
			base = true;
			maxPerFreq = 2;
		}
	}


	/*
	 * Ends the tournament. Declares winner and hangles ads if   n   is 1.
	 */

	public void endTournament(String freq, int n) {
		trState = 0;
		if (n == 1) {
			tournyCount++;
			displayScores();
			fStats fWin = freqs.get(freq);
			finishTournament(tournyID, fWin.getNames());
			m_botAction.sendArenaMessage("GAME OVER: Winner " + fWin.getNames() + "!", 5);

			m_botAction.warpFreqToLocation(Integer.parseInt(freq), 512, 512);

			if (stopped) {
				trState = -1;
				stopped = false;
				m_botAction.sendArenaMessage("TournyBot disabled (" + tournyCount + " tournaments hosted)");
				m_botAction.sendSmartPrivateMessage("lnx", "Turnaus loppu!!");

			} else {
				m_botAction.sendChatMessage(1, "Tourny over. Next tourny starting soon.");
				m_botAction.sendChatMessage(2, "Tourny over. Next tourny starting soon.");
				m_botAction.sendChatMessage(3, "Tourny over, the winner was " + fWin.getNames() + ". Next tourny starting soon, ?go tourny now!");

				if ((tournyCount%2 == 0 || fZoner) && !zonerLock) {
					m_botAction.sendZoneMessage("Next automated tournament is starting. Type ?go tourny to play");
					fZoner = false;
				}

				if (tournyCount%5 == 0) {
					int rand = 1+ (int) (Math.random() * 10);
					forceGame(rand, 5, m_botAction.getBotName());
				}
			}
		} else {
			m_botAction.sendArenaMessage("Not enough players, restarting.", 2);
		}

		m_botAction.cancelTasks();
		duels.clear();
		freqs.clear();
		players.clear();
		votes.clear();
		laggers.clear();
		delayers.clear();

		m_endGame = new TimerTask()
		{
			public void run()
			{
				playersNum = 0;

				Iterator<Player> i = m_botAction.getPlayerIterator();

				while (i.hasNext()) {
					Player p = i.next();
					if (p.getShipType() != 0) {
						playersNum++;
					}
				}

				m_botAction.toggleLocked();
				m_botAction.sendTeamMessage("Free to enter");
			}
		};
		m_botAction.scheduleTask(m_endGame, 20000);
	}

	public void warpPlayer(String name) {
		m_botAction.warpTo(name, playerXPos(name), playerYPos(name));
	}

	public void warpFreq(String freq) {
		int iFreq = Integer.parseInt(freq);
		fStats fWarp = freqs.get( freq );
		m_botAction.warpFreqToLocation(iFreq, playerXPos(fWarp.getName1()), playerYPos(fWarp.getName1()));
	}

	public void advanceFreq(String freq) {
		fStats aCheck = freqs.get( freq );
		pStats pCheck = players.get( aCheck.getName1() );

		if (laggers.containsKey(aCheck.getName1())) {
            m_botAction.cancelTask(laggers.get( aCheck.getName1() ));
			laggers.remove( aCheck.getName1() );
			pCheck.toggleTrueLag(false);
			pCheck.toggleSmallLag(true);
		} else if (pCheck.getPlayerState() == 2) {
			pCheck.toggleTrueLag(false);
			pCheck.toggleSmallLag(true);
			m_botAction.sendPrivateMessage(aCheck.getName1(), "Send !return to enter.");
		} else if (m_botAction.getPlayer(aCheck.getName1()) == null || m_botAction.getPlayer(aCheck.getName1()).getShipType() == 0 ) {
			pCheck.toggleTrueLag(false);
			pCheck.toggleSmallLag(true);
		}
		pCheck.reset();
		pCheck.sleeping();

		if (maxPerFreq == 2) {
			pStats p2Check = players.get( aCheck.getName2() );

			if( laggers.containsKey( aCheck.getName2() ) ) {
                m_botAction.cancelTask(laggers.get( aCheck.getName2() ));
				laggers.remove( aCheck.getName2() );
				p2Check.toggleTrueLag(false);
				p2Check.toggleSmallLag(true);
			} else if (p2Check.getPlayerState() == 2) {
				p2Check.toggleTrueLag(false);
				p2Check.toggleSmallLag(true);
				m_botAction.sendPrivateMessage(aCheck.getName2(), "Send !return to enter.");
			} else if (m_botAction.getPlayer(aCheck.getName2()) == null || m_botAction.getPlayer(aCheck.getName2()).getShipType() == 0 ) {
				p2Check.toggleTrueLag(false);
				p2Check.toggleSmallLag(true);
			}
			p2Check.reset();
			p2Check.sleeping();
		}

		int newBox;

		aCheck.sleeping();
		aCheck.incrementRound();


		// Round 7 means   freq   has won the tournament
		if (aCheck.getRound() == 7) {
			pCheck.endTournament();
			endTournament( freq, 1 );
			return;
		} else {
			even = (aCheck.getBox()%2 == 0);
			if (even) {
				newBox = aCheck.getBox() / 2;
				aCheck.changeNro(2);
			} else {
				newBox = (aCheck.getBox() +1) / 2;
				aCheck.changeNro(1);
			}

			aCheck.changeBox(newBox);
			checkOpponent(freq, 2);
		}
	}


	/*
	 * This is used to find an opponent for   freq   in the same Box and Round
	 * Returns null if there aren't any
	 */

	public String findOpponent(String freq) {
		fStats fFreq = freqs.get( freq );

		Iterator<String> it = getFreqList();

		while (it.hasNext()) {
			String oFreq = it.next();
			fStats opponent = freqs.get( oFreq );

			if (opponent.getFreqState() != 2 && !freq.equals(oFreq) && opponent.getRound() == fFreq.getRound() && opponent.getBox() == fFreq.getBox()) {
				return oFreq;
			}
		}
		return null;
	}

	public String getPartner(String name) {
		pStats pCheck = players.get( name );
		fStats fCheck = freqs.get( pCheck.getFreq() );

		if (fCheck.getName1().equals(name)) {
			return fCheck.getName2();
		} else {
			return fCheck.getName1();
		}
	}

	public void removePlayer(String name, boolean forfeit) {

		if (laggers.containsKey(name)) {
            m_botAction.cancelTask(laggers.get( name ));
			laggers.remove(name);
		}

		pStats lCheck = players.get( name );

		if (findOpponent(lCheck.getFreq()) == null) {
		    return;
		}


		lCheck.remove();
		m_botAction.spec(name);
		m_botAction.spec(name);

		if (maxPerFreq == 2) {

			// Removes the freq if both players are out
			if (!playerStillIn( getPartner(name) )) {
				removeFreq( lCheck.getFreq(), forfeit, getPartner(name) );
			} else {
				if (!forfeit) {
					m_botAction.sendArenaMessage( name + " is out. " + lCheck.getGameKills() + " kills, " + lCheck.getGameDeaths() + " deaths");
				}
				m_botAction.setFreq(name, Integer.parseInt(lCheck.getFreq()));
			}
		} else {
			removeFreq( lCheck.getFreq(), forfeit, name );
		}
	}

	public void removeFreq(String freq, boolean forfeit, String name) {
		fStats fCheck = freqs.get( freq );
		String duelName = "r" + fCheck.getRound() + "b" + fCheck.getBox();
		dStats fDuel = duels.get( duelName );

		if (findOpponent(freq) != null) {
			String opponent = findOpponent( freq );
			fStats oFreq = freqs.get( opponent );
			oFreq.busy(0);
			fDuel.endDuel(oFreq, fCheck);
			fCheck.remove();

			if (forfeit) {
	    			fCheck.laggedOut();
				checkOpponent(opponent, 2);
			} else {
				int newRound = oFreq.getRound() + 1;

				if (newRound != 7) {
					if (maxPerFreq == 2) {
						m_botAction.sendArenaMessage( oFreq.getNames() + "[+" + fDuel.getWR() + "] defeat " + fCheck.getNames() + "[-" + fDuel.getLR() + "]  Score: [" + fCheck.getGameDeaths() + " - " + oFreq.getGameDeaths() + "]  and advance to " + getRoundName(newRound) + "!");
					} else {
						m_botAction.sendArenaMessage( oFreq.getNames() + "[+" + fDuel.getWR() + "] defeats " + fCheck.getNames() + "[-" + fDuel.getLR() + "]  Score: [" + fCheck.getGameDeaths() + " - " + oFreq.getGameDeaths() + "]  and advances to " + getRoundName(newRound) + "!");
					}
				} else {
					if (maxPerFreq == 2) {
						m_botAction.sendArenaMessage( oFreq.getNames() + "[+" + fDuel.getWR() + "] defeat " + fCheck.getNames() + "[-" + fDuel.getLR() + "]  Score: [" + fCheck.getGameDeaths() + " - " + oFreq.getGameDeaths() + "]!");
					} else {
						m_botAction.sendArenaMessage( oFreq.getNames() + "[+" + fDuel.getWR() + "] defeats " + fCheck.getNames() + "[-" + fDuel.getLR() + "]  Score: [" + fCheck.getGameDeaths() + " - " + oFreq.getGameDeaths() + "]!");
					}
				}
				advanceFreq( opponent );
			}
			playersNum--;
		}

		// Get the partner to specfreq too ..
		if (maxPerFreq == 2) {
			m_botAction.setShip(name, 1);
			m_botAction.spec(name);
			m_botAction.spec(name);
		}
	}


	/*
	 * This is used to determine if   name   is still in the tournament (or has he ever been in it..)
	 */

	public boolean playerStillIn(String name) {

		if (!players.containsKey(name)) {
		    return false;
		}

		pStats stillIn = players.get( name );

		if (stillIn.getPlayerState() != 2) {
			return true;
		} else {
			return false;
		}
	}


	/*
	 * This is used to determine if   freq   is still in the tournament (or has it ever been in it..)
	 */

	public boolean freqStillIn(String freq) {

		if (!freqs.containsKey(freq)) {
		    return false;
		}

		fStats stillIn = freqs.get( freq );

		if (stillIn.getFreqState() != 2) {
			return true;
		} else {
			return false;
		}
	}


	/*
	 * This is used when forming the freqs. Returns true if   name   is not alone on his freq
	 */

	public boolean hasPartner(String name) {
		pStats player = players.get( name );
		Iterator<String> it = getPlayerList();

		while (it.hasNext()) {
			String name2 = it.next();
			pStats partner = players.get( name2 );
			if (!name.equals(name2) && player.getFreq().equals(partner.getFreq())) {
				return true;
			}
		}
		return false;
	}


	/*
	 * This is used when forming the freqs. Goes through all the players to find a possible partner
	 */

	public void findPartner(String name) {
		pStats player = players.get( name );
		Iterator<String> it2 = getPlayerList();

		while (it2.hasNext()) {
			String name2 = it2.next();
			pStats partner = players.get(name2);

			if (!name.equals(name2) && !hasPartner(name2)) {
				player.setFreq(partner.getFreq());
				player.setRandomT(true);
				partner.setRandomT(true);
				m_botAction.setFreq(name, Integer.parseInt(partner.getFreq()));
				return;
			}
		}
		m_botAction.spec(name);
		m_botAction.spec(name);
		playersNum--;
		players.remove(name);
		m_botAction.sendPrivateMessage(name, "Failed to find a partner for you. Next time make sure that you have a partner before the timer runs out.");
	}


	/*
	 * Sends queries of every team/player in the tournament to get their rating & ID
	 * Also assigns random number for each freq to make the seedings more random
	 */

	public void announcePlayers() {
		createTournament(playersNum);

		qSent = 0;
		qReceived = 0;

		int[] seedings = new int[playersNum];
		int temp = 0;

		Iterator<String> it = getFreqList();

		while (it.hasNext()) {
			String freq = it.next();
			fStats rand = freqs.get( freq );

			if (dbAvailable) {

				if (maxPerFreq == 1) {
					m_botAction.SQLBackgroundQuery(dbConn, rand.getP1().getFreq(), "SELECT player_id, rating FROM tblTournyPlayerStats WHERE playerName = '" + Tools.addSlashesToString(rand.getName1()) + "'");
					qSent++;

				} else {
					if (rand.getP1().getRandomT() && rand.getP2().getRandomT()) {
						rand.setDBID(-1);
						m_botAction.SQLBackgroundQuery(dbConn, rand.getP1().getFreq() + ":1", "SELECT player_id, rRating FROM tblTournyPlayerStats WHERE playerName = '" + Tools.addSlashesToString(rand.getName1()) + "'");
						qSent++;
						m_botAction.SQLBackgroundQuery(dbConn, rand.getP2().getFreq() + ":2", "SELECT player_id, rRating FROM tblTournyPlayerStats WHERE playerName = '" + Tools.addSlashesToString(rand.getName2()) + "'");
						qSent++;
					} else {
						m_botAction.SQLBackgroundQuery(dbConn, rand.getP1().getFreq(), "SELECT team_id, rating, player1Name, player2Name FROM tblTournyTeamStats WHERE (player1Name = '" + Tools.addSlashesToString(rand.getName1()) + "' AND player2Name = '" + Tools.addSlashesToString(rand.getName2()) + "') OR (player1Name = '" + Tools.addSlashesToString(rand.getName2()) + "' AND player2Name = '" + Tools.addSlashesToString(rand.getName1()) + "')");
						qSent++;
					}
				}
			}

			double tRandNum = 10000 * Math.random();
			int randNum = (int)tRandNum;

			rand.setRandNum(randNum);
			seedings[temp] = randNum;
			temp++;
		}

		Arrays.sort(seedings);

		int xB = 0;
		int yB = 0;
		int box = 1;
		int pNro = 1;

		for (int c = 0; c < seedings.length; c++) {
			Iterator<String> it2 = getFreqList();

			while (it2.hasNext()) {
				String freq = it2.next();
				fStats rCheck = freqs.get( freq );

				if (seedings[c] == rCheck.getRandNum()) {
					rCheck.setRealBox( Integer.toString(xB) + Integer.toString(yB) );

					xB++;
					if (xB > 3) {
						xB = 0;
						yB++;
					}

					rCheck.changeRound(firstRound);
					rCheck.changeNro(pNro);
					rCheck.changeBox(box);
					box++;

					if (box > maxBox) {
						xB = 0;
						yB = 0;
						box = 1;
						pNro = 2;
					}
				}
			}
		}

		nChecks = 0;

		// Schedules a task to check every second if all the queries have returned
		checkQueries = new TimerTask()
	        {
			public void run()
			{
				if (!dbAvailable) {
					reallyAnnouncePlayers();
				} else if (qSent <= qReceived) {
					reallyAnnouncePlayers();
				} else if (nChecks > 9) {
					dbAvailable = false;
					reallyAnnouncePlayers();
				} else {
					nChecks++;
				}
			}
		};
	        m_botAction.scheduleTaskAtFixedRate(checkQueries, 2000, 1000);
	}

	public void reallyAnnouncePlayers() {
        m_botAction.cancelTask(checkQueries);
		trState = 4;

		Iterator<String> it3 = getFreqList();

		while (it3.hasNext()) {
			String freq = it3.next();

			checkOpponent(freq, 1);
		}

		m_announcePrize = new TimerTask()
		{
			public void run()
			{
				if (dbAvailable) {
					if (playersNum < 5) {
						trPrize = 0;
						m_botAction.sendArenaMessage("Not enough teams for a prize tournament (Prize: 0)");
					} else {
						double tTrPrize = trPrize / 30;
						trPrize = (int)tTrPrize;
						m_botAction.sendArenaMessage("The prize of this tournament is " + trPrize + "! Good luck!");
					}
				} else {
					m_botAction.sendArenaMessage("Database unavailable, this tournament will not be recorded.");
				}
			}
		};
		m_botAction.scheduleTask(m_announcePrize, 5000);
	}

	public String getRoundName(int round) {

		if (round < 5) {
			return "Round " + round;
		} else if (round == 5) {
			return "Semi-Final";
		} else {
			return "FINAL";
		}
	}

	public void forceGame(int fT, int fD, String f) {
		boolean ok = true;

		if (forced && (!f.equals(m_botAction.getBotName()) || !f.equalsIgnoreCase(forcer))) {
			m_botAction.sendPrivateMessage(f, forcer + " has already forced a tournament.");
			ok = false;
		}

		if (fT < 1 || fT > 10) {
			m_botAction.sendPrivateMessage(f, "Gametype needs to be 0-10.");
			ok = false;
		}

		if (fD <1 || fT > 10) {
			m_botAction.sendPrivateMessage(f, "Deathlimit needs to be 0-10.");
			ok = false;
		}

		if (ok) {
			forced = true;
			fShipType = fT;
			fDeaths = fD;
			forcer = f;

			m_botAction.sendPrivateMessage(f, "Feel the force! Only my random may override your !force.");
		}
	}

	public void startDuel(String f1, String f2, int round, int box) {
		fStats freq1 = freqs.get( f1 );
		fStats freq2 = freqs.get( f2 );
		freq1.busy(1);
		freq2.busy(1);

		String duelName = "r" + round + "b" + box;

		duels.put( duelName, new dStats(freq1, freq2, maxPerFreq, tournyID, m_botAction) );

		m_botAction.sendArenaMessage( getRoundName(round) + ": " + freq1.getNames() + " vs. " + freq2.getNames() + " starting in 10 seconds");

		readyToPlay(freq1.getP1());
		readyToPlay(freq2.getP1());

		if (maxPerFreq == 2) {
			readyToPlay(freq1.getP2());
			readyToPlay(freq2.getP2());
		}

		m_botAction.scheduleTask( new duelStart( freq1, freq2 ), 10000 );
	}

	public void readyToPlay(pStats player) {
		fStats ops = freqs.get(findOpponent(player.getFreq()));

		m_botAction.setFreq(player.getName(), Integer.parseInt(player.getFreq()));

		if (player.getSmallLag() || m_botAction.getPlayer(player.getName()) == null || m_botAction.getPlayer(player.getName()).getShipType() == 0 ) {
			player.toggleSmallLag(true);
			m_botAction.sendSmartPrivateMessage( player.getName(), "Your duel vs. " + ops.getNames() + " is starting. You have 10 seconds to !return before you get a lagout.");
		} else {
			m_botAction.setShip(player.getName(), player.getShip());
		}
	}


	/*
	 * Tries to find an opponent for   freq
	 * If an opponent with same box and round is found, a duel between them is started
	 * If there aren't any opponents with the same box and round, it goes through all the players to see possible future opponents
	 */

	public void checkOpponent(String freq, int type) {
		fStats roundBox = freqs.get( freq );

		final int round = roundBox.getRound();
		int box = roundBox.getBox();

		if (roundBox.getBusy() != 0) {
		    return;
		}

		fStats oCheck;

		if (findOpponent(freq) != null) {
			oCheck = freqs.get(findOpponent(freq));
			String oFreq = oCheck.getP1().getFreq();

			roundBox.setRealBox(oCheck.getRealBox());
			warpFreq(freq);
			startDuel(freq, oFreq, round, box);
		} else {
			Iterator<String> it = getFreqList();

			while (it.hasNext()) {
				String oFreq = it.next();
				oCheck = freqs.get(oFreq);

				if (oCheck.getFreqState() != 2) {
					int box1;
					int lBox;

					for (int aRound = round; aRound >= 2; aRound--) {

						if (aRound == 2) {
							int newRound = 1;

							if (round == 3)	{
								box1 = box *4;
								lBox = box1 -3;

							} else if (round == 4) {
								box1 = box *8;
								lBox = box1 -7;

							} else if (round == 5) {
								box1 = box *16;
								lBox = box1 -15;

							} else if (round == 6) {
								box1 = 32;
								lBox = 1;

							} else {
								box1 = box *2;
								lBox = box1 -1;
							}

							for (int fBox = box1; fBox >= lBox; fBox--) {

								if (newRound == oCheck.getRound() && fBox == oCheck.getBox()) {
									warpFreq(freq);
									return;
								}
							}

						} else if (aRound == 3)	{
							int newRound = 2;

							if (round == 4) {
								box1 = box *4;
								lBox = box1 -3;

							} else if (round == 5) {
								box1 = box *8;
								lBox = box1 -7;

							} else if (round == 6) {
								box1 = box *16;
								lBox = box1 -15;

							} else {
								box1 = box *2;
								lBox = box1 -1;
							}

							for (int fBox = box1; fBox >= lBox; fBox--) {

								if (newRound == oCheck.getRound() && fBox == oCheck.getBox()) {
									warpFreq(freq);
									return;
								}
							}

						} else if (aRound == 4) {
							int newRound = 3;

							if (round == 5) {
								box1 = box *4;
								lBox = box1 -3;

							} else if (round == 6) {
								box1 = box *8;
								lBox = box1 -7;

							} else {
								box1 = box *2;
								lBox = box1 -1;
							}

							for (int fBox = box1; fBox >= lBox; fBox--) {

								if (newRound == oCheck.getRound() && fBox == oCheck.getBox()) {
									warpFreq(freq);
									return;
								}
							}

						} else if (aRound == 5) {
							int newRound = 4;

							if (round == 6) {
								box1 = box * 4;
								lBox = box1 -3;

							} else {
								box1 = box *2;
								lBox = box1 -1;
							}

							for (int fBox = box1; fBox >= lBox; fBox--) {

								if (newRound == oCheck.getRound() && fBox == oCheck.getBox()) {
									warpFreq(freq);
									return;
								}
							}

						} else if (aRound == 6)	{
							int newRound = 5;

							box1 = box *2;
							lBox = box1 -1;

							for (int fBox = box1; fBox >= lBox; fBox--) {

								if (newRound == oCheck.getRound() && fBox == oCheck.getBox()) {
									warpFreq(freq);
									return;
								}
							}
						}
					}
				}
			}

			// If no possible opponent is found,   freq   wins by default.
			warpFreq(freq);

			if (freqStillIn(freq)) {

				if (type == 1) {
					String duelName = "r" + round + "b" + box;
					duels.put( duelName, new dStats(maxPerFreq, tournyID, m_botAction) );
					dStats forfeitD = duels.get( duelName );
					forfeitD.endForfeitDuel(roundBox);
				}

				int xRound = round +1;
				if (xRound != 7) {
					if (maxPerFreq == 2) {
						m_botAction.sendArenaMessage( roundBox.getNames() + " win by default and advance to " + getRoundName(xRound) + "!");
					} else {
						m_botAction.sendArenaMessage( roundBox.getNames() + " wins by default and advances to " + getRoundName(xRound) + "!");
					}
				}
				advanceFreq(freq);
			}
		}
	}

	public void displayScores() {
		String out;

		m_botAction.sendArenaMessage( "+" + Tools.formatString("", 77, "-") + "+");
		m_botAction.sendArenaMessage( "| " + Tools.formatString("" + ship + " tournament to " + deaths + " death(s)", 45) + "Kills   Deaths   RChange   DQ  |");
		m_botAction.sendArenaMessage( "+" + Tools.formatString("", 77, "-") + "+");

		for (int r = 7; r >= 3; r--) {
			int eka = 1;
			Iterator<String> i = getFreqList();

			while (i.hasNext()) {
				String frequency = i.next();
				fStats freq = freqs.get( frequency );

				if (freq.getRound() == r) {

					if (eka == 1) {

						if (r == 7) {
						    out = Tools.formatString("  " + "Winner", 14);
						} else if (r == 6) {
						    out = Tools.formatString("  " + "Runner up", 14);
						} else {
						    out = Tools.formatString("  " + "Round " + r, 14);
						}
					} else {
						out = Tools.formatString("  " + " ", 14);
					}

					if (freq.getNames().length() > 30) {
						out += Tools.formatString("" + freq.getNames(), 32);
					} else {
						out += Tools.formatString("" + freq.getNames(), 31);
					}

					out += Tools.formatString("" + freq.getTotalKills(), 8);
					out += Tools.formatString("" + freq.getTotalDeaths(), 9);
					out += Tools.formatString("" + freq.getFRating(trPrize), 10);
					out += Tools.formatString("" + freq.getLagOut(), 3);
					m_botAction.sendArenaMessage("| " + out + " |");
					eka++;
				}
			}
		}
		m_botAction.sendArenaMessage( "+" + Tools.formatString("", 77, "-") + "+");
	}

	public Iterator<String> getPlayerList() {
		return players.keySet().iterator();
	}

	public Iterator<String> getFreqList() {
		return freqs.keySet().iterator();
	}

	public Iterator<String> getDuelList() {
		return duels.keySet().iterator();
	}

	public void handleVote(String name, String message, int range) {
		try {
			if (Tools.isAllDigits(message)) {
				int vote;

				try {
					vote = Integer.parseInt(message);
				} catch (NumberFormatException nfe) {
					return;
				}

				if(!(vote > 0 && vote <= range)) {
					return;
				}

				votes.put(name, new Integer(vote));
			}
		} catch( Exception e ) {}
	}

	public int countVote(int range) {
		int winner = 0;
		int[] counters = new int[range+1];
		Iterator<Integer> iterator = votes.values().iterator();

		while (iterator.hasNext()) {
			counters[iterator.next().intValue()]++;
		}

		for (int i = 1; i < counters.length; i++) {

			if (counters[winner] < counters[i]) {
				winner = i;
			}
		}
		return winner;
	}

	class duelStart extends TimerTask {
		fStats freq1;
		fStats freq2;

		public duelStart( fStats fr1, fStats fr2 ) {
			freq1 = fr1;
			freq2 = fr2;
		}

		public void run()
		{
			if (trState == 4) {
				String f1 = freq1.getP1().getFreq();
				String f2 = freq2.getP1().getFreq();

				if (freqStillIn(f1) && freqStillIn(f2))	{
					handlePlayerGo(freq1.getP1());
					handlePlayerGo(freq2.getP1());

					if (maxPerFreq == 2) {
						handlePlayerGo(freq1.getP2());
						handlePlayerGo(freq2.getP2());
					}
				}
			}
		}
	}

	public void handlePlayerGo(pStats p) {
		p.playing();

		if (p.getSmallLag() || m_botAction.getPlayer(p.getName()) == null || m_botAction.getPlayer(p.getName()).getShipType() == 0 ) {
			p.incrementLagOuts();
			p.toggleTrueLag(true);

			if (laggers.containsKey(p.getName())) {
			    m_botAction.cancelTask( laggers.get(p.getName()));
				laggers.remove(p.getName());
			}
			laggers.put( p.getName(), new Lagger( p.getName(), laggers ) );
			Lagger l = laggers.get( p.getName() );
			m_botAction.scheduleTask( l, 60000 );

			fStats freq2 = freqs.get( findOpponent(p.getFreq()) );

			m_botAction.sendSmartPrivateMessage( p.getName(), "Your duel vs. " + freq2.getNames() + " started without you. (= +1 lagout [Total: " + p.getLagOuts() + "] You have 60 seconds to !return)");
			m_botAction.sendPrivateMessage( freq2.getName1(), "Your opponent (" + p.getName() + ") failed to show up, and has 60 seconds to return. Total " + p.getLagOuts() + " lagouts.");

			if (maxPerFreq == 2) {
				m_botAction.sendPrivateMessage( getPartner(p.getName()), "Your partner (" + p.getName() + ") failed to show up, and has 60 seconds to return. Total " + p.getLagOuts() + " lagouts.");
				m_botAction.sendPrivateMessage( freq2.getName2(), "Your opponent (" + p.getName() + ") failed to show up, and has 60 seconds to return. Total " + p.getLagOuts() + " lagouts.");
			}
		} else {
			m_botAction.sendPrivateMessage( p.getName(), "Go!", 104);
			m_botAction.shipReset( p.getName() );
			m_botAction.scoreReset( p.getName() );
			warpPlayer( p.getName() );
		}
	}

	void setupStart() {
		m_start = new TimerTask()
		{
			public void run()
			{
				if (trState == -1) { startTournament(); }
			}
		};
	}

	void setupStop() {
		m_stop = new TimerTask()
		{
			public void run()
			{
				stopped = true;
			}
		};
	}

	public long getStartTime() {
		String[] ids = TimeZone.getAvailableIDs(-5 * 60 * 60 * 1000);
	    	SimpleTimeZone est = new SimpleTimeZone(-5 * 60 * 60 * 1000, ids[0]);
		Calendar greg = new GregorianCalendar(est);
		int day = greg.get(Calendar.DAY_OF_WEEK);
		int hours = greg.get(Calendar.HOUR_OF_DAY);
		int mins = greg.get(Calendar.MINUTE);
		long currentTime = ((((day - 1) * 24) + hours)  * 60 + mins) * 60 * 1000;
		long startingTime = ((5 * 24) + startHour) * 60 * 60 * 1000;
		long timeTil = startingTime - currentTime;

		if (timeTil < 0) {
			timeTil = (7 * 24 * 60 * 60 * 1000) + timeTil;
			if (trState == -1) {
				startTournament();
			}
		}
		return timeTil;
	}

	public long getStopTime() {
		String[] ids = TimeZone.getAvailableIDs(-5 * 60 * 60 * 1000);
		SimpleTimeZone est = new SimpleTimeZone(-5 * 60 * 60 * 1000, ids[0]);
		Calendar greg = new GregorianCalendar(est);
		int day = greg.get(Calendar.DAY_OF_WEEK);
		int hours = greg.get(Calendar.HOUR_OF_DAY);
		int mins = greg.get(Calendar.MINUTE);
		long currentTime = ((((day - 1) * 24) + hours)  * 60 + mins) * 60 * 1000;
		long stopingTime = (stopHour * 60) * 60 * 1000;
		long timeTil = stopingTime - currentTime;

		if (timeTil < 0) {
			timeTil = (7 * 24 * 60 * 60 * 1000) + timeTil;
		}
		return timeTil;
	}

	public void changeStart(String message) {
		String pieces[] = message.split(" ");
		int tempStart = 9;

		try {
			tempStart = Integer.parseInt(pieces[1]);
		} catch(Exception e) {}

		if (tempStart < 0) {
			tempStart = 0;
		}

		if (tempStart > 23) {
			tempStart = 23;
		}

		startHour = tempStart;
        m_botAction.cancelTask(m_start);
		setupStart();
		m_botAction.scheduleTaskAtFixedRate(m_start, getStartTime(), 7 * 24 * 60 * 60 * 1000);
	}

	public void changeStop(String message) {
		String pieces[] = message.split(" ");
		int tempStop = 23;

		try {
			tempStop = Integer.parseInt(pieces[1]);
		} catch(Exception e) {}

		if (tempStop < 0) {
			tempStop = 0;
		}

		if (tempStop > 23) {
			tempStop = 23;
		}

		stopHour = tempStop;
		m_stop.cancel();
		setupStop();
		m_botAction.scheduleTaskAtFixedRate(m_stop, getStopTime(), 7 * 24 * 60 * 60 * 1000);
	}

	/*
	 * Creates a record of the tournament to database
	 */

	public void createTournament(int num) {
		try {
			String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
			String typeTxt = ship + " tournament to " + deaths + " death(s)";
			String fields[] = {	"type", "typeTxt", "players", "started", "trState" };
			String values[] = { Integer.toString(maxPerFreq), typeTxt, Integer.toString(num), time, Integer.toString(0) };

			m_botAction.SQLInsertInto(dbConn, "tblTournyTournaments", fields, values);
			dbAvailable = true;
		} catch (Exception e) {
			dbAvailable = false;
		};

		trPrize = 0;

		try {
			ResultSet s = m_botAction.SQLQuery(dbConn, "SELECT MAX(id) AS tournyID FROM tblTournyTournaments");
			if (s.next()) {
				tournyID = s.getInt("tournyID");
			}
			m_botAction.SQLClose( s );
		} catch (Exception e) {
			dbAvailable = false;
			tournyID = -1;
		};

	}


	/*
	 * Finishes the tournament by updating playerstats and sending all the duel results to database
	 * Also updates the Rank tables
	 */

	public void finishTournament(int id, String winner) {
		if (!dbAvailable) {
			m_botAction.sendArenaMessage("Database error, no statistics available.");
			return;
		}

		try {
			Iterator<String> d = getDuelList();

			while (d.hasNext()) {
				String duel = d.next();
				dStats dU = duels.get( duel );

				dU.storeDuel(dbConn);
			}

			Iterator<String> f = getFreqList();

			while (f.hasNext()) {
				String freq = f.next();
				fStats fU = freqs.get( freq );

				String query;
				String query2;

				if (maxPerFreq == 1) {
					query = "UPDATE tblTournyPlayerStats SET rating = rating + '" + fU.getFRating(trPrize) + "', wins = wins + '" + fU.getWins() + "', losses = losses + '" + fU.getLosses() + "', kills = kills + '" + fU.getTotalKills() + "', deaths = deaths + '" + fU.getTotalDeaths() + "', trs = trs + '1' ";

					if (fU.getRound() == 7) {
						query += ", trWins = trWins + '1' ";
					}

					query += "WHERE player_id = '" + fU.getDBID() + "'";
				} else {
					if (fU.getDBID() == -1) {
						query = "UPDATE tblTournyPlayerStats SET rRating = rRating + '" + fU.getFRating(trPrize) + "', rWins = rWins + '" + fU.getWins() + "', rLosses = rLosses + '" + fU.getLosses() + "', rKills = rKills + '" + fU.getP1().getTotalKills() + "', rDeaths = rDeaths + '" + fU.getP1().getTotalDeaths() + "', rTrs = rTrs + '1' ";

						if (fU.getRound() == 7) {
							query += ", rTrWins = rTrWins + '1' ";
						}

						query += "WHERE player_id = '" + fU.getP1().getDBID() + "'";
						query2 = "UPDATE tblTournyPlayerStats SET rRating = rRating + '" + fU.getFRating(trPrize) + "', rWins = rWins + '" + fU.getWins() + "', rLosses = rLosses + '" + fU.getLosses() + "', rKills = rKills + '" + fU.getP2().getTotalKills() + "', rDeaths = rDeaths + '" + fU.getP2().getTotalDeaths() + "', rTrs = rTrs + '1' ";

						if (fU.getRound() == 7) {
							query2 += ", rTrWins = rTrWins + '1' ";
						}

						query2 += "WHERE player_id = '" + fU.getP2().getDBID() + "'";
						m_botAction.SQLQueryAndClose(dbConn, query2);
					} else {
						query = "UPDATE tblTournyTeamStats SET rating = rating + '" + fU.getFRating(trPrize) + "', wins = wins + '" + fU.getWins() + "', losses = losses + '" + fU.getLosses() + "', player1Kills = player1Kills + '" + fU.getP1().getTotalKills() + "', player1Deaths = player1Deaths + '" + fU.getP1().getTotalDeaths() + "', player2Kills = player2Kills + '" + fU.getP2().getTotalKills() + "', player2Deaths = player2Deaths + '" + fU.getP2().getTotalDeaths() + "', trs = trs + '1' ";

						if (fU.getRound() == 7) {
							query += ", trWins = trWins + '1' ";
						}

						query += "WHERE team_id = '" + fU.getDBID() + "'";
					}
				}
				m_botAction.SQLQueryAndClose(dbConn, query);
			}

			if (maxPerFreq == 1) {
			    m_botAction.SQLQueryAndClose(dbConn, "TRUNCATE TABLE tblTourny1v1Ranks");
				int rank = 1;
				ResultSet s = m_botAction.SQLQuery(dbConn, "SELECT player_id, rating FROM tblTournyPlayerStats ORDER BY rating DESC LIMIT 100");
				while (s.next()) {
					String fields[] = {
						"rank",
						"player_id"
					};
					String values[] = {
						Integer.toString(rank),
						Integer.toString(s.getInt("player_id"))
					};
					m_botAction.SQLBackgroundInsertInto(dbConn, "tblTourny1v1Ranks", fields, values);
					rank++;
				}
				m_botAction.SQLClose( s );
			} else {
			    m_botAction.SQLQueryAndClose(dbConn, "TRUNCATE TABLE tblTourny2v2Ranks");
				int rank = 1;
				ResultSet s = m_botAction.SQLQuery(dbConn, "SELECT team_id, rating FROM tblTournyTeamStats ORDER BY rating DESC LIMIT 100");
				while (s.next()) {
					String fields[] = {
						"rank",
						"team_id"
					};
					String values[] = {
						Integer.toString(rank),
						Integer.toString(s.getInt("team_id"))
					};
					m_botAction.SQLBackgroundInsertInto(dbConn, "tblTourny2v2Ranks", fields, values);
					rank++;
				}
				m_botAction.SQLClose( s );
				m_botAction.SQLQueryAndClose(dbConn, "TRUNCATE TABLE tblTournyRRanks");

				int rank2 = 1;
				ResultSet s2 = m_botAction.SQLQuery(dbConn, "SELECT player_id, rRating FROM tblTournyPlayerStats ORDER BY rRating DESC LIMIT 100");
				while (s2.next()) {
					String fields2[] = {
						"rRank",
						"player_id"
					};
					String values2[] = {
						Integer.toString(rank2),
						Integer.toString(s2.getInt("player_id"))
					};
					m_botAction.SQLBackgroundInsertInto(dbConn, "tblTournyRRanks", fields2, values2);
					rank2++;
				}
				m_botAction.SQLClose( s2 );
			}

			String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
			m_botAction.SQLQueryAndClose(dbConn, "UPDATE tblTournyTournaments SET prize = '" + trPrize + "', trState = 1, finished = '" + time + "', winner = '" + winner + "' WHERE id = " + id);
			m_botAction.sendArenaMessage("Complete tournament statistics at: " + m_botSettings.getString( "websiteR" ) + tournyID);
		} catch (Exception e) {
			m_botAction.sendArenaMessage("Database error, no statistics available.");
		};
	}

	class aboutToDieOut extends TimerTask {
		String player;

		public aboutToDieOut( String name ) {
			player = name;
		}

		public void run()
		{
			if (players.containsKey(player)) {
				pStats info2 = players.get(player);

				if (info2.getGameDeaths() >= deaths) {
					removePlayer(player, false);
				}
			}
		}
	}

	class duelDelay extends TimerTask {
		String dName;
		String player;
		String killer;
		HashMap<String, duelDelay> delayers;
		long timeStamp;
		boolean silent;

		public duelDelay( String tName, String name, String name2, HashMap<String, duelDelay> d, boolean s, long time ) {
			dName = tName;
			player = name;
			killer = name2;
			delayers = d;
			silent = s;
			timeStamp = time;
		}

		public void run()
		{
			delayers.remove(dName);

			if (trState == 4) {
				pStats info = players.get( killer );
				fStats fInfo = freqs.get( info.getFreq() );

				pStats info2 = players.get( player );
				fStats fInfo2 = freqs.get( info2.getFreq() );

				if( fInfo == null || fInfo2 == null )
				    return;

				if (freqStillIn(info2.getFreq()) && freqStillIn(info.getFreq())) {
					m_botAction.shipReset(player);

					if (!silent) {

						if (fInfo == fInfo2) {
							fInfo2 = freqs.get(findOpponent(info.getFreq()));
						}
						if( fInfo2 == null )
						    return;

						m_botAction.sendOpposingTeamMessage( m_botAction.getPlayerID(fInfo.getName1()), "Score: [" + fInfo.getNames() + " " + fInfo2.getGameDeaths() + " - " + fInfo.getGameDeaths() + " " + fInfo2.getNames() + "]", 0);
						m_botAction.sendOpposingTeamMessage( m_botAction.getPlayerID(fInfo2.getName1()), "Score: [" + fInfo2.getNames() + " " + fInfo.getGameDeaths() + " - " + fInfo2.getGameDeaths() + " " + fInfo.getNames() + "]", 0);
					}

					warpPlayer(player);
					if (maxPerFreq != 2) {
						m_botAction.shipReset(killer);
						warpPlayer(killer);
					}
				}
			}
		}
	}

	class Lagger extends TimerTask {
		String player;
		HashMap<String, Lagger> laggers;

		public Lagger( String name, HashMap<String, Lagger> l ) {
			player = name;
			laggers = l;
		}

		public void run()
		{
			pStats lagi = players.get( player );

			String dqM = "failed to return from his lagout in time and was disqualified.";

			m_botAction.sendArenaMessage( player + " " + dqM);
			laggers.remove(player);
			lagi.setNotes(dqM);
			lagi.laggedOut();
			removePlayer(player, true);
		}
	}
}

