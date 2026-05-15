package name.soulayrol.rhaa.sholi.sync.credentials;

public final class SyncJsonSerializer {

    private SyncJsonSerializer() {
    }

    public static String serialize(WebDavSyncProfile profile) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("\"schema_version\":1,");
        builder.append("\"webdav\":{");
        builder.append("\"url\":\"").append(escape(profile.getUrl())).append("\",");
        builder.append("\"username\":\"").append(escape(profile.getUsername())).append("\",");
        builder.append("\"remote_path\":\"").append(escape(profile.getRemotePath())).append("\"");
        builder.append("}}");
        return builder.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
