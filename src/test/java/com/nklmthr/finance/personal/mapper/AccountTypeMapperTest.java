package com.nklmthr.finance.personal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.nklmthr.finance.personal.dto.AccountTypeDTO;
import com.nklmthr.finance.personal.model.AccountType;

@ExtendWith(SpringExtension.class)
class AccountTypeMapperTest {

    @Autowired
    private AccountTypeMapper mapper;

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
