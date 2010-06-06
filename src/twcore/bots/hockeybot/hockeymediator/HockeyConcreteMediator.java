package twcore.bots.hockeybot.hockeymediator;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TimerTask;

import twcore.bots.hockeybot.hockeydatabase.HockeyDatabase;
import twcore.bots.hockeybot.hockeyteam.HockeyTeam;
import twcore.core.BotAction;
import twcore.core.game.Player;
import twcore.core.util.Tools;


/**
 * @author Dexter
 *
 * Hockey - Mediator design pattern
 * 
 * hockey concrete mediator is the heart of the game. 
 * 
 * It communicates to all the other classes: teams, practice, clock, states
 *  
 * */    
public class HockeyConcreteMediator implements HockeyMediator {

    private     HockeyState     stateController;
    private     HockeyTicker    ticker;
    private     HockeyTeam      teams[];
    private     BotAction       m_botAction;
    private     HockeyDatabase  sql;
    private     String          faceOffPlayer;
    private     ArrayList<GameRequest> gameRequest;
    private     HashMap<String, Integer> freqteam;

    private     KeepAliveConnection keepAliveConnection;
    
    
    public HockeyConcreteMediator(BotAction botAction){
       
        try {
            sql = new HockeyDatabase(botAction);
            
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        this.m_botAction = botAction;

        stateController = new HockeyState();
        stateController.setState(HockeyState.OFF);
        stateController.setMediator(this);
        
        freqteam = new HashMap<String, Integer>();
        
        gameRequest = new ArrayList<GameRequest>();
        
        keepAliveConnection = new KeepAliveConnection();
        m_botAction.scheduleTaskAtFixedRate( keepAliveConnection, 100, Tools.TimeInMillis.MINUTE*2);
        
    }
    
    //------------------------------------------------------------------
    
    public void challenge(String challenger, String sqdChallenged) throws SQLException{
        String sqdChallenger;
        sqdChallenger = /*"Dex";*/getCaptainTeamName(challenger);
        
        if( sqdChallenger == null)
        {    
            m_botAction.sendPrivateMessage(challenger, "Couldn't challenge, you are not Assistant / Captain of a squad");
            return ; 
        }
        if( sqdChallenger.toLowerCase().equals(sqdChallenged.toLowerCase()))
            m_botAction.sendPrivateMessage(challenger, "You can't challenge your own squad...");
        
        else if( !sql.isTeam(sqdChallenged))
            m_botAction.sendPrivateMessage(challenger, sqdChallenged+" is not a TWHT Squad.");
            
        else{
            gameRequest.add(new GameRequest( challenger, sqdChallenger, sqdChallenged ) );
            m_botAction.sendSquadMessage(sqdChallenged, challenger+" from "+sqdChallenger + " is challenging you for a Hockey Game in ?go "+m_botAction.getArenaName()+" ... come !accept "+sqdChallenger);
            m_botAction.sendPrivateMessage(challenger, "You challenged "+sqdChallenged+"!");
            }
    }

    /**
     * Commands
     * 
     * !accept <team>
     * !ready
     * !add (put)
     * !register <ship>
     * 
     * should be made:
     * !challenge <team>
     * @throws SQLException 
     * */
    
    //team 1 accepting team 2
    public void acceptGame(String captainTeamAccepter, String teamAccepted) throws SQLException{
        //!accept <team>
        String teamAccepter = /*"eumesmo";*/getCaptainTeamName(captainTeamAccepter);
        
        //startGame(captainTeamAccepter, teamAccepter, teamAccepted);
        if(teamAccepter == null){
            m_botAction.sendPrivateMessage(captainTeamAccepter, "You're not captain/assistant to accept a challenge");
            return;
        }
      
        int i = 0;
       
        for(GameRequest gr: gameRequest){
        
            if( gr.getSqdChallenged().equalsIgnoreCase(teamAccepter) && gr.getSqdChallenger().equalsIgnoreCase(teamAccepted) ){
                gameRequest.remove(gr);
                i++;
                startGame(captainTeamAccepter, gr.getRequester(), gr.getSqdChallenged(), gr.getSqdChallenger());
                break;
            }
        }      
        if( i == 0 )
            m_botAction.sendPrivateMessage(captainTeamAccepter, teamAccepted+" has not challenged your squad...");
        
        
    }
    
    public boolean isReady(int frequence){
        return teams[frequence].isReady();
    }

    /*
     * To ready 
     * ---------------------------------------------------------------
     * */
    public void readyTeam(String captainName, String message) throws SQLException{
        
        String teamReadyCapt = /*"Dex";*/getCaptainTeamName(captainName);
        int freq = -1;
        
        if( teamReadyCapt == null ){
            m_botAction.sendPrivateMessage(captainName, "You're not captain / assistant to !ready");
            return;
        }
      
        if( isPlaying(teamReadyCapt) ){
            freq = getTeamPlayingFrequency(teamReadyCapt);
            teams[freq].setReady(true);
            m_botAction.sendPrivateMessage(captainName, "You've ready your team.");
            checkReady();
            }
        else
            m_botAction.sendPrivateMessage(captainName, "Your squad is not playing this match at the moment");
        
    }
    
    private void checkReady(){
        if( teams[0].isReady() && teams[1].isReady())
        {
            
            setState(HockeyState.Game_In_Progress);
        }
    }
    public boolean isPlaying(String teamName){
        return teams[0].getTeamName().toLowerCase().equals(teamName.toLowerCase()) || teams[1].getTeamName().toLowerCase().equals(teamName.toLowerCase());
    }

    public int getTeamPlayingFrequency(String teamName){
        if( teams[0].getTeamName().equalsIgnoreCase(teamName) )
            return 0;
        
        else if ( teams[1].getTeamName().equalsIgnoreCase(teamName.toLowerCase()) )
            return 1;
        
        return -1;
    }


    public String getSquadChat(String teamName) throws SQLException{
        return sql.getChat(teamName);
    }
    
    /*
     * To ready*/
    
    public String getCaptainTeamName(String playerName) throws SQLException{
        //do the query
        
        return /*"Dex";*/sql.getCaptainTeamName(playerName);
    }
    
    public String getPlayerTeamName(String playerName) throws SQLException{
        return sql.getPlayerTeamName(playerName);
    }
    
    
    //------------------------------------------------------------------

    public void startGame(String captainTeamAccepter, String requester, String teamAccepter, String teamAccepted) throws SQLException{

        String chatTeamAccepter = getSquadChat( teamAccepter );
        String chatTeamAccepted = getSquadChat( teamAccepted );
        
        //3 cases for chat game starting tells
        if( chatTeamAccepter != null && chatTeamAccepted != null){
            m_botAction.sendUnfilteredPublicMessage("?chat="+chatTeamAccepted+","+chatTeamAccepter);
            m_botAction.sendChatMessage(1, "GO GO GO! Your squad has a match now against "+teamAccepter+" .. come ?go "+m_botAction.getArenaName()+" to play!");
            m_botAction.sendChatMessage(2, "GO GO GO! Your squad has a match now against "+teamAccepted+" .. come ?go "+m_botAction.getArenaName()+" to play!");
            
        }
        
        else if( chatTeamAccepter != null)
        {
            m_botAction.sendUnfilteredPublicMessage("?chat="+chatTeamAccepter);
            m_botAction.sendChatMessage("GO GO GO! Your squad has a match now against "+teamAccepted+" .. come ?go "+m_botAction.getArenaName()+" to play!");
        }
        
        else if( chatTeamAccepted != null){
            m_botAction.sendUnfilteredPublicMessage("?chat="+chatTeamAccepted);
            m_botAction.sendChatMessage("GO GO GO! Your squad has a match now against "+teamAccepter+" .. come ?go "+m_botAction.getArenaName()+" to play!");
        }
        
        m_botAction.sendUnfilteredPublicMessage("?chat=");
        m_botAction.specAll();
        m_botAction.cancelTasks();
        m_botAction.resetTimer();
        m_botAction.toggleLocked();
        
        stateController.setState(HockeyState.Pre_Start);
       
        m_botAction.sendArenaMessage("A game between "+teamAccepter+" and "+teamAccepted+" is starting!", 2);
        m_botAction.sendArenaMessage("Pre Start: players from those squads, use !register or !r <ship> to play", 2);
        
        ticker = new HockeyTicker();
        ticker.setMediator(this);
        m_botAction.scheduleTask( ticker , 100, Tools.TimeInMillis.SECOND);
        
        teams = new HockeyTeam[2];
        
        teams[0] = new HockeyTeam(0, teamAccepter, m_botAction);
        teams[0].setCaptainName(captainTeamAccepter);
        freqteam.put(teamAccepter.toLowerCase(), 0);
        
        teams[1] = new HockeyTeam(1, teamAccepted, m_botAction);
        teams[1].setCaptainName(requester);
        freqteam.put(teamAccepted.toLowerCase(), 1);
        
        //teams[1].setReady(true);
        //teams[0].setReady(true);
        
    }
    
    public void showStatus(String name){
        
        int mins = (int) ticker.getMins();
        int secs = (int) ticker.getSecs();
        
        if(gameIsRunning() || isInFaceOffOrInterval() ){
            m_botAction.sendPrivateMessage(name, "Current game: "+teams[0].getTeamName()+" ( "+teams[0].getTeamGoals()+" goals) vs "+teams[1].getTeamName()+" ( "+teams[1].getTeamGoals()+" goals) "+"( Time: "+mins+":"+secs+" )");}
        
        else if(isInRegisterTime())
            m_botAction.sendPrivateMessage(name, "Pre Start: players are still registering in ( Time: "+mins+":"+secs+" )");
        else
            m_botAction.sendPrivateMessage(name, "Error");
    }
    public void cancelGame() {
   
        m_botAction.cancelTasks();
        m_botAction.resetTimer();
        m_botAction.toggleLocked();
        m_botAction.cancelTask(ticker);
        stateController.setState(HockeyState.OFF);
        freqteam.clear();
        //cleanTeams();
        
    }
    
    @Override
    public void setState(int state) {
        // TO  Auto-generated method stub
        switch(state){
            case HockeyState.Game_In_Progress: doGameInProgress(); break;
            case HockeyState.In_Interval: doInterval(); break;
            case HockeyState.Face_Off: doFaceOff(); break;
            case HockeyState.End_Game: doEndGame(); break;
        }
        /*
        if(state == HockeyState.Game_In_Progress){
            
            if(isReady(0) && isReady(1) || ( teams[0].hasMin() && teams[1].hasMin() ) ){
                stateController.setState(state);
                ticker.resetTime();
                m_botAction.sendArenaMessage("State set to "+state);
            }
            
            else{
                try {
                    cancelGame();
                    stateController.setState(HockeyState.OFF);
                    m_botAction.sendArenaMessage("Not enough players to start");
                } catch (Throwable e) {
                    // TO  Auto-generated catch block
                    e.printStackTrace();
                }
            }
            //team1.setReady(true);
            //team2.setReady(true);
        }
        
        else if(state == HockeyState.In_Interval){
            stateController.setState(state);
            
            getBack = new TimerTask(){
                public void run(){
                     startBack();
                }
            };
            
            m_botAction.scheduleTask(getBack, Tools.TimeInMillis.MINUTE*1);
            m_botAction.sendArenaMessage("INTERVAL! 1 min and game starts back!", 2);
            //INVERT TEAMS POSITIONS
            
        }
        
        else if(state == HockeyState.Face_Off){
            stateController.setState(state);
            
            getBack = new TimerTask(){
                public void run(){
                     startBack();
                }
            };
            
            m_botAction.scheduleTask(getBack, Tools.TimeInMillis.SECOND*30);
            m_botAction.sendArenaMessage("Face off! 30 secs and time runs again!", 2);
        }
        
        else if(state == HockeyState.End_Game){
            stateController.setState(state);
            displayStatistics();
            cleanTeams();
            stateController.setState(HockeyState.OFF);
        }*/
        
    }
    
    /// states ///
    //===============================================
    private void doGameInProgress(){
        
        if(isReady(0) && isReady(1) || ( teams[0].hasMin() && teams[1].hasMin() ) ){
            TimerTask t;
            m_botAction.sendArenaMessage("Hockey game will start in 15 secs");
            m_botAction.sendArenaMessage("Get the puck and face off during it!", 1);
            stateController.setState(HockeyState.Game_In_Progress);
            
            t = new TimerTask(){
              public void run(){
                  ticker.resetTime();
                  m_botAction.scoreResetAll();
                  m_botAction.shipResetAll();
                  m_botAction.sendArenaMessage("GO GO GO", 104);
              }
            };
            m_botAction.scheduleTask(t, Tools.TimeInMillis.SECOND*15);
        }
        else{
            cancelGame();
            stateController.setState(HockeyState.OFF);
            m_botAction.sendArenaMessage("Not enough players to start! :(");
            }
    }
    
    private void doInterval(){

        TimerTask getBack;
        stateController.setState(HockeyState.In_Interval);
        
        getBack = new TimerTask(){
            public void run(){
                 startBack();
                 m_botAction.sendArenaMessage("GO GO GO ( Period 2 )", 104);
            }
        };
        m_botAction.scheduleTask(getBack, Tools.TimeInMillis.MINUTE*2);
        m_botAction.sendArenaMessage("INTERVAL! 2 mins and game starts back!", 2);
        //INVERT TEAMS POSITIONS
        
    }
    
    private void doFaceOff(){
        
        TimerTask getBack;
        stateController.setState(HockeyState.Face_Off);
        
        getBack = new TimerTask(){
            public void run(){
                 startBack();
                 m_botAction.sendArenaMessage("GO GO GO", 104);

                 /*if(faceOffPlayer == null)
                     return;
                 Player p = m_botAction.getPlayer(faceOffPlayer);
                 int ship = p.getShipType();
                 m_botAction.setShip(faceOffPlayer, 0);
                 m_botAction.setShip(faceOffPlayer, ship);
                 */
            }
        };
        m_botAction.scheduleTask(getBack, Tools.TimeInMillis.SECOND*30);
        m_botAction.sendArenaMessage("Face off! 30 secs and time runs again!", 2);
    }
    
    private void doEndGame(){
        
        stateController.setState(HockeyState.End_Game);
        displayStatistics();
        //cleanTeams();
        freqteam.clear();
        stateController.setState(HockeyState.OFF);
    }
    //==================================================
    
    public int getCurrentState(){
        return stateController.getCurrentState();
    }

    public boolean isInRegisterTime(){
        return stateController.getCurrentState() == HockeyState.Pre_Start;
    }

    public boolean gameIsRunning(){
        return stateController.getCurrentState() == HockeyState.Game_In_Progress;
    }
    
    public boolean isInFaceOffOrInterval(){
        return stateController.getCurrentState() == HockeyState.Face_Off || stateController.getCurrentState() == HockeyState.In_Interval; 
    }
    public void cleanTeams(){
        teams[0].resetPlayers();
        teams[1].resetPlayers();
        //teams[0] = null;
        //teams[1] = null;
    }
    
    public void notifyTime(long mins, long secs){
    
        //m_botAction.showObject((int)mins);
        //m_botAction.showObject((int) (30+secs));
        m_botAction.sendArenaMessage("Mins: "+mins+" Secs: "+secs);
        
    }
    
    public void startBack(){
        stateController.setState(HockeyState.Game_In_Progress);
        ticker.startBack();
       
    }
    public void pauseGame(){
        ticker.pause();
        
    }
    public void addPlayer(String name, int ship) throws SQLException { 
        // TO  Auto-generated method stub
        String teamNamePlaying = getPlayerTeamName(name);
        
        if( !freqteam.containsKey(teamNamePlaying.toLowerCase()) ){
            m_botAction.sendPrivateMessage("Dexter", "team "+teamNamePlaying+" not in, lower case problem?");
            m_botAction.sendPrivateMessage(name, teamNamePlaying+" is not playing this match, "+name+".");
            return;
        }
        int freq = freqteam.get(teamNamePlaying.toLowerCase());
        
        //int freq = getTeamPlayingFrequency( getPlayerTeamName(name) );
        
        
        //This if is not needed if I keep the hashmap. just in future case
        if(freq == -1){
            m_botAction.sendPrivateMessage(name, "Your hockey squad is not playing this match...");
            return ;
        }
        
        else{
            if(teams[freq].isFull()){
                m_botAction.sendPrivateMessage(name, "Team has 6 players already.");
                return;
            }
            else if(teams[freq].Contains(name)){
                m_botAction.sendPrivateMessage(name, "You're already registered in...wtf are you  ing?");
                return;
            }
            else{
                m_botAction.setShip(name, ship);
                m_botAction.setFreq(name, freq);
            
                teams[freq].addPlayer(name, ship);
                m_botAction.sendArenaMessage(name+" is registered on ship "+ship+ " for "+teams[freq].getTeamName());
            }
        }
    }
    
    public void putPlayerFaceOff(){
        
        if(faceOffPlayer == null)
            return;
        
        m_botAction.warpTo(faceOffPlayer, 512, 512);
        m_botAction.sendArenaMessage(faceOffPlayer+" get the puck and face off please");
        Player p = m_botAction.getPlayer(faceOffPlayer);
        int ship = p.getShipType();
        m_botAction.setShip(faceOffPlayer, 0);
        m_botAction.setShip(faceOffPlayer, ship);
    
    }
    public void lagout(String name){
        
    }

	@Override
	public void addPlayerPoint(String namePlayer, int freq, int pointType) {
		// TO  Auto-generated method stub
		final int typeGoal = 1;
		final int typeAssist = 2;
		final int typeSave = 3;
			
		switch (pointType){
			case typeGoal: addGoalPoint(namePlayer, freq); break;
			case typeAssist: addAssistPoint(namePlayer, freq); break;
			case typeSave: addSavePoint(namePlayer, freq); break;
		}
	}

	private void addSavePoint(String namePlayer, int freq) {
		// TO  Auto-generated method stub
		teams[freq].addSavePoint(namePlayer);
	}

	private void addAssistPoint(String namePlayer, int freq) {
		// TO  Auto-generated method stub
		teams[freq].addAssistPoint(namePlayer);
	}

	private void addGoalPoint(String namePlayer, int freq) {
		// TO  Auto-generated method stub
		teams[freq].addGoalPoint(namePlayer);
	}
	
	public void cancelTasks(){
	    this.m_botAction.cancelTasks();
	}

	@Override
	public void updateScore(int freq) {
		// TO  Auto-generated method stub
		teams[freq].setTeamGoals(teams[freq].getTeamGoals()+1);
	}


	@Override
	public void displayStatistics() {
		// TABLE RESULT
    	ArrayList<String> spam = new ArrayList<String>();
    	spam.add(" _______________________________________________________________");
    	spam.add("|                   Results / Statistics                        |");
    	spam.add("|                                                               |");
    	spam.add("|Team 1______ "+teams[0].getTeamGoals()+"/************************************************|");
    	spam.add("|                       |Goals  |Saves  |Assists|  Total Points |");
    	        
    	spam.add("|"+teams[0].getTeamName());
    	
    	spam.add("|---------------------------------------------------------------|");
    
    	spam.addAll(teams[0].displayStatistics());
    	
    	spam.add("|---------------------------------------------------------------|");
    	
        spam.add("|Team 2______ "+ teams[1].getTeamGoals()+"/************************************************|");
        spam.add("|                       |Goals  |Saves  |Assists|  Total Points |");
        spam.add("|"+teams[1].getTeamName());
        
        spam.add("|---------------------------------------------------------------|");
        
    	spam.addAll(teams[1].displayStatistics());
    	
    	spam.add("|---------------------------------------------------------------|");
    	spam.add("________________________________________________________________");
        m_botAction.arenaMessageSpam(spam.toArray(new String[spam.size()]));
    	
    }
	
	private class KeepAliveConnection extends TimerTask{   
	    public void run(){
	        sql.keepAlive();
	    }
	}
}
