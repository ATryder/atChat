package atChat;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public class SendFile {
	private static final long TIMEOUT = 60000 * 17;
	
	private final HostConnection.Client mClient;
	private final String handle;
	private final ChatMain chatWindow;
	
	private long startTime;
	
	private boolean filesChosen = false;
	private List<String> mFiles;
	private long mSize = 0;
	private long mTotalSent = 0;
	
	private SocketChannel socket;
	private final SocketIO sIO;
	
	private BufferedInputStream fileIn = null;
	
	private boolean mCancelled = false;
	private boolean mSending = false;
	
	private int currentFileLoc = 0;
	private boolean transferReady = false;
	private boolean accepted = false;
	
	private VBox downloadBox;
	private Label toLabel;
	private Label currentFile;
	private Label eta;
	private Label speed;
	private ProgressBar progressBar;
	private Button cancelButton;
	private ChangeListener<Bounds> changeListener;
	private ChangeListener<Number> parentHeight;
	
	private ChatCommon.DownloadCalculator dCalc;
	
	private int key;
	private int rfKey = -1;
	private String clientKey;
	
	private int numErrors = 0;
	
	private ArrayList<HostConnection.Client> sendTo = new ArrayList<HostConnection.Client>(); 
	
	public SendFile(HostConnection.Client client, ChatMain chatWindow) {
		startTime = System.currentTimeMillis();
		
		mClient = client;
		clientKey = mClient.getKey();
		key = mClient.setSendFile(this);
		this.chatWindow = chatWindow;
		handle = mClient.cHandle;
		
		sendTo.add(mClient);
		sIO = new SocketIO();
	}
	
	public SendFile(ChatMain chatWindow) throws IOException {
		startTime = System.currentTimeMillis();
		
		mClient = null;
		this.chatWindow = chatWindow;
		handle = chatWindow.getClient().getHostHandle();
		
		socket = SocketChannel.open();
		socket.configureBlocking(false);
		socket.socket().setTcpNoDelay(true);
		sIO = new SocketIO();
	}
	
	protected void connectSocket(int remotePort) throws IOException {
		startTime = System.currentTimeMillis();
		socket.connect(new InetSocketAddress(ChatPreferences.rIPAddress, remotePort));
	}
	
	public boolean isConnecting() {
		return socket.isConnected() || socket.isConnectionPending();
	}
	
	public boolean isExpired() {
		return System.currentTimeMillis() - startTime >= TIMEOUT;
	}
	
	public int getKey() {
		return key;
	}
	
	protected void setKey(int key) {
		this.key = key;
	}
	
	protected void setRFKey(int rfKey) {
		this.rfKey = rfKey;
	}
	
	protected void setClientKey(String clientKey) {
		this.clientKey = clientKey;
	}
	
	protected String getClientKey() {
		return clientKey;
	}
	
	protected void popChooser(boolean sendDir) {
		if (!sendDir) {
			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Select Files");
			fileChooser.setInitialDirectory((ChatPreferences.lastFilePath.exists()) ? ChatPreferences.lastFilePath : new File(System.getProperty("user.home")));
			List<File> fileList = fileChooser.showOpenMultipleDialog(this.chatWindow.getStage());
			
			if (fileList != null) {
				filesChosen = true;
				ChatPreferences.lastFilePath = (fileList.get(0).getParentFile() != null) ? fileList.get(0).getParentFile() : new File(System.getProperty("user.home"));
				new Thread(new FullFileList(fileList, this, sendDir)).start();
			} else { mFiles = null; }
		} else {
			DirectoryChooser dirChooser = new DirectoryChooser();
			dirChooser.setTitle("Select Files");
			dirChooser.setInitialDirectory((ChatPreferences.lastDirPath.exists()) ? ChatPreferences.lastDirPath : new File(System.getProperty("user.home")));
			File file = dirChooser.showDialog(this.chatWindow.getStage());
			
			if (file != null) {
				if (file.listFiles() != null) {
					filesChosen = true;
					ChatPreferences.lastDirPath = (file.getParentFile() != null) ? file.getParentFile() : file;
					List<File> fileList = new ArrayList<File>();
					fileList.add(file);
					new Thread(new FullFileList(fileList, this, sendDir)).start();
				} else {
					mFiles = null;
				}
			} else { mFiles = null; }
		}
	}
	
	protected List<String> getList() {
		return mFiles;
	}
	
	public boolean filesWereSelected() {
		return filesChosen;
	}
	
	private class FullFileList implements Runnable {
		private final SendFile thisSender;
		private final List<File> initialList;
		private final boolean isDirSend;
		
		public FullFileList(List<File> fileList, SendFile thisSender, boolean isDirSend) {
			initialList = fileList;
			this.thisSender = thisSender;
			this.isDirSend = isDirSend;
		}
		
		@Override
		public void run() {
			mFiles = setFullFileList(initialList);
			
			if (!mCancelled  && !mFiles.isEmpty()) {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						currentFile.setText("Awaiting Confirmation");
						eta.setText("ETA: ??");
						speed.setText("Speed: 0/Bps");
					}
				});
				
				String fileList = getStringList(!isDirSend);
				startTime = System.currentTimeMillis();
				
				if (chatWindow.isHosting()) {
					if (chatWindow.getHost().isConnected() && !chatWindow.getHost().isDisconnecting()) {
						chatWindow.getHost().fileServerListen.addSender(thisSender, getKey() + ":" + mClient.getKey());
						ArrayList<HostConnection.Client> sendTo = new ArrayList<HostConnection.Client>();
						sendTo.add(mClient);
						chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(((isDirSend) ? ChatCommon.TRANSFER_DIRECTORY : ChatCommon.TRANSFER_FILES) + ChatCommon.SEPARATOR + Integer.toString(getKey()) + ChatCommon.SEPARATOR + mClient.getKey() + ChatCommon.SEPARATOR + Integer.toString(ChatPreferences.lFilePort) + fileList), sendTo, false, false, null);
					}
				} else {
					if (chatWindow.getClient().isConnected()) {
						chatWindow.getClient().getFileSender().addSender(thisSender);
						chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(((isDirSend) ? ChatCommon.TRANSFER_DIRECTORY : ChatCommon.TRANSFER_FILES) + ChatCommon.SEPARATOR + Integer.toString(getKey()) + fileList), true);
					}
				}
			}
		}
	}
	
	private List<String> setFullFileList(List<File> fileList) {
		ArrayList<String> dirs = new ArrayList<String>();
		ArrayList<String> files = new ArrayList<String>();
		for (File file : fileList) {
			if (!mCancelled) {
				if (file.isDirectory()) {
					dirs.add(file.getPath());
				} else {
					files.add(file.getPath());
				}
			} else { break; }
		}
		
		if (!mCancelled) {
			Collections.sort(dirs, String.CASE_INSENSITIVE_ORDER);
			Collections.sort(files, String.CASE_INSENSITIVE_ORDER);
			
			ArrayList<String> dirContents = new ArrayList<String>();
			for (String dir : dirs) {
				dirContents.add(dir);
				dirContents.addAll(recursiveDirectory(new File(dir)));
				if (mCancelled) { break; }
			}
			dirContents.addAll(files);
			
			return dirContents;
		} else { return new ArrayList<String>(); }
	}
	
	private List<String> recursiveDirectory(File directory) {
		ArrayList<String> dirs = new ArrayList<String>();
		ArrayList<String> files = new ArrayList<String>();
		
		File[] contents = directory.listFiles();
		if (contents != null) {
			for (File file : contents) {
				if (!mCancelled) {
					if (file.isDirectory()) {
						dirs.add(file.getPath());
					} else {
						files.add(file.getPath());
					}
				} else {
					break;
				}
			}
			if (!mCancelled) {
				Collections.sort(dirs, String.CASE_INSENSITIVE_ORDER);
				Collections.sort(files, String.CASE_INSENSITIVE_ORDER);
				
				ArrayList<String> dirContents = new ArrayList<String>();
				for (String dir : dirs) {
					dirContents.add(dir);
					dirContents.addAll(recursiveDirectory(new File(dir)));
					if (mCancelled) { break; }
				}
				dirContents.addAll(files);
				
				return dirContents;
			} else { return new ArrayList<String>(); }
		} else { return new ArrayList<String>(); }
	}
	
	protected String getStringList(boolean nameOnly) {
		String list = "";
		if (!nameOnly) {
			int originalDirLength = 0;
			if (new File(mFiles.get(0)).getParent() != null) {
				if (new File(mFiles.get(0)).getParentFile().getParent() != null) {
					originalDirLength = new File(mFiles.get(0)).getParent().length() + 1;
				} else {
					originalDirLength = new File(mFiles.get(0)).getParent().length();
				}
			}
			
			for (String file : mFiles) {
				list += ChatCommon.SEPARATOR + ((new File(file).isDirectory()) ? file.substring(originalDirLength) + File.separator : file.substring(originalDirLength)) + ChatCommon.SEPARATOR + Long.toString(new File(file).length());
			}
		} else {
			for (String file : mFiles) {
				File item = new File(file);
				list += ChatCommon.SEPARATOR + item.getName() + ChatCommon.SEPARATOR + Long.toString(item.length());
			}
		}
		
		return list;
	}
	
	public ChangeListener<Bounds> getDownloadBoxListener() {
		return changeListener;
	}
	
	public ChangeListener<Number> getDownloadBoxParentListener() {
		return parentHeight;
	}
	
	protected VBox createDownloadBox(final VBox parent, final ScrollPane scroll) {
		downloadBox = new VBox(7);
		parent.setVgrow(downloadBox, Priority.NEVER);
		downloadBox.setPadding(new Insets(3, 3, 3, 3));
		
		downloadBox.setStyle("-fx-border-size: 0;"
				+ "-fx-border-color: grey;"
				+ "-fx-border-style: solid;"
				+ "-fx-border-radius: 5;"
				+ "-fx-background-color: transparent;");
		
		Label typeLabel = new Label("Send");
		typeLabel.setStyle("-fx-text-fill: rgb(93%, 93%, 93%); -fx-font-size: 11px;");
		
		toLabel = new Label(handle);
		toLabel.setAlignment(Pos.CENTER);
		toLabel.setWrapText(false);
		toLabel.setEllipsisString("...");
		downloadBox.setVgrow(toLabel, Priority.NEVER);
		
		toLabel.setStyle("-fx-text-fill: rgb(32, 186, 255); -fx-font-size: 14px; -fx-font-weight: bold;");
		
		currentFile = new Label("Preparing files");
		currentFile.setAlignment(Pos.CENTER);
		currentFile.setWrapText(false);
		currentFile.setEllipsisString("...");
		downloadBox.setVgrow(currentFile, Priority.NEVER);
		currentFile.setStyle(ChatCommon.msgStyle);
		
		VBox topBox = new VBox();
		topBox.getChildren().addAll(typeLabel, toLabel, currentFile);
		
		eta = new Label("ETA:  ??");
		eta.setWrapText(false);
		eta.setEllipsisString("...");
		downloadBox.setVgrow(eta, Priority.NEVER);
		eta.setStyle(ChatCommon.msgStyle);
		
		speed = new Label("Rate:  0Bp/s");
		speed.setWrapText(false);
		speed.setEllipsisString("...");
		downloadBox.setVgrow(speed, Priority.NEVER);
		speed.setStyle(ChatCommon.msgStyle);
		
		progressBar = new ProgressBar(0);
		progressBar.setMaxHeight(16);
		progressBar.setMinHeight(16);
		progressBar.setPrefHeight(16);
		progressBar.setMinWidth(64);
		downloadBox.setVgrow(progressBar, Priority.NEVER);
		
		cancelButton = new Button("Cancel");
		cancelButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				if (mCancelled) {
					chatWindow.removeDownloadBox(downloadBox, changeListener, parentHeight);
				} else {
					disconnect(ChatCommon.DOWNLOAD_S_CANCELLED, false);
					if (mClient == null) {
						chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_CANCELLED + ChatCommon.SEPARATOR + Integer.toString(rfKey)), false);
					} else {
						chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_CANCELLED + ChatCommon.SEPARATOR + Integer.toString(rfKey)), sendTo, false, false, null);
					}
				}
			}
		});
		
		String buttonStyle = "-fx-font-size: 12px;"
				+ "-fx-text-fill: rgb(93%, 93%, 93%);"
				+ "-fx-font-family: Arial;"
				+ "-fx-background-radius: 0;"
				+ "-fx-background-insets: 0;"
				+ "-fx-background-color: linear-gradient(from 0% 0% to 0% 100%, rgb(17%, 17%, 17%), rgb(17%, 17%, 17%) 41%, rgb(0%, 0%, 0%) 49%, rgb(0%, 0%, 0%) 75%, rgb(17%, 17%, 17%));";
		
		cancelButton.setStyle(buttonStyle);
		
		int scrollBarSize = 0;
		if (parent.getHeight() + 5 >= scroll.getHeight()) {
			scrollBarSize = 9;
		}
		double padding = downloadBox.getPadding().getLeft() + downloadBox.getPadding().getRight() + parent.getPadding().getLeft() + parent.getPadding().getRight() + 7 + scrollBarSize;
		double val = (scroll.getWidth() >= 64 + padding) ? scroll.getWidth() - padding : 64;
		toLabel.setPrefWidth(val);
		toLabel.setMaxWidth(val);
		toLabel.setMinWidth(val);
		
		currentFile.setPrefWidth(val);
		currentFile.setMaxWidth(val);
		currentFile.setMinWidth(val);
		
		progressBar.setPrefWidth(val);
		progressBar.setMaxWidth(val);
		progressBar.setMinWidth(val);
		
		cancelButton.setPrefWidth(val);
		cancelButton.setMaxWidth(val);
		cancelButton.setMinWidth(val);
		
		final VBox bottomBox = new VBox();
		bottomBox.getChildren().addAll(eta, speed, progressBar);
		
		changeListener = new ChangeListener<Bounds>() {
			@Override
			public void changed(ObservableValue<? extends Bounds> observable, Bounds oldValue, Bounds newValue) {
				int scrollBarSize = 0;
				if (parent.getHeight() + 5 >= scroll.getHeight()) {
					scrollBarSize = 9;
				}
				
				double padding = downloadBox.getPadding().getLeft() + downloadBox.getPadding().getRight() + parent.getPadding().getLeft() + parent.getPadding().getRight() + 7 + scrollBarSize;
				double val = (newValue.getWidth() >= 64 + padding) ? scroll.getWidth() - padding : 64;
				
				toLabel.setPrefWidth(val);
				toLabel.setMaxWidth(val);
				toLabel.setMinWidth(val);
				
				currentFile.setPrefWidth(val);
				currentFile.setMaxWidth(val);
				currentFile.setMinWidth(val);
				
				progressBar.setPrefWidth(val);
				progressBar.setMaxWidth(val);
				progressBar.setMinWidth(val);
				
				cancelButton.setPrefWidth(val);
				cancelButton.setMaxWidth(val);
				cancelButton.setMinWidth(val);
			}
		};
		scroll.viewportBoundsProperty().addListener(changeListener);
		
		parentHeight = new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				int scrollBarSize = 0;
				if (parent.getHeight() + 5 >= scroll.getHeight()) {
					scrollBarSize = 9;
				}
				
				double padding = downloadBox.getPadding().getLeft() + downloadBox.getPadding().getRight() + parent.getPadding().getLeft() + parent.getPadding().getRight() + 7 + scrollBarSize;
				double val = (scroll.getViewportBounds().getWidth() >= 64 + padding) ? scroll.getWidth() - padding : 64;
				
				toLabel.setPrefWidth(val);
				toLabel.setMaxWidth(val);
				toLabel.setMinWidth(val);
				
				currentFile.setPrefWidth(val);
				currentFile.setMaxWidth(val);
				currentFile.setMinWidth(val);
				
				progressBar.setPrefWidth(val);
				progressBar.setMaxWidth(val);
				progressBar.setMinWidth(val);
				
				cancelButton.setPrefWidth(val);
				cancelButton.setMaxWidth(val);
				cancelButton.setMinWidth(val);
			}
		};
		parent.heightProperty().addListener(parentHeight);
		
		downloadBox.getChildren().addAll(topBox, bottomBox, cancelButton);
		
		dCalc = new ChatCommon().new DownloadCalculator(eta, speed);

		return downloadBox;
	}
	
	protected void setSocket(SocketChannel s) {
		startTime = System.currentTimeMillis();
		socket = s;
		try {
			socket.socket().setTcpNoDelay(true);
		} catch (Exception e) {
			
		}
	}
	
	public boolean isCancelled() {
		return mCancelled;
	}
	
	protected void disconnect(final String reason, boolean runLater) {
		if (!mCancelled) {
			mCancelled = true;
			
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException cDiscE) {
					System.out.println("Unable to close file transfer socket:" + System.getProperty("line.separator") + ((cDiscE.getMessage() != null) ? cDiscE.getMessage() : "Unspecified"));
				}
			}
			
			if (fileIn != null) {
				try {
					fileIn.close();
				} catch (IOException fileinE) {
					
				}
			}
			
			if (mClient != null) { mClient.nullSendFile(this, runLater); }
			
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					switch(reason) {
					case ChatCommon.DOWNLOAD_S_CANCELLED:
						currentFile.setText("Cancelled");
						eta.setText("ETA: Never");
						speed.setText("Speed: 0B/s");
						break;
					case ChatCommon.DOWNLOAD_COMPLETE:
						currentFile.setText("Complete");
						eta.setText("ETA: Done");
						speed.setText("Speed: 0B/s");
						break;
					case ChatCommon.DOWNLOAD_S_REFUSED:
						currentFile.setText("Refused");
						eta.setText("ETA: Never");
						speed.setText("Speed: 0B/s");
						break;
					case ChatCommon.DOWNLOAD_S_TIMEDOUT:
						currentFile.setText("Timed Out");
						eta.setText("ETA: Never");
						speed.setText("Speed: 0B/s");
						break;
					case ChatCommon.DOWNLOAD_S_ERROR:
						currentFile.setText("Error");
						eta.setText("ETA: Never");
						speed.setText("Speed: 0B/s");
					}
					cancelButton.setText("Dismiss");
				}
			});
		}
	}
	
	public boolean run() {
		boolean done = false;
		
		if (!mCancelled) {
			if (mClient != null) {
				hostSender(done);
			} else {
				clientSender(done);
			}
		}
		
		return done;
	}
	
	private boolean hostSender(boolean done) {
		int numErrors = 0;
		
		if (!mSending) {
			String reply = null;
			boolean errored = false;
			try {
				reply = sIO.readBytes(socket, chatWindow.getHost().aes);
			} catch (IOException e) {
				numErrors ++;
				errored = true;
				if (numErrors >= 9 && !mCancelled) {
					done = true;
					ChatCommon.popError("File transfer to " + handle + " failed:" + System.getProperty("line.separator") + ((e.getMessage() != null) ? e.getMessage() : "Unspecified"), true);
					System.out.println("File transfer to " + handle + " failed:" + System.getProperty("line.separator") + ((e.getMessage() != null) ? e.getMessage() : "Unspecified"));
				}
			} finally {
				if (!errored) {
					if (transferReady) { dCalc.calc(0, mSize - mTotalSent); }
					numErrors = 0;
					if (reply != null) {
						if (!reply.equals("\n")) {
							String[] msg = reply.split(ChatCommon.SPLIT_SEPARATOR);
							
							if (msg.length > 1 && msg[0].equals(ChatCommon.DOWNLOAD_SETFILE)) {
								int index = -1;
								try {
									index = Integer.parseInt(msg[1]);
								} catch (NumberFormatException numE) {
									
								} finally {
									if (index >= 0) {
										if (!transferReady) {
											if (msg.length > 2) {
												int rKey = -1;
												try {
													rKey = Integer.parseInt(msg[2]);
												} catch (NumberFormatException keyE) {
													
												} finally {
													if (rKey > -1) {
														rfKey = rKey;
														transferReady = true;
														currentFileLoc = index;
														startTime = 0;
														dCalc.setLastTime(System.currentTimeMillis());
														if (msg.length > 3) {
															long totalUpload = -1;
															try {
																totalUpload = Long.parseLong(msg[3]);
															} catch (NumberFormatException numFormatE) {
																
															} finally {
																if (totalUpload > 0) {
																	mSize = totalUpload;
																} else if (!mCancelled) {
																	done = true;
																	disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
																	ChatCommon.popError("File Transfer to " + handle + " failed:" + System.getProperty("line.separator") + "Client sent improperly formatted response.", true);
																	System.out.println("File Transfer to " + handle + " failed:" + System.getProperty("line.separator") + "Client sent improperly formatted response.");
																	chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), sendTo, false, false, null);
																	
																}
															}
														}
													} else if (!mCancelled) {
														done = true;
														disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
														ChatCommon.popError("File Transfer to " + handle + " failed:" + System.getProperty("line.separator") + "Client sent improperly formatted response.", true);
														System.out.println("File Transfer to " + handle + " failed:" + System.getProperty("line.separator") + "Client sent improperly formatted response.");
													}
												}
											} else if (!mCancelled) {
												done = true;
												disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
												ChatCommon.popError("File Transfer to " + handle + " failed:" + System.getProperty("line.separator") + "Client sent improperly formatted response.", true);
												System.out.println("File Transfer to " + handle + " failed:" + System.getProperty("line.separator") + "Client sent improperly formatted response.");
											}
										}
										if (!done) {
											if (index >= 0 && index < mFiles.size()) {
												//Start sending file
												mSending = true;
												currentFileLoc = index;
												Platform.runLater(new Runnable() {
													@Override
													public void run() {
														if (!mCancelled) {
															currentFile.setText(new File(mFiles.get(currentFileLoc)).getName());
														}
													}
												});
											} else if (!mCancelled) {
												//File is out of array bounds
												done = true;
												disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
												ChatCommon.popError("File Transfer to " + handle + " failed:" + System.getProperty("line.separator") + "Client requested out of bounds file.", true);
												System.out.println("File Transfer to " + handle + " failed:" + System.getProperty("line.separator") + "Client requested out of bounds file.");
												chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), sendTo, false, false, null);
												
											}
										}
									} else if (!mCancelled) {
										//index is not formatted properly
										done = true;
										disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
										ChatCommon.popError("File Transfer to " + handle + " failed:" + System.getProperty("line.separator") + "Client failed to send properly formatted response.", true);
										System.out.println("File Transfer to " + handle + " failed:" + System.getProperty("line.separator") + "Client failed to send properly formatted response.");
										chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), sendTo, false, false, null);
										
									}
								}
							} else if (msg[0].equals(ChatCommon.DOWNLOAD_COMPLETE)) {
								done = true;
								disconnect(ChatCommon.DOWNLOAD_COMPLETE, true);
							} else if (!mCancelled) {
								//response is not formatted properly
								done = true;
								disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
								ChatCommon.popError("File Transfer to " + handle + " failed:" + System.getProperty("line.separator") + "Client failed to send properly formatted response.", true);
								System.out.println("File Transfer to " + handle + " failed:" + System.getProperty("line.separator") + "Client failed to send properly formatted response.");
								chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), sendTo, false, false, null);
								
							}
						} else {
							done = true;
							disconnect(ChatCommon.DOWNLOAD_S_CANCELLED, true);
						}
					} else if (System.currentTimeMillis() - startTime >= TIMEOUT && !transferReady && !mCancelled) {
						done = true;
						disconnect(ChatCommon.DOWNLOAD_S_TIMEDOUT, true);
						chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_TIMEDOUT + ChatCommon.SEPARATOR + Integer.toString(rfKey)), sendTo, false, false, null);
						
					}
				} else if (done && !mCancelled) {
					//Input stream errored
					disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
					chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), sendTo, false, false, null);
					
				}
			}
		} else {
			if (currentFileLoc < mFiles.size() && currentFileLoc >= 0) {
				if (new File(mFiles.get(currentFileLoc)).exists()) {
					boolean errored = false;
					try {
						if (fileIn == null) {
							fileIn = new BufferedInputStream(new FileInputStream(new File(mFiles.get(currentFileLoc))));
						}
						
						int written = 0;
						if ((written = sIO.writeFile(socket, fileIn)) == -1) {
							fileIn.close();
							fileIn = null;
							mSending = false;
							if (mTotalSent >= mSize) {
								disconnect(ChatCommon.DOWNLOAD_COMPLETE, true);
							}
						} else {
							mTotalSent += written;
							if (dCalc.calc(written, mSize - mTotalSent) || mTotalSent / (double) mSize >= 1) {
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										progressBar.setProgress(mTotalSent / (double) mSize);
									}
								});
							}
						}
					} catch (IOException fileE) {
						//input/ouput error
						errored = true;
						done = true;
						if (!mCancelled) {
							ChatCommon.popError("File transfer to " + handle + " failed:" + System.getProperty("line.separator") + ((fileE.getMessage() != null) ? fileE.getMessage() : "Unspecified"), true);
							chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), sendTo, false, false, null);
							
							disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
						}
					} finally {
						if (errored || !mSending) {
							if (fileIn != null) {
								try {
									fileIn.close();
								} catch (IOException closeE) {
									
								}
							}
							fileIn = null;
						}
					}
				} else if (!mCancelled) {
					//File does not exist
					done = true;
					ChatCommon.popError("File transfer to " + handle + " failed:" + System.getProperty("line.separator") + "File " + new File(mFiles.get(currentFileLoc)).getName() + " does not exist!", true);
					chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), sendTo, false, false, null);
					
					disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
				}
			} else if (!mCancelled) {
				//File index out of range
				done = true;
				ChatCommon.popError("File transfer to " + handle + " failed:" + System.getProperty("line.separator") + "File index out of range.", true);
				chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), sendTo, false, false, null);
				
				disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
			}
		}
		
		return done;
	}
	
	private boolean clientSender(boolean done) {
		boolean connected = false;
		if (socket.isConnectionPending()) {
			try {
				connected = socket.finishConnect();
			} catch (IOException connectE) {
				connected = false;
				if (!mCancelled) {
					ChatCommon.popError("File transfer from " + handle + " failed:" + System.getProperty("line.separator") + ((connectE.getMessage() != null) ? connectE.getMessage() : "Unspecified"), true);
					chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), false);
					disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
				}
			}
		} else { connected = socket.isConnected(); }
		
		if (connected && !mCancelled) {
			if (!accepted) {
				if (!transferReady) {
					boolean errored = false;
					try {
						sIO.writeBytes(socket, Integer.toString(rfKey) + ":" + clientKey, chatWindow.getClient().aes);
					} catch (IOException e) {
						errored = true;
						numErrors ++;
						if (numErrors >= 9) {
							done = true;
							if (!mCancelled) {
								ChatCommon.popError("Download from " + handle + " failed:" + System.getProperty("line.separator") + ((e.getMessage() != null) ? e.getMessage() : "Unspecified"), true);
								System.out.println("Download from " + handle + " failed:" + System.getProperty("line.separator") + ((e.getMessage() != null) ? e.getMessage() : "Unspecified"));
							}
						}
					} finally {
						if (!errored) {
							numErrors = 0;
							transferReady = true;
						} else if (done && !mCancelled) {
							disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
							chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), true);
						}
					}
				} else {
					String input = null;
					try {
						input = sIO.readBytes(socket, chatWindow.getClient().aes);
					} catch (IOException e) {
						done = true;
						if (!mCancelled) {
							ChatCommon.popError("Download from " + handle + " failed:" + System.getProperty("line.separator") + ((e.getMessage() != null) ? e.getMessage() : "Unspecified"), true);
							System.out.println("Download from " + handle + " failed:" + System.getProperty("line.separator") + ((e.getMessage() != null) ? e.getMessage() : "Unspecified"));
							chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), true);
							disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
						}
					} finally {
						if (!done) {
							if (input != null) {
								if (!input.equals("\n")) {
									if (input.equals(ChatCommon.ACCEPT_TRANSFER)) {
										accepted = true;
										transferReady = false;
									}
								} else {
									done = true;
									disconnect(ChatCommon.DOWNLOAD_S_CANCELLED, true);
								}
							}
						}
					}
				}
			} else {
				if (!transferReady) {
					String input = null;
					try {
						input = sIO.readBytes(socket, chatWindow.getClient().aes);
					} catch (IOException e) {
						done = true;
						if (!mCancelled) {
							ChatCommon.popError("Download from " + handle + " failed:" + System.getProperty("line.separator") + ((e.getMessage() != null) ? e.getMessage() : "Unspecified"), true);
							System.out.println("Download from " + handle + " failed:" + System.getProperty("line.separator") + ((e.getMessage() != null) ? e.getMessage() : "Unspecified"));
							chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), true);
							disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
						}
					} finally {
						if (!done) {
							if (mSize != 0) { dCalc.calc(0, mSize - mTotalSent); }
							if (input != null) {
								if (!input.equals("\n")) {
									String[] sMsg = input.split(ChatCommon.SPLIT_SEPARATOR);
									if (sMsg[0].equals(ChatCommon.DOWNLOAD_SETFILE) && sMsg.length > 1 && mSize != 0) {
										int index = -1;
										try {
											index = Integer.parseInt(sMsg[1]);
										} catch (NumberFormatException numE) {
											done = true;
											if (!mCancelled) {
												ChatCommon.popError("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host sent improperly formatted response.", true);
												System.out.println("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host sent improperly formatted response.");
												chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), true);
												disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
											}
										} finally {
											if (index >= 0 && index < mFiles.size()) {
												currentFileLoc = index;
												transferReady = true;
												Platform.runLater(new Runnable() {
													@Override
													public void run() {
														if (!mCancelled) {
															currentFile.setText(new File(mFiles.get(currentFileLoc)).getName());
														}
													}
												});
											} else if (!mCancelled) {
												done = true;
												//ChatCommon.popError("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host requested out of bounds file.", true);
												System.out.println("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host requested out of bounds file.");
												chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), true);
												disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
											}
										}
									} else if (sMsg[0].equals(ChatCommon.DOWNLOAD_SETFILE) && sMsg.length > 2 && mSize == 0) {
										int index = -1;
										long size = -1;
										try {
											index = Integer.parseInt(sMsg[1]);
											size = Long.parseLong(sMsg[2]);
										} catch (NumberFormatException numE) {
											done = true;
											if (!mCancelled) {
												ChatCommon.popError("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host sent improperly formatted response.", true);
												System.out.println("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host sent improperly formatted response.");
												chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), true);
												disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
											}
										} finally {
											if (index >= 0 && index < mFiles.size()) {
												if (size > 0) {
													currentFileLoc = index;
													mSize = size;
													transferReady = true;
													dCalc.setLastTime(System.currentTimeMillis());
													Platform.runLater(new Runnable() {
														@Override
														public void run() {
															if (!mCancelled) {
																currentFile.setText(new File(mFiles.get(currentFileLoc)).getName());
															}
														}
													});
												} else if (!mCancelled) {
													done = true;
													//ChatCommon.popError("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host sent improperly formatted respose.", true);
													System.out.println("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host sent improperly formatted respose.");
													chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), true);
													disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
												}
											} else if (!mCancelled) {
												done = true;
												//ChatCommon.popError("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host requested out of bounds file.", true);
												System.out.println("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host requested out of bounds file.");
												chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), true);
												disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
											}
										}
									} else if (!mCancelled) {
										done = true;
										ChatCommon.popError("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host sent improperly formatted respose.", true);
										System.out.println("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host sent improperly formatted respose.");
										chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), true);
										disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
									}
								} else {
									done = true;
									disconnect(ChatCommon.DOWNLOAD_S_CANCELLED, true);
								}
							}
						}
					}
				} else {
					//Send files
					if (currentFileLoc < mFiles.size() && currentFileLoc >= 0) {
						File currentFile = new File(mFiles.get(currentFileLoc));
						if (currentFile.exists()) {
							boolean errored = false;
							try {
								if (fileIn == null) {
									fileIn = new BufferedInputStream(new FileInputStream(new File(mFiles.get(currentFileLoc))));
								}
								
								int written = 0;
								if ((written = sIO.writeFile(socket, fileIn)) == -1) {
									fileIn.close();
									fileIn = null;
									transferReady = false;
									if (mTotalSent >= mSize) {
										disconnect(ChatCommon.DOWNLOAD_COMPLETE, true);
									}
								} else {
									mTotalSent += written;
									if (dCalc.calc(written, mSize - mTotalSent) || mTotalSent / (double) mSize >= 1) {
										Platform.runLater(new Runnable() {
											@Override
											public void run() {
												progressBar.setProgress(mTotalSent / (double) mSize);
											}
										});
									}
								}
								
							} catch (IOException e) {
								errored = true;
								done = true;
							} finally {
								if (errored || !transferReady) {
									if (fileIn != null) {
										try {
											fileIn.close();
										} catch (IOException closeE) {
											
										}
									}
									fileIn = null;
								}
							}
						} else if (!mCancelled) {
							done = true;
							ChatCommon.popError("Download from " + handle + " failed:" + System.getProperty("line.separator") + currentFile.getName() + " no longer exists!", true);
							System.out.println("Download from " + handle + " failed:" + System.getProperty("line.separator") + currentFile.getName() + " no longer exists!");
							chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), true);
							disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
						}
					} else if (!mCancelled) {
						done = true;
						ChatCommon.popError("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host requested out of bounds file.", true);
						System.out.println("Download from " + handle + " failed:" + System.getProperty("line.separator") + "Host requested out of bounds file.");
						chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_ERROR + ChatCommon.SEPARATOR + Integer.toString(rfKey)), true);
						disconnect(ChatCommon.DOWNLOAD_S_ERROR, true);
					}
				}
			}
		} else if (isExpired() && !mCancelled) {
			disconnect(ChatCommon.DOWNLOAD_S_TIMEDOUT, true);
			chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_R_TIMEDOUT + ChatCommon.SEPARATOR + Integer.toString(rfKey)), false);
		}
		
		return done;
	}

}
