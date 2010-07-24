package twcore.bots.matchbot;
import twcore.core.BotAction;
/*
 * Retrieves the Arena that the bot is located in. This class can be used if
 * different arenas (for example a twd arena vs. a twdt arena) need different
 * match settings, using the boolean. 
 * 
 * When I created this method I was a bit nervous to mess too much with matchbot
 * codes. Please feel free to consolidate the getArenaType() method into another
 * file should you see it fit, perhaps in MatchRound.java where the booleans
 * are tested.
 */
public class MatchArena {

	BotAction m_botAction;
	boolean TWLType = false;
	boolean TWDType = false;
	boolean TWDTType = false;
	boolean NoType = false;
	
    public void getArenaType() {
		if(m_botAction.getArenaName().startsWith("twl")){
			TWLType = true;
		}
		else if(m_botAction.getArenaName().startsWith("twjd") || m_botAction.getArenaName().startsWith("twdd") || m_botAction.getArenaName().startsWith("twbd")){
			TWDType = true;
		}
		else if(m_botAction.getArenaName().startsWith("twdt")){
			TWDTType = true;
		}
		else	{
			NoType = true;
			/*
			 * no type if there is some unforeseen issue reading the arena name,
			 * or if a matchbot is running in an arena not included above.
			 */
		}
	}
	
	
}
