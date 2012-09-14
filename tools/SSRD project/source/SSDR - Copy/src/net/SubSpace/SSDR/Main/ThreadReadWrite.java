package net.SubSpace.SSDR.Main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Scanner;

public class ThreadReadWrite extends Thread implements Runnable{

	public File file;
	public String Major;
	public String Minor;
	public String ZoneName;
	public String Message;
	
	public boolean isReadorWrite = false;
	
	public ThreadReadWrite(boolean isReadOrWrite, File file, String major, String minor, String zonename, String message)
	{
		isReadorWrite = isReadOrWrite;
		this.file = file;
		this.Major = major;
		this.Minor = minor;
		this.ZoneName = zonename;
		this.Message = message;
	}
	@Override
	public void run(){}

	public static final String PROP_ZONE_UPDATE = "ZONE_UPDATE";
	public static final String PROP_UPDATE = "UPDATE";
	
	public static String readProperty(File file, String Major, String Minor, String ZoneName) throws IOException {
	    String NL = System.getProperty("line.separator");
	    Scanner scanner = new Scanner(new FileInputStream(file.getAbsolutePath()), "UTF8");
	    try {
	      while (scanner.hasNextLine()){
	        //text.append(scanner.nextLine() + NL);
	    	  String temp = scanner.nextLine();
	    	  if(Major.equals( PROP_ZONE_UPDATE))
	    	  {
	    		  if(temp.startsWith("ZONE:"))
	    		  {
	    			  
	    		  }	    		  
	    	  }
	      }
	    }
	    finally{
	      scanner.close();
	    }
		return NL;
	  }
	
	 public static void writeProperty(File file, String message) throws IOException  {
		    Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF8");
		    file.createNewFile();
		    out.write(message);
		    out.close();
		  }
	 
	 public static void writeNewPropertyFile(File file) throws IOException  {
		    Writer out = new OutputStreamWriter(new FileOutputStream(file.getAbsolutePath()), "UTF8");
		    try {
		      out.write("true");
		    }
		    finally {
		      out.close();
		    }
		  }
	 
		public static String readPropertyFile(File file) throws IOException {

		    Scanner scanner = new Scanner(new FileInputStream(file), "UTF8");
		    try {
		      while (scanner.hasNextLine()){
		        //text.append(scanner.nextLine() + NL);
		    	  String temp = scanner.nextLine();
		    		  if(temp.equals("true"))
		    		  {
		    		      scanner.close();
		    		      System.out.println(file.getName() + " is true");
		    			  return "true";
		    		  }
		    		  else if(temp.startsWith("C:/") || temp.startsWith("C:\\"))
		    		  {
		    		      scanner.close();
		    		      System.out.println(file.getName() + " is true");
		    			  return temp;
		    		  }
		    	  }
		    }
		    finally{
		      scanner.close();
		    }
			return "false";
		  }
}
