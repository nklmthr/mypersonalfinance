package com.nklmthr.finance.personal.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.nklmthr.finance.personal.dto.AccountDTO;
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
    public ResponseEntity<List<AccountDTO>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountDTO> getAccount(@PathVariable String id) {
        return ResponseEntity.ok(accountService.findById(id));
    }

    @PostMapping
    public ResponseEntity<AccountDTO> createAccount(@RequestBody AccountDTO accountDTO) {
        return ResponseEntity.ok(accountService.createAccount(accountDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountDTO> updateAccount(@PathVariable String id, @RequestBody AccountDTO accountDTO) {
        return ResponseEntity.ok(accountService.updateAccount(id, accountDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAccount(@PathVariable String id) {
        try {
            accountService.deleteAccount(id);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot delete account: Transactions exist for this account.");
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/filter")
    public ResponseEntity<List<AccountDTO>> getAccounts(
            @RequestParam(required = false) String accountTypeId,
            @RequestParam(required = false) String institutionId) {
        List<AccountDTO> accounts = accountService.getFilteredAccounts(accountTypeId, institutionId);
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/snapshot")
    public ResponseEntity<?> createSnapshot() {
        try {
            snapshotService.createSnapshotsForDate(LocalDateTime.now());
            return ResponseEntity.ok("Snapshot created successfully");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
