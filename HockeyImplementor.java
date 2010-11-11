package twcore.bots.hockeybot;

import twcore.bots.hockeybot.hockeystate.HockeyState;
import twcore.bots.hockeybot.hockeyteam.HockeyPlayer;
import twcore.bots.hockeybot.hockeyteam.HockeyTeam;
import twcore.core.BotAction;
import twcore.core.events.BallPosition;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public abstract class HockeyImplementor {

    protected HockeyTeam teams[];
    
    protected HockeyAssistGoalStack<String> hockeyStackPlayers;
    protected BotAction bot;
    
    protected HockeyState hockeyState;
    protected HockeyState hockeyPreStart;
    protected HockeyState hockeyGameIsRunning;
    protected HockeyState hockeyInterval;
    protected HockeyState hockeyOff;
    protected HockeyState hockeyEndGame;
    
    public abstract void savePlayerGoal();
    public abstract void savePlayerAssist();
    public abstract void savePlayerSave();
    public abstract void savePlayerTackle();
    public abstract void registerPlayer( String name, int ship );
    public abstract void startGame( String name, String teamName1, int teamId1, int teamId2, String teamName2 );
    public abstract void readyTeam( String name );
    public abstract void lagoutPlayer( String name );
  
    //Simulation to handleEvent and record assists / goal / tackles / saves
    public void handleBall(BallPosition event){
        try{
            
            if(event.getCarrier() == -1)
                return;
            
            Player player = bot.getPlayer(event.getPlayerID());
            int playerFreq = player.getFrequency();
            Player previousPlayer;
            String previousPlayerName = null;
            int previousPlayerFreq = -1;

            if( !hockeyStackPlayers.isEmpty() ){
                previousPlayerName = hockeyStackPlayers.getLast();
                previousPlayer = bot.getPlayer(previousPlayerName);
                previousPlayerFreq = previousPlayer.getFrequency();
            }
            
            if( previousPlayerFreq != playerFreq)
                hockeyStackPlayers.clear();
            if(previousPlayerName != null)
                hockeyStackPlayers.push(previousPlayerName);
            
        }catch(Exception e){
            Tools.printLog(e.toString());
            }
    }
    
    protected String getShipName(int ship){
        switch(ship){
            case 1: return "warbird";
            case 2: return "javelin";
            case 3: return "spider";
            case 4: return "levithan";
            case 5: return "terrier";
            case 6: return "weasel";
            case 7: return "lancaster";
            case 8: return "shark";
        }
        
        return null;
    }
    
    public void setState(HockeyState state){
        this.hockeyState = state;
    }
    public HockeyState getHockeyPreStart() {
        return hockeyPreStart;
    }
    public HockeyState getHockeyGameIsRunning() {
        return hockeyGameIsRunning;
    }
    public HockeyState getHockeyInterval() {
        return hockeyInterval;
    }
  
    public HockeyState getHockeyOff() {
        return hockeyOff;
    }
    public HockeyState getHockeyEndGame() {
        return hockeyEndGame;
    }
}
