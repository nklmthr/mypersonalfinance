package com.nklmthr.finance.personal.dto;

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
	private String destinationAccountId;
    private String explanation;
}
