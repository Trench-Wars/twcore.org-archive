/*
 * MatchLogger.java
 *
 * Created on October 30, 2002, 11:32 PM
 */

/**
 *
 * @author  Administrator
 */
package twcore.bots.matchbot;

import twcore.core.BotAction;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FlagReward;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.events.ScoreReset;
import twcore.core.events.WeaponFired;
import twcore.core.util.Tools;

public class MatchLogger
{
	BotAction m_botAction;
	int m_fnMatchID, m_fnOrder;
	boolean m_active;
	String mySQLHost = "website";

	/** Creates a new instance of MatchLogger */
	public MatchLogger(BotAction botAction)
	{
		m_botAction = botAction;
		m_fnMatchID = 0;
		m_fnOrder = 0;
		m_active = false;
	}

	public void logEvent(WeaponFired event)
	{
		createLogRecord("weaponFired", "used a weapon", m_botAction.getPlayerName(event.getPlayerID()));
	}

	public void logEvent(Message event)
	{
		String cType = "", cName = m_botAction.getPlayerName(event.getPlayerID()), m = event.getMessage();

		int i = event.getMessageType();
		if (i == Message.ARENA_MESSAGE)
		{
			cType = "arena";
			cName = "";
			// if the arena message starts with C2S / S2C / IP: / PING: / LOSS: / TIME: / Bytes/Sec:
			// then set type to "info"
			if (m.startsWith("C2S")
				|| m.startsWith("S2C")
				|| m.startsWith("IP:")
				|| m.startsWith("Ping:")
				|| m.startsWith("LOSS:")
				|| m.startsWith("TIME:")
				|| m.startsWith("Bytes/Sec")
				|| m.startsWith("Player Score Reset"))
				cType = "info";
		};
		if (i == Message.PUBLIC_MESSAGE)
			cType = "public";
		if (i == Message.TEAM_MESSAGE)
			cType = "team";
		if (i == Message.OPPOSING_TEAM_MESSAGE)
			cType = "opposing team";
		if (i == Message.PRIVATE_MESSAGE)
			cType = "private";
		if (i == Message.CHAT_MESSAGE)
			cType = "chat " + event.getChatNumber();

		if (cName == null)
			cName = "";

		if (cType != "")
			createLogRecord(cType + " message", m, cName);
	};

	public void logEvent(ArenaJoined event)
	{
		createLogRecord("arenajoined", "joined arena", m_botAction.getBotName());
	};

	public void logEvent(FrequencyChange event)
	{
		String cName = m_botAction.getPlayerName(event.getPlayerID());
		createLogRecord("freqshipchange", "set in freq " + event.getFrequency(), cName);
	};

	public void logEvent(FrequencyShipChange event)
	{
		String cName = m_botAction.getPlayerName(event.getPlayerID());
		createLogRecord("freqshipchange", "set in ship " + event.getShipType() + " and freq " + event.getFrequency(), cName);
	};

	public void logEvent(FlagReward event)
	{
		createLogRecord("flagreward", event.getPoints() + " rewarded to freq " + event.getFrequency());
	};

	public void logEvent(FlagClaimed event)
	{
		createLogRecord("flagclaimed", "claimed the flag", m_botAction.getPlayerName(event.getPlayerID()));
	};

	public void logEvent(PlayerDeath event)
	{
		String cKiller = m_botAction.getPlayerName(event.getKillerID());
		String cKillee = m_botAction.getPlayerName(event.getKilleeID());
		createLogRecord("kill", "killed by " + cKiller + " (" + event.getKilledPlayerBounty() + ")", cKillee);
	};

	public void logEvent(PlayerLeft event)
	{
		createLogRecord("left", "left arena", m_botAction.getPlayerName(event.getPlayerID()));
	};

	public void logEvent(ScoreReset event)
	{
		String cName = m_botAction.getPlayerName(event.getPlayerID());
		if (event.getPlayerID() == -1)
			cName = "everybody";
		createLogRecord("scorereset", "score has been reset", cName);
	};

	public void createLogRecord(String cMessageType, String cMessage, String cPlayer)
	{
		if (m_active)
		{
			try
			{
				m_botAction.SQLBackgroundQuery(
					mySQLHost,
                    null,
					"INSERT INTO tblMatchLog(fnMatchID, fcMessageType, fcMessage, fcPlayer, fnOrder)"
						+ "VALUES("
						+ m_fnMatchID
						+ ", '"
						+ Tools.addSlashesToString(cMessageType)
						+ "', '"
						+ Tools.addSlashesToString(cMessage)
						+ "', "
						+ "'"
						+ Tools.addSlashesToString(cPlayer)
						+ "', "
						+ m_fnOrder
						+ "  )");
				m_fnOrder++;
			}
			catch (Exception e)
			{
				System.out.println("Error while trying to add logging records");
			};
		};
	};

	public void createLogRecord(String cMessageType, String cMessage)
	{
		createLogRecord(cMessageType, cMessage, "");
	};

	public void announce(String cMessage)
	{
		createLogRecord("MatchBot", cMessage, m_botAction.getBotName());
	};

	public void sendPrivateMessage(String name, String message, int soundCode)
	{
		createLogRecord("doing private message", message, name);
		m_botAction.sendPrivateMessage(name, message, soundCode);
	};

	public void sendPrivateMessage(String name, String message)
	{
		sendPrivateMessage(name, message, 0);
	};

	public void sendArenaMessage(String message, int soundCode)
	{
		announce("*arena " + message);
		m_botAction.sendArenaMessage(message, soundCode);
	};

	public void sendArenaMessage(String message)
	{
		sendArenaMessage(message, 0);
	};

	public void doubleSpec(String name)
	{
		announce("Speccing " + name);
		m_botAction.spec(name);
		m_botAction.spec(name);
	};

	public void setShip(String name, int ship)
	{
		announce("Putting " + name + " in ship " + ship);
		m_botAction.setShip(name, ship);
	};

	public void setFreq(String name, int freq)
	{
		announce("Putting " + name + " in freq " + freq);
		m_botAction.setFreq(name, freq);
	};

	public void setFreqAndShip(String name, int freq, int ship)
	{
		announce("Putting " + name + " in ship " + ship + " and freq " + freq);
		m_botAction.setShip(name, ship);
		m_botAction.setFreq(name, freq);
	};

	public void scoreReset(String name)
	{
		announce("Resetting score of " + name);
		m_botAction.scoreReset(name);
	};

	public void scoreResetAll()
	{
		announce("Resetting score of everybody");
		m_botAction.scoreResetAll();
	};

	public void specAndSetFreq(String name, int freq)
	{
		announce("Speccing " + name + " and putting player into freq " + freq);
		m_botAction.spec(name);
		m_botAction.spec(name);
		m_botAction.setFreq(name, freq);
	};

	public void setDoors(int n)
	{
		announce("Setting doors = " + n);
		m_botAction.setDoors(n);
	};

	public void shipReset(String name)
	{
		announce("Resetting ship of " + name);
		m_botAction.shipReset(name);
	};

	public void shipResetAll()
	{
		announce("Resetting ship of everybody");
		m_botAction.shipResetAll();
	};

	public void resetFlagGame()
	{
		announce("*flagreset");
		m_botAction.resetFlagGame();
	};

	public void activate(int nMatchID)
	{
		m_fnMatchID = nMatchID;
		m_active = true;
	};

	public void deactivate()
	{
		m_active = false;
	};
}
