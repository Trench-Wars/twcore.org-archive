package net.SubSpace.SSDR.Main;

import java.io.File;

public class Protocol {

	
	public boolean doesFileExist(String zoneName,String fileName)
	{
		File file = new File(SSDR.Directory + "Zones/" + fileName);
		return file.exists();
	}
	
	public Long getFileSize(String zoneName,String fileName)
	{
		File file = new File(SSDR.Directory + "Zones/" + fileName);
		return file.length();	
	}
}
