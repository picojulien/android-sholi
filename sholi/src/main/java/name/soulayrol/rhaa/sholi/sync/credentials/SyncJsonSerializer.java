package name.soulayrol.rhaa.sholi.sync.credentials;

public final class SyncJsonSerializer {

    private SyncJsonSerializer() {
    }

    public static String serialize(WebDavSyncProfile profile) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("\"schema_version\":1,");
        builder.append("\"webdav\":{");
        builder.append("\"url\":\"").append(escape(CredentialSafeText.url(profile.getUrl()))).append("\",");
        builder.append("\"username\":\"").append(escape(profile.getUsername())).append("\",");
        builder.append("\"remote_path\":\"").append(escape(CredentialSafeText.path(profile.getRemotePath()))).append("\",");
        builder.append("\"display_name\":\"").append(escape(profile.getDisplayName())).append("\"");
        appendOptional(builder, "client_id", profile.getClientId());
        appendOptional(builder, "last_test_status", profile.getLastTestStatus());
        appendOptional(builder, "last_test_message", CredentialSafeText.message(profile.getLastTestMessage()));
        builder.append("}}");
        return builder.toString();
    }

    private static void appendOptional(StringBuilder builder, String name, String value) {
        if (value != null) {
            builder.append(",\"").append(name).append("\":\"").append(escape(value)).append("\"");
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
