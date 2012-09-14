package net.SubSpace.SSDR.Main;

import java.io.*;
import java.nio.channels.*;
/**
 * Taken from http://www.rgagnon.com/javadetails/java-0288.html. Credit is due to them, not me.
 * @author http://www.rgagnon.com/
 *
 */
public class AppLock {
    private String appName;
    private File file;
    private FileChannel channel;
    private FileLock lock;

    public AppLock(String appName) {
        this.appName = appName;
    }

    public boolean isAppActive() {
    	File fil = null;
    	if(SSDR.forceRun)
    	{
    		if((fil = new File(System.getProperty("user.home"), appName + ".tmp")).exists())
    			fil.delete();
    	}
    	try {
            file = new File
                 (System.getProperty("user.home"), appName + ".tmp");
            channel = new RandomAccessFile(file, "rw").getChannel();

            try {
                lock = channel.tryLock();
            }
            catch (OverlappingFileLockException e) {
                // already locked
                closeLock();
                return true;
            }

            if (lock == null) {
                closeLock();
                return true;
            }

            Runtime.getRuntime().addShutdownHook(new Thread() {
                    // destroy the lock when the JVM is closing
                    public void run() {
                        closeLock();
                        deleteFile();
                    }
                });
            return false;
        }
        catch (Exception e) {
            closeLock();
            return true;
        }
    }

    private void closeLock() {
        try { lock.release();  }
        catch (Exception e) {  }
        try { channel.close(); }
        catch (Exception e) {  }
    }

    private void deleteFile() {
        try { file.delete(); }
        catch (Exception e) { }
    }
}