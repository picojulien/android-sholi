package name.soulayrol.rhaa.sholi.sync.credentials;

public interface EncryptedPreferences {

    void putString(String key, String value);

    String getString(String key);

    void remove(String key);
}
