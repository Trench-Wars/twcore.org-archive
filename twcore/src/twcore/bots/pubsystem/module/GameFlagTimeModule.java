package twcore.bots.pubsystem.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;
import twcore.core.lvz.Objset;
import twcore.core.util.Tools;

public class GameFlagTimeModule extends AbstractModule {

    private static final int FLAG_CLAIM_SECS = 3;		// Seconds it takes to fully claim a flag
	private static final int INTERMISSION_SECS = 90;	// Seconds between end of round and start of next
	private static final int MAX_FLAGTIME_ROUNDS = 5;   // Max # rounds (odd numbers only)
	
    private HashMap<String,Integer> playerTimes;        // Roundtime of player on freq
    private HashMap<String,Integer> flagClaims;
    private HashMap<String,Integer> kills;
    private HashMap<String,Integer> deaths;
    
    private FlagCountTask flagTimer;                    // Flag time main class
    private StartRoundTask startTimer;                  // TimerTask to start round
    private IntermissionTask intermissionTimer;         // TimerTask for round intermission
    private AuxLvzTask scoreDisplay;					// Displays score lvz
    private AuxLvzTask scoreRemove;						// Removes score lvz

    private int flagMinutesRequired;                    // Flag minutes required to win
    private int freq0Score, freq1Score;                 // # rounds won

    private List <String> mineClearedPlayers;           // Players who have already cleared mines this round
    
    private Objset objs;                                // For keeping track of counter
	
	private HashMap<String,PubPlayer> warpPlayers;
	
    // X and Y coords for warp points.  Note that the first X and Y should be
    // the "standard" warp; in TW this is the earwarp.  These coords are used in
    // strict flag time mode.
    private int warpPtsLeftX[];
    private int warpPtsLeftY[];
    private int warpPtsRightX[];
    private int warpPtsRightY[];
    
    // Warp coords for safes (for use in strict flag time mode)
    private int warpSafeLeftX;
    private int warpSafeLeftY;
    private int warpSafeRightX;
    private int warpSafeRightY;

    private boolean warpEnabled = false;
    private boolean autoWarp = false;
    
    private boolean flagTimeStarted = false;
    private boolean strictFlagTimeMode = false;
    
	public GameFlagTimeModule(BotAction botAction, PubContext context) {
		super(botAction, context, "Game FlagTime");
		
		this.context = context;
		
		mineClearedPlayers = Collections.synchronizedList(new LinkedList<String>());
		objs = m_botAction.getObjectSet();
		
		this.warpPlayers = new HashMap<String,PubPlayer>();
		
        warpPtsLeftX = m_botAction.getBotSettings().getIntArray("warp_leftX", ",");
        warpPtsLeftY = m_botAction.getBotSettings().getIntArray("warp_leftY", ",");
        warpPtsRightX = m_botAction.getBotSettings().getIntArray("warp_rightX", ",");
        warpPtsRightY = m_botAction.getBotSettings().getIntArray("warp_rightY", ",");
        
        warpSafeLeftX = m_botAction.getBotSettings().getInt("warp_safe_leftX");
        warpSafeLeftY = m_botAction.getBotSettings().getInt("warp_safe_leftY");
        warpSafeRightX = m_botAction.getBotSettings().getInt("warp_safe_rightX");
        warpSafeRightY = m_botAction.getBotSettings().getInt("warp_safe_rightY");
        
		if (m_botAction.getBotSettings().getInt("auto_warp")==1) {
			autoWarp = true;
		}
	}
	
	public void startFlagTimeStarted() {
		this.flagTimeStarted = true;
	}
	
	public void stopFlagTimeStarted() {
		this.flagTimeStarted = false;
	}
	
	public boolean isFlagTimeStarted() {
		return flagTimeStarted;
	}

	
    /**
     * Formats an integer time as a String.
     * @param time Time in seconds.
     * @return Formatted string in 0:00 format.
     */
    public String getTimeString( int time ) {
        if( time <= 0 ) {
            return "0:00";
        } else {
            int minutes = time / 60;
            int seconds = time % 60;
            return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
        }
    }
    
    /**
     * Adds all players to the hashmap which stores the time, in flagTimer time,
     * when they joined their freq.
     */
    public void setupPlayerTimes() {
    	
        playerTimes = new HashMap<String,Integer>();

        Iterator<Player> i = m_botAction.getPlayingPlayerIterator();
        Player player;

        try {
            while( i.hasNext() ) {
                player = (Player)i.next();
                playerTimes.put( player.getPlayerName(), new Integer(0) );
            }
        } catch (Exception e) {
        }
    }
    
	public void handleEvent(PlayerLeft event) {
		String playerName = m_botAction.getPlayerName(event.getPlayerID());
		warpPlayers.remove(playerName);
	}
    
	public void handleEvent(FrequencyShipChange event) 
	{
        int playerID = event.getPlayerID();
        int freq = event.getFrequency();
		int ship = event.getShipType();

        Player p = m_botAction.getPlayer( playerID );
        String playerName = p.getPlayerName();
        
        if( p == null )
            return;
        
        // Do nothing if the player is dueling
        if( context.getPubChallenge().isEnabled() ) {
        	if (context.getPubChallenge().isDueling(playerName))
        		return;
        }
        
        if(ship == 5)
            m_botAction.sendOpposingTeamMessageByFrequency(freq, "Player "+p.getPlayerName()+" is now a terr; you may attach");
        
        try {
            if( isFlagTimeStarted() && flagTimer != null && flagTimer.isRunning() ) {
                // Remove player if spec'ing
                if( ship == Tools.Ship.SPECTATOR ) {
                    String pname = p.getPlayerName();
                	playerTimes.remove( pname );
                } 
            }
            
            if (autoWarp && !warpPlayers.containsKey(playerName)) {
    	        if(ship != Tools.Ship.SPECTATOR)
    	        	doWarpCmd(playerName); 
            }

            // Terrs and Levis can't warp into base if Levis are enabled
            if (!context.getPlayerManager().isShipRestricted(Tools.Ship.LEVIATHAN)) {
    	        if (ship == Tools.Ship.LEVIATHAN || ship == Tools.Ship.TERRIER) {         
    	        	warpPlayers.remove(playerName);
    	        }   
            }     
            
        } catch (Exception e) {
        }
	}
	
	public void handleEvent(FrequencyChange event) 
	{
		// Reset the time of a player for MVP purpose
		if (flagTimer.isRunning()) {
			Player player = m_botAction.getPlayer(event.getPlayerID());
			playerTimes.put(player.getPlayerName(), new Integer(0));
		}
	}
	
	public void handleEvent(PlayerDeath event) {
		
		int killerID = event.getKillerID();
		int killedID = event.getKilleeID();
		Player killer = m_botAction.getPlayer(killerID);
		Player killed = m_botAction.getPlayer(killedID);
		
		flagTimer.addPlayerKill(killer.getPlayerName());
		flagTimer.addPlayerDeath(killed.getPlayerName());
	}
	
    public void handleEvent(PlayerEntered event) {
    	
        int playerID = event.getPlayerID();
        Player player = m_botAction.getPlayer(playerID);
        String playerName = m_botAction.getPlayerName(playerID);
    	
        if(isFlagTimeStarted() && isAutoWarpEnabled()) {
        	if( player.getShipType() != Tools.Ship.SPECTATOR )
        		doWarpCmd(playerName);
        }
        
        statusMessage(playerName);
    }
    
    public void handleEvent(FlagClaimed event) 
    {
    	if(!isFlagTimeStarted())
            return;

        int playerID = event.getPlayerID();
        Player p = m_botAction.getPlayer(playerID);

        try {
            if( p != null && flagTimer != null ) {
                flagTimer.flagClaimed( p.getFrequency(), playerID );
            }
        } catch (Exception e) {
        }
    }

    /**
     * Collects names of players on a freq into a Vector ArrayList by ship.
     * @param freq Frequency to collect info on
     * @return Vector array containing player names on given freq
     */
    public ArrayList<Vector<String>> getTeamData( int freq ) {
        ArrayList<Vector<String>> team = new ArrayList<Vector<String>>();
        // 8 ships plus potential spectators
        for( int i = 0; i < 9; i++ ) {
            team.add( new Vector<String>() );
        }
        Iterator<Player> i = m_botAction.getFreqPlayerIterator(freq);
        while( i.hasNext() ) {
            Player p = (Player)i.next();
            team.get(p.getShipType()).add(p.getPlayerName());
        }
        return team;
    }
    
    /**
     * Displays rules and pauses for intermission.
     */
    private void doIntermission() {
        if(!isFlagTimeStarted())
            return;

        int roundNum = freq0Score + freq1Score + 1;

        String roundTitle = "";
        switch( roundNum ) {
        case 1:
            m_botAction.sendArenaMessage( "Object: Hold flag for " + flagMinutesRequired + " consecutive minute" + (flagMinutesRequired == 1 ? "" : "s") + " to win a round.  Best " + ( MAX_FLAGTIME_ROUNDS + 1) / 2 + " of "+ MAX_FLAGTIME_ROUNDS + " wins the game." );
            roundTitle = "The next game";
            break;
        case MAX_FLAGTIME_ROUNDS:
            roundTitle = "Final Round";
            break;
        default:
            roundTitle = "Round " + roundNum;
        }

        m_botAction.sendArenaMessage( roundTitle + " begins in " + getTimeString( INTERMISSION_SECS ) + ".  (Score: " + freq0Score + " - " + freq1Score + ")" + (strictFlagTimeMode?"":("  Type !warp to set warp status, or send !help")) );

        m_botAction.cancelTask(startTimer);

        startTimer = new StartRoundTask();
        m_botAction.scheduleTask( startTimer, INTERMISSION_SECS * 1000 );
    }
    
    /**
     * Shows who on the team is in which ship.
     *
     * @param sender is the person issuing the command.
     */
    public void doShowTeamCmd(String sender) {
        Player p = m_botAction.getPlayer(sender);
        if( p == null )
            throw new RuntimeException("Can't find you.  Please report this to staff.");
        if( p.getShipType() == 0 )
            throw new RuntimeException("You must be in a ship for this command to work.");
        ArrayList<Vector<String>>  team = getTeamData( p.getFrequency() );
        int players = 0;
        for(int i = 1; i < 9; i++ ) {
            int num = team.get(i).size();
            String text = num + Tools.formatString( (" " + Tools.shipNameSlang(i) + (num==1 ? "":"s")), 8 );
            text += "   ";
            for( int j = 0; j < team.get(i).size(); j++) {
               text += (j+1) + ") " + team.get(i).get(j) + "  ";
               players++;
            }
            m_botAction.sendPrivateMessage(sender, text);
        }

        m_botAction.sendPrivateMessage(sender, "->  Use !ship <ship#> to change ships & keep MVP.  <-");
    }
    
    /**
     * Shows terriers on the team and their last observed locations.
     */
    public void doTerrCmd( String sender ) {
        Player p = m_botAction.getPlayer(sender);
        if( p == null )
            throw new RuntimeException("Can't find you.  Please report this to staff.");
        if( p.getShipType() == 0 )
            throw new RuntimeException("You must be in a ship for this command to work.");
        Iterator<Player> i = m_botAction.getFreqPlayerIterator(p.getFrequency());
        if( !i.hasNext() )
            throw new RuntimeException("ERROR: No players detected on your frequency!");
        m_botAction.sendPrivateMessage(sender, "Name of Terrier          Last seen");
        while( i.hasNext() ) {
            Player terr = (Player)i.next();
            if( terr.getShipType() == Tools.Ship.TERRIER )
                m_botAction.sendPrivateMessage( sender, Tools.formatString(terr.getPlayerName(), 25) + context.getPubUtil().getPlayerLocation(terr.getXTileLocation(), terr.getYTileLocation()) );
        }
    }

    /**
     * Displays info about time remaining in flag time round, if applicable.
     */
    public void doTimeCmd( String sender )
    {
        if( isFlagTimeStarted() )
            if( flagTimer != null )
                flagTimer.sendTimeRemaining( sender );
            else
                throw new RuntimeException( "Flag Time mode is just about to start." );
        else
            throw new RuntimeException( "Flag Time mode is not currently running." );
    }
    
    /**
     * Shows and hides scores (used at intermission only).
     * @param time Time after which the score should be removed
     */
    private void doScores(int time) {
        int[] objs1 = {2000,(freq0Score<10 ? 60 + freq0Score : 50 + freq0Score), (freq0Score<10 ? 80 + freq1Score : 70 + freq1Score)};
        boolean[] objs1Display = {true,true,true};
    	scoreDisplay = new AuxLvzTask(objs1, objs1Display);
        int[] objs2 = {2200,2000,(freq0Score<10 ? 60 + freq0Score : 50 + freq0Score), (freq0Score<10 ? 80 + freq1Score : 70 + freq1Score)};
        boolean[] objs2Display = {true,false,false,false};
    	scoreRemove = new AuxLvzTask(objs2, objs2Display);
    	m_botAction.scheduleTask(scoreDisplay, 1000);		// Do score display
    	m_botAction.scheduleTask(scoreRemove, time-1000);	// do score removal
    	m_botAction.showObject(2100);

    }
    
    /**
     * Starts a game of flag time mode.
     */
    private void doStartRound() {
        if(!isFlagTimeStarted())
            return;

        try {
            flagTimer.endGame();
            m_botAction.cancelTask(flagTimer);
        } catch (Exception e ) {
        }

        mineClearedPlayers.clear();
        flagTimer = new FlagCountTask();
        m_botAction.showObject(2300); //Turns on coutdown lvz
        m_botAction.hideObject(1000); //Turns off intermission lvz
        m_botAction.scheduleTaskAtFixedRate( flagTimer, 100, 1000);

    }

    /**
     * Ends a round of Flag Time mode & awards prizes.
     * After, sets up an intermission, followed by a new round.
     */
    private void doEndRound( ) {
        if( !isFlagTimeStarted() || flagTimer == null )
            return;

        HashSet <String>MVPs = new HashSet<String>();
        boolean gameOver     = false;       // Game over, man.. game over!
        int flagholdingFreq  = flagTimer.getHoldingFreq();
        int maxScore         = (MAX_FLAGTIME_ROUNDS + 1) / 2;  // Score needed to win
        int secs = flagTimer.getTotalSecs();
        int mins = secs / 60;
        int weight = (secs * 3 ) / 60;

        try {

            // Incremental bounty bonuses
            if( mins >= 90 )
                weight += 150;
            else if( mins >= 60 )
                weight += 100;
            else if( mins >= 30 )
                weight += 45;
            else if( mins >= 15 )
                weight += 20;


            if( flagholdingFreq == 0 || flagholdingFreq == 1 ) {
                if( flagholdingFreq == 0 )
                    freq0Score++;
                else
                    freq1Score++;

                if( freq0Score >= maxScore || freq1Score >= maxScore ) {
                    gameOver = true;
                } else {
                    int roundNum = freq0Score + freq1Score;
                    m_botAction.sendArenaMessage( "END OF ROUND " + roundNum + ": Freq " + flagholdingFreq + " wins after " + getTimeString( flagTimer.getTotalSecs() ) +
                            " (" + weight + " bounty bonus)  Score: " + freq0Score + " - " + freq1Score, 1 );
                }

            } else {
                if( flagholdingFreq < 100 )
                    m_botAction.sendArenaMessage( "END ROUND: Freq " + flagholdingFreq + " wins the round after " + getTimeString( flagTimer.getTotalSecs() ) + " (" + weight + " bounty bonus)", 1 );
                else
                    m_botAction.sendArenaMessage( "END ROUND: A private freq wins the round after " + getTimeString( flagTimer.getTotalSecs() ) + " (" + weight + " bounty bonus)", 1 );
            }

            int special = 0;
            // Special prizes for longer battles (add more if you think of any!)
            if( mins > 15 ) {
                Random r = new Random();
                int chance = r.nextInt(100);

                if( chance == 99 ) {
                    special = 8;
                } else if( chance == 98 ) {
                    special = 7;
                } else if( chance >= 94 ) {
                    special = 6;
                } else if( chance >= 90 ) {
                    special = 5;
                } else if( chance >= 75 ) {
                    special = 4;
                } else if( chance >= 60 ) {
                    special = 3;
                } else if( chance >= 35 ) {
                    special = 2;
                } else {
                    special = 1;
                }
            }

            Iterator<Player> iterator = m_botAction.getPlayingPlayerIterator();
            Player player;
            while(iterator.hasNext()) {
                player = (Player) iterator.next();
                if( player != null ) {
                    if(player.getFrequency() == flagholdingFreq ) {
                        String playerName = player.getPlayerName();

                        Integer i = playerTimes.get( playerName );

                        if( i != null ) {
                            // Calculate amount of time actually spent on freq

                            int timeOnFreq = secs - i.intValue();
                            int percentOnFreq = (int)( ( (float)timeOnFreq / (float)secs ) * 100 );
                            int modbounty = (int)(weight * ((float)percentOnFreq / 100));

                            if( percentOnFreq == 100 ) {
                                MVPs.add( playerName );
                                m_botAction.sendPrivateMessage( playerName, "For staying with the same freq the entire match, you are an MVP and receive the full bonus: " + modbounty );
                                int grabs = flagTimer.getFlagGrabs( playerName );
                                if( special == 4 ) {
                                    m_botAction.sendPrivateMessage( playerName, "You also receive an additional " + weight + " bounty as a special prize!" );
                                    modbounty *= 2;
                                }
                                if( grabs != 0 ) {
                                    modbounty += (modbounty * ((float)grabs / 10.0));
                                    m_botAction.sendPrivateMessage( playerName, "For your " + grabs + " flag grabs, you also receive an additional " + grabs + "0% bounty, for a total of " + modbounty );
                                }

                            } else {
                                m_botAction.sendPrivateMessage( playerName, "You were with the same freq and ship for the last " + getTimeString(timeOnFreq) + ", and receive " + percentOnFreq  + "% of the bounty reward: " + modbounty );
                            }

                            m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize " + modbounty);
                        }

                        if( MVPs.contains( playerName ) ) {
                            switch( special ) {
                            case 1:  // "Refreshments" -- replenishes all essentials + gives anti
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #6");
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #15");
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #20");
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21");
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21");
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #21");
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #22");
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #27");
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #28");
                                break;
                            case 2:  // "Full shrap"
                                for(int j = 0; j < 5; j++ )
                                    m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #19");
                                break;
                            case 3:  // "Trophy" -- decoy given
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #23");
                                break;
                            case 4:  // "Double bounty reward"
                                break;
                            case 5:  // "Triple trophy" -- 3 decoys
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #23");
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #23");
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #23");
                                break;
                            case 6:  // "Techno Dance Party" -- plays victory music :P
                                break;
                            case 7:  // "Sore Loser's Revenge" -- engine shutdown!
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #14");
                                break;
                            case 8:  // "Bodyguard" -- shields
                                m_botAction.sendUnfilteredPrivateMessage(player.getPlayerID(), "*prize #18");
                                break;
                            }
                        }
                    }
                }
            }

            String[] leaderInfo = flagTimer.getTeamLeader( MVPs );
            if( leaderInfo.length != 3 )
                return;
            String name, MVplayers = "";
            MVPs.remove( leaderInfo[0] );
            if( !leaderInfo[2].equals("") ) {
                String otherleaders[] = leaderInfo[2].split(", ");
                for( int j = 0; j<otherleaders.length; j++ )
                    MVPs.remove( otherleaders[j] );
            }
            Iterator<String> i = MVPs.iterator();

            if( i.hasNext() ) {
                switch( special ) {
                case 1:  // "Refreshments" -- replenishes all essentials + gives anti
                    m_botAction.sendArenaMessage( "Prize for MVPs: Refreshments! (+ AntiWarp for all loyal spiders)" );
                    break;
                case 2:  // "Full shrap"
                    m_botAction.sendArenaMessage( "Prize for MVPs: Full shrap!" );
                    break;
                case 3:  // "Trophy" -- decoy given
                    m_botAction.sendArenaMessage( "Prize for MVPs: Life-size Trophies of Themselves!" );
                    break;
                case 4:  // "Double bounty reward"
                    m_botAction.sendArenaMessage( "Prize for MVPs: Double Bounty Bonus!  (MVP bounty: " + weight * 2 + ")" );
                    break;
                case 5:  // "Triple trophy" -- 3 decoys
                    m_botAction.sendArenaMessage( "Prize for MVPs: The Triple Platinum Trophy!" );
                    break;
                case 6:  // "Techno Dance Party" -- plays victory music :P
                    m_botAction.sendArenaMessage( "Prize for MVPs: Ultimate Techno Dance Party!", 102);
                    break;
                case 7:  // "Sore Loser's Revenge" -- engine shutdown!
                    m_botAction.sendArenaMessage( "Prize for MVPs: Sore Loser's REVENGE!" );
                    break;
                case 8:  // "Bodyguard" -- shields
                    m_botAction.sendArenaMessage( "Prize for MVPs: Personal Body-Guard!" );
                    break;
                }

                MVplayers = (String)i.next();
                int grabs = flagTimer.getFlagGrabs(MVplayers);
                if( grabs > 0 )
                    MVplayers += "(" + grabs + ")";
            }
            int grabs = 0;
            while( i.hasNext() ) {
                name = (String)i.next();
                grabs = flagTimer.getFlagGrabs(name);
                if( grabs > 0 )
                    MVplayers = MVplayers + ", " + name + "(" + grabs + ")";
                else
                    MVplayers = MVplayers + ", " + name;
            }

            if( leaderInfo[0] != "" ) {
                if( leaderInfo[2] == "" )
                    m_botAction.sendArenaMessage( "Team Leader was " + leaderInfo[0] + "!  (" + leaderInfo[1] + " flag claim(s) + MVP)" );
                else
                    m_botAction.sendArenaMessage( "Team Leaders were " + leaderInfo[2] + "and " + leaderInfo[0] + "!  (" + leaderInfo[1] + " flag claim(s) + MVP)" );
            }
            if( MVplayers != "" )
                m_botAction.sendArenaMessage( "MVPs (+ claims): " + MVplayers );

        } catch(Exception e) {
            Tools.printStackTrace( e );
        }

        int intermissionTime = 10000;

        if( gameOver ) {
            intermissionTime = 20000;
            doScores(intermissionTime);

            int diff = 0;
            String winMsg = "";
            if( freq0Score >= maxScore ) {
                if( freq1Score == 0 )
                    diff = -1;
                else
                    diff = freq0Score - freq1Score;
            } else if( freq1Score >= maxScore ) {
                if( freq0Score == 0 )
                    diff = -1;
                else
                    diff = freq1Score - freq0Score;
            }
            switch( diff ) {
            case -1:
                winMsg = " for their masterful victory!";
                break;
            case 1:
                winMsg = " for their close win!";
                break;
            case 2:
                winMsg = " for a well-executed victory!";
                break;
            default:
                winMsg = " for their win!";
                break;
            }
            m_botAction.sendArenaMessage( "GAME OVER!  Freq " + flagholdingFreq + " has won the game after " + getTimeString( flagTimer.getTotalSecs() ) +
                    " (" + weight + " bounty bonus)  Final score: " + freq0Score + " - " + freq1Score, 2 );
            m_botAction.sendArenaMessage( "Give congratulations to FREQ " + flagholdingFreq + winMsg );

            freq0Score = 0;
            freq1Score = 0;
        }	else
        		doScores(intermissionTime);


        try {
            flagTimer.endGame();
            m_botAction.cancelTask(flagTimer);
            m_botAction.cancelTask(intermissionTimer);
        } catch (Exception e ) {
        }

        intermissionTimer = new IntermissionTask();
        m_botAction.scheduleTask( intermissionTimer, intermissionTime );
    }
    
    
    /**
     * This private class counts the consecutive flag time an individual team racks up.
     * Upon reaching the time needed to win, it fires the end of the round.
     */
    private class FlagCountTask extends TimerTask {
    	
        int flagHoldingFreq, flagClaimingFreq;
        int secondsHeld, totalSecs, claimSecs, preTimeCount;
        int claimerID;
        boolean isStarted, isRunning, isBeingClaimed;

        /**
         * FlagCountTask Constructor
         */
        public FlagCountTask() {
            flagHoldingFreq = -1;
            secondsHeld = 0;
            totalSecs = 0;
            claimSecs = 0;
            isStarted = false;
            isRunning = false;
            isBeingClaimed = false;
            flagClaims = new HashMap<String,Integer>();
            kills = new HashMap<String,Integer>();
            deaths = new HashMap<String,Integer>();
        }

        /**
         * This method is called by the FlagClaimed event, and tracks who currently
         * has or is in the process of claiming the flag.  While the flag can physically
         * be claimed in the game, 3 seconds are needed to claim it for the purpose of
         * the game.
         * @param freq Frequency of flag claimer
         * @param pid PlayerID of flag claimer
         */
        public void flagClaimed( int freq, int pid )
        {
            if( isRunning == false || freq == -1 )
                return;

            // Return the flag back to the team that had it if the claim attempt
            // is unsuccessful (countered by the holding team)
            if( freq == flagHoldingFreq ) {
                isBeingClaimed = false;
                claimSecs = 0;
                return;
            }

            if( freq != flagHoldingFreq ) {
                if( (!isBeingClaimed) || (isBeingClaimed && freq != flagClaimingFreq) ) {
                    claimerID = pid;
                    flagClaimingFreq = freq;
                    isBeingClaimed = true;
                    claimSecs = 0;
                }
            }
        }
        
        public void addPlayerKill(String player) {
        	Integer count = kills.get( player );
            if( count == null ) {
            	kills.put( player, new Integer(1) );
            } else {
            	kills.put( player, new Integer( count.intValue() + 1) );
            }
        }
        
        public void addPlayerDeath(String player) {
        	Integer count = deaths.get( player );
            if( count == null ) {
            	deaths.put( player, new Integer(1) );
            } else {
            	deaths.put( player, new Integer( count.intValue() + 1) );
            }
        }

        /**
         * Assigns flag (internally) to the claiming frequency.
         *
         */
        public void assignFlag() 
        {
            flagHoldingFreq = flagClaimingFreq;

            int remain = getTimeRemaining();

            Player p = m_botAction.getPlayer( claimerID );
            if( p != null ) {

                addFlagClaim( p.getPlayerName() );

                if( remain < 60 ) {
                    if( remain < 4 )
                        {m_botAction.sendArenaMessage( "INCONCIEVABLE!!: " + p.getPlayerName() + " claims flag for " + (flagHoldingFreq < 100 ? "Freq " + flagHoldingFreq : "priv. freq" ) + " with just " + remain + " second" + (remain == 1 ? "" : "s") + " left!", 65 );m_botAction.showObject(2500);m_botAction.showObject(2600);} //'Hot Freaking Daym!!' lvz
                    else if( remain < 11 )
                        {m_botAction.sendArenaMessage( "AMAZING!: " + p.getPlayerName() + " claims flag for " + (flagHoldingFreq < 100 ? "Freq " + flagHoldingFreq : "priv. freq" ) + " with just " + remain + " sec. left!" );m_botAction.showObject(2600);} // 'Daym!' lvz
                    else if( remain < 25 )
                        m_botAction.sendArenaMessage( "SAVE!: " + p.getPlayerName() + " claims flag for " + (flagHoldingFreq < 100 ? "Freq " + flagHoldingFreq : "priv. freq" ) + " with " + remain + " sec. left!" );
                    else
                        m_botAction.sendArenaMessage( "Save: " + p.getPlayerName() + " claims flag for " + (flagHoldingFreq < 100 ? "Freq " + flagHoldingFreq : "priv. freq" ) + " with " + remain + " sec. left." );
                }
            }

            m_botAction.showObject(2400); // Shows flag claimed lvz
            isBeingClaimed = false;
            flagClaimingFreq = -1;
            secondsHeld = 0;

        }
        
        public int getTimeRemaining() {
        	return (flagMinutesRequired * 60) - secondsHeld;
        }

        /**
         * Increments a count for player claiming the flag.
         * @param name Name of player.
         */
        public void addFlagClaim( String name ) {
            Integer count = flagClaims.get( name );
            if( count == null ) {
                flagClaims.put( name, new Integer(1) );
            } else {
                flagClaims.remove( name );
                flagClaims.put( name, new Integer( count.intValue() + 1) );
            }
        }

        /**
         * Ends the game for the timer's internal purposes.
         */
        public void endGame() {
            objs.hideAllObjects();
            m_botAction.setObjects();
            isRunning = false;
        }

        /**
         * Sends time info to requested player.
         * @param name Person to send info to
         */
        public void sendTimeRemaining( String name ) {
            m_botAction.sendSmartPrivateMessage( name, getTimeInfo() );
        }

        /**
         * @return True if a game is currently running; false if not
         */
        public boolean isRunning() {
            return isRunning;
        }

        /**
         * Gives the name of the top flag claimers out of the MVPs.  If there is
         * a tie, does not care because it's only bragging rights anyway. :P
         * @return Array of size 2, index 0 being the team leader and 1 being # flaggrabs
         */
        public String[] getTeamLeader( HashSet<String> MVPs ) {
            String[] leaderInfo = {"", "", ""};
            HashSet <String>ties = new HashSet<String>();

            if( MVPs == null )
                return leaderInfo;
            try {
                Iterator<String> i = MVPs.iterator();
                Integer dummyClaim, highClaim = new Integer(0);
                String leader = "", dummyPlayer;

                while( i.hasNext() ) {
                    dummyPlayer = i.next();
                    dummyClaim = flagClaims.get( dummyPlayer );
                    if( dummyClaim != null ) {
                        if( dummyClaim.intValue() > highClaim.intValue() ) {
                            leader = dummyPlayer;
                            highClaim = dummyClaim;
                            ties.clear();
                        } else if ( dummyClaim.intValue() == highClaim.intValue() ) {
                            ties.add(dummyPlayer);
                        }
                    }
                }
                leaderInfo[0] = leader;
                leaderInfo[1] = highClaim.toString();
                i = ties.iterator();
                while( i.hasNext() )
                    leaderInfo[2] += i.next() + ", ";
                return leaderInfo;

            } catch (Exception e ) {
                Tools.printStackTrace( e );
                return leaderInfo;
            }

        }

        /**
         * Returns number of flag grabs for given player.
         * @param name Name of player
         * @return Flag grabs
         */
        public int getFlagGrabs( String name ) {
            Integer grabs = flagClaims.get( name );
            if( grabs == null )
                return 0;
            else
                return grabs;
        }

        /**
         * @return Time-based status of game
         */
        public String getTimeInfo() {
            int roundNum = freq0Score + freq1Score + 1;

            if( isRunning == false ) {
                if( roundNum == 1 )
                    return "Round 1 of a new game is just about to start.";
                else
                    return "We are currently in between rounds (round " + roundNum + " starting soon).  Score: " + freq0Score + " - " + freq1Score;
            }
            return "ROUND " + roundNum + " Stats: " + (flagHoldingFreq == -1 || flagHoldingFreq > 99 ? "?" : "Freq " + flagHoldingFreq ) + " holding for " + getTimeString(secondsHeld) + ", needs " + getTimeString( (flagMinutesRequired * 60) - secondsHeld ) + " more.  [Time: " + getTimeString( totalSecs ) + "]  Score: " + freq0Score + " - " + freq1Score;
        }

        /**
         * @return Total number of seconds round has been running.
         */
        public int getTotalSecs() {
            return totalSecs;
        }

        /**
         * @return Frequency that currently holds the flag
         */
        public int getHoldingFreq() {
            return flagHoldingFreq;
        }

        /**
         * Timer running once per second that handles the starting of a round,
         * displaying of information updates every 5 minutes, the flag claiming
         * timer, and total flag holding time/round ends.
         */
        public void run()
        {
            if( isStarted == false ) {
                int roundNum = freq0Score + freq1Score + 1;
                if( preTimeCount == 0 ) {
                    m_botAction.sendArenaMessage( "Next round begins in 10 seconds . . ." );
                    if( strictFlagTimeMode )
                        safeWarp();
                }
                preTimeCount++;

                if( preTimeCount >= 10 ) {
                    isStarted = true;
                    isRunning = true;
                    String message = ( roundNum == MAX_FLAGTIME_ROUNDS ? "FINAL ROUND" : "ROUND " + roundNum) + " START!  Hold flag for " + flagMinutesRequired + " consecutive minute" + (flagMinutesRequired == 1 ? "" : "s") + " to win the round.";
                    int sound = strictFlagTimeMode ? Tools.Sound.GOGOGO : Tools.Sound.BEEP1;
                    m_botAction.sendArenaMessage( message, sound );
                    m_botAction.resetFlagGame();
                    setupPlayerTimes();
                    warpPlayers(strictFlagTimeMode);
                    return;
                }
            }

            if( isRunning == false )
                return;

            totalSecs++;

            // Display mode info at 5 min increments, unless we are near the end of a game
            if( (totalSecs % (5 * 60)) == 0 && ( (flagMinutesRequired * 60) - secondsHeld > 30) ) {
                m_botAction.sendArenaMessage( getTimeInfo() );
            }

            if( isBeingClaimed ) {
                claimSecs++;
                if( claimSecs >= FLAG_CLAIM_SECS ) {
                    claimSecs = 0;
                    assignFlag();
                }
                return;
            }

            if( flagHoldingFreq == -1 )
                return;

            secondsHeld++;

            do_updateTimer();

            int flagSecsReq = flagMinutesRequired * 60;
            if( secondsHeld >= flagSecsReq ) {
                endGame();
                doEndRound();
            } else if( flagSecsReq - secondsHeld == 60 ) {
                m_botAction.sendArenaMessage( (flagHoldingFreq < 100 ? "Freq " + flagHoldingFreq : "Private freq" ) + " will win in 60 seconds." );
            } else if( flagSecsReq - secondsHeld == 10 ) {
                m_botAction.sendArenaMessage( (flagHoldingFreq < 100 ? "Freq " + flagHoldingFreq : "Private freq" ) + " will win in 10 seconds . . ." );
            }
        }

        /**
         * Runs the LVZ-based timer.
         */
        private void do_updateTimer() {
            int secsNeeded = flagMinutesRequired * 60 - secondsHeld;
            objs.hideAllObjects();
            int minutes = secsNeeded / 60;
            int seconds = secsNeeded % 60;
            if( minutes < 1 ) objs.showObject( 1100 );
            if( minutes > 10 )
                objs.showObject( 10 + ((minutes - minutes % 10)/10) );
            objs.showObject( 20 + (minutes % 10) );
            objs.showObject( 30 + ((seconds - seconds % 10)/10) );
            objs.showObject( 40 + (seconds % 10) );
            m_botAction.setObjects();
        }
    }
	
    /**
     * This private class starts the round.
     */
    private class StartRoundTask extends TimerTask {

        /**
         * Starts the round when scheduled.
         */
        public void run() {
            doStartRound();
        }
    }


    /**
     * This private class provides a pause before starting the round.
     */
    private class IntermissionTask extends TimerTask {

        /**
         * Starts the intermission/rule display when scheduled.
         */
        public void run() {
            doIntermission();
            m_botAction.showObject(1000); //Shows intermission lvz
        }
    }

    /**
     * Used to turn on/off a set of LVZ objects at a particular time.
     */
    private class AuxLvzTask extends TimerTask {
        public int[] objNums;
        public boolean[] showObj;

        /**
         * Creates a new AuxLvzTask, given obj numbers defined in the LVZ and whether
         * or not to turn them on or off.  Cardinality of the two arrays must be the same.
         * @param objNums Numbers of objs defined in the LVZ to turn on or off
         * @param showObj For each index, true to show the obj; false to hide it
         */
        public AuxLvzTask(int[] objNums, boolean[] showObj)	{
            if( objNums.length != showObj.length )
                throw new RuntimeException("AuxLvzTask constructor error: Arrays must have same cardinality.");
            this.objNums = objNums;
            this.showObj = showObj;
        }

        /**
         * Shows and hides set objects.
         */
        public void run() {
        	for(int i=0 ; i<objNums.length ; i++)	{
                if(showObj[i])
                    m_botAction.showObject(objNums[i]);
                else
                	m_botAction.hideObject(objNums[i]);
            }
        }
    }

	public void stop()
	{
		if(!isFlagTimeStarted())
            throw new RuntimeException( "Flag Time mode is not currently running." );

        m_botAction.sendArenaMessage( "Flag Time mode has been disabled." );

        try {
            flagTimer.endGame();
            m_botAction.cancelTask(flagTimer);
            m_botAction.cancelTask(intermissionTimer);
            m_botAction.cancelTask(startTimer);
        } catch (Exception e ) {
        }

        stopFlagTimeStarted();
        strictFlagTimeMode = false;
	}


	@Override
	public void handleCommand(String sender, String command) {
		
		 try {
            if(command.equals("!time"))
                doTimeCmd(sender);
            else if(command.trim().equals("!team") || command.trim().equals("!tea"))
                doShowTeamCmd(sender);
            else if(command.trim().equals("!terr") || command.trim().equals("!t"))
                doTerrCmd(sender);
            else if(command.trim().equals("!warp") || command.trim().equals("!w"))
                doWarpCmd(sender);
            
        } catch(RuntimeException e) {
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
		
	}
	
	@Override
	public void handleModCommand(String sender, String command) {
		
		 try {
            if(command.startsWith("!starttime "))
                doStartTimeCmd(sender, command.substring(11));
            else if(command.equals("!stricttime"))
                doStrictTimeCmd(sender);
            else if(command.equals("!stoptime"))
                doStopTimeCmd(sender);
            else if (command.equals("!autowarp"))
            	doAutowarpCmd(sender);
			else if (command.equals("!allowwarp"))
				doAllowWarpCmd(sender);
            
        } catch(RuntimeException e) {
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
		
	}
	
	@Override
	public String[] getHelpMessage() {
		return new String[] {
			pubsystem.getHelpLine("!warp    -- Warps you into flagroom at start of next round. (abbv: !w)"),
            pubsystem.getHelpLine("!terr    -- Shows terriers on the team and their last seen locations. (abbv: !t)"),
            pubsystem.getHelpLine("!team    -- Tells you which ships your team members are in."),
            pubsystem.getHelpLine("!time    -- Displays info about time remaining in flag time round."),
        };
	}

	@Override
	public String[] getModHelpMessage() {
		return new String[] {
			pubsystem.getHelpLine("!starttime <#>    -- Starts Flag Time game to <#> minutes"),
			pubsystem.getHelpLine("!stoptime         -- Ends Flag Time mode."),
			pubsystem.getHelpLine("!stricttime       -- Toggles strict mode (all players warped)"),
			pubsystem.getHelpLine("!autowarp         -- Enables and disables 'opt out' warping style"),
			pubsystem.getHelpLine("!allowwarp        -- Allow/Disallow the !warp command"),
        };
	}

	@Override
	public void requestEvents(EventRequester eventRequester) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}
	

    /**
     * Starts a "flag time" mode in which a team must hold the flag for a certain
     * consecutive number of minutes in order to win the round.
     *
     * @param sender is the person issuing the command.
     * @param argString is the number of minutes to hold the game to.
     */
    public void doStartTimeCmd(String sender, String argString )
    {
		if(isFlagTimeStarted())
            throw new RuntimeException( "Flag Time mode has already been started." );

        int min = 0;

        try {
            min = (Integer.valueOf( argString )).intValue();
        } catch (Exception e) {
            throw new RuntimeException( "Bad input.  Please supply a number." );
        }

        if( min < 1 || min > 120 )
            throw new RuntimeException( "The number of minutes required must be between 1 and 120." );

        flagMinutesRequired = min;

        m_botAction.sendArenaMessage( "Flag Time mode has been enabled." );

        m_botAction.sendArenaMessage( "Object: Hold flag for " + flagMinutesRequired + " consecutive minute" + (flagMinutesRequired == 1 ? "" : "s") + " to win a round.  Best " + ( MAX_FLAGTIME_ROUNDS + 1) / 2 + " of "+ MAX_FLAGTIME_ROUNDS + " wins the game." );
        if( strictFlagTimeMode )
            m_botAction.sendArenaMessage( "Round 1 begins in 60 seconds.  All players will be warped at round start." );
        else
            if(isAutoWarpEnabled())
                m_botAction.sendArenaMessage( "Round 1 begins in 60 seconds.  You will be warped into flagroom at round start (type !warp to change). -" + m_botAction.getBotName() );
            else
                m_botAction.sendArenaMessage( "Round 1 begins in 60 seconds.  PM me with !warp to warp into flagroom at round start. -" + m_botAction.getBotName() );

        startFlagTimeStarted();
        freq0Score = 0;
        freq1Score = 0;

        m_botAction.scheduleTask( new StartRoundTask(), 60000 );
    }


    /**
     * Toggles "strict" flag time mode in which all players are first warped
     * automatically into safe (must be set), and then warped into base.
     *
     * @param sender is the person issuing the command.
     */
    public void doStrictTimeCmd(String sender ) {
        if( strictFlagTimeMode ) {
        	strictFlagTimeMode = false;
            if( isFlagTimeStarted() )
                m_botAction.sendSmartPrivateMessage(sender, "Strict flag time mode disabled. Changes will go into effect next round.");
            else
                m_botAction.sendSmartPrivateMessage(sender, "Strict flag time mode disabled. !starttime <minutes> to begin a normal flag time game.");
        } else {
        	strictFlagTimeMode = true;
            if( isFlagTimeStarted()) {
                m_botAction.sendSmartPrivateMessage(sender, "Strict flag time mode enabled. All players will be warped into base next round.");
            } else {
                m_botAction.sendSmartPrivateMessage(sender, "Strict flag time mode enabled. !starttime <minutes> to begin a strict flag time game.");
            }
        }
    }


    /**
     * Ends "flag time" mode.
     *
     * @param sender is the person issuing the command.
     */
    public void doStopTimeCmd(String sender )
    {
        stop();
    }

	public void statusMessage(String playerName) {
        if(isFlagTimeStarted()) {
            if( flagTimer != null)
                m_botAction.sendPrivateMessage(playerName, flagTimer.getTimeInfo() );
        }
	}


	@Override
	public void handleDisconnect() {
		objs.hideAllObjects();
	}
	
	public boolean isStarted() {
		if (flagTimer == null)
			return false;
		return flagTimer.isStarted;
	}
	
	/** WARP METHODS **/
	
	public void autoWarpEnable() {
		this.autoWarp = true;
	}
	
	public void autoWarpDisable() {
		this.autoWarp = false;
	}
	
	public void warpEnable() {
		this.warpEnabled = true;
	}
	
	public void warpDisable() {
		this.warpEnabled = false;
	}
	
	public boolean isWarpEnabled() {
		return warpEnabled;
	}
	
	public boolean isAutoWarpEnabled() {
		return autoWarp;
	}
	
    /**
     * Turns on or off "autowarp" mode, where players opt out of warping into base,
     * rather than opting in.
     *
     * @param sender is the person issuing the command.
     */
    public void doAutowarpCmd(String sender) {
        if( autoWarp ) {
            m_botAction.sendPrivateMessage(sender, "Players will no longer automatically be added to the !warp list when they enter the arena.");
            autoWarpDisable();
        } else {
            m_botAction.sendPrivateMessage(sender, "Players will be automatically added to the !warp list when they enter the arena.");
            autoWarpEnable();
        }
    }
    
    /**
     * Turns on or off allowing players to use !warp to get into base at the start of a round.
     *
     * @param sender is the person issuing the command.
     */
    public void doAllowWarpCmd(String sender) {
        if( isWarpEnabled() ) {
            m_botAction.sendPrivateMessage(sender, "Players will no longer be able to use !warp.");
            warpPlayers.clear();
            warpDisable();
        } else {
            m_botAction.sendPrivateMessage(sender, "Players will be allowed to use !warp.");
            warpEnable();
        }
    }
	
	public void doWarpCmd(String sender) {
		PubPlayer player = context.getPlayerManager().getPlayer(sender);
		if (player == null)
			return;

		if (!isFlagTimeStarted()) {
			m_botAction.sendSmartPrivateMessage(sender,
					"Flag Time mode is not currently running.");
			return;
		} 
		else if (strictFlagTimeMode) {
			m_botAction.sendSmartPrivateMessage(sender,"Strict Flag mode is currently running, !warp has no effect. You will automatically be warped.");
			return;
		} 
		else if (warpEnabled) {
			m_botAction.sendSmartPrivateMessage(sender,"Warping into base at round start is not currently allowed.");
			return;
		}

		// Terrs and Levis can't warp into base if Levis are enabled
		if (context.getPlayerManager().isShipRestricted(Tools.Ship.LEVIATHAN)) {
			Player p = m_botAction.getPlayer(sender);
			if (p.getShipType() == Tools.Ship.LEVIATHAN) {
				m_botAction.sendSmartPrivateMessage(sender,"Leviathans can not warp in to base at round start.");
				return;
			}
			if (p.getShipType() == Tools.Ship.TERRIER) {
				m_botAction
						.sendSmartPrivateMessage(sender,
								"Terriers can not warp into base at round start while Leviathans are enabled.");
				return;
			}
		}

		if (warpPlayers.containsKey(sender)) {
			warpPlayers.remove(sender);
			m_botAction.sendSmartPrivateMessage(sender,"You will NOT be warped inside FR at every round start. !warp again to turn back on.");
		} else {
			warpPlayers.put(sender, player);
			m_botAction.sendSmartPrivateMessage(sender,"You will be warped inside FR at every round start. Type !warp to turn off.");
		}
	}
	    
	/**
	 * Warps a player within a radius of 2 tiles to provided coord.
	 * 
	 * @param playerName
	 * @param xCoord
	 * @param yCoord
	 * @param radius
	 * @author Cpt.Guano!
	 */
	private void doPlayerWarp(String playerName, int xCoord, int yCoord) {
		
		// Don't warp a player dueling
		if (context.getPubChallenge().isEnabled()) {
			if (context.getPubChallenge().isDueling(playerName))
				return;
		}
		
		int radius = 2;
		double randRadians;
		double randRadius;
		int xWarp = -1;
		int yWarp = -1;

		randRadians = Math.random() * 2 * Math.PI;
		randRadius = Math.random() * radius;
		xWarp = calcXCoord(xCoord, randRadians, randRadius);
		yWarp = calcYCoord(yCoord, randRadians, randRadius);

		m_botAction.warpTo(playerName, xWarp, yWarp);
	}

	/**
	 * Warps all players who have PMed with !warp into FR at start. Ensures
	 * !warpers on freqs are warped all to 'their' side, but not predictably.
	 */
	public void warpPlayers(boolean allPlayers) {

		Iterator<?> i;

		if (allPlayers)
			i = m_botAction.getPlayingPlayerIterator();
		else
			i = warpPlayers.keySet().iterator();

		Random r = new Random();
		int rand;
		Player p;
		String pname;
		LinkedList<String> nullPlayers = new LinkedList<String>();

		int randomside = r.nextInt(2);

		while (i.hasNext()) {
			if (allPlayers) {
				p = (Player) i.next();
				pname = p.getPlayerName();
			} else {
				pname = (String) i.next();
				p = m_botAction.getPlayer(pname);
			}

			if (p != null) {
				if (allPlayers)
					rand = 0; // Warp freqmates to same spot in strict mode.
				// The warppoints @ index 0 must be set up
				// to default/earwarps for this to work properly.
				else
					rand = r.nextInt(warpPtsLeftX.length);
				if (p.getFrequency() % 2 == randomside)
					doPlayerWarp(pname, warpPtsLeftX[rand], warpPtsLeftY[rand]);
				else
					doPlayerWarp(pname, warpPtsRightX[rand],
							warpPtsRightY[rand]);
			} else {
				if (!allPlayers) {
					nullPlayers.add(pname);
				}
			}
		}

		if (!nullPlayers.isEmpty()) {
			i = nullPlayers.iterator();
			while (i.hasNext()) {
				warpPlayers.remove((String) i.next());
			}
		}
	}

	/**
	 * In Strict Flag Time mode, warp all players to a safe 10 seconds before
	 * starting. This gives a semi-official feeling to the game, and resets all
	 * mines, etc.
	 */
	public void safeWarp() {
		// Prevent pre-laid mines and portals in strict flag time by setting to
		// WB and back again (slightly hacky)
		HashMap<String, Integer> players = new HashMap<String, Integer>();
		HashMap<String, Integer> bounties = new HashMap<String, Integer>();
		Iterator<Player> it = m_botAction.getPlayingPlayerIterator();
		Player p;
		while (it.hasNext()) {
			p = it.next();
			if (p != null) {
				if (p.getShipType() == Tools.Ship.SHARK
						|| p.getShipType() == Tools.Ship.TERRIER
						|| p.getShipType() == Tools.Ship.LEVIATHAN) {
					players
							.put(p.getPlayerName(),
									new Integer(p.getShipType()));
					bounties.put(p.getPlayerName(), new Integer(p.getBounty()));
					m_botAction.setShip(p.getPlayerName(), 1);
				}
			}
		}
		Iterator<String> it2 = players.keySet().iterator();
		String name;
		Integer ship, bounty;
		while (it2.hasNext()) {
			name = it2.next();
			ship = players.get(name);
			bounty = bounties.get(name);
			if (ship != null)
				m_botAction.setShip(name, ship.intValue());
			if (bounty != null)
				m_botAction.giveBounty(name, bounty.intValue() - 3);
		}

		Iterator<Player> i = m_botAction.getPlayingPlayerIterator();
		while (i.hasNext()) {
			p = i.next();
			if (p != null) {
				if (p.getFrequency() % 2 == 0)
					m_botAction.warpTo(p.getPlayerID(), warpSafeLeftX,
							warpSafeLeftY);
				else
					m_botAction.warpTo(p.getPlayerID(), warpSafeRightX,
							warpSafeRightY);
			}
		}
	}

	private int calcXCoord(int xCoord, double randRadians, double randRadius) {
		return xCoord + (int) Math.round(randRadius * Math.sin(randRadians));
	}

	private int calcYCoord(int yCoord, double randRadians, double randRadius) {
		return yCoord + (int) Math.round(randRadius * Math.cos(randRadians));
	}

	
}
