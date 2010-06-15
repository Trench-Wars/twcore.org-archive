package twcore.bots.hockeybot;

import twcore.bots.hockeybot.hockeystate.EndGameState;
import twcore.bots.hockeybot.hockeystate.GameIsRunningState;
import twcore.bots.hockeybot.hockeystate.IntervalState;
import twcore.bots.hockeybot.hockeystate.OffState;
import twcore.bots.hockeybot.hockeystate.PreStartState;
import twcore.bots.hockeybot.hockeyteam.HockeyTeam;
import twcore.core.BotAction;
import twcore.core.util.Tools;

public class HockeyPractice
        extends HockeyImplementor {

    private int turn;
    
    public HockeyPractice(BotAction bot){
        this.bot = bot;
        this.hockeyState = new OffState(this, this.bot);
        this.hockeyEndGame = new EndGameState(this, this.bot);
        this.hockeyGameIsRunning = new GameIsRunningState(this, this.bot);
        this.hockeyInterval = new IntervalState(this, this.bot);
        this.hockeyOff = new OffState(this, this.bot);
        this.hockeyPreStart = new PreStartState(this, this.bot);
        this.hockeyStackPlayers = new HockeyAssistGoalStack<String>();
        
    }
    @Override
    public void lagoutPlayer(String name) {
        // TODO Auto-generated method stub

    }

    @Override
    public void readyTeam(String name) {
        // TODO Auto-generated method stub
        int freq = getTeamFreq(name);
        hockeyState.tryToReadyTeam(teams, teams[freq], name);
    }

    @Override
    public void registerPlayer(String name, int ship) {
        // TODO Auto-generated method stub
        int freq = getTurn();
        if( hockeyState.tryToRegisterPlayer(name) )
            teams[freq].addPlayer(name, ship);
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
    public void startGame(String name, String teamName1, int teamId1,
            int teamId2, String teamName2) {
        // TODO Auto-generated method stub
        if(hockeyState.tryToStartGame(name)){
            this.teams[0] = new HockeyTeam(teamId1, 0, teamName1, bot);
            this.teams[1] = new HockeyTeam(teamId2, 1, teamName2, bot);
            setTurn(0);
        }
    }

    private int getTeamFreq(String name){
     
        try{
            if(teams[0].Contains(name))
                return 0;
            else if(teams[1].Contains(name))
                return 1;
        }catch(Exception e){
            Tools.printLog(e.toString());
        }
        
        return -1;
    }
    
    private int getTurn(){
        int previousTurn = turn;
        
        if(previousTurn == 0 )
            setTurn(1);
        else if(previousTurn == 1)
            setTurn(0);
        
        return previousTurn;
    }
    
    private void setTurn(int turn){
        this.turn = turn;
    }
}
