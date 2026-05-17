package name.soulayrol.rhaa.sholi.sync.merge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import name.soulayrol.rhaa.sholi.sync.document.SyncItem;

public final class SyncConflict {

    public static final String STATUS_UNRESOLVED = "unresolved";
    public static final String STATUS_RESOLVED_LOCAL = "resolved_local";
    public static final String STATUS_RESOLVED_REMOTE = "resolved_remote";

    private final String syncId;
    private final SyncItem baselineItem;
    private final SyncItem localItem;
    private final SyncItem remoteItem;
    private final String remoteVersionMarker;
    private final long conflictTimestamp;
    private final String status;
    private final List<String> conflictingFields;

    public SyncConflict(
            String syncId,
            SyncItem baselineItem,
            SyncItem localItem,
            SyncItem remoteItem,
            String remoteVersionMarker,
            long conflictTimestamp,
            String status,
            List<String> conflictingFields) {
        if (isEmpty(syncId)) {
            throw new IllegalArgumentException("sync_id must not be empty");
        }
        if (conflictTimestamp < 0L) {
            throw new IllegalArgumentException("conflict timestamp must not be negative");
        }
        if (isEmpty(status)) {
            throw new IllegalArgumentException("status must not be empty");
        }
        if (conflictingFields == null) {
            throw new IllegalArgumentException("conflictingFields must not be null");
        }
        ArrayList<String> fields = new ArrayList<String>(conflictingFields.size());
        for (String field: conflictingFields) {
            if (isEmpty(field)) {
                throw new IllegalArgumentException("conflictingFields must not contain empty entries");
            }
            if (!fields.contains(field)) {
                fields.add(field);
            }
        }
        this.syncId = syncId;
        this.baselineItem = baselineItem;
        this.localItem = localItem;
        this.remoteItem = remoteItem;
        this.remoteVersionMarker = remoteVersionMarker;
        this.conflictTimestamp = conflictTimestamp;
        this.status = status;
        this.conflictingFields = Collections.unmodifiableList(fields);
    }

    public String getSyncId() {
        return syncId;
    }

    public SyncItem getBaselineItem() {
        return baselineItem;
    }

    public SyncItem getLocalItem() {
        return localItem;
    }

    public SyncItem getRemoteItem() {
        return remoteItem;
    }

    public String getRemoteVersionMarker() {
        return remoteVersionMarker;
    }

    public long getConflictTimestamp() {
        return conflictTimestamp;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getConflictingFields() {
        return conflictingFields;
    }

    public SyncConflict withStatus(String status) {
        return new SyncConflict(
                syncId,
                baselineItem,
                localItem,
                remoteItem,
                remoteVersionMarker,
                conflictTimestamp,
                status,
                conflictingFields);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
}
