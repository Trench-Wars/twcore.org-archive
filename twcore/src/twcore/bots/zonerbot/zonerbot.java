package twcore.bots.zonerbot;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import java.util.TimerTask;

import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.OperatorList;
import twcore.core.SubspaceBot;
import twcore.core.events.InterProcessEvent;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.util.Tools;
import twcore.core.util.ipc.IPCMessage;

/**
 * New ZonerBot
 * 
 * @author WingZero
 *
 */
public class zonerbot extends SubspaceBot {

    public BotAction ba;
    public OperatorList oplist;
    
    public static final String ZONE_CHANNEL = "Zone Channel";
    public static final int ADVERT_DELAY = 10;
    public static final int READVERT_MAX = 2;
    public static final int EXPIRE_TIME = 5;
    public static final int EXTENSION = 2;
    
    private boolean DEBUG;
    private String debugger;
    
    private AdvertTimer advertTimer;
    private ExpireTimer expireTimer;
    
    private Vectoid<String, Advert> queue;
    private ArrayList<Periodic> periodic;
    private LinkedList<Advert> usedAdverts;
    private LinkedList<String> trainers;
    
    private String currentUser;
    
    public zonerbot(BotAction botAction) {
        super(botAction);
        ba = m_botAction;
        oplist = ba.getOperatorList();
        queue = new Vectoid<String, Advert>();
        periodic = new ArrayList<Periodic>();
        trainers = new LinkedList<String>();
        usedAdverts = new LinkedList<Advert>();
        currentUser = null;
        ba.getEventRequester().request(EventRequester.LOGGED_ON);
        ba.getEventRequester().request(EventRequester.MESSAGE);
        loadTrainers();
        DEBUG = false;
        debugger = "";
    }

    /** Handles the LoggedOn event **/
    public void handleEvent(LoggedOn event) {
        ba.joinArena(ba.getBotSettings().getString("InitialArena"));
        ba.sendUnfilteredPublicMessage("?chat=robodev");
    }
    
    /** Handles the IPC event even though it is useless **/
    public void handleEvent(InterProcessEvent event) {
    }
    
    /** Handles the Message event **/
    public void handleEvent(Message event) {
        String name = event.getMessager();
        if (name == null || name.length() < 1)
            name = ba.getPlayerName(event.getPlayerID());
        String msg = event.getMessage();
        int type = event.getMessageType();
        
        if (type == Message.ALERT_MESSAGE && event.getAlertCommandType().equals("advert") && oplist.isER(name)) {
            if (msg.toLowerCase().contains("free"))
                cmd_free(name);
            else
                cmd_claim(name);
        }
        
        if (type == Message.ARENA_MESSAGE && currentUser != null && msg.startsWith("Not online, last seen")) {
            debug("currentUser " + currentUser + " is not online");
            removeMissing();
        }
        
        if (type == Message.REMOTE_PRIVATE_MESSAGE || type == Message.PRIVATE_MESSAGE) {
            if (oplist.isZH(name)) {
                if (msg.equals("!status"))
                    cmd_status(name);
                else if (msg.equals("!claim"))
                    cmd_claim(name);
                else if (msg.equals("!free"))
                    cmd_free(name);
                else if (msg.startsWith("!set "))
                    cmd_setAdvert(name, msg);
                else if (msg.startsWith("!sound "))
                    cmd_setSound(name, msg);
                else if (msg.startsWith("!view"))
                    cmd_view(name, msg);
                else if (msg.equals("!advert"))
                    cmd_advert(name);
                else if (msg.equals("!readvert"))
                    cmd_readvert(name);
                else if (msg.equals("!renew"))
                    cmd_renew(name);
                else if (msg.equals("!help"))
                    cmd_help(name);
            }
            if (oplist.isHighmod(name) || trainers.contains(name.toLowerCase())) {
                if (msg.startsWith("!grant "))
                    cmd_grant(name, msg);
                else if (msg.startsWith("!approve"))
                    cmd_approve(name, msg);
                else if (msg.equals("!debug"))
                    cmd_debug(name);
                else if (msg.equals("!die"))
                    cmd_die(name);
            }
            if (oplist.isSmod(name)) {
                if (msg.startsWith("!add "))
                    cmd_add(name, msg);
                else if (msg.startsWith("!remove "))
                    cmd_remove(name, msg);
                else if (msg.equals("!list"))
                    cmd_list(name);
            }
        }
    }
    
    /** Handles the !help command **/
    public void cmd_help(String name) {
        String[] msg = {
                "+-- ZonerBot Commands ------------------------------------------------------------------------.",
                "| !status                - Reports your current advert status                                 |",
                "| !claim                 - Claims an advert by adding you to the advert queue                 |",
                "| !free                  - Releases your advert and removes you from the queue                |",
                "| !set <message>         - Sets <message> as the advert message and must include ?go arena    |",
                "| !sound <#>             - Sets <#> as the sound to be used in the advert                     |",
                "| !view                  - Views your current advert message and sound                        |",
                "| !advert                - Sends the zone message as set by the advert                        |",
                "| !readvert              - Sends a last call default advert for the arena in your !advert     |",
                "| !renew                 - Prolongs the expiration of the advert for an extra 2 minutes       |",
        };
        ba.smartPrivateMessageSpam(name, msg);
        if (trainers.contains(name.toLowerCase()) || oplist.isSmod(name)) {
            msg = new String[] {
                    "+-- ZonerBot Trainer Commands ----------------------------------------------------------------+",
                    "| !grant <name>          - Grants an advert to a ZH allowing them to do a zoner once approved |",
                    "| !view <name>           - Views the current advert message and sound of <name>               |",
                    "| !approve               - Allows the earliest ZH you granted an advert to zone               |",
                    "| !approve <name>        - Allows <name> to zone the advert that was granted                  |",
            };
            ba.smartPrivateMessageSpam(name, msg);            
        }
        if (oplist.isSmod(name)) {
            msg = new String[] {
                    "+-- ZonerBot Smod Commands -------------------------------------------------------------------+",
                    "| !list                  - List of the current staff trainers                                 |",
                    "| !add <name>            - Adds <name> to the trainer list (allows zh advert granting)        |",
                    "| !remove <name>         - Removes <name> from the trainer list                               |",
            };
            ba.smartPrivateMessageSpam(name, msg);
        }
        ba.sendSmartPrivateMessage(name, "`---------------------------------------------------------------------------------------------'");
    }
    
    /** Handles the !add trainer command **/
    public void cmd_add(String name, String cmd) {
        if (cmd.length() < 5) return;
        String staff = cmd.substring(5).trim().toLowerCase();
        if (!trainers.contains(name)) {
            BotSettings settings = ba.getBotSettings();
            String ops = settings.getString("Trainers");

            if (ops.length() < 1) {
                settings.put("Trainers", staff);
            } else {
                settings.put("Trainers", ops + "," + staff);
            }
            ba.sendSmartPrivateMessage(name, "Trainer added: " + staff);
            settings.save();
            loadTrainers();
        } else
            ba.sendSmartPrivateMessage(name, "" + staff + " is already a trainer.");
    }
    
    /** Handles the !remove trainer command **/
    public void cmd_remove(String name, String cmd) {
        if (cmd.length() < 8) return;
        String staff = cmd.substring(8).trim().toLowerCase();
        if (trainers.contains(staff)) {
            BotSettings settings = ba.getBotSettings();
            String ops = "";
            for (String n : trainers)
                if (!n.equalsIgnoreCase(staff))
                    ops += n + ",";
            if (ops.length() > 1)
                ops = ops.substring(0, ops.length() - 1);
            settings.put("Trainers", ops);
            ba.sendSmartPrivateMessage(name, "Trainer removed: " + staff);
            settings.save();
            loadTrainers();  
        } else
            ba.sendSmartPrivateMessage(name, "" + staff + " is not a trainer.");
    }
    
    /** Handles the !list trainers command **/
    public void cmd_list(String name) {
        ba.sendSmartPrivateMessage(name, "Trainers: " + ba.getBotSettings().getString("Trainers"));
    }
    
    /** Handles the !status command **/
    public void cmd_status(String name) {
        int i = queue.indexOfKey(name);
        if (i > 0 && advertTimer != null)
            ba.sendSmartPrivateMessage(name, "The current advert belongs to: " + queue.firstValue().getName() + " Next advert: " + advertTimer.getTime());
        if (i > -1)
            sendQueuePosition(i);
        else
            ba.sendSmartPrivateMessage(name, "You have not claimed an advert.");
    }
    
    /** Handles the !claim command or ?advert **/
    public void cmd_claim(String name) {
        if (oplist.isZHExact(name)) {
            ba.sendSmartPrivateMessage(name, "You are not allowed to claim an advert. Have a trainer or high mod !grant you one instead.");
            return;
        }
        if (!queue.containsKey(name)) {
            debug("Queueing new Advert");
            queue.put(name, new Advert(name));
            if (queue.indexOfKey(name) == 0)
                prepareNext();
            else
                sendQueuePosition(name);
        } else {
            ba.sendSmartPrivateMessage(name, "You have already claimed an advert and cannot claim another until it is used.");
            sendQueuePosition(name);
        }
    }
    
    /** Handles the !grant command **/
    public void cmd_grant(String name, String cmd) {
        if (cmd.length() < 7 || !cmd.contains(" ")) {
            ba.sendSmartPrivateMessage(name, "Failed to evaluate command due to syntax error.");
            return;
        }
        String zh = cmd.substring(cmd.indexOf(" ") + 1);
        if (!oplist.isZHExact(zh)) {
            ba.sendSmartPrivateMessage(name, "Adverts can only be granted to ZHs. Use !claim instead.");
            return;
        }
        if (!queue.containsKey(zh)) {
            queue.put(zh, new Advert(zh, name));
            ba.sendSmartPrivateMessage(name, "Advert granted to " + zh + ".");
            ba.sendSmartPrivateMessage(zh, "You have been granted an advert by " + name + ". Advert must be approved before it can be used.");
            if (queue.indexOfKey(zh) == 0)
                prepareNext();
            else
                sendQueuePosition(zh);
        }
    }
    
    /** Handles the !free command or ?advert free **/
    public void cmd_free(String name) {
        if (queue.containsKey(name)) {
            Advert advert = queue.get(name);
            if (advert.getStatus() < Advert.ZONED) {
                int index = queue.indexOfKey(name);
                if (index == 0) {
                    queue.remove(0);
                    if (expireTimer != null && !expireTimer.hasExpired()) {
                        expireTimer.endNow();
                    }
                    prepareNext();
                } else {
                    queue.remove(index);
                    if (index < queue.size()) {
                        for (; index < queue.size(); index++)
                            sendQueuePosition(index);
                    }
                }
                ba.sendSmartPrivateMessage(name, "Advert freed.");
            } else
                ba.sendSmartPrivateMessage(name, "An advert must not have been used in order to free it.");
        } else
            ba.sendSmartPrivateMessage(name, "You have not claimed an advert.");
    }
    
    /** Handles the !set command **/
    public void cmd_setAdvert(String name, String cmd) {
        if (cmd.length() < 6 || !cmd.contains(" ")) return;
        String msg = cmd.substring(cmd.indexOf(" ") + 1);
        if (queue.containsKey(name))
            ba.sendSmartPrivateMessage(name, queue.get(name).setAdvert(msg));
        else
            ba.sendSmartPrivateMessage(name, "You must have claimed or been granted an advert before you can set its message.");
    }
    
    /** Handles the !sound command **/
    public void cmd_setSound(String name, String cmd) {
        if (cmd.length() < 7 || !cmd.contains(" ")) return;
        int sound = Advert.DEFAULT_SOUND;
        try {
            sound = Integer.parseInt(cmd.substring(cmd.indexOf(" ") + 1));
        } catch (NumberFormatException e) {
            ba.sendSmartPrivateMessage(name, "The sound must be a number.");
            return;
        }
        if (queue.containsKey(name))
            ba.sendSmartPrivateMessage(name, queue.get(name).setSound(sound));
        else
            ba.sendSmartPrivateMessage(name, "You must have claimed or been granted an advert before you can set its sound.");
    }
    
    /** Handles the !view command as well as the !view <name> command **/
    public void cmd_view(String name, String cmd) {
        if (cmd.contains(" ") && cmd.length() > 6) {
            String adverter = cmd.substring(cmd.indexOf(" ") + 1);
            if (queue.containsKey(adverter)) {
                Advert advert = queue.get(adverter);
                ba.sendSmartPrivateMessage(name, advert.getMessage());
                ba.sendSmartPrivateMessage(name, "Sound: " + advert.getSound());
            } else
                ba.sendSmartPrivateMessage(name, adverter + " does not have an advert."); 
        } else if (queue.containsKey(name)) {
            Advert advert = queue.get(name);
            ba.sendSmartPrivateMessage(name, advert.getMessage());
            ba.sendSmartPrivateMessage(name, "Sound: " + advert.getSound());
        } else
            ba.sendSmartPrivateMessage(name, "Advert was not found."); 
    }
    
    /** Handles the !approve command with or without the zh name **/
    public void cmd_approve(String name, String cmd) {
        if (cmd.length() > 8 && cmd.contains(" ")) {
            String zh = cmd.substring(cmd.indexOf(" ") + 1);
            if (queue.containsKey(zh)) 
                ba.sendSmartPrivateMessage(name, queue.get(zh).approve());
            else
                ba.sendSmartPrivateMessage(name, "No advert found for " + zh + ".");
        } else if (cmd.length() == 8) {
            for (Advert advert : queue.values()) {
                if (advert.isGranter(name)) {
                    ba.sendSmartPrivateMessage(name, advert.approve());
                    break;
                }
            }
            ba.sendSmartPrivateMessage(name, "You have not granted any adverts.");
        } else 
            ba.sendSmartPrivateMessage(name, "Failure to evaluate command syntax.");
    }
    
    /** Handles the !advert command which executes the current advert if possible **/
    public void cmd_advert(String name) {
        if (queue.containsKey(name)) {
            if (queue.indexOfKey(name) > 0)
                sendQueuePosition(name);
            else if (advertTimer != null && !advertTimer.hasExpired()) 
                sendQueuePosition(name);
            else {
                Advert advert = queue.firstValue();

                if (advert.getStatus() < Advert.READY) {
                    if (!advert.isGranted())
                        ba.sendSmartPrivateMessage(name, "You have to set an advert message before you can use it.");
                    else
                        ba.sendSmartPrivateMessage(name, "Your advert must be set and approved before it can be used.");
                } else if (advert.getStatus() > Advert.READY)
                    ba.sendSmartPrivateMessage(name, "The advert has already been zoned.");
                else {
                    advert.setStatus(Advert.ZONED);
                    // need to check the length n shit
                    ba.sendZoneMessage(advert.getMessage() + "-" + advert.getName(), advert.getSound());
                    
                    advertTimer = new AdvertTimer(ADVERT_DELAY);
                    ba.scheduleTask(advertTimer, ADVERT_DELAY * Tools.TimeInMillis.MINUTE);
                    if (expireTimer != null)
                        expireTimer.endNow();
                    
                    // Send an IPC message to Robohelp to record the advert
                    IPCMessage msg = new IPCMessage(name.toLowerCase() + "@ad@" + advert.getArena() + "@ad@" + advert.getMessage());
                    ba.ipcTransmit(ZONE_CHANNEL, msg);
                    
                    queue.remove(0);
                    usedAdverts.add(0, advert);
                    ba.sendSmartPrivateMessage(name, "A readvert will be available for " + advert.readvertTime() + ".");
                    prepareNext();
                }
            }
        } else
            ba.sendSmartPrivateMessage(name, "You have to claim an advert first before you can use it.");
    }
    
    /** Handles the !readvert command which sends a default zone message for a given arena **/
    public void cmd_readvert(String name) {
        if (queue.containsKey(name)) {
            ba.sendSmartPrivateMessage(name, "The initial advert must be used before readvert can be used.");
        } else if (usedAdverts.isEmpty()) {
            ba.sendSmartPrivateMessage(name, "An advert must be claimed and used before a readvert can be used.");
        } else if (!usedAdverts.getFirst().getName().equalsIgnoreCase(name)) {
            ba.sendSmartPrivateMessage(name, "No advert used by you is currently eligible for a readvert.");
        } else {
            Advert advert = usedAdverts.getFirst();
            if (advert.getStatus() == Advert.ZONED && advert.canReadvert()) {
                advert.setStatus(Advert.DONE);
                String event = advert.getArena().toUpperCase();
                String zoners[] = { 
                        "Last call for " + event + "." + " Type ?go " + event + " to play. -" + name,
                        "The event " + event + " is starting. Type ?go " + event + " to play. -" + name };
                ba.sendZoneMessage(zoners[new Random().nextInt(zoners.length)], 1);
            } else {
                ba.sendSmartPrivateMessage(name, "Your last advert does not have a readvert available. It was used or expired.");
            }
        }
    }
    
    /** Handles the !renew command which if the advert hasn't been zoned or expired, prolongs the expire time **/
    public void cmd_renew(String name) {
        if (!queue.containsKey(name))
            ba.sendSmartPrivateMessage(name, "There is no advert available for renewal.");
        else if (queue.indexOfKey(name) != 0)
            sendQueuePosition(name);
        else if (advertTimer != null && !advertTimer.hasExpired())
            sendQueuePosition(0);
        else if (expireTimer != null && !expireTimer.hasExpired())
            ba.sendSmartPrivateMessage(name, "Advert renewed. Remaining time: " + expireTimer.renewTime());
        else 
            ba.sendSmartPrivateMessage(name, "Advert has already expired.");
    }
    
    /** Removes the unused advert **/
    private void expireAdvert() {
        debug("Expiring current Advert");
        currentUser = null;
        Advert advert = queue.remove(0);
        if (advert != null && advert.getStatus() < Advert.ZONED) {
            ba.sendSmartPrivateMessage(advert.getName(), "Your advert has expired and has been removed from the queue.");
            prepareNext();
        }
    }
    
    /** Helper removes the current advert from the queue due to a missing adverter **/
    private void removeMissing() {
        debug("Removing Advert of offline person");
        if (queue.indexOfKey(currentUser) != 0) return;
        if (expireTimer != null)
            expireTimer.endNow();
        if (!queue.isEmpty())
            queue.remove(0);
        currentUser = null;
        prepareNext();
    }
    
    /** Helper sends appropriate queue position information to name **/ 
    private void sendQueuePosition(String name) {
        int i = queue.indexOfKey(name);
        if (i == 0) {
            if (expireTimer != null && !expireTimer.hasExpired())
                ba.sendSmartPrivateMessage(name, "It is your turn to use your advert. You have " + expireTimer.getTime() + " to use it.");
            else if (advertTimer != null && !advertTimer.hasExpired())
                ba.sendSmartPrivateMessage(name, "You are next in the advert queue. Time remaining: " + advertTimer.getTime());
        } else if (i > 0) {
            ba.sendSmartPrivateMessage(name, "There are " + i + " people currently ahead of you in the advert queue.");
        } else if (!usedAdverts.isEmpty() && usedAdverts.getFirst().getName().equalsIgnoreCase(name)) {
            Advert advert = usedAdverts.getFirst();
            if (advert.canReadvert())
                ba.sendSmartPrivateMessage(name, "Your readvert will be available for " + advert.readvertTime() + ".");
            else 
                ba.sendSmartPrivateMessage(name, "No advert available.");
        } else
            ba.sendSmartPrivateMessage(name, "You have not claimed an advert.");
    }
    
    /** Helper sends appropriate queue position information to name front advert index **/ 
    private void sendQueuePosition(int index) {
        String name = queue.getKey(index);
        if (index == 0) {
            if (expireTimer != null && !expireTimer.hasExpired())
                ba.sendSmartPrivateMessage(name, "It is your turn to use your advert. It will expire in " + expireTimer.getTime() + ".");
            else if (advertTimer != null && !advertTimer.hasExpired())
                ba.sendSmartPrivateMessage(name, "You are next in the advert queue. Time remaining: " + advertTimer.getTime());
        } else if (index > 0)
            ba.sendSmartPrivateMessage(name, "There are " + index + " people currently ahead of you in the advert queue.");
    }

    /** Called when the first advert in the queue can be used. Starts the idle timer and informs queue positions. **/
    private void prepareNext() {
        debug("Prepare next called");
        if (!queue.isEmpty()) {
            if (advertTimer == null || (advertTimer != null && advertTimer.hasExpired())) {
                Advert advert = queue.get(0);
                expireTimer = new ExpireTimer(advert.getName(), EXPIRE_TIME);
                ba.scheduleTask(expireTimer, EXPIRE_TIME * Tools.TimeInMillis.MINUTE);
                currentUser = advert.getName();
                ba.sendUnfilteredPublicMessage("?find " + currentUser);
            }
            for (String n : queue.keySet())
                sendQueuePosition(n);
        }
    }
    
    /** Loads the trainers listed in the cfg file **/
    private void loadTrainers() {
        trainers.clear();
        String[] list = ba.getBotSettings().getString("Trainers").split(",");
        for (String n : list)
            trainers.add(n.toLowerCase());
    }
    
    /**
     * This is a representation of an advert claim containing all relevant information
     * related to the advert including methods and functions.
     *
     * @author WingZero
     */
    private class Advert {
        
        String name;
        String granter;
        String advert;
        int sound;
        int status;
        boolean granted;
        long zoned;
        static final int APPROVE = 0;
        static final int READY = 1;
        static final int ZONED = 2;
        static final int DONE = 3;
        
        // sounds
        public static final int MAX_ADVERT_LENGTH = 400;
        public static final int DEFAULT_SOUND = 2;
        public static final int MIN_SOUND = 1;
        public static final int MAX_SOUND = 255;
        public static final int REGAN_SOUND = 6;
        public static final int SEX_SOUND = 12;
        public static final int PM_SOUND = 26;
        public static final int MUSIC1_SOUND = 100;
        public static final int MUSIC2_SOUND = 102;
        
        public Advert(String name) {
            this.name = name;
            granter = null;
            advert = null;
            sound = DEFAULT_SOUND;
            status = APPROVE;
            granted = false;
            zoned = -1;
        }
        
        public Advert(String name, String granter) {
            this.name = name;
            this.granter = granter;
            advert = null;
            sound = DEFAULT_SOUND;
            status = APPROVE;
            granted = true;
            zoned = -1;
        }
        
        public String getName() {
            return name;
        }

        public boolean isGranter(String g) {
            return (granted && g.equalsIgnoreCase(granter));
        }
        
        public boolean isGranted() {
            return granted;
        }
        
        public void setStatus(int nextStatus) {
            status = nextStatus;
            if (status == ZONED && zoned == -1)
                zoned = System.currentTimeMillis();
            debug("New status: " + status);
        }
        
        public String setAdvert(String str) {
            if (str == null || str.length() < 1) 
                return "No advert specified.";
            if (str.length() > MAX_ADVERT_LENGTH)
                return "Advert must be less than " + MAX_ADVERT_LENGTH + " characters long.";
            if (!str.contains("?go "))
                return "Advert is required to have ?go ArenaName at the end or towards the end.";
            if (str.toLowerCase().contains("-" + name.toLowerCase()))
                return "Do not include a tag (-Name) at the end because it will be added automatically.";
            if (status == READY) {
                if (!granted) {
                    advert = str + " ";
                    return "Advert message changed and can be seen with !view.";
                } else {
                    advert = str + " ";
                    status = APPROVE;
                    ba.sendSmartPrivateMessage(granter, "" + name + " set an advert message and waits for approval.");
                    return "Advert message changed and must be approved again before it can be used.";
                }
            } else if (status == APPROVE) {
                if (!granted) {
                    advert = str + " ";
                    status = READY;
                    return "Advert message set and can now be seen with !view.";
                } else {
                    advert = str + " ";
                    status = APPROVE;
                    ba.sendSmartPrivateMessage(granter, "" + name + " changed the advert message and needs approval.");
                    return "Advert message set and can now be seen with !view. The advert must now be approved before it can be used.";
                }                
            } else
                return "Advert has already been used.";
        }
        
        public String setSound(int s) {
            if (s < MIN_SOUND || s > MAX_SOUND)
                return "Invalid, sound number must be between " + MIN_SOUND + " and " + MAX_SOUND + ".";
            if (status > READY)
                return "Advert has already been used.";
            if (soundCheck(s)) {
                sound = s;
                if ((granted && status == APPROVE) || (!granted && status < ZONED))
                    return "Advert sound set to " + s + ".";
                else {
                    unapprove();
                    return "Advert sound set to " + s + " and must be approved again before it can be used.";
                }
            } else 
                return "Sound number " + s + " is not allowed.";
        }
        
        public String approve() {
            if (granted && status == APPROVE) {
                status = READY;
                ba.sendSmartPrivateMessage(name, "Advert approved. Use !advert to zone your advert.");
                return "Advert approved for " + name + ".";
            } else
                return "Advert has already been approved.";
        }
        
        public void unapprove() {
            if (granted && status == READY)
                status = APPROVE;    
        }
        
        public int getStatus() {
            return status;
        }
        
        // we will probably need to handle messages over length 120
        public String getMessage() {
            if (advert != null)
                return advert;
            else
                return "No advert message has been set.";
        }
        
        public int getSound() {
            return sound;
        }
        
        public String getArena() {
            if (advert != null && advert.contains("?go ")) {
                String arena = advert.substring(advert.lastIndexOf("?go ") + 4);
                if (arena.contains(" "))
                    return arena.substring(0, arena.indexOf(" "));
                else
                    return arena;
            } else
                return "No arena found or advert not set.";
        }
        
        public boolean canReadvert() {
            return ((System.currentTimeMillis() - zoned) < (READVERT_MAX * Tools.TimeInMillis.MINUTE));
        }
        
        public String readvertTime() {
            long time = (READVERT_MAX * Tools.TimeInMillis.MINUTE) - (System.currentTimeMillis() - zoned);
            if (time > 0)
                return "" + (time / Tools.TimeInMillis.SECOND) + " seconds";
            else return "expired";
        }
        
        private boolean soundCheck(int s) {
            return (s != REGAN_SOUND && s != SEX_SOUND && s != PM_SOUND && s != MUSIC1_SOUND && s != MUSIC2_SOUND);
        }
    }
    
    /**
     * The AdvertTimer is used to put time between each set of event zoners. If it is running
     * then that means no one should be able to advert. Only time things move is when this expires.     *
     * @author WingZero
     */
    private class AdvertTimer extends TimerTask {

        int delay;
        long timestamp;    
        boolean active;
        
        public AdvertTimer(int delayBetweenAdverts) {
            debug("New AdvertTimer created with delay: " + delay);
            delay = delayBetweenAdverts;
            timestamp = System.currentTimeMillis();
            active = true;
        }
        
        @Override
        public void run() {
            debug("AdvertTimer running run method");
            active = false;
            prepareNext();
        }
        
        public String getTime() {
            long left = ((delay * Tools.TimeInMillis.MINUTE) - (System.currentTimeMillis() - timestamp));
            if (left > 0) {
                int sec = (int) ((left / 1000) % 60);
                int min = (int) (((left / 1000) - sec) / 60);
                return "" + min + " minutes " + sec + " seconds";
            } else
                return null;
        }
        
        public boolean hasExpired() {
            return !active;
        }
    }
    
    /** 
     * This class is used to remove an advert after having not been used. 
     * Even after the advert has been used it will continue running to 
     * provide an expiration for the readvert.
     */
    private class ExpireTimer extends TimerTask {

        int delay;
        long timestamp;    
        boolean active;
        
        public ExpireTimer(String waiter, int expireDelay) {
            debug("New ExpireTimer created with delay: " + expireDelay);
            delay = expireDelay;
            timestamp = System.currentTimeMillis();
            active = true;
        }
        
        public String getTime() {
            return longString(((delay * Tools.TimeInMillis.MINUTE) - (System.currentTimeMillis() - timestamp)));
        }
        
        private String longString(long t) {
            if (t > 0) {
                int sec = (int) ((t / 1000) % 60);
                int min = (int) (((t / 1000) - sec) / 60);
                return "" + min + " minutes " + sec + " seconds";
            } else
                return null;
        }
        
        @Override
        public void run() {
            debug("ExpireTimer run method running");
            active = false;
            expireAdvert();
        }
        
        public void endNow() {
            if (active)
                ba.cancelTask(this);
            active = false;
        }
        
        public String renewTime() {
            endNow();
            return longString(((delay * Tools.TimeInMillis.MINUTE - (System.currentTimeMillis() - timestamp)) + (EXTENSION * Tools.TimeInMillis.MINUTE)));
        }
        
        public boolean hasExpired() {
            return !active;
        }
    }
    
    private class Periodic extends TimerTask {

        String name, advert;
        int index;
        int delay;
        long lifeTime;
        long started;
        boolean active;
        
        public Periodic(int index, String name) {
            this.index = index;
            this.name = name;
            advert = null;
            delay = -1;
            lifeTime = -1;
            started = -1;
            active = false;
        }
        
        public void run() {
            long now = System.currentTimeMillis();
            if (started + lifeTime < now) {
                // end it
                periodic.remove(index);
                this.cancel();
            } else if (active) {
                try {
                    if (advert.contains("%"))
                        ba.sendZoneMessage(advert.substring(0, advert.indexOf("%")), Integer.parseInt(advert.substring(advert.indexOf("%") + 1)));
                    else
                        ba.sendZoneMessage(advert);
                } catch (Exception e) {
                    ba.sendSmartPrivateMessage(name, "There is an" + " error in your sound code, it should be at the end."
                            + " Ex: \"!periodic 20;5;hello world! %%1\" . " + "Your periodic has been removed.");
                    periodic.remove(index - 1);
                    this.cancel();
                }
            }
        }
        
        public void setIndex(int i) {
            index = i;
        }
        
    }
    
    public void cmd_die(String name) {
        try {
            if (expireTimer != null)
                expireTimer.endNow();
            if (advertTimer != null && !advertTimer.hasExpired())
                ba.cancelTask(advertTimer);
        } catch (Exception e) {};
        ba.sendChatMessage("Logging off. Requested by: " + name);
        ba.scheduleTask(new Die(), 2000);
    }
    
    private class Die extends TimerTask {
        @Override
        public void run() {
            ba.die();
        }
    }
    
    private void cmd_debug(String name) {
        if (!DEBUG) {
            debugger = name;
            DEBUG = true;
            ba.sendSmartPrivateMessage(name, "Debugging ENABLED. You are now set as the debugger.");
        } else if (debugger.equalsIgnoreCase(name)){
            debugger = "";
            DEBUG = false;
            ba.sendSmartPrivateMessage(name, "Debugging DISABLED and debugger reset.");
        } else {
            ba.sendChatMessage(name + " has overriden " + debugger + " as the target of debug messages.");
            ba.sendSmartPrivateMessage(name, "Debugging still ENABLED and you have replaced " + debugger + " as the debugger.");
            debugger = name;
        }
    }
    
    public void debug(String msg) {
        if (DEBUG)
            ba.sendSmartPrivateMessage(debugger, "[DEBUG] " + msg);
    }
    
}
