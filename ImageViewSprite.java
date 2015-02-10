package atChat;

import javafx.animation.AnimationTimer;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class ImageViewSprite extends AnimationTimer {
	private final ImageView imageView;
	
	private final int totalFrames;
	private final float fps;
	
	private final int cols;
	private final int rows;
	
	private final int frameWidth;
	private final int frameHeight;
	
	private int currentCol = 0;
	private int currentRow = 0;
	
	private long lastFrame = 0;
	
	public ImageViewSprite(ImageView imageView, Image image, int columns, int rows, int totalFrames, int frameWidth, int frameHeight, float framesPerSecond) {
		this.imageView = imageView;
		imageView.setImage(image);
		imageView.setViewport(new Rectangle2D(0, 0, frameWidth, frameHeight));
		
		cols = columns;
		this.rows = rows;
		this.totalFrames = totalFrames;
		this.frameWidth = frameWidth;
		this.frameHeight = frameHeight;
		fps = framesPerSecond;
		
		lastFrame = System.nanoTime();
	}
	
	@Override
	public void handle(long now) {
		int frameJump = (int) Math.floor((now - lastFrame) / (1000000000 / fps));
		
		if (frameJump >= 1) {
			lastFrame = now;
			int addRows = (int) Math.floor((float) frameJump / (float) cols);
			int frameAdd = frameJump - (addRows * cols);

			if (currentCol + frameAdd >= cols) {
				currentRow += addRows + 1;
				currentCol = frameAdd - (cols - currentCol);
			} else {
				currentRow += addRows;
				currentCol += frameAdd;
			}
			currentRow = (currentRow >= rows) ? currentRow - ((int) Math.floor((float) currentRow / rows) * rows) : currentRow;
			if ((currentRow * cols) + currentCol >= totalFrames) {
				currentRow = 0;
				currentCol = Math.abs(currentCol - (totalFrames - (int) (Math.floor((float) totalFrames / cols) * cols)));
			}
			
			imageView.setViewport(new Rectangle2D(currentCol * frameWidth, currentRow * frameHeight, frameWidth, frameHeight));
		}
	}
}
