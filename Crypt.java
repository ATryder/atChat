package atChat;

public abstract class Crypt {
	
	protected abstract byte[] encrypt(final String text);
	
	protected abstract byte[] encrypt(final byte[] bytes);
	
	protected abstract String decrypt(final byte[] bytes);
	
	protected abstract byte[] decryptBytes(final byte[] bytes);
}
