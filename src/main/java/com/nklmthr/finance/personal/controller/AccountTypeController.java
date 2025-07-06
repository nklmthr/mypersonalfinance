package com.nklmthr.finance.personal.controller;

import com.nklmthr.finance.personal.model.AccountType;
import com.nklmthr.finance.personal.service.AccountTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/account-types")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class AccountTypeController {

    private final AccountTypeService accountTypeService;

    @PostMapping
    public ResponseEntity<AccountType> create(@RequestBody AccountType accountType) {
        AccountType created = accountTypeService.create(accountType);
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<List<AccountType>> getAll() {
        return ResponseEntity.ok(accountTypeService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountType> getById(@PathVariable Long id) {
        return accountTypeService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<AccountType> getByName(@PathVariable String name) {
        return accountTypeService.getByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountType> update(@PathVariable Long id, @RequestBody AccountType updated) {
        return ResponseEntity.ok(accountTypeService.update(id, updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        accountTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
