package com.nklmthr.finance.personal.controller;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.security.SecurityConfig;
import com.nklmthr.finance.personal.service.AppUserService;
import com.nklmthr.finance.personal.service.gmail.GmailAuthHelper;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping
public class GmailAuthorizationController {

	Logger logger = LoggerFactory.getLogger(GmailAuthorizationController.class);
	@Autowired
	private GmailAuthHelper gmailAuthHelper;

	@Autowired
	private AppUserService appUserService;

	@Autowired
	private SecurityConfig securityConfig;

	@Value("${app.frontend.base-url}")
	private String frontendBaseUrl;

	@GetMapping("/oauth/authorize")
	public void authorize(HttpServletResponse response) throws Exception {
		AppUser currentUser = appUserService.getCurrentUser();
		String url = gmailAuthHelper.getAuthorizationUrl(currentUser);
		logger.info("Redirecting user {} to Gmail OAuth URL: {}", currentUser.getUsername(), url);
		response.sendRedirect(url);
	}

	@GetMapping("/oauth/callback")
	public void oauthCallback(@RequestParam("code") String code, @RequestParam("state") String username,
			HttpServletResponse response) throws IOException {
		try {
			AppUser user = appUserService.getCurrentUser();
			gmailAuthHelper.exchangeCodeForTokens(user, code);
			logger.info("Gmail authorization successful for user: {}", username);
			logger.info("Redirecting to frontend URL: {}", frontendBaseUrl);
			response.sendRedirect(frontendBaseUrl);
		} catch (Exception e) {
			response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Authorization failed: " + e.getMessage());
		}
	}

	@GetMapping("/api/gmail/status")
	public Map<String, Boolean> getGmailStatus() {
		boolean connected = gmailAuthHelper.isUserConnected();
		return Map.of("connected", connected);
	}

	@GetMapping("/api/auth/status")
	public ResponseEntity<?> status() {
		AppUser user = appUserService.getCurrentUser();
		logger.info("Authenticated user: {}", user.getUsername());
		return ResponseEntity.ok(user.getUsername());
	}

	@PostMapping("/signup")
	public ResponseEntity<?> signup(@RequestBody AppUser appUser) {
		logger.info("Received signup request for user: {}", appUser.getUsername());
		if (appUserService.findByUsername(appUser.getUsername()) != null) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
		}

		AppUser user = new AppUser();
		user.setUsername(appUser.getUsername());
		user.setEmail(appUser.getEmail());
		user.setPassword(securityConfig.passwordEncoder().encode(appUser.getPassword()));
		user.setRole("USER");
		user.setEnabled(true);
		user.setCreatedAt(LocalDateTime.now());
		appUserService.save(user);
		logger.info("User {} signed up successfully", user.getUsername());
		return ResponseEntity.ok("Signup successful");
	}

	@PostMapping("/api/gmail/disconnect")
	public ResponseEntity<?> disconnectGmail() {
		AppUser user = appUserService.getCurrentUser();
		user.setGmailAccessToken(null);
		user.setGmailRefreshToken(null);
		user.setGmailTokenExpiry(null);
		appUserService.save(user);
		logger.info("Gmail disconnected for user: {}", user.getUsername());
		return ResponseEntity.ok(Map.of("disconnected", true));
	}

	@GetMapping("/api/user/profile")
	public Map<String, Object> getProfile() {
		AppUser user = appUserService.getCurrentUser();
		logger.info("Fetching profile for user: {}", user.getUsername());
		return Map.of("username", user.getUsername(), "email", user.getEmail(), "roles", user.getRole().split("\\,"));
	}

}
