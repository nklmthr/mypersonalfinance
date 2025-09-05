package com.nklmthr.finance.personal.controller;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.service.AppUserService;
import com.nklmthr.finance.personal.service.gmail.GmailAuthHelper;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/gmail")
public class GmailAuthorizationController {

	Logger logger = LoggerFactory.getLogger(GmailAuthorizationController.class);
	@Autowired
	private GmailAuthHelper gmailAuthHelper;

	@Autowired
	private AppUserService appUserService;

	@Value("${app.frontend.base-url}")
	private String frontendBaseUrl;

	@GetMapping("/authorize-url")
	public ResponseEntity<Map<String, String>> getAuthorizeUrl() throws Exception {
		AppUser currentUser = appUserService.getCurrentUser();
		String url = gmailAuthHelper.getAuthorizationUrl(currentUser);
		logger.info("Generated Gmail OAuth URL for user {}: {}", currentUser.getUsername(), url);
		return ResponseEntity.ok(Map.of("url", url));
	}

	@GetMapping("/oauth/callback")
	public void oauthCallback(@RequestParam("code") String code, @RequestParam("state") String username,
			HttpServletResponse response) throws IOException {
		try {
			logger.debug("OAuth callback received for state={} code={}", username, code);
			AppUser user = appUserService.findByUsername(username); // <--- This might be null if no auth
			logger.debug("Resolved AppUser: {}", user != null ? user.getUsername() : "null");

			gmailAuthHelper.exchangeCodeForTokens(user, code);
			logger.debug("Gmail authorization successful for user: {}", username);

			response.sendRedirect(frontendBaseUrl);
		} catch (Exception e) {
			logger.error("OAuth callback failed", e); // log stacktrace
			response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Authorization failed: " + e.getMessage());
		}
	}

}
