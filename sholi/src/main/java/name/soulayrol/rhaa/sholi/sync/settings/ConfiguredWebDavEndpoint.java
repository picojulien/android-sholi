package name.soulayrol.rhaa.sholi.sync.settings;

import java.net.URI;
import java.util.Locale;

import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentials;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavSyncProfile;

public final class ConfiguredWebDavEndpoint {

    private final WebDavSyncProfile profile;
    private final WebDavCredentials credentials;
    private final String remoteFileUrl;
    private final String parentCollectionUrl;

    public ConfiguredWebDavEndpoint(WebDavSyncProfile profile, WebDavCredentials credentials) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        if (credentials == null) {
            throw new IllegalArgumentException("credentials must not be null");
        }
        this.profile = profile;
        this.credentials = credentialsForProfile(profile, credentials);
        this.remoteFileUrl = buildRemoteFileUrl(profile);
        this.parentCollectionUrl = buildParentCollectionUrl(remoteFileUrl);
    }

    public WebDavSyncProfile getProfile() {
        return profile;
    }

    public WebDavCredentials getCredentials() {
        return credentials;
    }

    public String getRemoteFileUrl() {
        return remoteFileUrl;
    }

    public String getParentCollectionUrl() {
        return parentCollectionUrl;
    }

    public String buildProbeUrl() {
        String suffix = profile.getClientId() == null ? "client" : safeProbeToken(profile.getClientId());
        return parentCollectionUrl + ".sholi-connection-test-" + suffix + ".txt";
    }

    public static String buildRemoteFileUrl(WebDavSyncProfile profile) {
        URI baseUri = requireHttpUri(profile.getUrl());
        String remotePath = normalizeRemotePath(profile.getRemotePath());
        String base = baseUri.toString();
        String separator = base.endsWith("/") ? "" : "/";
        String url = base + separator + remotePath;
        requireHttpUri(url);
        return url;
    }

    public static String buildParentCollectionUrl(String remoteFileUrl) {
        int slash = remoteFileUrl.lastIndexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("remote file path must include a parent collection");
        }
        return remoteFileUrl.substring(0, slash + 1);
    }

    public static URI requireHttpUri(String url) {
        return WebDavUrlSecurity.requireHttpUri(url);
    }

    public static String normalizeRemotePath(String remotePath) {
        return WebDavRemotePathSecurity.requireSafeForStorage(remotePath);
    }

    private static String safeProbeToken(String clientId) {
        String normalized = clientId.toLowerCase(Locale.US).replaceAll("[^a-z0-9_-]", "-");
        if (normalized.length() == 0) {
            return "client";
        }
        return normalized;
    }

    private static WebDavCredentials credentialsForProfile(
            WebDavSyncProfile profile,
            WebDavCredentials credentials) {
        if (profile.getUsername().equals(credentials.getUsername())) {
            return credentials;
        }
        return new WebDavCredentials(profile.getUsername(), credentials.getPasswordOrToken());
    }
}
