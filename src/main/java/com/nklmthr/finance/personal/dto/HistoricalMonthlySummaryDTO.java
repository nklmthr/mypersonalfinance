package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;

public record HistoricalMonthlySummaryDTO(
		String month,
		String monthLabel,
		int transactionCount,
		BigDecimal totalAmount) {
}
