package twcore.bots.pubautobot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.Vector;

import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.SubspaceBot;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.WeaponFired;
import twcore.core.game.Player;
import twcore.core.game.Projectile;
import twcore.core.game.Ship;
import twcore.core.util.Point;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

/**
 * PubAutoBot
 * <p>
 * This class is mainly used as a feature for the !buy-system in the pub and 
 * fulfills roles like base terrier, roof turret and flag savior.
 * <p>
 * This bot is branched off of the PracBot.
 *
 * @author milosh - 1.15.08
 */
public class pubautobot extends SubspaceBot {

	private static boolean DEBUG_ENABLED = false;  // Debug mode. Do not use this on live due to potential exploits.

	// Game constant
	private static final double SS_CONSTANT = 0.1111111;   // Constant related to angles.
	private static final int SPAWN_TIME = 5005;            // Respawntime for the bot in ms.
	private static double REACTION_TIME = 0.075;           // Reaction time of the bot?

	// Inter-bot communication
	private static final String IPC_CHANNEL = "pubautobot";

	// Ownership
	private boolean locked = false;            // Wether or not the bot is locked.
	private String owner;                      // Usually a bot, who gave the activation commands to this bot.
	private String subowner;                   // The player who requested this bot at the owner.

	// Settings
	private boolean autoAiming = false;        // When set, automatically tries to track a nearby player.
	private boolean enemyAimingOnly = true;    // Only tracks hostile players.
	private boolean fireOnSight = false;       // Enables firing on targets.
	private boolean following = false;         // Set when the bot is tracking a player.
	private boolean killable = false;          // Allows the bot to be killed.
	private boolean quitOnDeath = false;       // If set, the bot will return to the stable after it dies.
	private boolean fastRotation = true;
	private int timeoutAt = -1;                // Amount of time (seconds) before the bot disappears to the stable.
	//private int energyOnStart = 1; unused      // one shot and dead
	private HashSet<String> locations;         // array of x:y position
	private int dieAtXshots = 1;               // Amount of hits the bot will take before 'dying'. If 1, the first hit kills the bot.
	private long startedAt = 0;                // Time at which this bot was activated. (Read: Being actually in a ship.)

	// In-game settings
	private int freq;                          // Bot's frequency.
	private int botX;                          // Bot's x-coordinate.
	private int botY;                          // Bot's y-coordinate.
	private int angle = 0;                     // Bot's target angle?
	private int angleTo = 0;                   // Bot's current angle?
	private int numberOfShots = 0;             // Number of hits the bot has taken (without dying).

	// Various tracking related settings and lists.
    private int turretPlayerID = -1;                                                    // ID of the player the bot is attached to.
    private LinkedList<Projectile> fired = new LinkedList<Projectile>();                // List for tracking projectiles.
	private Vector<RepeatFireTimer> repeatFireTimers = new Vector<RepeatFireTimer>();   // Stores the weapon types and speeds the bot shoots with and at.
    private RotationTask rotationTask;                                                  // Timer that allows the bot to slowly rotate.
    private DisableEnemyOnSightTask disableEnemyOnSightTask;                            // Timer that allows the bot to go to 'sleep' if it hasn't had an enemy in sight for a while.

	boolean isSpawning = false;            // Is the bot respawning?

	private boolean enemyOnSight = false;  // Enables tracking of a hostile player.
	private String target;                 // Name of the current target.
	
	private boolean m_debug = true;
	private String m_debugger = "ThePAP";

	// This list is disabled and not up to date.
	String[] helpmsg = {
		"!setship #			-- puts the bot into ship number #",
		"!setfreq #         -- operates like *setfreq",
		"!warpto # #		-- operates like *warpto",
		"!face #			-- changes the bot's direction (0-39)",
		"!aim               -- toggles auto-aiming",
		"!fire #			-- fires a weapon. (Most common: 1 and 97)",
		"!repeatfire # #    -- repeats fire - (weapon):(ms repeat)",
	    "!rfire # #         -- shortcut for !repeatfire # #",
		"!listfire          -- displays a list of all firing tasks",
		"!stopfire #        -- stops firing task of index #",
		"!stopfire          -- stops all firing tasks.",
		"!brick             -- drops a brick where the bot is.",
		"!brick # #         -- drops a brick at x, y.",
		"!attach #          -- attach to fuzzy player name",
		"!unattach          -- don't understand? this bot isn't for you",
		"!setaccuracy #     -- set difficulty(0-5) 0 being the highest",
		"!move # # # #      -- move bot to coords (#,#) velocity (#,#)",
		"!follow <optional> -- follow the first player on sight (5 tiles) or via <name>",
		"!spec				-- puts the bot into spectator mode.",

		"Standard Bot Commands:",
		"!go <arena>		-- sends the bot to <arena>",
		"!die				-- initiates shut down sequence",
		"!help				-- displays this"
	};

	TimerTask spawned;     // Timertask used for respawning the bot after it has died. (Only used when quitOnDeath == false.)
	TimerTask updateIt;    // Timertask used for checking the projectiles heading the bot's way.
	TimerTask unlockMe;    // Timertask used to unlock the bot if not set up in time.
	TimerTask freeMe;      // Timertask used to free/reset all the data.

	/**
	 * Constructor
	 * @param botAction botAction
	 */
    public pubautobot(BotAction botAction) {
        super(botAction);
        requestEvents();
        loadConfig();
    }

    /**
     * Loads the configuration for this bot.
     */
    public void loadConfig() {
    	locations = new HashSet<String>();
    	disableEnemyOnSightTask = new DisableEnemyOnSightTask();
    }

    /**
     * Handles the logged on event for this bot.
     */
    public void handleEvent(LoggedOn event) {
    	try {
			m_botAction.joinArena(m_botAction.getBotSettings().getString("Arena"),(short)3392,(short)3392);
		} catch (Exception e) {
			m_botAction.joinArena(m_botAction.getBotSettings().getString("Arena"));
		}
    	m_botAction.ipcSubscribe(IPC_CHANNEL);
    	m_botAction.ipcSendMessage(IPC_CHANNEL, "loggedon", null, m_botAction.getBotName());
    }

    /**
     * Handles the IPC event.
     * <p>
     * Step to request this bot: <pre>
     * 1. RandomBot> Send a "looking"
     * 2. Autobot> If not locked, send a "locked"
     *    If not confirmation after 5 seconds, the bot is free'd
     * 3. RandomBot> Confirm with "confirm_lock"
     * 4. Setup the new owner of this bot</pre>
     */
    public void handleEvent(InterProcessEvent event) {
    	IPCMessage ipc = (IPCMessage)event.getObject();
    	String message = ipc.getMessage();

    	if(message.equals("looking") && !locked) {
    	    // Step 1: Finding an available bot and locking it.
    		locked = true;
    		try {
    		    // Starts a timer to unlock the bot if not all the required steps are executed.
    		    m_botAction.cancelTask(unlockMe);
    		    unlockMe = new UnlockTask();
    		    m_botAction.scheduleTask(unlockMe, 5*Tools.TimeInMillis.SECOND);
    		    // Step 2: Send a confirmation to the requester.
    		    m_botAction.ipcSendMessage(IPC_CHANNEL, "locked", ipc.getSender(), m_botAction.getBotName());
    		} catch (IllegalStateException e) {
    		    Tools.printStackTrace("ISE PubAutoBot IPC.", e);
    		}
    	} else if(message.equals("confirm_lock") && ipc.getRecipient().equals(m_botAction.getBotName())) {
    	    // Step 3: Confirming the lock.
    	    // Step 4: Determine who's the owner (bot) of this bot ...
    		String[] owners = ipc.getSender().split(":");
    		owner = owners[0];
    		// ... as well as if there is a subowner (player).
    		if (owners.length==2) {
    			subowner = owners[1];
    		}
    	} else if(message.startsWith("command:") 
    	        && locked 
    	        && owner != null 
    	        && ipc.getRecipient().equals(m_botAction.getBotName())){
    		if (ipc.getSender().equals(owner) || (subowner != null && ipc.getSender().equals(subowner)))
    			handleCommand(ipc.getSender(), message.substring(8));
    	} else if(message.startsWith("locations:") 
    	        && owner != null 
    	        && owner.equals(ipc.getSender()) 
    	        && ipc.getRecipient().equals(m_botAction.getBotName())) {
    		locations = new HashSet<String>();
    		String[] data = message.substring(10).split(",");
    		locations.addAll(Arrays.asList(data));
    	}
    }

    public void requestEvents() {
        EventRequester req = m_botAction.getEventRequester();
        req.request(EventRequester.MESSAGE);
        req.request(EventRequester.WEAPON_FIRED);
        req.request(EventRequester.LOGGED_ON);
        req.request(EventRequester.PLAYER_POSITION);
        req.request(EventRequester.PLAYER_DEATH);
        req.request(EventRequester.PLAYER_LEFT);
        req.request(EventRequester.FREQUENCY_SHIP_CHANGE);
    }

    public void handleEvent(PlayerLeft event){
    	if(event.getPlayerID() == turretPlayerID)
    	    unAttach();
    }

    public void handleEvent(FrequencyShipChange event){
    	if(event.getPlayerID() == turretPlayerID && event.getShipType() == 0)
    	    unAttach();
    }

    public void handleEvent(PlayerDeath event) {
    	String killer = m_botAction.getPlayerName(event.getKillerID());
    	String killee = m_botAction.getPlayerName(event.getKilleeID());

    	if (killee == null || killer == null)
    		return;

    	//TODO: Check to make sure the killer isn't a bot.
    	if(turretPlayerID == event.getKilleeID()){
    		unAttach();
    		attach(killer);
    	}
    	if (killee.equals(m_botAction.getBotName())) {
    		numberOfShots = 0;
    	}
    	if (quitOnDeath && killee.equals(m_botAction.getBotName())) {
    		free();
    	}

    }

    /**
     * Adds a fired weapon to the big list of bullets and bombs for tracking.
     */
    public void handleEvent(WeaponFired event) {
        // If the bot isn't set to be killable, leave.
    	if(!killable) return;
    	// If the bot is in spec, leave.
    	if(m_botAction.getShip().getShip() == Ship.INTERNAL_SPECTATOR) return;
    	// If there is someone attached, we track deaths differently, leave.
    	if(turretPlayerID != -1) return;
    	// Get the player that fired the weapon.
    	Player p = m_botAction.getPlayer(event.getPlayerID());
    	
    	// Leave if the player cannot be found, the projectile is friendly or if the weapon's effect is too hard to track.
        if(p == null 
                || (p.getFrequency() == freq)
                || event.getWeaponType() > WeaponFired.WEAPON_MULTIFIRE 
                || event.isType(WeaponFired.WEAPON_REPEL) 
                || event.isType(WeaponFired.WEAPON_DECOY) 
                || event.isType(WeaponFired.WEAPON_BURST))
            return;
        
        double pSpeed = p.getWeaponSpeed();
        
        Projectile pjt = new Projectile(p.getPlayerName(), event, pSpeed);
        
        if(pjt.getImpactTime(new Point(m_botAction.getShip().getX(), m_botAction.getShip().getY()), 16, 5000) < 0)
            return;
        
        fired.add(pjt);
	}

    /**
     * Updates the player tracking and/or the movement/location of the bot.
     */
    public void handleEvent(PlayerPosition event) {
        // If the bot is specced, leave.
    	if(m_botAction.getShip().getShip() == 8) return;

    	Player p = m_botAction.getPlayer(event.getPlayerID());
    	// Leave if the player cannot be found, or we aren't shooting, or if it's a friendly, and we aren't set to shoot them.
        if(p == null || !autoAiming || (enemyAimingOnly && p.getFrequency() == freq)) {
        	return;
        }

        if (!locations.isEmpty()) {
        	String xy = event.getXLocation()/16 + ":" + event.getYLocation()/16;
        	if (!locations.contains(xy)) {
        		return;
        	}
        }

        // The bot can only receive playerposition packets of those in range.
        // If it hasn't received a packet in a while from a freq that it's supposed to track, it goes into a sleep mode
        // until it does receive a position packet from an opponent.
        m_botAction.cancelTask(disableEnemyOnSightTask);
        disableEnemyOnSightTask = new DisableEnemyOnSightTask();
        m_botAction.scheduleTask(disableEnemyOnSightTask, 3*Tools.TimeInMillis.SECOND);

        // Reenable tracking the enemy, because it has seen one.
        enemyOnSight = true;

        // Get the relative distance to the player and calculate its angle towards the bot in degrees.
        // Revert the angle, to know whereto the bot needs to aim.
        double diffY, diffX, angle;
        diffX = (event.getXLocation() + (event.getXVelocity() * REACTION_TIME)) - m_botAction.getShip().getX();
    	diffY = (event.getYLocation() + (event.getYVelocity() * REACTION_TIME)) - m_botAction.getShip().getY();
    	angle = (180 - (Math.atan2(diffX, diffY)*180/Math.PI)) * SS_CONSTANT;

    	if (!following) {
    	    // If the bot isn't tracking any player, start tracking this one.
    		doFaceCmd(m_botAction.getBotName(), Double.toString(angle));
    	} else {
    	    // If the target isn't the current target we're tracking, ignore it.
    		if (target != null && !target.equals(p.getPlayerName()))
    			return;

    		int pX = (int)((event.getXLocation() + (event.getXVelocity() * REACTION_TIME))/16);
    		int pY = (int)((event.getYLocation() + (event.getYVelocity() * REACTION_TIME))/16);

    		int bX = (int)(m_botAction.getShip().getX()/16);
    		int bY = (int)(m_botAction.getShip().getY()/16);

    		// Distance between the player and the bot
    		int d = (int)(Math.sqrt((pX-bX)*(pX-bX) + (pY-bY)*(pY-bY)));

    		// If we didn't have a target (which we shouldn't due to earlier checks),
    		// and the distance to the player is close enough, then set him as the new target.
    		// Otherwise, another one to ignore.
    		if (target == null && d < 10) {
    			target = p.getPlayerName();
    		} else if (target == null) {
    			return;
    		}

    		// Adjust the percentage for X/Y velocity
    		double pctX = diffX/(Math.abs(diffX)+Math.abs(diffY));
    		double pctY = diffY/(Math.abs(diffX)+Math.abs(diffY));

    		// Accelerate the velocity if the player is far
    		// Works only with the TW settings
    		double pctD = Math.min((int)Math.pow(1.075, d), 8);

    		// Compute the real velocity to apply
    		int vX = (int)(1000*pctX*pctD);
    		int vY = (int)(1000*pctY*pctD);

    		//System.out.println("Px:"+pX + "   Py:"+pY + "   Bx:"+bX + "   By:"+bY + "   BBx:"+botX + "   BBy:"+botY + "   Vx:"+vX + "   Vy:"+vY + "   A:"+angle);
    		
    		// Adjust the bots velocity and heading.
    		m_botAction.getShip().setVelocitiesAndDir(vX, vY, (int)angle);

    	}
    }

    /**
     * Tracks the projectiles fired at the bot and updates the bot's location if attached.
     * This is run through a scheduled task that is fired when the bot is in a ship.
     */
    public void update() {
        // Update current coords of the bot.
        botX = m_botAction.getShip().getX();
        botY = m_botAction.getShip().getY();

        // If we aren't attached to a player, check if a bullet will hit us.
        if(turretPlayerID == -1) {
            ListIterator<Projectile> it = fired.listIterator();
            while (it.hasNext()) {
                Projectile b = (Projectile) it.next();
                // If the projectile has an owner and it's hitting the bot, then let's do our magic.
                if (b.getOwner() != null && b.isHitting(botX, botY)) {
                    Player p = m_botAction.getPlayer(b.getOwner());

                    // Check if the bullet is hostile and the bot isn't respawning.
                    if (p != null && p.getFrequency() != freq && !isSpawning) {
                        // Increase the "shot that hit us"-counter.
                        numberOfShots++;
                        // Initiate a new task in case we are going to respawn.
                        spawned = new TimerTask() {
                            public void run() {
                                isSpawning = false;
                            }
                        };
                        // Check if we are supposed to die.
                        if (numberOfShots >= dieAtXshots) {
                            // Award a kill to the owner of the projectile.
                            m_botAction.sendDeath(m_botAction.getPlayerID(b.getOwner()), 0);
                            
                            // Pause firing at the players.
                            Iterator<RepeatFireTimer> i = repeatFireTimers.iterator();
                            while(i.hasNext())i.next().pause();
                            
                            if(quitOnDeath) {
                                m_botAction.sendTeamMessage("That last shot was fatal to me. Good luck and farewell.");
                                free();
                                return;
                            }

                            // Set us to respawning
                            isSpawning = true;
                            m_botAction.scheduleTask(spawned, SPAWN_TIME);
                        }
                    }
                    // Since it hit us, remove the projectile.
                    it.remove();
                } else if (b.getAge() > 5000 || b.getOwner() == null) {
                    // If the bullet becomes too old, or if there is no known owner (shouldn't happen), discard it.
                    it.remove();
                }
            }
        } else {
            // If we are attached, adjust our movement to match the player we're attached to.
            Player p = m_botAction.getPlayer(turretPlayerID);
            if (p == null) return;
            int xVel = p.getXVelocity();
            int yVel = p.getYVelocity();
            m_botAction.getShip().move(botX, botY, xVel, yVel);
        }
    }

    /**
     * Handles incoming messages.
     */
    public void handleEvent(Message event) {
    	// Get the message's specifics.
    	int messageType = event.getMessageType();
    	String message = event.getMessage();
    	String playerName = event.getMessager();
    	if (playerName == null) {
    		playerName = m_botAction.getPlayerName(event.getPlayerID());
    	}
    	
    	// Let's ignore bots, since all bot related stuff goes through IPC.
        if(m_botAction.getOperatorList().isBotExact(playerName))
            return;

    	// Only react to private messages
    	if(messageType == Message.PRIVATE_MESSAGE || messageType == Message.REMOTE_PRIVATE_MESSAGE) {
    	    // Filter for !die and !status
    		if(m_botAction.getOperatorList().isSmod(playerName)) {
    			if (message.equalsIgnoreCase("!die")) {
    			    disconnect();
    			    return;
    			} else if(message.equalsIgnoreCase("!status")) {
    			    m_botAction.sendSmartPrivateMessage(playerName, "My current status: "
    			            + "I am " + (locked?"":"un") + "locked; "
    			            + (owner!=null?"My current owner is: " + owner:"I have no owner") + "; "
    			            + (subowner!=null?"My current subowner is: " + subowner:"I have no subowner" + "."));
    			    return;
    			} else if(message.equalsIgnoreCase("!gtfo")) {
    			    m_botAction.sendSmartPrivateMessage(playerName, "Please, don't send me to my room... It's fun in here!");
    			    free();
    			    return;
    			} else if(message.equalsIgnoreCase("!debug")) {
    			    m_debug = !m_debug;
    			    m_debugger = playerName;
    			    m_botAction.sendSmartPrivateMessage(playerName, "Debugging is now " + (m_debug?"en":"dis") + "abled.");
    			}
    		}
    		
    		// Only allow direct command communication to this bot when debug mode is enabled.
            // When in production, the only command interaction with this bot should be by using the InterProcess channel.
            // The !die command is still accessible in case of emergencies, though.
    		if (locked && DEBUG_ENABLED
    		        && owner != null 
    		        && (owner.equals(playerName) || (subowner !=null && subowner.equals(playerName)))
    		        && handleCommand(playerName, message)) {
    			// If the bot is locked and one of the owners message it, throw the message through handleCommand().
    		    // This function will return true if it was an actual command that could be executed.
    		    // Read: If the owners send something else than a real command, this if-statement will be false.
    		} else {
    		    // When someone else tries to communicate with this bot or the owners didn't use a real command.
    			if (owner != null) {
    			    if(DEBUG_ENABLED) {
    			        // This feels like debugging information. Better hide it from the public to be honest.
        				String controllerBy = owner;
        				if (subowner != null) controllerBy += " and " + subowner;
        				m_botAction.sendSmartPrivateMessage(playerName, "Hi " + playerName + ", I am controlled by " + controllerBy + ". ");
    			    }

    			    //TODO: Clean this up into a better system.
    				String aboutMe  = "About me:";
    				String aboutMe2 = "";
    				String aboutMe3 = "";
    				if (freq < 100)
    				    aboutMe +=  " I've been hired to work for Freq " + freq +".";
    				if (killable) {
    				    // Display the amounts of deaths left, if applicable.
    					aboutMe2 += "I can be killed";
    					if ((dieAtXshots-numberOfShots) > 1) {
    						aboutMe2 += ", but I can still survive " + (dieAtXshots-numberOfShots) + " more hits.";
    					} else {
    						aboutMe2 += ". Hit me one more time and I die.";
    					}
    				} else {
    					aboutMe2 += "I cannot be killed. ";
    				}
    				
					// Display the amount of time left, if applicable.
					if (timeoutAt != -1) {
						aboutMe3 += "My job will be done in " + Tools.getTimeDiffString(startedAt + timeoutAt*1000, false) + ".";
					}
    				
					// Send the created messages.
    				m_botAction.sendSmartPrivateMessage(playerName, aboutMe);
    				m_botAction.sendSmartPrivateMessage(playerName, aboutMe2);
    				if (!aboutMe3.equals("")) {
    					m_botAction.sendSmartPrivateMessage(playerName, aboutMe3);
    				}
    			}
    		}
    	}
    }

    /**
     * Handles commands sent by the owner of this bot or any other bot.
     * Note: When debug is disabled, players do not have access to this.
     * 
     * @param name Name of the sender
     * @param msg Message
     * @return Boolean true if a matching command was found, otherwise false.
     */
    public boolean handleCommand(String name, String msg) {

    	msg = msg.toLowerCase().trim();

    	if(msg.startsWith("!setship "))
			doSetShipCmd(name,msg.substring(9));
    	else if(msg.startsWith("!setfreq "))
    		doSetFreqCmd(name,msg.substring(9));
    	else if(msg.startsWith("!setshipfreq "))
    	    doSetShipFreqCmd(name, msg.substring(13));
		else if(msg.startsWith("!warpto "))
			doWarpToCmd(name,msg.substring(8));
		else if(msg.startsWith("!face "))
			doFaceCmd(name,msg.substring(6));
		else if(msg.startsWith("!fire "))
			doFireCmd(msg.substring(6));
		else if(msg.startsWith("!repeatfireonsight "))
			doRepeatFireCmd(name, msg.substring(19), true);
		else if(msg.startsWith("!repeatfire "))
			doRepeatFireCmd(name, msg.substring(12), false);
		else if(msg.startsWith("!rfire "))
			doRepeatFireCmd(name, msg.substring(7), false);
		else if(msg.startsWith("!rfonsight "))
			doRepeatFireCmd(name, msg.substring(11), true);
		else if(msg.equals("!listfire"))
			doFireListCmd(name);
		else if(msg.startsWith("!stopfire "))
			doStopRepeatFireCmd(msg.substring(10));
		else if(msg.equals("!stopfire"))
			doStopRepeatFireCmd(null);

		else if(msg.equals("!killable"))
			setKillable(true);
		else if(msg.equals("!notkillable"))
			setKillable(false);
		else if(msg.startsWith("!dieatxshots "))
			doDieAtXShotsCmd(msg.substring(13));

		else if(msg.equals("!quitondeath"))
			setQuitOnDeath(true);

		else if(msg.startsWith("!brick "))
			doDropBrickCmd(msg.substring(7));
		else if(msg.equals("!brick"))
			dropBrick();

		else if(msg.startsWith("!follow"))
			doFollowOnSightCmd(msg.substring(7));

		else if(msg.equals("!aim"))
			setAiming(true);
		else if(msg.equals("!noaim"))
			setAiming(false);

		else if(msg.equals("!fastrotation"))
			setFastRotation(true);
		else if(msg.equals("!softrotation"))
			setFastRotation(false);


		else if(msg.equals("!aimingatenemy"))
			setEnemyAiming(true);
		else if(msg.equals("!aimingateveryone"))
			setEnemyAiming(false);

		else if(msg.startsWith("!attach "))
			attach(msg.substring(8));
		else if(msg.equals("!unattach"))
			unAttach();

		else if(msg.startsWith("!setaccuracy "))
			doSetAccuracyCmd(msg.substring(13));
		else if(msg.startsWith("!timeout "))
			doTimeoutDieCmd(msg.substring(9));
		else if(msg.startsWith("!move "))
			doMoveCmd(name,msg.substring(6));
		else if(msg.equals("!free"))
			free();
		else if(msg.startsWith("!go "))
			go(msg.substring(4).trim());
		else if(msg.equals("!die"))
			disconnect();
		else if(msg.equals("!testshots"))
			testShots();
		else if (msg.equals("!spec"))
			spec();
		else if (msg.equals("!baseterr"))
		    baseTerr();

    	/*
		else if (msg.equals("!help"))
			m_botAction.smartPrivateMessageSpam( name, helpmsg );
		*/
		else {
			return false;
		}

    	return true;
    }
    
    /**
     * Message triggered by !baseterr. (Auto-issued on start.)
     */
    public void baseTerr() {
        m_botAction.sendTeamMessage("ATTACH! " + m_botAction.getBotName() 
                + " BASETERR stationed in FLAGROOM but I can only take " 
                + (dieAtXshots-numberOfShots) + " more hits!", Tools.Sound.CANT_LOG_IN);
    }

    /**
     * Attaches to a player.
     * 
     * @param playerName The player to attach to.
     */
    public void attach(String playerName) {
    	String name = m_botAction.getFuzzyPlayerName(playerName);
    	if (name==null)
    		return;

    	Player p = m_botAction.getPlayer(name);
    	if (p==null)
    		return;

    	m_botAction.getShip().setFreq(p.getFrequency());
    	freq = p.getFrequency();

    	turretPlayerID = m_botAction.getPlayerID(name);
    	m_botAction.getShip().attach(turretPlayerID);
    }

    /**
     * Detach from a player
     */
    public void unAttach() {
    	if(turretPlayerID == -1)
    	    return;
    	m_botAction.getShip().unattach();
    	turretPlayerID = -1;
    }

    /**
     * Checks if the bot is attached to a player.
     * <p>
     * NOTE: Not accurate.<br>
     * Case: If the player forcefully detaches the bot.
     * 
     *  @return True if the bot is attached to a player. Otherwise false.
     */
    public boolean isAttached() {
    	if (turretPlayerID != -1)
    		return true;
    	return false;
    }

    private void setAiming(boolean aiming) {
    	autoAiming = aiming;
    }

    public void doFollowOnSightCmd(String name) {
    	following = !following;
    	if (following) {
    		autoAiming = true;
    		if (!name.isEmpty())
    			target = name.trim();
    	}
    }

    private void setQuitOnDeath(boolean quit) {
        this.quitOnDeath = quit;
    }

    private void setKillable(boolean killable) {
        this.killable = killable;
    }

    private void setEnemyAiming(boolean aiming) {
        this.enemyAimingOnly = aiming;
    }

    private void setFastRotation(boolean rotation) {
        this.fastRotation = rotation;
    }

    private void doDieAtXShotsCmd(String parameter) {
    	dieAtXshots = Integer.valueOf(parameter);
    }

    /**
     * Sets the amount of time after which the bot leaves.<p>
     * This function will also start a timer that fires when the time is up.
     * 
     * @param parameter Timeout value in seconds.
     */
    private void doTimeoutDieCmd(String parameter) {
    	int seconds = Integer.valueOf(parameter);
    	timeoutAt = seconds;
    	m_botAction.cancelTask(freeMe);
    	freeMe = new FreeTask();
        m_botAction.scheduleTask(freeMe, seconds * Tools.TimeInMillis.SECOND);
    }

    /**
     * Frees up all the used timers, variables and lists and such.
     * When possible, the values are reset to their initial values.
     */
    private void free() {
        // First clean up all the timers to avoid racing conditions...
        m_botAction.cancelTasks();
        
        // Unlock the bot and remove the owners.
    	locked = false;
    	owner = null;
    	subowner = null;
    	
    	// Move the bot back into the stable.
    	m_botAction.getShip().setShip(8);
    	m_botAction.changeArena(m_botAction.getBotSettings().getString("Arena"));
    	debug("Free: Switching to spec, changing arena.");

    	// Reset all the variables
    	autoAiming = false;
    	enemyAimingOnly = true;
    	fireOnSight = false;
    	following = false;
    	killable = false;
    	quitOnDeath = false;
    	fastRotation = true;
    	timeoutAt = -1;
    	//energyOnStart = 1; unused
    	
    	dieAtXshots = 1;
    	startedAt = 0;
    	numberOfShots = 0;
    	isSpawning = false;
    	enemyOnSight = false;
    	target = null;
    	
    	// Clear all the lists
    	locations.clear();
    	doStopRepeatFireCmd(null);
    	fired.clear();
    }

    /**
     * Starts a timer for the disconnect sequence. (500ms)
     */
    private void disconnect() {
    	m_botAction.scheduleTask(new DieTask(), 500);
    }

    private void testShots() {
    	for(int i=1; i<98; i++) {
    		fire(i);
    		try {
				Thread.sleep(700);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
    }

    public void go(String arena) {
        debug("Go: Changing arena to " + arena);
        m_botAction.changeArena(arena);
    }

    /**
     * Sets the ship for the bot to use and starts a new update timer.
     * (This function must be called through !setship to ensure the bot will work correctly.)
     * 
     * @param name Name of the person who issued the command
     * @param message The original message.
     */
    public void doSetShipCmd(String name, String message) {
    	try {
    		int ship = Integer.parseInt(message.trim());
    		if(ship <= 9 && ship >= 1) {
    			m_botAction.getShip().setShip(ship-1);
    			botX = m_botAction.getShip().getX();
    			botY = m_botAction.getShip().getY();
    			m_botAction.getShip().setFreq(0);
    			debug("SetShip command: switching to ship " + ship + " and freq 0.");
    			startedAt = System.currentTimeMillis();
    			fired.clear();
    			if(updateIt != null)
    			    updateIt.cancel();
    			
    			updateIt = new TimerTask() {
    		        public void run() {
    		            update();
    		        }
    		    };
    			m_botAction.scheduleTaskAtFixedRate(updateIt, 100, 100);
    		}
    	} catch(Exception e) {}
    }

    /**
     * Changes the freq of the bot and stores this internally.
     * <p>
     * Please note that using this when activating the bot is important, since the internally set freq
     * is used in various checks through this bot's code.
     * 
     * @param name The bot (or in debugmode the (sub)owner) who issued the command.
     * @param message The original command.
     */
    public void doSetFreqCmd(String name, String message) {
    	try {
    		int freq = Integer.parseInt(message.trim());
    		m_botAction.getShip().setFreq(freq);
    		debug("SetFreq command: Switching to freq: " + freq);
    		this.freq = freq;
    	} catch(Exception e) {}
    }

    /**
     * Combined command that changes both the ship and freq of the bot.
     * <p>
     * Additionally, this will store the frequency internally, as well as start an update timer.
     * @param name Bot who issued the command.
     * @param message The original command.
     */
    public void doSetShipFreqCmd(String name, String message) {
        if(message.isEmpty())
            return;
        
        String[] splitArgs = message.trim().split(":");
        try {
            int ship = Integer.parseInt(splitArgs[0]);
            int freq = Integer.parseInt(splitArgs[1]);
            
            if(ship <= 9 && ship >= 1) {
                m_botAction.getShip().setShip(ship-1);
                botX = m_botAction.getShip().getX();
                botY = m_botAction.getShip().getY();
                m_botAction.getShip().setFreq(freq);
                startedAt = System.currentTimeMillis();
                m_botAction.getShip().moveAndFire(botX, botY, 1);
                fired.clear();
                this.freq = freq;
                debug("SetShipFreq command: Switching to ship " + ship + " and freq " + freq);
                if(updateIt != null)
                    updateIt.cancel();
                
                updateIt = new TimerTask() {
                    public void run() {
                        update();
                    }
                };
                m_botAction.scheduleTaskAtFixedRate(updateIt, 100, 100);
            }
        } catch (Exception e) {}
    }
    /**
     * Puts the bot into spectator mode.
     * <p>
     * This method will also cancel the {@link #updateIt}-timer if it's running.
     */
    public void spec() {
        // If the bot is already specced, do nothing.
    	if(m_botAction.getShip().getShip() == 8)
    	    return;
    	// Cancel the current update timer.
    	try {
    	    m_botAction.cancelTask(updateIt);
	    } catch(Exception e) {}
    	// Spec the bot.
    	m_botAction.getShip().setShip(8);
    	debug("Spec: Speccing myself.");
    }

    /**
     * Warp the bot.
     * 
     * @param name The bot, or in debugmode, person, who issued the command.
     * @param message The original command.
     */
    public void doWarpToCmd(String name, String message) {
        // If the bot is specced, ignore this command.
    	if(m_botAction.getShip().getShip() == 8)
    	    return;
    	String[] msg = message.split(" ");
    	try {
    		int x = Integer.parseInt(msg[0]);
    		int y = Integer.parseInt(msg[1]);
    		warpTo(x,y);
    	} catch(Exception e) {}
    }

    /* 0-1024 */
    public void warpTo(int x, int y) {
		m_botAction.getShip().move(x * 16 + 8, y * 16 + 8);
		botX = x * 16 + 8;
		botY = y * 16 + 8;
    }

	public void doFaceCmd(String name, String message) {
		if(m_botAction.getShip().getShip() == 8)
		    return;
		try {
			float degree = Float.parseFloat(message);
			int angle = Math.round(degree);
			face(angle);
		} catch(Exception e) {}
	}

	/* 0-39 */
	public void face(int angle) {

		if (rotationTask == null && !fastRotation && !following) {
			rotationTask = new RotationTask();
			rotationTask.start();
		} else if (fastRotation || following) {
			if (rotationTask != null) {
				rotationTask.stopRunning();
				rotationTask = null;
			}
			m_botAction.getShip().setRotation(angle);
		}

		angleTo = angle;
	}

	public void doDropBrickCmd(String message) {
		if(m_botAction.getShip().getShip() == 8)
		    return;
		try {
			String[] msg = message.split(" ");
			int x = Integer.parseInt(msg[0]);
			int y = Integer.parseInt(msg[1]);
			dropBrickAtPosition(x,y);
		} catch(Exception e) {}
	}

	public void dropBrick(){
		m_botAction.getShip().dropBrick();
	}

	/* 0-1024 */
	public void dropBrickAtPosition(int x, int y) {
		m_botAction.getShip().dropBrick(x * 16 + 8, y * 16 + 8);
	}

	public void doFireCmd(String msg) {
		if(m_botAction.getShip().getShip() == 8)
		    return;
		try {
			int wep = Integer.parseInt(msg);
			fire(wep);
		} catch(Exception e) {}
	}

	public void fire(int weapon) {
		m_botAction.getShip().fire(weapon);
	}

	public void doRepeatFireCmd(String name, String message, boolean fireOnSight) {
		try {
			this.fireOnSight = fireOnSight;
			StringTokenizer msg = getArgTokens(message);
			if(msg.countTokens() != 2) {
			    m_botAction.sendSmartPrivateMessage( name, "Format: !repeatfire <Weapon>:<Repeat in Miliseconds>");
			    return;
		    }
			int weapon = Integer.parseInt(msg.nextToken());
			int timerStart = 0;
			int repeatTime = Integer.parseInt(msg.nextToken());
			if(repeatTime >= 200) {
				if(repeatFireTimers.size() > 2) {
					m_botAction.sendSmartPrivateMessage( name, "Please, no more than three firing tasks.");
				} else {
				    new RepeatFireTimer(weapon, timerStart, repeatTime);
				}
			} else {
				m_botAction.sendSmartPrivateMessage( name, "Sending a weapon packet every " + repeatTime + "ms can cause the 0x07 bi-directional packet.");
			}
		} catch(Exception e) {}
	}

	public void doFireListCmd(String name) {
		if(repeatFireTimers.size() == 0) {
			m_botAction.sendSmartPrivateMessage( name, "There are currently no firing tasks.");
			return;
		}
		Iterator<RepeatFireTimer> i = repeatFireTimers.iterator();
		while(i.hasNext()) {
			m_botAction.sendSmartPrivateMessage( name, i.next().toString());
		}
	}

	public void doStopRepeatFireCmd(String message) {
		if(repeatFireTimers.size() == 0) {
			return;
		}
		if(message == null) {
			Iterator<RepeatFireTimer> i = repeatFireTimers.iterator();
			while(i.hasNext()) {
				RepeatFireTimer r = i.next();
				r.cancel();
			}
			repeatFireTimers.clear();
		} else {
			try {
				int index = Integer.parseInt(message) - 1;
				repeatFireTimers.elementAt(index).cancel();
				repeatFireTimers.removeElementAt(index);
			} catch(Exception e) {}
		}
	}

	public void doSetAccuracyCmd(String message) {
		try {
			int x = Integer.parseInt(message);
			if(x < 0 || x > 5)
			    return;
			REACTION_TIME = .075;
			for(int i = 0; i < x; i++) {
				REACTION_TIME -= .02;
			}
		} catch(Exception e) {}
	}

	public void doMoveCmd(String name, String message) {
		if(m_botAction.getShip().getShip() == 8)
		    return;
		StringTokenizer argTokens = getArgTokens(message);
	    int numTokens = argTokens.countTokens();
	    if(numTokens != 4 && numTokens != 2)
	    	m_botAction.sendSmartPrivateMessage( name, "Format: !move X:Y:XVelocity:YVelocity");
	    else if(numTokens == 2)
	    	m_botAction.sendSmartPrivateMessage( name, "For a stationary warp use !warpto <X>:<Y>");
	    else if(numTokens == 4) {
	    	try {
	    		int x = Integer.parseInt(argTokens.nextToken()) * 16;
	    		int y = Integer.parseInt(argTokens.nextToken()) * 16;
	    		int xVel = Integer.parseInt(argTokens.nextToken());
	    		int yVel = Integer.parseInt(argTokens.nextToken());
	    		m_botAction.getShip().move(x,y,xVel,yVel);
	    	} catch(Exception e) {}
	    }
	}

	public StringTokenizer getArgTokens(String string) {
	    if(string.indexOf((int) ':') != -1)
	        return new StringTokenizer(string, ":");
	    
	    return new StringTokenizer(string);
    }

	private class DieTask extends TimerTask {
        public void run() {
            m_botAction.die();
        }
    }

	private class UnlockTask extends TimerTask {
        public void run() {
        	if (locked && owner == null) {
        		locked = false;
        	}
        }
    }

	private class FreeTask extends TimerTask {
        public void run() {
            m_botAction.sendTeamMessage("My time here is done. Good luck and goodbye!");
        	free();
        }
    }

	private class DisableEnemyOnSightTask extends TimerTask {
        public void run() {
        	enemyOnSight = false;
        }
    }

	private class RotationTask extends Thread {

		private boolean running = true;

		public void stopRunning() {
			running = false;
		}

        public void run() {

        	boolean invert = false;

        	while(running) {

        		if (angle == angleTo) {
        			m_botAction.getShip().setRotation(angleTo);

        		} else {
        			if (angle-angleTo < -20 || (angle-angleTo > 0 && angle-angleTo < 20)) {
        				invert = true;
        			} else {
        				invert = false;
        			}

	        		if (Math.abs(angle-angleTo) <= 2) {
	        			m_botAction.getShip().setRotation(angleTo);
	        		} else {
	        			angle += 3 * (invert?-1:1);
	        			if (angle > 39) {
	        				angle = angle-40;
	        			} else if (angle < 0) {
	        				angle = 40+angle;
	        			}

	            		m_botAction.getShip().setRotation(angle);
	        		}
        		}
        		try { 
        		    Thread.sleep(125); 
    		    } catch (InterruptedException e) { }
        	}
        }
    }


    private class RepeatFireTimer {
    	private int SPAWN_TIME = 5005;
    	public int weapon, delayms, repeatms;
    	public boolean isRunning = true;
    	//public boolean isSlowlyStopping = false; unused
    	TimerTask repeat = null;
    	//TimerTask slowly; unused
    
    	public RepeatFireTimer(int wep, int delayms, int repeatms) {
    		this.weapon = wep;
    		this.delayms = delayms;
    		this.repeatms = repeatms;
    		repeatFireTimers.add(this);
    		repeat = new TimerTask() {
    			public void run() {
    				if (!fireOnSight || (fireOnSight && enemyOnSight))
    					doFireCmd(Integer.toString(weapon));
    			}
    		};
    		m_botAction.scheduleTaskAtFixedRate(this.repeat, this.delayms, this.repeatms);
    	}
    	
    	public void cancel() {
    		if(repeat != null)
    			repeat.cancel();
    		isRunning = false;
    	}
    	
    	public void pause() {
    		if(!isRunning)
    		    return;
    		repeat.cancel();
    		repeat = new TimerTask() {
    			public void run() {
    				doFireCmd(Integer.toString(weapon));
    			}
    		};
    		m_botAction.scheduleTaskAtFixedRate(this.repeat, this.SPAWN_TIME, this.repeatms);
    	}
    
    	@SuppressWarnings("unused")
        public void stop() {
    		if(isRunning) {
    			repeat.cancel();
    			isRunning = false;
    		}
    	}
    
    	public String toString() {
    		String s = (repeatFireTimers.indexOf(this)+1) + ") Firing weapon(" + weapon + ") every " + repeatms + " ms." ;
    		return s;
    	}
    }
    
    private void debug(String msg) {
        if(m_debug) {
            m_botAction.sendSmartPrivateMessage(m_debugger, msg);
        }
    }

}