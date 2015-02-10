package atChat;

import java.io.File;

import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;

public class FileSelector {
	private static final Image checked = new Image("/atChat/resources/images/checkBox_checked.png");
	private static final Image unChecked = new Image("/atChat/resources/images/checkBox.png");
	private static final Image overwriteImage = new Image("/atChat/resources/images/file_overwrite.png");
	private static final Image directoryImage = new Image("/atChat/resources/images/folder.png");
	
	private boolean selected = true;
	private final boolean isDir;
	
	private final String path;
	private final long size;
	
	private final TreeItem<String> treeItem;
	
	public FileSelector(String path, long size) {
		this.path = path;
		this.size = size;
		isDir = (path.endsWith(File.separator));
		
		treeItem = new TreeItem<String>(path);
	}
	
	public FileSelector(String path) {
		this.path = path;
		this.size = 0;
		isDir = (path.endsWith(File.separator));
		
		treeItem = null;
	}
	
	public boolean isDirectory() {
		return isDir;
	}
	
	public String getPath() {
		return path;
	}
	
	public boolean isSelected() {
		return selected;
	}
	
	public void setSelected(boolean selected) {
		this.selected = selected;
	}
	
	public Image getCheckedImage() {
		return checked;
	}
	
	public Image getUnCheckedImage() {
		return unChecked;
	}
	
	public Image getOverwriteImage() {
		return overwriteImage;
	}
	
	public Image getDirImage() {
		return directoryImage;
	}
	
	public TreeItem<String> getItem() {
		return treeItem;
	}
	
	public long getSize() {
		return size;
	}
}
