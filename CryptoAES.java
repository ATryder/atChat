package atChat;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

public class CryptoAES extends Crypt {
	private final Cipher encryptor;
	private final Cipher decryptor;
	
	protected final byte[] key;
	
	protected CryptoAES() throws Exception {
		KeyGenerator keyGen = KeyGenerator.getInstance("AES");
		keyGen.init(128);
		key = keyGen.generateKey().getEncoded();
		
		SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
		
		encryptor = Cipher.getInstance("AES");
		encryptor.init(Cipher.ENCRYPT_MODE, keySpec);
		
		decryptor = Cipher.getInstance("AES");
		decryptor.init(Cipher.DECRYPT_MODE, keySpec);
	}
	
	protected CryptoAES(byte[] key) throws Exception {
		this.key = key;
		
		SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
		
		encryptor = Cipher.getInstance("AES");
		encryptor.init(Cipher.ENCRYPT_MODE, keySpec);
		
		decryptor = Cipher.getInstance("AES");
		decryptor.init(Cipher.DECRYPT_MODE, keySpec);
	}
	
	@Override
	protected byte[] encrypt(final String text) {
		try {
			return encryptor.doFinal(text.getBytes());
		} catch (Exception e) {
			return null;
		}
	}
	
	@Override
	protected byte[] encrypt(final byte[] bytes) {
		try {
			return encryptor.doFinal(bytes);
		} catch (Exception e) {
			return null;
		}
	}
	
	@Override
	protected String decrypt(final byte[] bytes) {
		try {
			return new String(decryptor.doFinal(bytes));
		} catch (Exception e) {
			return null;
		}
	}
	
	@Override
	protected byte[] decryptBytes(final byte[] bytes) {
		if (decryptor != null) {
			try {
				return decryptor.doFinal(bytes);
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}
}
