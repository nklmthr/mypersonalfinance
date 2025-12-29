package com.nklmthr.finance.personal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.nklmthr.finance.personal.dto.InstitutionDTO;
import com.nklmthr.finance.personal.model.Institution;

class InstitutionMapperTest {

    private InstitutionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(InstitutionMapper.class);
    }

    @Test
    void mapsEntityToDto() {
        Institution entity = Institution.builder().id("i1").name("Bank").description("d").build();
        InstitutionDTO dto = mapper.toDTO(entity);
        assertThat(dto.id()).isEqualTo("i1");
        assertThat(dto.name()).isEqualTo("Bank");
        assertThat(dto.description()).isEqualTo("d");
    }

    @Test
    void mapsDtoToEntity_ignoresAppUser() {
        InstitutionDTO dto = new InstitutionDTO("i1","Bank","d");
        Institution entity = mapper.toEntity(dto);
        assertThat(entity.getId()).isEqualTo("i1");
        assertThat(entity.getAppUser()).isNull();
    }
}
