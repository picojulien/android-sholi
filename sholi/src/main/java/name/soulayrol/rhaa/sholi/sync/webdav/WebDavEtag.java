package name.soulayrol.rhaa.sholi.sync.webdav;

public final class WebDavEtag {

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
            return new WebDavEtag(trimmed, Type.STRONG);
        }
        return new WebDavEtag(trimmed, Type.WEAK);
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
