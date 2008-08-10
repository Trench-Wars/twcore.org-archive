package twcore.bots.twl;

/* MatchGame.java
 *
 * Created on August 20, 2002, 8:46 PM
 */

/**
 *
 * @author  Administrator
 */

import java.sql.Connection;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FlagReward;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.ScoreReset;
import twcore.core.events.WeaponFired;
import twcore.core.util.Tools;

public class MatchGame
{

	Connection m_connection;
	BotAction m_botAction;

	BotSettings m_rules;

	MatchLogger m_logger;
	String dbConn = "website";
	String m_fcRuleFile;
	String m_fcTeam1Name;
	String m_fcTeam2Name;
	String m_fcArena;
	int m_fnTeam1Score = 0;
	int m_fnTeam2Score = 0;
	int m_fnMatchTypeID = 0;
	int m_fnMatchID = 0;
	int m_fnTeam1ID = 0;
	int m_fnTeam2ID = 0;

	int m_gameState = 0;
	int playersNum = 0;

	static int KILL_ME_PLEASE = 10;

	boolean m_gameStored = false;
	boolean announced = false;

	LinkedList<MatchRound> m_rounds;
	MatchRound m_curRound;

	/** Creates a new instance of MatchGame */
	public MatchGame(String ruleFile, String fcTeam1Name, String fcTeam2Name, int players, int m_id, BotAction botAction)
	{
		m_botAction = botAction;
		m_fcRuleFile = ruleFile;

		m_fcTeam1Name = fcTeam1Name;
		m_fcTeam2Name = fcTeam2Name;
		playersNum = players;
		m_rules = new BotSettings(m_fcRuleFile);
		m_logger = new MatchLogger(m_botAction);

		m_fnMatchTypeID = m_rules.getInt("matchtype");

		if ((m_rules.getInt("rosterjoined") == 1) || (m_rules.getInt("storegame") == 1))
		{
			m_fnTeam1ID = getTeamID(m_fcTeam1Name);
			m_fnTeam2ID = getTeamID(m_fcTeam2Name);
			if (m_rules.getInt("rosterjoined") == 1)
			{
				if ((m_fnTeam1ID == 0) || (m_fnTeam2ID == 0))
				{
					return;
				};
			};
		};

		if ((m_rules.getInt("storegame") == 1) && (m_rules.getInt("matchtype") != 0))
		{
			if (m_fnMatchTypeID > 0 && m_fnMatchTypeID < 4) {
				m_fnMatchID = m_id;
			} else {
				createGameRecord();
			}
			if (m_rules.getInt("loggame") == 1)
			{
				m_logger.activate(m_fnMatchID);
			};
		};

		/*
		m_fcArena = m_rules.getString("arena");
		if (m_fcArena == null) m_fcArena = "twd";
		m_botAction.joinArena(m_fcArena);
		 */

		TimerTask startup = new TimerTask()
		{
			public void run()
			{
				setupGame();
			};
		};
		m_botAction.scheduleTask(startup, 1000);

	}

	public int getTeamID(String fcTeamName)
	{
		try
		{
			ResultSet rs =
				m_botAction.SQLQuery(
					dbConn,
					"SELECT fnTeamID FROM tblTeam WHERE fcTeamName = '" + Tools.addSlashesToString(fcTeamName) + "' AND (fdDeleted IS NULL or fdDeleted = 0)");
			if (rs.next())
			{
				return rs.getInt("fnTeamID");
			}
			else
				return 0;
		}
		catch (Exception e)
		{
			System.out.println(e.getMessage());
			return 0;
		}
	};

	public void setupGame()
	{
		String winBy = m_rules.getString("winby");
		String title = m_rules.getString("name");

		if (winBy != null && winBy.equals("race"))
			title = title + " to " + m_rules.getInt("points");

		m_logger.sendArenaMessage(title + ": " + m_fcTeam1Name + " vs. " + m_fcTeam2Name);
		m_gameState = 1;
		m_rounds = new LinkedList<MatchRound>();
		m_curRound = new MatchRound(1, m_fcTeam1Name, m_fcTeam2Name, this);
		m_rounds.add(m_curRound);
	};

	public int getBotNumber()
	{
		try
		{
			return 1;
		}
		catch (Exception e)
		{
			return 0;
		}
	};

	// creates a Game record in the database
	public void createGameRecord()
	{
		try
		{
			String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());

			String[] fields = { "fnMatchTypeID", "fnMatchStateID", "fnTeam1ID", "fcTeam1Name", "fnTeam2ID", "fcTeam2Name", "ftTimeStarted" };
			String[] values =
				{
					m_rules.getString("matchtype"),
					"2",
					Integer.toString(m_fnTeam1ID),
					Tools.addSlashesToString(m_fcTeam1Name),
					Integer.toString(m_fnTeam2ID),
					Tools.addSlashesToString(m_fcTeam2Name),
					time };
			m_botAction.SQLInsertInto(dbConn, "tblMatch", fields, values);

			//            ResultSet s = m_botAction.SQLQuery(dbConn, "select fnMatchID from tblMatch where ftTimeStarted = '"+time+"' and fcTeam1Name = '"+Tools.addSlashesToString(m_fcTeam1Name)+"' and fcTeam2Name = '"+Tools.addSlashesToString(m_fcTeam2Name)+"'");
			ResultSet s = m_botAction.SQLQuery(dbConn, "select MAX(fnMatchID) as fnMatchID from tblMatch");
			if (s.next())
			{
				m_fnMatchID = s.getInt("fnMatchID");
			};
		}
		catch (Exception e)
		{
			System.out.println("unable to insert game record: " + e.getMessage());
		};
	};

	// store game results
	public void storeGameResult()
	{
		try
		{
			String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
			m_botAction.SQLQuery(
				dbConn,
				"UPDATE tblMatch SET fnMatchStateID = 3, fnTeam1Score="
					+ m_fnTeam1Score
					+ ", fnTeam2Score="
					+ m_fnTeam2Score
					+ ", ftTimeEnded='"
					+ time
					+ "' where fnMatchID = "
					+ m_fnMatchID);
			m_gameStored = true;
		}
		catch (Exception e)
		{
		};
	};

	/**
	 * @param event WeaponFired event
	 */
	public void handleEvent(WeaponFired event)
	{
		//m_logger.logEvent(event); too much extra overhead for database
		if (m_curRound != null) {
			m_curRound.handleEvent(event);
		}
	}

	public void handleEvent(ArenaJoined event)
	{
		m_logger.logEvent(event);
	};

	public void handleEvent(FlagClaimed event)
	{
		m_logger.logEvent(event);
		if (m_curRound != null)
		{
			m_curRound.handleEvent(event);
		}
	};

	public void handleEvent(FrequencyChange event)
	{
		m_logger.logEvent(event);
	};

	public void handleEvent(FrequencyShipChange event)
	{
		m_logger.logEvent(event);
		if (m_curRound != null)
		{
			m_curRound.handleEvent(event);
		};
	};

	public void handleEvent(FlagReward event)
	{
		m_logger.logEvent(event);
		if (m_curRound != null)
		{
			m_curRound.handleEvent(event);
		};
	};

	public void handleEvent(Message event)
	{
		m_logger.logEvent(event);
		if (m_curRound != null)
		{
			m_curRound.handleEvent(event);
		};
	};

	public void handleEvent(PlayerDeath event)
	{
		m_logger.logEvent(event);
		if (m_curRound != null)
		{
			m_curRound.handleEvent(event);
		};
	};

	public void handleEvent(PlayerEntered event)
	{
		m_botAction.sendPrivateMessage(event.getPlayerID(), shortStatus());
	        if( m_curRound != null ){
  	                     m_curRound.handleEvent( event );
                }
        }

	public void handleEvent(PlayerLeft event)
	{
		if (m_curRound != null)
		{
			m_curRound.handleEvent(event);
		};
	};

	public void handleEvent(PlayerPosition event)
	{
		if (m_curRound != null)
		{
			m_curRound.handleEvent(event);
		};
	};

	public void handleEvent(ScoreReset event)
	{
		m_logger.logEvent(event);
	};

	public ArrayList<String> getHelpMessages(String name, boolean isStaff)
	{
		ArrayList<String> help = new ArrayList<String>();

		help.add("!status                                  - Shows the current state of the entire game");

		if (isStaff && m_fnMatchTypeID > 0 && m_fnMatchTypeID < 4)
			help.add("!zone                                    - Announce the game in *zone");

		if (m_curRound != null)
		{
			help.addAll(m_curRound.getHelpMessages(name, isStaff));
		};

		return help;
	};

	public void parseCommand(String name, String command, String[] parameters, boolean isStaff)
	{
		if (command.equals("!status"))
			command_status(name, parameters);

		if (isStaff) {
			if (command.equals("!zone"))
				command_zone(name, parameters);
		}

		if (m_curRound != null)
		{
			m_curRound.parseCommand(name, command, parameters, isStaff);
		};
	};

	public void command_status(String name, String[] parameters)
	{
		m_logger.sendPrivateMessage(name, Tools.centerString("   " + m_rules.getString("name") + "   ", 65, '-'));
		m_logger.sendPrivateMessage(name, "Teams    :" + Tools.centerString(m_fcTeam1Name, 25) + " vs. " + Tools.centerString(m_fcTeam2Name, 25));

		if (m_rules.getInt("maxrounds") <= 1)
		{
			if (m_curRound != null)
				if ((m_curRound.m_fnRoundState == 3) || (m_curRound.m_fnRoundState == 4))
					if (m_rules.getString("winby").equals("timerace"))
					{
						String team1leadingZero = "";
						String team2leadingZero = "";

						if (m_curRound.m_team1.getTeamScore() % 60 < 10)
							team1leadingZero = "0";
						if (m_curRound.m_team2.getTeamScore() % 60 < 10)
							team2leadingZero = "0";

						m_logger.sendPrivateMessage(
							name,
							"                   "
								+ m_curRound.m_team1.getTeamScore() / 60
								+ ":"
								+ team1leadingZero
								+ m_curRound.m_team1.getTeamScore() % 60
								+ "             -            "
								+ m_curRound.m_team2.getTeamScore() / 60
								+ ":"
								+ team2leadingZero
								+ m_curRound.m_team2.getTeamScore() % 60);
					}
					else
						m_logger.sendPrivateMessage(
							name,
							"          "
								+ Tools.centerString(Integer.toString(m_curRound.m_team1.getTeamScore()), 25)
								+ "  -  "
								+ Tools.centerString(Integer.toString(m_curRound.m_team2.getTeamScore()), 25));
		}
		else
		{
			MatchRound z;
			if (m_rounds == null) { return; }
			ListIterator<MatchRound> i = m_rounds.listIterator();

			while (i.hasNext())
			{
				z = i.next();
				if ((z.m_fnRoundState == 3) || (z.m_fnRoundState == 4))
					m_logger.sendPrivateMessage(
						name,
						"Round "
							+ Tools.formatString(Integer.toString(z.m_fnRoundNumber), 2)
							+ " :"
							+ Tools.centerString(Integer.toString(z.m_team1.getTeamScore()), 25)
							+ "  -  "
							+ Tools.centerString(Integer.toString(z.m_team2.getTeamScore()), 25));
			}
		}

		// 0 - none, 1 - arranging lineup, 2 - starting, 3 - playing, 4 - finished

		String extra = getRoundStateSummary();
		if (extra != null)
			m_logger.sendPrivateMessage(name, "- " + extra);
	}

	public void command_zone(String name, String[] parameters) {

		if (!announced) {
			if (m_rules.getInt("matchtype") > 0 && m_rules.getInt("matchtype") < 4) {
				announced = true;
				m_botAction.sendZoneMessage("TWL Season 8: [" + m_rules.getString("name") + "] " + m_fcTeam1Name + " vs. " + m_fcTeam2Name + " Type ?go " + m_botAction.getArenaName());
			} else {
				m_botAction.sendPrivateMessage(name, "Only TWL games may be !zone'd");
			}
		} else {
			m_botAction.sendPrivateMessage(name, "A game may be !zone'd only once");
		}
	}

	public String getRoundStateSummary()
	{
		String append = null;
		if (m_curRound != null)
		{
			if (m_rules.getInt("maxrounds") == 1)
				append = "We are currently ";
			else
				append = "We are currently in round " + m_curRound.m_fnRoundNumber + ": ";
			switch (m_curRound.m_fnRoundState)
			{
				case 1 :
					append = append + "arranging lineups";
					break;
				case 2 :
					append = append + "starting the game";
					break;
				case 3 :
					long minutesPlayed = (System.currentTimeMillis() - m_curRound.m_timeStartedms) / 60000;
					append = append + "playing, " + minutesPlayed + " minutes played";
					break;
				case 4 :
					append = append + "ending the game";
					break;
			};
		};
		return append;
	}

	public String shortStatus()
	{
		String answer;

		answer = "Welcome to " + m_rules.getString("name") + ", Teams: " + m_fcTeam1Name + " vs. " + m_fcTeam2Name + ".";

		String extra = getRoundStateSummary();
		if (extra != null)
		{
			answer = answer + " " + extra + ".";
			if ((m_curRound.m_fnRoundState == 3) || (m_curRound.m_fnRoundState == 4))
			{
				String winby = m_rules.getString("winby");

				if (winby.equals("timerace"))
				{
					String team1leadingZero = "";
					String team2leadingZero = "";

					if (m_curRound.m_team1.getTeamScore() % 60 < 10)
						team1leadingZero = "0";
					if (m_curRound.m_team2.getTeamScore() % 60 < 10)
						team2leadingZero = "0";

					answer =
						answer
							+ " Score: "
							+ (m_curRound.m_team1.getTeamScore() / 60)
							+ ":"
							+ team1leadingZero
							+ m_curRound.m_team1.getTeamScore() % 60
							+ " - "
							+ (m_curRound.m_team2.getTeamScore() / 60)
							+ ":"
							+ team2leadingZero
							+ m_curRound.m_team2.getTeamScore() % 60;
				}
				else
					answer = answer + " Score: " + m_curRound.m_team1.getTeamScore() + " - " + m_curRound.m_team2.getTeamScore();
			}
		}

		return answer;
	}

	public void reportEndOfRound(boolean m_fbAffectsEntireGame)
	{
		// remove/disable any possible timers
		m_curRound.cancel();

		int rounds = m_rules.getInt("rounds");
		if (m_curRound.m_fnTeam1Score > m_curRound.m_fnTeam2Score)
			m_fnTeam1Score++;
		else if (m_curRound.m_fnTeam2Score > m_curRound.m_fnTeam1Score)
			m_fnTeam2Score++;

		if (m_fbAffectsEntireGame)
		{
			m_fnTeam1Score = 0;
			m_fnTeam2Score = 0;
			if (m_curRound.m_fnTeam1Score > m_curRound.m_fnTeam2Score)
				m_fnTeam1Score = (rounds + 1) / 2;
			if (m_curRound.m_fnTeam2Score > m_curRound.m_fnTeam1Score)
				m_fnTeam2Score = (rounds + 1) / 2;
		};

		if ((m_fnTeam1Score == (rounds + 1) / 2) || (m_fnTeam2Score == (rounds + 1) / 2))
		{
			if ((m_curRound.m_fnRoundNumber > 1) || (rounds > 1))
			{
				// Announce winner
				m_logger.sendArenaMessage(" ------- GAME OVER ------- ", 5);
				m_logger.sendArenaMessage(m_fcTeam1Name + " vs. " + m_fcTeam2Name + ": " + m_fnTeam1Score + " - " + m_fnTeam2Score);
				if (m_fnTeam1Score > m_fnTeam2Score)
				{
					m_logger.sendArenaMessage(m_fcTeam1Name + " wins this game!");
				}
				else if (m_fnTeam2Score > m_fnTeam1Score)
				{
					m_logger.sendArenaMessage(m_fcTeam2Name + " wins this game!");
				}
				else
					m_logger.sendArenaMessage("Draw. The game is declared void");
			};
			if ((m_rules.getInt("storegame") == 1) && (m_fnTeam1Score != m_fnTeam2Score))
				storeGameResult();
			m_curRound.cancel();
			m_gameState = KILL_ME_PLEASE;
		}
		else if ((m_curRound.m_fnRoundNumber < m_rules.getInt("maxrounds")) && (!m_fbAffectsEntireGame))
		{
			// announce current standing
			m_logger.sendArenaMessage("Current game standing of " + m_fcTeam1Name + " vs. " + m_fcTeam2Name + ": " + m_fnTeam1Score + " - " + m_fnTeam2Score);
			m_logger.sendArenaMessage("Prepare for round " + (m_curRound.m_fnRoundNumber + 1), 2);
			// start new round

			int rn = m_curRound.m_fnRoundNumber + 1;
			String t1 = m_curRound.m_team1.getTeamName();
			String t2 = m_curRound.m_team2.getTeamName();
			m_curRound.cancel();

			MatchRound newRound = new MatchRound(rn, t1, t2, this);

			m_curRound = newRound;
			m_rounds.add(newRound);
		}
		else if (!m_fbAffectsEntireGame)
		{
			m_botAction.sendArenaMessage("Due to the amount of draws, this game will be declared void", 2);
			m_curRound.cancel();
			m_gameState = KILL_ME_PLEASE;
		}
		else
		{
			m_curRound.cancel();
			m_gameState = KILL_ME_PLEASE;
		};
	};

	public void reportEndOfRound()
	{
		reportEndOfRound(false);
	};

	public int getGameState()
	{
		return m_gameState;
	};

	public int getPlayersNum()
	{
		return playersNum;
	};

	public void setPlayersNum(int n)
	{
		playersNum = n;
	};

	public void cancel()
	{
		if (m_curRound != null)
			m_curRound.cancel();

		if ((m_rules.getInt("storegame") == 1) && (!m_gameStored) && (m_fnMatchID != 0))
		{
			try
			{
				String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
				m_botAction.SQLQuery(dbConn, "UPDATE tblMatch SET ftTimeEnded = '" + time + "', fnMatchStateID=5 WHERE fnMatchID = " + m_fnMatchID);
			}
			catch (Exception e)
			{
			};
		};
	};
}