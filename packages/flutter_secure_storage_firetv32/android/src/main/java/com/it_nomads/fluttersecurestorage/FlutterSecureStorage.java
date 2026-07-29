package com.it_nomads.fluttersecurestorage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import com.it_nomads.fluttersecurestorage.ciphers.StorageCipher;
import com.it_nomads.fluttersecurestorage.ciphers.StorageCipherFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class FlutterSecureStorage {

    private final String TAG = "SecureStorageAndroid";
    private final Charset charset;
    private final Context applicationContext;
    protected String ELEMENT_PREFERENCES_KEY_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg";
    protected Map<String, Object> options;
    private String SHARED_PREFERENCES_NAME = "FlutterSecureStorage";
    private SharedPreferences preferences;
    private StorageCipher storageCipher;
    private StorageCipherFactory storageCipherFactory;

    public FlutterSecureStorage(Context context, Map<String, Object> options) {
        this.options = options;
        applicationContext = context.getApplicationContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            charset = StandardCharsets.UTF_8;
        } else {
            //noinspection CharsetObjectCanBeUsed
            charset = Charset.forName("UTF-8");
        }
    }

    @SuppressWarnings({"ConstantConditions"})
    boolean getResetOnError() {
        return options.containsKey("resetOnError") && options.get("resetOnError").equals("true");
    }

    @SuppressWarnings({"ConstantConditions"})
    private boolean getUseEncryptedSharedPreferences() {
        // Fire OS 5 is API 22. Keep the plugin's RSA/AES storage-cipher path
        // and avoid AndroidX Security, which requires the API 23 keystore path.
        return false;
    }

    public boolean containsKey(String key) {
        ensureInitialized();
        return preferences.contains(key);
    }

    public String addPrefixToKey(String key) {
        return ELEMENT_PREFERENCES_KEY_PREFIX + "_" + key;
    }

    public String read(String key) throws Exception {
        ensureInitialized();

        String rawValue = preferences.getString(key, null);
        if (getUseEncryptedSharedPreferences()) {
            return rawValue;
        }
        return decodeRawValue(rawValue);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> readAll() throws Exception {
        ensureInitialized();

        Map<String, String> raw = (Map<String, String>) preferences.getAll();

        Map<String, String> all = new HashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String keyWithPrefix = entry.getKey();
            if (keyWithPrefix.contains(ELEMENT_PREFERENCES_KEY_PREFIX)) {
                String key = entry.getKey().replaceFirst(ELEMENT_PREFERENCES_KEY_PREFIX + '_', "");
                if (getUseEncryptedSharedPreferences()) {
                    all.put(key, entry.getValue());
                } else {
                    String rawValue = entry.getValue();
                    String value = decodeRawValue(rawValue);

                    all.put(key, value);
                }
            }
        }
        return all;
    }

    public void write(String key, String value) throws Exception {
        ensureInitialized();

        SharedPreferences.Editor editor = preferences.edit();

        if (getUseEncryptedSharedPreferences()) {
            editor.putString(key, value);
        } else {
            byte[] result = storageCipher.encrypt(value.getBytes(charset));
            editor.putString(key, Base64.encodeToString(result, 0));
        }
        editor.apply();
    }

    public void delete(String key) {
        ensureInitialized();

        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(key);
        editor.apply();
    }

    public void deleteAll() {
        ensureInitialized();

        final SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        if (!getUseEncryptedSharedPreferences()) {
            storageCipherFactory.storeCurrentAlgorithms(editor);
        }
        editor.apply();
    }

   protected void ensureOptions(){
       if (options.containsKey("sharedPreferencesName") && !((String) options.get("sharedPreferencesName")).isEmpty()) {
           SHARED_PREFERENCES_NAME = (String) options.get("sharedPreferencesName");
       }

       if (options.containsKey("preferencesKeyPrefix") && !((String) options.get("preferencesKeyPrefix")).isEmpty()) {
           ELEMENT_PREFERENCES_KEY_PREFIX = (String) options.get("preferencesKeyPrefix");
       }
    }
    @SuppressWarnings({"ConstantConditions"})
    private void ensureInitialized() {
        // Check if already initialized.
        // TODO: Disable for now because this will break mixed usage of secureSharedPreference
//        if (preferences != null) return;

        ensureOptions();

        SharedPreferences nonEncryptedPreferences = applicationContext.getSharedPreferences(
                SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
        if (storageCipher == null) {
            try {
                initStorageCipher(nonEncryptedPreferences);

            } catch (Exception e) {
                Log.e(TAG, "StorageCipher initialization failed", e);
            }
        }
        preferences = nonEncryptedPreferences;
    }

    private void initStorageCipher(SharedPreferences source) throws Exception {
        storageCipherFactory = new StorageCipherFactory(source, options);
        if (getUseEncryptedSharedPreferences()) {
            storageCipher = storageCipherFactory.getSavedStorageCipher(applicationContext);
        } else if (storageCipherFactory.requiresReEncryption()) {
            reEncryptPreferences(storageCipherFactory, source);
        } else {
            storageCipher = storageCipherFactory.getCurrentStorageCipher(applicationContext);
        }
    }

    private void reEncryptPreferences(StorageCipherFactory storageCipherFactory, SharedPreferences source) throws Exception {
        try {
            storageCipher = storageCipherFactory.getSavedStorageCipher(applicationContext);
            final Map<String, String> cache = new HashMap<>();
            for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();
                if (v instanceof String && key.contains(ELEMENT_PREFERENCES_KEY_PREFIX)) {
                    final String decodedValue = decodeRawValue((String) v);
                    cache.put(key, decodedValue);
                }
            }
            storageCipher = storageCipherFactory.getCurrentStorageCipher(applicationContext);
            final SharedPreferences.Editor editor = source.edit();
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                byte[] result = storageCipher.encrypt(entry.getValue().getBytes(charset));
                editor.putString(entry.getKey(), Base64.encodeToString(result, 0));
            }
            storageCipherFactory.storeCurrentAlgorithms(editor);
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "re-encryption failed", e);
            storageCipher = storageCipherFactory.getSavedStorageCipher(applicationContext);
        }
    }

    private void checkAndMigrateToEncrypted(SharedPreferences source, SharedPreferences target) {
        try {
            for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();
                if (v instanceof String && key.contains(ELEMENT_PREFERENCES_KEY_PREFIX)) {
                    final String decodedValue = decodeRawValue((String) v);
                    target.edit().putString(key, (decodedValue)).apply();
                    source.edit().remove(key).apply();
                }
            }
            final SharedPreferences.Editor sourceEditor = source.edit();
            storageCipherFactory.removeCurrentAlgorithms(sourceEditor);
            sourceEditor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Data migration failed", e);
        }
    }

    private String decodeRawValue(String value) throws Exception {
        if (value == null) {
            return null;
        }
        byte[] data = Base64.decode(value, 0);
        byte[] result = storageCipher.decrypt(data);

        return new String(result, charset);
    }
}
