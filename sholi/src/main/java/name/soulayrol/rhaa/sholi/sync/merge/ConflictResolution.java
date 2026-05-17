package name.soulayrol.rhaa.sholi.sync.merge;

import java.util.ArrayList;
import java.util.Map;

import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;

public final class ConflictResolution {

    private ConflictResolution() {
    }

    public static SyncDocument resolveAll(
            SyncMergeResult result, Map<String, ConflictChoice> choicesBySyncId) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        if (!result.hasConflicts()) {
            return result.getMergedDocument();
        }
        if (choicesBySyncId == null) {
            throw new IllegalArgumentException("choicesBySyncId must not be null");
        }
        ArrayList<SyncItem> resolvedItems = new ArrayList<SyncItem>(
                result.getMergedItemsBeforeConflicts());
        for (SyncConflict conflict: result.getConflicts()) {
            ConflictChoice choice = choicesBySyncId.get(conflict.getSyncId());
            if (choice == null) {
                throw new IllegalStateException("unresolved conflict for sync_id " + conflict.getSyncId());
            }
            resolvedItems.add(resolve(conflict, choice));
        }
        return new SyncDocument(resolvedItems);
    }

    public static SyncItem resolve(SyncConflict conflict, ConflictChoice choice) {
        if (conflict == null) {
            throw new IllegalArgumentException("conflict must not be null");
        }
        if (choice == null) {
            throw new IllegalArgumentException("choice must not be null");
        }
        SyncItem chosen = choice == ConflictChoice.LOCAL
                ? conflict.getLocalItem()
                : conflict.getRemoteItem();
        if (chosen == null) {
            throw new IllegalStateException("Cannot resolve conflict because the selected snapshot is missing");
        }
        return chosen;
    }

    public static SyncConflict markResolved(SyncConflict conflict, ConflictChoice choice) {
        if (conflict == null) {
            throw new IllegalArgumentException("conflict must not be null");
        }
        if (choice == null) {
            throw new IllegalArgumentException("choice must not be null");
        }
        if (choice == ConflictChoice.LOCAL && conflict.getLocalItem() == null) {
            throw new IllegalStateException("Cannot resolve conflict because the selected local snapshot is missing");
        }
        if (choice == ConflictChoice.REMOTE && conflict.getRemoteItem() == null) {
            throw new IllegalStateException("Cannot resolve conflict because the selected remote snapshot is missing");
        }
        if (choice == ConflictChoice.LOCAL) {
            return conflict.withStatus(SyncConflict.STATUS_RESOLVED_LOCAL);
        }
        if (choice == ConflictChoice.REMOTE) {
            return conflict.withStatus(SyncConflict.STATUS_RESOLVED_REMOTE);
        }
        throw new IllegalArgumentException("unknown conflict choice");
    }
}
