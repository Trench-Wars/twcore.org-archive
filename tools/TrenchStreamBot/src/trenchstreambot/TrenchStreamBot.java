package trenchstreambot;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.IOException;

/**
 *
 * @author SpookedOne
 */
public class TrenchStreamBot {

    private static final String EXEC_PATH = 
            "c:/program files(x86)/continuum/continuum.exe";
    private static Robot bot;

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
        
        bot.delay(5 * 60 * 1000); //wait 5m for load (new map dl)
        
        //press pg down 40*
        for(int i = 0; i < 40; i++) {
            type(KeyEvent.VK_PAGE_DOWN);
        }
        
        long counter = 0;
        long pager = 0;
        do {

            //every 30s, change spec
            if (counter % 30 == 0) {
                if (pager == 8) {
                    for (int i = 0; i < 8; i++) {
                        type(KeyEvent.VK_PAGE_DOWN);
                    }
                    pager = 0;
                } else {
                    for (int i = 0; i < 8; i++) {
                        type(KeyEvent.VK_PAGE_UP);
                    }
                    pager++;
                }
                type(KeyEvent.VK_CONTROL);
            }
            
            //at 5m, keep alive/reset
            if (counter == 5 * 60) {
                type(":TrenchStream:Play TrenchWars!");
                type(KeyEvent.VK_ENTER);
                
                //reset counter so not to get too high
                counter = 0;
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
