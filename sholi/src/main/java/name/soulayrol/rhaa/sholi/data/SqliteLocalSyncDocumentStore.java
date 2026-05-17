package name.soulayrol.rhaa.sholi.data;

import java.util.List;

import name.soulayrol.rhaa.sholi.data.model.DaoSession;
import name.soulayrol.rhaa.sholi.data.model.Item;
import name.soulayrol.rhaa.sholi.data.model.ItemDao;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentItemAdapter;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentJson;
import name.soulayrol.rhaa.sholi.sync.webdav.LocalSyncDocumentStore;

public final class SqliteLocalSyncDocumentStore implements LocalSyncDocumentStore {

    private final DaoSession daoSession;

    public SqliteLocalSyncDocumentStore(DaoSession daoSession) {
        if (daoSession == null) {
            throw new IllegalArgumentException("daoSession must not be null");
        }
        this.daoSession = daoSession;
    }

    @Override
    public SyncDocument loadCurrentDocument() {
        return loadCurrentDocumentInOrder();
    }

    @Override
    public boolean isCurrentDocument(final SyncDocument expectedDocument) {
        if (expectedDocument == null) {
            throw new IllegalArgumentException("expectedDocument must not be null");
        }
        final boolean[] current = new boolean[] { false };
        daoSession.runInTx(new Runnable() {
            @Override
            public void run() {
                current[0] = sameDocument(loadCurrentDocumentInOrder(), expectedDocument);
            }
        });
        return current[0];
    }

    @Override
    public void applyDocument(SyncDocument document) {
        applyDocumentWithStoreTransaction(document, true);
    }

    @Override
    public boolean applyDocumentIfCurrent(final SyncDocument expectedDocument, final SyncDocument document) {
        if (expectedDocument == null) {
            throw new IllegalArgumentException("expectedDocument must not be null");
        }
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        final boolean[] applied = new boolean[] { false };
        daoSession.runInTx(new Runnable() {
            @Override
            public void run() {
                if (!sameDocument(loadCurrentDocumentInOrder(), expectedDocument)) {
                    return;
                }
                applyDocumentWithStoreTransaction(document, false);
                applied[0] = true;
            }
        });
        return applied[0];
    }

    private SyncDocument loadCurrentDocumentInOrder() {
        List<Item> items = daoSession.getItemDao().queryBuilder()
                .orderAsc(ItemDao.Properties.SyncId)
                .list();
        return SyncDocumentItemAdapter.toDocument(items);
    }

    private void applyDocumentWithStoreTransaction(SyncDocument document, final boolean openTransaction) {
        SyncDocumentItemAdapter.applyBySyncId(document, new SyncDocumentItemAdapter.SyncItemStore<Item>() {
            @Override
            public void runInTransaction(Runnable mutation) {
                if (openTransaction) {
                    daoSession.runInTx(mutation);
                } else {
                    mutation.run();
                }
            }

            @Override
            public List<Item> loadAll() {
                return daoSession.getItemDao().loadAll();
            }

            @Override
            public Item createItem() {
                return new Item();
            }

            @Override
            public void insert(Item item) {
                daoSession.getItemDao().insert(item);
            }

            @Override
            public void update(Item item) {
                daoSession.getItemDao().update(item);
            }
        });
    }

    private static boolean sameDocument(SyncDocument left, SyncDocument right) {
        return SyncDocumentJson.serialize(left).equals(SyncDocumentJson.serialize(right));
    }
}
