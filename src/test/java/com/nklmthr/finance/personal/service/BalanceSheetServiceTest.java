package com.nklmthr.finance.personal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklmthr.finance.personal.dto.BalanceSheetDTO;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountBalanceSnapshot;
import com.nklmthr.finance.personal.model.AccountType;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountBalanceSnapshotRepository;

@ExtendWith(MockitoExtension.class)
class BalanceSheetServiceTest {

    @Mock private AppUserService appUserService;
    @Mock private AccountBalanceSnapshotRepository snapshotRepository;
    @InjectMocks private BalanceSheetService service;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = AppUser.builder().id("u1").username("jane").password("p").role("USER").email("j@e.com").build();
        lenient().when(appUserService.getCurrentUser()).thenReturn(user);
        ReflectionTestUtils.setField(service, "appUserService", appUserService);
        ReflectionTestUtils.setField(service, "accountBalanceSnapshotRepository", snapshotRepository);
    }

    @Test
    void generateMonthlyBalanceSheet_groupsByClassification() {
        AccountType at = AccountType.builder().id("t1").name("Checking").classification("ASSET").appUser(user).build();
        Account acc = new Account();
        acc.setId("a1"); acc.setName("A"); acc.setAccountType(at);
        AccountBalanceSnapshot s1 = AccountBalanceSnapshot.builder().account(acc).balance(new BigDecimal("100")).snapshotDate(LocalDate.now().atStartOfDay()).appUser(user).build();
        when(snapshotRepository.findByAppUserAndSnapshotRange(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(List.of(s1));

        BalanceSheetDTO dto = service.generateMonthlyBalanceSheet(LocalDate.now());
        assertThat(dto.getSummaryByMonth().values().iterator().next()).isEqualByComparingTo(new BigDecimal("100"));
    }
}


