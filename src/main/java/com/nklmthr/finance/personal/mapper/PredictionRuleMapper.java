package com.nklmthr.finance.personal.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.nklmthr.finance.personal.dto.PredictionRuleDTO;
import com.nklmthr.finance.personal.model.PredictionRule;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PredictionRuleMapper {

	@Mapping(source = "category.id", target = "categoryId")
	@Mapping(source = "category.name", target = "categoryName")
	PredictionRuleDTO toDTO(PredictionRule rule);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "appUser", ignore = true)
	@Mapping(target = "category", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	PredictionRule toEntity(PredictionRuleDTO dto);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "appUser", ignore = true)
	@Mapping(target = "category", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	void updateEntityFromDTO(PredictionRuleDTO dto, @MappingTarget PredictionRule rule);
}

