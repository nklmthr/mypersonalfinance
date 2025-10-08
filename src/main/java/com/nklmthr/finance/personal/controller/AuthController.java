package com.nklmthr.finance.personal.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.nklmthr.finance.personal.dto.SignupRequest;
import com.nklmthr.finance.personal.dto.AuthRequest;
import com.nklmthr.finance.personal.dto.AuthResponse;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.security.JwtUtil;
import com.nklmthr.finance.personal.security.SecurityConfig;
import com.nklmthr.finance.personal.service.AppUserService;
import com.nklmthr.finance.personal.service.gmail.GmailAuthHelper;

import jakarta.validation.Valid;
 

@RestController
@RequestMapping("/api")
@Validated
public class AuthController {

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private AppUserService appUserService;

	@Autowired
	private SecurityConfig securityConfig;

	@Autowired
	private JwtUtil jwtUtil;
	
	@Autowired
	private GmailAuthHelper gmailAuthHelper;


	private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

	@PostMapping("/auth/login")
	public ResponseEntity<?> login(@RequestBody @Valid AuthRequest request) {
		try {
			Authentication authentication = authenticationManager
					.authenticate(new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

			SecurityContextHolder.getContext().setAuthentication(authentication);

			String token = jwtUtil.generateToken(request.getUsername());
			return ResponseEntity.ok(new AuthResponse(token));
		} catch (AuthenticationException ex) {
			logger.warn("Failed login attempt for username: {}", request.getUsername());
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
		}
	}

	@GetMapping("/auth/status")
	public ResponseEntity<?> status() {
		AppUser user = appUserService.getCurrentUser();
		logger.info("Authenticated user: {}", user.getUsername());
		return ResponseEntity.ok(user.getUsername());
	}

	@PostMapping("/signup")
	public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest signupRequest) {
		logger.info("Signup attempt for username: {}, password pattern: {}", signupRequest.getUsername(),
				getPasswordPatternSummary(signupRequest.getPassword()));

		if (appUserService.findByUsername(signupRequest.getUsername()) != null) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already exists"));
		}

		AppUser user = new AppUser();
		user.setUsername(signupRequest.getUsername());
		user.setEmail(signupRequest.getEmail());
		user.setPassword(securityConfig.passwordEncoder().encode(signupRequest.getPassword()));
		user.setRole("USER");
		user.setEnabled(true);
		user.setCreatedAt(LocalDateTime.now());

		appUserService.save(user);

		logger.info("User {} signed up successfully", user.getUsername());
		return ResponseEntity.ok("Signup successful");
	}

	@GetMapping("/gmail/status")
	public Map<String, Boolean> getGmailStatus() {
		boolean connected = gmailAuthHelper.isUserConnected();
		return Map.of("connected", connected);
	}

	@PostMapping("/gmail/disconnect")
	public ResponseEntity<?> disconnectGmail() {
		AppUser user = appUserService.getCurrentUser();

		try {
			GoogleAuthorizationCodeFlow flow = gmailAuthHelper.buildFlow(user);
			flow.getCredentialDataStore().delete(user.getUsername());

			user.setGmailAccessToken(null);
			user.setGmailRefreshToken(null);
			user.setGmailTokenExpiry(null);
			appUserService.save(user);

			logger.info("Gmail disconnected for user: {}", user.getUsername());
			return ResponseEntity.ok(Map.of("disconnected", true));
		} catch (Exception e) {
			logger.error("Failed to disconnect Gmail for user {}", user.getUsername(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to disconnect Gmail"));
		}
	}

	@GetMapping("/user/profile")
	public Map<String, Object> getProfile() {
		AppUser user = appUserService.getCurrentUser();
		logger.info("Fetching profile for user: {}", user.getUsername());
		String roleString = user.getRole();
		String[] roles = roleString != null && !roleString.isEmpty() ? roleString.split("\\,") : new String[] { "USER" };
		return Map.of("username", user.getUsername(), "email", user.getEmail(), "roles", roles);
	}

	private String getPasswordPatternSummary(String password) {
		if (password == null) {
			return "null";
		}

		int lower = 0, upper = 0, digits = 0, special = 0;
		for (char c : password.toCharArray()) {
			if (Character.isLowerCase(c))
				lower++;
			else if (Character.isUpperCase(c))
				upper++;
			else if (Character.isDigit(c))
				digits++;
			else
				special++;
		}

		return String.format("len=%d, lower=%d, upper=%d, digits=%d, special=%d", password.length(), lower, upper,
				digits, special);
	}
}
