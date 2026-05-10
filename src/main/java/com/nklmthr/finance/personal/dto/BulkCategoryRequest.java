package com.nklmthr.finance.personal.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BulkCategoryRequest {

	@NotEmpty(message = "transactionIds is required")
	@Size(max = 500, message = "Cannot bulk update more than 500 transactions in a single request")
	private List<String> transactionIds;

	@NotBlank(message = "categoryId is required")
	private String categoryId;
}
