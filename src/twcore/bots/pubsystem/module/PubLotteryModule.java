package twcore.bots.pubsystem.module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TimerTask;
import twcore.bots.pubsystem.PubContext;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.util.Tools;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.bots.pubsystem.module.PubPlayerManagerModule;



/*
 * By Eria
 */

public class PubLotteryModule extends AbstractModule {
	//guess variables
	public HashMap<String, Integer> playerGuesses;
	public LinkedList<String> gWinningPlayers;
	public int gTicketPrice;
	public int gJackpot;
	public int gWinningNumber;
	public Random r;
	public boolean guessOn;
	public String startingMessage;
	public PubPlayer p;
	PubPlayerManagerModule manager;
	
	//lottery variables
    private int ticketSize = 2; // amount of entry numbers per ticket; default is 2
    private int numberMax = 50; // interval of number options; default is 1-50
    private int jackpot;  // default jackpot is $1000; if you change this value, also change it in resetJackpot()
    private int price; // default ticket price is $100; if you change this value, also change it in resetPrice()
    private int entries; // counts the number of lottery tickets submitted
    private int[] winningNum = new int[ticketSize]; // lottery's winning numbers    
    private String matchingNumbers; // change 'store' to display?  // stores and displays the player's numbers that match the winning numbers    
    private boolean lotteryOn; // status for lottery; true = enabled, false = disabled
    
    
    /* TODO
     * - Price checks
     * - When to start/stop lottery -- must find a place to do startLottery() then will create winning numbers
     * - Disable commands such as !lprice and !lottery if lottery is not enabled. [methods to do this is commented]
     * - If player does not have enough money, tell player how much it costs
     * - lotteryOn if neccessary > enables/disables this lottery game
     */
    
    public PubLotteryModule(BotAction botAction, PubContext context) {
    	super(botAction, context, "Lottery");
    	
    	//guess
    	playerGuesses = new HashMap<String, Integer>();
    	gWinningPlayers = new LinkedList<String>();
    	gTicketPrice = 500;
    	gJackpot = 10000;
    	gWinningNumber = -1;
    	r = new Random();
    	guessOn = false;
    	startingMessage = "LOTTERY is starting! To buy a number, PM me with \"!guess #\", where # is " +
    					  "an integer between 0 and 100. -" + m_botAction.getBotName();
    	manager = context.getPlayerManager();
    	
    	//lottery
    	jackpot = 1000;
        price = 100;
        entries = 0;
        lotteryOn = false;      

    }
    
    
	public void requestEvents(EventRequester eventRequester)
	{
		
	}
    
    /**
     * Checks the player's submitted ticket.
     * @param sender
     * @param command
     */
    public void handleTicket(String sender, String command) {
        int matches = 0;
        String matchingNum = null;
        String[] entry = command.substring(command.indexOf(" ")+1).split(" ");
        
        matchingNumbers = "";
        try {
            if(this.isValid(sender, entry))
                return;
            if(entry.length != ticketSize) {
                m_botAction.sendSmartPrivateMessage(sender, "Please submit a ticket with two numbers.");
                return;
            }
            
            for( int i=0; i<ticketSize; i++ ) {
                for( int j=0; j<ticketSize; j++ )
                    if(Integer.parseInt(entry[i]) == winningNum[j] ) {
                        matchingNum = entry[i];
                        matches++;
    
                        this.saveLuckyNumbers(matchingNum);
                    }
            }
            
            entries++;
            this.raiseJackpot();
            this.checkLottery(sender, matches);
            
        } catch(Exception e) {
            m_botAction.sendSmartPrivateMessage(sender, "Submission denied; Must contain "+ticketSize+" unique numbers with no duplicates.");
            return;
        }
    }
    
    /**
     * Displays a message to the player about their submitted ticket.
     * @param sender
     * @param matches
     */
    public void checkLottery(String sender, int matches) {   
        if(matches > 0) {
            String message = "[Ticket #"+entries+"] Found "+matches+" lucky numbers: ["+matchingNumbers+"]";
            m_botAction.sendSmartPrivateMessage(sender, message);
        }
        else
            m_botAction.sendSmartPrivateMessage(sender, "[Ticket #"+entries+"] Sorry, it seems you aren't lucky this time.");
        
        if(matches == ticketSize) {
            m_botAction.sendArenaMessage(sender+" has won $"+jackpot+"! Ticket #"+entries+": ["+matchingNumbers+"]");
        }
    }
    
    /**
     * Creates random numbers for a winning lottery ticket. [unique numbers]
     * @return
     */
    public Set<Integer> createNumbers() {
        Set<Integer> numbers = new HashSet<Integer>(ticketSize);
       
        while (numbers.size() < ticketSize) {
            numbers.add((int) (Math.random() * numberMax + 1));
        }

        return numbers;
    }
    
    /**
     * Player's tickets will be compared to this winning ticket.
     */
    public void startLottery() {
        List<Object> list = new ArrayList<Object>(createNumbers());
        Object[] objects = list.toArray();

        for ( int i=0; i<objects.length; i++ ) {
            Object object = objects[i];
            winningNum[i] = Integer.parseInt(object.toString());
        }
        
        m_botAction.sendArenaMessage("Lottery has been started.");
    }
    
    /**
     * Checks if the player's ticket does not have any fault.
     * Such as having duplicate numbers and if their numbers are out of range.
     * @param sender
     * @param pieces
     * @return
     */
    private boolean isValid(String sender, String[] pieces) {
        for(int i = 0; i < ticketSize; i++)
            for( int j = 0; j < ticketSize; j++ ) {
                if( i == j )
                    continue;
                else if(pieces[i].equals(pieces[j])) {
                    m_botAction.sendSmartPrivateMessage(sender, "Must contain "+ticketSize+" unique numbers with no duplicates.");
                    return true; 
                }
                else if(Integer.parseInt(pieces[i]) < 1 || Integer.parseInt(pieces[i]) > numberMax) {
                    m_botAction.sendSmartPrivateMessage(sender, "Ticket entry is out of range. [1 to "+numberMax+"]");
                    return true;
                }
            }
      
        return false;
    }
    
    /**
     * Displays the jackpot money to the player.
     * @param sender
     */
    public void displayJackpot(String sender) {
        m_botAction.sendSmartPrivateMessage(sender, "Current jackpot: "+this.jackpot);
    }
    
    /**
     * Sets the price per ticket to the moderator's specified amount.
     * @param sender
     * @param command
     */
    public void setTicketPrice(String sender, String command) {
        String value = command.substring(8);
        try {
            if(Integer.parseInt(value) < 0)
                return;
            this.price = Integer.parseInt(value);
            m_botAction.sendSmartPrivateMessage(sender, "Price per ticket has been set to $"+this.price);
        } catch(Exception e) {
            m_botAction.sendSmartPrivateMessage(sender, "Invalid input. Please enter a proper value.");
            return;
        }
    }
    
    /**
     * Stores the lucky numbers into String variable: matchingNumbers
     * Used to display the matching numbers that player had in ticket.
     */
    public void saveLuckyNumbers(String matchingNum) {
        matchingNumbers += "("+matchingNum+")";
    }
    
    public void resetJackpot() {
        jackpot = 1000;
    }
    
    public void raiseJackpot() {
        jackpot += price;
    }
    
    public void resetPrice() {
        price = 100;
    }

	@Override
	public void handleCommand(String sender, String command) {
		/*
        else if(command.startsWith("!lottery ") || command.startsWith("!l ")) {
            pubLottery.handleTicket(sender, command);
        }
        else if(command.equals("!jackpot") || command.equals("!jp")) {
            pubLottery.displayJackpot(sender);
        }
        */    	    		
    	if (command.startsWith("!guess ")) {
    		if (guessOn) {
    			handleGuess(command, sender);
    		}
    		else
    			m_botAction.sendPrivateMessage(sender, "Guessing is not currently enabled.");
    	}  else if (command.equalsIgnoreCase("!startguess")) {
    		startGuessingGame();
    	}
    	
	}

	@Override
	public void handleModCommand(String sender, String command) {
        try {
            if(command.startsWith("!lprice ")){
                setTicketPrice(sender, command);
            }
        } catch(RuntimeException e) {
            m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }
		
	}
	
	@Override
	public String[] getHelpMessage(String sender) {
		return new String[]{};
	}

	@Override
	public String[] getModHelpMessage(String sender) {
		return new String[]{};
	}


	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void reloadConfig() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}


    @Override
    public void handleSmodCommand(String sender, String command) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[]{};
    }
    
    /*public boolean isEnabled() {
        return lotteryOn;
    }
    
    public void setStatus(boolean mode) {
        lotteryOn = mode;
    }*/ 
    

    
    public void startGuessingGame() {
    	guessOn = true;
    	m_botAction.sendArenaMessage(startingMessage, 2);
    	gWinningNumber = r.nextInt(99) + 1;
    	m_botAction.sendPublicMessage("Number is " + gWinningNumber);
		
    	TimerTask t = new TimerTask() {   
			public void run() {
				endGuessingGame();
			} 
		};
		m_botAction.scheduleTask(t, 2 * Tools.TimeInMillis.MINUTE);
    }
    
    public void endGuessingGame() {
    	String gWinners = "";
    	
    	for (String player : playerGuesses.keySet()) {
    		if (playerGuesses.get(player) == gWinningNumber) {
    			p = manager.getPlayer(player);
    			p.addMoney(gJackpot);
    			gWinningPlayers.add(player);
    			m_botAction.sendPrivateMessage(player, "$" + gJackpot + " has been added to your account for correctly guessing" +
    												   " the lottery number, congratulations!");
    		}
    	}
    	
    	if (gWinningPlayers.size() > 0) {
    		Iterator<String> i = gWinningPlayers.iterator();
    		while (i.hasNext()) {
    			i.next();
    			gWinners += i + ", ";
    		}
    		gWinners.trim();
    		gWinners = gWinners.substring(0, gWinners.length() - 1);
    		m_botAction.sendArenaMessage("Lottery has ended. Winner(s): " + gWinners + ". Congratulations! Winning number was " + gWinningNumber + ".", 2);
    		
    	}
    	else
    		m_botAction.sendArenaMessage("Lottery has ended. There are no winners. Winning number was " + gWinningNumber + ".", 2);
    	
    	guessOn = false;
    	playerGuesses.clear();
    }
    
    public void handleGuess(String message, String name) {
    	String s = message.substring(message.indexOf(" ") + 1);
    	int guess;
    	p = manager.getPlayer(name);
    	
    	try {
    		guess = Integer.valueOf(s);
    	} catch (NumberFormatException e) {
    		m_botAction.sendPrivateMessage(name, "You must guess a number between 0 and 100 in integer format. Example: !guess 50");
    		return;
    	} 
    	
    	if (guess > 0 && guess < 100) {
    		if (p.getMoney() >= gTicketPrice) {
    			if (playerGuesses.containsKey(name)) {
    				playerGuesses.put(name, guess);
    				m_botAction.sendPrivateMessage(name, "Your guess has been changed to " + guess);
    			}
    			else {
    				p.removeMoney(gTicketPrice);
    				playerGuesses.put(name, guess);
    				m_botAction.sendPrivateMessage(name, "You have guessed " + guess + " for $" + gTicketPrice + ".");
    			}
    		}
    		else
    			m_botAction.sendPrivateMessage(name, "You do not have enough funds to guess a number at this time. Please try again later.");
    	}
    	else
    		m_botAction.sendPrivateMessage(name, "You must guess a number between 0 and 100 in integer format. Example: !guess 50");
    		return;
    }
    
    
    
    
    
    
    
    
    
    
    
    
}