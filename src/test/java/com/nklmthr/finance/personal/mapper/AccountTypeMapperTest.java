package com.nklmthr.finance.personal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.nklmthr.finance.personal.dto.AccountTypeDTO;
import com.nklmthr.finance.personal.model.AccountType;

class AccountTypeMapperTest {

    private AccountTypeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(AccountTypeMapper.class);
    }

    @Test
    void mapsEntityToDto() {
        AccountType entity = AccountType.builder()
            .id("t1").name("Savings").description("desc").classification("ASSET")
            .accountTypeBalance(new BigDecimal("123.45")).build();

        AccountTypeDTO dto = mapper.toDTO(entity);
        assertThat(dto.id()).isEqualTo("t1");
        assertThat(dto.name()).isEqualTo("Savings");
        assertThat(dto.description()).isEqualTo("desc");
        assertThat(dto.classification()).isEqualTo("ASSET");
        assertThat(dto.accountTypeBalance()).isEqualByComparingTo("123.45");
    }

    @Test
    void mapsDtoToEntity_ignoresAppUser() {
        AccountTypeDTO dto = new AccountTypeDTO("t1","Savings","desc","ASSET", new BigDecimal("10"));
        AccountType entity = mapper.toEntity(dto);
        assertThat(entity.getId()).isEqualTo("t1");
        assertThat(entity.getAppUser()).isNull();
    }
}
