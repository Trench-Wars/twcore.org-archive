package net.SubSpace.SSDR.Server.Main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ServerProtocol {
	public static String directory = "SSDR/ZoneData/";
	
	
	public ServerProtocol(){}
	public byte[] sendFile(File file) throws IOException
	{
		return getBytesFromFile(file);
	}
	
	public boolean shouldFileBeUpdated(Long clientFileSize, File file)
	{
		
		if(!file.exists())
		{
			System.out.println("Something's wrong with the directory link:" + file.getAbsoluteFile());
			return true;
		}
		if(file.length() != clientFileSize)
		{
			return true;
		}
		else
		{			
			return false;
		}	
	}
	
	public long getFileSizeInBytes(File file)
	{
		return file.length();
	}
	
	public static byte[] getBytesFromFile(File file) throws IOException {
	    InputStream is = new FileInputStream(file);
	    long length = file.length();
	    if (length > Integer.MAX_VALUE)
	    {
	    }
	    byte[] bytes = new byte[(int)length];
	    int offset = 0;
	    int numRead = 0;
	    while (offset < bytes.length && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0)
	    {
	    	offset += numRead;
	    }
	    if (offset < bytes.length) {
	        throw new IOException("Could not completely read file "+file.getName());
	    }
	    is.close();
	    return bytes;
	}
}
