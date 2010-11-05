package twcore.bots.pubsystem.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import twcore.core.BotAction;
import twcore.core.util.Tools;

public class Log {

	private FileWriter writer;
	
	public Log(BotAction botAction, String filename) {

	    if (botAction.getBotSettings().getString("log_path") != null)
	    {
	    	try {
			    File file = new File(botAction.getBotSettings().getString("log_path"));
			    writer = new FileWriter(new File(file, filename), true);
			} catch (IOException e) {
				Tools.printStackTrace(e);
			} 
	    }
		
	}
	
	public void close() {
		try {
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void write(String text) {
		if (writer != null)
		try {
			writer.write(text + "\r\n");
			writer.flush();
		} catch (IOException e) {
			Tools.printStackTrace(e);
		}
	}
	
}
