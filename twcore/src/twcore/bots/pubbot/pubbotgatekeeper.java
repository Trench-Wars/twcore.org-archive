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
	 * Check for *lag message
	 */
	public void handleEvent(Message event) {

		String message = event.getMessage();
		
		if (event.getMessageType() == Message.ARENA_MESSAGE)
		{
			m_botAction.sendPublicMessage("Arena message!");
			// Received from a *lag
			if (message.startsWith("PING Current:"))
			{
				m_botAction.sendPublicMessage("Lag check!");
				// If the player is from another zone, the lag info will say 0 ms
				String pieces[] = message.split(" ");
				if (pieces.length>3 && pieces[4].equals("0")) {
					m_botAction.sendPublicMessage("Inter-zone!");
					m_botAction.sendChatMessage(2, "INTER-ZONE: " + lastPlayer + " (" + m_botAction.getArenaName() + ")");
				}
			}
		}
	
	}

	public void handleEvent(PlayerEntered event) {
		Player player = m_botAction.getPlayer(event.getPlayerID());
		m_botAction.sendUnfilteredPrivateMessage(player.getPlayerName(), "*lag");
		lastPlayer = player.getPlayerName();
		m_botAction.sendPublicMessage("Check!");
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
