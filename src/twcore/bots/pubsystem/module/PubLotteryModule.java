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
import twcore.bots.pubsystem.pubsystem;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.util.Tools;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.bots.pubsystem.module.PubPlayerManagerModule;

/*
 * By Eria
 */

public class PubLotteryModule extends AbstractModule {
    // guess variables
    public HashMap<String, Integer> playerGuesses;
    public LinkedList<String> gWinningPlayers;
    public int gTicketPrice;
    public int gJackpot;
    public int gWinningNumber;
    public int gTime;
    public TimerTask t;
    public Random r;
    public boolean guessOn;
    public String startingMessage;
    public PubPlayer p;
    PubPlayerManagerModule manager;

    // lottery variables
    private int ticketSize = 2; // amount of entry numbers per ticket; default is 2
    private int numberMax = 50; // interval of number options; default is 1-50
    private int jackpot; // default jackpot is $1000; if you change this value, also change it in resetJackpot()
    private int price; // default ticket price is $100; if you change this value, also change it in resetPrice()
    private int entries; // counts the number of lottery tickets submitted
    private int[] winningNum = new int[ticketSize]; // lottery's winning numbers
    private String matchingNumbers; // change 'store' to display? // stores and displays the player's numbers that match the winning numbers
    private boolean lotteryOn; // status for lottery; true = enabled, false = disabled

    /* TODO - Price checks - When to start/stop lottery -- must find a place to do startLottery() then will create winning numbers - Disable commands
     * such as !lprice and !lottery if lottery is not enabled. [methods to do this is commented] - If player does not have enough money, tell player
     * how much it costs - lotteryOn if neccessary > enables/disables this lottery game */

    public PubLotteryModule(BotAction botAction, PubContext context) {
        super(botAction, context, "Lottery");

        // guess
        playerGuesses = new HashMap<String, Integer>();
        gWinningPlayers = new LinkedList<String>();
        gTicketPrice = 100;
        gJackpot = 2500;
        gWinningNumber = -1;
        gTime = 5;
        r = new Random();
        guessOn = false;
        startingMessage = "LOTTERY is starting! To buy a number, PM me with !guess <#>, where # is an integer between 0 " +
        				  "and 100. For help, PM me with !lotteryhelp -" + m_botAction.getBotName();
        manager = context.getPlayerManager();

        // lottery
        jackpot = 1000;
        price = 100;
        entries = 0;
        lotteryOn = false;
        reloadConfig();
        m_botAction.sendPublicMessage("Lotto loaded!");

    }

    @Override
    public void requestEvents(EventRequester eventRequester) {

    }

    /**
     * Checks the player's submitted ticket.
     * 
     * @param sender
     * @param command
     */
    public void handleTicket(String sender, String command) {
        int matches = 0;
        String matchingNum = null;
        String[] entry = command.substring(command.indexOf(" ") + 1).split(" ");

        matchingNumbers = "";
        try {
            if (this.isValid(sender, entry))
                return;
            if (entry.length != ticketSize) {
                m_botAction.sendSmartPrivateMessage(sender, "Please submit a ticket with two numbers.");
                return;
            }

            for (int i = 0; i < ticketSize; i++) {
                for (int j = 0; j < ticketSize; j++)
                    if (Integer.parseInt(entry[i]) == winningNum[j]) {
                        matchingNum = entry[i];
                        matches++;

                        this.saveLuckyNumbers(matchingNum);
                    }
            }

            entries++;
            this.raiseJackpot();
            this.checkLottery(sender, matches);

        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(sender, "Submission denied; Must contain " + ticketSize + " unique numbers with no duplicates.");
            return;
        }
    }

    /**
     * Displays a message to the player about their submitted ticket.
     * 
     * @param sender
     * @param matches
     */
    public void checkLottery(String sender, int matches) {
        if (matches > 0) {
            String message = "[Ticket #" + entries + "] Found " + matches + " lucky numbers: [" + matchingNumbers + "]";
            m_botAction.sendSmartPrivateMessage(sender, message);
        } else
            m_botAction.sendSmartPrivateMessage(sender, "[Ticket #" + entries + "] Sorry, it seems you aren't lucky this time.");

        if (matches == ticketSize) {
            m_botAction.sendArenaMessage(sender + " has won $" + jackpot + "! Ticket #" + entries + ": [" + matchingNumbers + "]");
        }
    }

    /**
     * Creates random numbers for a winning lottery ticket. [unique numbers]
     * 
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

        for (int i = 0; i < objects.length; i++) {
            Object object = objects[i];
            winningNum[i] = Integer.parseInt(object.toString());
        }

        m_botAction.sendArenaMessage("Lottery has been started.");
    }

    /**
     * Checks if the player's ticket does not have any fault. Such as having duplicate numbers and if their numbers are out of range.
     * 
     * @param sender
     * @param pieces
     * @return
     */
    private boolean isValid(String sender, String[] pieces) {
        for (int i = 0; i < ticketSize; i++)
            for (int j = 0; j < ticketSize; j++) {
                if (i == j)
                    continue;
                else if (pieces[i].equals(pieces[j])) {
                    m_botAction.sendSmartPrivateMessage(sender, "Must contain " + ticketSize + " unique numbers with no duplicates.");
                    return true;
                } else if (Integer.parseInt(pieces[i]) < 1 || Integer.parseInt(pieces[i]) > numberMax) {
                    m_botAction.sendSmartPrivateMessage(sender, "Ticket entry is out of range. [1 to " + numberMax + "]");
                    return true;
                }
            }

        return false;
    }

    /**
     * Displays the jackpot money to the player.
     * 
     * @param sender
     */
    public void displayJackpot(String sender) {
        m_botAction.sendSmartPrivateMessage(sender, "Current jackpot: " + this.jackpot);
    }

    /**
     * Sets the price per ticket to the moderator's specified amount.
     * 
     * @param sender
     * @param command
     */
    public void setTicketPrice(String sender, String command) {
        String value = command.substring(8);
        try {
            if (Integer.parseInt(value) < 0)
                return;
            this.price = Integer.parseInt(value);
            m_botAction.sendSmartPrivateMessage(sender, "Price per ticket has been set to $" + this.price);
        } catch (Exception e) {
            m_botAction.sendSmartPrivateMessage(sender, "Invalid input. Please enter a proper value.");
            return;
        }
    }

    /**
     * Stores the lucky numbers into String variable: matchingNumbers Used to display the matching numbers that player had in ticket.
     */
    public void saveLuckyNumbers(String matchingNum) {
        matchingNumbers += "(" + matchingNum + ")";
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
        /*else if(command.startsWith("!lottery ") || command.startsWith("!l ")) { pubLottery.handleTicket(sender, command); } else
         * if(command.equals("!jackpot") || command.equals("!jp")) { pubLottery.displayJackpot(sender); } */

        try {
            if (command.startsWith("!guess ")) {
                if (guessOn)
                    handleGuess(sender, command);
                else
                    m_botAction.sendPrivateMessage(sender, "Guessing is not currently enabled.");
            } else if (command.equalsIgnoreCase("!lotteryhelp")) {
            	lotteryHelp(sender);
            } else if (command.equalsIgnoreCase("!prices")) {
            	lotteryPrices(sender);
            }

        } catch (RuntimeException e) {
            if (e != null && e.getMessage() != null)
                m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }

    }

    @Override
    public void handleModCommand(String sender, String command) {
        try {
            if (command.startsWith("!lprice ")) {
                setTicketPrice(sender, command);
            } else if (command.equalsIgnoreCase("!startguess")) {
                startGuessingGame();
            } else if (command.startsWith("!setjp ")) {
            	if (!guessOn) {
            		setGuessJackpot(sender, command);
            	} else {
            		m_botAction.sendPrivateMessage(sender, "You cannot use this command while lottery is running.");
            	}
            } else if (command.startsWith("!settp ")) {
            	if (!guessOn) {
            		setGuessTicketPrice(sender, command);
            	} else {
            		m_botAction.sendPrivateMessage(sender, "You cannot use this command while lottery is running.");
            	}
            } else if (command.startsWith("!settime ")) {
            	if (!guessOn) {
            		setGuessTime(sender, command);
            	} else {
            		m_botAction.sendPrivateMessage(sender, "You cannot use this command while lottery is running.");
            	}
            } else if (command.equalsIgnoreCase("!restoredefaults") || command.equalsIgnoreCase("!rd")) {
            	if (!guessOn) {
            		restoreGuessDefaults(sender);
            	} else {
            		m_botAction.sendPrivateMessage(sender, "You cannot use this command while lottery is running.");
            	}
            } else if (command.equalsIgnoreCase("!seevalues")) {
            	getGuessValues(sender, command);
            } else if (command.equalsIgnoreCase("!endlottery")) {
            	endLottery(sender, command);
            }
        } catch (RuntimeException e) {
            m_botAction.sendSmartPrivateMessage(sender, e.getMessage());
        }

    }



	@Override
    public String[] getHelpMessage(String sender) {
        return new String[] {
        		pubsystem.getHelpLine("!guess <#>        -- Buys a ticket for number <#>, where <#> is an integer between 0 and 100."),
        		pubsystem.getHelpLine("!prices           -- Shows ticket price and jackpot value for lottery."),
        		pubsystem.getHelpLine("!lotteryHelp      -- Info about the TW Lottery."),        		
        };
    }

    @Override
    public String[] getModHelpMessage(String sender) {
        return new String[] {        		
    			pubsystem.getHelpLine("!setjp <$>            -- Set lottery jackpot to <$>, must be between 1 and 50,000."),
    			pubsystem.getHelpLine("!settp <$>            -- Set ticket price to <$>, must be between 1 and 1,000 and less than the jackpot."),
    			pubsystem.getHelpLine("!settime <#>          -- Set length of lottery rounds to <#> in minutes, must be between 1 and 60."),
    			pubsystem.getHelpLine("!seevalues            -- View the current ticket/jackpot/round length values for lottery."),
    			pubsystem.getHelpLine("!restoredefaults/!rd  -- Restore the default lottery values."),
    			pubsystem.getHelpLine("!endlottery           -- Cancels the current lottery round, reimburses money to players."),
    			pubsystem.getHelpLine(" NOTE: Do not alter the lottery values without permission from an SMod+"),
		
        };
        
    }

    @Override
    public void start() {
        // TODO Auto-generated method stub

    }

    @Override
    public void reloadConfig() {
        if (m_botAction.getBotSettings().getInt("lottery_enabled") == 1) {
            enabled = true;
        }
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
        return new String[] {};
    }

    /* public boolean isEnabled() { return lotteryOn; }
     * 
     * public void setStatus(boolean mode) { lotteryOn = mode; } */

    public void startGuessingGame() {
        guessOn = true;
        clearGuessValues();
        m_botAction.sendArenaMessage(startingMessage, 2);
        gWinningNumber = r.nextInt(99) + 1;

        t = new TimerTask() {
            public void run() {
                endGuessingGame();
            }
        };
        m_botAction.scheduleTask(t, gTime * Tools.TimeInMillis.MINUTE);
    }

    public void endGuessingGame() {
        String gWinners = "";

        for (String player : playerGuesses.keySet()) {
            if (playerGuesses.get(player) == gWinningNumber) {
                p = manager.getPlayer(player);
                if (p == null) {
                	return;
                } 
                	p.addMoney(gJackpot);
                    gWinningPlayers.add(player);
                    m_botAction.sendPrivateMessage(player, "$" + gJackpot + " has been added to your account for correctly guessing"
                            + " the lottery number, congratulations!");
                
                
            } else if ((gWinningNumber - playerGuesses.get(player)) == 1) {
            	p = manager.getPlayer(player);
                if (p == null) {
                	return;
                }
                	p.addMoney(gJackpot/2);
                	gWinningPlayers.add(player);
                	m_botAction.sendPrivateMessage(player, "$" + (gJackpot/2) + " has been added to your account for guessing"
                            + " within 1 of the winning lottery number, congratulations!");
                 
            } else if ((gWinningNumber - playerGuesses.get(player)) <= 5) {
            	p = manager.getPlayer(player);
                if (p == null) 
                	return;
                
                	p.addMoney(gJackpot/5);
                	gWinningPlayers.add(player);
                	m_botAction.sendPrivateMessage(player, "$" + (gJackpot/5) + " has been added to your account for guessing"
                            + " within 5 of the winning lottery number, congratulations!");
                
            }
        }

        if (gWinningPlayers.size() > 0) {
            Iterator<String> i = gWinningPlayers.iterator();
            while (i.hasNext()) {
                String temp = i.next();                
                gWinners += temp + ", ";
            }
            gWinners = gWinners.substring(0, gWinners.length() - 2);
            m_botAction.sendArenaMessage("Lottery has ended. Winner(s): " + gWinners + ". Congratulations! Winning number was " + gWinningNumber
                    + ".", 2);

        } else
            m_botAction.sendArenaMessage("Lottery has ended. There are no winners. Winning number was " + gWinningNumber + ".", 2);

        guessOn = false;
        playerGuesses.clear();
    }
            

    public void handleGuess(String name, String message) {
        String s = message.substring(message.indexOf(" ") + 1);
        int guess;
        p = manager.getPlayer(name);
        if (p == null) 
        	return;        
        
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
        			} else {
        				p.removeMoney(gTicketPrice);
        				playerGuesses.put(name, guess);
        				m_botAction.sendPrivateMessage(name, "You have guessed " + guess + " for $" + gTicketPrice + ".");
        			}
        		} else
        			m_botAction.sendPrivateMessage(name, "You do not have enough funds to guess a number at this time. Please try again later.");
        	} else
        		m_botAction.sendPrivateMessage(name, "You must guess a number between 0 and 100 in integer format. Example: !guess 50");
        
    }
    
    
    public void lotteryHelp(String name) {
    	String[] help = {
    			"+---------------------------------- LOTTERY ------------------------------------.",
    			"|  Trench Wars lottery is a simple game of guessing a number and hoping to get  |",
    			"|  lucky. To play, simply wait for a round to start and then PM TW-Pub1 with    |",
    			"|  !guess <#>, where # is an integer value between 0 and 100. For example, if   |",
    			"|  you wanted to guess the number 50, you would simply type :TW-Pub1:!guess 50  |",
    			"|  and your guess will be recorded. When the lottery ends, if you guessed the   |",
    			"|  correct number, you will be handsomly rewarded! As an added bonus, there is  |",
    			"|  also a prize for guessing close to the winning number. Note: tickets are not |",
    			"|  free, so be sure you have the cash and are feelin' lucky! If you wish to     |",
    			"|  change your guess at any time, just use the !guess command and your number   |",
    			"|  will be changed, free of charge. Use !prices to see the cost of a ticket and |",
    			"|  the value of the jackpot. Good luck!                                         |",
    			"`-------------------------------------------------------------------------------'",    			
    	};
    	m_botAction.privateMessageSpam(name, help);
    }
    
    public void lotteryPrices(String name) {
    	String[] prices = {
        		"Ticket price     - $" + gTicketPrice,
        		"Jackpot          - $" + gJackpot,
    	};
    	m_botAction.privateMessageSpam(name, prices);
    }
    
    public void setGuessJackpot(String name, String cmd) {
    	String jp = cmd.substring(cmd.indexOf(" ") + 1);
    	try {
    		int tempJP = Integer.valueOf(jp);
    		if (tempJP >= gTicketPrice && tempJP >= 1 && tempJP <= 50000) {
    			gJackpot = tempJP;
    			m_botAction.sendPrivateMessage(name, "Jackpot has been set to " + gJackpot);
    		} else
    			m_botAction.sendPrivateMessage(name, "You must choose a value between 1 and 50,0000 that is greater than the ticket price.");
    	} catch (NumberFormatException e) {
    		m_botAction.sendPrivateMessage(name, "The action could not be completed at this time.");
    	}
    }
    
    public void setGuessTicketPrice(String name, String cmd) {
    	String tp = cmd.substring(cmd.indexOf(" ") + 1);
    	try {
    		int tempTP = Integer.valueOf(tp);
    		if (tempTP >= 1 && tempTP <= 1000) {
    			gTicketPrice = tempTP;
    			m_botAction.sendPrivateMessage(name, "Ticket price has been set to " + gTicketPrice);
    		} else 
    			m_botAction.sendPrivateMessage(name, "You must choose a value between 1 and 1000.");
    	} catch (NumberFormatException e){
    		m_botAction.sendPrivateMessage(name, "The action could not be completed at this time.");
    	}
    }
    
    public void setGuessTime(String name, String cmd) {
    	String time = cmd.substring(cmd.indexOf(" ") + 1);
    	try {
    		int tempTime = Integer.valueOf(time);
    		if (tempTime >= 1 && tempTime <= 60) {
    			gTime = tempTime;
    			m_botAction.sendPrivateMessage(name, "Time has been set to " + gTime);
    		} else
    			m_botAction.sendPrivateMessage(name, "You must choose a value between 1 and 60 in minutes.");
    	} catch (NumberFormatException e) {
    		m_botAction.sendPrivateMessage(name, "The action could not be completed at this time.");
    	}
    }
    
    public void getGuessValues(String name, String cmd) {
    	String[] gValues = {
        		"Ticket price     - $" + gTicketPrice,
        		"Jackpot          - $" + gJackpot,
        		"Time             - " + gTime + " mins",
    	};
    	m_botAction.privateMessageSpam(name, gValues);
    }
    
    public void restoreGuessDefaults(String name) {
        gTicketPrice = 100;
        gJackpot = 2500;
        gWinningNumber = -1;
        gTime = 5;
        String[] defaults = {
        		"Default values have been restored:",
        		" Ticket price     - $" + gTicketPrice,
        		" Jackpot          - $" + gJackpot,
        		" Time             - " + gTime + " mins",        		
        };
        m_botAction.privateMessageSpam(name, defaults);
    }
    
    public void clearGuessValues() {
    	playerGuesses.clear();
    	gWinningPlayers.clear();
    }
    
    public void endLottery(String name, String cmd) {
    	if (guessOn) {
    		m_botAction.cancelTask(t);    	
    		m_botAction.sendArenaMessage("LOTTERY has been cancelled. All players have been reimbursed for their tickets. -" + m_botAction.getBotName(), 2);
    		guessOn = false;
    		for (String player : playerGuesses.keySet()) {
    			p = manager.getPlayer(player);
    			if (p == null) 
    				return;
    			p.addMoney(gTicketPrice);
            
    		}
    	} else
    		m_botAction.sendPrivateMessage(name, "Lottery is not currently running.");
    }

}