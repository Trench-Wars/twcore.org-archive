package twcore.bots.duelbot;

import java.util.Random;
import java.util.Vector;

public class DuelBox {

	int d_box 		= -1;
	int d_type 		= -1;
	int d_x1   		= 0;
	int d_x2   		= 0;
	int d_y1   		= 0;
	int d_y2   		= 0;
	int d_safex1   	= 0;
	int d_safex2   	= 0;
	int d_safey1   	= 0;
	int d_safey2   	= 0;
	boolean inUse   = false;
	WarpPoint	last;

	Random generator;
	Vector<WarpPoint> randomWarpPoints = new Vector<WarpPoint>();

	public DuelBox( String settings[], String randomPt[], int b ) {
		d_box = b;
		d_type = Integer.parseInt( settings[0] );
		d_safex1 = Integer.parseInt( settings[1] );
		d_safey1 = Integer.parseInt( settings[2] );
		d_safex2 = Integer.parseInt( settings[3] );
		d_safey2 = Integer.parseInt( settings[4] );
		d_x1 = Integer.parseInt( settings[5] );
		d_y1 = Integer.parseInt( settings[6] );
		d_x2 = Integer.parseInt( settings[7] );
		d_y2 = Integer.parseInt( settings[8] );
		for( int i = 0; i < randomPt.length; i += 2 )
			randomWarpPoints.add( new WarpPoint( randomPt[i], randomPt[i+1] ) );
		generator = new Random();
	}

	public boolean gameType( int gameType ) {

		if( d_type == 1 && gameType == 3 ) return true;
		if( d_type == 1 && gameType == 7 ) return true;
		else if( d_type == gameType ) return true;
		else return false;
	}

	public WarpPoint getRandomWarpPoint() {

		WarpPoint p = randomWarpPoints.elementAt( generator.nextInt( randomWarpPoints.size() ) );
		if( p == last ) return getRandomWarpPoint();
		last = p;
		return p;
	}

	public int getXOne() { return d_x1; }
	public int getXTwo() { return d_x2; }
	public int getYOne() { return d_y1; }
	public int getYTwo() { return d_y2; }
	public int getSafeXOne() { return d_safex1; }
	public int getSafeXTwo() { return d_safex2; }
	public int getSafeYOne() { return d_safey1; }
	public int getSafeYTwo() { return d_safey2; }
	public int getBoxNumber() { return d_box; }
	public void toggleUse() { inUse = !inUse; }
	public boolean inUse() { return inUse; }
}