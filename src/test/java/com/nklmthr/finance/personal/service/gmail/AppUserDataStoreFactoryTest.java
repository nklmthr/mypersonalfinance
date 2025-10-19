package com.nklmthr.finance.personal.service.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nklmthr.finance.personal.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class AppUserDataStoreFactoryTest {

    @Mock AppUserRepository repo;

    @Test
    void getDataStore_returnsStoredCredentialStore() throws Exception {
        AppUserDataStoreFactory f = new AppUserDataStoreFactory(repo);
        var store = f.getDataStore("StoredCredential");
        assertThat(store).isInstanceOf(AppUserDataStore.class);
    }

    @Test
    void getDataStore_throwsOnUnsupportedId() {
        AppUserDataStoreFactory f = new AppUserDataStoreFactory(repo);
        assertThatThrownBy(() -> f.getDataStore("Other")).isInstanceOf(java.io.IOException.class);
    }
}
