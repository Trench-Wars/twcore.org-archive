package twcore.bots.pubbot;

import java.util.*;
import twcore.core.*;
import twcore.misc.pubcommon.*;

/**
 * Tracks TKs, warns players who TK excessively, and notifies staff as necessary.
 * Operates on a point system.
 */
public class pubbottk extends PubBotModule {

    private final int normTKpts = 12;        // Penalty for TKing (any ship but shark)
    private final int sharkTKpts = 8;        // Penalty for TKing as a shark
    private final int continuedTKpts = 24;   // Penalty for Tking same person twice in a row
    private final int warnAt = 20;           // Points at which player receives a warning
    private final int notifyAt = 40;         // Points at which staff is notified
    private final int cooldownSecs = 10;     // Time, in secs, it takes to remove 1 TK point
    private final int forgetTime = 15;      // Time, in secs, between when the
                                             //    slate is wiped clean for TKers who
                                             //    have left the arena. (def: 15 min)
    private TimerTask forgetOldTKers;
    private String botName;
    private String currentArena;
    private boolean checkTKs;                // true if TK checking enabled
    private HashMap tkers;                   // storage of all TKInfo objs
    private HashMap oldtkers;                // temp. stores TKers who leave arena
                                             // (low-cost abuse prevention)

    /**
     * Called when the module is loaded for each individual pubbot.
     */
    public void initializeModule() {
        checkTKs = false;
        currentArena = m_botAction.getArenaName();
        tkers = new HashMap();
        oldtkers = new HashMap();

        botName = m_botAction.getBotName();

        // Must be enabled or the bot won't register kills properly
        m_botAction.sendUnfilteredPublicMessage( "*relkills 1" );

        // I've left the arena.  Forget me not?  No, forget me every 15 minutes.
        forgetOldTKers = new TimerTask() {
            public void run() {
                oldtkers = new HashMap();
            }
        };
        m_botAction.scheduleTaskAtFixedRate( forgetOldTKers, forgetTime * 1000, forgetTime * 1000 );
    }


    /**
     * Requests all events needed.
     *
     * @param eventRequester Standard.
     */
    public void requestEvents( EventRequester eventRequester ) {
        eventRequester.request(EventRequester.ARENA_LIST);
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
        } else {
            if( checkTKs ) {
                m_botAction.sendChatMessage( "TK checking disabled in " + currentArena + ".");
                checkTKs = false;
            } else {
                m_botAction.sendChatMessage( "TK checking enabled in " + currentArena + ".");
                checkTKs = true;
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
        IPCMessage ipcMessage = (IPCMessage) event.getObject();
        String message = ipcMessage.getMessage();
        String recipient = ipcMessage.getRecipient();
        String sender = ipcMessage.getSender();
        String botSender = event.getSenderName();

        try {
            if(recipient == null || recipient.equals(botName)) {
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

        if( killed.getFrequency() != killer.getFrequency() )
            return;

        if( tkers != null )
            tk = (TKInfo)tkers.get( killer.getPlayerName() );

        if( tk != null ) {
            tk.addTK( killer.getShipType(), killed.getPlayerName() );
        } else {
            TKInfo newtk = new TKInfo( killer.getPlayerName() );
            newtk.addTK( killer.getShipType(), killed.getPlayerName() );
            m_botAction.scheduleTaskAtFixedRate( newtk, cooldownSecs * 1000, cooldownSecs * 1000 );
            tkers.put( killer.getPlayerName(), newtk );
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

        String pn = m_botAction.getPlayerName( event.getPlayerID() );
        TKInfo oldtker = (TKInfo)tkers.remove( pn );

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

        String pn = m_botAction.getPlayerName( event.getPlayerID() );
        TKInfo tker = (TKInfo)oldtkers.remove( pn );

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
     * Shark TKs ..........................  +8 points
     * Jav/Levi TKs ....................... +12 points
     * TKing same person twice in a row ... +24 points
     * Warning issued ...................... +6 points
     * Staff notified ...................... (removes point countdown functionality)
     *
     * SPECIALS
     * Every 10 seconds .................... -1 point
     * 4 TKs and no warnings .............. +20 points (warn)
     * 7 TKs and no notify ................ +40 points (notify)
     * 3 warns and no notify .............. +40 points (notify)
     *
     * @author qan
     */
    private class TKInfo extends TimerTask {
        private String playerName;
        private int m_TKpoints = 0;      // current amount of TK "points"
        private int m_TKs = 0;           // total # of TKs made
        private int m_warns = 0;         // total # warns given
        private String m_lastTKd = "";   // last person TKd by this person
        private boolean m_staffNotified = false;    // true if staff have been notified
        private boolean m_repeatKiller = false;     // true if killed same person twice
                                                    // in a row

        /**
         * Create a new TK object whenever a TKer is identified.
         */
        public TKInfo( String name ) {
            playerName = name;
        }


        /**
         * Store info on a TK based on shipnumber used to TK.
         * - At 3 TKs and 0 warns, a warning is given.
         * - At 7 TKs, if staff has not yet been notified, they are.
         * @param shipnum Ship number of the TKer.
         * @param playerTKd The name of the player who was TKd.
         */
        public void addTK( int shipnum, String playerTKd ) {
            m_TKs++;

            if( shipnum == 8 )
                m_TKpoints += sharkTKpts;
            else
                m_TKpoints += normTKpts;

            if( playerTKd.equals( m_lastTKd ) ) {
                m_TKpoints += continuedTKpts;
                m_repeatKiller = true;
            }

            if( m_TKpoints >= notifyAt ) {
                notifyStaff();
            } else if( m_TKpoints >= warnAt ) {
                addWarn();
            } else if( m_TKs >= 7 && m_staffNotified == false ) {
                m_TKpoints += notifyAt;
                notifyStaff();
            } else if( m_TKs >= 3 && m_warns == 0 ) {
                m_TKpoints += warnAt;
                addWarn();
            }

            m_lastTKd = playerTKd;
        }


        /**
         * Warn the player (not with *warn).  If a player is warned 3 times
         * (trying to abuse the system), staff is notified.
         */
        public void addWarn() {
            m_warns++;
            m_TKpoints += ( normTKpts / 2 );

            if( m_warns == 1 )
                m_botAction.sendPrivateMessage( playerName, "NOTICE: Excessive and intentional teamkilling (TKing) are both prohibited in Trench Wars." );
            else if( m_warns == 2 )
                m_botAction.sendPrivateMessage( playerName, "NOTICE: If you continue to kill players on your own frequency, you risk being banned from Trench Wars." );
            else if( m_warns > 2 ) {
                m_TKpoints += warnAt;
                notifyStaff();
            }
        }


        /**
         * Send a notification of repeated TKs to staff.
         */
        public void notifyStaff() {
            if( m_staffNotified == true ) {
                if( m_TKs % 10 != 0 )
                    return;
            }

            m_staffNotified = true;

            if( m_warns == 0 ) {
                m_warns++;
                m_botAction.sendPrivateMessage( playerName, "NOTICE: Excessive and intentional teamkilling (TKing) are prohibited in Trench Wars." );
            } else {
                m_warns++;
                m_botAction.sendPrivateMessage( playerName, "NOTICE: If you continue to kill players on your own frequency, you risk being banned from Trench Wars." );
            }

            String msg = "?cheater TKer: " + playerName + " - " + m_TKs + " TKs, warned " + m_warns + " time(s)";
            if( m_repeatKiller )
                msg = msg + " (also killed specific player 2+ times/row)";
            m_botAction.sendUnfilteredPublicMessage( msg );
        }


        /**
         * Removes a TK point (TKs fade over time, though are always recorded as
         * a total), if staff have not already been notified.
         *
         * TK points reduced by 1 at each tick (generally 10 seconds).
         */
        public void run() {
            if( m_staffNotified == false )
                if( m_TKpoints > 0 )
                    m_TKpoints--;
        }
    }
}
