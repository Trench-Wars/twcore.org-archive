package twcore.bots.hockeybot.hockeyteam;

import twcore.core.BotAction;

public class HockeyPlayer{

    /**
     * Falta trabalhar os estados do jogador: se ele sair, diminuir a quantidade de jogadores no time ( remover do mapa )
     * Substituições, tackle e save.
     * Falta adicionar os pontos na tabela
     *
     * */
    BotAction playerHandler;
    
    private String name;
    
    private int frequency;
    private int lagouts;
    private int registeredShip;

    private int playerID;
    
    private HockeyPlayerPoint playerPoint;
    private HockeyPlayerState playerState;
    private HockeyTeam  playerTeam;

    public HockeyPlayer(HockeyTeam team, String name, int ship, int frequency, BotAction botAction){
        
        this.playerTeam = team;
        this.playerHandler = botAction;
        this.lagouts = 0;
        
        setName(name);
        setRegisteredShip(ship);
        setFrequency(frequency);
        
        this.playerPoint = new HockeyPlayerPoint();
        this.playerState = new HockeyPlayerState();
 
    }
    
    public double getPoint(){
        return this.playerPoint.getPoint();
    }
    public int getNumberOfGoals(){
        return this.playerPoint.getNumberOfGoals();
    }
    public int getNumberOfAssists(){
    	return this.playerPoint.getNumberOfAssists();
    }
    public int getNumbersOfSaves(){
    	return this.playerPoint.getNumberOfSaves();
    }
    public int getTeamIndex(){
    	return playerTeam.getTeamFreq();
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
    
    public void addGoalPoint()
    {
    	this.playerPoint.setNumberOfGoals(this.playerPoint.getNumberOfGoals()+1);
    	this.playerPoint.setPoint(playerPoint.getPoint()+ HockeyPlayerPoint.goal);
    	System.out.println("Passei por aqui, adicionei mais um gol!");
    }
    
    public void addAssistPoint()
    {
        System.out.println("Passei por aqui, adicionei mais um assist!");
        
    	this.playerPoint.setNumberOfAssists(this.playerPoint.getNumberOfAssists()+1);
    	this.playerPoint.setPoint(this.playerPoint.getPoint()+ HockeyPlayerPoint.assist);
    }
    
    public void addSavePoint()
    {

        System.out.println("Passei por aqui, adicionei mais um save!");
    	this.playerPoint.setNumberOfSaves(this.playerPoint.getNumberOfSaves()+1);
    	this.playerPoint.setPoint(this.playerPoint.getPoint()+ HockeyPlayerPoint.save);
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
        
        public static final int goal = 20;
        public static final int save = 100;
        public static final double assist = 0.5;
        
        private double point;
        
        private int numberOfGoals;
        private int numberOfSaves;
        private int numberOfAssists;
        
        public HockeyPlayerPoint(){
            setPoint(0);
            setNumberOfGoals(0);
            setNumberOfAssists(0);
            setNumberOfSaves(0);
            
        }
        public void setPoint(double point) {
            this.point = point;
        }
        public double getPoint() {
            return point;
        }
		public void setNumberOfGoals(int numberOfGoals) {
			this.numberOfGoals = numberOfGoals;
		}
		public int getNumberOfGoals() {
			return numberOfGoals;
		}
		public void setNumberOfSaves(int numberOfSaves) {
			this.numberOfSaves = numberOfSaves;
		}
		public int getNumberOfSaves() {
			return numberOfSaves;
		}
		public void setNumberOfAssists(int numberOfAssists) {
			this.numberOfAssists = numberOfAssists;
		}
		public int getNumberOfAssists() {
			return numberOfAssists;
		}
        
                
    }
    
}
