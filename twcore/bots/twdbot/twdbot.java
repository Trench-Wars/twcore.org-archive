
/*
 * twdbot.java
 *
 * Created on October 20, 2002, 5:59 PM
 */

package twcore.bots.twdbot;

import java.util.*;
import java.sql.*;
import twcore.core.*;
import twcore.misc.database.DBPlayerData;

/**
 *
 * @author  Administrator
 */
public class twdbot extends SubspaceBot {

    String m_arena;
    BotSettings m_botSettings;
    OperatorList m_opList;
    LinkedList m_players;

    /** Creates a new instance of twdbot */
    public twdbot( BotAction botAction) {
    	//Setup of necessary stuff for any bot.
        super( botAction );

        m_botSettings   = m_botAction.getBotSettings();
        m_arena 	= m_botSettings.getString("Arena");
        m_opList        = m_botAction.getOperatorList();

        m_players = new LinkedList();
        requestEvents();
    }



    public void requestEvents() {
        EventRequester req = m_botAction.getEventRequester();
        req.request( EventRequester.MESSAGE );
    };


    public static String[] stringChopper( String input, char deliniator ){
      try
      {
        LinkedList list = new LinkedList();

        int nextSpace = 0;
        int previousSpace = 0;

        if( input == null ){
            return null;
        }

        do{
            previousSpace = nextSpace;
            nextSpace = input.indexOf( deliniator, nextSpace + 1 );

            if ( nextSpace!= -1 ){
                String stuff = input.substring( previousSpace, nextSpace ).trim();
                if( stuff!=null && !stuff.equals("") )
                    list.add( stuff );
            }

        } while( nextSpace != -1 );
        String stuff = input.substring( previousSpace );
        stuff=stuff.trim();
        if (stuff.length() > 0) {
            list.add( stuff );
        };
        return (String[])list.toArray(new String[list.size()]);
      }
      catch(Exception e)
      {
        throw new RuntimeException("Error in stringChopper.");
      }
    }




    public void handleEvent( Message event ){
      try
      {
        boolean isStaff;
        String message = event.getMessage();
        if( event.getMessageType() == Message.PRIVATE_MESSAGE ){
            String name = m_botAction.getPlayerName( event.getPlayerID() );
            if( m_opList.isER( name )) isStaff = true; else isStaff= false;

            // First: convert the command to a command with parameters
            String command = stringChopper(message, ' ')[0];
            String[] parameters = stringChopper( message.substring( command.length() ).trim(), ':' );
            for (int i=0; i < parameters.length; i++) parameters[i] = parameters[i].replace(':',' ').trim();
            command = command.trim();

            parseCommand( name, command, parameters, isStaff );
        }
      }
      catch(Exception e)
      {
        m_botAction.sendSmartPrivateMessage("Cpt.Guano!", e.getMessage());
      }
    }


    public void parseCommand(String name, String command, String[] parameters, boolean isStaff) {
      try
      {
        if (command.equals("!signup")) {
            command_signup(name, command, parameters);
        };
        if (command.equals("!help")) {
            m_botAction.sendPrivateMessage(name, "!signup <password> - Example: !signup mypass. This command will get you an useraccount for TWL and TWD. If you have forgotten your password, you can use this to pick a new password");
        };
      }
      catch(Exception e)
      {
        throw new RuntimeException("Error in parseCommand.");
      }
    };

    public void handleEvent( LoggedOn event ) {
        m_botAction.joinArena( m_arena );

        TimerTask checkMessages = new TimerTask() {
            public void run() {
                checkMessages();
            };
        };
        m_botAction.scheduleTaskAtFixedRate(checkMessages, 5000, 10000);
    }


    public void command_signup(String name, String command, String[] parameters) {
        try {
            if (parameters.length > 0) {
                boolean success = false;
                boolean can_continue = true;

                String fcPassword = parameters[0];
                DBPlayerData thisP;

                thisP = findPlayerInList(name);
                if (thisP != null)
                    if (System.currentTimeMillis() - thisP.getLastQuery() < 300000)
                        can_continue = false;

                if (thisP == null) {
                    thisP = new DBPlayerData(m_botAction, "website", name, true);
                    success = thisP.getPlayerAccountData();
                } else success = true;

                if (can_continue) {

                    if (!success) {
                        success = thisP.createPlayerAccountData(fcPassword);
                    } else {
                        if (!thisP.getPassword().equals(fcPassword)) {
                            success = thisP.updatePlayerAccountData(fcPassword);
                        }
                    };

                    if (!thisP.hasRank(2)) thisP.giveRank(2);

                    if (success) {
                        m_botAction.sendPrivateMessage(name, "This is your account information: ");
                        m_botAction.sendPrivateMessage(name, "Username: " + thisP.getUserName());
                        m_botAction.sendPrivateMessage(name, "Password: " + thisP.getPassword());
                        m_botAction.sendPrivateMessage(name, "To join your squad roster, go to http://twd.trenchwars.org . Log in, click on 'Roster', select your squad, click on main and then click on 'Apply for this squad'");
                        m_players.add(thisP);
                    } else {
                        m_botAction.sendPrivateMessage(name, "Couldn't create/update your useraccount. Try again another day, if it still doesn't work, ?message lnx");
                    }
                } else {
                    m_botAction.sendPrivateMessage(name, "You can only signup / change passwords once every 5 minutes");
                };
            } else
                m_botAction.sendPrivateMessage(name, "Specify a password, ex. '!signup mypass'");

        }
        catch(Exception e)
        {
          throw new RuntimeException("Error in command_signnup.");
        }
    };




    public DBPlayerData findPlayerInList(String name) {
      try
      {
        ListIterator l = m_players.listIterator();
        DBPlayerData thisP;

        while (l.hasNext()) {
            thisP = (DBPlayerData)l.next();
            if (name.equalsIgnoreCase(thisP.getUserName())) return thisP;
        };
        return null;
      }
      catch(Exception e)
      {
        throw new RuntimeException("Error in findPlayerInList.");
      }
    };



    public void checkMessages() {
        try {
            ResultSet s = m_botAction.SQLQuery("local", "select * from tblMessage where fnProcessed = 0 and fcSubject='TWD'");
            while (s.next()) {
                if (s.getString("fcMessageType").equalsIgnoreCase("squad")) {
                    m_botAction.sendSquadMessage(s.getString("fcTarget"), s.getString("fcMessage"), s.getInt("fnSound"));
                    m_botAction.SQLQuery("local", "update tblMessage set fnProcessed = 1 where fnMessageID = " + s.getInt("fnMessageID"));
                };
            };
        } catch (Exception e) {
            System.out.println("Can't check for new messages...");
        };
    };

}
