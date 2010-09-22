package twcore.bots.pubsystem.module;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.Map.Entry;

import twcore.bots.pubsystem.PubContext;
import twcore.core.BotAction;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.game.Player;
import twcore.core.util.Tools;

// TODO: lagouts, playerLeave, playerenter

/*
 * By subby
 */
public class PubChallengeModule extends AbstractModule {

    private BotAction m_botAction;
    private PubContext context;
    
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
        this.m_botAction = m_botAction;
        this.context = context;
        this.areas = new HashMap<Integer, DuelArea>();
        this.duelers = new HashMap<String, Dueler>();
        this.challenges = new HashMap<String, Challenge>();
        this.laggers = new HashMap<String,StartLagout>();

        // Setting Duel Areas
        for(int i=1; i<m_botAction.getBotSettings().getInt("duel_area")+1; i++) {
        	int pos1[] = m_botAction.getBotSettings().getIntArray("duel_area"+i+"_pos1", " ");
        	int pos2[] = m_botAction.getBotSettings().getIntArray("duel_area"+i+"_pos2", " ");
        	areas.put(i, new DuelArea(pos1[0],pos1[1],pos2[0],pos2[1]));
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
        
        announceWinnerAt = m_botAction.getBotSettings().getInt("duel_announce_winner_at"); 
        announceZoneWinnerAt = m_botAction.getBotSettings().getInt("duel_announce_zone_winner_at");
        deaths = m_botAction.getBotSettings().getInt("duel_deaths");
    }
    
    public void handleEvent(PlayerLeft event) {
        if(!enabled)
            return;
        playerLeftRemoveChallenge(m_botAction.getPlayerName(event.getPlayerID()));
        
    }
    
    public void handleEvent(Message event) {
        if(!enabled)
            return;
        String command = event.getMessage();
        String sender = event.getMessager();
        if(sender == null)
            sender = m_botAction.getPlayerName(event.getPlayerID());
        if(command.startsWith("!challenge ")){
            String pieces[] = command.substring(11).split(":");
            if(pieces.length == 2) {
                try {
                    int amount = Integer.parseInt(pieces[1]);
                    issueChallenge(sender, pieces[0], amount, 0);
                }catch(NumberFormatException e){
                    m_botAction.sendPrivateMessage(sender, "Proper use is !challenge name:amount");
                }
            }
            else if(pieces.length == 3) {
                try {
                	int ship = Integer.parseInt(pieces[1]);
                    int amount = Integer.parseInt(pieces[2]);
                    if (ship >= 1 && ship <= 8) {
                    	issueChallenge(sender, pieces[0], amount, ship);
                    }
                    else {
                    	 m_botAction.sendPrivateMessage(sender, "If you specify a ship, it must be a number between 1 and 8.");
                    }
                }catch(NumberFormatException e){
                    m_botAction.sendPrivateMessage(sender, "Proper use is !challenge name:ship:amount");
                }
            }
            if(pieces.length != 2 && pieces.length != 3)
                m_botAction.sendPrivateMessage(sender, "Proper use is   !challenge name:amount   or   !challenge name:ship:amount");
        }
        if(command.startsWith("!accept "))
            if(command.length() > 8)
                acceptChallenge(sender, command.substring(8));
        if(command.equalsIgnoreCase("!remove"))
            removeChallenge(sender);
        if(command.equalsIgnoreCase("!lagout"))
            returnFromLagout(sender);
    }
    public void handleEvent(FrequencyShipChange event) {
        if(!enabled)
            return;
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if(!duelers.containsKey(name.toLowerCase()))
            return;
        if(event.getShipType() == 0){
            duelers.get(name).lagouts++;
            if(duelers.get(name).lagouts > 2){
                announceWinner(duelers.get(name).opponent,name,0,0,duelers.get(name).amount,true);
                return;
            }
            laggers.put(name.toLowerCase(), new StartLagout(name));
            m_botAction.scheduleTask(laggers.get(name.toLowerCase()), 60*1000);
            m_botAction.sendPrivateMessage(name, "You have lagged out. You have 60 seconds to return to the game. Use !lagout to return. You have "+(2-duelers.get(name).lagouts)+" lagouts left.");
            m_botAction.sendPrivateMessage(duelers.get(name).opponent, "Your opponent has lagged out. He has 60 seconds to return to the game.");
            return;
        }
        if(event.getShipType() != duelers.get(name).ship) {
            m_botAction.setShip(name, duelers.get(name).ship);
            m_botAction.sendPrivateMessage(name, "Shipchange during duel is not permitted.");
        }
        
    }
    
    public void handleEvent(PlayerPosition event){
        if(!enabled)
            return;
        String name = m_botAction.getPlayerName(event.getPlayerID());
        if(!duelers.containsKey(name.toLowerCase()))
            return;
        int x = event.getXLocation()/16;
        int y = event.getYLocation()/16;
        if(y > 225 & y < 700 & x > 225 & x < 700){
            DuelArea area = areas.get(duelers.get(name).area);
            if(duelers.get(name).mode == 1){
                m_botAction.warpTo(name, area.warp1x, area.warp1y);
                if(laggers.containsKey(name.toLowerCase())){
                    m_botAction.cancelTask(laggers.get(name.toLowerCase()));
                    laggers.remove(name.toLowerCase());
                    m_botAction.sendPrivateMessage(name, "You have returned from lagout.");
                }
                    
                return;
            }
            m_botAction.warpTo(name, area.warp2x, area.warp2y);
            if(laggers.containsKey(name.toLowerCase())){
                m_botAction.cancelTask(laggers.get(name.toLowerCase()));
                laggers.remove(name.toLowerCase());
                m_botAction.sendPrivateMessage(name, "You have returned from lagout.");
            }
        }
            
    }
    
    public void handleEvent(PlayerDeath event) {
        if(!enabled)
            return;
        String killer = m_botAction.getPlayerName(event.getKillerID());
        String killee = m_botAction.getPlayerName(event.getKilleeID());
        if(!duelers.containsKey(killer.toLowerCase()) & !duelers.containsKey(killee.toLowerCase()))
            return;
        Dueler w = duelers.get(killer);
        Dueler l = duelers.get(killee);
        w.kills++;
        l.deaths++;
        if(l.deaths == deaths) {
         // TODO: maybe some variable to how many deaths
            announceWinner(w.name, l.name, w.kills, l.kills, w.amount,false);
            return;
        }
        m_botAction.sendPrivateMessage(killer, w.kills+"-"+l.kills);
        m_botAction.sendPrivateMessage(killee, l.kills+"-"+w.kills);
        m_botAction.scheduleTask(new SpawnBack(killer), 5*1000);
        m_botAction.scheduleTask(new SpawnBack(killee), 5*1000);
                
    }
    
   
    // returns 0 if no areas available;
    public int getEmptyDuelArea() {
        int empty = 0;
        Iterator<Entry<Integer,DuelArea>> iter = areas.entrySet().iterator();
        while(iter.hasNext()){
            Entry<Integer,DuelArea> entry = iter.next();
            DuelArea area = entry.getValue();
            if(!area.inUse){
                empty = entry.getKey();
                return empty;
            }
        }
        return empty;
    }
    
    public void playerLeftRemoveChallenge(String name){
        if(!challenges.containsKey((name)))
            return;
        challenges.remove(name);
    }
    
    public void issueChallenge(String challenger, String challenged, int amount, int ship) {
        if(challenges.containsKey(challenger)){
            Challenge pending = challenges.get(challenger);
            m_botAction.sendPrivateMessage(challenger, "You have already a pending challenge with "+pending.challenged+". Please remove it before challengin more.");
            return;
        }
        boolean validChallenge = false;
        Iterator<Player> arena = m_botAction.getPlayerIterator();
        while(arena.hasNext()){
            Player player = arena.next();
            if(player.getPlayerName().startsWith(challenged)){
                challenged = player.getPlayerName();
                validChallenge = true;
                break;
            }
        }
        if(!validChallenge){
            m_botAction.sendPrivateMessage(challenger, "No such player in the arena.");
            return;
        }
            
        if(challenger.equals(challenged)){
            m_botAction.sendPrivateMessage(challenger, "I pity the fool who challenges himself for a duel.");
            return;
        }
        
        if(context.getPlayerManager().getPlayer(challenger).getMoney() < amount){
            m_botAction.sendPrivateMessage(challenger, "You don't have enough money.");
            return;
        }
        
        String shipMessage = "Any Ship";
        if (ship != 0) {
        	shipMessage = Tools.shipName(ship);
        }
            
        m_botAction.sendPrivateMessage(challenged, challenger +" has challenged you to duel for amount of $"+amount+" in " + shipMessage + ". To accept reply !accept "+challenger);
        challenges.put(challenger, new Challenge(challenged,amount,ship));
        m_botAction.sendPrivateMessage(challenger, "Challenge sent to "+challenged+" for $"+amount+".");
        
    }
    
    public void acceptChallenge(String accepter, String challenger) {
        if(duelers.containsKey(accepter)) {
            m_botAction.sendPrivateMessage(accepter, "You are already dueling.");
            return;
        }
        Iterator<Entry<String, Challenge>> iter = challenges.entrySet().iterator();
        while(iter.hasNext()){
            Entry<String, Challenge> e = iter.next();
            if(e.getValue().challenged.equals(accepter))
                if(e.getKey().startsWith(challenger)){
                    challenger = e.getKey();
                    break;
                }
        }
        
        try {
            if(!challenges.get(challenger).challenged.equalsIgnoreCase(accepter)){
                m_botAction.sendPrivateMessage(accepter, "You dont have a challenge from "+challenger+".");
                return;
            }
        }catch(NullPointerException e){
            m_botAction.sendPrivateMessage(accepter, "You dont have a challenge from "+challenger+".");
            return;
        }
        
        int amount = challenges.get(challenger).amount;
        int ship = challenges.get(challenger).ship;
        
        if(context.getPlayerManager().getPlayer(accepter).getMoney() < amount){
            m_botAction.sendPrivateMessage(accepter, "You don't have enough money to accept the challenge. Challenge removed.");
            m_botAction.sendPrivateMessage(challenger, accepter+" doesn't have enough money to accept the challenge. Challenge removed.");
            challenges.remove(challenger);
            return;
        }
        int area = getEmptyDuelArea();
        if(area == 0){
            m_botAction.sendPrivateMessage(accepter, "Unfortunately there is no available duel area. Try again later.");
            m_botAction.sendPrivateMessage(challenger, accepter+"has accepted your challenge. Unfortunately there is no available duel area. Try again later.");
            return;
        }
        DuelArea dArea = areas.get(area);
        dArea.inUse = true;
        if (ship==0) {
        	m_botAction.sendPrivateMessage(challenger, accepter+" has accepted your challenge. You have 10 seconds to get into your dueling ship.");
        	m_botAction.sendPrivateMessage(accepter, "Challenge accepted. You have 10 seconds to get into your dueling ship.");
        } else {
        	m_botAction.sendPrivateMessage(challenger, accepter+" has accepted your challenge. The duel will start in 10 seconds.");
        	m_botAction.sendPrivateMessage(accepter, "Challenge accepted. The duel will start in 10 seconds.");
        }
        Player p_chall = m_botAction.getPlayer(challenger);
        Player p_acc = m_botAction.getPlayer(accepter);
        if(p_chall.getShipType() == 0){
            m_botAction.setShip(p_chall.getPlayerName(), 1);
            m_botAction.sendPrivateMessage(p_chall.getPlayerName(), "You have been set to default ship cause you were in spec.");
        }
        if(p_acc.getShipType() == 0){
            m_botAction.setShip(p_acc.getPlayerName(), 1);
            m_botAction.sendPrivateMessage(p_acc.getPlayerName(), "You have been set to default ship cause you were in spec.");
        }
        m_botAction.scheduleTask(new StartDuel(challenger,accepter,ship,area), 10*1000);
            
        if (announceNew && amount >= announceWinnerAt) {
        	if (ship == 0)
        		m_botAction.sendArenaMessage("A duel is starting between " + p_chall.getPlayerName() + "("+p_chall.getShipType()+") and " + p_acc.getPlayerName() + "("+p_acc.getShipType()+") for $"+ amount + ".", Tools.Sound.BEEP1);
        	else
        		m_botAction.sendArenaMessage("A duel is starting between " + p_chall.getPlayerName() + " and " + p_acc.getPlayerName() + " in " + Tools.shipName(ship) + " for $"+ amount + ".", Tools.Sound.BEEP1);
        }
        
        // Prepare the duel right now since we know the dueling ship
        if (ship > 0) {
        	duelers.put(challenger, new Dueler(challenger,area,1,challenges.get(challenger).amount,ship,accepter));
        	duelers.put(accepter, new Dueler(accepter,area,2,challenges.get(challenger).amount,ship,challenger));
        }
        
    }
    public void removeChallenge(String name){
        if(!challenges.containsKey(name)){
            m_botAction.sendPrivateMessage(name, "You don't have a pending challenge to remove.");
            return;
        }
            
        try{
            String challengee = challenges.get(name).challenged;
            challenges.remove(name);
            m_botAction.sendPrivateMessage(name, "Challenge removed.");
            m_botAction.sendPrivateMessage(challengee, name+" has removed the challenge against you.");
        }catch(NullPointerException e){
            m_botAction.sendPrivateMessage(name, "You don't have a pending challenge to remove.");
        }
    }
    private void announceWinner(String winner, String loser, int winnerKills, int loserKills ,int amount, boolean lagout){
        warpToSafe(winner);
        String realWinner = duelers.get(winner).name;
        String realLoser = duelers.get(loser).name;

        if(laggers.containsKey(winner) & laggers.containsKey(winner)) {
        	//if (announceWinner && announceWinnerAt == 0)
        	//	m_botAction.sendArenaMessage("Duel between "+realWinner+" and "+realLoser+" has been declared as void since both lagged out.");
            duelers.remove(winner);
            duelers.remove(loser);
            m_botAction.cancelTask(laggers.get(realWinner));
            laggers.remove(realWinner);
            laggers.remove(realLoser);
            return;
        }
            
        if(lagout){
        	if (announceWinner && amount >= announceWinnerAt)
        		m_botAction.sendArenaMessage("[PUB DUEL] " + realWinner + " has beaten "+realLoser+" by lagout in duel for $"+amount+".");
        	else {
        		m_botAction.sendPrivateMessage(realWinner,"You have beaten "+realLoser+" by lagout in duel for $"+amount+".");
        		m_botAction.sendPrivateMessage(realLoser,"You have lost to " + realLoser+" by lagout in duel for $"+amount+".");
            }

            laggers.remove(realLoser);
        }
        if(!lagout){
        	if (announceWinner && amount >= announceZoneWinnerAt)
        		m_botAction.sendZoneMessage("[PUB DUEL] " + realWinner+" has beaten "+realLoser+" by "+winnerKills+"-"+loserKills+" in duel for $"+amount+".");
        	else if (announceWinner && amount >= announceWinnerAt)
        		m_botAction.sendArenaMessage("[PUB DUEL] " + realWinner+" has beaten "+realLoser+" by "+winnerKills+"-"+loserKills+" in duel for $"+amount+".");
        	else {
        		m_botAction.sendPrivateMessage(realWinner,"You have beaten "+realLoser+" by "+winnerKills+"-"+loserKills+" in duel for $"+amount+".");
        		m_botAction.sendPrivateMessage(realLoser,"You have lost to " + realLoser+" by "+winnerKills+"-"+loserKills+" in duel for $"+amount+".");
            }
        		
        	DuelArea dArea = areas.get(duelers.get(winner).area);
            dArea.inUse = false;
        }
        DuelArea dArea = areas.get(duelers.get(winner).area);
        dArea.inUse = false;
        context.getPlayerManager().getPlayer(realWinner).addMoney(amount);
        context.getPlayerManager().getPlayer(realLoser).removeMoney(amount);
        
        Dueler d1 = duelers.remove(winner);
        Dueler d2 = duelers.remove(loser);
        
        if (saveDuel) {
        
	        String[] fields = {
	        	"fcNameChallenger",
	        	"fcNameAccepter",
	        	"fcWinner",
	        	"fnScoreChallenger",
	        	"fnScoreAccepter",
	        	"fnShipChallenger",
	        	"fnShipAccepter",
	        	"fnShip",
	        	"fnWinByLagout",
	        	"fnDuration",
	        	"fnMoney",
	        	"fdDate"
	        };
	        
	        String[] values = {
	        		Tools.addSlashes(d1.mode==Dueler.DUEL_CHALLENGER?d1.name:d2.name),
	        		Tools.addSlashes(d2.mode==Dueler.DUEL_ACCEPTER?d2.name:d1.name),
	        		Tools.addSlashes(realWinner),
	        		String.valueOf(d1.mode==Dueler.DUEL_CHALLENGER?d1.kills:d2.kills),
	        		String.valueOf(d2.mode==Dueler.DUEL_ACCEPTER?d2.kills:d1.kills),
	        		String.valueOf(d1.mode==Dueler.DUEL_CHALLENGER?d1.ship:d2.ship),
	        		String.valueOf(d2.mode==Dueler.DUEL_ACCEPTER?d2.ship:d1.ship),
	        		String.valueOf(d1.ship==d2.ship?d1.ship:0),
	        		String.valueOf(lagout?1:0),
	        		String.valueOf((int)((System.currentTimeMillis()-d1.start)/1000)),
	        		String.valueOf(amount),
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
            if(duelers.get(name).mode == 1){
                m_botAction.warpTo(name, areas.get(duelers.get(name).area).warp1x, areas.get(duelers.get(name).area).warp1y);
                m_botAction.shipReset(name);
            }
            if(duelers.get(name).mode == 2){
                m_botAction.warpTo(name, areas.get(duelers.get(name).area).warp2x, areas.get(duelers.get(name).area).warp2y);
                m_botAction.shipReset(name);
            }
        }     
    }
    private class StartLagout extends TimerTask {
        String name;
        private StartLagout(String name){
            this.name = name;
        }
        public void run(){
            announceWinner(duelers.get(name).opponent,name,0,0,duelers.get(name).amount,true);
            
        }
    }
    
    private class StartDuel extends TimerTask {
        String challenger, accepter;
        int area;
        int ship;
        private StartDuel(String challenger, String accepter, int ship, int area){
            this.challenger = challenger;
            this.accepter = accepter;
            this.area = area;
            this.ship = ship;
        }
        public void run() 
        {
            DuelArea dArea = areas.get(area);
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
                    
            // Let's prepare the duel if it's a "Any ship" kind of duel.
            if (ship==0) {
            	duelers.put(challenger, new Dueler(challenger,area,1,challenges.get(challenger).amount,ichalship,accepter));
            	duelers.put(accepter, new Dueler(accepter,area,2,challenges.get(challenger).amount,iaccship,challenger));
            }
            challenges.remove(challenger);
            m_botAction.setFreq(challenger, 0);
            m_botAction.setFreq(accepter, 1);
            m_botAction.warpTo(challenger, dArea.warp1x, dArea.warp1y);
            m_botAction.warpTo(accepter, dArea.warp2x, dArea.warp2y);
            m_botAction.sendPrivateMessage(challenger, "GO GO GO!", Tools.Sound.GOGOGO);
            m_botAction.sendPrivateMessage(accepter, "GO GO GO!", Tools.Sound.GOGOGO);

        }
    }
    
    private void warpToSafe(String name){
        m_botAction.warpTo(name, 305, 482);
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
        if(duelers.get(name).mode == 1){
            m_botAction.setShip(name, duelers.get(name).ship);
            m_botAction.warpTo(name, areas.get(duelers.get(name).area).warp1x, areas.get(duelers.get(name).area).warp1y);
            m_botAction.setFreq(name, 0);
            return;
        }
        if(duelers.get(name).mode == 2){
            m_botAction.setShip(name, duelers.get(name).ship);
            m_botAction.warpTo(name, areas.get(duelers.get(name).area).warp2x, areas.get(duelers.get(name).area).warp2y);
            m_botAction.setFreq(name, 1);
        }
    }

    
	@Override
	public void handleCommand(String sender, String command) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void handleModCommand(String sender, String command) {
		// TODO Auto-generated method stub
		
	}
    
}
class DuelArea {
    public int warp1y,warp1x,warp2y,warp2x;
    public boolean inUse = false;
    
    public DuelArea(int warp1x, int warp1y, int warp2x, int warp2y) {
        this.warp1x = warp1x;
        this.warp1y = warp1y;
        this.warp2x = warp2x;
        this.warp2y = warp2y;
    }
}
class Dueler {
	
	public static final int DUEL_CHALLENGER = 1;
	public static final int DUEL_ACCEPTER = 2;
	
    public int amount = 0;
    public int lagouts = 0;
    public String name,opponent;
    public int deaths = 0;
    public int kills = 0;
    public int area = 0;
    public int mode = 0; // 1=challenger 2=accepter
    public int ship = 0;
    public long start = 0;
    
    public Dueler(String name, int aNumber,int mode, int amount, int ship, String opponent){
        this.area = aNumber;
        this.mode = mode;
        this.name = name;
        this.amount = amount;
        this.ship = ship;
        this.opponent = opponent;
        this.start = System.currentTimeMillis();
    }
}
class Challenge {
	
    public int amount;
    public String challenged;
    public int ship;
    
    public Challenge (String challenged, int amount, int ship){
        this.amount = amount;
        this.challenged = challenged;
        this.ship = ship;
    }
}
