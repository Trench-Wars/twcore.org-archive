package twcore.bots.tournybot;

import twcore.core.*;
import java.util.*;

public class tournybot extends SubspaceBot {
	
	private BotSettings m_botSettings;
	HashMap players;
	HashMap votes;
	HashMap laggers;

	TimerTask m_20Seconds;
	TimerTask m_10Seconds;
	TimerTask m_startGame;
	TimerTask m_endGame;
	TimerTask m_duelStart;
	TimerTask m_duelDelay;
	TimerTask m_defaultA;
	TimerTask m_announceVote;
	TimerTask m_voteDeaths;
	TimerTask m_zoner;

	String ship;		// Shipname used in tourny
	int shipType = 0;	// Shipnumber used in tourny
	int deaths = 0;		// Deathlimit in tourny
	int playersNum = 0;	// # of players still in the tourny
	int trState = -1;	// Tournament state. -1 = stopped | 0 = less than 2 players have entered | 1 = voting on shiptype 
				// 2 = voting on deathlimit | 3 = starting in 10 seconds | 4 = tourney in progress
	int maxLagOuts = 2;	// Max lagouts
	int maxWarps = 3;	// Max warps
	int tournyCount = 0;
	boolean stopped = false;
	boolean even;
	boolean zoner = true;
	boolean zonerLock = false;

	public tournybot(BotAction botAction) {
		super(botAction);
		players = new HashMap();
		votes = new HashMap();
		laggers = new HashMap();

		requestEvents();
		m_botSettings = m_botAction.getBotSettings();
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

		if (event.getMessageType() == Message.PRIVATE_MESSAGE && (event.getMessage().equalsIgnoreCase("!about") || event.getMessage().equalsIgnoreCase("!help"))) {
		
			String aboutER[] = {
				"| !start              - Enables the tournybot              |",
				"| !stop               - Stops the tournybot. Note: Doesn't |",
				"|                       stop the ongoing tourny. Prevents  |",
				"|                       new tournies from starting.        |",
				"| !die	               - Kills the bot                      |",
				"+----------------------------------------------------------+"
			};
			String aboutSMOD[] = {
				"| !zone               - Enables/Disables *zoners           |",
				"+----------------------------------------------------------+"
			};

			String about[] = {
				"+----------------------------------------------------------+",
				"| TournyBot v.0.90b                         - author Sika  |",
				"+----------------------------------------------------------+",
				"| Hosts an automated tournament with up to 64 players.     |",
				"| Tournament begins when 2 or more players enter the game. |",
				"+----------------------------------------------------------+",
				"| !help OR !about     - Brings up this message             |",
				"| !status             - Tournament status                  |",
				"| !score <player>     - Displays the person's score        |",
				"| !score              - Displays your score                |",
				"| !return OR !lagout  - Return to the game                 |",
				"+----------------------------------------------------------+"
			};
			m_botAction.privateMessageSpam(name, about);
			if ( m_botAction.getOperatorList().isER( name ) ) { m_botAction.privateMessageSpam(name, aboutER); }
			if ( m_botAction.getOperatorList().isSmod( name ) ) { m_botAction.privateMessageSpam(name, aboutSMOD); }
		}

		if ((event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) && event.getMessage().equalsIgnoreCase("!status")) {
			String status;
			if (trState == -1) { status = "Waiting for host to !start me."; } else if (trState == 1 || trState == 2) { status = "Voting on gametype."; } else if (trState == 3) { status = ship + " tournament to " + deaths + " death(s) starting."; } else { status = ship + " tournament to " + deaths + " death(s) in progress with " + playersNum + " players in."; }
			m_botAction.sendSmartPrivateMessage(name, "Tournament Status: " + status);
		}

		if (event.getMessageType() == Message.PRIVATE_MESSAGE && event.getMessage().startsWith("!score")) {
			String scoreof;
			String message = event.getMessage();
			String temp = name;
			try
			{
				String pieces[] = message.split(" ");
				temp = pieces[1];
			} catch(Exception e) {}
			scoreof = temp;
			handleScore(name, scoreof);
		}

		if (event.getMessageType() == Message.PRIVATE_MESSAGE && (event.getMessage().equalsIgnoreCase("!return") || event.getMessage().equalsIgnoreCase("!lagout")) && players.containsKey(name)) {
			if (playerStillIn(name) && trState == 4 && m_botAction.getPlayer(name).getShipType() == 0) {

				pStats messager = (pStats)players.get( name );
				
				if (laggers.containsKey(name)) {
					((Lagger)laggers.get( name )).cancel();
					laggers.remove( name );
				
					if (findOpponent(name) != null)	{

						int remainingLO = maxLagOuts - messager.getLagOuts();

						if (messager.getPlayerState() == 1){
							m_botAction.sendPrivateMessage( name, "Go! (You have " + remainingLO + " lagout(s) left)", 104);
							if (!laggers.containsKey( findOpponent(name) )) { m_botAction.sendPrivateMessage( findOpponent(name), "Your opponent has returned from lagout, Go!", 104); }
							messager.setLastReturn((int)(System.currentTimeMillis() / 1000));
							warpPlayer(findOpponent(name));
						} else {
							m_botAction.sendPrivateMessage( findOpponent(name), "Your opponent has returned from lagout.");
						}
					}
				}
				messager.toggleSmallLag(false);
				m_botAction.setShip(name, messager.getShip());
			}
		}

		if (trState == 1) { handleVote( name, event.getMessage(), 9); }
		if (trState == 2) { handleVote( name, event.getMessage(), 10); }

		if ( !m_botAction.getOperatorList().isER( name ) ) { return; }

		if (event.getMessageType() == Message.PRIVATE_MESSAGE && event.getMessage().equalsIgnoreCase("!die")) {
			try { Thread.sleep(50); } catch (Exception e) {};
	   		m_botAction.die();
		}

		if (event.getMessageType() == Message.PRIVATE_MESSAGE && event.getMessage().equalsIgnoreCase("!start")) {
			if (trState == -1) { startTournament(); } else { m_botAction.sendPrivateMessage(name, "Tournament already in progress."); }
		}

		if (event.getMessageType() == Message.PRIVATE_MESSAGE && !stopped && event.getMessage().equalsIgnoreCase("!stop") && trState != -1) {
			if (trState == 0) {
				trState = -1;
				m_botAction.sendArenaMessage("TournyBot disabled");
			} else {
				stopped = true;
				m_botAction.sendPrivateMessage(name, "When the current tournament ends, no new tournament will start.");
			}
		}

		if ( !m_botAction.getOperatorList().isSmod( name ) ) { return; }

		if (event.getMessageType() == Message.PRIVATE_MESSAGE && event.getMessage().equalsIgnoreCase("!zone")) {
			if (zonerLock) { 
				zonerLock = false;
				m_botAction.sendPrivateMessage(name, "Zone messages enabled.");
			} else {
				zonerLock = true;
				m_botAction.sendPrivateMessage(name, "Zone messages disabled.");
			}
		}
	}

	public void handleEvent(PlayerLeft event) {

		String name = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();

		/* Lagout */

		if (players.containsKey( name ) && playerStillIn( name )) {

			if (trState > 3 && playersNum != 1) {

				pStats lagi = (pStats)players.get( name );

				if (lagi.getPlayerState() == 0) { lagi.toggleSmallLag(true); return; }

				lagi.incrementLagOuts();

				if (lagi.getLagOuts() > maxLagOuts) {
			
					m_botAction.sendArenaMessage( name + " has exceeded the lagout limit and was disqualified.");
					lagi.laggedOut();
					removePlayer( name, true );

				} else {

					if (!laggers.containsKey( name )) {

						laggers.put( name, new Lagger( name, laggers ) );
						Lagger l = (Lagger)laggers.get( name );
						m_botAction.scheduleTask( l, 60000 );

						m_botAction.sendPrivateMessage( findOpponent(name), "Your opponent has lagged out, and has 60 seconds to return. Total " + lagi.getLagOuts() + " lagouts.");
					}
				}

			} else if (trState < 4) {

				players.remove(name);
				playersNum--;
			}
		}

	};

	public void handleEvent(FrequencyShipChange event) {

		String name = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();

		/* Registering new players */

		if (trState < 4 && event.getShipType() != 0) {

			if (!players.containsKey( name )) {
				players.put( name, new pStats() );
			}
	
			pStats check = (pStats)players.get( name );
			int registered = check.getRegistered();

			if (registered == 0) {

				playersNum++;
				check.register();

				if (playersNum == 2 && trState == 0) {
					startTournament();
				}
			} 
		}

		/* Lagout */

		if (event.getShipType() == 0 && players.containsKey( name ) && playerStillIn( name )) {

			pStats lagi = (pStats)players.get( name );

			if (trState > 3 && playersNum != 1) {

				if (lagi.getPlayerState() == 0) { lagi.toggleSmallLag(true); return; }

				lagi.incrementLagOuts();
				if (lagi.getLagOuts() > maxLagOuts) {

					m_botAction.sendArenaMessage( name + " has exceeded the lagout limit and was disqualified.");
					lagi.laggedOut();
					removePlayer( name, true );

				} else {

					if (!laggers.containsKey( name )) {

						laggers.put( name, new Lagger( name, laggers ) );
						Lagger l = (Lagger)laggers.get( name );
						m_botAction.scheduleTask( l, 60000 );

						m_botAction.sendPrivateMessage( name, "You have 60 seconds to return to the game, send me !return to enter.");
						m_botAction.sendPrivateMessage( findOpponent(name), "Your opponent has lagged out, and has 60 seconds to return. Total " + lagi.getLagOuts() + " lagouts.");
					}
				}

			} else if (trState < 4) {

				players.remove(name);
				playersNum--;
			}
		}
	};

	public void handleEvent( PlayerEntered event ) {

		if (trState == -1) {
			m_botAction.sendPrivateMessage( event.getPlayerName(), "Welcome to Tournament! TournyBot is currently disabled. Send !help for instructions.");
		} else if (trState == 0) {
			m_botAction.sendPrivateMessage( event.getPlayerName(), "Welcome to Tournament! New tournament will begin when 2 or more players enter. Send !help for instructions.");
		} else if (trState > 0 && trState < 4) {
			m_botAction.sendPrivateMessage( event.getPlayerName(), "Welcome to Tournament! New " + ship + " tournament to " + deaths + " deaths is starting, hop in! Send !help for instructions.");
		} else {
			if (laggers.containsKey( event.getPlayerName() ) && playerStillIn( event.getPlayerName() ))	{
				m_botAction.sendPrivateMessage( event.getPlayerName(), "Send !return to get back in.");
			} else {
				m_botAction.sendPrivateMessage( event.getPlayerName(), "Welcome to Tournament! Current " + ship + " tournament to " + deaths + " deaths has " + playersNum + " players in. Send !help for instructions.");
			}
		}
	}

	public void handleEvent(PlayerPosition event) {

		/* Detects warping and warps them back */

		if (trState == 4)
		{
			String name = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();
			pStats out = (pStats)players.get(name);
			int maxx = 1024;
			int minx = 0;
			int maxy = 1024;
			int miny = 0;
			int plyrNo = out.getPlayerNro();
			if(plyrNo == 1)
			{
				minx = playerXPos(name) - 11;
				miny = playerYPos(name) - 11;
				maxx = minx + 128;
				maxy = miny + 129;
			}
			else if(plyrNo == 2)
			{
				maxx = playerXPos(name) + 11;
				maxy = playerYPos(name) + 11;
				minx = maxx - 128;
				miny = maxy - 129;
			}

			if (playerStillIn( name ) && event.getXLocation() / 16 > maxx || event.getXLocation() / 16 < minx || event.getYLocation() / 16 > maxy || event.getYLocation() / 16 < miny) {

				pStats ilWarp = (pStats)players.get( name );
				if (ilWarp.getPlayerState() == 1 && ilWarp.timeFromLastDeath() > 3 && ilWarp.timeFromLastReturn() > 3)	{
					ilWarp.incrementWarps();

					if (ilWarp.getWarps() >= maxWarps) {
						m_botAction.sendArenaMessage( name + " has exceeded the warp limit and was disqualified.");
						ilWarp.laggedOut();
						removePlayer( name, true );
					} else {
						int rWarps = maxWarps - ilWarp.getWarps();
						m_botAction.sendPrivateMessage( name, "Warping is not allowed. (" + rWarps + " more warp(s) = forfeit)");
					}
				}
				warpPlayer( name );
			}
		}
	} 

	public void handleEvent(PlayerDeath event) {

		if (trState == 4) {

			final String killerName = m_botAction.getPlayerName( event.getKillerID() );
	 	  	final String killeeName = m_botAction.getPlayerName( event.getKilleeID() );

			if (players.containsKey(killerName) && players.containsKey(killeeName)) {

				if (findOpponent(killeeName) == null) { return; }

				if (findOpponent(killeeName).equals(killerName)) {

					final pStats info = (pStats)players.get( killerName );
					final pStats info2 = (pStats)players.get( killeeName );

					/* Checks for doublekill */

					if ( info.timeFromLastDeath() < 2 ) {

						info2.removeKills();
						info.removeDeaths();
						m_botAction.sendPrivateMessage( killerName, "Double Kill, doesn't count." );
						m_botAction.sendPrivateMessage( killeeName, "Double Kill, doesn't count." );	

						return;
					}

					if (info2.getPlayerState() == 1 && info.getPlayerState() == 1) {

						info2.incrementDeaths();
						info.incrementKills();

						int vKills = info2.getGameKills();
						int vDeaths = info2.getGameDeaths();
						final int kKills = info.getGameKills();
						final int kDeaths = info.getGameDeaths();

						if (vDeaths >= deaths) {
							m_duelDelay = new TimerTask() {

								public void run() {

									if (info2.getGameDeaths() >= deaths) {
										int nuRound = info.getRound() +1;
										info.busy(0);

										if (nuRound == 7) {
											m_botAction.sendArenaMessage(killerName + " defeats " + killeeName + " [" + kKills + "-" + kDeaths + "]");
										} else {
											m_botAction.sendArenaMessage(killerName + " defeats " + killeeName + " [" + kKills + "-" + kDeaths + "] and advances to round " + nuRound + "!");
										}
										removePlayer(killeeName, false);
										advancePlayer(killerName);
									}
								}
							};
						m_botAction.scheduleTask(m_duelDelay, 2000);
						}
					
						info2.sleeping();
						info.sleeping();
						m_botAction.shipReset(killeeName);
						m_botAction.shipReset(killerName);
						warpPlayer(killeeName);
						warpPlayer(killerName);
						info2.playing();
						info.playing();

						m_duelDelay = new TimerTask() {

						   	public void run() {

								if (trState == 4 && playerStillIn(killeeName) && playerStillIn(killerName)) {

									m_botAction.shipReset(killeeName);
									m_botAction.shipReset(killerName);
									m_botAction.sendPrivateMessage( killerName, "Score: [" + killerName + " " + info.getGameKills() + "-" + info.getGameDeaths() + " " + killeeName + "]" );
									m_botAction.sendPrivateMessage( killeeName, "Score: [" + killeeName + " " + info2.getGameKills() + "-" + info2.getGameDeaths() + " " + killerName + "]" );	
									warpPlayer(killeeName);
									warpPlayer(killerName);
								}
							}
						};
						m_botAction.scheduleTask(m_duelDelay, 3000);
					}
				}
			}
		}
	}
	
	public void handleEvent(LoggedOn event) {
		m_botAction.joinArena(m_botSettings.getString("arena"));
		m_botAction.sendUnfilteredPublicMessage("?chat=alerts,uberalerts,tourny");
		players.clear();
		votes.clear();
		laggers.clear();
	}
	
   
	public void handleEvent(ArenaJoined event) {
		m_botAction.setReliableKills(1);
		m_botAction.specAll();
	}

	public void handleScore(String name, String tempPlayer) {

		String player = m_botAction.getFuzzyPlayerName(tempPlayer);

		if (players.containsKey(player) && trState == 4) {

			pStats score = (pStats)players.get( player );

			int sRound = score.getRound();

			if (!playerStillIn(player)) {
				m_botAction.sendPrivateMessage( name, player + " was eliminated in round " + sRound);
				return;
			}

			if (score.getBusy() == 0) {
				m_botAction.sendPrivateMessage( name, player + " is waiting for an opponent in round " + sRound);
			} else {
				int sKills = score.getGameKills();
				int sDeaths = score.getGameDeaths();
				String sOpponent = findOpponent(player);

				m_botAction.sendPrivateMessage( name, "Round " + sRound + ": [" + player + " " + sKills + "-" + sDeaths + " " + sOpponent + "]");
			}
		}
	}

	/* Fetches X coord for   name   from .cfg */

	public int playerXPos(String name) {

		if (!playerStillIn( name )) { return 512; }

		pStats pPos = (pStats)players.get( name );

		int boxer = pPos.getBox();
		int rounder = pPos.getRound();
		int nroer = pPos.getPlayerNro();

		if (pPos.getPlayerState() == 0) {
			String comboSafX = "box" + rounder + ":" + boxer + "out" + nroer + "x";
			String tx = m_botSettings.getString( comboSafX );
			int x = Integer.parseInt(tx);
			if(nroer == 1) x -= 6;
			else x +=6;
			return x;
		} else {
			String comboOutX = "box" + rounder + ":" + boxer + "out" + nroer + "x";
			String tx = m_botSettings.getString( comboOutX );
			int x = Integer.parseInt(tx);
			return x;
		}
	}


	/* Fetches Y coord for   name   from .cfg */

	public int playerYPos(String name) {

		if (!playerStillIn( name )) { return 512; }

		pStats pPos = (pStats)players.get( name );

		int boxer = pPos.getBox();
		int rounder = pPos.getRound();
		int nroer = pPos.getPlayerNro();

		if (pPos.getPlayerState() == 0) {
			String comboSafY = "box" + rounder + ":" + boxer + "out" + nroer + "y";
			String ty = m_botSettings.getString( comboSafY );
			int y = Integer.parseInt(ty);
			if(nroer == 1) y -= 6;
			else y += 6;
			return y;
		} else {
			String comboOutY = "box" + rounder + ":" + boxer + "out" + nroer + "y";
			String ty = m_botSettings.getString( comboOutY );
			int y = Integer.parseInt(ty);
			return y;
		}
	}

	public void startTournament() {

		trState = 1;
		m_botAction.sendArenaMessage("Vote: 1-Warbird 2-Javelin 3-Spider 4-Leviathan 5-Terrier 6-Weasel 7-Lancaster 8-Shark 9-Any ship");

		m_voteDeaths = new TimerTask() {

			public void run() {

				trState = 2;
				shipType = countVote(9);
				if (shipType == 0) { shipType = 1; }
				votes.clear();

				if (shipType == 1) { ship = "Warbird"; } else if (shipType == 2) { ship = "Javelin"; } else if (shipType == 3) { ship = "Spider"; } else if (shipType == 4) { ship = "Leviathan"; } else if (shipType == 5) { ship = "Terrier"; } else if (shipType == 6) { ship = "Weasel"; } else if (shipType == 7) { ship = "Lancaster"; } else if (shipType == 8) { ship = "Shark"; } else if (shipType == 9) { ship = "Any Ship"; }
				m_botAction.sendArenaMessage( ship + " tournament. Vote: How many deaths? (1-10)");
			};
		};

		m_announceVote = new TimerTask() {

			public void run() {

				trState = 3;
				deaths = countVote(10);
				if (deaths == 0) { deaths = 10; }
				votes.clear();
				m_botAction.sendArenaMessage( ship + " tournament to " + deaths + " death(s)! Locking in 10 seconds.");
				m_botAction.sendArenaMessage( "Rules: No warping! " + maxLagOuts + " lagouts/duel allowed. You may spec/leave arena if you don't have an opponent.");
			};
		};

		m_startGame = new TimerTask() {

			public void run() {

				trState = 4;
				m_botAction.toggleLocked();
				announcePlayers();
			};
		};

		m_botAction.scheduleTask(m_voteDeaths, 10000);
		m_botAction.scheduleTask(m_announceVote, 20000);
		m_botAction.scheduleTask(m_startGame, 30000);
	}

	public void endTournament(String name, int n) {

		if (n == 1) {

			displayScores();
			m_botAction.sendArenaMessage("GAME OVER: Winner " + name + "!", 5);
			if (!stopped) {
				m_botAction.sendChatMessage(1, "Tourny over. Next tourny starting soon.");
				m_botAction.sendChatMessage(2, "Tourny over. Next tourny starting soon.");
				m_botAction.sendChatMessage(3, "Tourny over, the winner was " + name + ". Next tourny starting soon, ?go tourny now!");
			}

			m_botAction.warpTo(name, 414, 447);
			tournyCount++;

		} else {

			m_botAction.sendArenaMessage("Not enough players, restarting.", 2);
		}
	
		even = (tournyCount%2 == 0);
		if (even && !zonerLock && !stopped) {
			m_botAction.sendZoneMessage("Next automated tournament is starting, type ?go tourny to play");
		}

		Iterator it = getPlayerList();
		while (it.hasNext()) {

			String unname = (String) it.next();
			pStats rCheck = (pStats)players.get( unname );

			if (rCheck.getRegistered() == 1) {

				rCheck.unRegister();
			}
		}

		if (stopped) { trState = -1; stopped = false; m_botAction.sendArenaMessage("TournyBot disabled"); } else { trState = 0; }

		players.clear();
		votes.clear();
		laggers.clear();
		playersNum = 0;

		m_endGame = new TimerTask() {

			public void run() {

				m_botAction.specAll();
				m_botAction.toggleLocked();
				m_botAction.sendTeamMessage("Free to enter");
			}

		};
		m_botAction.scheduleTask(m_endGame, 20000);
	}

	public void warpPlayer(String name) {

		m_botAction.warpTo(name, playerXPos(name), playerYPos(name));
	}


	/* Calculates the correct newBox and advances   name   */

	public void advancePlayer(String name) {

		pStats aCheck = (pStats)players.get( name );

		if( laggers.containsKey( name ) ) {
			((Lagger)laggers.get( name )).cancel();
			laggers.remove( name );

			aCheck.toggleSmallLag(true);
		}
	
		int newBox;

		aCheck.sleeping();
		aCheck.reset();
		aCheck.incrementRound();

		if (aCheck.getRound() == 7) {
		
			endTournament( name, 1 );
			return;
		} else {

			even = (aCheck.getBox()%2 == 0);
			if (even) {
				newBox = aCheck.getBox() / 2;
			} else {
				newBox = (aCheck.getBox() +1) / 2;
			}

			aCheck.changeBox(newBox);
			checkOpponent(name, 2);
		}
	}


	/* Tries to find an opponent with same box & round with   name   */

	public String findOpponent(String name) {

		pStats player = (pStats)players.get( name );

		int id1 = player.getPlayerID();

		Iterator it = getPlayerList();
		while (it.hasNext()) {

			String oname = (String) it.next();
			pStats opponent = (pStats)players.get( oname );

			int id2 = opponent.getPlayerID();

			if (id1 != id2 && opponent.getRound() == player.getRound() && opponent.getBox() == player.getBox()) {
				return oname;
			} 
		}
		return null;
	}

	public void removePlayer(String name, boolean forfeit) {

		pStats lCheck = (pStats)players.get( name );

		lCheck.remove();
		m_botAction.spec(name);
		m_botAction.spec(name);

		playersNum--;

		if (findOpponent( name ) != null && forfeit) {

			String vastus = findOpponent( name );
			pStats va = (pStats)players.get( vastus );
			va.busy(0);
			checkOpponent( vastus, 2 );
		}
	}


	/* Is   name   still in the tournament? */

	public boolean playerStillIn(String name) {

		pStats stillIn = (pStats)players.get( name );
		if (stillIn.getRegistered() == 1 && stillIn.getPlayerState() != 2) {
			return true;
		} else {
			return false;
		}
	}


	/* Generates the first round matchups and announces them. */
		
	public void announcePlayers() {

		int rood;
		int maxBox;

		if (playersNum <= 1) {
			endTournament("hihi", 0);
			return;
		} else if (playersNum == 2) {
			rood = 6;
			maxBox = 1;
			m_botAction.sendArenaMessage("Registrations locked! Skipping first 5 rounds due to lack of players.", 2);
		} else if (playersNum <= 4 && playersNum >= 3) {
			rood = 5;
			maxBox = 2;
			m_botAction.sendArenaMessage("Registrations locked! Skipping first 4 rounds due to lack of players.", 2);
		} else if (playersNum <= 8 && playersNum >= 5) {
			rood = 4;
			maxBox = 4;
			m_botAction.sendArenaMessage("Registrations locked! Skipping first 3 rounds due to lack of players.", 2);
		} else if (playersNum <= 16 && playersNum >= 9) {
			rood = 3;
			maxBox = 8;
			m_botAction.sendArenaMessage("Registrations locked! Skipping first 2 rounds due to lack of players.", 2);
		} else if (playersNum <= 32 && playersNum >= 17) {
			rood = 2;
			maxBox = 16;
			m_botAction.sendArenaMessage("Registrations locked! Skipping first round due to lack of players.", 2);
		} else {
			rood = 1;
			maxBox = 32;
			m_botAction.sendArenaMessage("Registrations locked!", 2);
		}

		int box = 1;
		int pNro = 1;
		int temp = 0;

		int[] seedings = new int[playersNum];

		Iterator it = getPlayerList();
		while (it.hasNext()) {

			String name = (String) it.next();
			pStats rand = (pStats)players.get( name );

			double tRandNum = 10000 * Math.random();
			int randNum = (int)tRandNum;

			m_botAction.setShip(name, shipType);
			rand.setRandNum(randNum);
			seedings[temp] = randNum;
			temp++;
		}

		Arrays.sort(seedings);

		for (int c = 0; c < seedings.length; c++) {
		
			Iterator it2 = getPlayerList();
			while (it2.hasNext()) {

				String name = (String) it2.next();
				pStats rCheck = (pStats)players.get( name );

				if (rCheck.getRegistered() == 1 && seedings[c] == rCheck.getRandNum()) {

					rCheck.changeRound(rood);
					rCheck.changeNro(pNro);
					rCheck.changeBox(box);
					box++;

					if (box > maxBox) {
						box = 1;
						pNro = 2;
					}

					/* if (pNro == 1) {
						rCheck.changeBox(box);
						rCheck.changeNro(pNro);
						pNro++;

					} else if (pNro == 2) {
						rCheck.changeBox(box);
						rCheck.changeNro(pNro);
						pNro = 1;
						box++;
					} */

					m_botAction.scoreReset(name);
					warpPlayer(name);
				}
			}
		}

		for (int c2 = 0; c2 < seedings.length; c2++) {

			Iterator it3 = getPlayerList();
			while (it3.hasNext()) {

				String name = (String) it3.next();
				pStats rCheck2 = (pStats)players.get( name );

				if (seedings[c2] == rCheck2.getRandNum()) { 
					checkOpponent(name, 1); 
					if (shipType == 9) { 
						rCheck2.setShip(m_botAction.getPlayer(name).getShipType());
					} else {
						rCheck2.setShip(shipType);
					}
				}
			}
		}
	}


	/* Starts a duel between   p1   and   p2   */

	public void startDuel(final String p1, final String p2, int round, int box) {

		m_botAction.sendArenaMessage( "Round " + round + ": " + p1 + " vs. " + p2 + " starting in 10 seconds");

		final pStats playa1 = (pStats)players.get( p1 );
		playa1.busy(1);
		final pStats playa2 = (pStats)players.get( p2 );
		playa2.busy(1);

		if (playa1.getSmallLag()) {
			m_botAction.sendSmartPrivateMessage( p1, "Your duel vs. " + p2 + " is starting. You have 10 seconds to !return before you get a lagout.");
		}
		if (playa2.getSmallLag()) {
			m_botAction.sendSmartPrivateMessage( p2, "Your duel vs. " + p1 + " is starting. You have 10 seconds to !return before you get a lagout.");
		}
	
		m_duelStart = new TimerTask() {

			public void run() {

				if (trState != 4) { return; }
				if (playerStillIn(p1) && playerStillIn(p2)) {

					playa1.playing();
					playa2.playing();

					if (!playa1.getSmallLag() && !playa2.getSmallLag())	{
				
						m_botAction.sendPrivateMessage( p1, "Go!", 104);
	   					m_botAction.sendPrivateMessage( p2, "Go!", 104);

						m_botAction.shipReset( p1 );
						m_botAction.scoreReset( p1 );
						warpPlayer(p1);

						m_botAction.shipReset( p2 );
						m_botAction.scoreReset( p2 );
						warpPlayer(p2);

					} else {

						if (playa1.getSmallLag() && !laggers.containsKey( p1 )) {

							playa1.incrementLagOuts();

							laggers.put( p1, new Lagger( p1, laggers ) );
							Lagger l = (Lagger)laggers.get( p1 );
							m_botAction.scheduleTask( l, 60000 );

							m_botAction.sendSmartPrivateMessage( p1, "Your duel vs. " + p2 + " started without you. (= +1 lagout [Total: " + playa1.getLagOuts() + "] You have 60 seconds to !return)");
							m_botAction.sendPrivateMessage( p2, "Your opponent (" + p1 + ") failed to show up, and has 60 seconds to return. Total " + playa1.getLagOuts() + " lagouts.");
						}

						if (playa2.getSmallLag() && !laggers.containsKey( p2 )) {

							playa2.incrementLagOuts();

							laggers.put( p2, new Lagger( p2, laggers ) );
							Lagger l = (Lagger)laggers.get( p2 );
							m_botAction.scheduleTask( l, 60000 );

							m_botAction.sendSmartPrivateMessage( p2, "Your duel vs. " + p1 + " started without you. (= +1 lagout [Total: " + playa2.getLagOuts() + "] You have 60 seconds to !return)");
							m_botAction.sendPrivateMessage( p1, "Your opponent (" + p2 + ") failed to show up, and has 60 seconds to return. Total " + playa2.getLagOuts() + " lagouts.");
						}
					}
				}
			}
		};
		m_botAction.scheduleTask(m_duelStart, 10000);
	}


	/* Tries to find an opponent for   name   . If it is round 1,   type   is 1 and thus it won't mess up
	   the player numbers generated in announcePlayers */

	public void checkOpponent(final String name, int type) {

		pStats roundBox = (pStats)players.get( name );

		final int round = roundBox.getRound();
		int box = roundBox.getBox();
		int id1 = roundBox.getPlayerID();

		if (roundBox.getBusy() != 0) { return; }

		Iterator it = getPlayerList();
		while (it.hasNext()) {

			String oname = (String) it.next();
			pStats oCheck = (pStats)players.get(oname);

			int id2 = oCheck.getPlayerID();

			if (id1 != id2 && oCheck.getRegistered() == 1 && oCheck.getPlayerState() != 2) {

				if (round == oCheck.getRound() && box == oCheck.getBox() && oCheck.getPlayerState() == 0) {

					if (type != 1) { roundBox.changeNro(2); warpPlayer(name); }
					startDuel(name, oname, round, box);
					return;

				/* If no opponent is found in the same box & round, it goes through all the possible boxes & rounds */

				} else {

					int box1;
					int lBox;
	
					for (int aRound = round; aRound >= 2; aRound--) {
					
						if (aRound == 2)
						{

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
									roundBox.changeNro(1);
									warpPlayer(name);
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
									roundBox.changeNro(1);
									warpPlayer(name);
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
									roundBox.changeNro(1);
									warpPlayer(name);
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
									roundBox.changeNro(1);
									warpPlayer(name);
									return;
								}
							}

						} else if (aRound == 6)	{

							int newRound = 5;
						
							box1 = box *2;
							lBox = box1 -1;
						
							for (int fBox = box1; fBox >= lBox; fBox--) {
							
								if (newRound == oCheck.getRound() && fBox == oCheck.getBox()) {
									roundBox.changeNro(1);
									warpPlayer(name);
									return;
								}
							}
						}
					}
				}
			}
		}

		/* If no possible opponent is found,   name   wins by default. */

		roundBox.changeNro(1);
		warpPlayer(name);

		if (playerStillIn(name)) {

			int xRound = round +1;
			if (xRound != 7) {
				m_botAction.sendArenaMessage( name + " wins by default and advances to round " + xRound + "!");
			}

			advancePlayer( name );
		}
	}


	/* Did any player finish the tournament in round   rundi   ? */

	public boolean emptyRound(int rundi) {

		Iterator i = getPlayerList();
		while (i.hasNext()) {

			String name = (String) i.next();
			pStats player = (pStats)players.get( name );

			if (player.getRound() == rundi) { return true; }
		}
		return false;
	}

	public void displayScores() {

		String out;

		m_botAction.sendArenaMessage( "+" + Tools.formatString("", 59, "-") + "+");
		m_botAction.sendArenaMessage( "| " + Tools.formatString("" + ship + " tournament to " + deaths + " death(s)", 37) + "Kills   Deaths   DQ  |");
		m_botAction.sendArenaMessage( "+" + Tools.formatString("", 59, "-") + "+");

		for (int r = 7; r >= 3; r--) {

			if (emptyRound( r )) {

				int eka = 1;
				Iterator i = getPlayerList();
				while (i.hasNext()) {

					String name = (String) i.next();
					pStats player = (pStats)players.get( name );

					if (player.getRound() == r) {

						if (eka == 1) {

							if (r == 7) { out = Tools.formatString("  " + "Winner", 14); }
							else if (r == 6) { out = Tools.formatString("  " + "Runner up", 14); }
							else { out = Tools.formatString("  " + "Round " + r, 14); }

						} else {
							out = Tools.formatString("  " + " ", 14);
						}
						out += Tools.formatString("" + name, 23);
						out += Tools.formatString("" + player.getTotalKills(), 8);
						out += Tools.formatString("" + player.getTotalDeaths(), 9);
						out += Tools.formatString("" + player.getLagOut(), 3);
						m_botAction.sendArenaMessage("| " + out + " |");
						eka++;
					}
				}
			}
		}

		m_botAction.sendArenaMessage( "+" + Tools.formatString("", 59, "-") + "+");
	}

	public Iterator getPlayerList() {
		return players.keySet().iterator();
	}


	/* Handles the votes.   range   is the # of options in the vote. */

	public void handleVote( String name, String message, int range ) {

		try{
			if( !Tools.isAllDigits( message )) {
				return;
			}

			int vote;
			try{
				vote = Integer.parseInt( message );
			} catch( NumberFormatException nfe ) {

				return;
			}
				
			if( !(vote > 0 && vote <= range )) {
				return;
			}
				
			votes.put( name, new Integer( vote ));
				
		} catch( Exception e ) {}
	}


	/* Declares the winner in voting.   range   is the # of options in the vote. */

	public int countVote( int range ) {

		int winner = 0;
		int[] counters = new int[range+1];
		Iterator iterator = votes.values().iterator();

		while( iterator.hasNext() ) {
			counters[((Integer)iterator.next()).intValue()]++;
		}

		for( int i = 1; i < counters.length; i++ ) {

			if (winner < counters[i]) { winner = i; }
		}
		return winner;
	}
	
	void setupZoner()
	{
		m_zoner = new TimerTask()
		{
			public void run()
			{
				zoner = true;
			}
		};
	}

	class Lagger extends TimerTask {

		String player;
		HashMap laggers;
	
		public Lagger( String name, HashMap l ) {
			player = name;
			laggers = l;
		}
	
		public void run() {

			pStats lagi = (pStats)players.get( player );

			m_botAction.sendArenaMessage( player + " failed to return from his lagout in time and was disqualified.");
			lagi.laggedOut();
			removePlayer(player, true);
		}
	}
}
