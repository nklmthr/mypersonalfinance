package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;

public record TransferSplitDTO(
    String id,
    String sourceTransactionId,
    AccountTransactionDTO destTransaction,
    BigDecimal amount,
    String explanation
) {}
