package twcore.bots.staffbot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Vector;

import twcore.bots.Module;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.FileArrived;
import twcore.core.events.Message;

public class staffbot_modlist extends Module {
    
    private static final String FILE = "moderate.txt";
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/YYYY");
    
    BotAction ba;
    
    boolean DEBUG;
    String debugger;

    @Override
    public void initializeModule() {
        ba = m_botAction;
        DEBUG = true;
        debugger = "WingZero";
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.MESSAGE);
        eventRequester.request(EventRequester.FILE_ARRIVED);
    }

    @Override
    public void cancel() {
    }
    
    public void handleEvent(Message event) {
        int type = event.getMessageType();
        String msg = event.getMessage();
        if (type == Message.REMOTE_PRIVATE_MESSAGE || type == Message.PRIVATE_MESSAGE || type == Message.CHAT_MESSAGE || type == Message.PUBLIC_MESSAGE) {
            String name = ba.getPlayerName(event.getPlayerID());
            if (name == null)
                name = event.getMessager();
            if (ba.getOperatorList().isSmod(name)) {
                if (msg.equalsIgnoreCase("!modlist"))
                    cmd_modlist(name);
            }
        }
    }
    
    public void cmd_modlist(String name) {
        ba.getServerFile(FILE);
        ba.sendSmartPrivateMessage(name, "Getting mod list...");
    }
    
    
    public void handleEvent(FileArrived event) {
        String f = event.getFileName();
        debug("Received file: " + f);
        if (!f.equalsIgnoreCase(FILE))
            return;
        
        File file = new File(ba.getGeneralSettings().getString("Core Location") + "/data/" + FILE);
        try {
            FileReader fread = new FileReader(file);
            BufferedReader in = new BufferedReader(fread);
            String line;
            boolean next = false;
            Vector<String> pretext = new Vector<String>();
            while (!next && (line = in.readLine()) != null) {
                pretext.add(line);
                if (line.startsWith("-- ~~ Notes ~~:"))
                    next = true;
            }
            
            next = false;
            Vector<StaffNote> notes = new Vector<StaffNote>();
            while (!next && (line = in.readLine()) != null) {
                if (!line.startsWith("-- ~~ Event Notes ~~"))
                    notes.add(new StaffNote(line));
                else
                    next = true;
            }
            
            next = false;
            // collect event notes...

            Vector<String> postext = new Vector<String>();
            while ((line = in.readLine()) != null)
                postext.add(line);
            
            in.close();
            fread.close();
            file.delete();
            file.createNewFile();
            FileWriter fwrite = new FileWriter(file);
            BufferedWriter buff = new BufferedWriter(fwrite);
            PrintWriter out = new PrintWriter(buff);
            for (String s : pretext)
                out.println(s);
            for (StaffNote n : notes)
                out.println(n.print());
            for (String s : postext)
                out.println(s);
            out.close();
            buff.close();
            fwrite.close();
            ba.putFile(file.getPath());
            debug("Done.");
        } catch (IOException e) {
            debug("Error opening mod file!");
            e.printStackTrace();
        }
        
    }
    
    class StaffNote {
        Calendar date;
        String author;
        String note;
        String code;
        
        public StaffNote(String line) {
            code = line.substring(1, line.substring(1).indexOf("-"));
            author = line.substring(line.lastIndexOf(" -") + 1);
            if (!ba.getOperatorList().isSmod(author)) {
                //debug("Error recognizing staff member: " + author);
                note = line.substring(line.indexOf(":") + 2);
                author = "";
            } else
                note = line.substring(line.indexOf(":") + 2, line.lastIndexOf(" -"));
            String ds = line.substring(5, 15);
            date = Calendar.getInstance();
            try {
                date.setTime(sdf.parse(ds));
            } catch (ParseException e) {
                debug("Error parsing list note date!");
            }
        }
        
        public String print() {
            String c =  "-" + code + "-";
            while (c.length() < 5)
                c += " ";
            return c + sdf.format(date.getTime()) + ": " + note + (author.length() > 0 ? " -" + author : "");
        }
        
        public String getDate() {
            return sdf.format(date.getTime());
        }
        
        public String getAuthor() {
            return author;
        }
        
        public String getNote() {
            return note;
        }
        
        public String getCode() {
            return code;
        }
    }
    
    class EventNote {
        Calendar date;
        String author;
        String event;
        String staff;
        
        public EventNote(String line) {
            
        }
    }
    
    
    void debug(String msg) {
        if (DEBUG)
            ba.sendSmartPrivateMessage(debugger, "[DEBUG] " + msg);
    }

}
