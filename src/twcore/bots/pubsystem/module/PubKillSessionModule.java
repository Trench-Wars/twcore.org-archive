package twcore.bots.pubsystem.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TimerTask;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.PlayerDeath;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubKillSessionModule extends AbstractModule {

	private int length;		// in minutes
	private int interval;	// in minutes
	
	private int winnerMoney;
	
	private boolean sessionStarted = false;
	
	private TimerTask startSessionTask;
	private TimerTask endSessionTask;
	
	private HashMap<String,Integer> kills;

	public PubKillSessionModule(BotAction botAction, PubContext context) {
		super(botAction,context,"Kill-o-thon");

		kills = new HashMap<String,Integer>();
		reloadConfig();
	}
	
	public boolean isRunning() {
		return sessionStarted;
	}
	
	public void startSession() {
		
		if (!enabled)
			return;
		
		kills = new HashMap<String,Integer>();
		
		if (!context.hasJustStarted()) {
			if (context.getMoneySystem().isEnabled())
				m_botAction.sendArenaMessage("[KILL-O-THON] A new session has started. Kill the most in " + length + " minutes and win $" + winnerMoney + ".");
			else
				m_botAction.sendArenaMessage("[KILL-O-THON] A new session has started. Kill the most in " + length + " minutes.");
		}
		
		startSessionTask = new TimerTask() {
			public void run() {
				startSession();
			}
		};
		
		endSessionTask = new TimerTask() {
			public void run() {
				stopSession(true);
			}
		};
		
		// Timer to end the session after "length" minutes
		m_botAction.scheduleTask(endSessionTask, length * Tools.TimeInMillis.MINUTE);
		// Prepare the next session in "interval" minutes
		m_botAction.scheduleTask(startSessionTask, interval * Tools.TimeInMillis.MINUTE);
	
		sessionStarted = true;
	}
	
	public void stopSession(boolean withWinner) {
		
		if (!enabled || !sessionStarted)
			return;
		
		// Sort by number of kills order descending
		List<Integer> nums = new ArrayList<Integer>(kills.values());
		Collections.sort(nums);
		
		List<Integer> reverseNums = new ArrayList<Integer>();
		for(int i=nums.size()-1; i>=0; i--) {
			reverseNums.add(nums.get(i));
		}
		
		// Remapping by number of kills
		HashMap<Integer,List<String>> index = new HashMap<Integer,List<String>>();
		for(String playerName: kills.keySet()) {
			int count = kills.get(playerName);
			List<String> names = index.get(count);
			if (names == null) {
				names = new ArrayList<String>();
			}
			names.add(playerName);
			index.put(count, names);
		}
		
		// Announce the winner(s)
		if (reverseNums.size() == 0) {
			
			if (withWinner)
				m_botAction.sendArenaMessage("[KILL-O-THON] End of the session. No winner.");
			
		} 
		else {
			
			int killNumber = reverseNums.get(0);

			List<String> names = index.get(killNumber);

			String namesString = names.get(0);
			if (names.size() > 1) {
				for(int i=1; i<names.size(); i++) {
					namesString += ", " + names.get(i);
				}
			}
			
			if (withWinner)
			{
				String moneyMessage = "";
				if (context.getMoneySystem().isEnabled()) {
					moneyMessage = " of $" + winnerMoney;
				}
				
				if (names.size()==1)
					m_botAction.sendArenaMessage("[KILL-O-THON] End of the session. Winner" + moneyMessage + " with " + killNumber + " kills : " + namesString);
				else
					m_botAction.sendArenaMessage("[KILL-O-THON] End of the session. Winners" + moneyMessage + " with " + killNumber + " kills : " + namesString);
	
				// Winner(s) money	
				if (context.getMoneySystem().isEnabled()) {
					for(String name: names) {
						if (context.getPlayerManager().getPlayer(name) != null)
							context.getPlayerManager().getPlayer(name).addMoney(winnerMoney);
					}
				}
			}
			
		}
		
		if (!withWinner) {
			endSessionTask.cancel();
			startSessionTask.cancel();
			m_botAction.sendArenaMessage("[KILL-O-THON] The session has been cancelled.");
		}
		
		sessionStarted = false;
	}
	
	public void handleEvent(PlayerDeath event) {
		
		if (!enabled || !sessionStarted)
			return;
		
		Player killer = m_botAction.getPlayer(event.getKillerID());
		Player killee = m_botAction.getPlayer(event.getKilleeID());
		
		if (killer == null || killee == null)
			return;
		
		if (killer.getFrequency() == killee.getFrequency())
			return;
		
		Integer count = kills.get(killer.getPlayerName());
		if (count == null) {
			count = 0;
		}
		count++;
		kills.put(killer.getPlayerName(), count);
		
	}
	
    public void doSettingCmd( String sender, String setting ) {

    	String[] split = setting.split(":");
    	
    	if (split.length == 3) {
    		try {
    			int length = Integer.parseInt(split[0]);
    			int interval = Integer.parseInt(split[1]);
    			int money = Integer.parseInt(split[2]);
    			
    			this.length = length;
    			this.interval = interval;
    			this.winnerMoney = money;
    			
    			m_botAction.sendPrivateMessage(sender, "New settings: Session of " + length + " mins. every " + interval + " mins.");
    			m_botAction.sendPrivateMessage(sender, "              The winner will receive $" + money + ".");
    			
    		} catch(NumberFormatException e) {
    			m_botAction.sendPrivateMessage(sender, "You must type a number for any of the arguments.");
    		}
    	}
    	else {
    		m_botAction.sendPrivateMessage(sender, "Wrong arguments, must be <length>:<interval>:<money>.");
    	}
    	
    }

    public void doStartCmd( String sender ) {
    	
    	if (sessionStarted) {
    		m_botAction.sendPrivateMessage(sender, "A session of kill-o-thon is already running. Use !killothon_stop to stop it.");
    		return;
    	}
    	startSession();
    }
    
    public void doStopCmd( String sender ) {
    	stopSession(true);
    }
    
    public void doCancelCmd( String sender ) {
    	stopSession(false);
    }
    
    public void doStatCmd( String sender ) {
    	if (sessionStarted) {
    		
    		if (kills.containsKey(sender)) {
    			m_botAction.sendPrivateMessage(sender, "You have " + kills.get(sender) + " kills.");
    		}
    		else {
    			m_botAction.sendPrivateMessage(sender, "You have 0 kill, what are you waiting for?");
    		}
    		
    		// Sort by number of kills order descending
    		List<Integer> nums = new ArrayList<Integer>(kills.values());
    		Collections.sort(nums);
    		
    		List<Integer> reverseNums = new ArrayList<Integer>();
    		for(int i=nums.size()-1; i>=0; i--) {
    			reverseNums.add(nums.get(i));
    		}
    		
    		if (reverseNums.size() > 0) {
	    		int highest = reverseNums.get(0);
	    		
	    		// Remapping by number of kills
	    		List<String> names = new ArrayList<String>();
	    		for(String playerName: kills.keySet()) {
	    			int count = kills.get(playerName);
	    			if (count == highest)
	    				names.add(playerName);
	    		}
	    		
				String namesString = names.get(0);
				if (names.size() > 1) {
					for(int i=1; i<names.size(); i++) {
						namesString += ", " + names.get(i);
					}
					m_botAction.sendPrivateMessage(sender, "Current leaders: " + namesString + " with " + highest + " kills.");
				} else {
					m_botAction.sendPrivateMessage(sender, "Current leader: " + namesString + " with " + highest + " kills.");
				}

    		}
    		else {
    			m_botAction.sendPrivateMessage(sender, "There is no leader at this moment.");
    		}
    		
    	}
    	else {
    		m_botAction.sendPrivateMessage(sender, "There is no session running at this moment.");
    	}
    }
    
	@Override
	public void handleCommand(String sender, String command) {

        try {
        	
            if(command.trim().equals("!killothon"))
            	doStatCmd(sender);
            
        } catch(RuntimeException e) {
        	Tools.printStackTrace(e);
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
		
	}

	@Override
	public void handleModCommand(String sender, String command) {
		
        try {
        	
            if(command.startsWith("!killothon_setting"))
            	doSettingCmd(sender, command.substring(18).trim());
            else if(command.trim().equals("!killothon_start"))
            	doStartCmd(sender);
            else if(command.trim().equals("!killothon_stop"))
            	doStopCmd(sender);
            else if(command.trim().equals("!killothon_cancel"))
            	doCancelCmd(sender);
            
        } catch(RuntimeException e) {
        	Tools.printStackTrace(e);
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
	}
	
	@Override
	public String[] getHelpMessage() {
		return new String[] {
			pubsystem.getHelpLine("!killothon        -- Your current stat + current leader."),
        };
	}

	@Override
	public String[] getModHelpMessage() {
		return new String[] {
			pubsystem.getHelpLine("!killothon_setting <length>:<interval>:<money>   -- Change settings (in minutes)."),
			pubsystem.getHelpLine("!killothon_start       -- Start a new session of kill-o-thon (" + length + " min. for $" + winnerMoney + ")."),
			pubsystem.getHelpLine("!killothon_stop        -- Stop the current session with winner announcement."),
			pubsystem.getHelpLine("!killothon_cancel      -- Cancel the current session without any announcement."),
        };
	}

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.PLAYER_DEATH);
	}

	@Override
	public void start() {
		startSession();
	}

	@Override
	public void reloadConfig() {
		
		length = m_botAction.getBotSettings().getInt("killsession_length");
		interval = m_botAction.getBotSettings().getInt("killsession_interval");
		winnerMoney = m_botAction.getBotSettings().getInt("killsession_winner_money");
		
		if (m_botAction.getBotSettings().getInt("killsession_enabled")==1) {
			enabled = true;
		}
		
	}

}
