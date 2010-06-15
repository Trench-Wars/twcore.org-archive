package twcore.bots.hockeybot.hockeystate;

import twcore.bots.hockeybot.HockeyImplementor;
import twcore.bots.hockeybot.hockeyteam.HockeyTeam;
import twcore.core.BotAction;

public class IntervalState extends HockeyState{
    
    private HockeyImplementor game; 
    public IntervalState( HockeyImplementor game, BotAction bot ){
        this.bot = bot;
        this.game = game;
    }

    @Override
    public boolean tryToAcceptSquad(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "We're on an interval now, can't accept a challenge.");
        return false;
    }

    @Override
    public boolean tryToChallengeSquad(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "We're on an interval now, please, challenge when there are no games.");
        return false;
    }

    @Override
    public boolean tryToRegisterPlayer(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "[INTERVAL] Registering you in ...");
        return true;
    }

    @Override
    public boolean tryToStartGame(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Can't start the game, we're on an interval...");
        return false;
    }

    @Override
    public boolean tryToReadyTeam(HockeyTeam teams[], HockeyTeam team, String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Ready!");
        return true;
    }
   

}
