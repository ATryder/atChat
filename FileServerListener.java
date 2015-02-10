package atChat;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FileServerListener implements Runnable {
	private static final long SLEEPTIME = (long) 42;
	private static final long TIMEOUT = 60000 * 7;
	
	private HashMap<String, SendFile> fSenders = new HashMap<String, SendFile>();
	private HashMap<String, SendFile> pending = new HashMap<String, SendFile>();
	private List<String> fPendingRemoval = new ArrayList<String>();
	
	private HashMap<String, ReceiveFile> fReceivers = new HashMap<String, ReceiveFile>();
	private HashMap<String, ReceiveFile> pendingReceivers = new HashMap<String, ReceiveFile>();
	private List<String> rPendingRemoval = new ArrayList<String>();
	
	private List<socketChannel> sockets = new ArrayList<socketChannel>();
	private List<socketChannel> pendingRemoval = new ArrayList<socketChannel>();
	
	private boolean disconnect = false;
	
	private final ChatMain chatWindow;
	
	public FileServerListener(ChatMain chatWindow) {
		this.chatWindow = chatWindow;
	}
	
	protected void setDisconnect() {
		disconnect = true;
		
		for (SendFile sf : fSenders.values()) {
			sf.disconnect(ChatCommon.DOWNLOAD_S_CANCELLED, true);
		}
		
		for (SendFile sf : pending.values()) {
			sf.disconnect(ChatCommon.DOWNLOAD_S_CANCELLED, true);
		}
		
		for (ReceiveFile rf : fReceivers.values()) {
			rf.disconnect(ChatCommon.DOWNLOAD_R_CANCELLED, true);
		}
		
		for (ReceiveFile rf : pendingReceivers.values()) {
			rf.disconnect(ChatCommon.DOWNLOAD_R_CANCELLED, true);
		}
		
		for (socketChannel sc : sockets) {
			sc.close();
		}
	}
	
	private class socketChannel {
		private final SocketChannel socket;
		private final SocketIO sIO;
		
		private final long startTime;
		
		public socketChannel(SocketChannel socket) throws IOException {
			this.socket = socket;
			this.socket.configureBlocking(false);
			sIO = new SocketIO();
			
			startTime = System.currentTimeMillis();
		}
		
		protected String getIP() {
			return socket.socket().getInetAddress().getHostAddress();
		}
		
		protected SocketChannel getChannel() {
			return socket;
		}
		
		public boolean isConnected() {
			boolean finished = false;
			try {
				finished = socket.finishConnect();
			} catch (IOException finE) {
				
			}
			return finished;
		}
		
		protected String read() throws IOException {
			return sIO.readBytes(socket, chatWindow.getHost().aes);
		}
		
		protected void accepted() throws IOException {
			sIO.writeBytes(socket, ChatCommon.ACCEPT_TRANSFER, chatWindow.getHost().aes);
		}
		
		protected void refused() throws IOException {
			sIO.writeBytes(socket, ChatCommon.DOWNLOAD_R_REFUSED, chatWindow.getHost().aes);
		}
		
		protected void close() {
			try {
				socket.close();
			} catch (IOException e) {
				
			}
		}
		
		protected boolean isExpired() {
			return (System.currentTimeMillis() - startTime >= TIMEOUT);
		}
	}
	
	@Override
	public void run() {
		while (!disconnect) {
			if (!fSenders.isEmpty() || !fReceivers.isEmpty()) {
				SocketChannel tmpSock = null;
				try {
					tmpSock = chatWindow.getHost().getFileServer().accept();
				} catch (IOException fileE) {
					
				} finally {
					if (tmpSock != null) {
						if (!disconnect) {
							socketChannel newSock = null;
							try {
								newSock = new socketChannel(tmpSock);
							} catch (IOException newE) {
								
								System.out.println((newE.getMessage() != null) ? newE.getMessage() : "Unspecified");
							} finally {
								if (newSock != null) { sockets.add(newSock); }
							}
						} else {
							try {
								tmpSock.close();
							} catch (IOException closeE) {
								
							}
						}
					}
				}
				
				for (socketChannel sock : sockets) {
					if (!sock.isExpired()) {
						String input = null;
						try {
							if (sock.isConnected()) { input = sock.read(); }
						} catch (IOException readE) {
							
						} finally {
							if (input != null) {
								if (!input.equals("\n")) {
									String[] sMsg = input.split(ChatCommon.SPLIT_SEPARATOR);
									if (sMsg.length > 1 && sMsg[0].equals(ChatCommon.ACCEPT_TRANSFER)) {
										String key = sMsg[1];
										if (fSenders.containsKey(key)) {
											SendFile fSend = fSenders.get(key);
											fPendingRemoval.add(fSend.getKey() + ":" + fSend.getClientKey());
											pendingRemoval.add(sock);
											boolean err = false;
											try {
												sock.accepted();
											} catch (IOException e) {
												err = true;
												sock.close();
												fSend.disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
											} finally {
												if (!err) {
													fSend.setSocket(sock.getChannel());
													//start sending file
													chatWindow.getHost().getFileSender().addSender(fSend);
												}
											}
										} else {
											try {
												sock.refused();
											} catch (IOException refE) {
												
											} finally {
												sock.close();
												pendingRemoval.add(sock);
											}
										}
									} else {
										String key = input;
										//for receivers
										if (fReceivers.containsKey(key)) {
											ReceiveFile rf = fReceivers.get(key);
											rPendingRemoval.add(rf.getRFKey() + ":" + rf.getClientKey());
											pendingRemoval.add(sock);
											boolean err = false;
											try {
												sock.accepted();
											} catch (IOException accE) {
												err = true;
												sock.close();
												rf.disconnect(ChatCommon.DOWNLOAD_R_ERROR, true);
											} finally {
												if (!err) {
													rf.setSocket(sock.getChannel());
													//start receiving file
													chatWindow.getHost().getFileReceiver().addReceiver(rf);
												}
											}
										} else {
											try {
												sock.refused();
											} catch (IOException refE) {
												
											} finally {
												sock.close();
												pendingRemoval.add(sock);
											}
										}
									}
								} else {
									//disconnected
									sock.close();
									pendingRemoval.add(sock);
								}
							}
						}
					} else {
						try {
							sock.refused();
						} catch (IOException e) {
							
						} finally {
							sock.close();
							pendingRemoval.add(sock);
						}
					}
				}
				
				for (SendFile sF : fSenders.values()) {
					if (sF.isExpired() || sF.isCancelled()) {
						if (!sF.isCancelled()) {
							sF.disconnect(ChatCommon.DOWNLOAD_S_TIMEDOUT, true);
						}
						fPendingRemoval.add(sF.getKey() + ":" + sF.getClientKey());
					}
				}
				
				for (ReceiveFile rF : fReceivers.values()) {
					if (rF.isExpired() || rF.isCancelled()) {
						if (!rF.isCancelled()) {
							rF.disconnect(ChatCommon.DOWNLOAD_S_TIMEDOUT, true);
						}
						rPendingRemoval.add(rF.getRFKey() + ":" + rF.getClientKey());
					}
				}
			}
			
			if (!fPendingRemoval.isEmpty()) {
				for (String str : fPendingRemoval) {
					if (!disconnect) {
						fSenders.remove(str);
					}
				}
				fPendingRemoval.clear();
			}
			
			if (!pendingRemoval.isEmpty()) {
				if (!disconnect) {
					sockets.removeAll(pendingRemoval);
					pendingRemoval.clear();
				}
			}
			
			if (!pending.isEmpty()) {
				if (!disconnect) {
					fSenders.putAll(pending);
					pending.clear();
				} else {
					for (SendFile sf : pending.values()) {
						sf.disconnect(ChatCommon.DOWNLOAD_S_CANCELLED, true);
					}
				}
			}
			
			if (!rPendingRemoval.isEmpty()) {
				for (String str : rPendingRemoval) {
					if (!disconnect) {
						fReceivers.remove(str);
					}
				}
				rPendingRemoval.clear();
			}
			
			if (!pendingReceivers.isEmpty()) {
				if (!disconnect) {
					fReceivers.putAll(pendingReceivers);
					pendingReceivers.clear();
				} else {
					for (ReceiveFile rf : pendingReceivers.values()) {
						rf.disconnect(ChatCommon.DOWNLOAD_R_CANCELLED, true);
					}
				}
			}
			
			try {
				Thread.sleep(SLEEPTIME);
			} catch (InterruptedException tE) {
				
			}
		}
		
		pending.clear();
		pendingRemoval.clear();
		fPendingRemoval.clear();
		fSenders.clear();
		fReceivers.clear();
		pendingReceivers.clear();
		rPendingRemoval.clear();
	}
	
	protected void addSender(SendFile fileSender, String key) {
		if (!disconnect) {
			pending.put(key, fileSender);
		}
	}
	
	protected void addReceiver(ReceiveFile fileReceiver, String key) {
		if (!disconnect) {
			pendingReceivers.put(key, fileReceiver);
		}
	}
}
