package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransactionPageDTO(
    List<AccountTransactionDTO> content,
    int totalPages,
    long totalElements,
    int number,
    int size,
    BigDecimal currentTotal
) {}
