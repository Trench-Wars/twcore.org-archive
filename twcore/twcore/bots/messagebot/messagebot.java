package twcore.bots.messagebot;

import twcore.core.*;
import twcore.core.command.CommandInterpreter;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.util.IPCMessage;
import twcore.core.util.Tools;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.net.*;
import java.io.*;

/** Bot to host "channels" that allow a player to ?message or pm
 *  everyone on the channel so that information can be spread easily.
 *
 *  @author Ikrit
 *  @version 1.8
 *
 *  Added database support of messages because I forgot SSC messaging only
 *  allows one message from a person at a time.
 *
 *  Added pubbot support so the bot can PM a player that has just logged
 *  in to tell them if they have messages.
 *
 *  Fixed possible SQL injection attacks (cough FINALLY cough).
 *
 *  Added support so the bot will sync with the website.
 *
 *  Added a news feature for the lobby thing that qan is designing. Highmod+ can add news messages that get arena'd every 90 sec's.
 *
 *  Added all new commands to !help thus making !help a 41 PM long help message.
 *
 *  Added debugging stuff...
 *
 *  Added an alerts chat type thing for in lobby.
 *
 *  Fixed help so it doesn't spam 41 lines.
 *
 *  Deleted AIM stuff because I dislike it
 *
 *  Added ability to leave messages to people instead of channels
 *
 *	Editted !help
 *
 *	Added ability to message yourself
 *
 *	Fixed !help order
 */

/** TODO:
 *
 *	Multi-line messages
 *
 */
public class messagebot extends SubspaceBot
{
	HashMap channels;
	HashMap defaultChannel;
	HashSet ops;
	HashMap news;
	ArrayList newsIDs;
	int newsID;
	CommandInterpreter m_CI;
	TimerTask messageDeleteTask, messageBotSync, newsTask, newsChatTask;
	public static final String IPCCHANNEL = "messages";
	boolean bug = false;
	boolean newsAlerts = false;

	/** Constructor, requests Message and Login events.
	 *  Also prepares bot for use.
	 */
	public messagebot(BotAction botAction)
	{
		super(botAction);
		EventRequester events = m_botAction.getEventRequester();
		events.request(EventRequester.MESSAGE);
		events.request(EventRequester.LOGGED_ON);
		channels = new HashMap();
		defaultChannel = new HashMap();
		ops = new HashSet();
		news = new HashMap();
		newsIDs = new ArrayList();
		newsID = 0;
		m_CI = new CommandInterpreter(m_botAction);
		registerCommands();
		createTasks();
		m_botAction.scheduleTaskAtFixedRate(messageDeleteTask, 30 * 60 * 1000, 30 * 60 * 1000);
		m_botAction.scheduleTaskAtFixedRate(messageBotSync, 2 * 60 * 1000, 2 * 60 * 1000);
		m_botAction.scheduleTaskAtFixedRate(newsTask, 15 * 60 * 1000, 15 * 60 * 1000);
		m_botAction.scheduleTaskAtFixedRate(newsChatTask, 15 * 60 * 1000, 15 * 60 * 1000);
	}

	/** This method handles an InterProcessEvent
	 *  @param event is the InterProcessEvent to handle.
	 */
	public void handleEvent(InterProcessEvent event)
	{
		IPCMessage ipcMessage = (IPCMessage) event.getObject();
		String message = ipcMessage.getMessage();
		String recipient = ipcMessage.getRecipient();
		String sender = ipcMessage.getSender();
		String botSender = event.getSenderName();
		checkNewMessages(message.toLowerCase());
	}

	/** Checks to see if the player has new messages.
	 *  @param Name of player to check.
	 */
	public void checkNewMessages(String name)
	{
		String query = "SELECT * FROM tblMessageSystem WHERE fcName = '"+Tools.addSlashesToString(name)+"' and fnRead = 0";
		try {
			ResultSet results = m_botAction.SQLQuery("local", query);
			boolean found = false;
			while(results.next() && !found)
			{
				int read = results.getInt("fnRead");
				if(read == 0)
				{
					m_botAction.sendSmartPrivateMessage(name, "You have new messages. PM me with !messages to receive them.");
					found = true;
				}
			}
			results.close();
		} catch(Exception e) { m_botAction.sendSmartPrivateMessage("Ikrit", e.getMessage()); Tools.printStackTrace(e); }
	}

	/** Sets up the CommandInterpreter to respond to
	 *  all of the commands.
	 *
	 */
	public void registerCommands() {
        int acceptedMessages;

        acceptedMessages = Message.PRIVATE_MESSAGE | Message.REMOTE_PRIVATE_MESSAGE;
        m_CI.registerCommand( "!create",     acceptedMessages, this, "createChannel" );
        m_CI.registerCommand( "!destroy",    acceptedMessages, this, "destroyChannel" );
        m_CI.registerCommand( "!join",       acceptedMessages, this, "joinChannel" );
        m_CI.registerCommand( "!quit",       acceptedMessages, this, "quitChannel" );
        m_CI.registerCommand( "!help",       acceptedMessages, this, "doHelp" );
        m_CI.registerCommand( "!accept",     acceptedMessages, this, "acceptPlayer" );
        m_CI.registerCommand( "!decline",    acceptedMessages, this, "declinePlayer" );
        m_CI.registerCommand( "!announce",   acceptedMessages, this, "announceToChannel" );
        m_CI.registerCommand( "!message",    acceptedMessages, this, "messageChannel" );
        m_CI.registerCommand( "!requests",   acceptedMessages, this, "listRequests");
        m_CI.registerCommand( "!ban",        acceptedMessages, this, "banPlayer");
        m_CI.registerCommand( "!unban",		 acceptedMessages, this, "unbanPlayer");
        m_CI.registerCommand( "!makeop",	 acceptedMessages, this, "makeOp");
        m_CI.registerCommand( "!deop", 	     acceptedMessages, this, "deOp");
        m_CI.registerCommand( "!owner",		 acceptedMessages, this, "sayOwner");
        m_CI.registerCommand( "!grant",		 acceptedMessages, this, "grantChannel");
        m_CI.registerCommand( "!private",	 acceptedMessages, this, "makePrivate");
        m_CI.registerCommand( "!public",	 acceptedMessages, this, "makePublic");
        m_CI.registerCommand( "!unread",	 acceptedMessages, this, "setAsNew");
        m_CI.registerCommand( "!read",		 acceptedMessages, this, "readMessage");
        m_CI.registerCommand( "!delete",	 acceptedMessages, this, "deleteMessage");
        m_CI.registerCommand( "!messages",	 acceptedMessages, this, "myMessages");
        m_CI.registerCommand( "!go",		 acceptedMessages, this, "handleGo");
        m_CI.registerCommand( "!members",	 acceptedMessages, this, "listMembers");
        m_CI.registerCommand( "!banned",	 acceptedMessages, this, "listBanned");
        m_CI.registerCommand( "!me",		 acceptedMessages, this, "myChannels");
        m_CI.registerCommand( "!die",		 acceptedMessages, this, "handleDie");
        m_CI.registerCommand( "!check",		 acceptedMessages, this, "playerLogin");
        m_CI.registerCommand( "!addnews",	 acceptedMessages, this, "addNewsItem");
        m_CI.registerCommand( "!delnews",	 acceptedMessages, this, "deleteNewsItem");
        m_CI.registerCommand( "!readnews",	 acceptedMessages, this, "readNewsItem");
        m_CI.registerCommand( "!bug",		 acceptedMessages, this, "bugMe");
        m_CI.registerCommand( "!debug",		 acceptedMessages, this, "stopBuggingMe");
        m_CI.registerCommand( "!alerts",	 acceptedMessages, this, "announceToAlerts");
        m_CI.registerCommand( "!ignore",	 acceptedMessages, this, "ignorePlayer");
        m_CI.registerCommand( "!unignore",	 acceptedMessages, this, "unignorePlayer");
        m_CI.registerCommand( "!ignored",	 acceptedMessages, this, "whoIsIgnored");
        m_CI.registerCommand( "!lmessage",	 acceptedMessages, this, "leaveMessage");
        m_CI.registerCommand( "!listnews",	 acceptedMessages, this, "listNews");
        m_CI.registerCommand( "!regall",	 acceptedMessages, this, "registerAll");

        m_CI.registerDefaultCommand( Message.REMOTE_PRIVATE_MESSAGE, this, "doNothing");

        m_botAction.ipcSubscribe(IPCCHANNEL);
    }

    /** Creates a channel
     *  @param Name of the player creating the channel.
     *  @param Name of the channel they are trying to create.
     */
    public void createChannel(String name, String message)
    {
    	if(channels.containsKey(message.toLowerCase()))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "Sorry, this channel is already owned.");
    		return;
    	}
    	if(!m_botAction.getOperatorList().isZH(name))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "Sorry, you need to be a ZH+ to create a channel.");
    		return;
    	}

    	Channel c = new Channel(name.toLowerCase(), message.toLowerCase(), m_botAction, true, false);
    	channels.put(message.toLowerCase(), c);

    	String query = "INSERT INTO tblChannel (fcChannelName, fcOwner, fnPrivate) VALUES('"+Tools.addSlashesToString(message.toLowerCase())+"', '"+Tools.addSlashesToString(name.toLowerCase())+"', 0)";
    	try {
    		m_botAction.SQLQuery("local", query);
    	} catch(SQLException sqle) { Tools.printStackTrace( sqle ); }
    }

    /** Allows a channel owner or Highmod to delete a channel.
     *  @param Name of player.
     *  @param Name of channel being deleted.
     */
    public void destroyChannel(String name, String message)
    {
    	String channel = getChannel(name, message, true).toLowerCase();
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOwner(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    	{
    		String query = "DELETE FROM tblChannel WHERE fcChannelName = '" + Tools.addSlashesToString(channel) + "'";
    		String query2 = "DELETE FROM tblChannelUser WHERE fcChannel = '" + Tools.addSlashesToString(channel) + "'";
    		try {
    			m_botAction.SQLQuery("local", query);
    			m_botAction.SQLQuery("local", query2);
    		} catch(SQLException sqle) { Tools.printStackTrace( sqle ); }
    		m_botAction.sendSmartPrivateMessage(name, "Channel deleted.");
    		c.messageChannel(name, "Channel " + channel + " deleted.");
    		channels.remove(channel);
    	}
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Puts in a request to join a channel.
     *  @param Name of player.
     *  @param Name of channel they want to join.
     */
    public void joinChannel(String name, String message)
    {
    	String channel = getChannel(name, message, true).toLowerCase();
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	c.joinRequest(name);
    }

    /** Allows a person to quit a channel.
     *  @param Name of player.
     *  @param Name of channel being quit.
     */
    public void quitChannel(String name, String message)
    {
    	String channel = getChannel(name, message, true).toLowerCase();
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOwner(name))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "You cannot leave while you are owner.");
    		return;
    	}
    	c.leaveChannel(name);
    }

    /** Accepts a player into the channel.
     *  @param Name of operator.
     *  @param Name of channel and player beinc accepted.
     */
    public void acceptPlayer(String name, String message)
    {
    	String channel = getChannel(name, message, false).toLowerCase();
    	String pieces[] = message.split(":", 2);
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOp(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
  		  	c.acceptPlayer(name, pieces[1]);
  		else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Declines a player's request to join a channel.
     *  @param Name of operator.
     *  @param Name of channel and player being rejected.
     */
    public void declinePlayer(String name, String message)
    {
    	String channel = getChannel(name, message, false).toLowerCase();
    	String pieces[] = message.split(":", 2);
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOp(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.rejectPlayer(name, pieces[1]);
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Sends a pm to all players on the channel.
     *  @param Name of operator.
     *  @param Name of channel and message to be sent.
     */
    public void announceToChannel(String name, String message)
    {
    	String channel = getChannel(name, message, false).toLowerCase();
    	String pieces[] = message.split(":", 2);
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOp(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.announceToChannel(name, pieces[1]);
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

	/** Sends ?message's to every player on a channel.
	 *  @param Name of operator.
	 *  @param Name of channel and message to be sent.
	 */
	public void messageChannel(String name, String message)
    {
    	String channel = getChannel(name, message, false).toLowerCase();
    	String pieces[] = message.split(":", 2);
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOp(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.messageChannel(name, pieces[1]);
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Lists all requests to join a channel.
     *  @param Name of operator
     *  @param Name of channel.
     */
    public void listRequests(String name, String message)
    {
    	String channel = getChannel(name, message, true).toLowerCase();
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOp(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.listRequests(name);
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Bans a player from a channel
     *  @param Name of operator
     *  @param Name of channel and player being banned.
     */
    public void banPlayer(String name, String message)
    {
    	String channel = getChannel(name, message, false).toLowerCase();
    	String pieces[] = message.split(":", 2);
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOp(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.banPlayer(name, pieces[1]);
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Removes a player's ban.
     *  @param Name of operator.
     *  @param Name of channel and player being unbanned.
     */
    public void unbanPlayer(String name, String message)
    {
    	String channel = getChannel(name, message, false).toLowerCase();
    	String pieces[] = message.split(":", 2);
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOp(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.unbanPlayer(name, pieces[1]);
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Lists all requests to join a channel.
     *  @param Name of operator
     *  @param Name of channel.
     */
    public void listBanned(String name, String message)
    {
    	String channel = getChannel(name, message, true).toLowerCase();
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOp(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.listBanned(name);
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Lists all requests to join a channel.
     *  @param Name of operator
     *  @param Name of channel.
     */
    public void listMembers(String name, String message)
    {
    	String channel = getChannel(name, message, true).toLowerCase();
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    //	if(c.isOp(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.listMembers(name);
    //	else
    //		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Makes a player a channel operator.
     *  @param Name of owner
     *  @param Name of channel and player being op'd.
     */
    public void makeOp(String name, String message)
    {
    	String channel = getChannel(name, message, false).toLowerCase();
    	String pieces[] = message.split(":", 2);
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOwner(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.makeOp(name, pieces[1]);
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Revokes a player's operator status
     *  @param Name of owner
     *  @param Name of channel and player.
     */
    public void deOp(String name, String message)
    {
    	String channel = getChannel(name, message, false).toLowerCase();
    	String pieces[] = message.split(":", 2);
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(!c.isOp(name))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That player is not an operator.");
    		return;
    	}

    	if(c.isOwner(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.deOp(name, pieces[1]);
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Grants ownership of a channel to a new player.
     *  @param Name of owner/Highmod
     *  @param Name of channel and player beign given operator.
     */
    public void grantChannel(String name, String message)
    {
    	String channel = getChannel(name, message, false).toLowerCase();
    	String pieces[] = message.split(":", 2);
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(pieces[0].toLowerCase());
    	if(c.isOwner(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.newOwner(name, pieces[1]);
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Tells who the owner of the requested channel is.
     *  @param Name of player
     *  @param Name of channel
     */
    public void sayOwner(String name, String message)
    {
    	String channel = getChannel(name, message, true).toLowerCase();
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	m_botAction.sendSmartPrivateMessage(name, "Owner: " + c.owner);
    }

    /** Makes a channel public.
     *  @param Name of operator
     *  @param Name of channel
     */
    public void makePublic(String name, String message)
    {
    	String channel = getChannel(name, message, true).toLowerCase();
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOp(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.makePublic(name);
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Makes a channel private
     *  @param Name of operator
     *  @param Name of channel.
     */
    public void makePrivate(String name, String message)
    {
    	String channel = getChannel(name, message, true).toLowerCase();
    	if(!channels.containsKey(channel))
    	{
    		m_botAction.sendSmartPrivateMessage(name, "That channel does not exist.");
    		return;
    	}

    	Channel c = (Channel)channels.get(channel);
    	if(c.isOp(name) || m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
    		c.makePrivate(name);
    	else
    		m_botAction.sendSmartPrivateMessage(name, "You do not have permission to do that on this channel.");
    }

    /** Tells the player all the channels he/she is on.
     *  @param Name of player
     *  @param does nothing
     */

    public void myChannels(String name, String message)
    {
    	String query = "SELECT * FROM tblChannelUser WHERE fcName = '"+Tools.addSlashesToString(name.toLowerCase())+"'";
    	try {
    		ResultSet results = m_botAction.SQLQuery("local", query);
    		while(results.next())
    		{
    			String channel = results.getString("fcChannel");
    			channel = channel.toLowerCase();
    			Channel c = (Channel)channels.get(channel);
    			if( c == null)
    			    return;
    			if(c.isOwner(name.toLowerCase()))
    				channel += ": Owner.";
    			else if(c.isOp(name.toLowerCase()))
    				channel += ": Operator.";
    			else
    				channel += ": Member.";
    			m_botAction.sendSmartPrivateMessage(name, channel);
    		}
    		results.close();
    	} catch(Exception e) { Tools.printStackTrace(e); }
    }


    /** Sends help messages
     *  @param Name of player.
     */
    public void doHelp(String name, String message)
    {
    	if(message.toLowerCase().startsWith("message")) {
	    	m_botAction.sendSmartPrivateMessage(name, "Messaging system commands:");
	        m_botAction.sendSmartPrivateMessage(name, "    !unread <num>                  -Sets message <num> as unread.");
	        m_botAction.sendSmartPrivateMessage(name, "    !read <num>                    -PM's you message <num>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !delete <num>                  -Deletes message <num>. !delete all works too.");
	        m_botAction.sendSmartPrivateMessage(name, "    !messages                      -PM's you all your message numbers.");
	        m_botAction.sendSmartPrivateMessage(name, "    !lmessage <name>:<message>     -Leaves <message> for <name>.");
	    } else if(message.toLowerCase().startsWith("channel")) {
	        m_botAction.sendSmartPrivateMessage(name, "    !me                            -Tells you what channels you have joined.");
	        m_botAction.sendSmartPrivateMessage(name, "    !join <channel>                -Puts in request to join <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !quit <channel>                -Removes you from <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !owner <channel>               -Tells you who owns <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !announce <channel>:<message>  -Sends everyone on <channel> a pm of <message>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !message <channel>:<message>   -Leaves a message for everyone on <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !requests <channel>            -PM's you with all the requests to join <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !banned <channel>              -Lists players banned on <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !members <channel>             -Lists all members on <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !ban <channel>:<name>          -Bans <name> from <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !unban <channel>:<name>        -Lifts <name>'s ban from <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !makeop <channel>:<name>       -Makes <name> an operator in <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !deop <channel>:<name>         -Revokes <name>'s operator status in <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !grant <channel>:<name>        -Grants ownership of <channel> to <name>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !private <channel>             -Makes <channel> a request based channel.");
	        m_botAction.sendSmartPrivateMessage(name, "    !public <channel>              -Makes <channel> open to everyone.");
	        m_botAction.sendSmartPrivateMessage(name, "    !destroy <channel>             -Destroys <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !accept <channel>:<name>       -Accepts <name>'s request to join <channel>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !decline <channel>:<name>      -Declines <name>'s request to join <channel>.");
		    if(m_botAction.getOperatorList().isZH(name))
		       	m_botAction.sendSmartPrivateMessage(name, "    !create <channel>              -Creates a channel with the name <channel>.");
	    } else if(message.toLowerCase().startsWith("news")) {
	    	m_botAction.sendSmartPrivateMessage(name, "News interface commands:");
	    	m_botAction.sendSmartPrivateMessage(name, "    !readnews <#>                  -PM's you news article #<#>.");
	    	m_botAction.sendSmartPrivateMessage(name, "    !listnews                      -PM's you all the news article numbers.");
	    } else if((m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase())) && message.toLowerCase().startsWith("smod")) {
	    	m_botAction.sendSmartPrivateMessage(name, "Smod+ commands:");
	        m_botAction.sendSmartPrivateMessage(name, "    !addnews <news>:<url>          -Adds a news article with <news> as the content and <url> for more info.");
	        m_botAction.sendSmartPrivateMessage(name, "    !delnews <#>                   -Deletes news id number <#>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !go <arena>                    -Sends messagebot to <arena>.");
	        m_botAction.sendSmartPrivateMessage(name, "    !die                           -Kills messagebot.");
        } else {
	    	String defaultHelp = "PM me with !help channel for channel system help, !help message for message system help, !help news for news system help";
	    	if(m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
	    		defaultHelp += ", !help smod for SMod command help.";
	    	else
	    		defaultHelp += ".";
        	m_botAction.sendSmartPrivateMessage(name, defaultHelp);
        }

    }

    /** Does nothing
     */
    public void doNothing(String name, String message) {}

	/** Passes a message event to the command interpreter
	 *  @param The message event being passed.
	 */
	public void handleEvent(Message event)
	{
		m_CI.handleEvent(event);
	}

	/** Sends the bot to the default arena and reloads all channels.
	 */
	public void handleEvent(LoggedOn event)
	{
		m_botAction.sendUnfilteredPublicMessage("?chat=news,superalerts");
		m_botAction.sendUnfilteredPublicMessage("?chat=news,superalerts");
		try {
			m_botAction.joinArena(m_botAction.getBotSettings().getString("Default arena"));
			m_botAction.ipcSubscribe(IPCCHANNEL);
			long before = Runtime.getRuntime().freeMemory();
			String opList[] = (m_botAction.getBotSettings().getString("Ops")).split(":");
			for(int k = 0;k < opList.length;k++)
				ops.add(opList[k].toLowerCase());

			String query = "SELECT * FROM tblChannel";
			try {
				ResultSet results = m_botAction.SQLQuery("local", query);

				while(results.next())
				{
					String channelName = results.getString("fcChannelName");
					String owner = results.getString("fcOwner");
					int pub = results.getInt("fnPrivate");
					boolean priv;
					if(pub == 1)
						priv = false;
					else
						priv = true;
					Channel c = new Channel(owner, channelName, m_botAction, priv, true);
					c.reload();
					channels.put(channelName.toLowerCase(), c);
				}
				results.close();
			} catch(Exception e) { Tools.printStackTrace( e ); }

			query = "SELECT * FROM tblBotNews ORDER BY fnID DESC";
			try {
				ResultSet results = m_botAction.SQLQuery("local", query);
				while(results.next()) {
					String name = results.getString("fcName");
					String content = results.getString("fcNews");
					String date = results.getString("fdTime");
					String url = results.getString("fcURL");
					int id = results.getInt("fnID");
					NewsArticle na = new NewsArticle(name, content, date, id, url);
					news.put(id, na);
					newsIDs.add(id);
				}
				results.close();
			} catch(SQLException e) { Tools.printStackTrace(e); }
			long after = Runtime.getRuntime().freeMemory();
			long memUsed = before - after;
			m_botAction.sendSmartPrivateMessage("ikrit", "King free before: " + before + " and after: " + after);
		} catch(Exception e) {}
		m_botAction.setMessageLimit(5);

	}

	/** Reads a message from the database.
	 *  @param Name of player reading the message.
	 *  @param Message number being read.
	 */
	public void readMessage(String name, String message)
	{
		int messageNumber = -1;
		try{
			messageNumber = Integer.parseInt(message);
		} catch(Exception e) {
//			e.printStackTrace();
			m_botAction.sendSmartPrivateMessage(name, "Invalid message number");
			return;
		}
		if(!ownsMessage(name.toLowerCase(), messageNumber)) {
			m_botAction.sendSmartPrivateMessage(name, "That is not your message!");
			return;
		}
		String query = "SELECT * FROM tblMessageSystem WHERE fcName = '"+Tools.addSlashesToString(name.toLowerCase())+"' AND fnID = " + messageNumber;
		try{
			ResultSet results = m_botAction.SQLQuery("local", query);
			if(results.next())
			{
				message = results.getString("fcMessage");
				String timestamp = results.getString("fdTimeStamp");

				m_botAction.sendSmartPrivateMessage(name, timestamp + " " + message);

				query = "UPDATE tblMessageSystem SET fnRead = 1 WHERE fcName = '"+Tools.addSlashesToString(name.toLowerCase())+"' AND fnID = " + messageNumber;
				m_botAction.SQLQuery("local", query);
			}
			else
			{
				m_botAction.sendSmartPrivateMessage(name, "Could not find that message.");
			}
			results.close();
		} catch(Exception e) { Tools.printStackTrace( e ); }
	}

	/** Marks a message's status as unread.
	 *  @param Name of player.
	 *  @param Message number being reset.
	 */
	public void setAsNew(String name, String message)
	{
		int messageNumber = -1;
		try{
			messageNumber = Integer.parseInt(message);
		} catch(Exception e) {
			m_botAction.sendSmartPrivateMessage(name, "Invalid message number");
			return;
		}
		if(!ownsMessage(name.toLowerCase(), messageNumber)) {
			m_botAction.sendSmartPrivateMessage(name, "That is not your message!");
			return;
		}
		String query = "UPDATE tblMessageSystem SET fnRead = 0 WHERE fcName = '"+Tools.addSlashesToString(name.toLowerCase())+"' AND fnID = " + messageNumber;
		try {
			m_botAction.SQLQuery("local", query);
			m_botAction.sendSmartPrivateMessage(name, "Message marked as unread.");
		} catch(SQLException e) {
			Tools.printStackTrace( e );
			m_botAction.sendSmartPrivateMessage(name, "Unable to mark as read.");
		}
	}

	/** Deletes a message from the database.
	 *  @param Name of player
	 *  @param Message being deleted.
	 */
	public void deleteMessage(String name, String message)
	{
		int messageNumber = -1;
		String query;
		if(message.equalsIgnoreCase("all")) {
			query = "DELETE FROM tblMessageSystem WHERE fcName = '"+Tools.addSlashesToString(name.toLowerCase())+"'";
		} else {
			try{
				messageNumber = Integer.parseInt(message);
			} catch(Exception e) {
				e.printStackTrace();
				m_botAction.sendSmartPrivateMessage(name, "Invalid message number");
				return;
			}
			if(!ownsMessage(name.toLowerCase(), messageNumber)) {
				m_botAction.sendSmartPrivateMessage(name, "That is not your message!");
				return;
			}
			query = "DELETE FROM tblMessageSystem WHERE fcName = '"+Tools.addSlashesToString(name.toLowerCase())+"' AND fnID = " + messageNumber;
		}
		try {
			m_botAction.SQLQuery("local", query);
			m_botAction.sendSmartPrivateMessage(name, "Message(s) deleted.");
		} catch(Exception e) {
			m_botAction.sendSmartPrivateMessage(name, "Message(s) unable to be deleted.");
			Tools.printStackTrace( e );
		}
	}

	/** PM's a player with all of his/her message numbers.
	 *  @param Name of player.
	 *  @param (does nothing).
	 */
	public void myMessages(String name, String message)
	{
		String query = "SELECT * FROM tblMessageSystem WHERE fcName = '"+Tools.addSlashesToString(name.toLowerCase())+"'";
		String messageNumbers = "";
		m_botAction.sendSmartPrivateMessage(name, "You have the following messages: ");
		try {
			ResultSet results = m_botAction.SQLQuery("local", query);
			while(results.next())
			{
				String thisMessage = "Message #" + String.valueOf(results.getInt("fnID")) + ". From: " + results.getString("fcSender")+". Status: ";
				if(results.getInt("fnRead") == 1)
					thisMessage += " Read";
				else
					thisMessage += " Unread";

				m_botAction.sendSmartPrivateMessage(name, thisMessage);
			}
			results.close();
		} catch(Exception e) {
			m_botAction.sendSmartPrivateMessage(name, "Error while reading message database.");
			Tools.printStackTrace( e );
		}
		m_botAction.sendSmartPrivateMessage(name, "PM me with !read <num> to read a message.");
	}

	/** Checks the database to make sure a player owns the
	 *  message that he's trying to do stuff with.
	 *  @param name Name of the player
	 *  @param messageNumber Number of the message he/she is
	 *         trying to do stuff to
	 */
	 public boolean ownsMessage(String name, int messageNumber)
	 {
	 	String query = "SELECT * FROM tblMessageSystem WHERE fnID = " + messageNumber;
	 	try {
	 		ResultSet results = m_botAction.SQLQuery("local", query);

	 		if(!results.next()) { results.close(); return false; }

	 		if(results.getString("fcName").toLowerCase().equals(name)) {
	 			results.close();
	 			return true;
	 		} else {
	 			results.close();
	 			return false;
	 		}
	 	} catch(Exception e) { Tools.printStackTrace(e); }
	 	return false;
	 }

	/** Retrieves the channel name out of the message.
	 *  @param name Name of player.
	 *  @param message Message sent
	 */
	 public String getChannel(String name, String message, boolean noParams) {
	 	if(noParams) {
	 		return message;
	 	} else {
		 	if(message.indexOf(":") == -1) {
	 			return "";
		 	} else {
		 		String pieces[] = message.split(":");
		 		return pieces[0];
		 	}
		}
	 }

	/** Handles a message sent by pubbots to tell a player if they have new messages
	 *  @param Name of player PM'ing
	 *  @param Name of player logging in
	 */
	 public void playerLogin(String name, String player)
	 {
	 	if(m_botAction.getOperatorList().isSysop(name))
	 		checkNewMessages(player.toLowerCase());
	 	else checkNewMessages(name.toLowerCase());
	 }

	/** Sends the bot to a new arena.
	 *  @param Name of player.
	 *  @param Arena to go to.
	 */
	 public void handleGo(String name, String message)
	 {
	 	if(m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase()))
	 		m_botAction.changeArena(message);
	 }

	/** Kills the bot
	 *  @param Name of player
	 *  @param should be blank
	 */
	 public void handleDie(String name, String message)
	 {
	 	if(m_botAction.getOperatorList().isHighmod(name) || ops.contains(name.toLowerCase())) {
	 		try {
			 	m_botAction.die();
			} catch(Exception e) { }
		}
	 }

	 /** Announces to a channel that they have recieved a new message.
	  *  @param Name of channel
	  */
	 public Set messageSentFromWebsite(String channel)
	 {
	 	Set s = null;

	 	try {
		 	Channel c = (Channel)channels.get(channel.toLowerCase());
		 	s = c.members.keySet();
		 	s.removeAll(c.banned);
		} catch(Exception e) {  Tools.printStackTrace( e ); }

		return s;
	 }

	 /** Syncs the website and MessageBot's access levels
	  *  @param Name of channel
	  *  @param Name of player
	  *  @param Access level
	  */
	 public void accessUpdateFromWebsite(String channel, String name, int level)
	 {
	 	try {
	 		Channel c = (Channel)channels.get(channel.toLowerCase());
	 		c.updateAccess(name, level);
	 	} catch(Exception e) { Tools.printStackTrace( e ); }
	 }

	/** Sets up the task that will delete messages that have expired.
	 */
	void createTasks()
	{
		messageDeleteTask = new TimerTask()
		{
			public void run()
			{
			//	String query = "DELETE FROM tblMessageSystem WHERE (fdTimeStamp < DATE_SUB(NOW(), INTERVAL 31 DAY)) AND fnRead = 0";
			//	String query2 = "DELETE FROM tblMessageSystem WHERE (fdTimeStamp < DATE_SUB(NOW(), INTERVAL 15 DAY)) AND fnRead = 1";
			//	try {
			//		m_botAction.SQLQuery("local", query);
			//		m_botAction.SQLQuery("local", query2);
			//		System.out.println("Deleting messages.");
			//	} catch(SQLException e) { Tools.printStackTrace( e ); }
			}
		};

		messageBotSync = new TimerTask()
		{
			public void run()
			{
				HashSet peopleToTell = new HashSet();

				String query = "SELECT * FROM tblMessageToBot ORDER BY fnID ASC";
				String query2 = "DELETE FROM tblMessageToBot";
				try {
					ResultSet results = m_botAction.SQLQuery("local", query);
					if(results == null) return;
					while(results.next()) {
						String event = results.getString("fcSyncData");
						String pieces[] = event.split(":");
						if(pieces.length == 2)
							peopleToTell.addAll(messageSentFromWebsite(pieces[1]));
						else
							accessUpdateFromWebsite(pieces[2], pieces[1], Integer.parseInt(pieces[3]));
					}
					m_botAction.SQLQuery("local", query2);
				} catch(Exception e) { Tools.printStackTrace( e ); }

				Iterator it = peopleToTell.iterator();
				while(it.hasNext()) {
					String player = (String)it.next();
					m_botAction.sendSmartPrivateMessage(player, "You have received a new message. PM me with !messages to read it.");
				}
			}
		};

        newsTask = new TimerTask() {
        	public void run() {
        		nextNews();
        	}
        };

        newsChatTask = new TimerTask() {
        	public void run() {
        		newsAlerts();
        	}
        };
	}

    /** Adds a news item.
     *  @param name Name of player adding.
     *  @param message Stuff...
     */
     public void addNewsItem(String name, String message) {
     	if(!m_botAction.getOperatorList().isHighmod(name) && !ops.contains(name.toLowerCase()))
     		return;
     	String contents;
     	String writer = name;
     	String url;

     	String pieces[] = message.split(":", 2);
     	if(pieces.length == 2) {
     		contents = pieces[0];
     		url = pieces[1];
     	} else {
     		contents = message;
     		url = "";
     	}
     	int id;
     	String date;
     	String query = "INSERT INTO tblBotNews (fnID, fcName, fcNews, fdTime, fcURL) VALUES (0, '"+Tools.addSlashesToString(writer)+"', ";
     	query += "'"+Tools.addSlashesToString(contents)+"', NOW(), '"+Tools.addSlashesToString(url) +"')";
     	String query2 = "SELECT fnID, fdTime FROM tblBotNews WHERE fcName = '"+Tools.addSlashesToString(writer)+"' ORDER BY fnID DESC";
     	try {
     		m_botAction.SQLQuery("local", query);
     		ResultSet results = m_botAction.SQLQuery("local", query2);
     		if(results.next()) {
	     		date = results.getString("fdTime");
	     		id = results.getInt("fnID");
	     	} else { return; }
     	} catch(Exception e) { if(bug) m_botAction.sendSmartPrivateMessage("ikrit <er>", "Error while adding news item :/."); return; }
     	NewsArticle na = new NewsArticle(writer, contents, date, id, url);
     	news.put(id, na);
     	newsIDs.add(id);
     	m_botAction.sendSmartPrivateMessage(name, "News item added.");
     }

    /** Deletes a news item
     *  @param name Name of player
     *  @param id News id
     */
     public void deleteNewsItem(String name, String id2) {
     	if(!m_botAction.getOperatorList().isHighmod(name) && !ops.contains(name.toLowerCase()))
     		return;
     	int id = 9283749;
     	try {
     		id = Integer.parseInt(id2);
     	} catch(Exception e) { m_botAction.sendSmartPrivateMessage(name, "Someone needs to go back to 1st grade to learn what a number is."); }
     	if(id == 9283749) return;

     	if(news.remove(id) != null) {
     		String query = "DELETE FROM tblBotNews WHERE fnID = " + id;
     		try {
     			m_botAction.SQLQuery("local", query);
     		} catch(Exception e) { m_botAction.sendSmartPrivateMessage(name, "Delete failed."); return; }
     		m_botAction.sendSmartPrivateMessage(name, "News article deleted.");
     		newsIDs.remove(newsIDs.indexOf(id));
     	} else {
     		m_botAction.sendSmartPrivateMessage(name, "Invalid news id.");
     	}
     }

    /** PM's a player with a news article.
     *  @param name Name of player
     *  @param id ID of article.
     */
     public void readNewsItem(String name, String id2) {
     	int id = 9283749;
     	try {
     		id = Integer.parseInt(id2);
     	} catch(Exception e) { m_botAction.sendSmartPrivateMessage(name, "Someone needs to go back to 1st grade to learn what a number is."); }
     	if(id == 9283749) return;

     	NewsArticle na = (NewsArticle)news.get(id);
     	if(na == null) {
     		m_botAction.sendSmartPrivateMessage(name, "Invalid news id.");
     		return;
     	}

     	m_botAction.sendSmartPrivateMessage(name, na.toString());
     	if(!na.url.equals(""))
     		m_botAction.sendSmartPrivateMessage(name, "For more information, visit: " + na.url);
     }

    /** Gets next news article in queue.
     */
     public void nextNews() {
     	if(bug) {
     		m_botAction.sendSmartPrivateMessage("ikrit <er>", "I should have just sent a news thing... I have " + newsIDs.size() + " news articles.");
     	}
     	if(newsID >= newsIDs.size()) newsID = 0;
     	if(newsIDs.isEmpty()) return;

     	NewsArticle na = (NewsArticle)news.get(newsIDs.get(newsID));
     	m_botAction.sendChatMessage(na.toString());
     	if(!na.url.equals(""))
     		m_botAction.sendChatMessage("For more information, click on this link: " + na.url);

     	newsID++;


     }

     public void listNews(String name, String blank) {
     	String message = "";

     	for(int k = 0;k < newsIDs.size();k++) {
     		message += (Integer)newsIDs.get(k) + ", ";
     	}
     	m_botAction.sendSmartPrivateMessage(name, "News IDs: ");
     	m_botAction.sendSmartPrivateMessage(name, message);
     }

    /** Alternates the arena message between news and superalerts.
     */
     public void newsAlerts() {
  		 m_botAction.sendArenaMessage("To receive news reports, join ?chat=news. -MessageBot");
  		 newsAlerts = !newsAlerts;
    }

    /** Bugs Ikrit
     */
     public void bugMe(String name, String bleh) {
     	if(name.toLowerCase().startsWith("ikrit"))
     		bug = true;
     }

    /** Stops bugging Ikrit
     */
     public void stopBuggingMe(String name, String bleh) {
     	if(name.toLowerCase().startsWith("ikrit"))
     		bug = false;
     }

    /** Sends a message to ?chat=superalerts
     */
     public void announceToAlerts(String name, String details) {
    	if(!m_botAction.getOperatorList().isSysop(name)) {
    		return;
    	}

    	m_botAction.sendChatMessage(2, details);
     }

     public void ignorePlayer(String name, String player) {
     	try {
     		if(!isIgnored(name, player)) {
	     		m_botAction.SQLQuery("local", "INSERT INTO tblMessageBotIgnore (fcIgnorer, fcIgnoree) VALUES('"+Tools.addSlashesToString(name)+"', '"+Tools.addSlashesToString(player)+"');");
	     		m_botAction.sendSmartPrivateMessage(name, player + " ignored.");
	     	} else {
	     		m_botAction.sendSmartPrivateMessage(name, player + " is already ignored.");
	     	}
     	} catch(Exception e) {}
     }

     public void unignorePlayer(String name, String player) {
     	try {
     		if(isIgnored(name, player)) {
     			m_botAction.SQLQuery("local", "DELETE FROM tblMessageBotIgnore WHERE fcIgnorer = '"+Tools.addSlashesToString(name)+"' AND fcIgnoree = '"+Tools.addSlashesToString(player)+"';");
     			m_botAction.sendSmartPrivateMessage(name, player + " unignored.");
     		} else {
     			m_botAction.sendSmartPrivateMessage(name, player + " is not currently ignored.");
     		}
     	} catch(Exception e) {}
     }

     public void whoIsIgnored(String name, String blank) {
     	try {
     		ResultSet results = m_botAction.SQLQuery("local", "SELECT * FROM tblMessageBotIgnore WHERE fcIgnorer = '"+Tools.addSlashesToString(name)+"'");
     		String ignored = "";
     		while(results.next()) {
     			ignored += results.getString("fcIgnoree");
     			if(ignored.length() > 150) {
     				m_botAction.sendSmartPrivateMessage(name, ignored);
     				ignored = "";
     			} else {
     				ignored += ", ";
     			}
     		}
     		m_botAction.sendSmartPrivateMessage(name, ignored.substring(0, ignored.length() - 2));
     	} catch(Exception e) {}
     }

     public boolean isIgnored(String name, String player) {
     	try {
     		ResultSet results = m_botAction.SQLQuery("local", "SELECT * FROM tblMessageBotIgnore WHERE fcIgnorer = '"+Tools.addSlashesToString(name)+"' AND fcIgnoree = '"+Tools.addSlashesToString(player)+"'");
     		if(results.next()) {
     			return true;
     		} else {
     			return false;
     		}
     	} catch(Exception e) {}
     	return false;
     }

     public void leaveMessage(String name, String message) {
     	if(message.indexOf(":") == -1) {
     		m_botAction.sendSmartPrivateMessage(name, "Correct usage: !lmessage <name>:<message>");
     		return;
     	}
     	String pieces[] = message.split(":", 2);
     	String player = pieces[0];
     	message = pieces[1];
     	try {
     		String query1 = "SELECT count(*) AS msgs FROM tblMessageSystem WHERE fcSender = '"+Tools.addSlashesToString(name)+"' AND fdTimeStamp > SUBDATE(NOW(), INTERVAL 7 DAY)";
     		String query2 = "SELECT count(*) AS msgs FROM tblMessageSystem WHERE fcName = '"+Tools.addSlashesToString(player)+"' AND fcSender = '"+Tools.addSlashesToString(name)+"' AND fdTimeStamp > SUBDATE(NOW(), INTERVAL 1 DAY)";
     		String query3 = "SELECT count(*) AS msgs FROM tblMessageSystem WHERE fcName = '"+Tools.addSlashesToString(player)+"'";
     		ResultSet results = m_botAction.SQLQuery("local", query1);
     		int msgsSent = 0;
     		if(results.next()) {
     			msgsSent = results.getInt("msgs");
     		}
     		int plrMsgsRcvdFrmName = 0;
     		results = m_botAction.SQLQuery("local", query2);
     		if(results.next()) {
     			plrMsgsRcvdFrmName = results.getInt("msgs");
     		}
     		int plrMsgsRcvd = 0;
     		results = m_botAction.SQLQuery("local", query3);
     		if(results.next()) {
     			plrMsgsRcvd = results.getInt("msgs");
     		}
     		if(msgsSent > 100) {
     			m_botAction.sendSmartPrivateMessage(name, "Sorry, you have reached your weekly quota of 100 messages. Please wait until some of your messages reset their stats before trying to send more.");
     			return;
     		} else if(plrMsgsRcvdFrmName > 3) {
     			m_botAction.sendSmartPrivateMessage(name, "Sorry, you have reached your daily limit on messages to this player. Please wait before sending more messages.");
     			return;
     		} else if(plrMsgsRcvd > 24) {
     			m_botAction.sendSmartPrivateMessage(name, "Sorry, the player's inbox is currently full. Please try to message him/her later.");
     			return;
     		} else if(isIgnored(player, name)) {
     			return;
     		} else {
     			m_botAction.SQLQuery("local", "INSERT INTO tblMessageSystem (fnID, fcName, fcMessage, fcSender, fnRead, fdTimeStamp) VALUES(0, '"+Tools.addSlashesToString(player)+"', '"+Tools.addSlashesToString(message)+"', '"+Tools.addSlashesToString(name)+"', 0, NOW())");
     			m_botAction.sendSmartPrivateMessage(name, "Message sent.");
     		}
     	} catch(Exception e) {}
     }
     
     public void registerAll(String name, String message) {
     	try {
    		String pieces[] = message.split(":",2);
    		URL site = new URL(pieces[1]);
    		HashSet nameList = new HashSet();
    		if(site.getFile().toLowerCase().endsWith("txt"))
    		{
    			URLConnection file = site.openConnection();
    			file.connect();
    			BufferedReader getnames = new BufferedReader(new InputStreamReader(file.getInputStream()));
    			String nextName;
    			while((nextName = getnames.readLine()) != null)
    				nameList.add(nextName);
    			file.getInputStream().close();
    			Iterator it = nameList.iterator();
    			while(it.hasNext()) {
    				String addName = (String)it.next();
					ResultSet results = m_botAction.SQLQuery("local", "SELECT * FROM tblChannelUser WHERE fcName = '"
						+ Tools.addSlashesToString(addName)+"' AND fcChannel = '"
						+ Tools.addSlashesToString(pieces[0])+"'");
					if(!results.next()) {
						m_botAction.SQLQuery("local", "INSERT INTO tblChannelUser (fcName, fcChannel, fnLevel) VALUES ('"
							+ Tools.addSlashesToString(addName)+"', '"+Tools.addSlashesToString(pieces[0])+"', 1)");
					}
    			}
    		}
    	} catch(IOException e) {e.printStackTrace(System.out);}
     }
}

class Channel
{
	String owner;
	String channelName;
	BotAction m_bA;
	HashMap members;
	HashSet banned;
	boolean isOpen;

	/** Constructs the new channel.
	 */
	public Channel(String creator, String cName, BotAction botAction, boolean pub, boolean auto)
	{
		m_bA = botAction;
		owner = creator;
		channelName = cName.toLowerCase();
		isOpen = pub;
		members = new HashMap();
		banned = new HashSet();
		members.put(owner.toLowerCase(), new Integer(3));
		updateSQL(owner.toLowerCase(), 3);

		if(!auto)
			m_bA.sendSmartPrivateMessage(owner, "Channel created.");
	}

	/** Returns wether the person is owner or not.
	 *  @param Name of player being checked.
	 */
	public boolean isOwner(String name)
	{
		if(!members.containsKey(name.toLowerCase()))
			return false;

		int level = ((Integer)members.get(name.toLowerCase())).intValue();
		if(level == 3) return true;
		else return false;
	}

	/** Returns whether the person is an operator or not.
	 *  @param Name of player being checked.
	 */
	public boolean isOp(String name)
	{
		if(!members.containsKey(name.toLowerCase()))
			return false;

		int level = ((Integer)members.get(name.toLowerCase())).intValue();
		if(level >= 2) return true;
		else return false;
	}

	/** Returns wheter the channel is public or private.
	 *  @return
	 */
	public boolean isOpen()
	{
		return isOpen;
	}

	/** Gives channel ownership away
	 *  @param Name of owner.
	 *  @param Name of new owner.
	 */
	public void newOwner(String name, String player)
	{
		if(members.containsKey(player.toLowerCase()))
		{
			updateSQL(owner.toLowerCase(), 1);
			updateSQL(player.toLowerCase(), 3);
			try {
				m_bA.SQLQuery("local", "UPDATE tblChannel SET fcOwner = '"+Tools.addSlashesToString(player)+"' WHERE fcChannelName = '" + Tools.addSlashesToString(channelName.toLowerCase()) + "'");
			} catch(Exception e) { Tools.printStackTrace( e ); }
			owner = player;
			m_bA.sendSmartPrivateMessage(player, "I have just left you an important message. PM me with !messages receive it.");
			leaveMessage(name, player, "You have been made the owner of " + channelName + " channel.");
			m_bA.sendSmartPrivateMessage(name, player + " has been granted ownership of " + channelName);
		}
		else
			m_bA.sendSmartPrivateMessage(name, "This player is not in the channel.");
	}

	/** Adds an operator.
	 *  @param Name of owner.
	 *  @param Name of new operator.
	 */
	public void makeOp(String name, String player)
	{
		if(isOwner(player)) {
				m_bA.sendSmartPrivateMessage(name, "You can't take away your owner access!");
				return;
		}
		if(members.containsKey(player.toLowerCase()))
		{
			updateSQL(player.toLowerCase(), 2);
			m_bA.sendSmartPrivateMessage(player, "I have just left you an important message. PM me with !messages receive it.");
			leaveMessage(name, player, "You have been made an operator in " + channelName + " channel.");
			m_bA.sendSmartPrivateMessage(name, player + " has been granted op powers in " + channelName);
		}
		else
			m_bA.sendSmartPrivateMessage(name, "This player is not in the channel.");
	}

	/** Removes an operator.
	 *  @param Name of owner.
	 *  @param Name of op being deleted.
	 */
	public void deOp(String name, String player)
	{
		if(members.containsKey(player.toLowerCase()))
		{
			if(isOwner(player)) {
				m_bA.sendSmartPrivateMessage(name, "You can't take away your owner access!");
				return;
			}
			updateSQL(player.toLowerCase(), 1);
			m_bA.sendSmartPrivateMessage(player, "I have just left you an important message. PM me with !messages receive it.");
			leaveMessage(name, player, "Your operator priveleges in " + channelName + " channel have been revoked.");
			m_bA.sendSmartPrivateMessage(name, player + "'s op powers in " + channelName + " have been revoked.");
		}
		else
			m_bA.sendSmartPrivateMessage(name, "This player is not in the channel.");
	}

	/** Makes the channel private.
	 *  @param Name of owner.
	 */
	public void makePrivate(String name)
	{
		try {
			m_bA.SQLQuery("local", "UPDATE tblChannel SET fnPrivate = 1 WHERE fcChannelName = '" + Tools.addSlashesToString(channelName.toLowerCase()) + "'");
		} catch(Exception e) { Tools.printStackTrace( e ); }
		m_bA.sendSmartPrivateMessage(name, "Now private channel.");
		isOpen = false;
	}

	/** Makes the channel public.
	 *  @param Name of owner.
	 */
	public void makePublic(String name)
	{
		try {
			m_bA.SQLQuery("local", "UPDATE tblChannel SET fnPrivate = 0 WHERE fcChannelName = '" + Tools.addSlashesToString(channelName.toLowerCase()) + "'");
		} catch(Exception e) { Tools.printStackTrace( e ); }
		m_bA.sendSmartPrivateMessage(name, "Now public channel.");
		isOpen = true;
	}

	/** Sends a ?message to everyone on channel and pm's the players that
	 *  are online to tell them they got a message.
	 *  @param Name of operator.
	 *  @param Message being sent.
	 */
	public void messageChannel(String name, String message)
	{
		Iterator it = members.keySet().iterator();

		while(it.hasNext())
		{
			String player = (String)it.next();
			int level = ((Integer)members.get(player.toLowerCase())).intValue();
			if(level > 0)
			{
				leaveMessage(name, player, message);
				m_bA.sendSmartPrivateMessage(player, "I have just left you an important message. PM me with !messages receive it.");
			}
		}
	}

	/** PM's everyone on channel.
	 *  @param Name of operator.
	 *  @param Message being sent.
	 */
	public void announceToChannel(String name, String message)
	{
		Iterator it = members.keySet().iterator();

		while(it.hasNext())
		{
			String player = (String)it.next();
			int level = ((Integer)members.get(player.toLowerCase())).intValue();
			if(level > 0)
			{
				m_bA.sendSmartPrivateMessage(player, channelName + ": " + name + "> " + message);
			}
		}
	}

	/** Puts in request to join channel.
	 *  @param Name of player wanting to join.
	 */
	public void joinRequest(String name)
	{
		if(members.containsKey(name.toLowerCase()))
		{
			m_bA.sendSmartPrivateMessage(name, "You are already on this channel.");
			return;
		}
		if(banned.contains(name.toLowerCase()))
		{
			m_bA.sendSmartPrivateMessage(name, "You have been banned from this channel.");
			return;
		}
		if(isOpen)
		{
			updateSQL(name.toLowerCase(), 1);
			m_bA.sendSmartPrivateMessage(name, "You have been accepted to " + channelName + " announcement channel.");
		}
		else
		{
			updateSQL(name.toLowerCase(), 0);
			m_bA.sendSmartPrivateMessage(name, "You have been placed into the channel request list. The channel owner will make the decision.");
		}
	}

	/** Removes a player from the channel
	 *  @param Name of player leaving.
	 */
	public void leaveChannel(String name)
	{
		if(members.containsKey(name.toLowerCase()))
		{
			int level = ((Integer)members.get(name.toLowerCase())).intValue();
			if(level < 0)
			{
				m_bA.sendSmartPrivateMessage(name, "You are not on this channel.");
				return;
			}
			if(isOwner(name)) {
				m_bA.sendSmartPrivateMessage(name, "You have to make a new owner before you leave.");
				return;
			}
			updateSQL(name.toLowerCase(), -5);
			m_bA.sendSmartPrivateMessage(name, "You have been removed from the channel.");
		}
		else
			m_bA.sendSmartPrivateMessage(name, "You are not on this channel.");
	}

	/** Lists all requests to join channel.
	 *  @param Name of operator.
	 */
	public void listRequests(String name)
	{
		Iterator it = members.keySet().iterator();

		m_bA.sendSmartPrivateMessage(name, "People requesting to join: ");

		while(it.hasNext())
		{
			String p = (String)it.next();
			int level = ((Integer)members.get(p)).intValue();
			if(level == 0)
				m_bA.sendSmartPrivateMessage(name, p);
		}
	}

	/** Accepts a player onto channel.
	 *  @param Name of operator.
	 *  @param Name of player being accepted.
	 */
	public void acceptPlayer(String name, String player)
	{
		if(members.containsKey(player.toLowerCase()))
		{
			updateSQL(player.toLowerCase(), new Integer(1));
			m_bA.sendSmartPrivateMessage(player, "I have just left you an important message. PM me with !messages receive it.");
			leaveMessage(name, player, "You have been accepted into " + channelName + " channel.");
			m_bA.sendSmartPrivateMessage(name, player + " accepted.");
		}
		else
			m_bA.sendSmartPrivateMessage(name, "This player has not requested to join.");
	}

	/** Rejects a player's request.
	 *  @param Name of operator.
	 *  @param Name of player being rejected.
	 */
	public void rejectPlayer(String name, String player)
	{
		if(members.containsKey(player.toLowerCase()))
		{
			updateSQL(player, -5);
			m_bA.sendSmartPrivateMessage(player, "I have just left you an important message. PM me with !messages receive it.");
			leaveMessage(name, player, "You have been rejected from " + channelName + " channel.");
			m_bA.sendSmartPrivateMessage(name, player + " rejected.");
		}
		else
			m_bA.sendSmartPrivateMessage(name, "This player is not on the channel.");
	}

	/** Bans a player from the channel.
	 *  @param Name of operator
	 *  @param Name of player getting banned.
	 */
	public void banPlayer(String name, String player)
	{
		if(isOwner(player)) {
			m_bA.sendSmartPrivateMessage(name, "You cannot ban the owner!");
			return;
		}
		if(banned.contains(player.toLowerCase()))
		{
			m_bA.sendSmartPrivateMessage(name, "That player is already banned.");
			return;
		}
		else
		{
			banned.add(player.toLowerCase());
			m_bA.sendSmartPrivateMessage(name, player + " has been banned.");
		}
		if(members.containsKey(player.toLowerCase()))
		{
			int level = ((Integer)members.get(player.toLowerCase())).intValue();
			level *= -1;
			updateSQL(player, level);
		}
	}

	/** Unbans a player.
	 *  @param Name of operator.
	 *  @param Name of player being unbanned.
	 */
	public void unbanPlayer(String name, String player)
	{
		if(banned.contains(player.toLowerCase()))
		{
			banned.remove(player.toLowerCase());
			m_bA.sendSmartPrivateMessage(name, player + " has been unbanned.");
		}
		else
		{
			m_bA.sendSmartPrivateMessage(name, "That player is not banned.");
			return;
		}
		if(members.containsKey(player.toLowerCase()))
		{
			int level = ((Integer)members.get(player.toLowerCase())).intValue();
			level *= -1;
			updateSQL(player, level);
		}
	}

	/** Updates the database when a player's level of access changes.
	 *  @param Player being changed.
	 *  @param New level of access.
	 */
	public void updateSQL(String player, int level)
	{
		String query = "SELECT * FROM tblChannelUser WHERE fcName = '" + Tools.addSlashesToString(player.toLowerCase())+"' AND fcChannel = '"+Tools.addSlashesToString(channelName)+"'";


		try {
			ResultSet results = m_bA.SQLQuery("local", query);
			if(results.next())
			{
				if(level != -5) {
					query = "UPDATE tblChannelUser SET fnLevel = " + level + " WHERE fcName = '" + Tools.addSlashesToString(player.toLowerCase()) + "' AND fcChannel = '" + Tools.addSlashesToString(channelName) +"'";
					members.put(player.toLowerCase(), new Integer(level));
				} else {
					query = "DELETE FROM tblChannelUser WHERE fcName = '" + Tools.addSlashesToString(player.toLowerCase())+"'";
					members.remove(player.toLowerCase());
				}
				m_bA.SQLQuery("local", query);
			}
			else
			{
				query = "INSERT INTO tblChannelUser (fcChannel, fcName, fnLevel) VALUES ('" + Tools.addSlashesToString(channelName) + "', '" + Tools.addSlashesToString(player.toLowerCase()) + "', " + level + ")";
				members.put(player.toLowerCase(), new Integer(level));
				m_bA.SQLQuery("local", query);
			}
			results.close();
		} catch(SQLException sqle) { Tools.printStackTrace( sqle ); }
		  catch(NullPointerException npe) { System.out.println("Silly debugging...."); }
	}

	/** Leaves a message from for a player in the database.
	 *  @param Name of player leaving the message.
	 *  @param Name of player recieving the message.
	 *  @param Message being left.
	 */
	public void leaveMessage(String name, String player, String message)
	{
		String query = "INSERT INTO tblMessageSystem (fnID, fcName, fcMessage, fcSender, fnRead, fdTimeStamp) VALUES (0, '"+Tools.addSlashesToString(player.toLowerCase())+"', '"+Tools.addSlashesToString(name) + ": " + Tools.addSlashesToString(message)+"', '"+Tools.addSlashesToString(channelName)+"', 0, NOW())";
		try {
			m_bA.SQLQuery("local", query);
		} catch(SQLException sqle) { Tools.printStackTrace( sqle ); }
	}

	/** Reload is called when the bot respawns. Allows channel to resume from where it was before bot went offline.
	 */
	public void reload()
	{
		String query = "SELECT * FROM tblChannelUser WHERE fcChannel = '" + Tools.addSlashesToString(channelName.toLowerCase())+"'";

		try {
			ResultSet results = m_bA.SQLQuery("local", query);
			while(results.next())
			{
				String name = results.getString("fcName");
				int level = results.getInt("fnLevel");
				members.put(name.toLowerCase(), new Integer(level));
				if(level < 0)
					banned.add(name.toLowerCase());
			}
			results.close();
		} catch(Exception e) { Tools.printStackTrace( e ); }
	}

	/** Lists all players on channel.
	 *  @param Name of player.
	 */
	public void listMembers(String name)
	{
		Iterator it = members.keySet().iterator();
		String message = "";
		m_bA.sendSmartPrivateMessage(name, "List of players on " + channelName + ": ");
		for(int k = 0;it.hasNext();)
		{
			String pName = (String)it.next();

			int level = (Integer)members.get(pName.toLowerCase());

			if(isOwner(pName))
				message += pName + " (Owner), ";
			else if(isOp(pName))
				message += pName + " (Op), ";
			else if(level > 0)
				message += pName + ", ";
			k++;
			if(k % 10 == 0 || !it.hasNext())
			{
				m_bA.sendSmartPrivateMessage(name, message.substring(0, message.length() - 2));
				message = "";
			}
		}
	}

	/** Lists all players banned from channel.
	 *  @param Name of player.
	 */
	public void listBanned(String name)
	{
		Iterator it = banned.iterator();
		String message = "";
		m_bA.sendSmartPrivateMessage(name, "List of players banned from " + channelName + ": ");
		for(int k = 0;it.hasNext();)
		{
			String pName = (String)it.next();
			if(k % 10 != 0)
				message += ", ";

			message += pName;
			k++;
			if(k % 10 == 0 || !it.hasNext())
			{
				m_bA.sendSmartPrivateMessage(name, message);
				message = "";
			}
		}
	}

	/** Updates access after website refresh
	 *  @param Name of player
	 *  @param New access level
	 */
	 public void updateAccess(String name, int level) {
	 	members.put(name.toLowerCase(), new Integer(level));
	 	if(level < 0) {
	 		banned.add(name.toLowerCase());
	 	} else if(banned.contains(name.toLowerCase())) {
	 		banned.remove(name.toLowerCase());
	 	}
	 }

}

class NewsArticle
{
	String writer;
	String contents;
	String d;
	int id;
	String url;

	public NewsArticle(String writer, String contents, String d, int id, String url)
	{
		this.writer = writer;
		this.contents = contents;
		this.d = d;
		this.id = id;
		this.url = url;
	}

	public String toString()
	{
		String news = "Article #" + id + ": " + contents + " -" + writer;
		return news;
	}
}