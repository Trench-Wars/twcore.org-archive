package twcore.bots.twchat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;


import twcore.core.BotAction;
import twcore.core.BotSettings;
import twcore.core.EventRequester;
import twcore.core.SubspaceBot;
import twcore.core.events.ArenaJoined;
import twcore.core.events.LoggedOn;
import twcore.core.events.Message;
import twcore.core.util.Tools;

public class twchat extends SubspaceBot{
    
        public static BotSettings m_botSettings;         

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
        }


        /**
         * You must write an event handler for each requested event/packet.
         * This is an example of how you can handle a message event.
         */
        public void handleEvent(Message event) {
            String name = event.getMessager() != null ? event.getMessager() : m_botAction.getPlayerName(event.getPlayerID());
            if (name == null) name = "-anonymous-";
            String message = event.getMessage();
            
            if(message.equalsIgnoreCase("!signup")){
                signup(name);
                
            } else if(message.equalsIgnoreCase("!test")){
                m_botAction.sendSmartPrivateMessage(name, "Test complete, Gotten VIP.TXT");
                m_botAction.sendUnfilteredPublicMessage("*getfile vip.txt");
                
            }else if(message.equalsIgnoreCase("!help")){
                m_botAction.sendSmartPrivateMessage(name, "Hello, I'm a bot that enables you to chat online via the Trench Wars Chat App. The chat app was created by Arobas and Dezmond, made available on the web by Zazu. "
                +"This bot was created by Dezmond.");
            }



        }


        private void signup(String name) {
            m_botAction.sendUnfilteredPublicMessage("*getfile vip.txt");
            try{
                BufferedReader reader = new BufferedReader(new FileReader( "/home/bots/twcore/bin/data/vip.txt" )); 
                BufferedWriter writer = new BufferedWriter(new FileWriter( "/home/bots/twcore/bin/data/vip.txt",true ));
                    
                    writer.write("\r\n" + name);
                    
                    //writer.write("\n"+name);
   
                    reader.close(); 
                    writer.close();
                    
             m_botAction.sendUnfilteredPublicMessage("*putfile vip.txt");
             m_botAction.sendSmartPrivateMessage(name, "You have successfully signed up to TWChat!");
             Tools.printLog("Added player "+name+" to VIP.txt for TWChat");}

            catch(Exception e){
                m_botAction.sendChatMessage("Error, Cannot edit VIP.txt for "+name+" "+e);
             Tools.printStackTrace( e );}
                    
            
             }
        

        public void handleEvent(LoggedOn event) {
            m_botAction.joinArena(m_botSettings.getString("Arena"));
        }



        public void handleEvent(ArenaJoined event) {
            m_botAction.setReliableKills(1);
            m_botAction.sendUnfilteredPublicMessage("?chat=robodev");
        }

    }