package twcore.bots.twlbot;

import java.util.Iterator;
import java.util.LinkedList;

import twcore.core.stats.Statistics;

public class LeaguePlayer
{
	class LeaguePlayerShip
	{
		private Statistics m_statisticTracker;

		private java.util.Date m_ftTimeStarted;
		private java.util.Date m_ftTimeEnded;

		public LeaguePlayerShip(int fnShipType)
		{
			m_ftTimeStarted = new java.util.Date();

			//statistics tracker
			m_statisticTracker = new Statistics(fnShipType);
		};

		// report kill
		public void setStatistic(int statType)
		{
			m_statisticTracker.setStatistic(statType);
		}

		/**
		 * Adds to the value already there
		 * @param statType the type of statistic to increment with value
		 * @param value the value used to incrememnt the statistic
		 */
		public void setStatistic(int statType, int value)
		{
			m_statisticTracker.setStatistic(statType, value);
		}

		/**
		 * Method changeStatistic.
		 * @param value the value the statistic needs to be changed to
		 */
		public void changeStatistic(int statType, int value)
		{
			m_statisticTracker.changeStatistic(statType, value);
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


	/**
		 * @author FoN
		 *
		 * This class is to congregate all the stats so they can be organised and added + removed easily
		 */
	private class TotalStatistics
	{
		private LeaguePlayerShip m_currentShip;
		private LinkedList<LeaguePlayerShip> m_ships;

		public TotalStatistics()
		{
			m_ships = new LinkedList<LeaguePlayerShip>();
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
			m_currentShip = new LeaguePlayerShip(fnShipType);
			m_ships.add(m_currentShip);
		}

		/**
		 * Method reportStatistic.
		 *
		 * @param statType the statisticType ot increment
		 * @param value the value to increment the statistic by
		 */
		public void reportStatistic(int statType,int value)
		{
			if (m_currentShip != null)
				m_currentShip.setStatistic(statType, value);

		}

		/**
		 * Method reportStatistic.
		 *
		 * @param statType the statisticType ot increment
		 */
		public void reportStatistic(int statType)
		{
			if (m_currentShip != null)
				m_currentShip.setStatistic(statType);
		}

		/**
		 * Method reportKill.
		 *
		 * @param statType the statisticType ot change
		 * @param value the value to change the statistic by
		 */
		public void changeStatistic(int statType,int value)
		{
			if (m_currentShip != null)
				m_currentShip.changeStatistic(statType, value);
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
			Iterator<LeaguePlayerShip> i = m_ships.iterator();
			LinkedList<String> summary = new LinkedList<String>();
			while (i.hasNext())
			{
				String[] summ = i.next().getStatisticsSummary();
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
			Iterator<LeaguePlayerShip> i = m_ships.iterator();
			int total = 0;

			while (i.hasNext())
			{
				total += i.next().getStatistic(statType);
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
		 * Gets the linked list of ships
		 * @return Iterator to the linked list of ships
		 */
		public Iterator<LeaguePlayerShip> getShips()
		{
			return m_ships.iterator();
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



	private String m_playerName;
	private int m_frequency;
	private int m_deathLimit;

	private int m_lagouts = 0;


	private boolean m_outOfGame = false;
	private boolean m_laggedOut = false;
	private boolean m_warned = false;
	private boolean m_inBase = false;
	private boolean m_saveState = false;
	private long m_timeOfLagout = 0;
	private int m_timer = 0;
	private String m_sub = "  -";

	private TotalStatistics m_statisticTracker;

	public LeaguePlayer(String name, int ship, int freq, int deaths)
	{
		m_playerName = name;
		m_frequency = freq;
		m_deathLimit = deaths;
		m_timer = (int) (System.currentTimeMillis() / 1000);
		m_statisticTracker = new TotalStatistics();
		m_statisticTracker.createNewShip(ship);
	}

	/**
	 * @author FoN
	 *
	 * Ends current ship and creates new ship
	 * @param s New shiptype
	 */
	public void setShip(int s)
	{
		m_statisticTracker.endNow();
		m_statisticTracker.createNewShip(s);
	}

	public int getFreq()
	{
		return m_frequency;
	}

	public int getDeathLimit()
	{
		return m_deathLimit;
	}

	public void setDeathLimit(int d)
	{
		m_deathLimit = d;
	}

	public void reportStatistic(int statisticType)
	{
		switch (statisticType)
		{
			case Statistics.DEATHS:
				m_timer = (int) (System.currentTimeMillis() / 1000);
				m_warned = false;
				m_inBase = false;
				m_statisticTracker.reportStatistic(statisticType);
				break;
			default:
				m_statisticTracker.reportStatistic(statisticType);
				break;
		};
	}

	public void reportStatistic(int statisticType, int value)
	{
		m_statisticTracker.reportStatistic(statisticType, value);
	}

	public void changeStatistic(int statisticType, int value)
	{
		m_statisticTracker.changeStatistic(statisticType, value);
	}

	public int getStatistic(int statType)
	{
		return m_statisticTracker.getStatistic(statType);
	}

	public int getTotalStatistic(int statType)
	{
		return m_statisticTracker.getTotalStatistic(statType);
	}

	public Iterator<LeaguePlayerShip> getPlayerShips()
	{
		return m_statisticTracker.getShips();
	}



	//other stuff
	public String getName()
	{
		return m_playerName;
	}

	public int getShip()
	{
		return m_statisticTracker.getShipType();
	}

	public int getLagouts()
	{
		return m_lagouts;
	}

	public void addLagout()
	{
		m_lagouts++;
	}

	public void setLagouts(int l)
	{
		m_lagouts = l;
	}

	public void isOut()
	{
		m_outOfGame = true;
	}

	public boolean isOutOfGame()
	{
		return m_outOfGame;
	}

	//lagout related functions
	public void laggedOut()
	{
		m_laggedOut = true;
		m_timeOfLagout = System.currentTimeMillis();
	}

	public void notLaggedOut()
	{
		m_laggedOut = false;
		m_timer = (int) (System.currentTimeMillis() / 1000);
	}

	public boolean isLagged()
	{
		return m_laggedOut;
	}

	public int getTimeSinceLagout()
	{
		int time = (int) (System.currentTimeMillis() / 1000);
		time -= m_timeOfLagout / 1000;
		return time;
	}

	public void subbedBy(String name)
	{
		m_sub = name;
	}

	public String getSub()
	{
		return m_sub;
	}

	public void updateTimer()
	{
		m_timer = (int) (System.currentTimeMillis() / 1000);
		m_warned = false;
		m_inBase = false;
	}

	public boolean hasBeenInBase()
	{
		return m_inBase;
	}

	public void inBase()
	{
		m_inBase = true;
	}

	public void notInBase()
	{
		m_inBase = false;
	}

	public int timeOutOfBounds()
	{
		int time = (int) (System.currentTimeMillis() / 1000);
		time -= m_timer;
		return time;
	}

	public boolean warned()
	{
		return m_warned;
	}

	public void haveWarned()
	{
		m_warned = true;
		//timer = (int)(System.currentTimeMillis()/1000);
	}

	public boolean state()
	{
		return m_saveState;
	}

	public void toggleState()
	{
		m_saveState = true;
	}

}
