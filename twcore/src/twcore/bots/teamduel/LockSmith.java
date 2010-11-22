package twcore.bots.teamduel;

import twcore.core.BotAction;

public class LockSmith {

    private boolean locked;
    private BotAction m_botAction;
    
    public LockSmith(BotAction action) {
        m_botAction = action;
        locked = true;
    }
    
    public boolean isLocked() {
        return locked;
    }
    
    public void lock() {
        locked = true;
        m_botAction.toggleLocked();
    }
    
    public void unlock() {
        locked = false;
        m_botAction.toggleLocked();
    }
    
    public void arenaUnlocked() {
        if (locked) 
            m_botAction.toggleLocked();
    }
    
    public void arenaLocked() {
        if (!locked) 
            m_botAction.toggleLocked();
    }
    
    
}
