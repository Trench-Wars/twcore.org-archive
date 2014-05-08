package twcore.bots.pubsystem.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TimerTask;
import java.sql.SQLException;
import java.sql.ResultSet; 

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.bots.pubsystem.module.PubUtilModule.Location;
import twcore.bots.pubsystem.module.PubUtilModule.Region;
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
	public long startAt = 0;
    private int killLeader = 0;
	
	private TimerTask pauseTask;
	private TimerTask endSessionTask;
	
	private HashMap<String,Integer> kills;
	private HashMap<String,Long> lastDeaths;
	private LinkedHashSet<Location> locations;
	private HashSet<String> notplaying;
	
	PubContext context;
	

	public PubKillSessionModule(BotAction botAction, PubContext context) {
		super(botAction,context,"Kill-o-thon");
		this.context = context;
		
		notplaying = new HashSet<String>();
		locations = new LinkedHashSet<Location>();
		kills = new HashMap<String,Integer>();
		
		reloadConfig();
		
	}

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.PLAYER_DEATH);
    }

    @Override
    public void reloadConfig() {
        
        length = m_botAction.getBotSettings().getInt("killothon_length");
        interval = m_botAction.getBotSettings().getInt("killothon_interval");
        winnerMoney = m_botAction.getBotSettings().getInt("killothon_winner_money");
        String k = "killothon_enabled";
        if (m_botAction.getBotSettings().getInt(k)==1) {
            enabled = true;
        }
        int[] pieces = m_botAction.getBotSettings().getIntArray("killothon_locations",",");
        for(int piece: pieces) {
            try {
                Location location = Location.valueOf(Region.values()[piece].toString());
                locations.add(location);
            } catch (Exception e) {
                Tools.printLog("[PubKillSession] Exception thrown when loading locations. " + e.getMessage());
            }
        }
    }
	
	public boolean isRunning() {
		return sessionStarted;
	}
	
	public void startSession() {
		
		if (!enabled)
			return;
		
		kills = new HashMap<String,Integer>();
		lastDeaths = new HashMap<String,Long>();

		if (!context.hasJustStarted()) {
			if (context.getMoneySystem().isEnabled())
				m_botAction.sendArenaMessage("[KILL-O-THON] begins. Most in-base kills in " + length + "min wins $" + winnerMoney + ". (:tw-p:!killothon for score)");
			else
				m_botAction.sendArenaMessage("[KILL-O-THON] has begun. Kill the most in " + length + " minutes.");
		}

		/*
		if (locations.size() > 0) {
			
			String message = "";
			for(Location loc: locations) {
				message += ", " + context.getPubUtil().getLocationName(loc);
			}
			m_botAction.sendArenaMessage("[KILL-O-THON] To count, kills must be inside of: " + message.substring(2));
			
		}
		*/
		
		try {
			endSessionTask.cancel();
			pauseTask.cancel();
		} catch (Exception e) { }
		
		pauseTask = new TimerTask() {
			public void run() {
				startSession();
			}
		};
		
		endSessionTask = new TimerTask() {
			public void run() {
				stopSession(true);
				m_botAction.scheduleTask(pauseTask, interval * Tools.TimeInMillis.MINUTE);
			}
		};
		
		// Timer to end the session after "length" minutes
		m_botAction.scheduleTask(endSessionTask, length * Tools.TimeInMillis.MINUTE);
	
		sessionStarted = true;
		startAt = System.currentTimeMillis();
	}
	
	public void stopSession(boolean announce) {
		
		if (!enabled || !sessionStarted) {
			try {
				endSessionTask.cancel();
				pauseTask.cancel();
			} catch (Exception e) { }
			return;
		}
		
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
			
			if (announce)
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
			
			if (announce)
			{
				String moneyMessage = "";
				if (context.getMoneySystem().isEnabled()) {
					moneyMessage = " of $" + winnerMoney;
				}
				
				if (names.size()==1)
					m_botAction.sendArenaMessage("[KILL-O-THON] is over. WINNER" + moneyMessage + " with " + killNumber + " kills : " + namesString);
				else
					m_botAction.sendArenaMessage("[KILL-O-THON] is over. WINNERS" + moneyMessage + " with " + killNumber + " kills : " + namesString);
	
				// Winner(s) money	
				if (context.getMoneySystem().isEnabled()) {
					for(String name: names) {
						context.getPlayerManager().addMoney(name, winnerMoney, true);
					}
				}
				
				for(String name: names) {
					updateWinnerDB(name);
				}
			}
			
		}
		
		if (!announce) {
			endSessionTask.cancel();
			pauseTask.cancel();
			m_botAction.sendArenaMessage("[KILL-O-THON] The session has been cancelled.");
		}
		
		sessionStarted = false;
	}
	
	/**
	 * Case-sensitive
	 */
	public boolean isLeader(String playerName) {
		
		if (!sessionStarted)
			return false;
		
		if (getLeadersList().contains(playerName)) {
			return true;
		} else {
			return false;
		}
	}
	
    private void updateWinnerDB(String playerName) {
    	
		String database = m_botAction.getBotSettings().getString("database");
		
		// The query will be closed by PlayerManagerModule
		Integer kotwins;
		if (database!=null) {
		    try {
		        ResultSet rs = m_botAction.SQLQuery(database, "SELECT fnKillothonWinner FROM tblPlayerStats WHERE fcName='"
		            + Tools.addSlashes(playerName) + "'");
	            if (rs.next()) {
	                kotwins = rs.getInt("fnKillothonWinner");
	                checkKoTAward(playerName, kotwins+1);
	            }
                m_botAction.SQLClose(rs);
		    } catch( SQLException e) {
		    }
		    
		    m_botAction.SQLBackgroundQuery(database, null, "UPDATE tblPlayerStats "
		            + "SET fnKillothonWinner = fnKillothonWinner+1 "
		            + "WHERE fcName='" + Tools.addSlashes(playerName) + "'");
		}    	
    }
    
    /**
     * Checks for and awards KoT special rewards.
     * @param numWins
     */
    public void checkKoTAward( String name, int numWins ) {
        String awardString = "";
        int bonus = 0;
        switch (numWins) {
        case 1:
            awardString = "You've won your first Kill-o-Thon! Have a nice fat bonus.";
            bonus = 1000;
            break;
        case 3:
            awardString = "That's a triple under your belt now. Keep up the good work.";
            bonus = 250;
            break;
        case 5:
            awardString = "I see you're getting the hang of this.";
            bonus = 500;
            break;
        case 10:
            awardString = "Well on the way to becoming an unstoppable assassin.";
            bonus = 750;
            break;
        case 25:
            awardString = "Striking fear into their trembling hearts.";
            bonus = 1000;
            break;
        case 50:
            awardString = "BEAST MODE ACTIVATED.";
            bonus = 2000;
            break;
        case 75:
            awardString = "Cheats?";
            bonus = 4000;
            break;
        case 100:
            awardString = "You might be able to go pro with this.";
            bonus = 6000;
            break;
        case 150:
            awardString = "Watch out, we got a badass here!";
            bonus = 8000;
            break;
        case 200:
            awardString = "Let me make sure I read that right...";
            bonus = 10000;
            break;
        case 250:
            awardString = "A LEGEND IN YOUR OWN TIME!";
            bonus = 15000;
            break;
        case 500:
            awardString = "WHO ARE YOU?!?!";
            bonus = 25000;
            break;
        case 750:
            awardString = "Let me take a minute to just stand here in awe.";
            bonus = 50000;
            break;
        case 1000:
            awardString = "YOU. ARE. A. GOD.";
            bonus = 100000;
            break;
        case 5000:
            awardString = "I don't know how you did it. I don't want to know. Just take my money and go.";
            bonus = 150000;
            break;
        case 10000:
            awardString = "To give you real money for this would only cheapen your accomplishment. Congratu-freakin'-lations.";
            bonus = 1;
            break;
        }
        
        if (bonus > 0) {
            m_botAction.sendPrivateMessage(name, "KILL-O-THON WINS MILESTONE ... " + numWins + " win" + (numWins==1?"":"s") + ".  Bonus: $" + bonus + "!  " + awardString );
            context.getPlayerManager().addMoney(name, bonus);
            
            if (numWins >= 50)
                m_botAction.sendArenaMessage( name + " has won Kill-o-Thon " + numWins + " times!", Tools.Sound.CROWD_OOO);
        }
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
    			
    			m_botAction.sendSmartPrivateMessage(sender, "New settings: Session of " + length + " mins. every " + interval + " mins.");
    			m_botAction.sendSmartPrivateMessage(sender, "              The winner will receive $" + money + ".");
    			
    		} catch(NumberFormatException e) {
    			m_botAction.sendSmartPrivateMessage(sender, "You must type a number for any of the arguments.");
    		}
    	}
    	else {
    		m_botAction.sendSmartPrivateMessage(sender, "Wrong arguments, must be <length>:<interval>:<money>.");
    	}
    	
    }

    public void doStartCmd( String sender ) {
    	
    	if (sessionStarted) {
    		m_botAction.sendSmartPrivateMessage(sender, "A session of kill-o-thon is already running.");
    		return;
    	}
    	startSession();
    }
    
    public void doStopCmd( String sender ) {
    	stopSession(true);
    }
    
    public void doCancelCmd( String sender ) {
    	stopSession(false);
    	m_botAction.sendSmartPrivateMessage(sender, "No more kill-o-thon will");
    }
    
    public void doNotPlayingCmd( String sender ) {
    	
    	if (notplaying.contains(sender)) {
    		notplaying.remove(sender);
    		m_botAction.sendSmartPrivateMessage(sender, "You have been removed from the not playing list.");
    	} else {
    		notplaying.add(sender);
    		m_botAction.sendSmartPrivateMessage(sender, "You have been added to the not playing list. Type !npkill again to play.");
    	}
    }
    
    public void doStatCmd( String sender, boolean messageSuffix ) {
    	
    	if (sessionStarted) {
    		
    		String message = "";
    		
    		if (kills.containsKey(sender)) {
    			message = "You have " + kills.get(sender) + " kills. ";
    		}
    		else {
    			message = "You have 0 kills, what are you waiting for? ";
    		}
    		
    		if (notplaying.contains(sender)) {
    			message = "You have are currently on the not playing list. ";
    		}
    		
    		// Sort by number of kills order descending
    		List<String> leaders = getLeadersList();
    		
    		if (!leaders.isEmpty()) {
    			
    			String namesString = leaders.get(0);
    			
				if (leaders.size() > 1) {
					for(int i=1; i<leaders.size(); i++) {
						namesString += ", " + leaders.get(i);
					}
					message += "Current leaders: " + namesString + " with " + killLeader + " kills.";
				} else if (namesString.equals(sender)) {
					message += "You are the leader!";
				} else {
					message += "Current leader: " + namesString + " with " + killLeader + " kills.";
				}

    		}
    		else {
    			message += "There is no leader at this moment.";
    		}
    		 
    		if (messageSuffix) {
    			m_botAction.sendSmartPrivateMessage(sender, "[KILL-O-THON] " + message);
    			m_botAction.sendSmartPrivateMessage(sender, "[KILL-O-THON] Time left: " + getTimeRemaining());
    		} else {
    			m_botAction.sendSmartPrivateMessage(sender, message);
    			m_botAction.sendSmartPrivateMessage(sender, "Time left: " + getTimeRemaining());
    		}
    		
    	}
    	else {
    		m_botAction.sendSmartPrivateMessage(sender, "There is no session running now. Next session: " + getNextSession());
    	}
    }

    public List<String> getLeadersList() {

		// Sort by number of kills order descending
		List<Integer> nums = new ArrayList<Integer>(kills.values());
		Collections.sort(nums);
		
		List<Integer> reverseNums = new ArrayList<Integer>();
		for(int i=nums.size()-1; i>=0; i--) {
			reverseNums.add(nums.get(i));
		}
		
		List<String> names = new ArrayList<String>();
		
		killLeader = 0;
		
		if (reverseNums.size() > 0) {
    		int highest = reverseNums.get(0);
    		// Remapping by number of kills
    		for(String playerName: kills.keySet()) {
    			int count = kills.get(playerName);
    			if (count == highest)
    				names.add(playerName);
    		}
    		killLeader = highest;
		}
		
		return names;
    }
    
    public String getNextSession() {
    	
    	if (sessionStarted) {
    		return "Currently running";
    	} else {
    		if (startAt == 0) {
    			return "Unknown";
    		} else {
    			return Tools.getTimeDiffString(startAt+(length+interval)*Tools.TimeInMillis.MINUTE, false);	
    		}
    	}
    	
    }
    
    public String getTimeRemaining() {
    	
    	if (sessionStarted) {
    		long diff = System.currentTimeMillis()-startAt;
    		return Tools.getTimeDiffString(System.currentTimeMillis()+length*Tools.TimeInMillis.MINUTE-diff, false);
    	} else {
    		return "Not running";
    	}
    	
    }
    
    public void handleEvent(PlayerDeath event) {
        
        if (!enabled || !sessionStarted)
            return;
        
        Player killer = m_botAction.getPlayer(event.getKillerID());
        Player killed = m_botAction.getPlayer(event.getKilleeID());
        
        if (killer == null || killed == null)
            return;
        
        if (killer.getFrequency() == killed.getFrequency())
            return;
        
        Location location = context.getPubUtil().getLocation(killer.getXTileLocation(), killer.getYTileLocation());
        
        // If locations is not empty, 
        // It means that the kill must be done inside of one of the location on this list to count
        if (locations.size() == 0 || locations.contains(location)) {
            
            Integer count = kills.get(killer.getPlayerName());
            if (count == null) {
                count = 0;
            }
            count++;
            kills.put(killer.getPlayerName(), count);
            if (count%10==0) {
                doStatCmd(killer.getPlayerName(), true);
            }
            
        } else {
            
            m_botAction.sendSmartPrivateMessage(killer.getPlayerName(), "");
            
        }

        lastDeaths.put(killed.getPlayerName(), System.currentTimeMillis());
        
    }
    
	@Override
	public void handleCommand(String sender, String command) {

        try {
        	
            if(command.trim().equals("!killothon"))
            	doStatCmd(sender,false);
            if(command.trim().equals("!npkillothon") || command.trim().equals("!npkill"))
            	doNotPlayingCmd(sender);
            
        } catch(RuntimeException e) {
        	Tools.printStackTrace(e);
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
		
	}

	@Override
	public void handleModCommand(String sender, String command) {
		
        try {
        	
            if(command.startsWith("!settingkillothon"))
            	doSettingCmd(sender, command.substring(18).trim());
            else if(command.trim().equals("!startkillothon"))
            	doStartCmd(sender);

        } catch(RuntimeException e) {
        	Tools.printStackTrace(e);
            if( e != null && e.getMessage() != null )
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
	}

    @Override
    public void handleSmodCommand(String sender, String command) {
        
    }
	
	@Override
	public String[] getHelpMessage(String sender) {
		return new String[] {
				pubsystem.getHelpLine("!killothon        -- Your current stat + current leader + time left or next session."),
				pubsystem.getHelpLine("!npkillothon      -- Toggles not playing mode. (!npkill)"),
        };
	}

	@Override
	public String[] getModHelpMessage(String sender) {
		return new String[] {
			pubsystem.getHelpLine("!settingkillothon <length>:<interval>:<money> -- KoT cfg. Current=" + length + ":" + interval + ":" + winnerMoney),
			pubsystem.getHelpLine("!startkillothon       -- Start a new session of kill-o-thon (" + length + " min. for $" + winnerMoney + ")."),
        };
	}

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[]{};
    }

    @Override
    public void start() {
        startSession();
    }

	@Override
	public void stop() {
		stopSession(false);
	}

}
