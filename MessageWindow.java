package atChat;

import com.sun.javafx.scene.control.skin.ScrollPaneSkin;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class MessageWindow {
	
	@FXML
	private HBox hBox;
	@FXML
	private ScrollPane scrollPane;
	@FXML
	private Label msgText;
	
	private Stage thisStage;
	
	protected void setMessage(String msg, Stage thisStage) {
		msgText.setText(msg);
		this.thisStage = thisStage;
		
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
		
		thisStage.setOnShown(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent event) {
				ScrollPaneSkin skin = (ScrollPaneSkin)scrollPane.getSkin();
				for (Node node : skin.getChildren()) {
					if (node instanceof StackPane) {
						((StackPane)node).setCache(false);
					}
				}
			}
		});
	}
	
	@FXML protected void dismissClick(ActionEvent Event) {
		thisStage.close();
	}
	
}
