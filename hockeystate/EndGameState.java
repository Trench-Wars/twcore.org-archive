package twcore.bots.hockeybot.hockeystate;

import twcore.bots.hockeybot.HockeyImplementor;
import twcore.bots.hockeybot.hockeyteam.HockeyTeam;
import twcore.core.BotAction;

public class EndGameState extends HockeyState {
    
    private HockeyImplementor game;
    
    public EndGameState(HockeyImplementor game, BotAction bot){
        this.bot = bot;
        this.game = game;
    }

    @Override
    public boolean tryToAcceptSquad(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Couldn't accept, wait a little more, a game is being finished now");
        return false;
    }

    @Override
    public boolean tryToChallengeSquad(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Couldn't challenge, wait a little more, a game is being finished now");
        return false;
    }

    @Override
    public boolean tryToRegisterPlayer(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Couldn't register, wait a little more, a game is being finished now");
        return false;
    }

    @Override
    public boolean tryToStartGame(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Couldn't start, wait a little more, a game is being finished now");
        return false;
    }

    @Override
    public boolean tryToReadyTeam(HockeyTeam teams[], HockeyTeam team, String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Couldn't ready the team, it was done before and we're just ending a game.");
        return false;
    }

}
