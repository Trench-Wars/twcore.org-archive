package twcore.bots.pubsystem.module;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
        
        Challenge challenge = challenges.get(name);
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
        if(!duelers.containsKey(name))
            return;
        
        Challenge challenge = challenges.get(name);
        
        if (challenge.isStarted()) {
        
	        if(event.getShipType() == 0)
	        {
	        	Dueler dueler = duelers.get(name);
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
        
        Challenge challenge = challenges.get(name);
        if (challenge.hasEnded()) {
        	challenges.remove(name);
        	duelers.remove(name);
        	warpToSafe(name, false);
        	return;
        }
        
        if(y > 225 && y < 700 && x > 225 && x < 700 && challenge.isStarted())
        {
            DuelArea area = challenge.area;

            if (System.currentTimeMillis()-dueler.lastDeath > 6000 
            		&& System.currentTimeMillis()-dueler.backFromLagout > 1000
            		&& !laggers.containsKey(name)
            		&& System.currentTimeMillis()-dueler.lastDeath > 6 * Tools.TimeInMillis.SECOND) {
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
        
        Challenge challenge = challenges.get(killer);
        if (challenge == null 
        		|| !challenge.getOppositeDueler(w).name.equals(l.name)
        		|| !challenge.isStarted())
        	return;
    
        if (System.currentTimeMillis()-l.lastDeath < 6 * Tools.TimeInMillis.SECOND) {
        	m_botAction.sendPrivateMessage(w.name, "Spawning is illegal, no count.");
        	m_botAction.sendPrivateMessage(l.name, "No count.");
        	return;
        }
        
        w.kills++;
        l.deaths++;
        l.lastDeath = System.currentTimeMillis();
        l.updateDeath();
        
        if(l.deaths == deaths) {
        	challenge.setWinner(duelers.get(killer));
            announceWinner(challenge);
            return;
        }
        
        m_botAction.sendPrivateMessage(killer, w.kills+"-"+l.kills);
        m_botAction.sendPrivateMessage(killee, l.kills+"-"+w.kills);
	      	
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
    	
        if(challenges.containsKey(challenger))
        {
            Challenge pending = challenges.get(challenger);
            m_botAction.sendPrivateMessage(challenger, "You have already a pending challenge with "+pending.getOppositeDueler(challenged)+".");
            m_botAction.sendPrivateMessage(challenger, "Please remove it using !removechallenge before challenging more.");
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
        
        Player playerChallenged = m_botAction.getPlayer(challenged);
        if(playerChallenged==null){
            m_botAction.sendPrivateMessage(challenger, "No such player in the arena.");
            return;
        }
            
        if(challenger.equalsIgnoreCase(challenged)){
            m_botAction.sendPrivateMessage(challenger, "I pity the fool who challenges himself for a duel.");
            return;
        }
        
        if (context.getMoneySystem().isEnabled()) {
	        if(context.getPlayerManager().getPlayer(challenger).getMoney() < amount){
	            m_botAction.sendPrivateMessage(challenger, "You don't have enough money.");
	            return;
	        }
        }
        if (context.getMoneySystem().isEnabled()) {
	        m_botAction.sendPrivateMessage(challenged, challenger +" has challenged you to duel for amount of $"+amount+" in " + Tools.shipName(ship) + ". To accept reply !accept "+challenger);
	        m_botAction.sendPrivateMessage(challenged, "Duel to " + deaths + ".");
	        challenges.put(challenger, new Challenge(amount,ship));
	        m_botAction.sendPrivateMessage(challenger, "Challenge sent to "+challenged+" for $"+amount+".");
        } else {
	        m_botAction.sendPrivateMessage(challenged, challenger +" has challenged you to duel in " + Tools.shipName(ship) + ". To accept reply !accept "+challenger);
	        m_botAction.sendPrivateMessage(challenged, "Duel to " + deaths + ".");
	        challenges.put(challenger, new Challenge(amount,ship));
	        m_botAction.sendPrivateMessage(challenger, "Challenge sent to "+challenged+".");
        }
        
    }
    
    public void acceptChallenge(String accepter, String challenger) {
    	
    	// Already playing?
        if(duelers.containsKey(accepter)) {
            m_botAction.sendPrivateMessage(accepter, "You are already dueling.");
            return;
        }
        
        // Get the real player name
        Player player = m_botAction.getFuzzyPlayer(challenger);
    	if (player != null)
    		challenger = player.getPlayerName();
    	else {
            m_botAction.sendPrivateMessage(accepter, "Player not found.");
            return;
    	}
        
    	Challenge challenge = challenges.get(challenger);
        if (challenge == null) {
        	m_botAction.sendPrivateMessage(accepter, "You dont have a challenge from "+challenger+".");
            return;
        }
                
        int amount = challenge.amount;
        int ship = challenge.ship;
                
        if(context.getMoneySystem().isEnabled() && context.getPlayerManager().getPlayer(accepter).getMoney() < amount){
            m_botAction.sendPrivateMessage(accepter, "You don't have enough money to accept the challenge. Challenge removed.");
            m_botAction.sendPrivateMessage(challenger, accepter+" doesn't have enough money to accept the challenge. Challenge removed.");
            challenges.remove(challenger);
            return;
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
        Dueler duelerChallenger = new Dueler(challenger, Dueler.DUEL_CHALLENGER);
        Dueler duelerAccepter = new Dueler(accepter, Dueler.DUEL_ACCEPTER);
        duelers.put(challenger, duelerChallenger);
        duelers.put(accepter, duelerAccepter);
        
        // The challenge was previously added for the challenger in issueChallenge()
        // Now we need to link the accepter with this challenge
        challenges.put(accepter, challenge);
        
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
        	if (ship == 0)
        		m_botAction.sendArenaMessage("A duel is starting between " + challenger + "("+playerChallenger.getShipType()+") and " + accepter + "("+playerAccepter.getShipType()+")" + moneyMessage + ".", Tools.Sound.BEEP1);
        	else
        		m_botAction.sendArenaMessage("A duel is starting between " + challenger + " and " + accepter + " in " + Tools.shipName(ship) + moneyMessage + ".", Tools.Sound.BEEP1);
        }
        
        // Prepare the timer, in 10 seconds the game should starts
        m_botAction.scheduleTask(new StartDuel(challenge), 10*1000);
        
    }
    
    public void removeChallenge(String name)
    {
        if(!challenges.containsKey(name)){
            m_botAction.sendPrivateMessage(name, "You don't have a pending challenge to remove.");
            return;
        }

        Challenge challenge = challenges.get(name);
        if (challenge == null) {
        	m_botAction.sendPrivateMessage(name, "You don't have a pending challenge to remove.");
        } else if (challenge.isStarted()) {
        	m_botAction.sendPrivateMessage(name, "You cannot remove a challenge already started.");
        } else {
            challenges.remove(name);
            m_botAction.sendPrivateMessage(name, "Challenge removed.");
        }

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
        		m_botAction.sendArenaMessage("[PUB DUEL] " + winner.name + " has defeated "+loser.name+" by lagout in duel" + moneyMessage + ".");
        	else {
        		m_botAction.sendPrivateMessage(winner.name,"You have defeated "+loser.name+" by lagout in duel" + moneyMessage + ".");
        		m_botAction.sendPrivateMessage(loser.name,"You have lost to " + winner.name+" by lagout in duel" + moneyMessage + ".");
            }

            laggers.remove(loser.name);
        }
        
        else
        {
        	if (announceWinner && money >= announceZoneWinnerAt) {
        		m_botAction.sendZoneMessage("[PUB DUEL] " + winner.name+" has defeated "+loser.name+" "+winnerKills+"-"+loserKills+" in duel" + moneyMessage + ".", Tools.Sound.CROWD_OOO);
        	} else if (announceWinner && money >= announceWinnerAt)
        		m_botAction.sendArenaMessage("[PUB DUEL] " + winner.name+" has defeated "+loser.name+" "+loserKills+"-"+winnerKills+" in duel" + moneyMessage + ".");
        	else {
        		m_botAction.sendPrivateMessage(winner.name,"You have defeated "+loser.name+" "+winnerKills+"-"+loserKills+" in duel" + moneyMessage + ".");
        		m_botAction.sendPrivateMessage(loser.name,"You have lost to " + winner.name+" "+loserKills+"-"+winnerKills+" in duel" + moneyMessage + ".");
            }
        }
        
        // Free the area
        challenge.area.free();
        
        // Give/Remove money
        if (context.getMoneySystem().isEnabled()) {
	        if (context.getPlayerManager().getPlayer(winner.name) != null)
	        	context.getPlayerManager().getPlayer(winner.name).addMoney(money);
	        if (context.getPlayerManager().getPlayer(loser.name) != null)
	        	context.getPlayerManager().getPlayer(loser.name).removeMoney(money);
        }
        
        Dueler d1 = duelers.remove(winner.name);
        Dueler d2 = duelers.get(loser.name);
        challenges.remove(d1.name);
        if (laggers.containsKey(d2.name)) {
        	challenges.remove(d2.name);
        }
        laggers.remove(d1.name);
        laggers.remove(d2.name);
        
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
	        		Tools.addSlashes(d1.type==Dueler.DUEL_CHALLENGER?d1.name:d2.name),
	        		Tools.addSlashes(d2.type==Dueler.DUEL_ACCEPTER?d2.name:d1.name),
	        		Tools.addSlashes(winner.name),
	        		String.valueOf(d1.type==Dueler.DUEL_CHALLENGER?d1.kills:d2.kills),
	        		String.valueOf(d2.type==Dueler.DUEL_ACCEPTER?d2.kills:d1.kills),
	        		String.valueOf(challenge.ship),
	        		String.valueOf(challenge.winByLagout?1:0),
	        		String.valueOf((int)((System.currentTimeMillis()-challenge.startAt)/1000)),
	        		String.valueOf(money),
	        		new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
	        };
	        
	    	m_botAction.SQLBackgroundInsertInto(database, "tblDuel", fields, values);
        }
    	        
    }
    
    private class SpawnBack extends TimerTask{
        String name;
        private SpawnBack(String name){
            this.name = name;
        }

        @Override
        public void run() {
        	
        	Challenge challenge = challenges.get(name);
        	
        	if (duelers.get(name) != null) {
	            if(duelers.get(name).type == 1){
	                m_botAction.warpTo(name, challenge.area.warp1x, challenge.area.warp1y);
	                m_botAction.shipReset(name);
	            }
	            else if(duelers.get(name).type == 2){
	                m_botAction.warpTo(name, challenge.area.warp2x, challenge.area.warp2y);
	                m_botAction.shipReset(name);
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
        	Challenge challenge = challenges.get(name);
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
        Challenge challenge = challenges.get(name);
        
        Dueler dueler = duelers.get(name);
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
    
    public void doCancelDuelCmd( String sender, String onePlayer ) {

    	String name = getRealName(onePlayer);

    	Challenge challenge = challenges.get(name);
    	
    	if (challenge!=null) {
    		String opponent = challenge.getOppositeDueler(name).name;
    		challenge.area.free();
    		challenges.remove(name);
    		challenges.remove(opponent);
        	duelers.remove(name);
        	duelers.remove(opponent);
        	laggers.remove(name);
        	laggers.remove(opponent);
        	warpToSafe(name, true);
        	warpToSafe(opponent, true);
        	m_botAction.sendPrivateMessage(name, "Your duel has been cancelled by " + sender);
        	m_botAction.sendPrivateMessage(opponent, "Your duel has been cancelled by " + sender);
    	}
    	else {
    		m_botAction.sendPrivateMessage(sender, "No duel found associated with this player.");
    	}

    	
    }
    
	@Override
	public void handleCommand(String sender, String command) {

        if(command.startsWith("!challenge ")){
            String pieces[] = command.substring(11).split(":");
            String opponent = "";
            // Get the real player name
            if (pieces.length == 3 || (pieces.length == 2 && !context.getMoneySystem().isEnabled())) {
	            PubPlayer player = context.getPlayerManager().getPlayer(pieces[0]);
	            if (player==null) {
	            	m_botAction.sendPrivateMessage(sender, "Player not found.");
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
        if(command.equalsIgnoreCase("!removechallenge"))
            removeChallenge(sender);
        if(command.equalsIgnoreCase("!lagout"))
            returnFromLagout(sender);
	}
	
	@Override
	public void handleModCommand(String sender, String command) {

        try {
        	
            if(command.startsWith("!cancelchallenge"))
            	doCancelDuelCmd(sender, command.substring(17).trim());
           
        } catch(RuntimeException e) {
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
	}
	
	@Override
	public String[] getHelpMessage() {
		if (context.getMoneySystem().isEnabled())
			return new String[] {
				pubsystem.getHelpLine("!challenge <name>:<ship>:<$>  -- Challenge a player to " + deaths + " in a specific ship (1-8) for $X."),
				pubsystem.getHelpLine("!removechallenge              -- Cancel a challenge sent to someone."),
	        };
		else
			return new String[] {
				pubsystem.getHelpLine("!challenge <name>:<ship>      -- Challenge a player to " + deaths + " in a specific ship (1-8)."),
				pubsystem.getHelpLine("!removechallenge              -- Cancel a challenge sent to someone."),
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
	
	// Stats during a duel
    public int lagouts = 0;
    public int deaths = 0;
    public int kills = 0;
    public int warps = 0;

    public long lastDeath = 0; // To detect warp vs death
    public long backFromLagout = 0; // To detect warp vs lagout
    
    public Dueler(String name, int type){
        this.name = name;
        this.type = type;
    }
    
    
    public void updateDeath() {
    	this.lastDeath = System.currentTimeMillis();
    }
    
    
}

class Challenge {
	
	public Dueler challenger;
	public Dueler accepter;
	
	public DuelArea area;
	
    public int amount;       // Playing for $ money
    public int ship;         // Which ship? 0 = any
    public long startAt = 0; // Started at? Epoch in millis
    
    public boolean duelEnded = false;
    public Dueler winner;
    public Dueler loser;
    public boolean winByLagout = false;
    
    public Challenge(int amount, int ship){
        this.amount = amount;
        this.ship = ship;
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
