package twcore.bots.pubsystem.module.achievements;

/**
 *
 * @author spookedone
 */
public class KillDeath {
    private int current = 0;
    private int minimum = -1, maximum = -1, ship = -1;
    private boolean turret = false;
    private Location location = null;
    private boolean complete = false;

    /**
     * @return the current
     */
    public int getCurrent() {
        return current;
    }

    /**
     * @param current the current to set
     */
    public void setCurrent(int current) {
        this.current = current;
    }

    /**
     * @return the minimum
     */
    public int getMinimum() {
        return minimum;
    }

    /**
     * @param minimum the minimum to set
     */
    public void setMinimum(int minimum) {
        this.minimum = minimum;
    }

    /**
     * @return the maximum
     */
    public int getMaximum() {
        return maximum;
    }

    /**
     * @param maximum the maximum to set
     */
    public void setMaximum(int maximum) {
        this.maximum = maximum;
    }

    /**
     * @return the ship
     */
    public int getShip() {
        return ship;
    }

    /**
     * @param ship the ship to set
     */
    public void setShip(int ship) {
        this.ship = ship;
    }

    /**
     * @return the turret
     */
    public boolean isTurret() {
        return turret;
    }

    /**
     * @param turret the turret to set
     */
    public void setTurret(boolean turret) {
        this.turret = turret;
    }

    /**
     * @return the location
     */
    public Location getLocation() {
        return location;
    }

    /**
     * @param location the location to set
     */
    public void setLocation(Location location) {
        this.location = location;
    }

    /**
     * @return the complete
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * @param complete the complete to set
     */
    public void setComplete(boolean complete) {
        this.complete = complete;
    }
}
