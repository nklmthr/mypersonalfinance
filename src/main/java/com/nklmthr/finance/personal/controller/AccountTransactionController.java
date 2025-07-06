package com.nklmthr.finance.personal.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.service.AccountTransactionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class AccountTransactionController {

    private final AccountTransactionService service;

    @GetMapping
    public List<AccountTransaction> getAll() {
        return service.getAll();
    }

    @GetMapping("/root")
    public List<AccountTransaction> getRootTransactions() {
        return service.getRootTransactions();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountTransaction> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/children")
    public List<AccountTransaction> getChildren(@PathVariable Long id) {
        return service.getChildren(id);
    }

    @PostMapping
    public AccountTransaction create(@RequestBody AccountTransaction tx) {
        return service.save(tx);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountTransaction> update(@PathVariable Long id, @RequestBody AccountTransaction tx) {
        return service.getById(id)
                .map(existing -> {
                    tx.setId(id);
                    return ResponseEntity.ok(service.save(tx));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
