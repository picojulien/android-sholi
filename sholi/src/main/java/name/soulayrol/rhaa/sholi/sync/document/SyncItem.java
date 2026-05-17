package name.soulayrol.rhaa.sholi.sync.document;

public final class SyncItem {

    public static final int STATUS_OFF_LIST = 0;
    public static final int STATUS_UNCHECKED = 1;
    public static final int STATUS_CHECKED = 2;

    private final String syncId;
    private final String name;
    private final int status;
    private final boolean deleted;
    private final long modifiedAt;
    private final ModifiedBy modifiedBy;

    public SyncItem(
            String syncId,
            String name,
            int status,
            boolean deleted,
            long modifiedAt,
            ModifiedBy modifiedBy) {
        if (isEmpty(syncId)) {
            throw new IllegalArgumentException("sync_id must not be empty");
        }
        if (isEmpty(name)) {
            throw new IllegalArgumentException("name must not be empty");
        }
        if (!isValidStatus(status)) {
            throw new IllegalArgumentException("status must be 0, 1, or 2");
        }
        if (modifiedAt < 0L) {
            throw new IllegalArgumentException("modified_at must not be negative");
        }
        if (modifiedBy == null) {
            throw new IllegalArgumentException("modified_by must not be null");
        }
        this.syncId = syncId;
        this.name = name;
        this.status = status;
        this.deleted = deleted;
        this.modifiedAt = modifiedAt;
        this.modifiedBy = modifiedBy;
    }

    public String getSyncId() {
        return syncId;
    }

    public String getName() {
        return name;
    }

    public int getStatus() {
        return status;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }

    public ModifiedBy getModifiedBy() {
        return modifiedBy;
    }

    public static boolean isValidStatus(int status) {
        return status == STATUS_OFF_LIST || status == STATUS_UNCHECKED || status == STATUS_CHECKED;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
}
