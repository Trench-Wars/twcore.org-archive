package twcore.bots.pubsystem;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.game.Player;

// TODO: lagouts, playerLeave, playerenter


public class PubChallenge {

    private BotAction m_botAction;
    private Map<Integer ,duelArea> areas;
    private Map<String,dueler> duelers;
    private Map<String, challenge> challenges;
    private PubPlayerManager manager;
    private Map<String, StartLagout> laggers;
    private boolean enabled = false;
        
    public PubChallenge (BotAction m_botAction, PubPlayerManager manager){
        this.m_botAction = m_botAction;
        this.areas = new HashMap<Integer, duelArea>();
        this.duelers = new HashMap<String, dueler>();
        this.challenges = new HashMap<String, challenge>();
        this.manager = manager;
        this.laggers = new HashMap<String, StartLagout>();
        // adding duel-area warp points here (duelAreas(int warp1y, int warp1x, int warp2y, int warp2x))
        // keep 0 free for no available arenas
        // TODO: get accurate duelarea coords
        areas.put(1, new duelArea(100,100,110,110));
        areas.put(2, new duelArea(200,200,210,210));
        
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
                    issueChallenge(sender, pieces[0], amount);
                }catch(NumberFormatException e){
                    m_botAction.sendPrivateMessage(sender, "Proper use is !challenge name:amount");
                }
            }
            if(pieces.length != 2)
                m_botAction.sendPrivateMessage(sender, "Proper use is !challenge name:amount");
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
            duelers.get(name.toLowerCase()).lagouts++;
            if(duelers.get(name.toLowerCase()).lagouts > 2){
                announceWinner(duelers.get(name.toLowerCase()).opponent,name,0,0,duelers.get(name).amount,true);
                return;
            }
            laggers.put(name, new StartLagout(name));
            m_botAction.scheduleTask(laggers.get(name), 60*1000);
            m_botAction.sendPrivateMessage(name, "You have lagged out. You have 60 seconds to return to the game. Use !lagout to return. You have "+(2-duelers.get(name.toLowerCase()).lagouts)+" lagouts left.");
            m_botAction.sendPrivateMessage(duelers.get(name.toLowerCase()).opponent, "Your opponent has lagged out. He has 60 seconds to return to the game.");
            return;
        }
        if(event.getShipType() != duelers.get(name.toLowerCase()).ship) {
            m_botAction.setShip(name, duelers.get(name.toLowerCase()).ship);
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
            duelArea area = areas.get(duelers.get(name.toLowerCase()).area);
            if(duelers.get(name.toLowerCase()).mode == 1){
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
    
    public void handleEvent(PlayerDeath event) {
        if(!enabled)
            return;
        String killer = m_botAction.getPlayerName(event.getKillerID()).toLowerCase();
        String killee = m_botAction.getPlayerName(event.getKilleeID()).toLowerCase();
        if(!duelers.containsKey(killer) & !duelers.containsKey(killee))
            return;
        dueler w = duelers.get(killer);
        dueler l = duelers.get(killee);
        w.kills++;
        l.deaths++;
        if(l.deaths == 3) {
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
        Iterator<Entry<Integer,duelArea>> iter = areas.entrySet().iterator();
        while(iter.hasNext()){
            Entry<Integer,duelArea> entry = iter.next();
            duelArea area = entry.getValue();
            if(!area.inUse){
                empty = entry.getKey();
                return empty;
            }
        }
        return empty;
    }
    
    public void playerLeftRemoveChallenge(String name){
        if(!challenges.containsKey((name.toLowerCase())))
            return;
        challenges.remove(name.toLowerCase());
    }
    
    public void issueChallenge(String challenger, String challenged, int amount) {
        if(challenges.containsKey(challenger)){
            challenge pending = challenges.get(challenger);
            m_botAction.sendPrivateMessage(challenger, "You have already a pending challenge with "+pending.challenged+". Please remove it before challengin more.");
            return;
        }
        boolean validChallenge = false;
        Iterator<Player> arena = m_botAction.getPlayerIterator();
        while(arena.hasNext()){
            Player player = arena.next();
            if(player.getPlayerName().toLowerCase().startsWith(challenged.toLowerCase())){
                challenged = player.getPlayerName();
                validChallenge = true;
                break;
            }
        }
        if(!validChallenge){
            m_botAction.sendPrivateMessage(challenger, "No such player in the arena.");
            return;
        }
            
        if(challenger.toLowerCase().equals(challenged.toLowerCase())){
            m_botAction.sendPrivateMessage(challenger, "I pity the fool who challenges himself for a duel.");
            return;
        }
        
        if(manager.getPlayer(challenger).getMoney() < amount){
            m_botAction.sendPrivateMessage(challenger, "You don't have enough money.");
            return;
        }
            
        m_botAction.sendPrivateMessage(challenged, challenger +" has challenged you to duel for amount of $"+amount+". To accept reply !accept "+challenger);
        challenges.put(challenger.toLowerCase(), new challenge(challenged.toLowerCase(),amount));
        m_botAction.sendPrivateMessage(challenger, "Challenge sent to "+challenged+" for $"+amount+".");
        
    }
    public void acceptChallenge(String accepter, String challenger) {
        if(duelers.containsKey(accepter.toLowerCase())) {
            m_botAction.sendPrivateMessage(accepter, "You are already dueling.");
            return;
        }
        Iterator<Entry<String, challenge>> iter = challenges.entrySet().iterator();
        while(iter.hasNext()){
            Entry<String, challenge> e = iter.next();
            if(e.getValue().challenged.equals(accepter.toLowerCase()))
                if(e.getKey().startsWith(challenger)){
                    challenger = e.getKey();
                    break;
                }
        }
        
        try {
            if(!challenges.get(challenger.toLowerCase()).challenged.equalsIgnoreCase(accepter)){
                m_botAction.sendPrivateMessage(accepter, "You dont have a challenge from "+challenger+".");
                return;
            }
        }catch(NullPointerException e){
            m_botAction.sendPrivateMessage(accepter, "You dont have a challenge from "+challenger+".");
            return;
        }
        
        if(manager.getPlayer(accepter).getMoney() < challenges.get(challenger.toLowerCase()).amount){
            m_botAction.sendPrivateMessage(accepter, "You don't have enough money to accept the challenge. Challenge removed.");
            m_botAction.sendPrivateMessage(challenger, accepter+" doesn't have enough money to accept the challenge. Challenge removed.");
            challenges.remove(challenger.toLowerCase());
            return;
        }
        int area = getEmptyDuelArea();
        if(area == 0){
            m_botAction.sendPrivateMessage(accepter, "Unfortunately there is no available duel area. Try again later.");
            m_botAction.sendPrivateMessage(challenger, accepter+"has accepted your challenge. Unfortunately there is no available duel area. Try again later.");
            return;
        }
        duelArea dArea = areas.get(area);
        dArea.inUse = true;
        m_botAction.sendPrivateMessage(challenger, accepter+" has accepted your challenge. You have 10 seconds to get into your dueling ship.");
        m_botAction.sendPrivateMessage(accepter, "Challenge accepted. You have 10 seconds to get into your dueling ship.");
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
        challenges.remove(accepter.toLowerCase());
        m_botAction.scheduleTask(new StartDuel(challenger,accepter,area), 10*1000);
            
        
    }
    public void removeChallenge(String name){
        if(!challenges.containsKey(name.toLowerCase())){
            m_botAction.sendPrivateMessage(name, "You don't have a pending challenge to remove.");
            return;
        }
            
        try{
            String challengee = challenges.get(name.toLowerCase()).challenged;
            challenges.remove(name.toLowerCase());
            m_botAction.sendPrivateMessage(name, "Challenge removed.");
            m_botAction.sendPrivateMessage(challengee, name+" has removed the challenge against you.");
        }catch(NullPointerException e){
            m_botAction.sendPrivateMessage(name, "You don't have a pending challenge to remove.");
        }
    }
    private void announceWinner(String winner, String loser, int winnerKills, int loserKills ,int amount, boolean lagout){
        warpToSafe(winner);
        String realWinner = m_botAction.getPlayerName(m_botAction.getPlayerID(winner));
        String realLoser = m_botAction.getPlayerName(m_botAction.getPlayerID(loser));
        if(laggers.containsKey(realWinner) & laggers.containsKey(realLoser)){
            m_botAction.sendArenaMessage("Duel between "+realWinner+" and "+realLoser+" has been declared as void since both lagged out.");
            duelers.remove(winner);
            duelers.remove(loser);
            m_botAction.cancelTask(laggers.get(realWinner));
            laggers.remove(realWinner);
            laggers.remove(realLoser);
            return;
        }
            
        if(lagout){
            m_botAction.sendArenaMessage(realWinner+" has beaten "+realLoser+" by lagout in duel for $"+amount+".");
            laggers.remove(realLoser);
        }
        if(!lagout){
            m_botAction.sendArenaMessage(realWinner+" has beaten "+realLoser+" by "+winnerKills+"-"+loserKills+" in duel for $"+amount+".");
            duelArea dArea = areas.get(duelers.get(winner).area);
            dArea.inUse = false;
        }
        duelArea dArea = areas.get(duelers.get(winner).area);
        dArea.inUse = false;
        manager.getPlayer(realWinner).setMoney(manager.getPlayer(realWinner).getMoney()+amount);
        manager.getPlayer(realLoser).setMoney(manager.getPlayer(realLoser).getMoney()-amount);
        duelers.remove(winner);
        duelers.remove(loser);
            
        
        
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
            announceWinner(duelers.get(name.toLowerCase()).opponent,name,0,0,duelers.get(name).amount,true);
            
        }
    }
    
    private class StartDuel extends TimerTask {
        String challenger, accepter;
        int area;
        private StartDuel(String challenger, String accepter, int area){
            this.challenger = challenger;
            this.accepter = accepter;
            this.area = area;
        }
        public void run() {
            duelArea dArea = areas.get(area);
            Player p_chall = m_botAction.getPlayer(challenger);
            Player p_acc = m_botAction.getPlayer(accepter);
            int ichalship = p_chall.getShipType();
            int iaccship = p_acc.getShipType();
            if(ichalship == 0){
                ichalship = 1;
                m_botAction.setShip(challenger, ichalship);
            }
            if(iaccship == 0){
                iaccship = 1;
                m_botAction.setShip(accepter, iaccship);
            }
            duelers.put(challenger.toLowerCase(), new dueler(challenger.toLowerCase(),area,1,challenges.get(challenger).amount,ichalship,accepter.toLowerCase()));
            duelers.put(accepter.toLowerCase(), new dueler(accepter.toLowerCase(),area,2,challenges.get(challenger).amount,iaccship,challenger.toLowerCase()));
            challenges.remove(challenger.toLowerCase());
            m_botAction.setFreq(challenger, 0);
            m_botAction.setFreq(accepter, 1);
            m_botAction.warpTo(challenger, dArea.warp1x, dArea.warp1y);
            m_botAction.warpTo(accepter, dArea.warp2x, dArea.warp2y);
            m_botAction.sendPrivateMessage(challenger, "GO GO GO!");
            m_botAction.sendPrivateMessage(accepter, "GO GO GO!");
        }
    }
    private void warpToSafe(String name){
        m_botAction.warpTo(name, 305, 482);
    }
    public boolean isDueling(String name) {
        return duelers.containsKey(name.toLowerCase());
    }
    public void changeState() {
        if(enabled)
            enabled = false;
        else
            enabled = true;
    }
    
    public void returnFromLagout(String name){
        if(!laggers.containsKey(name)){
            m_botAction.sendPrivateMessage(name, "You have not lagged out from a duel.");
            return;
        }
        m_botAction.cancelTask(laggers.get(name));
        laggers.remove(name);
        name = name.toLowerCase();
        if(duelers.get(name).mode == 1){
            m_botAction.setShip(name, duelers.get(name).ship);
            m_botAction.warpTo(name, areas.get(duelers.get(name).area).warp1x, areas.get(duelers.get(name).area).warp1y);
            m_botAction.setFreq(name, 0);
            return;
        }
        if(duelers.get(name.toLowerCase()).mode == 2){
            m_botAction.setShip(name, duelers.get(name).ship);
            m_botAction.warpTo(name, areas.get(duelers.get(name).area).warp2x, areas.get(duelers.get(name).area).warp2y);
            m_botAction.setFreq(name, 1);
        }
    }
    
}
class duelArea {
    public int warp1y,warp1x,warp2y,warp2x;
    public boolean inUse = false;
    
    public duelArea(int warp1y, int warp1x, int warp2y, int warp2x) {
        this.warp1x = warp1x;
        this.warp1y = warp1y;
        this.warp2x = warp2x;
        this.warp2y = warp2y;
    }
}
class dueler {
    public int amount = 0;
    public int lagouts = 0;
    public String name,opponent;
    public int deaths = 0;
    public int kills = 0;
    public int area = 0;
    public int mode = 0; // 1=challenger 2=accepter
    public int ship = 0;
    
    public dueler(String name, int aNumber,int mode, int amount, int ship, String opponent){
        this.area = aNumber;
        this.mode = mode;
        this.name = name;
        this.amount = amount;
        this.ship = ship;
        this.opponent = opponent;
    }
}
class challenge {
    public int amount;
    public String challenged;
    
    public challenge (String challenged, int amount){
        this.amount = amount;
        this.challenged = challenged;
    }
}
