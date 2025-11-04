package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.nklmthr.finance.personal.enums.TransactionType;

public record AccountTransactionDTO(
	String id,
	LocalDateTime date,
	BigDecimal amount,
	String description,
	String shortDescription,
	String explanation,
	String shortExplanation,
	TransactionType type,
	AccountDTO account,
	CategoryDTO category,
	String parentId,
	List<AccountTransactionDTO> children,
	String linkedTransferId,
	// GPT fields
	BigDecimal gptAmount,
	String gptDescription,
	String gptExplanation,
	TransactionType gptType,
	String currency,
	AccountDTO gptAccount,
	String gptCurrency,
	List<LabelDTO> labels
) {
	public String getShortDescription() {
		// Use only description field - GPT fields are only for comparison, not display
		return description != null ? description.length() > 40 ? description.substring(0, 40) + "..." : description
				: null;
	}

	public String getShortExplanation() {
		// Use only explanation field - GPT fields are only for comparison, not display
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
