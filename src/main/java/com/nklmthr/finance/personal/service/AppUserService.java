package com.nklmthr.finance.personal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AppUserRepository;

@Service
public class AppUserService {

	@Autowired
	private AppUserRepository appUserRepository;

	private static final Logger logger = LoggerFactory.getLogger(AppUserService.class);
	private final ThreadLocal<AppUser> currentUserCache = new ThreadLocal<>();

	public AppUser getCurrentUser() {
        if (currentUserCache.get() != null) {
			logger.info("Returning cached current user: " + currentUserCache.get().getUsername());
            return currentUserCache.get();
        }
		logger.info("Fetching current user from security context");
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null) {
			logger.error("No authentication in context");
			throw new RuntimeException("No authentication in context");
		}
		String username = auth.getName();
		return appUserRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
	}

	public AppUser findByUsername(String username) {
		if (username == null || username.isEmpty()) {
			logger.error("Username is null or empty");
			return null;
		}
		logger.info("Finding user by username: " + username);
		return appUserRepository.findByUsername(username).orElse(null);
	}

	public void save(AppUser user) {
		if (user == null) {
			logger.error("User is null");
			throw new IllegalArgumentException("User cannot be null");
		}
		logger.info("Saving user: " + user.getUsername());
		appUserRepository.save(user);
	}

	public AppUserRepository getRepository() {
		logger.info("Returning AppUserRepository instance");
		return appUserRepository;
	}
}
