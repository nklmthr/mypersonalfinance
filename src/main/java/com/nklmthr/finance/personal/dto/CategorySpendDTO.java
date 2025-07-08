package com.nklmthr.finance.personal.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorySpendDTO {
	private String categoryId;
	private String categoryName;
	private BigDecimal amount;
	private List<CategorySpendDTO> children;
}
