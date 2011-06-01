package twcore.bots.pubsystem.module.achievements;

/**
 *
 * @author spookedone
 */
public class Location {
    private int x = -1;
    private int y = -1;
    private int radius = -1;
    private int length = -1;
    private int width = -1;
    private int minDistance = -1;
    private int maxDistance = -1;

    private boolean unique = false;

    //time variables
    private long timeStamp = -1;
    private int timeMin = -1, timeMax = -1;
    private long flagTimeStamp = -1;
    private int flagTimeMin = -1, flagTimeMax = -1;
    //prize variables
    private int prizeCurrent = -1;
    private int prizeMin = -1, prizeMax = -1, prizeType = -1;

    private boolean achieved = false;

    /**
     * @return the x
     */
    public int getX() {
        return x;
    }

    /**
     * @param x the x to set
     */
    public void setX(int x) {
        this.x = x;
    }

    /**
     * @return the y
     */
    public int getY() {
        return y;
    }

    /**
     * @param y the y to set
     */
    public void setY(int y) {
        this.y = y;
    }

    /**
     * @return the radius
     */
    public int getRadius() {
        return radius;
    }

    /**
     * @param radius the radius to set
     */
    public void setRadius(int radius) {
        this.radius = radius;
    }

    /**
     * @return the length
     */
    public int getLength() {
        return length;
    }

    /**
     * @param legnth the length to set
     */
    public void setLength(int length) {
        this.length = length;
    }

    /**
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * @param width the width to set
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * @return the achieved
     */
    public boolean isAchieved() {
        return achieved;
    }

    /**
     * @param achieved the achieved to set
     */
    public void setAchieved(boolean achieved) {
        this.achieved = achieved;
    }

    /**
     * @return the minDistance
     */
    public int getMinDistance() {
        return minDistance;
    }

    /**
     * @param minDistance the minDistance to set
     */
    public void setMinDistance(int minDistance) {
        this.minDistance = minDistance;
    }

    /**
     * @return the maxDistance
     */
    public int getMaxDistance() {
        return maxDistance;
    }

    /**
     * @param maxDistance the maxDistance to set
     */
    public void setMaxDistance(int maxDistance) {
        this.maxDistance = maxDistance;
    }
}
