/**
 * Warp module for TWBot.
 *
 * This module allows the user 4 methods of warping:
 *
 * 1) Warping everyone to a location.
 * 2) Warping a freq to a location.
 * 3) Warping a ship type to a location.
 * 4) Setup warping for events.
 *
 * The syntax for the spec module is as follows:
 *
 * This module acts much like one would imagine, however there is now the option
 * to warp within a radius.  This is done by adding a radius to the end of the
 * warp command.  Please be advised that if there are any rocks or walls in the
 * warping area, some ships might end up back in the spawn area.  I will remedy
 * this once the bot is able to read maps.
 *
 * The setup warping feature is done as follows:
 * !Setupwarp &lt;Argument&gt;
 *
 * The arguments can be seen when you type !Setupwarplist.
 * The warp coordinates are stored in the Trench Wars Database and must be added
 * before you use them.  If the arena has not been entered into the database,
 * please tell me in game and i will stick it in.
 *
 * Here is the help menu:
 * !Warpto <X>:<Y>:<Radius>                  -- Warps everyone to <X>, <Y> within a distance of <Radius>."
 * !WarpFreq <Freq>:<X>:<Y>:<Radius>         -- Warps freq <Freq> to <X>, <Y> within a distance of <Radius>."
 * !WarpShip <Ship>:<X>:<Y>:<Radius>         -- Warps ship <Ship> to <X>, <Y> within a distance of <Radius>."
 * !SetupWarp &lt;Argument&gt;                     -- Performs the setup warp for this arena based on the <Argument>."
 * !SetupWarpList                            -- Displays the setup warp information."
 * !Where                                    -- Shows your current coords."
 *
 * NOTE: The !warpto command is removed from the standard module so please !load
 * warp first.
 *
 * Author: Cpt.Guano!
 * July 06, 2003
 * Added Spawn options January 01, 2008 -milosh
 */

package twcore.bots.multibot.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;
import java.util.TimerTask;
import java.util.ArrayList;

import twcore.bots.MultiUtil;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.game.Player;
import twcore.core.util.CodeCompiler;
import twcore.core.util.ModuleEventRequester;

public class utilwarp extends MultiUtil {
    public static final int WARP_ALL = 1;
    public static final int WARP_FREQ = 2;
    public static final int WARP_SHIP = 3;
    public static final int WARP_FIRST_FREQ = 4;
    
    public static final int MAX_FREQ = 9999;
    public static final int MIN_FREQ = 0;
    public static final int MAX_SHIP = 8;
    public static final int MIN_SHIP = 1;
    
    public static final int MIN_COORD = 1;
    public static final int MAX_COORD = 1022;
    
    public static final int SPAWN_TIME = 5005;
    
    private ArrayList<String> spawnTasks;
    
    private static final String database = "website";
    
    public void init() {
        spawnTasks = new ArrayList<String>();
    }
    
    public void requestEvents(ModuleEventRequester modEventReq) {}
    
    public String[] getHelpMessages() {
        String[] message = {
                "!Warpto <X>:<Y>:<Radius>                  -- Warps everyone to <X>, <Y> within a distance of <Radius>.",
                "!WarpFreq <Freq>:<X>:<Y>:<Radius>         -- Warps freq <Freq> to <X>, <Y> within a distance of <Radius>.",
                "!WarpShip <Ship>:<X>:<Y>:<Radius>         -- Warps ship <Ship> to <X>, <Y> within a distance of <Radius>.",
                "!Spawn <Message>                          -- Adds a spawn message <Message>",
                "!SpawnList                                -- Shows a list of all spawn tasks.",
                "!SpawnDel <Index>                         -- Deletes the spawn task at index <Index>",
                "!SpawnOff                                 -- Removes all spawn tasks.",
                "!SetupWarp <Argument>                     -- Performs the setup warp for this arena based on the <Argument>.",
                "!SetupWarpList                            -- Displays the setup warp information.",
                "!Whereami                                 -- Shows your current coords."
        };
        return message;
    }
    
    public void doWarpToCmd(String sender, String argString) {
        StringTokenizer argTokens = getArgTokens(argString);
        int numTokens = argTokens.countTokens();
        
        if (numTokens < 2 || numTokens > 3)
            throw new IllegalArgumentException("Please use the following format: !WarpTo <X>:<Y>:<Radius>.");
        try {
            int xCoord = Integer.parseInt(argTokens.nextToken());
            int yCoord = Integer.parseInt(argTokens.nextToken());
            double radius = 0;
            if (numTokens == 3)
                radius = Double.parseDouble(argTokens.nextToken());
            doWarp(WARP_ALL, 0, xCoord, yCoord, radius, false);
            m_botAction.sendSmartPrivateMessage(sender, getWarpString(WARP_ALL, 0, xCoord, yCoord, radius));
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Please use the following format: !WarpTo <X>:<Y>:<Radius>.");
        }
    }
    
    public void doWarpFreqCmd(String sender, String argString) {
        StringTokenizer argTokens = getArgTokens(argString);
        int numTokens = argTokens.countTokens();
        
        if (numTokens < 3 || numTokens > 4)
            throw new IllegalArgumentException("Please use the following format: !WarpFreq <Freq>:<X>:<Y>:<Radius>.");
        try {
            int freq = Integer.parseInt(argTokens.nextToken());
            int xCoord = Integer.parseInt(argTokens.nextToken());
            int yCoord = Integer.parseInt(argTokens.nextToken());
            double radius = 0;
            if (numTokens == 4)
                radius = Double.parseDouble(argTokens.nextToken());
            doWarp(WARP_FREQ, freq, xCoord, yCoord, radius, false);
            m_botAction.sendSmartPrivateMessage(sender, getWarpString(WARP_FREQ, freq, xCoord, yCoord, radius));
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Please use the following format: !WarpFreq <Freq>:<X>:<Y>:<Radius>.");
        }
    }
    
    public void doWarpShipCmd(String sender, String argString) {
        StringTokenizer argTokens = getArgTokens(argString);
        int numTokens = argTokens.countTokens();
        
        if (numTokens < 3 || numTokens > 4)
            throw new IllegalArgumentException("Please use the following format: !WarpShip <Ship>:<X>:<Y>:<Radius>.");
        try {
            int ship = Integer.parseInt(argTokens.nextToken());
            int xCoord = Integer.parseInt(argTokens.nextToken());
            int yCoord = Integer.parseInt(argTokens.nextToken());
            double radius = 0;
            if (numTokens == 4)
                radius = Double.parseDouble(argTokens.nextToken());
            doWarp(WARP_SHIP, ship, xCoord, yCoord, radius, false);
            m_botAction.sendSmartPrivateMessage(sender, getWarpString(WARP_SHIP, ship, xCoord, yCoord, radius));
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Please use the following format: !WarpShip <Ship>:<X>:<Y>:<Radius>.");
        }
    }
    
    /**
     * This handles the player death event and spawns if needed.
     * 
     * @param event
     *            is the player death event.
     */
    public void handleEvent(PlayerDeath event) {
        if (spawnTasks.size() > 0) {
            new SpawnTimer(m_botAction.getPlayer(event.getKilleeID()));
        }
    }
    
    /**
     * This method displays a list of all of the spawn tasks.
     * 
     * @param sender
     *            is the person that messaged the bot.
     */
    public void doSpawnListCmd(String sender) {
        int numTasks = spawnTasks.size();
        
        if (numTasks == 0)
            m_botAction.sendSmartPrivateMessage(sender, "There are currently no spawn tasks.");
        else {
            for (int index = 0; index < numTasks; index++) {
                m_botAction.sendSmartPrivateMessage(sender, "Task " + index + ") " + spawnTasks.get(index));
            }
        }
    }
    
    /**
     * This method removes a spawn task.
     * 
     * @param sender
     *            is the person that messaged the bot.
     * @param argString
     *            is the spawn task number that is to be removed.
     */
    public void doSpawnDelCmd(String sender, String argString) {
        try {
            int taskNumber = Integer.parseInt(argString);
            spawnTasks.remove(taskNumber);
            m_botAction.sendSmartPrivateMessage(sender, "Task at index " + taskNumber + " removed.");
        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(sender, "Please use the following format: !SpawnDel <Index>.");
        }
    }
    
    /**
     * This method clears all of the spawn tasks.
     */
    public void doSpawnOffCmd(String sender) {
        if (!spawnTasks.isEmpty()) {
            spawnTasks.clear();
            m_botAction.sendSmartPrivateMessage(sender, "All spawn tasks have been cleared.");
        } else {
            m_botAction.sendSmartPrivateMessage(sender, "There are no spawn tasks to remove.");
        }
    }
    
    /**
     * This method spawns all players at a specified location.
     * 
     * @param sender
     *            is the person operating the bot.
     * @param argString
     *            is the string of the arguments.
     */
    public void doSpawnCmd(String sender, String argString) {
        if (CodeCompiler.isAllowed(argString) || m_opList.isSmod(sender)) {
            spawnTasks.add(argString);
            m_botAction.sendSmartPrivateMessage(sender, "Spawn task added.");
        } else
            m_botAction.sendSmartPrivateMessage(sender, "Message not added; Restricted or unknown");
    }
    
    public void doSetupWarpListCmd(String sender) {
        String arenaName = m_botAction.getArenaName();
        
        try {
            ResultSet resultSet = m_botAction.SQLQuery(database, "SELECT SW.* " + "FROM tblArena A, tblSetupWarp SW " + "WHERE A.fnArenaID = SW.fnArenaID " + "AND A.fcArenaName = '" + arenaName + "'");
            
            int count = 0;
            while (resultSet.next()) {
                String argument = resultSet.getString("fcArgument").trim();
                String description = resultSet.getString("fcDescription").trim();
                if (description.equals(""))
                    description = "No Description.";
                
                m_botAction.sendSmartPrivateMessage(sender, "!SetupWarp " + padSpaces(argument, 31) + "-- " + description);
                count++;
            }
            m_botAction.SQLClose(resultSet);
            if (count == 0)
                m_botAction.sendSmartPrivateMessage(sender, "No setup warps are registered for this arena.");
        }

        catch (SQLException e) {
            m_botAction.sendSmartPrivateMessage(sender, "ERROR: Cannot connect to database.");
        } catch (NullPointerException e) {
            m_botAction.sendSmartPrivateMessage(sender, "ERROR: Cannot connect to database.");
        }
    }
    
    public void doSetupWarpCmd(String sender, String argument) {
        String arenaName = m_botAction.getArenaName();
        
        try {
            ResultSet resultSet = m_botAction.SQLQuery(database, "SELECT WP.* " + "FROM tblArena A, tblSetupWarp SW, tblWarpPoint WP " + "WHERE WP.fnSetupWarpID = SW.fnSetupWarpID " + "AND SW.fcArgument = '" + argument + "' " + "AND SW.fnArenaID = A.fnArenaID " + "AND A.fcArenaName = '" + arenaName + "'");
            
            int count = 0;
            while (resultSet.next()) {
                int warpType = resultSet.getInt("fnWarpTypeID");
                int warpID = resultSet.getInt("fnWarpSpecifier");
                int xCoord = resultSet.getInt("fnXCoord");
                int yCoord = resultSet.getInt("fnYCoord");
                double radius = (double) resultSet.getInt("fnRadius");
                doWarp(warpType, warpID, xCoord, yCoord, radius, true);
                count++;
            }
            m_botAction.SQLClose(resultSet);
            if (count == 0)
                m_botAction.sendSmartPrivateMessage(sender, "Invalid argument.  Please use !SetupWarpList to see the setup warps available");
            else
                m_botAction.sendSmartPrivateMessage(sender, "Setup warps completed.");
        } catch (SQLException e) {
            m_botAction.sendSmartPrivateMessage(sender, "ERROR: Cannot connect to database.");
        } catch (NullPointerException e) {
            m_botAction.sendSmartPrivateMessage(sender, "ERROR: Cannot connect to database.");
        }
    }
    
    /**
     * PMs sender's current coords.
     * 
     * @param sender
     *            Host who wants desperately to know own position
     */
    public void doWhereCmd(String sender) {
        m_botAction.spectatePlayer(sender);
        Player p = m_botAction.getPlayer(sender);
        if (p != null) {
            m_botAction.sendSmartPrivateMessage(sender, "You are at: (" + new Integer(p.getXLocation() / 16) + "," + new Integer(p.getYLocation() / 16) + ")");
        }
        m_botAction.stopSpectatingPlayer();
        m_botAction.moveToTile(512, 512);
    }
    
    public void doWarp(int warpType, int warpID, int xCoord, int yCoord, double radius, boolean resetGroup) {
        if (warpType < WARP_ALL || warpType > WARP_FIRST_FREQ)
            throw new IllegalArgumentException("ERROR: Unknown warp type.");
        if ((warpID < MIN_FREQ || warpID > MAX_FREQ) && warpType == WARP_FREQ)
            throw new IllegalArgumentException("Invalid freq number.");
        if ((warpID < MIN_SHIP || warpID > MAX_SHIP) && warpType == WARP_SHIP)
            throw new IllegalArgumentException("Invalid ship type.");
        if (!isValidCoord(xCoord, yCoord))
            throw new IllegalArgumentException("Coordinates are out of bounds.");
        if (radius < 0)
            throw new IllegalArgumentException("Invalid warp radius.");
        
        if (warpType == WARP_FIRST_FREQ) {
            Vector<Integer> freqNumbers = getFreqNumbers();
            
            if (warpID < freqNumbers.size()) {
                Integer freq = freqNumbers.get(warpID);
                doWarpGroup(WARP_FIRST_FREQ, freq.intValue(), xCoord, yCoord, radius, resetGroup);
            }
        } else
            doWarpGroup(warpType, warpID, xCoord, yCoord, radius, resetGroup);
    }
    
    public void doWarpGroup(int warpType, int warpID, int xCoord, int yCoord, double radius, boolean resetGroup) {
        Iterator<Player> iterator = m_botAction.getPlayingPlayerIterator();
        Player player;
        
        while (iterator.hasNext()) {
            player = iterator.next();
            if (isWarpable(player, warpType, warpID)) {
                if (resetGroup)
                    m_botAction.shipReset(player.getPlayerName());
                doRandomWarp(player.getPlayerName(), xCoord, yCoord, radius);
            }
        }
    }
    
    public void handleCommand(String sender, String message) {
        String command = message.toLowerCase();
        
        try {
            if (command.startsWith("!warpto "))
                doWarpToCmd(sender, message.substring(8));
            else if (command.startsWith("!warpfreq "))
                doWarpFreqCmd(sender, message.substring(10));
            else if (command.startsWith("!warpship "))
                doWarpShipCmd(sender, message.substring(10));
            else if (command.startsWith("!spawn "))
                doSpawnCmd(sender, message.substring(7));
            else if (command.equalsIgnoreCase("!spawnlist"))
                doSpawnListCmd(sender);
            else if (command.startsWith("!spawndel "))
                doSpawnDelCmd(sender, message.substring(10));
            else if (command.equalsIgnoreCase("!spawnoff"))
                doSpawnOffCmd(sender);
            else if (command.equalsIgnoreCase("!setupwarp"))
                doSetupWarpCmd(sender, "");
            else if (command.startsWith("!setupwarp "))
                doSetupWarpCmd(sender, message.substring(11));
            else if (command.equalsIgnoreCase("!setupwarplist"))
                doSetupWarpListCmd(sender);
            else if (command.equalsIgnoreCase("!whereami"))
                doWhereCmd(sender);
        } catch (RuntimeException e) {}
    }
    
    public void handleEvent(Message event) {
        int senderID = event.getPlayerID();
        String sender = m_botAction.getPlayerName(senderID);
        String message = event.getMessage().trim();
        
        if (m_opList.isER(sender))
            handleCommand(sender, message);
    }
    
    public void cancel() {}
    
    /**
     * Gets the argument tokens from a string. If there are no colons in the
     * string then the delimeter will default to space.
     * 
     * @param string
     *            is the string to tokenize.
     * @return a tokenizer separating the arguments is returned.
     */
    
    private StringTokenizer getArgTokens(String string) {
        if (string.indexOf((int) ':') != -1)
            return new StringTokenizer(string, ":");
        return new StringTokenizer(string);
    }
    
    private Vector<Integer> getFreqNumbers() {
        TreeSet<Integer> freqNumbers = new TreeSet<Integer>();
        Iterator<Player> iterator = m_botAction.getPlayingPlayerIterator();
        Player player;
        
        while (iterator.hasNext()) {
            player = iterator.next();
            freqNumbers.add(new Integer(player.getFrequency()));
        }
        return new Vector<Integer>(freqNumbers);
    }
    
    private boolean isWarpable(Player player, int warpType, int warpID) {
        switch (warpType) {
            case WARP_ALL:
                return true;
            case WARP_FIRST_FREQ:
                return warpID == player.getFrequency();
            case WARP_FREQ:
                return warpID == player.getFrequency();
            case WARP_SHIP:
                return warpID == player.getShipType();
        }
        return false;
    }
    
    private void doRandomWarp(String playerName, int xCoord, int yCoord, double radius) {
        double randRadians;
        double randRadius;
        int xWarp = -1;
        int yWarp = -1;
        
        while (!isValidCoord(xWarp, yWarp)) {
            randRadians = Math.random() * 2 * Math.PI;
            randRadius = Math.random() * radius;
            xWarp = calcXCoord(xCoord, randRadians, randRadius);
            yWarp = calcYCoord(yCoord, randRadians, randRadius);
        }
        m_botAction.warpTo(playerName, xWarp, yWarp);
    }
    
    private int calcXCoord(int xCoord, double randRadians, double randRadius) {
        return xCoord + (int) Math.round(randRadius * Math.sin(randRadians));
    }
    
    private int calcYCoord(int yCoord, double randRadians, double randRadius) {
        return yCoord + (int) Math.round(randRadius * Math.cos(randRadians));
    }
    
    private boolean isValidCoord(int xCoord, int yCoord) {
        return xCoord >= MIN_COORD && xCoord <= MAX_COORD && yCoord >= MIN_COORD && yCoord <= MAX_COORD;
    }
    
    /**
     * This method returns a string representation of a warp.
     * 
     * @param warpType
     *            is the type of warp.
     * @param warpID
     *            defines what gets warped.
     * @param xCoord
     *            is the x coord to warp to.
     * @param yCoord
     *            is the y coord to warp to.
     * @param radius
     *            is the radius within to warp to.
     * @return a string describing the warp is returned.
     */
    
    private String getWarpString(int warpType, int warpID, int xCoord, int yCoord, double radius) {
        StringBuffer warpString = new StringBuffer("Warped ");
        switch (warpType) {
            case WARP_ALL:
                warpString.append("all players");
                break;
            case WARP_FREQ:
                warpString.append("freq " + warpID);
                break;
            case WARP_SHIP:
                warpString.append("ship " + warpID);
                break;
            default:
                return "ERROR: Unknown Warp Type";
        }
        warpString.append(" to " + xCoord + ", " + yCoord);
        if (radius != 0)
            warpString.append(" with a radius of " + radius);
        return warpString.toString() + ".";
    }
    
    /**
     * This method makes a string a certain length, padding with spaces or
     * cutting the string if necessary.
     * 
     * @param string
     *            is the string to pad.
     * @param length
     *            is the length that the string is supposed to be.
     * @return the modified string.
     */
    
    private String padSpaces(String string, int length) {
        if (string.length() > length)
            return string.substring(0, length);
        
        StringBuffer returnString = new StringBuffer(string);
        
        for (int index = 0; index < length - string.length(); index++)
            returnString.append(" ");
        return returnString.toString();
    }
    
    private class SpawnTimer {
        Player p;
        private TimerTask runIt = new TimerTask() {
            public void run() {
                Iterator<String> i = spawnTasks.iterator();
                while (i.hasNext()) {
                    String s = i.next();
                    s = CodeCompiler.replaceKeys(m_botAction, p, s);
                    m_botAction.sendUnfilteredPrivateMessage(p.getPlayerName(), s);
                }
                
            }
        };
        
        public SpawnTimer(Player p) {
            this.p = p;
            m_botAction.scheduleTask(runIt, SPAWN_TIME);
        }
    }
}