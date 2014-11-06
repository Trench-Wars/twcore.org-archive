package twcore.bots.pubsystem.module;

import java.util.Iterator;
import java.util.TimerTask;

import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.pubsystem;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.game.Player;
import twcore.core.util.Tools;

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
    
    private String database;
    
    private static final long DELAY = 10 * Tools.TimeInMillis.MINUTE; //in mS, 10 min set here
    
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
                m_botAction.sendSmartPrivateMessage(sender, 
                        "[GaugeModule] Started!");
            } else {
                m_botAction.sendSmartPrivateMessage(sender, 
                        "[GaugeModule] Already Started!");
            }
        } else if (command.equals("!stopgauge")){
            if (running) {
                stop();
                m_botAction.sendSmartPrivateMessage(sender, 
                        "[GaugeModule] Stopped!");
            } else {
                m_botAction.sendSmartPrivateMessage(sender, 
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
        	
            int warbirds = 0;
            int javelins = 0;
            int spiders = 0;
            int leviathans = 0;
            int terriers = 0;
            int weasels = 0;
            int lancasters = 0;
            int sharks = 0;
            int spectators = 0;
            
            Iterator<Player> i = m_botAction.getPlayerIterator();
            
            while(i.hasNext()) {
            	Player p = i.next();
            	
            	switch((int)p.getShipType())
            	{
            	case Tools.Ship.WARBIRD:
            		warbirds++;
            		break;
            	case Tools.Ship.JAVELIN:
            		javelins++;
            		break;
            	case Tools.Ship.SPIDER:
            		spiders++;
            		break;
            	case Tools.Ship.LEVIATHAN:
            		leviathans++;
            		break;
            	case Tools.Ship.WEASEL:
            		weasels++;
            		break;
            	case Tools.Ship.LANCASTER:
            		lancasters++;
            		break;
            	case Tools.Ship.SHARK:
            		sharks++;
            		break;
            	case Tools.Ship.SPECTATOR:
            		spectators++;
            		break;
            	default:
            		spectators++;
            		break;
            	}
            }
        
        	String query = String.format("INSERT INTO `tblPubPopExtended`(`fcWarbirdCount`, `fcJavelinCount`, `fcSpiderCount`, `fcLeviCount`, `fcTerrierCount`, `fcWeaselCount`, `fcLancasterCount`, `fcSharkCount`, `fcSpecCount`) "
        			+ "VALUES (%d,%d,%d,%d,%d,%d,%d,%d,%d)", warbirds, javelins, spiders, leviathans, terriers, weasels, lancasters, sharks, spectators);
            
        	m_botAction.SQLBackgroundQuery(database, "GaugeModule Insert", query);
            	
        }
        
    }
    
}
