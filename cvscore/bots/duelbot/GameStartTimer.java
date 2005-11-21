package twcore.bots.duelbot;

import java.util.TimerTask;

import twcore.core.BotAction;

public class GameStartTimer extends TimerTask {
	
	BotAction m_botAction;
	String player1;
	String player2;
	Duel duel;
	
	public GameStartTimer( Duel d, String p1, String p2, BotAction b ) {
		m_botAction = b;
		duel = d;
		player1 = p1;
		player2 = p2;
	}
	
	public void run() {
		duel.started();
		m_botAction.warpTo( player1, duel.getXOne(), duel.getYOne() );
    	m_botAction.warpTo( player2, duel.getXTwo(), duel.getYTwo() );
    	m_botAction.sendPrivateMessage( player1, "GO GO GO", 104);
    	m_botAction.sendPrivateMessage( player2, "GO GO GO", 104);
	}
}