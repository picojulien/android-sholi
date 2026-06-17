package name.soulayrol.rhaa.sholi.sync.credentials;

public final class WebDavCredentialMetadata {

    private final String username;
    private final String secretPreferenceKey;
    private final String storageEngineClassName;

    public WebDavCredentialMetadata(
            String username,
            String secretPreferenceKey,
            String storageEngineClassName) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("username must not be empty");
        }
        if (secretPreferenceKey == null || secretPreferenceKey.trim().isEmpty()) {
            throw new IllegalArgumentException("secretPreferenceKey must not be empty");
        }
        if (storageEngineClassName == null || storageEngineClassName.trim().isEmpty()) {
            throw new IllegalArgumentException("storageEngineClassName must not be empty");
        }
        this.username = username;
        this.secretPreferenceKey = secretPreferenceKey;
        this.storageEngineClassName = storageEngineClassName;
    }

    public String getUsername() {
        return username;
    }

    public String getSecretPreferenceKey() {
        return secretPreferenceKey;
    }

    public String getStorageEngineClassName() {
        return storageEngineClassName;
    }
}
