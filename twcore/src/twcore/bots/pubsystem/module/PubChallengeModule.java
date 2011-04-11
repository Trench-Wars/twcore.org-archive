package twcore.bots.pubsystem.module;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.TimerTask;
import java.util.Map.Entry;
import java.util.Vector;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.game.Player;
import twcore.core.util.Tools;

/*
 * By subby, updated by Arobas+
 */
public class PubChallengeModule extends AbstractModule {

	public static final int MAX_WARP = 2;
	
    private Map<Integer,DuelArea> areas;
    private Map<String,Dueler> duelers;
    private Map<String,Challenge> challenges;
    private Map<String,StartLagout> laggers;
    private Map<String,Long> spam;
    
    // added this due to multiple people asking me to fix, 
    // i didn't know if sharks should get shrap or not so i made it changeable
    private boolean sharkShrap = false;
    private boolean saveDuel = false;
    private boolean allowBets = true;
    private String database = "";
    
    private boolean announceNew = false;
    private boolean announceWinner = false;
    private int announceWinnerAt = 0;
    private int announceZoneWinnerAt = 100000000;
    private int deaths;
    
    private int minBet = 100;
    
        
    public PubChallengeModule (BotAction m_botAction, PubContext context){
        super(m_botAction, context, "Challenge");
        
        reloadConfig();
        
    }

	public void requestEvents(EventRequester eventRequester)
	{
		eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
		eventRequester.request(EventRequester.MESSAGE);
		eventRequester.request(EventRequester.PLAYER_DEATH);
		eventRequester.request(EventRequester.PLAYER_LEFT);
		eventRequester.request(EventRequester.PLAYER_POSITION);
	}

    public void handleEvent(PlayerLeft event) {
        if(!enabled)
            return;
        
        Player p = m_botAction.getPlayer(event.getPlayerID());
        if (p==null)
        	return;
        
        String name = p.getPlayerName();
        
        Dueler dueler = duelers.get(name);
        if (dueler == null)
        	return;
        
        Challenge challenge = dueler.challenge;
        if (challenge != null && challenge.isStarted()) {
	        laggers.put(name, new StartLagout(name));
	        m_botAction.scheduleTask(laggers.get(name), 60*1000);
	        m_botAction.sendSmartPrivateMessage(challenge.getOppositeDueler(name).name, "Your opponent has lagged out. He has 60 seconds to return to the game.");
    	}
    }

    public void handleEvent(FrequencyShipChange event) {
    	
        if(!enabled)
            return;
        
        String name = m_botAction.getPlayerName(event.getPlayerID());
        
        Dueler dueler = duelers.get(name);
        if(dueler == null)
            return;
        
        Challenge challenge = dueler.challenge;
        
        if (challenge.isStarted()) {
        
	        if(event.getShipType() == 0)
	        {
	        	dueler.lagouts++;
	            if(dueler.lagouts > 2) {
	            	challenge.setLoser(dueler);
	            	challenge.setWinByLagout();
	                announceWinner(challenge);
	                return;
	            }
	            laggers.put(name, new StartLagout(name));
	            m_botAction.scheduleTask(laggers.get(name), 60*1000);
	            m_botAction.sendSmartPrivateMessage(name, "You have lagged out. You have 60 seconds to return to the game. Use !lagout to return. You have "+(2-duelers.get(name).lagouts)+" lagouts left.");
	            m_botAction.sendSmartPrivateMessage(challenge.getOppositeDueler(name).name, "Your opponent has lagged out. He has 60 seconds to return to the game.");
	            return;
	        }
	        if(event.getShipType() != challenge.ship) {
	        	dueler.lastShipChange = 1;
	        	m_botAction.setShip(name, challenge.ship);
	        }
        }
        
    }
    
    public void handleEvent(PlayerPosition event)
    {
        if(!enabled)
            return;
        
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if(!duelers.containsKey(name))
            return;
        
        Dueler dueler = duelers.get(name);
        
        int x = event.getXLocation()/16;
        int y = event.getYLocation()/16;
        
        if (dueler == null)
        	return;
        
        Challenge challenge = dueler.challenge;
        if (challenge.hasEnded()) {
        	challenges.remove(getKey(challenge));
        	duelers.remove(name);
        	warpToSafe(name, false);
        	return;
        }
        
        if(y > 225 && y < 700 && x > 225 && x < 700 && challenge.isStarted())
        {
            DuelArea area = challenge.area;

            if (!laggers.containsKey(name)
        		&& System.currentTimeMillis()-dueler.backFromLagout > 2 * Tools.TimeInMillis.SECOND
        		&& System.currentTimeMillis()-challenge.startAt > 7 * Tools.TimeInMillis.SECOND
        		&& System.currentTimeMillis()-dueler.lastDeath > 7 * Tools.TimeInMillis.SECOND)
            {
            	
            	if (dueler.lastShipChange!=1) {
            		dueler.warps++;
            	} else {
            		dueler.lastShipChange=0;
            	}
	            
	            if (MAX_WARP-dueler.warps==1 && dueler.lastShipChange==0) {
	            	m_botAction.sendSmartPrivateMessage(name, "You cannot warp/ship change during a duel. If you do it one more time, you lose.");
	            }
	            else if (MAX_WARP == dueler.warps) {
	            	m_botAction.sendSmartPrivateMessage(name, "Maximum of warp/ship change reached during a duel.");
	            	m_botAction.sendSmartPrivateMessage(challenge.getOppositeDueler(dueler).name, "Your opponent has warped too many times, you are the winner.");
	            	challenge.setLoser(dueler);
	            	challenge.getOppositeDueler(dueler).kills = deaths;
	            	announceWinner(challenge);
	            	return;
	            }
            }
            
            if(duelers.get(name).type == Dueler.DUEL_CHALLENGER){
                m_botAction.warpTo(name, area.warp1x, area.warp1y);
                if(laggers.containsKey(name)){
                    m_botAction.cancelTask(laggers.get(name));
                    laggers.remove(name);
                    m_botAction.sendSmartPrivateMessage(name, "You have returned from lagout.");
                } 
                return;
            }

            if(laggers.containsKey(name)){
                m_botAction.cancelTask(laggers.get(name));
                laggers.remove(name);
                m_botAction.sendSmartPrivateMessage(name, "You have returned from lagout.");
            }
        }
            
    }
    
    public void handleEvent(PlayerDeath event) 
    {
        if(!enabled)
            return;
        
        String killer = m_botAction.getPlayerName(event.getKillerID());
        String killee = m_botAction.getPlayerName(event.getKilleeID());
        
        if(!duelers.containsKey(killer) || !duelers.containsKey(killee)) {
        	if (duelers.containsKey(killee)) {
        		duelers.get(killee).lastDeath = System.currentTimeMillis();
        	}
        }
        
        Dueler w = duelers.get(killer);
        Dueler l = duelers.get(killee);

        if(w == null || l == null)
        	return;

        w.lastKill = System.currentTimeMillis();
        
        Challenge challenge = w.challenge;
        if (challenge == null 
        		|| !challenge.getOppositeDueler(w).name.equals(l.name)
        		|| !challenge.isStarted()) {
        	l.lastDeath = System.currentTimeMillis();
        	return;
        }
    
        if (System.currentTimeMillis()-l.lastDeath < 7 * Tools.TimeInMillis.SECOND
        		&& System.currentTimeMillis()-challenge.startAt > 7 * Tools.TimeInMillis.SECOND) {
        	w.spawns++;
        	m_botAction.sendSmartPrivateMessage(w.name, "Spawning is illegal, no count.");
        	m_botAction.sendSmartPrivateMessage(l.name, "No count.");
        	l.lastDeath = System.currentTimeMillis();
        	return;
        }
        
        l.lastDeath = System.currentTimeMillis();
                
        m_botAction.shipReset(killer);
        if (w.challenge.ship == 8) {
            m_botAction.scheduleTask(new ResetMines(killer), 4300);
            m_botAction.scheduleTask(new ResetMines(killee), 4300);
        }
        
        m_botAction.scheduleTask(new UpdateScore(w,l), 1*Tools.TimeInMillis.SECOND);
	      	
        m_botAction.scheduleTask(new EnergyDepletedTask(killer), 1*1000);
        m_botAction.scheduleTask(new EnergyDepletedTask(killer), 2*1000);
        m_botAction.scheduleTask(new EnergyDepletedTask(killer), 3*1000);
        m_botAction.scheduleTask(new EnergyDepletedTask(killer), 4*1000);
        
        m_botAction.scheduleTask(new SpawnBack(killer), 5*1000);
        m_botAction.scheduleTask(new SpawnBack(killee), 5*1000);
                
    }
    
    public DuelArea getEmptyDuelArea(int shipType) {
        Iterator<Entry<Integer,DuelArea>> iter = areas.entrySet().iterator();
        while(iter.hasNext()){
            Entry<Integer,DuelArea> entry = iter.next();
            DuelArea area = entry.getValue();
            if(!area.inUse && area.isShipAllowed(shipType)){
                return area;
            }
        }
        return null;
    }

    public void issueChallenge(String challenger, String challenged, int amount, int ship) {
    	
        Player playerChallenged = m_botAction.getFuzzyPlayer(challenged);
        if(playerChallenged==null){
            m_botAction.sendSmartPrivateMessage(challenger, "No such player in the arena.");
            return;
        }
        challenged = playerChallenged.getPlayerName();
    	
        if(isDueling(challenger)) {
            m_botAction.sendSmartPrivateMessage(challenger, "You are already dueling.");
            return;
        }
        
        if(isDueling(challenged)) {
            m_botAction.sendSmartPrivateMessage(challenger, "This player is already dueling.");
            return;
        }
        String key = challenger.toLowerCase() + "-" + challenged.toLowerCase(); 
        if (spam.containsKey(key) && ((System.currentTimeMillis() - spam.get(key)) < 30*Tools.TimeInMillis.SECOND)) {
            m_botAction.sendSmartPrivateMessage(challenger, "Please wait 30 seconds before challenging this player again.");
            return;
        }
        
        PubPlayer pubChallenger = context.getPlayerManager().getPlayer(challenger);
        PubPlayer pubChallenged = context.getPlayerManager().getPlayer(challenged);
        
        if (pubChallenger == null) {
        	m_botAction.sendSmartPrivateMessage(challenger, "Please wait, you are not in the system yet.");
            return;
        }
        
        if (pubChallenged == null) {
        	m_botAction.sendSmartPrivateMessage(challenger, "Please wait, " + challenged + " is not in the system yet.");
            return;
        }

        if(isChallengeAlreadySent(challenger, challenged)) {
            m_botAction.sendSmartPrivateMessage(challenger, "You have already a pending challenge with "+challenged+".");
            m_botAction.sendSmartPrivateMessage(challenger, "Please remove it using !removechallenge before challenging more.");
            return;
        }
                
        if(ship == Tools.Ship.TERRIER) {
            m_botAction.sendSmartPrivateMessage(challenger, "You cannot duel someone in Terrier.");
            return;
        }

        if(context.getPlayerManager().isShipRestricted(ship)) {
            m_botAction.sendSmartPrivateMessage(challenger, "This ship is restricted in this arena, you cannot duel a player in this ship.");
            return;
        }
        
        if (getEmptyDuelArea(ship)==null) {
        	m_botAction.sendSmartPrivateMessage(challenger, "There is no duel area avalaible. Please try later.");
        	return;
        }
        
        if (context.getMoneySystem().isEnabled()) {
	        if(amount < 10) {
	            m_botAction.sendSmartPrivateMessage(challenger, "You must challenge someone for $10 or more.");
	            return;
	        }
        }
   
        if(challenger.equalsIgnoreCase(challenged)){
            m_botAction.sendSmartPrivateMessage(challenger, "I pity the fool who challenges himself for a duel.");
            return;
        }
                
        if (context.getMoneySystem().isEnabled()) {
	        if(pubChallenger.getMoney() < amount){
	            m_botAction.sendSmartPrivateMessage(challenger, "You don't have enough money.");
	            return;
	        }
	        if(pubChallenged.getMoney() < amount){
	            m_botAction.sendSmartPrivateMessage(challenger, challenged + " does not have enough money.");
	            return;
	        }
        }

        if (context.getMoneySystem().isEnabled()) {
	        m_botAction.sendSmartPrivateMessage(challenged, challenger +" has challenged you to duel for amount of $"+amount+" in " + Tools.shipName(ship) + ". To accept reply !accept "+challenger);
	        m_botAction.sendSmartPrivateMessage(challenged, "Duel to " + deaths + ".");
	        m_botAction.sendSmartPrivateMessage(challenger, "Challenge sent to "+challenged+" for $"+amount+".");
        } else {
	        m_botAction.sendSmartPrivateMessage(challenged, challenger +" has challenged you to duel in " + Tools.shipName(ship) + ". To accept reply !accept "+challenger);
	        m_botAction.sendSmartPrivateMessage(challenged, "Duel to " + deaths + ".");
	        m_botAction.sendSmartPrivateMessage(challenger, "Challenge sent to "+challenged+".");
        }
        
        final Challenge challenge = new Challenge(amount,ship,challenger,challenged);
        addChallenge(challenge);
        spam.put(challenger.toLowerCase() + "-" + challenged.toLowerCase(), System.currentTimeMillis());
		m_botAction.scheduleTask(new RemoveChallenge(challenge), 60*Tools.TimeInMillis.SECOND);
        
    }
    
    public String getKey(Challenge challenge) {
    	return challenge.challengerName+"-"+challenge.challengedName;
    }
    
    public boolean isChallengeAlreadySent(String challenger, String challenged) {
    	return challenges.containsKey(challenger+"-"+challenged);
    }

    
    public void addChallenge(Challenge challenge) {
    	challenges.put(getKey(challenge), challenge);
    }
    
    public void acceptChallenge(String accepter, String challenger) {
    	
    	// Already playing?
        if(duelers.containsKey(accepter)) {
            m_botAction.sendSmartPrivateMessage(accepter, "You are already dueling.");
            return;
        }
        
        // Get the real player name
        Player player = m_botAction.getFuzzyPlayer(challenger);
    	if (player == null) {
            m_botAction.sendSmartPrivateMessage(accepter, "Player not found.");
            return;
    	}
    	challenger = player.getPlayerName();
    
    	Challenge challenge = challenges.get(challenger+"-"+accepter);
        if (challenge == null) {
        	m_botAction.sendSmartPrivateMessage(accepter, "You dont have a challenge from "+challenger+".");
            return;
        }
                
        int amount = challenge.amount;
        int ship = challenge.ship;
                
        if(context.getMoneySystem().isEnabled()) {
        	if (context.getPlayerManager().getPlayer(accepter).getMoney() < amount) {
	            m_botAction.sendSmartPrivateMessage(accepter, "You don't have enough money to accept the challenge. Challenge removed.");
	            m_botAction.sendSmartPrivateMessage(challenger, accepter+" does not have enough money to accept the challenge. Challenge removed.");
	            return;
        	}
        	if (context.getPlayerManager().getPlayer(challenger).getMoney() < amount) {
	            m_botAction.sendSmartPrivateMessage(accepter, challenger + " does not have the money.");
	            return;
        	}
        }
        
        DuelArea area = getEmptyDuelArea(ship);
        if(area == null){
            m_botAction.sendSmartPrivateMessage(accepter, "Unfortunately there is no available duel area. Try again later.");
            m_botAction.sendSmartPrivateMessage(challenger, accepter+"has accepted your challenge. Unfortunately there is no available duel area. Try again later.");
            return;
        } 
        else {
        	area.setInUse();
        }

        // Set duelers in the challenge
        Dueler duelerChallenger = new Dueler(challenger, Dueler.DUEL_CHALLENGER, challenge);
        Dueler duelerAccepter = new Dueler(accepter, Dueler.DUEL_ACCEPTER, challenge);
        duelers.put(challenger, duelerChallenger);
        duelers.put(accepter, duelerAccepter);
        
        challenge.setDuelers(duelerChallenger, duelerAccepter);
        challenge.setArea(area);
        
        if (ship==0) {
        	m_botAction.sendSmartPrivateMessage(challenger, accepter+" has accepted your challenge. You have 10 seconds to get into your dueling ship.");
        	m_botAction.sendSmartPrivateMessage(challenger, "Duel to " + deaths + ".");
        	m_botAction.sendSmartPrivateMessage(accepter, "Challenge accepted. You have 10 seconds to get into your dueling ship.");
        } else {
        	m_botAction.sendSmartPrivateMessage(challenger, accepter+" has accepted your challenge. The duel will start in 10 seconds.");
        	m_botAction.sendSmartPrivateMessage(accepter, "Challenge accepted. The duel will start in 10 seconds.");
        }
    	
        Player playerChallenger = m_botAction.getPlayer(challenger);
        Player playerAccepter = m_botAction.getPlayer(accepter);
        
        duelerAccepter.oldFreq = playerAccepter.isPlaying() ? playerAccepter.getFrequency() : 0;
        duelerChallenger.oldFreq = playerChallenger.isPlaying() ? playerChallenger.getFrequency() : 0;
        
        if(playerChallenger.getShipType() == 0) {
            m_botAction.setShip(challenger, ship);
        }
        if(playerAccepter.getShipType() == 0){
            m_botAction.setShip(accepter, ship);
        }
        
        String moneyMessage = "";
        if (context.getMoneySystem().isEnabled()) {
        	moneyMessage = " for $"+ amount;
        }
        
        if (announceNew && amount >= announceZoneWinnerAt) {
            m_botAction.sendZoneMessage("[PUB] A duel is starting between " + challenger + " and " + accepter + " in " + Tools.shipName(ship) + moneyMessage + ".", Tools.Sound.BEEP1);
        }
        
        removePendingChallenge(challenger, false);
        removePendingChallenge(accepter, false);
        
        // Prepare the timer, in 15 seconds the game should starts (added 5s to allow more bets)
        m_botAction.scheduleTask(new StartDuel(challenge), 10*1000);
        
    }
    
    public void watchDuel(final String sender, String command) {
    	
    	if (command.contains(" ")) {
    		
    		String playerName = command.substring(command.indexOf(" ")+1).trim();
    		Player player = m_botAction.getFuzzyPlayer(playerName);
    		if (player == null) {
    			m_botAction.sendSmartPrivateMessage(sender, "Player not found.");
    			return;
    		}
    		playerName = player.getPlayerName();
    		
    		Dueler dueler = duelers.get(playerName);
    		if (dueler == null) {
    			
    			m_botAction.sendSmartPrivateMessage(sender, playerName + " is not dueling.");
    			
    		} else if (dueler.challenge.area != null) {
    			
    			final int posX = Math.min(dueler.challenge.area.warp1x, dueler.challenge.area.warp2x);
    			final int posY = dueler.challenge.area.warp1y;

    			final int diff = Math.abs((dueler.challenge.area.warp1x-dueler.challenge.area.warp2x)/2);
    			
    			m_botAction.specWithoutLock(sender);
    			TimerTask timer = new TimerTask() {
					public void run() {
						m_botAction.warpTo(sender, posX+diff, posY);
					}
				};
				m_botAction.scheduleTask(timer, 1000);
    			
 
    			if (dueler.challenge.accepter.kills == dueler.challenge.challenger.kills) {
    				m_botAction.sendSmartPrivateMessage(sender, "Current stat: " + dueler.challenge.accepter.kills + "-" + dueler.challenge.challenger.kills);
    			} else if (dueler.challenge.accepter.kills < dueler.challenge.challenger.kills) {
    				m_botAction.sendSmartPrivateMessage(sender, "Current stat: " + dueler.challenge.challenger.kills + "-" + dueler.challenge.accepter.kills + ", " + dueler.challenge.challenger.name + " leading.");
    			} else {
    				m_botAction.sendSmartPrivateMessage(sender, "Current stat: " + dueler.challenge.accepter.kills + "-" + dueler.challenge.challenger.kills + ", " + dueler.challenge.accepter.name + " leading.");
    			}

    		}
    	}
    	
    }
    
    public void placeBet( String name, String cmd ) {
        if(!allowBets) {
            m_botAction.sendPrivateMessage(name, "Betting has been DISABLED for the time being.");
            return;
        }
        
        if( !context.getMoneySystem().isEnabled() ) {
            m_botAction.sendPrivateMessage(name, "[ERROR]  Please provide both name and amount to bet.  Example:  !beton qan:299");
            return;
        }
        
        String[] cmds = cmd.split( ":" );
        if( cmds.length != 2 )
            m_botAction.sendPrivateMessage(name, "[ERROR]  Please provide both name and amount to bet.  Example:  !beton qan:299");
        boolean bettingChallenger = false;
        Challenge foundDuel = null;

        String searchName = m_botAction.getFuzzyPlayerName( cmds[0] );
        if( searchName == null ) {
            m_botAction.sendPrivateMessage( name, "[SNARKY ERROR]  Sorry, the player you have dialed is either disconnected or is no longer in service.  Please check the name, then hang up and try again." );
            return;
        }
        
        if( !duelers.containsKey(searchName) ) {
            m_botAction.sendPrivateMessage( name, "[ERROR]  Dueler not found." );
            return;
        }
        
        Challenge c = duelers.get(searchName).challenge;

        if( c.challengerName.equalsIgnoreCase(name) || c.challengedName.equalsIgnoreCase(name) ) {
            m_botAction.sendPrivateMessage( name, "[ERROR]  You can't bet on your own duel! Nice try though." );
            return;
        }
        
        if( c != null ) {
            if( c.challengerName != null && c.challengedName != null ) {
                if( searchName.equalsIgnoreCase( c.getChallenger() ) ) {
                    foundDuel = c;
                    bettingChallenger = true;
                } else if( searchName.equalsIgnoreCase( c.getChallenged() ) ) {
                    foundDuel = c;
                    bettingChallenger = false;
                }
            }
        }
        
        if( foundDuel == null ) {
            m_botAction.sendPrivateMessage( name, "[ERROR]  Either duel for '" + searchName + "' has not yet started, a player has not accepted the challenge, or the challenge does not exist." );
            return;
        }
        
        if( !foundDuel.canBet() ) {
            m_botAction.sendPrivateMessage( name, "[ERROR]  Duel found, but either the betting time has passed (1 minute after duel start) or the duel is already over." );
            return;
        }
             
        
        Integer amt = 0;
        try {
            amt = Integer.decode( cmds[1] );
            if( amt < minBet ) {
                m_botAction.sendPrivateMessage( name, "[ERROR]  Your bet must be at least $" + minBet + "." );
                return;
            }
        } catch (Exception e) {
            m_botAction.sendPrivateMessage( name, "[ERROR]  Found player, but can't read the amount you have bet.  Usage: !beton <name>:<$amount>" );
            return;
        }
        
        PubPlayer bettor = context.getPlayerManager().getPlayer( name );
        if (!(bettor != null && bettor.getMoney() >= amt)) {
            m_botAction.sendPrivateMessage( name, "[ERROR]  You don't have the kind of cash to just throw around on idle bets, friend.  (You have $" + bettor.getMoney() + " available to bet.)" );
            return;
        }
        
        if( !foundDuel.betOnDueler( m_botAction, name, bettor, bettingChallenger, amt ) )
            m_botAction.sendPrivateMessage( name, "[ERROR]  Couldn't finalize bet.  (You are not allowed to be in the duel you're betting on, or bet on both players.)" );
        else
            m_botAction.sendPrivateMessage( name, "[OK!]  Your bet for $" + amt + " has been deducted from your balance and placed on " + searchName + ".  Good luck!  (NOTE: you'll lose your bet if you leave the arena before the duel is finished.)");
        
    }
    
    public void removePendingChallenge(String name, boolean tellPlayer)
    {
        Iterator<Challenge> it = challenges.values().iterator();
        int totalRemoved = 0;
        while(it.hasNext()) {
        	Challenge c = it.next();
        	if (c.challengerName.equals(name)) {
        		if (!c.isStarted()) {
        			if (tellPlayer)
        				m_botAction.sendSmartPrivateMessage(name, "Challenge against " + c.challengedName + " removed.");
        			it.remove();
        			totalRemoved++;
        		}
        	}
        }
        
        if (totalRemoved == 0) {
        	if (tellPlayer)
        		m_botAction.sendSmartPrivateMessage(name, "No pending challenge to remove.");
        }

    }
    
    public Challenge getChallengeStartedByPlayerName(String name) {
        Iterator<Challenge> it = challenges.values().iterator();
        int totalRemoved = 0;
        while(it.hasNext()) {
        	Challenge c = it.next();
        	if (c.isStarted()) {
        		if (c.challengedName.equals(name) || c.challengerName.equals(name))
        			return c;
        	}
        }
        return null;
    }

    private void announceWinner(Challenge challenge)
    {
    	if( challenge ==null || !challenge.hasEnded() ) {
    		throw new RuntimeException("You need to set a winner or a loser! setWinner()/setLoser()");
    	}
    	
    	boolean cancelled = false;
    	
    	Dueler winner = challenge.winner;
    	Dueler loser = challenge.loser;
    	
    	int winnerKills = winner.kills;
    	int loserKills = loser.kills;
    	
    	int money = challenge.amount;
    	
    	String moneyMessage = "";
    	if (context.getMoneySystem().isEnabled()) {
    		moneyMessage = " for $"+money;
    	}

        if(laggers.containsKey(winner.name) && laggers.containsKey(loser.name)) 
        {
            m_botAction.cancelTask(laggers.get(winner.name));
        	m_botAction.sendSmartPrivateMessage(winner.name,"Your duel against "+loser.name+" has been cancelled, both lagout/specced.");
    		m_botAction.sendSmartPrivateMessage(loser.name,"Your duel against "+winner.name+" has been cancelled, both lagout/specced.");
            cancelled = true;
            challenge.returnAllBets( context.getPlayerManager(), m_botAction );
        }
        
        else if(challenge.winByLagout)
        {
        	if (announceWinner && challenge.amount >= announceWinnerAt)
        		m_botAction.sendArenaMessage("[DUEL] " + winner.name + " has defeated "+loser.name+" by lagout in duel" + moneyMessage + ".");
        	else {
        		m_botAction.sendSmartPrivateMessage(winner.name,"You have defeated "+loser.name+" by lagout in duel" + moneyMessage + ".");
        		m_botAction.sendSmartPrivateMessage(loser.name,"You have lost to " + winner.name+" by lagout in duel" + moneyMessage + ".");
            }

            laggers.remove(loser.name);
            challenge.settleAllBets( winner.name, context.getPlayerManager(), m_botAction );

        }
        
        else
        {
        	if (announceWinner && money >= announceWinnerAt)
        		m_botAction.sendArenaMessage("[DUEL] " + winner.name+" has defeated "+loser.name+" "+winnerKills+"-"+loserKills+" in duel" + moneyMessage + ".");
        	else {
        		m_botAction.sendSmartPrivateMessage(winner.name,"You have defeated "+loser.name+" "+winnerKills+"-"+loserKills+" in duel" + moneyMessage + ".");
        		m_botAction.sendSmartPrivateMessage(loser.name,"You have lost to " + winner.name+" "+loserKills+"-"+winnerKills+" in duel" + moneyMessage + ".");
            }
            challenge.settleAllBets( winner.name, context.getPlayerManager(), m_botAction );
        }
        
        // Free the area
        challenge.area.free();
        
        // Give/Remove money
        if (!cancelled && context.getMoneySystem().isEnabled()) {
	        context.getPlayerManager().addMoney(winner.name, money);
	        context.getPlayerManager().removeMoney(loser.name, money, true);
        }
        
        // Setting the frequency before
        if (duelers.containsKey(winner.name)) 
        	m_botAction.setFreq(winner.name, duelers.get(winner.name).oldFreq);
        if (duelers.containsKey(loser.name))
        	m_botAction.setFreq(loser.name, duelers.get(loser.name).oldFreq);
        
        // Removing stuff
        duelers.remove(winner.name);
        if (challenge.winByLagout)
        	duelers.remove(loser.name);
        challenges.remove(getKey(challenge));
        laggers.remove(winner.name);
        laggers.remove(loser.name);
        
    	warpToSafe(winner.name, true);
    	warpToSafe(loser.name, false);
        
        if (saveDuel && !cancelled) {
        
	        String[] fields = {
	        	"fcNameChallenger",
	        	"fcNameAccepter",
	        	"fcWinner",
	        	"fnScoreChallenger",
	        	"fnScoreAccepter",
	        	"fnShip",
	        	"fnWinByLagout",
	        	"fnDuration",
	        	"fnMoney",
	        	"fdDate"
	        };
	        
	        String[] values = {
	        		Tools.addSlashes(winner.type==Dueler.DUEL_CHALLENGER?winner.name:loser.name),
	        		Tools.addSlashes(loser.type==Dueler.DUEL_ACCEPTER?loser.name:winner.name),
	        		Tools.addSlashes(winner.name),
	        		String.valueOf(winner.type==Dueler.DUEL_CHALLENGER?winner.kills:loser.kills),
	        		String.valueOf(loser.type==Dueler.DUEL_ACCEPTER?loser.kills:winner.kills),
	        		String.valueOf(challenge.ship),
	        		String.valueOf(challenge.winByLagout?1:0),
	        		String.valueOf((int)((System.currentTimeMillis()-challenge.startAt)/1000)),
	        		String.valueOf(money),
	        		new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
	        };
	        
	    	m_botAction.SQLBackgroundInsertInto(database, "tblDuel", fields, values);
        }
    	        
    }
    
    private void givePrize(String name) {
    	
    	m_botAction.shipReset(name);
		m_botAction.specificPrize(name, Tools.Prize.FULLCHARGE);
		m_botAction.specificPrize(name, Tools.Prize.MULTIFIRE);
		m_botAction.specificPrize(name, -27); // NEGATIVE ROCKET
		m_botAction.specificPrize(name, -21); // NEGATIVE REPEL
		m_botAction.specificPrize(name, -26); // NEGATIVE BRICK
    	
    }
    
    private class RemoveChallenge extends TimerTask {
    	
    	private Challenge challenge;
    	
    	public RemoveChallenge(Challenge c) {
    		this.challenge = c;
    	}
    	
    	public void run() {
    		if (challenge == null)
    			return;
			if (!challenge.isStarted()) {
				challenges.remove(getKey(challenge));
				m_botAction.sendSmartPrivateMessage(challenge.challengerName, "Challenge against " + challenge.challengedName + " removed. (timeout)");
			}
    	}
    }
    
    private void refreshSpamStopper() {
        Long now = System.currentTimeMillis();
        Vector<String> removes = new Vector<String>();
        for(String k : spam.keySet()) {
            if(now - spam.get(k) > Tools.TimeInMillis.MINUTE)
                removes.add(k);
        }
        
        while(!removes.isEmpty())
            spam.remove(removes.remove(0));
    }
    
    private class ResetMines extends TimerTask{
        
        String name;

        private ResetMines(String name){
            this.name = name;
        }

        @Override
        public void run() {
            
            Dueler dueler = duelers.get(name);
            if (dueler == null || dueler.challenge == null)
                return;
            
            if (dueler != null) {
                m_botAction.shipReset(name);
                m_botAction.warpTo(name, 512, 760);
            }
        }    
        
    }
    
    private class SpawnBack extends TimerTask{
    	
        String name;

        private SpawnBack(String name){
            this.name = name;
        }

        @Override
        public void run() {
        	
        	Dueler dueler = duelers.get(name);
        	if (dueler == null || dueler.challenge == null)
        		return;
        	
        	Challenge challenge = dueler.challenge;
        	
        	if (dueler != null) {
	            
        		if(dueler.type == 1){
	                m_botAction.warpTo(name, challenge.area.warp1x, challenge.area.warp1y);
	            }
	            else if(dueler.type == 2){
	                m_botAction.warpTo(name, challenge.area.warp2x, challenge.area.warp2y);
	            }
                givePrize(name);
        	}
        }     
    }
    
    private class EnergyDepletedTask extends TimerTask{
    	
        String name;

        private EnergyDepletedTask(String name){
            this.name = name;
        }

        @Override
        public void run() {
        	
        	Dueler dueler = duelers.get(name);
        	if (dueler != null) {
        		Challenge challenge = dueler.challenge;
	            m_botAction.specificPrize(name, Tools.Prize.ENERGY_DEPLETED);
        	}
        }     
    }
    
    private class UpdateScore extends TimerTask 
    {
        private Dueler killer;
        private Dueler killed;
        
        private UpdateScore(Dueler killer, Dueler killed) {
            this.killer = killer;
            this.killed = killed;
        }
        
        public void run()
        {
        	
            if (Math.abs(killer.lastDeath-killed.lastDeath) < 2*Tools.TimeInMillis.SECOND) {
            	m_botAction.sendSmartPrivateMessage(killer.name, "No count");
            	return;
            
            } else if (Math.abs(killed.lastKill-killer.lastKill) < 5*Tools.TimeInMillis.SECOND) {
                killed.kills--;
                killer.deaths--;
                m_botAction.sendSmartPrivateMessage(killer.name, "No count, back to " + killer.kills+"-"+killed.kills);
                m_botAction.sendSmartPrivateMessage(killed.name, "No count, back to " + killed.kills+"-"+killer.kills);
            	return;
            
            } else {
            	
                killer.kills++;
                killed.deaths++;
            	
                m_botAction.sendSmartPrivateMessage(killer.name, killer.kills+"-"+killed.kills);
                m_botAction.sendSmartPrivateMessage(killed.name, killed.kills+"-"+killer.kills);

                if(killed.deaths == deaths) {
                	killed.challenge.setWinner(killer);
                    announceWinner(killed.challenge);
                    return;
                }
            }
            

        }
    }
    
    private class StartLagout extends TimerTask 
    {
        private String name;
        
        private StartLagout(String name) {
            this.name = name;
        }
        
        public void run()
        {
        	Dueler dueler = duelers.get(name);
        	if (dueler == null)
        		return;
        	
        	Challenge challenge = dueler.challenge;
        	challenge.setLoser(duelers.get(name));
        	challenge.getOppositeDueler(duelers.get(name)).kills = deaths;
        	challenge.setWinByLagout();
            announceWinner(challenge);
        }
    }
    
    private class StartDuel extends TimerTask 
    {
    	private Challenge challenge;
        private String challenger, accepter;
        private DuelArea area;
        private int ship;
        
        private StartDuel(Challenge challenge)
        {
            this.challenge = challenge;
            this.ship = challenge.ship;
            this.area = challenge.area;
            this.challenger = challenge.challenger.name;
            this.accepter = challenge.accepter.name;
            refreshSpamStopper();
        }
        
        public void run() 
        {
        	challenge.start();
        	
            Player p_chall = m_botAction.getPlayer(challenger);
            Player p_acc = m_botAction.getPlayer(accepter);
            
            if (p_chall == null || p_acc == null) {
                duelers.remove(challenge.challengerName);
                duelers.remove(challenge.challengedName);
                challenges.remove(getKey(challenge));
                laggers.remove(challenge.challengerName);
                laggers.remove(challenge.challengedName);
                area.free();
                if (p_chall == null) 
                	m_botAction.sendSmartPrivateMessage(accepter, "The duel cannot start, " + challenger + " not found.");
                else
                	m_botAction.sendSmartPrivateMessage(challenger, "The duel cannot start, " + accepter + " not found.");
            	return;
            }
            
            int ichalship = p_chall.getShipType();
            int iaccship = p_acc.getShipType();
            
            // Player in spec? specify a ship
            if(ichalship == 0){
                ichalship = ship==0?1:ship;
                m_botAction.setShip(challenger, ichalship);
            }
            if(iaccship == 0){
                iaccship = ship==0?1:ship;
                m_botAction.setShip(accepter, iaccship);
            }
            
            // Set the player in the dueling ship
            if (ship>0 && ichalship!=ship) {
            	m_botAction.setShip(challenger, ship);
            }
            if (ship>0 && iaccship!=ship) {
            	m_botAction.setShip(accepter, ship);
            }            
            
            m_botAction.setFreq(challenger, 0);
            m_botAction.setFreq(accepter, 1);
            m_botAction.warpTo(challenger, area.warp1x, area.warp1y);
            m_botAction.warpTo(accepter, area.warp2x, area.warp2y);
            givePrize(challenger);
            givePrize(accepter);
            
            // putting this here for now, you can move it into givePrize later
            if (ship == 8) {
                if (!sharkShrap) {
                    m_botAction.specificPrize(challenger, -19);
                    m_botAction.specificPrize(challenger, -19);
                    m_botAction.specificPrize(accepter, -19);
                    m_botAction.specificPrize(accepter, -19);
                } else {
                    m_botAction.specificPrize(challenger, 19);
                    m_botAction.specificPrize(challenger, 19);
                    m_botAction.specificPrize(accepter, 19);
                    m_botAction.specificPrize(accepter, 19);
                }
            }
            
            m_botAction.sendSmartPrivateMessage(challenger, "GO GO GO!", Tools.Sound.GOGOGO);
            m_botAction.sendSmartPrivateMessage(accepter, "GO GO GO!", Tools.Sound.GOGOGO);
            
            if (allowBets) {
                m_botAction.sendArenaMessage("A " + Tools.shipName(ship) + " duel is starting between " + challenger + " and " + accepter + ". You have 1 minute to use !beton <name>:<amount> to place a bet on this duel!");
            }
        }
    }
    
    private void warpToSafe(String name, boolean winner)
    {
    	if (winner)
    		m_botAction.warpTo(name, 507, 730);
    	else
    		m_botAction.warpTo(name, 517, 730);
    }
    
    public boolean hasChallenged(String name) {
        Iterator<Challenge> it = challenges.values().iterator();
        while(it.hasNext()) {
        	Challenge c = it.next();
        	if (!c.isStarted() && c.challengerName.equals(name))
        		return true;
        }
        return false;
    }
    
	/**
	 * Case-sensitive
	 */
    public boolean isDueling(String name) {
        return duelers.containsKey(name);
    }

    public void returnFromLagout(String name){
    	
        if(!laggers.containsKey(name)){
            m_botAction.sendSmartPrivateMessage(name, "You have not lagged out from a duel.");
            return;
        }
        m_botAction.cancelTask(laggers.get(name));
        laggers.remove(name);

        Dueler dueler = duelers.get(name);
        if (dueler == null)
        	return;
        
        Challenge challenge = dueler.challenge;
        
        dueler.backFromLagout = System.currentTimeMillis();
        
        if(dueler.type == Dueler.DUEL_CHALLENGER) {
            m_botAction.setShip(name, challenge.ship);
            m_botAction.warpTo(name, challenge.area.warp1x, challenge.area.warp1y);
            m_botAction.setFreq(name, 0);
            return;
        }
        if(dueler.type == Dueler.DUEL_ACCEPTER){
            m_botAction.setShip(name, challenge.ship);
            m_botAction.warpTo(name, challenge.area.warp2x, challenge.area.warp2y);
            m_botAction.setFreq(name, 1);
        }
    }
    
    public String getRealName(String name) {
    	PubPlayer player = context.getPlayerManager().getPlayer(name);
    	if (player != null)
    		return player.getPlayerName();
    	return null;
    }
    
    public void doDebugCmd(String sender) {
    	
    	int count = 0;
    	for(Challenge c: challenges.values()) {

    		String status = c.isStarted() ? "STARTED" : "PENDING";
    		
    		Dueler d1 = null;
    		Dueler d2 = null;
    		if (c.isStarted()) {
    			d1 = c.challenger;
    			d2 = c.accepter;
    		}
    		
    		int k1 = d1==null? -1 : d1.kills;
    		int k2 = d2==null? -1 : d2.kills;
    		
    		m_botAction.sendSmartPrivateMessage(sender, "Challenge ["+status+"] (" + k1 + ":" + k2 + ")");
    		count++;
    	}
    	for(Dueler dueler: duelers.values()) {
    		String status = dueler.challenge.isStarted() ? "STARTED" : "PENDING";
    		m_botAction.sendSmartPrivateMessage(sender, "Dueler ["+status+"] " + dueler.name);
    		count++;
    	}
    	if (count==0)
    		m_botAction.sendSmartPrivateMessage(sender, "Nothing.");
    	
    }
    
    public void doCancelDuelCmd( String sender, String onePlayer ) {

    	String name = getRealName(onePlayer);

        Iterator<Challenge> it = challenges.values().iterator();
        Challenge challenge = null;
        while(it.hasNext()) {
        	Challenge c = it.next();
        	if (c.isStarted()) {
        		if (c.challengedName.equals(sender) || c.challengerName.equals(sender)) {
        			challenge = c;
        			break;
        		}
        	}
        }

    	if (challenge!=null) {
    		String opponent = challenge.getOppositeDueler(name).name;
    		challenge.area.free();
    		challenges.remove(getKey(challenge));
        	duelers.remove(name);
        	duelers.remove(opponent);
        	laggers.remove(name);
        	laggers.remove(opponent);
        	warpToSafe(name, true);
        	warpToSafe(opponent, false);
        	m_botAction.sendSmartPrivateMessage(name, "Your duel has been cancelled by " + sender);
        	m_botAction.sendSmartPrivateMessage(opponent, "Your duel has been cancelled by " + sender);
    	}
    	else {
    		m_botAction.sendSmartPrivateMessage(sender, "No duel found associated with this player.");
    	}

    	
    }
    
	@Override
	public void handleCommand(String sender, String command) {

        if(command.startsWith("!challenge ") || command.startsWith("!duel ")){
            String pieces[];
            if (command.startsWith("!challenge "))
            	pieces = command.substring(11).split(":");
            else
            	pieces = command.substring(7).split(":");
            
            String opponent = "";

            Player p = m_botAction.getFuzzyPlayer(pieces[0]);
            if (p != null)
            	opponent = p.getPlayerName();
            
            // Get the real player name
            if (pieces.length == 3 || (pieces.length == 2 && !context.getMoneySystem().isEnabled())) {
	            PubPlayer player = context.getPlayerManager().getPlayer(opponent);
	            if (player==null) {
	            	m_botAction.sendSmartPrivateMessage(sender, "Player not on the system yet.");
	            	return;
	            } else {
	            	opponent = player.getPlayerName();
	            }
            }
            if(pieces.length == 3 || (pieces.length == 2 && !context.getMoneySystem().isEnabled())) {
                try {
                	int ship = Integer.parseInt(pieces[1]);
                    int amount = pieces.length == 3 ? Integer.parseInt(pieces[2]) : 0;
                    if (ship >= 1 && ship <= 8) {
                    	issueChallenge(sender, opponent, amount, ship);
                    }
                    else {
                    	 m_botAction.sendSmartPrivateMessage(sender, "If you specify a ship, it must be a number between 1 and 8.");
                    }
                }catch(NumberFormatException e){
                	if (context.getMoneySystem().isEnabled())
                		m_botAction.sendSmartPrivateMessage(sender, "Proper use is !challenge name:ship:amount");
                	else
                		m_botAction.sendSmartPrivateMessage(sender, "Proper use is !challenge name:ship");
                }
            }
            else {
            	if (context.getMoneySystem().isEnabled())
            		m_botAction.sendSmartPrivateMessage(sender, "Proper use is !challenge name:ship:amount");
            	else
            		m_botAction.sendSmartPrivateMessage(sender, "Proper use is !challenge name:ship");
            }
            
        } else if(command.startsWith("!accept ")) {
            if(command.length() > 8)
                acceptChallenge(sender, command.substring(8));
        } else if(command.startsWith("!watchduel") || command.startsWith("!wd"))
            watchDuel(sender, command);
        else if(command.startsWith("!beton ") )
            placeBet( sender, command.substring(7) );
        else if(command.startsWith("!removechallenge") || command.equalsIgnoreCase("!rm"))
            removePendingChallenge(sender, true);
        else if(command.equalsIgnoreCase("!lagout"))
            returnFromLagout(sender);
        else if(command.equalsIgnoreCase("!ld") || command.equalsIgnoreCase("!duels"))
            listDuels(sender);
            
	}
	
	public void doToggleBets(String name) {
	    if (allowBets) {
	        allowBets = false;
            m_botAction.sendPrivateMessage(name, "Betting has been DISABLED.");
	    }
	    else {
            allowBets = true;
            m_botAction.sendPrivateMessage(name, "Betting has been ENABLED.");
        }
	}
	
	public void listDuels(String name) {
	    LinkedList<String> ops = new LinkedList<String>();
	    for (Dueler d : duelers.values()) {
	        if (!ops.contains(d.name) && d.challenge.isStarted()) {
	            ops.add(d.challenge.getOppositeDueler(d.name).name);
	            ops.add(d.name);
	            
	            String better = "";
	            String betted = "";
	            if (!d.challenge.challengerBets.isEmpty())
	                better = " (bets)";
                if (!d.challenge.challengedBets.isEmpty())
                    betted = " (bets)";
	            m_botAction.sendSmartPrivateMessage(name, "" + d.challenge.challengerName + better + " vs " + d.challenge.challengedName + betted + " in " + Tools.shipName(d.challenge.ship) + ": " + d.challenge.challenger.kills + "-" + d.challenge.accepter.kills);
	        }
	    }
	    
	    if (ops.isEmpty()) {
            m_botAction.sendSmartPrivateMessage(name, "There are currently no active duels.");
            return;	        
	    }
	}	
	
	public void doSharkShrap(String name) {
	    if (sharkShrap) {
	        sharkShrap = false;
	        m_botAction.sendSmartPrivateMessage(name, "Shark duels now have shrapnel DISABLED");
	    } else {
            sharkShrap = true;
            m_botAction.sendSmartPrivateMessage(name, "Shark duels now have shrapnel ENABLED");
	    }
	}
	
	@Override
	public void handleModCommand(String sender, String command) {

        try {
        	if(command.equalsIgnoreCase("!betting"))
        	    doToggleBets(sender);
        	if(command.startsWith("!cancelchallenge"))
            	doCancelDuelCmd(sender, command.substring(17).trim());
        	else if(command.startsWith("!debugchallenge"))
            	doDebugCmd(sender);
        	else if (command.startsWith("!sharkshrap"))
        	    doSharkShrap(sender);
           
        } catch(RuntimeException e) {
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
	}
	
	@Override
	public String[] getHelpMessage(String sender) {
		if (context.getMoneySystem().isEnabled())
			return new String[] {
				pubsystem.getHelpLine("!challenge <name>:<ship>:<$>  -- Challenge a player to " + deaths + " in a specific ship (1-8) for $X. (!duel)"),
				//pubsystem.getHelpLine("!watchduel <name>             -- Watch the duel of this player. (!wd)"),
				pubsystem.getHelpLine("!removechallenges             -- Cancel your challenges sent."),
                pubsystem.getHelpLine("!duels                        -- Lists the duels currently being played. (!ld)"),
                pubsystem.getHelpLine("!beton <name>:<$>             -- Bet on <name> to win a duel."),
                pubsystem.getHelpLine("!watchduel <name>             -- Displays the score of <names>'s duel. (!wd)"),
	        };
		else
			return new String[] {
		        pubsystem.getHelpLine("!duels                        -- Lists the duels currently being played. (!ld)"),
				pubsystem.getHelpLine("!challenge <name>:<ship>      -- Challenge a player to " + deaths + " in a specific ship (1-8)."),
				pubsystem.getHelpLine("!watchduel <name>             -- Displays the score of <names>'s duel. (!wd)"),
				pubsystem.getHelpLine("!removechallenges             -- Cancel your challenges sent. (!rm)"),
	        };
	}

	@Override
	public String[] getModHelpMessage(String sender) {
		return new String[] {
	        pubsystem.getHelpLine("!betting                     -- Toggles duel betting on or off."),
			pubsystem.getHelpLine("!cancelchallenge <name>      -- Cancel a challenge (specify one of the player)."),
        };
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void reloadConfig() 
	{
		if (challenges == null || challenges.size() == 0)
		{
			this.areas = new HashMap<Integer, DuelArea>();
	        this.duelers = new HashMap<String, Dueler>();
	        this.challenges = new HashMap<String, Challenge>();
	        this.laggers = new HashMap<String,StartLagout>();
	        this.spam = new HashMap<String,Long>();
	
	        // Setting Duel Areas
	        for(int i=1; i<m_botAction.getBotSettings().getInt("duel_area")+1; i++) {
	        	int pos1[] = m_botAction.getBotSettings().getIntArray("duel_area"+i+"_pos1", " ");
	        	int pos2[] = m_botAction.getBotSettings().getIntArray("duel_area"+i+"_pos2", " ");
	        	int shipArray[] = m_botAction.getBotSettings().getIntArray("duel_area"+i+"_ship", ",");
	        	boolean[] ships = { false,false,false,false,false,false,false,false };
	        	for(int ship: shipArray) {
	        		ships[ship-1] = true;
	        	}
	        	areas.put(i, new DuelArea(i, pos1[0],pos1[1],pos2[0],pos2[1], ships));
	        }
	        
	        // Setting Misc.        
	        if (m_botAction.getBotSettings().getInt("duel_enabled")==1) {
	        	enabled = true;
	        }
	        
	        if (m_botAction.getBotSettings().getInt("duel_announce_new")==1) {
	        	announceNew = true;
	        }
	        
	        if (m_botAction.getBotSettings().getInt("duel_announce_winner")==1) {
	        	announceWinner = true;
	        }
	        
	        if (m_botAction.getBotSettings().getInt("duel_database_enabled")==1) {
	        	saveDuel = true;
	        }
	        
	        database = m_botAction.getBotSettings().getString("database"); 
	        
	        announceWinnerAt = m_botAction.getBotSettings().getInt("duel_announce_arena_winner_at"); 
	        announceZoneWinnerAt = m_botAction.getBotSettings().getInt("duel_announce_zone_winner_at");
	        deaths = m_botAction.getBotSettings().getInt("duel_deaths");
		}
	}

	@Override
	public void stop() {
		Iterator<Challenge> it = challenges.values().iterator();
		while (it.hasNext()) {
			Challenge c = it.next();
			if (!c.hasEnded() && c.isStarted()) {
				it.remove();
				c.duelEnded = true;
		    	warpToSafe(c.challengerName, true);
		    	warpToSafe(c.challengedName, false);
		    	try {
			    	duelers.remove(c.challenger);
			    	duelers.remove(c.accepter);
			    	laggers.remove(c.challengerName);
			    	laggers.remove(c.challengedName);
		    	} catch (Exception e) {
		    		Tools.printStackTrace(e);
		    	}
		    	m_botAction.sendPrivateMessage(c.challengerName, "The bot stopped working due to a 'stop' request from a staff member. Your duel has been cancelled.");
		    	m_botAction.sendPrivateMessage(c.challengedName, "The bot stopped working due to a 'stop' request from a staff member. Your duel has been cancelled.");
			}
		}

	}
    
}
class DuelArea {
	
	public int number; // Area number
	
	public boolean inUse = false;
	 
    public int warp1y,warp1x,warp2y,warp2x;
    
    public boolean[] shipAllowed; // 0=wb, 7=shark
    
    public DuelArea(int number, int warp1x, int warp1y, int warp2x, int warp2y, boolean[] shipAllowed) {
    	this.number = number;
        this.warp1x = warp1x;
        this.warp1y = warp1y;
        this.warp2x = warp2x;
        this.warp2y = warp2y;
        this.shipAllowed = shipAllowed;
    }
    
    public boolean isShipAllowed(int shipType) {
    	if (shipType==0) {
    		return false;
    	}
    	if (shipAllowed.length >= shipType) {
    		return shipAllowed[shipType-1];
    	} else {
    		return false;
    	}
    }

    public boolean inUse() {
    	return inUse;
    }
    
    public void setInUse() {
    	this.inUse = true;
    }
    
    public void free() {
    	this.inUse = false;
    }

}

class Dueler {
	
	public static final int DUEL_CHALLENGER = 1;
	public static final int DUEL_ACCEPTER = 2;
	
	public int type = DUEL_CHALLENGER;
	
	public String name;
	public Challenge challenge;
	
	// Stats during a duel
    public int lagouts = 0;
    public int deaths = 0;
    public int kills = 0;
    public int warps = 0;
    public int spawns = 0;
    
    public int oldFreq = 0;
    
    public long lastDeath = 0; // To detect warp vs death and no count (timestamp)
    public long lastKill = 0; // For no count (timestamp)
    public long lastShipChange = 0; // To detect warp vs shipchange (value:0,1,2)
    public long backFromLagout = 0; // To detect warp vs lagout (timestamp)
    
    public Dueler(String name, int type, Challenge challenge){
        this.name = name;
        this.type = type;
        this.challenge = challenge;
    }

}

class Challenge {
	
	public Dueler challenger;
	public Dueler accepter;
	
	public DuelArea area;
	
	public String challengerName;
	public String challengedName;
	
    public int amount;       // Playing for $ money
    public int ship;         // Which ship? 0 = any
    public long startAt = 0; // Started at? Epoch in millis
    
    public boolean duelEnded = false;
    public Dueler winner;
    public Dueler loser;
    public boolean winByLagout = false;
    
    public HashMap<String,Integer> challengerBets;
    public HashMap<String,Integer> challengedBets;
    public int totalC = 0;
    public int totalA = 0;
    
    static int betTimeWindow = 60 * Tools.TimeInMillis.SECOND; // Time after duel start in which you can still bet
    static float betWinMultiplier = 1.8f;   // How much the person wins with a bet (80% of original=default)

    
    public Challenge(int amount, int ship, String challenger, String challenged){
        this.amount = amount;
        this.ship = ship;
        this.challengerName = challenger;
        this.challengedName = challenged;
        
        challengerBets = new HashMap<String,Integer>();
        challengedBets = new HashMap<String,Integer>();
    }
    
    public Dueler getOppositeDueler(Dueler dueler) {
    	return getOppositeDueler(dueler.name);
    }
    
    public String getChallenger() {
        return challengerName;
    }
    
    public String getChallenged() {
        return challengedName;
    }
    
    public Dueler getOppositeDueler(String playerName) {
    	if (challenger!=null && accepter!=null) {
    		if (challenger.name.equals(playerName))
    			return accepter;
    		else
    			return challenger;
    	} else {
    		return null;
    	}
    }
    
    public void setArea(DuelArea area) {
    	this.area = area;
    }
    
    public void setDuelers(Dueler challenger, Dueler accepter) {
    	this.challenger = challenger;
    	this.accepter = accepter;
    }
    
    public void start() {
    	this.startAt = System.currentTimeMillis();
    	challenger.lastDeath = System.currentTimeMillis();
    	accepter.lastDeath = System.currentTimeMillis();
    	challenger.backFromLagout = System.currentTimeMillis();
    	accepter.backFromLagout = System.currentTimeMillis();
    }
    
    public boolean isStarted() {
    	return startAt!=0;
    }
    
    public boolean hasEnded() {
    	return duelEnded;
    }

    public void setWinByLagout() {
    	this.winByLagout = true;
    }
    
    public void setWinner(Dueler dueler) {
    	this.winner = dueler;
    	this.loser = getOppositeDueler(dueler);
    	this.duelEnded = true;
    }
    
    public void setLoser(Dueler dueler) {
    	this.loser = dueler;
    	this.winner = getOppositeDueler(dueler);
    	this.duelEnded = true;
    }
    
    public boolean canBet() {
        return (!duelEnded && (System.currentTimeMillis() - startAt) < betTimeWindow );
    }
    
    public void returnAllBets( PubPlayerManagerModule ppmm, BotAction m_ba ) {
        Integer bet = 0;;
        PubPlayer p;
        for( String n : challengerBets.keySet() ) {
            p = ppmm.getPlayer( n );
            if( p != null ) {
                bet = challengerBets.get( n );
                p.addMoney( bet );
                m_ba.sendSmartPrivateMessage( n, "[BET INFO]  The duel you bet on has been cancelled.  $" + bet + " returned to your account." );
            }
        }
        for( String n : challengedBets.keySet() ) {
            p = ppmm.getPlayer( n );
            if( p != null ) {
                bet = challengedBets.get( n );
                p.addMoney( bet );
                m_ba.sendSmartPrivateMessage( n, "[BET INFO]  The duel you bet on has been cancelled.  $" + bet + " returned to your account." );
            }
        }
        
        challengerBets.clear();
        challengedBets.clear();
        totalC = 0;
        totalA = 0;
    }
    
    public void settleAllBets( String winner, PubPlayerManagerModule ppmm, BotAction m_ba ) {
        Integer bet = 0;
        PubPlayer p;
        boolean challengerWon = false;
        
        if( winner.equalsIgnoreCase( challengerName ) )
            challengerWon = true;
        
        for( String n : challengerBets.keySet() ) {
            p = ppmm.getPlayer( n );
            if( p != null ) {
                bet = challengerBets.get( n );
                if( bet != null ) {
                    if( totalC <= totalA ) {
                        if( challengerWon ) {
                            bet = bet * 2;
                            p.addMoney( bet );
                            m_ba.sendSmartPrivateMessage( n, "[BET WON]  " + challengerName + " defeated " + challengedName + ".  You win $" + bet + "!" );
                        } else
                            m_ba.sendSmartPrivateMessage( n, "[BET LOST]  " + challengedName + " defeated " + challengerName + ".  You lost $" + bet + ".  Better luck next time." );
                    } else {
                        if (challengerWon) {
                            bet = bet + Math.round(totalA * ((float) bet / totalC));
                            p.addMoney( bet );
                            m_ba.sendSmartPrivateMessage( n, "[BET WON]  " + challengerName + " defeated " + challengedName + ".  You win $" + bet + "!" );
                        } else {
                            Integer diff = (Math.round(totalA * ((float) bet / totalC)));
                            p.addMoney( bet - diff );
                            m_ba.sendSmartPrivateMessage( n, "[BET LOST]  " + challengedName + " defeated " + challengerName + ".  You lost $" + diff + ".  Better luck next time." );
                        }
                    }
                    /* old way
                    if( challengerWon ) {
                        bet = Math.round( ((float)bet * betWinMultiplier) );
                        p.addMoney( bet );
                        m_ba.sendSmartPrivateMessage( n, "[BET WON]  " + challengerName + " defeated " + challengedName + ".  You win $" + bet + "!" );
                    } else {
                        m_ba.sendSmartPrivateMessage( n, "[BET LOST]  " + challengedName + " defeated " + challengerName + ".  You lost $" + bet + ".  Better luck next time." );                    
                    }
                    */
                }
            }
        }
        for( String n : challengedBets.keySet() ) {
            p = ppmm.getPlayer( n );
            if( p != null ) {
                bet = challengedBets.get( n );
                if( bet != null ) {
                    if( totalA <= totalC ) {
                        if( !challengerWon ) {
                            bet = bet * 2;
                            p.addMoney( bet );
                            m_ba.sendSmartPrivateMessage( n, "[BET WON]  " + challengedName + " defeated " + challengerName + ".  You win $" + bet + "!" );
                        } else
                            m_ba.sendSmartPrivateMessage( n, "[BET LOST]  " + challengerName + " defeated " + challengedName + ".  You lost $" + bet + ".  Better luck next time." );
                    } else {
                        if ( !challengerWon ) {
                            bet = bet + Math.round(totalC * ((float)bet / totalA));
                            p.addMoney( bet );
                            m_ba.sendSmartPrivateMessage( n, "[BET WON]  " + challengedName + " defeated " + challengerName + ".  You win $" + bet + "!" );
                        } else {
                            Integer diff = (Math.round(totalC * ((float)bet / totalA)));
                            p.addMoney( bet - diff );
                            m_ba.sendSmartPrivateMessage( n, "[BET LOST]  " + challengerName + " defeated " + challengedName + ".  You lost $" + diff + ".  Better luck next time." );
                        }
                    }
                    /* old way
                    if( !challengerWon ) {
                        bet = Math.round( ((float)bet * betWinMultiplier) );
                        p.addMoney( bet );
                        m_ba.sendSmartPrivateMessage( n, "[BET WON]  " + challengedName + " defeated " + challengerName + ".  You win $" + bet + "!" );
                    } else {
                        m_ba.sendSmartPrivateMessage( n, "[BET LOST]  " + challengerName + " defeated " + challengedName + ".  You lost $" + bet + ".  Better luck next time." );                    
                    }
                    */
                }
            }
        }
        
        challengerBets.clear();
        challengedBets.clear();
        totalC = 0;
        totalA = 0;
        
    }

    
    public boolean betOnDueler( BotAction m_ba, String name, PubPlayer bettor, boolean bettingChallenger, int amount ) {
        if( duelEnded || (System.currentTimeMillis() - startAt) > betTimeWindow )
            return false;
        
        if( name.equals( challengerName ) || name.equals( challengedName ) )
            return false;
        
        if( bettingChallenger ) {
            if( challengedBets.containsKey( name ) )
                return false;
            
            if( challengerBets.containsKey( name ) ) {
                bettor.addMoney(challengerBets.get(name));
                totalC -= challengerBets.get(name);
                m_ba.sendSmartPrivateMessage(name, "[NOTE]  Your previous bet of $" + challengerBets.get(name) + " has been returned to you.");
            }
            
            bettor.removeMoney(amount);
            challengerBets.put( name, amount );
            totalC += amount;
            return true;
        } else {
            if( challengerBets.containsKey( name ) )
                return false;
            
            if( challengedBets.containsKey( name ) ) {
                bettor.addMoney(challengedBets.get(name));
                totalC -= challengedBets.get(name);
                m_ba.sendSmartPrivateMessage(name, "[NOTE]  Your previous bet of $" + challengedBets.get(name) + " has been returned to you.");
            }
            
            bettor.removeMoney(amount);
            challengedBets.put( name, amount );
            totalA += amount;
            return true;
        }
    }
    
}
