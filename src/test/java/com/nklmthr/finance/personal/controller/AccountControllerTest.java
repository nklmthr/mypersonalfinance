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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import com.nklmthr.finance.personal.security.SecurityConfig;
import com.nklmthr.finance.personal.security.JwtAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.service.AccountService;
import com.nklmthr.finance.personal.service.AccountSnapshotService;

@WebMvcTest(controllers = AccountController.class,
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
class AccountControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AccountService accountService;
    @MockBean AccountSnapshotService snapshotService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void getAllAccounts_returnsList() throws Exception {
        when(accountService.getAllAccounts()).thenReturn(List.of(new AccountDTO("a1","A1", BigDecimal.ONE, null, null, null, null, null)));
        mvc.perform(get("/api/accounts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getAccount_returns200() throws Exception {
        when(accountService.findById("a1")).thenReturn(new AccountDTO("a1","A1", BigDecimal.ONE, null, null, null, null, null));
        mvc.perform(get("/api/accounts/a1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("a1"));
    }

    @Test
    void createAccount_returns200() throws Exception {
        AccountDTO in = new AccountDTO(null,"A1", BigDecimal.TEN, null, null, null, null, null);
        AccountDTO out = new AccountDTO("a1","A1", BigDecimal.TEN, null, null, null, null, null);
        when(accountService.createAccount(Mockito.any())).thenReturn(out);
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(in)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("a1"));
    }

    @Test
    void updateAccount_returns200() throws Exception {
        AccountDTO out = new AccountDTO("a1","A2", BigDecimal.ONE, null, null, null, null, null);
        when(accountService.updateAccount(Mockito.eq("a1"), Mockito.any())).thenReturn(out);
        mvc.perform(put("/api/accounts/a1").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(out)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("A2"));
    }

    @Test
    void deleteAccount_conflictWhenTransactions() throws Exception {
        Mockito.doThrow(new IllegalStateException("Cannot delete account with existing transactions.")).when(accountService).deleteAccount("a1");
        mvc.perform(delete("/api/accounts/a1"))
            .andExpect(status().isConflict());
    }

    @Test
    void filter_returns200() throws Exception {
        when(accountService.getFilteredAccounts("t","i")).thenReturn(List.of());
        mvc.perform(get("/api/accounts/filter").param("accountTypeId","t").param("institutionId","i"))
            .andExpect(status().isOk());
    }
}


