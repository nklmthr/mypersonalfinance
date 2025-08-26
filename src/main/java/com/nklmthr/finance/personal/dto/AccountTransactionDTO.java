package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.nklmthr.finance.personal.enums.TransactionType;

public record AccountTransactionDTO(String id, LocalDateTime date, BigDecimal amount, String description,
		String shortDescription, String explanation, String shortExplanation, TransactionType type, AccountDTO account,
		CategoryDTO category, String parentId, List<AccountTransactionDTO> children) {
	public String getShortDescription() {
		return description != null ? description.length() > 40 ? description.substring(0, 40) + "..." : description
				: null;
	}

	public String getShortExplanation() {
		return explanation != null ? explanation.length() > 60 ? explanation.substring(0, 60) + "..." : explanation
				: null;
	}

	public BigDecimal getAmount() {
		if (children != null && !children.isEmpty()) {
			return amount().add(
					children.stream().map(AccountTransactionDTO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
		}
		return amount;
	}

}
