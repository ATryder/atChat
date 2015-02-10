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
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class ChatHost implements Initializable {
	
	@FXML
	private TextField chatPort;
	@FXML
	private TextField filePort;
	@FXML
	private TextField hostHandle;
	
	@FXML
	private CheckBox enablePassword;
	@FXML
	private CheckBox showPassword;
	@FXML
	private TextField passwordField;
	
	private PasswordField maskedPasswordField;
	
	@FXML
	private Button mAcceptButton;
	@FXML
	private Button mDeclineButton;
	
	@FXML
	private GridPane passwordGrid;
	
	private Stage thisStage;
	private ChatMain mainThread;
	
	protected void setStage(Stage thisStage, ChatMain mainThread) {
		this.thisStage = thisStage;
		this.mainThread = mainThread;
	}
	
	@Override
	public void initialize(URL url, ResourceBundle resources) {
		mAcceptButton.setGraphic(new ImageView(new Image("/atChat/resources/images/checkButton.png")));
		mDeclineButton.setGraphic(new ImageView(new Image("/atChat/resources/images/xButton.png")));
		
		maskedPasswordField = new PasswordField();
		maskedPasswordField.setMaxWidth(passwordField.getMaxWidth());
		maskedPasswordField.setMinWidth(passwordField.getMinWidth());
		maskedPasswordField.setPrefWidth(passwordField.getPrefWidth());
		
		passwordField.setDisable(true);
		showPassword.setDisable(true);
		
		passwordField.setOnKeyReleased(new EventHandler<KeyEvent>() {
			public void handle(final KeyEvent event) {
				if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == KeyCode.ENTER) {
					saveButton(null);
				}
			}
		});
		
		maskedPasswordField.setOnKeyReleased(new EventHandler<KeyEvent>() {
			public void handle(final KeyEvent event) {
				if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == KeyCode.ENTER) {
					saveButton(null);
				}
			}
		});
		
		chatPort.setText(new Integer(ChatPreferences.lChatPort).toString());
		filePort.setText(new Integer(ChatPreferences.lFilePort).toString());
		
		chatPort.setOnKeyReleased(new EventHandler<KeyEvent>() {
			public void handle(final KeyEvent event) {
				if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == KeyCode.ENTER) {
					if (!checkPort(chatPort, ChatPreferences.lChatPort)) {
						ChatCommon.popError("Ports must be greater than zero, less than or equal to 65535 and must contain only numeric characters.", false);
					} else {
						saveButton(null);
					}
				}
			}
		});
		
		chatPort.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldVal, String newVal) {
				numberChecker(chatPort);
			}
		});
		
		hostHandle.setText(ChatPreferences.handle);
		hostHandle.setOnKeyReleased(new EventHandler<KeyEvent>() {
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
		
		filePort.setOnKeyReleased(new EventHandler<KeyEvent>() {
			public void handle(final KeyEvent event) {
				if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == KeyCode.ENTER) {
					if (!checkPort(filePort, ChatPreferences.lChatPort)) {
						ChatCommon.popError("Ports must be greater than zero, less than or equal to 65535 and must contain only numeric characters.", false);
					} else {
						saveButton(null);
					}
				}
			}
		});
		
		filePort.textProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldVal, String newVal) {
				numberChecker(filePort);
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
	
	private boolean checkHandle(String defaultHandle) {
		if (hostHandle.getText() != null) {
			if (hostHandle.getText().length() == 0) {
				hostHandle.setText(defaultHandle);
				return false;
			} else if (ChatCommon.isSystemMessage(hostHandle.getText())) {
				String newHandle = ChatCommon.unFormatSystemMessage(hostHandle.getText());
				if (newHandle.length() == 0) {
					newHandle = defaultHandle;
				} else if (newHandle.length() >= 46) {
					newHandle = newHandle.substring(0, 46);
				}
				hostHandle.setText(newHandle);
				return false;
			} else if (hostHandle.getText().length() >= 46) {
				hostHandle.setText(hostHandle.getText().substring(0, 46));
				return false;
			} else if (hostHandle.getText().indexOf(ChatCommon.SEPARATOR) > -1) {
				if (hostHandle.getText().length() > 1) {
					hostHandle.setText(hostHandle.getText().replaceAll(ChatCommon.SPLIT_SEPARATOR, ""));
				} else {
					hostHandle.setText(defaultHandle);
				}
				return false;
			}
			
			if (hostHandle.getText().length() <= 2) {
				hostHandle.setText(hostHandle.getText() + ((hostHandle.getText().length() == 1) ? "  " : " "));
			}
		} else {
			hostHandle.setText(defaultHandle);
			return false;
		}
		
		return true;
	}
	
	@FXML protected void showPassTogg(ActionEvent event) {
		if (showPassword.isSelected()) {
			maskedPasswordField.setText(passwordField.getText());
			passwordGrid.getChildren().remove(passwordField);
			passwordGrid.add(maskedPasswordField, 1, 1, 1, 1);
			maskedPasswordField.setDisable(false);
		} else {
			passwordField.setText(maskedPasswordField.getText());
			passwordGrid.getChildren().remove(maskedPasswordField);
			passwordGrid.add(passwordField, 1, 1, 1, 1);
			passwordField.setDisable(false);
		}
	}
	
	@FXML protected void enablePasswordTogg(ActionEvent event) {
		if (!showPassword.isSelected()) {
			passwordField.setDisable(!enablePassword.isSelected());
		} else {
			maskedPasswordField.setDisable(!enablePassword.isSelected());
		}
		showPassword.setDisable(!enablePassword.isSelected());
	}
	
	@FXML protected void keyPress(KeyEvent event) {
		if (event.getCode().equals(KeyCode.ENTER)) {
			saveButton(null);
		}
	}
	
	@FXML protected void cancelButton(ActionEvent event) {
		thisStage.close();
	}
	
	@FXML protected void saveButton(ActionEvent event) {
		if (!checkPort(chatPort, ChatPreferences.lChatPort)) {
			ChatCommon.popError("Ports must be greater than zero, less than or equal to 65535 and must contain only numeric characters.", false);
		} else if (!checkPort(filePort, ChatPreferences.lFilePort)) {
			ChatCommon.popError("Ports must be greater than zero, less than or equal to 65535 and must contain only numeric characters.", false);
		} else if (!checkHandle(ChatPreferences.handle)) {
			ChatCommon.popError("Handle must be less than 46 characters in length and cannot contain " + ChatCommon.SEPARATOR, false);
		} else if (Integer.parseInt(chatPort.getText().toString()) != Integer.parseInt(filePort.getText().toString())) {
			ChatPreferences.lChatPort = Integer.parseInt(chatPort.getText());
			ChatPreferences.lFilePort = Integer.parseInt(filePort.getText());
			ChatPreferences.handle = hostHandle.getText();
			
			ChatPreferences.writePrefs();
			
			if (enablePassword.isSelected()) {				
				String pass = (showPassword.isSelected()) ? maskedPasswordField.getText() : passwordField.getText();
				if (pass != null) {
					if (pass.length() > 0) {
						mainThread.startHosting(pass);
					} else {
						mainThread.startHosting("");
					}
				} else {
					enablePassword.setSelected(false);
					mainThread.startHosting("");
				}
			} else {
				mainThread.startHosting("");
			}
			
			thisStage.close();
		} else {
			ChatCommon.popError("Chat and file ports must be different.", false);
		}
	}
	
}
