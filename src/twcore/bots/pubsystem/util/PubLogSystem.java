package twcore.bots.pubsystem.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import twcore.core.BotAction;
import twcore.core.util.Tools;

public class PubLogSystem {

	public static enum LogType { ITEM, MONEY, MISC, MOD };
	
	private static PubLogSystem instance;
	private HashMap<LogType, FileWriter> writers;
	
	private PubLogSystem(BotAction botAction) {
		
		writers = new HashMap<LogType, FileWriter>();
		
	    if (botAction.getBotSettings().getString("log_path") != null)
	    {
		    File file = new File(botAction.getBotSettings().getString("log_path"));
		    if (file.isDirectory()) {
		    	try {
		    		for(LogType log: LogType.values()) {
		    			String filename = log.toString().toLowerCase() + ".log";
		    			writers.put(log, new FileWriter(new File(file, filename), true));
		    		}
				} catch (IOException e) {
					e.printStackTrace();
				}
		    } else {
		    	Tools.printLog("Cannot store logs for the pub system.");
		    }
	    }
		
	}
	
	public static void prepare(BotAction botAction) {
		instance = new PubLogSystem(botAction);
	}
	
	public static void write(LogType log, String text) {
		if (instance != null)
			if (instance.writers.containsKey(log)) {
				FileWriter writer = instance.writers.get(log);
				if (writer != null) {
					try {
						writer.write(Tools.getTimeStamp() + " " + text);
						writer.flush();
					} catch (IOException e) { 
						
					}
				}
			}
	}
	
}
