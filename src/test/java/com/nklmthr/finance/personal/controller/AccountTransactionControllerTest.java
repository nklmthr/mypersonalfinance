package com.nklmthr.finance.personal.controller;

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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.dto.AccountTransactionDTO;
import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.service.AccountTransactionService;
import com.nklmthr.finance.personal.security.SecurityConfig;
import com.nklmthr.finance.personal.security.JwtAuthenticationFilter;

@WebMvcTest(controllers = AccountTransactionController.class,
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
class AccountTransactionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AccountTransactionService service;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void list_returnsPaged() throws Exception {
        Page<AccountTransactionDTO> page = new PageImpl<>(List.of());
        when(service.getFilteredTransactions(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(page);
        mvc.perform(get("/api/transactions"))
            .andExpect(status().isOk());
    }

    @Test
    void currentTotal_returnsNumber() throws Exception {
        when(service.getCurrentTotal(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(new BigDecimal("10"));
        mvc.perform(get("/api/transactions/currentTotal"))
            .andExpect(status().isOk());
    }

    @Test
    void export_returnsList() throws Exception {
        when(service.getFilteredTransactionsForExport(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(List.of());
        mvc.perform(get("/api/transactions/export"))
            .andExpect(status().isOk());
    }

    @Test
    void getById_found() throws Exception {
        AccountTransactionDTO dto = new AccountTransactionDTO("t1", java.time.LocalDateTime.now(), BigDecimal.ONE, "d", null, null, null, TransactionType.DEBIT, new AccountDTO("a","A", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null);
        when(service.getById("t1")).thenReturn(Optional.of(dto));
        mvc.perform(get("/api/transactions/t1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("t1"));
    }

    @Test
    void getById_notFound() throws Exception {
        when(service.getById("x")).thenReturn(Optional.empty());
        mvc.perform(get("/api/transactions/x"))
            .andExpect(status().isNotFound());
    }

    @Test
    void create_returns200() throws Exception {
        AccountTransactionDTO in = new AccountTransactionDTO(null, java.time.LocalDateTime.now(), BigDecimal.ONE, "d", null, null, null, TransactionType.DEBIT, new AccountDTO("a","A", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null);
        AccountTransactionDTO out = new AccountTransactionDTO("t1", java.time.LocalDateTime.now(), BigDecimal.ONE, "d", null, null, null, TransactionType.DEBIT, new AccountDTO("a","A", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null);
        when(service.save(Mockito.any(AccountTransactionDTO.class))).thenReturn(out);
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(in)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("t1"));
    }

    @Test
    void update_foundReturns200() throws Exception {
        AccountTransactionDTO out = new AccountTransactionDTO("t1", java.time.LocalDateTime.now(), BigDecimal.ONE, "d", null, null, null, TransactionType.DEBIT, new AccountDTO("a","A", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null);
        when(service.updateTransaction(Mockito.eq("t1"), Mockito.any())).thenReturn(Optional.of(out));
        mvc.perform(put("/api/transactions/t1").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(out)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("t1"));
    }

    @Test
    void update_notFoundReturns404() throws Exception {
        AccountTransactionDTO out = new AccountTransactionDTO("t1", java.time.LocalDateTime.now(), BigDecimal.ONE, "d", null, null, null, TransactionType.DEBIT, new AccountDTO("a","A", BigDecimal.ZERO, null, null, null, null, null), new CategoryDTO(), null, List.of(), null, null, null, null, null, null, null, null, null);
        when(service.updateTransaction(Mockito.eq("t1"), Mockito.any())).thenReturn(Optional.empty());
        mvc.perform(put("/api/transactions/t1").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(out)))
            .andExpect(status().isNotFound());
    }

    @Test
    void transfer_returns200() throws Exception {
        mvc.perform(post("/api/transactions/transfer").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceTransactionId\":\"t1\",\"destinationAccountId\":\"a2\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void split_returns200() throws Exception {
        when(service.splitTransaction(Mockito.anyList())).thenReturn(org.springframework.http.ResponseEntity.ok("ok"));
        mvc.perform(post("/api/transactions/split").contentType(MediaType.APPLICATION_JSON)
            .content("[{\"parentId\":\"t1\",\"amount\":10} ]"))
            .andExpect(status().isOk());
    }

    @Test
    void delete_returns204() throws Exception {
        mvc.perform(delete("/api/transactions/t1"))
            .andExpect(status().isNoContent());
    }
}


