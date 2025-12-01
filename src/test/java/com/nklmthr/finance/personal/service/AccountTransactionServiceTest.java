package com.nklmthr.finance.personal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.dto.AccountTransactionDTO;
import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.dto.LabelDTO;
import com.nklmthr.finance.personal.dto.TransferRequest;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.mapper.AccountMapper;
import com.nklmthr.finance.personal.mapper.AccountTransactionMapper;
import com.nklmthr.finance.personal.mapper.CategoryMapper;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AccountType;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.model.Institution;
import com.nklmthr.finance.personal.model.Label;
import com.nklmthr.finance.personal.repository.AccountRepository;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;

@ExtendWith(MockitoExtension.class)
class AccountTransactionServiceTest {

    @Mock
    private AppUserService appUserService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountTransactionRepository accountTransactionRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private LabelService labelService;

    @Mock
    private AccountTransactionMapper accountTransactionMapper;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private AccountTransactionService service;

    private AppUser currentUser;

    @BeforeEach
    void setUp() {
        currentUser = AppUser.builder()
            .id("user-1")
            .username("jane.doe")
            .password("pass")
            .role("USER")
            .email("jane@example.com")
            .build();
        lenient().when(appUserService.getCurrentUser()).thenReturn(currentUser);

        // Inject mocks into @Autowired fields
        ReflectionTestUtils.setField(service, "appUserService", appUserService);
        ReflectionTestUtils.setField(service, "accountRepository", accountRepository);
        ReflectionTestUtils.setField(service, "accountTransactionRepository", accountTransactionRepository);
        ReflectionTestUtils.setField(service, "categoryService", categoryService);
        ReflectionTestUtils.setField(service, "labelService", labelService);
        ReflectionTestUtils.setField(service, "accountTransactionMapper", accountTransactionMapper);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
    }

    private Account createAccount(String id, BigDecimal balance) {
        Account a = new Account();
        a.setId(id);
        a.setName("A-" + id);
        a.setBalance(balance);
        a.setAppUser(currentUser);
        return a;
    }

    @Test
    void createTransfer_updatesBalancesAndCreatesCredit() throws Exception {
        Account from = createAccount("a1", new BigDecimal("1000"));
        Account to = createAccount("a2", new BigDecimal("500"));
        AccountTransaction debit = AccountTransaction.builder()
            .id("tx1").amount(new BigDecimal("100")).type(TransactionType.DEBIT)
            .account(from).appUser(currentUser).description("Transfer out").build();
        Category transferCat = new Category();
        transferCat.setId("ct");
        transferCat.setName("TRANSFERS");
        transferCat.setAppUser(currentUser);

        when(accountTransactionRepository.findByAppUserAndId(currentUser, "tx1")).thenReturn(Optional.of(debit));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "tx1")).thenReturn(List.of());
        when(categoryService.getTransferCategory()).thenReturn(transferCat);
        when(accountRepository.findByAppUserAndId(currentUser, "a2")).thenReturn(Optional.of(to));
        
        // Mock save to return transaction with generated ID for credit
        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenAnswer(invocation -> {
            AccountTransaction tx = invocation.getArgument(0);
            if (tx.getId() == null && tx.getType() == TransactionType.CREDIT) {
                tx.setId("credit-1"); // Simulate ID generation for new credit
            }
            return tx;
        });

        service.createTransfer(TransferRequest.builder()
            .sourceTransactionId("tx1").destinationAccountId("a2").explanation("x").build());

        assertThat(from.getBalance()).isEqualByComparingTo("900");
        assertThat(to.getBalance()).isEqualByComparingTo("600");
        verify(accountRepository, times(1)).save(from);
        verify(accountRepository, times(1)).save(to);

        // Verify transaction repository saves occurred (credit + debit + credit again for linking)
        verify(accountTransactionRepository, atLeast(2)).save(any(AccountTransaction.class));
        
        // Verify the debit transaction now has linkedTransferId set
        assertThat(debit.getLinkedTransferId()).isNotNull();
        assertThat(debit.getCategory()).isEqualTo(transferCat);
    }

    @Test
    void createTransfer_preventsTransferOfSplitTransaction() {
        Account from = createAccount("a1", new BigDecimal("1000"));
        AccountTransaction parent = AccountTransaction.builder()
            .id("p1").amount(BigDecimal.ZERO).type(TransactionType.DEBIT)
            .account(from).appUser(currentUser).description("Split transaction").build();
        AccountTransaction child = AccountTransaction.builder()
            .id("c1").amount(new BigDecimal("100")).type(TransactionType.DEBIT)
            .account(from).appUser(currentUser).parent("p1").build();

        when(accountTransactionRepository.findByAppUserAndId(currentUser, "p1")).thenReturn(Optional.of(parent));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "p1")).thenReturn(List.of(child));

        TransferRequest request = TransferRequest.builder()
            .sourceTransactionId("p1")
            .destinationAccountId("a2")
            .explanation("Transfer attempt")
            .build();

        assertThatThrownBy(() -> service.createTransfer(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot transfer a split transaction");

        // Verify no changes were made
        verify(accountTransactionRepository, never()).save(any(AccountTransaction.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createTransfer_preventsTransferOfDebitTransaction_alreadyHasLinkedTransferId() {
        // Test: Attempting to transfer a DEBIT transaction that already has linkedTransferId
        Account from = createAccount("a1", new BigDecimal("1000"));
        AccountTransaction debitAlreadyTransferred = AccountTransaction.builder()
            .id("debit1")
            .linkedTransferId("credit1") // Already part of a transfer (debit side)
            .amount(new BigDecimal("100"))
            .type(TransactionType.DEBIT)
            .account(from)
            .appUser(currentUser)
            .description("Debit side of transfer")
            .build();

        when(accountTransactionRepository.findByAppUserAndId(currentUser, "debit1")).thenReturn(Optional.of(debitAlreadyTransferred));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "debit1")).thenReturn(List.of());

        TransferRequest request = TransferRequest.builder()
            .sourceTransactionId("debit1")
            .destinationAccountId("a3")
            .explanation("Attempting second transfer from debit")
            .build();

        assertThatThrownBy(() -> service.createTransfer(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already part of a transfer");

        // Verify no changes were made
        verify(accountTransactionRepository, never()).save(any(AccountTransaction.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createTransfer_preventsTransferOfCreditTransaction_alreadyHasLinkedTransferId() {
        // Test: Attempting to transfer a CREDIT transaction that already has linkedTransferId
        Account to = createAccount("a2", new BigDecimal("500"));
        AccountTransaction creditAlreadyTransferred = AccountTransaction.builder()
            .id("credit1")
            .linkedTransferId("debit1") // Already part of a transfer (credit side)
            .amount(new BigDecimal("100"))
            .type(TransactionType.CREDIT)
            .account(to)
            .appUser(currentUser)
            .description("Credit side of transfer")
            .build();

        when(accountTransactionRepository.findByAppUserAndId(currentUser, "credit1")).thenReturn(Optional.of(creditAlreadyTransferred));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "credit1")).thenReturn(List.of());

        TransferRequest request = TransferRequest.builder()
            .sourceTransactionId("credit1")
            .destinationAccountId("a3")
            .explanation("Attempting second transfer from credit")
            .build();

        assertThatThrownBy(() -> service.createTransfer(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already part of a transfer");

        // Verify no changes were made
        verify(accountTransactionRepository, never()).save(any(AccountTransaction.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createTransfer_preventsTransferOfDebitTransaction_referencedByCredit() {
        // Test: Attempting to transfer a DEBIT that is referenced by a credit's linkedTransferId
        // (Edge case: if debit somehow doesn't have linkedTransferId but credit points to it)
        Account from = createAccount("a1", new BigDecimal("1000"));
        Account to = createAccount("a2", new BigDecimal("500"));
        
        AccountTransaction debitReferenced = AccountTransaction.builder()
            .id("debit1")
            .linkedTransferId(null) // Edge case: doesn't point to credit yet
            .amount(new BigDecimal("100"))
            .type(TransactionType.DEBIT)
            .account(from)
            .appUser(currentUser)
            .description("Debit side")
            .build();

        AccountTransaction creditSide = AccountTransaction.builder()
            .id("credit1")
            .linkedTransferId("debit1") // Credit points to this debit
            .amount(new BigDecimal("100"))
            .type(TransactionType.CREDIT)
            .account(to)
            .appUser(currentUser)
            .description("Credit side")
            .build();

        when(accountTransactionRepository.findByAppUserAndId(currentUser, "debit1")).thenReturn(Optional.of(debitReferenced));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "debit1")).thenReturn(List.of());
        when(accountTransactionRepository.findByAppUserAndLinkedTransferId(currentUser, "debit1")).thenReturn(Optional.of(creditSide));

        TransferRequest request = TransferRequest.builder()
            .sourceTransactionId("debit1")
            .destinationAccountId("a3")
            .explanation("Attempting second transfer")
            .build();

        assertThatThrownBy(() -> service.createTransfer(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already part of a transfer");

        // Verify no changes were made
        verify(accountTransactionRepository, never()).save(any(AccountTransaction.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createTransfer_preventsTransferOfCreditTransaction_referencedByDebit() {
        // Test: Attempting to transfer a CREDIT that is referenced by a debit's linkedTransferId
        // (Edge case: if credit somehow doesn't have linkedTransferId but debit points to it)
        Account from = createAccount("a1", new BigDecimal("1000"));
        Account to = createAccount("a2", new BigDecimal("500"));
        
        AccountTransaction creditReferenced = AccountTransaction.builder()
            .id("credit1")
            .linkedTransferId(null) // Edge case: doesn't point to debit yet
            .amount(new BigDecimal("100"))
            .type(TransactionType.CREDIT)
            .account(to)
            .appUser(currentUser)
            .description("Credit side")
            .build();

        AccountTransaction debitSide = AccountTransaction.builder()
            .id("debit1")
            .linkedTransferId("credit1") // Debit points to this credit
            .amount(new BigDecimal("100"))
            .type(TransactionType.DEBIT)
            .account(from)
            .appUser(currentUser)
            .description("Debit side")
            .build();

        when(accountTransactionRepository.findByAppUserAndId(currentUser, "credit1")).thenReturn(Optional.of(creditReferenced));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "credit1")).thenReturn(List.of());
        when(accountTransactionRepository.findByAppUserAndLinkedTransferId(currentUser, "credit1")).thenReturn(Optional.of(debitSide));

        TransferRequest request = TransferRequest.builder()
            .sourceTransactionId("credit1")
            .destinationAccountId("a3")
            .explanation("Attempting second transfer")
            .build();

        assertThatThrownBy(() -> service.createTransfer(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already part of a transfer");

        // Verify no changes were made
        verify(accountTransactionRepository, never()).save(any(AccountTransaction.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void splitTransaction_successful() {
        Account account = createAccount("a1", new BigDecimal("1000"));
        AccountTransaction parent = AccountTransaction.builder()
            .id("p1").amount(new BigDecimal("300")).account(account).appUser(currentUser).currency("INR").build();
        when(accountTransactionRepository.findById("p1")).thenReturn(Optional.of(parent));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "p1")).thenReturn(List.of());
        Category splitCat = new Category();
        splitCat.setId("split");
        splitCat.setName("SPLIT");
        splitCat.setAppUser(currentUser);
        when(categoryService.getSplitTrnsactionCategory()).thenReturn(splitCat);
        Category foodCat = new Category();
        foodCat.setId("cat");
        foodCat.setName("Food");
        foodCat.setAppUser(currentUser);
        when(categoryService.getCategoryById("cat")).thenReturn(foodCat);

        CategoryDTO catDTO = new CategoryDTO();
        catDTO.setId("cat");
        AccountDTO accDTO = new AccountDTO("a1", "Test Account", BigDecimal.ZERO, null, null, null, null, null);

        AccountTransactionDTO st1 = new AccountTransactionDTO(
            null,
            LocalDateTime.now(),
            new BigDecimal("100"),
            "s1",
            null,
            null,
            null,
            TransactionType.DEBIT,
            accDTO,
            catDTO,
            "p1",
            List.of(),
            null,
            null,
            null,
            null,
            null,
            "INR",
            null,
            null,
            null
        );

        AccountTransactionDTO st2 = new AccountTransactionDTO(
            null,
            LocalDateTime.now(),
            new BigDecimal("200"),
            "s2",
            null,
            null,
            null,
            TransactionType.DEBIT,
            accDTO,
            catDTO,
            "p1",
            List.of(),
            null,
            null,
            null,
            null,
            null,
            "INR",
            null,
            null,
            null
        );

        ResponseEntity<String> resp = service.splitTransaction(List.of(st1, st2));
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        verify(accountTransactionRepository, times(3)).save(any(AccountTransaction.class)); // two children + parent
        verify(accountTransactionRepository, times(1)).save(parent); // parent saved once
    }

    @Test
    void splitTransaction_badRequestWhenEmpty() {
        ResponseEntity<String> resp = service.splitTransaction(List.of());
        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void splitTransaction_badRequestWhenParentMissing() {
        when(accountTransactionRepository.findById("p1")).thenReturn(Optional.empty());
        AccountTransactionDTO st = new AccountTransactionDTO(
            null,
            LocalDateTime.now(),
            new BigDecimal("100"),
            "test",
            null,
            null,
            null,
            TransactionType.DEBIT,
            null,
            new CategoryDTO(),
            "p1",
            List.of(),
            null,
            null,
            null,
            null,
            null,
            "INR",
            null,
            null,
            null
        );
        ResponseEntity<String> resp = service.splitTransaction(List.of(st));
        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void updateTransaction_sameAccount_adjustsBalance() {
        Account acc = createAccount("a1", new BigDecimal("1000"));
        AccountTransaction existing = AccountTransaction.builder()
            .id("tx1").amount(new BigDecimal("50")).type(TransactionType.DEBIT)
            .account(acc).appUser(currentUser).build();

        when(accountTransactionRepository.findById("tx1")).thenReturn(Optional.of(existing));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "tx1")).thenReturn(List.of());
        when(accountRepository.findByAppUserAndId(currentUser, "a1")).thenReturn(Optional.of(acc));
        Category foodCat2 = new Category();
        foodCat2.setId("cat");
        foodCat2.setName("Food");
        foodCat2.setAppUser(currentUser);
        when(categoryService.getCategoryById("cat")).thenReturn(foodCat2);

        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenReturn(new AccountTransaction());
        when(accountTransactionMapper.toDTO(any(AccountTransaction.class))).thenReturn(new AccountTransactionDTO("tx1", LocalDateTime.now(), new BigDecimal("20"), "d", null, null, null, TransactionType.CREDIT, new AccountDTO("a1","A-1", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null));

        AccountTransactionDTO update = new AccountTransactionDTO("tx1", LocalDateTime.now(), new BigDecimal("20"), "d", null, null, null, TransactionType.CREDIT, new AccountDTO("a1","A-1", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO("cat", "Food", null, false, List.of()), null, List.of(), null, null, null, null, null, null, null, null, null);

        Optional<AccountTransactionDTO> result = service.updateTransaction("tx1", update);

        assertThat(result).isPresent();
        // oldType=DEBIT refunds 50, then CREDIT adds 20 => 1070
        assertThat(acc.getBalance()).isEqualByComparingTo("1070");
        verify(accountRepository, times(1)).save(acc); // Verify account is saved
        verify(accountTransactionRepository, times(1)).save(any(AccountTransaction.class));
    }

    @Test
    void updateTransaction_differentAccount_adjustsBoth() {
        Account oldAcc = createAccount("a1", new BigDecimal("1000"));
        Account newAcc = createAccount("a2", new BigDecimal("500"));
        AccountTransaction existing = AccountTransaction.builder()
            .id("tx1").amount(new BigDecimal("30")).type(TransactionType.CREDIT)
            .account(oldAcc).appUser(currentUser).build();

        when(accountTransactionRepository.findById("tx1")).thenReturn(Optional.of(existing));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "tx1")).thenReturn(List.of());
        when(accountRepository.findByAppUserAndId(currentUser, "a2")).thenReturn(Optional.of(newAcc));
        Category foodCat3 = new Category();
        foodCat3.setId("cat");
        foodCat3.setName("Food");
        foodCat3.setAppUser(currentUser);
        when(categoryService.getCategoryById("cat")).thenReturn(foodCat3);

        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenReturn(new AccountTransaction());
        when(accountTransactionMapper.toDTO(any(AccountTransaction.class))).thenReturn(new AccountTransactionDTO("tx1", LocalDateTime.now(), new BigDecimal("40"), "d", null, null, null, TransactionType.DEBIT, new AccountDTO("a2","A-2", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null));

        AccountTransactionDTO update = new AccountTransactionDTO("tx1", LocalDateTime.now(), new BigDecimal("40"), "d", null, null, null, TransactionType.DEBIT, new AccountDTO("a2","A-2", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO("cat", "Food", null, false, List.of()), null, List.of(), null, null, null, null, null, null, null, null, null);

        Optional<AccountTransactionDTO> result = service.updateTransaction("tx1", update);

        assertThat(result).isPresent();
        // oldType=CREDIT: oldAcc -= 30 => 970; newType=DEBIT on newAcc: 500 - 40 => 460
        assertThat(oldAcc.getBalance()).isEqualByComparingTo("970");
        assertThat(newAcc.getBalance()).isEqualByComparingTo("460");
        verify(accountRepository, times(1)).save(oldAcc);
        verify(accountRepository, times(1)).save(newAcc);
    }
    
    @Test
    void updateTransaction_differentAccount_sameType_adjustsBoth() {
        Account oldAcc = createAccount("a1", new BigDecimal("1000"));
        Account newAcc = createAccount("a2", new BigDecimal("500"));
        AccountTransaction existing = AccountTransaction.builder()
            .id("tx1").amount(new BigDecimal("100")).type(TransactionType.DEBIT)
            .account(oldAcc).appUser(currentUser).build();

        when(accountTransactionRepository.findById("tx1")).thenReturn(Optional.of(existing));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "tx1")).thenReturn(List.of());
        when(accountRepository.findByAppUserAndId(currentUser, "a2")).thenReturn(Optional.of(newAcc));
        Category foodCat4 = new Category();
        foodCat4.setId("cat");
        foodCat4.setName("Food");
        foodCat4.setAppUser(currentUser);
        when(categoryService.getCategoryById("cat")).thenReturn(foodCat4);

        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenReturn(new AccountTransaction());
        when(accountTransactionMapper.toDTO(any(AccountTransaction.class))).thenReturn(new AccountTransactionDTO("tx1", LocalDateTime.now(), new BigDecimal("120"), "d", null, null, null, TransactionType.DEBIT, new AccountDTO("a2","A-2", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null));

        AccountTransactionDTO update = new AccountTransactionDTO("tx1", LocalDateTime.now(), new BigDecimal("120"), "d", null, null, null, TransactionType.DEBIT, new AccountDTO("a2","A-2", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO("cat", "Food", null, false, List.of()), null, List.of(), null, null, null, null, null, null, null, null, null);

        Optional<AccountTransactionDTO> result = service.updateTransaction("tx1", update);

        assertThat(result).isPresent();
        // oldType=DEBIT: oldAcc += 100 => 1100; newType=DEBIT on newAcc: 500 - 120 => 380
        assertThat(oldAcc.getBalance()).isEqualByComparingTo("1100");
        assertThat(newAcc.getBalance()).isEqualByComparingTo("380");
        verify(accountRepository, times(1)).save(oldAcc);
        verify(accountRepository, times(1)).save(newAcc);
    }

    @Test
    void updateTransaction_withChildren_preventsAmountChange() {
        Account acc = createAccount("a1", new BigDecimal("1000"));
        AccountTransaction parent = AccountTransaction.builder()
            .id("p1").amount(BigDecimal.ZERO).type(TransactionType.DEBIT)
            .account(acc).appUser(currentUser).build();
        AccountTransaction child = AccountTransaction.builder()
            .id("c1").amount(new BigDecimal("100")).type(TransactionType.DEBIT)
            .account(acc).appUser(currentUser).parent("p1").build();

        when(accountTransactionRepository.findById("p1")).thenReturn(Optional.of(parent));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "p1")).thenReturn(List.of(child));
        when(accountRepository.findByAppUserAndId(currentUser, "a1")).thenReturn(Optional.of(acc));

        AccountTransactionDTO update = new AccountTransactionDTO(
            "p1", 
            LocalDateTime.now(), 
            new BigDecimal("50"), // trying to change amount
            "Updated description", 
            null, 
            null, 
            null, 
            TransactionType.DEBIT, 
            new AccountDTO("a1","A-1", BigDecimal.ZERO, null, null, null, null, null), 
            new CategoryDTO("cat", "Food", null, false, List.of()), 
            null, 
            List.of(), 
            null, 
            null, 
            null, 
            null, 
            null, 
            null,
            null,
            null,
            null
        );

        assertThatThrownBy(() -> service.updateTransaction("p1", update))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot change amount of a split transaction");
    }

    @Test
    void updateTransaction_withChildren_allowsNonAmountChanges() {
        Account acc = createAccount("a1", new BigDecimal("1000"));
        AccountTransaction parent = AccountTransaction.builder()
            .id("p1").amount(BigDecimal.ZERO).type(TransactionType.DEBIT)
            .description("Original description")
            .account(acc).appUser(currentUser).build();
        AccountTransaction child = AccountTransaction.builder()
            .id("c1").amount(new BigDecimal("100")).type(TransactionType.DEBIT)
            .account(acc).appUser(currentUser).parent("p1").build();

        when(accountTransactionRepository.findById("p1")).thenReturn(Optional.of(parent));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "p1")).thenReturn(List.of(child));
        when(accountRepository.findByAppUserAndId(currentUser, "a1")).thenReturn(Optional.of(acc));
        
        Category newCat = new Category();
        newCat.setId("newcat");
        newCat.setName("NewCategory");
        newCat.setAppUser(currentUser);
        when(categoryService.getCategoryById("newcat")).thenReturn(newCat);

        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenReturn(new AccountTransaction());
        when(accountTransactionMapper.toDTO(any(AccountTransaction.class))).thenReturn(
            new AccountTransactionDTO("p1", LocalDateTime.now(), BigDecimal.ZERO, "Updated description", 
                null, null, null, TransactionType.DEBIT, 
                new AccountDTO("a1","A-1", BigDecimal.ZERO, null, null, null, null, null), 
                new CategoryDTO("newcat", "NewCategory", null, false, List.of()), 
                null, List.of(),             null, null, null, null, null, null, null, null, null)
        );

        AccountTransactionDTO update = new AccountTransactionDTO(
            "p1", 
            LocalDateTime.now(), 
            BigDecimal.ZERO, // same amount - should be allowed
            "Updated description", 
            null, 
            null, 
            null, 
            TransactionType.DEBIT, 
            new AccountDTO("a1","A-1", BigDecimal.ZERO, null, null, null, null, null), 
            new CategoryDTO("newcat", "NewCategory", null, false, List.of()), 
            null, 
            List.of(), 
            null, 
            null, 
            null, 
            null, 
            null, 
            null,
            null,
            null,
            null
        );

        Optional<AccountTransactionDTO> result = service.updateTransaction("p1", update);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("Updated description");
        verify(accountTransactionRepository, times(1)).save(any(AccountTransaction.class));
    }

    @Test
    void save_fromDto_setsDefaultsAndPersists() {
        Account acc = createAccount("a1", new BigDecimal("1000"));
        when(accountRepository.findByAppUserAndId(currentUser, "a1")).thenReturn(Optional.of(acc));

        AccountTransaction mapped = new AccountTransaction();
        mapped.setAccount(acc);
        mapped.setGptAccount(null);
        when(accountTransactionMapper.toEntity(any(AccountTransactionDTO.class))).thenAnswer(inv -> {
            AccountTransactionDTO dto = inv.getArgument(0);
            mapped.setType(dto.type());
            mapped.setAmount(dto.amount());
            mapped.setAccount(acc);
            mapped.setGptAccount(null);
            return mapped;
        });
        when(accountTransactionRepository.save(mapped)).thenReturn(mapped);
        when(accountTransactionMapper.toDTO(mapped)).thenReturn(new AccountTransactionDTO("txN", LocalDateTime.now(), new BigDecimal("10"), "d", null, null, null, TransactionType.DEBIT, new AccountDTO("a1","A-1", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null));

        AccountTransactionDTO input = new AccountTransactionDTO(null, LocalDateTime.now(), new BigDecimal("10"), "d", null, null, null, TransactionType.DEBIT, new AccountDTO("a1","A-1", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null);

        AccountTransactionDTO result = service.save(input);
        assertThat(result.id()).isEqualTo("txN");
        // gptAccount should be set same as account when null
        assertThat(mapped.getGptAccount()).isEqualTo(acc);
    }

    @Test
    void getById_mapsToDto() {
        AccountTransaction entity = AccountTransaction.builder().id("t1").appUser(currentUser).build();
        AccountTransactionDTO dto = new AccountTransactionDTO("t1", LocalDateTime.now(), BigDecimal.ZERO, null, null, null, null, TransactionType.DEBIT, null, null, null, List.of(), null, null, null, null, null, null, null, null, null);
        when(accountTransactionRepository.findByAppUserAndId(currentUser, "t1")).thenReturn(Optional.of(entity));
        when(accountTransactionMapper.toDTO(entity)).thenReturn(dto);

        Optional<AccountTransactionDTO> result = service.getById("t1");
        assertThat(result).contains(dto);
    }

    @Test
    void getChildren_mapsToDtos() {
        AccountTransaction e1 = AccountTransaction.builder().id("c1").appUser(currentUser).build();
        AccountTransaction e2 = AccountTransaction.builder().id("c2").appUser(currentUser).build();
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "p1")).thenReturn(List.of(e1, e2));
        AccountTransactionDTO d1 = new AccountTransactionDTO("c1", LocalDateTime.now(), BigDecimal.ZERO, null, null, null, null, TransactionType.DEBIT, null, null, null, List.of(), null, null, null, null, null, null, null, null, null);
        AccountTransactionDTO d2 = new AccountTransactionDTO("c2", LocalDateTime.now(), BigDecimal.ZERO, null, null, null, null, TransactionType.DEBIT, null, null, null, List.of(), null, null, null, null, null, null, null, null, null);
        when(accountTransactionMapper.toDTOList(List.of(e1, e2))).thenReturn(List.of(d1, d2));

        List<AccountTransactionDTO> result = service.getChildren("p1");
        assertThat(result).containsExactly(d1, d2);
    }

    @Test
    void delete_throwsWhenHasChildren() {
        Account acc = createAccount("a1", new BigDecimal("1000"));
        AccountTransaction existing = AccountTransaction.builder()
            .id("t1")
            .account(acc)
            .appUser(currentUser)
            .build();
        when(accountTransactionRepository.findByAppUserAndId(currentUser, "t1")).thenReturn(Optional.of(existing));
        when(accountTransactionRepository.findByParentAndAppUser("t1", currentUser)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.delete("t1")).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("children");
    }

    @Test
    void delete_updatesParentAndDeletes() {
        Account acc = createAccount("a1", new BigDecimal("1000"));
        AccountTransaction parent = AccountTransaction.builder()
            .id("p1")
            .amount(new BigDecimal("200"))
            .account(acc)
            .appUser(currentUser)
            .description("Parent")
            .build();
        AccountTransaction child = AccountTransaction.builder()
            .id("t1")
            .amount(new BigDecimal("50"))
            .parent("p1")
            .account(acc)
            .appUser(currentUser)
            .description("Child")
            .build();
        when(accountTransactionRepository.findByAppUserAndId(currentUser, "t1")).thenReturn(Optional.of(child));
        when(accountTransactionRepository.findByAppUserAndId(currentUser, "p1")).thenReturn(Optional.of(parent));
        when(accountTransactionRepository.findByParentAndAppUser("t1", currentUser)).thenReturn(List.of());

        service.delete("t1");

        assertThat(parent.getAmount()).isEqualByComparingTo("250");
        verify(accountTransactionRepository).save(parent);
        verify(accountTransactionRepository).deleteByAppUserAndId(currentUser, "t1");
    }

    // ============ Transfer Bidirectional Linking Tests ============

    @Test
    void createTransfer_setsBidirectionalLinks() throws Exception {
        // Setup source and destination accounts
        AccountType savingsType = new AccountType();
        savingsType.setId("savings");
        savingsType.setName("Savings");
        
        Institution bank = new Institution();
        bank.setId("bank1");
        bank.setName("Test Bank");
        
        Account from = new Account();
        from.setId("a1");
        from.setBalance(new BigDecimal("1000"));
        from.setAccountType(savingsType);
        from.setInstitution(bank);
        
        Account to = new Account();
        to.setId("a2");
        to.setBalance(new BigDecimal("500"));
        to.setAccountType(savingsType);
        to.setInstitution(bank);

        // Setup debit transaction
        AccountTransaction debit = AccountTransaction.builder()
            .id("debit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.DEBIT)
            .account(from)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer to Account 2")
            .currency("INR")
            .build();

        // Mock category service for transfer category
        Category transferCat = new Category();
        transferCat.setId("transfer");
        transferCat.setName("Transfer");
        when(categoryService.getTransferCategory()).thenReturn(transferCat);

        // Mock repository behavior
        when(accountTransactionRepository.findByAppUserAndId(currentUser, "debit1")).thenReturn(Optional.of(debit));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "debit1")).thenReturn(List.of());
        when(accountRepository.findByAppUserAndId(currentUser, "a2")).thenReturn(Optional.of(to));

        // Capture the saved credit transaction
        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenAnswer(invocation -> {
            AccountTransaction tx = invocation.getArgument(0);
            if (tx.getId() == null) {
                tx.setId("credit1"); // Simulate ID generation
            }
            return tx;
        });

        // Execute transfer
        TransferRequest request = TransferRequest.builder()
            .sourceTransactionId("debit1")
            .destinationAccountId("a2")
            .explanation("Monthly savings")
            .build();

        service.createTransfer(request);

        // Verify both transactions were saved with linked IDs
        verify(accountTransactionRepository, atLeast(2)).save(argThat(tx -> 
            tx.getLinkedTransferId() != null
        ));
        
        // Verify accounts were updated
        assertThat(from.getBalance()).isEqualByComparingTo("800");
        assertThat(to.getBalance()).isEqualByComparingTo("700");
        verify(accountRepository, times(2)).save(any(Account.class));
    }

    @Test
    void updateTransaction_syncsLinkedTransfer() {
        // Setup linked transfer pair
        AccountType savingsType = new AccountType();
        savingsType.setId("savings");
        savingsType.setName("Savings");
        
        Institution bank = new Institution();
        bank.setId("bank1");
        bank.setName("Test Bank");
        
        Account account1 = new Account();
        account1.setId("a1");
        account1.setBalance(new BigDecimal("1000"));
        account1.setAccountType(savingsType);
        account1.setInstitution(bank);
        
        Account account2 = new Account();
        account2.setId("a2");
        account2.setBalance(new BigDecimal("500"));
        account2.setAccountType(savingsType);
        account2.setInstitution(bank);

        Category transferCat = new Category();
        transferCat.setId("transfer");
        transferCat.setName("Transfer");

        AccountTransaction debit = AccountTransaction.builder()
            .id("debit1")
            .linkedTransferId("credit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.DEBIT)
            .account(account1)
            .category(transferCat)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer")
            .currency("INR")
            .build();

        AccountTransaction credit = AccountTransaction.builder()
            .id("credit1")
            .linkedTransferId("debit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.CREDIT)
            .account(account2)
            .category(transferCat)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer")
            .currency("INR")
            .build();

        // Setup DTO for update with new amount
        CategoryDTO catDTO = new CategoryDTO();
        catDTO.setId("transfer");
        catDTO.setName("Transfer");
        AccountDTO accDTO = new AccountDTO("a1", "Account 1", BigDecimal.ZERO, null, null, null, null, null);

        AccountTransactionDTO updateDTO = new AccountTransactionDTO(
            "debit1",
            LocalDateTime.now(),
            new BigDecimal("300"), // Changed amount
            "Updated Transfer",
            null,
            null,
            null,
            TransactionType.DEBIT,
            accDTO,
            catDTO,
            null,
            List.of(),
            "credit1",
            null,
            null,
            null,
            null,
            "INR",
            null,
            null,
            null
        );

        // Mock repository calls - Case 1: debit has linkedTransferId pointing to credit
        when(accountTransactionRepository.findById("debit1")).thenReturn(Optional.of(debit));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "debit1")).thenReturn(List.of());
        when(accountTransactionRepository.findByAppUserAndId(currentUser, "credit1")).thenReturn(Optional.of(credit));
        when(accountRepository.findByAppUserAndId(currentUser, "a1")).thenReturn(Optional.of(account1));
        when(categoryService.getCategoryById("transfer")).thenReturn(transferCat);
        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(accountTransactionMapper.toDTO(any(AccountTransaction.class))).thenReturn(updateDTO);

        // Execute update
        Optional<AccountTransactionDTO> result = service.updateTransaction("debit1", updateDTO);

        // Verify linked transaction was also updated
        assertThat(result).isPresent();
        verify(accountTransactionRepository, atLeast(2)).save(any(AccountTransaction.class));
        assertThat(credit.getAmount()).isEqualByComparingTo("300");
        assertThat(credit.getDescription()).isEqualTo("Updated Transfer");
        
        // Verify balances are updated correctly:
        // Main transaction (debit): oldAmount=200 DEBIT reversed => +200, newAmount=300 DEBIT => -300, net: -100 => account1: 900
        // Linked transaction (credit): oldAmount=200 CREDIT reversed => -200, newAmount=300 CREDIT => +300, net: +100 => account2: 600
        assertThat(account1.getBalance()).isEqualByComparingTo("900");  // 1000 + 200 - 300
        assertThat(account2.getBalance()).isEqualByComparingTo("600");  // 500 - 200 + 300
        verify(accountRepository, times(1)).save(account1); // Main transaction account
        verify(accountRepository, times(1)).save(account2); // Linked transaction account
    }

    @Test
    void updateTransaction_linkedTransfer_withAccountChange() {
        // Setup linked transfer pair where main transaction account will change
        Account account1 = new Account();
        account1.setId("a1");
        account1.setBalance(new BigDecimal("1000"));
        account1.setAppUser(currentUser);
        
        Account account2 = new Account();
        account2.setId("a2");
        account2.setBalance(new BigDecimal("500"));
        account2.setAppUser(currentUser);
        
        Account account3 = new Account();
        account3.setId("a3");
        account3.setBalance(new BigDecimal("2000"));
        account3.setAppUser(currentUser);

        Category transferCat = new Category();
        transferCat.setId("transfer");
        transferCat.setName("Transfer");

        AccountTransaction debit = AccountTransaction.builder()
            .id("debit1")
            .linkedTransferId("credit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.DEBIT)
            .account(account1)
            .category(transferCat)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer")
            .currency("INR")
            .build();

        AccountTransaction credit = AccountTransaction.builder()
            .id("credit1")
            .linkedTransferId("debit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.CREDIT)
            .account(account2)
            .category(transferCat)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer")
            .currency("INR")
            .build();

        // Setup DTO for update: change main transaction's account from a1 to a3
        CategoryDTO catDTO = new CategoryDTO();
        catDTO.setId("transfer");
        catDTO.setName("Transfer");
        AccountDTO accDTO3 = new AccountDTO("a3", "Account 3", BigDecimal.ZERO, null, null, null, null, null);

        AccountTransactionDTO updateDTO = new AccountTransactionDTO(
            "debit1",
            LocalDateTime.now(),
            new BigDecimal("250"), // Changed amount
            "Updated Transfer",
            null,
            null,
            null,
            TransactionType.DEBIT,
            accDTO3, // Changed account from a1 to a3
            catDTO,
            null,
            List.of(),
            "credit1",
            null,
            null,
            null,
            null,
            "INR",
            null,
            null,
            null
        );

        // Mock repository calls - Case 1: debit has linkedTransferId pointing to credit
        when(accountTransactionRepository.findById("debit1")).thenReturn(Optional.of(debit));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "debit1")).thenReturn(List.of());
        when(accountTransactionRepository.findByAppUserAndId(currentUser, "credit1")).thenReturn(Optional.of(credit));
        when(accountRepository.findByAppUserAndId(currentUser, "a3")).thenReturn(Optional.of(account3));
        when(categoryService.getCategoryById("transfer")).thenReturn(transferCat);
        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenAnswer(i -> i.getArgument(0));
        
        when(accountTransactionMapper.toDTO(any(AccountTransaction.class))).thenReturn(updateDTO);

        // Execute update
        Optional<AccountTransactionDTO> result = service.updateTransaction("debit1", updateDTO);

        // Verify balances are updated correctly:
        // Main transaction old account (a1): oldAmount=200 DEBIT reversed => +200 => 1200
        // Main transaction new account (a3): newAmount=250 DEBIT => -250 => 1750
        // Linked transaction account (a2): oldAmount=200 CREDIT reversed => -200, newAmount=250 CREDIT => +250, net: +50 => 550
        assertThat(result).isPresent();
        assertThat(account1.getBalance()).isEqualByComparingTo("1200");  // 1000 + 200 (reversed)
        assertThat(account3.getBalance()).isEqualByComparingTo("1750"); // 2000 - 250 (new debit)
        assertThat(account2.getBalance()).isEqualByComparingTo("550");  // 500 - 200 + 250 (linked transaction updated)
        verify(accountRepository, times(1)).save(account1); // Old account
        verify(accountRepository, times(1)).save(account3); // New account
        verify(accountRepository, times(1)).save(account2); // Linked transaction account
        verify(accountTransactionRepository, atLeast(2)).save(any(AccountTransaction.class));
        
        // Verify linked transaction was synced
        assertThat(credit.getAmount()).isEqualByComparingTo("250");
        assertThat(credit.getDescription()).isEqualTo("Updated Transfer");
        assertThat(credit.getAccount().getId()).isEqualTo("a2"); // Credit account stays the same (correct for transfers)
    }
    
    @Test
    void updateTransaction_linkedTransfer_creditAccountChange() {
        // Test updating credit account in a linked transfer
        Account account1 = new Account();
        account1.setId("a1");
        account1.setBalance(new BigDecimal("1000"));
        account1.setAppUser(currentUser);
        
        Account account2 = new Account();
        account2.setId("a2");
        account2.setBalance(new BigDecimal("500"));
        account2.setAppUser(currentUser);
        
        Account account4 = new Account();
        account4.setId("a4");
        account4.setBalance(new BigDecimal("3000"));
        account4.setAppUser(currentUser);

        Category transferCat = new Category();
        transferCat.setId("transfer");
        transferCat.setName("Transfer");

        AccountTransaction debit = AccountTransaction.builder()
            .id("debit1")
            .linkedTransferId("credit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.DEBIT)
            .account(account1)
            .category(transferCat)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer")
            .currency("INR")
            .build();

        AccountTransaction credit = AccountTransaction.builder()
            .id("credit1")
            .linkedTransferId("debit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.CREDIT)
            .account(account2)
            .category(transferCat)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer")
            .currency("INR")
            .build();

        // Setup DTO: updating credit transaction, changing its account from a2 to a4
        CategoryDTO catDTO = new CategoryDTO();
        catDTO.setId("transfer");
        catDTO.setName("Transfer");
        AccountDTO accDTO4 = new AccountDTO("a4", "Account 4", BigDecimal.ZERO, null, null, null, null, null);

        AccountTransactionDTO updateDTO = new AccountTransactionDTO(
            "credit1",
            LocalDateTime.now(),
            new BigDecimal("250"),
            "Updated Transfer Destination",
            null, null, null,
            TransactionType.CREDIT,
            accDTO4, // Changed credit account from a2 to a4
            catDTO,
            null, List.of(),
            "debit1",
            null, null, null, null,
            "INR",
            null,
            null,
            null
        );

        // Mock repository calls - Case 2: credit is referenced by debit's linkedTransferId
        when(accountTransactionRepository.findById("credit1")).thenReturn(Optional.of(credit));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "credit1")).thenReturn(List.of());
        when(accountTransactionRepository.findByAppUserAndLinkedTransferId(currentUser, "credit1")).thenReturn(Optional.of(debit));
        when(accountRepository.findByAppUserAndId(currentUser, "a4")).thenReturn(Optional.of(account4));
        when(categoryService.getCategoryById("transfer")).thenReturn(transferCat);
        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenAnswer(i -> i.getArgument(0));
        
        when(accountTransactionMapper.toDTO(any(AccountTransaction.class))).thenReturn(updateDTO);

        // Execute update
        Optional<AccountTransactionDTO> result = service.updateTransaction("credit1", updateDTO);

        // Verify balances:
        // Main transaction (credit) old account (a2): oldAmount=200 CREDIT reversed => -200 => 300
        // Main transaction (credit) new account (a4): newAmount=250 CREDIT => +250 => 3250
        // Linked transaction (debit) account (a1): oldAmount=200 DEBIT reversed => +200, newAmount=250 DEBIT => -250, net: -50 => 950
        assertThat(result).isPresent();
        assertThat(account2.getBalance()).isEqualByComparingTo("300");  // 500 - 200 (reversed)
        assertThat(account4.getBalance()).isEqualByComparingTo("3250"); // 3000 + 250 (new credit)
        assertThat(account1.getBalance()).isEqualByComparingTo("950");  // 1000 + 200 - 250 (linked debit updated)
        verify(accountRepository, times(1)).save(account1); // Linked debit account
        verify(accountRepository, times(1)).save(account2); // Credit old account
        verify(accountRepository, times(1)).save(account4); // Credit new account
        
        // Verify linked transaction was synced
        assertThat(debit.getAmount()).isEqualByComparingTo("250");
        assertThat(debit.getDescription()).isEqualTo("Updated Transfer Destination");
        assertThat(debit.getAccount().getId()).isEqualTo("a1"); // Debit account stays the same (correct for transfers)
    }

    @Test
    void updateTransaction_preventsTransferCategoryChange() {
        // Setup transfer transaction
        AccountType savingsType = new AccountType();
        savingsType.setId("savings");
        savingsType.setName("Savings");
        
        Institution bank = new Institution();
        bank.setId("bank1");
        bank.setName("Test Bank");
        
        Account account1 = new Account();
        account1.setId("a1");
        account1.setBalance(new BigDecimal("1000"));
        account1.setAccountType(savingsType);
        account1.setInstitution(bank);

        Category transferCat = new Category();
        transferCat.setId("transfer");
        transferCat.setName("Transfer");
        
        Category foodCat = new Category();
        foodCat.setId("food");
        foodCat.setName("Food");

        AccountTransaction debit = AccountTransaction.builder()
            .id("debit1")
            .linkedTransferId("credit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.DEBIT)
            .account(account1)
            .category(transferCat)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer")
            .currency("INR")
            .build();

        AccountTransaction credit = AccountTransaction.builder()
            .id("credit1")
            .linkedTransferId("debit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.CREDIT)
            .account(account1)
            .category(transferCat)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer")
            .currency("INR")
            .build();

        // Setup DTO attempting to change category
        CategoryDTO foodDTO = new CategoryDTO();
        foodDTO.setId("food");
        foodDTO.setName("Food");
        AccountDTO accDTO = new AccountDTO("a1", "Account 1", BigDecimal.ZERO, null, null, null, null, null);

        AccountTransactionDTO updateDTO = new AccountTransactionDTO(
            "debit1",
            LocalDateTime.now(),
            new BigDecimal("200"),
            "Transfer",
            null,
            null,
            null,
            TransactionType.DEBIT,
            accDTO,
            foodDTO, // Attempting to change category
            null,
            List.of(),
            "credit1",
            null,
            null,
            null,
            null,
            "INR",
            null,
            null,
            null
        );

        // Mock repository calls
        when(accountTransactionRepository.findById("debit1")).thenReturn(Optional.of(debit));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "debit1")).thenReturn(List.of());
        when(accountTransactionRepository.findByAppUserAndLinkedTransferId(currentUser, "debit1")).thenReturn(Optional.of(credit));
        when(accountRepository.findByAppUserAndId(currentUser, "a1")).thenReturn(Optional.of(account1));

        // Verify exception is thrown
        assertThatThrownBy(() -> service.updateTransaction("debit1", updateDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot change category of a transfer transaction");
    }

    @Test
    void deleteTransaction_deletesLinkedTransfer() {
        // Setup linked transfer pair
        Account account1 = new Account();
        account1.setId("a1");
        account1.setBalance(new BigDecimal("1000"));
        account1.setAppUser(currentUser);
        
        Account account2 = new Account();
        account2.setId("a2");
        account2.setBalance(new BigDecimal("500"));
        account2.setAppUser(currentUser);

        AccountTransaction debit = AccountTransaction.builder()
            .id("debit1")
            .linkedTransferId("credit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.DEBIT)
            .account(account1)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer")
            .build();

        AccountTransaction credit = AccountTransaction.builder()
            .id("credit1")
            .linkedTransferId("debit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.CREDIT)
            .account(account2)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer")
            .build();

        // Mock repository calls - Case 1: debit has linkedTransferId pointing to credit
        when(accountTransactionRepository.findByAppUserAndId(currentUser, "debit1")).thenReturn(Optional.of(debit));
        when(accountTransactionRepository.findByAppUserAndId(currentUser, "credit1")).thenReturn(Optional.of(credit));
        when(accountTransactionRepository.findByParentAndAppUser("debit1", currentUser)).thenReturn(List.of());

        // Execute delete
        service.delete("debit1");

        // Verify balances are reversed: DEBIT refunds to account1, CREDIT deducts from account2
        assertThat(account1.getBalance()).isEqualByComparingTo("1200"); // 1000 + 200
        assertThat(account2.getBalance()).isEqualByComparingTo("300");  // 500 - 200
        verify(accountRepository, times(1)).save(account1);
        verify(accountRepository, times(1)).save(account2);
        
        // Verify both transactions were deleted
        verify(accountTransactionRepository).deleteByAppUserAndId(currentUser, "credit1");
        verify(accountTransactionRepository).deleteByAppUserAndId(currentUser, "debit1");
    }
    
    @Test
    void deleteTransaction_deletesLinkedTransfer_ReverseCase() {
        // Test Case 2: Delete the credit transaction (which is referenced by debit's linkedTransferId)
        Account account1 = new Account();
        account1.setId("a1");
        account1.setBalance(new BigDecimal("1000"));
        account1.setAppUser(currentUser);
        
        Account account2 = new Account();
        account2.setId("a2");
        account2.setBalance(new BigDecimal("500"));
        account2.setAppUser(currentUser);

        AccountTransaction debit = AccountTransaction.builder()
            .id("debit1")
            .linkedTransferId("credit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.DEBIT)
            .account(account1)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer")
            .build();

        AccountTransaction credit = AccountTransaction.builder()
            .id("credit1")
            .linkedTransferId("debit1")
            .amount(new BigDecimal("200"))
            .type(TransactionType.CREDIT)
            .account(account2)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Transfer")
            .build();

        // Mock repository calls - Case 2: credit doesn't have linkedTransferId, but debit points to it
        when(accountTransactionRepository.findByAppUserAndId(currentUser, "credit1")).thenReturn(Optional.of(credit));
        when(accountTransactionRepository.findByAppUserAndLinkedTransferId(currentUser, "credit1")).thenReturn(Optional.of(debit));
        when(accountTransactionRepository.findByParentAndAppUser("credit1", currentUser)).thenReturn(List.of());

        // Execute delete on credit transaction
        service.delete("credit1");

        // Verify balances are reversed
        assertThat(account1.getBalance()).isEqualByComparingTo("1200"); // 1000 + 200 (debit reversed)
        assertThat(account2.getBalance()).isEqualByComparingTo("300");  // 500 - 200 (credit reversed)
        verify(accountRepository, times(1)).save(account1);
        verify(accountRepository, times(1)).save(account2);
        
        // Verify both transactions were deleted
        verify(accountTransactionRepository).deleteByAppUserAndId(currentUser, "debit1");
        verify(accountTransactionRepository).deleteByAppUserAndId(currentUser, "credit1");
    }

    @Test
    void deleteTransaction_withoutLinkedTransfer_deletesOnlyOne() {
        // Setup regular transaction without linked transfer
        Account account1 = new Account();
        account1.setId("a1");
        account1.setBalance(new BigDecimal("1000"));
        account1.setAppUser(currentUser);

        AccountTransaction tx = AccountTransaction.builder()
            .id("tx1")
            .linkedTransferId(null) // No linked transfer
            .amount(new BigDecimal("50"))
            .type(TransactionType.DEBIT)
            .account(account1)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Regular expense")
            .build();

        // Mock repository calls
        when(accountTransactionRepository.findByAppUserAndId(currentUser, "tx1")).thenReturn(Optional.of(tx));
        when(accountTransactionRepository.findByParentAndAppUser("tx1", currentUser)).thenReturn(List.of());

        // Execute delete
        service.delete("tx1");

        // Verify balance is reversed: DEBIT refunds 50 => 1050
        assertThat(account1.getBalance()).isEqualByComparingTo("1050");
        verify(accountRepository, times(1)).save(account1);
        
        // Verify only one transaction was deleted
        verify(accountTransactionRepository, times(1)).deleteByAppUserAndId(any(AppUser.class), anyString());
        verify(accountTransactionRepository).deleteByAppUserAndId(currentUser, "tx1");
    }
    
    @Test
    void deleteTransaction_credit_reversesBalance() {
        // Setup CREDIT transaction
        Account account1 = new Account();
        account1.setId("a1");
        account1.setBalance(new BigDecimal("1000"));
        account1.setAppUser(currentUser);

        AccountTransaction tx = AccountTransaction.builder()
            .id("tx1")
            .linkedTransferId(null)
            .amount(new BigDecimal("75"))
            .type(TransactionType.CREDIT)
            .account(account1)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Income")
            .build();

        // Mock repository calls
        when(accountTransactionRepository.findByAppUserAndId(currentUser, "tx1")).thenReturn(Optional.of(tx));
        when(accountTransactionRepository.findByParentAndAppUser("tx1", currentUser)).thenReturn(List.of());

        // Execute delete
        service.delete("tx1");

        // Verify balance is reversed: CREDIT deducts 75 => 925
        assertThat(account1.getBalance()).isEqualByComparingTo("925");
        verify(accountRepository, times(1)).save(account1);
        verify(accountTransactionRepository).deleteByAppUserAndId(currentUser, "tx1");
    }
    
    @Test
    void save_debit_updatesAccountBalance() {
        Account acc = createAccount("a1", new BigDecimal("1000"));
        when(accountRepository.findByAppUserAndId(currentUser, "a1")).thenReturn(Optional.of(acc));

        AccountTransaction transaction = new AccountTransaction();
        transaction.setAccount(acc);
        transaction.setType(TransactionType.DEBIT);
        transaction.setAmount(new BigDecimal("150"));
        transaction.setGptAccount(null);
        
        when(accountTransactionMapper.toEntity(any(AccountTransactionDTO.class))).thenAnswer(inv -> {
            AccountTransactionDTO dto = inv.getArgument(0);
            transaction.setType(dto.type());
            transaction.setAmount(dto.amount());
            transaction.setAccount(acc);
            transaction.setGptAccount(null);
            return transaction;
        });
        when(accountTransactionRepository.save(transaction)).thenReturn(transaction);
        when(accountTransactionMapper.toDTO(transaction)).thenReturn(
            new AccountTransactionDTO("tx1", LocalDateTime.now(), new BigDecimal("150"), "d", null, null, null, 
                TransactionType.DEBIT, new AccountDTO("a1","A-1", BigDecimal.ZERO, null, null, null, null, null), 
                new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null));

        AccountTransactionDTO input = new AccountTransactionDTO(null, LocalDateTime.now(), new BigDecimal("150"), "d", null, null, null, 
            TransactionType.DEBIT, new AccountDTO("a1","A-1", BigDecimal.ZERO, null, null, null, null, null), 
            new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null);

        AccountTransactionDTO result = service.save(input);
        
        // Verify balance is updated: DEBIT subtracts 150 => 850
        assertThat(acc.getBalance()).isEqualByComparingTo("850");
        verify(accountRepository, times(1)).save(acc);
        assertThat(result.id()).isEqualTo("tx1");
    }
    
    @Test
    void save_credit_updatesAccountBalance() {
        Account acc = createAccount("a1", new BigDecimal("1000"));
        when(accountRepository.findByAppUserAndId(currentUser, "a1")).thenReturn(Optional.of(acc));

        AccountTransaction transaction = new AccountTransaction();
        transaction.setAccount(acc);
        transaction.setType(TransactionType.CREDIT);
        transaction.setAmount(new BigDecimal("250"));
        transaction.setGptAccount(null);
        
        when(accountTransactionMapper.toEntity(any(AccountTransactionDTO.class))).thenAnswer(inv -> {
            AccountTransactionDTO dto = inv.getArgument(0);
            transaction.setType(dto.type());
            transaction.setAmount(dto.amount());
            transaction.setAccount(acc);
            transaction.setGptAccount(null);
            return transaction;
        });
        when(accountTransactionRepository.save(transaction)).thenReturn(transaction);
        when(accountTransactionMapper.toDTO(transaction)).thenReturn(
            new AccountTransactionDTO("tx1", LocalDateTime.now(), new BigDecimal("250"), "d", null, null, null, 
                TransactionType.CREDIT, new AccountDTO("a1","A-1", BigDecimal.ZERO, null, null, null, null, null), 
                new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null));

        AccountTransactionDTO input = new AccountTransactionDTO(null, LocalDateTime.now(), new BigDecimal("250"), "d", null, null, null, 
            TransactionType.CREDIT, new AccountDTO("a1","A-1", BigDecimal.ZERO, null, null, null, null, null), 
            new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null);

        AccountTransactionDTO result = service.save(input);
        
        // Verify balance is updated: CREDIT adds 250 => 1250
        assertThat(acc.getBalance()).isEqualByComparingTo("1250");
        verify(accountRepository, times(1)).save(acc);
        assertThat(result.id()).isEqualTo("tx1");
    }

    @Test
    void updateTransaction_preventsChildAmountChange() {
        // Setup category and account
        Category category = new Category();
        category.setId("cat1");
        category.setName("Test Category");
        category.setAppUser(currentUser);

        Account account = createAccount("acc1", BigDecimal.valueOf(1000.00));

        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId("cat1");
        categoryDTO.setName("Test Category");
        
        AccountDTO accountDTO = new AccountDTO("acc1", "A-acc1", BigDecimal.valueOf(1000.00), null, null, null, null, null);

        // Setup child transaction (parent reference is just an ID)
        AccountTransaction child = AccountTransaction.builder()
            .id("child1")
            .amount(BigDecimal.valueOf(50.00))
            .type(TransactionType.DEBIT)
            .category(category)
            .account(account)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Child expense")
            .build();
        child.setParent("parent1");

        // Mock repository calls
        when(accountTransactionRepository.findById("child1")).thenReturn(Optional.of(child));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "child1")).thenReturn(List.of());
        when(accountRepository.findByAppUserAndId(currentUser, "acc1")).thenReturn(Optional.of(account));

        // Create update DTO with different amount (matching the record field order)
        AccountTransactionDTO updateDTO = new AccountTransactionDTO(
            "child1",                       // id
            LocalDateTime.now(),            // date
            BigDecimal.valueOf(75.00),      // amount - Changed amount
            "Updated child",                // description
            null,                           // shortDescription
            null,                           // explanation
            null,                           // shortExplanation
            TransactionType.DEBIT,          // type
            accountDTO,                     // account
            categoryDTO,                    // category
            "parent1",                      // parentId
            List.of(),                      // children
            null,                           // linkedTransferId
            null,                           // gptAmount
            null,                           // gptDescription
            null,                           // gptExplanation
            null,                           // gptType
            "INR",                          // currency
            null,                           // gptAccount
            null,                           // gptCurrency
            null                            // labels
        );

        // Execute and verify exception is thrown using assertThatThrownBy
        assertThatThrownBy(() -> service.updateTransaction("child1", updateDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot change amount of a child transaction");
    }

    @Test
    void updateTransaction_preventsChildAccountChange() {
        // Setup category and accounts
        Category category = new Category();
        category.setId("cat1");
        category.setName("Test Category");
        category.setAppUser(currentUser);

        Account account1 = createAccount("acc1", BigDecimal.valueOf(1000.00));
        Account account2 = createAccount("acc2", BigDecimal.valueOf(2000.00));

        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId("cat1");
        categoryDTO.setName("Test Category");
        
        AccountDTO accountDTO2 = new AccountDTO("acc2", "A-acc2", BigDecimal.valueOf(2000.00), null, null, null, null, null);

        // Setup child transaction (parent reference is just an ID)
        AccountTransaction child = AccountTransaction.builder()
            .id("child1")
            .amount(BigDecimal.valueOf(50.00))
            .type(TransactionType.DEBIT)
            .category(category)
            .account(account1)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Child expense")
            .build();
        child.setParent("parent1");

        // Mock repository calls
        when(accountTransactionRepository.findById("child1")).thenReturn(Optional.of(child));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "child1")).thenReturn(List.of());
        when(accountRepository.findByAppUserAndId(currentUser, "acc2")).thenReturn(Optional.of(account2));

        // Create update DTO with different account
        AccountTransactionDTO updateDTO = new AccountTransactionDTO(
            "child1",                       // id
            LocalDateTime.now(),            // date
            BigDecimal.valueOf(50.00),      // amount - Same amount
            "Updated child",                // description
            null,                           // shortDescription
            null,                           // explanation
            null,                           // shortExplanation
            TransactionType.DEBIT,          // type
            accountDTO2,                    // account - Changed account
            categoryDTO,                    // category
            "parent1",                      // parentId
            List.of(),                      // children
            null,                           // linkedTransferId
            null,                           // gptAmount
            null,                           // gptDescription
            null,                           // gptExplanation
            null,                           // gptType
            "INR",                          // currency
            null,                           // gptAccount
            null,                           // gptCurrency
            null                            // labels
        );

        // Execute and verify exception is thrown
        assertThatThrownBy(() -> service.updateTransaction("child1", updateDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot change account of a child transaction");
    }

    @Test
    void updateTransaction_preventsChildTypeChange() {
        // Setup category and account
        Category category = new Category();
        category.setId("cat1");
        category.setName("Test Category");
        category.setAppUser(currentUser);

        Account account = createAccount("acc1", BigDecimal.valueOf(1000.00));

        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId("cat1");
        categoryDTO.setName("Test Category");
        
        AccountDTO accountDTO = new AccountDTO("acc1", "A-acc1", BigDecimal.valueOf(1000.00), null, null, null, null, null);

        // Setup child transaction (parent reference is just an ID)
        AccountTransaction child = AccountTransaction.builder()
            .id("child1")
            .amount(BigDecimal.valueOf(50.00))
            .type(TransactionType.DEBIT)
            .category(category)
            .account(account)
            .appUser(currentUser)
            .date(LocalDateTime.now())
            .description("Child expense")
            .build();
        child.setParent("parent1");

        // Mock repository calls
        when(accountTransactionRepository.findById("child1")).thenReturn(Optional.of(child));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "child1")).thenReturn(List.of());
        when(accountRepository.findByAppUserAndId(currentUser, "acc1")).thenReturn(Optional.of(account));

        // Create update DTO with different type
        AccountTransactionDTO updateDTO = new AccountTransactionDTO(
            "child1",                       // id
            LocalDateTime.now(),            // date
            BigDecimal.valueOf(50.00),      // amount - Same amount
            "Updated child",                // description
            null,                           // shortDescription
            null,                           // explanation
            null,                           // shortExplanation
            TransactionType.CREDIT,         // type - Changed type
            accountDTO,                     // account
            categoryDTO,                    // category
            "parent1",                      // parentId
            List.of(),                      // children
            null,                           // linkedTransferId
            null,                           // gptAmount
            null,                           // gptDescription
            null,                           // gptExplanation
            null,                           // gptType
            "INR",                          // currency
            null,                           // gptAccount
            null,                           // gptCurrency
            null                            // labels
        );

        // Execute and verify exception is thrown
        assertThatThrownBy(() -> service.updateTransaction("child1", updateDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot change transaction type of a child transaction");
    }

    @Test
    void save_withLabels_createsLabelsAndAssociates() {
        Account acc = createAccount("a1", new BigDecimal("1000"));
        Category cat = new Category();
        cat.setId("cat");
        cat.setName("Food");
        cat.setAppUser(currentUser);

        when(accountRepository.findByAppUserAndId(currentUser, "a1")).thenReturn(Optional.of(acc));
        lenient().when(categoryService.getCategoryById("cat")).thenReturn(cat);

        Label label1 = new Label();
        label1.setId("l1");
        label1.setName("Food");
        label1.setAppUser(currentUser);

        Label label2 = new Label();
        label2.setId("l2");
        label2.setName("Personal");
        label2.setAppUser(currentUser);

        when(labelService.findOrCreateLabel(currentUser, "Food")).thenReturn(label1);
        when(labelService.findOrCreateLabel(currentUser, "Personal")).thenReturn(label2);

        AccountTransactionDTO input = new AccountTransactionDTO(
            null,
            LocalDateTime.now(),
            new BigDecimal("100"),
            "Test transaction",
            null,
            null,
            null,
            TransactionType.DEBIT,
            new AccountDTO("a1", "Account 1", BigDecimal.ZERO, null, null, null, null, null),
            new CategoryDTO("cat", "Food", null, false, List.of()),
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            "INR",
            null,
            null,
            List.of(new LabelDTO("l1", "Food"), new LabelDTO("l2", "Personal"))
        );

        AccountTransaction entity = new AccountTransaction();
        entity.setId("tx1");
        entity.setAmount(new BigDecimal("100"));
        entity.setType(TransactionType.DEBIT);
        entity.setAccount(acc);
        entity.setCategory(cat);
        entity.setAppUser(currentUser);
        entity.setLabels(List.of(label1, label2), currentUser);

        when(accountTransactionMapper.toEntity(input)).thenReturn(entity);
        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenAnswer(invocation -> {
            AccountTransaction tx = invocation.getArgument(0);
            tx.setId("tx1");
            return tx;
        });

        AccountTransactionDTO outputDTO = new AccountTransactionDTO(
            "tx1",
            LocalDateTime.now(),
            new BigDecimal("100"),
            "Test transaction",
            null,
            null,
            null,
            TransactionType.DEBIT,
            new AccountDTO("a1", "Account 1", BigDecimal.ZERO, null, null, null, null, null),
            new CategoryDTO("cat", "Food", null, false, List.of()),
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            "INR",
            null,
            null,
            List.of(new LabelDTO("l1", "Food"), new LabelDTO("l2", "Personal"))
        );

        when(accountTransactionMapper.toDTO(any(AccountTransaction.class))).thenReturn(outputDTO);

        AccountTransactionDTO result = service.save(input);

        assertThat(result.id()).isEqualTo("tx1");
        assertThat(result.labels()).hasSize(2);
        assertThat(result.labels()).extracting(LabelDTO::name).containsExactly("Food", "Personal");
        verify(labelService).findOrCreateLabel(currentUser, "Food");
        verify(labelService).findOrCreateLabel(currentUser, "Personal");
    }

    @Test
    void updateTransaction_withLabels_updatesLabels() {
        Account acc = createAccount("a1", new BigDecimal("1000"));
        Category cat = new Category();
        cat.setId("cat");
        cat.setName("Food");
        cat.setAppUser(currentUser);

        AccountTransaction existing = AccountTransaction.builder()
            .id("tx1")
            .amount(new BigDecimal("50"))
            .type(TransactionType.DEBIT)
            .account(acc)
            .category(cat)
            .appUser(currentUser)
            .build();

        Label oldLabel = new Label();
        oldLabel.setId("l1");
        oldLabel.setName("OldLabel");
        oldLabel.setAppUser(currentUser);
        existing.setLabels(List.of(oldLabel), currentUser);

        when(accountTransactionRepository.findById("tx1")).thenReturn(Optional.of(existing));
        when(accountTransactionRepository.findByAppUserAndParent(currentUser, "tx1")).thenReturn(List.of());
        when(accountRepository.findByAppUserAndId(currentUser, "a1")).thenReturn(Optional.of(acc));
        when(categoryService.getCategoryById("cat")).thenReturn(cat);

        Label newLabel1 = new Label();
        newLabel1.setId("l2");
        newLabel1.setName("NewLabel1");
        newLabel1.setAppUser(currentUser);

        Label newLabel2 = new Label();
        newLabel2.setId("l3");
        newLabel2.setName("NewLabel2");
        newLabel2.setAppUser(currentUser);

        when(labelService.findOrCreateLabel(currentUser, "NewLabel1")).thenReturn(newLabel1);
        when(labelService.findOrCreateLabel(currentUser, "NewLabel2")).thenReturn(newLabel2);

        AccountTransactionDTO update = new AccountTransactionDTO(
            "tx1",
            LocalDateTime.now(),
            new BigDecimal("50"),
            "Updated",
            null,
            null,
            null,
            TransactionType.DEBIT,
            new AccountDTO("a1", "Account 1", BigDecimal.ZERO, null, null, null, null, null),
            new CategoryDTO("cat", "Food", null, false, List.of()),
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            "INR",
            null,
            null,
            List.of(new LabelDTO("l2", "NewLabel1"), new LabelDTO("l3", "NewLabel2"))
        );

        AccountTransaction mappedEntity = new AccountTransaction();
        mappedEntity.setId("tx1");
        mappedEntity.setAccount(acc);
        mappedEntity.setCategory(cat);
        mappedEntity.setAppUser(currentUser);
        mappedEntity.setLabels(List.of(newLabel1, newLabel2), currentUser);

        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenAnswer(invocation -> {
            AccountTransaction tx = invocation.getArgument(0);
            return tx;
        });

        AccountTransactionDTO outputDTO = new AccountTransactionDTO(
            "tx1",
            LocalDateTime.now(),
            new BigDecimal("50"),
            "Updated",
            null,
            null,
            null,
            TransactionType.DEBIT,
            new AccountDTO("a1", "Account 1", BigDecimal.ZERO, null, null, null, null, null),
            new CategoryDTO("cat", "Food", null, false, List.of()),
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            "INR",
            null,
            null,
            List.of(new LabelDTO("l2", "NewLabel1"), new LabelDTO("l3", "NewLabel2"))
        );

        when(accountTransactionMapper.toDTO(any(AccountTransaction.class))).thenReturn(outputDTO);

        Optional<AccountTransactionDTO> result = service.updateTransaction("tx1", update);

        assertThat(result).isPresent();
        assertThat(result.get().labels()).hasSize(2);
        assertThat(result.get().labels()).extracting(LabelDTO::name).containsExactly("NewLabel1", "NewLabel2");
        verify(labelService).findOrCreateLabel(currentUser, "NewLabel1");
        verify(labelService).findOrCreateLabel(currentUser, "NewLabel2");
    }

    @Test
    void save_withNewLabelNames_createsLabels() {
        Account acc = createAccount("a1", new BigDecimal("1000"));
        Category cat = new Category();
        cat.setId("cat");
        cat.setName("Food");
        cat.setAppUser(currentUser);

        when(accountRepository.findByAppUserAndId(currentUser, "a1")).thenReturn(Optional.of(acc));
        lenient().when(categoryService.getCategoryById("cat")).thenReturn(cat);

        Label newLabel = new Label();
        newLabel.setId("l1");
        newLabel.setName("BrandNewLabel");
        newLabel.setAppUser(currentUser);

        when(labelService.findOrCreateLabel(currentUser, "BrandNewLabel")).thenReturn(newLabel);

        AccountTransactionDTO input = new AccountTransactionDTO(
            null,
            LocalDateTime.now(),
            new BigDecimal("100"),
            "Test transaction",
            null,
            null,
            null,
            TransactionType.DEBIT,
            new AccountDTO("a1", "Account 1", BigDecimal.ZERO, null, null, null, null, null),
            new CategoryDTO("cat", "Food", null, false, List.of()),
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            "INR",
            null,
            null,
            List.of(new LabelDTO(null, "BrandNewLabel"))
        );

        AccountTransaction entity = new AccountTransaction();
        entity.setId("tx1");
        entity.setAmount(new BigDecimal("100"));
        entity.setType(TransactionType.DEBIT);
        entity.setAccount(acc);
        entity.setCategory(cat);
        entity.setAppUser(currentUser);
        entity.setLabels(List.of(newLabel), currentUser);

        when(accountTransactionMapper.toEntity(input)).thenReturn(entity);
        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenAnswer(invocation -> {
            AccountTransaction tx = invocation.getArgument(0);
            tx.setId("tx1");
            return tx;
        });

        AccountTransactionDTO outputDTO = new AccountTransactionDTO(
            "tx1",
            LocalDateTime.now(),
            new BigDecimal("100"),
            "Test transaction",
            null,
            null,
            null,
            TransactionType.DEBIT,
            new AccountDTO("a1", "Account 1", BigDecimal.ZERO, null, null, null, null, null),
            new CategoryDTO("cat", "Food", null, false, List.of()),
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            "INR",
            null,
            null,
            List.of(new LabelDTO("l1", "BrandNewLabel"))
        );

        when(accountTransactionMapper.toDTO(any(AccountTransaction.class))).thenReturn(outputDTO);

        AccountTransactionDTO result = service.save(input);

        assertThat(result.labels()).hasSize(1);
        assertThat(result.labels().get(0).name()).isEqualTo("BrandNewLabel");
        verify(labelService).findOrCreateLabel(currentUser, "BrandNewLabel");
    }
}


