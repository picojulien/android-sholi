package story6;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import name.soulayrol.rhaa.sholi.sync.document.ModifiedBy;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentJson;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;
import name.soulayrol.rhaa.sholi.sync.merge.ConflictChoice;
import name.soulayrol.rhaa.sholi.sync.merge.ConflictResolution;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMergeResult;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMerger;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataPersistence;
import name.soulayrol.rhaa.sholi.sync.merge.SyncStateRecorder;

public final class ConflictResolutionStoryTest {

    private ConflictResolutionStoryTest() {
    }

    public static void run() throws Exception {
        verifySameNameFieldChangeCreatesConflict();
        verifyStatusFieldConflict();
        verifyDeleteVersusLiveEditConflict();
        verifyLocalDeleteVersusRemoteLiveEditConflict();
        verifyBaselineAndMarkerRoundTripThroughPersistenceCodec();
        verifyConflictRecordsPersistAcrossFakeReload();
        verifyPendingConflictStatePersistsAcrossFakeReloadAndClears();
        verifyConflictReplaceClearSequencingAndTransactionCallback();
        verifyUnresolvedConflictsBlockMergedUploadDocument();
        verifyResolvingLocalVersusRemotePicksCompleteVersion();
        verifySqliteMetadataStoreWiringIsExplicitAndTransactional();
    }

    private static void verifySameNameFieldChangeCreatesConflict() {
        SyncItem baseline = item("sync-a", "Milk", 1, false, 10L, "base");
        SyncItem local = item("sync-a", "Oat milk", 2, false, 20L, "phone");
        SyncItem remote = item("sync-a", "Soy milk", 1, false, 30L, "tablet");

        SyncMergeResult result = SyncMerger.merge(
                document(baseline), document(local), document(remote), "etag-name", 1000L);

        assertEquals(true, result.hasConflicts(), "name conflict flag");
        assertEquals(1, result.getConflicts().size(), "name conflict count");
        assertEquals("sync-a", result.getConflicts().get(0).getSyncId(), "name conflict sync_id");
        assertEquals(true, result.getConflicts().get(0).getConflictingFields().contains("name"),
                "name conflict field");
    }

    private static void verifyStatusFieldConflict() {
        SyncItem baseline = item("sync-status", "Bread", 1, false, 10L, "base");
        SyncItem local = item("sync-status", "Bread", 2, false, 20L, "phone");
        SyncItem remote = item("sync-status", "Bread", 0, false, 30L, "tablet");

        SyncMergeResult result = SyncMerger.merge(
                document(baseline), document(local), document(remote), "etag-status", 2000L);

        assertEquals(true, result.hasConflicts(), "status conflict flag");
        assertEquals(true, result.getConflicts().get(0).getConflictingFields().contains("status"),
                "status conflict field");
    }

    private static void verifyDeleteVersusLiveEditConflict() {
        SyncItem baseline = item("sync-delete", "Cheese", 1, false, 10L, "base");
        SyncItem localEditedLive = item("sync-delete", "Blue cheese", 1, false, 20L, "phone");
        SyncItem remoteDeleted = item("sync-delete", "Cheese", 1, true, 30L, "tablet");

        SyncMergeResult result = SyncMerger.merge(
                document(baseline),
                document(localEditedLive),
                document(remoteDeleted),
                "etag-delete",
                3000L);

        assertEquals(true, result.hasConflicts(), "delete versus edit conflict flag");
        assertEquals(true, result.getConflicts().get(0).getConflictingFields().contains("deleted"),
                "deleted conflict field");
        assertEquals(true, result.getConflicts().get(0).getConflictingFields().contains("name"),
                "live edit conflict field");
    }

    private static void verifyLocalDeleteVersusRemoteLiveEditConflict() {
        SyncItem baseline = item("sync-delete-inverse", "Cheese", 1, false, 10L, "base");
        SyncItem localDeleted = item("sync-delete-inverse", "Cheese", 1, true, 80L, "phone");
        SyncItem remoteEditedLive = item("sync-delete-inverse", "Blue cheese", 1, false, 20L, "tablet");

        SyncMergeResult result = SyncMerger.merge(
                document(baseline),
                document(localDeleted),
                document(remoteEditedLive),
                "etag-delete-inverse",
                3500L);

        assertEquals(true, result.hasConflicts(), "local delete versus remote live edit conflict flag");
        assertEquals(true, result.getConflicts().get(0).getConflictingFields().contains("deleted"),
                "inverse deleted conflict field");
        assertEquals(true, result.getConflicts().get(0).getConflictingFields().contains("name"),
                "inverse live edit conflict field");
    }

    private static void verifyBaselineAndMarkerRoundTripThroughPersistenceCodec() {
        RecordingMetadataStorage storage = new RecordingMetadataStorage();
        SyncMetadataStore store = new SyncMetadataPersistence(storage);
        SyncDocument baseline = document(
                item("sync-baseline-a", "Tea", 1, false, 10L, "base"),
                item("sync-baseline-b", "Coffee", 2, true, 20L, "phone"));

        store.saveBaselineDocument(baseline);
        store.saveRemoteVersionMarker("etag-baseline-round-trip");

        SyncMetadataStore reloaded = new SyncMetadataPersistence(storage);
        assertEquals(
                SyncDocumentJson.serialize(baseline),
                SyncDocumentJson.serialize(reloaded.loadBaselineDocument()),
                "baseline round-trip encoding");
        assertEquals(
                "etag-baseline-round-trip",
                reloaded.loadRemoteVersionMarker(),
                "marker round-trip encoding");
    }

    private static void verifyConflictRecordsPersistAcrossFakeReload() {
        SyncItem baseline = item("sync-persist", "Tea", 1, false, 10L, "base");
        SyncItem local = item("sync-persist", "Green tea", 1, false, 20L, "phone");
        SyncItem remote = item("sync-persist", "Black tea", 1, false, 30L, "tablet");
        SyncMergeResult result = SyncMerger.merge(
                document(baseline), document(local), document(remote), "etag-persist", 4000L);
        RecordingMetadataStorage storage = new RecordingMetadataStorage();
        SyncMetadataStore firstOpen = new SyncMetadataPersistence(storage);

        firstOpen.replaceConflicts(result.getConflicts());
        SyncMetadataStore reloaded = new SyncMetadataPersistence(storage);
        List<SyncConflict> conflicts = reloaded.loadConflicts();

        assertEquals(1, conflicts.size(), "persisted conflict count");
        SyncConflict conflict = conflicts.get(0);
        assertEquals("sync-persist", conflict.getSyncId(), "persisted sync_id");
        assertItemEquals(baseline, conflict.getBaselineItem(), "persisted baseline snapshot");
        assertItemEquals(local, conflict.getLocalItem(), "persisted local snapshot");
        assertItemEquals(remote, conflict.getRemoteItem(), "persisted remote snapshot");
        assertEquals("etag-persist", conflict.getRemoteVersionMarker(), "persisted marker");
        assertEquals(4000L, conflict.getConflictTimestamp(), "persisted timestamp");
        assertEquals(SyncConflict.STATUS_UNRESOLVED, conflict.getStatus(), "persisted status");
    }

    private static void verifyPendingConflictStatePersistsAcrossFakeReloadAndClears() {
        SyncItem baseA = item("sync-pending-a", "Milk", 1, false, 10L, "base");
        SyncItem baseB = item("sync-pending-b", "Bread", 1, false, 11L, "base");
        SyncItem localA = item("sync-pending-a", "Oat milk", 1, false, 20L, "phone");
        SyncItem remoteA = item("sync-pending-a", "Soy milk", 1, false, 30L, "tablet");
        SyncItem remoteB = item("sync-pending-b", "Bread", 2, false, 40L, "tablet");
        SyncDocument baseline = document(baseA, baseB);
        SyncDocument local = document(localA, baseB);
        SyncDocument remote = document(remoteA, remoteB);
        SyncDocument pendingMerged = document(remoteB);
        RecordingMetadataStorage storage = new RecordingMetadataStorage();
        SyncMetadataStore firstOpen = new SyncMetadataPersistence(storage);
        SyncMergeResult result = SyncMerger.merge(baseline, local, remote, "etag-pending", 4500L);

        SyncStateRecorder.persistConflicts(
                firstOpen,
                result.getConflicts(),
                result.getPendingMergedDocument(),
                local);
        SyncMetadataStore reloaded = new SyncMetadataPersistence(storage);

        assertEquals(1, reloaded.loadConflicts().size(), "pending conflict count");
        assertEquals(
                SyncDocumentJson.serialize(pendingMerged),
                SyncDocumentJson.serialize(reloaded.loadPendingMergedDocument()),
                "pending merged document round-trip");
        assertEquals(
                SyncDocumentJson.serialize(local),
                SyncDocumentJson.serialize(reloaded.loadPendingLocalDocument()),
                "pending local document round-trip");
        assertEvents(
                list("tx:start", "delete-conflicts", "insert-conflict:sync-pending-a",
                        "save-pending-merged", "save-pending-local", "tx:end"),
                storage.events,
                "pending conflict state persistence order");

        storage.events.clear();
        reloaded.clearConflicts();

        assertEquals(0, reloaded.loadConflicts().size(), "pending conflict clear count");
        assertEquals(null, reloaded.loadPendingMergedDocument(), "pending merged cleared");
        assertEquals(null, reloaded.loadPendingLocalDocument(), "pending local cleared");
        assertEvents(
                list("delete-conflicts", "save-pending-merged", "save-pending-local"),
                storage.events,
                "pending conflict state clear order");
    }

    private static void verifyConflictReplaceClearSequencingAndTransactionCallback() {
        SyncConflict first = conflict(
                "sync-replace-a",
                item("sync-replace-a", "Apples", 1, false, 10L, "base"),
                item("sync-replace-a", "Green apples", 1, false, 20L, "phone"),
                item("sync-replace-a", "Red apples", 1, false, 30L, "tablet"));
        SyncConflict second = conflict(
                "sync-replace-b",
                item("sync-replace-b", "Bread", 1, false, 10L, "base"),
                item("sync-replace-b", "Bread", 2, false, 20L, "phone"),
                item("sync-replace-b", "Bread", 0, false, 30L, "tablet"));
        ArrayList<SyncConflict> conflicts = new ArrayList<SyncConflict>();
        conflicts.add(first);
        conflicts.add(second);
        RecordingMetadataStorage storage = new RecordingMetadataStorage();
        SyncMetadataStore store = new SyncMetadataPersistence(storage);

        store.replaceConflicts(conflicts);

        assertEquals(1, storage.transactionCount, "replace conflict transaction callback count");
        assertEvents(
                list("tx:start", "delete-conflicts", "insert-conflict:sync-replace-a",
                        "insert-conflict:sync-replace-b", "tx:end"),
                storage.events,
                "replace conflict operation order");

        store.replaceConflicts(Collections.singletonList(second));
        List<SyncConflict> replaced = store.loadConflicts();
        assertEquals(1, replaced.size(), "replacement removes prior unresolved conflict count");
        assertConflictEquals(second, replaced.get(0), "replacement keeps new unresolved conflict");

        storage.events.clear();
        store.clearConflicts();
        assertEvents(
                list("delete-conflicts", "save-pending-merged", "save-pending-local"),
                storage.events,
                "clear conflict operation order");
        assertEquals(0, store.loadConflicts().size(), "clear removes unresolved conflicts");
    }

    private static void verifyUnresolvedConflictsBlockMergedUploadDocument() {
        SyncMergeResult result = conflictingResult("sync-block");
        SyncMetadataStore store = new SyncMetadataPersistence(new RecordingMetadataStorage());
        store.replaceConflicts(result.getConflicts());

        assertEquals(false, result.canProduceUploadDocument(), "conflicted upload availability");
        assertEquals(true, SyncStateRecorder.hasUnresolvedConflicts(store), "unresolved conflict gate");
        try {
            result.getMergedDocument();
        } catch (IllegalStateException e) {
            return;
        }
        throw new AssertionError("Expected unresolved conflict to block merged document");
    }

    private static void verifyResolvingLocalVersusRemotePicksCompleteVersion() {
        SyncMergeResult localResult = conflictingResult("sync-resolve-local");
        SyncConflict localConflict = localResult.getConflicts().get(0);
        SyncDocument localResolved = ConflictResolution.resolveAll(
                localResult,
                Collections.singletonMap(localConflict.getSyncId(), ConflictChoice.LOCAL));
        assertItemEquals(
                localConflict.getLocalItem(),
                byId(localResolved, localConflict.getSyncId()),
                "resolved local complete version");

        SyncMergeResult remoteResult = conflictingResult("sync-resolve-remote");
        SyncConflict remoteConflict = remoteResult.getConflicts().get(0);
        SyncDocument remoteResolved = ConflictResolution.resolveAll(
                remoteResult,
                Collections.singletonMap(remoteConflict.getSyncId(), ConflictChoice.REMOTE));
        assertItemEquals(
                remoteConflict.getRemoteItem(),
                byId(remoteResolved, remoteConflict.getSyncId()),
                "resolved remote complete version");

        try {
            ConflictResolution.resolveAll(remoteResult, new HashMap<String, ConflictChoice>());
        } catch (IllegalStateException e) {
            return;
        }
        throw new AssertionError("Expected unresolved choices to block resolved document");
    }

    private static void verifySqliteMetadataStoreWiringIsExplicitAndTransactional() throws Exception {
        String operations = readUtf8("sholi/src/main/java/name/soulayrol/rhaa/sholi/data/Operations.java");
        assertContains(operations, "openWebDavSyncMetadataStore", "Operations sync metadata entry point");

        String sqliteStore = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/data/SqliteSyncMetadataStore.java");
        assertContains(sqliteStore, "DaoSession", "SQLite sync metadata store");
        assertContains(sqliteStore, "runInTx", "SQLite sync metadata store");
        assertContains(sqliteStore, "CREATE TABLE IF NOT EXISTS", "SQLite sync metadata store");
        assertContains(sqliteStore, "sync_baseline", "baseline table");
        assertContains(sqliteStore, "sync_conflicts", "conflicts table");
        assertEquals(false, sqliteStore.toLowerCase().contains("password"), "no secret fields in sync metadata store");
        assertEquals(false, sqliteStore.toLowerCase().contains("token"), "no token fields in sync metadata store");
    }

    private static SyncMergeResult conflictingResult(String syncId) {
        SyncItem baseline = item(syncId, "Cereal", 1, false, 10L, "base");
        SyncItem local = item(syncId, "Granola", 2, false, 20L, "phone");
        SyncItem remote = item(syncId, "Muesli", 1, false, 30L, "tablet");
        return SyncMerger.merge(document(baseline), document(local), document(remote), "etag", 5000L);
    }

    private static SyncConflict conflict(String syncId, SyncItem baseline, SyncItem local, SyncItem remote) {
        ArrayList<String> fields = new ArrayList<String>();
        fields.add("name");
        return new SyncConflict(
                syncId,
                baseline,
                local,
                remote,
                "etag-conflict",
                6000L,
                SyncConflict.STATUS_UNRESOLVED,
                fields);
    }

    private static SyncItem item(
            String syncId, String name, int status, boolean deleted, long modifiedAt, String modifiedBy) {
        return new SyncItem(syncId, name, status, deleted, modifiedAt, new ModifiedBy(modifiedBy, null));
    }

    private static SyncDocument document(SyncItem... items) {
        List<SyncItem> list = new ArrayList<SyncItem>();
        for (SyncItem item: items) {
            list.add(item);
        }
        return new SyncDocument(list);
    }

    private static SyncItem byId(SyncDocument document, String syncId) {
        for (SyncItem item: document.getItems()) {
            if (syncId.equals(item.getSyncId())) {
                return item;
            }
        }
        throw new AssertionError("Missing sync_id " + syncId);
    }

    private static void assertItemEquals(SyncItem expected, SyncItem actual, String label) {
        assertEquals(expected.getSyncId(), actual.getSyncId(), label + " sync_id");
        assertEquals(expected.getName(), actual.getName(), label + " name");
        assertEquals(expected.getStatus(), actual.getStatus(), label + " status");
        assertEquals(expected.isDeleted(), actual.isDeleted(), label + " deleted");
        assertEquals(expected.getModifiedAt(), actual.getModifiedAt(), label + " modified_at");
        assertEquals(
                expected.getModifiedBy().getName(),
                actual.getModifiedBy().getName(),
                label + " modified_by.name");
    }

    private static void assertConflictEquals(SyncConflict expected, SyncConflict actual, String label) {
        assertEquals(expected.getSyncId(), actual.getSyncId(), label + " sync_id");
        assertItemEqualsOrNull(expected.getBaselineItem(), actual.getBaselineItem(), label + " baseline");
        assertItemEqualsOrNull(expected.getLocalItem(), actual.getLocalItem(), label + " local");
        assertItemEqualsOrNull(expected.getRemoteItem(), actual.getRemoteItem(), label + " remote");
        assertEquals(expected.getRemoteVersionMarker(), actual.getRemoteVersionMarker(), label + " marker");
        assertEquals(expected.getConflictTimestamp(), actual.getConflictTimestamp(), label + " timestamp");
        assertEquals(expected.getStatus(), actual.getStatus(), label + " status");
        assertEquals(expected.getConflictingFields(), actual.getConflictingFields(), label + " fields");
    }

    private static void assertItemEqualsOrNull(SyncItem expected, SyncItem actual, String label) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual, label);
            return;
        }
        assertItemEquals(expected, actual, label);
    }

    private static void assertEvents(List<String> expected, List<String> actual, String label) {
        assertEquals(expected.size(), actual.size(), label + " size");
        for (int i = 0; i < expected.size(); ++i) {
            assertEquals(expected.get(i), actual.get(i), label + "[" + i + "]");
        }
    }

    private static void assertContains(String content, String expected, String label) {
        if (!content.contains(expected)) {
            throw new AssertionError("Expected " + label + " to contain " + expected);
        }
    }

    private static String readUtf8(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), "UTF-8");
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Unexpected " + label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static List<String> list(String... values) {
        ArrayList<String> list = new ArrayList<String>();
        for (String value: values) {
            list.add(value);
        }
        return list;
    }

    private static final class RecordingMetadataStorage implements SyncMetadataPersistence.Storage {

        private String baselineDocumentJson;
        private String remoteVersionMarker;
        private String pendingMergedDocumentJson;
        private String pendingLocalDocumentJson;
        private final Map<String, SyncMetadataPersistence.ConflictRecord> conflicts =
                new LinkedHashMap<String, SyncMetadataPersistence.ConflictRecord>();
        private final List<String> events = new ArrayList<String>();
        private int transactionCount;

        @Override
        public void runInTransaction(Runnable mutation) {
            transactionCount++;
            events.add("tx:start");
            mutation.run();
            events.add("tx:end");
        }

        @Override
        public String loadBaselineDocumentJson() {
            return baselineDocumentJson;
        }

        @Override
        public String loadRemoteVersionMarker() {
            return remoteVersionMarker;
        }

        @Override
        public String loadPendingMergedDocumentJson() {
            return pendingMergedDocumentJson;
        }

        @Override
        public String loadPendingLocalDocumentJson() {
            return pendingLocalDocumentJson;
        }

        @Override
        public void saveBaselineDocumentJson(String baselineDocumentJson) {
            this.baselineDocumentJson = baselineDocumentJson;
        }

        @Override
        public void saveRemoteVersionMarker(String remoteVersionMarker) {
            this.remoteVersionMarker = remoteVersionMarker;
        }

        @Override
        public void savePendingMergedDocumentJson(String pendingMergedDocumentJson) {
            events.add("save-pending-merged");
            this.pendingMergedDocumentJson = pendingMergedDocumentJson;
        }

        @Override
        public void savePendingLocalDocumentJson(String pendingLocalDocumentJson) {
            events.add("save-pending-local");
            this.pendingLocalDocumentJson = pendingLocalDocumentJson;
        }

        @Override
        public List<SyncMetadataPersistence.ConflictRecord> loadConflictRecords() {
            return new ArrayList<SyncMetadataPersistence.ConflictRecord>(conflicts.values());
        }

        @Override
        public void deleteAllConflictRecords() {
            events.add("delete-conflicts");
            conflicts.clear();
        }

        @Override
        public void saveConflictRecord(SyncMetadataPersistence.ConflictRecord record) {
            events.add("insert-conflict:" + record.getSyncId());
            conflicts.put(record.getSyncId(), record);
        }
    }

}
