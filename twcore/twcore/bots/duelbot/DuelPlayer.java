package twcore.bots.duelbot;

import twcore.core.*;
import java.sql.*;

public class DuelPlayer {
	
	private boolean m_winBy2 	= false;
	private boolean m_noCount 	= false;
	private boolean m_deathWarp = false;
	private int     m_toWin 	= 10;
	
	public DuelPlayer( ResultSet _result ) {
		try {
			if( _result.getInt( "fnWinBy2" ) == 1 ) m_winBy2 = true;
			if( _result.getInt( "fnNoCount" ) == 1 ) m_noCount = true;
			if( _result.getInt( "fnDeathWarp" ) == 1 ) m_deathWarp = true;
			m_toWin = _result.getInt( "fnGameKills" );
		} catch (Exception e) {}
	}
	
	public boolean getWinBy2() { return m_winBy2; }
	public boolean getNoCount() { return m_noCount; }
	public boolean getDeathWarp() { return m_deathWarp; }
	public int getToWin() { return m_toWin; }
}