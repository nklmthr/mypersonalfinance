package com.nklmthr.finance.personal.service.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.DataStoreFactory;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class AppUserDataStoreTest {

    @Mock AppUserRepository repo;
    @Mock DataStoreFactory factory;

    private AppUserDataStore<StoredCredential> store;

    @BeforeEach
    void setUp(){
        store = new AppUserDataStore<>(factory, "StoredCredential", repo);
    }

    @Test
    void get_returnsStoredCredentialWhenPresent() throws IOException {
        AppUser user = AppUser.builder().id("u1").username("jane").gmailAccessToken("at").gmailRefreshToken("rt").gmailTokenExpiry(123L).build();
        when(repo.findByUsername("jane")).thenReturn(Optional.of(user));
        StoredCredential cred = store.get("jane");
        assertThat(cred).isNotNull();
        assertThat(cred.getAccessToken()).isEqualTo("at");
        assertThat(cred.getRefreshToken()).isEqualTo("rt");
        assertThat(cred.getExpirationTimeMilliseconds()).isEqualTo(123L);
    }

    @Test
    void set_updatesUserTokens() throws IOException {
        AppUser user = AppUser.builder().id("u1").username("jane").build();
        when(repo.findByUsername("jane")).thenReturn(Optional.of(user));
        StoredCredential cred = new StoredCredential();
        cred.setAccessToken("at");
        cred.setRefreshToken("rt");
        cred.setExpirationTimeMilliseconds(456L);
        store.set("jane", cred);
        assertThat(user.getGmailAccessToken()).isEqualTo("at");
        assertThat(user.getGmailRefreshToken()).isEqualTo("rt");
        assertThat(user.getGmailTokenExpiry()).isEqualTo(456L);
    }

    @Test
    void set_throwsIfWrongType() {
        assertThatThrownBy(() -> store.set("jane", (StoredCredential)null)).isInstanceOf(IOException.class);
    }

    @Test
    void delete_clearsTokens() throws IOException {
        AppUser user = AppUser.builder().id("u1").username("jane").gmailAccessToken("x").gmailRefreshToken("y").gmailTokenExpiry(1L).build();
        when(repo.findByUsername("jane")).thenReturn(Optional.of(user));
        store.delete("jane");
        assertThat(user.getGmailAccessToken()).isNull();
        assertThat(user.getGmailRefreshToken()).isNull();
        assertThat(user.getGmailTokenExpiry()).isNull();
    }
}


