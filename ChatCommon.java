package atChat;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public class ChatCommon {
	public static final String REQ_PASSWORD = "REQPASSWORD";
	public static final String NO_PASSWORD = "NOPASSWORD";
	public static final String ACCEPTED = "ACCEPTED";
	public static final String REFUSED = "REFUSED";
	
	private static final String LINE_BREAK = "%%!%%!!";
	public static final String BEGIN_SYSTEM = "%%BEGINSYSTEM%%";
	public static final String END_SYSTEM = "%%ENDSYSTEM%%";
	public static final String SEPARATOR = "|";
	public static final String SPLIT_SEPARATOR = "\\|";
	
	public static final String NEWCLIENT = "NEWCLIENT";
	public static final String CONFIRM = "CONFIRMED";
	public static final String DISCONNECT = "DISCONNECT";
	public static final String TRANSFER_FILES = "TRANSFERFILES";
	public static final String TRANSFER_DIRECTORY = "TRANSFERDIRECTORY";
	public static final String ACCEPT_TRANSFER = "R_ACCEPTTRANSFER";
	
	public static final String DOWNLOAD_R_CANCELLED = "R_CANCELLED";
	public static final String DOWNLOAD_R_REFUSED = "R_REFUSED";
	public static final String DOWNLOAD_R_TIMEDOUT = "R_TIMEDOUT";
	public static final String DOWNLOAD_R_ERROR = "R_ERROR";
	
	public static final String DOWNLOAD_S_CANCELLED = "S_CANCELLED";
	public static final String DOWNLOAD_S_REFUSED = "S_REFUSED";
	public static final String DOWNLOAD_COMPLETE = "COMPLETE";
	public static final String DOWNLOAD_S_TIMEDOUT = "S_TIMEDOUT";
	public static final String DOWNLOAD_S_ERROR = "S_ERROR";
	public static final String DOWNLOAD_SETFILE = "SETFILE";
	
	public static final String senderHandleStyle = "-fx-font-family: Arial; -fx-text-fill: rgb(32, 186, 255); -fx-font-size: 13px;";
	public static final String localHandleStyle = "-fx-font-family: Arial; -fx-text-fill: rgb(255, 216, 23); -fx-font-size: 13px;";
	public static final String msgStyle = "-fx-font-family: Arial; -fx-text-fill: rgb(93%, 93%, 93%); -fx-font-size: 12px;";
	public static final String msgHoverStyle = "-fx-font-family: Arial; -fx-text-fill: rgb(186, 255, 32); -fx-font-size: 12px;";
	
	public static final String systemSenderStyle = "-fx-font-family: Arial; -fx-text-fill: rgb(255, 175, 38); -fx-font-size: 13px;";
	public static final String systemStyleInfo = "-fx-font-family: Arial; -fx-text-fill: rgb(93%, 93%, 93%); -fx-font-size: 12px;";
	public static final String systemStyleWarning = "-fx-font-family: Arial; -fx-text-fill: rgb(93%, 93%, 93%); -fx-font-size: 12px;";
	public static final String systemStyleSevere = "-fx-font-family: Arial; -fx-text-fill: rgb(255, 73, 11); -fx-font-size: 12px;";
	
	private static Clip incommingClip;
	private static Clip outgoingClip;
	private static Clip systemClip;
	
	private static ChatMain mMain;
	
	protected static void setMain(ChatMain main) {
		mMain = main;
	}
	
	public static String formatSystemMessage(String msg) {
		msg = BEGIN_SYSTEM + msg + END_SYSTEM;
		
		return msg;
	}
	
	public static String unFormatSystemMessage(String msg) {
		msg = msg.substring(BEGIN_SYSTEM.length(), msg.length() - END_SYSTEM.length());
		
		return msg;
	}
	
	public static boolean isSystemMessage(String msg) {
		if (msg.startsWith(BEGIN_SYSTEM) && msg.endsWith(END_SYSTEM) && msg.length() > BEGIN_SYSTEM.length() + END_SYSTEM.length()) {
			return true;
		} else {
			return false;
		}
	}
	
	public static String formatMessage(String msg) {
		msg = msg.replaceAll("\n", LINE_BREAK);
		
		return msg;
	}
	
	public static String unFormatMessage(String msg) {
		msg = msg.replaceAll(LINE_BREAK, Matcher.quoteReplacement("\n"));
		
		return msg;
	}
	
	public static String formatFilePath(String path) {
		String newpath = path.replace('/', File.separatorChar);
		newpath = newpath.replace('\\', File.separatorChar);
		if (newpath.indexOf(":") == 1 && newpath.indexOf(File.separatorChar) == 2) {
			newpath = (newpath.length() > 2) ? newpath.substring(2) : "Root" + File.separator;
		}
		if (newpath.indexOf(File.separatorChar) == 0) {
			if (newpath.length() > 2) {
				newpath = newpath.substring(1);
			} else if (newpath.length() > 1) {
				if (newpath.charAt(1) == File.separatorChar) {
					newpath = "Root" + File.separator;
				} else {
					newpath = newpath.substring(1);
				}
			} else {
				newpath = "Root" + File.separator;
			}
		}
		
		return newpath;
	}
	
	public static String formatFileSize(long size) {
		double kb = size / 1024d;
		double mb = kb / 1024d;
		double gb = mb / 1024d;
		double tb = gb / 1024d;
		
		if (tb >= 1) {
			return Double.toString(Math.round(tb * 100d) / 100d) + " TB";
		} else if (gb >= 1) {
			return Double.toString(Math.round(gb * 100d) / 100d) + " GB";
		} else if (mb >= 1) {
			return Double.toString(Math.round(mb * 100d) / 100d) + " MB";
		} else if (kb >= 1) {
			return Double.toString(Math.round(kb * 100d) / 100d) + " KB";
		} else {
			return Long.toString(Math.round(size)) + " Bytes";
		}
	}
	
	protected static void centerWindow(Stage window, Pane root, Stage centerStage) {
		GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		int width = gd.getDisplayMode().getWidth();
		int height = gd.getDisplayMode().getHeight();
		
		double x = ((centerStage.getWidth() / 2) + centerStage.getX()) - (root.getPrefWidth() / 2);
		double y = ((centerStage.getHeight() / 2) + centerStage.getY()) - (root.getPrefHeight() / 2);
		
		if (x < 0) {
			x = 0;
		} else if (x + root.getPrefWidth() > width) {
			x = width - root.getPrefWidth();
		}
		
		if (y < 0) {
			y = 0;
		} else if (y + root.getPrefHeight() > height) {
			y = height - root.getPrefHeight();
		}
		
		
		window.setX(x);
		window.setY(y);
	}
	
	protected static void popMessage(String title, String msg, boolean runLater) {
		if (!runLater) {
			try {
				final Stage dialogWin = new Stage(StageStyle.DECORATED);
				
				FXMLLoader loader = new FXMLLoader(Main.class.getResource("/atChat/resources/fxml/atMessageWindow.fxml"));
				AnchorPane msgRoot = (AnchorPane) loader.load();
				MessageWindow diagCont = (MessageWindow) loader.getController();
				diagCont.setMessage(msg, dialogWin);
				dialogWin.getIcons().addAll(new Image("/atChat/resources/images/@Chat_Icon_16_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_24_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_32_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_48_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_64_32bit.png"));
				//dialogWin.setResizable(false);
				dialogWin.setTitle(title);
				Scene scene = new Scene(msgRoot);
				scene.getStylesheets().clear();
				scene.getStylesheets().add(Main.class.getResource("/atChat/resources/fxml/atChatTheme.css").toExternalForm());
				dialogWin.setScene(scene);
				centerWindow(dialogWin, msgRoot, mMain.getStage());
				dialogWin.initOwner(mMain.getStage());
				dialogWin.initModality(Modality.APPLICATION_MODAL);
				dialogWin.show();
				double tmpW = scene.getWidth();
				double tmpH = scene.getHeight();
				double tmpX = dialogWin.getX();
				double tmpY = dialogWin.getY();
				dialogWin.setResizable(false);
				if (tmpW != scene.getWidth()) {
					dialogWin.setWidth(dialogWin.getWidth() - (scene.getWidth() - tmpW));
					dialogWin.setX(tmpX);
				}
				if (tmpH != scene.getHeight()) {
					dialogWin.setHeight(dialogWin.getHeight() - (scene.getHeight() - tmpH));
					dialogWin.setY(tmpY);
				}
			} catch (IOException e) {
				System.out.println("Unable to create message window:" + System.getProperty("line.separator") + e.getMessage());
				Logger.getLogger(Main.class.getName()).log(Level.WARNING, "Unable to create message window:" + System.getProperty("line.separator") + e.getMessage(), e);
			}
		} else {
			Platform.runLater(new ChatCommon().new PopMessage(title, msg));
		}
	}
	
	protected static void popError(String error, boolean runLater) {
		if (!runLater) {
			try {
				final Stage dialogWin = new Stage(StageStyle.DECORATED);
				
				FXMLLoader loader = new FXMLLoader(Main.class.getResource("/atChat/resources/fxml/atMessageWindow.fxml"));
				AnchorPane msgRoot = (AnchorPane) loader.load();
				MessageWindow diagCont = (MessageWindow) loader.getController();
				diagCont.setMessage(error, dialogWin);
				dialogWin.getIcons().addAll(new Image("/atChat/resources/images/@Chat_Icon_16_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_24_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_32_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_48_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_64_32bit.png"));
				//dialogWin.setResizable(false);
				dialogWin.setTitle("Error");
				Scene scene = new Scene(msgRoot);
				scene.getStylesheets().clear();
				scene.getStylesheets().add(Main.class.getResource("/atChat/resources/fxml/atChatTheme.css").toExternalForm());
				dialogWin.setScene(scene);
				centerWindow(dialogWin, msgRoot, mMain.getStage());
				dialogWin.initOwner(mMain.getStage());
				dialogWin.initModality(Modality.APPLICATION_MODAL);
				dialogWin.show();
				double tmpW = scene.getWidth();
				double tmpH = scene.getHeight();
				double tmpX = dialogWin.getX();
				double tmpY = dialogWin.getY();
				dialogWin.setResizable(false);
				if (tmpW != scene.getWidth()) {
					dialogWin.setWidth(dialogWin.getWidth() - (scene.getWidth() - tmpW));
					dialogWin.setX(tmpX);
				}
				if (tmpH != scene.getHeight()) {
					dialogWin.setHeight(dialogWin.getHeight() - (scene.getHeight() - tmpH));
					dialogWin.setY(tmpY);
				}
			} catch (IOException e) {
				System.out.println("Unable to create message window:" + System.getProperty("line.separator") + e.getMessage());
				Logger.getLogger(Main.class.getName()).log(Level.WARNING, "Unable to create message window:" + System.getProperty("line.separator") + e.getMessage(), e);
			}
		} else {
			Platform.runLater(new ChatCommon().new PopMessage("Error", error));
		}
	}
	
	private class PopMessage implements Runnable {
		private final String msg;
		private final String title;
		
		public PopMessage(String title, String msg) {
			this.msg = msg;
			this.title = title;
		}
		
		@Override
		public void run() {
			try {
				final Stage dialogWin = new Stage(StageStyle.DECORATED);
				
				FXMLLoader loader = new FXMLLoader(Main.class.getResource("/atChat/resources/fxml/atMessageWindow.fxml"));
				AnchorPane msgRoot = (AnchorPane) loader.load();
				MessageWindow diagCont = (MessageWindow) loader.getController();
				diagCont.setMessage(msg, dialogWin);
				dialogWin.getIcons().addAll(new Image("/atChat/resources/images/@Chat_Icon_16_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_24_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_32_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_48_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_64_32bit.png"));
				//dialogWin.setResizable(false);
				dialogWin.setTitle(title);
				Scene scene = new Scene(msgRoot);
				scene.getStylesheets().clear();
				scene.getStylesheets().add(Main.class.getResource("/atChat/resources/fxml/atChatTheme.css").toExternalForm());
				dialogWin.setScene(scene);
				centerWindow(dialogWin, msgRoot, mMain.getStage());
				dialogWin.initOwner(mMain.getStage());
				dialogWin.initModality(Modality.APPLICATION_MODAL);
				dialogWin.show();
				double tmpW = scene.getWidth();
				double tmpH = scene.getHeight();
				double tmpX = dialogWin.getX();
				double tmpY = dialogWin.getY();
				dialogWin.setResizable(false);
				if (tmpW != scene.getWidth()) {
					dialogWin.setWidth(dialogWin.getWidth() - (scene.getWidth() - tmpW));
					dialogWin.setX(tmpX);
				}
				if (tmpH != scene.getHeight()) {
					dialogWin.setHeight(dialogWin.getHeight() - (scene.getHeight() - tmpH));
					dialogWin.setY(tmpY);
				}
			} catch (IOException e) {
				System.out.println("Unable to create error window:" + System.getProperty("line.separator") + e.getMessage());
				Logger.getLogger(Main.class.getName()).log(Level.WARNING, "Unable to create error window:" + System.getProperty("line.separator") + e.getMessage(), e);
			}
		}
	}
	
	protected static void popAcceptWindow(HostConnection.Client client, CryptoRSA clientRSA, String handle, int connectingPort, String connectingIP) {
		Platform.runLater(new ChatCommon().new PopAccept(client, clientRSA, handle + " on port: " + Integer.toString(connectingPort) + " from IP: " + connectingIP));
	}
	
	private class PopAccept implements Runnable {
		private final HostConnection.Client client;
		private final CryptoRSA clientRSA;
		private final String connectingParty;
		
		public PopAccept(HostConnection.Client client, CryptoRSA clientRSA, String connectingParty) {
			this.client = client;
			this.clientRSA = clientRSA;
			this.connectingParty = connectingParty;
		}
		
		@Override
		public void run() {
			try {
				final Stage dialogWin = new Stage(StageStyle.DECORATED);
				
				FXMLLoader loader = new FXMLLoader(Main.class.getResource("/atChat/resources/fxml/atAcceptConnection.fxml"));
				AnchorPane acceptRoot = (AnchorPane) loader.load();
				AcceptConnectionWindow diagCont = (AcceptConnectionWindow) loader.getController();
				diagCont.initWindow(dialogWin, client, clientRSA, connectingParty, mMain);
				dialogWin.getIcons().addAll(new Image("/atChat/resources/images/@Chat_Icon_16_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_24_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_32_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_48_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_64_32bit.png"));
				//dialogWin.setResizable(false);
				dialogWin.setTitle("Accept New Client");
				Scene scene = new Scene(acceptRoot);
				scene.getStylesheets().clear();
				scene.getStylesheets().add(Main.class.getResource("/atChat/resources/fxml/atChatTheme.css").toExternalForm());
				dialogWin.setScene(scene);
				centerWindow(dialogWin, acceptRoot, mMain.getStage());
				dialogWin.initOwner(mMain.getStage());
				dialogWin.show();
				double tmpW = scene.getWidth();
				double tmpH = scene.getHeight();
				double tmpX = dialogWin.getX();
				double tmpY = dialogWin.getY();
				dialogWin.setResizable(false);
				if (tmpW != scene.getWidth()) {
					dialogWin.setWidth(dialogWin.getWidth() - (scene.getWidth() - tmpW));
					dialogWin.setX(tmpX);
				}
				if (tmpH != scene.getHeight()) {
					dialogWin.setHeight(dialogWin.getHeight() - (scene.getHeight() - tmpH));
					dialogWin.setY(tmpY);
				}
			} catch (IOException e) {
				System.out.println("Unable to create accept new client window:" + System.getProperty("line.separator") + e.getMessage());
				Logger.getLogger(Main.class.getName()).log(Level.WARNING, "Unable to create accept new client window:" + System.getProperty("line.separator") + e.getMessage(), e);
			}
		}
	}
	
	protected static void popAcceptFiles(final String[] files, final long[] fileSizes, final FileSelector[] selectors, final String handle, final int key, final String clientKey, final int remotePort, final HostConnection.Client client) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				try {
					final Stage dialogWin = new Stage(StageStyle.DECORATED);
					
					FXMLLoader loader = new FXMLLoader(Main.class.getResource("/atChat/resources/fxml/atAcceptFiles.fxml"));
					AnchorPane acceptRoot = (AnchorPane) loader.load();
					AcceptFileTransfer diagCont = (AcceptFileTransfer) loader.getController();
					diagCont.initWindow(files, fileSizes, selectors, dialogWin, handle, key, clientKey, remotePort, mMain, client);
					dialogWin.getIcons().addAll(new Image("/atChat/resources/images/@Chat_Icon_16_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_24_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_32_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_48_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_64_32bit.png"));
					//dialogWin.setResizable(false);
					dialogWin.setTitle("Accept File Transfer");
					Scene scene = new Scene(acceptRoot);
					scene.getStylesheets().clear();
					scene.getStylesheets().add(Main.class.getResource("/atChat/resources/fxml/atChatTheme.css").toExternalForm());
					dialogWin.setScene(scene);
					centerWindow(dialogWin, acceptRoot, mMain.getStage());
					dialogWin.initOwner(mMain.getStage());
					dialogWin.show();
					double tmpW = scene.getWidth();
					double tmpH = scene.getHeight();
					double tmpX = dialogWin.getX();
					double tmpY = dialogWin.getY();
					dialogWin.setResizable(false);
					if (tmpW != scene.getWidth()) {
						dialogWin.setWidth(dialogWin.getWidth() - (scene.getWidth() - tmpW));
						dialogWin.setX(tmpX);
					}
					if (tmpH != scene.getHeight()) {
						dialogWin.setHeight(dialogWin.getHeight() - (scene.getHeight() - tmpH));
						dialogWin.setY(tmpY);
					}
				} catch (IOException e) {
					System.out.println("Unable to create accept file transfer window:" + System.getProperty("line.separator") + e.getMessage());
					Logger.getLogger(Main.class.getName()).log(Level.WARNING, "Unable to create accept file transfer window:" + System.getProperty("line.separator") + e.getMessage(), e);
				}
			}
		});
	}
	
	protected static void popAcceptDirectory(final TreeItem<String> files, final long[] fileSizes, final LinkedHashMap<String, FileSelector> fSelectors, final String handle, final int key, final String clientKey, final int remotePort, final HostConnection.Client client) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				try {
					final Stage dialogWin = new Stage(StageStyle.DECORATED);
					
					FXMLLoader loader = new FXMLLoader(Main.class.getResource("/atChat/resources/fxml/atAcceptDirectory.fxml"));
					AnchorPane acceptRoot = (AnchorPane) loader.load();
					AcceptDirectoryTransfer diagCont = (AcceptDirectoryTransfer) loader.getController();
					diagCont.initWindow(files, fileSizes, fSelectors, dialogWin, handle, key, clientKey, remotePort, mMain, client);
					dialogWin.getIcons().addAll(new Image("/atChat/resources/images/@Chat_Icon_16_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_24_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_32_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_48_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_64_32bit.png"));
					//dialogWin.setResizable(false);
					dialogWin.setTitle("Accept Directory Transfer");
					Scene scene = new Scene(acceptRoot);
					scene.getStylesheets().clear();
					scene.getStylesheets().add(Main.class.getResource("/atChat/resources/fxml/atChatTheme.css").toExternalForm());
					dialogWin.setScene(scene);
					centerWindow(dialogWin, acceptRoot, mMain.getStage());
					dialogWin.initOwner(mMain.getStage());
					dialogWin.show();
					double tmpW = scene.getWidth();
					double tmpH = scene.getHeight();
					double tmpX = dialogWin.getX();
					double tmpY = dialogWin.getY();
					dialogWin.setResizable(false);
					if (tmpW != scene.getWidth()) {
						dialogWin.setWidth(dialogWin.getWidth() - (scene.getWidth() - tmpW));
						dialogWin.setX(tmpX);
					}
					if (tmpH != scene.getHeight()) {
						dialogWin.setHeight(dialogWin.getHeight() - (scene.getHeight() - tmpH));
						dialogWin.setY(tmpY);
					}
				} catch (IOException e) {
					System.out.println("Unable to create accept directory transfer window:" + System.getProperty("line.separator") + e.getMessage());
					Logger.getLogger(Main.class.getName()).log(Level.WARNING, "Unable to create accept directory transfer window:" + System.getProperty("line.separator") + e.getMessage(), e);
				}
			}
		});
	}
	
	public class parseTransferRequest implements Runnable {
		private final String[] sMsg;
		private final String handle;
		private final int key;
		private final String clientKey;
		private final int remotePort;
		private final HostConnection.Client client;
		
		private final boolean isDir;
		private LinkedHashMap<String, FileSelector> fSelectors;
		private int newLoc = 0;
		
		public parseTransferRequest(String[] msg, String handle, int key, String clientKey, int remotePort, HostConnection.Client client, boolean isDir) {
			sMsg = msg;
			this.handle = handle;
			this.key = key;
			this.clientKey = clientKey;
			this.remotePort = remotePort;
			this.client = client;
			
			this.isDir = isDir;
			
			fSelectors = new LinkedHashMap<String, FileSelector>();
		}
		
		@Override
		public void run() {
			boolean cancelled = false;
			String[] files;
			if (client == null) {
				files = Arrays.copyOfRange(sMsg, 4, sMsg.length);
			} else {
				files = Arrays.copyOfRange(sMsg, 2, sMsg.length);
			}
			if (files.length % 2 == 0) {
				String[] fileNames = new String[files.length / 2];
				long[] fileSizes = new long[fileNames.length];
				FileSelector[] fileSelectors =  null;
				boolean success = true;
				if (!isDir) {
					fileSelectors = new FileSelector[fileNames.length];
					for (int x = 0; x < files.length; x ++) {
						if (mMain.isConnected()) {
							fileNames[x / 2] = files[x];
							fileSelectors[x / 2] = new FileSelector(files[x]);
							try {
								fileSizes[(x / 2)] = Long.parseLong(files[x + 1]);
							} catch (NumberFormatException sizeE) {
								success = false;
								break;
							}
							x++;
						} else {
							cancelled = true;
							break;
						}
					}
				} else {
					for (int x = 0; x < files.length; x ++) {
						if (mMain.isConnected()) {
							fileNames[x / 2] = formatFilePath(files[x]);
							try {
								fileSizes[(x / 2)] = Long.parseLong(files[x + 1]);
							} catch (NumberFormatException sizeE) {
								success = false;
								break;
							}
							x++;
						} else {
							cancelled = true;
							break;
						}
					}
				}
				if (success && !cancelled && mMain.isConnected()) {
					if (client != null) {
						if (client.isAccepted()) {
							if (!isDir) {
								popAcceptFiles(fileNames, fileSizes, fileSelectors, handle, key, clientKey, remotePort, client);
							} else {
								TreeItem<String> rootNode = createTree(fileNames, fileSizes);
								popAcceptDirectory(rootNode, fileSizes, fSelectors, handle, key, clientKey, remotePort, client);
							}
						}
					} else {
						if (!isDir) {
							popAcceptFiles(fileNames, fileSizes, fileSelectors, handle, key, clientKey, remotePort, null);
						} else {
							TreeItem<String> rootNode = createTree(fileNames, fileSizes);
							popAcceptDirectory(rootNode, fileSizes, fSelectors, handle, key, clientKey, remotePort, null);
						}
					}
				} else if (!cancelled && mMain.isConnected()) {
					if (client == null) {
						mMain.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(key)), true);
					} else if (client.isAccepted()) {
						ArrayList<HostConnection.Client> sendTo = new ArrayList<HostConnection.Client>();
						sendTo.add(client);
						mMain.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(key)), sendTo, false, false, null);
					}
				}
			} else if (mMain.isConnected()) {
				if (client == null) {
					mMain.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(key)), true);
				} else if (client.isAccepted()) {
					ArrayList<HostConnection.Client> sendTo = new ArrayList<HostConnection.Client>();
					sendTo.add(client);
					mMain.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(key)), sendTo, false, false, null);
				}
			}
		}
		
		private TreeItem<String> createTree(String[] files, long[] sizes) {
			FileSelector rootSel = new FileSelector(files[0], sizes[0]);
			fSelectors.put(files[0], rootSel);
			TreeItem<String> root = rootSel.getItem();
			
			for (int t = 1; t < files.length; t ++) {
				String file = files[t];
				if (!file.endsWith(File.separator)) {
					FileSelector nodeSel = new FileSelector(file, sizes[t]);
					fSelectors.put(file, nodeSel);
					root.getChildren().add(nodeSel.getItem());
				} else {
					root.getChildren().add(recursiveTree(files, t, sizes));
					t = newLoc;
				}
			}
			
			return root;
		}
		
		private TreeItem<String> recursiveTree(String[] files, int startLoc, long[] sizes) {
			String parentDir = files[startLoc];
			FileSelector parentSel = new FileSelector(parentDir, sizes[startLoc]);
			TreeItem<String> subRoot = parentSel.getItem();
			fSelectors.put(files[startLoc], parentSel);
			
			if (files.length > startLoc + 1) {
				for (int n = startLoc + 1; n < files.length; n ++) {
					String file = files[n];
					if (file.startsWith(parentDir)) {
						if (!file.endsWith(File.separator)) {
							FileSelector nodeSel = new FileSelector(file, sizes[n]);
							subRoot.getChildren().add(nodeSel.getItem());
							fSelectors.put(file, nodeSel);
						} else {
							subRoot.getChildren().add(recursiveTree(files, n, sizes));
							n = newLoc;
						}
						if (n == files.length - 1) {
							newLoc = n;
						}
					} else {
						newLoc = n - 1;
						break;
					}
					
				}
			}
			
			return subRoot;
		}
	}
	
	public class DownloadCalculator {
		private long lastCalc;
		private long[] bytes = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		private long[] times = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		private int currentLoc = 9;
		private long currentAmount = 0;
		
		private final Label etaLabel;
		private final Label speedLabel;
		
		public DownloadCalculator(Label eta, Label speed) {
			lastCalc = System.currentTimeMillis();
			etaLabel = eta;
			speedLabel = speed;
		}
		
		public boolean calc(long byteAmount, long remaining) {
			currentAmount += byteAmount;
			
			if (System.currentTimeMillis() - lastCalc >= 1000) {
				currentLoc = (currentLoc == 9) ? 0 : currentLoc + 1;
				bytes[currentLoc] = currentAmount;
				times[currentLoc] = System.currentTimeMillis() - lastCalc;
				currentAmount = 0;
				
				long totalBytes = 0;
				long totalTimes = 0;
				for (int i = 0; i < bytes.length; i ++) {
					totalBytes += bytes[i];
					totalTimes += times[i];
				}
				
				if (totalTimes >= 0) {
					double seconds = (double) totalTimes / (double) 1000;
					double bytesPerSecond = totalBytes / seconds;
					
					double secondsRemaining = remaining / bytesPerSecond;
					
					setLabels("ETA:  " + formatTimeRemaining(secondsRemaining), "Rate:  " + formatSpeed(bytesPerSecond));
				} else {
					setLabels("ETA:  ??", "Rate:  ??");
				}
				
				lastCalc = System.currentTimeMillis();
				
				return true;
			} else {
				return false;
			}
		}
		
		private void setLabels(final String eta, final String speed) {
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					etaLabel.setText(eta);
					speedLabel.setText(speed);
				}
			});
		}
		
		private String formatSpeed(double bytesPerSecond) {
			double kb = bytesPerSecond / 1024;
			double mb = kb / 1024;
			double gb = mb / 1024;
			
			if (gb >= 1) {
				return Float.toString(Math.round(gb * 100) / 100f) + "GB/s";
			} else if (mb >= 1) {
				return Float.toString(Math.round(mb * 100) / 100f) + "MB/s";
			} else if (kb >= 1) {
				return Float.toString(Math.round(kb * 100) / 100f) + "KB/s";
			} else {
				return Float.toString(Math.round(bytesPerSecond * 100) / 100f) + "B/s";
			}
		}
		
		private String formatTimeRemaining (double secondsRemaining) {
			int seconds = (int) Math.floor(((secondsRemaining / (double) 60) - Math.floor(secondsRemaining / (double) 60)) * (double) 60);
			int minutes = (int) Math.floor((((secondsRemaining / 60) / 60) - Math.floor((secondsRemaining / 60) / 60)) * 60);
			int hours = (int) Math.floor((secondsRemaining / 60) / 60);
			
			if (hours <= 99) {
				String timeRemaining = Integer.toString(hours) + ":";
				if (minutes >= 10) {
					timeRemaining += Integer.toString(minutes) + ":";
				} else {
					timeRemaining += "0" + Integer.toString(minutes) + ":";
				}
				if (seconds >= 10) {
					timeRemaining += Integer.toString(seconds);
				} else {
					timeRemaining += "0" + Integer.toString(seconds);
				}
				
				return timeRemaining;
			} else {
				return ">99 hours";
			}
		}
		
		public void setLastTime(long time) {
			lastCalc = time;
		}
	}
	
	public static void playIncomming() {
		incommingClip.setFramePosition(0);
		incommingClip.start();
	}
	
	public static void playOutgoing() {
		outgoingClip.setFramePosition(0);
		outgoingClip.start();
	}
	
	public static void playSystem() {
		systemClip.setFramePosition(0);
		systemClip.start();
	}
	
	public static void loadAudioClips() {
		AudioInputStream incomming = null;
		AudioInputStream outgoing = null;
		AudioInputStream systemSound = null;
		try {
			incomming = AudioSystem.getAudioInputStream(Main.class.getResource("/atChat/resources/sounds/incoming_message.wav"));
			incommingClip = AudioSystem.getClip();
			incommingClip.open(incomming);
			
			outgoing = AudioSystem.getAudioInputStream(Main.class.getResource("/atChat/resources/sounds/outgoing_message.wav"));
			outgoingClip = AudioSystem.getClip();
			outgoingClip.open(outgoing);
			
			systemSound = AudioSystem.getAudioInputStream(Main.class.getResource("/atChat/resources/sounds/system_message.wav"));
			systemClip = AudioSystem.getClip();
			systemClip.open(systemSound);
		} catch (UnsupportedAudioFileException aE) {
			if (aE.getMessage() != null) {
				System.out.println("Unable to play sound clip:" + System.getProperty("line.separator") + aE.getMessage());
			}
		} catch (IOException ioE) {
			if (ioE.getMessage() != null) {
				System.out.println("Unable to play sound clip:" + System.getProperty("line.separator") + ioE.getMessage());
			}
		} catch (LineUnavailableException lineE) {
			if (lineE.getMessage() != null) {
				System.out.println("Unable to play sound clip:" + System.getProperty("line.separator") + lineE.getMessage());
			}
		} finally {
			if (incomming != null) {
				try {
					incomming.close();
				} catch (IOException e) {
					
				}
			}
			
			if (outgoing != null) {
				try {
					outgoing.close();
				} catch (IOException e) {
					
				}
			}
			
			if (systemSound != null) {
				try {
					systemSound.close();
				} catch (IOException e) {
					
				}
			}
		}
	}
	
	public static void closeClips() {
		if (outgoingClip != null) {
			outgoingClip.close();
		}
		if (incommingClip != null) {
			incommingClip.close();
		}
		if (systemClip != null) {
			systemClip.close();
		}
	}
}
