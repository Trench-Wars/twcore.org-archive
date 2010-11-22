package twcore.bots.teamduel;


//With intentions of being able to use this for a variety of things.

public class PlayerProfile
{
    int kills = 0, deaths = 0, teamKills = 0;
    int ship = 1, freq = 0, time = 0;
    int[] data = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
    int lastDeath = 0;
    String playerName;
    int team;

    public PlayerProfile(){
    }

    public PlayerProfile( String name ) {
 		playerName = name;
    }

    public PlayerProfile( String name, int s ) {
    	playerName = name;
    	ship = s;
    }

    public PlayerProfile( String name, int s, int f ) {
    	playerName = name;
    	ship = s;
    	freq = f;
    }

    public void addKill()   { kills++; }
    public void removeKill() { kills--; }
    public int  getKills()  { return kills; }

    public void addDeath()  {
    	deaths++;
    	lastDeath = (int)(System.currentTimeMillis() / 1000);
    }
    public void removeDeath() { deaths--; }
    public int  getDeaths() { return deaths; }

    public int timeFromLastDeath() { return (int)(System.currentTimeMillis() / 1000) - lastDeath; }
    public void setTimer() { lastDeath = (int)(System.currentTimeMillis() / 1000); }

    public void setShip( int s ) { ship = s; }
    public int  getShip()   { return ship; }

    public void setFreq( int f ) { freq = f; }
    public int  getFreq()   { return freq; }

    public void setTime( int t ) { time = t; }
    public int  getTime()   { return time; }

    public void setData( int location, int value ) { data[location] = value; }
    public void incData( int location ) { data[location]++; }
    public void decData( int location ) { data[location]--; }
    public int  getData( int location ) { return data[location]; }

    public String getName() { return playerName; }


}