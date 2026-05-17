package name.soulayrol.rhaa.sholi.sync.merge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentJson;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentParseException;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;

public final class SyncMetadataPersistence implements SyncMetadataStore {

    private final Storage storage;

    public SyncMetadataPersistence(Storage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("storage must not be null");
        }
        this.storage = storage;
    }

    @Override
    public void runInTransaction(Runnable mutation) {
        if (mutation == null) {
            throw new IllegalArgumentException("mutation must not be null");
        }
        storage.runInTransaction(mutation);
    }

    @Override
    public SyncDocument loadBaselineDocument() {
        String baselineJson = storage.loadBaselineDocumentJson();
        if (baselineJson == null) {
            return null;
        }
        try {
            return SyncDocumentJson.parse(baselineJson);
        } catch (SyncDocumentParseException e) {
            throw new IllegalStateException("Stored sync baseline is invalid", e);
        }
    }

    @Override
    public String loadRemoteVersionMarker() {
        return storage.loadRemoteVersionMarker();
    }

    @Override
    public void saveBaselineDocument(SyncDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        storage.saveBaselineDocumentJson(SyncDocumentJson.serialize(document));
    }

    @Override
    public void saveRemoteVersionMarker(String remoteVersionMarker) {
        storage.saveRemoteVersionMarker(remoteVersionMarker);
    }

    @Override
    public List<SyncConflict> loadConflicts() {
        List<ConflictRecord> records = storage.loadConflictRecords();
        ArrayList<SyncConflict> conflicts = new ArrayList<SyncConflict>(records.size());
        for (ConflictRecord record: records) {
            conflicts.add(decodeConflict(record));
        }
        return conflicts;
    }

    @Override
    public SyncDocument loadPendingMergedDocument() {
        return parseDocument(storage.loadPendingMergedDocumentJson(), "Stored pending merged document is invalid");
    }

    @Override
    public SyncDocument loadPendingLocalDocument() {
        return parseDocument(storage.loadPendingLocalDocumentJson(), "Stored pending local document is invalid");
    }

    @Override
    public void replaceConflicts(List<SyncConflict> conflicts) {
        if (conflicts == null) {
            throw new IllegalArgumentException("conflicts must not be null");
        }
        final ArrayList<ConflictRecord> records = encodeConflicts(conflicts);
        storage.runInTransaction(new Runnable() {
            @Override
            public void run() {
                storage.deleteAllConflictRecords();
                for (ConflictRecord record: records) {
                    storage.saveConflictRecord(record);
                }
            }
        });
    }

    @Override
    public void replacePendingConflictState(
            List<SyncConflict> conflicts,
            SyncDocument pendingMergedDocument,
            SyncDocument pendingLocalDocument) {
        if (conflicts == null) {
            throw new IllegalArgumentException("conflicts must not be null");
        }
        if (pendingMergedDocument == null) {
            throw new IllegalArgumentException("pendingMergedDocument must not be null");
        }
        if (pendingLocalDocument == null) {
            throw new IllegalArgumentException("pendingLocalDocument must not be null");
        }
        final ArrayList<ConflictRecord> records = encodeConflicts(conflicts);
        final String pendingMergedJson = SyncDocumentJson.serialize(pendingMergedDocument);
        final String pendingLocalJson = SyncDocumentJson.serialize(pendingLocalDocument);
        storage.runInTransaction(new Runnable() {
            @Override
            public void run() {
                storage.deleteAllConflictRecords();
                for (ConflictRecord record: records) {
                    storage.saveConflictRecord(record);
                }
                storage.savePendingMergedDocumentJson(pendingMergedJson);
                storage.savePendingLocalDocumentJson(pendingLocalJson);
            }
        });
    }

    @Override
    public void clearConflicts() {
        storage.runInTransaction(new Runnable() {
            @Override
            public void run() {
                storage.deleteAllConflictRecords();
                storage.savePendingMergedDocumentJson(null);
                storage.savePendingLocalDocumentJson(null);
            }
        });
    }

    private static ArrayList<ConflictRecord> encodeConflicts(List<SyncConflict> conflicts) {
        ArrayList<ConflictRecord> records = new ArrayList<ConflictRecord>(conflicts.size());
        for (SyncConflict conflict: conflicts) {
            if (conflict == null) {
                throw new IllegalArgumentException("conflicts must not contain null entries");
            }
            records.add(encodeConflict(conflict));
        }
        return records;
    }

    private static ConflictRecord encodeConflict(SyncConflict conflict) {
        return new ConflictRecord(
                conflict.getSyncId(),
                encodeItem(conflict.getBaselineItem()),
                encodeItem(conflict.getLocalItem()),
                encodeItem(conflict.getRemoteItem()),
                conflict.getRemoteVersionMarker(),
                conflict.getConflictTimestamp(),
                conflict.getStatus(),
                joinFields(conflict.getConflictingFields()));
    }

    private static SyncConflict decodeConflict(ConflictRecord record) {
        return new SyncConflict(
                record.getSyncId(),
                parseItem(record.getBaselineItemJson()),
                parseItem(record.getLocalItemJson()),
                parseItem(record.getRemoteItemJson()),
                record.getRemoteVersionMarker(),
                record.getConflictTimestamp(),
                record.getStatus(),
                splitFields(record.getConflictingFields()));
    }

    private static String encodeItem(SyncItem item) {
        if (item == null) {
            return null;
        }
        return SyncDocumentJson.serialize(new SyncDocument(Collections.singletonList(item)));
    }

    private static SyncItem parseItem(String json) {
        if (json == null) {
            return null;
        }
        try {
            List<SyncItem> items = SyncDocumentJson.parse(json).getItems();
            if (items.size() != 1) {
                throw new IllegalStateException("Stored sync item snapshot is invalid");
            }
            return items.get(0);
        } catch (SyncDocumentParseException e) {
            throw new IllegalStateException("Stored sync item snapshot is invalid", e);
        }
    }

    private static SyncDocument parseDocument(String json, String invalidMessage) {
        if (json == null) {
            return null;
        }
        try {
            return SyncDocumentJson.parse(json);
        } catch (SyncDocumentParseException e) {
            throw new IllegalStateException(invalidMessage, e);
        }
    }

    private static String joinFields(List<String> fields) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fields.size(); ++i) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(fields.get(i));
        }
        return builder.toString();
    }

    private static List<String> splitFields(String fields) {
        ArrayList<String> result = new ArrayList<String>();
        if (fields == null || fields.length() == 0) {
            return result;
        }
        int start = 0;
        for (int i = 0; i <= fields.length(); ++i) {
            if (i == fields.length() || fields.charAt(i) == ',') {
                result.add(fields.substring(start, i));
                start = i + 1;
            }
        }
        return result;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    public interface Storage {

        void runInTransaction(Runnable mutation);

        String loadBaselineDocumentJson();

        String loadRemoteVersionMarker();

        String loadPendingMergedDocumentJson();

        String loadPendingLocalDocumentJson();

        void saveBaselineDocumentJson(String baselineDocumentJson);

        void saveRemoteVersionMarker(String remoteVersionMarker);

        void savePendingMergedDocumentJson(String pendingMergedDocumentJson);

        void savePendingLocalDocumentJson(String pendingLocalDocumentJson);

        List<ConflictRecord> loadConflictRecords();

        void deleteAllConflictRecords();

        void saveConflictRecord(ConflictRecord record);
    }

    public static final class ConflictRecord {

        private final String syncId;
        private final String baselineItemJson;
        private final String localItemJson;
        private final String remoteItemJson;
        private final String remoteVersionMarker;
        private final long conflictTimestamp;
        private final String status;
        private final String conflictingFields;

        public ConflictRecord(
                String syncId,
                String baselineItemJson,
                String localItemJson,
                String remoteItemJson,
                String remoteVersionMarker,
                long conflictTimestamp,
                String status,
                String conflictingFields) {
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
            this.syncId = syncId;
            this.baselineItemJson = baselineItemJson;
            this.localItemJson = localItemJson;
            this.remoteItemJson = remoteItemJson;
            this.remoteVersionMarker = remoteVersionMarker;
            this.conflictTimestamp = conflictTimestamp;
            this.status = status;
            this.conflictingFields = conflictingFields;
        }

        public String getSyncId() {
            return syncId;
        }

        public String getBaselineItemJson() {
            return baselineItemJson;
        }

        public String getLocalItemJson() {
            return localItemJson;
        }

        public String getRemoteItemJson() {
            return remoteItemJson;
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

        public String getConflictingFields() {
            return conflictingFields;
        }
    }
}
