package name.soulayrol.rhaa.sholi.sync;

import android.content.Context;

import name.soulayrol.rhaa.sholi.data.Operations;
import name.soulayrol.rhaa.sholi.sync.android.AndroidEncryptedPreferencesFactory;
import name.soulayrol.rhaa.sholi.sync.credentials.CredentialMetadataStore;
import name.soulayrol.rhaa.sholi.sync.credentials.CredentialStore;
import name.soulayrol.rhaa.sholi.sync.credentials.CredentialStoreFactory;
import name.soulayrol.rhaa.sholi.sync.credentials.EncryptedPreferencesFactory;

public final class ProductionCredentialStores {

    private ProductionCredentialStores() {
    }

    public static CredentialStore webDav(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        CredentialMetadataStore metadataStore =
                Operations.openWebDavCredentialMetadataStore(context);
        EncryptedPreferencesFactory encryptedPreferencesFactory =
                new AndroidEncryptedPreferencesFactory(context);
        return CredentialStoreFactory.forProduction(encryptedPreferencesFactory, metadataStore);
    }
}
