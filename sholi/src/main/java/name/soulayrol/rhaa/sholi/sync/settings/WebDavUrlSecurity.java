package name.soulayrol.rhaa.sholi.sync.settings;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Locale;

public final class WebDavUrlSecurity {

    private WebDavUrlSecurity() {
    }

    public static URI requireHttpUri(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                throw new IllegalArgumentException("Enter a valid WebDAV URL");
            }
            String normalizedScheme = scheme.toLowerCase(Locale.US);
            if (!"https".equals(normalizedScheme) && !"http".equals(normalizedScheme)) {
                throw new IllegalArgumentException("Enter a valid WebDAV URL using HTTP or HTTPS");
            }
            requireSafeComponents(uri);
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Enter a valid WebDAV URL", e);
        }
    }

    public static void requireSafeForStorage(String url) {
        requireHttpUri(url);
    }

    private static void requireSafeComponents(URI uri) {
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException(
                    "WebDAV URL must not include username, password, or token text");
        }
        if (hasSensitiveQuery(uri.getRawQuery()) || hasSensitiveText(uri.getRawFragment())) {
            throw new IllegalArgumentException(
                    "WebDAV URL must not include password, token, secret, or auth query/fragment values");
        }
    }

    private static boolean hasSensitiveQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.length() == 0) {
            return false;
        }
        String[] parts = rawQuery.split("[&;]", -1);
        for (String part: parts) {
            int equals = part.indexOf('=');
            String key = equals < 0 ? part : part.substring(0, equals);
            String value = equals < 0 ? "" : part.substring(equals + 1);
            if (hasSensitiveText(key) || hasSensitiveText(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSensitiveText(String value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        String normalized = decode(value).toLowerCase(Locale.US).replace('-', '_');
        return normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("passwd")
                || normalized.contains("secret")
                || normalized.contains("credential")
                || normalized.contains("auth");
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
