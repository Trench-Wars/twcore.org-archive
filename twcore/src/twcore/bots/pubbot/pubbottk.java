package twcore.bots.pubbot;

import java.util.Date;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.Iterator;

import twcore.bots.PubBotModule;
import twcore.core.util.Tools;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.events.ArenaList;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.util.ipc.IPCMessage;

/**
    Tracks TKs, warns players who TK excessively, and notifies staff as necessary.
    Operates on a point system.

    Note: Setting HARDASS final to false reduces functionality of this module to
    that of a TK tracker (not issuing disciplinary action).

    Setting IGNORE_FAILSAFES to true is highly recommended.  If you choose to use
    failsafes that warn, setship and notify staff based on number of TKs, be sure
    that you are addressing a problem of someone intentionally TKing and abusing
    the bot, and not just enabling them because it sounds like a good idea.  This
    option can create many problems with players who play in the same arena for
    hours and occasionally TK, but not enough to set off the bot.

    @author qan
*/
public class pubbottk extends PubBotModule {

    private final boolean HARDASS = false;   // True if bot should warn/setship

    private final boolean TATTLER = true;    // True if bot should send msgs to staff to check
    // on players when they are TKing quite a lot.

    private final boolean IGNORE_FAILSAFES = true;  // True if "failsafes" that protect
    // against sneaky TKers should be
    // ignored.  Should be kept at true
    // unless you know what you are doing.

    private final boolean ALLOW_PLAYER_NOTIFY = true;   // True to allow players to notify staff
    // after being TK'd.

    private final int TK_POINTS_NORM = 12;   // Penalty for TKing (any ship but shark or levi)
    private final int TK_POINTS_LEVI = 8;    // Penalty for TKing as a lev
    private final int TK_POINTS_SHARK = 1;   // Penalty for TKing as a shark
    private final int TK_POINTS_REPEAT = 20; // Penalty for Tking same person twice in a row
    //private final int MAX_TK_FREQ = 90;      // Maximum frequency of TKs (in seconds) 10 min
    // after the player's first TK before
    // bot sends a request to player
    private final int AMT_WARNAT = 45;       // Points at which player receives a warning
    private final int AMT_NOTIFYAT = 90;     // Points at which staff is notified
    private final int AMT_TATTLEAT = 125;    // Points at which staff is notified with HARDASS off
    private final int TKNUM_EMERGENCY_WARN = 10;    // # TK's to force a first warning
    private final int TKNUM_EMERGENCY_SETSHIP = 30; // # TK's to force a setship
    private final int TKNUM_EMERGENCY_NOTIFY = 50;  // # TK's to force a notify

    private final int COOLDOWN_SECS = 10;    // Time, in secs, it takes to remove 1 TK point
    private final int DEFAULT_LOG_LENGTH = 15;  // Default max record length of !tklog
    private final int DEFAULT_LOG_TIME = 60;   // Default max time length of !tklogt
    private OperatorList m_opList;           // Access list
    private String currentArena;             // Current arena the host bot is in
    private boolean checkTKs;                // True if TK checking enabled
    private HashMap <String, TKInfo>tkers;   // (String)Name -> (TKInfo)Teamkilling record
    private WeakHashMap <String, TKInfo>oldtkers; // Same as above; stores TKers who leave arena
    // (low-cost abuse prevention).  Old keys dropped regularly.
    private HashMap <String, String>tked;    // (String)Name TKd -> (String)Last name who TKd them
    private TreeSet<String> ignores;

    /**
        Called when the module is loaded for each individual pubbot.
    */
    public void initializeModule() {
        currentArena = m_botAction.getArenaName();
        ignores = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

        if(m_botAction.getBotSettings().getString("Ignores") != null) {
            String[] ignore = m_botAction.getBotSettings().getString("Ignores").trim().split(",");

            for (String i : ignore)
                ignores.add(i);
        }

        // TODO: Add to CFG
        if( currentArena.toLowerCase().equals("tourny") || currentArena.toLowerCase().startsWith("base") || currentArena.toLowerCase().equals("duel") )
            checkTKs = false;
        else
            checkTKs = true;

        tkers = new HashMap<String, TKInfo>();
        oldtkers = new WeakHashMap<String, TKInfo>();
        tked = new HashMap<String, String>();

        m_opList = m_botAction.getOperatorList();

        // Must be enabled or the bot won't register kills properly
        m_botAction.sendUnfilteredPublicMessage( "*relkills 1" );
    }


    /**
        Requests all events needed.

        @param eventRequester Standard.
    */
    public void requestEvents( EventRequester eventRequester ) {
        eventRequester.request(EventRequester.ARENA_LIST);
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.PLAYER_DEATH);
        eventRequester.request(EventRequester.PLAYER_LEFT);
    }


    /**
        Handles IPC messages from senders with names (nonbots).

        @param botSender The bot the msg was passed through.
        @param sender Person that sent the msg.
        @param message The msg itself.
    */
    public void handlePlayerIPC( String botSender, String sender, String message ) {
        String command = message.toLowerCase();

        try {
            if(command.equals("!tk on"))
                doTKset( 1 );

            if(command.equals("!tk off"))
                doTKset( 0 );

            if(command.equals("!tk"))
                doTKset( -1 );
        }
        catch(Exception e) {
            m_botAction.sendChatMessage(e.getMessage());
        }
    }


    /**
        Set TK module on or off.

        @param setting 1 = on, 0 = off, -1 = toggle
    */
    public void doTKset( int setting ) {
        if( setting == -1 ) {
            if( checkTKs )
                setting = 0;
            else
                setting = 1;
        }

        if( setting == 1 ) {
            if( !checkTKs ) {
                m_botAction.sendChatMessage( "TK checking enabled in " + currentArena + ".");
                checkTKs = true;
            }
        } else if( setting == 0 ) {
            if( checkTKs ) {
                m_botAction.sendChatMessage( "TK checking disabled in " + currentArena + ".");
                checkTKs = false;
            }
        }
    }


    /**
        Change the arena name held in memory whenever the arena is changed.
    */
    public void handleEvent( ArenaList event ) {
        currentArena = event.getCurrentArenaName();
    }


    /**
        This method handles an InterProcessEvent.

        @param event is the InterProcessEvent to handle.
    */
    public void handleEvent( InterProcessEvent event ) {
        // If the event.getObject() is anything else then the IPCMessage (pubbotchatIPC f.ex) then return
        if(event.getObject() instanceof IPCMessage == false) {
            return;
        }

        IPCMessage ipcMessage = (IPCMessage) event.getObject();
        String message = ipcMessage.getMessage();
        String recipient = ipcMessage.getRecipient();
        String sender = ipcMessage.getSender();
        String botSender = event.getSenderName();

        try {
            if(recipient == null || recipient.equals(m_botAction.getBotName())) {
                if(sender == null)
                    return;         // not handling bot IPC
                else
                    handlePlayerIPC(botSender, sender, message);
            }
        }
        catch(Exception e) {
            m_botAction.sendChatMessage(e.getMessage());
        }
    }


    /**
        Handles command PMs to the bot.

        @param event The Message event containing all necessary information derived
        from the incoming packet.
    */
    public void handleEvent( Message event ) {

        String message = event.getMessage();

        if( event.getMessageType() == Message.PRIVATE_MESSAGE ) {
            String name = m_botAction.getPlayerName( event.getPlayerID() );

            if (ignores.contains(name))
                return;

            if( m_opList.isZH( name ) ) {
                if (message.equals( "!help" )) {
                    m_botAction.sendPrivateMessage( name, "!tkinfo name         - Gives TK information on 'name', if they have TK'd");
                    m_botAction.sendPrivateMessage( name, "!tklog name:#        - Shows log of last # TKs (use no :# for default=" + DEFAULT_LOG_LENGTH + ")");
                    m_botAction.sendPrivateMessage( name, "!tklogt name:#       - Shows log of TKs in last # minutes (default=" + DEFAULT_LOG_TIME + ")");
                    m_botAction.sendPrivateMessage( name, "!tklogp name:target  - Shows log of all TKs made against a specific target");
                    m_botAction.sendPrivateMessage( name, "!ignore name         - Ignores or un-ignores tk reporting attempts from name");
                    m_botAction.sendPrivateMessage( name, "!ignores             - Lists ignored players");

                } else if (message.startsWith( "!tkinfo " )) {
                    msgTKInfo( name, message.substring(8).toLowerCase() );
                } else if (message.startsWith( "!tklog " )) {
                    cmdTKLog(name, message.substring(7).toLowerCase(), 0);
                } else if (message.startsWith( "!tklogt " )) {
                    cmdTKLog(name, message.substring(8).toLowerCase(), 1);
                } else if (message.startsWith( "!tklogp " )) {
                    cmdTKLog(name, message.substring(8).toLowerCase(), 2);
                } else if (message.startsWith("!ignore "))
                    cmd_ignore(name, message);
                else if (message.equalsIgnoreCase("!ignores"))
                    cmd_ignores(name);
            }

            if( message.equals( "report") && ALLOW_PLAYER_NOTIFY) {
                doManualPlayerNotify( name );
            }
        }
    }

    public void cmd_ignore(String name, String cmd) {
        if (cmd.length() < "!ignore n".length())
            return;

        BotSettings cfg = m_botAction.getBotSettings();
        String p = cmd.substring(8);

        if (ignores.remove(p)) {
            String igns = "";

            for (String i : ignores)
                igns += i + ",";

            cfg.put("Ignores", igns);
            cfg.save();
            m_botAction.sendPrivateMessage(name, "" + p + " is no longer ignored.");
        } else {
            ignores.add(p);
            String igns = "";

            for (String i : ignores)
                igns += i + ",";

            cfg.put("Ignores", igns);
            cfg.save();
            m_botAction.sendPrivateMessage(name, "" + p + " has been IGNORED.");
        }
    }

    public void cmd_ignores(String name) {

        String igns = "";

        for (String i : ignores)
            igns += i + ",";

        m_botAction.sendPrivateMessage(name, "Ignoring: " + igns);
    }


    /**
        Messages a staff member the information on a TKer.
        @param staffname Staff member to msg
        @param tkname Name of TKer
    */
    public void msgTKInfo( String staffname, String tkname ) {
        TKInfo tker = getTKerFromName(staffname, tkname);

        if( tker == null )
            return;

        m_botAction.sendSmartPrivateMessage( staffname, "'" + tker.getName() + "' TK Record    [First record " + Tools.getTimeDiffString(tker.getFirstTKTime(), true) + " ago]" );
        m_botAction.sendSmartPrivateMessage( staffname, "TKs:  " + tker.getNumTKs() + (HARDASS ? ("     Warns:  " + tker.getNumWarns()) : "") );
        //m_botAction.sendSmartPrivateMessage( staffname, "Last player TKd:  " + tker.getLastTKd() + "    [" + Tools.getTimeDiffString(tker.getLastTKTime(), true) + " ago]" );
        long frequency = (((System.currentTimeMillis() - tker.getFirstTKTime()) / 1000) / tker.getNumTKs());
        String avgTime = "(Avg 1 TK every ";

        if( frequency > 60 )
            avgTime += frequency / 60 + " min, ";

        avgTime += frequency % 60 + " seconds.)";
        m_botAction.sendSmartPrivateMessage( staffname, avgTime);

        String pointsmsg = "";

        if( tker.wasStaffNotified() ) {
            pointsmsg = "TK Points at current / at notify:  " + tker.getTKpoints() + " / " + tker.getTKpointsAtNotify();
            m_botAction.sendSmartPrivateMessage( staffname, "  - Staff has been notified.");

            if( tker.wasSetShipped() )
                m_botAction.sendSmartPrivateMessage( staffname, "  - Player has been setshipped.");
        } else {
            pointsmsg = "TK Points at current:  " + tker.getTKpoints();

            if( tker.wasSetShipped() )
                m_botAction.sendSmartPrivateMessage( staffname, "  - Player has been setshipped.");
        }

        if( HARDASS || TATTLER )
            m_botAction.sendSmartPrivateMessage( staffname, pointsmsg );

        if( tker.wasRepeatKiller() ) {
            m_botAction.sendSmartPrivateMessage( staffname, "Potential 'target' player:  " + tker.getRepeatTKd() );

            if( tker.getNumRepeats() > 2 )
                m_botAction.sendSmartPrivateMessage( staffname, "  - TKd this player twice in a row, then TKd them " + (tker.getNumRepeats() - 2) + " more time(s)." );
            else
                m_botAction.sendSmartPrivateMessage( staffname, "  - TKd this player twice in a row." );
        }

        printTKLog( staffname, tker, 5 );
    }


    /**
        Print a TK log.
        @param staffname
        @param cmd
        @param type 0=number of TKs (!tklog); 1=length of time (!tklogt); 2=tked player (!tklogp)
    */
    public void cmdTKLog( String staffname, String cmd, int type ) {
        String[] cmds = cmd.split( ":", 2 );
        int var = 0;
        TKInfo tker = getTKerFromName( staffname, cmds[0] );

        if( tker == null )
            return;

        if( cmds.length == 1 ) {
            if( type == 0 ) {
                printTKLog(staffname, tker, DEFAULT_LOG_LENGTH);
            } else if( type == 1 ) {
                printTKLogByTime(staffname, tker, DEFAULT_LOG_TIME);
            } else {
                m_botAction.sendSmartPrivateMessage( staffname, "Usage: !tklog tkername:targetname" );
                return;
            }
        } else {
            if( type == 0 || type == 1 ) {
                try {
                    var = Integer.parseInt( cmds[1] );
                } catch( Exception e) {
                    m_botAction.sendSmartPrivateMessage( staffname, "Specify a valid number as the 2nd argument (e.g., !tklog name:50 or !tklogt name:30). Using defaults instead." );

                    if( type == 0 )
                        printTKLog(staffname, tker, DEFAULT_LOG_LENGTH);
                    else
                        printTKLogByTime(staffname, tker, DEFAULT_LOG_TIME);

                    return;
                }

                if( type == 0 )
                    printTKLog(staffname, tker, var);
                else
                    printTKLogByTime(staffname, tker, var);
            } else {
                printTKLogByName(staffname, tker, cmds[1]);
            }
        }

    }


    /**
        Print out a log of the last X number of TKs.
        @param staffname
        @param tker
        @param numToPrint
    */
    public void printTKLog( String staffname, TKInfo tker, int numToPrint ) {
        TreeMap <Long, String>tklog = tker.getTKLog();

        if( numToPrint < 1 || numToPrint > 100 )
            return;

        Long lasttime = System.currentTimeMillis();
        String lastname;
        m_botAction.sendSmartPrivateMessage( staffname, "[TK LOG for '" + tker.getName() + "' of last " + numToPrint + " players TK'd]" );

        do {
            lasttime = tklog.lowerKey(lasttime);    // Get next biggest key entry

            if( lasttime == null )
                return;

            lastname = tklog.get(lasttime);

            if( lastname != null )
                m_botAction.sendSmartPrivateMessage( staffname, Tools.formatString(lastname, 25) + Tools.getTimeDiffString(lasttime, true) + " ago");

            numToPrint--;
        } while (lastname != null && numToPrint > 0 );
    }


    /**
        Print out a log of all TKs occurring in the last X minutes.
        @param staffname
        @param tker
        @param numToPrint
    */
    public void printTKLogByTime( String staffname, TKInfo tker, int minutes ) {
        TreeMap <Long, String>tklog = tker.getTKLog();

        if( minutes < 0 || minutes > 24 * 60 )
            return;

        Long timelimit = System.currentTimeMillis() - (minutes * Tools.TimeInMillis.MINUTE);
        Long lasttime = System.currentTimeMillis();
        String lastname;
        m_botAction.sendSmartPrivateMessage( staffname, "[TK LOG for '" + tker.getName() + "' of players TK'd in last " + minutes + " minutes]" );

        do {
            lasttime = tklog.lowerKey(lasttime);    // Get next biggest key entry

            if( lasttime == null || lasttime < timelimit )
                return;

            lastname = tklog.get(lasttime);

            if( lastname != null )
                m_botAction.sendSmartPrivateMessage( staffname, Tools.formatString(lastname, 25) + Tools.getTimeDiffString(lasttime, true) + " ago");
        } while (lastname != null );
    }


    /**
        Print out a log of all TKs on player X.
        @param staffname
        @param tker
        @param numToPrint
    */
    public void printTKLogByName( String staffname, TKInfo tker, String tkedPlayer ) {
        String tkedPlayerFullName = m_botAction.getFuzzyPlayerName(tkedPlayer);

        if(tkedPlayerFullName == null) {
            tkedPlayerFullName = tkedPlayer;
        }

        TreeMap <Long, String>tklog = tker.getTKLog();
        Long lasttime = System.currentTimeMillis();
        String lastname;

        m_botAction.sendSmartPrivateMessage( staffname, "[TK LOG for '" + tker.getName() + "' showing times for all TKs against player '" + tkedPlayerFullName + "']" );

        do {
            lasttime = tklog.lowerKey(lasttime);    // Get next biggest key entry

            if( lasttime == null )
                return;

            lastname = tklog.get(lasttime);

            if( lastname != null && lastname.equalsIgnoreCase( tkedPlayerFullName ))
                m_botAction.sendSmartPrivateMessage( staffname, Tools.formatString(lastname, 25) + Tools.getTimeDiffString(lasttime, true) + " ago");
        } while (lastname != null );
    }


    /**
        Helper fuction (extracted from msgTKInfo) that gets a TK log given a player's name.
        @param staffname
        @param tkname
        @return
    */
    public TKInfo getTKerFromName( String staffname, String tkname ) {
        TKInfo tker = tkers.get( tkname );

        if( tker == null ) {
            Iterator<Player> i = m_botAction.getPlayerIterator();
            Player searchPlayer = null;

            while( i.hasNext() && searchPlayer == null ) {
                searchPlayer = i.next();

                // For inexact names
                if(! searchPlayer.getPlayerName().toLowerCase().startsWith( tkname ) ) {
                    // Long name hack
                    if(! tkname.startsWith( searchPlayer.getPlayerName().toLowerCase() ) ) {
                        searchPlayer = null;
                    }
                }
            }

            if( searchPlayer == null ) {
                tker = oldtkers.get( tkname );

                if( tker == null ) {
                    m_botAction.sendSmartPrivateMessage( staffname, "Player not in current arena or backlog. If searching backlog, use full name." );
                    return null;
                } else {
                    m_botAction.sendSmartPrivateMessage( staffname, "Player found in backlog (NOT presently in arena):" );
                }
            } else {
                tker = tkers.get( searchPlayer.getPlayerName().toLowerCase());

                if( tker == null ) {
                    tker = oldtkers.get( tkname );

                    if( tker == null ) {
                        m_botAction.sendSmartPrivateMessage( staffname, "'" + searchPlayer.getPlayerName() + "' has no TK record. Use full name to search backlog." );
                        return null;
                    } else {
                        m_botAction.sendSmartPrivateMessage( staffname, "Player found in backlog (NOT presently in arena):" );
                    }
                }
            }
        }

        return tker;
    }


    /**
        Sends a manual warning to moderators from a player that claims a TK has
        been made intentionally.
    */
    public void doManualPlayerNotify( String name ) {
        String tker = tked.get( name );

        if( tker == null )
            return;

        TKInfo info = tkers.get( tker.toLowerCase() );

        if( info == null ) {
            m_botAction.sendPrivateMessage( name, "Error reporting player '" + tker + "' - player not found.  Please use the ?cheater command to manually notify staff." );
            return;
        }

        if( info.playerHasNotified() ) {
            m_botAction.sendPrivateMessage( name, "Staff has already been notified about '" + tker + "'.  Please use ?cheater if the problem continues or you do not receive a response." );
            return;
        }

        String msg = "?cheater TK Report: " + name + " is reporting " + tker + " for intentional TK.  (" + info.getNumTKs() + " total TKs)";
        m_botAction.sendUnfilteredPublicMessage( msg );
        m_botAction.sendPrivateMessage( name, tker + " was reported to staff for intentionally teamkilling.  If a staff member does not contact you, please use ?cheater", 1 );
        info.setNotified();
        info.setPlayerHasNotified();
    }


    /**
        Main grunt of the module.  If a person kills someone on the same freq,
        add a TK to their preexisting TKInfo obj, or make a new one if this is
        their first TK. (aw, virgins)
        @param event The event object.
    */
    public void handleEvent( PlayerDeath event ) {
        if( !checkTKs )
            return;

        TKInfo tk = null;

        Player killed = m_botAction.getPlayer( event.getKilleeID() );
        Player killer = m_botAction.getPlayer( event.getKillerID() );

        if( killed == null || killer == null || killed.getFrequency() != killer.getFrequency() )
            return;

        if( tkers != null )
            tk = tkers.get( killer.getPlayerName().toLowerCase() );

        if( tk != null ) {
            tk.addTK( killer.getShipType(), killed.getPlayerName() );
        } else {
            TKInfo newtk = new TKInfo( killer.getPlayerName() );
            newtk.addTK( killer.getShipType(), killed.getPlayerName() );

            tkers.put( newtk.getName(), newtk );
        }

        if( ALLOW_PLAYER_NOTIFY ) {
            // Tell players who are TKd for the first time that they can notify staff
            if( tked.remove( killed.getPlayerName() ) == null )
                m_botAction.sendPrivateMessage( event.getKilleeID(), "You were TK'd by " + killer.getPlayerName() + ".  Type ::report to notify staff of any non-accidental TKs." );

            tked.put( killed.getPlayerName(), killer.getPlayerName() );
        }
    }


    /**
        Remove players from the TK list if they leave the arena, and add them to
        the temporary old TKer storage.
        @param event The event object.
    */
    public void handleEvent( PlayerLeft event ) {
        if( !checkTKs )
            return;

        String pn = m_botAction.getPlayerName( event.getPlayerID() ).toLowerCase();
        TKInfo oldtker = tkers.remove( pn );

        if( oldtker != null )
            oldtkers.put( pn, oldtker );
    }


    /**
        Check if player has stored info on them.  If so, add them back into
        the mix.  Prevents abuse by leaving/reentering arena.
        @param event The event object.
    */
    public void handleEvent( PlayerEntered event ) {
        if( !checkTKs )
            return;

        String pn = m_botAction.getPlayerName( event.getPlayerID() ).toLowerCase();
        TKInfo tker = oldtkers.remove( pn );

        if( tker != null )
            tkers.put( pn, tker );
    }


    /**
        Unimplemented.
    */
    public void cancel() {
    }


    /**
        Used to store info on TKers.  Operates on a point system.

        Defaults:

        Shark TKs ..........................  +7 points
        Jav/Levi TKs ....................... +12 points
        TKing same person twice in a row ... +20 points
        Shipset ............................ Each TK's points doubled
        Staff notified ..................... (records point total @ notified)

        SPECIALS (REVISE)
        Every 10 seconds .................... -1 point
        4 TKs and no warnings .............. +20 points (auto warning)
        8 TKs and no setship ............... +40 points (auto setship)
        4 warns and no setship ............. +40 points (auto setship)

        @author qan
    */
    private class TKInfo {
        private String m_playerName;     // name of TKer
        private TreeMap <Long, String>m_tkLog;  // Log of TKs with timestamps
        private String m_lastTKd = "";   // last person TKd by this person
        private String m_lastRepeatTK;   // last person "repeat" TK by this person
        private int m_TKpoints = 0;      // current amount of TK "points"
        private int m_ptsAtNotify = 0;   // points TKer has when staff is notified
        private int m_TKs = 0;           // total # of TKs made
        private int m_warns = 0;         // total # warns given
        private int m_repeat = 0;        // total # "streak" TKs of given individual
        private boolean m_staffNotified = false;    // true if staff have been notified
        private boolean m_setShipped = false;       // true if player has been setshipped
        private boolean m_repeatKiller = false;     // true if killed same person twice
        // in a row
        //private boolean m_requestedToChange = false;// true if bot has requested player to change ships
        private boolean m_playerHasNotified = false;// true if last TKd notified staff
        private long m_lastTKTime;                  // Last systemclock MS person TKd
        private long m_firstTKTime;                 // Time started TKing
        private long m_lastWarn;                    // Time of last warning

        /**
            Create a new TK object whenever a TKer is identified.
        */
        public TKInfo( String name ) {
            m_playerName = name.toLowerCase();
            m_firstTKTime = System.currentTimeMillis();
            m_lastTKTime = System.currentTimeMillis();
            m_tkLog = new TreeMap<Long, String>();
        }


        /**
            Store info on a TK based on shipnumber used to TK.
            - At 4 TKs and 0 warns, a warning is given.
            - At 8 TKs and no setship, the player is set to a different ship.
            - At 10 TKs, if staff has not yet been notified, they are.
            @param shipnum Ship number of the TKer.
            @param playerTKd The name of the player who was TKd.
        */
        public void addTK( int shipnum, String playerTKd ) {
            calculatePointLoss();
            m_lastTKTime = System.currentTimeMillis();

            m_TKs++;

            if( shipnum == 8 ) {
                m_TKpoints += TK_POINTS_SHARK;

                if( m_setShipped == true )
                    m_TKpoints += TK_POINTS_SHARK;  // counts for double if you've been setshipped
            } else if( shipnum == 4 ) {
                m_TKpoints += TK_POINTS_LEVI;

                if( m_setShipped == true )
                    m_TKpoints += TK_POINTS_LEVI;   // counts for double if you've been setshipped
            } else {
                m_TKpoints += TK_POINTS_NORM;

                if( m_setShipped == true )
                    m_TKpoints += TK_POINTS_NORM;   // counts for double if you've been setshipped
            }

            // System to find out of one person is being targetted.  Activated
            // by one person being TKd twice in a row.  After that records every
            // additional TK on that person, regardless of whether it's consecutive.
            if( playerTKd.equals( m_lastRepeatTK ) ) {
                m_repeat += 1;

                if( shipnum != 4 && shipnum != 8 )
                    m_TKpoints += TK_POINTS_REPEAT;
            } else if( playerTKd.equals( m_lastTKd ) ) {
                m_repeatKiller = true;
                m_lastRepeatTK = m_lastTKd;
                m_repeat = 2;

                if( shipnum != 4 && shipnum != 8 )
                    m_TKpoints += TK_POINTS_REPEAT;
            }

            m_tkLog.put(System.currentTimeMillis(), playerTKd);
            m_lastTKd = playerTKd;
            m_playerHasNotified = false;

            /*
                TODO: Frequency tracking.  Code in progress.
                long first = new Date().getTime() - getFirstTKTime();
                long frequency = (((new Date().getTime() - getFirstTKTime()) / 1000) / getNumTKs());
                if( first > (10 * 60) && frequency <= 60 )
            */


            // Here is where ends the "neutered" version of the bot for info gathering only

            if( TATTLER && !HARDASS ) {
                if( m_TKpoints >= AMT_TATTLEAT )
                    notifyStaff();
            }


            if( HARDASS ) {
                if( m_setShipped && m_TKpoints >= AMT_NOTIFYAT ) {
                    notifyStaff();
                } else if( m_TKpoints >= AMT_NOTIFYAT) {
                    setTKerShip();
                } else if( m_TKpoints >= AMT_WARNAT ) {
                    if( IGNORE_FAILSAFES == false && m_warns >= 4 && m_setShipped )
                        notifyStaff();
                    else if( IGNORE_FAILSAFES == false && m_warns >= 4 )
                        setTKerShip();
                    else
                        addWarn();

                } else if( IGNORE_FAILSAFES ) {
                    return;

                    // Below: "Failsafes" for players attempting to cheat the system
                } else if( m_TKs >= TKNUM_EMERGENCY_NOTIFY && m_staffNotified == false ) {
                    if( m_TKpoints < AMT_NOTIFYAT )
                        m_TKpoints = AMT_NOTIFYAT;

                    notifyStaff();
                } else if( m_TKs >= TKNUM_EMERGENCY_SETSHIP && m_setShipped == false ) {
                    if( m_TKpoints < AMT_NOTIFYAT )
                        m_TKpoints = AMT_NOTIFYAT;

                    setTKerShip();
                } else if( m_TKs >= TKNUM_EMERGENCY_WARN && m_warns == 0 ) {
                    if( m_TKpoints < AMT_WARNAT )
                        m_TKpoints = AMT_WARNAT;

                    addWarn();
                }
            }
        }


        /**
            Calculates the number of points a player has lost over time since the last TK.
        */
        public void calculatePointLoss() {
            long diff = new Date().getTime() - m_lastTKTime;

            if( diff <= 0 )
                return;

            long diffsecs = diff / 1000;

            if( diffsecs <= 0 )
                return;

            long pointloss = diffsecs / COOLDOWN_SECS;

            m_TKpoints -= pointloss;

            if( m_TKpoints < 0 )
                m_TKpoints = 0;
        }


        /**
            Sets ship to a non-TKable ship.  This almost always happens before staff is notified.
        */
        public void setTKerShip() {
            addWarn();
            m_botAction.setShip( m_playerName, 1 );
            m_botAction.sendPrivateMessage( m_playerName, "NOTICE: Your ship has been automatically changed due to excessive killing of teammates.", 1 );
            m_setShipped = true;
        }


        /**
            Add to player warning count (not with *warn), and send a warning
            of appropriate severity.  If warned in the past 30 seconds, the
            warning will be ignored.
        */
        public void addWarn() {
            // If warned in the past 30 seconds, ignore warning
            if( m_lastWarn > new Date().getTime() - 30000 )
                return;

            m_lastWarn = new Date().getTime();

            m_warns++;
            sendWarn();
        }


        /**
            Sends a warning message to player depending on number of past warns.
        */
        public void sendWarn() {
            if( m_warns == 1 )
                m_botAction.sendPrivateMessage( m_playerName, "NOTICE: Excessive and intentional team killing (TKing) are prohibited in Trench Wars.  Ships with names in yellow are your own teammates.  Please try not to kill them.", 1 );
            else if( m_warns == 2 )
                m_botAction.sendPrivateMessage( m_playerName, "WARNING: Team killing is not allowed in Trench Wars.  If you continue to kill players on your own frequency (yellow), you may be forced to change ships, or may be banned.", 2 );
            else if( m_warns == 3 )
                m_botAction.sendPrivateMessage( m_playerName, "WARNING!  Continuing to kill your teammates will result in a ship change.  Please cease immediately.", 2 );
            else if( m_warns == 4 )
                m_botAction.sendPrivateMessage( m_playerName, ">>> WARNING! <<<  Continuing to kill your teammates puts you in serious danger of being banned from Trench Wars!", 2 );
            else if( m_warns > 4 )
                m_botAction.sendPrivateMessage( m_playerName, ">>> WARNING! <<<  Moderators will be notified, and you may be banned if you continue to TK!!", 2 );
        }


        /**
            Send a notification of repeated TKs to staff.
        */
        public void notifyStaff() {
            if( m_staffNotified == true ) {
                if( m_TKs % 100 != 0 )
                    return;
            }

            m_staffNotified = true;
            m_ptsAtNotify = m_TKpoints;

            // Do not add on additional warns for this particular warn (so as not to throw
            // off numbers for staff reviewing it).
            sendWarn();

            String msg;

            if( HARDASS ) {
                msg = "?cheater " + m_playerName + " - TKs: " + m_TKs + ", BotWarns: " + m_warns;

                if( m_repeatKiller )
                    msg = msg + " (player '" + m_lastRepeatTK + "' TK'd " + m_repeat + " times)";
            } else {
                msg = "?cheater Possible TKer: [" + m_playerName + "]  TKs: " + m_TKs;

                if( m_repeatKiller )
                    msg = msg + " ... (player '" + m_lastRepeatTK + "' TK'd " + m_repeat + " times)";
            }

            m_botAction.sendUnfilteredPublicMessage( msg );
        }


        // Getter methods
        public String getName() {
            return m_playerName;
        }

        public TreeMap<Long, String> getTKLog() {
            return m_tkLog;
        }

        @SuppressWarnings("unused")
        public String getLastTKd() {
            return m_lastTKd;
        }

        public String getRepeatTKd() {
            return m_lastRepeatTK;
        }

        public int getNumTKs() {
            return m_TKs;
        }

        public int getNumWarns() {
            return m_warns;
        }

        public int getTKpoints() {
            return m_TKpoints;
        }

        public int getTKpointsAtNotify() {
            return m_ptsAtNotify;
        }

        public int getNumRepeats() {
            return m_repeat;
        }

        public boolean wasStaffNotified() {
            return m_staffNotified;
        }

        public boolean wasSetShipped() {
            return m_setShipped;
        }

        public boolean wasRepeatKiller() {
            return m_repeatKiller;
        }

        @SuppressWarnings("unused")
        public long getLastTKTime() {
            return m_lastTKTime;
        }

        public long getFirstTKTime() {
            return m_firstTKTime;
        }

        public boolean playerHasNotified() {
            return m_playerHasNotified;
        }

        public void setPlayerHasNotified() {
            m_playerHasNotified = true;
        }

        public void setNotified() {
            m_staffNotified = true;
        }
    }
}
