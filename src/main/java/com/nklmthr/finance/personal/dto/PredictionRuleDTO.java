package com.nklmthr.finance.personal.dto;

import com.nklmthr.finance.personal.enums.PredictionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionRuleDTO {
	private String id;
	private String categoryId;
	private String categoryName;
	private PredictionType predictionType;
	private boolean enabled;
	private int lookbackMonths;
	private Integer specificMonth;
}

