package twcore.bots.pubhub;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashMap;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.events.InterProcessEvent;
import twcore.core.util.Tools;

public class pubhubstats extends PubBotModule {
	//private String database = "pubstats";
	private String database = "server";

	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	// boolean to immediately stop execution
	private boolean stop = false;

	/**
	 * This method initializes the pubhubstats module.  It is called after
	 * m_botAction has been initialized.
	 */
	public void initializeModule() {}

	/**
	 * Unused method but needs to be overridden
	 * @see Module.requestEvents()
	 */
	public void requestEvents(EventRequester eventRequester) {}

	/**
	 * This method handles the incoming InterProcessEvents,
	 * checks if the IPC is meant for this module and then extracts the containing
	 * object to store it to database.
	 *
	 * @param event is the InterProcessEvent to handle.
	 */
	@SuppressWarnings("unchecked")
	public void handleEvent(InterProcessEvent event)
	{

		// If the event.getObject() is anything else then the HashMap then return
		if(event.getObject() instanceof HashMap == false) {
			return;
		}
		if(event.getChannel().equals(pubhub.IPCPUBSTATS) == false) {
			return;
		}

		HashMap<String, PubStatsScore> stats = (HashMap<String, PubStatsScore>)event.getObject();
		updateDatabase(stats.values());
  }

	/**
	 * cancel() is called when this module is unloaded
	 */
	public void cancel() {
		stop = true;
	}

	/**
	 * Stores all the data from the PubStats objects into the tblScore table in the database
	 *
	 * @param stats the Collection containing the PubStats objects
	 */
	private void updateDatabase(Collection<PubStatsScore> stats) {
		// Loop over all the PubStats objects and replace each in the stats table
		/*for(PubStatsScore pubstats:stats) {
			if(stop) break;

			String query =
				"REPLACE INTO tblScore VALUES (" +
				"'" + Tools.addSlashesToString(pubstats.getPlayername()) + "'," +
				pubstats.getShip() + "," +
				"'" + pubstats.getSquad() + "'," +
				pubstats.getFlagPoints() + "," +
				pubstats.getKillPoints() + "," +
				pubstats.getWins() + "," +
				pubstats.getLosses() + "," +
				pubstats.getRate() + "," +
				pubstats.getAverage() + "," +
				"'" + sdf.format(pubstats.getDate()) + "'";
			m_botAction.SQLBackgroundQuery(this.database, null, query);
		}*/
	}
}