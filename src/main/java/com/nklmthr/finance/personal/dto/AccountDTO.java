package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;

public record AccountDTO(String id, String name, BigDecimal balance, AccountTypeDTO accountType,
		InstitutionDTO institution, String accountNumber, String accountKeywords, String accountAliases) {
}
