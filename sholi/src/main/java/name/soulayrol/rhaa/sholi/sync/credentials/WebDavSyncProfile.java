package name.soulayrol.rhaa.sholi.sync.credentials;

public final class WebDavSyncProfile {

    private final String url;
    private final String username;
    private final String remotePath;
    private final String displayName;
    private final String clientId;
    private final String lastTestStatus;
    private final String lastTestMessage;

    public WebDavSyncProfile(String url, String username, String remotePath) {
        this(url, username, remotePath, username, null, null, null);
    }

    public WebDavSyncProfile(
            String url,
            String username,
            String remotePath,
            String displayName,
            String clientId) {
        this(url, username, remotePath, displayName, clientId, null, null);
    }

    public WebDavSyncProfile(
            String url,
            String username,
            String remotePath,
            String displayName,
            String clientId,
            String lastTestStatus,
            String lastTestMessage) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("url must not be empty");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("username must not be empty");
        }
        if (remotePath == null || remotePath.trim().isEmpty()) {
            throw new IllegalArgumentException("remotePath must not be empty");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("displayName must not be empty");
        }
        this.url = url.trim();
        this.username = username.trim();
        this.remotePath = remotePath.trim();
        this.displayName = displayName.trim();
        this.clientId = emptyToNull(clientId);
        this.lastTestStatus = emptyToNull(lastTestStatus);
        this.lastTestMessage = emptyToNull(lastTestMessage);
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

    public String getDisplayName() {
        return displayName;
    }

    public String getClientId() {
        return clientId;
    }

    public String getLastTestStatus() {
        return lastTestStatus;
    }

    public String getLastTestMessage() {
        return lastTestMessage;
    }

    public WebDavSyncProfile withTestStatus(String status, String message) {
        return new WebDavSyncProfile(
                url,
                username,
                remotePath,
                displayName,
                clientId,
                status,
                CredentialSafeText.message(message));
    }

    @Override
    public String toString() {
        return "WebDavSyncProfile{url='" + CredentialSafeText.url(url) + '\''
                + ", username='" + username + '\''
                + ", remotePath='" + CredentialSafeText.path(remotePath) + '\''
                + ", displayName='" + displayName + '\''
                + ", clientId='" + clientId + '\''
                + ", lastTestStatus='" + lastTestStatus + '\''
                + ", lastTestMessage='" + CredentialSafeText.message(lastTestMessage) + '\''
                + '}';
    }

    private static String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
