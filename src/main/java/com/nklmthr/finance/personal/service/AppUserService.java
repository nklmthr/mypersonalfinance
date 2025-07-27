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

	private static final Logger logger = LoggerFactory.getLogger(CategoryService.class);

	public AppUser getCurrentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null) {
			throw new RuntimeException("No authentication in context");
		}
		String username = auth.getName();
		logger.info("username=" + username);
		return appUserRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
	}

	public AppUser findByUsername(String username) {
		return appUserRepository.findByUsername(username).orElse(null);
	}

	public void save(AppUser user) {
		appUserRepository.save(user);
	}

	public AppUserRepository getRepository() {
		return appUserRepository;
	}
}
