package twcore.bots.hockeybot;

import twcore.core.BotAction;

public class HockeyPlayer {

    BotAction playerHandler;
    
    private String name;
    
    private int registeredShip;
    
    private int frequency;
    private int playerState;
    
    private int playerID;
    
    private HockeyPoints playerPoints;
    /*States ship
     * */
    private static final int IN = 0;
    private static final int LAGOUT = 1;
    
    public HockeyPlayer(String name, int ship, int frequency){
        setName(name);
        setRegisteredShip(ship);
        setFrequency(frequency);
        playerHandler.scoreReset(name);
        
        playerPoints = new HockeyPoints();
        
    }
    
    public void addPlayer(){
        playerState = IN;
        
        if(playerHandler.getPlayer(name) == null)
            return;
        
        playerHandler.setShip(getName(), getRegisteredShip());
        playerHandler.setFreq(getName(), getFrequency());
        
    }
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
    
    private class HockeyPoints{
        
        private final int goal = 2;
        private final int save = 1;
        
        private final double assist = 0.5;

        public int getSavePoint() {
            return save;
        }

        public int getGoalPoint() {
            return goal;
        }

        public double getAssistPoint() {
            return assist;
        }
        
    }
    
}
