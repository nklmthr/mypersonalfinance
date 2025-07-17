package com.nklmthr.finance.personal.service.gmail;

import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AppUserRepository;

import java.io.IOException;

public class AppUserDataStoreFactory implements DataStoreFactory {

    private final AppUser appUser;
    private final AppUserRepository appUserRepository;

    public AppUserDataStoreFactory(AppUser appUser, AppUserRepository appUserRepository) {
        this.appUser = appUser;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public <V extends java.io.Serializable> DataStore<V> getDataStore(String id) throws IOException {
        if (!"StoredCredential".equals(id)) {
            throw new IOException("Unsupported data store ID: " + id);
        }
        return new AppUserDataStore<>(this, appUser, appUserRepository, id);
    }
}
