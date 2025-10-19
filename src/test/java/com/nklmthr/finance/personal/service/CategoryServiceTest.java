package com.nklmthr.finance.personal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.mapper.CategoryMapper;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.repository.CategoryRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private AppUserService appUserService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private CategoryMapper categoryMapper;
    @InjectMocks private CategoryService service;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = AppUser.builder().id("u1").username("jane").password("p").role("USER").email("j@e.com").build();
        lenient().when(appUserService.getCurrentUser()).thenReturn(user);
        ReflectionTestUtils.setField(service, "appUserService", appUserService);
        ReflectionTestUtils.setField(service, "categoryRepository", categoryRepository);
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
    }

    @Test
    void getAllCategories_mapsToDTOs() {
        Category c = new Category(); c.setId("c1"); c.setName("Food"); c.setAppUser(user);
        when(categoryRepository.findByAppUser(user, Sort.by("name").ascending())).thenReturn(List.of(c));
        CategoryDTO dto = new CategoryDTO(); dto.setId("c1"); dto.setName("Food");
        when(categoryMapper.toDTOList(List.of(c))).thenReturn(List.of(dto));

        List<CategoryDTO> result = service.getAllCategories();
        assertThat(result).containsExactly(dto);
    }
}


