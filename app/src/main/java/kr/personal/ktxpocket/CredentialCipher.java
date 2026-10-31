package kr.personal.ktxpocket;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Versioned authenticated encryption; keys are supplied by Android Keystore. */
final class CredentialCipher {
    private static final byte[] AAD = "kr.personal.ktxpocket.credentials.v1".getBytes(StandardCharsets.UTF_8);

    static String encrypt(SecretKey key, String plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.updateAAD(AAD);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return "1:" + Base64.getEncoder().encodeToString(cipher.getIV()) + ":" + Base64.getEncoder().encodeToString(encrypted);
    }

    static String decrypt(SecretKey key, String payload) throws GeneralSecurityException {
        try {
            String[] parts = payload.split(":", -1);
            if (parts.length != 3 || !"1".equals(parts[0])) throw new IllegalArgumentException();
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            if (iv.length != 12 || encrypted.length < 16) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw new GeneralSecurityException("Invalid saved credential format", error);
        }
    }
}
