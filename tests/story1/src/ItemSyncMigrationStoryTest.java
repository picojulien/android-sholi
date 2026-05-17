package story1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import name.soulayrol.rhaa.sholi.sync.items.ItemSyncMetadata;
import name.soulayrol.rhaa.sholi.sync.items.ItemSyncMetadataMigration;

public final class ItemSyncMigrationStoryTest {

    private ItemSyncMigrationStoryTest() {
    }

    public static void run() {
        verifyMigrationBackfillsMetadataAndDeduplicatesLegacyItems();
        verifyMigrationCanBeRerunWithoutChangingMigratedRows();
    }

    private static void verifyMigrationBackfillsMetadataAndDeduplicatesLegacyItems() {
        FakeDatabase database = new FakeDatabase();
        database.add(new ItemSyncMetadataMigration.Row(2L, "Milk", 1));
        database.add(new ItemSyncMetadataMigration.Row(1L, "MILK", 2));
        database.add(new ItemSyncMetadataMigration.Row(3L, "  Bread\tFlour  ", 3));
        database.add(new ItemSyncMetadataMigration.Row(4L, "Bread  Flour", 4));

        ItemSyncMetadataMigration.migrate(database, 123456L, "phone-a");

        assertEquals(1, database.transactionCount, "migration transaction count");
        assertEquals(
                new LinkedHashSet<String>(Arrays.asList(
                        "sync_id", "modified_at", "modified_by_name", "deleted", "deleted_synced_at")),
                database.addedColumns,
                "added sync metadata columns");
        assertEquals(2, database.rows.size(), "deduplicated row count");
        assertEquals(true, database.deletedIds.contains(2L), "duplicate row deleted");
        assertEquals(true, database.deletedIds.contains(4L), "whitespace duplicate row deleted");

        ItemSyncMetadataMigration.Row milk = database.requireRow(1L);
        assertEquals("milk", milk.getName(), "case-only duplicate survivor display name");
        assertEquals(Integer.valueOf(2), milk.getStatus(), "survivor status unchanged");
        assertEquals(
                "0309fd6921255a0f39bcf25e963e066a4f8b7077a70e2fd05cae63883a3180d7",
                milk.getSyncId(),
                "survivor sync id");
        assertEquals(Long.valueOf(123456L), milk.getModifiedAt(), "survivor modified_at");
        assertEquals("phone-a", milk.getModifiedByName(), "survivor modified_by.name");
        assertEquals(Boolean.FALSE, milk.getDeleted(), "survivor deleted flag");

        ItemSyncMetadataMigration.Row bread = database.requireRow(3L);
        assertEquals("  Bread\tFlour  ", bread.getName(), "unique display name retained");
        assertEquals(
                ItemSyncMetadata.initialSyncIdForName("Bread Flour"),
                bread.getSyncId(),
                "unique item sync id uses canonical name");
        assertEquals(Integer.valueOf(3), bread.getStatus(), "unique status unchanged");
    }

    private static void verifyMigrationCanBeRerunWithoutChangingMigratedRows() {
        FakeDatabase database = new FakeDatabase();
        database.add(new ItemSyncMetadataMigration.Row(2L, "Milk", 1));
        database.add(new ItemSyncMetadataMigration.Row(1L, "MILK", 2));
        database.add(new ItemSyncMetadataMigration.Row(3L, "Bread", 3));

        ItemSyncMetadataMigration.migrate(database, 1000L, "phone-a");
        Map<Long, String> firstSnapshot = database.snapshot();

        ItemSyncMetadataMigration.migrate(database, 2000L, "phone-b");
        Map<Long, String> secondSnapshot = database.snapshot();

        assertEquals(firstSnapshot, secondSnapshot, "rerun snapshot");
        assertEquals(2, database.transactionCount, "rerun transaction count");
        assertEquals(1, database.uniqueIndexCreateCount, "unique index created once");
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Unexpected " + label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static final class FakeDatabase implements ItemSyncMetadataMigration.Database {
        private final Set<String> columns = new LinkedHashSet<String>(Arrays.asList("_id", "item", "status"));
        private final Set<String> addedColumns = new LinkedHashSet<String>();
        private final List<ItemSyncMetadataMigration.Row> rows = new ArrayList<ItemSyncMetadataMigration.Row>();
        private final Set<Long> deletedIds = new LinkedHashSet<Long>();
        private int transactionCount;
        private int uniqueIndexCreateCount;

        private void add(ItemSyncMetadataMigration.Row row) {
            rows.add(row);
        }

        private ItemSyncMetadataMigration.Row requireRow(long id) {
            for (ItemSyncMetadataMigration.Row row: rows) {
                if (row.getId().longValue() == id) {
                    return row;
                }
            }
            throw new AssertionError("Missing row " + id);
        }

        private Map<Long, String> snapshot() {
            Map<Long, String> snapshot = new HashMap<Long, String>();
            for (ItemSyncMetadataMigration.Row row: rows) {
                snapshot.put(row.getId(), row.toSnapshotString());
            }
            return snapshot;
        }

        @Override
        public void runInTransaction(Runnable mutation) {
            transactionCount++;
            mutation.run();
        }

        @Override
        public boolean hasColumn(String columnName) {
            return columns.contains(columnName);
        }

        @Override
        public void addColumn(String columnDefinition) {
            String columnName = columnDefinition.split(" ")[0];
            columns.add(columnName);
            addedColumns.add(columnName);
        }

        @Override
        public List<ItemSyncMetadataMigration.Row> loadItems() {
            return new ArrayList<ItemSyncMetadataMigration.Row>(rows);
        }

        @Override
        public void update(ItemSyncMetadataMigration.Row updated) {
            for (int i = 0; i < rows.size(); ++i) {
                if (rows.get(i).getId().equals(updated.getId())) {
                    rows.set(i, updated.copy());
                    return;
                }
            }
            throw new AssertionError("Cannot update missing row " + updated.getId());
        }

        @Override
        public void deleteById(Long id) {
            for (int i = 0; i < rows.size(); ++i) {
                if (rows.get(i).getId().equals(id)) {
                    rows.remove(i);
                    deletedIds.add(id);
                    return;
                }
            }
            throw new AssertionError("Cannot delete missing row " + id);
        }

        @Override
        public void createSyncIdUniqueIndex() {
            if (uniqueIndexCreateCount == 0) {
                uniqueIndexCreateCount++;
            }
        }
    }
}
