package twcore.bots.twchat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;


import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.FileArrived;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.util.Tools;

public class twchat extends SubspaceBot{
    
        BotSettings m_botSettings;
        
        public ArrayList<String> lastPlayer = new ArrayList<String>();
      
        

        public twchat(BotAction botAction) {
            super(botAction);
            requestEvents();
             
            
            m_botSettings = m_botAction.getBotSettings();
                   
        }



        public void requestEvents() {
            EventRequester req = m_botAction.getEventRequester();
            req.request(EventRequester.MESSAGE);
            req.request(EventRequester.ARENA_JOINED);
            req.request(EventRequester.LOGGED_ON);
            req.request(EventRequester.FILE_ARRIVED);
        }


        /**
         * You must write an event handler for each requested event/packet.
         * This is an example of how you can handle a message event.
         */
        public void handleEvent(Message event) {
            short sender = event.getPlayerID();
            String name = event.getMessageType() == Message.REMOTE_PRIVATE_MESSAGE ? event.getMessager() :  m_botAction.getPlayerName(sender);
            String message = event.getMessage();
            
            if(message.equalsIgnoreCase("!signup")){
                m_botAction.getServerFile("vip.txt");
                name = name.toLowerCase();
                    lastPlayer.add(name);
           
                
            } else if(message.equalsIgnoreCase("!test") && m_botAction.getOperatorList().isDeveloper(name)){
                m_botAction.sendSmartPrivateMessage(name, "Test complete, Gotten VIP.TXT");
                m_botAction.getServerFile("vip.txt");
                
            }else if(message.startsWith("!vipadd ")){
                m_botAction.getServerFile("vip.txt");
                String msg = message.substring(8).toLowerCase();
                lastPlayer.add(msg);
                m_botAction.sendSmartPrivateMessage(name, "Done.");
                
            }else if(message.equalsIgnoreCase("!help")){
                String[] startCommands = 
                {   "+-------------------------------------------------------------------------------+",
                    "|                                 Trench Wars Chat                              |",  
                    "|                                                                               |",
                    "| Hello! I'm a bot that will allow you to chat on the web!                      |",
                    "| Please look below for the available commands.                                 |"
                };
                String[] publicCommands = 
                {   "|                                                                               |",
                    "| !signup                     - Signs you up to be able to use the online TW    |",
                    "|                               Chat App                                        |", };
                String[] modCommands = 
                {   "|------------------------------- Developer+ ------------------------------------|",
                    "| !test                       - Retrieves the VIP text file from the server to  |",
                    "|                               be accurate where it is placed.                 |",
                    "| !die                        - Throw me off a bridge without a parachute       |",
                    "| !vipadd                     - Manually add this person to VIP.                |",
                    };
                String[] endCommands =
                {   "\\-------------------------------------------------------------------------------/"   };
                
                m_botAction.smartPrivateMessageSpam(name, startCommands);
                m_botAction.smartPrivateMessageSpam(name, publicCommands);
                
                if(m_botAction.getOperatorList().isDeveloper(name)){
                    m_botAction.smartPrivateMessageSpam(name, modCommands);}
                
                m_botAction.smartPrivateMessageSpam(name, endCommands);


                
            }else if(message.equalsIgnoreCase("!die") && m_botAction.getOperatorList().isDeveloper(name)){
                m_botAction.die();
            }
            



        }


       public void handleEvent( FileArrived event ){
            for (int i = 0; i < lastPlayer.size(); i++) {
            if( event.getFileName().equals( "vip.txt" )){
                try{
                    BufferedReader reader = new BufferedReader(new FileReader( m_botAction.getDataFile("vip.txt") )); 
                    BufferedWriter writer = new BufferedWriter(new FileWriter( m_botAction.getDataFile("vip.txt"),true ));
                        
                        reader.readLine();
                        writer.write("\r\n"+lastPlayer.get(i));
                        
                        //writer.write("\n"+name);
       
                        reader.close(); 
                        writer.close();
                        
                 m_botAction.putFile("vip.txt");
                 m_botAction.sendSmartPrivateMessage(lastPlayer.get(i), "You have successfully signed up to TWChat!");
                 Tools.printLog("Added player "+lastPlayer.get(i)+" to VIP.txt for TWChat");
                 m_botAction.sendChatMessage("Good Day, I have added "+lastPlayer.get(i)+ " to VIP for TWChat.");
                 lastPlayer.remove(i);
                 }

                catch(Exception e){
                    m_botAction.sendChatMessage("Error, Cannot edit VIP.txt for "+lastPlayer.get(i)+" "+e);
                 Tools.printStackTrace( e );}
                
            }}
        }
        

        public void handleEvent(LoggedOn event) {
            m_botAction.joinArena(m_botSettings.getString("Arena"));
        }



        public void handleEvent(ArenaJoined event) {
            m_botAction.setReliableKills(1);
            m_botAction.sendUnfilteredPublicMessage("?chat=robodev");
        }

    }