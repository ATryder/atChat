package atChat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Callback;

public class AcceptDirectoryTransfer {
	
	@FXML
	private Label mLabel;
	@FXML
	private TreeView<String> mTreeView;
	@FXML
	private Label saveInLabel;
	
	private LinkedHashMap<String, FileSelector> mSelectors;
	private long[] mSizes;
	
	private Stage thisStage;
	private ChatMain chatWindow;
	
	@FXML
	private ImageView checkedImageView;
	@FXML
	private ImageView unCheckedImageView;
	@FXML
	private ImageView overwriteImageView;
	@FXML
	private ImageView folderImageView;
	
	@FXML
	private Button mAcceptButton;
	@FXML Button mDeclineButton;
	
	private String unChecked = "/atChat/resources/images/checkBox.png";
	private String checked = "/atChat/resources/images/checkBox_checked.png";
	private String overwrite = "/atChat/resources/images/file_overwrite.png";
	
	private String lastPath;
	
	private int key;
	private String clientKey;
	private int remotePort;
	
	private HostConnection.Client client;
	private String handle = "";
	
	protected void initWindow(TreeItem<String> files, long[] sizes, LinkedHashMap<String, FileSelector> selectors, Stage thisStage, String handle, int key, String clientKey, int remotePort, ChatMain chatWindow, HostConnection.Client client) {
		this.thisStage = thisStage;
		this.key = key;
		this.clientKey = clientKey;
		this.chatWindow = chatWindow;
		this.client = client;
		this.remotePort = remotePort;
		mSelectors = selectors;
		
		mAcceptButton.setGraphic(new ImageView(new Image("/atChat/resources/images/checkButton.png")));
		mDeclineButton.setGraphic(new ImageView(new Image("/atChat/resources/images/xButton.png")));
		
		mLabel.setText("Select files you wish to receive from " + handle);
		this.handle = handle;
		if (!ChatPreferences.lastDownloadPath.exists()) {
			if (new File(System.getProperty("user.home"), "Downloads").exists()) {
				lastPath = new File(System.getProperty("user.home"), "Downloads").getPath();
			} else { lastPath = System.getProperty("user.home"); }
		} else {
			lastPath = ChatPreferences.lastDownloadPath.getPath();
		}
		saveInLabel.setText(lastPath);
		
		Rectangle2D viewRect = new Rectangle2D(0, 0, 18, 18);
		checkedImageView.setImage(new Image(checked));
		unCheckedImageView.setImage(new Image(unChecked));
		overwriteImageView.setImage(new Image(overwrite));
		folderImageView.setImage(new Image("/atChat/resources/images/folder.png"));
		checkedImageView.setViewport(viewRect);
		unCheckedImageView.setViewport(viewRect);
		overwriteImageView.setViewport(viewRect);
		folderImageView.setViewport(viewRect);
		mSizes = sizes;
		
		mTreeView.setRoot(files);
		files.setExpanded(true);
		mTreeView.setCellFactory(new Callback<TreeView<String>, TreeCell<String>>() {
			@Override
			public TreeCell<String> call(TreeView<String> treeView) {
				return new FileCell<String>();
			}
		});
		mTreeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		
		mTreeView.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				TreeItem<String> item = mTreeView.getSelectionModel().getSelectedItem();
				if (item != null) {
					FileSelector selector = mSelectors.get(item.getValue());
					selector.setSelected(!selector.isSelected());
					
					if (selector.isDirectory()) {
						for (TreeItem<String> c : item.getChildren()) {
							FileSelector sel = mSelectors.get(c.getValue());
							sel.setSelected(selector.isSelected());
							if (sel.isDirectory()) {
								directorySelection(sel);
							}
						}
					}
					
					TreeItem<String> p = item.getParent();
					if (p != null && selector.isSelected()) {
						mSelectors.get(p.getValue()).setSelected(true);
						TreeItem<String> pP = p.getParent();
						if (pP != null) {
							reverseDirectorySelection(mSelectors.get(pP.getValue()));
						}
					}
					
					TreeItem<String> root = mTreeView.getRoot();
					ScrollBar sV = (ScrollBar) mTreeView.lookup(".scroll-bar:vertical");
					ScrollBar sH = (ScrollBar) mTreeView.lookup(".scroll-bar:horizontal");
					double posV = sV.getValue();
					double posH = sH.getValue();
					
					mTreeView.setRoot(null);
					mTreeView.setRoot(root);
	
					sV.setValue(posV);
					sH.setValue(posH);
				}
			}
		});
		
		this.thisStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent event) {
				declineClick(null);
			}
		});
	}
	
	private void directorySelection(FileSelector selector) {
		for (TreeItem<String> tItem : selector.getItem().getChildren()) {
			FileSelector nSel = mSelectors.get(tItem.getValue());
			nSel.setSelected(selector.isSelected());
			if (nSel.isDirectory()) {
				directorySelection(nSel);
			}
		}
	}
	
	private void reverseDirectorySelection(FileSelector selector) {
		TreeItem<String> item = selector.getItem();
		selector.setSelected(true);
		
		TreeItem<String> p = item.getParent();
		if (p != null) {
			reverseDirectorySelection(mSelectors.get(p.getValue()));
		}
	}
	
	@FXML protected void saveInClick (ActionEvent event) {
		DirectoryChooser dirChooser = new DirectoryChooser();
		dirChooser.setTitle("Select Files");
		dirChooser.setInitialDirectory((new File(lastPath).exists()) ? new File(lastPath) : new File(System.getProperty("user.home")));
		File folder = dirChooser.showDialog(thisStage);
		
		if (folder != null) {
			lastPath = folder.getPath();
			saveInLabel.setText(lastPath);
			
			TreeItem<String> root = mTreeView.getRoot();
			ScrollBar sV = (ScrollBar) mTreeView.lookup(".scroll-bar:vertical");
			ScrollBar sH = (ScrollBar) mTreeView.lookup(".scroll-bar:horizontal");
			double posV = sV.getValue();
			double posH = sH.getValue();
			
			mTreeView.setRoot(null);
			mTreeView.setRoot(root);

			sV.setValue(posV);
			sH.setValue(posH);
		}
	}
	
	@FXML protected void acceptClick (ActionEvent event) {
		File newPath = new File(lastPath);
		boolean noPath = false;
		if (!newPath.exists()) {
			if (new File(System.getProperty("user.home"), "Downloads").exists()) {
				ChatPreferences.lastDownloadPath = new File(System.getProperty("user.home"), "Downloads");
			} else { ChatPreferences.lastDownloadPath = new File(System.getProperty("user.home")); }
			ChatCommon.popError("The selected download directory does not exist!", false);
			lastPath = ChatPreferences.lastDownloadPath.getPath();
			saveInLabel.setText(lastPath);
			noPath = true;
		} else {
			ChatPreferences.lastDownloadPath = newPath;
		}
		
		if (!noPath && chatWindow.isConnected()) {
			ChatPreferences.writePrefs();
			
			List<Integer> indices = new ArrayList<Integer>();
			List<String> selectedItems = new ArrayList<String>();
			int index = 0;
			for (FileSelector selector : mSelectors.values()) {
				if (selector.isSelected() && !selector.isDirectory()) {
					indices.add(new Integer(index));
					selectedItems.add(selector.getPath());
				}
				index ++;
			}
			
			if (!indices.isEmpty()) {
				if (client != null) {
					if (client.isAccepted()) {
						ReceiveFile rf = new ReceiveFile(key, chatWindow, selectedItems, indices, mSizes, lastPath, client);
						chatWindow.getDownloadBox().getChildren().add(rf.createDownloadBox(chatWindow.getDownloadBox(), chatWindow.getDownloadScroll()));
						client.setReceiveFile(rf);
						chatWindow.getHost().fileServerListen.addReceiver(rf, Integer.toString(rf.getRFKey()) + ":" + client.getKey());
						ArrayList<HostConnection.Client> sendTo = new ArrayList<HostConnection.Client>();
						sendTo.add(client);
						chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.ACCEPT_TRANSFER + ChatCommon.SEPARATOR + Integer.toString(key) + ChatCommon.SEPARATOR + Integer.toString(rf.getRFKey()) + ChatCommon.SEPARATOR + Integer.toString(ChatPreferences.lFilePort) + ChatCommon.SEPARATOR + client.getKey()), sendTo, false, false, null);
						
						thisStage.close();
					} else {
						ChatCommon.popError(handle + " is no longer connected.", false);
						thisStage.close();
					}
				} else {
					ReceiveFile rf = null;
					try {
						rf = new ReceiveFile(key, clientKey, chatWindow, selectedItems, indices, mSizes, remotePort, lastPath);
					} catch (IOException e) {
						ChatCommon.popError("Unable to open directory transfer socket:" + System.getProperty("line.separator") + e.getMessage(), false);
					} finally {
						if (rf != null) {
							chatWindow.getDownloadBox().getChildren().add(rf.createDownloadBox(chatWindow.getDownloadBox(), chatWindow.getDownloadScroll()));
							chatWindow.getClient().getFileReceiver().addReceiver(rf);
							
							thisStage.close();
						}
					}
				}
			} else {
				declineClick(null);
			}
		} else if (chatWindow.isConnected()) {
			ChatCommon.popError("The selected download directory does not exist.", false);
		} else {
			ChatCommon.popError(handle + " is no longer connected.", false);
			thisStage.close();
		}
	}
	
	@FXML protected void declineClick (ActionEvent event) {
		if (chatWindow.isConnected()) {
			if (chatWindow.isHosting()) {
				if (chatWindow.getHost().isConnected() && ! chatWindow.getHost().isDisconnecting()) {
					ArrayList<HostConnection.Client> sendTo = new ArrayList<HostConnection.Client>();
					sendTo.add(client);
					chatWindow.getHost().addMessageToQueue(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_REFUSED + ChatCommon.SEPARATOR + Integer.toString(key)), sendTo, false, false, null);
				}
			} else {
				if (chatWindow.getClient().isConnected()) {
					chatWindow.getClient().sendMessage(ChatCommon.formatSystemMessage(ChatCommon.DOWNLOAD_S_REFUSED + ChatCommon.SEPARATOR + Integer.toString(key)), false);
				}
			}
		}
		
		thisStage.close();
	}
	
	private class FileCell<String> extends TreeCell<String> {
		
		@Override
		public void updateSelected(boolean selected) {
			super.updateSelected(selected);
		}
		
		@Override
		protected void updateItem(String item, boolean empty) {
			super.updateItem(item, empty);
			
			if (item != null) {
				HBox h = new HBox(7);
				h.setAlignment(Pos.CENTER_LEFT);
				
				FileSelector selector = mSelectors.get(item.toString());
				ImageView checkBox = new ImageView(selector.isSelected() ? selector.getCheckedImage() : selector.getUnCheckedImage());
				checkBox.setViewport(new Rectangle2D(0, 0, 18, 18));
				
				VBox v = new VBox(1);
				v.setAlignment(Pos.CENTER_LEFT);
				
				Label label = new Label(new File(item.toString()).getName());
				
				if (!selector.isDirectory()) {
					label.setStyle("-fx-font-family: Arial;"
							+ "-fx-text-fill: rgb(32, 186, 255);"
							+ "-fx-font-size: 12px;"
							+ "-fx-font-weight: bold");
					
					Label fileSize = new Label(ChatCommon.formatFileSize(selector.getSize()));
					fileSize.setStyle("-fx-font-family: Arial;"
							+ "-fx-text-fill: rgb(83%, 83%, 83%);"
							+ "-fx-font-size: 10px;");
					
					v.getChildren().addAll(label, fileSize);
				} else {
					label.setStyle("-fx-font-family: Arial;"
							+ "-fx-text-fill: rgb(93%, 93%, 93%);"
							+ "-fx-font-size: 12px;"
							+ "-fx-font-weight: bold");
					
					v.getChildren().add(label);
				}
				
				if (selector.isDirectory()) {
					ImageView dirView = new ImageView(selector.getDirImage());
					dirView.setViewport(new Rectangle2D(0, 0, 18, 18));
					h.getChildren().addAll(dirView, checkBox, v);
				}else if (!new File(lastPath, item.toString()).exists()) {
					h.getChildren().addAll(checkBox, v);
				} else {
					ImageView overwriteView = new ImageView(selector.getOverwriteImage());
					overwriteView.setViewport(new Rectangle2D(0, 0, 18, 18));
					h.getChildren().addAll(checkBox, overwriteView, v);
				}
				
				setGraphic(h);
			} else {
				setText("");
				setGraphic(null);
			}
		}
	}
}
