package name.soulayrol.rhaa.sholi.sync.credentials;

public interface EncryptedPreferencesFactory {

    String storageEngineClassName();

    EncryptedPreferences open(String fileName);
}
