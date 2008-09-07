package twcore.bots.pubbot;

import java.util.Date;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.Iterator;

import twcore.bots.PubBotModule;
import twcore.core.util.Tools;
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
 * Tracks TKs, warns players who TK excessively, and notifies staff as necessary.
 * Operates on a point system.
 *
 * Note: Setting HARDASS final to false reduces functionality of this module to
 * that of a TK tracker (not issuing disciplinary action).
 *
 * Setting IGNORE_FAILSAFES to true is highly recommended.  If you choose to use
 * failsafes that warn, setship and notify staff based on number of TKs, be sure
 * that you are addressing a problem of someone intentionally TKing and abusing
 * the bot, and not just enabling them because it sounds like a good idea.  This
 * option can create many problems with players who play in the same arena for
 * hours and occasionally TK, but not enough to set off the bot.
 *
 * @author qan
 */
public class pubbottk extends PubBotModule {

    private final boolean HARDASS = false;   // True if bot should warn, setship and
                                             // notify staff.  If set to false, bot only
                                             // records info about TKs.

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
    private final int TKNUM_EMERGENCY_WARN = 10;    // # TK's to force a first warning
    private final int TKNUM_EMERGENCY_SETSHIP = 30; // # TK's to force a setship
    private final int TKNUM_EMERGENCY_NOTIFY = 50;  // # TK's to force a notify

    private final int COOLDOWN_SECS = 7;     // Time, in secs, it takes to remove 1 TK point
    private OperatorList m_opList;           // Access list
    private String currentArena;             // Current arena the host bot is in
    private boolean checkTKs;                // True if TK checking enabled
    private HashMap <String,TKInfo>tkers;    // (String)Name -> (TKInfo)Teamkilling record
    private WeakHashMap <String,TKInfo>oldtkers; // Same as above; stores TKers who leave arena
                                                 // (low-cost abuse prevention).  Old keys dropped regularly.
    private HashMap <String,String>tked;     // (String)Name TKd -> (String)Last name who TKd them

    /**
     * Called when the module is loaded for each individual pubbot.
     */
    public void initializeModule() {
        currentArena = m_botAction.getArenaName();

        // TODO: Add to CFG
        if( currentArena.toLowerCase().equals("tourny") || currentArena.toLowerCase().startsWith("base") || currentArena.toLowerCase().equals("duel") )
            checkTKs = false;
        else
            checkTKs = true;

        tkers = new HashMap<String,TKInfo>();
        oldtkers = new WeakHashMap<String,TKInfo>();
        tked = new HashMap<String,String>();

        m_opList = m_botAction.getOperatorList();

        // Must be enabled or the bot won't register kills properly
        m_botAction.sendUnfilteredPublicMessage( "*relkills 1" );
    }


    /**
     * Requests all events needed.
     *
     * @param eventRequester Standard.
     */
    public void requestEvents( EventRequester eventRequester ) {
        eventRequester.request(EventRequester.ARENA_LIST);
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.PLAYER_DEATH);
        eventRequester.request(EventRequester.PLAYER_LEFT);
    }


    /**
     * Handles IPC messages from senders with names (nonbots).
     *
     * @param botSender The bot the msg was passed through.
     * @param sender Person that sent the msg.
     * @param message The msg itself.
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
     * Set TK module on or off.
     *
     * @param setting 1 = on, 0 = off, -1 = toggle
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
     * Change the arena name held in memory whenever the arena is changed.
     */
    public void handleEvent( ArenaList event ) {
        currentArena = event.getCurrentArenaName();
    }


    /**
     * This method handles an InterProcessEvent.
     *
     * @param event is the InterProcessEvent to handle.
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
     * Handles command PMs to the bot.
     *
     * @param event The Message event containing all necessary information derived
     * from the incoming packet.
     */
    public void handleEvent( Message event ){

        String message = event.getMessage();
        if( event.getMessageType() == Message.PRIVATE_MESSAGE ){
            String name = m_botAction.getPlayerName( event.getPlayerID() );
            if( m_opList.isBot( name ) ) {

                if( message.equals( "!help" )){
                    m_botAction.sendPrivateMessage( name, "Pubbot TK Module" );
                	m_botAction.sendPrivateMessage( name, "!help        - this message");
                	m_botAction.sendPrivateMessage( name, "!tkinfo name - gives TK information on 'name', if they have TK'd");

                } else if( message.startsWith( "!tkinfo " )){
                    String tkname = message.substring( 8 ).toLowerCase();
                    if( tkname == null )
           		        m_botAction.sendPrivateMessage( name, "Formatting error.  Please use format: !tkinfo playername" );
                    else
           		        msgTKInfo( name, tkname );
                }
            }
            if( message.equals( "report") && ALLOW_PLAYER_NOTIFY == true ) {
                doManualPlayerNotify( name );
            }
        }
    }


    /**
     * Messages a staff member the information on a TKer.
     * @param staffname Staff member to msg
     * @param tkname Name of TKer
     */
    public void msgTKInfo( String staffname, String tkname ) {
    	TKInfo tker = tkers.get( tkname );

        if( tker == null ){
            Iterator<Player> i = m_botAction.getPlayerIterator();
            Player searchPlayer = null;
            while( i.hasNext() && searchPlayer == null ){
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
    			m_botAction.sendSmartPrivateMessage( staffname, "Player not found.  Please verify the person is in the arena." );
                return;
            } else {
                tker = tkers.get( searchPlayer.getPlayerName().toLowerCase());
                if( tker == null ) {
        			m_botAction.sendSmartPrivateMessage( staffname, "Teamkill record not found.  Please check the name and verify they have teamkilled." );
                    return;
                }
            }
        }

    	m_botAction.sendSmartPrivateMessage( staffname, "'" + tker.getName() + "' TK Record    [First record " + Tools.getTimeDiffString(tker.getFirstTKTime(), true) + " ago]" );
		m_botAction.sendSmartPrivateMessage( staffname, "TKs:  " + tker.getNumTKs() + (HARDASS ? ("     Warns:  " + tker.getNumWarns()) : "") );
    	m_botAction.sendSmartPrivateMessage( staffname, "Last player TKd:  " + tker.getLastTKd() + "    [" + Tools.getTimeDiffString(tker.getLastTKTime(), true) + " ago]" );
        long frequency = (((new Date().getTime() - tker.getFirstTKTime()) / 1000) / tker.getNumTKs());
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

        if( HARDASS )
            m_botAction.sendSmartPrivateMessage( staffname, pointsmsg );
		if( tker.wasRepeatKiller() ) {
			m_botAction.sendSmartPrivateMessage( staffname, "Potential 'target' player:  " + tker.getRepeatTKd() );
			if( tker.getNumRepeats() > 2 )
				m_botAction.sendSmartPrivateMessage( staffname, "  - TKd this player twice in a row, then TKd them " + (tker.getNumRepeats() - 2) + " more time(s)." );
			else
			    m_botAction.sendSmartPrivateMessage( staffname, "  - TKd this player twice in a row." );
		}
    }


    /**
     * Sends a manual warning to moderators from a player that claims a TK has
     * been made intentionally.
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
     * Main grunt of the module.  If a person kills someone on the same freq,
     * add a TK to their preexisting TKInfo obj, or make a new one if this is
     * their first TK. (aw, virgins)
     * @param event The event object.
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

        if( ALLOW_PLAYER_NOTIFY == true ) {
            // Tell players who are TKd for the first time that they can notify staff
            if( tked.remove( killed.getPlayerName() ) == null )
                m_botAction.sendPrivateMessage( event.getKilleeID(), "You were TK'd by " + killer.getPlayerName() + ".  Type ::report to notify staff of any non-accidental TKs." );

            tked.put( killed.getPlayerName(), killer.getPlayerName() );
        }
    }


    /**
     * Remove players from the TK list if they leave the arena, and add them to
     * the temporary old TKer storage.
     * @param event The event object.
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
     * Check if player has stored info on them.  If so, add them back into
     * the mix.  Prevents abuse by leaving/reentering arena.
     * @param event The event object.
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
     * Unimplemented.
     */
    public void cancel() {
    }


    /**
     * Used to store info on TKers.  Operates on a point system.
     *
     * Defaults:
     *
     * Shark TKs ..........................  +7 points
     * Jav/Levi TKs ....................... +12 points
     * TKing same person twice in a row ... +20 points
     * Shipset ............................ Each TK's points doubled
     * Staff notified ..................... (records point total @ notified)
     *
     * SPECIALS (REVISE)
     * Every 10 seconds .................... -1 point
     * 4 TKs and no warnings .............. +20 points (auto warning)
     * 8 TKs and no setship ............... +40 points (auto setship)
     * 4 warns and no setship ............. +40 points (auto setship)
     *
     * @author qan
     */
    private class TKInfo {
        private String m_playerName;     // name of TKer
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
         * Create a new TK object whenever a TKer is identified.
         */
        public TKInfo( String name ) {
            m_playerName = name.toLowerCase();
            m_firstTKTime = new Date().getTime();
            m_lastTKTime = new Date().getTime();
        }


        /**
         * Store info on a TK based on shipnumber used to TK.
         * - At 4 TKs and 0 warns, a warning is given.
         * - At 8 TKs and no setship, the player is set to a different ship.
         * - At 10 TKs, if staff has not yet been notified, they are.
         * @param shipnum Ship number of the TKer.
         * @param playerTKd The name of the player who was TKd.
         */
        public void addTK( int shipnum, String playerTKd ) {
            calculatePointLoss();
            m_lastTKTime = new Date().getTime();

            m_TKs++;

            if( shipnum == 8 ) {
                m_TKpoints += TK_POINTS_SHARK;
                if( m_setShipped == true )
                	m_TKpoints += TK_POINTS_SHARK;	// counts for double if you've been setshipped
		    } else if( shipnum == 4 ) {
                m_TKpoints += TK_POINTS_LEVI;
                if( m_setShipped == true )
                	m_TKpoints += TK_POINTS_LEVI;	// counts for double if you've been setshipped
            } else {
                m_TKpoints += TK_POINTS_NORM;
                if( m_setShipped == true )
                	m_TKpoints += TK_POINTS_NORM;	// counts for double if you've been setshipped
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

            m_lastTKd = playerTKd;
            m_playerHasNotified = false;

			/*
	         * TODO: Frequency tracking.  Code in progress.
            long first = new Date().getTime() - getFirstTKTime();
            long frequency = (((new Date().getTime() - getFirstTKTime()) / 1000) / getNumTKs());
            if( first > (10 * 60) && frequency <= 60 )
			*/


            // "Neutered" version of the bot for info gathering only
            if( HARDASS == false )
                return;

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

            } else if( IGNORE_FAILSAFES == true ) {
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


        /**
         * Calculates the number of points a player has lost over time since the last TK.
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
         * Sets ship to a non-TKable ship.  This almost always happens before staff is notified.
         */
        public void setTKerShip() {
            addWarn();
        	m_botAction.setShip( m_playerName, 1 );
        	m_botAction.sendPrivateMessage( m_playerName, "NOTICE: Your ship has been automatically changed due to excessive killing of teammates.", 1 );
        	m_setShipped = true;
        }


        /**
         * Add to player warning count (not with *warn), and send a warning
         * of appropriate severity.  If warned in the past 30 seconds, the
         * warning will be ignored.
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
         * Sends a warning message to player depending on number of past warns.
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
         * Send a notification of repeated TKs to staff.
         */
        public void notifyStaff() {
            if( m_staffNotified == true ) {
                if( m_TKs % 25 != 0 )
                    return;
            }

            m_staffNotified = true;
            m_ptsAtNotify = m_TKpoints;

            // Do not add on additional warns for this particular warn (so as not to throw
            // off numbers for staff reviewing it).
            sendWarn();

            String msg = "?cheater " + m_playerName + " - TKs: " + m_TKs + ", BotWarns: " + m_warns;
            if( m_repeatKiller )
                msg = msg + " (player '" + m_lastRepeatTK + "' TK'd " + m_repeat + " times)";

            m_botAction.sendUnfilteredPublicMessage( msg );
        }


        // Getter methods
        public String getName() {
        	return m_playerName;
        }

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
