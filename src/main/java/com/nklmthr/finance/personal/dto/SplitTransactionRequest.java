package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.nklmthr.finance.personal.enums.TransactionType;

import lombok.Data;

@Data
public class SplitTransactionRequest {
    private String description;
    private BigDecimal amount;
    private LocalDateTime date;
    private TransactionType type;
    private AccountIdWrapper account;
    private CategoryIdWrapper category;
    private String parentId;

    @Data
    public static class AccountIdWrapper {
        private String id;
    }

    @Data
    public static class CategoryIdWrapper {
        private String id;
    }
}
