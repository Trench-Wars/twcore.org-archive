package twcore.bots.tournybot;

import twcore.core.*;
import twcore.core.util.Tools;

public class dStats {

	BotAction m_botAction;

	fStats freq1;
	fStats freq2;
	int type;
	int tournyID;
	int startTime;
	int round;
	int box;
	int id1;
	int id2;

	int wR = 0;
	int lR = 0;

	boolean finished;

	// 1v1
	String player1Name;
	String player2Name;
	int player1Score;
	String player1Notes;
	int player2Score;
	String player2Notes;

	// 2v2
	String team1player1;
	String team1player2;
	String team2player1;
	String team2player2;
	int team1player1Kills;
	int team1player1Deaths;
	String team1player1Notes;
	int team1player2Kills;
	int team1player2Deaths;
	String team1player2Notes;
	int team2player1Kills;
	int team2player1Deaths;
	String team2player1Notes;
	int team2player2Kills;
	int team2player2Deaths;
	String team2player2Notes;

	int victor;
	int duration;


	public dStats(fStats f1, fStats f2, int teams, int tID, BotAction botAction) {
		freq1 = f1;
		freq2 = f2;
		m_botAction = botAction;
		type = teams;
		tournyID = tID;
		startTime = (int)(System.currentTimeMillis() / 1000);
		finished = false;
	}

	public dStats(int teams, int tID, BotAction botAction) {
		m_botAction = botAction;
		type = teams;
		tournyID = tID;
		startTime = (int)(System.currentTimeMillis() / 1000);
		finished = true;
	}


	public fStats getF1() { return freq1; }

	public fStats getF2() { return freq2; }

	public boolean getFinished() { return finished; }

	public int duration() { return (int)(System.currentTimeMillis() / 1000) - startTime; }

	public int getWR() { return wR; }

	public int getLR() { return lR; }

	public void endForfeitDuel(fStats winner) {

		round = winner.getRound();
		box = winner.getBox();

		id1 = winner.getDBID();
		id2 = -1;

		victor = winner.getPlayerNro();
		duration = this.duration();

		if (type == 1) {
			player1Name = winner.getName1();
			player2Name = "-BYE-";
			player1Score = winner.getGameKills();
			player1Notes = winner.getP1().getNotes();
			player2Score = 0;
			player2Notes = "-";
		} else {
			team1player1 = winner.getName1();
			team1player2 = winner.getName2();
			team2player1 = "-BYE-";
			team2player2 = "-BYE-";
			team1player1Kills = winner.getP1().getGameKills();
			team1player1Deaths = winner.getP1().getGameDeaths();
			team1player1Notes = winner.getP1().getNotes();
			team1player2Kills = winner.getP2().getGameKills();
			team1player2Deaths = winner.getP2().getGameDeaths();
			team1player2Notes = winner.getP2().getNotes();
			team2player1Kills = 0;
			team2player1Deaths = 0;
			team2player1Notes = "-";
			team2player2Kills = 0;
			team2player2Deaths = 0;
			team2player2Notes = "-";
		}
	}

	public void endDuel(fStats winner, fStats loser) {

   		finished = true;

		winner.incrementWins();
		loser.incrementLosses();

		if (winner.getRating() <= 0) {
			winner.setRating(1);
		}

		if (loser.getRating() <= 0) {
			loser.setRating(1);
		}

		double wRating = (loser.getRating() / 10.0) * (0.5 * (loser.getRating() / (double)winner.getRating())) + 5;
		double lRating = (loser.getRating() / 10.0) * (0.1 * (loser.getRating() / (double)winner.getRating())) + 1;

		wR = (int)wRating;
		lR = (int)lRating;

		winner.setRatingChange(winner.getRatingChange() + wR);
		loser.setRatingChange(loser.getRatingChange() - lR);

		if (winner.getPlayerNro() == 1) {
			freq1 = winner;
			freq2 = loser;
		} else {
			freq1 = loser;
			freq2 = winner;
		}

		round = freq1.getRound();
		box = freq1.getBox();

		id1 = freq1.getDBID();
		id2 = freq2.getDBID();

		victor = winner.getPlayerNro();
		duration = this.duration();

		if (type == 1) {
			player1Name = freq1.getName1();
			player2Name = freq2.getName1();
			player1Score = freq1.getGameKills();
			player1Notes = 	freq1.getP1().getNotes();
			player2Score = freq2.getGameKills();
			player2Notes = freq2.getP1().getNotes();
		} else {
			team1player1 = freq1.getName1();
			team1player2 = freq1.getName2();
			team2player1 = freq2.getName1();
			team2player2 = freq2.getName2();
			team1player1Kills = freq1.getP1().getGameKills();
			team1player1Deaths = freq1.getP1().getGameDeaths();
			team1player1Notes = freq1.getP1().getNotes();
			team1player2Kills = freq1.getP2().getGameKills();
			team1player2Deaths = freq1.getP2().getGameDeaths();
			team1player2Notes = freq1.getP2().getNotes();
			team2player1Kills = freq2.getP1().getGameKills();
			team2player1Deaths = freq2.getP1().getGameDeaths();
			team2player1Notes = freq2.getP1().getNotes();
			team2player2Kills = freq2.getP2().getGameKills();
			team2player2Deaths = freq2.getP2().getGameDeaths();
			team2player2Notes = freq2.getP2().getNotes();
		}
	}

	public void storeDuel(String connection) {

		if (type == 1) {
			try {
				String fields[] = {
					"tourny_ID",
					"round",
					"box",
					"player1id",
					"player1Name",
					"player2id",
					"player2Name",
					"player1Score",
					"player1Notes",
					"player2Score",
					"player2Notes",
					"winner",
					"duration"
				};

				String values[] = {
					Integer.toString(tournyID),
					Integer.toString(round),
					Integer.toString(box),
					Integer.toString(id1),
					Tools.addSlashesToString(player1Name),
					Integer.toString(id2),
					Tools.addSlashesToString(player2Name),
					Integer.toString(player1Score),
					player1Notes,
					Integer.toString(player2Score),
					player2Notes,
					Integer.toString(victor),
					Integer.toString(duration)
				};

				m_botAction.SQLBackgroundInsertInto(connection, "tblTourny1v1games", fields, values);
			} catch (Exception e) { };
		} else {

			try {

				String fields[] = {
					"tourny_ID",
					"round",
					"box",
					"team1",
					"team2",
					"team1player1",
					"team1player2",
					"team2player1",
					"team2player2",
					"team1player1Kills",
					"team1player1Deaths",
					"team1player1Notes",
					"team1player2Kills",
					"team1player2Deaths",
					"team1player2Notes",
					"team2player1Kills",
					"team2player1Deaths",
					"team2player1Notes",
					"team2player2Kills",
					"team2player2Deaths",
					"team2player2Notes",
					"winner",
					"duration"
				};

				String values[] = {
					Integer.toString(tournyID),
					Integer.toString(round),
					Integer.toString(box),
					Integer.toString(id1),
					Integer.toString(id2),
					Tools.addSlashesToString(team1player1),
					Tools.addSlashesToString(team1player2),
					Tools.addSlashesToString(team2player1),
					Tools.addSlashesToString(team2player2),
					Integer.toString(team1player1Kills),
					Integer.toString(team1player1Deaths),
					team1player1Notes,
					Integer.toString(team1player2Kills),
					Integer.toString(team1player2Deaths),
					team1player2Notes,
					Integer.toString(team2player1Kills),
					Integer.toString(team2player1Deaths),
					team2player1Notes,
					Integer.toString(team2player2Kills),
					Integer.toString(team2player2Deaths),
					team2player2Notes,
					Integer.toString(victor),
					Integer.toString(duration)
				};

				m_botAction.SQLBackgroundInsertInto(connection, "tblTourny2v2games", fields, values);
			} catch (Exception e) { };
		}
	}
}
