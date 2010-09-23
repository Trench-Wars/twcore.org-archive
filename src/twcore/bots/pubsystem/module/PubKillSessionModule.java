package twcore.bots.pubsystem.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;

import twcore.bots.pubsystem.PubContext;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.PlayerDeath;
import twcore.core.game.Player;
import twcore.core.util.Tools;

public class PubKillSessionModule extends AbstractModule {

	private PubContext context;
	
	private int length;		// in minutes
	private int interval;	// in minutes
	
	private int winnerMoney;
	
	private boolean sessionStarted = false;
	
	private HashMap<String,Integer> kills;

	public PubKillSessionModule(BotAction botAction, PubContext context) {
		super(botAction);
		
		this.context = context;
		
		length = m_botAction.getBotSettings().getInt("killsession_length");
		interval = m_botAction.getBotSettings().getInt("killsession_interval");
		winnerMoney = m_botAction.getBotSettings().getInt("killsession_winner_money");
		
		if (m_botAction.getBotSettings().getInt("killsession_enabled")==1) {
			enabled = true;
		}
		
		kills = new HashMap<String,Integer>();
		
	}
	
	public void startSession() {
		
		if (!enabled)
			return;
		
		kills = new HashMap<String,Integer>();
		
		m_botAction.sendArenaMessage("[KILL-O-THON] A new session has started. Kill the most in " + length + " minutes and win $" + winnerMoney + ".");
		
		TimerTask startSessionTask = new TimerTask() {
			public void run() {
				startSession();
			}
		};
		
		TimerTask endSessionTask = new TimerTask() {
			public void run() {
				stopSession();
			}
		};
		
		// Timer to end the session after "length" minutes
		m_botAction.scheduleTask(endSessionTask, length * Tools.TimeInMillis.MINUTE);
		// Prepare the next session in "interval" minutes
		m_botAction.scheduleTask(startSessionTask, interval * Tools.TimeInMillis.MINUTE);
	
		sessionStarted = true;
	}
	
	public void stopSession() {
		
		if (!enabled || !sessionStarted)
			return;
		
		// Sort by number of kills order descending
		List<Integer> nums = new ArrayList<Integer>(kills.values());
		System.out.println(nums.size());
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
				
			if (names.size()==1)
				m_botAction.sendArenaMessage("[KILL-O-THON] End of the session. Winner of $" + winnerMoney+ " with " + killNumber + " kills : " + namesString);
			else
				m_botAction.sendArenaMessage("[KILL-O-THON] End of the session. Winners of $" + winnerMoney+ " with " + killNumber + " kills : " + namesString);

			// Winner(s) money		
			for(String name: names) {
				context.getPlayerManager().getPlayer(name).addMoney(winnerMoney);
			}
			
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

	@Override
	public void handleCommand(String sender, String command) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void handleModCommand(String sender, String command) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.PLAYER_DEATH);
	}

	@Override
	public void start() {
		startSession();
	}

}
