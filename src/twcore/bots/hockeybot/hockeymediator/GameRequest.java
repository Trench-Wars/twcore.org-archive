package twcore.bots.hockeybot.hockeymediator;

public class GameRequest {

    String sqdChallenger;
    String sqdChallenged;
    String requester;
    
    public GameRequest(String requester, String sqdChallenger, String sqdChallenged){
        this.sqdChallenged = sqdChallenged;
        this.sqdChallenger = sqdChallenger;
        this.requester = requester;
    }

    public String getSqdChallenger() {
        return sqdChallenger;
    }

    public void setSqdChallenger(String sqdChallenger) {
        this.sqdChallenger = sqdChallenger;
    }

    public String getSqdChallenged() {
        return sqdChallenged;
    }

    public void setSqdChallenged(String sqdChallenged) {
        this.sqdChallenged = sqdChallenged;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }
    
    
}
