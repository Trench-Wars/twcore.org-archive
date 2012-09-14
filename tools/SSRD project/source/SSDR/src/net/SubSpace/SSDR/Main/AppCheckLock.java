package net.SubSpace.SSDR.Main;


/**
 * Taken from http://www.rgagnon.com/javadetails/java-0288.html. Credit is due to them, not me.
 * @author http://www.rgagnon.com/
 *
 */
public class AppCheckLock {
    public static void main(String[] args) {
        new AppCheckLock().test();
    }

    void test() {
        AppLock ua = new AppLock("SSDRClient");

        if (ua.isAppActive()) {
            System.out.println("Already active.");
            System.exit(1);    
        }
        else {
            System.out.println("NOT already active.");
        }
    }
}
