package com.nklmthr.finance.personal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock private AppUserRepository repo;
    @InjectMocks private AppUserService service;

    @Test
    void getCurrentUser_readsFromSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("jane", "n/a"));
        AppUser jane = AppUser.builder().id("u1").username("jane").password("p").role("USER").email("j@e.com").build();
        when(repo.findByUsername("jane")).thenReturn(Optional.of(jane));

        AppUser u = service.getCurrentUser();
        assertThat(u.getUsername()).isEqualTo("jane");
        // second call returns cached
        AppUser u2 = service.getCurrentUser();
        assertThat(u2).isSameAs(u);
    }

    @Test
    void getCurrentUser_throwsWhenNoAuth() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.getCurrentUser()).isInstanceOf(RuntimeException.class);
    }
}


