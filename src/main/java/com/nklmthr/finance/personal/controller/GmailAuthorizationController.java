package com.nklmthr.finance.personal.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;

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

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.nklmthr.finance.personal.dto.SignupRequest;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.security.SecurityConfig;
import com.nklmthr.finance.personal.service.AppUserService;
import com.nklmthr.finance.personal.service.gmail.GmailAuthHelper;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class GmailAuthorizationController {

	Logger logger = LoggerFactory.getLogger(GmailAuthorizationController.class);
	@Autowired
	private GmailAuthHelper gmailAuthHelper;

	@Autowired
	private AppUserService appUserService;

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

	
	

}
