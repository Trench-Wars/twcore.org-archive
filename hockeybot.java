package twcore.bots.hockeybot;

import java.sql.SQLException;
import java.util.ArrayList;

import twcore.bots.hockeybot.hockeymediator.*;
import twcore.bots.hockeybot.hockeyregistrator.HockeyRegistrator;
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
 * 
 * falta combinar os estados de jogo com os eventos
 * */
/**
 * 
 * Need to work on Player states
 * Need to work on if(states)
 * Need to work on !pause ( face off pause state )
 * 
 * */

public class hockeybot
        extends SubspaceBot{

    /* Mediator pattern - the 'heart' of the game*/
    private HockeyConcreteMediator mediator;
    
    /*
     * List to stats: save, goal, assist ( 1st, 2nd )*/
    private HockeyAssistGoalStack<String> list;
    
    private ArrayList<String> twhtOps;
    
    private OperatorList op;
    private BotSettings m_botSettings;
    
    private EventRequester events;
    
    private String twhtHead;
    
    private String shortCmds[] = {
            "| Dee Dee's way:                                                                   |",
            "| !a <SquadName>               - to Accept a squad challenge                       |",
            "| !c <SquadName>               - to Challenge a squad                              |",
            "| !t <SquadName>               - to Create a Team                                  |",
            "| !r <Ship>                    - to register a ship in a game                      |",
            "| -------------------------------------------------------------------------------- |"
    };
    private String pubHelp [] = {
            "Hi, I'm a bot in development to the TW-Hockey-Tournament,",
            "you can register your squad already!",
            "| Commands -----------------------------------------------------------------------",
            "| !teamsignup <squadName>      -  Registers your squad on TWHT's site              |",
            "| !accept <SquadName>          -  To a accept a squad from the List of request     |",
            "| !challenge <SquadName>       -  To challenge a squad if you're capt/assist       |",
            "| !remove                      -  To remove your squad from requesting game's list |",
            "| !register                    -  To register a ship if a game is running          |",
            "| !twhtops                     - Shows you the list of TWHTOperators and Dev       |", 
            "| check TWHT'S Site: www.trenchwars.org/twht"
    };
    
    private String erHelp [] = {
            "| Mod Commands                                                                     |",
            "| !go <arena>                                                                      |"
    };
    
    private String twhtOpHelp [] = {
            "| TWHTOp Commands                                                                  |",  
            "| !cancelgame                  -  Cancels the current game                         |",
            "| !die                         -  Kills the bot                                    |"
    };
    
    private String twhtHeadHelp [] = {
            "| TWHTHead Commands                                                                |",
            "| !addop <opName>                                                                  |"
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
        this.twhtOps = new ArrayList<String>();
        this.m_botSettings = m_botAction.getBotSettings();
        
        String [] ops = m_botSettings.getString("TWHTOps").split(",");
        
        for( String st:ops )
            twhtOps.add(st);
        
        this.twhtHead = m_botSettings.getString("TWHTHead");
        
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
            /**
             * Trabalhar aqui para adicionar pontos a cada jogador
             * */
        if(mediator.gameIsRunning()){
            
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
                    addPlayerPoint(p_A1, freq, 2);
                }
                if(p_A2 != null){
                    m_botAction.sendArenaMessage("2nd Assist: "+p_A2);
                    addPlayerPoint(p_A2, freq, 2);
                }
                
                addPlayerPoint(p_Goal, freq, 1);
                setFaceOffState();
                updateScore(freq);
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
    }
    //}
    
	@Override
    public void handleEvent(BallPosition event){
        //gets the carrier and keeps getting him in a loop..
        
        /**
         * IF STATE == GAME IN PROGRESS
        */
	    if(mediator.gameIsRunning()){
    	       Player p; 
               String p_name;
               int p_freq;
               int p_freq2 = -1;
               
               if(event.getCarrier() == -1)
                   return;
               
               p = m_botAction.getPlayer(event.getPlayerID());
                
               /*if(p.getShipType() == 7 || p.getShipType() == 8){
                    list.clear();
                    m_botAction.sendArenaMessage("Save! "+p.getPlayerName());
                    addPlayerPoint(p.getPlayerName(), p.getFrequency(), 3);
                }
                */
               //else{
               if( p.getShipType() == 1 || p.getShipType() == 2 || p.getShipType() == 3 || p.getShipType() == 4
                       || p.getShipType() == 5 || p.getShipType() == 6 ){
                   
                   try{
                        p = m_botAction.getPlayer(event.getPlayerID());
                        p_freq = p.getFrequency();
                        p_name = m_botAction.getPlayerName(event.getPlayerID());
                        
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
                }
               
	            else if (p.getShipType() == 7 || p.getShipType() == 8)
               {
                   list.clear();
                   m_botAction.sendArenaMessage("Save! "+p.getPlayerName()) ;
                   addPlayerPoint(p.getPlayerName(), p.getFrequency(), 3);
               }
	        }
	    }
           // }
	    //else
	      //  return;
	//}
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
            
            try {
                handleCommand(name, message);
            } catch (SQLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
    }
    
    private void handleCommand(String name, String message) throws SQLException {
        
         if(message.startsWith("!help")){
            m_botAction.privateMessageSpam(name, this.pubHelp);
            if( op.isER(name) )
                m_botAction.privateMessageSpam(name, this.erHelp);
         
            if( twhtOps.contains(name) ){
                m_botAction.privateMessageSpam(name, this.twhtOpHelp);
            }
            
            if( this.twhtHead.equalsIgnoreCase(name) )
                m_botAction.privateMessageSpam(name, this.twhtHeadHelp);

            m_botAction.privateMessageSpam(name, this.shortCmds);
        }
        /**Players commands
         * */
        else if( message.startsWith("!twhtops"))
            showOps(name);

        else if( message.startsWith("!status"))
            showStatus(name);
        /**
         * Mod commands*/
        else if ( message.startsWith("!go") && op.isER(name)){
            //!go <>
            //01234
            if(message.length() < 4){
                m_botAction.sendPrivateMessage(name, "Please specify what arena: !go <arena>");
                return ;
            }
            else
                go(name, message.substring(4));
                
        }
        
        /**
         * Challenge, accept and register commands
         * */
        
        //Short Cut Key to !accept
        else if( message.charAt(1) == 'a' && message.charAt(2) == ' ')
        {
            //!a <squadname>
            //0123
            if( message.length() <= 3){
                m_botAction.sendPrivateMessage(name, "The shortcut key !a should be used with a <SquadName>: Eg.: !a DexterSquad");
                return ;
            }else
                this.acceptChallenge(name, message.substring(3) );
            
        }
        
        else if(message.startsWith("!accept")){
            //!accept <>
            //012345678
            if(message.length() <= 8){
                m_botAction.sendPrivateMessage(name, "Please use the command !accept <Squadname>");
                return ;
            }else
                acceptChallenge(name, message.substring(8));
            
        }
        
        //ShortCut Key to !register
        else if( message.charAt(1) == 'r' && message.charAt(2) == ' '){
            //!r <>
            //0123
            if( message.length() <= 3){
                m_botAction.sendPrivateMessage(name, "The shortcut key !r should be used with <Ship>: Eg.: !r 3 to register as spider");
                return;
            }else
                registerPlayer(name, message.substring(3) );
                
        }
        else if (message.startsWith("!register")){
            /**
             * IF GAME IN PROGRESS*/
            if(message.length() <= 9){
                m_botAction.sendPrivateMessage(name, "Use !register <ship> please.");
                return ;
            } 
            else
                registerPlayer(name, message.substring(10));
        }
        
        //ShortCut Key to !challenge
        else if( message.charAt(1) == 'c' && message.charAt(2) == ' '){
            //!c <>
            //0123
            if( message.length() <= 3){
                m_botAction.sendPrivateMessage(name, "The shortkut key !c should be used with <SquadName>: Eg.: !c DexterSquad");
                return ;
            }else
                challengeTeam( name, message.substring(3) );
                
        }
        else if( message.startsWith("!challenge") ){
            //!challenge <squadname>
            //0123456789TE
            if(message.length() < 11){
                m_botAction.sendPrivateMessage(name, "...Use !challenge <squadname> please, the full command that is.");
                return ;
            }else
                challengeTeam(name, message.substring(11));
        }   
        /**---------------------------------------------*/
        
        /**
         * TWHT-OP Commands
         * */
        else if(message.startsWith("!cancelgame") && this.twhtOps.contains(name.toLowerCase()))
            cancelGame(name, message);
        
        else if( message.startsWith("!score") && this.twhtOps.contains(name.toLowerCase()) )
            mediator.displayStatistics();
        
        else if ( message.startsWith("!addop"))
            //!addop
            //01234567
            this.addOp(name, message.substring(7) );
        /**
         * Registering squad commands
         * */
        //ShortCut Key to !TeamSignup
        else if( message.charAt(1) == 't' && message.charAt(2) == ' ')
        {
            if( message.length() <= 3){
                m_botAction.sendPrivateMessage(name, "Please, the shortcut to create a team is !t <TeamName>..Eg: !t DexterSquad");
                return ;
            }else
                registerSquad(name, message.substring(3) );
                
        }
        else if( message.startsWith("!teamsignup")){
            if(message.length() <= 12 ){
                m_botAction.sendPrivateMessage(name, "Please, use the command !teamsignup <squadName> to register a squad into TWHT.");
                return ;
            }else
                registerSquad(name, message.substring(12));
        }
        
  
        /**
         * During game commands */
        else if(message.startsWith("!ready")){
            readyTeam(name, message);
        }
        
                
        else if(message.startsWith("!die") && op.isModerator(name))
            doDie(name, message);
        
        else if(message.startsWith("!stop"));
    }

    public void acceptChallenge(String name, String squadAccepted){

        try{
            /**
             * IF GAME IN PROGRESS*/
            if(mediator.gameIsRunning() || mediator.isInRegisterTime() )
                m_botAction.sendPrivateMessage(name, "A game is already running. Please wait");
            
            else{
                mediator.acceptGame(name, squadAccepted);
            }
        }catch(SQLException e){
            Tools.printLog(e.toString());
        }
        catch(Exception e){
            Tools.printLog(e.toString());
        }
    }
    public void challengeTeam(String name, String squadName) throws SQLException{
        
        if( mediator.gameIsRunning() || mediator.isInRegisterTime() )
            m_botAction.sendPrivateMessage(name, "Couldn't challenge, a game is running on here already!");
        else
            mediator.challenge(name, squadName);
    }
    
    public void readyTeam(String name, String message) throws SQLException{
       
        if(mediator.isInRegisterTime())
            mediator.readyTeam(name, message);
        else
            m_botAction.sendPrivateMessage(name, "Just use !ready in PRE Start please");
        
    }
    
    public void addPlayerPoint(String namePlayer, int freq, int pointType){
    	mediator.addPlayerPoint(namePlayer, freq, pointType);
    }
    
    private void registerSquad(String name, String squadName){
        try{
            //!teamsignup squadname
            //0123456789TE
            //0123456789DOD
            HockeyRegistrator registrator;
            registrator = new HockeyRegistrator(m_botAction);
            registrator.createTeam(name, squadName);
            
        }catch(SQLException e){
            e.printStackTrace();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void registerPlayer(String name, String ship) throws SQLException{
        //!register <ship>
        //0123456789S
        if(mediator.isInRegisterTime()){
            int shipNumber = Integer.parseInt(ship);//check if hes in the squad
            mediator.addPlayer(name, shipNumber);
        }
        
        else{
            m_botAction.sendPrivateMessage(name, "Couldn't register you in. Register time has expired.");
            return;
        }
    }
    
    private void updateScore(int freq){
        mediator.updateScore(freq);
    }
    
    private void setFaceOffState() {
        mediator.setState(HockeyState.Face_Off);
    }
    
    private void cancelGame(String name, String message){
        
        if(mediator.gameIsRunning() || mediator.isInFaceOffOrInterval() || mediator.isInRegisterTime()){
            mediator.cancelGame();
            m_botAction.sendArenaMessage("Game canceled by "+name);
        }
        else
            m_botAction.sendPrivateMessage(name, "TWHT-Op, don't cancel a game if there is no one running!");
    }
    
    private void go(String name, String arena){
        m_botAction.sendRemotePrivateMessage(name, "Going to "+arena);
        m_botAction.cancelTasks();
        mediator.cancelGame();
        m_botAction.changeArena(arena);
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
    
    public void addOp(String name, String opName){
        m_botSettings.put("TWHTOps", opName);
        this.twhtOps.add(opName);
        m_botAction.sendPrivateMessage(name, "Added "+opName+" to the TWH-OPList");
        
    }
    public void showStatus(String name){
        
        if(mediator.gameIsRunning() || mediator.isInRegisterTime() || mediator.isInFaceOffOrInterval())
            mediator.showStatus(name);
        else
            m_botAction.sendPrivateMessage(name, "There is no game happening atm.");
        
    }
    public void showOps(String name){

        m_botAction.sendPrivateMessage(name, "=============================");
        m_botAction.sendPrivateMessage(name, "| TWH-Operators");
        for(String st:this.twhtOps)
            m_botAction.sendPrivateMessage(name, "| "+st);

        m_botAction.sendPrivateMessage(name, "=============================");
    }
}
