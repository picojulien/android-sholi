package story5;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import name.soulayrol.rhaa.sholi.sync.document.ModifiedBy;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentJson;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMergeResult;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMerger;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;
import name.soulayrol.rhaa.sholi.sync.merge.SyncStateRecorder;

public final class MergeNonConflictingStoryTest {

    private MergeNonConflictingStoryTest() {
    }

    public static void run() {
        verifyIndependentItemChangesMergeAutomatically();
        verifyIndependentFieldsOnSameItemMergeAutomatically();
        verifyIndependentFieldMergeUsesLocalEvidenceWhenLocalContributionIsNewest();
        verifyLocalOnlyAndRemoteOnlyChangesMerge();
        verifyTombstonePreventsOlderLiveResurrection();
        verifyBaselineTombstoneRejectsOlderLiveRemote();
        verifyBaselineLiveRejectsOlderRemoteTombstone();
        verifyModifiedMetadataAloneDoesNotConflict();
        verifyBaselineAndMarkerUpdateOnlyAfterExplicitSuccess();
    }

    private static void verifyIndependentItemChangesMergeAutomatically() {
        SyncItem baseA = item("sync-a", "Milk", 1, false, 10L, "base");
        SyncItem baseB = item("sync-b", "Bread", 1, false, 11L, "base");
        SyncItem localA = item("sync-a", "Oat milk", 1, false, 20L, "phone");
        SyncItem remoteB = item("sync-b", "Bread", 2, false, 30L, "tablet");

        SyncMergeResult result = SyncMerger.merge(
                document(baseA, baseB),
                document(localA, baseB),
                document(baseA, remoteB),
                "etag-independent",
                1000L);

        assertEquals(false, result.hasConflicts(), "independent changes conflict flag");
        SyncDocument merged = result.getMergedDocument();
        assertItemEquals(localA, byId(merged, "sync-a"), "local item A preserved");
        assertItemEquals(remoteB, byId(merged, "sync-b"), "remote item B preserved");
    }

    private static void verifyIndependentFieldsOnSameItemMergeAutomatically() {
        SyncItem baseline = item("sync-fields", "Rice", 1, false, 10L, "base");
        SyncItem localRenamed = item("sync-fields", "Brown rice", 1, false, 20L, "phone");
        SyncItem remoteChecked = item("sync-fields", "Rice", 2, false, 30L, "tablet");

        SyncMergeResult result = SyncMerger.merge(
                document(baseline),
                document(localRenamed),
                document(remoteChecked),
                "etag-fields",
                1500L);

        assertEquals(false, result.hasConflicts(), "independent same-item fields conflict flag");
        SyncItem merged = byId(result.getMergedDocument(), "sync-fields");
        assertEquals("Brown rice", merged.getName(), "merged same-item name");
        assertEquals(2, merged.getStatus(), "merged same-item status");
        assertEquals(false, merged.isDeleted(), "merged same-item deleted");
        assertEquals(30L, merged.getModifiedAt(), "merged same-item modified_at from newest contributor");
        assertEquals("tablet", merged.getModifiedBy().getName(),
                "merged same-item modified_by from newest contributor");
    }

    private static void verifyIndependentFieldMergeUsesLocalEvidenceWhenLocalContributionIsNewest() {
        SyncItem baseline = item("sync-fields-local-newest", "Rice", 1, false, 10L, "base");
        SyncItem localChecked = item("sync-fields-local-newest", "Rice", 2, false, 50L, "phone");
        SyncItem remoteRenamed = item("sync-fields-local-newest", "Brown rice", 1, false, 30L, "tablet");

        SyncMergeResult result = SyncMerger.merge(
                document(baseline),
                document(localChecked),
                document(remoteRenamed),
                "etag-fields-local-newest",
                1750L);

        assertEquals(false, result.hasConflicts(), "local newest independent fields conflict flag");
        SyncItem merged = byId(result.getMergedDocument(), "sync-fields-local-newest");
        assertEquals("Brown rice", merged.getName(), "local newest merged name");
        assertEquals(2, merged.getStatus(), "local newest merged status");
        assertEquals(50L, merged.getModifiedAt(), "local newest merged modified_at");
        assertEquals("phone", merged.getModifiedBy().getName(), "local newest merged modified_by");
    }

    private static void verifyLocalOnlyAndRemoteOnlyChangesMerge() {
        SyncItem baseLocal = item("sync-local", "Apples", 1, false, 10L, "base");
        SyncItem localChanged = item("sync-local", "Apples", 2, false, 20L, "phone");
        SyncItem baseRemote = item("sync-remote", "Coffee", 1, false, 11L, "base");
        SyncItem remoteChanged = item("sync-remote", "Decaf coffee", 1, false, 30L, "tablet");

        SyncMergeResult result = SyncMerger.merge(
                document(baseLocal, baseRemote),
                document(localChanged, baseRemote),
                document(baseLocal, remoteChanged),
                "etag-local-remote",
                2000L);

        assertEquals(false, result.hasConflicts(), "local-only/remote-only conflict flag");
        SyncDocument merged = result.getMergedDocument();
        assertItemEquals(localChanged, byId(merged, "sync-local"), "local-only version in merge");
        assertItemEquals(remoteChanged, byId(merged, "sync-remote"), "remote-only version in merge");
    }

    private static void verifyTombstonePreventsOlderLiveResurrection() {
        SyncItem baselineLive = item("sync-old", "Yogurt", 1, false, 10L, "base");
        SyncItem localTombstone = item("sync-old", "Yogurt", 1, true, 40L, "phone");

        SyncMergeResult result = SyncMerger.merge(
                document(baselineLive),
                document(localTombstone),
                document(baselineLive),
                "etag-live",
                3000L);

        assertEquals(false, result.hasConflicts(), "old live versus changed tombstone conflict flag");
        assertItemEquals(
                localTombstone,
                byId(result.getMergedDocument(), "sync-old"),
                "changed tombstone wins over unchanged live remote");
    }

    private static void verifyBaselineTombstoneRejectsOlderLiveRemote() {
        SyncItem baselineTombstone = item("sync-baseline-deleted", "Yogurt", 1, true, 40L, "phone");
        SyncItem olderRemoteLive = item("sync-baseline-deleted", "Yogurt", 1, false, 10L, "tablet");

        SyncMergeResult result = SyncMerger.merge(
                document(baselineTombstone),
                document(baselineTombstone),
                document(olderRemoteLive),
                "etag-stale-live",
                3100L);

        assertEquals(false, result.hasConflicts(), "baseline tombstone versus older live conflict flag");
        assertItemEquals(
                baselineTombstone,
                byId(result.getMergedDocument(), "sync-baseline-deleted"),
                "baseline tombstone blocks older live remote");
    }

    private static void verifyBaselineLiveRejectsOlderRemoteTombstone() {
        SyncItem baselineLive = item("sync-baseline-live", "Yogurt", 1, false, 40L, "phone");
        SyncItem olderRemoteTombstone = item("sync-baseline-live", "Yogurt", 1, true, 10L, "tablet");

        SyncMergeResult result = SyncMerger.merge(
                document(baselineLive),
                document(baselineLive),
                document(olderRemoteTombstone),
                "etag-stale-delete",
                3200L);

        assertEquals(false, result.hasConflicts(), "baseline live versus older tombstone conflict flag");
        assertItemEquals(
                baselineLive,
                byId(result.getMergedDocument(), "sync-baseline-live"),
                "baseline live blocks older remote tombstone");
    }

    private static void verifyModifiedMetadataAloneDoesNotConflict() {
        SyncItem baseline = item("sync-meta", "Pears", 1, false, 10L, "base");
        SyncItem localMetadataOnly = item("sync-meta", "Pears", 1, false, 20L, "phone");
        SyncItem remoteMetadataOnly = item("sync-meta", "Pears", 1, false, 30L, "tablet");

        SyncMergeResult result = SyncMerger.merge(
                document(baseline),
                document(localMetadataOnly),
                document(remoteMetadataOnly),
                "etag-meta",
                3500L);

        assertEquals(false, result.hasConflicts(), "metadata-only conflict flag");
        assertEquals("Pears", byId(result.getMergedDocument(), "sync-meta").getName(),
                "metadata-only semantic name");
    }

    private static void verifyBaselineAndMarkerUpdateOnlyAfterExplicitSuccess() {
        SyncItem baseline = item("sync-a", "Milk", 1, false, 10L, "base");
        SyncItem local = item("sync-a", "Oat milk", 1, false, 20L, "phone");
        SyncMergeResult result = SyncMerger.merge(
                document(baseline),
                document(local),
                document(baseline),
                "etag-success",
                4000L);
        RecordingMetadataStore store = new RecordingMetadataStore();

        assertEquals(null, store.loadBaselineDocument(), "baseline before explicit success");
        assertEquals(null, store.loadRemoteVersionMarker(), "marker before explicit success");

        SyncStateRecorder.recordSuccessfulUpload(
                store,
                result.getMergedDocument(),
                "etag-after-upload");

        assertEquals(1, store.transactionCount, "success transaction count");
        assertEquals(
                SyncDocumentJson.serialize(result.getMergedDocument()),
                SyncDocumentJson.serialize(store.loadBaselineDocument()),
                "baseline after explicit success");
        assertEquals("etag-after-upload", store.loadRemoteVersionMarker(), "marker after explicit success");
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

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Unexpected " + label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static final class RecordingMetadataStore implements SyncMetadataStore {
        private SyncDocument baselineDocument;
        private String remoteVersionMarker;
        private List<SyncConflict> conflicts = new ArrayList<SyncConflict>();
        private int transactionCount;

        @Override
        public void runInTransaction(Runnable mutation) {
            transactionCount++;
            mutation.run();
        }

        @Override
        public SyncDocument loadBaselineDocument() {
            return baselineDocument;
        }

        @Override
        public String loadRemoteVersionMarker() {
            return remoteVersionMarker;
        }

        @Override
        public void saveBaselineDocument(SyncDocument document) {
            baselineDocument = document;
        }

        @Override
        public void saveRemoteVersionMarker(String remoteVersionMarker) {
            this.remoteVersionMarker = remoteVersionMarker;
        }

        @Override
        public List<SyncConflict> loadConflicts() {
            return new ArrayList<SyncConflict>(conflicts);
        }

        @Override
        public void replaceConflicts(List<SyncConflict> conflicts) {
            this.conflicts = new ArrayList<SyncConflict>(conflicts);
        }

        @Override
        public void clearConflicts() {
            conflicts.clear();
        }
    }
}
