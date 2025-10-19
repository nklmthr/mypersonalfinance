package com.nklmthr.finance.personal.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountBalanceSnapshot;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountBalanceSnapshotRepository;
import com.nklmthr.finance.personal.repository.AccountRepository;

@ExtendWith(MockitoExtension.class)
class AccountSnapshotServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private AccountBalanceSnapshotRepository snapshotRepository;
    @Mock private AppUserService appUserService;
    @InjectMocks private AccountSnapshotService service;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = AppUser.builder().id("u1").username("jane").password("p").role("USER").email("j@e.com").build();
        lenient().when(appUserService.getCurrentUser()).thenReturn(user);
        ReflectionTestUtils.setField(service, "accountRepository", accountRepository);
        ReflectionTestUtils.setField(service, "snapshotRepository", snapshotRepository);
        ReflectionTestUtils.setField(service, "appUserService", appUserService);
    }

    @Test
    void createSnapshotsForDate_throwsWhenRecentSnapshotsExist() {
        when(accountRepository.findAll()).thenReturn(List.of());
        when(snapshotRepository.findByAppUserAndSnapshotDateAfter(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
            .thenReturn(List.of(AccountBalanceSnapshot.builder().build()));

        assertThatThrownBy(() -> service.createSnapshotsForDate(LocalDateTime.now()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createSnapshotsForDate_savesWhenNoRecent() {
        Account a = new Account(); a.setId("a1"); a.setName("A");
        when(accountRepository.findAll()).thenReturn(List.of(a));
        when(snapshotRepository.findByAppUserAndSnapshotDateAfter(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
            .thenReturn(List.of());

        service.createSnapshotsForDate(LocalDateTime.now());
        verify(snapshotRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}


