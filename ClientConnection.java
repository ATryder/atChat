package atChat;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;

public class ClientConnection {
	private static final long TIMEOUT = 60000 * 7;
	private static final long SLEEPTIME = (long) 55;
	
	private boolean CONNECTED = false;
	
	private ConnectingProgress connectionProgressWindow;
	private boolean cancelConnection = false;
	
	private SocketChannel chatSocket;
	private SocketIO sIO;
	private final ChatMain chatWindow;
	
	private String hostHandle = "";
	private LinkedHashMap<String, Client> clients = new LinkedHashMap<String, Client>();
	
	private ArrayList<String> messageQueue = new ArrayList<String>();
	
	private ChatListener chatListener;
	private Thread chatListenThread;
	
	private MessageSender chatSender;
	private Thread chatSendThread;
	
	private FileSender fileSender = null;
	private Thread fileSenderThread = null;
	
	private FileReceiver fileReceiver = null;
	private Thread fileReceiverThread = null;
	
	private Thread newSocketThread;
	
	protected CryptoAES aes;
	
	public ClientConnection (ChatMain chatWindow, String password, ConnectingProgress connectionWindow) {
		this.chatWindow = chatWindow;
		connectionProgressWindow = connectionWindow;
		createChatSocket(password);
		
		sIO = new SocketIO();
	}
	
	protected void cancelConnecting() {
		cancelConnection = true;
		if (newSocketThread != null) {
			if (newSocketThread.isAlive()) {
				newSocketThread.interrupt();
			}
		}
	}
	
	public String getHostHandle() {
		return hostHandle;
	}
	
	protected FileSender getFileSender() {
		return fileSender;
	}
	
	protected FileReceiver getFileReceiver() {
		return fileReceiver;
	}
	
	public boolean isConnected() {
		return CONNECTED;
	}
	
	protected void removeConnectionProgressWindow() {
		connectionProgressWindow.dismissWindow();
		connectionProgressWindow = null;
	}
	
	protected void setConnected(boolean connected) {
		CONNECTED = connected;
	}
	
	protected class Client {
		private final String handle;
		private final String address;
		
		public Client(String handle, String address) {
			this.handle = handle;
			this.address = address;
			
			clients.put(address, this);
			chatWindow.addParticipant(clients.size() + 1, handle);
		}
	}
	
	protected void createChatSocket(String password) {
		if (!cancelConnection) {
			NewChatSocket newSock = null;
			String error = "";
			try {
				newSock = new NewChatSocket(ChatPreferences.rIPAddress, ChatPreferences.lChatPort, password);
			} catch (UnknownHostException e) {
				error = (e.getMessage() != null) ? e.getMessage() : "Unspecified";
			} finally {
				if (newSock != null && error.length() == 0 && !cancelConnection) {
					newSocketThread = new Thread(newSock);
					newSocketThread.start();
				} else if (!cancelConnection){
					startDisconnect();
					System.out.println("Unable to parse server IP and port:" + System.getProperty("line.separator") + error);
					//pop error unable to create InetAddress
					ChatCommon.popError("Unable to parse server IP and port:" + System.getProperty("line.separator") + error, false);
				} else {
					startDisconnect();
				}
			}
		} else {
			startDisconnect();
		}
	}
	
	private class NewChatSocket implements Runnable {
		private final InetAddress hostIP;
		private final String password;
		
		private SocketChannel tmpSock = null;
		
		public NewChatSocket(String ip, int port, String password) throws UnknownHostException {
			hostIP = InetAddress.getByName(ip);
			this.password = password;
		}
		
		private class ConnectionCanceller implements Runnable {
			@Override
			public void run() {
				while (newSocketThread != null) {
					if (cancelConnection) {
						if (tmpSock != null) {
							try {
								tmpSock.close();
							} catch (IOException ioE) {
								newSocketThread.stop();
								newSocketThread = null;
								startDisconnect();
							}
						}
					}
				}
			}
		}
		
		@Override
		public void run() {
			String tmpErr = "";
			
			Thread newSockCancel = new Thread(new ConnectionCanceller());
			newSockCancel.start();
			
			try {
				tmpSock = SocketChannel.open();
				tmpSock.connect(new InetSocketAddress(hostIP, ChatPreferences.rChatPort));
			} catch (IOException e) {
				tmpErr = (e.getMessage() != null) ? e.getMessage() : "Unspecified";
			} finally {
				if (tmpSock == null || tmpErr.length() != 0) {
					if (tmpSock != null) {
						try {
							tmpSock.close();
						} catch (IOException ioE) {
							System.out.println("Creating client connection failed and unable to close socket:" + System.getProperty("line.separator") + ioE.getMessage());
							Logger.getLogger(ClientConnection.class.getName()).log(Level.WARNING, "Creating client connection failed, unable to close socket:" + System.getProperty("line.separator") + ioE.getMessage(), ioE);
						}
					}
					startDisconnect();
					//pop error unable to create socket
					if (!cancelConnection) {
						ChatCommon.popError("Unable to create socket:" + System.getProperty("line.separator") + tmpErr, true);
						System.out.println("Unable to create socket:" + System.getProperty("line.separator") + tmpErr);
					}
				} else if (tmpErr.length() == 0 && tmpSock.isConnected()) {
					//success
					try {
						tmpSock.configureBlocking(false);
					} catch (IOException ioE) {
						tmpErr = (ioE.getMessage() != null) ? ioE.getMessage() : "Unspecified";
					} finally {
						if (tmpErr != null) {
							handShake(tmpSock);
						} else {
							ChatCommon.popError("Unable to create socket:" + System.getProperty("line.separator") + tmpErr, true);
							System.out.println("Unable to create socket:" + System.getProperty("line.separator") + tmpErr);
							startDisconnect();
						}
					}
				}
				newSocketThread = null;
			}
		}
		
		private void handShake(SocketChannel tmpSock) {
			boolean requiresPass = false;
			boolean timedOut = false;
			long startTime = System.currentTimeMillis();
			int phase = 0;
			String refused = "";
			boolean accepted = false;
			String error = "";
			
			CryptoRSA hostRSA = null;
			CryptoRSA clientRSA = null;
			
			try {
				while (phase < 5 && !cancelConnection) {
					if (System.currentTimeMillis() - startTime >= TIMEOUT) {
						timedOut = true;
						break;
					}
					switch (phase) {
					case 0:
						byte[] hostKey = sIO.readBytes(tmpSock);
						if (hostKey != null) {
							if (hostKey.length > 1) {
								try {
									hostRSA = new CryptoRSA(hostKey);
									clientRSA = new CryptoRSA();
									
									sIO.writeBytes(tmpSock, clientRSA.pubKey);
									startTime = System.currentTimeMillis();
									phase ++;
								} catch (Exception cE) {
									error = "Host sent an invalid RSA public key.";
									cancelConnecting();
									phase = 10;
								}
							} else if (hostKey.length == 1) {
								refused = "Host is not accepting new connections.";
								phase = 10;
							} else {
								error = "Host disconnected.";
								cancelConnecting();
								phase = 10;
							}
						}
						break;
					case 1:
						//Does the host require a password?
						String input = sIO.readBytes(tmpSock, clientRSA);
						if (input != null) {
							if (!input.equals("\n")) {
								String[] split = input.split(ChatCommon.SPLIT_SEPARATOR);
								if (split[0].equals(ChatCommon.REFUSED)) {
									//host is not listening for new connections
									refused = "Host is not accepting new connections.";
									phase = 10;
								} else if (split[0].equals(ChatCommon.REQ_PASSWORD)) {
									requiresPass = true;
									phase ++;
								} else if (split[0].equals(ChatCommon.NO_PASSWORD)) {
									phase ++;
								}
								
								if (split.length >= 2) {
									hostHandle = (split[1].length() > 0) ? split[1] : "No handle";
								} else {
									hostHandle = "No handle";
								}
								
								if (split.length >= 3) {
									try {
										float version = Float.parseFloat(split[2]);
										if (version != ChatPreferences.VERSION) {
											refused = "Incompatible version of @Chat.  The host is using version " + split[2];
											phase = 10;
										} else {
											startTime = System.currentTimeMillis();
										}
									} catch (NumberFormatException numE) {
										refused = "Incompatible version of @Chat.  The host is using version " + split[2];
										phase = 10;
									}
								} else {
									refused = "Incompatible version of @Chat.  The host is using version 1.0";
									phase = 10;
								}
							} else {
								error = "The host disconnected";
								cancelConnecting();
								phase = 10;
							}
						}
						break;
					case 2:
						//Send password if required and handle
						String infoSend = (requiresPass) ? password + ChatCommon.SEPARATOR + ChatPreferences.handle : ChatPreferences.handle;
						sIO.writeBytes(tmpSock, infoSend, hostRSA);
						
						startTime = System.currentTimeMillis();
						phase ++;
						break;
					case 3:
						String accept = sIO.readBytes(tmpSock, clientRSA);
						
						if (accept != null) {
							if (!accept.equals("\n")) {
								String[] split = accept.split(ChatCommon.SPLIT_SEPARATOR);
								if (split.length > 0) {
									if (split[0].equals(ChatCommon.REFUSED)){
										if (split.length >= 2) {
											refused = split[1];
										} else {
											refused = "No reason supplied.";
										}
										phase ++;
									} else if (split[0].equals(ChatCommon.ACCEPTED)) {
										accepted = true;
										if (split.length >= 2) {
											hostHandle = (split[1].length() > 0) ? split[1] : "No handle";
										} else {
											hostHandle = "No Handle";
										}
										Platform.runLater(new GetSessionInfo(split));
										
										phase ++;
									}
								}
							} else {
								error = "The host disconnected";
								cancelConnecting();
								accepted = false;
								phase = 10;
							}
						}
						break;
					case 4:
						byte[] aesKey = sIO.readBytesB(tmpSock, clientRSA);
						if (aesKey != null) {
							if (aesKey.length > 0) {
								try {
									aes = new CryptoAES(aesKey);
									phase ++;
								} catch (Exception aesE) {
									error = "Thost sent an invalid AES encryption key.";
									cancelConnecting();
									accepted = false;
									phase = 10;
								}
							} else {
								error = "The host disconnected";
								cancelConnecting();
								accepted = false;
								phase = 10;
							}
						}
						break;
					}
				}
				
			} catch (IOException e) {
				error = (e.getMessage() != null) ? e.getMessage() : "Unspecified";
			} finally {
				if (error.length() == 0 && !cancelConnection) {
					if (!timedOut && accepted) {
						//connection accepted
						chatSocket = tmpSock;
						String confirmError = "";
						int numErrors = 0;
						do {
							boolean errored = false;
							try {
								sIO.writeBytes(chatSocket, ChatCommon.formatSystemMessage(ChatCommon.CONFIRM), aes);
							} catch (IOException confirmE) {
								errored = true;
								confirmError = (confirmE.getMessage() != null) ? confirmE.getMessage() : "Unspecified";
							} finally {
								if (!errored) {
									setConnected(true);
									Platform.runLater(new Runnable() {
										@Override
										public void run() {
											connectionProgressWindow.setToDismiss();
										}
									});
									chatListener = new ChatListener();
									chatListenThread = new Thread(chatListener);
									chatListenThread.start();
									chatSender = new MessageSender(true);
									chatSendThread = new Thread(chatSender);
									chatSendThread.start();
									
									fileSender = new FileSender(chatWindow);
									fileSenderThread = new Thread(fileSender);
									fileSenderThread.start();
									
									fileReceiver = new FileReceiver(chatWindow);
									fileReceiverThread = new Thread(fileReceiver);
									fileReceiverThread.start();
									
									numErrors = 0;
								} else {
									numErrors ++;
									if (numErrors >= 9) {
										startDisconnect();
										ChatCommon.popError("Unable to send connection confirmation:" + System.getProperty("line.separator") + confirmError, true);
									}
								}
							}
						} while (numErrors > 0 && numErrors < 9);
					} else if (timedOut) {
						//timedout
						ChatCommon.popMessage("Timed Out", "The connection timed out.", true);
						startDisconnect();
					} else {
						//refused
						ChatCommon.popMessage("Connection Refused", "The connection was refused by the host for the following reason:" + System.getProperty("line.separator") + refused, true);
						startDisconnect();
					}
				} else if (!cancelConnection) {
					startDisconnect();
					System.out.println("Unable to complete handshake with server:" + System.getProperty("line.separator") + error);
					ChatCommon.popError("Unable to complete handshake with server:" + System.getProperty("line.separator") + error, true);
				} else {
					startDisconnect();
				}
			}
		}
	}
	
	private class GetSessionInfo implements Runnable {
		private final String[] info;
		
		public GetSessionInfo(String[] input) {
			info = input;
		}
		
		@Override
		public void run() {
			chatWindow.addParticipant(0, hostHandle);
			if (info.length >= 5) {
				boolean numRead = true;
				int numberOfClients = 0;
				try {
					numberOfClients = Integer.parseInt(info[2]);
				} catch (NumberFormatException numE) {
					numRead = false;
				}
				if (numRead) {
					if (info.length >= (numberOfClients * 2) + 3) {
						for (int c = 3; c < (numberOfClients * 2) + 3; c ++) {
							new Client(info[c], info[c + 1]);
							c++;
						}
					}
				}
			}
			chatWindow.setConnectButtons(true, false);
		}
	}
	
	private class ChatListener implements Runnable {
		private int numberOfErrors = 0;
		private static final int maxErrors = 10;
		
		@Override
		public void run() {
			String error = "";
			while (isConnected()) {
				String input = null;
				try {
					input = sIO.readBytes(chatSocket, aes);
				} catch (IOException e) {
					error = (e.getMessage() != null) ? e.getMessage() : "Unspecified";
				} finally {
					if (input != null && error.length() == 0) {
						if (!input.equals("\n")) {
							numberOfErrors = 0;
							int sepIndex = input.indexOf(ChatCommon.SEPARATOR);
							String fromName = null;
							if (sepIndex > -1) {
								fromName = input.substring(0, sepIndex);
								input = input.substring(sepIndex + 1, input.length());
							}
							if (fromName != null) {
								Platform.runLater(new PostMessage((ChatCommon.isSystemMessage(input)) ? input : ChatCommon.unFormatMessage(input), (fromName.length() > 0) ? fromName : "No handle", false));
							} else {
								Platform.runLater(new PostMessage((ChatCommon.isSystemMessage(input)) ? input : ChatCommon.unFormatMessage(input), null, false));
							}
							try {
								Thread.sleep((long) SLEEPTIME);
							} catch (InterruptedException ie) {
								
							}
						} else {
							Platform.runLater(new PostMessage(ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT), null, false));
						}
					} else if (error.length() > 0) {
						numberOfErrors ++;
						if (numberOfErrors >= maxErrors && isConnected()) {
							startDisconnect();
							Platform.runLater(new PostMessage(ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT), null, true));
							ChatCommon.popError("Socket input stream failed:" + System.getProperty("line.separator") + error, true);
							System.out.println("Socket input stream failed:" + System.getProperty("line.separator") + error);
						}
					}
				}
			}
		}
	}
	
	protected class PostMessage implements Runnable {
		private final Label msgSender;
		private final Label msg;
		
		private boolean sayNothing = false;
		
		public PostMessage (String message, String sender, boolean local) {
			msgSender = new Label();
			msg = new Label();
			
			if (!ChatCommon.isSystemMessage(message)) {
				msgSender.setText((local) ? sender : sender);
				msgSender.setWrapText(true);
				msgSender.setStyle((local) ? ChatCommon.localHandleStyle : ChatCommon.senderHandleStyle);
				
				msg.setText(message + System.getProperty("line.separator") + System.getProperty("line.separator"));
				msg.setWrapText(true);
				msg.setStyle(ChatCommon.msgStyle);
				
				msg.setOnMouseClicked(new EventHandler<MouseEvent>() {
					@Override
					public void handle(MouseEvent event) {
						StringSelection str = new StringSelection((msg.getText().length() > 2) ? msg.getText().substring(0, msg.getText().length() - 2) : msg.getText());
						Toolkit.getDefaultToolkit().getSystemClipboard().setContents(str, null);
					}
				});
				
				msg.setOnMouseEntered(new EventHandler<MouseEvent>() {
					@Override
					public void handle(MouseEvent event) {
						msg.setStyle(ChatCommon.msgHoverStyle);
					}
				});
				
				msg.setOnMouseExited(new EventHandler<MouseEvent>() {
					@Override
					public void handle(MouseEvent event) {
						msg.setStyle(ChatCommon.msgStyle);
					}
				});
			} else {
				msgSender.setText("@System");
				msgSender.setWrapText(true);
				msgSender.setStyle(ChatCommon.systemSenderStyle);
				
				msg.setWrapText(true);
				handleSystemMessage(message, sender);
				
			}
		}
		
		@Override
		public void run() {
			if (!sayNothing && msg.getText() != null) {
				if (msg.getText() != "") { chatWindow.mChatText.getChildren().addAll(msgSender, msg); }
			}
		}
		
		private void handleSystemMessage(String message, String sender) {
			String[] sMsg = ChatCommon.unFormatSystemMessage(message).split(ChatCommon.SPLIT_SEPARATOR);
			switch (sMsg[0]) {
			case ChatCommon.DISCONNECT:
				if (sender == null) {
					msg.setText("DISCONNECTED" + System.getProperty("line.separator") + System.getProperty("line.separator"));
					msg.setStyle(ChatCommon.systemStyleSevere);
					startDisconnect();
				} else {
					msg.setText(sender + " has disconnected." + System.getProperty("line.separator") + System.getProperty("line.separator"));
					msg.setStyle(ChatCommon.systemStyleWarning);
					if (sMsg.length > 1) {
						Platform.runLater(new RemoveClient(sMsg[1]));
					}
				}
				break;
			case ChatCommon.NEWCLIENT:
				if (sMsg.length > 1) {
					String newClients = "";
					int numNew = 0;
					for (int newC = 1; newC < sMsg.length; newC ++) {
						newClients += sMsg[newC] + " ";
						Platform.runLater(new AddNewClient(sMsg[newC], sMsg[newC + 1]));
						newC++;
						numNew ++;
					}
					newClients += (numNew > 1) ? "have joined the conversation." : "has joined the conversation.";
					msg.setText(newClients + System.getProperty("line.separator") + System.getProperty("line.separator"));
					msg.setStyle(ChatCommon.systemStyleInfo);
				}
				break;
			case ChatCommon.TRANSFER_FILES:
				sayNothing = true;
				if (sMsg.length > 4) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							String clientKey = sMsg[2];
							int remotePort = -1;
							try {
								remotePort = Integer.parseInt(sMsg[3]);
							} catch (NumberFormatException portE) {
								
							} finally {
								if (remotePort > 0) {
									Thread thread = new Thread(new ChatCommon().new parseTransferRequest(sMsg, hostHandle, key, clientKey, remotePort, null, false));
									thread.start();
								} else {
									sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + key), true);
								}
							}
						}
					}
				}
				break;
			case ChatCommon.TRANSFER_DIRECTORY:
				sayNothing = true;
				if (sMsg.length > 4) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							String clientKey = sMsg[2];
							int remotePort = -1;
							try {
								remotePort = Integer.parseInt(sMsg[3]);
							} catch (NumberFormatException portE) {
								
							} finally {
								if (remotePort > 0) {
									Thread thread = new Thread(new ChatCommon().new parseTransferRequest(sMsg, hostHandle, key, clientKey, remotePort, null, true));
									thread.start();
								} else {
									sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + key), true);
								}
							}
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_R_ERROR:
				sayNothing = true;
				if (sMsg.length > 1) {
					int index = -1;
					try {
						index = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (index > -1) {
							fileReceiver.disconnectByKey(index, ChatCommon.DOWNLOAD_R_ERROR, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_R_REFUSED:
				sayNothing = true;
				if (sMsg.length > 1) {
					int index = -1;
					try {
						index = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (index > -1) {
							fileReceiver.disconnectByKey(index, ChatCommon.DOWNLOAD_R_REFUSED, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_R_TIMEDOUT:
				sayNothing = true;
				if (sMsg.length > 1) {
					int index = -1;
					try {
						index = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (index > -1) {
							fileReceiver.disconnectByKey(index, ChatCommon.DOWNLOAD_R_TIMEDOUT, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_R_CANCELLED:
				sayNothing = true;
				if (sMsg.length > 1) {
					int index = -1;
					try {
						index = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (index > -1) {
							fileReceiver.disconnectByKey(index, ChatCommon.DOWNLOAD_R_CANCELLED, true);
						}
					}
				}
				break;
			case ChatCommon.ACCEPT_TRANSFER:
				sayNothing = true;
				if (sMsg.length >= 5) {
					int sfKey = -1;
					int rfKey = -1;
					int remotePort = -1;
					try {
						sfKey = Integer.parseInt(sMsg[1]);
						rfKey = Integer.parseInt(sMsg[2]);
						remotePort = Integer.parseInt(sMsg[3]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (sMsg[4] != null) {
							if (sfKey > -1 && rfKey > -1 && remotePort > 0 && remotePort <= 65535 && sMsg[4].length() > 0) {
								fileSender.connectSender(sfKey, rfKey, remotePort, sMsg[4]);
							} else if (sfKey > -1) {
								fileSender.disconnectByKey(sfKey, ChatCommon.DOWNLOAD_S_ERROR, true);
							}
						} else if (sfKey > -1) {
							fileSender.disconnectByKey(sfKey, ChatCommon.DOWNLOAD_S_ERROR, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_S_ERROR:
				sayNothing = true;
				if (sMsg.length > 1) {
					int index = -1;
					try {
						index = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (index > -1) {
							fileSender.disconnectByKey(index, ChatCommon.DOWNLOAD_S_ERROR, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_S_REFUSED:
				sayNothing = true;
				if (sMsg.length > 1) {
					int index = -1;
					try {
						index = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (index > -1) {
							fileSender.disconnectByKey(index, ChatCommon.DOWNLOAD_S_REFUSED, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_S_TIMEDOUT:
				sayNothing = true;
				if (sMsg.length > 1) {
					int index = -1;
					try {
						index = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (index > -1) {
							fileSender.disconnectByKey(index, ChatCommon.DOWNLOAD_S_TIMEDOUT, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_S_CANCELLED:
				sayNothing = true;
				if (sMsg.length > 1) {
					int index = -1;
					try {
						index = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (index > -1) {
							fileSender.disconnectByKey(index, ChatCommon.DOWNLOAD_S_CANCELLED, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_COMPLETE:
				sayNothing = true;
				if (sMsg.length > 1) {
					int index = -1;
					try {
						index = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (index > -1) {
							fileSender.disconnectByKey(index, ChatCommon.DOWNLOAD_COMPLETE, true);
						}
					}
				}
				break;
			default:
				sayNothing = true;
				break;
			}
		}
	}
	
	private class RemoveClient implements Runnable {
		private final String key;
		
		public RemoveClient(String key) {
			this.key = key;
		}
		
		@Override
		public void run() {
			ArrayList<String> keys = new ArrayList<String>(clients.keySet());
			int index = keys.indexOf(key);
			if (index > -1) {
				clients.remove(key);
				chatWindow.removeParticipant(index + 1);
			}
		}
	}
	
	private class AddNewClient implements Runnable {
		private final String handle;
		private final String address;
		
		public AddNewClient(String handle, String address) {
			this.handle = handle;
			this.address = address;
		}
		
		@Override
		public void run() {
			new Client(handle, address);
		}
	}
	
	protected void sendMessage(String msg, boolean runLater) {
		messageQueue.add(msg);
		chatSender.setUpdate(runLater);
	}
	
	private class MessageSender implements Runnable {
		private String[] msgQueue = new String[0];
		private boolean updating = false;
		
		private ArrayList<String> tmpQueueList = new ArrayList<String>();
		
		public MessageSender(boolean updateLater) {
			setUpdate(updateLater);
		}
		
		@Override
		public void run() {
			while(isConnected()) {
				for (String msg : msgQueue) {
					//send message
					String error = "";
					String msgToSend = ChatCommon.formatMessage(msg);
					boolean notSent = true;
					int numErrors = 0;
					while (notSent) {
						try {
							sIO.writeBytes(chatSocket, msgToSend, aes);
						} catch (IOException e) {
							error = (e.getMessage() != null) ? e.getMessage() : "Unspecified";
						} finally {
							if (error.length() != 0) {
								if (numErrors >= 9) {
									System.out.println("Unable to send messages: " + System.getProperty("line.separator") + error);
									Platform.runLater(new PostMessage(ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT), null, true));
									startDisconnect();
									notSent = false;
								} else { numErrors ++; }
							} else {
								notSent = false;
								if (!ChatCommon.isSystemMessage(msg)) {
									Platform.runLater(new PostMessage(msg, ChatPreferences.handle, true));
								}
							}
						}
					}
				}
				msgQueue = new String[0];
				
				if (isConnected()) {
					if (updating) {
						updateMessages();
					}
					
					try {
						Thread.sleep((long) SLEEPTIME);
					} catch (InterruptedException intE) {
						
					}
				}
			}
		}
		
		private void updateMessages() {
			String tmpArray[] = new String[tmpQueueList.size()];
			tmpQueueList.toArray(tmpArray);
			msgQueue = tmpArray;
			tmpQueueList = new ArrayList<String>();
			updating = false;
		}
		
		protected void setUpdate(boolean runLater) {
			if (!runLater) {
				while (updating) {
					try {
						Thread.sleep((long) SLEEPTIME);
					} catch (InterruptedException intE) {
						
					}
				}
				tmpQueueList = copyArray(messageQueue, tmpQueueList);
				messageQueue = new ArrayList<String>();
				updating = true;
			} else {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						while (updating) {
							try {
								Thread.sleep((long) SLEEPTIME);
							} catch (InterruptedException intE) {
								
							}
						}
						tmpQueueList = copyArray(messageQueue, tmpQueueList);
						messageQueue = new ArrayList<String>();
						updating = true;
					}
				});
			}
		}
		
		private ArrayList<String> copyArray(ArrayList<String> from, ArrayList<String> to) {
			to = new ArrayList<String>();
			for (String item : from) {
				to.add(item);
			}
			
			return to;
		}
	}
	
	public void startDisconnect() {
		boolean wasConnected = isConnected();
		setConnected(false);
		Thread disc = new Thread(new SetDisconnect(wasConnected));
		disc.start();
	}
	
	private class SetDisconnect implements Runnable {
		private final boolean wasConnected;
		
		public SetDisconnect(boolean wasConnected) {
			this.wasConnected = wasConnected;
		}
		
		@Override
		public void run() {
			if (fileSender != null) { fileSender.setDisconnect(); }
			if (fileReceiver != null) { fileReceiver.setDisconnect(); }
			try {
				if (chatSendThread != null) {
					chatSendThread.join();
				}
				if (chatListenThread != null) {
					chatListenThread.join();
				}
				
				if (fileSenderThread != null) {
					fileSenderThread.join();
				}
				if (fileReceiverThread != null) {
					fileReceiverThread.join();
				}
			} catch (InterruptedException intE) {
				
			}
			
			if (chatSocket != null && wasConnected) {
				try {
					sIO.writeBytes(chatSocket, ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT), aes);
				} catch (IOException discE) {
					
				}
			}
			
			try {
				if (chatSocket != null) {
					chatSocket.close();
				}
			} catch (IOException ioE) {
				System.out.println("Could not close sockets on disconnect:" + System.getProperty("line.separator") + ((ioE.getMessage() != null) ? ioE.getMessage() : "Unspecified"));
				Logger.getLogger(ClientConnection.class.getName()).log(Level.WARNING, "Could not close sockets on disconnect:" + System.getProperty("line.separator") + ((ioE.getMessage() != null) ? ioE.getMessage() : "Unspecified"), ioE);
			}
			
			Platform.runLater( new Runnable() {
				@Override
				public void run() {
					if (connectionProgressWindow != null) {
						removeConnectionProgressWindow();
					}
					chatSender = null;
					chatListener = null;
					fileReceiver = null;
					fileSender = null;
					chatWindow.setDisconnected();
					chatWindow.clearParticipants();
				}
			});
		}
	}

}
