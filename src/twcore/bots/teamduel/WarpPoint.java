package twcore.bots.teamduel;

public class WarpPoint {

    int d_xLocation;
    int d_yLocation;

    public WarpPoint(String x, String y) {
        d_xLocation = Integer.parseInt(x);
        d_yLocation = Integer.parseInt(y);
    }

    public int getXCoord() {
        return d_xLocation;
    }

    public int getYCoord() {
        return d_yLocation;
    }
}