package atChat;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;

public class HostConnection {
	private static final long TIMEOUT = 60000 * 7;
	private static final long SLEEPTIME = (long) 55;
	
	private static final String CLIENT_DISCONNECT = "CLDISCONNECT";
	
	private boolean CONNECTED = false;
	private boolean DISCONNECTING = false;
	
	private final ChatMain chatWindow;
	private final String password;
	
	private boolean listening = false;
	
	private ServerSocketChannel chatSock;
	private ServerSocketChannel fileServer;
	
	private ConnectionListener cListener = null;
	private Thread connectionThread = null;
	
	private ChatListener clientChatListener = null;
	private Thread chatThread = null;
	
	private MessageSender Sender = null;
	private Thread senderThread = null;
	
	public FileServerListener fileServerListen = null;
	private Thread fileServerListenThread = null;
	
	private FileSender fileSender = null;
	private Thread fileSenderThread = null;
	
	private FileReceiver fileReceiver = null;
	private Thread fileReceiverThread = null;
	
	private ArrayList<Client> clients = new ArrayList<Client>();
	private ArrayList<TextMessage> messageQueue = new ArrayList<TextMessage>();
	
	protected CryptoAES aes;
	private CryptoRSA rsa;
	
	public HostConnection (ChatMain chatWindow, String pass) {
		this.chatWindow = chatWindow;
		password = pass;
		
		boolean wasError = false;
		try {
			aes = new CryptoAES();
			rsa = new CryptoRSA();
		} catch (Exception cE) {
			wasError = true;
			ChatCommon.popError("Unable to activate encryption:" + System.getProperty("line.separator") + cE.getMessage(), false);
			startDisconnect(false);
			System.out.println("Unable to activate encryption:" + System.getProperty("line.separator") + cE.getMessage());
		}
		
		if (!wasError) {
			ServerSocketChannel cSock = null;
			ServerSocketChannel fSock = null;
			String error = "";
			try {
				cSock = ServerSocketChannel.open();
				cSock.bind(new InetSocketAddress(ChatPreferences.lChatPort));
				cSock.configureBlocking(false);
				fSock = ServerSocketChannel.open();
				fSock.socket().bind(new InetSocketAddress(ChatPreferences.lFilePort));
				fSock.configureBlocking(false);
			} catch (IOException e) {
				error = (e.getMessage() != null) ? e.getMessage() : "Unspecified";
			} finally {
				if ((cSock == null && fSock == null) || error.length() != 0) {
					//pop error unable to create server sockets
					ChatCommon.popError("Unable to open server sockets:" + System.getProperty("line.separator") + error, false);
					startDisconnect(false);
					System.out.println("Unable to open server sockets:" + System.getProperty("line.separator") + error);
				} else {
					chatSock = cSock;
					fileServer = fSock;
					
					try {
						startAccepting();
					} catch (Exception lE) {
						wasError = true;
						ChatCommon.popError("Unable to activate RSA encryption:" + System.getProperty("line.separator") + lE.getMessage(), false);
						startDisconnect(false);
						System.out.println("Unable to activate RSA encryption:" + System.getProperty("line.separator") + lE.getMessage());
					}
					
					if (!wasError) {
						setConnected(true);
						chatWindow.setConnectButtons(true, true);
						Sender = new MessageSender(false);
						senderThread = new Thread(Sender);
						senderThread.start();
						clientChatListener = new ChatListener();
						chatThread = new Thread(clientChatListener);
						chatThread.start();
						fileServerListen = new FileServerListener(chatWindow);
						fileServerListenThread = new Thread(fileServerListen);
						fileServerListenThread.start();
						fileSender = new FileSender(chatWindow);
						fileSenderThread = new Thread(fileSender);
						fileSenderThread.start();
						fileReceiver = new FileReceiver(chatWindow);
						fileReceiverThread = new Thread(fileReceiver);
						fileReceiverThread.start();
					}
				}
			}
		}
	}
	
	protected ArrayList<Client> getClients() {
		return clients;
	}
	
	protected Client getSelectedClient(int index) {
		int ind = -1;
		for (Client client : clients) {
			if (client.accepted && !client.awaitingConfirmation) {
				ind ++;
				if (ind == index) { return client; }
			}
		}
		
		return null;
	}
	
	private int getActiveClientIndex(Client client) {
		int index = -1;
		for (int ind = 0; ind < clients.size(); ind ++) {
			Client c = clients.get(ind);
			if (c.accepted && !c.awaitingConfirmation) {
				index ++;
			}
			if (c.equals(client)) {
				break;
			}
		}
		
		return index;
	}
	
	private int getInactiveClientIndex(Client client) {
		int index = -1;
		for (int ind = 0; ind < clients.size(); ind ++) {
			Client c = clients.get(ind);
			if (c.accepted && !c.awaitingConfirmation) {
				index ++;
			}
			if (c.equals(client)) {
				index ++;
				break;
			}
		}
		
		return index;
	}
	
	protected ServerSocketChannel getFileServer() {
		return fileServer;
	}
	
	protected FileSender getFileSender() {
		return fileSender;
	}
	
	protected FileReceiver getFileReceiver() {
		return fileReceiver;
	}
	
	public void setConnected(boolean connected) {
		CONNECTED = connected;
	}
	
	public void setDisconnecting(boolean disconnecting) {
		DISCONNECTING = disconnecting;
	}
	
	public boolean isConnected() {
		return CONNECTED;
	}
	
	public boolean isDisconnecting() {
		return DISCONNECTING;
	}
	
	public boolean isListening() {
		return listening;
	}
	
	public void startAccepting() throws Exception {
		listening = true;
		cListener = new ConnectionListener();
		connectionThread = new Thread(cListener);
		connectionThread.start();
	}
	
	private class ConnectionListener implements Runnable {
		private final CryptoRSA rsa;
		
		private ConnectionListener() throws Exception {
			rsa = new CryptoRSA();
		}
		
		@Override
		public void run() {
			do {
				String error = "";
				SocketChannel newConnection = null;
				try {
					newConnection = chatSock.accept();
					if (newConnection != null) {
						newConnection.configureBlocking(false);
					}
				} catch (IOException e) {
					error = (e.getMessage() != null) ? e.getMessage() : "Unspecified";
				} finally {
					if (newConnection != null && isConnected() && !isDisconnecting()) {
						if (ChatPreferences.continueListening) {
							//begin accepting connection
							Platform.runLater(new AddClient(newConnection));
						} else {
							try {
								SocketIO tmpIO = new SocketIO();
								//tmpIO.writeLine(newConnection, ChatCommon.REFUSED);
								tmpIO.writeBytes(newConnection, new byte[1]);
							} catch (IOException newConnE) {
								
							} finally {
								try {
									newConnection.close();
								} catch (IOException closeE) {
									
								}
							}
						}
					} else if (newConnection != null) {
						try {
							newConnection.close();
						} catch (IOException closeE) {
							
						}
					}
				}
				
				try {
					Thread.sleep((long) 100);
				} catch (InterruptedException intE) {
					
				}
				
				while (!ChatPreferences.continueListening && isConnected() && !isDisconnecting()) {
					try {
						Thread.sleep((long) 250);
					} catch (InterruptedException intE) {
						
					}
				}
			} while (isConnected() && !isDisconnecting());
			
			listening = false;
		}
		
		private class AddClient implements Runnable {
			private final SocketChannel sock;
			
			public AddClient(SocketChannel sock) {
				this.sock = sock;
			}
			
			@Override
			public void run() {
				Client cl = new Client(sock);
					
				if (isConnected() && !isDisconnecting()) {
					int index = -1;
					for (int ind = 0; ind < clients.size(); ind ++) {
						Client c = clients.get(ind);
						if (c.cChatSock.socket().getPort() == sock.socket().getPort() && c.cChatSock.socket().getInetAddress().getHostAddress().equals(sock.socket().getInetAddress().getHostAddress())) {
							index = ind;
						}
					}
					if (index == -1) {
						clients.add(cl);
					} else {
						clients.remove(index);
						clients.add(index, cl);
						clientChatListener.setUpdate(false);
					}
					cl.startHandShake();
				}
			}
		}
	}
	
	private class ChatListener implements Runnable {
		private Client clientArray[] = new Client[0];
		private ArrayList<Client> tmpClientList = new ArrayList<Client>();
		
		private boolean setClientUpdate = false;
		
		public ChatListener() {
			setUpdate(false);
		}
		
		@Override
		public void run() {
			while (isConnected() && !isDisconnecting()) {
				for (Client c : clientArray) {
					if (c.isAccepted()) {
						c.receiveMessage();
					}
					if (isDisconnecting() || !isConnected()) {
						break;
					}
				}
				
				if (setClientUpdate && isConnected() && !isDisconnecting()) {
					updateClients();
				}
				if (!isDisconnecting() || !isConnected()) {
					try {
						Thread.sleep((long) SLEEPTIME);
					} catch (InterruptedException e) {
						
					}
				}
			}
		}
		
		private void updateClients() {
			Client tmpArray[] = new Client[tmpClientList.size()];
			tmpClientList.toArray(tmpArray);
			clientArray = tmpArray;
			tmpClientList = new ArrayList<Client>();
			setClientUpdate = false;
		}
		
		protected void setUpdate(boolean runLater) {
			if (!runLater) {
				while (setClientUpdate) {
					try {
						Thread.sleep((long) SLEEPTIME);
					} catch (InterruptedException intE) {
						
					}
				}
				tmpClientList = copyArray(clients, tmpClientList);
				setClientUpdate = true;
			} else {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						while (setClientUpdate) {
							try {
								Thread.sleep((long) SLEEPTIME);
							} catch (InterruptedException intE) {
								
							}
						}
						tmpClientList = copyArray(clients, tmpClientList);
						setClientUpdate = true;
					}
				});
			}
		}
		
		private ArrayList<Client> copyArray(ArrayList<Client> from, ArrayList<Client> to) {
			to = new ArrayList<Client>();
			for (Client item : from) {
				to.add(item);
			}
			
			return to;
		}
	}
	
	protected class Client {
		protected String cHandle = "No handle";
		
		private final SocketChannel cChatSock;
		private final SocketIO sIO;
		private boolean accepted = false;
		private boolean disconnecting = false;
		
		private HashMap<Integer, SendFile> sendFileHash = new HashMap<Integer, SendFile>();
		private int sendFileKey = 0;
		
		private HashMap<Integer, ReceiveFile> receiveFileHash = new HashMap<Integer, ReceiveFile>();
		private int receiveFileKey = 0;
		
		private final Client thisClient;
		
		private boolean awaitingConfirmation = true;
		private long awaitingConfTime;
		
		public Client(SocketChannel clientChatSocket) {
			thisClient = this;
			
			cChatSock = clientChatSocket;
			sIO = new SocketIO();
		}
		
		public boolean atMaxReceive() {
			return receiveFileKey == Integer.MAX_VALUE;
		}
		
		protected int setReceiveFile(ReceiveFile rFile) {
			receiveFileKey ++;
			rFile.setReceiveKey(receiveFileKey);
			receiveFileHash.put(new Integer(receiveFileKey), rFile);
			
			return receiveFileKey;
		}
		
		protected ReceiveFile getReceiveFile(int key) {
			return receiveFileHash.get(new Integer(key));
		}
		
		protected void nullReceiveFile(final int key, boolean runLater) {
			if (!runLater) {
				if (key >= 0) {
					receiveFileHash.remove(new Integer(key));
				} else { receiveFileHash.clear(); }
				if (receiveFileHash.isEmpty() && receiveFileKey >= 1337) {
					receiveFileKey = 0;
				}
			} else {
				Platform.runLater(new Runnable(){
					@Override
					public void run() {
						if (key >= 0) {
							receiveFileHash.remove(new Integer(key));
						} else { receiveFileHash.clear(); }
						if (receiveFileHash.isEmpty() && receiveFileKey >= 1337) {
							receiveFileKey = 0;
						}
					}
				});
			}
		}
		
		protected void nullReceiveFile(final ReceiveFile rFile, boolean runLater) {
			if (!runLater) {
				int rfKey = rFile.getRFKey();
				receiveFileHash.remove(rfKey);
				if (receiveFileHash.isEmpty() && receiveFileKey >= 1337) {
					receiveFileKey = 0;
				}
			} else {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						int rfKey = rFile.getRFKey();
						receiveFileHash.remove(rfKey);
						if (receiveFileHash.isEmpty() && receiveFileKey >= 1337) {
							receiveFileKey = 0;
						}
					}
				});
			}
		}
		
		protected void disconnectReceiveFile(final String reason, final int key, final boolean runLater) {
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					if (key >= 0) {
						if (receiveFileHash.containsKey(new Integer(key))) {
							receiveFileHash.get(new Integer(key)).disconnect(reason, runLater);
						}
					} else {
						for (ReceiveFile rf : receiveFileHash.values()) {
							rf.disconnect(reason, runLater);
						}
					}
				}
			});
		}
		
		public boolean atMaxSend() {
			return sendFileKey == Integer.MAX_VALUE;
		}
		
		protected int setSendFile(SendFile sendFile) {
			sendFileKey ++;
			sendFileHash.put(new Integer(sendFileKey), sendFile);
			
			return sendFileKey;
		}
		
		protected SendFile getSendFile(int key) {
			return sendFileHash.get(new Integer(key));
		}
		
		protected void nullSendFile(final int key, boolean runLater) {
			if (!runLater) {
				if (key >= 0) {
					sendFileHash.remove(new Integer(key));
				} else { sendFileHash.clear(); }
				if (sendFileHash.isEmpty() && sendFileKey >= 1337) {
					sendFileKey = 0;
				}
			} else {
				Platform.runLater(new Runnable(){
					@Override
					public void run() {
						if (key >= 0) {
							sendFileHash.remove(new Integer(key));
						} else { sendFileHash.clear(); }
						if (sendFileHash.isEmpty() && sendFileKey >= 1337) {
							sendFileKey = 0;
						}
					}
				});
			}
		}
		
		protected void nullSendFile(final SendFile sf, boolean runLater) {
			if (!runLater) {
				int sfKey = sf.getKey();
				sendFileHash.remove(sfKey);
				if (sendFileHash.isEmpty() && sendFileKey >= 1337) {
					sendFileKey = 0;
				}
			} else {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						int sfKey = sf.getKey();
						sendFileHash.remove(sfKey);
						if (sendFileHash.isEmpty() && sendFileKey >= 1337) {
							sendFileKey = 0;
						}
					}
				});
			}
		}
		
		protected void disconnectSendFile(final String reason, final int key, final boolean runLater) {
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					if (key >= 0) {
						if (sendFileHash.containsKey(new Integer(key))) {
							sendFileHash.get(new Integer(key)).disconnect(reason, runLater);
						}
					} else {
						for (SendFile sf : sendFileHash.values()) {
							sf.disconnect(reason, runLater);
						}
					}
				}
			});
		}
		
		protected String getKey() {
			return cChatSock.socket().getPort() + ":" + cChatSock.socket().getInetAddress().getHostAddress();
		}
		
		protected void disconnectSockets() throws IOException {
			if (cChatSock != null) {
				cChatSock.close();
			}
		}
		
		protected void sendMessage(String msg) {
			if (isAccepted()) {
				String error = "";
				int numErrors = 0;
				boolean notSent = true;
				while(notSent) {
					try {
						sIO.writeBytes(cChatSock, msg, aes);
					} catch (IOException sendE) {
						error = sendE.toString();
					} finally {
						if (error.length() != 0) {
							if (numErrors >= 9) {
								System.out.println("Unable to send message to " + cHandle + ": " + cChatSock.socket().getInetAddress().getHostAddress() + System.getProperty("line.separator") + error);
								Platform.runLater(new PostMessage(ChatCommon.formatSystemMessage(CLIENT_DISCONNECT), thisClient, true));
								removeClient(true, true);
								notSent = false;
							} else { numErrors ++; }
							
						} else {
							notSent = false;
						}
					}
				}
			}
		}
		
		protected void receiveMessage() {
			String error = "";
			String message = null;
			boolean timedOut = false;
			int numErrors = 0;
			if (awaitingConfirmation) {
				if (System.currentTimeMillis() - awaitingConfTime >= TIMEOUT) {
					timedOut = true;
					accepted = false;
					removeClient(true, false);
				}
			}
			if (!timedOut) {
				do {
					try {
						message = sIO.readBytes(cChatSock, aes);
					} catch (IOException chatE) {
						error = (chatE.getMessage() != null) ? chatE.getMessage() : "Unspecified";
					} finally {
						if (error.length() != 0) {
							numErrors ++;
							if (numErrors >= 9) {
								if (!awaitingConfirmation) {
									Platform.runLater(new PostMessage(ChatCommon.formatSystemMessage(CLIENT_DISCONNECT), thisClient, false));
								}
								removeClient(true, true);
								System.out.println("Unable to receive message from " + cHandle + ": " + cChatSock.socket().getInetAddress().getHostAddress() + System.getProperty("line.separator") + error);
							}
						} else if (message != null) {
							if (!message.equals("\n")) {
								numErrors = 0;
								if (awaitingConfirmation) {
									awaitingConfirmation = false;
									Platform.runLater(new Runnable() {
										@Override
										public void run() {
											int index = getActiveClientIndex(thisClient);
											if (index > -1) {
												chatWindow.addParticipant(index, cHandle);
											}
										}
									});
									Platform.runLater(new PostMessage(ChatCommon.formatSystemMessage(ChatCommon.NEWCLIENT), thisClient, false));
									if (!ChatCommon.isSystemMessage(message)) {
										Platform.runLater(new PostMessage(message, thisClient, false));
									}
								} else {
									Platform.runLater(new PostMessage(message, thisClient, false));
								}
							} else {
								Platform.runLater(new PostMessage(ChatCommon.formatSystemMessage(CLIENT_DISCONNECT), thisClient, true));
								removeClient(true, !awaitingConfirmation);
							}
						}
					}
				} while (numErrors > 0 && numErrors < 9);
			}
		}
		
		protected void setDisconnecting(boolean a) {
			disconnecting = a;
		}
		
		public boolean isDisconnecting() {
			return disconnecting;
		}
		
		protected boolean isAccepted() {
			return accepted;
		}
		
		protected void startHandShake() {
			if (!accepted) {
				Thread handShake = new Thread(new ClientHandShake());
				handShake.start();
			}
		}
		
		protected void removeClient(boolean runLater, boolean onError) {
			boolean wasAccepted = accepted && !awaitingConfirmation;
			accepted = false;
			disconnectSendFile(ChatCommon.DOWNLOAD_S_CANCELLED, -1, true);
			disconnectReceiveFile(ChatCommon.DOWNLOAD_R_CANCELLED, -1, true);
			
			try {
				if (cChatSock != null) {
					cChatSock.close();
				}
			} catch (IOException ioE) {
				System.out.println("Unable to close client chat socket " + cHandle + ": " + cChatSock.socket().getInetAddress().getHostAddress() + System.getProperty("line.separator") + ((ioE.getMessage() != null) ? ioE.getMessage() : "Unspecified"));
				Logger.getLogger(HostConnection.class.getName()).log(Level.WARNING, "Unable to close client chat socket upon connection refusal:" + System.getProperty("line.separator") + ((ioE.getMessage() != null) ? ioE.getMessage() : "Unspecified"), ioE);
			}
			
			if (runLater) {
				Platform.runLater(new DeleteClient(thisClient, wasAccepted, onError));
			} else {
				if (wasAccepted) {
					int index = getInactiveClientIndex(thisClient);
					if (index > -1) {
						chatWindow.removeParticipant(index);
					}
					
					if (onError) {
						ArrayList<HostConnection.Client> sendTo = new ArrayList<HostConnection.Client>();
						for (HostConnection.Client cl : clients) {
							if (!cl.equals(thisClient)) {
								sendTo.add(cl);
							}
						}
						addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT + ChatCommon.SEPARATOR + thisClient.getKey()), sendTo, false, false, thisClient);
					}
				}
				
				if (clients.remove(thisClient) && clientChatListener != null) {
					clientChatListener.setUpdate(true);
				}
			}
		}
		
		private class DeleteClient implements Runnable {
			private final Client client;
			private final boolean wasAccepted;
			
			private final boolean onError;
			
			public DeleteClient(Client client, boolean wasAccepted, boolean onError) {
				this.client = client;
				this.wasAccepted = wasAccepted;
				
				this.onError = onError;
			}
			
			@Override
			public void run() {
				if (wasAccepted) {
					int index = getInactiveClientIndex(client);
					if (index > -1) {
						chatWindow.removeParticipant(index);
					}
					
					if (onError) {
						ArrayList<HostConnection.Client> sendTo = new ArrayList<HostConnection.Client>();
						for (HostConnection.Client cl : clients) {
							if (!cl.equals(thisClient)) {
								sendTo.add(cl);
							}
						}
						addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT + ChatCommon.SEPARATOR + thisClient.getKey()), sendTo, false, false, thisClient);
					}
				}
				
				if (clients.remove(client) && clientChatListener != null) {
					clientChatListener.setUpdate(false);
				}
			}
		}
		
		private class ClientHandShake implements Runnable {
			
			@Override
			public void run() {
				String error = "";
				boolean passMatch = true;
				boolean timedOut = false;
				long time = System.currentTimeMillis();
				boolean refused = false;
				
				CryptoRSA clientRSA = null;
				boolean badKey = false;
				boolean disc = false;
				try {
					sIO.writeBytes(cChatSock, rsa.pubKey);
					byte[] clientKey = null;
					do {
						clientKey = sIO.readBytes(cChatSock);
						if (clientKey != null) {
							if (clientKey.length > 0) {
								try {
									clientRSA = new CryptoRSA(clientKey);
								} catch (Exception cryptE) {
									badKey = true;
									break;
								}
								time = System.currentTimeMillis();
							} else {
								removeClient(true, false);
								disc = true;
							}
							break;
						} else if (System.currentTimeMillis() - time >= TIMEOUT) {
							timedOut = true;
						}
					} while (!timedOut && isConnected() && !isDisconnecting() && !disc);
					
					if (!timedOut && isConnected() && !isDisconnecting() && !badKey && !disc) {
						String infoSend = (password.length() > 0) ? ChatCommon.REQ_PASSWORD + ChatCommon.SEPARATOR + ChatPreferences.handle + ChatCommon.SEPARATOR + Float.toString(ChatPreferences.VERSION) : ChatCommon.NO_PASSWORD + ChatCommon.SEPARATOR + ChatPreferences.handle + ChatCommon.SEPARATOR + Float.toString(ChatPreferences.VERSION);
						sIO.writeBytes(cChatSock, infoSend, clientRSA);
						String input = null;
						boolean quitListen = false;
						do {
							input = sIO.readBytes(cChatSock, rsa);
							if (input != null) {
								if (!input.equals("\n")) {
									if (password.length() != 0) {
										String[] split = input.split(ChatCommon.SPLIT_SEPARATOR);
										if (split.length >= 2) {
											quitListen = true;
											if (split[0].equals(password)) {
												cHandle = (split[1].length() > 0) ? split[1] : "No handle";
											} else {
												passMatch = false;
											}
										} else if (split.length > 0) {
											if (split[0].equals(password)) {
												quitListen = true;
												cHandle = "No handle";
											} else {
												passMatch = false;
												quitListen = true;
											}
										} else {
											passMatch = false;
											quitListen = true;
										}
									} else {
										cHandle = (input.length() > 0) ? input : "No Handle";
										quitListen = true;
									}
								} else {
									removeClient(true, false);
									quitListen = true;
								}
							} else if (System.currentTimeMillis() - time >= TIMEOUT) {
								timedOut = true;
							}
						} while (!quitListen && !timedOut && isConnected() && !isDisconnecting());
					}
					if (isConnected() && !isDisconnecting() && !disc) {
						if (passMatch && !timedOut) {
							final CryptoRSA cRSA = clientRSA;
							if (ChatPreferences.autoAccept) {
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										acceptClient(cRSA);
									}
								});
							}
						} else if (!passMatch && !timedOut) {
							refuseClient("Incorrect password.", clientRSA);
							refused = true;
						} else if (timedOut) {
							refuseClient("Operation timed out.", clientRSA);
							refused = true;
						} else {
							refuseClient("Invalid RSA public key.", clientRSA);
							refused = true;
						}
					}
				} catch (IOException e) {
					if (e.getMessage() != null) {
						error = e.getMessage();
					} else {
						error = "Unspecified";
					}
				} finally {
					if (isConnected() && !isDisconnecting()) {
						if (error.length() == 0) {
							if (refused || disc) {
								removeClient(true, false);
							} else if (!ChatPreferences.autoAccept) {
								//pop window asking to accept connection
								ChatCommon.popAcceptWindow(thisClient, clientRSA, cHandle, cChatSock.socket().getPort(), cChatSock.socket().getInetAddress().getHostAddress());
							}
						} else {
							removeClient(true, false);
							System.out.println("Unable to send handshake request to client " + cHandle + ": " + cChatSock.socket().getInetAddress().getHostAddress() + System.getProperty("line.separator") + error);
						}
					}
				}
			}
		}
		
		/*private class ClientHandShake implements Runnable {
			
			@Override
			public void run() {
				String error = "";
				boolean passMatch = true;
				boolean timedOut = false;
				long time = System.currentTimeMillis();
				boolean refused = false;
				try {
					
					String infoSend = (password.length() > 0) ? ChatCommon.REQ_PASSWORD + ChatCommon.SEPARATOR + ChatPreferences.handle : ChatCommon.NO_PASSWORD + ChatCommon.SEPARATOR + ChatPreferences.handle;
					sIO.writeLine(cChatSock, infoSend);
					String input = null;
					boolean quitListen = false;
					do {
						input = sIO.readLine(cChatSock);
						if (input != null) {
							if (!input.equals("\n")) {
								if (password.length() != 0) {
									String[] split = input.split(ChatCommon.SPLIT_SEPARATOR);
									if (split.length >= 2) {
										quitListen = true;
										if (split[0].equals(password)) {
											cHandle = (split[1].length() > 0) ? split[1] : "No handle";
										} else {
											passMatch = false;
										}
									} else if (split.length > 0) {
										if (split[0].equals(password)) {
											quitListen = true;
											cHandle = "No handle";
										} else {
											passMatch = false;
											quitListen = true;
										}
									} else {
										passMatch = false;
										quitListen = true;
									}
								} else {
									cHandle = (input.length() > 0) ? input : "No Handle";
									quitListen = true;
								}
							} else {
								removeClient(true, false);
							}
						} else if (System.currentTimeMillis() - time >= TIMEOUT) {
							timedOut = true;
						}
					} while (!quitListen && !timedOut && isConnected() && !isDisconnecting());
					if (isConnected() && !isDisconnecting()) {
						if (passMatch && !timedOut) {
							if (ChatPreferences.autoAccept) {
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										acceptClient();
									}
								});
							}
						} else if (!passMatch && !timedOut) {
							refuseClient("Incorrect password.");
							refused = true;
						} else {
							refuseClient("Operation timed out.");
							refused = true;
						}
					}
				} catch (IOException e) {
					if (e.getMessage() != null) {
						error = e.getMessage();
					} else {
						error = "Unspecified";
					}
				} finally {
					if (isConnected() && !isDisconnecting()) {
						if (error.length() == 0) {
							if (refused) {
								removeClient(true, false);
							} else if (!ChatPreferences.autoAccept) {
								//pop window asking to accept connection
								ChatCommon.popAcceptWindow(thisClient, cHandle, cChatSock.socket().getPort(), cChatSock.socket().getInetAddress().getHostAddress());
							}
						} else {
							removeClient(true, false);
							System.out.println("Unable to send handshake request to client " + cHandle + ": " + cChatSock.socket().getInetAddress().getHostAddress() + System.getProperty("line.separator") + error);
						}
					}
				}
			}
		}*/
		
		protected void refuseClient(String refuseMsg, final CryptoRSA clientRSA) throws IOException {
			sIO.writeBytes(cChatSock, ChatCommon.REFUSED + ChatCommon.SEPARATOR + refuseMsg, clientRSA);
		}
		
		protected void acceptClient(final CryptoRSA clientRSA) {
			awaitingConfTime = System.currentTimeMillis();
			String error = "";
			String clientList = "";
			int numClients = 0;
			for (int c = 0; c < clients.size(); c++) {
				if (clients.get(c).isAccepted() && !clients.get(c).awaitingConfirmation) {
					numClients ++;
					clientList += clients.get(c).cHandle + ChatCommon.SEPARATOR + Integer.toString(clients.get(c).cChatSock.socket().getPort()) + ":" + clients.get(c).cChatSock.socket().getInetAddress().getHostAddress() + ChatCommon.SEPARATOR;
				}
			}
			
			int numErrors = 0;
			do {
				try {
					accepted = true;
					if (numClients > 0) {
						clientList = clientList.substring(0, clientList.length() - 1);
						sIO.writeBytes(cChatSock, ChatCommon.ACCEPTED + ChatCommon.SEPARATOR + ChatPreferences.handle + ChatCommon.SEPARATOR + Integer.toString(numClients) + ChatCommon.SEPARATOR + clientList, clientRSA);
					} else {
						sIO.writeBytes(cChatSock, ChatCommon.ACCEPTED + ChatCommon.SEPARATOR + ChatPreferences.handle, clientRSA);
					}
					sIO.writeBytes(cChatSock, aes.key, clientRSA);
				} catch (IOException acceptE) {
					if (acceptE.getMessage() != null) {
						error = acceptE.getMessage();
					} else { error = "Unspecified"; }
				} finally {
					if (accepted) {
						//connect file socket and listen for chats
						numErrors = 0;
						if (clientChatListener != null) {
							clientChatListener.setUpdate(false);
						}
						
					} else {
						numErrors ++;
						if (numErrors >= 9) {
							if (!ChatPreferences.autoAccept) {
								Platform.runLater(new PostMessage(ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT), thisClient, true));
							}
							System.out.println("Unable to send accept signal to client " + cHandle + ": " + cChatSock.socket().getInetAddress().getHostAddress() + System.getProperty("line.separator") + error);
							removeClient(false, false);
						}
					}
				}
			} while (numErrors > 0 && numErrors < 9);
		}
		
		/*protected void acceptClient() {
			awaitingConfTime = System.currentTimeMillis();
			String error = "";
			String clientList = "";
			int numClients = 0;
			for (int c = 0; c < clients.size(); c++) {
				if (clients.get(c).isAccepted() && !clients.get(c).awaitingConfirmation) {
					numClients ++;
					clientList += clients.get(c).cHandle + ChatCommon.SEPARATOR + Integer.toString(clients.get(c).cChatSock.socket().getPort()) + ":" + clients.get(c).cChatSock.socket().getInetAddress().getHostAddress() + ChatCommon.SEPARATOR;
				}
			}
			
			int numErrors = 0;
			do {
				try {
					accepted = true;
					if (numClients > 0) {
						clientList = clientList.substring(0, clientList.length() - 1);
						sIO.writeLine(cChatSock, ChatCommon.ACCEPTED + ChatCommon.SEPARATOR + ChatPreferences.handle + ChatCommon.SEPARATOR + Integer.toString(numClients) + ChatCommon.SEPARATOR + clientList);
					} else {
						sIO.writeLine(cChatSock, ChatCommon.ACCEPTED + ChatCommon.SEPARATOR + ChatPreferences.handle);
					}
				} catch (IOException acceptE) {
					if (acceptE.getMessage() != null) {
						error = acceptE.getMessage();
					} else { error = "Unspecified"; }
				} finally {
					if (accepted) {
						//connect file socket and listen for chats
						numErrors = 0;
						if (clientChatListener != null) {
							clientChatListener.setUpdate(false);
						}
						
					} else {
						numErrors ++;
						if (numErrors >= 9) {
							if (!ChatPreferences.autoAccept) {
								Platform.runLater(new PostMessage(ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT), thisClient, true));
							}
							System.out.println("Unable to send accept signal to client " + cHandle + ": " + cChatSock.socket().getInetAddress().getHostAddress() + System.getProperty("line.separator") + error);
							removeClient(false, false);
						}
					}
				}
			} while (numErrors > 0 && numErrors < 9);
		}*/
	}
	
	protected class PostMessage implements Runnable {
		private final Label msgSender;
		private final Label msg;
		
		private String strMessage;
		private final Client caller;
		
		private boolean isLocal;
		
		boolean sayNothing = false;
		
		boolean system = false;
		
		public PostMessage(String message, Client caller, boolean local) {
			msgSender = new Label();
			msg = new Label();
			msg.setEllipsisString(" ");
			
			this.caller = caller;
			
			isLocal = local;
			
			if (!ChatCommon.isSystemMessage(message)) {
				strMessage = message;
				
				msgSender.setText((local) ? ChatPreferences.handle : caller.cHandle);
				msgSender.setWrapText(true);
				msgSender.setStyle((local) ? ChatCommon.localHandleStyle : ChatCommon.senderHandleStyle);
				
				msg.setText(ChatCommon.unFormatMessage(message) + "\n" + "\n");
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
				system = true;
				msgSender.setText("@System");
				msgSender.setWrapText(true);
				msgSender.setStyle(ChatCommon.systemSenderStyle);
				
				msg.setWrapText(true);
				
				handleSystemMessage(message);
			}
		}
		
		public void run() {
			//post message to screen
			if (!sayNothing) {
				chatWindow.mChatText.getChildren().addAll(msgSender, msg);
				
				if (system) {
					ChatCommon.playSystem();
				} else if (!isLocal) {
					ChatCommon.playIncomming();
				} else {
					ChatCommon.playOutgoing();
				}
			}
			
			//forward incomming messages to other clients
			if (!isLocal && strMessage != null) {
				if (strMessage.length() > 0) {
					ArrayList<Client> forwardTo = new ArrayList<Client>();
					if (caller != null) {
						for (Client f : clients) {
							if (!f.equals(caller) && f.isAccepted() && !f.awaitingConfirmation) {
								forwardTo.add(f);
							}
						}
					} else {
						for (Client f : clients) {
							forwardTo.add(f);
						}
					}
					
					addMessageToQueue(strMessage, forwardTo, false, false, caller);
				}
			}
		}
		
		private void handleSystemMessage(String message) {
			String sMsg[] = ChatCommon.unFormatSystemMessage(message).split(ChatCommon.SPLIT_SEPARATOR);
			switch (sMsg[0]) {
			case ChatCommon.DISCONNECT:
				msg.setText(caller.cHandle + " has disconnected." + "\n" + "\n");
				msg.setStyle(ChatCommon.systemStyleWarning);
				caller.removeClient(true, false);
				strMessage = ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT + ChatCommon.SEPARATOR + caller.getKey());
				break;
			case ChatCommon.NEWCLIENT:
				msg.setText(caller.cHandle + " has joined the conversation." + "\n" + "\n");
				msg.setStyle(ChatCommon.systemStyleInfo);
				strMessage = ChatCommon.formatSystemMessage(ChatCommon.NEWCLIENT + ChatCommon.SEPARATOR + caller.cHandle + ChatCommon.SEPARATOR + caller.getKey());
				break;
			case ChatCommon.TRANSFER_FILES:
				sayNothing = true;
				isLocal = true;
				if (sMsg.length > 2) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							Thread thread = new Thread(new ChatCommon().new parseTransferRequest(sMsg, caller.cHandle, key, "", -1, caller, false));
							thread.start();
						}
					}
				}
				break;
			case ChatCommon.TRANSFER_DIRECTORY:
				sayNothing = true;
				isLocal = true;
				if (sMsg.length > 2) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							Thread thread = new Thread(new ChatCommon().new parseTransferRequest(sMsg, caller.cHandle, key, "", -1, caller, true));
							thread.start();
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_S_CANCELLED:
				sayNothing = true;
				isLocal = true;
				if (sMsg.length > 1) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							caller.disconnectSendFile(ChatCommon.DOWNLOAD_S_CANCELLED, key, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_S_REFUSED:
				sayNothing = true;
				isLocal = true;
				if (sMsg.length > 1) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							caller.disconnectSendFile(ChatCommon.DOWNLOAD_S_REFUSED, key, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_S_TIMEDOUT:
				sayNothing = true;
				isLocal = true;
				if (sMsg.length > 1) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							caller.disconnectSendFile(ChatCommon.DOWNLOAD_S_TIMEDOUT, key, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_S_ERROR:
				sayNothing = true;
				isLocal = true;
				if (sMsg.length > 1) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							caller.disconnectSendFile(ChatCommon.DOWNLOAD_S_ERROR, key, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_R_CANCELLED:
				sayNothing = true;
				isLocal = true;
				if (sMsg.length > 1) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							caller.disconnectReceiveFile(ChatCommon.DOWNLOAD_R_CANCELLED, key, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_R_REFUSED:
				sayNothing = true;
				isLocal = true;
				if (sMsg.length > 1) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							caller.disconnectReceiveFile(ChatCommon.DOWNLOAD_R_REFUSED, key, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_R_TIMEDOUT:
				sayNothing = true;
				isLocal = true;
				if (sMsg.length > 1) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							caller.disconnectReceiveFile(ChatCommon.DOWNLOAD_R_TIMEDOUT, key, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_R_ERROR:
				sayNothing = true;
				isLocal = true;
				if (sMsg.length > 1) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							caller.disconnectReceiveFile(ChatCommon.DOWNLOAD_R_ERROR, key, true);
						}
					}
				}
				break;
			case ChatCommon.DOWNLOAD_COMPLETE:
				sayNothing = true;
				isLocal = true;
				if (sMsg.length > 1) {
					int key = -1;
					try {
						key = Integer.parseInt(sMsg[1]);
					} catch (NumberFormatException numE) {
						
					} finally {
						if (key >= 0) {
							caller.disconnectSendFile(ChatCommon.DOWNLOAD_COMPLETE, key, true);
						}
					}
				}
				break;
			case CLIENT_DISCONNECT:
				isLocal = true;
				msg.setText(caller.cHandle + " has disconnected." + "\n" + "\n");
				msg.setStyle(ChatCommon.systemStyleWarning);
				break;
			default:
				sayNothing = true;
				isLocal = true;
				break;
			}
		}
	}
	
	public void addMessageToQueue(String msg, ArrayList<Client> sendTo, boolean runLater, boolean isLocal, Client from) {
		
		if (sendTo != null) {
			if (sendTo.size() > 0) {
				messageQueue.add(new TextMessage(msg, sendTo, isLocal, from));
				Sender.setUpdateMessageQueue(runLater);
			}
		} else {
			if (clients.size() > 0) {
				ArrayList<Client> tmpList = new ArrayList<Client>();
				for (Client tmpClient : clients) {
					tmpList.add(tmpClient);
				}
				messageQueue.add(new TextMessage(msg, tmpList, isLocal, null));
				Sender.setUpdateMessageQueue(runLater);
			}
		}
	}
	
	private class MessageSender implements Runnable {
		private TextMessage[] msgQueue = new TextMessage[0];
		private boolean updatingQueue = false;
		private ArrayList<TextMessage> tmpMsgQueue = new ArrayList<TextMessage>();
		
		public MessageSender(boolean updateLater) {
			setUpdateMessageQueue(updateLater);
		}
		
		@Override
		public void run() {
			while (isConnected() && !isDisconnecting()) {
				for (TextMessage textMsg : msgQueue) {
					String h = ChatPreferences.handle;
					if (!textMsg.isLocal()) {
						if (textMsg.fromClient != null) {
							h = ((Client) textMsg.fromClient).cHandle;
						}
					}
					for (Client c : textMsg.getClients()) {
						if (!c.isDisconnecting()) {
							c.sendMessage(h + ChatCommon.SEPARATOR + ChatCommon.formatMessage(textMsg.getMsg()));
						} else if (textMsg.getMsg().equals(ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT))) {
							c.sendMessage(textMsg.getMsg());
							c.removeClient(true, false);
						}
					}
					if (textMsg.isLocal()) { Platform.runLater(new PostMessage(textMsg.getMsg(), null, true)); }
				}
				msgQueue = new TextMessage[0];
				
				if (isConnected()) {
					if (updatingQueue) {
						updateMessageQueue();
					}
					
					try {
						Thread.sleep((long) SLEEPTIME);
					} catch (InterruptedException intE) {
						
					}
				}
			}
		}
		
		private void updateMessageQueue() {
			TextMessage[] tmpArray = new TextMessage[tmpMsgQueue.size()];
			tmpMsgQueue.toArray(tmpArray);
			msgQueue = tmpArray;
			tmpMsgQueue = new ArrayList<TextMessage>();
			updatingQueue = false;
		}
		
		protected void setUpdateMessageQueue(boolean runLater) {
			if (!runLater) {
				while (updatingQueue) {
					try {
						Thread.sleep((long) SLEEPTIME);
					} catch (InterruptedException intE) {
						
					}
				}
				tmpMsgQueue = copyMsgArray(messageQueue, tmpMsgQueue);
				messageQueue = new ArrayList<TextMessage>();
				updatingQueue = true;
			} else {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						while (updatingQueue) {
							try {
								Thread.sleep((long) SLEEPTIME);
							} catch (InterruptedException intE) {
								
							}
						}
						tmpMsgQueue = copyMsgArray(messageQueue, tmpMsgQueue);
						messageQueue = new ArrayList<TextMessage>();
						updatingQueue = true;
					}
				});
			}
		}
		
		private ArrayList<TextMessage> copyMsgArray(ArrayList<TextMessage> from, ArrayList<TextMessage> to) {
			to = new ArrayList<TextMessage>();
			for (TextMessage item : from) {
				to.add(item);
			}
			
			return to;
		}
	}
	
	public void startDisconnect(boolean runLater) {
		if (!runLater) {
			Thread disc = new Thread(new SetDisconnect());
			disc.start();
		} else {
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					Thread disc = new Thread(new SetDisconnect());
					disc.start();
				}
			});
		}
	}
	
	private class SetDisconnect implements Runnable {
		private final Client[] tmpClients;
		
		public SetDisconnect() {
			setDisconnecting(true);
			
			Client[] tmpC = new Client[clients.size()];
			clients.toArray(tmpC);
			tmpClients = tmpC;
		}
		
		@Override
		public void run() {
			if (fileServerListen != null) { fileServerListen.setDisconnect(); }
			if (fileSender != null) { fileSender.setDisconnect(); }
			if (fileReceiver != null) {fileReceiver.setDisconnect(); }
			try {
				if (chatThread != null) {
					chatThread.join();
				}
				if (senderThread != null) {
					senderThread.join();
				}
				
				if (fileServerListenThread != null) {
					fileServerListenThread.join();
				}
				
				if (fileSenderThread != null) {
					fileSenderThread.join();
				}
				
				if (fileReceiverThread != null) {
					fileReceiverThread.join();
				}
			} catch (InterruptedException intE) {
				
			}
			
			for (Client c : tmpClients) {
				if (c.isAccepted() && !c.awaitingConfirmation) { 
					c.sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT));
				}
				try {
					c.disconnectSockets();
				} catch (IOException discE) {
					
				}
			}
			
			try {
				if (chatSock != null) {
					chatSock.close();
				}
				if (fileServer != null) {
					fileServer.close();
				}
			} catch (IOException ioE) {
				Logger.getLogger(HostConnection.class.getName()).log(Level.WARNING, "Unable to close host sockets on disconnect:" + System.getProperty("line.separator") + ioE.getMessage(), ioE);
				System.out.println("Unable to close host sockets on disconnect:" + System.getProperty("line.separator") + ((ioE.getMessage() != null) ? ioE.getMessage() : "Unspecified"));
			}
			
			if (chatSock != null && connectionThread != null) {
				try {
					connectionThread.join();
				} catch (InterruptedException interruptE) {
					
				}
			}
			
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					fileSender = null;
					fileServerListen = null;
					fileReceiver = null;
					clientChatListener = null;
					Sender = null;
					chatWindow.clearParticipants();
					setConnected(false);
					chatWindow.setDisconnected();
				}
			});
		}
	}

}
