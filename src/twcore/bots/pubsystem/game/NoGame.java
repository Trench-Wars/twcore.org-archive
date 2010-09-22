package twcore.bots.pubsystem.game;

public class NoGame extends AbstractGame {

	public NoGame() {
		super("NoGame");
	}

	@Override
	public boolean isIdle() {
		return false;
	}

	@Override
	public void start(String argument) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stop() {

	}

	@Override
	public void die() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void handleCommand(String sender, String command) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void handleModCommand(String sender, String command) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void statusMessage(String playerName) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isStarted() {
		return false;
	}

}
