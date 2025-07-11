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

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.service.AccountService;
import com.nklmthr.finance.personal.service.AccountTransactionService;
import com.nklmthr.finance.personal.service.CategoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class AccountTransactionController {

    private final AccountTransactionService accountTransactionService;
    
    private final AccountService accountService;
    
    private final CategoryService categoryService;

    @GetMapping
    public List<AccountTransaction> getAll() {
        return accountTransactionService.getAll();
    }

    @GetMapping("/root")
    public List<AccountTransaction> getRootTransactions() {
        return accountTransactionService.getRootTransactions();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountTransaction> getById(@PathVariable String id) {
        return accountTransactionService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/children")
    public List<AccountTransaction> getChildren(@PathVariable String id) {
        return accountTransactionService.getChildren(id);
    }

    @PostMapping
    public AccountTransaction create(@RequestBody AccountTransaction tx) {
        return accountTransactionService.save(tx);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountTransaction> update(@PathVariable String id, @RequestBody AccountTransaction tx) {
        return accountTransactionService.getById(id)
                .map(existing -> {
                    tx.setId(id);
                    tx.setAccount(accountService.getAccount(tx.getAccount().getId()));
                    tx.setCategory(categoryService.getCategoryById(tx.getCategory().getId()));
                    tx.setParent(existing.getParent());
                    tx.setChildren(existing.getChildren());
                    tx.setSourceId(existing.getSourceId());
                    tx.setSourceThreadId(existing.getSourceThreadId());
                    tx.setHref(existing.getHref());
                    tx.setHrefText(existing.getHrefText());
                    tx.setSourceTime(existing.getSourceTime());
                    if(tx.getType().equals(TransactionType.DEBIT)) {
                    	tx.getAccount().setBalance(tx.getAccount().getBalance().subtract(tx.getAmount()));
                    } else {
                    	tx.getAccount().setBalance(tx.getAccount().getBalance().add(tx.getAmount()));
                    }
                    return ResponseEntity.ok(accountTransactionService.save(tx));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/transactions/split")
    public ResponseEntity<AccountTransaction> splitTransaction(@RequestBody AccountTransaction transaction) {
        if (transaction.getParent() == null || transaction.getParent().getId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(accountTransactionService.save(transaction));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        accountTransactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
