package com.nklmthr.finance.personal.scheduler.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.nklmthr.finance.personal.enums.TransactionType;

/**
 * Comprehensive tests for TransactionPatternLibrary.
 * Tests all pattern variations against real bank email formats.
 */
class TransactionPatternLibraryTest {

    // ==================== AMOUNT EXTRACTION TESTS ====================

    @Test
    void extractAmount_standardINRFormat() {
        String content = "Transaction Amount: INR 340.50 has been debited";
        PatternResult<BigDecimal> result = TransactionPatternLibrary.extractAmount(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualByComparingTo("340.50");
        assertThat(result.hasHighConfidence()).isTrue();
        assertThat(result.getMatchedPattern()).contains("AMOUNT");
    }

    @Test
    void extractAmount_withCommas() {
        String content = "INR 1,340.50 spent on credit card";
        PatternResult<BigDecimal> result = TransactionPatternLibrary.extractAmount(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualByComparingTo("1340.50");
    }

    @Test
    void extractAmount_rsFormat() {
        String content = "Rs.100 spent at merchant";
        PatternResult<BigDecimal> result = TransactionPatternLibrary.extractAmount(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualByComparingTo("100");
    }

    @Test
    void extractAmount_rsWithSpace() {
        String content = "Rs 250.00 has been debited from your account";
        PatternResult<BigDecimal> result = TransactionPatternLibrary.extractAmount(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualByComparingTo("250.00");
    }

    @Test
    void extractAmount_rupeeSymbol() {
        String content = "Received Amount ₹500.00 credited to your account";
        PatternResult<BigDecimal> result = TransactionPatternLibrary.extractAmount(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualByComparingTo("500.00");
    }

    @Test
    void extractAmount_amountDebited() {
        String content = "Amount Debited: INR 75.50 from your account";
        PatternResult<BigDecimal> result = TransactionPatternLibrary.extractAmount(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualByComparingTo("75.50");
    }

    @Test
    void extractAmount_amountCredited() {
        String content = "Amount Credited: INR 1000.00 to your account";
        PatternResult<BigDecimal> result = TransactionPatternLibrary.extractAmount(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualByComparingTo("1000.00");
    }

    @Test
    void extractAmount_forINR() {
        String content = "Transaction for INR 99.99 at TestMerchant";
        PatternResult<BigDecimal> result = TransactionPatternLibrary.extractAmount(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualByComparingTo("99.99");
    }

    @Test
    void extractAmount_reverseFormat() {
        String content = "340.50 has been debited from your account";
        PatternResult<BigDecimal> result = TransactionPatternLibrary.extractAmount(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualByComparingTo("340.50");
    }

    @Test
    void extractAmount_withINR() {
        String content = "Your account has been debited with INR 150.00";
        PatternResult<BigDecimal> result = TransactionPatternLibrary.extractAmount(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualByComparingTo("150.00");
    }

    @Test
    void extractAmount_largeAmountWithCommas() {
        String content = "INR 1,23,456.78 has been debited";
        PatternResult<BigDecimal> result = TransactionPatternLibrary.extractAmount(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualByComparingTo("123456.78");
    }

    @Test
    void extractAmount_noMatch() {
        String content = "This email contains no amount information";
        PatternResult<BigDecimal> result = TransactionPatternLibrary.extractAmount(content);
        
        assertThat(result.isPresent()).isFalse();
        assertThat(result.getConfidenceScore()).isEqualTo(0);
    }

    // ==================== DESCRIPTION/MERCHANT EXTRACTION TESTS ====================

    @Test
    void extractDescription_atMerchantOn() {
        String content = "Transaction at TESTMERCHANT on 28-09-2025";
        PatternResult<String> result = TransactionPatternLibrary.extractDescription(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualTo("TESTMERCHANT");
    }

    @Test
    void extractDescription_merchantName() {
        String content = "Merchant Name: Amazon India";
        PatternResult<String> result = TransactionPatternLibrary.extractDescription(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualTo("Amazon India");
    }

    @Test
    void extractDescription_transactionInfo() {
        String content = "Transaction Info: SHASHIKALAKUMARI UPI payment";
        PatternResult<String> result = TransactionPatternLibrary.extractDescription(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).contains("SHASHIKALAKUMARI");
    }

    @Test
    void extractDescription_byPayee() {
        String content = "Amount credited by Ganesh S N on 28-09-2025";
        PatternResult<String> result = TransactionPatternLibrary.extractDescription(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualTo("Ganesh S N");
    }

    @Test
    void extractDescription_upiMerchant() {
        String content = "UPI/P2A/123456/merchant@paytm transaction completed";
        PatternResult<String> result = TransactionPatternLibrary.extractDescription(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).contains("merchant@paytm");
    }

    @Test
    void extractDescription_referenceNo() {
        String content = "Reference no. - 123456789 for your transaction";
        PatternResult<String> result = TransactionPatternLibrary.extractDescription(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualTo("123456789");
    }

    @Test
    void extractDescription_withFooterRemoved() {
        String content = "Transaction Info: TestMerchant Regards, Customer Service";
        PatternResult<String> result = TransactionPatternLibrary.extractDescription(content);
        
        assertThat(result.isPresent()).isTrue();
        assertThat(result.getValue()).isEqualTo("TestMerchant");
        assertThat(result.getValue()).doesNotContain("Regards");
    }

    @Test
    void extractDescription_noMatch() {
        String content = "No merchant information in this email";
        PatternResult<String> result = TransactionPatternLibrary.extractDescription(content);
        
        assertThat(result.isPresent()).isFalse();
    }

    // ==================== ACCOUNT IDENTIFIER TESTS ====================

    @Test
    void extractAccountIdentifier_xxFormat() {
        String content = "Transaction on card XX2804";
        Optional<String> result = TransactionPatternLibrary.extractAccountIdentifier(content);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("2804");
    }

    @Test
    void extractAccountIdentifier_endingWith() {
        String content = "Account ending with 2606";
        Optional<String> result = TransactionPatternLibrary.extractAccountIdentifier(content);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("2606");
    }

    @Test
    void extractAccountIdentifier_accountNumber() {
        String content = "A/c no. 123456789 has been debited";
        Optional<String> result = TransactionPatternLibrary.extractAccountIdentifier(content);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("123456789");
    }

    @Test
    void extractAccountIdentifier_creditCard() {
        String content = "Credit Card no. XX9057";
        Optional<String> result = TransactionPatternLibrary.extractAccountIdentifier(content);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("9057");
    }

    // ==================== TRANSACTION TYPE DETECTION TESTS ====================

    @Test
    void detectTransactionType_credit() {
        String content = "Amount credited to your account INR 500.00";
        TransactionType type = TransactionPatternLibrary.detectTransactionType(content);
        
        assertThat(type).isEqualTo(TransactionType.CREDIT);
    }

    @Test
    void detectTransactionType_debit() {
        String content = "Amount debited from your account INR 100.00";
        TransactionType type = TransactionPatternLibrary.detectTransactionType(content);
        
        assertThat(type).isEqualTo(TransactionType.DEBIT);
    }

    @Test
    void detectTransactionType_creditNotification() {
        String content = "Credit notification from Axis Bank - INR 1000 received";
        TransactionType type = TransactionPatternLibrary.detectTransactionType(content);
        
        assertThat(type).isEqualTo(TransactionType.CREDIT);
    }

    @Test
    void detectTransactionType_debitWithSpent() {
        String content = "Transaction Amount: INR 340.50 spent at merchant";
        TransactionType type = TransactionPatternLibrary.detectTransactionType(content);
        
        assertThat(type).isEqualTo(TransactionType.DEBIT);
    }

    @Test
    void detectTransactionType_refund() {
        String content = "Refund of Rs.250 has been processed to your account";
        TransactionType type = TransactionPatternLibrary.detectTransactionType(content);
        
        assertThat(type).isEqualTo(TransactionType.CREDIT);
    }

    @Test
    void detectTransactionType_cashback() {
        String content = "Cashback of Rs.50 credited to your account";
        TransactionType type = TransactionPatternLibrary.detectTransactionType(content);
        
        assertThat(type).isEqualTo(TransactionType.CREDIT);
    }

    @Test
    void detectTransactionType_defaultsToDebit() {
        String content = "Transaction of Rs.100 at merchant";
        TransactionType type = TransactionPatternLibrary.detectTransactionType(content);
        
        assertThat(type).isEqualTo(TransactionType.DEBIT);
    }

    // ==================== UPI REFERENCE TESTS ====================

    @Test
    void extractUpiReference_upiRef() {
        String content = "UPI Ref 123456789 for your transaction";
        Optional<String> result = TransactionPatternLibrary.extractUpiReference(content);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("123456789");
    }

    @Test
    void extractUpiReference_upiId() {
        String content = "UPI/P2A/123456/merchant@upi";
        Optional<String> result = TransactionPatternLibrary.extractUpiReference(content);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("123456");
    }

    @Test
    void extractUpiReference_noMatch() {
        String content = "No UPI information in this transaction";
        Optional<String> result = TransactionPatternLibrary.extractUpiReference(content);
        
        assertThat(result).isEmpty();
    }

    // ==================== INTEGRATION TESTS (Real Email Formats) ====================

    @Test
    void realEmail_axisCC() {
        String content = "Transaction Amount: INR 340.50 on your Axis Bank Credit Card no. XX0434 at TESTMERCHANT on 28-09-2025";
        
        PatternResult<BigDecimal> amount = TransactionPatternLibrary.extractAmount(content);
        PatternResult<String> description = TransactionPatternLibrary.extractDescription(content);
        Optional<String> account = TransactionPatternLibrary.extractAccountIdentifier(content);
        TransactionType type = TransactionPatternLibrary.detectTransactionType(content);
        
        assertThat(amount.getValue()).isEqualByComparingTo("340.50");
        assertThat(description.getValue()).contains("TESTMERCHANT");
        assertThat(account).isPresent();
        assertThat(account.get()).isEqualTo("0434");
        assertThat(type).isEqualTo(TransactionType.DEBIT);
    }

    @Test
    void realEmail_sbiCC() {
        String content = "Rs.100.00 spent on your SBI Credit Card at MERCHANT on 28-09-2025. Ref No. 123456";
        
        PatternResult<BigDecimal> amount = TransactionPatternLibrary.extractAmount(content);
        PatternResult<String> description = TransactionPatternLibrary.extractDescription(content);
        TransactionType type = TransactionPatternLibrary.detectTransactionType(content);
        
        assertThat(amount.getValue()).isEqualByComparingTo("100.00");
        assertThat(description.isPresent()).isTrue();
        assertThat(type).isEqualTo(TransactionType.DEBIT);
    }

    @Test
    void realEmail_axisCredit() {
        String content = "Amount Credited: INR 1000.00 Account Number: XX2804 Date & Time: 28-09-25, 12:48:34 IST Transaction Info: Salary Credit";
        
        PatternResult<BigDecimal> amount = TransactionPatternLibrary.extractAmount(content);
        PatternResult<String> description = TransactionPatternLibrary.extractDescription(content);
        Optional<String> account = TransactionPatternLibrary.extractAccountIdentifier(content);
        TransactionType type = TransactionPatternLibrary.detectTransactionType(content);
        
        assertThat(amount.getValue()).isEqualByComparingTo("1000.00");
        assertThat(description.getValue()).contains("Salary Credit");
        assertThat(account.get()).isEqualTo("2804");
        assertThat(type).isEqualTo(TransactionType.CREDIT);
    }

    @Test
    void realEmail_amazonRefund() {
        String content = "Refund subtotal: ₹250.00 for Order # 123-456 will be refunded to your credit card ending in 9057";
        
        PatternResult<BigDecimal> amount = TransactionPatternLibrary.extractAmount(content);
        Optional<String> account = TransactionPatternLibrary.extractAccountIdentifier(content);
        TransactionType type = TransactionPatternLibrary.detectTransactionType(content);
        
        assertThat(amount.getValue()).isEqualByComparingTo("250.00");
        assertThat(account).isPresent();
        assertThat(account.get()).isEqualTo("9057");
        assertThat(type).isEqualTo(TransactionType.CREDIT);
    }
}

