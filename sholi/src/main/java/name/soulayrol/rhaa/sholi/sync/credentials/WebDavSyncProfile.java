package name.soulayrol.rhaa.sholi.sync.credentials;

public final class WebDavSyncProfile {

    private final String url;
    private final String username;
    private final String remotePath;

    public WebDavSyncProfile(String url, String username, String remotePath) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("url must not be empty");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("username must not be empty");
        }
        if (remotePath == null || remotePath.trim().isEmpty()) {
            throw new IllegalArgumentException("remotePath must not be empty");
        }
        this.url = url;
        this.username = username;
        this.remotePath = remotePath;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getRemotePath() {
        return remotePath;
    }
}
