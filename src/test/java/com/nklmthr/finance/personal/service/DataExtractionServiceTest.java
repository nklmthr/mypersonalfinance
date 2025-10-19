package com.nklmthr.finance.personal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.scheduler.AbstractDataExtractionService;

@ExtendWith(MockitoExtension.class)
class DataExtractionServiceTest {

    @Mock AbstractDataExtractionService svc1;
    @Mock AbstractDataExtractionService svc2;

    @InjectMocks DataExtractionService service;

    @Test
    void triggerAll_runsAllAndSummarizes() throws Exception {
        ReflectionTestUtils.setField(service, "dataExtractionServices", List.of(svc1, svc2));

        doThrow(new RuntimeException("boom")).when(svc2).run();

        CompletableFuture<String> result = service.triggerAllDataExtractionServices();
        assertThat(result.join()).contains("Success: 1, Failures: 1");
    }

    // Minimal concrete subclass example if needed by future changes
    static class DummyExtractor extends AbstractDataExtractionService {
        @Override
        protected String getSender() { return "sender@example.com"; }
        @Override
        protected java.util.List<String> getEmailSubject() { return java.util.List.of("subject"); }
        @Override
        protected AccountTransaction extractTransactionData(AccountTransaction tx, String body, AppUser user) {
            tx.setType(TransactionType.DEBIT); return tx;
        }
    }
}


