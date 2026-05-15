package story7;

import name.soulayrol.rhaa.sholi.sync.credentials.CredentialSafeLogger;
import name.soulayrol.rhaa.sholi.sync.credentials.SyncExportFormatter;
import name.soulayrol.rhaa.sholi.sync.credentials.SyncJsonSerializer;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentials;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavSyncProfile;

public final class SecretSafetyStoryTest {

    private SecretSafetyStoryTest() {
    }

    public static void run() {
        String secret = "ultra-secret";
        WebDavCredentials credentials = new WebDavCredentials("alice", secret);
        WebDavSyncProfile profile = new WebDavSyncProfile(
                "https://cloud.example.net/remote.php/dav/files/alice",
                "alice",
                "sholi/sync.json");

        String logLine = CredentialSafeLogger.syncConfigured(profile, credentials);
        assertDoesNotContain(secret, logLine, "log line");

        String syncJson = SyncJsonSerializer.serialize(profile);
        assertDoesNotContain(secret, syncJson, "sync JSON");

        String exported = SyncExportFormatter.export(profile);
        assertDoesNotContain(secret, exported, "exported settings");

        String errorLine = CredentialSafeLogger.authenticationError(profile, credentials);
        assertDoesNotContain(secret, errorLine, "error message");
    }

    private static void assertDoesNotContain(String forbidden, String content, String label) {
        if (content.contains(forbidden)) {
            throw new AssertionError("The " + label + " leaks secret material");
        }
    }
}
