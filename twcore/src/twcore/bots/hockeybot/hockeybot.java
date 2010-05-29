package twcore.bots.hockeybot;


import java.sql.SQLException;

import twcore.bots.hockeybot.hockeymediator.*;
import twcore.bots.hockeybot.hockeyregistrator.HockeyRegistrator;
import twcore.bots.hockeybot.hockeyregistrator.HockeyRegistrator.GameRequest;
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
import twcore.core.util.Tools;

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
        extends SubspaceBot{

    /* Mediator pattern - the 'heart' of the game*/
    private HockeyConcreteMediator mediator;
    
    /*
     * List to stats: save, goal, assist ( 1st, 2nd )*/
    private HockeyAssistGoalStack<String> list ;
    
    private OperatorList op;
    private EventRequester events;
 
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
    

    public hockeybot(BotAction botAction) {
        super(botAction);
        doStartBot(); 
       
    }
    
    public void doStartBot(){
        
        this.mediator = new HockeyConcreteMediator(m_botAction);
        this.events = m_botAction.getEventRequester();
        this.requestEvents(events);
        this.op = m_botAction.getOperatorList();
        this.list = new HockeyAssistGoalStack<String>();
        //this.mediator = new HockeyConcreteMediator(m_botAction);
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
    public void handleEvent(SoccerGoal event){
        //m_botAction.getEventRequester().notify();
       
        
        /** 
         * if(mediator.gameIsRunning()){
           */ 
        m_botAction.sendArenaMessage("Goal by freq "+event.getFrequency()+" !", 2);
            System.out.println("GOAL!");
            
            /**
             * Trabalhar aqui para adicionar pontos a cada jogador
             * */
            
            try{
                String p_Goal = null;
                String p_A1 = null;
                String p_A2 = null;
                int freq = event.getFrequency();
                
                
                p_Goal = list.pop();
                
                if(p_Goal == null)
                    return;
                
                if(list.getSize() >= 1){
                    p_A1 = list.pop();
                    
                }
                if(list.getSize() == 1)
                    p_A2 = list.pop();
                
                m_botAction.sendArenaMessage("Goal by: "+p_Goal);
                
                if(p_A1 != null){
                    m_botAction.sendArenaMessage("Assist: "+p_A1);
                    this.addPlayerPoint(p_A1, freq, 2);
                }
                if(p_A2 != null){
                    m_botAction.sendArenaMessage("2nd Assist: "+p_A2);
                    this.addPlayerPoint(p_A2, freq, 2);
                }
                /*if(!p_Goal.equals(p_A1) && !p_A1.equals(p_A2) && p_A2 != null && p_A1 != null){
                    this.addPlayerPoint(p_A2, freq, 2);
                    this.addPlayerPoint(p_A1, freq, 2);
                    System.out.println("Entrei aqui");
                }*/
                this.addPlayerPoint(p_Goal, freq, 1);
                setFaceOffState();
                list.clear();
            }
            catch(HockeyListEmptyException e){
                Tools.printLog(e.toString());
            }catch(Exception e){
                Tools.printLog(e.toString());
            } catch (Throwable e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    //}
    
    @Override
    public void handleEvent(BallPosition event){
        //gets the carrier and keeps getting him in a loop..
        //if(getClass().isInstance(practice)){
        
        /**
         * IF STATE == GAME IN PROGRESS
        */
        Player p; 
           String p_name;
           String pprevious_name;
           int p_freq;
           int p_freq2 = -1;
           
           if(event.getCarrier() == -1)
               return;
           
           p = m_botAction.getPlayer(event.getPlayerID());
            
            if(p.getShipType() == 7 || p.getShipType() == 8){
                list.clear();
                m_botAction.sendArenaMessage("Save! "+p.getPlayerName());
                
            }
            else{
                try{
                    p = m_botAction.getPlayer(event.getPlayerID());
                    p_freq = p.getFrequency();
                    p_name = m_botAction.getPlayerName(event.getPlayerID());
                    m_botAction.sendArenaMessage("Ball: "+p_name);
                    if(!list.isEmpty())
                        p_freq2 = m_botAction.getPlayer(list.getLast()).getFrequency();
                    
                    if(p_freq != p_freq2)
                        list.clear();
                        
                    list.push(p_name);
                        
                }catch(HockeyListEmptyException e){
                    Tools.printLog(e.toString());
                }catch(Exception e){
                    Tools.printLog(e.toString());
                }
            }}
    
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
            /*if(name == null)
                return;
            */
            try {
                handleCommand(name, message);
            } catch (SQLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
    }
    
    private void handleCommand(String name, String message) throws SQLException {
       
        if(message.startsWith("!help"))
            m_botAction.privateMessageSpam(name, this.pubHelp);
        
        else if(message.startsWith("!help") && op.isModerator(name))
            m_botAction.privateMessageSpam(name, this.smodHelp);
        
        else if(message.startsWith("!loadgame"))
            doLoadGame(name, message);
        else if (message.startsWith("!faceoff"))
            doFaceOff();
        /**
         * Façade
         * */
      
        else if( message.startsWith("!result") && mediator.gameIsRunning())
            mediator.displayResult();
        
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
            requestGame(name, message);
        }
        
        else if (message.startsWith("!register")){
            /**
             * IF GAME IN PROGRESS*/
            if(mediator.gameIsRunning()){
                registerPlayer(name, message);
                m_botAction.sendPrivateMessage(name, "Registering you into the ship");
            }
            else
                m_botAction.sendPrivateMessage(name, "Couldn't register you in because there are no games running.");
        }
            
        else if(message.startsWith("!challenges"))
        {
            for(String st: GameRequest.squadRequests)
                m_botAction.sendPrivateMessage(name, st);
        }
        
        else if(message.startsWith("!gpoints"))
        {}//doGetGoalPoints(name);
        else if(message.startsWith("!spoints")){
            
        }
        else if(message.startsWith("!ready")){
            doReadyTeam(name, message);
        }
        
        else if(message.startsWith("!accept")){
            try{
                String squadAccepted;
                
                /**
                 * IF GAME IN PROGRESS*/
                if(mediator.gameIsRunning()){
                    m_botAction.sendPrivateMessage(name, "A game is already running. Please wait");
                    return;
                }
                
                squadAccepted = message.substring(8);
                
                if(!GameRequest.squadRequests.contains(squadAccepted)){
                    m_botAction.sendPrivateMessage(name, "The squad "+squadAccepted+" is not on the list, try to accept other one: look !challenges");
                    return;
                }
                
               doAcceptGame(name, squadAccepted);
         
            }catch(Exception e){
                e.printStackTrace();
            }
            //else m_botAction.sendPrivateMessage(name, "A game is running already. Please wait");
        }
        
        else if(message.startsWith("!die") && op.isModerator(name))
            doDie(name, message);
        
        else if(message.startsWith("!stop"));
            //this.practice = null;
        //else if(message.startsWith("!squads"))
          //  doDisplaySquads(name, message);
    }

    private void doFaceOff() {
        // TODO Auto-generated method stub
        //Player p = m_botAction.getPlayer( m_botAction.getBotName() );
        Player p = m_botAction.getFuzzyPlayer(m_botAction.getBotName());
        
        m_botAction.splitTeam(0, 470, 474, 470, 594);
        m_botAction.splitTeam(1, 553, 474, 553, 549);
        
        /*m_botAction.getShip().setShip(0);
        m_botAction.getShip().setFreq(555);
        m_botAction.warpTo(p.getPlayerName(), 512, 512);
        
        m_botAction.getShip().move(512, 512);
    */
    }

    public void doReadyTeam(String name, String message){
        
    }
    
    public void addPlayerPoint(String namePlayer, int freq, int pointType){
    	mediator.addPlayerPoint(namePlayer, freq, pointType);
    }
    
    private void doAcceptGame(String name, String squadAccepted){
        mediator.startPractice(name, squadAccepted);
        
        
    }
    
    private void registerPlayer(String name, String message){
        //!register <ship>
        //0123456789S
        if(message.length() <= 9){
            m_botAction.sendPrivateMessage(name, "Use !register <ship> please.");
            return;
        }
        int ship = Integer.parseInt(message.substring(10));//check if hes in the squad
        mediator.addPlayer(name, ship, 0);
        //HockeyConcreteMediator.getInstance(m_botAction).addPlayer(name, ship);
        
    }
    /*
    public void doGetSavePoints(String name){
        int i = mediator.getSavePoint(name);
        int j = mediator.getNSave(name);
        m_botAction.sendArenaMessage("Name: "+name+" Points: "+i+" SAVES: "+j);
    }
    public void doGetGoalPoints(String name){
        int i = mediator.doGetGoalPoints(name);
        int j = mediator.doGetNGoalPoints(name);
        m_botAction.sendArenaMessage("Name: "+name+" Points: "+i+" goals: "+j);
    }
    */
    private void setFaceOffState() {
        //mediator = HockeyConcreteMediator.getInstance(m_botAction);
        mediator.setState(HockeyState.Face_Off);
        
        
    }
    
    public void createTeam(String name, String message){}
    
    public void doRegister(String name, String message){}

    private void requestGame(String name, String message) throws SQLException{
        m_botAction.sendPrivateMessage(name, "Adding...");
        HockeyRegistrator hockey = new HockeyRegistrator(m_botAction);
        hockey.requestGame(name, message);
    }
    
    private void doDie(String name, String message){
        m_botAction.sendPrivateMessage(name, "Bot disconnecting.");
        mediator.cancelTasks();
        m_botAction.cancelTasks();
        m_botAction.die();
        
    }
    
    public void requestEvents(EventRequester eventRequester){
        eventRequester.request(EventRequester.SOCCER_GOAL);
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.LOGGED_ON);
        eventRequester.request(EventRequester.BALL_POSITION);
        
    }
}
