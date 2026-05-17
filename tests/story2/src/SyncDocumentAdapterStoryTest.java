package story2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import name.soulayrol.rhaa.sholi.sync.document.ModifiedBy;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentItemAdapter;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentJson;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentParseException;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;
import name.soulayrol.rhaa.sholi.sync.items.ItemSyncMetadata;
import name.soulayrol.rhaa.sholi.sync.items.SyncTrackedItem;

public final class SyncDocumentAdapterStoryTest {

    private SyncDocumentAdapterStoryTest() {
    }

    public static void run() throws Exception {
        verifyAdapterExportsAndAppliesBySyncIdWithoutDuplicates();
        verifyFullSnapshotApplyDeletesOnlySafeAbsentTombstones();
        verifyFullSnapshotApplyDeletesExpectedAbsentTombstoneOmittedByCleanup();
        verifyParseAndApplyFailsWithoutLocalDataChanges();
        verifyInvalidRequiredFieldsFailWithoutLocalDataChanges();
        verifySemanticParseFailuresDoNotMutateLocalData();
        verifyDuplicateKeyParseFailureDoesNotMutateLocalData();
    }

    private static void verifyAdapterExportsAndAppliesBySyncIdWithoutDuplicates() {
        FakeStore store = new FakeStore();
        FakeItem existing = new FakeItem(1L, "Old milk", 0);
        existing.setSyncId("sync-a");
        existing.setModifiedAt(Long.valueOf(10L));
        existing.setModifiedByName("old phone");
        existing.setDeleted(Boolean.FALSE);
        store.add(existing);

        List<SyncItem> remoteItems = new ArrayList<SyncItem>();
        remoteItems.add(new SyncItem(
                "sync-a", "New milk", 2, false, 200L, new ModifiedBy("remote", "client-a")));
        remoteItems.add(new SyncItem(
                "sync-b", "Remote bread", 1, true, 300L, new ModifiedBy("tablet", null)));

        SyncDocumentItemAdapter.applyBySyncId(new SyncDocument(remoteItems), store);

        assertEquals(Integer.valueOf(1), Integer.valueOf(store.transactionCount), "transaction count");
        assertEquals(Integer.valueOf(1), Integer.valueOf(store.updateCount), "update count");
        assertEquals(Integer.valueOf(1), Integer.valueOf(store.insertCount), "insert count");
        assertEquals(Integer.valueOf(2), Integer.valueOf(store.items.size()), "no duplicate rows by sync_id");

        FakeItem updated = store.itemBySyncId("sync-a");
        assertEquals(Long.valueOf(1L), updated.getId(), "existing item id retained");
        assertEquals("New milk", updated.getName(), "updated name");
        assertEquals(Integer.valueOf(2), updated.getStatus(), "updated status");
        assertEquals(Long.valueOf(200L), updated.getModifiedAt(), "updated modified_at");
        assertEquals("remote", updated.getModifiedByName(), "updated modified_by.name");
        assertEquals(Boolean.FALSE, updated.getDeleted(), "updated deleted flag");

        FakeItem inserted = store.itemBySyncId("sync-b");
        assertEquals("Remote bread", inserted.getName(), "inserted name");
        assertEquals(Boolean.TRUE, inserted.getDeleted(), "inserted tombstone flag");
        assertEquals(Long.valueOf(300L), inserted.getModifiedAt(), "inserted modified_at");
        assertEquals("tablet", inserted.getModifiedByName(), "inserted modified_by.name");

        String exported = SyncDocumentJson.serialize(SyncDocumentItemAdapter.toDocument(store.items));
        assertEquals(true, exported.contains("\"sync_id\":\"sync-a\""), "exported existing sync id");
        assertEquals(true, exported.contains("\"deleted\":true"), "exported deleted item");
    }

    private static void verifyFullSnapshotApplyDeletesOnlySafeAbsentTombstones() {
        long now = 1000L + ItemSyncMetadata.TOMBSTONE_RETENTION_MILLIS;
        FakeStore store = new FakeStore();
        FakeItem live = trackedItem(1L, "sync-live", "Old milk", 1, false, 10L, "phone");
        FakeItem activeAbsent = trackedItem(2L, "sync-active-absent", "Local eggs", 1, false, 11L, "phone");
        FakeItem expiredTombstone = trackedItem(
                3L,
                "sync-expired-tombstone",
                "Old bread",
                1,
                true,
                12L,
                "phone");
        expiredTombstone.setDeletedSyncedAt(Long.valueOf(1L));
        FakeItem freshTombstone = trackedItem(
                4L,
                "sync-fresh-tombstone",
                "Old tea",
                1,
                true,
                13L,
                "phone");
        freshTombstone.setDeletedSyncedAt(
                Long.valueOf(now - ItemSyncMetadata.TOMBSTONE_RETENTION_MILLIS + 1L));
        FakeItem unsyncedTombstone = trackedItem(
                5L,
                "sync-unsynced-tombstone",
                "Old coffee",
                1,
                true,
                14L,
                "phone");
        store.add(live);
        store.add(activeAbsent);
        store.add(expiredTombstone);
        store.add(freshTombstone);
        store.add(unsyncedTombstone);

        SyncDocument incoming = document(syncItem("sync-live", "Remote milk", 2, false, 20L, "tablet"));
        SyncDocumentItemAdapter.applyFullSnapshotBySyncId(incoming, null, store, now);

        assertEquals(false, store.containsSyncId("sync-expired-tombstone"),
                "expired synced tombstone absent from snapshot is hard-deleted");
        assertEquals(true, store.containsSyncId("sync-active-absent"),
                "active item absent from snapshot is retained");
        assertEquals(true, store.containsSyncId("sync-fresh-tombstone"),
                "fresh synced tombstone absent from snapshot is retained");
        assertEquals(true, store.containsSyncId("sync-unsynced-tombstone"),
                "unsynced tombstone absent from snapshot is retained");
        assertEquals(Integer.valueOf(1), Integer.valueOf(store.deleteCount), "safe absent tombstone delete count");
        assertEquals("Remote milk", store.itemBySyncId("sync-live").getName(), "present item still updates");
    }

    private static void verifyFullSnapshotApplyDeletesExpectedAbsentTombstoneOmittedByCleanup() {
        long now = 5000L;
        FakeStore store = new FakeStore();
        FakeItem activeAbsent = trackedItem(1L, "sync-active-expected", "Local eggs", 1, false, 10L, "phone");
        FakeItem recentlySyncedTombstone = trackedItem(
                2L,
                "sync-recent-tombstone",
                "Old bread",
                1,
                true,
                11L,
                "phone");
        recentlySyncedTombstone.setDeletedSyncedAt(Long.valueOf(now));
        store.add(activeAbsent);
        store.add(recentlySyncedTombstone);

        SyncDocument expected = document(
                syncItem("sync-active-expected", "Local eggs", 1, false, 10L, "phone"),
                syncItem("sync-recent-tombstone", "Old bread", 1, true, 11L, "phone"));
        SyncDocument incoming = document();
        SyncDocumentItemAdapter.applyFullSnapshotBySyncId(incoming, expected, store, now);

        assertEquals(false, store.containsSyncId("sync-recent-tombstone"),
                "expected tombstone omitted by cleanup snapshot is hard-deleted");
        assertEquals(true, store.containsSyncId("sync-active-expected"),
                "expected active item absent from snapshot is retained");
        assertEquals(Integer.valueOf(1), Integer.valueOf(store.deleteCount),
                "cleanup-omitted tombstone delete count");
    }

    private static void verifyParseAndApplyFailsWithoutLocalDataChanges() {
        FakeStore store = new FakeStore();
        FakeItem existing = new FakeItem(1L, "Local milk", 0);
        existing.setSyncId("sync-a");
        existing.setModifiedAt(Long.valueOf(10L));
        existing.setModifiedByName("local");
        existing.setDeleted(Boolean.FALSE);
        store.add(existing);
        Map<String, String> before = store.snapshot();

        try {
            SyncDocumentItemAdapter.parseAndApplyBySyncId(
                    "{\"schema_version\":99,\"items\":[]}",
                    store);
        } catch (SyncDocumentParseException e) {
            assertEquals(
                    SyncDocumentParseException.Reason.UNSUPPORTED_SCHEMA_VERSION,
                    e.getReason(),
                    "future schema rejection reason");
            assertEquals(Integer.valueOf(0), Integer.valueOf(store.transactionCount), "no transaction on parse failure");
            assertEquals(before, store.snapshot(), "store unchanged after parse failure");
            return;
        }
        throw new AssertionError("Expected future schema parse failure");
    }

    private static void verifyInvalidRequiredFieldsFailWithoutLocalDataChanges() {
        FakeStore store = new FakeStore();
        FakeItem existing = new FakeItem(1L, "Local bread", 1);
        existing.setSyncId("sync-b");
        existing.setModifiedAt(Long.valueOf(20L));
        existing.setModifiedByName("local");
        existing.setDeleted(Boolean.FALSE);
        store.add(existing);
        Map<String, String> before = store.snapshot();

        try {
            SyncDocumentItemAdapter.parseAndApplyBySyncId(
                    "{\"schema_version\":1,\"items\":[{\"sync_id\":\"sync-b\","
                            + "\"status\":1,\"deleted\":false,\"modified_at\":1,"
                            + "\"modified_by\":{\"name\":\"remote\"}}]}",
                    store);
        } catch (SyncDocumentParseException e) {
            assertEquals(
                    SyncDocumentParseException.Reason.MISSING_REQUIRED_FIELD,
                    e.getReason(),
                    "missing required field rejection reason");
            assertEquals(Integer.valueOf(0), Integer.valueOf(store.transactionCount), "no transaction on invalid field");
            assertEquals(before, store.snapshot(), "store unchanged after invalid required field");
            return;
        }
        throw new AssertionError("Expected missing required field parse failure");
    }

    private static void verifySemanticParseFailuresDoNotMutateLocalData() {
        assertParseAndApplyFailureDoesNotMutate(
                "{\"schema_version\":1,\"items\":[{\"sync_id\":\"sync-b\","
                        + "\"name\":\"Remote bread\",\"status\":3,\"deleted\":false,"
                        + "\"modified_at\":1,\"modified_by\":{\"name\":\"remote\"}}]}",
                SyncDocumentParseException.Reason.INVALID_FIELD_VALUE,
                "unknown status");
        assertParseAndApplyFailureDoesNotMutate(
                "{\"schema_version\":1,\"items\":[{\"sync_id\":\"sync-b\","
                        + "\"name\":\"Remote bread\",\"status\":1,\"deleted\":false,"
                        + "\"modified_at\":-1,\"modified_by\":{\"name\":\"remote\"}}]}",
                SyncDocumentParseException.Reason.INVALID_FIELD_VALUE,
                "negative modified_at");
    }

    private static void verifyDuplicateKeyParseFailureDoesNotMutateLocalData() {
        assertParseAndApplyFailureDoesNotMutate(
                "{\"schema_version\":1,\"items\":[{\"sync_id\":\"sync-b\","
                        + "\"name\":\"Remote bread\",\"status\":1,\"deleted\":false,"
                        + "\"modified_at\":1,\"modified_by\":{\"name\":\"remote\","
                        + "\"name\":\"tablet\"}}]}",
                SyncDocumentParseException.Reason.MALFORMED_JSON,
                "duplicate modified_by.name");
    }

    private static void assertParseAndApplyFailureDoesNotMutate(
            String json, SyncDocumentParseException.Reason expectedReason, String label) {
        FakeStore store = new FakeStore();
        FakeItem existing = new FakeItem(1L, "Local bread", 1);
        existing.setSyncId("sync-b");
        existing.setModifiedAt(Long.valueOf(20L));
        existing.setModifiedByName("local");
        existing.setDeleted(Boolean.FALSE);
        store.add(existing);
        Map<String, String> before = store.snapshot();

        try {
            SyncDocumentItemAdapter.parseAndApplyBySyncId(json, store);
        } catch (SyncDocumentParseException e) {
            assertEquals(expectedReason, e.getReason(), label + " rejection reason");
            assertEquals(Integer.valueOf(0), Integer.valueOf(store.transactionCount),
                    "no transaction after " + label);
            assertEquals(before, store.snapshot(), "store unchanged after " + label);
            return;
        }
        throw new AssertionError("Expected parse failure for " + label);
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Unexpected " + label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static SyncDocument document(SyncItem... items) {
        ArrayList<SyncItem> list = new ArrayList<SyncItem>();
        for (SyncItem item: items) {
            list.add(item);
        }
        return new SyncDocument(list);
    }

    private static SyncItem syncItem(
            String syncId, String name, int status, boolean deleted, long modifiedAt, String modifiedBy) {
        return new SyncItem(syncId, name, status, deleted, modifiedAt, new ModifiedBy(modifiedBy, null));
    }

    private static FakeItem trackedItem(
            Long id, String syncId, String name, int status, boolean deleted, long modifiedAt, String modifiedBy) {
        FakeItem item = new FakeItem(id, name, Integer.valueOf(status));
        item.setSyncId(syncId);
        item.setDeleted(Boolean.valueOf(deleted));
        item.setModifiedAt(Long.valueOf(modifiedAt));
        item.setModifiedByName(modifiedBy);
        return item;
    }

    private static final class FakeStore implements SyncDocumentItemAdapter.SyncItemStore<FakeItem> {
        private final List<FakeItem> items = new ArrayList<FakeItem>();
        private long nextId = 10L;
        private int transactionCount;
        private int updateCount;
        private int insertCount;
        private int deleteCount;

        private void add(FakeItem item) {
            items.add(item);
        }

        private FakeItem itemBySyncId(String syncId) {
            for (FakeItem item: items) {
                if (syncId.equals(item.getSyncId())) {
                    return item;
                }
            }
            throw new AssertionError("Missing item with sync_id " + syncId);
        }

        private boolean containsSyncId(String syncId) {
            for (FakeItem item: items) {
                if (syncId.equals(item.getSyncId())) {
                    return true;
                }
            }
            return false;
        }

        private Map<String, String> snapshot() {
            Map<String, String> snapshot = new LinkedHashMap<String, String>();
            for (FakeItem item: items) {
                snapshot.put(item.getSyncId(), item.toSnapshotString());
            }
            return snapshot;
        }

        @Override
        public void runInTransaction(Runnable mutation) {
            transactionCount++;
            mutation.run();
        }

        @Override
        public List<FakeItem> loadAll() {
            return new ArrayList<FakeItem>(items);
        }

        @Override
        public FakeItem createItem() {
            FakeItem item = new FakeItem(Long.valueOf(nextId++), "", 0);
            return item;
        }

        @Override
        public void insert(FakeItem item) {
            insertCount++;
            items.add(item);
        }

        @Override
        public void update(FakeItem item) {
            updateCount++;
        }

        @Override
        public void delete(FakeItem item) {
            deleteCount++;
            items.remove(item);
        }
    }

    private static final class FakeItem implements SyncTrackedItem {
        private Long id;
        private String name;
        private Integer status;
        private String syncId;
        private Long modifiedAt;
        private String modifiedByName;
        private Boolean deleted;
        private Long deletedSyncedAt;

        private FakeItem(Long id, String name, Integer status) {
            this.id = id;
            this.name = name;
            this.status = status;
        }

        private String toSnapshotString() {
            return id + "|" + name + "|" + status + "|" + syncId + "|" + modifiedAt
                    + "|" + modifiedByName + "|" + deleted + "|" + deletedSyncedAt;
        }

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }

        @Override
        public Integer getStatus() {
            return status;
        }

        @Override
        public void setStatus(Integer status) {
            this.status = status;
        }

        @Override
        public String getSyncId() {
            return syncId;
        }

        @Override
        public void setSyncId(String syncId) {
            this.syncId = syncId;
        }

        @Override
        public Long getModifiedAt() {
            return modifiedAt;
        }

        @Override
        public void setModifiedAt(Long modifiedAt) {
            this.modifiedAt = modifiedAt;
        }

        @Override
        public String getModifiedByName() {
            return modifiedByName;
        }

        @Override
        public void setModifiedByName(String modifiedByName) {
            this.modifiedByName = modifiedByName;
        }

        @Override
        public Boolean getDeleted() {
            return deleted;
        }

        @Override
        public void setDeleted(Boolean deleted) {
            this.deleted = deleted;
        }

        @Override
        public Long getDeletedSyncedAt() {
            return deletedSyncedAt;
        }

        @Override
        public void setDeletedSyncedAt(Long deletedSyncedAt) {
            this.deletedSyncedAt = deletedSyncedAt;
        }
    }
}
