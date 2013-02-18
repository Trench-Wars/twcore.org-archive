package twcore.bots.socialmedia;

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

    }

    public void handleEvent(ArenaList event) {

    }

    public void handleEvent(ArenaJoined event) {
    }

    public void handleEvent(PlayerEntered event) {
        //We've just had a benchmarks in players for pub. Lets update Twitter!!!
        String arena = ba.getArenaName();
        if (ba.getBotName().equals(twitterBot)) {
            if (ba.getPlayingPlayers().size() > 50 && Tools.isAllDigits(arena)) {
                try {
                    twitter.updateStatus("We have just went passed the 50 player mark in public (non specced players)! Come join in the basing action in ?go "
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

        if (event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE) {
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
            if (oplist.isSmod(name)) {
                if (msg.startsWith("!go ")) {
                    String go = msg.substring(4);
                    ba.changeArena(go);
                    ba.sendSmartPrivateMessage(name, "Moving services to " + go);
                } else if (msg.startsWith("!tpost ") && ba.getBotName().equals(twitterBot)) {
                    String status = msg.substring(7);
                    try {
                        twitter.updateStatus(status + " -" + ba.getBotName());
                    } catch (TwitterException e) {
                        // TODO Auto-generated catch block
                        Tools.printStackTrace(e);
                    }
                } else if (msg.startsWith("!fbpost ") && ba.getBotName().equals(facebookBot)) {
                    String fbstatus = msg.substring(8);
                    try {
                        facebook.postStatusMessage(fbstatus + " -" + ba.getBotName());
                    } catch (FacebookException e) {
                        // TODO Auto-generated catch block
                        Tools.printStackTrace(e);
                    }
                } else if (msg.equalsIgnoreCase("!die")) {
                    ba.die();
                }
            }
        }

        if (event.getMessageType() == Message.ARENA_MESSAGE) {
            String arenamsg = event.getMessage();
            String eventmsg = arenamsg.toLowerCase();
            if (ba.getBotName().equals(twitterBot)) {
                if (eventmsg.contains("?go base") || eventmsg.contains("?go wbduel") || eventmsg.contains("?go javduel")
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
                }
            }
        }
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
                "|                  SMod Commands (Twitter)                                |",
                "| !tpost                 - Posts a status update with <msg>               |",
                "| !go <arena>            - Sends the bot to <arena>                       |",
                "| !die                   - Kills bot                                      |",
                "+-------------------------------------------------------------------------+", };
        String[] stafffb = { "|                                                                         |",
                "+-------------------------------------------------------------------------+",
                "|                  SMod Commands (Facebook)                               |",
                "| !fbpost                 - Posts a status update with <msg>              |",
                "| !go                     - Sends the bot to <arena>                      |",
                "| !die                    - Kills bot                                     |",
                "+-------------------------------------------------------------------------+", };
        if(ba.getBotName().equals(twitterBot)){
            ba.smartPrivateMessageSpam(name, strs);
        } else {
            ba.smartPrivateMessageSpam(name, stafffb);
        }
        if (oplist.isSmod(name.toLowerCase()) && ba.getBotName().equals(twitterBot)) {
            ba.smartPrivateMessageSpam(name, staff);
        } else {
            ba.smartPrivateMessageSpam(name, stafffb);

        }
    }

}