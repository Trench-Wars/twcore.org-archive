package twcore.bots.pubsystem.module;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.module.achievements.Achievement;
import twcore.bots.pubsystem.module.achievements.KillDeath;
import twcore.bots.pubsystem.module.achievements.Location;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.*;
import twcore.core.game.Player;

/**
 *
 * @author spookedone
 */
public final class PubAchievementsModule extends AbstractModule {

    private static final String XML_FILE_NAME = "C:/subspace/Achievements.xml";
    private static final boolean XML_VALIDATION = false;

    private final List<Achievement> achievements;
    private Map<Short, List<Achievement>> players;

    private volatile long time = -1;
    private boolean running = false;

    private enum Type {
        kill, death, location, time, flagclaim, flagtime, turret, prize;

        public int value() {
            return 1 << ordinal();
        }
    };

    public PubAchievementsModule(BotAction m_botAction, PubContext context) {
        super(m_botAction, context, "Achievements");

        achievements = new LinkedList<Achievement>();
        players = Collections.synchronizedMap(new HashMap<Short, List<Achievement>>());

        synchronized (achievements) {
            reloadConfig();
        }
        
    }

    @Override
    public void reloadConfig() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setValidating(XML_VALIDATION);

        try {
            SAXParser parser = factory.newSAXParser();

            InputSource input = new InputSource(new FileReader(XML_FILE_NAME));
            input.setSystemId("file://" + new File(XML_FILE_NAME).getAbsolutePath());

            AchievementHandler handler = new AchievementHandler();

            parser.parse(input, handler);
        } catch (IOException ex) {
            Logger.getLogger(PubAchievementsModule.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(PubAchievementsModule.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SAXException ex) {
            Logger.getLogger(PubAchievementsModule.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void loadPlayer(String name) {

    }

    /* EVENT */

    @Override
    public void handleEvent(Message event){
    
    }

    @Override
    public void handleEvent(ArenaList event){}

    @Override
    public void handleEvent(PlayerEntered event){}

    @Override
    public void handleEvent(PlayerPosition event){}

    @Override
    public void handleEvent(PlayerLeft event){}

    @Override
    public void handleEvent(PlayerDeath event){}

    @Override
    public void handleEvent(PlayerBanner event){}

    @Override
    public void handleEvent(Prize event){}

    @Override
    public void handleEvent(ScoreUpdate event){}

    @Override
    public void handleEvent(ScoreReset event){}

    @Override
    public void handleEvent(SoccerGoal event){}

    @Override
    public void handleEvent(WeaponFired event){}

    @Override
    public void handleEvent(FrequencyChange event){}

    @Override
    public void handleEvent(FrequencyShipChange event){}

    @Override
    public void handleEvent(LoggedOn event){}

    @Override
    public void handleEvent(FileArrived event){}

    @Override
    public void handleEvent(ArenaJoined event){}

    @Override
    public void handleEvent(WatchDamage event){}

    @Override
    public void handleEvent(BallPosition event){}

    @Override
    public void handleEvent(TurretEvent event){}

    @Override
    public void handleEvent(FlagPosition event){}

    @Override
    public void handleEvent(FlagDropped event){}

    @Override
    public void handleEvent(FlagVictory event){}

    @Override
    public void handleEvent(FlagReward event){}

    @Override
    public void handleEvent(FlagClaimed event){}

    @Override
    public void handleEvent(InterProcessEvent event){}

    @Override
    public void handleEvent(SQLResultEvent event) {}

    @Override
    public void handleDisconnect() {}

    @Override
    public void start() {
        Iterator<Player> i = m_botAction.getPlayerIterator();
        while(i.hasNext()) {
        	Player player = i.next();
        	players.put(player.getPlayerID(), Collections.synchronizedList(
                        new LinkedList<Achievement>(achievements)));
        }

        m_botAction.scheduleTaskAtFixedRate(new TimerTask(){

            @Override
            public void run() {
                time++;
            }
        }, 0, 1000);
    }


    @Override
    public void stop() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.PLAYER_DEATH);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.PLAYER_ENTERED);
        eventRequester.request(EventRequester.PLAYER_LEFT);
        eventRequester.request(EventRequester.PLAYER_POSITION);
        eventRequester.request(EventRequester.FREQUENCY_CHANGE);
        eventRequester.request(EventRequester.FREQUENCY_SHIP_CHANGE);
        eventRequester.request(EventRequester.FLAG_CLAIMED);
        eventRequester.request(EventRequester.PRIZE);
    }

    @Override
    public void handleCommand(String sender, String command) {
        if (command.startsWith("!list")) {
            sendAchievementList(sender);
        }
    }

    @Override
    public void handleModCommand(String sender, String command) {
        if(command.startsWith("!achieve")) {
            start();
        }
    }

    @Override
    public String[] getHelpMessage(String sender) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String[] getModHelpMessage(String sender) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Command support methods
     */
    public void sendAchievementList(String recipient) {
        for (Achievement a: achievements) {
            m_botAction.sendPrivateMessage(recipient, a.getName() + " - " + a.getDescription());
        }
    }

    /**
     * Handles loading Achievements from XML
     */
    private final class AchievementHandler extends DefaultHandler {

        private StringBuffer buffer = new StringBuffer();
        private Achievement achievement;
        private KillDeath kill;
        private KillDeath death;

        private boolean killTag = false, deathTag = false;
        
        @Override
        public void characters(char[] buffer, int start, int length) {
            this.buffer.append(buffer, start, length);
        }

        @Override
        public void startElement(String namespace, String name, String fullName, Attributes attributes) {
            buffer.setLength(0);
            if (name.equals("achievement")) {
                achievement = new Achievement(Integer.parseInt(attributes.getValue("uid")));
                achievement.setName(attributes.getValue("name"));
                String ship = attributes.getValue("ship");
                if (ship != null) achievement.setShip(Integer.parseInt(ship));
            } else {
                int type = achievement.getType();
                switch (Type.valueOf(name)) {
                    case kill:
                        type |= Type.kill.value();

                        kill = new KillDeath();

                        String kMin = attributes.getValue("minimum");
                        String kMax = attributes.getValue("maximum");
                        String kShip = attributes.getValue("ship");

                        if (kMin != null) kill.setMinimum(Integer.parseInt(kMin));
                        if (kMax != null) kill.setMaximum(Integer.parseInt(kMax));
                        if (kShip != null) kill.setShip(Integer.parseInt(kShip));

                        killTag = true;

                        break;
                    case death:
                        type |= Type.death.value();

                        death = new KillDeath();

                        String dMin = attributes.getValue("minimum");
                        String dMax = attributes.getValue("maximum");
                        String dShip = attributes.getValue("ship");

                        if (dMin != null) death.setMinimum(Integer.parseInt(dMin));
                        if (dMax != null) death.setMaximum(Integer.parseInt(dMax));
                        if (dShip != null) death.setShip(Integer.parseInt(dShip));

                        deathTag = true;

                        break;
                    case location:
                        type |= Type.location.value();

                        Location location = new Location();

                        String x = attributes.getValue("x");
                        String y = attributes.getValue("y");
                        String radius = attributes.getValue("radius");
                        String width = attributes.getValue("width");
                        String length = attributes.getValue("legnth");
                        String minDistance = attributes.getValue("minimumDistance");
                        String maxDistance = attributes.getValue("maximumDistance");

                        if (x != null) location.setX(Integer.parseInt(x));
                        if (y != null) location.setY(Integer.parseInt(y));
                        if (radius != null) location.setRadius(Integer.parseInt(radius));
                        if (width != null) location.setWidth(Integer.parseInt(width));
                        if (length != null) location.setLength(Integer.parseInt(length));
                        if (minDistance != null) location.setMinDistance(Integer.parseInt(minDistance));
                        if (maxDistance != null) location.setMaxDistance(Integer.parseInt(maxDistance));

                        if (killTag) {
                            kill.setLocation(location);
                        } else if (deathTag) {
                            death.setLocation(location);
                        } else {
                            achievement.addLocation(location);
                        }

                        break;
                    case time:
                        type |= Type.time.value();

                        String timeMin = attributes.getValue("minimum");
                        String timeMax = attributes.getValue("maximum");

                        if (timeMin != null) achievement.setTimeMin(Integer.parseInt(timeMin));
                        if (timeMax != null) achievement.setTimeMax(Integer.parseInt(timeMax));

                        break;
                    case flagclaim:
                        type |= Type.flagclaim.value();

                        String flagClaimMin = attributes.getValue("minimum");
                        String flagClaimMax = attributes.getValue("maximum");

                        if (flagClaimMin != null) achievement.setFlagClaimMin(Integer.parseInt(flagClaimMin));
                        if (flagClaimMax != null) achievement.setFlagClaimMax(Integer.parseInt(flagClaimMax));

                        break;
                    case flagtime:
                        type |= Type.flagtime.value();

                        String flagTimeMin = attributes.getValue("minimum");
                        String flagTimeMax = attributes.getValue("maximum");

                        if (flagTimeMin != null) achievement.setFlagTimeMin(Integer.parseInt(flagTimeMin));
                        if (flagTimeMax != null) achievement.setFlagTimeMax(Integer.parseInt(flagTimeMax));

                        break;
                    case turret:
                        type |= Type.turret.value();

                        if (killTag) {
                            kill.setTurret(true);
                        } else if (deathTag) {
                            death.setTurret(true);
                        } else {
                            achievement.setTurret(true);
                        }

                        break;
                    case prize:
                        type |= Type.prize.value();

                        String prizeMin = attributes.getValue("minimum");
                        String prizeMax = attributes.getValue("maximum");
                        String prizeType = attributes.getValue("type");

                        if (prizeMin != null) achievement.setPrizeMin(Integer.parseInt(prizeMin));
                        if (prizeMax != null) achievement.setPrizeMax(Integer.parseInt(prizeMax));
                        if (prizeType != null) achievement.setPrizeType(Integer.parseInt(prizeType));

                        break;
                }
                achievement.setType(type);
            }
        }

        @Override
        public void endElement(String namespace, String name, String fullName) {
            if (name.equals("description")) {
                achievement.setDescription(buffer.toString().trim());
            } else if (name.equals("kill")) {
                killTag = false;
                achievement.addKill(kill);
            } else if (name.equals("death")) {
                deathTag = false;
                achievement.addDeath(death);
            } else if (name.equals("achievement")) {
                achievements.add(achievement);
            }
        }

        /** This method is called when warnings occur */
        @Override
        public void warning(SAXParseException exception) {
            System.err.println("WARNING: line " + exception.getLineNumber() + ": "
                    + exception.getMessage());
        }

        /** This method is called when errors occur */
        @Override
        public void error(SAXParseException exception) {
            System.err.println("ERROR: line " + exception.getLineNumber() + ": "
                    + exception.getMessage());
        }

        /** This method is called when non-recoverable errors occur. */
        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            System.err.println("FATAL: line " + exception.getLineNumber() + ": "
                    + exception.getMessage());
        }
    }

    @Override
    public void handleSmodCommand(String sender, String command) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[]{};
    }
}
