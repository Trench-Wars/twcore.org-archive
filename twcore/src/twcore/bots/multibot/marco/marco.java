package twcore.bots.multibot.marco;

import static twcore.core.EventRequester.FREQUENCY_SHIP_CHANGE;
import static twcore.core.EventRequester.PLAYER_LEFT;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

import twcore.bots.MultiModule;
import twcore.core.util.ModuleEventRequester;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerLeft;
import twcore.core.game.Player;

/**
    MultiBot module that hosts the game Marco Polo.
    The host adds words to the list and when the host says marco
    the players need to say a word that is randomly chosen to
    get their cloak back.  Alternately the host can provide the words
    on the fly, but they should then PM to the bot instead of using pubchat.
    @author Jacen Solo / modified by qan
    @version 1.0
*/
public class marco extends MultiModule
{
    HashSet <String>deCloaked = new HashSet<String>();    //List of people that have not said the word to get their cloak back
    ArrayList <String>wordList = new ArrayList<String>(); //List of words that are randomly chosen as 'polo'
    String currentWord;                   //Current 'polo' word
    boolean isRunning = false;            //boolean variable to determine if the marco mode is activated
    Random rand = new Random();           //Random object to pick the 'polo' word

    /**
        Creates a new instance of the module when it is loaded.
        and adds polo to wordList.
    */
    public void init()
    {
        wordList.add("polo");
    }

    public void requestEvents(ModuleEventRequester events)
    {
        events.request(this, PLAYER_LEFT);
        events.request(this, FREQUENCY_SHIP_CHANGE);
    }

    /**
        Handles a message event, if the person is a ER+
        it calls the handleCommand method, if the person
        is ZH or lower, it calls the handlePublicCommand.
        @param event event is the Message object.
    */
    public void handleEvent(Message event)
    {
        String message = event.getMessage();

        if(event.getMessageType() == Message.PRIVATE_MESSAGE || event.getMessageType() == Message.PUBLIC_MESSAGE)
        {
            String name = m_botAction.getPlayerName(event.getPlayerID());

            if(opList.isER(name))
                handleCommand(name, message);
            else
                handlePublicCommand(name, message);
        }
    }

    /**
        Remove a person from the deCloaked list if they
        leave the arena so the last person to say the word
        will still end up cloakless.
        @param event event is the PlayerLeft object generated by a person leaving.
    */
    public void handleEvent(PlayerLeft event)
    {
        if(deCloaked.contains(m_botAction.getPlayerName(event.getPlayerID())))
            deCloaked.remove(m_botAction.getPlayerName(event.getPlayerID()));
    }

    /**
        Remove a person from the deCloaked list if they
        spec so the last person to say the word will still
        end up cloakless.
        @param event event is the FrequencyShipChange object generated by a changing ship or frequency.
    */
    public void handleEvent(FrequencyShipChange event)
    {
        if(deCloaked.contains(m_botAction.getPlayerName(event.getPlayerID())) && event.getShipType() == 0)
            deCloaked.remove(m_botAction.getPlayerName(event.getPlayerID()));
    }

    /**
        Handles commands from a ER+ to run the Marco Polo game
        @param name name is the person that sent the command.
        @param message is the message the person sent so the method can determine what the command is.
    */
    public void handleCommand(String name, String message)
    {
        if(message.toLowerCase().startsWith("!onoff"))
            toggleRunning();
        else if(message.toLowerCase().startsWith("marco ")) {
            String pieces[] = message.split(" ");

            if( pieces.length == 2 )
                do_marco( pieces[1].toLowerCase() );
        } else if(message.toLowerCase().startsWith("marco"))
            do_marco( null );
        else if(message.toLowerCase().startsWith("!addword "))
            addWord(name, message, false);
        else if(message.toLowerCase().startsWith("!delword "))
            deleteWord(name, message);
        else if(message.toLowerCase().startsWith("!words"))
            displayWords(name);
        else if(message.toLowerCase().startsWith("!clear"))
            clearWords(name);
        else if(message.toLowerCase().startsWith("!url "))
            getWordList(name, message);
        else
            handlePublicCommand(name, message);
    }

    /**
        Checks to see if the person said the right word and if they are cloakless.
        if both are true it gives the person their cloak back.
        @param name name is the name of the person that sent the message
        @param message message is the message the person sent.
    */
    public void handlePublicCommand(String name, String message)
    {
        if(message.toLowerCase().equals(currentWord) && deCloaked.contains(name) && isRunning)
        {
            deCloaked.remove(name);
            m_botAction.sendUnfilteredPrivateMessage(name, "*prize #5");

            if(deCloaked.isEmpty())
                m_botAction.sendUnfilteredPrivateMessage(name, "*prize #-5");
        }
    }

    /**
        Toggles the mode of Marco Polo, if it is running
        when the method is called it will turn it off.
        If it isn't running when the method is called it
        will turn it on.
    */
    public void toggleRunning()
    {
        if(!isRunning)
        {
            isRunning = true;
            m_botAction.sendArenaMessage("Marco Polo has begun.  When the host says marco, all cloaks are dropped, and a word is given.", 19);
            m_botAction.sendArenaMessage("Last person to say the word does not get their cloak back!  Good luck.");
        }
        else
        {
            isRunning = false;
            m_botAction.sendArenaMessage("Marco Polo mode deactivated.");
        }
    }

    /**
        pm's the person that sent the !words message
        with the list of words and the index of the
        word.
        @param name name is the name of the person that requested the words.
    */
    public void displayWords(String name)
    {
        for(int k = 0; k < wordList.size(); k++)
            m_botAction.sendPrivateMessage(name, k + ". " + wordList.get(k));
    }

    /**
        Checks to see if wordList has the specified index
        and deletes the word at that index.
        @param name name of the person that requested the word deletion.
        @param message message is the message the person sent.
    */
    public void deleteWord(String name, String message)
    {
        String pieces[] = message.split(" ");
        int num = 500;

        try {
            num = Integer.parseInt(pieces[1]);
        } catch(Exception e) {}

        if(num < wordList.size())
        {
            m_botAction.sendPrivateMessage(name, wordList.get(num) + " has been removed from the word list.");
            wordList.remove(num);

            if(wordList.isEmpty())
                wordList.add("polo");
        }
        else
            m_botAction.sendPrivateMessage(name, "No word at that index.");
    }

    /**
        Adds a word to wordList
        @param name name of the person that sent the request to add a word.
        @param message message the person sent.
        @param fromURL determines if the word was sent from the getWordList method so it gets the right part of the message.
    */
    public void addWord(String name, String message, boolean fromURL)
    {
        int pieceNumber = 1;

        if(fromURL)
            pieceNumber = 0;

        String pieces[] = message.split(" ");

        if(pieces[pieceNumber].length() > 8)
            pieces[pieceNumber] = pieces[1].substring(0, 7);

        wordList.add(pieces[pieceNumber]);
        m_botAction.sendPrivateMessage(name, pieces[pieceNumber] + " has been added to the word list.");
    }

    /**
        Clears wordList
        @param name name of the person that requested the wordList.clear()
    */
    public void clearWords(String name)
    {
        wordList.clear();
        wordList.add("polo");
        m_botAction.sendPrivateMessage(name, "Word list cleared.");
    }

    /**
        Gets words from a txt file on the internet.
        @param name name of the person that sent the request to add the list of words.
        @param message message the person sent.
    */
    public void getWordList(String name, String message)
    {
        try {
            String pieces[] = message.split(" ");
            URL site = new URL(pieces[1]);

            if(site.getFile().toLowerCase().endsWith("txt"))
            {
                URLConnection file = site.openConnection();
                file.connect();
                BufferedReader getWordz = new BufferedReader(new InputStreamReader(file.getInputStream()));
                String nextWord;

                while((nextWord = getWordz.readLine()) != null)
                    addWord(name, nextWord, true);

                file.getInputStream().close();
            }
        } catch(IOException e) {
            e.printStackTrace(System.out);
        }
    }

    /**
        Takes away everyone's cloaks,
        adds everyone playing to the deCloaked list,
        and picks a random word from wordList to be the 'polo' word.
    */
    public void do_marco( String word )
    {
        if(isRunning)
        {
            deCloaked.clear();

            if( word == null ) {
                int num = wordList.size();

                if(num == 1)
                    num = 0;
                else
                    num = rand.nextInt(num);

                currentWord = String.valueOf(wordList.get(num));
            } else
                currentWord = word;

            m_botAction.sendArenaMessage("MARCO!  Say " + currentWord + " to get you cloak back!!!", 13);
            m_botAction.sendUnfilteredPublicMessage("*prize #-5");
            Iterator<Player> it = m_botAction.getPlayingPlayerIterator();

            while(it.hasNext())
            {
                Player p = (Player)it.next();
                deCloaked.add(p.getPlayerName());
            }
        }
    }

    /**
        Returns the help message for the marco module
    */
    public String[] getModHelpMessage()
    {
        String help[] = {
            "Macro - players lose cloak avility and must say a word to get it back.",
            "NOTE: If you use marco <word>, you should probably PM it rather than enter in chat.",
            "!onoff           - Toggles the mode on and off",
            "marco <word>     - Makes people say <word> to get cloaks back (no list needed).",
            "marco            - Makes people say a word in wordlist get their cloaks back.",
            "!addword <word>  - Adds a word to the 'polo' list",
            "!delword <#>     - Deletes the word at the specified index from the 'polo' list.",
            "!clear           - Deletes all words except polo",
            "!words           - Displays the words in the 'polo' list.",
            "!url <url>       - Gets the words from txt file at <url>."
        };
        return help;
    }

    public boolean isUnloadable() {
        return true;
    }

    public void cancel()
    {
    }
}