package com.nklmthr.finance.personal.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.nklmthr.finance.personal.dto.PredictedTransactionDTO;
import com.nklmthr.finance.personal.model.PredictedTransaction;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PredictedTransactionMapper {

	@Mapping(source = "predictionRule.id", target = "predictionRuleId")
	@Mapping(source = "category.id", target = "categoryId")
	@Mapping(source = "category.name", target = "categoryName")
	@Mapping(source = "account.id", target = "accountId")
	@Mapping(source = "account.name", target = "accountName")
	@Mapping(source = "calculationDate", target = "calculationDate", dateFormat = "yyyy-MM-dd'T'HH:mm:ss")
	PredictedTransactionDTO toDTO(PredictedTransaction prediction);
}

