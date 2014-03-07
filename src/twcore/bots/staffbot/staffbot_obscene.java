/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package twcore.bots.staffbot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;

import twcore.bots.Module;
import twcore.core.EventRequester;
import twcore.core.events.FileArrived;
import twcore.core.events.Message;
import twcore.core.util.Tools;

/**
 *
 * @author Trancid
 */
public class staffbot_obscene extends Module {
    
    private static enum State {
        FREE,
        ADDING,
        LISTING,
        REMOVING
    };
    
    private volatile State m_state = State.FREE;
    
    private HashSet<String> bangops = new HashSet<String>();
    
    private String m_user;
    private String m_word;
    
    @Override
    public void initializeModule() {
        updateBangOps();
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.FILE_ARRIVED);
        eventRequester.request(EventRequester.MESSAGE);
    }

    @Override
    public void cancel() {

    }

    public void handleEvent(FileArrived event) {
        if(!event.getFileName().equals("obscene.txt"))
            return;
        
        if(m_state.equals(State.LISTING)) {
            listWords();
        } else if(m_state.equals(State.REMOVING)) {
            removeWord();
        }
    }
        
    @Override
    public void handleEvent(Message event) {
        if(event.getMessageType() != Message.PRIVATE_MESSAGE
                && event.getMessageType() != Message.REMOTE_PRIVATE_MESSAGE)
            return;
        
        String name = event.getMessager();
        String message = event.getMessage().trim().toLowerCase();
        String params = "";
        
        if (name == null) {
            name = m_botAction.getPlayerName(event.getPlayerID());
        }
        
        if(message == null || message.isEmpty() || name == null || name.isEmpty()) {
            return;
        }
        
        if(message.contains(" ")) {
            String splitMsg[] = event.getMessage().split(" ", 2);
            message = splitMsg[0].toLowerCase();
            if(splitMsg.length > 1) {
                params = splitMsg[1].trim();
            }
        }

        if (m_botAction.getOperatorList().isSmod(name)) {
            if (message.equals("!help")) {
                cmd_help(name);
            } else if (message.equals("!listobscene")) {
                cmd_listobscene(name);
            } else if(m_botAction.getOperatorList().isSysop(name) || isBangOp(name)) {
                if(message.equals("!addobscene")) {
                    cmd_addobscene(name, params);
                } else if(message.equals("!remobscene")) {
                    cmd_remobscene(name, params);
                }
            }
        }
    }

    /**
     * Adds a word to the server's obscene.txt. (Sysop+)
     * @param name Issuer of command.
     * @param param Specific word.
     */
    public void cmd_addobscene(String name, String param) {
        if(param == null || param.isEmpty()) {
            m_botAction.sendSmartPrivateMessage(name, "Please use the correct syntax: !addobscene <word>");
            return;
        }
        
        if(!m_state.equals(State.FREE)) {
            m_botAction.sendSmartPrivateMessage(name, "I'm currently in use. Try again later.");
            return;
        } else {
            m_state = State.ADDING;
        }
        
        m_botAction.sendUnfilteredPublicMessage("*addword " + param);
        m_botAction.sendSmartPrivateMessage(name, "Added " + param + " to obscene.txt.");
        
        recordChange("[" + m_user + "] added [" + m_word + "].");
        
        m_state = State.FREE;
    }
    
    /**
     * Help display. (SMod+)
     * @param name Sender of command.
     */
    public void cmd_help(String name) {
        if(m_botAction.getOperatorList().isSysop(name) || isBangOp(name)) {
            String[] spam = {
                    "----------------[Obscene: Sysop ]-----------------",
                    " !addobscene <word>        - Adds a specific entry to obscene.txt.",
                    " !listobscene              - Lists the current entries in obscene.txt.",
                    " !remobscene <word>        - Removes a specific entry from obscene.txt."
            };
            m_botAction.smartPrivateMessageSpam(name, spam);
        } else if(m_botAction.getOperatorList().isSmodExact(name)) {
                String[] spam = {
                        "----------------[Obscene: SMod ]-----------------",
                        " !listobscene              - Lists the current entries in obscene.txt.",
                };
                m_botAction.smartPrivateMessageSpam(name, spam);
            }
    }
    
    /**
     * Lists the contents of obscene.txt. (SMod+)
     * @param name Issuer of the command.
     */
    public void cmd_listobscene(String name) {
        if(!m_state.equals(State.FREE)) {
            m_botAction.sendSmartPrivateMessage(name, "I'm currently in use. Try again later.");
            return;
        } else {
            m_state = State.LISTING;
        }
        
        m_user = name;
        
        m_botAction.getServerFile("obscene.txt");
        m_botAction.sendSmartPrivateMessage(name, "Retreiving data. One moment please...");
    }

    /**
     * Removes an entry from obscene.txt. (Sysop+)
     * @param name Issuer of the command.
     * @param param Case sensitive entry that needs to be removed.
     */
    public void cmd_remobscene(String name, String param) {
        if(param == null || param.isEmpty()) {
            m_botAction.sendSmartPrivateMessage(name, "Please use the correct syntax: !remobscene <word>");
            return;            
        }
        
        if(!m_state.equals(State.FREE)) {
            m_botAction.sendSmartPrivateMessage(name, "I'm currently in use. Try again later.");
            return;
        } else {
            m_state = State.REMOVING;
        }
        
        m_user = name;
        m_word = param;
        
        m_botAction.getServerFile("obscene.txt");
        m_botAction.sendSmartPrivateMessage(name, "Retreiving data. One moment please...");
    }
    
    /**
     * Loads the received obscene.txt and displays it to the user.
     */
    private void listWords() {
        
        try {
            File file = m_botAction.getDataFile("obscene.txt");
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            
            ArrayList<String> obsceneList = new ArrayList<String>();
            
            String line;
            int maxLen = 0;
            
            while ((line = br.readLine()) != null) {
                obsceneList.add("[" + line + "] ");
                int len = line.length() + 3;
                if(len > maxLen) {
                    maxLen = len;
                }
            }
            
            br.close();
            fr.close();
            
            m_botAction.sendSmartPrivateMessage(m_user, "Current contents of obscene.txt:");
            if(obsceneList != null && obsceneList.size() > 0) {
                int size = obsceneList.size();
                int columns = 100 / maxLen; 
                int rows = obsceneList.size() / columns + 1;
                for(int i = 1; i < columns; i++) {
                    for(int j = 0; j < rows && rows < obsceneList.size(); j++) {
                        obsceneList.set(j, Tools.formatString(obsceneList.get(j), maxLen).concat(obsceneList.remove(rows)));
                    }
                }
                String[] spam = obsceneList.toArray(new String[obsceneList.size()]);
                m_botAction.smartPrivateMessageSpam(m_user, spam);
                m_botAction.sendSmartPrivateMessage(m_user, "Displayed " + size + " entries.");
            } else {
                m_botAction.sendSmartPrivateMessage(m_user, "Displayed 0 entries.");
            }

        } catch (NullPointerException npe) {
            Tools.printStackTrace(npe);
            m_botAction.sendSmartPrivateMessage(m_user, "Incorrect path name.");
        } catch (SecurityException se) {
            Tools.printStackTrace(se);
            m_botAction.sendSmartPrivateMessage(m_user, "Access denied to file.");
        } catch (FileNotFoundException fnfe) {
            Tools.printStackTrace(fnfe);
            m_botAction.sendSmartPrivateMessage(m_user, "File not found.");
        } catch (IOException ioe) {
            Tools.printStackTrace(ioe);
            m_botAction.sendSmartPrivateMessage(m_user, "Unable to read from file.");
        } finally {
            m_state = State.FREE;
        }
    }
    
    /**
     * Reads in the obscene.txt, removes the requested entry if possible and uploads the file
     * back to the server.
     */
    private void removeWord() {
        try {
            File file = m_botAction.getDataFile("obscene.txt");
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            
            ArrayList<String> obsceneList = new ArrayList<String>();
            
            String line;
            int maxLen = 0;
            
            while ((line = br.readLine()) != null) {
                obsceneList.add(line);
                int len = line.length();
                if(len > maxLen) {
                    maxLen = len;
                }
            }
            
            br.close();
            fr.close();
            
            if(obsceneList == null || obsceneList.isEmpty() || !obsceneList.remove(m_word)) {
                m_botAction.sendSmartPrivateMessage(m_user, m_word + " is not present in obscene.txt.");
            } else {
                file.delete();
                file.createNewFile();
                
                FileWriter fw = new FileWriter(file);
                BufferedWriter bw = new BufferedWriter(fw);
                
                // Special check in case we removed the final word.
                if(obsceneList != null && !obsceneList.isEmpty()) {
                    // Keeping first entry outside to prevent extra line feed.
                    bw.write(obsceneList.remove(0));
                    while(obsceneList.size() > 0) {
                        bw.newLine();
                        bw.write(obsceneList.remove(0));
                    }
                }
                
                bw.flush();
                bw.close();
                fw.close();
                
                m_botAction.putFile("obscene.txt");
                
                m_botAction.sendSmartPrivateMessage(m_user, "Successfully removed " + m_word + " from obscene.txt.");
                
                recordChange("[" + m_user + "] removed [" + m_word + "].");
            }

        } catch (NullPointerException npe) {
            Tools.printStackTrace(npe);
            m_botAction.sendSmartPrivateMessage(m_user, "Incorrect path name.");
        } catch (SecurityException se) {
            Tools.printStackTrace(se);
            m_botAction.sendSmartPrivateMessage(m_user, "Access denied to file.");
        } catch (FileNotFoundException fnfe) {
            Tools.printStackTrace(fnfe);
            m_botAction.sendSmartPrivateMessage(m_user, "File not found.");
        } catch (IOException ioe) {
            Tools.printStackTrace(ioe);
            m_botAction.sendSmartPrivateMessage(m_user, "Unable to read from file.");
        } finally {
            m_state = State.FREE;
        }
    }
    
    /**
     * Loads the BanGOp list from the database.
     */
    private void updateBangOps() {
        try {
            ResultSet r = m_botAction.SQLQuery("website", "SELECT tblUser.fcUsername FROM `tblUserRank`, `tblUser` WHERE `fnRankID` = '31' AND tblUser.fnUserID = tblUserRank.fnUserID");
            if (r == null)
                return;

            bangops.clear();

            while (r.next()) {
                String name = r.getString("fcUsername");
                bangops.add(name.toLowerCase());
            }
            m_botAction.SQLClose(r);
        } catch (SQLException sqle) {
            Tools.printStackTrace(sqle);;
        }
    }
    
    /**
     * Checks whether the person is a BanGOp
     * @param name
     * @return
     */
    private boolean isBangOp(String name) {
        if (name==null || bangops==null)
            return false;
        return bangops.contains(name.toLowerCase());
    }
    
    /**
     * Records any changes made to the obscene list.
     * @param message Message to be logged.
     */
    private void recordChange(String message) {
        try {
            Calendar c = Calendar.getInstance();
            String timestamp = c.get(Calendar.MONTH) + "/" + c.get(Calendar.DAY_OF_MONTH) + "/" + c.get(Calendar.YEAR) + " - ";

            BufferedWriter writer = new BufferedWriter(new FileWriter("/home/bots/twcore/bin/logs/obscene.log", true));

            writer.write("\r\n" + timestamp + " - " + message);

            writer.close();

        } catch (Exception e) {
            Tools.printStackTrace(e);
        }

    }
}
