package atChat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FileSender implements Runnable {
	
	private List<SendFile> fSenders;
	private List<SendFile> pending;
	private List<SendFile> pendingRemoval;
	
	private boolean disconnect = false;
	
	private final ChatMain chatWindow;
	
	public FileSender(ChatMain chatWindow) {
		this.chatWindow = chatWindow;
	}
	
	protected void setDisconnect() {
		disconnect = true;
		if (chatWindow.isHosting()) {
			for (SendFile fSend : fSenders) {
				fSend.disconnect(ChatCommon.DOWNLOAD_S_CANCELLED, true);
			}
		} else {
			for (SendFile fSend : cFSenders.values()) {
				fSend.disconnect(ChatCommon.DOWNLOAD_S_CANCELLED, true);
			}
			
			for (SendFile fSend : pendingCFSenders.values()) {
				fSend.disconnect(ChatCommon.DOWNLOAD_S_CANCELLED, true);
			}
		}
	}
	
	@Override
	public void run() {
		if (chatWindow.isHosting()) {
			hostSender();
		} else {
			clientSender();
		}
	}
	
	private void hostSender() {
		fSenders = new ArrayList<SendFile>();
		pending = new ArrayList<SendFile>();
		pendingRemoval = new ArrayList<SendFile>();
		
		while (!disconnect) {
			for (SendFile fSend : fSenders) {
				if (fSend.isCancelled()) {
					pendingRemoval.add(fSend);
				} else {
					if (fSend.run()) {
						pendingRemoval.add(fSend);
					}
				}
			}
			
			if (!pendingRemoval.isEmpty()) {
				if (!disconnect) {
					fSenders.removeAll(pendingRemoval);
					pendingRemoval.clear();
				}
			}
			
			if (!pending.isEmpty()) {
				if (!disconnect) {
					fSenders.addAll(pending);
					pending.clear();
				} else {
					for (SendFile sf : pending) {
						sf.disconnect(ChatCommon.DOWNLOAD_S_CANCELLED, true);
					}
				}
			}
			
			if (fSenders.isEmpty()) {
				try {
					Thread.sleep(125);
				} catch (InterruptedException tE) {
					
				}
			}
		}
	}
	
	private HashMap<Integer, SendFile> cFSenders;
	private HashMap<Integer, SendFile> pendingCFSenders;
	private List<Integer> pendingRemovalCFSenders;
	private List<Integer> pPendingRemovalCFSenders;
	
	private int currentKey = 0;
	
	private void clientSender() {
		cFSenders = new HashMap<Integer, SendFile>();
		pendingCFSenders = new HashMap<Integer, SendFile>();
		pendingRemovalCFSenders = new ArrayList<Integer>();
		pPendingRemovalCFSenders = new ArrayList<Integer>();
		
		while (!disconnect) {
			for (SendFile fSend : cFSenders.values()) {
				if (fSend.isCancelled()) {
					pendingRemovalCFSenders.add(new Integer(fSend.getKey()));
				} else {
					if (fSend.run()) {
						pendingRemovalCFSenders.add(new Integer(fSend.getKey()));
					}
				}
			}
			
			if (!pendingRemovalCFSenders.isEmpty()) {
				for (Integer i : pendingRemovalCFSenders) {
					if (!disconnect) {
						cFSenders.remove(i);
					}
				}
				pendingRemovalCFSenders.clear();
			}
			
			if (!pendingCFSenders.isEmpty()) {
				for (SendFile sf : pendingCFSenders.values()) {
					if (!sf.isCancelled()) {
						if (!disconnect) {
							if (!sf.isExpired()) {
								if (sf.isConnecting()) {
									cFSenders.put(new Integer(sf.getKey()), sf);
									pPendingRemovalCFSenders.add(new Integer(sf.getKey()));
								}
							} else {
								pPendingRemovalCFSenders.add(new Integer(sf.getKey()));
								sf.disconnect(ChatCommon.DOWNLOAD_S_TIMEDOUT, true);
							}
						} else {
							sf.disconnect(ChatCommon.DOWNLOAD_S_CANCELLED, true);
						}
					} else {
						pPendingRemovalCFSenders.add(new Integer(sf.getKey()));
					}
				}
			}
			
			if (!pPendingRemovalCFSenders.isEmpty()) {
				for (Integer i : pPendingRemovalCFSenders) {
					if (!disconnect) {
						pendingCFSenders.remove(i);
					}
				}
				pPendingRemovalCFSenders.clear();
			}
			
			if (cFSenders.isEmpty() && pendingCFSenders.isEmpty()) {
				try {
					Thread.sleep(125);
				} catch (InterruptedException tE) {
					
				}
			}
		}
	}
	
	public boolean atMaxSenders() {
		return currentKey == Integer.MAX_VALUE;
	}
	
	protected void disconnectByKey(int key, String reason, boolean runLater) {
		if (pendingCFSenders.containsKey(new Integer(key))) {
			pendingCFSenders.get(new Integer(key)).disconnect(reason, runLater);
		} else if (cFSenders.containsKey(new Integer(key))) {
			cFSenders.get(new Integer(key)).disconnect(reason, runLater);
		}
	}
	
	protected void connectSender(int key, int rfKey, int remotePort, String clientKey) {
		if (pendingCFSenders.containsKey(new Integer(key)) && !disconnect) {
			try {
				pendingCFSenders.get(new Integer(key)).connectSocket(remotePort);
				pendingCFSenders.get(new Integer(key)).setRFKey(rfKey);
				pendingCFSenders.get(new Integer(key)).setClientKey(clientKey);
			} catch (IOException connectionE) {
				pendingCFSenders.get(new Integer(key)).disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
				if (!pendingCFSenders.get(new Integer(key)).isCancelled()) {
					chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), true);
					ChatCommon.popError("File transfer to " + chatWindow.getClient().getHostHandle() + " failed:" + System.getProperty("line.separator") + connectionE.getMessage(), true);
				}
			}
		}
	}
	
	protected void addSender(SendFile fileSender) {
		if (!disconnect) {
			if (chatWindow.isHosting()) {
				pending.add(fileSender);
			} else {
				if (cFSenders.isEmpty() && pendingCFSenders.isEmpty() && currentKey >= 1337) {
					currentKey = 0;
				}
				fileSender.setKey(currentKey);
				pendingCFSenders.put(new Integer(currentKey), fileSender);
				currentKey ++;
			}
		}
	}
}
