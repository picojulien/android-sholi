package story1;

import name.soulayrol.rhaa.sholi.sync.items.ItemSyncMetadata;
import name.soulayrol.rhaa.sholi.sync.items.SyncTrackedItem;

public final class ItemSyncMetadataStoryTest {

    private ItemSyncMetadataStoryTest() {
    }

    public static void run() {
        verifyCanonicalizationAndStableHashing();
        verifyMutationsTouchMetadataWithoutChangingSyncId();
        verifyDeletionTombstoneRetentionHelpers();
    }

    private static void verifyCanonicalizationAndStableHashing() {
        assertEquals(
                "milk chocolate",
                ItemSyncMetadata.canonicalizeName("  MILK\t  Chocolate \n"),
                "canonical item name");
        assertEquals(
                "608667a0d12e5fd56670db0baf9364d2982f9d6f27dad4ef0552c6203f336fcf",
                ItemSyncMetadata.initialSyncIdForName("  MILK\t  Chocolate \n"),
                "stable sync id");
    }

    private static void verifyMutationsTouchMetadataWithoutChangingSyncId() {
        FakeItem item = new FakeItem(42L, "Milk", 0);
        ItemSyncMetadata.initializeNewItem(item, 1000L, "phone-a");

        assertEquals(
                "0309fd6921255a0f39bcf25e963e066a4f8b7077a70e2fd05cae63883a3180d7",
                item.getSyncId(),
                "initial item sync id");
        assertEquals(Long.valueOf(1000L), item.getModifiedAt(), "initial modified_at");
        assertEquals("phone-a", item.getModifiedByName(), "initial modified_by.name");
        assertEquals(Boolean.FALSE, item.getDeleted(), "initial deleted flag");

        item.setName("Bread");
        item.setStatus(1);
        ItemSyncMetadata.touch(item, 2000L, "phone-b");

        assertEquals(
                "0309fd6921255a0f39bcf25e963e066a4f8b7077a70e2fd05cae63883a3180d7",
                item.getSyncId(),
                "sync id remains stable after edits");
        assertEquals(Long.valueOf(2000L), item.getModifiedAt(), "edited modified_at");
        assertEquals("phone-b", item.getModifiedByName(), "edited modified_by.name");
    }

    private static void verifyDeletionTombstoneRetentionHelpers() {
        FakeItem item = new FakeItem(7L, "Cheese", 0);
        ItemSyncMetadata.initializeNewItem(item, 1000L, "phone-a");
        item.setDeletedSyncedAt(900L);

        ItemSyncMetadata.markDeleted(item, 3000L, "phone-b");
        assertEquals(Boolean.TRUE, item.getDeleted(), "deleted flag");
        assertEquals(null, item.getDeletedSyncedAt(), "deleted_synced_at reset on deletion");
        assertEquals(Long.valueOf(3000L), item.getModifiedAt(), "deletion modified_at");
        assertEquals("phone-b", item.getModifiedByName(), "deletion modified_by.name");

        ItemSyncMetadata.markDeletedSynced(item, 4000L);
        assertEquals(Long.valueOf(4000L), item.getDeletedSyncedAt(), "deleted_synced_at set");
        assertEquals(
                false,
                ItemSyncMetadata.isTombstoneReadyForCleanup(
                        item, 4000L + ItemSyncMetadata.TOMBSTONE_RETENTION_MILLIS - 1L),
                "tombstone retained before 30 days");
        assertEquals(
                true,
                ItemSyncMetadata.isTombstoneReadyForCleanup(
                        item, 4000L + ItemSyncMetadata.TOMBSTONE_RETENTION_MILLIS),
                "tombstone cleanup eligibility after 30 days");
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
