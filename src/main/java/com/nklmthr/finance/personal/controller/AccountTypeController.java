package com.nklmthr.finance.personal.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.dto.AccountTypeDTO;
import com.nklmthr.finance.personal.service.AccountTypeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account-types")
@RequiredArgsConstructor
public class AccountTypeController {

    private final AccountTypeService accountTypeService;

    @PostMapping
    public ResponseEntity<AccountTypeDTO> create(@RequestBody AccountTypeDTO accountTypeDTO) {
        AccountTypeDTO created = accountTypeService.create(accountTypeDTO);
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<List<AccountTypeDTO>> getAll() {
        return ResponseEntity.ok(accountTypeService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountTypeDTO> getById(@PathVariable String id) {
        return accountTypeService.getById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<AccountTypeDTO> getByName(@PathVariable String name) {
        return accountTypeService.getByName(name)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountTypeDTO> update(@PathVariable String id, @RequestBody AccountTypeDTO updatedDTO) {
        AccountTypeDTO updated = accountTypeService.update(id, updatedDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        accountTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
