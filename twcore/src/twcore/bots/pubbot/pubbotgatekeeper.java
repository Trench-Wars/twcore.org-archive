package twcore.bots.pubbot;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.game.Player;

/**
 * This bot is used to check inter-zone arena
 * If a player comes from another zone, a message will be displayed on the robochat
 */
public class pubbotgatekeeper extends PubBotModule {

	private String lastPlayer = "";

	/**
	 * Requests the necessary events for this module to work properly
	 */
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.MESSAGE);
		eventRequester.request(EventRequester.PLAYER_ENTERED);
	}

	/**
	 * Check for *info message
	 */
	public void handleEvent(Message event) {

		String message = event.getMessage();
		
		if (event.getMessageType() == Message.ARENA_MESSAGE)
		{
			// Received from a *info
			if (message.startsWith("Ping:0ms"))
			{
				m_botAction.sendPublicMessage("Inter-zone!");
				m_botAction.sendChatMessage(2, "INTER-ZONE: " + lastPlayer + " (" + m_botAction.getArenaName() + ")");
			}
		}
	
	}

	public void handleEvent(PlayerEntered event) {
		Player player = m_botAction.getPlayer(event.getPlayerID());
		m_botAction.sendUnfilteredPrivateMessage(player.getPlayerName(), "*info");
		lastPlayer = player.getPlayerName();
	}

	@Override
	public void cancel() {
		// TODO Auto-generated method stub
	}

	@Override
	public void initializeModule() {
		// TODO Auto-generated method stub
	}

}
