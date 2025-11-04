package com.nklmthr.finance.personal.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.nklmthr.finance.personal.dto.LabelDTO;
import com.nklmthr.finance.personal.model.Label;

@Mapper(componentModel = "spring")
public interface LabelMapper extends GenericMapper<LabelDTO, Label> {

	@Override
	LabelDTO toDTO(Label entity);

	@Mapping(target = "appUser", ignore = true)
	Label toEntity(LabelDTO dto);
}

