package atChat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
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

public class ReceiveFile {
	private static final long TIMEOUT = 60000 * 17;
	
	private final HostConnection.Client mClient;
	private ArrayList<HostConnection.Client> sendTo;
	
	private boolean mCancelled = false;
	
	private final String saveDir;
	
	private final int sendFileKey;
	private final String clientKey;
	private int receiveFileKey;
	
	private final ChatMain chatWindow;
	
	private final List<String> mFiles;
	private final List<Integer> indices;
	private long[] tmpFileSizes;
	private long[] mSizes;
	
	private long totalDownloadSize = 0;
	private long currentProgress = 0;
	
	private int currentFileLoc = 0;
	
	private SocketChannel socket;
	private final int remotePort;
	private final SocketIO sIO;
	
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
	
	private boolean accepted = false;
	private boolean msgSent = false;
	private boolean connected = false;
	
	private BufferedOutputStream fileOut = null;
	
	private int numErrors = 0;
	
	private long startTime;
	
	//Client
	public ReceiveFile(int sendFileKey, String clientKey, ChatMain chatWindow, List<String> files, List<Integer> indices, long[] fileSizes, int remotePort, String saveDirectory) throws IOException {
		this.sendFileKey = sendFileKey;
		this.clientKey = clientKey;
		this.chatWindow = chatWindow;
		saveDir = saveDirectory;
		tmpFileSizes = fileSizes;
		
		mClient = null;
		sendTo = null;
		
		mFiles = files;
		this.indices = indices;
		
		socket = SocketChannel.open();
		socket.configureBlocking(false);
		sIO = new SocketIO(64 * 1024);
		this.remotePort = remotePort;
		
		startTime = System.currentTimeMillis();
	}
	
	//Host
	public ReceiveFile(int sendFileKey, ChatMain chatWindow, List<String> files, List<Integer> indices, long[] fileSizes, String saveDirectory, HostConnection.Client client) {
		this.sendFileKey = sendFileKey;
		this.chatWindow = chatWindow;
		saveDir = saveDirectory;
		tmpFileSizes = fileSizes;
		
		mClient = client;
		clientKey = client.getKey();
		sendTo = new ArrayList<HostConnection.Client>();
		sendTo.add(mClient);
		
		mFiles = files;
		this.indices = indices;
		
		sIO = new SocketIO(64 *1024);
		this.remotePort = 0;
		
		startTime = System.currentTimeMillis();
	}
	
	protected void setSocket(SocketChannel socket) {
		startTime = System.currentTimeMillis();
		this.socket = socket;
	}
	
	public String getClientKey() {
		return clientKey;
	}
	
	public boolean isExpired() {
		if (System.currentTimeMillis() - startTime >= TIMEOUT) {
			return true;
		} else { return false; }
	}
	
	protected void setReceiveKey(int key) {
		receiveFileKey = key;
	}
	
	public int getRFKey() {
		return receiveFileKey;
	}
	
	protected HostConnection.Client getClient() {
		return mClient;
	}
	
	public int getSendFileKey() {
		return sendFileKey;
	}
	
	public String getHandle() {
		return (mClient != null) ? mClient.cHandle : chatWindow.getClient().getHostHandle();
	}
	
	public boolean isConnected() throws IOException {
		if (!connected) {
			if (isConnecting()) {
				connected = socket.finishConnect();
				return connected;
			} else if (socket.isConnected()) {
				connected = true;
				return true;
			} else {
				return false;
			}
		} else { return true; }
	}
	
	public boolean isConnecting() {
		return socket.isConnectionPending();
	}
	
	protected void connectSocket() throws IOException {
		startTime = System.currentTimeMillis();
		socket.connect(new InetSocketAddress(ChatPreferences.rIPAddress, remotePort));
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
		
		Label typeLabel = new Label("Receive");
		typeLabel.setStyle("-fx-text-fill: rgb(93%, 93%, 93%); -fx-font-size: 11px;");
		
		toLabel = new Label((mClient != null) ? mClient.cHandle : chatWindow.getClient().getHostHandle());
		toLabel.setAlignment(Pos.CENTER);
		toLabel.setWrapText(false);
		toLabel.setEllipsisString("...");
		downloadBox.setVgrow(toLabel, Priority.NEVER);
		
		toLabel.setStyle("-fx-text-fill: rgb(32, 186, 255); -fx-font-size: 14px; -fx-font-weight: bold;");
		
		currentFile = new Label("Connecting");
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
		
		speed = new Label("Rate:  0B/s");
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
					disconnect(ChatCommon.DOWNLOAD_R_CANCELLED, false);
					if (mClient == null) {
						chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_CANCELLED + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), false);
					} else {
						chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_CANCELLED + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), sendTo, false, false, null);
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
		
		VBox bottomBox = new VBox();
		bottomBox.getChildren().addAll(eta, speed, progressBar);
		
		changeListener = new ChangeListener<Bounds>() {
			@Override
			public void changed(ObservableValue<? extends Bounds> observable, Bounds oldValue, Bounds newValue) {
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
	
	protected void disconnect(final String reason, boolean runLater) {
		if (!mCancelled) {
			mCancelled = true;
			
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException cDiscE) {
					System.out.println("Unable to close file transfer socket:" + System.getProperty("line.separator") + cDiscE.getMessage());
				}
			}
			
			if (mClient != null) { mClient.nullReceiveFile(this, runLater); }
			
			if (fileOut != null) {
				try {
					fileOut.close();
				} catch (IOException fileoutE) {
					
				}
			}
			
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					switch(reason) {
					case ChatCommon.DOWNLOAD_R_CANCELLED:
						currentFile.setText("Cancelled");
						eta.setText("ETA: Never");
						speed.setText("Speed: 0B/s");
						break;
					case ChatCommon.DOWNLOAD_COMPLETE:
						currentFile.setText("Complete");
						eta.setText("ETA: Done");
						speed.setText("Speed: 0B/s");
						break;
					case ChatCommon.DOWNLOAD_R_REFUSED:
						currentFile.setText("Refused");
						eta.setText("ETA: Never");
						speed.setText("Speed: 0B/s");
						break;
					case ChatCommon.DOWNLOAD_R_TIMEDOUT:
						currentFile.setText("Timed Out");
						eta.setText("ETA: Never");
						speed.setText("Speed: 0B/s");
						break;
					case ChatCommon.DOWNLOAD_R_ERROR:
						currentFile.setText("Error");
						eta.setText("ETA: Never");
						speed.setText("Speed: 0B/s");
					}
					cancelButton.setText("Dismiss");
				}
			});
		}
	}
	
	public boolean isCancelled() {
		return mCancelled;
	}
	
	protected boolean run() {
		boolean done = false;
		
		if (!mCancelled) {
			if (mClient == null) {
				done = clientReceiver(done);
			} else {
				done = hostReceiver(done);
			}
		} else {
			return true;
		}
		
		return done;
	}
	
	private boolean clientReceiver(boolean done) {
		//Client receiver
		if (!accepted) {
			if (!msgSent) {
				boolean errored = false;
				try {
					sIO.writeBytes(socket, ChatCommon.ACCEPT_TRANSFER + ChatCommon.SEPARATOR + Integer.toString(sendFileKey) + ":" + clientKey, chatWindow.getClient().aes);
				} catch (IOException ioE) {
					errored = true;
					numErrors ++;
					if (numErrors >= 9) {
						done = true;
						if (!mCancelled) {
							ChatCommon.popError("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + ioE.getMessage(), true);
							System.out.println("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + ioE.getMessage());
						}
					}
				} finally {
					if (!errored) {
						msgSent = true;
						numErrors = 0;
					} else if (done) {
						if (!mCancelled) {
							disconnect(ChatCommon.DOWNLOAD_R_ERROR, true);
							chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), false);
						}
					}
				}
			} else {
				String input = null;
				try {
					input = sIO.readBytes(socket, chatWindow.getClient().aes);
				} catch (IOException e) {
					done = true;
					if (!mCancelled) {
						ChatCommon.popError("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + e.getMessage(), true);
						System.out.println("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + e.getMessage());
					}
				} finally {
					if (!done) {
						if (input != null) {
							if (!input.equals("\n")) {
								if (input.equals(ChatCommon.ACCEPT_TRANSFER)) {
									msgSent = false;
									accepted = true;
								} else if (input.equals(ChatCommon.DOWNLOAD_R_REFUSED)) {
									done = true;
									disconnect(ChatCommon.DOWNLOAD_R_REFUSED, true);
								}
							} else {
								//disconnected
								done = true;
								disconnect(ChatCommon.DOWNLOAD_R_CANCELLED, true);
							}
						}
					} else {
						if (!mCancelled) {
							disconnect(ChatCommon.DOWNLOAD_R_ERROR, true);
							chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), false);
						}
					}
				}
			}
		} else {
			if (!msgSent) {
				boolean errored = false;
				try {
					if (currentFileLoc == 0) {
						totalDownloadSize = 0;
						mSizes = new long[indices.size()];
						for (int x = 0; x < mSizes.length; x ++) {
							mSizes[x] = tmpFileSizes[indices.get(x).intValue()];
							totalDownloadSize += mSizes[x];
						}
						if (totalDownloadSize <= 0) {
							done = true;
							errored = true;
							numErrors = 10;
						} else {
							sIO.writeBytes(socket, ChatCommon.DOWNLOAD_SETFILE + ChatCommon.SEPARATOR + Integer.toString(indices.get(currentFileLoc)) + ChatCommon.SEPARATOR + Integer.toString(receiveFileKey) + ChatCommon.SEPARATOR + Long.toString(totalDownloadSize), chatWindow.getClient().aes);
							dCalc.setLastTime(System.currentTimeMillis());
						}
					} else {
						sIO.writeBytes(socket, ChatCommon.DOWNLOAD_SETFILE + ChatCommon.SEPARATOR + Integer.toString(indices.get(currentFileLoc)), chatWindow.getClient().aes);
					}
				} catch (IOException ioE) {
					errored = true;
					numErrors ++;
					if (numErrors >= 9) {
						done = true;
						if (!mCancelled) {
							ChatCommon.popError("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + ioE.getMessage(), true);
							System.out.println("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + ioE.getMessage());
						}
					}
				} finally {
					if (!errored) {
						Platform.runLater(new Runnable() {
							@Override
							public void run() {
								if (!mCancelled) {
									currentFile.setText(new File(mFiles.get(currentFileLoc)).getName());
								}
							}
						});
						msgSent = true;
						numErrors = 0;
					} else if (done) {
						if (!mCancelled) {
							disconnect(ChatCommon.DOWNLOAD_R_ERROR, true);
							chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), false);
						}
					}
				}
			} else {
				if (currentFileLoc < mFiles.size() && currentFileLoc >= 0) {
					if (!mCancelled) {
						File saveFile = new File(saveDir, mFiles.get(currentFileLoc));
						boolean errored = false;
						try {
							if (fileOut == null) {
								saveFile.getParentFile().mkdirs();
								sIO.setFileWriter(mSizes[currentFileLoc]);
								fileOut = new BufferedOutputStream(new FileOutputStream(saveFile));
								
								if (sIO.readFile(socket, fileOut)) {
									currentProgress += mSizes[currentFileLoc];
									currentFileLoc ++;
									fileOut.close();
									fileOut = null;
									msgSent = false;
									if (currentFileLoc >= mFiles.size()) {
										currentFileLoc = mFiles.size() - 1;
										done = true;
										if (!mCancelled) {
											disconnect(ChatCommon.DOWNLOAD_COMPLETE, true);
											chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_COMPLETE + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), false);
										}
										Platform.runLater(new Runnable() {
											@Override
											public void run() {
												progressBar.setProgress(1.0);
											}
										});
									} else {
										if (dCalc.calc(sIO.getLastWriteAmount(), totalDownloadSize - currentProgress)) {
											Platform.runLater(new Runnable() {
												@Override
												public void run() {
													progressBar.setProgress(currentProgress / (double) totalDownloadSize);
												}
											});
										}
									}
								} else {
									if (dCalc.calc(sIO.getLastWriteAmount(), totalDownloadSize - (currentProgress + sIO.getCurrentLength()))) {
										Platform.runLater(new Runnable() {
											@Override
											public void run() {
												progressBar.setProgress((currentProgress + sIO.getCurrentLength()) / (double) totalDownloadSize);
											}
										});
									}
								}
								
							} else if (saveFile.exists()) {
								if (sIO.readFile(socket, fileOut)) {
									currentProgress += mSizes[currentFileLoc];
									currentFileLoc ++;
									fileOut.close();
									fileOut = null;
									msgSent = false;
									if (currentFileLoc >= mFiles.size()) {
										currentFileLoc = mFiles.size() - 1;
										done = true;
										if (!mCancelled) {
											disconnect(ChatCommon.DOWNLOAD_COMPLETE, true);
											chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_COMPLETE + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), false);
										}
										Platform.runLater(new Runnable() {
											@Override
											public void run() {
												progressBar.setProgress(1.0);
											}
										});
									} else {
										if (dCalc.calc(sIO.getLastWriteAmount(), totalDownloadSize - currentProgress)) {
											Platform.runLater(new Runnable() {
												@Override
												public void run() {
													progressBar.setProgress(currentProgress / (double) totalDownloadSize);
												}
											});
										}
									}
								} else {
									if (dCalc.calc(sIO.getLastWriteAmount(), totalDownloadSize - (currentProgress + sIO.getCurrentLength()))) {
										Platform.runLater(new Runnable() {
											@Override
											public void run() {
												progressBar.setProgress((currentProgress + sIO.getCurrentLength()) / (double) totalDownloadSize);
											}
										});
									}
								}
							} else {
								ChatCommon.popError("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + saveFile.getName() + " was removed during the write process.", true);
								System.out.println("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + saveFile.getName() + " was removed during the write process.");
								disconnect(ChatCommon.DOWNLOAD_R_ERROR, true);
								chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), false);
							}
						} catch (IOException outE) {
							errored = true;
							done = true;
							if (!mCancelled) {
								disconnect(ChatCommon.DOWNLOAD_R_ERROR, true);
								chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), false);
								ChatCommon.popError("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + outE.getMessage(), true);
								System.out.println("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + outE.getMessage());
							}
						} finally {
							if (errored) {
								if (fileOut != null) {
									try {
										fileOut.close();
									} catch (IOException clsoeE) {
										
									}
									fileOut = null;
								}
							}
						}
					}
				} else {
					done = true;
					if (!mCancelled) {
						disconnect(ChatCommon.DOWNLOAD_R_ERROR, true);
						chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), false);
						ChatCommon.popError("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + "File index out of range.", true);
						System.out.println("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + "File index out of range");
					}
				}
			}
		}
		
		return done;
	}
	
	private boolean hostReceiver(boolean done) {
		if (!msgSent) {
			boolean errored = false;
			try {
				if (currentFileLoc == 0) {
					totalDownloadSize = 0;
					mSizes = new long[indices.size()];
					for (int x = 0; x < mSizes.length; x ++) {
						mSizes[x] = tmpFileSizes[indices.get(x).intValue()];
						totalDownloadSize += mSizes[x];
					}
					if (totalDownloadSize <= 0) {
						done = true;
						errored = true;
						numErrors = 10;
					} else {
						sIO.writeBytes(socket, ChatCommon.DOWNLOAD_SETFILE + ChatCommon.SEPARATOR + Integer.toString(indices.get(currentFileLoc)) + ChatCommon.SEPARATOR + Long.toString(totalDownloadSize), chatWindow.getHost().aes);
						dCalc.setLastTime(System.currentTimeMillis());
					}
				} else {
					sIO.writeBytes(socket, ChatCommon.DOWNLOAD_SETFILE + ChatCommon.SEPARATOR + Integer.toString(indices.get(currentFileLoc)), chatWindow.getHost().aes);
				}
			} catch (IOException e) {
				errored = true;
				numErrors ++;
				if (numErrors >= 9) {
					done = true;
					if (!mCancelled) {
						ChatCommon.popError("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + e.getMessage(), true);
						System.out.println("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + e.getMessage());
					}
				}
			} finally {
				if (!errored) {
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							if (!mCancelled) {
								currentFile.setText(new File(mFiles.get(currentFileLoc)).getName());
							}
						}
					});
					msgSent = true;
					numErrors = 0;
				} else if (done && !mCancelled) {
					disconnect(ChatCommon.DOWNLOAD_R_ERROR, true);
					chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), sendTo, false, false, null);
				}
			}
		} else {
			File saveFile = new File(saveDir, mFiles.get(currentFileLoc));
			
			if (!mCancelled) {
				boolean errored = false;
				try {
					if (fileOut == null) {
						saveFile.getParentFile().mkdirs();
						sIO.setFileWriter(mSizes[currentFileLoc]);
						fileOut = new BufferedOutputStream(new FileOutputStream(saveFile));
						
						if (sIO.readFile(socket, fileOut)) {
							currentProgress += mSizes[currentFileLoc];
							currentFileLoc ++;
							fileOut.close();
							fileOut = null;
							msgSent = false;
							if (currentFileLoc >= mFiles.size()) {
								currentFileLoc = mFiles.size() - 1;
								done = true;
								if (!mCancelled) {
									disconnect(ChatCommon.DOWNLOAD_COMPLETE, true);
									chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_COMPLETE + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), sendTo, false, false, null);
								}
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										progressBar.setProgress(1.0);
									}
								});
							} else {
								if (dCalc.calc(sIO.getLastWriteAmount(), totalDownloadSize - currentProgress)) {
									Platform.runLater(new Runnable() {
										@Override
										public void run() {
											progressBar.setProgress(currentProgress / (double) totalDownloadSize);
										}
									});
								}
							}
						} else {
							if (dCalc.calc(sIO.getLastWriteAmount(), totalDownloadSize - (currentProgress + sIO.getCurrentLength()))) {
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										progressBar.setProgress((currentProgress + sIO.getCurrentLength()) / (double) totalDownloadSize);
									}
								});
							}
						}
						
					} else if (saveFile.exists()) {
						if (sIO.readFile(socket, fileOut)) {
							currentProgress += mSizes[currentFileLoc];
							currentFileLoc ++;
							fileOut.close();
							fileOut = null;
							msgSent = false;
							if (currentFileLoc >= mFiles.size()) {
								currentFileLoc = mFiles.size() - 1;
								done = true;
								if (!mCancelled) {
									disconnect(ChatCommon.DOWNLOAD_COMPLETE, true);
									chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_COMPLETE + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), sendTo, false, false, null);
								}
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										progressBar.setProgress(1.0);
									}
								});
							} else {
								if (dCalc.calc(sIO.getLastWriteAmount(), totalDownloadSize - currentProgress)) {
									Platform.runLater(new Runnable() {
										@Override
										public void run() {
											progressBar.setProgress(currentProgress / (double) totalDownloadSize);
										}
									});
								}
							}
						} else {
							if (dCalc.calc(sIO.getLastWriteAmount(), totalDownloadSize - (currentProgress + sIO.getCurrentLength()))) {
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										progressBar.setProgress((currentProgress + sIO.getCurrentLength()) / (double) totalDownloadSize);
									}
								});
							}
						}
					} else {
						done = true;
						ChatCommon.popError("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + saveFile.getName() + " was removed during the write process.", true);
						System.out.println("Download from " + getHandle() + " failed:" + System.getProperty("line.separator") + saveFile.getName() + " was removed during the write process.");
						disconnect(ChatCommon.DOWNLOAD_R_ERROR, true);
						chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_ERROR + ChatCommon.SEPARATOR + Integer.toString(sendFileKey)), sendTo, false, false, null);
					}
				} catch (IOException e) {
					errored = true;
				} finally {
					if (errored && fileOut != null) {
						try {
							fileOut.close();
						} catch (IOException fOutE) {
							
						}
					}
				}
			}
		}
		
		return done;
	}

}
