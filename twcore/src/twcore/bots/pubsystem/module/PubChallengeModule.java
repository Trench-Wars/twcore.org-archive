package twcore.bots.pubsystem.module;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.TimerTask;
import java.util.Map.Entry;

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
    
    private boolean saveDuel = false;
    private String database = "";
    
    private boolean announceNew = false;
    private boolean announceWinner = false;
    private int announceWinnerAt = 0;
    private int announceZoneWinnerAt = 100000000;
    private int deaths;
    
        
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
        String name = p.getPlayerName();
        
        Dueler dueler = duelers.get(name);
        if (dueler == null)
        	return;
        
        Challenge challenge = dueler.challenge;
        if (challenge != null && challenge.isStarted()) {
	        laggers.put(name, new StartLagout(name));
	        m_botAction.scheduleTask(laggers.get(name), 60*1000);
	        m_botAction.sendPrivateMessage(challenge.getOppositeDueler(name).name, "Your opponent has lagged out. He has 60 seconds to return to the game.");
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
	            m_botAction.sendPrivateMessage(name, "You have lagged out. You have 60 seconds to return to the game. Use !lagout to return. You have "+(2-duelers.get(name).lagouts)+" lagouts left.");
	            m_botAction.sendPrivateMessage(challenge.getOppositeDueler(name).name, "Your opponent has lagged out. He has 60 seconds to return to the game.");
	            return;
	        }
	        if(event.getShipType() != challenge.ship) {
	            m_botAction.setShip(name, challenge.ship);
	            m_botAction.sendPrivateMessage(name, "Ship change during duel is not permitted.");
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
        		&& System.currentTimeMillis()-dueler.backFromLagout > 1 * Tools.TimeInMillis.SECOND
        		&& System.currentTimeMillis()-challenge.startAt > 7 * Tools.TimeInMillis.SECOND
        		&& System.currentTimeMillis()-dueler.lastDeath > 7 * Tools.TimeInMillis.SECOND) 
            {
	            dueler.warps++;
	            
	            if (MAX_WARP-dueler.warps==1) {
	            	m_botAction.sendPrivateMessage(name, "You cannot warp during a duel. If you do it one more time, you lose.");
	            }
	            else if (MAX_WARP == dueler.warps) {
	            	m_botAction.sendPrivateMessage(name, "Maximum of warp reached during a duel.");
	            	m_botAction.sendPrivateMessage(challenge.getOppositeDueler(dueler).name, "Your opponent has warped too many times, you are the winner.");
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
                    m_botAction.sendPrivateMessage(name, "You have returned from lagout.");
                } 
                return;
            }
            m_botAction.warpTo(name, area.warp2x, area.warp2y);
            if(laggers.containsKey(name)){
                m_botAction.cancelTask(laggers.get(name));
                laggers.remove(name);
                m_botAction.sendPrivateMessage(name, "You have returned from lagout.");
            }
        }
            
    }
    
    public void handleEvent(PlayerDeath event) 
    {
        if(!enabled)
            return;
        
        String killer = m_botAction.getPlayerName(event.getKillerID());
        String killee = m_botAction.getPlayerName(event.getKilleeID());
        
        if(!duelers.containsKey(killer) || !duelers.containsKey(killee))
            return;

        Dueler w = duelers.get(killer);
        Dueler l = duelers.get(killee);
        
        if(w == null || l == null)
        	return;
        
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
        	m_botAction.sendPrivateMessage(w.name, "Spawning is illegal, no count.");
        	m_botAction.sendPrivateMessage(l.name, "No count.");
        	l.lastDeath = System.currentTimeMillis();
        	return;
        }
        
        w.kills++;
        l.deaths++;
        l.lastDeath = System.currentTimeMillis();
        
        if(l.deaths == deaths) {
        	challenge.setWinner(duelers.get(killer));
            announceWinner(challenge);
            return;
        }
        
        m_botAction.shipReset(killer);
        
        
        m_botAction.sendPrivateMessage(killer, w.kills+"-"+l.kills);
        m_botAction.sendPrivateMessage(killee, l.kills+"-"+w.kills);
	      	
        m_botAction.scheduleTask(new EnergyDepletedTask(killer), 1*1000);
        m_botAction.scheduleTask(new EnergyDepletedTask(killer), 2*1000);
        m_botAction.scheduleTask(new EnergyDepletedTask(killer), 3*1000);
        m_botAction.scheduleTask(new EnergyDepletedTask(killer), 4*1000);
        
        m_botAction.scheduleTask(new SpawnBack(killer), 5*1000);
        m_botAction.scheduleTask(new SpawnBack(killee), 5*1000);
                
    }
    
    public DuelArea getEmptyDuelArea() {
        Iterator<Entry<Integer,DuelArea>> iter = areas.entrySet().iterator();
        while(iter.hasNext()){
            Entry<Integer,DuelArea> entry = iter.next();
            DuelArea area = entry.getValue();
            if(!area.inUse){
                return area;
            }
        }
        return null;
    }
    
    public void issueChallenge(String challenger, String challenged, int amount, int ship) {
    	
        Player playerChallenged = m_botAction.getFuzzyPlayer(challenged);
        if(playerChallenged==null){
            m_botAction.sendPrivateMessage(challenger, "No such player in the arena.");
            return;
        }
        challenged = playerChallenged.getPlayerName();
    	
        if(isDueling(challenger)) {
            m_botAction.sendPrivateMessage(challenger, "You are already dueling.");
            return;
        }
        
        if(isDueling(challenged)) {
            m_botAction.sendPrivateMessage(challenger, "This player is already dueling.");
            return;
        }
        
        PubPlayer pubChallenger = context.getPlayerManager().getPlayer(challenger);
        PubPlayer pubChallenged = context.getPlayerManager().getPlayer(challenged);
        
        if (pubChallenger == null) {
        	m_botAction.sendPrivateMessage(challenger, "Please wait, you are not in the system yet.");
            return;
        }
        
        if (pubChallenged == null) {
        	m_botAction.sendPrivateMessage(challenger, "Please wait, " + challenged + " is not in the system yet.");
            return;
        }

        if(isChallengeAlreadySent(challenger, challenged)) {
            m_botAction.sendPrivateMessage(challenger, "You have already a pending challenge with "+challenged+".");
            m_botAction.sendPrivateMessage(challenger, "Please remove it using !removechallenge before challenging more.");
            return;
        }
                
        if(ship == Tools.Ship.TERRIER) {
            m_botAction.sendPrivateMessage(challenger, "You cannot duel someone in Terrier.");
            return;
        }

        if(context.getPlayerManager().isShipRestricted(ship)) {
            m_botAction.sendPrivateMessage(challenger, "This ship is restricted in this arena, you cannot duel a player in this ship.");
            return;
        }
        
        if (getEmptyDuelArea()==null) {
        	m_botAction.sendPrivateMessage(challenger, "There is no duel area avalaible. Please try later.");
        	return;
        }
        
        if (context.getMoneySystem().isEnabled()) {
	        if(amount < 100) {
	            m_botAction.sendPrivateMessage(challenger, "You must challenge someone for $100 or more.");
	            return;
	        }
        }
   
        if(challenger.equalsIgnoreCase(challenged)){
            m_botAction.sendPrivateMessage(challenger, "I pity the fool who challenges himself for a duel.");
            return;
        }
                
        if (context.getMoneySystem().isEnabled()) {
	        if(pubChallenger.getMoney() < amount){
	            m_botAction.sendPrivateMessage(challenger, "You don't have enough money.");
	            return;
	        }
	        if(pubChallenged.getMoney() < amount){
	            m_botAction.sendPrivateMessage(challenger, challenged + " does not have enough money.");
	            return;
	        }
        }

        if (context.getMoneySystem().isEnabled()) {
	        m_botAction.sendPrivateMessage(challenged, challenger +" has challenged you to duel for amount of $"+amount+" in " + Tools.shipName(ship) + ". To accept reply !accept "+challenger);
	        m_botAction.sendPrivateMessage(challenged, "Duel to " + deaths + ".");
	        m_botAction.sendPrivateMessage(challenger, "Challenge sent to "+challenged+" for $"+amount+".");
        } else {
	        m_botAction.sendPrivateMessage(challenged, challenger +" has challenged you to duel in " + Tools.shipName(ship) + ". To accept reply !accept "+challenger);
	        m_botAction.sendPrivateMessage(challenged, "Duel to " + deaths + ".");
	        m_botAction.sendPrivateMessage(challenger, "Challenge sent to "+challenged+".");
        }
        
        final Challenge challenge = new Challenge(amount,ship,challenger,challenged);
        addChallenge(challenge);
        
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
            m_botAction.sendPrivateMessage(accepter, "You are already dueling.");
            return;
        }
        
        // Get the real player name
        Player player = m_botAction.getFuzzyPlayer(challenger);
    	if (player == null) {
            m_botAction.sendPrivateMessage(accepter, "Player not found.");
            return;
    	}
    	challenger = player.getPlayerName();
    
    	Challenge challenge = challenges.get(challenger+"-"+accepter);
        if (challenge == null) {
        	m_botAction.sendPrivateMessage(accepter, "You dont have a challenge from "+challenger+".");
            return;
        }
                
        int amount = challenge.amount;
        int ship = challenge.ship;
                
        if(context.getMoneySystem().isEnabled()) {
        	if (context.getPlayerManager().getPlayer(accepter).getMoney() < amount) {
	            m_botAction.sendPrivateMessage(accepter, "You don't have enough money to accept the challenge. Challenge removed.");
	            m_botAction.sendPrivateMessage(challenger, accepter+" does not have enough money to accept the challenge. Challenge removed.");
	            return;
        	}
        	if (context.getPlayerManager().getPlayer(challenger).getMoney() < amount) {
	            m_botAction.sendPrivateMessage(accepter, challenger + " does not have the money.");
	            return;
        	}
        }
        
        DuelArea area = getEmptyDuelArea();
        if(area == null){
            m_botAction.sendPrivateMessage(accepter, "Unfortunately there is no available duel area. Try again later.");
            m_botAction.sendPrivateMessage(challenger, accepter+"has accepted your challenge. Unfortunately there is no available duel area. Try again later.");
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
        	m_botAction.sendPrivateMessage(challenger, accepter+" has accepted your challenge. You have 10 seconds to get into your dueling ship.");
        	m_botAction.sendPrivateMessage(challenger, "Duel to " + deaths + ".");
        	m_botAction.sendPrivateMessage(accepter, "Challenge accepted. You have 10 seconds to get into your dueling ship.");
        } else {
        	m_botAction.sendPrivateMessage(challenger, accepter+" has accepted your challenge. The duel will start in 10 seconds.");
        	m_botAction.sendPrivateMessage(accepter, "Challenge accepted. The duel will start in 10 seconds.");
        }
    	
        Player playerChallenger = m_botAction.getPlayer(challenger);
        Player playerAccepter = m_botAction.getPlayer(accepter);
        
        duelerAccepter.oldFreq = playerAccepter.isPlaying() ? playerAccepter.getFrequency() : 0;
        duelerChallenger.oldFreq = playerChallenger.isPlaying() ? playerChallenger.getFrequency() : 0;
        
        if(playerChallenger.getShipType() == 0)
        {
            m_botAction.setShip(challenger, 1);
            m_botAction.sendPrivateMessage(challenger, "You have been set to default ship cause you were in spec.");
        }
        if(playerAccepter.getShipType() == 0)
        {
            m_botAction.setShip(accepter, 1);
            m_botAction.sendPrivateMessage(accepter, "You have been set to default ship cause you were in spec.");
        }
        
        String moneyMessage = "";
        if (context.getMoneySystem().isEnabled()) {
        	moneyMessage = " for $"+ amount;
        }
        
        if (announceNew && amount >= announceWinnerAt) {
        	if (amount >= announceZoneWinnerAt)
        		m_botAction.sendZoneMessage("[PUB] A duel is starting between " + challenger + " and " + accepter + " in " + Tools.shipName(ship) + moneyMessage + ".", Tools.Sound.BEEP1);
        	else
        		m_botAction.sendArenaMessage("A duel is starting between " + challenger + " and " + accepter + " in " + Tools.shipName(ship) + moneyMessage + ".", Tools.Sound.BEEP1);
        		
        }
        
        removePendingChallenge(challenger, false);
        removePendingChallenge(accepter, false);
        
        // Prepare the timer, in 10 seconds the game should starts
        m_botAction.scheduleTask(new StartDuel(challenge), 10*1000);
        
    }
    
    public void watchDuel(final String sender, String command) {
    	
    	if (command.contains(" ")) {
    		
    		String playerName = command.substring(command.indexOf(" ")+1).trim();
    		Player player = m_botAction.getFuzzyPlayer(playerName);
    		if (player == null) {
    			m_botAction.sendPrivateMessage(sender, "Player not found.");
    			return;
    		}
    		playerName = player.getPlayerName();
    		
    		Dueler dueler = duelers.get(playerName);
    		if (dueler == null) {
    			
    			m_botAction.sendPrivateMessage(sender, playerName + " is not dueling.");
    			
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
    				m_botAction.sendPrivateMessage(sender, "Current stat: " + dueler.challenge.accepter.kills + "-" + dueler.challenge.challenger.kills);
    			} else if (dueler.challenge.accepter.kills < dueler.challenge.challenger.kills) {
    				m_botAction.sendPrivateMessage(sender, "Current stat: " + dueler.challenge.challenger.kills + "-" + dueler.challenge.accepter.kills + ", " + dueler.challenge.challenger.name + " leading.");
    			} else {
    				m_botAction.sendPrivateMessage(sender, "Current stat: " + dueler.challenge.accepter.kills + "-" + dueler.challenge.challenger.kills + ", " + dueler.challenge.accepter.name + " leading.");
    			}

    		}
    	}
    	
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
        				m_botAction.sendPrivateMessage(name, "Challenge against " + c.challengedName + " removed.");
        			it.remove();
        			totalRemoved++;
        		}
        	}
        }
        
        if (totalRemoved == 0) {
        	if (tellPlayer)
        		m_botAction.sendPrivateMessage(name, "No pending challenge to remove.");
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
    	if (!challenge.hasEnded()) {
    		throw new RuntimeException("You need to set a winner or a loser! setWinner()/setLoser()");
    	}
    	
    	Dueler winner = challenge.winner;
    	Dueler loser = challenge.loser;
    	
    	int winnerKills = winner.kills;
    	int loserKills = loser.kills;
    	
    	int money = challenge.amount;
    	
    	String moneyMessage = "";
    	if (context.getMoneySystem().isEnabled()) {
    		moneyMessage = " for $"+money;
    	}

        if(laggers.containsKey(winner) && laggers.containsKey(loser)) 
        {
            m_botAction.cancelTask(laggers.get(winner.name));
        }
        
        else if(challenge.winByLagout)
        {
        	if (announceWinner && challenge.amount >= announceWinnerAt)
        		m_botAction.sendArenaMessage("[DUEL] " + winner.name + " has defeated "+loser.name+" by lagout in duel" + moneyMessage + ".");
        	else {
        		m_botAction.sendPrivateMessage(winner.name,"You have defeated "+loser.name+" by lagout in duel" + moneyMessage + ".");
        		m_botAction.sendPrivateMessage(loser.name,"You have lost to " + winner.name+" by lagout in duel" + moneyMessage + ".");
            }

            laggers.remove(loser.name);
        }
        
        else
        {
        	if (announceWinner && money >= announceWinnerAt)
        		m_botAction.sendArenaMessage("[DUEL] " + winner.name+" has defeated "+loser.name+" "+winnerKills+"-"+loserKills+" in duel" + moneyMessage + ".");
        	else {
        		m_botAction.sendPrivateMessage(winner.name,"You have defeated "+loser.name+" "+winnerKills+"-"+loserKills+" in duel" + moneyMessage + ".");
        		m_botAction.sendPrivateMessage(loser.name,"You have lost to " + winner.name+" "+loserKills+"-"+winnerKills+" in duel" + moneyMessage + ".");
            }
        }
        
        // Free the area
        challenge.area.free();
        
        // Give/Remove money
        if (context.getMoneySystem().isEnabled()) {
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
        
        if (saveDuel) {
        
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
				m_botAction.sendPrivateMessage(challenge.challengerName, "Challenge against " + challenge.challengedName + " removed. (timeout)");
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
	                m_botAction.specificPrize(name, Tools.Prize.FULLCHARGE);
	                m_botAction.shipReset(name);
	            }
	            else if(dueler.type == 2){
	                m_botAction.warpTo(name, challenge.area.warp2x, challenge.area.warp2y);
	                m_botAction.specificPrize(name, Tools.Prize.FULLCHARGE);
	                m_botAction.shipReset(name);
	            }
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
                if (p_chall == null) 
                	m_botAction.sendPrivateMessage(accepter, "The duel cannot start, " + challenger + " not found.");
                else
                	m_botAction.sendPrivateMessage(challenger, "The duel cannot start, " + accepter + " not found.");
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
            m_botAction.sendPrivateMessage(challenger, "GO GO GO!", Tools.Sound.GOGOGO);
            m_botAction.sendPrivateMessage(accepter, "GO GO GO!", Tools.Sound.GOGOGO);

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
    
    public boolean isDueling(String name) {
        return duelers.containsKey(name);
    }

    public void returnFromLagout(String name){
    	
        if(!laggers.containsKey(name)){
            m_botAction.sendPrivateMessage(name, "You have not lagged out from a duel.");
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
    		
    		m_botAction.sendPrivateMessage(sender, "Challenge ["+status+"] (" + k1 + ":" + k2 + ")");
    		count++;
    	}
    	for(Dueler dueler: duelers.values()) {
    		String status = dueler.challenge.isStarted() ? "STARTED" : "PENDING";
    		m_botAction.sendPrivateMessage(sender, "Dueler ["+status+"] " + dueler.name);
    		count++;
    	}
    	if (count==0)
    		m_botAction.sendPrivateMessage(sender, "Nothing.");
    	
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
        	m_botAction.sendPrivateMessage(name, "Your duel has been cancelled by " + sender);
        	m_botAction.sendPrivateMessage(opponent, "Your duel has been cancelled by " + sender);
    	}
    	else {
    		m_botAction.sendPrivateMessage(sender, "No duel found associated with this player.");
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
	            	m_botAction.sendPrivateMessage(sender, "Player not on the system yet.");
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
                    	 m_botAction.sendPrivateMessage(sender, "If you specify a ship, it must be a number between 1 and 8.");
                    }
                }catch(NumberFormatException e){
                	if (context.getMoneySystem().isEnabled())
                		m_botAction.sendPrivateMessage(sender, "Proper use is !challenge name:ship:amount");
                	else
                		m_botAction.sendPrivateMessage(sender, "Proper use is !challenge name:ship");
                }
            }
            else {
            	if (context.getMoneySystem().isEnabled())
            		m_botAction.sendPrivateMessage(sender, "Proper use is !challenge name:ship:amount");
            	else
            		m_botAction.sendPrivateMessage(sender, "Proper use is !challenge name:ship");
            }
        }
        if(command.startsWith("!accept "))
            if(command.length() > 8)
                acceptChallenge(sender, command.substring(8));
        if(command.startsWith("!watchduel") || command.startsWith("!wd"))
            watchDuel(sender, command);
        if(command.startsWith("!removechallenge") || command.equalsIgnoreCase("!rm"))
            removePendingChallenge(sender, true);
        if(command.equalsIgnoreCase("!lagout"))
            returnFromLagout(sender);
	}
	
	@Override
	public void handleModCommand(String sender, String command) {

        try {
        	
        	if(command.startsWith("!cancelchallenge"))
            	doCancelDuelCmd(sender, command.substring(17).trim());
        	else if(command.startsWith("!debugchallenge"))
            	doDebugCmd(sender);
           
        } catch(RuntimeException e) {
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
	}
	
	@Override
	public String[] getHelpMessage() {
		if (context.getMoneySystem().isEnabled())
			return new String[] {
				pubsystem.getHelpLine("!challenge <name>:<ship>:<$>  -- Challenge a player to " + deaths + " in a specific ship (1-8) for $X. (!duel)"),
				//pubsystem.getHelpLine("!watchduel <name>             -- Watch the duel of this player. (!wd)"),
				pubsystem.getHelpLine("!removechallenges             -- Cancel your challenges sent."),
	        };
		else
			return new String[] {
				pubsystem.getHelpLine("!challenge <name>:<ship>      -- Challenge a player to " + deaths + " in a specific ship (1-8)."),
				//pubsystem.getHelpLine("!watchduel <name>             -- Watch the duel of this player. (!wd)"),
				pubsystem.getHelpLine("!removechallenges             -- Cancel your challenges sent. (!rm)"),
	        };
	}

	@Override
	public String[] getModHelpMessage() {
		return new String[] {
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
	
	        // Setting Duel Areas
	        for(int i=1; i<m_botAction.getBotSettings().getInt("duel_area")+1; i++) {
	        	int pos1[] = m_botAction.getBotSettings().getIntArray("duel_area"+i+"_pos1", " ");
	        	int pos2[] = m_botAction.getBotSettings().getIntArray("duel_area"+i+"_pos2", " ");
	        	areas.put(i, new DuelArea(i, pos1[0],pos1[1],pos2[0],pos2[1]));
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
		// TODO Auto-generated method stub
		
	}
    
}
class DuelArea {
	
	public int number; // Area number
	
	public boolean inUse = false;
	 
    public int warp1y,warp1x,warp2y,warp2x;
    
    public DuelArea(int number, int warp1x, int warp1y, int warp2x, int warp2y) {
    	this.number = number;
        this.warp1x = warp1x;
        this.warp1y = warp1y;
        this.warp2x = warp2x;
        this.warp2y = warp2y;
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
    
    public long lastDeath = 0; // To detect warp vs death
    public long backFromLagout = 0; // To detect warp vs lagout
    
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
    
    public Challenge(int amount, int ship, String challenger, String challenged){
        this.amount = amount;
        this.ship = ship;
        this.challengerName = challenger;
        this.challengedName = challenged;
    }
    
    public Dueler getOppositeDueler(Dueler dueler) {
    	return getOppositeDueler(dueler.name);
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
    
}
