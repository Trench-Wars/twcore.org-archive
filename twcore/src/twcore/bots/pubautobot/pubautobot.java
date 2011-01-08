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
import twcore.core.events.ArenaJoined;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.WeaponFired;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

/**
 * The original PracBot.
 * 
 * @author milosh - 1.15.08
 */
public class pubautobot extends SubspaceBot {

	private static boolean DEBUG_ENABLED = true;
	
	// Game constant
	private static final double SS_CONSTANT = 0.1111111;
	private static final int SPAWN_TIME = 5005;
	private static double REACTION_TIME = 0.075;
	
	// Inter-bot communication
	private static final String IPC_CHANNEL = "pubautobot";

	// Ownership
	private boolean locked = false;
	private String owner; // usually a bot
	private String subowner; // usually a player
	
	// Settings
	private boolean autoAiming = false;
	private boolean enemyAimingOnly = true;
	private boolean fireOnSight = false;
	private boolean following = false;
	private boolean killable = false;
	private boolean quitOnDeath = false;
	private boolean fastRotation = true;
	private int timeoutAt = -1; // after timeout, bot disconnects
	private int energyOnStart = 1; // one shot and dead
	private HashSet<String> locations; // array of x:y position
	private int dieAtXshots = 1; // if 1, first hit the bot dies
	private long startedAt = 0;
	
	// In-game settings
	private int freq;
	private int botX;
	private int botY;
	private int angle = 0;
	private int angleTo = 0;
	private int numberOfShots = 0;
	
    private int turretPlayerID = -1;
    private LinkedList<Projectile> fired = new LinkedList<Projectile>();
	private Vector<RepeatFireTimer> repeatFireTimers = new Vector<RepeatFireTimer>();
    private RotationTask rotationTask;
    private DisableEnemyOnSightTask disableEnemyOnSightTask;
	
	boolean isSpawning = false;

	private boolean enemyOnSight = false;
	private String target;

	// THIS LIST IS NOT UP-TO-DATE !
	
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
	
	TimerTask spawned;
	TimerTask updateIt = new TimerTask(){
		public void run(){
			update();
		}
	};
    
    public pubautobot(BotAction botAction) {
        super(botAction);
        requestEvents();
        loadConfig();
    }
    
    public void loadConfig() {
    	
    	locations = new HashSet<String>();
    	disableEnemyOnSightTask = new DisableEnemyOnSightTask();
    	
    }
    
    public void handleEvent(LoggedOn event) {   
    	try {
			m_botAction.joinArena(m_botAction.getBotSettings().getString("Arena"),(short)3392,(short)3392);
		} catch (Exception e) {
			m_botAction.joinArena(m_botAction.getBotSettings().getString("Arena"));
		}
    	m_botAction.ipcSubscribe(IPC_CHANNEL);
    	m_botAction.ipcSendMessage(IPC_CHANNEL, "loggedon", null, m_botAction.getBotName());
		m_botAction.setPlayerPositionUpdating(200);
    }

    /* Step to request this bot
     * 
     * 1. RandomBot> Send a "looking"
     * 2. Autobot> If not locked, send a "locked"
     *    If not confirmation after 5 seconds, the bot is free'd
     * 3. RandomBot> Confirm with "confirm_lock"
     * 4. Setup the new owner of this bot
     */
    public void handleEvent(InterProcessEvent event)
    {
    	IPCMessage ipc = (IPCMessage)event.getObject();
    	String message = ipc.getMessage();
    	
    	if(message.equals("looking") && !locked) {
    		locked = true;
    		m_botAction.scheduleTask(new UnlockTask(), 5*Tools.TimeInMillis.SECOND);
    		m_botAction.ipcSendMessage(IPC_CHANNEL, "locked", ipc.getSender(), m_botAction.getBotName());
    	}
    	else if(message.equals("confirm_lock") && ipc.getRecipient().equals(m_botAction.getBotName())) {
    		String[] owners = ipc.getSender().split(":");
    		owner = owners[0];
    		if (owners.length==2) {
    			subowner = owners[1];
    		}
    	}
    	else if(message.startsWith("command:") && locked && owner != null){
    		if (ipc.getSender().equals(owner) || ipc.getSender().equals(subowner))
    			handleCommand(ipc.getSender(), message.substring(8));
    	}
    	else if(message.startsWith("locations:") && owner != null && owner.equals(ipc.getSender())) {
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
    	if(event.getPlayerID() == turretPlayerID)unAttach();
    }
    
    public void handleEvent(FrequencyShipChange event){
    	if(event.getPlayerID() == turretPlayerID && event.getShipType() == 0)unAttach();
    }
    
    public void handleEvent(PlayerDeath event) {

    	String killer = m_botAction.getPlayerName(event.getKillerID());
    	String killee = m_botAction.getPlayerName(event.getKilleeID());
    	
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
    
    public void handleEvent(WeaponFired event) {
    	if(!killable) return;
    	if(m_botAction.getShip().getShip() == 8) return;
    	if(turretPlayerID != -1) return;
    	Player p = m_botAction.getPlayer(event.getPlayerID());
        if(p == null || (p.getFrequency() == m_botAction.getShip().getAge()) || event.getWeaponType() > 8 || event.isType(5) || event.isType(6) || event.isType(7))return;
        double pSpeed = p.getWeaponSpeed();
		double bearing = Math.PI * 2 * (double)event.getRotation() / 40.0;
		fired.add(new Projectile(p.getPlayerName(), event.getXLocation() + (short)(10.0 * Math.sin(bearing)), event.getYLocation() - (short)(10.0 * Math.cos(bearing)), event.getXVelocity() + (short)(pSpeed * Math.sin(bearing)), event.getYVelocity() - (short)(pSpeed * Math.cos(bearing)), event.getWeaponType(), event.getWeaponLevel()));
	}
    
    public void handleEvent(PlayerPosition event) {
    	
    	if(m_botAction.getShip().getShip() == 8) return;
    	
    	Player p = m_botAction.getPlayer(event.getPlayerID());
        if(p == null || !autoAiming || (enemyAimingOnly && p.getFrequency() == freq)) {
        	return;
        }

        if (!locations.isEmpty()) {
        	String xy = event.getXLocation()/16 + ":" + event.getYLocation()/16;
        	if (!locations.contains(xy)) {
        		return;
        	}
        }

        m_botAction.cancelTask(disableEnemyOnSightTask);
        disableEnemyOnSightTask = new DisableEnemyOnSightTask();
        m_botAction.scheduleTask(disableEnemyOnSightTask, 3*Tools.TimeInMillis.SECOND);
        
        enemyOnSight = true;
        
        double diffY, diffX, angle;
        diffX = (event.getXLocation() + (event.getXVelocity() * REACTION_TIME)) - m_botAction.getShip().getX();
    	diffY = (event.getYLocation() + (event.getYVelocity() * REACTION_TIME)) - m_botAction.getShip().getY();
    	angle = (180 - (Math.atan2(diffX, diffY)*180/Math.PI)) * SS_CONSTANT;

    	if (!following) {
    		doFaceCmd(m_botAction.getBotName(), Double.toString(angle));
    	}
    	else {
    		
    		if (target != null && !target.equals(p.getPlayerName()))
    			return;

    		int pX = (int)((event.getXLocation() + (event.getXVelocity() * REACTION_TIME))/16);
    		int pY = (int)((event.getYLocation() + (event.getYVelocity() * REACTION_TIME))/16);
    	
    		int bX = (int)(m_botAction.getShip().getX()/16);
    		int bY = (int)(m_botAction.getShip().getY()/16);
    		
    		// Distance between the player and the bot
    		int d = (int)(Math.sqrt((pX-bX)*(pX-bX) + (pY-bY)*(pY-bY)));

    		if (target == null && d < 10) {
    			target = p.getPlayerName();
    		} else if (target == null) {
    			return;
    		}
    		
    		// Adjust the percentage for X/Y velocity
    		// Quick and dirty.. feel free to optimize it in 1 line of code
    		double pctX = Math.abs(diffX)/(Math.abs(diffX)+Math.abs(diffY));
    		double pctY = Math.abs(diffY)/(Math.abs(diffX)+Math.abs(diffY));
    		if (diffX>0 && diffY>0) {
    			pctX *= 1;
    			pctY *= 1;
    		} else if (diffX>0 && diffY<0) {
    			pctX *= 1;
    			pctY *= -1;
    		} else if (diffX<0 && diffY<0) {
    			pctX *= -1;
    			pctY *= -1;
    		} else if (diffX<0 && diffY>0) {
    			pctX *= -1;
    			pctY *= 1;
    		}
    		
    		// Accelerate the velocity if the player is far
    		// Works only with the TW settings
    		double pctD = Math.min((int)Math.pow(1.075, d), 8);
 
    		// Compute the real velocity to apply
    		int vX = (int)(1000*pctX*pctD);
    		int vY = (int)(1000*pctY*pctD);

    		//System.out.println("Px:"+pX + "   Py:"+pY + "   Bx:"+bX + "   By:"+bY + "   BBx:"+botX + "   BBy:"+botY + "   Vx:"+vX + "   Vy:"+vY + "   A:"+angle);
    		
    		m_botAction.getShip().setVelocitiesAndDir(vX, vY, (int)angle);
    		
    	}
    }
    
    public void update(){
    	
    	botX = m_botAction.getShip().getX();
 		botY = m_botAction.getShip().getY();

     	if(turretPlayerID == -1){
     		ListIterator<Projectile> it = fired.listIterator();
     		while (it.hasNext()) {
     			Projectile b = (Projectile) it.next();   
     			if (b.isHitting(botX, botY)) {

     				if (m_botAction.getPlayer(b.getOwner()).getFrequency()==freq)
     					return;

     				if(!isSpawning){
         				numberOfShots++;
     					spawned = new TimerTask(){
     						public void run(){
     							isSpawning = false;
     						}
     					};
     					if (numberOfShots >= dieAtXshots) {
	     					isSpawning = true;
	     					m_botAction.scheduleTask(spawned, SPAWN_TIME);
	     					m_botAction.sendDeath(m_botAction.getPlayerID(b.getOwner()), 0);
	     					Iterator<RepeatFireTimer> i = repeatFireTimers.iterator();
	     					while(i.hasNext())i.next().pause(); 
     					}
     				}
     				it.remove();
     			} 
     			else if (b.getAge() > 5000) {
     				it.remove();
     			}
     		}
		}
     	else{
     		Player p = m_botAction.getPlayer(turretPlayerID);
     		if(p == null)return;
			int xVel = p.getXVelocity();
			int yVel = p.getYVelocity();
			m_botAction.getShip().move(botX, botY, xVel, yVel);
     	}
    }
    
    public void handleEvent(Message event) 
    {
    	// If in production (not debug), you can only interact with this bot
    	// by using the InterProcess channel
    	if (!DEBUG_ENABLED)
    		return;
    	
    	int messageType = event.getMessageType();
    	String message = event.getMessage();
    	String playerName = event.getMessager();
    	if (playerName == null) {
    		playerName = m_botAction.getPlayerName(event.getPlayerID());
    	}

    	if(messageType == Message.PRIVATE_MESSAGE || messageType == Message.REMOTE_PRIVATE_MESSAGE)
    	{
    		if(m_botAction.getOperatorList().isSmod(playerName)){
    			if (m_botAction.getOperatorList().isSmod(playerName) && message.equalsIgnoreCase("!die"))
    	            disconnect();
    				
    		}
    		
    		if (locked && owner != null && (owner.equals(playerName) || subowner.equals(playerName)) && handleCommand(playerName, message)) {
    			// ok
    		}
    		else {
    			if (owner != null) {
    				String controllerBy = owner;
    				if (subowner != null) controllerBy += " and " + subowner;
    				m_botAction.sendSmartPrivateMessage(playerName, "Hi " + playerName + ", I am controlled by " + controllerBy + ". ");
    				
    				String aboutMe  = "About me: ";
    				String aboutMe2 = "";
    				if (killable) 
    				{
    					aboutMe += "I can be killed. ";
    					if (dieAtXshots > 1) {
    						aboutMe += "I die after " + dieAtXshots + " shots, " + (dieAtXshots-numberOfShots) + " left. ";
    					} else {
    						aboutMe += "Hit me 1 time and I die. ";
    					}
    					if (timeoutAt != -1) {
    						aboutMe2 += "I will disconnect in " + Tools.getTimeDiffString(startedAt+timeoutAt*1000, false) + ". ";
    					}
    				} 
    				else {
    					aboutMe += "I cannot be killed. ";
    					if (timeoutAt != -1) {
    						aboutMe += "I will disconnect in " + Tools.getTimeDiffString(startedAt+timeoutAt*1000, false) + ". ";
    					}
    				}
    				
    				m_botAction.sendSmartPrivateMessage(playerName, aboutMe);
    				if (!aboutMe2.equals("")) {
    					m_botAction.sendSmartPrivateMessage(playerName, aboutMe2);
    				}
    			}
    		}
    	}
    }
    
    public boolean handleCommand(String name, String msg){

    	msg = msg.toLowerCase().trim();
    	
    	if(msg.startsWith("!setship "))
			doSetShipCmd(name,msg.substring(9));
    	else if(msg.startsWith("!setfreq "))
    		doSetFreqCmd(name,msg.substring(9));
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
    	
    	/*
		else if (msg.equals("!help"))
			m_botAction.smartPrivateMessageSpam( name, helpmsg );
		*/
		else {
			return false;
		}
    	
    	return true;

    }
    
    public void attach(String playerName) 
    {
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
    
    public void unAttach() {
    	if(turretPlayerID == -1)return;
    	m_botAction.getShip().unattach();
    	turretPlayerID = -1;
    }
    
    /* Not accurate
     * Case: If the player dettach the bot */
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
    
    private void doTimeoutDieCmd(String parameter) {
    	int seconds = Integer.valueOf(parameter);
    	timeoutAt = seconds;
        m_botAction.scheduleTask(new FreeTask(), seconds * Tools.TimeInMillis.SECOND);
    }
    
    private void free() {
    	locked = false;
    	owner = null;
    	subowner = null;
    	m_botAction.getShip().setShip(8);
    	m_botAction.changeArena(m_botAction.getBotSettings().getString("Arena"));
    	
    	autoAiming = false;
    	enemyAimingOnly = true;
    	fireOnSight = false;
    	following = false;
    	killable = false;
    	quitOnDeath = false;
    	fastRotation = true;
    	timeoutAt = -1;
    	energyOnStart = 1;
    	locations.clear();
    	dieAtXshots = 1;
    	startedAt = 0;
    	isSpawning = false;
    	enemyOnSight = false;
    	target = null;
    }
    
    private void disconnect() {
    	m_botAction.scheduleTask(new DieTask(), 500);
    }
    
    private void testShots() {
    	for(int i=1; i<98; i++) {
    		fire(i);
    		try {
				Thread.sleep(700);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }

    public void go(String arena) {
        m_botAction.changeArena(arena);
    }

    public void doSetShipCmd(String name, String message){
    	try{
    		int ship = Integer.parseInt(message.trim());
    		if(ship <= 9 && ship >= 1){
    			m_botAction.getShip().setShip(ship-1);
    			botX = m_botAction.getShip().getX();
    			botY = m_botAction.getShip().getY();
    			m_botAction.getShip().setFreq(0);
    			m_botAction.scheduleTaskAtFixedRate(updateIt, 100, 100);
    			startedAt = System.currentTimeMillis();
    		}
    	}catch(Exception e){}    		
    }
    
    public void doSetFreqCmd(String name, String message){
    	try{
    		int freq = Integer.parseInt(message.trim());
    		m_botAction.getShip().setFreq(freq);
    		this.freq = freq;
    	}catch(Exception e){}
    }

    public void spec() {
    	if(m_botAction.getShip().getShip() == 8)return;
    	try{m_botAction.cancelTask(updateIt);}catch(Exception e){}
    	m_botAction.getShip().setShip(8);
    }
    
    public void doWarpToCmd(String name, String message){
    	if(m_botAction.getShip().getShip() == 8)return;
    	String[] msg = message.split(" ");
    	try {
    		int x = Integer.parseInt(msg[0]);
    		int y = Integer.parseInt(msg[1]);
    		warpTo(x,y);
    	}catch(Exception e){}
    }
    
    /* 0-1024 */
    public void warpTo(int x, int y) {
		m_botAction.getShip().move(x * 16 + 8, y * 16 + 8);
		botX = x * 16 + 8;
		botY = y * 16 + 8;
    }
    
	public void doFaceCmd(String name, String message){
		if(m_botAction.getShip().getShip() == 8)return;
		try{
			float degree = Float.parseFloat(message);
			int angle = Math.round(degree);		
			face(angle);
		}catch(Exception e){}
	}
	
	/* 0-39 */
	public void face(int angle) {
		
		if (rotationTask == null && !fastRotation && !following) {
			rotationTask = new RotationTask();
			rotationTask.start();
		}
		else if (fastRotation || following) {
			if (rotationTask != null) {
				rotationTask.stopRunning();
				rotationTask = null;
			}
			m_botAction.getShip().setRotation(angle);
		}
		
		angleTo = angle;
	}
	
	public void doDropBrickCmd(String message){
		if(m_botAction.getShip().getShip() == 8)return;
		try{
			String[] msg = message.split(" ");
			int x = Integer.parseInt(msg[0]);
			int y = Integer.parseInt(msg[1]);
			dropBrickAtPosition(x,y);
		}catch(Exception e){}
	}
	
	public void dropBrick(){
		m_botAction.getShip().dropBrick();
	}
	
	/* 0-1024 */
	public void dropBrickAtPosition(int x, int y){
		m_botAction.getShip().dropBrick(x * 16 + 8, y * 16 + 8);
	}
	
	public void doFireCmd(String msg){
		if(m_botAction.getShip().getShip() == 8)return;
		try{
			int wep = Integer.parseInt(msg);
			fire(wep);
		}catch(Exception e){}
	}
	
	public void fire(int weapon) {
		m_botAction.getShip().fire(weapon);
	}

	public void doRepeatFireCmd(String name, String message, boolean fireOnSight){
		try{
			this.fireOnSight = fireOnSight;
			StringTokenizer msg = getArgTokens(message);
			if(msg.countTokens() != 2){m_botAction.sendSmartPrivateMessage( name, "Format: !repeatfire <Weapon>:<Repeat in Miliseconds>");return;}
			int weapon = Integer.parseInt(msg.nextToken());
			int timerStart = 0;
			int repeatTime = Integer.parseInt(msg.nextToken());
			if(repeatTime >= 200){
				if(repeatFireTimers.size() > 2){
					m_botAction.sendSmartPrivateMessage( name, "Please, no more than three firing tasks.");
				}else new RepeatFireTimer(weapon, timerStart, repeatTime);
			}
			else
				m_botAction.sendSmartPrivateMessage( name, "Sending a weapon packet every " + repeatTime + "ms can cause the 0x07 bi-directional packet.");
			
		}catch(Exception e){}
	}
	
	public void doFireListCmd(String name){
		if(repeatFireTimers.size() == 0){			
			m_botAction.sendSmartPrivateMessage( name, "There are currently no firing tasks.");
			return;
		}
		Iterator<RepeatFireTimer> i = repeatFireTimers.iterator();
		while(i.hasNext()){
			m_botAction.sendSmartPrivateMessage( name, i.next().toString());
		}
	}
	
	public void doStopRepeatFireCmd(String message){
		if(repeatFireTimers.size() == 0){			
			return;
		}
		if(message == null){
			Iterator<RepeatFireTimer> i = repeatFireTimers.iterator();
			while(i.hasNext()){
				RepeatFireTimer r = i.next();
				r.cancel();
			}
			repeatFireTimers.clear();
		}
		else{
			try{
				int index = Integer.parseInt(message) - 1;
				repeatFireTimers.elementAt(index).cancel();
				repeatFireTimers.removeElementAt(index);
			}catch(Exception e){}
		}
	}
	
	public void doSetAccuracyCmd(String message){
		try{
			int x = Integer.parseInt(message);
			if(x < 0 || x > 5)return;
			REACTION_TIME = .075;
			for(int i = 0; i < x; i++){
				REACTION_TIME -= .02;
			}			
		}catch(Exception e){}
	}
	
	public void doMoveCmd(String name, String message){
		if(m_botAction.getShip().getShip() == 8)return;
		StringTokenizer argTokens = getArgTokens(message);
	    int numTokens = argTokens.countTokens();
	    if(numTokens != 4 && numTokens != 2)
	    	m_botAction.sendSmartPrivateMessage( name, "Format: !move X:Y:XVelocity:YVelocity");
	    else if(numTokens == 2)
	    	m_botAction.sendSmartPrivateMessage( name, "For a stationary warp use !warpto <X>:<Y>");
	    else if(numTokens == 4){
	    	try{
	    		int x = Integer.parseInt(argTokens.nextToken()) * 16;
	    		int y = Integer.parseInt(argTokens.nextToken()) * 16;
	    		int xVel = Integer.parseInt(argTokens.nextToken());
	    		int yVel = Integer.parseInt(argTokens.nextToken());
	    		m_botAction.getShip().move(x,y,xVel,yVel);
	    	}catch(Exception e){}
	    }
	}
	
	public StringTokenizer getArgTokens(String string)
	  {
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
        		try { Thread.sleep(125); } catch (InterruptedException e) { }

        	}
        }
    }

	
private class RepeatFireTimer {
	private int SPAWN_TIME = 5005;
	public int weapon, delayms, repeatms;
	public boolean isRunning = true, isSlowlyStopping = false;
	TimerTask repeat = null;
	TimerTask slowly;
	
	public RepeatFireTimer(int wep, int delayms, int repeatms){
		this.weapon = wep;
		this.delayms = delayms;
		this.repeatms = repeatms;
		repeatFireTimers.add(this);
		repeat = new TimerTask(){
			public void run(){
				if (!fireOnSight || (fireOnSight && enemyOnSight))
					doFireCmd(Integer.toString(weapon));
			}
		};
		m_botAction.scheduleTaskAtFixedRate(this.repeat, this.delayms, this.repeatms);
	}
	public void cancel(){
		if(repeat != null)
			repeat.cancel();
		isRunning = false;
	}
	public void pause(){
		if(!isRunning)return;
		repeat.cancel();
		repeat = new TimerTask(){
			public void run(){
				doFireCmd(Integer.toString(weapon));
			}
		};
		m_botAction.scheduleTaskAtFixedRate(this.repeat, this.SPAWN_TIME, this.repeatms);		
	}
	
	public void stop(){
		if(isRunning){
			repeat.cancel();
			isRunning = false;
		}
	}
	
	public String toString(){
		String s = (repeatFireTimers.indexOf(this)+1) + ") Firing weapon(" + weapon + ") every " + repeatms + " ms." ;		
		return s;
	}
}



}