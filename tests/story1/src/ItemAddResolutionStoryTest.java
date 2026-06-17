package story1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import name.soulayrol.rhaa.sholi.sync.items.ItemAddResolution;
import name.soulayrol.rhaa.sholi.sync.items.ItemSyncMetadata;
import name.soulayrol.rhaa.sholi.sync.items.SyncTrackedItem;

public final class ItemAddResolutionStoryTest {

    private ItemAddResolutionStoryTest() {
    }

    public static void run() throws IOException {
        verifyActiveCanonicalDuplicateIsIgnored();
        verifyTombstoneCanonicalDuplicateIsRestored();
        verifyNewCanonicalNameGetsDeterministicSyncId();
        verifyEditFragmentLooksUpAddsBySyncId();
    }

    private static void verifyActiveCanonicalDuplicateIsIgnored() {
        FakeItem active = new FakeItem(1L, "Milk", 0);
        ItemSyncMetadata.initializeNewItem(active, 1000L, "phone-a");

        ItemAddResolution resolution = ItemAddResolution.resolveForName("  milk\t", active);

        assertEquals(
                ItemAddResolution.Action.IGNORE_ACTIVE_DUPLICATE,
                resolution.getAction(),
                "active canonical duplicate action");
        assertEquals(active.getSyncId(), resolution.getSyncId(), "active canonical duplicate sync id");
        assertEquals(Boolean.FALSE, active.getDeleted(), "active duplicate remains active");
    }

    private static void verifyTombstoneCanonicalDuplicateIsRestored() {
        FakeItem tombstone = new FakeItem(2L, "  Milk ", 1);
        ItemSyncMetadata.initializeNewItem(tombstone, 1000L, "phone-a");
        String originalSyncId = tombstone.getSyncId();
        ItemSyncMetadata.markDeleted(tombstone, 1500L, "phone-a");
        tombstone.setDeletedSyncedAt(1600L);

        ItemAddResolution resolution = ItemAddResolution.resolveForName("MILK", tombstone);
        assertEquals(
                ItemAddResolution.Action.RESTORE_TOMBSTONE,
                resolution.getAction(),
                "tombstone canonical duplicate action");
        assertEquals(originalSyncId, resolution.getSyncId(), "tombstone canonical duplicate sync id");

        ItemAddResolution.restoreTombstone(tombstone, 0, 2000L, "phone-b");
        assertEquals(originalSyncId, tombstone.getSyncId(), "restored tombstone stable sync id");
        assertEquals(Integer.valueOf(0), tombstone.getStatus(), "restored tombstone status");
        assertEquals(Boolean.FALSE, tombstone.getDeleted(), "restored tombstone deleted flag");
        assertEquals(null, tombstone.getDeletedSyncedAt(), "restored tombstone deleted_synced_at");
        assertEquals(Long.valueOf(2000L), tombstone.getModifiedAt(), "restored tombstone modified_at");
        assertEquals("phone-b", tombstone.getModifiedByName(), "restored tombstone modified_by.name");
    }

    private static void verifyNewCanonicalNameGetsDeterministicSyncId() {
        ItemAddResolution resolution = ItemAddResolution.resolveForName("  Bread\tFlour  ", null);

        assertEquals(ItemAddResolution.Action.INSERT_NEW, resolution.getAction(), "new item action");
        assertEquals(
                ItemSyncMetadata.initialSyncIdForName("Bread Flour"),
                resolution.getSyncId(),
                "new item deterministic sync id");
    }

    private static void verifyEditFragmentLooksUpAddsBySyncId() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/EditFragment.java")),
                StandardCharsets.UTF_8);

        assertContains(source, "ItemSyncMetadata.initialSyncIdForName(name)",
                "EditFragment computes canonical add sync id");
        assertContains(source, "ItemDao.Properties.SyncId.eq(syncId)",
                "EditFragment add lookup uses sync_id");
        assertNotContains(source, "findItemByName",
                "EditFragment add lookup must not use exact display name only");
    }

    private static void assertContains(String value, String expected, String label) {
        if (value.indexOf(expected) < 0) {
            throw new AssertionError("Missing " + label + ": " + expected);
        }
    }

    private static void assertNotContains(String value, String unexpected, String label) {
        if (value.indexOf(unexpected) >= 0) {
            throw new AssertionError("Unexpected " + label + ": " + unexpected);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Unexpected " + label + ": expected=" + expected + " actual=" + actual);
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
