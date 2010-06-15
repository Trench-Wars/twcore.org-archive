package twcore.bots.hockeybot;

import twcore.core.BotAction;
import twcore.core.SubspaceBot;
import twcore.core.events.BallPosition;
import twcore.core.events.Message;
import twcore.core.events.SoccerGoal;
import twcore.core.util.Tools;

public abstract class HockeyGame
        extends SubspaceBot {

    protected HockeyImplementor game; //Bridge
    
    public HockeyGame(BotAction botAction) {
        super(botAction);
        // TODO Auto-generated constructor stub
    }
    
    public abstract void startGame( String name, String teamName1, int teamId1, int teamId2, String teamName2 );
    public abstract boolean readyTeam();
    public abstract void endGame();
    public abstract void registerPlayer();
    public abstract boolean recordGoal();
    public abstract boolean recordSave();
    public abstract boolean recordAssist();
    public abstract boolean recordTackle();
    public abstract void lagoutPlayer();
    public abstract boolean challengeTeam();
    public abstract boolean removeChallenge();
    public abstract boolean acceptChallenge();
    
    private boolean setModule(String module){
        if(module.equals("practice"))
            game = new HockeyPractice(m_botAction);
        else if(module.equals("division"))
            game = new HockeyDivision(m_botAction);
        
        return true;
    }
    
    protected void preLock(String name, String message){
        try{
            //!lock <name>
            //0123456
            if( message.length() < 5)
                return;
            
            String module = message.substring(6);
            setModule(module);
            m_botAction.sendPrivateMessage(name, "Locked in "+module+" module");
            
        }catch(Exception e){
            Tools.printLog(e.toString());
        }
    }
    
    @Override
    public void handleEvent(SoccerGoal event){
        
    }
    
    @Override
    public void handleEvent(BallPosition event){
        game.handleBall(event);
    }
    
    @Override
    public void handleEvent(Message event){
        String message = event.getMessage();
        String name = m_botAction.getPlayerName(event.getPlayerID());
        handleMessage(name, message);
    }

    //Template method
    private void handleMessage(String name, String message){
       try{
            if(message.startsWith("!register"))
                registerPlayer();
        
            else if( message.startsWith("!challenge"))
                challengeTeam();
            else if( message.startsWith("!game"))
                preStart(name, message);
            else if( message.startsWith("!lock"))
                preLock(name, message);
            
       }catch(Exception e){
            Tools.printLog(e.toString());
            m_botAction.sendPrivateMessage(name, "Syntax error, please type the right commands followin in !help interface.");
       }
    }
    
    private void preStart(String name, String command){
        //!game A:B
        //0123456
        String split[] = command.split(":");
        split[0] = split[0].substring(0);
        startGame(name, split[0], 0, 1, split[1]);
    }
}
