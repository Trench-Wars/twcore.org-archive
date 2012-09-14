import java.io.File;

import javax.swing.JOptionPane;


public class Uninstaller {

	public static void main(String args[])
	{
		System.out.printf("JOptionPane.YES_OPTION    = %d%n" +
                "JOptionPane.NO_OPTION     = %d%n" +
                "JOptionPane.CLOSED_OPTION = %d%n",
                 JOptionPane.YES_OPTION,
                 JOptionPane.NO_OPTION,
                 JOptionPane.CLOSED_OPTION);
		int more = JOptionPane.YES_OPTION;
		more = JOptionPane.showConfirmDialog(null, "Do you want to uninstall SSDR for this Windows Account?", "Input",
                            JOptionPane.YES_NO_OPTION);
		if(more == 0)
		{
		File AppDataSSDR = new File(System.getenv("APPDATA") + "/SSDR/");	
		
		deleteDir(AppDataSSDR);
		File exe = new File(System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\SSDRClient.exe");
		exe.delete();
		File SSDRDir = new File(System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\SSDR\\");  
		if(!SSDRDir.exists())
		{
			JOptionPane.showMessageDialog(null, "SSDR has not been installed on this Windows account.", "SSDR Uninstaller", 1);

			System.exit(0);
		}
		deleteDir(SSDRDir);
		}
		else
		{
			System.exit(0);
		}
		JOptionPane.showMessageDialog(null, "SSDR has been successfully uninstalled for this Windows account.", "SSDR Uninstaller", 1);
	}
	
	public static boolean deleteDir(File dir) {
	    if (dir.isDirectory()) {
	        String[] children = dir.list();
	        for (int i=0; i<children.length; i++) {
	            boolean success = deleteDir(new File(dir, children[i]));
	            if (!success) {
	                return false;
	            }
	        }
	    }

	    // The directory is now empty so delete it
	    return dir.delete();
	}
}
