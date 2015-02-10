package atChat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SocketIO {
	private final int buffer;
	
	private ByteBuffer intBytes = ByteBuffer.allocate(4);
	private ByteBuffer mBytes;
	private int intCount = 0;
	private int mCount = 0;
	private int mSize = 0;
	
	private long fileSize = 0;
	private long currentFileSize = 0;
	private int lastWritten = 0;
	
	private ByteBuffer fileBuffer;
	
	public SocketIO(int buffer) {
		this.buffer = buffer;
		fileBuffer = ByteBuffer.allocate(buffer);
	}
	
	public SocketIO() {
		buffer = 1024 * 8;
		fileBuffer = ByteBuffer.allocate(buffer);
	}
	
	public String readBytes(final SocketChannel s, final Crypt crypt) throws IOException {
		int bytesRead = 0;
		
		if (mSize == 0) {
			do {
				bytesRead = s.read(intBytes);
				intCount += (bytesRead > -1) ? bytesRead : 0;
				if (intCount >= 4) {
					break;
				}
			} while (bytesRead > 0);
			
			if (bytesRead == -1) {
				intCount = 0;
				intBytes = ByteBuffer.allocate(4);
				
				return "\n";
			}
			
			
			if (intCount == 4) {
				intBytes.flip();
				mSize = intBytes.getInt();
				mBytes = ByteBuffer.allocate(mSize);
				intBytes.clear();
				intCount = 0;
				
				bytesRead = 0;
				
				do {
					bytesRead = s.read(mBytes);
					mCount += (bytesRead > -1) ? bytesRead : 0;
					if (mCount >= mSize) {
						break;
					}
				} while (bytesRead > 0);
				
				if (bytesRead == -1) {
					mBytes = null;
					mCount = 0;
					mSize = 0;
					
					return "\n";
				}
				
				
				if (mCount == mSize) {
					mBytes.flip();
					String msg = crypt.decrypt(mBytes.array());
					
					mBytes = null;
					mCount = 0;
					mSize = 0;
					
					return msg;
				}
			}
		} else {
			do {
				bytesRead = s.read(mBytes);
				mCount += (bytesRead > -1) ? bytesRead : 0;
				if (mCount >= mSize) {
					break;
				}
			} while (bytesRead > 0);
			
			if (bytesRead == -1) {
				mBytes = null;
				mCount = 0;
				mSize = 0;
				
				return "\n";
			}
			
			
			if (mCount == mSize) {
				mBytes.flip();
				String msg = crypt.decrypt(mBytes.array());
				
				mBytes = null;
				mCount = 0;
				mSize = 0;
				
				return msg;
			}
		}
		
		return null;
	}
	
	public byte[] readBytesB(final SocketChannel s, final Crypt crypt) throws IOException {
		int bytesRead = 0;
		
		if (mSize == 0) {
			do {
				bytesRead = s.read(intBytes);
				intCount += (bytesRead > -1) ? bytesRead : 0;
				if (intCount >= 4) {
					break;
				}
			} while (bytesRead > 0);
			
			if (bytesRead == -1) {
				intCount = 0;
				intBytes = ByteBuffer.allocate(4);
				
				return new byte[0];
			}
			
			
			if (intCount == 4) {
				intBytes.flip();
				mSize = intBytes.getInt();
				mBytes = ByteBuffer.allocate(mSize);
				intBytes.clear();
				intCount = 0;
				
				bytesRead = 0;
				
				do {
					bytesRead = s.read(mBytes);
					mCount += (bytesRead > -1) ? bytesRead : 0;
					if (mCount >= mSize) {
						break;
					}
				} while (bytesRead > 0);
				
				if (bytesRead == -1) {
					mBytes = null;
					mCount = 0;
					mSize = 0;
					
					return new byte[0];
				}
				
				
				if (mCount == mSize) {
					mBytes.flip();
					byte[] dBytes = crypt.decryptBytes(mBytes.array());
					
					mBytes = null;
					mCount = 0;
					mSize = 0;
					
					return dBytes;
				}
			}
		} else {
			do {
				bytesRead = s.read(mBytes);
				mCount += (bytesRead > -1) ? bytesRead : 0;
				if (mCount >= mSize) {
					break;
				}
			} while (bytesRead > 0);
			
			if (bytesRead == -1) {
				mBytes = null;
				mCount = 0;
				mSize = 0;
				
				return new byte[0];
			}
			
			
			if (mCount == mSize) {
				mBytes.flip();
				byte[] dBytes = crypt.decryptBytes(mBytes.array());
				
				mBytes = null;
				mCount = 0;
				mSize = 0;
				
				return dBytes;
			}
		}
		
		return null;
	}
	
	public byte[] readBytes(final SocketChannel s) throws IOException {
		int bytesRead = 0;
		
		if (mSize == 0) {
			do {
				bytesRead = s.read(intBytes);
				intCount += (bytesRead > -1) ? bytesRead : 0;
				if (intCount >= 4) {
					break;
				}
			} while (bytesRead > 0);
			
			if (bytesRead == -1) {
				intCount = 0;
				intBytes = ByteBuffer.allocate(4);
				
				return new byte[0];
			}
			
			
			if (intCount == 4) {
				intBytes.flip();
				mSize = intBytes.getInt();
				mBytes = ByteBuffer.allocate(mSize);
				intBytes.clear();
				intCount = 0;
				
				bytesRead = 0;
				
				do {
					bytesRead = s.read(mBytes);
					mCount += (bytesRead > -1) ? bytesRead : 0;
					if (mCount >= mSize) {
						break;
					}
				} while (bytesRead > 0);
				
				if (bytesRead == -1) {
					mBytes = null;
					mCount = 0;
					mSize = 0;
					
					return new byte[0];
				}
				
				
				if (mCount == mSize) {
					mBytes.flip();
					byte[] a = mBytes.array();
					mBytes = null;
					mCount = 0;
					mSize = 0;
					
					return a;
				}
			}
		} else {
			do {
				bytesRead = s.read(mBytes);
				mCount += (bytesRead > -1) ? bytesRead : 0;
				if (mCount >= mSize) {
					break;
				}
			} while (bytesRead > 0);
			
			if (bytesRead == -1) {
				mBytes = null;
				mCount = 0;
				mSize = 0;
				
				return new byte[0];
			}
			
			
			if (mCount == mSize) {
				mBytes.flip();
				byte[] a = mBytes.array();
				mBytes = null;
				mCount = 0;
				mSize = 0;
				
				return a;
			}
		}
		
		return null;
	}	
	
	public void writeBytes(final SocketChannel s, final String text, final Crypt crypt) throws IOException {
		byte[] bytes = crypt.encrypt(text);
		if (bytes != null) {
			ByteBuffer b = ByteBuffer.allocate(bytes.length + 4);
			b.putInt(bytes.length);
			b.put(bytes);
			b.flip();
			while (b.hasRemaining()) {
				s.write(b);
			}
		}
	}
	
	public void writeBytes(final SocketChannel s, final byte[] bytesToSend, final Crypt crypt) throws IOException {
		byte[] bytes = crypt.encrypt(bytesToSend);
		if (bytes != null) {
			ByteBuffer b = ByteBuffer.allocate(bytes.length + 4);
			b.putInt(bytes.length);
			b.put(bytes);
			b.flip();
			while (b.hasRemaining()) {
				s.write(b);
			}
		}
	}
	
	public void writeBytes(final SocketChannel s, final byte[] bytes) throws IOException {
		ByteBuffer b = ByteBuffer.allocate(bytes.length + 4);
		b.putInt(bytes.length);
		b.put(bytes);
		b.flip();
		while (b.hasRemaining()) {
			s.write(b);
		}
	}
	
	protected void resetFileWriter() {
		fileSize = 0;
		currentFileSize = 0;
		lastWritten = 0;
	}
	
	protected void setFileWriter(final long fileSize) {
		this.fileSize = fileSize;
		currentFileSize = 0;
		lastWritten = 0;
	}
	
	public long getCurrentLength() {
		return currentFileSize;
	}
	
	public int getLastWriteAmount() {
		return lastWritten;
	}
	
	protected boolean readFile(final SocketChannel s, final BufferedOutputStream out) throws IOException {
		int n = -1;
		if ((n = s.read(fileBuffer)) != -1) {
			if (n > 0) {
				out.write(fileBuffer.array(), 0, n);
				out.flush();
				fileBuffer.clear();
			}
			lastWritten = n;
			currentFileSize += n;
			if (currentFileSize >= fileSize) {
				resetFileWriter();
				return true;
			}
		} else {
			lastWritten = 0;
		}
		
		return false;
	}
	
	protected int writeFile(final SocketChannel s, final BufferedInputStream in) throws IOException {
		byte[] buffer = new byte[this.buffer]; //8192
		int n = -1;
		if ((n = in.read(buffer)) != -1) {
			fileBuffer.put(buffer, 0, n);
			fileBuffer.flip();
			while(fileBuffer.hasRemaining()) {
				s.write(fileBuffer);
			}
			
			if (n > 0) {
				fileBuffer.clear();
			}

			return n;
		} else {
			return -1;
		}
	}
}
