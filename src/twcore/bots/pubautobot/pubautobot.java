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

	// Mode
	private static enum Mode { STATIC, DYNAMIC };
	private Mode mode = Mode.STATIC;
	
	// Bot settings
	private boolean locked = false;
	private String owner; // usually a bot
	private String subowner; // usually a player
	private boolean autoAiming = false;
	private boolean fireOnSight = false;
	private boolean following = false;
	private boolean killable = true;
	private HashSet<String> locations; // array of x:y position

	private int freq;
	private int botX;
	private int botY;
	private int angle = 0;
	private int angleTo = 0;
	
    private int turret = -1;
    private LinkedList<Projectile> fired = new LinkedList<Projectile>();
	private Vector<RepeatFireTimer> repeatFireTimers = new Vector<RepeatFireTimer>();
    private RotationTask rotationTask;
	
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

    public void handleEvent(ArenaJoined event) {
    	
    	/*
    	String onStart = m_botAction.getBotSettings().getString("OnStart"+m_botAction.getBotNumber());
    	if (onStart != null) {
    		
    		String[] commands = onStart.split(",");
    		for(String command: commands) {
    			handleCommand("null", command.trim());
    		}
    		
    	}
    	*/
    	
    }
    
    public void handleEvent(InterProcessEvent event){
    	IPCMessage ipc = (IPCMessage)event.getObject();
    	String message = ipc.getMessage();
    	if(message.equals("looking") && !locked) {
    		locked = true;
    		m_botAction.scheduleTask(new FreeTask(), 5*Tools.TimeInMillis.SECOND);
    		m_botAction.ipcSendMessage(IPC_CHANNEL, "locked", null, m_botAction.getBotName());
    	}
    	else if(message.equals("confirm_lock")) {
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
        req.request(EventRequester.ARENA_JOINED);
        req.request(EventRequester.PLAYER_LEFT);
        req.request(EventRequester.FREQUENCY_SHIP_CHANGE);
    }

    public void handleEvent(PlayerLeft event){
    	if(event.getPlayerID() == turret)doUnAttachCmd();
    }
    
    public void handleEvent(FrequencyShipChange event){
    	if(event.getPlayerID() == turret && event.getShipType() == 0)doUnAttachCmd();
    }
    
    public void handleEvent(PlayerDeath event) {
    	if(turret == -1)return;
    	String killer = m_botAction.getPlayerName(event.getKillerID());
    	//TODO: Check to make sure the killer isn't a bot.
    	if(turret == event.getKilleeID()){
    		doUnAttachCmd();
    		doAttachCmd(killer);
    	}

    }
    
    public void handleEvent(WeaponFired event) {
    	if(!killable) return;
    	if(m_botAction.getShip().getShip() == 8) return;
    	if(turret != -1) return;
    	Player p = m_botAction.getPlayer(event.getPlayerID());
        if(p == null || (p.getFrequency() == m_botAction.getShip().getAge()) || event.getWeaponType() > 8 || event.isType(5) || event.isType(6) || event.isType(7))return;
        double pSpeed = p.getWeaponSpeed();
		double bearing = Math.PI * 2 * (double)event.getRotation() / 40.0;
		fired.add(new Projectile(p.getPlayerName(), event.getXLocation() + (short)(10.0 * Math.sin(bearing)), event.getYLocation() - (short)(10.0 * Math.cos(bearing)), event.getXVelocity() + (short)(pSpeed * Math.sin(bearing)), event.getYVelocity() - (short)(pSpeed * Math.cos(bearing)), event.getWeaponType(), event.getWeaponLevel()));
	}
    
    public void handleEvent(PlayerPosition event) {
    	
    	if(m_botAction.getShip().getShip() == 8) {
    		enemyOnSight = false;
    		return;
    	}
    	Player p = m_botAction.getPlayer(event.getPlayerID());
        if(p == null || !autoAiming || (p.getFrequency() == freq)) {
        	enemyOnSight = false;
        	return;
        }
        
        if (!locations.isEmpty()) {
        	String xy = event.getXLocation()/16 + ":" + event.getYLocation()/16;
        	if (!locations.contains(xy)) {
        		enemyOnSight = false;
        		return;
        	}
        }

        enemyOnSight = true;
        
        double diffY, diffX, angle;
    	diffY = (event.getYLocation() + (event.getYVelocity() * REACTION_TIME)) - botY;
    	diffX = (event.getXLocation() + (event.getXVelocity() * REACTION_TIME)) - botX;
    	angle = (180 - (Math.atan2(diffX, diffY)*180/Math.PI)) * SS_CONSTANT;
    	
    	if (!following)
    		doFaceCmd(m_botAction.getBotName(), Double.toString(angle));
    	
    	if (following) {
    		
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

    		m_botAction.getShip().setVelocitiesAndDir(vX, vY, (int)angle);
    		
    	}
    }
    
    public void update(){
    	botX = m_botAction.getShip().getX();
 		botY = m_botAction.getShip().getY();

     	if(turret == -1){
     		ListIterator<Projectile> it = fired.listIterator();
     		while (it.hasNext()) {
     			Projectile b = (Projectile) it.next();     			
     			if (b.isHitting(botX, botY)) {
     				
     				if (m_botAction.getPlayer(b.getOwner()).getFrequency()==freq)
     					return;
     			
     				if(!isSpawning){
     					spawned = new TimerTask(){
     						public void run(){
     							isSpawning = false;
     						}
     					};
     					isSpawning = true;
     					m_botAction.scheduleTask(spawned, SPAWN_TIME);
     					m_botAction.sendDeath(m_botAction.getPlayerID(b.getOwner()), 0);
     					Iterator<RepeatFireTimer> i = repeatFireTimers.iterator();
     					while(i.hasNext())i.next().pause(); 				
     					it.remove();
     				}
     			} 
     			else if (b.getAge() > 5000) {
     				it.remove();
     			}
     		}
		}
     	else{
     		Player p = m_botAction.getPlayer(turret);
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
    	            doDieCmd(playerName);
    				
    		}
    		
    		if (locked && owner != null && (owner.equals(playerName) || subowner.equals(playerName))) {
    			handleCommand(playerName, message);
    		}
    		else {
    			if (owner != null) {
    				String controllerBy = owner;
    				if (subowner != null) controllerBy += " and " + subowner;
    				m_botAction.sendSmartPrivateMessage( playerName, "Hi! I'm controlled by " + controllerBy + ".");
    			}
    		}
    	}
    }
    
    public void handleCommand(String name, String msg){

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
		else if(msg.equalsIgnoreCase("!listfire"))
			doFireListCmd(name);
		else if(msg.startsWith("!stopfire "))
			doStopRepeatFireCmd(msg.substring(10));
		else if(msg.equalsIgnoreCase("!stopfire"))
			doStopRepeatFireCmd(null);
		else if(msg.equalsIgnoreCase("!killable"))
			doKillableCmd(name);
		else if(msg.startsWith("!brick "))
			doDropBrickCmd(msg.substring(7));
		else if(msg.equalsIgnoreCase("!brick"))
			doDropBrickWhereBotIsCmd();
		else if(msg.startsWith("!follow"))
			doFollowOnSightCmd(msg.substring(7));
		else if(msg.equalsIgnoreCase("!aim"))
			doAimCmd();
		else if(msg.startsWith("!attach "))
			doAttachCmd(msg.substring(8));
		else if(msg.equalsIgnoreCase("!unattach"))
			doUnAttachCmd();
		else if(msg.startsWith("!setaccuracy "))
			doSetAccuracyCmd(msg.substring(13));
		else if(msg.startsWith("!timeout "))
			doTimeoutDieCmd(msg.substring(9));
		else if(msg.startsWith("!move "))
			doMoveCmd(name,msg.substring(6));
		else if(msg.startsWith("!go "))
			doGoCmd(name, msg.substring(4).trim());	
		else if(msg.equalsIgnoreCase("!die"))
			doDieCmd(name);
		else if (msg.equalsIgnoreCase("!spec"))
			doSpecCmd(name);
		else if (msg.equalsIgnoreCase("!help"))
			m_botAction.smartPrivateMessageSpam( name, helpmsg );

    }
    
    public void doAttachCmd(String msg) 
    {
    	String name = m_botAction.getFuzzyPlayerName(msg);
    	if (name==null)
    		return;
    	
    	Player p = m_botAction.getPlayer(name);
    	if (p==null)
    		return;
    	
    	m_botAction.getShip().setFreq(p.getFrequency());
    	freq = p.getFrequency();
    	m_botAction.getShip().attach(m_botAction.getPlayerID(name));
    	turret = m_botAction.getPlayerID(name);
    }
    
    public void doUnAttachCmd(){
    	if(turret == -1)return;
    	m_botAction.getShip().unattach();
    	turret = -1;
    }
    
    public void doAimCmd(){
    	autoAiming = !autoAiming;
    }
    
    public void doFollowOnSightCmd(String name) {
    	following = !following;
    	if (following) {
    		autoAiming = true;
    		if (!name.isEmpty())
    			target = name.trim();
    	}
    }
    
    private void doKillableCmd(String sender) {
        killable = !killable;
    }
    
    private void doTimeoutDieCmd(String parameter) {
    	int seconds = Integer.valueOf(parameter);
    	int minutes = (int)(seconds/60);
        m_botAction.scheduleTask(new DieTask(), seconds * Tools.TimeInMillis.SECOND);
    }
    
    private void doDieCmd(String sender) {
        m_botAction.sendSmartPrivateMessage(sender, "Logging Off.");
        m_botAction.scheduleTask(new DieTask(), 500);
    }
    
    private void doGoCmd(String sender, String argString) {
        String currentArena = m_botAction.getArenaName();

        //if (currentArena.equalsIgnoreCase(argString))
        //    throw new IllegalArgumentException("Bot is already in that arena.");
        //if (isPublicArena(argString))
        //    throw new IllegalArgumentException("Bot can not go into public arenas.");
       	m_botAction.changeArena(argString);
        m_botAction.sendSmartPrivateMessage(sender, "Going to " + argString + ".");
    }
    
    private boolean isPublicArena(String arenaName) {
        try {
            Integer.parseInt(arenaName);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    public void doSetShipCmd(String name, String message){
    	try{
    		int ship = Integer.parseInt(message.trim());
    		if(ship <= 9 && ship >= 1){
    			m_botAction.getShip().setShip(ship-1);
    			botX = m_botAction.getShip().getX();
    			botY = m_botAction.getShip().getY();
    			m_botAction.getShip().setFreq(0);
    			m_botAction.cancelTask(updateIt);
    			m_botAction.scheduleTaskAtFixedRate(updateIt, 100, 100);
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
    
    public void doSpecCmd(String name){
    	if(m_botAction.getShip().getShip() == 8)return;
    	try{m_botAction.cancelTask(updateIt);}catch(Exception e){}
    	m_botAction.getShip().setShip(8);
    }
    
    public void doWarpToCmd(String name, String message){
    	if(m_botAction.getShip().getShip() == 8)return;
    	String[] msg = message.split(" ");
    	try {
    		int x = Integer.parseInt(msg[0]) * 16 + 8;
    		int y = Integer.parseInt(msg[1]) * 16 + 8;
    		m_botAction.getShip().move(x, y);
    		botX = x;
    		botY = y;
    	}catch(Exception e){}
    }
    
	public void doFaceCmd(String name, String message){
		if(m_botAction.getShip().getShip() == 8)return;
		try{
			float degree = Float.parseFloat(message);
			int l = Math.round(degree);		

			if (rotationTask == null) {
				rotationTask = new RotationTask();
				rotationTask.start();
			}
			angleTo = l;
			//System.out.println(l);
			//m_botAction.getShip().setRotation(l);
			
		}catch(Exception e){}
	}
	
	public void doDropBrickCmd(String message){
		if(m_botAction.getShip().getShip() == 8)return;
		try{
			String[] msg = message.split(" ");
			int x = Integer.parseInt(msg[0]);
			int y = Integer.parseInt(msg[1]);
			m_botAction.getShip().dropBrick(x, y);
		}catch(Exception e){}
	}
	
	public void doDropBrickWhereBotIsCmd(){
		m_botAction.getShip().dropBrick();
	}
	
	public void doFireCmd(String msg){
		if(m_botAction.getShip().getShip() == 8)return;
		try{
			int wep = Integer.parseInt(msg);
			m_botAction.getShip().fire(wep);
		}catch(Exception e){}
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
	
	private StringTokenizer getArgTokens(String string)
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
	
	private class FreeTask extends TimerTask {
        public void run() {
        	if (locked && owner == null) {
        		locked = false;
        	}
        }
    }
	
	private class RotationTask extends Thread {

		public RotationTask() {

		}
		
        public void run() {
        	
        	boolean invert = false;
        	
        	while(true) {

        		if (angle == angleTo) {
        			//try { Thread.sleep(500); } catch (InterruptedException e) { }
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