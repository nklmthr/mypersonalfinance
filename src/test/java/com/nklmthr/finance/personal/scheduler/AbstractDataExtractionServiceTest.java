package com.nklmthr.finance.personal.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;

/**
 * Test suite for AbstractDataExtractionService
 * Tests the email extraction, text cleaning, and query generation logic
 */
@ExtendWith(MockitoExtension.class)
class AbstractDataExtractionServiceTest {

    private TestDataExtractionService service;

    @BeforeEach
    void setUp() {
        service = new TestDataExtractionService();
    }

    // ==================== Email Extraction Tests ====================

    /**
     * Test email extraction from plain text
     */
    @Test
    void extractPlainText_fromPlainTextEmail() {
        // Setup
        String originalText = "Hello World Transaction";
        Message message = new Message();
        message.setId("msg-1");
        MessagePart payload = new MessagePart();
        payload.setMimeType("text/plain");
        MessagePartBody body = new MessagePartBody();
        body.setData(Base64.getUrlEncoder().encodeToString(originalText.getBytes()));
        payload.setBody(body);
        message.setPayload(payload);
        
        // Execute
        String result = service.extractPlainText(message);
        
        // Verify
        assertThat(result).isEqualTo("Hello World Transaction");
    }

    /**
     * Test email extraction from HTML
     */
    @Test
    void extractPlainText_fromHtmlEmail() {
        // Setup
        String htmlContent = "<html><body><p>Transaction of <b>Rs.100</b> at TestMerchant</p></body></html>";
        Message message = new Message();
        message.setId("msg-1");
        MessagePart payload = new MessagePart();
        payload.setMimeType("text/html");
        MessagePartBody body = new MessagePartBody();
        body.setData(Base64.getUrlEncoder().encodeToString(htmlContent.getBytes()));
        payload.setBody(body);
        message.setPayload(payload);
        
        // Execute
        String result = service.extractPlainText(message);
        
        // Verify - HTML tags should be stripped
        assertThat(result).isEqualTo("Transaction of Rs.100 at TestMerchant");
        assertThat(result).doesNotContain("<html>");
        assertThat(result).doesNotContain("<b>");
    }

    /**
     * Test email extraction from multipart message
     */
    @Test
    void extractPlainText_fromMultipartEmail() {
        // Setup
        Message message = new Message();
        message.setId("msg-1");
        MessagePart payload = new MessagePart();
        payload.setMimeType("multipart/alternative");
        
        // Plain text part
        MessagePart textPart = new MessagePart();
        textPart.setMimeType("text/plain");
        MessagePartBody textBody = new MessagePartBody();
        textBody.setData(Base64.getUrlEncoder().encodeToString("Plain text content".getBytes()));
        textPart.setBody(textBody);
        
        // HTML part
        MessagePart htmlPart = new MessagePart();
        htmlPart.setMimeType("text/html");
        MessagePartBody htmlBody = new MessagePartBody();
        htmlBody.setData(Base64.getUrlEncoder().encodeToString("<html>HTML content</html>".getBytes()));
        htmlPart.setBody(htmlBody);
        
        payload.setParts(List.of(textPart, htmlPart));
        message.setPayload(payload);
        
        // Execute
        String result = service.extractPlainText(message);
        
        // Verify - should extract both parts
        assertThat(result).contains("Plain text content");
        assertThat(result).contains("HTML content");
    }

    /**
     * Test that null message is handled gracefully
     */
    @Test
    void extractPlainText_handlesNullMessage() {
        String result = service.extractPlainText(null);
        assertThat(result).isEqualTo("[Null message]");
    }

    /**
     * Test that missing payload is handled gracefully
     */
    @Test
    void extractPlainText_handlesNullPayload() {
        Message message = new Message();
        message.setId("msg-1");
        message.setPayload(null);
        
        String result = service.extractPlainText(message);
        assertThat(result).isEqualTo("[No payload]");
    }

    /**
     * Test that empty message part returns empty content marker
     */
    @Test
    void extractPlainText_handlesEmptyContent() {
        Message message = new Message();
        message.setId("msg-1");
        MessagePart payload = new MessagePart();
        payload.setMimeType("text/plain");
        payload.setBody(new MessagePartBody());
        message.setPayload(payload);
        
        String result = service.extractPlainText(message);
        assertThat(result).isEqualTo("[Empty content]");
    }

    /**
     * Test email with nested multipart structure
     */
    @Test
    void extractPlainText_handlesNestedMultipart() {
        Message message = new Message();
        message.setId("msg-1");
        
        // Create nested structure
        MessagePart outerPart = new MessagePart();
        outerPart.setMimeType("multipart/mixed");
        
        MessagePart innerPart = new MessagePart();
        innerPart.setMimeType("multipart/alternative");
        
        MessagePart textPart = new MessagePart();
        textPart.setMimeType("text/plain");
        MessagePartBody textBody = new MessagePartBody();
        textBody.setData(Base64.getUrlEncoder().encodeToString("Nested content".getBytes()));
        textPart.setBody(textBody);
        
        innerPart.setParts(List.of(textPart));
        outerPart.setParts(List.of(innerPart));
        message.setPayload(outerPart);
        
        // Execute
        String result = service.extractPlainText(message);
        
        // Verify
        assertThat(result).contains("Nested content");
    }

    // ==================== Text Cleaning Tests ====================

    /**
     * Test cleanText removes special non-breaking spaces
     */
    @Test
    void cleanText_removesNonBreakingSpaces() {
        String input = "Hello\u00A0World"; // Non-breaking space
        String result = service.cleanText(input);
        assertThat(result).isEqualTo("Hello World");
    }

    /**
     * Test cleanText removes zero-width spaces
     */
    @Test
    void cleanText_removesZeroWidthSpaces() {
        String input = "Hello\u200BWorld\u200B"; // Zero-width space
        String result = service.cleanText(input);
        assertThat(result).isEqualTo("Hello World");
    }

    /**
     * Test cleanText normalizes multiple whitespace characters
     */
    @Test
    void cleanText_normalizesWhitespace() {
        String input = "Hello    World\t\n\rTest";
        String result = service.cleanText(input);
        assertThat(result).isEqualTo("Hello World Test");
    }

    /**
     * Test cleanText handles null input
     */
    @Test
    void cleanText_handlesNull() {
        String result = service.cleanText(null);
        assertThat(result).isEmpty();
    }

    /**
     * Test cleanText removes all special Unicode spaces
     */
    @Test
    void cleanText_removesAllSpecialSpaces() {
        String input = "Test\u00A0\u2007\u202F\u200B\uFEFFText";
        String result = service.cleanText(input);
        assertThat(result).isEqualTo("Test Text");
    }

    /**
     * Test cleanText trims leading and trailing whitespace
     */
    @Test
    void cleanText_trimsWhitespace() {
        String input = "   Hello World   ";
        String result = service.cleanText(input);
        assertThat(result).isEqualTo("Hello World");
    }

    // ==================== Gmail Query Generation Tests ====================

    /**
     * Test Gmail query generation includes all required fields
     */
    @Test
    void getGMailAPIQuery_generatesCorrectQueries() {
        List<String> queries = service.getGMailAPIQuery();
        
        assertThat(queries).isNotEmpty();
        assertThat(queries).hasSize(1);
        
        String query = queries.get(0);
        assertThat(query).contains("subject:(Test Subject)");
        assertThat(query).contains("from:(test@bank.com)");
        assertThat(query).contains("after:");
        assertThat(query).contains("before:");
    }

    /**
     * Test Gmail query with multiple subjects
     */
    @Test
    void getGMailAPIQuery_handlesMultipleSubjects() {
        TestDataExtractionService multiSubjectService = new TestDataExtractionService() {
            @Override
            protected List<String> getEmailSubject() {
                return List.of("Subject1", "Subject2", "Subject3");
            }
        };
        
        List<String> queries = multiSubjectService.getGMailAPIQuery();
        
        assertThat(queries).hasSize(3);
        assertThat(queries.get(0)).contains("subject:(Subject1)");
        assertThat(queries.get(1)).contains("subject:(Subject2)");
        assertThat(queries.get(2)).contains("subject:(Subject3)");
    }

    /**
     * Test Gmail query date format
     */
    @Test
    void getGMailAPIQuery_usesCorrectDateFormat() {
        List<String> queries = service.getGMailAPIQuery();
        String query = queries.get(0);
        
        // Should have format YYYY-MM-DD
        assertThat(query).containsPattern("after:\\d{4}-\\d{2}-\\d{2}");
        assertThat(query).containsPattern("before:\\d{4}-\\d{2}-\\d{2}");
    }

    // ==================== Transaction Data Extraction Tests ====================

    /**
     * Test that extractTransactionData populates basic fields
     */
    @Test
    void extractTransactionData_populatesBasicFields() {
        AccountTransaction tx = new AccountTransaction();
        AppUser user = AppUser.builder().id("user-1").username("test@example.com").build();
        String emailContent = "Transaction of Rs.100 at TestMerchant";
        
        AccountTransaction result = service.extractTransactionData(tx, emailContent, user);
        
        assertThat(result).isNotNull();
        assertThat(result.getDescription()).isEqualTo("Test Description");
        assertThat(result.getAmount()).isEqualByComparingTo("100");
        assertThat(result.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(result.getAccount()).isNotNull();
    }

    /**
     * Test that extractTransactionData preserves existing fields
     */
    @Test
    void extractTransactionData_preservesExistingFields() {
        AccountTransaction tx = new AccountTransaction();
        tx.setSourceId("existing-source-id");
        tx.setSourceThreadId("existing-thread-id");
        tx.setRawData("existing-raw-data");
        
        AppUser user = AppUser.builder().id("user-1").build();
        String emailContent = "Transaction content";
        
        AccountTransaction result = service.extractTransactionData(tx, emailContent, user);
        
        // Should preserve the fields that were already set
        assertThat(result.getSourceId()).isEqualTo("existing-source-id");
        assertThat(result.getSourceThreadId()).isEqualTo("existing-thread-id");
        assertThat(result.getRawData()).isEqualTo("existing-raw-data");
    }

    // ==================== Integration Tests ====================

    /**
     * Test complete flow: extract email -> clean text -> verify output
     */
    @Test
    void completeFlow_extractAndClean() {
        // Setup email with special characters and HTML
        String htmlContent = "<html><body>Transaction\u00A0of  Rs.100\t\nat\nTestMerchant</body></html>";
        Message message = new Message();
        message.setId("msg-1");
        MessagePart payload = new MessagePart();
        payload.setMimeType("text/html");
        MessagePartBody body = new MessagePartBody();
        body.setData(Base64.getUrlEncoder().encodeToString(htmlContent.getBytes()));
        payload.setBody(body);
        message.setPayload(payload);
        
        // Execute
        String result = service.extractPlainText(message);
        
        // Verify - should have HTML stripped and whitespace normalized
        assertThat(result).doesNotContain("<html>");
        assertThat(result).doesNotContain("\u00A0");
        assertThat(result).contains("Transaction of Rs.100 at TestMerchant");
    }

    // ==================== Test Helper Class ====================

    /**
     * Test implementation of AbstractDataExtractionService for testing
     */
    static class TestDataExtractionService extends AbstractDataExtractionService {
        @Override
        protected List<String> getEmailSubject() {
            return List.of("Test Subject");
        }

        @Override
        protected String getSender() {
            return "test@bank.com";
        }

        @Override
        protected AccountTransaction extractTransactionData(AccountTransaction tx, String emailContent, AppUser appUser) {
            // Simple extraction logic for testing
            tx.setDescription("Test Description");
            tx.setAmount(new BigDecimal("100"));
            tx.setType(TransactionType.DEBIT);
            
            Account mockAccount = new Account();
            mockAccount.setId("acc-1");
            mockAccount.setName("Test Account");
            tx.setAccount(mockAccount);
            
            return tx;
        }
    }
}
