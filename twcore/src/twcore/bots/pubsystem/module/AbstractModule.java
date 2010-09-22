package twcore.bots.pubsystem.module;

import twcore.core.events.ArenaJoined;
import twcore.core.events.ArenaList;
import twcore.core.events.BallPosition;
import twcore.core.events.FileArrived;
import twcore.core.events.FlagClaimed;
import twcore.core.events.FlagDropped;
import twcore.core.events.FlagPosition;
import twcore.core.events.FlagReward;
import twcore.core.events.FlagVictory;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerBanner;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.Prize;
import twcore.core.events.ScoreReset;
import twcore.core.events.ScoreUpdate;
import twcore.core.events.SoccerGoal;
import twcore.core.events.WatchDamage;
import twcore.core.events.WeaponFired;

/*
 * A module is something that can be disabled/enabled.
 * It has a functionnality to the pub system.
 * 
 * Some are more importants than others like :
 * - MoneySystem
 * - PlayerManager
 * 
 * Also, some modules are dependants to another module
 * Dependencies:
 * - TO BE COMPLETED
 * 
 */
public abstract class AbstractModule {

	protected boolean enabled = false;
	
	public void enable() {
		this.enabled = true;
	}
	
	public void disable() {
		this.enabled = false;
	}
	
	public boolean isEnabled() {
		return enabled;
	}
	
	public abstract void handleCommand(String sender, String command);
	public abstract void handleModCommand(String sender, String command);
	
	public void handleEvent(Message event) {

	}
	
	public void handleEvent(ArenaList event) {

	}
	
	public void handleEvent(PlayerLeft event) {

	}

	public void handleEvent(PlayerEntered event) {

	}

	public void handleEvent(PlayerPosition event) {

	}

	public void handleEvent(PlayerDeath event) {

	}

	public void handleEvent(PlayerBanner event) {

	}

	public void handleEvent(Prize event) {

	}

	public void handleEvent(ScoreUpdate event) {

	}

	public void handleEvent(WeaponFired event) {

	}

	public void handleEvent(FrequencyChange event) {

	}

	public void handleEvent(FrequencyShipChange event) {
		
	}

	public void handleEvent(FileArrived event) {
		
	}

	public void handleEvent(ArenaJoined event) {

	}

	public void handleEvent(FlagVictory event) {
		
	}

	public void handleEvent(FlagReward event) {
		
	}

	public void handleEvent(ScoreReset event) {
		
	}

	public void handleEvent(WatchDamage event) {
		
	}

	public void handleEvent(SoccerGoal event) {
		
	}

	public void handleEvent(BallPosition event) {
		
	}

	public void handleEvent(FlagPosition event) {
		
	}

	public void handleEvent(FlagDropped event) {
		
	}

	public void handleEvent(FlagClaimed event) {
		
	}
	
}
