package com.nklmthr.finance.personal.service.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.nklmthr.finance.personal.model.AppUser;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.nklmthr.finance.personal.repository.AppUserRepository;
import com.nklmthr.finance.personal.service.AppUserService;

@ExtendWith(MockitoExtension.class)
class GmailAuthHelperTest {

    @Mock private AppUserService appUserService;
    @Mock private AppUserRepository appUserRepository;

    @InjectMocks private GmailAuthHelper helper;

    private AppUser user;

    @BeforeEach
    void setUp(){
        user = AppUser.builder().id("u1").username("jane").password("p").role("USER").email("j@e.com").build();
        lenient().when(appUserService.getCurrentUser()).thenReturn(user);
        lenient().when(appUserService.getRepository()).thenReturn(appUserRepository);
        ReflectionTestUtils.setField(helper, "redirectUri", "http://localhost/callback");
    }

    @Test
    void isUserConnected_falseWhenNoCredential() throws Exception {
        GoogleAuthorizationCodeFlow flow = org.mockito.Mockito.mock(GoogleAuthorizationCodeFlow.class);
        // Spy helper to override buildFlow
        GmailAuthHelper spy = org.mockito.Mockito.spy(helper);
        org.mockito.Mockito.doReturn(flow).when(spy).buildFlow(user);
        when(flow.loadCredential("jane")).thenReturn((Credential)null);
        assertThat(spy.isUserConnected()).isFalse();
    }

    @Test
    void isUserConnected_trueWhenCredentialPresent() throws Exception {
        GoogleAuthorizationCodeFlow flow = org.mockito.Mockito.mock(GoogleAuthorizationCodeFlow.class);
        GmailAuthHelper spy = org.mockito.Mockito.spy(helper);
        org.mockito.Mockito.doReturn(flow).when(spy).buildFlow(user);
        Credential cred = org.mockito.Mockito.mock(Credential.class);
        when(cred.getAccessToken()).thenReturn("token");
        when(flow.loadCredential("jane")).thenReturn(cred);
        assertThat(spy.isUserConnected()).isTrue();
    }

    @Test
    void getAuthorizationUrl_buildsUrl() throws Exception {
        GoogleAuthorizationCodeFlow flow = org.mockito.Mockito.mock(GoogleAuthorizationCodeFlow.class);
        GmailAuthHelper spy = org.mockito.Mockito.spy(helper);
        org.mockito.Mockito.doReturn(flow).when(spy).buildFlow(user);
        GoogleAuthorizationCodeRequestUrl urlReq = org.mockito.Mockito.mock(GoogleAuthorizationCodeRequestUrl.class);
        when(flow.newAuthorizationUrl()).thenReturn(urlReq);
        when(urlReq.setRedirectUri(org.mockito.ArgumentMatchers.anyString())).thenReturn(urlReq);
        when(urlReq.setAccessType(org.mockito.ArgumentMatchers.anyString())).thenReturn(urlReq);
        when(urlReq.setApprovalPrompt(org.mockito.ArgumentMatchers.anyString())).thenReturn(urlReq);
        when(urlReq.setState(org.mockito.ArgumentMatchers.anyString())).thenReturn(urlReq);
        when(urlReq.build()).thenReturn("http://auth?redirect_uri=http://localhost/callback");

        String url = spy.getAuthorizationUrl(user);
        assertThat(url).contains("http://localhost/callback");
    }
}
