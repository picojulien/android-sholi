package name.soulayrol.rhaa.sholi.sync.credentials;

public final class DatabaseBackedCredentialStore implements CredentialStore {

    private final CredentialMetadataStore metadataStore;
    private final EncryptedSharedPreferencesCredentialStore encryptedStore;

    public DatabaseBackedCredentialStore(
            CredentialMetadataStore metadataStore,
            EncryptedSharedPreferencesCredentialStore encryptedStore) {
        if (metadataStore == null) {
            throw new IllegalArgumentException("metadataStore must not be null");
        }
        if (encryptedStore == null) {
            throw new IllegalArgumentException("encryptedStore must not be null");
        }
        this.metadataStore = metadataStore;
        this.encryptedStore = encryptedStore;
    }

    @Override
    public void save(final WebDavCredentials credentials) {
        if (credentials == null) {
            throw new IllegalArgumentException("credentials must not be null");
        }
        metadataStore.runInTransaction(new Runnable() {
            @Override
            public void run() {
                metadataStore.save(new WebDavCredentialMetadata(
                        credentials.getUsername(),
                        EncryptedSharedPreferencesCredentialStore.SECRET_PREFERENCE_KEY,
                        EncryptedSharedPreferencesCredentialStore
                                .ANDROIDX_ENCRYPTED_SHARED_PREFERENCES_CLASS));
                encryptedStore.save(credentials);
            }
        });
    }

    @Override
    public WebDavCredentials load() {
        WebDavCredentialMetadata metadata = metadataStore.load();
        if (metadata == null) {
            return null;
        }

        WebDavCredentials encryptedCredentials = encryptedStore.load();
        if (encryptedCredentials == null) {
            return null;
        }
        return new WebDavCredentials(
                metadata.getUsername(),
                encryptedCredentials.getPasswordOrToken());
    }

    @Override
    public void clear() {
        metadataStore.runInTransaction(new Runnable() {
            @Override
            public void run() {
                metadataStore.clear();
                encryptedStore.clear();
            }
        });
    }
}
