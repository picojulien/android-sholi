package name.soulayrol.rhaa.sholi.sync.credentials;

public final class CredentialStoreFactory {

    private CredentialStoreFactory() {
    }

    public static CredentialStore forProduction(
            Object context,
            CredentialMetadataStore metadataStore) {
        return forProduction(
                context,
                new ReflectionAndroidXEncryptedPreferencesRuntime(),
                metadataStore);
    }

    public static CredentialStore forProduction(
            Object context,
            AndroidXEncryptedPreferencesRuntime runtime,
            CredentialMetadataStore metadataStore) {
        EncryptedPreferencesFactory factory =
                new AndroidXEncryptedSharedPreferencesFactory(context, runtime);
        return forProduction(factory, metadataStore);
    }

    public static CredentialStore forProduction(
            EncryptedPreferencesFactory factory,
            CredentialMetadataStore metadataStore) {
        if (metadataStore == null) {
            throw new IllegalArgumentException("metadataStore must not be null");
        }
        EncryptedSharedPreferencesCredentialStore encryptedStore =
                new EncryptedSharedPreferencesCredentialStore(factory);
        return new DatabaseBackedCredentialStore(metadataStore, encryptedStore);
    }
}
