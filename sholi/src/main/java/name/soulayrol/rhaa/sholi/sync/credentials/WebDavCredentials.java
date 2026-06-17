package name.soulayrol.rhaa.sholi.sync.credentials;

public final class WebDavCredentials {

    private final String username;
    private final String passwordOrToken;

    public WebDavCredentials(String username, String passwordOrToken) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("username must not be empty");
        }
        if (passwordOrToken == null || passwordOrToken.isEmpty()) {
            throw new IllegalArgumentException("passwordOrToken must not be empty");
        }
        this.username = username;
        this.passwordOrToken = passwordOrToken;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordOrToken() {
        return passwordOrToken;
    }

    @Override
    public String toString() {
        return "WebDavCredentials{username='" + username + "', passwordOrToken='***'}";
    }
}
