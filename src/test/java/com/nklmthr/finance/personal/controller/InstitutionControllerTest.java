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
import com.nklmthr.finance.personal.model.Institution;
import com.nklmthr.finance.personal.service.InstitutionService;
import com.nklmthr.finance.personal.security.SecurityConfig;
import com.nklmthr.finance.personal.security.JwtAuthenticationFilter;

@WebMvcTest(controllers = InstitutionController.class,
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
class InstitutionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean InstitutionService service;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void getAll_returnsList() throws Exception {
        Institution i = new Institution(); i.setId("i1"); i.setName("Bank");
        when(service.getAllInstitutions()).thenReturn(List.of(i));
        mvc.perform(get("/api/institutions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getById_found() throws Exception {
        Institution i = new Institution(); i.setId("i1");
        when(service.getInstitutionById("i1")).thenReturn(Optional.of(i));
        mvc.perform(get("/api/institutions/i1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("i1"));
    }

    @Test
    void getById_notFound() throws Exception {
        when(service.getInstitutionById("x")).thenReturn(Optional.empty());
        mvc.perform(get("/api/institutions/x"))
            .andExpect(status().isNotFound());
    }

    @Test
    void create_returns200() throws Exception {
        Institution in = new Institution(); in.setName("Bank");
        Institution out = new Institution(); out.setId("i1"); out.setName("Bank");
        when(service.createInstitution(Mockito.any())).thenReturn(out);
        mvc.perform(post("/api/institutions").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(in)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("i1"));
    }

    @Test
    void update_returns200() throws Exception {
        Institution in = new Institution(); in.setName("New Bank");
        when(service.updateInstitution(Mockito.eq("i1"), Mockito.any())).thenReturn(in);
        mvc.perform(put("/api/institutions/i1").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(in)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("New Bank"));
    }

    @Test
    void delete_returns204() throws Exception {
        mvc.perform(delete("/api/institutions/i1"))
            .andExpect(status().isNoContent());
    }
}


