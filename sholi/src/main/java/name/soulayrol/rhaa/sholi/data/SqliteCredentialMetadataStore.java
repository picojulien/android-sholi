package name.soulayrol.rhaa.sholi.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import name.soulayrol.rhaa.sholi.data.model.DaoSession;
import name.soulayrol.rhaa.sholi.sync.credentials.CredentialMetadataStore;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentialMetadata;

public final class SqliteCredentialMetadataStore implements CredentialMetadataStore {

    private static final String TABLE = "webdav_credentials";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_USERNAME = "username";
    private static final String COLUMN_SECRET_KEY = "secret_key";
    private static final String COLUMN_STORAGE_ENGINE = "storage_engine";
    private static final String DEFAULT_ID = "default";

    private final DaoSession daoSession;
    private final SQLiteDatabase database;

    public SqliteCredentialMetadataStore(DaoSession daoSession, SQLiteDatabase database) {
        if (daoSession == null) {
            throw new IllegalArgumentException("daoSession must not be null");
        }
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        this.daoSession = daoSession;
        this.database = database;
        createTable(database);
    }

    public static void createTable(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS '" + TABLE + "' ("
                + "'" + COLUMN_ID + "' TEXT PRIMARY KEY,"
                + "'" + COLUMN_USERNAME + "' TEXT NOT NULL,"
                + "'" + COLUMN_SECRET_KEY + "' TEXT NOT NULL,"
                + "'" + COLUMN_STORAGE_ENGINE + "' TEXT NOT NULL);");
    }

    @Override
    public void runInTransaction(final Runnable mutation) {
        if (mutation == null) {
            throw new IllegalArgumentException("mutation must not be null");
        }
        daoSession.runInTx(mutation);
    }

    @Override
    public void save(WebDavCredentialMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must not be null");
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, DEFAULT_ID);
        values.put(COLUMN_USERNAME, metadata.getUsername());
        values.put(COLUMN_SECRET_KEY, metadata.getSecretPreferenceKey());
        values.put(COLUMN_STORAGE_ENGINE, metadata.getStorageEngineClassName());
        database.replace(TABLE, null, values);
    }

    @Override
    public WebDavCredentialMetadata load() {
        Cursor cursor = database.query(
                TABLE,
                new String[] { COLUMN_USERNAME, COLUMN_SECRET_KEY, COLUMN_STORAGE_ENGINE },
                COLUMN_ID + "=?",
                new String[] { DEFAULT_ID },
                null,
                null,
                null);
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new WebDavCredentialMetadata(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2));
        } finally {
            cursor.close();
        }
    }

    @Override
    public void clear() {
        database.delete(TABLE, COLUMN_ID + "=?", new String[] { DEFAULT_ID });
    }
}
