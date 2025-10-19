package com.nklmthr.finance.personal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.mapper.AccountMapper;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountType;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Institution;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.repository.AccountRepository;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.AccountTypeRepository;
import com.nklmthr.finance.personal.repository.InstitutionRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AppUserService appUserService;
    @Mock private AccountRepository accountRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private AccountTypeRepository accountTypeRepository;
    @Mock private AccountTransactionRepository accountTransactionRepository;
    @Mock private AccountMapper accountMapper;

    @InjectMocks private AccountService service;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = AppUser.builder().id("u1").username("jane").password("p").role("USER").email("j@e.com").build();
        lenient().when(appUserService.getCurrentUser()).thenReturn(user);
        ReflectionTestUtils.setField(service, "appUserService", appUserService);
        ReflectionTestUtils.setField(service, "accountRepository", accountRepository);
        ReflectionTestUtils.setField(service, "institutionRepository", institutionRepository);
        ReflectionTestUtils.setField(service, "accountTypeRepository", accountTypeRepository);
        ReflectionTestUtils.setField(service, "acountTransactionRepository", accountTransactionRepository);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
    }

    private Account make(String id, String name, BigDecimal balance) {
        Account a = new Account();
        a.setId(id); a.setName(name); a.setBalance(balance); a.setAppUser(user);
        a.setInstitution(new Institution()); a.getInstitution().setId("ins");
        a.setAccountType(new AccountType()); a.getAccountType().setId("type");
        return a;
    }

    @Test
    void getAllAccounts_mapsToDTOs() {
        Account a1 = make("a1","A1", BigDecimal.ONE);
        Account a2 = make("a2","A2", BigDecimal.TWO);
        when(accountRepository.findAllByAppUser(user, Sort.by("name").ascending())).thenReturn(List.of(a1, a2));
        AccountDTO d1 = new AccountDTO("a1","A1", BigDecimal.ONE, null, null, null, null, null);
        AccountDTO d2 = new AccountDTO("a2","A2", BigDecimal.TWO, null, null, null, null, null);
        when(accountMapper.toDTO(a1)).thenReturn(d1);
        when(accountMapper.toDTO(a2)).thenReturn(d2);

        List<AccountDTO> result = service.getAllAccounts();
        assertThat(result).containsExactly(d1, d2);
    }

    @Test
    void findById_usesMapper() {
        Account a = make("a1","A1", BigDecimal.ONE);
        when(accountRepository.findByAppUserAndId(user, "a1")).thenReturn(Optional.of(a));
        AccountDTO dto = new AccountDTO("a1","A1", BigDecimal.ONE, null, null, null, null, null);
        when(accountMapper.toDTO(a)).thenReturn(dto);

        AccountDTO result = service.findById("a1");
        assertThat(result).isEqualTo(dto);
    }

    @Test
    void createAccount_checksInstitutionAndType() {
        Account toSave = make(null, "A1", BigDecimal.TEN);
        when(accountMapper.toEntity(any(AccountDTO.class))).thenReturn(toSave);
        when(institutionRepository.findByAppUserAndId(user, "ins")).thenReturn(Optional.of(new Institution()));
        when(accountTypeRepository.existsByAppUserAndId(user, "type")).thenReturn(true);
        Account saved = make("a1","A1", BigDecimal.TEN);
        when(accountRepository.save(toSave)).thenReturn(saved);
        AccountDTO dto = new AccountDTO("a1","A1", BigDecimal.TEN, null, null, null, null, null);
        when(accountMapper.toDTO(saved)).thenReturn(dto);

        AccountDTO result = service.createAccount(new AccountDTO(null,"A1", BigDecimal.TEN, null, null, null, null, null));
        assertThat(result).isEqualTo(dto);
    }

    @Test
    void createAccount_throwsWhenMissingInstitution() {
        Account toSave = make(null, "A1", BigDecimal.TEN);
        when(accountMapper.toEntity(any(AccountDTO.class))).thenReturn(toSave);
        when(institutionRepository.findByAppUserAndId(user, "ins")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createAccount(new AccountDTO(null,"A1", BigDecimal.TEN, null, null, null, null, null)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Institution not found");
    }

    @Test
    void createAccount_throwsWhenMissingAccountType() {
        Account toSave = make(null, "A1", BigDecimal.TEN);
        when(accountMapper.toEntity(any(AccountDTO.class))).thenReturn(toSave);
        when(institutionRepository.findByAppUserAndId(user, "ins")).thenReturn(Optional.of(new Institution()));
        when(accountTypeRepository.existsByAppUserAndId(user, "type")).thenReturn(false);
        assertThatThrownBy(() -> service.createAccount(new AccountDTO(null,"A1", BigDecimal.TEN, null, null, null, null, null)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("AccountType not found");
    }

    @Test
    void updateAccount_updatesFieldsAndSaves() {
        Account existing = make("a1","Old", new BigDecimal("100"));
        when(accountRepository.findByAppUserAndId(user, "a1")).thenReturn(Optional.of(existing));
        Account updatedEntity = make("a1","New", new BigDecimal("150"));
        updatedEntity.setAccountNumber("123");
        updatedEntity.setAccountKeywords("k");
        updatedEntity.setAccountAliases("al");
        when(accountMapper.toEntity(any(AccountDTO.class))).thenReturn(updatedEntity);
        when(accountRepository.save(existing)).thenReturn(existing);
        when(accountMapper.toDTO(existing)).thenReturn(new AccountDTO("a1","New", new BigDecimal("150"), null, null, "123","k","al"));

        AccountDTO result = service.updateAccount("a1", new AccountDTO("a1","New", new BigDecimal("150"), null, null, "123","k","al"));
        assertThat(result.name()).isEqualTo("New");
        verify(accountRepository).save(existing);
    }

    @Test
    void deleteAccount_throwsWhenHasTransactions() {
        when(accountTransactionRepository.findByAppUserAndAccountId(user, "a1")).thenReturn(List.of(new AccountTransaction()));
        assertThatThrownBy(() -> service.deleteAccount("a1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteAccount_deletesWhenNoTransactions() {
        when(accountTransactionRepository.findByAppUserAndAccountId(user, "a1")).thenReturn(List.of());
        service.deleteAccount("a1");
        verify(accountRepository).deleteById("a1");
    }

    @Test
    void getFilteredAccounts_handlesBranches() {
        when(accountRepository.findByAppUserAndAccountTypeIdAndInstitutionId(user, "t","i")).thenReturn(List.of());
        when(accountRepository.findByAppUserAndAccountTypeId(user, "t")).thenReturn(List.of());
        when(accountRepository.findByAppUserAndInstitutionId(user, "i")).thenReturn(List.of());
        when(accountRepository.findAllByAppUser(user, Sort.by("name").ascending())).thenReturn(List.of());
        assertThat(service.getFilteredAccounts("t","i")).isEmpty();
        assertThat(service.getFilteredAccounts("t",null)).isEmpty();
        assertThat(service.getFilteredAccounts(null,"i")).isEmpty();
        assertThat(service.getFilteredAccounts(null,null)).isEmpty();
    }

    @Test
    void getAccountByName_throwsWhenMissing() {
        when(accountRepository.findByAppUserAndName(user, "X")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getAccountByName("X", user)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void save_setsAppUser() {
        when(accountMapper.toEntity(any(AccountDTO.class))).thenReturn(new Account());
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        service.save(new AccountDTO(null, "A", BigDecimal.TEN, null, null, null, null, null));
        verify(accountRepository, times(1)).save(any(Account.class));
    }
}


