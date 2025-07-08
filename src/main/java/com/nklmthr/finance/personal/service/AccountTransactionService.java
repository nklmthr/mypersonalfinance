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

    private final AccountTransactionRepository repository;

    public List<AccountTransaction> getAll() {
        return repository.findAll();
    }

    public Optional<AccountTransaction> getById(String id) {
        return repository.findById(id);
    }

    public List<AccountTransaction> getRootTransactions() {
        return repository.findByParentIsNull();
    }

    public List<AccountTransaction> getChildren(String parentId) {
        return repository.findByParentId(parentId);
    }

    public AccountTransaction save(AccountTransaction transaction) {
        return repository.save(transaction);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }
}
