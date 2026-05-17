package name.soulayrol.rhaa.sholi.sync.credentials;

public final class SyncExportFormatter {

    private SyncExportFormatter() {
    }

    public static String export(WebDavSyncProfile profile) {
        StringBuilder builder = new StringBuilder();
        builder.append("WebDAV URL: ").append(CredentialSafeText.url(profile.getUrl())).append('\n');
        builder.append("WebDAV username: ").append(profile.getUsername()).append('\n');
        builder.append("WebDAV remote path: ").append(profile.getRemotePath()).append('\n');
        return builder.toString();
    }
}
