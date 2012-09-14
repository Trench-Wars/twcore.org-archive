import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.jar.JarFile;

import javax.swing.JOptionPane;


public class Installer {

	public static void main(String args[])
	{

		File dir = null;
		try {
			dir = new File(Installer.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
		} catch (URISyntaxException e) {

			e.printStackTrace();
		};
		if(dir.exists())
		{
			System.out.println(dir.getAbsolutePath());
			
		}
		
		System.out.printf("JOptionPane.YES_OPTION    = %d%n" +
                "JOptionPane.NO_OPTION     = %d%n" +
                "JOptionPane.CLOSED_OPTION = %d%n",
                 JOptionPane.YES_OPTION,
                 JOptionPane.NO_OPTION,
                 JOptionPane.CLOSED_OPTION);
		int more = JOptionPane.YES_OPTION;
		more = JOptionPane.showConfirmDialog(null, "Do you want to install SSDR ?", "Input",
                            JOptionPane.YES_NO_OPTION);
		if(more == 0)
		{
			
			File SSDRDir = new File(System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\SSDR\\");
			SSDRDir.mkdirs();
			//Install
			File dest = new File(System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\SSDRClient.exe");
			File dest2 = new File(System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\SSDR\\SSDRClient.exe");
			File exe = new File("Data/SSDRClient.exe");
			File uninstaller = new File("Data/UninstallSSDR.exe");
			File uninstallerdest = new File(System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\SSDR\\UninstallSSDR.exe");
			if(!exe.exists())
			{
				JOptionPane.showMessageDialog(null, "Installer Missing Data/SSDRClient.exe", "SSDR Installer", 1);
				System.exit(0);
			}
			if(!uninstaller.exists())
			{
				JOptionPane.showMessageDialog(null, "Installer Missing Data/UninstallSSDR.exe", "SSDR Installer", 1);
				System.exit(0);
			}
			try {
				dest.createNewFile();
				dest2.createNewFile();
				uninstallerdest.createNewFile();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			System.out.println("Loc: " + exe.getAbsolutePath());
			System.out.println("Dest: " + dest.getAbsolutePath());
			try {
				copyFile(exe,dest);
				copyFile(exe,dest2);
				copyFile(uninstaller,uninstallerdest);

			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
			JOptionPane.showMessageDialog(null, "SSDR has successfully been installed on this Windows account.", "SSDR Installer", 1);

		}
		
		else
		{
			System.exit(0);
		}
	}
	
	private static void copyFile(File sourceFile, File destFile) throws IOException
	{
		if (!sourceFile.exists()) {
		    return;
		}
		if (!destFile.exists()) {
		    destFile.createNewFile();
		}
		FileChannel source = null;
		FileChannel destination = null;
		source = new FileInputStream(sourceFile).getChannel();
		destination = new FileOutputStream(destFile).getChannel();
		if (destination != null && source != null)
		{
		    destination.transferFrom(source, 0, source.size());
		}
		if (source != null)
		{
		    source.close();
		}
		if (destination != null)
		{
		    destination.close();
		}
	}
	public File retrieveFileFromJar(String name)
	{
		File filetoreturn = null;
		java.util.jar.JarFile jar = null;
		try {
			jar = new java.util.jar.JarFile("");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		java.util.Enumeration enum2 = jar.entries();
		while (enum2.hasMoreElements()) {
			java.util.jar.JarEntry file = (java.util.jar.JarEntry) enum2.nextElement();
			if(file.getName().equals(name))
			{
				java.io.File f = new java.io.File("C:/temp/" + java.io.File.separator + file.getName());
				if (file.isDirectory()) { // if its a directory, create it
					f.mkdir();
					continue;
				}
				java.io.InputStream is = null;
				try {
					is = jar.getInputStream(file);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} // get the input stream
				java.io.FileOutputStream fos = null;
				try {
					fos = new java.io.FileOutputStream(f);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try {
					while (is.available() > 0) {  // write contents of 'is' to 'fos'
						fos.write(is.read());
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try {
					fos.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try {
					is.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}				
			}
		}
		return filetoreturn;
		
	}

}
