package atChat;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

public class AboutWindow implements Initializable {
	private static final int STATE_PLAY = 0;
	private static final int STATE_PAUSE = 1;
	private static final int STATE_REPLAY = 2;
	
	private int playState = STATE_PAUSE;
	private boolean playHovered = false;
	
	@FXML
	private HBox mediaHBox;
	@FXML
	private Button mRewind;
	@FXML
	private Button mPlay;
	@FXML
	private Button mFastForward;
	
	private MediaPlayer mediaPlayer;
	private boolean endOfMedia = false;
	private boolean errored = false;
	
	private Stage thisStage;
	
	private static final Image rewindImage = new Image("/atChat/resources/images/rewindbutton.png");
	private static final Image playImage = new Image("/atChat/resources/images/playbutton.png");
	private static final Image pauseImage = new Image("/atChat/resources/images/pausebutton.png");
	private static final Image replayImage = new Image("/atChat/resources/images/replaybutton.png");
	private static final Image ffImage = new Image("/atChat/resources/images/fastforwardbutton.png");
	
	private static final Image rewindImageH = new Image("/atChat/resources/images/rewindbutton_hover.png");
	private static final Image playImageH = new Image("/atChat/resources/images/playbutton_hover.png");
	private static final Image pauseImageH = new Image("/atChat/resources/images/pausebutton_hover.png");
	private static final Image replayImageH = new Image("/atChat/resources/images/replaybutton_hover.png");
	private static final Image ffImageH = new Image("/atChat/resources/images/fastforwardbutton_hover.png");
	
	public void initWindow(Stage thisStage) {
		this.thisStage = thisStage;
		
		thisStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent event) {
				dismissClick();
				event.consume();
			}
		});
	}
	
	@Override
	public void initialize(URL url, ResourceBundle resources) {
		mRewind.setGraphic(new ImageView(rewindImage));
		mPlay.setGraphic(new ImageView(pauseImage));
		mFastForward.setGraphic(new ImageView(ffImage));
		
		mRewind.setOnMouseEntered(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				mRewind.setGraphic(new ImageView(rewindImageH));
			}
		});
		mRewind.setOnMouseExited(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				mRewind.setGraphic(new ImageView(rewindImage));
			}
		});
		
		mPlay.setOnMouseEntered(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				playEnter();
			}
		});
		mPlay.setOnMouseExited(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				playExit();
			}
		});
		
		mFastForward.setOnMouseEntered(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				mFastForward.setGraphic(new ImageView(ffImageH));
			}
		});
		mFastForward.setOnMouseExited(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				mFastForward.setGraphic(new ImageView(ffImage));
			}
		});
		
		mediaPlayer = null;
		
		File cwd = null;
		try {
			cwd = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());
		} catch (IllegalArgumentException iE) {
			try {
				cwd = new File(ClassLoader.getSystemClassLoader().getResource(".").getPath());
			} catch (NullPointerException e) {
				setErrored();
			}
		} catch (NullPointerException nullE) {
			try {
				cwd = new File(ClassLoader.getSystemClassLoader().getResource(".").getPath());
			} catch (NullPointerException e) {
				setErrored();
			}
		}
		
		if (!errored) {
			try {
				mediaPlayer = new MediaPlayer(new Media(new File(cwd, "video" + File.separator + "created_by.mp4").toURI().toString()));
			} catch (NullPointerException nE) {
				System.out.println(nE.getMessage());
				setErrored();
			} catch (IllegalArgumentException iE) {
				System.out.println(iE.getMessage());
				setErrored();
			} catch (UnsupportedOperationException uE) {
				System.out.println(uE.getMessage());
				setErrored();
			} catch (MediaException mE) {
				System.out.println(mE.getMessage());
				setErrored();
			}
		}
		
		if (!errored) {
			mediaPlayer.setAutoPlay(true);
			mediaPlayer.setOnError(new Runnable() {
				@Override
				public void run() {
					setErrored();
				}
			});
			
			mediaPlayer.setOnHalted(new Runnable() {
				@Override
				public void run() {
					setErrored();
				}
			});
			
			MediaView mediaView = new MediaView(mediaPlayer);
			mediaView.setFitWidth(480);
			mediaView.setFitHeight(270);
			
			mediaHBox.getChildren().add(mediaView);
			
			mediaPlayer.setOnEndOfMedia(new Runnable() {
				@Override
				public void run() {
					setEndOfMedia();
				}
			});
		}
	}
	
	private void setErrored() {
		if (mediaPlayer != null) {
			if (mediaPlayer.getError() != null) {
				System.out.println((mediaPlayer.getError().getMessage() != null) ? mediaPlayer.getError().getMessage() : "Unable to play about animation for unknown reasons.");
			}
		}
		errored = true;
		
		mediaHBox.getChildren().clear();
		
		ImageView createdBy = new ImageView(new Image("/atChat/resources/images/created_by.png"));
		createdBy.setViewport(new Rectangle2D(0, 0, 480, 270));
		createdBy.prefWidth(480);
		createdBy.prefHeight(270);
		createdBy.minWidth(480);
		createdBy.minHeight(270);
		createdBy.maxWidth(480);
		createdBy.maxHeight(270);
		
		mediaHBox.getChildren().add(createdBy);
		((HBox) mPlay.getParent()).getChildren().removeAll(mRewind, mPlay, mFastForward);
		
	}
	
	private void setEndOfMedia() {
		endOfMedia = true;
		playState = STATE_REPLAY;
		if (!playHovered) {
			mPlay.setGraphic(new ImageView(replayImage));
		} else {
			mPlay.setGraphic(new ImageView(replayImageH));
		}
	}
	
	private void setToPlayButton() {
		playState = STATE_PLAY;
		if (!playHovered) {
			mPlay.setGraphic(new ImageView(playImage));
		} else {
			mPlay.setGraphic(new ImageView(playImageH));
		}
	}
	
	private void setToPauseButton() {
		playState = STATE_PAUSE;
		if (!playHovered) {
			mPlay.setGraphic(new ImageView(pauseImage));
		} else {
			mPlay.setGraphic(new ImageView(pauseImageH));
		}
	}
	
	private void playEnter() {
		playHovered = true;
		switch (playState) {
		case STATE_PLAY:
			mPlay.setGraphic(new ImageView(playImageH));
			break;
		case STATE_PAUSE:
			mPlay.setGraphic(new ImageView(pauseImageH));
			break;
		case STATE_REPLAY:
			mPlay.setGraphic(new ImageView(replayImageH));
			break;
		}
	}
	
	private void playExit() {
		playHovered = false;
		switch (playState) {
		case STATE_PLAY:
			mPlay.setGraphic(new ImageView(playImage));
			break;
		case STATE_PAUSE:
			mPlay.setGraphic(new ImageView(pauseImage));
			break;
		case STATE_REPLAY:
			mPlay.setGraphic(new ImageView(replayImage));
			break;
		}
	}
	
	@FXML protected void rewindClick(ActionEvent event) {
		if (mediaPlayer.getStatus() != MediaPlayer.Status.HALTED && !errored) {
			if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
				if (!endOfMedia) {
					mediaPlayer.seek(Duration.ZERO);
				} else {
					endOfMedia = false;
					mediaPlayer.stop();
					setToPlayButton();
				}
			} else {
				endOfMedia = false;
				mediaPlayer.stop();
				setToPlayButton();
			}
		}
	}
	
	@FXML protected void playClick(ActionEvent event) {
		if (!errored) {
			if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING || mediaPlayer.getStatus() == MediaPlayer.Status.STALLED) {
				if (!endOfMedia) {
					mediaPlayer.pause();
					setToPlayButton();
				} else {
					endOfMedia = false;
					mediaPlayer.seek(Duration.ZERO);
					setToPauseButton();
				}
			} else if (mediaPlayer.getStatus() == MediaPlayer.Status.READY || mediaPlayer.getStatus() == MediaPlayer.Status.STOPPED || mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
				if (endOfMedia) {
					endOfMedia = false;
					mediaPlayer.play();
					mediaPlayer.seek(Duration.ZERO);
				} else {
					mediaPlayer.play();
				}
				setToPauseButton();
			}
		}
	}
	
	@FXML protected void fastForwardClick(ActionEvent event) {
		if (mediaPlayer.getStatus() != MediaPlayer.Status.HALTED && mediaPlayer.getStatus() != MediaPlayer.Status.STALLED && mediaPlayer.getMedia().getDuration() != Duration.UNKNOWN && mediaPlayer.getMedia().getDuration() != Duration.INDEFINITE && !errored) {
			if (mediaPlayer.getStatus() != MediaPlayer.Status.STOPPED) { 
				mediaPlayer.seek(new Duration(mediaPlayer.getMedia().getDuration().toMillis() - 100));
				mediaPlayer.pause();
				setEndOfMedia();
			} else {
				mediaPlayer.play();
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						fastForwardClick(null);
					}
				});
			}
		}
	}
	
	protected void dismissClick() {
		if (mediaPlayer != null) { mediaPlayer.stop(); }
		thisStage.close();
	}

}
