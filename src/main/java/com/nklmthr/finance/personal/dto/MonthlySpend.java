package com.nklmthr.finance.personal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlySpend {
    private String month; // format YYYY-MM
    private double amount;
}
