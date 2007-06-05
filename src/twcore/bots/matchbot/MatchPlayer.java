package twcore.bots.matchbot;

/*
 * MatchPlayer.java
 *
 * Created on August 19, 2002, 8:37 PM
 */

/**        m_logger = m_team.m_logger;
 *
 * @author  Administrator
 */

import java.sql.Connection;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.events.Message;
import twcore.core.game.Player;
import twcore.core.stats.DBPlayerData;
import twcore.core.stats.Statistics;
import twcore.core.util.Tools;

public class MatchPlayer implements Comparable
{
	/** This class holds 2 connections: 1 to the SS game, and 1 to the DB.
	 *  The SS connection is dynamic though, so it will have to refresh every
	 *  PlayerLeft and PlayerEntered event.
	 */
	boolean useDatabase;
	private PlayerLagInfo playerLagInfo;
	private LagRequestTask lagRequestTask;
	private int maxCurrPing;
	private double maxPacketLoss;
	private double maxSlowPackets;
	private double maxStandardDeviation;
	private int maxNumSpikes;

	private TotalStatistics m_statTracker;
	Connection m_connection;
	BotAction m_botAction;
	Player m_player;
	BotSettings m_rules;

	String dbConn = "local";

	MatchTeam m_team;
	MatchLogger m_logger;

	DBPlayerData m_dbPlayer;

	String m_fcPlayerName;

	// 0 - regular
	// 1 - captain
	// 2 - assistant
	int m_fnRank = 0;

	long m_fnLaggedTime = 0;
	long m_fnOutOfBorderTime = 0;
	long m_fnLastLagCheck = 0;
	boolean m_fbHasHalfBorderWarning = false;

	// regular game stats
	int m_deafaultShip = 1;
	int m_fnSpecAt = 10;
	int m_fnFrequency = 0;
	int m_fnLagouts = 0;
	/* playerstate: 0 - Not In Game (0 deaths)
	                1 - In Game
	                2 - Substituted
	                3 - Lagged (m_fnSpecAt deaths)
	                4 - Out (m_fnSpecAt deaths)
	 */
	int m_fnPlayerState = 0;
	int m_fnMaxLagouts;

	boolean m_aboutToBeSubbed = false;
	boolean m_fbSubstituter = false;
	boolean m_switchedShip = false;
	boolean m_lagByBot = false;

	//constants
	static final int NOT_IN_GAME = 0;
	static final int IN_GAME = 1;
	static final int SUBSTITUTED = 2;
	static final int LAGGED = 3;
	static final int OUT = 4;

	/** Creates a new instance of MatchPlayer */
	public MatchPlayer(String fcPlayerName, MatchTeam Matchteam)
	{
		useDatabase = false;
		m_team = Matchteam;
		m_botAction = m_team.m_botAction;
		m_rules = m_team.m_rules;
		m_logger = m_team.m_logger;
		m_fcPlayerName = fcPlayerName;
		m_fnMaxLagouts = m_rules.getInt("lagouts");
		m_fnSpecAt = m_rules.getInt("deaths");
		m_player = m_botAction.getPlayer(m_fcPlayerName);
		m_fnFrequency = m_player.getFrequency();
		m_fnPlayerState = 0;
		playerLagInfo = new PlayerLagInfo(m_botAction, fcPlayerName, m_rules.getInt("spikesize"));
		playerLagInfo.updateLag();
		maxCurrPing = m_rules.getInt("maxcurrping");
		maxPacketLoss = m_rules.getDouble("maxploss");
		maxSlowPackets = m_rules.getDouble("maxslowpackets");
		maxStandardDeviation = m_rules.getDouble("maxstandarddeviation");
		maxNumSpikes = m_rules.getInt("maxnumspikes");

		m_statTracker = new TotalStatistics();

		if ((m_rules.getInt("storegame") != 0) || (m_rules.getInt("rosterjoined") != 0))
			m_dbPlayer = new DBPlayerData(m_botAction, dbConn, m_fcPlayerName);

		m_logger.scoreReset(m_fcPlayerName);
	}

	/**
	 * @author FoN
	 *
	 * @param anotherPlayer Another matchplayer class from which it will compare points for MVP
	 * @exception throws exception if wrong class is passed
	 */
	public int compareTo(Object anotherPlayer) throws ClassCastException
	{
		if (!(anotherPlayer instanceof MatchPlayer))
			throw new ClassCastException("A MatchPlayer object expected.");

		//this has to be done in reverse order so it can be sorted in decending order
		return  ((MatchPlayer) anotherPlayer).getPoints() - this.getPoints();
	}

	/**
	 *
	 * This function stores all the values in the database at the end of the game
	 * It now also implements storing of individual ship database stats.
	 *
	 * @param fnMatchRoundID The match round ID that is being played
	 * @param fnTeam The team the player belongs to
	 */
	public void storePlayerResult(int fnMatchRoundID, int fnTeam)
	{
		try
		{
			int substituted = 0;
			if (m_fnPlayerState == 2)
				substituted = 1;

			//first put stats into table: tblMatchRoundUser
			String[] fields =
				{
					"fnMatchRoundID",
					"fnUserID",
					"fcUserName",
					"fnTeam",
					"fnShipTypeID",
					"fnScore",
					"fnWins",
					"fnLosses",
					"fnLagout",
					"fnSubstituted" };

			String[] values =
				{
					Integer.toString(fnMatchRoundID),
					Integer.toString(m_dbPlayer.getUserID()),
					Tools.addSlashesToString(m_fcPlayerName),
					Integer.toString(fnTeam),
					Integer.toString(m_statTracker.getShipType()),
					Integer.toString(m_statTracker.getTotalStatistic(Statistics.SCORE)),
					Integer.toString(m_statTracker.getTotalStatistic(Statistics.TOTAL_KILLS)),
					Integer.toString(m_statTracker.getTotalStatistic(Statistics.DEATHS)),
					Integer.toString(m_fnLagouts),
					Integer.toString(substituted)};

			m_botAction.SQLInsertInto(dbConn, "tblMatchRoundUser", fields, values);

			//get fnMatchRoundUserID
			int fnMatchRoundUserID = 0;

			try
			{
				ResultSet qryMatchRoundUserID = m_botAction.SQLQuery(dbConn, "SELECT MAX(fnMatchRoundUserID) as fnMatchRoundUserID " + "FROM tblMatchRoundUser");

				if (qryMatchRoundUserID.next())
				{
					fnMatchRoundUserID = qryMatchRoundUserID.getInt("fnMatchRoundUserID");
				}
                                m_botAction.SQLClose( qryMatchRoundUserID );
			}
			catch (Exception e)
			{
				Tools.printStackTrace(e);
			}

			//store for each ship
			java.util.Date m_ftTimeStarted;
			java.util.Date m_ftTimeEnded;
			MatchPlayerShip MPS;
			ListIterator i = m_statTracker.m_ships.listIterator();
			String started, ended;

			while (i.hasNext())
			{
				MPS = (MatchPlayerShip) i.next();
				m_ftTimeStarted = MPS.getTimeStarted();
				m_ftTimeEnded = MPS.getTimeEnded();

				if (m_ftTimeStarted == null)
					m_ftTimeStarted = new java.util.Date();
				if (m_ftTimeEnded == null)
					m_ftTimeEnded = new java.util.Date();

				started = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(m_ftTimeStarted);
				ended = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(m_ftTimeEnded);

				String[] shipFields =
					{
						"fnMatchRoundUserID",
						"fnShipTypeID",
						"fnScore",
						"fnDeaths",
						"fnWarbirdKill",
						"fnJavelinKill",
						"fnSpiderKill",
						"fnLeviathanKill",
						"fnTerrierKill",
						"fnWeaselKill",
						"fnLancasterKill",
						"fnSharkKill",
						"fnWarbirdTeamKill",
						"fnJavelinTeamKill",
						"fnSpiderTeamKill",
						"fnLeviathanTeamKill",
						"fnTerrierTeamKill",
						"fnWeaselTeamKill",
						"fnLancasterTeamKill",
						"fnSharkTeamKill",
						"fnFlagClaimed",
						"fnRating",
						"fnRepelsUsed",
						"ftTimeStarted",
						"ftTimeEnded" };

				String[] shipValues =
					{
						Integer.toString(fnMatchRoundUserID),
						Integer.toString(MPS.getShipType()),
						Integer.toString(MPS.getStatistic(Statistics.SCORE)),
						Integer.toString(MPS.getStatistic(Statistics.DEATHS)),
						Integer.toString(MPS.getStatistic(Statistics.WARBIRD_KILL)),
						Integer.toString(MPS.getStatistic(Statistics.JAVELIN_KILL)),
						Integer.toString(MPS.getStatistic(Statistics.SPIDER_KILL)),
						Integer.toString(MPS.getStatistic(Statistics.LEVIATHAN_KILL)),
						Integer.toString(MPS.getStatistic(Statistics.TERRIER_KILL)),
						Integer.toString(MPS.getStatistic(Statistics.WEASEL_KILL)),
						Integer.toString(MPS.getStatistic(Statistics.LANCASTER_KILL)),
						Integer.toString(MPS.getStatistic(Statistics.SHARK_KILL)),
						Integer.toString(MPS.getStatistic(Statistics.WARBIRD_TEAMKILL)),
						Integer.toString(MPS.getStatistic(Statistics.JAVELIN_TEAMKILL)),
						Integer.toString(MPS.getStatistic(Statistics.SPIDER_TEAMKILL)),
						Integer.toString(MPS.getStatistic(Statistics.LEVIATHAN_TEAMKILL)),
						Integer.toString(MPS.getStatistic(Statistics.TERRIER_TEAMKILL)),
						Integer.toString(MPS.getStatistic(Statistics.WEASEL_TEAMKILL)),
						Integer.toString(MPS.getStatistic(Statistics.LANCASTER_TEAMKILL)),
						Integer.toString(MPS.getStatistic(Statistics.SHARK_TEAMKILL)),
						Integer.toString(MPS.getStatistic(Statistics.FLAG_CLAIMED)),
						Integer.toString(MPS.getStatistic(Statistics.RATING)),
						Integer.toString(MPS.getStatistic(Statistics.REPELS_USED)),
						started,
						ended };

				m_botAction.SQLInsertInto(dbConn, "tblMatchRoundUserShip", shipFields, shipValues);
			}
		}
		catch (Exception e)
		{
			System.out.println("Error: " + e.getMessage() + e.getLocalizedMessage());
			e.printStackTrace();
		};
	};

	// sets rank
	public void setRank(int fnRank)
	{
		m_fnRank = fnRank;
	};

	// set ship and freq
	public void setShipAndFreq(int fnShipType, int fnFrequency)
	{
		if (m_statTracker.getShipType() != fnShipType)
			m_statTracker.createNewShip(fnShipType);
		m_fnFrequency = fnFrequency;
	};

	// set amount of deaths
	public void setSpecAt(int fnSpecAt)
	{
		m_fnSpecAt = fnSpecAt;
	};

	// warpto
	public void warpTo(int x, int y)
	{
		m_botAction.warpTo(m_fcPlayerName, x, y);
	};

	// report start of game
	public void reportStartOfGame()
	{
		if (m_fnPlayerState == 0)
		{
			m_fnPlayerState = 1;
			m_statTracker.startNow();
			lagRequestTask = new LagRequestTask();
			m_botAction.scheduleTaskAtFixedRate(lagRequestTask, 0, m_rules.getInt("lagcheckdelay") * 1000);
		};
	};

	// report kill
	public void reportKill(int fnPoints, int killeeID)
	{
		int shipType = m_botAction.getPlayer(killeeID).getShipType();
		int killeeFreq = m_botAction.getPlayer(killeeID).getFrequency();

		m_statTracker.reportKill(fnPoints, killeeID, m_fnFrequency, shipType, killeeFreq);
	};

	/**
	 *
	 * Adds repelUsed to stats
	 */
	public void reportRepelUsed()
	{
		m_statTracker.reportRepelUsed();
	}

	/**
	 * Method reportFlagClaimed.
	 *
	 * Adds flagclaimed to stats
	 */
	public void reportFlagClaimed()
	{
		m_statTracker.reportFlagClaimed();
	}

	// report death
	public void reportDeath()
	{
		resetOutOfBorderTime();
		m_statTracker.reportDeath();

		//lag check timer cancel
		if ((m_statTracker.getStatistic(Statistics.DEATHS) >= m_fnSpecAt) && (m_rules.getString("winby").equals("kills")))
		{
			if (m_fnPlayerState != 2)
			{
				m_fnPlayerState = 4;
				if (lagRequestTask != null)
                    m_botAction.cancelTask(lagRequestTask);
			}
			m_logger.specAndSetFreq(m_fcPlayerName, m_team.getFrequency());
			m_logger.sendArenaMessage(getPlayerName() + " is out. " + getKills() + " wins " + getDeaths() + " losses");
		};
	}

	// report lagout
	/*
	public void reportLagout() {
	    if (m_currentShip != null) m_currentShip.endNow();
	    {
	      m_fnPlayerState = 3;
	lagRequestTask.cancel();
	    }
	};
	 */

	// report being substituted
	public void reportSubstituted()
	{
		resetOutOfBorderTime();
		m_fnPlayerState = 2;

		//cancel lag checks
		if (lagRequestTask != null)
            m_botAction.cancelTask(lagRequestTask);

		m_fnSpecAt = m_statTracker.getStatistic(Statistics.DEATHS);
		m_statTracker.endNow();

		if (m_player != null)
		{
			m_logger.specAndSetFreq(m_fcPlayerName, m_team.getFrequency());
		};
	};

	// substitute (put yourself in)
	public void substitute(int newSpecAt)
	{
		m_fnSpecAt = newSpecAt;
        m_botAction.scoreReset( m_fcPlayerName );
		getInGame(true);
	};

	// lagin (put back in game)
	public String lagin()
	{
		resetOutOfBorderTime();
		int fnRoundState = m_team.m_round.m_fnRoundState;
		m_player = m_botAction.getPlayer(m_fcPlayerName);
		int lagoutsecs = m_rules.getInt("waitafterlagout");

		if (m_player != null)
		{
			if (isAllowedToPlay())
			{
				if (((System.currentTimeMillis() - m_fnLaggedTime) >= lagoutsecs * 1000) || (m_team.m_round.m_fnRoundState <= 2))
				{
					if ((m_fnLagouts < m_fnMaxLagouts) || (m_fnMaxLagouts == -1) || (fnRoundState == 0))
					{
						if (m_player.getShipType() == 0)
						{
							if (fnRoundState == 3)
								m_fnLagouts++;
							// if the player lagged out for over 5 minutes, create a new ship record:
							/*
							if (System.currentTimeMillis() - m_fnLaggedTime > 5*60*1000) {
							    createNewShip(m_fnShipType);
							};
							 */
							if (m_statTracker.getTotalStatistic(Statistics.TOTAL_KILLS) == 0 && m_statTracker.getTotalStatistic(Statistics.DEATHS) == 0)
								m_botAction.shipReset(m_fcPlayerName);
							getInGame(true);
							if ((m_fnMaxLagouts > 0) && (fnRoundState == 3))
								m_logger.sendPrivateMessage(m_fcPlayerName, "You have " + getLagoutsLeft() + " lagouts left");
							return "yes";
						}
						else
							return "Player is not in spec mode";
					}
					else
						return "Player reached maximum number of lagouts";
				}
				else
					return "You have to wait at least " + lagoutsecs + " seconds before you can return in the game";
			}
			else
				return "Player isn't in game";
		}
		else
			return "Player isn't in the arena";
	};

	// lagout event (when a player lags out to spec)
	public void lagout(boolean fbOutOfArena)
	{
		m_statTracker.endNow();

		resetOutOfBorderTime();
		if (fbOutOfArena)
			m_player = null;
		if (lagRequestTask != null)
            m_botAction.cancelTask(lagRequestTask);
		if (m_fnPlayerState == 1)
			m_fnPlayerState = 3;
		m_fnLaggedTime = System.currentTimeMillis();
	};

	// report end of game
	public void reportEndOfGame()
	{
		if (m_fnPlayerState == 1)
		{
			m_statTracker.endNow();
			if (lagRequestTask != null)
                m_botAction.cancelTask(lagRequestTask);
		}
	};

	// get out of game
	public void getInGame(boolean fbSilent)
	{
		resetOutOfBorderTime();
		m_logger.setFreqAndShip(m_fcPlayerName, m_fnFrequency, m_statTracker.getShipType());
		m_fnPlayerState = 1;
		if (m_rules.getInt("checkforlag") == 1)
		{
			lagRequestTask = new LagRequestTask();
			int lagcheckdelay = m_rules.getInt("lagcheckdelay");
			if (lagcheckdelay <= 5)
				lagcheckdelay = 60;
			m_botAction.scheduleTaskAtFixedRate(lagRequestTask, 0, lagcheckdelay * 1000);
		}

		if (!fbSilent)
		{
			if (m_rules.getInt("ship") == 0)
				m_logger.sendArenaMessage(m_fcPlayerName + " in for " + m_team.m_fcTeamName + " with ship " + m_statTracker.getShipType());
			else
				m_logger.sendArenaMessage(m_fcPlayerName + " in for " + m_team.m_fcTeamName);
		}
	};

	// get out of game
	public void getOutOfGame()
	{
		resetOutOfBorderTime();
		m_logger.doubleSpec(m_fcPlayerName);
		m_fnPlayerState = 0;
		if (lagRequestTask != null)
            m_botAction.cancelTask(lagRequestTask);
	};

	// reward
	public void flagReward(int points)
	{
		m_statTracker.reportFlagReward(points);
	};

	public void setAboutToBeSubbed(boolean waarde)
	{
		m_aboutToBeSubbed = waarde;
	};

	public void setShip(int ship)
	{
		if (ship != m_statTracker.getShipType())
		{
			m_statTracker.createNewShip(ship);
		};
		m_logger.setShip(m_fcPlayerName, m_statTracker.getShipType());

		if (m_player != null)
			if (m_player.getFrequency() != getFrequency())
				m_logger.setFreq(m_fcPlayerName, getFrequency());
	};

	// return the amount of deaths the scoreboard should count for him.
	public int getDeaths()
	{
		if (m_rules.getString("winby").equals("killrace") || ((m_fnPlayerState >= 1) && (m_fnPlayerState <= 2)))
			return m_statTracker.getTotalStatistic(Statistics.DEATHS);
		if (m_fnPlayerState == 0)
			return 0;
		if (m_fnPlayerState == 3)
			return m_fnSpecAt;
		if (m_fnPlayerState == 4)
			return m_fnSpecAt;
		return 0;
	};

	public String getLagoutsLeft()
	{
		if (m_fnMaxLagouts != -1)
			return new Integer(m_fnMaxLagouts - m_fnLagouts).toString();
		else
			return "unlimited";
	};

	public int getLagOuts() { return m_fnLagouts; }

	public boolean isLagged()
	{
		if (m_fnPlayerState == 3)
			return true;
		return false;
	};

	public void setLagByBot(boolean b)
	{
		m_lagByBot = b;
	}

	public boolean getLagByBot()
	{
		return m_lagByBot;
	}

	public boolean isReadyToPlay()
	{
		if (((m_fnPlayerState == 0) || (m_fnPlayerState == 1)) && (!m_aboutToBeSubbed))
			return true;
		else
			return false;
	};

	public boolean isAllowedToPlay()
	{
		if (((m_fnPlayerState == 0) || (m_fnPlayerState == 1) || (m_fnPlayerState == 3)) && (!m_aboutToBeSubbed))
			return true;
		else
			return false;
	};

	public boolean isWasInGame()
	{
		if (((m_fnPlayerState == 0) || (m_fnPlayerState == 1) || (m_fnPlayerState == 3) || (m_fnPlayerState == 4)) && (!m_aboutToBeSubbed))
			return true;
		else
			return false;
	};

	public String getPlayerStateName()
	{
		if (m_fnPlayerState == 0)
			return "not in game";
		else if (m_fnPlayerState == 1)
			return "in game";
		else if (m_fnPlayerState == 2)
			return "substituted";
		else if (m_fnPlayerState == 3)
			return "lagged out";
		else if (m_fnPlayerState == 4)
			return "out";
		return "";
	};

	public int getPoints()
	{
		String winby = m_rules.getString("winby").toLowerCase();

		if (winby.equals("score") || winby.equals("race"))
		{
			return getScore();
		}
		else if (winby.equals("kills") || winby.equals("killrace"))
		{
			return getKills();
		}
		else if (winby.equals("timerace"))
		{
			if (m_switchedShip)
				return m_statTracker.getTotalStatistic(Statistics.RATING);
			else
				return m_statTracker.getStatistic(Statistics.RATING);
		}
		return 0;
	}

	/**
	 * Method getStatistics.
	 * @return String A formated line of stats depending on the ship
	 */
	public String[] getTotalStatistics()
	{
		return m_statTracker.getTotalStatisticsSummary();
	}

	/**
	 * Method getStatistics.
	 * @return String A formated line of stats depending on the ship
	 */
	public String[] getStatistics()
	{
		return m_statTracker.getStatisticsSummary();
	};

	public long getOutOfBorderTime()
	{
		return m_fnOutOfBorderTime;
	};
	public void setOutOfBorderTime()
	{
		m_fnOutOfBorderTime = System.currentTimeMillis();
		m_fbHasHalfBorderWarning = false;
	};
	public void resetOutOfBorderTime()
	{
		m_fnOutOfBorderTime = 0;
		m_fbHasHalfBorderWarning = false;
	};

	public void setHalfBorderWarning()
	{
		m_fbHasHalfBorderWarning = true;
	};
	public boolean hasHalfBorderWarning()
	{
		return m_fbHasHalfBorderWarning;
	};

	public void kickOutOfGame()
	{
		m_fnPlayerState = 4;
		if (lagRequestTask != null)
            m_botAction.cancelTask(lagRequestTask);

		m_statTracker.changeDeaths(10);

		m_logger.specAndSetFreq(m_fcPlayerName, m_team.getFrequency());
		m_logger.sendArenaMessage(getPlayerName() + " is out (too long outside of base). " + getKills() + " wins " + getDeaths() + " losses");
	};

	public int getActualDeaths()
	{
		return m_statTracker.getTotalStatistic(Statistics.DEATHS);
	};
	public int getKills()
	{
		return m_statTracker.getTotalStatistic(Statistics.TOTAL_KILLS);
	}

	/**
	 * Gets total statistics added over all ships
	 *
	 * @param statType @see twcore.misc.statistics.StatisticRequester.java for stattypes
	 */
	public int getTotalStatistic(int statType)
	{
		return m_statTracker.getTotalStatistic(statType);
	}

	/**
	 * Gets total statistics for current ships
	 *
	 * @param statType @see twcore.misc.statistics.StatisticRequester.java for stattypes
	 */
	public int getStatistic(int statType)
	{
		return m_statTracker.getStatistic(statType);
	};
	public int getScore()
	{
		return m_statTracker.getStatistic(Statistics.SCORE);
	};
	public int getSpecAt()
	{
		return m_fnSpecAt;
	};
	public int getPlayerState()
	{
		return m_fnPlayerState;
	};
	public int getShipType()
	{
		return m_statTracker.getShipType();
	};
	public String getPlayerName()
	{
		return m_fcPlayerName;
	};
	public long getLaggedTime()
	{
		return m_fnLaggedTime;
	};

	public int getFrequency()
	{
		return m_fnFrequency;
	}

	public void handleEvent(Message event)
	{
		playerLagInfo.handleEvent(event);
	}

	public void specForLag(String reason)
	{
		m_botAction.sendSmartPrivateMessage(m_fcPlayerName, "You have been placed into spectator mode due to lag.");
		m_botAction.sendSmartPrivateMessage(m_fcPlayerName, reason);
		m_logger.doubleSpec(m_fcPlayerName);
	}

	public void checkLag()
	{
		try
		{
			int currentPing = playerLagInfo.getCurrentPing();
			double s2c = playerLagInfo.getS2C();
			double c2s = playerLagInfo.getC2S();
			double s2cSlowPercent = playerLagInfo.getS2CSlowPercent();
			double c2sSlowPercent = playerLagInfo.getC2SSlowPercent();
			double spikeSD = playerLagInfo.getSpikeSD();
			int numSpikes = playerLagInfo.getNumSpikes();
			/*
				m_botAction.sendArenaMessage("Lag for " + m_fcPlayerName + ":");
				m_botAction.sendArenaMessage("Current Ping: " + currentPing + "ms.");
				m_botAction.sendArenaMessage("C2S Packetloss: " + c2s + "%.  S2C Packetloss: " + s2c + "%.");
				m_botAction.sendArenaMessage("C2S Slow Packets: " + c2sSlowPercent + "%.  S2C Slow Packets: " + s2cSlowPercent + "%.");
				m_botAction.sendArenaMessage("Spike: +- " + spikeSD + "ms.  Number of spikes: " + numSpikes + ".");
			*/
			if (m_fnPlayerState == 1)
			{
				if (currentPing > maxCurrPing)
					specForLag("Current ping is: " + currentPing + "ms.  Maximum allowed ping is: " + maxCurrPing + "ms.");
				else if (s2c > maxPacketLoss)
					specForLag("Current S2C Packetloss is: " + s2c + "%.  Maximum allowed S2C Packetloss is: " + maxPacketLoss + "%.");
				else if (c2s > maxPacketLoss)
					specForLag("Current C2S Packetloss is: " + c2s + "%.  Maximum allowed C2S Packetloss is: " + maxPacketLoss + "%.");
				//else if(s2cSlowPercent > maxSlowPackets)
				//  specForLag("Current S2C Slow Packetloss is: " + s2cSlowPercent + "%.  Maximum allowed S2C Slow Packetloss is: " + maxSlowPackets + "%.");
				//else if(c2sSlowPercent > maxSlowPackets)
				//  specForLag("Current C2S Slow Packetloss is: " + c2sSlowPercent + "%.  Maximum allowed C2S Slow Packetloss is: " + maxSlowPackets + "%.");
				else if (spikeSD > maxStandardDeviation)
					specForLag("Current spiking: +- " + spikeSD + "ms.  " + "Maximum spiking allowed: +- " + maxStandardDeviation + "ms.");
				else if (numSpikes > maxNumSpikes)
					specForLag("Number of recent spikes: " + spikeSD + ".  Maximum spikes allowed: " + maxNumSpikes + ".");
			}
		}
		catch (Exception e)
		{
		}
	}

	/**
		 * @author FoN
		 *
		 * This class is to congregate all the stats so they can be organised and added + removed easily
		 */
	private class TotalStatistics
	{
		private MatchPlayerShip m_currentShip;
		private LinkedList m_ships;

		public TotalStatistics()
		{
			m_ships = new LinkedList();
		}

		/**
		* Creates a new ship given a shiptype
		*
		* @param fnShipType Type of ship 1 - 8 corresponding to Wb - Shark
		*/
		public void createNewShip(int fnShipType)
		{
			if (m_currentShip != null)
				m_currentShip.endNow();
			m_currentShip = new MatchPlayerShip(fnShipType);
			m_ships.add(m_currentShip);
		}

		/**
		 * Method reportKill.
		 *
		 * @param fnPoints The amount of points obtained for kill
		 * @param killeeID The person who got killed
		 * @param m_fnFrequency The frequency of the killer
		 * @param shipType The type of ship killed
		 * @param killeeFreq The frequency of the killed
		 */
		public void reportKill(int fnPoints, int killeeID, int m_fnFrequency, int shipType, int killeeFreq)
		{
			if (m_currentShip != null)
				m_currentShip.reportKill(fnPoints, killeeID, m_fnFrequency, shipType, killeeFreq);

		}

		/**
		* Method reportRepelUsed.
		*/
		public void reportRepelUsed()
		{
			if (m_currentShip != null)
				m_currentShip.reportRepelUsed();
		}

		/**
		* Method reportDeath.
		*/
		public void reportDeath()
		{
			if (m_currentShip != null)
				m_currentShip.reportDeath();
		}

		/**
		 * Method reportFlagClaimed.
		 *
		 * Adds flagclaimed to stats
		 */
		public void reportFlagClaimed()
		{
			if (m_currentShip != null)
				m_currentShip.reportFlagClaimed();
		}

		/**
		 * Adds to the m_score.
		 * @param score The m_score to set
		 */
		public void reportFlagReward(int score)
		{
			if (m_currentShip != null)
				m_currentShip.flagReward(score);

		}

		/**
		 * Method changeDeaths.
		 * @param deaths
		 */
		public void changeDeaths(int deaths)
		{
			if (m_currentShip != null)
				m_currentShip.changeDeaths(deaths);
		}

		/**
		* Method startNow.
		*/
		public void startNow()
		{
			if (m_currentShip != null)
				m_currentShip.startNow();
		}

		/**
		 * Method endNow.
		 */
		public void endNow()
		{
			if (m_currentShip != null)
				m_currentShip.endNow();
		}

		/**
		 * Method getStatistics.
		 * @return String depending on the ship type
		 */
		public String[] getTotalStatisticsSummary()
		{
			Iterator i = m_ships.iterator();
			LinkedList summary = new LinkedList();
			while (i.hasNext())
			{
				String[] summ = ((MatchPlayerShip) i.next()).getStatisticsSummary();
				for (int j = 0; j < summ.length; j++)
					summary.add(summ[j]);
			}

			return (String[]) summary.toArray();
		}

		/**
		 * Method getStatistics.
		 * @return String depending on the ship type
		 */
		public String[] getStatisticsSummary()
		{
			return m_currentShip.getStatisticsSummary();
		}

		/**
		 * Method getTotalStatistic.
		 * @param i
		 * @return int
		 */
		public int getTotalStatistic(int statType)
		{
			Iterator i = m_ships.iterator();
			int total = 0;

			while (i.hasNext())
			{
				total += ((MatchPlayerShip) i.next()).getStatistic(statType);
			}

			return total;
		}

		/**
		 * Method getTotalStatistic.
		 * @param statType Type of statistic
		 * @return int
		 */
		public int getStatistic(int statType)
		{
			return m_currentShip.getStatistic(statType);
		}

		/**
		 * @return shipType the type of current ship
		 */
		public int getShipType()
		{
			if (m_currentShip != null)
				return m_currentShip.getShipType();
			else
				return 0; //error
		}
	}

	private class MatchPlayerShip
	{
		private Statistics m_statisticTracker;

		private java.util.Date m_ftTimeStarted;
		private java.util.Date m_ftTimeEnded;

		public MatchPlayerShip(int fnShipType)
		{
			m_ftTimeStarted = new java.util.Date();

			//statistics tracker
			m_statisticTracker = new Statistics(fnShipType);
		};

		// report kill
		public void reportKill(int fnPoints, int killeeID, int frequency, int shipType, int killeeFreq)
		{
			if (killeeFreq == frequency)
			{
				switch (shipType)
				{
					case 1 : //wb
						m_statisticTracker.setStatistic(Statistics.WARBIRD_TEAMKILL);
						break;

					case 2 : //jav
						m_statisticTracker.setStatistic(Statistics.JAVELIN_TEAMKILL);
						break;

					case 3 : //spider
						m_statisticTracker.setStatistic(Statistics.SPIDER_TEAMKILL);
						break;

					case 4 : //lev
						m_statisticTracker.setStatistic(Statistics.LEVIATHAN_TEAMKILL);
						break;

					case 5 : //terr
						m_statisticTracker.setStatistic(Statistics.TERRIER_TEAMKILL);
						break;

					case 6 : //x
						m_statisticTracker.setStatistic(Statistics.WEASEL_TEAMKILL);
						break;

					case 7 : //lanc
						m_statisticTracker.setStatistic(Statistics.LANCASTER_TEAMKILL);
						break;

					case 8 : //shark
						m_statisticTracker.setStatistic(Statistics.SHARK_TEAMKILL);
						break;
				}
			}
			else
			{
				switch (shipType)
				{
					case 1 : //wb
						m_statisticTracker.setStatistic(Statistics.WARBIRD_KILL);
						break;

					case 2 : //jav
						m_statisticTracker.setStatistic(Statistics.JAVELIN_KILL);
						break;

					case 3 : //spider
						m_statisticTracker.setStatistic(Statistics.SPIDER_KILL);
						break;

					case 4 : //lev
						m_statisticTracker.setStatistic(Statistics.LEVIATHAN_KILL);
						break;

					case 5 : //terr
						m_statisticTracker.setStatistic(Statistics.TERRIER_KILL);
						break;

					case 6 : //x
						m_statisticTracker.setStatistic(Statistics.WEASEL_KILL);
						break;

					case 7 : //lanc
						m_statisticTracker.setStatistic(Statistics.LANCASTER_KILL);
						break;

					case 8 : //shark
						m_statisticTracker.setStatistic(Statistics.SHARK_KILL);
						break;
				}
			}

			m_statisticTracker.setStatistic(Statistics.SCORE, fnPoints);
		}

		/**
		* Method reportRepelUsed.
		*/
		public void reportRepelUsed()
		{
			m_statisticTracker.setStatistic(Statistics.REPELS_USED);
		}

		/**
		 * Method reportFlagClaimed.
		 *
		 * Adds flagclaimed to stats
		 */
		public void reportFlagClaimed()
		{
			m_statisticTracker.setStatistic(Statistics.FLAG_CLAIMED);
		}

		// report death
		public void reportDeath()
		{
			m_statisticTracker.setStatistic(Statistics.DEATHS);
		}

		public void flagReward(int points)
		{
			m_statisticTracker.setStatistic(Statistics.SCORE, points);
		}

		/**
		 * Method changeDeaths.
		 * @param deaths
		 */
		public void changeDeaths(int deaths)
		{
			m_statisticTracker.changeStatistic(Statistics.DEATHS, deaths);
		}

		// report end of playership
		public void endNow()
		{
			m_ftTimeEnded = new java.util.Date();
		}

		// report start of playership
		public void startNow()
		{
			m_ftTimeStarted = new java.util.Date();
		}

		public int getShipType()
		{
			return m_statisticTracker.getShipType();
		}

		public String[] getStatisticsSummary()
		{
			return m_statisticTracker.getStatisticsSummary();
		}

		public int getStatistic(int statType)
		{
			return m_statisticTracker.getIntStatistic(statType);
		}

		public java.util.Date getTimeStarted()
		{
			return m_ftTimeStarted;
		}

		public java.util.Date getTimeEnded()
		{
			return m_ftTimeEnded;
		}
	}

	private class LagRequestTask extends TimerTask
	{
		public void run()
		{
			try
			{
				playerLagInfo.updateLag();
				m_botAction.scheduleTask(new LagCheckTask(), 3000);
			}
			catch (Exception e)
			{
				if (lagRequestTask != null)
                    m_botAction.cancelTask(lagRequestTask);
			}
		}
	}

	private class LagCheckTask extends TimerTask
	{
		public void run()
		{
			checkLag();
		}
	}

};
