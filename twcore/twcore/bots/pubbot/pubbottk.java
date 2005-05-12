package twcore.bots.pubbot;

import java.util.*;
//import java.util.Date;
import twcore.core.*;
import twcore.misc.pubcommon.*;

/**
 * Tracks TKs, warns players who TK excessively, and notifies staff as necessary.
 * Operates on a point system.
 * 
 * @author qan
 */
public class pubbottk extends PubBotModule {

    private final int normTKpts = 12;        // Penalty for TKing (any ship but shark)
    private final int sharkTKpts = 6;        // Penalty for TKing as a shark
    private final int continuedTKpts = 20;   // Penalty for Tking same person twice in a row
    private final int warnAt = 20;           // Points at which player receives a warning
    private final int notifyAt = 40;         // Points at which staff is notified
    private final int cooldownSecs = 10;     // Time, in secs, it takes to remove 1 TK point
    private final int forgetTime = 15;       // Time, in secs, between when the
                                             //    slate is wiped clean for TKers who
                                             //    have left the arena. (def: 15 min)
    private OperatorList m_opList;
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
        m_opList = m_botAction.getOperatorList();

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
     * Handles command PMs to the bot.
     * 
     * @param event The Message event containing all necessary information derived
     * from the incoming packet.
     */
    public void handleEvent( Message event ){

        String message = event.getMessage();
        if( event.getMessageType() == Message.PRIVATE_MESSAGE ){
            String name = m_botAction.getPlayerName( event.getPlayerID() );
            if( m_opList.isZH( name ) ) {
           	
                if( message.equals( "!help" )){
                    m_botAction.sendPrivateMessage( name, "Pubbot TK Module (qan@twdev.org)" );
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
        }
    }
    
    
    /**
     * Messages a staff member the information on a TKer.
     * FIXME: After running long periods of time, all TK info is sometimes destroyed, and no new
     *        TKs can be recorded. (returns 'Teamkill record not found' msg)
     * @param staffname Staff member to msg
     * @param tkname Name of TKer
     */
    public void msgTKInfo( String staffname, String tkname ) {  	
    	TKInfo tker = (TKInfo)tkers.get( tkname );
    	
        if( tker == null ){
            Iterator i = m_botAction.getPlayerIterator();
            Player searchPlayer = null;            
            while( i.hasNext() && searchPlayer == null ){
                searchPlayer = (Player)i.next();
                // For inexact names
                if(! searchPlayer.getPlayerName().toLowerCase().startsWith( tkname ) ) {
                    // Long name hack
                    if(! tkname.startsWith( searchPlayer.getPlayerName().toLowerCase() ) ) {
                        searchPlayer = null;
                    }
                }
            }
            if( searchPlayer == null ) {
    			m_botAction.sendPrivateMessage( staffname, "Player not found.  Please verify the person is in the arena." );                		
                return;
            } else {
                tker = (TKInfo)tkers.get( searchPlayer.getPlayerName().toLowerCase() );
                if( tker == null ) {
        			m_botAction.sendPrivateMessage( staffname, "Teamkill record not found.  Please check the name and verify they have teamkilled." );                		
                    return;
                }
            }
        }
    	
    	m_botAction.sendPrivateMessage( staffname, "'" + tker.getName() + "' TK Record" );
		m_botAction.sendPrivateMessage( staffname, "TKs:  " + tker.getNumTKs() + "     Warns:  " + tker.getNumWarns() );
    	m_botAction.sendPrivateMessage( staffname, "Last player TKd:  " + tker.getLastTKd() );
		
    	String pointsmsg = "";
		
		if( tker.wasStaffNotified() ) {
		    pointsmsg = "TK Points at current / at notify:  " + tker.getTKpoints() + " / " + tker.getTKpointsAtNotify();			
		    m_botAction.sendPrivateMessage( staffname, "  - Staff has been notified.");
		    if( tker.wasSetShipped() )
		        m_botAction.sendPrivateMessage( staffname, "  - Player has been setshipped.");
		} else {
		    pointsmsg = "TK Points at current:  " + tker.getTKpoints();			
		    if( tker.wasSetShipped() )
		        m_botAction.sendPrivateMessage( staffname, "  - Player has been setshipped.");
		}
	
		m_botAction.sendPrivateMessage( staffname, pointsmsg );
		if( tker.wasRepeatKiller() ) {
			m_botAction.sendPrivateMessage( staffname, "Potential 'target' player:  " + tker.getRepeatTKd() );
			if( tker.getNumRepeats() > 2 )
				m_botAction.sendPrivateMessage( staffname, "  - TKd this player twice in a row, then TKd them " + (tker.getNumRepeats() - 2) + " more time(s)." );
			else
			    m_botAction.sendPrivateMessage( staffname, "  - TKd this player twice in a row." );
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
            tk = (TKInfo)tkers.get( killer.getPlayerName().toLowerCase() );

        if( tk != null ) {
            tk.addTK( killer.getShipType(), killed.getPlayerName() );
        } else {
            TKInfo newtk = new TKInfo( killer.getPlayerName() );
            newtk.addTK( killer.getShipType(), killed.getPlayerName() );
            m_botAction.scheduleTaskAtFixedRate( newtk, cooldownSecs * 1000, cooldownSecs * 1000 );
            tkers.put( newtk.getName(), newtk );
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

        String pn = m_botAction.getPlayerName( event.getPlayerID() ).toLowerCase();
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
    private class TKInfo extends TimerTask {
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


        /**
         * Create a new TK object whenever a TKer is identified.
         */
        public TKInfo( String name ) {
            m_playerName = name.toLowerCase();
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
            m_TKs++;

            if( shipnum == 8 ) {
                m_TKpoints += sharkTKpts;
                if( m_setShipped == true )
                	m_TKpoints += sharkTKpts;	// counts for double if you've been setshipped
            } else {
                m_TKpoints += normTKpts;
                if( m_setShipped == true )
                	m_TKpoints += normTKpts;	// counts for double if you've been setshipped
            }

            // System to find out of one person is being targetted.  Activated
            // by one person being TKd twice in a row.  After that records every
            // additional TK on that person, regardless of whether it's consecutive.
            if( playerTKd.equals( m_lastRepeatTK ) ) {
                m_TKpoints += continuedTKpts;
            	m_repeat += 1;
            } else if( playerTKd.equals( m_lastTKd ) ) {
                m_TKpoints += continuedTKpts;
                m_repeatKiller = true;
                m_lastRepeatTK = m_lastTKd;
                m_repeat = 2;
            }

            if( m_setShipped && m_TKpoints >= notifyAt ) {
                notifyStaff();
            } else if( m_TKpoints >= notifyAt) {
                setTKerShip();
            } else if( m_TKpoints >= warnAt ) {
                if( m_warns >= 4 && m_setShipped )
                    notifyStaff();
                else if( m_warns >= 4 )
                    setTKerShip();
                else
                    addWarn();
            } else if( m_TKs >= 10 && m_staffNotified == false ) {
                m_TKpoints += notifyAt;
                notifyStaff();
            } else if( m_TKs >= 8 && m_setShipped == false ) {
                m_TKpoints += notifyAt;
                setTKerShip();
            } else if( m_TKs >= 4 && m_warns == 0 ) {
                m_TKpoints += warnAt;
                addWarn();
            }

            m_lastTKd = playerTKd;
        }


        /**
         * Add to player warning count (not with *warn), and send a warning
         * of appropriate severity.
         */
        public void addWarn() {
            m_warns++;
            sendWarn();
        }
        
        
        /**
         * Sets ship to a non-TKable ship.  This almost always happens before staff is notified.
         */
        public void setTKerShip() {
        	m_botAction.setShip( m_playerName, 1 );
        	m_botAction.sendPrivateMessage( m_playerName, "NOTICE: Your ship has been automatically changed due to excessive killing of teammates.", 1 );
        	m_setShipped = true;
            addWarn();
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
            	m_botAction.sendPrivateMessage( m_playerName, ">>> WARNING! <<<  You will be banned if you continue to TK!!  >>> WARNING! <<<", 2 );
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
            m_ptsAtNotify = m_TKpoints;
            
            // Do not add on additional warns for this particular warn (so as not to throw
            // off numbers for staff reviewing it).
            sendWarn();

            String msg = "?cheater " + m_playerName + " - TKs: " + m_TKs + ", BotWarns: " + m_warns;
            if( m_repeatKiller )
                msg = msg + " (player '" + m_lastRepeatTK + "' TK'd " + m_repeat + " times)";
            
            m_botAction.sendUnfilteredPublicMessage( msg );
        }


        /**
         * Removes a TK point (TKs fade over time, though are always recorded as
         * a total), if staff have not already been notified.
         *
         * TK points reduced by 1 at each tick (generally 10 seconds).
         */
        public void run() {
            if( m_TKpoints > 0 )
                m_TKpoints--;
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
    }
}
