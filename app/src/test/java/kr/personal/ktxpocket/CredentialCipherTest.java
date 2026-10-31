package kr.personal.ktxpocket;

import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.Test;
import static org.junit.Assert.*;

public class CredentialCipherTest {
    private SecretKey key() throws Exception { KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(256); return generator.generateKey(); }

    @Test public void credentialsRoundTripWithoutPlaintextStorage() throws Exception {
        SecretKey key = key();
        String original = "{\"username\":\"test-user\",\"password\":\"가상 암호 🔐\"}";
        String stored = CredentialCipher.encrypt(key, original);
        assertFalse(stored.contains("test-user"));
        assertFalse(stored.contains("가상 암호"));
        assertEquals(original, CredentialCipher.decrypt(key, stored));
    }

    @Test public void eachSaveUsesFreshNonce() throws Exception {
        SecretKey key = key();
        String first = CredentialCipher.encrypt(key, "same test input");
        String second = CredentialCipher.encrypt(key, "same test input");
        assertNotEquals(first.split(":")[1], second.split(":")[1]);
        assertNotEquals(first, second);
    }

    @Test public void wrongKeyCannotRecoverSavedCredentials() throws Exception {
        String stored = CredentialCipher.encrypt(key(), "synthetic test password");
        assertThrows(GeneralSecurityException.class, () -> CredentialCipher.decrypt(key(), stored));
    }

    @Test public void alteredCiphertextIsRejected() throws Exception {
        SecretKey key = key();
        String[] parts = CredentialCipher.encrypt(key, "synthetic test password").split(":");
        byte[] bytes = Base64.getDecoder().decode(parts[2]); bytes[0] ^= 1;
        String changed = parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(bytes);
        assertThrows(GeneralSecurityException.class, () -> CredentialCipher.decrypt(key, changed));
    }

    @Test public void invalidOrUnsupportedFormatIsRejected() throws Exception {
        SecretKey key = key();
        for (String value : new String[]{"plaintext", "2:abc:abc", "1:%%:abc", "1:AA==:AA=="})
            assertThrows(GeneralSecurityException.class, () -> CredentialCipher.decrypt(key, value));
    }
}
