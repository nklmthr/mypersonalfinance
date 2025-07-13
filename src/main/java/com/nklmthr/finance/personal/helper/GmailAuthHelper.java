package com.nklmthr.finance.personal.helper;

import java.io.File;
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
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.GmailScopes;

@Component
public class GmailAuthHelper {

    @Value("${gmail.oauth.redirect-uri}")
    private String redirectUri;

    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = List.of(GmailScopes.GMAIL_READONLY);

    public String getAuthorizationUrl(String userName) throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow(userName);
        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setState(userName)
                .build();
    }

    public void exchangeCodeForTokens(String userName, String code) throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow(userName);
        TokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();
        flow.createAndStoreCredential(tokenResponse, "user-" + userName);
    }

    public GoogleAuthorizationCodeFlow buildFlow(String userName) throws Exception {
        var clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(getClass().getResourceAsStream(CREDENTIALS_FILE_PATH)));

        return new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                clientSecrets,
                SCOPES
        ).setAccessType("offline")
         .setDataStoreFactory(new FileDataStoreFactory(new File("tokens/user-" + userName)))
         .build();
    }
}
