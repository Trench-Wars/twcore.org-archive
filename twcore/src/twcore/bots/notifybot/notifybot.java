package twcore.bots.notifybot;

import twcore.core.*;
import twcore.core.events.*;
import twcore.core.stats.DBPlayerData;
import twcore.core.util.Tools;
import twcore.core.events.SQLResultEvent;

import java.net.*;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class notifybot extends SubspaceBot {
    private BotAction BA;
    private BotSettings BS;
    private OperatorList oplist;
    private LinkedList<NotifyPlayer> playerlist;
    String pname;

    public notifybot(BotAction botAction) {
        super(botAction);

        BA = BotAction.getBotAction();
        BS = BA.getBotSettings();
        EventRequester req = BA.getEventRequester();
        oplist = BA.getOperatorList();

        req.request(EventRequester.MESSAGE);
        req.request(EventRequester.ARENA_JOINED);
        req.request(EventRequester.LOGGED_ON);

        playerlist = new LinkedList<NotifyPlayer>();
        pname = null;
    }

    public void handleEvent(Message event) {
        String msg = event.getMessage();
        int msgtype = event.getMessageType();
        String name = event.getMessager();
        if (name == null)
            name = BA.getPlayerName(event.getPlayerID());
        //if(name == null) name="arena";

        if (pname == null)
            pname = name;
        // BA.sendPrivateMessage(pname,name+"> "+msg);

        if ((msgtype == Message.PRIVATE_MESSAGE) || (msgtype == Message.REMOTE_PRIVATE_MESSAGE)) {
            if (oplist.isER(name)) {
                if (msg.equalsIgnoreCase("!die")) {
                    BA.sendSmartPrivateMessage(pname, name + " killed me, Disconnecting clients...");

                    BA.ipcUnSubscribe("TWNotify");

                    for (Iterator<NotifyPlayer> i = playerlist.iterator(); i.hasNext();) {
                        NotifyPlayer player = (NotifyPlayer) i.next();
                        player.send("DISCONNECT: Server shutting down.");
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {}
                    ;
                    BA.die();
                } else if (msg.equalsIgnoreCase("!help")) {
                    String[] help = { "+------------------------------------------------------+",
                            "|           Trench Wars Notify Bot Server              |", "+------------------------------------------------------+",
                            "| This is for TWN Admins only.                         |", "+------------------------------------------------------+",
                            "| !start                - Start the TWN server         |", "| !stop                 - Stop the TWN server          |",
                            "| !msg                  - Sends a standard message     |", "| !alert                - Sends an alert message       |",
                            "| !size                 - How many is online with TWN? |", "| !who                  - Show who's logged in to TWN  |",
                            "+------------------------------------------------------+" };

                    if (BA.getOperatorList().isSysop(name)) {
                        BA.smartPrivateMessageSpam(name, help);
                    }
                } else if (msg.equalsIgnoreCase("!start")) {
                    BA.sendSmartPrivateMessage(pname, "Starting the TWN server.");
                    new NotifyServer(BA, playerlist).start();
                } else if (msg.equalsIgnoreCase("!stop")) {
                    BA.sendPrivateMessage(pname, "Disconnecting the server...");
                    BA.ipcUnSubscribe("TWNotify");

                    for (Iterator<NotifyPlayer> i = playerlist.iterator(); i.hasNext();) {
                        NotifyPlayer player = (NotifyPlayer) i.next();
                        player.send("DISCONNECT: Server shutting down.");
                    }
                } else if (msg.startsWith("!send ")) {
                    String str = msg.substring(msg.indexOf(" ") + 1, msg.length());
                    BA.sendSmartPrivateMessage(pname, "Server Message sent: " + str);

                    for (Iterator<NotifyPlayer> i = playerlist.iterator(); i.hasNext();) {
                        NotifyPlayer player = (NotifyPlayer) i.next();
                        player.send(str);
                    }
                } else if (msg.startsWith("!squadmsg ")) {
                    StringTokenizer arguments = new StringTokenizer(msg.substring(10), ":");
                    String squad = arguments.nextToken();
                    String str = arguments.nextToken();

                    BA.sendSmartPrivateMessage(pname, "Server Message sent: " + "MSG:" + str);

                    for (Iterator<NotifyPlayer> i = playerlist.iterator(); i.hasNext();) {
                        NotifyPlayer player = (NotifyPlayer) i.next();
                        player.send("SQUAD:" + squad + ":" + "MSG:" + str);
                    }
                } else if (msg.startsWith("!squadalert ")) {
                    StringTokenizer arguments = new StringTokenizer(msg.substring(12), ":");
                    String squad = arguments.nextToken();
                    String str = arguments.nextToken();

                    BA.sendSmartPrivateMessage(pname, "Server Message sent: " + "MSG:" + str);

                    for (Iterator<NotifyPlayer> i = playerlist.iterator(); i.hasNext();) {
                        NotifyPlayer player = (NotifyPlayer) i.next();
                        player.send("SQUAD:" + squad + ":" + "ALERT:" + str);
                    }
                } else if (msg.startsWith("!msg ")) {
                    String str = msg.substring(msg.indexOf(" ") + 1, msg.length());
                    BA.sendSmartPrivateMessage(pname, "Server Message sent: " + "MSG:" + str);

                    for (Iterator<NotifyPlayer> i = playerlist.iterator(); i.hasNext();) {
                        NotifyPlayer player = (NotifyPlayer) i.next();
                        player.send("MSG:" + str);
                    }
                } else if (msg.startsWith("!alert ")) {
                    String str = msg.substring(msg.indexOf(" ") + 1, msg.length());
                    BA.sendSmartPrivateMessage(pname, "Server Message sent: " + "ALERT:" + str);

                    for (Iterator<NotifyPlayer> i = playerlist.iterator(); i.hasNext();) {
                        NotifyPlayer player = (NotifyPlayer) i.next();
                        player.send("ALERT:" + str);
                    }
                } else if (msg.equalsIgnoreCase("!size")) {
                    BA.sendSmartPrivateMessage(pname, "The current size of TWN users online is " + playerlist.size());
                } else if (msg.equalsIgnoreCase("!who")) {
                    String str = "Online:";
                    for (Iterator<NotifyPlayer> i = playerlist.iterator(); i.hasNext();) {
                        NotifyPlayer player = (NotifyPlayer) i.next();
                        str = str.concat(" " + player.getName() + ",");
                    }
                    BA.sendSmartPrivateMessage(pname, str);
                }
            }
        }
    }

    public void handleEvent(LoggedOn event) {
        BA.joinArena(BS.getString("arena"));
        BA.ipcSubscribe("TWNotify");
    }

    public void handleEvent(ArenaJoined event) {
        BA.setReliableKills(1);
    }

    public void handleEvent(SQLResultEvent event) {
    }

    public void handleEvent(InterProcessEvent event) {
        //other applications will send strings on the TWNotify channel
        //currently implemented:
        //PLAYER:name:MSG:text
        //PLAYER:name:ALERT:text
        //SQUAD:name:MSG:text
        //SQUAD:name:ALERT:text
        //will later have things like PLAYER:MSG:GREEN:TEXT or something

        if (!event.getChannel().equals("TWNotify") || !event.getType().equals("String"))
            return;

        String str = (String) event.getObject();
        int cut = str.indexOf(":");
        if (cut == -1)
            return;
        String type = str.substring(0, cut - 1);
        int cut2 = str.indexOf(":", cut);
        if (cut2 == -1)
            return;
        String name = str.substring(cut + 1, cut2 - 1);
        // int cut3=str.indexOf(":",cut2);
        // if(cut3 == -1) return;
        // String type2=str.substring(cut2+1,cut3-1);
        // String message=str.substring(cut3+1,str.length());
        String message = str.substring(cut2 + 1, str.length());

        for (Iterator<NotifyPlayer> i = playerlist.iterator(); i.hasNext();) {
            NotifyPlayer p = (NotifyPlayer) i.next();
            if ((type.equalsIgnoreCase("PLAYER") && p.getName().equalsIgnoreCase(name))
                    || (type.equalsIgnoreCase("SQUAD") && p.getSquad().equalsIgnoreCase(name))) {
                p.send(message);
                break;
            }
        }
    }

    private class NotifyServer extends Thread {
        private ServerSocket socket;
        private boolean running;
        private BotAction BA;
        private LinkedList<NotifyPlayer> playerlist;

        public NotifyServer(BotAction botact, LinkedList<NotifyPlayer> plist) {
            BA = botact;
            playerlist = plist;
            running = false;

            try {
                socket = new ServerSocket(Integer.decode(BS.getString("port")));
                socket.setReuseAddress(true); //to rebind after bot crash and no twcore crash
                running = true;
            } catch (IOException e) {
                BA.sendPrivateMessage(pname, "couldnt get socket");
            }

            BA.sendPrivateMessage(pname, "s1=" + socket);
        }

        public void run() {
            BA.sendPrivateMessage(pname, "running");
            while (running) {
                try {
                    new NotifyClient(socket.accept(), BA, playerlist).start();
                } catch (IOException e) {
                    BA.sendPrivateMessage(pname, "accept failed");
                }
            }

            end();
        }

        public void end() {
            BA.sendPrivateMessage(pname, "ending");
            running = false;
            try {
                socket.close();
            } catch (IOException e) {
                BA.sendPrivateMessage(pname, "close2 failed");
            }

        }

    }

    private class NotifyClient extends Thread {
        private Socket socket;
        private boolean running;
        private BotAction BA;
        private LinkedList<NotifyPlayer> playerlist;
        private LinkedList<String> queue;
        private NotifyPlayer player;

        public NotifyClient(Socket s, BotAction botact, LinkedList<NotifyPlayer> plist) {
            socket = s;
            BA = botact;
            playerlist = plist;
            queue = new LinkedList<String>();
            player = null;
            BA.sendPrivateMessage(pname, "s2=" + socket);
            running = false;
        }

        public void run() {
            BA.sendPrivateMessage(pname, "run2");
            try {
                InputStreamReader hack = new InputStreamReader(socket.getInputStream());
                BufferedReader in = new BufferedReader(hack);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                BA.sendSmartPrivateMessage(pname, "IP: " + socket.getInetAddress().getHostAddress());
                BA.sendSmartPrivateMessage(pname, "PORT: " + socket.getPort());
                BA.sendSmartPrivateMessage(pname, "HOST: " + socket.getInetAddress().getHostName());

                running = true;
                while (running) {
                    if (queue.size() > 0) {
                        String output = (String) queue.removeFirst();
                        out.printf("%s\0", output); //send one, send rest later
                        // BA.sendPrivateMessage(pname,"send: "+output);
                    }

                    if (!hack.ready())
                        continue; //to avoid block

                    String input = in.readLine();
                    if (input != null) {
                        // BA.sendPrivateMessage(pname,"recv: "+input);

                        String[] args = input.trim().split(":");

                        if (args[0] != null) {
                            if(args[0].startsWith("LOGIN"))
                                {

                                    if ((args[1] != null) && (args[2] != null)) {
                                        String name = args[1];
                                        String pw = args[2];
                                        DBPlayerData checker = new DBPlayerData(BA, "website", name);
                                        boolean success = checker.getPlayerAccountData();
                                       // m_botAction.sendSmartPrivateMessage(pname, "Test One!");

                                      /**  ResultSet result = m_botAction.SQLQuery("website", "SELECT U.*, " + "PASSWORD("
                                                + Tools.addSlashesToString(pw) + ") AS EncPW " + "FROM tblUser U"
                                                + " JOIN tblUserAccount UA ON U.fnUserID = UA.fnUserID" + " WHERE U.fcUserName = "
                                                + Tools.addSlashesToString(name) + " AND (U.fdDeleted = 0 or U.fdDeleted is null)"
                                                + " AND UA.fcPassword = PASSWORD(" + Tools.addSlashesToString(pw) + ") "
                                                + "AND U.fnUserID = UA.fnUserID");*/
                                        
                                        //m_botAction.sendSmartPrivateMessage(pname, "Test two!");
                                        //if (!result.next()) {
                                            m_botAction.sendSmartPrivateMessage(pname, "Wrong password for " + name);
                                           // m_botAction.SQLClose(result);
                                            if(!success || (success)){
                                        } else {
                                            BA.sendSmartPrivateMessage(pname, "Login from " + name);
                                            BA.sendSmartPrivateMessage(pname, "Password: " + pw);
                                            player = new NotifyPlayer(name, checker.getTeamName(), socket.getInetAddress(), socket.getPort(), queue);
                                            playerlist.add(player);
                                            queue.add("LOGINOK:" + name);
                                           // m_botAction.SQLClose(result);

                                        }
                                    } else {
                                        queue.add("LOGINBAD:Badly formatted login request.");
                                        continue;
                                    
                                    }}
                            else if(args[0].startsWith("LOGOUT"))
                                {
                                    BA.sendPrivateMessage(pname, "quitting");
                                    running = false;
                                }
                            else if(args[0].startsWith("NOOP"))
                                {
                                    //TODO: add noop
                                    queue.add("NOOP");
                                    out.printf("NOOP2\0");
                                }
                            else if(args[0].startsWith("TEST"))
                                {
                                    BA.sendPrivateMessage(pname, "TEST");
                                }
                            }
                        

                    } else {
                        BA.sendPrivateMessage(pname, "EOF");
                        running = false;
                    }
                }

                in.close();
                out.close();
                socket.close();
            } catch (Exception e) {
                BA.sendPrivateMessage(pname, "something failed");
            }

            if (player != null)
                playerlist.remove(player);
        }
    }

    private class NotifyPlayer {
        private LinkedList<String> queue;
        private InetAddress addr;
        private int port;
        private String name;
        private String squad;

        public NotifyPlayer(String n, String sq, InetAddress a, int p, LinkedList<String> list) {
            name = n;
            squad = sq;
            addr = a;
            port = p;
            queue = list;
        }

        public void send(String msg) {
            queue.add(msg);
        }

        public String getName() {
            return name;
        }

        public String getSquad() {
            return squad;
        }
    }
}
