package com.nklmthr.finance.personal.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountTransactionService {

    private final AccountTransactionRepository accountTransactionRepository;

    public List<AccountTransaction> getAll() {
        return accountTransactionRepository.findAll();
    }

    public Optional<AccountTransaction> getById(String id) {
        return accountTransactionRepository.findById(id);
    }

    public List<AccountTransaction> getRootTransactions() {
        return accountTransactionRepository.findAllWithGraph();
    }

    public List<AccountTransaction> getChildren(String parentId) {
        return accountTransactionRepository.findByParentId(parentId);
    }

    public AccountTransaction save(AccountTransaction transaction) {
        return accountTransactionRepository.save(transaction);
    }

    public void delete(String id) {
        accountTransactionRepository.deleteById(id);
    }
    
    public boolean isTransactionAlreadyPresent(AccountTransaction newTransaction) {
        Optional<AccountTransaction> existingOpt = accountTransactionRepository.findBySourceThreadId(newTransaction.getSourceThreadId());

        if (existingOpt.isEmpty()) {
            // No transaction with this sourceThreadId found
            return false;
        }

        AccountTransaction existing = existingOpt.get();

        return existing.getDescription().trim().equalsIgnoreCase(newTransaction.getDescription().trim())
            && existing.getAmount().compareTo(newTransaction.getAmount()) == 0
            && existing.getType() == newTransaction.getType();
    }
}
