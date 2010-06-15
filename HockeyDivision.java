package twcore.bots.hockeybot;

import java.sql.SQLException;

import twcore.bots.hockeybot.hockeydatabase.HockeyDatabase;
import twcore.bots.hockeybot.hockeystate.EndGameState;
import twcore.bots.hockeybot.hockeystate.GameIsRunningState;
import twcore.bots.hockeybot.hockeystate.IntervalState;
import twcore.bots.hockeybot.hockeystate.OffState;
import twcore.bots.hockeybot.hockeystate.PreStartState;
import twcore.bots.hockeybot.hockeyteam.HockeyTeam;
import twcore.core.BotAction;
import twcore.core.util.Tools;

public class HockeyDivision
        extends HockeyImplementor {

    private HockeyDatabase database;
    
    public HockeyDivision(BotAction bot){
        this.bot = bot;
        this.hockeyState = new OffState(this, this.bot);
        this.hockeyEndGame = new EndGameState(this, this.bot);
        this.hockeyGameIsRunning = new GameIsRunningState(this, this.bot);
        this.hockeyInterval = new IntervalState(this, this.bot);
        this.hockeyOff = new OffState(this, this.bot);
        this.hockeyPreStart = new PreStartState(this, this.bot);
        this.hockeyStackPlayers = new HockeyAssistGoalStack<String>();
 
        try{
            database = new HockeyDatabase(bot);
        }catch(SQLException e){
            Tools.printLog(e.toString());
            }
    }
    @Override
    public void lagoutPlayer(String name) {
        // TODO Auto-generated method stub

    }

    @Override
    public void registerPlayer(String playerName, int ship) {
        // TODO Auto-generated method stub
       if(hockeyState.tryToRegisterPlayer(playerName)){
          
           try{

               int teamFreq = getTeamFreq(playerName);
               teams[teamFreq].addPlayer(playerName, ship);
               bot.setShip(playerName, ship);
               bot.setFreq(playerName, teamFreq);
               bot.sendArenaMessage(playerName+" is registered in as "+getShipName(ship));
               
           }catch(SQLException e){
               Tools.printLog(e.toString());
           }
       }
    }

    @Override
    public void savePlayerAssist() {
        // TODO Auto-generated method stub

    }

    @Override
    public void savePlayerGoal() {
        // TODO Auto-generated method stub

    }

    @Override
    public void savePlayerSave() {
        // TODO Auto-generated method stub

    }

    @Override
    public void savePlayerTackle() {
        // TODO Auto-generated method stub

    }

    @Override
    public void startGame(String name, String teamName1, int teamId1, int teamId2, String teamName2) {
        // TODO Auto-generated method stub
        if(hockeyState.tryToStartGame(name)){
            this.teams[0] = new HockeyTeam(teamId1, 0, teamName1, bot);
            this.teams[1] = new HockeyTeam(teamId2, 1, teamName2, bot);
        }
    }
    
    private int getTeamFreq(String playerName) throws SQLException{
        String name = database.getPlayerTeamName(playerName);
        
        if(teams[0].getTeamName().equals(name))
            return 0;
        else if(teams[1].getTeamName().equals(name))
            return 1;
        
        return -1;
    }
    
    @Override
    public void readyTeam(String name) {
        // TODO Auto-generated method stub
        try{
            int freq = getTeamFreq(name);
            hockeyState.tryToReadyTeam(teams, teams[freq], name);
        }catch(SQLException e){
            Tools.printLog(e.toString());
        }catch(Exception e){
            Tools.printLog(e.toString());
        }
    }

}
