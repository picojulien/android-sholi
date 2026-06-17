package name.soulayrol.rhaa.sholi.sync.credentials;

public final class EncryptedSharedPreferencesCredentialStore implements CredentialStore {

    public static final String ANDROIDX_ENCRYPTED_SHARED_PREFERENCES_CLASS =
            "androidx.security.crypto.EncryptedSharedPreferences";
    public static final String PREFERENCES_FILE = "sholi_webdav_credentials";
    public static final String SECRET_PREFERENCE_KEY = "webdav_password_or_token";

    private static final String KEY_USERNAME = "webdav_username";

    private final EncryptedPreferences preferences;

    public EncryptedSharedPreferencesCredentialStore(EncryptedPreferencesFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }
        if (!ANDROIDX_ENCRYPTED_SHARED_PREFERENCES_CLASS.equals(factory.storageEngineClassName())) {
            throw new IllegalArgumentException(
                    "Only AndroidX EncryptedSharedPreferences can be used in production");
        }
        this.preferences = factory.open(PREFERENCES_FILE);
    }

    @Override
    public void save(WebDavCredentials credentials) {
        if (credentials == null) {
            throw new IllegalArgumentException("credentials must not be null");
        }
        preferences.putString(KEY_USERNAME, credentials.getUsername());
        preferences.putString(SECRET_PREFERENCE_KEY, credentials.getPasswordOrToken());
    }

    @Override
    public WebDavCredentials load() {
        String username = preferences.getString(KEY_USERNAME);
        String secret = preferences.getString(SECRET_PREFERENCE_KEY);
        if (username == null || secret == null) {
            return null;
        }
        return new WebDavCredentials(username, secret);
    }

    @Override
    public void clear() {
        preferences.remove(KEY_USERNAME);
        preferences.remove(SECRET_PREFERENCE_KEY);
    }
}
