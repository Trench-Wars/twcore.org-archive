package twcore.bots.robohelp;

public class NewbCall extends Call {

    static final int FREE = 0;
    static final int TAKEN = 1;
    static final int FALSE = 2;
    
    
    public NewbCall(String player) {
        super();
        this.playerName = player;
        this.message = "";
        this.claimer = "";
        this.taken = FREE;
    }
    
    public void claim(String name) {
        claimer = name;
        taken = TAKEN;
    }
    
    public String getName() {
        return playerName;
    }

    public void falsePos() {
        taken = FALSE;
        claimer = "[FALSE-POSITIVE]";
    }

}