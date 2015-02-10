package atChat;

import java.util.ArrayList;

public class TextMessage {
	private final String msg;
	private final HostConnection.Client[] sendTo;
	
	private final boolean local;
	
	public final HostConnection.Client fromClient;
	
	public TextMessage(String msg, ArrayList<HostConnection.Client> sendTo, HostConnection.Client from) {
		this.msg = msg;
		this.sendTo = new HostConnection.Client[sendTo.size()];
		sendTo.toArray(this.sendTo);
		
		local = false;
		fromClient = from;
	}
	
	public TextMessage(String msg, ArrayList<HostConnection.Client> sendTo, boolean local, HostConnection.Client from) {
		this.msg = msg;
		this.sendTo = new HostConnection.Client[sendTo.size()];
		sendTo.toArray(this.sendTo);
		
		this.local = local;
		fromClient = from;
	}
	
	protected boolean isLocal() {
		return local;
	}
	
	protected String getMsg() {
		return msg;
	}
	
	protected HostConnection.Client[] getClients() {
		return sendTo;
	}
}
