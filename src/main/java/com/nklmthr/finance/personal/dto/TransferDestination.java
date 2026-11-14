package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferDestination {
    private String accountId;
    @NotNull(message = "amount is required")
    private BigDecimal amount;
    private String explanation;
}
