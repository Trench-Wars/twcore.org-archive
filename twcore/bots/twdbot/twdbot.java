/*
 * twdbot.java
 *
 * Created on October 20, 2002, 5:59 PM
 */

package twcore.bots.twdbot;

import java.util.*;
import java.sql.*;
import twcore.core.*;
import java.text.*;
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
	LinkedList m_squadowner;

	private String requester = "";

	private String register = "";
	private HashMap m_access;
	private HashMap m_waitingAction;

	int ownerID;

    /** Creates a new instance of twdbot */
    public twdbot( BotAction botAction) {
    	//Setup of necessary stuff for any bot.
        super( botAction );

        m_botSettings   = m_botAction.getBotSettings();
        m_arena 	= m_botSettings.getString("Arena");
        m_opList        = m_botAction.getOperatorList();

        m_players = new LinkedList();
		m_squadowner = new LinkedList();

		m_access = new HashMap();
		m_waitingAction = new HashMap();

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

			if( m_opList.isSmod( name ) || m_access.containsKey( name.toLowerCase() ) ) 
			{
				//Operator commands
				if( message.startsWith( "!resetname " ) )
					commandResetName( name, message.substring( 11 ), false );
				else if( message.startsWith( "!cancelreset " ) )
					commandCancelResetName( name, message.substring( 13 ), false );
				else if( message.startsWith( "!resettime " ) )
					commandGetResetTime( name, message.substring( 11 ), false, false );
				else if( message.startsWith( "!enablename " ) )
					commandEnableName( name, message.substring( 12 ) );
				else if( message.startsWith( "!disablename " ) )
					commandDisableName( name, message.substring( 13 ) );
				else if( message.startsWith( "!info " ) )
					commandDisplayInfo( name, message.substring( 6 ) );
				else if( message.startsWith( "!register " ) )
					commandRegisterName( name, message.substring( 10 ), false );
				else if( message.startsWith( "!registered " ) )
					commandCheckRegistered( name, message.substring( 12 ) );
				else if( message.startsWith( "!ipcheck " ) )
					commandIPCheck( name, message.substring( 9 ) );
				else if( message.startsWith( "!midcheck " ) )
					commandMIDCheck( name, message.substring( 10 ) );
				else if( message.startsWith( "!check " ) )
                    checkIP( name, message.substring( 7 ) );
				else if( message.startsWith( "!go " ) )
					m_botAction.changeArena( message.substring( 4 ) );
				else if( message.startsWith( "!help" ) )
					commandDisplayHelp( name, false );
			} 
			else 
			{
				//Player commands
				if( message.equals( "!resetname" ) )
					commandResetName( name, name, true);
				else if( message.equals( "!resettime" ) )
					commandGetResetTime( name, name, true, false );
				else if( message.equals( "!cancelreset" ) )
					commandCancelResetName( name, name, true );
				else if( message.equals( "!registered" ) )
					commandCheckRegistered( name, name );
				else if( message.startsWith( "!registered " ) )
					commandCheckRegistered( name, message.substring( 12 ) );
				else if( message.equals( "!register" ) )
					commandRegisterName( name, name, true );
				else if( message.equals( "!help" ) )
					commandDisplayHelp( name, true );
			}

            // First: convert the command to a command with parameters
            String command = stringChopper(message, ' ')[0];
            String[] parameters = stringChopper( message.substring( command.length() ).trim(), ':' );
            for (int i=0; i < parameters.length; i++) parameters[i] = parameters[i].replace(':',' ').trim();
            command = command.trim();

            parseCommand( name, command, parameters, isStaff );
        }

        if( event.getMessageType() == Message.ARENA_MESSAGE) {
			if (event.getMessage().startsWith("Owner is ")) {
				String squadOwner = event.getMessage().substring(9);

				ListIterator i = m_squadowner.listIterator();
				while (i.hasNext())
				{
					SquadOwner t = (SquadOwner) i.next();
					if (t.getID() == ownerID) {
						if (t.getOwner().equalsIgnoreCase(squadOwner)) {
							storeSquad(t.getSquad(), t.getOwner());
						} else {
							m_botAction.sendSmartPrivateMessage(t.getOwner(), "You are not the owner of the squad " + t.getSquad());
						}
					}
				}
				ownerID++;
			} else if (message.startsWith( "IP:" )) {
				parseIP( message );
			}
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
        if (command.equals("!squadsignup")) {
            command_squadsignup(name, command);
        };
        if (command.equals("!help")) {
			String help[] = {
				"--------- TWD/TWL COMMANDS -----------------------------------------------------------",
				"!signup <password>      - Replace <password> with a password which is hard to guess.",
				"                          You are safer if you choose a password that differs",
				"                          completely from your current SSCU Continuum password.",
				"                          Example: !signup mypass. This command will get you an",
				"                          useraccount for TWL and TWD. If you have forgotten your",
				"                          password, you can use this to pick a new password",
				"!squadsignup            - This command will sign up your current ?squad for TWD.",
				"                          Note: You need to be the squadowner of the squad", 
				"                          and !registered"
			};
			m_botAction.privateMessageSpam(name, help);
        };
      }
      catch(Exception e)
      {
        throw new RuntimeException("Error in parseCommand.");
      }
    };

    public void handleEvent( LoggedOn event ) {
        m_botAction.joinArena( m_arena );
		ownerID = 0;

		String accessList = m_botSettings.getString( "AccessList" );
        
		//Parse accesslist
		String pieces[] = accessList.split( "," );
		for( int i = 0; i < pieces.length; i++ )
			m_access.put( pieces[i].toLowerCase(), pieces[i] );

        TimerTask checkMessages = new TimerTask() {
            public void run() {
                checkMessages();
				checkNamesToReset();
            };
        };
        m_botAction.scheduleTaskAtFixedRate(checkMessages, 5000, 10000);
    }


    public void command_signup(String name, String command, String[] parameters) {
        try {
            if (parameters.length > 0 && passwordIsValid(parameters[0])) {

		boolean success = false;
                boolean can_continue = true;

                String fcPassword = parameters[0];
                DBPlayerData thisP;

                thisP = findPlayerInList(name);
                if (thisP != null)
                    if (System.currentTimeMillis() - thisP.getLastQuery() < 300000)
                        can_continue = false;

		if (thisP == null) {
                    thisP = new DBPlayerData(m_botAction, "local", name, true);
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
                        m_botAction.sendSmartPrivateMessage(name, "This is your account information: ");
                        m_botAction.sendSmartPrivateMessage(name, "Username: " + thisP.getUserName());
                        m_botAction.sendSmartPrivateMessage(name, "Password: " + thisP.getPassword());
                        m_botAction.sendSmartPrivateMessage(name, "To join your squad roster, go to http://twd.trenchwars.org . Log in, click on 'Roster', select your squad, click on main and then click on 'Apply for this squad'");
                        m_players.add(thisP);
                    } else {
                        m_botAction.sendSmartPrivateMessage(name, "Couldn't create/update your useraccount. Try again another day, if it still doesn't work, ?message PriitK");
                    }
                } else {
                    m_botAction.sendSmartPrivateMessage(name, "You can only signup / change passwords once every 5 minutes");
                };
            } else
                m_botAction.sendSmartPrivateMessage(name, "Specify a password, ex. '!signup mypass'. The password must contain a number and needs to be at least 5 characters long.");

        }
        catch(Exception e)
        {
          throw new RuntimeException("Error in command_signup.");
        }
    };

    public boolean passwordIsValid(String pw) {

        if (pw.length() < 5) {
            return false;
        } else {
             for (int i = 0; i < pw.length(); i++) {

                 if (Character.isDigit(pw.charAt(i))) {
                     return true;
                 }
             }
             return false;
        }
    }

	public void command_squadsignup(String name, String command) {
        Player p = m_botAction.getPlayer(name);
		String squad = p.getSquadName();
		if (squad.equals(""))
		{
			m_botAction.sendSmartPrivateMessage(name, "You are not in a squad.");
		} else {
			m_squadowner.add(new SquadOwner(name, squad, ownerID));
			m_botAction.sendUnfilteredPublicMessage("?squadowner " + squad);
		}
	}


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
            ResultSet s = m_botAction.SQLQuery("local", "select * from tblMessage where fnProcessed = 0 and fcSubject='TWD' and fcMessage != ' '");
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


	public void storeSquad(String squad, String owner) {

		try
		{
			DBPlayerData thisP2 = new DBPlayerData(m_botAction, "server", owner, false);

			if (thisP2 == null || !thisP2.isRegistered()) {
				m_botAction.sendSmartPrivateMessage(owner, "Your name has not been !registered. Please private message AliasTron with !register.");
				return;
			}
			if (!thisP2.isEnabled()) {
				return;
			}

			DBPlayerData thisP = new DBPlayerData(m_botAction, "local", owner, false);

			if (thisP != null)
			{
				if (thisP.getTeamID() == 0)
				{
					ResultSet s = m_botAction.SQLQuery("local", "select fnTeamID from tblTeam where fcTeamName = '" + Tools.addSlashesToString(squad) + "' and (fdDeleted = 0 or fdDeleted IS NULL)");
					if (s.next()) {
						m_botAction.sendSmartPrivateMessage(owner, "That squad is already registered..");
						return;
					}

					String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
					String fields[] = {
						"fcTeamName",
						"fdCreated"
					};
					String values[] = {
						Tools.addSlashesToString(squad),
						time
					};
					m_botAction.SQLInsertInto("local", "tblTeam", fields, values);

					int teamID;

					ResultSet s2 = m_botAction.SQLQuery("local", "SELECT MAX(fnTeamID) AS fnTeamID FROM tblTeam");
					if (s2.next()) {
						teamID = s2.getInt("fnTeamID");
					} else {
						m_botAction.sendSmartPrivateMessage(owner, "Database error, contact a TWD Op.");
						return;
					}

					String fields2[] = {
						"fnUserID",
						"fnTeamID",
						"fdJoined",
						"fnCurrentTeam"
					};
					String values2[] = {
						Integer.toString(thisP.getUserID()),
						Integer.toString(teamID),
						time,
						"1"
					};
					m_botAction.SQLInsertInto("local", "tblTeamUser", fields2, values2);

					thisP.giveRank(4);

					m_botAction.sendSmartPrivateMessage(owner, "The squad " + squad + " has been signed up for TWD.");
				} else
					m_botAction.sendSmartPrivateMessage(owner, "You must leave your current squad first.");
			} else
				m_botAction.sendSmartPrivateMessage(owner, "You must !signup first.");
		}
		catch (Exception e)
		{
			m_botAction.sendSmartPrivateMessage(owner, "Database error, contact a TWD Op.");
		}
	}

	class SquadOwner {
	    String owner = "", squad = "";
		int id;

	    public SquadOwner(String name, String tSquad, int tID) {
			owner = name;
			squad = tSquad;
			id = tID;
	    };

	    public String getOwner() { return owner; };
	    public String getSquad() { return squad; };
		public int getID() { return id; };
	};




	// aliasbot

	public void commandCheckRegistered( String name, String message ) 
	{
		
		DBPlayerData dbP = new DBPlayerData( m_botAction, "server", message );
		
		if( dbP.isRegistered() )
			m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has been registered." );
		else
			m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has NOT been registered." );
	}

	public void commandResetName( String name, String message, boolean player ) 
	{

		DBPlayerData dbP = new DBPlayerData( m_botAction, "server", message );

		if( !dbP.isRegistered() ) 
		{
			if( player )
				m_botAction.sendSmartPrivateMessage( name, "Your name '"+message+"' has not been registered." );
			else
				m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has not been registered." );
			return;
		}

		if( !dbP.isEnabled() )
		{
			m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' is disabled and can't be reset." );    
			return;
		}

		if ( player ) {
			if( !resetPRegistration(dbP.getUserID()) ) 
			{
				m_botAction.sendSmartPrivateMessage( name, "Unable to reset name, please contact a TWD Op." );
				return;
			}
		} else {
			if ( !dbP.resetRegistration() )
			{
				m_botAction.sendSmartPrivateMessage( name, "Error resetting name '"+message+"'" );
			}
			return;
		}

		if( player ) {
			m_botAction.sendSmartPrivateMessage( name, "Your name will be reset in 24 hours." );
		} else {
			m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has been reset." );
		}
	}

	public void commandCancelResetName( String name, String message, boolean player )
	{
		DBPlayerData dbP = new DBPlayerData( m_botAction, "server", message );

		try
		{
			ResultSet s = m_botAction.SQLQuery( "server", "SELECT * FROM tblAliasSuppression WHERE fnUserID = '" + dbP.getUserID() + "' && fdResetTime IS NOT NULL");
			if (s.next())
			{
				m_botAction.SQLQuery( "server", "UPDATE tblAliasSuppression SET fdResetTime = NULL WHERE fnUserID = '" + dbP.getUserID() + "'");

				if (player)
				{
					m_botAction.sendSmartPrivateMessage( name, "Your name has been removed from the list of names about to get reset.");
				} else {
					m_botAction.sendSmartPrivateMessage( name, "The name '" + message + "' has been removed from the list of names about to get reset.");
				}
			} else {
				if (player)
				{
					m_botAction.sendSmartPrivateMessage( name, "Your name isn't on the list of names about to get reset.");
				} else {
					m_botAction.sendSmartPrivateMessage( name, "The name '" + message + "' was not found on the list of names about to get reset.");
				}
			}
		}		
		catch (Exception e)
		{
			if (player)
			{
				m_botAction.sendSmartPrivateMessage( name, "Database error, contact a TWD Op.");
			} else {
				m_botAction.sendSmartPrivateMessage( name, "Database error: " + e.getMessage() + ".");
			}
		}
	}

	public void commandGetResetTime( String name, String message, boolean player, boolean silent )
	{
		DBPlayerData dbP = new DBPlayerData( m_botAction, "server", message );

		try
		{
			ResultSet s = m_botAction.SQLQuery( "server", "SELECT DATE_ADD(fdResetTime, INTERVAL 1 DAY) AS resetTime FROM tblAliasSuppression WHERE fnUserID = '" + dbP.getUserID() + "' && fdResetTime IS NOT NULL");
			if (s.next())
			{
				String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
				if (player)
				{
					m_botAction.sendSmartPrivateMessage( name, "Your name will reset at " + s.getString("resetTime") + ". Current time: " + time);
				} else {
					m_botAction.sendSmartPrivateMessage( name, "The name '" + message + "' will reset at " + s.getString("resetTime") + ". Current time: " + time);
				}
			} else {
				if (!silent) {
					if (player)
					{
						m_botAction.sendSmartPrivateMessage( name, "Your name was not found on the list of names about to get reset.");
					} else {
						m_botAction.sendSmartPrivateMessage( name, "The name '" + message + "' was not found on the list of names about to get reset.");
					}
				}
			}
		}
		catch (Exception e)
		{
			if (player)
			{
				m_botAction.sendSmartPrivateMessage( name, "Database error, contact a TWD Op.");
			} else {
				m_botAction.sendSmartPrivateMessage( name, "Database error: " + e.getMessage() + ".");
			}
		}
	}
		
	public void commandEnableName( String name, String message ) 
	{

		DBPlayerData dbP = new DBPlayerData( m_botAction, "server", message );

		if( !dbP.isRegistered() ) 
		{
			m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has not been registered." );
			return;
		}

		if( dbP.isEnabled() ) 
		{
			m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' is already enabled." );
			return;
		}

		if( !dbP.enableName() ) 
		{
			m_botAction.sendSmartPrivateMessage( name, "Error enabling name '"+message+"'" );
			return;
		}
		m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has been enabled." );
	}

	public void commandDisableName( String name, String message ) 
	{

		DBPlayerData dbP = new DBPlayerData( m_botAction, "server", message );

		if( !dbP.isRegistered() ) 
		{
			m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has not been registered." );
			return;
		}

		if( !dbP.isEnabled() ) 
		{
			m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' is already disabled." );
			return;
		}

		if( !dbP.disableName() ) 
		{
			m_botAction.sendSmartPrivateMessage( name, "Error disabling name '"+message+"'" );
			return;
		}
		m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has been disabled." );
	}

	public void commandDisplayInfo( String name, String message ) 
	{

		DBPlayerData dbP = new DBPlayerData( m_botAction, "server", message );

		if( !dbP.isRegistered() ) 
		{
			m_botAction.sendSmartPrivateMessage( name, "The name '"+message+"' has not been registered." );
			return;
		}
		String status = "ENABLED";
		if( !dbP.isEnabled() ) status = "DISABLED";
		m_botAction.sendSmartPrivateMessage( name, "'"+message+"'  IP:"+dbP.getIP()+"  MID:"+dbP.getMID()+"  "+status );
		commandGetResetTime( name, message, false, true );
	}

	public void commandRegisterName( String name, String message, boolean p ) 
	{

		String player = m_botAction.getFuzzyPlayerName( message );
		if( message == null ) 
		{
			m_botAction.sendSmartPrivateMessage( name, "Unable to find "+message+" in the arena." );
			return;
		}

		DBPlayerData dbP = new DBPlayerData( m_botAction, "server", player );

		if( dbP.isRegistered() ) 
		{
			m_botAction.sendSmartPrivateMessage( name, "This name has already been registered." );
			return;
		}
		register = name;
		if( p )
			m_waitingAction.put( player, "register" );
		else
			m_waitingAction.put( player, "forceregister" );
		m_botAction.sendUnfilteredPrivateMessage( player, "*info" );
	}

	public void commandIPCheck( String name, String ip ) 
	{

		try 
		{
			String query = "SELECT fcUserName, fcIP, fnMID FROM tblAliasSuppression AS A, ";
			query += " tblUser AS U WHERE A.fnUserID = U.fnUserID AND fcIP LIKE '"+ip+"%'";
			ResultSet result = m_botAction.SQLQuery( "server", query );
			while( result.next () ) 
			{
				String out = result.getString( "fcUserName" ) + "  ";
				out += "IP:" + result.getString( "fcIP" ) + "  ";
				out += "MID:" + result.getString( "fnMID" );
				m_botAction.sendSmartPrivateMessage( name, out );
			}
		} 
		catch (Exception e) 
		{
			Tools.printStackTrace( e );
			m_botAction.sendSmartPrivateMessage( name, "Error doing IP check." );
		}
	}

	public void commandMIDCheck( String name, String mid ) 
	{

	    if( mid == null || mid == "" || !(Tools.isAllDigits(mid)) ) {
			m_botAction.sendSmartPrivateMessage( name, "MID must be all numeric." );	        
	        return;
	    }
	    
		try 
		{
			String query = "SELECT fcUserName, fcIP, fnMID FROM tblAliasSuppression AS A, ";
			query += " tblUser AS U WHERE A.fnUserID = U.fnUserID AND fnMID = "+mid;
			ResultSet result = m_botAction.SQLQuery( "server", query );
			while( result.next () ) 
			{
				String out = result.getString( "fcUserName" ) + "  ";
				out += "IP:" + result.getString( "fcIP" ) + "  ";
				out += "MID:" + result.getString( "fnMID" );
				m_botAction.sendSmartPrivateMessage( name, out );
			}
		} 
		catch (Exception e) 
		{
			Tools.printStackTrace( e );
			m_botAction.sendSmartPrivateMessage( name, "Error doing MID check." );
		}
	}

	public void commandDisplayHelp( String name, boolean player ) 
	{
		String help[] = 
			{
				"--------- ACCOUNT MANAGEMENT COMMANDS ------------------------------------------------",
				"!resetname <name>       - resets the name (unregisters it)",
				"!resettime <name>       - returns the time when the name will be reset",
				"!cancelreset <name>     - cancels the !reset a player has issued",
				"!enablename <name>      - enables the name so it can be used in TWD/TWL games",
				"!disablename <name>     - disables the name so it can not be used in TWD/TWL games",
				"!register <name>        - force registers that name, that player must be in the arena",
				"!registered <name>      - checks if the name is registered",
				"--------- ALIAS CHECK COMMANDS -------------------------------------------------------",
				"!info <name>            - displays the IP/MID that was used to register this name",
				"!ipcheck <IP>           - looks for matching records based on <IP>",
				"!midcheck <MID>         - looks for matching records based on <MID>",
				"!ipidcheck <IP> <MID>   - looks for matching records based on <IP> and <MID>",
				"         <IP> can be partial address - ie:  192.168.0.",
				"--------- MISC COMMANDS --------------------------------------------------------------",
				"!check <name>           - checks the IP and MID of person with <name>",
				"!go <arena>             - moves the bot"
			};
		String help2[] = 
			{
				"--------- ACCOUNT MANAGEMENT COMMANDS ------------------------------------------------",
				"!resetname              - resets your name",
				"!resettime              - returns the time when your name will be reset",
				"!cancelreset            - cancels the !resetname",
				"!register               - registers your name",
				"!registered <name>      - checks if the name is registered"
			};
        
		if( player )
			m_botAction.privateMessageSpam( name, help2 );
		else
			m_botAction.privateMessageSpam( name, help );
	}

	public void parseIP( String message ) 
	{

		String[] pieces = message.split("  ");
		String name = pieces[3].substring(10);
		String ip = pieces[0].substring(3);
		String mid = pieces[5].substring(10);

		DBPlayerData dbP = new DBPlayerData( m_botAction, "server", name );

		//If an info action wasn't set don't handle it
		if( m_waitingAction.containsKey( name ) ) {
		
			String option = (String)m_waitingAction.get( name );
			m_waitingAction.remove( name );

			//Note you can't get here if already registered, so can't match yourself.
			if( dbP.aliasMatch( ip, mid ) ) 
			{
        	
				if( option.equals("register") ) 
				{
					m_botAction.sendSmartPrivateMessage( name, "Please reset your old name(s) with !resetname and wait the 24h, and then register this name. In case of problems with reseting, feel free to ask for assistance of TW Staff with ?help." );
					return;
				} 
				else
					m_botAction.sendSmartPrivateMessage( register, "WARNING: Another account may have been registered on that connection." );
			}

			if( !dbP.register( ip, mid ) ) 
			{
				m_botAction.sendSmartPrivateMessage( register, "Unable to register name." );
				return;
			}
			m_botAction.sendSmartPrivateMessage( register, "Registration successful." );
		} else {
			String response = name + "  IP:"+ip+"  MID:"+mid;
	        m_botAction.sendSmartPrivateMessage( requester, response );
		}
	}

	public boolean resetPRegistration(int id) {
    	
    	try {
			String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
			m_botAction.SQLQuery( "server", "UPDATE tblAliasSuppression SET fdResetTime = '"+time+"' WHERE fnUserID = '" + id + "'");
    		return true;
    	} catch (Exception e) {
    		return false;
    	}
    }

    public void checkNamesToReset() {
        try {
            m_botAction.SQLQuery("server", "DELETE FROM tblAliasSuppression WHERE fdResetTime < DATE_SUB(NOW(), INTERVAL 1 DAY);");
        } catch (Exception e) {
            System.out.println("Can't check for new names to reset...");
        };
    };


	// ipbot

	public void checkIP( String name, String message ) {
    
		String target = m_botAction.getFuzzyPlayerName( message );
		if( target == null ) {
			m_botAction.sendSmartPrivateMessage( name, "Unable to find "+message+" in this arena." );
			return;
		}
    
		m_botAction.sendUnfilteredPrivateMessage( target, "*info" );
		requester = name;
	}
}
