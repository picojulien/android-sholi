package name.soulayrol.rhaa.sholi.sync.settings;

import name.soulayrol.rhaa.sholi.sync.credentials.SyncCredentialProvider;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentials;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavSyncProfile;

public final class WebDavSyncProfileLoader {

    private final WebDavSyncProfileStore profileStore;
    private final SyncCredentialProvider credentialProvider;

    public WebDavSyncProfileLoader(
            WebDavSyncProfileStore profileStore,
            SyncCredentialProvider credentialProvider) {
        if (profileStore == null) {
            throw new IllegalArgumentException("profileStore must not be null");
        }
        if (credentialProvider == null) {
            throw new IllegalArgumentException("credentialProvider must not be null");
        }
        this.profileStore = profileStore;
        this.credentialProvider = credentialProvider;
    }

    public ConfiguredWebDavEndpoint requireEndpoint() {
        WebDavSyncProfile profile = profileStore.load();
        if (profile == null) {
            throw new IllegalStateException("WebDAV sync profile is not configured");
        }
        WebDavCredentials credentials = credentialProvider.requireCredentials();
        return new ConfiguredWebDavEndpoint(profile, credentials);
    }
}
