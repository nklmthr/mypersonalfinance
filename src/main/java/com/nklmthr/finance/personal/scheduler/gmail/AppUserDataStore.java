package com.nklmthr.finance.personal.scheduler.gmail;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AppUserRepository;

public class AppUserDataStore<V extends Serializable> implements DataStore<V> {

	private static final Logger logger = LoggerFactory.getLogger(AppUserDataStore.class);
	private final AppUser appUser;
	private final String id;
	private final Map<String, V> cache = new HashMap<>();
	private final AppUserRepository appUserRepository;

	public AppUserDataStore(AppUser appUser, String id, AppUserRepository appUserRepository) {
		logger.info("Creating AppUserDataStore for user: {}, id: {}", appUser.getUsername(), id);
		this.appUser = appUser;
		this.id = id;
		this.appUserRepository = appUserRepository;
	}

	@Override
	public DataStoreFactory getDataStoreFactory() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public int size() throws IOException {
		return cache.size();
	}

	@Override
	public boolean isEmpty() throws IOException {
		return cache.isEmpty();
	}

	@Override
	public boolean containsKey(String key) throws IOException {
		return get(key) != null;
	}

	@Override
	public boolean containsValue(V value) throws IOException {
		return cache.containsValue(value);
	}

	@Override
	public Set<String> keySet() throws IOException {
		return cache.keySet();
	}

	@Override
	public Collection<V> values() throws IOException {
		return cache.values();
	}

	@Override
	public V get(String key) throws IOException {
	    logger.info("Retrieving key: {} for user: {}", key, appUser.getUsername());

	    if (cache.containsKey(key)) {
	        logger.info("Cache hit for key: {}", key);
	        return cache.get(key);
	    }

	    // Accept both "user" and username as keys
	    if ("user".equals(key) || appUser.getUsername().equals(key)) {
	        if (appUser.getGmailAccessToken() == null || appUser.getGmailRefreshToken() == null) {
	            logger.warn("No Gmail credentials found for user: {}", appUser.getUsername());
	            return null;
	        }

	        StoredCredential stored = new StoredCredential();
	        logger.info("Creating StoredCredential for user: {}", appUser.getUsername());
	        stored.setAccessToken(appUser.getGmailAccessToken());
	        stored.setRefreshToken(appUser.getGmailRefreshToken());
	        stored.setExpirationTimeMilliseconds(appUser.getGmailTokenExpiry());

	        V value = (V) stored;
	        cache.put(key, value);
	        return value;
	    }

	    logger.warn("Key not matched or credentials missing for key: {}", key);
	    return null;
	}



	@Override
	public DataStore<V> set(String key, V value) throws IOException {
		cache.put(key, value);
		logger.info("Setting key: {} for user: {}", key, appUser.getUsername());
		if ("user".equals(key) && value instanceof StoredCredential stored) {
			appUser.setGmailAccessToken(stored.getAccessToken());
			appUser.setGmailRefreshToken(stored.getRefreshToken());
			appUser.setGmailTokenExpiry(stored.getExpirationTimeMilliseconds());
			appUserRepository.save(appUser);
		}
		return this;
	}

	@Override
	public DataStore<V> clear() throws IOException {
		cache.clear();
		return this;
	}

	@Override
	public DataStore<V> delete(String key) throws IOException {
		cache.remove(key);
		return this;
	}
}
