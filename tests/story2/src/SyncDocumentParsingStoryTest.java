package story2;

import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentJson;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentParseException;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;

public final class SyncDocumentParsingStoryTest {

    private SyncDocumentParsingStoryTest() {
    }

    public static void run() throws Exception {
        verifyRoundTripPreservesSemanticFields();
        verifyUnknownOptionalFieldsAreIgnored();
        verifyFutureSchemaVersionIsRejected();
        verifyMissingAndInvalidRequiredFieldsAreRejected();
    }

    private static void verifyRoundTripPreservesSemanticFields() throws Exception {
        String json = "{\"schema_version\":1,\"items\":[{"
                + "\"sync_id\":\"stable-id\","
                + "\"name\":\"Oat milk\","
                + "\"status\":2,"
                + "\"deleted\":true,"
                + "\"modified_at\":987654321,"
                + "\"modified_by\":{\"name\":\"Kitchen tablet\",\"client_id\":\"tablet-1\"}"
                + "}]}";

        SyncDocument document = SyncDocumentJson.parse(json);
        SyncItem item = document.getItems().get(0);

        assertEquals(SyncDocument.SCHEMA_VERSION, document.getSchemaVersion(), "schema version");
        assertEquals("stable-id", item.getSyncId(), "sync_id");
        assertEquals("Oat milk", item.getName(), "name");
        assertEquals(Integer.valueOf(2), Integer.valueOf(item.getStatus()), "status");
        assertEquals(Boolean.TRUE, Boolean.valueOf(item.isDeleted()), "deleted");
        assertEquals(Long.valueOf(987654321L), Long.valueOf(item.getModifiedAt()), "modified_at");
        assertEquals("Kitchen tablet", item.getModifiedBy().getName(), "modified_by.name");
        assertEquals("tablet-1", item.getModifiedBy().getClientId(), "modified_by.client_id");
    }

    private static void verifyUnknownOptionalFieldsAreIgnored() throws Exception {
        String json = "{\"schema_version\":1,\"generated_by\":\"future\",\"items\":[{"
                + "\"sync_id\":\"stable-id\","
                + "\"name\":\"Eggs\","
                + "\"status\":1,"
                + "\"deleted\":false,"
                + "\"modified_at\":123,"
                + "\"color\":\"blue\","
                + "\"modified_by\":{\"name\":\"phone\",\"timezone\":\"ignored\"}"
                + "}]}";

        String canonical = SyncDocumentJson.serialize(SyncDocumentJson.parse(json));

        assertEquals(
                "{\"schema_version\":1,\"items\":[{\"sync_id\":\"stable-id\","
                        + "\"name\":\"Eggs\",\"status\":1,\"deleted\":false,"
                        + "\"modified_at\":123,\"modified_by\":{\"name\":\"phone\"}}]}",
                canonical,
                "canonical JSON without unknown optional fields");
    }

    private static void verifyFutureSchemaVersionIsRejected() {
        assertParseFailure(
                "{\"schema_version\":2,\"items\":[]}",
                SyncDocumentParseException.Reason.UNSUPPORTED_SCHEMA_VERSION,
                "future schema version");
    }

    private static void verifyMissingAndInvalidRequiredFieldsAreRejected() {
        assertParseFailure(
                "{\"items\":[]}",
                SyncDocumentParseException.Reason.MISSING_REQUIRED_FIELD,
                "missing schema_version");
        assertParseFailure(
                "{\"schema_version\":1,\"items\":[{\"name\":\"Milk\",\"status\":1,"
                        + "\"deleted\":false,\"modified_at\":1,\"modified_by\":{\"name\":\"phone\"}}]}",
                SyncDocumentParseException.Reason.MISSING_REQUIRED_FIELD,
                "missing sync_id");
        assertParseFailure(
                "{\"schema_version\":1,\"items\":[{\"sync_id\":\"id\",\"name\":\"Milk\","
                        + "\"status\":\"checked\",\"deleted\":false,\"modified_at\":1,"
                        + "\"modified_by\":{\"name\":\"phone\"}}]}",
                SyncDocumentParseException.Reason.INVALID_FIELD_TYPE,
                "invalid status");
        assertParseFailure(
                "{\"schema_version\":1,\"items\":[{\"sync_id\":\"id\",\"name\":\"Milk\","
                        + "\"status\":1,\"deleted\":false,\"modified_at\":1,\"modified_by\":{}}]}",
                SyncDocumentParseException.Reason.MISSING_REQUIRED_FIELD,
                "missing modified_by.name");
        assertParseFailure(
                "{\"schema_version\":1,\"items\":{}}",
                SyncDocumentParseException.Reason.INVALID_FIELD_TYPE,
                "items must be array");
    }

    private static void assertParseFailure(
            String json, SyncDocumentParseException.Reason expectedReason, String label) {
        try {
            SyncDocumentJson.parse(json);
        } catch (SyncDocumentParseException e) {
            assertEquals(expectedReason, e.getReason(), label + " reason");
            return;
        }
        throw new AssertionError("Expected parse failure for " + label);
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Unexpected " + label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
