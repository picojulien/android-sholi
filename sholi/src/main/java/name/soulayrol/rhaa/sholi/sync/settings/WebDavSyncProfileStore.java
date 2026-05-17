package name.soulayrol.rhaa.sholi.sync.settings;

import name.soulayrol.rhaa.sholi.sync.credentials.WebDavSyncProfile;

public interface WebDavSyncProfileStore {

    void save(WebDavSyncProfile profile);

    WebDavSyncProfile load();

    void clear();
}
