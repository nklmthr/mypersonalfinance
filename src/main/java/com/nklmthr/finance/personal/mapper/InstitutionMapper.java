package com.nklmthr.finance.personal.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.nklmthr.finance.personal.dto.InstitutionDTO;
import com.nklmthr.finance.personal.model.Institution;

@Mapper(componentModel = "spring")
public interface InstitutionMapper extends GenericMapper<InstitutionDTO, Institution> {

	@Override
	InstitutionDTO toDTO(Institution entity);

	@Mapping(target = "appUser", ignore = true)
	Institution toEntity(InstitutionDTO dto);
}
