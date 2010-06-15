package twcore.bots.hockeybot.hockeystate;

import java.sql.SQLException;

import twcore.bots.hockeybot.HockeyImplementor;
import twcore.bots.hockeybot.hockeydatabase.HockeyDatabase;
import twcore.bots.hockeybot.hockeyteam.HockeyTeam;
import twcore.core.BotAction;

public class OffState extends HockeyState {

    private HockeyImplementor game; 
    public OffState( HockeyImplementor game, BotAction bot ){
        this.game = game;
        this.bot = bot;
        }

    @Override
    public boolean tryToAcceptSquad(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Accepting the challenge!");
        return true;
    }

    @Override
    public boolean tryToChallengeSquad(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Challenged successfully!");
        return true;
    }

    @Override
    public boolean tryToRegisterPlayer(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "There are no games running, couldn't register you in!");
        return false;
    }

    @Override
    public boolean tryToStartGame(String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Starting the game!");
        game.setState(game.getHockeyGameIsRunning());
        return true;
    }

    @Override
    public boolean tryToReadyTeam(HockeyTeam teams[], HockeyTeam team, String name) {
        // TODO Auto-generated method stub
        bot.sendPrivateMessage(name, "Couldn't ready, there are no games running!");
        return false;
    }
   

}
