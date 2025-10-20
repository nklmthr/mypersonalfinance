package com.nklmthr.finance.personal.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.service.CategoryService;
import com.nklmthr.finance.personal.security.SecurityConfig;
import com.nklmthr.finance.personal.security.JwtAuthenticationFilter;

@WebMvcTest(controllers = CategoryController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
    excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    OAuth2ClientAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class
})
@ActiveProfiles("dev")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@ExtendWith(SpringExtension.class)
class CategoryControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CategoryService categoryService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void getAll_returnsList() throws Exception {
        when(categoryService.getAllCategories()).thenReturn(List.of(new CategoryDTO()));
        mvc.perform(get("/api/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getById_found() throws Exception {
        CategoryDTO c = new CategoryDTO(); c.setId("c1");
        when(categoryService.getCategoryDTOById("c1")).thenReturn(c);
        mvc.perform(get("/api/categories/c1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("c1"));
    }

    @Test
    void getById_notFound() throws Exception {
        when(categoryService.getCategoryDTOById("x")).thenReturn(null);
        mvc.perform(get("/api/categories/x"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getChildren_returnsList() throws Exception {
        when(categoryService.getChildrenDTO("c1")).thenReturn(List.of(new CategoryDTO()));
        mvc.perform(get("/api/categories/c1/children"))
            .andExpect(status().isOk());
    }

    @Test
    void create_returns200() throws Exception {
        CategoryDTO in = new CategoryDTO(); in.setName("Food");
        CategoryDTO out = new CategoryDTO(); out.setId("c1"); out.setName("Food");
        when(categoryService.saveCategory(Mockito.any(CategoryDTO.class))).thenReturn(out);
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(in)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("c1"));
    }

    @Test
    void update_returns200WhenExists() throws Exception {
        // Existence check still uses entity path
        Category existing = new Category(); existing.setId("c1"); existing.setName("Old");
        when(categoryService.getCategoryById("c1")).thenReturn(existing);

        CategoryDTO updated = new CategoryDTO(); updated.setId("c1"); updated.setName("New");
        when(categoryService.saveCategory(Mockito.any(CategoryDTO.class))).thenReturn(updated);

        mvc.perform(put("/api/categories/c1").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updated)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("New"));
    }

    @Test
    void update_returns404WhenMissing() throws Exception {
        Category updated = new Category(); updated.setId("c1"); updated.setName("New");
        when(categoryService.getCategoryById("c1")).thenReturn(null);
        mvc.perform(put("/api/categories/c1").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updated)))
            .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        mvc.perform(delete("/api/categories/c1"))
            .andExpect(status().isNoContent());
    }
}


