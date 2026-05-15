package name.soulayrol.rhaa.sholi.sync.credentials;

public final class InMemoryCredentialStore implements CredentialStore {

    private WebDavCredentials credentials;

    @Override
    public void save(WebDavCredentials credentials) {
        if (credentials == null) {
            throw new IllegalArgumentException("credentials must not be null");
        }
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
