package com.nklmthr.finance.personal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.dto.AccountTransactionDTO;
import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.Category;

class AccountTransactionMapperTest {

    private AccountTransactionMapper mapper;

    @BeforeEach
    void wireDependencies() {
        // Initialize mapper instance
        mapper = Mappers.getMapper(AccountTransactionMapper.class);
        
        // Inject required mappers since componentModel is spring
        AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);
        // Wire nested mappers used by AccountMapper
        ReflectionTestUtils.setField(accountMapper, "accountTypeMapper", Mappers.getMapper(AccountTypeMapper.class));
        ReflectionTestUtils.setField(accountMapper, "institutionMapper", Mappers.getMapper(InstitutionMapper.class));
        ReflectionTestUtils.setField(mapper, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(mapper, "categoryMapper", Mappers.getMapper(CategoryMapper.class));
    }

    @Test
    void toDTO_mapsParentAndNested() {
        Account acc = new Account(); acc.setId("a1");
        Category cat = new Category(); cat.setId("c1");
        AccountTransaction tx = AccountTransaction.builder()
            .id("t1").date(LocalDateTime.now()).amount(new BigDecimal("10"))
            .description("d").type(TransactionType.DEBIT)
            .account(acc).category(cat).parent("p1")
            .linkedTransferId("linked1")
            .currency("INR")
            .build();

        AccountTransactionDTO dto = mapper.toDTO(tx);
        assertThat(dto.id()).isEqualTo("t1");
        assertThat(dto.parentId()).isEqualTo("p1");
        assertThat(dto.linkedTransferId()).isEqualTo("linked1");
    }

    @Test
    void toEntity_mapsParentAndIgnoresAppUser() {
        AccountDTO a = new AccountDTO("a1","A", BigDecimal.ZERO, null, null, null, null, null);
        CategoryDTO c = new CategoryDTO(); c.setId("c1");
        AccountTransactionDTO dto = new AccountTransactionDTO("t1", LocalDateTime.now(), new BigDecimal("10"), "d", null, null, null, TransactionType.CREDIT, a, c, "p1", java.util.List.of(), "linked1", null, null, null, null, null, null, null);
        AccountTransaction entity = mapper.toEntity(dto);
        assertThat(entity.getParent()).isEqualTo("p1");
        assertThat(entity.getLinkedTransferId()).isEqualTo("linked1");
        assertThat(entity.getAppUser()).isNull();
    }
}


