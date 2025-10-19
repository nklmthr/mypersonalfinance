package com.nklmthr.finance.personal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.model.Category;

class CategoryMapperTest {

    private final CategoryMapper mapper = Mappers.getMapper(CategoryMapper.class);

    @Test
    void mapsParentFields() {
        Category c = new Category(); c.setId("c1"); c.setName("Food"); c.setParent("p1");
        CategoryDTO dto = mapper.toDTO(c);
        assertThat(dto.getId()).isEqualTo("c1");
        assertThat(dto.getParentId()).isEqualTo("p1");
    }

    @Test
    void toEntity_ignoresAppUser() {
        CategoryDTO dto = new CategoryDTO(); dto.setId("c1"); dto.setParentId("p1");
        Category e = mapper.toEntity(dto);
        assertThat(e.getParent()).isEqualTo("p1");
        assertThat(e.getAppUser()).isNull();
    }
}


