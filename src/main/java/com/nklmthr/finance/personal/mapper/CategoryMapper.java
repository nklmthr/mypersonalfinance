package com.nklmthr.finance.personal.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.model.Category;

@Mapper(componentModel = "spring")
public interface CategoryMapper extends GenericMapper<CategoryDTO, Category> {

	@Override
	@Mapping(target = "parentId", source = "parent.id")
	@Mapping(target = "children", ignore = true)
	CategoryDTO toDTO(Category entity);

	@Mapping(target = "appUser", ignore = true)
	@Mapping(target = "parent.id", source = "parentId")
	Category toEntity(CategoryDTO dto);
}
