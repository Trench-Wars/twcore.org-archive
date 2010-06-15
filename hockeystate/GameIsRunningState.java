package twcore.bots.hockeybot.hockeystate;

import twcore.bots.hockeybot.HockeyImplementor;
import twcore.bots.hockeybot.hockeyteam.HockeyTeam;
import twcore.core.BotAction;

public class GameIsRunningState extends HockeyState {
    
    private HockeyImplementor game; 
    public GameIsRunningState( HockeyImplementor game, BotAction bot ){
        this.bot = bot;
        this.game = game;
    }

    @Override
    public boolean tryToAcceptSquad(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Couldn't accept a squad, there are teams playing now.");
        return false;
    }

    @Override
    public boolean tryToChallengeSquad(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Couldn't challenge, a game is running now!");
        return false;
    }

    @Override
    public boolean tryToRegisterPlayer(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Couldn't register you in, the game has started already.");
        return false;
    }

    @Override
    public boolean tryToStartGame(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Couldn't start, a game is running now!");
        return false;
    }

    @Override
    public boolean tryToReadyTeam(HockeyTeam teams[], HockeyTeam team, String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "[Game running] Couldn't ready the team, it has been ready already.");
        return false;
    }
    
}
