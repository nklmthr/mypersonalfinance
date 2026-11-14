package com.nklmthr.finance.personal.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequest {
    private String sourceTransactionId;
    @NotEmpty(message = "At least one destination is required")
    private List<TransferDestination> destinations;
}
