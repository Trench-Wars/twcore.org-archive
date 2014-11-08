package twcore.bots.pubsystem.module;

import twcore.bots.pubsystem.PubContext;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.game.Player;
import twcore.core.util.Tools;

/*
 * Disallows players from speccing on public freqs.
 */

public class BlockSpecModule extends AbstractModule {
	
	private OperatorList oplist;

	public BlockSpecModule(BotAction botAction, PubContext context) {
		super(botAction, context, "BlockSpec");
		oplist = m_botAction.getOperatorList();
	}
    
    public void handleEvent(FrequencyChange event) {
    	if(event.getFrequency() > 1)
    		return;
    	
    	short pid = event.getPlayerID();
    	Player player = m_botAction.getPlayer(pid);
    	
    	boolean isStaffer = false;
    	
    	if(player != null)
        	isStaffer = oplist.isBot(name);
    	else
    		return;

    	if(player.getShipType() != Tools.Ship.SPECTATOR)
    		return;
    	
    	if(isStaffer == false) {
    		m_botAction.sendPrivateMessage(pid, "Spectating on public frequencies is forbidden! D:");
    		m_botAction.setShip(pid, Tools.Ship.WARBIRD);
    		m_botAction.specWithoutLock(pid);
    	}

    }
    
    @Override
	public String[] getHelpMessage(String sender) {
		return new String[] {};
	}

	@Override
	public String[] getModHelpMessage(String sender) {
		return new String[] {};
	}

    public void handleCommand(String sender, String command) {
    }

	@Override
	public void handleModCommand(String sender, String command) {
	}

	@Override
	public void reloadConfig() {

	}

	@Override
	public void requestEvents(EventRequester eventRequester) {
		eventRequester.request(EventRequester.FREQUENCY_CHANGE);
	}
	
	@Override
	public void start() {
		
	}
	
	@Override
	public void stop() {
	}

    @Override
    public void handleSmodCommand(String sender, String command) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[]{};
    }

}
