package name.soulayrol.rhaa.sholi.sync.credentials;

public final class SyncExportFormatter {

    private SyncExportFormatter() {
    }

    public static String export(WebDavSyncProfile profile) {
        StringBuilder builder = new StringBuilder();
        builder.append("WebDAV URL: ").append(CredentialSafeText.url(profile.getUrl())).append('\n');
        builder.append("WebDAV username: ").append(profile.getUsername()).append('\n');
        builder.append("WebDAV remote path: ").append(CredentialSafeText.path(profile.getRemotePath())).append('\n');
        builder.append("WebDAV display name: ").append(profile.getDisplayName()).append('\n');
        if (profile.getClientId() != null) {
            builder.append("WebDAV client id: ").append(profile.getClientId()).append('\n');
        }
        if (profile.getLastTestStatus() != null) {
            builder.append("WebDAV last test status: ").append(profile.getLastTestStatus()).append('\n');
        }
        if (profile.getLastTestMessage() != null) {
            builder.append("WebDAV last test message: ")
                    .append(CredentialSafeText.message(profile.getLastTestMessage()))
                    .append('\n');
        }
        return builder.toString();
    }
}
