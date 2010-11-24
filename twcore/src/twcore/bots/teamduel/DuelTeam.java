package twcore.bots.teamduel;

public class DuelTeam {
    int teamID;
    String[] names;
    int[] ids;
    int division;
    String divisionName;
    boolean notPlaying;
    boolean nowPlaying;

    public DuelTeam(int id, String name1, String name2, int nameid1, int nameid2, int div, String divis) {
        teamID = id;
        names = new String[] {name1, name2};
        ids = new int[] {nameid1, nameid2};
        division = div;
        notPlaying = false;
        nowPlaying = false;
        divisionName = divis;
    }

    public String toString() {
        String team = "|";
        
        if (teamID / 10 > 0) {
            if (teamID / 100 > 0) {
                if (teamID / 1000 > 0) {
                    team += " " + teamID + "|"; 
                } else
                    team += "  " + teamID + " |";
            } else
                team += "  " + teamID + " |";
        } else // "| ID#  | Division | Partners"
            team += "   " + teamID + " |";
        if (division == 1 || division == 2) 
            team += " " + divisionName + "  | ";
        else if (division == 3)
            team += "  " + divisionName + "  | ";
        else if (division == 4 || division == 7)
            team += " " + divisionName + "| ";
        else if (division == 5)
            team += "  " + divisionName + "   | ";
        team += names[0] + " and " + names[1];
        
        while (team.length() < 55) {
            team += " ";
        }
        
        return team + "|";
    }

    public int getTeamID() {
        return teamID;
    }

    public String[] getNames() {
        return names;
    }

    public int[] getIDs() {
        return ids;
    }

    public int getDivision() {
        return division;
    }

    public boolean getNotPlaying() {
        return notPlaying;
    }

    public void setNotPlayingOn() {
        notPlaying = true;
    }

    public void setNotPlayingOff() {
        notPlaying = false;
    }

    public void setNowPlaying() {
        if (!nowPlaying)
            nowPlaying = true;
        else
            nowPlaying = false;
    }

    public boolean getNowPlaying() {
        return nowPlaying;
    }

}
