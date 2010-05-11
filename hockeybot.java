package twcore.bots.hockeybot;

import java.sql.SQLException;

import twcore.bots.hockeybot.HockeyPractice.HockeyState;
import twcore.bots.hockeybot.HockeyRegistrator.GameRequest;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.BallPosition;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.SoccerGoal;
import twcore.core.game.Player;

//goal_datetime - start_datetime = minutes in game
//ref decides:"clean/lag/phase/cr/bcr"
//"No goal: lag", No goal: shot inside crease", no goal: phase"
//ball intent..etc
//cr = goal shot in crease
//bcr = ball intentionally shot in crease
/**
 * @author: Dexter
 * Bot project to Hockey - !teamsignup ready to signup squads on the trenchwars.org/twht site
 *
 * Façade design pattern as a bot / application
 * */

public class hockeybot
        extends SubspaceBot {

    
    private OperatorList op;
    private EventRequester events;
    
    protected hockeybot practice;
    
    private String pubHelp [] = {
            "Hi, I'm a bot in development to the TW-Hockey-Tournament,",
            "you can register your squad already!",
            "| Commands --------------------------------------------------------",
            "| !teamsignup <squadName>    -  Registers your squad on TWHT's site",
            "| check TWHT'S Site: www.trenchwars.org/twht"
    };
    
    private String smodHelp [] = {
            "| !die                        -  Kills the bot "
    };
    
    public void requestEvents(EventRequester eventRequester){
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.LOGGED_ON);
        eventRequester.request(EventRequester.BALL_POSITION);
        eventRequester.request(EventRequester.SOCCER_GOAL);
    }

    public hockeybot(BotAction botAction) {
        super(botAction);
        doStartBot();     
       
    }
    
    public void doStartBot(){
        this.events = m_botAction.getEventRequester();
        this.requestEvents(events);
        this.op = m_botAction.getOperatorList();
        
    }

    public void doLoadGame(String name, String message){
        //this.hzsql.
        //!load id
        //123456
        int matchId = Integer.parseInt( message.substring(6) );
    }

    /**
     * Events being worked on still.
     * */
    @Override
    public void handleEvent(BallPosition event){
        //gets the carrier and keeps getting him in a loop..
        Player p;
        
        if(event.getCarrier() == -1)
            return;
        
        if(HockeyPractice.state.getCurrentState() == HockeyState.Period_In_Progress){
            p = m_botAction.getPlayer(event.getPlayerID());
        }
    }
    
    @Override
    public void handleEvent(SoccerGoal event){
       //gets the frequence number of teams goal
        if(HockeyPractice.state.getCurrentState() == HockeyState.Period_In_Progress){
            m_botAction.sendArenaMessage("woo Freq "+
                event.getFrequency()+"!");
            HockeyPractice.state.setState(HockeyState.FaceOff);
        }
        
    }
    
    @Override
    public void handleEvent(LoggedOn event){
        BotSettings botSettings = m_botAction.getBotSettings();
        String initialSpawn = botSettings.getString("InitialArena");
        m_botAction.joinArena(initialSpawn);
    }
    
    @Override
    public void handleEvent(Message event){
        
        String name = m_botAction.getPlayerName( event.getPlayerID() );
        String message = event.getMessage();
        int messageType = event.getMessageType();
        
        if( messageType == Message.PRIVATE_MESSAGE){
            if(name == null)
                return;
            
            handleCommand(name, message);
        }
        
    }
    
    private void handleCommand(String name, String message) {
       
        if(message.startsWith("!help"))
            m_botAction.privateMessageSpam(name, this.pubHelp);
        
        if(message.startsWith("!help") && op.isModerator(name))
            m_botAction.privateMessageSpam(name, this.smodHelp);
        
        if(message.startsWith("!loadgame"))
            doLoadGame(name, message);
        
        /**
         * Façade
         * */
        else if( message.startsWith("!teamsignup")){
            
            hockeybot registrator;
            
            try{
                registrator = new HockeyRegistrator(m_botAction);
                registrator.createTeam(name, message);
                
            }catch(SQLException e){
                e.printStackTrace();
            }catch(Exception e){
                e.printStackTrace();
            }
            
        }
        
        else if (message.startsWith("!put")){
            hockeybot registrator;
            try{
                registrator = new HockeyRegistrator(m_botAction);
                registrator.requestGame(name, message);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        
        else if (message.startsWith("!register")){
            
            if( getClass().isInstance(practice)
                    && 
                    HockeyPractice.state.getCurrentState() == HockeyState.PreStartPeriod ){
                
                m_botAction.sendPrivateMessage(name, "Registering you into the ship");
                
                try{
                    practice.addPlayer(name, message);
                    
                }catch(Exception e){e.printStackTrace();}
            }else m_botAction.sendPrivateMessage(name, "Game hasn't started.");
        }
            
        else if(message.startsWith("!challenges"))
        {
            for(String st: GameRequest.squadRequests)
                m_botAction.sendPrivateMessage(name, st);
        }
        
        else if(message.startsWith("!accept")){
            if(!getClass().isInstance(practice)){
                try{
                    practice = new HockeyPractice(m_botAction);
                    practice.doAcceptGame(name, message);
                    
                }catch(Exception e){
                    e.printStackTrace();
                }
            }else m_botAction.sendPrivateMessage(name, "A game is running already. Please wait");
        }
        
        else if(message.startsWith("!die") && op.isModerator(name))
            doDie(name, message);
        
        else if(message.startsWith("!pause")){
            HockeyPractice.HockeyChronometer.doPause();

            m_botAction.sendArenaMessage("Game paused!", 7);
        }
        
        else if(message.startsWith("!back")){
            HockeyPractice.HockeyChronometer.doStartBack();
            m_botAction.sendArenaMessage("GO GO GO, back playing!", 104);
        }
        
        else if(message.startsWith("!stop"))
            this.practice = null;
        //else if(message.startsWith("!squads"))
          //  doDisplaySquads(name, message);
    }

    
    public void doRegister(String name, String message){}
    public void doAcceptGame(String name, String message){}
    public void doStartGame(String name, String teamName1, String teamName2){}
    public void doPreStart(String name, String teamName1, String teamName2){}
    public void createTeam(String name, String message){}
    public void requestGame(String name, String message){}
    public void addPlayer(String name, String message) throws SQLException{}
    public void doDie(String name, String message){
        m_botAction.sendPrivateMessage(name, "Bot disconnecting.");
        m_botAction.die();
        
    }
}
