package name.soulayrol.rhaa.sholi.sync.webdav;

import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;

public interface LocalSyncDocumentStore {

    SyncDocument loadCurrentDocument();

    boolean isCurrentDocument(SyncDocument expectedDocument);

    void applyDocument(SyncDocument document);

    boolean applyDocumentIfCurrent(SyncDocument expectedDocument, SyncDocument document);

    boolean markDeletedSyncedAndCleanupIfCurrent(SyncDocument expectedDocument, long now);
}
