package name.soulayrol.rhaa.sholi.sync.credentials;

public final class CredentialSafeLogger {

    private CredentialSafeLogger() {
    }

    public static String syncConfigured(WebDavSyncProfile profile, WebDavCredentials credentials) {
        return "Sync configured for user '" + profile.getUsername()
                + "' at '" + profile.getUrl() + "'";
    }

    public static String authenticationError(WebDavSyncProfile profile, WebDavCredentials credentials) {
        return "WebDAV authentication failed for user '"
                + profile.getUsername() + "'";
    }
}
