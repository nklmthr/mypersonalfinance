package com.nklmthr.finance.personal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import com.nklmthr.finance.personal.dto.LabelDTO;
import com.nklmthr.finance.personal.service.LabelService;
import com.nklmthr.finance.personal.security.SecurityConfig;
import com.nklmthr.finance.personal.security.JwtAuthenticationFilter;

@WebMvcTest(controllers = LabelController.class,
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
class LabelControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockBean
    LabelService labelService;
    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void getAll_returns200() throws Exception {
        LabelDTO label1 = new LabelDTO("l1", "Food");
        LabelDTO label2 = new LabelDTO("l2", "Travel");
        when(labelService.getAllLabels()).thenReturn(List.of(label1, label2));

        mvc.perform(get("/api/labels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").value("l1"))
            .andExpect(jsonPath("$[0].name").value("Food"))
            .andExpect(jsonPath("$[1].id").value("l2"))
            .andExpect(jsonPath("$[1].name").value("Travel"));
    }

    @Test
    void search_withQuery_returnsMatchingLabels() throws Exception {
        LabelDTO label1 = new LabelDTO("l1", "Food");
        when(labelService.searchLabels("food")).thenReturn(List.of(label1));

        mvc.perform(get("/api/labels/search").param("q", "food"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].name").value("Food"));
    }

    @Test
    void search_withoutQuery_returnsAllLabels() throws Exception {
        LabelDTO label1 = new LabelDTO("l1", "Food");
        when(labelService.searchLabels(null)).thenReturn(List.of(label1));

        mvc.perform(get("/api/labels/search"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void create_returns200() throws Exception {
        LabelDTO input = new LabelDTO(null, "NewLabel");
        LabelDTO output = new LabelDTO("l1", "NewLabel");
        when(labelService.createLabel(input)).thenReturn(output);

        mvc.perform(post("/api/labels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(input)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("l1"))
            .andExpect(jsonPath("$.name").value("NewLabel"));
    }
}

