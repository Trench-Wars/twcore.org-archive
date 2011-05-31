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
                
            }else if(message.equalsIgnoreCase("!help")){
                m_botAction.sendSmartPrivateMessage(name, "Hello, I'm a bot that enables you to chat online via the Trench Wars Chat App. The chat app was created by Arobas and Dezmond, made available on the web by Zazu. "
                +"This bot was created by Dezmond.");
                m_botAction.sendSmartPrivateMessage(name, "Available Commands: !signup");
                if(m_botAction.getOperatorList().isDeveloper(name)){
                    m_botAction.sendSmartPrivateMessage(name, "" +
                    		"Available Commands (Developer): !die       !test");
                }
                
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