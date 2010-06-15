package twcore.bots.hockeybot.hockeystate;

import java.sql.SQLException;

import twcore.bots.hockeybot.HockeyImplementor;
import twcore.bots.hockeybot.hockeydatabase.HockeyDatabase;
import twcore.bots.hockeybot.hockeyteam.HockeyTeam;
import twcore.core.BotAction;

public class PreStartState extends HockeyState {
    
    private HockeyImplementor game; 
    public PreStartState( HockeyImplementor game, BotAction bot ) {
        this.bot = bot;
        this.game = game;
    }

    @Override
    public boolean tryToAcceptSquad(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "You can't accept a squad now, we're running a game on here.");
        return false;
    }

    @Override
    public boolean tryToChallengeSquad(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Sorry, can't challenge a squad, we're on a PRE-START now.");
        return false;
    }

    @Override
    public boolean tryToRegisterPlayer(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "[PRE START] Registering you in!");
        return true;
    }

    @Override
    public boolean tryToStartGame(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Couldn't start a game, we are on a PRE START!");
        return false;
    }

    @Override
    public boolean tryToReadyTeam(HockeyTeam teams[], HockeyTeam team, String name) {
        // TODO Auto-generated method stub
        team.setReady(true);
        bot.sendPrivateMessage(name, "Ready!");
        bot.sendArenaMessage("Team "+team.getTeamName()+" is ready to play");
        
        if(checkReady(teams))
            game.setState(game.getHockeyGameIsRunning());
        
        return true;
    }
    
    private boolean checkReady(HockeyTeam teams[]){
        
        if(teams[0].isReady() && teams[1].isReady())
            return true;
        
        return false;
    }

}
