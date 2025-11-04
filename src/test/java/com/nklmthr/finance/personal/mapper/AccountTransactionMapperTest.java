package com.nklmthr.finance.personal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.dto.AccountTransactionDTO;
import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.dto.LabelDTO;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.model.Label;

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
        ReflectionTestUtils.setField(mapper, "labelMapper", Mappers.getMapper(LabelMapper.class));
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
        AccountTransactionDTO dto = new AccountTransactionDTO("t1", LocalDateTime.now(), new BigDecimal("10"), "d", null, null, null, TransactionType.CREDIT, a, c, "p1", java.util.List.of(), "linked1", null, null, null, null, null, null, null, null);
        AccountTransaction entity = mapper.toEntity(dto);
        assertThat(entity.getParent()).isEqualTo("p1");
        assertThat(entity.getLinkedTransferId()).isEqualTo("linked1");
        assertThat(entity.getAppUser()).isNull();
    }

    @Test
    void toDTO_mapsLabels() {
        Account acc = new Account();
        acc.setId("a1");
        Category cat = new Category();
        cat.setId("c1");
        
        AppUser user = new AppUser();
        user.setId("u1");
        user.setUsername("testuser");
        
        Label label1 = new Label();
        label1.setId("l1");
        label1.setName("Food");
        label1.setAppUser(user);
        
        Label label2 = new Label();
        label2.setId("l2");
        label2.setName("Travel");
        label2.setAppUser(user);
        
        AccountTransaction tx = AccountTransaction.builder()
            .id("t1")
            .date(LocalDateTime.now())
            .amount(new BigDecimal("10"))
            .description("d")
            .type(TransactionType.DEBIT)
            .account(acc)
            .category(cat)
            .appUser(user)
            .build();
        
        // Use helper method to add labels
        tx.setLabels(List.of(label1, label2), user);

        AccountTransactionDTO dto = mapper.toDTO(tx);
        
        assertThat(dto.id()).isEqualTo("t1");
        assertThat(dto.labels()).isNotNull();
        assertThat(dto.labels()).hasSize(2);
        assertThat(dto.labels()).extracting(LabelDTO::name).containsExactly("Food", "Travel");
    }

    @Test
    void toEntity_mapsLabels() {
        AccountDTO a = new AccountDTO("a1", "A", BigDecimal.ZERO, null, null, null, null, null);
        CategoryDTO c = new CategoryDTO();
        c.setId("c1");
        
        LabelDTO label1 = new LabelDTO("l1", "Food");
        LabelDTO label2 = new LabelDTO("l2", "Travel");
        
        AccountTransactionDTO dto = new AccountTransactionDTO(
            "t1",
            LocalDateTime.now(),
            new BigDecimal("10"),
            "d",
            null,
            null,
            null,
            TransactionType.CREDIT,
            a,
            c,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(label1, label2)
        );
        
        AccountTransaction entity = mapper.toEntity(dto);
        
        // Note: Labels are now handled via transactionLabels and processed in service layer
        // The mapper ignores transactionLabels, so labels won't be mapped here
        // This is by design - labels are managed through helper methods in the service
        assertThat(entity.getTransactionLabels()).isEmpty();
        assertThat(entity.getLabels()).isEmpty();
    }

    @Test
    void toDTO_withNullLabels_handlesGracefully() {
        Account acc = new Account();
        acc.setId("a1");
        Category cat = new Category();
        cat.setId("c1");
        
        AppUser user = new AppUser();
        user.setId("u1");
        
        AccountTransaction tx = AccountTransaction.builder()
            .id("t1")
            .date(LocalDateTime.now())
            .amount(new BigDecimal("10"))
            .description("d")
            .type(TransactionType.DEBIT)
            .account(acc)
            .category(cat)
            .appUser(user)
            .build();

        AccountTransactionDTO dto = mapper.toDTO(tx);
        
        assertThat(dto.id()).isEqualTo("t1");
        // Labels list should be empty by default (from helper method)
        assertThat(dto.labels()).isEmpty();
    }
}


