package com.nklmthr.finance.personal.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.service.AccountService;

/**
 * Test that declined/failed transactions are NOT extracted
 */
public class DeclinedTransactionTest {

    @Test
    void testICICI_DeclinedTransaction_NotExtracted() {
        // The actual declined transaction email from production
        String declinedEmail = """
            Dear Customer, 
            Transaction attempted through your ICICI Bank Credit Card XX9057 has been declined due to incorrect PIN. 
            To reset PIN, Please use Internet Banking/iMobile Pay/IVR. 
            Sincerely, Team ICICI Bank 
            This is an auto-generated mail. Please do not reply.
            """;

        ICICICCDataExtractionServiceImpl extractor = new ICICICCDataExtractionServiceImpl();
        extractor.accountService = Mockito.mock(AccountService.class);
        
        AccountTransaction tx = new AccountTransaction();
        AppUser user = new AppUser();
        
        AccountTransaction result = extractor.extractTransactionData(tx, declinedEmail, user);
        
        // Should return null (skipped)
        assertNull(result, "Declined transaction should return null and not be saved");
        System.out.println("✅ ICICI declined transaction correctly skipped");
    }

    @Test
    void testAxis_DeclinedTransaction_NotExtracted() {
        String declinedEmail = """
            Transaction attempted on your Axis Bank Credit Card XX0434 has been declined due to incorrect PIN.
            Please reset your PIN through Internet Banking.
            """;

        AxisCCDataExtractionService extractor = new AxisCCDataExtractionService();
        extractor.accountService = Mockito.mock(AccountService.class);
        
        AccountTransaction tx = new AccountTransaction();
        AppUser user = new AppUser();
        
        AccountTransaction result = extractor.extractTransactionData(tx, declinedEmail, user);
        
        assertNull(result, "Declined transaction should return null");
        System.out.println("✅ Axis declined transaction correctly skipped");
    }

    @Test
    void testSBI_DeclinedTransaction_NotExtracted() {
        String declinedEmail = """
            Your transaction on SBI Credit Card has been declined.
            Reason: Incorrect PIN entered.
            """;

        SBICCDataExtractionServiceImpl extractor = new SBICCDataExtractionServiceImpl();
        extractor.accountService = Mockito.mock(AccountService.class);
        
        AccountTransaction tx = new AccountTransaction();
        AppUser user = new AppUser();
        
        AccountTransaction result = extractor.extractTransactionData(tx, declinedEmail, user);
        
        assertNull(result, "Declined transaction should return null");
        System.out.println("✅ SBI declined transaction correctly skipped");
    }

    @Test
    void testICICI_SuccessfulTransaction_IsExtracted() {
        // This is a SUCCESSFUL transaction - should be extracted
        String successEmail = """
            Dear Customer,
            Transaction alert for your ICICI Bank Credit Card XX9057
            INR 1250.50 on Oct 24, 2025 at 15:30:00. 
            Info: AMAZON PAYMENTS
            """;

        ICICICCDataExtractionServiceImpl extractor = new ICICICCDataExtractionServiceImpl();
        extractor.accountService = Mockito.mock(AccountService.class);
        
        Mockito.when(extractor.accountService.getAccountByName(Mockito.anyString(), Mockito.any()))
            .thenReturn(null); // Mock returns null, that's okay for this test
        
        AccountTransaction tx = new AccountTransaction();
        AppUser user = new AppUser();
        
        AccountTransaction result = extractor.extractTransactionData(tx, successEmail, user);
        
        // Should NOT be null (successful transaction)
        assertNotNull(result, "Successful transaction should NOT be null");
        System.out.println("✅ ICICI successful transaction correctly extracted");
    }

    @Test
    void testVariousDeclinedKeywords() {
        String[] declinedEmails = {
            "Your transaction has been declined",
            "Transaction declined due to insufficient funds",
            "Transaction failed - please try again",
            "Card declined due to incorrect PIN",
            "Payment has been declined by the bank"
        };

        ICICICCDataExtractionServiceImpl extractor = new ICICICCDataExtractionServiceImpl();
        extractor.accountService = Mockito.mock(AccountService.class);
        
        for (String email : declinedEmails) {
            AccountTransaction result = extractor.extractTransactionData(
                new AccountTransaction(), email, new AppUser());
            assertNull(result, "Email with '" + email + "' should be skipped");
        }
        
        System.out.println("✅ All declined keyword variations correctly detected");
    }
}

