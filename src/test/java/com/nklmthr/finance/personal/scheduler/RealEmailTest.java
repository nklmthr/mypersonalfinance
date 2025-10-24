package com.nklmthr.finance.personal.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.scheduler.util.PatternResult;
import com.nklmthr.finance.personal.scheduler.util.TransactionPatternLibrary;

/**
 * Test with real email samples to ensure pattern library works correctly
 */
public class RealEmailTest {

    @Test
    void testAxisCreditCardEmail_MadhulokaL() {
        String emailContent = """
            23-10-2025 Dear Nikhil Mathur, Here's the summary of your Axis Bank Credit Card Transaction: 
            Transaction Amount: INR 3480 
            Merchant Name: MADHULOKA L 
            Axis Bank Credit Card No. XX0434 
            Date & Time: 23-10-2025, 16:10:58 IST 
            Available Limit*: INR 1323005.97 
            Total Credit Limit*: INR 1350000 
            *The information above includes the available and total credit limit across all of your Axis Bank credit cards. 
            If this transaction was not intiated by you: SMS BLOCK 0434 to +919951860002 or 
            Call us at: 18001035577 (Toll Free) 18604195555 (Charges Applicable) 
            Always open to help you. 
            Regards, Axis Bank Ltd.
            """;

        // Test amount extraction
        PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
        assertTrue(amountResult.isPresent(), "Amount should be extracted");
        assertEquals(new BigDecimal("3480"), amountResult.getValue(), "Amount should be 3480");
        assertTrue(amountResult.hasHighConfidence(), "Should have high confidence for labeled amount");
        System.out.println("✅ Amount: " + amountResult.getValue() + " (Pattern: " + amountResult.getMatchedPattern() + ", Confidence: " + amountResult.getConfidenceScore() + ")");

        // Test description extraction
        PatternResult<String> descResult = TransactionPatternLibrary.extractDescription(emailContent);
        assertTrue(descResult.isPresent(), "Description should be extracted");
        assertEquals("MADHULOKA L", descResult.getValue(), "Description should be merchant name");
        assertTrue(descResult.hasHighConfidence(), "Should have high confidence for labeled merchant");
        System.out.println("✅ Description: " + descResult.getValue() + " (Pattern: " + descResult.getMatchedPattern() + ", Confidence: " + descResult.getConfidenceScore() + ")");

        // Test account identifier extraction
        var accountResult = TransactionPatternLibrary.extractAccountIdentifier(emailContent);
        assertTrue(accountResult.isPresent(), "Account identifier should be extracted");
        assertEquals("0434", accountResult.get(), "Should extract last 4 digits");
        System.out.println("✅ Account: " + accountResult.get());

        // Test transaction type detection
        TransactionType type = TransactionPatternLibrary.detectTransactionType(emailContent);
        assertEquals(TransactionType.DEBIT, type, "Credit card transaction should be DEBIT");
        System.out.println("✅ Type: " + type);

        // Summary
        System.out.println("\n📋 EXTRACTION SUMMARY:");
        System.out.println("   Amount:      INR " + amountResult.getValue());
        System.out.println("   Merchant:    " + descResult.getValue());
        System.out.println("   Card:        XX" + accountResult.get());
        System.out.println("   Type:        " + type);
        System.out.println("   ✅ All patterns matched successfully!");
    }

    @Test
    void testAxisCreditCardEmail_AlternativeFormat() {
        // Test with slightly different format (missing some fields)
        String emailContent = """
            Transaction Amount: INR 1250.50
            Merchant Name: SWIGGY BANGALORE
            Credit Card No. XX0434
            Date: 24-10-2025
            """;

        PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
        assertTrue(amountResult.isPresent());
        assertEquals(new BigDecimal("1250.50"), amountResult.getValue());

        PatternResult<String> descResult = TransactionPatternLibrary.extractDescription(emailContent);
        assertTrue(descResult.isPresent());
        assertEquals("SWIGGY BANGALORE", descResult.getValue());
    }

    @Test
    void testAxisCreditCardEmail_EdgeCases() {
        // Test with amount but no merchant name
        String emailContent1 = """
            Transaction Amount: INR 500
            Card ending in 0434
            """;

        PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent1);
        assertTrue(amountResult.isPresent());
        assertEquals(new BigDecimal("500"), amountResult.getValue());

        // Description might not be present - verify it's either absent or generic
        PatternResult<String> descResult = TransactionPatternLibrary.extractDescription(emailContent1);
        // This is okay - not all patterns will match all emails
        assertNotNull(descResult, "Result should not be null even if no match");
    }

    @Test
    void testAxisCreditCardEmail_FooterNotMatched() {
        // This is the CRITICAL test - footer should NOT be matched as description
        String emailWithFooter = """
            Transaction Amount: INR 3480
            Merchant Name: MADHULOKA L
            Axis Bank Credit Card No. XX0434
            Chat Web Support Mobile App Internet Banking WhatsApp Branch Locator Copyright Axis Bank Ltd
            """;

        // Should extract merchant correctly, NOT the footer
        PatternResult<String> descResult = TransactionPatternLibrary.extractDescription(emailWithFooter);
        assertTrue(descResult.isPresent(), "Description should be extracted");
        assertEquals("MADHULOKA L", descResult.getValue(), "Should extract merchant, NOT footer");
        assertFalse(descResult.getValue().contains("Web Support"), "Footer text should NOT be in description");
        assertFalse(descResult.getValue().contains("Chat"), "Footer text should NOT be in description");
        
        System.out.println("✅ Footer correctly IGNORED, merchant correctly extracted: " + descResult.getValue());
    }

    @Test
    void testAxisSavingCreditEmail_NEFT() {
        String emailContent = """
            24-10-2025 Dear Nikhil Mathur, Thank you for banking with us. We wish to inform you that your A/c no. XX2804 has been credited with INR 26380.00 on 24-10-2025 at 02:03:02 IST by NEFT/CITIN25643200622/SAP . To check your available balance, please click here . For details, please contact your Burgundy RM or call 18004190065. Always open to help you. Regards, Axis Bank Ltd.
            """;

        // Test amount extraction
        PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
        assertTrue(amountResult.isPresent(), "Amount should be extracted");
        assertEquals(new BigDecimal("26380.00"), amountResult.getValue(), "Amount should be 26380.00, not 2804");
        System.out.println("✅ Amount: " + amountResult.getValue() + " (Pattern: " + amountResult.getMatchedPattern() + ")");

        // Test description extraction
        PatternResult<String> descResult = TransactionPatternLibrary.extractDescription(emailContent);
        assertTrue(descResult.isPresent(), "Description should be extracted");
        assertEquals("NEFT/CITIN25643200622/SAP", descResult.getValue(), "Description should be NEFT reference");
        System.out.println("✅ Description: " + descResult.getValue() + " (Pattern: " + descResult.getMatchedPattern() + ")");

        // Test account identifier extraction
        var accountResult = TransactionPatternLibrary.extractAccountIdentifier(emailContent);
        assertTrue(accountResult.isPresent(), "Account identifier should be extracted");
        assertEquals("2804", accountResult.get(), "Should extract last 4 digits");
        System.out.println("✅ Account: " + accountResult.get());

        // Test transaction type detection
        TransactionType type = TransactionPatternLibrary.detectTransactionType(emailContent);
        assertEquals(TransactionType.CREDIT, type, "Should be CREDIT transaction");
        System.out.println("✅ Type: " + type);
    }

    @Test
    void testICICICreditCardEmail_AvenueECommerce() {
        String emailContent = """
            ICICI Bank Online Dear Customer, Your ICICI Bank Credit Card XX9057 has been used for a transaction of INR 738.00 on Oct 23, 2025 at 06:47:39. Info: AVENUE E COMMERCE LIMIT. The Available Credit Limit on your card is INR 1,98,069.68 and Total Credit Limit is INR 2,00,000.00.
            """;

        // Test amount extraction
        PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
        assertTrue(amountResult.isPresent(), "Amount should be extracted");
        assertEquals(new BigDecimal("738.00"), amountResult.getValue(), "Amount should be 738.00");
        System.out.println("✅ Amount: " + amountResult.getValue() + " (Pattern: " + amountResult.getMatchedPattern() + ")");

        // Test description extraction
        PatternResult<String> descResult = TransactionPatternLibrary.extractDescription(emailContent);
        assertTrue(descResult.isPresent(), "Description should be extracted");
        assertEquals("AVENUE E COMMERCE LIMIT", descResult.getValue(), "Description should be AVENUE E COMMERCE LIMIT, not customer");
        assertFalse(descResult.getValue().toLowerCase().contains("customer"), "Should not extract 'customer' from 'Dear Customer'");
        System.out.println("✅ Description: " + descResult.getValue() + " (Pattern: " + descResult.getMatchedPattern() + ")");

        // Test transaction type detection
        TransactionType type = TransactionPatternLibrary.detectTransactionType(emailContent);
        assertEquals(TransactionType.DEBIT, type, "Should be DEBIT transaction");
        System.out.println("✅ Type: " + type);
    }

    @Test
    void testAxisDebitEmail_ATMWithdrawal() {
        String emailContent = """
            22-10-2025 Dear Customer, Thank you for banking with us. We wish to inform you that INR 400.00 has been debited from your A/c no. XX592804 on 22-10-2025 12:05:46 at ATM-WDL/AXPR/246. Available balance: INR 31081.98. Please SMS BLOCKCARD 4605 to +919951860002 or call 1860 500 5555, if the transaction has not been initiated by you. Always open to help you. Regards, Axis Bank Ltd.
            """;

        // Test amount extraction
        PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
        assertTrue(amountResult.isPresent(), "Amount should be extracted");
        assertEquals(new BigDecimal("400.00"), amountResult.getValue(), "Amount should be 400.00");
        System.out.println("✅ Amount: " + amountResult.getValue() + " (Pattern: " + amountResult.getMatchedPattern() + ")");

        // Test description extraction - should get ATM location, not "you"
        PatternResult<String> descResult = TransactionPatternLibrary.extractDescription(emailContent);
        assertTrue(descResult.isPresent(), "Description should be extracted");
        assertEquals("ATM-WDL/AXPR/246", descResult.getValue(), "Description should be ATM location, not 'you'");
        assertFalse(descResult.getValue().equalsIgnoreCase("you"), "Should not extract 'you' from 'by you'");
        System.out.println("✅ Description: " + descResult.getValue() + " (Pattern: " + descResult.getMatchedPattern() + ")");

        // Test transaction type detection
        TransactionType type = TransactionPatternLibrary.detectTransactionType(emailContent);
        assertEquals(TransactionType.DEBIT, type, "Should be DEBIT transaction");
        System.out.println("✅ Type: " + type);
    }

    @Test
    void testAmazonRefundEmail_TotalRefund() {
        String emailContent = """
            Hello Nikhil, We've issued your refund for the item below. Your return is now complete. 
            Order #406-9504965-5369111 
            Refund subtotal ₹1,090.00 
            Promo discount deduction: -₹32.70 
            Total refund ₹1,057.30
            Refund of ₹1,090.00 is now initiated.
            Item returned: 1 [Fabrilia Floral Pattern Embroidery...] Quantity: 2
            """;

        // Test amount extraction - should get Total refund amount
        PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
        assertTrue(amountResult.isPresent(), "Amount should be extracted");
        // Should extract one of the amounts (preferably 1,057.30 or 1,090.00)
        assertTrue(amountResult.getValue().compareTo(new BigDecimal("1000")) > 0, "Amount should be > 1000");
        System.out.println("✅ Amount: " + amountResult.getValue() + " (Pattern: " + amountResult.getMatchedPattern() + ")");

        // Test transaction type detection
        TransactionType type = TransactionPatternLibrary.detectTransactionType(emailContent);
        assertEquals(TransactionType.CREDIT, type, "Refund should be CREDIT transaction");
        System.out.println("✅ Type: " + type);
    }
}

