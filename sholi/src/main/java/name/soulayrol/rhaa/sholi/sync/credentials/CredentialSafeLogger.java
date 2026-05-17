package name.soulayrol.rhaa.sholi.sync.credentials;

public final class CredentialSafeLogger {

    private CredentialSafeLogger() {
    }

    public static String syncConfigured(WebDavSyncProfile profile, WebDavCredentials credentials) {
        String message = "Sync configured for user '" + profile.getUsername()
                + "' at '" + CredentialSafeText.url(profile.getUrl()) + "'";
        return CredentialSafeText.redactCredential(message, credentials);
    }

    public static String authenticationError(WebDavSyncProfile profile, WebDavCredentials credentials) {
        String message = "WebDAV authentication failed for user '"
                + profile.getUsername() + "'";
        return CredentialSafeText.redactCredential(message, credentials);
    }
}
