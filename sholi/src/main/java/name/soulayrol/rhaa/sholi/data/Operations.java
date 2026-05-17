/*
 * ShoLi, a simple tool to produce short (shopping) lists.
 *
 * Copyright (C) 2013,2014,2015  David Soulayrol
 *
 * ShoLi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ShoLi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package name.soulayrol.rhaa.sholi.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import de.greenrobot.dao.query.LazyList;
import name.soulayrol.rhaa.sholi.R;
import name.soulayrol.rhaa.sholi.SettingsActivity;
import name.soulayrol.rhaa.sholi.data.model.Checkable;
import name.soulayrol.rhaa.sholi.data.model.DaoMaster;
import name.soulayrol.rhaa.sholi.data.model.DaoSession;
import name.soulayrol.rhaa.sholi.data.model.Item;
import name.soulayrol.rhaa.sholi.data.model.ItemDao;
import name.soulayrol.rhaa.sholi.sync.credentials.CredentialMetadataStore;
import name.soulayrol.rhaa.sholi.sync.items.ItemSyncMetadata;
import name.soulayrol.rhaa.sholi.sync.items.ItemSyncMetadataMigration;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;


public class Operations {

    private static final String TAG = "db";

    private static final String DATABASE_NAME = "sholi.db";

    private static final String DEFAULT_MODIFIED_BY_NAME = ItemSyncMetadata.DEFAULT_MODIFIED_BY_NAME;

    // TODO: This is arbitrary and should match the maximum database item's name field
    private static final int MAX_SERIALIZED_LENGTH = 256;

    private static SQLiteDatabase _database;

    public static DaoSession openSession(Context context) {
        if (_database == null) {
            DaoMaster.OpenHelper helper = new RegularOpenHelper(context);
            _database = helper.getWritableDatabase();
        }
        DaoMaster daoMaster = new DaoMaster(_database);
        return daoMaster.newSession();
    }

    public static CredentialMetadataStore openWebDavCredentialMetadataStore(Context context) {
        DaoSession daoSession = openSession(context);
        return new SqliteCredentialMetadataStore(daoSession, _database);
    }

    public static SyncMetadataStore openWebDavSyncMetadataStore(Context context) {
        DaoSession daoSession = openSession(context);
        return new SqliteSyncMetadataStore(daoSession, _database);
    }

    public static void serialize(Context context, LazyList<Item> items, StringBuilder builder) {
        Map<Integer, String> map = buildMapping(context);
        for (Item item: items) {
            if (Boolean.TRUE.equals(item.getDeleted())) {
                continue;
            }
            builder.append(map.get(item.getStatus()));
            builder.append(item.getName()).append('\n');
        }
    }

    public static List<Item> deserialize(Context context, String data) {
        Map<Integer, String> map = buildMapping(context);
        List<Item> items = new ArrayList<Item>();
        Scanner scanner = new Scanner(data);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.length() <= MAX_SERIALIZED_LENGTH) {
                for (Map.Entry e: map.entrySet()) {
                    if (line.startsWith((String) e.getValue())) {
                        items.add(newItem(
                                context,
                                line.substring(((String) e.getValue()).length()).trim(),
                                (Integer) e.getKey()));
                        break;
                    }
                }
            }
        }

        return items;
    }

    public static Item newItem(String name, int status) {
        return newItem(name, status, DEFAULT_MODIFIED_BY_NAME);
    }

    public static Item newItem(Context context, String name, int status) {
        return newItem(name, status, modifiedByName(context));
    }

    private static Item newItem(String name, int status, String modifiedByName) {
        Item item = new Item(null, name, status, null, null, null, null, null);
        ItemSyncMetadata.initializeNewItem(item, System.currentTimeMillis(), modifiedByName);
        return item;
    }

    public static void touch(Item item) {
        ItemSyncMetadata.touch(item, System.currentTimeMillis(), DEFAULT_MODIFIED_BY_NAME);
    }

    public static void touch(Context context, Item item) {
        ItemSyncMetadata.touch(item, System.currentTimeMillis(), modifiedByName(context));
    }

    public static void restore(Item item) {
        ItemSyncMetadata.restore(item, System.currentTimeMillis(), DEFAULT_MODIFIED_BY_NAME);
    }

    public static void restore(Context context, Item item) {
        ItemSyncMetadata.restore(item, System.currentTimeMillis(), modifiedByName(context));
    }

    public static void markDeleted(Item item) {
        ItemSyncMetadata.markDeleted(item, System.currentTimeMillis(), DEFAULT_MODIFIED_BY_NAME);
    }

    public static void markDeleted(Context context, Item item) {
        ItemSyncMetadata.markDeleted(item, System.currentTimeMillis(), modifiedByName(context));
    }

    public static String modifiedByName(Context context) {
        if (context == null) {
            return DEFAULT_MODIFIED_BY_NAME;
        }
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return configuredModifiedByName(sharedPref.getString(
                SettingsActivity.KEY_WEBDAV_DISPLAY_NAME,
                DEFAULT_MODIFIED_BY_NAME));
    }

    public static long countVisibleItems(DaoSession session) {
        return session.getItemDao().queryBuilder()
                .where(ItemDao.Properties.Deleted.eq(false))
                .buildCount()
                .count();
    }

    public static class RegularOpenHelper extends DaoMaster.OpenHelper {
        public RegularOpenHelper(Context context) {
            super(context, DATABASE_NAME, null);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            boolean handled = false;
            if (oldVersion <= 1 && newVersion >= 2) {
                Log.i(TAG, "Upgraded schema to version 2. Introducing greenDAO.");
                handled = true;
            }
            if (oldVersion < 3 && newVersion >= 3) {
                Log.i(TAG, "Upgrading schema to version 3. Adding item sync metadata.");
                ItemSyncMetadataMigration.migrate(
                        new SqliteItemSyncMetadataMigrationDatabase(db),
                        System.currentTimeMillis(),
                        DEFAULT_MODIFIED_BY_NAME);
                handled = true;
            }
            if (!handled || newVersion > 3)
                Log.w(TAG, "Unsupported upgrade from version " + oldVersion + " to " + newVersion);
        }
    }

    private static class SqliteItemSyncMetadataMigrationDatabase
            implements ItemSyncMetadataMigration.Database {

        private final SQLiteDatabase _db;

        SqliteItemSyncMetadataMigrationDatabase(SQLiteDatabase db) {
            _db = db;
        }

        @Override
        public void runInTransaction(Runnable mutation) {
            _db.beginTransaction();
            try {
                mutation.run();
                _db.setTransactionSuccessful();
            } finally {
                _db.endTransaction();
            }
        }

        @Override
        public boolean hasColumn(String columnName) {
            Cursor cursor = _db.rawQuery("PRAGMA table_info('items')", null);
            try {
                while (cursor.moveToNext()) {
                    if (columnName.equals(cursor.getString(1))) {
                        return true;
                    }
                }
                return false;
            } finally {
                cursor.close();
            }
        }

        @Override
        public void addColumn(String columnDefinition) {
            _db.execSQL("ALTER TABLE 'items' ADD COLUMN " + columnDefinition);
        }

        @Override
        public List<ItemSyncMetadataMigration.Row> loadItems() {
            List<ItemSyncMetadataMigration.Row> rows = new ArrayList<ItemSyncMetadataMigration.Row>();
            Cursor cursor = _db.rawQuery(
                    "SELECT _id, item, status, sync_id, modified_at, modified_by_name, "
                            + "deleted, deleted_synced_at FROM items",
                    null);
            try {
                while (cursor.moveToNext()) {
                    rows.add(new ItemSyncMetadataMigration.Row(
                            cursor.isNull(0) ? null : cursor.getLong(0),
                            cursor.getString(1),
                            cursor.isNull(2) ? null : cursor.getInt(2),
                            cursor.isNull(3) ? null : cursor.getString(3),
                            cursor.isNull(4) ? null : cursor.getLong(4),
                            cursor.isNull(5) ? null : cursor.getString(5),
                            cursor.isNull(6) ? null : cursor.getShort(6) != 0,
                            cursor.isNull(7) ? null : cursor.getLong(7)));
                }
                return rows;
            } finally {
                cursor.close();
            }
        }

        @Override
        public void update(ItemSyncMetadataMigration.Row row) {
            ContentValues values = new ContentValues();
            values.put("item", row.getName());
            values.put("sync_id", row.getSyncId());
            values.put("modified_at", row.getModifiedAt());
            values.put("modified_by_name", row.getModifiedByName());
            values.put("deleted", Boolean.TRUE.equals(row.getDeleted()) ? 1 : 0);
            if (row.getDeletedSyncedAt() == null)
                values.putNull("deleted_synced_at");
            else
                values.put("deleted_synced_at", row.getDeletedSyncedAt());
            _db.update("items", values, "_id = ?", new String[] { String.valueOf(row.getId()) });
        }

        @Override
        public void deleteById(Long id) {
            _db.delete("items", "_id = ?", new String[] { String.valueOf(id) });
        }

        @Override
        public void createSyncIdUniqueIndex() {
            _db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_items_sync_id ON items(sync_id)");
        }
    }

    private static Map<Integer, String> buildMapping(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        Map<Integer, String> map = new HashMap<Integer, String>();

        map.put(Checkable.CHECKED, sharedPref.getString(
                SettingsActivity.KEY_IMPORT_SYMBOL_CHECKED,
                context.getResources().getString(R.string.setting_import_default_value)));
        map.put(Checkable.UNCHECKED, sharedPref.getString(
                SettingsActivity.KEY_IMPORT_SYMBOL_UNCHECKED,
                context.getResources().getString(R.string.setting_import_default_value)));
        map.put(Checkable.OFF_LIST, sharedPref.getString(
                SettingsActivity.KEY_IMPORT_SYMBOL_OFF_LIST,
                context.getResources().getString(R.string.setting_import_default_value)));

        return map;
    }

    private static String configuredModifiedByName(String value) {
        if (value == null || value.trim().length() == 0) {
            return DEFAULT_MODIFIED_BY_NAME;
        }
        return value.trim();
    }
}
