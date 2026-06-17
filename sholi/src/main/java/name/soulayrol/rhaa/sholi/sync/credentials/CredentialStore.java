package name.soulayrol.rhaa.sholi.sync.credentials;

public interface CredentialStore {

    void save(WebDavCredentials credentials);

    WebDavCredentials load();

    void clear();
}
