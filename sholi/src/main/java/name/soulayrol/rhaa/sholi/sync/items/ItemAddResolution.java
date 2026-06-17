package name.soulayrol.rhaa.sholi.sync.items;

public final class ItemAddResolution {

    public enum Action {
        INSERT_NEW,
        RESTORE_TOMBSTONE,
        IGNORE_ACTIVE_DUPLICATE
    }

    private final Action action;
    private final String syncId;

    private ItemAddResolution(Action action, String syncId) {
        this.action = action;
        this.syncId = syncId;
    }

    public static ItemAddResolution resolveForName(String name, SyncTrackedItem existing) {
        return resolve(ItemSyncMetadata.initialSyncIdForName(name), existing);
    }

    public static ItemAddResolution resolve(String syncId, SyncTrackedItem existing) {
        if (existing == null) {
            return new ItemAddResolution(Action.INSERT_NEW, syncId);
        }
        if (Boolean.TRUE.equals(existing.getDeleted())) {
            return new ItemAddResolution(Action.RESTORE_TOMBSTONE, syncId);
        }
        return new ItemAddResolution(Action.IGNORE_ACTIVE_DUPLICATE, syncId);
    }

    public static void restoreTombstone(
            SyncTrackedItem item, int status, long modifiedAt, String modifiedByName) {
        item.setStatus(status);
        ItemSyncMetadata.restore(item, modifiedAt, modifiedByName);
    }

    public Action getAction() {
        return action;
    }

    public String getSyncId() {
        return syncId;
    }
}
