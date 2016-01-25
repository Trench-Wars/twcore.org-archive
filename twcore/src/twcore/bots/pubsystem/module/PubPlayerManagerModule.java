package twcore.bots.pubsystem.module;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.Random;
import java.util.TimerTask;
import java.util.Vector;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
//import twcore.bots.pubsystem.module.PubHuntModule.HuntPlayer;
import twcore.bots.pubsystem.module.moneysystem.item.PubItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubShipItem;
import twcore.bots.pubsystem.module.moneysystem.item.PubShipUpgradeItem;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.bots.pubsystem.util.Log;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.SQLResultEvent;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.lvz.Objset;

public class PubPlayerManagerModule extends AbstractModule {

    private int MSG_AT_FREQSIZE_DIFF = -1;      // Max # difference in size of freqs before
    //   bot requests players even frequencies.
    //   Value of -1 disables this feature.
    private int MIDROUND_FREQSIZE_DIFF = -1;    // As above, but for midround differences

    @SuppressWarnings("unused")
    private int KEEP_MVP_FREQSIZE_DIFF = 0;// Max # difference in size of freqs required
    //   for a player to keep MVP/get bonus on switching.

    private int NICEGUY_BOUNTY_AWARD = 0; // Bounty given to those that even freqs/ships

    private int MAX_MID_SPAWN = -1; // Max Freq Size to enable mid spawning, -1 = off

    private int SHUFFLE_SIZE = -1;

    private int MAX_EXTRA_ON_PRIVATES = 999;            // -999 to disable (in CFG)

    private TreeMap<String, PubPlayer> players;         // Always lowercase!
    private TreeSet<String> freq0;                      // Players on freq 0
    private TreeSet<String> freq1;                      // Players on freq 1

    private int tkTax = 0;                            // Amount to deduct for team kills

    private int[] freqSizeInfo = {0, 0};                // Index 0: size difference; 1: # of smaller freq

    private Vector<Integer> shipWeight;
    private boolean isPurePub = false;
    private boolean isSortaPurePub = false;

    private String databaseName;

    private SavePlayersTask saveTask = new SavePlayersTask();
    private int SAVETASK_INTERVAL = 1; // minutes

    //private Log logMoneyDBTransaction;

    private boolean notify = false;
    private boolean switchAllowed = false;
    private boolean shuffleVoting = false;
    private boolean lowPopSpawning = false;

    private Random r = new Random();
    private TreeMap<Integer, Integer> votes = new TreeMap<Integer, Integer>();
    private Objset moneyObjs;

    public PubPlayerManagerModule(BotAction m_botAction, PubContext context)
    {
        super(m_botAction, context, "PlayerManager");
        moneyObjs = new Objset();

        //logMoneyDBTransaction = new Log(m_botAction, "moneydb.log");

        this.players = new TreeMap<String, PubPlayer>();
        this.freq0 = new TreeSet<String>();
        this.freq1 = new TreeSet<String>();

        m_botAction.scheduleTaskAtFixedRate(saveTask, SAVETASK_INTERVAL * Tools.TimeInMillis.MINUTE, SAVETASK_INTERVAL * Tools.TimeInMillis.MINUTE);

        // Always enabled!
        enabled = true;

        reloadConfig();
    }

    public void handleEvent(Message event) {
        if (event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.PUBLIC_MESSAGE) {
            if (event.getMessage().equalsIgnoreCase("!switch") && switchAllowed) {
                int id = event.getPlayerID();
                Player p = m_botAction.getPlayer(id);

                if ((p.getFrequency() != 0 && p.getFrequency() != 1) || p.getShipType() == 0)
                    return;

                if( MSG_AT_FREQSIZE_DIFF != -1 ) {
                    if (freqSizeInfo[0] <= 1) {
                        m_botAction.sendPrivateMessage(id, "Team adjustment not needed.");
                        switchAllowed = false;
                        return;
                    }

                    if((freqSizeInfo[1] == 1 && p.getFrequency() == 0) || (freqSizeInfo[1] == 0 && p.getFrequency() == 1)) {
                        doFrequencySwitch(p.getPlayerID());
                    }
                }
            } else if (shuffleVoting && SHUFFLE_SIZE != -1) {
                int id = event.getPlayerID();
                Player p = m_botAction.getPlayer(id);

                if (p == null || (p.getFrequency() != 0 && p.getFrequency() != 1) || p.getShipType() == 0)
                    return;

                int vote = -1;

                try {
                    vote = Integer.valueOf(event.getMessage());
                } catch (NumberFormatException e) {
                    return;
                }

                if (vote == 1 || vote == 2) {
                    votes.put(id, vote);
                    m_botAction.sendPrivateMessage(id, "Your vote to " + (vote == 1 ? "" : "not ") + "shuffle has been counted.");
                }
            }
        }
    }


    public void requestEvents(EventRequester eventRequester)
    {
        eventRequester.request(EventRequester.PLAYER_DEATH);
        eventRequester.request(EventRequester.ARENA_JOINED);
        eventRequester.request(EventRequester.FREQUENCY_CHANGE);
        eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
        eventRequester.request(EventRequester.PLAYER_LEFT);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.MESSAGE);
    }

    public boolean addMoney(String playerName, int money) {
        return addMoney(playerName, money, false);
    }

    public boolean removeMoney(String playerName, int money) {
        return removeMoney(playerName, money, false);
    }

    public boolean addMoney(String playerName, int money, boolean forceToDB) {
        PubPlayer player = players.get(playerName.toLowerCase());

        if (player != null) {
            player.addMoney(money);
            return true;
        }
        else if (forceToDB) {

            String database = m_botAction.getBotSettings().getString("database");

            // The query will be closed by PlayerManagerModule
            if (database != null)
                try {
                    ResultSet rs = m_botAction.SQLQuery(database, "UPDATE tblPlayerStats "
                                                        + "SET fnMoney = fnMoney+" + Math.abs(money) + " "
                                                        + "WHERE fcName='" + Tools.addSlashes(playerName) + "'");

                    if (rs.getStatement().getUpdateCount() < 1) {
                        m_botAction.SQLClose(rs);
                        return false;
                    }

                    m_botAction.SQLClose(rs);
                } catch (SQLException e) {
                    return false;
                }

            return true;
        }

        return false;
    }

    public boolean removeMoney(String playerName, int money, boolean forceToDB) {
        PubPlayer player = players.get(playerName.toLowerCase());

        if (player != null) {
            player.removeMoney(money);
            return true;
        }
        else if (forceToDB) {

            String database = m_botAction.getBotSettings().getString("database");

            if (database != null)
                m_botAction.SQLBackgroundQuery(database, null, "UPDATE tblPlayerStats "
                                               + "SET fnMoney = IF(fnMoney-" + Math.abs(money) + "<0,0,fnMoney-" + Math.abs(money) + ") "
                                               + "WHERE fcName='" + playerName + "'");

        }

        return false;
    }


    /**
        Retrieve a player on the cache only (if the player is currently playing)
        or by requesting the database. You may only want to request the database
        if you want to get information about a player not playing.
    */
    public PubPlayer getPlayer(String playerName, boolean cacheOnly)
    {
        PubPlayer player = players.get(playerName.toLowerCase());

        if (cacheOnly) {
            if (player != null)
                player.setName(playerName);

            return player;
        }
        else {

            if (player != null)
                return player;

            if (databaseName != null) {
                try {
                    //ResultSet rs = m_botAction.SQLQuery(databaseName, "SELECT s.fcName, s.fnMoney, p.fbWarp, s.fcTileset, s.fnBestStreak, p.fnID FROM tblPlayerStats s, tblPlayer p WHERE s.fcName = '"+Tools.addSlashes(playerName)+"'");
                    //12Aug2013 POiD    Updated query to use 2 outer joins (MYSQL way to handle Full outer joins) so that entries from both tblPlayer and tblPlayerStats will be included
                    //                  even if an entry exists in only 1 of the tables and not in both. Previous query would return nothing if both tables didn't have a row.
                    ResultSet rs = m_botAction.SQLQuery(databaseName, "SELECT s.fcName as sfcName,p.fcName as pfcName,s.fnMoney,s.fbWarp,s.fcTileset,s.fnBestStreak,p.fnID from tblPlayerStats as s left outer JOIN tblPlayer as p on p.fcName=s.fcName"
                                                        + " WHERE s.fcName='" + Tools.addSlashes(playerName) + "'"
                                                        + " UNION"
                                                        + " SELECT s.fcName as sfcName,p.fcName as pfcName,s.fnMoney,s.fbWarp,s.fcTileset,s.fnBestStreak,p.fnID from tblPlayerStats as s right outer JOIN tblPlayer as p on p.fcName=s.fcName"
                                                        + " WHERE p.fcName='" + Tools.addSlashes(playerName) + "'");

                    if (rs.next()) {
                        player = getPlayerByResultSet(rs);

                        if (player == null)
                            return null;

                        player.setName(playerName);
                        m_botAction.SQLClose(rs);
                        return player;
                    }

                    m_botAction.SQLClose(rs);
                } catch (SQLException e) {
                    Tools.printStackTrace(e);
                }
            }

            return players.get(playerName.toLowerCase());
        }
    }

    public PubPlayer getPlayer(String name) {
        return getPlayer(name, true);
    }

    public boolean isPlayerExists(String name) {
        return players.containsKey(name.toLowerCase());
    }

    public boolean isPlayerOnFreq(String name, int freq) {
        if (freq == 0)
            return freq0.contains(players.get(name.toLowerCase()));

        if (freq == 1)
            return freq1.contains(players.get(name.toLowerCase()));

        return false;
    }

    public void handleEvent(PlayerEntered event) {

        String playerName = event.getPlayerName();

        PubPlayer pubPlayer = addPlayerToSystem(playerName);

        String restrictions = "";
        int weight;

        for(int i = 1; i < 9; i++) {
            weight = shipWeight.get(i).intValue();

            if( weight == 0 )
                restrictions += Tools.shipName( i ) + "s disabled.  ";

            if( weight > 1 )
                restrictions += Tools.shipName( i ) + "s limited.  ";
        }

        if (isPurePub)
            restrictions += "No new Leviathans; existing Levis can't kill (PurePub)";

        if (isSortaPurePub)
            restrictions += "No new Leviathans; existing Levis can't kill on private freqs (SortaPurePub)";

        if (!context.getPubUtil().isLevAttachEnabled()) {
            restrictions += "Leviathan attach disabled on public frequencies.";
        }

        if( restrictions != "" )
            m_botAction.sendSmartPrivateMessage(playerName, "Ship restrictions: " + restrictions );

        if (context.isStarted()) {
            checkPlayer(event.getPlayerID());
            //if(!context.getPubUtil().isPrivateFrequencyEnabled()) {
            //    checkFreq(event.getPlayerID(), event.getTeam(), false);
            //checkFreqSizes();
            //}
            checkLowPopSpawn();
        }

        if (pubPlayer != null) {
            if (pubPlayer.getMoney() < 1000)
                m_botAction.sendSmartPrivateMessage(playerName, "PM me back with !help for a list of commands. (Type :: to reply to last PM sent)");

            pubPlayer.resetSpecTime(event.getShipType());
        }
    }

    public void handleEvent(PlayerLeft event) {

        Player p = m_botAction.getPlayer(event.getPlayerID());

        if (p == null)
            return;

        String playerName = p.getPlayerName();

        removeFromLists(playerName);
        checkLowPopSpawn();
        //checkFreqSizes();
    }

    public void handleEvent(PlayerDeath event) {

        Player killer = m_botAction.getPlayer(event.getKillerID());
        Player killed = m_botAction.getPlayer(event.getKilleeID());

        if (killer == null || killed == null)
            return;

        PubPlayer pubPlayerKiller = getPlayer(killer.getPlayerName());
        PubPlayer pubPlayerKilled = getPlayer(killed.getPlayerName());

        if (pubPlayerKilled != null) {
            //long diff = System.currentTimeMillis() - pubPlayerKilled.getLastDeath();
            pubPlayerKilled.addDeath();

            // Spawn check (not working properly, the attach info isn't updating fast enough)
            /*
                if (diff < 8 * Tools.TimeInMillis.SECOND && System.currentTimeMillis()-pubPlayerKilled.getLastAttach() > 3.5*Tools.TimeInMillis.SECOND) {
                if (pubPlayerKiller != null) {
                    if (pubPlayerKiller.getMoney() >= 200) {
                        pubPlayerKiller.removeMoney(200);
                        m_botAction.sendSmartPrivateMessage(killer.getPlayerName(), "Spawning is uncool, $200 subtracted from your money.");
                    } else {
                        m_botAction.sendSmartPrivateMessage(killer.getPlayerName(), "Spawning is uncool.");
                    }
                }
                }
            */

            if(lowPopSpawning) {
                if(!context.getPubChallenge().isDueling(pubPlayerKilled.getPlayerName()) && killed.isPlaying()) {
                    pubPlayerKilled.doLowPopSpawn(true);
                }
            }
        }

        // Check new PurePub and SortaPurePub ... Levis can exist, but can't make kills.
        if (killer.getShipType() == Tools.Ship.LEVIATHAN) {
            if (isPurePub) {
                m_botAction.sendPrivateMessage(killer.getPlayerID(), "[PUREPUB] is enabled. By making a kill, you have opted out of saving your bounty during PurePub, and will be placed in a new ship.");
                m_botAction.setShip(killer.getPlayerID(), 7);
            } else if (isSortaPurePub) {
                if (killer.getFrequency() > 1) {
                    m_botAction.sendPrivateMessage(killer.getPlayerID(), "[SORTAPUREPUB] is enabled. By making a kill on a private freq, you have opted out of saving your bounty during SortaPurePub, and will be placed in a new ship.");
                    m_botAction.setShip(killer.getPlayerID(), 7);
                    return;
                }
            }
        }

        // The following four if statements deduct the tax value from a player who TKs.
        if (tkTax > 0 && (killer.getFrequency() == killed.getFrequency()) && (killer.getShipType() != 8)) {
            if (pubPlayerKiller != null) {
                int money = pubPlayerKiller.getMoney();

                if (money >= tkTax) {
                    pubPlayerKiller.removeMoney(tkTax);
                    m_botAction.sendPrivateMessage(pubPlayerKiller.getPlayerName(), "Your account has been deducted $" + tkTax + " for team-killing " + killed);
                }
            }
        }

    }

    public void handleEvent(FrequencyChange event) {
        Player player = m_botAction.getPlayer(event.getPlayerID());

        if (player == null)
            return;

        PubPlayer pubPlayer;
        pubPlayer = players.get(player.getPlayerName().toLowerCase());

        if (lowPopSpawning) {
            if (pubPlayer != null) {
                if(!context.getPubChallenge().isDueling(pubPlayer.getPlayerName()) && player.isPlaying()) {
                    pubPlayer.doLowPopSpawn(false);
                }
            }
        }

        if (context.isStarted()) {
            if (event.getFrequency() > 1) {
                if (!context.getPubUtil().isPrivateFrequencyEnabled()) {
                    checkFreq(event.getPlayerID(), event.getFrequency(), true);
                } else {
                    if( m_botAction.getOperatorList().isModerator(name) )
                        return;

                    checkCanSwitchToPrivate( event.getPlayerID(), event.getFrequency() );
                }

                if (player.getShipType() == Tools.Ship.LEVIATHAN ) {
                    if (isSortaPurePub) {
                        m_botAction.sendPrivateMessage(event.getPlayerID(), "[SORTAPUREPUB] is enabled. You may not change onto a private freq as a Leviathan at this time.");
                        m_botAction.setShip(event.getPlayerID(), 7);
                    } else if(event.getFrequency() == context.getGameFlagTime().getHunterFreq() && context.getGameFlagTime().isHunterFreqEnabled()) {
                        m_botAction.sendPrivateMessage(event.getPlayerID(), "This is a special frequency dedicated to hunting LeviTerrs. You can not be a Levi on this freq.");
                        m_botAction.setShip(event.getPlayerID(), 3);
                    }
                }
            } else {
                if (pubPlayer != null) {
                    if( m_botAction.getOperatorList().isModerator(name) )
                        return;

                    int freq0 = m_botAction.getPlayingFrequencySize(0);
                    int freq1 = m_botAction.getPlayingFrequencySize(1);

                    if( event.getFrequency() == 0 && freq0 > freq1 + 1 ) {
                        m_botAction.setFreq( player.getPlayerID(), 1 );
                        m_botAction.sendPrivateMessage(event.getPlayerID(), "You have been placed on freq 1 to prevent a team imbalance.");
                    }

                    if( event.getFrequency() == 1 && freq1 > freq0 + 1 ) {
                        m_botAction.setFreq( player.getPlayerID(), 0 );
                        m_botAction.sendPrivateMessage(event.getPlayerID(), "You have been placed on freq 0 to prevent a team imbalance.");
                    }
                }
            }

            checkLowPopSpawn();
        }

        String p = m_botAction.getPlayerName(event.getPlayerID());

        if (p != null) {
            if (pubPlayer != null)
                pubPlayer.setLastFreqSwitch(event.getFrequency());
        }


        /*  Disabled until hunt running again
            if (context.isStarted() && context.getPubHunt().isRunning()) {
            Player player = m_botAction.getPlayer(event.getPlayerID());
            if (player==null)
                return;
            HuntPlayer huntPlayer = context.getPubHunt().getPlayerPlaying(player.getPlayerName());
            if (huntPlayer != null) {
                if (huntPlayer.freq != event.getFrequency()) {
                    m_botAction.setFreq(player.getPlayerID(), huntPlayer.freq);
                    m_botAction.sendSmartPrivateMessage(player.getPlayerName(), "You cannot change your frequency during a game of hunt.");
                }
            } else {
                checkPlayer(player.getPlayerID());
                //if(!context.getPubUtil().isPrivateFrequencyEnabled()) {
                //    checkFreq(playerID, freq, true);
                //    checkFreqSizes();
                //}
            }
            }
        */
    }

    public void handleEvent(FrequencyShipChange event) {
        if (lowPopSpawning) {
            Player p = m_botAction.getPlayer(event.getPlayerID());

            if (p == null)
                return;

            PubPlayer pubPlayer = players.get(p.getPlayerName().toLowerCase());

            if (pubPlayer != null) {
                pubPlayer.handleShipChange(event);

                if(!context.getPubChallenge().isDueling(pubPlayer.getPlayerName()) && p.isPlaying()) {
                    pubPlayer.doLowPopSpawn(false);
                }
            }
        }

        if (context.isStarted()) {
            checkPlayer(event.getPlayerID());

            if (event.getFrequency() > 1) {
                if (!context.getPubUtil().isPrivateFrequencyEnabled()) {
                    checkFreq(event.getPlayerID(), event.getFrequency(), true);
                }

                if (event.getShipType() == Tools.Ship.LEVIATHAN ) {
                    if (isSortaPurePub) {
                        m_botAction.sendPrivateMessage(event.getPlayerID(), "[SORTAPUREPUB] is enabled. You may not change into a Leviathan while on a private freq at this time.");
                        m_botAction.setShip(event.getPlayerID(), 7);
                    } else if(event.getFrequency() == context.getGameFlagTime().getHunterFreq() && context.getGameFlagTime().isHunterFreqEnabled()) {
                        m_botAction.sendPrivateMessage(event.getPlayerID(), "This is a special frequency dedicated to hunting LeviTerrs. You can not be a Levi on this freq.");
                        m_botAction.setShip(event.getPlayerID(), 3);
                    }
                }
            } else {
                if (event.getShipType() == Tools.Ship.LEVIATHAN && isPurePub) {
                    m_botAction.sendPrivateMessage(event.getPlayerID(), "[PUREPUB] is enabled. You may not change into a Leviathan at this time.");
                    m_botAction.setShip(event.getPlayerID(), 7);
                }
            }

            checkLowPopSpawn();

        }
    }

    public void handleDisconnect() {
        saveTask.force();
    }

    public void handleEvent(SQLResultEvent event) {
        try {
            if (event.getIdentifier().startsWith("moneydb")) {
                String[] pieces = event.getIdentifier().split(":");
                String force = pieces[3].equals("1") ? "(F) " : "";
                //logMoneyDBTransaction.write(Tools.getTimeStamp() + " - " + force + pieces[1] + "> " + pieces[2]);
            } else if (event.getIdentifier().startsWith("newplayer")) {
                ResultSet rs = event.getResultSet();
                String playerName = event.getIdentifier().substring(10);

                if (rs.next()) {
                    getPlayerByResultSet(rs);
                } else {
                    players.put(playerName.toLowerCase(), new PubPlayer(m_botAction, playerName, moneyObjs));
                }
            }

            //12Aug2013 POiD    Commented out the below updates as currently no updates to tblPlayer are needed. Left code in case of future changes.
            //                  note the tracking of fnID is to help determine whether to do an Update or Insert during timer based updates.
            //else if (event.getIdentifier().startsWith("warpInsert"))
            //{
            //  String playerName = event.getIdentifier().substring(11);
            //  ResultSet rs = event.getResultSet();
            //  if (rs.next())
            //  {
            //      long fnID = rs.getLong("fnID");
            //      players.get(playerName.toLowerCase()).setPlayerID(fnID);
            //  }
            //}
        } catch (SQLException e) {
            m_botAction.SQLClose(event.getResultSet());
            Tools.printStackTrace(e);
        } finally {
            m_botAction.SQLClose(event.getResultSet());
        }
    }

    public PubPlayer getPlayerByResultSet(ResultSet rs) {

        PubPlayer player = null;

        try {

            String name = rs.getString("sfcName");
            boolean hasStats = true;

            if (name == null)
            {
                name = rs.getString("pfcName");
                hasStats = false;
            }

            int money = rs.getInt("fnMoney");
            boolean warp = rs.getInt("fbWarp") == 1;
            long fnID = rs.getLong("fnID");

            if (rs.wasNull())
                fnID = -1;

            player = new PubPlayer(m_botAction, name, money, warp, fnID, moneyObjs);
            player.setHasStatsDB(hasStats);
            players.put(name.toLowerCase(), player);
            player.reloadPanel(false);
            player.setBestStreak(rs.getInt("fnBestStreak"));

        } catch (Exception e) {
            //m_botAction.sendSmartPrivateMessage("poid","broke with "+e.getMessage());
            Tools.printStackTrace(e);
        }

        return player;
    }

    public void handleEvent(ArenaJoined event)
    {
        Iterator<Player> it = m_botAction.getPlayerIterator();

        while(it.hasNext()) {
            Player p = it.next();
            addPlayerToSystem(p.getPlayerName());
        }
    }

    private PubPlayer addPlayerToSystem(final String playerName) {

        PubPlayer player = players.get(playerName.toLowerCase());

        if (player != null) {
            player.reloadPanel(false);
            player.setName(playerName);
            //context.getPubUtil().setTileset(player.getTileset(), player.getPlayerName(), false);
            return player;
        }
        else if (databaseName != null) {
            //m_botAction.SQLBackgroundQuery(databaseName, "newplayer_"+playerName, "SELECT s.fcName as fcName, s.fnMoney as fnMoney, s.fcTileset as fcTileset, s.fnBestStreak as fnBestStreak, p.fbWarp as fbWarp, p.fnId FROM tblPlayerStats s, tblPlayer p WHERE s.fcName = '"+Tools.addSlashes(playerName)+"' and p.fcName = '"+Tools.addSlashes(playerName)+"'");
            //12Aug2013 POiD    Updated query to use 2 outer joins (MYSQL way to handle Full outer joins) so that entries from both tblPlayer and tblPlayerStats will be included
            //                  even if an entry exists in only 1 of the tables and not in both. Previous query would return nothing if both tables didn't have a row.
            m_botAction.SQLBackgroundQuery(databaseName, "newplayer_" + playerName, "SELECT s.fcName as sfcName,p.fcName as pfcName,s.fnMoney,s.fbWarp,s.fcTileset,s.fnBestStreak,p.fnID from tblPlayerStats as s left outer JOIN tblPlayer as p on p.fcName=s.fcName"
                                           + " WHERE s.fcName='" + Tools.addSlashes(playerName) + "'"
                                           + " UNION"
                                           + " SELECT s.fcName as sfcName,p.fcName as pfcName,s.fnMoney,s.fbWarp,s.fcTileset,s.fnBestStreak,p.fnID from tblPlayerStats as s right outer JOIN tblPlayer as p on p.fcName=s.fcName"
                                           + " WHERE p.fcName='" + Tools.addSlashes(playerName) + "'");
        }
        else {
            player = new PubPlayer(m_botAction, playerName, moneyObjs);
            players.put(playerName.toLowerCase(), player);
        }

        return player;
    }

    public boolean isShipRestricted(int ship) {
        return shipWeight.get(ship) == 0;
    }

    /**
        Specs all ships in the arena that are over the weighted restriction limit.
    */
    private void specRestrictedShips()
    {
        Iterator<Player> iterator = m_botAction.getPlayingPlayerIterator();
        Player player;

        while(iterator.hasNext()) {
            player = (Player) iterator.next();
            checkPlayer(player.getPlayerID());
        }
    }

    /**
        Sets a given ship to a particular restriction.

         Values:
         0  - No ships of this type allowed
         1  - Unlimited number of ships of this type are allowed
         #  - If the number of current ships of the type on this frequency is
              greater than the total number of people on the frequency divided
              by this number (ships of this type &gt; total ships / weight), then the
              ship is not allowed.  The exception to this rule is if the player is
              the only one on the freq currently in the ship.

    */
    public void doSetCmd(String sender, String argString) {
        String[] args = argString.split(" ");

        if( args.length != 2 )
            throw new RuntimeException("Usage: !set <ship#> <weight#>");

        try {
            Integer ship = Integer.valueOf(args[0]);
            ship = ship.intValue();
            Integer weight = Integer.valueOf(args[1]);

            if( ship > 0 && ship < 9 ) {
                if( weight >= 0 ) {
                    shipWeight.set( ship.intValue(), weight );

                    if( weight == 0 )
                        m_botAction.sendSmartPrivateMessage(sender, Tools.shipName( ship ) + "s: disabled." );

                    if( weight == 1 )
                        m_botAction.sendSmartPrivateMessage(sender, Tools.shipName( ship ) + "s: unrestricted." );

                    if( weight > 1 )
                        m_botAction.sendSmartPrivateMessage(sender, Tools.shipName( ship ) + "s: limited to 1/" + weight + " of the size of a frequency (but 1 always allowed).");

                    specRestrictedShips();
                } else
                    throw new RuntimeException("Weight must be >= 0.");
            } else
                throw new RuntimeException("Invalid ship number.");
        } catch (Exception e) {
            throw new RuntimeException("Usage: !set <ship#> <weight#>");
        }
    }

    /**
        Sets PurePub restriction status. If on, Levis may not make kills,
        or else be switched into a new ship.
        @param b True if PurePub is on
    */
    public void setPurePubStatus(boolean b) {
        isPurePub = b;
    }


    /**
        Sets PurePub restriction status. If on, Levis on private freqs may not make kills,
        or else be switched into a new ship.
        @param b True if you'll allow Levis to make kills on your privates; false if you won't
    */
    public void setSortaPurePubStatus(boolean b) {
        isSortaPurePub = b;
    }

    /**
        Lists any ship restrictions in effect.
    */
    public void doRestrictionsCmd(String sender)
    {
        int weight;
        m_botAction.sendSmartPrivateMessage(sender, "Ship limitations/restrictions (if any)" );

        for( int i = 1; i < 9; i++ ) {
            weight = shipWeight.get( i ).intValue();

            if( weight == 0 )
                m_botAction.sendSmartPrivateMessage(sender, Tools.shipName( i ) + "s disabled." );
            else if( weight > 1 )
                m_botAction.sendSmartPrivateMessage(sender, Tools.shipName( i ) + "s limited to 1/" + weight + " of the size of a frequency (but 1 always allowed).");
        }

        m_botAction.sendSmartPrivateMessage(sender, "Private frequencies are " + (context.getPubUtil().isPrivateFrequencyEnabled() ? "enabled." : "disabled.") );
    }

    /**
        This method checks to see if a player is in a restricted ship, or the
        weight for the given ship has been reached.  If either is true, then the
        player is specced.

        Weights can be thought of as a denominator (bottom number) of a fraction,
        the fraction saying how much of the freq can be made up of ships of this
        type.  If the weight is 0, no ships of a type are allowed.  Weight of 1
        gives a fraction of 1/1, or a whole -- the entire freq can be made up of
        this ship.  Following that, 2 is half, 3 is a third, 4 is a fourth, etc.
        Play with what weight seems right to you.

        Note that even with a very small freq, if a weight is 1 or greater, 1 ship
        of this type is ALWAYS allowed.

        Value for ship "weights":

        0  - No ships of this type allowed
        1  - Unlimited number of ships of this type are allowed
        #  - If the number of current ships of the type on this frequency is
            greater than the total number of people on the frequency divided
            by this number (ships of this type &gt; total ships / weight), then the
            ship is not allowed.  Exception to this rule is if the player is the
            only one on the freq currently in the ship.

        @param playerID is the player to be checked.
    */
    public void checkPlayer(int playerID)
    {
        Player player = m_botAction.getPlayer(playerID);

        if( player == null )
            return;

        // Re-enable if doing a hard shipset with purepub buys
        /*
            if (isSortaPurePub) {
            if (player.getFrequency() > 1 && player.getShipType() == Tools.Ship.LEVIATHAN) {
                Random r = new Random();
                m_botAction.sendPrivateMessage(playerID, "[SORTAPUREPUB] Levis are not currently allowed on private freqs.");
                m_botAction.setFreq(playerID, r.nextInt(2));
                return;
            }
            }
        */

        int weight = shipWeight.get(player.getShipType()).intValue();

        // If weight is 1, unlimited number of that shiptype is allowed.  (Spec is also set to 1.)
        if( weight == 1 )
            return;

        // If weight is 0, ship is completely restricted.
        if( weight == 0 ) {

            int randomShip = player.getShipType();

            while( randomShip == player.getShipType()
                    && shipWeight.get(randomShip) == 0) {
                randomShip = new Random().nextInt(8);

                if(randomShip == 0)
                    randomShip = player.getShipType();
            }

            PubPlayer pubPlayer = context.getPlayerManager().getPlayer(player.getPlayerName());

            if (pubPlayer != null && pubPlayer.hasShipItem()) {
                PubShipItem item = pubPlayer.getShipItem();

                if (item.getShipNumber() == player.getShipType()) {
                    return;
                }
            }

            m_botAction.setShip(playerID, randomShip);
            m_botAction.sendSmartPrivateMessage(m_botAction.getPlayerName(playerID), "That ship has been restricted in this arena.");
            m_botAction.sendSmartPrivateMessage(m_botAction.getPlayerName(playerID), "Please choose another, or type ?arena to select another arena. You've been put randomly in ship " + randomShip);

            return;
        }

        // For all other weights, we must decide whether they can play based on the
        // number of people on freq who are also using the ship.
        Iterator<Player> i = m_botAction.getPlayingPlayerIterator();

        if( i == null)
            return;

        int freqTotal = 0;
        int numShipsOfType = 0;

        Player dummy;

        while( i.hasNext() ) {
            dummy = (Player)i.next();

            if( dummy != null) {
                if( dummy.getFrequency() == player.getFrequency() ) {
                    freqTotal++;

                    if( dummy.getShipType() == player.getShipType() )
                        numShipsOfType++;
                }
            }
        }

        // Free pass if you're the only one on the freq, regardless of weight.
        if( numShipsOfType <= 1 )
            return;

        if( freqTotal == 0 ) {
            m_botAction.sendSmartPrivateMessage(m_botAction.getPlayerName(playerID), "Problem locating your freq!  Please contact a mod with ?help.");
            return;
        }

        if( numShipsOfType > freqTotal / weight ) {
            // If unlimited spiders are allowed, set them to spider; else spec
            if( shipWeight.get(3).intValue() == 1 ) {
                m_botAction.setShip(playerID, 3);
                m_botAction.sendSmartPrivateMessage(m_botAction.getPlayerName(playerID), "There are too many ships of that kind (" + (numShipsOfType - 1) + "), or not enough people on the freq to allow you to play that ship.");
            } else {
                m_botAction.spec(playerID);
                m_botAction.spec(playerID);
                m_botAction.sendSmartPrivateMessage(m_botAction.getPlayerName(playerID), "There are too many ships of that kind (" + (numShipsOfType - 1) + "), or not enough people on the freq to allow you to play that ship.  Please choose another.");
            }
        }
    }

    /**
        Sets a player to a freq and updates the freq lists.
    */
    private void addToLists(String playerName, int freq)
    {
        String lowerName = playerName.toLowerCase();

        if(freq == pubsystem.FREQ_0)
            freq0.add(lowerName);

        if(freq == pubsystem.FREQ_1)
            freq1.add(lowerName);
    }

    private void checkLowPopSpawn() {
        int numPlayers = m_botAction.getFrequencySize(0) + m_botAction.getFrequencySize(1);
        int numPrivPlayers = m_botAction.getNumPlayers() - numPlayers;

        if (numPrivPlayers > 0)
            numPlayers += (numPrivPlayers / 2);

        if (!lowPopSpawning) {
            if (numPlayers <= MAX_MID_SPAWN)
                lowPopSpawning = true;
        } else if (numPlayers > MAX_MID_SPAWN && lowPopSpawning)
            lowPopSpawning = false;
    }

    /**
        Removes a playerName from the freq tracking lists.
    */
    private void removeFromLists(String playerName) {
        String lowerName = playerName.toLowerCase();
        freq0.remove(lowerName);
        freq1.remove(lowerName);
    }

    public void enableLowPopWarp() {
        if(!lowPopSpawning)
            lowPopSpawning = true;

    }

    public boolean isLowPopWarp() {
        return lowPopSpawning;
    }

    /**
        Checks to see if a player is on a private freq.  If they are then
        they are changed to the pub freq with the fewest number of players.

        @param Player player is the player to check.
        @param changeMessage is true if a changeMessage will be displayed.
    */
    private void checkFreq(int playerID, int freq, boolean changeMessage)
    {
        Player player = m_botAction.getPlayer(playerID);

        if( player == null )
            return;

        String playerName = player.getPlayerName();

        /*  Disabled until hunt re-enabled
            if (context.getPubHunt().isPlayerPlaying(playerName)) {
            return;
            }
        */

        int ship = player.getShipType();
        int newFreq = freq;

        if( playerName == null )
            return;

        removeFromLists(playerName);

        if(ship != pubsystem.SPEC)
        {
            if(player != null && freq != pubsystem.FREQ_0 && freq != pubsystem.FREQ_1)
            {
                if(freq0.size() <= freq1.size())
                    newFreq = pubsystem.FREQ_0;
                else
                    newFreq = pubsystem.FREQ_1;

                if(changeMessage)
                    m_botAction.sendSmartPrivateMessage(playerName, "Private frequencies are currently disabled. You have been placed on a public frequency.");

                m_botAction.setFreq(playerName, newFreq);
            }

            addToLists(playerName, newFreq);
        }
    }



    /**
        Checks for imbalance in frequencies, and requests the stacked freq to even it up
        if there's a significant gap.
        @param midRoundCheck True if checking sizes mid-round (needs larger gap)
    */
    public void checkFreqSizes( boolean midRoundCheck )
    {
        if( MSG_AT_FREQSIZE_DIFF == -1)
            return;

        int freq0 = getPlayingFrequencySizeIgnoringBots(0);
        int freq1 = getPlayingFrequencySizeIgnoringBots(1);
        int diff = java.lang.Math.abs( freq0 - freq1 );

        if( diff == freqSizeInfo[0] )
            return;

        freqSizeInfo[0] = diff;
        int neededDiff = (midRoundCheck ? MIDROUND_FREQSIZE_DIFF : MSG_AT_FREQSIZE_DIFF);

        if( freqSizeInfo[0] >= neededDiff ) {
            switchAllowed = true;
            String msg = "TEAMS UNEVEN: " + freq0 + "v" + freq1 + ". Need " + freqSizeInfo[0] / 2 + " volunteer" + (freqSizeInfo[0] / 2 == 1 ? "" : "s")
                         + " to switch freqs. Type :tw-p:!switch to accept. Reward: $" + NICEGUY_BOUNTY_AWARD + "!";

            if( freq0 > freq1 ) {
                m_botAction.sendOpposingTeamMessageByFrequency(0, msg);
                freqSizeInfo[1] = 1;
            } else {
                m_botAction.sendOpposingTeamMessageByFrequency(1, msg);
                freqSizeInfo[1] = 0;
            }
        } else {
            switchAllowed = false;
        }
    }

    public int getPlayingFrequencySizeIgnoringBots(int freq) {
        int size = 0;

        Iterator<Player> i = m_botAction.getFreqPlayerIterator(freq);

        while (i.hasNext()) {
            Player p = i.next();

            if (!m_botAction.getOperatorList().isBotExact(p.getPlayerName())) {
                size++;
            }
        }

        return size;
    }

    public void checkCanSwitchToPrivate( int pid, int freq ) {
        if (MAX_EXTRA_ON_PRIVATES == 999)
            return;

        int freq0 = m_botAction.getPlayingFrequencySize(0);
        int freq1 = m_botAction.getPlayingFrequencySize(1);
        int privatePlayers = m_botAction.getPlayingFrequencySize(freq);

        if (privatePlayers >= freq0 + MAX_EXTRA_ON_PRIVATES || privatePlayers >= freq1 + MAX_EXTRA_ON_PRIVATES) {
            if( freq0 > freq1 )
                m_botAction.setFreq( pid, 1 );
            else
                m_botAction.setFreq( pid, 0 );

            m_botAction.sendPrivateMessage(pid, "You have been placed back on a public frequency; moving to this private freq would make the game too imbalanced.");
        }

    }


    public void checkSpecTime() {
        Iterator<Player> i = m_botAction.getPlayerIterator();

        while (i.hasNext()) {
            Player p = i.next();

            if (p.isShip(Tools.Ship.SPECTATOR)) {
                PubPlayer pp = players.get( p.getPlayerName().toLowerCase() );

                if (pp != null)
                    pp.checkSpecTime();
            }
        }
    }


    /**
        Handles the !switch command duirng voting for the checkFreqSizes

    */
    public void doFrequencySwitch(int playerID) {
        PubPlayer pubPlayer =  context.getPlayerManager().getPlayer(m_botAction.getPlayerName(playerID));

        if (pubPlayer != null) {
            freqSizeInfo[0] = freqSizeInfo[0] - 1;

            //Switches the players frequency
            if (freqSizeInfo[1] == 1)
                m_botAction.setFreq(playerID, 1);
            else
                m_botAction.setFreq(playerID, 0);

            //Checks if the player has recieved a reward in the last 10 minutes.
            if (pubPlayer.getLastSwitchReward() != -1) {
                long diff = System.currentTimeMillis() - pubPlayer.getLastSwitchReward();

                if (diff < 10 * Tools.TimeInMillis.MINUTE) {
                    m_botAction.sendPrivateMessage(playerID, "You have been switched but you may only receive one award every 10 minutes.");
                    return;
                }
            }

            m_botAction.sendPrivateMessage(playerID, "Thank you for switching!  You have recieved " + NICEGUY_BOUNTY_AWARD + "$");
            addMoney(m_botAction.getPlayerName(playerID), NICEGUY_BOUNTY_AWARD);
            pubPlayer.setLastSwitchReward();
            checkFreqSizes(false);
        }
    }


    public void checkSizesAndShuffle(int dx) {
        if (SHUFFLE_SIZE < 0 || dx < SHUFFLE_SIZE)
            return;

        int freq0 = m_botAction.getPlayingFrequencySize(0);
        int freq1 = m_botAction.getPlayingFrequencySize(1);
        int diff = java.lang.Math.abs( freq0 - freq1 );

        if (diff >= SHUFFLE_SIZE)
            doShuffleVote();
    }

    public void checkSizesAndShuffle(String name) {
        if (SHUFFLE_SIZE < 0)
            return;

        int freq0 = m_botAction.getPlayingFrequencySize(0);
        int freq1 = m_botAction.getPlayingFrequencySize(1);
        int diff = java.lang.Math.abs( freq0 - freq1 );

        if (diff >= SHUFFLE_SIZE)
            doShuffleVote();
        else
            m_botAction.sendSmartPrivateMessage(name, "The teams are not uneven enough to constitute a shuffle vote.");
    }

    public boolean checkSizes() {
        int freq0 = m_botAction.getPlayingFrequencySize(0);
        int freq1 = m_botAction.getPlayingFrequencySize(1);
        int diff = java.lang.Math.abs( freq0 - freq1 );

        if (diff >= SHUFFLE_SIZE)
            return true;
        else
            return false;

    }

    public void doShuffleVote() {
        if (notify)
            m_botAction.sendSmartPrivateMessage("WingZero", "Shuffle vote in progress...");

        shuffleVoting = true;
        votes = new TreeMap<Integer, Integer>();
        m_botAction.sendOpposingTeamMessageByFrequency(0, "[TEAM SHUFFLE POLL] Teams may be imbalanced. You have 45 seconds to vote to shuffle teams!");
        m_botAction.sendOpposingTeamMessageByFrequency(0, " 1 = Yes");
        m_botAction.sendOpposingTeamMessageByFrequency(0, " 2 = No");
        m_botAction.sendOpposingTeamMessageByFrequency(0, "PM your vote to " + m_botAction.getBotName() + " ( :tw-pub:<number> )");
        m_botAction.sendOpposingTeamMessageByFrequency(1, "[TEAM SHUFFLE POLL] Teams may be unbalanced: You have 45 seconds to vote to shuffle teams!");
        m_botAction.sendOpposingTeamMessageByFrequency(1, " 1 = Yes");
        m_botAction.sendOpposingTeamMessageByFrequency(1, " 2 = No");
        m_botAction.sendOpposingTeamMessageByFrequency(1, "PM your vote to " + m_botAction.getBotName() + " ( :tw-pub:<number> )");
        TimerTask count = new TimerTask() {
            public void run() {
                shuffleVoting = false;
                int[] results = {0, 0};

                for (Integer v : votes.values())
                    results[v - 1]++;

                int win = 0;

                if (results[0] == results[1])
                    win = r.nextInt(2);
                else if (results[0] < results[1])
                    win = 1;

                m_botAction.sendOpposingTeamMessageByFrequency(0, "[TEAM SHUFFLE POLL] Results: Teams will " + (win == 0 ? "be SHUFFLED." : "NOT be shuffled."));
                m_botAction.sendOpposingTeamMessageByFrequency(0, " YES ... " + results[0] + " votes");
                m_botAction.sendOpposingTeamMessageByFrequency(0, "  NO ... " + results[1] + " votes");
                m_botAction.sendOpposingTeamMessageByFrequency(1, "[TEAM SHUFFLE POLL] Results: Teams will " + (win == 0 ? "be SHUFFLED." : "NOT be shuffled."));
                m_botAction.sendOpposingTeamMessageByFrequency(1, " YES ... " + results[0] + " votes");
                m_botAction.sendOpposingTeamMessageByFrequency(1, "  NO ... " + results[1] + " votes");

                if (win == 0) {
                    m_botAction.sendArenaMessage("[TEAM SHUFFLE] Shuffling public frequencies by popular demand!");
                    shuffle();
                }
            }
        };
        m_botAction.scheduleTask(count, 45 * Tools.TimeInMillis.SECOND);
    }

    /**
        Shuffles the public frequencies
    */
    public void shuffle() {
        if (notify)
            m_botAction.sendSmartPrivateMessage("WingZero", "Shuffling teams!");

        ArrayList<Integer> plist = new ArrayList<Integer>();
        Iterator<Integer> i = m_botAction.getFreqIDIterator(0);
        int s = m_botAction.getFrequencySize(0);

        while (s > 0 && i.hasNext())
            plist.add(i.next());

        i = m_botAction.getFreqIDIterator(1);
        s = m_botAction.getFrequencySize(1);

        while (s > 0 && i.hasNext())
            plist.add(i.next());

        int freqSize = plist.size() / 2;

        for (int j = 0; j < freqSize; j++) {
            if (!plist.isEmpty()) {
                int id = plist.remove(r.nextInt(plist.size()));
                m_botAction.setFreq(id, 0);

                for (PubItem pi : getPlayer(m_botAction.getPlayerName(id)).getItemsBoughtThisLife()) {
                    if (pi instanceof PubShipUpgradeItem) {
                        for(int prizeNumber : ((PubShipUpgradeItem) pi).getPrizes()) {
                            m_botAction.specificPrize(id, prizeNumber);
                        }
                    }
                }
            }
        }

        while (!plist.isEmpty()) {
            int id = plist.remove(r.nextInt(plist.size()));
            m_botAction.setFreq(id, 1);

            for (PubItem pi : getPlayer(m_botAction.getPlayerName(id)).getItemsBoughtThisLife()) {
                if (pi instanceof PubShipUpgradeItem) {
                    for(int prizeNumber : ((PubShipUpgradeItem) pi).getPrizes()) {
                        m_botAction.specificPrize(id, prizeNumber);
                    }
                }
            }
        }
    }

    /**
        Fixes the freq of each player.
    */
    public void fixFreqs()
    {
        Iterator<Player> iterator = m_botAction.getPlayingPlayerIterator();
        Player player;

        fillFreqLists();

        while(iterator.hasNext())
        {
            player = (Player) iterator.next();
            checkFreq(player.getPlayerID(), player.getFrequency(), false);
        }
    }


    /**
        Fills the freq lists for freqs 1 and 0.
    */
    private void fillFreqLists()
    {
        Iterator<Player> iterator = m_botAction.getPlayingPlayerIterator();
        Player player;
        String lowerName;

        freq0.clear();
        freq1.clear();

        while(iterator.hasNext())
        {
            player = (Player) iterator.next();
            lowerName = player.getPlayerName().toLowerCase();

            if(player.getFrequency() == pubsystem.FREQ_0)
                freq0.add(lowerName);

            if(player.getFrequency() == pubsystem.FREQ_1)
                freq1.add(lowerName);
        }
    }


    private class SavePlayersTask extends TimerTask {

        private boolean force = false;

        public void force() {
            force = true;
            run();
        }

        public void run() {

            Collection<String> arenaPlayers = new ArrayList<String>();
            Iterator<Player> it = m_botAction.getPlayerIterator();

            while(it.hasNext()) {
                Player p = it.next();
                arenaPlayers.add(p.getPlayerName());
            }

            Iterator<PubPlayer> it2 = players.values().iterator();

            while(it2.hasNext()) {

                PubPlayer player = it2.next();

                // Money is always saved
                if (databaseName != null) {
                    if (force || player.getLastMoneyUpdate() > player.getLastMoneySavedState())
                    {
                        if (player.hasStatsDB())
                        {
                            m_botAction.SQLBackgroundQuery(databaseName, "moneydb:" + player.getPlayerName() + ":" + player.getMoney() + ":" + (force ? "1" : "0"), "UPDATE tblPlayerStats Set fnMoney=" + player.getMoney() + ", fbWarp=" + (player.getWarp() ? 1 : 0 ) + " where fcName = '" + Tools.addSlashes(player.getPlayerName()) + "'");
                        }
                        else
                        {
                            m_botAction.SQLBackgroundQuery(databaseName, "moneydb:" + player.getPlayerName() + ":" + player.getMoney() + ":" + (force ? "1" : "0"), "INSERT INTO tblPlayerStats (fcName,fnMoney,fbWarp) VALUES ('" + Tools.addSlashes(player.getPlayerName()) + "'," + player.getMoney() + "," + (player.getWarp() ? 1 : 0) + ")");
                            player.setHasStatsDB(true);
                        }

                        player.moneySavedState();
                    }

                    //12Aug2013 POiD    Commented out the below updates as currently no updates to tblPlayer are needed. Left code in case of future changes.
                    //
                    //if (player.getLastOptionsUpdate() > player.getLastSavedState()) {
                    //String tilesetName = player.getTileset().toString().toLowerCase();
                    //long playerID = player.getPlayerID();
                    //if (playerID != -1)
                    //{
                    //  m_botAction.SQLBackgroundQuery(databaseName,null, "UPDATE tblPlayer Set fbWarp = " + (player.getWarp() ? 1 : 0) + " WHERE fnID= " + playerID);
                    //}
                    //else
                    //{
                    //  m_botAction.SQLBackgroundQuery(databaseName,null, "INSERT INTO tblPlayer (fcName,fbWarp,fcIP,fdLastSeen,fnOnline) VALUES ('" + Tools.addSlashes(player.getPlayerName())+"'," + (player.getWarp() ? 1 : 0) + ")");
                    //  m_botAction.SQLBackgroundQuery(databaseName,"warpInsert:"+player.getPlayerName(), "SELECT fnID from tblPlayer WHERE fcName = '" + Tools.addSlashes(player.getPlayerName())+"'");
                    //}
                    //m_botAction.SQLBackgroundQuery(databaseName, null, "INSERT INTO tblPlayer (fcName,fbWarp) VALUES ('"+Tools.addSlashes(player.getPlayerName())+"'," + (player.getWarp() ? 1 : 0) + ") ON DUPLICATE KEY UPDATE fbWarp=" + (player.getWarp() ? 1 : 0));
                    //player.savedState();
                    //}

                }

                // Not anymore on this arena? remove this player from the PubPlayerManager
                if (m_botAction.getPlayer(player.getPlayerName()) == null) {
                    it2.remove();
                }
            }

            force = false;
        }
    }

    /**
        This method sets the value to be deducted from a players account for TKing
        @param sender String
        @param command String
    */
    public void doSetTeamKillTax(String sender, String command) {
        int newTkTax = 0;

        try {
            newTkTax = Integer.valueOf(command.substring(command.indexOf(" ") + 1));
        } catch (NumberFormatException e) {
            m_botAction.sendPrivateMessage( sender, "Invalid syntax. Please use use !tax <value>, where <value> is an integer greater than 0.");
            return;
        }

        if (newTkTax > -1) {
            tkTax = newTkTax;
            m_botAction.sendPrivateMessage( sender, "The tax deduction for team killing has been set to " + tkTax);
        } else
            m_botAction.sendPrivateMessage( sender, "Invalid syntax. Please use use !tax <value>, where <value> is an integer greater than 0.");
    }

    public void doGetTeamKillTax(String sender, String command) {
        m_botAction.sendSmartPrivateMessage(sender, "The current teamkill tax is $" + tkTax);
    }

    public void doSetShuffleSize(String name, String msg) {
        msg = msg.substring(msg.indexOf(" ") + 1);
        int n = -1;

        try {
            n = Integer.valueOf(msg);
        } catch (NumberFormatException e) {
            m_botAction.sendPrivateMessage(name, "Error converting " + msg);
            return;
        }

        SHUFFLE_SIZE = n;
        m_botAction.sendPrivateMessage(name, "Freq difference shuffle trigger = " + SHUFFLE_SIZE);
    }

    /**
        Send a staff member to spectator mode. SMod+
    */
    public void doSpecStaff(String name, String msg) {
        msg = msg.substring(msg.indexOf(" ") + 1);

        Player p = m_botAction.getPlayer( msg );

        if (p == null) {
            p = m_botAction.getFuzzyPlayer( msg );

            if (p == null ) {
                m_botAction.sendPrivateMessage(name, "Can't find a player corresponding to '" + msg + "'");
                return;
            }
        }

        m_botAction.specWithoutLock( p.getPlayerID() );
        m_botAction.sendPrivateMessage(name, "'" + p.getPlayerName() + "' sent to spec.");
    }


    @Override
    public void handleCommand(String sender, String command) {

    }

    @Override
    public void handleModCommand(String sender, String command) {
        if(command.trim().equals("!tax")) {
            doGetTeamKillTax(sender, command);
        } else if(command.trim().startsWith("!tax ")) {
            doSetTeamKillTax(sender, command);
        }
    }

    @Override
    public void handleSmodCommand(String sender, String command) {
        if (command.trim().equals("!vote")) {
            doShuffleVote();
        } else if (command.trim().equals("!forceshuffle")) {
            shuffle();
        } else if (command.startsWith("!trigger ")) {
            doSetShuffleSize(sender, command);
        } else if (command.startsWith("!specstaff ")) {
            doSpecStaff(sender, command);
        } else if(command.equals("!debug")) {
            for(PubPlayer p : players.values()) {
                m_botAction.sendSmartPrivateMessage(sender, p.getPlayerName());
            }
        } else if (sender.equalsIgnoreCase("WingZero") && command.equals("!notify")) {
            setNotify();
        }
    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[] {
                   pubsystem.getHelpLine("!vote             -- Force a vote to shuffle teams."),
                   pubsystem.getHelpLine("!forceshuffle     -- Force shuffle teams."),
                   pubsystem.getHelpLine("!trigger          -- Set the freq size difference trigger for shuffle vote (-1 is off)."),
                   pubsystem.getHelpLine("!specstaff <name> -- Spec SMod+ who is AFK"),
               };
    }

    @Override
    public String[] getHelpMessage(String sender) {
        return new String[] {};
    }

    @Override
    public String[] getModHelpMessage(String sender) {
        return new String[] {
                   "   !tax <$>          -- Sets <$> as the amount deducted for teamkills",
                   "   !tax              -- Shows the current teamkill tax"
               };
    }

    @Override
    public void start() {

    }


    @Override
    public void reloadConfig()
    {
        this.databaseName = m_botAction.getBotSettings().getString("database");
        this.shipWeight = new Vector<Integer>();
        shipWeight.add(new Integer(1));     // Allow unlimited number of spec players
        String[] restrictions = m_botAction.getBotSettings().getString("ShipRestrictions" + m_botAction.getBotNumber()).split(",");

        for(String r : restrictions) {
            shipWeight.add(new Integer(r));
        }

        NICEGUY_BOUNTY_AWARD =  Integer.valueOf(m_botAction.getBotSettings().getString("niceguy_bounty_award"));
        MAX_MID_SPAWN =  Integer.valueOf(m_botAction.getBotSettings().getString("max_mid_spawn"));
        MSG_AT_FREQSIZE_DIFF = Integer.valueOf(m_botAction.getBotSettings().getString("msg_at_freq_diff"));
        MIDROUND_FREQSIZE_DIFF = Integer.valueOf(m_botAction.getBotSettings().getString("midround_freq_diff"));
        SHUFFLE_SIZE = Integer.valueOf(m_botAction.getBotSettings().getString("shuffle_size"));
        MAX_EXTRA_ON_PRIVATES = m_botAction.getBotSettings().getInt("max_extra_on_privates");
    }


    @Override
    public void stop() {

    }

    private void setNotify() {
        notify = !notify;

        if (notify)
            m_botAction.sendSmartPrivateMessage("WingZero", "Notify ENABLED");
        else
            m_botAction.sendSmartPrivateMessage("WingZero", "Notify DISABLED");
    }
}


