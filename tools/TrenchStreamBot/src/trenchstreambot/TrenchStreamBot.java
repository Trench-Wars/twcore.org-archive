package trenchstreambot;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.IOException;

/**
 * TrenchStreamBot
 * 
 * Simple bot to assist our stream for spectating players in pub. Launches 
 * continuum and pages to bottom of player list. Cycles through a set amount of 
 * player by jumping up two players on the list and returning once. 
 * 
 * @author SpookedOne
 */
public class TrenchStreamBot {

    private static final String EXEC_PATH = 
            "C:/Program Files (x86)/Continuum/continuum.exe";
    private static Robot bot;

    private static final int SPEC_DELAY = 12; //in seconds
    private static final int SPEC_CYCLE = 10; //num of ppl before reset
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        System.out.println("Running TrenchStreamBot...");

        //launch continuum and bot
        try {
            Runtime runTime = Runtime.getRuntime();
            Process process = runTime.exec(EXEC_PATH);
            bot = new Robot();
        } catch (IOException | AWTException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }

        bot.delay(10 * 1000); //wait 10s for continuum to load
        type(KeyEvent.VK_ENTER); //press enter to join
        
        bot.delay(30 * 1000); //wait 30s for load (not handling new map dl)
        
        type(KeyEvent.VK_F2); //keep player list open
        
        //press pg down 40*
        for(int i = 0; i < 40; i++) {
            type(KeyEvent.VK_PAGE_DOWN);
        }
        
        long counter = 0;
        boolean page = false;
        do {

            //change spec
            if (counter % SPEC_DELAY == 0) {
                
                //reset after cycle complete
                if (counter % ((SPEC_CYCLE - 1) * 2) == 0) {
                    //press pg down 40*
                    for(int i = 0; i < 40; i++) {
                        type(KeyEvent.VK_PAGE_DOWN);
                    }
                    page = false;
                }
                
                if (page) {
                    for (int i = 0; i < 1; i++) {
                        type(KeyEvent.VK_PAGE_DOWN);
                    }
                    page = false;
                } else {
                    for (int i = 0; i < 2; i++) {
                        type(KeyEvent.VK_PAGE_UP);
                    }
                    page = true;
                }
                bot.delay(200); //wait 200ms
                type(KeyEvent.VK_CONTROL, 40); //delay set due to slow response
            }

            bot.delay(1 * 1000); //sleep for 1s
            counter ++; //increment our counter
            
        } while (true); //run forever
    }

    private static void type(int i) {
        bot.delay(40);
        bot.keyPress(i);
        bot.keyRelease(i);
    }

    private static void type(int i, int delay) {
        bot.delay(40);
        bot.keyPress(i);
        bot.delay(delay);
        bot.keyRelease(i);
    }
    
    private static void type(String s) {
        byte[] bytes = s.getBytes();
        for (byte b : bytes) {
            int code = b;
            // keycode only handles [A-Z] (which is ASCII decimal [65-90])
            if (code > 96 && code < 123) {
                code = code - 32;
            }
            bot.delay(40);
            bot.keyPress(code);
            bot.keyRelease(code);
        }
    }
}
