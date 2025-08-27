package com.nklmthr.finance.personal.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AppUserRepository;

@Service
public class UserService implements UserDetailsService {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UserService.class);
	@Autowired
	private AppUserRepository appUserRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		AppUser appUser = appUserRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
		logger.debug("Loaded user: {}", appUser.getUsername());
		return User.withUsername(appUser.getUsername()).password(appUser.getPassword()).roles(appUser.getRole())
				.build();
	}

}
