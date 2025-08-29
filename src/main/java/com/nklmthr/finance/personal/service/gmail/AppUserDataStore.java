package com.nklmthr.finance.personal.service.gmail;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.AbstractDataStore;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AppUserRepository;

public class AppUserDataStore<V extends Serializable> extends AbstractDataStore<V> {

	@Autowired
    private final AppUserRepository appUserRepository;

    protected AppUserDataStore(DataStoreFactory dataStoreFactory, String id, AppUserRepository appUserRepository) {
        super(dataStoreFactory, id);
        this.appUserRepository = appUserRepository;
    }

    @Override
    public int size() throws IOException {
        // not used, but required
        return (int) appUserRepository.count();
    }

    @Override
    public boolean isEmpty() throws IOException {
        return size() == 0;
    }

    @Override
    public boolean containsKey(String key) throws IOException {
        return appUserRepository.findByUsername(key) != null;
    }

    @Override
    public boolean containsValue(V value) throws IOException {
        // optional, rarely used
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(String key) throws IOException {
        AppUser user = appUserRepository.findByUsername(key).get();
        if (user == null || user.getGmailAccessToken() == null) {
            return null;
        }
        StoredCredential cred = new StoredCredential()
                .setAccessToken(user.getGmailAccessToken())
                .setRefreshToken(user.getGmailRefreshToken())
                .setExpirationTimeMilliseconds(user.getGmailTokenExpiry() != null
                        ? user.getGmailTokenExpiry()
                        : null);
        return (V) cred;
    }

    @Override
    public DataStore<V> set(String key, V value) throws IOException {
        if (!(value instanceof StoredCredential)) {
            throw new IOException("Expected StoredCredential");
        }
        AppUser user = appUserRepository.findByUsername(key).get();
        if (user == null) {
            throw new IOException("User not found: " + key);
        }

        StoredCredential cred = (StoredCredential) value;
        user.setGmailAccessToken(cred.getAccessToken());
        user.setGmailRefreshToken(cred.getRefreshToken());
        if (cred.getExpirationTimeMilliseconds() != null) {
        	user.setGmailTokenExpiry(cred.getExpirationTimeMilliseconds());
        }
        appUserRepository.save(user);
        return this;
    }

    @Override
    public DataStore<V> clear() throws IOException {
        throw new UnsupportedOperationException("Clear not supported");
    }

    @Override
    public DataStore<V> delete(String key) throws IOException {
        AppUser user = appUserRepository.findByUsername(key).get();
        if (user != null) {
            user.setGmailAccessToken(null);
            user.setGmailRefreshToken(null);
            user.setGmailTokenExpiry(null);
            appUserRepository.save(user);
        }
        return this;
    }

    @Override
    public Set<String> keySet() throws IOException {
        // rarely used, can be empty
        return Collections.emptySet();
    }

    @Override
    public Collection<V> values() throws IOException {
        return Collections.emptyList();
    }
}
