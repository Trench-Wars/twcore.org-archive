package twcore.bots.radiobot;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;


import twcore.core.*;
import twcore.core.events.LoggedOn;
import twcore.core.events.ArenaJoined;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.SQLResultEvent;
import twcore.core.util.Tools;

/**
    To assist in hosting radio (while not requiring a host to have staff access).
    based on relaybot
    @author Flibb
    Update @author Derek (dezmond)
    Update @author POiD (28/03/2012)
*/

public final class radiobot extends SubspaceBot {
    private EventRequester m_req;
    private OperatorList m_opList;
    //private LinkedList<String> m_loggedInList;
    //private String m_currentPassword = "";
    private LinkedList<String> m_alreadyZoned;
    private String m_currentHost = "";
    private String m_url = "";
    private Poll m_currentPoll = null;
    private boolean m_announcing = false;
    private boolean m_announcingArena = false;
    private boolean m_someoneHosting = false;
    //private boolean m_pub = false;
    private long m_timeStarted;
    private long m_timeStartedToHost = 0;
    private long m_timeToClearZone;
    private long m_timeOfLastZone = 0;

    HashMap <String, String> operators   = new HashMap<String, String>();
    HashMap <String, String> hosts = new HashMap<String, String>();


    private RadioQueue m_shoutouts;
    private RadioQueue m_requests;
    private RadioQueue m_topics;
    private RadioQueue m_questions;

    private final static String NO_HOST = "No one is currently hosting.";
    private final static int MAX_ANNOUNCE_LINES = 3;
    private final static long SIX_HOURS = 21600000;
    private final static long TEN_MINUTES = 600000;
    private final static long THIRTY_MINUTES = 1800000;


    private String[] m_announcement = { "", "", "" };
    private int m_announceLength = 0;
    private String m_welcome = "";
    final String        mySQLHost = "website";
    BotSettings         m_botSettings;


    /** Creates a new instance of radiobot */
    public radiobot(BotAction botAction) {
        super(botAction);
        m_req = botAction.getEventRequester();
        m_req.request(EventRequester.LOGGED_ON);
        m_req.request(EventRequester.MESSAGE);
        m_req.request(EventRequester.PLAYER_ENTERED);
        m_req.request( EventRequester.ARENA_JOINED );
        m_opList = botAction.getOperatorList();
        //m_loggedInList = new LinkedList<String>();
        m_alreadyZoned = new LinkedList<String>();


        //m_currentPassword = m_botAction.getBotSettings().getString("ServPass");
        // if(m_currentPassword == null)
        //m_currentPassword = "";

        m_shoutouts = new RadioQueue("Shoutout");
        m_requests = new RadioQueue("Song");
        m_topics = new RadioQueue("Topic");
        m_questions = new RadioQueue("Question");

        m_timeStarted = System.currentTimeMillis();
        m_timeStartedToHost = System.currentTimeMillis();
        m_timeToClearZone = m_timeStarted + SIX_HOURS;
        load_authorize();
    }

    public boolean isIdle() {
        return !m_someoneHosting;
    }

    public void handleEvent(LoggedOn event) {
        m_botSettings = m_botAction.getBotSettings();
        String chat = m_botSettings.getString( "chat" );
        m_botAction.sendUnfilteredPublicMessage( "?chat=" + chat );
        m_botAction.joinArena(m_botSettings.getString("initialarena"));
        m_botAction.setMessageLimit(10);
        load_authorize();
    }

    public void handleEvent( SQLResultEvent event) {}

    public void handleEvent (ArenaJoined event) {
        m_botAction.sendChatMessage(m_botAction.getBotName() + " is here!");
    }

    public void handleEvent(PlayerEntered event) {
        if(!m_welcome.equals("")) {
            m_botAction.sendPrivateMessage(event.getPlayerID(), m_welcome);
        }
    }

    public void handleEvent(Message event) {

        int messageType = event.getMessageType();

        if(messageType != Message.PRIVATE_MESSAGE)
            return;

        int id = event.getPlayerID();
        String name = m_botAction.getPlayerName(id);

        if(name == null)
            return;

        String message = event.getMessage();
        //load_authorize();
        //boolean isHost = hosts.containsKey(name);
        //boolean isOp = operators.containsKey(name);
        //boolean isCurrentHost = m_someoneHosting && hosts.containsKey(name.toLowerCase()) && m_currentHost.equals(name);
        boolean isER = m_opList.isER(name);

        //make command part lowercase
        message = message.trim();
        int indexOfSpace = message.indexOf(' ');

        if(indexOfSpace > 0)
            message = message.substring(0, indexOfSpace).toLowerCase() + message.substring(indexOfSpace);
        else
            message = message.toLowerCase();

        /**
            Handle !help
        */
        if (message.startsWith("!help")) {
            m_botAction.sendPrivateMessage(id, m_someoneHosting ? "The current host is " + m_currentHost : NO_HOST);
            m_botAction.privateMessageSpam(id, pubHelp);

            if (hosts.containsKey(name.toLowerCase()) || operators.containsKey(name.toLowerCase())) {
                m_botAction.privateMessageSpam(id, staffHelp);

                if (m_someoneHosting && m_currentHost.equals(name))
                    m_botAction.privateMessageSpam(id, currentRadioHostHelp);
            }

            if (operators.containsKey(name.toLowerCase()) || m_opList.isSmod(name))
                m_botAction.privateMessageSpam(id, operatorHelp);

            if (m_opList.isSmod(name))
                m_botAction.privateMessageSpam(id, smodHelp);

            if(isER)
                m_botAction.privateMessageSpam(id, erHelp);

            m_botAction.privateMessageSpam(id, endHelp);
            return;
        }

        /**
            Player
        */
        if(!m_someoneHosting
                && (message.startsWith("!shoutout ")
                    || message.startsWith("!request ")
                    || message.startsWith("!topic ")
                    || message.startsWith("!question "))) {
            m_botAction.sendPrivateMessage(id, NO_HOST);
        } else if(message.startsWith("!shoutout ")) {
            m_shoutouts.handleAdd(id, name, message.substring(10));
        } else if(message.startsWith("!request ")) {
            m_requests.handleAdd(id, name, message.substring(9));
        } else if(message.startsWith("!topic ")) {
            m_topics.handleAdd(id, name, message.substring(7));
        } else if(message.startsWith("!question ")) {
            m_questions.handleAdd(id, name, message.substring(10));
        } else if(message.equals("!how")) {
            m_botAction.sendSmartPrivateMessage(name, "TW Radio is managed by a Radio Server. If you want to be a DJ fill out an application at ...");
        } else if(message.equalsIgnoreCase("!staff")) {
            showHosts(name);
        }

        /**
            Handle logged in commands
        */
        if (hosts.containsKey(name.toLowerCase()) || operators.containsKey(name.toLowerCase())) {
            if (message.startsWith("!host")) {
                commandNewHost(name, id);

                /*  } else if(message.startsWith("!who")) {
                    handled = true;
                    m_botAction.sendPrivateMessage(id, "Radio hosts who are logged in:");
                    Iterator<String> i = m_loggedInList.iterator();
                    String who;
                    while(i.hasNext()) {
                        who = i.next();
                        m_botAction.sendPrivateMessage(id, who + (who.equals(m_currentHost) ? " (current host)" : ""));}*/

            } else if (message.startsWith("!status")) {
                commandStatus(id);

            } else if(!m_currentHost.equals(name)
                      && (message.startsWith("!poll ")
                          || message.startsWith("!arena ")
                          || message.startsWith("!zone ")
                          || message.startsWith("!endpoll"))) {
                m_botAction.sendPrivateMessage(id, "Sorry, only the current radio host may use that command."
                                               + " If you are hosting, use the !host command.");

            } else if (message.startsWith("!sex")) {
                commandPublicSex();
            }
        }

        /**
            Handle current host only commands
        */
        if (m_someoneHosting && m_currentHost.equals(name)) {
            int sound = event.getSoundCode();

            if (message.startsWith("!arena ")) {
                m_botAction.sendArenaMessage(message.substring(7) + " -" + m_currentHost
                                             , sound == 12 ? 2 : sound);

            } else if (message.startsWith("!poll ")) {
                commandStartPoll(name, id, message.substring(6));

            } else if (message.startsWith("!endpoll")) {
                if (m_currentPoll == null) {
                    m_botAction.sendPrivateMessage(id, "There is no poll running right now.");
                } else {
                    m_currentPoll.endPoll();
                    m_currentPoll = null;
                }

            } else if (message.startsWith("!zone ")) {
                commandNewZone(name, id, message.substring(6));

            } else if (message.startsWith("!time")) {
                long now = System.currentTimeMillis();
                long start = now - m_timeStartedToHost;
                m_botAction.sendPrivateMessage(id, "You have hosted for " + (start / 1000 / 60 / 60) + " hours and " + (start / 1000 / 60 % 60) + " minutes.");

            } else if (message.startsWith("!nextshout")) {
                int i = 1;

                if(message.startsWith("!nextshout ")) {
                    try {
                        i = Integer.parseInt(message.substring(11));
                    } catch(NumberFormatException e) {
                        i = 1;
                    }
                }

                i = i < m_shoutouts.size() ? i : m_shoutouts.size();

                for(; i > 0; i--) {
                    m_botAction.sendPrivateMessage(id, "Shout: " + m_shoutouts.remove());
                }
            } else if (message.startsWith("!nextreq")) {
                int i = 1;

                if (message.startsWith("!nextreq ")) {
                    try {
                        i = Integer.parseInt(message.substring(9));
                    } catch(NumberFormatException e) {
                        i = 1;
                    }
                }

                i = i < m_requests.size() ? i : m_requests.size();

                for(; i > 0; i--) {
                    m_botAction.sendPrivateMessage(id, "Reqst: " + m_requests.remove());
                }
            } else if (message.startsWith("!nexttop")) {
                int i = 1;

                if (message.startsWith("!nexttop ")) {
                    try {
                        i = Integer.parseInt(message.substring(9));
                    } catch(NumberFormatException e) {
                        i = 1;
                    }
                }

                i = i < m_topics.size() ? i : m_topics.size();

                for(; i > 0; i--) {
                    m_botAction.sendPrivateMessage(id, "Topic: " + m_topics.remove());
                }
            } else if (message.startsWith("!nextques")) {
                int i = 1;

                if (message.startsWith("!nextques ")) {
                    try {
                        i = Integer.parseInt(message.substring(10));
                    } catch(NumberFormatException e) {
                        i = 1;
                    }
                }

                i = i < m_questions.size() ? i : m_questions.size();

                for(; i > 0; i--) {
                    m_botAction.sendPrivateMessage(id, "Quest: " + m_questions.remove());
                }
            } else if (message.startsWith("!nextall")) {
                m_botAction.sendPrivateMessage(id, "Shout: " + m_shoutouts.remove());
                m_botAction.sendPrivateMessage(id, "Reqst: " + m_requests.remove());
                m_botAction.sendPrivateMessage(id, "Topic: " + m_topics.remove());
                m_botAction.sendPrivateMessage(id, "Quest: " + m_questions.remove());

            } else if (message.startsWith("!clear")) {
                m_shoutouts.clear();
                m_requests.clear();
                m_topics.clear();
                m_questions.clear();
                m_botAction.sendPrivateMessage(id, "All requests cleared.");

            } else if (message.startsWith("!addannounce ")) {
                if (m_announceLength >= MAX_ANNOUNCE_LINES) {
                    m_botAction.sendPrivateMessage(id, "Too many announce lines. (Do !clrannounce)");
                } else {
                    m_announcement[m_announceLength++] = message.substring(13);
                    m_botAction.sendPrivateMessage(id, "Message added.");
                }

            } else if (message.startsWith("!clrannounce")) {
                m_announceLength = 0;
                m_botAction.sendPrivateMessage(id, "Announcement cleared.");

            } else if (message.startsWith("!setmax ")) {
                commandSetMax(id, message.substring(8));

            } else if (message.startsWith("!viewannounce")) {
                for(int i = 0; i < m_announceLength; i++) {
                    m_botAction.sendPrivateMessage(id, m_announcement[i]);
                }

            } else if (message.startsWith("!setwelcome ")) {
                m_welcome = message.substring(12);
                m_botAction.sendPrivateMessage(id, "Welcome message set.");

            } else if (message.startsWith("!welcomeoff")) {
                m_welcome = "";
                m_botAction.sendPrivateMessage(id, "Welcome message turned off.");

            } else if (message.startsWith("!seturl ")) {
                m_url = message.substring(8);
                m_botAction.sendPrivateMessage(id, "URL set to " + m_url);

            } else if (message.startsWith("!unhost")) {
                commandUnhost(name, m_currentHost, false);

            } else if(message.startsWith("!ask")) {
                long now = System.currentTimeMillis();
                m_botAction.sendUnfilteredPublicMessage("?help The radio host is requesting a zoner (" + ((now - m_timeOfLastZone) / 1000 / 60) + " minutes and "
                                                        + ((now - m_timeOfLastZone) / 1000 % 60) + " seconds since last zone)" );
            }
        }

        /**
            Handle <ER>+ commands
        */
        if (isER) {
            /*  if(message.startsWith("!setpw ")) {
                handled = true;
                m_currentPassword = message.substring(7);
                m_botAction.sendPrivateMessage(id, "Password changed.");*/

            if (message.startsWith("!unhost")) {
                if (m_someoneHosting) {
                    commandUnhost(name, m_currentHost, true);
                }

            } else if (message.startsWith("!grantzone")) {
                commandGrantZone(name, id, m_opList.isSmod(name));

            } else if (message.startsWith("!setwelcome ")) {
                m_welcome = message.substring(12);
                m_botAction.sendPrivateMessage(id, "Welcome message set.");

            } else if (message.startsWith("!welcomeoff")) {
                m_welcome = "";
                m_botAction.sendPrivateMessage(id, "Welcome message turned off.");

            } else if (message.startsWith("!seturl ")) {
                m_url = message.substring(8);
                m_botAction.sendPrivateMessage(id, "URL set to " + m_url);
            }
        }

        /**
            Handle Op Commands
        */
        if (operators.containsKey(name.toLowerCase()) || m_opList.isSmod(name)) {
            if (message.equalsIgnoreCase("!reset")) {
                commandResetStats(name);

            } else if (message.startsWith("!addhost ")) {
                commandAddHost(name, message.substring(9), false);

            } else if(message.startsWith("!removehost ")) {
                commandRemoveHost(name, message.substring(12), false);

            } else if (message.startsWith("!addop ")) {
                commandAddHost(name, message.substring(7), true);

            } else if (message.startsWith("!removeop ")) {
                commandRemoveHost(name, message.substring(10), true);

            } else if(message.equalsIgnoreCase("!reload"))
                load_authorize();
        }

        /**
            Handle Smod+ Commands
        */
        if (m_opList.isSmod(name)) {
            if (message.startsWith("!go ")) {
                String arena = message.substring(4);

                if (Tools.isAllDigits(arena)) {
                    //m_pub = true;
                    m_botAction.changeArena("1");/*What happened to pub 0 anyway? - DRAL */
                } else {
                    m_botAction.changeArena(arena);
                }

            } else if (message.equals("!die")) {
                m_botAction.sendChatMessage(name + " Just killed me!");
                m_botAction.die("!die by " + name);

            } else if (message.startsWith("!dbcon")) {
                if (!m_botAction.SQLisOperational()) {
                    m_botAction.sendChatMessage( "WARNING: The Radio Database is offline, I can't connect!" );
                }

                try {
                    m_botAction.SQLQueryAndClose( mySQLHost, "SELECT * FROM tblRadio_Host LIMIT 0,1" );
                    m_botAction.sendChatMessage( "The Database is online. I can connect to it!" );
                } catch (Exception e ) {
                    m_botAction.sendChatMessage( "WARNING: The Radio Database is offline, I can't connect!" );
                }
            }
        }

        /*  } else if(message.startsWith("!login ")) {
            handled = true;
            if(m_currentPassword.equals("")) {
                m_botAction.sendPrivateMessage(id, "Login currently disabled.");
            } else if(m_currentPassword.equals(message.substring(7))) {
                if(!m_loggedInList.contains(m_botAction.getPlayerName(id))) {
                    m_loggedInList.add(name);
                    m_botAction.sendChatMessage(name +" has logged in successfully.");
                    m_botAction.sendSmartPrivateMessage(name,"Login Successfull.");
                } else {
                    m_botAction.sendPrivateMessage(id, "You are already logged in.");
                }
            } else {
                m_botAction.sendPrivateMessage(id, "Incorrect password.");
                m_botAction.sendChatMessage(name + " has attempted to login and failed. Is he/she a host?.");
            }*/

        /**
            Handle poll votes
        */
        if (m_currentPoll != null) {
            m_currentPoll.handlePollCount(name, message);
        }
    }

    /**
        Handle the !host command.

        @param name of the playerID who entered the message
        @param id the playerID of the player that sent the message
    */
    private void commandNewHost(String name, int id)
    {
        if (!m_someoneHosting) {
            m_currentHost = name;

            if (!m_announcing) {
                m_botAction.scheduleTaskAtFixedRate(
                    new AnnounceTask(), 10000, 150000);
                m_announcing = true;
            }

            if (!m_announcingArena) {
                m_botAction.scheduleTaskAtFixedRate(
                    new AnnounceArenaTask(), 10000, 300000);
                m_announcingArena = true;
            }

            m_someoneHosting = true;
            m_botAction.sendPrivateMessage(id, "You are now the current host. Do not abuse the green messages, or write anything inappropriate. Thanks!");
            m_botAction.sendChatMessage(name + " has enabled current hosting commands.");
            updateStatRecordsHOST( name );
        } else {
            m_botAction.sendPrivateMessage(id, "Someone is already hosting. Wait until they are done, or get an <ER>+ to !unhost them first.");
        }
    }

    /**
        Handle the !status command

        @param id the playerID of the player that sent the message
    */
    private void commandStatus(int id) {

        long now = System.currentTimeMillis();
        long ontime = now - m_timeStarted;

        if (!m_someoneHosting) {
            m_botAction.sendPrivateMessage(id, NO_HOST);
        } else {
            long nextzone = (m_timeOfLastZone + TEN_MINUTES - now) / 1000;
            m_botAction.sendPrivateMessage(id, "Current host: " + m_currentHost + " - Can zone: "
                                           + (m_alreadyZoned.contains(m_currentHost) ? "no"
                                              : (nextzone <= 0) ? "yes" : "in " + (nextzone / 60) + " mins " + (nextzone % 60) + " secs"));
        }

        m_botAction.sendPrivateMessage(id,
                                       "Topics:" + m_topics.size() + "/" + m_topics.getMax()
                                       + " Requests:" + m_requests.size() + "/" + m_requests.getMax()
                                       + " Questions:" + m_questions.size() + "/" + m_questions.getMax()
                                       + " Shoutouts:" + m_shoutouts.size() + "/" + m_shoutouts.getMax());
        m_botAction.sendPrivateMessage(id, "Poll running: " + (m_currentPoll != null));
        m_botAction.sendPrivateMessage(id, "Welcome: " + m_welcome);
        m_botAction.sendPrivateMessage(id, "URL: " + m_url);
        m_botAction.sendPrivateMessage(id, "Online for " + (ontime / 1000 / 60 / 60) + " hours and " + (ontime / 1000 / 60 % 60) + " minutes.");
    }

    /**
        handle the !sex command to just add some fun for hosts
    */
    private void commandPublicSex() {
        Random r = new Random();
        int randInt = Math.abs(r.nextInt()) % 14;

        if( randInt == 1 ) {
            m_botAction.sendChatMessage( "O yea baby keep hosting it that way OMG OMG YAAAA BABY!" );
        } else if(randInt == 2) {
            m_botAction.sendChatMessage( "You're so sexy...GOD YEA!" );
        } else if(randInt == 3) {
            m_botAction.sendChatMessage( "Unf Unf!!" );
        } else if(randInt == 4) {
            m_botAction.sendChatMessage( "Why Do you want sex now?" );
        } else if(randInt == 5) {
            m_botAction.sendChatMessage( "I'M BUSY!" );
        } else if(randInt == 6) {
            m_botAction.sendChatMessage( "Can you repeat that?" );
        } else if(randInt == 7) {
            m_botAction.sendChatMessage( "Gonna Ride you like a horse!" );
        } else if(randInt == 8) {
            m_botAction.sendChatMessage( "I'm here to amuse the hosts!" );
        }
    }

    /**
        Handle the !poll command to Start a new radio poll

        @param name is the name of the person that sent the message
        @param id is the playerID of the person that sent the message
        @param poll the string containing the poll to add
    */
    private void commandStartPoll(String name, int id, String poll) {
        if(m_currentPoll != null) {
            m_botAction.sendPrivateMessage(name, "A poll is currently in session."
                                           + " End this poll before beginning another one.");
        }

        StringTokenizer izer = new StringTokenizer(poll, ":");
        int tokens = izer.countTokens();

        if(tokens < 2) {
            m_botAction.sendPrivateMessage(id, "Sorry but the poll format is wrong.");
        }

        String[] polls = new String[tokens];
        int i = 0;

        while(izer.hasMoreTokens()) {
            polls[i] = izer.nextToken();
            i++;
        }

        m_currentPoll = new Poll(polls);
    }

    /**
        Handle the !zone command to zone a radio hosting message

        @param name of the person that sent the message
        @param id is the playerID of the person that sent the message
        @param zoneMessage is the zone message to be sent
    */
    private void commandNewZone(String name, int id, String zoneMessage) {
        long now = System.currentTimeMillis();

        if (now >= m_timeToClearZone) {
            m_alreadyZoned.clear();
            m_timeToClearZone += SIX_HOURS;
        }

        if (m_alreadyZoned.contains(name)) {
            m_botAction.sendPrivateMessage(id, "Sorry, you've already used your zone message. Get an <ER>+ to grant you another.");
        } else if (now < m_timeOfLastZone + THIRTY_MINUTES) {
            m_botAction.sendPrivateMessage(id, "Sorry, you must wait "
                                           + ((m_timeOfLastZone + THIRTY_MINUTES - now) / 1000 / 60) + " minutes and "
                                           + ((m_timeOfLastZone + THIRTY_MINUTES - now) / 1000 % 60) + " seconds before you may zone.");
        } else {
            m_botAction.sendZoneMessage(zoneMessage + " -" + name, 2);
            m_alreadyZoned.add(name);
            m_timeOfLastZone = now;
        }
    }

    /**
        Handle the !unhost command to sign off from hosting radio

        @param name is the name of the person that sent the message
        @param currentHost is the name of the person that was currently hosting
        @param byStaff is boolean indicator signifying if this hosting was ended by TW Staff
    */
    private void commandUnhost(String name, String currentHost, boolean byStaff) {

        m_botAction.cancelTasks();
        m_announcing = false;
        m_announcingArena = false;
        m_someoneHosting = false;
        long diff = System.currentTimeMillis() - m_timeStartedToHost;

        m_botAction.sendArenaMessage(m_currentHost + " has signed off the radio, thank you for listening!", 5);

        if (byStaff) {
            m_botAction.sendSmartPrivateMessage(currentHost, "Your hosting has been signed off by " + name);
            m_botAction.sendChatMessage(currentHost + " has finished hosting radio. Signed off by " + name);
        } else
            m_botAction.sendChatMessage(currentHost + " has finished hosting radio.");

        m_botAction.sendSmartPrivateMessage(currentHost, "You hosted for " + (diff / 1000 / 60 / 60) + " hours and " + (diff / 1000 / 60 % 60) + " minutes.");
        unhostStats();
    }


    /**
        Store the details of the hosting performed in the database.
    */
    private void unhostStats() {
        if( !m_botAction.SQLisOperational())
            return;

        try {
            long diff = System.currentTimeMillis() - m_timeStartedToHost;
            int minute = (int)(diff / (1000 * 60));
            ResultSet result = m_botAction.SQLQuery(mySQLHost, "SELECT fnHostID FROM tblRadio_Host WHERE fcUserName = '" + m_currentHost + "' ORDER BY fnHostID DESC LIMIT 1");

            if(result.next()) {
                short id = result.getShort("fnHostID");
                m_botAction.SQLQueryAndClose( mySQLHost, "UPDATE tblRadio_Host SET fnDuration = " + minute + " WHERE fnHostID = " + id);
            } else {
                m_botAction.sendChatMessage("Host duration of " + m_currentHost + " cannot be recorded. Not in the database table.");
            }

            m_botAction.SQLClose( result );
            this.m_timeStartedToHost = 0;
        } catch ( Exception e ) {
            m_botAction.sendChatMessage("Error occured when registering host duration :" + e.getMessage());
            Tools.printStackTrace(e);
        }

        m_currentHost = "";
    }


    /**
        Load the authorized Hosts and Operators from the configuration files.
    */
    private void load_authorize() {
        try {
            m_botSettings = m_botAction.getBotSettings();
            hosts.clear();
            operators.clear();

            String host[] = m_botSettings.getString( "Hosts" ).split( "," );

            for( int i = 0; i < host.length; i++ )
                hosts.put(host[i].toLowerCase(), host[i]);

            String ops[] = m_botSettings.getString( "Operators" ).split( "," );

            for( int j = 0; j < ops.length; j++ )
                operators.put(ops[j].toLowerCase(), ops[j]);

        } catch (Exception e) {
            Tools.printStackTrace( "Method Failed: ", e );
        }
    }

    /**
        Handle the !setmax command to adjust the limits of the various public request queues.

        @param id is the playerID of the person that sent the message
        @param newValues is the string of new values to be used in setting the queue limits
    */
    private void commandSetMax(int id, String newValues) {
        String[] params = newValues.split(" +");
        int count = params.length;
        int[] nums = new int[count];

        for(int i = 0, j; i < count; i++) {
            try {
                j = Integer.parseInt(params[i]);

                if(j < 0) j = 0;
                else if(j > 100) j = 100;
            } catch(NumberFormatException e) {
                j = 20;
            }

            nums[i] = j;
        }

        switch(count) {
        case 4:
            m_shoutouts.setMax(nums[3]);

        case 3:
            m_questions.setMax(nums[2]);

        case 2:
            m_requests.setMax(nums[1]);

        case 1:
            m_topics.setMax(nums[0]);
            m_botAction.sendPrivateMessage(id, "New limits: Topics=" + m_topics.getMax()
                                           + " Requests=" + m_requests.getMax()
                                           + " Questions=" + m_questions.getMax()
                                           + " Shoutouts=" + m_shoutouts.getMax());
            break;

        default:
            m_botAction.sendPrivateMessage(id, "Too many parameters.");
        }
    }

    /**
        Handle the !addhost command to add a new host to the Radio Bot

        @param name is the name of the person that sent the message
        @param newHost is the name of the new Radio Host to add to the Hosts list
    */
    private void commandAddHost(String name, String newHost, boolean asOp) {

        String hostType = "Hosts";

        if (asOp)
            hostType = "Operators";

        String host = m_botSettings.getString(hostType);

        if ((asOp && operators.containsKey(newHost.toLowerCase())) || (!asOp && hosts.containsKey(newHost.toLowerCase()))) {
            m_botAction.sendSmartPrivateMessage(name, newHost + " is already listed as one of the " + hostType + ".");
            return;
        }

        if (host.length() < 1)
            m_botSettings.put(hostType, newHost);
        else
            m_botSettings.put(hostType, host + "," + newHost);

        m_botAction.sendSmartPrivateMessage(name, "Add " + hostType + ":" + newHost + " successful");
        m_botSettings.save();

        if (asOp)
            m_botAction.sendChatMessage("A new Radio Operator is amongst us! Bow down to " + newHost + "!!!");
        else
            m_botAction.sendChatMessage("Host " + newHost + " has offically joined Radio!");

        load_authorize();
    }

    /**
        Handle the !removehost command to remove a host from the list of valid radio hosts

        @param name is the name of the person that sent the message
        @param host is the name of the radio host to be removed from the Hosts list
    */
    private void commandRemoveHost(String name, String host, boolean asOp) {

        String hostType = "Hosts";
        String hostTypeDisp = "Host";

        if (asOp) {
            hostType = "Operators";
            hostTypeDisp = "Operator";
        }

        HashMap <String, String> hostTypeMap;

        if (asOp)
            hostTypeMap = operators;
        else
            hostTypeMap = hosts;

        if (!hostTypeMap.containsKey(host.toLowerCase())) {
            m_botAction.sendPrivateMessage(name, host + " is not on the list of " + hostType + ".");
            return;
        }

        hostTypeMap.remove(host.toLowerCase());

        String newList = "";
        Iterator<String> it1 = hostTypeMap.values().iterator();

        while( it1.hasNext() ) {
            if( it1.hasNext() )
                newList += (String)it1.next() + ",";
            else
                newList += (String)it1.next();
        }

        m_botSettings.put(hostType, newList);
        m_botSettings.save();
        m_botAction.sendSmartPrivateMessage(name, "Delete " + hostTypeDisp + ": " + host + " successful");

        load_authorize();
    }

    /**
        Handle the !staff command to display the current Ops and Hosts configured for this radio bot

        @param name is the name of the person that sent the message
    */
    public void showHosts (String name) {

        //load_authorize();
        String hops = "Radio Operators: ";
        Iterator<String> it1 = operators.values().iterator();

        while( it1.hasNext() ) {
            if( it1.hasNext() )
                hops += (String)it1.next() + ", ";
            else
                hops += (String)it1.next();
        }

        String ops = "Hosts: ";
        Iterator<String> it2 = hosts.values().iterator();

        while( it2.hasNext() ) {
            if( it2.hasNext() )
                ops += (String)it2.next() + ", ";
            else
                ops += (String)it2.next();
        }

        hops = hops.substring(0, hops.length() - 2);
        ops = ops.substring(0, ops.length() - 2);
        m_botAction.sendPrivateMessage( name, hops );
        m_botAction.sendPrivateMessage( name, ops );
    }

    /**
        Saves the start time for this new Host to both the database and in the local variable for later use.

        @param name is the name of the host that has just started radio hosting
    */
    public void updateStatRecordsHOST(String name) {
        if (!m_botAction.SQLisOperational())
            return;

        //try {
        String[] fields = {
            "fcUserName",
            "fnCount",
            "fdDate",
        };
        String[] values = {
            Tools.addSlashes(name),
            "1",
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),
        };
        m_botAction.SQLInsertInto(mySQLHost, "tblRadio_Host", fields, values);
        //( mySQLHost, null, "INSERT INTO tblRadio_Host (fcUserName, fnCount, fnType, fdDate) VALUES ('"+name+"', 1, 0 CURDATE())" );
        m_botAction.sendSmartPrivateMessage(name, "Host count recorded, Start time enabled..");
        this.m_timeStartedToHost = System.currentTimeMillis();
        /*  } catch ( Exception e ) {
            m_botAction.sendChatMessage("Error occured when registering host count :"+e.getMessage());
            Tools.printStackTrace(e);
            }*/
    }

    /**
        Reset the Radio hosting stats in the database

        @param name
    */
    private void commandResetStats(String name) {
        if( !m_botAction.SQLisOperational())
        {
            m_botAction.sendSmartPrivateMessage(name, "Unable to reset Radio Host Database stats as the Database is currently not available.");
            return;
        }

        try {
            m_botAction.SQLBackgroundQuery( mySQLHost, null, "UPDATE tblRadio_Host SET fnCount = 0 AND fnDuration = 0 AND fdDate = CURDATE()");
            m_botAction.sendSmartPrivateMessage(name, "All Radio host Database Stats have been Reset.");
        } catch ( Exception e ) {
            m_botAction.sendChatMessage("Error occured when registering host count :" + e.getMessage());
            Tools.printStackTrace(e);
        }
    }

    /**
        Handle the !grantzone command to give a zoner to the radio host

        @param name is the name of the person that sent the message
        @param id is the playerID of the person that sent the message
        @param isSmod is a boolean that is true if a smod+ sent the command
    */
    private void commandGrantZone(String name, int id, boolean isSmod) {

        long now = System.currentTimeMillis();

        if (m_alreadyZoned.contains(m_currentHost)) {
            if (now < m_timeOfLastZone + THIRTY_MINUTES && !isSmod) {
                m_botAction.sendPrivateMessage(id, "Sorry, you may only grantzone 30 minutes from the last zone which was "
                                               + ((now - m_timeOfLastZone) / 1000 / 60) + " minutes ago.");
            } else {
                m_alreadyZoned.remove(m_currentHost);
                m_botAction.sendPrivateMessage(id, "Zoner granted to " + m_currentHost);
                m_botAction.sendPrivateMessage(m_currentHost, name + " has granted you another zone message.");

                if (isSmod) {
                    m_timeOfLastZone = 0;
                    m_botAction.sendPrivateMessage(m_currentHost, "As it was a smod+ that granted you the zoner, you may use it as soon as you wish.");
                }
            }
        } else {
            m_botAction.sendPrivateMessage(id, "No current host or host is already granted a zone.");
        }
    }

    private class Poll {

        private String[] poll;
        private int range;
        private HashMap<String, Integer> votes;

        public Poll(String[] poll) {
            this.poll = poll;
            votes = new HashMap<String, Integer>();
            range = poll.length - 1;
            m_botAction.sendArenaMessage("Poll: " + poll[0]);

            for (int i = 1; i < poll.length; i++) {
                m_botAction.sendArenaMessage(i + ": " + poll[i]);
            }

            m_botAction.sendArenaMessage("Private message your answers to " + m_botAction.getBotName());
        }

        public void handlePollCount(String name, String message) {
            try {
                if (!Tools.isAllDigits(message)) {
                    return;
                }

                int vote;

                try {
                    vote = Integer.parseInt(message);
                } catch(NumberFormatException nfe) {
                    m_botAction.sendSmartPrivateMessage(name, "Invalid vote. "
                                                        + "Your vote must be a number corresponding to the choices "
                                                        + "in the poll.");
                    return;
                }

                if(!(vote > 0 && vote <= range)) {
                    return;
                }

                if(votes.containsKey(name))
                    m_botAction.sendSmartPrivateMessage(name, "Your vote has been changed.");
                else
                    m_botAction.sendSmartPrivateMessage(name, "Your vote has been counted.");

                votes.put(name, new Integer(vote));
            } catch(Exception e) {
                m_botAction.sendArenaMessage(e.getMessage());
                m_botAction.sendArenaMessage(e.getClass().getName());
            }
        }

        public void endPoll() {
            m_botAction.sendArenaMessage("The poll has ended! Question: " + poll[0]);

            int[] counters = new int[range + 1];
            Iterator<Integer> iterator = votes.values().iterator();

            while(iterator.hasNext()) {
                counters[iterator.next().intValue()]++;
            }

            for(int i = 1; i < counters.length; i++) {
                m_botAction.sendArenaMessage(i + ". " + poll[i] + ": "
                                             + counters[i]);
            }
        }
    }

    private class AnnounceTask extends TimerTask {
        public void run() {
            for(int i = 0; i < m_announceLength; i++) {
                m_botAction.sendArenaMessage(m_announcement[i]);
            }

            m_botAction.sendSmartPrivateMessage(m_currentHost,
                                                "Shoutouts:" + m_shoutouts.size()
                                                + " Requests:" + m_requests.size()
                                                + " Topics:" + m_topics.size()
                                                + " Questions:" + m_questions.size());
        }
    }

    private class AnnounceArenaTask extends TimerTask {
        public void run() {
            m_botAction.sendArenaMessage("Radio: Host - " + m_currentHost + " - Use !help to RadioBot for commands! - " + (m_url.equals("") ? "" : "  Radio - " + m_url));
        }
    }

    private class RadioQueue {
        private LinkedList<String> m_queue;
        private HashMap<String, String> m_map;
        private int m_maxItems;
        private String m_label;

        public RadioQueue(String label) {
            m_queue = new LinkedList<String>();
            m_map = new HashMap<String, String>();
            m_maxItems = 20;
            m_label = label;
        }

        public void handleAdd(int id, String name, String msg) {
            if(m_maxItems <= 0) {
                m_botAction.sendPrivateMessage(id, m_label + " requests have been disabled by the host.");
            } else if(m_map.containsKey(name)) {
                m_map.put(name, msg);
                m_botAction.sendPrivateMessage(id, "Your " + m_label + " request was updated.");
            } else if(m_queue.size() >= m_maxItems) {
                m_botAction.sendPrivateMessage(id, "Sorry, " + m_label + " request box is full. Try later.");
            } else {
                m_queue.add(name);
                m_map.put(name, msg);
                m_botAction.sendPrivateMessage(id, "Your " + m_label + " request was added.");
            }
        }

        public String remove() {
            if(m_queue.size() == 0)
                return "<empty>";

            String name = m_queue.remove();
            return name + "> " + m_map.remove(name);
        }

        public void setMax(int max) {
            m_maxItems = max;
        }

        public int getMax() {
            return m_maxItems;
        }

        /*  Unused.
            public String peek() {
            if(m_queue.size() == 0)
                return "<empty>";

            String name = m_queue.peek();
            return name + "> " + m_map.get(name);
            }
        */

        public void clear() {
            m_queue.clear();
            m_map.clear();
        }

        public int size() {
            return m_queue.size();
        }
    }

    private final static String[] endHelp = {
        "+---------------------------------------------------------------------------------+"
    };

    private final static String[] pubHelp = {
        "+--------------------------Player Help--------------------------------------------+",
        "|!topic <topic>        - Suggest an idea/topic/poll to the radio host.            |",
        "|!request <artist - title>  - Request a song.                                     |",
        "|!question <question>  - Ask the radio host a question to be answered on air.     |",
        "|!shoutout <shoutout>  - Request a shoutout to the radio host.                    |",
        "|!how                  - Shows how you could host Radio and how its done.         |",
        "|!staff                - Show Radio Staff                                         |",
    };

    private final static String[] staffHelp = {
        "+-------------------------Radio Staff Help----------------------------------------+",
        "|!host                 - Enables hosting commands. Announces you are current host.|",
        "|!status               - Shows bot status.                                        |",
        "|!sex                  - Lets amuse the hosts in chat! - H.M.S.                   |",
    };

    private final static String[] currentRadioHostHelp = {
        "+---------------------Current Radio Host Only-------------------------------------+",
        "|!arena <message>      - Sends an arena message.                                  |",
        "|!zone <message>       - Limited to once a day.                                   |",
        "|!poll <Topic>:<answer1>:<answer2> (and on and on) - Starts a poll.               |",
        "|!endpoll              - Ends a poll and tallies the results.                     |",
        "|!addannounce <msg>    - Adds a line to the announcement. 3 lines max.            |",
        "|!clrannounce          - Clears the announcement.                                 |",
        "|!viewannounce         - View the current announcement.                           |",
        "|!unhost               - Do this when you're done to allow someone else to host.  |",
        "|!setwelcome <msg>     - Sets welcome message (!welcomeoff to disable).           |",
        "|!seturl               - Sets the URL that appears in your announcement.          |",
        "|!ask                  - Sends a ?help asking for another zoner.                  |",
        "|!time                 - Displays how long you have currently hosted for.        |",
        "+---------------------------------------------------------------------------------+",
        "|    Read requests (append a number to retrieve several at once)                  |",
        "+---------------------------------------------------------------------------------|",
        "|!nexttop !nextreq !nextques !nextshout !nextall                                  |",
        "|!clear                - Erases all pending requests.                             |",
        "|!setmax <t> <r> <q> <s> - Sets max queue size for Topics, Requests,              |",
        "| Questions, Shoutouts. (Disable=0)                                               |",
    };

    private final static String[] operatorHelp = {
        "+--------------------------Operator Help------------------------------------------+",
        "|!reset                - Reset Stats of Hosts                                     |",
        "|!addhost              - Adds a host to use RadioBot                              |",
        "|!removehost           - Removes a host to use RadioBot                           |",
        "|!addop                - Adds a new Operator to RadioBot                          |",
        "|!removeop             - Removes an Operator from RadioBot                        |",
        "|!reload               - Reloads Powers from the .cfg of file                     |",
    };

    private final static String[] erHelp = {
        "+-------------------------ER Commands---------------------------------------------+",
        "|!grantzone            - Grants the radio host another zoner.                     |",
        "|                        If granted by smod+, it can be used immediately.         |",
        "|!unhost               - Removes the current host.                                |",
        "|!seturl <url>         - Sets the URL that appears in the announcements.          |",
        "|!setwelcome           - Use this if the host has put an inappropiate welcome msg |",
        "|                        (Also !welcomeoff)                                       |",
    };

    private final static String[] smodHelp = {
        "+-------------------------Smod Commands-------------------------------------------+",
        "|!go <arena>           - Moves bot to <arena>.                                    |",
        "|!die                  - Disconnects bot.                                         |",
        "|!dbcon                - Checks for a database connection                         |",
    };

}
