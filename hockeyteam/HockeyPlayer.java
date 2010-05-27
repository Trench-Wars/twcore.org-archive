package twcore.bots.hockeybot.hockeyteam;

import twcore.core.BotAction;

public class HockeyPlayer{

    BotAction playerHandler;
    
    private String name;
    
    private int frequency;
    private int lagouts;
    private int registeredShip;

    private int playerID;
    
    private HockeyPlayerPoint playerPoint;
    private HockeyPlayerState playerState;
    private HockeyTeam  playerTeam;
    
    /*private int point [][];
    private int p = 0;
    */
    
    public HockeyPlayer(HockeyTeam team, String name, int ship, int frequency, BotAction botAction){
        
        this.playerTeam = team;
        this.playerHandler = botAction;
        this.lagouts = 0;
        
        setName(name);
        setRegisteredShip(ship);
        setFrequency(frequency);
        
        this.playerPoint = new HockeyPlayerPoint();
        this.playerState = new HockeyPlayerState();
        
        /*
        point = new int[4][4];
        point[0][0] = 0;
        point[0][1] = 0;
        point[1][0] = 0;
        point[1][1] = 0;*/
    }
    
    public void lagout(){
        setPlayerState(playerState.lagout);
    }
    
    public void setPlayerState(int state){
        playerState.setPlayerState(state);
        
    }
    
    public int getPlayerState(){
        return playerState.getPlayerState();
        
    }
    
    public void setPoint(double point){
        playerPoint.setPoint(point);
        
    }
    
    public void addPlayer(){
        
      
        if(playerHandler.getPlayer(name) == null)
            return;
        
        setPlayerState(playerState.in);
        setPoint(0);
        
        playerHandler.setShip(getName(), getRegisteredShip());
        playerHandler.setFreq(getName(), getFrequency());
        
    }
    
    /*public int getGoalPoints(){
        return this.point[0][1];
    }
    
    public int getNumberSaves(){
        return this.point[1][0];
    }
    public int getSavePoint(){
        return this.point[1][1];
    }
    public int getNumberGoals(){
        return this.point[0][0];
    }
    
    public void addSavePoint(int point){
        this.point[1][0]++;
        this.point[1][1]+=point;
    }
    public void addGoalPoint(int point){
        this.point[0][0]++;
        this.point[0][1]+=point;
    }*/
    
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    public int getRegisteredShip(){
    
        return registeredShip;
    }
    public void setRegisteredShip(int ship1) {
        this.registeredShip = ship1;
    }
    
    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public int getFrequency() {
        return frequency;
    }
    
    private class HockeyPlayerState{
        
        public static final int in = 0;
        public static final int lagout = 1;
        public static final int subbed = 2;
        public static final int out = 3;
        
        private int state;
        
        public HockeyPlayerState(){
            setPlayerState(-1);
            
        }
        public void setPlayerState(int state) {
            this.state = state;
        }

        public int getPlayerState() {
            return state;
        }
        
        
    }
    
    private class HockeyPlayerPoint{
        
        public static final int goal = 2;
        public static final int save = 1;
        public static final double assist = 0.5;
        private double point;
        
        public HockeyPlayerPoint(){
            setPoint(0);
            
        }
        public void setPoint(double point) {
            this.point = point;
        }
        public double getPoint() {
            return point;
        }
        
                
    }
    
}
