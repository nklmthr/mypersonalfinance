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
	String gptCurrency
) {
	public String getShortDescription() {
		// Prioritize GPT description over original description
		String primaryDescription = gptDescription != null && !gptDescription.trim().isEmpty() ? gptDescription : description;
		return primaryDescription != null ? primaryDescription.length() > 40 ? primaryDescription.substring(0, 40) + "..." : primaryDescription
				: null;
	}

	public String getShortExplanation() {
		// Prioritize GPT explanation over original explanation
		String primaryExplanation = gptExplanation != null && !gptExplanation.trim().isEmpty() ? gptExplanation : explanation;
		return primaryExplanation != null ? primaryExplanation.length() > 60 ? primaryExplanation.substring(0, 60) + "..." : primaryExplanation
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
