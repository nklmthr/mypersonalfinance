package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.nklmthr.finance.personal.enums.TransactionType;

public record AccountTransactionDTO(String id, LocalDateTime date, BigDecimal amount, String description,
		String shortDescription, String explanation, String shortExplanation, TransactionType type, 
		AccountDTO account, CategoryDTO category, String parentId, List<AccountTransactionDTO> children) {

}
