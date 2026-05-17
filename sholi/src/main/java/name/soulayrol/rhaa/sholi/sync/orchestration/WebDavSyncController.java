package name.soulayrol.rhaa.sholi.sync.orchestration;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentials;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;
import name.soulayrol.rhaa.sholi.sync.merge.SyncStateRecorder;
import name.soulayrol.rhaa.sholi.sync.settings.ConfiguredWebDavEndpoint;
import name.soulayrol.rhaa.sholi.sync.webdav.LocalSyncDocumentStore;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavClient;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavSyncEngine;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavSyncResult;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavTransport;

public final class WebDavSyncController {

    private static final char[] BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private final LocalSyncDocumentStore localStore;
    private final SyncMetadataStore metadataStore;
    private final EndpointProvider endpointProvider;
    private final EngineFactory engineFactory;

    public WebDavSyncController(
            LocalSyncDocumentStore localStore,
            SyncMetadataStore metadataStore,
            EndpointProvider endpointProvider,
            EngineFactory engineFactory) {
        if (localStore == null) {
            throw new IllegalArgumentException("localStore must not be null");
        }
        if (metadataStore == null) {
            throw new IllegalArgumentException("metadataStore must not be null");
        }
        if (endpointProvider == null) {
            throw new IllegalArgumentException("endpointProvider must not be null");
        }
        if (engineFactory == null) {
            throw new IllegalArgumentException("engineFactory must not be null");
        }
        this.localStore = localStore;
        this.metadataStore = metadataStore;
        this.endpointProvider = endpointProvider;
        this.engineFactory = engineFactory;
    }

    public WebDavSyncResult synchronize() {
        return synchronize(System.currentTimeMillis());
    }

    public WebDavSyncResult synchronize(long conflictTimestamp) {
        if (conflictTimestamp < 0L) {
            throw new IllegalArgumentException("conflictTimestamp must not be negative");
        }
        List<SyncConflict> conflicts = metadataStore.loadConflicts();
        if (SyncStateRecorder.hasUnresolvedConflicts(metadataStore)) {
            return WebDavSyncResult.conflicts(conflicts);
        }

        ConfiguredWebDavEndpoint endpoint;
        try {
            endpoint = endpointProvider.requireEndpoint();
        } catch (RuntimeException e) {
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.CONFIGURATION_ERROR,
                    "WebDAV synchronization is not fully configured");
        }
        return engineFactory.create(endpoint, localStore, metadataStore).synchronize(conflictTimestamp);
    }

    public interface EndpointProvider {
        ConfiguredWebDavEndpoint requireEndpoint();
    }

    public interface EngineFactory {
        SafeSyncEngine create(
                ConfiguredWebDavEndpoint endpoint,
                LocalSyncDocumentStore localStore,
                SyncMetadataStore metadataStore);
    }

    public interface SafeSyncEngine {
        WebDavSyncResult synchronize(long conflictTimestamp);
    }

    public static final class DefaultEngineFactory implements EngineFactory {

        private final WebDavTransport transport;

        public DefaultEngineFactory(WebDavTransport transport) {
            if (transport == null) {
                throw new IllegalArgumentException("transport must not be null");
            }
            this.transport = transport;
        }

        @Override
        public SafeSyncEngine create(
                ConfiguredWebDavEndpoint endpoint,
                LocalSyncDocumentStore localStore,
                SyncMetadataStore metadataStore) {
            if (endpoint == null) {
                throw new IllegalArgumentException("endpoint must not be null");
            }
            WebDavClient client = new WebDavClient(
                    transport,
                    authorizationHeaders(endpoint.getCredentials()));
            return new EngineAdapter(new WebDavSyncEngine(
                    client,
                    endpoint.getRemoteFileUrl(),
                    localStore,
                    metadataStore));
        }
    }

    public static Map<String, String> authorizationHeaders(WebDavCredentials credentials) {
        if (credentials == null) {
            throw new IllegalArgumentException("credentials must not be null");
        }
        LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "Basic "
                + base64(credentials.getUsername() + ":" + credentials.getPasswordOrToken()));
        return headers;
    }

    private static String base64(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(((bytes.length + 2) / 3) * 4);
        for (int i = 0; i < bytes.length; i += 3) {
            int b0 = bytes[i] & 0xff;
            int b1 = i + 1 < bytes.length ? bytes[i + 1] & 0xff : 0;
            int b2 = i + 2 < bytes.length ? bytes[i + 2] & 0xff : 0;
            builder.append(BASE64[b0 >>> 2]);
            builder.append(BASE64[((b0 & 0x03) << 4) | (b1 >>> 4)]);
            builder.append(i + 1 < bytes.length ? BASE64[((b1 & 0x0f) << 2) | (b2 >>> 6)] : '=');
            builder.append(i + 2 < bytes.length ? BASE64[b2 & 0x3f] : '=');
        }
        return builder.toString();
    }

    private static final class EngineAdapter implements SafeSyncEngine {

        private final WebDavSyncEngine engine;

        EngineAdapter(WebDavSyncEngine engine) {
            this.engine = engine;
        }

        @Override
        public WebDavSyncResult synchronize(long conflictTimestamp) {
            return engine.synchronize(conflictTimestamp);
        }
    }
}
