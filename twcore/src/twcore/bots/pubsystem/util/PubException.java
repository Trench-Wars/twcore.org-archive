package twcore.bots.pubsystem.util;

public class PubException extends Exception {

	private String message;
	
	public PubException(String message) {
		this.message = message;
	}
	
	public String getMessage() {
		return message;
	}
	
}
