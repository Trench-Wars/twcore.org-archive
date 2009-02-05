package twcore.bots.twl;

/*
 * MatchTeam.java
 *
 * Created on August 19, 2002, 10:27 PM
 */

/**
 *
 * @author  Administrator
 */

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.events.WeaponFired;
import twcore.core.game.Player;
import twcore.core.stats.DBPlayerData;
import twcore.core.util.Tools;

public class MatchTeam
{

    // MatchTeam is a team during one round.
    // So it contains players as well as the result of the round of that team
    boolean useDatabase;

    Connection m_connection;
    BotAction m_botAction;
    MatchLogger m_logger;

    BotSettings m_rules;
    MatchRound m_round;

    String dbConn = "website";

    String m_fcTeamName;
    int m_fnTeamID;
    int m_fnTeamNumber;
    int m_fnFrequency;
    int m_fnSubstitutes;
    int m_fnShipSwitches;
    int m_fnShipChanges;

    // 0 - no forfeit, 1 - forfeitwin, 2 - forfeitloss
    int m_fnForfeit;

	boolean m_addPlayer = false;
    boolean m_turn = false;
    boolean m_blueoutState = false;

    LinkedList<MatchPlayer> m_players;
    LinkedList<String> m_captains;

    boolean m_fbReadyToGo;

    String m_fcLaggerName;

    TimerTask m_substituteDelay;
    String plA = "-ready-", plB = "-ready-", nme = "-ready-";

    //time race variables
    private boolean m_flagOwned = false;
    private int m_teamTime = 0;

    private boolean m_threeMinuteWarning = true;

    private boolean m_oneMinuteWarning = true;

    /** Creates a new instance of MatchTeam */
    public MatchTeam(String fcTeamName, int fnFrequency, int fnTeamNumber, MatchRound Matchround)
    {
        useDatabase = false;
        m_round = Matchround;
        m_botAction = m_round.m_botAction;
        m_rules = m_round.m_rules;
        m_logger = m_round.m_logger;
        m_fcTeamName = fcTeamName;
        m_players = new LinkedList<MatchPlayer>();
        m_captains = new LinkedList<String>();
        m_fnFrequency = fnFrequency;
        m_fbReadyToGo = false;
        m_fnSubstitutes = 0;
        m_fnShipChanges = 0;
        m_fnShipSwitches = 0;
        m_fnTeamNumber = fnTeamNumber;

        if (fnTeamNumber == 1)
            m_fnTeamID = m_round.m_game.m_fnTeam1ID;
        else
            m_fnTeamID = m_round.m_game.m_fnTeam2ID;

        if (m_rules.getInt("rosterjoined") == 1)
        {
            populateCaptainList();
        };
    }

    // saves player data
    public void storePlayerResults()
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();

        while (i.hasNext())
        {
            i.next().storePlayerResult(m_round.m_fnMatchRoundID, m_fnTeamNumber);
        };
    };

    // retrieves captains
    public void populateCaptainList()
    {
        try
        {
            ResultSet rs =
                m_botAction.SQLQuery(
                    dbConn,
                    "SELECT DISTINCT tblUser.fcUserName FROM tblUser, tblTeamUser, tblUserRank WHERE "
                        + "tblUser.fnUserID = tblTeamUser.fnUserID AND tblTeamUser.fnCurrentTeam = 1 "
                        + "AND tblTeamUser.fnTeamID = "
                        + m_fnTeamID
                        + " AND tblUser.fnUserID = tblUserRank.fnUserID "
                        + "AND tblUser.fnUserID = tblUserRank.fnUserID AND tblUserRank.fnRankID IN (3,4) ORDER BY tblUser.fcUserName");

            m_captains = new LinkedList<String>();

            while (rs.next())
            {
                m_captains.add(rs.getString("fcUserName").toLowerCase());
            };

        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        };
    };

    /**
     * Can get various weapon info and the player who used it
     * Get repel used count
     *
     * @param event WeaponFired event
     */
    public void handleEvent(WeaponFired event)
    {
        String playerName = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();
        MatchPlayer p = getPlayer(playerName);

        if (event.getWeaponType() == WeaponFired.WEAPON_REPEL)
            p.reportRepelUsed();

    }

    // when somebody lags out
    public void handleEvent(FrequencyShipChange event)
    {
        String playerName = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();

        MatchPlayer p = getPlayer(playerName);
        if ((event.getShipType() == 0) && (p.getPlayerState() == MatchPlayer.IN_GAME))
        {
            if (m_round.m_fnRoundState < 4) {
                sendPrivateMessageToCaptains(playerName + " lagged out or manually specced", 13);

                if (m_rules.getInt("deaths") != -1 && m_round.m_fnRoundState == 3)
                {
					if (!p.getLagByBot())
					{
	                    m_botAction.sendArenaMessage(playerName + " has changed to spectator mode - +1 death");
		                p.reportDeath();
					}
					else
					{
						p.setLagByBot(false);
					}
                }
			}
            p.lagout(false);
        };
    };

    // when somebody dies
    public void handleEvent(PlayerDeath event)
    {
        if (m_round.m_fnRoundState == 3)
        {
            try
            {
                String playerName = m_botAction.getPlayer(event.getKilleeID()).getPlayerName();
                MatchPlayer p = getPlayer(playerName);
                p.reportDeath();
            }
            catch (Exception e)
            {
            };
        };
    };

    // not officially an event, but it's treated like one.
    public void reportKill(PlayerDeath event)
    {
        if (m_round.m_fnRoundState == 3)
        {
            try
            {
                String playerName = m_botAction.getPlayer(event.getKillerID()).getPlayerName();
                MatchPlayer p = getPlayer(playerName);
                p.reportKill(event.getKilledPlayerBounty(), event.getKilleeID());
            }
            catch (Exception e)
            {
                Tools.printStackTrace(e);
            };
        };
    };

    // when somebody disconnects
    public void handleEvent(PlayerLeft event)
    {
        String playerName = m_botAction.getPlayer(event.getPlayerID()).getPlayerName();
        MatchPlayer p = getPlayer(playerName);
        if ((m_round.m_fnRoundState < 4) && (p.getPlayerState() == MatchPlayer.IN_GAME)) {
            sendPrivateMessageToCaptains(playerName + " lagged out or left the arena", 13);

            if (m_rules.getInt("deaths") != -1 && m_round.m_fnRoundState == 3) {
		m_botAction.sendArenaMessage(playerName + " has lagged out/left arena - +1 death");
	        p.reportDeath();
	    }
	}
        p.lagout(true);
    };

    public void handleEvent(Message event)
    {
        MatchPlayer matchPlayer;

                if( event.getMessageType() == Message.ARENA_MESSAGE ){
                    String msg = event.getMessage();
                    if( msg.indexOf( "Idle:" ) != -1 ){
                        String      name;
                        int         idleTime;
                        name = msg.substring( 0, msg.indexOf( ":" ) );

                        idleTime = getIdleTime( msg );
                        if( isPlayerOnTeam( name ) ){
                            sendPrivateMessageToCaptains( name + " has been idle for " + idleTime + " seconds." );
                        }
                    }

                    return;
                }

        for (int index = 0; index < m_players.size(); index++)
        {
            matchPlayer = m_players.get(index);
            matchPlayer.handleEvent(event);
        }
    }

    // show help messages
    public ArrayList<String> getHelpMessages(String name, boolean isStaff)
    {
        ArrayList<String> help = new ArrayList<String>();

        if ((isStaff) || (isCaptain(name)))
        {
            if (m_rules.getInt("captainfixed") == 0)
                help.add("!setcaptain <player>                     - changes captain to <player>. Note: there can be only 1 captain");
            if (m_round.m_fnRoundState == 1)
            {
                help.add("!list                                    - lists all players on this team");
                help.add("!add <player>:<ship>                     - adds player, <ship> only required for basing");
                help.add("!remove <player>                         - removes specified player");
                help.add("!switch <player>:<player>                - exchanges the ship of both players");
                help.add("!change <player>:<ship>                  - sets the player in the specified ship");
                help.add("!ready                                   - use this when you're done setting your lineup");
				help.add("!addplayer                               - request to add an extra player");
                help.add("!lagout <player>                         - puts <player> back in the game");
                if (m_rules.getInt("blueout") == 1)
                    help.add("!blueout                                 - enable/disable blueout");
            }
            else if (m_round.m_fnRoundState == 3)
            {
                help.add("!list                                    - lists all players on this team");
                help.add("!add <player>:<ship>                     - adds player, <ship> only required for basing");
				help.add("!addplayer                               - request to add an extra player");
                help.add("!lagout <player>                         - puts <player> back in the game");
                help.add("!sub <playerA>:<playerB>                 - substitutes <playerA> with <playerB>");
                if (m_rules.getInt("shipswitches") != 0)
                    help.add("!switch <player>:<player>                - exchanges the ship of both players");
                if (m_rules.getInt("shipchanges") != 0)
                    help.add("!change <player>:<ship>                  - sets the player in the specified ship");
                if (m_rules.getInt("blueout") == 1)
                    help.add("!blueout                                 - enable/disable blueout");
                //                help.add("!lagger <player>                         - make me check <player>'s lag again (in case his lag increased during the game)");
            };
        }
        else if (getPlayer(name) != null)
        {
            if (m_round.m_fnRoundState == 1)
            {
                help.add("!list                                    - lists all players on this team");
                help.add("!lagout                                  - puts you back in the game");
            }
            else if (m_round.m_fnRoundState == 3)
            {
                help.add("!list                                    - lists all players on this team");
                help.add("!lagout                                  - puts you back in the game");
            };
        };

        return help;
    };

    // Process commands given by a player
    public void parseCommand(String name, String command, String[] parameters, boolean isStaff)
    {
        try
        {
            if ((isStaff) || (isCaptain(name)))
            {
                if (m_rules.getInt("captainfixed") == 0)
                    if (command.equals("!setcaptain"))
                        command_setcaptain(name, parameters);
                if (m_round.m_fnRoundState == 1)
                {
                    if (command.equals("!list"))
                        command_list(name, parameters);
                    if (command.equals("!add"))
                        command_add(name, parameters);
                    if (command.equals("!addplayer"))
                        command_addplayer(name, parameters);
                    if (command.equals("!remove"))
                        command_remove(name, parameters);
                    if (command.equals("!switch"))
                        command_switch(name, parameters);
                    if (command.equals("!change"))
                        command_change(name, parameters);
                    if (command.equals("!ready"))
                        command_ready(name, parameters);
                    if (command.equals("!lagout"))
                        command_lagout(name, parameters);
                    if (command.equals("!blueout"))
                        command_blueout(name, parameters);
                }
                else if (m_round.m_fnRoundState == 2)
                {
                    if (command.equals("!blueout"))
                        command_blueout(name, parameters);
                    if (command.equals("!addplayer"))
                        command_addplayer(name, parameters);
                }
                else if (m_round.m_fnRoundState == 3)
                {
                    if (command.equals("!add"))
                        command_add(name, parameters);
                    if (command.equals("!addplayer"))
                        command_addplayer(name, parameters);
                    if (command.equals("!list"))
                        command_list(name, parameters);
                    if (command.equals("!lagout"))
                        command_lagout(name, parameters);
                    if (command.equals("!sub"))
                        command_sub(name, parameters);
                    if (command.equals("!switch"))
                        command_switch(name, parameters);
                    if (command.equals("!change"))
                        command_change(name, parameters);
                    if (command.equals("!blueout"))
                        command_blueout(name, parameters);
                    //                    if (command.equals("!lagger")) command_lagger(name, parameters);
                };
            }
            else if (getPlayer(name) != null)
            {
                if (command.equals("!list"))
                    command_list(name, parameters);
                if ((command.equals("!lagout")) && (parameters.length == 0))
                    command_lagout(name, parameters);
            };
        }
        catch (Exception e)
        {
        };

    }


;

    // sets the captain
    public void command_setcaptain(String name, String[] parameters)
    {
        // requirements:
        // v captainfixed should be 0
        // v captain should not be a captain or player of the other team
        // v captain should not be captain already
        // v captain should be in the arena
        // v captain should be on the squad if squadjoined=1
        String newCapt;

        MatchTeam otherTeam = m_round.getOtherTeam(getFrequency());

        if ((m_rules.getInt("captainfixed") == 0) && (m_rules.getInt("rosterjoined") == 0))
        {
            if (parameters.length == 1)
            {
                newCapt = parameters[0];
                Player p;
                p = m_botAction.getPlayer(newCapt);
                if( p == null )
                    p = m_botAction.getFuzzyPlayer(newCapt);
                newCapt = p.getPlayerName();
                if (p != null)
                {
                    if (!isCaptain(newCapt))
                    {
                        if ((m_rules.getInt("squadjoined") == 0) || ((m_rules.getInt("squadjoined") == 1) && (p.getSquadName().equalsIgnoreCase(m_fcTeamName))))
                        {
                            if (otherTeam.getPlayer(newCapt) == null)
                            {
                                if (!otherTeam.isCaptain(newCapt))
                                {
                                    if (m_captains.size() == 0)
                                        m_captains.add(p.getPlayerName().toLowerCase());
                                    else
                                        m_captains.set(0, p.getPlayerName().toLowerCase());
                                    m_logger.sendArenaMessage(p.getPlayerName() + " assigned as captain for " + getTeamName());
                                }
                                else
                                    m_logger.sendPrivateMessage(name, "Player is captain of the other team");
                            }
                            else
                                m_logger.sendPrivateMessage(name, "Player is in the other team");
                        }
                        else
                            m_logger.sendPrivateMessage(name, "Player isn't squadjoined");
                    }
                    else
                        m_logger.sendPrivateMessage(name, "Player is captain already");
                }
                else
                    m_logger.sendPrivateMessage(name, "Player is not in this arena");
            }
            else
                m_logger.sendPrivateMessage(name, "Specify the name of the new captain");
        }
        else
            m_logger.sendPrivateMessage(name, "You cannot set other captains");

    };

    // adds a player to the team (ship specified)
    public void command_add(String name, String[] parameters)
    {
        try
        {
            String answer;
            int fnShip = m_rules.getInt("ship");
            if ((fnShip == 0) && (parameters.length == 2))
                fnShip = Integer.parseInt(parameters[1]);
            if (fnShip != 0)
            {
                Player p;
                p = m_botAction.getPlayer(parameters[0]);
                if( p == null )
                    p = m_botAction.getFuzzyPlayer(parameters[0]);
                parameters[0] = p.getPlayerName();
                answer = addPlayer(p.getPlayerName(), fnShip, true, false);
                if (answer.equals("yes"))
                {
                    m_logger.sendPrivateMessage(name, "Player " + p.getPlayerName() + " added to " + m_fcTeamName);
                    m_logger.sendPrivateMessage(p.getPlayerName(), "You've been put in the game");

                    m_botAction.sendUnfilteredPrivateMessage( p.getPlayerName(), "*einfo" );
                    if (m_rules.getInt("pickbyturn") == 1)
                    {
                        m_turn = !m_turn;
                        m_round.determineNextPick();
                    };
                }
                else
                {
                    m_logger.sendPrivateMessage(name, "Could not add player " + parameters[0] + ": " + answer);
                };
            }
            else
                m_logger.sendPrivateMessage(name, "Specify ship, for example: !add Sphonk:3 to set the player is a spider");
        }
        catch (Exception e)
        {
            m_logger.sendPrivateMessage(name, "Could not add player " + parameters[0] + ": unknown error in command_add (" + e.getMessage() + ")");
        };
    };

	public void command_addplayer(String name, String[] parameters)
	{
		if (!m_addPlayer)
		{
			if (m_round.m_game.getPlayersNum() < m_rules.getInt("players"))
			{
				m_addPlayer = true;
				if (!m_round.checkAddPlayer(m_fcTeamName))
				{
					m_botAction.sendPrivateMessage(name, "Your request of adding an extra player has been sent to opposing team.");
				}
			}
			else {
				m_botAction.sendPrivateMessage(name, "The game already has maximum # of players.");
			}
		}
	}

    // removes a player from the team
    public void command_remove(String name, String[] parameters)
    {
        try
        {
            MatchPlayer p;

            if (parameters.length == 0)
            {
                m_logger.sendPrivateMessage(name, "Specify player");
                return;
            };

            p = getPlayer(parameters[0]);

            if (p != null)
            {
                p.getOutOfGame();
                m_players.remove(p);
                m_logger.sendPrivateMessage(name, "Player removed from the game");

                if (m_rules.getInt("pickbyturn") == 1)
                {
                    m_round.determineNextPick();
                };

                return;
            };
            m_logger.sendPrivateMessage(name, "Player not found");
        }
        catch (Exception e)
        {
            m_logger.sendPrivateMessage(name, "Could not remove player, unknown error in command_remove (" + e.getMessage() + ")");
        };
    };

    // lists all current players
    public void command_list(String name, String[] parameters)
    {
        MatchPlayer p;
        String answ;
        int i, j;

        // specify a comparator on how to sort the list
        // sort order: player state, ship number, player name
        Comparator<MatchPlayer> a = new Comparator<MatchPlayer>()
        {
            public int compare(MatchPlayer pa, MatchPlayer pb)
            {
                if (pa.m_fnPlayerState < pb.m_fnPlayerState)
                    return -1;
                else if (pa.m_fnPlayerState > pb.m_fnPlayerState)
                    return 1;
                else if (pa.getShipType() < pb.getShipType())
                    return -1;
                else if (pa.getShipType() > pb.getShipType())
                    return 1;
                else if (pb.getPlayerName().compareTo(pa.getPlayerName()) < 0)
                    return 1;
                else
                    return 0;
            };
        };

        // use the comparator
        MatchPlayer[] players = m_players.toArray(new MatchPlayer[m_players.size()]);
        Arrays.sort(players, a);

        // show the sorted list
        m_logger.sendPrivateMessage(name, m_fcTeamName + ":");

        answ = "";
        for (i = 0; i < players.length; i++)
        {
            p = players[i];
            if (p.getPlayerName().length() > 15)
                answ = answ + p.getPlayerName().substring(0, 15);
            else
                answ = answ + p.getPlayerName();
            for (j = 0; j < (15 - p.getPlayerName().length()); j++)
                answ = answ + " ";
            answ = answ + "- ";
            answ = answ + p.getPlayerStateName();
            for (j = 0; j < (12 - p.getPlayerStateName().length()); j++)
                answ = answ + " ";
            answ = answ + "- " + p.getShipType();
            if (i % 2 == 1)
            {
                m_logger.sendPrivateMessage(name, answ);
                answ = "";
            }
            else
                answ = answ + "          ";
        };
        if (!answ.equals(""))
            m_logger.sendPrivateMessage(name, answ);
    };

    // switch player
    public void command_switch(String name, String[] parameters)
    {
        MatchPlayer pA, pB;

        if ((m_fnShipSwitches < m_rules.getInt("shipswitches")) || (m_rules.getInt("shipswitches") == -1))
        {
            if (parameters.length == 2)
            {
                pA = getPlayer(parameters[0]);
                pB = getPlayer(parameters[1]);
                if (pA != null)
                {
                    if (pA.isAllowedToPlay())
                    {
                        if (pB != null)
                        {
                            if (pB.isAllowedToPlay())
                            {
                                int ship = pB.getShipType();
                                pB.setShip(pA.getShipType());
                                pA.setShip(ship);

                                //this indicates that the player has switched ships during the game
                                //currently it voids the player from getting mvp in time race games
                                pA.m_switchedShip = true;
                                pB.m_switchedShip = true;

                                m_logger.sendArenaMessage(
                                    pA.m_fcPlayerName + " (" + pB.getShipType() + ") and " + pB.m_fcPlayerName + " (" + pA.getShipType() + ") switched ships.");
                                if (m_round.m_fnRoundState == 3)
                                {
                                    m_fnShipSwitches++;
                                    if (m_rules.getInt("shipswitches") != -1)
                                        m_logger.sendPrivateMessage(name, "You have " + (m_rules.getInt("shipswitches") - m_fnShipSwitches) + " shipswitches left");
                                };
                            }
                            else
                                m_logger.sendPrivateMessage(name, pB.getPlayerName() + " is not in the game (subbed or is out)");
                        }
                        else
                            m_logger.sendPrivateMessage(name, pB.getPlayerName() + " is not on your team");
                    }
                    else
                        m_logger.sendPrivateMessage(name, pA.getPlayerName() + " is not in the game (subbed or is out)");
                }
                else
                    m_logger.sendPrivateMessage(name, pA.getPlayerName() + " is not on your team");
            }
            else
                m_logger.sendPrivateMessage(name, "Specify the players to switch ships with");
        }
        else
            m_logger.sendPrivateMessage(name, "There are no more switches allowed");
    };

    // changes ship
    public void command_change(String name, String[] parameters)
    {
        MatchPlayer pA;
        int newShip;

        if ((m_fnShipChanges < m_rules.getInt("shipchanges")) || (m_rules.getInt("shipchanges") == -1))
        {
            if (parameters.length == 2)
            {
                pA = getPlayer(parameters[0]);
                try
                {
                    newShip = Integer.parseInt(parameters[1]);
                }
                catch (Exception e)
                {
                    newShip = 0;
                };
                if (pA != null)
                {
                    if (pA.isReadyToPlay())
                    {
                        if ((newShip >= 1) && (newShip <= 8))
                        {
                            // rules.ship should be 0 (allow any), and rules.maxship<#> shouldn't be -1
                            if ((m_rules.getInt("ship") == 0) && (m_rules.getInt("maxship" + newShip) != -1))
                            {

                                // on round state 3, all maxima and minima should be checked
                                if ((m_round.m_fnRoundState == 3) || (m_round.m_fnRoundState == 1))
                                {
                                    int oldShip = pA.getShipType();
                                    // when the player leaves that ship, does it get under the minimum?
                                    if ((m_rules.getInt("minship" + oldShip) == 0) || (m_rules.getInt("minship" + oldShip) < getPlayersRosteredInShip(oldShip)))
                                    {
                                        // when the player enters the new ship, does it get over the maximum?
                                        if ((m_rules.getInt("maxship" + newShip) == 0) || (m_rules.getInt("maxship" + newShip) > getPlayersRosteredInShip(newShip)))
                                        {
                                            pA.setShip(newShip);

                                            //this indicates that the player has switched ships during the game
                                            //currently it voids the player from getting mvp in time race games
                                            pA.m_switchedShip = true;

                                            m_logger.sendArenaMessage(pA.m_fcPlayerName + " changed from ship " + oldShip + " to ship " + newShip);
                                            if ((m_rules.getInt("shipchanges") != -1) && (m_round.m_fnRoundState == 3))
                                            {
                                                m_fnShipChanges++;
                                                m_logger.sendPrivateMessage(name, "You have " + (m_rules.getInt("shipchanges") - m_fnShipChanges) + " shipchanges left");
                                            };
                                        }
                                        else
                                            m_logger.sendPrivateMessage(name, "Can't change ship, you are at the maximum amount of ship " + newShip);
                                    }
                                    else
                                        m_logger.sendPrivateMessage(name, "Can't change ship, you are at the minimum amount of ship " + oldShip);
                                }
                                else
                                    m_logger.sendPrivateMessage(name, "Can't shipchange at this point in the game");
                            }
                            else
                                m_logger.sendPrivateMessage(name, "That ship is not allowed");
                        }
                        else
                            m_logger.sendPrivateMessage(name, "Give a valid ship number");
                    }
                    else
                        m_logger.sendPrivateMessage(name, "Player is not in the game (lagged, subbed or is out)");
                }
                else
                    m_logger.sendPrivateMessage(name, "Player is not on this team");
            }
            else
                m_logger.sendPrivateMessage(name, "Specify the player to be shipchanged");
        }
        else
            m_logger.sendPrivateMessage(name, "There are no more changeships allowed");
    };

    // ready, toggles 'm_fbReadyToGo'
    public void command_ready(String name, String[] parameters)
    {
        // team should have >= minPlayers, <= players
        if (!m_fbReadyToGo)
        {
            // does the team meet the requirements to start:
            String message = isAllowedToBegin();
            if (message.equals("yes"))
            {
                m_fbReadyToGo = true;
                m_logger.sendArenaMessage(m_fcTeamName + " is ready to begin");

		if (m_rules.getInt("manual_game_start") == 0)
	                m_round.checkReadyToGo();
            }
            else
                m_logger.sendPrivateMessage(name, message);
        }
        else
        {
            m_fbReadyToGo = false;
            m_logger.sendArenaMessage(m_fcTeamName + " is NOT ready to begin");
        };
    };

    // puts a player back in the game, IF ALLOWED TO
    public void command_lagout(String name, String[] parameters)
    {
        String lagger, message;
        MatchPlayer p;
        boolean commandByOther = true;

        // does the player want to put himself, or someone else back in the game?
        if (parameters.length == 0)
        {
            lagger = name;
            commandByOther = false;
        }
        else
            lagger = parameters[0];

        p = getPlayer(lagger);
        if (p != null && (m_rules.getInt("rosterjoined") == 0 || getTeamName().equalsIgnoreCase(m_botAction.getPlayer(lagger).getSquadName())))
        {
            // put player back in, returns message to report if it's succesful
            message = p.lagin();
            if (message.equals("yes"))
            {
                if (commandByOther)
                    m_logger.sendPrivateMessage(name, "Player is back in, " + p.getLagoutsLeft() + " lagouts left");
            }
            else
            {
                // if not succesful, inform either the host/captain or the player himself:
                if (commandByOther)
                    m_logger.sendPrivateMessage(name, "Couldn't put player back in: " + message);
                else
                    m_logger.sendPrivateMessage(name, "Couldn't put you back in: " + message);
            };
        }
        else
            m_logger.sendPrivateMessage(name, "Player isn't in the game");

    };

    // substitutes a player with another
    public void command_sub(String name, String[] parameters)
    {
        /* definitions:
         * v m_fnSubstitutes < rules.substitutes or rules.substitutes == -1
         * v playerA to be substituted
         * v playerB to be the substite
         * ----
         * v m_round.m_fnRoundState should be (3 - playing)
         * v playerA should be in the m_players list
         * v playerA should either be (1 - in game) or (3 - lagged)
         * v playerB should be in the arena
         * when playerB is not registered:
         * v call command_addp from here
         * when playerB is registered:
         * v playerB should be either (2 - substituted) or ( 0 - not in game)
         */

        // check if the given substitute command is legal

        // if subdelaytime > 0 then create a timertask to call the substitute routine in subdelaytime seconds

        String playerA, playerB;
        MatchPlayer pA = null;
        int subdelaytime;

        if ((m_fnSubstitutes < m_rules.getInt("substitutes")) || (m_rules.getInt("substitutes") == -1))
        {
            if (parameters.length == 2)
            {
                playerA = parameters[0];
                playerB = parameters[1];

                if (m_round.m_fnRoundState == 3)
                {
                    pA = getPlayer(playerA);
                    if (pA != null)
                    {
                        subdelaytime = m_rules.getInt("subdelaytime");
                        if (subdelaytime > 5)
                            subdelaytime = 5;
                        
                        // Randomize delay with +(0-3) seconds
                        subdelaytime = subdelaytime + Math.round(new Float(Math.random()*3));
                        
                        if (subdelaytime > 0)
                        {
                            m_logger.sendPrivateMessage(name, "Your substitute request will be processed in a few seconds");
                            // if the timertask isn't busy then create timertask
                            if ((nme == "-ready-") && (plA == "-ready-") && (plB == "-ready-"))
                            {
                                nme = name;
                                plA = playerA;
                                plB = playerB;
                                m_substituteDelay = new TimerTask()
                                {
                                    public void run()
                                    {
                                        dosubstitute(nme, plA, plB);
                                        nme = "-ready-";
                                        plA = "-ready-";
                                        plB = "-ready-";
                                    };
                                };
                                m_botAction.scheduleTask(m_substituteDelay, subdelaytime * 1000);
                            }
                            else
                                m_logger.sendPrivateMessage(name, "There's already a substitute request being processed, please wait...");

                        }
                        else
                            dosubstitute(name, playerA, playerB);
                    }
                    else
                        m_logger.sendPrivateMessage(name, playerA + " doesn't play for your team");
                }
                else
                    m_logger.sendPrivateMessage(name, "Game hasn't started yet");
            }
            else
                m_logger.sendPrivateMessage(name, "Specify the player to be substituted, and the substitute");
        }
        else
            m_logger.sendPrivateMessage(name, "There are no more substitutes allowed");
        if (pA != null)
            pA.setAboutToBeSubbed(false);
    };

    public void dosubstitute(String name, String playerA, String playerB)
    {
        String answer;
        MatchPlayer pA = null, pB;
        Player ppB;

        if ((m_fnSubstitutes < m_rules.getInt("substitutes")) || (m_rules.getInt("substitutes") == -1))
        {
            if (m_round.m_fnRoundState == 3)
            {
                pA = getPlayer(playerA);
                if (pA != null)
                {
                    pA.setAboutToBeSubbed(true);
                    if ((pA.getPlayerState() == 1) || (pA.getPlayerState() == 3))
                    {
                        ppB = m_botAction.getFuzzyPlayer(playerB);
                        if (ppB != null)
                        {
                            pB = getPlayer(ppB.getPlayerName());

                            // If playerB isn't in the arena.
                            if (pB == null)
                            {
                                answer = addPlayer(ppB.getPlayerName(), pA.getShipType(), false, true);
                                if (answer.equals("yes"))
                                    pB = getPlayer(ppB.getPlayerName());
                                else
                                    m_logger.sendPrivateMessage(name, "Could not add player " + playerB + ": " + answer);
                            };

                            // if the adding of playerB didn't fail:
                            if (pB != null)
                            {
                                if ((pB.getPlayerState() == 0) || (pB.getPlayerState() == 2))
                                {
                                    int subDeathsLeft = pA.getSpecAt() - pA.getActualDeaths();
                                    pA.reportSubstituted();
                                    pB.substitute(subDeathsLeft);
                                    pB.setShip(pA.getShipType());
                                    m_logger.sendPrivateMessage(pB.getPlayerName(), "You are subbed in the game");
                                    m_fnSubstitutes++;
                                    if (m_rules.getInt("deaths") == -1)
                                        m_logger.sendArenaMessage(pA.getPlayerName() + " has been substituted by " + pB.getPlayerName());
                                    else
                                        m_logger.sendArenaMessage(
                                            pA.getPlayerName() + " has been substituted by " + pB.getPlayerName() + ", with " + subDeathsLeft + " deaths left");

                                    if (m_rules.getInt("substitutes") != -1)
                                        m_logger.sendPrivateMessage(name, "You have " + (m_rules.getInt("substitutes") - m_fnSubstitutes) + " substitutes left");
                                }
                                else
                                    m_logger.sendPrivateMessage(name, pB.getPlayerName() + " is already in the game");
                            };

                        }
                        else
                            m_logger.sendPrivateMessage(name, playerB + " isn't in the arena");
                    }
                    else
                        m_logger.sendPrivateMessage(name, pA.getPlayerName() + " isn't in the game");
                    pA.setAboutToBeSubbed(false);
                }
                else
                    m_logger.sendPrivateMessage(name, playerA + " doesn't play for your team");
            }
            else
                m_logger.sendPrivateMessage(name, "Game hasn't started yet");
        }
        else
            m_logger.sendPrivateMessage(name, "There are no more substitutes allowed");
        if (pA != null)
            pA.setAboutToBeSubbed(false);
    }

    public void command_blueout(String name, String[] parameters)
    {
        if (m_rules.getInt("blueout") == 1)
        {
            m_blueoutState = !m_blueoutState;
            if (m_blueoutState == true)
            {
                if (m_round.m_blueoutState != 1)
                    m_round.requestBlueout(m_blueoutState);
                else
                    m_logger.sendPrivateMessage(name, "Blueout has already been enabled");

                if (m_round.m_fnRoundState < 2)
                    m_logger.sendPrivateMessage(name, "Blueout will be enabled as soon as the game begins");
            }
            else
            {
                if (m_round.getOtherTeam(m_fnFrequency).getBlueoutState() == false)
                    m_round.requestBlueout(m_blueoutState);
                m_logger.sendPrivateMessage(name, "If the other team also requested to turn off blueout, blueout will be taken off.");
            };

        };
    };

    // warpto (safe spots in this case)
    public void warpTo(int x, int y)
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();

        while (i.hasNext())
        {
            i.next().warpTo(x, y);
        };
    };

    // playerallowedtoplay - checks several requirements
    public String playerAllowedToPlay(String name, int ship)
    {
        Player p;
        int playersAvail;

        // is it the turn of this freq to pick a player?
        if ((m_round.m_fnRoundState == 1)
            && (m_rules.getInt("pickbyturn") == 1)
            && (m_turn == false)
            && (m_round.getOtherTeam(m_fnFrequency).getPlayersRostered() - getPlayersRostered() <= 0))
        {
            return "The other team currently has the turn to pick a ship";
        };

        // does the player want to be picked
        if (m_round.m_notPlaying.indexOf(name.toLowerCase()) != -1)
        {
            return "player can't or doesn't want to play this round";
        };

        // when rosterjoined=1, has to exist in the Roster database
        if (m_rules.getInt("rosterjoined") == 1)
        {
            DBPlayerData dbP = new DBPlayerData(m_botAction, dbConn, name);
            if (!m_fcTeamName.equalsIgnoreCase(dbP.getTeamName()))
                return "Player isn't on the squad roster";
            //            if(isDoubleSquadding(name))
            //                return "Player is not elligible to play because he / she is double squadding.";
            // if eligibleafter specified, player has to be on the roster for x days
            if (m_rules.getInt("eligibleafter") > 0)
            {
                GregorianCalendar today = new GregorianCalendar();
                GregorianCalendar signup = new GregorianCalendar();
                signup.setTime(dbP.getTeamSignedUp());
                signup.add(GregorianCalendar.DATE, m_rules.getInt("eligibleafter"));
                double msDiff = (today.getTimeInMillis() - signup.getTimeInMillis()) / 1000 / 60 / 60;
                if (msDiff < 0)
                    return "Player isn't eligible yet, he will be eligible " + (-msDiff) + " hours";
            };

	    // only for TWL games
	    if (m_rules.getInt("matchtype") < 4)
            {
		try {
			ResultSet s = m_botAction.SQLQuery(dbConn, "SELECT tblTWL__LockDate.fdTWL__LockDate AS lockDate FROM tblTWL__LockDate, tblTeamUser WHERE tblTWL__LockDate.fcTWL__LockType = 'hard' AND tblTeamUser.fnUserID = '" + dbP.getUserID() + "' AND tblTeamUser.fdJoined < tblTWL__LockDate.fdTWL__LockDate;");
			if (!s.next()) {
			    return "Player was rostered after the roster lock and is ineligible for TWL games";
			}
			ResultSet s2 = m_botAction.SQLQuery(dbConn, "SELECT * FROM tblTWL__Player WHERE fnUserID = '" + dbP.getUserID() + "'");
			if (!s2.next()) {
			    return "Player is not rostered as a TWL player";
			}
		} catch (SQLException e) {
			return "Error: " + e.getMessage();
		}
	    }
        };

        // name should not start with "matchbot"
        if (name.toLowerCase().startsWith("matchbot"))
        {
            return "Playername should not start with 'matchbot'";
        };

        // name should not start with "robo ref"
        if (name.toLowerCase().startsWith("robo ref"))
        {
            return "Playername should not start with 'robo ref'";
        };

        // player should be in the arena
        p = m_botAction.getPlayer(name);
        if (p == null)
            return "Player is not in this arena";

        // there should be room in the team
        playersAvail = getPlayersIsWasInGame();
        if (playersAvail >= m_round.m_game.getPlayersNum())
            return "Team is full, maximum of " + m_round.m_game.getPlayersNum();

        // maximum number of that ship shouldn't be reached
        int maxShips = m_rules.getInt("maxship" + ship);
        if (maxShips != 0)
        {
            if (maxShips == -1)
                return "Ship " + ship + " is not allowed";
            else if (getPlayersRosteredInShip(ship) >= maxShips)
                return "The maximum number (" + maxShips + ") of ship " + ship + " has been reached already";
        };

        // player is not in this team, and not in the other

        if ((m_round.m_team1.getPlayer(name, true) != null) || (m_round.m_team2.getPlayer(name, true) != null))
            return "Player is already in either of the teams";

        // player should be squadjoined (when squadjoined == 1)
        if ((m_rules.getInt("squadjoined") == 1) && (!p.getSquadName().equalsIgnoreCase(m_fcTeamName)))
            return "Player should be squadjoined to the correct squad";

        // player shouldn't be captain of the other team
        if (m_round.getOtherTeam(getFrequency()).isCaptain(name))
            return "Player is captain of the other team";

        // player is in the right ship
        if (!((ship == m_rules.getInt("ship")) || (m_rules.getInt("ship") == 0)))
            return "invalid ship";

        if( m_rules.getInt("aliascheck") == 1 ) {
            // redudant action
            DBPlayerData dbP = new DBPlayerData(m_botAction, dbConn, name);

            // a name has to be registered
            if (!dbP.isRegistered()) {
				m_botAction.sendPrivateMessage(dbP.getUserName(), "Your name is not registered. You must send !register to TWDBot in ?go twd before you can play.");
                return "Player must register this name to play.  (Usage: !register to TWDBot in ?go twd)";
			}

            // the name must be enabled
            if (!dbP.isEnabled())
                return "Player's name is disabled.";
        }

        // do a lag check.

        return "yes";
    };

    public boolean isDoubleSquadding(String name)
    {
        /*      String aliasName;
              int dblSqdSensitivity = m_rules.getInt("dblSqdSensitivity");
              int dblSqdTime = m_rules.getInt("dblSqdDays");
              java.util.Date dblSqdDate = new java.util.Date(System.currentTimeMillis() - dblSqdTime * 24 * 60 * 60 * 1000);
              String dblSqdDateString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(dblSqdDate);

              try
              {
                ResultSet nameSet = m_botAction.SQLQuery(dbConn,
                  "SELECT A.* " +
                  "FROM `tblAlias` A, `tblUser` U " +
                  "WHERE U.fcUserName = \"" + name + "\" " +
                  "AND A.fnUserID = U.fnUserID " +
                  "AND A.fdUpdated >  \"" + dblSqdDateString + "\" " +
                  "ORDER BY `fnMachineID` DESC"
                );
                return false;
              }
              catch(SQLException e)
              {
                return false;
              }*/
        return false;
    }

    // isAllowedToStart
    public String isAllowedToBegin()
    {
        if (getPlayersReadyToPlay() >= m_rules.getInt("minplayers"))
        {
            // 2. the minimum amount of ships for each shiptype should be met
            return minimumShipAmountsMet();
        }
        else
            return "Team doesn't have enough players, need at least " + m_rules.getInt("minplayers");
    };

    // setReadyToGo
    public void setReadyToGo()
    {
        m_fbReadyToGo = true;
    };

    // forfeitWin
    public void forfeitWin()
    {
        m_fnForfeit = 1;
    };

    // forfeitLoss
    public void forfeitLoss()
    {
        m_fnForfeit = 2;
    };

    //
    public boolean isForfeit()
    {
        if (m_fnForfeit == 0)
            return false;
        else
            return true;
    };

    // addPlayer, including errormessages
    public String addPlayer(String playerName, int fnShip, boolean getInGame, boolean fbSilent)
    {
        try
        {
            String answer;
            if ((fnShip >= 1) && (fnShip <= 8))
            {
                Player p;
                p = m_botAction.getPlayer(playerName);
                if( p == null )
                    p = m_botAction.getFuzzyPlayer(playerName);
                answer = playerAllowedToPlay(p.getPlayerName(), fnShip);
                if (answer.equals("yes"))
                {
                    addPlayerFinal(p.getPlayerName(), fnShip, getInGame, fbSilent);
                    return "yes";
                }
                else
                    return answer;
            }
            else
                return "Ship " + fnShip + " is invalid";
        }
        catch (Exception e)
        {
                    e.printStackTrace();
            return "Could not add player, unknown error in addPlayer: " + e.getMessage();
        }
    };

    // adds a player to the team (finally)
    public void addPlayerFinal(String fcPlayerName, int fnShipType, boolean getInGame, boolean fbSilent)
    {
        MatchPlayer p;
        if (!useDatabase)
        {
            p = new MatchPlayer(fcPlayerName, this);
            p.setShipAndFreq(fnShipType, m_fnFrequency);
            if (getInGame)
                p.getInGame(fbSilent);
            m_players.add(p);
        };
    };

    // sets turn to true
    public void setTurn()
    {
        if (getPlayersRostered() < m_rules.getInt("players"))
        {
            m_turn = true;
            String andShip = "";
            if (m_rules.getInt("ship") == -1)
                andShip = " and specify ship";
            m_logger.sendArenaMessage(getTeamName() + ", pick a player" + andShip);
        };
    };

    // flagreward
    public void flagReward(int points)
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();

        while (i.hasNext())
             i.next().flagReward(points);

    };

    // checks if the team has players in-game
    public boolean isDead()
    {
        MatchPlayer p;
        if (m_round.m_fnRoundState != 3)
            return false;

        ListIterator<MatchPlayer> i = m_players.listIterator();
        int retval = 0;

        while (i.hasNext())
        {
            p = i.next();
            if ((p.getPlayerState() == MatchPlayer.IN_GAME)
                || ((p.getPlayerState() == MatchPlayer.LAGGED) && ((System.currentTimeMillis() - p.getLaggedTime()) <= m_rules.getInt("lagoutextension") * 1000)))
                retval++;
        };

        if (retval == 0)
            return true;
        else
            return false;
    };

    public boolean wonRace()
    {
        String winBy = m_rules.getString("winby");
        int raceTo = m_rules.getInt("points");

        if (m_round.m_fnRoundState != 3 || !winBy.equals("race") || raceTo < 0)
            return false;
        return getTeamScore() >= raceTo;
    }

    /**
     * Sets the flag control flag
     * Then it sets a timertask to countdown the remainder of the time to win
     * If team holds flag till that time and the timertask expires it updates
     * scores and checks if the team won.
     *
     * @author FoN
     */
    public void ownFlag(int playerID)
    {
        if (m_round.m_fnRoundState == 3 && m_flagOwned == false)
        {
            String playerName = m_botAction.getPlayer(playerID).getPlayerName();
            MatchPlayer p = getPlayer(playerName);
		if (p != null)      p.reportFlagClaimed();
            m_flagOwned = true;
        }
    }

    /**
     * Unset the flag control flag and updates score
     *
     * @author FoN
     */
    public void disownFlag()
    {
        if (m_round.m_fnRoundState == 3 && m_flagOwned == true)
        {
            m_flagOwned = false;
        }
    }

    /**
     * Method getTimeScore.
     *
     * @author FoN
     * @return int which returns the updated score if this team owns the flag
     */
    public int getTimeScore()
    {
            if (m_fnForfeit == 0)
                return m_teamTime;
            else if (m_fnForfeit == 1)
                return m_rules.getInt("forfeit_winner_score");
            else if (m_fnForfeit == 2)
                return m_rules.getInt("forfeit_loser_score");
            return 0;
    }

    /**
     * Method addTimePoint
     * This is called every sec by a repeated timer when the game starts
     *
     * @author FoN
     */
    public void addTimePoint()
    {
        if (m_flagOwned)
            m_teamTime += 1;

        if (((m_round.getRaceTarget() - m_teamTime) == 3 * 60) && m_threeMinuteWarning) //3 mins * 60 secs
        {
            m_logger.sendArenaMessage(getTeamName() + " needs 3 mins of flag time to win");
            m_threeMinuteWarning = false;
        }

        if (((m_round.getRaceTarget() - m_teamTime) == 1 * 60) && m_oneMinuteWarning) //1 mins * 60 secs
        {
            m_logger.sendArenaMessage(getTeamName() + " needs 1 minute of flag time to win");
            m_oneMinuteWarning = false;
        }

        if (m_teamTime >= m_round.getRaceTarget())
        {
            m_round.endGame();
        }
    }

    // searchers for player <name>. Returns NULL if not exists
    public MatchPlayer getPlayer(String name, boolean matchExact)
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();
        MatchPlayer answ, best = null;

        while (i.hasNext())
        {
            answ = i.next();

			if (answ.getPlayerName() != null) {
	            if ((!matchExact) && (answ.getPlayerName().toLowerCase().startsWith(name.toLowerCase())))
		            if (best == null)
			            best = answ;
				    else if (best.getPlayerName().toLowerCase().compareTo(answ.getPlayerName().toLowerCase()) > 0)
					    best = answ;

				if (answ.getPlayerName().equalsIgnoreCase(name))
		            return answ;
			}
        };
        return best;
    };

    public MatchPlayer getPlayer(String name)
    {
        return getPlayer(name, false);
    };

    // get MVP
    public MatchPlayer getMVP()
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();
        MatchPlayer best = null, rightnow;

        while (i.hasNext())
        {
            rightnow = i.next();
            if (best == null)
                best = rightnow;
            else if (rightnow.getPoints() > best.getPoints())
                best = rightnow;
        };

        return best;
    };

    // get team score
    public int getTeamScore()
    {
        String winby = m_rules.getString("winby").toLowerCase();

        if (m_fnForfeit == 0)
        {
            if (winby.equals("timerace"))
            {
                return getTimeScore();
            }

            if (winby.equals("score") || winby.equals("race"))
            {
                return getTotalScore();
            }
            else if (winby.equals("kills"))
            {
                //int deaths = m_round.getOtherTeam(m_fnFrequency).getTotalDeaths();
                // for players that weren't there, count their deaths too
                int otheramount = m_rules.getInt("players") - m_round.getOtherTeam(m_fnFrequency).getPlayersIsWasInGame();

                return m_round.getOtherTeam(m_fnFrequency).getTotalDeaths() + otheramount * m_rules.getInt("deaths");
            };
        }
        else if (m_fnForfeit == 1)
            return m_rules.getInt("forfeit_winner_score");
        else if (m_fnForfeit == 2)
            return m_rules.getInt("forfeit_loser_score");
        return 0;
    };

    // get total deaths
    public int getTotalDeaths()
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();
        int retval = 0;

        while (i.hasNext())
            retval = retval + i.next().getDeaths();

        return retval;
    };

    // get total score
    public int getTotalScore()
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();
        int retval = 0;

        while (i.hasNext())
            retval = retval + i.next().getScore();

        return retval;
    };

    public int getTotalLagOuts()
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();
        int retval = 0;

        while (i.hasNext())
            retval = retval + i.next().getLagOuts();

        return retval;
    };

    public int getDTotalStats(int sType)
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();
        int retval = 0;

        while (i.hasNext())
            retval = retval + i.next().getTotalStatistic(sType);

        return retval;
    }

    // get # ready to play players
    public int getPlayersReadyToPlay()
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();
        int retval = 0;

        while (i.hasNext())
            if (i.next().isReadyToPlay())
                retval++;

        return retval;
    };

    // get # ready to play players
    public int getPlayersRostered()
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();
        int retval = 0;

        while (i.hasNext())
            if (i.next().isAllowedToPlay())
                retval++;

        return retval;
    };

    public int getPlayersIsWasInGame()
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();
        int retval = 0;

        while (i.hasNext())
            if (i.next().isWasInGame())
                retval++;

        return retval;
    };

    // get # ready to play in ship #
    public int getPlayersRosteredInShip(int shipType)
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();
        MatchPlayer p;
        int retval = 0;

        while (i.hasNext())
        {
            p = i.next();
            if ((p.isAllowedToPlay()) && (p.getShipType() == shipType))
                retval++;
        };

        return retval;
    };

    // checks if all minimum numbers of ships are met
    public String minimumShipAmountsMet()
    {
        int minShips, curShips;
        for (int i = 1; i <= 8; i++)
        {
            minShips = m_rules.getInt("minship" + i);
            curShips = getPlayersRosteredInShip(i);
            if (curShips < minShips)
                return "You need at least " + minShips + " of type " + i + ", there are currently " + curShips;
        };
        return "yes";
    };

    // send start signal to all players
    public void signalStartToPlayers()
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();

        while (i.hasNext())
             i.next().reportStartOfGame();
    };

    // send end signal to all players
    public void signalEndToPlayers()
    {
        ListIterator<MatchPlayer> i = m_players.listIterator();

        while (i.hasNext())
             i.next().reportEndOfGame();
    };

    public void sendPrivateMessageToCaptains(String text)
    {
        sendPrivateMessageToCaptains(text, 0);
    };

    public void sendPrivateMessageToCaptains(String text, int soundCode)
    {
        ListIterator<String> i = m_captains.listIterator();
        while (i.hasNext())
        {
            m_logger.sendPrivateMessage(i.next(), text, soundCode);
        };
    };

    public void reportLaggerName(String name)
    {
        m_fcLaggerName = name;
    };

    public void reportLaggerLag(int ping)
    {
        if (m_fcLaggerName != null)
        {
            int maxPing = m_rules.getInt("maxping");
            if (maxPing == 0)
                maxPing = 1000;
            if (ping > maxPing)
            {
                m_logger.doubleSpec(m_fcLaggerName);
                m_logger.sendPrivateMessage(m_fcLaggerName, "your lag is too high to play in this game. Max ping: " + maxPing + ", your ping: " + ping);
            };
            m_fcLaggerName = null;
        };
    };

    public boolean isCaptain(String name)
    {
        if (m_captains.indexOf(name.toLowerCase()) >= 0)
            return true;
        else
            return false;
    };

    public String getCaptains()
    {
        ListIterator<String> i = m_captains.listIterator();
        String answ = "", temp;
        boolean isFirst = true;

        while (i.hasNext())
        {
            temp = i.next();
            if (m_botAction.getPlayer(temp) != null)
            {
                if (isFirst)
                    isFirst = false;
                else
                    answ = answ + ", ";
                answ = answ + temp;
            };
        };
        return answ;
    };

    public String getTeamName()
    {
        return m_fcTeamName;
    };
    public int getFrequency()
    {
        return m_fnFrequency;
    };
    public boolean isReadyToGo()
    {
        return m_fbReadyToGo;
    };
	public boolean addEPlayer()
	{
		return m_addPlayer;
	};
	public void setAddPlayer(boolean b)
	{
		m_addPlayer = b;
	};
    public boolean getBlueoutState()
    {
        return m_blueoutState;
    };

    public boolean isPlayerOnTeam( String name ){
        MatchPlayer player;
        ListIterator<MatchPlayer> i = m_players.listIterator();

        while( i.hasNext() ){
            player = i.next();
            if( player.getPlayerName().toLowerCase().compareTo( name.toLowerCase() ) == 0 ){
                return true;
            }
        }

        return false;
    }

    private int getIdleTime( String message ){
        int beginIndex = message.indexOf("Idle: ") + 6;
        int endIndex = message.indexOf(" s", beginIndex);

        if( beginIndex > -1 && endIndex > -1 ){
            return Integer.parseInt(message.substring(beginIndex, endIndex));
        } else {
            return 0;
        }
    }

    public ArrayList<String> getDScores(boolean duelG, boolean wbG) {

	ArrayList<String> out = new ArrayList<String>();

        Comparator<MatchPlayer> a = new Comparator<MatchPlayer>()
        {
            public int compare(MatchPlayer pa, MatchPlayer pb)
            {
                return pb.getPlayerName().compareToIgnoreCase(pa.getPlayerName());
            };
        };

        // use the comparator
        MatchPlayer[] players = m_players.toArray(new MatchPlayer[m_players.size()]);
        Arrays.sort(players, a);


	if (duelG) {
		if (wbG) {
		    out.add("|                          ,------+------+-----------+----+");
		    out.add("| " + Tools.formatString(m_fcTeamName, 23) + " /  " + rightenString(Integer.toString(getDTotalStats(20)), 4) + " | " + rightenString(Integer.toString(getDTotalStats(0)), 4) + " | " + rightenString(Integer.toString(getDTotalStats(23)), 9) + " | " + rightenString(Integer.toString(getTotalLagOuts()), 2) + " |");
		    out.add("+------------------------'        |      |           |    |");

		    for (int i = players.length - 1; i >= 0; i--) {
			MatchPlayer p = players[i];
			out.add("|  " + Tools.formatString(p.getPlayerName(), 25) + " " + rightenString(Integer.toString(p.getTotalStatistic(20)), 4) + " | " + rightenString(Integer.toString(p.getTotalStatistic(0)), 4) + " | " + rightenString(Integer.toString(p.getTotalStatistic(23)), 9) + " | " + rightenString(Integer.toString(p.getLagOuts()), 2) + " |");
		    }
		} else {
		    out.add("|                          ,------+------+------+-----------+----+");
		    out.add("| " + Tools.formatString(m_fcTeamName, 23) + " /  " + rightenString(Integer.toString(getDTotalStats(20)), 4) + " | " + rightenString(Integer.toString(getDTotalStats(0)), 4) + " | " + rightenString(Integer.toString(getDTotalStats(21)), 4) + " | " + rightenString(Integer.toString(getDTotalStats(23)), 9) + " | " + rightenString(Integer.toString(getTotalLagOuts()), 2) + " |");
		    out.add("+------------------------'        |      |      |           |    |");

		    for (int i = players.length - 1; i >= 0; i--) {
			MatchPlayer p = players[i];
			out.add("|  " + Tools.formatString(p.getPlayerName(), 25) + " " + rightenString(Integer.toString(p.getTotalStatistic(20)), 4) + " | " + rightenString(Integer.toString(p.getTotalStatistic(0)), 4) + " | " + rightenString(Integer.toString(p.getTotalStatistic(21)), 4) + " | " + rightenString(Integer.toString(p.getTotalStatistic(23)), 9) + " | " + rightenString(Integer.toString(p.getLagOuts()), 2) + " |");
		    }
		}
	} else {
	    out.add("|                          ,------+------+------+-----------+------+------+-----------+----+");
	    out.add("| " + Tools.formatString(m_fcTeamName, 23) + " /  " + rightenString(Integer.toString(getDTotalStats(20)), 4) + " | " + rightenString(Integer.toString(getDTotalStats(0)), 4) + " | " + rightenString(Integer.toString(getDTotalStats(21)), 4) + " | " + rightenString(Integer.toString(getDTotalStats(1)), 9) + " | " + rightenString(Integer.toString(getDTotalStats(18)), 4) + " | " + rightenString(Integer.toString(getDTotalStats(6)), 4) + " | " + rightenString(Integer.toString(getDTotalStats(23)), 9) + " | " + rightenString(Integer.toString(getTotalLagOuts()), 2) + " |");
	    out.add("+------------------------'        |      |      |           |      |      |           |    |");

	    for (int i = players.length - 1; i >= 0; i--) {
		MatchPlayer p = players[i];
		out.add("|  " + Tools.formatString(p.getPlayerName(), 25) + " " + rightenString(Integer.toString(p.getTotalStatistic(20)), 4) + " | " + rightenString(Integer.toString(p.getTotalStatistic(0)), 4) + " | " + rightenString(Integer.toString(p.getTotalStatistic(21)), 4) + " | " + rightenString(Integer.toString(p.getTotalStatistic(1)), 9) + " | " + rightenString(Integer.toString(p.getTotalStatistic(18)), 4) + " | " + rightenString(Integer.toString(p.getTotalStatistic(6)), 4) + " | " + rightenString(Integer.toString(p.getTotalStatistic(23)), 9) + " | " + rightenString(Integer.toString(p.getLagOuts()), 2) + " |");
	    }

	}

	return out;
    }

    public String rightenString(String fragment, int length) {
	int curLength = fragment.length();
	int startPos = length - curLength;
	String result = "";

	for (int i=0; i < startPos; i++) result = result + " ";
	result = result + fragment;
	for (int j=result.length(); j < length; j++) result = result + " ";

	return result;
    }
}



