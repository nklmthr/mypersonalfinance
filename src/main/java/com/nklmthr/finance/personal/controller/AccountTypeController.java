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
import com.nklmthr.finance.personal.model.AccountType;
import com.nklmthr.finance.personal.service.AccountTypeService;

import lombok.RequiredArgsConstructor;

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
	public ResponseEntity<List<AccountTypeDTO>> getAll() {
		return ResponseEntity.ok(accountTypeService.getAll());
	}

	@GetMapping("/{id}")
	public ResponseEntity<AccountType> getById(@PathVariable String id) {
		return accountTypeService.getById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/name/{name}")
	public ResponseEntity<AccountType> getByName(@PathVariable String name) {
		return accountTypeService.getByName(name).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/{id}")
	public ResponseEntity<AccountType> update(@PathVariable String id, @RequestBody AccountType updated) {
		return ResponseEntity.ok(accountTypeService.update(id, updated));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		accountTypeService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
