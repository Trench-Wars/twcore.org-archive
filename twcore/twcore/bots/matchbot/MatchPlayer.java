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

import twcore.core.*;
import twcore.misc.database.DBPlayerData;
import java.sql.*;
import java.util.*;
import java.text.*;

public class MatchPlayer
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

	Connection m_connection;
	BotAction m_botAction;
	Player m_player;
	BotSettings m_rules;

	MatchPlayerShip m_currentShip;
	LinkedList m_ships;

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
	int  m_fnShipType = 0, m_fnSpecAt = 10, m_fnFrequency = 0, m_fnLagouts = 0,
	/* playerstate: 0 - Not In Game (0 deaths)
	                1 - In Game
	                2 - Substituted
	                3 - Lagged (m_fnSpecAt deaths)
	                4 - Out (m_fnSpecAt deaths)
	 */
	m_fnPlayerState = 0, m_fnMaxLagouts;
	boolean m_aboutToBeSubbed = false;
	boolean m_fbSubstituter = false;

	static int NOT_IN_GAME = 0;
	static int IN_GAME = 1;
	static int SUBSTITUTED = 2;
	static int LAGGED = 3;
	static int OUT = 4;

	//statistic tracker
	private Statistics m_statisticTracker;
	
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
		m_fnShipType = m_player.getShipType();
		m_fnFrequency = m_player.getFrequency();
		m_fnPlayerState = 0;
		m_ships = new LinkedList();
		playerLagInfo = new PlayerLagInfo(m_botAction, fcPlayerName, m_rules.getInt("spikesize"));
		playerLagInfo.updateLag();
		maxCurrPing = m_rules.getInt("maxcurrping");
		maxPacketLoss = m_rules.getDouble("maxploss");
		maxSlowPackets = m_rules.getDouble("maxslowpackets");
		maxStandardDeviation = m_rules.getDouble("maxstandarddeviation");
		maxNumSpikes = m_rules.getInt("maxnumspikes");

		//statistics tracker
		m_statisticTracker = new Statistics(m_fnShipType);

		if ((m_rules.getInt("storegame") != 0) || (m_rules.getInt("rosterjoined") != 0))
			m_dbPlayer = new DBPlayerData(m_botAction, "local", m_fcPlayerName);

		m_logger.scoreReset(m_fcPlayerName);
	}

	// store player result
	public void storePlayerResult(int fnMatchRoundID, int fnTeam)
	{
		try
		{
			int substituted = 0;
			if (m_fnPlayerState == 2)
				substituted = 1;

			MatchPlayerShip MPS;
			ListIterator i = m_ships.listIterator();
			java.util.Date m_ftTimeStarted, m_ftTimeEnded;
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
						"ftTimeStarted",
						"ftTimeEnded",
						"fnSubstituted" };
				String[] values =
					{
						Integer.toString(fnMatchRoundID),
						Integer.toString(m_dbPlayer.getUserID()),
						Tools.addSlashesToString(m_fcPlayerName),
						Integer.toString(fnTeam),
						Integer.toString(m_fnShipType),
						Integer.toString(MPS.getScore()),
						Integer.toString(MPS.getKills()),
						Integer.toString(MPS.getDeaths()),
						Integer.toString(m_fnLagouts),
						started,
						ended,
						Integer.toString(substituted)};
				m_botAction.SQLInsertInto("local", "tblMatchRoundUser", fields, values);
			};
		}
		catch (Exception e)
		{
			System.out.println("Error: " + e.getMessage() + e.getLocalizedMessage());
			e.printStackTrace();
		};
	};

	//
	private void createNewShip(int fnShipType)
	{
		if (m_currentShip != null)
			m_currentShip.endNow();
		m_currentShip = new MatchPlayerShip(fnShipType);
		m_ships.add(m_currentShip);
	};

	// sets rank
	public void setRank(int fnRank)
	{
		m_fnRank = fnRank;
	};

	// set ship and freq
	public void setShipAndFreq(int fnShipType, int fnFrequency)
	{
		if (m_fnShipType != fnShipType)
			createNewShip(fnShipType);
		m_fnShipType = fnShipType;
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
			if (m_currentShip != null)
				m_currentShip.startNow();
			lagRequestTask = new LagRequestTask();
			m_botAction.scheduleTaskAtFixedRate(lagRequestTask, 0, m_rules.getInt("lagcheckdelay") * 1000);

		};
	};

	// report kill
	public void reportKill(int fnPoints, int killeeID)
	{
		int shipType = m_botAction.getPlayer(killeeID).getShipType();
		int killeeFreq = m_botAction.getPlayer(killeeID).getFrequency();
		
		if (killeeFreq == m_fnFrequency)
		{
			m_statisticTracker.setTeamKills();
		}
		else
		{
			switch (shipType)
			{
				case 1 : //wb
					m_statisticTracker.setWbKill();
					break;

				case 2 : //jav
					m_statisticTracker.setJavKill();
					break;

				case 3 : //spider
					m_statisticTracker.setSpiderKill();
					break;

				case 4 : //lev
					m_statisticTracker.setLevKill();
					break;

				case 5 : //terr
					m_statisticTracker.setTerrKill();
					break;

				case 6 : //x
					m_statisticTracker.setWeaselKill();
					break;

				case 7 : //lanc
					m_statisticTracker.setLancKill();
					break;

				case 8 : //shark
					m_statisticTracker.setSharkKill();
					break;
			}
		}

		m_statisticTracker.setScore(fnPoints);
		if (m_currentShip != null)
			m_currentShip.reportKill(fnPoints);
	};

	/**
	 * Method reportFlagClaimed.
	 * 
	 * Adds flagclaimed to stats
	 */
	public void reportFlagClaimed()
	{
		m_statisticTracker.setFlagClaimed();
	}

	// report death
	public void reportDeath()
	{
		m_statisticTracker.setDeaths();
		if (m_fnShipType == 8) //shark
			m_statisticTracker.setAverageRepelCount(m_botAction.getPlayer(m_fcPlayerName).getRepelCount());
		
		resetOutOfBorderTime();
		if (m_currentShip != null)
			m_currentShip.reportDeath();
		if ((m_statisticTracker.getDeaths() >= m_fnSpecAt) && (m_rules.getInt("deaths") > 0))
		{
			if (m_fnPlayerState != 2)
			{
				m_fnPlayerState = 4;
				if (lagRequestTask != null)
					lagRequestTask.cancel();
			}
			m_logger.specAndSetFreq(m_fcPlayerName, m_team.getFrequency());
			m_logger.sendArenaMessage(getPlayerName() + " is out. " + getKills() + " wins " + getDeaths() + " losses");
		};
	};

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
		if (lagRequestTask != null)
			lagRequestTask.cancel();
		m_fnSpecAt = m_statisticTracker.getDeaths();
		if (m_currentShip != null)
			m_currentShip.endNow();

		if (m_player != null)
		{
			m_logger.specAndSetFreq(m_fcPlayerName, m_team.getFrequency());
		};
	};

	// substitute (put yourself in)
	public void substitute(int newSpecAt)
	{
		m_fnSpecAt = newSpecAt;
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
							if (m_statisticTracker.getTotalKills() == 0 && m_statisticTracker.getDeaths() == 0)
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
		m_currentShip.endNow();
		resetOutOfBorderTime();
		if (fbOutOfArena)
			m_player = null;
		if (lagRequestTask != null)
			lagRequestTask.cancel();
		if (m_fnPlayerState == 1)
			m_fnPlayerState = 3;
		m_fnLaggedTime = System.currentTimeMillis();
	};

	// report end of game
	public void reportEndOfGame()
	{
		if ((m_fnPlayerState == 1) && (m_currentShip != null))
		{
			m_currentShip.endNow();
			if (lagRequestTask != null)
				lagRequestTask.cancel();
		}
	};

	// get out of game
	public void getInGame(boolean fbSilent)
	{
		resetOutOfBorderTime();
		m_logger.setFreqAndShip(m_fcPlayerName, m_fnFrequency, m_fnShipType);
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
				m_logger.sendArenaMessage(m_fcPlayerName + " in for " + m_team.m_fcTeamName + " with ship " + m_fnShipType);
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
			lagRequestTask.cancel();
	};

	// reward
	public void flagReward(int points)
	{
		m_statisticTracker.setScore(points);
		if (m_currentShip != null)
			m_currentShip.flagReward(points);
	};

	public void setAboutToBeSubbed(boolean waarde)
	{
		m_aboutToBeSubbed = waarde;
	};

	public void setShip(int ship)
	{
		if (ship != m_fnShipType)
		{
			m_fnShipType = ship;
			createNewShip(ship);
		};
		m_logger.setShip(m_fcPlayerName, m_fnShipType);
                
                if (m_player != null)
                    if (m_player.getFrequency() != getFrequency()) m_logger.setFreq(m_fcPlayerName, getFrequency());
	};

	// return the amount of deaths the scoreboard should count for him.
	public int getDeaths()
	{
		if ((m_fnPlayerState >= 1) && (m_fnPlayerState <= 2))
			return m_statisticTracker.getDeaths();
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

	public boolean isLagged()
	{
		if (m_fnPlayerState == 3)
			return true;
		return false;
	};

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
		else if (winby.equals("kills"))
		{
			return getKills();
		}
		else if (winby.equals("timerace"))
		{
			return m_statisticTracker.getRating();
		}
		return 0;
	}


	;

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
			lagRequestTask.cancel();
		
		m_statisticTracker.changeDeaths(10);
		
		m_logger.specAndSetFreq(m_fcPlayerName, m_team.getFrequency());
		m_logger.sendArenaMessage(getPlayerName() + " is out (too long outside of base). " + getKills() + " wins " + getDeaths() + " losses");
	};

	public int getActualDeaths()
	{
		return m_statisticTracker.getDeaths();
	};
	public int getKills()
	{
		return m_statisticTracker.getTotalKills();
	};
	public int getScore()
	{
		return m_statisticTracker.getScore();
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
		if (m_currentShip != null)
			return m_currentShip.getShipType();
		else
			return 0;
	};
	public String getPlayerName()
	{
		return m_fcPlayerName;
	};
	public long getLaggedTime()
	{
		return m_fnLaggedTime;
	};

        public int getFrequency() { return m_fnFrequency; }
        
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
					lagRequestTask.cancel();
				m_botAction.sendSmartPrivateMessage("Cpt.Guano!", "ERROR: " + e.getMessage());
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
	
	/**
	 * @author FoN
	 * 
	 * This class is to congregate all the stats so they can be organised and added + removed easily
	 */
	private class Statistics
	{
		//stats
		private int m_wbKill;
		private int m_javKill;
		private int m_spiderKill;
		private int m_levKill;
		private int m_terrKill;
		private int m_weaselKill;
		private int m_lancKill;
		private int m_sharkKill;
		private int m_deaths;
		private int m_score;
		private int m_teamKills;
		private int m_avgRepelCount;
		private int m_flagClaimed;

		//others
		private int m_shipType;
		
		private final int MAXIMUM_RATIO = 4;

		public Statistics(int shipType)
		{
			m_shipType = shipType;
			reset();
		}

		/**
		 * Method getRating.
		 * This returns the rating for the player according to this:
		 * 
		 * warbird: points * (1 + (2x + y)/z) * (1 + 0.1x + 0.01y)
		 * x = terr kills 
		 * y = shark kills
		 * z = kills
		 *
		 * jav: points * (1 + .01x - .1y + 0.1z)
		 * x = kills
		 * y = teamkills
		 * z = terr kills
		 *
		 * spiders: points * .8kills/deaths * (1 + .01x + .001y)
		 * x = terr kills
		 * y = shark kills
		 * 
		 * terr: points * 2(x/y) * (1 + 0.2z)
		 * x = kills
		 * y = deaths
		 * z = terr kills
		 *
		 * weasel: points * (x/y) * (1 + 0.2z + 0.2a)
		 * x = kills
		 * y = deaths
		 * z = terr kills
		 * a = num of times flag claimed
		 * 
		 * lanc: points * (x/y) * (1 + 0.05z + 0.001(a + b + c + d))
		 * x = kills
		 * y = deaths
		 * z = terr kills
		 * a = jav kills
		 * b = lanc kills
		 * c = weasel kills
		 * d = wb kills
		 *  
		 * shark: points * (1 + .05x - .05y + .1z) * (1 + 4(x/a)) * (3 / avgRepelCount)
		 * x = kills
		 * y = teamkills
		 * z = terr kills
		 * a = deaths
		 * 
		 * Original idea by Randedl
		 * 
		 * @author FoN
		 * 
		 * @return int which is the rating depending on the shiptype
		 */
		public int getRating()
		{
			int rating = 0;

			switch (m_shipType)
			{
				case 1 : //warbird
					if (getTotalKills() == 0) //can't divide by zero
						return (int) (m_score * MAXIMUM_RATIO * (1 + 0.1 * m_terrKill + 0.01 * m_sharkKill));
					rating = (int) (m_score * (1 + (2 * m_terrKill + m_sharkKill/getTotalKills())) * (1 + 0.1 * m_terrKill + 0.01 * m_sharkKill));
					return rating;

				case 2 : //jav
					rating = (int) (m_score * (1 + 0.01 * getTotalKills() - 0.1 * m_teamKills + 0.1 * m_terrKill));
					return rating;

				case 3 : //spider				
					if (m_deaths == 0) //can't divide by zero
						return (int) (m_score * 0.8 * MAXIMUM_RATIO * (1 + 0.01 * m_terrKill + 0.001 * m_sharkKill));
					rating = (int) (m_score * (0.8 * getTotalKills() / m_deaths) * (1 + 0.01 * m_terrKill + 0.001 * m_sharkKill));
					return rating;

				case 4 : //lev
					rating = m_score;
					return rating;

				case 5 : //terr
					if (m_deaths == 0)
						return (int) (m_score * 2 * MAXIMUM_RATIO * (1 + 0.2 * m_terrKill)); //can't divide by zero
					rating = (int) (m_score * 2 * (getTotalKills() / m_deaths) * (1 + 0.5 * m_terrKill));
					return rating;

				case 6 : //weasel
					if (m_deaths == 0) //can't divide by zero
						return (int) (m_score * MAXIMUM_RATIO * (1 + 0.2 * m_terrKill + 0.2 * m_flagClaimed));
					rating = (int) (m_score * (getTotalKills()/m_deaths) * (1 + 0.2 * m_terrKill + 0.2 * m_flagClaimed));
					return rating;

				case 7 : //lanc
					if (m_deaths == 0) //can't divide by zero
						return (int) (m_score * MAXIMUM_RATIO * (1 + 0.05 * m_terrKill + 0.001 * (m_wbKill + m_javKill + m_weaselKill + m_lancKill)));
					rating = (int) (m_score * (getTotalKills()/m_deaths) * (1 + 0.05 * m_terrKill + 0.001 * (m_wbKill + m_javKill + m_weaselKill + m_lancKill)));
					return rating;

				case 8 : //shark
					if (m_deaths == 0 && (getAverageRepelCount() != 0))
						return (int) (m_score * (1 + 0.05 * getTotalKills() - 0.05 * m_teamKills + 0.1 * m_terrKill) * (1 +  MAXIMUM_RATIO) * (3 / getAverageRepelCount()));
					else if (m_deaths == 0 && getAverageRepelCount() == 0)
						return (int) (m_score * (1 + 0.05 * getTotalKills() - 0.05 * m_teamKills + 0.1 * m_terrKill) * (1 +  MAXIMUM_RATIO) * MAXIMUM_RATIO);
					else if (getAverageRepelCount() == 0)
						return (int) (m_score * (1 + 0.05 * getTotalKills() - 0.05 * m_teamKills + 0.1 * m_terrKill) * (1 + 4 * getTotalKills() / m_deaths) * MAXIMUM_RATIO);	
					else
					{
						rating = (int) (m_score * (1 + 0.05 * getTotalKills() - 0.05 * m_teamKills + 0.1 * m_terrKill) * (1 +  4 * getTotalKills() / m_deaths) * (3 / getAverageRepelCount()));
						return rating;
					}

				default : //if errored
					rating = m_score;
					return rating;
			}

		}

		/**
		 * sets all the stat variables to zero or their initial values
		 */
		private void reset()
		{
			m_wbKill = 0;
			m_javKill = 0;
			m_spiderKill = 0;
			m_levKill = 0;
			m_terrKill = 0;
			m_weaselKill = 0;
			m_lancKill = 0;
			m_sharkKill = 0;
			m_deaths = 0;
			m_score = 0;
			m_teamKills = 0;
			m_flagClaimed = 0;
			m_avgRepelCount = 0;
		}

		/**
		 * Adds up all the shiptype kills and returns total
		 * @return int
		 */
		public int getTotalKills()
		{
			return m_wbKill + m_javKill + m_spiderKill + m_levKill + m_terrKill + m_weaselKill + m_lancKill + m_sharkKill;
		}

		public float getKillDeathRatio()
		{
			if (m_deaths == 0) //cant divide by zero
				return getTotalKills();
			return getTotalKills() / m_deaths;
		}

		/**
		 * Returns the m_javKill.
		 * @return int
		 */
		public int getJavKill()
		{
			return m_javKill;
		}

		/**
		 * Returns the m_lancKill.
		 * @return int
		 */
		public int getLancKill()
		{
			return m_lancKill;
		}

		/**
		 * Returns the m_levKill.
		 * @return int
		 */
		public int getLevKill()
		{
			return m_levKill;
		}

		/**
		 * Returns the m_sharkKill.
		 * @return int
		 */
		public int getSharkKill()
		{
			return m_sharkKill;
		}

		/**
		 * Returns the m_spiderKill.
		 * @return int
		 */
		public int getSpiderKill()
		{
			return m_spiderKill;
		}

		/**
		 * Returns the m_terrKill.
		 * @return int
		 */
		public int getTerrKill()
		{
			return m_terrKill;
		}

		/**
		 * Returns the m_wbKill.
		 * @return int
		 */
		public int getWbKill()
		{
			return m_wbKill;
		}

		/**
		 * Returns the m_weaselKill.
		 * @return int
		 */
		public int getWeaselKill()
		{
			return m_weaselKill;
		}

		/**
		 * Sets the m_javKill.
		 * Increments it by one
		 */
		public void setJavKill()
		{
			m_javKill++;
		}

		/**
		 * Sets the m_lancKill.
		 * Increments it by one
		 */
		public void setLancKill()
		{
			m_lancKill++;
		}

		/**
		 * Sets the m_levKill.
		 * Increments it by one
		 */
		public void setLevKill()
		{
			m_levKill++;
		}

		/**
		 * Sets the m_sharkKill.
		 * Increments it by one
			 */
		public void setSharkKill()
		{
			m_sharkKill++;
		}

		/**
		 * Sets the m_spiderKill.
		 * Increments it by one
		 */
		public void setSpiderKill()
		{
			m_spiderKill++;
		}

		/**
		 * Sets the m_terrKill.
		 * Increments it by one
		 */
		public void setTerrKill()
		{
			m_terrKill++;
		}

		/**
		 * Sets the m_wbKill.
		 * Increments it by one
		 */
		public void setWbKill()
		{
			m_wbKill++;
		}

		/**
		 * Sets the m_weaselKill.
		 * Increments it by one
		 */
		public void setWeaselKill()
		{
			m_weaselKill++;
		}

		/**
		 * Returns the m_deaths.
		 * @return int
		 */
		public int getDeaths()
		{
			return m_deaths;
		}

		/**
		 * Sets the m_deaths.
		 */
		public void setDeaths()
		{
			m_deaths++;
		}

		/**
		 * Changes the death via the input
		 * @ param deaths The deaths to be changed to
		 */
		public void changeDeaths(int deaths)
		{
			m_deaths = deaths;
		}

		/**
		 * Returns the m_score.
		 * @return int
		 */
		public int getScore()
		{
			return m_score;
		}

		/**
		 * Adds to the m_score.
		 * @param score The m_score to set
		 */
		public void setScore(int score)
		{
			m_score += score;
		}

		/**
		 * Returns the m_teamKills.
		 * @return int
		 */
		public int getTeamKills()
		{
			return m_teamKills;
		}

		/**
		 * Sets the m_teamKills.
		 */
		public void setTeamKills()
		{
			m_teamKills++;
		}

		/**
		 * Returns the m_avgRepelCount.
		 * @return int
		 */
		public int getAverageRepelCount()
		{
			if (m_deaths == 0)
				return m_avgRepelCount / 1; //can't divide by zero;
			return m_avgRepelCount/m_deaths;
		}

		/**
		 * Returns the m_flagClaimed.
		 * @return int
		 */
		public int getFlagClaimed()
		{
			return m_flagClaimed;
		}

		/**
		 * Sets the m_avgRepelCount.
		 * @param m_avgRepelCount The m_avgRepelCount to set
		 */
		public void setAverageRepelCount(int avgRepelCount)
		{
			m_avgRepelCount += avgRepelCount;
		}

		/**
		 * Sets the m_flagClaimed.
		 */
		public void setFlagClaimed()
		{
			m_flagClaimed++;
		}

	}
}


class MatchPlayerShip {

    int m_fnShipType = 0,
        m_fnKills = 0,
        m_fnDeaths = 0,
        m_fnScore = 0;

    java.util.Date m_ftTimeStarted, m_ftTimeEnded;


    public MatchPlayerShip(int fnShipType) {
        m_fnShipType = fnShipType;
        m_ftTimeStarted = new java.util.Date();
    };

    // report kill
    public void reportKill(int fnPoints) {
        m_fnKills++;
        m_fnScore += fnPoints;
    };


    // report death
    public void reportDeath() {
        m_fnDeaths++;
    };

   public void flagReward(int points) {
        m_fnScore += points;
   };

   // report end of playership
    public void endNow() {
        m_ftTimeEnded = new java.util.Date();
    };

    // report start of playership
    public void startNow() {
        m_ftTimeStarted = new java.util.Date();
    };

    public int getShipType() { return m_fnShipType; };
    public int getKills() { return m_fnKills; };
    public int getDeaths() { return m_fnDeaths; };
    public int getScore() { return m_fnScore; };
    public java.util.Date getTimeStarted() { return m_ftTimeStarted; };
    public java.util.Date getTimeEnded() { return m_ftTimeEnded; };


};
