package twcore.bots.duelbot;

import twcore.core.*;
import java.sql.*;

public class DuelPlayer {
	
	boolean winBy2 = false;
	boolean noCount = false;
	boolean deathWarp = false;
	int     toWin = 10;
	
	public DuelPlayer( ResultSet result ) {
		try {
			if( result.getInt( "fnWinBy2" ) == 1 ) winBy2 = true;
			if( result.getInt( "fnNoCount" ) == 1 ) noCount = true;
			if( result.getInt( "fnDeathWarp" ) == 1 ) deathWarp = true;
			toWin = result.getInt( "fnGameKills" );
		} catch (Exception e) {}
	}
	
	public boolean winBy2() { return winBy2; }
	public boolean noCount() { return noCount; }
	public boolean deathWarp() { return deathWarp; }
	public int toWin() { return toWin; }
}