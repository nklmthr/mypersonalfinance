package com.nklmthr.finance.personal.scheduler.gmail;

import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AppUserRepository;

public class AppUserDataStoreFactory implements DataStoreFactory {
    private final AppUser appUser;
    private final AppUserRepository appUserRepository;

    public AppUserDataStoreFactory(AppUser appUser, AppUserRepository appUserRepository) {
        this.appUser = appUser;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public <V extends java.io.Serializable> DataStore<V> getDataStore(String id) {
        return new AppUserDataStore<>(appUser, id, appUserRepository);
    }
}
