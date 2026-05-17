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
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;
import name.soulayrol.rhaa.sholi.sync.merge.ConflictChoice;
import name.soulayrol.rhaa.sholi.sync.merge.ConflictResolution;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMergeResult;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMerger;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;
import name.soulayrol.rhaa.sholi.sync.merge.SyncStateRecorder;

public final class ConflictResolutionStoryTest {

    private ConflictResolutionStoryTest() {
    }

    public static void run() throws Exception {
        verifySameNameFieldChangeCreatesConflict();
        verifyStatusFieldConflict();
        verifyDeleteVersusLiveEditConflict();
        verifyConflictRecordsPersistAcrossFakeReload();
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

    private static void verifyConflictRecordsPersistAcrossFakeReload() {
        SyncItem baseline = item("sync-persist", "Tea", 1, false, 10L, "base");
        SyncItem local = item("sync-persist", "Green tea", 1, false, 20L, "phone");
        SyncItem remote = item("sync-persist", "Black tea", 1, false, 30L, "tablet");
        SyncMergeResult result = SyncMerger.merge(
                document(baseline), document(local), document(remote), "etag-persist", 4000L);
        ReloadableMetadataStore.Backing backing = new ReloadableMetadataStore.Backing();
        ReloadableMetadataStore firstOpen = new ReloadableMetadataStore(backing);

        firstOpen.replaceConflicts(result.getConflicts());
        ReloadableMetadataStore reloaded = new ReloadableMetadataStore(backing);
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

    private static void verifyUnresolvedConflictsBlockMergedUploadDocument() {
        SyncMergeResult result = conflictingResult("sync-block");
        ReloadableMetadataStore store = new ReloadableMetadataStore(new ReloadableMetadataStore.Backing());
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

    private static final class ReloadableMetadataStore implements SyncMetadataStore {
        private final Backing backing;

        private ReloadableMetadataStore(Backing backing) {
            this.backing = backing;
        }

        @Override
        public void runInTransaction(Runnable mutation) {
            mutation.run();
        }

        @Override
        public SyncDocument loadBaselineDocument() {
            return backing.baselineDocument;
        }

        @Override
        public String loadRemoteVersionMarker() {
            return backing.remoteVersionMarker;
        }

        @Override
        public void saveBaselineDocument(SyncDocument document) {
            backing.baselineDocument = document;
        }

        @Override
        public void saveRemoteVersionMarker(String remoteVersionMarker) {
            backing.remoteVersionMarker = remoteVersionMarker;
        }

        @Override
        public List<SyncConflict> loadConflicts() {
            return new ArrayList<SyncConflict>(backing.conflicts.values());
        }

        @Override
        public void replaceConflicts(List<SyncConflict> conflicts) {
            backing.conflicts.clear();
            for (SyncConflict conflict: conflicts) {
                backing.conflicts.put(conflict.getSyncId(), conflict);
            }
        }

        @Override
        public void clearConflicts() {
            backing.conflicts.clear();
        }

        private static final class Backing {
            private SyncDocument baselineDocument;
            private String remoteVersionMarker;
            private final Map<String, SyncConflict> conflicts = new LinkedHashMap<String, SyncConflict>();
        }
    }
}
