package twcore.bots.notifybot;

import twcore.core.*;
import twcore.core.events.*;
import twcore.core.util.Tools;
import twcore.core.events.SQLResultEvent;

import java.net.*;
import java.io.*;
import java.sql.ResultSet;
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

    //Standard Message Event for TWCore. Commands and stuff!
    public void handleEvent(Message event) {
        String msg = event.getMessage();
        int msgtype = event.getMessageType();
        String name = event.getMessager();
        if (name == null)
            name = BA.getPlayerName(event.getPlayerID());

        if (pname == null)
            pname = name;

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
                    BA.die("!die by " + name);
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
                    System.out.println("Starting Server..");
                    //startServer();
                } else if (msg.equalsIgnoreCase("!stop")) {
                    BA.sendSmartPrivateMessage(pname, "Disconnecting the server...");
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
                    if (!(arguments.countTokens() == 2)) {
                        m_botAction.sendSmartPrivateMessage(pname, "Sucker, Learn to type the command right.");
                    } else {

                        String squad = arguments.nextToken();
                        String str = arguments.nextToken();

                        for (Iterator<NotifyPlayer> i = playerlist.iterator(); i.hasNext();) {
                            NotifyPlayer p = (NotifyPlayer) i.next();
                            if (p.getSquad().equalsIgnoreCase(squad)) {
                                p.send("MSG:" + str);
                                m_botAction.sendSmartPrivateMessage(pname, "Server Squad Broadcast Message sent: " + str);
                            }
                        }
                    }

                } else if (msg.startsWith("!squadalert ")) {
                    StringTokenizer arguments = new StringTokenizer(msg.substring(12), ":");
                    if (!(arguments.countTokens() == 2)) {
                        m_botAction.sendSmartPrivateMessage(pname, "Sucker, Learn to type the command right.");
                    } else {
                        String squad = arguments.nextToken();
                        String str = arguments.nextToken();

                        for (Iterator<NotifyPlayer> i = playerlist.iterator(); i.hasNext();) {
                            NotifyPlayer p = (NotifyPlayer) i.next();
                            if (p.getSquad().equalsIgnoreCase(squad)) {
                                p.send("ALERT:" + str);
                                m_botAction.sendSmartPrivateMessage(pname, "Server Squad Broadcast Alert sent: " + str);

                            }
                        }
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

    private void startServer() {
        new NotifyServer(BA, playerlist).start();
        System.out.println("Starting Server..");
    }

    public void handleEvent(LoggedOn event) {
        BA.joinArena(BS.getString("arena"));
        BA.ipcSubscribe("TWNotify");
        startServer();
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

    public class NotifyServer extends Thread {
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

    public class NotifyClient extends Thread {
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
            check(socket, playerlist, queue, player, running);
        }
    }

    public void check(Socket socket, LinkedList<NotifyPlayer> playerlist2, LinkedList<String> queue, NotifyPlayer player, boolean running) {
        try {
            InputStreamReader hack = new InputStreamReader(socket.getInputStream());
            BufferedReader in = new BufferedReader(hack);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            BA.sendSmartPrivateMessage(pname, "A user is connecting...");
            BA.sendSmartPrivateMessage(pname, "IP: " + socket.getInetAddress().getHostAddress());
            BA.sendSmartPrivateMessage(pname, "PORT: " + socket.getPort());
            BA.sendSmartPrivateMessage(pname, "HOST: " + socket.getInetAddress().getHostName());
            System.out.println("IP: " + socket.getInetAddress().getHostAddress());
            System.out.println("PORT: " + socket.getPort());
            System.out.println("HOST: " + socket.getInetAddress().getHostName());
            running = true;
            while (running) {
                if (queue.size() > 0) {
                    String output = (String) queue.removeFirst();
                    out.printf("%s\0", output); //send one, send rest later
                }

                if (!hack.ready())
                    continue; //to avoid block

                String input = in.readLine();
                if (input != null) {

                    String[] args = input.trim().split(":");

                    if (args[0] != null) {
                        if (args[0].startsWith("LOGIN")) {
                            if ((args[1] != null) && (args[2] != null)) {
                                String name = args[1].trim();
                                String pw = args[2];
                                m_botAction.sendSmartPrivateMessage(pname, "Received LOGIN request...authorizing...");
                                System.out.println("Login Request...");
                                
                                /*
                                 * We need to grab the user ID from the tblUser table, so we can then compare the values
                                 * We also need to encrypt the password before we query on it, as we don't want to query in plain text. 
                                 */
                                
                                //User ID query
                                ResultSet rs = m_botAction.SQLQuery("website", "SELECT fnUserID FROM tblUser WHERE fcUserName = '" + name + "'");
                                
                               
                                
                                //Encrypt the password through MySQL
                                ResultSet rspw = m_botAction.SQLQuery("website", "SELECT SHA1(UNHEX(SHA1('" + pw + "'))) AS Encrypted");
                                
                                
                                if(!rs.next() || !rspw.next()) {
                                    System.out.println("No results found!");
                                    
                                } else { 
                                    
                                String userID = rs.getString("fnUserID"); //Return the UserID
                                String encrypted = rspw.getString("Encrypted"); //Return the encrypted password
                                encrypted = "*" + encrypted.toUpperCase(); //Format the password so it matches what's stored in SQL
                                
                                
                                //The Holy Grail query! This compares the password stored in the tblUserAccount to that in which the player enters into TWN
                                ResultSet match = m_botAction.SQLQuery("website", "SELECT fcPassword FROM tblUserAccount WHERE fnUserID = '" + userID
                                        + "' AND fcPassword = '" + encrypted + "'");
                                
                                
                                
                                //If nothing is returned from the holy grail query, then we can assume the password is incorrect
                                if (!match.next()) {
                                    m_botAction.sendSmartPrivateMessage(pname, "Wrong password for " + name);
                                    out.printf("BADLOGIN:Wrong Password." + '\n');
                                    BA.SQLClose(rs);
                                    BA.SQLClose(rspw);
                                    BA.SQLClose(match);
                                
                                
                                //Wait! We have a result. The password query matched, time to get some information from the database about this player
                                } else {
                                    m_botAction.sendSmartPrivateMessage(pname, "Password Correct - getting squad..."); // Debug
                                    System.out.println("Login correct! Getting squad...");
                                    
                                    ResultSet squad = m_botAction.SQLQuery("pubstats", "SELECT fcSquad FROM tblPlayer WHERE fcName = '" + name + "'"); //Return the squad of the player
                                    
                                    m_botAction.sendSmartPrivateMessage(pname, "Pubstats query done..."); // Debug
                                    
                                    
                                   //We'll add the squads to the stats of the player. If they don't have a squad, use 'Unknown'
                                    if (squad.next() || !squad.next()) {
                                        String squads = squad.getString("fcSquad");
                                        if (squads == null)
                                            squads = "Unknown";
                                        
                                        //Debug
                                        BA.sendSmartPrivateMessage(pname, "Connected user successful login.");
                                        BA.sendSmartPrivateMessage(pname, "Login Name: " + name);
                                        BA.sendSmartPrivateMessage(pname, "Squad: " + squads);
                                        
                                        //Create the stats and notify the client about the successfull login
                                        player = new NotifyPlayer(name, squads, socket.getInetAddress(), socket.getPort(), queue);
                                        playerlist.add(player);
                                        out.printf("LOGINOK:" + name + ":" + player.getSquad() + '\n');
                                        BA.SQLClose(squad);
                                        BA.SQLClose(rs);
                                        BA.SQLClose(rspw);
                                        BA.SQLClose(match);

                                    }
                                
                                }
                                
                                BA.SQLClose(rs);
                                BA.SQLClose(rspw);
                                BA.SQLClose(match);
                                }
                            //The login request isn't right...
                            } else {
                                m_botAction.sendSmartPrivateMessage(pname, "Hacker Alert!");
                                out.printf("LOGINBAD:Badly formatted login request." + '\n');
                                
                                continue;

                            }

                        } else if (args[0].startsWith("LOGOUT")) {
                            running = false;
                            playerlist.remove(player);
                            
                        } else if (args[0].startsWith("CHECKRSPND")) {
                            System.out.println("Request from client to send info held in the queue.");
                            for(int i = 0; i < queue.size(); i++) {   
                                out.printf(queue.get(i) + '\n');
                                queue.remove(queue.get(i));
                            }  
                            
                        } else if (args[0].startsWith("TEST")) {}
                    }
               

                } else {
                    running = false;
                }
            }
            

            // We are done with the authentication process. Close the sockets.
            in.close();
            out.close();
            socket.close();
            
        } catch (Exception e) {
            BA.sendPrivateMessage(pname, "something failed");
            Tools.printStackTrace(e);
        }

        if (player != null)
            playerlist.remove(player);
    }

    public class NotifyPlayer {
        private LinkedList<String> queue;
        private InetAddress addr;
        private int port;
        public String name;
        public String squad;

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
        
        public InetAddress getAddress() {
            return addr;
        }
        
        public int getPort() {
            return port;
        }
    }
}
