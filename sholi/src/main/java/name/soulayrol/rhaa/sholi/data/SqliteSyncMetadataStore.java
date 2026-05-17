package name.soulayrol.rhaa.sholi.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import name.soulayrol.rhaa.sholi.data.model.DaoSession;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataPersistence;

public final class SqliteSyncMetadataStore implements SyncMetadataStore, SyncMetadataPersistence.Storage {

    private static final String BASELINE_TABLE = "sync_baseline";
    private static final String CONFLICT_TABLE = "sync_conflicts";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_BASELINE_JSON = "baseline_json";
    private static final String COLUMN_REMOTE_VERSION_MARKER = "remote_version_marker";
    private static final String COLUMN_SYNC_ID = "sync_id";
    private static final String COLUMN_LOCAL_JSON = "local_json";
    private static final String COLUMN_REMOTE_JSON = "remote_json";
    private static final String COLUMN_CONFLICT_TIMESTAMP = "conflict_timestamp";
    private static final String COLUMN_STATUS = "status";
    private static final String COLUMN_CONFLICTING_FIELDS = "conflicting_fields";
    private static final String DEFAULT_ID = "default";

    private final DaoSession daoSession;
    private final SQLiteDatabase database;
    private final SyncMetadataPersistence persistence;

    public SqliteSyncMetadataStore(DaoSession daoSession, SQLiteDatabase database) {
        if (daoSession == null) {
            throw new IllegalArgumentException("daoSession must not be null");
        }
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        this.daoSession = daoSession;
        this.database = database;
        this.persistence = new SyncMetadataPersistence(this);
        createTables(database);
    }

    public static void createTables(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS '" + BASELINE_TABLE + "' ("
                + "'" + COLUMN_ID + "' TEXT PRIMARY KEY,"
                + "'" + COLUMN_BASELINE_JSON + "' TEXT,"
                + "'" + COLUMN_REMOTE_VERSION_MARKER + "' TEXT);");
        database.execSQL("CREATE TABLE IF NOT EXISTS '" + CONFLICT_TABLE + "' ("
                + "'" + COLUMN_SYNC_ID + "' TEXT PRIMARY KEY,"
                + "'" + COLUMN_BASELINE_JSON + "' TEXT,"
                + "'" + COLUMN_LOCAL_JSON + "' TEXT,"
                + "'" + COLUMN_REMOTE_JSON + "' TEXT,"
                + "'" + COLUMN_REMOTE_VERSION_MARKER + "' TEXT,"
                + "'" + COLUMN_CONFLICT_TIMESTAMP + "' INTEGER NOT NULL,"
                + "'" + COLUMN_STATUS + "' TEXT NOT NULL,"
                + "'" + COLUMN_CONFLICTING_FIELDS + "' TEXT NOT NULL);");
    }

    @Override
    public void runInTransaction(final Runnable mutation) {
        if (mutation == null) {
            throw new IllegalArgumentException("mutation must not be null");
        }
        daoSession.runInTx(mutation);
    }

    @Override
    public SyncDocument loadBaselineDocument() {
        return persistence.loadBaselineDocument();
    }

    @Override
    public String loadRemoteVersionMarker() {
        return loadBaselineColumn(COLUMN_REMOTE_VERSION_MARKER);
    }

    @Override
    public void saveBaselineDocument(SyncDocument document) {
        persistence.saveBaselineDocument(document);
    }

    @Override
    public void saveRemoteVersionMarker(String remoteVersionMarker) {
        ContentValues values = new ContentValues();
        if (remoteVersionMarker == null) {
            values.putNull(COLUMN_REMOTE_VERSION_MARKER);
        } else {
            values.put(COLUMN_REMOTE_VERSION_MARKER, remoteVersionMarker);
        }
        updateOrInsertBaseline(values);
    }

    @Override
    public List<SyncConflict> loadConflicts() {
        return persistence.loadConflicts();
    }

    @Override
    public void replaceConflicts(final List<SyncConflict> conflicts) {
        persistence.replaceConflicts(conflicts);
    }

    @Override
    public void clearConflicts() {
        persistence.clearConflicts();
    }

    @Override
    public String loadBaselineDocumentJson() {
        return loadBaselineColumn(COLUMN_BASELINE_JSON);
    }

    @Override
    public void saveBaselineDocumentJson(String baselineDocumentJson) {
        ContentValues values = new ContentValues();
        if (baselineDocumentJson == null) {
            values.putNull(COLUMN_BASELINE_JSON);
        } else {
            values.put(COLUMN_BASELINE_JSON, baselineDocumentJson);
        }
        updateOrInsertBaseline(values);
    }

    @Override
    public List<SyncMetadataPersistence.ConflictRecord> loadConflictRecords() {
        Cursor cursor = database.query(
                CONFLICT_TABLE,
                new String[] {
                        COLUMN_SYNC_ID,
                        COLUMN_BASELINE_JSON,
                        COLUMN_LOCAL_JSON,
                        COLUMN_REMOTE_JSON,
                        COLUMN_REMOTE_VERSION_MARKER,
                        COLUMN_CONFLICT_TIMESTAMP,
                        COLUMN_STATUS,
                        COLUMN_CONFLICTING_FIELDS },
                null,
                null,
                null,
                null,
                COLUMN_SYNC_ID);
        try {
            ArrayList<SyncMetadataPersistence.ConflictRecord> conflicts =
                    new ArrayList<SyncMetadataPersistence.ConflictRecord>();
            while (cursor.moveToNext()) {
                conflicts.add(new SyncMetadataPersistence.ConflictRecord(
                        cursor.getString(0),
                        cursor.isNull(1) ? null : cursor.getString(1),
                        cursor.isNull(2) ? null : cursor.getString(2),
                        cursor.isNull(3) ? null : cursor.getString(3),
                        cursor.isNull(4) ? null : cursor.getString(4),
                        cursor.getLong(5),
                        cursor.getString(6),
                        cursor.getString(7)));
            }
            return conflicts;
        } finally {
            cursor.close();
        }
    }

    @Override
    public void deleteAllConflictRecords() {
        database.delete(CONFLICT_TABLE, null, null);
    }

    @Override
    public void saveConflictRecord(SyncMetadataPersistence.ConflictRecord record) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_SYNC_ID, record.getSyncId());
        putNullable(values, COLUMN_BASELINE_JSON, record.getBaselineItemJson());
        putNullable(values, COLUMN_LOCAL_JSON, record.getLocalItemJson());
        putNullable(values, COLUMN_REMOTE_JSON, record.getRemoteItemJson());
        putNullable(values, COLUMN_REMOTE_VERSION_MARKER, record.getRemoteVersionMarker());
        values.put(COLUMN_CONFLICT_TIMESTAMP, record.getConflictTimestamp());
        values.put(COLUMN_STATUS, record.getStatus());
        values.put(COLUMN_CONFLICTING_FIELDS, record.getConflictingFields());
        database.replace(CONFLICT_TABLE, null, values);
    }

    private String loadBaselineColumn(String column) {
        Cursor cursor = database.query(
                BASELINE_TABLE,
                new String[] { column },
                COLUMN_ID + "=?",
                new String[] { DEFAULT_ID },
                null,
                null,
                null);
        try {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return null;
            }
            return cursor.getString(0);
        } finally {
            cursor.close();
        }
    }

    private void updateOrInsertBaseline(ContentValues values) {
        int updated = database.update(
                BASELINE_TABLE,
                values,
                COLUMN_ID + "=?",
                new String[] { DEFAULT_ID });
        if (updated == 0) {
            values.put(COLUMN_ID, DEFAULT_ID);
            database.insert(BASELINE_TABLE, null, values);
        }
    }

    private static void putNullable(ContentValues values, String column, String value) {
        if (value == null) {
            values.putNull(column);
        } else {
            values.put(column, value);
        }
    }
}
