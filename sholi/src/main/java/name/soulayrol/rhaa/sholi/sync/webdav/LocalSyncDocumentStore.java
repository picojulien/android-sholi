package name.soulayrol.rhaa.sholi.sync.webdav;

import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;

public interface LocalSyncDocumentStore {

    SyncDocument loadCurrentDocument();

    void applyDocument(SyncDocument document);
}
