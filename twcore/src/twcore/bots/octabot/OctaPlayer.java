package twcore.bots.octabot;

import twcore.core.BotAction;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FlagDropped;
import twcore.core.events.PlayerDeath;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class OctaPlayer {
	
	private String m_name;
	
	private int m_timeStart;
	private int m_freq;
	
	private int m_kill = 0;
	private int m_teamKill = 0;
	private int m_death = 0;
	
	private int m_flagKill = 0;
	private int m_flagDeath = 0;
	
	private int m_killPoints = 0;
	private int m_flagPoints = 0;
	
	private int m_lastFlag = 0;
	
	public OctaPlayer( Player p ) {
		m_name = p.getPlayerName();
		m_freq = p.getFrequency();
		m_timeStart = (int)(System.currentTimeMillis()/1000);
	}
	
	public void update( Player p ) {
		m_freq = p.getFrequency();
	}
	
	public void update( FlagClaimed event ) {
	}
	
	public void update( FlagDropped event ) {
		m_lastFlag = (int)(System.currentTimeMillis()/1000);
	}
	
	public void handleEvent( PlayerDeath event, boolean killer, BotAction botAction, OctaPlayer o ) {
		
		Player p = botAction.getPlayer( event.getKillerID() );
		Player p2 = botAction.getPlayer( event.getKilleeID() );
		
		if( killer ) {
			if( p.getFrequency() == p2.getFrequency() )
				addTeamKill();
			else 
				addKill();
			
			if( o.getLastFlagTouch() < 2 ) addFlagKill();
			
			addKillPoints( event.getScore() );
		} else {
			if( getLastFlagTouch() < 2  ) addFlagDeath();
			addDeath();
		}
	}
	
	public void addKill() { m_kill++; }
	public void addTeamKill() { m_teamKill++; }
	public void addDeath() { m_death++; }
	public void addFlagKill() { m_flagKill++; }
	public void addFlagDeath() { m_flagDeath++; }
	public void addFlagPoints( int p ) { m_flagPoints += p; }
	public void addKillPoints( int p ) { m_killPoints += p; }
	
	public int getFrequency() { return m_freq; }
	
	public int getLastFlagTouch() {
		return (int)(System.currentTimeMillis()/1000) - m_lastFlag;
	}
	
	public String getQueryString( int id, int gameId ) {
		
		int time = ((int)(System.currentTimeMillis()/1000))-m_timeStart;
		
		String query = "INSERT INTO `tblOctaPlayer` (`fnUserID` , `fcUserName` , `fnGameID` , `fnTeam` , `fnKills` , `fnTeamKills` , `fnDeaths` , `fnFlagKills` , `fnFlagDeaths` , `fnFlagPoints` , `fnKillPoints` , `fnTimePlayed` ) "; 
		query += "VALUES ('"+id+"', '"+Tools.addSlashesToString(m_name)+"',";
		query += "'"+gameId+"', '"+m_freq+"', '"+m_kill+"',";
		query += "'"+m_teamKill+"', '"+m_death+"',";
		query += "'"+m_flagKill+"', '"+m_flagDeath+"',";
		query += "'"+m_flagPoints+"', '"+m_killPoints+"', '"+time+"')";
		return query;
	}
}