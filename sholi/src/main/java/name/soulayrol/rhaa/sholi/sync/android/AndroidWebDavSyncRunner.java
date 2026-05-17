package name.soulayrol.rhaa.sholi.sync.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

import name.soulayrol.rhaa.sholi.data.Operations;
import name.soulayrol.rhaa.sholi.data.SqliteLocalSyncDocumentStore;
import name.soulayrol.rhaa.sholi.data.model.DaoSession;
import name.soulayrol.rhaa.sholi.sync.ProductionCredentialStores;
import name.soulayrol.rhaa.sholi.sync.credentials.SyncCredentialProvider;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentJson;
import name.soulayrol.rhaa.sholi.sync.merge.ConflictChoice;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;
import name.soulayrol.rhaa.sholi.sync.orchestration.ResolvedConflictSyncService;
import name.soulayrol.rhaa.sholi.sync.orchestration.WebDavSyncController;
import name.soulayrol.rhaa.sholi.sync.settings.ConfiguredWebDavEndpoint;
import name.soulayrol.rhaa.sholi.sync.settings.KeyValueWebDavSyncProfileStore;
import name.soulayrol.rhaa.sholi.sync.settings.WebDavSyncProfileLoader;
import name.soulayrol.rhaa.sholi.sync.webdav.HttpUrlConnectionWebDavTransport;
import name.soulayrol.rhaa.sholi.sync.webdav.LocalSyncDocumentStore;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavClient;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavPutResult;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavSyncResult;

public final class AndroidWebDavSyncRunner {

    private final Context context;

    public AndroidWebDavSyncRunner(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        Context applicationContext = context.getApplicationContext();
        this.context = applicationContext == null ? context : applicationContext;
    }

    public WebDavSyncResult synchronizeOrResume() {
        SyncStores stores = openStores();
        List<SyncConflict> conflicts = stores.metadataStore.loadConflicts();
        if (!conflicts.isEmpty() && !hasUnresolved(conflicts)) {
            return resumeResolvedConflicts(stores);
        }
        return new WebDavSyncController(
                stores.localStore,
                stores.metadataStore,
                new AndroidEndpointProvider(context),
                new WebDavSyncController.DefaultEngineFactory(
                        new HttpUrlConnectionWebDavTransport()))
                .synchronize();
    }

    public List<SyncConflict> loadUnresolvedConflicts() {
        SyncMetadataStore metadataStore = Operations.openWebDavSyncMetadataStore(context);
        ArrayList<SyncConflict> unresolved = new ArrayList<SyncConflict>();
        for (SyncConflict conflict: metadataStore.loadConflicts()) {
            if (SyncConflict.STATUS_UNRESOLVED.equals(conflict.getStatus())) {
                unresolved.add(conflict);
            }
        }
        return unresolved;
    }

    public SyncConflict choose(String syncId, ConflictChoice choice) {
        SyncStores stores = openStores();
        return new ResolvedConflictSyncService(
                stores.localStore,
                stores.metadataStore,
                new NoopConflictUploader())
                .choose(syncId, choice);
    }

    public WebDavSyncResult resumeResolvedConflicts() {
        return resumeResolvedConflicts(openStores());
    }

    private WebDavSyncResult resumeResolvedConflicts(SyncStores stores) {
        ConfiguredWebDavEndpoint endpoint;
        try {
            endpoint = endpoint();
        } catch (RuntimeException e) {
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.CONFIGURATION_ERROR,
                    "WebDAV synchronization is not fully configured");
        }
        return new ResolvedConflictSyncService(
                stores.localStore,
                stores.metadataStore,
                new AndroidConflictUploader(endpoint))
                .resumeResolvedConflicts();
    }

    private SyncStores openStores() {
        DaoSession daoSession = Operations.openSession(context);
        return new SyncStores(
                new SqliteLocalSyncDocumentStore(daoSession),
                Operations.openWebDavSyncMetadataStore(context));
    }

    private ConfiguredWebDavEndpoint endpoint() {
        return new AndroidEndpointProvider(context).requireEndpoint();
    }

    private static boolean hasUnresolved(List<SyncConflict> conflicts) {
        for (SyncConflict conflict: conflicts) {
            if (SyncConflict.STATUS_UNRESOLVED.equals(conflict.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private static final class SyncStores {
        private final LocalSyncDocumentStore localStore;
        private final SyncMetadataStore metadataStore;

        SyncStores(LocalSyncDocumentStore localStore, SyncMetadataStore metadataStore) {
            this.localStore = localStore;
            this.metadataStore = metadataStore;
        }
    }

    private static final class AndroidEndpointProvider implements WebDavSyncController.EndpointProvider {
        private final Context context;

        AndroidEndpointProvider(Context context) {
            this.context = context;
        }

        @Override
        public ConfiguredWebDavEndpoint requireEndpoint() {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            return new WebDavSyncProfileLoader(
                    new KeyValueWebDavSyncProfileStore(new SharedPreferencesKeyValueStore(preferences)),
                    new SyncCredentialProvider(ProductionCredentialStores.webDav(context)))
                    .requireEndpoint();
        }
    }

    private static final class AndroidConflictUploader
            implements ResolvedConflictSyncService.ConflictUploader {
        private final ConfiguredWebDavEndpoint endpoint;

        AndroidConflictUploader(ConfiguredWebDavEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public WebDavPutResult uploadResolvedDocument(SyncDocument document, String remoteVersionMarker) {
            WebDavClient client = new WebDavClient(
                    new HttpUrlConnectionWebDavTransport(),
                    WebDavSyncController.authorizationHeaders(endpoint.getCredentials()));
            return client.putIfMatch(
                    endpoint.getRemoteFileUrl(),
                    SyncDocumentJson.serialize(document),
                    remoteVersionMarker);
        }
    }

    private static final class NoopConflictUploader
            implements ResolvedConflictSyncService.ConflictUploader {
        @Override
        public WebDavPutResult uploadResolvedDocument(SyncDocument document, String remoteVersionMarker) {
            throw new IllegalStateException("No upload expected while saving a conflict choice");
        }
    }

    private static final class SharedPreferencesKeyValueStore
            implements KeyValueWebDavSyncProfileStore.KeyValueStore {

        private final SharedPreferences sharedPreferences;

        SharedPreferencesKeyValueStore(SharedPreferences sharedPreferences) {
            this.sharedPreferences = sharedPreferences;
        }

        @Override
        public String getString(String key, String defaultValue) {
            return sharedPreferences.getString(key, defaultValue);
        }

        @Override
        public void putString(String key, String value) {
            sharedPreferences.edit().putString(key, value).apply();
        }

        @Override
        public void remove(String key) {
            sharedPreferences.edit().remove(key).apply();
        }
    }
}
