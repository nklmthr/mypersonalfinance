package com.nklmthr.finance.personal.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.service.AccountService;
import com.nklmthr.finance.personal.util.AccountFuzzyMatcher;

/**
 * CRITICAL TEST: Ensures Regex and GPT fields are NEVER mixed
 * 
 * Regex should ONLY set: description, amount, type, account
 * GPT should ONLY set: gptDescription, gptAmount, gptType, gptAccount
 */
public class FieldSeparationTest {

    @Test
    void testAxisCC_RegexOnlySetsNonGptFields() throws Exception {
        String email = """
            Transaction Amount: INR 1000
            Merchant Name: TEST MERCHANT
            Card No. XX0434
            """;

        AxisCCDataExtractionService extractor = new AxisCCDataExtractionService();
        
        // Mock accountService
        AccountService mockAccountService = Mockito.mock(AccountService.class);
        Account mockAccount = new Account();
        mockAccount.setName("Test Account");
        Mockito.when(mockAccountService.getAllAccounts(Mockito.any()))
            .thenReturn(java.util.List.of());
        Mockito.when(mockAccountService.getAccountByName(Mockito.anyString(), Mockito.any()))
            .thenReturn(mockAccount);
        
        // Mock accountFuzzyMatcher
        AccountFuzzyMatcher mockFuzzyMatcher = Mockito.mock(AccountFuzzyMatcher.class);
        AccountDTO mockAccountDTO = new AccountDTO("test", "Test Account", null, null, null, null, null, null);
        Mockito.when(mockFuzzyMatcher.findBestMatch(Mockito.anyList(), Mockito.anyString(), Mockito.anyString()))
            .thenReturn(new AccountFuzzyMatcher.MatchResult(mockAccountDTO, 10));
        
        // Use reflection to set fields (accountService is in parent class)
        Field accountServiceField = AbstractDataExtractionService.class.getDeclaredField("accountService");
        accountServiceField.setAccessible(true);
        accountServiceField.set(extractor, mockAccountService);
        
        Field fuzzyMatcherField = AxisCCDataExtractionService.class.getDeclaredField("accountFuzzyMatcher");
        fuzzyMatcherField.setAccessible(true);
        fuzzyMatcherField.set(extractor, mockFuzzyMatcher);

        AccountTransaction tx = new AccountTransaction();
        AccountTransaction result = extractor.extractTransactionData(tx, email, new AppUser());

        // ✅ Regex SHOULD set non-GPT fields
        assertNotNull(result.getAmount(), "Regex should set amount");
        assertEquals(new BigDecimal("1000"), result.getAmount());
        assertNotNull(result.getDescription(), "Regex should set description");
        assertEquals("TEST MERCHANT", result.getDescription());
        assertNotNull(result.getType(), "Regex should set type");
        assertEquals(TransactionType.DEBIT, result.getType());

        // ❌ Regex should NEVER touch GPT fields (they should remain null)
        assertNull(result.getGptAmount(), "Regex must NOT set gptAmount");
        assertNull(result.getGptDescription(), "Regex must NOT set gptDescription");
        assertNull(result.getGptType(), "Regex must NOT set gptType");
        assertNull(result.getGptAccount(), "Regex must NOT set gptAccount");

        System.out.println("✅ Regex extraction correctly isolated - no GPT fields touched");
    }

    @Test
    void testICICI_RegexOnlySetsNonGptFields() throws Exception {
        String email = """
            Transaction alert for your ICICI Bank Credit Card XX9057
            INR 500.50 on Oct 24, 2025 at 15:30:00. 
            Info: SWIGGY FOOD
            """;

        ICICICCDataExtractionServiceImpl extractor = new ICICICCDataExtractionServiceImpl();
        
        // Mock accountService
        AccountService mockAccountService = Mockito.mock(AccountService.class);
        Account mockAccount = new Account();
        mockAccount.setName("Test Account");
        Mockito.when(mockAccountService.getAllAccounts(Mockito.any()))
            .thenReturn(java.util.List.of());
        Mockito.when(mockAccountService.getAccountByName(Mockito.anyString(), Mockito.any()))
            .thenReturn(mockAccount);
        
        // Mock accountFuzzyMatcher
        AccountFuzzyMatcher mockFuzzyMatcher = Mockito.mock(AccountFuzzyMatcher.class);
        AccountDTO mockAccountDTO = new AccountDTO("test", "Test Account", null, null, null, null, null, null);
        Mockito.when(mockFuzzyMatcher.findBestMatch(Mockito.anyList(), Mockito.anyString(), Mockito.anyString()))
            .thenReturn(new AccountFuzzyMatcher.MatchResult(mockAccountDTO, 10));
        
        // Use reflection to set fields (accountService is in parent class)
        Field accountServiceField = AbstractDataExtractionService.class.getDeclaredField("accountService");
        accountServiceField.setAccessible(true);
        accountServiceField.set(extractor, mockAccountService);
        
        Field fuzzyMatcherField = ICICICCDataExtractionServiceImpl.class.getDeclaredField("accountFuzzyMatcher");
        fuzzyMatcherField.setAccessible(true);
        fuzzyMatcherField.set(extractor, mockFuzzyMatcher);

        AccountTransaction tx = new AccountTransaction();
        AccountTransaction result = extractor.extractTransactionData(tx, email, new AppUser());

        // ✅ Regex sets non-GPT fields
        assertNotNull(result.getAmount());
        assertNotNull(result.getType());

        // ❌ Regex never touches GPT fields
        assertNull(result.getGptAmount(), "Regex must NOT set gptAmount");
        assertNull(result.getGptDescription(), "Regex must NOT set gptDescription");
        assertNull(result.getGptType(), "Regex must NOT set gptType");

        System.out.println("✅ ICICI regex extraction correctly isolated");
    }

    @Test
    void testRegexFailure_UsesDefaultNotGptFallback() {
        // Email with NO extractable description
        String email = """
            Transaction Amount: INR 999
            Card: XX0434
            """;

        AxisCCDataExtractionService extractor = new AxisCCDataExtractionService();
        extractor.accountService = Mockito.mock(AccountService.class);

        AccountTransaction tx = new AccountTransaction();
        
        // Simulate GPT setting gptDescription
        tx.setGptDescription("GPT EXTRACTED MERCHANT");
        
        AccountTransaction result = extractor.extractTransactionData(tx, email, new AppUser());

        // ✅ Amount extracted by regex
        assertNotNull(result.getAmount());
        assertEquals(new BigDecimal("999"), result.getAmount());

        // ✅ Description NOT extracted (pattern failed)
        // The AbstractDataExtractionService will set it to "Unknown", NOT copy from gptDescription
        // Since we're only testing the extractor here, it should be null
        assertNull(result.getDescription(), "Regex should not set description when pattern fails");

        // ✅ GPT field remains unchanged
        assertEquals("GPT EXTRACTED MERCHANT", result.getGptDescription(), 
            "gptDescription should remain untouched by regex");

        System.out.println("✅ Regex failure does NOT fallback to GPT - fields remain separate");
    }

    @Test
    void testFieldSeparation_BothPopulated() {
        AccountTransaction tx = new AccountTransaction();
        
        // Regex sets non-GPT fields
        tx.setDescription("Regex Description");
        tx.setAmount(new BigDecimal("100"));
        tx.setType(TransactionType.DEBIT);

        // GPT sets GPT fields
        tx.setGptDescription("GPT Description");
        tx.setGptAmount(new BigDecimal("100.0"));
        tx.setGptType(TransactionType.DEBIT);

        // ✅ Both should coexist without interference
        assertEquals("Regex Description", tx.getDescription());
        assertEquals("GPT Description", tx.getGptDescription());
        assertEquals(new BigDecimal("100"), tx.getAmount());
        assertEquals(new BigDecimal("100.0"), tx.getGptAmount());
        assertEquals(TransactionType.DEBIT, tx.getType());
        assertEquals(TransactionType.DEBIT, tx.getGptType());

        assertNotEquals(tx.getDescription(), tx.getGptDescription(), 
            "Regex and GPT descriptions should be independent");

        System.out.println("✅ Regex and GPT fields coexist independently");
    }

    @Test
    void testNoGptFieldsPollutedByRegex() {
        AccountTransaction tx = new AccountTransaction();
        
        // Start with clean GPT fields
        assertNull(tx.getGptDescription());
        assertNull(tx.getGptAmount());
        assertNull(tx.getGptType());
        assertNull(tx.getGptAccount());

        // Simulate regex extraction setting non-GPT fields
        tx.setDescription("Merchant Name");
        tx.setAmount(new BigDecimal("500"));
        tx.setType(TransactionType.CREDIT);

        // ✅ GPT fields should still be null (not touched)
        assertNull(tx.getGptDescription(), "setDescription() should NOT affect gptDescription");
        assertNull(tx.getGptAmount(), "setAmount() should NOT affect gptAmount");
        assertNull(tx.getGptType(), "setType() should NOT affect gptType");

        System.out.println("✅ Regex setters do not pollute GPT fields");
    }
}

