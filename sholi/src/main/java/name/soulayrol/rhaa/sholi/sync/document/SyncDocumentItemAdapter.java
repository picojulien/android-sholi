package name.soulayrol.rhaa.sholi.sync.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import name.soulayrol.rhaa.sholi.sync.items.ItemSyncMetadata;
import name.soulayrol.rhaa.sholi.sync.items.SyncTrackedItem;

public final class SyncDocumentItemAdapter {

    public interface SyncItemStore<T extends SyncTrackedItem> {
        void runInTransaction(Runnable mutation);

        List<T> loadAll();

        T createItem();

        void insert(T item);

        void update(T item);

        void delete(T item);
    }

    private SyncDocumentItemAdapter() {
    }

    public static SyncDocument toDocument(Iterable<? extends SyncTrackedItem> items) {
        if (items == null) {
            throw new IllegalArgumentException("items must not be null");
        }
        List<SyncItem> syncItems = new ArrayList<SyncItem>();
        for (SyncTrackedItem item: items) {
            syncItems.add(toSyncItem(item));
        }
        return new SyncDocument(syncItems);
    }

    public static SyncItem toSyncItem(SyncTrackedItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        return new SyncItem(
                requireString(item.getSyncId(), "sync_id"),
                requireString(item.getName(), "name"),
                requireInteger(item.getStatus(), "status"),
                Boolean.TRUE.equals(item.getDeleted()),
                requireLong(item.getModifiedAt(), "modified_at"),
                new ModifiedBy(requireString(item.getModifiedByName(), "modified_by.name"), null));
    }

    public static void applyToItem(SyncItem syncItem, SyncTrackedItem item) {
        if (syncItem == null) {
            throw new IllegalArgumentException("syncItem must not be null");
        }
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        item.setSyncId(syncItem.getSyncId());
        item.setName(syncItem.getName());
        item.setStatus(Integer.valueOf(syncItem.getStatus()));
        item.setDeleted(Boolean.valueOf(syncItem.isDeleted()));
        item.setModifiedAt(Long.valueOf(syncItem.getModifiedAt()));
        item.setModifiedByName(syncItem.getModifiedBy().getName());
        item.setDeletedSyncedAt(null);
    }

    public static <T extends SyncTrackedItem> void applyBySyncId(
            final SyncDocument document, final SyncItemStore<T> store) {
        applyBySyncIdInternal(document, null, store, 0L, false);
    }

    public static <T extends SyncTrackedItem> void applyFullSnapshotBySyncId(
            final SyncDocument document,
            final SyncDocument expectedDocument,
            final SyncItemStore<T> store,
            final long now) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        if (now < 0L) {
            throw new IllegalArgumentException("now must not be negative");
        }
        applyBySyncIdInternal(document, expectedDocument, store, now, true);
    }

    private static <T extends SyncTrackedItem> void applyBySyncIdInternal(
            final SyncDocument document,
            final SyncDocument expectedDocument,
            final SyncItemStore<T> store,
            final long now,
            final boolean cleanupMissingTombstones) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        store.runInTransaction(new Runnable() {
            @Override
            public void run() {
                Map<String, SyncItem> incomingBySyncId = mapBySyncId(document);
                Set<String> expectedDeletedSyncIds = deletedSyncIds(expectedDocument);
                List<T> existingItems = store.loadAll();
                Map<String, T> existingBySyncId = new HashMap<String, T>();
                for (T item: existingItems) {
                    if (item.getSyncId() != null && !existingBySyncId.containsKey(item.getSyncId())) {
                        existingBySyncId.put(item.getSyncId(), item);
                    }
                }
                for (SyncItem syncItem: document.getItems()) {
                    T existing = existingBySyncId.get(syncItem.getSyncId());
                    if (existing == null) {
                        T created = store.createItem();
                        applyToItem(syncItem, created);
                        store.insert(created);
                        existingBySyncId.put(syncItem.getSyncId(), created);
                    } else {
                        applyToItem(syncItem, existing);
                        store.update(existing);
                    }
                }
                if (cleanupMissingTombstones) {
                    deleteMissingTombstonesSafeForCleanup(
                            existingItems,
                            incomingBySyncId,
                            expectedDeletedSyncIds,
                            store,
                            now);
                }
            }
        });
    }

    public static <T extends SyncTrackedItem> void parseAndApplyBySyncId(
            String json, SyncItemStore<T> store) throws SyncDocumentParseException {
        SyncDocument document = SyncDocumentJson.parse(json);
        applyBySyncId(document, store);
    }

    private static Map<String, SyncItem> mapBySyncId(SyncDocument document) {
        HashMap<String, SyncItem> bySyncId = new HashMap<String, SyncItem>();
        for (SyncItem item: document.getItems()) {
            bySyncId.put(item.getSyncId(), item);
        }
        return bySyncId;
    }

    private static Set<String> deletedSyncIds(SyncDocument document) {
        HashSet<String> syncIds = new HashSet<String>();
        if (document == null) {
            return syncIds;
        }
        for (SyncItem item: document.getItems()) {
            if (item.isDeleted()) {
                syncIds.add(item.getSyncId());
            }
        }
        return syncIds;
    }

    private static <T extends SyncTrackedItem> void deleteMissingTombstonesSafeForCleanup(
            List<T> existingItems,
            Map<String, SyncItem> incomingBySyncId,
            Set<String> expectedDeletedSyncIds,
            SyncItemStore<T> store,
            long now) {
        for (T item: existingItems) {
            String syncId = item.getSyncId();
            if (syncId == null || incomingBySyncId.containsKey(syncId)) {
                continue;
            }
            if (shouldHardDeleteMissingTombstone(item, expectedDeletedSyncIds, now)) {
                store.delete(item);
            }
        }
    }

    private static boolean shouldHardDeleteMissingTombstone(
            SyncTrackedItem item, Set<String> expectedDeletedSyncIds, long now) {
        if (!Boolean.TRUE.equals(item.getDeleted())) {
            return false;
        }
        if (ItemSyncMetadata.isTombstoneReadyForCleanup(item, now)) {
            return true;
        }
        return item.getDeletedSyncedAt() != null && expectedDeletedSyncIds.contains(item.getSyncId());
    }

    private static String requireString(String value, String field) {
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return value;
    }

    private static int requireInteger(Integer value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value.intValue();
    }

    private static long requireLong(Long value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value.longValue();
    }
}
