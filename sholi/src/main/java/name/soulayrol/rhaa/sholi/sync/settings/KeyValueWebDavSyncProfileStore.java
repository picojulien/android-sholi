package name.soulayrol.rhaa.sholi.sync.settings;

import name.soulayrol.rhaa.sholi.sync.credentials.WebDavSyncProfile;

public final class KeyValueWebDavSyncProfileStore implements WebDavSyncProfileStore {

    public static final String KEY_URL = "pref_webdav_url";
    public static final String KEY_USERNAME = "pref_webdav_username";
    public static final String KEY_REMOTE_PATH = "pref_webdav_remote_path";
    public static final String KEY_DISPLAY_NAME = "pref_webdav_display_name";
    public static final String KEY_CLIENT_ID = "pref_webdav_client_id";
    public static final String KEY_LAST_TEST_STATUS = "pref_webdav_last_test_status";
    public static final String KEY_LAST_TEST_MESSAGE = "pref_webdav_last_test_message";

    private final KeyValueStore store;

    public KeyValueWebDavSyncProfileStore(KeyValueStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
    }

    @Override
    public void save(WebDavSyncProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        store.putString(KEY_URL, profile.getUrl());
        store.putString(KEY_USERNAME, profile.getUsername());
        store.putString(KEY_REMOTE_PATH, profile.getRemotePath());
        store.putString(KEY_DISPLAY_NAME, profile.getDisplayName());
        putOptional(KEY_CLIENT_ID, profile.getClientId());
        putOptional(KEY_LAST_TEST_STATUS, profile.getLastTestStatus());
        putOptional(KEY_LAST_TEST_MESSAGE, profile.getLastTestMessage());
    }

    @Override
    public WebDavSyncProfile load() {
        String url = emptyToNull(store.getString(KEY_URL, null));
        String username = emptyToNull(store.getString(KEY_USERNAME, null));
        String remotePath = emptyToNull(store.getString(KEY_REMOTE_PATH, null));
        String displayName = emptyToNull(store.getString(KEY_DISPLAY_NAME, null));
        if (url == null || username == null || remotePath == null || displayName == null) {
            return null;
        }
        return new WebDavSyncProfile(
                url,
                username,
                remotePath,
                displayName,
                emptyToNull(store.getString(KEY_CLIENT_ID, null)),
                emptyToNull(store.getString(KEY_LAST_TEST_STATUS, null)),
                emptyToNull(store.getString(KEY_LAST_TEST_MESSAGE, null)));
    }

    @Override
    public void clear() {
        store.remove(KEY_URL);
        store.remove(KEY_USERNAME);
        store.remove(KEY_REMOTE_PATH);
        store.remove(KEY_DISPLAY_NAME);
        store.remove(KEY_CLIENT_ID);
        store.remove(KEY_LAST_TEST_STATUS);
        store.remove(KEY_LAST_TEST_MESSAGE);
    }

    private void putOptional(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            store.remove(key);
        } else {
            store.putString(key, value.trim());
        }
    }

    private static String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    public interface KeyValueStore {

        String getString(String key, String defaultValue);

        void putString(String key, String value);

        void remove(String key);
    }
}
