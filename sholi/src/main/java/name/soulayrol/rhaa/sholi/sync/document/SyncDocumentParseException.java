package name.soulayrol.rhaa.sholi.sync.document;

public final class SyncDocumentParseException extends Exception {

    public enum Reason {
        MALFORMED_JSON,
        MISSING_REQUIRED_FIELD,
        INVALID_FIELD_TYPE,
        INVALID_FIELD_VALUE,
        UNSUPPORTED_SCHEMA_VERSION,
        DUPLICATE_SYNC_ID
    }

    private final Reason reason;
    private final String fieldPath;
    private final Integer schemaVersion;

    SyncDocumentParseException(Reason reason, String fieldPath) {
        this(reason, fieldPath, null);
    }

    SyncDocumentParseException(Reason reason, String fieldPath, Integer schemaVersion) {
        super(buildMessage(reason, fieldPath, schemaVersion));
        this.reason = reason;
        this.fieldPath = fieldPath;
        this.schemaVersion = schemaVersion;
    }

    public Reason getReason() {
        return reason;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    private static String buildMessage(Reason reason, String fieldPath, Integer schemaVersion) {
        StringBuilder builder = new StringBuilder("Invalid sync document: ");
        builder.append(reason);
        if (fieldPath != null) {
            builder.append(" at ").append(fieldPath);
        }
        if (schemaVersion != null) {
            builder.append(" (schema_version=").append(schemaVersion).append(')');
        }
        return builder.toString();
    }
}
