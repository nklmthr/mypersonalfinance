package com.nklmthr.finance.personal.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
import com.nklmthr.finance.personal.dto.AccountTypeDTO;
import com.nklmthr.finance.personal.service.AccountTypeService;
import com.nklmthr.finance.personal.security.SecurityConfig;
import com.nklmthr.finance.personal.security.JwtAuthenticationFilter;

@WebMvcTest(controllers = AccountTypeController.class,
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
class AccountTypeControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AccountTypeService service;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @Autowired ObjectMapper objectMapper;

    @Test
    void create_returns200() throws Exception {
        AccountTypeDTO dto = new AccountTypeDTO(null, "Savings", null, null, BigDecimal.ZERO);
        AccountTypeDTO created = new AccountTypeDTO("id1", "Savings", null, null, BigDecimal.ZERO);
        when(service.create(Mockito.any())).thenReturn(created);
        mvc.perform(post("/api/account-types").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("id1"));
    }

    @Test
    void getAll_returnsList() throws Exception {
        when(service.getAll()).thenReturn(List.of(new AccountTypeDTO("id1","t",null,null,null)));
        mvc.perform(get("/api/account-types"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getById_found() throws Exception {
        when(service.getById("id1")).thenReturn(Optional.of(new AccountTypeDTO("id1","t",null,null,null)));
        mvc.perform(get("/api/account-types/id1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("id1"));
    }

    @Test
    void getById_notFound() throws Exception {
        when(service.getById("missing")).thenReturn(Optional.empty());
        mvc.perform(get("/api/account-types/missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getByName_found() throws Exception {
        when(service.getByName("t")).thenReturn(Optional.of(new AccountTypeDTO("id1","t",null,null,null)));
        mvc.perform(get("/api/account-types/name/t"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("t"));
    }

    @Test
    void getByName_notFound() throws Exception {
        when(service.getByName("x")).thenReturn(Optional.empty());
        mvc.perform(get("/api/account-types/name/x"))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_returns200() throws Exception {
        AccountTypeDTO updated = new AccountTypeDTO("id1","n",null,null,null);
        when(service.update(Mockito.eq("id1"), Mockito.any())).thenReturn(updated);
        mvc.perform(put("/api/account-types/id1").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updated)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("n"));
    }

    @Test
    void delete_returns204() throws Exception {
        mvc.perform(delete("/api/account-types/id1"))
            .andExpect(status().isNoContent());
    }
}
