package name.soulayrol.rhaa.sholi.data;

import java.util.List;

import name.soulayrol.rhaa.sholi.data.model.DaoSession;
import name.soulayrol.rhaa.sholi.data.model.Item;
import name.soulayrol.rhaa.sholi.data.model.ItemDao;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentItemAdapter;
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
        List<Item> items = daoSession.getItemDao().queryBuilder()
                .orderAsc(ItemDao.Properties.SyncId)
                .list();
        return SyncDocumentItemAdapter.toDocument(items);
    }

    @Override
    public void applyDocument(SyncDocument document) {
        SyncDocumentItemAdapter.applyBySyncId(document, new SyncDocumentItemAdapter.SyncItemStore<Item>() {
            @Override
            public void runInTransaction(Runnable mutation) {
                daoSession.runInTx(mutation);
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
}
