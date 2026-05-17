package name.soulayrol.rhaa.sholi.sync.merge;

import java.util.List;

import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;

public interface SyncMetadataStore {

    void runInTransaction(Runnable mutation);

    SyncDocument loadBaselineDocument();

    String loadRemoteVersionMarker();

    void saveBaselineDocument(SyncDocument document);

    void saveRemoteVersionMarker(String remoteVersionMarker);

    List<SyncConflict> loadConflicts();

    SyncDocument loadPendingMergedDocument();

    SyncDocument loadPendingLocalDocument();

    void replaceConflicts(List<SyncConflict> conflicts);

    void replacePendingConflictState(
            List<SyncConflict> conflicts,
            SyncDocument pendingMergedDocument,
            SyncDocument pendingLocalDocument);

    void clearConflicts();
}
