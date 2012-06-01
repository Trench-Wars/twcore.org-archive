package twcore.bots.pubsystem.module;

import java.util.TimerTask;
import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.core.BotAction;
import twcore.core.EventRequester;

/**
 * Gauge Module for Pub System
 * 
 * Module should support benchmarking of current pub population and store to
 * database.
 * 
 * @see http://twcore.org/ticket/780
 * @author SpookedOne
 */
public class GaugeModule extends AbstractModule {
    
    private static final String TABLE = "tblPubPop";
    private String database;
    
    private static final long DELAY = 1000 * 60 * 10; //in mS, 10 min set here
    
    private Task task = null;
    private boolean running = false;
    
    public GaugeModule(BotAction botAction, PubContext context) {
        super(botAction, context, "Gauge");
        task = new Task();
        database = m_botAction.getBotSettings().getString("database");
    }
    
    @Override
    public void start() {
        m_botAction.scheduleTaskAtFixedRate(task, 0, DELAY);
        running = true;
    }

    @Override
    public void stop() {
        m_botAction.cancelTask(task);
        running = false;
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {}

    @Override
    public void reloadConfig() {}

    @Override
    public void handleCommand(String sender, String command) {
    }

    @Override
    public void handleModCommand(String sender, String command) {
    }

    @Override
    public void handleSmodCommand(String sender, String command) {
        if (command.equals("!startgauge")) {
            if (!running) {
                start();
                m_botAction.sendPrivateMessage(sender, 
                        "[GaugeModule] Started!");
            } else {
                m_botAction.sendPrivateMessage(sender, 
                        "[GaugeModule] Already Started!");
            }
        } else if (command.equals("!stopgauge")){
            if (running) {
                stop();
                m_botAction.sendPrivateMessage(sender, 
                        "[GaugeModule] Stopped!");
            } else {
                m_botAction.sendPrivateMessage(sender, 
                        "[GaugeModule] Already Stopped!");
            }
            
        }
    }

    @Override
    public String[] getHelpMessage(String sender) {
        return new String[]{};
    }

    @Override
    public String[] getModHelpMessage(String sender) {
        return new String[]{};
    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[] { 
            pubsystem.getHelpLine("!startgauge      -- Starts the GaugeModule."),
            pubsystem.getHelpLine("!stopgauge       -- Stops the GaugeModule."),
        };
    }
    
    private class Task extends TimerTask {

        @Override
        public void run() {
            int n = m_botAction.getNumPlaying();
            m_botAction.SQLBackgroundQuery(database, "GaugeModule Insert", 
                    "INSERT INTO " + TABLE + " (fcCount) VALUES (" + n + ");");
        }
        
    }
    
}
