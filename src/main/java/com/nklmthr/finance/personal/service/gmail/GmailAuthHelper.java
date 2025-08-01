package com.nklmthr.finance.personal.service.gmail;

import java.io.InputStreamReader;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.GmailScopes;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.service.AppUserService;

@Component
public class GmailAuthHelper {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GmailAuthHelper.class);

	@Value("${gmail.oauth.redirect-uri}")
	private String redirectUri;

	private final AppUserService appUserService;

	public GmailAuthHelper(AppUserService appUserService) {
		this.appUserService = appUserService;
	}

	private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final List<String> SCOPES = List.of(GmailScopes.GMAIL_READONLY);

	public String getAuthorizationUrl(AppUser user) throws Exception {
		GoogleAuthorizationCodeFlow flow = buildFlow(user);
		return flow.newAuthorizationUrl().setRedirectUri(redirectUri).setState(user.getUsername()).build();
	}

	public void exchangeCodeForTokens(AppUser user, String code) throws Exception {
		GoogleAuthorizationCodeFlow flow = buildFlow(user);
		TokenResponse tokenResponse = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
		logger.info("Exchanging code for tokens for user: {}", user.getUsername());
		flow.createAndStoreCredential(tokenResponse, "user");
	}

	public GoogleAuthorizationCodeFlow buildFlow(AppUser appUser) throws Exception {
		var clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
				new InputStreamReader(getClass().getResourceAsStream(CREDENTIALS_FILE_PATH)));

		return new GoogleAuthorizationCodeFlow.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY,
				clientSecrets, SCOPES)
				.setDataStoreFactory(new AppUserDataStoreFactory(appUser, appUserService.getRepository()))
				.setAccessType("offline").build();
	}

	public boolean isUserConnected(String username) {
		try {
			AppUser user = appUserService.findByUsername(username);
			return user.getGmailAccessToken() != null && user.getGmailRefreshToken() != null
					&& (user.getGmailTokenExpiry() == null || user.getGmailTokenExpiry() > System.currentTimeMillis());
		} catch (Exception e) {
			return false;
		}
	}

}
