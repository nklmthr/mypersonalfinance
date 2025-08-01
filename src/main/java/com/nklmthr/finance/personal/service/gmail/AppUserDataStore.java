package com.nklmthr.finance.personal.service.gmail;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.AbstractDataStore;
import com.google.api.client.util.store.DataStore;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AppUserRepository;

public class AppUserDataStore<V extends java.io.Serializable> extends AbstractDataStore<V> {

	private final AppUser appUser;
	private final AppUserRepository appUserRepository;
	private final Map<String, V> cache = new ConcurrentHashMap<>();

	protected AppUserDataStore(AppUserDataStoreFactory factory, AppUser appUser, AppUserRepository appUserRepository,
			String id) {
		super(factory, id);
		this.appUser = appUser;
		this.appUserRepository = appUserRepository;

		// If token already exists in DB, load it into cache
		if (appUser.getGmailAccessToken() != null) {
			StoredCredential cred = new StoredCredential().setAccessToken(appUser.getGmailAccessToken())
					.setRefreshToken(appUser.getGmailRefreshToken())
					.setExpirationTimeMilliseconds(appUser.getGmailTokenExpiry());
			// noinspection unchecked
			cache.put("user", (V) cred);
		}
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
		return cache.get(key);
	}

	@Override
	public DataStore<V> set(String key, V value) throws IOException {
		if (!"user".equals(key))
			throw new IOException("Only 'user' key supported");

		cache.put(key, value);

		if (value instanceof StoredCredential credential) {
			appUser.setGmailAccessToken(credential.getAccessToken());
			appUser.setGmailRefreshToken(credential.getRefreshToken());
			appUser.setGmailTokenExpiry(credential.getExpirationTimeMilliseconds());
			appUserRepository.save(appUser);
		}

		return this;
	}

	@Override
	public DataStore<V> clear() throws IOException {
		cache.clear();
		appUser.setGmailAccessToken(null);
		appUser.setGmailRefreshToken(null);
		appUser.setGmailTokenExpiry(null);
		appUserRepository.save(appUser);
		return this;
	}

	@Override
	public DataStore<V> delete(String key) throws IOException {
		cache.remove(key);
		if ("user".equals(key)) {
			appUser.setGmailAccessToken(null);
			appUser.setGmailRefreshToken(null);
			appUser.setGmailTokenExpiry(null);
			appUserRepository.save(appUser);
		}
		return this;
	}
}
