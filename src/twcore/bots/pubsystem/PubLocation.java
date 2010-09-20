package twcore.bots.pubsystem;

import twcore.core.game.Player;
import twcore.core.util.Point;
import twcore.core.util.PointLocation;

/**
 * Simple wrapper to get the name of the location
 */
public class PubLocation {

    private static final int TOP_FR = 248;              // Coords forming boxes in which players
    private static final int BOTTOM_FR = 292;           // may be located: FR, mid and lower.  Spawn
    private static final int LEFT_FR = 478;             // and roof are defined by single Y coords
    private static final int RIGHT_FR = 546;            // and are checked after other boxes to determine
    private static final int TOP_MID = 287;             // a player's location.  Boxes can overlap.
    private static final int BOTTOM_MID = 334;
    private static final int LEFT_MID = 463;
    private static final int RIGHT_MID = 561;
    private static final int TOP_LOWER = 335;
    private static final int BOTTOM_LOWER = 395;
    private static final int LEFT_LOWER = 424;
    private static final int RIGHT_LOWER = 600;
    private static final int TOP_SPAWN_AREA = 396;
    private static final int BOTTOM_ROOF = 271;
	
    private PointLocation location;
    private String name;
    
    public PubLocation(PointLocation location, String name){
    	this.location = location;
    	this.name = name;
    }
   
    public boolean isInside(Point point) {
    	return location.isInside(point);
    }

    public String getName() {
    	return name;
    }
    
    /**
     * Based on provided coords, returns location of player as a String.
     * @return Last location recorded of player, as a String
     */
    public static String getPlayerLocation( Player p, boolean isStaff ) {
        int x = p.getXLocation() / 16;
        int y = p.getYLocation() / 16;
        String exact = "";
        if( isStaff )
            exact = "  (" + x + "," + y + ")";
        if( x==0 && y==0 )
            return "Not yet spotted" + exact;
        if( y >= TOP_FR  &&  y <= BOTTOM_FR  &&  x >= LEFT_FR  &&  x <= RIGHT_FR )
            return "in Flagroom" + exact;
        if( y >= TOP_MID  &&  y <= BOTTOM_MID  &&  x >= LEFT_MID  &&  x <= RIGHT_MID )
            return "in Mid Base" + exact;
        if( y >= TOP_LOWER  &&  y <= BOTTOM_LOWER  &&  x >= LEFT_LOWER  &&  x <= RIGHT_LOWER )
            return "in Lower Base" + exact;
        if( y <= BOTTOM_ROOF )
            return "Roofing ..." + exact;
        if( y >= TOP_SPAWN_AREA )
            return "in spawn" + exact;
        return "Outside base" + exact;
    }
}
