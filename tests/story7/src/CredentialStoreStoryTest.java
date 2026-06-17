package story7;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import name.soulayrol.rhaa.sholi.sync.credentials.AndroidXEncryptedPreferencesRuntime;
import name.soulayrol.rhaa.sholi.sync.credentials.AndroidXEncryptedSharedPreferencesFactory;
import name.soulayrol.rhaa.sholi.sync.credentials.CredentialStore;
import name.soulayrol.rhaa.sholi.sync.credentials.CredentialStoreFactory;
import name.soulayrol.rhaa.sholi.sync.credentials.CredentialMetadataStore;
import name.soulayrol.rhaa.sholi.sync.credentials.DatabaseBackedCredentialStore;
import name.soulayrol.rhaa.sholi.sync.credentials.EncryptedSharedPreferencesCredentialStore;
import name.soulayrol.rhaa.sholi.sync.credentials.SyncCredentialProvider;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentials;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentialMetadata;

public final class CredentialStoreStoryTest {

    private CredentialStoreStoryTest() {
    }

    public static void run() throws Exception {
        verifySyncLogicUsesCredentialStoreAbstraction();
        verifyInMemoryStoreLivesInTestsOnly();
        verifyEncryptedStoreUsesAndroidXFactory();
        verifyProductionStoreFactoryCreatesDatabaseBackedEncryptedStore();
        verifyProductionAndroidWiringUsesDatabaseAndAndroidX();
    }

    private static void verifySyncLogicUsesCredentialStoreAbstraction() {
        FakeCredentialStore fakeStore = new FakeCredentialStore();
        WebDavCredentials credentials = new WebDavCredentials("alice", "token-123");
        fakeStore.save(credentials);

        SyncCredentialProvider provider = new SyncCredentialProvider(fakeStore);
        WebDavCredentials loaded = provider.requireCredentials();

        assertEquals("alice", loaded.getUsername(), "username");
        assertEquals("token-123", loaded.getPasswordOrToken(), "secret");
    }

    private static void verifyInMemoryStoreLivesInTestsOnly() {
        if (Files.exists(Paths.get(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/credentials/InMemoryCredentialStore.java"))) {
            throw new AssertionError(
                    "In-memory credential storage must be test-only and not ship in production sources");
        }
    }

    private static void verifyEncryptedStoreUsesAndroidXFactory() {
        RecordingRuntime runtime = new RecordingRuntime();
        AndroidXEncryptedSharedPreferencesFactory factory =
                new AndroidXEncryptedSharedPreferencesFactory(new Object(), runtime);
        EncryptedSharedPreferencesCredentialStore store =
                new EncryptedSharedPreferencesCredentialStore(factory);

        WebDavCredentials credentials = new WebDavCredentials("bob", "app-token");
        store.save(credentials);

        WebDavCredentials loaded = store.load();
        assertEquals("bob", loaded.getUsername(), "encrypted username");
        assertEquals("app-token", loaded.getPasswordOrToken(), "encrypted secret");
        assertEquals("sholi_webdav_credentials", runtime.openedFileName,
                "encrypted preference file");
    }

    private static void verifyProductionStoreFactoryCreatesDatabaseBackedEncryptedStore() {
        RecordingRuntime runtime = new RecordingRuntime();
        RecordingCredentialMetadataStore metadataStore = new RecordingCredentialMetadataStore();
        CredentialStore store = CredentialStoreFactory.forProduction(
                new Object(),
                runtime,
                metadataStore);

        if (!(store instanceof DatabaseBackedCredentialStore)) {
            throw new AssertionError("Production store must be database-backed");
        }

        store.save(new WebDavCredentials("charlie", "token-x"));
        WebDavCredentials loaded = store.load();
        assertEquals("charlie", loaded.getUsername(), "production username");
        assertEquals("token-x", loaded.getPasswordOrToken(), "production secret");
        assertEquals("charlie", metadataStore.savedMetadata.getUsername(), "metadata username");
        assertEquals(
                EncryptedSharedPreferencesCredentialStore.SECRET_PREFERENCE_KEY,
                metadataStore.savedMetadata.getSecretPreferenceKey(),
                "metadata secret key");
        assertEquals(
                EncryptedSharedPreferencesCredentialStore.ANDROIDX_ENCRYPTED_SHARED_PREFERENCES_CLASS,
                metadataStore.savedMetadata.getStorageEngineClassName(),
                "metadata storage engine");
        assertEquals(1, metadataStore.transactionCount, "metadata transaction count");
    }

    private static void verifyProductionAndroidWiringUsesDatabaseAndAndroidX() throws Exception {
        String productionWiring = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/ProductionCredentialStores.java");
        assertContains(
                productionWiring,
                "Operations.openWebDavCredentialMetadataStore",
                "production credential wiring");
        assertContains(
                productionWiring,
                "AndroidEncryptedPreferencesFactory",
                "production credential wiring");

        String androidFactory = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/android/AndroidEncryptedPreferencesFactory.java");
        assertContains(
                androidFactory,
                "EncryptedSharedPreferences.create",
                "Android encrypted preferences factory");
        assertContains(androidFactory, "MasterKey.Builder", "Android encrypted preferences factory");

        String operations = readUtf8("sholi/src/main/java/name/soulayrol/rhaa/sholi/data/Operations.java");
        assertContains(
                operations,
                "openWebDavCredentialMetadataStore",
                "Operations credential metadata entry point");

        String sqliteStore = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/data/SqliteCredentialMetadataStore.java");
        assertContains(sqliteStore, "DaoSession", "SQLite credential metadata store");
        assertContains(sqliteStore, "runInTx", "SQLite credential metadata store");
        assertContains(sqliteStore, "CREATE TABLE IF NOT EXISTS", "SQLite credential metadata store");
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Unexpected " + label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError("Unexpected " + label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertContains(String content, String expected, String label) {
        if (!content.contains(expected)) {
            throw new AssertionError("Expected " + label + " to contain " + expected);
        }
    }

    private static String readUtf8(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), "UTF-8");
    }

    private static final class FakeCredentialStore implements CredentialStore {
        private WebDavCredentials credentials;

        @Override
        public void save(WebDavCredentials credentials) {
            this.credentials = credentials;
        }

        @Override
        public WebDavCredentials load() {
            return credentials;
        }

        @Override
        public void clear() {
            credentials = null;
        }
    }

    private static final class RecordingCredentialMetadataStore implements CredentialMetadataStore {
        private WebDavCredentialMetadata savedMetadata;
        private WebDavCredentialMetadata currentMetadata;
        private int transactionCount;

        @Override
        public void runInTransaction(Runnable mutation) {
            transactionCount++;
            mutation.run();
        }

        @Override
        public void save(WebDavCredentialMetadata metadata) {
            savedMetadata = metadata;
            currentMetadata = metadata;
        }

        @Override
        public WebDavCredentialMetadata load() {
            return currentMetadata;
        }

        @Override
        public void clear() {
            currentMetadata = null;
        }
    }

    private static final class RecordingRuntime implements AndroidXEncryptedPreferencesRuntime {
        private final Map<String, String> values = new HashMap<String, String>();
        private String openedFileName;

        @Override
        public Object openEncryptedSharedPreferences(Object context, String fileName) {
            openedFileName = fileName;
            return new MapBackedSharedPreferences(values);
        }
    }

    public static final class MapBackedSharedPreferences {
        private final Map<String, String> values;

        MapBackedSharedPreferences(Map<String, String> values) {
            this.values = values;
        }

        public String getString(String key, String defaultValue) {
            String value = values.get(key);
            return value == null ? defaultValue : value;
        }

        public Editor edit() {
            return new Editor(values);
        }
    }

    public static final class Editor {
        private final Map<String, String> values;

        Editor(Map<String, String> values) {
            this.values = values;
        }

        public Editor putString(String key, String value) {
            values.put(key, value);
            return this;
        }

        public Editor remove(String key) {
            values.remove(key);
            return this;
        }

        public void apply() {
            // Nothing to do for in-memory fake.
        }
    }
}
