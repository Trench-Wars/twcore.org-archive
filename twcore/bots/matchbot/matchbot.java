


/*
 * Matchtwl.java
 *
 * Created on August 19, 2002, 8:34 PM
 */

/**
 *
 * @author  Administrator
 */



package twcore.bots.matchbot;

import twcore.core.*;
import twcore.misc.database.DBPlayerData;
import java.util.*;
import java.sql.*;
import java.io.*;
import java.util.regex.*;


public class matchbot extends SubspaceBot
{

    MatchGame m_game;
    String m_arena;
    BotSettings m_botSettings;
    OperatorList m_opList;
    LinkedList m_arenaList;
    LinkedList m_gameRequests;
    TimerTask m_gameKiller;
    String startMessage;
    HashMap m_registerList;

    //
    boolean m_isLocked = false;
    boolean m_isStartingUp = false;
    String m_locker;
    int m_lockState = 0;
    //
    static int CHECKING_ARENAS = 1, LOCKED = 2;
    static int INACTIVE_MESSAGE_LIMIT = 3, ACTIVE_MESSAGE_LIMIT = 8;
    // these variables are for when the bot is locked
    BotSettings m_rules;
    String m_rulesFileName;

    // --- temporary
    String m_team1 = null, m_team2 = null;

    private static Pattern parseInfoRE = Pattern.compile("^IP:(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})  TimeZoneBias:\\d+  Freq:\\d+  TypedName:(.*)  Demo:\\d  MachineId:(\\d+)$");
    private static Pattern cruncherRE = Pattern.compile("\\s+");

    /** Creates a new instance of Matchtwl */
    public matchbot(BotAction botAction)
    {
        //Setup of necessary stuff for any bot.
        super(botAction);

        m_botSettings = m_botAction.getBotSettings();
        m_arena = m_botSettings.getString("Arena");
        m_opList = m_botAction.getOperatorList();
        m_gameRequests = new LinkedList();
        m_registerList = new HashMap();

        requestEvents();

    }

    public static String[] stringChopper(String input, char deliniator)
    {

        LinkedList list = new LinkedList();

        int nextSpace = 0;
        int previousSpace = 0;

        if (input == null)
        {
            return null;
        }

        do
        {
            previousSpace = nextSpace;
            nextSpace = input.indexOf(deliniator, nextSpace + 1);

            if (nextSpace != -1)
            {
                String stuff = input.substring(previousSpace, nextSpace).trim();
                if (stuff != null && !stuff.equals(""))
                    list.add(stuff);
            }

        }
        while (nextSpace != -1);
        String stuff = input.substring(previousSpace);
        stuff = stuff.trim();
        if (stuff.length() > 0)
        {
            list.add(stuff);
        };

        return (String[]) list.toArray(new String[list.size()]);
    }

    public int getBotNumber()
    {
        int nrBots = m_botSettings.getInt("Max Bots");
        for (int i = 1; i <= nrBots; i++)
        {
            if (m_botSettings.getString("Name" + i).equalsIgnoreCase(m_botAction.getBotName()))
                return i;
        };
        return 0;
    };

    public void requestEvents()
    {
        EventRequester req = m_botAction.getEventRequester();
        req.request(EventRequester.LOGGED_ON);
        req.request(EventRequester.FREQUENCY_SHIP_CHANGE);
        req.request(EventRequester.FREQUENCY_CHANGE);
        req.request(EventRequester.SCORE_RESET);
        req.request(EventRequester.FLAG_CLAIMED);
        req.request(EventRequester.FLAG_REWARD);
        req.request(EventRequester.PLAYER_DEATH);
        req.request(EventRequester.PLAYER_LEFT);
        req.request(EventRequester.PLAYER_ENTERED);
        req.request(EventRequester.PLAYER_POSITION);
        req.request(EventRequester.MESSAGE);
        req.request(EventRequester.ARENA_JOINED);
        req.request(EventRequester.WEAPON_FIRED);
    };

    /**
     * @param event The weapon Fired event
     */
    public void handleEvent(WeaponFired event)
    {
        if (m_game != null)
            m_game.handleEvent(event);
    }

    public void handleEvent(ArenaJoined event)
    {
        if (m_game != null)
        {
            m_game.handleEvent(event);
        };
    }

    public void handleEvent(LoggedOn event)
    {
        m_botAction.ipcSubscribe("MatchBot");

        String def = m_botSettings.getString("Default" + getBotNumber());
        int typeNumber = getGameTypeNumber(def);

        if (typeNumber == 0)
        {
            m_botAction.joinArena(m_arena);
        }
        else
        {
            String[] param = { Integer.toString(typeNumber)};
            command_lock(m_botAction.getBotName(), param);
        };
        m_botAction.setMessageLimit(INACTIVE_MESSAGE_LIMIT);
    }

    public void handleEvent(FlagClaimed event)
    {
        if (m_game != null)
        {
            m_game.handleEvent(event);
        };
    };

    public void handleEvent(FrequencyChange event)
    {
        if (m_game != null)
        {
            m_game.handleEvent(event);
        };
    };

    public void handleEvent(FrequencyShipChange event)
    {
        if (m_game != null)
        {
            m_game.handleEvent(event);
        };
    };

    public void handleEvent(FlagReward event)
    {
        if (m_game != null)
        {
            m_game.handleEvent(event);
        };
    };

    public void handleEvent(InterProcessEvent event)
    {
        if ((event.getChannel().equals("MatchBot")) && (!event.getSenderName().equals(m_botAction.getBotName())))
        {
            if (event.getObject() instanceof String)
            {
                String s = (String) event.getObject();
                if (s.equals("whatArena"))
                {
                    m_botAction.ipcTransmit("MatchBot", "myArena:" + m_botAction.getArenaName());
                };

                if ((s.startsWith("myArena:")) && (m_isLocked) && (m_lockState == CHECKING_ARENAS))
                {
                    if (m_arenaList == null)
                    {
                        m_arenaList = new LinkedList();
                    };
                    m_arenaList.add(s.substring(8).toLowerCase());
                };
            };
        };
    };

    public void handleEvent(PlayerDeath event)
    {
        if (m_game != null)
        {
            m_game.handleEvent(event);
        };
    };

    public void handleEvent(PlayerLeft event)
    {
        if (m_game != null)
        {
            m_game.handleEvent(event);
        };
    };

    public void handleEvent(PlayerEntered event)
    {
        if (m_game != null)
        {
            m_game.handleEvent(event);
        };
    };

    public void handleEvent(PlayerPosition event)
    {
        if (m_game != null)
        {
            m_game.handleEvent(event);
        };
    };

    public void handleEvent(ScoreReset event)
    {
        if (m_game != null)
            m_game.handleEvent(event);
    };

    public void handleEvent(SQLResultEvent event)
    {
    };

    public void handleEvent(Message event)
    {
        boolean isStaff, isRestrictedStaff;
        String message = event.getMessage();

        if ((event.getMessageType() == Message.ARENA_MESSAGE)
            && (event.getMessage().equals("WARNING: You have been disconnected because server has not been receiving data from you.")))
        {
            if (m_game != null)
                m_game.cancel();
            m_botAction.die();
        };

        if ((event.getMessageType() == Message.ARENA_MESSAGE)
            && (event.getMessage()).startsWith("IP:"))
        {
            parseInfo( event.getMessage() );
        };

        if ((event.getMessageType() == Message.PRIVATE_MESSAGE)
            || ((event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) && (message.toLowerCase().startsWith("!accept"))))
        {
            String name = m_botAction.getPlayerName(event.getPlayerID());
            if (name == null)
            {
                name = event.getMessager();
            };

            isStaff = false;
            isRestrictedStaff = false;
            if ((m_isLocked) && (m_rules != null))
            {
                if (m_rules.getString("specialaccess") != null)
                {
                    if ((new String(":" + m_rules.getString("specialaccess").toLowerCase() + ":")).indexOf(":" + name.toLowerCase() + ":") != -1)
                    {
                        isStaff = true;
                        isRestrictedStaff = true;
                    };
                };
            };

            if (m_opList.isZH(name))
            {
                isStaff = true;
                isRestrictedStaff = false;
            };

            // First: convert the command to a command with parameters
            String command = stringChopper(message, ' ')[0];
            String[] parameters = stringChopper(message.substring(command.length()).trim(), ':');
            for (int i = 0; i < parameters.length; i++)
                parameters[i] = parameters[i].replace(':', ' ').trim();
            command = command.trim();

            parseCommand(name, command, parameters, isStaff, isRestrictedStaff);

        }
        else if (event.getMessageType() == Message.ARENA_MESSAGE)
        {
            String msg = event.getMessage();
            if (msg.startsWith("Arena UNLOCKED"))
                m_botAction.toggleLocked();
        };
        if (m_game != null)
        {
            m_game.handleEvent(event);
        };
    }

    // getHelpMessages, for hosts....
    public String[] getHelpMessages(String name, boolean isStaff, boolean isRestrictedStaff)
    {
        ArrayList help = new ArrayList();

        if (m_game != null)
        {
            if (isStaff)
            {
                help.add("!killgame                                - stops a game _immediately_");
            };
            help.addAll(m_game.getHelpMessages(name, isStaff));
        }
        else
        {
            if (isStaff)
            {
                if (!m_isLocked)
                {
                    if (!isRestrictedStaff)
                        help.add("!listgames                               - list all available game types");
                    help.add("!game <typenumber>                       - start a game of <type>");
                    help.add("!game <typenumber>:<teamA>:<teamB>       - start a game of <type>");
                    if (!isRestrictedStaff)
                    {
                        help.add("!go <arena>                              - makes the bot go to the specified arena");
                        help.add("!lock <typenumber>                       - lock at a free arena where the event can be hosted");
                    };
                }
                else
                {
                    help.add("!game <squadA>:<squadB>                  - start a game of " + m_rules.getString("name") + " between teamA and teamB");
                    if (!isRestrictedStaff)
                    {
                        help.add("!unlock                                  - unlock the bot, makes it go back to ?go twd");
                        if (m_opList.isSmod(name))
                        {
                            help.add("!listaccess                              - list all the players who have special access to this game");
                            help.add("!addaccess <name>                        - add a player to the list");
                            help.add("!removeaccess <name>                     - remove a player from the list");
                        };
                    };
                }
            };
            if (m_isLocked)
            {
                if (m_rules.getInt("captain_can_start_game") == 1)
                {
                    help.add("The following command only works for rostered captains and assistants:");
                    help.add("!challenge <squad>                       - request a game of " + m_rules.getString("name") + " against <squad>");
                    help.add("!accept <squad>                          - accept the !challenge made by the challenging squad");
                };
            };
        };

        return (String[]) help.toArray(new String[help.size()]);
    }

    public void parseCommand(String name, String command, String[] parameters, boolean isStaff, boolean isRestrictedStaff)
    {
        if (isStaff)
        {
            if (command.equals("!game"))
                createGame(name, parameters);
            if (!isRestrictedStaff)
            {
                if (command.equals("!listgames"))
                    listGames(name);
                if (command.equals("!go"))
                    command_go(name, parameters);
                if (command.equals("!lock"))
                    command_lock(name, parameters);
                if (command.equals("!unlock"))
                    command_unlock(name, parameters);
                if ((command.equals("!die")) && (m_opList.isSmod(name)))
                    m_botAction.die();
                if ((command.equals("!listaccess")) && (m_opList.isSmod(name)))
                    command_listaccess(name, parameters);
                if ((command.equals("!addaccess")) && (m_opList.isSmod(name)))
                    command_addaccess(name, parameters);
                if ((command.equals("!removeaccess")) && (m_opList.isSmod(name)))
                    command_removeaccess(name, parameters);
            };
            if (m_game != null)
            {
                if (command.equals("!killgame"))
                {
                    m_botAction.sendArenaMessage("The game has been brutally killed by " + name);
                    m_botAction.setMessageLimit(INACTIVE_MESSAGE_LIMIT);
                    m_game.cancel();
                    m_game = null;
                };
                if (command.equals("!endgameverysilently"))
                {
                    m_botAction.setMessageLimit(INACTIVE_MESSAGE_LIMIT);
                    m_game.cancel();
                    m_game = null;
                };
                if (command.equals("!startinfo"))
                {
                    if (startMessage != null)
                        m_botAction.sendPrivateMessage(name,startMessage);
                }

            };
        };
        if ((m_rules != null) && (m_rules.getInt("captain_can_start_game") == 1))
        {
            if (command.equals("!challenge"))
                command_challenge(name, parameters);
            if (command.equals("!accept"))
                command_accept(name, parameters);
        };
        if (command.equals("!help"))
            m_botAction.privateMessageSpam(name, getHelpMessages(name, isStaff, isRestrictedStaff));

        if (command.equals("!register"))
            command_registername(name, parameters);

        if (m_game != null)
            m_game.parseCommand(name, command, parameters, isStaff);
    };

    public void parseInfo(String message) {

        Matcher m = parseInfoRE.matcher(message);
        if ( !m.matches() )
            return;
        String ip = m.group(1);
        String name = cruncherRE.matcher( m.group(2) ).replaceAll(" ");
        String mid = m.group(3);

        //The purpose of this is to not confuse the info doen by PlayerLagInfo
        if( !m_registerList.containsKey( name ) ) return;

        m_registerList.remove( name );

        DBPlayerData dbP = new DBPlayerData( m_botAction, "local", name );

        //Note you can't get here if already registered, so can't match yourself.
        if( dbP.aliasMatch( ip, mid ) ) {
            m_botAction.sendSmartPrivateMessage( name, "Another account has already been registered on your connection, please contact a TWD/TWL Op for further information." );
            return;
        }

        if( !dbP.register( ip, mid ) ) {
            m_botAction.sendSmartPrivateMessage( name, "Unable to register name, please contact a TWL/TWD op for further help." );
            return;
        }
        m_botAction.sendSmartPrivateMessage( name, "Registration successful." );

    };

    public void command_go(String name, String[] parameters)
    {
        if (m_game == null)
        {
            if (!m_isLocked)
            {
                if (parameters.length > 0)
                {
                    String s = parameters[0];
                    m_arena = s;
                    m_botAction.joinArena(m_arena);
                };
            }
            else
                m_botAction.sendPrivateMessage(name, "I am locked in this arena");
        }
        else
            m_botAction.sendPrivateMessage(name, "There's still a game going on, kill it first");
    };

    public void command_lock(String name, String[] parameters)
    {
        if (m_game != null)
        {
            m_botAction.sendPrivateMessage(name, "Can't lock to a game, there is another game going on");
            return;
        };

        if (m_isLocked)
        {
            m_botAction.sendPrivateMessage(name, "Can't lock to a game, I'm already locked in here. Unlock me first");
            return;
        };

        // lock here

        if (parameters.length >= 1)
        {
            try
            {
                int typenumber = Integer.parseInt(parameters[0]);
                String typeName = getGameTypeName(typenumber);
                if (typeName != null)
                {
                    m_rulesFileName = m_botAction.getGeneralSettings().getString("Core Location") + "/data/Rules/" + typeName + ".txt";
                    m_rules = new BotSettings(m_rulesFileName);
                    m_isLocked = true;
                    m_lockState = CHECKING_ARENAS;
                    m_locker = name;
                    m_arenaList = new LinkedList();
                    m_isLocked = true;
                    m_botAction.ipcTransmit("MatchBot", "whatArena");
                    TimerTask a = new TimerTask()
                    {
                        public void run()
                        {
                            goToLockedArena();
                        };
                    };
                    m_botAction.scheduleTask(a, 100);
                }
                else
                    m_botAction.sendPrivateMessage(name, "That game type does not exist");
            }
            catch (Exception e)
            {
                System.out.println(e.getMessage());
            };

        };
    };

    public void command_unlock(String name, String[] parameters)
    {
        if (m_game != null)
        {
            m_botAction.sendPrivateMessage(name, "Can't unlock, there's a game going on");
            return;
        };

        m_isLocked = false;
        m_botAction.sendPrivateMessage(name, "Unlocked, going to ?go twd");
        m_botAction.changeArena("twd");
    };

    //
    public void command_challenge(String name, String[] parameters)
    {
        try
        {
            if (m_game == null)
            {
                Player p = m_botAction.getPlayer(name);
                // check if he isn't challenging his own squad
                if (!p.getSquadName().equalsIgnoreCase(parameters[0]))
                {
                    DBPlayerData dp = new DBPlayerData(m_botAction, "local", name);
                    if ((dp.getTeamName() != null) && (!dp.getTeamName().equals("")) && (p.getSquadName().equalsIgnoreCase(dp.getTeamName())))
                    {
                        // check if the challenged team exists
                        String nmySquad = parameters[0];
                        ResultSet rs =
                            m_botAction.SQLQuery(
                                "local",
                                "select fnTeamID from tblTeam where fcTeamName = '" + Tools.addSlashesToString(nmySquad) + "' and (fdDeleted = 0 or fdDeleted IS NULL)");
                        if (rs.next())
                        {
                            // check if he is assistant or captain
                            if (dp.hasRank(3) || dp.hasRank(4))
                            {
                                m_gameRequests.add(new GameRequest(name, p.getSquadName(), nmySquad));
                                m_botAction.sendSquadMessage(
                                    nmySquad,
                                    name
                                        + " is challenging you for a game of "
                                        + m_rules.getString("name")
                                        + " versus "
                                        + p.getSquadName()
                                        + ". Captains/assistants, ?go "
                                        + m_botAction.getArenaName()
                                        + " and pm me with '!accept "
                                        + p.getSquadName()
                                        + "'");
                                m_botAction.sendPrivateMessage(name, "Your challenge has been sent out to " + nmySquad);
                            }
                            else
                                m_botAction.sendPrivateMessage(name, "You're not allowed to make challenges for your squad");
                        }
                        else
                            m_botAction.sendPrivateMessage(name, "The team you want to challenge does NOT exist in TWD");
                    }
                    else
                        m_botAction.sendPrivateMessage(name, "Your ?squad and your squad on the TWD roster are not the same");
                }
                else
                    m_botAction.sendPrivateMessage(name, "You can't challenge your own squad, silly :P");
            }
            else
                m_botAction.sendPrivateMessage(name, "You can't challenge here, there is a game going on here already");
        }
        catch (Exception e)
        {
            m_botAction.sendPrivateMessage(name, "Specify the squad you want to challenge");
        };
    };

    public void command_accept(String name, String[] parameters)
    {
        try
        {
            if (m_isStartingUp == false)
            {
                if (m_game == null)
                {
                    DBPlayerData dp = new DBPlayerData(m_botAction, "local", name);
                    Player p = m_botAction.getPlayer(name);
                    if (p != null)
                    {
                        if ((dp.getTeamName() != null) && (!dp.getTeamName().equals("")) && (p.getSquadName().equalsIgnoreCase(dp.getTeamName())))
                        {
                            // check if the accepted challenge exists
                            String nmySquad = parameters[0];
                            GameRequest t, r = null;
                            ListIterator i = m_gameRequests.listIterator();
                            while (i.hasNext())
                            {
                                t = (GameRequest) i.next();
                                if (t.getRequestAge() >= 300000)
                                    i.remove();
                                else if ((t.getChallenged().equalsIgnoreCase(p.getSquadName())) && (t.getChallenger().equalsIgnoreCase(nmySquad)))
                                    r = t;
                            };
                            if (r != null)
                            {
                                // check if he is assistant or captain
                                if (dp.hasRank(3) || dp.hasRank(4))
                                {
                                    m_isStartingUp = true;
                                    m_botAction.sendSquadMessage(
                                        nmySquad,
                                        "A game of "
                                            + m_rules.getString("name")
                                            + " versus "
                                            + p.getSquadName()
                                            + " will start in ?go "
                                            + m_botAction.getArenaName()
                                            + " in 30 seconds");
                                    m_botAction.sendSquadMessage(
                                        p.getSquadName(),
                                        "A game of " + m_rules.getString("name") + " versus " + nmySquad + " will start in ?go " + m_botAction.getArenaName() + " in 30 seconds");
                                    m_botAction.sendArenaMessage(nmySquad + " vs. " + p.getSquadName() + " will start here in 30 seconds", 2);
                                    m_team1 = nmySquad;
                                    m_team2 = p.getSquadName();
                                    startMessage = name + "(" + p.getSquadName() + ") accepted challenge from " + r.getRequester() + "(" + r.getChallenger() + ")";

                                    TimerTask m_startGameTimer = new TimerTask()
                                    {
                                        public void run()
                                        {
                                            m_isStartingUp = false;
                                            String dta[] = { m_team1, m_team2 };
                                            createGame(m_botAction.getBotName(), dta);
                                        };
                                    };

                                    m_botAction.scheduleTask(m_startGameTimer, 30000);
                                }
                                else
                                    m_botAction.sendPrivateMessage(name, "You're not allowed to accept challenges for your squad");
                            }
                            else
                                m_botAction.sendPrivateMessage(name, "The team you want to accept the challenge of does NOT exist in TWD");
                        }
                        else
                            m_botAction.sendPrivateMessage(name, "Your ?squad and your squad on the TWD roster are not the same");
                    }
                    else
                        m_botAction.sendSmartPrivateMessage(
                            name,
                            "Please ?go " + m_botAction.getArenaName() + " and accept the challenge from there so I can check your ?squad. Thanks.");
                }
                else
                    m_botAction.sendPrivateMessage(name, "Can't accept challenge, there's a game going on here already");
            }
            else
                m_botAction.sendPrivateMessage(name, "Another game will start up here soon, already.");
        }
        catch (Exception e)
        {
        };
    };

    public String[] getAccessList()
    {
        String accA[] = stringChopper(m_rules.getString("specialaccess"), ':');
        for (int i = 1; i < accA.length; i++)
            accA[i] = accA[i].substring(1);
        return accA;
    };

    public void command_registername(String name, String[] parameters)
    {

        if( m_rules.getInt("aliascheck") == 0 ) return;

        DBPlayerData dbP = new DBPlayerData( m_botAction, "local", name );

        if( dbP.isRegistered() ) {
            m_botAction.sendSmartPrivateMessage( name, "This name has already been registered." );
            return;
        }

        m_registerList.put( name, name );
        m_botAction.sendUnfilteredPrivateMessage( name, "*info" );
    }

    public void command_listaccess(String name, String[] parameters)
    {
        String accA[] = getAccessList();
        String answ = "";
        int j = 0;
        m_botAction.sendPrivateMessage(name, "Access list for game: " + m_rules.getString("name"));
        for (int i = 0; i < accA.length; i++)
        {
            if (accA[i].length() > 20)
                answ = answ + accA[i].substring(0, 20);
            else
                answ = answ + accA[i];
            for (j = 0; j < (20 - accA[i].length()); j++)
                answ = answ + " ";
            if (i % 3 == 1)
            {
                m_botAction.sendPrivateMessage(name, answ);
                answ = "";
            }
            else
                answ = answ + "          ";
        };
        if (!answ.equals(""))
            m_botAction.sendPrivateMessage(name, answ);
    };

    public void command_addaccess(String name, String[] parameters)
    {
        try
        {
            String newP = parameters[0];
            String acc = m_rules.getString("specialaccess");
            if (!(acc.trim().equals("")))
                acc = acc + ":";
            acc = acc + newP;
            m_rules.put("specialaccess", acc);
            m_rules.save();
            m_botAction.sendPrivateMessage(name, newP + " has been added to the access list");
        }
        catch (Exception e)
        {
            System.out.println("Error in command_addaccess: " + e.getMessage());
        };
    };

    public void command_removeaccess(String name, String[] parameters)
    {
        try
        {
            String newP = parameters[0].toLowerCase();
            String acc = m_rules.getString("specialaccess");
            int cutFrom = acc.toLowerCase().indexOf(newP);
            if (cutFrom != -1)
            {
                int cutTo = acc.indexOf(":", cutFrom);
                if (cutTo == -1)
                    cutTo = acc.length();
                if (cutFrom == 0)
                {
                    cutFrom = 1;
                    cutTo += 1;
                };
                if (cutTo > acc.length())
                    cutTo = acc.length();
                acc = acc.substring(0, cutFrom - 1) + acc.substring(cutTo);
                m_rules.put("specialaccess", acc);
                m_rules.save();
                m_botAction.sendPrivateMessage(name, newP + " has been removed from the access list");
            };
        }
        catch (Exception e)
        {
            System.out.println("Error in command_removeaccess: " + e.getMessage());
        };
    };

    //
    public void goToLockedArena()
    {
        String[] avaArena = m_rules.getString("arena").split(",");
        String pick = null;
        for (int i = 0; i < avaArena.length; i++)
        {

            if ((!m_arenaList.contains(avaArena[i].toLowerCase())) && (pick == null))
            {
                pick = avaArena[i].toLowerCase();
            };
        };
        if (pick != null)
        {
            m_lockState = LOCKED;
            m_botAction.sendPrivateMessage(m_locker, "Going to ?go " + pick);
            m_botAction.changeArena(pick);
        }
        else
        {
            if (m_locker != null)
                m_botAction.sendPrivateMessage(m_locker, "I'm sorry, every arena where this event can be hosted is in use (" + m_rules.getString("arena") + ")");
            m_lockState = 0;
            m_isLocked = false;
        };
    };

    //
    public void createKillChecker()
    {
        if (m_gameKiller == null)
        {
            m_gameKiller = new TimerTask()
            {
                public void run()
                {
                    if (m_game != null)
                    {
                        if (m_game.getGameState() == m_game.KILL_ME_PLEASE)
                        {
                            m_game.cancel();
                            m_game = null;
                            m_botAction.setMessageLimit(INACTIVE_MESSAGE_LIMIT);
                        };
                    };
                };
            };
            m_botAction.scheduleTaskAtFixedRate(m_gameKiller, 2000, 2000);
        };
    };

    // create game
    public void createGame(String name, String[] parameters)
    {
        try
        {

            createKillChecker();
            String fcTeam1Name = null, fcTeam2Name = null, rulesName = null;

            int typenumber;
            if (!m_isLocked)
            {
                if (parameters.length >= 1)
                {
                    typenumber = Integer.parseInt(parameters[0]);
                    rulesName = m_botAction.getGeneralSettings().getString("Core Location") + "/data/Rules/" + getGameTypeName(typenumber) + ".txt";
                    if (parameters.length < 3)
                    {
                        fcTeam1Name = "Freq 1";
                        fcTeam2Name = "Freq 2";
                    }
                    else
                    {
                        fcTeam1Name = parameters[1];
                        fcTeam2Name = parameters[2];
                    };
                };
            }
            else
            {
                rulesName = m_rulesFileName;
                if (parameters.length < 2)
                {
                    fcTeam1Name = "Freq 1";
                    fcTeam2Name = "Freq 2";
                }
                else
                {
                    fcTeam1Name = parameters[0];
                    fcTeam2Name = parameters[1];
                };
            };

            if (rulesName != null)
            {
                if (m_game == null)
                {
                    m_botAction.toggleLocked();
                    m_botAction.setMessageLimit(ACTIVE_MESSAGE_LIMIT);
                    if (!name.equalsIgnoreCase(m_botAction.getBotName()))
                        startMessage = "Game started by " + name;
                    m_game = new MatchGame(rulesName, fcTeam1Name, fcTeam2Name, m_botAction);
                }
                else
                    m_botAction.sendPrivateMessage(name, "There's already a game running, type !killgame to kill it first");
            }
            else
                m_botAction.sendPrivateMessage(name, "Game type doesn't exist");
        }
        catch (Exception e)
        {
            m_botAction.sendPrivateMessage(name, "Provide a correct game type number");
            Tools.printStackTrace(e);
        };
    };

    // list games
    public void listGames(String name)
    {
        File f = m_botAction.getCoreDirectoryFile("data/Rules");
        String[] s = f.list();
        int cnter = 0;
        m_botAction.sendPrivateMessage(name, "I contain the following " + "games:");
        for (int i = 0; i < s.length; i++)
        {
            if (s[i].endsWith(".txt"))
            {
                s[i] = s[i].substring(0, s[i].lastIndexOf('.'));
                if (s[i].indexOf('$') == -1)
                {
                    cnter++;
                    String extraInfo = m_botSettings.getString(s[i]);
                    if (extraInfo == null)
                        extraInfo = "";
                    else
                        extraInfo = "      " + extraInfo;
                    m_botAction.sendPrivateMessage(name, cnter + ". " + s[i] + extraInfo);
                }
            }
        }
    };

    public String getGameTypeName(int fnGameTypeNumber)
    {
        File f = m_botAction.getCoreDirectoryFile("data/Rules");
        String[] s = f.list();
        int cnter = 0;
        for (int i = 0; i < s.length; i++)
        {
            if (s[i].endsWith(".txt"))
            {
                s[i] = s[i].substring(0, s[i].lastIndexOf('.'));
                if (s[i].indexOf('$') == -1)
                {
                    cnter++;
                    if (cnter == fnGameTypeNumber)
                        return s[i];
                }
            }
        }
        return null;
    };

    public int getGameTypeNumber(String fcGameTypeName)
    {
        File f = m_botAction.getCoreDirectoryFile("data/Rules");
        String[] s = f.list();
        int cnter = 0;
        for (int i = 0; i < s.length; i++)
        {
            if (s[i].endsWith(".txt"))
            {
                s[i] = s[i].substring(0, s[i].lastIndexOf('.'));
                if (s[i].indexOf('$') == -1)
                {
                    cnter++;
                    if (s[i].equalsIgnoreCase(fcGameTypeName))
                        return cnter;
                }
            }
        }
        return 0;
    };

    public void cancel()
    {
        m_gameKiller.cancel();
        m_gameKiller = null;
        m_botAction.cancelTasks();
        m_botAction.ipcUnSubscribe("MatchBot");
    }
}


class GameRequest {
    long m_timeRequest = 0;
    String m_challenger = "", m_challenged = "", m_requester = "";
    boolean accepted = false;
    BotAction m_botAction;

    public GameRequest(String requester, String challenger, String challenged) {
        m_requester = requester;
        m_challenger = challenger;
        m_challenged = challenged;
        m_timeRequest = System.currentTimeMillis();
    };


    public String getChallenged() { return m_challenged; };
    public String getChallenger() { return m_challenger; };
    public String getRequester()  { return m_requester;  };
    public long getRequestAge() { return (System.currentTimeMillis()-m_timeRequest); };


};
