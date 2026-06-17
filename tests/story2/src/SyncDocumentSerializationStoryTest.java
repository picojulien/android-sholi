package story2;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentials;
import name.soulayrol.rhaa.sholi.sync.document.ModifiedBy;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentJson;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;

public final class SyncDocumentSerializationStoryTest {

    private SyncDocumentSerializationStoryTest() {
    }

    public static void run() throws Exception {
        verifyCanonicalJsonOutputAndSecretExclusion();
        verifyDeterministicSortingForHundredsOfItems();
        verifyJsonEscapingIsRoundTrippable();
    }

    private static void verifyCanonicalJsonOutputAndSecretExclusion() {
        WebDavCredentials credentials = new WebDavCredentials("alice", "ultra-secret-token");
        List<SyncItem> items = new ArrayList<SyncItem>();
        items.add(new SyncItem(
                "sync-b",
                "Milk",
                1,
                false,
                2000L,
                new ModifiedBy("Alice's phone", "client-b")));
        items.add(new SyncItem(
                "sync-a",
                "Deleted bread",
                2,
                true,
                1000L,
                new ModifiedBy("Tablet", null)));

        String json = SyncDocumentJson.serialize(new SyncDocument(items));

        assertEquals(
                "{\"schema_version\":1,\"items\":["
                        + "{\"sync_id\":\"sync-a\",\"name\":\"Deleted bread\",\"status\":2,"
                        + "\"deleted\":true,\"modified_at\":1000,\"modified_by\":{\"name\":\"Tablet\"}},"
                        + "{\"sync_id\":\"sync-b\",\"name\":\"Milk\",\"status\":1,"
                        + "\"deleted\":false,\"modified_at\":2000,"
                        + "\"modified_by\":{\"name\":\"Alice's phone\",\"client_id\":\"client-b\"}}]}",
                json,
                "canonical JSON");
        assertDoesNotContain("ultra-secret-token", json, "sync document JSON");
        assertDoesNotContain(credentials.getPasswordOrToken(), json, "sync document JSON");
        assertDoesNotContain("password", json, "sync document JSON");
        assertDoesNotContain("token", json, "sync document JSON");
        assertDoesNotContain("webdav", json, "sync document JSON");

        String serializerSource = readSource(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/document/SyncDocumentJson.java");
        assertDoesNotContain("sync.credentials", serializerSource, "sync document serializer source");
        assertDoesNotContain("WebDavCredentials", serializerSource, "sync document serializer source");
        assertDoesNotContain("passwordOrToken", serializerSource, "sync document serializer source");
    }

    private static void verifyDeterministicSortingForHundredsOfItems() {
        List<SyncItem> items = new ArrayList<SyncItem>();
        for (int i = 249; i >= 0; --i) {
            String syncId = String.format("item-%03d", Integer.valueOf(i));
            items.add(new SyncItem(
                    syncId,
                    "Name " + i,
                    i % 3,
                    i % 11 == 0,
                    100000L + i,
                    new ModifiedBy("device", null)));
        }

        String first = SyncDocumentJson.serialize(new SyncDocument(items));
        String second = SyncDocumentJson.serialize(new SyncDocument(items));

        assertEquals(first, second, "deterministic serialization");
        assertEquals(
                true,
                first.indexOf("\"sync_id\":\"item-000\"") < first.indexOf("\"sync_id\":\"item-249\""),
                "items sorted by sync_id");
        assertEquals(true, first.contains("\"deleted\":true"), "deleted tombstones are included");
    }

    private static void verifyJsonEscapingIsRoundTrippable() throws Exception {
        List<SyncItem> items = new ArrayList<SyncItem>();
        items.add(new SyncItem(
                "escape",
                "Line \"one\"\\two\nthree",
                0,
                false,
                5L,
                new ModifiedBy("Phone\tA", "client\\quote\"")));

        SyncDocument parsed = SyncDocumentJson.parse(SyncDocumentJson.serialize(new SyncDocument(items)));
        SyncItem item = parsed.getItems().get(0);

        assertEquals("Line \"one\"\\two\nthree", item.getName(), "escaped item name");
        assertEquals("Phone\tA", item.getModifiedBy().getName(), "escaped modifier name");
        assertEquals("client\\quote\"", item.getModifiedBy().getClientId(), "escaped client id");
    }

    private static void assertDoesNotContain(String forbidden, String content, String label) {
        if (content.contains(forbidden)) {
            throw new AssertionError("The " + label + " leaks " + forbidden);
        }
    }

    private static String readSource(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("Cannot read source file " + path, e);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Unexpected " + label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
