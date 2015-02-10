package atChat;

import java.io.IOException;

import javafx.animation.AnimationTimer;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class AcceptConnectionWindow {
	@FXML
	private HBox hBox;
	@FXML
	private ScrollPane scrollPane;
	
	@FXML
	private Label acceptLabel;
	@FXML
	private RadioButton acceptRadio;
	@FXML
	private RadioButton declineRadio;
	@FXML
	private TextField declineReason;
	
	private HostConnection.Client client;
	private CryptoRSA clientRSA;
	private Stage thisStage;
	
	private AnimationTimer timeLeft;
	private long startTime = -1;
	private static final long TIMEOUT = 60000000000l * 7;
	
	private ChatMain chatWindow;
	
	protected void initWindow(Stage thisStage, HostConnection.Client client, CryptoRSA clientRSA, String connectingParty, ChatMain cWindow) {
		this.thisStage = thisStage;
		this.client = client;
		this.clientRSA = clientRSA;
		this.chatWindow = cWindow;
		
		acceptLabel.setText(acceptLabel.getText() + System.getProperty("line.separator") + System.getProperty("line.separator") + connectingParty);
		
		hBox.heightProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				if (hBox.getHeight() < scrollPane.getHeight()) {
					hBox.setMaxHeight(scrollPane.getHeight());
					hBox.setMinHeight(scrollPane.getHeight());
					hBox.setPrefHeight(scrollPane.getHeight());
				}
			}
		});
		
		timeLeft = new AnimationTimer() {
			@Override
			public void handle(long now) {
				if (startTime == -1) {
					startTime = now;
				} else if (now - startTime >= TIMEOUT) {
					acceptRadio.setSelected(false);
					declineRadio.setSelected(true);
					declineReason.setText("Operation timed out.");
					dismissWindow(null);
				} else if (!chatWindow.isConnected()) {
					dismissWindow(null);
				}
			}
		};
		
		timeLeft.start();
	}
	
	@FXML protected void acceptClick(ActionEvent event) {
		declineReason.setDisable(true);
	}
	
	@FXML protected void declineClick(ActionEvent event) {
		declineReason.setDisable(false);
	}
	
	@FXML protected void dismissWindow(ActionEvent event) {
		if (chatWindow.isConnected()) {
			if (acceptRadio.isSelected()) {
				client.acceptClient(clientRSA);
			} else {
				try {
					String reason = declineReason.getText();
					if (reason != null) {
						if (ChatCommon.isSystemMessage(reason)) {
							reason = ChatCommon.unFormatSystemMessage(reason);
						}
						reason = ChatCommon.formatMessage(reason);
						client.refuseClient((reason.length() > 0) ? reason : "No reason supplied.", clientRSA);
					} else {
						client.refuseClient("No reason supplied.", clientRSA);
					}
				} catch (IOException e) {
					
				} finally {
					client.removeClient(false, false);
				}
			}
		}
		timeLeft.stop();
		thisStage.close();
	}

}
