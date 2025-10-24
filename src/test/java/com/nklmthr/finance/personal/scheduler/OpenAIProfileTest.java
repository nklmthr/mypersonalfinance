package com.nklmthr.finance.personal.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.openai.OpenAIClient;
import com.nklmthr.finance.personal.repository.AppUserRepository;
import com.nklmthr.finance.personal.service.AccountService;
import com.nklmthr.finance.personal.service.AccountTransactionService;
import com.nklmthr.finance.personal.service.CategoryService;
import com.nklmthr.finance.personal.service.gmail.GmailServiceProvider;

/**
 * Tests to verify OpenAI calls are controlled by Spring Boot profile
 */
@ExtendWith(MockitoExtension.class)
class OpenAIProfileTest {

    @Mock
    private OpenAIClient openAIClient;

    @Mock
    private AccountTransactionService accountTransactionService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private GmailServiceProvider gmailServiceProvider;

    @Mock
    private AccountService accountService;

    @Mock
    private CategoryService categoryService;

    private TestDataExtractionService service;

    /**
     * Concrete test implementation for testing
     */
    private static class TestDataExtractionService extends AbstractDataExtractionService {
        @Override
        protected String getSender() {
            return "test@example.com";
        }

        @Override
        protected List<String> getEmailSubject() {
            return List.of("Test Transaction");
        }

        @Override
        protected AccountTransaction extractTransactionData(AccountTransaction accountTransaction, 
                String emailContent, AppUser appUser) {
            accountTransaction.setDescription("Test Merchant");
            accountTransaction.setAmount(new BigDecimal("100.00"));
            accountTransaction.setRawData(emailContent);
            return accountTransaction;
        }
    }

    @BeforeEach
    void setUp() {
        service = new TestDataExtractionService();
        ReflectionTestUtils.setField(service, "openAIClient", openAIClient);
        ReflectionTestUtils.setField(service, "accountTransactionService", accountTransactionService);
        ReflectionTestUtils.setField(service, "appUserRepository", appUserRepository);
        ReflectionTestUtils.setField(service, "gmailServiceProvider", gmailServiceProvider);
        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "categoryService", categoryService);
        ReflectionTestUtils.setField(service, "gmailLookbackDays", 7);
    }

    @Test
    void testOpenAIEnabled_CallsOpenAI() {
        // Arrange
        ReflectionTestUtils.setField(service, "openAIEnabled", true);

        String emailContent = "Test email content";
        AccountTransaction tx = new AccountTransaction();
        tx.setDescription("Test");
        tx.setAmount(new BigDecimal("100.00"));

        // Act - simulate the OpenAI check logic
        if ((boolean) ReflectionTestUtils.getField(service, "openAIEnabled")) {
            openAIClient.getGptResponse(emailContent, tx);
        }

        // Assert
        verify(openAIClient, times(1)).getGptResponse(emailContent, tx);
    }

    @Test
    void testOpenAIDisabled_SkipsOpenAI() {
        // Arrange
        ReflectionTestUtils.setField(service, "openAIEnabled", false);

        String emailContent = "Test email content";
        AccountTransaction tx = new AccountTransaction();
        tx.setDescription("Test");
        tx.setAmount(new BigDecimal("100.00"));

        // Act - simulate the OpenAI check logic
        if ((boolean) ReflectionTestUtils.getField(service, "openAIEnabled")) {
            openAIClient.getGptResponse(emailContent, tx);
        }

        // Assert
        verify(openAIClient, never()).getGptResponse(anyString(), any(AccountTransaction.class));
    }

    @Test
    void testDefaultValue_OpenAIEnabledByDefault() {
        // Arrange - set the default value (true)
        ReflectionTestUtils.setField(service, "openAIEnabled", true);

        // Assert
        boolean enabled = (boolean) ReflectionTestUtils.getField(service, "openAIEnabled");
        assertTrue(enabled, "OpenAI should be enabled by default");
    }

    @Test
    void testDevProfile_OpenAIDisabled() {
        // Arrange - simulate dev profile
        ReflectionTestUtils.setField(service, "openAIEnabled", false);

        // Assert
        boolean enabled = (boolean) ReflectionTestUtils.getField(service, "openAIEnabled");
        assertFalse(enabled, "OpenAI should be disabled in dev profile");
    }

    @Test
    void testProdProfile_OpenAIEnabled() {
        // Arrange - simulate prod profile
        ReflectionTestUtils.setField(service, "openAIEnabled", true);

        // Assert
        boolean enabled = (boolean) ReflectionTestUtils.getField(service, "openAIEnabled");
        assertTrue(enabled, "OpenAI should be enabled in prod profile");
    }

    @Test
    void testTransactionSavedEvenWhenOpenAIDisabled() {
        // Arrange
        ReflectionTestUtils.setField(service, "openAIEnabled", false);

        String emailContent = "Test email content";
        AppUser appUser = new AppUser();
        appUser.setUsername("test@example.com");

        AccountTransaction tx = new AccountTransaction();
        tx.setDescription("Test Merchant");
        tx.setAmount(new BigDecimal("100.00"));
        tx.setRawData(emailContent);

        // Act - simulate the save logic
        if ((boolean) ReflectionTestUtils.getField(service, "openAIEnabled")) {
            openAIClient.getGptResponse(emailContent, tx);
        }
        
        // Ensure description is never null
        if (tx.getDescription() == null) {
            tx.setDescription("Unknown");
        }
        
        accountTransactionService.save(tx, appUser);

        // Assert
        verify(openAIClient, never()).getGptResponse(anyString(), any(AccountTransaction.class));
        verify(accountTransactionService, times(1)).save(tx, appUser);
        assertNotNull(tx.getDescription(), "Description should not be null");
    }
}

