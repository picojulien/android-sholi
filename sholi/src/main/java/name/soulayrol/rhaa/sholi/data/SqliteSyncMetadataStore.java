package name.soulayrol.rhaa.sholi.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import name.soulayrol.rhaa.sholi.data.model.DaoSession;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentJson;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentParseException;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;

public final class SqliteSyncMetadataStore implements SyncMetadataStore {

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

    public SqliteSyncMetadataStore(DaoSession daoSession, SQLiteDatabase database) {
        if (daoSession == null) {
            throw new IllegalArgumentException("daoSession must not be null");
        }
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        this.daoSession = daoSession;
        this.database = database;
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
        String baselineJson = loadBaselineColumn(COLUMN_BASELINE_JSON);
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
        return loadBaselineColumn(COLUMN_REMOTE_VERSION_MARKER);
    }

    @Override
    public void saveBaselineDocument(SyncDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_BASELINE_JSON, SyncDocumentJson.serialize(document));
        updateOrInsertBaseline(values);
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
            ArrayList<SyncConflict> conflicts = new ArrayList<SyncConflict>();
            while (cursor.moveToNext()) {
                conflicts.add(new SyncConflict(
                        cursor.getString(0),
                        parseItem(cursor.isNull(1) ? null : cursor.getString(1)),
                        parseItem(cursor.isNull(2) ? null : cursor.getString(2)),
                        parseItem(cursor.isNull(3) ? null : cursor.getString(3)),
                        cursor.isNull(4) ? null : cursor.getString(4),
                        cursor.getLong(5),
                        cursor.getString(6),
                        splitFields(cursor.getString(7))));
            }
            return conflicts;
        } finally {
            cursor.close();
        }
    }

    @Override
    public void replaceConflicts(final List<SyncConflict> conflicts) {
        if (conflicts == null) {
            throw new IllegalArgumentException("conflicts must not be null");
        }
        daoSession.runInTx(new Runnable() {
            @Override
            public void run() {
                database.delete(CONFLICT_TABLE, null, null);
                for (SyncConflict conflict: conflicts) {
                    ContentValues values = new ContentValues();
                    values.put(COLUMN_SYNC_ID, conflict.getSyncId());
                    putItem(values, COLUMN_BASELINE_JSON, conflict.getBaselineItem());
                    putItem(values, COLUMN_LOCAL_JSON, conflict.getLocalItem());
                    putItem(values, COLUMN_REMOTE_JSON, conflict.getRemoteItem());
                    if (conflict.getRemoteVersionMarker() == null) {
                        values.putNull(COLUMN_REMOTE_VERSION_MARKER);
                    } else {
                        values.put(COLUMN_REMOTE_VERSION_MARKER, conflict.getRemoteVersionMarker());
                    }
                    values.put(COLUMN_CONFLICT_TIMESTAMP, conflict.getConflictTimestamp());
                    values.put(COLUMN_STATUS, conflict.getStatus());
                    values.put(COLUMN_CONFLICTING_FIELDS, joinFields(conflict.getConflictingFields()));
                    database.replace(CONFLICT_TABLE, null, values);
                }
            }
        });
    }

    @Override
    public void clearConflicts() {
        database.delete(CONFLICT_TABLE, null, null);
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

    private static void putItem(ContentValues values, String column, SyncItem item) {
        if (item == null) {
            values.putNull(column);
        } else {
            values.put(column, SyncDocumentJson.serialize(new SyncDocument(Collections.singletonList(item))));
        }
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
}
