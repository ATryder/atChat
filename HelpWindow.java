package atChat;

import com.sun.javafx.scene.control.skin.ScrollPaneSkin;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Callback;

public class HelpWindow {
	
	@FXML
	private ScrollPane mScrollPane;
	@FXML
	private VBox mTextArea;
	@FXML
	private TreeView<String> mTreeView;
	
	public void initWindow(Stage thisStage) {
		initTreeView();
		
		thisStage.setOnShown(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent event) {
				ScrollPaneSkin skin = (ScrollPaneSkin)mScrollPane.getSkin();
				for (Node node : skin.getChildren()) {
					if (node instanceof StackPane) {
						((StackPane)node).setCache(false);
					}
				}
			}
		});
	}
	
	private void initTreeView() {
		TreeItem<String> root = new TreeItem<String>("Root");
		
		TreeItem<String> connecting = new TreeItem<String>("Connecting");
		TreeItem<String> hosting = new TreeItem<String>("Host");
		TreeItem<String> client = new TreeItem<String>("Client");
		connecting.getChildren().addAll(hosting, client);
		
		TreeItem<String> chatting = new TreeItem<String>("Chat Messages");
		TreeItem<String> sr = new TreeItem<String>("Sending/Receiving");
		TreeItem<String> clipboard = new TreeItem<String>("Copying to the Clipboard");
		chatting.getChildren().addAll(sr, clipboard);
		
		TreeItem<String> transfer = new TreeItem<String>("File Transfer");
		TreeItem<String> sending = new TreeItem<String>("Sending Files and Directories");
		TreeItem<String> receivingFiles = new TreeItem<String>("Receiving Files");
		TreeItem<String> receivingDirectories = new TreeItem<String>("Receiving a Directory");
		transfer.getChildren().addAll(sending, receivingFiles, receivingDirectories);
		
		root.getChildren().addAll(connecting, chatting, transfer);
		root.setExpanded(true);
		
		mTreeView.setRoot(root);
		mTreeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		mTreeView.setCellFactory(new Callback<TreeView<String>, TreeCell<String>>() {
			@Override
			public TreeCell<String> call(TreeView<String> treeView) {
				return new FileCell<String>();
			}
		});
		
		mTreeView.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				TreeItem<String> item = mTreeView.getSelectionModel().getSelectedItem();
				if (item != null) {
					changeText(item.getValue());
				}
			}
		});
	}
	
	private void changeText(String selection) {
		String text = "";
		switch (selection) {
		case "Host":
			text = "To host an @Chat session begin by selecting \"Host\" from the \"File\" menu.  You will be presented with the hosting options window which will list out the following options:" + System.getProperty("line.separator") + System.getProperty("line.separator") + "Chat Port:  All incoming connections will initially contact your system on this port.  In addition chat messages will be exchanged using this port.  The port must be unique and must fall between 1 and 65535.  This is the port you\'ll need to give to others in order for them to connect to your session.*" + System.getProperty("line.separator") + System.getProperty("line.separator") + "File Port:  All file transfers, both incoming and outgoing, will be exchanged over this port.  This port must be unique and fall between 1 and 65535.*" + System.getProperty("line.separator") + System.getProperty("line.separator") + "Handle:  This is your screen name, when you send a message or files to others they will recognize you by this name." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Password:  An optional password may be specified, any incoming connections will need this password in order to connect to your session." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Additional hosting options are available from the \"Connections\" section on the main @Chat window." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Auto Accept:  When someone attempts to connect to your @Chat session you will be presented with a window asking if you\'d like to accept or reject this new connection.  If \"Auto Accept\" is checked all incoming connections will bypass this and their connection will be automatically accepted." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Listen:  If this is checked on @Chat will continually listen for incoming connections, when this is checked off no new connections can be made." + System.getProperty("line.separator") + System.getProperty("line.separator") + "*If you are behind a router you\'ll need to enable \"Port Forwarding\" on your router for both the Chat and File ports.  This entails setting the router to automatically forward any data coming in on a particular port directly to a specified IP Address, the local IP Address of your computer.  Consult your router\'s manual for information on how to enable port forwarding.  Clients connecting to your system will be connecting to the IP Address of your router, not the IP Address of your computer.";
			break;
		case "Client":
			text = "To connect to an existing @Chat session begin by selecting \"Connect\" from the \"File\" menu.  This will bring up the connection options window in which the following options are available:" + System.getProperty("line.separator") + System.getProperty("line.separator") + "Port:  This port will be used to connect and exchange chat messages with the host and, by extension, other clients.  This port should be identical to the Host\'s \"Chat Port.\"" + System.getProperty("line.separator") + System.getProperty("line.separator") + "IP Address:  This is the IP Address of the host computer, you will need to obtain this information from the host prior to connecting." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Handle:  This is your screen name, when you send a message or files to others they will recognize you by this name." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Password:  The Host may have specified an optional password, if so you\'ll need to enter this password here in order to successfully join the @Chat session.";
			break;
		case "Sending/Receiving":
			text = "Once you are either connected to or hosting a session with at least one connected client you can send and receive text messages.  From the main @Chat window select the text entry box in the lower left, enter a message and click send or press Shift + Enter.  All incoming and outgoing messages will be displayed in the upper left display window.";
			break;
		case "Copying to the Clipboard":
			text = "There are two options available for copying chat messages to the clipboard.  If you desire to copy all messages currently displayed in the chat view select \"Copy Messages\" from the \"Edit\" menu.  You may also copy a single message to the clipboard, hover your mouse cursor over the message you wish to copy, when the message turns green click it with the mouse and that message will have been copied to the clipboard.";
			break;
		case "Sending Files and Directories":
			text = "To send one or more files to a connected party expand the \"Connections\" menu from the @Chat main window, right click their name in the connections list and select \"Send Files.\"  If you are Hosting this session you may send files to all connected parties, if you are connected to a host then you may only send files to the host which will be the first name listed." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Additionally you may opt to send an entire directory to someone, follow the same process as sending files except select \"Send Directory\" from the right click context menu." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Once you\'ve opted to send files or directories you will be presented with a file selection window.  Navigate to the folder containing the files you wish to send and select one or more files, if you\'re sending a directory select the directory you desire to send.  Once you\'ve sent the file transfer request to someone they will be given the option to accept or decline the transfer." + System.getProperty("line.separator") + System.getProperty("line.separator") + "You may view the status of your file transfers, both receiving and sending, by selecting the \"Downloads\" menu from the @Chat main window.";
			break;
		case "Receiving Files":
			text = "When you receive a file transfer request a window displaying the files contained in the request will open.  Select \"Decline\" if you do not wish to download any of these files, select \"Accept\" to begin downloading the selected files." + System.getProperty("line.separator") + System.getProperty("line.separator") + "From the list of files being transferred you may opt to download some, but not all, of the files.  To select or deselect a file simply click on the file with your mouse, an x next to the file indicates that it will not be downloaded while a check mark indicates that the file will be downloaded." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Pay attention to an additional icon that may appear between the check-box and file name.  If you see an image displaying a white page with a red slash through it that file will need to be overwritten, if you opt to download this file the current version of this file in the selected download folder will be overwritten!" + System.getProperty("line.separator") + System.getProperty("line.separator") + "You may change the folder in which files will be downloaded to by selecting the \"Browse\" button.  This will bring up a folder selection window, simply navigate to and select the folder you wish to store these files in." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Once a download has been accepted you may view the status of this download from the \"Downloads\" menu in @Chat\'s main window.";
			break;
		case "Receiving a Directory":
			text = "Receiving a directory is largely the same as receiving files, refer to the \"Receiving Files\" section for additional info.  However; there are some differences in the selection of which files/folders you wish to download." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Much like receiving a range of files you may click on a file in the list to select or deselect it.  Clicking a folder will select/deselect all of the files, folders, sub-folders and sub-files contained in that folder.  Selecting a single file in a folder will automatically check all parent folders, but this will not check all files contained within those parent folders." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Expand and collapse folders by selecting the arrow next to the folder name." + System.getProperty("line.separator") + System.getProperty("line.separator") + "Once a download has been accepted you may view the status of this download from the \"Downloads\" menu in @Chat\'s main window.";
			break;
		}
		
		if (text.length() > 0) {
			Label helpText = new Label(text);
			helpText.setStyle("-fx-text-fill: rgb(93%, 93%, 93%); -fx-font-size: 12px;");
			helpText.setWrapText(true);
			mTextArea.getChildren().clear();
			mTextArea.getChildren().add(helpText);
			
			mScrollPane.setVvalue(0);
		}
	}
	
	private class FileCell<String> extends TreeCell<String> {
		
		@Override
		public void updateSelected(boolean selected) {
			if (!getTreeItem().isLeaf()) {
				selected = false;
			} else {
				super.updateSelected(selected);
			}
		}
		
		@Override
		protected void updateItem(String item, boolean empty) {
			super.updateItem(item, empty);
			
			if (item != null) {
				if (!getTreeItem().isLeaf()) {
					Label label = new Label(item.toString());
					label.setStyle("-fx-text-fill: rgb(32, 186, 255); -fx-font-size: 14px;");
					setGraphic(label);
				} else {
					setText(item.toString());
				}
			} else {
				setText("");
				setGraphic(null);
			}
		}
	}
}
