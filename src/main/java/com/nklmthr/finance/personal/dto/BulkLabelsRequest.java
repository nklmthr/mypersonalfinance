package com.nklmthr.finance.personal.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BulkLabelsRequest {

	public enum Mode {
		ADD,
		REPLACE
	}

	@NotEmpty(message = "transactionIds is required")
	@Size(max = 500, message = "Cannot bulk update more than 500 transactions in a single request")
	private List<String> transactionIds;

	// Labels (each entry should at least have a non-blank name; id is optional - new
	// labels are created by name on demand using LabelService.findOrCreateLabel).
	// May be empty when mode == REPLACE to clear all labels on selected transactions.
	private List<LabelDTO> labels;

	@NotNull(message = "mode is required (ADD or REPLACE)")
	private Mode mode;
}
