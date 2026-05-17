package name.soulayrol.rhaa.sholi.sync.credentials;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CredentialSafeText {

    private static final String REDACTED = "[REDACTED]";
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*)[^\\r\\n,;}]+(?:\\s+[^\\r\\n,;}]+)?");
    private static final Pattern SENSITIVE_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)(access_token|password|passwd|token|secret|credential|auth)=([^\\s&#,;}]+)");
    private static final Pattern SENSITIVE_FRAGMENT_PATTERN = Pattern.compile(
            "(?i)(^|[^a-z0-9])(access[_-]?token|refresh[_-]?token|id[_-]?token|token|password|passwd|secret|credential|auth)([^a-z0-9]|$)");

    private CredentialSafeText() {
    }

    public static String url(String value) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = new URI(value);
            URI redacted = new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    sanitizeQuery(uri.getQuery()),
                    sanitizeFragment(uri.getFragment()));
            return redacted.toString();
        } catch (URISyntaxException e) {
            return sanitizeFragmentInRawText(sanitizeQueryInRawText(removeRawUserInfo(value)));
        }
    }

    public static String message(String value) {
        if (value == null) {
            return null;
        }
        String result = url(value);
        result = redactAuthorization(result);
        result = redactSensitiveAssignments(result);
        return result;
    }

    public static String redactCredential(String value, WebDavCredentials credentials) {
        if (value == null || credentials == null) {
            return value;
        }
        return value.replace(credentials.getPasswordOrToken(), REDACTED);
    }

    private static String redactAuthorization(String value) {
        Matcher matcher = AUTHORIZATION_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + REDACTED));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String redactSensitiveAssignments(String value) {
        Matcher matcher = SENSITIVE_ASSIGNMENT_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(
                    buffer,
                    Matcher.quoteReplacement(matcher.group(1) + "=" + REDACTED));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String removeRawUserInfo(String value) {
        int scheme = value.indexOf("://");
        if (scheme < 0) {
            return value;
        }
        int authorityStart = scheme + 3;
        int authorityEnd = firstIndexOf(value, authorityStart, '/', '?', '#');
        int userInfoEnd = value.indexOf('@', authorityStart);
        if (userInfoEnd < 0 || userInfoEnd > authorityEnd) {
            return value;
        }
        return value.substring(0, authorityStart)
                + REDACTED
                + value.substring(userInfoEnd);
    }

    private static String sanitizeQueryInRawText(String value) {
        int queryStart = value.indexOf('?');
        if (queryStart < 0) {
            return value;
        }
        int fragmentStart = value.indexOf('#', queryStart);
        String prefix = value.substring(0, queryStart + 1);
        String query = fragmentStart < 0
                ? value.substring(queryStart + 1)
                : value.substring(queryStart + 1, fragmentStart);
        String suffix = fragmentStart < 0 ? "" : value.substring(fragmentStart);
        return prefix + sanitizeQuery(query) + suffix;
    }

    private static String sanitizeFragmentInRawText(String value) {
        int fragmentStart = value.indexOf('#');
        if (fragmentStart < 0) {
            return value;
        }
        return value.substring(0, fragmentStart + 1) + sanitizeFragment(value.substring(fragmentStart + 1));
    }

    private static String sanitizeQuery(String query) {
        if (query == null || query.length() == 0) {
            return query;
        }
        String[] parts = query.split("&", -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append('&');
            }
            String part = parts[i];
            int equals = part.indexOf('=');
            String key = equals < 0 ? part : part.substring(0, equals);
            if (isSensitiveQueryKey(key)) {
                builder.append(key).append('=').append(REDACTED);
            } else {
                builder.append(part);
            }
        }
        return builder.toString();
    }

    private static String sanitizeFragment(String fragment) {
        if (fragment == null || fragment.length() == 0) {
            return fragment;
        }
        if (SENSITIVE_FRAGMENT_PATTERN.matcher(fragment).find()) {
            return REDACTED;
        }
        return fragment;
    }

    private static boolean isSensitiveQueryKey(String key) {
        String normalized = key.toLowerCase(Locale.US);
        return normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("passwd")
                || normalized.contains("secret")
                || normalized.contains("credential")
                || normalized.equals("auth")
                || normalized.endsWith("_auth");
    }

    private static int firstIndexOf(String value, int from, char a, char b, char c) {
        int result = value.length();
        int index = value.indexOf(a, from);
        if (index >= 0 && index < result) {
            result = index;
        }
        index = value.indexOf(b, from);
        if (index >= 0 && index < result) {
            result = index;
        }
        index = value.indexOf(c, from);
        if (index >= 0 && index < result) {
            result = index;
        }
        return result;
    }
}
