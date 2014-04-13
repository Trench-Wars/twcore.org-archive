package twcore.bots.pubbot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import twcore.bots.PubBotModule;
import twcore.core.EventRequester;
import twcore.core.command.CommandInterpreter;
import twcore.core.events.FrequencyChange;
import twcore.core.events.FrequencyShipChange;
import twcore.core.events.Message;
import twcore.core.events.PlayerEntered;
import twcore.core.events.PlayerLeft;

public class pubbotpure extends PubBotModule {
    Map<String, Integer> m_playerList;
    int                 m_freqOneSize;
    int                 m_freqZeroSize;
    private boolean     m_levisAllowed;
    CommandInterpreter  m_commandInterpreter;
    private boolean     m_privateFreqsAllowed;

    public void initializeModule(){
        m_freqOneSize = 0;
        m_freqZeroSize = 0;
        m_levisAllowed = true;
        m_privateFreqsAllowed = true;

        m_commandInterpreter = new CommandInterpreter( m_botAction );
        registerCommands();

        m_playerList = Collections.synchronizedMap( new HashMap<String, Integer>() );
    }

    public void cancel(){
    }

    void registerCommands(){
        int         acceptedMessages;

        acceptedMessages = Message.REMOTE_PRIVATE_MESSAGE | Message.PRIVATE_MESSAGE;

        m_commandInterpreter.registerCommand( "!privfreqs", acceptedMessages, this, "handlePrivFreqs" );
        m_commandInterpreter.registerCommand( "!levis", acceptedMessages, this, "handleLevisAllowed" );
        m_commandInterpreter.registerCommand( "!help", acceptedMessages, this, "handleHelp" );
    }

    public void requestEvents( EventRequester eventRequester ){
        eventRequester.request( EventRequester.MESSAGE );
        eventRequester.request( EventRequester.PLAYER_LEFT );
        eventRequester.request( EventRequester.PLAYER_ENTERED );
        eventRequester.request( EventRequester.FREQUENCY_CHANGE );
        eventRequester.request( EventRequester.FREQUENCY_SHIP_CHANGE );
    }

    public void handleEvent( Message event ){

        m_commandInterpreter.handleEvent( event );
    }

    public void handleEvent( PlayerEntered event ){
        int         team = event.getTeam();

        if( team == 0 ){
            m_freqZeroSize++;
        } else if( team == 1 ){
            m_freqOneSize++;
        }

        if( m_privateFreqsAllowed == false && team > 1 ){
            changePlayerFreq( event.getPlayerName() );
        } else {
            m_playerList.put( event.getPlayerName().toLowerCase(), new Integer( team ) );
        }

        if( m_levisAllowed == false ){
            if( event.getShipType() == 4 ){
                m_botAction.sendPrivateMessage( event.getPlayerName(), "Levis are not currently allowed in this arena." );
                m_botAction.setShip( event.getPlayerName(), 8 );
            }
        }
    }

    public void handleEvent( PlayerLeft event ){
        String          name;
        Integer         frequency;

        name = m_botAction.getPlayerName( event.getPlayerID() );
        frequency = m_playerList.remove( name.toLowerCase() );

        if( frequency.intValue() == 0 ){
            m_freqZeroSize--;
        } else if( frequency.intValue() == 1 ){
            m_freqOneSize--;
        }
    }

    public void handleEvent( FrequencyChange event ){
        String          name;
        Integer         oldFrequency;
        int             newFrequency = event.getFrequency();

        name = m_botAction.getPlayerName( event.getPlayerID() );
        oldFrequency = m_playerList.remove( name.toLowerCase() );

        if( oldFrequency.intValue() == 0 ){
            m_freqZeroSize--;
        } else if( oldFrequency.intValue() == 1 ){
            m_freqOneSize--;
        }

        if( newFrequency == 0 ){
            m_freqZeroSize++;
        } else if( newFrequency == 1 ){
            m_freqOneSize++;
        }

        if( m_privateFreqsAllowed == false && newFrequency > 1 ){
            changePlayerFreq( name );
        } else {
            m_playerList.put( name.toLowerCase(), new Integer( newFrequency ) );
        }
    }

    public void handleEvent( FrequencyShipChange event ){
        String          name;
        Integer         oldFrequency;
        int             newFrequency = event.getFrequency();

        name = m_botAction.getPlayerName( event.getPlayerID() );
        oldFrequency = m_playerList.remove( name.toLowerCase() );

        if( oldFrequency.intValue() == 0 ){
            m_freqZeroSize--;
        } else if( oldFrequency.intValue() == 1 ){
            m_freqOneSize--;
        }

        if( newFrequency == 0 ){
            m_freqZeroSize++;
        } else if( newFrequency == 1 ){
            m_freqOneSize++;
        }

        if( m_privateFreqsAllowed == false ){
            changePlayerFreq( name );
        } else {
            m_playerList.put( name.toLowerCase(), new Integer( newFrequency ) );
        }

        if( m_levisAllowed == false ){
            if( event.getShipType() == 4 ){
                m_botAction.sendPrivateMessage( name, "Levis are not currently allowed in this arena." );
                m_botAction.setShip( name, 8 );
            }
        }
    }

    public void handlePrivFreqs( String playerName, String message ){

        if( opList.isHighmod( playerName ) ){
            if( m_privateFreqsAllowed == true ){
                m_privateFreqsAllowed = false;
                m_botAction.sendRemotePrivateMessage( playerName, "Private frequencies are no longer allowed." );
            } else {
                m_privateFreqsAllowed = true;
                m_botAction.sendRemotePrivateMessage( playerName, "Private frequencies are now allowed." );
            }
        }
    }

    public void changePlayerFreq( String name ){

        if( m_freqZeroSize > m_freqOneSize ){
            m_botAction.setFreq( name, 1 );
            m_playerList.put( name.toLowerCase(), new Integer( 1 ) );
        } else {
            m_botAction.setFreq( name, 0 );
            m_playerList.put( name.toLowerCase(), new Integer( 0 ) );
        }
    }

    public void handleLevisAllowed( String playerName, String message ){

        if( opList.isHighmod( playerName ) ){
            if( m_levisAllowed == true ){
                m_levisAllowed = false;
                m_botAction.sendRemotePrivateMessage( playerName, "Levis are no longer allowed." );
            } else {
                m_levisAllowed = true;
                m_botAction.sendRemotePrivateMessage( playerName, "Levis are now allowed." );
            }
        }
    }

    public void handleHelp( String playerName, String message ){

        if( opList.isHighmod( playerName ) ){
            m_botAction.sendRemotePrivateMessage( playerName, "----------- Pure pub settings -----------" );
            m_botAction.sendRemotePrivateMessage( playerName, "!privfreqs - Toggle whether private freqs are allowed or not." );
            m_botAction.sendRemotePrivateMessage( playerName, "!levis - Toggle whether levis are allowed or not." );
        }
    }
}

