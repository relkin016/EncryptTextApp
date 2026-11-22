package dk.meznik.jan.encrypttext.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Saved passwords, encrypted with an AES-GCM key from the Android Keystore.
 * The key can only be used after the user confirms with a fingerprint or the screen lock,
 * so every encrypt/decrypt goes through {@link AuthGate}.
 */
public class PasswordVault {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "saved_passwords";
    private static final String PREFS = "saved_passwords";
    private static final String PREF_ENTRIES = "entries";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;

    // Before Android 11 the key can't be bound to a single operation together with the
    // screen lock, so it stays usable for a short window after authentication instead.
    static final int AUTH_WINDOW_SECONDS = 15;

    public static final class Entry {
        public final String name;
        final byte[] iv;
        final byte[] data;

        Entry(String name, byte[] iv, byte[] data) {
            this.name = name;
            this.iv = iv;
            this.data = data;
        }
    }

    private final SharedPreferences prefs;

    public PasswordVault(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(PREF_ENTRIES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                entries.add(new Entry(o.getString("name"),
                        Base64.decode(o.getString("iv"), Base64.NO_WRAP),
                        Base64.decode(o.getString("data"), Base64.NO_WRAP)));
            }
        } catch (JSONException | IllegalArgumentException e) {
            return Collections.emptyList();
        }
        return entries;
    }

    public boolean isEmpty() {
        return list().isEmpty();
    }

    public Cipher encryptCipher() throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key(true));
        return cipher;
    }

    public Cipher decryptCipher(Entry entry) throws GeneralSecurityException {
        SecretKey key = key(false);
        if (key == null) {
            // Entries without a key (e.g. restored from elsewhere) can never be decrypted.
            throw new KeyPermanentlyInvalidatedException();
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, entry.iv));
        return cipher;
    }

    /** Encrypts and stores the password, replacing an entry with the same name. */
    public void add(String name, String password, Cipher cipher) throws GeneralSecurityException {
        byte[] data = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        List<Entry> entries = new ArrayList<>(list());
        removeByName(entries, name);
        entries.add(new Entry(name, cipher.getIV(), data));
        save(entries);
    }

    public String reveal(Entry entry, Cipher cipher) throws GeneralSecurityException {
        return new String(cipher.doFinal(entry.data), StandardCharsets.UTF_8);
    }

    public void remove(String name) {
        List<Entry> entries = new ArrayList<>(list());
        removeByName(entries, name);
        save(entries);
    }

    public void clear() {
        prefs.edit().clear().apply();
        try {
            keyStore().deleteEntry(KEY_ALIAS);
        } catch (GeneralSecurityException ignored) {
        }
    }

    private static void removeByName(List<Entry> entries, String name) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).name.equals(name)) entries.remove(i);
        }
    }

    private void save(List<Entry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (Entry e : entries) {
                array.put(new JSONObject()
                        .put("name", e.name)
                        .put("iv", Base64.encodeToString(e.iv, Base64.NO_WRAP))
                        .put("data", Base64.encodeToString(e.data, Base64.NO_WRAP)));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        prefs.edit().putString(PREF_ENTRIES, array.toString()).apply();
    }

    private static KeyStore keyStore() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        try {
            keyStore.load(null);
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
        return keyStore;
    }

    private static SecretKey key(boolean create) throws GeneralSecurityException {
        Key key = keyStore().getKey(KEY_ALIAS, null);
        if (key instanceof SecretKey) return (SecretKey) key;
        if (!create) return null;

        KeyGenParameterSpec.Builder spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            spec.setUserAuthenticationParameters(0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL);
        } else {
            spec.setUserAuthenticationValidityDurationSeconds(AUTH_WINDOW_SECONDS);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(spec.build());
        return generator.generateKey();
    }
}
