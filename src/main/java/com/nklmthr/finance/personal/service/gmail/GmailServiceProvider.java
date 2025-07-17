package com.nklmthr.finance.personal.service.gmail;

import java.io.InputStreamReader;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AppUserRepository;

@Component
public class GmailServiceProvider {

	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

	@Autowired
	private AppUserRepository appUserRepository;

	public Gmail getGmailService(AppUser appUser) throws Exception {
		String userKey = "user";

		var clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
				new InputStreamReader(getClass().getResourceAsStream("/credentials.json")));

		var flow = new GoogleAuthorizationCodeFlow.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY,
				clientSecrets, List.of(GmailScopes.GMAIL_READONLY)).setAccessType("offline")
				.setDataStoreFactory(new AppUserDataStoreFactory(appUser, appUserRepository)).build();

		var credential = flow.loadCredential(userKey);

		return new Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
				.setApplicationName("Finance App").build();
	}
}
