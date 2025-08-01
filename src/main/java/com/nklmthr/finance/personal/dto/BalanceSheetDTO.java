package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSheetDTO {
	private List<BalanceSheetMonthlyDTO> rows;
	private Map<String, BigDecimal> summaryByMonth; // Totals for each month
}
