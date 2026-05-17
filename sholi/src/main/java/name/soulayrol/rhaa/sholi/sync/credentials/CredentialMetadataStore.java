package name.soulayrol.rhaa.sholi.sync.credentials;

public interface CredentialMetadataStore {

    void runInTransaction(Runnable mutation);

    void save(WebDavCredentialMetadata metadata);

    WebDavCredentialMetadata load();

    void clear();
}
