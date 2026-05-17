package name.soulayrol.rhaa.sholi.sync.android;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.security.GeneralSecurityException;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import name.soulayrol.rhaa.sholi.sync.credentials.EncryptedPreferences;
import name.soulayrol.rhaa.sholi.sync.credentials.EncryptedPreferencesFactory;

public final class AndroidEncryptedPreferencesFactory implements EncryptedPreferencesFactory {

    private final Context context;

    public AndroidEncryptedPreferencesFactory(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.context = context;
    }

    @Override
    public String storageEngineClassName() {
        return EncryptedSharedPreferences.class.getName();
    }

    @Override
    public EncryptedPreferences open(String fileName) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            SharedPreferences preferences = EncryptedSharedPreferences.create(
                    context,
                    fileName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            return new SharedPreferencesEncryptedPreferences(preferences);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Could not initialize AndroidX EncryptedSharedPreferences", e);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not initialize AndroidX EncryptedSharedPreferences", e);
        }
    }

    private static final class SharedPreferencesEncryptedPreferences
            implements EncryptedPreferences {
        private final SharedPreferences preferences;

        private SharedPreferencesEncryptedPreferences(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override
        public void putString(String key, String value) {
            preferences.edit().putString(key, value).apply();
        }

        @Override
        public String getString(String key) {
            return preferences.getString(key, null);
        }

        @Override
        public void remove(String key) {
            preferences.edit().remove(key).apply();
        }
    }
}
