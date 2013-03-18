package twcore.bots.socialmedia;

import java.util.HashMap;
import java.util.Iterator;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.ArenaList;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.util.Tools;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;
import facebook4j.Facebook;
import facebook4j.FacebookException;
import facebook4j.FacebookFactory;

/*
 * SSCU Trench Wars Social Media Bot(s)
 * 
 * Twitter - The core function of this bot is to handle the Social Media tools of Trench Wars.
 * The below code is a function incorporated with Twitter4J. This allows TWCore to communicate with the Twitter API.
 * We can have functions such as posting updates on certain events.
 * 
 * Facebook - The core function of this bot is to handle the Social Media tools of Trench Wars.
 * The difference between this and Twitter, is Facebook is not meant to handle the instant events such as events being hosted.
 * We don't clog up the feed of people who have liked our page, and push them away.
 * This purpose is to log important events that we can broadcast to our players on FB.
 * 
 * TODO:
 * We need to get a list of what should be logged for what in each bot.
 * 
 * NOTE:
 * This is two bots in one!
 * 
 * 
 * @author Dezmond
 */

public class socialmedia extends SubspaceBot {
    //The mothership of Social Media

    private BotAction ba;

    //Stores Staff Access Levels
    private OperatorList oplist;

    //Twitter Config
    twitter4j.conf.ConfigurationBuilder twitterConfig; //builds the authentication 
    TwitterFactory twitterFactory; //initiates the Twitter authentication 
    Twitter twitter; //what we will reference through this program to post etc
    String twitterConsumer; //authentication
    String twitterConsSecret; //authentication
    String twitterAccessToken; //authentication
    String twitterAccessTokenSecret; //authentication

    //Facebook Config
    facebook4j.conf.ConfigurationBuilder facebookConfig; //builds the authentication
    FacebookFactory facebookFactory; //initiates the Facebook authentication
    Facebook facebook; //what we will reference through this program to post etc
    String facebookAppID; //authentication
    String facebookAppSecret; //authentication
    String facebookAccessToken; //authentication
    String facebookPermissions; //authentication

    //Bots
    String facebookBot = "TW-FacebookBot";
    String twitterBot = "TW-TwitterBot";

    HashMap<String, String> mediaops = new HashMap<String, String>();

    BotSettings cfg;

    public socialmedia(BotAction botAction) {
        super(botAction);

        ba = botAction;
        cfg = ba.getBotSettings();
        oplist = m_botAction.getOperatorList();

        //Facebook
        facebookAppID = cfg.getString("FBAppID");
        facebookAppSecret = cfg.getString("FBAppSecret");
        facebookAccessToken = cfg.getString("FBAuthAccessToken");
        facebookPermissions = cfg.getString("FBAuthPermissions");

        facebookConfig = new facebook4j.conf.ConfigurationBuilder();
        facebookConfig.setDebugEnabled(true);
        facebookConfig.setOAuthAppId(facebookAppID);
        facebookConfig.setOAuthAppSecret(facebookAppSecret);
        facebookConfig.setOAuthAccessToken(facebookAccessToken);
        facebookConfig.setOAuthPermissions(facebookPermissions);
        facebookFactory = new FacebookFactory(facebookConfig.build());
        facebook = facebookFactory.getInstance();

        //Twitter
        twitterConsumer = cfg.getString("TOAuthConsumerKey");
        twitterConsSecret = cfg.getString("TOAuthConsumerSecret");
        twitterAccessToken = cfg.getString("TOAuthAccessToken");
        twitterAccessTokenSecret = cfg.getString("TOAuthAccessTokenSecret");

        twitterConfig = new ConfigurationBuilder();
        twitterConfig.setDebugEnabled(true);
        twitterConfig.setOAuthConsumerKey(twitterConsumer);
        twitterConfig.setOAuthConsumerSecret(twitterConsSecret);
        twitterConfig.setOAuthAccessToken(twitterAccessToken);
        twitterConfig.setOAuthAccessTokenSecret(twitterAccessTokenSecret);
        twitterFactory = new TwitterFactory(twitterConfig.build());
        twitter = twitterFactory.getInstance();

        //Instantiate your EventRequester
        EventRequester events = ba.getEventRequester();
        //Request PlayerEntered events
        events.request(EventRequester.PLAYER_ENTERED);
        //Request chat message events
        events.request(EventRequester.MESSAGE);
        events.request(EventRequester.LOGGED_ON);
        events.request(EventRequester.ARENA_JOINED);
        events.request(EventRequester.ARENA_LIST);

    }

    public void handleEvent(LoggedOn event) {
        //Retrieve information from .cfg - in this case, the arena name to join to.
        //Get the initial arena from config and enter it
        if (ba.getBotName().equals(twitterBot)) {
            ba.joinArena(cfg.getString("InitialArena"));
        } else {
            ba.joinArena(cfg.getString("FBInitialArena"));
        }
        ba.sendUnfilteredPublicMessage("?chat=media");
    }

    public void handleEvent(ArenaList event) {

    }

    public void handleEvent(ArenaJoined event) {
        ba.setFreq(ba.getPlayerID(ba.getBotName()), 9751);
        if (ba.getBotName().equals(twitterBot)) {
            ba.sendChatMessage("My OAuth Credentials were accepted. Monitoring Trench Wars events...");
        } else {
            ba.sendChatMessage("My OAuth Credentials were accepted. Monitoring important Trench Wars events...");
        }

    }

    public void handleEvent(PlayerEntered event) {
        //We've just had a benchmarks in players for pub. Lets update Twitter!!!
        String arena = ba.getArenaName();
        if (ba.getBotName().equals(twitterBot)) {
            if (ba.getPlayingPlayers().size() > 50 && Tools.isAllDigits(arena)) {
                try {
                    twitter.updateStatus("We have just went passed the 50 player mark in public (non spectating players)! Come join in the basing action in ?go "
                            + ba.getArenaName() + " -" + ba.getBotName());
                } catch (TwitterException e) {
                    // TODO Auto-generated catch block
                    Tools.printStackTrace(e);
                }
            }

        }
    }

    public void handleEvent(Message event) {
        String name = event.getMessager();
        if (name == null || name.length() < 1)
            name = m_botAction.getPlayerName(event.getPlayerID());
        String msg = event.getMessage().toLowerCase();

        if (event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE || event.getMessageType() == Message.CHAT_MESSAGE) {
            if (msg.equalsIgnoreCase("!help")) {
                cmd_help(name, msg);
            } else if (msg.equalsIgnoreCase("!about")) {
                if (ba.getBotName().equals(twitterBot)) {
                    m_botAction.sendSmartPrivateMessage(name, "Hello there! My name is TwitterBot. I'm the sister of FacebookBot. My purpose is to track important events "
                            + "and post them on our Twitter page! Want to see what I post? Why don't you follow me on http://www.twitter.com/SSTrenchWars. "
                            + "Oh and not to mention, Dezmond owns me. Safe to say I won't be going anywhere soon...");
                } else {
                    m_botAction.sendSmartPrivateMessage(name, "Hey! My name is FacebookBot. If you didn't know, TwitterBot is my sister! My reason for being here "
                            + "is to post important events and status updates to our Facebook page for SSCU Trench Wars. Interested in liking my page? "
                            + "You can visit it at http://facebook.com/TWSubspace. Unfortunately my owner doesn't let me go -ANYWHERE-, so I'll be here to post frequently!");
                }
            }
            if (event.getMessageType() == Message.CHAT_MESSAGE) {
                if (oplist.isSmod(name)) {
                    if (msg.startsWith("!go ")) {
                        String go = msg.substring(4);
                        ba.changeArena(go);
                        ba.sendChatMessage("Moving services to " + go);
                    } else if (msg.startsWith("!addop ")) {
                        String opname = msg.substring(7);
                        addOp(name, opname);
                    } else if (msg.startsWith("!deop ")) {
                        String opname = msg.substring(6);
                        deOp(name, opname);
                    } else if (msg.equalsIgnoreCase("!listops")) {
                        listOp();
                    } else if (msg.equalsIgnoreCase("!die")) {
                        m_botAction.sendChatMessage(name + " killed me!");
                        ba.die();
                    }
                }

                        if (mediaops.containsKey(name.toLowerCase())) {

                            if (msg.startsWith("!tpost ") && ba.getBotName().equals(twitterBot)) {
                                String status = msg.substring(7);
                                try {
                                    twitter.updateStatus(status);
                                    m_botAction.sendChatMessage("Tweeted to SSTrenchWars!");
                                } catch (TwitterException e) {
                                    // TODO Auto-generated catch block
                                    Tools.printStackTrace(e);
                                }
                            } else if (msg.startsWith("!fbpost ") && ba.getBotName().equals(facebookBot)) {
                                String fbstatus = msg.substring(8);
                                try {
                                    facebook.postStatusMessage(fbstatus);
                                    m_botAction.sendChatMessage("Posted to TWSubspace!");
                                } catch (FacebookException e) {
                                    // TODO Auto-generated catch block
                                    Tools.printStackTrace(e);
                                }
                            }
                        }

            }

            if (event.getMessageType() == Message.ARENA_MESSAGE) {
                String arenamsg = event.getMessage();
                String eventmsg = arenamsg.toLowerCase();
                /*if (ba.getBotName().equals(twitterBot)) {
                    if (eventmsg.contains("?go base") || eventmsg.contains("?go wbduel") || eventmsg.contains("?go spidduel") || eventmsg.contains("?go javduel")
                            || eventmsg.contains("?go hockey")) {
                        try {
                            twitter.updateStatus("AUTOMATED-EVENT: " + arenamsg);
                        } catch (TwitterException e) {
                            // TODO Auto-generated catch block
                            Tools.printStackTrace(e);
                        }
                    } else if (eventmsg.contains("?go ")) {
                        try {
                            twitter.updateStatus("EVENT: " + arenamsg);
                        } catch (TwitterException e) {
                            // TODO Auto-generated catch block
                            Tools.printStackTrace(e);
                        }
                    } else if (eventmsg.contains("TWD") || eventmsg.contains("TWL")){
                    	try {
                			twitter.updateStatus("LEAGUE GAME: " + arenamsg);
                		} catch (TwitterException e) {
                			// TODO Auto-generated catch block
                			Tools.printStackTrace(e);
                		}
                    }
                }
                }*/
            }
        }
    }

    private void loadOps() {
        try {
            BotSettings m_botSettings = m_botAction.getBotSettings();
            mediaops.clear();
            //
            String ops[] = m_botSettings.getString("MediaOps").split(",");
            for (int i = 0; i < ops.length; i++)
                mediaops.put(ops[i].toLowerCase(), ops[i]);
        } catch (Exception e) {
            Tools.printStackTrace("Method Failed: ", e);
        }

    }

    public void deOp(String playerName, String message) {

        loadOps();
        BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("MediaOps");

        int spot = ops.indexOf(message);
        if (spot == 0 && ops.length() == message.length()) {
            ops = "";
            m_botAction.sendChatMessage("Removed " + message + " as a Media Operator");
        } else if (spot == 0 && ops.length() > message.length()) {
            ops = ops.substring(message.length() + 1);
            m_botAction.sendChatMessage("Removed " + message + " as a Media Operator");
        } else if (spot > 0 && spot + message.length() < ops.length()) {
            ops = ops.substring(0, spot) + ops.substring(spot + message.length() + 1);
            m_botAction.sendChatMessage("Removed " + message + " as a Media Operator");
        } else if (spot > 0 && spot == ops.length() - message.length()) {
            ops = ops.substring(0, spot - 1);
            m_botAction.sendChatMessage("Removed " + message + " as a Media Operator");
        } else
            m_botAction.sendChatMessage("Removed " + message + " as a Media Operator");

        m_botSettings.put("MediaOps", ops);
        m_botSettings.save();
        loadOps();
    }

    public void addOp(String playerName, String message) {

        BotSettings m_botSettings = m_botAction.getBotSettings();
        String ops = m_botSettings.getString("MediaOps");

        if (ops.contains(message)) {
            m_botAction.sendChatMessage(message + " is already a Media Operator");
            return;
        }
        if (ops.length() < 1)
            m_botSettings.put("MediaOps", message);
        else
            m_botSettings.put("MediaOps", ops + "," + message);
        m_botAction.sendChatMessage("Added " + message + " as a Media Operator");
        m_botSettings.save();
        loadOps();
    }

    public void listOp() {
        loadOps();
        String hops = "Media Operators: ";
        Iterator<String> it1 = mediaops.values().iterator();
        while (it1.hasNext())
            if (it1.hasNext())
                hops += it1.next() + ", ";
            else
                hops += it1.next();
        m_botAction.sendChatMessage(hops);
    }

    private void cmd_help(String name, String msg) {
        String[] strs = { "+-------------------------------------------------------------------------+",
                "|                  SSCU Trench Wars Social Media Bot                      |",
                "|                           by Dezmond                                    |",
                "|                  Player Commands for : " + ba.getBotName() + "          |",
                "|                                                                         |",
                "| Commands:                                                               |",
                "| !help                  - Displays this message                          |",
                "| !about                 - Who am I and what do I do?                     |",
                "|                                                                         |",
                "+-------------------------------------------------------------------------+", };
        String[] staff = { "|                                                                         |",
                "+-------------------------------------------------------------------------+",
                "|                  Ops & SMod Commands (Twitter)                          |",
                "| !tpost                 - Posts a status update with <msg>               |",
                "| !go <arena>            - Sends the bot to <arena>                       |",
                "| !die                   - Kills bot                                      |",
                "| !listops               - List Ops                                       |",
                "| !addop                 - Add Op                                         |",
                "| !deop                  - De Op                                          |",
                "+-------------------------------------------------------------------------+", };
        String[] stafffb = { "|                                                                         |",
                "+--------------------------------------------------------------------------+",
                "|                 Ops & SMod Commands (Facebook)                           |",
                "| !fbpost                 - Posts a status update with <msg>               |",
                "| !go <arena>             - Sends the bot to <arena>                       |",
                "| !die                    - Kills bot                                      |",
                "| !listops                - List Ops                                       |",
                "| !addop                  - Add Op                                         |",
                "| !deop                   - De Op                                          |",
                "+--------------------------------------------------------------------------+", };
            ba.smartPrivateMessageSpam(name, strs);

        if (oplist.isSmod(name.toLowerCase()) || mediaops.containsKey(name.toLowerCase()) && ba.getBotName().equals(twitterBot)) {
            ba.smartPrivateMessageSpam(name, staff);
        } else {
            ba.smartPrivateMessageSpam(name, stafffb);

        }
    }

}