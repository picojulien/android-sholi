package name.soulayrol.rhaa.sholi.sync.merge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;

public final class SyncMergeResult {

    private final SyncDocument mergedDocument;
    private final List<SyncItem> mergedItemsBeforeConflicts;
    private final List<SyncConflict> conflicts;

    static SyncMergeResult clean(List<SyncItem> mergedItems) {
        return new SyncMergeResult(new SyncDocument(mergedItems), mergedItems, Collections.<SyncConflict>emptyList());
    }

    static SyncMergeResult conflicted(List<SyncItem> mergedItemsBeforeConflicts, List<SyncConflict> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            throw new IllegalArgumentException("conflicts must not be empty");
        }
        return new SyncMergeResult(null, mergedItemsBeforeConflicts, conflicts);
    }

    private SyncMergeResult(
            SyncDocument mergedDocument,
            List<SyncItem> mergedItemsBeforeConflicts,
            List<SyncConflict> conflicts) {
        if (mergedItemsBeforeConflicts == null) {
            throw new IllegalArgumentException("mergedItemsBeforeConflicts must not be null");
        }
        if (conflicts == null) {
            throw new IllegalArgumentException("conflicts must not be null");
        }
        this.mergedDocument = mergedDocument;
        this.mergedItemsBeforeConflicts = Collections.unmodifiableList(
                new ArrayList<SyncItem>(mergedItemsBeforeConflicts));
        this.conflicts = Collections.unmodifiableList(new ArrayList<SyncConflict>(conflicts));
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    public boolean canProduceUploadDocument() {
        return !hasConflicts();
    }

    public SyncDocument getMergedDocument() {
        if (hasConflicts()) {
            throw new IllegalStateException("unresolved sync conflicts block merged document output");
        }
        return mergedDocument;
    }

    public List<SyncConflict> getConflicts() {
        return conflicts;
    }

    public SyncDocument getPendingMergedDocument() {
        return new SyncDocument(mergedItemsBeforeConflicts);
    }

    List<SyncItem> getMergedItemsBeforeConflicts() {
        return mergedItemsBeforeConflicts;
    }
}
