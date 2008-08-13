package twcore.bots.pubbot;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.PlayerEntered;
import twcore.core.game.Player;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

public class pubbottwd extends PubBotModule {
	
	private String PUBBOTS = "pubBots";
	
    public void initializeModule(){}
    public void cancel(){}

    public void requestEvents( EventRequester eventRequester ){
        eventRequester.request( EventRequester.PLAYER_ENTERED );
    }

    public void handleEvent( PlayerEntered event ){
    	Player p = m_botAction.getPlayer(event.getPlayerID());
    	if(p == null)return;
    	m_botAction.ipcTransmit(PUBBOTS, new IPCMessage("getgame " + p.getPlayerName() + ":" + p.getSquadName(),"PubHub"));
    }
    
    public void handleEvent(InterProcessEvent event) {
      	try
          {
      		if(!(event.getObject() instanceof IPCMessage))return;
      		IPCMessage ipcMessage = (IPCMessage) event.getObject();
      		String message = ipcMessage.getMessage();
      		String recipient = ipcMessage.getRecipient();
      		String sender = ipcMessage.getSender();
      		if(recipient == null || recipient.equals(m_botAction.getBotName()))
      			handleBotIPC(sender, message);
        }
        catch(Exception e){
          Tools.printStackTrace(e);
        }
    }
    
    public void handleBotIPC(String sender, String message){
    	if(message.startsWith("givegame "))
    		doGotGame(message.substring(9));
    }
    
    public void doGotGame(String message){
    	String[] msg = message.split(":");
    	m_botAction.sendSmartPrivateMessage(msg[0], "Your squad is currently playing a TWD match in ?go " + msg[1] + " -" + msg[2]);
    }
}

