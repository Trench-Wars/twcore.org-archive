package twcore.bots.staffbot;

import twcore.bots.ModuleHandler;
import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
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
import twcore.core.events.InterProcessEvent;
import twcore.core.events.KotHReset;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerBanner;
import twcore.core.events.PlayerDeath;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;
import twcore.core.events.PlayerPosition;
import twcore.core.events.Prize;
import twcore.core.events.SQLResultEvent;
import twcore.core.events.ScoreReset;
import twcore.core.events.ScoreUpdate;
import twcore.core.events.SoccerGoal;
import twcore.core.events.SubspaceEvent;
import twcore.core.events.TurfFlagUpdate;
import twcore.core.events.TurretEvent;
import twcore.core.events.WatchDamage;
import twcore.core.events.WeaponFired;
import twcore.core.util.Tools;

public class staffbot extends SubspaceBot {
	
	private ModuleHandler moduleHandler;
	
    OperatorList        m_opList;
    BotAction           m_botAction;
    BotSettings         m_botSettings;

    /* Initialization code */
    public staffbot( BotAction botAction ) {
        super( botAction );
        
        moduleHandler = new ModuleHandler(botAction, botAction.getGeneralSettings().getString("Core Location") + "/twcore/bots/staffbot", "staffbot");
        
        m_botAction = botAction;
        m_botSettings = m_botAction.getBotSettings();

        // Request Events
        EventRequester req = botAction.getEventRequester();
        req.requestAll();
    }
    
    @Override
    public void handleDisconnect() {
    	moduleHandler.unloadAllModules();
    }

	@Override
	public void handleEvent(LoggedOn event) {
		// join arena
		m_botAction.joinArena(m_botSettings.getString("InitialArena"));
		
		// join chats
		// 1 = staff chat
		// 2 = smod chat
		String staffchat = m_botAction.getGeneralSettings().getString("Staff Chat");
		String smodchat = m_botAction.getGeneralSettings().getString("Smod Chat");
		m_botAction.sendUnfilteredPublicMessage("?chat="+staffchat+","+smodchat);
		
		// load modules
		moduleHandler.loadModule("_serverwarningecho");
		moduleHandler.loadModule("_warnings");
		moduleHandler.loadModule("_savelog");
	}

	@Override
	public void handleEvent(Message event) {
		if( ( event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) && 
				event.getMessage().startsWith("!")
			 ) {
			// Commands
			String message = event.getMessage().toLowerCase();
			short sender = event.getPlayerID();
			String senderName = event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ? event.getMessager() :  m_botAction.getPlayerName(sender);
			int operatorLevel = m_botAction.getOperatorList().getAccessLevel(senderName);;
			
			// Ignore player's commands
			if(operatorLevel == OperatorList.PLAYER_LEVEL) {
				return;
			}
			
			// !help
			if(message.startsWith("!help")) {
				String[] help = {
						" Op: "+senderName+" ("+Tools.staffName(operatorLevel)+")",
						"-----------------------[ Staff Bot ]-----------------------"
				};
				
				String[] smodHelp = {
						" !die                      - Disconnects Staffbot"
				};
				
				m_botAction.smartPrivateMessageSpam(senderName, help);
				
				if(m_botAction.getOperatorList().isSmod(senderName)) {
					m_botAction.smartPrivateMessageSpam(senderName, smodHelp);
				}
			}
			if(message.equalsIgnoreCase("!die")) {
				moduleHandler.unloadAllModules();
				this.handleDisconnect();
				m_botAction.die();
			}
		}
		
		moduleHandler.handleEvent(event);
	}
	
	@Override
	public void handleEvent(SubspaceEvent event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(ArenaJoined event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(ArenaList event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(BallPosition event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(FileArrived event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(FlagClaimed event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(FlagDropped event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(FlagPosition event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(FlagReward event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(FlagVictory event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(FrequencyChange event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(FrequencyShipChange event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(InterProcessEvent event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(KotHReset event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(PlayerBanner event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(PlayerDeath event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(PlayerEntered event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(PlayerLeft event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(PlayerPosition event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(Prize event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(ScoreReset event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(ScoreUpdate event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(SoccerGoal event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(SQLResultEvent event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(TurfFlagUpdate event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(TurretEvent event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(WatchDamage event) {
		moduleHandler.handleEvent(event);
	}

	@Override
	public void handleEvent(WeaponFired event) {
		moduleHandler.handleEvent(event);
	}
	
}