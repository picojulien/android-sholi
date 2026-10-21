package name.soulayrol.rhaa.sholi.sync.webdav;

public final class WebDavEtag {

    private static final String GZIP_VARIANT_SUFFIX = "-gzip";

    public enum Type {
        STRONG,
        WEAK,
        MISSING
    }

    private final String value;
    private final Type type;

    private WebDavEtag(String value, Type type) {
        this.value = value;
        this.type = type;
    }

    public static WebDavEtag classify(String value) {
        if (value == null) {
            return new WebDavEtag(null, Type.MISSING);
        }
        String trimmed = value.trim();
        if (trimmed.length() == 0) {
            return new WebDavEtag(null, Type.MISSING);
        }
        if (trimmed.regionMatches(true, 0, "W/", 0, 2)) {
            return new WebDavEtag(trimmed, Type.WEAK);
        }
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '"'
                && trimmed.charAt(trimmed.length() - 1) == '"') {
            return new WebDavEtag(canonicalStrongValue(trimmed), Type.STRONG);
        }
        return new WebDavEtag(trimmed, Type.WEAK);
    }

    private static String canonicalStrongValue(String quotedValue) {
        String innerValue = quotedValue.substring(1, quotedValue.length() - 1);
        if (innerValue.endsWith(GZIP_VARIANT_SUFFIX)) {
            return "\""
                    + innerValue.substring(0, innerValue.length() - GZIP_VARIANT_SUFFIX.length())
                    + "\"";
        }
        return quotedValue;
    }

    public static String describeForMessage(String label, String marker) {
        WebDavEtag etag = classify(marker);
        String safeLabel = label == null || label.length() == 0 ? "ETag" : label;
        if (etag.isMissing()) {
            return safeLabel + ": <missing>";
        }
        return safeLabel + ": " + etag.getValue();
    }

    public String getValue() {
        return value;
    }

    public Type getType() {
        return type;
    }

    public boolean isStrong() {
        return type == Type.STRONG;
    }

    public boolean isMissing() {
        return type == Type.MISSING;
    }

    @Override
    public String toString() {
        return "WebDavEtag{type=" + type + ", present=" + (value != null) + '}';
    }
}
