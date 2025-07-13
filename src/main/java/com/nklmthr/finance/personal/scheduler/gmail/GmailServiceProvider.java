package com.nklmthr.finance.personal.scheduler.gmail;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.springframework.stereotype.Component;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.nklmthr.finance.personal.model.AppUser;

@Component
public class GmailServiceProvider {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GmailServiceProvider.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    public static Gmail getGmailService(AppUser appUser) throws Exception {
    	
        // Example directory: tokens/user-{username}
        String userKey = "user-" + appUser.getUsername();

        InputStream in = GmailServiceProvider.class.getResourceAsStream("/credentials.json");
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                clientSecrets,
                List.of(GmailScopes.GMAIL_READONLY)
        )
        .setDataStoreFactory(new FileDataStoreFactory(new File("tokens/" + userKey)))
        .setAccessType("offline")
        .build();

        Credential credential = flow.loadCredential(userKey); 
        logger.info("Loaded credentials for user: {} = {}", appUser.getUsername(), credential != null ? "Available" : "Not Available");
        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential
        ).setApplicationName("Finance App").build();
    }
}
