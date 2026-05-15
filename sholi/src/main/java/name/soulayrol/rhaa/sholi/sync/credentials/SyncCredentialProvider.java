package name.soulayrol.rhaa.sholi.sync.credentials;

public final class SyncCredentialProvider {

    private final CredentialStore credentialStore;

    public SyncCredentialProvider(CredentialStore credentialStore) {
        if (credentialStore == null) {
            throw new IllegalArgumentException("credentialStore must not be null");
        }
        this.credentialStore = credentialStore;
    }

    public WebDavCredentials requireCredentials() {
        WebDavCredentials credentials = credentialStore.load();
        if (credentials == null) {
            throw new IllegalStateException("WebDAV credentials are not configured");
        }
        return credentials;
    }
}
