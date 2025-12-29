package com.nklmthr.finance.personal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.nklmthr.finance.personal.dto.InstitutionDTO;
import com.nklmthr.finance.personal.model.Institution;

@ExtendWith(SpringExtension.class)
class InstitutionMapperTest {

    @Autowired
    private InstitutionMapper mapper;

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
