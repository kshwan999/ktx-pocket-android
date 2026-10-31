package kr.personal.ktxpocket;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.json.JSONObject;

/** Only ciphertext is persisted. No plaintext fallback and no exported encryption key. */
final class CredentialStore {
    private static final String ALIAS = "ktx-pocket.login.v1";
    private final SharedPreferences preferences;

    static final class Saved {
        final String username, password;
        Saved(String username, String password) { this.username = username; this.password = password; }
    }

    CredentialStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("remembered_credentials", Context.MODE_PRIVATE);
    }

    boolean hasSaved() { return preferences.contains("payload"); }

    Saved load() throws Exception {
        String payload = preferences.getString("payload", null);
        if (payload == null) return new Saved("", "");
        JSONObject data = new JSONObject(CredentialCipher.decrypt(key(false), payload));
        return new Saved(data.getString("username"), data.optString("password", ""));
    }

    void save(String username, String password, boolean rememberId, boolean rememberPassword) throws Exception {
        if (!rememberId || username.isEmpty()) { clear(); return; }
        JSONObject data = new JSONObject().put("username", username).put("password", rememberPassword ? password : "");
        String payload = CredentialCipher.encrypt(key(true), data.toString());
        if (!preferences.edit().putString("payload", payload).commit()) throw new IOException("Credential storage failed");
    }

    void forgetPassword() throws Exception {
        Saved previous;
        try { previous = load(); }
        catch (Exception error) { clear(); return; }
        clear();
        if (!previous.username.isEmpty()) save(previous.username, "", true, false);
    }

    void clear() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        // Removing the key also makes an old ciphertext copy unusable.
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS);
        if (!preferences.edit().clear().commit()) throw new IOException("Credential removal failed");
    }

    private SecretKey key(boolean create) throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        if (!create) throw new UnrecoverableKeyException("Saved credential key is unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
}
