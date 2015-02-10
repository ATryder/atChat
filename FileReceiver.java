package atChat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FileReceiver implements Runnable {
	
	private int currentKey = 0;
	
	private HashMap<Integer, ReceiveFile> FR;
	private HashMap<Integer, ReceiveFile> pendingFR;
	private List<Integer> pendingRemovalFR;
	
	private boolean disconnect = false;
	
	private final ChatMain chatWindow;
	
	public FileReceiver(ChatMain chatWindow) {
		this.chatWindow = chatWindow;
	}
	
	protected void setDisconnect() {
		disconnect = true;
		
		if (FR != null) {
			for (ReceiveFile fr : FR.values()) {
				fr.disconnect(ChatCommon.DOWNLOAD_R_CANCELLED, true);
			}
		}
		
		if (fReceivers != null) {
			for (ReceiveFile fr : fReceivers) {
				fr.disconnect(ChatCommon.DOWNLOAD_R_CANCELLED, true);
			}
		}
	}
	
	@Override
	public void run() {
		
		if (!chatWindow.isHosting()) {
			clientReceiver();
		} else {
			hostReceiver();
		}
	}
	
	protected void disconnectByKey(int key, String reason, boolean runLater) {
		if (FR.containsKey(new Integer(key))) {
			FR.get(new Integer(key)).disconnect(reason, runLater);
		} else if (pendingFR.containsKey(new Integer(key))) {
			pendingFR.get(new Integer(key)).disconnect(reason, runLater);
		}
	}
	
	protected void addReceiver(ReceiveFile receiveFile) {
		if (!disconnect) {
			if (!chatWindow.isHosting()) {
				pendingFR.put(new Integer(currentKey), receiveFile);
				receiveFile.setReceiveKey(currentKey);
				currentKey ++;
			} else {
				pendingReceivers.add(receiveFile);
			}
		}
	}
	
	private void clientReceiver() {
		FR = new HashMap<Integer, ReceiveFile>();
		pendingFR = new HashMap<Integer, ReceiveFile>();
		pendingRemovalFR = new ArrayList<Integer>();
		
		while (!disconnect) {
			for (ReceiveFile fr : FR.values()) {
				if (fr.isCancelled()) {
					pendingRemovalFR.add(fr.getRFKey());
				} else {
					boolean sockConnected = false;
					boolean connectError = false;
					try {
						sockConnected = fr.isConnected();
					} catch (IOException connectionE) {
						connectError = true;
						ChatCommon.popError("File transfer from " + fr.getHandle() + " failed:" + System.getProperty("line.separator") + ((connectionE.getMessage() != null) ? connectionE.getMessage() : "Unspecified"), true);
					} finally {
						if (connectError && !fr.isCancelled()) {
							fr.disconnect(ChatCommon.DOWNLOAD_R_ERROR, true);
							chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(fr.getSendFileKey())), false);
							
						}
					}
					
					if (!connectError) {
						if (!sockConnected && !fr.isConnecting()) {
							boolean errored = false;
							try {
								fr.connectSocket();
							} catch (IOException sockE) {
								errored = true;
								if (!fr.isCancelled()) {
									ChatCommon.popError("File transfer from " + fr.getHandle() + " failed:" + System.getProperty("line.separator") + ((sockE.getMessage() != null) ? sockE.getMessage() : "Unspecified"), true);
								}
							} finally {
								if (errored) {
									if (!fr.isCancelled()) {
										fr.disconnect(ChatCommon.DOWNLOAD_R_ERROR, true);
										pendingRemovalFR.add(fr.getRFKey());
										chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(fr.getSendFileKey())), false);
										
									}
								}
							}
						} else if (sockConnected) {
							if (fr.run()) {
								pendingRemovalFR.add(fr.getRFKey());
							}
						} else if (!sockConnected && fr.isConnecting()) {
							if (fr.isExpired() && !fr.isCancelled()) {
								pendingRemovalFR.add(fr.getRFKey());
								fr.disconnect(ChatCommon.DOWNLOAD_R_TIMEDOUT, true);
								chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_TIMEDOUT + ChatCommon.SEPARATOR + Integer.toString(fr.getSendFileKey())), false);
								
							}
						}
					}
				}
			}
			
			if (!pendingFR.isEmpty()) {
				if (!disconnect) {
					FR.putAll(pendingFR);
					pendingFR.clear();
				} else {
					for (ReceiveFile rf : pendingFR.values()) {
						rf.disconnect(ChatCommon.DOWNLOAD_R_CANCELLED, true);
					}
				}
			}
			
			if (!pendingRemovalFR.isEmpty()) {
				for (Integer i : pendingRemovalFR) {
					if (!disconnect) {
						FR.remove(i);
					}
				}
				pendingRemovalFR.clear();
				if (FR.isEmpty() && pendingFR.isEmpty() && currentKey >= 1337) {
					currentKey = 0;
				}
			}
			
			if (FR.isEmpty()) {
				try {
					Thread.sleep(125);
				} catch (InterruptedException tE) {
					
				}
			}
		}
		
		pendingFR.clear();
		pendingRemovalFR.clear();
		FR.clear();
	}
	
	private List<ReceiveFile> fReceivers;
	private List<ReceiveFile> pendingReceivers;
	private List<ReceiveFile> pendingReceiverRemoval;
	
	private void hostReceiver() {
		fReceivers = new ArrayList<ReceiveFile>();
		pendingReceivers = new ArrayList<ReceiveFile>();
		pendingReceiverRemoval = new ArrayList<ReceiveFile>();
		
		while (!disconnect) {
			for (ReceiveFile rf : fReceivers) {
				if (rf.isCancelled()) {
					pendingReceiverRemoval.add(rf);
				} else if (rf.run()) {
					pendingReceiverRemoval.add(rf);
				}
			}
			
			if (!pendingReceiverRemoval.isEmpty()) {
				if (!disconnect) {
					fReceivers.removeAll(pendingReceiverRemoval);
					pendingReceiverRemoval.clear();
				}
			}
			
			if (!pendingReceivers.isEmpty()) {
				if (!disconnect) {
					fReceivers.addAll(pendingReceivers);
					pendingReceivers.clear();
				} else {
					for (ReceiveFile rf: pendingReceivers) {
						rf.disconnect(ChatCommon.DOWNLOAD_R_CANCELLED, true);
					}
				}
			}
			
			if (fReceivers.isEmpty()) {
				try {
					Thread.sleep(125);
				} catch (InterruptedException tE) {
					
				}
			}
		}
		
		fReceivers = null;
		pendingReceivers = null;
		pendingReceiverRemoval = null;
	}
}
