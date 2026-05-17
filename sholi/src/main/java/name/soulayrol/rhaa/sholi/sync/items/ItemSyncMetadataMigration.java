package name.soulayrol.rhaa.sholi.sync.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ItemSyncMetadataMigration {

    public static final String COLUMN_SYNC_ID = "sync_id";
    public static final String COLUMN_MODIFIED_AT = "modified_at";
    public static final String COLUMN_MODIFIED_BY_NAME = "modified_by_name";
    public static final String COLUMN_DELETED = "deleted";
    public static final String COLUMN_DELETED_SYNCED_AT = "deleted_synced_at";

    private static final String[] REQUIRED_COLUMNS = new String[] {
            COLUMN_SYNC_ID + " TEXT",
            COLUMN_MODIFIED_AT + " INTEGER",
            COLUMN_MODIFIED_BY_NAME + " TEXT",
            COLUMN_DELETED + " INTEGER DEFAULT 0",
            COLUMN_DELETED_SYNCED_AT + " INTEGER"
    };

    private ItemSyncMetadataMigration() {
    }

    public static void migrate(final Database database, final long initialModifiedAt,
                               final String modifiedByName) {
        database.runInTransaction(new Runnable() {
            @Override
            public void run() {
                ensureColumns(database);
                migrateRows(database, initialModifiedAt, modifiedByName);
                database.createSyncIdUniqueIndex();
            }
        });
    }

    private static void ensureColumns(Database database) {
        for (String columnDefinition: REQUIRED_COLUMNS) {
            String columnName = columnDefinition.substring(0, columnDefinition.indexOf(' '));
            if (!database.hasColumn(columnName)) {
                database.addColumn(columnDefinition);
            }
        }
    }

    private static void migrateRows(Database database, long initialModifiedAt, String modifiedByName) {
        List<Row> rows = database.loadItems();
        Map<String, List<Row>> legacyRowsByCanonicalName = new LinkedHashMap<String, List<Row>>();

        for (Row row: rows) {
            if (isLegacy(row)) {
                String canonicalName = ItemSyncMetadata.canonicalizeName(row.getName());
                List<Row> canonicalRows = legacyRowsByCanonicalName.get(canonicalName);
                if (canonicalRows == null) {
                    canonicalRows = new ArrayList<Row>();
                    legacyRowsByCanonicalName.put(canonicalName, canonicalRows);
                }
                canonicalRows.add(row.copy());
            }
        }

        for (Map.Entry<String, List<Row>> entry: legacyRowsByCanonicalName.entrySet()) {
            migrateCanonicalRows(database, entry.getKey(), entry.getValue(), initialModifiedAt, modifiedByName);
        }
    }

    private static void migrateCanonicalRows(Database database, String canonicalName, List<Row> rows,
                                             long initialModifiedAt, String modifiedByName) {
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row left, Row right) {
                int idComparison = compareNullableLong(left.getId(), right.getId());
                if (idComparison != 0) {
                    return idComparison;
                }
                String leftName = left.getName() == null ? "" : left.getName();
                String rightName = right.getName() == null ? "" : right.getName();
                return leftName.compareTo(rightName);
            }
        });

        Row survivor = rows.get(0);
        if (rows.size() > 1 && namesDifferOnlyByCasing(rows)) {
            survivor.setName(canonicalName);
        }
        ItemSyncMetadata.ensurePresent(survivor, initialModifiedAt, modifiedByName);

        for (int i = 1; i < rows.size(); ++i) {
            database.deleteById(rows.get(i).getId());
        }
        database.update(survivor);
    }

    private static boolean isLegacy(Row row) {
        return ItemSyncMetadata.isEmpty(row.getSyncId())
                || row.getModifiedAt() == null
                || ItemSyncMetadata.isEmpty(row.getModifiedByName())
                || row.getDeleted() == null;
    }

    private static boolean namesDifferOnlyByCasing(List<Row> rows) {
        String first = null;
        boolean foundCaseDifference = false;
        for (Row row: rows) {
            String normalized = ItemSyncMetadata.collapseWhitespace(row.getName() == null ? "" : row.getName());
            if (first == null) {
                first = normalized;
            } else if (!first.equals(normalized)) {
                if (!first.equalsIgnoreCase(normalized)) {
                    return false;
                }
                foundCaseDifference = true;
            }
        }
        return foundCaseDifference;
    }

    private static int compareNullableLong(Long left, Long right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    public interface Database {
        void runInTransaction(Runnable mutation);

        boolean hasColumn(String columnName);

        void addColumn(String columnDefinition);

        List<Row> loadItems();

        void update(Row row);

        void deleteById(Long id);

        void createSyncIdUniqueIndex();
    }

    public static final class Row implements SyncTrackedItem {
        private Long id;
        private String name;
        private Integer status;
        private String syncId;
        private Long modifiedAt;
        private String modifiedByName;
        private Boolean deleted;
        private Long deletedSyncedAt;

        public Row(Long id, String name, Integer status) {
            this(id, name, status, null, null, null, null, null);
        }

        public Row(Long id, String name, Integer status, String syncId, Long modifiedAt,
                   String modifiedByName, Boolean deleted, Long deletedSyncedAt) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.syncId = syncId;
            this.modifiedAt = modifiedAt;
            this.modifiedByName = modifiedByName;
            this.deleted = deleted;
            this.deletedSyncedAt = deletedSyncedAt;
        }

        public Row copy() {
            return new Row(id, name, status, syncId, modifiedAt, modifiedByName, deleted, deletedSyncedAt);
        }

        public String toSnapshotString() {
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
