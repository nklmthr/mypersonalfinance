package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;

import com.nklmthr.finance.personal.enums.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictedTransactionDTO {
	private String id;
	private String predictionRuleId;
	private String categoryId;
	private String categoryName;
	private String accountId;
	private String accountName;
	private BigDecimal predictedAmount;
	private BigDecimal remainingAmount;
	private BigDecimal actualSpent;
	private TransactionType transactionType;
	private String predictionMonth;
	private String description;
	private String explanation;
	private String currency;
	private String calculationDate;
	private int basedOnTransactionCount;
	private boolean visible;
}

