package atChat;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

public class CryptoRSA extends Crypt {
	private final Cipher encryptor;
	private final Cipher decryptor;
	
	protected final byte[] pubKey;
	
	protected CryptoRSA() throws Exception {
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(2048);
		KeyPair pair = kpg.genKeyPair();
		
		KeyFactory kFact = KeyFactory.getInstance("RSA");
		RSAPublicKeySpec pubKeySpec = kFact.getKeySpec(pair.getPublic(), RSAPublicKeySpec.class);
		PublicKey pKey = kFact.generatePublic(pubKeySpec);
		pubKey = pair.getPublic().getEncoded();
		
		encryptor = Cipher.getInstance("RSA");
		encryptor.init(Cipher.ENCRYPT_MODE, pKey);
		
		decryptor = Cipher.getInstance("RSA");
		decryptor.init(Cipher.DECRYPT_MODE, kFact.generatePrivate(kFact.getKeySpec(pair.getPrivate(), RSAPrivateKeySpec.class)));
	}
	
	protected CryptoRSA(byte[] pubKey) throws Exception {
		this.pubKey = pubKey;
		
		KeyFactory kFact = KeyFactory.getInstance("RSA");
		PublicKey pKey = kFact.generatePublic(new X509EncodedKeySpec(pubKey));
		
		encryptor = Cipher.getInstance("RSA");
		encryptor.init(Cipher.ENCRYPT_MODE, pKey);
		
		decryptor = null;
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
		if (decryptor != null) {
			try {
				return new String(decryptor.doFinal(bytes));
			} catch (Exception e) {
				return null;
			}
		}
		return null;
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
