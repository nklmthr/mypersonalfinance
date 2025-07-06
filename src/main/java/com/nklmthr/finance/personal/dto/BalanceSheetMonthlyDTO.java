package com.nklmthr.finance.personal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSheetMonthlyDTO {
    private String classification;
    private Map<String, BigDecimal> balancesByMonth; // e.g., {"01-Feb-2025": 12345.67, ...}
}
