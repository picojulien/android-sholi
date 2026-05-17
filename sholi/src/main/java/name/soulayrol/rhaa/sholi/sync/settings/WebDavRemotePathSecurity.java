package name.soulayrol.rhaa.sholi.sync.settings;

public final class WebDavRemotePathSecurity {

    private static final String UNSAFE_URI_CHARACTERS = ":?#[]@=&;% ";

    private WebDavRemotePathSecurity() {
    }

    public static String requireSafeForStorage(String remotePath) {
        String path = normalize(remotePath);
        if (path.indexOf("://") >= 0) {
            throw new IllegalArgumentException(
                    "Remote file path must not include a full URL or URI scheme");
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (isUnsafeUriCharacter(c)) {
                throw new IllegalArgumentException(
                        "Remote file path must not include URLs, user info, query strings, fragments, or secret-bearing URI characters");
            }
        }
        String[] parts = path.split("/");
        for (String part: parts) {
            if (part.length() == 0 || ".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException(
                        "Enter a remote file path without . or .. segments");
            }
        }
        return path;
    }

    private static String normalize(String remotePath) {
        if (remotePath == null || remotePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a remote file path");
        }
        String path = remotePath.trim().replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.length() == 0 || path.endsWith("/")) {
            throw new IllegalArgumentException(
                    "Enter a remote file path for the sync JSON file");
        }
        return path;
    }

    private static boolean isUnsafeUriCharacter(char c) {
        return UNSAFE_URI_CHARACTERS.indexOf(c) >= 0 || Character.isISOControl(c);
    }
}
