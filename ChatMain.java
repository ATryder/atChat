package atChat;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Callback;

import com.sun.javafx.scene.control.skin.ScrollPaneSkin;
import com.sun.javafx.scene.control.skin.TextAreaSkin;

public class ChatMain implements Initializable {
	private Stage primaryStage;
	
	@FXML
	private MenuItem mFileHost;
	@FXML
	private MenuItem mFileConnect;
	@FXML
	private MenuItem mFileDisconnect;
	
	@FXML
	protected VBox mChatText;
	@FXML
	private TextArea mMessageText;
	@FXML
	private ScrollPane mChatScroll;
	
	@FXML
	private Button mSendButton;
	@FXML
	private Button mCancelButton;
	
	@FXML
	private Label mNumConnectionsLabel;
	@FXML
	private CheckBox mAutoAcceptBox;
	@FXML
	private CheckBox mListeningButton;
	
	@FXML
	private ListView<String> mConnectionList;
	private ObservableList<String> mParticipants = FXCollections.observableArrayList();
	
	@FXML
	private VBox mDownloadBox;
	@FXML
	private ScrollPane mDownloadScroll;
	
	private boolean mConnected = false;
	private boolean mHosting = false;
	
	private HostConnection mHost;
	private ClientConnection mClient;
	
	@FXML
	private TitledPane connectionsTitledPane;
	@FXML
	private TitledPane downloadsTitledPane;
	
	protected void prepare(Stage primaryStage) {
		this.primaryStage = primaryStage;
		
		primaryStage.setOnShown(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent event) {
				connectionsTitledPane.setExpanded(true);
				
				ScrollPaneSkin skin = (ScrollPaneSkin)mDownloadScroll.getSkin();
				for (Node node : skin.getChildren()) {
					if (node instanceof StackPane) {
						((StackPane)node).setCache(false);
					}
				}
				
				skin = (ScrollPaneSkin)mChatScroll.getSkin();
				for (Node node : skin.getChildren()) {
					if (node instanceof StackPane) {
						((StackPane)node).setCache(false);
					}
				}
				
				/*TitledPaneSkin tSkin = (TitledPaneSkin)connectionsTitledPane.getSkin();
				connectionsTitledPane.setCache(false);
				for (Node node : tSkin.getChildren()) {
					node.setCache(false);
				}*/
				
				/*tSkin = (TitledPaneSkin)downloadsTitledPane.getSkin();
				downloadsTitledPane.setCache(false);
				for (Node node : tSkin.getChildren()) {
					node.setCache(false);
				}*/
				
				TextAreaSkin taSkin = (TextAreaSkin)mMessageText.getSkin();
				for (Node node : taSkin.getChildren()) {
					if (node instanceof ScrollPane) {
						skin = (ScrollPaneSkin)((ScrollPane)node).getSkin();
						for (Node n : skin.getChildren()) {
							if (n instanceof StackPane) {
								n.setCache(false);
							}
						}
					}
				}
			}
		});
		
		this.primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent event) {
				if (mConnected) {
					if (mHosting) {
						if (mHost.isConnected() && !mHost.isDisconnecting()) {
							setConnectButtons(false, mHosting);
							mHost.startDisconnect(false);
						}
					} else if (mClient.isConnected()){
						setConnectButtons(false, false);
						mClient.startDisconnect();
					}
				}
				
				ChatCommon.closeClips();
				
				ChatPreferences.writePrefs();
			}
		});
	}
	
	@Override
	public void initialize(URL url, ResourceBundle resources) {
		
		ChatCommon.setMain(this);
		
		mSendButton.setDisable(true);
		mCancelButton.setDisable(true);
		mMessageText.setDisable(true);
		mFileDisconnect.setDisable(true);
		mSendButton.setGraphic(new ImageView(new Image("/atChat/resources/images/checkButton.png")));
		mCancelButton.setGraphic(new ImageView(new Image("/atChat/resources/images/xButton.png")));
		
		mConnectionList.setItems(mParticipants);
		mConnectionList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		
		EventHandler<ActionEvent> sendFileHandler = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				if (mHosting) {
					HostConnection.Client client = mHost.getSelectedClient(mConnectionList.getSelectionModel().getSelectedIndex());
					if (client != null) {
						if (!client.atMaxSend()) {
							SendFile sf = new SendFile(client, getMainThread());
							VBox downloadBox = sf.createDownloadBox(mDownloadBox, mDownloadScroll);
							mDownloadBox.getChildren().add(downloadBox);
							sf.popChooser(false);
							if (!sf.filesWereSelected()) {
								removeDownloadBox(downloadBox, sf.getDownloadBoxListener(), sf.getDownloadBoxParentListener());
								client.nullSendFile(sf, false);
							}
						} else {
							ChatCommon.popError("You have reached the maximum number of file transfers to this client.", false);
						}
					}
				} else {
					//for client side
					if (!mClient.getFileSender().atMaxSenders()) {
						SendFile sf = null;
						try {
							sf = new SendFile(getMainThread());
						} catch (IOException sfE) {
							ChatCommon.popError("Unable to create file transfer socket:" + System.getProperty("line.separator") + sfE.getMessage(), false);
						} finally {
							if (sf != null) {
								VBox downloadBox = sf.createDownloadBox(mDownloadBox, mDownloadScroll);
								mDownloadBox.getChildren().add(downloadBox);
								sf.popChooser(false);
								if (!sf.filesWereSelected()) {
									removeDownloadBox(downloadBox, sf.getDownloadBoxListener(), sf.getDownloadBoxParentListener());
								}
							}
						}
					} else {
						ChatCommon.popError("You have reached the maximum number of outgoing file transfers.", false);
					}
				}
			}
		};
		
		EventHandler<ActionEvent> sendDirHandler = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				if (mHosting) {
					HostConnection.Client client = mHost.getSelectedClient(mConnectionList.getSelectionModel().getSelectedIndex());
					if (client != null) {
						if (!client.atMaxSend()) {
							SendFile sf = new SendFile(client, getMainThread());
							VBox downloadBox = sf.createDownloadBox(mDownloadBox, mDownloadScroll);
							mDownloadBox.getChildren().add(downloadBox);
							sf.popChooser(true);
							if (!sf.filesWereSelected()) {
								removeDownloadBox(downloadBox, sf.getDownloadBoxListener(), sf.getDownloadBoxParentListener());
								client.nullSendFile(sf, false);
							}
						} else {
							ChatCommon.popError("You have reached the maximum number of file transfers to this client.", false);
						}
					}
				} else {
					//for client side
					if (!mClient.getFileSender().atMaxSenders()) {
						SendFile sf = null;
						try {
							sf = new SendFile(getMainThread());
						} catch (IOException sfE) {
							ChatCommon.popError("Unable to create file transfer socket:" + System.getProperty("line.separator") + sfE.getMessage(), false);
						} finally {
							if (sf != null) {
								VBox downloadBox = sf.createDownloadBox(mDownloadBox, mDownloadScroll);
								mDownloadBox.getChildren().add(downloadBox);
								sf.popChooser(true);
								if (!sf.filesWereSelected()) {
									removeDownloadBox(downloadBox, sf.getDownloadBoxListener(), sf.getDownloadBoxParentListener());
								}
							}
						}
					} else {
						ChatCommon.popError("You have reached the maximum number of outgoing file transfers.", false);
					}
				}
			}
		};
		
		EventHandler<ActionEvent> disconnectClientHandler = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				HostConnection.Client client = mHost.getSelectedClient(mConnectionList.getSelectionModel().getSelectedIndex());
				
				client.setDisconnecting(true);
				ArrayList<HostConnection.Client> disClient = new ArrayList<HostConnection.Client>();
				disClient.add(client);
				mHost.addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT), disClient, false, false, null);
				
				ArrayList<HostConnection.Client> sendTo = new ArrayList<HostConnection.Client>();
				for (HostConnection.Client cl : mHost.getClients()) {
					if (!cl.equals(client)) {
						sendTo.add(cl);
					}
				}
				mHost.addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DISCONNECT + ChatCommon.SEPARATOR + client.getKey()), sendTo, false, false, client);
			}
		};
		
		ContextMenu listContext = new ContextMenu();
		ContextMenu listClientContext = new ContextMenu();
		
		MenuItem sendFileItem = new MenuItem("Send Files");
		ImageView fileImage = new ImageView(new Image("/atChat/resources/images/file.png"));
		fileImage.setViewport(new Rectangle2D(0, 0, 18, 18));
		sendFileItem.setGraphic(fileImage);
		sendFileItem.setOnAction(sendFileHandler);
		
		MenuItem sendDirItem = new MenuItem("Send Directory");
		ImageView directoryImage = new ImageView(new Image("/atChat/resources/images/folder.png"));
		directoryImage.setViewport(new Rectangle2D(0, 0, 18, 18));
		sendDirItem.setGraphic(directoryImage);
		sendDirItem.setOnAction(sendDirHandler);
		
		listClientContext.getItems().addAll(sendFileItem, sendDirItem);
		
		sendFileItem = new MenuItem("Send Files");
		fileImage = new ImageView(new Image("/atChat/resources/images/file.png"));
		fileImage.setViewport(new Rectangle2D(0, 0, 18, 18));
		sendFileItem.setGraphic(fileImage);
		sendFileItem.setOnAction(sendFileHandler);
		
		sendDirItem = new MenuItem("Send Directory");
		directoryImage = new ImageView(new Image("/atChat/resources/images/folder.png"));
		directoryImage.setViewport(new Rectangle2D(0, 0, 18, 18));
		sendDirItem.setGraphic(directoryImage);
		sendDirItem.setOnAction(sendDirHandler);
		
		MenuItem disconnectClient = new MenuItem("Disconnect");
		ImageView disconnectImage = new ImageView(new Image("/atChat/resources/images/disconnect_client.png"));
		disconnectImage.setViewport(new Rectangle2D(0, 0, 18, 18));
		disconnectClient.setGraphic(disconnectImage);
		disconnectClient.setOnAction(disconnectClientHandler);
		
		listContext.getItems().addAll(sendFileItem, sendDirItem, disconnectClient);
		
		mConnectionList.setCellFactory(ContextCellFactory.<String>factory(listContext, listClientContext, this));
		
		mMessageText.setOnKeyReleased(new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == KeyCode.ENTER && event.isShiftDown()) {
					clickSend(null);
					event.consume();
				}
			}
		});
		
		ChatPreferences.prefPath = new File(System.getProperty("user.home"), "@Chat.pref");
		ChatPreferences.lastFilePath = new File(System.getProperty("user.home"));
		ChatPreferences.lastDirPath = new File(System.getProperty("user.home"));
		ChatPreferences.lastDownloadPath = new File(System.getProperty("user.home"), "Downloads");
		if (!ChatPreferences.lastDownloadPath.exists()) {
			ChatPreferences.lastDownloadPath = new File(System.getProperty("user.home"));
		}
		ChatPreferences.setLocalDefault();
		ChatPreferences.setRemoteDefault();
		ChatPreferences.readPrefs();
		
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				mAutoAcceptBox.setSelected(ChatPreferences.autoAccept);
				mListeningButton.setSelected(ChatPreferences.continueListening);
			}
		});
		
		mChatText.heightProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				if (newValue.doubleValue() > mChatScroll.getHeight()) {
					mChatScroll.setVvalue(mChatScroll.getVmax());
				}
			}
		});
		
		ChatCommon.loadAudioClips();
	}
	
	protected VBox getDownloadBox() {
		return mDownloadBox;
	}
	
	protected ScrollPane getDownloadScroll() {
		return mDownloadScroll;
	}
	
	protected void removeDownloadBox(VBox item, ChangeListener<Bounds> changeListener, ChangeListener<Number> parentHeight) {
		mDownloadBox.getChildren().remove(item);
		mDownloadScroll.viewportBoundsProperty().removeListener(changeListener);
		mDownloadBox.heightProperty().removeListener(parentHeight);
	}
	
	protected ChatMain getMainThread() {
		return this;
	}
	
	protected void setClientClass(ClientConnection clientClass) {
		mClient = clientClass;
	}
	
	protected void addParticipant(int loc, String handle) {
		if (loc >= 0 && loc < mParticipants.size()) {
			mParticipants.add(loc, handle);
		} else if (loc >= mParticipants.size()) { mParticipants.add(handle); }
		mNumConnectionsLabel.setText(Integer.toString(mParticipants.size()));
		mConnectionList.getSelectionModel().clearSelection();
	}
	
	protected void removeParticipant(int loc) {
		if (loc >= 0 && loc < mParticipants.size()) {
			mParticipants.remove(loc);
			mNumConnectionsLabel.setText(Integer.toString(mParticipants.size()));
			
			mConnectionList.getSelectionModel().clearSelection();
		}
	}
	
	protected void clearParticipants() {
		mParticipants.clear();
		mNumConnectionsLabel.setText("0");
	}
	
	protected boolean isConnected() {
		return mConnected;
	}
	
	protected boolean isHosting() {
		return mHosting;
	}
	
	protected HostConnection getHost() {
		return mHost;
	}
	
	protected ClientConnection getClient() {
		return mClient;
	}
	
	protected void startHosting(String password) {
		mConnected = true;
		mHosting = true;
		mHost = new HostConnection(this, password);
	}
	
	protected void startClient(String password) {
		mConnected = true;
		mHosting = false;
		
		try {
			final Stage dialogWin = new Stage(StageStyle.DECORATED);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/atChat/resources/fxml/atConnectingProgress.fxml"));
			AnchorPane progRoot = (AnchorPane) loader.load();
			ConnectingProgress diagCont = (ConnectingProgress) loader.getController();
			diagCont.setStage(dialogWin, this, password);
			dialogWin.getIcons().addAll(new Image("/atChat/resources/images/@Chat_Icon_16_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_24_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_32_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_48_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_64_32bit.png"));
			//dialogWin.setResizable(false);
			dialogWin.setTitle("Connecting");
			Scene scene = new Scene(progRoot);
			scene.getStylesheets().clear();
			scene.getStylesheets().add(Main.class.getResource("/atChat/resources/fxml/atChatTheme.css").toExternalForm());
			dialogWin.setScene(scene);
			ChatCommon.centerWindow(dialogWin, progRoot, primaryStage);
			dialogWin.initOwner(primaryStage);
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
			Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Unable to open connection progress window:" + System.getProperty("line.separator") + e.getMessage(), e);
			System.out.println("Unable to open connection progress window:" + System.getProperty("line.separator") + e.getMessage());
		}
	}
	
	protected Stage getStage() {
		return primaryStage;
	}
	
	@FXML protected void autoAcceptAction(ActionEvent event) {
		ChatPreferences.autoAccept = mAutoAcceptBox.isSelected();
	}
	
	@FXML protected void listenButtonClick(ActionEvent event) {
		ChatPreferences.continueListening = mListeningButton.isSelected();
	}
	
	protected void setConnectButtons(boolean connected, boolean hosting) {
		if (connected) {
			mMessageText.setDisable(false);
			mFileHost.setDisable(true);
			mFileConnect.setDisable(true);
			mFileDisconnect.setDisable(false);
			mSendButton.setDisable(false);
			mCancelButton.setDisable(false);
			if (hosting) {
				mListeningButton.setDisable(false);
			} else {
				mAutoAcceptBox.setDisable(true);
				mListeningButton.setDisable(true);
			}
		} else {
			mMessageText.setDisable(true);
			mFileHost.setDisable(false);
			mFileConnect.setDisable(false);
			mFileDisconnect.setDisable(true);
			mSendButton.setDisable(true);
			mCancelButton.setDisable(true);
		}
	}
	
	@FXML protected void fileHost(ActionEvent event) {
		try {
			final Stage dialogWin = new Stage(StageStyle.DECORATED);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/atChat/resources/fxml/chatHost.fxml"));
			AnchorPane hostRoot = (AnchorPane) loader.load();
			ChatHost diagCont = (ChatHost) loader.getController();
			diagCont.setStage(dialogWin, this);
			dialogWin.getIcons().addAll(new Image("/atChat/resources/images/@Chat_Icon_16_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_24_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_32_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_48_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_64_32bit.png"));
			//dialogWin.setResizable(false);
			dialogWin.setTitle("Host");
			Scene scene = new Scene(hostRoot);
			scene.getStylesheets().clear();
			scene.getStylesheets().add(Main.class.getResource("/atChat/resources/fxml/atChatTheme.css").toExternalForm());
			dialogWin.setScene(scene);
			ChatCommon.centerWindow(dialogWin, hostRoot, primaryStage);
			dialogWin.initOwner(primaryStage);
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
			Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Unable to open hosting options window:" + System.getProperty("line.separator") + e.getMessage(), e);
			System.out.println("Unable to open hosting options window:" + System.getProperty("line.separator") + e.getMessage());
		}
	}
	
	@FXML protected void fileConnect(ActionEvent event) {
		try {
			final Stage dialogWin = new Stage(StageStyle.DECORATED);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/atChat/resources/fxml/chatConnect.fxml"));
			AnchorPane clientRoot = (AnchorPane) loader.load();
			ChatConnectTo diagCont = (ChatConnectTo) loader.getController();
			diagCont.setStage(dialogWin, this);
			dialogWin.getIcons().addAll(new Image("/atChat/resources/images/@Chat_Icon_16_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_24_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_32_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_48_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_64_32bit.png"));
			//dialogWin.setResizable(false);
			dialogWin.setTitle("Connect");
			Scene scene = new Scene(clientRoot);
			scene.getStylesheets().clear();
			scene.getStylesheets().add(Main.class.getResource("/atChat/resources/fxml/atChatTheme.css").toExternalForm());
			dialogWin.setScene(scene);
			ChatCommon.centerWindow(dialogWin, clientRoot, primaryStage);
			dialogWin.initOwner(primaryStage);
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
			Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Unable to open client options window:" + System.getProperty("line.separator") + e.getMessage(), e);
			System.out.println("Unable to open client options window:" + System.getProperty("line.separator") + e.getMessage());
		}
	}
	
	@FXML protected void fileDisconnect(ActionEvent event) {
		setConnectButtons(false, mHosting);
		
		if (mHosting) {
			mHost.startDisconnect(false);
		} else {
			mClient.startDisconnect();
		}
	}
	
	@FXML protected void fileClose(ActionEvent event) {
		if (mConnected) {
			if (mHosting) {
				if (mHost.isConnected() && !mHost.isDisconnecting()) {
					setConnectButtons(false, mHosting);
					mHost.startDisconnect(false);
				}
			} else if (mClient.isConnected()){
				setConnectButtons(false, false);
				mClient.startDisconnect();
			}
		}
		
		ChatCommon.closeClips();
		
		ChatPreferences.writePrefs();
		
		primaryStage.close();
	}
	
	@FXML protected void editClear(ActionEvent event) {
		for (Node n : mChatText.getChildren()) {
			if (n.getOnMouseClicked() != null) {
				n.removeEventHandler(MouseEvent.MOUSE_CLICKED, n.getOnMouseClicked());
			}
			if (n.getOnMouseEntered() != null) {
				n.removeEventHandler(MouseEvent.MOUSE_ENTERED, n.getOnMouseEntered());
			}
			if (n.getOnMouseExited() != null) {
				n.removeEventHandler(MouseEvent.MOUSE_EXITED, n.getOnMouseExited());
			}
		}
		mChatText.getChildren().clear();
	}
	
	@FXML protected void editCopy(ActionEvent event) {
		String toCopy = "";
		int index = 1;
		for (Node n : mChatText.getChildren()) {
			toCopy += ((Label) n).getText();
			if (index % 2 != 0) {
				toCopy += System.getProperty("line.separator");
			}
			index ++;
		}
		if (toCopy.length() > 0) {
			toCopy = (toCopy.length() > 2) ? toCopy.substring(0, toCopy.length() - 2) : toCopy;
			StringSelection str = new StringSelection(toCopy);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(str, null);
		}
	}
	
	@FXML protected void helpHelp(ActionEvent event) {
		try {
			final Stage dialogWin = new Stage(StageStyle.DECORATED);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/atChat/resources/fxml/atHelp.fxml"));
			AnchorPane helpRoot = (AnchorPane) loader.load();
			HelpWindow diagCont = (HelpWindow) loader.getController();
			diagCont.initWindow(dialogWin);
			dialogWin.getIcons().addAll(new Image("/atChat/resources/images/@Chat_Icon_16_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_24_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_32_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_48_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_64_32bit.png"));
			dialogWin.setResizable(true);
			dialogWin.setTitle("@Chat Help");
			Scene scene = new Scene(helpRoot);
			scene.getStylesheets().clear();
			scene.getStylesheets().add(Main.class.getResource("/atChat/resources/fxml/atChatTheme.css").toExternalForm());
			dialogWin.setScene(scene);
			ChatCommon.centerWindow(dialogWin, helpRoot, primaryStage);
			dialogWin.show();
		} catch (IOException e) {
			Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Unable to open help window:" + System.getProperty("line.separator") + e.getMessage(), e);
			System.out.println("Unable to open help window:" + System.getProperty("line.separator") + e.getMessage());
		}
	}
	
	@FXML protected void helpAbout(ActionEvent event) {
		try {
			final Stage dialogWin = new Stage(StageStyle.DECORATED);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/atChat/resources/fxml/atAbout.fxml"));
			AnchorPane aboutRoot = (AnchorPane) loader.load();
			AboutWindow diagCont = (AboutWindow) loader.getController();
			diagCont.initWindow(dialogWin);
			dialogWin.getIcons().addAll(new Image("/atChat/resources/images/@Chat_Icon_16_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_24_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_32_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_48_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_64_32bit.png"));
			//dialogWin.setResizable(false);
			dialogWin.setTitle("About @Chat v" + String.format("%.1f", ChatPreferences.VERSION));
			Scene scene = new Scene(aboutRoot);
			scene.getStylesheets().clear();
			scene.getStylesheets().add(Main.class.getResource("/atChat/resources/fxml/atChatTheme.css").toExternalForm());
			dialogWin.setScene(scene);
			ChatCommon.centerWindow(dialogWin, aboutRoot, primaryStage);
			dialogWin.initOwner(primaryStage);
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
			Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Unable to open about window:" + System.getProperty("line.separator") + e.getMessage(), e);
			System.out.println("Unable to open about window:" + System.getProperty("line.separator") + e.getMessage());
		}
	}
	
	@FXML protected void clickSend(ActionEvent event) {
		if (mHosting) {
			if (Integer.parseInt(mNumConnectionsLabel.getText()) > 0) {
				String msg = mMessageText.getText();
				if (ChatCommon.isSystemMessage(msg)) {
					msg = ChatCommon.unFormatSystemMessage(msg);
				}
				if (msg.length() > 0) {
					msg = msg.length() <= 2 ? msg + ((msg.length() == 1) ? "  " : " ") : msg;
					mHost.addMessageToQueue(msg, null, false, true, null);
				}
			}
		} else if (mClient.isConnected()) {
			String msg = mMessageText.getText();
			if (ChatCommon.isSystemMessage(msg)) {
				msg = ChatCommon.unFormatSystemMessage(msg);
			}
			if (msg.length() > 0) {
				msg = msg.length() <= 2 ? msg + ((msg.length() == 1) ? "  " : " ") : msg;
				mClient.sendMessage(msg, false);
			}
		}
		mMessageText.setText("");
	}
	
	@FXML protected void clickCancel(ActionEvent event) {
		mMessageText.setText("");
	}
	
	protected void setDisconnected() {
		Platform.runLater(new SetDisconnected());
	}
	
	protected class SetDisconnected implements Runnable {
		@Override
		public void run() {
			setConnectButtons(false, mHosting);
			
			mConnected = false;
			mHosting = false;
			mHost = null;
			mClient = null;
		}
	}
	
	private class ParticipantCell<String> extends ListCell<String> {
		private final ContextMenu contextMenu;
		private final ContextMenu clientContextMenu;
		private final ChatMain chatWindow;
		
		public ParticipantCell(ContextMenu contextMenu, ContextMenu clientContextMenu, ChatMain chatWindow) {
			this.contextMenu = contextMenu;
			this.clientContextMenu = clientContextMenu;
			this.chatWindow = chatWindow;
		}
		
		@Override
		public void updateSelected(boolean selected) {
			super.updateSelected(selected);
			updateItem(getItem(), false);
		}
		
		@Override
		protected void updateItem(String item, boolean empty) {
			super.updateItem(item, empty);
			
			if (item != null) {
				if (chatWindow.mHosting) {
					setContextMenu(contextMenu);
				} else if (getIndex() == 0) {
					setContextMenu(clientContextMenu);
				}
				
				HBox h = new HBox(7);
				h.setAlignment(Pos.CENTER_LEFT);
				
				ImageView avatar = new ImageView();
				if (chatWindow.mHosting || getIndex() > 0) {
					avatar.setImage(new Image("/atChat/resources/images/client_avatar.png"));
				} else {
					avatar.setImage(new Image("/atChat/resources/images/host_avatar.png"));
				}
				avatar.setViewport(new Rectangle2D(0, 0, 18, 18));
				
				Label label = new Label(item.toString());
				java.lang.String style = (!isSelected()) ? "-fx-font-family: Arial;"
						+ "-fx-text-fill: rgb(93%, 93%, 93%);"
						+ "-fx-font-size: 12px;" : "-fx-font-family: Arial;"
						+ "-fx-text-fill: black;"
						+ "-fx-font-size: 12px;";
				label.setStyle(style);
				h.getChildren().addAll(avatar, label);
				setGraphic(h);
				
			} else {
				setText("");
				setGraphic(null);
			}
		}
	}
	
	private static class ContextCellFactory<String> extends ListCell<String> {
		
		public static <String> Callback<ListView<String>, ListCell<String>> factory(final ContextMenu contextMenu, final ContextMenu clientContextMenu, final ChatMain chatWindow) {
			return new Callback<ListView<String>, ListCell<String>>() {
				@Override
				public ListCell<String> call(ListView<String> listView) {
					ListCell<String> cell = new ChatMain().new ParticipantCell<String>(contextMenu, clientContextMenu, chatWindow);
					return cell;
				}
			};
		}
	}

}
