package com.nklmthr.finance.personal.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.service.AccountService;
import com.nklmthr.finance.personal.service.AccountSnapshotService;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

	@Autowired
	private AccountService accountService;

	@Autowired
	private AccountSnapshotService snapshotService;

	@GetMapping
	public List<Account> getAllAccounts() {
		return accountService.getAllAccounts();
	}

	@GetMapping("/{id}")
	public ResponseEntity<Account> getAccount(@PathVariable Long id) {
		return ResponseEntity.ok(accountService.getAccount(id));
	}

	@PostMapping
	public ResponseEntity<Account> createAccount(@RequestBody Account account) {
		return ResponseEntity.ok(accountService.createAccount(account));
	}

	@PutMapping("/{id}")
	public ResponseEntity<Account> updateAccount(@PathVariable Long id, @RequestBody Account account) {
		return ResponseEntity.ok(accountService.updateAccount(id, account));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<String> deleteAccount(@PathVariable Long id) {
		try {
			accountService.deleteAccount(id);
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body("Cannot delete account: Transactions exist for this account.");
		}
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/filter")
	public ResponseEntity<List<Account>> getAccounts(@RequestParam(required = false) Long accountTypeId,
			@RequestParam(required = false) Long institutionId) {
		List<Account> accounts = accountService.getFilteredAccounts(accountTypeId, institutionId);
		return ResponseEntity.ok(accounts);
	}

	@PostMapping("/snapshot")
	public ResponseEntity<?> createSnapshot() {
		try {
			snapshotService.createSnapshotsForDate(LocalDate.now());
			return ResponseEntity.ok("Snapshot created successfully");
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
		}
	}

}
