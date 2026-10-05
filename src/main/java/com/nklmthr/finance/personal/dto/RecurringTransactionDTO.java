package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;

import com.nklmthr.finance.personal.enums.RecurrenceType;
import com.nklmthr.finance.personal.enums.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringTransactionDTO {
	private String id;
	private String description;
	private String categoryId;
	private String categoryName;
	private BigDecimal amount;
	private TransactionType transactionType;
	private RecurrenceType recurrenceType;
	private String activeMonths;
	private int dayOfMonth;
	private String startYearMonth;
	private String endYearMonth;
	private boolean enabled;
}
