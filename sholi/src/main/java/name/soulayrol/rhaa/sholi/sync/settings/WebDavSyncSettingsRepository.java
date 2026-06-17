package name.soulayrol.rhaa.sholi.sync.settings;

import name.soulayrol.rhaa.sholi.sync.credentials.CredentialStore;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentials;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavSyncProfile;

public final class WebDavSyncSettingsRepository {

    private final WebDavSyncProfileStore profileStore;
    private final CredentialStore credentialStore;

    public WebDavSyncSettingsRepository(
            WebDavSyncProfileStore profileStore,
            CredentialStore credentialStore) {
        if (profileStore == null) {
            throw new IllegalArgumentException("profileStore must not be null");
        }
        if (credentialStore == null) {
            throw new IllegalArgumentException("credentialStore must not be null");
        }
        this.profileStore = profileStore;
        this.credentialStore = credentialStore;
    }

    public void save(WebDavSyncProfile profile, String passwordOrToken) {
        profileStore.save(profile);
        if (passwordOrToken != null && passwordOrToken.length() > 0) {
            credentialStore.save(new WebDavCredentials(profile.getUsername(), passwordOrToken));
        }
    }

    public WebDavSyncProfile loadProfile() {
        return profileStore.load();
    }

    public WebDavCredentials loadCredentials() {
        return credentialStore.load();
    }
}
