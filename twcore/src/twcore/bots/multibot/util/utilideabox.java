package twcore.bots.multibot.util;

import twcore.bots.MultiUtil;
import twcore.core.events.Message;
import twcore.core.util.ModuleEventRequester;
import twcore.core.util.Tools;

/**
 * @author milosh
 */
public class utilideabox extends MultiUtil {
	
	@Override
	public String[] getHelpMessages() {
		String[] message =
	    {
	      "+=============== HELP ===============+",
	      "| !idea <message> - submits an idea  |",
	      "+====================================+"
		
	    };
	    return message;
	}
	
	@Override
	public void init() {}

	@Override
	public void requestEvents(ModuleEventRequester modEventReq) {}
	
	public void handleEvent(Message event)
	{
		String message = event.getMessage();
        String name = event.getMessager() == null ? m_botAction.getPlayerName(event.getPlayerID()) : event.getMessager();
		if(event.getMessageType() == Message.PRIVATE_MESSAGE)
			handlePlayerCommands(name, message);
	}
	
	public void handlePlayerCommands(String name, String cmd){
		if(cmd.startsWith("!idea ") && cmd.substring(6).length() > 0){
			try{
				m_botAction.sendEmailMessage("twsuggestions@googlegroups.com", cmd.substring(6));
			}catch(Exception e){Tools.printStackTrace(e);}
		}
	}
	
	public void cancel() {}
}