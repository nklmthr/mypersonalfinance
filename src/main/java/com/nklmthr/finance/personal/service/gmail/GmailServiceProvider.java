package com.nklmthr.finance.personal.service.gmail;

import java.io.InputStreamReader;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
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
        // Use username as key (better than hardcoded "user")
        String userKey = appUser.getUsername();

        // Load credentials.json from resources
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                JSON_FACTORY,
                new InputStreamReader(getClass().getResourceAsStream("/credentials.json"))
        );

        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                JSON_FACTORY,
                clientSecrets,
                List.of(GmailScopes.GMAIL_READONLY)
        )
        .setAccessType("offline")
        .setDataStoreFactory(new AppUserDataStoreFactory(appUserRepository))
        .build();

        // Load credentials for this user
        var credential = flow.loadCredential(userKey);

        return new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName("Finance App")
                .build();
    }
}
