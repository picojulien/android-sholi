package story7;

import java.util.HashMap;
import java.util.Map;

import name.soulayrol.rhaa.sholi.sync.credentials.CredentialStore;
import name.soulayrol.rhaa.sholi.sync.credentials.EncryptedPreferences;
import name.soulayrol.rhaa.sholi.sync.credentials.EncryptedPreferencesFactory;
import name.soulayrol.rhaa.sholi.sync.credentials.EncryptedSharedPreferencesCredentialStore;
import name.soulayrol.rhaa.sholi.sync.credentials.InMemoryCredentialStore;
import name.soulayrol.rhaa.sholi.sync.credentials.SyncCredentialProvider;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentials;

public final class CredentialStoreStoryTest {

    private CredentialStoreStoryTest() {
    }

    public static void run() {
        verifyInMemoryCredentialStoreCanDriveSyncLogic();
        verifyEncryptedStoreRequiresAndroidXEncryptedSharedPreferences();
        verifyEncryptedStorePersistsCredentials();
    }

    private static void verifyInMemoryCredentialStoreCanDriveSyncLogic() {
        CredentialStore store = new InMemoryCredentialStore();
        WebDavCredentials credentials = new WebDavCredentials("alice", "token-123");

        store.save(credentials);

        SyncCredentialProvider provider = new SyncCredentialProvider(store);
        WebDavCredentials loaded = provider.requireCredentials();

        assertEquals("alice", loaded.getUsername(), "username");
        assertEquals("token-123", loaded.getPasswordOrToken(), "secret");
    }

    private static void verifyEncryptedStoreRequiresAndroidXEncryptedSharedPreferences() {
        RecordingFactory wrongFactory = new RecordingFactory("android.content.SharedPreferences");

        try {
            new EncryptedSharedPreferencesCredentialStore(wrongFactory);
            throw new AssertionError("Expected store construction to fail when factory is not AndroidX encrypted prefs");
        } catch (IllegalArgumentException expected) {
            // Expected path.
        }
    }

    private static void verifyEncryptedStorePersistsCredentials() {
        RecordingFactory factory = new RecordingFactory(
                EncryptedSharedPreferencesCredentialStore.ANDROIDX_ENCRYPTED_SHARED_PREFERENCES_CLASS);
        EncryptedSharedPreferencesCredentialStore store =
                new EncryptedSharedPreferencesCredentialStore(factory);

        WebDavCredentials credentials = new WebDavCredentials("bob", "app-token");
        store.save(credentials);

        WebDavCredentials loaded = store.load();
        assertEquals("bob", loaded.getUsername(), "encrypted username");
        assertEquals("app-token", loaded.getPasswordOrToken(), "encrypted secret");
        assertEquals("sholi_webdav_credentials", factory.getOpenedFileName(), "encrypted preference file");
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Unexpected " + label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static final class RecordingFactory implements EncryptedPreferencesFactory {
        private final String storageEngineClassName;
        private final Map<String, String> values = new HashMap<String, String>();
        private String openedFileName;

        private RecordingFactory(String storageEngineClassName) {
            this.storageEngineClassName = storageEngineClassName;
        }

        @Override
        public String storageEngineClassName() {
            return storageEngineClassName;
        }

        @Override
        public EncryptedPreferences open(String fileName) {
            openedFileName = fileName;
            return new MapBackedEncryptedPreferences(values);
        }

        String getOpenedFileName() {
            return openedFileName;
        }
    }

    private static final class MapBackedEncryptedPreferences implements EncryptedPreferences {
        private final Map<String, String> values;

        private MapBackedEncryptedPreferences(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public void putString(String key, String value) {
            values.put(key, value);
        }

        @Override
        public String getString(String key) {
            return values.get(key);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }
    }
}
