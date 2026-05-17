package name.soulayrol.rhaa.sholi.sync.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SyncDocument {

    public static final int SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final List<SyncItem> items;

    public SyncDocument(List<SyncItem> items) {
        this(SCHEMA_VERSION, items);
    }

    SyncDocument(int schemaVersion, List<SyncItem> items) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version must be " + SCHEMA_VERSION);
        }
        if (items == null) {
            throw new IllegalArgumentException("items must not be null");
        }
        ArrayList<SyncItem> copy = new ArrayList<SyncItem>(items.size());
        Set<String> syncIds = new HashSet<String>();
        for (SyncItem item: items) {
            if (item == null) {
                throw new IllegalArgumentException("items must not contain null entries");
            }
            if (!syncIds.add(item.getSyncId())) {
                throw new IllegalArgumentException("Duplicate sync_id in sync document");
            }
            copy.add(item);
        }
        this.schemaVersion = schemaVersion;
        this.items = Collections.unmodifiableList(copy);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public List<SyncItem> getItems() {
        return items;
    }
}
