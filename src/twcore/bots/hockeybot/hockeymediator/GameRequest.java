package twcore.bots.hockeybot.hockeymediator;

public class GameRequest {

    String sqdChallenger;
    String sqdChallenged;
    String requester;
    
    int idSqdChallenger;
    int idSqdChallenged;
    
    String chatSqdChallenger, chatSqdChallenged;
    
    public GameRequest(String requester, String sqdChallenger, String sqdChallenged, int idSqdChallenger, int idSqdChallenged,
            String chatSqdChallenger, String chatSqdChallenged){
        this.sqdChallenged = sqdChallenged;
        this.sqdChallenger = sqdChallenger;
        this.requester = requester;
        this.idSqdChallenger = idSqdChallenger;
        this.idSqdChallenged = idSqdChallenged;
        this.chatSqdChallenger = chatSqdChallenger;
        this.chatSqdChallenged = chatSqdChallenged;
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

    public String getChatSqdChallenger() {
        return chatSqdChallenger;
    }

    public void setChatSqdChallenger(String chatSqdChallenger) {
        this.chatSqdChallenger = chatSqdChallenger;
    }

    public String getChatSqdChallenged() {
        return chatSqdChallenged;
    }

    public void setChatSqdChallenged(String chatSqdChallenged) {
        this.chatSqdChallenged = chatSqdChallenged;
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
    
    public int getIdsqdChallenger() {
        return idSqdChallenger;
    }

    public void setIdsqdChallenger(int idsqdChallenger) {
        this.idSqdChallenger = idsqdChallenger;
    }

    public int getIdSqdChallenged() {
        return idSqdChallenged;
    }

    public void setIdSqdChallenged(int idSqdChallenged) {
        this.idSqdChallenged = idSqdChallenged;
    }

    
}
