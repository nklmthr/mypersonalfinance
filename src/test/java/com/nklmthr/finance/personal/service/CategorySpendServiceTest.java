package com.nklmthr.finance.personal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklmthr.finance.personal.dto.CategorySpendDTO;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.CategoryMonthlyProjection;

@ExtendWith(MockitoExtension.class)
class CategorySpendServiceTest {

    @Mock private AppUserService appUserService;
    @Mock private CategoryService categoryService;
    @Mock private AccountTransactionRepository repo;
    @InjectMocks private CategorySpendService service;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = AppUser.builder().id("u1").username("jane").password("p").role("USER").email("j@e.com").build();
        lenient().when(appUserService.getCurrentUser()).thenReturn(user);
        ReflectionTestUtils.setField(service, "appUserService", appUserService);
        ReflectionTestUtils.setField(service, "categoryService", categoryService);
        ReflectionTestUtils.setField(service, "accountTransactionRepository", repo);
    }

    @Test
    void getCategorySpendingLastMonths_buildsHierarchyAndTotals() {
        lenient().when(categoryService.getNonClassifiedCategory()).thenReturn(dummy("nc"));
        lenient().when(categoryService.getTransferCategory()).thenReturn(dummy("tr"));
        lenient().when(categoryService.getSplitTrnsactionCategory()).thenReturn(dummy("sp"));

        LocalDate month = LocalDate.now();
        int months = 6;
        LocalDate startDate = month.withDayOfMonth(1).minusMonths(months - 1);

        CategoryMonthlyProjection p1 = projection("c1","Food", null, month.toString(), 100.0);
        CategoryMonthlyProjection p2 = projection("c2","Dining","c1", month.toString(), 50.0);
        when(repo.getCategoryMonthlySpend(user.getId(), startDate, List.of("tr","sp")))
            .thenReturn(List.of(p1, p2));

        List<CategorySpendDTO> roots = service.getCategorySpendingLastMonths(months);
        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getChildren()).extracting("id").contains("c2");
    }

    private com.nklmthr.finance.personal.model.Category dummy(String id){
        com.nklmthr.finance.personal.model.Category c = new com.nklmthr.finance.personal.model.Category();
        c.setId(id); return c;
    }

    private CategoryMonthlyProjection projection(String id, String name, String parentId, String month, Double total) {
        return new CategoryMonthlyProjection() {
            public String getCategoryId(){ return id; }
            public String getCategoryName(){ return name; }
            public String getParentId(){ return parentId; }
            public String getMonth(){ return month; }
            public Double getTotal(){ return total; }
        };
    }
}


