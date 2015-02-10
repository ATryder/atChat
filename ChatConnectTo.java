package atChat;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class ChatConnectTo implements Initializable {
	@FXML
	private TextField remoteChatPort;
	@FXML
	private TextField remoteIPAddress;
	@FXML
	private TextField clientHandle;
	
	@FXML
	HBox passwordHBox;
	@FXML
	private TextField passwordField;
	@FXML
	private CheckBox showPassword;
	
	private PasswordField maskedPasswordField;
	
	@FXML
	private Button mAcceptButton;
	@FXML
	private Button mDeclineButton;
	
	private Stage thisStage;
	private ChatMain mainThread;
	
	protected void setStage(Stage thisStage, ChatMain mainThread) {
		this.thisStage = thisStage;
		this.mainThread = mainThread;
	}
	
	@Override
	public void initialize(URL url, ResourceBundle resources) {
		maskedPasswordField = new PasswordField();
		maskedPasswordField.setMaxWidth(passwordField.getMaxWidth());
		maskedPasswordField.setMinWidth(passwordField.getMinWidth());
		maskedPasswordField.setPrefWidth(passwordField.getPrefWidth());
		maskedPasswordField.setMaxHeight(clientHandle.getMaxHeight());
		maskedPasswordField.setMinHeight(clientHandle.getMinHeight());
		maskedPasswordField.setPrefHeight(clientHandle.getPrefHeight());
		passwordField.setMaxHeight(clientHandle.getMaxHeight());
		passwordField.setMinHeight(clientHandle.getMinHeight());
		passwordField.setPrefHeight(clientHandle.getPrefHeight());
		
		mAcceptButton.setGraphic(new ImageView(new Image("/atChat/resources/images/checkButton.png")));
		mDeclineButton.setGraphic(new ImageView(new Image("/atChat/resources/images/xButton.png")));
		
		remoteChatPort.setText(new Integer(ChatPreferences.rChatPort).toString());
		remoteIPAddress.setText(ChatPreferences.rIPAddress);
		
		clientHandle.setText(ChatPreferences.handle);
		
		maskedPasswordField.setOnKeyReleased(new EventHandler<KeyEvent>() {
			public void handle(final KeyEvent event) {
				if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == KeyCode.ENTER) {
					saveButton(null);
				}
			}
		});
		passwordField.setOnKeyReleased(new EventHandler<KeyEvent>() {
			public void handle(final KeyEvent event) {
				if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == KeyCode.ENTER) {
					saveButton(null);
				}
			}
		});
		
		remoteChatPort.setOnKeyReleased(new EventHandler<KeyEvent>() {
			public void handle(final KeyEvent event) {
				if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == KeyCode.ENTER) {
					if (!checkPort(remoteChatPort, ChatPreferences.rChatPort)) {
						ChatCommon.popError("Ports must be greater than zero, less than or equal to 65535 and must contain only numeric characters.", false);
					} else {
						saveButton(null);
					}
				}
			}
		});
		
		remoteChatPort.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldVal, String newVal) {
				numberChecker(remoteChatPort);
			}
		});
		
		remoteIPAddress.setOnKeyReleased(new EventHandler<KeyEvent>() {
			public void handle(final KeyEvent event) {
				if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == KeyCode.ENTER) {
					if (!checkIP()) {
						ChatCommon.popError("IP address must be in the 255.255.255.255 format, four numeric sections between 0 and 255 seperated by periods.", false);
					} else {
						saveButton(null);
					}
				}
			}
		});
		
		remoteIPAddress.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldVal, String newVal) {
				String s = remoteIPAddress.getText();
				whileLoop: while (true) {
					for (int i = 0; i < s.length(); i++) {
						char c = s.charAt(i);
						if (!(c >= '0' && c <= '9') && c != '.') {
							if (i > 0 && i < s.length() - 1) {
								s = s.substring(0, i) + s.substring(i + 1, s.length());
							} else if (i > 0) {
								s = s.substring(0, i);
							} else if (s.length() > 1){
								s = s.substring(1, s.length());
							} else {
								s = "1";
							}
							continue whileLoop;
						}
					}
					
					break;
				}
				
				remoteIPAddress.setText(s);
			}
		});
		
		clientHandle.setOnKeyReleased(new EventHandler<KeyEvent>() {
			public void handle(final KeyEvent event) {
				if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == KeyCode.ENTER) {
					if (!checkHandle(ChatPreferences.handle)) {
						ChatCommon.popError("Handle must be less than 46 characters in length and cannot contain " + ChatCommon.SEPARATOR, false);
					} else {
						saveButton(null);
					}
				}
			}
		});
	}
	
	private void numberChecker(TextField field) {
		String s = field.getText();
		whileLoop: while (true) {
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				if (!(c >= '0' && c <= '9')) {
					if (i > 0 && i < s.length() - 1) {
						s = s.substring(0, i) + s.substring(i + 1, s.length());
					} else if (i > 0) {
						s = s.substring(0, i);
					} else if (s.length() > 1){
						s = s.substring(1, s.length());
					} else {
						s = "1";
					}
					continue whileLoop;
				}
			}
			
			break;
		}
		
		if (!s.isEmpty()) {
			try {
				int i = Integer.parseInt(s);
				if (i <= 0) {
					s = "1";
				} else if (i > 65535) {
					s = "65535";
				}
			} catch (Exception e) {
				
			}
		} else {
			s = "1";
		}
		
		field.setText(s);
	}
	
	private boolean checkPort(TextField field, int defaultPort) {
		int port = -1;
		try {
			port = Integer.parseInt(field.getText());
		} catch (NumberFormatException e) {
			port = -1;
		} finally {
			if (port <= -1) {
				field.setText(new Integer(defaultPort).toString());
			} else if (port > 65535 || port == 0) {
				port = -1;
				field.setText(new Integer(defaultPort).toString());
			}
		}
		
		return (port > -1);
	}
	
	private boolean checkIP() {
		boolean goodIP = false;
		String[] ipS = remoteIPAddress.getText().split("\\.");
		if (ipS.length == 4) {
			if ((ipS[0].length() > 0 && ipS[0].length() <= 3) && (ipS[1].length() > 0 && ipS[1].length() <= 3) && (ipS[2].length() > 0 && ipS[2].length() <= 3) && (ipS[3].length() > 0 && ipS[3].length() <= 3)) {
				goodIP = true;
				for (String str : ipS) {
					try {
						Integer.parseInt(str);
					} catch (NumberFormatException e) {
						goodIP = false;
						break;
					}
				}
			}
		}
		if (!goodIP) {
			remoteIPAddress.setText(ChatPreferences.rIPAddress);
		}
		
		return goodIP;
	}
	
	private boolean checkHandle(String defaultHandle) {
		if (clientHandle.getText() != null) {
			if (clientHandle.getText().length() == 0) {
				clientHandle.setText(defaultHandle);
				return false;
			} else if (ChatCommon.isSystemMessage(clientHandle.getText())){
				String newHandle = ChatCommon.unFormatSystemMessage(clientHandle.getText());
				if (newHandle.length() == 0) {
					newHandle = defaultHandle;
				} else if (newHandle.length() >= 46) {
					newHandle = newHandle.substring(0, 46);
				}
				clientHandle.setText(newHandle);
				return false;
			} else if (clientHandle.getText().length() >= 46) {
				clientHandle.setText(clientHandle.getText().substring(0, 46));
				return false;
			} else if (clientHandle.getText().indexOf(ChatCommon.SEPARATOR) > -1) {
				if (clientHandle.getText().length() > 1) {
					clientHandle.setText(clientHandle.getText().replaceAll(ChatCommon.SPLIT_SEPARATOR, ""));
				} else {
					clientHandle.setText(defaultHandle);
				}
				return false;
			}
			
			if (clientHandle.getText().length() <= 2) {
				clientHandle.setText(clientHandle.getText() + ((clientHandle.getText().length() == 1) ? "  " : " "));
			}
		} else {
			clientHandle.setText(defaultHandle);
			return false;
		}
		
		return true;
	}
	
	@FXML protected void showPassTogg(ActionEvent event) {
		if (showPassword.isSelected()) {
			maskedPasswordField.setText(passwordField.getText());
			passwordHBox.getChildren().remove(passwordField);
			passwordHBox.getChildren().add(maskedPasswordField);
		} else {
			passwordField.setText(maskedPasswordField.getText());
			passwordHBox.getChildren().remove(maskedPasswordField);
			passwordHBox.getChildren().add(passwordField);
		}
	}
	
	@FXML protected void keyPress(KeyEvent event) {
		if (event.getCode().equals(KeyCode.ENTER)) {
			saveButton(null);
		}
	}
	
	@FXML protected void saveButton(ActionEvent event) {
		if (!checkPort(remoteChatPort, ChatPreferences.rChatPort)) {
			ChatCommon.popError("Ports must be greater than zero, less than or equal to 65535 and must contain only numeric characters.", false);
		} else if (!checkHandle(ChatPreferences.handle)) {
			ChatCommon.popError("Handle must be less than 46 characters in length and cannot contain " + ChatCommon.SEPARATOR, false);
		} else if (!checkIP()) {
			ChatCommon.popError("IP address must be in the 255.255.255.255 format, four numeric sections between 0 and 255 seperated by periods.", false);
		} else {
			ChatPreferences.rChatPort = Integer.parseInt(remoteChatPort.getText());
			ChatPreferences.rIPAddress = remoteIPAddress.getText();
			ChatPreferences.handle = clientHandle.getText();
			
			ChatPreferences.writePrefs();
			
			String password = (showPassword.isSelected()) ? maskedPasswordField.getText() : passwordField.getText();
			mainThread.startClient((password == null) ? "" : password);
			
			thisStage.close();
		}
	}
	
	@FXML protected void cancelButton(ActionEvent event) {
		thisStage.close();
	}
}
