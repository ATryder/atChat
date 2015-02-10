package atChat;

import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class Main extends Application {

	public static void main(String[] args) {
		Application.launch(args);

	}
	
	@Override
	public void start(Stage primaryStage) {
		try {
			FXMLLoader loader = new FXMLLoader(Main.class.getResource("/atChat/resources/fxml/chatMain.fxml"));
			AnchorPane mainPane = (AnchorPane) loader.load();
			((ChatMain) loader.getController()).prepare(primaryStage);
			
			Scene scene = new Scene(mainPane);
			primaryStage.setScene(scene);
			primaryStage.getIcons().addAll(new Image("/atChat/resources/images/@Chat_Icon_16_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_24_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_32_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_48_32bit.png"), new Image("/atChat/resources/images/@Chat_Icon_64_32bit.png"));
			scene.getStylesheets().clear();
			scene.getStylesheets().add(Main.class.getResource("/atChat/resources/fxml/atChatTheme.css").toExternalForm());
			primaryStage.setTitle("@Chat");
			primaryStage.show();
		} catch (Exception e) {
			Logger.getLogger(Main.class.getName()).log(Level.SEVERE, e.getMessage(), e);
			System.out.println("Unable to open @Chat:" + System.getProperty("line.separator") + e.getMessage());
		}
	}

}
