package name.soulayrol.rhaa.sholi.sync.orchestration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import name.soulayrol.rhaa.sholi.sync.document.SyncItem;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;

public final class SyncConflictDisplayModel {

    private final String syncId;
    private final List<String> changedFields;
    private final Side local;
    private final Side remote;
    private final long conflictTimestamp;
    private final String status;

    private SyncConflictDisplayModel(
            String syncId,
            List<String> changedFields,
            Side local,
            Side remote,
            long conflictTimestamp,
            String status) {
        this.syncId = syncId;
        this.changedFields = Collections.unmodifiableList(new ArrayList<String>(changedFields));
        this.local = local;
        this.remote = remote;
        this.conflictTimestamp = conflictTimestamp;
        this.status = status;
    }

    public static SyncConflictDisplayModel from(SyncConflict conflict) {
        if (conflict == null) {
            throw new IllegalArgumentException("conflict must not be null");
        }
        return new SyncConflictDisplayModel(
                conflict.getSyncId(),
                conflict.getConflictingFields(),
                Side.from(conflict.getLocalItem()),
                Side.from(conflict.getRemoteItem()),
                conflict.getConflictTimestamp(),
                conflict.getStatus());
    }

    public String getSyncId() {
        return syncId;
    }

    public List<String> getChangedFields() {
        return changedFields;
    }

    public Side getLocal() {
        return local;
    }

    public Side getRemote() {
        return remote;
    }

    public long getConflictTimestamp() {
        return conflictTimestamp;
    }

    public String getStatus() {
        return status;
    }

    public boolean canChooseLocal() {
        return local.isPresent();
    }

    public boolean canChooseRemote() {
        return remote.isPresent();
    }

    public String toDisplayText() {
        StringBuilder builder = new StringBuilder();
        builder.append("Sync ID: ").append(syncId).append('\n');
        builder.append("Changed fields: ").append(changedFields).append('\n');
        builder.append("Conflict timestamp: ").append(conflictTimestamp).append('\n');
        builder.append("Resolution status: ").append(status).append('\n');
        builder.append("Local: ").append(local.toDisplayText()).append('\n');
        builder.append("Remote: ").append(remote.toDisplayText());
        return builder.toString();
    }

    public static final class Side {

        private final boolean present;
        private final String name;
        private final Integer status;
        private final Boolean deleted;
        private final Long modifiedAt;
        private final String modifiedByName;

        private Side(
                boolean present,
                String name,
                Integer status,
                Boolean deleted,
                Long modifiedAt,
                String modifiedByName) {
            this.present = present;
            this.name = name;
            this.status = status;
            this.deleted = deleted;
            this.modifiedAt = modifiedAt;
            this.modifiedByName = modifiedByName;
        }

        static Side from(SyncItem item) {
            if (item == null) {
                return new Side(false, null, null, null, null, null);
            }
            return new Side(
                    true,
                    item.getName(),
                    Integer.valueOf(item.getStatus()),
                    Boolean.valueOf(item.isDeleted()),
                    Long.valueOf(item.getModifiedAt()),
                    item.getModifiedBy().getName());
        }

        public boolean isPresent() {
            return present;
        }

        public String getName() {
            return name;
        }

        public Integer getStatus() {
            return status;
        }

        public Boolean getDeleted() {
            return deleted;
        }

        public Long getModifiedAt() {
            return modifiedAt;
        }

        public String getModifiedByName() {
            return modifiedByName;
        }

        String toDisplayText() {
            if (!present) {
                return "missing";
            }
            return "name=" + name
                    + ", status=" + status
                    + ", deleted=" + deleted
                    + ", modified_at=" + modifiedAt
                    + ", modified_by.name=" + modifiedByName;
        }
    }
}
