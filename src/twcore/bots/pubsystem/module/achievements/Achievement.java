package twcore.bots.pubsystem.module.achievements;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Holds Achievement information
 * @author spookedone
 */
public final class Achievement {

    //generic variables
    private final int id;
    private String name = "", description = "";
    private int ship = -1;
    //time variables
    private int timeMin = -1, timeMax = -1;
    private int flagTimeMin = -1, flagTimeMax = -1;
    //prize variables
    private int prizeMin = -1, prizeMax = -1, prizeType = -1;
    //turret booleans
    private boolean turret = false;
    //flagclaim variables
    private int flagClaimMin = -1, flagClaimMax = -1;
    //type bitmask
    private int type = 0;

    //location variables
    private final List<KillDeath> kills;
    private final List<KillDeath> deaths;
    private final List<Location> locations;

    private boolean complete;

    public Achievement(int id) {
        this.id = id;
        kills = Collections.synchronizedList(new LinkedList<KillDeath>());
        deaths = Collections.synchronizedList(new LinkedList<KillDeath>());
        locations = Collections.synchronizedList(new LinkedList<Location>());
    }

    public Achievement(Achievement achievement) {
        this.id = achievement.id;
        this.name = achievement.name;
        this.description = achievement.description;
        this.ship = achievement.ship;

        this.timeMin = achievement.timeMin;
        this.timeMax = achievement.timeMax;
        this.flagTimeMin = achievement.flagTimeMin;
        this.flagTimeMax = achievement.flagTimeMax;

        this.prizeMin = achievement.prizeMin;
        this.prizeMax = achievement.prizeMax;
        this.prizeType = achievement.prizeType;

        this.turret = achievement.turret;

        this.flagClaimMin = achievement.flagClaimMin;
        this.flagClaimMax = achievement.flagClaimMax;
        
        this.type = achievement.type;

        kills = Collections.synchronizedList(new LinkedList<KillDeath>(achievement.kills));
        deaths = Collections.synchronizedList(new LinkedList<KillDeath>(achievement.deaths));
        locations = Collections.synchronizedList(new LinkedList<Location>(achievement.locations));
    }

    public void checkIfComplete() {

    }

    /**
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
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
     * @return the timeMin
     */
    public int getTimeMin() {
        return timeMin;
    }

    /**
     * @param timeMin the timeMin to set
     */
    public void setTimeMin(int timeMin) {
        this.timeMin = timeMin;
    }

    /**
     * @return the timeMax
     */
    public int getTimeMax() {
        return timeMax;
    }

    /**
     * @param timeMax the timeMax to set
     */
    public void setTimeMax(int timeMax) {
        this.timeMax = timeMax;
    }

    /**
     * @return the prizeMin
     */
    public int getPrizeMin() {
        return prizeMin;
    }

    /**
     * @param prizeMin the prizeMin to set
     */
    public void setPrizeMin(int prizeMin) {
        this.prizeMin = prizeMin;
    }

    /**
     * @return the prizeMax
     */
    public int getPrizeMax() {
        return prizeMax;
    }

    /**
     * @param prizeMax the prizeMax to set
     */
    public void setPrizeMax(int prizeMax) {
        this.prizeMax = prizeMax;
    }

    /**
     * @return the prizeType
     */
    public int getPrizeType() {
        return prizeType;
    }

    /**
     * @param prizeType the prizeType to set
     */
    public void setPrizeType(int prizeType) {
        this.prizeType = prizeType;
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
     * @return the type
     */
    public int getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(int type) {
        this.type = type;
    }

    /**
     * @return the flagTimeMin
     */
    public int getFlagTimeMin() {
        return flagTimeMin;
    }

    /**
     * @param flagTimeMin the flagTimeMin to set
     */
    public void setFlagTimeMin(int flagTimeMin) {
        this.flagTimeMin = flagTimeMin;
    }

    /**
     * @return the flagTimeMax
     */
    public int getFlagTimeMax() {
        return flagTimeMax;
    }

    /**
     * @param flagTimeMax the flagTimeMax to set
     */
    public void setFlagTimeMax(int flagTimeMax) {
        this.flagTimeMax = flagTimeMax;
    }

    /**
     * @return the flagClaimMin
     */
    public int getFlagClaimMin() {
        return flagClaimMin;
    }

    /**
     * @param flagClaimMin the flagClaimMin to set
     */
    public void setFlagClaimMin(int flagClaimMin) {
        this.flagClaimMin = flagClaimMin;
    }

    /**
     * @return the flagClaimMax
     */
    public int getFlagClaimMax() {
        return flagClaimMax;
    }

    /**
     * @param flagClaimMax the flagClaimMax to set
     */
    public void setFlagClaimMax(int flagClaimMax) {
        this.flagClaimMax = flagClaimMax;
    }

    /**
     * @param kill the kill to add
     */
    public void addKill(KillDeath kill) {
        synchronized (kills) {
            kills.add(kill);
        }
    }

    /**
     * @return the kills
     */
    public List<KillDeath> getKills() {
        return kills;
    }

    /**
     * @param death the death to add
     */
    public void addDeath(KillDeath death) {
        synchronized (deaths) {
            deaths.add(death);
        }
    }

    /**
     * @return the deaths
     */
    public List<KillDeath> getDeaths() {
        return deaths;
    }

    /**
     * @param location the location to add
     */
    public void addLocation(Location location) {
        synchronized (locations) {
            locations.add(location);
        }
    }

    /**
     * @return the locations
     */
    public List<Location> getLocations() {
        return locations;
    }

}
