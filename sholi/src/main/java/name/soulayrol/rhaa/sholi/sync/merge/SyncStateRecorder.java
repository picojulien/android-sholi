package name.soulayrol.rhaa.sholi.sync.merge;

import java.util.List;

import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;

public final class SyncStateRecorder {

    private SyncStateRecorder() {
    }

    public static void recordSuccessfulUpload(
            SyncMetadataStore store, SyncDocument baselineDocument, String remoteVersionMarker) {
        recordSuccessfulSync(store, baselineDocument, remoteVersionMarker);
    }

    public static void recordSuccessfulPullOnly(
            SyncMetadataStore store, SyncDocument baselineDocument, String remoteVersionMarker) {
        recordSuccessfulSync(store, baselineDocument, remoteVersionMarker);
    }

    public static void persistConflicts(SyncMetadataStore store, List<SyncConflict> conflicts) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        if (conflicts == null) {
            throw new IllegalArgumentException("conflicts must not be null");
        }
        store.replaceConflicts(conflicts);
    }

    public static boolean hasUnresolvedConflicts(SyncMetadataStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        for (SyncConflict conflict: store.loadConflicts()) {
            if (SyncConflict.STATUS_UNRESOLVED.equals(conflict.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private static void recordSuccessfulSync(
            final SyncMetadataStore store,
            final SyncDocument baselineDocument,
            final String remoteVersionMarker) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        if (baselineDocument == null) {
            throw new IllegalArgumentException("baselineDocument must not be null");
        }
        store.runInTransaction(new Runnable() {
            @Override
            public void run() {
                store.saveBaselineDocument(baselineDocument);
                store.saveRemoteVersionMarker(remoteVersionMarker);
                store.clearConflicts();
            }
        });
    }
}
