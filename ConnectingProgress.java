package atChat;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class ConnectingProgress implements Initializable {
	
	@FXML
	private Label connectingLabel;
	@FXML
	private Button connectingButton;
	@FXML
	private ImageView connectingAnimView;
	
	private ImageViewSprite anim;
	
	private Stage thisStage;
	private ChatMain main;
	private ClientConnection clientConnection;
	
	private String password;
	
	private ConnectingProgress thisClass;
	
	protected void setStage(Stage thisStage, ChatMain cMain, String pass) {
		this.thisStage = thisStage;
		main = cMain;
		password = pass;
		thisClass = this;
		
		thisStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent event) {
				cancelClick(null);
				event.consume();
			}
		});
		
		connectingLabel.setText("Connecting to " + ChatPreferences.rIPAddress + " on port " + Integer.toString(ChatPreferences.rChatPort) + ".");
		
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				clientConnection = new ClientConnection(main, password, thisClass);
				main.setClientClass(clientConnection);
			}
		});
	}
	
	@Override
	public void initialize(URL url, ResourceBundle resources) {
		anim = new ImageViewSprite(connectingAnimView, new Image("/atChat/resources/images/connectingAnim.jpg"), 8, 6, 47, 360, 86, 24);
		anim.start();
	}
	
	protected void setToDismiss() {
		thisStage.setTitle("Connected");
		connectingLabel.setText("Connection Accepted!");
		connectingButton.setText("Dismiss");
	}
	
	protected void dismissWindow() {
		anim.stop();
		thisStage.close();
	}
	
	@FXML protected void cancelClick(ActionEvent event) {
		if (!clientConnection.isConnected()) {
			connectingLabel.setText("Canceling...");
			connectingButton.setDisable(true);
			clientConnection.cancelConnecting();
		} else {
			clientConnection.removeConnectionProgressWindow();
		}
	}
}
