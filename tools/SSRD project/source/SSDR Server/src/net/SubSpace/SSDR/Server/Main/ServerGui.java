package net.SubSpace.SSDR.Server.Main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;


public class ServerGui extends JFrame{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	JPanel jPanel1 = new JPanel();
	JScrollPane jScrollPane1 = new JScrollPane();
	static JTextArea text = new JTextArea();
	Font font;
	public ServerGui()
	{
		font = new Font("Courier", 10, 12);
        text.setFont(font);
		try {jbInit();} catch (Exception e) {e.printStackTrace();}

	}

	 private void jbInit() throws Exception {
		 
		 this.setIconImage(Toolkit.getDefaultToolkit().getImage("SSDRServerShield.png"));
		 text.setBackground(new Color(0, 0, 0));
		 text.setForeground(new Color(200, 200, 255));
		 text.setBorder(BorderFactory.createEmptyBorder());
		 text.setToolTipText("");
		 text.setEditable(false);
		 text.setColumns(54);
		    text.setRows(14);
		    JScrollPane second = 
	            new JScrollPane(text, 
	                JScrollPane.VERTICAL_SCROLLBAR_NEVER, 
	                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
	        //Create a text pane.
		    this.setTitle("SSDR Server");
		    this.addWindowListener(new java.awt.event.WindowAdapter() {
		      public void windowClosing(WindowEvent e) {
		        this_windowClosed(e);
		      }
		    });
		    jScrollPane1.getViewport().add(second);
		    jPanel1.add(jScrollPane1);
		    this.getContentPane().add(jPanel1, BorderLayout.WEST);
		    text.setWrapStyleWord(true);
		    this.setVisible(true);
		    this.setSize(400, 300);
		    this.setResizable(false);
		    this.validate();
        	Server.sendMessage(ServerGui.MESSAGE_NONE,"/****************************************************\\");
        	Server.sendMessage(ServerGui.MESSAGE_NONE,"|*   --(]SubSpace Download  Repository Server[)--   *|");
        	Server.sendMessage(ServerGui.MESSAGE_NONE,"|*                      V1.01                       *|");
        	Server.sendMessage(ServerGui.MESSAGE_NONE,"|* *  *))-           -((Alpha))-            -((*  * *|");
        	Server.sendMessage(ServerGui.MESSAGE_NONE,"|*                     Author:                      *|");
        	Server.sendMessage(ServerGui.MESSAGE_NONE,"|*)                   JabJabJab                    (*|");
        	Server.sendMessage(ServerGui.MESSAGE_NONE,"/*                      V1.01                       *\\");
        	Server.sendMessage(ServerGui.MESSAGE_NONE,"\n\n");
	 }
	  void this_windowClosed(WindowEvent e) {
	    System.exit(1);
	  }
	 
	  static public String MESSAGE_NONE = "NONE";
	  static public String MESSAGE_NOTICE = "NOTICE";
	  static public String MESSAGE_ERROR = "ERROR";
	  static public String MESSAGE_WARNING = "WARNING";
	  static public String MESSAGE_CONNECTION = "CONNECTION";
}