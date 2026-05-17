package name.soulayrol.rhaa.sholi.sync.merge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;

public final class SyncMerger {

    private SyncMerger() {
    }

    public static SyncMergeResult merge(
            SyncDocument baseline,
            SyncDocument local,
            SyncDocument remote,
            String remoteVersionMarker,
            long conflictTimestamp) {
        if (local == null) {
            throw new IllegalArgumentException("local document must not be null");
        }
        if (remote == null) {
            throw new IllegalArgumentException("remote document must not be null");
        }
        if (conflictTimestamp < 0L) {
            throw new IllegalArgumentException("conflict timestamp must not be negative");
        }

        Map<String, SyncItem> baselineById = mapBySyncId(baseline);
        Map<String, SyncItem> localById = mapBySyncId(local);
        Map<String, SyncItem> remoteById = mapBySyncId(remote);
        TreeSet<String> syncIds = new TreeSet<String>();
        syncIds.addAll(baselineById.keySet());
        syncIds.addAll(localById.keySet());
        syncIds.addAll(remoteById.keySet());

        List<SyncItem> mergedItems = new ArrayList<SyncItem>();
        List<SyncConflict> conflicts = new ArrayList<SyncConflict>();
        for (String syncId: syncIds) {
            ItemMerge merge = mergeItem(
                    syncId,
                    baselineById.get(syncId),
                    localById.get(syncId),
                    remoteById.get(syncId),
                    remoteVersionMarker,
                    conflictTimestamp);
            if (merge.conflict == null) {
                if (merge.item != null) {
                    mergedItems.add(merge.item);
                }
            } else {
                conflicts.add(merge.conflict);
            }
        }

        if (conflicts.isEmpty()) {
            return SyncMergeResult.clean(mergedItems);
        }
        return SyncMergeResult.conflicted(mergedItems, conflicts);
    }

    private static ItemMerge mergeItem(
            String syncId,
            SyncItem baseline,
            SyncItem local,
            SyncItem remote,
            String remoteVersionMarker,
            long conflictTimestamp) {
        if (baseline == null) {
            return mergeWithoutBaseline(syncId, local, remote, remoteVersionMarker, conflictTimestamp);
        }

        boolean localChanged = !sameSemanticItem(baseline, local);
        boolean remoteChanged = !sameSemanticItem(baseline, remote);
        if (!localChanged && !remoteChanged) {
            return ItemMerge.item(preferPresent(local, remote, baseline));
        }
        if (localChanged && !remoteChanged) {
            return ItemMerge.item(local);
        }
        if (!localChanged) {
            return ItemMerge.item(remote);
        }

        if (local == null && remote == null) {
            return ItemMerge.item(null);
        }
        if (local == null || remote == null) {
            return conflict(syncId, baseline, local, remote, remoteVersionMarker, conflictTimestamp);
        }
        if (hasSemanticConflict(baseline, local, remote, localChanged, remoteChanged)) {
            return conflict(syncId, baseline, local, remote, remoteVersionMarker, conflictTimestamp);
        }
        return ItemMerge.item(mergeIndependentFieldChanges(baseline, local, remote));
    }

    private static ItemMerge mergeWithoutBaseline(
            String syncId,
            SyncItem local,
            SyncItem remote,
            String remoteVersionMarker,
            long conflictTimestamp) {
        if (local == null) {
            return ItemMerge.item(remote);
        }
        if (remote == null) {
            return ItemMerge.item(local);
        }
        if (sameSemanticItem(local, remote)) {
            return ItemMerge.item(local);
        }
        return conflict(syncId, null, local, remote, remoteVersionMarker, conflictTimestamp);
    }

    private static boolean hasSemanticConflict(
            SyncItem baseline,
            SyncItem local,
            SyncItem remote,
            boolean localChanged,
            boolean remoteChanged) {
        if (fieldChangedDifferently(baseline.getName(), local.getName(), remote.getName())) {
            return true;
        }
        if (fieldChangedDifferently(
                Integer.valueOf(baseline.getStatus()),
                Integer.valueOf(local.getStatus()),
                Integer.valueOf(remote.getStatus()))) {
            return true;
        }
        if (fieldChangedDifferently(
                Boolean.valueOf(baseline.isDeleted()),
                Boolean.valueOf(local.isDeleted()),
                Boolean.valueOf(remote.isDeleted()))) {
            return true;
        }
        return localChanged && remoteChanged && local.isDeleted() != remote.isDeleted();
    }

    private static SyncItem mergeIndependentFieldChanges(SyncItem baseline, SyncItem local, SyncItem remote) {
        String name = !equals(baseline.getName(), local.getName())
                ? local.getName()
                : (!equals(baseline.getName(), remote.getName()) ? remote.getName() : baseline.getName());
        int status = baseline.getStatus() != local.getStatus()
                ? local.getStatus()
                : (baseline.getStatus() != remote.getStatus() ? remote.getStatus() : baseline.getStatus());
        boolean deleted = baseline.isDeleted() != local.isDeleted()
                ? local.isDeleted()
                : (baseline.isDeleted() != remote.isDeleted() ? remote.isDeleted() : baseline.isDeleted());

        if (sameSemanticValues(local, name, status, deleted)) {
            return local;
        }
        if (sameSemanticValues(remote, name, status, deleted)) {
            return remote;
        }

        SyncItem evidenceSource = !sameSemanticItem(baseline, local) ? local : remote;
        return new SyncItem(
                baseline.getSyncId(),
                name,
                status,
                deleted,
                evidenceSource.getModifiedAt(),
                evidenceSource.getModifiedBy());
    }

    private static ItemMerge conflict(
            String syncId,
            SyncItem baseline,
            SyncItem local,
            SyncItem remote,
            String remoteVersionMarker,
            long conflictTimestamp) {
        return ItemMerge.conflict(new SyncConflict(
                syncId,
                baseline,
                local,
                remote,
                remoteVersionMarker,
                conflictTimestamp,
                SyncConflict.STATUS_UNRESOLVED,
                differingSemanticFields(local, remote)));
    }

    private static List<String> differingSemanticFields(SyncItem local, SyncItem remote) {
        ArrayList<String> fields = new ArrayList<String>();
        if (local == null || remote == null) {
            fields.add("presence");
            return fields;
        }
        if (!equals(local.getName(), remote.getName())) {
            fields.add("name");
        }
        if (local.getStatus() != remote.getStatus()) {
            fields.add("status");
        }
        if (local.isDeleted() != remote.isDeleted()) {
            fields.add("deleted");
        }
        return fields;
    }

    private static boolean fieldChangedDifferently(Object baseline, Object local, Object remote) {
        return !equals(baseline, local) && !equals(baseline, remote) && !equals(local, remote);
    }

    private static boolean sameSemanticItem(SyncItem left, SyncItem right) {
        if (left == null || right == null) {
            return left == right;
        }
        return sameSemanticItemIgnoringSyncId(left, right) && equals(left.getSyncId(), right.getSyncId());
    }

    private static boolean sameSemanticItemIgnoringSyncId(SyncItem left, SyncItem right) {
        return equals(left.getName(), right.getName())
                && left.getStatus() == right.getStatus()
                && left.isDeleted() == right.isDeleted();
    }

    private static boolean sameSemanticValues(SyncItem item, String name, int status, boolean deleted) {
        return equals(item.getName(), name)
                && item.getStatus() == status
                && item.isDeleted() == deleted;
    }

    private static SyncItem preferPresent(SyncItem first, SyncItem second, SyncItem fallback) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return fallback;
    }

    private static Map<String, SyncItem> mapBySyncId(SyncDocument document) {
        HashMap<String, SyncItem> bySyncId = new HashMap<String, SyncItem>();
        if (document == null) {
            return bySyncId;
        }
        for (SyncItem item: document.getItems()) {
            bySyncId.put(item.getSyncId(), item);
        }
        return bySyncId;
    }

    private static boolean equals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static final class ItemMerge {
        private final SyncItem item;
        private final SyncConflict conflict;

        private ItemMerge(SyncItem item, SyncConflict conflict) {
            this.item = item;
            this.conflict = conflict;
        }

        static ItemMerge item(SyncItem item) {
            return new ItemMerge(item, null);
        }

        static ItemMerge conflict(SyncConflict conflict) {
            return new ItemMerge(null, conflict);
        }
    }
}
