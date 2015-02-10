package atChat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatPreferences {
	protected static final float VERSION = 1.1f;
	
	protected static File prefPath;
	protected static File lastFilePath;
	protected static File lastDirPath;
	
	protected static File lastDownloadPath;
	
	protected static String handle = "Screen Name";
	
	protected static int lChatPort = 13582;
	protected static int lFilePort = 13583;
	
	protected static int rChatPort = 12582;
	protected static String rIPAddress = "127.0.0.1";
	
	protected static boolean autoAccept = false;
	protected static boolean continueListening = true;
	
	protected static void setLocalDefault() {
		lChatPort = 13582;
		lFilePort = 13583;
	}
	
	protected static void setRemoteDefault() {
		rChatPort = 13582;
		rIPAddress = "127.0.0.1";
	}
	
	protected static String readPrefs() {
		String msg = "";
		
		if (prefPath.exists()) {
			DataInputStream in = null;
			
			try {
				in = new DataInputStream(new BufferedInputStream(new FileInputStream(prefPath)));
				
				in.readInt();
				
				handle = in.readUTF();
				if (handle != null) {
					handle = (handle.length() > 0) ? handle : "Screen Name";
				} else { handle = "Screen Name"; }
				lChatPort = in.readInt();
				lFilePort = in.readInt();
				rChatPort = in.readInt();
				rIPAddress = in.readUTF();
				
				lChatPort = (lChatPort > 0 && lChatPort <= 65535) ? lChatPort : 13582;
				lFilePort = (lFilePort > 0 && lFilePort <= 65535) ? lFilePort : 13583;
				rChatPort = (rChatPort > 0 && rChatPort <= 65535) ? rChatPort : 13582;
				
				boolean goodIP = false;
				String[] ipS = rIPAddress.split("\\.");
				if (ipS.length == 4) {
					if ((ipS[0].length() > 0 && ipS[0].length() <= 3) && (ipS[1].length() > 0 && ipS[1].length() <= 3) && (ipS[2].length() > 0 && ipS[2].length() <= 3) && (ipS[3].length() > 0 && ipS[3].length() <= 3)) {
						goodIP = true;
						for (String str : ipS) {
							try {
								Integer.parseInt(str);
							} catch (NumberFormatException e) {
								goodIP = false;
								break;
							}
						}
					}
				}
				if (!goodIP) {
					rIPAddress = "127.0.0.1";
				}
				
				autoAccept = in.readBoolean();
				
				lastFilePath = new File(in.readUTF());
				lastDirPath = new File(in.readUTF());
				lastDownloadPath = new File(in.readUTF());
				
				lastFilePath = (lastFilePath.exists()) ? lastFilePath : new File(System.getProperty("user.home"));
				lastDirPath = (lastDirPath.exists()) ? lastDirPath : new File(System.getProperty("user.home"));
				lastDownloadPath = (lastDownloadPath.exists()) ? lastDownloadPath : (new File(System.getProperty("user.home"), "Downloads").exists()) ? new File(System.getProperty("user.home"), "Downloads") : new File(System.getProperty("user.home"));
				
				continueListening = in.readBoolean();
			} catch (FileNotFoundException FNEe) {
				msg = FNEe.getMessage();
			} catch (IOException IOe) {
				msg = IOe.getMessage();
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
						Logger.getLogger(ChatPreferences.class.getName()).log(Level.WARNING, e.getMessage(), e);
					}
				}
			}
		}
		
		return msg;
	}
	
	protected static String writePrefs() {
		String msg = "";
		
		if (prefPath.getParentFile().exists()) {
			DataOutputStream os = null;
			
			try {
				os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(prefPath, false)));
				
				os.writeFloat(VERSION);
				
				os.writeUTF((handle.length() > 0) ? handle : "Screen Name");
				os.writeInt(lChatPort);
				os.writeInt(lFilePort);
				os.writeInt(rChatPort);
				os.writeUTF(rIPAddress);
				
				os.writeBoolean(autoAccept);
				
				os.writeUTF(lastFilePath.getPath());
				os.writeUTF(lastDirPath.getPath());
				os.writeUTF(lastDownloadPath.getPath());
				
				os.writeBoolean(continueListening);
			} catch (FileNotFoundException FNFe) {
				msg = FNFe.getMessage();
			} catch (IOException IOe) {
				msg = IOe.getMessage();
			} catch (NullPointerException nE) {
				msg = nE.getMessage();
			} finally {
				if (os != null) {
					try {
						os.close();
					} catch (IOException e) {
						Logger.getLogger(ChatPreferences.class.getName()).log(Level.WARNING, e.getMessage(), e);
					}
				}
			}
		}
		
		return msg;
	}
}
